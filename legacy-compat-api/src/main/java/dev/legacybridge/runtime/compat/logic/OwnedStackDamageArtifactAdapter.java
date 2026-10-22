package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: finite own-stack helper using native damage hooks after identity and range guards.
 * Target API: verified native ItemStack damage operations and native Item.Properties.durability.
 * Transformation: generate private candidate provenance and implemented offline operation bindings.
 * Behavior differences: no raw NBT emulation, hook bypass, maximum clamp or synthesized gameplay caller.
 * Known limitations: capacity 128, one received owned stack, damage-minus-one only; no wear, break,
 * synchronization, thread authority, public export or native/multiplayer qualification.
 */
public final class OwnedStackDamageArtifactAdapter {
    public static final String ID = "owned-stack-damage-candidate-v1";
    private OwnedStackDamageArtifactAdapter() { }
    public static List<String> requiredAdapters() {
        return List.of(AdapterCatalog.LOGIC, AdapterCatalog.STACK_DAMAGE_MANIFEST, AdapterCatalog.STACK_DAMAGE_BINDINGS,
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
            manifest.getMainAttributes().putValue("LegacyBridge-Owned-Stack-Damage-Revision","1");
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification","STATIC-CANDIDATE-ONLY");
            var bytes = new ByteArrayOutputStream(); manifest.write(bytes); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
