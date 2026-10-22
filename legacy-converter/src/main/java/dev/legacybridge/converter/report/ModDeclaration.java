package dev.legacybridge.converter.report;

import java.util.Objects;

public record ModDeclaration(String modId, String version, String displayName) {
    public ModDeclaration {
        modId = Objects.requireNonNullElse(modId, "");
        version = Objects.requireNonNullElse(version, "");
        displayName = Objects.requireNonNullElse(displayName, "");
    }
}

