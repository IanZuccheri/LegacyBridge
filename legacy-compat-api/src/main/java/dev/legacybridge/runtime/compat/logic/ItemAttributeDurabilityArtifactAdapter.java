package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: finite MAINHAND modifiers, native UUID identities, durability 128 and iron repair predicate.
 * Target API: native attribute and repair overrides, Properties.durability and verified Guava ABI.
 * Transformation: generate private candidate provenance and implemented offline operation bindings.
 * Behavior differences: no hook bypass or behavior emulation; target-native NBT/event/equipment code.
 * Known limitations: no wear, breakage, full anvil semantics, public export or native/multiplayer qualification.
 */
public final class ItemAttributeDurabilityArtifactAdapter {
    public static final String ID = "item-attribute-durability-candidate-v1";
    private ItemAttributeDurabilityArtifactAdapter() { }
    public static List<String> requiredAdapters() {
        return List.of(AdapterCatalog.LOGIC, AdapterCatalog.ATTRIBUTE_DURABILITY_MANIFEST, AdapterCatalog.ATTRIBUTE_DURABILITY_BINDINGS,
                AdapterCatalog.METADATA, AdapterCatalog.PACK);
    }
    public static Map<String,Object> bindingTree() {
        return Map.of("schemaVersion",AdapterCatalog.SCHEMA_VERSION,"runtimeVersion",AdapterCatalog.RUNTIME_VERSION,
                "adapters",requiredAdapters().stream().map(AdapterCatalog::require).map(AdapterDescriptor::referenceTree).toList());
    }
    public static byte[] targetManifest(String version) {
        try {
            var manifest = new Manifest(new ByteArrayInputStream(ModMetadataAdapter.targetManifest(version)));
            manifest.getMainAttributes().putValue("LegacyBridge-Profile",ID);
            manifest.getMainAttributes().putValue("LegacyBridge-Item-Attribute-Durability-Revision","1");
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification","STATIC-CANDIDATE-ONLY");
            var bytes = new ByteArrayOutputStream(); manifest.write(bytes); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
