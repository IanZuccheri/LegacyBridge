package dev.legacybridge.product.controller;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.product.install.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Actual inert conversion + disk installer, not a Minecraft or discovery test. */
class JobInstallationReceiptTest {
    @TempDir Path game;
    private final InstallSession session = new InstallSession(Set.of("minecraft", "forge", "legacybridge_runtime"), Set.of(), false);

    @Test void applyAppendsEveryApprovedJobAndKeepsOriginalConversionReceiptAndUnselectedJob() throws Exception {
        try (var controller = converted(new Operations(), 3)) {
            List<JobView> jobs = controller.jobs();
            Map<Path, byte[]> before = receiptBytes();
            byte[] original = Files.readAllBytes(jobs.get(0).input());
            assertFalse(Files.exists(game.resolve("mods")));
            ReceiptInstallerFixtures.platform(game);
            UUID operation = controller.applyJobs(approvals(jobs.subList(0, 2)), session).orElseThrow(); await(controller);
            assertEquals(List.of(JobState.PENDING_RESTART, JobState.PENDING_RESTART, JobState.CONVERTED),
                    controller.jobs().stream().map(JobView::status).toList());
            assertEquals(5, receiptBytes().size());
            for (int i = 0; i < 2; i++) {
                Map<?, ?> receipt = last(jobs.get(i));
                assertEquals(2, number(receipt.get("schemaVersion"))); assertEquals(2, number(receipt.get("sequence")));
                assertEquals("INSTALLATION", receipt.get("event")); assertEquals("PENDING_RESTART", receipt.get("state"));
                assertEquals("NOT_OBSERVED", receipt.get("activation"));
                assertEquals(operation.toString(), observation(receipt).get("operationId"));
                assertEquals(i, number(observation(receipt).get("entryIndex")));
                assertEquals("COMMITTED", observation(receipt).get("transactionStatus"));
                assertEquals("INSTALLED", observation(receipt).get("entryState"));
                assertEquals("APPLY", observation(receipt).get("action"));
                assertEquals(true, observation(receipt).get("transactionSafeToClose"));
                assertEquals(controller.jobs().get(i).installed().orElseThrow().toString(), receipt.get("installed"));
                assertNotNull(receipt.get("artifactSha256"));
                assertEquals(receipt.get("transaction"), observation(receipt).get("transaction"));
            }
            for (var entry : before.entrySet()) assertArrayEquals(entry.getValue(), Files.readAllBytes(entry.getKey()));
            assertArrayEquals(original, Files.readAllBytes(jobs.get(0).input()));
            assertEquals(session.loadedModIds(), Set.of("minecraft", "forge", "legacybridge_runtime"));
            assertEquals(1, receipts(jobs.get(2)).size());
            assertTrue(controller.installation().error().isEmpty());
        }
    }

    @Test void partialBatchResumeAndRestoreAppendOrderedPerEntryObservations() throws Exception {
        var operations = new Operations(); operations.applyInstaller = ReceiptInstallerFixtures.partial();
        try (var controller = converted(operations, 2)) {
            var jobs = controller.jobs(); ReceiptInstallerFixtures.platform(game);
            controller.applyJobs(approvals(jobs), session).orElseThrow(); await(controller);
            var partial = controller.installation().result().orElseThrow(); assertEquals(TransactionResult.Status.PARTIAL, partial.status());
            for (var job : jobs) {
                assertEquals("PARTIAL", observation(last(job)).get("transactionStatus"));
                assertEquals(false, observation(last(job)).get("transactionSafeToClose"));
            }
            assertEquals("PENDING_RESTART", last(jobs.get(0)).get("state"));
            assertEquals("CONVERTED", last(jobs.get(1)).get("state"));
            UUID resumed = controller.recoverTransaction(partial.transaction(), RecoveryAction.RESUME, session).orElseThrow(); await(controller);
            for (var job : jobs) {
                assertEquals(3, number(last(job).get("sequence")));
                assertEquals("COMMITTED", observation(last(job)).get("transactionStatus"));
                assertEquals(resumed.toString(), observation(last(job)).get("operationId"));
                assertNull(last(job).get("error"));
            }
            controller.recoverTransaction(partial.transaction(), RecoveryAction.RESTORE, session).orElseThrow(); await(controller);
            for (var job : jobs) {
                assertEquals(4, receipts(job).size()); assertEquals("CONVERTED", last(job).get("state"));
                assertEquals("RESTORED", observation(last(job)).get("entryState"));
                assertEquals("RESTORE", observation(last(job)).get("action")); assertNull(last(job).get("installed"));
            }
        }
    }

