package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PublicEvidenceVerifierTest {
    @TempDir Path root;
    Map<String, Object> index;
    List<Map<String, Object>> files, assertions, observations;

    @BeforeEach void prepare() throws Exception {
        // All data is inert, including the fake .jar and .png. No decoder or classloader is involved.
        Files.writeString(root.resolve("public.jar"), "inert public fixture");
        String publicHash = hash(root.resolve("public.jar"));
        Files.writeString(root.resolve("ledger.json"), CorpusJson.canonicalJson(Map.of("public", publicHash,
                "status", "PASS", "nested", List.of(Map.of("value", true)), "integer", 1)));
        Files.writeString(root.resolve("capture.png"), "inert screenshot fixture");
        Files.writeString(root.resolve("game.log"), "inert native log fixture");
        files = new ArrayList<>(List.of(file("PUBLIC", "public.jar", "JAR"), file("LEDGER", "ledger.json", "LEDGER"),
                file("IMAGE", "capture.png", "SCREENSHOT"), file("LOG", "game.log", "LOG")));
        assertions = new ArrayList<>(List.of(assertion("PUBLIC_BINDING", "/public", publicHash), assertion("OBSERVED", "/status", "PASS")));
        observations = new ArrayList<>(List.of(observation("NATIVE", "NATIVE_CLIENT", "RECORDED_PASS",
                List.of("LEDGER", "IMAGE", "LOG"), List.of("OBSERVED")),
                observation("COMMIT_RECOVERY", "NATIVE_CLIENT", "PENDING", List.of(), List.of())));
        index = new LinkedHashMap<>(Map.of("schemaVersion", 1, "reportType", "LEGACYBRIDGE_PUBLIC_EVIDENCE_INDEX",
                "qualification", "PARTIAL", "publicSha256", publicHash, "publicArtifact", "PUBLIC",
                "files", files, "assertions", assertions, "observations", observations));
    }

    @Test void validatesOnlyIntegrityAndPreservesFilesAndPendingGates() throws Exception {
        write(); byte[] before = Files.readAllBytes(root.resolve("index.json"));
        var result = new PublicEvidenceVerifier().verify(root, "index.json");
        assertEquals(4, result.files()); assertEquals(2, result.assertions());
        assertEquals(1, result.recordedObservations()); assertEquals(List.of("COMMIT_RECOVERY"), result.pending());
        assertThrows(UnsupportedOperationException.class, () -> result.pending().clear());
        assertArrayEquals(before, Files.readAllBytes(root.resolve("index.json")));
        try (var paths = Files.list(root)) { assertEquals(5, paths.count()); }
    }
    @Test void changedBytesOfSameSizeRefuse() throws Exception {
        Files.writeString(root.resolve("public.jar"), "INERT public fixture"); refuses("hash mismatch");
    }
    @Test void changedSizeRefuses() throws Exception { Files.writeString(root.resolve("public.jar"), "changed"); refuses("size mismatch"); }
    @Test void missingEvidenceRefusesInsteadOfSkippingOrRecreating() throws Exception {
        Files.delete(root.resolve("capture.png")); refuses("regular file"); assertFalse(Files.exists(root.resolve("capture.png")));
    }
    @Test void changedPublicIdentityRefuses() throws Exception { index.put("publicSha256", "0".repeat(64)); refuses("Public identity"); }
    @Test void allEvidenceRehashedStillRequiresMatchingRecordedAssertion() throws Exception {
        Files.writeString(root.resolve("ledger.json"), "{\"public\":\"" + index.get("publicSha256") + "\",\"status\":\"FAIL\"}");
        files.set(1, file("LEDGER", "ledger.json", "LEDGER")); refuses("Recorded assertion changed");
    }
    @Test void refusesMissingBinding() throws Exception { assertions.remove(0); refuses("bind the exact public hash"); }
    @Test void rejectsDuplicateFileIds() throws Exception { files.get(1).put("id", "PUBLIC"); refuses("Duplicate evidence ID"); }
    @Test void rejectsDuplicatePaths() throws Exception { files.add(new LinkedHashMap<>(files.get(0))); refuses("Duplicate evidence path"); }
    @Test void rejectsDuplicateAssertions() throws Exception { assertions.add(assertions.get(0)); refuses("Duplicate assertion"); }
    @Test void rejectsDuplicateObservations() throws Exception { observations.add(observations.get(0)); refuses("Duplicate observation"); }
    @Test void rejectsUnknownReferencedFile() throws Exception { assertions.get(0).put("file", "MISSING"); refuses("Missing evidence reference"); }
    @Test void rejectsUnknownReferencedAssertion() throws Exception {
        observations.get(0).put("assertions", List.of("MISSING")); refuses("Unknown/duplicate observation assertion");
    }
    @Test void rejectsAssertionSourceOmittedFromObservation() throws Exception {
        observations.get(0).put("evidence", List.of("IMAGE", "LOG")); refuses("Assertion ledger absent");
    }
    @Test void cannotUseHeadlessXmlAsNativeImage() throws Exception { files.get(2).put("kind", "TEST_RESULT"); refuses("Native client needs"); }
    @Test void nativeServerAlsoNeedsLogs() throws Exception {
        observations.get(0).put("dimension", "NATIVE_SERVER"); observations.get(0).put("evidence", List.of("LEDGER")); refuses("needs log evidence");
    }
    @Test void nativeLogOnlyObservationDoesNotRequireOrClaimVisualEvidence() throws Exception {
        observations.get(0).put("dimension", "NATIVE_CLIENT_LOG"); observations.get(0).put("evidence", List.of("LEDGER", "LOG"));
        write(); assertEquals(1, new PublicEvidenceVerifier().verify(root, "index.json").recordedObservations());
    }
    @Test void nativeLogOnlyObservationStillRequiresLog() throws Exception {
        observations.get(0).put("dimension", "NATIVE_CLIENT_LOG"); observations.get(0).put("evidence", List.of("LEDGER")); refuses("needs log evidence");
    }
    @Test void pendingCannotBeElidedToApprovePartialQualification() throws Exception { observations.remove(1); refuses("retain its pending gates"); }
    @Test void integrityCannotApproveRelease() throws Exception { index.put("qualification", "ACCEPTED"); refuses("cannot approve"); }
    @Test void unknownSchemaRefuses() throws Exception { index.put("schemaVersion", 2); refuses("Unsupported evidence index schema"); }
    @Test void unsupportedKindRefuses() throws Exception { files.get(2).put("kind", "MOCKUP"); refuses("Unknown evidence kind"); }
    @Test void unsupportedStateRefuses() throws Exception { observations.get(0).put("state", "ACCEPTED"); refuses("Unknown evidence dimension/state"); }
    @Test void pendingAndUnclaimedAreDistinct() throws Exception {
        observations.add(observation("OFFLINE", "REVIEW", "NOT_CLAIMED", List.of(), List.of()));
        write(); var result = new PublicEvidenceVerifier().verify(root, "index.json");
        assertEquals(List.of("OFFLINE"), result.notClaimed()); assertEquals(List.of("COMMIT_RECOVERY"), result.pending());
    }
    @Test void oversizedDeclaredFileRefusesBeforeReading() throws Exception {
        files.get(0).put("bytes", PublicEvidenceVerifier.MAX_FILE_BYTES + 1); refuses("file exceeds byte bound");
    }
    @Test void excessiveInventoryRefusesBeforeResolvingEntries() throws Exception {
        index.put("files", java.util.Collections.nCopies(513, files.get(0))); refuses("array limit");
    }
    @Test void aggregateBytesAreBoundedBeforeHashing() throws Exception {
        for (var file : files) file.put("bytes", PublicEvidenceVerifier.MAX_FILE_BYTES);
        Files.writeString(root.resolve("extra.log"), "small"); var extra = file("EXTRA", "extra.log", "LOG");
        extra.put("bytes", 1); files.add(extra); refuses("aggregate exceeds");
    }
    @Test void actualOversizedIndexRefuses() throws Exception {
        Files.writeString(root.resolve("index.json"), " ".repeat(CorpusJson.MAX_BYTES + 1));
        assertThrows(IOException.class, () -> new PublicEvidenceVerifier().verify(root, "index.json"));
    }
    @Test void limitsNumberOfParsedLedgers() throws Exception {
        for (int n = 0; n < 16; n++) {
            String name = "more-" + n + ".json", id = "MORE_" + n;
            Files.writeString(root.resolve(name), "{\"status\":\"PASS\"}");
            files.add(file(id, name, "LEDGER"));
            var check = assertion("CHECK_" + n, "/status", "PASS"); check.put("file", id); assertions.add(check);
        }
        refuses("Parsed ledger budget exceeded");
    }
    @Test void malformedJsonRefuses() throws Exception {
        Files.writeString(root.resolve("index.json"), "{broken");
        assertThrows(IOException.class, () -> new PublicEvidenceVerifier().verify(root, "index.json"));
    }
    @Test void nullRequiresExistingKeyNotMissingKey() throws Exception {
        assertions.get(1).put("pointer", "/absent"); assertions.get(1).put("expected", null); refuses("Missing evidence pointer key");
    }
    @Test void nestedBooleanAndIntegralNumberAssertionsWork() throws Exception {
        assertions.add(assertion("BOOL", "/nested/0/value", true)); assertions.add(assertion("NUMBER", "/integer", 1.0));
        write(); assertEquals(4, new PublicEvidenceVerifier().verify(root, "index.json").assertions());
    }
    @ParameterizedTest @ValueSource(strings = {"../public.jar", "/public.jar", "C:/public.jar", "a\\b", "a//b", "a/./b", "public.jar.", "public.jar ", "a/../public.jar"})
    void unsafePathRefuses(String path) throws Exception { files.get(0).put("path", path); refuses("Unsafe evidence"); }
    @ParameterizedTest @ValueSource(strings = {"/nested/-1", "/nested/01", "/nested/999999999999", "/nested/2", "/status/key", "/~0", "/"})
    void badPointerRefuses(String pointer) throws Exception { assertions.get(1).put("pointer", pointer); refuses("evidence"); }
    @Test void symlinkEvidenceRefusesWhenPlatformAllowsItsCreation() throws Exception {
        Path alias = root.resolve("alias.jar");
        try { Files.createSymbolicLink(alias, root.resolve("public.jar")); }
        catch (IOException | UnsupportedOperationException unavailable) { assumeTrue(false, "Platform cannot create symlink: " + unavailable.getClass().getSimpleName()); }
        files.get(0).put("path", "alias.jar"); refuses("regular file");
    }

    private Map<String, Object> file(String id, String path, String kind) throws Exception {
        return new LinkedHashMap<>(Map.of("id", id, "path", path, "kind", kind,
                "bytes", Files.size(root.resolve(path)), "sha256", hash(root.resolve(path))));
    }
    private static Map<String, Object> assertion(String id, String pointer, Object expected) {
        return new LinkedHashMap<>(Map.of("id", id, "file", "LEDGER", "pointer", pointer, "expected", expected));
    }
    private static Map<String, Object> observation(String id, String dimension, String state, List<String> evidence, List<String> assertions) {
        return new LinkedHashMap<>(Map.of("id", id, "dimension", dimension, "state", state,
                "boundary", "Test-declared evidence only, not real game execution", "evidence", evidence, "assertions", assertions));
    }
    private static String hash(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private void write() throws IOException { Files.writeString(root.resolve("index.json"), CorpusJson.canonicalJson(index)); }
    private void refuses(String expected) throws Exception {
        write(); IOException error = assertThrows(IOException.class, () -> new PublicEvidenceVerifier().verify(root, "index.json"));
        assertTrue(error.getMessage().toLowerCase(java.util.Locale.ROOT)
                .contains(expected.toLowerCase(java.util.Locale.ROOT)), error::getMessage);
    }
}
