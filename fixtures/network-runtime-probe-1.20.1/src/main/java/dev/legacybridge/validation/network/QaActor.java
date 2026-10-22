package dev.legacybridge.validation.network;

import com.google.gson.JsonObject;
import java.time.Duration;

/** Main-thread bounded state machine. The mailbox is the only producer. */
abstract class QaActor {
    QaMailbox.Pending pending;
    private long deadline;
    final void accept(QaMailbox.Pending next) {
        if (pending != null) { next.response().completeExceptionally(new IllegalStateException("Only one QA operation may be pending")); return; }
        pending = next;
        deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        try { begin(next.command()); } catch (Throwable failure) { fail(failure); }
    }
    final void tick() {
        if (pending == null) return;
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Native state deadline: " + snapshot());
            if (satisfied(pending.command())) {
                JsonObject result = snapshot();
                QaMailbox.Pending done = pending;
                pending = null;
                done.response().complete(result);
            }
        } catch (Throwable failure) { fail(failure); }
    }
    private void fail(Throwable failure) {
        if (pending != null) { pending.response().completeExceptionally(failure); pending = null; }
    }
    abstract void begin(QaCommand command);
    abstract boolean satisfied(QaCommand command);
    abstract JsonObject snapshot();
    static void require(boolean predicate, String message) { if (!predicate) throw new IllegalStateException(message); }
}
