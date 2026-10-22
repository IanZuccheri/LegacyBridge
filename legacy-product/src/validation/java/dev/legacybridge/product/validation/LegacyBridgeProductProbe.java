package dev.legacybridge.product.validation;

import net.minecraftforge.fml.common.Mod;

/** Separate QA mod. Never part of LegacyBridge.jar and inert only by refusing accidental installation. */
@Mod(LegacyBridgeProductProbe.MOD_ID)
public final class LegacyBridgeProductProbe {
    public static final String MOD_ID = "legacybridge_product_probe";
    public static final String FIXTURE_ID = "legacybridge_example";

    public LegacyBridgeProductProbe() {
        if (!Boolean.getBoolean("legacybridge.product.probe") && !Boolean.getBoolean("legacybridge.product.p4")
                && !Boolean.getBoolean("legacybridge.product.p5")) {
            throw new IllegalStateException("Test-only LegacyBridge probe requires an explicit product probe, P4 or P5 flag");
        }
    }
}
