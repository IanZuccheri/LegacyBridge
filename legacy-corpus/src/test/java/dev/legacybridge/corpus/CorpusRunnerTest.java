package dev.legacybridge.corpus;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** All bytes are owned inert test data; no private corpus, mappings, game or mod implementation is loaded. */
class CorpusRunnerTest {
    @Test void successfulScopeWarningsAreNotConversionBlockers() {
        var result = new dev.legacybridge.converter.conversion.ConversionResult(true,
                Map.of("diagnostics", List.of("Only the accepted finite registration grammar is supported.")));
        Map<String, Object> summary = CorpusRunner.diagnosticSummary(result);
        assertEquals(List.of(), summary.get("blockers"));
        assertEquals(result.report().get("diagnostics"), summary.get("conversionDiagnostics"));
    }

    @Test void actualRefusalDiagnosticsRemainBlockers() {
        var result = new dev.legacybridge.converter.conversion.ConversionResult(false,
                Map.of("diagnostics", List.of("Unsupported behavior")));
        Map<String, Object> summary = CorpusRunner.diagnosticSummary(result);
        assertEquals(List.of("Unsupported behavior"), summary.get("blockers"));
        assertEquals(summary.get("blockers"), summary.get("conversionDiagnostics"));
    }

    @TempDir Path project;
    Path corpus;
    Path record;
    Path original;
    Path bundle;
    Path output;
    byte[] originalBytes;

    @BeforeEach void prepare() throws Exception {
        corpus = Files.createDirectory(project.resolve("corpus"));
        Files.createDirectory(corpus.resolve("records"));
        Files.createDirectory(corpus.resolve("evidence"));
        Files.createDirectories(project.resolve("build/corpus-runs"));
        output = project.resolve("build/corpus-runs/new-observation");
        original = project.resolve("owned-corrupt.jar");
        originalBytes = "Deliberately inert local test data, not a ZIP or any executable class.\n".getBytes(StandardCharsets.UTF_8);
        Files.write(original, originalBytes);
        record = corpus.resolve("records/reviewed.json");
        // A corrupt analysis never enters mapping loading. This is explicitly not a valid mapping fixture.
        bundle = Files.createDirectory(project.resolve("unloaded-mapping-placeholder"));
        CorpusJson.writeCanonicalCreateNew(bundle.resolve("manifest.json"), Map.of("testOnly", "MUST_NOT_BE_LOADED_FOR_CORRUPT_INPUT"));
    }

    @Test void sourceHashMismatchRefusesBeforeCreatingAnyOutput() throws Exception {
        reviewed(true, originalBytes.length);
        byte[] changed = originalBytes.clone(); changed[0] ^= 1; Files.write(original, changed);
        IOException failure = assertThrows(IOException.class, () -> observe(bundle, output));
        assertTrue(failure.getMessage().contains("does not match reviewed"));
        assertFalse(Files.exists(output)); assertArrayEquals(changed, Files.readAllBytes(original));
    }

    @Test void sourceSizeMismatchRefusesBeforeCreatingAnyOutput() throws Exception {
        reviewed(true, originalBytes.length + 1L);
        IOException failure = assertThrows(IOException.class, () -> observe(bundle, output));
        assertTrue(failure.getMessage().contains("does not match reviewed"));
        assertFalse(Files.exists(output)); assertArrayEquals(originalBytes, Files.readAllBytes(original));
    }

    @Test void unlistedRecordDoesNotAcquireAuthorityFromItsFileName() throws Exception {
        reviewed(false, originalBytes.length);
        IOException failure = assertThrows(IOException.class, () -> observe(bundle, output));
        assertTrue(failure.getMessage().contains("not in the reviewed corpus catalog"));
        assertFalse(Files.exists(output));
    }

    @Test void refusesOutputInPublicCorpusOrOtherPrivateDirectories() throws Exception {
        reviewed(true, originalBytes.length);
        for (Path rejected : List.of(corpus.resolve("new-observation"), project.resolve("private/new-observation"),
                project.resolve("build/corpus-runs"), project.resolve("build/corpus-runs/../escaped"))) {
            IOException failure = assertThrows(IOException.class, () -> observe(bundle, rejected));
            assertTrue(failure.getMessage().contains("must stay in ignored build/corpus-runs"));
            if (!rejected.normalize().equals(project.resolve("build/corpus-runs"))) assertFalse(Files.exists(rejected.normalize()));
        }
        assertArrayEquals(originalBytes, Files.readAllBytes(original));
    }

    @Test void existingObservationAndUnrelatedSentinelAreNeverOverwritten() throws Exception {
        reviewed(true, originalBytes.length);
        Files.createDirectory(output);
        Path sentinel = output.resolve("unrelated.txt"); byte[] bytes = "Preserve this user's existing content.".getBytes(StandardCharsets.UTF_8);
        Files.write(sentinel, bytes);
        assertThrows(FileAlreadyExistsException.class, () -> observe(bundle, output));
        assertArrayEquals(bytes, Files.readAllBytes(sentinel));
        try (var files = Files.list(output)) { assertEquals(1, files.count()); }
        assertArrayEquals(originalBytes, Files.readAllBytes(original));
    }

