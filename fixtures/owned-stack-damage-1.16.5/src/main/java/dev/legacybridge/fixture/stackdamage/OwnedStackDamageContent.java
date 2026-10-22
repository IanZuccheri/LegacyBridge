package dev.legacybridge.fixture.stackdamage;

import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Authored original registration graph; never execute during analysis or conversion. */
@Mod(OwnedStackDamageContent.MOD_ID)
public final class OwnedStackDamageContent {
    public static final String MOD_ID = "legacybridge_stack_damage";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> STACK_ITEM = ITEMS.register("bridge_stack_item", OwnedStackDamageContent::createItem);

    public OwnedStackDamageContent() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    private static Item createItem() {
        return new StackDamageItem();
    }
}
