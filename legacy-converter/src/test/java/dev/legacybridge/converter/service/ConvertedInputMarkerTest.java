package dev.legacybridge.converter.service;

import dev.legacybridge.converter.application.AnalysisService;
import dev.legacybridge.converter.archive.InputRejectedException;
import dev.legacybridge.converter.report.ExecutionStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

/** Marker recognition is an inert inventory hint, never authentication or a runtime validation. */
class ConvertedInputMarkerTest {
    private static final String ID = "marker_example";
    private static final String MANIFEST = "META-INF/MANIFEST.MF";
    private static final String METADATA = "META-INF/mods.toml";
    private static final String CONTENT = "META-INF/legacybridge/" + ID + "/content.json";
    private static final String BINDINGS = "META-INF/legacybridge/" + ID + "/adapters.json";
    private static final int CAPTURE_LIMIT = 1_048_576;
    @TempDir Path directory;

    @Test void correlatedMarkersAreDetectedRegardlessOfFilenameWithoutExecutingClasses() throws Exception {
        String initializerMarker = "legacybridge.marker.test." + UUID.randomUUID();
        Map<String, byte[]> entries = markers();
        entries.put("qa/MarkerOriginal.class", inertClass(initializerMarker));
        Path input = jar(directory.resolve("ordinary-original-looking-name.jar"), entries);
        byte[] before = Files.readAllBytes(input);
        assertNull(System.getProperty(initializerMarker));
        var report = new AnalysisService().analyze(input);
        assertEquals(1, report.bytecode().classCount());
        assertEquals(ExecutionStatus.NOT_TESTED, report.assessment().executionStatus());
        assertTrue(ConvertedInputMarker.detect(input, report));
        var row = inventory(input);
        assertTrue(row.alreadyConverted());
        assertTrue(row.error().isEmpty());
        assertEquals(ID, row.mods().get(0).modId());
        assertNull(System.getProperty(initializerMarker), "Analysis and marker scans must never initialize a mod class");
        assertArrayEquals(before, Files.readAllBytes(input));
    }

    @Test void filenameOrOneIsolatedMarkerDoesNotIdentifyConvertedContent() throws Exception {
        for (String retained : new String[] { "none", MANIFEST, CONTENT, BINDINGS }) {
            Map<String, byte[]> entries = markers();
            for (String marker : new String[] { MANIFEST, CONTENT, BINDINGS })
                if (!marker.equals(retained)) entries.remove(marker);
            Path input = jar(subdirectory("only-" + retained.hashCode()).resolve("LegacyBridge-converted-1.20.1.jar"), entries);
            assertFalse(detect(input), retained);
            assertFalse(inventory(input).alreadyConverted(), retained);
        }
    }

    @Test void everyCorrelatedMarkerIsRequired() throws Exception {
        for (String missing : new String[] { MANIFEST, CONTENT, BINDINGS }) {
            Map<String, byte[]> entries = markers();
            entries.remove(missing);
            Path input = jar(subdirectory("missing-" + missing.hashCode()).resolve("mod.jar"), entries);
            assertFalse(detect(input), missing);
            assertFalse(inventory(input).alreadyConverted(), missing);
        }
    }

    @Test void namespaceAndContentModIdMustMatchTheDeclaredIdentity() throws Exception {
        for (String mismatch : new String[] { "content-path", "bindings-path", "content-id" }) {
            Map<String, byte[]> entries = markers();
            if (mismatch.equals("content-id")) entries.put(CONTENT, bytes(plan("another_mod", "1")));
            else {
                String selected = mismatch.equals("content-path") ? CONTENT : BINDINGS;
                entries.put(selected.replace(ID, "another_mod"), entries.remove(selected));
            }
            Path input = jar(subdirectory(mismatch).resolve("mod.jar"), entries);
            assertFalse(detect(input), mismatch);
            assertFalse(inventory(input).alreadyConverted(), mismatch);
        }
    }

    @Test void profileCatalogAndPlanSchemaMustBeRecognized() throws Exception {
        for (String mismatch : new String[] { "profile", "catalog", "schema-zero", "schema-future", "schema-string" }) {
            Map<String, byte[]> entries = markers();
            switch (mismatch) {
                case "profile" -> entries.put(MANIFEST, bytes(manifest("unverified-profile", "1")));
                case "catalog" -> entries.put(MANIFEST, bytes(manifest("simple-content-v1", "2")));
                case "schema-zero" -> entries.put(CONTENT, bytes(plan(ID, "0")));
                case "schema-future" -> entries.put(CONTENT, bytes(plan(ID, "2")));
                case "schema-string" -> entries.put(CONTENT, bytes(plan(ID, "\"1\"")));
                default -> throw new AssertionError(mismatch);
            }
            Path input = jar(subdirectory(mismatch).resolve("mod.jar"), entries);
            assertFalse(detect(input), mismatch);
        }
    }

