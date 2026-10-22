package dev.legacybridge.converter.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComplexityTest {
    @Test
    void classifiesEveryDocumentedBoundary() {
        assertEquals(Complexity.SIMPLE, Complexity.fromRiskScore(0));
        assertEquals(Complexity.SIMPLE, Complexity.fromRiskScore(14));
        assertEquals(Complexity.MODERATE, Complexity.fromRiskScore(15));
        assertEquals(Complexity.MODERATE, Complexity.fromRiskScore(34));
        assertEquals(Complexity.COMPLEX, Complexity.fromRiskScore(35));
        assertEquals(Complexity.COMPLEX, Complexity.fromRiskScore(59));
        assertEquals(Complexity.EXTREME, Complexity.fromRiskScore(60));
        assertEquals(Complexity.EXTREME, Complexity.fromRiskScore(100));
    }
}
