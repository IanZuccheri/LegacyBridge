package dev.legacybridge.product.install;

import dev.legacybridge.converter.service.EngineError;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Disk installation observation only. COMMITTED is pending restart, never activation/behavior proof. */
public record PreservedInstallResult(State state, Optional<Path> transaction, Optional<String> planSha256,
                                     Optional<Path> installed, Optional<EngineError> error) {
    public enum State { PREPARED, PARTIAL, COMMITTED, RESTORING, RESTORED, RECOVERY_REQUIRED }
    public PreservedInstallResult {
        Objects.requireNonNull(state); Objects.requireNonNull(transaction); Objects.requireNonNull(planSha256);
        Objects.requireNonNull(installed); Objects.requireNonNull(error);
        if (state != State.RECOVERY_REQUIRED && (transaction.isEmpty() || planSha256.isEmpty() || error.isPresent()))
            throw new IllegalArgumentException("Successful observations need exact transaction pins and no error");
        if (state == State.COMMITTED && installed.isEmpty() || state == State.RESTORED && installed.isPresent())
            throw new IllegalArgumentException("Installed identity contradicts terminal observation");
    }
    public boolean safeToClose() { return error.isEmpty() && (state == State.COMMITTED || state == State.RESTORED); }
}
