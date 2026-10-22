package dev.legacybridge.converter.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalReportWriterTest {
    @Test
    void outputIsDeterministicOrderedAndEscaped() {
        CompatibilityReport report = new CompatibilityReport(
                CompatibilityReport.SCHEMA_VERSION,
                new ToolInfo("LegacyBridge", "test", "policy-v1"),
                new InputFingerprint("quote\"fixture\u202e.jar", 7, "b".repeat(64)),
                new ArchiveSummary(0, 0, false, false, 0, false, List.of()),
                MetadataSummary.missing(),
                BytecodeSummary.empty(),
                List.of(),
                List.of(new Finding("LB-T001", Severity.INFO, "test", "line\nbreak", List.of())),
                new Assessment(
                        AnalysisStatus.COMPLETE,
                        SourceProfile.AMBIGUOUS,
                        ConversionStatus.NOT_IMPLEMENTED,
                        ExecutionStatus.NOT_TESTED
                )
        );
        CanonicalReportWriter writer = new CanonicalReportWriter();

        String first = writer.toJson(report);
        String second = writer.toJson(report);

        assertEquals(first, second);
        assertFalse(first.contains("\r"));
        assertTrue(first.endsWith("\n"));
        assertTrue(first.contains("quote\\\"fixture\\u202e.jar"));
        assertFalse(first.contains("\u202e"));
        assertTrue(first.contains("line\\nbreak"));
        assertTrue(first.indexOf("\"schemaVersion\"") < first.indexOf("\"assessment\""));
    }
}
