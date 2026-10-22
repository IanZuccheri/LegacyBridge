package dev.legacybridge.fixture.suite;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.EventPriority;
/** Called on native loading/watcher threads. No world access, thread creation or scheduling. */
public final class ConfigEvents {
    private static volatile boolean enabled;
    private static volatile int factor;
    private static volatile int loads;
    private static volatile int reloads;
    private ConfigEvents() {}
    public static void install() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EventPriority.NORMAL,false,ModConfig.Loading.class,ConfigEvents::loading);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(EventPriority.NORMAL,false,ModConfig.Reloading.class,ConfigEvents::reloading);
    }
    private static void loading(ModConfig.Loading event) {
        if (event.getConfig().getSpec() != SuiteConfig.SPEC || event.getConfig().getType() != ModConfig.Type.COMMON) return;
        enabled = SuiteConfig.isEnabled(); factor = SuiteConfig.factor(); loads++;
    }
    private static void reloading(ModConfig.Reloading event) {
        if (event.getConfig().getSpec() != SuiteConfig.SPEC || event.getConfig().getType() != ModConfig.Type.COMMON) return;
        enabled = SuiteConfig.isEnabled(); factor = SuiteConfig.factor(); reloads++;
    }
    public static boolean enabled() { return enabled; }
    public static int factor() { return factor; }
    public static int loads() { return loads; }
    public static int reloads() { return reloads; }
}
