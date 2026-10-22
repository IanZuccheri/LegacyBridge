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
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic evidence only; no fixture class, Minecraft code or native behavior is executed. */
class CompositionObservationVerifierTest {
    @TempDir Path root;
    private Map<String, Object> index, artifacts, proof, receipt, runs;
    private final Map<String, Map<String, Object>> traces = new LinkedHashMap<>();
    private static final String EXPORT = "composition-qa/run-synthetic/";
    private static final String TOKEN = "legacybridge_composed:count_token";

    @BeforeEach void prepare() throws Exception {
        Files.createDirectories(root.resolve(EXPORT));
        artifacts = map();
        for (String role : List.of("original", "candidate", "runtime")) {
            String path = role.equals("candidate") ? EXPORT + "candidate.jar" : role + ".jar";
            Files.writeString(root.resolve(path), "inert synthetic " + role + " bytes, not a mod");
            artifacts.put(role, file(path));
        }
        proof = map("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_CANDIDATE",
                "status", "CANDIDATE_VALIDATED", "candidateIdentity", "composed-logic-recipes-v1",
                "modId", "legacybridge_composed", "modVersion", "1.0.0",
                "input", map("sha256", hashOf("original")),
                "candidate", map("sha256", hashOf("candidate"), "bytes", obj(artifacts.get("candidate")).get("bytes")),
                "runtimeRequired", "legacybridge_runtime", "runtimeVersion", "0.1.1-SNAPSHOT",
                "targetMinecraft", "1.20.1", "targetForge", "47.4.23", "publicationAllowed", false,
                "bytecodeExecuted", false, "transformationsCommitted", false, "runtimeValidation", "NOT_RUN");
        receipt = map("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_QA_EXPORT", "status", "EXPORTED_FOR_QA",
                "sourceSha256", hashOf("original"), "candidateSha256", hashOf("candidate"),
                "files", List.of("candidate.jar", "proof.json", "receipt.json"),
                "installationAllowed", false, "runtimeValidation", "NOT_RUN");
        proof.put("preservedClassCount", 4); proof.put("preservedMethodCount", 10); proof.put("replacedClassCount", 0);
        proof.put("bytecodeValidation", map("status", "PASSED_STATIC_REOPENED", "modClassesLoaded", false, "offlineLinkageVerified", true));
        proof.put("candidateArchiveValidation", map("status", "PASSED_REOPENED", "ownershipAndHashes", true, "centralDirectoryAndCrc", true));
        for (String phase : List.of("sourceOfflineLinkage", "targetOfflineLinkage"))
            proof.put(phase, map("status", "PASSED_SCOPED", "classesExecuted", false));
        syncProof();
        runs = map();
        for (String role : List.of("source", "target")) {
            Files.writeString(root.resolve(role + "-probe.jar"), "inert synthetic " + role + " probe");
            Map<String, Object> run = map("id", "run-" + role, "probe", file(role + "-probe.jar"));
            runs.put(role, run);
            boolean source = role.equals("source");
            Map<String, Object> bindings = map("fixtureSha256", hashOf(source ? "original" : "candidate"),
                    "probeSha256", obj(run.get("probe")).get("sha256"), "runtimeSha256", source ? null : hashOf("runtime"),
                    "receiptSha256", source ? null : hashOf("receipt"));
            Map<String, Object> mods = map("minecraft", source ? "1.16.5" : "1.20.1",
                    "forge", source ? "36.2.42" : "47.4.23", "legacybridge_composed", "1.0.0",
                    "legacybridge_composition_probe", "1.0.0");
            if (!source) mods.put("legacybridge_runtime", "0.1.1-SNAPSHOT");
            traces.put(role, map("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_OBSERVATION",
                    "oracle", CompositionObservationVerifier.ORACLE, "runId", run.get("id"),
                    "role", source ? "SOURCE" : "TARGET", "status", "CAPTURED",
                    "environment", map("minecraft", mods.get("minecraft"), "forge", mods.get("forge"),
                            "javaMajor", 17, "physicalSide", "DEDICATED_SERVER", "logicalSide", "SERVER", "serverThread", true),
                    "before", bindings, "after", new LinkedHashMap<>(bindings), "loadedMods", mods, "behavior", behavior()));
            syncTrace(role);
        }
        index = map("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_OBSERVATION_INDEX",
                "oracle", CompositionObservationVerifier.ORACLE, "artifacts", artifacts, "runs", runs);
    }

