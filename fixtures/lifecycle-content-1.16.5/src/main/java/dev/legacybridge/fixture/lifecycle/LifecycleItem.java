package dev.legacybridge.fixture.lifecycle;

import net.minecraft.item.Item;

/** Ordinary native item with no custom override; keep registry and item identity unchanged. */
public final class LifecycleItem extends Item {
    public LifecycleItem() {
        super(new Item.Properties().stacksTo(16));
    }
}
