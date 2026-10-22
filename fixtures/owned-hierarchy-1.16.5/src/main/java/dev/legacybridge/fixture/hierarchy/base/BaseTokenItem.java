package dev.legacybridge.fixture.hierarchy.base;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public abstract class BaseTokenItem extends Item {
    protected final int seed;
    protected BaseTokenItem() { super(new Item.Properties().stacksTo(16)); seed = 3; }
    protected abstract int score(int count);
    public int projected(int count) { return score(count); }
    @Override public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || (score(stack.getCount()) & 1) == 0;
    }
}
