package dev.legacybridge.fixture.cohort.library;

/** Authored observable API; no platform calls, registration or shared mutable state. */
public final class Score {
    private final int offset;
    public Score(int offset) { this.offset = offset; }
    public int compute(int input) {
        if (input < 0) throw new IllegalArgumentException("negative input");
        return input * 3 + offset;
    }
}
