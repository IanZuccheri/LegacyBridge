package dev.legacybridge.fixture.receiver;
import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(ReceiverContent.MOD_ID)
public final class ReceiverContent {
    public static final String MOD_ID = "legacybridge_receiver";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> TOKEN = ITEMS.register("count_token", ReceiverItem::new);
    public ReceiverContent() { ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus()); }
}
