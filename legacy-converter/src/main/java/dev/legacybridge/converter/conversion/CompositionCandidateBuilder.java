package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.archive.*;
import dev.legacybridge.converter.conversion.composition.CompositionPlan;
import dev.legacybridge.converter.conversion.extract.*;
import dev.legacybridge.converter.mapping.MappingConfidence;
import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.transformation.*;
import dev.legacybridge.converter.transformation.linkage.OfflineLinkageVerifier;
import dev.legacybridge.runtime.compat.logic.ComposedArtifactAdapter;
import dev.legacybridge.runtime.compat.logic.ConstructorArtifactAdapter;
import dev.legacybridge.runtime.compat.logic.HierarchyArtifactAdapter;
import dev.legacybridge.converter.transformation.linkage.OwnedItemHierarchy;
import dev.legacybridge.converter.transformation.linkage.OwnedInterfaceDefaults;
import dev.legacybridge.runtime.compat.logic.InterfaceArtifactAdapter;
import dev.legacybridge.runtime.compat.logic.ReceiverArtifactAdapter;
import dev.legacybridge.runtime.compat.logic.EventArtifactAdapter;
import dev.legacybridge.runtime.compat.logic.ClientConfigArtifactAdapter;
import dev.legacybridge.runtime.compat.logic.ServerConfigArtifactAdapter;
import dev.legacybridge.runtime.compat.logic.LifecycleSuiteAdapter;
import dev.legacybridge.runtime.compat.logic.DurableItemArtifactAdapter;
import dev.legacybridge.converter.transformation.linkage.OwnedReceiverHandles;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Closed candidate assembly. Every original byte is accounted for before the private archive is written. */
final class CompositionCandidateBuilder {
    private CompositionCandidateBuilder() { }

