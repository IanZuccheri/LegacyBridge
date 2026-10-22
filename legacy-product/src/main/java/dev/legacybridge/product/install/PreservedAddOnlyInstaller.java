package dev.legacybridge.product.install;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationLimits;
import dev.legacybridge.converter.service.EngineError;
import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.workflow.BridgeWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import static dev.legacybridge.product.install.TransactionFiles.require;
import static dev.legacybridge.product.install.PreservedInstallJournal.Mark;
import static dev.legacybridge.product.install.PreservedInstallResult.State;

/**
 * Developer-only, worker-only add-only installation of independently inspected logic-preserving-v1.
 * Not connected to public Apply, V1/V2, launchers or classloaders. No updates or automatic recovery.
 * Caller obtains explicit approval, supplies the actual stable loader snapshot, runs off render,
 * and must not interrupt/close the process until the operation has safely returned.
 */
public final class PreservedAddOnlyInstaller {
    enum Boundary { PAYLOAD_COPY, JOURNAL_WRITE, PREPARED, BEFORE_PUBLISH, PUBLISHED,
        BEFORE_COMMIT, RESTORE_STARTED, BEFORE_REMOVE, REMOVED }
    @FunctionalInterface interface Hook { void reached(Boundary boundary, Path transaction) throws IOException; }
    private final Hook hook;
    public PreservedAddOnlyInstaller() { this((boundary, transaction) -> { }); }
    PreservedAddOnlyInstaller(Hook hook) { this.hook = java.util.Objects.requireNonNull(hook); }

    /** New confirmation nonce per Apply. The admission PASS alone never calls this method. */
    public PreservedInstallResult apply(Path gameDir, PreservedInstallApproval approval, MappingData data, InstallSession session) {
        Path tx = null; String pin = null;
        try (var scope = control().enter()) {
            var w = workspace(gameDir, session); java.util.Objects.requireNonNull(approval);
            Path admission = TransactionFiles.transaction(approval.admission(), w.transactions());
            try (var lock = InstallationLock.acquire(w.transactions())) {
                var admitted = PreservedAdmissionStore.recheckLocked(w, admission, approval.admissionSha256(), data);
                unusedApproval(w, approval);
                var candidate = new StagedArtifactValidator.ValidatedCandidate(admitted.modId(), admitted.modVersion());
                InstalledModInventory.read(w.mods(), session.loadedModIds(), candidate);
                TransactionFiles.budget(w.transactions());
                tx = w.transactions().resolve(UUID.randomUUID().toString()); Files.createDirectory(tx);
                var plan = new PreservedInstallJournal.Plan(tx.getFileName().toString(), admitted.transactionId(), approval.admissionSha256(),
                        approval.approvalId().toString(), admitted.modId(), admitted.modVersion(), admitted.candidateSha256());
                try (var guards = TransactionFiles.pinDirectories(directories(w, tx))) {
                    Path owned = tx, source = admission.resolve(PreservedAdmissionJournal.PAYLOAD);
                    require(Files.getFileStore(tx).getUsableSpace() >= Files.size(source) + (1L << 20), "Insufficient space for complete private install payload");
                    TransactionFiles.copy(source, tx.resolve(PreservedInstallJournal.PAYLOAD),
                            (boundary, index) -> hook.reached(Boundary.PAYLOAD_COPY, owned), TransactionalInstaller.Boundary.PAYLOAD_COPY, 0);
                    verify(w, tx, plan, null, admitted, data, session);
                    var snapshot = preflight(w, tx, plan, session, false);
                    guards.verify(); InstalledModInventory.unchanged(w.mods(), snapshot);
                    TransactionFiles.publishJson(tx.resolve(PreservedInstallJournal.PLAN), PreservedInstallJournal.tree(plan), journalHook(tx));
                    pin = AddOnlyInstaller.hash(tx.resolve(PreservedInstallJournal.PLAN));
                    hook.reached(Boundary.PREPARED, tx);
                    perform(w, tx, plan, pin, admitted, data, session, RecoveryAction.RESUME, snapshot, guards);
                }
                return observe(w, tx, plan, pin);
            }
        } catch (Exception failure) { return failure(tx, pin, failure); }
    }

    /** Read-only journal/JAR observation; still requires current explicit pins, data and session. */
    public PreservedInstallResult inspect(Path gameDir, Path transaction, String planSha256,
            PreservedInstallApproval approval, MappingData data, InstallSession session) {
        return revisit(gameDir, transaction, planSha256, approval, data, session, null);
    }
    /** Explicit resume/restore only. Loaded IDs/files or open worlds refuse even a pending commit receipt. */
    public PreservedInstallResult recover(Path gameDir, Path transaction, String planSha256,
            PreservedInstallApproval approval, MappingData data, InstallSession session, RecoveryAction action) {
        java.util.Objects.requireNonNull(action);
        return revisit(gameDir, transaction, planSha256, approval, data, session, action);
    }

