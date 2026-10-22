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
 * Legacy API: native Food.Builder nutrition/saturation/build and Item.Properties.food in Forge 36.2.42.
 * Target API: FoodProperties.Builder and Item.Properties.food in Forge 47.4.23.
 * Transformation: checked structural descriptor components only; retain the original constructor and supplier.
 * Behavior differences: target native eating/use emits EAT/ITEM_INTERACT_START/ITEM_INTERACT_FINISH game
 * events and queries stack/entity-sensitive food.
 * The closed caller envelope has no overrides, effects or remainder; target game-event behavior is explicit,
 * not an assertion that old and new worlds react identically. No eating call or game code is emulated here.
 * Known limitations: literal plain-food constructor only; no custom use, flags, containers, effects, tools,
 * blocks, food-property overrides, active listeners, third-party hooks or universal consumption equivalence.
 */
public final class FoodContractAdapter {
    public static final String PROFILE = "food-item-v1";
    private FoodContractAdapter() { }

    public static String remapDescriptor(String descriptor, Map<String, String> classes) {
        if (classes == null) throw new UnsupportedCompatibilityException("Missing reviewed food component map");
        for (String suffix : List.of("", "$Builder")) {
            String legacy = "net/minecraft/item/Food" + suffix;
            if (classes.containsKey(legacy) && !("net/minecraft/world/food/FoodProperties" + suffix).equals(classes.get(legacy)))
                throw new UnsupportedCompatibilityException("Unreviewed food class equivalence");
        }
        return PreservedLogicAdapter.remapDescriptor(descriptor, classes);
    }

    /** Legacy API: validated inert manifest. Target API: exact profile marker and preserved version.
     * Transformation: serialize inert provenance. Behavior differences: no gameplay certificate.
     * Known limitations: active manifest hooks and arbitrary metadata must already be rejected. */
    public static byte[] targetManifest(String version) {
        try {
            Manifest manifest = new Manifest(new ByteArrayInputStream(PreservedLogicAdapter.targetManifest(version)));
            manifest.getMainAttributes().putValue("LegacyBridge-Profile", PROFILE);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            manifest.write(output);
            return output.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Legacy API: proven operations. Target API: schema-1 implemented adapter identities.
     * Transformation: serialize finite bindings. Behavior differences: adds non-executable provenance.
     * Known limitations: not permission for other food or content contracts. */
    public static Map<String, Object> bindingTree() {
        return Map.of("schemaVersion", AdapterCatalog.SCHEMA_VERSION, "runtimeVersion", AdapterCatalog.RUNTIME_VERSION,
                "adapters", List.of(AdapterCatalog.LOGIC, AdapterCatalog.FOOD, AdapterCatalog.FOOD_MANIFEST,
                        AdapterCatalog.FOOD_BINDINGS, AdapterCatalog.METADATA, AdapterCatalog.PACK)
                        .stream().map(AdapterCatalog::require).map(AdapterDescriptor::referenceTree).toList());
    }
}
