package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.archive.InputRejectedException;
import dev.legacybridge.converter.archive.SafeJarReader;
import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.conversion.extract.SubsetRejectionException;
import dev.legacybridge.converter.conversion.logic.VerifiedLogicMappings;
import dev.legacybridge.converter.mapping.MappingConfidence;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.metadata.ForgeMetadataAnalyzer;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStage;
import dev.legacybridge.converter.policy.NumericVersionRange;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.transformation.*;
import dev.legacybridge.converter.transformation.linkage.OfflineLinkageVerifier;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.UnsupportedCompatibilityException;
import dev.legacybridge.runtime.compat.logic.PreservedLogicAdapter;
import dev.legacybridge.runtime.compat.logic.FoodContractAdapter;
import dev.legacybridge.runtime.compat.networking.SnapshotContractAdapter;
import dev.legacybridge.converter.service.ConversionProfile;
import dev.legacybridge.converter.service.CompositionPreview;
import dev.legacybridge.converter.service.CompositionCandidate;
import dev.legacybridge.runtime.compat.logic.ComposedArtifactAdapter;
import dev.legacybridge.runtime.compat.logic.ConstructorArtifactAdapter;
import dev.legacybridge.runtime.compat.logic.HierarchyArtifactAdapter;
import dev.legacybridge.runtime.compat.logic.InterfaceArtifactAdapter;
import dev.legacybridge.converter.transformation.linkage.OwnedInterfaceDefaults;
import dev.legacybridge.converter.transformation.linkage.OwnedReceiverHandles;
import dev.legacybridge.runtime.compat.logic.ReceiverArtifactAdapter;
import dev.legacybridge.runtime.compat.logic.EventArtifactAdapter;
import dev.legacybridge.runtime.compat.logic.ClientConfigArtifactAdapter;
import dev.legacybridge.runtime.compat.logic.ServerConfigArtifactAdapter;
import dev.legacybridge.runtime.compat.logic.LifecycleSuiteAdapter;
import dev.legacybridge.runtime.compat.logic.DurableItemArtifactAdapter;
import dev.legacybridge.converter.transformation.linkage.OwnedItemHierarchy;
import dev.legacybridge.converter.conversion.composition.CompositionPlan;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/** A separate closed-external-world, body-preserving profile. No finite-graph bootstrap lowering. */
public final class LogicPreservingConverter {
    public static final String PROFILE = PreservedLogicAdapter.PROFILE;
    private final boolean lifecycle;
    private final boolean food;
    private final boolean network;
    public LogicPreservingConverter() { this(false); }
    public LogicPreservingConverter(boolean lifecycle) { this.lifecycle = lifecycle; this.food = false; this.network = false; }
    public LogicPreservingConverter(ConversionProfile profile) {
        Objects.requireNonNull(profile);
        if (profile == ConversionProfile.SIMPLE_CONTENT_V1 || profile == ConversionProfile.DATAPACK_RECIPES_V1)
            throw new IllegalArgumentException("Use the bootstrap converter for simple content and datapack recipes");
        this.lifecycle = profile == ConversionProfile.LIFECYCLE_PRESERVING_V1;
        this.food = profile == ConversionProfile.FOOD_ITEM_V1;
        this.network = profile == ConversionProfile.NETWORK_SNAPSHOT_V1;
    }

    public ConversionResult convert(Path input, Path output, MappingInputs inputs) throws IOException, InputRejectedException {
        try (var prepared = prepare(input, output, inputs)) { return new SimpleContentConverter().publish(prepared); }
    }

    public SimpleContentConverter.PreparedConversion prepare(Path input, Path output, MappingInputs inputs)
            throws IOException, InputRejectedException {
        return prepare(input, output, inputs, null);
    }

    /** Static developer preview only; no archive is serialized, returned or publishable. */
    public static CompositionPreview previewLogicWithRecipes(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var preview = new CompositionInspection(false);
        try (var proof = new LogicPreservingConverter().prepare(input, prospectiveOutput, inputs, preview)) {
            return new CompositionPreview(preview.admitted != null, proof.inputSha256(),
                    Optional.ofNullable(preview.admitted), proof.report());
        }
    }