    @Test void recoveryDoesNotAttachToAnUnapprovedReconversionOfTheSameModId() throws Exception {
        try (var controller = converted(new Operations(), 1)) {
            ReceiptInstallerFixtures.platform(game);
            var first = controller.jobs().get(0);
            controller.applyJobs(approvals(List.of(first)), session).orElseThrow(); await(controller);
            assertTrue(controller.enqueue(List.of(0), "")); await(controller);
            var second = controller.jobs().get(1);
            assertEquals(first.input(), second.input()); assertEquals(JobState.CONVERTED, second.status());
            Path transaction = controller.installation().result().orElseThrow().transaction();
            controller.recoverTransaction(transaction, RecoveryAction.RESTORE, session).orElseThrow(); await(controller);
            assertEquals(3, receipts(first).size()); assertEquals(1, receipts(second).size());
            assertNull(last(second).get("transaction")); assertNull(last(second).get("installation"));
        }
    }

    @Test void missingRecoveryEntriesCannotHideBehindAnEarlierPartialError() throws Exception {
        var operations = new Operations(); operations.applyInstaller = ReceiptInstallerFixtures.partial();
        try (var controller = converted(operations, 2)) {
            ReceiptInstallerFixtures.platform(game); var jobs = controller.jobs();
            controller.applyJobs(approvals(jobs), session).orElseThrow(); await(controller);
            var partial = controller.installation().result().orElseThrow();
            assertEquals(TransactionResult.Status.PARTIAL, partial.status());
            operations.omitRecoveryEntries = true;
            controller.recoverTransaction(partial.transaction(), RecoveryAction.RESUME, session).orElseThrow(); await(controller);
            assertEquals("INSTALL_RESULT_MISMATCH", controller.installation().error().orElseThrow().code());
            for (var job : jobs) {
                assertEquals("INSTALL_RESULT_MISMATCH", ((Map<?, ?>) last(job).get("error")).get("code"));
                assertNull(observation(last(job)).get("entryState"));
            }
        }
    }

    @Test void refusedInstallationPersistsErrorWithoutInventingAnInstalledEntry() throws Exception {
        try (var controller = converted(new Operations(), 1)) {
            ReceiptInstallerFixtures.platform(game); var job = controller.jobs().get(0);
            Files.writeString(job.input(), "test-owned changed source");
            controller.applyJobs(approvals(List.of(job)), session).orElseThrow(); await(controller);
            Map<?, ?> receipt = last(job);
            assertEquals(2, receipts(job).size()); assertNotNull(receipt.get("error"));
            assertNull(receipt.get("installed")); assertNull(observation(receipt).get("entryState"));
            assertEquals(false, observation(receipt).get("transactionSafeToClose"));
            assertFalse(controller.installation().result().orElseThrow().safeToClose());
            assertEquals("test-owned changed source", Files.readString(job.input()));
        }
    }

    @Test void preTransactionExceptionAndUnexpectedExceptionBothPersistFailures() throws Exception {
        var operations = new Operations();
        operations.before = () -> { throw new InstallException("TEST_REFUSAL", "Synthetic refusal", "Inspect", Optional.empty(), Optional.empty()); };
        try (var controller = converted(operations, 1)) {
            var job = controller.jobs().get(0);
            controller.applyJobs(approvals(List.of(job)), session).orElseThrow(); await(controller);
            assertEquals("TEST_REFUSAL", ((Map<?, ?>) last(job).get("error")).get("code"));
            assertEquals(JobState.CONVERTED, controller.jobs().get(0).status());
            assertNull(observation(last(job)).get("transactionStatus"));
            operations.before = () -> { throw new IllegalStateException("Synthetic unexpected failure"); };
            controller.applyJobs(approvals(controller.jobs()), session).orElseThrow(); await(controller);
            assertEquals(3, receipts(job).size()); assertEquals("FAILED", last(job).get("state"));
            assertEquals("INSTALL_OUTCOME_UNKNOWN", ((Map<?, ?>) last(job).get("error")).get("code"));
            assertEquals(false, observation(last(job)).get("transactionSafeToClose"));
        }
    }

