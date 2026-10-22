package dev.legacybridge.converter.service;

import java.nio.file.Path;
import java.util.Objects;

/** Explicit immutable selection for read-only inspection; not a conversion or Apply capability. */
public record PreservedArtifactRequest(Path original, String originalSha256, Path candidate,
                                       String candidateSha256, MappingData mappingData) {
    public PreservedArtifactRequest {
        original = Objects.requireNonNull(original).toAbsolutePath().normalize();
        candidate = Objects.requireNonNull(candidate).toAbsolutePath().normalize();
        Objects.requireNonNull(mappingData);
        for (String hash : new String[]{originalSha256, candidateSha256})
            if (hash == null || !hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Both exact selected SHA-256 values are required");
        if (original.equals(candidate)) throw new IllegalArgumentException("Original and candidate must be distinct files");
    }
}
