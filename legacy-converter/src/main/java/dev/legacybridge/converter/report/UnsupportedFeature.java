package dev.legacybridge.converter.report;

import java.util.List;
import java.util.Objects;

/** A detected requirement for which the current runtime has no reviewed adapter. */
public record UnsupportedFeature(
        String id,
        long occurrenceCount,
        List<String> evidence,
        boolean evidenceTruncated
) {
    public static final int EVIDENCE_LIMIT = 16;

    public UnsupportedFeature {
        id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (occurrenceCount < 1) {
            throw new IllegalArgumentException("occurrenceCount must be positive");
        }
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.size() > EVIDENCE_LIMIT) {
            throw new IllegalArgumentException("evidence sample limit exceeded");
        }
        evidence = evidence.stream()
                .map(value -> Objects.requireNonNull(value, "evidence must not contain null"))
                .distinct().sorted().toList();
        if (evidence.size() > occurrenceCount) {
            throw new IllegalArgumentException("evidence cannot exceed occurrences");
        }
    }
}
