package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: finite native durable Item and iron-ingredient repair override on Forge 36.
 * Target API: the corresponding native Forge 47 Item override, separately proved.
 * Transformation: generate distinct private candidate provenance and offline operation bindings.
 * Behavior differences: target-native hooks remain; no body replacement or damage emulation.
 * Known limitations: no tools, armor, breakage/anvil qualification, public export or install authority.
 */
public final class DurableItemArtifactAdapter {
    public static final String ID = "durable-item-candidate-v1";
    private DurableItemArtifactAdapter() { }
    public static List<String> requiredAdapters() {
        return List.of(AdapterCatalog.LOGIC, AdapterCatalog.DURABLE_MANIFEST, AdapterCatalog.DURABLE_BINDINGS,
                AdapterCatalog.METADATA, AdapterCatalog.PACK);
    }
    public static Map<String,Object> bindingTree() {
        return Map.of("schemaVersion", AdapterCatalog.SCHEMA_VERSION, "runtimeVersion", AdapterCatalog.RUNTIME_VERSION,
                "adapters", requiredAdapters().stream().map(AdapterCatalog::require).map(AdapterDescriptor::referenceTree).toList());
    }
    public static byte[] targetManifest(String version) {
        try {
            var manifest = new Manifest(new ByteArrayInputStream(ModMetadataAdapter.targetManifest(version)));
            manifest.getMainAttributes().putValue("LegacyBridge-Profile", ID);
            manifest.getMainAttributes().putValue("LegacyBridge-Durable-Item-Revision", "1");
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification", "STATIC-CANDIDATE-ONLY");
            var bytes = new ByteArrayOutputStream(); manifest.write(bytes); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
