package dev.legacybridge.fixture.food;

import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Genuine original Forge 36 food-only entrypoint, never executed by analysis or conversion. */
@Mod(FoodContent.MOD_ID)
public final class FoodContent {
    public static final String MOD_ID = "legacybridge_food";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> RATION = ITEMS.register("ration", RationItem::new);

    public FoodContent() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