    /** Serialize and re-open a private candidate, then return only its proof/hash, never publishable bytes. */
    public static CompositionCandidate planLogicWithRecipes(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true);
        try (var proof = new LogicPreservingConverter().prepare(input, prospectiveOutput, inputs, inspection)) {
            return new CompositionCandidate(inspection.admitted != null, proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()), proof.report());
        }
    }

    private static final class CompositionInspection {
        final boolean serializeCandidate;
        final boolean constructors;
        final boolean hierarchy;
        final boolean defaults;
        final boolean receivers;
        final boolean events;
        final boolean clientConfig;
        final boolean serverConfig;
        final boolean suite;
        final boolean durable;
        final boolean plainBlock;
        final boolean horizontalBlock;
        final boolean staticShapeBlock;
        final boolean booleanToggleBlock;
        final boolean itemAttributes;
        final boolean itemAttributeDurability;
        final boolean ownedStackDamage;
        final boolean ownedStackRepairUse;
        boolean retainCandidate;
        byte[] bytes;
        CompositionPlan admitted;
        CompositionInspection(boolean serializeCandidate) { this(serializeCandidate, false); }
        CompositionInspection(boolean serializeCandidate, boolean constructors) {
            this(serializeCandidate, constructors, false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy) {
            this(serializeCandidate, constructors, hierarchy, false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults) {
            this(serializeCandidate, constructors, hierarchy, defaults, false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers) {
            this(serializeCandidate, constructors, hierarchy, defaults, receivers, false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events) {
            this(serializeCandidate, constructors, hierarchy, defaults, receivers, events, false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig) {
            this(serializeCandidate, constructors, hierarchy, defaults, receivers, events, clientConfig, false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig) {
            this(serializeCandidate, constructors, hierarchy, defaults, receivers, events, clientConfig, serverConfig, false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite) {
            this(serializeCandidate, constructors, hierarchy, defaults, receivers, events, clientConfig, serverConfig, suite, false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable) {
            this(serializeCandidate, constructors, hierarchy, defaults, receivers, events, clientConfig, serverConfig, suite, durable, false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock) {
            this(serializeCandidate, constructors, hierarchy, defaults, receivers, events, clientConfig, serverConfig, suite, durable, plainBlock, false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock) {
            this(serializeCandidate,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock) {
            this(serializeCandidate,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock) {
            this(serializeCandidate,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,booleanToggleBlock,false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock, boolean itemAttributes) {
            this(serializeCandidate,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,booleanToggleBlock,itemAttributes,false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock, boolean itemAttributes, boolean itemAttributeDurability) {
            this(serializeCandidate,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,booleanToggleBlock,itemAttributes,itemAttributeDurability,false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock, boolean itemAttributes, boolean itemAttributeDurability, boolean ownedStackDamage) {
            this(serializeCandidate,constructors,hierarchy,defaults,receivers,events,clientConfig,serverConfig,suite,durable,plainBlock,horizontalBlock,staticShapeBlock,booleanToggleBlock,itemAttributes,itemAttributeDurability,ownedStackDamage,false);
        }
        CompositionInspection(boolean serializeCandidate, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock, boolean itemAttributes, boolean itemAttributeDurability, boolean ownedStackDamage, boolean ownedStackRepairUse) {
            this.ownedStackRepairUse=ownedStackRepairUse;
            this.ownedStackDamage=ownedStackDamage;
            this.itemAttributeDurability=itemAttributeDurability;
            this.itemAttributes=itemAttributes;
            this.booleanToggleBlock=booleanToggleBlock;
            this.staticShapeBlock = staticShapeBlock;
            this.horizontalBlock = horizontalBlock;
            this.plainBlock = plainBlock;
            this.durable = durable;
            this.suite = suite;
            if ((ownedStackRepairUse ? 1 : 0) + (ownedStackDamage ? 1 : 0) + (itemAttributeDurability ? 1 : 0) + (itemAttributes ? 1 : 0) + (booleanToggleBlock ? 1 : 0) + (staticShapeBlock ? 1 : 0) + (horizontalBlock ? 1 : 0) + (plainBlock ? 1 : 0) + (durable ? 1 : 0) + (suite ? 1 : 0) + (constructors ? 1 : 0) + (hierarchy ? 1 : 0) + (defaults ? 1 : 0) + (receivers ? 1 : 0) + (events ? 1 : 0) + (clientConfig ? 1 : 0) + (serverConfig ? 1 : 0) > 1)
                throw new IllegalArgumentException("Independent private contracts cannot be implicitly combined");
            this.serializeCandidate = serializeCandidate; this.constructors = constructors; this.hierarchy = hierarchy; this.defaults = defaults; this.receivers = receivers; this.events = events; this.clientConfig = clientConfig; this.serverConfig = serverConfig;
        }
    }

    /** Distinct private constructor candidate; never a seventh profile or publishable PreparedConversion. */
    public static CompositionCandidate planOwnedConstructors(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true, true);
        try (var proof = new LogicPreservingConverter().prepare(input, prospectiveOutput, inputs, inspection)) {
            return new CompositionCandidate(inspection.admitted != null, proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()), proof.report());
        }
    }

    /** Separate finite hierarchy candidate, never a new public conversion profile or output exporter. */
    public static CompositionCandidate planOwnedHierarchy(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true, false, true);
        try (var proof = new LogicPreservingConverter().prepare(input, prospectiveOutput, inputs, inspection)) {
            return new CompositionCandidate(inspection.admitted != null, proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()), proof.report());
        }
    }

    /** Separate private interface/default candidate; never grants publication or installation. */
    public static CompositionCandidate planOwnedInterfaceDefaults(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true, false, false, true);
        try (var proof = new LogicPreservingConverter().prepare(input, prospectiveOutput, inputs, inspection)) {
            return new CompositionCandidate(inspection.admitted != null, proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()), proof.report());
        }
    }

    /** Independent receiver proof/readback only, never an exporter or install authority. */
    public static CompositionCandidate planOwnedReceiverHandles(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true, false, false, false, true);
        try (var proof = new LogicPreservingConverter().prepare(input, prospectiveOutput, inputs, inspection)) {
            return new CompositionCandidate(inspection.admitted != null, proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()), proof.report());
        }
    }

    /** Private finite event candidate. No new public profile, bytes or installation authority. */
    public static CompositionCandidate planEventPriority(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true, false, false, false, false, true);
        try (var proof = new LogicPreservingConverter(true).prepare(input, prospectiveOutput, inputs, inspection)) {
            return new CompositionCandidate(inspection.admitted != null, proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()), proof.report());
        }
    }

    /** Private CLIENT config candidate: no export, profile promotion or installation authority. */
    public static CompositionCandidate planClientConfig(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true, false, false, false, false, false, true);
        try (var proof = new LogicPreservingConverter(true).prepare(input, prospectiveOutput, inputs, inspection)) {
            return new CompositionCandidate(inspection.admitted != null, proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()), proof.report());
        }
    }

    /** Private SERVER config candidate: no export, profile promotion or installation authority. */
    public static CompositionCandidate planServerConfig(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true, false, false, false, false, false, false, true);
        try (var proof = new LogicPreservingConverter(true).prepare(input, prospectiveOutput, inputs, inspection)) {
            return new CompositionCandidate(inspection.admitted != null, proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()), proof.report());
        }
    }

    /** Finite server-authoritative interaction proof; never publishes bytes or installs a mod. */
    public static CompositionCandidate planBooleanToggleBlock(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection=new CompositionInspection(true,false,false,false,false,false,false,false,false,false,false,false,false,true);
        try(var proof=new LogicPreservingConverter().prepare(input,prospectiveOutput,inputs,inspection)) {
            return new CompositionCandidate(inspection.admitted!=null,proof.inputSha256(),
                Optional.ofNullable(proof.candidateSha256()),proof.report());
        }
    }

    /** Private fixed geometry candidate; no export, install or native execution. */
    public static CompositionCandidate planStaticShapeBlock(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true,false,false,false,false,false,false,false,false,false,false,false,true);
        try (var proof = new LogicPreservingConverter().prepare(input,prospectiveOutput,inputs,inspection)) {
            return new CompositionCandidate(inspection.admitted != null,proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()),proof.report());
        }
    }

    /** Private finite state/placement candidate. Never exported, installed or hot activated. */
    public static CompositionCandidate planHorizontalBlock(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true, false, false, false, false, false, false, false, false, false, false, true);
        try (var proof = new LogicPreservingConverter().prepare(input, prospectiveOutput, inputs, inspection)) {
            return new CompositionCandidate(inspection.admitted != null, proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()), proof.report());
        }
    }

    /** Finite Block/BlockItem candidate only: no exporter or installer authority. */
    public static CompositionCandidate planPlainBlock(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true, false, false, false, false, false, false, false, false, false, true);
        try (var proof = new LogicPreservingConverter().prepare(input, prospectiveOutput, inputs, inspection)) {
            return new CompositionCandidate(inspection.admitted != null, proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()), proof.report());
        }
    }

    /** Private finite attributes proof: no published bytes, installation or native qualification. */
    public static CompositionCandidate planItemAttributes(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true,false,false,false,false,false,false,false,false,false,false,false,false,false,true);
        try (var proof = new LogicPreservingConverter().prepare(input,prospectiveOutput,inputs,inspection)) {
            return new CompositionCandidate(inspection.admitted != null,proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()),proof.report());
        }
    }

    /** Private finite attribute/durability composition proof: no published bytes, installation or native qualification. */
    public static CompositionCandidate planItemAttributeDurability(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true);
        try (var proof = new LogicPreservingConverter().prepare(input,prospectiveOutput,inputs,inspection)) {
            return new CompositionCandidate(inspection.admitted != null,proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()),proof.report());
        }
    }

    /** Private guarded stack helper proof/hash; no gameplay activation, publication or installation. */
    public static CompositionCandidate planOwnedStackDamage(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true);
        try (var proof = new LogicPreservingConverter().prepare(input,prospectiveOutput,inputs,inspection)) {
            return new CompositionCandidate(inspection.admitted != null,proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()),proof.report());
        }
    }

    /** Private guarded same-hand native use proof/hash; no gameplay activation, publication or installation. */
    public static CompositionCandidate planOwnedStackRepairUse(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true);
        try (var proof = new LogicPreservingConverter().prepare(input,prospectiveOutput,inputs,inspection)) {
            return new CompositionCandidate(inspection.admitted != null,proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()),proof.report());
        }
    }

    /** Independent private durable-item proof/hash; never export or install authority. */
    public static CompositionCandidate planDurableItem(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true, false, false, false, false, false, false, false, false, true);
        try (var proof = new LogicPreservingConverter().prepare(input, prospectiveOutput, inputs, inspection)) {
            return new CompositionCandidate(inspection.admitted != null, proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()), proof.report());
        }
    }

    /** Private integrated lifecycle proof/hash; never export or installation authority. */
    public static CompositionCandidate planLifecycleSuite(Path input, Path prospectiveOutput, MappingInputs inputs)
            throws IOException, InputRejectedException {
        var inspection = new CompositionInspection(true, false, false, false, false, false, false, false, true);
        try (var proof = new LogicPreservingConverter(true).prepare(input, prospectiveOutput, inputs, inspection)) {
            return new CompositionCandidate(inspection.admitted != null, proof.inputSha256(),
                    Optional.ofNullable(proof.candidateSha256()), proof.report());
        }
    }

    /** Internal, synchronous borrowing seam for separately compiled developer QA. Not a public service API. */
    @FunctionalInterface interface CandidateReceiver {
        void accept(byte[] bytes, CompositionCandidate proof) throws IOException;
    }

    static CompositionCandidate inspectLogicWithRecipesCandidate(Path input, Path prospectiveOutput, MappingInputs inputs,
                                                                 CandidateReceiver receiver) throws IOException, InputRejectedException {
        Objects.requireNonNull(receiver);
        var inspection = new CompositionInspection(true); inspection.retainCandidate = true;
        try (var prepared = new LogicPreservingConverter().prepare(input, prospectiveOutput, inputs, inspection)) {
            var proof = new CompositionCandidate(inspection.admitted != null, prepared.inputSha256(),
                    Optional.ofNullable(prepared.candidateSha256()), prepared.report());
            if (proof.validated()) {
                OperationControl.checkpoint();
                receiver.accept(inspection.bytes, proof);
            }
            return proof;
        } finally {
            if (inspection.bytes != null) Arrays.fill(inspection.bytes, (byte) 0);
            inspection.bytes = null;
        }
    }

    private SimpleContentConverter.PreparedConversion prepare(Path input, Path output, MappingInputs inputs, CompositionInspection preview)
            throws IOException, InputRejectedException {
        OperationControl.checkpoint();
        boolean constructors = preview != null && preview.constructors;
        boolean hierarchyCandidate = preview != null && preview.hierarchy;
        boolean interfaceCandidate = preview != null && preview.defaults;
        boolean receiverCandidate = preview != null && preview.receivers;
        boolean eventCandidate = preview != null && preview.events;
        boolean booleanToggleBlockCandidate = preview != null && preview.booleanToggleBlock;
        boolean staticShapeBlockCandidate = preview != null && preview.staticShapeBlock;
        boolean horizontalBlockCandidate = preview != null && preview.horizontalBlock;
        boolean plainBlockCandidate = preview != null && preview.plainBlock;
        boolean ownedStackRepairUseCandidate = preview != null && preview.ownedStackRepairUse;
        boolean ownedStackDamageCandidate = preview != null && preview.ownedStackDamage;
        boolean itemAttributeDurabilityCandidate = preview != null && preview.itemAttributeDurability;
        boolean itemAttributesCandidate = preview != null && preview.itemAttributes;
        boolean durableCandidate = preview != null && preview.durable;
        boolean suiteCandidate = preview != null && preview.suite;
        boolean serverConfigCandidate = preview != null && preview.serverConfig;
        boolean clientConfigCandidate = preview != null && preview.clientConfig;
        Path destination = SimpleContentConverter.destination(input, output);
        var captured = new SimpleContentConverter.CapturedArchive();
        var archive = new SafeJarReader().scan(input, captured);
        Map<String, Object> report = new TreeMap<>();
        report.put("schemaVersion", 1); report.put("reportType", "LEGACY_CONVERSION_PLAN");
        report.put("supportProfile", network ? SnapshotContractAdapter.PROFILE : food ? FoodContractAdapter.PROFILE : lifecycle ? dev.legacybridge.runtime.compat.logic.LifecycleContractAdapter.PROFILE : PROFILE);
        report.put("input", Map.of("fileName", archive.input().fileName(), "sha256", archive.input().sha256()));
        report.put("targetMinecraft", "1.20.1"); report.put("targetForge", "47.4.23");
        report.put("runtimeRequired", "legacybridge_runtime"); report.put("runtimeVersion", AdapterCatalog.RUNTIME_VERSION);
        report.put("sources", inputs.sources().stream().map(source -> Map.of("id", source.id(), "sha256", source.sha256(), "origin", source.origin())).toList());
        report.put("bytecodeExecuted", false); report.put("runtimeValidation", "NOT_RUN");
        if (preview != null) {
            report.put("reportType", "LEGACY_COMPOSITION_PREVIEW");
            report.put("supportProfile", "NOT_A_CONVERSION_PROFILE");
            report.put("composition", "logic-with-datapack-recipes-v1");
            report.put("publicationAllowed", false);
            if (preview.serializeCandidate) {
                report.put("reportType", "LEGACY_COMPOSITION_CANDIDATE");
                report.put("candidateIdentity", ownedStackRepairUseCandidate ? dev.legacybridge.runtime.compat.logic.OwnedStackRepairUseArtifactAdapter.ID : ownedStackDamageCandidate ? dev.legacybridge.runtime.compat.logic.OwnedStackDamageArtifactAdapter.ID : itemAttributeDurabilityCandidate ? dev.legacybridge.runtime.compat.logic.ItemAttributeDurabilityArtifactAdapter.ID : itemAttributesCandidate ? dev.legacybridge.runtime.compat.logic.ItemAttributeArtifactAdapter.ID : booleanToggleBlockCandidate ? dev.legacybridge.runtime.compat.logic.BooleanToggleBlockArtifactAdapter.ID : staticShapeBlockCandidate ? dev.legacybridge.runtime.compat.logic.StaticShapeBlockArtifactAdapter.ID : horizontalBlockCandidate ? dev.legacybridge.runtime.compat.logic.HorizontalBlockArtifactAdapter.ID : plainBlockCandidate ? dev.legacybridge.runtime.compat.logic.PlainBlockArtifactAdapter.ID : durableCandidate ? DurableItemArtifactAdapter.ID : suiteCandidate ? LifecycleSuiteAdapter.ID : serverConfigCandidate ? ServerConfigArtifactAdapter.ID : clientConfigCandidate ? ClientConfigArtifactAdapter.ID : eventCandidate ? EventArtifactAdapter.ID : receiverCandidate ? ReceiverArtifactAdapter.ID : interfaceCandidate ? InterfaceArtifactAdapter.ID : hierarchyCandidate ? HierarchyArtifactAdapter.ID : constructors ? ConstructorArtifactAdapter.ID : ComposedArtifactAdapter.ID);
            }
            if (booleanToggleBlockCandidate) { report.put("reportType","LEGACY_BOOLEAN_TOGGLE_BLOCK_CANDIDATE");
                report.put("composition","boolean-toggle-block-with-recipes-v1"); report.put("booleanToggleVariant","BOOLEAN_TOGGLE_USE"); }
            if (staticShapeBlockCandidate) { report.put("reportType","LEGACY_STATIC_SHAPE_BLOCK_CANDIDATE");
                report.put("composition","static-shape-block-with-recipes-v1"); }
            if (horizontalBlockCandidate) { report.put("reportType", "LEGACY_HORIZONTAL_BLOCK_CANDIDATE");
                report.put("composition", "horizontal-block-with-recipes-v1"); }
            if (plainBlockCandidate) { report.put("reportType", "LEGACY_PLAIN_BLOCK_CANDIDATE");
                report.put("composition", "plain-block-with-recipes-v1"); }
            if (ownedStackRepairUseCandidate) { report.put("reportType","LEGACY_OWNED_STACK_REPAIR_USE_CANDIDATE");
                report.put("composition","owned-stack-repair-use-with-recipes-v1");
                report.put("gameplayActivation","PRESERVED_NATIVE_CALLBACK_NOT_RUN");
                report.put("threadAndSyncQualification","NOT_RUN");
                report.put("nativeUseLimits",List.of("Client SUCCESS predicts swing, not server repair",
                    "Server helper runs once per eligible callback, not a universal per-click guarantee",
                    "Native creative caller restores prior damage",
                    "Cancellation, cooldown, spectator bypass and synchronization require native QA")); }
            if (ownedStackDamageCandidate) { report.put("reportType","LEGACY_OWNED_STACK_DAMAGE_CANDIDATE");
                report.put("composition","owned-stack-damage-with-recipes-v1"); report.put("gameplayActivation","NOT_IMPLEMENTED");
                report.put("threadAndSyncQualification","NOT_RUN"); }
            if (itemAttributeDurabilityCandidate) { report.put("reportType", "LEGACY_ITEM_ATTRIBUTE_DURABILITY_CANDIDATE");
                report.put("composition", "item-attribute-durability-with-recipes-v1"); }
            if (itemAttributesCandidate) { report.put("reportType", "LEGACY_ITEM_ATTRIBUTE_CANDIDATE");
                report.put("composition", "item-attributes-with-recipes-v1"); }
            if (durableCandidate) { report.put("reportType", "LEGACY_DURABLE_ITEM_CANDIDATE");
                report.put("composition", "durable-item-with-recipes-v1"); }
            if (suiteCandidate) { report.put("reportType", "LEGACY_LIFECYCLE_SUITE_CANDIDATE");
                report.put("runtimeValidation", "NOT_RUN"); }
            if (serverConfigCandidate) { report.put("reportType", "LEGACY_SERVER_CONFIG_CANDIDATE");
                report.put("composition", "server-config-with-recipes-v1"); }
            if (clientConfigCandidate) { report.put("reportType", "LEGACY_CLIENT_CONFIG_CANDIDATE");
                report.put("composition", "client-config-with-recipes-v1"); }
            if (eventCandidate) { report.put("reportType", "LEGACY_EVENT_PRIORITY_CANDIDATE");
                report.put("composition", "event-priority-with-recipes-v1"); }
            if (constructors) { report.put("reportType", "LEGACY_OWNED_CONSTRUCTOR_CANDIDATE");
                report.put("composition", "owned-constructors-with-recipes-v1"); }
            if (hierarchyCandidate) { report.put("reportType", "LEGACY_OWNED_HIERARCHY_CANDIDATE");
                report.put("composition", "owned-hierarchy-with-recipes-v1"); }
            if (receiverCandidate) { report.put("reportType", "LEGACY_OWNED_RECEIVER_CANDIDATE");
                report.put("composition", "owned-receiver-with-recipes-v1"); }
            if (interfaceCandidate) { report.put("reportType", "LEGACY_OWNED_INTERFACE_CANDIDATE");
                report.put("composition", "owned-interface-with-recipes-v1"); }
        }
        var pipeline = new ArrayList<String>();
        if (network) pipeline.addAll(List.of("NetworkClassLimits", "RawRecipeAdmission"));
        pipeline.addAll(List.of("ClassReader", "LegacyReferenceScanner", "SourceOfflineLinkage",
                "ExactRecipeMappingResolver", "ClassRemapper", "DescriptorTransformer", "TargetOfflineLinkage",
                "ClassWriter", "CheckClassAdapter", "FinalOfflineLinkage"));
        if(plainBlockCandidate || horizontalBlockCandidate || staticShapeBlockCandidate || booleanToggleBlockCandidate) pipeline.add(pipeline.indexOf("ClassRemapper"), "ExactContextualStonePairLowering");
        if(ownedStackRepairUseCandidate) pipeline.add(pipeline.indexOf("LegacyReferenceScanner")+1,"OwnedStackRepairUseGraphBoundsAndContract");
        if(ownedStackDamageCandidate) pipeline.add(pipeline.indexOf("LegacyReferenceScanner")+1,"OwnedStackDamageGraphBoundsAndContract");
        if(itemAttributeDurabilityCandidate) pipeline.add(pipeline.indexOf("LegacyReferenceScanner")+1,"ItemAttributeDurabilityGraphBoundsAndContract");
        if(itemAttributesCandidate) pipeline.add(pipeline.indexOf("LegacyReferenceScanner")+1,"ItemAttributeGraphBoundsAndContract");
        report.put("pipeline", List.copyOf(pipeline));
        TransformationLog log = new TransformationLog();
        TransformationBindings bindings = ownedStackRepairUseCandidate ? TransformationBindings.forOwnedStackRepairUse() : ownedStackDamageCandidate ? TransformationBindings.forOwnedStackDamage() : itemAttributeDurabilityCandidate ? TransformationBindings.forItemAttributeDurability() : itemAttributesCandidate ? TransformationBindings.forItemAttributes() : booleanToggleBlockCandidate ? TransformationBindings.forBooleanToggleBlock() : staticShapeBlockCandidate ? TransformationBindings.forStaticShapeBlock() : horizontalBlockCandidate ? TransformationBindings.forHorizontalBlock() : plainBlockCandidate ? TransformationBindings.forPlainBlock() : durableCandidate ? TransformationBindings.forDurableItem() : suiteCandidate ? TransformationBindings.forLifecycleSuite() : serverConfigCandidate ? TransformationBindings.forServerConfig() : clientConfigCandidate ? TransformationBindings.forClientConfig() : eventCandidate ? TransformationBindings.forEventPriority() : receiverCandidate ? TransformationBindings.forOwnedReceiverHandles() : interfaceCandidate ? TransformationBindings.forOwnedInterfaceDefaults() : hierarchyCandidate ? TransformationBindings.forOwnedHierarchy() : constructors ? TransformationBindings.forOwnedConstructors()
                : preview != null && preview.serializeCandidate ? TransformationBindings.forComposition()
                : network ? TransformationBindings.forNetwork() : food ? TransformationBindings.forFood() : lifecycle ? TransformationBindings.forLifecycle() : TransformationBindings.forLogic();
        var callbackPolicy = ownedStackRepairUseCandidate ? OfflineLinkageVerifier.CallbackPolicy.OWNED_STACK_REPAIR_USE : ownedStackDamageCandidate ? OfflineLinkageVerifier.CallbackPolicy.OWNED_STACK_DAMAGE : itemAttributeDurabilityCandidate ? OfflineLinkageVerifier.CallbackPolicy.ITEM_ATTRIBUTE_DURABILITY : itemAttributesCandidate ? OfflineLinkageVerifier.CallbackPolicy.ITEM_ATTRIBUTES : booleanToggleBlockCandidate ? OfflineLinkageVerifier.CallbackPolicy.BOOLEAN_TOGGLE_BLOCK : staticShapeBlockCandidate ? OfflineLinkageVerifier.CallbackPolicy.STATIC_SHAPE_BLOCK : horizontalBlockCandidate ? OfflineLinkageVerifier.CallbackPolicy.HORIZONTAL_BLOCK : suiteCandidate ? OfflineLinkageVerifier.CallbackPolicy.LIFECYCLE_SUITE : serverConfigCandidate ? OfflineLinkageVerifier.CallbackPolicy.SERVER_CONFIG : clientConfigCandidate ? OfflineLinkageVerifier.CallbackPolicy.CLIENT_CONFIG : eventCandidate ? OfflineLinkageVerifier.CallbackPolicy.OWNED_EVENT_PRIORITY : network ? OfflineLinkageVerifier.CallbackPolicy.NETWORK_SNAPSHOT
                : lifecycle ? OfflineLinkageVerifier.CallbackPolicy.LIFECYCLE : OfflineLinkageVerifier.CallbackPolicy.LOGIC;
        try {
            OperationControl.stage(OperationStage.SEMANTIC_VALIDATION, "Checking explicit logic-preserving profile admission");
            require(inputs.complete(), "Complete explicit mapping/API inputs are required");
            require(!archive.archive().signed() && !archive.archive().multiRelease(), "Signed/multi-release input is unsupported");
            var metadata = new ForgeMetadataAnalyzer().analyze(captured.entries.get("META-INF/mods.toml"));
            require(metadata.valid() && metadata.summary().mods().size() == 1, "Exactly one valid Forge mod declaration is required");
            require("javafml".equals(metadata.summary().modLoader()) && NumericVersionRange.contains(metadata.summary().loaderVersion(), "36"), "Source loader must admit javafml 36");
            var mod = metadata.summary().mods().get(0);
            require(!Set.of("minecraft", "forge", "legacybridge_runtime").contains(mod.modId()), "Reserved modId");
            boolean minecraft = false;
            for (var dependency : metadata.summary().dependencies()) {
                if (!lifecycle && !food) {
                    require(Set.of("minecraft", "forge").contains(dependency.modId()), "Unknown external dependency: " + dependency.modId());
                    require("BOTH".equals(dependency.side()), "Side-specific dependency contracts are unsupported in this profile");
                    require(NumericVersionRange.contains(dependency.versionRange(), dependency.modId().equals("forge") ? "36.2.42" : "1.16.5"), "Dependency excludes the exact source baseline");
                }
                minecraft |= dependency.modId().equals("minecraft") && dependency.mandatory();
            }
            require(minecraft, "Mandatory Minecraft 1.16.5 dependency is required");
            var manifest = SimpleContentConverter.validateManifest(captured.entries.get("META-INF/MANIFEST.MF"));
            String version = mod.version();
            if (version.equals("${file.jarVersion}")) version = manifest.getMainAttributes().getValue("Implementation-Version");
            require(version != null && version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}"), "Unresolved or unsafe exact mod version");
            report.put("modId", mod.modId()); report.put("modVersion", version);
            if (lifecycle || food) LifecycleMetadata.verify(captured.entries.get("META-INF/mods.toml"), version, false, report);
            Map<String, byte[]> source = new TreeMap<>();
            captured.entries.forEach((name, bytes) -> { if (name.endsWith(".class")) source.put(name, bytes); });
            require(!source.isEmpty() && source.size() <= 32, "Logic profile admits at most 32 owned classes");
            // Network admission and class-file allocation bounds run BEFORE any frame expansion/dataflow.
            if (network) report.put("networkContract", NetworkSnapshotRecipe.validateRaw(source, mod.modId(), version));
            var scan = new LegacyReferenceScanner().scan(source);
            Map<String, ClassNode> originals = scan.classNodes();
            // Reject hostile frame bounds and unaccounted effects BEFORE ASM allocates analysis frames.
            if(ownedStackRepairUseCandidate) OwnedStackRepairUseContract.inspect(originals);
            if(ownedStackDamageCandidate) OwnedStackDamageContract.inspect(originals);
            if(itemAttributeDurabilityCandidate) ItemAttributeDurabilityContract.inspect(originals);
            if(itemAttributesCandidate) ItemAttributeContract.inspect(originals);
            for (byte[] bytes : source.values()) BytecodeValidator.validate(bytes);
            if (network) NetworkSnapshotRecipe.validate(originals, mod.modId(), version);
            OperationControl.stage(OperationStage.MAPPING_RESOLUTION, "Composing exact independently reviewed logic contracts");
            var mappings = ownedStackRepairUseCandidate ? VerifiedLogicMappings.compileOwnedStackRepairUse(originals,scan,inputs) : ownedStackDamageCandidate ? VerifiedLogicMappings.compileOwnedStackDamage(originals,scan,inputs) : itemAttributeDurabilityCandidate ? VerifiedLogicMappings.compileItemAttributeDurability(originals,scan,inputs) : itemAttributesCandidate ? VerifiedLogicMappings.compileItemAttributes(originals,scan,inputs) : booleanToggleBlockCandidate ? VerifiedLogicMappings.compileBooleanToggleBlock(originals,scan,inputs) : staticShapeBlockCandidate ? VerifiedLogicMappings.compileStaticShapeBlock(originals,scan,inputs) : horizontalBlockCandidate ? VerifiedLogicMappings.compileHorizontalBlock(originals, scan, inputs) : plainBlockCandidate ? VerifiedLogicMappings.compilePlainBlock(originals, scan, inputs) : durableCandidate ? VerifiedLogicMappings.compileDurableItem(originals, scan, inputs) : suiteCandidate ? VerifiedLogicMappings.compileLifecycleSuite(originals, scan, inputs) : serverConfigCandidate ? VerifiedLogicMappings.compileServerConfig(originals, scan, inputs) : clientConfigCandidate ? VerifiedLogicMappings.compileClientConfig(originals, scan, inputs) : eventCandidate ? VerifiedLogicMappings.compileEvents(originals, scan, inputs) : network ? VerifiedLogicMappings.compileNetwork(originals, scan, inputs) : food ? VerifiedLogicMappings.compileFood(originals, scan, inputs) : VerifiedLogicMappings.compile(originals, scan, inputs, lifecycle);
            report.put("mappingRecipes", mappings.rules());
            OperationControl.stage(OperationStage.BYTECODE_VALIDATION, "Checking original linkage/access without loading classes");
            String constructorOwner = null;
            if (constructors) {
                var items = originals.values().stream().filter(n -> "net/minecraft/item/Item".equals(n.superName)).toList();
                require(items.size() == 1, "One exact legacy Item owner is required for constructor proof");
                constructorOwner = items.get(0).name;
                report.put("sourceOwnedConstructorProof", dev.legacybridge.converter.transformation.linkage.OwnedConstructorGraph.inspect(items.get(0)).toTree());
            }
            var sourceLinkage = receiverCandidate ? new OfflineLinkageVerifier().verifyOwnedReceiverHandles(source, mappings.sourceApi())
                    : interfaceCandidate ? new OfflineLinkageVerifier().verifyOwnedInterfaceDefaults(source, mappings.sourceApi())
                    : constructors ? new OfflineLinkageVerifier().verifyOwnedConstructors(source, mappings.sourceApi(), constructorOwner)
                    : new OfflineLinkageVerifier().verify(source, mappings.sourceApi(), callbackPolicy);
            report.put("sourceOfflineLinkage", sourceLinkage.toTree());
            var sourceHierarchy = hierarchyCandidate ? OwnedItemHierarchy.inspect(originals, mappings.sourceApi(), "net/minecraft/item/Item") : null;
            var sourceInterface = interfaceCandidate ? OwnedInterfaceDefaults.inspect(originals) : null;
            var sourceReceiver = receiverCandidate ? OwnedReceiverHandles.inspect(originals) : null;
            var sourceBooleanToggleBlock = booleanToggleBlockCandidate ? BooleanToggleBlockContract.inspect(originals) : null;
            var sourceStaticShapeBlock = staticShapeBlockCandidate ? StaticShapeBlockContract.inspect(originals) : null;
            var sourceHorizontalBlock = horizontalBlockCandidate ? HorizontalBlockContract.inspect(originals) : null;
            var sourcePlainBlock = plainBlockCandidate ? PlainBlockContract.inspect(originals) : null;
            var sourceOwnedStackRepairUse = ownedStackRepairUseCandidate ? OwnedStackRepairUseContract.inspect(originals) : null;
            var sourceOwnedStackDamage = ownedStackDamageCandidate ? OwnedStackDamageContract.inspect(originals) : null;
            var sourceItemAttributeDurability = itemAttributeDurabilityCandidate ? ItemAttributeDurabilityContract.inspect(originals) : null;
            var sourceItemAttributes = itemAttributesCandidate ? ItemAttributeContract.inspect(originals) : null;
            var sourceDurable = durableCandidate ? DurableItemContract.inspect(originals) : null;
            var sourceSuite = suiteCandidate ? LifecycleSuiteContract.inspect(originals) : null;
            var sourceServerConfig = serverConfigCandidate ? ServerConfigContract.inspect(originals) : null;
            var sourceClientConfig = clientConfigCandidate ? ClientConfigContract.inspect(originals) : null;
            var sourceEvent = eventCandidate ? EventPriorityContract.inspect(originals) : null;
            MappingResolver resolver = new MappingResolver(mappings.database(), mappings.adapterBindings(), originals.keySet());
            Map<String, ClassNode> nodes = new TreeMap<>();
            OperationControl.stage(OperationStage.CLASS_REMAP, "Preserving owned bodies and remapping only exact approved symbols");
            var booleanToggleMapped = booleanToggleBlockCandidate ? PlainBlockLowering.remapBooleanToggleBlock(originals,resolver,mappings.plainBlockRecipe(),log) : null;
            var staticShapeMapped = staticShapeBlockCandidate ? PlainBlockLowering.remapStaticShapeBlock(originals,resolver,mappings.plainBlockRecipe(),log) : null;
            var horizontalMapped = horizontalBlockCandidate ? PlainBlockLowering.remapHorizontal(originals,resolver,mappings.plainBlockRecipe(),log) : null;
            for (ClassNode node : originals.values()) {
                OperationControl.checkpoint();
                ClassNode mapped = booleanToggleBlockCandidate ? booleanToggleMapped.get(node.name) : staticShapeBlockCandidate ? staticShapeMapped.get(node.name) : horizontalBlockCandidate ? horizontalMapped.get(node.name) : plainBlockCandidate ? PlainBlockLowering.remap(node, resolver, mappings.plainBlockRecipe(), log)
                        : LoggedClassRemapper.remap(node, resolver, log);
                if (!plainBlockCandidate && !horizontalBlockCandidate && !staticShapeBlockCandidate && !booleanToggleBlockCandidate) LogicBodyPreservation.verify(node, mapped);
                require(nodes.putIfAbsent(mapped.name, mapped) == null, "Mapped class collision");
            }
            // This proves the finite registration/Item override envelope; it does not consume any body.
            var targetHierarchy = hierarchyCandidate ? OwnedItemHierarchy.inspect(nodes, mappings.targetApi(), "net/minecraft/world/item/Item") : null;
            var targetInterface = interfaceCandidate ? OwnedInterfaceDefaults.inspect(nodes) : null;
            var targetReceiver = receiverCandidate ? OwnedReceiverHandles.inspect(nodes) : null;
            var targetBooleanToggleBlock = booleanToggleBlockCandidate ? BooleanToggleBlockContract.inspect(nodes) : null;
            var targetStaticShapeBlock = staticShapeBlockCandidate ? StaticShapeBlockContract.inspect(nodes) : null;
            var targetHorizontalBlock = horizontalBlockCandidate ? HorizontalBlockContract.inspect(nodes) : null;
            var targetPlainBlock = plainBlockCandidate ? PlainBlockContract.inspect(nodes) : null;
            var targetOwnedStackRepairUse = ownedStackRepairUseCandidate ? OwnedStackRepairUseContract.inspect(nodes) : null;
            var targetOwnedStackDamage = ownedStackDamageCandidate ? OwnedStackDamageContract.inspect(nodes) : null;
            var targetItemAttributeDurability = itemAttributeDurabilityCandidate ? ItemAttributeDurabilityContract.inspect(nodes) : null;
            var targetItemAttributes = itemAttributesCandidate ? ItemAttributeContract.inspect(nodes) : null;
            var targetDurable = durableCandidate ? DurableItemContract.inspect(nodes) : null;
            var targetSuite = suiteCandidate ? LifecycleSuiteContract.inspect(nodes) : null;
            var targetServerConfig = serverConfigCandidate ? ServerConfigContract.inspect(nodes) : null;
            var targetClientConfig = clientConfigCandidate ? ClientConfigContract.inspect(nodes) : null;
            var targetEvent = eventCandidate ? EventPriorityContract.inspect(nodes) : null;
            if (booleanToggleBlockCandidate) {
                require(sourceBooleanToggleBlock.equals(targetBooleanToggleBlock),"Boolean toggle state/side/effect graph changed");
                report.put("sourceBooleanToggleBlockContract",sourceBooleanToggleBlock.toTree());
                report.put("booleanToggleBlockContract",targetBooleanToggleBlock.toTree());
                report.put("booleanToggleBlockMappingCommutation","PASSED_EXACT_RULES");
            }
            if (staticShapeBlockCandidate) {
                require(sourceStaticShapeBlock.equals(targetStaticShapeBlock),"Static shape contract changed");
                report.put("sourceStaticShapeBlockContract",sourceStaticShapeBlock.toTree());
                report.put("staticShapeBlockContract",targetStaticShapeBlock.toTree());
                report.put("staticShapeBlockMappingCommutation","PASSED_EXACT_RULES");
            }
            if (horizontalBlockCandidate) {
                require(sourceHorizontalBlock.equals(targetHorizontalBlock),"Horizontal state/placement contract changed");
                report.put("sourceHorizontalBlockContract",sourceHorizontalBlock.toTree());
                report.put("horizontalBlockContract",targetHorizontalBlock.toTree());
                report.put("horizontalBlockMappingCommutation","PASSED_EXACT_RULES");
                report.put("horizontalVariant",targetHorizontalBlock.orientationTransforms() ? "HORIZONTAL_ROTATE_MIRROR" : "HORIZONTAL_FACING");
            }
            if (plainBlockCandidate) {
                require(sourcePlainBlock.equals(targetPlainBlock), "Plain block contract changed during lowering");
                report.put("sourcePlainBlockContract", sourcePlainBlock.toTree());
                report.put("plainBlockContract", targetPlainBlock.toTree());
                report.put("plainBlockMappingCommutation", "PASSED_EXACT_RULES");
            }
            if (ownedStackDamageCandidate) {
                require(sourceOwnedStackDamage.equals(targetOwnedStackDamage), "Guarded owned stack helper changed during remapping");
                report.put("sourceOwnedStackDamageContract",sourceOwnedStackDamage.toTree());
                report.put("ownedStackDamageContract",targetOwnedStackDamage.toTree());
                report.put("ownedStackDamageMappingCommutation","PASSED_EXACT_RULES");
            }
            if (ownedStackRepairUseCandidate) {
                require(sourceOwnedStackRepairUse.equals(targetOwnedStackRepairUse), "Same-hand guarded native item use changed during remapping");
                report.put("sourceOwnedStackRepairUseContract",sourceOwnedStackRepairUse.toTree());
                report.put("ownedStackRepairUseContract",targetOwnedStackRepairUse.toTree());
                report.put("ownedStackRepairUseMappingCommutation","PASSED_EXACT_RULES");
            }
            if (itemAttributeDurabilityCandidate) {
                require(sourceItemAttributeDurability.equals(targetItemAttributeDurability), "Attribute/durability composition changed during remapping");
                report.put("sourceItemAttributeDurabilityContract",sourceItemAttributeDurability.toTree());
                report.put("itemAttributeDurabilityContract",targetItemAttributeDurability.toTree());
                report.put("itemAttributeDurabilityMappingCommutation","PASSED_EXACT_RULES");
            }
            if (itemAttributesCandidate) {
                require(sourceItemAttributes.equals(targetItemAttributes), "Owned attributes contract changed during remapping");
                report.put("sourceItemAttributeContract",sourceItemAttributes.toTree());
                report.put("itemAttributeContract",targetItemAttributes.toTree());
                report.put("itemAttributeMappingCommutation","PASSED_EXACT_RULES");
            }
            if (durableCandidate) {
                require(sourceDurable.equals(targetDurable), "Durability/repair contract changed during remapping");
                report.put("sourceDurableItemContract", sourceDurable.toTree());
                report.put("durableItemContract", targetDurable.toTree());
                report.put("durableItemMappingCommutation", "PASSED_EXACT_RULES");
            }
            if (suiteCandidate) {
                require(sourceSuite.equals(targetSuite), "Integrated lifecycle contract changed during remapping");
                report.put("sourceLifecycleSuiteContract", sourceSuite.toTree());
                report.put("lifecycleSuiteContract", targetSuite.toTree());
                report.put("lifecycleSuiteMappingCommutation", "PASSED_EXACT_RULES");
            }
            if (serverConfigCandidate) {
                require(sourceServerConfig.equals(targetServerConfig), "SERVER config schema/lifecycle contract changed during remapping");
                report.put("sourceServerConfigContract", sourceServerConfig.toTree());
                report.put("serverConfigContract", targetServerConfig.toTree());
                report.put("serverConfigMappingCommutation", "PASSED_EXACT_RULES");
            }
            if (clientConfigCandidate) {
                require(sourceClientConfig.equals(targetClientConfig), "CLIENT config side/schema/phase contract changed during remapping");
                report.put("sourceClientConfigContract", sourceClientConfig.toTree());
                report.put("clientConfigContract", targetClientConfig.toTree());
                report.put("clientConfigMappingCommutation", "PASSED_EXACT_RULES");
            }
            if (eventCandidate) {
                // All roles, callback descriptors and external event API identities stay exact.
                require(sourceEvent.equals(targetEvent), "Owned event effect graph changed during remapping");
                report.put("sourceEventContract", sourceEvent.toTree()); report.put("eventContract", targetEvent.toTree());
                report.put("eventMappingCommutation", "PASSED_EXACT_RULES");
            }
            if (receiverCandidate) {
                require(sourceReceiver.remap(resolver).equals(targetReceiver), "Owned receiver contract changed under exact remapping");
                report.put("sourceOwnedReceiver", sourceReceiver.toTree()); report.put("ownedReceiver", targetReceiver.toTree());
                report.put("receiverMappingCommutation", "PASSED_EXACT_RULES");
            }
            if (interfaceCandidate) {
                require(sourceInterface.remap(resolver).equals(targetInterface), "Owned interface/default dispatch changed under exact remapping");
                report.put("sourceOwnedInterface", sourceInterface.toTree()); report.put("ownedInterface", targetInterface.toTree());
                report.put("interfaceMappingCommutation", "PASSED_EXACT_RULES");
            }
            if (hierarchyCandidate) {
                require(sourceHierarchy.remap(resolver).equals(targetHierarchy), "Owned hierarchy/dispatch selection changed under exact remapping");
                report.put("sourceOwnedHierarchy", sourceHierarchy.toTree()); report.put("ownedHierarchy", targetHierarchy.toTree());
                report.put("hierarchyMappingCommutation", "PASSED_EXACT_RULES");
            }
            ContentPlan resourcePlan;
            if (network) {
                resourcePlan = new ContentPlan(mod.modId(), version, List.of(), List.of(), List.of());
                report.put("validatedSlices", List.of("reviewed-explicit-s2c-channel-v1", "fixed-int-codec-v1",
                        "strict-ordered-connection-snapshot-v1", "retained-game-thread-enqueue-v1"));
            } else if (food) {
                var admitted = FoodProfileShape.validate(nodes, mod.modId(), version);
                resourcePlan = admitted.resources();
                report.put("foodProperties", admitted.foodTree());
                report.put("validatedSlices", List.of("deferred-item-registration-v1", "literal-native-food-v1"));
            } else resourcePlan = ownedStackRepairUseCandidate ? OwnedStackRepairUseContract.validate(nodes,mod.modId(),version) : ownedStackDamageCandidate ? OwnedStackDamageContract.validate(nodes,mod.modId(),version) : itemAttributeDurabilityCandidate ? ItemAttributeDurabilityContract.validate(nodes,mod.modId(),version) : itemAttributesCandidate ? ItemAttributeContract.validate(nodes,mod.modId(),version) : booleanToggleBlockCandidate ? BooleanToggleBlockContract.validate(nodes,mod.modId(),version) : staticShapeBlockCandidate ? StaticShapeBlockContract.validate(nodes,mod.modId(),version) : horizontalBlockCandidate ? HorizontalBlockContract.validate(nodes,mod.modId(),version) : plainBlockCandidate ? PlainBlockContract.validate(nodes, mod.modId(), version) : durableCandidate ? DurableItemContract.validate(nodes, mod.modId(), version) : suiteCandidate ? LifecycleSuiteContract.validate(nodes, mod.modId(), version) : serverConfigCandidate ? ServerConfigContract.validate(nodes, mod.modId(), version) : clientConfigCandidate ? ClientConfigContract.validate(nodes, mod.modId(), version) : eventCandidate ? EventPriorityContract.validate(nodes, mod.modId(), version) : hierarchyCandidate ? LogicProfileShape.validateOwnedHierarchy(nodes, mod.modId(), version, targetHierarchy)
                    : constructors ? LogicProfileShape.validateOwnedConstructors(nodes, mod.modId(), version)
                    : lifecycle ? LifecycleProfileShape.validate(nodes, mod.modId(), version) : LogicProfileShape.validate(nodes, mod.modId(), version);
            if (constructors) {
                var proof = LogicProfileShape.constructorProof(nodes).toTree();
                require(proof.equals(report.get("sourceOwnedConstructorProof")), "Constructor delegation or resolved arguments changed during remapping");
                report.put("ownedConstructorProof", proof);
            }
            if (horizontalBlockCandidate) {
                var slices = new ArrayList<>(List.of("preserved-owned-horizontal-block-v1","native-state-definition-v1",
                        "native-opposite-horizontal-placement-v1","four-preserved-facing-variants-v1","target-pickaxe-tag-v1"));
                if(targetHorizontalBlock.orientationTransforms()) slices.add("native-horizontal-rotation-mirror-pair-v1");
                report.put("validatedSlices",List.copyOf(slices));
            }
            if (booleanToggleBlockCandidate) report.put("validatedSlices",List.of("preserved-owned-boolean-toggle-v1","native-two-state-definition-v1","logical-client-no-world-write-v1","single-callback-position-server-write-v1","two-matching-visual-variants-v1","target-pickaxe-tag-v1"));
            if (staticShapeBlockCandidate) report.put("validatedSlices",List.of("preserved-owned-static-bottom-half-shape-v1","exact-outline-and-collision-overrides-v1","matching-half-block-model-v1","half-shape-stone-properties-v1","target-pickaxe-tag-v1"));
            if (plainBlockCandidate) report.put("validatedSlices", List.of("preserved-native-block-and-blockitem-v1", "stone-properties-expansion-v1", "target-pickaxe-tag-v1"));
            if (ownedStackRepairUseCandidate) report.put("validatedSlices",List.of("owned-stack-repair-use-v1",
                    "native-durability-128-stack-one-v1","same-stack-own-item-identity-guard-v1",
                    "native-damage-hook-dispatch-v1","positive-bounded-damage-minus-one-v1",
                    "single-received-hand-lookup-v1","client-success-without-mutation-v1",
                    "server-this-helper-consume-or-pass-v1","same-stack-all-result-holders-v1"));
            if (ownedStackDamageCandidate) report.put("validatedSlices",List.of("owned-stack-damage-helper-v1",
                    "native-durability-128-stack-one-v1","same-stack-own-item-identity-guard-v1",
                    "native-damage-hook-dispatch-v1","positive-bounded-damage-minus-one-v1","no-automatic-gameplay-activation-v1"));
            if (itemAttributeDurabilityCandidate) report.put("validatedSlices",List.of("owned-attribute-durability-composition-v1",
                    "owned-mainhand-immutable-modifiers-v1","native-inherited-uuid-object-identity-v1","explicit-guava-builder-abi-v1",
                    "native-durability-128-stack-one-v1","native-iron-repair-predicate-v1","preserved-two-native-overrides-v1"));
            if (itemAttributesCandidate) report.put("validatedSlices",List.of("owned-mainhand-immutable-modifiers-v1",
                    "native-inherited-uuid-object-identity-v1","explicit-guava-builder-abi-v1","native-item-attribute-override-v1"));
            if (durableCandidate) report.put("validatedSlices", List.of("native-positive-durability-stack-one-v1", "native-iron-repair-predicate-v1"));
            if (eventCandidate) report.put("validatedSlices", List.of("owned-cancelable-event-v1", "explicit-high-low-listeners-v1", "queued-common-setup-post-v1"));
            if (suiteCandidate) report.put("validatedSlices", List.of("late-registry-reference-v1", "native-common-loading-reloading-v1", "owned-event-priority-cancellation-v1", "typed-server-commands-v1"));
            if (serverConfigCandidate) report.put("validatedSlices", List.of("native-server-config-v1", "native-boolean-range-schema-v1", "server-started-snapshot-stopped-cleanup-v1"));
            if (clientConfigCandidate) report.put("validatedSlices", List.of("guarded-client-config-v1", "native-boolean-range-schema-v1", "queued-loaded-client-snapshot-v1"));
            if (lifecycle && !eventCandidate && !clientConfigCandidate && !serverConfigCandidate && !suiteCandidate) report.put("validatedSlices", List.of("deferred-item-registration-v1", "late-owned-item-reference-v1",
                    "common-setup-listener-v1", "static-forge-command-subscriber-v1", "common-boolean-integer-config-v1", "bounded-integer-server-command-v1"));
            OperationControl.stage(OperationStage.RESOURCE_VALIDATION, "Validating the unchanged finite item resource language");
            CompositionPlan composition = null;
            if (preview == null) ContentResourceValidator.validate(captured.entries, resourcePlan);
            else {
                require(captured.entries.keySet().stream().anyMatch(p -> p.startsWith("data/") && p.contains("/recipes/") && p.endsWith(".json")),
                        "Composition preview requires at least one recipe from the existing datapack contract");
                // Resolve ownership/effect conflicts first. No bootstrap is selected or invoked.
                composition = new CompositionPlan(List.of(
                        new CompositionPlan.Operation("logic.owned-bodies", CompositionPlan.Kind.PRESERVE_OWNED_BODIES,
                                Set.of(), source.entrySet().stream().map(e -> new CompositionPlan.Claim(e.getKey(), SimpleContentConverter.hash(e.getValue()))).toList()),
                        new CompositionPlan.Operation("data.recipe-resources", CompositionPlan.Kind.PRESERVE_RECIPE_RESOURCES,
                                Set.of("logic.owned-bodies"), captured.entries.entrySet().stream()
                                .filter(e -> e.getKey().startsWith("assets/") || e.getKey().startsWith("data/"))
                                .map(e -> new CompositionPlan.Claim(e.getKey(), SimpleContentConverter.hash(e.getValue()))).toList())));
                var recipes = booleanToggleBlockCandidate ? ContentResourceValidator.validateBooleanToggleRecipes(captured.entries,resourcePlan) : staticShapeBlockCandidate ? ContentResourceValidator.validateStaticShapeRecipes(captured.entries,resourcePlan) : horizontalBlockCandidate ? ContentResourceValidator.validateHorizontalRecipes(captured.entries,resourcePlan)
                        : ContentResourceValidator.validateDatapackRecipes(captured.entries, resourcePlan);
                require(!recipes.isEmpty(), "Composition preview requires at least one recipe from the existing datapack contract");
                report.put("resourceValidation", DatapackRecipeContract.report("PASSED_STATIC", recipes));
                if(staticShapeBlockCandidate) {
                    var loot=StaticShapeLootContract.inspect(captured.entries,resourcePlan);
                    report.put("staticShapeVariant",loot.isPresent()?"STATIC_BOTTOM_HALF_SHAPE_SELF_DROP":"STATIC_BOTTOM_HALF_SHAPE");
                    loot.ifPresent(proof -> report.put("staticShapeLootContract",proof));
                    if(loot.isPresent()) {
                        @SuppressWarnings("unchecked") var slices=new ArrayList<>((List<String>)report.get("validatedSlices"));
                        slices.add("byte-preserved-local-self-drop-v1");
                        report.put("validatedSlices",List.copyOf(slices));
                    }
                }
            }
            Map<String, byte[]> hierarchy = new HashMap<>(OfflineLinkageVerifier.declarationHierarchy(mappings.targetApi()));
            // Seed owned headers before frame merging, never define these classes.
            for (var node : nodes.values()) { ClassWriter writer = new ClassWriter(0); node.accept(writer); hierarchy.put(node.name, writer.toByteArray()); }
            Map<String, byte[]> classes = new TreeMap<>();
            for (var node : nodes.values()) {
                OperationControl.checkpoint();
                var writer = new OfflineClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, hierarchy);
                node.accept(writer); classes.put(node.name + ".class", writer.toByteArray());
            }
            classes.forEach((name, bytes) -> hierarchy.put(name.substring(0, name.length() - 6), bytes));
            OperationControl.stage(OperationStage.BYTECODE_VALIDATION, "Validating preserved output dataflow, linkage and access");
            for (byte[] bytes : classes.values()) BytecodeValidator.validate(bytes, hierarchy);
            var targetLinkage = receiverCandidate ? new OfflineLinkageVerifier().verifyOwnedReceiverHandles(classes, mappings.targetApi())
                    : interfaceCandidate ? new OfflineLinkageVerifier().verifyOwnedInterfaceDefaults(classes, mappings.targetApi())
                    : constructors ? new OfflineLinkageVerifier().verifyOwnedConstructors(classes, mappings.targetApi(), constructorOwner)
                    : new OfflineLinkageVerifier().verify(classes, mappings.targetApi(), callbackPolicy);
            report.put("targetOfflineLinkage", targetLinkage.toTree());
            // Retain every source class and every executable method; no bootstrap or declarative body substitute.
            require(classes.keySet().equals(source.keySet()), "Class loss or renaming is forbidden in the logic-preserving profile");
            if (preview != null) {
                report.put("bytecodeModified", false); report.put("output", null); report.put("candidate", null);
                if (preview.serializeCandidate) {
                    byte[] candidateBytes = CompositionCandidateBuilder.inspect(captured.entries, classes, composition,
                            resourcePlan, mappings.targetApi(), bindings, log, report, constructorOwner, targetHierarchy, targetInterface, targetReceiver, targetEvent, targetClientConfig, targetServerConfig, targetSuite, targetDurable, targetPlainBlock, targetHorizontalBlock, targetStaticShapeBlock, targetBooleanToggleBlock, targetItemAttributes, targetItemAttributeDurability, targetOwnedStackDamage, targetOwnedStackRepairUse);
                    if (preview.retainCandidate) preview.bytes = candidateBytes;
                }
                report.put("status", preview.serializeCandidate ? "CANDIDATE_VALIDATED" : "PREVIEW_VALIDATED");
                report.put("compatibilityStatus", preview.serializeCandidate ? "STATIC_CANDIDATE_ONLY" : "STATIC_INSPECTION_ONLY");
                report.put("preservedClassCount", source.size()); report.put("replacedClassCount", 0);
                report.put("preservedMethodCount", originals.values().stream().mapToInt(n -> n.methods.size()).sum());
                report.put("bodyPreservation", plainBlockCandidate || horizontalBlockCandidate || staticShapeBlockCandidate || booleanToggleBlockCandidate ? "CHECKED_EXCEPT_EXACT_LOGGED_STONE_PAIR_EXPANSION" : "CHECKED_OPCODES_CONTROL_FLOW_LITERALS_FIELD_CONSTANTS_AND_DEBUG");
                report.put("inspectedTargetClasses", classes.entrySet().stream().map(e -> Map.of("path", e.getKey(), "sha256", SimpleContentConverter.hash(e.getValue()))).toList());
                report.put("bytecodeValidation", Map.of("status", preview.serializeCandidate ? "PASSED_STATIC_REOPENED" : "PASSED_STATIC_IN_MEMORY", "inputClasses", source.size(),
                        "outputClasses", classes.size(), "modClassesLoaded", false, "offlineLinkageVerified", true,
                        "jvmLinkageVerified", false, "serializedJar", preview.serializeCandidate));
                report.put("operations", composition.toTree()); report.put("transformations", bindings.report());
                report.put("adapterBindings", bindings.bindingTree()); report.put("transformationLog", log.toTree());
                report.put("transformationsCommitted", false);
                report.put("diagnostics", List.of(preview.serializeCandidate
                        ? "Private serialized candidate validated and discarded. No publication, installation or runtime acceptance."
                        : "Static composition preview only: no serialized JAR, metadata migration, installation or runtime acceptance."));
                OperationControl.stage(OperationStage.PREPARED, "Static composition inspected; publication remains disabled");
                OperationControl.checkpoint();
                preview.admitted = composition;
                return new SimpleContentConverter.PreparedConversion(input.toAbsolutePath().normalize(), destination,
                        archive.input().sha256(), null, SimpleContentConverter.canonicalTree(report));
            }
            Map<String, byte[]> target = new TreeMap<>(captured.entries);
            target.putAll(classes);
            target.put("META-INF/mods.toml", lifecycle || food ? LifecycleMetadata.rewrite(captured.entries.get("META-INF/mods.toml"), mod.modId(), version)
                    : SimpleContentConverter.targetMetadata(captured.entries.get("META-INF/mods.toml"), mod.modId(), version));
            if (lifecycle || food) LifecycleMetadata.verify(target.get("META-INF/mods.toml"), version, true, report);
            target.put("pack.mcmeta", SimpleContentConverter.targetPack(captured.entries.get("pack.mcmeta")));
            target.put("META-INF/MANIFEST.MF", network ? SnapshotContractAdapter.targetManifest(version) : food ? FoodContractAdapter.targetManifest(version) : lifecycle ? dev.legacybridge.runtime.compat.logic.LifecycleContractAdapter.targetManifest(version) : PreservedLogicAdapter.targetManifest(version));
            target.put("META-INF/legacybridge/" + mod.modId() + "/adapters.json", new CanonicalReportWriter()
                    .toJsonTree(SimpleContentConverter.canonicalTree(network ? SnapshotContractAdapter.bindingTree() : food ? FoodContractAdapter.bindingTree() : lifecycle ? dev.legacybridge.runtime.compat.logic.LifecycleContractAdapter.bindingTree() : PreservedLogicAdapter.bindingTree())).getBytes(StandardCharsets.UTF_8));
            Map<String, String> resourceRules = Map.of("META-INF/mods.toml", AdapterCatalog.METADATA,
                    "pack.mcmeta", AdapterCatalog.PACK, "META-INF/MANIFEST.MF", network ? AdapterCatalog.NETWORK_MANIFEST : food ? AdapterCatalog.FOOD_MANIFEST : lifecycle ? AdapterCatalog.LIFECYCLE_MANIFEST : AdapterCatalog.LOGIC_MANIFEST,
                    "META-INF/legacybridge/" + mod.modId() + "/adapters.json", network ? AdapterCatalog.NETWORK_BINDINGS : food ? AdapterCatalog.FOOD_BINDINGS : lifecycle ? AdapterCatalog.LIFECYCLE_BINDINGS : AdapterCatalog.LOGIC_BINDINGS);
            for (String path : target.keySet()) {
                if (path.endsWith(".class") || Arrays.equals(captured.entries.get(path), target.get(path))) continue;
                String adapter = resourceRules.get(path);
                require(adapter != null, "Resource rewrite has no exact implemented operation: " + path);
                log.add(TransformationLog.Kind.RESOURCE_REWRITE, path, fingerprint(captured.entries.get(path)), fingerprint(target.get(path)),
                        adapter + ":revision-1", MappingConfidence.VERIFIED, bindings.require(adapter).transformation(), adapter);
            }
            // Validate the exact candidate class set immediately before serialization, not a surrogate/stub set.
            new OfflineLinkageVerifier().verify(classes, mappings.targetApi(), callbackPolicy);
            OperationControl.stage(OperationStage.SERIALIZATION, "Serializing private body-preserving candidate");
            byte[] jar = SimpleContentConverter.jar(target);
            report.put("status", "PLANNED"); report.put("compatibilityStatus", "SUPPORTED_FIXTURE_PROFILE");
            report.put("bytecodeModified", false); report.put("output", null);
            report.put("candidate", Map.of("fileName", output.getFileName().toString(), "sha256", SimpleContentConverter.hash(jar)));
            report.put("preservedClassCount", source.size()); report.put("replacedClassCount", 0);
            report.put("preservedMethodCount", originals.values().stream().mapToInt(node -> node.methods.size()).sum());
            report.put("bodyPreservation", "CHECKED_OPCODES_CONTROL_FLOW_LITERALS_FIELD_CONSTANTS_AND_DEBUG");
            report.put("itemCount", network ? 0 : 1); report.put("blockCount", 0); report.put("soundCount", 0);
            report.put("preservedResources", captured.entries.entrySet().stream().filter(entry -> !entry.getKey().endsWith(".class") && Arrays.equals(entry.getValue(), target.get(entry.getKey())))
                    .map(entry -> Map.of("path", entry.getKey(), "sha256", SimpleContentConverter.hash(entry.getValue()))).toList());
            report.put("transformations", bindings.report()); report.put("adapterBindings", bindings.bindingTree());
            report.put("transformationLog", log.toTree()); report.put("transformationsCommitted", false);
            report.put("bytecodeValidation", Map.of("status", "PASSED", "inputClasses", source.size(), "outputClasses", classes.size(),
                    "modClassesLoaded", false, "jvmLinkageVerified", false, "offlineLinkageVerified", true,
                    "checks", List.of("CheckClassAdapter dataflow", "offline reference assignability", "source and target member/access/hierarchy/constructor/handle checks")));
            report.put("diagnostics", List.of("Owned logic is preserved under a finite external contract allowlist, not arbitrary Forge behavior. Runtime and multiplayer validation remain separate."));
            OperationControl.stage(OperationStage.PREPARED, "Candidate is private; no activation or runtime execution occurred");
            return new SimpleContentConverter.PreparedConversion(input.toAbsolutePath().normalize(), destination, archive.input().sha256(), jar, SimpleContentConverter.canonicalTree(report));
        } catch (SubsetRejectionException | TransformationException | UnsupportedCompatibilityException refused) {
            report.put("status", "ABORTED"); report.put("compatibilityStatus", "UNSUPPORTED"); report.put("bytecodeModified", false);
            report.put("output", null); report.put("transformations", List.of()); report.put("transformationLog", log.toTree());
            report.put("transformationsCommitted", false); report.put("diagnostics", List.of(refused.getMessage()));
            return new SimpleContentConverter.PreparedConversion(input.toAbsolutePath().normalize(), destination, archive.input().sha256(), null, SimpleContentConverter.canonicalTree(report));
        }
    }
    private static String fingerprint(byte[] bytes) { return bytes == null ? "<absent>" : "sha256:" + SimpleContentConverter.hash(bytes) + " bytes=" + bytes.length; }
    private static void require(boolean condition, String reason) { if (!condition) throw new TransformationException(reason); }
}
