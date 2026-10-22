package dev.legacybridge.converter.analysis;

import dev.legacybridge.converter.report.Complexity;
import dev.legacybridge.converter.report.UnsupportedFeature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskScoreCalculatorTest {
    private final RiskScoreCalculator calculator = new RiskScoreCalculator();

    @Test
    void blockerLikeArchiveConditionsCannotBeReportedAsSimple() {
        for (LegacyFeatureId id : List.of(
                LegacyFeatureId.ACCESS_TRANSFORMER,
                LegacyFeatureId.NESTED_JAR,
                LegacyFeatureId.SIGNED_JAR,
                LegacyFeatureId.MULTI_RELEASE,
                LegacyFeatureId.EXTERNAL_CLASS_PATH,
                LegacyFeatureId.METADATA_UNCERTAINTY
        )) {
            var result = calculator.calculate(List.of(feature(id)), List.of(), 0);
            assertTrue(result.riskScore() >= 15, id.name());
            assertTrue(result.complexity().ordinal() >= Complexity.MODERATE.ordinal(), id.name());
        }
    }

    @Test
    void unsupportedLoaderAndLegacyBootstrapHaveExplicitFloors() {
        assertEquals(Complexity.COMPLEX, calculator.calculate(
                List.of(feature(LegacyFeatureId.UNSUPPORTED_LOADER)), List.of(), 0).complexity());
        assertEquals(Complexity.EXTREME, calculator.calculate(
                List.of(feature(LegacyFeatureId.LEGACY_BOOTSTRAP)), List.of(), 0).complexity());
    }

    private static UnsupportedFeature feature(LegacyFeatureId id) {
        return new UnsupportedFeature(id.name(), 1, List.of(id.name()), false);
    }
}
