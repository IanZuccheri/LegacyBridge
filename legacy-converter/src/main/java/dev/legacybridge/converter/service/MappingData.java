package dev.legacybridge.converter.service;

import dev.legacybridge.converter.mapping.portable.PortableMappingBundle;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.converter.mapping.builtin.EmbeddedSimpleContentData;
import dev.legacybridge.converter.mapping.builtin.EmbeddedCohortData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Bundled finite contracts or explicit trusted-local data; never cache discovery or downloads. */
public record MappingData(Kind kind, Optional<Path> path, Optional<String> expectedManifestSha256) {
    public MappingData(Kind kind, Optional<Path> path) { this(kind, path, Optional.empty()); }
    public enum Kind { PORTABLE_BUNDLE, EXPLICIT_PROFILE, BUILTIN_SIMPLE_CONTENT, UNAVAILABLE, BUILTIN_COHORT_PREVIEW }

    public MappingData {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(expectedManifestSha256, "expectedManifestSha256");
        if (expectedManifestSha256.isPresent() && (kind != Kind.PORTABLE_BUNDLE
                || !expectedManifestSha256.orElseThrow().matches("[a-f0-9]{64}")))
            throw new IllegalArgumentException("Only an explicit portable bundle can carry a reviewed manifest SHA-256");
        boolean pathless = kind == Kind.UNAVAILABLE || kind == Kind.BUILTIN_SIMPLE_CONTENT || kind == Kind.BUILTIN_COHORT_PREVIEW;
        if (pathless != path.isEmpty()) throw new IllegalArgumentException("Mapping source/path mismatch");
        path = path.map(value -> value.toAbsolutePath().normalize());
    }

    public static MappingData bundle(Path path) { return new MappingData(Kind.PORTABLE_BUNDLE, Optional.of(path)); }
    public static MappingData reviewedBundle(Path path, String sha256) {
        return new MappingData(Kind.PORTABLE_BUNDLE, Optional.of(path), Optional.of(sha256));
    }
    public static MappingData profile(Path path) { return new MappingData(Kind.EXPLICIT_PROFILE, Optional.of(path)); }
    public static MappingData unavailable() { return new MappingData(Kind.UNAVAILABLE, Optional.empty()); }
    public static MappingData builtinSimpleContent() { return new MappingData(Kind.BUILTIN_SIMPLE_CONTENT, Optional.empty()); }
    public static MappingData builtinCohortPreview() { return new MappingData(Kind.BUILTIN_COHORT_PREVIEW, Optional.empty()); }

    MappingInputs load() throws IOException {
        return switch (kind) {
            case PORTABLE_BUNDLE -> PortableMappingBundle.load(path.orElseThrow(), expectedManifestSha256.orElse(null));
            case EXPLICIT_PROFILE -> new MappingInputsLoader().load(path.orElseThrow());
            case BUILTIN_SIMPLE_CONTENT -> EmbeddedSimpleContentData.load();
            case BUILTIN_COHORT_PREVIEW -> EmbeddedCohortData.load();
            case UNAVAILABLE -> throw new MissingMappingDataException();
        };
    }

    static final class MappingScopeException extends IOException {
        MappingScopeException() {
            super("Included data is scoped to its exact operation: simple-content conversion or finite cohort preview. Select explicit full data for other operations; no fallback.");
        }
    }

    static final class MissingMappingDataException extends IOException {
        MissingMappingDataException() {
            super("Select a prepared local mapping bundle or an explicit mapping profile; no data is downloaded automatically.");
        }
    }
}
