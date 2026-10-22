package dev.legacybridge.converter.report;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompatibilityReportTest {
    @Test
    void findingsAreDefensivelyCopied() {
        List<Finding> findings = new ArrayList<>(List.of(new Finding(
                "LB-T001", Severity.INFO, "test", "first", List.of()
        )));
        CompatibilityReport report = new CompatibilityReport(
                CompatibilityReport.SCHEMA_VERSION,
                new ToolInfo("LegacyBridge", "test", "test-policy"),
                new InputFingerprint("fixture.jar", 1, "a".repeat(64)),
                new ArchiveSummary(0, 0, false, false, 0, false, List.of()),
                MetadataSummary.missing(),
                BytecodeSummary.empty(),
                List.of(),
                findings,
                new Assessment(
                        AnalysisStatus.COMPLETE,
                        SourceProfile.AMBIGUOUS,
                        ConversionStatus.NOT_IMPLEMENTED,
                        ExecutionStatus.NOT_TESTED
                )
        );

        findings.add(new Finding("LB-T002", Severity.INFO, "test", "second", List.of()));

        assertEquals(1, report.findings().size());
        assertEquals("first", report.findings().get(0).message());
        assertThrows(UnsupportedOperationException.class, () -> report.findings().add(
                new Finding("LB-T003", Severity.INFO, "test", "third", List.of())
        ));
    }
}
