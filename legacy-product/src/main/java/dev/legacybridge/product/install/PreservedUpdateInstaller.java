package dev.legacybridge.product.install;

import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.policy.NumericVersionRange;
import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.workflow.BridgeWorkspace;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static dev.legacybridge.product.install.TransactionFiles.require;
import static dev.legacybridge.product.install.PreservedAddOnlyInstaller.*;
import static dev.legacybridge.product.install.PreservedUpdateJournal.*;
import static dev.legacybridge.product.install.PreservedInstallResult.State;

/** Worker-only explicit update from an unloaded preserved add-only predecessor; no UI, queue or startup hook. */
public final class PreservedUpdateInstaller {
    enum Boundary { PAYLOAD_COPY, BACKUP_COPY, JOURNAL_WRITE, PREPARED, RETIRE_STARTED, BEFORE_RETIRE, RETIRED,
        BEFORE_PUBLISH, PUBLISHED, BEFORE_COMMIT, RESTORE_STARTED, BEFORE_REMOVE, REMOVED, BEFORE_RESTORE_OLD, RESTORED_OLD }
    @FunctionalInterface interface Hook {void reached(Boundary point,Path transaction) throws IOException;}
    private final Hook hook;
    public PreservedUpdateInstaller() {this((point,tx)->{});}
    PreservedUpdateInstaller(Hook hook) {this.hook=Objects.requireNonNull(hook);}
    private record Inputs(Path previous, PreservedInstallJournal.Plan old, PreservedAdmissionJournal.Plan oldAdmission, PreservedAdmissionJournal.Plan incoming) {}

    /** Fresh preflight only: no approval nonce, transaction reservation, backup, checkpoint or mods mutation. */
    public PreservedUpdatePreview preview(Path root, Path previous, String previousSha256,
            Path admission, String admissionSha256, MappingData data, InstallSession session) {
        try (var scope = control().enter()) {
            var w = workspace(root, session);
            try (var lock = InstallationLock.acquire(w.transactions())) {
                var i = inputs(w, previous, previousSha256, admission, admissionSha256, data, session);
                refuseSuccessor(w, i.previous(), null); owned(oldPath(w, i), i.previous(), i.old());
                var snapshot = InstalledModInventory.readFuture(w.mods(), session.loadedModIds(),
                        List.of(new StagedArtifactValidator.ValidatedCandidate(i.incoming().modId(), i.incoming().modVersion())),
                        Map.of(oldPath(w, i), i.old().candidateSha256()), Set.of(), Set.of(i.old().modId()));
                InstalledModInventory.unchanged(w.mods(), snapshot); w.revalidate();
                return new PreservedUpdatePreview(Optional.of(new PreservedUpdatePreview.Identity(i.old().modId(),
                        i.old().modVersion(), i.incoming().modVersion(), i.old().candidateSha256(), i.incoming().candidateSha256())), Optional.empty());
            }
        } catch (Exception problem) {
            return new PreservedUpdatePreview(Optional.empty(), failure(null, null, problem).error());
        }
    }

