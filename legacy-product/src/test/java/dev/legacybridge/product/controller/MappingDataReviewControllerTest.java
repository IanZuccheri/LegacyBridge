package dev.legacybridge.product.controller;

import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MappingDataReviewControllerTest {
    @TempDir Path game;
    @Test void badPathsLeaveReviewRetryableWithStructuredErrorAndNoJobsOrWorkspace() throws Exception {
        try (var controller = new ConversionController(game)) {
            for (String value : List.of("", "relative", "a".repeat(4097), game.resolve("missing").toString())) {
                assertTrue(controller.reviewMappingData(value)); await(controller);
                assertEquals(MappingDataView.Status.FAILED, controller.mappingDataReview().status());
                assertTrue(controller.mappingDataReview().review().isEmpty());
                assertTrue(controller.mappingDataReview().error().isPresent()); assertTrue(controller.jobs().isEmpty());
            }
            assertFalse(Files.exists(game.resolve("legacybridge"))); assertFalse(Files.exists(game.resolve("mods")));
            controller.close(); assertFalse(controller.reviewMappingData(game.toString()));
        }
    }
    static void await(ConversionController controller) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (controller.snapshot().busy() && System.nanoTime() < deadline) Thread.sleep(10);
        assertFalse(controller.snapshot().busy(), "Data review deadline exceeded");
    }
}
