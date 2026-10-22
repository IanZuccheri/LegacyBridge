package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.composition.CompositionPlan;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.transformation.TransformationException;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.UnsupportedCompatibilityException;
import dev.legacybridge.runtime.compat.logic.OwnedStackRepairUseArtifactAdapter;
import dev.legacybridge.runtime.compat.world.BlockMiningAdapter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Byte ownership/provenance only. Synthetic class bytes are never loaded or admitted as bytecode. */
class OwnedStackRepairUseArtifactPlanTest {
    private static final String OWNED = "owned/ItemAttributeContent.class";
    private static final String ITEM = "owned/AttributeContentItem.class";
    private static final String DATA = "data/example/recipes/attribute_item.json";
    private static final String MODEL = "assets/example/models/item/bridge_attribute_item.json";
    private static final String LANGUAGE = "assets/example/lang/en_us.json";
    private static final String MODS = "META-INF/mods.toml";
    private static final String MANIFEST = "META-INF/MANIFEST.MF";
    private static final String PACK = "pack.mcmeta";
    private static final String BINDINGS = "META-INF/legacybridge/example/adapters.json";

    @Test void exactPrivateIdentityOwnsMetadataWithoutMiningOrPublicAuthority() {
        var source = source(); var target = target(source); var plan = resolve(source, target);
        plan.verify(source, target);
        var tree = plan.toTree();
        assertEquals(1, tree.get("schemaVersion"));
        assertEquals("PRIVATE_ARTIFACT_PLAN_NOT_AUTHORITY", tree.get("kind"));
        assertEquals(false, tree.get("publicationAllowed"));
        assertEquals(source.size() + 1, tree.get("entryCount"));
        assertEquals(AdapterCatalog.RUNTIME_VERSION, tree.get("runtimeVersion"));
        assertFalse(target.containsKey(BlockMiningAdapter.TARGET_PATH));
        var binding = find(tree, "entries", "path", BINDINGS);
        assertEquals("GENERATE", binding.get("disposition"));
        assertNull(binding.get("sourceSha256"));
        assertEquals(SimpleContentConverter.hash(target.get(BINDINGS)), binding.get("targetSha256"));
        assertEquals("metadata.owned-stack-repair-use-adapter-bindings", binding.get("operation"));
        assertEquals(AdapterCatalog.STACK_REPAIR_USE_BINDINGS, ((Map<?, ?>) binding.get("adapter")).get("id"));
        var manifest = find(tree, "entries", "path", MANIFEST);
        assertEquals("REWRITE", manifest.get("disposition"));
        assertEquals("metadata.owned-stack-repair-use-candidate-identity", manifest.get("operation"));
        assertEquals(AdapterCatalog.STACK_REPAIR_USE_MANIFEST, ((Map<?, ?>) manifest.get("adapter")).get("id"));
        for (String path : List.of(DATA, MODEL, LANGUAGE, "LICENSE")) {
            assertArrayEquals(source.get(path), target.get(path));
            assertEquals("PRESERVE_BYTES", find(tree, "entries", "path", path).get("disposition"));
        }
        for (String path : List.of(OWNED, ITEM)) {
            var entry = find(tree, "entries", "path", path);
            assertEquals("REWRITE", entry.get("disposition"));
            assertEquals("logic.owned-bodies", entry.get("operation"));
            assertEquals(SimpleContentConverter.hash(source.get(path)), entry.get("sourceSha256"));
            assertEquals(SimpleContentConverter.hash(target.get(path)), entry.get("targetSha256"));
        }
        for (Object value : (List<?>) tree.get("metadataOperations")) {
            var operation = (Map<?, ?>) value;
            assertEquals("PRIVATE_METADATA_SERIALIZATION", operation.get("phase"));
            assertEquals("BOUNDED_ENGINE_WORKER", operation.get("thread"));
            assertEquals(List.of("logic.owned-bodies", "data.recipe-resources"), operation.get("requires"));
            assertTrue(((List<?>) operation.get("preconditions")).contains("Exact implemented metadata generators"));
        }
        source.clear(); target.clear(); assertEquals(tree, plan.toTree());
    }

