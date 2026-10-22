package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.composition.CompositionPlan;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.transformation.TransformationException;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.logic.StaticShapeBlockArtifactAdapter;
import dev.legacybridge.runtime.compat.world.BlockMiningAdapter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic byte-ownership tests only: not API, JSON parser, bytecode or game qualification. */
class StaticShapeBlockArtifactPlanTest {
    private static final String OWNED = "owned/BlockContent.class";
    private static final String BLOCK = "owned/StaticShapeBlock.class";
    private static final String DATA = "data/example/recipes/block.json";
    private static final String MODEL = "assets/example/models/block/bridge_block.json";
    private static final String BLOCKSTATE = "assets/example/blockstates/bridge_block.json";
    private static final String LANGUAGE = "assets/example/lang/en_us.json";
    private static final String BINDINGS = "META-INF/legacybridge/example/adapters.json";
    private static final String MINING = BlockMiningAdapter.TARGET_PATH;

    @Test void explicitGeneratedMiningOwnershipHasBehavioralContextAndNoPublicationAuthority() {
        var source=source(); var target=target(source); var plan=resolve(source,target);
        plan.verify(source,target);
        var tree=plan.toTree(); assertEquals(false,tree.get("publicationAllowed"));
        assertEquals(source.size()+2,tree.get("entryCount"));
        var entry=find(tree,"entries","path",MINING);
        assertEquals("GENERATE",entry.get("disposition")); assertNull(entry.get("sourceSha256"));
        assertEquals(SimpleContentConverter.hash(target.get(MINING)),entry.get("targetSha256"));
        assertEquals(AdapterCatalog.MINING,((Map<?,?>)entry.get("adapter")).get("id"));
        var operation=find(tree,"metadataOperations","path",MINING);
        assertEquals("PRIVATE_MINING_RESOURCE_GENERATION",operation.get("phase"));
        assertTrue(operation.get("runtimeContext").toString().contains("behavior-affecting"));
        assertFalse(operation.get("runtimeContext").toString().startsWith("Inert"));
        assertTrue(((List<?>)operation.get("preconditions")).contains("Exact static bottom-half geometry with independently requalified STONE defaults"));
        assertFalse(((List<?>)operation.get("preconditions")).contains("Exact native STONE full-cube contract"));
        assertEquals(AdapterCatalog.STATIC_SHAPE_BINDINGS,((Map<?,?>)find(tree,"entries","path",BINDINGS).get("adapter")).get("id"));
        assertEquals(AdapterCatalog.STATIC_SHAPE_MANIFEST,((Map<?,?>)find(tree,"entries","path","META-INF/MANIFEST.MF").get("adapter")).get("id"));
        for(String path:List.of(DATA,MODEL,BLOCKSTATE,LANGUAGE,"LICENSE")) {
            assertArrayEquals(source.get(path),target.get(path));
            assertEquals("PRESERVE_BYTES",find(tree,"entries","path",path).get("disposition"));
        }
        for(String path:List.of(OWNED,BLOCK)) {
            var owned=find(tree,"entries","path",path);
            assertEquals("REWRITE",owned.get("disposition"));assertEquals("logic.owned-bodies",owned.get("operation"));
            assertEquals(SimpleContentConverter.hash(source.get(path)),owned.get("sourceSha256"));
        }
        source.clear(); target.clear(); assertEquals(tree,plan.toTree());
    }

    @Test void propertyRecipeHasDistinctImplementedScopedBindingAndExactPrivateIdentity() throws Exception {
        var ids=StaticShapeBlockArtifactAdapter.requiredAdapters();
        assertTrue(ids.contains(AdapterCatalog.STATIC_SHAPE_PROPERTIES));
        assertFalse(ids.contains(AdapterCatalog.PLAIN_BLOCK_PROPERTIES));
        assertTrue(ids.contains(AdapterCatalog.PLAIN_BLOCK)); // Only the unchanged late RegistryObject descriptor recipe.
        assertEquals(ids.size(),new HashSet<>(ids).size());
        assertThrows(UnsupportedOperationException.class,()->ids.add("unimplemented"));
        var scoped=AdapterCatalog.require(AdapterCatalog.STATIC_SHAPE_PROPERTIES);
        assertEquals("OFFLINE",scoped.phase());
        assertEquals(StaticShapeBlockArtifactAdapter.class.getName(),scoped.implementationClass());
        assertEquals("stonePropertiesCalls",scoped.method());assertEquals("()Ljava/util/List;",scoped.descriptor());
        var calls=StaticShapeBlockArtifactAdapter.stonePropertiesCalls();
        assertEquals(List.of("STATIC:of","FIELD:STONE","VIRTUAL:mapColor","FIELD:BASEDRUM","VIRTUAL:instrument"),
            calls.stream().map(call->call.get("kind")+":"+call.get("name")).toList());
        assertEquals(dev.legacybridge.runtime.compat.logic.PlainBlockArtifactAdapter.stonePropertiesCalls(),calls);
        assertThrows(UnsupportedOperationException.class,()->calls.add(Map.of()));
        assertThrows(UnsupportedOperationException.class,()->calls.get(0).put("name","forceSolidOn"));
        var binding=StaticShapeBlockArtifactAdapter.bindingTree();
        assertEquals(AdapterCatalog.RUNTIME_VERSION,binding.get("runtimeVersion"));
        var reference=find(binding,"adapters","id",AdapterCatalog.STATIC_SHAPE_PROPERTIES);
        assertEquals(scoped.referenceTree(),reference);
        assertFalse(((List<?>)binding.get("adapters")).stream().map(Map.class::cast)
            .anyMatch(entry->AdapterCatalog.PLAIN_BLOCK_PROPERTIES.equals(entry.get("id"))));
        var manifest=new java.util.jar.Manifest(new java.io.ByteArrayInputStream(StaticShapeBlockArtifactAdapter.targetManifest("1.0.0")));
        assertEquals("static-shape-block-candidate-v1",manifest.getMainAttributes().getValue("LegacyBridge-Profile"));
        assertEquals("1",manifest.getMainAttributes().getValue("LegacyBridge-Static-Shape-Revision"));
        assertEquals("STATIC-CANDIDATE-ONLY",manifest.getMainAttributes().getValue("LegacyBridge-Qualification"));
    }

