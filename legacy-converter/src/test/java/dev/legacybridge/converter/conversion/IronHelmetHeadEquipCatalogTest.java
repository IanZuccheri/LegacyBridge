package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.service.ConversionProfile;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.AdapterSelection;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Neutral catalog checks; native receiver and effect bytecode has a separate oracle. */
class IronHelmetHeadEquipCatalogTest {
    @Test void restrictedHeadEffectHasItsOwnConcreteVersionedEntrypoint() {
        var adapter=AdapterCatalog.require(AdapterCatalog.IRON_HELMET_HEAD_EQUIP_EFFECTS);
        assertEquals("item.iron-helmet-head-equip-effects.v1",adapter.id());
        assertEquals(1,adapter.revision());assertEquals("RUNTIME",adapter.phase());
        assertEquals("dev.legacybridge.runtime.compat.item.LegacyIronHelmetHeadEquipEffects",adapter.implementationClass());
        assertEquals("equip",adapter.method());
        assertEquals("(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V",adapter.descriptor());
        assertEquals(List.of("fixtures/iron-helmet-1.16.5"),adapter.fixtures());
        assertTrue(adapter.legacyApi().contains("playEquipSound"));
        assertTrue(adapter.targetApi().contains("HEAD index"));
        assertTrue(adapter.transformation().contains("before writing the same received stack reference"));
        assertTrue(adapter.transformation().contains("Empty stacks write without sound"));
        assertTrue(adapter.behaviorDifferences().contains("No target verifyEquippedItem/onEquipItem/GameEvent"));
        assertTrue(adapter.behaviorDifferences().contains("sound-event contracts differ"));
        assertTrue(adapter.knownLimitations().contains("unknown receiver subclasses rejected before effects"));
        assertTrue(adapter.knownLimitations().contains("Copy ownership, thread"));
        assertTrue(adapter.knownLimitations().contains("Unused by conversion: no recipe, profile or Apply"));
        assertThrows(UnsupportedOperationException.class,()->adapter.referenceTree().clear());
        assertThrows(UnsupportedOperationException.class,()->adapter.documentationTree().clear());
    }

    @Test void headEffectDoesNotReplaceThePreviousFactoryOrPromoteAnyProfile() {
        var previous=AdapterCatalog.require(AdapterCatalog.IRON_HELMET_EMPTY_SLOT);
        assertEquals(1,previous.revision());assertEquals("create",previous.method());
        assertEquals("dev.legacybridge.runtime.compat.item.LegacyIronHelmetItem",previous.implementationClass());
        assertEquals("()Ldev/legacybridge/runtime/compat/item/LegacyIronHelmetItem;",previous.descriptor());
        for(int flags=0;flags<32;flags++) {
            var selected=AdapterSelection.required((flags&1)!=0,(flags&2)!=0,(flags&4)!=0,(flags&8)!=0,(flags&16)!=0);
            assertFalse(selected.contains(AdapterCatalog.IRON_HELMET_HEAD_EQUIP_EFFECTS));
        }
        assertEquals(List.of("simple-content-v1","logic-preserving-v1","lifecycle-preserving-v1",
            "food-item-v1","datapack-recipes-v1","network-snapshot-v1"),
            Arrays.stream(ConversionProfile.values()).map(ConversionProfile::id).toList());
        assertThrows(IllegalArgumentException.class,()->ConversionProfile.parse(AdapterCatalog.IRON_HELMET_HEAD_EQUIP_EFFECTS));
        assertEquals(1,AdapterCatalog.SCHEMA_VERSION);
        assertEquals("0.1.1-SNAPSHOT",AdapterCatalog.RUNTIME_VERSION);
        assertEquals(List.of("entity","capability","rendering"),AdapterCatalog.unsupportedAreas());
        assertNull(AdapterCatalog.class.getClassLoader().getResource(
            "dev/legacybridge/runtime/compat/item/LegacyIronHelmetHeadEquipEffects.class"));
        assertNull(AdapterCatalog.class.getClassLoader().getResource(
            "dev/legacybridge/runtime/compat/item/LegacyIronHelmetHeadEquipEffects$ClientReceivers.class"));
    }
}
