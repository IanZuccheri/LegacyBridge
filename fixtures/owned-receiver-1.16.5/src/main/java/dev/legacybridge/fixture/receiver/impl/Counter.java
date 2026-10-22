package dev.legacybridge.fixture.receiver.impl;
import java.util.function.IntUnaryOperator;

public final class Counter {
    private int calls;
    public Counter() { }
    public IntUnaryOperator operation() { return this::score; }
    public int score(int count) { calls++; return count * 3 + 1; }
    public int calls() { return calls; }
}