    static byte[] inspect(Map<String, byte[]> source, Map<String, byte[]> classes, CompositionPlan base,
                        ContentPlan content, ApiIndex targetApi, TransformationBindings bindings,
                        TransformationLog log, Map<String, Object> report) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source, classes, base, content, targetApi, bindings, log, report, null);
    }
    static byte[] inspect(Map<String, byte[]> source, Map<String, byte[]> classes, CompositionPlan base,
                        ContentPlan content, ApiIndex targetApi, TransformationBindings bindings,
                        TransformationLog log, Map<String, Object> report, String constructorOwner) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source, classes, base, content, targetApi, bindings, log, report, constructorOwner, null);
    }
    static byte[] inspect(Map<String, byte[]> source, Map<String, byte[]> classes, CompositionPlan base,
                        ContentPlan content, ApiIndex targetApi, TransformationBindings bindings,
                        TransformationLog log, Map<String, Object> report, String constructorOwner,
                        OwnedItemHierarchy.Proof hierarchyProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source, classes, base, content, targetApi, bindings, log, report, constructorOwner, hierarchyProof, null);
    }
    static byte[] inspect(Map<String, byte[]> source, Map<String, byte[]> classes, CompositionPlan base,
                        ContentPlan content, ApiIndex targetApi, TransformationBindings bindings,
                        TransformationLog log, Map<String, Object> report, String constructorOwner,
                        OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source, classes, base, content, targetApi, bindings, log, report, constructorOwner, hierarchyProof, interfaceProof, null);
    }
    static byte[] inspect(Map<String, byte[]> source, Map<String, byte[]> classes, CompositionPlan base,
                        ContentPlan content, ApiIndex targetApi, TransformationBindings bindings,
                        TransformationLog log, Map<String, Object> report, String constructorOwner,
                        OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof,
                        OwnedReceiverHandles.Proof receiverProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source, classes, base, content, targetApi, bindings, log, report, constructorOwner, hierarchyProof, interfaceProof, receiverProof, null);
    }
    static byte[] inspect(Map<String, byte[]> source, Map<String, byte[]> classes, CompositionPlan base,
                        ContentPlan content, ApiIndex targetApi, TransformationBindings bindings,
                        TransformationLog log, Map<String, Object> report, String constructorOwner,
                        OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof,
                        OwnedReceiverHandles.Proof receiverProof, EventPriorityContract.Proof eventProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source, classes, base, content, targetApi, bindings, log, report, constructorOwner, hierarchyProof, interfaceProof, receiverProof, eventProof, null);
    }
    static byte[] inspect(Map<String, byte[]> source, Map<String, byte[]> classes, CompositionPlan base,
                        ContentPlan content, ApiIndex targetApi, TransformationBindings bindings,
                        TransformationLog log, Map<String, Object> report, String constructorOwner,
                        OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof,
                        OwnedReceiverHandles.Proof receiverProof, EventPriorityContract.Proof eventProof,
                        ClientConfigContract.Proof clientConfigProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source, classes, base, content, targetApi, bindings, log, report, constructorOwner, hierarchyProof, interfaceProof, receiverProof, eventProof, clientConfigProof, null);
    }
    static byte[] inspect(Map<String, byte[]> source, Map<String, byte[]> classes, CompositionPlan base,
                        ContentPlan content, ApiIndex targetApi, TransformationBindings bindings,
                        TransformationLog log, Map<String, Object> report, String constructorOwner,
                        OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof,
                        OwnedReceiverHandles.Proof receiverProof, EventPriorityContract.Proof eventProof,
                        ClientConfigContract.Proof clientConfigProof, ServerConfigContract.Proof serverConfigProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source, classes, base, content, targetApi, bindings, log, report, constructorOwner, hierarchyProof, interfaceProof, receiverProof, eventProof, clientConfigProof, serverConfigProof, null);
    }
    static byte[] inspect(Map<String, byte[]> source, Map<String, byte[]> classes, CompositionPlan base,
                        ContentPlan content, ApiIndex targetApi, TransformationBindings bindings,
                        TransformationLog log, Map<String, Object> report, String constructorOwner,
                        OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof,
                        OwnedReceiverHandles.Proof receiverProof, EventPriorityContract.Proof eventProof,
                        ClientConfigContract.Proof clientConfigProof, ServerConfigContract.Proof serverConfigProof, LifecycleSuiteContract.Proof suiteProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source, classes, base, content, targetApi, bindings, log, report, constructorOwner, hierarchyProof, interfaceProof, receiverProof, eventProof, clientConfigProof, serverConfigProof, suiteProof, null);
    }
    static byte[] inspect(Map<String, byte[]> source, Map<String, byte[]> classes, CompositionPlan base,
                        ContentPlan content, ApiIndex targetApi, TransformationBindings bindings,
                        TransformationLog log, Map<String, Object> report, String constructorOwner,
                        OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof,
                        OwnedReceiverHandles.Proof receiverProof, EventPriorityContract.Proof eventProof,
                        ClientConfigContract.Proof clientConfigProof, ServerConfigContract.Proof serverConfigProof, LifecycleSuiteContract.Proof suiteProof, DurableItemContract.Proof durableProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source,classes,base,content,targetApi,bindings,log,report,constructorOwner,hierarchyProof,interfaceProof,receiverProof,eventProof,clientConfigProof,serverConfigProof,suiteProof,durableProof,null);
    }
    static byte[] inspect(Map<String,byte[]> source, Map<String,byte[]> classes, CompositionPlan base,
            ContentPlan content, ApiIndex targetApi, TransformationBindings bindings, TransformationLog log, Map<String,Object> report,
            String constructorOwner, OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof,
            OwnedReceiverHandles.Proof receiverProof, EventPriorityContract.Proof eventProof, ClientConfigContract.Proof clientConfigProof,
            ServerConfigContract.Proof serverConfigProof, LifecycleSuiteContract.Proof suiteProof, DurableItemContract.Proof durableProof,
            PlainBlockContract.Proof plainBlockProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source,classes,base,content,targetApi,bindings,log,report,constructorOwner,hierarchyProof,interfaceProof,
                receiverProof,eventProof,clientConfigProof,serverConfigProof,suiteProof,durableProof,plainBlockProof,null);
    }
    static byte[] inspect(Map<String,byte[]> source, Map<String,byte[]> classes, CompositionPlan base,
            ContentPlan content, ApiIndex targetApi, TransformationBindings bindings, TransformationLog log, Map<String,Object> report,
            String constructorOwner, OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof,
            OwnedReceiverHandles.Proof receiverProof, EventPriorityContract.Proof eventProof, ClientConfigContract.Proof clientConfigProof,
            ServerConfigContract.Proof serverConfigProof, LifecycleSuiteContract.Proof suiteProof, DurableItemContract.Proof durableProof,
            PlainBlockContract.Proof plainBlockProof, HorizontalBlockContract.Proof horizontalBlockProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source,classes,base,content,targetApi,bindings,log,report,constructorOwner,hierarchyProof,interfaceProof,
                receiverProof,eventProof,clientConfigProof,serverConfigProof,suiteProof,durableProof,plainBlockProof,horizontalBlockProof,null);
    }
    static byte[] inspect(Map<String,byte[]> source, Map<String,byte[]> classes, CompositionPlan base,
            ContentPlan content, ApiIndex targetApi, TransformationBindings bindings, TransformationLog log, Map<String,Object> report,
            String constructorOwner, OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof,
            OwnedReceiverHandles.Proof receiverProof, EventPriorityContract.Proof eventProof, ClientConfigContract.Proof clientConfigProof,
            ServerConfigContract.Proof serverConfigProof, LifecycleSuiteContract.Proof suiteProof, DurableItemContract.Proof durableProof,
            PlainBlockContract.Proof plainBlockProof, HorizontalBlockContract.Proof horizontalBlockProof, StaticShapeBlockContract.Proof staticShapeProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source,classes,base,content,targetApi,bindings,log,report,constructorOwner,hierarchyProof,interfaceProof,
                receiverProof,eventProof,clientConfigProof,serverConfigProof,suiteProof,durableProof,plainBlockProof,horizontalBlockProof,staticShapeProof,null);
    }
    static byte[] inspect(Map<String,byte[]> source, Map<String,byte[]> classes, CompositionPlan base,
            ContentPlan content, ApiIndex targetApi, TransformationBindings bindings, TransformationLog log, Map<String,Object> report,
            String constructorOwner, OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof,
            OwnedReceiverHandles.Proof receiverProof, EventPriorityContract.Proof eventProof, ClientConfigContract.Proof clientConfigProof,
            ServerConfigContract.Proof serverConfigProof, LifecycleSuiteContract.Proof suiteProof, DurableItemContract.Proof durableProof,
            PlainBlockContract.Proof plainBlockProof, HorizontalBlockContract.Proof horizontalBlockProof, StaticShapeBlockContract.Proof staticShapeProof, BooleanToggleBlockContract.Proof booleanToggleProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source,classes,base,content,targetApi,bindings,log,report,constructorOwner,hierarchyProof,interfaceProof,
                receiverProof,eventProof,clientConfigProof,serverConfigProof,suiteProof,durableProof,plainBlockProof,horizontalBlockProof,staticShapeProof,booleanToggleProof,null);
    }
    static byte[] inspect(Map<String,byte[]> source, Map<String,byte[]> classes, CompositionPlan base,
            ContentPlan content, ApiIndex targetApi, TransformationBindings bindings, TransformationLog log, Map<String,Object> report,
            String constructorOwner, OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof,
            OwnedReceiverHandles.Proof receiverProof, EventPriorityContract.Proof eventProof, ClientConfigContract.Proof clientConfigProof,
            ServerConfigContract.Proof serverConfigProof, LifecycleSuiteContract.Proof suiteProof, DurableItemContract.Proof durableProof,
            PlainBlockContract.Proof plainBlockProof, HorizontalBlockContract.Proof horizontalBlockProof, StaticShapeBlockContract.Proof staticShapeProof, BooleanToggleBlockContract.Proof booleanToggleProof, ItemAttributeContract.Proof itemAttributeProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source,classes,base,content,targetApi,bindings,log,report,constructorOwner,hierarchyProof,interfaceProof,
                receiverProof,eventProof,clientConfigProof,serverConfigProof,suiteProof,durableProof,plainBlockProof,horizontalBlockProof,staticShapeProof,booleanToggleProof,itemAttributeProof,null);
    }
    static byte[] inspect(Map<String,byte[]> source, Map<String,byte[]> classes, CompositionPlan base,
            ContentPlan content, ApiIndex targetApi, TransformationBindings bindings, TransformationLog log, Map<String,Object> report,
            String constructorOwner, OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof,
            OwnedReceiverHandles.Proof receiverProof, EventPriorityContract.Proof eventProof, ClientConfigContract.Proof clientConfigProof,
            ServerConfigContract.Proof serverConfigProof, LifecycleSuiteContract.Proof suiteProof, DurableItemContract.Proof durableProof,
            PlainBlockContract.Proof plainBlockProof, HorizontalBlockContract.Proof horizontalBlockProof, StaticShapeBlockContract.Proof staticShapeProof,
            BooleanToggleBlockContract.Proof booleanToggleProof, ItemAttributeContract.Proof itemAttributeProof,
            ItemAttributeDurabilityContract.Proof itemAttributeDurabilityProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source,classes,base,content,targetApi,bindings,log,report,constructorOwner,hierarchyProof,interfaceProof,
                receiverProof,eventProof,clientConfigProof,serverConfigProof,suiteProof,durableProof,plainBlockProof,horizontalBlockProof,
                staticShapeProof,booleanToggleProof,itemAttributeProof,itemAttributeDurabilityProof,null);
    }
    static byte[] inspect(Map<String,byte[]> source, Map<String,byte[]> classes, CompositionPlan base,
            ContentPlan content, ApiIndex targetApi, TransformationBindings bindings, TransformationLog log, Map<String,Object> report,
            String constructorOwner, OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof,
            OwnedReceiverHandles.Proof receiverProof, EventPriorityContract.Proof eventProof, ClientConfigContract.Proof clientConfigProof,
            ServerConfigContract.Proof serverConfigProof, LifecycleSuiteContract.Proof suiteProof, DurableItemContract.Proof durableProof,
            PlainBlockContract.Proof plainBlockProof, HorizontalBlockContract.Proof horizontalBlockProof, StaticShapeBlockContract.Proof staticShapeProof,
            BooleanToggleBlockContract.Proof booleanToggleProof, ItemAttributeContract.Proof itemAttributeProof,
            ItemAttributeDurabilityContract.Proof itemAttributeDurabilityProof, OwnedStackDamageContract.Proof ownedStackDamageProof) throws IOException, InputRejectedException, SubsetRejectionException {
        return inspect(source,classes,base,content,targetApi,bindings,log,report,constructorOwner,hierarchyProof,interfaceProof,
                receiverProof,eventProof,clientConfigProof,serverConfigProof,suiteProof,durableProof,plainBlockProof,horizontalBlockProof,
                staticShapeProof,booleanToggleProof,itemAttributeProof,itemAttributeDurabilityProof,ownedStackDamageProof,null);
    }
    static byte[] inspect(Map<String,byte[]> source, Map<String,byte[]> classes, CompositionPlan base,
            ContentPlan content, ApiIndex targetApi, TransformationBindings bindings, TransformationLog log, Map<String,Object> report,
            String constructorOwner, OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof,
            OwnedReceiverHandles.Proof receiverProof, EventPriorityContract.Proof eventProof, ClientConfigContract.Proof clientConfigProof,
            ServerConfigContract.Proof serverConfigProof, LifecycleSuiteContract.Proof suiteProof, DurableItemContract.Proof durableProof,
            PlainBlockContract.Proof plainBlockProof, HorizontalBlockContract.Proof horizontalBlockProof, StaticShapeBlockContract.Proof staticShapeProof,
            BooleanToggleBlockContract.Proof booleanToggleProof, ItemAttributeContract.Proof itemAttributeProof,
            ItemAttributeDurabilityContract.Proof itemAttributeDurabilityProof, OwnedStackDamageContract.Proof ownedStackDamageProof, OwnedStackRepairUseContract.Proof ownedStackRepairUseProof) throws IOException, InputRejectedException, SubsetRejectionException {
        OperationControl.stage(OperationStage.RESOURCE_VALIDATION, "Planning composed metadata and complete output ownership");
        LifecycleMetadata.verify(source.get("META-INF/mods.toml"), content.modVersion(), false, report);
        Map<String, byte[]> metadata = metadata(source, content, constructorOwner != null, hierarchyProof != null, interfaceProof != null, receiverProof != null, eventProof != null, clientConfigProof != null, serverConfigProof != null, suiteProof != null, durableProof != null, plainBlockProof != null, horizontalBlockProof != null, staticShapeProof != null, booleanToggleProof != null, itemAttributeProof != null, itemAttributeDurabilityProof != null, ownedStackDamageProof != null, ownedStackRepairUseProof != null);
        var target = new TreeMap<>(source); target.putAll(classes); target.putAll(metadata);
        var plan = CompositionArtifactPlan.resolve(source, target, base, content.modId(), constructorOwner != null, hierarchyProof != null, interfaceProof != null, receiverProof != null, eventProof != null, clientConfigProof != null, serverConfigProof != null, suiteProof != null, durableProof != null, plainBlockProof != null, horizontalBlockProof != null, staticShapeProof != null, booleanToggleProof != null, itemAttributeProof != null, itemAttributeDurabilityProof != null, ownedStackDamageProof != null, ownedStackRepairUseProof != null);
        plan.verify(source, target);
        for (var entry : new TreeMap<>(CompositionArtifactPlan.metadataRules(content.modId(), constructorOwner != null, hierarchyProof != null, interfaceProof != null, receiverProof != null, eventProof != null, clientConfigProof != null, serverConfigProof != null, suiteProof != null, durableProof != null, plainBlockProof != null, horizontalBlockProof != null, staticShapeProof != null, booleanToggleProof != null, itemAttributeProof != null, itemAttributeDurabilityProof != null, ownedStackDamageProof != null, ownedStackRepairUseProof != null)).entrySet()) {
            String path = entry.getKey(), adapter = entry.getValue();
            log.add(TransformationLog.Kind.RESOURCE_REWRITE, path, fingerprint(source.get(path)), fingerprint(target.get(path)),
                    adapter + ":revision-1", MappingConfidence.VERIFIED, bindings.require(adapter).transformation(), adapter);
        }
        OperationControl.stage(OperationStage.SERIALIZATION, "Serializing a private composition candidate, not a published mod");
        byte[] jar = SimpleContentConverter.jar(target);
        var reopened = reopen(jar, null);
        plan.verify(source, reopened);
        validateReopened(reopened, metadata, content, targetApi, report, constructorOwner, hierarchyProof, interfaceProof, receiverProof, eventProof, clientConfigProof, serverConfigProof, suiteProof, durableProof, plainBlockProof, horizontalBlockProof, staticShapeProof, booleanToggleProof, itemAttributeProof, itemAttributeDurabilityProof, ownedStackDamageProof, ownedStackRepairUseProof);
        report.put("artifactPlan", plan.toTree());
        report.put("candidate", Map.of("sha256", SimpleContentConverter.hash(jar), "bytes", jar.length, "kind", "PRIVATE_SERIALIZED_JAR"));
        report.put("candidateArchiveValidation", Map.of("status", "PASSED_REOPENED", "entryCount", reopened.size(),
                "ownershipAndHashes", true, "centralDirectoryAndCrc", true, "scratchDeleted", true, "modClassesLoaded", false));
        // Package-private handoff only; public inspection discards this array. There is no publisher here.
        return jar;
    }

    private static Map<String, byte[]> metadata(Map<String, byte[]> source, ContentPlan content, boolean constructors, boolean hierarchy, boolean defaults, boolean receivers, boolean events, boolean clientConfig, boolean serverConfig, boolean suite, boolean durable, boolean plainBlock, boolean horizontalBlock, boolean staticShapeBlock, boolean booleanToggleBlock, boolean itemAttributes, boolean itemAttributeDurability, boolean ownedStackDamage, boolean ownedStackRepairUse) throws SubsetRejectionException {
        var result = new TreeMap<>(Map.of("META-INF/mods.toml", LifecycleMetadata.rewrite(source.get("META-INF/mods.toml"), content.modId(), content.modVersion()),
                "pack.mcmeta", SimpleContentConverter.targetPack(source.get("pack.mcmeta")),
                "META-INF/MANIFEST.MF", ownedStackRepairUse ? dev.legacybridge.runtime.compat.logic.OwnedStackRepairUseArtifactAdapter.targetManifest(content.modVersion()) : ownedStackDamage ? dev.legacybridge.runtime.compat.logic.OwnedStackDamageArtifactAdapter.targetManifest(content.modVersion()) : itemAttributeDurability ? dev.legacybridge.runtime.compat.logic.ItemAttributeDurabilityArtifactAdapter.targetManifest(content.modVersion()) : itemAttributes ? dev.legacybridge.runtime.compat.logic.ItemAttributeArtifactAdapter.targetManifest(content.modVersion()) : booleanToggleBlock ? dev.legacybridge.runtime.compat.logic.BooleanToggleBlockArtifactAdapter.targetManifest(content.modVersion()) : staticShapeBlock ? dev.legacybridge.runtime.compat.logic.StaticShapeBlockArtifactAdapter.targetManifest(content.modVersion()) : horizontalBlock ? dev.legacybridge.runtime.compat.logic.HorizontalBlockArtifactAdapter.targetManifest(content.modVersion()) : plainBlock ? dev.legacybridge.runtime.compat.logic.PlainBlockArtifactAdapter.targetManifest(content.modVersion()) : durable ? DurableItemArtifactAdapter.targetManifest(content.modVersion()) : suite ? LifecycleSuiteAdapter.targetManifest(content.modVersion()) : serverConfig ? ServerConfigArtifactAdapter.targetManifest(content.modVersion()) : clientConfig ? ClientConfigArtifactAdapter.targetManifest(content.modVersion()) : events ? EventArtifactAdapter.targetManifest(content.modVersion()) : receivers ? ReceiverArtifactAdapter.targetManifest(content.modVersion()) : defaults ? InterfaceArtifactAdapter.targetManifest(content.modVersion()) : hierarchy ? HierarchyArtifactAdapter.targetManifest(content.modVersion()) : constructors ? ConstructorArtifactAdapter.targetManifest(content.modVersion()) : ComposedArtifactAdapter.targetManifest(content.modVersion()),
                "META-INF/legacybridge/" + content.modId() + "/adapters.json", new CanonicalReportWriter()
                        .toJsonTree(SimpleContentConverter.canonicalTree(ownedStackRepairUse ? dev.legacybridge.runtime.compat.logic.OwnedStackRepairUseArtifactAdapter.bindingTree() : ownedStackDamage ? dev.legacybridge.runtime.compat.logic.OwnedStackDamageArtifactAdapter.bindingTree() : itemAttributeDurability ? dev.legacybridge.runtime.compat.logic.ItemAttributeDurabilityArtifactAdapter.bindingTree() : itemAttributes ? dev.legacybridge.runtime.compat.logic.ItemAttributeArtifactAdapter.bindingTree() : booleanToggleBlock ? dev.legacybridge.runtime.compat.logic.BooleanToggleBlockArtifactAdapter.bindingTree() : staticShapeBlock ? dev.legacybridge.runtime.compat.logic.StaticShapeBlockArtifactAdapter.bindingTree() : horizontalBlock ? dev.legacybridge.runtime.compat.logic.HorizontalBlockArtifactAdapter.bindingTree() : plainBlock ? dev.legacybridge.runtime.compat.logic.PlainBlockArtifactAdapter.bindingTree() : durable ? DurableItemArtifactAdapter.bindingTree() : suite ? LifecycleSuiteAdapter.bindingTree() : serverConfig ? ServerConfigArtifactAdapter.bindingTree() : clientConfig ? ClientConfigArtifactAdapter.bindingTree() : events ? EventArtifactAdapter.bindingTree() : receivers ? ReceiverArtifactAdapter.bindingTree() : defaults ? InterfaceArtifactAdapter.bindingTree() : hierarchy ? HierarchyArtifactAdapter.bindingTree() : constructors ? ConstructorArtifactAdapter.bindingTree() : ComposedArtifactAdapter.bindingTree())).getBytes(StandardCharsets.UTF_8)));
        if(plainBlock || horizontalBlock || staticShapeBlock || booleanToggleBlock) {
            String tag=dev.legacybridge.runtime.compat.world.BlockMiningAdapter.TARGET_PATH;
            if(source.containsKey(tag))throw new TransformationException("PLAIN_BLOCK_SOURCE_MINING_TAG");
            result.put(tag,miningTag(content));
        }
        return result;
    }

    private static byte[] miningTag(ContentPlan content) {
        return new CanonicalReportWriter().toJsonTree(SimpleContentConverter.canonicalTree(
            dev.legacybridge.runtime.compat.world.BlockMiningAdapter.targetTag(content.modId(),
                content.blocks().stream().map(ContentPlan.BlockDefinition::name).toList()))).getBytes(StandardCharsets.UTF_8);
    }

    /** Trusted package-local test seam: verifies genuine ZIP bytes through the production safe reader. */
    static Map<String, byte[]> reopen(byte[] jar, Path scratchDirectory) throws IOException, InputRejectedException {
        OperationControl.checkpoint();
        Path scratch = scratchDirectory == null ? Files.createTempFile("legacybridge-compose-", ".jar")
                : Files.createTempFile(scratchDirectory, "legacybridge-compose-", ".jar");
        try {
            try (OutputStream stream = Files.newOutputStream(scratch, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int offset = 0; offset < jar.length; offset += 65536) {
                    OperationControl.checkpoint(); stream.write(jar, offset, Math.min(65536, jar.length - offset));
                }
            }
            var captured = new SimpleContentConverter.CapturedArchive();
            var scan = new SafeJarReader().scan(scratch, captured);
            if (!scan.input().sha256().equals(SimpleContentConverter.hash(jar))) throw new IOException("Private candidate changed before validation");
            return captured.entries;
        } finally { Files.deleteIfExists(scratch); }
    }

    private static void validateReopened(Map<String, byte[]> entries, Map<String, byte[]> metadata,
                                          ContentPlan content, ApiIndex targetApi, Map<String, Object> report, String constructorOwner, OwnedItemHierarchy.Proof hierarchyProof, OwnedInterfaceDefaults.Proof interfaceProof, OwnedReceiverHandles.Proof receiverProof, EventPriorityContract.Proof eventProof, ClientConfigContract.Proof clientConfigProof, ServerConfigContract.Proof serverConfigProof, LifecycleSuiteContract.Proof suiteProof, DurableItemContract.Proof durableProof, PlainBlockContract.Proof plainBlockProof, HorizontalBlockContract.Proof horizontalBlockProof, StaticShapeBlockContract.Proof staticShapeProof, BooleanToggleBlockContract.Proof booleanToggleProof, ItemAttributeContract.Proof itemAttributeProof, ItemAttributeDurabilityContract.Proof itemAttributeDurabilityProof, OwnedStackDamageContract.Proof ownedStackDamageProof, OwnedStackRepairUseContract.Proof ownedStackRepairUseProof) throws SubsetRejectionException {
        OperationControl.stage(OperationStage.BYTECODE_VALIDATION, "Checking reopened composition bytes, metadata, resources and linkage");
        for (var entry : metadata.entrySet()) if (!Arrays.equals(entry.getValue(), entries.get(entry.getKey())))
            throw new TransformationException("COMPOSITION_METADATA_DRIFT: " + entry.getKey());
        LifecycleMetadata.verify(entries.get("META-INF/mods.toml"), content.modVersion(), true, report);
        var resources = new TreeMap<>(entries);
        // Only this exact generated/verified binding document is not legacy resource language.
        resources.remove("META-INF/legacybridge/" + content.modId() + "/adapters.json");
        if(plainBlockProof!=null || horizontalBlockProof!=null || staticShapeProof!=null || booleanToggleProof!=null) {
            String tag=dev.legacybridge.runtime.compat.world.BlockMiningAdapter.TARGET_PATH;
            if(!Arrays.equals(miningTag(content),resources.get(tag)))throw new TransformationException("PLAIN_BLOCK_MINING_TAG_DRIFT");
            resources.remove(tag); // Exact regenerated adapter bytes checked above, never discard unknown data.
        }
        report.put("resourceValidation", DatapackRecipeContract.report("PASSED_REOPENED", booleanToggleProof!=null ? ContentResourceValidator.validateBooleanToggleRecipes(resources,content) : staticShapeProof!=null ? ContentResourceValidator.validateStaticShapeRecipes(resources,content) : horizontalBlockProof!=null ? ContentResourceValidator.validateHorizontalRecipes(resources,content) : ContentResourceValidator.validateDatapackRecipes(resources, content)));
        Map<String, byte[]> classes = new TreeMap<>();
        entries.forEach((path, bytes) -> { if (path.endsWith(".class")) classes.put(path, bytes); });
        var scan = new LegacyReferenceScanner().scan(classes);
        if(ownedStackRepairUseProof!=null) {
            var reopened=OwnedStackRepairUseContract.inspect(scan.classNodes());
            var checked=OwnedStackRepairUseContract.validate(scan.classNodes(),content.modId(),content.modVersion());
            if(!ownedStackRepairUseProof.equals(reopened) || !content.equals(checked))throw new TransformationException("OWNED_STACK_REPAIR_USE_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedOwnedStackRepairUseContract",reopened.toTree());
        } else if(ownedStackDamageProof!=null) {
            var reopened=OwnedStackDamageContract.inspect(scan.classNodes());
            var checked=OwnedStackDamageContract.validate(scan.classNodes(),content.modId(),content.modVersion());
            if(!ownedStackDamageProof.equals(reopened) || !content.equals(checked))throw new TransformationException("OWNED_STACK_DAMAGE_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedOwnedStackDamageContract",reopened.toTree());
        } else if(itemAttributeDurabilityProof!=null) {
            var reopened=ItemAttributeDurabilityContract.inspect(scan.classNodes());
            var checked=ItemAttributeDurabilityContract.validate(scan.classNodes(),content.modId(),content.modVersion());
            if(!itemAttributeDurabilityProof.equals(reopened) || !content.equals(checked))throw new TransformationException("ITEM_ATTRIBUTE_DURABILITY_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedItemAttributeDurabilityContract",reopened.toTree());
        } else if(itemAttributeProof!=null) {
            var reopened=ItemAttributeContract.inspect(scan.classNodes());
            var checked=ItemAttributeContract.validate(scan.classNodes(),content.modId(),content.modVersion());
            if(!itemAttributeProof.equals(reopened) || !content.equals(checked))throw new TransformationException("ITEM_ATTRIBUTE_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedItemAttributeContract",reopened.toTree());
        } else if(booleanToggleProof!=null) {
            var reopened=BooleanToggleBlockContract.inspect(scan.classNodes());
            var checked=BooleanToggleBlockContract.validate(scan.classNodes(),content.modId(),content.modVersion());
            if(!booleanToggleProof.equals(reopened) || !content.equals(checked))throw new TransformationException("BOOLEAN_TOGGLE_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedBooleanToggleBlockContract",reopened.toTree());
        } else if(staticShapeProof!=null) {
            var reopened=StaticShapeBlockContract.inspect(scan.classNodes());
            var checked=StaticShapeBlockContract.validate(scan.classNodes(),content.modId(),content.modVersion());
            if(!staticShapeProof.equals(reopened) || !content.equals(checked))throw new TransformationException("STATIC_SHAPE_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedStaticShapeBlockContract",reopened.toTree());
            var loot=StaticShapeLootContract.inspect(resources,content);
            if(!loot.equals(Optional.ofNullable(report.get("staticShapeLootContract"))))
                throw new TransformationException("STATIC_SHAPE_SELF_DROP_READBACK_DRIFT");
            loot.ifPresent(proof -> report.put("reopenedStaticShapeLootContract",proof));
        } else if(horizontalBlockProof!=null) {
            var reopened=HorizontalBlockContract.inspect(scan.classNodes());
            var checked=HorizontalBlockContract.validate(scan.classNodes(),content.modId(),content.modVersion());
            if(!horizontalBlockProof.equals(reopened) || !content.equals(checked))throw new TransformationException("HORIZONTAL_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedHorizontalBlockContract",reopened.toTree());
        } else if (plainBlockProof != null) {
            var reopened=PlainBlockContract.inspect(scan.classNodes());
            var checked=PlainBlockContract.validate(scan.classNodes(),content.modId(),content.modVersion());
            if(!plainBlockProof.equals(reopened) || !content.equals(checked))throw new TransformationException("PLAIN_BLOCK_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedPlainBlockContract",reopened.toTree());
        } else if (durableProof != null) {
            var reopened=DurableItemContract.inspect(scan.classNodes());
            var checked=DurableItemContract.validate(scan.classNodes(),content.modId(),content.modVersion());
            if(!durableProof.equals(reopened) || !content.equals(checked))throw new TransformationException("DURABLE_ITEM_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedDurableItemContract",reopened.toTree());
        } else if (suiteProof != null) {
            var reopened = LifecycleSuiteContract.inspect(scan.classNodes());
            var checked = LifecycleSuiteContract.validate(scan.classNodes(), content.modId(), content.modVersion());
            if (!suiteProof.equals(reopened) || !content.equals(checked)) throw new TransformationException("LIFECYCLE_SUITE_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedLifecycleSuiteContract", reopened.toTree());
        } else if (serverConfigProof != null) {
            var reopened = ServerConfigContract.inspect(scan.classNodes());
            var checked = ServerConfigContract.validate(scan.classNodes(), content.modId(), content.modVersion());
            if (!serverConfigProof.equals(reopened) || !content.equals(checked)) throw new TransformationException("SERVER_CONFIG_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedServerConfigContract", reopened.toTree());
        }
        if (clientConfigProof != null) {
            var reopened = ClientConfigContract.inspect(scan.classNodes());
            var checked = ClientConfigContract.validate(scan.classNodes(), content.modId(), content.modVersion());
            if (!clientConfigProof.equals(reopened) || !content.equals(checked)) throw new TransformationException("CLIENT_CONFIG_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedClientConfigContract", reopened.toTree());
        }
        if (eventProof != null) {
            var reopened = EventPriorityContract.inspect(scan.classNodes());
            var checked = EventPriorityContract.validate(scan.classNodes(), content.modId(), content.modVersion());
            if (!eventProof.equals(reopened) || !content.equals(checked)) throw new TransformationException("EVENT_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedEventContract", reopened.toTree());
        }
        if (receiverProof != null) {
            var reopened = OwnedReceiverHandles.inspect(scan.classNodes());
            var checked = LogicProfileShape.validate(scan.classNodes(), content.modId(), content.modVersion());
            if (!receiverProof.equals(reopened) || !content.equals(checked)) throw new TransformationException("RECEIVER_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedOwnedReceiver", reopened.toTree());
        }
        if (interfaceProof != null) {
            var reopened = OwnedInterfaceDefaults.inspect(scan.classNodes());
            var checked = LogicProfileShape.validate(scan.classNodes(), content.modId(), content.modVersion());
            if (!interfaceProof.equals(reopened) || !content.equals(checked)) throw new TransformationException("INTERFACE_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedOwnedInterface", reopened.toTree());
        }
        if (hierarchyProof != null) {
            var reopened = OwnedItemHierarchy.inspect(scan.classNodes(), targetApi, "net/minecraft/world/item/Item");
            var checked = LogicProfileShape.validateOwnedHierarchy(scan.classNodes(), content.modId(), content.modVersion(), reopened);
            if (!hierarchyProof.equals(reopened) || !content.equals(checked)) throw new TransformationException("HIERARCHY_CANDIDATE_READBACK_DRIFT");
            report.put("reopenedOwnedHierarchy", reopened.toTree());
        }
        if (constructorOwner != null) {
            var checked = LogicProfileShape.validateOwnedConstructors(scan.classNodes(), content.modId(), content.modVersion());
            if (!content.equals(checked) || !constructorOwner.equals(LogicProfileShape.constructorProof(scan.classNodes()).owner()))
                throw new TransformationException("CONSTRUCTOR_CANDIDATE_READBACK_DRIFT");
        }
        var hierarchy = new HashMap<>(OfflineLinkageVerifier.declarationHierarchy(targetApi));
        classes.forEach((path, bytes) -> hierarchy.put(path.substring(0, path.length() - 6), bytes));
        for (byte[] bytes : classes.values()) { OperationControl.checkpoint(); BytecodeValidator.validate(bytes, hierarchy); }
        report.put("serializedOfflineLinkage", (ownedStackRepairUseProof != null ? new OfflineLinkageVerifier().verify(classes,targetApi,OfflineLinkageVerifier.CallbackPolicy.OWNED_STACK_REPAIR_USE)
                : ownedStackDamageProof != null ? new OfflineLinkageVerifier().verify(classes,targetApi,OfflineLinkageVerifier.CallbackPolicy.OWNED_STACK_DAMAGE)
                : itemAttributeDurabilityProof != null ? new OfflineLinkageVerifier().verify(classes,targetApi,OfflineLinkageVerifier.CallbackPolicy.ITEM_ATTRIBUTE_DURABILITY)
                : itemAttributeProof != null ? new OfflineLinkageVerifier().verify(classes,targetApi,OfflineLinkageVerifier.CallbackPolicy.ITEM_ATTRIBUTES)
                : booleanToggleProof != null ? new OfflineLinkageVerifier().verify(classes,targetApi,OfflineLinkageVerifier.CallbackPolicy.BOOLEAN_TOGGLE_BLOCK)
                : staticShapeProof != null ? new OfflineLinkageVerifier().verify(classes,targetApi,OfflineLinkageVerifier.CallbackPolicy.STATIC_SHAPE_BLOCK)
                : horizontalBlockProof != null ? new OfflineLinkageVerifier().verify(classes,targetApi,OfflineLinkageVerifier.CallbackPolicy.HORIZONTAL_BLOCK)
                : suiteProof != null ? new OfflineLinkageVerifier().verify(classes, targetApi, OfflineLinkageVerifier.CallbackPolicy.LIFECYCLE_SUITE)
                : serverConfigProof != null ? new OfflineLinkageVerifier().verify(classes, targetApi, OfflineLinkageVerifier.CallbackPolicy.SERVER_CONFIG)
                : clientConfigProof != null ? new OfflineLinkageVerifier().verify(classes, targetApi, OfflineLinkageVerifier.CallbackPolicy.CLIENT_CONFIG)
                : eventProof != null ? new OfflineLinkageVerifier().verify(classes, targetApi, OfflineLinkageVerifier.CallbackPolicy.OWNED_EVENT_PRIORITY)
                : receiverProof != null ? new OfflineLinkageVerifier().verifyOwnedReceiverHandles(classes, targetApi)
                : interfaceProof != null ? new OfflineLinkageVerifier().verifyOwnedInterfaceDefaults(classes, targetApi)
                : constructorOwner == null ? new OfflineLinkageVerifier().verify(classes, targetApi)
                : new OfflineLinkageVerifier().verifyOwnedConstructors(classes, targetApi, constructorOwner)).toTree());
    }
    private static String fingerprint(byte[] bytes) {
        return bytes == null ? "<absent>" : "sha256:" + SimpleContentConverter.hash(bytes) + " bytes=" + bytes.length;
    }
}
