package dev.legacybridge.product.controller;

import dev.legacybridge.converter.conversion.ConversionResult;
import dev.legacybridge.converter.operation.OperationProgress;
import dev.legacybridge.converter.operation.OperationStage;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.report.CompatibilityReport;
import dev.legacybridge.converter.report.SourceProfile;
import dev.legacybridge.converter.service.ConversionRequest;
import dev.legacybridge.converter.service.ConversionProfile;
import dev.legacybridge.converter.service.CompositionPreview;
import dev.legacybridge.converter.service.CohortInspectionRequest;
import dev.legacybridge.converter.service.CohortPlanningRequest;
import dev.legacybridge.converter.service.EngineError;
import dev.legacybridge.converter.service.EngineJob;
import dev.legacybridge.converter.service.EngineOutcome;
import dev.legacybridge.converter.service.InventoryResult;
import dev.legacybridge.converter.service.LegacyEngineService;
import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.converter.service.PreservedArtifactRequest;
import dev.legacybridge.workflow.BridgeWorkspace;
import dev.legacybridge.product.history.JobHistory;
import dev.legacybridge.product.history.JobHistoryReader;
import dev.legacybridge.product.history.SavedReport;
import dev.legacybridge.product.history.SavedReportStore;
import dev.legacybridge.product.history.PreservedInstallReceipts;
import dev.legacybridge.product.history.PreservedUpdateReceipts;
import dev.legacybridge.product.install.PreservedUpdateInstaller;
import dev.legacybridge.product.install.PreservedUpdateApproval;
import dev.legacybridge.product.install.PreservedUpdatePreview;
import dev.legacybridge.product.install.PreservedAddOnlyInstaller;
import dev.legacybridge.product.install.PreservedAdmissionStore;
import dev.legacybridge.product.install.PreservedAdmissionResult;
import dev.legacybridge.product.install.PreservedInstallApproval;
import dev.legacybridge.product.install.PreservedInstallResult;
import dev.legacybridge.product.install.InstallException;
import dev.legacybridge.product.install.InstallSelection;
import dev.legacybridge.product.install.InstallSession;
import dev.legacybridge.product.install.RecoveryAction;
import dev.legacybridge.product.install.TransactionResult;
import dev.legacybridge.product.install.TransactionalInstaller;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/**
 * JDK-only, instance-owned presentation controller. Never loads a legacy class, calls the
 * CLI or changes the current loaded-mod set. Explicit transactional installation is separate.
 * All filesystem operations and
 * report serialization run on its one bounded coordinator, not the caller/render thread.
 * One engine worker performs static work; there is at most one executing controller action.
 * At most 32 queued/running/completed per-job summaries are retained, never full engine
 * report/bytecode history. Closing requests cancellation and never waits for worker shutdown.
 *
 * <p>Path checks reject observed aliases but are not an OS sandbox against concurrent
 * filesystem replacement. The controller must receive the launcher's trusted gameDir.</p>
 */
public final class ConversionController implements AutoCloseable {
    public static final int MAX_JOBS = 32;
    public enum Phase { READY, INVENTORY, ANALYSIS, CONVERSION, RESULT, ERROR, CANCELLED }

    /** This is installation evidence only; even COMMITTED does not mean activation. */
    public record InstallView(Optional<UUID> operationId, boolean busy, Optional<TransactionResult> result,
                              Optional<EngineError> error, List<TransactionResult> transactions) {
        public InstallView { transactions = List.copyOf(transactions); }
    }

    public record View(Phase phase, boolean busy, List<InventoryResult.Entry> entries, int selected,
                       boolean inventoryTruncated, String message, Optional<EngineError> error,
                       Optional<Path> artifact, Optional<Path> report, Optional<OperationProgress> progress) {
        public View {
            entries = List.copyOf(entries);
            message = safeText(message);
            Objects.requireNonNull(phase);
            Objects.requireNonNull(error);
            Objects.requireNonNull(artifact);
            Objects.requireNonNull(report);
            Objects.requireNonNull(progress);
            if (selected < -1 || selected >= entries.size()) throw new IllegalArgumentException("Invalid selection");
        }

        public Optional<InventoryResult.Entry> selection() {
            return selected < 0 ? Optional.empty() : Optional.of(entries.get(selected));
        }
    }

