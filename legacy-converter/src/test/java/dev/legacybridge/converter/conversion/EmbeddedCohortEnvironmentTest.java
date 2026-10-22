package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.dependency.CohortSourceInspector;
import dev.legacybridge.converter.mapping.builtin.EmbeddedCohortData;
import dev.legacybridge.converter.mapping.portable.PortableMappingBundle;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exact authored inputs are data only. The complete environment is a development oracle, never operational fallback. */
@EnabledIfSystemProperty(named = "legacybridge.cohortBundle", matches = ".+")
class EmbeddedCohortEnvironmentTest {
    @TempDir Path temp;
    static MappingInputs full;
    static List<CohortInspectionRequest.Artifact> pair;
    @BeforeAll static void data() throws Exception {
        full = PortableMappingBundle.load(Path.of(System.getProperty("legacybridge.cohortBundle")));
        var inputs = new ArrayList<CohortInspectionRequest.Artifact>();
        for (String id : List.of("consumer", "library")) {
            Path file = Path.of(System.getProperty("legacybridge.fixtureRoot"), "dependency-cohort-1.16.5/build/libs/legacybridge-cohort-" + id + "-1.16.5-1.0.0.jar");
            inputs.add(new CohortInspectionRequest.Artifact(id, file, SimpleContentConverter.hash(Files.readAllBytes(file))));
        }
        pair = List.copyOf(inputs);
    }
    @AfterAll static void releaseDevelopmentOracle() { full = null; pair = null; }
    @Test void completeRetainedDeclarationAndProvenanceMatchBothActualVersionInventories() throws Exception {
        var included = EmbeddedCohortData.load();
        assertEquals(full.legacyApi().getClass(EmbeddedCohortData.MOD), included.legacyApi().getClass(EmbeddedCohortData.MOD));
        assertEquals(full.targetApi().getClass(EmbeddedCohortData.MOD), included.targetApi().getClass(EmbeddedCohortData.MOD));
        for (var source : included.sources()) if (source.id().contains("apiJars")) assertTrue(full.sources().stream().anyMatch(s -> s.id().equals(source.id()) && s.sha256().equals(source.sha256())));
        for (var mapping : List.of(full.legacyOfficial(), full.targetOfficial(), full.legacySrg(), full.targetSrg()))
            assertNull(mapping.getClass(EmbeddedCohortData.MOD), "Forge declaration must not be inferred through Minecraft renames");
    }
    @Test void fullAndFinitePlansHaveIdenticalCandidatesLogsGraphsAndChecksExceptHonestDataProvenance() throws Exception {
        var request = new CohortPlanningRequest(new CohortInspectionRequest(pair, MappingData.builtinCohortPreview()));
        var included = new CohortTargetPlanner().plan(request, EmbeddedCohortData.load());
        var complete = new CohortTargetPlanner().plan(request, full);
        assertTrue(included.validated(), included.toTree().toString()); assertTrue(complete.validated());
        assertEquals(complete.candidates(), included.candidates()); assertEquals(complete.transformationLog(), included.transformationLog());
        var expected = new TreeMap<>(complete.toTree()); var actual = new TreeMap<>(included.toTree());
        expected.put("sourceInspection", withoutProvenance(complete.source())); actual.put("sourceInspection", withoutProvenance(included.source()));
        assertEquals(new CanonicalReportWriter().toJsonTree(expected), new CanonicalReportWriter().toJsonTree(actual));
        assertEquals(false, ((Map<?, ?>) included.source().toTree().get("mappingData")).get("completeMappingEnvironment"));
        try (var engine = new LegacyEngineService()) {
            var result = engine.planCohort(request).completion().toCompletableFuture().get();
            assertEquals(EngineOutcome.Status.COMPLETED, result.status()); assertEquals(included.toTree(), result.value().orElseThrow().toTree());
        }
        Path evidence = Path.of(System.getProperty("legacybridge.cohortEvidence")); Files.createDirectories(evidence);
        Files.writeString(Files.createTempFile(evidence, "included-cohort-", ".json"), new CanonicalReportWriter().toJsonTree(included.toTree()));
        for (var source : pair) assertEquals(source.expectedSha256(), SimpleContentConverter.hash(Files.readAllBytes(source.input())));
    }
    @Test void missingLibraryReportsDependencyButLibraryAloneDoesNotApprovePair() throws Exception {
        for (var source : pair) {
            var request = new CohortPlanningRequest(new CohortInspectionRequest(List.of(source), MappingData.builtinCohortPreview()));
            var result = new CohortTargetPlanner().plan(request, EmbeddedCohortData.load());
            assertFalse(result.validated()); assertTrue(result.candidates().isEmpty());
            assertEquals(source.id().equals("library"), result.source().sourceLinkagePassed());
        }
    }
    @Test void actualUnreviewedArchiveBytesCannotUseTheSmallerOwnershipInventory() throws Exception {
        var original = pair.get(0); Path changed = temp.resolve("changed.jar");
        var entries = new TreeMap<String, byte[]>();
        try (var zip = new java.util.zip.ZipFile(original.input().toFile())) {
            for (var entry : java.util.Collections.list(zip.entries())) if (!entry.isDirectory())
                try (var stream = zip.getInputStream(entry)) { entries.put(entry.getName(), stream.readAllBytes()); }
        }
        entries.put("unreviewed.txt", new byte[]{0});
        Files.write(changed, SimpleContentConverter.jar(entries), StandardOpenOption.CREATE_NEW);
        var changedRow = new CohortInspectionRequest.Artifact(original.id(), changed, SimpleContentConverter.hash(Files.readAllBytes(changed)));
        var request = new CohortInspectionRequest(List.of(changedRow, pair.get(1)), MappingData.builtinCohortPreview());
        var result = new CohortSourceInspector().inspect(request, EmbeddedCohortData.load());
        assertFalse(result.sourceLinkagePassed()); assertTrue(result.artifacts().isEmpty());
        assertEquals("FINITE_COHORT_DATA_SCOPE", result.diagnostics().get(0).error().code());
        // Claiming the reviewed digest does not grant authority: real bytes are rehashed first.
        var forged = new CohortInspectionRequest.Artifact(original.id(), changed, original.expectedSha256());
        var mismatch = new CohortSourceInspector().inspect(new CohortInspectionRequest(List.of(forged), request.mappingData()), EmbeddedCohortData.load());
        assertEquals("INPUT_CHANGED", mismatch.diagnostics().get(0).error().code());
    }
    private static Map<String, Object> withoutProvenance(CohortSourceInspector.Result source) {
        var tree = new TreeMap<>(source.toTree()); tree.remove("mappingDataSources"); tree.remove("mappingData"); return tree;
    }
}
