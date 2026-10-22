package dev.legacybridge.converter.service;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Explicit setup/export action, never performed implicitly when analyzing or converting a player's mod. */
public record MappingPreparationRequest(Path output, Optional<Path> profile, boolean fromDevelopmentCache) {
    public MappingPreparationRequest {
        output = Objects.requireNonNull(output, "output").toAbsolutePath().normalize();
        Objects.requireNonNull(profile, "profile");
        if (profile.isPresent() == fromDevelopmentCache)
            throw new IllegalArgumentException("Choose exactly one explicit profile or development-cache preparation");
        profile = profile.map(path -> path.toAbsolutePath().normalize());
    }
}
