package dev.legacybridge.corpus;

import com.sun.nio.file.ExtendedOpenOption;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.legacybridge.corpus.CompositionRunFiles.*;
import static dev.legacybridge.corpus.CompositionRunPlanner.Role.*;
import static dev.legacybridge.corpus.CompositionRunStager.State.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Inert ZIPs and java.exe bytes: never executes Java, Minecraft or mod classes. */
final class CompositionRunStagerTest {
    private static final String INVENTORY = "docs/platform.json";
    private static final String SOURCE_LIBRARY = "libraries/synthetic/source/source-1.0.jar";
    private static final String MODULE = "libraries/synthetic/module/module-1.0.jar";
    private static final String LEGACY = "libraries/synthetic/legacy/legacy-1.0.jar";
    private static final CompositionRunStager.Hook NO_HOOK = (point, directory) -> { };
    @TempDir Path root;
    private Path javaHome;
    private final Map<String, Pin> inputs = new LinkedHashMap<>();

    @BeforeEach void prepare() throws Exception {
        root = root.toRealPath();
        javaHome = Files.createDirectories(root.resolve("jdk/bin")).getParent();
        Files.writeString(javaHome.resolve("bin/java.exe"), "INERT - must never execute");
        Files.writeString(javaHome.resolve("release"), "JAVA_VERSION=\"17.0.12\"\n");
        Files.createDirectories(root.resolve("docs"));
        Files.createDirectories(root.resolve("inputs"));
        for (String key : List.of("original", "candidate", "proof", "receipt", "runtime", "probe")) {
            jar(root.resolve("inputs/" + key + ".jar"), Map.of("Synthetic-Identity", key));
            inputs.put(key, pin("inputs/" + key + ".jar"));
        }
        Map<String, Object> platforms = new LinkedHashMap<>();
        for (var role : CompositionRunPlanner.Role.values()) {
            Files.createDirectories(template(role));
            Files.writeString(template(role).resolve("eula.txt"), "# Preaccepted inert fixture\neula=true\n");
            List<String> names = new ArrayList<>(CompositionRunPlanner.dynamicPaths(role));
            if (role == SOURCE) names.add(SOURCE_LIBRARY);
            else { names.add(MODULE); names.add(LEGACY); }
            for (String name : names) jar(template(role).resolve(name), Map.of("Synthetic-Identity", name));
            names.add(launch(role));
            if (role == SOURCE) jar(template(role).resolve(launch(role)), Map.of(
                    "Main-Class", "net.minecraftforge.server.ServerMain", "Class-Path", SOURCE_LIBRARY,
                    "ServerLaunchArgs", "--gameDir . --launchTarget fmlserver --fml.forgeVersion 36.2.42 "
                            + "--fml.mcpVersion 20210115.111550 --fml.mcVersion 1.16.5 --fml.forgeGroup net.minecraftforge"));
            else {
                Path args = template(role).resolve(launch(role)); Files.createDirectories(args.getParent());
                Files.writeString(args, String.join("\n", List.of("-p " + MODULE,
                        "--add-modules ALL-MODULE-PATH", "--add-opens java.base/java.util.jar=cpw.mods.securejarhandler",
                        "--add-opens java.base/java.lang.invoke=cpw.mods.securejarhandler",
                        "--add-exports java.base/sun.security.util=cpw.mods.securejarhandler",
                        "--add-exports jdk.naming.dns/com.sun.jndi.dns=java.naming", "-Djava.net.preferIPv6Addresses=system",
                        "-DignoreList=module-1.0.jar", "-DlibraryDirectory=libraries", "-DlegacyClassPath=" + LEGACY,
                        "cpw.mods.bootstraplauncher.BootstrapLauncher", "--launchTarget forgeserver", "--fml.forgeVersion 47.4.23",
                        "--fml.mcVersion 1.20.1", "--fml.forgeGroup net.minecraftforge", "--fml.mcpVersion 20230612.114412")) + "\n");
            }
            List<Map<String, Object>> entries = new ArrayList<>();
            for (String name : names) {
                Path file = template(role).resolve(name);
                entries.add(Map.of("path", name, "sha256", hash(file), "bytes", Files.size(file)));
            }
            platforms.put(role.name(), Map.of("templateRoot", templateName(role), "launchFile", launch(role), "files", entries));
        }
        Files.writeString(root.resolve(INVENTORY), CorpusJson.canonicalJson(Map.of("schemaVersion", 1,
                "reportType", "LEGACY_COMPOSITION_PLATFORM_INVENTORY", "provenance", "Inert staging fixture only",
                "platforms", platforms)));
    }

