package dev.legacybridge.product.install;

import dev.legacybridge.converter.service.EngineError;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Per-file outcomes: a batch is recoverable, never a claim of multi-file atomicity or activation. */
public record TransactionResult(Path transaction, Status status, List<EntryResult> entries,
                                Optional<EngineError> error) {
    public enum Status { PREPARED, COMMITTED, PARTIAL, RESTORED, MANUAL, RECOVERY_REQUIRED }
    public enum EntryState { PREPARED, RETIRED, INSTALLED, RESTORED, MANUAL, RECOVERY_REQUIRED }
    public record EntryResult(int index, String modId, String version, EntryState state,
                              Optional<Path> installed, String sha256, Optional<EngineError> error) {
        public EntryResult {
            Objects.requireNonNull(modId); Objects.requireNonNull(version); Objects.requireNonNull(state);
            installed = Objects.requireNonNull(installed); Objects.requireNonNull(sha256);
            error = Objects.requireNonNull(error);
        }
    }
    public TransactionResult {
        Objects.requireNonNull(transaction); Objects.requireNonNull(status);
        entries = List.copyOf(entries); error = Objects.requireNonNull(error);
    }
    public boolean safeToClose() { return error.isEmpty() && (status == Status.COMMITTED || status == Status.RESTORED); }
}
