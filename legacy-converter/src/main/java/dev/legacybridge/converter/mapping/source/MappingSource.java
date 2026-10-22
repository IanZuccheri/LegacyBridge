package dev.legacybridge.converter.mapping.source;

import java.util.Objects;

/** Provenance for input bytes; intentionally contains no local absolute path. */
public record MappingSource(String id, String sha256, String origin) {
    public MappingSource {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(origin, "origin");
        if (id.isBlank() || !sha256.matches("[a-f0-9]{64}") || origin.isBlank()) {
            throw new IllegalArgumentException("Mapping provenance must have an id, SHA-256 and origin");
        }
    }
}
