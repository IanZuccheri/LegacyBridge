package dev.legacybridge.converter.service;

import dev.legacybridge.converter.application.AnalysisService;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationLimits;
import dev.legacybridge.converter.operation.OperationStage;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.testutil.SyntheticForgeJarFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LegacyEngineServiceTest {
    @TempDir Path directory;

    @Test void analysisMatchesExistingEngineReportWithoutAConsoleProtocol() throws Exception {
        Path input = SyntheticForgeJarFactory.create(directory.resolve("mod.jar"));
        CanonicalReportWriter writer = new CanonicalReportWriter();
        String expected = writer.toJson(new AnalysisService().analyze(input));
        try (var service = new LegacyEngineService()) {
            var job = service.analyze(input);
            var outcome = await(job);
            assertEquals(EngineOutcome.Status.COMPLETED, outcome.status());
            assertEquals(expected, writer.toJson(outcome.value().orElseThrow()));
            assertEquals(OperationStage.COMPLETED, job.latestProgress().orElseThrow().stage());
            assertFalse(job.cancel(), "a completed result cannot later be reported cancelled");
        }
    }

    @Test void inventoryReportsDuplicatesAndInvalidJarsWithoutExecutingThem() throws Exception {
        SyntheticForgeJarFactory.create(directory.resolve("a.jar"));
        SyntheticForgeJarFactory.create(directory.resolve("b.jar"));
        Files.writeString(directory.resolve("broken.jar"), "not a ZIP");
        try (var service = new LegacyEngineService()) {
            var result = await(service.inventory(directory)).value().orElseThrow();
            assertEquals(3, result.entries().size());
            assertTrue(result.entries().get(0).duplicateModId());
            assertTrue(result.entries().get(1).duplicateModId());
            assertTrue(result.entries().get(2).error().isPresent());
            assertFalse(result.truncated());
        }
        assertEquals("not a ZIP", Files.readString(directory.resolve("broken.jar")));
    }

    @Test void inventoryEnumerationAndRetentionAreExplicitlyBounded() throws Exception {
        SyntheticForgeJarFactory.create(directory.resolve("a.jar"));
        SyntheticForgeJarFactory.create(directory.resolve("b.jar"));
        try (var service = new LegacyEngineService(new EngineOptions(1, 1, OperationLimits.defaults()))) {
            var result = await(service.inventory(directory)).value().orElseThrow();
            assertEquals(1, result.entries().size());
            assertTrue(result.truncated());
        }
    }

    @Test void missingMappingsDoNotImplicitlyProbeTheDevelopmentCache() throws Exception {
        Path input = SyntheticForgeJarFactory.create(directory.resolve("mod.jar"));
        Path output = directory.resolve("output.jar");
        try (var service = new LegacyEngineService()) {
            var outcome = await(service.convert(new ConversionRequest(input, output, MappingData.unavailable())));
            assertEquals("MAPPING_DATA_REQUIRED", outcome.error().orElseThrow().code());
            assertFalse(Files.exists(output));
            assertEquals("MAPPING_DATA_REQUIRED", await(service.mappings(input, MappingData.unavailable()))
                    .error().orElseThrow().code());
        }
    }

    @Test void actualReadBudgetProducesStructuredFailureAndNoResult() throws Exception {
        Path input = SyntheticForgeJarFactory.create(directory.resolve("mod.jar"));
        OperationLimits tiny = new OperationLimits(1, 1024, 1024, Duration.ofSeconds(10));
        try (var service = new LegacyEngineService(new EngineOptions(1, 1, tiny))) {
            var outcome = await(service.analyze(input));
            assertEquals(EngineOutcome.Status.FAILED, outcome.status());
            assertEquals("INPUT_LIMIT", outcome.error().orElseThrow().code());
            assertTrue(outcome.value().isEmpty());
        }
        assertTrue(Files.isRegularFile(input));
    }

    @Test void includedContractCannotAuthorizeOtherProfilesOrBroadMappings() throws Exception {
        Path input = SyntheticForgeJarFactory.create(directory.resolve("mod.jar"));
        Path output = directory.resolve("output.jar");
        try (var service = new LegacyEngineService()) {
            for (var profile : ConversionProfile.values()) {
                if (profile == ConversionProfile.SIMPLE_CONTENT_V1) continue;
                var request = new ConversionRequest(input, output, MappingData.builtinSimpleContent(), java.util.Optional.empty(), profile);
                assertEquals("MAPPING_SCOPE_UNSUPPORTED", await(service.convert(request)).error().orElseThrow().code());
                assertEquals("MAPPING_SCOPE_UNSUPPORTED", await(service.plan(request)).error().orElseThrow().code());
            }
            assertEquals("MAPPING_SCOPE_UNSUPPORTED", await(service.mappings(input, MappingData.builtinSimpleContent()))
                    .error().orElseThrow().code());
        }
        assertFalse(Files.exists(output));
    }

    @Test void workNeverRunsOnTheSubmittingThreadAndOnlyOneWorkerRunsAtOnce() throws Exception {
        String caller = Thread.currentThread().getName();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        try (var service = new LegacyEngineService()) {
            var first = service.submit(() -> {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                try { return EngineOutcome.completed(Thread.currentThread().getName()); }
                finally { active.decrementAndGet(); }
            });
            var second = service.submit(() -> {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                try { return EngineOutcome.completed(Thread.currentThread().getName()); }
                finally { active.decrementAndGet(); }
            });
            assertNotEquals(caller, await(first).value().orElseThrow());
            assertNotEquals(caller, await(second).value().orElseThrow());
            assertEquals(1, maximum.get());
        }
    }

    @Test void boundedQueueRejectsAndCancelledQueuedJobsReleaseTheirSlot() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger queuedExecutions = new AtomicInteger();
        try (var service = new LegacyEngineService(new EngineOptions(1, 1, OperationLimits.defaults()))) {
            var running = service.submit(() -> {
                started.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                return EngineOutcome.completed("running");
            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            var queued = service.submit(() -> { queuedExecutions.incrementAndGet(); return EngineOutcome.completed("queued"); });
            var refused = service.submit(() -> EngineOutcome.completed("overflow"));
            assertEquals("QUEUE_FULL", await(refused).error().orElseThrow().code());
            assertTrue(queued.cancel());
            assertEquals(EngineOutcome.Status.CANCELLED, await(queued).status());
            var replacement = service.submit(() -> EngineOutcome.completed("replacement"));
            release.countDown();
            assertEquals("running", await(running).value().orElseThrow());
            assertEquals("replacement", await(replacement).value().orElseThrow());
            assertEquals(0, queuedExecutions.get());
        } finally { release.countDown(); }
    }

    @Test void cancellationWinsBeforePublicationAndDoesNotLeakToTheNextJob() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var service = new LegacyEngineService()) {
            var running = service.submit(() -> {
                started.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                OperationControl.checkpoint();
                return EngineOutcome.completed("must not publish");
            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            assertTrue(running.cancel());
            release.countDown();
            assertEquals(EngineOutcome.Status.CANCELLED, await(running).status());
            assertEquals("next", await(service.submit(() -> EngineOutcome.completed("next"))).value().orElseThrow());
        } finally { release.countDown(); }
    }

    @Test void publicationBarrierRejectsCancellationInsteadOfLyingAboutACommittedJob() throws Exception {
        CountDownLatch publishing = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var service = new LegacyEngineService()) {
            var job = service.submit(() -> {
                OperationControl.beginPublication();
                publishing.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                return EngineOutcome.completed("committed");
            });
            assertTrue(publishing.await(10, TimeUnit.SECONDS));
            assertFalse(job.cancel());
            release.countDown();
            assertEquals(EngineOutcome.Status.COMPLETED, await(job).status());
        } finally { release.countDown(); }
    }

    @Test void closeCancelsQueuedAndActiveWorkWithoutWaitingOnTheCaller() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        var service = new LegacyEngineService();
        try {
            var running = service.submit(() -> {
                started.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                return EngineOutcome.completed("unpublished");
            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            var queued = service.submit(() -> EngineOutcome.completed("queued"));
            service.close();
            assertEquals(1, release.getCount(), "close must not wait for the blocked worker");
            assertEquals(EngineOutcome.Status.CANCELLED, await(queued).status());
            assertEquals("SERVICE_CLOSED", await(service.submit(() -> EngineOutcome.completed("late"))).error().orElseThrow().code());
            release.countDown();
            assertEquals(EngineOutcome.Status.CANCELLED, await(running).status());
        } finally { release.countDown(); service.close(); }
    }

    @Test void publicContractsRejectContradictoryRequestsAndSanitizeDiagnostics() {
        assertThrows(IllegalArgumentException.class, () -> new MappingPreparationRequest(directory, java.util.Optional.empty(), false));
        assertThrows(IllegalArgumentException.class, () -> new MappingPreparationRequest(directory, java.util.Optional.of(directory), true));
        assertThrows(IllegalArgumentException.class, () -> new EngineOptions(0, 1, OperationLimits.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new EngineOptions(1, 257, OperationLimits.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new EngineError("bad\ncode", "text", "text"));
        EngineError error = new EngineError("TEST_ERROR", "line\n\u202Econtrol", "x".repeat(1000));
        assertFalse(error.message().contains("\n"));
        assertFalse(error.message().contains("\u202E"));
        assertEquals(512, error.remediation().length());
        assertThrows(IllegalArgumentException.class, () -> new ConversionRequest(directory, directory,
                MappingData.unavailable(), java.util.Optional.of("not-a-hash")));
    }

    private static <T> EngineOutcome<T> await(EngineJob<T> job) throws Exception {
        return job.completion().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }
}
