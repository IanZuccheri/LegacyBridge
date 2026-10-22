package dev.legacybridge.product.controller;

import dev.legacybridge.converter.service.*;
import dev.legacybridge.product.history.*;
import dev.legacybridge.product.install.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual authored original/converted files, real inspector/installer, inert platform metadata; no game/class execution. */
@EnabledIfSystemProperty(named="legacybridge.admissionBundle", matches=".+")
class PreservedInstallCoordinatorTest {
    static final String SOURCE="49dfbd7c65918c16ce8c057df992f7e9b7c188a3fe5634adb42bb1bc5dbfc19f";
    static final String TARGET="78de0e2fd62ed83315518a1980ae0738b354531736a5eea73195d332aecc186d";
    static final String DATA="0edd3ba641dbf858b7464096794a3caf4efca9d04ef4484a740a9599e9aec2bb";
    static final InstallSession SESSION=new InstallSession(Set.of("minecraft","forge","legacybridge_runtime"),Set.of(),false);
    @TempDir Path game;
    Path original, staged; PreservedInstallSelection selection;
    AtomicReference<InstallSession> session=new AtomicReference<>(SESSION);
    @BeforeEach void prepare() throws Exception {
        for(String area:List.of("input","staging","reports","cache","transactions")) Files.createDirectories(game.resolve("legacybridge/"+area));
        ReceiptInstallerFixtures.platform(game);
        original=Files.copy(Path.of(System.getProperty("legacybridge.admissionOriginal")),game.resolve("legacybridge/input/original.jar"));
        staged=Files.copy(Path.of(System.getProperty("legacybridge.admissionCandidate")),game.resolve("legacybridge/staging/candidate.jar"));
        var data=MappingData.reviewedBundle(Path.of(System.getProperty("legacybridge.admissionBundle")),DATA);
        var admission=new PreservedAdmissionStore().prepare(game,new PreservedArtifactRequest(original,SOURCE,staged,TARGET,data));
        assertEquals(PreservedAdmissionResult.State.RECORDED_NOT_INSTALLABLE,admission.state(),admission.toString());
        selection=new PreservedInstallSelection(admission.transaction().orElseThrow(),admission.planSha256().orElseThrow(),data,Optional.empty());
    }
    ConversionController controller() { return ConversionController.forPreservedDevelopment(game,session::get); }
    @Test void developerFormBindsRealReviewAndCannotRehydrateConsentFromTerminalObservation() throws Exception {
        var form = new PreservedDeveloperForm();
        form.set(PreservedDeveloperForm.Field.ADMISSION, selection.admission().toString());
        form.set(PreservedDeveloperForm.Field.ADMISSION_HASH, selection.admissionSha256());
        form.set(PreservedDeveloperForm.Field.DATA, selection.data().path().orElseThrow().toString());
        form.set(PreservedDeveloperForm.Field.DATA_HASH, DATA);
        try(var c=controller()) {
            form.reviewStarted(c.reviewPreservedInstall(form.selection()).orElseThrow()); idle(c);
            assertTrue(form.canConfirm(c.preservedInstallation(),false));
            assertTrue(c.confirmPreservedInstall(form.consume(c.preservedInstallation(),false).orElseThrow()).isPresent()); idle(c);
            assertEquals(PreservedInstallView.State.PENDING_RESTART,c.preservedInstallation().state());
            assertTrue(form.consume(c.preservedInstallation(),false).isEmpty());
            assertEquals(SOURCE,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(original))));
            assertTrue(c.refreshPreservedHistory()); idle(c);
            assertFalse(new PreservedDeveloperForm().canConfirm(c.preservedInstallation(),false));
            assertTrue(c.preservedHistory().receipts().stream().noneMatch(PreservedInstallReceipts.Receipt::installationAllowed));
        }
    }
    @Test void unavailableHostAtConfirmationPreservesCauseAndConsumesReview() throws Exception {
        try(var c=controller()) {
            UUID id=review(c); session.set(null); assertTrue(c.confirmPreservedInstall(id).isEmpty());
            assertEquals("SESSION_UNAVAILABLE",c.preservedInstallation().error().orElseThrow().code());
            session.set(SESSION); assertTrue(c.confirmPreservedInstall(id).isEmpty());
        }
    }
    UUID review(ConversionController c) throws Exception {
        assertTrue(c.reviewPreservedInstall(selection).isPresent()); idle(c);
        assertEquals(PreservedInstallView.State.REVIEWED,c.preservedInstallation().state(),c.preservedInstallation().toString());
        assertFalse(c.preservedInstallation().safeToClose());
        return c.preservedInstallation().reviewId().orElseThrow();
    }
    static void idle(ConversionController c) throws Exception {
        long end=System.nanoTime()+Duration.ofSeconds(45).toNanos();
        while(c.snapshot().busy() && System.nanoTime()<end) Thread.sleep(10);
        assertFalse(c.snapshot().busy(),c.snapshot().toString());
    }
    @Test void publicConstructorCannotReviewConfirmOrReadPreservedAuthority() throws Exception {
        try(var c=new ConversionController(game)) {
            assertEquals(PreservedInstallView.State.DISABLED,c.preservedInstallation().state());
            assertTrue(c.reviewPreservedInstall(selection).isEmpty()); assertTrue(c.confirmPreservedInstall(UUID.randomUUID()).isEmpty());
            assertFalse(c.refreshPreservedHistory()); assertTrue(c.jobs().isEmpty());
        }
    }
    @Test void actualReviewApprovalInstallHistoryFreshControllerAndRestoreAreSeparated() throws Exception {
        UUID oldReview; PreservedInstallResult installed; UUID approval;
        try(var c=controller()) {
            oldReview=review(c); byte[] reviewBytes=Files.readAllBytes(c.preservedInstallation().receipt().orElseThrow());
            try(var jars=Files.list(game.resolve("mods"))) { assertEquals(1,jars.count()); }
            assertTrue(c.jobs().isEmpty()); assertTrue(c.applyJob(oldReview,SESSION.loadedModIds())==false);
            Path reviewReceipt=c.preservedInstallation().receipt().orElseThrow();
            assertTrue(c.confirmPreservedInstall(oldReview).isPresent()); idle(c);
            var view=c.preservedInstallation(); assertEquals(PreservedInstallView.State.PENDING_RESTART,view.state(),view.toString());
            assertTrue(view.safeToClose()); assertEquals("NOT_OBSERVED",view.activation()); installed=view.result().orElseThrow();
            assertTrue(c.confirmPreservedInstall(oldReview).isEmpty()); assertTrue(c.installation().result().isEmpty());
            assertArrayEquals(reviewBytes,Files.readAllBytes(reviewReceipt));
            assertTrue(c.refreshPreservedHistory()); idle(c);
            var history=c.preservedHistory(); assertEquals(PreservedInstallReceipts.Status.COMPLETE,history.status(),history.toString());
            assertEquals(2,history.receipts().size());
            assertTrue(history.receipts().stream().noneMatch(r->r.installationAllowed()||r.safeToClose()));
            approval=history.receipts().stream().filter(r->r.action()==PreservedInstallReceipts.Action.APPLY).findFirst().orElseThrow().approvalId().orElseThrow();
            assertEquals(JobHistory.Status.COMPLETE,new JobHistoryReader().read(game,()->false).status());
        }
        try(var c=controller()) {
            assertTrue(c.refreshPreservedHistory()); idle(c); assertTrue(c.jobs().isEmpty());
            assertTrue(c.confirmPreservedInstall(oldReview).isEmpty());
            selection=new PreservedInstallSelection(selection.admission(),selection.admissionSha256(),selection.data(),Optional.of(
                    new PreservedInstallSelection.Recovery(installed.transaction().orElseThrow(),installed.planSha256().orElseThrow(),approval,RecoveryAction.RESTORE)));
            assertTrue(c.confirmPreservedInstall(review(c)).isPresent()); idle(c);
            assertEquals(PreservedInstallView.State.RESTORED,c.preservedInstallation().state(),c.preservedInstallation().toString());
            assertTrue(c.preservedInstallation().safeToClose()); assertFalse(Files.exists(installed.installed().orElseThrow()));
            assertEquals(TARGET,hash(staged)); assertEquals(SOURCE,hash(original));
        }
    }
    @Test void changedBytesAfterReviewRefuseWithoutPublishingAndConsumeConfirmation() throws Exception {
        try(var c=controller()) {
            UUID id=review(c); Files.write(staged,new byte[]{1,2,3});
            assertTrue(c.confirmPreservedInstall(id).isPresent()); idle(c);
            assertEquals(PreservedInstallView.State.FAILED,c.preservedInstallation().state());
            assertFalse(c.preservedInstallation().safeToClose()); assertTrue(c.confirmPreservedInstall(id).isEmpty());
            try(var jars=Files.list(game.resolve("mods"))) { assertEquals(1,jars.count()); }
        }
    }
    @Test void worldOpenAndChangedLoadedSnapshotInvalidateReviewWithoutIO() throws Exception {
        try(var c=controller()) {
            UUID id=review(c); session.set(new InstallSession(SESSION.loadedModIds(),Set.of(),true));
            assertTrue(c.confirmPreservedInstall(id).isEmpty()); assertEquals("SESSION_CHANGED",c.preservedInstallation().error().orElseThrow().code());
            assertTrue(c.reviewPreservedInstall(selection).isEmpty()); assertEquals("WORLD_OPEN",c.preservedInstallation().error().orElseThrow().code());
            session.set(SESSION); assertTrue(c.confirmPreservedInstall(id).isEmpty());
        }
    }
    @Test void invalidAdmissionPinCannotBecomeAReview() throws Exception {
        selection=new PreservedInstallSelection(selection.admission(),"a".repeat(64),selection.data(),Optional.empty());
        try(var c=controller()) {
            assertTrue(c.reviewPreservedInstall(selection).isPresent()); idle(c);
            assertEquals(PreservedInstallView.State.BLOCKED,c.preservedInstallation().state());
            assertTrue(c.confirmPreservedInstall(c.preservedInstallation().reviewId().orElseThrow()).isEmpty());
        }
    }
    @Test void receiptFailureAfterRealCommitRetainsTypedOutcomeAndBlocksSafeClose() throws Exception {
        var store=new PreservedInstallReceipts();
        try(var c=new ConversionController(game,session::get,new ConversionController.RealPreservedOperations(),(root,s,action,a,v)->{
            if(action==PreservedInstallReceipts.Action.APPLY) throw new IOException("injected disk full during receipt");
            return store.write(root,s,action,a,v);
        })) {
            UUID id=review(c); assertTrue(c.confirmPreservedInstall(id).isPresent()); idle(c);
            var v=c.preservedInstallation(); assertEquals("PRESERVED_RECEIPT_WRITE_FAILED",v.error().orElseThrow().code());
            assertEquals(PreservedInstallResult.State.COMMITTED,v.result().orElseThrow().state());
            assertTrue(Files.exists(v.result().orElseThrow().installed().orElseThrow())); assertFalse(v.safeToClose());
            assertTrue(c.refreshPreservedHistory()); idle(c); assertFalse(c.preservedInstallation().safeToClose());
            assertEquals("PRESERVED_RECEIPT_WRITE_FAILED",c.installation().error().orElseThrow().code());
        }
    }
    @Test void receiptFailureDuringReviewNeverCreatesApproval() throws Exception {
        try(var c=new ConversionController(game,session::get,new ConversionController.RealPreservedOperations(),(root,s,action,a,v)->{
            throw new IOException("injected deny write");
        })) {
            assertTrue(c.reviewPreservedInstall(selection).isPresent()); idle(c);
            assertTrue(c.confirmPreservedInstall(c.preservedInstallation().reviewId().orElseThrow()).isEmpty());
            assertFalse(c.preservedInstallation().safeToClose());
        }
    }
    @Test void closeIsNonblockingAndAcceptedInstallRetainsGateThroughReceiptFlush() throws Exception {
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1); var store=new PreservedInstallReceipts();
        var c=new ConversionController(game,session::get,new ConversionController.RealPreservedOperations(),(root,s,action,a,v)->{
            if(action==PreservedInstallReceipts.Action.APPLY) {
                assertEquals("legacybridge-controller",Thread.currentThread().getName()); entered.countDown();
                try { if(!release.await(30,TimeUnit.SECONDS)) throw new IOException("test release timeout"); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            }
            return store.write(root,s,action,a,v);
        });
        try {
            UUID id=review(c); assertTrue(c.confirmPreservedInstall(id).isPresent()); assertTrue(entered.await(30,TimeUnit.SECONDS));
            assertTrue(c.isInstalling()); assertFalse(c.cancel()); assertFalse(c.refreshHistory()); assertFalse(c.refreshTransactions(SESSION));
            assertTrue(c.reviewPreservedInstall(selection).isEmpty()); assertFalse(c.enqueue(List.of(0),""));
            assertTimeout(Duration.ofSeconds(1),c::close); assertTrue(c.isInstalling()); assertFalse(c.preservedInstallation().safeToClose());
        } finally { release.countDown(); idle(c); c.close(); }
        assertFalse(c.isInstalling()); assertTrue(c.preservedInstallation().safeToClose(),c.preservedInstallation().toString());
    }
    @Test void providerRecheckedAtDispatchAndThrowsAreStructured() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        try(var c=ConversionController.forPreservedDevelopment(game,()->{
            if(calls.incrementAndGet()>1) return new InstallSession(SESSION.loadedModIds(),Set.of(),true); return SESSION;
        })) {
            assertTrue(c.reviewPreservedInstall(selection).isPresent()); idle(c);
            assertEquals("SESSION_CHANGED",c.preservedInstallation().error().orElseThrow().code());
            assertTrue(c.confirmPreservedInstall(c.preservedInstallation().reviewId().orElseThrow()).isEmpty());
        }
        try(var c=ConversionController.forPreservedDevelopment(game,()->{throw new IllegalStateException("synthetic host unavailable");})) {
            assertTrue(c.reviewPreservedInstall(selection).isEmpty());
            assertEquals("SESSION_UNAVAILABLE",c.preservedInstallation().error().orElseThrow().code());
        }
    }
    static String hash(Path path) throws Exception { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
}
