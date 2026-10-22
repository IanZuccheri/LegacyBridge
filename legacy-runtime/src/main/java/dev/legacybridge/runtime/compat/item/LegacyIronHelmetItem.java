package dev.legacybridge.runtime.compat.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Legacy API: native Forge 36.2.42 ArmorItem(ArmorMaterial.IRON, EquipmentSlotType.HEAD,
 * fresh Item.Properties) and its empty-slot-only use(World, PlayerEntity, Hand) body.
 * Target API: native Forge 47.4.23 ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.HELMET,
 * fresh Item.Properties), with the received-player/hand equipment operations below.
 * Transformation: this implemented, fixed runtime factory/override is a versioned operation;
 * no converter recipe calls it yet. It equips a copy only into the resolved empty slot,
 * clears the original held stack, and returns that same stack in sidedSuccess or fail.
 * Behavior differences: deliberately retains the legacy operation on both logical sides,
 * rather than target occupied-slot swaps, Binding Curse/stack-match guards, copyAndClear
 * or a direct ITEM_USED award. Native target slot resolution and setItemSlot hooks remain
 * native; this body does not claim their effects or the platform callers are equivalent.
 * Known limitations: final, stateless iron helmet only; no arbitrary material, slot,
 * properties or subclass. Inherited Equipable.swapWithEquipmentSlot can be called separately
 * and is not adapted here. Constructor/dispenser/equipment hooks, sound/rendering/trim,
 * enchantment/anvil/wear, thread authority, save/load and multiplayer need separate contracts
 * and native qualification. Presence in the runtime is not conversion or installation approval.
 */
public final class LegacyIronHelmetItem extends ArmorItem {
    public LegacyIronHelmetItem() {
        super(ArmorMaterials.IRON, ArmorItem.Type.HELMET, new Item.Properties());
    }

    /** Concrete public-static catalog entrypoint, unused by current conversion profiles. */
    public static LegacyIronHelmetItem create() {
        return new LegacyIronHelmetItem();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        EquipmentSlot slot = Mob.getEquipmentSlotForItem(held);
        ItemStack equipped = player.getItemBySlot(slot);
        if (equipped.isEmpty()) {
            player.setItemSlot(slot, held.copy());
            held.setCount(0);
            return InteractionResultHolder.sidedSuccess(held, world.isClientSide());
        } else {
            return InteractionResultHolder.fail(held);
        }
    }
}