    @ParameterizedTest @ValueSource(strings={OWNED,BLOCK,DATA,MODEL,BLOCKSTATE,LANGUAGE,"META-INF/mods.toml","META-INF/MANIFEST.MF","pack.mcmeta","LICENSE"})
    void frozenPlanRejectsAnyOriginalHashDrift(String path) {
        var source=source();var target=target(source);var plan=resolve(source,target);
        source.put(path,text("Changed original"));
        assertThrows(TransformationException.class,()->plan.verify(source,target));
    }

    @ParameterizedTest @ValueSource(strings={DATA,MODEL,BLOCKSTATE,LANGUAGE,"LICENSE"})
    void resourceAndNoticeBytesCannotChangeEvenBeforeOwnershipIsFrozen(String path) {
        var source=source();var target=target(source);target.put(path,text("Changed resource bytes"));
        assertThrows(TransformationException.class,()->resolve(source,target));
    }

    @ParameterizedTest @ValueSource(strings={"SOURCE_ADDED","SOURCE_REMOVED","TARGET_REMOVED","TARGET_ADDED","MANIFEST_DRIFT","BINDING_DRIFT","CLASS_DRIFT"})
    void reopenedCandidateRequiresCompleteFrozenOwnershipAndMetadata(String defect) {
        var source=source();var target=target(source);var plan=resolve(source,target);
        switch(defect) {
            case "SOURCE_ADDED" -> source.put("NOTICE",text("new"));
            case "SOURCE_REMOVED" -> source.remove(DATA);
            case "TARGET_REMOVED" -> target.remove(BLOCK);
            case "TARGET_ADDED" -> target.put("NOTICE",text("new"));
            case "MANIFEST_DRIFT" -> target.put("META-INF/MANIFEST.MF",text("untrusted profile"));
            case "BINDING_DRIFT" -> target.put(BINDINGS,text("different adapters"));
            case "CLASS_DRIFT" -> target.put(BLOCK,text("different body"));
            default -> throw new AssertionError(defect);
        }
        assertThrows(TransformationException.class,()->plan.verify(source,target));
    }

    @Test void staleClassClaimCannotAcquireOwnershipOfChangedOriginal() {
        var source=source();var target=target(source);var base=base(source);
        source.put(BLOCK,text("Changed original after claim"));
        assertThrows(TransformationException.class,()->CompositionArtifactPlan.resolve(source,target,base,"example",
            false,false,false,false,false,false,false,false,false,false,false,true));
    }

    @ParameterizedTest @ValueSource(strings={"MISSING_TAG","SOURCE_COLLISION","UNOWNED_MINING_TAG","UNOWNED_LOCAL_DATA","UNOWNED_CLASS","UNOWNED_METADATA"})
    void rejectsMissingOrUnownedGeneratedDataBeforeArchivePublication(String defect) {
        var source=source();var target=target(source);
        switch(defect) {
            case "MISSING_TAG" -> target.remove(MINING);
            case "SOURCE_COLLISION" -> source.put(MINING,target.get(MINING).clone());
            case "UNOWNED_MINING_TAG" -> target.put("data/minecraft/tags/blocks/mineable/axe.json",text("{}"));
            case "UNOWNED_LOCAL_DATA" -> target.put("data/example/recipes/unowned.json",text("{}"));
            case "UNOWNED_CLASS" -> target.put("owned/Unexpected.class",text("not executable test bytes"));
            case "UNOWNED_METADATA" -> target.put("META-INF/unowned.json",text("{}"));
            default -> throw new AssertionError(defect);
        }
        assertThrows(TransformationException.class,()->resolve(source,target));
    }

