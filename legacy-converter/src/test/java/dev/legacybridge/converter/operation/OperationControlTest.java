package dev.legacybridge.converter.operation;

import dev.legacybridge.converter.archive.JarEntryVisitor;
import dev.legacybridge.converter.archive.SafeJarReader;
import dev.legacybridge.converter.testutil.TestJarBuilder;
import dev.legacybridge.converter.bytecode.BytecodeScanner;
import dev.legacybridge.converter.conversion.extract.SyntheticContentFixture;
import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.mapping.source.ApiIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OperationControlTest {
    @TempDir Path temporary;

    @Test void validatesLimitsAndScopeIsNonInheritableAndCannotNest() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new OperationLimits(0, 1, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new OperationLimits(1, 1, 1, Duration.ZERO));
        var control = control();
        try (var scope = control.enter()) {
            assertThrows(IllegalStateException.class, () -> control().enter());
            assertTrue(control.requestCancel());
            var failure = new AtomicReference<Throwable>();
            var thread = new Thread(() -> { try { OperationControl.checkpoint(); } catch (Throwable e) { failure.set(e); } });
            thread.start(); thread.join(5000);
            assertFalse(thread.isAlive()); assertNull(failure.get());
            assertEquals(OperationStoppedException.Code.CANCELLED,
                    assertThrows(OperationStoppedException.class, OperationControl::checkpoint).code());
        }
        assertDoesNotThrow(OperationControl::checkpoint);
    }

    @Test void cooperativeDeadlineUsesMonotonicClockWithoutSleeping() {
        var clock = new AtomicLong();
        var c = new OperationControl(new OperationLimits(100, 100, 100, Duration.ofNanos(10)), e -> {}, clock::get);
        try (var scope = c.enter()) {
            clock.set(9); OperationControl.checkpoint();
            clock.set(10);
            assertEquals(OperationStoppedException.Code.TIMEOUT,
                    assertThrows(OperationStoppedException.class, OperationControl::checkpoint).code());
        }
    }

    @Test void byteBudgetsCountActualPayloadAndCannotOverflow() {
        var c = new OperationControl(new OperationLimits(10, 20, 30, Duration.ofMinutes(1)), e -> {});
        try (var scope = c.enter()) {
            OperationControl.inputBytes(10); OperationControl.inputBytes(10);
            assertEquals(OperationStoppedException.Code.INPUT_LIMIT,
                    assertThrows(OperationStoppedException.class, () -> OperationControl.inputBytes(11)).code());
            OperationControl.inflatedBytes(10); OperationControl.inflatedBytes(10);
            assertEquals(OperationStoppedException.Code.INFLATED_LIMIT,
                    assertThrows(OperationStoppedException.class, () -> OperationControl.inflatedBytes(Long.MAX_VALUE)).code());
            OperationControl.capturedBytes(30);
            assertEquals(OperationStoppedException.Code.CAPTURE_LIMIT,
                    assertThrows(OperationStoppedException.class, () -> OperationControl.capturedBytes(1)).code());
        }
    }

    @Test void observerExceptionsAreIsolatedAndCancelledCompletionCannotBecomeSuccess() {
        var c = new OperationControl(OperationLimits.defaults(), e -> { throw new AssertionError("observer"); });
        try (var scope = c.enter()) {
            assertDoesNotThrow(() -> OperationControl.stage(OperationStage.ANALYSIS, "test"));
            assertTrue(c.requestCancel());
            assertThrows(OperationStoppedException.class, c::complete);
        }
    }

    @Test void publicationBarrierLinearizesAgainstCancellationFromAnotherThread() throws Exception {
        for (boolean publishFirst : List.of(false, true)) {
            var c = control();
            var ready = new CountDownLatch(1); var cancelled = new CountDownLatch(1);
            var accepted = new AtomicBoolean(); var failure = new AtomicReference<Throwable>();
            var thread = new Thread(() -> {
                try { assertTrue(ready.await(5, TimeUnit.SECONDS)); accepted.set(c.requestCancel()); }
                catch (Throwable e) { failure.set(e); }
                finally { cancelled.countDown(); }
            });
            thread.start();
            try (var scope = c.enter()) {
                if (publishFirst) OperationControl.beginPublication();
                ready.countDown(); assertTrue(cancelled.await(5, TimeUnit.SECONDS));
                if (publishFirst) { assertFalse(accepted.get()); OperationControl.checkpoint(); c.complete(); }
                else {
                    assertTrue(accepted.get());
                    assertEquals(OperationStoppedException.Code.CANCELLED,
                            assertThrows(OperationStoppedException.class, OperationControl::beginPublication).code());
                }
            } finally { ready.countDown(); thread.join(5000); }
            assertFalse(thread.isAlive()); assertNull(failure.get());
        }
    }

    @Test void cancellationDuringSnapshotReadCleansItsPrivateTemporary() throws Exception {
        byte[] payload = new byte[150_000]; new Random(7).nextBytes(payload);
        var input = TestJarBuilder.write(temporary.resolve("input.jar"), Map.of("blob.bin", payload));
        var snapshotsBefore = snapshots();
        var holder = new AtomicReference<OperationControl>();
        var readObserved = new AtomicBoolean();
        var c = new OperationControl(OperationLimits.defaults(), event -> {
            if (event.stage() == OperationStage.INPUT_SNAPSHOT && event.completed() > 0) {
                readObserved.set(true); holder.get().requestCancel();
            }
        }); holder.set(c);
        try (var scope = c.enter()) {
            assertEquals(OperationStoppedException.Code.CANCELLED,
                    assertThrows(OperationStoppedException.class, () -> new SafeJarReader().scan(input, visitor(true))).code());
        }
        assertTrue(readObserved.get());
        assertEquals(snapshotsBefore, snapshots());
        assertTrue(Files.isRegularFile(input));
    }

    @Test void cancellationDuringInflationDoesNotReturnPartialInventory() throws Exception {
        byte[] payload = new byte[50_000]; new Random(8).nextBytes(payload);
        var input = TestJarBuilder.write(temporary.resolve("input.jar"), Map.of("blob.bin", payload));
        var snapshotsBefore = snapshots(); var holder = new AtomicReference<OperationControl>();
        var c = new OperationControl(OperationLimits.defaults(), event -> {
            if (event.stage() == OperationStage.ARCHIVE_SCAN && event.detail().equals("Inflated bytes inspected"))
                holder.get().requestCancel();
        }); holder.set(c);
        try (var scope = c.enter()) {
            assertEquals(OperationStoppedException.Code.CANCELLED,
                    assertThrows(OperationStoppedException.class, () -> new SafeJarReader().scan(input, visitor(true))).code());
        }
        assertEquals(snapshotsBefore, snapshots());
    }

    @Test void safeReaderEnforcesScopedInflationAndCaptureLimitsAndCleansSnapshots() throws Exception {
        var input = TestJarBuilder.write(temporary.resolve("input.jar"), Map.of("blob.bin", new byte[20_000]));
        var snapshotsBefore = snapshots();
        for (boolean captureLimit : List.of(false, true)) {
            var c = new OperationControl(new OperationLimits(1_000_000, captureLimit ? 1_000_000 : 10,
                    captureLimit ? 10 : 1_000_000, Duration.ofMinutes(1)), e -> {});
            try (var scope = c.enter()) {
                var failure = assertThrows(OperationStoppedException.class,
                        () -> new SafeJarReader().scan(input, visitor(true)));
                assertEquals(captureLimit ? OperationStoppedException.Code.CAPTURE_LIMIT : OperationStoppedException.Code.INFLATED_LIMIT, failure.code());
            }
            assertEquals(snapshotsBefore, snapshots());
        }
    }

    @Test void completionRefusesLateCancellationAndWrappedStopRetainsIdentity() {
        var c = control();
        try (var scope = c.enter()) { c.complete(); }
        assertFalse(c.requestCancel());
        var stopped = new OperationStoppedException(OperationStoppedException.Code.TIMEOUT, "deadline");
        assertSame(stopped, assertThrows(OperationStoppedException.class,
                () -> OperationStoppedException.rethrowIfPresent(new IllegalStateException("wrapped", stopped))));
    }

    @Test void bytecodeScannerDoesNotRelabelCancellationAsUnreadableClass() {
        var c = control();
        try (var scope = c.enter()) {
            var error = assertThrows(OperationStoppedException.class, () -> new BytecodeScanner().scan(
                    "fixture/SimpleMod.class", SyntheticContentFixture.bytecode("valid"), reference -> c.requestCancel()));
            assertEquals(OperationStoppedException.Code.CANCELLED, error.code());
        }
    }

    @Test void jsonAndApiIndexObserveTheSameControlWithoutInputClassExecution() throws Exception {
        var clock = new AtomicLong();
        var c = new OperationControl(new OperationLimits(1_000_000, 1_000_000, 1_000_000, Duration.ofNanos(8)),
                e -> {}, clock::getAndIncrement);
        try (var scope = c.enter()) {
            assertEquals(OperationStoppedException.Code.TIMEOUT, assertThrows(OperationStoppedException.class,
                    () -> BoundedJsonParser.parse("[1,2,3,4,5,6,7,8,9,10]")).code());
        }
        var input = TestJarBuilder.write(temporary.resolve("api.jar"),
                Map.of("fixture/SimpleMod.class", SyntheticContentFixture.bytecode("valid")));
        var cancelled = control(); cancelled.requestCancel();
        try (var scope = cancelled.enter()) {
            assertEquals(OperationStoppedException.Code.CANCELLED,
                    assertThrows(OperationStoppedException.class, () -> ApiIndex.read(List.of(input))).code());
        }
    }

    private static OperationControl control() { return new OperationControl(OperationLimits.defaults(), e -> {}); }
    private static JarEntryVisitor visitor(boolean capture) {
        return new JarEntryVisitor() {
            @Override public boolean requiresBytes(String name) { return capture; }
            @Override public void visit(String name, byte[] bytes) { }
        };
    }
    private static List<Path> snapshots() throws Exception {
        try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.filter(path -> path.getFileName().toString().startsWith("legacybridge-input-"))
                    .sorted().toList();
        }
    }
}
