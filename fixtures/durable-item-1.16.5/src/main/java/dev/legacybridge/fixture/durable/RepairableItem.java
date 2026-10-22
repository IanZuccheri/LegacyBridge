package dev.legacybridge.fixture.durable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Original native Item, not a tool: no damage mutation, state, callbacks or inventory consumption. */
public final class RepairableItem extends Item {
    public RepairableItem() {
        super(new Item.Properties().durability(128));
    }

    @Override
    public boolean isValidRepairItem(ItemStack damaged, ItemStack ingredient) {
        return ingredient.getItem() == Items.IRON_INGOT;
    }
}