    public PreservedInstallResult apply(Path root,PreservedUpdateApproval approval,MappingData data,InstallSession session) {
        Path tx=null; String pin=null;
        try(var scope=control().enter()) {
            var w=workspace(root,session); Objects.requireNonNull(approval);
            try(var lock=InstallationLock.acquire(w.transactions())) {
                var i=inputs(w,approval,data,session); refuseSuccessor(w,i.previous(),null); unusedApproval(w,approval.replacement());
                owned(oldPath(w,i),i.previous(),i.old());
                TransactionFiles.budget(w.transactions());
                tx=w.transactions().resolve(UUID.randomUUID().toString()); Files.createDirectory(tx);
                var p=new Plan(tx.getFileName().toString(),i.old().transactionId(),approval.previousPlanSha256(),i.incoming().transactionId(),
                        approval.replacement().admissionSha256(),approval.replacement().approvalId().toString(),i.old().modId(),i.old().modVersion(),
                        i.incoming().modVersion(),i.old().candidateSha256(),i.incoming().candidateSha256());
                var snapshot=preflight(w,tx,p,i,session,false);
                try(var guards=TransactionFiles.pinDirectories(directories(w,tx))) {
                    Path source=w.transactions().resolve(p.admissionId()).resolve(PreservedAdmissionJournal.PAYLOAD);
                    require(Files.getFileStore(tx).getUsableSpace()>=Files.size(source)+Files.size(oldPath(w,i))+(1L<<20),"Insufficient space for independent payload and backup");
                    copy(source,tx.resolve(PAYLOAD),Boundary.PAYLOAD_COPY,tx);
                    copy(oldPath(w,i),tx.resolve(BACKUP),Boundary.BACKUP_COPY,tx);
                    verify(w,tx,p,null,i,data,session); guards.verify(); InstalledModInventory.unchanged(w.mods(),snapshot);
                    TransactionFiles.publishJson(tx.resolve(PLAN),tree(p),journalHook(tx)); pin=AddOnlyInstaller.hash(tx.resolve(PLAN)); hook.reached(Boundary.PREPARED,tx);
                    perform(w,tx,p,pin,i,data,session,RecoveryAction.RESUME,snapshot,guards);
                }
                return observe(w,tx,p,pin,i);
            }
        } catch(Exception failure) {return failure(tx,pin,failure);}
    }
    public PreservedInstallResult inspect(Path root,Path tx,String pin,PreservedUpdateApproval approval,MappingData data,InstallSession session) {
        return revisit(root,tx,pin,approval,data,session,null);
    }
    public PreservedInstallResult recover(Path root,Path tx,String pin,PreservedUpdateApproval approval,MappingData data,InstallSession session,RecoveryAction action) {
        return revisit(root,tx,pin,approval,data,session,Objects.requireNonNull(action));
    }
    private PreservedInstallResult revisit(Path root,Path requested,String pin,PreservedUpdateApproval approval,MappingData data,InstallSession session,RecoveryAction action) {
        Path tx=null;
        try(var scope=control().enter()) {
            var w=workspace(root,session); tx=TransactionFiles.transaction(requested,w.transactions());
            try(var lock=InstallationLock.acquire(w.transactions())) {
                fingerprint(tx.resolve(PLAN),pin); var p=read(tx); var i=inputs(w,approval,data,session);
                require(p.previousId().equals(i.old().transactionId()) && p.previousSha256().equals(approval.previousPlanSha256())
                        && p.admissionId().equals(i.incoming().transactionId()) && p.admissionSha256().equals(approval.replacement().admissionSha256())
                        && p.approvalId().equals(approval.replacement().approvalId().toString()),"Explicit update approval differs from binding");
                refuseSuccessor(w,i.previous(),tx); verify(w,tx,p,pin,i,data,session); var observation=observe(w,tx,p,pin,i);
                if(action==RecoveryAction.RESUME) require(observation.state()!=State.RESTORING && observation.state()!=State.RESTORED,"Restoration started; RESUME cannot reapply update");
                var snapshot=preflight(w,tx,p,i,session,action==RecoveryAction.RESTORE || observation.state()==State.RESTORING || observation.state()==State.RESTORED);
                if(action==null) return observation;
                TransactionFiles.cleanupRetainedGuards(tx,directories(w,tx));
                try(var guards=TransactionFiles.pinDirectories(directories(w,tx))) {perform(w,tx,p,pin,i,data,session,action,snapshot,guards);}
                return observe(w,tx,p,pin,i);
            }
        } catch(Exception failure) {return failure(tx,pin,failure);}
    }
    private static Inputs inputs(BridgeWorkspace w,PreservedUpdateApproval approval,MappingData data,InstallSession session) throws Exception {
        return inputs(w, approval.previousInstallation(), approval.previousPlanSha256(), approval.replacement().admission(),
                approval.replacement().admissionSha256(), data, session);
    }
    private static Inputs inputs(BridgeWorkspace w,Path selectedPrevious,String previousPin,Path selectedAdmission,
                                 String admissionPin,MappingData data,InstallSession session) throws Exception {
        Path previous=TransactionFiles.transaction(selectedPrevious,w.transactions());
        fingerprint(previous.resolve(PreservedInstallJournal.PLAN),previousPin); var old=PreservedInstallJournal.read(previous);
        require(PreservedInstallJournal.matches(previous,old,previousPin,PreservedInstallJournal.Mark.COMMITTED)
                && !PreservedInstallJournal.matches(previous,old,previousPin,PreservedInstallJournal.Mark.RESTORE_STARTED)
                && !PreservedInstallJournal.matches(previous,old,previousPin,PreservedInstallJournal.Mark.RESTORED),"Predecessor must be a committed preserved add-only installation");
        var prior=PreservedAdmissionStore.recheckLocked(w,w.transactions().resolve(old.admissionId()),old.admissionSha256(),data);
        Path admission=TransactionFiles.transaction(selectedAdmission,w.transactions());
        var incoming=PreservedAdmissionStore.recheckLocked(w,admission,admissionPin,data);
        PreservedAddOnlyInstaller.verify(w,previous,old,previousPin,prior,data,session);
        guardUnloaded(session,incoming.modId(),List.of(TransactionFiles.resolveRelative(w.input(),incoming.original()),
                TransactionFiles.resolveRelative(w.staging(),incoming.staged()),admission.resolve(PreservedAdmissionJournal.PAYLOAD)));
        require(old.modId().equals(incoming.modId()) && !old.candidateSha256().equals(incoming.candidateSha256())
                && NumericVersionRange.contains("("+old.modVersion()+",)",incoming.modVersion()),"Update requires same mod ID and strictly newer comparable numeric version");
        return new Inputs(previous,old,prior,incoming);
    }
    private static void verify(BridgeWorkspace w,Path tx,Plan p,String pin,Inputs i,MappingData data,InstallSession session) throws Exception {
        w.revalidate(); TransactionFiles.directory(tx);
        if(pin!=null) {fingerprint(tx.resolve(PLAN),pin); require(read(tx).equals(p),"Update binding changed");}
        PreservedAddOnlyInstaller.verify(w,i.previous(),i.old(),p.previousSha256(),i.oldAdmission(),data,session);
        var admission=w.transactions().resolve(p.admissionId()); PreservedAdmissionStore.verifyBoundInputs(w,admission,i.incoming(),p.admissionSha256(),data);
        require(PreservedAdmissionJournal.checkpointMatches(admission,i.incoming(),p.admissionSha256()),"Incoming admission observation changed");
        require(p.modId().equals(i.old().modId()) && p.modId().equals(i.incoming().modId()) && p.modVersion().equals(i.incoming().modVersion())
                && p.previousVersion().equals(i.old().modVersion()) && p.previousArtifactSha256().equals(i.old().candidateSha256())
                && p.candidateSha256().equals(i.incoming().candidateSha256()),"Update identity differs from independently inspected inputs");
        Path payload=TransactionFiles.descendant(tx.resolve(PAYLOAD),tx),backup=TransactionFiles.descendant(tx.resolve(BACKUP),tx);
        fingerprint(payload,p.candidateSha256()); fingerprint(backup,p.previousArtifactSha256());
        var originals=List.of(TransactionFiles.resolveRelative(w.input(),i.incoming().original()),TransactionFiles.resolveRelative(w.staging(),i.incoming().staged()),
                admission.resolve(PreservedAdmissionJournal.PAYLOAD),i.previous().resolve(PreservedInstallJournal.PAYLOAD),
                TransactionFiles.resolveRelative(w.input(),i.oldAdmission().original()),TransactionFiles.resolveRelative(w.staging(),i.oldAdmission().staged()),
                w.transactions().resolve(i.oldAdmission().transactionId()).resolve(PreservedAdmissionJournal.PAYLOAD));
        for(Path original:originals) {require(!Files.isSameFile(payload,original) && !Files.isSameFile(backup,original),"Update payload/backup must be independent");}
        require(!Files.isSameFile(payload,backup),"Backup aliases new payload");
        var paths=new ArrayList<>(originals); paths.addAll(List.of(payload,backup,tx.resolve(RETIRED),w.mods().resolve(p.installedName()),oldPath(w,i)));
        guardUnloaded(session,p.modId(),paths); require(Files.getFileStore(payload).equals(Files.getFileStore(w.mods())),"Same-filesystem publication required");
        if(TransactionFiles.exists(tx.resolve(RETIRED))) owned(tx.resolve(RETIRED),i.previous(),i.old());
        if(TransactionFiles.exists(oldPath(w,i))) owned(oldPath(w,i),i.previous(),i.old());
        if(TransactionFiles.exists(w.mods().resolve(p.installedName()))) ownNew(w.mods().resolve(p.installedName()),tx,p);
    }
    private static InstalledModInventory.Snapshot preflight(BridgeWorkspace w,Path tx,Plan p,Inputs i,InstallSession session,boolean restore) throws Exception {
        var excluded=new TreeMap<Path,String>(); Path old=oldPath(w,i),next=w.mods().resolve(p.installedName());
        if(TransactionFiles.exists(old)) {owned(old,i.previous(),i.old()); excluded.put(old,p.previousArtifactSha256());}
        if(TransactionFiles.exists(next)) {ownNew(next,tx,p); excluded.put(next,p.candidateSha256());}
        return InstalledModInventory.readFuture(w.mods(),session.loadedModIds(),List.of(new StagedArtifactValidator.ValidatedCandidate(p.modId(),restore?p.previousVersion():p.modVersion())),
                excluded,Set.of(),Set.of(p.modId()));
    }
    private void perform(BridgeWorkspace w,Path tx,Plan p,String pin,Inputs i,MappingData data,InstallSession session,RecoveryAction action,
                         InstalledModInventory.Snapshot snapshot,TransactionFiles.DirectoryIdentities guards) throws Exception {
        verify(w,tx,p,pin,i,data,session); observe(w,tx,p,pin,i); guards.verify(); InstalledModInventory.unchanged(w.mods(),snapshot);
        Path old=oldPath(w,i),next=w.mods().resolve(p.installedName()); OperationControl.beginPublication();
        if(action==RecoveryAction.RESUME) {
            record(tx,p,pin,Mark.RETIRE_STARTED); hook.reached(Boundary.RETIRE_STARTED,tx);
            if(!TransactionFiles.exists(tx.resolve(RETIRED))) {owned(old,i.previous(),i.old()); TransactionFiles.link(tx.resolve(RETIRED),old);}
            if(TransactionFiles.exists(old)) {
                hook.reached(Boundary.BEFORE_RETIRE,tx); verify(w,tx,p,pin,i,data,session); guards.verify(); InstalledModInventory.unchanged(w.mods(),snapshot);
                owned(old,i.previous(),i.old()); Files.delete(old); snapshot=changed(snapshot,old,null); hook.reached(Boundary.RETIRED,tx);
            }
            if(!TransactionFiles.exists(next)) {
                hook.reached(Boundary.BEFORE_PUBLISH,tx); verify(w,tx,p,pin,i,data,session); guards.verify(); InstalledModInventory.unchanged(w.mods(),snapshot);
                TransactionFiles.link(next,tx.resolve(PAYLOAD)); snapshot=changed(snapshot,next,p.candidateSha256()); hook.reached(Boundary.PUBLISHED,tx);
            }
            hook.reached(Boundary.BEFORE_COMMIT,tx); verify(w,tx,p,pin,i,data,session); guards.verify(); InstalledModInventory.unchanged(w.mods(),snapshot);
            record(tx,p,pin,Mark.COMMITTED);
        } else {
            record(tx,p,pin,Mark.RESTORE_STARTED); hook.reached(Boundary.RESTORE_STARTED,tx);
            if(TransactionFiles.exists(next)) {
                hook.reached(Boundary.BEFORE_REMOVE,tx); verify(w,tx,p,pin,i,data,session); guards.verify(); InstalledModInventory.unchanged(w.mods(),snapshot);
                ownNew(next,tx,p); Files.delete(next); snapshot=changed(snapshot,next,null); hook.reached(Boundary.REMOVED,tx);
            }
            if(!TransactionFiles.exists(old)) {
                hook.reached(Boundary.BEFORE_RESTORE_OLD,tx); verify(w,tx,p,pin,i,data,session); guards.verify(); InstalledModInventory.unchanged(w.mods(),snapshot);
                owned(tx.resolve(RETIRED),i.previous(),i.old()); TransactionFiles.link(old,tx.resolve(RETIRED)); snapshot=changed(snapshot,old,p.previousArtifactSha256()); hook.reached(Boundary.RESTORED_OLD,tx);
            }
            hook.reached(Boundary.BEFORE_COMMIT,tx); verify(w,tx,p,pin,i,data,session); guards.verify(); InstalledModInventory.unchanged(w.mods(),snapshot);
            record(tx,p,pin,Mark.RESTORED);
        }
        verify(w,tx,p,pin,i,data,session); guards.verify(); InstalledModInventory.unchanged(w.mods(),snapshot); observe(w,tx,p,pin,i);
    }
    private static PreservedInstallResult observe(BridgeWorkspace w,Path tx,Plan p,String pin,Inputs i) throws Exception {
        boolean started=matches(tx,p,pin,Mark.RETIRE_STARTED),committed=matches(tx,p,pin,Mark.COMMITTED),restoring=matches(tx,p,pin,Mark.RESTORE_STARTED),restored=matches(tx,p,pin,Mark.RESTORED);
        Path destination=w.mods().resolve(p.installedName()); boolean old=TransactionFiles.exists(oldPath(w,i)),next=TransactionFiles.exists(destination),retired=TransactionFiles.exists(tx.resolve(RETIRED));
        require(!old || !next,"Two update versions are simultaneously installed"); require(started || old && !next && !retired,"Missing predecessor without retirement intent");
        require(old || started && retired,"Missing retired ownership identity"); require(!next || started && retired,"New publication lacks retirement provenance");
        require(!committed || next || restoring,"Committed candidate disappeared; no blind reconstruction");
        require(!restored || restoring && old && !next,"Restored receipt contradicts actual prior set");
        State state=restored?State.RESTORED:restoring?State.RESTORING:committed?State.COMMITTED:started?State.PARTIAL:State.PREPARED;
        return new PreservedInstallResult(state,Optional.of(tx),Optional.of(pin),next?Optional.of(destination):Optional.empty(),Optional.empty());
    }
    /** Block earlier recovery/new update while a live successor owns retirement, including PREPARED. */
    static void refuseSuccessor(BridgeWorkspace w,Path previous,Path current) throws Exception {
        int count=0; try(var entries=Files.newDirectoryStream(w.transactions())) {
            for(Path tx:entries) {
                require(++count<=128,"Update successor inventory exceeds bound"); if(!TransactionFiles.uuid(tx.getFileName().toString())) continue;
                TransactionFiles.directory(tx); if(tx.equals(current) || !TransactionFiles.exists(tx.resolve(PLAN))) continue;
                var p=read(tx); if(!p.previousId().equals(previous.getFileName().toString())) continue;
                fingerprint(previous.resolve(PreservedInstallJournal.PLAN),p.previousSha256()); String pin=AddOnlyInstaller.hash(tx.resolve(PLAN));
                require(matches(tx,p,pin,Mark.RESTORED) && matches(tx,p,pin,Mark.RESTORE_STARTED)
                        && !TransactionFiles.exists(w.mods().resolve(p.installedName())),"Resolve preserved update successor first: "+tx.getFileName());
            }
        }
    }
    private static Path oldPath(BridgeWorkspace w,Inputs i) {return w.mods().resolve(i.old().installedName());}
    private static void ownNew(Path destination,Path tx,Plan p) throws Exception {fingerprint(destination,p.candidateSha256()); require(Files.isSameFile(destination,tx.resolve(PAYLOAD)),"Unknown new publication identity");}
    private void copy(Path source,Path destination,Boundary point,Path tx) throws Exception {TransactionFiles.copy(source,destination,(ignored,index)->hook.reached(point,tx),TransactionalInstaller.Boundary.PAYLOAD_COPY,0);}
    private TransactionalInstaller.Hook journalHook(Path tx) {return (ignored,index)->hook.reached(Boundary.JOURNAL_WRITE,tx);}
    private void record(Path tx,Plan p,String pin,Mark mark) throws Exception {TransactionFiles.publishJson(path(tx,mark),checkpoint(p,pin,mark),journalHook(tx));}
    private static OperationControl control() {return new OperationControl(new OperationLimits(64L<<20,1L<<30,512L<<20,Duration.ofMinutes(5)),p->{});}
}
