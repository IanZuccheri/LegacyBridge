package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.service.ConversionProfile;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.AdapterSelection;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Metadata only; actual production bodies and native APIs are checked separately. */
class IronHelmetDispenserCatalogTest {
    @Test void implementedDispenserHasSeparateIdentityAndExplicitSemanticLimits() {
        var operation=AdapterCatalog.require(AdapterCatalog.IRON_HELMET_DISPENSER);
        assertEquals("item.iron-helmet-dispenser.v1",operation.id());
        assertEquals(1,operation.revision());assertEquals("RUNTIME",operation.phase());
        assertEquals("dev.legacybridge.runtime.compat.item.LegacyIronHelmetDispenser",operation.implementationClass());
        assertEquals("dispense",operation.method());
        assertEquals("(Lnet/minecraft/core/BlockSource;Lnet/minecraft/world/item/ItemStack;)Z",operation.descriptor());
        assertEquals(List.of("fixtures/iron-helmet-1.16.5","fixtures/iron-helmet-dispenser-1.16.5"),operation.fixtures());
        assertTrue(operation.legacyApi().contains("ArmorItem.dispenseArmor"));
        assertTrue(operation.targetApi().contains("silent Mob armor write"));
        assertTrue(operation.transformation().contains("before split(1)"));
        assertTrue(operation.behaviorDifferences().contains("False means no eligible result only"));
        assertTrue(operation.behaviorDifferences().contains("without fabricated rollback"));
        assertTrue(operation.knownLimitations().contains("Unused by conversion or registration"));
        assertTrue(operation.knownLimitations().contains("no unknown-first skipping"));
        assertThrows(UnsupportedOperationException.class,()->operation.referenceTree().clear());
        assertThrows(UnsupportedOperationException.class,()->operation.fixtures().clear());
    }

    @Test void catalogDoesNotEnableAProfileOrRewritePreviousOperations() {
        assertEquals("create",AdapterCatalog.require(AdapterCatalog.IRON_HELMET_EMPTY_SLOT).method());
        assertEquals("equip",AdapterCatalog.require(AdapterCatalog.IRON_HELMET_HEAD_EQUIP_EFFECTS).method());
        for(int flags=0;flags<32;flags++)assertFalse(AdapterSelection.required((flags&1)!=0,(flags&2)!=0,
                (flags&4)!=0,(flags&8)!=0,(flags&16)!=0).contains(AdapterCatalog.IRON_HELMET_DISPENSER));
        assertEquals(List.of("simple-content-v1","logic-preserving-v1","lifecycle-preserving-v1",
                "food-item-v1","datapack-recipes-v1","network-snapshot-v1"),
                Arrays.stream(ConversionProfile.values()).map(ConversionProfile::id).toList());
        assertThrows(IllegalArgumentException.class,()->ConversionProfile.parse(AdapterCatalog.IRON_HELMET_DISPENSER));
        assertEquals("0.1.1-SNAPSHOT",AdapterCatalog.RUNTIME_VERSION);assertEquals(1,AdapterCatalog.SCHEMA_VERSION);
        assertNull(AdapterCatalog.class.getClassLoader().getResource("dev/legacybridge/runtime/compat/item/LegacyIronHelmetDispenser.class"));
    }
}
