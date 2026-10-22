package dev.legacybridge.product.controller;

import dev.legacybridge.converter.service.InventoryResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CohortInspectionControllerTest {
    @TempDir Path game;
    @Test void emptyAndUnobservedSelectionsDoNotCreateWorkspace() throws Exception {
        try (var controller = new ConversionController(game)) {
            assertFalse(controller.inspectCohort(List.of(), ""));
            assertEquals(CohortInspectionView.Status.NOT_REQUESTED, controller.cohortInspection().status());
            assertFalse(Files.exists(game.resolve("legacybridge")));
            assertTrue(controller.jobs().isEmpty());
        }
    }
    @Test void includedCohortDataDoesNotBorrowSimpleAdmissionOrChangeExistingJobsAndInstallation() throws Exception {
        Path input = source(); byte[] original = Files.readAllBytes(input);
        try (var controller = inventoried()) {
            assertTrue(controller.enqueue(List.of(0), "")); ConversionQueueTest.await(controller);
            assertEquals(JobState.CONVERTED, controller.jobs().get(0).status());
            var jobs = controller.jobs(); var install = controller.installation();
            assertTrue(controller.inspectCohort(controller.snapshot().entries(), "")); ConversionQueueTest.await(controller);
            assertEquals("COHORT_TARGET_BLOCKED", controller.cohortInspection().error().orElseThrow().code());
            assertEquals(CohortInspectionView.Status.BLOCKED, controller.cohortInspection().status());
            assertTrue(controller.cohortInspection().diagnostics().stream().anyMatch(d -> d.code().equals("FINITE_COHORT_DATA_SCOPE")));
            assertEquals(jobs, controller.jobs()); assertEquals(install, controller.installation());
            assertTrue(controller.cohortInspection().report().isPresent());
            assertArrayEquals(original, Files.readAllBytes(input));
        }
    }
    @Test void rejectsRepeatedTooManyAndForeignRowsBeforeStarting() throws Exception {
        source();
        try (var controller = inventoried(); var other = new ConversionController(game.resolve("other"))) {
            var row = controller.snapshot().entries().get(0);
            assertFalse(other.inspectCohort(List.of(row), ""));
            assertFalse(Files.exists(game.resolve("other/legacybridge")));
            assertFalse(controller.inspectCohort(List.of(row, row), ""));
            assertFalse(controller.inspectCohort(java.util.Collections.nCopies(5, row), ""));
            assertEquals(CohortInspectionView.Status.NOT_REQUESTED, controller.cohortInspection().status());
        }
    }
    @Test void inventoryRefreshInvalidatesFrozenReview() throws Exception {
        Path input = source();
        try (var controller = inventoried()) {
            var frozen = controller.snapshot().entries();
            Files.delete(input); assertTrue(controller.refresh()); ConversionQueueTest.await(controller);
            assertFalse(controller.inspectCohort(frozen, ""));
            assertEquals(CohortInspectionView.Status.NOT_REQUESTED, controller.cohortInspection().status());
        }
    }
    @Test void invalidBundlePathsRemainRetryableWithoutReportsOrJobs() throws Exception {
        source();
        try (var controller = inventoried()) {
            for (String bundle : List.of("relative", "x".repeat(4097), game.resolve("missing").toString())) {
                assertTrue(controller.inspectCohort(controller.snapshot().entries(), bundle)); ConversionQueueTest.await(controller);
                assertEquals(CohortInspectionView.Status.FAILED, controller.cohortInspection().status());
                assertTrue(controller.cohortInspection().report().isEmpty()); assertTrue(controller.jobs().isEmpty());
            }
            assertFalse(Files.exists(game.resolve("mods")));
            try (var files = Files.list(game.resolve("legacybridge/staging"))) { assertEquals(0, files.count()); }
            try (var files = Files.list(game.resolve("legacybridge/reports"))) { assertEquals(0, files.count()); }
        }
    }
    @Test void duplicateAndCorruptInputsNeverReachProof() throws Exception {
        Path input = source(); Files.copy(input, input.resolveSibling("duplicate.jar"));
        Files.writeString(input.resolveSibling("broken.jar"), "not a jar");
        try (var controller = inventoried()) {
            for (InventoryResult.Entry row : controller.snapshot().entries()) {
                assertTrue(controller.inspectCohort(List.of(row), game.resolve("unused").toString())); ConversionQueueTest.await(controller);
                assertEquals("INELIGIBLE_COHORT_INPUT", controller.cohortInspection().error().orElseThrow().code());
                assertTrue(controller.cohortInspection().report().isEmpty()); assertTrue(controller.jobs().isEmpty());
            }
        }
    }
    private Path source() throws Exception {
        Path path = Files.createDirectories(game.resolve("legacybridge/input")).resolve("source.jar");
        ReceiptSourceFixture.write(path, "product_cohort_inert"); return path;
    }
    private ConversionController inventoried() throws Exception {
        var controller = new ConversionController(game); assertTrue(controller.refresh()); ConversionQueueTest.await(controller); return controller;
    }
}
