package dev.legacybridge.converter.service;

import dev.legacybridge.converter.operation.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EngineAdmissionTest {
    @Test void measurementsAreSeparateFromTheDeterministicResultAndRecordRealWork() throws Exception {
        try (var service = new LegacyEngineService()) {
            var job = service.submit(() -> {
                OperationControl.inputBytes(12); OperationControl.inputBytes(7);
                OperationControl.inflatedBytes(23); OperationControl.capturedBytes(8);
                return EngineOutcome.completed("same-result");
            });
            assertEquals("same-result", await(job).value().orElseThrow());
            assertEquals(12, job.metrics().largestInputBytes()); assertEquals(23, job.metrics().inflatedWorkBytes());
            assertEquals(8, job.metrics().capturedWorkBytes()); assertTrue(job.metrics().heapSamples() > 0);
            assertTrue(job.metrics().peakObservedSharedHeapUsedBytes() > 0);
            assertEquals(false, job.metrics().toTree().get("hardHeapLimit"));
        }
    }
    @Test void instancesShareOneWorkerSlotAndWaitingCancellationDoesNotRunTheJob() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1); var calls = new AtomicInteger();
        try (var first = new LegacyEngineService(); var second = new LegacyEngineService()) {
            var active = first.submit(() -> { started.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return EngineOutcome.completed(1); });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var waiting = second.submit(() -> { calls.incrementAndGet(); return EngineOutcome.completed(2); });
            assertTrue(waiting.cancel()); assertEquals(EngineOutcome.Status.CANCELLED, await(waiting).status());
            assertEquals(0, calls.get()); release.countDown(); assertEquals(1, await(active).value().orElseThrow());
            assertEquals(3, await(second.submit(() -> EngineOutcome.completed(3))).value().orElseThrow());
        } finally { release.countDown(); }
    }
    @Test void aggregateCapacityCannotBeBypassedWithMoreServiceInstancesAndAllLeasesAreReleased() throws Exception {
        var services = new ArrayList<LegacyEngineService>(); var jobs = new ArrayList<EngineJob<Integer>>();
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            var first = new LegacyEngineService(); services.add(first);
            jobs.add(first.submit(() -> { started.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return EngineOutcome.completed(1); }));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 1; i < EngineAdmission.MAX_ADMITTED; i++) {
                var service = new LegacyEngineService(); services.add(service);
                jobs.add(service.submit(() -> EngineOutcome.completed(2)));
            }
            var extra = new LegacyEngineService(); services.add(extra);
            assertEquals("ENGINE_CAPACITY", await(extra.submit(() -> EngineOutcome.completed(9))).error().orElseThrow().code());
            jobs.subList(1, jobs.size()).forEach(EngineJob::cancel);
            for (var job : jobs.subList(1, jobs.size())) assertEquals(EngineOutcome.Status.CANCELLED, await(job).status());
            release.countDown(); await(jobs.get(0));
            assertEquals(9, await(extra.submit(() -> EngineOutcome.completed(9))).value().orElseThrow());
        } finally { release.countDown(); services.forEach(LegacyEngineService::close); }
    }
    @Test void waitingForAnotherInstanceCountsTowardDeadline() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var activeService = new LegacyEngineService(); var waitingService = new LegacyEngineService(
                new EngineOptions(1, 1, new OperationLimits(1024, 1024, 1024, Duration.ofMillis(100))))) {
            var active = activeService.submit(() -> { started.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return EngineOutcome.completed(1); });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var result = await(waitingService.submit(() -> { fail("Timed out work must never execute"); return EngineOutcome.completed(2); }));
            assertEquals("TIMEOUT", result.error().orElseThrow().code()); release.countDown(); await(active);
        } finally { release.countDown(); }
    }
    @Test void exceptionReleasesCapacityForAnotherInstance() throws Exception {
        try (var first = new LegacyEngineService(); var second = new LegacyEngineService()) {
            assertEquals("IO_ERROR", await(first.submit(() -> { throw new java.io.IOException("negative"); })).error().orElseThrow().code());
            assertEquals(7, await(second.submit(() -> EngineOutcome.completed(7))).value().orElseThrow());
        }
    }
    private static <T> EngineOutcome<T> await(EngineJob<T> job) throws Exception { return job.completion().toCompletableFuture().get(10, TimeUnit.SECONDS); }
}