    @Test void runtimeDependencyMustBeMandatoryAndMetadataMustDescribeOneJavaFmlMod() throws Exception {
        for (String mismatch : new String[] { "no-runtime", "optional-runtime", "other-loader", "multiple-mods" }) {
            Map<String, byte[]> entries = markers();
            String metadata = metadata(!mismatch.equals("no-runtime"), !mismatch.equals("optional-runtime"));
            if (mismatch.equals("other-loader")) metadata = metadata.replace("javafml", "lowcodefml");
            if (mismatch.equals("multiple-mods")) metadata += "\n[[mods]]\nmodId=\"another_mod\"\nversion=\"1.0.0\"\n";
            entries.put(METADATA, bytes(metadata));
            Path input = jar(subdirectory(mismatch).resolve("mod.jar"), entries);
            assertFalse(detect(input), mismatch);
            assertFalse(inventory(input).alreadyConverted(), mismatch);
        }
    }

    @Test void emptyOrNonObjectBindingsDoNotCountAsCorrelation() throws Exception {
        for (String binding : new String[] { "{}", "[]", "null", "\"marker\"" }) {
            Map<String, byte[]> entries = markers();
            entries.put(BINDINGS, bytes(binding));
            Path input = jar(subdirectory("bindings-" + binding.hashCode()).resolve("mod.jar"), entries);
            assertFalse(detect(input), binding);
        }
    }

    @Test void malformedJsonIsAReportedInventoryErrorInsteadOfAConvertedFlag() throws Exception {
        for (String path : new String[] { CONTENT, BINDINGS }) {
            Map<String, byte[]> entries = markers();
            entries.put(path, bytes("{\"incomplete\":"));
            Path input = jar(subdirectory("json-" + path.hashCode()).resolve("mod.jar"), entries);
            IOException failure = assertThrows(IOException.class, () -> detect(input));
            assertEquals("Malformed converted-product markers", failure.getMessage());
            assertInvalidInventory(input, "IO_ERROR");
        }
    }

    @Test void malformedUtf8IsRejectedWithoutReplacementDecoding() throws Exception {
        for (String path : new String[] { CONTENT, BINDINGS }) {
            Map<String, byte[]> entries = markers();
            entries.put(path, new byte[] { '{', '"', (byte) 0xc3, '(', '"', ':', '1', '}' });
            Path input = jar(subdirectory("utf8-" + path.hashCode()).resolve("mod.jar"), entries);
            assertThrows(java.nio.charset.CharacterCodingException.class, () -> detect(input));
            assertInvalidInventory(input, "IO_ERROR");
        }
    }

    @Test void eachMarkerCaptureAcceptsOneMebibyteButRejectsTheNextByte() throws Exception {
        for (String path : new String[] { CONTENT, BINDINGS }) {
            Map<String, byte[]> entries = markers();
            byte[] padded = new byte[CAPTURE_LIMIT];
            Arrays.fill(padded, (byte) ' ');
            System.arraycopy(entries.get(path), 0, padded, 0, entries.get(path).length);
            entries.put(path, padded);
            Path exact = jar(subdirectory("exact-" + path.hashCode()).resolve("mod.jar"), entries);
            assertTrue(detect(exact), "Exactly the capture limit remains permitted: " + path);
            entries.put(path, Arrays.copyOf(padded, CAPTURE_LIMIT + 1));
            Path excess = jar(subdirectory("excess-" + path.hashCode()).resolve("mod.jar"), entries);
            var report = new AnalysisService().analyze(excess);
            InputRejectedException failure = assertThrows(InputRejectedException.class, () -> ConvertedInputMarker.detect(excess, report));
            assertEquals("ENTRY_SIZE_LIMIT", failure.reasonCode());
            assertInvalidInventory(excess, "ENTRY_SIZE_LIMIT");
        }
    }

    @Test void reportFingerprintCannotBeReusedAfterTheInputChanges() throws Exception {
        Path input = jar(directory.resolve("mod.jar"), markers());
        var report = new AnalysisService().analyze(input);
        Map<String, byte[]> changed = markers();
        changed.put("change.txt", bytes("Only this operation-owned temporary fixture is replaced."));
        Path replacement = jar(directory.resolve("replacement.jar"), changed);
        Files.copy(replacement, input, StandardCopyOption.REPLACE_EXISTING);
        IOException failure = assertThrows(IOException.class, () -> ConvertedInputMarker.detect(input, report));
        assertEquals("Input changed while identifying converted markers", failure.getMessage());
    }

