package dev.legacybridge.product.controller;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "legacybridge.productCompositionBundle", matches = ".+")
class MappingDataReviewEnvironmentTest {
    @TempDir Path game;
    private Path copyBundle() throws Exception {
        Path copy = Files.createDirectory(game.resolve("data"));
        try (var files = Files.list(Path.of(System.getProperty("legacybridge.productCompositionBundle")))) {
            for (Path file : files.toList()) Files.copy(file, copy.resolve(file.getFileName()));
        }
        return copy;
    }
    @Test void fullReviewPinsSubsequentPreviewAndExplicitRereviewRestoresUsability() throws Exception {
        Path bundle = copyBundle();
        Path input = Files.createDirectories(game.resolve("legacybridge/input")).resolve("composed.jar");
        Files.copy(Path.of(System.getProperty("legacybridge.productCompositionFixture")), input);
        byte[] original = Files.readAllBytes(input);
        try (var controller = new ConversionController(game)) {
            assertTrue(controller.refresh()); MappingDataReviewControllerTest.await(controller);
            assertTrue(controller.reviewMappingData(bundle.toString())); MappingDataReviewControllerTest.await(controller);
            assertEquals(MappingDataView.Status.REVIEWED, controller.mappingDataReview().status(), controller.snapshot().error().toString());
            var old = controller.mappingDataReview().review().orElseThrow(); assertTrue(old.complete());
            assertFalse(old.selection().expectedManifestSha256().isEmpty());
            Files.writeString(bundle.resolve("manifest.json"), "\n", StandardOpenOption.APPEND);
            assertTrue(controller.inspectSelectedComposition(bundle.toString())); MappingDataReviewControllerTest.await(controller);
            assertEquals("MAPPING_DATA_CHANGED", controller.compositionInspection().error().orElseThrow().code());
            assertTrue(controller.compositionInspection().report().isEmpty());
            // A failed review of another path must not erase a previously bound fingerprint.
            assertTrue(controller.reviewMappingData(game.resolve("missing").toString())); MappingDataReviewControllerTest.await(controller);
            assertTrue(controller.inspectSelectedComposition(bundle.toString())); MappingDataReviewControllerTest.await(controller);
            assertEquals("MAPPING_DATA_CHANGED", controller.compositionInspection().error().orElseThrow().code());
            assertTrue(controller.reviewMappingData(bundle.toString())); MappingDataReviewControllerTest.await(controller);
            assertEquals(MappingDataView.Status.REVIEWED, controller.mappingDataReview().status(), controller.snapshot().error().toString());
            assertNotEquals(old.selection().expectedManifestSha256(), controller.mappingDataReview().review().orElseThrow().selection().expectedManifestSha256());
            assertTrue(controller.inspectSelectedComposition(bundle.toString())); MappingDataReviewControllerTest.await(controller);
            assertEquals(CompositionInspectionView.Status.PREVIEW_VALIDATED, controller.compositionInspection().status(), controller.compositionInspection().error().toString());
            assertTrue(controller.jobs().isEmpty()); assertFalse(Files.exists(game.resolve("mods")));
            try (var files = Files.list(game.resolve("legacybridge/staging"))) { assertEquals(0, files.count()); }
        }
        assertArrayEquals(original, Files.readAllBytes(input));
    }
    @Test void cancellationAndCloseNeverLeaveAReusablePartialReview() throws Exception {
        Path bundle = copyBundle();
        try (var controller = new ConversionController(game)) {
            assertTrue(controller.reviewMappingData(bundle.toString()));
            assertTrue(controller.cancel(), "Actual cancellation must win before publication"); controller.close();
            MappingDataReviewControllerTest.await(controller);
            assertEquals(MappingDataView.Status.CANCELLED, controller.mappingDataReview().status());
            assertTrue(controller.mappingDataReview().review().isEmpty()); assertTrue(controller.jobs().isEmpty());
        }
    }
}
