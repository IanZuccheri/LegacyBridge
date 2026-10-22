package dev.legacybridge.converter.config;

import java.util.List;
import java.util.Objects;

/** Lexical access-transformer facts only; rules are deliberately not resolved. */
public record AccessTransformerSummary(
        String path,
        boolean parsed,
        int ruleCount,
        List<String> normalizedRuleSamples,
        boolean samplesTruncated
) {
    public AccessTransformerSummary {
        Objects.requireNonNull(path, "path");
        normalizedRuleSamples = List.copyOf(normalizedRuleSamples);
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (ruleCount < 0) {
            throw new IllegalArgumentException("ruleCount must be non-negative");
        }
    }

    static AccessTransformerSummary unreadable(String path) {
        return new AccessTransformerSummary(path, false, 0, List.of(), false);
    }
}
