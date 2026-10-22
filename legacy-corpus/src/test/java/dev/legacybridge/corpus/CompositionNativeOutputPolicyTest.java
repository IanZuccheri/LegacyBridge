package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.legacybridge.corpus.CompositionNativeOutputPolicy.Category.*;
import static dev.legacybridge.corpus.CompositionRunFiles.*;
import static dev.legacybridge.corpus.CompositionRunPlanner.Role.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure policy checks and nine synthetic stages. No process, game or native output is produced. */
final class CompositionNativeOutputPolicyTest {
    private static final String RUN = "run-policy-test";
    private static final List<String> AUTHORITY_FLAGS = List.of("executionAllowed", "gameExecutionVerified",
            "nativeContentVerified", "installationAllowed", "nativeLaunchContractVerified");
    @TempDir Path root;

    @ParameterizedTest(name = "{0}: {1} is {2}") @MethodSource("admittedFiles")
    void recognizesOnlyExplicitNativeFileFamilies(CompositionRunPlanner.Role role, String path,
                                                 CompositionNativeOutputPolicy.Category category) throws Exception {
        assertEquals(category, CompositionNativeOutputPolicy.classify(role, RUN, path, false));
        outputFailure(() -> CompositionNativeOutputPolicy.classify(role, RUN, path, true));
    }

