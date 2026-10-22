package dev.legacybridge.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeDescriptorTest {
    @Test
    void runtimeDeclaresOnlyItsImplementedAdapterAndNoBytecodeTransformer() {
        RuntimeDescriptor descriptor = RuntimeDescriptor.current();

        assertEquals("1.20.1", descriptor.minecraftVersion());
        assertEquals(47, descriptor.forgeMajor());
        assertSame(RuntimeCapabilityCatalog.current(), descriptor.capabilityCatalog());
        assertFalse(descriptor.transformationsEnabled());
        assertEquals(1, descriptor.enabledShims().size());
        assertEquals("simple-content-v1", descriptor.enabledShims().get(0).id());
        assertEquals(1, descriptor.capabilityCatalog().enabledCapabilities().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> descriptor.capabilityCatalog().enabledCapabilities().clear()
        );
    }
}
