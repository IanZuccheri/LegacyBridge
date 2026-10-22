package dev.legacybridge.converter.analysis;

import dev.legacybridge.converter.report.Complexity;

/** Stable v2 feature IDs and their migration-risk weights. */
public enum LegacyFeatureId {
    LEGACY_REGISTRIES(6),
    LEGACY_EVENTS(4),
    LEGACY_NETWORKING(10),
    LEGACY_CAPABILITIES(8),
    CUSTOM_ENTITIES(10),
    TILE_ENTITY(12),
    CLIENT_RENDERING(12),
    GUI(8),
    WORLD_GENERATION(15),
    DIMENSIONS(18),
    RECIPES(5),
    LOOT(5),
    REFLECTION(12),
    MIXINS(25, Complexity.COMPLEX),
    ACCESS_TRANSFORMER(10, Complexity.MODERATE),
    COREMOD(35, Complexity.EXTREME),
    TRANSFORMATION_SERVICE(35, Complexity.EXTREME),
    NATIVE_CODE(35, Complexity.EXTREME),
    NESTED_JAR(8, Complexity.MODERATE),
    UNREADABLE_BYTECODE(15, Complexity.COMPLEX),
    UNSUPPORTED_CLASS_VERSION(25, Complexity.COMPLEX),
    PREVIEW_BYTECODE(25, Complexity.COMPLEX),
    SIGNED_JAR(3, Complexity.MODERATE),
    MULTI_RELEASE(8, Complexity.MODERATE),
    SERVICE_PROVIDER(4),
    EXTERNAL_CLASS_PATH(15, Complexity.MODERATE),
    LEGACY_BOOTSTRAP(35, Complexity.EXTREME),
    METADATA_UNCERTAINTY(15, Complexity.MODERATE),
    UNSUPPORTED_LOADER(35, Complexity.COMPLEX);

    private final int weight;
    private final Complexity minimumComplexity;

    LegacyFeatureId(int weight) {
        this(weight, Complexity.SIMPLE);
    }

    LegacyFeatureId(int weight, Complexity minimumComplexity) {
        this.weight = weight;
        this.minimumComplexity = minimumComplexity;
    }

    public int weight() {
        return weight;
    }

    public Complexity minimumComplexity() {
        return minimumComplexity;
    }
}
