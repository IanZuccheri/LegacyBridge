package dev.legacybridge.converter.report;

public enum Severity {
    INFO(0),
    WARNING(1),
    ERROR(2),
    BLOCKER(3);

    private final int rank;

    Severity(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}

