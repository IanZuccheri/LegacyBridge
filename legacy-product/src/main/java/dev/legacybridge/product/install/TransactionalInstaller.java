package dev.legacybridge.product.install;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationLimits;
import dev.legacybridge.converter.service.EngineError;
import dev.legacybridge.workflow.BridgeWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import static dev.legacybridge.product.install.TransactionResult.EntryState;
import static dev.legacybridge.product.install.TransactionResult.Status;
import static dev.legacybridge.product.install.TransactionFiles.require;

/**
 * At most four independent generated mods, with explicit recovery and managed, unloaded updates.
 * Every JAR publication is add-only; a batch is NOT one atomic filesystem operation.
 * Run off the game thread. Once started this API is non-cancellable; never interrupt it to close UI.
 * No discovery hook, automatic restart, classloading, universal dependency solver or world migration.
 */
public final class TransactionalInstaller {
    enum Boundary { PAYLOAD_COPY, BACKUP_COPY, JOURNAL_WRITE, PREPARED, BEFORE_RETIRE, RETIRED,
        BEFORE_PUBLISH, PUBLISHED, BEFORE_COMMIT, BEFORE_RESTORE_REMOVE, RESTORE_REMOVED, RESTORE_OLD }
    @FunctionalInterface interface Hook { void reached(Boundary boundary, int index) throws IOException; }
    private final Hook hook;
    public TransactionalInstaller() { this((boundary, index) -> { }); }
    TransactionalInstaller(Hook hook) { this.hook = java.util.Objects.requireNonNull(hook); }

    public TransactionResult apply(Path gameDir, List<InstallSelection> selections, InstallSession session) throws InstallException {
        BridgeWorkspace workspace = workspace(gameDir, session, true);
        if (selections == null || selections.isEmpty() || selections.size() > 4) throw outside("BATCH_LIMIT", "Select 1-4 independent converted mods");
        List<InstallSelection> inputs = List.copyOf(selections);
        Path transaction = null; TransactionJournal.Plan plan = null;
        try (var scope = control().enter(); var lock = InstallationLock.acquire(workspace.transactions())) {
            workspace.revalidate(); TransactionFiles.budget(workspace.transactions());
            transaction = workspace.transactions().resolve(UUID.randomUUID().toString()); Files.createDirectory(transaction);
            TransactionFiles.directory(transaction);
            List<TransactionJournal.Entry> entries = new ArrayList<>(); Set<String> ids = new HashSet<>(); Set<Path> replacements = new HashSet<>();
            long reserved = 0;
            for (InstallSelection selection : inputs) {
                int index = entries.size(); TransactionFiles.digest(selection.originalSha256()); TransactionFiles.digest(selection.resultSha256());
                Path original = TransactionFiles.descendant(selection.original(), workspace.input());
                Path staged = TransactionFiles.descendant(selection.staged(), workspace.staging());
                require(!Files.isSameFile(original, staged), "Staged result aliases its original");
                require(AddOnlyInstaller.hash(original).equals(selection.originalSha256()) && AddOnlyInstaller.hash(staged).equals(selection.resultSha256()), "Source/staged fingerprints changed since the session conversion");
                reserved += Files.size(staged); require(reserved <= (256L << 20), "Batch source payload limit exceeded");
                require(Files.getFileStore(transaction).getUsableSpace() >= Files.size(staged) + (1L << 20), "Insufficient space for complete private payload");
                Path payload = transaction.resolve("payload-" + index + ".jar");
                TransactionFiles.copy(staged, payload, hook, Boundary.PAYLOAD_COPY, index);
                var candidate = StagedArtifactValidator.validate(payload, selection.resultSha256());
                require(ids.add(candidate.modId()), "Duplicate incoming batch ID"); guardUnloaded(session, candidate.modId(), List.of());
                TransactionJournal.Previous previous = null;
                if (selection.replaceTransaction().isPresent()) {
                    var managed = TransactionJournal.managed(workspace, selection.replaceTransaction().orElseThrow(), candidate.modId());
                    require(replacements.add(managed.installed()), "Duplicate replacement artifact");
                    guardUnloaded(session, candidate.modId(), List.of(managed.installed()));
                    ownedIdentity(managed.installed(), managed.identity());
                    var old = StagedArtifactValidator.validate(managed.installed(), managed.sha256());
                    require(old.modId().equals(candidate.modId()), "Replacement identity differs");
                    require(!managed.sha256().equals(selection.resultSha256())
                            && dev.legacybridge.converter.policy.NumericVersionRange.contains("(" + old.modVersion() + ",)", candidate.modVersion()),
                            "MANUAL: replacement must be a strictly newer numeric version; same-version, downgrade and unknown ordering are refused");
                    reserved += Files.size(managed.installed()); require(reserved <= (512L << 20), "Batch payload/backup limit exceeded");
                    require(Files.getFileStore(transaction).getUsableSpace() >= Files.size(managed.installed()) + (1L << 20), "Insufficient space for independent backup");
                    Path backup = transaction.resolve("backup-" + index + ".jar");
                    TransactionFiles.copy(managed.installed(), backup, hook, Boundary.BACKUP_COPY, index);
                    require(StagedArtifactValidator.validate(backup, managed.sha256()).equals(old), "Independent backup failed validation");
                    previous = new TransactionJournal.Previous(managed.transactionId(), managed.schema(), managed.index(), managed.sha256(), old.modVersion());
                }
                entries.add(new TransactionJournal.Entry(index, candidate.modId(), candidate.modVersion(), TransactionFiles.relative(original, workspace.input()),
                        selection.originalSha256(), TransactionFiles.relative(staged, workspace.staging()), selection.resultSha256(), previous));
            }
            plan = new TransactionJournal.Plan(transaction.getFileName().toString(), entries);
            verifyArtifacts(workspace, transaction, plan, true);
            preflight(workspace, transaction, plan, session, false);
            TransactionFiles.publishJson(transaction.resolve(TransactionJournal.PLAN), TransactionJournal.tree(plan), hook);
            hook.reached(Boundary.PREPARED, -1);
            perform(workspace, transaction, plan, session, RecoveryAction.RESUME);
            return observe(workspace, transaction, plan, Optional.empty());
        } catch (Exception failure) {
            if (transaction == null) throw outside("INSTALL_MANUAL", message(failure));
            return failed(workspace, transaction, plan, failure);
        }
    }