    @ParameterizedTest @EnumSource(CompositionRunPlanner.Role.class)
    void stagesExactCopiesAndReceiptWithoutArmingExecutingOrInstalling(CompositionRunPlanner.Role role) throws Exception {
        var plan = plan(role);
        Map<String, String> originals = inputSnapshot();
        var outcome = stage(plan, NO_HOOK);
        assertEquals(STAGED_UNARMED, outcome.status(), String.valueOf(outcome.error()));
        assertEquals("build/composition-probe-runs/run-test", outcome.runDirectory());
        assertEquals(plan.sha256(), outcome.planSha256());
        assertNull(outcome.error());
        Path run = run(plan);
        assertEquals(hash(run.resolve("stage-receipt.json")), outcome.receiptSha256());
        assertEquals(plan.canonicalJson(), Files.readString(run.resolve("stage-plan.json")));
        assertTrue(Files.isRegularFile(run.resolve("stage-intent.json")));
        assertTrue(Files.isRegularFile(run.resolve("journal/0000.json")));
        for (var copy : plan.copies()) {
            Path target = plan.workingDirectory().resolve(copy.destination());
            assertArrayEquals(Files.readAllBytes(root.resolve(copy.source())), Files.readAllBytes(target));
            assertEquals(copy.sha256(), hash(target));
            assertFalse(Files.isSameFile(root.resolve(copy.source()), target), "Copies must not alias original inputs");
        }
        Map<String, Object> body = tree(plan);
        assertEquals(object(body.get("generatedFiles")).get("server.properties"),
                Files.readString(plan.workingDirectory().resolve("server.properties")));
        assertFalse(Files.exists(plan.workingDirectory().resolve("composition-probe-authorized.txt")));
        assertFalse(Files.exists(run.resolve("stage-receipt.pending")));
        assertFalse(Files.exists(root.resolve("mods")));
        assertEquals(originals, inputSnapshot());
        Map<String, Object> receipt = CorpusJson.read(run.resolve("stage-receipt.json"));
        assertEquals(1L, integer(receipt.get("schemaVersion")));
        assertEquals("LEGACY_COMPOSITION_STAGE_RECEIPT", receipt.get("reportType"));
        assertEquals("STAGED_UNARMED", receipt.get("status"));
        assertEquals(plan.runId(), receipt.get("runId"));
        assertEquals(role.name(), receipt.get("role"));
        assertEquals(plan.sha256(), receipt.get("planSha256"));
        assertFalse(string(receipt.get("operationId")).isBlank());
        assertEquals(List.of(role.name().toLowerCase(java.util.Locale.ROOT) + "/composition-probe-authorized.txt"), receipt.get("withheldFiles"));
        for (String flag : List.of("executionAllowed", "gameExecutionVerified", "installationAllowed")) assertEquals(false, receipt.get(flag), flag);
        for (String flag : List.of("executionAllowed", "gameExecutionVerified", "installationAllowed")) assertEquals(false, outcome.toTree().get(flag), flag);
        assertThrows(UnsupportedOperationException.class, () -> outcome.toTree().put("executionAllowed", true));
        assertTrue(receipt.get("files") instanceof List<?>);
        Set<String> recorded = new java.util.HashSet<>();
        for (Object raw : (List<?>) receipt.get("files")) {
            Map<String, Object> file = object(raw);
            String path = string(file.get("path"));
            assertTrue(recorded.add(path), "No duplicate receipt paths");
            assertEquals(hash(run.resolve(path)), file.get("sha256"));
            assertEquals(Files.size(run.resolve(path)), integer(file.get("bytes")));
        }
        for (var copy : plan.copies()) assertTrue(recorded.contains(role.name().toLowerCase(java.util.Locale.ROOT) + "/" + copy.destination()));
    }

    @Test void publicEntrypointNeverTreatsAnInertUnreviewedRepositoryAsApproved() throws Exception {
        var outcome = CompositionRunStager.stage(root, new CompositionRunPlanner.Request(SOURCE, "run-public", javaHome),
                "0".repeat(64), () -> false);
        assertEquals(BLOCKED, outcome.status());
        assertFalse(Files.exists(root.resolve("build/composition-probe-runs")));
        assertNull(outcome.receiptSha256());
    }

    @Test void mismatchingReviewedPlanDigestBlocksBeforeCreatingAnything() throws Exception {
        var plan = plan(SOURCE);
        var outcome = CompositionRunStager.stagePlan(root, plan, "0".repeat(64), () -> false, NO_HOOK);
        blockedBeforeWrite(plan, outcome);
    }

