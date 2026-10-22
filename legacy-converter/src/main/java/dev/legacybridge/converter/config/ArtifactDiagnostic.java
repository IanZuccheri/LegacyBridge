package dev.legacybridge.converter.config;

import java.util.Objects;

/** A bounded, inert parsing diagnostic for an archive configuration artifact. */
public record ArtifactDiagnostic(
        String code,
        ArtifactDiagnosticSeverity severity,
        String path,
        String message
) implements Comparable<ArtifactDiagnostic> {
    public ArtifactDiagnostic {
        code = requireText(code, "code");
        severity = Objects.requireNonNull(severity, "severity");
        path = requireText(path, "path");
        message = requireText(message, "message");
    }

    @Override
    public int compareTo(ArtifactDiagnostic other) {
        int byPath = path.compareTo(other.path);
        if (byPath != 0) {
            return byPath;
        }
        int byCode = code.compareTo(other.code);
        return byCode != 0 ? byCode : message.compareTo(other.message);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
