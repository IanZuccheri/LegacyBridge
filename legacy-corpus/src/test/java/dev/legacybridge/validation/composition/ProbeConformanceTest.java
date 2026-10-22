package dev.legacybridge.validation.composition;

import dev.legacybridge.corpus.CompositionObservationVerifier;
import dev.legacybridge.corpus.CorpusJson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Recorder-to-checker protocol conformance using inert files and synthetic delegates only.
 * No native/fixture classes are loaded, no game runs, and the synthetic proof is not real ASM evidence.
 * Deliberately wrong observations reach the checker unchanged; capture does not implement the oracle.
 */
class ProbeConformanceTest {
    private static final String TOKEN = "legacybridge_composed:count_token";
    @TempDir Path root;
    private final Map<String, Object> artifacts = new LinkedHashMap<>(), runs = new LinkedHashMap<>();
    private Path original, candidate, runtime, receipt;

    @BeforeEach void syntheticEvidence() throws Exception {
        Path export = Files.createDirectories(root.resolve("composition-qa/run-synthetic"));
        original = Files.writeString(root.resolve("original.jar"), "inert synthetic original, native NOT_RUN");
        candidate = Files.writeString(export.resolve("candidate.jar"), "inert synthetic candidate, native NOT_RUN");
        runtime = Files.writeString(root.resolve("runtime.jar"), "inert synthetic runtime, native NOT_RUN");
        artifacts.put("original", file(original)); artifacts.put("candidate", file(candidate)); artifacts.put("runtime", file(runtime));
        Map<String, Object> proof = map("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_CANDIDATE",
                "status", "CANDIDATE_VALIDATED", "candidateIdentity", "composed-logic-recipes-v1",
                "modId", "legacybridge_composed", "modVersion", "1.0.0", "input", map("sha256", ProbeFiles.sha256(original)),
                "candidate", map("sha256", ProbeFiles.sha256(candidate), "bytes", Files.size(candidate)),
                "runtimeRequired", "legacybridge_runtime", "runtimeVersion", "0.1.1-SNAPSHOT",
                "targetMinecraft", "1.20.1", "targetForge", "47.4.23", "publicationAllowed", false,
                "bytecodeExecuted", false, "transformationsCommitted", false, "runtimeValidation", "NOT_RUN",
                "preservedClassCount", 4, "preservedMethodCount", 10, "replacedClassCount", 0,
                "bytecodeValidation", map("status", "PASSED_STATIC_REOPENED", "modClassesLoaded", false, "offlineLinkageVerified", true),
                "candidateArchiveValidation", map("status", "PASSED_REOPENED", "ownershipAndHashes", true, "centralDirectoryAndCrc", true));
        for (String phase : List.of("sourceOfflineLinkage", "targetOfflineLinkage"))
            proof.put(phase, map("status", "PASSED_SCOPED", "classesExecuted", false));
        Path proofPath = write(export.resolve("proof.json"), proof); artifacts.put("proof", file(proofPath));
        receipt = write(export.resolve("receipt.json"), map("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_QA_EXPORT",
                "status", "EXPORTED_FOR_QA", "sourceSha256", ProbeFiles.sha256(original), "candidateSha256", ProbeFiles.sha256(candidate),
                "proofSha256", ProbeFiles.sha256(proofPath), "files", List.of("candidate.jar", "proof.json", "receipt.json"),
                "installationAllowed", false, "runtimeValidation", "NOT_RUN"));
        artifacts.put("receipt", file(receipt));
    }

    @Test void actualRecorderOutputConformsToCheckerButNeverProvesGameExecution() throws Exception {
        String sourceBefore = ProbeFiles.sha256(original), targetBefore = ProbeFiles.sha256(candidate);
        Capture source = capture("SOURCE", platform -> { }); Capture target = capture("TARGET", platform -> { });
        assertNull(source.failure()); assertNull(target.failure());
        var summary = verify();
        assertEquals(CompositionObservationVerifier.State.RECORDED_MATCH_ONLY, summary.status());
        assertEquals(11, summary.files()); assertEquals(false, summary.toTree().get("gameExecutionVerified"));
        assertEquals(false, summary.toTree().get("installationAllowed"));
        assertTrue(summary.toTree().get("pending").toString().contains("COMPOSE_01_ACCEPTANCE"));
        assertEquals(sourceBefore, summary.sourceSha256()); assertEquals(targetBefore, summary.candidateSha256());
        assertEquals(sourceBefore, ProbeFiles.sha256(original)); assertEquals(targetBefore, ProbeFiles.sha256(candidate));
        assertEquals(summary, verify());
        for (Capture value : List.of(source, target)) {
            assertEquals(120, value.platform().furnace.ticks); assertTrue(value.platform().furnace.closed);
            assertEquals(4, value.platform().item.calls); assertEquals(1, value.platform().shutdowns);
            assertTrue(Files.readString(value.log()).contains("sha256=" + ProbeFiles.sha256(value.observation())));
        }
        assertNull(obj(CorpusJson.read(source.observation()).get("before")).get("runtimeSha256"));
        assertNull(obj(CorpusJson.read(source.observation()).get("before")).get("receiptSha256"));
    }

