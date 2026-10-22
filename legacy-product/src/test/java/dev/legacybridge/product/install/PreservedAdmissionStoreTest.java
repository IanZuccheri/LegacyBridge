package dev.legacybridge.product.install;

import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.converter.service.PreservedArtifactRequest;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static dev.legacybridge.product.install.PreservedAdmissionResult.State.*;

/** Real authored binary pair only; static readback, never definition/execution of its classes. */
@EnabledIfSystemProperty(named="legacybridge.admissionBundle", matches=".+")
class PreservedAdmissionStoreTest {
    private static final String SOURCE="49dfbd7c65918c16ce8c057df992f7e9b7c188a3fe5634adb42bb1bc5dbfc19f";
    private static final String TARGET="78de0e2fd62ed83315518a1980ae0738b354531736a5eea73195d332aecc186d";
    private static final String DATA="0edd3ba641dbf858b7464096794a3caf4efca9d04ef4484a740a9599e9aec2bb";
    @TempDir Path game;
    Path original, staged;
    MappingData data;
    @BeforeEach void setup() throws Exception {
        for(String area:List.of("input","staging","reports","cache","transactions")) Files.createDirectories(game.resolve("legacybridge/"+area));
        Files.createDirectory(game.resolve("mods")); Files.writeString(game.resolve("mods/untouched.txt"),"user-owned");
        original=Files.copy(Path.of(System.getProperty("legacybridge.admissionOriginal")),game.resolve("legacybridge/input/original.jar"));
        staged=Files.copy(Path.of(System.getProperty("legacybridge.admissionCandidate")),game.resolve("legacybridge/staging/candidate.jar"));
        assertEquals(SOURCE,AddOnlyInstaller.hash(original)); assertEquals(TARGET,AddOnlyInstaller.hash(staged));
        data=MappingData.reviewedBundle(Path.of(System.getProperty("legacybridge.admissionBundle")),DATA);
    }
    PreservedArtifactRequest request() { return new PreservedArtifactRequest(original,SOURCE,staged,TARGET,data); }
    @Test void controlledPreparationCancelledBeforeStartDoesNotCreateTransactionAndCannotBeReused() throws Exception {
        var job=new PreservedAdmissionStore().preparation(progress->{ }); assertTrue(job.cancel());
        var result=job.run(game,request()); assertEquals(RECOVERY_REQUIRED,result.state()); assertTrue(result.transaction().isEmpty());
        assertFalse(job.cancel()); assertThrows(IllegalStateException.class,()->job.run(game,request())); unchangedUserFiles();
    }
    @Test void controlledCancellationAfterBindingRetainsRecoverableJournalWithoutReadyAdmission() throws Exception {
        var ref=new java.util.concurrent.atomic.AtomicReference<PreservedAdmissionStore.Preparation>();
        var store=new PreservedAdmissionStore((boundary,path)->{if(boundary==PreservedAdmissionStore.Boundary.BOUND) assertTrue(ref.get().cancel());});
        var job=store.preparation(progress->{ }); ref.set(job); var result=job.run(game,request());
        assertEquals(RECOVERY_REQUIRED,result.state()); assertEquals("ADMISSION_INTERRUPTED",result.error().orElseThrow().code());
        var fresh=new PreservedAdmissionStore(); var inspected=fresh.inspect(game,tx(result),pin(result),data);
        assertEquals(INCOMPLETE,inspected.state(),inspected.toString());
        assertEquals(RECHECKED_NOT_INSTALLABLE,fresh.resume(game,tx(result),pin(result),data).state()); unchangedUserFiles();
    }
    @Test void cancellationLosesAfterCompletedAdmissionPublicationBarrier() throws Exception {
        var ref=new java.util.concurrent.atomic.AtomicReference<PreservedAdmissionStore.Preparation>();
        var observed=new java.util.concurrent.atomic.AtomicBoolean();
        var cancelledAtPublication=new java.util.concurrent.atomic.AtomicBoolean();
        var job=new PreservedAdmissionStore().preparation(progress->{
            if(progress.stage()==dev.legacybridge.converter.operation.OperationStage.PUBLICATION) {observed.set(true); cancelledAtPublication.set(ref.get().cancel());}
        }); ref.set(job);
        assertEquals(RECORDED_NOT_INSTALLABLE,job.run(game,request()).state()); assertTrue(observed.get());
        // Progress observers deliberately isolate callback failures; assert on the test thread instead.
        assertFalse(cancelledAtPublication.get()); assertFalse(job.cancel()); unchangedUserFiles();
    }
    PreservedAdmissionResult prepare() {
        var result=new PreservedAdmissionStore().prepare(game,request());
        assertEquals(RECORDED_NOT_INSTALLABLE,result.state(),result.toString()); return result;
    }
    Path tx(PreservedAdmissionResult r) { return r.transaction().orElseThrow(); }
    String pin(PreservedAdmissionResult r) { return r.planSha256().orElseThrow(); }
    void unchangedUserFiles() throws Exception {
        assertEquals(SOURCE,AddOnlyInstaller.hash(original)); assertEquals(TARGET,AddOnlyInstaller.hash(staged));
        assertEquals("user-owned",Files.readString(game.resolve("mods/untouched.txt")));
        assertEquals(Map.of("untouched.txt",AddOnlyInstaller.hash(game.resolve("mods/untouched.txt"))),snapshot(game.resolve("mods")));
    }
    static Map<String,String> snapshot(Path root) throws Exception {
        Map<String,String> result=new TreeMap<>();
        try(var paths=Files.walk(root)) { for(Path path:paths.filter(Files::isRegularFile).toList()) result.put(root.relativize(path).toString(),AddOnlyInstaller.hash(path)); }
        return result;
    }

