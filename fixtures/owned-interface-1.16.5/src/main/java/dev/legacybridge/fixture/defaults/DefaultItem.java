package dev.legacybridge.fixture.defaults;
import dev.legacybridge.fixture.defaults.api.Scoring;
import dev.legacybridge.fixture.defaults.impl.Counter;
import java.util.function.IntUnaryOperator;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class DefaultItem extends Item {
    private final Counter counter;
    public DefaultItem() { super(new Item.Properties().stacksTo(16)); counter = new Counter(); }
    @Override public boolean isFoil(ItemStack stack) {
        Scoring scoring = counter;
        IntUnaryOperator operation = count -> scoring.score(count);
        return super.isFoil(stack) || (operation.applyAsInt(stack.getCount()) & 1) == 0;
    }
    public int calls() { return counter.calls(); }
}
