package dev.legacybridge.validation.lifecycle;

/** Explicit test expectations only; these properties never modify Forge config files or values. */
final class ProbeExpectations {
    final boolean enabled;
    final int factor;

    private ProbeExpectations(boolean enabled, int factor) {
        this.enabled = enabled;
        this.factor = factor;
    }

    static ProbeExpectations read() {
        return parse(System.getProperty("legacybridge.probe.expectedEnabled", "true"),
                System.getProperty("legacybridge.probe.expectedFactor", "3"));
    }

    static ProbeExpectations parse(String enabled, String factor) {
        if (!"true".equals(enabled) && !"false".equals(enabled)) {
            throw new IllegalArgumentException("expectedEnabled must be exactly true or false");
        }
        if (factor == null || !factor.matches("[1-8]")) {
            throw new IllegalArgumentException("expectedFactor must be an integer in 1..8");
        }
        return new ProbeExpectations("true".equals(enabled), Integer.parseInt(factor));
    }
}
