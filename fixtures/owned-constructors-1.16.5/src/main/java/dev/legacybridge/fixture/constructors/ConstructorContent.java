package dev.legacybridge.fixture.constructors;

import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(ConstructorContent.MOD_ID)
public final class ConstructorContent {
    public static final String MOD_ID = "legacybridge_constructors";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> TOKEN = ITEMS.register("count_token", DelegatingItem::new);
    public ConstructorContent() { ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus()); }
}
