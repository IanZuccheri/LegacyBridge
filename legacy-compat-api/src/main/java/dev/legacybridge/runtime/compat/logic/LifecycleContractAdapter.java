package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.AdapterDescriptor;
import dev.legacybridge.runtime.compat.UnsupportedCompatibilityException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

/**
 * Legacy API: RegistryObject.get():IForgeRegistryEntry and registerConfig(Type,ForgeConfigSpec).
 * Target API: RegistryObject.get():Object and registerConfig(Type,IConfigSpec).
 * Transformation: exact call-site descriptor recipes, never global erasure or runtime emulation.
 * Behavior differences: native config and event delivery remain native; the caller must prove late
 * registry/config use and a separate finite phase/side proof before publication.
 * Known limitations: no handles of these calls, null/future registry emulation, custom IConfigSpec,
 * external dependencies, arbitrary configurations or generic lifecycle contracts.
 */
public final class LifecycleContractAdapter {
    public static final String PROFILE = "lifecycle-preserving-v1";
    private LifecycleContractAdapter() { }

    public static String methodDescriptor(String owner, String name, String before, String structural) {
        if (owner.equals("net/minecraftforge/fml/RegistryObject") && name.equals("get")
                && before.equals("()Lnet/minecraftforge/registries/IForgeRegistryEntry;")) return "()Ljava/lang/Object;";
        if (owner.equals("net/minecraftforge/fml/ModLoadingContext") && name.equals("registerConfig")
                && before.equals("(Lnet/minecraftforge/fml/config/ModConfig$Type;Lnet/minecraftforge/common/ForgeConfigSpec;)V"))
            return "(Lnet/minecraftforge/fml/config/ModConfig$Type;Lnet/minecraftforge/fml/config/IConfigSpec;)V";
        if (before == null || structural == null) throw new UnsupportedCompatibilityException("Null lifecycle descriptor");
        return structural;
    }

    /** Legacy API: inert legacy manifest. Target API: explicit opt-in profile marker.
     * Transformation: serialize validated version and marker. Behavior differences: provenance only.
     * Known limitations: no active/unknown manifest metadata accepted by the caller. */
    public static byte[] targetManifest(String version) {
        try {
            Manifest manifest = new Manifest(new ByteArrayInputStream(PreservedLogicAdapter.targetManifest(version)));
            manifest.getMainAttributes().putValue("LegacyBridge-Profile", PROFILE);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); manifest.write(bytes); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Legacy API: admitted operations. Target API: exact schema-1 adapter bindings.
     * Transformation: build inert bindings. Behavior differences: no execution certificate.
     * Known limitations: only this profile's implemented operation set. */
    public static Map<String, Object> bindingTree() {
        return Map.of("schemaVersion", AdapterCatalog.SCHEMA_VERSION, "runtimeVersion", AdapterCatalog.RUNTIME_VERSION,
                "adapters", List.of(AdapterCatalog.LOGIC, AdapterCatalog.LIFECYCLE, AdapterCatalog.LIFECYCLE_MANIFEST,
                        AdapterCatalog.LIFECYCLE_BINDINGS, AdapterCatalog.METADATA, AdapterCatalog.PACK)
                        .stream().map(AdapterCatalog::require).map(AdapterDescriptor::referenceTree).toList());
    }
}