    private final Path gameDir;
    private final LegacyEngineService engine;
    private final InstallOperations installer;
    private final BiFunction<Path, BooleanSupplier, JobHistory> historyReader;
    private final SavedReportReader savedReportReader;
    private JobHistory history = JobHistory.notRead();
    private Optional<SavedReport> savedReport = Optional.empty();
    private CompositionInspectionView compositionInspection = CompositionInspectionView.notRequested();
    private CohortInspectionView cohortInspection = CohortInspectionView.notRequested();
    private MappingDataView mappingDataReview = MappingDataView.initial();
    private MappingData reviewedMappingSelection;
    private final ThreadPoolExecutor coordinator;
    private final ThreadPoolExecutor pathWorker;
    private final Object gate = new Object();
    private final AtomicReference<View> state = new AtomicReference<>(new View(Phase.READY, false, List.of(), -1,
            false, "Only simple-content-v1. Originals belong in legacybridge/input, not mods.",
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
    private volatile EngineJob<?> activeJob;
    private boolean closed;
    private boolean running;
    private boolean cancelRequested;
    private boolean cancellable;
    private final Map<UUID, JobRecord> jobRecords = new LinkedHashMap<>();
    private final ArrayDeque<JobRecord> pending = new ArrayDeque<>();
    private UUID selectedJobId;
    private JobRecord activeRecord;
    private Optional<EngineError> globalError = Optional.empty();
    private boolean installing;
    private UUID installOperation;
    private TransactionResult installResult;
    private Optional<EngineError> installError = Optional.empty();
    private List<TransactionResult> transactions = List.of();
    private List<JobRecord> installationRecords = List.of();
    private String installationAction;
    private InstallationShutdownBarrier.Lease installationLease;
    // Null in the public UI constructor. No system property, saved JSON or profile fallback enables this route.
    private Supplier<InstallSession> preservedSession;
    private PreservedOperations preservedOperations = new RealPreservedOperations();
    private PreservedReceiptWriter preservedReceiptWriter = new PreservedInstallReceipts()::write;
    private PreservedInstallView preservedView = PreservedInstallView.initial(false);
    private PreservedReview preservedReview;
    private PreservedInstallReceipts.History preservedHistory = PreservedInstallReceipts.History.notRead();
    private UpdateOperations updateOperations = new RealUpdateOperations();
    private UpdateReceiptWriter updateReceiptWriter = new PreservedUpdateReceipts()::write;
    private PreservedUpdateSelection updateSelection;
    private Optional<PreservedUpdatePreview> updatePreview = Optional.empty();
    private PreservedUpdateReceipts.History updateHistory = PreservedUpdateReceipts.History.notRead();
    private PreservedPreparationView preparationView = PreservedPreparationView.initial(false);
    private PreservedPreparationRequest preparationRequest;
    private PreservedAdmissionStore.Preparation admissionPreparation;
    private final AtomicReference<OperationProgress> preparationProgress = new AtomicReference<>();
    @FunctionalInterface interface PreparationHook { void reached(PreservedPreparationView.State stage) throws Exception; }
    private PreparationHook preparationHook = stage -> { };

    /** Test-only deterministic boundaries; never an alternative conversion implementation. */
    ConversionController(Path root, Supplier<InstallSession> session, PreparationHook hook) {
        this(root); preservedSession = Objects.requireNonNull(session); preparationHook = Objects.requireNonNull(hook);
        preservedView = PreservedInstallView.initial(true);
    }

    public PreservedPreparationView preparation() {
        synchronized (gate) {
            var v = preparationView;
            if (v.state() == PreservedPreparationView.State.DISABLED && preservedSession != null) return PreservedPreparationView.initial(true);
            var progress = activeJob == null ? Optional.ofNullable(preparationProgress.get()) : activeJob.latestProgress();
            return new PreservedPreparationView(v.state(), v.operationId(), v.candidate(), v.admission(), v.reports(),
                    running && v.state().ordinal() >= PreservedPreparationView.State.ANALYZING.ordinal()
                            && v.state().ordinal() <= PreservedPreparationView.State.WRITING_REPORTS.ordinal() ? progress : Optional.empty(), v.error());
        }
    }
    /** Current completed preparation only. This selection still requires a fresh review and separate consent. */
    public Optional<PreservedInstallSelection> preparedSelection(UUID operation) {
        synchronized (gate) {
            if (closed || running || preparationView.state() != PreservedPreparationView.State.READY
                    || preparationView.operationId().filter(operation::equals).isEmpty()) return Optional.empty();
            var result = preparationView.admission().orElseThrow();
            return Optional.of(new PreservedInstallSelection(result.transaction().orElseThrow(), result.planSha256().orElseThrow(),
                    preparationRequest.data(), Optional.empty()));
        }
    }
    public Optional<UUID> preparePreserved(PreservedPreparationRequest request) {
        Objects.requireNonNull(request);
        synchronized (gate) {
            if (preservedSession == null || closed || running || !pending.isEmpty()) return Optional.empty();
            InstallSession session = preservedSnapshot();
            if (session == null) return Optional.empty();
            if (session.worldOpen()) { preservedRefusal("WORLD_OPEN", "Close the current world/connection before preserved preparation."); return Optional.empty(); }
            UUID operation = UUID.randomUUID();
            preparationRequest = request; preservedReview = null; preservedView = PreservedInstallView.initial(true);
            updateSelection = null; updatePreview = Optional.empty();
            preparationProgress.set(null);
            preparationView = new PreservedPreparationView(PreservedPreparationView.State.ANALYZING, Optional.of(operation),
                    Optional.empty(), Optional.empty(), Map.of(), Optional.empty(), Optional.empty());
            boolean accepted = start(Phase.ANALYSIS, "Preparing finite preserved content; no installation or activation.",
                    () -> preparePreservedWorker(request, session));
            if (!accepted) preparationView = new PreservedPreparationView(PreservedPreparationView.State.FAILED,
                    Optional.of(operation), Optional.empty(), Optional.empty(), Map.of(), Optional.empty(), globalError);
            return accepted ? Optional.of(operation) : Optional.empty();
        }
    }

    private void preparationStage(PreservedPreparationView.State stage) throws Exception {
        synchronized (gate) {
            if (stage != PreservedPreparationView.State.WRITING_REPORTS) { checkCancelled(); cancellable = true; }
            var v = preparationView;
            preparationView = new PreservedPreparationView(stage, v.operationId(), v.candidate(), v.admission(), v.reports(), Optional.empty(), Optional.empty());
        }
        preparationHook.reached(stage);
    }
    private void preparationReport(BridgeWorkspace workspace, Path directory, String name, Object content) throws IOException {
        revalidateOperation(workspace, directory);
        Path path = directory.resolve(name + ".json"); var writer = new CanonicalReportWriter();
        if (!(content instanceof Map<?, ?> values)) throw new IOException("Report is not a JSON object");
        Map<String, Object> tree = new LinkedHashMap<>();
        for (var entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new IOException("Report key is not text");
            tree.put(key, entry.getValue());
        }
        writer.writeJsonAtomically(path, writer.toJsonTree(tree), false, gameDir);
        synchronized (gate) {
            var v = preparationView; var reports = new LinkedHashMap<>(v.reports()); reports.put(name, path);
            preparationView = new PreservedPreparationView(v.state(), v.operationId(), v.candidate(), v.admission(), reports, Optional.empty(), v.error());
        }
    }
    private static void preparationInput(Path file, Path area, InstallSession session) throws Exception {
        if (!file.startsWith(area) || file.equals(area) || !file.equals(file.toRealPath())
                || Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw failure("UNSAFE_PREPARATION_INPUT", "Selected file is outside its canonical workspace area.");
        for (Path parent = file.getParent(); parent != null && parent.startsWith(area); parent = parent.getParent())
            if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || !parent.equals(parent.toRealPath()))
                throw failure("UNSAFE_PREPARATION_INPUT", "Selected file has an unsafe parent.");
        for (Path loaded : session.loadedModFiles()) if (file.equals(loaded) || Files.isSameFile(file, loaded))
            throw failure("LOADED_PREPARATION_INPUT", "Original/candidate aliases an actually loaded mod; close/reconfigure the instance first.");
    }
    private void preparePreservedWorker(PreservedPreparationRequest request, InstallSession session) throws Exception {
        BridgeWorkspace workspace = null; Path reports = null, staging = null;
        EngineError problem = null; PreservedPreparationView.State outcome = PreservedPreparationView.State.FAILED;
        try {
            workspace = workspace(); preparationInput(request.original(), workspace.input(), session);
            if (request.candidate().isPresent()) preparationInput(request.candidate().orElseThrow().path(), workspace.staging(), session);
            reports = operationDirectory(workspace, workspace.reports());
            preparationStage(PreservedPreparationView.State.ANALYZING);
            var analysis = await(() -> engine.analyze(request.original()));
            if (analysis.status() != EngineOutcome.Status.COMPLETED) throw new ControllerFailure(analysis.error().orElseThrow());
            if (!analysis.value().orElseThrow().input().sha256().equals(request.originalSha256()))
                throw failure("INPUT_CHANGED", "Selected original no longer matches its explicit SHA-256.");
            // Reuse the existing schema serializer, never execute mod classes to build a report.
            Path analysisPath = reports.resolve("analysis.json"); revalidateOperation(workspace, reports);
            new CanonicalReportWriter().writeAtomically(analysisPath, analysis.value().orElseThrow(), false, gameDir);
            synchronized (gate) {
                var v = preparationView;
                preparationView = new PreservedPreparationView(v.state(), v.operationId(), v.candidate(), v.admission(), Map.of("analysis", analysisPath), Optional.empty(), Optional.empty());
            }
            checkCancelled();
            PreservedPreparationRequest.Candidate candidate;
            if (request.candidate().isPresent()) candidate = request.candidate().orElseThrow();
            else {
                preparationStage(PreservedPreparationView.State.CONVERTING);
                staging = operationDirectory(workspace, workspace.staging()); Path target = staging.resolve("converted.jar");
                var converted = await(() -> engine.convert(new ConversionRequest(request.original(), target, request.data(),
                        Optional.of(request.originalSha256()), ConversionProfile.LOGIC_PRESERVING_V1)));
                if (converted.value().filter(ConversionResult::converted).isPresent()) {
                    Object output = converted.value().orElseThrow().report().get("output");
                    if (!(output instanceof Map<?, ?> info) || !(info.get("sha256") instanceof String hash))
                        throw failure("OUTPUT_HASH_MISSING", "Engine did not supply the completed candidate fingerprint.");
                    candidate = new PreservedPreparationRequest.Candidate(target, hash);
                    synchronized (gate) { var v = preparationView;
                        preparationView = new PreservedPreparationView(v.state(), v.operationId(), Optional.of(candidate), v.admission(), v.reports(), Optional.empty(), Optional.empty()); }
                } else candidate = null;
                if (converted.value().isPresent()) {
                    var report = converted.value().orElseThrow().report(); preparationReport(workspace, reports, "conversion", report);
                    Object log = report.get("transformationLog");
                    if (log != null) preparationReport(workspace, reports, "transformation-log", log);
                }
                if (candidate == null) throw new ControllerFailure(converted.error().orElseThrow());
            }
            synchronized (gate) { var v = preparationView;
                preparationView = new PreservedPreparationView(v.state(), v.operationId(), Optional.of(candidate), v.admission(), v.reports(), Optional.empty(), Optional.empty()); }
            preparationInput(candidate.path(), workspace.staging(), session);
            var selected = new PreservedArtifactRequest(request.original(), request.originalSha256(), candidate.path(), candidate.sha256(), request.data());
            preparationStage(PreservedPreparationView.State.INSPECTING);
            var inspection = await(() -> engine.inspectPreservedArtifact(selected));
            if (inspection.value().isPresent()) preparationReport(workspace, reports, "inspection", inspection.value().orElseThrow().toTree());
            if (inspection.status() != EngineOutcome.Status.COMPLETED) throw new ControllerFailure(inspection.error().orElseThrow());
            preparationStage(PreservedPreparationView.State.ADMITTING);
            synchronized (gate) {
                checkCancelled();
                InstallSession current = preservedSnapshot();
                if (current == null) throw new ControllerFailure(globalError.orElseThrow());
                if (!session.equals(current)) throw failure("SESSION_CHANGED", "Host session changed before admission preparation.");
                admissionPreparation = new PreservedAdmissionStore().preparation(preparationProgress::set); cancellable = true;
            }
            var admitted = admissionPreparation.run(gameDir, selected);
            synchronized (gate) {
                admissionPreparation = null; cancellable = false;
                var v = preparationView;
                preparationView = new PreservedPreparationView(v.state(), v.operationId(), v.candidate(), Optional.of(admitted), v.reports(), Optional.empty(), admitted.error());
            }
            if (admitted.error().isPresent()) throw new ControllerFailure(admitted.error().orElseThrow());
            outcome = PreservedPreparationView.State.READY;
        } catch (Exception failure) {
            problem = failure instanceof ControllerFailure controllerFailure ? controllerFailure.error
                    : new EngineError("PREPARATION_FAILED", safeText(String.valueOf(failure.getMessage())),
                            "Inspect the retained reports and private recovery files; nothing was installed. Do not delete unknown files.");
            synchronized (gate) {
                outcome = cancelRequested || closed || problem.code().equals("CANCELLED") ? PreservedPreparationView.State.CANCELLED
                        : blocked(problem.code()) ? PreservedPreparationView.State.BLOCKED : PreservedPreparationView.State.FAILED;
            }
        } finally {
            synchronized (gate) { admissionPreparation = null; cancellable = false; }
            try {
                if (staging != null) cleanEmptyDirectory(staging); // Only an empty directory; completed candidates are retained.
                if (reports != null) {
                    preparationStage(PreservedPreparationView.State.WRITING_REPORTS);
                    var v = preparation(); Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("schemaVersion", 1); summary.put("reportType", "LEGACY_PRESERVED_PREPARATION");
                    summary.put("operationId", v.operationId().orElseThrow().toString()); summary.put("state", outcome.name());
                    summary.put("profile", "logic-preserving-v1"); summary.put("original", request.original().toString());
                    summary.put("originalSha256", request.originalSha256()); summary.put("dataManifestSha256", request.data().expectedManifestSha256().orElseThrow());
                    summary.put("candidate", v.candidate().map(c -> c.path().toString()).orElse(null));
                    summary.put("candidateSha256", v.candidate().map(PreservedPreparationRequest.Candidate::sha256).orElse(null));
                    summary.put("admission", v.admission().flatMap(a -> a.transaction()).map(Path::toString).orElse(null));
                    summary.put("admissionPlanSha256", v.admission().flatMap(a -> a.planSha256()).orElse(null));
                    summary.put("reports", v.reports().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
                    summary.put("error", problem == null ? null : Map.of("code", problem.code(), "message", problem.message(), "remediation", problem.remediation()));
                    summary.put("installationAllowed", false); summary.put("activation", "NOT_OBSERVED"); summary.put("runtimeValidation", "NOT_RUN");
                    preparationReport(workspace, reports, "preparation", summary);
                }
            } catch (Exception failure) {
                problem = new EngineError("PREPARATION_REPORT_FAILED", safeText((problem == null ? "" : problem.code() + ": ") + failure.getMessage()),
                        "Complete candidates/admission recovery files may remain; no ready selection was granted. Inspect files and retry explicitly.");
                outcome = PreservedPreparationView.State.FAILED;
            }
            synchronized (gate) {
                var v = preparationView;
                preparationView = new PreservedPreparationView(outcome, v.operationId(), v.candidate(), v.admission(), v.reports(), Optional.empty(), Optional.ofNullable(problem));
                terminal(outcome == PreservedPreparationView.State.READY ? Phase.RESULT : outcome == PreservedPreparationView.State.CANCELLED ? Phase.CANCELLED : Phase.ERROR,
                        "Preserved preparation: " + outcome + ". No installation, activation or runtime validation.", Optional.ofNullable(problem),
                        v.candidate().map(PreservedPreparationRequest.Candidate::path), v.reports().containsKey("preparation") ? Optional.of(v.reports().get("preparation")) : Optional.empty());
            }
        }
    }

    /**
     * Explicit developer integration, not the native/public Convert/Apply path. The trusted provider
     * must return a thread-safe, nonblocking current loader/world snapshot, never a cached receipt.
     * Its host must exclude world/connect/exit actions while isInstalling() is true. No Forge calls here.
     */
    public static ConversionController forPreservedDevelopment(Path gameDir, Supplier<InstallSession> currentSession) {
        return new ConversionController(gameDir, currentSession, new RealPreservedOperations(), new PreservedInstallReceipts()::write);
    }

    /** Test barriers wrap the real operations; production never supplies alternate validators. */
    ConversionController(Path gameDir, Supplier<InstallSession> currentSession, PreservedOperations operations,
                         PreservedReceiptWriter receiptWriter) {
        this(gameDir);
        preservedSession = Objects.requireNonNull(currentSession);
        preservedOperations = Objects.requireNonNull(operations);
        preservedReceiptWriter = Objects.requireNonNull(receiptWriter);
        preservedView = PreservedInstallView.initial(true);
    }

    public ConversionController(Path gameDir) {
        this(gameDir, new InstallOperations() {
            private final TransactionalInstaller delegate = new TransactionalInstaller();
            public TransactionResult apply(Path root, List<InstallSelection> selected, InstallSession session) throws InstallException {
                return delegate.apply(root, selected, session);
            }
            public List<TransactionResult> list(Path root, InstallSession session) throws InstallException { return delegate.list(root, session); }
            public TransactionResult recover(Path root, Path transaction, InstallSession session, RecoveryAction action) throws InstallException {
                return delegate.recover(root, transaction, session, action);
            }
        });
    }

    ConversionController(Path gameDir, InstallOperations installer) {
        this(gameDir, installer, new JobHistoryReader()::read);
    }

    /** Package-local reader seam for deterministic cancellation/close tests, not platform discovery. */
    ConversionController(Path gameDir, InstallOperations installer, BiFunction<Path, BooleanSupplier, JobHistory> historyReader) {
        this(gameDir, installer, historyReader, new SavedReportStore()::read);
    }

    @FunctionalInterface
    interface SavedReportReader {
        EngineOutcome<SavedReport> read(Path root, JobHistory.Receipt receipt, SavedReport.Kind kind, BooleanSupplier cancelled);
    }

    /** Same coordinator; the package-local seam only controls test barriers, not public authority. */
    ConversionController(Path gameDir, InstallOperations installer, BiFunction<Path, BooleanSupplier, JobHistory> historyReader,
                         SavedReportReader savedReportReader) {
        Objects.requireNonNull(gameDir, "gameDir");
        if (!gameDir.isAbsolute()) throw new IllegalArgumentException("gameDir must be explicitly absolute");
        this.gameDir = gameDir.normalize();
        this.installer = Objects.requireNonNull(installer);
        this.historyReader = Objects.requireNonNull(historyReader);
        this.savedReportReader = Objects.requireNonNull(savedReportReader);
        engine = new LegacyEngineService();
        coordinator = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
            Thread thread = new Thread(task, "legacybridge-controller");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        pathWorker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4), task -> {
            Thread thread = new Thread(task, "legacybridge-path-action");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** Nonblocking immutable snapshot. Poll from the client tick; never join a job there. */
    public View snapshot() {
        synchronized (gate) {
        View value = state.get();
        EngineJob<?> job = activeJob;
        return new View(value.phase(), value.busy(), value.entries(), value.selected(), value.inventoryTruncated(),
                value.message(), value.error(), value.artifact(), value.report(),
                job == null ? value.progress() : job.latestProgress());
        }
    }

    /** Allows a UI owner to reject reopening until this closed coordinator has released its job. */
    public boolean isStopped() { return coordinator.isTerminated() && pathWorker.isTerminated(); }

    public static InventoryKind classify(InventoryResult.Entry entry) {
        if (entry.error().isPresent() || entry.fingerprint().isEmpty()) return InventoryKind.INVALID;
        if (entry.duplicateModId()) return InventoryKind.DUPLICATE;
        if (entry.alreadyConverted()) return InventoryKind.ALREADY_CONVERTED;
        return entry.sourceProfile() == SourceProfile.FORGE_1_16_5_CANDIDATE
                ? InventoryKind.CANDIDATE : InventoryKind.INCOMPATIBLE;
    }

    public List<JobView> jobs() {
        synchronized (gate) { return jobRecords.values().stream().map(this::jobView).toList(); }
    }

    /** No I/O: history is separate from actionable jobs and never rehydrates Apply authority. */
    public JobHistory history() { synchronized (gate) { return history; } }

    public Optional<SavedReport> savedReport() { synchronized (gate) { return savedReport; } }

    /** Read-only display; the ID must belong to the current complete history, never the actionable queue. */
    public boolean readSavedReport(UUID jobId, SavedReport.Kind kind) {
        Objects.requireNonNull(kind);
        synchronized (gate) {
            if (closed || running || !pending.isEmpty() || history.status() != JobHistory.Status.COMPLETE) return false;
            var receipt = history.jobs().stream().filter(job -> job.jobId().equals(jobId))
                    .flatMap(job -> job.highestRecorded().stream()).findFirst();
            if (receipt.isEmpty()) return false;
            savedReport = Optional.empty();
            return start(Phase.ANALYSIS, "Reading a saved report; hashes are not proof of authenticity or activation.", () -> {
                var result = savedReportReader.read(gameDir, receipt.orElseThrow(), kind, () -> {
                    synchronized (gate) { return closed || cancelRequested; }
                });
                synchronized (gate) {
                    checkCancelled();
                    savedReport = result.value();
                    cancellable = false;
                    terminal(result.value().isPresent() ? Phase.RESULT : Phase.ERROR,
                            "Saved report read finished; installation authority is unchanged.", result.error(), Optional.empty(), Optional.empty());
                }
            });
        }
    }

    public CompositionInspectionView compositionInspection() { synchronized (gate) { return compositionInspection; } }

    public CohortInspectionView cohortInspection() { synchronized (gate) { return cohortInspection; } }

    public MappingDataView mappingDataReview() { synchronized (gate) { return mappingDataReview; } }

    /** Explicit read-only local import review. Heavy work stays on the same coordinator/engine worker. */
    public boolean reviewMappingData(String explicitBundleDirectory) {
        Objects.requireNonNull(explicitBundleDirectory);
        synchronized (gate) {
            if (closed || running || !pending.isEmpty()) return false;
            mappingDataReview = new MappingDataView(MappingDataView.Status.RUNNING, Optional.empty(), Optional.empty());
            return start(Phase.ANALYSIS, "Reviewing local mapping data; no mod compatibility or installation approval.", () -> {
                try {
                    if (explicitBundleDirectory.isBlank() || explicitBundleDirectory.length() > 4096)
                        throw failure("INVALID_MAPPING_PATH", "Select an explicit prepared local bundle to review; simple and exact-cohort data are already included.");
                    Path path = Path.of(explicitBundleDirectory);
                    if (!path.isAbsolute()) throw failure("INVALID_MAPPING_PATH", "Select an absolute local bundle directory.");
                    var outcome = await(() -> engine.inspectMappingData(MappingData.bundle(path)));
                    synchronized (gate) {
                        checkCancelled(); cancellable = false;
                        mappingDataReview = new MappingDataView(outcome.value().isPresent() ? MappingDataView.Status.REVIEWED
                                : outcome.status() == EngineOutcome.Status.CANCELLED ? MappingDataView.Status.CANCELLED : MappingDataView.Status.FAILED,
                                outcome.value(), outcome.error());
                        outcome.value().ifPresent(review -> reviewedMappingSelection = review.selection());
                    }
                    if (outcome.value().isEmpty()) finish(outcome);
                    else terminal(Phase.RESULT, "Local data reviewed and hash pinned for this session. Not mod compatibility or installation approval.",
                            Optional.empty(), Optional.empty(), Optional.empty());
                } catch (Exception failure) {
                    synchronized (gate) { mappingDataReview = new MappingDataView(cancelRequested || closed ? MappingDataView.Status.CANCELLED
                            : MappingDataView.Status.FAILED, Optional.empty(), Optional.of(failure instanceof ControllerFailure typed ? typed.error
                            : new EngineError("DATA_REVIEW_FAILED", "Local data review failed: " + failure.getClass().getSimpleName(),
                            "Check the data path and rights; retry explicitly."))); }
                    throw failure;
                }
            });
        }
    }

    /** A review pins subsequent operations on that path. Every payload is still parsed/hashed afresh. */
    private MappingData selectedMappingData(Path path) {
        synchronized (gate) {
            return Optional.ofNullable(reviewedMappingSelection)
                    .filter(data -> data.path().orElseThrow().equals(path.toAbsolutePath().normalize()))
                    .orElseGet(() -> MappingData.bundle(path));
        }
    }

    /** Frozen rows must belong to this inventory. No fallback to a focused row or inferred dependency. */
    public boolean inspectCohort(List<InventoryResult.Entry> selected, String explicitBundleDirectory) {
        Objects.requireNonNull(selected); Objects.requireNonNull(explicitBundleDirectory);
        if (selected.isEmpty() || selected.size() > CohortInspectionView.MAX_INPUTS || selected.stream().anyMatch(Objects::isNull)) return false;
        var observed = List.copyOf(selected);
        synchronized (gate) {
            if (closed || running || !pending.isEmpty() || observed.stream().distinct().count() != observed.size()
                    || !state.get().entries().containsAll(observed)) return false;
            cohortInspection = CohortInspectionView.review(observed).state(CohortInspectionView.Status.RUNNING, Optional.empty());
            var selection = cohortInspection;
            boolean started = start(Phase.ANALYSIS, "Planning selected cohort privately; no public JAR or installation.", () -> {
                if (observed.stream().anyMatch(e -> classify(e) != InventoryKind.CANDIDATE))
                    throw failure("INELIGIBLE_COHORT_INPUT", "Select unique, valid original Forge 1.16.5 candidates and refresh the inventory.");
                if (explicitBundleDirectory.length() > 4096) throw failure("INVALID_MAPPING_PATH", "Mapping path is too long.");
                MappingData data = MappingData.builtinCohortPreview();
                if (!explicitBundleDirectory.isBlank()) {
                    Path bundle = Path.of(explicitBundleDirectory);
                    if (!bundle.isAbsolute()) throw failure("INVALID_MAPPING_PATH", "Select an absolute local bundle directory.");
                    data = selectedMappingData(bundle);
                }
                BridgeWorkspace workspace = workspace();
                for (var entry : observed) { checkCancelled(); validateObservedInput(entry, workspace); }
                var artifacts = selection.inputs().stream().map(i -> new CohortInspectionRequest.Artifact(i.artifactId(), i.path(), i.sha256().orElseThrow())).toList();
                var request = new CohortPlanningRequest(new CohortInspectionRequest(artifacts, data));
                var outcome = await(() -> engine.planCohort(request));
                if (outcome.value().isEmpty()) { finish(outcome); return; }
                var result = outcome.value().orElseThrow();
                synchronized (gate) { checkCancelled(); cancellable = false; }
                Path directory = operationDirectory(workspace, workspace.reports());
                try {
                    Path report = directory.resolve("cohort-preview.json");
                    var view = CohortInspectionView.from(selection, result, report, outcome.error());
                    Map<String, Object> envelope = new LinkedHashMap<>();
                    envelope.put("schemaVersion", CohortInspectionView.SCHEMA_VERSION); envelope.put("reportType", "PRODUCT_COHORT_PREVIEW");
                    envelope.put("status", view.status().name()); envelope.put("publicConversionAllowed", false); envelope.put("installationAllowed", false);
                    envelope.put("runtimeModId", view.runtimeModId()); envelope.put("runtimeVersion", view.runtimeVersion());
                    envelope.put("remainingGates", view.remainingGates().stream().map(Enum::name).toList());
                    envelope.put("selectedInputs", selection.inputs().stream().map(i -> Map.of("artifactId", i.artifactId(), "path", i.path().toString(),
                            "expectedSha256", i.sha256().orElseThrow(), "observedIdentity", i.observedIdentity())).toList());
                    envelope.put("engineReport", result.toTree());
                    var writer = new CanonicalReportWriter(); String json = writer.toJsonTree(envelope);
                    if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2 * 1024 * 1024)
                        throw failure("COHORT_REPORT_LIMIT", "Cohort report exceeds 2 MiB; no report or JAR published.");
                    revalidateOperation(workspace, directory);
                    writer.writeJsonAtomically(report, json, false, observed.get(0).path());
                    synchronized (gate) { cohortInspection = view; }
                    terminal(result.validated() ? Phase.RESULT : Phase.ERROR, "Cohort proof saved. Public conversion, installation and native qualification remain blocked.",
                            outcome.error(), Optional.empty(), Optional.of(report));
                } finally { cleanEmptyDirectory(directory); }
            });
            if (!started) finishCohortInspection();
            return started;
        }
    }

    private void finishCohortInspection() {
        if (cohortInspection.status() == CohortInspectionView.Status.RUNNING)
            cohortInspection = cohortInspection.state(state.get().phase() == Phase.CANCELLED ? CohortInspectionView.Status.CANCELLED : CohortInspectionView.Status.FAILED,
                    state.get().error());
    }

    /** Explicit advanced inspection, not enqueue/convert/Apply. Only the existing finite engine proof is used. */
    public boolean inspectSelectedComposition(String explicitBundleDirectory) {
        Objects.requireNonNull(explicitBundleDirectory);
        synchronized (gate) {
            if (closed || running || !pending.isEmpty()) return false;
            Optional<InventoryResult.Entry> selected = state.get().selection();
            if (selected.isEmpty()) return false;
            InventoryResult.Entry entry = selected.orElseThrow();
            compositionInspection = new CompositionInspectionView(CompositionInspectionView.Status.RUNNING,
                    Optional.of(entry.path()), entry.fingerprint().map(value -> value.sha256()), List.of(), Optional.empty(), Optional.empty());
            boolean started = start(Phase.ANALYSIS, "Inspecting logic + recipes; no JAR or installation will be produced.", () -> {
                if (classify(entry) != InventoryKind.CANDIDATE)
                    throw failure("INELIGIBLE_COMPOSITION_INPUT", "Select a unique, valid, original Forge 1.16.5 candidate and refresh its inventory.");
                if (explicitBundleDirectory.isBlank()) throw failure("COMPOSITION_MAPPING_DATA_REQUIRED",
                        "Composition inspection requires an explicitly prepared full local bundle. Included data covers simple-content and the exact cohort only; no automatic download.");
                if (explicitBundleDirectory.length() > 4096) throw failure("INVALID_MAPPING_PATH", "Mapping path is too long.");
                Path bundle = Path.of(explicitBundleDirectory);
                if (!bundle.isAbsolute()) throw failure("INVALID_MAPPING_PATH", "Select an absolute local bundle directory.");
                BridgeWorkspace workspace = workspace();
                validateObservedInput(entry, workspace);
                Path prospectiveOutput = workspace.staging().resolve("inspection-" + UUID.randomUUID() + ".jar");
                EngineOutcome<CompositionPreview> outcome = await(() -> engine.previewLogicWithRecipes(new ConversionRequest(
                        entry.path(), prospectiveOutput, selectedMappingData(bundle),
                        Optional.of(entry.fingerprint().orElseThrow().sha256()), ConversionProfile.LOGIC_PRESERVING_V1)));
                if (outcome.value().isEmpty()) { finish(outcome); return; }
                var preview = outcome.value().orElseThrow();
                // Same gate as cancellation/close. Once committed to report publication, Cancel refuses.
                synchronized (gate) { checkCancelled(); cancellable = false; }
                Path directory = operationDirectory(workspace, workspace.reports());
                try {
                    Path report = directory.resolve("composition-preview.json");
                    var view = CompositionInspectionView.from(entry.path(), preview, report, outcome.error());
                    Map<String, Object> envelope = new LinkedHashMap<>();
                    envelope.put("schemaVersion", CompositionInspectionView.SCHEMA_VERSION);
                    envelope.put("reportType", "PRODUCT_COMPOSITION_INSPECTION");
                    envelope.put("status", view.status().name());
                    envelope.put("publicConversionAllowed", view.publicConversionAllowed());
                    envelope.put("installationAllowed", view.installationAllowed());
                    envelope.put("runtimeModId", view.runtimeModId()); envelope.put("runtimeVersion", view.runtimeVersion());
                    envelope.put("remainingGates", view.remainingGates().stream().map(Enum::name).toList());
                    envelope.put("engineReport", preview.report());
                    var writer = new CanonicalReportWriter(); String json = writer.toJsonTree(envelope);
                    if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2 * 1024 * 1024)
                        throw failure("COMPOSITION_REPORT_LIMIT", "Inspection report exceeds the 2 MiB product limit; no report/JAR was published.");
                    revalidateOperation(workspace, directory);
                    writer.writeJsonAtomically(report, json, false, entry.path());
                    synchronized (gate) { compositionInspection = view; }
                    terminal(preview.staticallyAdmitted() ? Phase.RESULT : Phase.ERROR,
                            "Inspection report saved. Public composition conversion and installation remain blocked.",
                            outcome.error(), Optional.empty(), Optional.of(report));
                } finally { cleanEmptyDirectory(directory); }
            });
            if (!started) finishCompositionInspection();
            return started;
        }
    }

    /** Called under gate; never synthesizes an installable JobRecord from a preview. */
    private void finishCompositionInspection() {
        if (compositionInspection.status() != CompositionInspectionView.Status.RUNNING) return;
        var previous = compositionInspection;
        compositionInspection = new CompositionInspectionView(state.get().phase() == Phase.CANCELLED
                ? CompositionInspectionView.Status.CANCELLED : CompositionInspectionView.Status.FAILED,
                previous.input(), previous.inputSha256(), List.of(), Optional.empty(), state.get().error());
    }

    /** Explicit bounded read on the existing coordinator; does not create a workspace or job. */
    public boolean refreshHistory() {
        synchronized (gate) {
            if (closed || running || !pending.isEmpty()) return false;
            return start(Phase.ANALYSIS, "Reading inert job receipts; installation and activation are not inferred.", () -> {
                JobHistory observed = historyReader.apply(gameDir, () -> {
                    synchronized (gate) { return cancelRequested || closed; }
                });
                synchronized (gate) {
                    checkCancelled();
                    if (observed.status() == JobHistory.Status.CANCELLED) throw failure("CANCELLED", "Receipt history read cancelled.");
                    history = observed;
                    // Publication and accepted cancellation are mutually exclusive under gate.
                    cancellable = false;
                    terminal(observed.status() == JobHistory.Status.COMPLETE ? Phase.RESULT : Phase.ERROR,
                            "Receipt history: " + observed.status() + ". This is not proof of installation or activation.",
                            observed.status() == JobHistory.Status.COMPLETE ? Optional.empty() : Optional.of(new EngineError(
                                    "HISTORY_INCOMPLETE", "Some receipts could not be trusted or the bounded scan did not finish.",
                                    "Inspect history diagnostics. Do not infer a latest outcome or approve installation from receipts.")),
                            Optional.empty(), Optional.empty());
                }
            });
        }
    }

    /** Inventory/workspace errors are not errors of a previously selected job. */
    public Optional<EngineError> globalError() { synchronized (gate) { return globalError; } }

    public boolean isInstalling() { synchronized (gate) { return installing; } }

    public InstallView installation() {
        synchronized (gate) {
            return new InstallView(Optional.ofNullable(installOperation), installing,
                    Optional.ofNullable(installResult), installError, transactions);
        }
    }

    public PreservedInstallView preservedInstallation() { synchronized (gate) { return preservedView; } }
    public PreservedInstallReceipts.History preservedHistory() { synchronized (gate) { return preservedHistory; } }
    public PreservedUpdateView preservedUpdate() {
        synchronized (gate) { return new PreservedUpdateView(Optional.ofNullable(updateSelection), updatePreview, preservedView); }
    }
    public PreservedUpdateReceipts.History preservedUpdateHistory() { synchronized (gate) { return updateHistory; } }

    public Optional<UUID> reviewPreservedUpdate(PreservedUpdateSelection selection) {
        Objects.requireNonNull(selection);
        return reviewPreserved(selection.replacement(), selection);
    }

    /** Explicit fresh readback, not consent. Does not produce an actionable simple-content job. */
    public Optional<UUID> reviewPreservedInstall(PreservedInstallSelection selection) {
        return reviewPreserved(selection, null);
    }
    private Optional<UUID> reviewPreserved(PreservedInstallSelection selection, PreservedUpdateSelection update) {
        Objects.requireNonNull(selection);
        synchronized (gate) {
            if (preservedSession == null || closed || running || !pending.isEmpty()) return Optional.empty();
            preservedReview = null; updateSelection = update; updatePreview = Optional.empty();
            InstallSession session = preservedSnapshot();
            if (session == null) return Optional.empty();
            if (!reserveInstallation(session)) {
                preservedRefusal(installError.orElseThrow().code(), installError.orElseThrow().message());
                return Optional.empty();
            }
            UUID reviewId = UUID.randomUUID();
            return startPreserved(selection, update, reviewId, session, true, null, Optional.empty());
        }
    }

    /** One explicit confirmation of this controller's fresh review. A historical UUID is never accepted. */
    public Optional<UUID> confirmPreservedInstall(UUID reviewId) {
        return confirmPreserved(reviewId, false);
    }
    public Optional<UUID> confirmPreservedUpdate(UUID reviewId) {
        return confirmPreserved(reviewId, true);
    }
    private Optional<UUID> confirmPreserved(UUID reviewId, boolean update) {
        Objects.requireNonNull(reviewId);
        synchronized (gate) {
            if (preservedSession == null || closed || running || !pending.isEmpty() || preservedReview == null
                    || !preservedReview.id().equals(reviewId) || (preservedReview.update() != null) != update) return Optional.empty();
            PreservedReview reviewed = preservedReview;
            InstallSession session = preservedSnapshot();
            if (session == null) { preservedReview = null; return Optional.empty(); }
            if (!session.equals(reviewed.session())) {
                preservedReview = null;
                preservedRefusal("SESSION_CHANGED", "The loaded set or world state changed. Request a fresh review.");
                return Optional.empty();
            }
            if (!reserveInstallation(session)) return Optional.empty();
            preservedReview = null; // Consume before scheduling; failures require another explicit review.
            UUID approval = reviewed.selection().recovery().map(PreservedInstallSelection.Recovery::priorApprovalId)
                    .orElseGet(UUID::randomUUID);
            return startPreserved(reviewed.selection(), reviewed.update(), reviewId, session, false, approval, Optional.of(reviewed.admission()));
        }
    }

    public boolean refreshPreservedHistory() {
        synchronized (gate) {
            if (preservedSession == null || closed || running || !pending.isEmpty()) return false;
            return start(Phase.ANALYSIS, "Reading inert preserved-install receipts; no approval or activation is inferred.", () -> {
                var observed = new PreservedInstallReceipts().read(gameDir, () -> {
                    synchronized (gate) { return closed || cancelRequested; }
                });
                synchronized (gate) {
                    checkCancelled(); cancellable = false; preservedHistory = observed;
                    terminal(observed.status() == PreservedInstallReceipts.Status.COMPLETE ? Phase.RESULT : Phase.ERROR,
                            "Preserved history: " + observed.status() + ". No current file or runtime validation.",
                            observed.diagnostic().map(code -> new EngineError(code, "Preserved history is incomplete.",
                                    "Inspect the retained files; no latest result or installation consent is inferred.")), Optional.empty(), Optional.empty());
                }
            });
        }
    }

    public boolean refreshPreservedUpdateHistory() {
        synchronized (gate) {
            if (preservedSession == null || closed || running || !pending.isEmpty()) return false;
            return start(Phase.ANALYSIS, "Reading inert update receipts; no consent or activation is inferred.", () -> {
                var observed = new PreservedUpdateReceipts().read(gameDir, () -> {
                    synchronized (gate) { return closed || cancelRequested; }
                });
                synchronized (gate) {
                    checkCancelled(); cancellable = false; updateHistory = observed;
                    terminal(observed.status() == PreservedInstallReceipts.Status.COMPLETE ? Phase.RESULT : Phase.ERROR,
                            "Preserved update history: " + observed.status() + ". No current file or runtime validation.",
                            observed.diagnostic().map(code -> new EngineError(code, "Update history is incomplete.",
                                    "Inspect retained files; history never restores consent.")), Optional.empty(), Optional.empty());
                }
            });
        }
    }

    private InstallSession preservedSnapshot() {
        try { return Objects.requireNonNull(preservedSession.get()); }
        catch (Exception error) {
            preservedRefusal("SESSION_UNAVAILABLE", "The trusted host could not supply the current loader/world snapshot.");
            return null;
        }
    }
    private void preservedRefusal(String code, String message) {
        EngineError error = new EngineError(code, message, "Return to the title screen and review explicitly; do not reuse saved approval.");
        installError = Optional.of(error); globalError = installError;
        preservedView = new PreservedInstallView(PreservedInstallView.State.BLOCKED, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), installError);
    }

    /** Called with the same reserved shutdown lease and coordinator used by public installation. */
    private Optional<UUID> startPreserved(PreservedInstallSelection selection, PreservedUpdateSelection update, UUID reviewId, InstallSession session,
            boolean review, UUID approvalId, Optional<PreservedAdmissionResult> knownAdmission) {
        UUID operation = installOperation;
        var action = PreservedInstallReceipts.Action.valueOf((review ? "REVIEW_" : "")
                + selection.recovery().map(r -> r.action().name()).orElse("APPLY"));
        preservedView = new PreservedInstallView(review ? PreservedInstallView.State.REVIEWING : PreservedInstallView.State.INSTALLING,
                Optional.of(reviewId), Optional.of(operation), knownAdmission, Optional.empty(), Optional.empty(), Optional.empty());
        boolean accepted = start(review ? Phase.ANALYSIS : Phase.CONVERSION,
                review ? "Rechecking preserved admission on the coordinator; this is not installation consent."
                        : "Applying the explicitly approved preserved operation; next launch only, no automatic restart.", () -> {
            Optional<PreservedAdmissionResult> admission = knownAdmission;
            Optional<PreservedUpdatePreview> preview = Optional.empty();
            Optional<PreservedInstallResult> result = Optional.empty(); Optional<EngineError> error = Optional.empty();
            PreservedInstallView.State outcome = PreservedInstallView.State.FAILED;
            try {
                // Recheck at dispatch, not only at acceptance. The host keeps its world gate until completion.
                if (!session.equals(Objects.requireNonNull(preservedSession.get())) || session.worldOpen())
                    throw failure("SESSION_CHANGED", "Loader/world state changed before worker execution; re-review required.");
                if (review) {
                    var checked = preservedOperations.review(gameDir, selection);
                    admission = Optional.of(checked); error = checked.error();
                    if (error.isEmpty() && checked.state() != PreservedAdmissionResult.State.RECHECKED_NOT_INSTALLABLE)
                        error = Optional.of(new EngineError("ADMISSION_INCOMPLETE", "Fresh complete admission readback is missing.", "Inspect the admission; do not apply."));
                    if (error.isEmpty() && update != null && selection.recovery().isEmpty()) {
                        var observed = updateOperations.preview(gameDir, update, session); preview = Optional.of(observed); error = observed.error();
                        if (error.isEmpty() && !observed.reviewed()) error = Optional.of(new EngineError("UPDATE_REVIEW_INCOMPLETE",
                                "Fresh predecessor/replacement preflight is missing.", "Review both selected pins again; no consent was issued."));
                    }
                    if (error.isEmpty() && selection.recovery().isPresent()) {
                        var recovery = selection.recovery().orElseThrow();
                        var checkedInstall = update == null ? preservedOperations.inspect(gameDir, selection, session)
                                : updateOperations.inspect(gameDir, update, session);
                        result = Optional.of(checkedInstall); error = checkedInstall.error();
                        if (error.isEmpty() && recovery.action() == RecoveryAction.RESUME &&
                                (checkedInstall.state() == PreservedInstallResult.State.RESTORING || checkedInstall.state() == PreservedInstallResult.State.RESTORED))
                            error = Optional.of(new EngineError("RESTORE_ALREADY_STARTED", "RESUME cannot reapply a restoring/restored operation.", "Select RESTORE explicitly."));
                    }
                    outcome = error.isEmpty() ? PreservedInstallView.State.REVIEWED : PreservedInstallView.State.BLOCKED;
                } else {
                    var installed = update == null ? preservedOperations.install(gameDir, selection, approvalId, session)
                            : updateOperations.install(gameDir, update, approvalId, session);
                    result = Optional.of(installed); error = installed.error();
                    outcome = installed.safeToClose() ? installed.state() == PreservedInstallResult.State.COMMITTED
                            ? PreservedInstallView.State.PENDING_RESTART : PreservedInstallView.State.RESTORED : PreservedInstallView.State.FAILED;
                    if (error.isEmpty() && !installed.safeToClose()) error = Optional.of(new EngineError("INSTALL_INCOMPLETE",
                            "A nonterminal installation outcome was returned.", "Inspect the transaction; absence of an installed path does not prove nothing was published."));
                }
            } catch (Exception failure) {
                error = Optional.of(failure instanceof ControllerFailure typed ? typed.error : new EngineError("PRESERVED_OUTCOME_UNKNOWN",
                        "Preserved operation did not return a verified outcome: " + failure.getClass().getSimpleName(),
                        "Inspect retained transactions before retrying; no automatic close or rollback."));
            }
            var completed = new PreservedInstallView(outcome, Optional.of(reviewId), Optional.of(operation), admission, result, Optional.empty(), error);
            try {
                // Shutdown/world exclusion remains held through receipt persistence, even after screen/controller close.
                Path receipt = update == null ? preservedReceiptWriter.write(gameDir, selection, action, approvalId, completed)
                        : updateReceiptWriter.write(gameDir, update, action, approvalId, completed);
                completed = new PreservedInstallView(outcome, completed.reviewId(), completed.operationId(), admission, result, Optional.of(receipt), error);
            } catch (Exception failure) {
                error = Optional.of(new EngineError("PRESERVED_RECEIPT_WRITE_FAILED", "Installation observation retained but its presentation receipt could not be saved.",
                        "Inspect the transaction and report permissions. Do not assume the committed result was undone; re-review explicitly."));
                completed = new PreservedInstallView(PreservedInstallView.State.FAILED, completed.reviewId(), completed.operationId(), admission, result, Optional.empty(), error);
            }
            synchronized (gate) {
                preservedView = completed; installError = error;
                if (review && update != null) updatePreview = preview;
                if (review && completed.state() == PreservedInstallView.State.REVIEWED && !closed)
                    preservedReview = new PreservedReview(reviewId, selection, update, session, admission.orElseThrow());
                terminal(error.isEmpty() ? Phase.RESULT : Phase.ERROR,
                        "Preserved operation: " + completed.state() + ". Activation NOT_OBSERVED; reopen using the launcher after applying.",
                        error, Optional.empty(), completed.receipt());
            }
        });
        if (!accepted) {
            installing = false; installationLease.close(); installationLease = null;
            preservedRefusal("QUEUE_FULL", "The shared coordinator refused the operation.");
            return Optional.empty();
        }
        return Optional.of(operation);
    }

    private record PreservedReview(UUID id, PreservedInstallSelection selection, PreservedUpdateSelection update,
                                   InstallSession session, PreservedAdmissionResult admission) { }
    interface PreservedOperations {
        PreservedAdmissionResult review(Path root, PreservedInstallSelection selection);
        PreservedInstallResult inspect(Path root, PreservedInstallSelection selection, InstallSession session);
        PreservedInstallResult install(Path root, PreservedInstallSelection selection, UUID approval, InstallSession session);
    }
    static final class RealPreservedOperations implements PreservedOperations {
        public PreservedAdmissionResult review(Path root, PreservedInstallSelection s) {
            return new PreservedAdmissionStore().inspect(root, s.admission(), s.admissionSha256(), s.data());
        }
        public PreservedInstallResult inspect(Path root, PreservedInstallSelection s, InstallSession session) {
            var r = s.recovery().orElseThrow();
            return new PreservedAddOnlyInstaller().inspect(root, r.transaction(), r.planSha256(),
                    new PreservedInstallApproval(s.admission(), s.admissionSha256(), r.priorApprovalId()), s.data(), session);
        }
        public PreservedInstallResult install(Path root, PreservedInstallSelection s, UUID approval, InstallSession session) {
            var consent = new PreservedInstallApproval(s.admission(), s.admissionSha256(), approval);
            var installer = new PreservedAddOnlyInstaller();
            if (s.recovery().isEmpty()) return installer.apply(root, consent, s.data(), session);
            var r = s.recovery().orElseThrow();
            return installer.recover(root, r.transaction(), r.planSha256(), consent, s.data(), session, r.action());
        }
    }
    @FunctionalInterface interface PreservedReceiptWriter {
        Path write(Path root, PreservedInstallSelection selection, PreservedInstallReceipts.Action action, UUID approval, PreservedInstallView view) throws IOException;
    }

    ConversionController(Path root, Supplier<InstallSession> session, UpdateOperations operations, UpdateReceiptWriter writer) {
        this(root, session, new RealPreservedOperations(), new PreservedInstallReceipts()::write);
        updateOperations = Objects.requireNonNull(operations); updateReceiptWriter = Objects.requireNonNull(writer);
    }
    interface UpdateOperations {
        PreservedUpdatePreview preview(Path root, PreservedUpdateSelection selection, InstallSession session);
        PreservedInstallResult inspect(Path root, PreservedUpdateSelection selection, InstallSession session);
        PreservedInstallResult install(Path root, PreservedUpdateSelection selection, UUID approval, InstallSession session);
    }
    static final class RealUpdateOperations implements UpdateOperations {
        public PreservedUpdatePreview preview(Path root, PreservedUpdateSelection update, InstallSession session) {
            var s = update.replacement();
            return new PreservedUpdateInstaller().preview(root, update.previousInstallation(), update.previousPlanSha256(),
                    s.admission(), s.admissionSha256(), s.data(), session);
        }
        private PreservedUpdateApproval approval(PreservedUpdateSelection update, UUID nonce) {
            var s = update.replacement();
            return new PreservedUpdateApproval(new PreservedInstallApproval(s.admission(), s.admissionSha256(), nonce),
                    update.previousInstallation(), update.previousPlanSha256());
        }
        public PreservedInstallResult inspect(Path root, PreservedUpdateSelection update, InstallSession session) {
            var s = update.replacement(); var r = s.recovery().orElseThrow();
            return new PreservedUpdateInstaller().inspect(root, r.transaction(), r.planSha256(), approval(update, r.priorApprovalId()), s.data(), session);
        }
        public PreservedInstallResult install(Path root, PreservedUpdateSelection update, UUID nonce, InstallSession session) {
            var s = update.replacement(); var installer = new PreservedUpdateInstaller(); var consent = approval(update, nonce);
            if (s.recovery().isEmpty()) return installer.apply(root, consent, s.data(), session);
            var r = s.recovery().orElseThrow();
            return installer.recover(root, r.transaction(), r.planSha256(), consent, s.data(), session, r.action());
        }
    }
    @FunctionalInterface interface UpdateReceiptWriter {
        Path write(Path root, PreservedUpdateSelection selection, PreservedInstallReceipts.Action action, UUID approval, PreservedInstallView view) throws IOException;
    }

    public Optional<JobView> selectedJob() {
        synchronized (gate) { return Optional.ofNullable(jobRecords.get(selectedJobId)).map(this::jobView); }
    }

    public boolean selectJob(UUID id) {
        synchronized (gate) {
            if (closed || !jobRecords.containsKey(id)) return false;
            selectedJobId = id;
            return true;
        }
    }

    /** Immutable observed rows only. Failures are individual; exactly one engine job runs at a time. */
    public boolean enqueue(List<Integer> indices, String bundle) {
        Objects.requireNonNull(indices); Objects.requireNonNull(bundle);
        synchronized (gate) {
            if (closed || running && activeRecord == null || bundle.length() > 4096 || indices.size() > MAX_JOBS) return false;
            List<Integer> unique = indices.stream().distinct().toList();
            View view = state.get();
            if (unique.isEmpty() || unique.size() + jobRecords.size() > MAX_JOBS
                    || unique.stream().anyMatch(index -> index == null || index < 0 || index >= view.entries().size())) return false;
            for (int index : unique) {
                JobRecord record = new JobRecord(view.entries().get(index), bundle, false);
                jobRecords.put(record.id, record);
                pending.addLast(record);
                selectedJobId = record.id;
            }
            drainNext();
            return true;
        }
    }

    public boolean cancelJob(UUID id) {
        synchronized (gate) {
            JobRecord record = jobRecords.get(id);
            if (record == null) return false;
            if (pending.remove(record)) { record.status = JobState.CANCELLED; record.error = cancellation(); return true; }
            return activeRecord == record && cancel();
        }
    }

    /** Forgets bounded UI summaries only; never deletes reports, staged JARs or transactions. */
    public boolean clearFinished() {
        synchronized (gate) {
            if (closed || installing) return false;
            jobRecords.values().removeIf(record -> record != activeRecord && !pending.contains(record));
            if (!jobRecords.containsKey(selectedJobId)) selectedJobId = jobRecords.keySet().stream().findFirst().orElse(null);
            return true;
        }
    }

    public boolean refresh() {
        return start(Phase.INVENTORY, "Inspecting the input folder without loading mod classes.", () -> {
            BridgeWorkspace workspace = workspace();
            EngineOutcome<InventoryResult> outcome = await(() -> engine.inventory(workspace.input()));
            if (outcome.status() != EngineOutcome.Status.COMPLETED) { finish(outcome); return; }
            InventoryResult inventory = outcome.value().orElseThrow();
            state.set(new View(Phase.READY, true, inventory.entries(), inventory.entries().isEmpty() ? -1 : 0,
                    inventory.truncated(), inventory.truncated() ? "Inventory limit reached; move fewer candidates into input."
                    : "Select one candidate. Analysis is not a promise that it can be converted.",
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        });
    }

    /** Selects only a row actually observed by this controller, never an arbitrary path. */
    public boolean select(int index) {
        synchronized (gate) {
            View value = state.get();
            if (closed || running || index < 0 || index >= value.entries().size()) return false;
            if (index == value.selected()) return true;
            state.set(new View(Phase.READY, false, value.entries(), index, value.inventoryTruncated(),
                    "Selected a different candidate. Analyze or convert it to obtain its own result; previous files remain unchanged.",
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            return true;
        }
    }

    public boolean analyzeSelected() {
        return startSelected(Phase.ANALYSIS, "Analyzing selected input; no code is executed.", (entry, workspace) -> {
            EngineOutcome<CompatibilityReport> outcome = await(() -> engine.analyze(entry.path()));
            if (outcome.status() != EngineOutcome.Status.COMPLETED) { finish(outcome); return; }
            CompatibilityReport report = outcome.value().orElseThrow();
            if (!entry.fingerprint().orElseThrow().sha256().equals(report.input().sha256()))
                throw failure("INPUT_CHANGED", "The selected input changed after inventory. Refresh before retrying.");
            Path reportDirectory = operationDirectory(workspace, workspace.reports());
            Path target = reportDirectory.resolve("analysis.json");
            try {
                revalidateOperation(workspace, reportDirectory);
                new CanonicalReportWriter().writeAtomically(target, report, false, entry.path());
                synchronized (gate) { if (activeRecord != null) activeRecord.compatibilityReport = target; }
            } catch (IOException error) {
                cleanEmptyDirectory(reportDirectory);
                throw failure("REPORT_WRITE_FAILED", "Analysis completed but its report could not be saved: " + error.getMessage());
            }
            terminal(Phase.RESULT, "Analysis saved. " + report.complexity() + "; conversion remains a separate validation.",
                    Optional.empty(), Optional.empty(), Optional.of(target));
        });
    }

    /** Blank selects only the included simple-content contract; explicit bundles never fall back on failure. */
    public boolean convertSelected(String explicitBundleDirectory) {
        String selectedBundle = Objects.requireNonNull(explicitBundleDirectory, "explicitBundleDirectory");
        return startSelected(Phase.CONVERSION, "Converting into staging only; this session's mods cannot change.", (entry, workspace) -> {
            if (selectedBundle.length() > 4096) throw failure("INVALID_MAPPING_PATH", "Mapping path is too long.");
            MappingData mappingData;
            if (selectedBundle.isBlank()) mappingData = MappingData.builtinSimpleContent();
            else {
                Path bundle = Path.of(selectedBundle);
                if (!bundle.isAbsolute()) throw failure("INVALID_MAPPING_PATH", "Select an absolute local bundle directory.");
                mappingData = selectedMappingData(bundle);
            }
            if (entry.duplicateModId()) throw failure("DUPLICATE_MOD_ID", "More than one inventoried input declares this mod ID.");
            if (entry.sourceProfile() != SourceProfile.FORGE_1_16_5_CANDIDATE)
                throw failure("SOURCE_PROFILE_UNCONFIRMED", "Inventory did not identify a Forge 1.16.5 candidate.");
            if (entry.alreadyConverted()) throw failure("ALREADY_CONVERTED", "This input carries converted-product metadata; do not convert it again.");
            // Real analysis precedes conversion; its full report is separate from the transformation log.
            synchronized (gate) { activeRecord.status = JobState.ANALYZING; }
            EngineOutcome<CompatibilityReport> analysis = await(() -> engine.analyze(entry.path()));
            if (analysis.status() != EngineOutcome.Status.COMPLETED) { finish(analysis); return; }
            CompatibilityReport compatibility = analysis.value().orElseThrow();
            if (!entry.fingerprint().orElseThrow().sha256().equals(compatibility.input().sha256()))
                throw failure("INPUT_CHANGED", "The selected input changed after inventory. Refresh before retrying.");
            checkCancelled();
            Path analysisDirectory = operationDirectory(workspace, workspace.reports());
            Path analysisPath = analysisDirectory.resolve("analysis.json");
            try { new CanonicalReportWriter().writeAtomically(analysisPath, compatibility, false, entry.path()); }
            catch (IOException failure) { cleanEmptyDirectory(analysisDirectory); throw failure; }
            synchronized (gate) {
                activeRecord.compatibilityReport = analysisPath;
                activeRecord.status = JobState.ANALYZED;
            }
            checkCancelled();
            synchronized (gate) { activeRecord.status = JobState.CONVERTING; }
            Path staging = operationDirectory(workspace, workspace.staging());
            Path output = staging.resolve("converted.jar");
            Path reports = null;
            boolean published = false;
            try {
                revalidateOperation(workspace, staging);
                EngineOutcome<ConversionResult> outcome = await(() -> engine.convert(new ConversionRequest(entry.path(), output,
                        mappingData, Optional.of(entry.fingerprint().orElseThrow().sha256()))));
                published = outcome.value().map(ConversionResult::converted).orElse(false);
                if (published) {
                    // Preserve the committed path even if a later receipt check fails.
                    terminal(Phase.RESULT, "Validated JAR staged; not installed, activated or game-tested.",
                            Optional.empty(), Optional.of(output), Optional.empty());
                    Object outputInfo = outcome.value().orElseThrow().report().get("output");
                    if (!(outputInfo instanceof Map<?, ?> values) || !(values.get("sha256") instanceof String sha)
                            || !sha.matches("[0-9a-f]{64}")) throw new IllegalStateException("Engine omitted the committed output fingerprint");
                    synchronized (gate) { activeRecord.outputSha256 = sha; }
                }
                if (outcome.value().isPresent()) {
                    reports = operationDirectory(workspace, workspace.reports());
                    Path report = reports.resolve("conversion.json");
                    revalidateOperation(workspace, reports);
                    CanonicalReportWriter writer = new CanonicalReportWriter();
                    writer.writeJsonAtomically(report, writer.toJsonTree(outcome.value().orElseThrow().report()), false, entry.path());
                    synchronized (gate) { activeRecord.conversionReport = report; }
                    Object log = outcome.value().orElseThrow().report().get("transformationLog");
                    if (log != null) {
                        if (!(log instanceof Map<?, ?> logMap)) throw new IOException("Invalid transformation log structure");
                        Path logPath = reports.resolve("transformation-log.json");
                        Map<String, Object> logTree = new LinkedHashMap<>();
                        for (var logEntry : logMap.entrySet()) {
                            if (!(logEntry.getKey() instanceof String key)) throw new IOException("Invalid transformation log key");
                            logTree.put(key, logEntry.getValue());
                        }
                        writer.writeJsonAtomically(logPath, writer.toJsonTree(logTree), false, entry.path());
                        synchronized (gate) { activeRecord.transformationLog = logPath; }
                    }
                    terminal(published ? Phase.RESULT : Phase.ERROR, published
                                    ? "Validated JAR and report staged; not installed, activated or game-tested."
                                    : "Conversion refused. Inspect the report; no converted JAR was published.",
                            outcome.error(), published ? Optional.of(output) : Optional.empty(), Optional.of(report));
                } else finish(outcome);
            } catch (IOException error) {
                terminal(Phase.ERROR, published ? "JAR staged, but its external report could not be saved. Do not mistake this for cancellation."
                                : "Local I/O failed. No converted JAR was published.",
                        Optional.of(new EngineError(published ? "REPORT_WRITE_FAILED_PUBLISHED" : "IO_ERROR",
                                error.getMessage() == null ? "Local I/O failed." : error.getMessage(),
                                "Inspect the reported staging path and filesystem permissions.")),
                        published ? Optional.of(output) : Optional.empty(), Optional.empty());
            } finally {
                if (!published) cleanEmptyDirectory(staging);
                if (reports != null) cleanEmptyDirectory(reports);
            }
        });
    }

    /**
     * Nonblocking presentation hint, with no I/O or cancellation side effects. A publication
     * race can still occur after this snapshot; only cancel() decides whether cancellation won.
     */
    public boolean canCancel() {
        synchronized (gate) {
            if (closed || !running || cancelRequested || !cancellable || !state.get().busy()) return false;
            EngineJob<?> job = activeJob;
            return job == null || job.latestProgress().map(progress -> progress.stage() != OperationStage.PUBLICATION
                    && progress.stage() != OperationStage.COMPLETED).orElse(true);
        }
    }

    /** False means no cancellable operation, or publication/completion has already won. */
    public boolean cancel() {
        synchronized (gate) {
            if (!state.get().busy() || !cancellable) return false;
            if (admissionPreparation != null && !admissionPreparation.cancel()) return false;
            EngineJob<?> job = activeJob;
            if (job != null && !job.cancel()) return false;
            cancelRequested = true;
            return true;
        }
    }

    private boolean startSelected(Phase phase, String message, SelectedAction action) {
        synchronized (gate) {
            View value = state.get();
            if (closed || running) return false;
            Optional<InventoryResult.Entry> selection = value.selection();
            if (selection.isEmpty() || selection.orElseThrow().fingerprint().isEmpty() || selection.orElseThrow().error().isPresent()) {
                terminal(Phase.ERROR, "Select a valid, inventoried input first.", Optional.of(new EngineError("INVALID_SELECTION",
                        "There is no valid observed input selection.", "Refresh the inventory and select a valid candidate.")), Optional.empty(), Optional.empty());
                return false;
            }
            InventoryResult.Entry entry = selection.orElseThrow();
            if (activeRecord == null) {
                if (jobRecords.size() == MAX_JOBS) return false;
                activeRecord = new JobRecord(entry, "", phase == Phase.ANALYSIS);
                activeRecord.status = phase == Phase.ANALYSIS ? JobState.ANALYZING : JobState.CONVERTING;
                jobRecords.put(activeRecord.id, activeRecord);
                selectedJobId = activeRecord.id;
            }
            return start(phase, message, () -> {
                BridgeWorkspace workspace = workspace();
                validateObservedInput(entry, workspace);
                action.run(entry, workspace);
            });
        }
    }

    private boolean start(Phase phase, String message, Action action) {
        synchronized (gate) {
            if (closed || running) return false;
            running = true;
            globalError = Optional.empty();
            cancelRequested = false;
            cancellable = !installing && (activeRecord == null || activeRecord.status != JobState.APPLYING);
            View previous = state.get();
            state.set(new View(phase, true, previous.entries(), previous.selected(), previous.inventoryTruncated(), message,
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            try {
                coordinator.execute(() -> {
                    try {
                        if (!isInstalling() && (activeRecord == null || activeRecord.status != JobState.APPLYING)) checkCancelled();
                        action.run();
                    } catch (ControllerFailure failure) {
                        terminal("CANCELLED".equals(failure.error.code()) ? Phase.CANCELLED : Phase.ERROR,
                                failure.error.message(), Optional.of(failure.error), Optional.empty(), Optional.empty());
                    } catch (Exception failure) {
                        View existing = state.get();
                        boolean installationFailure;
                        synchronized (gate) { installationFailure = installing || activeRecord != null && (activeRecord.status == JobState.APPLYING
                                || activeRecord.status == JobState.PENDING_RESTART); }
                        if (installationFailure) {
                            EngineError error = new EngineError("INSTALL_OUTCOME_UNKNOWN", safeText(String.valueOf(failure.getMessage())),
                                    "Inspect transactions and mods before any retry. Do not assume installation failed or delete any file.");
                            synchronized (gate) {
                                terminal(Phase.ERROR, "Unexpected installation failure; inspect the transaction before taking further action.",
                                        Optional.of(error), Optional.empty(), Optional.empty());
                                installError = Optional.of(error);
                                for (JobRecord record : installationRecords) record.error = error;
                                if (activeRecord != null) {
                                    activeRecord.error = error;
                                    activeRecord.status = activeRecord.installed == null ? JobState.FAILED : JobState.PENDING_RESTART;
                                }
                            }
                        } else terminal(Phase.ERROR, existing.artifact().isPresent()
                                ? "JAR already staged; later report/cleanup failed. No installation was attempted."
                                : "Controller operation failed; no installation was attempted.", Optional.of(new EngineError(
                                existing.artifact().isPresent() ? "POST_PUBLICATION_FAILURE"
                                : failure instanceof IOException ? "IO_ERROR" : "CONTROLLER_ERROR",
                                failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(),
                                "Check the input, local data and workspace. Originals are never overwritten.")), existing.artifact(), existing.report());
                    } finally {
                        // An installation has no active conversion record. Flush every affected
                        // job before publishing idle state or releasing the shutdown lease.
                        List<JobRecord> summaries;
                        synchronized (gate) {
                            if (preparationView.state() == PreservedPreparationView.State.ANALYZING && preparationView.operationId().isPresent()) {
                                var v = preparationView;
                                preparationView = new PreservedPreparationView(cancelRequested || closed ? PreservedPreparationView.State.CANCELLED : PreservedPreparationView.State.FAILED,
                                        v.operationId(), v.candidate(), v.admission(), v.reports(), Optional.empty(), state.get().error());
                            }
                            summaries = installing ? installationRecords
                                    : activeRecord == null ? List.of() : List.of(activeRecord);
                        }
                        for (JobRecord record : summaries) saveJobSummary(record);
                        synchronized (gate) {
                            finishCompositionInspection();
                            finishCohortInspection();
                            if (mappingDataReview.status() == MappingDataView.Status.RUNNING)
                                mappingDataReview = new MappingDataView(cancelRequested || closed ? MappingDataView.Status.CANCELLED
                                        : MappingDataView.Status.FAILED, Optional.empty(), state.get().error());
                            activeJob = null;
                            cancellable = false;
                            running = false;
                            activeRecord = null;
                            installing = false;
                            installationRecords = List.of();
                            installationAction = null;
                            if (installationLease != null) { installationLease.close(); installationLease = null; }
                            state.updateAndGet(value -> new View(value.phase(), false, value.entries(), value.selected(),
                                    value.inventoryTruncated(), value.message(), value.error(), value.artifact(), value.report(), value.progress()));
                            drainNext();
                        }
                    }
                });
            } catch (RejectedExecutionException rejected) {
                running = false;
                terminal(Phase.ERROR, "Controller cannot accept this operation.", Optional.of(new EngineError("QUEUE_FULL",
                        "The bounded controller queue is full or closed.", "Wait for completion or reopen the screen.")), Optional.empty(), Optional.empty());
                return false;
            }
            return true;
        }
    }

    private <T> EngineOutcome<T> await(Supplier<EngineJob<T>> submit) throws Exception {
        EngineJob<T> job;
        synchronized (gate) {
            checkCancelled();
            cancellable = true;
            job = submit.get();
            activeJob = job;
        }
        // This wait occurs exclusively on the coordinator, never on the render thread.
        EngineOutcome<T> outcome = job.completion().toCompletableFuture().get();
        synchronized (gate) { cancellable = false; activeJob = null; }
        return outcome;
    }

    private BridgeWorkspace workspace() throws IOException, ControllerFailure {
        checkCancelled();
        BridgeWorkspace workspace = BridgeWorkspace.inspect(gameDir);
        for (Path directory : List.of(workspace.root(), workspace.input(), workspace.staging(), workspace.reports(),
                workspace.cache(), workspace.transactions())) {
            checkCancelled();
            workspace.revalidate();
            try { Files.createDirectory(directory); }
            catch (FileAlreadyExistsException exists) { workspace.revalidate(); }
        }
        workspace.revalidate();
        return workspace;
    }

    private static void validateObservedInput(InventoryResult.Entry entry, BridgeWorkspace workspace) throws IOException, ControllerFailure {
        if (!entry.path().getParent().equals(workspace.input()) || Files.isSymbolicLink(entry.path())
                || !Files.isRegularFile(entry.path(), LinkOption.NOFOLLOW_LINKS) || !entry.path().equals(entry.path().toRealPath()))
            throw failure("UNSAFE_INPUT", "Selected input no longer belongs to the real input directory.");
    }

    private static Path operationDirectory(BridgeWorkspace workspace, Path parent) throws IOException {
        workspace.revalidate();
        Path result = parent.resolve(UUID.randomUUID().toString());
        Files.createDirectory(result);
        revalidateOperation(workspace, result);
        return result;
    }

    private static void revalidateOperation(BridgeWorkspace workspace, Path operation) throws IOException {
        workspace.revalidate();
        if ((!operation.getParent().equals(workspace.staging()) && !operation.getParent().equals(workspace.reports()))
                || Files.isSymbolicLink(operation) || !Files.isDirectory(operation, LinkOption.NOFOLLOW_LINKS)
                || !operation.equals(operation.toRealPath())) throw new IOException("Unsafe operation directory");
    }

    private static void cleanEmptyDirectory(Path owned) throws IOException {
        // Non-recursive: never delete an unexpected child/file or follow a replaced link.
        if (Files.isSymbolicLink(owned) || !Files.isDirectory(owned, LinkOption.NOFOLLOW_LINKS)
                || !owned.equals(owned.toRealPath())) throw new IOException("Refusing cleanup of replaced operation directory");
        boolean empty;
        try (var children = Files.newDirectoryStream(owned)) { empty = !children.iterator().hasNext(); }
        if (empty) Files.delete(owned);
    }

    private void checkCancelled() throws ControllerFailure {
        synchronized (gate) {
            if (cancelRequested || closed) throw failure("CANCELLED", "Operation cancelled before engine publication.");
        }
    }

    private void finish(EngineOutcome<?> outcome) {
        EngineError error = outcome.error().orElseThrow();
        terminal(outcome.status() == EngineOutcome.Status.CANCELLED ? Phase.CANCELLED : Phase.ERROR,
                error.message(), Optional.of(error), Optional.empty(), Optional.empty());
    }

    private void terminal(Phase phase, String message, Optional<EngineError> error, Optional<Path> artifact, Optional<Path> report) {
        synchronized (gate) {
            if (activeRecord == null) globalError = error;
            if (activeRecord != null && activeRecord.status != JobState.APPLYING && activeRecord.status != JobState.PENDING_RESTART) {
                activeRecord.error = error.orElse(null);
                if (artifact.isPresent()) activeRecord.artifact = artifact.orElseThrow();
                if (activeRecord.analysisOnly && report.isPresent()) activeRecord.compatibilityReport = report.orElseThrow();
                if (phase == Phase.CANCELLED) activeRecord.status = JobState.CANCELLED;
                else if (activeRecord.artifact != null) activeRecord.status = JobState.CONVERTED;
                else if (error.isPresent()) activeRecord.status = blocked(error.orElseThrow().code()) ? JobState.BLOCKED : JobState.FAILED;
                else if (phase == Phase.RESULT) activeRecord.status = JobState.ANALYZED;
            }
            state.updateAndGet(previous -> new View(phase, running, previous.entries(), previous.selected(),
                    previous.inventoryTruncated(), message, error, artifact, report, Optional.empty()));
        }
    }

    @Override public void close() {
        synchronized (gate) {
            if (closed) return;
            for (JobRecord record : pending) { record.status = JobState.CANCELLED; record.error = cancellation(); }
            pending.clear();
            cancel();
            closed = true;
        }
        coordinator.shutdown();
        engine.close();
        pathWorker.shutdown();
    }

    public static String safeText(String text) {
        StringBuilder result = new StringBuilder();
        Objects.requireNonNull(text).codePoints().limit(512).forEach(cp -> result.appendCodePoint(
                Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT || cp == 0xA7 ? ' ' : cp));
        return result.toString();
    }

    /** Compatibility entry point; native UI uses the complete trusted session snapshot. */
    public boolean applyJob(UUID id, Set<String> loadedModIds) {
        return applyJobs(Map.of(id, Optional.empty()), new InstallSession(loadedModIds, Set.of(), false)).isPresent();
    }

    /** Only observed successful jobs can become an approved batch. No arbitrary report/path authority. */
    public Optional<UUID> applyJobs(Map<UUID, Optional<Path>> approvals, InstallSession session) {
        Objects.requireNonNull(approvals); Objects.requireNonNull(session);
        synchronized (gate) {
            if (closed || running || !pending.isEmpty() || approvals.isEmpty() || approvals.size() > 4) return Optional.empty();
            List<JobRecord> records = new ArrayList<>();
            List<InstallSelection> selected = new ArrayList<>();
            for (var approval : approvals.entrySet()) {
                JobRecord record = jobRecords.get(approval.getKey());
                if (record == null || record.status != JobState.CONVERTED || record.artifact == null
                        || record.outputSha256 == null || record.compatibilityReport == null
                        || record.conversionReport == null || record.transformationLog == null) return Optional.empty();
                records.add(record);
                selected.add(new InstallSelection(record.entry.path(), record.entry.fingerprint().orElseThrow().sha256(),
                        record.artifact, record.outputSha256, Objects.requireNonNull(approval.getValue())));
            }
            Optional<UUID> accepted = startInstallation(session, records, "APPLY", () -> {
                TransactionResult result = installer.apply(gameDir, selected, session);
                synchronized (gate) {
                    for (var entry : result.entries()) {
                        if (entry.index() >= 0 && entry.index() < records.size()) updateInstalledRecord(records.get(entry.index()), result, entry);
                    }
                }
                return result;
            });
            if (accepted.isPresent()) for (JobRecord record : records) { record.status = JobState.APPLYING; record.error = null; }
            return accepted;
        }
    }

    /** Bounded read-only journal inspection; invalid journals produce a visible diagnostic. */
    public boolean refreshTransactions(InstallSession session) {
        Objects.requireNonNull(session);
        synchronized (gate) {
            if (closed || running) return false;
            return start(Phase.INVENTORY, "Inspecting persistent transactions without changing installed files.", () -> {
                try {
                    List<TransactionResult> observed = installer.list(gameDir, session);
                    synchronized (gate) {
                        transactions = observed;
                        // Inspection is not a retry of failed receipt persistence or an
                        // upgrade of the last install's safe-close evidence.
                        if (installResult == null) installError = Optional.empty();
                    }
                    terminal(Phase.RESULT, "Transaction inventory inspected; recovery always requires confirmation.",
                            Optional.empty(), Optional.empty(), Optional.empty());
                } catch (InstallException failure) {
                    EngineError error = new EngineError(failure.code(), failure.getMessage(), failure.remediation());
                    synchronized (gate) { transactions = List.of(); installError = Optional.of(error); }
                    terminal(Phase.ERROR, error.message(), Optional.of(error), Optional.empty(), Optional.empty());
                }
            });
        }
    }

    /** Select only a journal from this controller's last inspected inventory. */
    public Optional<UUID> recoverTransaction(Path transaction, RecoveryAction action, InstallSession session) {
        Objects.requireNonNull(transaction); Objects.requireNonNull(action); Objects.requireNonNull(session);
        synchronized (gate) {
            if (transactions.stream().noneMatch(value -> value.transaction().equals(transaction))) return Optional.empty();
            List<JobRecord> records = jobRecords.values().stream()
                    .filter(record -> transaction.equals(record.transaction)).toList();
            return startInstallation(session, records, action.name(), () -> {
                TransactionResult result = installer.recover(gameDir, transaction, session, action);
                synchronized (gate) {
                    for (JobRecord record : records) {
                        for (var entry : result.entries()) if (record.transactionEntryIndex == entry.index())
                            updateInstalledRecord(record, result, entry);
                    }
                }
                return result;
            });
        }
    }

    private void updateInstalledRecord(JobRecord record, TransactionResult result, TransactionResult.EntryResult entry) {
        // Mod ID alone is not enough: retries/conversions can share it. Bind only an
        // exact per-file observation returned for this selected result.
        if (!Objects.equals(record.outputSha256, entry.sha256()) || record.entry.mods().stream()
                .noneMatch(mod -> mod.modId().equals(entry.modId()) && mod.version().equals(entry.version()))) {
            record.status = JobState.FAILED;
            record.error = new EngineError("INSTALL_RESULT_MISMATCH", "Installer entry does not match the selected job identity and hash.",
                    "Inspect the transaction; no installed-file claim was attached to this job.");
            return;
        }
        record.transaction = result.transaction();
        record.transactionEntryIndex = entry.index();
        record.installationObservation = new InstallationObservation(installOperation, installationAction,
                result.transaction(), result.status(), entry.index(), entry.state(), result.safeToClose());
        record.installed = entry.installed().orElse(null);
        record.error = entry.error().or(() -> result.error()).orElse(null);
        record.status = entry.state() == TransactionResult.EntryState.INSTALLED ? JobState.PENDING_RESTART
                : entry.state() == TransactionResult.EntryState.PREPARED || entry.state() == TransactionResult.EntryState.RESTORED
                ? JobState.CONVERTED : JobState.FAILED;
    }

    private Optional<UUID> startInstallation(InstallSession session, List<JobRecord> records, String actionName, InstallAction action) {
        synchronized (gate) {
            if (!reserveInstallation(session)) return Optional.empty();
            installationRecords = List.copyOf(records);
            installationAction = actionName;
            UUID operation = installOperation;
            for (JobRecord record : records) {
                record.installationObservation = new InstallationObservation(operation, actionName, null, null, null, null, false);
            }
            boolean accepted = start(Phase.CONVERSION, "Applying a journaled operation for the next launch; this operation cannot be cancelled.", () -> {
                try {
                    TransactionResult result = action.run();
                    synchronized (gate) {
                        installResult = result; installError = result.error();
                        for (JobRecord record : records) if (record.installationObservation.status() == null) {
                            record.installationObservation = new InstallationObservation(operation, actionName,
                                    result.transaction(), result.status(), null, null, result.safeToClose());
                            record.error = result.error().orElseGet(() -> new EngineError(
                                    "INSTALL_RESULT_MISMATCH", "Installer omitted a verified per-file outcome for this job.",
                                    "Inspect the transaction; do not infer installation from the batch status."));
                        }
                        if (installError.isEmpty()) installError = records.stream().map(record -> record.error)
                                .filter(Objects::nonNull).filter(error -> error.code().equals("INSTALL_RESULT_MISMATCH")).findFirst();
                        List<TransactionResult> updated = new ArrayList<>(transactions);
                        updated.removeIf(value -> value.transaction().equals(result.transaction()));
                        updated.add(result);
                        transactions = List.copyOf(updated);
                    }
                    terminal(result.safeToClose() && installError.isEmpty() ? Phase.RESULT : Phase.ERROR,
                            "Transaction: " + result.status() + ". Files are not activated in this session.",
                            installError, Optional.empty(), Optional.of(result.transaction()));
                } catch (InstallException failure) {
                    EngineError error = new EngineError(failure.code(), failure.getMessage(), failure.remediation());
                    synchronized (gate) {
                        installError = Optional.of(error);
                        for (JobRecord record : records) {
                            record.error = error;
                            record.installationObservation = new InstallationObservation(operation, actionName,
                                    failure.transaction().orElse(null), null, null, null, false);
                        }
                    }
                    terminal(Phase.ERROR, "Installation refused: " + error.message(), Optional.of(error), Optional.empty(), failure.transaction());
                } finally {
                    synchronized (gate) {
                        for (JobRecord record : records) if (record.status == JobState.APPLYING) {
                            record.status = installResult == null && installError.isPresent() ? JobState.CONVERTED : JobState.FAILED;
                            record.error = installError.orElseGet(() -> new EngineError("INSTALL_OUTCOME_UNKNOWN",
                                    "No verified per-file outcome was returned.", "Inspect the journal before retrying."));
                        }
                    }
                }
            });
            if (!accepted) {
                installing = false; installationLease.close(); installationLease = null;
                installationRecords = List.of(); installationAction = null;
                return Optional.empty();
            }
            return Optional.of(operation);
        }
    }

    /** Shared acceptance gate, used before enqueuing either installer; caller holds gate. */
    private boolean reserveInstallation(InstallSession session) {
        if (closed || running || !pending.isEmpty()) return false;
        if (session.worldOpen()) {
            installError = Optional.of(new EngineError("WORLD_OPEN", "Installation is unavailable while a world or connection is open.",
                    "Return to the title screen before applying or recovering results."));
            globalError = installError;
            return false;
        }
        try { installationLease = InstallationShutdownBarrier.acquire(); }
        catch (IllegalStateException | SecurityException failure) {
            installError = Optional.of(new EngineError("SHUTDOWN_GUARD_UNAVAILABLE", safeText(String.valueOf(failure.getMessage())),
                    "Do not apply while shutdown has begun or shutdown hooks are unavailable."));
            globalError = installError; return false;
        }
        installing = true; installOperation = UUID.randomUUID(); installResult = null; installError = Optional.empty();
        return true;
    }

    @FunctionalInterface private interface InstallAction { TransactionResult run() throws Exception; }

    /** Package-private concurrency-test seam. Production always delegates to the real installer. */
    interface InstallOperations {
        TransactionResult apply(Path root, List<InstallSelection> selected, InstallSession session) throws InstallException;
        List<TransactionResult> list(Path root, InstallSession session) throws InstallException;
        TransactionResult recover(Path root, Path transaction, InstallSession session, RecoveryAction action) throws InstallException;
    }

    public boolean openInput(Consumer<Path> opener, Consumer<EngineOutcome<Path>> completion) {
        return requestPath(() -> {
            BridgeWorkspace workspace = BridgeWorkspace.inspect(gameDir);
            workspace.revalidate();
            Path path = workspace.input();
            requireReal(path, true);
            return path;
        }, opener, completion);
    }

    public boolean openJobPath(UUID id, JobPathKind kind, Consumer<Path> opener, Consumer<EngineOutcome<Path>> completion) {
        Path selected;
        synchronized (gate) {
            JobRecord record = jobRecords.get(id);
            if (record == null) return false;
            selected = switch (kind) {
                case RESULT_FOLDER -> record.artifact == null ? null : record.artifact.getParent();
                case COMPATIBILITY_REPORT -> record.compatibilityReport;
                case TRANSFORMATION_LOG -> record.transformationLog;
                case CONVERSION_REPORT -> record.conversionReport;
            };
        }
        if (selected == null) return false;
        return requestPath(() -> {
            BridgeWorkspace workspace = BridgeWorkspace.inspect(gameDir);
            workspace.revalidate();
            Path boundary = kind == JobPathKind.RESULT_FOLDER ? workspace.staging() : workspace.reports();
            if (!selected.startsWith(boundary) || selected.equals(boundary)) throw new IOException("Result path outside its operation area");
            requireReal(selected.getParent(), true);
            requireReal(selected, kind == JobPathKind.RESULT_FOLDER);
            if (kind != JobPathKind.RESULT_FOLDER && (!selected.getFileName().toString().endsWith(".json")
                    || Files.size(selected) > 32L * 1024 * 1024)) throw new IOException("Invalid or oversized report path");
            return selected;
        }, opener, completion);
    }

    private boolean requestPath(PathAction action, Consumer<Path> opener, Consumer<EngineOutcome<Path>> completion) {
        Objects.requireNonNull(opener); Objects.requireNonNull(completion);
        synchronized (gate) {
            if (closed) return false;
            try {
                pathWorker.execute(() -> {
                    EngineOutcome<Path> outcome;
                    try {
                        synchronized (gate) { if (closed) throw new IOException("Screen closed before opening the path"); }
                        Path path = action.run();
                        opener.accept(path); // Trusted client integration; never sourced from a mod's metadata.
                        outcome = EngineOutcome.completed(path); // REQUESTED, not proof the OS opened a window.
                    } catch (Exception failure) {
                        outcome = EngineOutcome.failed(new EngineError("PATH_ACTION_FAILED", safeText(String.valueOf(failure.getMessage())),
                                "Inspect filesystem permissions or copy the validated result path manually."));
                    }
                    completion.accept(outcome);
                });
                return true;
            } catch (RejectedExecutionException full) { return false; }
        }
    }

    private static void requireReal(Path path, boolean directory) throws IOException {
        if (Files.isSymbolicLink(path) || (directory ? !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                : !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) || !path.equals(path.toRealPath()))
            throw new IOException("Path no longer resolves to the expected real file/directory");
    }

    private void drainNext() {
        while (!closed && !running && !pending.isEmpty()) {
            JobRecord record = pending.removeFirst();
            activeRecord = record;
            selectedJobId = record.id;
            View view = state.get();
            int index = -1;
            for (int i = 0; i < view.entries().size(); i++) if (view.entries().get(i).path().equals(record.entry.path())) { index = i; break; }
            if (index < 0) { record.status = JobState.FAILED; record.error = new EngineError("INPUT_CHANGED", "Observed row no longer exists.", "Refresh the input list."); activeRecord = null; continue; }
            state.set(new View(Phase.READY, false, view.entries(), index, view.inventoryTruncated(), "Starting the next queued job.",
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            record.status = JobState.CONVERTING;
            if (convertSelected(record.bundle)) return;
            if (record.error == null) { record.status = JobState.FAILED; record.error = new EngineError("INVALID_SELECTION", "Cannot run the queued selection.", "Refresh the input inventory."); }
            activeRecord = null;
        }
    }

    private JobView jobView(JobRecord record) {
        return new JobView(record.id, record.entry.path(), record.entry.mods().stream()
                .map(mod -> mod.displayName().isBlank() ? mod.modId() : mod.displayName()).limit(4)
                .reduce((a, b) -> a + ", " + b).orElse(record.entry.path().getFileName().toString()), record.status,
                Optional.ofNullable(record.error), Optional.ofNullable(record.artifact), Optional.ofNullable(record.compatibilityReport),
                Optional.ofNullable(record.transformationLog), Optional.ofNullable(record.conversionReport), Optional.ofNullable(record.installed),
                record == activeRecord && activeJob != null ? activeJob.latestProgress() : Optional.empty(),
                "legacybridge.job." + record.status.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Writes an inert per-operation receipt; inability to write remains an explicit diagnostic. */
    private void saveJobSummary(JobRecord record) {
        try {
            BridgeWorkspace workspace = BridgeWorkspace.inspect(gameDir);
            workspace.revalidate();
            Path directory = record.compatibilityReport != null ? record.compatibilityReport.getParent()
                    : record.conversionReport != null ? record.conversionReport.getParent() : operationDirectory(workspace, workspace.reports());
            revalidateOperation(workspace, directory);
            JobView summary;
            synchronized (gate) { summary = jobView(record); }
            // Sidecar only: receipt schemas 1/2 and installation journals stay unchanged.
            // An index failure never erases a committed artifact or suppresses its receipt.
            if (record.installationObservation == null) {
                Map<SavedReport.Kind, Path> saved = new java.util.EnumMap<>(SavedReport.Kind.class);
                if (record.compatibilityReport != null) saved.put(SavedReport.Kind.COMPATIBILITY, record.compatibilityReport);
                if (record.conversionReport != null) saved.put(SavedReport.Kind.CONVERSION, record.conversionReport);
                if (record.transformationLog != null) saved.put(SavedReport.Kind.TRANSFORMATIONS, record.transformationLog);
                if (!saved.isEmpty()) try {
                    new SavedReportStore().writeIndex(gameDir, record.id, record.entry.fingerprint().orElseThrow().sha256(),
                            directory, saved, record.entry.path());
                } catch (IOException indexError) {
                    synchronized (gate) {
                        record.error = new EngineError(record.error == null ? "SAVED_REPORT_INDEX_FAILED" : record.error.code(),
                                safeText((record.error == null ? "" : record.error.message() + " ")
                                        + "Persistent report index unavailable: " + indexError.getMessage()),
                                "Current-session reports and committed result retain their states. Inspect permissions/report size; historical report access may be unavailable.");
                        summary = jobView(record);
                    }
                }
            }
            Map<String, Object> tree = new LinkedHashMap<>();
            tree.put("schemaVersion", 2); tree.put("reportType", "LEGACYBRIDGE_JOB"); tree.put("jobId", record.id.toString());
            tree.put("sequence", ++record.receiptSequence);
            tree.put("event", record.installationObservation == null ? "CONVERSION" : "INSTALLATION");
            tree.put("state", summary.status().name()); tree.put("input", record.entry.path().getFileName().toString());
            tree.put("inputSha256", record.entry.fingerprint().map(value -> value.sha256()).orElse("UNKNOWN"));
            tree.put("artifactSha256", record.outputSha256); tree.put("artifact", summary.artifact().map(Path::toString).orElse(null));
            tree.put("installed", summary.installed().map(Path::toString).orElse(null));
            tree.put("transaction", record.transaction == null ? null : record.transaction.toString());
            tree.put("installation", record.installationObservation == null ? null : record.installationObservation.tree());
            tree.put("activation", "NOT_OBSERVED");
            tree.put("error", summary.error().map(error -> Map.of("code", error.code(), "message", safeText(error.message()),
                    "remediation", safeText(error.remediation()))).orElse(null));
            CanonicalReportWriter writer = new CanonicalReportWriter();
            // Append a new receipt after Apply; never overwrite the conversion's first outcome.
            writer.writeJsonAtomically(directory.resolve("job-" + UUID.randomUUID() + ".json"), writer.toJsonTree(tree), false, record.entry.path());
        } catch (Exception failure) {
            synchronized (gate) {
                record.error = new EngineError(record.error == null ? "JOB_REPORT_WRITE_FAILED" : record.error.code(),
                        safeText((record.error == null ? "" : record.error.message() + " ") + "Job receipt could not be saved: " + failure.getMessage()),
                        "Inspect workspace permissions. Existing published/installed paths keep their recorded states.");
                if (installing) {
                    installError = Optional.of(new EngineError("JOB_REPORT_WRITE_FAILED", record.error.message(), record.error.remediation()));
                    globalError = installError;
                    state.updateAndGet(value -> new View(Phase.ERROR, value.busy(), value.entries(), value.selected(),
                            value.inventoryTruncated(), "Installation outcome retained, but its job receipt could not be saved.",
                            installError, value.artifact(), value.report(), value.progress()));
                }
            }
        }
    }

    private static boolean blocked(String code) {
        return Set.of("UNSUPPORTED", "SOURCE_PROFILE_UNCONFIRMED", "DUPLICATE_MOD_ID", "MAPPING_DATA_REQUIRED",
                "INVALID_MAPPING_PATH", "ALREADY_CONVERTED").contains(code);
    }

    private static EngineError cancellation() { return new EngineError("CANCELLED", "Queued operation cancelled before execution.", "Queue it again when ready."); }

    private static final class JobRecord {
        private final UUID id = UUID.randomUUID();
        private final InventoryResult.Entry entry;
        private final String bundle;
        private final boolean analysisOnly;
        private JobState status = JobState.QUEUED;
        private EngineError error;
        private Path artifact, compatibilityReport, transformationLog, conversionReport, installed, transaction;
        private String outputSha256;
        private int receiptSequence;
        private int transactionEntryIndex = -1;
        private InstallationObservation installationObservation;
        private JobRecord(InventoryResult.Entry entry, String bundle, boolean analysisOnly) {
            this.entry = entry; this.bundle = bundle; this.analysisOnly = analysisOnly;
        }
    }

    /** Presentation receipt only: never installation/recovery authority or a discovery claim. */
    private record InstallationObservation(UUID operationId, String action, Path transaction,
                                          TransactionResult.Status status, Integer entryIndex,
                                          TransactionResult.EntryState entryState, boolean transactionSafeToClose) {
        Map<String, Object> tree() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("operationId", operationId.toString()); result.put("action", action);
            result.put("transaction", transaction == null ? null : transaction.toString());
            result.put("transactionStatus", status == null ? null : status.name());
            result.put("entryIndex", entryIndex); result.put("entryState", entryState == null ? null : entryState.name());
            result.put("transactionSafeToClose", transactionSafeToClose);
            return result;
        }
    }

    @FunctionalInterface private interface PathAction { Path run() throws Exception; }

    private static ControllerFailure failure(String code, String message) {
        return new ControllerFailure(new EngineError(code, message, "Refresh or correct the local input/data and retry."));
    }

    private static final class ControllerFailure extends Exception {
        private final EngineError error;
        private ControllerFailure(EngineError error) { super(error.message()); this.error = error; }
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }
    @FunctionalInterface private interface SelectedAction { void run(InventoryResult.Entry entry, BridgeWorkspace workspace) throws Exception; }
}
