package dev.legacybridge.product.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import static org.junit.jupiter.api.Assertions.*;

class ConversionControllerTest {
    @TempDir Path game;

    @Test void instanceDirectoryMustBeExplicitAndAbsolute() {
        assertThrows(IllegalArgumentException.class, () -> new ConversionController(Path.of("relative-instance")));
    }

    @Test void constructionIsReadOnlyAndRefreshCreatesOnlyReservedWorkspace() throws Exception {
        Path mods = Files.createDirectory(game.resolve("mods"));
        Path untouched = Files.writeString(mods.resolve("unrelated.txt"), "keep");
        try (var controller = new ConversionController(game)) {
            assertFalse(Files.exists(game.resolve("legacybridge")));
            assertTrue(controller.refresh());
            assertEquals(ConversionController.Phase.READY, await(controller).phase());
            for (String area : new String[]{"input", "staging", "reports", "cache", "transactions"})
                assertTrue(Files.isDirectory(game.resolve("legacybridge").resolve(area)));
            assertEquals("keep", Files.readString(untouched));
            assertTrue(controller.snapshot().entries().isEmpty());
        }
    }

    @Test void inventoryObservesInputOnlyAndSelectionCannotInjectPaths() throws Exception {
        Path input = Files.createDirectories(game.resolve("legacybridge/input"));
        fixture(input.resolve("selected.jar"), "1.0");
        fixture(Files.createDirectory(game.resolve("mods")).resolve("not-input.jar"), "2.0");
        fixture(game.resolve("also-not-input.jar"), "3.0");
        try (var controller = new ConversionController(game)) {
            assertFalse(controller.select(0));
            assertTrue(controller.refresh());
            var view = await(controller);
            assertEquals(1, view.entries().size());
            assertEquals("selected.jar", view.selection().orElseThrow().path().getFileName().toString());
            assertFalse(controller.select(-1));
            assertFalse(controller.select(1));
            assertTrue(controller.select(0));
        }
    }

    @Test void analysisUsesTypedEngineAndNeverExecutesOriginalInitializer() throws Exception {
        Path input = fixture(Files.createDirectories(game.resolve("legacybridge/input")).resolve("original.jar"), "1.0");
        String original = sha256(input);
        System.clearProperty("legacybridge.product.fixture.executed");
        try (var controller = inventoried()) {
            assertTrue(controller.analyzeSelected());
            var view = await(controller);
            assertEquals(ConversionController.Phase.RESULT, view.phase(), view.message());
            assertFalse(controller.canCancel());
            assertTrue(view.artifact().isEmpty());
            String json = Files.readString(view.report().orElseThrow());
            assertTrue(json.contains("\"schemaVersion\": 2"));
            assertTrue(json.contains("\"modId\": \"controller_fixture\""));
            assertEquals(original, sha256(input));
            assertNull(System.getProperty("legacybridge.product.fixture.executed"));
            assertFalse(Files.exists(game.resolve("mods")));
        }
    }

    @Test void repeatedAnalysisUsesNewOwnedReportDirectoriesWithoutOverwrite() throws Exception {
        fixture(Files.createDirectories(game.resolve("legacybridge/input")).resolve("original.jar"), "1.0");
        try (var controller = inventoried()) {
            controller.analyzeSelected();
            Path first = await(controller).report().orElseThrow();
            String original = Files.readString(first);
            controller.analyzeSelected();
            Path second = await(controller).report().orElseThrow();
            assertNotEquals(first, second);
            assertEquals(original, Files.readString(first));
            assertEquals(original, Files.readString(second));
        }
    }

