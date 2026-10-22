package dev.legacybridge.fixture.ironhelmet;

import net.minecraft.item.Item;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ArmorItem;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Original Forge 36 fixture for inherited armor-semantics auditing.
 * Constructor similarity does not approve occupied-slot swaps or new equipment-stat effects.
 * The analyzer and converter must never execute this class.
 */
@Mod(IronHelmetContent.MOD_ID)
public final class IronHelmetContent {
    public static final String MOD_ID = "legacybridge_iron_helmet";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> HELMET = ITEMS.register("bridge_helmet", IronHelmetContent::createHelmet);

    public IronHelmetContent() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    private static Item createHelmet() {
        return new ArmorItem(ArmorMaterial.IRON, EquipmentSlotType.HEAD, new Item.Properties());
    }
}
