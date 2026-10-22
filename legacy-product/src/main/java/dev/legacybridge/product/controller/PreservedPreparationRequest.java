package dev.legacybridge.product.controller;

import dev.legacybridge.converter.service.MappingData;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Explicit developer selection; absent candidate requests the existing finite logic-preserving conversion. */
public record PreservedPreparationRequest(Path original, String originalSha256, Optional<Candidate> candidate, MappingData data) {
    public record Candidate(Path path, String sha256) { public Candidate { checkedPath(path); hash(sha256); } }
    public PreservedPreparationRequest {
        checkedPath(original); hash(originalSha256); Objects.requireNonNull(candidate); Objects.requireNonNull(data);
        if (data.kind() != MappingData.Kind.PORTABLE_BUNDLE || data.expectedManifestSha256().isEmpty())
            throw new IllegalArgumentException("Explicit full local data and manifest pin required");
        checkedPath(data.path().orElseThrow());
        if (candidate.filter(c -> c.path().equals(original)).isPresent()) throw new IllegalArgumentException("Original/candidate must differ");
    }
    static Path checkedPath(Path path) {
        if (path == null || !path.isAbsolute() || !path.equals(path.normalize()) || path.toString().length() > 4096
                || path.toString().codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Bounded absolute normalized path required");
        return path;
    }
    static String hash(String hash) {
        if (hash == null || !hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Exact lowercase SHA-256 required");
        return hash;
    }
}
