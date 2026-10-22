package dev.legacybridge.validation.composition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.versions.forge.ForgeVersion;

/**
 * Explicit, disposable native TARGET capture only. Never called during conversion or shipped
 * in LegacyBridge. The detached furnace is manually ticked synchronously before the server loop;
 * this is not client, live-world scheduling, persistence, XP payout or multiplayer evidence.
 */
@Mod(ProbeSession.MOD_ID)
public final class CompositionRuntimeProbe {
    private static final String OWNED_CLASS = "dev.legacybridge.fixture.composed.ComposedItem";
    private static final String ROLE = "TARGET";
    private final Path gameDir;
    private final ProbeSession.Config config;
    private boolean started;

    public CompositionRuntimeProbe() {
        require(FMLEnvironment.dist == Dist.DEDICATED_SERVER, "Composition probe refuses the physical client");
        gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        try {
            // Main loads @Mod constructors before creating/loading the native level in this baseline.
            config = ProbeSession.arm(gameDir, ROLE, System.getProperty("legacybridge.compositionProbe.run"));
        } catch (Exception failure) {
            throw new IllegalStateException("Composition probe was not explicitly armed for a fresh disposable run", failure);
        }
        MinecraftForge.EVENT_BUS.addListener(this::serverStarted);
    }

    private void serverStarted(ServerStartedEvent event) {
        require(!started, "Composition probe cannot capture twice");
        started = true;
        try {
            ProbeSession.run(config, new NativePlatform(event.getServer()));
        } catch (Exception failure) {
            throw new IllegalStateException("Native composition capture failed", failure);
        }
    }

    private final class NativePlatform implements ProbeSession.Platform {
        private final MinecraftServer server;
        NativePlatform(MinecraftServer server) { this.server = server; }

        @Override public Map<String, Object> environment() {
            ServerLevel level = server.overworld();
            return ProbeJson.object("minecraft", server.getServerVersion(), "forge", ForgeVersion.getVersion(),
                    "javaMajor", javaMajor(), "physicalSide", FMLEnvironment.dist.name(),
                    "logicalSide", level != null && !level.isClientSide ? "SERVER" : "CLIENT",
                    "serverThread", server.isSameThread());
        }

        @Override public Map<String, String> loadedMods() {
            Map<String, String> result = new LinkedHashMap<String, String>();
            ModList.get().getMods().forEach(mod -> {
                require(result.put(mod.getModId(), mod.getVersion().toString()) == null, "Duplicate loaded modId");
            });
            return result;
        }

        @Override public Path modFile(String id) {
            require(ModList.get().getModFileById(id) != null, "Missing actually loaded mod: " + id);
            return ModList.get().getModFileById(id).getFile().getFilePath();
        }

        @Override public void assertIsolated() throws Exception {
            require(FMLEnvironment.dist == Dist.DEDICATED_SERVER && server.isDedicatedServer()
                            && server.isSameThread() && server.overworld() != null
                            && !server.overworld().isClientSide && server.getPlayerList().getPlayerCount() == 0,
                    "Native capture requires the dedicated logical server thread with zero players");
            Path actual = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path expected = gameDir.resolve("world");
            require(actual.equals(expected) && actual.toRealPath().equals(expected),
                    "Actual native world escaped the armed disposable directory");
        }

