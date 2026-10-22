package dev.legacybridge.product.controller;

import dev.legacybridge.converter.service.EngineError;
import dev.legacybridge.product.install.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Controller concurrency tests only; filesystem commit evidence belongs to installer tests. */
class InstallationControllerTest {
    @TempDir Path game;
    private final InstallSession session = new InstallSession(Set.of("minecraft", "forge", "legacybridge_runtime"), Set.of(), false);

    @Test void unobservedJobsAndUnobservedJournalsCannotBecomeAuthority() throws Exception {
        try (var controller = new ConversionController(game)) {
            assertTrue(controller.applyJobs(Map.of(UUID.randomUUID(), Optional.empty()), session).isEmpty());
            assertTrue(controller.recoverTransaction(game.resolve("arbitrary"), RecoveryAction.RESTORE, session).isEmpty());
            assertFalse(Files.exists(game.resolve("mods")));
            assertTrue(controller.installation().operationId().isEmpty());
        }
    }

    @Test void actualListingReportsCorruptJournalAndNeverChangesMods() throws Exception {
        for (String name : List.of("input", "staging", "reports", "cache", "transactions")) Files.createDirectories(game.resolve("legacybridge").resolve(name));
        Files.createDirectory(game.resolve("mods"));
        Path journal = Files.createDirectory(game.resolve("legacybridge/transactions").resolve(UUID.randomUUID().toString()));
        Path corrupt = Files.writeString(journal.resolve("plan-v2.json"), "{partial");
        try (var controller = new ConversionController(game)) {
            assertTrue(controller.refreshTransactions(session)); ConversionQueueTest.await(controller);
            assertEquals(1, controller.installation().transactions().size());
            assertTrue(controller.installation().transactions().get(0).error().isPresent());
            assertEquals("{partial", Files.readString(corrupt));
            try (var mods = Files.list(game.resolve("mods"))) { assertEquals(0, mods.count()); }
            assertTrue(controller.recoverTransaction(journal, RecoveryAction.RESTORE,
                    new InstallSession(session.loadedModIds(), Set.of(), true)).isEmpty());
            assertEquals("WORLD_OPEN", controller.installation().error().orElseThrow().code());
        }
    }

    @Test void acceptedRecoveryIsSerialNonCancellableAndSurvivesScreenClosure() throws Exception {
        var operations = new BlockingOperations();
        var controller = new ConversionController(game, operations);
        try {
            assertTrue(controller.refreshTransactions(session)); ConversionQueueTest.await(controller);
            UUID operation = controller.recoverTransaction(operations.result.transaction(), RecoveryAction.RESUME, session).orElseThrow();
            assertTrue(operations.entered.await(5, TimeUnit.SECONDS));
            assertTrue(controller.isInstalling()); assertFalse(controller.canCancel()); assertFalse(controller.cancel());
            assertFalse(controller.refresh()); assertFalse(controller.clearFinished());
            assertTrue(controller.recoverTransaction(operations.result.transaction(), RecoveryAction.RESTORE, session).isEmpty());
            assertTimeout(Duration.ofSeconds(1), controller::close);
            assertTrue(controller.isInstalling(), "Closing a screen must not cancel the accepted disk operation");
            operations.release.countDown(); ConversionQueueTest.await(controller);
            assertEquals(operation, controller.installation().operationId().orElseThrow());
            assertTrue(controller.installation().result().orElseThrow().safeToClose());
            assertFalse(controller.isInstalling());
            assertEquals("legacybridge-controller", operations.threadName);
        } finally { operations.release.countDown(); controller.close(); }
    }

    @Test void partialRecoveryErrorNeverBecomesPermissionToCloseAndInventoryRetryStaysUsable() throws Exception {
        var operations = new BlockingOperations();
        operations.result = new TransactionResult(operations.result.transaction(), TransactionResult.Status.PARTIAL, List.of(),
                Optional.of(new EngineError("TEST_DISK_FULL", "Synthetic controller-only failure", "Inspect journal")));
        operations.release.countDown();
        try (var controller = new ConversionController(game, operations)) {
            assertTrue(controller.refreshTransactions(session)); ConversionQueueTest.await(controller);
            assertTrue(controller.recoverTransaction(operations.result.transaction(), RecoveryAction.RESUME, session).isPresent());
            ConversionQueueTest.await(controller);
            assertFalse(controller.installation().result().orElseThrow().safeToClose());
            assertEquals("TEST_DISK_FULL", controller.installation().error().orElseThrow().code());
            assertTrue(controller.refreshTransactions(session)); ConversionQueueTest.await(controller);
            assertFalse(controller.installation().result().orElseThrow().safeToClose(), "Inspection cannot upgrade a failed operation into safe-close evidence");
        }
    }

    private final class BlockingOperations implements ConversionController.InstallOperations {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile String threadName;
        volatile TransactionResult result = new TransactionResult(game.resolve("synthetic-controller-only"), TransactionResult.Status.COMMITTED, List.of(), Optional.empty());
        public List<TransactionResult> list(Path root, InstallSession observed) { return List.of(result); }
        public TransactionResult apply(Path root, List<InstallSelection> selection, InstallSession observed) { throw new AssertionError("Not a conversion fixture"); }
        public TransactionResult recover(Path root, Path transaction, InstallSession observed, RecoveryAction action) {
            threadName = Thread.currentThread().getName(); entered.countDown();
            try { assertTrue(release.await(10, TimeUnit.SECONDS), "Test did not release its synthetic I/O seam"); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
            return result;
        }
    }
}