    @ParameterizedTest @ValueSource(strings = {"digest", "role", "runId", "workingDirectory", "arguments", "copies", "noncanonical", "unknownField", "executionAllowed", "installationAllowed"})
    void rejectsTypedAndCanonicalDisagreementOrUnreviewedPlanContracts(String variation) throws Exception {
        var original = plan(SOURCE);
        var plan = switch (variation) {
            case "digest" -> new CompositionRunPlanner.Plan(original.role(), original.runId(), original.workingDirectory(),
                    original.arguments(), original.copies(), original.canonicalJson(), "1".repeat(64));
            case "role" -> new CompositionRunPlanner.Plan(TARGET, original.runId(), original.workingDirectory(),
                    original.arguments(), original.copies(), original.canonicalJson(), original.sha256());
            case "runId" -> new CompositionRunPlanner.Plan(original.role(), "run-other", original.workingDirectory(),
                    original.arguments(), original.copies(), original.canonicalJson(), original.sha256());
            case "workingDirectory" -> new CompositionRunPlanner.Plan(original.role(), original.runId(), root.resolve("foreign"),
                    original.arguments(), original.copies(), original.canonicalJson(), original.sha256());
            case "arguments" -> new CompositionRunPlanner.Plan(original.role(), original.runId(), original.workingDirectory(),
                    List.of("must-not-execute"), original.copies(), original.canonicalJson(), original.sha256());
            case "copies" -> new CompositionRunPlanner.Plan(original.role(), original.runId(), original.workingDirectory(),
                    original.arguments(), List.of(), original.canonicalJson(), original.sha256());
            case "noncanonical" -> withJson(original, original.canonicalJson() + "\n");
            default -> mutate(original, body -> body.put(variation, true));
        };
        blockedBeforeWrite(original, stage(plan, NO_HOOK));
    }

    @ParameterizedTest @ValueSource(strings = {"../escape.jar", "/absolute.jar", "mods/CON.jar", "mods/fixture.jar:stream", "mods/fixture.jar.", "stage-receipt.json"})
    void refusesUnsafeOrOutOfContractCopyDestinations(String destination) throws Exception {
        var original = plan(SOURCE);
        List<CompositionRunPlanner.Copy> copies = new ArrayList<>(original.copies());
        var first = copies.get(0);
        copies.set(0, new CompositionRunPlanner.Copy(first.source(), destination, first.sha256(), first.bytes()));
        var plan = withCopies(original, copies);
        blockedBeforeWrite(original, stage(plan, NO_HOOK));
    }

    @Test void duplicateCopyDestinationsCannotOverwriteAnEarlierStagedFile() throws Exception {
        var original = plan(SOURCE);
        List<CompositionRunPlanner.Copy> copies = new ArrayList<>(original.copies());
        copies.add(copies.get(0));
        blockedBeforeWrite(original, stage(withCopies(original, copies), NO_HOOK));
    }

    @ParameterizedTest @ValueSource(strings = {"inputs/original.jar", "inputs/candidate.jar", "jdk/bin/java.exe", "jdk/release", "docs/platform.json"})
    void changedSelectedInputOrJavaMetadataBlocksWithoutAResult(String relative) throws Exception {
        var plan = plan(SOURCE);
        Files.writeString(root.resolve(relative), "Drift of inert reviewed input");
        blockedBeforeWrite(plan, stage(plan, NO_HOOK));
    }

    @Test void unknownExistingRunIsPreservedByteForByte() throws Exception {
        var plan = plan(SOURCE);
        Path foreign = Files.createDirectories(run(plan)).resolve("user-file.txt");
        Files.writeString(foreign, "not owned by staging");
        Map<String, String> before = snapshot(run(plan));
        var outcome = stage(plan, NO_HOOK);
        assertEquals(BLOCKED, outcome.status());
        assertNull(outcome.receiptSha256());
        assertEquals(before, snapshot(run(plan)));
    }

    @Test void secondInvocationNeverReusesOrOverwritesAnAlreadyStagedRun() throws Exception {
        var plan = plan(SOURCE);
        var first = stage(plan, NO_HOOK);
        assertEquals(STAGED_UNARMED, first.status());
        Map<String, String> before = snapshot(run(plan));
        var second = stage(plan, NO_HOOK);
        assertEquals(BLOCKED, second.status());
        assertNull(second.receiptSha256());
        assertEquals(before, snapshot(run(plan)));
    }

    @Test void precancelledRequestCreatesNoDirectoriesOrReceipt() throws Exception {
        var plan = plan(SOURCE);
        var outcome = CompositionRunStager.stagePlan(root, plan, plan.sha256(), () -> true, NO_HOOK);
        assertEquals(CANCELLED, outcome.status());
        assertFalse(Files.exists(run(plan)));
        assertNull(outcome.receiptSha256());
        assertFalse(outcome.remainderRetained());
    }

