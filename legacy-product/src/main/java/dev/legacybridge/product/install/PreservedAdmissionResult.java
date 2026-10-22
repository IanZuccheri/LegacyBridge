package dev.legacybridge.product.install;

import dev.legacybridge.converter.service.EngineError;
import dev.legacybridge.converter.service.PreservedArtifactInspection;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Preparation/readback only. Neither this record nor persisted JSON is an Apply/close capability. */
public record PreservedAdmissionResult(State state, Optional<Path> transaction,
        Optional<String> planSha256, Optional<PreservedArtifactInspection> inspection,
        Optional<EngineError> error) {
    public enum State { RECORDED_NOT_INSTALLABLE, RECHECKED_NOT_INSTALLABLE, INCOMPLETE, RECOVERY_REQUIRED }
    public PreservedAdmissionResult {
        Objects.requireNonNull(state); Objects.requireNonNull(transaction); Objects.requireNonNull(planSha256);
        Objects.requireNonNull(inspection); Objects.requireNonNull(error);
        if (state != State.RECOVERY_REQUIRED && (transaction.isEmpty() || planSha256.isEmpty()
                || inspection.isEmpty() || !inspection.orElseThrow().validated() || error.isPresent()))
            throw new IllegalArgumentException("Missing fresh admission evidence");
        if (state == State.RECOVERY_REQUIRED && error.isEmpty()) throw new IllegalArgumentException("Missing recovery diagnostic");
    }
    public boolean installationAllowed() { return false; }
    public boolean safeToClose() { return false; }
}
