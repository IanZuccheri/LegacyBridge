package dev.legacybridge.converter.report;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, deterministic result of static analysis. A successful analysis
 * is deliberately independent from conversion and execution support.
 */
public record CompatibilityReport(
        int schemaVersion,
        LegacyAnalysisSummary analysis,
        ToolInfo tool,
        InputFingerprint input,
        ArchiveSummary archive,
        MetadataSummary metadata,
        BytecodeSummary bytecode,
        List<FeatureMarker> markers,
        List<Finding> findings,
        Assessment assessment
) {
    public static final int SCHEMA_VERSION = 2;

    private static final Comparator<Finding> FINDING_ORDER = Comparator
            .comparingInt((Finding finding) -> finding.severity().rank()).reversed()
            .thenComparing(Finding::code)
            .thenComparing(Finding::category)
            .thenComparing(Finding::message);

    public CompatibilityReport {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("schemaVersion must be " + SCHEMA_VERSION);
        }
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(archive, "archive");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(bytecode, "bytecode");
        Objects.requireNonNull(assessment, "assessment");
        Objects.requireNonNull(markers, "markers");
        markers = markers.stream()
                .map(value -> Objects.requireNonNull(value, "markers must not contain null"))
                .sorted(Comparator.comparing(FeatureMarker::id)).toList();
        Objects.requireNonNull(findings, "findings");
        findings = findings.stream()
                .map(value -> Objects.requireNonNull(value, "findings must not contain null"))
                .sorted(FINDING_ORDER).toList();
    }

    /** Source-compatible convenience constructor for tests and internal callers. */
    public CompatibilityReport(
            int schemaVersion,
            ToolInfo tool,
            InputFingerprint input,
            ArchiveSummary archive,
            MetadataSummary metadata,
            BytecodeSummary bytecode,
            List<FeatureMarker> markers,
            List<Finding> findings,
            Assessment assessment
    ) {
        this(schemaVersion, LegacyAnalysisSummary.empty(), tool, input, archive, metadata,
                bytecode, markers, findings, assessment);
    }

    public String modId() { return analysis.modId(); }
    public String modVersion() { return analysis.modVersion(); }
    public int riskScore() { return analysis.riskScore(); }
    public Complexity complexity() { return analysis.complexity(); }

    public boolean hasAtLeast(Severity threshold) {
        Objects.requireNonNull(threshold, "threshold");
        return findings.stream().anyMatch(finding -> finding.severity().rank() >= threshold.rank());
    }
}
