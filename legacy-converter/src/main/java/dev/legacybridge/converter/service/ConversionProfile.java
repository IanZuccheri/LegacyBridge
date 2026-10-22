package dev.legacybridge.converter.service;

/** Explicit opt-in; adding a profile never changes the default player/CLI conversion grammar. */
public enum ConversionProfile {
    SIMPLE_CONTENT_V1("simple-content-v1"),
    LOGIC_PRESERVING_V1("logic-preserving-v1"),
    LIFECYCLE_PRESERVING_V1("lifecycle-preserving-v1"),
    FOOD_ITEM_V1("food-item-v1"),
    DATAPACK_RECIPES_V1("datapack-recipes-v1"),
    NETWORK_SNAPSHOT_V1("network-snapshot-v1");

    private final String id;
    ConversionProfile(String id) { this.id = id; }
    public String id() { return id; }
    public static ConversionProfile parse(String id) {
        for (var profile : values()) if (profile.id.equals(id)) return profile;
        throw new IllegalArgumentException("Unknown conversion profile: " + id);
    }
}
