package dev.legacybridge.fixture.suite;
import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(SuiteContent.MOD_ID)
public final class SuiteContent {
    public static final String MOD_ID = "legacybridge_suite";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> TOKEN = ITEMS.register("suite_token", SuiteItem::new);
    private static Item observedItem;
    public static Item observedItem() { return observedItem; }
    public SuiteContent() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, SuiteConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
            EventPriority.NORMAL, false, FMLCommonSetupEvent.class, SuiteContent::setup);
        EventFlow.install();
        ConfigEvents.install();
    }
    private static void setup(FMLCommonSetupEvent event) { event.enqueueWork(SuiteContent::finish); }
    private static void finish() { observedItem = TOKEN.get(); EventFlow.run(); }
}