    @Test void corruptAnalysisPublishesOnlyFailureReceiptWithAllRuntimeStagesNotRun() throws Exception {
        reviewed(true, originalBytes.length);
        byte[] originalRecord = Files.readAllBytes(record);
        byte[] originalCatalog = Files.readAllBytes(corpus.resolve("catalog.json"));
        Map<String, Object> observation = observe(bundle, output);
        assertEquals("FAILED", observation.get("analysisOperationStatus"));
        assertEquals("MALFORMED_ZIP", observation.get("analysisErrorCode"));
        assertEquals("NOT_RUN", observation.get("outputStatus"));
        assertNull(observation.get("outputSha256"));
        assertEquals(Boolean.TRUE, observation.get("originalHashUnchanged"));
        assertEquals(Boolean.FALSE, observation.get("runtimeEvidenceInherited"));
        assertEquals(Boolean.FALSE, observation.get("originalClassesExecuted"));
        assertEquals(Boolean.FALSE, observation.get("networkAcquisition"));
        Map<?, ?> stages = (Map<?, ?>) observation.get("stages");
        assertEquals("FAILED", stages.get("analyzable"));
        for (String stage : List.of("convertible", "bytecodeValidated", "clientInitializes", "serverInitializes", "clientBehavior", "serverBehavior", "multiplayer"))
            assertEquals("NOT_RUN", stages.get(stage), "No historical stage can be inherited: " + stage);
        assertTrue(((Map<?, ?>) observation.get("reports")).isEmpty());
        assertFalse(Files.exists(output.resolve("converted.jar")));
        assertFalse(Files.exists(output.resolve("conversion.json")));
        assertTrue(Files.isRegularFile(output.resolve("observation.json")));
        assertEquals(CorpusJson.canonicalJson(observation), CorpusJson.canonicalJson(CorpusJson.read(output.resolve("observation.json"))));
        assertEquals(CorpusRunner.hash(record), observation.get("recordSha256"));
        assertEquals(CorpusRunner.hash(corpus.resolve("catalog.json")), observation.get("catalogSha256"));
        assertArrayEquals(originalRecord, Files.readAllBytes(record));
        assertArrayEquals(originalCatalog, Files.readAllBytes(corpus.resolve("catalog.json")));
        assertArrayEquals(originalBytes, Files.readAllBytes(original));
        try (var files = Files.list(output)) { assertEquals(List.of("observation.json"), files.map(path -> path.getFileName().toString()).toList()); }
    }

    @Test void explicitMissingBundleIsRefusedWithoutCacheSearchOrOutput() throws Exception {
        reviewed(true, originalBytes.length);
        assertThrows(IOException.class, () -> observe(project.resolve("does-not-exist-bundle"), output));
        assertFalse(Files.exists(output));
        assertArrayEquals(originalBytes, Files.readAllBytes(original));
    }

    @Test void bundleWithoutManifestIsRefusedWithoutPublishingObservation() throws Exception {
        reviewed(true, originalBytes.length);
        Path missingManifest = Files.createDirectory(project.resolve("manifest-absent"));
        assertThrows(IOException.class, () -> observe(missingManifest, output));
        assertFalse(Files.exists(output));
    }

    private Map<String, Object> observe(Path selectedBundle, Path destination) throws Exception {
        return CorpusRunner.observe(record, original, selectedBundle, destination);
    }

    private void reviewed(boolean listed, long declaredSize) throws Exception {
        Path authorization = corpus.resolve("evidence/local-request.json");
        CorpusJson.writeCanonicalCreateNew(authorization, Map.of("basis", "PROJECT_LOCAL_REQUEST", "scope", "Owned inert unit-test data only; no third-party authorization asserted."));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("loader", "unknown"); source.put("minecraftVersions", List.of("UNKNOWN"));
        source.put("forgeBuild", null); source.put("forgeVersionEvidence", "INTENTIONALLY_CORRUPT_LOCAL_TEST_DATA");
        Map<String, Object> outputDescriptor = new LinkedHashMap<>(); outputDescriptor.put("sha256", null); outputDescriptor.put("status", "NOT_RUN");
        Map<String, Object> proof = new LinkedHashMap<>(); proof.put("id", "authorization"); proof.put("type", "AUTHORIZATION");
        proof.put("sourceSha256", null); proof.put("targetSha256", null); proof.put("path", "corpus/evidence/local-request.json"); proof.put("sha256", CorpusRunner.hash(authorization));
        Map<String, Object> stages = new LinkedHashMap<>();
        for (String stage : List.of("analyzable", "convertible", "bytecodeValidated", "clientInitializes", "serverInitializes", "clientBehavior", "serverBehavior", "multiplayer"))
            stages.put(stage, Map.of("status", "NOT_RUN", "evidence", List.of()));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", 1); value.put("id", "owned-corrupt"); value.put("kind", "PROJECT_FIXTURE");
        value.put("projectKey", "unit-test"); value.put("version", "fixture-r1");
        value.put("artifact", Map.of("fileName", original.getFileName().toString(), "sha256", CorpusRunner.hash(original), "sizeBytes", declaredSize));
        value.put("authorization", Map.of("basis", "PROJECT_LOCAL_REQUEST", "license", "All Rights Reserved", "redistributeBinary", false, "evidence", List.of("authorization")));
        value.put("source", source); value.put("dependencies", List.of()); value.put("detectedFeatures", List.of()); value.put("blockers", List.of("INTENTIONALLY_CORRUPT_ZIP"));
        value.put("output", outputDescriptor); value.put("stages", stages); value.put("evidence", List.of(proof));
        CorpusJson.writeCanonicalCreateNew(record, value);
        CorpusJson.writeCanonicalCreateNew(corpus.resolve("catalog.json"), Map.of("schemaVersion", 1, "corpusVersion", "unit-test-v1",
                "records", listed ? List.of("records/reviewed.json") : List.of()));
        Files.createDirectory(corpus.resolve("snapshots"));
        CorpusJson.writeCanonicalCreateNew(corpus.resolve("snapshots/unit-test-v1.json"), Map.of("schemaVersion", 1, "corpusVersion", "unit-test-v1",
                "records", listed ? Map.of("records/reviewed.json", CorpusRunner.hash(record)) : Map.of()));
    }
}
