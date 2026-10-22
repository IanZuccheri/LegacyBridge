package dev.legacybridge.runtime;

import dev.legacybridge.runtime.compat.event.ModEventBusAdapter;
import dev.legacybridge.runtime.compat.registry.ItemPropertiesAdapter;
import dev.legacybridge.runtime.compat.registry.SoundEventAdapter;
import dev.legacybridge.runtime.compat.world.BlockPropertiesAdapter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Invalid direct adapter calls are rejected before any target object or event listener is created. */
class RuntimeAdapterInputTest {
    @Test
    void directItemAdapterCallsCannotBypassTheNarrowPlanContract() {
        assertThrows(IllegalArgumentException.class, () -> ItemPropertiesAdapter.create(new SimpleContentPlan.ItemProperties(65, 0, false, "COMMON", null)));
        assertThrows(IllegalArgumentException.class, () -> ItemPropertiesAdapter.create(new SimpleContentPlan.ItemProperties(1, 10, false, "COMMON", null)));
        assertThrows(IllegalArgumentException.class, () -> ItemPropertiesAdapter.create(new SimpleContentPlan.ItemProperties(32, 0, true, "COMMON", null)));
        assertThrows(IllegalArgumentException.class, () -> ItemPropertiesAdapter.create(new SimpleContentPlan.ItemProperties(32, 0, false, "RARE", null)));
        assertThrows(IllegalArgumentException.class, () -> ItemPropertiesAdapter.create(new SimpleContentPlan.ItemProperties(32, 0, false, "COMMON", "TOOLS")));
    }

    @Test
    void directBlockAdapterCallsCannotInventUnverifiedMaterialOrMiningBehavior() {
        assertThrows(IllegalArgumentException.class, () -> BlockPropertiesAdapter.create(new SimpleContentPlan.BlockProperties("WOOD", 2, 6, "STONE", false, false)));
        assertThrows(IllegalArgumentException.class, () -> BlockPropertiesAdapter.create(new SimpleContentPlan.BlockProperties("STONE", Float.NaN, 6, "STONE", false, false)));
        assertThrows(IllegalArgumentException.class, () -> BlockPropertiesAdapter.create(new SimpleContentPlan.BlockProperties("STONE", 2, -1, "STONE", false, false)));
        assertThrows(IllegalArgumentException.class, () -> BlockPropertiesAdapter.create(new SimpleContentPlan.BlockProperties("STONE", 2, 6, "STONE", true, false)));
        assertThrows(IllegalArgumentException.class, () -> BlockPropertiesAdapter.create(new SimpleContentPlan.BlockProperties("STONE", 2, 6, "STONE", false, true)));
    }

    @Test
    void invalidSoundAndContextIdentifiersFailBeforeTargetCalls() {
        assertThrows(IllegalArgumentException.class, () -> SoundEventAdapter.create("minecraft:block.note_block.pling"));
        assertThrows(IllegalArgumentException.class, () -> SoundEventAdapter.create("legacybridge_example:../bad"));
        assertThrows(IllegalArgumentException.class, () -> SoundEventAdapter.create("legacybridge_example:" + "x".repeat(257)));
        assertThrows(IllegalArgumentException.class, () -> ModEventBusAdapter.requireContext(null, "1.0.0"));
    }
}