    static Stream<Arguments> admittedFiles() {
        List<Arguments> cases = new ArrayList<>();
        for (var role : CompositionRunPlanner.Role.values()) {
            cases.add(Arguments.of(role, "server.properties", SERVER_PROPERTIES));
            for (String path : List.of("config/fml.toml", "config/forge-common.toml", "world/serverconfig/forge-server.toml"))
                cases.add(Arguments.of(role, path, CONFIGURATION));
            for (String path : List.of("logs/latest.log", "logs/debug.log", "logs/2026-09-08-1.log.gz",
                    "logs/2026-09-08-7.log.gz", "logs/2026-09-08-99.log.gz", "logs/2024-02-29-1.log.gz",
                    "logs/debug-1.log.gz", "logs/debug-5.log.gz"))
                cases.add(Arguments.of(role, path, LOG));
            for (String path : List.of("world/level.dat", "world/level.dat_old", "world/session.lock", "world/data/scoreboard.dat"))
                cases.add(Arguments.of(role, path, WORLD));
            for (String dimension : List.of("world", "world/DIM-1", "world/DIM1")) {
                for (String tail : List.of("data/capabilities.dat", "region/r.0.0.mca", "poi/r.0.0.mca"))
                    cases.add(Arguments.of(role, dimension + "/" + tail, WORLD));
                cases.add(Arguments.of(role, dimension + (dimension.equals("world/DIM1") ? "/data/raids_end.dat" : "/data/raids.dat"), WORLD));
                if (role == TARGET) cases.add(Arguments.of(role, dimension + "/entities/r.0.0.mca", WORLD));
            }
            if (role == TARGET) cases.add(Arguments.of(role, "world/data/random_sequences.dat", WORLD));
            for (String path : List.of("banned-players.json", "banned-ips.json", "ops.json", "whitelist.json", "usercache.json"))
                cases.add(Arguments.of(role, path, EMPTY_PLAYER_LIST));
            cases.add(Arguments.of(role, "composition-observations/" + RUN + ".json", OBSERVATION));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}: directory {1}") @MethodSource("admittedDirectories")
    void recognizesFiniteDirectoryLayout(CompositionRunPlanner.Role role, String path) throws Exception {
        assertEquals(DIRECTORY, CompositionNativeOutputPolicy.classify(role, RUN, path, true));
        outputFailure(() -> CompositionNativeOutputPolicy.classify(role, RUN, path, false));
    }

    static Stream<Arguments> admittedDirectories() {
        List<Arguments> cases = new ArrayList<>();
        for (var role : CompositionRunPlanner.Role.values()) {
            for (String path : List.of("config", "defaultconfigs", "logs", "world", "world/datapacks", "world/serverconfig", "world/playerdata"))
                cases.add(Arguments.of(role, path));
            for (String dimension : List.of("world", "world/DIM-1", "world/DIM1")) {
                if (!dimension.equals("world")) cases.add(Arguments.of(role, dimension));
                for (String tail : List.of("data", "region", "poi")) cases.add(Arguments.of(role, dimension + "/" + tail));
                if (role == TARGET) cases.add(Arguments.of(role, dimension + "/entities"));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest @ValueSource(strings = {
            "", ".", "..", "../server.properties", "world/../server.properties", "./server.properties",
            "/server.properties", "C:/server.properties", "world\\level.dat", "world//level.dat",
            "server.properties/", "server.properties ", "server.properties.", " server.properties", "SERVER.PROPERTIES",
            "logs/Latest.log", "logs/latest.log:stream", "logs/latest.log.tmp", "logs/latest.log.gz", "logs/latest.log.1",
            "logs/2026-09-08-0.log.gz", "logs/2026-09-08-100.log.gz", "logs/2026-09-08-01.log.gz",
            "logs/2026-9-8-1.log.gz", "logs/debug-0.log.gz", "logs/debug-6.log.gz", "logs/debug-01.log.gz",
            "logs/2026-02-29-1.log.gz", "logs/2026-13-01-1.log.gz", "logs/2026-04-31-1.log.gz",
            "world/level.dat_new", "world/session.lock.tmp", "world/data/map_0.dat", "world/data/idcounts.dat",
            "world/region/r.00.0.mca", "world/region/r.2147483647.2147483647.mca", "world/region/r.0.0.mca.tmp",
            "world/region/r.-0.0.mca", "world/region/r.0.-0.mca", "world/region/r.0.0.mcc", "world/poi/r.0.0.dat", "world/playerdata/player.dat",
            "world/advancements/player.json", "world/stats/player.json", "world/datapacks/pack.zip",
            "world/data/raids_end.dat", "world/DIM-1/data/raids_end.dat", "world/DIM1/data/raids.dat",
            "world/DIM0/region/r.0.0.mca", "world/DIM2/region/r.0.0.mca", "world/dimensions/custom/moon/region/r.0.0.mca",
            "world/serverconfig/unknown.toml", "config/unknown.toml", "defaultconfigs/forge-server.toml",
            "mods/extra.jar", "libraries/extra.jar", "plugins/extra.jar", "crash-reports/crash.txt",
            "composition-probe-authorized.txt", "composition-observations/run-other.json",
            "composition-observations/run-policy-test.json.tmp", "composition-observations/run-policy-test.JSON",
            "eula.txt", "user_jvm_args.txt", "run.bat", "unknown.json", "ops.json.bak", "target/server.properties"
    })
    void rejectsUnknownPathsAliasesAndUnboundedNamesForBothRoles(String path) {
        for (var role : CompositionRunPlanner.Role.values()) {
            outputFailure(() -> CompositionNativeOutputPolicy.classify(role, RUN, path, false));
            outputFailure(() -> CompositionNativeOutputPolicy.classify(role, RUN, path, true));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"world/entities", "world/DIM-1/entities", "world/DIM1/entities"})
    void sourceCannotAdoptTargetOnlyEntityStorage(String directory) {
        outputFailure(() -> CompositionNativeOutputPolicy.classify(SOURCE, RUN, directory, true));
        outputFailure(() -> CompositionNativeOutputPolicy.classify(SOURCE, RUN, directory + "/r.0.0.mca", false));
    }

    @ParameterizedTest @ValueSource(strings = {"", "policy-test", "run-", "run-Upper", "run-other/../policy-test"})
    void classificationCannotLaunderAnInvalidRunIdentity(String runId) {
        outputFailure(() -> CompositionNativeOutputPolicy.classify(SOURCE, runId, "server.properties", false));
    }

    @Test void classificationRequiresAnExplicitRoleAndCanonicalRun() {
        outputFailure(() -> CompositionNativeOutputPolicy.classify(null, RUN, "server.properties", false));
        outputFailure(() -> CompositionNativeOutputPolicy.classify(SOURCE, null, "server.properties", false));
        outputFailure(() -> CompositionNativeOutputPolicy.classify(SOURCE, RUN, null, false));
        outputFailure(() -> CompositionNativeOutputPolicy.classify(SOURCE, RUN, "world/level.dat\0", false));
        outputFailure(() -> CompositionNativeOutputPolicy.classify(SOURCE, RUN, "world/data/random_sequences.dat", false));
    }

    @ParameterizedTest @EnumSource(CompositionRunPlanner.Role.class)
    void propertiesAllowCommentsReorderingAndKnownDefaultsWithoutChangingAnyExplicitFlag(CompositionRunPlanner.Role role) throws Exception {
        Map<String, String> explicit = protectedProperties(role);
        List<String> reversed = new ArrayList<>(explicit.keySet()); Collections.reverse(reversed);
        StringBuilder text = new StringBuilder("#Minecraft server properties\r\n! generated timestamp\r\n\r\n");
        for (String key : reversed) text.append(key).append(" : ").append(explicit.get(key)).append("\r\n");
        text.append("motd=Private probe\nallow-flight=false\ndifficulty=easy\n");
        Map<String, String> parsed = CompositionNativeOutputPolicy.validateProperties(role,
                text.toString().getBytes(StandardCharsets.UTF_8), explicit);
        explicit.forEach((key, value) -> assertEquals(value, parsed.get(key), key));
        assertEquals("Private probe", parsed.get("motd"));
        assertEquals("false", parsed.get("allow-flight"));
        assertEquals("easy", parsed.get("difficulty"));
    }

    @ParameterizedTest(name = "{0}: pinned {1}") @MethodSource("protectedKeys")
    void explicitPropertiesCannotBeChangedOrOmitted(CompositionRunPlanner.Role role, String key) {
        Map<String, String> explicit = protectedProperties(role);
        Map<String, String> changed = new LinkedHashMap<>(explicit); changed.put(key, "changed-private-value");
        outputFailure(() -> CompositionNativeOutputPolicy.validateProperties(role, propertyBytes(changed), explicit));
        changed.remove(key);
        outputFailure(() -> CompositionNativeOutputPolicy.validateProperties(role, propertyBytes(changed), explicit));
    }

    static Stream<Arguments> protectedKeys() {
        return Arrays.stream(CompositionRunPlanner.Role.values())
                .flatMap(role -> protectedProperties(role).keySet().stream().map(key -> Arguments.of(role, key)));
    }

    @ParameterizedTest @ValueSource(strings = {"unknown-key=true", "server_port=25597", "Server-ip=127.0.0.1",
            "java-home=private-path", "legacybridge-approval=true", "=value", "unknown\\ key=value"})
    void propertiesDoNotAdoptUnknownKeys(String additional) {
        for (var role : CompositionRunPlanner.Role.values()) {
            Map<String, String> explicit = protectedProperties(role);
            outputFailure(() -> CompositionNativeOutputPolicy.validateProperties(role,
                    (propertyText(explicit) + additional + "\n").getBytes(StandardCharsets.UTF_8), explicit));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"server-ip=127.0.0.1", "server\\u002dip=127.0.0.1",
            "\\u0073erver-ip=127.0.0.1", "server\\-ip=127.0.0.1", "server-\\\n ip=127.0.0.1",
            "motd=first\nmotd=second", "motd=first\n\\u006dotd=first"})
    void duplicateSemanticKeysAreRejectedIncludingEqualValuesEscapesAndContinuations(String additional) {
        for (var role : CompositionRunPlanner.Role.values()) {
            Map<String, String> explicit = protectedProperties(role);
            outputFailure(() -> CompositionNativeOutputPolicy.validateProperties(role,
                    (propertyText(explicit) + additional + "\n").getBytes(StandardCharsets.UTF_8), explicit));
        }
    }

    @Test void propertiesDecodeSourceLatin1AndTargetUtf8AccordingToTheirNativeVersions() throws Exception {
        var source = protectedProperties(SOURCE);
        byte[] latin1 = (propertyText(source) + "motd=Caf\u00e9\n").getBytes(StandardCharsets.ISO_8859_1);
        assertEquals("Caf\u00e9", CompositionNativeOutputPolicy.validateProperties(SOURCE, latin1, source).get("motd"));
        var target = protectedProperties(TARGET);
        byte[] utf8 = (propertyText(target) + "motd=Caf\u00e9 \u2603\n").getBytes(StandardCharsets.UTF_8);
        assertEquals("Caf\u00e9 \u2603", CompositionNativeOutputPolicy.validateProperties(TARGET, utf8, target).get("motd"));
        byte[] malformed = Arrays.copyOf(propertyBytes(target), propertyBytes(target).length + 2);
        malformed[malformed.length - 2] = (byte) 0xc3; malformed[malformed.length - 1] = 0x28;
        outputFailure(() -> CompositionNativeOutputPolicy.validateProperties(TARGET, malformed, target));
    }

    @Test void propertiesRejectFieldsThatBelongOnlyToTheOtherMinecraftVersion() {
        var source = protectedProperties(SOURCE); var target = protectedProperties(TARGET);
        outputFailure(() -> CompositionNativeOutputPolicy.validateProperties(SOURCE,
                (propertyText(source) + "simulation-distance=2\n").getBytes(StandardCharsets.UTF_8), source));
        outputFailure(() -> CompositionNativeOutputPolicy.validateProperties(TARGET,
                (propertyText(target) + "max-build-height=256\n").getBytes(StandardCharsets.UTF_8), target));
    }

    @ParameterizedTest @ValueSource(strings = {"motd=\\u00", "motd=\\uXXXX", "motd=\\u0g01"})
    void malformedPropertyEscapesProduceSanitizedCheckedFailures(String additional) {
        for (var role : CompositionRunPlanner.Role.values()) {
            var explicit = protectedProperties(role);
            outputFailure(() -> CompositionNativeOutputPolicy.validateProperties(role,
                    (propertyText(explicit) + additional + "\n").getBytes(StandardCharsets.UTF_8), explicit));
        }
    }

    @ParameterizedTest @EnumSource(CompositionRunPlanner.Role.class)
    void propertyByteBudgetIncludesIgnoredComments(CompositionRunPlanner.Role role) throws Exception {
        var explicit = protectedProperties(role);
        String prefix = propertyText(explicit) + "#";
        String atLimit = prefix + "x".repeat(65_536 - prefix.length());
        assertEquals(explicit, CompositionNativeOutputPolicy.validateProperties(role, atLimit.getBytes(StandardCharsets.UTF_8), explicit));
        String oversized = atLimit + "x";
        outputFailure(() -> CompositionNativeOutputPolicy.validateProperties(role,
                oversized.getBytes(StandardCharsets.UTF_8), explicit));
    }

    @ParameterizedTest @EnumSource(CompositionRunPlanner.Role.class)
    void policyAndEmptyLayoutReviewAreReadOnlyImmutableAndNeverGrantAuthority(CompositionRunPlanner.Role role) throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, role, null);
        Map<String, String> before = hashes(root);
        var policy = CompositionNativeOutputPolicy.prepare(snapshot, () -> false);
        var again = CompositionNativeOutputPolicy.prepare(snapshot, () -> false);
        assertTrue(policy.sha256().matches("[a-f0-9]{64}")); assertEquals(policy.sha256(), again.sha256());
        assertEquals(digest(CorpusJson.canonicalJson(policy.toTree()).getBytes(StandardCharsets.UTF_8)), policy.sha256());
        for (String flag : AUTHORITY_FLAGS) assertEquals(false, policy.toTree().get(flag), flag);
        assertEquals(policy.toTree(), again.toTree()); assertDeeplyImmutable(policy.toTree());
        var review = policy.reviewRecordedLayout(() -> false); assertReviewOnly(review.toTree());
        assertDeeplyImmutable(review.toTree()); assertEquals(before, hashes(root));
        assertFalse(Files.exists(snapshot.plan().workingDirectory().resolve("composition-probe-authorized.txt")));
    }

    @Test void plausibleNamesAndFabricatedLogWorldBytesEstablishLayoutOnly() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, TARGET, null);
        var policy = CompositionNativeOutputPolicy.prepare(snapshot, () -> false);
        Path cwd = snapshot.plan().workingDirectory();
        for (String directory : List.of("defaultconfigs", "world/datapacks", "world/DIM1/data", "world/playerdata"))
            Files.createDirectories(cwd.resolve(directory));
        for (String path : List.of("config/fml.toml", "config/forge-common.toml", "world/serverconfig/forge-server.toml", "world/level.dat",
                "world/level.dat_old", "world/session.lock", "world/data/raids.dat", "world/region/r.0.0.mca",
                "world/poi/r.0.0.mca", "world/entities/r.0.0.mca", "world/DIM-1/region/r.0.0.mca",
                "world/DIM1/entities/r.0.0.mca", "logs/latest.log", "logs/debug-1.log.gz"))
            write(cwd, path, "FABRICATED CONTENT; no game was run\n");
        for (String path : List.of("banned-players.json", "banned-ips.json", "ops.json", "whitelist.json", "usercache.json"))
            write(cwd, path, " [ \r\n ] \n");
        write(cwd, "composition-observations/" + snapshot.plan().runId() + ".json", "{\"synthetic\":true}\n");
        write(cwd, "server.properties", "# harmless native rewrite\nlevel-name=world\nmotd=Private probe\n"
                + "server-port=25598\nserver-ip=127.0.0.1\n");
        Map<String, String> before = hashes(root);
        var review = policy.reviewRecordedLayout(() -> false).toTree(); assertReviewOnly(review);
        for (String path : List.of("target/world/level.dat", "target/logs/latest.log", "target/config/fml.toml",
                "target/composition-observations/" + snapshot.plan().runId() + ".json"))
            assertTrue(((List<?>) review.get("contentUnchecked")).contains(path), path);
        assertEquals(before, hashes(root));
    }

