package dev.legacybridge.fixture.defaults.impl;
import dev.legacybridge.fixture.defaults.api.Scoring;

public final class Counter implements Scoring {
    private int calls;
    public Counter() { }
    @Override public int bias() { return 1; }
    @Override public int score(int count) { calls++; return Scoring.super.score(count) + 2; }
    public int calls() { return calls; }
}