    @Test void realPairPersistsExactBindingAndIndependentPayloadAndRereadsWithoutInstall() throws Exception {
        var result=prepare(); Path transaction=tx(result);
        assertFalse(result.installationAllowed()); assertFalse(result.safeToClose());
        assertEquals(6,result.inspection().orElseThrow().classCount()); assertEquals(25,result.inspection().orElseThrow().methodCount());
        var plan=PreservedAdmissionJournal.read(transaction);
        assertEquals(DATA,plan.mappingManifestSha256()); assertEquals(SOURCE,plan.originalSha256()); assertEquals(TARGET,plan.candidateSha256());
        assertEquals("legacybridge_logic",plan.modId()); assertEquals("1.0.0",plan.modVersion());
        assertFalse(Files.isSameFile(staged,transaction.resolve(PreservedAdmissionJournal.PAYLOAD)));
        assertFalse(Files.readString(transaction.resolve(PreservedAdmissionJournal.PLAN)).contains(data.path().orElseThrow().toString()));
        var before=snapshot(game); var checked=new PreservedAdmissionStore().inspect(game,transaction,pin(result),data);
        assertEquals(RECHECKED_NOT_INSTALLABLE,checked.state(),checked.toString()); assertEquals(before,snapshot(game));
        assertThrows(Exception.class,()->TransactionJournal.read(transaction));
        assertThrows(InstallException.class,()->new AddOnlyInstaller().inspect(game,transaction));
        unchangedUserFiles();
    }
    @Test void interruptedAfterBindingRechecksThenExplicitlyCompletesWithoutReplayOrOverwrite() throws Exception {
        var interrupted=new PreservedAdmissionStore((point,transaction)->{if(point==PreservedAdmissionStore.Boundary.BOUND) throw new IOException("simulated interrupted write");}).prepare(game,request());
        assertEquals(RECOVERY_REQUIRED,interrupted.state()); Path transaction=tx(interrupted);
        assertFalse(Files.exists(transaction.resolve(PreservedAdmissionJournal.CHECKED)));
        var before=snapshot(game); var store=new PreservedAdmissionStore();
        assertEquals(INCOMPLETE,store.inspect(game,transaction,pin(interrupted),data).state()); assertEquals(before,snapshot(game));
        assertEquals(RECHECKED_NOT_INSTALLABLE,store.resume(game,transaction,pin(interrupted),data).state());
        before=snapshot(game);
        assertEquals(RECHECKED_NOT_INSTALLABLE,store.resume(game,transaction,pin(interrupted),data).state()); assertEquals(before,snapshot(game));
        unchangedUserFiles();
    }
    @ParameterizedTest @ValueSource(strings={"PAYLOAD_COPY","JOURNAL_WRITE"})
    void failureBeforeCompleteBindingIsAnOrphanNotAnImplicitResume(String point) throws Exception {
        var failed=new PreservedAdmissionStore((boundary,transaction)->{if(boundary.name().equals(point)) throw new IOException("simulated disk/write refusal");}).prepare(game,request());
        assertEquals(RECOVERY_REQUIRED,failed.state()); assertTrue(failed.planSha256().isEmpty());
        Path transaction=tx(failed); assertFalse(Files.exists(transaction.resolve(PreservedAdmissionJournal.PLAN)));
        var before=snapshot(game);
        assertEquals(RECOVERY_REQUIRED,new PreservedAdmissionStore().resume(game,transaction,"0".repeat(64),data).state());
        assertEquals(before,snapshot(game)); unchangedUserFiles();
    }
    @ParameterizedTest @ValueSource(strings={"original","staged","payload","checkpoint","missing-payload","alias-payload"})
    void completeObservationNeverOverridesChangedOrMissingLiveInputs(String mutation) throws Exception {
        var result=prepare(); Path transaction=tx(result), payload=transaction.resolve(PreservedAdmissionJournal.PAYLOAD);
        switch(mutation) {
            case "original" -> Files.writeString(original,"changed original");
            case "staged" -> Files.writeString(staged,"changed staged");
            case "payload" -> Files.writeString(payload,"changed payload");
            case "checkpoint" -> Files.writeString(transaction.resolve(PreservedAdmissionJournal.CHECKED),"{\"installationAllowed\":true}");
            case "missing-payload" -> Files.delete(payload);
            case "alias-payload" -> { Files.delete(payload); Files.createLink(payload,staged); }
        }
        var before=snapshot(game);
        assertEquals(RECOVERY_REQUIRED,new PreservedAdmissionStore().resume(game,transaction,pin(result),data).state()); assertEquals(before,snapshot(game));
    }
    @ParameterizedTest @ValueSource(strings={"schemaVersion","recipe","runtimeVersion","installationAllowed","targetForge","extra","original","transactionId","mappingManifestSha256"})
    void evenAnExplicitNewHashDoesNotApproveAnUnknownJournalContract(String field) throws Exception {
        var result=prepare(); Path transaction=tx(result);
        Map<String,Object> row=new TreeMap<>(TransactionFiles.read(transaction.resolve(PreservedAdmissionJournal.PLAN)));
        row.put(field,switch(field){case "schemaVersion"->3;case "installationAllowed"->true;case "original"->"../outside.jar";case "mappingManifestSha256"->"0".repeat(64);default->"unreviewed";});
        Files.write(transaction.resolve(PreservedAdmissionJournal.PLAN),TransactionFiles.json(row));
        var before=snapshot(game); String changed=AddOnlyInstaller.hash(transaction.resolve(PreservedAdmissionJournal.PLAN));
        assertEquals(RECOVERY_REQUIRED,new PreservedAdmissionStore().resume(game,transaction,changed,data).state()); assertEquals(before,snapshot(game));
        unchangedUserFiles();
    }
    @Test void matchingNewHashesAndOldObservationCannotHideSemanticTampering() throws Exception {
        var result=prepare(); Path transaction=tx(result); Path mutated=game.resolve("mutated.jar");
        try(var input=new JarFile(staged.toFile()); var output=new JarOutputStream(Files.newOutputStream(mutated))) {
            for(var entry:Collections.list(input.entries())) {
                output.putNextEntry(new JarEntry(entry.getName()));
                if(entry.getName().endsWith("/lang/en_us.json")) output.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                else try(var stream=input.getInputStream(entry)) { stream.transferTo(output); }
                output.closeEntry();
            }
        }
        Files.copy(mutated,staged,StandardCopyOption.REPLACE_EXISTING); Files.copy(mutated,transaction.resolve(PreservedAdmissionJournal.PAYLOAD),StandardCopyOption.REPLACE_EXISTING);
        var row=new TreeMap<>(TransactionFiles.read(transaction.resolve(PreservedAdmissionJournal.PLAN))); row.put("candidateSha256",AddOnlyInstaller.hash(staged));
        Files.write(transaction.resolve(PreservedAdmissionJournal.PLAN),TransactionFiles.json(row));
        var failed=new PreservedAdmissionStore().inspect(game,transaction,AddOnlyInstaller.hash(transaction.resolve(PreservedAdmissionJournal.PLAN)),data);
        assertEquals(RECOVERY_REQUIRED,failed.state()); assertEquals("ADMISSION_RESOURCE",failed.error().orElseThrow().code());
        assertEquals(SOURCE,AddOnlyInstaller.hash(original));
    }
    @Test void changedMappingPayloadIsReimportedOnRecoveryDespiteUnchangedManifestAndObservation() throws Exception {
        Path bundle=Files.createDirectory(game.resolve("reviewed-data"));
        try(var paths=Files.list(data.path().orElseThrow())) { for(Path path:paths.toList()) Files.copy(path,bundle.resolve(path.getFileName())); }
        data=MappingData.reviewedBundle(bundle,DATA); var result=prepare();
        Files.writeString(bundle.resolve("legacy-api.lbapi"),"changed API");
        var before=snapshot(game);
        var failed=new PreservedAdmissionStore().resume(game,tx(result),pin(result),data);
        assertEquals(RECOVERY_REQUIRED,failed.state()); assertEquals(before,snapshot(game));
        assertEquals(DATA,AddOnlyInstaller.hash(bundle.resolve("manifest.json"))); unchangedUserFiles();
    }
    @Test void mismatchedReviewedSelectionAndPlanHashAreRefusedBeforeRecoveryWrites() throws Exception {
        var result=prepare(); var before=snapshot(game); var store=new PreservedAdmissionStore();
        assertEquals(RECOVERY_REQUIRED,store.resume(game,tx(result),"0".repeat(64),data).state());
        assertEquals(RECOVERY_REQUIRED,store.resume(game,tx(result),pin(result),MappingData.reviewedBundle(data.path().orElseThrow(),"0".repeat(64))).state());
        assertEquals(before,snapshot(game));
    }
    @Test void mixedLegacyJournalIsRefusedAndRetainedWithoutMigration() throws Exception {
        var result=prepare(); Files.writeString(tx(result).resolve(TransactionJournal.PLAN),"{}"); var before=snapshot(game);
        assertEquals(RECOVERY_REQUIRED,new PreservedAdmissionStore().resume(game,tx(result),pin(result),data).state()); assertEquals(before,snapshot(game));
    }
    @Test void lateSourceChangePreventsCompleteObservationAndKeepsBindingRecoverable() throws Exception {
        var failed=new PreservedAdmissionStore((boundary,transaction)->{if(boundary==PreservedAdmissionStore.Boundary.BEFORE_OBSERVATION) Files.writeString(original,"late external change");}).prepare(game,request());
        assertEquals(RECOVERY_REQUIRED,failed.state()); assertTrue(failed.planSha256().isPresent());
        assertFalse(Files.exists(tx(failed).resolve(PreservedAdmissionJournal.CHECKED))); assertEquals(TARGET,AddOnlyInstaller.hash(staged));
    }
    @Test void sharedInstallerLockRefusesCompetingPreparationWithoutCreatingAnOperation() throws Exception {
        var acquired=new CountDownLatch(1); var release=new CountDownLatch(1); var executor=Executors.newSingleThreadExecutor();
        Future<?> holder=executor.submit(()->{try(var lock=InstallationLock.acquire(game.resolve("legacybridge/transactions"))) { acquired.countDown(); assertTrue(release.await(20,TimeUnit.SECONDS)); } catch(Exception e) {throw new RuntimeException(e);} });
        try {
            assertTrue(acquired.await(20,TimeUnit.SECONDS)); var failed=new PreservedAdmissionStore().prepare(game,request());
            assertEquals("INSTALL_BUSY",failed.error().orElseThrow().code()); assertTrue(failed.transaction().isEmpty()); unchangedUserFiles();
        } finally { release.countDown(); holder.get(20,TimeUnit.SECONDS); executor.shutdown(); }
    }
}
