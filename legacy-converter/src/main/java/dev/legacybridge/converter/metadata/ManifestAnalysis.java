package dev.legacybridge.converter.metadata;

import dev.legacybridge.converter.report.FeatureMarker;
import dev.legacybridge.converter.report.Finding;

import java.util.List;

public record ManifestAnalysis(List<FeatureMarker> markers, List<Finding> findings) {
    public ManifestAnalysis {
        markers = List.copyOf(markers);
        findings = List.copyOf(findings);
    }
}

