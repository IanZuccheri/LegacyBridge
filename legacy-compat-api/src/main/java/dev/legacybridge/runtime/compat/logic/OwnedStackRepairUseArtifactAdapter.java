package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: finite Item.use callback, received-hand lookup, logical side and guarded repair helper.
 * Target API: verified Item.use, Player inherited hand lookup, Level flag and InteractionResultHolder.
 * Transformation: generate private candidate provenance and implemented offline operation bindings.
 * Behavior differences: client SUCCESS predicts swing without mutation; server CONSUME/PASS retains the same stack.
 * Native creative restoration, cancellation, cooldown and prediction paths remain unchanged.
 * Known limitations: capacity 128, one helper call per eligible server callback; no costs, wear, break,
 * synchronization, thread authority, public export or native/multiplayer qualification.
 */
public final class OwnedStackRepairUseArtifactAdapter {
    public static final String ID = "owned-stack-repair-use-candidate-v1";
    private OwnedStackRepairUseArtifactAdapter() { }
    public static List<String> requiredAdapters() {
        return List.of(AdapterCatalog.LOGIC, AdapterCatalog.STACK_REPAIR_USE_MANIFEST, AdapterCatalog.STACK_REPAIR_USE_BINDINGS,
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
            manifest.getMainAttributes().putValue("LegacyBridge-Owned-Stack-Repair-Use-Revision","1");
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification","STATIC-CANDIDATE-ONLY");
            var bytes = new ByteArrayOutputStream(); manifest.write(bytes); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