    @Test void failedReceiptWriteStaysVisibleKeepsCommitAndStillSavesOtherJobs() throws Exception {
        var operations = new Operations();
        try (var controller = converted(operations, 2)) {
            var jobs = controller.jobs(); ReceiptInstallerFixtures.platform(game);
            Path directory = jobs.get(0).compatibilityReport().orElseThrow().getParent();
            Path saved = directory.resolveSibling(directory.getFileName() + "-test-saved");
            operations.after = () -> { Files.move(directory, saved); Files.writeString(directory, "test-owned collision"); };
            controller.applyJobs(approvals(jobs), session).orElseThrow(); await(controller);
            assertEquals(TransactionResult.Status.COMMITTED, controller.installation().result().orElseThrow().status());
            assertEquals("JOB_REPORT_WRITE_FAILED", controller.installation().error().orElseThrow().code());
            assertEquals("JOB_REPORT_WRITE_FAILED", controller.globalError().orElseThrow().code());
            assertEquals(2, receipts(jobs.get(1)).size());
            for (var job : controller.jobs()) {
                assertEquals(JobState.PENDING_RESTART, job.status()); assertTrue(Files.isRegularFile(job.installed().orElseThrow()));
            }
            assertEquals("test-owned collision", Files.readString(directory));
            assertTrue(Files.isDirectory(saved));
            assertTrue(controller.refreshTransactions(session)); await(controller);
            assertEquals("JOB_REPORT_WRITE_FAILED", controller.installation().error().orElseThrow().code(),
                    "Read-only inspection must not turn a missing presentation receipt into a successful close grant");
        }
    }

    @ParameterizedTest @ValueSource(strings = {"HASH", "VERSION", "MOD_ID", "INDEX", "MISSING"})
    void mismatchedOrMissingEntryCannotBindJobOrAuthorizeAutomaticClosing(String defect) throws Exception {
        var operations = new Operations(); operations.defect = defect;
        try (var controller = converted(operations, 1)) {
            ReceiptInstallerFixtures.platform(game); var job = controller.jobs().get(0);
            controller.applyJobs(approvals(List.of(job)), session).orElseThrow(); await(controller);
            assertEquals("FAILED", last(job).get("state")); assertNull(last(job).get("installed"));
            assertEquals("INSTALL_RESULT_MISMATCH", controller.installation().error().orElseThrow().code());
            assertEquals("INSTALL_RESULT_MISMATCH", ((Map<?, ?>) last(job).get("error")).get("code"));
        }
    }

    @Test void worldOpenRefusalDoesNotCreateReceiptOrStartAnInstallation() throws Exception {
        try (var controller = converted(new Operations(), 1)) {
            var job = controller.jobs().get(0);
            assertTrue(controller.applyJobs(approvals(List.of(job)), new InstallSession(session.loadedModIds(), Set.of(), true)).isEmpty());
            assertEquals(1, receipts(job).size()); assertFalse(Files.exists(game.resolve("mods")));
        }
    }

    @Test void closingControllerDuringApplyDoesNotLoseFinalReceipt() throws Exception {
        var operations = new Operations(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        operations.after = () -> { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); };
        var controller = converted(operations, 1);
        try {
            ReceiptInstallerFixtures.platform(game); var job = controller.jobs().get(0);
            controller.applyJobs(approvals(List.of(job)), session).orElseThrow();
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertTrue(controller.isInstalling()); assertFalse(controller.cancel());
            assertTimeout(Duration.ofSeconds(1), controller::close);
            release.countDown(); await(controller);
            assertEquals(2, receipts(job).size()); assertEquals("PENDING_RESTART", last(job).get("state"));
            assertFalse(controller.isInstalling()); assertEquals("legacybridge-controller", operations.thread);
        } finally { release.countDown(); controller.close(); }
    }

