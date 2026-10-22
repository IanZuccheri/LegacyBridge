package dev.legacybridge.product.controller;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual original fixture, full explicit local data and shared engine. No Minecraft execution. */
@EnabledIfSystemProperty(named = "legacybridge.productCompositionBundle", matches = ".+")
class CompositionInspectionEnvironmentTest {
    @TempDir Path game;
    private Path bundle() { return Path.of(System.getProperty("legacybridge.productCompositionBundle")); }
    private Path source() throws Exception {
        Path input = Files.createDirectories(game.resolve("legacybridge/input")).resolve("composed.jar");
        Files.copy(Path.of(System.getProperty("legacybridge.productCompositionFixture")), input); return input;
    }
    @Test void productPreviewEqualsSharedEngineAndKeepsSimpleConversionAdmissionClosed() throws Exception {
        Path input = source(); byte[] original = Files.readAllBytes(input);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
        Object recordedEngineReport;
        try (var controller = new ConversionController(game)) {
            assertTrue(controller.refresh()); await(controller);
            assertTrue(controller.inspectSelectedComposition(bundle().toString())); await(controller);
            var view = controller.compositionInspection();
            assertEquals(CompositionInspectionView.Status.PREVIEW_VALIDATED, view.status(), view.error().toString());
            assertEquals(List.of("logic.owned-bodies", "data.recipe-resources"), view.operations().stream().map(operation -> operation.id()).toList());
            assertEquals(sha, view.inputSha256().orElseThrow()); assertTrue(controller.jobs().isEmpty());
            Map<?, ?> report = (Map<?, ?>) BoundedJsonParser.parse(Files.readString(view.report().orElseThrow()));
            assertEquals("PRODUCT_COMPOSITION_INSPECTION", report.get("reportType"));
            assertEquals(false, report.get("installationAllowed")); assertEquals(false, report.get("publicConversionAllowed"));
            recordedEngineReport = report.get("engineReport");
            assertTrue(controller.enqueue(List.of(0), "")); await(controller);
            assertEquals(JobState.BLOCKED, controller.jobs().get(0).status());
            assertFalse(Files.exists(game.resolve("mods")));
            try (var files = Files.list(game.resolve("legacybridge/staging"))) { assertEquals(0, files.count()); }
        }
        try (var service = new LegacyEngineService()) {
            var request = new ConversionRequest(input, game.resolve("never-published.jar"), MappingData.bundle(bundle()),
                    Optional.of(sha), ConversionProfile.LOGIC_PRESERVING_V1);
            var result = service.previewLogicWithRecipes(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, result.status(), result.toString());
            assertEquals(recordedEngineReport, BoundedJsonParser.parse(new CanonicalReportWriter().toJsonTree(result.value().orElseThrow().report())));
            assertFalse(Files.exists(request.output()));
        }
        assertArrayEquals(original, Files.readAllBytes(input));
        System.out.println("PRODUCT_COMPOSITION_STATIC_OK sourceSha256=" + sha + " conversionAllowed=false installationAllowed=false native=NOT_RUN");
    }

    @Test void changedOriginalCannotInheritInventoryHashOrPublishReport() throws Exception {
        Path input = source();
        try (var controller = new ConversionController(game)) {
            assertTrue(controller.refresh()); await(controller);
            // Valid inert replacement, not executing an initializer or mutating the real fixture.
            Path replacement = ReceiptSourceFixture.write(game.resolve("replacement.jar"), "different_fixture");
            Files.write(input, Files.readAllBytes(replacement));
            assertTrue(controller.inspectSelectedComposition(bundle().toString())); await(controller);
            assertEquals(CompositionInspectionView.Status.FAILED, controller.compositionInspection().status());
            assertEquals("INPUT_CHANGED", controller.compositionInspection().error().orElseThrow().code());
            assertTrue(controller.compositionInspection().report().isEmpty()); assertTrue(controller.jobs().isEmpty());
        }
    }

    @Test void acceptedCancellationOrCloseCannotPublishIncompletePreview() throws Exception {
        source();
        try (var controller = new ConversionController(game)) {
            assertTrue(controller.refresh()); await(controller);
            assertTrue(controller.inspectSelectedComposition(bundle().toString()));
            // Assert the actual race outcome; a lost request is not a simulated cancellation PASS.
            assertTrue(controller.cancel(), "Cancellation did not win: do not call a completed proof cancelled");
            assertTimeoutPreemptively(Duration.ofSeconds(1), controller::close);
            await(controller);
            assertEquals(CompositionInspectionView.Status.CANCELLED, controller.compositionInspection().status());
            assertTrue(controller.compositionInspection().report().isEmpty()); assertTrue(controller.jobs().isEmpty());
            try (var files = Files.list(game.resolve("legacybridge/reports"))) { assertEquals(0, files.count()); }
            assertFalse(controller.inspectSelectedComposition(bundle().toString()));
        }
    }

    @Test void unsupportedBodyProducesBlockedReportNotInstallableJob() throws Exception {
        Path input = Files.createDirectories(game.resolve("legacybridge/input")).resolve("unsupported-body.jar");
        ReceiptSourceFixture.write(input, "not_the_owned_body_contract");
        try (var controller = new ConversionController(game)) {
            assertTrue(controller.refresh()); await(controller);
            assertTrue(controller.inspectSelectedComposition(bundle().toString())); await(controller);
            var view = controller.compositionInspection();
            assertEquals(CompositionInspectionView.Status.BLOCKED, view.status(), view.error().toString());
            assertTrue(view.operations().isEmpty()); assertTrue(view.error().isPresent());
            assertTrue(view.remainingGates().contains(CompositionInspectionView.Gate.STATIC_PROOF_REQUIRED));
            assertTrue(Files.isRegularFile(view.report().orElseThrow())); assertTrue(controller.jobs().isEmpty());
            try (var files = Files.list(game.resolve("legacybridge/staging"))) { assertEquals(0, files.count()); }
        }
    }
    private static void await(ConversionController controller) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (controller.snapshot().busy() && System.nanoTime() < deadline) Thread.sleep(10);
        assertFalse(controller.snapshot().busy(), "Inspection timed out");
    }
}
