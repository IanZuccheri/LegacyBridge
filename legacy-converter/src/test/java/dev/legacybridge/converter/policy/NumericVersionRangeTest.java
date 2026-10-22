package dev.legacybridge.converter.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericVersionRangeTest {
    @Test
    void detectsIntersectionWithAForgeMajorInsteadOfOnePinnedPatch() {
        assertTrue(NumericVersionRange.intersectsMajor("[36.0,36.1)", 36));
        assertTrue(NumericVersionRange.intersectsMajor("(35,36]", 36));
        assertTrue(NumericVersionRange.intersectsMajor("36.2.42", 36));
        assertFalse(NumericVersionRange.intersectsMajor("[35,36)", 36));
        assertFalse(NumericVersionRange.intersectsMajor("[37,)", 36));
        assertFalse(NumericVersionRange.intersectsMajor("${forge_range}", 36));
    }

    @Test
    void evaluatesTheNumericSubsetWithoutSubstringMatches() {
        assertTrue(NumericVersionRange.contains("[1.16,1.17)", "1.16.5"));
        assertTrue(NumericVersionRange.contains("[1.16.5]", "1.16.5"));
        assertTrue(NumericVersionRange.contains("[36,)", "36.2.42"));
        assertFalse(NumericVersionRange.contains("[136,)", "36.2.42"));
        assertFalse(NumericVersionRange.contains("[1.16.50,)", "1.16.5"));
        assertFalse(NumericVersionRange.contains("[1.15,1.16.5)", "1.16.5"));
    }

    @Test
    void unsupportedOrMalformedRangesNeverMatch() {
        assertFalse(NumericVersionRange.contains("[1.16,1.17),[2,)", "1.16.5"));
        assertFalse(NumericVersionRange.contains("${file.jarVersion}", "1.16.5"));
        assertFalse(NumericVersionRange.contains("[1.16.5-beta,)", "1.16.5"));
        assertFalse(NumericVersionRange.contains("(,)", "1.16.5"));
    }

    @Test
    void validatesOnlyTheDocumentedNumericSubset() {
        assertTrue(NumericVersionRange.isSupported("[36,)"));
        assertTrue(NumericVersionRange.isSupported("[1.16.5,1.17)"));
        assertTrue(NumericVersionRange.isSupported("[36.2.42]"));
        assertFalse(NumericVersionRange.isSupported("${forge_loader_range}"));
        assertFalse(NumericVersionRange.isSupported("[36-beta,)"));
        assertFalse(NumericVersionRange.isSupported("[47,36]"));
        assertFalse(NumericVersionRange.isSupported("(36,36]"));
    }
}
