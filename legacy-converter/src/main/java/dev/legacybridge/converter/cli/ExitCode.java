package dev.legacybridge.converter.cli;

public enum ExitCode {
    ANALYSIS_COMPLETE(0),
    FINDINGS_REQUIRE_REVIEW(1),
    INVALID_USAGE(2),
    INPUT_REJECTED(3),
    REPORT_WRITE_FAILED(4),
    INTERNAL_ERROR(70);

    private final int value;

    ExitCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}