    @Test void changingSelectionDetachesOldResultWithoutDeletingOrRestoringItsFiles() throws Exception {
        Path input = Files.createDirectories(game.resolve("legacybridge/input"));
        Path firstInput = fixture(input.resolve("a-first.jar"), "1.0");
        Path secondInput = fixture(input.resolve("b-second.jar"), "2.0");
        try (var controller = inventoried()) {
            assertEquals(firstInput, controller.snapshot().selection().orElseThrow().path());
            assertTrue(controller.analyzeSelected());
            var firstResult = await(controller);
            assertEquals(ConversionController.Phase.RESULT, firstResult.phase());
            Path firstReport = firstResult.report().orElseThrow();
            String reportBytes = Files.readString(firstReport);

            assertTrue(controller.select(0));
            assertEquals(firstResult, controller.snapshot(), "Reselecting the same row must preserve its result");

            assertTrue(controller.select(1));
            var secondSelection = controller.snapshot();
            assertEquals(secondInput, secondSelection.selection().orElseThrow().path());
            assertEquals(ConversionController.Phase.READY, secondSelection.phase());
            assertTrue(secondSelection.error().isEmpty());
            assertTrue(secondSelection.artifact().isEmpty());
            assertTrue(secondSelection.report().isEmpty(), "Copy paths must not expose another candidate's report");
            assertTrue(secondSelection.progress().isEmpty());
            assertFalse(secondSelection.busy());
            assertEquals(reportBytes, Files.readString(firstReport), "Selection must not delete the earlier result");

            assertTrue(controller.select(0));
            var firstAgain = controller.snapshot();
            assertEquals(firstInput, firstAgain.selection().orElseThrow().path());
            assertEquals(ConversionController.Phase.READY, firstAgain.phase());
            assertTrue(firstAgain.error().isEmpty());
            assertTrue(firstAgain.artifact().isEmpty());
            assertTrue(firstAgain.report().isEmpty(), "Returning to a row must not restore stale result state");
            assertTrue(firstAgain.progress().isEmpty());
            assertEquals(reportBytes, Files.readString(firstReport));
        }
    }

    @Test void changingSelectionClearsPreviousCandidateErrorWhileSameRowRetainsIt() throws Exception {
        Path input = Files.createDirectories(game.resolve("legacybridge/input"));
        fixture(input.resolve("a-first.jar"), "1.0");
        fixture(input.resolve("b-second.jar"), "2.0");
        try (var controller = inventoried()) {
            assertTrue(controller.convertSelected("relative-bundle"));
            var failure = await(controller);
            assertEquals("INVALID_MAPPING_PATH", failure.error().orElseThrow().code());
            assertTrue(controller.select(0));
            assertEquals(failure, controller.snapshot());
            assertTrue(controller.select(1));
            var next = controller.snapshot();
            assertEquals(ConversionController.Phase.READY, next.phase());
            assertTrue(next.error().isEmpty());
            assertTrue(next.artifact().isEmpty());
            assertTrue(next.report().isEmpty());
            assertTrue(next.progress().isEmpty());
            assertFalse(controller.canCancel());
        }
    }

    @Test void changedSelectedInputRequiresFreshInventory() throws Exception {
        Path input = fixture(Files.createDirectories(game.resolve("legacybridge/input")).resolve("original.jar"), "1.0");
        try (var controller = inventoried()) {
            Files.delete(input);
            fixture(input, "2.0");
            controller.analyzeSelected();
            var view = await(controller);
            assertEquals("INPUT_CHANGED", view.error().orElseThrow().code());
            assertTrue(view.report().isEmpty());
            assertTrue(view.artifact().isEmpty());
        }
    }

    @Test void blankUsesIncludedContractAndRejectsUnimplementedBehaviorWithoutExecutingIt() throws Exception {
        Path input = fixture(Files.createDirectories(game.resolve("legacybridge/input")).resolve("original.jar"), "1.0");
        String original = sha256(input);
        System.clearProperty("legacybridge.product.fixture.executed");
        try (var controller = inventoried()) {
            assertTrue(controller.convertSelected(""));
            var view = await(controller);
            assertEquals("UNSUPPORTED", view.error().orElseThrow().code(), view.message());
            assertTrue(view.artifact().isEmpty());
            assertTrue(Files.isRegularFile(view.report().orElseThrow()));
            String conversion = Files.readString(view.report().orElseThrow());
            assertTrue(conversion.contains("\"status\": \"ABORTED\""), conversion);
            assertTrue(conversion.contains("\"supportProfile\": \"simple-content-v1\""), conversion);
            assertNull(System.getProperty("legacybridge.product.fixture.executed"));
            try (var entries = Files.list(game.resolve("legacybridge/staging"))) { assertEquals(0, entries.count()); }
            assertEquals(original, sha256(input));
            assertFalse(Files.exists(game.resolve("mods")));
        }
    }

