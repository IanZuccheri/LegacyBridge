package dev.legacybridge.corpus;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CorpusVerifierTest {
    @TempDir Path project;
    Path corpus;
    private static final String SOURCE = "a".repeat(64);
    private static final String TARGET = "b".repeat(64);
    private static final Map<String, String> STAGES = Map.of(
            "analyzable", "ANALYSIS", "convertible", "CONVERSION", "bytecodeValidated", "BYTECODE",
            "clientInitializes", "CLIENT_INITIALIZATION", "serverInitializes", "SERVER_INITIALIZATION",
            "clientBehavior", "CLIENT_BEHAVIOR", "serverBehavior", "SERVER_BEHAVIOR", "multiplayer", "MULTIPLAYER");

    @BeforeEach void prepare() throws IOException {
        corpus = Files.createDirectory(project.resolve("corpus"));
        Files.createDirectory(corpus.resolve("records"));
        Files.createDirectory(corpus.resolve("evidence"));
    }

    @Test void verifiesCompleteHistoricalChainWithoutRunningAnyMod() throws Exception {
        var record = fixture("example", SOURCE);
        converted(record);
        for (String stage : List.of("clientInitializes", "serverInitializes", "clientBehavior", "serverBehavior", "multiplayer")) pass(record, stage);
        write(record);
        var summary = new CorpusVerifier().verify(corpus);
        assertEquals(1, summary.records());
        assertEquals(1, summary.projectFixtures());
        assertEquals(0, summary.realArtifacts());
        assertTrue(summary.missingLocalEvidence().isEmpty());
        assertTrue(summary.fixturePassedStages().values().stream().allMatch(count -> count == 1));
        assertTrue(summary.realPassedStages().values().stream().allMatch(count -> count == 0));
        assertFalse((Boolean) summary.toTree().get("urlContentVerified"));
        assertThrows(UnsupportedOperationException.class, () -> summary.fixturePassedStages().put("fake", 99));
    }

    @Test void separatesDistinctRealProjectsAndExactArtifactsFromFixtures() throws Exception {
        var fixture = fixture("fixture", SOURCE); converted(fixture);
        var real1 = real("real-one", "project-a", "c".repeat(64)); pass(real1, "analyzable");
        var real2 = real("real-two", "project-a", "d".repeat(64));
        var real3 = real("real-three", "project-b", "e".repeat(64)); pass(real3, "analyzable");
        write(fixture, real1, real2, real3);
        var summary = new CorpusVerifier().verify(corpus);
        assertEquals(4, summary.records()); assertEquals(1, summary.projectFixtures());
        assertEquals(3, summary.realArtifacts()); assertEquals(2, summary.distinctRealProjects());
        assertEquals(2, summary.realPassedStages().get("analyzable"));
        assertEquals(0, summary.realPassedStages().get("convertible"));
        assertEquals(1, summary.fixturePassedStages().get("convertible"));
        assertFalse(CorpusJson.canonicalJson(summary.toTree()).contains("percentage"));
    }

    @Test void missingHistoricalEvidenceIsExplicitAndNeverPretendsBytesWereVerified() throws Exception {
        var record = fixture("missing", SOURCE); converted(record);
        Files.delete(project.resolve((String) evidence(record).get(1).get("path")));
        write(record);
        var result = new CorpusVerifier().verify(corpus);
        assertEquals(List.of("missing/analyzable:corpus/evidence/missing-analyzable.json"), result.missingLocalEvidence());
        assertEquals(1, result.fixturePassedStages().get("analyzable"), "Recorded historical attestation, not a newly passed test");
    }

    @Test void rejectsAvailableEvidenceWithWrongHash() throws Exception {
        var record = fixture("changed", SOURCE);
        Files.writeString(project.resolve((String) evidence(record).get(0).get("path")), "changed");
        write(record); rejected("Evidence file SHA-256 mismatch");
    }

    @Test void rejectsEvidenceBoundToDifferentSourceArtifact() throws Exception {
        var record = fixture("source", SOURCE); converted(record);
        evidence(record).get(1).put("sourceSha256", "c".repeat(64));
        write(record); rejected("different source artifact");
    }

    @Test void rejectsEvidenceBoundToDifferentOutputArtifact() throws Exception {
        var record = fixture("target", SOURCE); converted(record);
        evidence(record).get(2).put("targetSha256", "c".repeat(64));
        write(record); rejected("different or absent converted artifact");
    }

    @Test void rejectsPassedStageWithNoEvidence() throws Exception {
        var record = fixture("empty-proof", SOURCE);
        stage(record, "analyzable").put("status", "PASSED");
        write(record); rejected("matching typed");
    }

    @Test void screenshotAloneCannotProveBytecodeOrRuntimeBehavior() throws Exception {
        var record = fixture("screenshot", SOURCE); converted(record);
        evidence(record).get(3).put("type", "SCREENSHOT");
        write(record); rejected("matching typed");
    }

    @ParameterizedTest @ValueSource(strings = {"TEST_COUNT", "MAPPING_RESOLUTION", "MAPPINGS", "BUILD"})
    void testCountOrMappingsCannotBePromotedIntoStageProof(String type) throws Exception {
        var record = fixture("not-proof", SOURCE); pass(record, "analyzable");
        evidence(record).get(1).put("type", type);
        write(record); rejected("Unknown evidence type");
    }

    @Test void rejectsBytecodeWithoutConversion() throws Exception {
        var record = fixture("broken-static", SOURCE); converted(record);
        stage(record, "convertible").put("status", "PARTIAL");
        write(record); rejected("requires a successful conversion");
    }

    @Test void rejectsConversionWithoutAnalysis() throws Exception {
        var record = fixture("no-analysis", SOURCE); converted(record);
        stage(record, "analyzable").put("status", "PARTIAL");
        write(record); rejected("requires analysis");
    }

    @Test void rejectsClientBehaviorWithoutClientInitialization() throws Exception {
        var record = fixture("no-client-init", SOURCE); converted(record); pass(record, "clientBehavior");
        write(record); rejected("requires client initialization");
    }

    @Test void rejectsServerBehaviorWithoutServerInitialization() throws Exception {
        var record = fixture("no-server-init", SOURCE); converted(record); pass(record, "serverBehavior");
        write(record); rejected("requires server initialization");
    }

    @Test void rejectsMultiplayerWithoutBothInitializationProofs() throws Exception {
        var record = fixture("not-multiplayer", SOURCE); converted(record); pass(record, "clientInitializes"); pass(record, "multiplayer");
        write(record); rejected("requires both initializations");
    }

    @Test void rejectsConvertedOutputWithoutCompleteBytecodeGate() throws Exception {
        var record = fixture("invalid-output", SOURCE); converted(record);
        stage(record, "bytecodeValidated").put("status", "FAILED");
        write(record); rejected("complete static proof chain");
    }

    @Test void rejectsDuplicateSourceHashEvenAcrossFixtureAndRealMod() throws Exception {
        write(fixture("fixture", SOURCE), real("real", "project-a", SOURCE));
        rejected("Duplicate artifact SHA-256");
    }

    @Test void rejectsDuplicateRecordIdentity() throws Exception {
        var first = fixture("first", SOURCE);
        var second = fixture("second", "c".repeat(64)); second.put("id", "first");
        CorpusJson.writeCanonicalCreateNew(corpus.resolve("records/first.json"), first);
        CorpusJson.writeCanonicalCreateNew(corpus.resolve("records/second.json"), second);
        catalog(List.of("records/first.json", "records/second.json"));
        rejected("Duplicate record id");
    }

    @Test void rejectsDuplicateEvidenceIdAndUnknownReferences() throws Exception {
        var record = fixture("duplicate-evidence", SOURCE);
        evidence(record).add(new LinkedHashMap<>(evidence(record).get(0)));
        write(record); rejected("Duplicate evidence id");
    }

    @Test void rejectsUnknownEvidenceReference() throws Exception {
        var record = fixture("dangling", SOURCE);
        stage(record, "analyzable").put("evidence", List.of("does-not-exist"));
        write(record); rejected("Unknown evidence reference");
    }

    @ParameterizedTest @ValueSource(strings = {"../private.json", "/absolute.json", "C:/private.json", "folder\\private.json", "./private.json", "folder//private.json", "folder./private.json", "CON", "folder/NUL.txt", "folder/*.json"})
    void rejectsUnsafeLocalEvidencePathsEvenWhenAbsent(String path) throws Exception {
        var record = fixture("unsafe", SOURCE); evidence(record).get(0).put("path", path);
        write(record); rejected("Unsafe relative corpus path");
    }

    @Test void rejectsTraversalInCatalogRecords() throws Exception {
        catalog(List.of("../outside.json")); rejected("Unsafe relative corpus path");
    }

    @Test void missingEvidenceCannotPassThroughAnExistingRegularFileAsDirectory() throws Exception {
        var record = fixture("bad-parent", SOURCE);
        Files.writeString(project.resolve("not-directory"), "owned test file");
        evidence(record).get(0).put("path", "not-directory/proof.json");
        write(record); rejected("Evidence parent is not a directory");
    }

    @Test void rejectsDuplicateCatalogPaths() throws Exception {
        var record = fixture("duplicate-path", SOURCE);
        CorpusJson.writeCanonicalCreateNew(corpus.resolve("records/one.json"), record);
        catalog(List.of("records/one.json", "records/one.json")); rejected("Duplicate record path");
    }

    @Test void refusesUnauthorizedRealModEvenWithLocalFixturePermission() throws Exception {
        var record = fixture("unauthorized", SOURCE); record.put("kind", "REAL_MOD");
        write(record); rejected("REAL_MOD requires SPDX");
    }

    @Test void requiresAuthorizationEvidenceRatherThanLicenseStringAlone() throws Exception {
        var record = real("license-only", "project-a", SOURCE);
        object(record, "authorization").put("evidence", List.of());
        write(record); rejected("Authorization requires");
    }

    @Test void refusesBinaryRedistributionAuthority() throws Exception {
        var record = real("redistribution", "project-a", SOURCE);
        object(record, "authorization").put("redistributeBinary", true);
        write(record); rejected("never grants binary redistribution");
    }

    @Test void acceptsUnlicenseAndAdjacentBuildWithoutInventingForge36() throws Exception {
        var record = real("adjacent-build", "appleskin", SOURCE);
        object(record, "authorization").put("license", "Unlicense");
        object(record, "source").put("forgeBuild", "35.1.4");
        object(record, "source").put("forgeVersionEvidence", "UPSTREAM_PINNED_BUILD_ADJACENT_SUPPORTED_1_16_5");
        record.put("structuredMetadata", Map.of("declaredDependencies", List.of(Map.of("modId", "forge", "mandatory", true))));
        record.put("analysisSummary", Map.of("truncated", false, "modId", "appleskin"));
        write(record); assertEquals(1, new CorpusVerifier().verify(corpus).realArtifacts());
    }

    @ParameterizedTest @ValueSource(strings = {"http://github.com/owner/repo", "https://user:secret@github.com/owner/repo", "https://github.com/owner/repo?token=secret", "https://github.com/owner/repo#fragment", "https://github.com:8443/owner/repo"})
    void refusesUnsafeAuthorizationUrl(String url) throws Exception {
        var record = real("unsafe-url", "project-a", SOURCE); evidence(record).get(0).put("url", url);
        write(record); rejected("Evidence URL must be HTTPS");
    }

    @Test void refusesUnsupportedSpdxExpressionsRatherThanGuessingPermission() throws Exception {
        var record = real("license-expression", "project-a", SOURCE);
        object(record, "authorization").put("license", "MIT OR invented-license");
        write(record); rejected("SPDX identifier is outside");
    }

    @Test void rejectsOverBudgetCatalogAndNonNumericSchema() throws Exception {
        catalog(java.util.Collections.nCopies(257, "records/not-read.json"));
        rejected("Invalid or overlong catalog records");
    }

    @Test void rejectsStringSchemaVersion() throws Exception {
        var record = fixture("schema", SOURCE); record.put("schemaVersion", "1");
        write(record); rejected("must be a JSON number");
    }

    private Map<String, Object> fixture(String id, String sha) throws Exception {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("schemaVersion", 1); record.put("id", id); record.put("kind", "PROJECT_FIXTURE"); record.put("projectKey", id); record.put("version", "fixture-r1");
        record.put("artifact", Map.of("fileName", id + ".jar", "sha256", sha, "sizeBytes", 123));
        record.put("authorization", new LinkedHashMap<>(Map.of("basis", "PROJECT_LOCAL_REQUEST", "license", "All Rights Reserved", "redistributeBinary", false, "evidence", List.of("authorization"))));
        var source = new LinkedHashMap<String, Object>(); source.put("loader", "forge"); source.put("minecraftVersions", List.of("1.16.5")); source.put("forgeBuild", null); source.put("forgeVersionEvidence", "METADATA_RANGE_ONLY"); record.put("source", source);
        record.put("dependencies", List.of("forge [36,)", "minecraft [1.16.5]")); record.put("detectedFeatures", List.of()); record.put("blockers", List.of());
        var output = new LinkedHashMap<String, Object>(); output.put("sha256", null); output.put("status", "NOT_RUN"); record.put("output", output);
        var stages = new HashMap<String, Object>(); STAGES.keySet().forEach(key -> stages.put(key, new LinkedHashMap<>(Map.of("status", "NOT_RUN", "evidence", List.of())))); record.put("stages", stages);
        record.put("evidence", new ArrayList<Map<String, Object>>());
        evidence(record).add(proof(record, "authorization", "AUTHORIZATION", null));
        return record;
    }

    private Map<String, Object> real(String id, String projectKey, String sha) throws Exception {
        var record = fixture(id, sha); record.put("kind", "REAL_MOD"); record.put("projectKey", projectKey); record.put("version", "1.0.0");
        object(record, "authorization").put("basis", "SPDX_LICENSE"); object(record, "authorization").put("license", "MIT");
        var auth = evidence(record).get(0); auth.remove("path"); auth.put("url", "https://github.com/example/project/blob/0000000000000000000000000000000000000000/LICENSE");
        return record;
    }
    private void converted(Map<String, Object> record) throws Exception {
        object(record, "output").put("status", "CONVERTED"); object(record, "output").put("sha256", TARGET);
        for (String stage : List.of("analyzable", "convertible", "bytecodeValidated")) pass(record, stage);
    }
    private void pass(Map<String, Object> record, String stage) throws Exception {
        evidence(record).add(proof(record, stage, STAGES.get(stage), stage.equals("analyzable") ? null : TARGET));
        stage(record, stage).put("status", "PASSED"); stage(record, stage).put("evidence", List.of(stage));
    }
    private Map<String, Object> proof(Map<String, Object> record, String id, String type, String target) throws Exception {
        String relative = "corpus/evidence/" + record.get("id") + "-" + id + ".json";
        Path path = project.resolve(relative);
        CorpusJson.writeCanonicalCreateNew(path, Map.of("syntheticTestProofOnly", true, "type", type));
        var proof = new LinkedHashMap<String, Object>(); proof.put("id", id); proof.put("type", type);
        proof.put("sourceSha256", type.equals("AUTHORIZATION") ? null : object(record, "artifact").get("sha256"));
        proof.put("targetSha256", target); proof.put("path", relative); proof.put("sha256", hash(Files.readAllBytes(path)));
        return proof;
    }
    @SafeVarargs private final void write(Map<String, Object>... records) throws IOException {
        var paths = new ArrayList<String>();
        for (var record : records) { String relative = "records/" + record.get("id") + ".json"; CorpusJson.writeCanonicalCreateNew(corpus.resolve(relative), record); paths.add(relative); }
        catalog(paths);
    }
    private void catalog(List<String> records) throws IOException { CorpusJson.writeCanonicalCreateNew(corpus.resolve("catalog.json"), Map.of("schemaVersion", 1, "corpusVersion", "test-v1", "records", records)); }
    private void rejected(String message) { assertTrue(assertThrows(IOException.class, () -> new CorpusVerifier().verify(corpus)).getMessage().contains(message)); }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Map<String, Object> parent, String key) { return (Map<String, Object>) parent.get(key); }
    private static Map<String, Object> stage(Map<String, Object> record, String key) { return object(object(record, "stages"), key); }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> evidence(Map<String, Object> record) { return (List<Map<String, Object>>) record.get("evidence"); }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
