package dev.legacybridge.runtime;

import dev.legacybridge.runtime.compat.registry.SimpleContentAdapter;

/** Compatibility facade for the earlier generated bootstrap; new conversions bind the concrete adapter. */
public final class SimpleContentRuntime {
    public static final String CAPABILITY_ID = SimpleContentAdapter.CAPABILITY_ID;

    private SimpleContentRuntime() { }

    public static void register(String modId, ClassLoader loader, String planResource) {
        SimpleContentAdapter.register(modId, loader, planResource);
    }
}