    public TransactionResult inspect(Path gameDir, Path transactionDirectory, InstallSession session) throws InstallException {
        BridgeWorkspace workspace = workspace(gameDir, session, false); Path transaction = owned(workspace, transactionDirectory);
        try (var scope = control().enter(); var lock = InstallationLock.acquire(workspace.transactions())) {
            var plan = TransactionJournal.read(transaction); verifyArtifacts(workspace, transaction, plan, false);
            return observe(workspace, transaction, plan, Optional.empty());
        } catch (Exception failure) { return failed(workspace, transaction, null, failure); }
    }

    public TransactionResult recover(Path gameDir, Path transactionDirectory, InstallSession session, RecoveryAction action) throws InstallException {
        BridgeWorkspace workspace = workspace(gameDir, session, true); Path transaction = owned(workspace, transactionDirectory);
        java.util.Objects.requireNonNull(action); TransactionJournal.Plan plan = null;
        try (var scope = control().enter(); var lock = InstallationLock.acquire(workspace.transactions())) {
            plan = TransactionJournal.read(transaction); verifyArtifacts(workspace, transaction, plan, action == RecoveryAction.RESUME);
            var successor = successor(workspace, transaction, plan);
            require(successor.isEmpty(), "MANUAL: resolve the managed successor first: " + successor.map(Path::toString).orElse(""));
            TransactionFiles.cleanupRetainedGuards(transaction, workspace.gameDir(), workspace.root(), workspace.input(), workspace.staging(), workspace.transactions(), workspace.mods(), transaction);
            perform(workspace, transaction, plan, session, action);
            return observe(workspace, transaction, plan, Optional.empty());
        } catch (Exception failure) { return failed(workspace, transaction, plan, failure); }
    }

