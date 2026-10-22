package dev.legacybridge.converter.report;

import java.util.Objects;

public record ToolInfo(String name, String version, String policyVersion) {
    public ToolInfo {
        name = requireText(name, "name");
        version = requireText(version, "version");
        policyVersion = requireText(policyVersion, "policyVersion");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

