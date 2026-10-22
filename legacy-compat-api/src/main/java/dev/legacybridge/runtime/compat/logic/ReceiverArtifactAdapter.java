package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: proved finite owned this-bound virtual handle with Forge 36 metadata.
 * Target API: Forge 47 metadata with explicit static-only receiver-candidate provenance.
 * Transformation: generate candidate identity and real offline operation bindings.
 * Behavior differences: build provenance replaced; executable method bodies are not replaced.
 * Known limitations: no general receiver handles, runtime proof, profile promotion or installation authority.
 */
public final class ReceiverArtifactAdapter {
    public static final String ID = "owned-receiver-candidate-v1";
    private ReceiverArtifactAdapter() { }
    public static List<String> requiredAdapters() {
        return List.of(AdapterCatalog.LOGIC, AdapterCatalog.RECEIVER_MANIFEST, AdapterCatalog.RECEIVER_BINDINGS,
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
            manifest.getMainAttributes().putValue("LegacyBridge-Receiver-Revision", "1");
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification", "STATIC-CANDIDATE-ONLY");
            var bytes = new ByteArrayOutputStream(); manifest.write(bytes); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
