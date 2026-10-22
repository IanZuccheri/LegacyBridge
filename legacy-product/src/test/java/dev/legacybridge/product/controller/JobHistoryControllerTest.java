package dev.legacybridge.product.controller;

import dev.legacybridge.product.history.JobHistory;
import dev.legacybridge.product.history.JobHistoryReader;
import dev.legacybridge.product.install.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual inert conversions + actual disposable installer; not Forge execution. */
class JobHistoryControllerTest {
    @TempDir Path game;
    private final InstallSession session = new InstallSession(Set.of("minecraft", "forge", "legacybridge_runtime"), Set.of(), false);

    @Test void readsActualConversionApplyRestoreReceiptsButReopenedControllerCannotApplyHistory() throws Exception {
        UUID oldJob;
        try (var controller = converted()) {
            oldJob = controller.jobs().get(0).id();
            ReceiptInstallerFixtures.platform(game);
            controller.applyJobs(Map.of(oldJob, Optional.empty()), session).orElseThrow(); await(controller);
            Path tx = controller.installation().result().orElseThrow().transaction();
            controller.recoverTransaction(tx, RecoveryAction.RESTORE, session).orElseThrow(); await(controller);
            List<JobView> jobs = controller.jobs(); var install = controller.installation();
            Map<Path, byte[]> before = bytes();
            assertTrue(controller.refreshHistory()); await(controller);
            assertEquals(JobHistory.Status.COMPLETE, controller.history().status(), controller.history().issues().toString());
            var history = controller.history().jobs().get(0);
            assertEquals(List.of(1, 2, 3), history.receipts().stream().map(r -> r.sequence().orElseThrow()).toList());
            assertEquals(JobHistory.Action.RESTORE, history.highestRecorded().orElseThrow().installation().orElseThrow().action());
            assertEquals(JobHistory.Activation.NOT_OBSERVED, history.highestRecorded().orElseThrow().activation());
            assertEquals(jobs, controller.jobs()); assertEquals(install, controller.installation()); unchanged(before);
        }
        try (var reopened = new ConversionController(game)) {
            assertEquals(JobHistory.Status.NOT_READ, reopened.history().status());
            Map<Path, byte[]> before = bytes(); assertTrue(reopened.refreshHistory()); await(reopened);
            assertEquals(1, reopened.history().jobs().size()); assertTrue(reopened.jobs().isEmpty());
            assertTrue(reopened.applyJobs(Map.of(oldJob, Optional.empty()), session).isEmpty());
            assertTrue(reopened.installation().transactions().isEmpty()); unchanged(before);
        }
    }

    @Test void historyReadOnNewInstanceDoesNotCreateDirectoriesAndReusesCoordinator() throws Exception {
        AtomicReference<String> thread = new AtomicReference<>();
        try (var controller = new ConversionController(game, noInstaller(), (root, cancel) -> {
            thread.set(Thread.currentThread().getName()); return new JobHistoryReader().read(root, cancel);
        })) {
            assertTrue(controller.refreshHistory()); await(controller);
            assertEquals("legacybridge-controller", thread.get());
            assertEquals(JobHistory.Status.COMPLETE, controller.history().status());
            assertFalse(Files.exists(game.resolve("legacybridge"))); assertTrue(controller.jobs().isEmpty());
            assertFalse(controller.cancel());
        }
    }

    @Test void busyCancellationAndCloseAreBoundedAndNeverPublishAfterAcceptedCancellation() throws Exception {
        AtomicInteger calls = new AtomicInteger(); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var controller = new ConversionController(game, noInstaller(), (root, cancel) -> {
            if (calls.incrementAndGet() > 1) {
                entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("test barrier timed out"); }
                catch (InterruptedException error) { throw new AssertionError(error); }
            }
            // Deliberately stale success from seam tests the controller publication guard.
            return new JobHistoryReader().read(root, () -> false);
        });
        try {
            assertTrue(controller.refreshHistory()); await(controller); var previous = controller.history();
            assertTrue(controller.refreshHistory()); assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertFalse(controller.refreshHistory()); assertFalse(controller.refresh());
            assertTrue(controller.cancel());
            assertTimeoutPreemptively(Duration.ofSeconds(1), controller::close);
            release.countDown(); await(controller);
            assertSame(previous, controller.history()); assertTrue(controller.jobs().isEmpty());
            assertFalse(controller.refreshHistory()); assertEquals(ConversionController.Phase.CANCELLED, controller.snapshot().phase());
        } finally { release.countDown(); controller.close(); }
    }

    @Test void invalidHistoryDoesNotChangeLiveConversionOrInstallationAuthority() throws Exception {
        try (var controller = converted()) {
            List<JobView> before = controller.jobs();
            Path parent = before.get(0).compatibilityReport().orElseThrow().getParent();
            Path corrupt = Files.writeString(parent.resolve("job-" + UUID.randomUUID() + ".json"), "{broken");
            assertTrue(controller.refreshHistory()); await(controller);
            assertEquals(JobHistory.Status.PARTIAL, controller.history().status());
            assertEquals(before, controller.jobs()); assertEquals("HISTORY_INCOMPLETE", controller.globalError().orElseThrow().code());
            assertTrue(controller.history().jobs().get(0).highestRecorded().isEmpty());
            // Correction is an explicit test fixture operation, never reader cleanup.
            Files.delete(corrupt); assertTrue(controller.refreshHistory()); await(controller);
            assertEquals(JobHistory.Status.COMPLETE, controller.history().status()); assertTrue(controller.globalError().isEmpty());
        }
    }

    private ConversionController converted() throws Exception {
        Path input = Files.createDirectories(game.resolve("legacybridge/input"));
        ReceiptSourceFixture.write(input.resolve("source.jar"), "history_fixture");
        var controller = new ConversionController(game);
        try {
            assertTrue(controller.refresh()); await(controller); assertTrue(controller.enqueue(List.of(0), "")); await(controller);
            assertEquals(JobState.CONVERTED, controller.jobs().get(0).status()); return controller;
        } catch (Throwable error) { controller.close(); throw error; }
    }
    private Map<Path, byte[]> bytes() throws Exception {
        Map<Path, byte[]> result = new LinkedHashMap<>();
        try (var files = Files.walk(game)) { for (Path file : files.filter(Files::isRegularFile).toList()) result.put(file, Files.readAllBytes(file)); }
        return result;
    }
    private void unchanged(Map<Path, byte[]> before) throws Exception {
        var after = bytes(); assertEquals(before.keySet(), after.keySet());
        before.forEach((path, data) -> assertArrayEquals(data, after.get(path), path.toString()));
    }
    private static void await(ConversionController value) throws Exception { ConversionQueueTest.await(value); }
    private static ConversionController.InstallOperations noInstaller() {
        return new ConversionController.InstallOperations() {
            public TransactionResult apply(Path root, List<InstallSelection> selected, InstallSession session) { throw new AssertionError("history called installer"); }
            public List<TransactionResult> list(Path root, InstallSession session) { throw new AssertionError("history inspected journal"); }
            public TransactionResult recover(Path root, Path transaction, InstallSession session, RecoveryAction action) { throw new AssertionError("history called recovery"); }
        };
    }
}