    /** Bounded read-only listing; malformed/unknown real transaction directories are visible as errors. */
    public List<TransactionResult> list(Path gameDir, InstallSession session) throws InstallException {
        return listTransactions(gameDir, session);
    }

    /** Alias retaining the explicit operation name for non-UI consumers. */
    public List<TransactionResult> listTransactions(Path gameDir, InstallSession session) throws InstallException {
        BridgeWorkspace workspace = workspace(gameDir, session, false); List<TransactionResult> results = new ArrayList<>();
        try (var scope = control().enter(); var lock = InstallationLock.acquire(workspace.transactions());
             var stream = Files.newDirectoryStream(workspace.transactions())) {
            int count = 0;
            for (Path path : stream) {
                require(++count <= 128, "Transaction listing exceeds its bound");
                if (path.getFileName().toString().equals("add-only.lock")) { AddOnlyInstaller.regular(path); continue; }
                if (TransactionFiles.knownTransactionCanary(path, workspace.transactions())) continue;
                require(results.size() < 32, "Transaction directory listing exceeds its bound");
                if (!Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                    results.add(new TransactionResult(path, Status.MANUAL, List.of(), Optional.of(error("UNKNOWN_TRANSACTION_LAYOUT", "Unknown transaction entry; no recovery was attempted")))); continue;
                }
                if (!TransactionFiles.uuid(path.getFileName().toString())) {
                    results.add(new TransactionResult(path, Status.MANUAL, List.of(), Optional.of(error("UNKNOWN_TRANSACTION_LAYOUT", "Directory is not an owned transaction UUID")))); continue;
                }
                if (!TransactionFiles.exists(path.resolve(TransactionJournal.PLAN)) && TransactionFiles.exists(path.resolve("prepared.json"))) {
                    try {
                        InstallResult old = new AddOnlyInstaller().inspect(gameDir, path);
                        var candidate = StagedArtifactValidator.validate(old.installed(), old.sha256());
                        results.add(new TransactionResult(path, Status.MANUAL, List.of(new TransactionResult.EntryResult(0, old.modIds().get(0), candidate.modVersion(),
                                EntryState.INSTALLED, Optional.of(old.installed()), old.sha256(), Optional.empty())),
                                Optional.of(error("LEGACY_V1_RECEIPT", "Legacy V1 publication is visible; use V1 inspection or an explicit managed update, not V2 recovery"))));
                    } catch (Exception invalid) { results.add(new TransactionResult(path, Status.RECOVERY_REQUIRED, List.of(),
                            Optional.of(error(invalid instanceof InstallException typed ? typed.code() : "RECOVERY_REQUIRED", message(invalid))))); }
                } else {
                    try {
                        var plan = TransactionJournal.read(path); verifyArtifacts(workspace, path, plan, false);
                        results.add(observe(workspace, path, plan, Optional.empty()));
                    } catch (Exception invalid) { results.add(failed(workspace, path, null, invalid)); }
                }
            }
        } catch (Exception failure) { throw outside("TRANSACTION_LISTING_REFUSED", message(failure)); }
        return results.stream().sorted(java.util.Comparator.comparing(result -> result.transaction().toString())).toList();
    }