    private static boolean detect(Path input) throws Exception {
        return ConvertedInputMarker.detect(input, new AnalysisService().analyze(input));
    }

    private static InventoryResult.Entry inventory(Path input) throws Exception {
        try (var engine = new LegacyEngineService()) {
            var outcome = engine.inventory(input.getParent()).completion().toCompletableFuture().get(15, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, outcome.status());
            return outcome.value().orElseThrow().entries().stream().filter(row -> row.path().equals(input)).findFirst().orElseThrow();
        }
    }

    private static void assertInvalidInventory(Path input, String code) throws Exception {
        var row = inventory(input);
        assertFalse(row.alreadyConverted());
        assertTrue(row.fingerprint().isPresent(), "Even rejected regular input retains its bounded inert fingerprint");
        assertEquals(code, row.error().orElseThrow().code());
    }

    private Path subdirectory(String name) throws IOException { return Files.createDirectory(directory.resolve(name)); }

    private static Map<String, byte[]> markers() {
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put(MANIFEST, bytes(manifest("simple-content-v1", "1")));
        entries.put(METADATA, bytes(metadata(true, true)));
        entries.put(CONTENT, bytes(plan(ID, "1")));
        // Same binding shape and real implemented bootstrap tuple as the converter's baseline output.
        entries.put(BINDINGS, bytes("""
                {"schemaVersion":1,"runtimeVersion":"0.1.1-SNAPSHOT","adapters":[
                  {"id":"registry.simple-content.v1","revision":1,
                   "implementationClass":"dev.legacybridge.runtime.compat.registry.SimpleContentAdapter",
                   "method":"register","descriptor":"(Ljava/lang/String;Ljava/lang/ClassLoader;Ljava/lang/String;)V"}]}
                """));
        return entries;
    }

    private static String metadata(boolean includeRuntime, boolean mandatory) {
        String text = "modLoader=\"javafml\"\nloaderVersion=\"[47,48)\"\nlicense=\"All Rights Reserved\"\n"
                + "[[mods]]\nmodId=\"" + ID + "\"\nversion=\"1.0.0\"\ndisplayName=\"Marker fixture\"\n"
                + "[[dependencies." + ID + "]]\nmodId=\"minecraft\"\nmandatory=true\nversionRange=\"[1.20.1]\"\nordering=\"NONE\"\nside=\"BOTH\"\n";
        if (includeRuntime) text += "[[dependencies." + ID + "]]\nmodId=\"legacybridge_runtime\"\nmandatory=" + mandatory
                + "\nversionRange=\"[0.1.1-SNAPSHOT]\"\nordering=\"AFTER\"\nside=\"BOTH\"\n";
        return text;
    }

    private static String manifest(String profile, String catalog) {
        return "Manifest-Version: 1.0\r\nLegacyBridge-Profile: " + profile + "\r\nLegacyBridge-Adapter-Catalog: " + catalog + "\r\n\r\n";
    }
    private static String plan(String id, String schema) {
        return "{\"modId\":\"" + id + "\",\"modVersion\":\"1.0.0\",\"schemaVersion\":" + schema + ",\"items\":[],\"blocks\":[],\"sounds\":[]}";
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static Path jar(Path path, Map<String, byte[]> entries) throws IOException {
        try (var output = new ZipOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            for (var entry : new TreeMap<>(entries).entrySet()) {
                ZipEntry zip = new ZipEntry(entry.getKey());
                // STORED keeps capture-limit tests separate from compression-ratio policy.
                zip.setMethod(ZipEntry.STORED);
                zip.setSize(entry.getValue().length);
                zip.setCompressedSize(entry.getValue().length);
                CRC32 crc = new CRC32(); crc.update(entry.getValue()); zip.setCrc(crc.getValue());
                zip.setTime(0);
                output.putNextEntry(zip); output.write(entry.getValue()); output.closeEntry();
            }
        }
        return path;
    }

    private static byte[] inertClass(String property) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "qa/MarkerOriginal", null, "java/lang/Object", null);
        var initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitLdcInsn(property); initializer.visitLdcInsn("ORIGINAL_EXECUTED");
        initializer.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        initializer.visitInsn(Opcodes.POP); initializer.visitInsn(Opcodes.RETURN);
        initializer.visitMaxs(2, 0); initializer.visitEnd(); writer.visitEnd();
        return writer.toByteArray();
    }
}
