package dev.legacybridge.converter.application;

import dev.legacybridge.converter.content.ContentCategory;
import dev.legacybridge.converter.report.Complexity;
import dev.legacybridge.converter.report.AnalysisStatus;
import dev.legacybridge.converter.report.ThirdPartyDependency;
import dev.legacybridge.converter.testutil.SyntheticForgeJarFactory;
import dev.legacybridge.converter.testutil.TestJarBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyModAnalyzerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void emitsRequestedAnalyzerFieldsAndDetailedStaticEvidence() throws Exception {
        Path input = SyntheticForgeJarFactory.createFeatureRich(
                temporaryDirectory.resolve("feature-rich.jar")
        );
        byte[] before = Files.readAllBytes(input);

        var report = new AnalysisService().analyze(input);
        var analysis = report.analysis();

        assertEquals("legacybridge_fixture", analysis.modId());
        assertEquals("1.0.0", analysis.modVersion());
        assertEquals("1.16.5", analysis.detectedMinecraftVersion());
        assertEquals("36.x", analysis.detectedForgeVersion());
        assertEquals(2, analysis.classCount());
        assertFalse(analysis.minecraftReferences().references().isEmpty());
        assertFalse(analysis.forgeReferences().references().isEmpty());
        assertTrue(analysis.thirdPartyDependencies().stream()
                .map(ThirdPartyDependency::identifier).anyMatch("jei"::equals));
        assertEquals(1, analysis.mixins().size());
        assertEquals(2, analysis.mixins().get(0).declarationCount());
        assertEquals(1, analysis.accessTransformers().get(0).declarationCount());
        assertEquals(1, analysis.coremods().get(0).declarationCount());
        assertEquals(Complexity.EXTREME, analysis.complexity());
        assertTrue(analysis.riskScore() >= 60);

        Set<String> featureIds = analysis.unsupportedFeatures().stream()
                .map(feature -> feature.id()).collect(Collectors.toSet());
        assertTrue(featureIds.containsAll(Set.of(
                "LEGACY_REGISTRIES", "LEGACY_EVENTS", "LEGACY_NETWORKING", "LEGACY_CAPABILITIES",
                "CUSTOM_ENTITIES", "TILE_ENTITY", "CLIENT_RENDERING", "GUI",
                "WORLD_GENERATION", "DIMENSIONS", "RECIPES", "LOOT",
                "REFLECTION", "MIXINS", "ACCESS_TRANSFORMER", "COREMOD"
        )));
        var markerCounts = report.markers().stream().collect(Collectors.toMap(
                marker -> marker.id(), marker -> marker.occurrenceCount()));
        assertEquals(1L, markerCounts.get("MIXIN_CONFIGURATION"));
        assertEquals(1L, markerCounts.get("ACCESS_TRANSFORMER"));
        assertEquals(1L, markerCounts.get("COREMOD"));
        var featureCounts = analysis.unsupportedFeatures().stream().collect(Collectors.toMap(
                feature -> feature.id(), feature -> feature.occurrenceCount()));
        assertEquals(1L, featureCounts.get("MIXINS"));
        assertEquals(1L, featureCounts.get("ACCESS_TRANSFORMER"));
        assertEquals(1L, featureCounts.get("COREMOD"));
        assertEquals(1, analysis.content().entryCount(ContentCategory.DATA_RECIPES));
        assertEquals(1, analysis.content().entryCount(ContentCategory.DATA_LOOT_TABLES));
        assertEquals(1, analysis.content().entryCount(ContentCategory.DATA_WORLDGEN));
        assertEquals(1, analysis.content().entryCount(ContentCategory.DATA_DIMENSIONS));

        var featureClass = analysis.classReferences().classDetails().stream()
                .filter(detail -> detail.internalName().endsWith("FeatureMod"))
                .findFirst().orElseThrow();
        assertTrue(featureClass.references().samples().stream().anyMatch(reference ->
                reference.owner().equals("java/lang/Class")
                        && reference.memberName().equals("forName")));
        assertTrue(featureClass.references().samples().stream().anyMatch(reference ->
                reference.owner().equals("net/minecraft/tileentity/TileEntity")));
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(input)),
                "static analysis must not modify the input JAR");

        String json = new dev.legacybridge.converter.report.CanonicalReportWriter().toJson(report);
        for (String required : Set.of(
                "modId", "modVersion", "detectedMinecraftVersion", "detectedForgeVersion",
                "classCount", "minecraftReferences", "forgeReferences",
                "thirdPartyDependencies", "mixins", "accessTransformers", "coremods",
                "unsupportedFeatures", "riskScore", "complexity", "classReferences", "content"
        )) {
            assertTrue(json.contains("\"" + required + "\""), required);
        }
        assertTrue(json.contains("\"superclass\": \"net/minecraft/tileentity/TileEntity\""));
        assertTrue(json.contains("\"method\": \"forName\""));
        assertNotNull(report.input().sha256());
    }

    @Test
    void resourceLocationAndModAnnotationAloneRemainSimple() throws Exception {
        var report = new AnalysisService().analyze(
                SyntheticForgeJarFactory.create(temporaryDirectory.resolve("simple.jar"))
        );

        assertEquals(0, report.riskScore());
        assertEquals(Complexity.SIMPLE, report.complexity());
        assertTrue(report.analysis().unsupportedFeatures().isEmpty());
    }

    @Test
    void missingForgeMetadataIsExplicitlyPartialAndNotSimple() throws Exception {
        Path input = TestJarBuilder.write(
                temporaryDirectory.resolve("missing-metadata.jar"),
                Map.of("assets/example/lang/en_us.json", "{}".getBytes(StandardCharsets.UTF_8))
        );

        var report = new AnalysisService().analyze(input);

        assertEquals(AnalysisStatus.PARTIAL, report.assessment().analysisStatus());
        assertEquals(Complexity.MODERATE, report.complexity());
        assertTrue(report.analysis().unsupportedFeatures().stream()
                .anyMatch(feature -> "METADATA_UNCERTAINTY".equals(feature.id())));
    }

    @Test
    void neverExecutesStaticInitializersFromTheInputJar() throws Exception {
        String property = "legacybridge.fixture.must.not.execute";
        System.clearProperty(property);
        Path input = SyntheticForgeJarFactory.create(
                temporaryDirectory.resolve("non-execution.jar"),
                Map.of(
                        "dev/legacybridge/fixture/ExplosiveInitializer.class",
                        SyntheticForgeJarFactory.classWithObservableStaticInitializer(property)
                )
        );

        new AnalysisService().analyze(input);

        assertEquals(null, System.getProperty(property));
    }

    @Test
    void wrongCaseLoaderFilesAreOnlyNonCanonicalContent() throws Exception {
        Path input = SyntheticForgeJarFactory.create(
                temporaryDirectory.resolve("wrong-case.jar"),
                Map.of(
                        "meta-inf/AccessTransformer.cfg", "public example.Class".getBytes(StandardCharsets.UTF_8),
                        "meta-inf/CoreMods.json", "{}".getBytes(StandardCharsets.UTF_8),
                        "meta-inf/services/cpw.mods.modlauncher.api.itransformationservice", new byte[0]
                )
        );

        var report = new AnalysisService().analyze(input);

        assertTrue(report.analysis().accessTransformers().isEmpty());
        assertTrue(report.analysis().coremods().isEmpty());
        assertFalse(report.markers().stream().anyMatch(marker ->
                Set.of("ACCESS_TRANSFORMER", "COREMOD", "TRANSFORMATION_SERVICE").contains(marker.id())));
        assertEquals(3, report.analysis().content().entryCount(
                ContentCategory.NON_CANONICAL_RESOURCE_PATH));
    }

    @Test
    void parsesNonConventionalMixinDeclaredByManifestInBoundedSecondPass() throws Exception {
        Path input = SyntheticForgeJarFactory.create(
                temporaryDirectory.resolve("declared-mixin.jar"),
                Map.of(
                        "META-INF/MANIFEST.MF",
                        ("Manifest-Version: 1.0\r\nMixinConfigs: config/custom.json\r\n\r\n")
                                .getBytes(StandardCharsets.UTF_8),
                        "config/custom.json",
                        "{\"package\":\"dev.fixture.mixin\",\"mixins\":[\"DeclaredMixin\"]}"
                                .getBytes(StandardCharsets.UTF_8)
                )
        );

        var report = new AnalysisService().analyze(input);

        assertEquals(1, report.analysis().mixins().size());
        assertEquals("config/custom.json", report.analysis().mixins().get(0).path());
        assertTrue(report.analysis().mixins().get(0).parsed());
        assertEquals(Set.of("DeclaredMixin"),
                Set.copyOf(report.analysis().mixins().get(0).attributes().get("mixins")));
    }

    @Test
    void wrongCaseClassSuffixIsNotParsedAsJvmBytecode() throws Exception {
        Path input = SyntheticForgeJarFactory.create(
                temporaryDirectory.resolve("wrong-class-case.jar"),
                Map.of("not/ActuallyAClass.CLASS", new byte[]{1, 2, 3})
        );

        var report = new AnalysisService().analyze(input);

        assertEquals(1, report.bytecode().classCount());
        assertEquals(0, report.bytecode().unreadableClassCount());
        assertEquals(1, report.analysis().content().entryCount(
                ContentCategory.NON_CANONICAL_RESOURCE_PATH));
    }

    @Test
    void missingManifestDeclaredMixinMakesAnalysisPartial() throws Exception {
        Path input = SyntheticForgeJarFactory.create(
                temporaryDirectory.resolve("missing-mixin.jar"),
                Map.of("META-INF/MANIFEST.MF",
                        ("Manifest-Version: 1.0\r\nMixinConfigs: absent/custom.json\r\n\r\n")
                                .getBytes(StandardCharsets.UTF_8))
        );

        var report = new AnalysisService().analyze(input);

        assertEquals(AnalysisStatus.PARTIAL, report.assessment().analysisStatus());
        assertTrue(report.findings().stream().anyMatch(finding -> "LB-CFG107".equals(finding.code())));
        assertEquals("absent/custom.json", report.analysis().mixins().get(0).path());
        assertTrue(report.analysis().mixins().get(0).partial());
    }

    @Test
    void legacyBootstrapManifestHooksAreReportedAsExtremeCoremodArtifacts() throws Exception {
        Path input = SyntheticForgeJarFactory.create(
                temporaryDirectory.resolve("legacy-bootstrap.jar"),
                Map.of("META-INF/MANIFEST.MF",
                        ("Manifest-Version: 1.0\r\n"
                                + "TweakClass: example.LegacyTweaker\r\n"
                                + "ForceLoadAsMod: true\r\n\r\n")
                                .getBytes(StandardCharsets.UTF_8))
        );

        var report = new AnalysisService().analyze(input);

        assertEquals(Complexity.EXTREME, report.complexity());
        assertEquals(2, report.analysis().coremods().size());
        assertTrue(report.analysis().unsupportedFeatures().stream()
                .anyMatch(feature -> "LEGACY_BOOTSTRAP".equals(feature.id())));
        assertTrue(report.findings().stream().anyMatch(finding -> "LB-F013".equals(finding.code())));
    }

    @Test
    void specialEntryFeatureCountsRemainExactBeyondRetainedArchiveSamples() throws Exception {
        Map<String, byte[]> nested = new java.util.TreeMap<>();
        for (int index = 0; index < 10; index++) {
            nested.put("META-INF/jars/dependency" + index + ".jar", new byte[0]);
        }
        Path input = SyntheticForgeJarFactory.create(
                temporaryDirectory.resolve("nested-counts.jar"),
                nested
        );

        var report = new AnalysisService().analyze(input);
        var feature = report.analysis().unsupportedFeatures().stream()
                .filter(value -> "NESTED_JAR".equals(value.id()))
                .findFirst().orElseThrow();

        assertEquals(10, feature.occurrenceCount());
        assertTrue(feature.evidenceTruncated());
    }

    @Test
    void multiModIdentitySelectsFirstValidSortedDeclaration() throws Exception {
        String metadata = """
                modLoader="javafml"
                loaderVersion="[36,)"
                [[mods]]
                modId="bad-id"
                version="9"
                [[mods]]
                modId="valid_mod"
                version="2.0"
                [[dependencies.valid_mod]]
                modId="minecraft"
                mandatory=true
                versionRange="[1.16.5,1.17)"
                ordering="NONE"
                side="BOTH"
                """;
        Path input = SyntheticForgeJarFactory.create(
                temporaryDirectory.resolve("multi-mod.jar"),
                Map.of("META-INF/mods.toml", metadata.getBytes(StandardCharsets.UTF_8))
        );

        var report = new AnalysisService().analyze(input);

        assertEquals("valid_mod", report.modId());
        assertEquals("2.0", report.modVersion());
        assertEquals(2, report.metadata().mods().size());
        assertTrue(report.findings().stream().anyMatch(finding -> "LB-M020".equals(finding.code())));
    }

    @Test
    void optionalMinecraftDependencyDoesNotAssertDetectedSourceVersion() throws Exception {
        String metadata = """
                modLoader="javafml"
                loaderVersion="[36,)"
                [[mods]]
                modId="example"
                [[dependencies.example]]
                modId="minecraft"
                mandatory=false
                versionRange="[1.16.5,1.17)"
                ordering="NONE"
                side="BOTH"
                """;
        Path input = SyntheticForgeJarFactory.create(
                temporaryDirectory.resolve("optional-minecraft.jar"),
                Map.of("META-INF/mods.toml", metadata.getBytes(StandardCharsets.UTF_8))
        );

        var report = new AnalysisService().analyze(input);

        assertEquals("UNKNOWN", report.analysis().detectedMinecraftVersion());
        assertEquals("36.x", report.analysis().detectedForgeVersion());
    }
}