    private PreservedInstallResult revisit(Path gameDir, Path requested, String pin, PreservedInstallApproval approval,
            MappingData data, InstallSession session, RecoveryAction action) {
        Path tx = null;
        try (var scope = control().enter()) {
            var w = workspace(gameDir, session); java.util.Objects.requireNonNull(approval); TransactionFiles.digest(pin);
            tx = TransactionFiles.transaction(requested, w.transactions());
            try (var lock = InstallationLock.acquire(w.transactions())) {
                fingerprint(tx.resolve(PreservedInstallJournal.PLAN), pin);
                var plan = PreservedInstallJournal.read(tx);
                PreservedUpdateInstaller.refuseSuccessor(w, tx, null);
                Path admission = TransactionFiles.transaction(approval.admission(), w.transactions());
                require(admission.getFileName().toString().equals(plan.admissionId()) && approval.admissionSha256().equals(plan.admissionSha256())
                        && approval.approvalId().toString().equals(plan.approvalId()), "Explicit approval differs from install binding");
                var admitted = PreservedAdmissionStore.recheckLocked(w, admission, plan.admissionSha256(), data);
                verify(w, tx, plan, pin, admitted, data, session);
                var observed = observe(w, tx, plan, pin);
                // Refuse this action before even cleaning crash-retained guards. Recovery
                // material must remain unchanged when the requested direction is invalid.
                if (action == RecoveryAction.RESUME)
                    require(observed.state() != State.RESTORING && observed.state() != State.RESTORED,
                            "Restoration started; RESUME cannot silently reapply it");
                boolean restoring = action == RecoveryAction.RESTORE || observed.state() == State.RESTORING || observed.state() == State.RESTORED;
                var snapshot = preflight(w, tx, plan, session, restoring);
                if (action == null) return observed;
                TransactionFiles.cleanupRetainedGuards(tx, directories(w, tx));
                try (var guards = TransactionFiles.pinDirectories(directories(w, tx))) {
                    perform(w, tx, plan, pin, admitted, data, session, action, snapshot, guards);
                }
                return observe(w, tx, plan, pin);
            }
        } catch (Exception failure) { return failure(tx, pin, failure); }
    }

    private void perform(BridgeWorkspace w, Path tx, PreservedInstallJournal.Plan plan, String pin,
            PreservedAdmissionJournal.Plan admitted, MappingData data, InstallSession session, RecoveryAction action,
            InstalledModInventory.Snapshot snapshot, TransactionFiles.DirectoryIdentities guards) throws Exception {
        var observed = observe(w, tx, plan, pin);
        if (action == RecoveryAction.RESUME)
            require(observed.state() != State.RESTORING && observed.state() != State.RESTORED, "Restoration started; RESUME cannot silently reapply it");
        verify(w, tx, plan, pin, admitted, data, session); guards.verify(); InstalledModInventory.unchanged(w.mods(), snapshot);
        Path destination = w.mods().resolve(plan.installedName());
        OperationControl.beginPublication(); // Non-cancellable commit/restore boundary, no shutdown calls.
        if (action == RecoveryAction.RESUME) {
            if (!TransactionFiles.exists(destination)) {
                hook.reached(Boundary.BEFORE_PUBLISH, tx);
                verify(w, tx, plan, pin, admitted, data, session); guards.verify(); InstalledModInventory.unchanged(w.mods(), snapshot);
                TransactionFiles.link(destination, tx.resolve(PreservedInstallJournal.PAYLOAD));
                hook.reached(Boundary.PUBLISHED, tx);
            }
            owned(destination, tx, plan);
            snapshot = changed(snapshot, destination, plan.candidateSha256());
            hook.reached(Boundary.BEFORE_COMMIT, tx);
            verify(w, tx, plan, pin, admitted, data, session); guards.verify(); InstalledModInventory.unchanged(w.mods(), snapshot);
            record(tx, plan, pin, Mark.COMMITTED);
        } else {
            // Durable intent BEFORE removal prevents a crash from being mistaken for a new Apply.
            record(tx, plan, pin, Mark.RESTORE_STARTED); hook.reached(Boundary.RESTORE_STARTED, tx);
            if (TransactionFiles.exists(destination)) {
                hook.reached(Boundary.BEFORE_REMOVE, tx);
                verify(w, tx, plan, pin, admitted, data, session); guards.verify(); InstalledModInventory.unchanged(w.mods(), snapshot);
                owned(destination, tx, plan); Files.delete(destination); hook.reached(Boundary.REMOVED, tx);
            }
            snapshot = changed(snapshot, destination, null);
            hook.reached(Boundary.BEFORE_COMMIT, tx);
            verify(w, tx, plan, pin, admitted, data, session); guards.verify(); InstalledModInventory.unchanged(w.mods(), snapshot);
            require(!TransactionFiles.exists(destination), "Restored destination unexpectedly exists");
            record(tx, plan, pin, Mark.RESTORED);
        }
        verify(w, tx, plan, pin, admitted, data, session); guards.verify(); InstalledModInventory.unchanged(w.mods(), snapshot);
        observe(w, tx, plan, pin);
    }