    @Test void preparedPolicyRejectsChangedImmutableOriginalJavaReceiptAndStagedFiles() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, SOURCE, null);
        var policy = CompositionNativeOutputPolicy.prepare(snapshot, () -> false);
        List<Path> targets = List.of(root.resolve("inputs/fixture.jar"), root.resolve("jdk/bin/java.exe"), root.resolve("jdk/release"),
                snapshot.runRoot().resolve("stage-receipt.json"), snapshot.runRoot().resolve("stage-plan.json"),
                snapshot.plan().workingDirectory().resolve("mods/fixture.jar"));
        for (Path target : targets) {
            byte[] original = Files.readAllBytes(target);
            try {
                Files.writeString(target, "changed-private-input"); Map<String, String> changed = hashes(root);
                assertThrows(IOException.class, () -> policy.reviewRecordedLayout(() -> false), target.toString());
                assertEquals(changed, hashes(root), "A blocked review must not repair inputs");
            } finally { Files.write(target, original); }
        }
        Path properties = snapshot.plan().workingDirectory().resolve("server.properties");
        String original = Files.readString(properties);
        for (String changed : List.of(original.replace("127.0.0.1", "0.0.0.0"), original.replace("25597", "25598"),
                original.replace("level-name=world", "level-name=unreviewed-world"), original.replace("server-port=25597\n", ""))) {
            try {
                Files.writeString(properties, changed);
                assertThrows(IOException.class, () -> policy.reviewRecordedLayout(() -> false));
            } finally { Files.writeString(properties, original); }
        }
        assertReviewOnly(policy.reviewRecordedLayout(() -> false).toTree());
    }

    @Test void unknownFilesArmingAndNonemptyPlayerListsBlockWithoutRemovingEvidence() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, SOURCE, null);
        var policy = CompositionNativeOutputPolicy.prepare(snapshot, () -> false);
        Path cwd = snapshot.plan().workingDirectory();
        for (String path : List.of("unknown.jar", "composition-probe-authorized.txt", "ops.json", "whitelist.json",
                "banned-players.json", "banned-ips.json", "usercache.json")) {
            Path extra = Files.writeString(cwd.resolve(path), "[{\"name\":\"synthetic-player\"}]\n");
            try {
                Map<String, String> changed = hashes(root);
                assertThrows(IOException.class, () -> policy.reviewRecordedLayout(() -> false), path);
                assertEquals(changed, hashes(root));
            } finally { Files.delete(extra); }
        }
        for (String invalid : List.of("{}", "null", "[null]", "[] []", "[] trailing")) {
            Path extra = Files.writeString(cwd.resolve("ops.json"), invalid);
            try { assertThrows(IOException.class, () -> policy.reviewRecordedLayout(() -> false), invalid); }
            finally { Files.delete(extra); }
        }
        for (String directory : List.of("unknown", "config/nested", "world/playerdata/nested", "world/DIM2")) {
            Path extra = Files.createDirectories(cwd.resolve(directory));
            try { assertThrows(IOException.class, () -> policy.reviewRecordedLayout(() -> false), directory); }
            finally { Files.delete(extra); }
        }
        Path datapacks = Files.createDirectories(cwd.resolve("world/datapacks"));
        Path injected = Files.writeString(datapacks.resolve("payload.zip"), "synthetic");
        try { assertThrows(IOException.class, () -> policy.reviewRecordedLayout(() -> false)); }
        finally { Files.delete(injected); }
        assertReviewOnly(policy.reviewRecordedLayout(() -> false).toTree());
    }

    @Test void cancellationAndFailingCallbacksNeverPublishApprovalOrRewriteInputs() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, SOURCE, null);
        var policy = CompositionNativeOutputPolicy.prepare(snapshot, () -> false); Map<String, String> before = hashes(root);
        assertThrows(IOException.class, () -> CompositionNativeOutputPolicy.prepare(snapshot, () -> true));
        assertThrows(IOException.class, () -> policy.reviewRecordedLayout(() -> true));
        AtomicBoolean later = new AtomicBoolean();
        assertThrows(IOException.class, () -> policy.reviewRecordedLayout(() -> later.getAndSet(true)));
        IOException callback = assertThrows(IOException.class, () -> policy.reviewRecordedLayout(() -> {
            throw new IllegalStateException("synthetic-private-callback-content");
        }));
        assertFalse(callback.getMessage().contains("synthetic-private")); assertNull(callback.getCause());
        try {
            Thread.currentThread().interrupt(); assertThrows(IOException.class, () -> policy.reviewRecordedLayout(() -> false));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertEquals(before, hashes(root));
    }

    @Test void prepareCannotAdoptDriftAlreadyPresentBeforePolicyCreation() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, SOURCE, null);
        Path properties = snapshot.plan().workingDirectory().resolve("server.properties");
        byte[] original = Files.readAllBytes(properties);
        Files.writeString(properties, "server-ip=127.0.0.1\nserver-port=25597\nlevel-name=world\nmotd=changed-before-prepare\n");
        assertThrows(IOException.class, () -> CompositionNativeOutputPolicy.prepare(snapshot, () -> false));
        Files.write(properties, original);
        Path extra = Files.writeString(snapshot.plan().workingDirectory().resolve("unreviewed.jar"), "synthetic");
        try { assertThrows(IOException.class, () -> CompositionNativeOutputPolicy.prepare(snapshot, () -> false)); }
        finally { Files.delete(extra); }
        assertReviewOnly(CompositionNativeOutputPolicy.prepare(snapshot, () -> false).reviewRecordedLayout(() -> false).toTree());
    }

    @Test void perCategoryByteLimitsRejectOversizedOutputsBeforeContentCanBeAdopted() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, TARGET, null);
        var policy = CompositionNativeOutputPolicy.prepare(snapshot, () -> false); Path cwd = snapshot.plan().workingDirectory();
        Map<String, Integer> oversized = new LinkedHashMap<>();
        oversized.put("logs/latest.log", (8 << 20) + 1); oversized.put("config/fml.toml", (2 << 20) + 1);
        oversized.put("composition-observations/" + snapshot.plan().runId() + ".json", (2 << 20) + 1);
        oversized.put("ops.json", 8193); oversized.put("server.properties", 65_537);
        for (var entry : oversized.entrySet()) {
            Path file = cwd.resolve(entry.getKey()); Files.createDirectories(file.getParent());
            byte[] original = Files.exists(file) ? Files.readAllBytes(file) : null;
            Files.write(file, new byte[entry.getValue()]);
            try { assertThrows(IOException.class, () -> policy.reviewRecordedLayout(() -> false), entry.getKey()); }
            finally { if (original == null) Files.delete(file); else Files.write(file, original); }
        }
        Path oversizedWorld = cwd.resolve("world/level.dat"); Files.createDirectories(oversizedWorld.getParent());
        try (var output = java.nio.channels.FileChannel.open(oversizedWorld, java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE)) {
            output.position(64L << 20); output.write(java.nio.ByteBuffer.wrap(new byte[] {0}));
        }
        try { assertEquals("COMPOSE_OUTPUT_BYTE_LIMIT", outputFailure(() -> policy.reviewRecordedLayout(() -> false)).getMessage()); }
        finally { Files.delete(oversizedWorld); }
        Path properties = cwd.resolve("server.properties"); byte[] original = Files.readAllBytes(properties); Files.delete(properties);
        try { assertThrows(IOException.class, () -> policy.reviewRecordedLayout(() -> false)); }
        finally { Files.write(properties, original); }
        Path required = cwd.resolve("tmp"); Files.delete(required);
        try { assertThrows(IOException.class, () -> policy.reviewRecordedLayout(() -> false)); }
        finally { Files.createDirectory(required); }
        assertReviewOnly(policy.reviewRecordedLayout(() -> false).toTree());
    }

    @Test void hardLinkedOtherwiseValidGeneratedFilesCannotShareOutputIdentity() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, SOURCE, null);
        var policy = CompositionNativeOutputPolicy.prepare(snapshot, () -> false);
        Path cwd = snapshot.plan().workingDirectory(), original = Files.writeString(cwd.resolve("ops.json"), "[]");
        Path alias = Files.createLink(cwd.resolve("whitelist.json"), original);
        assertTrue(Files.isSameFile(original, alias));
        IOException rejected = outputFailure(() -> policy.reviewRecordedLayout(() -> false));
        assertEquals("COMPOSE_OUTPUT_FILE_ALIAS", rejected.getMessage());
        assertEquals("[]", Files.readString(original)); assertEquals("[]", Files.readString(alias));
        Files.delete(alias); Files.delete(original);
        assertReviewOnly(policy.reviewRecordedLayout(() -> false).toTree());
    }

    private static Map<String, String> protectedProperties(CompositionRunPlanner.Role role) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("server-ip", "127.0.0.1"); properties.put("server-port", role == SOURCE ? "25597" : "25598");
        properties.put("level-name", "world"); properties.put("enable-rcon", "false"); properties.put("enable-query", "false");
        properties.put("max-players", "1"); properties.put("white-list", "true"); properties.put("enforce-whitelist", "true");
        properties.put("view-distance", "2");
        if (role == TARGET) properties.put("simulation-distance", "2");
        properties.put("generate-structures", "false");
        return properties;
    }

    private static String propertyText(Map<String, String> properties) {
        StringBuilder result = new StringBuilder(); properties.forEach((key, value) -> result.append(key).append('=').append(value).append('\n'));
        return result.toString();
    }

    private static byte[] propertyBytes(Map<String, String> properties) { return propertyText(properties).getBytes(StandardCharsets.UTF_8); }

    private static void write(Path cwd, String relative, String text) throws IOException {
        Path file = cwd.resolve(relative); Files.createDirectories(file.getParent()); Files.writeString(file, text);
    }

    private static void assertReviewOnly(Map<String, Object> tree) {
        assertEquals(1, ((Number) tree.get("schemaVersion")).intValue());
        assertEquals("BOUNDED_LAYOUT_REVIEW_ONLY", tree.get("status"));
        for (String flag : AUTHORITY_FLAGS) assertEquals(false, tree.get(flag), flag);
    }

    @SuppressWarnings("unchecked") private static void assertDeeplyImmutable(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> mutableView = (Map<Object, Object>) map;
            assertThrows(UnsupportedOperationException.class, () -> mutableView.put("syntheticApproval", true));
            for (Object child : map.values()) assertDeeplyImmutable(child);
        } else if (value instanceof List<?> list) {
            List<Object> mutableView = (List<Object>) list;
            assertThrows(UnsupportedOperationException.class, () -> mutableView.add("syntheticApproval"));
            for (Object child : list) assertDeeplyImmutable(child);
        }
    }

    private static IOException outputFailure(org.junit.jupiter.api.function.Executable action) {
        IOException failure = assertThrows(IOException.class, action);
        assertTrue(failure.getMessage() != null && failure.getMessage().matches("COMPOSE_OUTPUT_[A-Z0-9_]+"), failure.toString());
        assertNull(failure.getCause()); return failure;
    }

    private static Map<String, String> hashes(Path root) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) result.put(root.relativize(file).toString(), hash(file));
        }
        return result;
    }
}
