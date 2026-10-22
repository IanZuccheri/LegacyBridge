package dev.legacybridge.validation.datapack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.AbstractCookingRecipe;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.item.crafting.StonecuttingRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ITag;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Native recipe/tag lookup and reload only; no machine ticks, menu interaction or mod execution during conversion. */
@Mod(DatapackRuntimeProbe.MOD_ID)
public final class DatapackRuntimeProbe {
    public static final String MOD_ID = "legacybridge_datapack_probe";
    private static final String FIXTURE_ID = "legacybridge_datapack";
    private static final String BASELINE = "1.16.5-forge-36.2.42";
    private static final String PACK_NAME = "legacybridge-tag-replacement";
    private static final String PACK_ID = "file/" + PACK_NAME;
    private static final String WORLD_NAME = "datapack-comparison-world";
    private static final int PACK_FORMAT = 6;
    private final LinkedHashMap<String, Object> observations = new LinkedHashMap<String, Object>();
    private MinecraftServer server;
    private List<String> originalPacks;
    private List<String> requestedPacks;
    private RecipeManager previousManager;
    private CompletableFuture<Void> pendingReload;
    private int phase;
    private int waitTicks;
    private boolean finished;

    public DatapackRuntimeProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::serverStarted);
        MinecraftForge.EVENT_BUS.addListener(this::serverTick);
    }

    private void serverStarted(FMLServerStartedEvent event) {
        require(server == null && !finished, "Datapack probe started more than once");
        server = event.getServer();
    }

    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null || finished) return;
        boolean completed = false;
        Throwable failure = null;
        try {
            require(server.isSameThread() && server.getPlayerList().getPlayerCount() == 0,
                    "Datapack observations require the isolated logical server thread without connected players");
            if (phase == 0) {
                initializeIdentity();
                originalPacks = selectedPackIds();
                require(!originalPacks.contains(PACK_ID), "The test override is already active");
                observePhase("baseline", false);
                writeReplacementPack();
                server.getPackRepository().reload();
                require(server.getPackRepository().getAvailableIds().contains(PACK_ID),
                        "Native repository did not discover the actual test-owned datapack");
                require(selectedPackIds().equals(originalPacks), "Repository discovery changed existing selection order");
                List<String> replacement = new ArrayList<String>(originalPacks);
                replacement.add(PACK_ID); // Last selected pack has higher native resource priority.
                requestNativeReload(replacement, 1);
            } else {
                waitTicks++;
                require(waitTicks <= 200, "Native reload did not complete within 200 post-return server END ticks");
                require(pendingReload != null, "Missing actual native reload future");
                if (!pendingReload.isDone()) return;
                pendingReload.getNow(null); // Already complete; propagate exceptional completion, never block/join.
                require(server.getRecipeManager() != previousManager, "Native reload did not replace RecipeManager");
                require(selectedPackIds().equals(requestedPacks), "Native selected pack order differs after reload");
                if (phase == 1) {
                    observePhase("replacement", true);
                    requestNativeReload(originalPacks, 2);
                } else {
                    require(phase == 2 && selectedPackIds().equals(originalPacks)
                                    && !selectedPackIds().contains(PACK_ID),
                            "Original datapack selection was not restored");
                    observePhase("restored", false);
                    observations.put("nativeReloadCount", 2);
                    observations.put("nativeManagerReplaced", true);
                    observations.put("higherPriorityReplaceValidated", true);
                    observations.put("originalPackSelectionRestored", true);
                    observations.put("nativeProbeServerThread", true);
                    observations.put("nativeProbePhase", "SERVER_END");
                    observations.put("machineProcessingValidated", false);
                    observations.put("menuInteractionValidated", false);
                    completed = true;
                }
            }
        } catch (Throwable observed) {
            failure = observed;
        }
        if (failure != null || completed) finishProbe(failure);
    }

    private void initializeIdentity() {
        require(ModList.get().getModContainerById(FIXTURE_ID).isPresent(), "Fixture was not discovered");
        require("1.0.0".equals(ModList.get().getModContainerById(FIXTURE_ID).get()
                .getModInfo().getVersion().toString()), "Fixture version changed");
        require(server.overworld() != null, "No actual overworld exists");
        Item ingot = requireItem("bridge_ingot");
        Item blockItem = requireItem("bridge_block");
        Block block = ForgeRegistries.BLOCKS.getValue(id("bridge_block"));
        require(block != null && block.getClass() == Block.class, "Expected a registered plain native block");
        require(ingot.getClass() == Item.class && blockItem instanceof BlockItem
                        && ((BlockItem) blockItem).getBlock() == block, "Native item/block registry relationship differs");
        require(new ItemStack(ingot).getMaxStackSize() == 32, "Plain item stack size differs");
        observations.put("schemaVersion", 1);
        observations.put("modId", FIXTURE_ID);
        observations.put("modVersion", "1.0.0");
        observations.put("ingotRegistryId", id("bridge_ingot").toString());
        observations.put("blockRegistryId", id("bridge_block").toString());
        observations.put("itemStackSize", 32);
    }

    private void requestNativeReload(List<String> packs, int nextPhase) {
        require(server.isSameThread(), "Native reload request left the server thread");
        requestedPacks = new ArrayList<String>(packs);
        previousManager = server.getRecipeManager();
        phase = nextPhase;
        waitTicks = 0;
        // Both exact native APIs managedBlock internally on their own server thread.
        // Do not replace that engine mechanism with a manual tick loop or a blocking future join.
        pendingReload = server.reloadResources(requestedPacks);
        require(pendingReload != null, "Native reload returned no future");
    }

    private List<String> selectedPackIds() {
        List<String> result = new ArrayList<String>();
        // getSelectedIds returns a set in the source version; preserve actual priority list order.
        server.getPackRepository().getSelectedPacks().forEach(pack -> result.add(pack.getId()));
        return result;
    }

    private void observePhase(String prefix, boolean replacement) {
        require(server.isSameThread() && server.getPlayerList().getPlayerCount() == 0,
                "Native reload observation involved another execution side or connected player");
        RecipeManager manager = server.getRecipeManager();
        Item ingot = requireItem("bridge_ingot");
        Item blockItem = requireItem("bridge_block");
        Item accepted = replacement ? blockItem : ingot;
        Item rejected = replacement ? ingot : blockItem;
        long ownRecipes = manager.getRecipes().stream().filter(recipe ->
                FIXTURE_ID.equals(recipe.getId().getNamespace())).count();
        require(ownRecipes == 6, "Actual RecipeManager does not contain exactly six fixture recipes");
        observeTag("processing_leaf", accepted, rejected);
        observeTag("processing_inputs", accepted, rejected);
        observations.put(prefix + "RecipeCount", (int) ownRecipes);
        observations.put(prefix + "AcceptedItem", ForgeRegistries.ITEMS.getKey(accepted).toString());
        observations.put(prefix + "RejectedItem", ForgeRegistries.ITEMS.getKey(rejected).toString());
        observations.put(prefix + "Crafting", observeCrafting(manager, ingot, blockItem));
        observations.put(prefix + "Smelting", observeSingle(manager, IRecipeType.SMELTING,
                "smelting", accepted, rejected, blockItem, 1, 200));
        observations.put(prefix + "Blasting", observeSingle(manager, IRecipeType.BLASTING,
                "blasting", accepted, rejected, blockItem, 1, 100));
        observations.put(prefix + "Smoking", observeSingle(manager, IRecipeType.SMOKING,
                "smoking", accepted, rejected, blockItem, 1, 100));
        observations.put(prefix + "Campfire", observeSingle(manager, IRecipeType.CAMPFIRE_COOKING,
                "campfire_cooking", accepted, rejected, blockItem, 1, 600));
        observations.put(prefix + "Stonecutting", observeSingle(manager, IRecipeType.STONECUTTING,
                "stonecutting", accepted, rejected, blockItem, 2, 0));
    }

    private void observeTag(String name, Item accepted, Item rejected) {
        ITag<Item> tag = server.getTags().getItems().getTag(id(name));
        require(tag != null && tag.getValues().size() == 1 && tag.contains(accepted) && !tag.contains(rejected),
                "Native local tag contents did not reflect the exact nonempty replacement");
    }

    private <T extends IRecipe<IInventory>> int[] observeSingle(RecipeManager manager, IRecipeType<T> type,
            String recipeName, Item accepted, Item rejected, Item blockItem, int expectedCount, int expectedTime) {
        ResourceLocation recipeId = id(recipeName + "_bridge_block");
        IRecipe<?> named = manager.byKey(recipeId).orElseThrow(() ->
                new IllegalStateException("Native recipe is missing: " + recipeId));
        Inventory inventory = new Inventory(new ItemStack(accepted, 3));
        Inventory wrong = new Inventory(new ItemStack(rejected, 3));
        T selected = manager.getRecipeFor(type, inventory, server.overworld()).orElseThrow(() ->
                new IllegalStateException("Native ingredient matching failed for " + recipeId));
        require(selected == named && selected.getId().equals(recipeId) && selected.getType() == type,
                "Native manager selected another recipe or serializer type");
        require(selected.matches(inventory, server.overworld()) && !selected.matches(wrong, server.overworld()),
                "Native recipe matching did not accept/reject the expected tag members");
        require(!manager.getRecipeFor(type, wrong, server.overworld()).isPresent(),
                "Rejected owned ingredient unexpectedly matched a native recipe");
        require(selected.getIngredients().size() == 1 && selected.getIngredients().get(0).test(inventory.getItem(0))
                        && !selected.getIngredients().get(0).test(wrong.getItem(0)),
                "Native Ingredient disagrees with RecipeManager");
        ItemStack result = selected.assemble(inventory);
        ItemStack second = selected.assemble(inventory);
        require(result != second && result.getItem() == blockItem && second.getItem() == blockItem
                        && result.getCount() == expectedCount && second.getCount() == expectedCount,
                "Native assemble result identity, copy or count differs");
        require(inventory.getItem(0).getItem() == accepted && inventory.getItem(0).getCount() == 3,
                "Recipe assembly mutated input; this probe does not pretend to process a machine");
        int experienceMilli = 0;
        if (expectedTime > 0) {
            require(selected instanceof AbstractCookingRecipe, "Expected the actual native cooking recipe class");
            AbstractCookingRecipe cooking = (AbstractCookingRecipe) selected;
            require(cooking.getCookingTime() == expectedTime && Float.compare(cooking.getExperience(), 0.25F) == 0,
                    "Native declared cooking time or experience differs");
            experienceMilli = (int) (cooking.getExperience() * 1000.0F);
        } else {
            require(selected instanceof StonecuttingRecipe, "Expected the actual native stonecutting recipe class");
        }
        return new int[]{1, 0, result.getCount(), expectedTime, experienceMilli, inventory.getItem(0).getCount()};
    }

    private int[] observeCrafting(RecipeManager manager, Item ingot, Item blockItem) {
        // A test-only notification owner for a real native crafting grid, never a registered/opened menu.
        // Unexpected UI behavior fails loudly rather than providing fake slot-transfer implementations.
        Container menu = new Container(null, 0) {
            @Override public ItemStack quickMoveStack(PlayerEntity player, int slot) {
                throw new UnsupportedOperationException("No menu interaction belongs to this recipe-only probe");
            }
            @Override public boolean stillValid(PlayerEntity player) {
                throw new UnsupportedOperationException("No player opens this test-only crafting grid");
            }
        };
        CraftingInventory grid = new CraftingInventory(menu, 3, 3);
        for (int slot = 0; slot < 9; slot++) grid.setItem(slot, new ItemStack(ingot));
        ICraftingRecipe recipe = manager.getRecipeFor(IRecipeType.CRAFTING, grid, server.overworld())
                .orElseThrow(() -> new IllegalStateException("Native base shaped recipe did not match"));
        require(recipe.getId().equals(id("bridge_block")), "Unexpected base shaped recipe selected");
        ItemStack result = recipe.assemble(grid);
        require(result.getItem() == blockItem && result.getCount() == 1, "Native shaped assembly differs");
        for (int slot = 0; slot < 9; slot++) require(grid.getItem(slot).getCount() == 1,
                "Shaped assembly consumed input outside a native processing endpoint");
        grid.setItem(0, ItemStack.EMPTY);
        require(!manager.getRecipeFor(IRecipeType.CRAFTING, grid, server.overworld()).isPresent(),
                "Incomplete base shaped recipe unexpectedly matched");
        return new int[]{9, 1, 0};
    }

    private void writeReplacementPack() throws IOException {
        Path working = Paths.get("").toAbsolutePath().normalize().toRealPath();
        Path expected = working.resolve(WORLD_NAME).resolve("datapacks");
        Path actual = server.getWorldPath(FolderName.DATAPACK_DIR).toAbsolutePath().normalize();
        require(actual.equals(expected), "Native datapack directory escapes the explicitly owned disposable world");
        requirePlainDirectory(actual);
        Path pack = actual.resolve(PACK_NAME);
        Files.createDirectory(pack); // Refuse any old/unknown pack; no overwrite, delete or shared world.
        requirePlainDirectory(pack);
        BehaviorTraceWriter.writeNew(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":" + PACK_FORMAT
                + ",\"description\":\"LegacyBridge local native tag replacement probe\"}}\n");
        Path child = pack;
        for (String part : new String[]{"data", FIXTURE_ID, "tags", "items"}) {
            child = Files.createDirectory(child.resolve(part));
            requirePlainDirectory(child);
        }
        BehaviorTraceWriter.writeNew(child.resolve("processing_leaf.json"),
                "{\"replace\":true,\"values\":[\"legacybridge_datapack:bridge_block\"]}\n");
    }

    private static void requirePlainDirectory(Path directory) throws IOException {
        for (Path current = directory; current != null; current = current.getParent()) {
            require(Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(current)
                            && current.toRealPath().equals(current.toAbsolutePath().normalize()),
                    "Datapack test directories must be real local directories without aliases");
        }
    }

    private void finishProbe(Throwable failure) {
        require(!finished, "Datapack probe tried to finish twice");
        finished = true;
        try {
            if (failure != null) throw failure;
            BehaviorTraceWriter.writeNew(Paths.get("datapack-trace.json"), BehaviorTraceWriter.render(observations));
            System.out.println("LEGACYBRIDGE_DATAPACK_BEHAVIOR_OK baseline=" + BASELINE
                    + " fixture=legacybridge_datapack version=1.0.0 nativeReloads=2 trace=datapack-trace.json");
        } catch (Throwable observed) {
            failure = observed;
            System.err.println("LEGACYBRIDGE_DATAPACK_BEHAVIOR_FAILED baseline=" + BASELINE);
            observed.printStackTrace(System.err);
        } finally {
            try {
                server.execute(() -> server.halt(false));
            } catch (Throwable shutdownFailure) {
                System.err.println("LEGACYBRIDGE_DATAPACK_BEHAVIOR_FAILED shutdown baseline=" + BASELINE);
                shutdownFailure.printStackTrace(System.err);
                if (failure != null) shutdownFailure.addSuppressed(failure);
                throw new IllegalStateException("Cannot schedule orderly datapack probe shutdown", shutdownFailure);
            }
        }
        if (failure != null) throw new IllegalStateException("Datapack runtime probe failed", failure);
    }

    private static Item requireItem(String name) {
        ResourceLocation key = id(name);
        require(ForgeRegistries.ITEMS.containsKey(key), "Native fixture item is missing: " + key);
        Item item = ForgeRegistries.ITEMS.getValue(key);
        require(item != null && key.equals(ForgeRegistries.ITEMS.getKey(item)), "Item registry identity changed");
        return item;
    }

    private static ResourceLocation id(String name) { return new ResourceLocation(FIXTURE_ID, name); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
