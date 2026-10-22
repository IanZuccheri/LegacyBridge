package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: STONE properties of a fixed bottom-half Block plus its outline/collision overrides.
 * Target API: exact native box and BlockBehaviour properties; target solidity derives from cached geometry.
 * Transformation: scoped five-call STONE recipe and private candidate provenance; no runtime shim.
 * Behavior differences: native placement adds BLOCK_PLACE; mining eligibility needs an additive tag.
 * Known limitations: only box(0,0,0,16,8,16), constant shape callbacks, default predicates and finite strength.
 * Geometry/defaults must be independently proved before selecting this recipe; no arbitrary shapes or install.
 */
public final class StaticShapeBlockArtifactAdapter {
    public static final String ID="static-shape-block-candidate-v1";
    private static final String PROPERTIES="net/minecraft/world/level/block/state/BlockBehaviour$Properties";
    private static final String COLOR="net/minecraft/world/level/material/MapColor";
    private static final String INSTRUMENT="net/minecraft/world/level/block/state/properties/NoteBlockInstrument";
    // Independently reviewed contract, not a second lowering implementation or inherited permission.
    private static final List<Map<String,String>> EXPECTED_STONE_CALLS=List.of(
        expected("STATIC",PROPERTIES,"of","()L"+PROPERTIES+";"),
        expected("FIELD",COLOR,"STONE","L"+COLOR+";"),
        expected("VIRTUAL",PROPERTIES,"mapColor","(L"+COLOR+";)L"+PROPERTIES+";"),
        expected("FIELD",INSTRUMENT,"BASEDRUM","L"+INSTRUMENT+";"),
        expected("VIRTUAL",PROPERTIES,"instrument","(L"+INSTRUMENT+";)L"+PROPERTIES+";"));
    private StaticShapeBlockArtifactAdapter() { }

    /** Same native instruction recipe, separately qualified and bound for the exact half-cuboid. */
    public static List<Map<String,String>> stonePropertiesCalls() {
        var calls = PlainBlockArtifactAdapter.stonePropertiesCalls();
        validateStonePropertiesCalls(calls);
        return calls.stream().map(Map::copyOf).toList();
    }
    /** Exact order, fields and descriptors; a full-cube recipe change requires a separate review here. */
    static void validateStonePropertiesCalls(List<Map<String,String>> calls) {
        if (!EXPECTED_STONE_CALLS.equals(calls))
            throw new UnsupportedCompatibilityException("Static half-shape STONE recipe changed; independent review required");
    }
    private static Map<String,String> expected(String kind,String owner,String name,String descriptor) {
        return Map.of("kind",kind,"owner",owner,"name",name,"descriptor",descriptor);
    }
    public static List<String> requiredAdapters() {
        return List.of(AdapterCatalog.LOGIC,AdapterCatalog.PLAIN_BLOCK,AdapterCatalog.STATIC_SHAPE_PROPERTIES,
            AdapterCatalog.STATIC_SHAPE_MANIFEST,AdapterCatalog.STATIC_SHAPE_BINDINGS,
            AdapterCatalog.MINING,AdapterCatalog.METADATA,AdapterCatalog.PACK);
    }
    public static Map<String,Object> bindingTree() {
        return Map.of("schemaVersion",AdapterCatalog.SCHEMA_VERSION,"runtimeVersion",AdapterCatalog.RUNTIME_VERSION,
            "adapters",requiredAdapters().stream().map(AdapterCatalog::require).map(AdapterDescriptor::referenceTree).toList());
    }
    public static byte[] targetManifest(String version) {
        try {
            var manifest=new Manifest(new ByteArrayInputStream(ModMetadataAdapter.targetManifest(version)));
            manifest.getMainAttributes().putValue("LegacyBridge-Profile",ID);
            manifest.getMainAttributes().putValue("LegacyBridge-Static-Shape-Revision","1");
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification","STATIC-CANDIDATE-ONLY");
            var bytes=new ByteArrayOutputStream();manifest.write(bytes);return bytes.toByteArray();
        } catch(IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
