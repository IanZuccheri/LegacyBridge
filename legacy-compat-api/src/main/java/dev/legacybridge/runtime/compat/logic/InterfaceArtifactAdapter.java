package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: proved finite owned interface/default dispatch with Forge 36 metadata.
 * Target API: Forge 47 metadata with explicit static-only interface-candidate provenance.
 * Transformation: generate candidate identity and real offline operation bindings.
 * Behavior differences: build provenance replaced; executable method bodies are not replaced.
 * Known limitations: no general default resolution, runtime proof, profile promotion or installation authority.
 */
public final class InterfaceArtifactAdapter {
    public static final String ID = "owned-interface-candidate-v1";
    private InterfaceArtifactAdapter() { }
    public static List<String> requiredAdapters() {
        return List.of(AdapterCatalog.LOGIC, AdapterCatalog.INTERFACE_MANIFEST, AdapterCatalog.INTERFACE_BINDINGS,
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
            manifest.getMainAttributes().putValue("LegacyBridge-Interface-Revision", "1");
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification", "STATIC-CANDIDATE-ONLY");
            var bytes = new ByteArrayOutputStream(); manifest.write(bytes); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
