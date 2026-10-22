package dev.legacybridge.converter.service;

import dev.legacybridge.converter.application.AnalysisService;
import dev.legacybridge.converter.archive.InputRejectedException;
import dev.legacybridge.converter.conversion.ConversionResult;
import dev.legacybridge.converter.conversion.SimpleContentConverter;
import dev.legacybridge.converter.conversion.LogicPreservingConverter;
import dev.legacybridge.converter.mapping.MappingAnalysisService;
import dev.legacybridge.converter.mapping.MappingReport;
import dev.legacybridge.converter.mapping.portable.PortableMappingBundle;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationProgress;
import dev.legacybridge.converter.operation.OperationStage;
import dev.legacybridge.converter.operation.OperationStoppedException;
import dev.legacybridge.converter.report.CompatibilityReport;
import dev.legacybridge.converter.report.Complexity;
import dev.legacybridge.converter.report.SourceProfile;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared static engine facade: no CLI calls, stdout protocol, game classes or process exits.
 * One bounded worker per instance; applications should own one long-lived service, not one
 * per button click. Queue entries retain requests only, not mappings, reports or JAR bytes.
 * Closing requests cancellation without blocking a render thread or interrupting publication.
 */
public final class LegacyEngineService implements AutoCloseable {
    private static final AtomicInteger SERVICE_IDS = new AtomicInteger();
    private final EngineOptions options;
    private final ThreadPoolExecutor executor;
    private final Set<EngineJob<?>> jobs = ConcurrentHashMap.newKeySet();

    public LegacyEngineService() { this(EngineOptions.defaults()); }

