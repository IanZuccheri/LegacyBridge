package dev.legacybridge.fixture.clientconfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Native startup snapshot, not reload handling, synchronization or gameplay. */
public final class ClientConfigHooks {
    private static boolean enabled;
    private static int factor;
    private static int loads;
    private ClientConfigHooks() {}
    public static void install() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfigValues.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
            EventPriority.NORMAL, false, FMLClientSetupEvent.class, ClientConfigHooks::setup);
    }
    private static void setup(FMLClientSetupEvent event) { event.enqueueWork(ClientConfigHooks::read); }
    private static void read() {
        enabled = ClientConfigValues.isEnabled();
        factor = ClientConfigValues.factor();
        loads++;
    }
    public static boolean enabled() { return enabled; }
    public static int factor() { return factor; }
    public static int loads() { return loads; }
}
