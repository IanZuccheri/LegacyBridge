package dev.legacybridge.product.controller;

import dev.legacybridge.product.install.InstallSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CompositionInspectionControllerTest {
    @TempDir Path game;

    @Test void unavailableSelectionDoesNotCreateWorkspaceOrJob() throws Exception {
        try (var controller = new ConversionController(game)) {
            assertFalse(controller.inspectSelectedComposition(""));
            assertEquals(CompositionInspectionView.Status.NOT_REQUESTED, controller.compositionInspection().status());
            assertFalse(Files.exists(game.resolve("legacybridge"))); assertTrue(controller.jobs().isEmpty());
        }
    }

    @Test void missingExplicitDataNeverFallsBackToSimpleAndDoesNotAffectExistingJobs() throws Exception {
        Path input = source(); byte[] original = Files.readAllBytes(input);
        try (var controller = inventoried()) {
            assertTrue(controller.enqueue(List.of(0), "")); ConversionQueueTest.await(controller);
            assertEquals(JobState.CONVERTED, controller.jobs().get(0).status());
            var jobs = controller.jobs(); var installation = controller.installation();
            assertTrue(controller.inspectSelectedComposition("")); ConversionQueueTest.await(controller);
            var view = controller.compositionInspection();
            assertEquals(CompositionInspectionView.Status.FAILED, view.status());
            assertEquals("COMPOSITION_MAPPING_DATA_REQUIRED", view.error().orElseThrow().code());
            assertTrue(view.report().isEmpty()); assertFalse(view.publicConversionAllowed()); assertFalse(view.installationAllowed());
            assertEquals(jobs, controller.jobs()); assertEquals(installation, controller.installation());
            assertArrayEquals(original, Files.readAllBytes(input));
            assertTrue(controller.applyJobs(Map.of(UUID.randomUUID(), Optional.empty()),
                    new InstallSession(Set.of("minecraft", "forge", "legacybridge_runtime"), Set.of(), false)).isEmpty());
        }
    }

    @Test void relativeMissingAndOversizedBundleErrorsLeaveInspectionRetryableWithoutJar() throws Exception {
        source();
        try (var controller = inventoried()) {
            for (String bundle : List.of("relative", "x".repeat(4097), game.resolve("missing").toString())) {
                assertTrue(controller.inspectSelectedComposition(bundle)); ConversionQueueTest.await(controller);
                assertEquals(CompositionInspectionView.Status.FAILED, controller.compositionInspection().status());
                assertTrue(controller.compositionInspection().error().isPresent()); assertTrue(controller.jobs().isEmpty());
            }
            try (var files = Files.list(game.resolve("legacybridge/staging"))) { assertEquals(0, files.count()); }
            try (var files = Files.list(game.resolve("legacybridge/reports"))) { assertEquals(0, files.count()); }
            assertTrue(controller.refresh()); ConversionQueueTest.await(controller);
        }
    }

    @Test void duplicateInputCannotReachCompositionProof() throws Exception {
        Path source = source(); Files.copy(source, source.resolveSibling("duplicate.jar"));
        try (var controller = inventoried()) {
            assertTrue(controller.inspectSelectedComposition(game.resolve("unused").toString())); ConversionQueueTest.await(controller);
            assertEquals("INELIGIBLE_COMPOSITION_INPUT", controller.compositionInspection().error().orElseThrow().code());
            assertTrue(controller.jobs().isEmpty());
        }
    }

    @Test void evenConstructedSuccessfulPresentationCannotAuthorizePublicConversionOrInstallation() {
        var forged = new CompositionInspectionView(CompositionInspectionView.Status.PREVIEW_VALIDATED,
                Optional.empty(), Optional.empty(), List.of(), Optional.empty(), Optional.empty());
        assertFalse(forged.publicConversionAllowed()); assertFalse(forged.installationAllowed());
        assertEquals(4, forged.remainingGates().size());
        assertThrows(UnsupportedOperationException.class, () -> forged.remainingGates().clear());
    }

    private Path source() throws Exception {
        Path path = Files.createDirectories(game.resolve("legacybridge/input")).resolve("source.jar");
        ReceiptSourceFixture.write(path, "compose_product_fixture"); return path;
    }
    private ConversionController inventoried() throws Exception {
        var controller = new ConversionController(game); assertTrue(controller.refresh()); ConversionQueueTest.await(controller); return controller;
    }
}