    public LegacyEngineService(EngineOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        int serviceId = SERVICE_IDS.incrementAndGet();
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(options.queueCapacity()), runnable -> {
                    Thread thread = new Thread(runnable, "legacybridge-engine-" + serviceId);
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public EngineJob<CompatibilityReport> analyze(Path input) {
        Path path = absolute(input);
        return submit(() -> {
            OperationControl.stage(OperationStage.ANALYSIS, "Inspecting metadata, resources and bytecode");
            return EngineOutcome.completed(new AnalysisService().analyze(path));
        });
    }

    /** Readback of existing selected bytes; never convertible/installable output, even when scoped checks pass. */
    public EngineJob<PreservedArtifactInspection> inspectPreservedArtifact(PreservedArtifactRequest request) {
        Objects.requireNonNull(request);
        return submit(() -> {
            if (request.mappingData().kind() == MappingData.Kind.BUILTIN_SIMPLE_CONTENT
                    || request.mappingData().kind() == MappingData.Kind.BUILTIN_COHORT_PREVIEW) throw new MappingData.MappingScopeException();
            OperationControl.stage(OperationStage.MAPPING_RESOLUTION,"Loading explicit data for independent preserved-artifact readback");
            var result = new dev.legacybridge.converter.conversion.PreservedArtifactInspector().inspect(request,request.mappingData().load());
            return result.validated() ? EngineOutcome.completed(result) : EngineOutcome.blocked(result,result.blockers().get(0));
        });
    }

    /** Read-only finite equipment diagnostic; constructor recognition never authorizes a candidate. */
    public EngineJob<IronSwordInspection> inspectIronSword(IronSwordInspectionRequest request) {
        Objects.requireNonNull(request);
        return submit(() -> {
            if (request.mappingData().kind() == MappingData.Kind.BUILTIN_SIMPLE_CONTENT
                    || request.mappingData().kind() == MappingData.Kind.BUILTIN_COHORT_PREVIEW) throw new MappingData.MappingScopeException();
            OperationControl.stage(OperationStage.MAPPING_RESOLUTION, "Loading explicitly selected equipment audit data");
            var result = new dev.legacybridge.converter.conversion.IronSwordInspector().inspect(request,request.mappingData().load());
            return EngineOutcome.blocked(result,result.blockers().get(0));
        });
    }

    /** Read-only finite armor diagnostic; native target equipment behavior is not a legacy adapter. */
    public EngineJob<IronHelmetInspection> inspectIronHelmet(IronHelmetInspectionRequest request) {
        Objects.requireNonNull(request);
        return submit(() -> {
            if (request.mappingData().kind() == MappingData.Kind.BUILTIN_SIMPLE_CONTENT
                    || request.mappingData().kind() == MappingData.Kind.BUILTIN_COHORT_PREVIEW) throw new MappingData.MappingScopeException();
            OperationControl.stage(OperationStage.MAPPING_RESOLUTION, "Loading explicitly selected armor audit data");
            var result = new dev.legacybridge.converter.conversion.IronHelmetInspector().inspect(request,request.mappingData().load());
            return EngineOutcome.blocked(result,result.blockers().get(0));
        });
    }

    /** Explicit read-only data import review; no original mod is needed or executed. */
    public EngineJob<MappingDataReview> inspectMappingData(MappingData selection) {
        Objects.requireNonNull(selection);
        return submit(() -> {
            OperationControl.stage(OperationStage.MAPPING_RESOLUTION, "Validating explicitly selected mapping data, not mod compatibility");
            return EngineOutcome.completed(MappingDataReview.inspect(selection));
        });
    }

    /** Read-only source closure/linkage, separate from all conversion profiles and installation. */
    public EngineJob<dev.legacybridge.converter.conversion.dependency.CohortSourceInspector.Result> inspectCohort(CohortInspectionRequest request) {
        Objects.requireNonNull(request);
        return submit(() -> {
            if (request.mappingData().kind() == MappingData.Kind.BUILTIN_SIMPLE_CONTENT) throw new MappingData.MappingScopeException();
            OperationControl.stage(OperationStage.MAPPING_RESOLUTION, "Loading selected cohort inspection data: " + request.mappingData().kind());
            var result = new dev.legacybridge.converter.conversion.dependency.CohortSourceInspector().inspect(request, request.mappingData().load());
            return result.sourceLinkagePassed() ? EngineOutcome.completed(result) : EngineOutcome.blocked(result,
                    new EngineError("COHORT_BLOCKED", "Source cohort inspection rejected the selection.", "Inspect per-artifact diagnostics; no converted JAR was produced."));
        });
    }

    /** Finite two-artifact private candidates. Not a public conversion profile or installation request. */
    public EngineJob<dev.legacybridge.converter.conversion.CohortTargetPlanner.Result> planCohort(CohortPlanningRequest request) {
        Objects.requireNonNull(request);
        return submit(() -> {
            if (request.source().mappingData().kind() == MappingData.Kind.BUILTIN_SIMPLE_CONTENT) throw new MappingData.MappingScopeException();
            OperationControl.stage(OperationStage.MAPPING_RESOLUTION, "Loading selected finite cohort target data: " + request.source().mappingData().kind());
            var result = new dev.legacybridge.converter.conversion.CohortTargetPlanner().plan(request, request.source().mappingData().load());
            return result.validated() ? EngineOutcome.completed(result) : EngineOutcome.blocked(result,
                    new EngineError("COHORT_TARGET_BLOCKED", "The complete target pair was rejected.", "Inspect structured diagnostics; no output was published or installed."));
        });
    }

    public EngineJob<MappingReport> mappings(Path input, MappingData data) {
        Path path = absolute(input);
        Objects.requireNonNull(data, "data");
        return submit(() -> {
            if (data.kind() == MappingData.Kind.BUILTIN_SIMPLE_CONTENT || data.kind() == MappingData.Kind.BUILTIN_COHORT_PREVIEW) throw new MappingData.MappingScopeException();
            OperationControl.stage(OperationStage.MAPPING_RESOLUTION, "Loading explicitly selected mapping data");
            MappingInputs inputs = data.load();
            return EngineOutcome.completed(new MappingAnalysisService().analyze(path, inputs));
        });
    }

    public EngineJob<ConversionPlan> plan(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        return submit(() -> {
            MappingInputs inputs = loadMappings(request);
            OperationControl.stage(OperationStage.SEMANTIC_VALIDATION, "Planning " + request.profile().id());
            try (var prepared = prepare(request, inputs)) {
                checkSelectedHash(request, prepared.inputSha256());
                ConversionPlan plan = new ConversionPlan(prepared.convertible(), request.input(), request.output(),
                        prepared.inputSha256(), Optional.ofNullable(prepared.candidateSha256()), prepared.report());
                return plan.convertible() ? EngineOutcome.completed(plan)
                        : EngineOutcome.blocked(plan, unsupported(plan.report()));
            }
        });
    }

    public EngineJob<ConversionResult> convert(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        return submit(() -> {
            MappingInputs inputs = loadMappings(request);
            OperationControl.stage(OperationStage.SEMANTIC_VALIDATION, "Converting only the supported profile");
            SimpleContentConverter converter = new SimpleContentConverter();
            try (var prepared = prepare(request, inputs)) {
                checkSelectedHash(request, prepared.inputSha256());
                ConversionResult result = converter.publish(prepared);
                return result.converted() ? EngineOutcome.completed(result)
                        : EngineOutcome.blocked(result, unsupported(result.report()));
            }
        });
    }

    /** Explicit developer inspection, separate from the six conversion profiles and publication. */
    public EngineJob<CompositionPreview> previewLogicWithRecipes(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.profile() != ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Composition inspection requires the unchanged logic-preserving base");
        return submit(() -> {
            MappingInputs inputs = loadMappings(request);
            var preview = LogicPreservingConverter.previewLogicWithRecipes(request.input(), request.output(), inputs);
            checkSelectedHash(request, preview.inputSha256());
            return preview.staticallyAdmitted() ? EngineOutcome.completed(preview)
                    : EngineOutcome.blocked(preview, unsupported(preview.report()));
        });
    }

    /** Actual private archive validation. Separate result type, never passed to the ordinary publisher/UI. */
    public EngineJob<CompositionCandidate> planLogicWithRecipes(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.profile() != ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Composition planning requires the unchanged logic-preserving base");
        return submit(() -> {
            MappingInputs inputs = loadMappings(request);
            var candidate = LogicPreservingConverter.planLogicWithRecipes(request.input(), request.output(), inputs);
            checkSelectedHash(request, candidate.inputSha256());
            return candidate.validated() ? EngineOutcome.completed(candidate)
                    : EngineOutcome.blocked(candidate, unsupported(candidate.report()));
        });
    }

    /** Private finite constructor proof/candidate on the same bounded worker; no output publication. */
    public EngineJob<CompositionCandidate> planOwnedConstructors(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.profile() != ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Constructor inspection requires the unchanged logic base");
        return submit(() -> {
            MappingInputs inputs = loadMappings(request);
            var candidate = LogicPreservingConverter.planOwnedConstructors(request.input(), request.output(), inputs);
            checkSelectedHash(request, candidate.inputSha256());
            return candidate.validated() ? EngineOutcome.completed(candidate)
                    : EngineOutcome.blocked(candidate, unsupported(candidate.report()));
        });
    }

    /** Private hierarchy/dispatch proof, reusing this service's existing bounded worker. */
    public EngineJob<CompositionCandidate> planOwnedHierarchy(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.profile() != ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Hierarchy inspection requires the unchanged logic base");
        return submit(() -> {
            var candidate = LogicPreservingConverter.planOwnedHierarchy(request.input(), request.output(), loadMappings(request));
            checkSelectedHash(request, candidate.inputSha256());
            return candidate.validated() ? EngineOutcome.completed(candidate)
                    : EngineOutcome.blocked(candidate, unsupported(candidate.report()));
        });
    }

    /** Private interface/default candidate; no new worker, public profile or output authority. */
    public EngineJob<CompositionCandidate> planOwnedInterfaceDefaults(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.profile() != ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Interface inspection requires the unchanged logic base");
        return submit(() -> {
            var candidate = LogicPreservingConverter.planOwnedInterfaceDefaults(request.input(), request.output(), loadMappings(request));
            checkSelectedHash(request, candidate.inputSha256());
            return candidate.validated() ? EngineOutcome.completed(candidate)
                    : EngineOutcome.blocked(candidate, unsupported(candidate.report()));
        });
    }

    /** Private bound-receiver inspection on the existing bounded worker; no output publication. */
    public EngineJob<CompositionCandidate> planOwnedReceiverHandles(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.profile() != ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Receiver inspection requires the unchanged logic base");
        return submit(() -> {
            var candidate = LogicPreservingConverter.planOwnedReceiverHandles(request.input(), request.output(), loadMappings(request));
            checkSelectedHash(request, candidate.inputSha256());
            return candidate.validated() ? EngineOutcome.completed(candidate)
                    : EngineOutcome.blocked(candidate, unsupported(candidate.report()));
        });
    }

    /** Private finite event proof on the existing bounded worker; never calls CLI or publishes. */
    public EngineJob<CompositionCandidate> planEventPriority(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.profile() != ConversionProfile.LIFECYCLE_PRESERVING_V1)
            throw new IllegalArgumentException("Event inspection requires the unchanged lifecycle base");
        return submit(() -> {
            var candidate = LogicPreservingConverter.planEventPriority(request.input(), request.output(), loadMappings(request));
            checkSelectedHash(request, candidate.inputSha256());
            return candidate.validated() ? EngineOutcome.completed(candidate)
                    : EngineOutcome.blocked(candidate, unsupported(candidate.report()));
        });
    }

    /** Private CLIENT config proof/hash only, on the same bounded cancellable worker. */
    public EngineJob<CompositionCandidate> planClientConfig(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.profile() != ConversionProfile.LIFECYCLE_PRESERVING_V1)
            throw new IllegalArgumentException("Client config inspection requires the unchanged lifecycle base");
        return submit(() -> {
            var candidate = LogicPreservingConverter.planClientConfig(request.input(), request.output(), loadMappings(request));
            checkSelectedHash(request, candidate.inputSha256());
            return candidate.validated() ? EngineOutcome.completed(candidate)
                    : EngineOutcome.blocked(candidate, unsupported(candidate.report()));
        });
    }

