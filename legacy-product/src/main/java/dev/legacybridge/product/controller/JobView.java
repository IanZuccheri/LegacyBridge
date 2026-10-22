package dev.legacybridge.product.controller;

import dev.legacybridge.converter.operation.OperationProgress;
import dev.legacybridge.converter.service.EngineError;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded immutable per-job presentation. No retained class bytes or full report trees. */
public record JobView(UUID id, Path input, String displayName, JobState status,
                      Optional<EngineError> error, Optional<Path> artifact,
                      Optional<Path> compatibilityReport, Optional<Path> transformationLog,
                      Optional<Path> conversionReport, Optional<Path> installed,
                      Optional<OperationProgress> progress, String summaryKey) {
    public JobView {
        Objects.requireNonNull(id); Objects.requireNonNull(input); Objects.requireNonNull(status);
        displayName = ConversionController.safeText(displayName);
        Objects.requireNonNull(error); Objects.requireNonNull(artifact);
        Objects.requireNonNull(compatibilityReport); Objects.requireNonNull(transformationLog);
        Objects.requireNonNull(conversionReport); Objects.requireNonNull(installed);
        Objects.requireNonNull(progress); Objects.requireNonNull(summaryKey);
    }
}
