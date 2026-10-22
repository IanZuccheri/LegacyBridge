package dev.legacybridge.converter.metadata;

import dev.legacybridge.converter.report.Finding;
import dev.legacybridge.converter.report.MetadataSummary;
import dev.legacybridge.converter.report.Severity;

import java.util.List;
import java.util.Objects;

public record MetadataAnalysis(MetadataSummary summary, List<Finding> findings) {
    public MetadataAnalysis {
        Objects.requireNonNull(summary, "summary");
        findings = List.copyOf(findings);
    }

    /** True only when TOML parsing and the supported Forge metadata shape both validate. */
    public boolean valid() {
        return summary.parsed() && findings.stream()
                .noneMatch(finding -> finding.severity().rank() >= Severity.ERROR.rank());
    }
}
