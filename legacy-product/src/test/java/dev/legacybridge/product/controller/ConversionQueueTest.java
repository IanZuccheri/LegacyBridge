package dev.legacybridge.product.controller;

import dev.legacybridge.converter.service.EngineOutcome;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import static org.junit.jupiter.api.Assertions.*;

class ConversionQueueTest {
    @TempDir Path game;

    @Test void mixedQueueRetainsIndividualFailureAndBlockedResultsWithoutInstallingAnything() throws Exception {
        Path input = Files.createDirectories(game.resolve("legacybridge/input"));
        Files.writeString(input.resolve("a-corrupt.jar"), "not a zip");
        fixture(input.resolve("b-valid.jar"), "queue_fixture", "1.16.5");
        System.clearProperty("legacybridge.queue.original.executed");
        try (var controller = inventoried()) {
            var rows = controller.snapshot().entries();
            assertEquals(InventoryKind.INVALID, ConversionController.classify(rows.get(0)));
            assertTrue(rows.get(0).fingerprint().isPresent(), "Corrupt regular files still show bounded size/hash evidence");
            assertEquals(InventoryKind.CANDIDATE, ConversionController.classify(rows.get(1)));
            assertTrue(controller.enqueue(List.of(0, 1), ""));
            await(controller);
            assertEquals(List.of(JobState.FAILED, JobState.BLOCKED), controller.jobs().stream().map(JobView::status).toList());
            assertTrue(controller.jobs().stream().allMatch(job -> job.artifact().isEmpty() && job.installed().isEmpty()));
            assertTrue(controller.selectJob(controller.jobs().get(0).id()));
            assertEquals("INVALID_SELECTION", controller.selectedJob().orElseThrow().error().orElseThrow().code());
            assertTrue(controller.selectJob(controller.jobs().get(1).id()));
            assertEquals("UNSUPPORTED", controller.selectedJob().orElseThrow().error().orElseThrow().code());
            assertTrue(controller.selectedJob().orElseThrow().conversionReport().isPresent());
            assertNull(System.getProperty("legacybridge.queue.original.executed"));
            assertFalse(Files.exists(game.resolve("mods")));
            assertEquals("not a zip", Files.readString(input.resolve("a-corrupt.jar")));
            assertTrue(controller.refresh(), "A failed job must leave inventory actions usable");
            await(controller);
        }
    }

    @Test void jobHistoryAndQueueAreBoundedAndClearingDoesNotDeleteReceipts() throws Exception {
        Path input = Files.createDirectories(game.resolve("legacybridge/input"));
        for (int i = 0; i < 33; i++) fixture(input.resolve("input-%02d.jar".formatted(i)), "queue_" + i, "1.16.5");
        try (var controller = inventoried()) {
            assertFalse(controller.enqueue(IntStream.range(0, 33).boxed().toList(), ""));
            assertTrue(controller.jobs().isEmpty());
            assertTrue(controller.enqueue(IntStream.range(0, 32).boxed().toList(), ""));
            assertFalse(controller.enqueue(List.of(32), ""));
            await(controller);
            assertEquals(32, controller.jobs().size());
            long receipts;
            try (var files = Files.walk(game.resolve("legacybridge/reports"))) { receipts = files.filter(Files::isRegularFile).count(); }
            assertEquals(32 * 5, receipts, "Every analyzed refusal retains analysis, conversion, ASM log, job receipt and saved-report index");
            for (JobView job : controller.jobs()) {
                assertEquals(JobState.BLOCKED, job.status());
                assertEquals("UNSUPPORTED", job.error().orElseThrow().code());
                assertTrue(Files.isRegularFile(job.compatibilityReport().orElseThrow()));
                assertTrue(Files.isRegularFile(job.conversionReport().orElseThrow()));
                assertTrue(Files.isRegularFile(job.transformationLog().orElseThrow()));
            }
            assertTrue(controller.clearFinished());
            assertTrue(controller.jobs().isEmpty());
            try (var files = Files.walk(game.resolve("legacybridge/reports"))) { assertEquals(receipts, files.filter(Files::isRegularFile).count()); }
            assertTrue(controller.enqueue(List.of(32), ""));
            await(controller);
        }
    }

