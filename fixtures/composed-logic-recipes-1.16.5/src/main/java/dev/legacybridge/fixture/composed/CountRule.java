package dev.legacybridge.fixture.composed;

interface CountRule {
    int score(int count);
    int calls();
}

final class OddRule implements CountRule {
    private int calls;
    public int score(int count) { calls++; return 3 * count + 1; }
    public int calls() { return calls; }
}
