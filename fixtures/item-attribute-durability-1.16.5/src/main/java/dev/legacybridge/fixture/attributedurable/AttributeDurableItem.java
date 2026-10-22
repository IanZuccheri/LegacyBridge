package dev.legacybridge.fixture.attributedurable;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Finite native attribute endpoint, not a sword, tool or combat implementation.
 * The inherited UUID objects deliberately preserve native tooltip identity checks.
 * Only immutable constants are shared; no player/entity state is retained here.
 * Durability/repair are declarations only: base Item does not spend durability on attacks/mining.
 */
public final class AttributeDurableItem extends Item {
    private static final Multimap<Attribute, AttributeModifier> MODIFIERS =
        ImmutableMultimap.<Attribute, AttributeModifier>builder()
            .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,
                "LegacyBridge attack damage", 2.0D, AttributeModifier.Operation.ADDITION))
            .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID,
                "LegacyBridge attack speed", -1.0D, AttributeModifier.Operation.ADDITION))
            .build();

    public AttributeDurableItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlotType slot) {
        return slot == EquipmentSlotType.MAINHAND ? MODIFIERS : ImmutableMultimap.of();
    }

    @Override
    public boolean isValidRepairItem(ItemStack damaged, ItemStack ingredient) {
        return ingredient.getItem() == Items.IRON_INGOT;
    }
}
