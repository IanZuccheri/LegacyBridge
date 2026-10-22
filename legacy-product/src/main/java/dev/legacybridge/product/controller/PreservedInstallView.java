package dev.legacybridge.product.controller;

import dev.legacybridge.converter.service.EngineError;
import dev.legacybridge.product.install.PreservedAdmissionResult;
import dev.legacybridge.product.install.PreservedInstallResult;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/** Immutable observation; only a current controller review can be explicitly confirmed. */
public record PreservedInstallView(State state, Optional<UUID> reviewId, Optional<UUID> operationId,
        Optional<PreservedAdmissionResult> admission, Optional<PreservedInstallResult> result,
        Optional<Path> receipt, Optional<EngineError> error) {
    public enum State { DISABLED, IDLE, REVIEWING, REVIEWED, BLOCKED, INSTALLING, PENDING_RESTART, RESTORED, FAILED }
    public static PreservedInstallView initial(boolean enabled) {
        return new PreservedInstallView(enabled ? State.IDLE : State.DISABLED, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
    /** Never true for review/history/partial outcomes; caller must also observe controller idle. */
    public boolean safeToClose() {
        return (state == State.PENDING_RESTART || state == State.RESTORED) && receipt.isPresent()
                && error.isEmpty() && result.filter(PreservedInstallResult::safeToClose).isPresent();
    }
    public String activation() { return "NOT_OBSERVED"; }
}
