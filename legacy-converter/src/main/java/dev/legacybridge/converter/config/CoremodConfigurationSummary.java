package dev.legacybridge.converter.config;

import java.util.List;
import java.util.Objects;

/** Declarative contents of META-INF/coremods.json. */
public record CoremodConfigurationSummary(
        String path,
        boolean parsed,
        int declaredCoremodCount,
        List<CoremodDeclaration> coremods,
        boolean samplesTruncated,
        boolean partial
) {
    public CoremodConfigurationSummary {
        Objects.requireNonNull(path, "path");
        coremods = List.copyOf(coremods);
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (declaredCoremodCount < 0) {
            throw new IllegalArgumentException("declaredCoremodCount must be non-negative");
        }
    }

    static CoremodConfigurationSummary unreadable(String path) {
        return new CoremodConfigurationSummary(path, false, 0, List.of(), false, false);
    }
}
