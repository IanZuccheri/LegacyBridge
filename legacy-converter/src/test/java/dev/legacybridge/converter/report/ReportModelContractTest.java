package dev.legacybridge.converter.report;

import dev.legacybridge.converter.bytecode.ArchiveReferenceInventory;
import dev.legacybridge.converter.bytecode.BytecodeReference;
import dev.legacybridge.converter.bytecode.BytecodeReferenceKind;
import dev.legacybridge.converter.content.ContentInventory;
import dev.legacybridge.converter.content.ContentNamespaceSummary;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportModelContractTest {
    @Test
    void markerTracksOccurrencesAndBoundsEvidenceWithoutBreakingTwoArgumentCallers() {
        FeatureMarker emptyEvidence = new FeatureMarker("SIGNED_JAR", List.of());
        assertEquals(1, emptyEvidence.occurrenceCount());
        assertFalse(emptyEvidence.evidenceTruncated());

        List<String> observations = IntStream.range(0, 20)
                .mapToObj(value -> "evidence-" + value)
                .toList();
        FeatureMarker bounded = new FeatureMarker("SERVICE_PROVIDER", observations);
        assertEquals(20, bounded.occurrenceCount());
        assertEquals(FeatureMarker.EVIDENCE_LIMIT, bounded.evidence().size());
        assertTrue(bounded.evidenceTruncated());

        assertThrows(IllegalArgumentException.class,
                () -> new FeatureMarker("SERVICE_PROVIDER", 15, observations, false));
        assertThrows(IllegalArgumentException.class,
                () -> new FeatureMarker("SERVICE_PROVIDER", 0, List.of(), false));
    }

    @Test
    void rejectsValuesOutsideClosedSchemaEnums() {
        assertThrows(IllegalArgumentException.class,
                () -> new ThirdPartyDependency("example", "GUESS", false, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new NamespaceUsage("UNRECOGNIZED", 0, 0, List.of(), List.of(), false, false));
        assertThrows(IllegalArgumentException.class,
                () -> analysis("1.20.1", "UNKNOWN", List.of(), ContentInventory.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> analysis("UNKNOWN", "47.x", List.of(), ContentInventory.empty()));
    }

    @Test
    void rejectsSamplesThatWouldExceedSchemaLimits() {
        BytecodeReference reference = BytecodeReference.type("example/Type", "Lexample/Type;");
        assertThrows(IllegalArgumentException.class, () -> new ReferenceSummary(
                300,
                Collections.nCopies(257, reference),
                true,
                true
        ));
        assertThrows(IllegalArgumentException.class, () -> new ReferenceSummary(
                1,
                List.of(new BytecodeReference(
                        BytecodeReferenceKind.SUPERCLASS, "example/Base", "", "Lexample/Base;")),
                false,
                true
        ));
        assertThrows(IllegalArgumentException.class, () -> new UnsupportedFeature(
                "EXAMPLE",
                17,
                IntStream.range(0, 17).mapToObj(value -> "evidence-" + value).toList(),
                true
        ));

        List<String> packages = IntStream.range(0, 257)
                .mapToObj(value -> "example.package" + value)
                .toList();
        assertThrows(IllegalArgumentException.class,
                () -> analysis("UNKNOWN", "UNKNOWN", packages, ContentInventory.empty()));

        ContentInventory excessiveContentSamples = new ContentInventory(
                0, 0, 0,
                new ContentNamespaceSummary(17,
                        IntStream.range(0, 17).mapToObj(value -> "namespace" + value).toList(), false),
                ContentNamespaceSummary.EMPTY,
                Map.of()
        );
        assertThrows(IllegalArgumentException.class,
                () -> analysis("UNKNOWN", "UNKNOWN", List.of(), excessiveContentSamples));
    }

    @Test
    void rejectsManifestAndBytecodeShapesThatCannotBeSerializedBySchema() {
        assertThrows(IllegalArgumentException.class, () -> new ManifestSummary(
                true, true, Map.of("Arbitrary-Attribute", "value"), false));
        assertThrows(IllegalArgumentException.class, () -> new ManifestSummary(
                true, true, Map.of("MixinConfigs", "x".repeat(4097)), true));
        assertThrows(NullPointerException.class, () -> new ManifestSummary(
                true, true, Collections.singletonMap("MixinConfigs", null), false));

        assertThrows(IllegalArgumentException.class, () -> new BytecodeSummary(
                1, 0, 0, 0, new TreeMap<>(Map.of(52, 0)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new BytecodeSummary(
                1, 0, 0, 0, new TreeMap<>(Map.of(-1, 1)), List.of()));
    }

    @Test
    void rejectsBlankRequiredTextAndNullCollectionMembers() {
        assertThrows(IllegalArgumentException.class,
                () -> new FeatureMarker(" ", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Finding("", Severity.INFO, "test", "message", List.of()));
        assertThrows(NullPointerException.class, () -> new StaticArtifact(
                "fixture", true, 0, Collections.singletonMap("key", null),
                List.of(), false, false));
    }

    private static LegacyAnalysisSummary analysis(
            String minecraftVersion,
            String forgeVersion,
            List<String> packages,
            ContentInventory content
    ) {
        return new LegacyAnalysisSummary(
                "example", "1.0.0", minecraftVersion, forgeVersion, 0,
                ReferenceSummary.EMPTY, ReferenceSummary.EMPTY,
                List.of(), false, List.of(), List.of(), List.of(), List.of(),
                0, Complexity.SIMPLE, packages, false,
                ArchiveReferenceInventory.EMPTY, content, ManifestSummary.ABSENT
        );
    }
}
