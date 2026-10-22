package dev.legacybridge.converter.report;

import java.util.Objects;

public record Assessment(
        AnalysisStatus analysisStatus,
        SourceProfile sourceProfile,
        ConversionStatus conversionStatus,
        ExecutionStatus executionStatus
) {
    public Assessment {
        Objects.requireNonNull(analysisStatus, "analysisStatus");
        Objects.requireNonNull(sourceProfile, "sourceProfile");
        Objects.requireNonNull(conversionStatus, "conversionStatus");
        Objects.requireNonNull(executionStatus, "executionStatus");
    }
}
