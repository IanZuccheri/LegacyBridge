package dev.legacybridge.fixture.logic;

/** Original superclass with observable protected instance state and shared construction state. */
public class BaseRule {
    private static int instances;
    protected final int bias;
    protected int calls;

    public BaseRule(int bias) {
        this.bias = bias;
        instances++;
    }

    public int score(int input) {
        calls++;
        if (input < 0) {
            throw new IllegalArgumentException("negative score input");
        }
        return input + bias;
    }

    public int callCount() {
        return calls;
    }

    public static int instanceCount() {
        return instances;
    }
}