        @Override public Map<String, Object> captureBehavior() throws Exception {
            assertIsolated();
            final ResourceLocation key = id("count_token");
            require(ForgeRegistries.ITEMS.containsKey(key), "Fixture item absent from native registry");
            final Item item = ForgeRegistries.ITEMS.getValue(key);
            require(item != null && key.equals(ForgeRegistries.ITEMS.getKey(item)), "Native registry identity differs");
            final Class<?> owner = item.getClass();
            require(OWNED_CLASS.equals(owner.getName()), "Owned fixture subclass was replaced or renamed");
            final Method calls = owner.getDeclaredMethod("calls");
            require(calls.getDeclaringClass() == owner && calls.getReturnType() == int.class
                            && Modifier.isPublic(calls.getModifiers()) && !Modifier.isStatic(calls.getModifiers())
                            && calls.getParameterCount() == 0,
                    "Unexpected exact fixture-owned public calls() signature");
            BehaviorCapture.ItemView view = new BehaviorCapture.ItemView() {
                @Override public String registryId() { return ForgeRegistries.ITEMS.getKey(item).toString(); }
                @Override public String className() { return owner.getName(); }
                @Override public int stackLimit() { return new ItemStack(item).getMaxStackSize(); }
                @Override public int calls() throws Exception { return invokeCalls(calls, item); }
                @Override public BehaviorCapture.StackView stack(int count) {
                    final ItemStack stack = new ItemStack(item, count);
                    return new BehaviorCapture.StackView() {
                        @Override public int count() { return stack.getCount(); }
                        @Override public boolean foil() { return stack.hasFoil(); }
                        @Override public boolean enchanted() { return stack.isEnchanted(); }
                        @Override public void enchant() {
                            require(new ResourceLocation("minecraft", "unbreaking")
                                            .equals(ForgeRegistries.ENCHANTMENTS.getKey(Enchantments.UNBREAKING)),
                                    "Native unbreaking registry binding differs");
                            stack.enchant(Enchantments.UNBREAKING, 1);
                        }
                    };
                }
            };
            return BehaviorCapture.capture(view, () -> {
                assertIsolated();
                return NativeFurnace.open(server);
            });
        }

        @Override public void shutdown() {
            require(server.isSameThread(), "Orderly shutdown must remain on the owning server thread");
            server.halt(false); // false never self-joins; native runServer finally performs save/close.
        }
    }

    /**
     * Actual native furnace inventory, deliberately never added as a world block entity.
     * An empty native support furnace supplies the real LIT block state required by vanilla
     * ignition. Only this detached instance owns input/fuel/output; synchronous server-thread
     * execution prevents the world's empty support entity from interleaving with our 120 calls.
     */
    private static final class NativeFurnace implements BehaviorCapture.FurnaceView {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final BlockPos pos;
        private final BlockState original;
        private BlockEntity support;
        private FurnaceBlockEntity detached;
        private boolean placed;
        private boolean closed;

        private NativeFurnace(MinecraftServer server) {
            this.server = server;
            level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            pos = new BlockPos(spawn.getX(), 200, spawn.getZ());
            require(level.hasChunkAt(pos), "Probe refuses to force-load an unprepared chunk");
            original = level.getBlockState(pos);
            require(original.getBlock() == Blocks.AIR && level.getBlockEntity(pos) == null,
                    "Reserved native furnace support position is not empty air");
        }

