package dev.legacybridge.converter.report;

import java.util.Objects;

public record DependencyDeclaration(
        String ownerModId,
        String modId,
        boolean mandatoryDeclared,
        boolean mandatory,
        String versionRange,
        String ordering,
        String side
) {
    public DependencyDeclaration {
        ownerModId = Objects.requireNonNullElse(ownerModId, "");
        modId = Objects.requireNonNullElse(modId, "");
        versionRange = Objects.requireNonNullElse(versionRange, "");
        ordering = Objects.requireNonNullElse(ordering, "");
        side = Objects.requireNonNullElse(side, "");
    }
}
