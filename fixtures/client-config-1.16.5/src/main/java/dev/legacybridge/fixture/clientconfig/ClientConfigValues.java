package dev.legacybridge.fixture.clientconfig;

import net.minecraftforge.common.ForgeConfigSpec;

/** Native COMMON spec only: no paths, manual I/O, reload listener, or synchronization claim. */
public final class ClientConfigValues {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.IntValue FACTOR;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ENABLED = builder.define("enabled", true);
        FACTOR = builder.defineInRange("factor", 3, 1, 8);
        SPEC = builder.build();
    }

    private ClientConfigValues() {
    }

    /** Call only after Forge has loaded the COMMON spec. */
    public static boolean isEnabled() {
        return ENABLED.get();
    }

    /** Call only after Forge has loaded the COMMON spec. */
    public static int factor() {
        return FACTOR.get();
    }
}
