package dev.legacybridge.converter.service;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** A destination is an output request, not authorization to overwrite, install or activate a mod. */
public record ConversionRequest(Path input, Path output, MappingData mappings, Optional<String> expectedInputSha256,
                                ConversionProfile profile) {
    public ConversionRequest(Path input, Path output, MappingData mappings) {
        this(input, output, mappings, Optional.empty(), ConversionProfile.SIMPLE_CONTENT_V1);
    }

    public ConversionRequest(Path input, Path output, MappingData mappings, Optional<String> expectedInputSha256) {
        this(input, output, mappings, expectedInputSha256, ConversionProfile.SIMPLE_CONTENT_V1);
    }

    public ConversionRequest {
        input = Objects.requireNonNull(input, "input").toAbsolutePath().normalize();
        output = Objects.requireNonNull(output, "output").toAbsolutePath().normalize();
        Objects.requireNonNull(mappings, "mappings");
        Objects.requireNonNull(expectedInputSha256, "expectedInputSha256");
        Objects.requireNonNull(profile, "profile");
        expectedInputSha256.ifPresent(hash -> {
            if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Expected SHA-256 must be lowercase hexadecimal");
        });
    }
}
