package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.composition.CompositionPlan;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.transformation.TransformationException;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.logic.HorizontalBlockArtifactAdapter;
import dev.legacybridge.runtime.compat.world.BlockMiningAdapter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic byte-ownership tests only: not API, JSON parser, bytecode or game qualification. */
class HorizontalBlockArtifactPlanTest {
    private static final String OWNED = "owned/BlockContent.class";
    private static final String DATA = "data/example/recipes/block.json";
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
        assertEquals(AdapterCatalog.HORIZONTAL_BLOCK_BINDINGS,((Map<?,?>)find(tree,"entries","path",BINDINGS).get("adapter")).get("id"));
        assertEquals(AdapterCatalog.HORIZONTAL_BLOCK_MANIFEST,((Map<?,?>)find(tree,"entries","path","META-INF/MANIFEST.MF").get("adapter")).get("id"));
        assertArrayEquals(source.get(DATA),target.get(DATA));
        source.clear(); target.clear(); assertEquals(tree,plan.toTree());
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

    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8,9})
    void horizontalBlockCannotImplicitlyCombineWithAnyOtherCandidate(int other) {
        boolean[] flags=new boolean[10];flags[other]=true;
        assertThrows(TransformationException.class,()->CompositionArtifactPlan.metadataRules("example",
            flags[0],flags[1],flags[2],flags[3],flags[4],flags[5],flags[6],flags[7],flags[8],flags[9],true));
        var source=source();var target=target(source);
        assertThrows(TransformationException.class,()->CompositionArtifactPlan.resolve(source,target,base(source),"example",
            flags[0],flags[1],flags[2],flags[3],flags[4],flags[5],flags[6],flags[7],flags[8],flags[9],true));
    }

    @Test void miningOwnershipRemainsOptInAndDoesNotWidenExistingMetadataGrammar() {
        assertFalse(CompositionArtifactPlan.metadataRules("example").containsKey(MINING));
        var source=source();var target=target(source);
        assertThrows(TransformationException.class,()->CompositionArtifactPlan.resolve(source,target,base(source),"example"));
    }

    private static Map<String,byte[]> source() {
        var entries=new TreeMap<String,byte[]>();
        for(String path:List.of(OWNED,DATA,"META-INF/mods.toml","META-INF/MANIFEST.MF","pack.mcmeta","LICENSE"))
            entries.put(path,text("Synthetic structural ownership payload: "+path));
        return entries;
    }
    private static Map<String,byte[]> target(Map<String,byte[]> source) {
        var target=new TreeMap<>(source);target.put(OWNED,text("Synthetic changed class bytes; never executed"));
        target.put("META-INF/MANIFEST.MF",HorizontalBlockArtifactAdapter.targetManifest("1.0.0"));
        target.put(BINDINGS,json(HorizontalBlockArtifactAdapter.bindingTree()));
        target.put(MINING,json(BlockMiningAdapter.targetTag("example",List.of("bridge_block"))));
        return target;
    }
    private static CompositionPlan base(Map<String,byte[]> source) {
        return new CompositionPlan(List.of(
            new CompositionPlan.Operation("logic.owned-bodies",CompositionPlan.Kind.PRESERVE_OWNED_BODIES,Set.of(),List.of(claim(source,OWNED))),
            new CompositionPlan.Operation("data.recipe-resources",CompositionPlan.Kind.PRESERVE_RECIPE_RESOURCES,Set.of("logic.owned-bodies"),List.of(claim(source,DATA)))));
    }
    private static CompositionPlan.Claim claim(Map<String,byte[]> entries,String path) {
        return new CompositionPlan.Claim(path,SimpleContentConverter.hash(entries.get(path)));
    }
    private static CompositionArtifactPlan resolve(Map<String,byte[]> source,Map<String,byte[]> target) {
        return CompositionArtifactPlan.resolve(source,target,base(source),"example",false,false,false,false,false,false,false,false,false,false,true);
    }
    private static Map<?,?> find(Map<String,Object> tree,String group,String key,String value) {
        return ((List<?>)tree.get(group)).stream().map(Map.class::cast).filter(e->value.equals(e.get(key))).findFirst().orElseThrow();
    }
    private static byte[] json(Map<String,Object> tree) {
        return text(new CanonicalReportWriter().toJsonTree(SimpleContentConverter.canonicalTree(tree)));
    }
    private static byte[] text(String value) {return value.getBytes(StandardCharsets.UTF_8);}
}
