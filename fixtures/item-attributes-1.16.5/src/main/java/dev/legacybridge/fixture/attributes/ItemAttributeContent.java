package dev.legacybridge.fixture.attributes;

import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Authored original Forge 36 registration graph; never executed by analysis or conversion. */
@Mod(ItemAttributeContent.MOD_ID)
public final class ItemAttributeContent {
    public static final String MOD_ID = "legacybridge_attributes";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> ATTRIBUTE_ITEM = ITEMS.register("bridge_attribute_item", ItemAttributeContent::createItem);

    public ItemAttributeContent() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    private static Item createItem() {
        return new AttributeContentItem(new Item.Properties());
    }
}
