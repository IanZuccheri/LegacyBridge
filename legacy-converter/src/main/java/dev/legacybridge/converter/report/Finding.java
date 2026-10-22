package dev.legacybridge.converter.report;

import java.util.List;
import java.util.Objects;

public record Finding(
        String code,
        Severity severity,
        String category,
        String message,
        List<String> evidence
) {
    public Finding {
        code = requireNonBlank(code, "code");
        Objects.requireNonNull(severity, "severity");
        category = requireNonBlank(category, "category");
        message = requireNonBlank(message, "message");
        Objects.requireNonNull(evidence, "evidence");
        evidence = evidence.stream()
                .map(value -> Objects.requireNonNull(value, "evidence must not contain null"))
                .distinct().sorted().toList();
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
