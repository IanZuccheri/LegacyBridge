package dev.legacybridge.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeCapabilityCatalogTest {
    @Test
    void catalogEnablesOnlyTheImplementedSimpleContentAdapter() {
        RuntimeCapabilityCatalog catalog = RuntimeCapabilityCatalog.current();

        assertEquals(1, catalog.enabledCapabilities().size());
        assertEquals("simple-content-v1", catalog.enabledCapabilities(RuntimeCapabilityType.LEGACY_API_SHIM).get(0).id());
        assertTrue(catalog.enabledCapabilities(RuntimeCapabilityType.BYTECODE_TRANSFORMATION).isEmpty());
        assertTrue(catalog.hasEnabledCapability(RuntimeCapabilityType.LEGACY_API_SHIM));
        assertFalse(catalog.hasEnabledCapability(RuntimeCapabilityType.BYTECODE_TRANSFORMATION));
    }
}
