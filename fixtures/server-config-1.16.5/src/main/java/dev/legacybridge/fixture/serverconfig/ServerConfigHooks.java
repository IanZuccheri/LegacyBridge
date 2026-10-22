package dev.legacybridge.fixture.serverconfig;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Logical-server session snapshot. No client synchronization or reload handler. */
public final class ServerConfigHooks {
    private static boolean active;
    private static boolean enabled;
    private static int factor;
    private static int starts;
    private static int stops;
    private ServerConfigHooks() {}
    public static void install() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfigValues.SPEC);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, FMLServerStartedEvent.class, ServerConfigHooks::started);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, FMLServerStoppedEvent.class, ServerConfigHooks::stopped);
    }
    private static void started(FMLServerStartedEvent event) {
        enabled = ServerConfigValues.isEnabled();
        factor = ServerConfigValues.factor();
        active = true;
        starts++;
    }
    private static void stopped(FMLServerStoppedEvent event) {
        active = false;
        enabled = false;
        factor = 0;
        stops++;
    }
    public static boolean active() { return active; }
    public static boolean enabled() { return enabled; }
    public static int factor() { return factor; }
    public static int starts() { return starts; }
    public static int stops() { return stops; }
}