    @Test void completeSyntheticPairChecksRecordedValuesButNeverProvesNativeExecution() throws Exception {
        writeIndex();
        var result = verify();
        assertEquals(CompositionObservationVerifier.State.RECORDED_MATCH_ONLY, result.status());
        assertEquals(11, result.files());
        assertEquals(false, result.toTree().get("gameExecutionVerified"));
        assertEquals(false, result.toTree().get("installationAllowed"));
        assertTrue(result.toTree().get("pending").toString().contains("COMPOSE_01_ACCEPTANCE"));
        assertEquals(hashOf("candidate"), result.candidateSha256());
    }
    @Test void absentRunsAreNotRunNotAnAcceptedEmptyComparison() throws Exception {
        index.put("runs", null); writeIndex();
        var result = verify();
        assertEquals(CompositionObservationVerifier.State.NOT_RUN, result.status());
        assertEquals(5, result.files());
    }
    @Test void readOnlyAndRepeatable() throws Exception {
        writeIndex(); Map<Path, String> before = hashes();
        assertEquals(verify(), verify()); assertEquals(before, hashes());
    }
    @Test void missingHalfOfPairIsBlockedNotNotRun() throws Exception { runs.remove("target"); refuses("KEYS"); }
    @Test void nullHalfIsBlocked() throws Exception { runs.put("source", null); refuses("OBJECT"); }
    @Test void unknownIndexFieldIsBlocked() throws Exception { index.put("passed", true); refuses("KEYS"); }
    @Test void unknownOracleIsBlocked() throws Exception { index.put("oracle", "arbitrary-mods"); refuses("ORACLE"); }
    @Test void newerSchemaIsBlocked() throws Exception { index.put("schemaVersion", 2); refuses("INDEX_SCHEMA"); }
    @Test void missingArtifactIsNotSkipped() throws Exception { Files.delete(root.resolve("original.jar")); refuses("regular file"); }
    @Test void corruptedSelectedArtifactIsBlocked() throws Exception {
        byte[] bytes = Files.readAllBytes(root.resolve("original.jar")); bytes[0] ^= 1;
        Files.write(root.resolve("original.jar"), bytes); refuses("FILE_HASH");
    }
    @Test void changedArtifactSizeIsBlocked() throws Exception { Files.writeString(root.resolve("runtime.jar"), "changed"); refuses("FILE_SIZE"); }
    @Test void rehashedSourceCannotReuseProof() throws Exception {
        Files.writeString(root.resolve("original.jar"), "new source"); artifacts.put("original", file("original.jar")); refuses("RECEIPT_SOURCE");
    }
    @Test void proofSourceDisagreesWithReceipt() throws Exception { proof.put("input", map("sha256", "0".repeat(64))); syncProof(); refuses("PROOF_SOURCE"); }
    @Test void runtimeRevisionDisagrees() throws Exception { proof.put("runtimeVersion", "0.2.0"); syncProof(); refuses("PROOF_RUNTIME_VERSION"); }
    @Test void proofCannotPretendToPublish() throws Exception { proof.put("publicationAllowed", true); syncProof(); refuses("PROOF_AUTHORITY"); }
    @Test void failedProofCannotBecomeRuntimeEvidence() throws Exception { proof.put("status", "BLOCKED"); syncProof(); refuses("PROOF_STATUS"); }
    @Test void replacementBootstrapCannotClaimBodyPreservation() throws Exception { proof.put("replacedClassCount", 1); syncProof(); refuses("PROOF_REPLACEMENT"); }
    @Test void absentBytecodeProofIsBlocked() throws Exception { proof.remove("bytecodeValidation"); syncProof(); refuses("OBJECT"); }
    @Test void failedArchiveProofIsBlocked() throws Exception { obj(proof.get("candidateArchiveValidation")).put("ownershipAndHashes", false); syncProof(); refuses("PROOF_ARCHIVE"); }
    @Test void failedLinkageIsBlocked() throws Exception { obj(proof.get("targetOfflineLinkage")).put("status", "FAILED"); syncProof(); refuses("PROOF_LINKAGE"); }
    @Test void receiptCannotAuthorizeInstallation() throws Exception {
        receipt.put("installationAllowed", true); syncReceipt(); refuses("RECEIPT_AUTHORITY");
    }
    @Test void unexpectedPrivateFileBlocksHandoff() throws Exception {
        Files.writeString(root.resolve(EXPORT + "partial.tmp"), "unknown"); refuses("QA_ENTRY_SET");
    }
    @Test void pendingDirectoryCannotBeTreatedAsExport() throws Exception {
        Files.createDirectories(root.resolve("composition-qa/.pending-test"));
        for (String role : List.of("candidate", "proof", "receipt")) {
            Map<String, Object> file = obj(artifacts.get(role));
            Path old = root.resolve((String) file.get("path"));
            String path = "composition-qa/.pending-test/" + old.getFileName();
            Files.copy(old, root.resolve(path)); file.put("path", path);
        }
        refuses("QA_HANDOFF");
    }
    @ParameterizedTest @ValueSource(strings = {"../original.jar", "/original.jar", "a//b.jar", "a/./b.jar", "a/../b.jar", "original.jar:stream", "a\\b.jar", "a./b.jar"})
    void unsafePathsAreBlocked(String path) throws Exception { obj(artifacts.get("original")).put("path", path); refuses("PATH"); }
    @Test void hardlinkAliasIsBlockedEvenWithDistinctNames() throws Exception {
        Files.createLink(root.resolve("runtime-alias.jar"), root.resolve("original.jar"));
        artifacts.put("runtime", file("runtime-alias.jar")); refuses("FILE_ALIAS");
    }
    @Test void oversizedDeclaredFileIsBlocked() throws Exception { obj(artifacts.get("original")).put("bytes", (128L << 20) + 1); refuses("BYTE_BUDGET"); }
    @Test void fractionalByteCountIsBlocked() throws Exception { obj(artifacts.get("original")).put("bytes", 0.5); refuses("NUMBER"); }
    @Test void booleanIsNotAnInteger() throws Exception { index.put("schemaVersion", true); refuses("INDEX_SCHEMA"); }
    @Test void malformedHashIsBlocked() throws Exception { obj(artifacts.get("original")).put("sha256", "g".repeat(64)); refuses("HASH_FORMAT"); }
    @Test void wrongPhysicalSideIsBlocked() throws Exception { mutate("environment", m -> m.put("physicalSide", "CLIENT")); refuses("SIDE"); }
    @Test void wrongLogicalSideIsBlocked() throws Exception { mutate("environment", m -> m.put("logicalSide", "CLIENT")); refuses("SIDE"); }
    @Test void wrongThreadIsBlocked() throws Exception { mutate("environment", m -> m.put("serverThread", false)); refuses("THREAD"); }
    @Test void wrongForgePatchIsBlocked() throws Exception { mutate("environment", m -> m.put("forge", "47.0.0")); refuses("BASELINE"); }
    @Test void untestedJavaRuntimeIsBlocked() throws Exception { mutate("environment", m -> m.put("javaMajor", 8)); refuses("JAVA_BASELINE"); }
    @Test void unexpectedLoadedModIsBlocked() throws Exception { mutate("loadedMods", m -> m.put("extra", "1.0")); refuses("LOADED_COHORT"); }
    @Test void missingRuntimeIsBlocked() throws Exception { mutate("loadedMods", m -> m.remove("legacybridge_runtime")); refuses("LOADED_COHORT"); }
    @ParameterizedTest @ValueSource(strings = {"fixtureSha256", "probeSha256", "runtimeSha256", "receiptSha256"})
    void allBeforeAndAfterArtifactsMustMatchSelection(String field) throws Exception {
        mutate("after", m -> m.put(field, "0".repeat(64))); refuses("TRACE_");
    }
    @Test void failedTraceIsBlocked() throws Exception { traces.get("target").put("status", "FAILED"); syncTrace("target"); refuses("TRACE_STATUS"); }
    @Test void exchangedRolesAreBlocked() throws Exception { traces.get("target").put("role", "SOURCE"); syncTrace("target"); refuses("TRACE_ROLE"); }
    @Test void duplicateRunIdentityIsBlocked() throws Exception {
        obj(runs.get("target")).put("id", "run-source"); traces.get("target").put("runId", "run-source"); syncTrace("target"); refuses("RUN_REUSE");
    }
    @Test void changedStackLimitIsBlocked() throws Exception { mutate("behavior", m -> m.put("maxStackSize", 64)); refuses("STACK_SIZE"); }
    @Test void replacedOwnedClassIsBlocked() throws Exception { mutate("behavior", m -> m.put("itemClass", "generated.Bootstrap")); refuses("OWNED_CLASS"); }
    @Test void extraHelperCallsAreBlocked() throws Exception { mutate("behavior", m -> obj(((List<?>) m.get("foil")).get(0)).put("callsAfter", 2)); refuses("HELPER_CALLS"); }
    @Test void missingFoilCaseIsBlocked() throws Exception { mutate("behavior", m -> ((List<?>) m.get("foil")).remove(3)); refuses("ARRAY_SIZE"); }
    @Test void falseFoilResultIsBlocked() throws Exception { mutate("behavior", m -> obj(((List<?>) m.get("foil")).get(0)).put("foil", false)); refuses("FOIL_RESULT"); }
    @Test void bothSidesAgreeingOnWrongBehaviorStillFailOracle() throws Exception {
        for (String role : List.of("source", "target")) { obj(traces.get(role).get("behavior")).put("maxStackSize", 64); syncTrace(role); }
        refuses("STACK_SIZE");
    }
    @Test void lostSuperShortCircuitIsBlocked() throws Exception { mutate("behavior", m -> obj(m.get("enchanted")).put("callsAfter", 5)); refuses("SUPER_SHORT_CIRCUIT"); }
    @Test void emptyTagIsBlocked() throws Exception { cooking(m -> m.put("tagItems", List.of())); refuses("TAG_CONTENTS"); }
    @Test void recipeXpCannotBeLostOrInvented() throws Exception { cooking(m -> m.put("recipeExperience", 1)); refuses("RECIPE_XP"); }
    @Test void alteredRecipeTimeIsBlocked() throws Exception { cooking(m -> m.put("recipeTicks", 200)); refuses("RECIPE_TICKS"); }
    @Test void missingTickIsBlocked() throws Exception { cooking(m -> ((List<?>) m.get("samples")).remove(39)); refuses("ARRAY_SIZE"); }
    @Test void duplicateTickIsBlocked() throws Exception { sample(40, m -> m.put("tick", 39)); refuses("TICK_ORDER"); }
    @Test void earlyOutputIsBlocked() throws Exception { sample(39, m -> m.put("outputCount", 1)); refuses("COOK_OUTPUT"); }
    @Test void delayedOutputIsBlocked() throws Exception { sample(40, m -> m.put("outputCount", 0)); refuses("COOK_OUTPUT"); }
    @Test void unconsumedInputIsBlocked() throws Exception { sample(40, m -> m.put("inputCount", 1)); refuses("COOK_CONSUMPTION"); }
    @Test void postCompletionDuplicationIsBlocked() throws Exception { sample(120, m -> m.put("outputCount", 2)); refuses("COOK_OUTPUT"); }
    @Test void unconsumedFuelIsBlocked() throws Exception { sample(1, m -> m.put("fuelCount", 1)); refuses("COOK_FUEL"); }
    @Test void unknownBehaviorFieldIsNotIgnored() throws Exception { mutate("behavior", m -> m.put("visualPass", true)); refuses("KEYS"); }
    @Test void missingLogCompletionIsBlocked() throws Exception { replaceLog("no completion\n"); refuses("LOG_COMPLETION"); }
    @Test void failedProbeMarkerIsBlocked() throws Exception { replaceLog("LEGACYBRIDGE_COMPOSE_CAPTURE_FAILED\n"); refuses("LOG_MARKER"); }
    @Test void duplicateLogCompletionIsBlocked() throws Exception {
        String text = Files.readString(root.resolve("target.log")); replaceLog(text + text); refuses("LOG_COMPLETION");
    }
    @Test void staleObservationDigestInLogIsBlocked() throws Exception {
        String text = Files.readString(root.resolve("target.log")); replaceLog(text.replaceFirst("sha256=[a-f0-9]+", "sha256=" + "0".repeat(64))); refuses("LOG_MARKER");
    }
    @Test void laterProbeFailureInvalidatesEarlierSuccessMarker() throws Exception {
        replaceLog(Files.readString(root.resolve("target.log")) + "LEGACYBRIDGE_COMPOSE_SHUTDOWN_FAILED\n"); refuses("LOG_MARKER");
    }
    @Test void excessiveLogLinesAreBlockedWithoutMaterializingAllLines() throws Exception {
        replaceLog("\n".repeat(100_001)); refuses("LOG_LINE_BUDGET");
    }
    @Test void excessiveLogBytesAreBlocked() throws Exception {
        replaceLog("x".repeat((8 << 20) + 1)); refuses("CAPTURE_BUDGET");
    }
    @Test void invalidUtf8IsBlocked() throws Exception {
        Files.write(root.resolve("target.log"), new byte[]{(byte) 0xc3, 0x28}); obj(runs.get("target")).put("log", file("target.log"));
        writeIndex(); assertThrows(IOException.class, this::verify);
    }
    @Test void duplicateJsonKeyIsBlocked() throws Exception {
        Files.writeString(root.resolve("target.json"), "{\"schemaVersion\":1,\"schemaVersion\":1}");
        obj(runs.get("target")).put("observation", file("target.json")); refuses("OBS_JSON");
    }