    @Test void realImplementedBindingsAndManifestDoNotBorrowToolOrBlockAuthority() throws Exception {
        var ids = OwnedStackRepairUseArtifactAdapter.requiredAdapters();
        assertEquals(List.of(AdapterCatalog.LOGIC, AdapterCatalog.STACK_REPAIR_USE_MANIFEST,
                AdapterCatalog.STACK_REPAIR_USE_BINDINGS, AdapterCatalog.METADATA, AdapterCatalog.PACK), ids);
        assertEquals(ids.size(), new HashSet<>(ids).size());
        assertThrows(UnsupportedOperationException.class, () -> ids.add("unimplemented"));
        var binding = OwnedStackRepairUseArtifactAdapter.bindingTree();
        var transforms = TransformationBindings.forOwnedStackRepairUse();
        assertEquals(binding, transforms.bindingTree());
        assertEquals(AdapterCatalog.SCHEMA_VERSION, binding.get("schemaVersion"));
        assertEquals(AdapterCatalog.RUNTIME_VERSION, binding.get("runtimeVersion"));
        for (String id : ids)
            assertEquals(AdapterCatalog.require(id).referenceTree(), find(binding, "adapters", "id", id));
        for (String id : List.of(AdapterCatalog.STACK_REPAIR_USE_MANIFEST, AdapterCatalog.STACK_REPAIR_USE_BINDINGS)) {
            var operation = transforms.require(id);
            assertEquals("OFFLINE", operation.phase());
            assertEquals(OwnedStackRepairUseArtifactAdapter.class.getName(), operation.implementationClass());
            assertEquals(List.of("fixtures/owned-stack-repair-use-1.16.5"), operation.fixtures());
            assertFalse(operation.knownLimitations().isBlank());
        }
        assertEquals("targetManifest", transforms.require(AdapterCatalog.STACK_REPAIR_USE_MANIFEST).method());
        assertEquals("(Ljava/lang/String;)[B", transforms.require(AdapterCatalog.STACK_REPAIR_USE_MANIFEST).descriptor());
        assertEquals("bindingTree", transforms.require(AdapterCatalog.STACK_REPAIR_USE_BINDINGS).method());
        assertEquals("()Ljava/util/Map;", transforms.require(AdapterCatalog.STACK_REPAIR_USE_BINDINGS).descriptor());
        for (String forbidden : List.of(AdapterCatalog.MINING, AdapterCatalog.PLAIN_BLOCK_PROPERTIES,
                AdapterCatalog.DURABLE_MANIFEST, AdapterCatalog.BOOLEAN_TOGGLE_BLOCK_BINDINGS))
            assertThrows(UnsupportedCompatibilityException.class, () -> transforms.require(forbidden));
        var manifest = new java.util.jar.Manifest(new java.io.ByteArrayInputStream(
                OwnedStackRepairUseArtifactAdapter.targetManifest("1.0.0")));
        assertEquals("owned-stack-repair-use-candidate-v1", manifest.getMainAttributes().getValue("LegacyBridge-Profile"));
        assertEquals("1", manifest.getMainAttributes().getValue("LegacyBridge-Owned-Stack-Repair-Use-Revision"));
        assertEquals("STATIC-CANDIDATE-ONLY", manifest.getMainAttributes().getValue("LegacyBridge-Qualification"));
        assertArrayEquals(OwnedStackRepairUseArtifactAdapter.targetManifest("1.0.0"),
                OwnedStackRepairUseArtifactAdapter.targetManifest("1.0.0"));
    }

    @Test void metadataRulesAreExplicitImmutableAndPreviousOverloadsRemainIndependent() {
        var rules = rules(new boolean[16]);
        assertEquals(Map.of(MODS, AdapterCatalog.METADATA, PACK, AdapterCatalog.PACK,
                MANIFEST, AdapterCatalog.STACK_REPAIR_USE_MANIFEST, BINDINGS, AdapterCatalog.STACK_REPAIR_USE_BINDINGS), rules);
        assertThrows(UnsupportedOperationException.class, () -> rules.put("unknown", "unknown"));
        var old = CompositionArtifactPlan.metadataRules("example", false, false, false, false, false,
                false, false, false, false, false, false, false, false);
        assertEquals(CompositionArtifactPlan.metadataRules("example"), old);
        assertEquals(AdapterCatalog.COMPOSE_MANIFEST, old.get(MANIFEST));
        assertFalse(old.containsValue(AdapterCatalog.STACK_REPAIR_USE_BINDINGS));
        assertThrows(UnsupportedCompatibilityException.class,
                () -> TransformationBindings.forComposition().require(AdapterCatalog.STACK_REPAIR_USE_MANIFEST));
        assertThrows(UnsupportedCompatibilityException.class,
                () -> TransformationBindings.forLogic().require(AdapterCatalog.STACK_REPAIR_USE_BINDINGS));
    }

