package dev.legacybridge.converter.report;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Bounded facts parsed from a non-executable configuration artifact. */
public record StaticArtifact(
        String path,
        boolean parsed,
        long declarationCount,
        Map<String, List<String>> attributes,
        List<String> diagnostics,
        boolean truncated,
        boolean partial
) {
    public StaticArtifact {
        path = Objects.requireNonNull(path, "path");
        if (path.isBlank()) throw new IllegalArgumentException("path must not be blank");
        if (declarationCount < 0) throw new IllegalArgumentException("declarationCount must not be negative");
        Objects.requireNonNull(attributes, "attributes");
        TreeMap<String, List<String>> copy = new TreeMap<>();
        attributes.forEach((key, values) -> {
            String checkedKey = Objects.requireNonNull(key, "attribute key");
            List<String> checkedValues = Objects.requireNonNull(values, "attribute values").stream()
                    .map(value -> Objects.requireNonNull(value, "attribute values must not contain null"))
                    .distinct().sorted().toList();
            copy.put(checkedKey, checkedValues);
        });
        attributes = Collections.unmodifiableMap(copy);
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = diagnostics.stream()
                .map(value -> Objects.requireNonNull(value, "diagnostics must not contain null"))
                .distinct().sorted().toList();
    }
}
