package dev.legacybridge.runtime.compat.registry;

import dev.legacybridge.runtime.compat.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.jar.Manifest;

/**
 * Legacy API: the two hash-pinned authored deps-cohort-source-v1 metadata documents.
 * Target API: javafml 47, Minecraft 1.20.1, unchanged direct library dependency and runtime ABI.
 * Transformation: update platform requirements and add explicit offline-operation provenance.
 * Behavior differences: target-only loading and a required LegacyBridge runtime; no gameplay rewrite.
 * Known limitations: private finite candidates, not general dependency migration or installation
 * approval. All code bytes require a separately reviewed recipe. No legacy classes are loaded here.
 */
public final class CohortMetadataAdapter {
    public static final String ID = "finite-cohort-pair-v1";
    private static final Map<String, String> TOML = Map.of(
            "lb_cohort_library", "a89f02750d6b4946e9fecf9fac44b270ebad3fb4238ca0e4fe390e6508babfbd",
            "lb_cohort_consumer", "634d373bbb25e34c2893fb159a196749cffb698433e30c4af1adb3d03efb08b4");
    private static final Map<String, String> MANIFEST = Map.of(
            "lb_cohort_library", "ecd8fcfdc7df4347310f032ee3170b3d787bb1afb8cb87e56ad032b6adf9eafb",
            "lb_cohort_consumer", "255ac7b429e91746f8b46010ac7dadaef0ead3a38aa15d7d625a931aaef4b884");
    private CohortMetadataAdapter() { }

    public static byte[] targetToml(String modId, byte[] source) {
        pinned(TOML, modId, source);
        var ranges = ModMetadataAdapter.targetRanges("1.16.5", "36.2.42");
        String text = new String(source, StandardCharsets.UTF_8)
                .replace("loaderVersion=\"[36,)\"", "loaderVersion=\"" + ranges.get("loaderVersion") + "\"")
                .replace("versionRange=\"[36.2.42]\"", "versionRange=\"" + ranges.get("forge") + "\"")
                .replace("versionRange=\"[1.16.5]\"", "versionRange=\"" + ranges.get("minecraft") + "\"");
        return (text + "\n[[dependencies." + modId + "]]\nmodId=\"legacybridge_runtime\"\nmandatory=true\nversionRange=\""
                + ranges.get("legacybridge_runtime") + "\"\nordering=\"AFTER\"\nside=\"BOTH\"\n").getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] targetManifest(String modId, byte[] source) {
        pinned(MANIFEST, modId, source);
        try {
            var manifest = new Manifest(new ByteArrayInputStream(source));
            manifest.getMainAttributes().putValue("LegacyBridge-Profile", ID);
            manifest.getMainAttributes().putValue("LegacyBridge-Qualification", "STATIC-CANDIDATE-ONLY");
            manifest.getMainAttributes().putValue("LegacyBridge-Runtime-Version", AdapterCatalog.RUNTIME_VERSION);
            var bytes = new ByteArrayOutputStream(); manifest.write(bytes); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException("Pinned manifest is invalid", impossible); }
    }

    public static Map<String, Object> bindingTree() {
        return Map.of("schemaVersion", AdapterCatalog.SCHEMA_VERSION, "runtimeVersion", AdapterCatalog.RUNTIME_VERSION,
                "adapters", List.of(AdapterCatalog.COHORT_TOML, AdapterCatalog.COHORT_MANIFEST, AdapterCatalog.COHORT_BINDINGS)
                        .stream().map(AdapterCatalog::require).map(AdapterDescriptor::referenceTree).toList());
    }

    private static void pinned(Map<String, String> hashes, String id, byte[] bytes) {
        if (bytes == null || bytes.length > 4096 || !hashes.containsKey(id))
            throw new UnsupportedCompatibilityException("Finite authored cohort metadata required");
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!hash.equals(hashes.get(id))) throw new UnsupportedCompatibilityException("Unreviewed cohort metadata bytes");
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
