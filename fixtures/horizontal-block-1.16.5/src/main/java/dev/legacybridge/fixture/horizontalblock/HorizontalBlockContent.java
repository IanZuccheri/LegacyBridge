package dev.legacybridge.fixture.horizontalblock;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Original Forge 36 registration graph; analysis and conversion never execute this class. */
@Mod(HorizontalBlockContent.MOD_ID)
public final class HorizontalBlockContent {
    public static final String MOD_ID = "legacybridge_horizontal_block";
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Block> BLOCK = BLOCKS.register("bridge_block", HorizontalBlockContent::createBlock);
    public static final RegistryObject<BlockItem> BLOCK_ITEM = ITEMS.register("bridge_block", HorizontalBlockContent::createBlockItem);

    public HorizontalBlockContent() {
        BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    private static Block createBlock() {
        return new HorizontalContentBlock(AbstractBlock.Properties.of(Material.STONE).strength(2.0F, 6.0F));
    }

    private static BlockItem createBlockItem() {
        return new BlockItem(BLOCK.get(), new Item.Properties().stacksTo(32));
    }
}
