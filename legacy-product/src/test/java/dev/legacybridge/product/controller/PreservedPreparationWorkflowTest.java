package dev.legacybridge.product.controller;

import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.product.install.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real authored fixtures, actual engine/store/installer; no original or converted class execution. */
@EnabledIfSystemProperty(named="legacybridge.admissionBundle", matches=".+")
class PreservedPreparationWorkflowTest {
    static final String SOURCE="49dfbd7c65918c16ce8c057df992f7e9b7c188a3fe5634adb42bb1bc5dbfc19f";
    static final String TARGET="78de0e2fd62ed83315518a1980ae0738b354531736a5eea73195d332aecc186d";
    static final String DATA="0edd3ba641dbf858b7464096794a3caf4efca9d04ef4484a740a9599e9aec2bb";
    @TempDir Path game;
    Path original,candidate;
    MappingData data;
    AtomicReference<InstallSession> session=new AtomicReference<>(new InstallSession(Set.of("minecraft","forge","legacybridge_runtime"),Set.of(),false));
    @BeforeEach void setup() throws Exception {
        Files.createDirectories(game.resolve("legacybridge/input")); Files.createDirectories(game.resolve("legacybridge/staging"));
        original=Files.copy(Path.of(System.getProperty("legacybridge.admissionOriginal")),game.resolve("legacybridge/input/original.jar"));
        candidate=Files.copy(Path.of(System.getProperty("legacybridge.admissionCandidate")),game.resolve("legacybridge/staging/candidate.jar"));
        data=MappingData.reviewedBundle(Path.of(System.getProperty("legacybridge.admissionBundle")),DATA);
    }
    PreservedPreparationRequest request(boolean convert) {return new PreservedPreparationRequest(original,SOURCE,
            convert?Optional.empty():Optional.of(new PreservedPreparationRequest.Candidate(candidate,TARGET)),data);}
    ConversionController controller() {return ConversionController.forPreservedDevelopment(game,session::get);}
    static void idle(ConversionController c) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(90).toNanos();
        while(c.snapshot().busy()&&System.nanoTime()<deadline) Thread.sleep(10);
        assertFalse(c.snapshot().busy(),c.snapshot().toString());
    }
    static String sha(Path path) throws Exception {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    void noInstallation() {assertFalse(Files.exists(game.resolve("mods")));}
    @Test void ordinaryControllerCannotPrepareOrImportAuthority() {
        try(var c=new ConversionController(game)) {
            assertEquals(PreservedPreparationView.State.DISABLED,c.preparation().state());
            assertTrue(c.preparePreserved(request(false)).isEmpty()); assertTrue(c.preparedSelection(UUID.randomUUID()).isEmpty());
            assertFalse(Files.exists(game.resolve("legacybridge/reports")));
        }
    }
    @Test void existingCandidateGetsFreshAdmissionReportsButNoConsentOrInstallation() throws Exception {
        try(var c=controller()) {
            UUID id=c.preparePreserved(request(false)).orElseThrow(); idle(c); var v=c.preparation();
            assertEquals(PreservedPreparationView.State.READY,v.state(),v.toString());
            assertEquals(Set.of("analysis","inspection","preparation"),v.reports().keySet());
            assertEquals(TARGET,v.candidate().orElseThrow().sha256()); assertFalse(v.installationAllowed()); assertFalse(v.safeToClose());
            assertEquals("NOT_OBSERVED",v.activation()); assertTrue(c.preparedSelection(id).isPresent());
            assertTrue(c.confirmPreservedInstall(id).isEmpty()); assertTrue(c.jobs().isEmpty());
            assertTrue(c.preparedSelection(UUID.randomUUID()).isEmpty()); assertEquals(SOURCE,sha(original)); assertEquals(TARGET,sha(candidate)); noInstallation();
            for(Path report:v.reports().values()) assertTrue(Files.size(report)>0);
        }
        try(var c=controller()) {assertEquals(PreservedPreparationView.State.IDLE,c.preparation().state()); assertTrue(c.preparedSelection(UUID.randomUUID()).isEmpty());}
    }
    @Test void originalConversionMatchesKnownCandidateThenRequiresSeparateReviewAndInstall() throws Exception {
        ReceiptInstallerFixtures.platform(game);
        try(var c=controller()) {
            UUID operation=c.preparePreserved(request(true)).orElseThrow(); idle(c); var v=c.preparation();
            assertEquals(PreservedPreparationView.State.READY,v.state(),v.toString());
            assertEquals(Set.of("analysis","conversion","transformation-log","inspection","preparation"),v.reports().keySet());
            Path converted=v.candidate().orElseThrow().path(); assertNotEquals(candidate,converted); assertEquals(TARGET,sha(converted));
            assertTrue(c.installation().result().isEmpty()); assertTrue(c.confirmPreservedInstall(operation).isEmpty());
            assertTrue(c.reviewPreservedInstall(c.preparedSelection(operation).orElseThrow()).isPresent()); idle(c);
            assertEquals(PreservedInstallView.State.REVIEWED,c.preservedInstallation().state());
            assertTrue(c.confirmPreservedInstall(c.preservedInstallation().reviewId().orElseThrow()).isPresent()); idle(c);
            assertEquals(PreservedInstallView.State.PENDING_RESTART,c.preservedInstallation().state()); assertEquals(SOURCE,sha(original));
        }
    }
    @Test void changedOriginalHashFailsAndExplicitRetryRemainsUsable() throws Exception {
        try(var c=controller()) {
            UUID old=c.preparePreserved(new PreservedPreparationRequest(original,"0".repeat(64),request(false).candidate(),data)).orElseThrow(); idle(c);
            assertEquals("INPUT_CHANGED",c.preparation().error().orElseThrow().code()); assertTrue(c.preparedSelection(old).isEmpty());
            UUID next=c.preparePreserved(request(false)).orElseThrow(); idle(c); assertNotEquals(old,next);
            assertEquals(PreservedPreparationView.State.READY,c.preparation().state()); assertTrue(c.preparedSelection(old).isEmpty()); noInstallation();
        }
    }
    @Test void changedCandidateHashCannotBecomeReady() throws Exception {
        try(var c=controller()) {
            UUID id=c.preparePreserved(new PreservedPreparationRequest(original,SOURCE,Optional.of(new PreservedPreparationRequest.Candidate(candidate,"0".repeat(64))),data)).orElseThrow(); idle(c);
            assertNotEquals(PreservedPreparationView.State.READY,c.preparation().state()); assertTrue(c.preparation().error().isPresent());
            assertTrue(c.preparedSelection(id).isEmpty()); assertTrue(c.preparation().admission().isEmpty()); noInstallation();
        }
    }
    @Test void corruptedCandidateWithCurrentHashIsRefusedSemantically() throws Exception {
        Files.writeString(candidate,"not a jar");
        try(var c=controller()) {
            UUID id=c.preparePreserved(new PreservedPreparationRequest(original,SOURCE,Optional.of(new PreservedPreparationRequest.Candidate(candidate,sha(candidate))),data)).orElseThrow(); idle(c);
            assertTrue(c.preparedSelection(id).isEmpty()); assertTrue(c.preparation().error().isPresent()); noInstallation();
        }
    }
    @Test void fullDataPinNeverFallsBackToIncludedData() throws Exception {
        try(var c=controller()) {
            c.preparePreserved(new PreservedPreparationRequest(original,SOURCE,Optional.empty(),MappingData.reviewedBundle(data.path().orElseThrow(),"0".repeat(64)))).orElseThrow(); idle(c);
            assertNotEquals(PreservedPreparationView.State.READY,c.preparation().state()); assertTrue(c.preparation().candidate().isEmpty()); noInstallation();
        }
    }
    @Test void inputOutsideReservedAreaIsRejectedBeforeAnalysis() throws Exception {
        Path outside=Files.copy(original,game.resolve("outside.jar"));
        try(var c=controller()) {
            c.preparePreserved(new PreservedPreparationRequest(outside,SOURCE,Optional.empty(),data)).orElseThrow(); idle(c);
            assertEquals("UNSAFE_PREPARATION_INPUT",c.preparation().error().orElseThrow().code()); assertTrue(c.preparation().reports().isEmpty()); noInstallation();
        }
    }
    @Test void loadedOriginalAndWorldOpenAreRefused() throws Exception {
        session.set(new InstallSession(Set.of("forge"),Set.of(original),false));
        try(var c=controller()) {
            c.preparePreserved(request(false)).orElseThrow(); idle(c);
            assertEquals("LOADED_PREPARATION_INPUT",c.preparation().error().orElseThrow().code());
            session.set(new InstallSession(Set.of(),Set.of(),true)); assertTrue(c.preparePreserved(request(false)).isEmpty());
            assertEquals("WORLD_OPEN",c.globalError().orElseThrow().code()); noInstallation();
        }
    }
    @Test void hostChangeBeforeAdmissionBlocksWithoutJournal() throws Exception {
        try(var c=new ConversionController(game,session::get,stage->{if(stage==PreservedPreparationView.State.ADMITTING) session.set(new InstallSession(Set.of(),Set.of(),true));})) {
            c.preparePreserved(request(false)).orElseThrow(); idle(c);
            assertEquals("SESSION_CHANGED",c.preparation().error().orElseThrow().code()); assertTrue(c.preparation().admission().isEmpty()); noInstallation();
        }
    }
    @Test void cancelBeforeAdmissionRetainsOnlyCompleteCandidateWithoutReadySelection() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var c=new ConversionController(game,session::get,stage->{if(stage==PreservedPreparationView.State.ADMITTING){entered.countDown();assertTrue(release.await(30,TimeUnit.SECONDS));}})) {
            UUID id=c.preparePreserved(request(true)).orElseThrow(); assertTrue(entered.await(60,TimeUnit.SECONDS));
            try {assertTrue(c.cancel()); assertTrue(c.preparePreserved(request(false)).isEmpty());} finally {release.countDown();}
            idle(c); assertEquals(PreservedPreparationView.State.CANCELLED,c.preparation().state());
            assertEquals(TARGET,sha(c.preparation().candidate().orElseThrow().path())); assertTrue(c.preparation().admission().isEmpty());
            assertTrue(c.preparedSelection(id).isEmpty()); noInstallation();
        } finally {release.countDown();}
    }
    @Test void closeBeforeAnalysisDoesNotWaitAndProducesNoCandidate() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        var c=new ConversionController(game,session::get,stage->{if(stage==PreservedPreparationView.State.ANALYZING){entered.countDown();assertTrue(release.await(30,TimeUnit.SECONDS));}});
        try {
            c.preparePreserved(request(true)).orElseThrow(); assertTrue(entered.await(10,TimeUnit.SECONDS));
            assertTimeout(Duration.ofSeconds(1),c::close); release.countDown(); idle(c);
            assertEquals(PreservedPreparationView.State.CANCELLED,c.preparation().state()); assertTrue(c.preparation().candidate().isEmpty()); noInstallation();
        } finally {release.countDown();c.close();}
    }
    @Test void unknownSummaryFileIsNotOverwrittenAndReadinessIsRevokedAfterAdmission() throws Exception {
        AtomicReference<ConversionController> ref=new AtomicReference<>(); AtomicReference<Path> obstruction=new AtomicReference<>();
        try(var c=new ConversionController(game,session::get,stage->{if(stage==PreservedPreparationView.State.WRITING_REPORTS) {
            Path file=ref.get().preparation().reports().get("analysis").getParent().resolve("preparation.json");
            Files.writeString(file,"user-owned",StandardOpenOption.CREATE_NEW); obstruction.set(file);
        }})) {
            ref.set(c); UUID id=c.preparePreserved(request(false)).orElseThrow(); idle(c);
            assertEquals(PreservedPreparationView.State.FAILED,c.preparation().state()); assertEquals("PREPARATION_REPORT_FAILED",c.preparation().error().orElseThrow().code());
            assertTrue(c.preparation().admission().isPresent()); assertTrue(c.preparedSelection(id).isEmpty());
            assertEquals("user-owned",Files.readString(obstruction.get())); noInstallation();
        }
    }
}
