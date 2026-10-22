package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.legacybridge.corpus.CompositionRunFiles.*;
import static dev.legacybridge.corpus.CompositionRunPlanner.Role.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic stages exercise only the package-private seam; they never prove public native selection. */
final class CompositionStagedRunVerifierTest {
    @TempDir Path root;

    /** Tiny trusted-plan seam reusable by inert session tests. No game, compiler or Java executable is started. */
    static CompositionStagedRunVerifier.Snapshot fixture(Path repository, CompositionRunPlanner.Role role, Path javaHome) throws Exception {
        return fixture(repository, role, javaHome, "");
    }
    // Additional exact baseline settings for proposal-conflict tests; still an inert, package-private seam.
    static CompositionStagedRunVerifier.Snapshot fixture(Path repository, CompositionRunPlanner.Role role, Path javaHome,
                                                         String additionalProperties) throws Exception {
        Path root = Files.createDirectories(repository).toRealPath();
        if (javaHome == null) {
            javaHome = Files.createDirectories(root.resolve("jdk/bin")).getParent();
            Files.writeString(javaHome.resolve("bin/java.exe"), "INERT EXECUTABLE - NEVER RUN");
            Files.writeString(javaHome.resolve("release"), "JAVA_VERSION=\"17.0.12\"\n");
        }
        javaHome = javaHome.toRealPath();
        Path executable = javaHome.resolve(Files.exists(javaHome.resolve("bin/java.exe")) ? "bin/java.exe" : "bin/java");
        String runId = "run-fixture-" + role.name().toLowerCase(java.util.Locale.ROOT);
        String cwdName = "build/composition-probe-runs/" + runId + "/" + role.name().toLowerCase(java.util.Locale.ROOT);
        Path cwd = root.resolve(cwdName);
        Files.createDirectories(root.resolve("inputs"));
        List<CompositionRunPlanner.Copy> copies = new ArrayList<>(); List<Map<String, Object>> inputs = new ArrayList<>();
        for (String name : role == SOURCE ? List.of("fixture", "probe") : List.of("fixture", "probe", "runtime")) {
            String source = "inputs/" + name + ".jar";
            Path file = Files.writeString(root.resolve(source), "INERT CONTENT: " + name);
            String destination = "mods/" + (name.equals("runtime") ? "LegacyBridge" : name.equals("probe") ? "composition-probe" : name) + ".jar";
            copies.add(new CompositionRunPlanner.Copy(source, destination, hash(file), Files.size(file)));
            inputs.add(Map.of("path", source, "sha256", hash(file), "bytes", Files.size(file)));
        }
        List<String> args = List.of(executable.toString(), "-Xms512M", "-Xmx2G", "-Duser.home=" + cwd.resolve("empty-user-home"),
                "-Djava.io.tmpdir=" + cwd.resolve("tmp"), "-Dlegacybridge.compositionProbe.run=" + runId, "-jar", "mods/fixture.jar", "nogui");
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("schemaVersion", 1); tree.put("reportType", "LEGACY_COMPOSITION_RUN_PLAN"); tree.put("status", "PLANNED_ONLY");
        tree.put("role", role.name()); tree.put("runId", runId); tree.put("workingDirectory", cwdName);
        tree.put("runtimeValidation", "NOT_RUN"); tree.put("executionAllowed", false); tree.put("installationAllowed", false);
        tree.put("java", Map.of("executable", executable.toString(), "sha256", hash(executable),
                "releaseSha256", hash(javaHome.resolve("release")), "declaredVersion", "\"17.0.12\"", "executed", false));
        tree.put("arguments", args); tree.put("copyFiles", copies.stream().map(CompositionRunPlanner.Copy::tree).toList());
        tree.put("generatedFiles", Map.of("composition-probe-authorized.txt", runId + "\n",
                "server.properties", "server-ip=127.0.0.1\nserver-port=" + (role == SOURCE ? "25597" : "25598") + "\nlevel-name=world\n" + additionalProperties));
        tree.put("createDirectories", List.of("mods", "composition-observations", "empty-user-home", "empty-user-home/absent-gradle", "tmp"));
        tree.put("removeEnvironment", List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"));
        tree.put("setEnvironment", Map.of("GRADLE_USER_HOME", cwd.resolve("empty-user-home/absent-gradle").toString()));
        tree.put("limits", Map.of("maxHeapMiB", 2048, "maxConcurrentGames", 1, "timeoutMillis", 240000,
                "stopGraceMillis", 60000, "stdoutMaxBytes", 8388608, "stderrMaxBytes", 8388608));
        tree.put("inputs", inputs); tree.put("pending", List.of("INERT_TEST_ONLY_NOT_REVIEWED_NATIVE_SELECTION"));
        String json = CorpusJson.canonicalJson(tree);
        var plan = new CompositionRunPlanner.Plan(role, runId, cwd, args, copies, json, digest(json.getBytes(StandardCharsets.UTF_8)));
        var outcome = CompositionRunStager.stagePlan(root, plan, plan.sha256(), () -> false, (point, run) -> { });
        assertEquals(CompositionRunStager.State.STAGED_UNARMED, outcome.status(), outcome.toString());
        return CompositionStagedRunVerifier.inspect(root, plan, outcome.receiptSha256(), () -> false);
    }

    @ParameterizedTest @EnumSource(CompositionRunPlanner.Role.class)
    void checksSmallExactStagesWithoutGrantingExecutionOrChangingFiles(CompositionRunPlanner.Role role) throws Exception {
        var snapshot = fixture(root, role, null); Map<String, String> before = hashes(root);
        var checked = snapshot.recheck(() -> false);
        assertEquals(snapshot.plan(), checked.plan()); assertEquals(snapshot.runRoot(), checked.runRoot());
        assertEquals(snapshot.receiptSha256(), checked.receiptSha256()); assertEquals(root.toRealPath(), checked.repository());
        assertEquals("STAGED_INPUTS_VERIFIED_ONLY", checked.report().get("status"));
        assertEquals("NOT_RUN", checked.report().get("runtimeValidation"));
        for (String flag : List.of("executionAllowed", "gameExecutionVerified", "installationAllowed", "launchContractVerified"))
            assertEquals(false, checked.toTree().get(flag));
        assertThrows(UnsupportedOperationException.class, () -> checked.toTree().put("executionAllowed", true));
        assertThrows(UnsupportedOperationException.class, () -> checked.immutableInputs().clear());
        assertTrue(checked.immutableInputs().stream().anyMatch(pin -> pin.path().equals(root.resolve("jdk/bin/java.exe"))));
        assertTrue(checked.immutableInputs().stream().anyMatch(pin -> pin.path().equals(root.resolve("jdk/release"))));
        assertTrue(checked.immutableInputs().stream().anyMatch(pin -> pin.path().equals(root.resolve("inputs/fixture.jar"))));
        assertTrue(checked.immutableInputs().stream().anyMatch(pin -> pin.path().equals(snapshot.runRoot().resolve("stage-receipt.json"))));
        assertFalse(checked.immutableInputs().stream().anyMatch(pin -> pin.path().equals(checked.plan().workingDirectory().resolve("server.properties"))));
        assertFalse(Files.exists(checked.plan().workingDirectory().resolve("composition-probe-authorized.txt")));
        assertEquals(before, hashes(root));
    }
    @ParameterizedTest @ValueSource(strings = {"stage-plan.json", "stage-intent.json", "journal/0000.json", "source/mods/fixture.jar", "source/server.properties"})
    void detectsTamperedStagedInputsIncludingPreLaunchMutableConfiguration(String relative) throws Exception {
        var snapshot = fixture(root, SOURCE, null); Files.writeString(snapshot.runRoot().resolve(relative), "TAMPERED");
        assertThrows(IOException.class, () -> snapshot.recheck(() -> false));
    }
    @ParameterizedTest @ValueSource(strings = {"inputs/fixture.jar", "jdk/bin/java.exe", "jdk/release"})
    void detectsOriginalAndSelectedJavaDrift(String relative) throws Exception {
        var snapshot = fixture(root, SOURCE, null); Files.writeString(root.resolve(relative), "DRIFT");
        assertThrows(IOException.class, () -> snapshot.recheck(() -> false));
    }
    @ParameterizedTest @ValueSource(strings = {"world", "logs", "config", "unknown/child"})
    void rejectsExtraEvenEmptyDirectories(String relative) throws Exception {
        var snapshot = fixture(root, SOURCE, null); Files.createDirectories(snapshot.plan().workingDirectory().resolve(relative));
        assertThrows(IOException.class, () -> snapshot.recheck(() -> false));
    }
    @ParameterizedTest @ValueSource(strings = {"stage-receipt.pending", "source/unknown.jar", "source/composition-probe-authorized.txt", "target/composition-probe-authorized.txt"})
    void rejectsExtraFilesAndArmingOnEitherSide(String relative) throws Exception {
        var snapshot = fixture(root, SOURCE, null); Path extra = snapshot.runRoot().resolve(relative);
        Files.createDirectories(extra.getParent()); Files.writeString(extra, "unapproved");
        assertThrows(IOException.class, () -> snapshot.recheck(() -> false));
    }
    @Test void missingFileBlocksWithoutRepairingIt() throws Exception {
        var snapshot = fixture(root, SOURCE, null); Path file = snapshot.plan().workingDirectory().resolve("mods/fixture.jar"); Files.delete(file);
        assertThrows(IOException.class, () -> snapshot.recheck(() -> false)); assertFalse(Files.exists(file));
    }
    @Test void missingExpectedEmptyDirectoryIsNotRecreated() throws Exception {
        var snapshot = fixture(root, SOURCE, null); Path directory = snapshot.plan().workingDirectory().resolve("tmp"); Files.delete(directory);
        assertThrows(IOException.class, () -> snapshot.recheck(() -> false)); assertFalse(Files.exists(directory));
    }
    @ParameterizedTest @ValueSource(strings = {"oversized", "invalid-utf8"})
    void receiptLimitsAndStrictUtf8AreCheckedBeforeReturningSnapshot(String mode) throws Exception {
        var snapshot = fixture(root, SOURCE, null); Path file = snapshot.runRoot().resolve("stage-receipt.json");
        Files.write(file, mode.equals("oversized") ? new byte[CorpusJson.MAX_BYTES + 1] : new byte[] {(byte) 0xc3, 0x28});
        String changed = hash(file);
        assertThrows(IOException.class, () -> CompositionStagedRunVerifier.inspect(root, snapshot.plan(), changed, () -> false));
    }
    @ParameterizedTest @ValueSource(strings = {"malformed", "0000000000000000000000000000000000000000000000000000000000000000"})
    void wrongOrMalformedCallerReceiptHashCannotAdoptCurrentStage(String supplied) throws Exception {
        var snapshot = fixture(root, SOURCE, null);
        assertThrows(IOException.class, () -> CompositionStagedRunVerifier.inspect(root, snapshot.plan(), supplied, () -> false));
    }
    @ParameterizedTest @ValueSource(strings = {"unknown", "schemaVersion", "role", "runId", "operationId", "planSha256", "executionAllowed", "installationAllowed", "gameExecutionVerified", "withheldFiles", "files", "directories"})
    void rejectsForgedReceiptEvenWhenCallerSuppliesItsUpdatedHash(String field) throws Exception {
        var snapshot = fixture(root, SOURCE, null); Path file = snapshot.runRoot().resolve("stage-receipt.json");
        Map<String, Object> receipt = new LinkedHashMap<>(CorpusJson.read(file));
        receipt.put(field, switch (field) {
            case "schemaVersion" -> 2; case "role" -> "TARGET"; case "runId" -> "run-other";
            case "operationId" -> "not-a-uuid"; case "planSha256" -> "0".repeat(64);
            case "files", "directories", "withheldFiles" -> List.of(); default -> true;
        });
        Files.writeString(file, CorpusJson.canonicalJson(receipt)); String changed = hash(file);
        assertThrows(IOException.class, () -> CompositionStagedRunVerifier.inspect(root, snapshot.plan(), changed, () -> false));
    }
    @Test void receiptCannotInventAnExtraFileEvenWithMatchingBytesAndHashes() throws Exception {
        var snapshot = fixture(root, SOURCE, null); Path extra = Files.writeString(snapshot.runRoot().resolve("source/unapproved.jar"), "extra");
        Path receiptFile = snapshot.runRoot().resolve("stage-receipt.json"); Map<String, Object> receipt = new LinkedHashMap<>(CorpusJson.read(receiptFile));
        List<Object> files = new ArrayList<>((List<?>) receipt.get("files"));
        files.add(Map.of("path", "source/unapproved.jar", "sha256", hash(extra), "bytes", Files.size(extra))); receipt.put("files", files);
        Files.writeString(receiptFile, CorpusJson.canonicalJson(receipt)); String changed = hash(receiptFile);
        assertThrows(IOException.class, () -> CompositionStagedRunVerifier.inspect(root, snapshot.plan(), changed, () -> false));
    }
    @Test void duplicateJsonKeysAndNonCanonicalReceiptsAreNotAccepted() throws Exception {
        var snapshot = fixture(root, SOURCE, null); Path file = snapshot.runRoot().resolve("stage-receipt.json"); String valid = Files.readString(file);
        for (String bad : List.of(" " + valid, valid.replaceFirst("\\{", "{\"schemaVersion\":1,"))) {
            Files.writeString(file, bad); String changed = hash(file);
            assertThrows(IOException.class, () -> CompositionStagedRunVerifier.inspect(root, snapshot.plan(), changed, () -> false));
        }
    }
    @Test void stagedHardLinkToItsSelectedOriginalIsRejected() throws Exception {
        var snapshot = fixture(root, SOURCE, null); Path target = snapshot.plan().workingDirectory().resolve("mods/fixture.jar");
        Files.delete(target); Files.createLink(target, root.resolve("inputs/fixture.jar"));
        assertThrows(IOException.class, () -> snapshot.recheck(() -> false));
    }
    @Test void stagedSymbolicLinkToItsSelectedOriginalIsRejectedWhenProviderPermitsCreatingOne() throws Exception {
        var snapshot = fixture(root, SOURCE, null); Path target = snapshot.plan().workingDirectory().resolve("mods/fixture.jar");
        Files.delete(target);
        try { Files.createSymbolicLink(target, root.resolve("inputs/fixture.jar")); }
        catch (IOException | UnsupportedOperationException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic-link creation is unavailable on this test host"); return;
        }
        assertThrows(IOException.class, () -> snapshot.recheck(() -> false));
    }
    @Test void nonCanonicalRepositoryAliasCannotBeNormalizedIntoApproval() throws Exception {
        var snapshot = fixture(root, SOURCE, null); Path aliased = root.resolve("inputs/..");
        assertThrows(IOException.class, () -> CompositionStagedRunVerifier.inspect(aliased, snapshot.plan(), snapshot.receiptSha256(), () -> false));
    }
    @Test void closedSnapshotCanBeCancelledWithoutWritingAndCallbackContentIsSanitized() throws Exception {
        var snapshot = fixture(root, SOURCE, null); Map<String, String> before = hashes(root);
        IOException cancelled = assertThrows(IOException.class, () -> snapshot.recheck(() -> true)); assertEquals("STAGED_VERIFY_CANCELLED", cancelled.getMessage());
        IOException failure = assertThrows(IOException.class, () -> snapshot.recheck(() -> { throw new IllegalStateException("synthetic-private"); }));
        assertEquals("STAGED_VERIFY_CANCELLATION_CALLBACK", failure.getMessage()); assertNull(failure.getCause());
        assertEquals(before, hashes(root));
    }
    @Test void lateCancellationDuringReadRevalidationPreventsSnapshot() throws Exception {
        var snapshot = fixture(root, SOURCE, null); AtomicBoolean later = new AtomicBoolean();
        IOException failure = assertThrows(IOException.class, () -> snapshot.recheck(() -> later.getAndSet(true)));
        assertEquals("STAGED_VERIFY_CANCELLED", failure.getMessage());
    }
    @Test void interruptedCallerStaysInterruptedAndReceivesNoSnapshot() throws Exception {
        var snapshot = fixture(root, SOURCE, null);
        try {
            Thread.currentThread().interrupt(); assertThrows(IOException.class, () -> snapshot.recheck(() -> false));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
    @Test void publicSelectorDoesNotAdoptFabricatedStageAndExistingPlannerDestinationStillBlocks() throws Exception {
        var snapshot = fixture(root, SOURCE, null);
        var request = new CompositionRunPlanner.Request(SOURCE, snapshot.plan().runId(), root.resolve("jdk"));
        assertThrows(IOException.class, () -> CompositionStagedRunVerifier.verify(root, request, snapshot.plan().sha256(), snapshot.receiptSha256(), () -> false));
        IOException exists = assertThrows(IOException.class, () -> CompositionRunPlanner.inspect(root, request, new CompositionRunFiles(root), Map.of(), "unused", "0".repeat(64)));
        assertEquals("COMPOSE_PLAN_DESTINATION_EXISTS", exists.getMessage());
    }
    private static Map<String, String> hashes(Path root) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) result.put(root.relativize(file).toString(), hash(file));
        }
        return result;
    }
}
