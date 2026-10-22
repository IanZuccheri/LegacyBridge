package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: proved finite COMMON reload, owned events and typed server commands with Forge 36 metadata.
 * Target API: Forge 47 metadata with explicit static-only lifecycle-suite candidate provenance.
 * Transformation: generate candidate identity and real offline operation bindings.
 * Behavior differences: build provenance replaced; executable method bodies are not replaced.
 * Known limitations: no arbitrary configuration, runtime proof, profile promotion or installation authority.
 */
public final class LifecycleSuiteAdapter {
    public static final String ID = "lifecycle-suite-candidate-v1";
    private LifecycleSuiteAdapter() { }
    /** Legacy API: ModConfig.getSpec():ForgeConfigSpec.
     * Target API: ModConfig.getSpec():IConfigSpec.
     * Transformation: contextual return-descriptor rewrite, only after whole-graph identity-filter proof.
     * Behavior differences: erased interface return, never a global ForgeConfigSpec mapping.
     * Known limitations: no dereference, cast, custom spec or method-handle contract. */
    public static String methodDescriptor(String owner,String name,String before,String structural) {
        if (owner.equals("net/minecraftforge/fml/config/ModConfig") && name.equals("getSpec")
                && before.equals("()Lnet/minecraftforge/common/ForgeConfigSpec;"))
            return "()Lnet/minecraftforge/fml/config/IConfigSpec;";
        return java.util.Objects.requireNonNull(structural);
    }
    public static List<String> requiredAdapters() {
        return List.of(AdapterCatalog.LOGIC, AdapterCatalog.LIFECYCLE, AdapterCatalog.CONFIG_SPEC_IDENTITY, AdapterCatalog.LIFECYCLE_SUITE_MANIFEST, AdapterCatalog.LIFECYCLE_SUITE_BINDINGS,
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
            manifest.getMainAttributes().putValue("LegacyBridge-Lifecycle-Suite-Revision", "1");
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification", "STATIC-CANDIDATE-ONLY");
            var bytes = new ByteArrayOutputStream(); manifest.write(bytes); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
