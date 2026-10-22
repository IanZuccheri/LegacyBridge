package dev.legacybridge.fixture.composed;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Different owned helper/interface layout, same reviewed native override/constructor contract. */
public final class ComposedItem extends Item {
    private final CountRule rule;
    public ComposedItem() { super(new Item.Properties().stacksTo(16)); rule = new OddRule(); }
    @Override public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || (rule.score(stack.getCount()) & 1) == 0;
    }
    public int calls() { return rule.calls(); }
}