    private ConversionController converted(Operations operations, int count) throws Exception {
        Path input = Files.createDirectories(game.resolve("legacybridge/input"));
        for (int i = 0; i < count; i++) ReceiptSourceFixture.write(input.resolve("input-" + i + ".jar"), "receipt_" + i);
        var controller = new ConversionController(game, operations);
        try {
            assertTrue(controller.refresh()); await(controller);
            assertTrue(controller.enqueue(java.util.stream.IntStream.range(0, count).boxed().toList(), "")); await(controller);
            for (var job : controller.jobs()) assertEquals(JobState.CONVERTED, job.status(), job.toString());
            return controller;
        } catch (Throwable failure) { controller.close(); throw failure; }
    }
    private static void await(ConversionController controller) throws Exception { ConversionQueueTest.await(controller); }
    private static Map<UUID, Optional<Path>> approvals(List<JobView> jobs) {
        Map<UUID, Optional<Path>> result = new LinkedHashMap<>(); jobs.forEach(job -> result.put(job.id(), Optional.empty())); return result;
    }
    private List<Map<?, ?>> receipts(JobView job) throws Exception {
        List<Map<?, ?>> result = new ArrayList<>();
        try (var files = Files.list(job.compatibilityReport().orElseThrow().getParent())) {
            for (Path path : files.filter(path -> path.getFileName().toString().startsWith("job-")).toList()) {
                Map<?, ?> value = (Map<?, ?>) BoundedJsonParser.parse(Files.readString(path));
                if (job.id().toString().equals(value.get("jobId"))) result.add(value);
            }
        }
        result.sort(Comparator.comparingInt(value -> number(value.get("sequence")))); return result;
    }
    private Map<?, ?> last(JobView job) throws Exception { var receipts = receipts(job); return receipts.get(receipts.size() - 1); }
    private static Map<?, ?> observation(Map<?, ?> receipt) { return (Map<?, ?>) receipt.get("installation"); }
    private static int number(Object value) { return ((Number) value).intValue(); }
    private Map<Path, byte[]> receiptBytes() throws Exception {
        Map<Path, byte[]> result = new LinkedHashMap<>();
        try (var files = Files.walk(game.resolve("legacybridge/reports"))) {
            for (Path path : files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().startsWith("job-")).toList())
                result.put(path, Files.readAllBytes(path));
        }
        return result;
    }
    @FunctionalInterface private interface Step { void run() throws Exception; }
    private static final class Operations implements ConversionController.InstallOperations {
        TransactionalInstaller applyInstaller = new TransactionalInstaller();
        Step before = () -> { }, after = () -> { }; String defect; String thread; boolean omitRecoveryEntries;
        public TransactionResult apply(Path root, List<InstallSelection> selected, InstallSession session) throws InstallException {
            thread = Thread.currentThread().getName();
            try {
                before.run(); var result = applyInstaller.apply(root, selected, session); after.run();
                if (defect == null) return result;
                if (defect.equals("MISSING")) return new TransactionResult(result.transaction(), result.status(), List.of(), result.error());
                var entry = result.entries().get(0);
                return new TransactionResult(result.transaction(), result.status(), List.of(new TransactionResult.EntryResult(
                        defect.equals("INDEX") ? 9 : entry.index(), defect.equals("MOD_ID") ? "different_mod" : entry.modId(),
                        defect.equals("VERSION") ? "999" : entry.version(), entry.state(), entry.installed(),
                        defect.equals("HASH") ? "0".repeat(64) : entry.sha256(), entry.error())), result.error());
            } catch (InstallException error) { throw error; }
            catch (RuntimeException error) { throw error; }
            catch (Exception error) { throw new RuntimeException(error); }
        }
        public List<TransactionResult> list(Path root, InstallSession session) throws InstallException { return new TransactionalInstaller().list(root, session); }
        public TransactionResult recover(Path root, Path transaction, InstallSession session, RecoveryAction action) throws InstallException {
            var result = new TransactionalInstaller().recover(root, transaction, session, action);
            return omitRecoveryEntries ? new TransactionResult(result.transaction(), result.status(), List.of(), result.error()) : result;
        }
    }
}
