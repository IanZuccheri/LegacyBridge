package dev.legacybridge.runtime.compat.registry;

import dev.legacybridge.runtime.SimpleContentPlan;
import net.minecraft.world.item.Item;
import java.util.Objects;

/**
 * Legacy API: {@code net.minecraft.item.Item.Properties#stacksTo(int)} with default durability/fire/rarity.
 * Target API: {@code net.minecraft.world.item.Item.Properties#stacksTo(int)}.
 * Transformation: builds target properties from a statically proven declarative value.
 * Behavior differences: creative grouping is handled separately by the target tab event.
 * Known limitations: stacks 1..64, durability zero, non-fire-resistant, COMMON rarity, and two vanilla tabs only.
 */
public final class ItemPropertiesAdapter {
    private ItemPropertiesAdapter() { }

    public static Item.Properties create(SimpleContentPlan.ItemProperties source) {
        Objects.requireNonNull(source, "source");
        if (source.stackSize() < 1 || source.stackSize() > 64 || source.durability() != 0
                || source.fireResistant() || !"COMMON".equals(source.rarity())
                || (source.creativeTab() != null && !source.creativeTab().equals("MISC") && !source.creativeTab().equals("BUILDING_BLOCKS"))) {
            throw new IllegalArgumentException("Item properties are outside the verified adapter subset");
        }
        return new Item.Properties().stacksTo(source.stackSize());
    }
}
