package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: validated inert Forge 36 manifest and independently proven logic/recipe operations.
 * Target API: Forge 47 JAR manifest plus LegacyBridge schema-1 adapter identity metadata.
 * Transformation: emit a distinct composition identity and exactly implemented operation bindings.
 * Behavior differences: build provenance is replaced; metadata is NOT runtime qualification.
 * Known limitations: private composed-logic-recipes-v1 candidates only, no installation approval,
 * gameplay shim, new symbol equivalence, runtime capability or public profile promotion.
 */
public final class ComposedArtifactAdapter {
    public static final String ID = "composed-logic-recipes-v1";
    private ComposedArtifactAdapter() { }

    public static List<String> requiredAdapters() {
        return List.of(AdapterCatalog.LOGIC, AdapterCatalog.COMPOSE_MANIFEST,
                AdapterCatalog.COMPOSE_BINDINGS, AdapterCatalog.METADATA, AdapterCatalog.PACK);
    }

    public static Map<String, Object> bindingTree() {
        return Map.of("schemaVersion", AdapterCatalog.SCHEMA_VERSION, "runtimeVersion", AdapterCatalog.RUNTIME_VERSION,
                "adapters", requiredAdapters().stream().map(AdapterCatalog::require).map(AdapterDescriptor::referenceTree).toList());
    }

    public static byte[] targetManifest(String version) {
        try {
            Manifest manifest = new Manifest(new ByteArrayInputStream(ModMetadataAdapter.targetManifest(version)));
            manifest.getMainAttributes().putValue("LegacyBridge-Profile", ID);
            manifest.getMainAttributes().putValue("LegacyBridge-Composition-Revision", "1");
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification", "STATIC-CANDIDATE-ONLY");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            manifest.write(bytes);
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