    private void perform(BridgeWorkspace workspace, Path transaction, TransactionJournal.Plan plan, InstallSession session, RecoveryAction action) throws Exception {
        boolean restored = checkpoint(transaction, plan, "RESTORED", -1);
        if (action == RecoveryAction.RESUME) require(!restored, "MANUAL: restored transactions cannot be silently reapplied");
        var snapshot = preflight(workspace, transaction, plan, session, action == RecoveryAction.RESTORE);
        try (var directories = TransactionFiles.pinDirectories(workspace.gameDir(), workspace.root(), workspace.input(), workspace.staging(), workspace.transactions(), workspace.mods(), transaction)) {
        workspace.revalidate(); InstalledModInventory.unchanged(workspace.mods(), snapshot);
        OperationControl.beginPublication();
        for (var entry : plan.entries()) {
            workspace.revalidate(); directories.verify();
            Path installed = workspace.mods().resolve(entry.installedName(plan.transactionId()));
            Path old = entry.previous() == null ? null : workspace.mods().resolve(entry.previousName());
            if (action == RecoveryAction.RESUME) {
                if (old != null && TransactionFiles.exists(old)) {
                    guardUnloaded(session, entry.modId(), List.of(old, installed)); verifyOld(workspace, entry, old);
                    Path retired = transaction.resolve(entry.retiredName());
                    if (!TransactionFiles.exists(retired)) TransactionFiles.link(retired, old);
                    require(Files.isSameFile(old, retired), "Unknown retired artifact identity");
                    hook.reached(Boundary.BEFORE_RETIRE, entry.index());
                    workspace.revalidate(); directories.verify(); verifyOld(workspace, entry, old);
                    require(Files.isSameFile(old, retired), "Retired file identity changed before removal"); Files.delete(old);
                    hook.reached(Boundary.RETIRED, entry.index()); record(transaction, plan, "RETIRED", entry.index());
                }
                if (!TransactionFiles.exists(installed)) {
                    guardUnloaded(session, entry.modId(), List.of(installed));
                    verifyFile(transaction.resolve(entry.payloadName()), entry.sha256(), entry.modId(), entry.version());
                    hook.reached(Boundary.BEFORE_PUBLISH, entry.index());
                    workspace.revalidate(); directories.verify(); verifySource(workspace, entry);
                    verifyFile(transaction.resolve(entry.payloadName()), entry.sha256(), entry.modId(), entry.version());
                    TransactionFiles.link(installed, transaction.resolve(entry.payloadName()));
                    hook.reached(Boundary.PUBLISHED, entry.index());
                }
                verifyNew(transaction, entry, installed); record(transaction, plan, "INSTALLED", entry.index());
            } else {
                guardUnloaded(session, entry.modId(), old == null ? List.of(installed) : List.of(installed, old));
                if (TransactionFiles.exists(installed)) {
                    verifyNew(transaction, entry, installed);
                    hook.reached(Boundary.BEFORE_RESTORE_REMOVE, entry.index());
                    workspace.revalidate(); directories.verify(); verifyNew(transaction, entry, installed); Files.delete(installed);
                    hook.reached(Boundary.RESTORE_REMOVED, entry.index());
                }
                if (old != null && !TransactionFiles.exists(old)) {
                    Path backup = transaction.resolve(entry.backupName()); verifyFile(backup, entry.previous().sha256(), entry.modId(), entry.previous().version());
                    // The backup preserves bytes independently; restoring an old receipt also
                    // requires its still-intact owned identity. Lost/replaced identity is MANUAL.
                    Path source = previousIdentity(workspace, entry);
                    verifyFile(source, entry.previous().sha256(), entry.modId(), entry.previous().version());
                    workspace.revalidate(); directories.verify();
                    TransactionFiles.link(old, source); hook.reached(Boundary.RESTORE_OLD, entry.index());
                }
                if (old != null) verifyOld(workspace, entry, old);
                record(transaction, plan, "ENTRY_RESTORED", entry.index());
            }
        }
        hook.reached(Boundary.BEFORE_COMMIT, -1);
        workspace.revalidate(); directories.verify();
        record(transaction, plan, action == RecoveryAction.RESUME ? "COMMITTED" : "RESTORED", -1);
        }
    }