    @Test void independentCopiesRevalidateFrozenSourceAndTargetHashes() {
        var source = source(); var target = target(source); var plan = resolve(source, target);
        var reopenedSource = copy(source); var reopenedTarget = copy(target);
        plan.verify(reopenedSource, reopenedTarget);
        assertNotSame(target.get(ITEM), reopenedTarget.get(ITEM));
        reopenedTarget.get(ITEM)[0] ^= 1;
        assertThrows(TransformationException.class, () -> plan.verify(reopenedSource, reopenedTarget));
        plan.verify(source, target);
    }

    @ParameterizedTest @ValueSource(strings={OWNED, ITEM, DATA, MODEL, LANGUAGE, MODS, MANIFEST, PACK, "LICENSE"})
    void originalHashDriftCannotAcquireFrozenOwnership(String path) {
        var source = source(); var target = target(source); var plan = resolve(source, target);
        source.put(path, text("changed original"));
        assertThrows(TransformationException.class, () -> plan.verify(source, target));
    }

    @ParameterizedTest @ValueSource(strings={OWNED, ITEM, DATA, MODEL, LANGUAGE, MODS, MANIFEST, PACK, BINDINGS, "LICENSE"})
    void targetHashDriftCannotSurviveIndependentReadback(String path) {
        var source = source(); var target = target(source); var plan = resolve(source, target);
        target.put(path, text("changed candidate"));
        assertThrows(TransformationException.class, () -> plan.verify(source, target));
    }

    @ParameterizedTest @ValueSource(strings={DATA, MODEL, LANGUAGE, "LICENSE"})
    void preservedResourcesAndNoticesCannotChangeBeforeFreezing(String path) {
        var source = source(); var target = target(source);
        target.put(path, text("not the original bytes"));
        assertThrows(TransformationException.class, () -> resolve(source, target));
    }

    @ParameterizedTest @ValueSource(strings={"SOURCE_ADDED", "SOURCE_REMOVED", "TARGET_REMOVED", "TARGET_ADDED"})
    void frozenPlanRequiresCompleteSourceAndTargetEntrySets(String mutation) {
        var source = source(); var target = target(source); var plan = resolve(source, target);
        switch (mutation) {
            case "SOURCE_ADDED" -> source.put("NOTICE", text("new source"));
            case "SOURCE_REMOVED" -> source.remove(DATA);
            case "TARGET_REMOVED" -> target.remove(ITEM);
            case "TARGET_ADDED" -> target.put("NOTICE", text("new target"));
            default -> throw new AssertionError(mutation);
        }
        assertThrows(TransformationException.class, () -> plan.verify(source, target));
    }

    @ParameterizedTest @ValueSource(strings={"MISSING_BINDINGS", "MISSING_MANIFEST", "GENERATED_MINING", "GENERATED_RECIPE", "GENERATED_CLASS", "GENERATED_METADATA", "GENERATED_NOTICE"})
    void missingMetadataAndUnimplementedGeneratedEntriesAreRejected(String mutation) {
        var source = source(); var target = target(source);
        switch (mutation) {
            case "MISSING_BINDINGS" -> target.remove(BINDINGS);
            case "MISSING_MANIFEST" -> target.remove(MANIFEST);
            case "GENERATED_MINING" -> target.put(BlockMiningAdapter.TARGET_PATH, text("{}"));
            case "GENERATED_RECIPE" -> target.put("data/example/recipes/unowned.json", text("{}"));
            case "GENERATED_CLASS" -> target.put("owned/Extra.class", text("not executable"));
            case "GENERATED_METADATA" -> target.put("META-INF/unowned.json", text("{}"));
            case "GENERATED_NOTICE" -> target.put("NOTICE", text("unowned notice"));
            default -> throw new AssertionError(mutation);
        }
        assertThrows(TransformationException.class, () -> resolve(source, target));
    }

