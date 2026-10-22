package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Inert synthetic files only: neither the invented proof nor exit records attest a native process. */
class CompositionExitVerifierTest {
    private static final String EXPORT = "composition-qa/run-synthetic/", TOKEN = "legacybridge_composed:count_token";
    private static final String SERVER = "[12:34:56] [Server thread/INFO] [minecraft/MinecraftServer]: ";
    private static final String DONE = "[12:34:55] [Server thread/INFO] [minecraft/DedicatedServer]: Done (1.234s)! For help, type \"help\"\n";
    @TempDir Path root;
    private Map<String, Object> observationIndex, exitIndex, artifacts, observationRuns, exitRuns;
    private final Map<String, Map<String, Object>> traces = new LinkedHashMap<>(), exits = new LinkedHashMap<>();

    @BeforeEach void prepareInertRecords() throws Exception {
        Files.createDirectories(root.resolve(EXPORT));
        artifacts = map();
        for (String role : List.of("original", "candidate", "runtime")) {
            String path = role.equals("candidate") ? EXPORT + "candidate.jar" : role + ".jar";
            Files.writeString(root.resolve(path), "inert synthetic " + role + ", native NOT_RUN"); artifacts.put(role, file(path));
        }
        Map<String, Object> proof = map("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_CANDIDATE",
                "status", "CANDIDATE_VALIDATED", "candidateIdentity", "composed-logic-recipes-v1", "modId", "legacybridge_composed", "modVersion", "1.0.0",
                "input", map("sha256", hashOf("original")), "candidate", map("sha256", hashOf("candidate"), "bytes", obj(artifacts.get("candidate")).get("bytes")),
                "runtimeRequired", "legacybridge_runtime", "runtimeVersion", "0.1.1-SNAPSHOT", "targetMinecraft", "1.20.1", "targetForge", "47.4.23",
                "publicationAllowed", false, "bytecodeExecuted", false, "transformationsCommitted", false, "runtimeValidation", "NOT_RUN",
                "preservedClassCount", 4, "preservedMethodCount", 10, "replacedClassCount", 0,
                "bytecodeValidation", map("status", "PASSED_STATIC_REOPENED", "modClassesLoaded", false, "offlineLinkageVerified", true),
                "candidateArchiveValidation", map("status", "PASSED_REOPENED", "ownershipAndHashes", true, "centralDirectoryAndCrc", true));
        for (String phase : List.of("sourceOfflineLinkage", "targetOfflineLinkage")) proof.put(phase, map("status", "PASSED_SCOPED", "classesExecuted", false));
        write(EXPORT + "proof.json", proof); artifacts.put("proof", file(EXPORT + "proof.json"));
        write(EXPORT + "receipt.json", map("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_QA_EXPORT", "status", "EXPORTED_FOR_QA",
                "sourceSha256", hashOf("original"), "candidateSha256", hashOf("candidate"), "proofSha256", hashOf("proof"),
                "files", List.of("candidate.jar", "proof.json", "receipt.json"), "installationAllowed", false, "runtimeValidation", "NOT_RUN"));
        artifacts.put("receipt", file(EXPORT + "receipt.json"));
        observationRuns = map(); exitRuns = map();
        for (String role : List.of("source", "target")) {
            boolean source = role.equals("source");
            Files.writeString(root.resolve(role + "-probe.jar"), "inert synthetic " + role + " probe, native NOT_RUN");
            Map<String, Object> binding = map("fixtureSha256", hashOf(source ? "original" : "candidate"), "probeSha256", digest(root.resolve(role + "-probe.jar")),
                    "runtimeSha256", source ? null : hashOf("runtime"), "receiptSha256", source ? null : hashOf("receipt"));
            Map<String, Object> mods = map("minecraft", source ? "1.16.5" : "1.20.1", "forge", source ? "36.2.42" : "47.4.23",
                    "legacybridge_composed", "1.0.0", "legacybridge_composition_probe", "1.0.0");
            if (!source) mods.put("legacybridge_runtime", "0.1.1-SNAPSHOT");
            Map<String, Object> trace = map("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_OBSERVATION", "oracle", CompositionObservationVerifier.ORACLE,
                    "runId", "run-" + role, "role", source ? "SOURCE" : "TARGET", "status", "CAPTURED", "loadedMods", mods,
                    "environment", map("minecraft", mods.get("minecraft"), "forge", mods.get("forge"), "javaMajor", 17,
                            "physicalSide", "DEDICATED_SERVER", "logicalSide", "SERVER", "serverThread", true),
                    "before", binding, "after", new LinkedHashMap<>(binding), "behavior", behavior());
            traces.put(role, trace); write(role + ".json", trace);
            Files.writeString(root.resolve(role + ".stdout.log"), normalLog(role)); Files.write(root.resolve(role + ".stderr.log"), new byte[0]);
            observationRuns.put(role, map("id", "run-" + role, "probe", file(role + "-probe.jar"), "observation", file(role + ".json"), "log", file(role + ".stdout.log")));
            Map<String, Object> exit = map("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_PROCESS_EXIT", "runId", "run-" + role,
                    "role", source ? "SOURCE" : "TARGET", "stdoutSha256", digest(root.resolve(role + ".stdout.log")),
                    "stderrSha256", digest(root.resolve(role + ".stderr.log")), "observationSha256", digest(root.resolve(role + ".json")),
                    "processExited", true, "exitCode", 0, "stdoutComplete", true, "stderrComplete", true,
                    "timedOut", false, "cancelled", false, "forcedTermination", false, "elapsedMillis", 12_345);
            exits.put(role, exit); write(role + ".exit.json", exit);
            exitRuns.put(role, map("id", "run-" + role, "exit", file(role + ".exit.json"), "stderr", file(role + ".stderr.log")));
        }
        observationIndex = map("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_OBSERVATION_INDEX",
                "oracle", CompositionObservationVerifier.ORACLE, "artifacts", artifacts, "runs", observationRuns);
        exitIndex = map("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_EXIT_INDEX", "oracle", CompositionObservationVerifier.ORACLE,
                "observationIndexSha256", "pending", "runs", exitRuns);
        syncIndexes();
    }

