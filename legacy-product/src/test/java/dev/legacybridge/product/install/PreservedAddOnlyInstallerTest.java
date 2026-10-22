package dev.legacybridge.product.install;

import dev.legacybridge.converter.service.*;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static dev.legacybridge.product.install.PreservedInstallResult.State.*;

/** Real authored original/converted pair, read solely as data; synthetic installed metadata is inert. */
@EnabledIfSystemProperty(named="legacybridge.admissionBundle", matches=".+")
class PreservedAddOnlyInstallerTest {
    static final String SOURCE="49dfbd7c65918c16ce8c057df992f7e9b7c188a3fe5634adb42bb1bc5dbfc19f";
    static final String TARGET="78de0e2fd62ed83315518a1980ae0738b354531736a5eea73195d332aecc186d";
    static final String DATA="0edd3ba641dbf858b7464096794a3caf4efca9d04ef4484a740a9599e9aec2bb";
    static final Set<String> BASE=Set.of("minecraft","forge","legacybridge_runtime");
    static final InstallSession SESSION=new InstallSession(BASE,Set.of(),false);
    @TempDir Path game;
    Path original,staged; MappingData data; PreservedInstallApproval approval;
    final PreservedAddOnlyInstaller installer=new PreservedAddOnlyInstaller();
    @BeforeEach void setup() throws Exception {
        for(String area:List.of("input","staging","reports","cache","transactions")) Files.createDirectories(game.resolve("legacybridge/"+area));
        Files.createDirectory(game.resolve("mods")); Files.writeString(game.resolve("mods/user.txt"),"untouched");
        AddOnlyInstallerTest.installedMod(game.resolve("mods/LegacyBridge.jar"),"legacybridge_runtime",AdapterCatalog.RUNTIME_VERSION);
        original=Files.copy(Path.of(System.getProperty("legacybridge.admissionOriginal")),game.resolve("legacybridge/input/original.jar"));
        staged=Files.copy(Path.of(System.getProperty("legacybridge.admissionCandidate")),game.resolve("legacybridge/staging/candidate.jar"));
        data=MappingData.reviewedBundle(Path.of(System.getProperty("legacybridge.admissionBundle")),DATA);
        var admission=new PreservedAdmissionStore().prepare(game,new PreservedArtifactRequest(original,SOURCE,staged,TARGET,data));
        assertEquals(PreservedAdmissionResult.State.RECORDED_NOT_INSTALLABLE,admission.state(),admission.toString());
        approval=new PreservedInstallApproval(admission.transaction().orElseThrow(),admission.planSha256().orElseThrow(),UUID.randomUUID());
    }
    PreservedInstallResult apply() { return installer.apply(game,approval,data,SESSION); }
    PreservedInstallResult recover(PreservedInstallResult result, RecoveryAction action) {
        return installer.recover(game,result.transaction().orElseThrow(),result.planSha256().orElseThrow(),approval,data,SESSION,action);
    }
    PreservedInstallResult inspect(PreservedInstallResult result) {
        return installer.inspect(game,result.transaction().orElseThrow(),result.planSha256().orElseThrow(),approval,data,SESSION);
    }
    PreservedAddOnlyInstaller fail(PreservedAddOnlyInstaller.Boundary at) {
        return new PreservedAddOnlyInstaller((point,tx)->{if(point==at) throw new IOException("injected write/permission/disk failure at "+at);});
    }
    static void refused(PreservedInstallResult result) {
        assertEquals(RECOVERY_REQUIRED,result.state(),result.toString()); assertFalse(result.safeToClose()); assertTrue(result.error().isPresent());
    }
    void sourceIntact() throws Exception {
        assertEquals(SOURCE,AddOnlyInstaller.hash(original)); assertEquals(TARGET,AddOnlyInstaller.hash(staged));
        assertEquals("untouched",Files.readString(game.resolve("mods/user.txt")));
    }
    @Test void installInspectResumeRestoreAreRealIdempotentAndDoNotPromoteOldInstallers() throws Exception {
        var admittedBefore=PreservedAdmissionStoreTest.snapshot(approval.admission());
        var result=apply(); assertEquals(COMMITTED,result.state(),result.toString()); assertTrue(result.safeToClose());
        Path installed=result.installed().orElseThrow(), tx=result.transaction().orElseThrow();
        assertTrue(Files.isSameFile(installed,tx.resolve(PreservedInstallJournal.PAYLOAD)));
        assertFalse(Files.isSameFile(installed,staged)); assertFalse(Files.isSameFile(installed,approval.admission().resolve(PreservedAdmissionJournal.PAYLOAD)));
        assertEquals(TARGET,AddOnlyInstaller.hash(installed));
        var before=PreservedAdmissionStoreTest.snapshot(game);
        assertEquals(result,inspect(result)); assertEquals(before,PreservedAdmissionStoreTest.snapshot(game));
        assertEquals(result,recover(result,RecoveryAction.RESUME)); assertEquals(before,PreservedAdmissionStoreTest.snapshot(game));
        assertThrows(Exception.class,()->TransactionJournal.read(tx));
        assertThrows(InstallException.class,()->new AddOnlyInstaller().inspect(game,tx));
        assertThrows(Exception.class,()->StagedArtifactValidator.validate(installed,TARGET));
        var restored=recover(result,RecoveryAction.RESTORE); assertEquals(RESTORED,restored.state(),restored.toString());
        assertTrue(restored.safeToClose()); assertFalse(Files.exists(installed)); assertEquals(TARGET,AddOnlyInstaller.hash(tx.resolve(PreservedInstallJournal.PAYLOAD)));
        before=PreservedAdmissionStoreTest.snapshot(game);
        assertEquals(restored,recover(result,RecoveryAction.RESTORE)); assertEquals(before,PreservedAdmissionStoreTest.snapshot(game));
        refused(recover(result,RecoveryAction.RESUME)); refused(apply());
        assertEquals(admittedBefore,PreservedAdmissionStoreTest.snapshot(approval.admission())); sourceIntact();
    }
    @ParameterizedTest @ValueSource(strings={"PREPARED","BEFORE_PUBLISH","PUBLISHED","BEFORE_COMMIT"})
    void interruptionRetainsExactPlanAndExplicitResumeCompletesWithoutReinstall(String point) throws Exception {
        var result=fail(PreservedAddOnlyInstaller.Boundary.valueOf(point)).apply(game,approval,data,SESSION); refused(result);
        var observed=inspect(result);
        assertEquals(Set.of("PUBLISHED","BEFORE_COMMIT").contains(point)?PARTIAL:PREPARED,observed.state(),observed.toString());
        assertFalse(observed.safeToClose());
        var resumed=recover(result,RecoveryAction.RESUME); assertEquals(COMMITTED,resumed.state(),resumed.toString());
        assertEquals(resumed,recover(result,RecoveryAction.RESUME)); sourceIntact();
    }
    @ParameterizedTest @ValueSource(strings={"PAYLOAD_COPY","JOURNAL_WRITE"})
    void incompletePrivateCopyOrJournalIsNeverPublished(String point) throws Exception {
        var before=PreservedAdmissionStoreTest.snapshot(game.resolve("mods"));
        var result=fail(PreservedAddOnlyInstaller.Boundary.valueOf(point)).apply(game,approval,data,SESSION); refused(result);
        assertTrue(result.planSha256().isEmpty());
        assertEquals(before,PreservedAdmissionStoreTest.snapshot(game.resolve("mods"))); sourceIntact();
    }
    @ParameterizedTest @ValueSource(strings={"RESTORE_STARTED","BEFORE_REMOVE","REMOVED"})
    void interruptedRestoreCannotBeReappliedAndResumesDeterministically(String point) throws Exception {
        var result=apply(); assertEquals(COMMITTED,result.state(),result.toString());
        var interrupted=fail(PreservedAddOnlyInstaller.Boundary.valueOf(point)).recover(game,result.transaction().orElseThrow(),
                result.planSha256().orElseThrow(),approval,data,SESSION,RecoveryAction.RESTORE); refused(interrupted);
        assertEquals(RESTORING,inspect(result).state()); refused(recover(result,RecoveryAction.RESUME));
        assertEquals(RESTORED,recover(result,RecoveryAction.RESTORE).state()); sourceIntact();
    }
    @Test void invalidResumeDirectionDoesNotCleanCrashRetainedGuards() throws Exception {
        var result=apply(); assertEquals(COMMITTED,result.state(),result.toString()); Path tx=result.transaction().orElseThrow();
        var started=fail(PreservedAddOnlyInstaller.Boundary.RESTORE_STARTED).recover(game,tx,result.planSha256().orElseThrow(),approval,data,SESSION,RecoveryAction.RESTORE);
        refused(started);
        try(var guards=TransactionFiles.pinDirectories(game,game.resolve("legacybridge"),game.resolve("legacybridge/input"),
                game.resolve("legacybridge/staging"),game.resolve("legacybridge/transactions"),game.resolve("mods"),tx)) {
            var before=PreservedAdmissionStoreTest.snapshot(game);
            refused(recover(result,RecoveryAction.RESUME));
            assertEquals(before,PreservedAdmissionStoreTest.snapshot(game)); guards.verify();
        }
        assertEquals(RESTORED,recover(result,RecoveryAction.RESTORE).state()); sourceIntact();
    }
    @ParameterizedTest @ValueSource(strings={"schemaVersion","profile","runtimeVersion","approvalKind","installedName","admissionId","extra"})
    void editedSchemaOrApprovalCannotAuthorizeRecovery(String field) throws Exception {
        var result=apply(); Path file=result.transaction().orElseThrow().resolve(PreservedInstallJournal.PLAN);
        var row=new TreeMap<>(TransactionFiles.read(file)); row.put(field,"unknown"); Files.write(file,TransactionFiles.json(row));
        String pin=AddOnlyInstaller.hash(file); var before=PreservedAdmissionStoreTest.snapshot(game);
        refused(installer.recover(game,result.transaction().orElseThrow(),pin,approval,data,SESSION,RecoveryAction.RESTORE));
        assertEquals(before,PreservedAdmissionStoreTest.snapshot(game)); sourceIntact();
    }
    @ParameterizedTest @ValueSource(strings={"original","staged","admission","payload","checkpoint","independent-copy","missing-installed"})
    void changedFilesNeverBecomeSavedPassOrOverwriteAuthority(String change) throws Exception {
        var result=apply(); assertEquals(COMMITTED,result.state(),result.toString());
        Path tx=result.transaction().orElseThrow(), installed=result.installed().orElseThrow();
        switch(change) {
            case "original" -> Files.writeString(original,"changed");
            case "staged" -> Files.writeString(staged,"changed");
            case "admission" -> Files.writeString(approval.admission().resolve(PreservedAdmissionJournal.PLAN),"{}");
            case "payload" -> Files.writeString(tx.resolve(PreservedInstallJournal.PAYLOAD),"changed");
            case "checkpoint" -> Files.writeString(PreservedInstallJournal.path(tx,PreservedInstallJournal.Mark.COMMITTED),"{}");
            case "independent-copy" -> { Files.delete(installed); Files.copy(staged,installed); }
            case "missing-installed" -> Files.delete(installed);
        }
        var before=PreservedAdmissionStoreTest.snapshot(game); refused(inspect(result));
        refused(recover(result,RecoveryAction.RESUME)); refused(recover(result,RecoveryAction.RESTORE));
        assertEquals(before,PreservedAdmissionStoreTest.snapshot(game));
    }
    @ParameterizedTest @ValueSource(strings={"world","loaded-id","loaded-file","missing-runtime","runtime-version","duplicate","missing-dependency","optional-version","inbound-order","unknown-mods-directory"})
    void unsafeSessionAndProjectedInstalledSetsBlockApply(String reason) throws Exception {
        InstallSession session=SESSION;
        switch(reason) {
            case "world" -> session=new InstallSession(BASE,Set.of(),true);
            case "loaded-id" -> { var ids=new HashSet<>(BASE); ids.add("legacybridge_logic"); session=new InstallSession(ids,Set.of(),false); }
            case "loaded-file" -> session=new InstallSession(BASE,Set.of(staged),false);
            case "missing-runtime" -> Files.delete(game.resolve("mods/LegacyBridge.jar"));
            case "runtime-version" -> { Files.delete(game.resolve("mods/LegacyBridge.jar")); AddOnlyInstallerTest.installedMod(game.resolve("mods/LegacyBridge.jar"),"legacybridge_runtime","9.0"); }
            case "duplicate" -> Files.copy(staged,game.resolve("mods/duplicate.jar"));
            case "missing-dependency" -> dependent("missing_library","[1,2)",true,"NONE");
            case "optional-version" -> dependent("legacybridge_logic","[2,3)",false,"NONE");
            case "inbound-order" -> dependent("legacybridge_logic","[1,2)",true,"AFTER");
            case "unknown-mods-directory" -> { Files.createDirectory(game.resolve("mods/unsafe-directory")); }
        }
        var before=PreservedAdmissionStoreTest.snapshot(game.resolve("mods"));
        refused(installer.apply(game,approval,data,session)); assertEquals(before,PreservedAdmissionStoreTest.snapshot(game.resolve("mods"))); sourceIntact();
    }
    @Test void lateInventoryChangeRefusesPublicationAndUnknownCollisionIsNotOverwritten() throws Exception {
        var late=new PreservedAddOnlyInstaller((point,tx)->{
            if(point==PreservedAddOnlyInstaller.Boundary.BEFORE_PUBLISH) AddOnlyInstallerTest.installedMod(game.resolve("mods/late.jar"),"late_mod","1.0");
        }).apply(game,approval,data,SESSION); refused(late); assertEquals(PREPARED,inspect(late).state());
        var collision=new PreservedAddOnlyInstaller((point,tx)->{
            if(point==PreservedAddOnlyInstaller.Boundary.BEFORE_PUBLISH) {
                try { Files.writeString(game.resolve("mods").resolve(PreservedInstallJournal.read(tx).installedName()),"unknown",StandardOpenOption.CREATE_NEW); }
                catch(Exception failure) { throw new IOException(failure); }
            }
        }).recover(game,late.transaction().orElseThrow(),late.planSha256().orElseThrow(),approval,data,SESSION,RecoveryAction.RESUME);
        refused(collision); Path destination=game.resolve("mods").resolve(PreservedInstallJournal.read(late.transaction().orElseThrow()).installedName());
        assertEquals("unknown",Files.readString(destination)); sourceIntact();
    }
    @Test void loadedAliasAndMandatoryConsumerPreventRemovalAndNewApprovalCannotRebindOldPlan() throws Exception {
        var result=apply(); assertEquals(COMMITTED,result.state(),result.toString());
        Path alias=Files.createLink(game.resolve("loaded-alias.jar"),result.installed().orElseThrow());
        refused(installer.recover(game,result.transaction().orElseThrow(),result.planSha256().orElseThrow(),approval,data,
                new InstallSession(BASE,Set.of(alias),false),RecoveryAction.RESTORE));
        var wrong=new PreservedInstallApproval(approval.admission(),approval.admissionSha256(),UUID.randomUUID());
        refused(installer.recover(game,result.transaction().orElseThrow(),result.planSha256().orElseThrow(),wrong,data,SESSION,RecoveryAction.RESTORE));
        dependent("legacybridge_logic","[1,2)",true,"NONE");
        refused(recover(result,RecoveryAction.RESTORE)); assertEquals(TARGET,AddOnlyInstaller.hash(result.installed().orElseThrow())); sourceIntact();
    }
    @Test void missingObservationAndUnreviewedDataCannotGrantApply() throws Exception {
        Files.delete(approval.admission().resolve(PreservedAdmissionJournal.CHECKED));
        var before=PreservedAdmissionStoreTest.snapshot(game);
        refused(apply()); refused(installer.apply(game,approval,MappingData.builtinSimpleContent(),SESSION));
        refused(installer.apply(game,null,data,SESSION)); assertEquals(before,PreservedAdmissionStoreTest.snapshot(game));
    }
    private void dependent(String target,String range,boolean mandatory,String order) throws Exception {
        String metadata=AddOnlyInstallerTest.metadata("consumer","1.0",false)+"[[dependencies.consumer]]\nmodId=\""+target
                +"\"\nmandatory="+mandatory+"\nversionRange=\""+range+"\"\nordering=\""+order+"\"\nside=\"BOTH\"\n";
        AddOnlyInstallerTest.jar(game.resolve("mods/consumer.jar"),Map.of("META-INF/mods.toml",metadata.getBytes(StandardCharsets.UTF_8),
                "fixture/Declared.class",AddOnlyInstallerTest.declaration("fixture/Declared","consumer")));
    }
}
