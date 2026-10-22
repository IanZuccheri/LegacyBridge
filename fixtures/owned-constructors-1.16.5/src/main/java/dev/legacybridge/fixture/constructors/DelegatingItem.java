package dev.legacybridge.fixture.constructors;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Fixture expectation: stack limit 16, constructor trace 123, odd-count foil without vanilla foil.
 * These are source-level expectations, NOT a claim of native execution validation. */
public final class DelegatingItem extends Item {
    private final int seed;
    private final CountRule rule;
    private int trace;
    public DelegatingItem() { this(16); trace = trace * 10 + 3; }
    private DelegatingItem(int limit) { this(limit, 3); trace = trace * 10 + 2; }
    private DelegatingItem(int limit, int seed) {
        super(new Item.Properties().stacksTo(limit));
        this.seed = seed;
        this.rule = new CountRule();
        this.trace = 1;
    }
    @Override public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || (rule.score(stack.getCount(), seed) & 1) == 0;
    }
    public int constructorTrace() { return trace; }
    public int calls() { return rule.calls(); }
}
