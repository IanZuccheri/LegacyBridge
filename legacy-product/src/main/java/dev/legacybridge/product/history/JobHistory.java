package dev.legacybridge.product.history;

import dev.legacybridge.product.controller.JobState;
import dev.legacybridge.product.install.TransactionResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Immutable, untrusted receipt observations. Never a JobView or installation approval. */
public record JobHistory(Status status, List<Job> jobs, List<Issue> issues,
                         int entriesVisited, int receiptsExamined, long bytesRead, int omittedIssues) {
    public JobHistory { jobs = List.copyOf(jobs); issues = List.copyOf(issues); }
    public enum Status { NOT_READ, COMPLETE, PARTIAL, CANCELLED, FAILED }
    public enum Ordering { SEQUENCED, LEGACY_UNORDERED, AMBIGUOUS }
    public enum Event { LEGACY_UNSPECIFIED, CONVERSION, INSTALLATION }
    public enum Action { APPLY, RESUME, RESTORE }
    public enum Activation { NOT_OBSERVED }
    public enum Code {
        WORKSPACE_UNSAFE, PATH_UNSAFE, READ_FAILED, CHANGED_DURING_READ, ALIASED_RECEIPT,
        INVALID_NAME, INVALID_UTF8, INVALID_JSON, UNKNOWN_SCHEMA, INVALID_RECEIPT,
        FILE_LIMIT, ENTRY_LIMIT, BYTE_LIMIT, FILE_TOO_LARGE, TIME_LIMIT, CANCELLED,
        DUPLICATE_SEQUENCE, SEQUENCE_GAP, MIXED_SCHEMAS, IDENTITY_CONFLICT
    }
    /** Location and message are bounded display text, never a path-opening capability. */
    public record Issue(Code code, String location, String message) { }
    /** Paths are inert, installation-relative strings; existence/content is NOT verified. */
    public record Receipt(String location, int schemaVersion, UUID jobId, Optional<Integer> sequence,
                          Event event, JobState recordedState, String input, String inputSha256,
                          Optional<String> artifactSha256, Optional<String> artifact,
                          Optional<String> installed, Optional<String> transaction,
                          Optional<Installation> installation, Optional<RecordedError> recordedError) {
        public Activation activation() { return Activation.NOT_OBSERVED; }
    }
    /** This boolean is the recorded transaction result, NOT permission to close the game. */
    public record Installation(UUID operationId, Action action, Optional<String> transaction,
                               Optional<TransactionResult.Status> status, Optional<Integer> entryIndex,
                               Optional<TransactionResult.EntryState> entryState,
                               boolean recordedTransactionSafeToClose) { }
    /** Untrusted original diagnostic, distinct from the reader's own Issue codes. */
    public record RecordedError(String code, String message, String remediation) { }
    /** Highest only orders observed receipts; it is NOT current installer/discovery state. */
    public record Job(UUID jobId, Ordering ordering, List<Receipt> receipts, Optional<Receipt> highestRecorded) {
        public Job { receipts = List.copyOf(receipts); }
    }
    public static JobHistory notRead() {
        return new JobHistory(Status.NOT_READ, List.of(), List.of(), 0, 0, 0, 0);
    }
}
