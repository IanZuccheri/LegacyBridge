package dev.legacybridge.product.install;

import dev.legacybridge.converter.service.*;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static dev.legacybridge.product.install.PreservedInstallResult.State.*;

/** Authored original, metadata-only next version and actual engine output; no mod class execution. */
@EnabledIfSystemProperty(named="legacybridge.admissionBundle",matches=".+")
class PreservedUpdateInstallerTest {
    static final String SOURCE=PreservedAddOnlyInstallerTest.SOURCE,TARGET=PreservedAddOnlyInstallerTest.TARGET,DATA=PreservedAddOnlyInstallerTest.DATA;
    static final InstallSession SESSION=PreservedAddOnlyInstallerTest.SESSION;
    @TempDir static Path variants;
    static Path updatedOriginal,updatedCandidate; static String updatedSourceSha,updatedCandidateSha;
    @TempDir Path game;
    Path original,staged,newOriginal,newStaged; MappingData data;
    PreservedInstallApproval initialApproval; PreservedInstallResult predecessor; PreservedUpdateApproval approval;
    @BeforeAll static void versionFixture() throws Exception {
        updatedOriginal=variants.resolve("original-1.0.1.jar"); updatedCandidate=variants.resolve("converted-1.0.1.jar");
        try(var zip=new ZipFile(System.getProperty("legacybridge.admissionOriginal")); var out=new ZipOutputStream(Files.newOutputStream(updatedOriginal,StandardOpenOption.CREATE_NEW))) {
            for(var entry:Collections.list(zip.entries())) {
                byte[] bytes; try(var in=zip.getInputStream(entry)){bytes=in.readAllBytes();}
                if(entry.getName().equals("META-INF/mods.toml") || entry.getName().equals("META-INF/MANIFEST.MF"))
                    bytes=new String(bytes,StandardCharsets.UTF_8).replace("1.0.0","1.0.1").getBytes(StandardCharsets.UTF_8);
                var copy=new ZipEntry(entry.getName()); copy.setTime(0); out.putNextEntry(copy); out.write(bytes); out.closeEntry();
            }
        }
        updatedSourceSha=AddOnlyInstaller.hash(updatedOriginal);
        try(var engine=new LegacyEngineService()) {
            var result=engine.convert(new ConversionRequest(updatedOriginal,updatedCandidate,mappingData(),Optional.of(updatedSourceSha),ConversionProfile.LOGIC_PRESERVING_V1))
                    .completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED,result.status(),result.toString());
        }
        updatedCandidateSha=AddOnlyInstaller.hash(updatedCandidate);
        System.out.println("PRESERVED_UPDATE_FIXTURE original="+updatedSourceSha+" candidate="+updatedCandidateSha+" version=1.0.1");
    }
    static MappingData mappingData() {return MappingData.reviewedBundle(Path.of(System.getProperty("legacybridge.admissionBundle")),DATA);}
    @BeforeEach void setup() throws Exception {
        for(String area:List.of("input","staging","reports","cache","transactions")) Files.createDirectories(game.resolve("legacybridge/"+area));
        Files.createDirectory(game.resolve("mods")); Files.writeString(game.resolve("mods/user.txt"),"untouched");
        AddOnlyInstallerTest.installedMod(game.resolve("mods/LegacyBridge.jar"),"legacybridge_runtime",AdapterCatalog.RUNTIME_VERSION);
        original=Files.copy(Path.of(System.getProperty("legacybridge.admissionOriginal")),game.resolve("legacybridge/input/original.jar"));
        staged=Files.copy(Path.of(System.getProperty("legacybridge.admissionCandidate")),game.resolve("legacybridge/staging/old.jar"));
        newOriginal=Files.copy(updatedOriginal,game.resolve("legacybridge/input/new.jar")); newStaged=Files.copy(updatedCandidate,game.resolve("legacybridge/staging/new.jar"));
        data=mappingData(); initialApproval=admit(original,SOURCE,staged,TARGET);
        predecessor=new PreservedAddOnlyInstaller().apply(game,initialApproval,data,SESSION); assertEquals(COMMITTED,predecessor.state(),predecessor.toString());
        approval=new PreservedUpdateApproval(admit(newOriginal,updatedSourceSha,newStaged,updatedCandidateSha),predecessor.transaction().orElseThrow(),predecessor.planSha256().orElseThrow());
    }
    PreservedInstallApproval admit(Path source,String sourceSha,Path candidate,String candidateSha) {
        var result=new PreservedAdmissionStore().prepare(game,new PreservedArtifactRequest(source,sourceSha,candidate,candidateSha,data));
        assertEquals(PreservedAdmissionResult.State.RECORDED_NOT_INSTALLABLE,result.state(),result.toString());
        return new PreservedInstallApproval(result.transaction().orElseThrow(),result.planSha256().orElseThrow(),UUID.randomUUID());
    }
    PreservedInstallResult apply() {return new PreservedUpdateInstaller().apply(game,approval,data,SESSION);}
    PreservedInstallResult recovery(PreservedInstallResult r,RecoveryAction action) {return new PreservedUpdateInstaller().recover(game,r.transaction().orElseThrow(),r.planSha256().orElseThrow(),approval,data,SESSION,action);}
    PreservedInstallResult inspect(PreservedInstallResult r) {return new PreservedUpdateInstaller().inspect(game,r.transaction().orElseThrow(),r.planSha256().orElseThrow(),approval,data,SESSION);}
    PreservedUpdateInstaller failing(String at) {return new PreservedUpdateInstaller((point,tx)->{if(point.name().equals(at))throw new IOException("Injected write/disk/permission failure at "+at);});}
    static void refused(PreservedInstallResult r) {assertEquals(RECOVERY_REQUIRED,r.state(),r.toString());assertFalse(r.safeToClose());assertTrue(r.error().isPresent());}
    void originalsIntact() throws Exception {assertEquals(SOURCE,AddOnlyInstaller.hash(original));assertEquals(TARGET,AddOnlyInstaller.hash(staged));
        assertEquals(updatedSourceSha,AddOnlyInstaller.hash(newOriginal));assertEquals(updatedCandidateSha,AddOnlyInstaller.hash(newStaged));assertEquals("untouched",Files.readString(game.resolve("mods/user.txt")));}
    @Test void realUpgradeBackupIdempotentResumeAndRestoreKeepPredecessorJournalImmutable() throws Exception {
        var priorFiles=PreservedAdmissionStoreTest.snapshot(predecessor.transaction().orElseThrow());
        var result=apply();assertEquals(COMMITTED,result.state(),result.toString());assertTrue(result.safeToClose());
        Path tx=result.transaction().orElseThrow(),backup=tx.resolve(PreservedUpdateJournal.BACKUP);
        assertEquals(TARGET,AddOnlyInstaller.hash(backup));assertFalse(Files.isSameFile(backup,predecessor.transaction().orElseThrow().resolve(PreservedInstallJournal.PAYLOAD)));
        assertFalse(Files.exists(predecessor.installed().orElseThrow()));assertEquals(updatedCandidateSha,AddOnlyInstaller.hash(result.installed().orElseThrow()));
        assertEquals(priorFiles,PreservedAdmissionStoreTest.snapshot(predecessor.transaction().orElseThrow()));
        var snapshot=PreservedAdmissionStoreTest.snapshot(game);assertEquals(result,inspect(result));assertEquals(result,recovery(result,RecoveryAction.RESUME));assertEquals(snapshot,PreservedAdmissionStoreTest.snapshot(game));
        refused(new PreservedAddOnlyInstaller().recover(game,approval.previousInstallation(),approval.previousPlanSha256(),initialApproval,data,SESSION,RecoveryAction.RESTORE));
        refused(apply()); // Fresh update needed, no duplicate nonce/reuse of missing predecessor.
        var restored=recovery(result,RecoveryAction.RESTORE);assertEquals(RESTORED,restored.state(),restored.toString());assertTrue(restored.safeToClose());
        assertEquals(TARGET,AddOnlyInstaller.hash(predecessor.installed().orElseThrow()));assertFalse(Files.exists(result.installed().orElseThrow()));
        snapshot=PreservedAdmissionStoreTest.snapshot(game);assertEquals(restored,recovery(result,RecoveryAction.RESTORE));refused(recovery(result,RecoveryAction.RESUME));assertEquals(snapshot,PreservedAdmissionStoreTest.snapshot(game));
        assertEquals(predecessor,new PreservedAddOnlyInstaller().inspect(game,approval.previousInstallation(),approval.previousPlanSha256(),initialApproval,data,SESSION));
        assertEquals(priorFiles,PreservedAdmissionStoreTest.snapshot(predecessor.transaction().orElseThrow()));originalsIntact();
    }
    @ParameterizedTest @ValueSource(strings={"PREPARED","RETIRE_STARTED","BEFORE_RETIRE","RETIRED","BEFORE_PUBLISH","PUBLISHED","BEFORE_COMMIT"})
    void interruptedApplyCanResumeAndThenRestore(String boundary) throws Exception {
        var result=failing(boundary).apply(game,approval,data,SESSION);refused(result);
        var before=PreservedAdmissionStoreTest.snapshot(game);assertTrue(Set.of(PREPARED,PARTIAL).contains(inspect(result).state()));assertEquals(before,PreservedAdmissionStoreTest.snapshot(game));
        var resume=recovery(result,RecoveryAction.RESUME);assertEquals(COMMITTED,resume.state(),resume.toString());
        assertEquals(RESTORED,recovery(result,RecoveryAction.RESTORE).state());originalsIntact();
    }
    @ParameterizedTest @ValueSource(strings={"PREPARED","RETIRE_STARTED","BEFORE_RETIRE","RETIRED","PUBLISHED"})
    void interruptedApplyCanRestoreWithoutPublishingNewVersion(String boundary) throws Exception {
        var result=failing(boundary).apply(game,approval,data,SESSION);refused(result);
        assertEquals(RESTORED,recovery(result,RecoveryAction.RESTORE).state());assertEquals(TARGET,AddOnlyInstaller.hash(predecessor.installed().orElseThrow()));
        var snapshot=PreservedAdmissionStoreTest.snapshot(game);refused(recovery(result,RecoveryAction.RESUME));assertEquals(snapshot,PreservedAdmissionStoreTest.snapshot(game));originalsIntact();
    }
    @ParameterizedTest @ValueSource(strings={"RESTORE_STARTED","BEFORE_REMOVE","REMOVED","BEFORE_RESTORE_OLD","RESTORED_OLD","BEFORE_COMMIT"})
    void interruptedRestoreCannotReapplyAndExplicitRestoreIsDeterministic(String boundary) throws Exception {
        var result=apply();assertEquals(COMMITTED,result.state(),result.toString());
        refused(failing(boundary).recover(game,result.transaction().orElseThrow(),result.planSha256().orElseThrow(),approval,data,SESSION,RecoveryAction.RESTORE));
        var snapshot=PreservedAdmissionStoreTest.snapshot(game);refused(recovery(result,RecoveryAction.RESUME));assertEquals(snapshot,PreservedAdmissionStoreTest.snapshot(game));
        assertEquals(RESTORED,recovery(result,RecoveryAction.RESTORE).state());originalsIntact();
    }
    @ParameterizedTest @ValueSource(strings={"PAYLOAD_COPY","BACKUP_COPY","JOURNAL_WRITE"})
    void privateWriteFailureNeverRetiresOldModOrPublishesPartial(String boundary) throws Exception {
        var before=PreservedAdmissionStoreTest.snapshot(game.resolve("mods"));var result=failing(boundary).apply(game,approval,data,SESSION);refused(result);
        assertEquals(before,PreservedAdmissionStoreTest.snapshot(game.resolve("mods")));assertTrue(result.planSha256().isEmpty());originalsIntact();
    }
    @ParameterizedTest @ValueSource(strings={"world","id","file","alias","new-file","new-alias"})
    void loadedIdentityAndWorldRefuseWithoutRetiringAnything(String kind) throws Exception {
        Path loaded=predecessor.installed().orElseThrow();if(kind.equals("alias")){loaded=game.resolve("alias.jar");Files.createLink(loaded,predecessor.installed().orElseThrow());}
        if(kind.equals("new-file"))loaded=newStaged;
        if(kind.equals("new-alias")){loaded=game.resolve("alias-new.jar");Files.createLink(loaded,newStaged);}
        var session=new InstallSession(kind.equals("id")?Set.of("minecraft","forge","legacybridge_runtime","legacybridge_logic"):SESSION.loadedModIds(),
                Set.of("file","alias","new-file","new-alias").contains(kind)?Set.of(loaded):Set.of(),kind.equals("world"));
        var before=PreservedAdmissionStoreTest.snapshot(game);refused(new PreservedUpdateInstaller().apply(game,approval,data,session));assertEquals(before,PreservedAdmissionStoreTest.snapshot(game));
    }
    @Test void identicalIndependentOldFileIsUnknownAndNeverRetired() throws Exception {
        Path file=predecessor.installed().orElseThrow();Files.delete(file);Files.copy(staged,file);
        var before=PreservedAdmissionStoreTest.snapshot(game);refused(apply());assertEquals(before,PreservedAdmissionStoreTest.snapshot(game));
    }
    @Test void sameVersionIsNotUpdateAndSavedPassDoesNotOverrideChangedInputs() throws Exception {
        var same=new PreservedUpdateApproval(initialApproval,approval.previousInstallation(),approval.previousPlanSha256());
        var before=PreservedAdmissionStoreTest.snapshot(game);refused(new PreservedUpdateInstaller().apply(game,same,data,SESSION));assertEquals(before,PreservedAdmissionStoreTest.snapshot(game));
        Files.writeString(newOriginal,"corrupt");before=PreservedAdmissionStoreTest.snapshot(game);refused(apply());assertEquals(before,PreservedAdmissionStoreTest.snapshot(game));
    }
    @ParameterizedTest @ValueSource(strings={"backup","new-identity","old-identity","plan","old-plan","approval","data"})
    void changedRecoveryInputsCannotAuthorizeMutation(String kind) throws Exception {
        var result=apply();assertEquals(COMMITTED,result.state(),result.toString());Path tx=result.transaction().orElseThrow();
        if(kind.equals("backup")) Files.writeString(tx.resolve(PreservedUpdateJournal.BACKUP),"corrupt");
        if(kind.equals("new-identity")){Path p=result.installed().orElseThrow();Files.delete(p);Files.copy(newStaged,p);}
        if(kind.equals("old-identity")){Path p=approval.previousInstallation().resolve(PreservedInstallJournal.PAYLOAD);Files.delete(p);Files.copy(staged,p);}
        if(kind.equals("plan")) Files.writeString(tx.resolve(PreservedUpdateJournal.PLAN),"{}");
        if(kind.equals("old-plan")) Files.writeString(approval.previousInstallation().resolve(PreservedInstallJournal.PLAN),"{}");
        if(kind.equals("approval")) approval=new PreservedUpdateApproval(new PreservedInstallApproval(approval.replacement().admission(),approval.replacement().admissionSha256(),UUID.randomUUID()),approval.previousInstallation(),approval.previousPlanSha256());
        if(kind.equals("data")) data=MappingData.reviewedBundle(data.path().orElseThrow(),"0".repeat(64));
        var before=PreservedAdmissionStoreTest.snapshot(game);refused(recovery(result,RecoveryAction.RESTORE));assertEquals(before,PreservedAdmissionStoreTest.snapshot(game));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void optionalVersionMismatchAndRequiredConsumerBlockRetirement(boolean mandatory) throws Exception {
        String toml=AddOnlyInstallerTest.metadata("consumer","1.0.0",false)+"[[dependencies.consumer]]\nmodId=\"legacybridge_logic\"\nmandatory="+mandatory+"\nordering=\"NONE\"\nside=\"BOTH\"\nversionRange=\""+(mandatory?"[1.0.0,2.0.0)":"[1.0.0]")+"\"\n";
        AddOnlyInstallerTest.jar(game.resolve("mods/consumer.jar"),Map.of("META-INF/mods.toml",toml.getBytes(StandardCharsets.UTF_8),"fixture/Declared.class",AddOnlyInstallerTest.declaration("fixture/Declared","consumer")));
        var before=PreservedAdmissionStoreTest.snapshot(game.resolve("mods"));refused(apply());assertEquals(before,PreservedAdmissionStoreTest.snapshot(game.resolve("mods")));
    }
    @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void realWindowsDenyDeleteKeepsOldArtifactAndExplicitResumeWorksAfterRelease() throws Exception {
        PreservedInstallResult interrupted;
        try(var held=java.nio.channels.FileChannel.open(predecessor.installed().orElseThrow(),StandardOpenOption.READ,com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE)) {
            assertTrue(held.isOpen());interrupted=apply();refused(interrupted);
            assertEquals(TARGET,AddOnlyInstaller.hash(predecessor.installed().orElseThrow()));
        }
        assertEquals(COMMITTED,recovery(interrupted,RecoveryAction.RESUME).state());assertEquals(RESTORED,recovery(interrupted,RecoveryAction.RESTORE).state());originalsIntact();
    }
    @Test void lateInstalledSetChangeIsVisibleAndDoesNotRetireOldArtifact() throws Exception {
        var installer=new PreservedUpdateInstaller((point,tx)->{if(point==PreservedUpdateInstaller.Boundary.BEFORE_RETIRE)
            AddOnlyInstallerTest.installedMod(game.resolve("mods/external.jar"),"external","1.0.0");});
        var result=installer.apply(game,approval,data,SESSION);refused(result);
        assertEquals(TARGET,AddOnlyInstaller.hash(predecessor.installed().orElseThrow()));assertTrue(Files.exists(game.resolve("mods/external.jar")));
        assertEquals(RESTORED,recovery(result,RecoveryAction.RESTORE).state());assertTrue(Files.exists(game.resolve("mods/external.jar")));originalsIntact();
    }
    @Test void preparedSuccessorBlocksPredecessorRestoreAndSecondUpdateUntilExplicitlyRestored() throws Exception {
        var pending=failing("PREPARED").apply(game,approval,data,SESSION);refused(pending);var before=PreservedAdmissionStoreTest.snapshot(game);
        refused(new PreservedAddOnlyInstaller().recover(game,approval.previousInstallation(),approval.previousPlanSha256(),initialApproval,data,SESSION,RecoveryAction.RESTORE));
        refused(apply());assertEquals(before,PreservedAdmissionStoreTest.snapshot(game));assertEquals(RESTORED,recovery(pending,RecoveryAction.RESTORE).state());
        // Reusing the old nonce remains forbidden; only newly granted consent can start another update.
        refused(apply());approval=new PreservedUpdateApproval(new PreservedInstallApproval(approval.replacement().admission(),approval.replacement().admissionSha256(),UUID.randomUUID()),approval.previousInstallation(),approval.previousPlanSha256());
        assertEquals(COMMITTED,apply().state());originalsIntact();
    }
    @ParameterizedTest @ValueSource(strings={"schemaVersion","runtimeVersion","payload","previousId","extra"})
    void explicitNewDigestStillCannotAdmitUnknownJournalContract(String field) throws Exception {
        var result=apply();assertEquals(COMMITTED,result.state());Path tx=result.transaction().orElseThrow(),plan=tx.resolve(PreservedUpdateJournal.PLAN);
        var values=new TreeMap<>(TransactionFiles.read(plan));values.put(field,field.equals("schemaVersion")?7:"unknown");Files.write(plan,TransactionFiles.json(values));
        var before=PreservedAdmissionStoreTest.snapshot(game);
        refused(new PreservedUpdateInstaller().recover(game,tx,AddOnlyInstaller.hash(plan),approval,data,SESSION,RecoveryAction.RESTORE));
        assertEquals(before,PreservedAdmissionStoreTest.snapshot(game));
    }
    @ParameterizedTest @ValueSource(strings={"RETIRED","PUBLISHED","REMOVED"})
    void actualHelperTerminationAndFreshJvmRecoveryKeepExactArtifacts(String boundary) throws Exception {
        PreservedInstallResult result=null;
        if(boundary.equals("REMOVED")){result=apply();assertEquals(COMMITTED,result.state());}
        child(83,"halt-"+boundary,result);
        if(result==null) {
            try(var entries=Files.list(game.resolve("legacybridge/transactions"))) {
                Path tx=entries.filter(Files::isDirectory).filter(p->Files.exists(p.resolve(PreservedUpdateJournal.PLAN))).findFirst().orElseThrow();
                result=new PreservedInstallResult(RECOVERY_REQUIRED,Optional.of(tx),Optional.of(AddOnlyInstaller.hash(tx.resolve(PreservedUpdateJournal.PLAN))),Optional.empty(),Optional.empty());
            }
        }
        Path tx=result.transaction().orElseThrow();
        if(System.getProperty("os.name").startsWith("Windows"))
            try(var entries=Files.list(tx)){assertTrue(entries.anyMatch(p->p.getFileName().toString().startsWith("guard-")),"Windows abrupt exit should retain directory guard references");}
        if(!boundary.equals("REMOVED")){child(0,"resume",result);assertEquals(COMMITTED,inspect(result).state());}
        else {var before=PreservedAdmissionStoreTest.snapshot(game.resolve("legacybridge/transactions"));child(0,"refuse-resume",result);assertEquals(before,PreservedAdmissionStoreTest.snapshot(game.resolve("legacybridge/transactions")));}
        child(0,"restore",result);assertEquals(RESTORED,inspect(result).state());
        var before=PreservedAdmissionStoreTest.snapshot(game.resolve("legacybridge/transactions"));child(0,"restore",result);assertEquals(before,PreservedAdmissionStoreTest.snapshot(game.resolve("legacybridge/transactions")));
        assertEquals(TARGET,AddOnlyInstaller.hash(predecessor.installed().orElseThrow()));originalsIntact();
    }
    private void child(int exit,String mode,PreservedInstallResult result) throws Exception {
        String id=UUID.randomUUID().toString();Path arguments=game.resolve("helper-"+id+".args"),output=game.resolve("helper-"+id+".log");
        var args=new ArrayList<>(List.of("-Xmx512m","-Djava.awt.headless=true","-cp",TransactionalProcessCrashTest.childClasspath(),Child.class.getName(),game.toString(),
                approval.previousInstallation().toString(),approval.previousPlanSha256(),approval.replacement().admission().toString(),approval.replacement().admissionSha256(),
                approval.replacement().approvalId().toString(),data.path().orElseThrow().toString(),mode));
        if(result!=null) args.addAll(List.of(result.transaction().orElseThrow().toString(),result.planSha256().orElseThrow()));
        Files.writeString(arguments,args.stream().map(a->"\""+a.replace("\\","\\\\").replace("\"","\\\"")+"\"").collect(java.util.stream.Collectors.joining("\n")));
        var java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");
        Process child=new ProcessBuilder(java.toString(),"@"+arguments).directory(game.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();child.getOutputStream().close();
        boolean finished=child.waitFor(45,TimeUnit.SECONDS);if(!finished){child.destroyForcibly();child.waitFor(5,TimeUnit.SECONDS);}
        String log;try(var in=Files.newInputStream(output)){log=new String(in.readNBytes(16384),StandardCharsets.UTF_8);}
        assertTrue(finished,log);assertEquals(exit,child.exitValue(),log);System.out.println("PRESERVED_UPDATE_HELPER mode="+mode+" exit="+child.exitValue());
    }
    /** Test-only Java filesystem helper. Never loads an original/converted mod or starts Forge. */
    public static final class Child {
        public static void main(String[] args) throws Exception {
            var root=Path.of(args[0]);var approval=new PreservedUpdateApproval(new PreservedInstallApproval(Path.of(args[3]),args[4],UUID.fromString(args[5])),Path.of(args[1]),args[2]);
            var data=MappingData.reviewedBundle(Path.of(args[6]),DATA);String mode=args[7];
            var installer=new PreservedUpdateInstaller((point,tx)->{if(mode.equals("halt-"+point.name()))Runtime.getRuntime().halt(83);});
            PreservedInstallResult result;
            if(mode.startsWith("halt-")&&!mode.equals("halt-REMOVED"))result=installer.apply(root,approval,data,SESSION);
            else result=installer.recover(root,Path.of(args[8]),args[9],approval,data,SESSION,
                    mode.equals("restore")||mode.equals("halt-REMOVED")?RecoveryAction.RESTORE:RecoveryAction.RESUME);
            StateExpected.check(mode,result);
        }
    }
    private static final class StateExpected {
        static void check(String mode,PreservedInstallResult result) {
            var expected=mode.equals("refuse-resume")?RECOVERY_REQUIRED:mode.equals("restore")?RESTORED:COMMITTED;
            if(mode.startsWith("halt-") || result.state()!=expected)throw new IllegalStateException("Unexpected helper result: "+result);
        }
    }
}
