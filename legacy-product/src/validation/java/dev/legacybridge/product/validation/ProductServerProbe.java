package dev.legacybridge.product.validation;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import static dev.legacybridge.product.validation.ProductProbeSupport.require;

/** Dedicated-only QA subscriber. Runtime assertions stay on the server thread; static work does not. */
@Mod.EventBusSubscriber(modid = LegacyBridgeProductProbe.MOD_ID, value = Dist.DEDICATED_SERVER,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProductServerProbe {
    private static final AtomicBoolean FINISHED = new AtomicBoolean();
    private static MinecraftServer server;
    private static long deadline;

    private ProductServerProbe() { }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!ProductProbeSupport.enabled("server")) return;
        server = event.getServer();
        deadline = ProductProbeSupport.deadline();
        try {
            verifyContent(server);
            LogUtils.getLogger().info("LEGACYBRIDGE_PRODUCT_SERVER_CONTENT_OK item=bridge_ingot block=bridge_block recipe=9_to_1 negativeRecipe=passed tags=passed");
            ProductProbeSupport.run("server", deadline, false).whenComplete((evidence, failure) ->
                    server.execute(() -> finish(evidence, failure)));
        } catch (Throwable failure) {
            finish(null, failure);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && server != null && !FINISHED.get()
                && System.nanoTime() > deadline) finish(null, new IllegalStateException("Production probe timeout"));
    }

    private static void finish(String evidence, Throwable failure) {
        if (!FINISHED.compareAndSet(false, true)) return;
        if (failure == null) LogUtils.getLogger().info("LEGACYBRIDGE_PRODUCT_SERVER_OK {}", evidence);
        else LogUtils.getLogger().error("LEGACYBRIDGE_PRODUCT_SERVER_FAILED", failure);
        ProductProbeSupport.close();
        server.execute(() -> server.halt(false));
    }

    static void verifyContent(MinecraftServer server) {
        require(ModList.get().getModContainerById(LegacyBridgeProductProbe.FIXTURE_ID).orElseThrow()
                .getModInfo().getVersion().toString().equals("1.0.0"), "Converted fixture version changed");
        var ingotId = id("bridge_ingot");
        var blockId = id("bridge_block");
        require(ForgeRegistries.ITEMS.containsKey(ingotId) && ForgeRegistries.BLOCKS.containsKey(blockId)
                && ForgeRegistries.ITEMS.containsKey(blockId), "Converted registrations are missing");
        Item ingot = ForgeRegistries.ITEMS.getValue(ingotId);
        Block block = ForgeRegistries.BLOCKS.getValue(blockId);
        Item blockItem = ForgeRegistries.ITEMS.getValue(blockId);
        require(ingot != null && ingot.getClass() == Item.class, "Fixture item must be a plain native Item");
        require(block != null && block.getClass() == Block.class, "Fixture block must be a plain native Block");
        require(blockItem instanceof BlockItem nativeItem && nativeItem.getBlock() == block, "Incorrect BlockItem binding");
        require(new ItemStack(ingot).getMaxStackSize() == 32, "Item stack-size semantics changed");
        require(block.defaultBlockState().getDestroySpeed(server.overworld(), BlockPos.ZERO) == 2.0f,
                "Block hardness changed");
        require(block.getExplosionResistance() == 6.0f, "Block resistance changed");
        require(block.defaultBlockState().instrument() == NoteBlockInstrument.BASEDRUM, "Stone instrument changed");
        require(new ItemStack(ingot).is(TagKey.create(Registries.ITEM, id("bridge_ingots"))), "Item tag missing");
        require(block.defaultBlockState().is(TagKey.create(Registries.BLOCK, id("bridge_blocks"))), "Block tag missing");
        require(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE), "Mining adapter tag missing");
        require(new ItemStack(Items.IRON_PICKAXE).getDestroySpeed(block.defaultBlockState()) == Tiers.IRON.getSpeed(),
                "Stone mining behavior changed");
        AbstractContainerMenu menu = new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        var grid = new TransientCraftingContainer(menu, 3, 3);
        for (int slot = 0; slot < 9; slot++) grid.setItem(slot, new ItemStack(ingot));
        var recipe = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, server.overworld()).orElseThrow();
        require(recipe.getId().equals(blockId), "Wrong native crafting recipe matched");
        ItemStack result = recipe.assemble(grid, server.registryAccess());
        require(result.is(blockItem) && result.getCount() == 1, "Native recipe did not craft nine ingots into one block");
        grid.setItem(0, ItemStack.EMPTY);
        require(server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, server.overworld()).isEmpty(),
                "Incomplete recipe unexpectedly matched");
    }

    private static ResourceLocation id(String path) { return new ResourceLocation(LegacyBridgeProductProbe.FIXTURE_ID, path); }
}
