package dev.legacybridge.converter.conversion;

import java.util.List;
import java.util.Map;

/**
 * Authored, version-pinned schema policy, not copied Minecraft mappings or a runtime shim.
 * Legacy API: Forge 36.2.42 CookingRecipeSerializer / SingleItemRecipe.Serializer JSON.
 * Target API: Forge 47.4.23 SimpleCookingSerializer / SingleItemRecipe.Serializer JSON.
 * Transformation: none for approved recipe/tag bytes; the existing pack adapter alone
 * migrates pack metadata. See docs/RESOURCE_API_BASELINES.md for declarations/provenance.
 * Behavior differences: target cooking recipe-book category defaults to MISC; no claim
 * of identical book grouping, machine ticks, experience awards or network behavior.
 * Known limitations: no conditions, NBT, custom ingredients/serializers or external tags.
 */
public final class DatapackRecipeContract {
    public static final String ID = "datapack-recipes-v1";
    public static final Map<String, Integer> COOKING_DEFAULT_TICKS = Map.of(
            "minecraft:smelting", 200, "minecraft:blasting", 100,
            "minecraft:smoking", 100, "minecraft:campfire_cooking", 100);
    private DatapackRecipeContract() { }

    static Map<String, Object> report(String status, List<Map<String, Object>> recipes) {
        return Map.ofEntries(Map.entry("schemaVersion", 1), Map.entry("contract", ID),
                Map.entry("source", "minecraft:1.16.5/forge:36.2.42"),
                Map.entry("target", "minecraft:1.20.1/forge:47.4.23"),
                Map.entry("status", status), Map.entry("recipes", List.copyOf(recipes)),
                Map.entry("baseResourceContract", "simple-content-v1; tag references restricted to local closure"),
                Map.entry("provenance", "docs/RESOURCE_API_BASELINES.md"),
                Map.entry("runtimeValidation", "NOT_RUN"),
                Map.entry("differences", List.of("Target cooking recipe-book category defaults to MISC; client grouping is not validated.")),
                Map.entry("unsupported", List.of("conditional recipes", "custom serializers and ingredients", "NBT recipe results",
                        "external tag composition", "loot tables and global loot modifiers", "advancements",
                        "functions and commands", "animated textures", "custom model behavior", "local Ogg audio")));
    }
}