    @Test void cancellationAfterCreatingSharedParentsReportsRetainedRemainderWithoutARun() throws Exception {
        var plan = plan(SOURCE);
        Path parent = root.resolve("build/composition-probe-runs");
        assertFalse(Files.exists(parent, LinkOption.NOFOLLOW_LINKS));
        var outcome = CompositionRunStager.stagePlan(root, plan, plan.sha256(),
                () -> Files.exists(parent, LinkOption.NOFOLLOW_LINKS), NO_HOOK);
        assertEquals(CANCELLED, outcome.status());
        assertTrue(outcome.remainderRetained(), "Created shared parents must not be hidden as no-write cancellation");
        assertTrue(Files.isDirectory(parent));
        assertFalse(Files.exists(run(plan), LinkOption.NOFOLLOW_LINKS));
        assertNull(outcome.receiptSha256());
        try (var children = Files.list(parent)) { assertEquals(0, children.count()); }
    }

    @Test void cancellationAfterReceiptMoveDoesNotRelabelCompletedStagingAsCancelled() throws Exception {
        var plan = plan(SOURCE); AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean boundaryReached = new AtomicBoolean();
        var outcome = CompositionRunStager.stagePlan(root, plan, plan.sha256(), cancelled::get,
                (point, directory) -> {
                    if (point.equals("AFTER_RECEIPT_MOVE")) {
                        assertTrue(Files.isRegularFile(directory.resolve("stage-receipt.json")));
                        boundaryReached.set(true); cancelled.set(true);
                    }
                });
        assertTrue(boundaryReached.get(), "The publication boundary must actually be exercised");
        assertEquals(STAGED_UNARMED, outcome.status(), String.valueOf(outcome.error()));
        assertEquals(hash(run(plan).resolve("stage-receipt.json")), outcome.receiptSha256());
        assertFalse(outcome.remainderRetained());
        assertFalse(Files.exists(plan.workingDirectory().resolve("composition-probe-authorized.txt")));
        for (String flag : List.of("executionAllowed", "gameExecutionVerified", "installationAllowed")) assertEquals(false, outcome.toTree().get(flag));
    }

    @ParameterizedTest @ValueSource(strings = {"receiptBytes", "copyBytes", "unknownFile", "ioFailure"})
    void failureAfterReceiptMoveDoesNotApproveTheFinalFileOrDeleteEvidence(String variation) throws Exception {
        var plan = plan(SOURCE); AtomicBoolean boundaryReached = new AtomicBoolean();
        var outcome = stage(plan, (point, directory) -> {
            if (!point.equals("AFTER_RECEIPT_MOVE")) return;
            assertTrue(Files.isRegularFile(directory.resolve("stage-receipt.json")));
            boundaryReached.set(true);
            switch (variation) {
                case "receiptBytes" -> Files.writeString(directory.resolve("stage-receipt.json"), "tampered final receipt");
                case "copyBytes" -> Files.writeString(plan.workingDirectory().resolve("mods/fixture.jar"), "tampered published-stage copy");
                case "unknownFile" -> Files.writeString(directory.resolve("foreign-after-publication.txt"), "unknown late bytes");
                case "ioFailure" -> throw new IOException("Synthetic post-publication I/O failure");
                default -> throw new AssertionError(variation);
            }
        });
        assertTrue(boundaryReached.get(), "The publication boundary must actually be exercised");
        assertEquals(FAILED, outcome.status());
        assertTrue(outcome.remainderRetained());
        assertNotNull(outcome.error());
        assertNull(outcome.receiptSha256(), "A file named stage-receipt.json is not an approved successful outcome");
        assertTrue(Files.isRegularFile(run(plan).resolve("stage-receipt.json")));
        assertFalse(Files.exists(run(plan).resolve("stage-receipt.pending")));
        assertFalse(Files.exists(plan.workingDirectory().resolve("composition-probe-authorized.txt")));
        assertEquals(inputs.get("original").sha256(), hash(root.resolve("inputs/original.jar")));
        if (variation.equals("receiptBytes")) assertEquals("tampered final receipt", Files.readString(run(plan).resolve("stage-receipt.json")));
        if (variation.equals("unknownFile")) assertEquals("unknown late bytes", Files.readString(run(plan).resolve("foreign-after-publication.txt")));
        for (String flag : List.of("executionAllowed", "gameExecutionVerified", "installationAllowed")) assertEquals(false, outcome.toTree().get(flag));
    }

