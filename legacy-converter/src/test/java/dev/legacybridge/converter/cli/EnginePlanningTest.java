package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.service.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EnginePlanningTest {
    @TempDir Path directory;

    @Test void planIsNonPublishingAndConversionRevalidatesTheSameCandidate() throws Exception {
        Path input = CliEngineFixture.input(directory.resolve("source.jar"), "valid");
        Path profile = CliEngineFixture.profile(directory);
        Path output = directory.resolve("converted.jar");
        try (var engine = new LegacyEngineService()) {
            var request = new ConversionRequest(input, output, MappingData.profile(profile));
            var planned = engine.plan(request).completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, planned.status(), planned.toString());
            var plan = planned.value().orElseThrow();
            assertTrue(plan.convertible());
            assertFalse(Files.exists(output));
            assertEquals("LEGACY_CONVERSION_PLAN", plan.report().get("reportType"));
            assertEquals(1, plan.report().get("schemaVersion"));
            assertEquals("PLANNED", plan.report().get("status"));
            assertNull(plan.report().get("output"));
            assertEquals(false, plan.report().get("transformationsCommitted"));
            assertThrows(UnsupportedOperationException.class, () -> plan.report().put("status", "CONVERTED"));
            var converted = engine.convert(new ConversionRequest(input, output, MappingData.profile(profile),
                    Optional.of(plan.inputSha256()))).completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, converted.status(), converted.toString());
            Map<?, ?> evidence = (Map<?, ?>) converted.value().orElseThrow().report().get("output");
            assertEquals(plan.candidateSha256().orElseThrow(), evidence.get("sha256"));
        }
    }

    @Test void unsupportedPlanAndChangedSelectionPublishNothing() throws Exception {
        Path input = CliEngineFixture.input(directory.resolve("source.jar"), "reflection");
        Path profile = CliEngineFixture.profile(directory);
        Path output = directory.resolve("converted.jar");
        try (var engine = new LegacyEngineService()) {
            var planned = engine.plan(new ConversionRequest(input, output, MappingData.profile(profile)))
                    .completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.BLOCKED, planned.status());
            assertEquals("UNSUPPORTED", planned.error().orElseThrow().code());
            assertEquals("ABORTED", planned.value().orElseThrow().report().get("status"));
            assertTrue(planned.value().orElseThrow().candidateSha256().isEmpty());
            Path valid = CliEngineFixture.input(directory.resolve("valid.jar"), "valid");
            var changed = engine.convert(new ConversionRequest(valid, output, MappingData.profile(profile),
                    Optional.of("0".repeat(64)))).completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals("INPUT_CHANGED", changed.error().orElseThrow().code());
            assertFalse(Files.exists(output));
        }
    }
}
