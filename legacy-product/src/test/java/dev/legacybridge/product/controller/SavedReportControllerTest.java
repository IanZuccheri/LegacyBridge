package dev.legacybridge.product.controller;

import dev.legacybridge.product.history.*;
import dev.legacybridge.product.install.InstallSession;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import dev.legacybridge.product.install.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SavedReportControllerTest {
    @TempDir Path game;
    @Test void newConversionReportsSurviveReopeningWithoutApplyAuthorityOrDiskWrites() throws Exception {
        UUID job;
        try(var controller=converted()) {
            job=controller.jobs().get(0).id();
            assertTrue(controller.jobs().get(0).error().isEmpty(),controller.jobs().get(0).error().toString());
        }
        var before=snapshot();
        try(var reopened=new ConversionController(game)) {
            assertFalse(reopened.readSavedReport(job,SavedReport.Kind.CONVERSION));
            assertTrue(reopened.refreshHistory()); await(reopened);
            for(var kind:SavedReport.Kind.values()) {
                assertTrue(reopened.readSavedReport(job,kind)); await(reopened);
                var report=reopened.savedReport().orElseThrow(()->new AssertionError(reopened.globalError()));
                assertEquals(kind,report.kind()); assertEquals(job,report.jobId());
            }
            assertTrue(reopened.jobs().isEmpty());
            assertTrue(reopened.applyJobs(Map.of(job,Optional.empty()),new InstallSession(Set.of(),Set.of(),false)).isEmpty());
            assertFalse(reopened.readSavedReport(UUID.randomUUID(),SavedReport.Kind.COMPATIBILITY));
        }
        assertEquals(before,snapshot());
    }
    @Test void tamperedReportClearsPreviousSnapshotAndLeavesReaderUsable() throws Exception {
        try(var controller=converted()) {
            JobView job=controller.jobs().get(0); assertTrue(controller.refreshHistory()); await(controller);
            assertTrue(controller.readSavedReport(job.id(),SavedReport.Kind.CONVERSION)); await(controller); assertTrue(controller.savedReport().isPresent());
            Path report=job.conversionReport().orElseThrow(); byte[] original=Files.readAllBytes(report);
            Files.writeString(report," ",StandardOpenOption.APPEND);
            assertTrue(controller.readSavedReport(job.id(),SavedReport.Kind.CONVERSION)); await(controller);
            assertTrue(controller.savedReport().isEmpty()); assertEquals("REPORT_HASH_MISMATCH",controller.globalError().orElseThrow().code());
            assertEquals(JobState.CONVERTED,controller.jobs().get(0).status());
            Files.write(report,original); assertTrue(controller.readSavedReport(job.id(),SavedReport.Kind.CONVERSION)); await(controller); assertTrue(controller.savedReport().isPresent());
        }
    }
    @Test void oldJobsKeepTheirReceiptButHaveNoReconstructedReportLinks() throws Exception {
        UUID job;
        try(var controller=converted()) { job=controller.jobs().get(0).id(); }
        try(var files=Files.walk(game.resolve("legacybridge/reports"))) {
            for(Path path:files.filter(path->path.getFileName().toString().equals("reports-"+job+".json")).toList())Files.delete(path);
        }
        var before=snapshot();
        try(var controller=new ConversionController(game)) {
            assertTrue(controller.refreshHistory()); await(controller);
            assertTrue(controller.readSavedReport(job,SavedReport.Kind.COMPATIBILITY)); await(controller);
            assertEquals("REPORT_INDEX_MISSING",controller.globalError().orElseThrow().code());
            assertEquals(1,controller.history().jobs().size()); assertTrue(controller.jobs().isEmpty());
        }
        assertEquals(before,snapshot());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void cancellationAndCloseRefuseLateSuccessOnTheSingleWorker(boolean close) throws Exception {
        UUID job; try(var original=converted()) { job=original.jobs().get(0).id(); }
        var before=snapshot(); var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        var worker=new AtomicReference<String>();
        var controller=new ConversionController(game, noInstaller(), new JobHistoryReader()::read, (root, receipt, kind, cancel)-> {
            worker.set(Thread.currentThread().getName());
            var result=new SavedReportStore().read(root,receipt,kind,()->false);
            assertTrue(result.value().isPresent()); entered.countDown();
            try { assertTrue(release.await(10,TimeUnit.SECONDS)); }
            catch(InterruptedException failure) { throw new AssertionError(failure); }
            return result; // Deliberately stale success: controller must reject it after cancellation.
        });
        try {
            assertTrue(controller.refreshHistory()); await(controller);
            assertTrue(controller.readSavedReport(job,SavedReport.Kind.CONVERSION)); assertTrue(entered.await(10,TimeUnit.SECONDS));
            assertEquals("legacybridge-controller",worker.get());
            assertFalse(controller.readSavedReport(job,SavedReport.Kind.COMPATIBILITY));
            assertFalse(controller.refreshHistory()); assertFalse(controller.refresh());
            if(close) assertTimeoutPreemptively(Duration.ofSeconds(1),controller::close);
            else assertTrue(controller.cancel());
            release.countDown(); await(controller);
            assertTrue(controller.savedReport().isEmpty()); assertTrue(controller.jobs().isEmpty());
            assertEquals(ConversionController.Phase.CANCELLED,controller.snapshot().phase());
            if(!close) { assertTrue(controller.readSavedReport(job,SavedReport.Kind.CONVERSION)); await(controller); assertTrue(controller.savedReport().isPresent()); }
            assertEquals(before,snapshot());
        } finally { release.countDown(); controller.close(); }
    }
    @Test void exactCompiledLegacyFixtureKeepsConversionBytesAndReopensAllReports() throws Exception {
        String fixture=System.getProperty("legacybridge.savedReportFixture","");
        assumeTrue(!fixture.isBlank(),"Explicit compiled Forge fixture was not supplied; no cache discovery");
        Path original=Path.of(fixture); byte[] source=Files.readAllBytes(original);
        assertEquals("00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947",sha(source));
        Path input=Files.createDirectories(game.resolve("legacybridge/input")); Files.copy(original,input.resolve("source.jar"));
        UUID job;
        try(var controller=new ConversionController(game)) {
            assertTrue(controller.refresh()); await(controller); assertTrue(controller.enqueue(List.of(0),"")); await(controller);
            var view=controller.jobs().get(0); assertEquals(JobState.CONVERTED,view.status(),view.error().toString());
            assertTrue(view.error().isEmpty(),view.error().toString()); job=view.id();
            assertEquals("541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb",sha(Files.readAllBytes(view.artifact().orElseThrow())));
        }
        var before=snapshot();
        try(var reopened=new ConversionController(game)) {
            assertTrue(reopened.refreshHistory()); await(reopened);
            for(var kind:SavedReport.Kind.values()) { assertTrue(reopened.readSavedReport(job,kind)); await(reopened); assertTrue(reopened.savedReport().isPresent(),reopened.globalError().toString()); }
            assertTrue(reopened.jobs().isEmpty());
        }
        assertEquals(before,snapshot()); assertArrayEquals(source,Files.readAllBytes(original)); assertFalse(Files.exists(game.resolve("mods")));
    }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static ConversionController.InstallOperations noInstaller() {
        return new ConversionController.InstallOperations() {
            public TransactionResult apply(Path root,List<InstallSelection> selected,InstallSession session) { throw new AssertionError("Saved report called installer"); }
            public List<TransactionResult> list(Path root,InstallSession session) { throw new AssertionError("Saved report inspected journal"); }
            public TransactionResult recover(Path root,Path transaction,InstallSession session,RecoveryAction action) { throw new AssertionError("Saved report called recovery"); }
        };
    }
    private ConversionController converted() throws Exception {
        Path input=Files.createDirectories(game.resolve("legacybridge/input")); ReceiptSourceFixture.write(input.resolve("source.jar"),"saved_reports_fixture");
        var controller=new ConversionController(game);
        try { assertTrue(controller.refresh()); await(controller); assertTrue(controller.enqueue(List.of(0),"")); await(controller);
            assertEquals(JobState.CONVERTED,controller.jobs().get(0).status(),controller.jobs().get(0).error().toString()); return controller;
        } catch(Throwable failure) { controller.close(); throw failure; }
    }
    private static void await(ConversionController controller) throws Exception { ConversionQueueTest.await(controller); }
    private Map<String,String> snapshot() throws Exception { var result=new TreeMap<String,String>();try(var paths=Files.walk(game)){for(Path path:paths.filter(Files::isRegularFile).toList())result.put(game.relativize(path).toString(),Base64.getEncoder().encodeToString(Files.readAllBytes(path)));}return result; }
}
