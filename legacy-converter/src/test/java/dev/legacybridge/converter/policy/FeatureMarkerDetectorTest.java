package dev.legacybridge.converter.policy;

import dev.legacybridge.converter.report.ArchiveSummary;
import dev.legacybridge.converter.report.BytecodeSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureMarkerDetectorTest {
    @Test
    void preservesExactSpecialEntryCountsBeyondEvidenceSamples() {
        ArchiveSummary archive = new ArchiveSummary(
                10,
                0,
                false,
                false,
                10,
                true,
                List.of("libs/a.jar", "libs/b.jar", "libs/c.jar", "libs/d.jar"),
                Map.of("NESTED_JAR", 10L)
        );

        var marker = new FeatureMarkerDetector().detect(archive, BytecodeSummary.empty()).get(0);

        assertEquals("NESTED_JAR", marker.id());
        assertEquals(10, marker.occurrenceCount());
        assertEquals(4, marker.evidence().size());
        assertTrue(marker.evidenceTruncated());
    }
}
