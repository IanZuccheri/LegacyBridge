package dev.legacybridge.fixture.receiver;
import dev.legacybridge.fixture.receiver.impl.Counter;
import java.util.function.IntUnaryOperator;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class ReceiverItem extends Item {
    private final Counter counter;
    public ReceiverItem() { super(new Item.Properties().stacksTo(16)); counter = new Counter(); }
    @Override public boolean isFoil(ItemStack stack) {
        IntUnaryOperator operation = counter.operation();
        return super.isFoil(stack) || (operation.applyAsInt(stack.getCount()) & 1) == 0;
    }
    public int calls() { return counter.calls(); }
}
