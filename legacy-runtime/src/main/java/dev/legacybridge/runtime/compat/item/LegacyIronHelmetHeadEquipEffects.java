package dev.legacybridge.runtime.compat.item;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Legacy API: Forge 36.2.42 PlayerEntity.setItemSlot(HEAD, stack), whose inherited
 * playEquipSound uses the fixed iron material before assigning inventory.armor[3].
 * Target API: Forge 47.4.23 virtual Player.playSound, ArmorMaterials.IRON.getEquipSound,
 * Player.getInventory, Inventory.armor and EquipmentSlot.HEAD.getIndex.
 * Transformation: this separate versioned operation enforces HEAD, exact pinned
 * native player receivers and an empty stack or one final LegacyIronHelmetItem.
 * It plays the material sound before storing the exact received stack reference;
 * an empty stack is stored as received without sound. No conversion calls it yet.
 * Behavior differences: deliberately does not invoke the target setItemSlot,
 * verifyEquippedItem or onEquipItem/GameEvent path. It adds no first-tick, silent,
 * spectator or logical-side sound gate. Virtual native sound dispatch is retained,
 * including the different legacy/target server and client Forge sound-event contracts.
 * Known limitations: unknown player subclasses, non-HEAD slots, nonempty foreign
 * items and counts other than one fail before sound or inventory access. Accepted
 * receivers are exact ServerPlayer, LocalPlayer and RemotePlayer only; client
 * identities live in the physically guarded helper, never the shared class.
 * This does not prove copied-stack ownership, caller thread, pristine transformed
 * platform methods or listener/cohort equivalence. It does not guard occupancy or
 * clear a hand; those are future caller obligations. Constructor/dispenser,
 * Equipable, rendering, persistence, synchronization and native/multiplayer
 * qualification remain separate. No recipe, public profile or Apply is enabled.
 */
public final class LegacyIronHelmetHeadEquipEffects {
    private LegacyIronHelmetHeadEquipEffects() { }

    public static void equip(Player player, EquipmentSlot slot, ItemStack stack) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(stack, "stack");
        if (slot != EquipmentSlot.HEAD) {
            throw new IllegalArgumentException("Iron helmet equipment effects require HEAD");
        }
        Class<?> receiver = player.getClass();
        if (receiver != ServerPlayer.class
                && (FMLEnvironment.dist != Dist.CLIENT || !ClientReceivers.isKnown(receiver))) {
            throw new UnsupportedOperationException("Unknown player receiver for iron helmet equipment effects");
        }
        if (!stack.isEmpty()) {
            if (!(stack.getItem() instanceof LegacyIronHelmetItem) || stack.getCount() != 1) {
                throw new IllegalArgumentException("Iron helmet equipment effects require one fixed runtime helmet or an empty stack");
            }
            player.playSound(ArmorMaterials.IRON.getEquipSound(), 1.0F, 1.0F);
        }
        player.getInventory().armor.set(EquipmentSlot.HEAD.getIndex(), stack);
    }

    /** Resolved only behind the physical-client guard; no native class-name guessing. */
    private static final class ClientReceivers {
        private ClientReceivers() { }

        private static boolean isKnown(Class<?> receiver) {
            return receiver == net.minecraft.client.player.LocalPlayer.class
                || receiver == net.minecraft.client.player.RemotePlayer.class;
        }
    }
}