    private void cooking(Consumer<Map<String, Object>> change) throws Exception { mutate("behavior", m -> change.accept(obj(m.get("cooking")))); }
    private void sample(int tick, Consumer<Map<String, Object>> change) throws Exception { cooking(m -> change.accept(obj(((List<?>) m.get("samples")).get(tick)))); }
    private void mutate(String section, Consumer<Map<String, Object>> change) throws Exception { change.accept(obj(traces.get("target").get(section))); syncTrace("target"); }
    private void refuses(String message) throws Exception { writeIndex(); assertTrue(assertThrows(IOException.class, this::verify).getMessage().contains(message)); }
    private CompositionObservationVerifier.Summary verify() throws IOException { return CompositionObservationVerifier.verify(root, "index.json"); }
    private void writeIndex() throws IOException { write("index.json", index); }
    private void syncProof() throws Exception {
        write(EXPORT + "proof.json", proof); artifacts.put("proof", file(EXPORT + "proof.json"));
        receipt.put("proofSha256", hashOf("proof")); syncReceipt();
    }
    private void syncReceipt() throws Exception { write(EXPORT + "receipt.json", receipt); artifacts.put("receipt", file(EXPORT + "receipt.json")); }
    private void syncTrace(String role) throws Exception {
        write(role + ".json", traces.get(role)); Map<String, Object> run = obj(runs.get(role));
        run.put("observation", file(role + ".json"));
        Files.writeString(root.resolve(role + ".log"), "[Server thread/INFO] LEGACYBRIDGE_COMPOSE_CAPTURE_OK run="
                + run.get("id") + " sha256=" + obj(run.get("observation")).get("sha256") + "\n");
        run.put("log", file(role + ".log"));
    }
    private void replaceLog(String text) throws Exception { Files.writeString(root.resolve("target.log"), text); obj(runs.get("target")).put("log", file("target.log")); }
    private String hashOf(String role) { return (String) obj(artifacts.get(role)).get("sha256"); }
    private Map<String, Object> file(String path) throws Exception { return map("path", path, "sha256", digest(root.resolve(path)), "bytes", Files.size(root.resolve(path))); }
    private static String digest(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private void write(String path, Map<String, Object> value) throws IOException { Files.writeString(root.resolve(path), CorpusJson.canonicalJson(value)); }
    private Map<Path, String> hashes() throws Exception {
        Map<Path, String> result = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) { for (Path path : paths.filter(Files::isRegularFile).toList()) result.put(path, digest(path)); }
        return result;
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> obj(Object value) { return (Map<String, Object>) value; }
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> value = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) value.put((String) pairs[i], pairs[i + 1]);
        return value;
    }
    private static Map<String, Object> behavior() {
        List<Map<String, Object>> foil = new ArrayList<>(), samples = new ArrayList<>();
        for (int count = 1; count <= 4; count++) foil.add(map("count", count, "foil", count % 2 == 1, "callsBefore", count - 1, "callsAfter", count));
        for (int tick = 0; tick <= 120; tick++) samples.add(map("tick", tick,
                "inputId", tick < 40 ? "minecraft:iron_ingot" : "minecraft:air", "inputCount", tick < 40 ? 1 : 0,
                "fuelId", tick == 0 ? "minecraft:coal" : "minecraft:air", "fuelCount", tick == 0 ? 1 : 0,
                "outputId", tick < 40 ? "minecraft:air" : TOKEN, "outputCount", tick < 40 ? 0 : 1));
        return map("registryId", TOKEN, "itemClass", "dev.legacybridge.fixture.composed.ComposedItem", "maxStackSize", 16,
                "initialCalls", 0, "foil", foil,
                "enchanted", map("count", 2, "enchantment", "minecraft:unbreaking", "level", 1,
                        "isEnchanted", true, "foil", true, "callsBefore", 4, "callsAfter", 4),
                "cooking", map("recipeId", TOKEN, "recipeType", "minecraft:smelting",
                        "ingredientTag", "legacybridge_composed:token_material", "tagItems", List.of("minecraft:iron_ingot"),
                        "recipeTicks", 40, "recipeExperience", 0.0, "samples", samples));
    }
}
