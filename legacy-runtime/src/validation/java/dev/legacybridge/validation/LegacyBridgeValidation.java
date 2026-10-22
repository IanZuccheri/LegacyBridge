package dev.legacybridge.validation;

import net.minecraftforge.fml.common.Mod;

/** Test-only mod, included in validation run source sets and never in the player runtime JAR. */
@Mod(LegacyBridgeValidation.MOD_ID)
public final class LegacyBridgeValidation {
    public static final String MOD_ID = "legacybridge_validation";
    public static final String FIXTURE_ID = "legacybridge_example";

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("LEGACYBRIDGE_VALIDATION_FAILED: " + message);
    }
}
