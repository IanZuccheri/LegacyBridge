package dev.legacybridge.fixture.logic;

import java.util.function.IntUnaryOperator;

/** Mod-owned static initialization, a static method reference and a primitive-capturing lambda. */
public final class LogicMath {
    private static int initializerRuns;
    private static final int SEED = initializeSeed();
    private static final IntUnaryOperator DOUBLE = LogicMath::twice;

    private LogicMath() {
    }

    private static int initializeSeed() {
        initializerRuns++;
        return 7;
    }

    public static int twice(int value) {
        return value * 2;
    }

    public static int applyMethodReference(int value) {
        return DOUBLE.applyAsInt(value);
    }

    public static IntUnaryOperator shift(int offset) {
        return value -> value + offset + SEED;
    }

    public static int seed() {
        return SEED;
    }

    public static int initializationCount() {
        return initializerRuns;
    }
}
