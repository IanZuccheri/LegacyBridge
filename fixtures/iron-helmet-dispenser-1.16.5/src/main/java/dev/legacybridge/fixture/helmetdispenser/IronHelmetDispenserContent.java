package dev.legacybridge.fixture.helmetdispenser;

import net.minecraft.dispenser.IBlockSource;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Original finite endpoint fixture; analysis and conversion never execute this class. */
@Mod(IronHelmetDispenserContent.MOD_ID)
public final class IronHelmetDispenserContent {
    public static final String MOD_ID = "legacybridge_helmet_dispenser";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> HELMET = ITEMS.register("bridge_helmet", IronHelmetDispenserContent::createHelmet);

    public IronHelmetDispenserContent() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    private static Item createHelmet() {
        return new ArmorItem(ArmorMaterial.IRON, EquipmentSlotType.HEAD, new Item.Properties());
    }

    /** Native source first-result behavior, including receivers not admitted by the target contract. */
    public static boolean dispense(IBlockSource source, ItemStack stack) {
        return ArmorItem.dispenseArmor(source, stack);
    }
}