    /** Private SERVER config proof/hash only, on the same bounded cancellable worker. */
    public EngineJob<CompositionCandidate> planServerConfig(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.profile() != ConversionProfile.LIFECYCLE_PRESERVING_V1)
            throw new IllegalArgumentException("Server config inspection requires the unchanged lifecycle base");
        return submit(() -> {
            var candidate = LogicPreservingConverter.planServerConfig(request.input(), request.output(), loadMappings(request));
            checkSelectedHash(request, candidate.inputSha256());
            return candidate.validated() ? EngineOutcome.completed(candidate)
                    : EngineOutcome.blocked(candidate, unsupported(candidate.report()));
        });
    }


    /** Finite state/use proof on the shared bounded worker. No output, activation or world access. */
    public EngineJob<CompositionCandidate> planBooleanToggleBlock(ConversionRequest request) {
        Objects.requireNonNull(request,"request");
        if(request.profile()!=ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Boolean toggle inspection requires unchanged logic base");
        return submit(() -> {
            var candidate=LogicPreservingConverter.planBooleanToggleBlock(request.input(),request.output(),loadMappings(request));
            checkSelectedHash(request,candidate.inputSha256());
            return candidate.validated()?EngineOutcome.completed(candidate)
                :EngineOutcome.blocked(candidate,unsupported(candidate.report()));
        });
    }

    /** Finite geometry proof/hash on the bounded cancellable worker; no output or activation. */
    public EngineJob<CompositionCandidate> planStaticShapeBlock(ConversionRequest request) {
        Objects.requireNonNull(request,"request");
        if(request.profile()!=ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Static shape block inspection requires unchanged logic base");
        return submit(() -> {
            var candidate=LogicPreservingConverter.planStaticShapeBlock(request.input(),request.output(),loadMappings(request));
            checkSelectedHash(request,candidate.inputSha256());
            return candidate.validated()?EngineOutcome.completed(candidate)
                :EngineOutcome.blocked(candidate,unsupported(candidate.report()));
        });
    }

    /** Finite private state candidate on the bounded cancellable worker; never an output exporter. */
    public EngineJob<CompositionCandidate> planHorizontalBlock(ConversionRequest request) {
        Objects.requireNonNull(request,"request");
        if(request.profile()!=ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Horizontal block inspection requires unchanged logic base");
        return submit(() -> {
            var candidate=LogicPreservingConverter.planHorizontalBlock(request.input(),request.output(),loadMappings(request));
            checkSelectedHash(request,candidate.inputSha256());
            return candidate.validated()?EngineOutcome.completed(candidate)
                :EngineOutcome.blocked(candidate,unsupported(candidate.report()));
        });
    }

    /** Private plain block candidate using the same bounded worker/cancellation as other engine operations. */
    public EngineJob<CompositionCandidate> planPlainBlock(ConversionRequest request) {
        Objects.requireNonNull(request,"request");
        if(request.profile()!=ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Plain block inspection requires unchanged logic base");
        return submit(() -> {
            var candidate=LogicPreservingConverter.planPlainBlock(request.input(),request.output(),loadMappings(request));
            checkSelectedHash(request,candidate.inputSha256());
            return candidate.validated()?EngineOutcome.completed(candidate)
                :EngineOutcome.blocked(candidate,unsupported(candidate.report()));
        });
    }

    /** Developer-only owned attributes proof on the bounded cancellable worker; never publishable. */
    public EngineJob<CompositionCandidate> planItemAttributes(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if(request.profile()!=ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Owned attribute inspection requires unchanged logic base");
        return submit(() -> {
            var candidate=LogicPreservingConverter.planItemAttributes(request.input(),request.output(),loadMappings(request));
            checkSelectedHash(request,candidate.inputSha256());
            return candidate.validated()?EngineOutcome.completed(candidate)
                :EngineOutcome.blocked(candidate,unsupported(candidate.report()));
        });
    }

    /** Developer-only owned attributes/durability composition proof on the bounded cancellable worker; never publishable. */
    public EngineJob<CompositionCandidate> planItemAttributeDurability(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if(request.profile()!=ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Composed attribute/durability inspection requires unchanged logic base");
        return submit(() -> {
            var candidate=LogicPreservingConverter.planItemAttributeDurability(request.input(),request.output(),loadMappings(request));
            checkSelectedHash(request,candidate.inputSha256());
            return candidate.validated()?EngineOutcome.completed(candidate)
                :EngineOutcome.blocked(candidate,unsupported(candidate.report()));
        });
    }

    /** Developer-only guarded own-stack proof on the bounded worker, never publication or gameplay authority. */
    public EngineJob<CompositionCandidate> planOwnedStackDamage(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if(request.profile()!=ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Owned stack damage inspection requires unchanged logic base");
        return submit(() -> {
            var candidate=LogicPreservingConverter.planOwnedStackDamage(request.input(),request.output(),loadMappings(request));
            checkSelectedHash(request,candidate.inputSha256());
            return candidate.validated()?EngineOutcome.completed(candidate)
                :EngineOutcome.blocked(candidate,unsupported(candidate.report()));
        });
    }

    /** Developer-only guarded native item-use proof on the bounded worker, never publication or gameplay authority. */
    public EngineJob<CompositionCandidate> planOwnedStackRepairUse(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if(request.profile()!=ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Owned stack repair use inspection requires unchanged logic base");
        return submit(() -> {
            var candidate=LogicPreservingConverter.planOwnedStackRepairUse(request.input(),request.output(),loadMappings(request));
            checkSelectedHash(request,candidate.inputSha256());
            return candidate.validated()?EngineOutcome.completed(candidate)
                :EngineOutcome.blocked(candidate,unsupported(candidate.report()));
        });
    }

    /** Developer-only durable item candidate on the bounded cancellable worker. */
    public EngineJob<CompositionCandidate> planDurableItem(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if(request.profile()!=ConversionProfile.LOGIC_PRESERVING_V1)
            throw new IllegalArgumentException("Durable item inspection requires unchanged logic base");
        return submit(() -> {
            var candidate=LogicPreservingConverter.planDurableItem(request.input(),request.output(),loadMappings(request));
            checkSelectedHash(request,candidate.inputSha256());
            return candidate.validated()?EngineOutcome.completed(candidate)
                :EngineOutcome.blocked(candidate,unsupported(candidate.report()));
        });
    }

    /** Private integrated LB-09 proof; no publication or installation. */
    public EngineJob<CompositionCandidate> planLifecycleSuite(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.profile() != ConversionProfile.LIFECYCLE_PRESERVING_V1)
            throw new IllegalArgumentException("Integrated lifecycle inspection requires the unchanged lifecycle base");
        return submit(() -> {
            var candidate = LogicPreservingConverter.planLifecycleSuite(request.input(), request.output(), loadMappings(request));
            checkSelectedHash(request, candidate.inputSha256());
            return candidate.validated() ? EngineOutcome.completed(candidate)
                    : EngineOutcome.blocked(candidate, unsupported(candidate.report()));
        });
    }

    public EngineJob<Path> prepareMappings(MappingPreparationRequest request) {
        Objects.requireNonNull(request, "request");
        return submit(() -> {
            OperationControl.stage(OperationStage.MAPPING_RESOLUTION, "Preparing explicitly selected local development data");
            MappingInputs inputs = new MappingInputsLoader().load(request.profile().orElse(null));
            if (!inputs.complete()) throw new IOException("Selected mapping inputs are incomplete; prepare the pinned local baselines first");
            return EngineOutcome.completed(PortableMappingBundle.export(inputs, request.output()));
        });
    }

    private static SimpleContentConverter.PreparedConversion prepare(ConversionRequest request, MappingInputs inputs)
            throws IOException, InputRejectedException {
        return switch (request.profile()) {
            case SIMPLE_CONTENT_V1 -> new SimpleContentConverter().prepare(request.input(), request.output(), inputs);
            case DATAPACK_RECIPES_V1 -> new SimpleContentConverter(ConversionProfile.DATAPACK_RECIPES_V1).prepare(request.input(), request.output(), inputs);
            case LOGIC_PRESERVING_V1 -> new LogicPreservingConverter().prepare(request.input(), request.output(), inputs);
            case LIFECYCLE_PRESERVING_V1 -> new LogicPreservingConverter(true).prepare(request.input(), request.output(), inputs);
            case FOOD_ITEM_V1 -> new LogicPreservingConverter(ConversionProfile.FOOD_ITEM_V1).prepare(request.input(), request.output(), inputs);
            case NETWORK_SNAPSHOT_V1 -> new LogicPreservingConverter(ConversionProfile.NETWORK_SNAPSHOT_V1).prepare(request.input(), request.output(), inputs);
        };
    }

    public EngineJob<InventoryResult> inventory(Path directory) {
        Path selected = absolute(directory);
        return submit(() -> EngineOutcome.completed(inventoryNow(selected)));
    }

    private InventoryResult inventoryNow(Path directory) throws Exception {
        OperationControl.stage(OperationStage.INVENTORY, "Listing input candidates without loading them");
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory))
            throw new IOException("Input folder must be an existing real directory");
        Path resolved = directory.toRealPath();
        if (!resolved.equals(directory)) throw new IOException("Input folder resolves to another location");
        List<Path> candidates = new ArrayList<>();
        boolean truncated = false;
        int scanned = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path candidate : stream) {
                OperationControl.checkpoint();
                // Bound enumeration even if a directory contains mostly non-JAR files.
                if (++scanned > options.inventoryLimit() * 8) { truncated = true; break; }
                if (!candidate.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
                if (candidates.size() == options.inventoryLimit()) { truncated = true; break; }
                candidates.add(candidate);
            }
        }
        candidates.sort(Comparator.comparing(path -> path.getFileName().toString()));
        List<InventoryResult.Entry> entries = new ArrayList<>();
        for (Path candidate : candidates) {
            OperationControl.checkpoint();
            try {
                CompatibilityReport report = new AnalysisService().analyze(candidate);
                Optional<EngineError> inventoryError = report.assessment().analysisStatus()
                        != dev.legacybridge.converter.report.AnalysisStatus.COMPLETE || !report.metadata().parsed()
                        || report.metadata().mods().isEmpty()
                        ? Optional.of(new EngineError("INVALID_MOD_METADATA", "Metadata or bytecode analysis is incomplete or invalid.",
                                "Inspect the CompatibilityReport; do not infer compatibility from the filename.")) : Optional.empty();
                entries.add(new InventoryResult.Entry(candidate, Optional.of(report.input()), report.metadata().mods(),
                        report.assessment().sourceProfile(), report.complexity(), report.riskScore(), false, inventoryError,
                        inventoryError.isEmpty() && ConvertedInputMarker.detect(candidate, report)));
            } catch (InputRejectedException error) {
                entries.add(rejectedEntry(candidate, describe(error)));
            } catch (IOException error) {
                entries.add(rejectedEntry(candidate, describe(error)));
            }
        }
        Map<String, Integer> ids = new HashMap<>();
        entries.forEach(entry -> entry.mods().stream().map(mod -> mod.modId()).filter(id -> !id.isBlank()).distinct()
                .forEach(id -> ids.merge(id, 1, Integer::sum)));
        List<InventoryResult.Entry> marked = entries.stream().map(entry -> entry.withDuplicate(entry.mods().stream()
                .anyMatch(mod -> ids.getOrDefault(mod.modId(), 0) > 1))).toList();
        return new InventoryResult(directory, marked, truncated);
    }

    /** A corrupt ZIP can still have a bounded file fingerprint; that never makes it a valid mod. */
    private InventoryResult.Entry rejectedEntry(Path candidate, EngineError diagnostic) throws Exception {
        Optional<dev.legacybridge.converter.report.InputFingerprint> fingerprint = Optional.empty();
        try {
            if (Files.isSymbolicLink(candidate) || !candidate.equals(candidate.toRealPath())) throw new IOException("Aliased input");
            var before = Files.readAttributes(candidate, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!before.isRegularFile() || before.size() > options.operationLimits().maxInputBytes())
                throw new IOException("Fingerprint exceeds the configured input budget or is not a regular file");
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (var stream = Files.newInputStream(candidate, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = new byte[8192];
                for (int count; (count = stream.read(bytes)) != -1;) {
                    OperationControl.checkpoint();
                    if ((total += count) > options.operationLimits().maxInputBytes()) throw new IOException("Input grew beyond fingerprint budget");
                    digest.update(bytes, 0, count);
                }
            }
            var after = Files.readAttributes(candidate, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (total != before.size() || before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !Objects.equals(before.fileKey(), after.fileKey()) || !candidate.equals(candidate.toRealPath()))
                throw new IOException("Input changed while fingerprinting");
            fingerprint = Optional.of(new dev.legacybridge.converter.report.InputFingerprint(candidate.getFileName().toString(), total,
                    java.util.HexFormat.of().formatHex(digest.digest())));
        } catch (IOException unavailable) {
            diagnostic = new EngineError(diagnostic.code(), diagnostic.message() + " Fingerprint unavailable: " + unavailable.getMessage(), diagnostic.remediation());
        }
        return new InventoryResult.Entry(candidate, fingerprint, List.of(), SourceProfile.AMBIGUOUS,
                Complexity.EXTREME, 100, false, Optional.of(diagnostic));
    }

    private MappingInputs loadMappings(ConversionRequest request) throws IOException {
        if (request.mappings().kind() == MappingData.Kind.BUILTIN_COHORT_PREVIEW) throw new MappingData.MappingScopeException();
        boolean builtin = request.mappings().kind() == MappingData.Kind.BUILTIN_SIMPLE_CONTENT;
        if (builtin && request.profile() != ConversionProfile.SIMPLE_CONTENT_V1) throw new MappingData.MappingScopeException();
        OperationControl.stage(OperationStage.MAPPING_RESOLUTION, builtin
                ? "Verifying included simple-content contracts" : "Loading explicitly selected mapping data");
        return request.mappings().load();
    }

    private static void checkSelectedHash(ConversionRequest request, String actual) throws InputRejectedException {
        if (request.expectedInputSha256().isPresent() && !request.expectedInputSha256().orElseThrow().equals(actual))
            throw new InputRejectedException("INPUT_CHANGED", "Input no longer matches the player's selected fingerprint");
    }

    private static EngineError unsupported(Map<String, Object> report) {
        Object diagnostics = report.get("diagnostics");
        String detail = diagnostics instanceof List<?> list && !list.isEmpty() ? String.valueOf(list.get(0))
                : "The existing finite conversion profile cannot prove this mod compatible.";
        return new EngineError("UNSUPPORTED", detail, "Inspect the compatibility report; no partial JAR was published.");
    }

    // Package-private seam for deterministic scheduler/cancellation tests; never supplied by a mod JAR.
    <T> EngineJob<T> submit(Work<T> work) {
        AtomicReference<OperationProgress> progress = new AtomicReference<>();
        OperationControl control = new OperationControl(options.operationLimits(), progress::set);
        EngineJob<T> job = new EngineJob<>(control, progress);
        if (executor.isShutdown()) {
            job.complete(EngineOutcome.failed(new EngineError("SERVICE_CLOSED", "Service is closed.", "Open a fresh service.")), false);
            return job;
        }
        EngineAdmission.Lease admission = EngineAdmission.admit();
        if (admission == null) {
            job.complete(EngineOutcome.failed(new EngineError("ENGINE_CAPACITY", "Aggregate engine request limit reached.",
                    "Wait or cancel queued work; creating another service does not bypass the bound.")), false);
            return job;
        }
        Runnable task = () -> {
            try (var scope = control.enter()) {
                OperationControl.checkpoint();
                EngineOutcome<T> outcome;
                try (var active = EngineAdmission.enter()) { outcome = work.run(); }
                // Release before consumer callbacks: callbacks must not retain the shared engine permit.
                admission.close();
                job.complete(outcome, true);
            } catch (Exception error) {
                admission.close();
                EngineError diagnostic = describe(error);
                job.complete("CANCELLED".equals(diagnostic.code()) ? EngineOutcome.cancelled()
                        : EngineOutcome.failed(diagnostic), false);
            } catch (Error fatal) {
                admission.close();
                close(); // Poison this service; do not silently run its queued work after a JVM failure.
                job.complete(EngineOutcome.failed(new EngineError("INTERNAL_ERROR", "Engine JVM failure.",
                        "Close the service and inspect local diagnostics before retrying.")), false);
                throw fatal;
            } finally {
                admission.close();
                jobs.remove(job);
            }
        };
        job.configureQueueRemoval(() -> executor.remove(task), () -> { admission.close(); jobs.remove(job); });
        jobs.add(job);
        try {
            executor.execute(task);
        } catch (RejectedExecutionException rejected) {
            admission.close();
            jobs.remove(job);
            job.complete(EngineOutcome.failed(new EngineError(executor.isShutdown() ? "SERVICE_CLOSED" : "QUEUE_FULL",
                    "Conversion service cannot accept this job.", "Wait for a job to finish or open a new service.")), false);
        } catch (RuntimeException | Error cannotSubmit) {
            admission.close(); jobs.remove(job); close(); throw cannotSubmit;
        }
        return job;
    }

    @Override public void close() {
        // Reject new submissions before cancelling the stable/current queued and active set.
        executor.shutdown();
        jobs.forEach(EngineJob::cancel);
    }

    static EngineError describe(Exception error) {
        if (error instanceof PortableMappingBundle.ChangedBundleException)
            return new EngineError("MAPPING_DATA_CHANGED", error.getMessage(), "Review the explicitly selected data again; no fallback or partial result.");
        if (error instanceof OperationStoppedException stopped)
            return new EngineError(stopped.code().name(), stopped.getMessage(), "Retry with a smaller input or a fresh operation.");
        if (error instanceof MappingData.MissingMappingDataException)
            return new EngineError("MAPPING_DATA_REQUIRED", error.getMessage(),
                    "Use prepare-mappings explicitly, then select its local bundle with --mapping-bundle or the typed API.");
        if (error instanceof MappingData.MappingScopeException)
            return new EngineError("MAPPING_SCOPE_UNSUPPORTED", error.getMessage(),
                    "Use the built-in data only with simple-content-v1, or explicitly supply complete mapping data.");
        if (error instanceof InputRejectedException rejected)
            return new EngineError(rejected.reasonCode(), rejected.getMessage(), "Select an unchanged, valid, supported JAR.");
        if (error instanceof IOException)
            return new EngineError("IO_ERROR", error.getMessage() == null ? "Local I/O failed." : error.getMessage(),
                    "Check local input/data paths, permissions and free space; nothing is downloaded automatically.");
        if (error instanceof IllegalArgumentException)
            return new EngineError("INVALID_REQUEST", error.getMessage() == null ? "Invalid request." : error.getMessage(), "Check the request fields.");
        return new EngineError("INTERNAL_ERROR", "Unexpected engine error: " + error.getClass().getSimpleName(),
                "Keep the original JAR unchanged and report a minimal reproducer.");
    }

    private static Path absolute(Path path) { return Objects.requireNonNull(path, "path").toAbsolutePath().normalize(); }

    @FunctionalInterface interface Work<T> { EngineOutcome<T> run() throws Exception; }
}
