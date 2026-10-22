package dev.legacybridge.runtime;

import java.util.Objects;

/** Typed metadata for an actually implemented and enabled runtime capability. */
public record RuntimeCapability(String id, RuntimeCapabilityType type) {
    public RuntimeCapability {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
