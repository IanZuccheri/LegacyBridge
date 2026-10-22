package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Real separately compiled Forge fixture JARs, read only. Neither original is loaded or executed. */
@EnabledIfSystemProperty(named = "legacybridge.cohortBundle", matches = ".+")
class CohortInspectionEnvironmentTest {
    private static CohortInspectionRequest request() throws Exception {
        Path root = Path.of(System.getProperty("legacybridge.fixtureRoot"), "dependency-cohort-1.16.5/build/libs");
        var selections = new ArrayList<CohortInspectionRequest.Artifact>();
        for (String id : List.of("library", "consumer")) {
            Path jar = root.resolve("legacybridge-cohort-" + id + "-1.16.5-1.0.0.jar");
            assertTrue(Files.isRegularFile(jar), "Compile the explicit authored cohort first");
            selections.add(new CohortInspectionRequest.Artifact(id, jar, sha(jar)));
        }
        return new CohortInspectionRequest(selections, MappingData.bundle(Path.of(System.getProperty("legacybridge.cohortBundle"))));
    }
    @Test void actualForgeCompiledPairPassesSourceLinkageAndMatchesCliExactly() throws Exception {
        var request = request();
        try (var engine = new LegacyEngineService()) {
            var outcome = engine.inspectCohort(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, outcome.status(), outcome.toString());
            var result = outcome.value().orElseThrow();
            assertTrue(result.sourceLinkagePassed());
            assertEquals(3, result.artifacts().stream().mapToInt(a -> a.classes().size()).sum());
            assertTrue(result.crossArtifactReferences().stream().anyMatch(r -> r.reference().name().equals("compute") && r.reference().descriptor().equals("(I)I")));
            String json = new CanonicalReportWriter().toJsonTree(result.toTree());
            var args = new ArrayList<>(List.of("inspect-cohort", "--mapping-bundle", request.mappingData().path().orElseThrow().toString()));
            for (var a : request.artifacts()) args.addAll(List.of("--artifact", a.id(), a.expectedSha256(), a.input().toString()));
            var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
            int exit = LegacyBridgeCli.run(args.toArray(String[]::new), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8), Map.of());
            assertEquals(0, exit, err.toString(StandardCharsets.UTF_8));
            assertEquals(json, out.toString(StandardCharsets.UTF_8));
            for (var a : request.artifacts()) assertEquals(a.expectedSha256(), sha(a.input()));
            Path evidence = Path.of(System.getProperty("legacybridge.cohortEvidence"));
            Files.createDirectories(evidence);
            Files.writeString(evidence.resolve("real-source-inspection.json"), json);
        }
    }
    @Test void consumerAloneIsBlockedWithoutDownloadingOrRemovingDependency() throws Exception {
        var request = request();
        var consumer = request.artifacts().stream().filter(a -> a.id().equals("consumer")).findFirst().orElseThrow();
        try (var engine = new LegacyEngineService()) {
            var outcome = engine.inspectCohort(new CohortInspectionRequest(List.of(consumer), request.mappingData())).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.BLOCKED, outcome.status());
            var result = outcome.value().orElseThrow();
            assertFalse(result.graph().orElseThrow().passed());
            assertTrue(result.artifacts().get(0).mod().dependencies().stream().anyMatch(d -> d.modId().equals("lb_cohort_library") && d.mandatory()));
        }
    }
    @Test void embeddedSimpleDataCannotImpersonateCohortData() throws Exception {
        var request = request();
        try (var engine = new LegacyEngineService()) {
            var outcome = engine.inspectCohort(new CohortInspectionRequest(request.artifacts(), MappingData.builtinSimpleContent())).completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.FAILED, outcome.status());
            assertTrue(outcome.value().isEmpty());
        }
    }
    @Test void targetPairServiceAndCliReturnIdenticalProofWithoutOutputAuthority() throws Exception {
        var request = request();
        try (var engine = new LegacyEngineService()) {
            var outcome = engine.planCohort(new CohortPlanningRequest(request)).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, outcome.status(), outcome.toString());
            var result = outcome.value().orElseThrow(); assertTrue(result.validated());
            String json = new CanonicalReportWriter().toJsonTree(result.toTree());
            var args = new ArrayList<>(List.of("plan-cohort", "--mapping-bundle", request.mappingData().path().orElseThrow().toString()));
            for (var a : request.artifacts()) args.addAll(List.of("--artifact", a.id(), a.expectedSha256(), a.input().toString()));
            var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
            int exit = LegacyBridgeCli.run(args.toArray(String[]::new), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8), Map.of());
            assertEquals(0, exit, err.toString(StandardCharsets.UTF_8)); assertEquals(json, out.toString(StandardCharsets.UTF_8));
            assertEquals(false, result.toTree().get("publicationAllowed"));
            for (var a : request.artifacts()) assertEquals(a.expectedSha256(), sha(a.input()));
            Path evidence = Path.of(System.getProperty("legacybridge.cohortEvidence")); Files.createDirectories(evidence);
            Files.writeString(evidence.resolve("real-target-pair.json"), json);
        }
    }
    private static String sha(Path file) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))); }
}