    @Test void cancelQueuedJobIsDeterministicAndDoesNotPublishAnOutput() throws Exception {
        Path input = Files.createDirectories(game.resolve("legacybridge/input"));
        for (int i = 0; i < 32; i++) fixture(input.resolve("input-%02d.jar".formatted(i)), "cancel_" + i, "1.16.5");
        try (var controller = inventoried()) {
            assertTrue(controller.enqueue(IntStream.range(0, 32).boxed().toList(), ""));
            JobView last = controller.jobs().get(31);
            assertEquals(JobState.QUEUED, last.status());
            assertTrue(controller.cancelJob(last.id()));
            await(controller);
            assertEquals(JobState.CANCELLED, controller.jobs().get(31).status());
            assertTrue(controller.jobs().get(31).artifact().isEmpty());
            assertFalse(controller.cancelJob(last.id()));
        }
    }

    @Test void closingCancelsPendingJobsAndDoesNotWaitForFilesystemOrEngine() throws Exception {
        Path input = Files.createDirectories(game.resolve("legacybridge/input"));
        for (int i = 0; i < 32; i++) fixture(input.resolve("input-%02d.jar".formatted(i)), "close_" + i, "1.16.5");
        var controller = inventoried();
        assertTrue(controller.enqueue(IntStream.range(0, 32).boxed().toList(), ""));
        assertTimeout(Duration.ofSeconds(1), controller::close);
        await(controller);
        assertTrue(controller.jobs().stream().noneMatch(job -> job.status() == JobState.QUEUED));
        assertTrue(controller.jobs().stream().anyMatch(job -> job.status() == JobState.CANCELLED));
        assertFalse(controller.enqueue(List.of(0), ""));
        assertFalse(Files.exists(game.resolve("mods")));
    }

    @Test void filenameIsNotVersionEvidenceAndDuplicateIdsAreExplicit() throws Exception {
        Path input = Files.createDirectories(game.resolve("legacybridge/input"));
        fixture(input.resolve("a-forge-1.16.5.jar"), "target_fixture", "1.20.1");
        fixture(input.resolve("b.jar"), "same_fixture", "1.16.5");
        fixture(input.resolve("c.jar"), "same_fixture", "1.16.5");
        try (var controller = inventoried()) {
            assertEquals(List.of(InventoryKind.INCOMPATIBLE, InventoryKind.DUPLICATE, InventoryKind.DUPLICATE),
                    controller.snapshot().entries().stream().map(ConversionController::classify).toList());
        }
    }

    @Test void changedObservedInputFailsBeforeConversionAndRemainsAnalyzableAfterRefresh() throws Exception {
        Path input = fixture(Files.createDirectories(game.resolve("legacybridge/input")).resolve("input.jar"), "queue_fixture", "1.16.5");
        try (var controller = inventoried()) {
            Files.delete(input);
            fixture(input, "other_fixture", "1.16.5");
            assertTrue(controller.enqueue(List.of(0), game.toString()));
            await(controller);
            assertEquals(JobState.FAILED, controller.jobs().get(0).status());
            assertEquals("INPUT_CHANGED", controller.jobs().get(0).error().orElseThrow().code());
            assertTrue(controller.refresh()); await(controller);
            assertTrue(controller.analyzeSelected()); await(controller);
            assertEquals(JobState.ANALYZED, controller.jobs().get(1).status());
        }
    }

    @Test void pathActionsValidateOnWorkerAndNeverOpenAJarOrLoadItsInitializer() throws Exception {
        fixture(Files.createDirectories(game.resolve("legacybridge/input")).resolve("input.jar"), "queue_fixture", "1.16.5");
        System.clearProperty("legacybridge.queue.original.executed");
        try (var controller = inventoried()) {
            AtomicReference<String> worker = new AtomicReference<>();
            CompletableFuture<EngineOutcome<Path>> opened = new CompletableFuture<>();
            assertTrue(controller.openInput(path -> { assertEquals(game.resolve("legacybridge/input"), path); worker.set(Thread.currentThread().getName()); }, opened::complete));
            assertEquals(EngineOutcome.Status.COMPLETED, opened.get(5, TimeUnit.SECONDS).status());
            assertEquals("legacybridge-path-action", worker.get());
            assertTrue(controller.analyzeSelected()); await(controller);
            JobView result = controller.jobs().get(0);
            assertEquals(JobState.ANALYZED, result.status());
            assertTrue(result.compatibilityReport().isPresent());
            assertFalse(controller.applyJob(result.id(), Set.of("forge", "minecraft", "legacybridge_runtime")));
            CompletableFuture<EngineOutcome<Path>> report = new CompletableFuture<>();
            assertTrue(controller.openJobPath(result.id(), JobPathKind.COMPATIBILITY_REPORT,
                    path -> assertTrue(path.toString().endsWith("analysis.json")), report::complete));
            assertEquals(EngineOutcome.Status.COMPLETED, report.get(5, TimeUnit.SECONDS).status());
            assertNull(System.getProperty("legacybridge.queue.original.executed"));
        }
    }

