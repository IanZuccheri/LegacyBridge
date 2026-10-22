package dev.legacybridge.fixture.hierarchy.leaf;
import dev.legacybridge.fixture.hierarchy.base.BaseTokenItem;
import net.minecraft.item.ItemStack;

public final class HierarchyItem extends BaseTokenItem {
    private int calls;
    public HierarchyItem() { super(); }
    @Override protected int score(int count) { calls++; return CountRule.score(count, seed); }
    @Override public boolean isFoil(ItemStack stack) { return super.isFoil(stack); }
    public int inheritedProbe(int count) { return projected(count); }
    public int calls() { return calls; }
}
