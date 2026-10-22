package dev.legacybridge.converter.service;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Explicit complete source set present on BOTH physical sides; not conversion/installation authority. */
public record CohortInspectionRequest(List<Artifact> artifacts, MappingData mappingData) {
    public CohortInspectionRequest {
        Objects.requireNonNull(mappingData);
        if (artifacts == null || artifacts.isEmpty() || artifacts.size() > 4)
            throw new IllegalArgumentException("Select 1..4 source artifacts explicitly");
        artifacts = List.copyOf(artifacts);
        var ids = new HashSet<String>();
        for (Artifact artifact : artifacts) if (!ids.add(artifact.id()))
            throw new IllegalArgumentException("Duplicate artifact label");
        artifacts = artifacts.stream().sorted(java.util.Comparator.comparing(Artifact::id)).toList();
    }

    public record Artifact(String id, Path input, String expectedSha256) {
        public Artifact {
            if (id == null || !id.matches("[a-z][a-z0-9_-]{0,63}"))
                throw new IllegalArgumentException("Artifact label must be a bounded lowercase identifier");
            input = Objects.requireNonNull(input).toAbsolutePath().normalize();
            if (expectedSha256 == null || !expectedSha256.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Every selected artifact requires an exact SHA-256");
        }
    }
}
