package dev.legacybridge.converter.transformation;

import dev.legacybridge.converter.conversion.TransformationBindings;
import dev.legacybridge.converter.conversion.extract.*;
import dev.legacybridge.converter.mapping.*;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStage;
import dev.legacybridge.converter.operation.OperationStoppedException;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Transactional in-memory ASM pipeline. Only the existing fixture-proven profile is exposed to the CLI. */
public final class TransformationPipeline {
    public static final List<String> STAGES = List.of("ClassReader", "LegacyReferenceScanner", "MappingResolver",
            "ClassRemapper", "LegacyApiTransformer", "DescriptorTransformer", "CompatibilityAdapterTransformer",
            "ClassWriter", "CheckClassAdapter");

    public record Result(ContentPlan plan, TransformationBindings bindings, Map<String, byte[]> classes,
                         int scannedClassCount, int scannedReferenceCount) { }

    public Result transform(Map<String, byte[]> sourceEntries, MappingInputs inputs, String modId,
                            String version, TransformationLog log) throws SubsetRejectionException {
        try {
            return transformChecked(sourceEntries, inputs, modId, version, log);
        } catch (OperationStoppedException stopped) {
            throw stopped;
        } catch (TransformationException refused) {
            throw refused;
        } catch (RuntimeException | LinkageError invalid) {
            throw new TransformationException("ASM pipeline rejected malformed or unsupported bytecode: " + invalid.getMessage(), invalid);
        }
    }

    private Result transformChecked(Map<String, byte[]> sourceEntries, MappingInputs inputs, String modId,
                                    String version, TransformationLog log) throws SubsetRejectionException {
        OperationControl.stage(OperationStage.ANALYSIS, "Scanning bytecode references without loading classes");
        var scan = new LegacyReferenceScanner().scan(sourceEntries);
        OperationControl.stage(OperationStage.BYTECODE_VALIDATION, "Validating original bytecode");
        for (var source : sourceEntries.values()) {
            OperationControl.checkpoint();
            BytecodeValidator.validate(source);
        }
        // This is the semantic proof, not a promotion of inferred HIGH mappings to VERIFIED.
        OperationControl.stage(OperationStage.SEMANTIC_VALIDATION, "Proving the complete supported registration graph");
        ContentPlan plan = new StaticContentExtractor().extract(sourceEntries, inputs, modId, version);
        TransformationBindings bindings = TransformationBindings.forPlan(plan);
        Map<String, ClassNode> originals = scan.classNodes();
        String oldEntrypoint = originals.values().stream().filter(node -> node.visibleAnnotations != null
                && node.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lnet/minecraftforge/fml/common/Mod;")))
                .map(node -> node.name).findFirst().orElseThrow(() -> new TransformationException("No proven mod entrypoint"));
        String targetEntrypoint = "dev/legacybridge/generated/" + modId + "/ConvertedMod";
        if (originals.containsKey(targetEntrypoint) && !oldEntrypoint.equals(targetEntrypoint))
            throw new TransformationException("Generated entrypoint would collide with an input class");
        var legacy = LegacySymbol.classSymbol(oldEntrypoint);
        var rule = new MappingRule(legacy, TargetSymbol.classSymbol(targetEntrypoint),
                oldEntrypoint.equals(targetEntrypoint) ? MappingCategory.IDENTITY : MappingCategory.PACKAGE_MOVE,
                MappingConfidence.VERIFIED, MappingStatus.RESOLVED,
                List.of("Own mod entrypoint is fully consumed by the implemented simple-content-v1 bootstrap recipe"));
        OperationControl.stage(OperationStage.MAPPING_RESOLUTION, "Resolving verified simple-content rules");
        var resolver = new MappingResolver(MappingDatabase.builder().add(rule).build(),
                Map.of(legacy, bindings.require(AdapterCatalog.BOOTSTRAP).id()), originals.keySet());
        Map<String, ClassNode> remapped = new TreeMap<>();
        Map<String, String> sourceLocations = new TreeMap<>();
        OperationControl.stage(OperationStage.CLASS_REMAP, "Remapping proven mod-owned classes");
        for (var original : originals.values()) {
            OperationControl.checkpoint();
            ClassNode mapped = LoggedClassRemapper.remap(original, resolver, log);
            if (remapped.putIfAbsent(mapped.name, mapped) != null) throw new TransformationException("Class remap collision");
            sourceLocations.put(mapped.name, scan.classes().get(original.name).location());
        }
        OperationControl.stage(OperationStage.API_TRANSFORMATION, "Lowering the proven registration graph");
        ClassNode lowered = new LegacyApiTransformer().transform(remapped, sourceLocations, targetEntrypoint, plan, bindings, log);
        OperationControl.stage(OperationStage.DESCRIPTOR_TRANSFORMATION, "Transforming verified descriptors");
        ClassNode described = new DescriptorTransformer(resolver).transform(lowered, log);
        OperationControl.stage(OperationStage.ADAPTER_TRANSFORMATION, "Binding existing compatibility adapters");
        new CompatibilityAdapterTransformer().transform(described, modId,
                "META-INF/legacybridge/" + modId + "/content.json", sourceLocations.get(targetEntrypoint), bindings, log);
        // Changed method bodies have no reliable source frames. Never ask ASM to load mod/game classes.
        var writer = new OfflineClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, Map.of());
        described.accept(writer);
        byte[] candidate = writer.toByteArray();
        Map<String, byte[]> output = Map.of(described.name + ".class", candidate);
        OperationControl.stage(OperationStage.BYTECODE_VALIDATION, "Validating generated bytecode offline");
        BytecodeValidator.validate(candidate, Map.of(described.name, candidate));
        OperationControl.checkpoint();
        return new Result(plan, bindings, output, originals.size(), scan.references().size());
    }
}
