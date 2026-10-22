package dev.legacybridge.fixture.attributedurable;

import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Authored original Forge 36 registration graph; never executed by analysis or conversion. */
@Mod(ItemAttributeDurableContent.MOD_ID)
public final class ItemAttributeDurableContent {
    public static final String MOD_ID = "legacybridge_attr_durable";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> ATTRIBUTE_ITEM = ITEMS.register("bridge_durable_attribute_item", ItemAttributeDurableContent::createItem);

    public ItemAttributeDurableContent() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    private static Item createItem() {
        return new AttributeDurableItem(new Item.Properties().durability(128));
    }
}
