package dev.legacybridge.fixture.serverconfig;
import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(ServerConfigContent.MOD_ID)
public final class ServerConfigContent {
    public static final String MOD_ID = "legacybridge_serverconfig";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> TOKEN = ITEMS.register("config_token", ServerConfigItem::new);
    public ServerConfigContent() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ServerConfigHooks.install();
    }
}
