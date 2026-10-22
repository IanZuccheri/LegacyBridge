package dev.legacybridge.converter.service;

import dev.legacybridge.converter.operation.OperationLimits;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EngineJobCallbackTest {
    @Test void serviceCloseDoesNotWaitForABlockedWorkerCompletionCallback() throws Exception {
        var workStarted = new CountDownLatch(1);
        var releaseWork = new CountDownLatch(1);
        var callbackStarted = new CountDownLatch(1);
        var releaseCallback = new CountDownLatch(1);
        var closeReturned = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var service = new LegacyEngineService();
        Thread closer = null;
        try {
            var job = service.submit(() -> {
                workStarted.countDown();
                assertTrue(releaseWork.await(10, TimeUnit.SECONDS));
                return EngineOutcome.completed("done");
            });
            assertTrue(workStarted.await(5, TimeUnit.SECONDS));
            var callback = job.completion().thenAccept(outcome -> {
                callbackStarted.countDown();
                awaitCallbackRelease(releaseCallback);
            });
            releaseWork.countDown();
            assertTrue(callbackStarted.await(5, TimeUnit.SECONDS));
            closer = new Thread(() -> {
                try { service.close(); }
                catch (Throwable error) { failure.set(error); }
                finally { closeReturned.countDown(); }
            }, "engine-close-test");
            closer.start();
            assertTrue(closeReturned.await(2, TimeUnit.SECONDS),
                    "close must not wait for the worker's consumer continuation to release the job lock");
            assertEquals(1, releaseCallback.getCount(), "the callback is deliberately still blocked");
            assertFalse(job.cancel(), "completion has been sealed even while its consumer callback runs");
            assertNull(failure.get());
            releaseCallback.countDown();
            callback.toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally {
            releaseWork.countDown(); releaseCallback.countDown();
            if (closer != null) closer.join(5000);
            service.close();
        }
    }

    @Test void queuedCancellationReleasesJobBeforeInvokingItsConsumerCallback() throws Exception {
        var workStarted = new CountDownLatch(1);
        var releaseWork = new CountDownLatch(1);
        var callbackStarted = new CountDownLatch(1);
        var releaseCallback = new CountDownLatch(1);
        var closeReturned = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var service = new LegacyEngineService(new EngineOptions(1, 1, OperationLimits.defaults()));
        Thread canceller = null, closer = null;
        try {
            service.submit(() -> {
                workStarted.countDown();
                assertTrue(releaseWork.await(10, TimeUnit.SECONDS));
                return EngineOutcome.completed("running");
            });
            assertTrue(workStarted.await(5, TimeUnit.SECONDS));
            var queued = service.submit(() -> EngineOutcome.completed("must not run"));
            var callback = queued.completion().thenAccept(outcome -> {
                assertEquals(EngineOutcome.Status.CANCELLED, outcome.status());
                callbackStarted.countDown();
                awaitCallbackRelease(releaseCallback);
            });
            canceller = new Thread(() -> {
                try { assertTrue(queued.cancel()); }
                catch (Throwable error) { failure.set(error); }
            }, "engine-cancel-test");
            canceller.start();
            assertTrue(callbackStarted.await(5, TimeUnit.SECONDS));
            closer = new Thread(() -> {
                try { service.close(); }
                catch (Throwable error) { failure.set(error); }
                finally { closeReturned.countDown(); }
            }, "engine-close-queued-test");
            closer.start();
            assertTrue(closeReturned.await(2, TimeUnit.SECONDS));
            assertFalse(queued.cancel());
            assertEquals(1, releaseCallback.getCount());
            releaseCallback.countDown();
            callback.toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally {
            releaseWork.countDown(); releaseCallback.countDown();
            if (canceller != null) canceller.join(5000);
            if (closer != null) closer.join(5000);
            service.close();
        }
        assertNull(failure.get());
    }

    private static void awaitCallbackRelease(CountDownLatch release) {
        try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
}
