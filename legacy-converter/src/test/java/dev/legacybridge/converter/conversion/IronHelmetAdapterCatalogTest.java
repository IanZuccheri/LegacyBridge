package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.service.ConversionProfile;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.AdapterSelection;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Catalog metadata only. This test never resolves the runtime item or a game class. */
class IronHelmetAdapterCatalogTest {
    @Test void realFixedFactoryHasOneVersionedRuntimeDescriptorWithoutClaimingConversion() {
        var adapter=AdapterCatalog.require(AdapterCatalog.IRON_HELMET_EMPTY_SLOT);
        assertEquals("item.iron-helmet-empty-slot.v1",adapter.id());
        assertEquals(1,adapter.revision());assertEquals("RUNTIME",adapter.phase());
        assertEquals("dev.legacybridge.runtime.compat.item.LegacyIronHelmetItem",adapter.implementationClass());
        assertEquals("create",adapter.method());
        assertEquals("()Ldev/legacybridge/runtime/compat/item/LegacyIronHelmetItem;",adapter.descriptor());
        assertEquals(List.of("fixtures/iron-helmet-1.16.5"),adapter.fixtures());
        assertTrue(adapter.legacyApi().contains("IRON/HEAD"));assertTrue(adapter.targetApi().contains("IRON/HELMET"));
        assertTrue(adapter.transformation().contains("equips a copy"));
        assertTrue(adapter.behaviorDifferences().contains("both logical sides"));
        assertTrue(adapter.behaviorDifferences().contains("no target occupied-slot swap"));
        assertTrue(adapter.knownLimitations().contains("Unused by conversion"));
        assertTrue(adapter.knownLimitations().contains("Inherited Equipable paths"));
        assertTrue(adapter.knownLimitations().contains("native/multiplayer qualification"));
        assertThrows(UnsupportedOperationException.class,()->adapter.fixtures().clear());
        assertThrows(UnsupportedOperationException.class,()->adapter.documentationTree().clear());
    }

    @Test void descriptorDoesNotEnlargeSimpleSelectionOrPublicProfileSet() {
        for(int flags=0;flags<32;flags++) {
            var selected=AdapterSelection.required((flags&1)!=0,(flags&2)!=0,(flags&4)!=0,(flags&8)!=0,(flags&16)!=0);
            assertFalse(selected.contains(AdapterCatalog.IRON_HELMET_EMPTY_SLOT));
        }
        assertEquals(List.of("simple-content-v1","logic-preserving-v1","lifecycle-preserving-v1",
            "food-item-v1","datapack-recipes-v1","network-snapshot-v1"),
            Arrays.stream(ConversionProfile.values()).map(ConversionProfile::id).toList());
        assertThrows(IllegalArgumentException.class,()->ConversionProfile.parse(AdapterCatalog.IRON_HELMET_EMPTY_SLOT));
        assertEquals(1,AdapterCatalog.SCHEMA_VERSION);
        assertEquals("0.1.1-SNAPSHOT",AdapterCatalog.RUNTIME_VERSION);
        assertEquals(List.of("entity","capability","rendering"),AdapterCatalog.unsupportedAreas());
        assertNull(AdapterCatalog.class.getClassLoader().getResource(
            "dev/legacybridge/runtime/compat/item/LegacyIronHelmetItem.class"),"The neutral contract cannot bundle the native implementation");
    }
}
