package dev.legacybridge.runtime.compat.logic;

import dev.legacybridge.runtime.compat.UnsupportedCompatibilityException;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.AdapterDescriptor;
import java.util.jar.Manifest;

/**
 * Legacy API: reviewed 1.16.5 item-foil, plain item properties and finite native deferred registration descriptors.
 * Target API: their separately reviewed 1.20.1 declarations; RegistryObject.get is NOT one of these contracts.
 * Transformation: substitute verified object-type components without changing primitive types, array rank,
 * argument order or arity; retain original owned method bodies and serialize the explicit new profile marker.
 * Behavior differences: registration name ownership moves to native Forge47 registration. This operation
 * alone proves no API semantics: the converter must supply exact independent contract recipes and linkage gates.
 * Known limitations: finite item-foil profile only; no generic adapter for removed APIs, changed erasure,
 * reflection, scheduling, rendering frequency, arbitrary dependencies or a renamed-but-unreviewed method.
 */
public final class PreservedLogicAdapter {
    public static final String PROFILE = "logic-preserving-v1";
    private PreservedLogicAdapter() { }

    public static String remapDescriptor(String descriptor, Map<String, String> classes) {
        if (descriptor == null || descriptor.isEmpty() || descriptor.length() > 4096 || classes == null)
            throw new UnsupportedCompatibilityException("Invalid bounded descriptor mapping request");
        StringBuilder result = new StringBuilder();
        int[] offset = {0};
        if (descriptor.charAt(0) == '(') {
            result.append('('); offset[0]++;
            while (offset[0] < descriptor.length() && descriptor.charAt(offset[0]) != ')') type(descriptor, offset, result, classes, false);
            if (offset[0] >= descriptor.length()) fail();
            result.append(')'); offset[0]++;
            type(descriptor, offset, result, classes, true);
        } else type(descriptor, offset, result, classes, false);
        if (offset[0] != descriptor.length()) fail();
        return result.toString();
    }

    private static void type(String descriptor, int[] offset, StringBuilder result, Map<String, String> classes, boolean allowVoid) {
        if (offset[0] >= descriptor.length()) fail();
        int arrays = 0;
        while (offset[0] < descriptor.length() && descriptor.charAt(offset[0]) == '[') {
            if (++arrays > 255) fail();
            result.append('['); offset[0]++;
        }
        if (offset[0] >= descriptor.length()) fail();
        char kind = descriptor.charAt(offset[0]++);
        if ("ZCBSIFJD".indexOf(kind) >= 0 || kind == 'V' && allowVoid && arrays == 0) { result.append(kind); return; }
        if (kind != 'L') fail();
        int end = descriptor.indexOf(';', offset[0]);
        if (end < 0) fail();
        String before = descriptor.substring(offset[0], end);
        String after = classes.getOrDefault(before, before);
        if (!internalName(before) || !internalName(after)) fail();
        result.append('L').append(after).append(';'); offset[0] = end + 1;
    }

    private static boolean internalName(String name) {
        return name != null && name.matches("[A-Za-z_$][A-Za-z0-9_$]*(/[A-Za-z_$][A-Za-z0-9_$]*)*");
    }
    private static void fail() { throw new UnsupportedCompatibilityException("Malformed descriptor or non-structural type substitution"); }

    /** Legacy API: proven conversion operations. Target API: schema-1 adapter identity metadata.
     * Transformation: build only implemented profile bindings. Behavior differences: inert provenance is added.
     * Known limitations: not a runtime linkage or semantic certificate; exact catalog revisions only. */
    public static Map<String, Object> bindingTree() {
        return Map.of("schemaVersion", AdapterCatalog.SCHEMA_VERSION, "runtimeVersion", AdapterCatalog.RUNTIME_VERSION,
                "adapters", List.of(AdapterCatalog.LOGIC, AdapterCatalog.LOGIC_MANIFEST, AdapterCatalog.LOGIC_BINDINGS,
                        AdapterCatalog.METADATA, AdapterCatalog.PACK).stream().map(AdapterCatalog::require).map(AdapterDescriptor::referenceTree).toList());
    }

    public static byte[] targetManifest(String version) {
        try {
            Manifest manifest = new Manifest(new ByteArrayInputStream(ModMetadataAdapter.targetManifest(version)));
            manifest.getMainAttributes().putValue("LegacyBridge-Profile", PROFILE);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); manifest.write(bytes);
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
