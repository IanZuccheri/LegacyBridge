package dev.legacybridge.product.controller;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual inert source archives through the production controller/engine. No class definition or game launch. */
@EnabledIfSystemProperty(named = "legacybridge.productCohortBundle", matches = ".+")
class CohortInspectionEnvironmentTest {
    @TempDir Path game;
    private String bundle() { return System.getProperty("legacybridge.productCohortBundle"); }
    private List<Path> source() throws Exception {
        var files = new ArrayList<Path>(); Path input = Files.createDirectories(game.resolve("legacybridge/input"));
        for (String role : List.of("library", "consumer")) {
            String filename = "legacybridge-cohort-" + role + "-1.16.5-1.0.0.jar";
            Path copy = input.resolve(filename);
            Files.copy(Path.of(System.getProperty("legacybridge.productCohortFixtureRoot")).resolve(filename), copy); files.add(copy);
        }
        return List.copyOf(files);
    }
    @Test void controllerAndSharedServiceProduceIdenticalPairEvidenceWithoutInstallableJobs() throws Exception {
        var originals = source(); var hashes = originals.stream().map(CohortInspectionEnvironmentTest::hash).toList();
        Object recorded; CohortPlanningRequest request;
        try (var controller = inventoried()) {
            var selection = controller.snapshot().entries();
            assertTrue(controller.inspectCohort(selection, bundle())); await(controller);
            var view = controller.cohortInspection();
            assertEquals(CohortInspectionView.Status.STATIC_PAIR_VALIDATED, view.status(), view.error().toString());
            assertEquals(2, view.candidates().size()); assertEquals(3, view.sources().stream().mapToInt(CohortInspectionView.Source::classes).sum());
            assertEquals(Set.of("facd86ef056da22304c0e78fd2a892fda08f3a4d9e0142bfac6b63e255cff88b", "434d3010118fcde50c7c0783ae3f1ba3f7291466ba82c8324d91e244c48f97bd"),
                    new HashSet<>(view.candidates().stream().map(CohortInspectionView.Candidate::sha256).toList()));
            assertTrue(view.sources().stream().allMatch(CohortInspectionView.Source::linkageChecked));
            assertTrue(view.sources().stream().flatMap(s -> s.dependencies().stream()).anyMatch(d -> d.modId().equals("lb_cohort_library") && d.mandatory() && d.ordering().equals("AFTER")));
            assertFalse(view.publicConversionAllowed()); assertFalse(view.installationAllowed()); assertTrue(controller.jobs().isEmpty());
            var report = (Map<?, ?>) BoundedJsonParser.parse(Files.readString(view.report().orElseThrow()));
            assertEquals("PRODUCT_COHORT_PREVIEW", report.get("reportType")); assertEquals(false, report.get("installationAllowed"));
            recorded = report.get("engineReport");
            request = new CohortPlanningRequest(new CohortInspectionRequest(view.inputs().stream().map(i ->
                    new CohortInspectionRequest.Artifact(i.artifactId(), i.path(), i.sha256().orElseThrow())).toList(), MappingData.bundle(Path.of(bundle()))));
            String evidence = System.getProperty("legacybridge.productCohortEvidence", "");
            if (!evidence.isEmpty()) Files.writeString(Files.createTempFile(Files.createDirectories(Path.of(evidence)), "cohort-preview-", ".json"), Files.readString(view.report().orElseThrow()));
            assertNoArtifacts(); assertEquals(hashes, originals.stream().map(CohortInspectionEnvironmentTest::hash).toList());
            assertTrue(controller.refreshHistory()); await(controller);
            assertTrue(controller.history().jobs().isEmpty(), "A preview report is not a job receipt");
            assertTrue(controller.history().issues().isEmpty());
            // Returning to ordinary conversion does not inherit a preview pass or enable a library.
            assertTrue(controller.enqueue(List.of(0, 1), "")); await(controller);
            assertEquals(List.of(JobState.BLOCKED, JobState.BLOCKED), controller.jobs().stream().map(JobView::status).toList());
            assertNoArtifacts();
        }
        try (var service = new LegacyEngineService()) {
            var outcome = service.planCohort(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, outcome.status(), outcome.toString());
            assertEquals(recorded, BoundedJsonParser.parse(new CanonicalReportWriter().toJsonTree(outcome.value().orElseThrow().toTree())));
        }
        System.out.println("PRODUCT_COHORT_PREVIEW_OK sourceSha256=" + hashes + " jobs=none installationAllowed=false native=NOT_RUN");
    }
    @Test void blankFieldUsesIncludedCohortDataWithTheSamePrivateCandidatesButNoApply() throws Exception {
        source();
        try (var controller = inventoried()) {
            var selection = controller.snapshot().entries();
            assertTrue(controller.inspectCohort(selection, "")); await(controller);
            var view = controller.cohortInspection();
            assertEquals(CohortInspectionView.Status.STATIC_PAIR_VALIDATED, view.status(), view.error().toString());
            assertFalse(view.installationAllowed()); assertTrue(controller.jobs().isEmpty()); assertNoArtifacts();
            var envelope = (Map<?, ?>) BoundedJsonParser.parse(Files.readString(view.report().orElseThrow()));
            var engine = (Map<?, ?>) envelope.get("engineReport");
            var source = (Map<?, ?>) engine.get("sourceInspection");
            var data = (Map<?, ?>) source.get("mappingData");
            assertEquals("FINITE_COHORT_CONTRACT", data.get("scope")); assertEquals(false, data.get("completeMappingEnvironment"));
            var candidates = view.candidates();
            assertTrue(controller.inspectCohort(selection, bundle())); await(controller);
            assertEquals(candidates, controller.cohortInspection().candidates()); assertNoArtifacts();
            assertTrue(controller.inspectCohort(selection, game.resolve("missing-explicit-bundle").toString())); await(controller);
            assertEquals(CohortInspectionView.Status.FAILED, controller.cohortInspection().status());
            assertTrue(controller.cohortInspection().candidates().isEmpty(), "Bad explicit data must not reuse the prior included-data pass");
        }
    }
    @Test void missingLibraryBlocksAndExplainsSourceDependencyInsteadOfApprovingPartialTarget() throws Exception {
        source();
        try (var controller = inventoried()) {
            var consumer = controller.snapshot().entries().stream().filter(e -> e.mods().stream().anyMatch(m -> m.modId().equals("lb_cohort_consumer"))).findFirst().orElseThrow();
            assertTrue(controller.inspectCohort(List.of(consumer), bundle())); await(controller);
            var view = controller.cohortInspection(); assertEquals(CohortInspectionView.Status.BLOCKED, view.status(), view.error().toString());
            assertTrue(view.candidates().isEmpty()); assertFalse(view.diagnostics().isEmpty()); assertTrue(view.report().isPresent());
            assertTrue(Files.readString(view.report().orElseThrow()).contains("lb_cohort_library")); assertTrue(controller.jobs().isEmpty()); assertNoArtifacts();
        }
    }
    @Test void changedSourceCannotReuseTheObservedHash() throws Exception {
        var inputs = source();
        try (var controller = inventoried()) {
            var rows = controller.snapshot().entries();
            Files.write(inputs.get(0), new byte[] {0}, StandardOpenOption.APPEND);
            assertTrue(controller.inspectCohort(rows, bundle())); await(controller);
            assertNotEquals(CohortInspectionView.Status.STATIC_PAIR_VALIDATED, controller.cohortInspection().status());
            assertTrue(controller.cohortInspection().candidates().isEmpty()); assertTrue(controller.jobs().isEmpty());
            assertNoArtifacts();
        }
    }
    @Test void unknownThirdArtifactBlocksEvenWhenTheAuditedPairIsIntact() throws Exception {
        var inputs = source();
        Path unknown = inputs.get(0).resolveSibling("third.jar"); ReceiptSourceFixture.write(unknown, "unreviewed_third");
        try (var controller = inventoried()) {
            assertTrue(controller.inspectCohort(controller.snapshot().entries(), bundle())); await(controller);
            assertEquals(CohortInspectionView.Status.BLOCKED, controller.cohortInspection().status());
            assertTrue(controller.cohortInspection().candidates().isEmpty()); assertTrue(controller.jobs().isEmpty()); assertNoArtifacts();
        }
    }
    @Test void cancelAndCloseDoNotPublishAndBlockOtherCoordinatorWorkWhileRunning() throws Exception {
        source();
        try (var controller = inventoried()) {
            var selection = controller.snapshot().entries();
            assertTrue(controller.inspectCohort(selection, bundle()));
            assertFalse(controller.inspectCohort(selection, bundle())); assertFalse(controller.refresh());
            assertFalse(controller.enqueue(List.of(0), ""));
            assertTrue(controller.cancel(), "Cancellation must actually win; a completed proof is not a cancelled one");
            assertTimeoutPreemptively(Duration.ofSeconds(1), controller::close); await(controller);
            assertEquals(CohortInspectionView.Status.CANCELLED, controller.cohortInspection().status());
            assertTrue(controller.cohortInspection().report().isEmpty()); assertTrue(controller.cohortInspection().candidates().isEmpty());
            assertTrue(controller.jobs().isEmpty()); assertNoArtifacts();
            try (var files = Files.list(game.resolve("legacybridge/reports"))) { assertEquals(0, files.count()); }
            assertFalse(controller.inspectCohort(selection, bundle()));
        }
    }
    @Test void reportWorkspaceObstructionFailsWithoutDestroyingTheObstructingFile() throws Exception {
        source();
        try (var controller = inventoried()) {
            Path reports = game.resolve("legacybridge/reports"); Files.delete(reports); Files.writeString(reports, "test-owned obstruction");
            assertTrue(controller.inspectCohort(controller.snapshot().entries(), bundle())); await(controller);
            assertEquals(CohortInspectionView.Status.FAILED, controller.cohortInspection().status());
            assertTrue(controller.cohortInspection().report().isEmpty()); assertTrue(controller.jobs().isEmpty());
            assertEquals("test-owned obstruction", Files.readString(reports)); assertNoArtifacts();
        }
    }
    private ConversionController inventoried() throws Exception {
        var controller = new ConversionController(game); assertTrue(controller.refresh()); await(controller); return controller;
    }
    private void assertNoArtifacts() throws Exception {
        assertFalse(Files.exists(game.resolve("mods")));
        try (var files = Files.list(game.resolve("legacybridge/staging"))) { assertEquals(0, files.count()); }
    }
    private static String hash(Path path) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static void await(ConversionController controller) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (controller.snapshot().busy() && System.nanoTime() < deadline) Thread.sleep(10);
        assertFalse(controller.snapshot().busy(), "Preview timed out");
    }
}
