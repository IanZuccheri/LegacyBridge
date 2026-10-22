package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: plain STONE Properties.of(Material), native Block/BlockItem and deferred get.
 * Target API: native full-cube BlockBehaviour properties and registries.RegistryObject.get.
 * Transformation: finite canonical instruction recipe, contextual return erasure and private metadata.
 * Behavior differences: additive pickaxe tag is mandatory; target placement emits BLOCK_PLACE.
 * Known limitations: no custom states, loot, shapes, subclasses, creative tabs or public install authority.
 */
public final class PlainBlockArtifactAdapter {
    public static final String ID = "plain-block-candidate-v1";
    private static final String PROPERTIES = "net/minecraft/world/level/block/state/BlockBehaviour$Properties";
    private static final String COLOR = "net/minecraft/world/level/material/MapColor";
    private static final String INSTRUMENT = "net/minecraft/world/level/block/state/properties/NoteBlockInstrument";
    private PlainBlockArtifactAdapter() { }

    /** Canonical symbols only. The engine must resolve and check every actual target declaration. */
    public static List<Map<String,String>> stonePropertiesCalls() {
        return List.of(call("STATIC", PROPERTIES, "of", "()L"+PROPERTIES+";"),
            call("FIELD", COLOR, "STONE", "L"+COLOR+";"),
            call("VIRTUAL", PROPERTIES, "mapColor", "(L"+COLOR+";)L"+PROPERTIES+";"),
            call("FIELD", INSTRUMENT, "BASEDRUM", "L"+INSTRUMENT+";"),
            call("VIRTUAL", PROPERTIES, "instrument", "(L"+INSTRUMENT+";)L"+PROPERTIES+";"));
    }
    private static Map<String,String> call(String kind,String owner,String name,String descriptor) {
        return Map.of("kind",kind,"owner",owner,"name",name,"descriptor",descriptor);
    }
    public static String registryDescriptor(String owner,String name,String legacy,String structural) {
        return owner.equals("net/minecraftforge/fml/RegistryObject") && name.equals("get")
            && legacy.equals("()Lnet/minecraftforge/registries/IForgeRegistryEntry;")
            ? "()Ljava/lang/Object;" : structural;
    }
    public static List<String> requiredAdapters() {
        return List.of(AdapterCatalog.LOGIC, AdapterCatalog.PLAIN_BLOCK, AdapterCatalog.PLAIN_BLOCK_PROPERTIES,
            AdapterCatalog.PLAIN_BLOCK_MANIFEST, AdapterCatalog.PLAIN_BLOCK_BINDINGS,
            AdapterCatalog.MINING, AdapterCatalog.METADATA, AdapterCatalog.PACK);
    }
    public static Map<String,Object> bindingTree() {
        return Map.of("schemaVersion",AdapterCatalog.SCHEMA_VERSION,"runtimeVersion",AdapterCatalog.RUNTIME_VERSION,
            "adapters",requiredAdapters().stream().map(AdapterCatalog::require).map(AdapterDescriptor::referenceTree).toList());
    }
    public static byte[] targetManifest(String version) {
        try {
            var manifest=new Manifest(new ByteArrayInputStream(ModMetadataAdapter.targetManifest(version)));
            manifest.getMainAttributes().putValue("LegacyBridge-Profile",ID);
            manifest.getMainAttributes().putValue("LegacyBridge-Plain-Block-Revision","1");
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification","STATIC-CANDIDATE-ONLY");
            var bytes=new ByteArrayOutputStream();manifest.write(bytes);return bytes.toByteArray();
        } catch(IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
