package dev.legacybridge.product.install;

import dev.legacybridge.converter.conversion.PreservedArtifactInspector;
import dev.legacybridge.converter.mapping.portable.PortableMappingBundle;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationLimits;
import dev.legacybridge.converter.operation.OperationStoppedException;
import dev.legacybridge.converter.operation.OperationProgress;
import dev.legacybridge.converter.operation.OperationStage;
import dev.legacybridge.converter.service.EngineError;
import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.converter.service.PreservedArtifactInspection;
import dev.legacybridge.converter.service.PreservedArtifactRequest;
import dev.legacybridge.workflow.BridgeWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static dev.legacybridge.product.install.TransactionFiles.require;
import static dev.legacybridge.product.install.PreservedAdmissionResult.State;

/**
 * Worker-only synchronous preparation, readback and explicit recovery of one admission record.
 * Shares the installer lock; no queue, CLI, Minecraft classes, mods writes, restart or activation.
 * Never consumes a saved inspection as authority. Every successful call performs fresh real readback.
 */
public final class PreservedAdmissionStore {
    enum Boundary { PAYLOAD_COPY, JOURNAL_WRITE, BOUND, BEFORE_OBSERVATION }
    @FunctionalInterface interface Hook { void reached(Boundary boundary, Path transaction) throws IOException; }
    private final Hook hook;
    public PreservedAdmissionStore() { this((boundary, transaction) -> { }); }
    PreservedAdmissionStore(Hook hook) { this.hook = java.util.Objects.requireNonNull(hook); }

    /** One-shot, bounded preparation handle. Cancellation never confers a completed admission. */
    public final class Preparation {
        private final OperationControl control;
        private final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean();
        private volatile boolean finished;
        private Preparation(java.util.function.Consumer<OperationProgress> progress) {
            control = new OperationControl(new OperationLimits(64L << 20, 128L << 20, 64L << 20, Duration.ofMinutes(2)), progress);
        }
        public boolean cancel() { return !finished && control.requestCancel(); }
        public PreservedAdmissionResult run(Path root, PreservedArtifactRequest request) {
            if (!started.compareAndSet(false, true)) throw new IllegalStateException("Preparation handles are single use");
            try { return prepare(root, request, control); } finally { finished = true; }
        }
    }
    public Preparation preparation(java.util.function.Consumer<OperationProgress> progress) {
        return new Preparation(java.util.Objects.requireNonNull(progress));
    }

    /** Explicitly selected session result only; creates private recovery material, never an installed artifact. */
    public PreservedAdmissionResult prepare(Path gameDir, PreservedArtifactRequest selection) {
        return prepare(gameDir, selection, control());
    }
    private PreservedAdmissionResult prepare(Path gameDir, PreservedArtifactRequest selection, OperationControl control) {
        Path transaction = null; String planSha = null;
        try (var scope = control.enter()) {
            OperationControl.stage(OperationStage.INPUT_SNAPSHOT, "Revalidating selected admission inputs");
            BridgeWorkspace workspace = workspace(gameDir);
            String dataSha = reviewed(selection.mappingData());
            Path original = TransactionFiles.descendant(selection.original(), workspace.input());
            Path staged = TransactionFiles.descendant(selection.candidate(), workspace.staging());
            require(!Files.isSameFile(original, staged), "Selected original and candidate alias");
            fingerprint(original, selection.originalSha256()); fingerprint(staged, selection.candidateSha256());
            try (var lock = InstallationLock.acquire(workspace.transactions())) {
                workspace.revalidate(); TransactionFiles.budget(workspace.transactions());
                transaction = workspace.transactions().resolve(UUID.randomUUID().toString()); Files.createDirectory(transaction);
                TransactionFiles.directory(transaction);
                try (var guards = TransactionFiles.pinDirectories(directories(workspace, transaction))) {
                    Path owned = transaction;
                    require(Files.getFileStore(transaction).getUsableSpace() >= Files.size(staged) + (1L << 20), "Insufficient space for private admission payload");
                    OperationControl.stage(OperationStage.SERIALIZATION, "Copying independent admission payload");
                    TransactionFiles.copy(staged, transaction.resolve(PreservedAdmissionJournal.PAYLOAD),
                            (boundary, index) -> hook.reached(Boundary.PAYLOAD_COPY, owned), TransactionalInstaller.Boundary.PAYLOAD_COPY, 0);
                    var probe = fresh(workspace, transaction, original, staged, selection.originalSha256(), selection.candidateSha256(), selection.mappingData());
                    var identity = probe.identity().orElseThrow();
                    var plan = new PreservedAdmissionJournal.Plan(transaction.getFileName().toString(),
                            TransactionFiles.relative(original, workspace.input()), selection.originalSha256(),
                            TransactionFiles.relative(staged, workspace.staging()), selection.candidateSha256(),
                            identity.modId(), identity.modVersion(), dataSha);
                    guards.verify(); workspace.revalidate();
                    OperationControl.stage(OperationStage.PREPARED, "Binding freshly inspected payload and exact input/data pins");
                    Path planFile = transaction.resolve(PreservedAdmissionJournal.PLAN);
                    TransactionFiles.publishJson(planFile, PreservedAdmissionJournal.tree(plan),
                            (boundary, index) -> hook.reached(Boundary.JOURNAL_WRITE, owned));
                    planSha = AddOnlyInstaller.hash(planFile);
                    hook.reached(Boundary.BOUND, transaction);
                    guards.verify(); verifyBoundInputs(workspace, transaction, plan, planSha, selection.mappingData());
                    recordObservation(workspace, transaction, plan, planSha, selection.mappingData(), guards);
                    // JSON is only recovery material; this barrier linearizes availability of a complete admission.
                    OperationControl.beginPublication();
                    return success(State.RECORDED_NOT_INSTALLABLE, transaction, planSha, probe);
                }
            }
        } catch (Exception failure) { return failure(transaction, planSha, failure); }
    }