    @Test void blankMappingSelectionConvertsIncludedProfileIntoStagingWithoutApplying() throws Exception {
        Path input = SimpleItemControllerFixture.write(Files.createDirectories(game.resolve("legacybridge/input"))
                .resolve("one-item.jar"));
        String original = sha256(input);
        try (var controller = inventoried()) {
            assertTrue(controller.convertSelected("  "));
            var view = await(controller);
            assertEquals(ConversionController.Phase.RESULT, view.phase(), view.message());
            assertTrue(view.error().isEmpty(), view.message());
            JobView job = controller.selectedJob().orElseThrow();
            assertEquals(JobState.CONVERTED, job.status());
            assertTrue(Files.isRegularFile(job.artifact().orElseThrow()));
            assertTrue(job.artifact().orElseThrow().startsWith(game.resolve("legacybridge/staging")));
            assertTrue(Files.isRegularFile(job.compatibilityReport().orElseThrow()));
            assertTrue(Files.isRegularFile(job.transformationLog().orElseThrow()));
            String conversion = Files.readString(job.conversionReport().orElseThrow());
            assertTrue(conversion.contains("\"itemCount\": 1"), conversion);
            assertTrue(conversion.contains("\"bytecodeExecuted\": false"), conversion);
            assertEquals(original, sha256(input));
            assertTrue(job.installed().isEmpty());
            assertFalse(Files.exists(game.resolve("mods")), "Continuing conversion must not install or activate the result");
            assertTrue(controller.refresh());
            assertEquals(ConversionController.Phase.READY, await(controller).phase());
            assertFalse(Files.exists(game.resolve("mods")));
        }
    }

    @Test void includedContractsDoNotAuthorizeAnUnconfirmedSourceVersion() throws Exception {
        Path input = ConversionQueueTest.fixture(Files.createDirectories(game.resolve("legacybridge/input"))
                .resolve("looks-like-1.16.5.jar"), "wrong_source", "1.20.1");
        String original = sha256(input);
        try (var controller = inventoried()) {
            assertTrue(controller.convertSelected(""));
            var view = await(controller);
            assertEquals("SOURCE_PROFILE_UNCONFIRMED", view.error().orElseThrow().code());
            assertEquals(JobState.BLOCKED, controller.selectedJob().orElseThrow().status());
            assertTrue(view.artifact().isEmpty());
            assertTrue(view.report().isEmpty());
            assertEquals(original, sha256(input));
        }
    }

    @Test void relativeMappingDirectoryCannotDependOnWorkingDirectory() throws Exception {
        fixture(Files.createDirectories(game.resolve("legacybridge/input")).resolve("original.jar"), "1.0");
        try (var controller = inventoried()) {
            assertTrue(controller.convertSelected("cache/mappings"));
            assertEquals("INVALID_MAPPING_PATH", await(controller).error().orElseThrow().code());
        }
    }

    @Test void missingBundleFailsThroughServiceAndCleansOwnedEmptyStaging() throws Exception {
        Path input = SimpleItemControllerFixture.write(Files.createDirectories(game.resolve("legacybridge/input"))
                .resolve("original.jar"));
        String original = sha256(input);
        try (var controller = inventoried()) {
            assertTrue(controller.convertSelected(game.resolve("missing-bundle").toString()));
            var view = await(controller);
            assertEquals("IO_ERROR", view.error().orElseThrow().code(), view.message());
            assertTrue(view.artifact().isEmpty());
            assertEquals(JobState.FAILED, controller.selectedJob().orElseThrow().status());
            try (var entries = Files.list(game.resolve("legacybridge/staging"))) { assertEquals(0, entries.count()); }
            assertEquals(original, sha256(input));
        }
    }

    @Test void duplicateIdentitiesAreNotSelectedForConversionImplicitly() throws Exception {
        Path input = Files.createDirectories(game.resolve("legacybridge/input"));
        fixture(input.resolve("first.jar"), "1.0");
        fixture(input.resolve("second.jar"), "2.0");
        try (var controller = inventoried()) {
            assertTrue(controller.snapshot().selection().orElseThrow().duplicateModId());
            controller.convertSelected(game.resolve("explicit-bundle").toString());
            assertEquals("DUPLICATE_MOD_ID", await(controller).error().orElseThrow().code());
        }
    }

    @Test void malformedCandidateIsReportedWithoutBecomingAConversionSelection() throws Exception {
        Path bad = Files.createDirectories(game.resolve("legacybridge/input")).resolve("invalid.jar");
        Files.writeString(bad, "not a jar");
        try (var controller = inventoried()) {
            assertEquals(1, controller.snapshot().entries().size());
            assertTrue(controller.snapshot().selection().orElseThrow().error().isPresent());
            assertFalse(controller.convertSelected(""));
            assertEquals("INVALID_SELECTION", controller.snapshot().error().orElseThrow().code());
            assertEquals("not a jar", Files.readString(bad));
        }
    }

