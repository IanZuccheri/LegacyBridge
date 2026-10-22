package dev.legacybridge.fixture.plainblock;

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
@Mod(PlainBlockContent.MOD_ID)
public final class PlainBlockContent {
    public static final String MOD_ID = "legacybridge_plain_block";
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Block> BLOCK = BLOCKS.register("bridge_block", PlainBlockContent::createBlock);
    public static final RegistryObject<BlockItem> BLOCK_ITEM = ITEMS.register("bridge_block", PlainBlockContent::createBlockItem);

    public PlainBlockContent() {
        BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    private static Block createBlock() {
        return new Block(AbstractBlock.Properties.of(Material.STONE).strength(2.0F, 6.0F));
    }

    private static BlockItem createBlockItem() {
        return new BlockItem(BLOCK.get(), new Item.Properties().stacksTo(32));
    }
}
