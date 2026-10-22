package dev.legacybridge.fixture.stackdamage;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Bounded owned helper for a future isolated QA harness, not an automatic gameplay callback.
 * No wear, break notification, inventory/player/world access or client synchronization is added.
 * The native Forge damage hooks remain in the call path; malformed NBT is not a supported input.
 */
public final class StackDamageItem extends Item {
    public StackDamageItem() {
        super(new Item.Properties().durability(128));
    }

    public boolean repairOne(ItemStack stack) {
        if (stack == null || stack.getItem() != this || !stack.isDamaged()) {
            return false;
        }
        int damage = stack.getDamageValue();
        if (damage <= 0 || damage > stack.getMaxDamage()) {
            return false;
        }
        stack.setDamageValue(damage - 1);
        return true;
    }
}
