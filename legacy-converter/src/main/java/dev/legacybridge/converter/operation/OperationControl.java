package dev.legacybridge.converter.operation;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Cooperative control, scoped only on the operation's worker thread. No game/CLI dependency.
 * Opaque parsers and OS calls are bounded by input policies but are not preemptible.
 * Observer callbacks must be nonblocking; observer exceptions cannot invalidate an operation.
 */
public final class OperationControl {
    private static final ThreadLocal<OperationControl> CURRENT = new ThreadLocal<>();
    private enum State { ACTIVE, CANCELLED, PUBLISHING, COMPLETED }
    private final OperationLimits limits;
    private final Consumer<OperationProgress> observer;
    private final LongSupplier clock;
    private final long started;
    private State state = State.ACTIVE;
    private volatile long inflated;
    private volatile long captured;
    private volatile long largestInput;
    private long peakSharedHeapUsed;
    private long heapSamples;
    private Thread owner;

    public OperationControl(OperationLimits limits, Consumer<OperationProgress> observer) {
        this(limits, observer, System::nanoTime);
    }

    OperationControl(OperationLimits limits, Consumer<OperationProgress> observer, LongSupplier clock) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.observer = Objects.requireNonNull(observer, "observer");
        this.clock = Objects.requireNonNull(clock, "clock");
        started = clock.getAsLong();
    }

    public OperationLimits limits() { return limits; }

    public synchronized OperationMetrics metrics() {
        return new OperationMetrics(largestInput, inflated, captured, peakSharedHeapUsed, heapSamples);
    }

    public synchronized Scope enter() {
        if (CURRENT.get() != null || owner != null || state == State.PUBLISHING || state == State.COMPLETED)
            throw new IllegalStateException("An operation scope cannot be nested or shared between workers");
        owner = Thread.currentThread();
        CURRENT.set(this);
        return new Scope(this, owner);
    }

    /** True is the linearized guarantee that this job will not begin publishing a candidate. */
    public synchronized boolean requestCancel() {
        if (state == State.CANCELLED) return true;
        if (state != State.ACTIVE) return false;
        state = State.CANCELLED;
        return true;
    }

    /** Mark a read-only job terminal, or finish after publication. */
    public synchronized void complete() {
        if (state == State.ACTIVE || state == State.CANCELLED) check();
        state = State.COMPLETED;
    }

    public static void checkpoint() {
        var control = CURRENT.get();
        if (control != null) control.check();
    }

    private synchronized void check() {
        if (state == State.PUBLISHING || state == State.COMPLETED) return;
        if (state == State.CANCELLED || Thread.currentThread().isInterrupted()) {
            state = State.CANCELLED;
            throw new OperationStoppedException(OperationStoppedException.Code.CANCELLED, "Operation cancelled before publication");
        }
        if (clock.getAsLong() - started >= limits.timeout().toNanos())
            throw new OperationStoppedException(OperationStoppedException.Code.TIMEOUT, "Cooperative operation deadline exceeded");
        long headroom = HeapPressure.availableBytes();
        peakSharedHeapUsed = Math.max(peakSharedHeapUsed, Runtime.getRuntime().maxMemory() - headroom);
        heapSamples++;
        HeapPressure.require(headroom, HeapPressure.RESERVE_BYTES);
    }

    public static void stage(OperationStage stage, String detail) { progress(stage, 0, -1, detail); }

    public static void progress(OperationStage stage, long completed, long total, String detail) {
        var control = CURRENT.get();
        if (control == null) return;
        control.check();
        try { control.observer.accept(new OperationProgress(stage, completed, total, detail)); }
        catch (Throwable observerFailure) {
            // Application observers are not part of conversion correctness. Never dispatch input code.
            if (observerFailure instanceof ThreadDeath fatal) throw fatal;
            if (observerFailure instanceof VirtualMachineError fatal) throw fatal;
        }
        control.check();
    }

    /** Maximum observed source length, checked on every actual read and rehash, not cumulative rereads. */
    public static void inputBytes(long actual) {
        checkpoint();
        var c = CURRENT.get();
        if (c != null && actual > c.limits.maxInputBytes())
            throw new OperationStoppedException(OperationStoppedException.Code.INPUT_LIMIT, "Operation input byte limit exceeded");
        if (c != null) c.largestInput = Math.max(c.largestInput, actual);
    }

    /** Actual inflated work across this job, including repeated scans. */
    public static void inflatedBytes(long delta) {
        checkpoint();
        var c = CURRENT.get();
        if (c != null) {
            if (delta < 0 || delta > c.limits.maxInflatedBytes() - c.inflated)
                throw new OperationStoppedException(OperationStoppedException.Code.INFLATED_LIMIT, "Operation inflated byte budget exceeded");
            c.inflated += delta;
        }
    }

    /** Captured/generated payload work, not exact Java-object memory consumption. */
    public static void capturedBytes(long delta) {
        checkpoint();
        var c = CURRENT.get();
        if (c != null) {
            if (delta < 0 || delta > c.limits.maxCapturedBytes() - c.captured)
                throw new OperationStoppedException(OperationStoppedException.Code.CAPTURE_LIMIT, "Operation captured payload budget exceeded");
            c.captured += delta;
        }
    }

    /** Called immediately before the irreversible no-replace move, after all candidate bytes are closed. */
    public static void beginPublication() {
        var c = CURRENT.get();
        if (c == null) return;
        synchronized (c) {
            c.check();
            if (c.state != State.ACTIVE) throw new IllegalStateException("Publication may begin only once");
            c.state = State.PUBLISHING;
        }
        // Cancellation is no longer accepted; callbacks must not alter the outcome.
        progress(OperationStage.PUBLICATION, 0, -1, "Publishing fully validated candidate");
    }

    public static final class Scope implements AutoCloseable {
        private final OperationControl control;
        private final Thread thread;
        private boolean closed;
        private Scope(OperationControl control, Thread thread) { this.control = control; this.thread = thread; }
        @Override public void close() {
            if (closed) return;
            if (Thread.currentThread() != thread || CURRENT.get() != control)
                throw new IllegalStateException("Operation scope must close on its owning worker");
            CURRENT.remove();
            synchronized (control) { control.owner = null; }
            closed = true;
        }
    }
}
