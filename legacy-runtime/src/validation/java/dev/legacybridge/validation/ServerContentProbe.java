package dev.legacybridge.validation;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.BlockTags;
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
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import static dev.legacybridge.validation.LegacyBridgeValidation.require;

@Mod.EventBusSubscriber(modid = LegacyBridgeValidation.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerContentProbe {
    private ServerContentProbe() { }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("legacybridge.validation")) return;
        var server = event.getServer();
        try {
            var fixture = ModList.get().getModContainerById(LegacyBridgeValidation.FIXTURE_ID).orElseThrow();
            require(fixture.getModInfo().getVersion().toString().equals("1.0.0"), "Converted mod version was not preserved");
            require(ModList.get().isLoaded("legacybridge_runtime"), "Runtime mod is not loaded");
            ResourceLocation ingotId = id("bridge_ingot");
            ResourceLocation blockId = id("bridge_block");
            require(ForgeRegistries.ITEMS.containsKey(ingotId), "Converted item is absent");
            require(ForgeRegistries.BLOCKS.containsKey(blockId), "Converted block is absent");
            require(ForgeRegistries.ITEMS.containsKey(blockId), "Converted BlockItem is absent");
            Item ingot = ForgeRegistries.ITEMS.getValue(ingotId);
            Block block = ForgeRegistries.BLOCKS.getValue(blockId);
            Item blockItem = ForgeRegistries.ITEMS.getValue(blockId);
            require(ingot != null && ingot.getClass() == Item.class, "Fixture ingot must be a plain native Item");
            require(block != null && block.getClass() == Block.class, "Fixture block must be a plain native Block");
            require(blockItem instanceof BlockItem && ((BlockItem) blockItem).getBlock() == block, "BlockItem references the wrong block");
            require(new ItemStack(ingot).getMaxStackSize() == 32, "Item max stack size changed");
            require(block.defaultBlockState().getDestroySpeed(server.overworld(), BlockPos.ZERO) == 2.0f, "Block hardness changed");
            require(block.getExplosionResistance() == 6.0f, "Block resistance changed");
            require(block.defaultBlockState().instrument() == NoteBlockInstrument.BASEDRUM,
                    "Plain stone lost the legacy BASEDRUM note-block instrument");
            require(new ItemStack(ingot).is(TagKey.create(Registries.ITEM, id("bridge_ingots"))), "Item tag was not loaded");
            require(block.defaultBlockState().is(TagKey.create(Registries.BLOCK, id("bridge_blocks"))), "Block tag was not loaded");
            require(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE), "Stone material mining tag adapter was not applied");
            require(new ItemStack(Items.IRON_PICKAXE).getDestroySpeed(block.defaultBlockState()) == Tiers.IRON.getSpeed(),
                    "Iron pickaxe lost its legacy stone-material mining speed");

            AbstractContainerMenu menu = new AbstractContainerMenu(null, 0) {
                @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
                @Override public boolean stillValid(Player player) { return true; }
            };
            TransientCraftingContainer grid = new TransientCraftingContainer(menu, 3, 3);
            for (int slot = 0; slot < 9; slot++) grid.setItem(slot, new ItemStack(ingot));
            var recipe = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, server.overworld()).orElseThrow();
            require(recipe.getId().equals(blockId), "Unexpected crafting recipe matched");
            ItemStack result = recipe.assemble(grid, server.registryAccess());
            require(result.is(blockItem) && result.getCount() == 1, "Native RecipeManager did not craft nine ingots into one block");
            grid.setItem(0, ItemStack.EMPTY);
            require(server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, server.overworld()).isEmpty(),
                    "Incomplete nine-ingot recipe unexpectedly matched");
            LogUtils.getLogger().info("LEGACYBRIDGE_SERVER_VALIDATION_OK modId={} version=1.0.0 itemStack=32 blockHardness=2 blockResistance=6 instrument=BASEDRUM tags=3 ironPickaxe=legacySpeed recipe=9_to_1 negativeRecipe=passed",
                    LegacyBridgeValidation.FIXTURE_ID);
            server.execute(() -> server.halt(false));
        } catch (Throwable failure) {
            LogUtils.getLogger().error("LEGACYBRIDGE_SERVER_VALIDATION_FAILED", failure);
            throw new IllegalStateException("Converted fixture server validation failed", failure);
        }
    }

    private static ResourceLocation id(String path) { return new ResourceLocation(LegacyBridgeValidation.FIXTURE_ID, path); }
}
