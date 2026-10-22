package dev.legacybridge.converter.service;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Planning executes the same static proof without publishing a JAR. It retains no candidate byte array. */
public record ConversionPlan(boolean convertible, Path input, Path prospectiveOutput,
                             String inputSha256, Optional<String> candidateSha256,
                             Map<String, Object> report) {
    public ConversionPlan {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(prospectiveOutput, "prospectiveOutput");
        Objects.requireNonNull(inputSha256, "inputSha256");
        Objects.requireNonNull(candidateSha256, "candidateSha256");
        // Reports originate in the converter's deep immutable canonical tree.
        report = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(report));
        if (convertible != candidateSha256.isPresent()) throw new IllegalArgumentException("Invalid candidate evidence");
    }
}