    private InstalledModInventory.Snapshot preflight(BridgeWorkspace workspace, Path transaction, TransactionJournal.Plan plan,
                                                    InstallSession session, boolean restore) throws Exception {
        verifyArtifacts(workspace, transaction, plan, !restore);
        List<StagedArtifactValidator.ValidatedCandidate> future = new ArrayList<>(); Map<Path, String> excluded = new TreeMap<>();
        Set<String> loaded = new HashSet<>(session.loadedModIds()), removed = new HashSet<>(), updates = new HashSet<>();
        for (var entry : plan.entries()) {
            Path installed = workspace.mods().resolve(entry.installedName(plan.transactionId()));
            Path old = entry.previous() == null ? null : workspace.mods().resolve(entry.previousName());
            if (TransactionFiles.exists(installed)) {
                verifyNew(transaction, entry, installed); excluded.put(installed, entry.sha256());
                if (!restore && old == null || !restore && old != null && !TransactionFiles.exists(old)) loaded.remove(entry.modId());
            }
            if (old != null && TransactionFiles.exists(old)) {
                verifyOld(workspace, entry, old); excluded.put(old, entry.previous().sha256());
            }
            if (old != null) updates.add(entry.modId());
            if (restore || !TransactionFiles.exists(installed) || old != null && TransactionFiles.exists(old))
                guardUnloaded(session, entry.modId(), old == null ? List.of(installed) : List.of(installed, old));
            if (restore) {
                if (old == null) removed.add(entry.modId());
                else future.add(new StagedArtifactValidator.ValidatedCandidate(entry.modId(), entry.previous().version()));
            } else future.add(entry.candidate());
        }
        return InstalledModInventory.readFuture(workspace.mods(), loaded, future, excluded, removed, updates);
    }