    @ParameterizedTest @ValueSource(strings = {"AFTER_INTENT", "AFTER_COPY", "BEFORE_RECEIPT"})
    void cancellationRetainsPrivateRemainderButNeverApprovesIt(String checkpoint) throws Exception {
        var plan = plan(SOURCE); AtomicBoolean cancelled = new AtomicBoolean();
        var outcome = CompositionRunStager.stagePlan(root, plan, plan.sha256(), cancelled::get,
                (point, directory) -> { if (point.equals(checkpoint)) cancelled.set(true); });
        assertEquals(CANCELLED, outcome.status());
        retainedWithoutReceipt(plan, outcome);
        assertEquals(inputs.get("original").sha256(), hash(root.resolve("inputs/original.jar")));
    }

    @ParameterizedTest @ValueSource(strings = {"AFTER_INTENT", "AFTER_COPY", "BEFORE_RECEIPT"})
    void writeFailureRetainsOnlyUnapprovedOwnedRemainder(String checkpoint) throws Exception {
        var plan = plan(SOURCE);
        var outcome = stage(plan, (point, directory) -> {
            if (point.equals(checkpoint)) throw new IOException("Synthetic disk full at controlled write boundary");
        });
        assertEquals(FAILED, outcome.status());
        retainedWithoutReceipt(plan, outcome);
        assertNotNull(outcome.error());
    }

    @ParameterizedTest @ValueSource(strings = {"unknown.txt", "stage-receipt.pending", "stage-receipt.json", "source/mods/unknown.jar", "source/composition-probe-authorized.txt"})
    void injectedUnknownFileOrReceiptCollisionIsNotOverwrittenOrAccepted(String relative) throws Exception {
        var plan = plan(SOURCE);
        var outcome = stage(plan, (point, directory) -> {
            if (point.equals("BEFORE_RECEIPT")) {
                Path injected = directory.resolve(relative); Files.createDirectories(injected.getParent());
                Files.writeString(injected, "foreign bytes must survive");
            }
        });
        assertEquals(FAILED, outcome.status());
        assertTrue(outcome.remainderRetained());
        assertNull(outcome.receiptSha256());
        assertEquals("foreign bytes must survive", Files.readString(run(plan).resolve(relative)));
        if (!relative.equals("stage-receipt.json")) assertFalse(Files.exists(run(plan).resolve("stage-receipt.json")));
    }

    @Test void evenEmptyUnknownDirectoryPreventsReceiptAndIsNotCleanedUp() throws Exception {
        var plan = plan(SOURCE);
        var outcome = stage(plan, (point, directory) -> {
            if (point.equals("BEFORE_RECEIPT")) Files.createDirectory(directory.resolve("unowned-empty-directory"));
        });
        assertEquals(FAILED, outcome.status());
        retainedWithoutReceipt(plan, outcome);
        assertTrue(Files.isDirectory(run(plan).resolve("unowned-empty-directory")));
    }

    @ParameterizedTest @ValueSource(strings = {"bytes", "sourceHardlink", "missing"})
    void finalAuditRejectsChangedReplacedAliasedOrRemovedCopy(String variation) throws Exception {
        var plan = plan(SOURCE);
        var outcome = stage(plan, (point, directory) -> {
            if (!point.equals("BEFORE_RECEIPT")) return;
            Path target = plan.workingDirectory().resolve("mods/fixture.jar");
            switch (variation) {
                case "bytes" -> Files.writeString(target, "changed staged output");
                case "sourceHardlink" -> {
                    Files.delete(target); Files.createLink(target, root.resolve("inputs/original.jar"));
                }
                case "missing" -> Files.delete(target);
                default -> throw new AssertionError(variation);
            }
        });
        assertEquals(FAILED, outcome.status());
        retainedWithoutReceipt(plan, outcome);
    }

    @Test void independentSameBytesReplacementIsAnExplicitHashOnlyLimitationNotExecutionAuthority() throws Exception {
        var plan = plan(SOURCE);
        // Quiescent-filesystem contract, not a hostile-writer identity sandbox: no stable fileKey
        // is available on every supported provider. Equal bytes in an independent regular file
        // can satisfy this stage receipt; it still cannot arm or execute a probe.
        var outcome = stage(plan, (point, directory) -> {
            if (!point.equals("BEFORE_RECEIPT")) return;
            Path target = plan.workingDirectory().resolve("mods/fixture.jar");
            byte[] bytes = Files.readAllBytes(target);
            Files.move(target, root.resolve("displaced-copy.jar"));
            Files.write(target, bytes);
        });
        assertEquals(STAGED_UNARMED, outcome.status());
        assertFalse(Files.exists(plan.workingDirectory().resolve("composition-probe-authorized.txt")));
        for (String flag : List.of("executionAllowed", "gameExecutionVerified", "installationAllowed")) assertEquals(false, outcome.toTree().get(flag));
        assertEquals(inputs.get("original").sha256(), hash(root.resolve("inputs/original.jar")));
    }