    @Test void reservedPathCollisionIsRejectedAndPreserved() throws Exception {
        Files.createDirectory(game.resolve("legacybridge"));
        Path collision = Files.writeString(game.resolve("legacybridge/staging"), "user-owned");
        try (var controller = new ConversionController(game)) {
            assertTrue(controller.refresh());
            assertEquals(ConversionController.Phase.ERROR, await(controller).phase());
            assertEquals("user-owned", Files.readString(collision));
            assertFalse(Files.exists(game.resolve("mods")));
        }
    }

    @Test void activeActionRejectsQueueGrowthAndAcceptedCancellationPublishesNothing() throws Exception {
        Path input = Files.createDirectories(game.resolve("legacybridge/input"));
        for (int index = 0; index < 32; index++) fixture(input.resolve("candidate-" + index + ".jar"), "1.0");
        try (var controller = new ConversionController(game)) {
            assertFalse(controller.canCancel());
            assertTrue(controller.refresh());
            assertFalse(controller.refresh());
            assertTrue(controller.canCancel());
            assertTrue(controller.cancel());
            assertFalse(controller.canCancel(), "Accepted cancellation must stop advertising another cancel request");
            var view = await(controller);
            assertEquals(ConversionController.Phase.CANCELLED, view.phase());
            assertTrue(view.artifact().isEmpty());
            assertTrue(view.report().isEmpty());
            assertFalse(controller.canCancel());
        }
    }

    @Test void closeIsNonblockingAndCannotBeUsedAsAnImplicitNewService() {
        var controller = new ConversionController(game);
        assertTimeout(Duration.ofSeconds(1), controller::close);
        assertFalse(controller.refresh());
        assertFalse(controller.analyzeSelected());
        assertFalse(controller.convertSelected(""));
        assertFalse(controller.cancel());
        assertFalse(controller.canCancel());
    }

    @Test void presentationIsBoundedAndStripsMetadataFormatting() {
        assertEquals("one two three", ConversionController.safeText("one\ntwo\u00A7three"));
        assertEquals(512, ConversionController.safeText("x".repeat(10_000)).length());
        assertFalse(ConversionController.safeText("a\u202Eb").contains("\u202E"));
    }

    private ConversionController inventoried() throws Exception {
        var controller = new ConversionController(game);
        controller.refresh();
        await(controller);
        return controller;
    }

    private static ConversionController.View await(ConversionController controller) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (controller.snapshot().busy() && System.nanoTime() < deadline) Thread.sleep(10);
        assertFalse(controller.snapshot().busy(), "Controller did not complete within the test deadline");
        return controller.snapshot();
    }

    private static Path fixture(Path path, String version) throws IOException {
        String metadata = """
                modLoader="javafml"
                loaderVersion="[36,)"
                license="MIT"
                [[mods]]
                modId="controller_fixture"
                version="%s"
                displayName="Controller fixture"
                [[dependencies.controller_fixture]]
                modId="forge"
                mandatory=true
                versionRange="[36,)"
                ordering="NONE"
                side="BOTH"
                [[dependencies.controller_fixture]]
                modId="minecraft"
                mandatory=true
                versionRange="[1.16.5,1.17)"
                ordering="NONE"
                side="BOTH"
                """.formatted(version);
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/StaticOnly", null, "java/lang/Object", null);
        var annotation = writer.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true);
        annotation.visit("value", "controller_fixture");
        annotation.visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "location", "Lnet/minecraft/util/ResourceLocation;", null, null).visitEnd();
        var initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitLdcInsn("legacybridge.product.fixture.executed");
        initializer.visitLdcInsn("true");
        initializer.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        initializer.visitInsn(Opcodes.POP);
        initializer.visitInsn(Opcodes.RETURN);
        initializer.visitMaxs(2, 0);
        initializer.visitEnd();
        writer.visitEnd();
        try (var stream = new ZipOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            stream.putNextEntry(new ZipEntry("META-INF/mods.toml"));
            stream.write(metadata.getBytes(StandardCharsets.UTF_8));
            stream.closeEntry();
            stream.putNextEntry(new ZipEntry("fixture/StaticOnly.class"));
            stream.write(writer.toByteArray());
            stream.closeEntry();
        }
        return path;
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
