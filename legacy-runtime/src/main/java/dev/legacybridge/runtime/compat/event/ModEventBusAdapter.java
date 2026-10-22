package dev.legacybridge.runtime.compat.event;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Legacy API: {@code FMLJavaModLoadingContext.get().getModEventBus()} during the legacy mod constructor.
 * Target API: {@code FMLJavaModLoadingContext.get().getModEventBus()} in Forge 47's active mod context.
 * Transformation: checks preserved mod identity/version and returns that mod's target lifecycle bus.
 * Behavior differences: only the generated constructor attaches registrations; arbitrary old listeners are not replayed.
 * Known limitations: constructor context only; custom buses, subscribers and delayed registration are unsupported.
 */
public final class ModEventBusAdapter {
    private ModEventBusAdapter() { }

    public static IEventBus requireContext(String modId, String version) {
        if (modId == null || !modId.matches("[a-z][a-z0-9_]{1,63}") || version == null || version.isBlank()) {
            throw new IllegalArgumentException("Mod event bus adapter requires explicit mod identity and version");
        }
        var active = ModLoadingContext.get().getActiveContainer();
        if (active == null || !modId.equals(active.getModId()) || !version.equals(active.getModInfo().getVersion().toString())) {
            throw new IllegalStateException("Adapter must execute in the matching converted mod constructor context");
        }
        return FMLJavaModLoadingContext.get().getModEventBus();
    }
}
