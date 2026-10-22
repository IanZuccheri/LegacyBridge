package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: finite Item MAINHAND immutable damage/speed modifiers and inherited UUID objects.
 * Target API: native Item attribute override with verified Guava ABI and protected UUID fields.
 * Transformation: generate private candidate provenance and implemented offline operation bindings.
 * Behavior differences: no hook bypass or behavior emulation; target-native NBT/event/equipment code.
 * Known limitations: no public export, installation, arbitrary attributes, combat or multiplayer proof.
 */
public final class ItemAttributeArtifactAdapter {
    public static final String ID = "item-attributes-candidate-v1";
    private ItemAttributeArtifactAdapter() { }
    public static List<String> requiredAdapters() {
        return List.of(AdapterCatalog.LOGIC, AdapterCatalog.ATTRIBUTE_MANIFEST, AdapterCatalog.ATTRIBUTE_BINDINGS,
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
            manifest.getMainAttributes().putValue("LegacyBridge-Item-Attributes-Revision","1");
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification","STATIC-CANDIDATE-ONLY");
            var bytes = new ByteArrayOutputStream(); manifest.write(bytes); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
