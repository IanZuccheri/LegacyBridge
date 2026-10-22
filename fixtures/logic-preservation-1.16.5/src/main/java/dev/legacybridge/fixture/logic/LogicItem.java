package dev.legacybridge.fixture.logic;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Item virtual dispatch must reach the preserved subclass, interface and helper graph. */
public final class LogicItem extends Item {
    private final ParityRule concreteRule;
    private final ScoreRule rule;

    public LogicItem() {
        super(new Item.Properties().stacksTo(16));
        concreteRule = new ParityRule(2);
        rule = concreteRule;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || (rule.score(stack.getCount()) & 1) == 0;
    }

    /** Separate runtime probes call this with the documented ordered input vector. */
    public int score(int input) {
        return rule.score(input);
    }

    public int callCount() {
        return concreteRule.callCount();
    }

    public static int ruleInstances() {
        return BaseRule.instanceCount();
    }

    public static int helperSeed() {
        return LogicMath.seed();
    }

    public static int helperInitializationCount() {
        return LogicMath.initializationCount();
    }
}