    @ParameterizedTest @ValueSource(strings = {"singleFile", "aggregate", "fileCount", "inputCount", "properties"})
    void declaredBudgetOverflowIsRejectedBeforeCopying(String variation) throws Exception {
        var original = plan(SOURCE);
        CompositionRunPlanner.Plan changed;
        if (variation.equals("properties")) changed = mutate(original, body -> body.put("generatedFiles", Map.of(
                "composition-probe-authorized.txt", "run-test\n", "server.properties", "x".repeat(8193))));
        else if (variation.equals("inputCount")) changed = mutate(original, body -> {
            List<Object> pins = new ArrayList<>((List<?>) body.get("inputs"));
            while (pins.size() <= 512) pins.add(pins.get(0));
            body.put("inputs", pins);
        });
        else {
            var source = original.copies().get(0);
            List<CompositionRunPlanner.Copy> copies = new ArrayList<>();
            int count = variation.equals("aggregate") ? 5 : variation.equals("fileCount") ? 262 : 1;
            long size = variation.equals("singleFile") ? (128L << 20) + 1
                    : variation.equals("aggregate") ? 128L << 20 : source.bytes();
            for (int i = 0; i < count; i++) copies.add(new CompositionRunPlanner.Copy(source.source(),
                    "libraries/synthetic/budget-" + i + ".jar", source.sha256(), size));
            changed = withCopies(original, copies);
        }
        blockedBeforeWrite(original, stage(changed, NO_HOOK));
    }

    @ParameterizedTest @ValueSource(strings = {"inputs/original.jar", "inputs/candidate.jar", "jdk/bin/java.exe", "jdk/release"})
    void sourceAndJavaAreRevalidatedAfterCopiesNotOnlyDuringPreflight(String relative) throws Exception {
        var plan = plan(SOURCE); AtomicBoolean once = new AtomicBoolean();
        var outcome = stage(plan, (point, directory) -> {
            if (point.equals("AFTER_COPY") && once.compareAndSet(false, true)) Files.writeString(root.resolve(relative), "late input drift");
        });
        assertEquals(FAILED, outcome.status());
        retainedWithoutReceipt(plan, outcome);
    }

    @Test void selectedHardlinkAliasesAreRejectedEvenWhenBytesStillMatchPins() throws Exception {
        Files.copy(root.resolve("inputs/original.jar"), root.resolve("inputs/identical.jar"));
        inputs.put("proof", pin("inputs/identical.jar"));
        var plan = plan(SOURCE);
        Files.delete(root.resolve("inputs/identical.jar"));
        Files.createLink(root.resolve("inputs/identical.jar"), root.resolve("inputs/original.jar"));
        blockedBeforeWrite(plan, stage(plan, NO_HOOK));
    }

    @Test void selectedHardlinkAliasIntroducedBeforeReceiptCannotPassUnchangedPins() throws Exception {
        Path original = root.resolve("inputs/original.jar"), identical = root.resolve("inputs/identical.jar");
        Files.copy(original, identical);
        inputs.put("proof", pin("inputs/identical.jar"));
        assertFalse(Files.isSameFile(original, identical));
        var plan = plan(SOURCE); AtomicBoolean hookReached = new AtomicBoolean();
        var outcome = stage(plan, (point, directory) -> {
            if (!point.equals("BEFORE_RECEIPT")) return;
            Files.delete(identical); Files.createLink(identical, original);
            hookReached.set(true);
            assertTrue(Files.isSameFile(original, identical));
            assertEquals(inputs.get("proof").sha256(), hash(identical), "Alias introduction does not change reviewed bytes");
        });
        assertTrue(hookReached.get(), "Alias drift must occur after initial admission and copying");
        assertEquals(FAILED, outcome.status());
        retainedWithoutReceipt(plan, outcome);
        assertEquals(inputs.get("original").sha256(), hash(original));
        assertTrue(Files.isSameFile(original, identical), "Failure does not remove or rewrite selected inputs");
    }

