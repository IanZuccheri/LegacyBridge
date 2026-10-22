package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: validated Forge 36 metadata and proven owned integer constructor delegation.
 * Target API: Forge 47 metadata with distinct private candidate provenance.
 * Transformation: emit implemented offline bindings and an explicit unqualified identity.
 * Behavior differences: build provenance replaced; executable bodies remain native code.
 * Known limitations: no runtime shim, arbitrary constructor support, publication or install authority.
 */
public final class ConstructorArtifactAdapter {
    public static final String ID = "owned-constructors-candidate-v1";
    private ConstructorArtifactAdapter() { }
    public static List<String> requiredAdapters() {
        return List.of(AdapterCatalog.LOGIC, AdapterCatalog.CONSTRUCTOR_MANIFEST, AdapterCatalog.CONSTRUCTOR_BINDINGS,
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
            manifest.getMainAttributes().putValue("LegacyBridge-Constructor-Revision", "1");
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification", "STATIC-CANDIDATE-ONLY");
            var bytes = new ByteArrayOutputStream(); manifest.write(bytes); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
