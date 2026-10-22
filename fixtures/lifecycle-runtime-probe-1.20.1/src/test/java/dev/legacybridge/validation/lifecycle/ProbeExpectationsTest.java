package dev.legacybridge.validation.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Expectations are not a fake config implementation and never load or mutate a native spec. */
final class ProbeExpectationsTest {
    @Test
    void acceptsOnlyTheExplicitBoundedNativeConfigExpectations() {
        ProbeExpectations defaults = ProbeExpectations.parse("true", "3");
        assertTrue(defaults.enabled);
        assertEquals(3, defaults.factor);
        ProbeExpectations alternate = ProbeExpectations.parse("false", "5");
        assertFalse(alternate.enabled);
        assertEquals(5, alternate.factor);
        assertEquals(1, ProbeExpectations.parse("true", "1").factor);
        assertEquals(8, ProbeExpectations.parse("true", "8").factor);
    }

    @Test
    void refusesMalformedOrOutOfScopeExpectations() {
        for (String factor : new String[]{null, "0", "9", "03", "-1", "3 ", "text"}) {
            assertThrows(IllegalArgumentException.class, () -> ProbeExpectations.parse("true", factor));
        }
        for (String enabled : new String[]{null, "TRUE", "1", "false ", ""}) {
            assertThrows(IllegalArgumentException.class, () -> ProbeExpectations.parse(enabled, "3"));
        }
    }
}
