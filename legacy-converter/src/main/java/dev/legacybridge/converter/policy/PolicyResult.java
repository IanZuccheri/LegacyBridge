package dev.legacybridge.converter.policy;

import dev.legacybridge.converter.report.Assessment;
import dev.legacybridge.converter.report.Finding;

import java.util.List;

public record PolicyResult(Assessment assessment, List<Finding> findings) {
    public PolicyResult {
        findings = List.copyOf(findings);
    }
}