    @Test void unsafeNormalizedRepositoryAliasIsRejectedBeforeWriting() throws Exception {
        var plan = plan(SOURCE);
        var outcome = CompositionRunStager.stagePlan(root.resolve("docs/.."), plan, plan.sha256(), () -> false, NO_HOOK);
        blockedBeforeWrite(plan, outcome);
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void realWindowsDenyReadOnOriginalBlocksPreflightWithoutAnyStagingWrite() throws Exception {
        var plan = plan(SOURCE);
        Path original = root.resolve("inputs/original.jar");
        AtomicReference<FileChannel> held = new AtomicReference<>();
        AtomicBoolean hookReached = new AtomicBoolean();
        assertFalse(Files.exists(root.resolve("build/composition-probe-runs")));
        try {
            held.set(FileChannel.open(original, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_READ));
            assertTrue(held.get().isOpen());
            assertThrows(IOException.class, () -> Files.readAllBytes(original), "Prove native read-sharing denial, not an advisory lock");
            var outcome = stage(plan, (point, directory) -> hookReached.set(true));
            blockedBeforeWrite(plan, outcome);
            assertFalse(outcome.remainderRetained());
            assertFalse(hookReached.get(), "Preflight refusal must precede the first staged intent");
            assertFalse(Files.exists(root.resolve("build/composition-probe-runs")));
        } finally {
            FileChannel channel = held.getAndSet(null);
            if (channel != null) channel.close();
        }
        assertEquals(inputs.get("original").sha256(), hash(original));
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void realWindowsDenyReadOnStagedCopyPreventsReceiptAndPreservesRemainder() throws Exception {
        var plan = plan(SOURCE);
        Path copy = plan.workingDirectory().resolve("mods/fixture.jar");
        AtomicReference<FileChannel> held = new AtomicReference<>();
        AtomicBoolean hookReached = new AtomicBoolean();
        try {
            var outcome = stage(plan, (point, directory) -> {
                if (!point.equals("BEFORE_RECEIPT")) return;
                held.set(FileChannel.open(copy, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_READ));
                hookReached.set(true);
                assertTrue(held.get().isOpen());
                assertThrows(IOException.class, () -> Files.readAllBytes(copy), "Prove the actual Windows read-sharing violation");
            });
            assertTrue(hookReached.get(), "The selected copy must be locked at the final validation boundary");
            assertEquals(FAILED, outcome.status());
            retainedWithoutReceipt(plan, outcome);
            assertNotNull(outcome.error());
        } finally {
            FileChannel channel = held.getAndSet(null);
            if (channel != null) channel.close();
        }
        // Windows may deny even attribute reads while held; verify preserved bytes after release.
        assertEquals(inputs.get("original").sha256(), hash(copy));
        assertEquals(inputs.get("original").sha256(), hash(root.resolve("inputs/original.jar")));
        assertFalse(Files.exists(run(plan).resolve("stage-receipt.json")));
    }

    @Test void symlinkedDestinationAncestorCannotRedirectStaging() throws Exception {
        var plan = plan(SOURCE);
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path alias = root.resolve("build/composition-probe-runs");
        try { Files.createSymbolicLink(alias, outside); }
        catch (IOException | UnsupportedOperationException denied) { assumeTrue(false, "Symbolic links unavailable: " + denied.getClass().getSimpleName()); }
        try {
            var outcome = stage(plan, NO_HOOK);
            assertEquals(BLOCKED, outcome.status());
            try (var files = Files.list(outside)) { assertEquals(0, files.count()); }
        } finally { Files.deleteIfExists(alias); }
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void realWindowsJunctionCannotRedirectDestinationToUnownedDirectory() throws Exception {
        var plan = plan(SOURCE);
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path alias = root.resolve("build/composition-probe-runs");
        junction(alias, outside);
        try {
            var outcome = stage(plan, NO_HOOK);
            assertEquals(BLOCKED, outcome.status());
            assertNull(outcome.receiptSha256());
            try (var files = Files.list(outside)) { assertEquals(0, files.count()); }
        } finally { Files.delete(alias); }
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void sourceDirectoryReplacedWithJunctionCannotPassUnchangedHashes() throws Exception {
        var plan = plan(SOURCE);
        Path alias = root.resolve("inputs"); Path moved = root.resolve("relocated-inputs");
        Files.move(alias, moved); junction(alias, moved);
        try { blockedBeforeWrite(plan, stage(plan, NO_HOOK)); }
        finally { Files.delete(alias); }
    }

    @Test void concurrentInvocationCannotAdoptFirstOperationsDirectory() throws Exception {
        var plan = plan(SOURCE);
        CountDownLatch intentWritten = new CountDownLatch(1), release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> stage(plan, (point, directory) -> {
                if (point.equals("AFTER_INTENT")) {
                    intentWritten.countDown();
                    try { if (!release.await(20, TimeUnit.SECONDS)) throw new IOException("Test release timeout"); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
                }
            }));
            assertTrue(intentWritten.await(20, TimeUnit.SECONDS));
            Map<String, String> whileOwned = snapshot(run(plan));
            var second = stage(plan, NO_HOOK);
            assertEquals(BUSY, second.status());
            assertNull(second.receiptSha256());
            assertEquals(whileOwned, snapshot(run(plan)));
            release.countDown();
            assertEquals(STAGED_UNARMED, first.get(20, TimeUnit.SECONDS).status());
        } finally {
            release.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    private CompositionRunPlanner.Plan plan(CompositionRunPlanner.Role role) throws IOException {
        return CompositionRunPlanner.inspect(root, new CompositionRunPlanner.Request(role, "run-test", javaHome),
                new CompositionRunFiles(root), inputs, INVENTORY, hash(template(role).resolve(launch(role))));
    }
    private CompositionRunStager.Outcome stage(CompositionRunPlanner.Plan plan, CompositionRunStager.Hook hook) {
        return CompositionRunStager.stagePlan(root, plan, plan.sha256(), () -> false, hook);
    }
    private Path run(CompositionRunPlanner.Plan plan) { return plan.workingDirectory().getParent(); }
    private void blockedBeforeWrite(CompositionRunPlanner.Plan plan, CompositionRunStager.Outcome outcome) {
        assertEquals(BLOCKED, outcome.status(), String.valueOf(outcome.error()));
        assertNull(outcome.receiptSha256());
        assertFalse(Files.exists(run(plan), LinkOption.NOFOLLOW_LINKS));
    }
    private void retainedWithoutReceipt(CompositionRunPlanner.Plan plan, CompositionRunStager.Outcome outcome) {
        assertTrue(outcome.remainderRetained());
        assertTrue(Files.isRegularFile(run(plan).resolve("stage-intent.json")));
        assertNull(outcome.receiptSha256());
        assertFalse(Files.exists(run(plan).resolve("stage-receipt.json")));
        assertFalse(Files.exists(plan.workingDirectory().resolve("composition-probe-authorized.txt")));
    }
    private Pin pin(String relative) throws IOException { Path file = root.resolve(relative); return new Pin(relative, hash(file), Files.size(file)); }
    private Path template(CompositionRunPlanner.Role role) { return root.resolve(templateName(role)); }
    private static String templateName(CompositionRunPlanner.Role role) { return "build/" + role.name().toLowerCase(java.util.Locale.ROOT) + "-template"; }
    private static String launch(CompositionRunPlanner.Role role) {
        return role == SOURCE ? "forge-1.16.5-36.2.42.jar" : "libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt";
    }
    private static void jar(Path path, Map<String, String> values) throws IOException {
        Files.createDirectories(path.getParent()); Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        values.forEach((key, value) -> manifest.getMainAttributes().putValue(key, value));
        try (var archive = new JarOutputStream(Files.newOutputStream(path), manifest)) { /* No classes or executable fixture. */ }
    }
    private Map<String, String> inputSnapshot() throws IOException {
        Map<String, String> result = new TreeMap<>();
        for (String relative : List.of("inputs", "jdk", "docs", "build/source-template", "build/target-template")) {
            for (var entry : snapshot(root.resolve(relative)).entrySet()) result.put(relative + "/" + entry.getKey(), entry.getValue());
        }
        return result;
    }
    private static Map<String, String> snapshot(Path directory) throws IOException {
        Map<String, String> result = new TreeMap<>();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.toList()) result.put(directory.relativize(path).toString(),
                    Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ? "DIRECTORY" : hash(path));
        }
        return result;
    }
    private static Map<String, Object> tree(CompositionRunPlanner.Plan plan) throws IOException {
        try { return object(dev.legacybridge.converter.config.BoundedJsonParser.parse(plan.canonicalJson())); }
        catch (dev.legacybridge.converter.config.BoundedJsonParser.JsonSyntaxException malformed) { throw new IOException(malformed); }
    }
    private static CompositionRunPlanner.Plan mutate(CompositionRunPlanner.Plan plan, java.util.function.Consumer<Map<String, Object>> mutation) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>(tree(plan)); mutation.accept(body);
        return withJson(plan, CorpusJson.canonicalJson(body));
    }
    private static CompositionRunPlanner.Plan withJson(CompositionRunPlanner.Plan plan, String json) {
        return new CompositionRunPlanner.Plan(plan.role(), plan.runId(), plan.workingDirectory(), plan.arguments(), plan.copies(),
                json, digest(json.getBytes(StandardCharsets.UTF_8)));
    }
    private static CompositionRunPlanner.Plan withCopies(CompositionRunPlanner.Plan original, List<CompositionRunPlanner.Copy> copies) throws IOException {
        var updated = mutate(original, body -> body.put("copyFiles", copies.stream().map(CompositionRunPlanner.Copy::tree).toList()));
        return new CompositionRunPlanner.Plan(original.role(), original.runId(), original.workingDirectory(), original.arguments(),
                copies, updated.canonicalJson(), updated.sha256());
    }
    private static void junction(Path alias, Path target) throws Exception {
        var process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", alias.toString(), target.toString()).redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }
}
