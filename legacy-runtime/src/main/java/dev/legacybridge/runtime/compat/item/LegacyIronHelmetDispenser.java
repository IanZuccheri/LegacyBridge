package dev.legacybridge.runtime.compat.item;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

/**
 * Legacy API: Forge 36.2.42 ArmorItem.dispenseArmor(IBlockSource, ItemStack),
 * restricted to the fixed iron HEAD item and native player or zombie receivers.
 * Target API: Forge 47.4.23 BlockSource, native armor eligibility predicate/query,
 * ItemStack.split, the separate player HEAD operation and Mob armor/drop/persistence APIs.
 * Transformation: query the facing block, keep the first eligible receiver, resolve
 * HEAD, reject unsupported receivers/list layouts before split, then equip split(1).
 * Player equipment retains sound-before-write; exact Zombie equipment is silent,
 * followed by native drop chance 2 and persistence, in that order.
 * Behavior differences: no target setter verify/onEquipItem/GameEvent effects.
 * Unsupported input, receiver or layout throws rather than returning the no-target
 * false result, skipping the first receiver or triggering a ground-drop fallback.
 * Native predicates and the order returned by the target query are retained,
 * not sorted or reconstructed. The two versions' spatial query engines differ.
 * Known limitations: only nonempty final LegacyIronHelmetItem, exact ServerPlayer
 * and exact Zombie. No registration or conversion recipe calls this operation yet.
 * Other mobs, ArmorStand, fake/custom players, direct fallback dispense behavior,
 * arbitrary source/world/predicate hooks, thread, source/target sound-event listener
 * equivalence, copy/capability serialization, rendering and native/multiplayer QA
 * remain unqualified. Native exceptions after split are propagated without a fake
 * rollback: source split precedes equipment and post-equipment effects too.
 */
public final class LegacyIronHelmetDispenser {
    private LegacyIronHelmetDispenser() { }

    public static boolean dispense(BlockSource source, ItemStack stack) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty() || !(stack.getItem() instanceof LegacyIronHelmetItem)) {
            throw new IllegalArgumentException("Iron helmet dispenser requires a nonempty fixed runtime helmet stack");
        }
        BlockPos target = source.getPos().relative(source.getBlockState().getValue(DispenserBlock.FACING));
        List<LivingEntity> eligible = source.getLevel().getEntitiesOfClass(LivingEntity.class, new AABB(target),
                EntitySelector.NO_SPECTATORS.and(new EntitySelector.MobCanWearArmorEntitySelector(stack)));
        if (eligible.isEmpty()) {
            return false;
        }
        LivingEntity receiver = eligible.get(0);
        EquipmentSlot slot = Mob.getEquipmentSlotForItem(stack);
        if (slot != EquipmentSlot.HEAD) {
            throw new UnsupportedOperationException("Iron helmet dispenser requires the resolved HEAD slot");
        }
        if (receiver.getClass() == ServerPlayer.class) {
            ItemStack equipped = stack.split(1);
            LegacyIronHelmetHeadEquipEffects.equip((ServerPlayer) receiver, slot, equipped);
        } else if (receiver.getClass() == Zombie.class) {
            Zombie zombie = (Zombie) receiver;
            Iterable<ItemStack> slots = zombie.getArmorSlots();
            if (slots == null || slots.getClass() != NonNullList.class || ((NonNullList<?>) slots).size() != 4) {
                throw new UnsupportedOperationException("Unknown zombie armor-list layout");
            }
            // The pinned native Mob getter returns its own NonNullList<ItemStack>.
            @SuppressWarnings("unchecked")
            NonNullList<ItemStack> armor = (NonNullList<ItemStack>) slots;
            ItemStack equipped = stack.split(1);
            armor.set(slot.getIndex(), equipped);
            zombie.setDropChance(slot, 2.0F);
            zombie.setPersistenceRequired();
        } else {
            throw new UnsupportedOperationException("Unknown first receiver for iron helmet dispenser");
        }
        return true;
    }
}