    @Test void extraActualHelperCallIsRecordedUnchangedThenRejectedByOracle() throws Exception {
        capture("SOURCE", platform -> { }); Capture target = capture("TARGET", platform -> platform.item.helperDelta = 2);
        assertNull(target.failure());
        Map<String, Object> behavior = obj(CorpusJson.read(target.observation()).get("behavior"));
        Map<String, Object> first = obj(((List<?>) behavior.get("foil")).get(0));
        assertEquals(2, ((Number) first.get("callsAfter")).intValue());
        assertEquals(8, target.platform().item.calls);
        refuses("HELPER_CALLS");
        assertTrue(Files.readString(target.log()).contains("CAPTURE_OK")); // Capture success is not behavior acceptance.
    }

    @Test void duplicatedActualFurnaceOutputIsRecordedUnchangedThenRejectedByOracle() throws Exception {
        capture("SOURCE", platform -> { }); Capture target = capture("TARGET", platform -> platform.furnace.outputMultiplier = 2);
        assertNull(target.failure());
        Map<String, Object> cooking = obj(obj(CorpusJson.read(target.observation()).get("behavior")).get("cooking"));
        Map<String, Object> tick40 = obj(((List<?>) cooking.get("samples")).get(40));
        assertEquals(2, ((Number) tick40.get("outputCount")).intValue());
        assertEquals(TOKEN, tick40.get("outputId"));
        assertEquals(120, target.platform().furnace.ticks); assertTrue(target.platform().furnace.closed);
        refuses("COOK_OUTPUT");
    }

    @Test void bothSidesMatchingAnIncorrectFoilResultDoNotBecomeCompatible() throws Exception {
        Capture source = capture("SOURCE", platform -> platform.item.forceNoFoil = true);
        Capture target = capture("TARGET", platform -> platform.item.forceNoFoil = true);
        for (Capture value : List.of(source, target)) {
            assertNull(value.failure());
            Map<String, Object> behavior = obj(CorpusJson.read(value.observation()).get("behavior"));
            assertEquals(false, obj(((List<?>) behavior.get("foil")).get(0)).get("foil"));
        }
        refuses("FOIL_RESULT");
    }

    @Test void shutdownFailureInvalidatesTheActualPublishedObservationAndSuccessMarker() throws Exception {
        capture("SOURCE", platform -> { }); Capture target = capture("TARGET", platform -> platform.failShutdown = true);
        assertInstanceOf(IOException.class, target.failure());
        assertEquals("synthetic shutdown failure", target.failure().getMessage());
        assertTrue(Files.exists(target.observation()));
        String log = Files.readString(target.log());
        assertTrue(log.contains("LEGACYBRIDGE_COMPOSE_CAPTURE_OK"));
        assertTrue(log.contains("sha256=" + ProbeFiles.sha256(target.observation())));
        assertTrue(log.contains("LEGACYBRIDGE_COMPOSE_SHUTDOWN_FAILED"));
        refuses("LOG_MARKER");
    }