        static NativeFurnace open(MinecraftServer server) throws Exception {
            NativeFurnace furnace = new NativeFurnace(server);
            try {
                require(furnace.level.setBlock(furnace.pos, Blocks.FURNACE.defaultBlockState(), 3),
                        "Native support furnace placement failed");
                furnace.placed = true;
                furnace.support = furnace.level.getBlockEntity(furnace.pos);
                require(furnace.support instanceof FurnaceBlockEntity, "Native support block entity is not a furnace");
                furnace.detached = new FurnaceBlockEntity(furnace.pos, furnace.level.getBlockState(furnace.pos));
                furnace.detached.setLevel(furnace.level);
                furnace.assertOwned();
                furnace.detached.setItem(0, new ItemStack(Items.IRON_INGOT, 1));
                furnace.detached.setItem(1, new ItemStack(Items.COAL, 1));
                return furnace;
            } catch (Exception failure) {
                try { furnace.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            } catch (Error failure) {
                try { furnace.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        @Override public Map<String, Object> recipe() {
            assertOwned();
            Recipe<?> recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, detached, level)
                    .orElseThrow(() -> new IllegalStateException("Native furnace selected no recipe"));
            require(recipe instanceof AbstractCookingRecipe, "Native selection is not a cooking recipe");
            Recipe<?> byId = level.getRecipeManager().byKey(recipe.getId())
                    .orElseThrow(() -> new IllegalStateException("Selected native recipe ID is absent"));
            require(recipe == byId && recipe.getIngredients().size() == 1, "Native recipe selection differs");
            JsonElement raw = recipe.getIngredients().get(0).toJson();
            require(raw.isJsonObject(), "Native ingredient is not the exact single tag form");
            JsonObject ingredient = raw.getAsJsonObject();
            require(ingredient.entrySet().size() == 1 && ingredient.has("tag")
                            && ingredient.get("tag").isJsonPrimitive()
                            && ingredient.getAsJsonPrimitive("tag").isString(),
                    "Native ingredient contract was rewritten into another form");
            ResourceLocation tagId = new ResourceLocation(ingredient.get("tag").getAsString());
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
            HolderSet.Named<Item> tag = server.registryAccess().registryOrThrow(Registries.ITEM).getTag(tagKey)
                    .orElseThrow(() -> new IllegalStateException("Native ingredient tag is missing"));
            List<String> values = new ArrayList<String>();
            for (Holder<Item> value : tag) values.add(ForgeRegistries.ITEMS.getKey(value.value()).toString());
            Collections.sort(values);
            AbstractCookingRecipe cooking = (AbstractCookingRecipe) recipe;
            return ProbeJson.object("recipeId", recipe.getId().toString(),
                    "recipeType", BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString(),
                    "ingredientTag", tagId.toString(), "tagItems", values,
                    "recipeTicks", cooking.getCookingTime(), "recipeExperience", cooking.getExperience());
        }

        @Override public Map<String, Object> sample(int tick) {
            assertOwned();
            ItemStack input = detached.getItem(0), fuel = detached.getItem(1), output = detached.getItem(2);
            return ProbeJson.object("tick", tick, "inputId", stackId(input), "inputCount", stackCount(input),
                    "fuelId", stackId(fuel), "fuelCount", stackCount(fuel),
                    "outputId", stackId(output), "outputCount", stackCount(output));
        }

        @Override public void tick() {
            assertOwned();
            net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(
                    level, pos, level.getBlockState(pos), detached);
            assertOwned();
        }

        private void assertOwned() {
            require(!closed && server.isSameThread() && !level.isClientSide
                            && server.getPlayerList().getPlayerCount() == 0,
                    "Native furnace driver lost its isolated server-thread context");
            require(placed && level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() == Blocks.FURNACE
                            && support instanceof FurnaceBlockEntity && level.getBlockEntity(pos) == support
                            && support != detached && ((FurnaceBlockEntity) support).isEmpty(),
                    "Furnace support changed identity/state; refusing another object's inventory or block");
        }

        @Override public void close() {
            if (closed) return;
            if (!placed) { closed = true; return; }
            assertOwned();
            if (detached != null) detached.clearContent(); // Owned detached stacks, no native world drops.
            require(level.setBlock(pos, original, 3), "Cannot restore verified owned support block to original air");
            require(level.getBlockState(pos).equals(original) && level.getBlockEntity(pos) == null,
                    "Native support cleanup left another block/entity; no fallback overwrite permitted");
            closed = true;
        }
    }

    private static int invokeCalls(Method method, Item receiver) throws Exception {
        try {
            Object result = method.invoke(receiver);
            require(result instanceof Integer, "Owned calls() returned a non-int");
            return ((Integer) result).intValue();
        } catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Unexpected owned fixture invocation failure", wrapped);
        }
    }

    private static int javaMajor() {
        String version = System.getProperty("java.specification.version");
        return Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
    }
    private static ResourceLocation id(String value) { return new ResourceLocation(ProbeSession.FIXTURE_ID, value); }
    private static String stackId(ItemStack stack) {
        return stack.isEmpty() ? "minecraft:air" : ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }
    private static int stackCount(ItemStack stack) { return stack.isEmpty() ? 0 : stack.getCount(); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
