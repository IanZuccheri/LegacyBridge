package dev.legacybridge.fixture.constructors;

final class CountRule {
    private int calls;
    public int score(int count, int seed) { calls++; return seed * count + 1; }
    public int calls() { return calls; }
}