    private static void verifyArtifacts(BridgeWorkspace workspace, Path transaction, TransactionJournal.Plan plan, boolean sourceRequired) throws Exception {
        workspace.revalidate(); TransactionFiles.directory(transaction);
        for (var entry : plan.entries()) {
            verifyFile(transaction.resolve(entry.payloadName()), entry.sha256(), entry.modId(), entry.version());
            require(Files.getFileStore(transaction).equals(Files.getFileStore(workspace.mods())), "Same-filesystem publication/recovery required");
            if (sourceRequired) {
                verifySource(workspace, entry);
            }
            if (entry.previous() != null) {
                TransactionJournal.verifyPrevious(workspace, entry);
                verifyFile(transaction.resolve(entry.backupName()), entry.previous().sha256(), entry.modId(), entry.previous().version());
                verifyFile(previousIdentity(workspace, entry), entry.previous().sha256(), entry.modId(), entry.previous().version());
                require(!Files.isSameFile(transaction.resolve(entry.backupName()), previousIdentity(workspace, entry)), "Backup is not independent from the retired artifact");
                Path installed = workspace.mods().resolve(entry.installedName(plan.transactionId()));
                require(!TransactionFiles.exists(installed) || !Files.isSameFile(transaction.resolve(entry.backupName()), installed), "Independent backup aliases new installation");
            }
        }
    }
    private static void verifySource(BridgeWorkspace workspace, TransactionJournal.Entry entry) throws Exception {
        Path original = TransactionFiles.resolveRelative(workspace.input(), entry.original());
        Path staged = TransactionFiles.resolveRelative(workspace.staging(), entry.staged());
        require(AddOnlyInstaller.hash(original).equals(entry.originalSha256()) && AddOnlyInstaller.hash(staged).equals(entry.sha256()), "Recovery source/staged fingerprints changed or disappeared");
    }
    private static void ownedIdentity(Path installed, Path identity) throws Exception {
        AddOnlyInstaller.regular(installed); AddOnlyInstaller.regular(identity);
        require(Files.isSameFile(installed, identity), "MANUAL: managed destination was replaced; matching bytes do not prove ownership");
    }
    private static Path previousIdentity(BridgeWorkspace workspace, TransactionJournal.Entry entry) throws Exception {
        return TransactionJournal.managed(workspace, workspace.transactions().resolve(entry.previous().transactionId()), entry.modId()).identity();
    }
    private static void verifyOld(BridgeWorkspace workspace, TransactionJournal.Entry entry, Path old) throws Exception {
        ownedIdentity(old, previousIdentity(workspace, entry)); verifyFile(old, entry.previous().sha256(), entry.modId(), entry.previous().version());
    }
    private static void verifyNew(Path transaction, TransactionJournal.Entry entry, Path installed) throws Exception {
        ownedIdentity(installed, transaction.resolve(entry.payloadName())); verifyFile(installed, entry.sha256(), entry.modId(), entry.version());
    }
    private static void verifyFile(Path path, String sha, String modId, String version) throws Exception {
        AddOnlyInstaller.regular(path); require(AddOnlyInstaller.hash(path).equals(sha), "Managed artifact hash differs: " + path.getFileName());
        var actual = StagedArtifactValidator.validate(path, sha);
        require(actual.modId().equals(modId) && actual.modVersion().equals(version), "Managed artifact identity/version differs");
    }
    private static void guardUnloaded(InstallSession session, String modId, List<Path> paths) throws Exception {
        require(!session.worldOpen() && !session.loadedModIds().contains(modId), "MANUAL: modifying a loaded mod or an open-world installation is forbidden");
        for (Path path : paths) for (Path loaded : session.loadedModFiles()) {
            require(!path.toAbsolutePath().normalize().equals(loaded.toAbsolutePath().normalize())
                    && (!TransactionFiles.exists(path) || !TransactionFiles.exists(loaded) || !Files.isSameFile(path, loaded)),
                    "MANUAL: destination aliases a loaded mod file");
        }
    }
    private void record(Path transaction, TransactionJournal.Plan plan, String state, int index) throws Exception {
        TransactionFiles.publishJson(checkpointPath(transaction, state, index), TransactionJournal.checkpoint(transaction, plan, state, index), hook);
    }
    private static Path checkpointPath(Path transaction, String state, int index) { return transaction.resolve("checkpoint-" + state.toLowerCase(java.util.Locale.ROOT) + "-" + index + ".json"); }
    private static boolean checkpoint(Path transaction, TransactionJournal.Plan plan, String state, int index) throws Exception {
        return TransactionJournal.checkpointMatches(checkpointPath(transaction, state, index), transaction, plan, state, index);
    }
    private static TransactionResult observe(BridgeWorkspace workspace, Path transaction, TransactionJournal.Plan plan, Optional<EngineError> failure) throws Exception {
        boolean committed = checkpoint(transaction, plan, "COMMITTED", -1), restored = checkpoint(transaction, plan, "RESTORED", -1);
        List<TransactionResult.EntryResult> outcomes = new ArrayList<>(); int installedCount = 0, previousCount = 0, missingOld = 0;
        for (var entry : plan.entries()) {
            Path installed = workspace.mods().resolve(entry.installedName(plan.transactionId()));
            Path old = entry.previous() == null ? null : workspace.mods().resolve(entry.previousName());
            boolean hasNew = TransactionFiles.exists(installed), hasOld = old != null && TransactionFiles.exists(old);
            if (hasNew) { verifyNew(transaction, entry, installed); installedCount++; }
            if (hasOld) { verifyOld(workspace, entry, old); previousCount++; }
            if (old != null && !hasOld) missingOld++;
            EntryState state = hasNew ? EntryState.INSTALLED : old != null && !hasOld ? EntryState.RETIRED : restored ? EntryState.RESTORED : EntryState.PREPARED;
            outcomes.add(new TransactionResult.EntryResult(entry.index(), entry.modId(), entry.version(), state,
                    hasNew ? Optional.of(installed) : Optional.empty(), entry.sha256(), Optional.empty()));
        }
        Status status;
        if (restored && installedCount == 0 && missingOld == 0) status = Status.RESTORED;
        else if (!restored && committed && installedCount == plan.entries().size() && previousCount == 0) status = Status.COMMITTED;
        else if (installedCount == 0 && missingOld == 0 && !committed && !restored) status = Status.PREPARED;
        else status = Status.PARTIAL;
        if (installedCount < plan.entries().size() && !restored) {
            var successor = successor(workspace, transaction, plan);
            if (successor.isPresent()) return new TransactionResult(transaction, Status.MANUAL, outcomes,
                    Optional.of(error("ARTIFACT_SUPERSEDED", "A managed update owns this artifact's pending/current successor. Inspect and resolve " + successor.orElseThrow().getFileName() + " first.")));
        }
        if (status == Status.PARTIAL && failure.isEmpty()) failure = Optional.of(error("RECOVERY_REQUIRED", "Observed files/checkpoints describe an incomplete transaction; explicitly resume or restore"));
        return new TransactionResult(transaction, status, outcomes, failure);
    }

