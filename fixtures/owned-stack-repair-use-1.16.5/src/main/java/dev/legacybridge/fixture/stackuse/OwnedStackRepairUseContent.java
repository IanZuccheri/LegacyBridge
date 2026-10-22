package dev.legacybridge.fixture.stackuse;

import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Authored original registration graph; never execute during analysis or conversion. */
@Mod(OwnedStackRepairUseContent.MOD_ID)
public final class OwnedStackRepairUseContent {
    public static final String MOD_ID = "legacybridge_stack_use";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> STACK_ITEM = ITEMS.register("bridge_stack_use_item", OwnedStackRepairUseContent::createItem);

    public OwnedStackRepairUseContent() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }

    private static Item createItem() {
        return new StackRepairUseItem();
    }
}
