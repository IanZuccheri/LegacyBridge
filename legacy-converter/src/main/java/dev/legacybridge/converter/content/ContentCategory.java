package dev.legacybridge.converter.content;

/**
 * Static content categories found in a mod JAR.
 *
 * <p>Asset and data-pack entries are assigned one primary category. A
 * non-canonical path can additionally be recorded under
 * {@link #NON_CANONICAL_RESOURCE_PATH}.</p>
 */
public enum ContentCategory {
    PACK_METADATA,
    PACK_ICON,

    ASSET_BLOCKSTATES,
    ASSET_MODELS,
    ASSET_TEXTURES,
    ASSET_LANG,
    ASSET_SOUNDS,
    ASSET_SHADERS,
    ASSET_FONTS,
    ASSET_PARTICLES,
    ASSET_ATLASES,
    ASSET_OTHER,

    DATA_RECIPES,
    DATA_LOOT_TABLES,
    DATA_WORLDGEN,
    DATA_DIMENSIONS,
    DATA_DIMENSION_TYPES,
    DATA_TAGS,
    DATA_ADVANCEMENTS,
    DATA_FUNCTIONS,
    DATA_PREDICATES,
    DATA_ITEM_MODIFIERS,
    DATA_STRUCTURES,
    DATA_FORGE_LOOT_MODIFIERS,
    DATA_OTHER,

    CONFIGURATION,
    NON_CANONICAL_RESOURCE_PATH
}
