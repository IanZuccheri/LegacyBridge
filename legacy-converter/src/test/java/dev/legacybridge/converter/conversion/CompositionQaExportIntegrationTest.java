package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.operation.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Real authored Forge source + exact mappings. Never defines source/target classes or launches games. */
@EnabledIfSystemProperty(named = "legacybridge.verifyCompositionEnvironment", matches = "true")
class CompositionQaExportIntegrationTest {
    @TempDir Path directory;
    static MappingInputs mappings;
    static final String SOURCE = "873013592b97449b9fffd3f569cc7b7fc3309fcc9b1d241f5a230d1d45731212";
    static final String CANDIDATE = "328c475e98e80c022aeb044d55cdf1b375e62db89c0602da8fc3c00fb495eca2";
    static MappingInputs inputs() throws Exception {
        if (mappings == null) mappings = new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));
        return mappings;
    }
    @AfterAll static void release() { mappings = null; }
    Path fixture() { return Path.of(System.getProperty("legacybridge.fixtureRoot"), "composed-logic-recipes-1.16.5/build/libs/legacybridge-composed-logic-recipes-1.16.5-1.0.0.jar").toAbsolutePath().normalize(); }
    Path root() throws Exception { return Files.createDirectory(directory.resolve("composition-qa")); }
    @Test void exportsExactlyThePreviouslyProvedCandidateWithoutChangingOrdinaryConversion() throws Exception {
        Path root = root();
        var result = CompositionQaExport.export(fixture(), SOURCE, CANDIDATE, inputs(), root, "run-real");
        assertTrue(result.exported(), result.proof().report().toString()); assertFalse(result.proof().publicationAllowed());
        Path output = result.directory().orElseThrow().resolve("candidate.jar");
        assertEquals(CANDIDATE, CompositionQaFiles.hashFile(output)); assertEquals(SOURCE, CompositionQaFiles.hashFile(fixture()));
        assertEquals(5913, Files.size(output));
        try (var jar = new JarFile(output.toFile(), false)) {
            assertEquals("composed-logic-recipes-v1", jar.getManifest().getMainAttributes().getValue("LegacyBridge-Profile"));
            assertEquals("STATIC-CANDIDATE-ONLY", jar.getManifest().getMainAttributes().getValue("LegacyBridge-Qualification"));
            assertNotNull(jar.getEntry("dev/legacybridge/fixture/composed/ComposedItem.class"));
        }
        var ordinary = new LogicPreservingConverter().convert(fixture(), directory.resolve("ordinary.jar"), inputs());
        assertFalse(ordinary.converted()); assertFalse(Files.exists(directory.resolve("ordinary.jar")));
    }
    @Test void callbackOnlySeesValidatedBytesAndBorrowIsWipedAfterSuccess() throws Exception {
        AtomicReference<byte[]> borrow = new AtomicReference<>();
        var proof = LogicPreservingConverter.inspectLogicWithRecipesCandidate(fixture(), directory.resolve("never.jar"), inputs(), (bytes, valid) -> {
            assertTrue(valid.validated()); assertEquals(CANDIDATE, SimpleContentConverter.hash(bytes)); borrow.set(bytes);
        });
        assertTrue(proof.validated()); assertArrayEquals(new byte[5913], borrow.get()); assertFalse(Files.exists(directory.resolve("never.jar")));
    }
    @Test void callbackFailureWipesBorrowAndDoesNotMasqueradeAsConversion() throws Exception {
        AtomicReference<byte[]> borrow = new AtomicReference<>();
        assertThrows(IOException.class, () -> LogicPreservingConverter.inspectLogicWithRecipesCandidate(fixture(), directory.resolve("never.jar"), inputs(), (bytes, proof) -> {
            borrow.set(bytes); throw new IOException("dev consumer failure");
        })); assertArrayEquals(new byte[5913], borrow.get());
    }
    @Test void cancelledFinalPreparationNeverCallsExporter() throws Exception {
        var baseline = inputs(); AtomicReference<OperationControl> ref = new AtomicReference<>();
        var control = new OperationControl(OperationLimits.defaults(), p -> { if (p.stage() == OperationStage.PREPARED) ref.get().requestCancel(); }); ref.set(control);
        try (var scope = control.enter()) {
            assertThrows(OperationStoppedException.class, () -> LogicPreservingConverter.inspectLogicWithRecipesCandidate(fixture(), directory.resolve("never.jar"), baseline,
                    (bytes, proof) -> fail("cancelled proof must not be borrowed")));
        }
    }
    @Test void wrongSourceSelectionIsRejectedBeforeConversionOrWriting() throws Exception {
        Path root = root();
        assertThrows(IOException.class, () -> CompositionQaExport.export(fixture(), "0".repeat(64), CANDIDATE, inputs(), root, "run-stale"));
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }
    @Test void wrongCandidateSelectionIsRejectedAfterProofWithoutExport() throws Exception {
        Path root = root();
        assertThrows(IOException.class, () -> CompositionQaExport.export(fixture(), SOURCE, "0".repeat(64), inputs(), root, "run-stale"));
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }
    @ParameterizedTest @ValueSource(strings = {"UNKNOWN_RESOURCE", "DEPENDENCY"})
    void unsupportedSourceReturnsStructuredBlockAndNeverBorrowsBytes(String defect) throws Exception {
        Path changed = directory.resolve("changed.jar");
        try (var original = new JarFile(fixture().toFile(), false); var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(changed))) {
            var entries = original.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); if (entry.isDirectory()) continue;
                byte[] bytes; try (var stream = original.getInputStream(entry)) { bytes = stream.readAllBytes(); }
                if (defect.equals("DEPENDENCY") && entry.getName().equals("META-INF/mods.toml")) {
                    String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    bytes = text.replace("modId=\"forge\"", "modId=\"missing_library\"").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
                output.putNextEntry(new java.util.zip.ZipEntry(entry.getName())); output.write(bytes); output.closeEntry();
            }
            if (defect.equals("UNKNOWN_RESOURCE")) { output.putNextEntry(new java.util.zip.ZipEntry("unknown-resource.bin")); output.write(42); output.closeEntry(); }
        }
        Path root = root(); var result = CompositionQaExport.export(changed, CompositionQaFiles.hashFile(changed), CANDIDATE, inputs(), root, "run-blocked");
        assertFalse(result.exported()); assertFalse(result.proof().validated());
        assertEquals("BLOCKED", result.toTree().get("status")); assertEquals("UNSUPPORTED", result.proof().report().get("compatibilityStatus"));
        assertFalse(((List<?>) result.proof().report().get("diagnostics")).isEmpty());
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }
    @Test void corruptJarIsAnInputErrorNotAnExport() throws Exception {
        Path corrupt = Files.write(directory.resolve("bad.jar"), new byte[]{1, 2, 3}); Path root = root();
        assertThrows(dev.legacybridge.converter.archive.InputRejectedException.class, () -> CompositionQaExport.export(corrupt, CompositionQaFiles.hashFile(corrupt), CANDIDATE, inputs(), root, "run-corrupt"));
        try (var files = Files.list(root)) { assertEquals(0, files.count()); }
    }
}
