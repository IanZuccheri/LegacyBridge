package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: finite owned Block state-definition and placement overrides, Forge 36 registration.
 * Target API: exact native Block/StateDefinition/BlockPlaceContext declarations on Forge 47.
 * Transformation: implemented OFFLINE private manifest and operation provenance, no runtime emulation.
 * Behavior differences: target placement emits BLOCK_PLACE; source STONE expansion/mining tag are separate operations.
 * Known limitations: one horizontal property, default NORTH, opposite placement, optional complete native rotation/mirror pair,
 * full cube only; no general callbacks or public install.
 */
public final class HorizontalBlockArtifactAdapter {
    public static final String ID = "horizontal-block-candidate-v1";
    private HorizontalBlockArtifactAdapter() { }
    public static List<String> requiredAdapters() {
        return List.of(AdapterCatalog.LOGIC,AdapterCatalog.PLAIN_BLOCK,AdapterCatalog.PLAIN_BLOCK_PROPERTIES,
            AdapterCatalog.HORIZONTAL_BLOCK_MANIFEST,AdapterCatalog.HORIZONTAL_BLOCK_BINDINGS,
            AdapterCatalog.MINING,AdapterCatalog.METADATA,AdapterCatalog.PACK);
    }
    public static Map<String,Object> bindingTree() {
        return Map.of("schemaVersion",AdapterCatalog.SCHEMA_VERSION,"runtimeVersion",AdapterCatalog.RUNTIME_VERSION,
            "adapters",requiredAdapters().stream().map(AdapterCatalog::require).map(AdapterDescriptor::referenceTree).toList());
    }
    public static byte[] targetManifest(String version) {
        try {
            var manifest = new Manifest(new ByteArrayInputStream(ModMetadataAdapter.targetManifest(version)));
            manifest.getMainAttributes().putValue("LegacyBridge-Profile",ID);
            manifest.getMainAttributes().putValue("LegacyBridge-Horizontal-Block-Revision","1");
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification","STATIC-CANDIDATE-ONLY");
            var bytes = new ByteArrayOutputStream(); manifest.write(bytes); return bytes.toByteArray();
        } catch(IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
