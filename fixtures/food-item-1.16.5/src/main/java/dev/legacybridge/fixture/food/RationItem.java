package dev.legacybridge.fixture.food;

import net.minecraft.item.Food;
import net.minecraft.item.Item;

/** A native food definition only. No effects, container item, timing override, or callback. */
public final class RationItem extends Item {
    public RationItem() {
        super(new Item.Properties().stacksTo(16)
                .food(new Food.Builder().nutrition(3).saturationMod(0.5F).build()));
    }
}