    /** Bounded non-recursive ownership lookup, not an automatic rollback of earlier journals. */
    private static Optional<Path> successor(BridgeWorkspace workspace, Path transaction, TransactionJournal.Plan plan) throws Exception {
        int count = 0, directories = 0;
        try (var paths = Files.newDirectoryStream(workspace.transactions())) {
            for (Path path : paths) {
                require(++count <= 128, "Successor inventory exceeds its bound");
                if (!Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                require(++directories <= 32, "Successor transaction inventory exceeds its bound");
                if (path.equals(transaction) || !TransactionFiles.exists(path.resolve(TransactionJournal.PLAN))) continue;
                TransactionFiles.transaction(path, workspace.transactions());
                var later = TransactionJournal.read(path);
                if (checkpoint(path, later, "RESTORED", -1)) continue;
                for (var replacement : later.entries()) {
                    var previous = replacement.previous();
                    if (previous == null || previous.schema() != 2 || !previous.transactionId().equals(plan.transactionId())) continue;
                    require(previous.index() < plan.entries().size(), "Successor indexes an unknown predecessor entry");
                    var earlier = plan.entries().get(previous.index());
                    require(earlier.modId().equals(replacement.modId()) && earlier.sha256().equals(previous.sha256())
                            && earlier.version().equals(previous.version()), "Successor ownership differs from predecessor");
                    verifyArtifacts(workspace, path, later, false);
                    Path installed = workspace.mods().resolve(replacement.installedName(later.transactionId()));
                    Path retired = path.resolve(replacement.retiredName());
                    if (TransactionFiles.exists(installed)) verifyNew(path, replacement, installed);
                    if (TransactionFiles.exists(retired)) ownedIdentity(retired, previousIdentity(workspace, replacement));
                    return Optional.of(path);
                }
            }
        }
        return Optional.empty();
    }
    private static TransactionResult failed(BridgeWorkspace workspace, Path transaction, TransactionJournal.Plan plan, Exception failure) {
        EngineError error = error("TRANSACTION_REFUSED", message(failure));
        if (plan != null && TransactionFiles.exists(transaction.resolve(TransactionJournal.PLAN))) {
            try { return observe(workspace, transaction, plan, Optional.of(error)); }
            catch (Exception inconclusive) { error = error("RECOVERY_REQUIRED", message(failure) + "; installed state cannot be proven: " + message(inconclusive)); }
        }
        return new TransactionResult(transaction, !TransactionFiles.exists(transaction.resolve(TransactionJournal.PLAN)) ? Status.MANUAL : Status.RECOVERY_REQUIRED, List.of(), Optional.of(error));
    }
    private static OperationControl control() { return new OperationControl(new OperationLimits(64L << 20, 2L << 30, 1L << 30, Duration.ofMinutes(5)), progress -> { }); }
    private static BridgeWorkspace workspace(Path gameDir, InstallSession session, boolean mutating) throws InstallException {
        try {
            java.util.Objects.requireNonNull(session); require(!mutating || !session.worldOpen(), "MANUAL: close the current world before installation/recovery");
            require(session.loadedModIds().containsAll(Set.of("minecraft", "forge", "legacybridge_runtime")), "Pinned loaded runtime baseline is missing");
            BridgeWorkspace workspace = BridgeWorkspace.inspect(gameDir);
            for (Path directory : List.of(workspace.root(), workspace.input(), workspace.staging(), workspace.transactions(), workspace.mods())) TransactionFiles.directory(directory);
            return workspace;
        } catch (Exception failure) { throw outside("INSTALL_MANUAL", message(failure)); }
    }
    private static Path owned(BridgeWorkspace workspace, Path transaction) throws InstallException {
        try { return TransactionFiles.transaction(transaction, workspace.transactions()); }
        catch (Exception failure) { throw outside("UNKNOWN_TRANSACTION", message(failure)); }
    }
    private static String message(Exception failure) { return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(); }
    private static EngineError error(String code, String message) { return new EngineError(code, message, "Keep originals and transaction evidence. No automatic restart/rollback occurs; inspect and explicitly resume or restore only unloaded managed artifacts."); }
    private static InstallException outside(String code, String message) { var error = error(code, message); return new InstallException(error.code(), error.message(), error.remediation(), Optional.empty(), Optional.empty()); }
}