    @Test void completeSyntheticExitPairVerifiesOnlyUnsignedRecords() throws Exception {
        var result = verify();
        assertEquals(CompositionExitVerifier.State.RECORDED_EXIT_ONLY, result.status()); assertEquals(17, result.files());
        assertEquals(false, result.toTree().get("gameExecutionVerified")); assertEquals(false, result.toTree().get("installationAllowed"));
        assertEquals(false, result.toTree().get("launchContractVerified"));
        assertTrue(result.toTree().get("pending").toString().contains("APPROVED_LAUNCH_PLAN_ASSOCIATION"));
        assertTrue(result.toTree().get("pending").toString().contains("INDEPENDENT_PROCESS_OBSERVATION"));
        assertEquals(digest(root.resolve("observations.json")), result.observationIndexSha256());
        assertEquals(digest(root.resolve("exits.json")), result.exitIndexSha256());
        assertEquals(hashOf("original"), result.sourceSha256()); assertEquals(hashOf("candidate"), result.candidateSha256());
    }
    @Test void verificationIsReadOnlyAndRepeatable() throws Exception {
        Map<Path, String> before = hashes(); assertEquals(verify(), verify()); assertEquals(before, hashes());
    }
    @Test void absentObservationRunsAreNotAcceptedAsAnExitComparison() throws Exception {
        observationIndex.put("runs", null); syncIndexes(); refuses("OBSERVATIONS_NOT_RECORDED");
    }
    @Test void wrongBehaviorCannotBypassTheExistingObservationGate() throws Exception {
        obj(traces.get("target").get("behavior")).put("maxStackSize", 64); write("target.json", traces.get("target"));
        obj(observationRuns.get("target")).put("observation", file("target.json"));
        replaceStdout("target", normalLog("target")); refuses("OBSERVATION_REJECTED");
    }
    @ParameterizedTest @ValueSource(strings = {"schemaVersion", "reportType", "oracle"})
    void exactExitIndexIdentityIsRequired(String field) throws Exception {
        exitIndex.put(field, field.equals("schemaVersion") ? 2 : "wrong"); write("exits.json", exitIndex); refuses("EXIT_");
    }
    @Test void unknownExitIndexFieldIsRejected() throws Exception { exitIndex.put("passed", true); write("exits.json", exitIndex); refuses("KEYS"); }
    @Test void missingHalfOfExitPairIsRejected() throws Exception { exitRuns.remove("target"); write("exits.json", exitIndex); refuses("KEYS"); }
    @Test void unknownRoleInExitPairIsRejected() throws Exception { exitRuns.put("client", map()); write("exits.json", exitIndex); refuses("KEYS"); }
    @Test void nullHalfOfExitPairIsRejected() throws Exception { exitRuns.put("target", null); write("exits.json", exitIndex); refuses("OBJECT"); }
    @Test void staleObservationIndexHashIsRejected() throws Exception {
        exitIndex.put("observationIndexSha256", "0".repeat(64)); write("exits.json", exitIndex); refuses("OBSERVATION_INDEX_HASH");
    }
    @Test void differentRunInExitIndexIsRejected() throws Exception {
        obj(exitRuns.get("target")).put("id", "run-another"); write("exits.json", exitIndex); refuses("RUN_BINDING");
    }
    @Test void duplicatedRunIdentityIsRejected() throws Exception {
        obj(exitRuns.get("target")).put("id", "run-source"); write("exits.json", exitIndex); refuses("RUN_ID");
    }
    @Test void unknownExitRunFieldIsRejected() throws Exception {
        obj(exitRuns.get("target")).put("pid", 1); write("exits.json", exitIndex); refuses("KEYS");
    }
    @ParameterizedTest @ValueSource(strings = {"schemaVersion", "reportType", "runId", "role"})
    void exactExitRecordIdentityIsRequired(String field) throws Exception {
        mutateExit("target", row -> row.put(field, field.equals("schemaVersion") ? 2 : "wrong")); refuses("RECORD_");
    }
    @Test void unknownExitRecordFieldIsRejectedRatherThanTreatedAsLaunchApproval() throws Exception {
        mutateExit("target", row -> row.put("launchPlanApproved", true)); refuses("KEYS");
    }
    @ParameterizedTest @ValueSource(strings = {"stdoutSha256", "stderrSha256", "observationSha256"})
    void everyExitBindingMustMatchActualSelectedBytes(String field) throws Exception {
        mutateExit("target", row -> row.put(field, "0".repeat(64))); refuses("HASH");
    }
    @ParameterizedTest @ValueSource(strings = {"processExited", "stdoutComplete", "stderrComplete"})
    void missingTerminationOrEofIsRejected(String field) throws Exception {
        mutateExit("target", row -> row.put(field, false)); refuses("INCOMPLETE_EXIT");
    }
    @ParameterizedTest @ValueSource(strings = {"timedOut", "cancelled", "forcedTermination"})
    void abortedExecutionRecordsAreRejected(String field) throws Exception {
        mutateExit("target", row -> row.put(field, true)); refuses("ABORTED_EXIT");
    }
    @Test void nonZeroExitCannotPassDespiteGoodCaptureAndSavedMarkers() throws Exception {
        mutateExit("target", row -> row.put("exitCode", 1)); refuses("EXIT_CODE");
    }
    @Test void booleansAreNotIntegersOrStringBooleanAliases() throws Exception {
        mutateExit("target", row -> row.put("exitCode", true)); refuses("EXIT_CODE");
        mutateExit("target", row -> { row.put("exitCode", 0); row.put("processExited", "true"); }); refuses("INCOMPLETE_EXIT");
    }
    @ParameterizedTest @ValueSource(longs = {0, -1, 300001})
    void invalidElapsedTimeIsRejected(long elapsed) throws Exception { mutateExit("target", row -> row.put("elapsedMillis", elapsed)); refuses("ELAPSED"); }
    @Test void fractionalElapsedTimeIsRejected() throws Exception { mutateExit("target", row -> row.put("elapsedMillis", 1.5)); refuses("NUMBER"); }
    @Test void maximumElapsedTimeIsAcceptedWithoutARealProcessClaim() throws Exception {
        mutateExit("target", row -> row.put("elapsedMillis", 300_000)); assertEquals(CompositionExitVerifier.State.RECORDED_EXIT_ONLY, verify().status());
    }
    @ParameterizedTest @ValueSource(strings = {"../source.stderr.log", "/source.stderr.log", "a//b.log", "a/./b.log", "a/../b.log", "target.stderr.log:secret", "a\\b.log", "a./b.log"})
    void unsafeExitArtifactPathsAreRejected(String path) throws Exception {
        obj(obj(exitRuns.get("target")).get("stderr")).put("path", path); write("exits.json", exitIndex); refuses("PATH");
    }
    @Test void aliasesAcrossObservationAndExitArtifactsAreRejected() throws Exception {
        Files.createLink(root.resolve("stderr-alias.log"), root.resolve("original.jar"));
        obj(exitRuns.get("target")).put("stderr", file("stderr-alias.log"));
        mutateExit("target", row -> row.put("stderrSha256", hashOf("original"))); refuses("FILE_ALIAS");
    }
    @Test void identicalPathCannotBeUsedAsBothExitAndStderr() throws Exception {
        obj(exitRuns.get("target")).put("stderr", file("target.exit.json")); write("exits.json", exitIndex); refuses("FILE_ALIAS");
    }
    @Test void changedExitBytesCannotReuseRecordedHash() throws Exception {
        byte[] bytes = Files.readAllBytes(root.resolve("target.exit.json")); bytes[0] ^= 1; Files.write(root.resolve("target.exit.json"), bytes); refuses("FILE_HASH");
    }
    @Test void changedExitLengthCannotReuseRecordedSize() throws Exception {
        Files.writeString(root.resolve("target.exit.json"), "changed"); refuses("FILE_SIZE");
    }
    @Test void changedStderrCannotReuseItsRecordedHash() throws Exception {
        replaceStderr("target", "warning-one\n"); Files.writeString(root.resolve("target.stderr.log"), "warning-two\n"); refuses("FILE_HASH");
    }
    @Test void malformedHashPinIsRejected() throws Exception {
        obj(obj(exitRuns.get("target")).get("stderr")).put("sha256", "G".repeat(64)); write("exits.json", exitIndex); refuses("HASH_FORMAT");
    }
    @Test void oversizedDeclaredExitOrStderrIsRejectedBeforeRead() throws Exception {
        obj(obj(exitRuns.get("target")).get("exit")).put("bytes", 65537); write("exits.json", exitIndex); refuses("BYTE_BUDGET");
        obj(exitRuns.get("target")).put("exit", file("target.exit.json"));
        obj(obj(exitRuns.get("target")).get("stderr")).put("bytes", (8L << 20) + 1); write("exits.json", exitIndex); refuses("BYTE_BUDGET");
    }
    @Test void actualOversizedExitJsonIsRejected() throws Exception {
        Files.writeString(root.resolve("target.exit.json"), " ".repeat(65537)); obj(exitRuns.get("target")).put("exit", file("target.exit.json"));
        write("exits.json", exitIndex); refuses("BYTE_BUDGET");
    }
    @Test void duplicateJsonKeyIsRejectedWithoutEchoingContents() throws Exception {
        Files.writeString(root.resolve("target.exit.json"), "{\"schemaVersion\":1,\"schemaVersion\":1}");
        obj(exitRuns.get("target")).put("exit", file("target.exit.json")); write("exits.json", exitIndex); refuses("JSON");
    }
    @Test void malformedJsonDiagnosticContainsNoInputExcerpt() throws Exception {
        Files.writeString(root.resolve("target.exit.json"), "{untrusted-private-content}");
        obj(exitRuns.get("target")).put("exit", file("target.exit.json")); write("exits.json", exitIndex);
        IOException failure = assertThrows(IOException.class, this::verify); assertEquals("EXIT_JSON", failure.getMessage()); assertNull(failure.getCause());
    }
    @Test void invalidUtf8StderrIsRejected() throws Exception {
        replaceStderrBytes("target", new byte[]{(byte) 0xc3, 0x28}); refuses("UTF8");
    }
    @Test void invalidUtf8ExitRecordIsRejected() throws Exception {
        Files.write(root.resolve("target.exit.json"), new byte[]{(byte) 0xc3, 0x28}); obj(exitRuns.get("target")).put("exit", file("target.exit.json"));
        write("exits.json", exitIndex); refuses("UTF8");
    }
    @Test void stderrLineCountIsBounded() throws Exception { replaceStderr("target", "\n".repeat(100_001)); refuses("LOG_LINE_BUDGET"); }
    @Test void stderrLineByteLengthIsBounded() throws Exception { replaceStderr("target", "x".repeat(65537)); refuses("LOG_LINE_SIZE"); }
    @Test void unicodeLineLimitCountsUtf8BytesNotOnlyCharacters() throws Exception { replaceStderr("target", "\u20ac".repeat(22000)); refuses("LOG_LINE_SIZE"); }
    @Test void exactLineBoundaryIsAccepted() throws Exception { replaceStderr("target", "x".repeat(65536)); verify(); }
    @Test void existingSourceDuplicateChunkCloseMessagesArePermitted() throws Exception {
        replaceStdout("source", normalLog("source") + chunk("source", "world") + chunk("source", "DIM-1") + chunk("source", "DIM1")); verify();
    }
    @Test void targetRequiresFinalAllDimensionsSavedNotJustThreeChunkMarkers() throws Exception {
        replaceStdout("target", normalLog("target").replace(SERVER + "ThreadedAnvilChunkStorage: All dimensions are saved\n", "")); refuses("STOP_INCOMPLETE");
    }
    @Test void sourceDoesNotAcceptAnInventedTargetOnlyCompletionMarker() throws Exception {
        replaceStdout("source", normalLog("source") + SERVER + "ThreadedAnvilChunkStorage: All dimensions are saved\n"); refuses("LOG_ORDER");
    }
    @Test void missingDimensionSaveIsRejected() throws Exception {
        replaceStdout("source", normalLog("source").replace(chunk("source", "DIM1"), "")); refuses("STOP_INCOMPLETE");
    }
    @Test void unknownWorldNameIsRejectedInsteadOfCountedAsTheDisposableWorld() throws Exception {
        replaceStdout("source", normalLog("source").replace("(world):", "(user-world):")); refuses("LOG_WORLD");
    }
    @Test void missingDoneMarkerCannotUseCaptureAsStartupEvidence() throws Exception {
        replaceStdout("target", normalLog("target").replace(DONE, "")); refuses("LOG_ORDER");
    }
    @Test void captureBeforeDoneIsRejected() throws Exception {
        String normal = normalLog("target"), marker = captureMarker("target");
        replaceStdout("target", marker + DONE + normal.substring((DONE + marker).length())); refuses("LOG_ORDER");
    }
    @Test void stoppingBeforeCaptureIsRejected() throws Exception {
        String normal = normalLog("target"), marker = captureMarker("target"), stop = SERVER + "Stopping server\n";
        replaceStdout("target", normal.replace(marker + stop, stop + marker)); refuses("LOG_ORDER");
    }
    @Test void reorderedSaveSequenceIsRejected() throws Exception {
        replaceStdout("target", normalLog("target").replace(SERVER + "Saving players\n" + SERVER + "Saving worlds\n", SERVER + "Saving worlds\n" + SERVER + "Saving players\n")); refuses("LOG_ORDER");
    }
    @Test void chunksBeforeSavingWorldsAreRejected() throws Exception {
        replaceStdout("source", normalLog("source").replace(SERVER + "Saving worlds\n" + chunk("source", "world"), chunk("source", "world") + SERVER + "Saving worlds\n")); refuses("LOG_ORDER");
    }
    @Test void finalTargetMarkerBeforeAllDimensionsIsRejected() throws Exception {
        String normal = normalLog("target"), end = SERVER + "ThreadedAnvilChunkStorage: All dimensions are saved\n";
        replaceStdout("target", normal.replace(chunk("target", "DIM1") + end, end + chunk("target", "DIM1"))); refuses("LOG_ORDER");
    }
    @Test void wrongNativeThreadCannotSupplySaveMarkers() throws Exception {
        replaceStdout("target", normalLog("target").replace("[Server thread/INFO]", "[Worker thread/INFO]")); refuses("LOG_ORDER");
    }
    @Test void wrongNativeLoggerCannotSupplyChunkCompletion() throws Exception {
        replaceStdout("source", normalLog("source").replace("[minecraft/ChunkManager]", "[minecraft/MinecraftServer]")); refuses("LOG_ORDER");
    }
    @Test void truncatedStopSequenceIsRejectedEvenWithRecordedExitZeroAndEof() throws Exception {
        replaceStdout("source", DONE + captureMarker("source") + SERVER + "Stopping server\n"); refuses("STOP_INCOMPLETE");
    }
    @ParameterizedTest @ValueSource(strings = {"[Server thread/ERROR] failure", "[Server thread/FATAL] failure", "ERROR failure", "FATAL failure",
            "Encountered an unexpected exception", "Exception stopping the server", "Failed to unlock level world",
            "Failed to start the minecraft server", "Exception closing the level", "java.lang.IllegalStateException: broken", "Exception in thread server"})
    void lateFailureInStderrRejectsGoodCaptureStopAndExitZero(String failure) throws Exception {
        replaceStderr("target", failure + "\n"); refuses("LOG_FAILURE");
    }
    @Test void lateErrorInStdoutAlsoRejectsTheRecord() throws Exception {
        replaceStdout("target", normalLog("target") + "[12:34:57] [Server thread/ERROR] [minecraft/MinecraftServer]: closing failed\n"); refuses("LOG_FAILURE");
    }
    @Test void probeFailureOnStderrIsNotHiddenByGoodStdout() throws Exception {
        replaceStderr("target", "LEGACYBRIDGE_COMPOSE_SHUTDOWN_FAILED\n"); refuses("LOG_FAILURE");
    }
    @Test void extraCaptureMarkerOnStderrIsRejected() throws Exception { replaceStderr("target", captureMarker("target")); refuses("STDERR_PROBE_MARKER"); }
    @ParameterizedTest @ValueSource(strings = {"accessToken=synthetic-private", "refresh_token=synthetic-private", "Authorization: Bearer synthetic-private",
            "--username synthetic-private", "--uuid synthetic-private", "--password synthetic-private", "--token synthetic-private", "password=synthetic-private", "secret=synthetic-private",
            "Picked up JAVA_TOOL_OPTIONS: -Dtoken=synthetic-private"})
    void sensitivePatternsAreRejectedWithoutEchoingCapturedData(String content) throws Exception {
        replaceStderr("target", content); IOException failure = assertThrows(IOException.class, this::verify);
        assertEquals("EXIT_SENSITIVE_DATA", failure.getMessage()); assertNull(failure.getCause());
    }
    @Test void sensitiveStdoutIsRejectedWithoutEchoingCapturedData() throws Exception {
        replaceStdout("target", normalLog("target") + "accessToken=synthetic-private\n"); refuses("SENSITIVE_DATA");
    }
    @Test void benignAuthlibOrTokenRegistryNamesAreNotClassifiedAsSecrets() throws Exception {
        replaceStderr("target", "[WARN] inspected authlib.jar and legacybridge_composed:count_token\n"); verify();
    }
    @Test void relativeOrUnnormalizedRepositoryIsRejected() {
        assertThrows(IOException.class, () -> CompositionExitVerifier.verify(Path.of("."), "observations.json", "exits.json"));
        assertThrows(IOException.class, () -> CompositionExitVerifier.verify(root.resolve("x/../"), "observations.json", "exits.json"));
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void windowsJunctionCannotAliasExitRecordParents() throws Exception {
        Path real = Files.createDirectory(root.resolve("exit-source")); Files.copy(root.resolve("target.exit.json"), real.resolve("target.exit.json"));
        Path alias = root.resolve("exit-alias"); Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", alias.toString(), real.toString()).redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
        try {
            obj(obj(exitRuns.get("target")).get("exit")).put("path", "exit-alias/target.exit.json"); write("exits.json", exitIndex); refuses("PATH_ALIAS");
        } finally { Files.delete(alias); }
    }

    private CompositionExitVerifier.Summary verify() throws IOException { return CompositionExitVerifier.verify(root, "observations.json", "exits.json"); }
    private void refuses(String code) { IOException failure = assertThrows(IOException.class, this::verify); assertTrue(failure.getMessage().contains(code), failure.getMessage()); }
    private void mutateExit(String role, Consumer<Map<String, Object>> edit) throws Exception { edit.accept(exits.get(role)); syncExit(role); syncIndexes(); }
    private void syncExit(String role) throws Exception { write(role + ".exit.json", exits.get(role)); obj(exitRuns.get(role)).put("exit", file(role + ".exit.json")); }
    private void syncIndexes() throws Exception {
        write("observations.json", observationIndex); exitIndex.put("observationIndexSha256", digest(root.resolve("observations.json"))); write("exits.json", exitIndex);
    }
    private void replaceStdout(String role, String contents) throws Exception {
        Files.writeString(root.resolve(role + ".stdout.log"), contents); obj(observationRuns.get(role)).put("log", file(role + ".stdout.log"));
        exits.get(role).put("stdoutSha256", digest(root.resolve(role + ".stdout.log"))); syncExit(role); syncIndexes();
    }
    private void replaceStderr(String role, String contents) throws Exception { replaceStderrBytes(role, contents.getBytes(StandardCharsets.UTF_8)); }
    private void replaceStderrBytes(String role, byte[] bytes) throws Exception {
        Files.write(root.resolve(role + ".stderr.log"), bytes); obj(exitRuns.get(role)).put("stderr", file(role + ".stderr.log"));
        exits.get(role).put("stderrSha256", digest(root.resolve(role + ".stderr.log"))); syncExit(role); syncIndexes();
    }
    private String normalLog(String role) throws Exception {
        return DONE + captureMarker(role) + SERVER + "Stopping server\n" + SERVER + "Saving players\n" + SERVER + "Saving worlds\n"
                + chunk(role, "world") + chunk(role, "DIM-1") + chunk(role, "DIM1")
                + (role.equals("target") ? SERVER + "ThreadedAnvilChunkStorage: All dimensions are saved\n" : "");
    }
    private String captureMarker(String role) throws Exception {
        String marker = "LEGACYBRIDGE_COMPOSE_CAPTURE_OK run=run-" + role + " sha256=" + digest(root.resolve(role + ".json")) + "\n";
        return role.equals("source") ? "[12:34:55] [Server thread/INFO] [STDOUT/]: [dev.legacybridge.validation.composition.ProbeSession:run:1]: " + marker : marker;
    }
    private static String chunk(String role, String world) {
        return "[12:34:56] [Server thread/INFO] [minecraft/" + (role.equals("source") ? "ChunkManager" : "MinecraftServer")
                + "]: ThreadedAnvilChunkStorage (" + world + "): All chunks are saved\n";
    }
    private String hashOf(String role) { return (String) obj(artifacts.get(role)).get("sha256"); }
    private Map<String, Object> file(String path) throws Exception { return map("path", path, "sha256", digest(root.resolve(path)), "bytes", Files.size(root.resolve(path))); }
    private static String digest(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private void write(String path, Map<String, Object> value) throws IOException { Files.writeString(root.resolve(path), CorpusJson.canonicalJson(value)); }
    private Map<Path, String> hashes() throws Exception {
        Map<Path, String> result = new LinkedHashMap<>(); try (var paths = Files.walk(root)) { for (Path path : paths.filter(Files::isRegularFile).toList()) result.put(path, digest(path)); } return result;
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> obj(Object value) { return (Map<String, Object>) value; }
    private static Map<String, Object> map(Object... pairs) { Map<String, Object> result = new LinkedHashMap<>(); for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]); return result; }
    private static Map<String, Object> behavior() {
        List<Map<String, Object>> foil = new ArrayList<>(), samples = new ArrayList<>();
        for (int count = 1; count <= 4; count++) foil.add(map("count", count, "foil", count % 2 == 1, "callsBefore", count - 1, "callsAfter", count));
        for (int tick = 0; tick <= 120; tick++) samples.add(map("tick", tick, "inputId", tick < 40 ? "minecraft:iron_ingot" : "minecraft:air", "inputCount", tick < 40 ? 1 : 0,
                "fuelId", tick == 0 ? "minecraft:coal" : "minecraft:air", "fuelCount", tick == 0 ? 1 : 0, "outputId", tick < 40 ? "minecraft:air" : TOKEN, "outputCount", tick < 40 ? 0 : 1));
        return map("registryId", TOKEN, "itemClass", "dev.legacybridge.fixture.composed.ComposedItem", "maxStackSize", 16, "initialCalls", 0, "foil", foil,
                "enchanted", map("count", 2, "enchantment", "minecraft:unbreaking", "level", 1, "isEnchanted", true, "foil", true, "callsBefore", 4, "callsAfter", 4),
                "cooking", map("recipeId", TOKEN, "recipeType", "minecraft:smelting", "ingredientTag", "legacybridge_composed:token_material", "tagItems", List.of("minecraft:iron_ingot"),
                        "recipeTicks", 40, "recipeExperience", 0.0, "samples", samples));
    }
}