    @Test void staleClassClaimCannotOwnAnotherOriginalEvenBeforeSerialization() {
        var source = source(); var target = target(source); var oldPlan = base(source);
        source.put(ITEM, text("new unapproved subclass"));
        assertThrows(TransformationException.class, () -> resolve(source, target, oldPlan, new boolean[16]));
    }

    @ParameterizedTest @ValueSource(ints={0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15})
    void stackRepairUseCannotImplicitlyCombineWithAnyPriorPrivateCandidate(int other) {
        boolean[] flags = new boolean[16]; flags[other] = true;
        assertThrows(TransformationException.class, () -> rules(flags));
        var source = source(); var target = target(source);
        assertThrows(TransformationException.class, () -> resolve(source, target, base(source), flags));
    }

    private static Map<String, byte[]> source() {
        var source = new TreeMap<String, byte[]>();
        for (String path : List.of(OWNED, ITEM, DATA, MODEL, LANGUAGE, MODS, MANIFEST, PACK, "LICENSE"))
            source.put(path, text("Synthetic ownership payload, never executable: " + path));
        return source;
    }
    private static Map<String, byte[]> target(Map<String, byte[]> source) {
        var target = new TreeMap<>(source);
        target.put(OWNED, text("Synthetic changed entry class"));
        target.put(ITEM, text("Synthetic changed item class"));
        target.put(MANIFEST, OwnedStackRepairUseArtifactAdapter.targetManifest("1.0.0"));
        target.put(BINDINGS, text(new CanonicalReportWriter().toJsonTree(
                SimpleContentConverter.canonicalTree(OwnedStackRepairUseArtifactAdapter.bindingTree()))));
        return target;
    }
    private static Map<String, byte[]> copy(Map<String, byte[]> entries) {
        var copied = new TreeMap<String, byte[]>();
        entries.forEach((path, bytes) -> copied.put(path, bytes.clone())); return copied;
    }
    private static CompositionPlan base(Map<String, byte[]> source) {
        return new CompositionPlan(List.of(
                new CompositionPlan.Operation("logic.owned-bodies", CompositionPlan.Kind.PRESERVE_OWNED_BODIES,
                        Set.of(), List.of(claim(source, OWNED), claim(source, ITEM))),
                new CompositionPlan.Operation("data.recipe-resources", CompositionPlan.Kind.PRESERVE_RECIPE_RESOURCES,
                        Set.of("logic.owned-bodies"), List.of(claim(source, DATA), claim(source, MODEL), claim(source, LANGUAGE)))));
    }
    private static CompositionPlan.Claim claim(Map<String, byte[]> source, String path) {
        return new CompositionPlan.Claim(path, SimpleContentConverter.hash(source.get(path)));
    }
    private static CompositionArtifactPlan resolve(Map<String, byte[]> source, Map<String, byte[]> target) {
        return resolve(source, target, base(source), new boolean[16]);
    }
    private static CompositionArtifactPlan resolve(Map<String, byte[]> source, Map<String, byte[]> target,
            CompositionPlan base, boolean[] f) {
        return CompositionArtifactPlan.resolve(source, target, base, "example", f[0], f[1], f[2], f[3], f[4],
                f[5], f[6], f[7], f[8], f[9], f[10], f[11], f[12], f[13], f[14], f[15], true);
    }
    private static Map<String, String> rules(boolean[] f) {
        return CompositionArtifactPlan.metadataRules("example", f[0], f[1], f[2], f[3], f[4], f[5], f[6],
                f[7], f[8], f[9], f[10], f[11], f[12], f[13], f[14], f[15], true);
    }
    private static Map<?, ?> find(Map<String, Object> tree, String group, String key, String value) {
        return ((List<?>) tree.get(group)).stream().map(Map.class::cast)
                .filter(entry -> value.equals(entry.get(key))).findFirst().orElseThrow();
    }
    private static byte[] text(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