    @ParameterizedTest @ValueSource(strings={"REMOVED","MUTATED_BYTES","REPLACE_TRUE","OTHER_BLOCK","EXTRA_TAG"})
    void frozenPlanRejectsMiningChangesOnReopening(String mutation) {
        var source=source();var target=target(source);var plan=resolve(source,target);
        switch(mutation) {
            case "REMOVED" -> target.remove(MINING);
            case "MUTATED_BYTES" -> target.get(MINING)[0]^=1;
            case "REPLACE_TRUE" -> target.put(MINING,text("{\"replace\":true,\"values\":[\"example:bridge_block\"]}"));
            case "OTHER_BLOCK" -> target.put(MINING,text("{\"replace\":false,\"values\":[\"example:other\"]}"));
            case "EXTRA_TAG" -> target.put("data/minecraft/tags/blocks/mineable/axe.json",text("{}"));
            default -> throw new AssertionError(mutation);
        }
        assertThrows(TransformationException.class,()->plan.verify(source,target));
    }

    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8,9,10})
    void staticShapeCannotImplicitlyCombineWithAnyOtherCandidate(int other) {
        boolean[] flags=new boolean[11];flags[other]=true;
        assertThrows(TransformationException.class,()->CompositionArtifactPlan.metadataRules("example",
            flags[0],flags[1],flags[2],flags[3],flags[4],flags[5],flags[6],flags[7],flags[8],flags[9],flags[10],true));
        var source=source();var target=target(source);
        assertThrows(TransformationException.class,()->CompositionArtifactPlan.resolve(source,target,base(source),"example",
            flags[0],flags[1],flags[2],flags[3],flags[4],flags[5],flags[6],flags[7],flags[8],flags[9],flags[10],true));
    }

    @Test void miningOwnershipRemainsOptInAndDoesNotWidenExistingMetadataGrammar() {
        assertFalse(CompositionArtifactPlan.metadataRules("example").containsKey(MINING));
        var source=source();var target=target(source);
        assertThrows(TransformationException.class,()->CompositionArtifactPlan.resolve(source,target,base(source),"example"));
    }

    private static Map<String,byte[]> source() {
        var entries=new TreeMap<String,byte[]>();
        for(String path:List.of(OWNED,BLOCK,DATA,MODEL,BLOCKSTATE,LANGUAGE,"META-INF/mods.toml","META-INF/MANIFEST.MF","pack.mcmeta","LICENSE"))
            entries.put(path,text("Synthetic structural ownership payload: "+path));
        return entries;
    }
    private static Map<String,byte[]> target(Map<String,byte[]> source) {
        var target=new TreeMap<>(source);target.put(OWNED,text("Synthetic changed class bytes; never executed"));
        target.put(BLOCK,text("Synthetic changed subclass bytes; never executed"));
        target.put("META-INF/MANIFEST.MF",StaticShapeBlockArtifactAdapter.targetManifest("1.0.0"));
        target.put(BINDINGS,json(StaticShapeBlockArtifactAdapter.bindingTree()));
        target.put(MINING,json(BlockMiningAdapter.targetTag("example",List.of("bridge_block"))));
        return target;
    }
    private static CompositionPlan base(Map<String,byte[]> source) {
        return new CompositionPlan(List.of(
            new CompositionPlan.Operation("logic.owned-bodies",CompositionPlan.Kind.PRESERVE_OWNED_BODIES,Set.of(),List.of(claim(source,OWNED),claim(source,BLOCK))),
            new CompositionPlan.Operation("data.recipe-resources",CompositionPlan.Kind.PRESERVE_RECIPE_RESOURCES,Set.of("logic.owned-bodies"),List.of(claim(source,DATA),claim(source,MODEL),claim(source,BLOCKSTATE),claim(source,LANGUAGE)))));
    }
    private static CompositionPlan.Claim claim(Map<String,byte[]> entries,String path) {
        return new CompositionPlan.Claim(path,SimpleContentConverter.hash(entries.get(path)));
    }
    private static CompositionArtifactPlan resolve(Map<String,byte[]> source,Map<String,byte[]> target) {
        return CompositionArtifactPlan.resolve(source,target,base(source),"example",false,false,false,false,false,false,false,false,false,false,false,true);
    }
    private static Map<?,?> find(Map<String,Object> tree,String group,String key,String value) {
        return ((List<?>)tree.get(group)).stream().map(Map.class::cast).filter(e->value.equals(e.get(key))).findFirst().orElseThrow();
    }
    private static byte[] json(Map<String,Object> tree) {
        return text(new CanonicalReportWriter().toJsonTree(SimpleContentConverter.canonicalTree(tree)));
    }
    private static byte[] text(String value) {return value.getBytes(StandardCharsets.UTF_8);}
}


