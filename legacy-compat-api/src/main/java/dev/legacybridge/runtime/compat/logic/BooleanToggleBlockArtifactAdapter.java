package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: one BooleanProperty and Block.use guarded by World.isClientSide.
 * Target API: exact native state, Level.isClientSide, setBlock and InteractionResult declarations.
 * Transformation: private manifest/binding provenance; owned interaction bytecode stays intact.
 * Behavior differences: native target placement/update hooks and interaction protocol checks apply;
 * CONSUME reports handled input, not successful setBlock. No events or item changes are invented.
 * Known limitations: full cube, false default, one callback-position server write with flags 3;
 * no async/network/inventory/loot/redstone, arbitrary callbacks or public installation authority.
 */
public final class BooleanToggleBlockArtifactAdapter {
    public static final String ID = "boolean-toggle-block-candidate-v1";
    private BooleanToggleBlockArtifactAdapter() { }
    public static List<String> requiredAdapters() {
        return List.of(AdapterCatalog.LOGIC,AdapterCatalog.PLAIN_BLOCK,AdapterCatalog.PLAIN_BLOCK_PROPERTIES,
            AdapterCatalog.BOOLEAN_TOGGLE_BLOCK_MANIFEST,AdapterCatalog.BOOLEAN_TOGGLE_BLOCK_BINDINGS,
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
            manifest.getMainAttributes().putValue("LegacyBridge-Boolean-Toggle-Revision","1");
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification","STATIC-CANDIDATE-ONLY");
            var bytes = new ByteArrayOutputStream(); manifest.write(bytes); return bytes.toByteArray();
        } catch(IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