    /** No journal writes (the shared cooperative lock file may be created). Missing observation is INCOMPLETE. */
    public PreservedAdmissionResult inspect(Path gameDir, Path transaction, String expectedPlanSha256, MappingData reviewedData) {
        return revisit(gameDir, transaction, expectedPlanSha256, reviewedData, false);
    }

    /** Only completes a missing admission observation after fresh readback. Never repairs/overwrites a payload. */
    public PreservedAdmissionResult resume(Path gameDir, Path transaction, String expectedPlanSha256, MappingData reviewedData) {
        return revisit(gameDir, transaction, expectedPlanSha256, reviewedData, true);
    }

    private PreservedAdmissionResult revisit(Path gameDir, Path requested, String expectedSha, MappingData data, boolean resume) {
        Path transaction = null;
        try (var scope = control().enter()) {
            BridgeWorkspace workspace = workspace(gameDir); TransactionFiles.digest(expectedSha); reviewed(data);
            transaction = TransactionFiles.transaction(requested, workspace.transactions());
            try (var lock = InstallationLock.acquire(workspace.transactions())) {
                fingerprint(transaction.resolve(PreservedAdmissionJournal.PLAN), expectedSha);
                var plan = PreservedAdmissionJournal.read(transaction);
                verifyBoundInputs(workspace, transaction, plan, expectedSha, data);
                // A complete old observation never bypasses this invocation.
                var probe = fresh(workspace, transaction, TransactionFiles.resolveRelative(workspace.input(), plan.original()),
                        TransactionFiles.resolveRelative(workspace.staging(), plan.staged()), plan.originalSha256(), plan.candidateSha256(), data);
                require(probe.identity().orElseThrow().equals(new PreservedArtifactInspection.Identity(plan.modId(), plan.modVersion())), "Admission identity changed");
                boolean checked = PreservedAdmissionJournal.checkpointMatches(transaction, plan, expectedSha);
                if (!resume) {
                    verifyBoundInputs(workspace, transaction, plan, expectedSha, data);
                    return success(checked ? State.RECHECKED_NOT_INSTALLABLE : State.INCOMPLETE, transaction, expectedSha, probe);
                }
                TransactionFiles.cleanupRetainedGuards(transaction, directories(workspace, transaction));
                try (var guards = TransactionFiles.pinDirectories(directories(workspace, transaction))) {
                    recordObservation(workspace, transaction, plan, expectedSha, data, guards);
                    return success(State.RECHECKED_NOT_INSTALLABLE, transaction, expectedSha, probe);
                }
            }
        } catch (Exception failure) { return failure(transaction, expectedSha, failure); }
    }

