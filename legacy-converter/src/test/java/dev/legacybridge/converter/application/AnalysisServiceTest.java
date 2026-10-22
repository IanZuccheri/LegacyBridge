package dev.legacybridge.converter.application;

import dev.legacybridge.converter.report.AnalysisStatus;
import dev.legacybridge.converter.report.ConversionStatus;
import dev.legacybridge.converter.report.Severity;
import dev.legacybridge.converter.report.SourceProfile;
import dev.legacybridge.converter.testutil.SyntheticForgeJarFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void correlatesMetadataAndJava8BytecodeWithoutPromisingConversion() throws Exception {
        Path input = SyntheticForgeJarFactory.create(temporaryDirectory.resolve("minimal.jar"));

        var report = new AnalysisService().analyze(input);

        assertEquals(AnalysisStatus.COMPLETE, report.assessment().analysisStatus());
        assertEquals(SourceProfile.FORGE_1_16_5_CANDIDATE, report.assessment().sourceProfile());
        assertEquals(ConversionStatus.NOT_IMPLEMENTED, report.assessment().conversionStatus());
        assertEquals(1, report.bytecode().classCount());
        assertEquals(Map.of(52, 1), report.bytecode().classMajorVersions());
        assertEquals("legacybridge_fixture", report.metadata().mods().get(0).modId());
        assertFalse(report.hasAtLeast(Severity.ERROR));
        assertTrue(report.findings().stream().anyMatch(finding -> "LB-P001".equals(finding.code())));
    }

    @Test
    void unsupportedTransformationMarkersAreBlockers() throws Exception {
        Path input = SyntheticForgeJarFactory.create(
                temporaryDirectory.resolve("mixin.jar"),
                Map.of("mixins.legacybridge.json", "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );

        var report = new AnalysisService().analyze(input);

        assertTrue(report.hasAtLeast(Severity.BLOCKER));
        assertTrue(report.markers().stream().anyMatch(marker -> "MIXIN_CONFIGURATION".equals(marker.id())));
        assertTrue(report.findings().stream().anyMatch(finding -> "LB-F003".equals(finding.code())));
    }

    @Test
    void unreadableClassRetainsTooNewHeaderFactsAndCannotBeCandidate() throws Exception {
        Path input = SyntheticForgeJarFactory.create(
                temporaryDirectory.resolve("unreadable-too-new.jar"),
                Map.of("broken/TooNew.class", rawHeader(0xFFFF, 62))
        );

        var report = new AnalysisService().analyze(input);

        assertEquals(AnalysisStatus.PARTIAL, report.assessment().analysisStatus());
        assertEquals(SourceProfile.AMBIGUOUS, report.assessment().sourceProfile());
        assertEquals(2, report.bytecode().classCount());
        assertEquals(1, report.bytecode().unreadableClassCount());
        assertEquals(1, report.bytecode().previewClassCount());
        assertEquals(Map.of(52, 1, 62, 1), report.bytecode().classMajorVersions());
        assertTrue(report.findings().stream().anyMatch(finding -> "LB-B002".equals(finding.code())));
        assertTrue(report.findings().stream().anyMatch(finding -> "LB-B003".equals(finding.code())));
        assertTrue(report.findings().stream().anyMatch(finding -> "LB-B004".equals(finding.code())));
    }

    @Test
    void unreadableJava8ClassStillCannotBeCandidate() throws Exception {
        Path input = SyntheticForgeJarFactory.create(
                temporaryDirectory.resolve("unreadable-java8.jar"),
                Map.of("broken/Legacy.class", rawHeader(0, 52))
        );

        var report = new AnalysisService().analyze(input);

        assertEquals(SourceProfile.AMBIGUOUS, report.assessment().sourceProfile());
        assertEquals(1, report.bytecode().unreadableClassCount());
        assertEquals(Map.of(52, 2), report.bytecode().classMajorVersions());
    }

    @Test
    void semanticallyInvalidMetadataCannotBeCandidate() throws Exception {
        String invalidMetadata = """
                modLoader="javafml"
                loaderVersion=36
                [[mods]]
                modId="legacybridge_fixture"
                [[dependencies.legacybridge_fixture]]
                modId="minecraft"
                mandatory=true
                versionRange="[1.16.5,1.17)"
                """;
        Path input = SyntheticForgeJarFactory.create(
                temporaryDirectory.resolve("invalid-metadata.jar"),
                Map.of("META-INF/mods.toml", invalidMetadata.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );

        var report = new AnalysisService().analyze(input);

        assertEquals(SourceProfile.AMBIGUOUS, report.assessment().sourceProfile());
        assertEquals("UNKNOWN", report.analysis().detectedMinecraftVersion());
        assertEquals("UNKNOWN", report.analysis().detectedForgeVersion());
        assertTrue(report.analysis().unsupportedFeatures().stream()
                .anyMatch(feature -> "METADATA_UNCERTAINTY".equals(feature.id())));
        assertTrue(report.hasAtLeast(Severity.ERROR));
        assertTrue(report.findings().stream().anyMatch(finding -> "LB-M004".equals(finding.code())));
    }

    private static byte[] rawHeader(int minor, int major) {
        return new byte[]{
                (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                (byte) (minor >>> 8), (byte) minor,
                (byte) (major >>> 8), (byte) major
        };
    }
}
