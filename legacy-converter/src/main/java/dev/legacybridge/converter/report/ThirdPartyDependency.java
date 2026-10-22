package dev.legacybridge.converter.report;

import java.util.Objects;
import java.util.Set;

/** Declared mod dependency or bytecode-inferred package; inference is never a coordinate claim. */
public record ThirdPartyDependency(
        String identifier,
        String source,
        boolean mandatory,
        String versionRange
) {
    private static final Set<String> SOURCES = Set.of(
            "DECLARED_MOD",
            "INFERRED_PACKAGE",
            "MANIFEST_CLASS_PATH",
            "MANIFEST_CONTAINED_DEPS"
    );

    public ThirdPartyDependency {
        identifier = requireNonBlank(identifier, "identifier");
        source = requireNonBlank(source, "source");
        if (!SOURCES.contains(source)) {
            throw new IllegalArgumentException("source is not a supported dependency-evidence type");
        }
        versionRange = Objects.requireNonNullElse(versionRange, "");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
