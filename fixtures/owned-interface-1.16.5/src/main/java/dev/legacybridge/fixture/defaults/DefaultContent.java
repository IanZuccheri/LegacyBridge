package dev.legacybridge.fixture.defaults;
import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(DefaultContent.MOD_ID)
public final class DefaultContent {
    public static final String MOD_ID = "legacybridge_defaults";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> TOKEN = ITEMS.register("count_token", DefaultItem::new);
    public DefaultContent() { ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus()); }
}
