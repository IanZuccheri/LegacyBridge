package dev.legacybridge.runtime.compat.registry;

import dev.legacybridge.runtime.SimpleContentPlan;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import java.util.Objects;

/**
 * Legacy API: {@code net.minecraft.item.BlockItem(Block, Item.Properties)}.
 * Target API: {@code net.minecraft.world.item.BlockItem(Block, Item.Properties)}.
 * Transformation: constructs a native target BlockItem for an already registered plain local block.
 * Behavior differences: the old block RegistryObject is replaced by a target DeferredRegister reference.
 * Known limitations: exact plain Block only; custom placement, subclasses, NBT and durability are unsupported.
 */
public final class BlockItemAdapter {
    private BlockItemAdapter() { }

    public static BlockItem create(Block block, SimpleContentPlan.ItemProperties properties) {
        Objects.requireNonNull(block, "block");
        if (block.getClass() != Block.class) throw new IllegalArgumentException("Only a plain target Block can back a converted BlockItem");
        return new BlockItem(block, ItemPropertiesAdapter.create(properties));
    }
}