    // Internal composition point: caller MUST own InstallationLock and an OperationControl scope.
    // No saved observation is a substitute for the real inspector, including during installation.
    static PreservedAdmissionJournal.Plan recheckLocked(BridgeWorkspace workspace, Path transaction, String sha, MappingData data) throws Exception {
        fingerprint(transaction.resolve(PreservedAdmissionJournal.PLAN), sha);
        var plan = PreservedAdmissionJournal.read(transaction);
        verifyBoundInputs(workspace, transaction, plan, sha, data);
        var probe = fresh(workspace, transaction, TransactionFiles.resolveRelative(workspace.input(), plan.original()),
                TransactionFiles.resolveRelative(workspace.staging(), plan.staged()), plan.originalSha256(), plan.candidateSha256(), data);
        require(probe.identity().orElseThrow().equals(new PreservedArtifactInspection.Identity(plan.modId(), plan.modVersion())), "Admission identity changed");
        require(PreservedAdmissionJournal.checkpointMatches(transaction, plan, sha), "Explicitly complete admission before requesting installation");
        verifyBoundInputs(workspace, transaction, plan, sha, data);
        return plan;
    }

    private static PreservedArtifactInspection fresh(BridgeWorkspace workspace, Path transaction, Path original, Path staged,
            String sourceSha, String candidateSha, MappingData data) throws Exception {
        Path payload = TransactionFiles.descendant(transaction.resolve(PreservedAdmissionJournal.PAYLOAD), transaction);
        require(!Files.isSameFile(original, staged) && !Files.isSameFile(original, payload) && !Files.isSameFile(staged, payload),
                "Admission payload must be an independent copy");
        fingerprint(original, sourceSha); fingerprint(staged, candidateSha); fingerprint(payload, candidateSha);
        var inputs = PortableMappingBundle.load(data.path().orElseThrow(), reviewed(data));
        var probe = new PreservedArtifactInspector().inspect(new PreservedArtifactRequest(original, sourceSha, payload, candidateSha, data), inputs);
        if (!probe.validated()) throw new Rejected(probe.blockers().get(0));
        workspace.revalidate(); fingerprint(staged, candidateSha); verifyDataManifest(data);
        return probe;
    }
    private void recordObservation(BridgeWorkspace workspace, Path transaction, PreservedAdmissionJournal.Plan plan,
            String sha, MappingData data, TransactionFiles.DirectoryIdentities guards) throws Exception {
        hook.reached(Boundary.BEFORE_OBSERVATION, transaction);
        guards.verify(); verifyBoundInputs(workspace, transaction, plan, sha, data);
        TransactionFiles.publishJson(transaction.resolve(PreservedAdmissionJournal.CHECKED), PreservedAdmissionJournal.checked(plan, sha),
                (boundary, index) -> hook.reached(Boundary.JOURNAL_WRITE, transaction));
        guards.verify(); verifyBoundInputs(workspace, transaction, plan, sha, data);
        require(PreservedAdmissionJournal.checkpointMatches(transaction, plan, sha), "Missing complete admission observation");
    }
    static void verifyBoundInputs(BridgeWorkspace workspace, Path transaction, PreservedAdmissionJournal.Plan plan,
            String sha, MappingData data) throws Exception {
        workspace.revalidate(); TransactionFiles.directory(transaction);
        fingerprint(transaction.resolve(PreservedAdmissionJournal.PLAN), sha);
        require(PreservedAdmissionJournal.read(transaction).equals(plan), "Admission binding changed");
        require(reviewed(data).equals(plan.mappingManifestSha256()), "Explicit reviewed bundle differs from admission pin");
        verifyDataManifest(data);
        Path original = TransactionFiles.resolveRelative(workspace.input(), plan.original());
        Path staged = TransactionFiles.resolveRelative(workspace.staging(), plan.staged());
        Path payload = TransactionFiles.descendant(transaction.resolve(PreservedAdmissionJournal.PAYLOAD), transaction);
        require(!Files.isSameFile(original, staged) && !Files.isSameFile(original, payload) && !Files.isSameFile(staged, payload), "Admission payload/source/staged aliases");
        fingerprint(original, plan.originalSha256()); fingerprint(staged, plan.candidateSha256()); fingerprint(payload, plan.candidateSha256());
    }
    private static void verifyDataManifest(MappingData data) throws Exception {
        Path directory = data.path().orElseThrow(); TransactionFiles.directory(directory);
        Path manifest = directory.resolve(PortableMappingBundle.MANIFEST);
        require(Files.size(manifest) <= (256 << 10), "Data manifest exceeds admission bound");
        fingerprint(manifest, reviewed(data));
    }
    private static String reviewed(MappingData data) throws Exception {
        require(data != null && data.kind() == MappingData.Kind.PORTABLE_BUNDLE && data.expectedManifestSha256().isPresent(),
                "Admission requires an explicit reviewed local bundle pin, not profile/cache/included-data fallback");
        return data.expectedManifestSha256().orElseThrow();
    }
    private static void fingerprint(Path file, String sha) throws Exception {
        TransactionFiles.digest(sha); require(AddOnlyInstaller.hash(file).equals(sha), "Admission fingerprint changed: " + file.getFileName());
    }
    static BridgeWorkspace workspace(Path gameDir) throws Exception {
        require(gameDir != null && gameDir.isAbsolute() && gameDir.normalize().equals(gameDir.toRealPath()), "Expected canonical explicit game directory");
        var workspace = BridgeWorkspace.inspect(gameDir);
        for (Path path : List.of(workspace.root(), workspace.input(), workspace.staging(), workspace.reports(), workspace.transactions())) TransactionFiles.directory(path);
        if (gameDir.getFileSystem().getSeparator().equals("\\")) {
            // Audited JDK 17.0.12 WindowsPath adds the extended prefix above 247 chars;
            // WindowsFileStore.equals then compares different root spellings (C: vs \\?\C:).
            // Keep the existing V1/V2 link/identity checks intact. Fail before creating guards,
            // rather than reinterpret native volume identity or strand a half-prepared journal.
            String uuid = "00000000-0000-0000-0000-000000000000";
            Path[] paths = directories(workspace, workspace.transactions().resolve(uuid));
            for (int index = 0; index < paths.length; index++)
                require(paths[index].resolve(".legacybridge-guard-" + uuid + "-" + uuid + "-" + index).toString().length() <= 247,
                        "MANUAL: Windows admission guard path exceeds the reviewed 247-character boundary; select a shorter game directory");
        }
        return workspace;
    }
    private static Path[] directories(BridgeWorkspace w, Path transaction) {
        // Fixed canary indexes for this schema; index 4 shares the transactions-area guard convention.
        // Unlike installation, admission never places a canary in mods.
        return new Path[] {w.gameDir(), w.root(), w.input(), w.staging(), w.transactions(), w.reports(), transaction};
    }
    private static OperationControl control() {
        return new OperationControl(new OperationLimits(64L << 20, 128L << 20, 64L << 20, Duration.ofMinutes(2)), progress -> { });
    }
    private static PreservedAdmissionResult success(State state, Path transaction, String sha, PreservedArtifactInspection inspection) {
        return new PreservedAdmissionResult(state, Optional.of(transaction), Optional.of(sha), Optional.of(inspection), Optional.empty());
    }
    private static PreservedAdmissionResult failure(Path transaction, String sha, Exception failure) {
        EngineError error = failure instanceof Rejected rejected ? rejected.error
                : new EngineError(failure instanceof InstallException install ? install.code()
                        : failure instanceof OperationStoppedException ? "ADMISSION_INTERRUPTED" : "ADMISSION_RECOVERY_REQUIRED",
                        failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(),
                        "Keep originals and recovery files. Reselect the exact journal/data pins and inspect; unknown/partial files are never overwritten. No Apply or close is authorized.");
        return new PreservedAdmissionResult(State.RECOVERY_REQUIRED, Optional.ofNullable(transaction), Optional.ofNullable(sha), Optional.empty(), Optional.of(error));
    }
    private static final class Rejected extends IOException {
        final EngineError error;
        Rejected(EngineError error) { super(error.message()); this.error = error; }
    }
}
