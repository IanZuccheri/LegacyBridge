package dev.legacybridge.converter.service;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStage;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Aggregate bound for all service instances in this engine classloader, not foreign loaders or direct synchronous APIs. */
final class EngineAdmission {
    static final int MAX_ADMITTED = 33;
    private static final Semaphore ADMITTED = new Semaphore(MAX_ADMITTED);
    private static final Semaphore ACTIVE = new Semaphore(1, true);
    private EngineAdmission() { }
    static Lease admit() { return ADMITTED.tryAcquire() ? new Lease(ADMITTED) : null; }
    static Lease enter() throws InterruptedException {
        OperationControl.stage(OperationStage.WAITING_FOR_ENGINE, "Waiting for the shared engine slot; queue time counts against deadline");
        while (!ACTIVE.tryAcquire(25, TimeUnit.MILLISECONDS)) OperationControl.checkpoint();
        var lease = new Lease(ACTIVE);
        try { OperationControl.checkpoint(); return lease; }
        catch (RuntimeException | Error failure) { lease.close(); throw failure; }
    }
    static final class Lease implements AutoCloseable {
        private final Semaphore semaphore;
        private final AtomicBoolean released = new AtomicBoolean();
        Lease(Semaphore semaphore) { this.semaphore = semaphore; }
        @Override public void close() { if (released.compareAndSet(false, true)) semaphore.release(); }
    }
}
