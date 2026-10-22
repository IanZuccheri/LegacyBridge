package dev.legacybridge.fixture.datapack;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Actual Forge 36 fixture: no compatibility stubs or mod-code execution during conversion. */
@Mod(DatapackContent.MOD_ID)
public final class DatapackContent {
    public static final String MOD_ID = "legacybridge_datapack";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);

    public static final RegistryObject<Item> BRIDGE_INGOT = ITEMS.register("bridge_ingot",
            () -> new Item(new Item.Properties().tab(ItemGroup.TAB_MISC).stacksTo(32)));

    public static final RegistryObject<Block> BRIDGE_BLOCK = BLOCKS.register("bridge_block",
            () -> new Block(AbstractBlock.Properties.of(Material.STONE).strength(2.0F, 6.0F)));

    public static final RegistryObject<Item> BRIDGE_BLOCK_ITEM = ITEMS.register("bridge_block",
            () -> new BlockItem(BRIDGE_BLOCK.get(), new Item.Properties().tab(ItemGroup.TAB_BUILDING_BLOCKS)));

    public DatapackContent() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
