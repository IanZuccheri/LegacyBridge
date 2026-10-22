package dev.legacybridge.converter.conversion.extract;

import java.util.List;

/** Declarative data, not executable legacy code. All names retain their source namespace. */
public record ContentPlan(String modId, String modVersion, List<ItemDefinition> items,
                          List<BlockDefinition> blocks, List<SoundDefinition> sounds) {
    public ContentPlan {
        items = List.copyOf(items);
        blocks = List.copyOf(blocks);
        sounds = List.copyOf(sounds);
    }

    /** block is null for a plain Item, or the complete resource ID for a BlockItem. */
    public record ItemDefinition(String name, String block, ItemProperties properties) { }
    public record ItemProperties(int stackSize, int durability, boolean fireResistant,
                                 String rarity, String creativeTab) { }
    public record BlockDefinition(String name, BlockProperties properties) { }
    public record BlockProperties(String material, float hardness, float resistance, String sound,
                                  boolean requiresCorrectTool, boolean noOcclusion) { }
    public record SoundDefinition(String name, String resourceLocation) { }
}