    @Test void globalRefreshErrorsRemainVisibleWithoutContaminatingAnOlderJob() throws Exception {
        Path original = fixture(Files.createDirectories(game.resolve("legacybridge/input")).resolve("input.jar"), "queue_fixture", "1.16.5");
        byte[] originalBytes = Files.readAllBytes(original);
        try (var controller = inventoried()) {
            assertTrue(controller.analyzeSelected()); await(controller);
            JobView analyzed = controller.selectedJob().orElseThrow();
            assertTrue(controller.enqueue(List.of(0), "")); await(controller);
            assertTrue(controller.globalError().isEmpty(), "A blocked job is not a global screen error");
            assertTrue(controller.selectJob(analyzed.id()));
            assertTrue(controller.selectedJob().orElseThrow().error().isEmpty());
            Path cache = game.resolve("legacybridge/cache");
            Path saved = game.resolve("legacybridge/cache-test-saved");
            Files.move(cache, saved);
            Files.writeString(cache, "intentional test layout collision", StandardOpenOption.CREATE_NEW);
            assertTrue(controller.refresh()); await(controller);
            assertTrue(controller.globalError().isPresent());
            assertEquals(analyzed.id(), controller.selectedJob().orElseThrow().id());
            assertEquals(JobState.ANALYZED, controller.selectedJob().orElseThrow().status());
            assertTrue(controller.selectedJob().orElseThrow().error().isEmpty());
            Files.delete(cache); // This test's exact collision file, never a recursive/user path.
            Files.move(saved, cache);
            assertTrue(controller.refresh()); await(controller);
            assertTrue(controller.globalError().isEmpty(), "Successful retry clears the global diagnostic");
            assertArrayEquals(originalBytes, Files.readAllBytes(original));
        }
    }

    private ConversionController inventoried() throws Exception {
        var controller = new ConversionController(game);
        assertTrue(controller.refresh()); await(controller); return controller;
    }

    static void await(ConversionController controller) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (controller.snapshot().busy() && System.nanoTime() < deadline) Thread.sleep(5);
        assertFalse(controller.snapshot().busy(), "Queue exceeded test deadline");
    }

    static Path fixture(Path path, String id, String version) throws Exception {
        boolean old = version.equals("1.16.5");
        String metadata = """
                modLoader="javafml"
                loaderVersion="[%s,)"
                license="MIT"
                [[mods]]
                modId="%s"
                version="1.0.0"
                displayName="Queue fixture"
                [[dependencies.%s]]
                modId="forge"
                mandatory=true
                versionRange="[%s,)"
                ordering="NONE"
                side="BOTH"
                [[dependencies.%s]]
                modId="minecraft"
                mandatory=true
                versionRange="[%s]"
                ordering="NONE"
                side="BOTH"
                """.formatted(old ? "36" : "47", id, id, old ? "36" : "47", id, version);
        ClassWriter writer = new ClassWriter(0);
        writer.visit(old ? Opcodes.V1_8 : Opcodes.V17, Opcodes.ACC_PUBLIC, "fixture/QueueFixture", null, "java/lang/Object", null);
        var annotation = writer.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true);
        annotation.visit("value", id); annotation.visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "location", old ? "Lnet/minecraft/util/ResourceLocation;" : "Lnet/minecraft/resources/ResourceLocation;", null, null).visitEnd();
        var method = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        method.visitCode(); method.visitLdcInsn("legacybridge.queue.original.executed"); method.visitLdcInsn("true");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        method.visitInsn(Opcodes.POP); method.visitInsn(Opcodes.RETURN); method.visitMaxs(2, 0); method.visitEnd(); writer.visitEnd();
        try (var zip = new ZipOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            zip.putNextEntry(new ZipEntry("META-INF/mods.toml")); zip.write(metadata.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("fixture/QueueFixture.class")); zip.write(writer.toByteArray()); zip.closeEntry();
        }
        return path;
    }
}
