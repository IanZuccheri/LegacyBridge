package dev.legacybridge.converter.service;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationProgress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Async typed handle. UI polls latestProgress and marshals completion to its own UI thread.
 * Non-async CompletionStage continuations may execute on the completing worker, or on the
 * caller that cancels a queued job. Keep them short and dispatch UI work explicitly. No job
 * lock is held while invoking them; this cannot prevent a caller's own callback from blocking
 * that caller or occupying the worker.
 */
public final class EngineJob<T> {
    private final UUID id = UUID.randomUUID();
    private final CompletableFuture<EngineOutcome<T>> completion = new CompletableFuture<>();
    private final CompletionStage<EngineOutcome<T>> view = completion.minimalCompletionStage();
    private final AtomicReference<OperationProgress> progress;
    private final OperationControl control;
    private final Object gate = new Object();
    private boolean terminal;
    private BooleanSupplier removeQueued = () -> false;
    private Runnable release = () -> { };

    EngineJob(OperationControl control, AtomicReference<OperationProgress> progress) {
        this.control = control;
        this.progress = progress;
    }

    public UUID id() { return id; }
    public CompletionStage<EngineOutcome<T>> completion() { return view; }
    public Optional<OperationProgress> latestProgress() { return Optional.ofNullable(progress.get()); }
    public dev.legacybridge.converter.operation.OperationMetrics metrics() { return control.metrics(); }

    /** False means already finished or the publication commit barrier was crossed; not cancelled. */
    public boolean cancel() {
        boolean removed;
        synchronized (gate) {
            if (terminal || !control.requestCancel()) return false;
            removed = removeQueued.getAsBoolean();
            if (removed) terminal = true;
        }
        if (removed) {
            release.run();
            completion.complete(EngineOutcome.cancelled());
        }
        return true;
    }

    void configureQueueRemoval(BooleanSupplier removal, Runnable release) {
        synchronized (gate) {
            this.removeQueued = java.util.Objects.requireNonNull(removal);
            this.release = java.util.Objects.requireNonNull(release);
        }
    }

    void complete(EngineOutcome<T> outcome, boolean checkCancellation) {
        synchronized (gate) {
            if (terminal) return;
            if (checkCancellation) {
                OperationControl.checkpoint();
                control.complete();
                OperationControl.stage(dev.legacybridge.converter.operation.OperationStage.COMPLETED, "Operation completed; no game activation implied");
            }
            // Seal the gap before future completion; cancellation cannot win after this point.
            terminal = true;
        }
        // CompletableFuture may execute arbitrary consumer continuations inline. Never hold gate.
        completion.complete(outcome);
    }
}
