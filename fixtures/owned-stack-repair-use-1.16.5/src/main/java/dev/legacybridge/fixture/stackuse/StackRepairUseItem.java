package dev.legacybridge.fixture.stackuse;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Authored input for the next finite use contract, not a qualified gameplay feature.
 * The client consumes the attempted action without writing stack damage. The server invokes
 * the existing bounded helper, then reports CONSUME or PASS using the same held stack.
 * Native event/cooldown/spectator and creative restoration behavior is not replaced here.
 */
public final class StackRepairUseItem extends Item {
    public StackRepairUseItem() {
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

    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide) {
            return ActionResult.success(stack);
        }
        return repairOne(stack) ? ActionResult.consume(stack) : ActionResult.pass(stack);
    }
}
