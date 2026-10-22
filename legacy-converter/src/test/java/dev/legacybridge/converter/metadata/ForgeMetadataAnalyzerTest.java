package dev.legacybridge.converter.metadata;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeMetadataAnalyzerTest {
    @Test
    void parsesRealForge1165ModsTomlShape() {
        String toml = """
                modLoader="javafml"
                loaderVersion="[36,)"
                license="All Rights Reserved"

                [[mods]]
                modId="example"
                version="1.0.0"
                displayName="Example"

                [[dependencies.example]]
                modId="forge"
                mandatory=true
                versionRange="[36,)"
                ordering="NONE"
                side="BOTH"

                [[dependencies.example]]
                modId="minecraft"
                mandatory=true
                versionRange="[1.16.5,1.17)"
                ordering="NONE"
                side="BOTH"
                """;

        MetadataAnalysis result = new ForgeMetadataAnalyzer().analyze(toml.getBytes(StandardCharsets.UTF_8));

        assertTrue(result.summary().parsed());
        assertEquals("javafml", result.summary().modLoader());
        assertEquals("example", result.summary().mods().get(0).modId());
        assertEquals(2, result.summary().dependencies().size());
        assertTrue(result.summary().dependencies().stream().allMatch(dependency -> dependency.mandatoryDeclared()));
        assertTrue(result.valid());
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void reportsMissingAndMalformedMetadata() {
        MetadataAnalysis missing = new ForgeMetadataAnalyzer().analyze(null);
        MetadataAnalysis malformed = new ForgeMetadataAnalyzer().analyze("[[[".getBytes(StandardCharsets.UTF_8));

        assertEquals("LB-M001", missing.findings().get(0).code());
        assertEquals("LB-M006", malformed.findings().get(0).code());
        assertFalse(missing.valid());
        assertFalse(malformed.valid());
    }

    @Test
    void rejectsInvalidAndDuplicateModIdsAndUndeclaredDependencyOwners() {
        String toml = """
                modLoader="javafml"
                loaderVersion="[36,)"

                [[mods]]
                modId="Example"

                [[mods]]
                modId="bad-id"

                [[mods]]
                modId="valid_id"

                [[mods]]
                modId="valid_id"

                [[dependencies.not_declared]]
                modId="minecraft"
                mandatory=true
                versionRange="[1.16.5,1.17)"
                """;

        MetadataAnalysis result = analyze(toml);

        assertFalse(result.valid());
        assertEquals(Set.of("LB-M009", "LB-M010", "LB-M013"), findingCodes(result));
    }

    @Test
    void preservesAbsentMandatoryAndAllowsForgeDefaultUnboundedVersionRange() {
        String toml = """
                modLoader="javafml"
                loaderVersion="[36,)"

                [[mods]]
                modId="example"

                [[dependencies.example]]
                modId="forge"
                versionRange="[36,)"

                [[dependencies.example]]
                mandatory=false
                versionRange="not-a-numeric-range"
                """;

        MetadataAnalysis result = analyze(toml);
        var forge = result.summary().dependencies().stream()
                .filter(dependency -> "forge".equals(dependency.modId()))
                .findFirst()
                .orElseThrow();
        var missingModId = result.summary().dependencies().stream()
                .filter(dependency -> dependency.modId().isEmpty())
                .findFirst()
                .orElseThrow();

        assertFalse(forge.mandatoryDeclared());
        assertFalse(forge.mandatory());
        assertTrue(missingModId.mandatoryDeclared());
        assertFalse(missingModId.mandatory());
        assertFalse(result.valid());
        assertEquals(Set.of("LB-M014", "LB-M015", "LB-M019"), findingCodes(result));
    }

    @Test
    void missingDependencyVersionRangeUsesForgeUnboundedDefault() {
        String toml = """
                modLoader="javafml"
                loaderVersion="[36,)"

                [[mods]]
                modId="example"

                [[dependencies.example]]
                modId="optionalmod"
                mandatory=false
                """;

        MetadataAnalysis result = analyze(toml);

        assertTrue(result.valid());
        assertEquals("", result.summary().dependencies().get(0).versionRange());
    }

    @Test
    void retainsUnsupportedMavenRangeSyntaxButDoesNotUseItForInference() {
        String toml = """
                modLoader="javafml"
                loaderVersion="${forge_loader_range}"

                [[mods]]
                modId="example"
                """;

        MetadataAnalysis result = analyze(toml);

        assertTrue(result.valid());
        assertEquals(Set.of("LB-M007"), findingCodes(result));
    }

    @Test
    void rejectsInvalidUtf8AndExcessiveTomlNestingWithoutEscapingTheAnalyzer() {
        MetadataAnalysis invalidUtf8 = new ForgeMetadataAnalyzer().analyze(new byte[]{'#', (byte) 0xc3});
        String deeplyNested = "a=" + "[".repeat(TomlStructuralPreflight.MAX_NESTING_DEPTH + 1)
                + "0" + "]".repeat(TomlStructuralPreflight.MAX_NESTING_DEPTH + 1);
        MetadataAnalysis nested = analyze(deeplyNested);

        assertFalse(invalidUtf8.summary().parsed());
        assertFalse(nested.summary().parsed());
        assertEquals(Set.of("LB-M006"), findingCodes(invalidUtf8));
        assertEquals(Set.of("LB-M006"), findingCodes(nested));
    }

    @Test
    void missingModVersionUsesForgeDefaultOne() {
        MetadataAnalysis result = analyze("""
                modLoader="javafml"
                loaderVersion="[36,)"
                [[mods]]
                modId="example"
                """);

        assertTrue(result.valid());
        assertEquals("1", result.summary().mods().get(0).version());
    }

    @Test
    void appliesForgeDefaultsAndRejectsUnknownDependencyEnums() {
        String toml = """
                modLoader="javafml"
                loaderVersion="[36,)"

                [[mods]]
                modId="example"

                [[dependencies.example]]
                modId="minecraft"
                mandatory=true
                versionRange="[1.16.5,1.17)"

                [[dependencies.example]]
                modId="forge"
                mandatory=true
                versionRange="[36,)"
                ordering="BANANA"
                side="MOON"
                """;

        MetadataAnalysis result = analyze(toml);
        var minecraft = result.summary().dependencies().stream()
                .filter(dependency -> "minecraft".equals(dependency.modId()))
                .findFirst()
                .orElseThrow();

        assertEquals("NONE", minecraft.ordering());
        assertEquals("BOTH", minecraft.side());
        assertFalse(result.valid());
        assertEquals(Set.of("LB-M017", "LB-M018"), findingCodes(result));
    }

    private static MetadataAnalysis analyze(String toml) {
        return new ForgeMetadataAnalyzer().analyze(toml.getBytes(StandardCharsets.UTF_8));
    }

    private static Set<String> findingCodes(MetadataAnalysis analysis) {
        return analysis.findings().stream().map(finding -> finding.code()).collect(Collectors.toSet());
    }
}
