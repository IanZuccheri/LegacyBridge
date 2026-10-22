package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static dev.legacybridge.corpus.CompositionRunFiles.*;
import static dev.legacybridge.corpus.CompositionRunPlanner.Role;
import static org.junit.jupiter.api.Assertions.*;

/** Fabricated, inert file trees. Even all-present output is explicitly NOT native success. */
final class CompositionNativeContentReviewTest {
    @TempDir Path root;

    @ParameterizedTest @EnumSource(Role.class)
    void unrunStageReportsMissingOutputsWithoutInventingConfigurations(Role role) throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, role, null);
        var policy = CompositionNativeOutputPolicy.prepare(snapshot, () -> false);
        var before = hashes(root); var review = policy.reviewRecordedContent(() -> false);
        var report = review.toTree(); assertAuthorityFalse(report); assertImmutable(report);
        assertEquals("BOUNDED_CONFIG_REVIEW_ONLY", report.get("status"));
        assertEquals(List.of(), report.get("configurations"));
        var outputs = object(report.get("requiredOutputs"));
        assertEquals("MISSING", outputs.get("status"));
        assertEquals(role == Role.SOURCE ? 13 : 12, ((List<?>) outputs.get("missing")).size());
        assertFalse(((List<?>) outputs.get("missing")).contains("server.properties"));
        assertEquals(before, hashes(root)); assertEquals(review.sha256(), policy.reviewRecordedContent(() -> false).sha256());
    }

    @ParameterizedTest @EnumSource(Role.class)
    void allRequiredNamesAndDefaultConfigsDoNotVerifyFabricatedWorldLogOrObservation(Role role) throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, role, null);
        var policy = CompositionNativeOutputPolicy.prepare(snapshot, () -> false); Path cwd = snapshot.plan().workingDirectory();
        populate(cwd, role, snapshot.plan().runId()); var before = hashes(root);
        var report = policy.reviewRecordedContent(() -> false).toTree(); assertAuthorityFalse(report); assertImmutable(report);
        assertEquals("PRESENT_ONLY", object(report.get("requiredOutputs")).get("status"));
        assertEquals(false, object(report.get("requiredOutputs")).get("successfulOutputSetVerified"));
        assertEquals(role == Role.SOURCE ? 3 : 2, ((List<?>) report.get("configurations")).size());
        assertTrue(((List<?>) report.get("launchConcerns")).contains("GLOBAL_VERSION_CHECK_ENABLED"));
        assertEquals(role == Role.TARGET, ((List<?>) report.get("launchConcerns")).contains("LAN_ADVERTISEMENT_ENABLED"));
        String prefix = role.name().toLowerCase(java.util.Locale.ROOT) + "/";
        for (String path : List.of("world/level.dat", "world/session.lock", "logs/latest.log", "logs/debug.log",
                "composition-observations/" + snapshot.plan().runId() + ".json"))
            assertTrue(((List<?>) report.get("contentUnchecked")).contains(prefix + path), path);
        assertFalse(((List<?>) report.get("contentUnchecked")).contains(prefix + "config/fml.toml"));
        assertFalse(CorpusJson.canonicalJson(report).contains("FAKE_PAYLOAD"));
        assertEquals(before, hashes(root));
        Path list = cwd.resolve("ops.json"); Files.writeString(list, "[{\"uuid\":\"not-allowed\"}]");
        assertThrows(IOException.class, () -> policy.reviewRecordedContent(() -> false));
        assertTrue(Files.readString(list).contains("not-allowed"));
    }

    @Test void contentReviewRejectsConfigurationsThatOpaqueLayoutAllowsWithoutRepair() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, Role.SOURCE, null);
        var policy = CompositionNativeOutputPolicy.prepare(snapshot, () -> false); Path cwd = snapshot.plan().workingDirectory();
        write(cwd, "config/forge-common.toml", "[general]\ndefaultWorldType = \"default\"\n");
        assertEquals(1, ((List<?>) policy.reviewRecordedContent(() -> false).toTree().get("configurations")).size());
        for (String bad : List.of("[general]\ndefaultWorldType = \"private-world\"\n", "[general]\n", "unknown = true\n", "#" + "x".repeat(65536))) {
            write(cwd, "config/forge-common.toml", bad); var before = hashes(root);
            assertEquals("BOUNDED_LAYOUT_REVIEW_ONLY", policy.reviewRecordedLayout(() -> false).toTree().get("status"));
            IOException error = assertThrows(IOException.class, () -> policy.reviewRecordedContent(() -> false));
            assertTrue(error.getMessage().matches("COMPOSE_(CONFIG|CONTENT)_[A-Z0-9_]+")); assertNull(error.getCause());
            assertFalse(error.getMessage().contains("private-world")); assertEquals(before, hashes(root));
        }
    }

    @Test void emptyTargetCommonFileIsNotSilentlyAdoptedAsRegisteredConfiguration() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, Role.TARGET, null);
        var policy = CompositionNativeOutputPolicy.prepare(snapshot, () -> false);
        write(snapshot.plan().workingDirectory(), "config/forge-common.toml", "# empty unregistered file\n");
        var before = hashes(root);
        assertEquals("BOUNDED_LAYOUT_REVIEW_ONLY", policy.reviewRecordedLayout(() -> false).toTree().get("status"));
        assertEquals("COMPOSE_CONFIG_UNREGISTERED_CONFIGURATION", assertThrows(IOException.class,
                () -> policy.reviewRecordedContent(() -> false)).getMessage());
        assertEquals(before, hashes(root));
    }

    @Test void cancellationAndInterruptionAbortWithoutAuthorityWritesOrLeakingCallbackData() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, Role.SOURCE, null);
        var policy = CompositionNativeOutputPolicy.prepare(snapshot, () -> false); var before = hashes(root);
        assertEquals("COMPOSE_CONTENT_CANCELLED", assertThrows(IOException.class, () -> policy.reviewRecordedContent(() -> true)).getMessage());
        var count = new AtomicInteger();
        assertEquals("COMPOSE_CONTENT_CANCELLED", assertThrows(IOException.class,
                () -> policy.reviewRecordedContent(() -> count.incrementAndGet() > 5)).getMessage());
        IOException error = assertThrows(IOException.class, () -> policy.reviewRecordedContent(() -> { throw new IllegalStateException("PRIVATE_CALLBACK_DATA"); }));
        assertEquals("COMPOSE_CONTENT_CANCELLATION_CALLBACK", error.getMessage()); assertNull(error.getCause());
        try {
            Thread.currentThread().interrupt();
            assertEquals("COMPOSE_CONTENT_CANCELLED", assertThrows(IOException.class, () -> policy.reviewRecordedContent(() -> false)).getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertEquals(before, hashes(root));
    }

    @Test void immutableOriginalDriftRemainsABlockerForContentReview() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, Role.SOURCE, null);
        var policy = CompositionNativeOutputPolicy.prepare(snapshot, () -> false);
        Files.writeString(root.resolve("inputs/fixture.jar"), "CHANGED ORIGINAL"); var before = hashes(root);
        assertThrows(IOException.class, () -> policy.reviewRecordedContent(() -> false));
        assertEquals(before, hashes(root));
    }

    @Test void necessaryInventoryRejectsInvalidBindingsAndIsFiniteImmutableVersionSpecific() throws Exception {
        var source = CompositionNativeContentReview.requiredFiles(Role.SOURCE, "run-test");
        var target = CompositionNativeContentReview.requiredFiles(Role.TARGET, "run-test");
        assertEquals(14, source.size()); assertEquals(13, target.size());
        assertTrue(source.contains("config/forge-common.toml")); assertFalse(target.contains("config/forge-common.toml"));
        assertTrue(target.contains("usercache.json")); assertTrue(target.contains("logs/debug.log"));
        assertFalse(target.contains("world/level.dat_old")); assertThrows(UnsupportedOperationException.class, () -> source.add("invented"));
        for (String run : List.of("", "../run-test", "run-UPPER", "run-other.json"))
            assertThrows(IOException.class, () -> CompositionNativeContentReview.requiredFiles(Role.SOURCE, run));
        assertThrows(IOException.class, () -> CompositionNativeContentReview.requiredFiles(null, "run-test"));
    }

    private static void populate(Path cwd, Role role, String run) throws Exception {
        // Separate handwritten oracle; no validator schema/default factory is used to generate positives.
        for (String path : List.of("banned-players.json", "banned-ips.json", "ops.json", "whitelist.json", "usercache.json")) write(cwd, path, "[]\n");
        for (String path : List.of("world/level.dat", "world/session.lock", "logs/latest.log", "logs/debug.log", "composition-observations/" + run + ".json"))
            write(cwd, path, "FAKE_PAYLOAD; no game was run\n");
        String fml = "versionCheck = true\ndefaultConfigPath = \"defaultconfigs\"\nmaxThreads = -1\n";
        fml += role == Role.SOURCE ? "splashscreen = true\n" : "earlyWindowControl = true\nearlyWindowProvider = \"fmlearlywindow\"\n"
                + "earlyWindowWidth = 854\nearlyWindowHeight = 480\nearlyWindowFBScale = 1\nearlyWindowMaximized = false\n"
                + "disableOptimizedDFU = true\nearlyWindowSkipGLVersions = []\nearlyWindowLogHelpMessage = true\nearlyWindowSquir = false\nearlyWindowShowCPU = false\n";
        write(cwd, "config/fml.toml", fml);
        if (role == Role.SOURCE) write(cwd, "config/forge-common.toml", "[general]\ndefaultWorldType = \"default\"\n");
        String server = "[server]\nremoveErroringEntities = false\nfullBoundingBoxLadders = false\nzombieBaseSummonChance = 0.1\nzombieBabyChance = 0.05\n";
        server += role == Role.SOURCE ? "removeErroringTileEntities = false\nlogCascadingWorldGeneration = true\nfixVanillaCascading = false\n"
                + "dimensionUnloadQueueDelay = 0\ntreatEmptyTagsAsAir = false\nfixAdvancementLoading = true\n"
                : "removeErroringBlockEntities = false\npermissionHandler = \"forge:default_handler\"\nadvertiseDedicatedServerToLan = true\nuseItemWithDurationZero = false\n";
        write(cwd, "world/serverconfig/forge-server.toml", server);
    }
    private static void write(Path cwd, String path, String value) throws IOException {
        Path selected = cwd.resolve(path); Files.createDirectories(selected.getParent()); Files.writeString(selected, value);
    }
    private static Map<String, String> hashes(Path root) throws IOException {
        Map<String, String> hashes = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) { for (Path path : paths.filter(Files::isRegularFile).toList()) hashes.put(root.relativize(path).toString(), hash(path)); }
        return hashes;
    }
    private static void assertAuthorityFalse(Map<String, Object> report) {
        for (String flag : List.of("executionAllowed", "installationAllowed", "gameExecutionVerified", "nativeContentVerified", "nativeLaunchContractVerified", "exitObserved"))
            assertEquals(false, report.get(flag), flag);
        assertEquals("NOT_RUN", report.get("runtimeValidation"));
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertImmutable(Object value) {
        if (value instanceof Map map) { assertThrows(UnsupportedOperationException.class, () -> map.put("forged", true)); map.values().forEach(CompositionNativeContentReviewTest::assertImmutable); }
        else if (value instanceof List list) { assertThrows(UnsupportedOperationException.class, () -> list.add("forged")); list.forEach(CompositionNativeContentReviewTest::assertImmutable); }
    }
}
