package dev.legacybridge.fixture.clientconfig;
import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(ClientConfigContent.MOD_ID)
public final class ClientConfigContent {
    public static final String MOD_ID = "legacybridge_clientconfig";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> TOKEN = ITEMS.register("config_token", ClientConfigItem::new);
    public ClientConfigContent() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        if (FMLEnvironment.dist == Dist.CLIENT) ClientConfigHooks.install();
    }
}
