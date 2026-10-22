package dev.legacybridge.converter.report;

import java.util.List;
import java.util.Objects;

public record FeatureMarker(
        String id,
        long occurrenceCount,
        List<String> evidence,
        boolean evidenceTruncated
) {
    public static final int EVIDENCE_LIMIT = 16;

    public FeatureMarker {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(evidence, "evidence");
        if (occurrenceCount < Math.max(1L, evidence.size())) {
            throw new IllegalArgumentException(
                    "occurrenceCount must cover at least one occurrence and every supplied evidence item");
        }
        List<String> sortedEvidence = evidence.stream()
                .map(value -> Objects.requireNonNull(value, "evidence must not contain null"))
                .distinct().sorted().toList();
        if (sortedEvidence.size() > EVIDENCE_LIMIT) {
            evidenceTruncated = true;
            sortedEvidence = sortedEvidence.subList(0, EVIDENCE_LIMIT);
        }
        evidence = List.copyOf(sortedEvidence);
    }

    /** Source-compatible form for detectors whose evidence list represents each observation. */
    public FeatureMarker(String id, List<String> evidence) {
        this(id, inferredOccurrenceCount(evidence), evidence, false);
    }

    private static long inferredOccurrenceCount(List<String> evidence) {
        Objects.requireNonNull(evidence, "evidence");
        return Math.max(1L, evidence.size());
    }
}
