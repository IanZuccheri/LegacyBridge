package dev.legacybridge.fixture.composed;

import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Authored Forge 36 fixture. Original executable registration must not be replaced. */
@Mod(ComposedContent.MOD_ID)
public final class ComposedContent {
    public static final String MOD_ID = "legacybridge_composed";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> TOKEN = ITEMS.register("count_token", ComposedItem::new);
    public ComposedContent() { ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus()); }
}