    private Capture capture(String role, Consumer<SyntheticPlatform> configure) throws Exception {
        String lower = role.toLowerCase(java.util.Locale.ROOT), id = "run-" + lower;
        Path game = Files.createDirectories(root.resolve("composition-probe-runs").resolve(id).resolve(lower));
        Path mods = Files.createDirectory(game.resolve("mods")); Files.createDirectory(game.resolve("composition-observations"));
        Files.writeString(game.resolve("composition-probe-authorized.txt"), id + "\n");
        Files.writeString(game.resolve("server.properties"), "level-name=world\nserver-ip=127.0.0.1\nenable-rcon=false\nenable-query=false\n");
        boolean source = role.equals("SOURCE");
        Path fixture = Files.copy(source ? original : candidate, mods.resolve("fixture.jar"));
        Path probe = Files.writeString(mods.resolve("probe.jar"), "inert " + lower + " probe, native NOT_RUN");
        Path localRuntime = mods.resolve("runtime.jar");
        if (!source) { Files.copy(runtime, localRuntime); Files.copy(receipt, game.resolve("composition-receipt.json")); }
        ProbeSession.Pins pins = new ProbeSession.Pins(ProbeFiles.sha256(original), ProbeFiles.sha256(candidate), ProbeFiles.sha256(runtime), ProbeFiles.sha256(receipt));
        SyntheticPlatform platform = new SyntheticPlatform(source, fixture, probe, localRuntime); configure.accept(platform);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); Exception failure = null;
        try (PrintStream log = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            try { ProbeSession.run(ProbeSession.arm(game, role, id, game, pins), platform, log, log); }
            catch (Exception observed) { failure = observed; }
        }
        Path observation = game.resolve("composition-observations").resolve(id + ".json");
        Path log = Files.write(game.resolve("capture.log"), bytes.toByteArray());
        assertTrue(Files.exists(observation), "These synthetic conformance cases must publish a recorder-produced observation");
        runs.put(lower, map("id", id, "probe", file(probe), "observation", file(observation), "log", file(log)));
        return new Capture(observation, log, platform, failure);
    }

    private record Capture(Path observation, Path log, SyntheticPlatform platform, Exception failure) { }

    private static final class SyntheticPlatform implements ProbeSession.Platform {
        final Map<String, Object> environment;
        final Map<String, String> mods = new LinkedHashMap<>();
        final Map<String, Path> paths = new LinkedHashMap<>();
        final SyntheticItem item = new SyntheticItem();
        final SyntheticFurnace furnace = new SyntheticFurnace();
        boolean failShutdown; int shutdowns;
        SyntheticPlatform(boolean source, Path fixture, Path probe, Path runtime) {
            String minecraft = source ? "1.16.5" : "1.20.1", forge = source ? "36.2.42" : "47.4.23";
            environment = ProbeJson.object("minecraft", minecraft, "forge", forge, "javaMajor", 17,
                    "physicalSide", "DEDICATED_SERVER", "logicalSide", "SERVER", "serverThread", true);
            mods.put("minecraft", minecraft); mods.put("forge", forge); mods.put(ProbeSession.FIXTURE_ID, "1.0.0"); mods.put(ProbeSession.MOD_ID, "1.0.0");
            paths.put(ProbeSession.FIXTURE_ID, fixture); paths.put(ProbeSession.MOD_ID, probe);
            if (!source) { mods.put("legacybridge_runtime", "0.1.1-SNAPSHOT"); paths.put("legacybridge_runtime", runtime); }
        }
        @Override public Map<String, Object> environment() { return environment; }
        @Override public Map<String, String> loadedMods() { return mods; }
        @Override public Path modFile(String id) { return paths.get(id); }
        @Override public void assertIsolated() { }
        @Override public Map<String, Object> captureBehavior() throws Exception { return BehaviorCapture.capture(item, () -> furnace); }
        @Override public void shutdown() throws IOException { shutdowns++; if (failShutdown) throw new IOException("synthetic shutdown failure"); }
    }

    /** Fake observations live only here, never in the recorder or native implementations. */
    private static final class SyntheticItem implements BehaviorCapture.ItemView {
        int calls, helperDelta = 1; boolean forceNoFoil;
        @Override public String registryId() { return TOKEN; }
        @Override public String className() { return "dev.legacybridge.fixture.composed.ComposedItem"; }
        @Override public int stackLimit() { return 16; }
        @Override public int calls() { return calls; }
        @Override public BehaviorCapture.StackView stack(int count) {
            return new BehaviorCapture.StackView() {
                private boolean enchanted;
                @Override public int count() { return count; }
                @Override public boolean foil() { if (enchanted) return true; calls += helperDelta; return !forceNoFoil && count % 2 == 1; }
                @Override public boolean enchanted() { return enchanted; }
                @Override public void enchant() { enchanted = true; }
            };
        }
    }

    /** Deterministic fake clock for protocol testing; it does not implement or emulate a Minecraft furnace. */
    private static final class SyntheticFurnace implements BehaviorCapture.FurnaceView {
        int ticks, outputMultiplier = 1; boolean closed;
        @Override public Map<String, Object> recipe() {
            return ProbeJson.object("recipeId", TOKEN, "recipeType", "minecraft:smelting", "ingredientTag", "legacybridge_composed:token_material",
                    "tagItems", List.of("minecraft:iron_ingot"), "recipeTicks", 40, "recipeExperience", 0.0);
        }
        @Override public Map<String, Object> sample(int tick) {
            assertEquals(ticks, tick); assertFalse(closed);
            return ProbeJson.object("tick", ticks, "inputId", ticks < 40 ? "minecraft:iron_ingot" : "minecraft:air", "inputCount", ticks < 40 ? 1 : 0,
                    "fuelId", ticks == 0 ? "minecraft:coal" : "minecraft:air", "fuelCount", ticks == 0 ? 1 : 0,
                    "outputId", ticks < 40 ? "minecraft:air" : TOKEN, "outputCount", ticks < 40 ? 0 : outputMultiplier);
        }
        @Override public void tick() { assertFalse(closed); ticks++; }
        @Override public void close() { closed = true; }
    }

    private CompositionObservationVerifier.Summary verify() throws IOException {
        write(root.resolve("index.json"), map("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_OBSERVATION_INDEX",
                "oracle", CompositionObservationVerifier.ORACLE, "artifacts", artifacts, "runs", runs));
        return CompositionObservationVerifier.verify(root, "index.json");
    }
    private void refuses(String code) { assertTrue(assertThrows(IOException.class, this::verify).getMessage().contains(code)); }
    private Map<String, Object> file(Path path) throws IOException {
        return map("path", root.relativize(path).toString().replace('\\', '/'), "sha256", ProbeFiles.sha256(path), "bytes", Files.size(path));
    }
    private static Path write(Path path, Map<String, Object> value) throws IOException { return Files.writeString(path, CorpusJson.canonicalJson(value)); }
    @SuppressWarnings("unchecked") private static Map<String, Object> obj(Object value) { return (Map<String, Object>) value; }
    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
