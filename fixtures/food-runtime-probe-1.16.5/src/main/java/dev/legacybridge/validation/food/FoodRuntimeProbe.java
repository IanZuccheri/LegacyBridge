package dev.legacybridge.validation.food;

import com.mojang.authlib.GameProfile;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Native server endpoints only. FakePlayer.tick is a no-op: this is not a 32-tick eating simulation. */
@Mod(FoodRuntimeProbe.MOD_ID)
public final class FoodRuntimeProbe {
    public static final String MOD_ID = "legacybridge_food_probe";
    private static final String FIXTURE_ID = "legacybridge_food";
    private static final String ENTRY_CLASS = "dev.legacybridge.fixture.food.FoodContent";
    private static final String ITEM_CLASS = "dev.legacybridge.fixture.food.RationItem";
    private static final String BASELINE = "1.16.5-forge-36.2.42";
    private static final int MAX_READINESS_TICKS = 200;
    private MinecraftServer pendingServer;
    private ServerWorld pendingWorld;
    private boolean ownsChunkTicket;
    private boolean finished;
    private int readinessTicks;
    private int consecutiveReadyTicks;

    public FoodRuntimeProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::serverStarted);
        MinecraftForge.EVENT_BUS.addListener(this::serverTick);
    }

    private void serverStarted(FMLServerStartedEvent event) {
        try {
            require(pendingServer == null && !finished, "Food probe was started more than once");
            pendingServer = event.getServer();
            require(pendingServer.isSameThread() && pendingServer.getPlayerList().getPlayerCount() == 0,
                    "Chunk readiness must start on the isolated server thread without connected players");
            pendingWorld = pendingServer.overworld();
            require(pendingWorld != null && pendingWorld.getForcedChunks().isEmpty(),
                    "Food probe requires a fresh overworld with no pre-existing forced chunks");
            // Own the attempted change before calling: the native setter can fail after inserting its saved flag.
            ownsChunkTicket = true;
            require(pendingWorld.setChunkForced(0, 0, true),
                    "Native ticket request did not newly force the test-owned chunk");
            require(pendingWorld.getForcedChunks().size() == 1 && pendingWorld.getForcedChunks().contains(0L),
                    "Native forced chunk set differs from the sole test-owned chunk 0,0");
            System.out.println("LEGACYBRIDGE_FOOD_CHUNK_WAIT baseline=" + BASELINE + " chunk=0,0 maxTicks=200");
        } catch (Throwable failure) {
            finishProbe(failure);
        }
    }

    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingServer == null || finished) return;
        Throwable failure = null;
        boolean ready = false;
        try {
            require(pendingServer.isSameThread() && pendingServer.getPlayerList().getPlayerCount() == 0,
                    "Native chunk readiness must remain on the isolated logical server thread");
            require(pendingWorld == pendingServer.overworld() && ownsChunkTicket
                            && pendingWorld.getForcedChunks().size() == 1
                            && pendingWorld.getForcedChunks().contains(0L),
                    "The native test-owned chunk ticket changed while waiting");
            readinessTicks++;
            require(readinessTicks <= MAX_READINESS_TICKS,
                    "Native entity-ticking readiness did not stabilize within 200 server END ticks");
            consecutiveReadyTicks = nativeChunkReady(pendingWorld) ? consecutiveReadyTicks + 1 : 0;
            ready = consecutiveReadyTicks >= 2;
        } catch (Throwable observed) {
            failure = observed;
        }
        // Do not catch a failure from finishProbe and then accidentally invoke or suppress it a second time.
        if (failure != null || ready) finishProbe(failure);
    }

    private static boolean nativeChunkReady(ServerWorld world) {
        return world.getChunkSource().isEntityTickingChunk(new ChunkPos(0, 0));
    }

    private void releaseOwnedChunkTicket() {
        if (!ownsChunkTicket) return;
        require(pendingServer.isSameThread() && pendingWorld != null,
                "Native test-owned ticket cleanup requires its server thread");
        require(pendingWorld.getForcedChunks().contains(0L) && pendingWorld.setChunkForced(0, 0, false),
                "Native test-owned chunk ticket could not be released");
        ownsChunkTicket = false;
        require(pendingWorld.getForcedChunks().isEmpty(),
                "Unexpected forced chunks remain after releasing the sole test-owned ticket");
    }

    private void finishProbe(Throwable failure) {
        require(!finished, "Food probe attempted to finish more than once");
        finished = true;
        try {
            if (failure != null) throw failure;
            require(readinessTicks >= 2 && consecutiveReadyTicks >= 2 && nativeChunkReady(pendingWorld),
                    "Food behavior cannot run before native entity-ticking readiness");
            LinkedHashMap<String, Object> observations = captureBehavior(pendingServer);
            releaseOwnedChunkTicket();
            observations.put("nativeChunkReadinessValidated", true);
            observations.put("nativeReadinessPhase", "SERVER_END");
            observations.put("nativeForcedChunkReleased", true);
            BehaviorTraceWriter.writeNew(Paths.get("food-trace.json"), BehaviorTraceWriter.render(observations));
            System.out.println("LEGACYBRIDGE_FOOD_BEHAVIOR_OK baseline=" + BASELINE
                    + " fixture=legacybridge_food version=1.0.0 trace=food-trace.json readinessTicks=" + readinessTicks);
        } catch (Throwable observed) {
            failure = observed;
            System.err.println("LEGACYBRIDGE_FOOD_BEHAVIOR_FAILED baseline=" + BASELINE);
            observed.printStackTrace(System.err);
        } finally {
            try {
                releaseOwnedChunkTicket();
            } catch (Throwable cleanupFailure) {
                System.err.println("LEGACYBRIDGE_FOOD_BEHAVIOR_FAILED chunk-cleanup baseline=" + BASELINE);
                cleanupFailure.printStackTrace(System.err);
                if (failure != null) failure.addSuppressed(cleanupFailure);
                else failure = cleanupFailure;
            }
            try {
                require(pendingServer != null, "Cannot stop a probe without its actual server");
                pendingServer.execute(() -> pendingServer.halt(false));
            } catch (Throwable shutdownFailure) {
                System.err.println("LEGACYBRIDGE_FOOD_BEHAVIOR_FAILED shutdown baseline=" + BASELINE);
                shutdownFailure.printStackTrace(System.err);
                if (failure != null) shutdownFailure.addSuppressed(failure);
                throw new IllegalStateException("Cannot schedule orderly food probe shutdown", shutdownFailure);
            }
        }
        if (failure != null) throw new IllegalStateException("Food runtime comparison probe failed", failure);
    }

    private static LinkedHashMap<String, Object> captureBehavior(MinecraftServer server) throws Exception {
        require(server.isSameThread(), "Food probe must run on the native logical server thread");
        require(server.getPlayerList().getPlayerCount() == 0,
                "Native game-mode broadcasts require this disposable server to have no connected players");
        require(ModList.get().getModContainerById(FIXTURE_ID).isPresent(), "Fixture not loaded");
        Object fixture = ModList.get().getModContainerById(FIXTURE_ID).get().getMod();
        require(fixture != null && ENTRY_CLASS.equals(fixture.getClass().getName()), "Entrypoint identity changed");
        String version = ModList.get().getModContainerById(FIXTURE_ID).get().getModInfo().getVersion().toString();
        require("1.0.0".equals(version), "Unexpected fixture version");
        ResourceLocation id = new ResourceLocation(FIXTURE_ID, "ration");
        require(ForgeRegistries.ITEMS.containsKey(id), "Native food item was not registered");
        Item ration = ForgeRegistries.ITEMS.getValue(id);
        require(ration != null && id.equals(ForgeRegistries.ITEMS.getKey(ration)), "Registry identity differs");
        require(ITEM_CLASS.equals(ration.getClass().getName()), "Original Item subclass was replaced");
        require(new ItemStack(ration).getMaxStackSize() == 16, "Stack size differs");
        int nativeDuration = new ItemStack(ration).getUseDuration();
        require(nativeDuration == 32, "Unexpected native food duration");
        ServerWorld world = server.overworld();
        require(world != null, "No native overworld for the isolated fake player");
        // Fresh, test-owned profile, not the shared FakePlayerFactory singleton or a player's account.
        FakePlayer player = new FakePlayer(world, new GameProfile(
                UUID.fromString("00000000-0000-0000-0000-00000000000f"), "LegacyFoodProbe"));
        player.setPos(0.5D, 80.0D, 0.5D);
        player.setHealth(20.0F);
        // Readiness was reached by real server END ticks, not a synchronous FULL generation assumption.
        require(world.getForcedChunks().contains(0L) && nativeChunkReady(world),
                "The actual drop chunk is no longer natively entity-ticking");

        int[] nativeGameModes = new int[4];
        int[] survivalHungry = consumeCase(world, player, ration, false, 10, true, 2, 13, 3.0F);
        nativeGameModes[0] = player.gameMode.getGameModeForPlayer().getId();
        int[] survivalFull = consumeCase(world, player, ration, false, 20, false, 3, 20, 0.0F);
        nativeGameModes[1] = player.gameMode.getGameModeForPlayer().getId();
        int[] creativeHungry = consumeCase(world, player, ration, true, 10, true, 3, 13, 3.0F);
        nativeGameModes[2] = player.gameMode.getGameModeForPlayer().getId();
        int[] creativeFull = consumeCase(world, player, ration, true, 20, true, 3, 20, 3.0F);
        nativeGameModes[3] = player.gameMode.getGameModeForPlayer().getId();
        selectNativeGameMode(player, false);
        int dropGameMode = player.gameMode.getGameModeForPlayer().getId();
        player.inventory.clearContent();
        player.setItemInHand(Hand.MAIN_HAND, new ItemStack(ration, 7));
        require(inventoryCount(player, ration) == 7, "Drop fixture inventory did not start with seven units");

        int[] afterRemoval = new int[3];
        int[] liveDropped = new int[3];
        int[] afterPickup = new int[3];
        boolean[] removedAfterPickup = new boolean[3];
        boolean[] worldDiscoveredDrops = new boolean[3];
        for (int index = 0; index < 3; index++) {
            // Native inventory removal FIRST. Supplying a fresh stack here would fabricate duplication.
            ItemStack removed = player.getItemInHand(Hand.MAIN_HAND).split(2);
            require(removed.getCount() == 2, "Native inventory split failed");
            afterRemoval[index] = inventoryCount(player, ration);
            require(afterRemoval[index] == 5, "Drop did not remove exactly two carried units");
            // ServerPlayer.drop already adds the ItemEntity to its level. Never add it a second time.
            ItemEntity dropped = player.drop(removed, false, true);
            require(dropped != null && dropped.isAlive(), "Native drop did not create a live item entity");
            worldDiscoveredDrops[index] = world.getEntity(dropped.getId()) == dropped;
            require(worldDiscoveredDrops[index], "Native world lookup did not discover the actual dropped entity");
            liveDropped[index] = dropped.getItem().getCount();
            require(liveDropped[index] == 2 && afterRemoval[index] + liveDropped[index] == 7,
                    "Live dropped plus carried item count was not conserved");
            dropped.setNoPickUpDelay();
            dropped.playerTouch(player);
            removedAfterPickup[index] = !dropped.isAlive();
            require(removedAfterPickup[index], "Complete native pickup did not remove its item entity");
            afterPickup[index] = inventoryCount(player, ration);
            require(afterPickup[index] == 7, "Native pickup lost or duplicated carried items");
            // Native pickup restores the removed entity's stack count for feedback. Do not count a
            // removed entity or call playerTouch on it again; the next iteration creates a NEW drop.
        }
        require(player.getActiveEffects().isEmpty(), "The plain food unexpectedly granted a status effect");
        require(server.getPlayerList().getPlayerCount() == 0,
                "The controlled fake-player observation must not involve a connected player");

        LinkedHashMap<String, Object> observations = new LinkedHashMap<String, Object>();
        observations.put("schemaVersion", 1);
        observations.put("modId", FIXTURE_ID);
        observations.put("modVersion", version);
        observations.put("registryId", id.toString());
        observations.put("itemClass", ration.getClass().getName());
        observations.put("maxStackSize", 16);
        observations.put("nativeProbeServerThread", true);
        observations.put("nativeGameModeValidated", true);
        observations.put("nativeGameModes", nativeGameModes);
        observations.put("dropGameMode", dropGameMode);
        observations.put("nativeUseDuration", nativeDuration);
        observations.put("fullTickSequenceValidated", false);
        observations.put("survivalHungry", survivalHungry);
        observations.put("survivalFull", survivalFull);
        observations.put("creativeHungry", creativeHungry);
        observations.put("creativeFull", creativeFull);
        observations.put("dropStartingCount", 7);
        observations.put("afterInventoryRemoval", afterRemoval);
        observations.put("liveDroppedCounts", liveDropped);
        observations.put("afterPickupCounts", afterPickup);
        observations.put("removedAfterPickup", removedAfterPickup);
        observations.put("worldDiscoveredDrops", worldDiscoveredDrops);
        observations.put("activeEffects", 0);
        return observations;
    }

    /**
     * Native start and completion endpoints, not elapsed ticks.
     * Result vector: [startFood,startSaturationMilli,startStack,consuming,finalFood,finalSaturationMilli,finalStack].
     */
    private static int[] consumeCase(ServerWorld world, FakePlayer player, Item ration, boolean creative,
                                     int startingFood, boolean consumes, int finalCount, int finalFood,
                                     float finalSaturation) {
        player.stopUsingItem();
        player.inventory.clearContent();
        selectNativeGameMode(player, creative);
        // Forge36 setSaturation is client-only. Use the actual native persistence reader on both sides.
        CompoundNBT food = new CompoundNBT();
        food.putInt("foodLevel", startingFood);
        food.putInt("foodTickTimer", 0);
        food.putFloat("foodSaturationLevel", 0.0F);
        food.putFloat("foodExhaustionLevel", 0.0F);
        player.getFoodData().readAdditionalSaveData(food);
        require(player.getFoodData().getFoodLevel() == startingFood
                        && Float.compare(player.getFoodData().getSaturationLevel(), 0.0F) == 0,
                "Native food-state reset failed");
        player.setItemInHand(Hand.MAIN_HAND, new ItemStack(ration, 3));
        ItemStack held = player.getItemInHand(Hand.MAIN_HAND);
        ActionResult<ItemStack> result = held.use(world, player, Hand.MAIN_HAND);
        require(result.getObject() == held, "Native food-use stack identity changed");
        if (consumes) {
            require(result.getResult() == ActionResultType.CONSUME, "Hungry/creative player could not start native food use");
            require(player.isUsingItem() && player.getUseItem() == held
                            && player.getUseItemRemainingTicks() == 32,
                    "Native use-start state was cancelled, replaced or changed before completion");
            // Authorized direct native completion endpoint. Does not pretend to run Forge finish-use events.
            ItemStack completed = result.getObject().finishUsingItem(world, player);
            require(completed == held, "Plain native food completion replaced the original stack object");
            player.setItemInHand(Hand.MAIN_HAND, completed);
        } else {
            require(result.getResult() == ActionResultType.FAIL, "Full survival hunger did not refuse native food use");
            require(!player.isUsingItem(), "Rejected full-hunger use unexpectedly started consuming");
        }
        player.stopUsingItem();
        int observedCount = player.getItemInHand(Hand.MAIN_HAND).getCount();
        int observedFood = player.getFoodData().getFoodLevel();
        float observedSaturation = player.getFoodData().getSaturationLevel();
        require(observedCount == finalCount && observedFood == finalFood
                        && Float.compare(observedSaturation, finalSaturation) == 0,
                "Native consumption, hunger or saturation disagrees with the independent food oracle");
        require(inventoryCount(player, ration) == finalCount, "Consumption generated an unexpected container or extra stack");
        return new int[]{startingFood, 0, 3, consumes ? 1 : 0, observedFood,
                (int) (observedSaturation * 1000.0F), observedCount};
    }

    /** Uses the real player transition, including Forge cancellation and native ability updates. */
    private static void selectNativeGameMode(FakePlayer player, boolean creative) {
        MinecraftServer server = player.getServer();
        require(server != null && server.isSameThread() && server.getPlayerList().getPlayerCount() == 0,
                "Native game-mode transition requires the isolated server thread with no connected players");
        GameType desired = creative ? GameType.CREATIVE : GameType.SURVIVAL;
        if (player.gameMode.getGameModeForPlayer() != desired) {
            player.setGameMode(desired);
        }
        require(player.gameMode.getGameModeForPlayer() == desired && player.isCreative() == creative
                        && !player.isSpectator(), "Native effective game mode differs from the requested test mode");
        require(player.abilities.instabuild == creative && player.abilities.invulnerable == creative
                        && player.abilities.mayfly == creative && player.abilities.mayBuild,
                "Native game-mode transition did not set the expected abilities");
    }

    private static int inventoryCount(FakePlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < player.inventory.getContainerSize(); slot++) {
            ItemStack stack = player.inventory.getItem(slot);
            if (!stack.isEmpty()) {
                require(stack.getItem() == item, "Food or pickup created an unexpected container or other item");
                require(stack.getCount() <= 16, "Native inventory contains an over-limit ration stack");
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
