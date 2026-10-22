package dev.legacybridge.product.controller;

import dev.legacybridge.converter.service.*;
import dev.legacybridge.product.history.*;
import dev.legacybridge.product.install.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static dev.legacybridge.product.controller.PreservedInstallCoordinatorTest.*;

/** Real authored fixture conversion and actual disk installers. No game or original class loading. */
@EnabledIfSystemProperty(named="legacybridge.admissionBundle", matches=".+")
class PreservedUpdateCoordinatorTest {
    @TempDir static Path variants;
    static Path updatedOriginal, updatedCandidate;
    static String nextSource, nextTarget;
    @TempDir Path game;
    Path original, staged, replacement;
    PreservedInstallSelection admission;
    PreservedUpdateSelection selection;
    PreservedInstallResult predecessor;
    AtomicReference<InstallSession> session = new AtomicReference<>(SESSION);

    @BeforeAll static void convertAuthoredNextVersion() throws Exception {
        updatedOriginal = variants.resolve("original-1.0.1.jar"); updatedCandidate = variants.resolve("converted-1.0.1.jar");
        try (var zip = new ZipFile(System.getProperty("legacybridge.admissionOriginal"));
             var out = new ZipOutputStream(Files.newOutputStream(updatedOriginal, StandardOpenOption.CREATE_NEW))) {
            for (var entry : Collections.list(zip.entries())) {
                byte[] bytes; try (var in = zip.getInputStream(entry)) { bytes = in.readAllBytes(); }
                if (Set.of("META-INF/mods.toml", "META-INF/MANIFEST.MF").contains(entry.getName()))
                    bytes = new String(bytes, StandardCharsets.UTF_8).replace("1.0.0", "1.0.1").getBytes(StandardCharsets.UTF_8);
                var copy = new ZipEntry(entry.getName()); copy.setTime(0); out.putNextEntry(copy); out.write(bytes); out.closeEntry();
            }
        }
        nextSource = hash(updatedOriginal);
        try (var engine = new LegacyEngineService()) {
            var result = engine.convert(new ConversionRequest(updatedOriginal, updatedCandidate, data(), Optional.of(nextSource),
                    ConversionProfile.LOGIC_PRESERVING_V1)).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, result.status(), result.toString());
        }
        nextTarget = hash(updatedCandidate);
        assertEquals("71abbc4eb9b6b5cf6e942f7878eee006515ce524777464bedcf439e58417c547", nextSource);
        assertEquals("d6b7051dd301a58b2aeda595124e797e1f30b6a1f898225698d35cba83fea644", nextTarget);
        System.out.println("UPDATE_COORDINATOR_FIXTURE original=" + nextSource + " candidate=" + nextTarget);
    }
    static MappingData data() { return MappingData.reviewedBundle(Path.of(System.getProperty("legacybridge.admissionBundle")), DATA); }
    @BeforeEach void prepare() throws Exception {
        for (String area : List.of("input", "staging", "reports", "cache", "transactions")) Files.createDirectories(game.resolve("legacybridge/" + area));
        ReceiptInstallerFixtures.platform(game);
        original = Files.copy(Path.of(System.getProperty("legacybridge.admissionOriginal")), game.resolve("legacybridge/input/original.jar"));
        staged = Files.copy(Path.of(System.getProperty("legacybridge.admissionCandidate")), game.resolve("legacybridge/staging/old.jar"));
        var old = admit(original, SOURCE, staged, TARGET);
        predecessor = new PreservedAddOnlyInstaller().apply(game, new PreservedInstallApproval(old.admission(), old.admissionSha256(), UUID.randomUUID()), data(), SESSION);
        assertEquals(PreservedInstallResult.State.COMMITTED, predecessor.state(), predecessor.toString());
        Path source = Files.copy(updatedOriginal, game.resolve("legacybridge/input/new.jar"));
        replacement = Files.copy(updatedCandidate, game.resolve("legacybridge/staging/new.jar"));
        admission = admit(source, nextSource, replacement, nextTarget);
        selection = new PreservedUpdateSelection(admission, predecessor.transaction().orElseThrow(), predecessor.planSha256().orElseThrow());
    }
    PreservedInstallSelection admit(Path source, String sourceHash, Path target, String targetHash) {
        var r = new PreservedAdmissionStore().prepare(game, new PreservedArtifactRequest(source, sourceHash, target, targetHash, data()));
        assertEquals(PreservedAdmissionResult.State.RECORDED_NOT_INSTALLABLE, r.state(), r.toString());
        return new PreservedInstallSelection(r.transaction().orElseThrow(), r.planSha256().orElseThrow(), data(), Optional.empty());
    }
    ConversionController controller() { return ConversionController.forPreservedDevelopment(game, session::get); }
    UUID review(ConversionController c) throws Exception {
        assertTrue(c.reviewPreservedUpdate(selection).isPresent()); idle(c);
        assertEquals(PreservedInstallView.State.REVIEWED, c.preservedInstallation().state(), c.preservedInstallation().toString());
        assertFalse(c.preservedInstallation().safeToClose()); return c.preservedInstallation().reviewId().orElseThrow();
    }
    void recovery(PreservedInstallResult installed, UUID approval, RecoveryAction action) {
        selection = new PreservedUpdateSelection(new PreservedInstallSelection(admission.admission(), admission.admissionSha256(), data(), Optional.of(
                new PreservedInstallSelection.Recovery(installed.transaction().orElseThrow(), installed.planSha256().orElseThrow(), approval, action))),
                selection.previousInstallation(), selection.previousPlanSha256());
    }
    PreservedDeveloperForm nativeForm() {
        var f = new PreservedDeveloperForm(); f.kind(PreservedDeveloperForm.Kind.UPDATE);
        var replacement = selection.replacement();
        f.set(PreservedDeveloperForm.Field.ADMISSION, replacement.admission().toString());
        f.set(PreservedDeveloperForm.Field.ADMISSION_HASH, replacement.admissionSha256());
        f.set(PreservedDeveloperForm.Field.DATA, replacement.data().path().orElseThrow().toString());
        f.set(PreservedDeveloperForm.Field.DATA_HASH, replacement.data().expectedManifestSha256().orElseThrow());
        f.set(PreservedDeveloperForm.Field.PREVIOUS_INSTALLATION, selection.previousInstallation().toString());
        f.set(PreservedDeveloperForm.Field.PREVIOUS_PLAN_HASH, selection.previousPlanSha256());
        replacement.recovery().ifPresent(r -> {
            f.mode(r.action() == RecoveryAction.RESUME ? PreservedDeveloperForm.Mode.RESUME : PreservedDeveloperForm.Mode.RESTORE);
            f.set(PreservedDeveloperForm.Field.TRANSACTION, r.transaction().toString());
            f.set(PreservedDeveloperForm.Field.PLAN_HASH, r.planSha256());
            f.set(PreservedDeveloperForm.Field.APPROVAL, r.priorApprovalId().toString());
        });
        return f;
    }
    @Test void nativeFormHandsOffRealReviewConsentUpdateAndRestoreWithoutHistoryAuthority() throws Exception {
        try (var c = controller()) {
            var f = nativeForm(); var before = files(game.resolve("mods"));
            UUID operation = c.reviewPreservedUpdate(f.updateSelection()).orElseThrow(); f.reviewStarted(operation); idle(c);
            var v = c.preservedUpdate(); assertTrue(f.canConfirm(v, false));
            assertNotEquals(operation, v.observation().reviewId().orElseThrow());
            assertEquals(TARGET, v.preview().orElseThrow().identity().orElseThrow().previousArtifactSha256());
            assertEquals(nextTarget, v.preview().orElseThrow().identity().orElseThrow().replacementArtifactSha256());
            assertEquals(before, files(game.resolve("mods")), "Review/continue is read-only");
            UUID consent = f.consume(v, false).orElseThrow(); assertTrue(c.confirmPreservedUpdate(consent).isPresent()); idle(c);
            assertTrue(f.consume(c.preservedUpdate(), false).isEmpty());
            var installed = c.preservedUpdate().observation().result().orElseThrow();
            assertEquals(PreservedInstallResult.State.COMMITTED, installed.state()); assertEquals(nextTarget, hash(installed.installed().orElseThrow()));
            f.invalidate(); assertTrue(c.refreshPreservedUpdateHistory()); idle(c);
            assertFalse(f.canConfirm(c.preservedUpdate(), false));
            UUID prior = c.preservedUpdateHistory().receipts().stream().map(PreservedUpdateReceipts.Receipt::observation)
                    .filter(r -> r.action() == PreservedInstallReceipts.Action.APPLY).findFirst().orElseThrow().approvalId().orElseThrow();
            recovery(installed, prior, RecoveryAction.RESTORE); f = nativeForm();
            f.reviewStarted(c.reviewPreservedUpdate(f.updateSelection()).orElseThrow()); idle(c);
            assertTrue(c.preservedUpdate().preview().isEmpty()); assertTrue(f.canConfirm(c.preservedUpdate(), false));
            assertTrue(c.confirmPreservedUpdate(f.consume(c.preservedUpdate(), false).orElseThrow()).isPresent()); idle(c);
            assertEquals(PreservedInstallView.State.RESTORED, c.preservedUpdate().observation().state());
            assertEquals(TARGET, hash(predecessor.installed().orElseThrow())); assertFalse(Files.exists(installed.installed().orElseThrow()));
        }
        assertEquals(SOURCE, hash(original)); assertEquals(TARGET, hash(staged)); assertEquals(nextTarget, hash(replacement));
    }
    @Test void actualPreviewConsentUpdateFreshControllerResumeRestoreAndHistoryAreSeparated() throws Exception {
        PreservedInstallResult installed; UUID approval, reviewed;
        try (var c = controller()) {
            var before = files(game.resolve("mods")); reviewed = review(c);
            assertEquals(before, files(game.resolve("mods")));
            var preview = c.preservedUpdate().preview().orElseThrow(); assertTrue(preview.reviewed());
            assertEquals("1.0.0", preview.identity().orElseThrow().previousVersion()); assertEquals("1.0.1", preview.identity().orElseThrow().replacementVersion());
            assertTrue(c.confirmPreservedInstall(reviewed).isEmpty()); assertFalse(c.applyJob(reviewed, SESSION.loadedModIds()));
            assertTrue(c.jobs().isEmpty()); assertTrue(c.confirmPreservedUpdate(reviewed).isPresent()); idle(c);
            var v = c.preservedUpdate().observation(); assertEquals(PreservedInstallView.State.PENDING_RESTART, v.state(), v.toString());
            assertTrue(v.safeToClose()); installed = v.result().orElseThrow(); assertEquals(nextTarget, hash(installed.installed().orElseThrow()));
            assertFalse(Files.exists(predecessor.installed().orElseThrow())); assertTrue(c.confirmPreservedUpdate(reviewed).isEmpty());
            assertTrue(c.refreshPreservedUpdateHistory()); idle(c);
            var history = c.preservedUpdateHistory(); assertEquals(PreservedInstallReceipts.Status.COMPLETE, history.status(), history.toString());
            assertEquals(2, history.receipts().size());
            assertTrue(history.receipts().stream().noneMatch(r -> r.installationAllowed() || r.safeToClose()));
            approval = history.receipts().stream().map(PreservedUpdateReceipts.Receipt::observation)
                    .filter(r -> r.action() == PreservedInstallReceipts.Action.APPLY).findFirst().orElseThrow().approvalId().orElseThrow();
            assertTrue(c.refreshPreservedHistory()); idle(c); assertTrue(c.preservedHistory().receipts().isEmpty());
        }
        try (var c = controller()) {
            assertTrue(c.refreshPreservedUpdateHistory()); idle(c); assertTrue(c.confirmPreservedUpdate(reviewed).isEmpty());
            recovery(installed, approval, RecoveryAction.RESUME); assertTrue(c.confirmPreservedUpdate(review(c)).isPresent()); idle(c);
            assertEquals(PreservedInstallView.State.PENDING_RESTART, c.preservedInstallation().state(), c.preservedInstallation().toString());
            recovery(installed, approval, RecoveryAction.RESTORE); assertTrue(c.confirmPreservedUpdate(review(c)).isPresent()); idle(c);
            assertEquals(PreservedInstallView.State.RESTORED, c.preservedInstallation().state(), c.preservedInstallation().toString());
            assertEquals(TARGET, hash(predecessor.installed().orElseThrow())); assertFalse(Files.exists(installed.installed().orElseThrow()));
            assertTrue(c.confirmPreservedUpdate(review(c)).isPresent()); idle(c); assertTrue(c.preservedInstallation().safeToClose());
            recovery(installed, approval, RecoveryAction.RESUME); assertTrue(c.reviewPreservedUpdate(selection).isPresent()); idle(c);
            assertEquals("RESTORE_ALREADY_STARTED", c.preservedInstallation().error().orElseThrow().code());
        }
        assertEquals(SOURCE, hash(original)); assertEquals(TARGET, hash(staged)); assertEquals(nextTarget, hash(replacement));
    }
    @ParameterizedTest @ValueSource(strings={"previousPin", "admissionPin", "dataPin", "missingPrevious", "loaded", "alias"})
    void unsafeSelectionNeverIssuesApproval(String mutation) throws Exception {
        var s = admission;
        if (mutation.equals("admissionPin")) s = new PreservedInstallSelection(s.admission(), "a".repeat(64), data(), Optional.empty());
        if (mutation.equals("dataPin")) s = new PreservedInstallSelection(s.admission(), s.admissionSha256(), MappingData.reviewedBundle(data().path().orElseThrow(), "a".repeat(64)), Optional.empty());
        selection = new PreservedUpdateSelection(s, mutation.equals("missingPrevious") ? game.resolve("legacybridge/transactions/" + UUID.randomUUID()) : selection.previousInstallation(),
                mutation.equals("previousPin") ? "a".repeat(64) : selection.previousPlanSha256());
        if (mutation.equals("loaded")) session.set(new InstallSession(Set.of("minecraft", "forge", "legacybridge_runtime", "legacybridge_logic"), Set.of(), false));
        if (mutation.equals("alias")) { Path alias = Files.createLink(game.resolve("loaded-alias.jar"), predecessor.installed().orElseThrow()); session.set(new InstallSession(SESSION.loadedModIds(), Set.of(alias), false)); }
        try (var c = controller()) {
            assertTrue(c.reviewPreservedUpdate(selection).isPresent()); idle(c);
            assertEquals(PreservedInstallView.State.BLOCKED, c.preservedInstallation().state(), c.preservedInstallation().toString());
            assertTrue(c.confirmPreservedUpdate(c.preservedInstallation().reviewId().orElseThrow()).isEmpty());
            assertEquals(TARGET, hash(predecessor.installed().orElseThrow()));
        }
    }
    @ParameterizedTest @ValueSource(strings={"original", "candidate", "previousPlan"})
    void changedReviewedBytesAreRecheckedAndConfirmationIsConsumed(String changed) throws Exception {
        try (var c = controller()) {
            UUID review = review(c);
            Path file = switch (changed) { case "original" -> original; case "candidate" -> replacement;
                default -> selection.previousInstallation().resolve("preserved-install-v1.json"); };
            Files.write(file, new byte[]{1,2,3});
            assertTrue(c.confirmPreservedUpdate(review).isPresent()); idle(c);
            assertEquals(PreservedInstallView.State.FAILED, c.preservedInstallation().state());
            assertFalse(c.preservedInstallation().safeToClose()); assertTrue(c.confirmPreservedUpdate(review).isEmpty());
            assertEquals(TARGET, hash(predecessor.installed().orElseThrow()));
        }
    }
    @Test void publicRouteCrossKindAndSupersededReviewsCannotAuthorizeUpdate() throws Exception {
        try (var c = new ConversionController(game)) {
            assertTrue(c.reviewPreservedUpdate(selection).isEmpty()); assertTrue(c.confirmPreservedUpdate(UUID.randomUUID()).isEmpty()); assertFalse(c.refreshPreservedUpdateHistory());
        }
        try (var c = controller()) {
            UUID old = review(c); UUID current = review(c); assertTrue(c.confirmPreservedUpdate(old).isEmpty());
            assertTrue(c.reviewPreservedInstall(admission).isPresent()); idle(c);
            assertTrue(c.confirmPreservedUpdate(current).isEmpty());
            assertTrue(c.confirmPreservedUpdate(c.preservedInstallation().reviewId().orElseThrow()).isEmpty());
            assertTrue(c.preservedUpdate().selection().isEmpty());
        }
    }
    @ParameterizedTest @ValueSource(strings={"world", "loadedSet", "unavailable"})
    void currentSessionChangeInvalidatesConsent(String change) throws Exception {
        try (var c = controller()) {
            UUID id = review(c);
            session.set(change.equals("unavailable") ? null : new InstallSession(change.equals("loadedSet") ? Set.of("minecraft", "forge", "legacybridge_runtime", "other") : SESSION.loadedModIds(), Set.of(), change.equals("world")));
            assertTrue(c.confirmPreservedUpdate(id).isEmpty());
            assertEquals(change.equals("unavailable") ? "SESSION_UNAVAILABLE" : "SESSION_CHANGED", c.preservedInstallation().error().orElseThrow().code());
            session.set(SESSION); assertTrue(c.confirmPreservedUpdate(id).isEmpty()); assertEquals(TARGET, hash(predecessor.installed().orElseThrow()));
        }
    }
    @ParameterizedTest @ValueSource(strings={"REVIEW_APPLY", "APPLY"})
    void receiptFailureRetainsRealResultButGrantsNeitherConsentNorSafeClose(String stage) throws Exception {
        var store = new PreservedUpdateReceipts();
        try (var c = new ConversionController(game, session::get, new ConversionController.RealUpdateOperations(), (root,s,a,n,v) -> {
            if (a.name().equals(stage)) throw new IOException("Injected receipt disk/permission failure"); return store.write(root,s,a,n,v);
        })) {
            if (stage.equals("APPLY")) assertTrue(c.confirmPreservedUpdate(review(c)).isPresent()); else assertTrue(c.reviewPreservedUpdate(selection).isPresent());
            idle(c); var v = c.preservedInstallation(); assertEquals("PRESERVED_RECEIPT_WRITE_FAILED", v.error().orElseThrow().code());
            assertFalse(v.safeToClose()); assertTrue(c.confirmPreservedUpdate(v.reviewId().orElseThrow()).isEmpty());
            if (stage.equals("APPLY")) { assertEquals(PreservedInstallResult.State.COMMITTED, v.result().orElseThrow().state()); assertTrue(Files.exists(v.result().orElseThrow().installed().orElseThrow())); }
            else assertEquals(TARGET, hash(predecessor.installed().orElseThrow()));
            assertTrue(c.refreshPreservedUpdateHistory()); idle(c); assertFalse(c.preservedInstallation().safeToClose());
        }
    }
    @ParameterizedTest @ValueSource(strings={"REVIEW_APPLY", "APPLY"})
    void acceptedUpdateRetainsSharedGateAndShutdownLeaseThroughReceiptAfterClose(String stage) throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var store = new PreservedUpdateReceipts();
        var c = new ConversionController(game, session::get, new ConversionController.RealUpdateOperations(), (root,s,a,n,v) -> {
            assertEquals("legacybridge-controller", Thread.currentThread().getName());
            if (a.name().equals(stage)) {
                entered.countDown(); try { if (!release.await(30, TimeUnit.SECONDS)) throw new IOException("Release timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            }
            return store.write(root,s,a,n,v);
        });
        try {
            if (stage.equals("APPLY")) assertTrue(c.confirmPreservedUpdate(review(c)).isPresent()); else assertTrue(c.reviewPreservedUpdate(selection).isPresent());
            assertTrue(entered.await(30, TimeUnit.SECONDS));
            assertTrue(c.isInstalling()); assertFalse(c.cancel()); assertFalse(c.refreshPreservedHistory()); assertFalse(c.refreshPreservedUpdateHistory());
            assertTrue(c.reviewPreservedInstall(admission).isEmpty()); assertTrue(c.reviewPreservedUpdate(selection).isEmpty());
            assertFalse(c.refreshTransactions(SESSION)); assertFalse(c.enqueue(List.of(0), ""));
            assertTimeout(Duration.ofSeconds(1), c::close); assertTrue(c.isInstalling()); assertFalse(c.preservedInstallation().safeToClose());
        } finally { release.countDown(); idle(c); c.close(); }
        assertFalse(c.isInstalling());
        assertEquals(stage.equals("APPLY"), c.preservedInstallation().safeToClose(), c.preservedInstallation().toString());
        assertTrue(c.confirmPreservedUpdate(c.preservedInstallation().reviewId().orElseThrow()).isEmpty());
    }
    @Test void dispatchRechecksHostAndRefusesWithoutPredecessorMutation() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        try (var c = ConversionController.forPreservedDevelopment(game, () -> calls.incrementAndGet() == 1 ? SESSION : new InstallSession(SESSION.loadedModIds(), Set.of(), true))) {
            assertTrue(c.reviewPreservedUpdate(selection).isPresent()); idle(c);
            assertEquals("SESSION_CHANGED", c.preservedInstallation().error().orElseThrow().code());
            assertTrue(c.confirmPreservedUpdate(c.preservedInstallation().reviewId().orElseThrow()).isEmpty());
            assertEquals(TARGET, hash(predecessor.installed().orElseThrow()));
        }
    }
    @Test void incompletePreflightCannotBePromotedToReview() throws Exception {
        var impossible = new ConversionController.UpdateOperations() {
            public PreservedUpdatePreview preview(Path root, PreservedUpdateSelection s, InstallSession current) {
                return new PreservedUpdatePreview(Optional.empty(), Optional.empty());
            }
            public PreservedInstallResult inspect(Path root, PreservedUpdateSelection s, InstallSession current) { throw new AssertionError("No recovery selected"); }
            public PreservedInstallResult install(Path root, PreservedUpdateSelection s, UUID nonce, InstallSession current) { throw new AssertionError("No consent"); }
        };
        try (var c = new ConversionController(game, session::get, impossible, new PreservedUpdateReceipts()::write)) {
            assertTrue(c.reviewPreservedUpdate(selection).isPresent()); idle(c);
            assertEquals("UPDATE_REVIEW_INCOMPLETE", c.preservedInstallation().error().orElseThrow().code());
            assertTrue(c.confirmPreservedUpdate(c.preservedInstallation().reviewId().orElseThrow()).isEmpty());
        }
    }
    @Test void actualPartialPublicationIsRecoverableOnlyAfterFreshReviewAndExplicitConsent() throws Exception {
        var real = new ConversionController.RealUpdateOperations();
        var interrupted = new ConversionController.UpdateOperations() {
            public PreservedUpdatePreview preview(Path root, PreservedUpdateSelection s, InstallSession current) { return real.preview(root, s, current); }
            public PreservedInstallResult inspect(Path root, PreservedUpdateSelection s, InstallSession current) { return real.inspect(root, s, current); }
            public PreservedInstallResult install(Path root, PreservedUpdateSelection s, UUID nonce, InstallSession current) {
                var incoming = s.replacement();
                return ReceiptInstallerFixtures.partialPreservedUpdate().apply(root, new PreservedUpdateApproval(
                        new PreservedInstallApproval(incoming.admission(), incoming.admissionSha256(), nonce), s.previousInstallation(), s.previousPlanSha256()), incoming.data(), current);
            }
        };
        PreservedInstallResult partial; UUID approval;
        try (var c = new ConversionController(game, session::get, interrupted, new PreservedUpdateReceipts()::write)) {
            assertTrue(c.confirmPreservedUpdate(review(c)).isPresent()); idle(c);
            var v = c.preservedInstallation(); assertEquals(PreservedInstallView.State.FAILED, v.state(), v.toString());
            partial = v.result().orElseThrow(); assertEquals(PreservedInstallResult.State.RECOVERY_REQUIRED, partial.state());
            assertFalse(v.safeToClose()); assertTrue(c.refreshPreservedUpdateHistory()); idle(c);
            approval = c.preservedUpdateHistory().receipts().stream().map(PreservedUpdateReceipts.Receipt::observation)
                    .filter(r -> r.action() == PreservedInstallReceipts.Action.APPLY).findFirst().orElseThrow().approvalId().orElseThrow();
        }
        try (var c = controller()) {
            recovery(partial, approval, RecoveryAction.RESUME);
            assertTrue(c.confirmPreservedUpdate(UUID.randomUUID()).isEmpty());
            assertTrue(c.confirmPreservedUpdate(review(c)).isPresent()); idle(c);
            assertEquals(PreservedInstallView.State.PENDING_RESTART, c.preservedInstallation().state(), c.preservedInstallation().toString());
            recovery(partial, approval, RecoveryAction.RESTORE); assertTrue(c.confirmPreservedUpdate(review(c)).isPresent()); idle(c);
            assertEquals(TARGET, hash(predecessor.installed().orElseThrow())); assertTrue(c.preservedInstallation().safeToClose());
        }
    }
    static Map<String,String> files(Path directory) throws Exception {
        Map<String,String> result = new TreeMap<>(); try (var paths = Files.list(directory)) {
            for (Path p : paths.toList()) result.put(p.getFileName().toString(), hash(p));
        } return result;
    }
}
