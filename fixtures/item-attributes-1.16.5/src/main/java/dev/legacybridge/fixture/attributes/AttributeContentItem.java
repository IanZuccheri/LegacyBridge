package dev.legacybridge.fixture.attributes;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;

/**
 * Finite native attribute endpoint, not a sword, tool or combat implementation.
 * The inherited UUID objects deliberately preserve native tooltip identity checks.
 * Only immutable constants are shared; no player/entity state is retained here.
 */
public final class AttributeContentItem extends Item {
    private static final Multimap<Attribute, AttributeModifier> MODIFIERS =
        ImmutableMultimap.<Attribute, AttributeModifier>builder()
            .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,
                "LegacyBridge attack damage", 2.0D, AttributeModifier.Operation.ADDITION))
            .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID,
                "LegacyBridge attack speed", -1.0D, AttributeModifier.Operation.ADDITION))
            .build();

    public AttributeContentItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlotType slot) {
        return slot == EquipmentSlotType.MAINHAND ? MODIFIERS : ImmutableMultimap.of();
    }
}
