package dev.legacybridge.converter.service;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Explicit immutable selection for a read-only diagnostic. No destination or installation authority. */
public record IronHelmetInspectionRequest(Path input, String inputSha256, MappingData mappingData) {
    public IronHelmetInspectionRequest {
        input = Objects.requireNonNull(input, "input").toAbsolutePath().normalize();
        inputSha256 = Objects.requireNonNull(inputSha256, "inputSha256").toLowerCase(Locale.ROOT);
        if (!inputSha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Exact selected input SHA-256 required");
        Objects.requireNonNull(mappingData, "mappingData");
    }
}