    static void verify(BridgeWorkspace w, Path tx, PreservedInstallJournal.Plan plan, String pin,
            PreservedAdmissionJournal.Plan admitted, MappingData data, InstallSession session) throws Exception {
        w.revalidate(); TransactionFiles.directory(tx); TransactionFiles.directory(w.mods());
        if (pin != null) {
            fingerprint(tx.resolve(PreservedInstallJournal.PLAN), pin);
            require(PreservedInstallJournal.read(tx).equals(plan), "Installation binding changed");
        }
        Path admission = TransactionFiles.transaction(w.transactions().resolve(plan.admissionId()), w.transactions());
        PreservedAdmissionStore.verifyBoundInputs(w, admission, admitted, plan.admissionSha256(), data);
        require(plan.modId().equals(admitted.modId()) && plan.modVersion().equals(admitted.modVersion())
                && plan.candidateSha256().equals(admitted.candidateSha256()), "Install identity differs from fresh admission");
        require(PreservedAdmissionJournal.checkpointMatches(admission, admitted, plan.admissionSha256()), "Admission observation disappeared");
        Path payload = TransactionFiles.descendant(tx.resolve(PreservedInstallJournal.PAYLOAD), tx);
        fingerprint(payload, plan.candidateSha256());
        Path original = TransactionFiles.resolveRelative(w.input(), admitted.original()), staged = TransactionFiles.resolveRelative(w.staging(), admitted.staged());
        Path admittedPayload = admission.resolve(PreservedAdmissionJournal.PAYLOAD);
        for (Path other : List.of(original, staged, admittedPayload))
            require(!Files.isSameFile(other, payload), "Installation payload must be independent from admission/source/staging");
        guardUnloaded(session, plan.modId(), List.of(original, staged, admittedPayload, payload, w.mods().resolve(plan.installedName())));
        require(Files.getFileStore(payload).equals(Files.getFileStore(w.mods())), "Same-filesystem publication required");
    }
    private static InstalledModInventory.Snapshot preflight(BridgeWorkspace w, Path tx, PreservedInstallJournal.Plan plan,
            InstallSession session, boolean restore) throws Exception {
        Path destination = w.mods().resolve(plan.installedName());
        Map<Path, String> excluded = Map.of();
        if (TransactionFiles.exists(destination)) { owned(destination, tx, plan); excluded = Map.of(destination, plan.candidateSha256()); }
        // This carrier is metadata only: the simple bootstrap validator is NOT relaxed or invoked.
        var candidates = restore ? List.<StagedArtifactValidator.ValidatedCandidate>of()
                : List.of(new StagedArtifactValidator.ValidatedCandidate(plan.modId(), plan.modVersion()));
        return InstalledModInventory.readFuture(w.mods(), session.loadedModIds(), candidates, excluded, restore ? Set.of(plan.modId()) : Set.of());
    }
    private static PreservedInstallResult observe(BridgeWorkspace w, Path tx, PreservedInstallJournal.Plan plan, String pin) throws Exception {
        boolean committed = PreservedInstallJournal.matches(tx, plan, pin, Mark.COMMITTED);
        boolean restoring = PreservedInstallJournal.matches(tx, plan, pin, Mark.RESTORE_STARTED);
        boolean restored = PreservedInstallJournal.matches(tx, plan, pin, Mark.RESTORED);
        Path destination = w.mods().resolve(plan.installedName()); boolean present = TransactionFiles.exists(destination);
        if (present) owned(destination, tx, plan);
        require(!restored || restoring && !present, "Restoration receipt contradicts owned destination/intent");
        require(!committed || present || restoring, "Committed artifact disappeared; no blind reconstruction");
        State state = restored ? State.RESTORED : restoring ? State.RESTORING : committed ? State.COMMITTED : present ? State.PARTIAL : State.PREPARED;
        return new PreservedInstallResult(state, Optional.of(tx), Optional.of(pin), present ? Optional.of(destination) : Optional.empty(), Optional.empty());
    }
    static void owned(Path destination, Path tx, PreservedInstallJournal.Plan plan) throws Exception {
        Path payload = tx.resolve(PreservedInstallJournal.PAYLOAD);
        fingerprint(destination, plan.candidateSha256()); fingerprint(payload, plan.candidateSha256());
        require(Files.isSameFile(destination, payload), "Unknown destination identity; matching bytes do not prove ownership");
    }
    static void guardUnloaded(InstallSession session, String modId, List<Path> paths) throws Exception {
        require(!session.worldOpen() && !session.loadedModIds().contains(modId), "Loaded mod/open world cannot be installed or restored");
        for (Path path : paths) for (Path loaded : session.loadedModFiles())
            require(!path.normalize().equals(loaded.normalize()) && (!TransactionFiles.exists(path) || !TransactionFiles.exists(loaded) || !Files.isSameFile(path, loaded)),
                    "Installation source/payload/destination aliases a loaded file");
    }
    static void unusedApproval(BridgeWorkspace w, PreservedInstallApproval approval) throws Exception {
        int entries = 0;
        try (var paths = Files.newDirectoryStream(w.transactions())) {
            for (Path path : paths) {
                require(++entries <= 128, "Approval inventory exceeds bound");
                if (!TransactionFiles.uuid(path.getFileName().toString())) continue;
                TransactionFiles.directory(path);
                if (TransactionFiles.exists(path.resolve(PreservedInstallJournal.PLAN)))
                    require(!PreservedInstallJournal.read(path).approvalId().equals(approval.approvalId().toString()), "Approval already bound; explicitly recover that operation or obtain new consent");
                if (TransactionFiles.exists(path.resolve(PreservedUpdateJournal.PLAN)))
                    require(!PreservedUpdateJournal.read(path).approvalId().equals(approval.approvalId().toString()), "Approval already bound to a preserved update");
            }
        }
    }
    private void record(Path tx, PreservedInstallJournal.Plan plan, String pin, Mark mark) throws Exception {
        TransactionFiles.publishJson(PreservedInstallJournal.path(tx, mark), PreservedInstallJournal.checkpoint(plan, pin, mark), journalHook(tx));
    }
    private TransactionalInstaller.Hook journalHook(Path tx) { return (boundary, index) -> hook.reached(Boundary.JOURNAL_WRITE, tx); }
    static void fingerprint(Path path, String sha) throws Exception {
        TransactionFiles.digest(sha); require(AddOnlyInstaller.hash(path).equals(sha), "Preserved install fingerprint changed: " + path.getFileName());
    }
    static InstalledModInventory.Snapshot changed(InstalledModInventory.Snapshot before, Path destination, String sha) {
        var jars = new TreeMap<>(before.jars()); if (sha == null) jars.remove(destination); else jars.put(destination, sha);
        return new InstalledModInventory.Snapshot(Map.copyOf(jars), before.ids());
    }
    static BridgeWorkspace workspace(Path gameDir, InstallSession session) throws Exception {
        java.util.Objects.requireNonNull(session); require(!session.worldOpen(), "Close the world before preserved installation/inspection/recovery");
        require(session.loadedModIds().containsAll(Set.of("minecraft", "forge", "legacybridge_runtime")), "Pinned loaded runtime baseline is missing");
        var w = PreservedAdmissionStore.workspace(gameDir); TransactionFiles.directory(w.mods());
        // Same seven indexes and maximum path depth as V2; admission records are not modified.
        return w;
    }
    static Path[] directories(BridgeWorkspace w, Path tx) {
        return new Path[] {w.gameDir(), w.root(), w.input(), w.staging(), w.transactions(), w.mods(), tx};
    }
    private static OperationControl control() {
        return new OperationControl(new OperationLimits(64L << 20, 512L << 20, 256L << 20, Duration.ofMinutes(3)), progress -> { });
    }
    static PreservedInstallResult failure(Path tx, String pin, Exception failure) {
        var error = new EngineError(failure instanceof InstallException install ? install.code() : "PRESERVED_INSTALL_RECOVERY_REQUIRED",
                failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(),
                "Keep all originals, admissions and transactions; do not retry Apply blindly or close automatically. Inspect exact pins and explicitly RESUME/RESTORE only while unloaded. No automatic restart.");
        return new PreservedInstallResult(State.RECOVERY_REQUIRED, Optional.ofNullable(tx), Optional.ofNullable(pin), Optional.empty(), Optional.of(error));
    }
}
