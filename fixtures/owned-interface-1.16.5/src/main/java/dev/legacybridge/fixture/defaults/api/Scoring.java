package dev.legacybridge.fixture.defaults.api;

public interface Scoring {
    int bias();
    default int score(int count) { return count * 3 + bias(); }
}
