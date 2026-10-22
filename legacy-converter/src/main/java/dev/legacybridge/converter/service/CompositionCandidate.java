package dev.legacybridge.converter.service;

import java.util.*;

/** Evidence of an actually serialized/reopened private JAR; never bytes or installation authority. */
public record CompositionCandidate(boolean validated, String inputSha256,
                                   Optional<String> candidateSha256, Map<String, Object> report) {
    public CompositionCandidate {
        Objects.requireNonNull(inputSha256); Objects.requireNonNull(candidateSha256);
        if (!inputSha256.matches("[0-9a-f]{64}") || validated != candidateSha256.isPresent()
                || candidateSha256.stream().anyMatch(s -> !s.matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("Invalid private candidate evidence");
        // Producer supplies the existing deep immutable canonical report tree.
        report = Collections.unmodifiableMap(new TreeMap<>(report));
    }
    public boolean publicationAllowed() { return false; }
}
