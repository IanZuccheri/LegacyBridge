package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static dev.legacybridge.corpus.CompositionRunFiles.*;
import static dev.legacybridge.corpus.CompositionRunPlanner.Role.*;
import static org.junit.jupiter.api.Assertions.*;

/** Inert ZIPs and inert java.exe bytes only. These tests never launch a JVM, Forge or a mod. */
final class CompositionRunPlannerTest {
    private static final String INVENTORY = "docs/platform.json";
    private static final String SOURCE_LIBRARY = "libraries/synthetic/source/source-1.0.jar";
    private static final String MODULE = "libraries/synthetic/module/module-1.0.jar";
    private static final String LEGACY = "libraries/synthetic/legacy/legacy-1.0.jar";
    private static final String SOURCE_ARGS = "--gameDir . --launchTarget fmlserver --fml.forgeVersion 36.2.42 "
            + "--fml.mcpVersion 20210115.111550 --fml.mcVersion 1.16.5 --fml.forgeGroup net.minecraftforge";
    @TempDir Path root;
    private Path javaHome;
    private final Map<String, Pin> inputs = new LinkedHashMap<>();
    private final Map<CompositionRunPlanner.Role, List<String>> members = new HashMap<>();
    private Map<String, Object> inventory;

    @BeforeEach void prepare() throws Exception {
        root = root.toRealPath();
        javaHome = Files.createDirectories(root.resolve("jdk/bin")).getParent();
        Files.writeString(javaHome.resolve("bin/java.exe"), "INERT executable selection only, never run");
        Files.writeString(javaHome.resolve("release"), "JAVA_VERSION=\"17.0.12\"\n");
        Files.createDirectories(root.resolve("docs"));
        for (String key : List.of("original", "candidate", "proof", "receipt", "runtime", "probe")) {
            Path file = root.resolve(key + ".jar");
            jar(file, Map.of("Synthetic-Identity", key));
            inputs.put(key, pin(key + ".jar"));
        }
        for (var role : CompositionRunPlanner.Role.values()) {
            Files.createDirectories(template(role));
            Files.writeString(template(role).resolve("eula.txt"), "# Already accepted synthetic setup\neula=true\n");
            List<String> names = new ArrayList<>(CompositionRunPlanner.dynamicPaths(role));
            if (role == SOURCE) {
                names.add(SOURCE_LIBRARY);
                names.add(launch(role));
            } else {
                names.add(MODULE); names.add(LEGACY); names.add(launch(role));
            }
            members.put(role, names);
            for (String name : names) if (!name.equals(launch(role))) jar(template(role).resolve(name), Map.of("Synthetic-Identity", name));
        }
        sourceManifest(Map.of());
        targetArguments(targetLines());
        refreshInventory();
    }

    @Test void sourcePlanIsReadOnlyRepeatableAndNeverCreatesItsRunDirectory() throws Exception {
        Map<String, String> before = snapshot();
        var first = plan(SOURCE);
        var second = plan(SOURCE);
        assertEquals(first, second);
        assertEquals(before, snapshot());
        assertFalse(Files.exists(first.workingDirectory(), LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.exists(root.resolve("build/composition-probe-runs")));
        assertEquals(SOURCE, first.role());
        assertEquals("run-test", first.runId());
        assertEquals(digest(first.canonicalJson().getBytes(StandardCharsets.UTF_8)), first.sha256());
    }

    @Test void targetPlanIsReadOnlyWithClosedFlattenedArgumentsNotAnArgfileCommand() throws Exception {
        Map<String, String> before = snapshot();
        var result = plan(TARGET);
        assertEquals(before, snapshot());
        assertEquals("nogui", result.arguments().get(result.arguments().size() - 1));
        assertTrue(result.arguments().contains("cpw.mods.bootstraplauncher.BootstrapLauncher"));
        assertEquals(List.of("--launchTarget", "forgeserver", "--fml.forgeVersion", "47.4.23",
                        "--fml.mcVersion", "1.20.1", "--fml.forgeGroup", "net.minecraftforge", "--fml.mcpVersion", "20230612.114412", "nogui"),
                result.arguments().subList(result.arguments().indexOf("--launchTarget"), result.arguments().size()));
        assertTrue(result.arguments().stream().noneMatch(value -> value.startsWith("@") || value.contains("javaagent")
                || value.equals("--world") || value.equals("--universe") || value.equals("-jar")));
    }

    @Test void sourceUsesJarLauncherAndOnlyFixedJvmProperties() throws Exception {
        var result = plan(SOURCE);
        assertEquals(javaHome.resolve("bin/java.exe").toString(), result.arguments().get(0));
        assertEquals(List.of("-jar", "forge-1.16.5-36.2.42.jar", "nogui"), result.arguments().subList(6, 9));
        assertEquals(9, result.arguments().size());
        assertTrue(result.arguments().contains("-Dlegacybridge.compositionProbe.run=run-test"));
        assertTrue(result.arguments().stream().noneMatch(value -> value.startsWith("@") || value.equals("cmd.exe")
                || value.equals("powershell.exe") || value.contains("-javaagent") || value.equals("--world") || value.equals("--universe")));
    }

    @Test void copiesOriginalOnlyForSourceAndCandidateRuntimeReceiptOnlyForTarget() throws Exception {
        var source = byDestination(plan(SOURCE));
        var target = byDestination(plan(TARGET));
        assertEquals("original.jar", source.get("mods/fixture.jar").source());
        assertEquals("probe.jar", source.get("mods/composition-probe.jar").source());
        assertFalse(source.containsKey("mods/LegacyBridge.jar"));
        assertFalse(source.containsKey("composition-receipt.json"));
        assertEquals("candidate.jar", target.get("mods/fixture.jar").source());
        assertEquals("runtime.jar", target.get("mods/LegacyBridge.jar").source());
        assertEquals("receipt.jar", target.get("composition-receipt.json").source());
        assertTrue(source.values().stream().noneMatch(copy -> copy.source().equals("candidate.jar")));
        assertTrue(target.values().stream().noneMatch(copy -> copy.source().equals("original.jar")));
        for (var copy : target.values()) {
            assertEquals(hash(root.resolve(copy.source())), copy.sha256());
            assertEquals(Files.size(root.resolve(copy.source())), copy.bytes());
        }
    }

    @Test void copySetContainsExactlySelectedPlatformPlusExplicitRoleInputsAndAcceptedEula() throws Exception {
        assertEquals(members.get(SOURCE).size() + 3, plan(SOURCE).copies().size());
        assertEquals(members.get(TARGET).size() + 5, plan(TARGET).copies().size());
        for (var role : CompositionRunPlanner.Role.values()) {
            var copies = byDestination(plan(role));
            for (String name : members.get(role)) assertTrue(copies.containsKey(name), name);
            assertEquals(templateName(role) + "/eula.txt", copies.get("eula.txt").source());
            assertFalse(copies.containsKey("proof.jar"));
        }
    }

    @Test void limitsAndEnvironmentAreDeclarationsOnlyAndNeverAppliedByPlanning() throws Exception {
        Map<String, String> environmentBefore = System.getenv();
        var result = plan(TARGET);
        Map<String, Object> tree = tree(result);
        assertEquals(false, tree.get("executionAllowed"));
        assertEquals(false, tree.get("installationAllowed"));
        assertEquals("NOT_RUN", tree.get("runtimeValidation"));
        assertEquals("PLANNED_ONLY", tree.get("status"));
        assertEquals(false, object(tree.get("java")).get("executed"));
        Map<String, Object> limits = object(tree.get("limits"));
        assertEquals(2048, ((Number) limits.get("maxHeapMiB")).intValue());
        assertEquals(1, ((Number) limits.get("maxConcurrentGames")).intValue());
        assertEquals(240000, ((Number) limits.get("timeoutMillis")).intValue());
        assertEquals(List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"), tree.get("removeEnvironment"));
        assertEquals(environmentBefore, System.getenv());
        assertTrue(tree.get("pending").toString().contains("EXPLICIT_NATIVE_AUTHORIZATION"));
        assertTrue(tree.get("pending").toString().contains("NATIVE_JAVA17_SOURCE_VALIDATION"));
        assertFalse(Files.exists(result.workingDirectory()));
    }

    @Test void generatedPropertiesMatchExactWorldAndIncludeSimulationDistanceOnlyOnTarget() throws Exception {
        for (var role : CompositionRunPlanner.Role.values()) {
            Map<String, Object> generated = object(tree(plan(role)).get("generatedFiles"));
            assertEquals("run-test\n", generated.get("composition-probe-authorized.txt"));
            Properties props = new Properties();
            props.load(new java.io.StringReader((String) generated.get("server.properties")));
            assertEquals("world", props.getProperty("level-name"));
            assertEquals("127.0.0.1", props.getProperty("server-ip"));
            assertEquals("false", props.getProperty("enable-rcon"));
            assertEquals("false", props.getProperty("enable-query"));
            assertEquals("true", props.getProperty("white-list"));
            assertEquals(role == TARGET ? "2" : null, props.getProperty("simulation-distance"));
        }
    }

    @Test void returnedListsAndReportAreImmutableAndDigestTracksCanonicalPlan() throws Exception {
        var result = plan(TARGET);
        assertThrows(UnsupportedOperationException.class, () -> result.arguments().add("--world"));
        assertThrows(UnsupportedOperationException.class, () -> result.copies().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.report().put("executionAllowed", true));
        assertEquals(result.sha256(), result.report().get("planSha256"));
        assertEquals(false, result.report().get("executionAllowed"));
        assertEquals(false, result.report().get("gameExecutionVerified"));
        assertDoesNotThrow(() -> CorpusJson.canonicalJson(result.report()));
    }

    @Test void changedSelectedInputBytesBlockWithoutCreatingAnyDestination() throws Exception {
        Files.writeString(root.resolve("candidate.jar"), "CHANGED INERT BYTES");
        rejected(TARGET, "PIN_DRIFT");
        assertFalse(Files.exists(root.resolve("build/composition-probe-runs")));
    }

    @Test void missingMandatoryInputBlocksStructuredPlanning() throws Exception {
        inputs.remove("runtime");
        rejected(TARGET, "MISSING_INPUT");
    }

    @Test void missingSelectedPlatformFileBlocksWithoutUsingAnotherTemplate() throws Exception {
        Files.delete(template(TARGET).resolve(LEGACY));
        assertThrows(IOException.class, () -> plan(TARGET));
        assertFalse(Files.exists(root.resolve("build/composition-probe-runs")));
    }

    @Test void changedPlatformBytesDoNotSilentlyRefreshTheInventoryPins() throws Exception {
        Files.writeString(template(SOURCE).resolve(SOURCE_LIBRARY), "drift");
        rejected(SOURCE, "PIN_DRIFT");
    }

    @Test void explicitLaunchHashMismatchBlocksEvenWhenInventoryWasUpdated() throws Exception {
        var request = request(SOURCE, "run-test", javaHome);
        assertThrows(IOException.class, () -> CompositionRunPlanner.inspect(root, request,
                new CompositionRunFiles(root), inputs, INVENTORY, "0".repeat(64)));
    }

    @Test void reorderedTargetArgsAreRejectedDespiteMatchingNewSyntheticPin() throws Exception {
        List<String> lines = targetLines(); Collections.swap(lines, 2, 3);
        targetArguments(lines); refreshInventory();
        rejected(TARGET, "ARGFILE_ORDER");
    }

    @Test void injectedUniverseWorldArgfileAndAgentsCannotEnterTheClosedTargetArgs() throws Exception {
        for (String injected : List.of("--universe ../playerworlds", "--world external", "@hidden.args", "-javaagent:agent.jar")) {
            List<String> lines = targetLines(); lines.set(6, injected);
            targetArguments(lines); refreshInventory();
            rejected(TARGET, "ARGFILE_ORDER");
        }
    }

    @Test void extraTargetArgumentLineIsRejected() throws Exception {
        List<String> lines = targetLines(); lines.add("--world bad");
        targetArguments(lines); refreshInventory();
        rejected(TARGET, "ARGFILE_LINES");
    }

    @Test void targetIgnoreListMustMatchActualModuleBasenames() throws Exception {
        List<String> lines = targetLines(); lines.set(7, "-DignoreList=another.jar");
        targetArguments(lines); refreshInventory();
        rejected(TARGET, "IGNORE_LIST");
    }

    @Test void duplicateTargetClasspathEntryIsRejected() throws Exception {
        List<String> lines = targetLines(); lines.set(9, "-DlegacyClassPath=" + LEGACY + ";" + LEGACY);
        targetArguments(lines); refreshInventory();
        rejected(TARGET, "CLASSPATH_SIZE");
    }

    @Test void missingExactDynamicDependencyBlocksBothRoles() throws Exception {
        for (var role : CompositionRunPlanner.Role.values()) {
            String missing = CompositionRunPlanner.dynamicPaths(role).get(0);
            members.get(role).remove(missing); refreshInventory();
            rejected(role, "DYNAMIC_DEPENDENCY");
            members.get(role).add(missing); refreshInventory();
        }
    }

    @Test void wrongSourceMainAndSourceWorldOverrideCannotBeForwarded() throws Exception {
        sourceManifest(Map.of("Main-Class", "synthetic.UnauthorizedMain")); refreshInventory();
        rejected(SOURCE, "SOURCE_MAIN");
        sourceManifest(Map.of("ServerLaunchArgs", SOURCE_ARGS + " --world outside")); refreshInventory();
        rejected(SOURCE, "SOURCE_ARGUMENTS");
    }

    @Test void duplicatedSourceManifestClasspathIsRejected() throws Exception {
        sourceManifest(Map.of("Class-Path", SOURCE_LIBRARY + " " + SOURCE_LIBRARY)); refreshInventory();
        assertThrows(IOException.class, () -> plan(SOURCE));
    }

    @Test void transitiveManifestCannotEscapeEvenWhenItsLibraryHashIsCorrect() throws Exception {
        jar(template(SOURCE).resolve(SOURCE_LIBRARY), Map.of("Class-Path", "../../outside.jar"));
        refreshInventory();
        assertThrows(IOException.class, () -> plan(SOURCE));
    }

    @Test void transitiveManifestCannotAddAnUndeclaredLocalJar() throws Exception {
        jar(template(SOURCE).resolve(SOURCE_LIBRARY), Map.of("Class-Path", "unreviewed.jar"));
        jar(template(SOURCE).resolve("libraries/synthetic/source/unreviewed.jar"), Map.of());
        refreshInventory();
        assertThrows(IOException.class, () -> plan(SOURCE));
    }

    @Test void completeMainManifestMayEndAfterSingleLfWithoutBlankSectionDelimiter() throws Exception {
        rawManifest(template(SOURCE).resolve(SOURCE_LIBRARY), "Manifest-Version: 1.0\nSynthetic-Identity: eof-after-lf\n");
        refreshInventory();
        assertEquals(SOURCE, plan(SOURCE).role());
    }

    @Test void unterminatedMainManifestCannotHideItsLastClasspathLine() throws Exception {
        rawManifest(template(SOURCE).resolve(SOURCE_LIBRARY), "Manifest-Version: 1.0\nClass-Path: hidden.jar");
        refreshInventory();
        assertThrows(IOException.class, () -> plan(SOURCE));
    }

    @Test void inventoryRejectsDuplicatePathsUnknownKeysAndNonRedistributableDevelopmentFiles() throws Exception {
        Map<String, Object> platform = platform(SOURCE);
        List<Object> entries = entries(platform); entries.add(entries.get(0)); writeInventory();
        rejected(SOURCE, "DUPLICATE_PLATFORM_PATH");
        refreshInventory(); platform(SOURCE).put("unreviewed", true); writeInventory();
        rejected(SOURCE, "KEYS");
        refreshInventory();
        String development = "libraries/synthetic/forge-userdev.jar";
        jar(template(SOURCE).resolve(development), Map.of()); members.get(SOURCE).add(development); refreshInventory();
        rejected(SOURCE, "DEVELOPMENT_ARTIFACT");
    }

    @Test void unsafeRelativePathsAndWindowsDevicesAreRejectedLexically() throws Exception {
        for (String path : List.of("../x", "a/../x", "/x", "C:/x", "a\\x", "a/x.jar:stream", "a/%2e%2e/x",
                "a/x.jar;cmd", "a/CON", "a/AUX.jar", "a/LPT1.txt", "a/trailing.", "a/space name.jar", "@args", "a/\n.jar")) {
            assertThrows(IOException.class, () -> relative(path), path);
        }
        Map<String, Object> first = object(entries(platform(SOURCE)).get(0)); first.put("path", "../unknown.jar");
        writeInventory(); rejected(SOURCE, "PATH");
    }

    @Test void wrongInventorySchemaAndPinFormatAreRejected() throws Exception {
        inventory.put("schemaVersion", 2); writeInventory(); rejected(SOURCE, "INVENTORY_SCHEMA");
        refreshInventory(); object(entries(platform(SOURCE)).get(0)).put("sha256", "bad");
        writeInventory(); rejected(SOURCE, "PIN_FORMAT");
    }

    @Test void invalidRunIdsDoNotSelectDirectories() throws Exception {
        for (String id : List.of("test", "run-", "run-../bad", "run-UPPER", "run-a/b", "run-" + "a".repeat(42))) {
            var request = request(SOURCE, id, javaHome);
            assertThrows(IOException.class, () -> inspect(request));
        }
    }

    @Test void existingHalfRunAndUnknownFilesAreNeverReusedOrAltered() throws Exception {
        Path existing = Files.createDirectories(root.resolve("build/composition-probe-runs/run-test/target"));
        Path sentinel = existing.resolve("unknown.txt"); Files.writeString(sentinel, "preserve me");
        Map<String, String> before = snapshot();
        rejected(SOURCE, "DESTINATION_EXISTS"); rejected(TARGET, "DESTINATION_EXISTS");
        assertEquals("preserve me", Files.readString(sentinel));
        assertEquals(before, snapshot());
    }

    @Test void nonCanonicalJavaHomeIsRejectedRatherThanNormalized() throws Exception {
        assertThrows(IOException.class, () -> inspect(request(SOURCE, "run-test", javaHome.resolve("bin/.."))));
        assertThrows(IOException.class, () -> inspect(request(SOURCE, "run-test", Path.of("jdk"))));
    }

    @Test void previouslyUnacceptedMissingOrDuplicateEulaDoesNotBecomeConsent() throws Exception {
        Path eula = template(SOURCE).resolve("eula.txt");
        Files.writeString(eula, "eula=false\n"); rejected(SOURCE, "EULA_NOT_ACCEPTED");
        assertEquals("eula=false\n", Files.readString(eula));
        Files.writeString(eula, "eula=true\neula=false\n"); rejected(SOURCE, "PROPERTIES");
        Files.delete(eula); assertThrows(IOException.class, () -> plan(SOURCE));
    }

    @Test void javaEightEarlyAccessMalformedOrDuplicatedReleaseVersionCannotSatisfyJava17() throws Exception {
        for (String version : List.of("\"1.8.0_492\"", "\"21.0.1\"", "\"17-ea\"", "17.0.12", "\"17evil\"")) {
            Files.writeString(javaHome.resolve("release"), "JAVA_VERSION=" + version + "\n");
            rejected(SOURCE, "JAVA_17_REQUIRED");
        }
        Files.writeString(javaHome.resolve("release"), "JAVA_VERSION=\"17\"\nJAVA_VERSION=\"8\"\n");
        rejected(SOURCE, "PROPERTIES");
    }

    @Test void ordinaryQuotedJava17ReleaseFormsRemainDeclaredNotExecuted() throws Exception {
        for (String version : List.of("\"17\"", "\"17.0.12\"", "\"17.0.12+7\"")) {
            Files.writeString(javaHome.resolve("release"), "JAVA_VERSION=" + version + "\n");
            Map<String, Object> java = object(tree(plan(SOURCE)).get("java"));
            assertEquals(version, java.get("declaredVersion"));
            assertEquals(false, java.get("executed"));
        }
    }

    @Test void malformedUtf8InReleaseCommentCannotHideBehindAValidJavaVersion() throws Exception {
        byte[] prefix = "JAVA_VERSION=\"17.0.12\"\n# invalid UTF-8: ".getBytes(StandardCharsets.UTF_8);
        byte[] malformed = java.util.Arrays.copyOf(prefix, prefix.length + 3);
        malformed[prefix.length] = (byte) 0xc3;
        malformed[prefix.length + 1] = 0x28;
        malformed[prefix.length + 2] = '\n';
        Files.write(javaHome.resolve("release"), malformed);
        Map<String, String> before = snapshot();
        assertThrows(IOException.class, () -> plan(SOURCE));
        assertEquals(before, snapshot());
        assertFalse(Files.exists(root.resolve("build/composition-probe-runs"), LinkOption.NOFOLLOW_LINKS));
    }

    @Test void oversizedReleaseOrEulaIsRejectedWithoutExecutingTheSelectedJava() throws Exception {
        Files.writeString(javaHome.resolve("release"), "x".repeat(8193)); rejected(SOURCE, "JAVA_RELEASE_SIZE");
        Files.writeString(javaHome.resolve("release"), "JAVA_VERSION=\"17\"\n");
        Files.writeString(template(SOURCE).resolve("eula.txt"), "#" + "x".repeat(8192) + "\neula=true\n");
        rejected(SOURCE, "TEXT_SIZE");
    }

    @Test void hardLinkedInputAliasesAreRejectedEvenWithMatchingDeclaredHashes() throws Exception {
        Files.delete(root.resolve("runtime.jar"));
        Files.createLink(root.resolve("runtime.jar"), root.resolve("original.jar"));
        inputs.put("runtime", pin("runtime.jar"));
        rejected(TARGET, "FILE_ALIAS");
        assertTrue(Files.isSameFile(root.resolve("runtime.jar"), root.resolve("original.jar")));
    }

    @Test void regularByteIdenticalButIndependentInputFilesAreNotMistakenForAliases() throws Exception {
        Files.copy(root.resolve("original.jar"), root.resolve("another.jar"));
        var files = new CompositionRunFiles(root);
        assertEquals(files.read("original.jar").sha256(), files.read("another.jar").sha256());
        assertFalse(Files.isSameFile(root.resolve("original.jar"), root.resolve("another.jar")));
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void genuineWindowsJunctionCannotAliasJavaHomeOrTheDestinationParents() throws Exception {
        Path alias = root.resolve("jdk-alias");
        junction(alias, javaHome);
        try {
            assertThrows(IOException.class, () -> inspect(request(SOURCE, "run-test", alias)));
            Path outside = Files.createDirectory(root.resolve("outside"));
            Path runs = root.resolve("build/composition-probe-runs");
            junction(runs, outside);
            try {
                assertThrows(IOException.class, () -> plan(SOURCE));
                try (var entries = Files.list(outside)) { assertEquals(List.of(), entries.toList()); }
            } finally {
                // Delete the junction itself, never traverse or recursively delete its target.
                Files.delete(runs);
            }
        } finally {
            Files.delete(alias);
        }
    }

    @Test void inputReaderFileCountLimitRefuses513thSelection() throws Exception {
        var files = new CompositionRunFiles(root);
        for (int i = 0; i < 512; i++) {
            String path = "tiny-" + i + ".txt"; Files.writeString(root.resolve(path), "inert-" + i); files.read(path);
        }
        Files.writeString(root.resolve("tiny-extra.txt"), "inert-extra");
        assertTrue(assertThrows(IOException.class, () -> files.read("tiny-extra.txt")).getMessage().contains("FILE_BUDGET"));
    }

    @Test void inputReaderRejectsEmptyOversizedAndMalformedUtf8Files() throws Exception {
        Files.write(root.resolve("empty.txt"), new byte[0]);
        assertThrows(IOException.class, () -> new CompositionRunFiles(root).read("empty.txt"));
        try (var sparse = java.nio.channels.FileChannel.open(root.resolve("large.bin"), java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE)) {
            sparse.position(128L << 20); sparse.write(java.nio.ByteBuffer.wrap(new byte[] {1}));
        }
        assertTrue(assertThrows(IOException.class, () -> new CompositionRunFiles(root).read("large.bin")).getMessage().contains("FILE_SIZE"));
        Files.write(root.resolve("invalid.txt"), new byte[] {(byte) 0xc3, 0x28});
        var files = new CompositionRunFiles(root); var invalid = files.read("invalid.txt");
        assertThrows(IOException.class, () -> files.text(invalid, 10));
    }

    private CompositionRunPlanner.Plan plan(CompositionRunPlanner.Role role) throws IOException {
        return inspect(request(role, "run-test", javaHome));
    }
    private CompositionRunPlanner.Plan inspect(CompositionRunPlanner.Request request) throws IOException {
        return CompositionRunPlanner.inspect(root, request, new CompositionRunFiles(root), inputs, INVENTORY,
                hash(template(request.role()).resolve(launch(request.role()))));
    }
    private static CompositionRunPlanner.Request request(CompositionRunPlanner.Role role, String id, Path java) {
        return new CompositionRunPlanner.Request(role, id, java);
    }
    private void rejected(CompositionRunPlanner.Role role, String code) {
        assertTrue(assertThrows(IOException.class, () -> plan(role)).getMessage().contains(code), code);
    }
    private Map<String, Object> tree(CompositionRunPlanner.Plan plan) throws IOException {
        try { return object(dev.legacybridge.converter.config.BoundedJsonParser.parse(plan.canonicalJson())); }
        catch (dev.legacybridge.converter.config.BoundedJsonParser.JsonSyntaxException bad) { throw new IOException(bad); }
    }
    private Map<String, CompositionRunPlanner.Copy> byDestination(CompositionRunPlanner.Plan plan) {
        Map<String, CompositionRunPlanner.Copy> result = new LinkedHashMap<>();
        for (var copy : plan.copies()) assertNull(result.put(copy.destination(), copy));
        return result;
    }
    private static String templateName(CompositionRunPlanner.Role role) { return "build/" + role.name().toLowerCase(java.util.Locale.ROOT) + "-template"; }
    private Path template(CompositionRunPlanner.Role role) { return root.resolve(templateName(role)); }
    private static String launch(CompositionRunPlanner.Role role) {
        return role == SOURCE ? "forge-1.16.5-36.2.42.jar" : "libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt";
    }
    private Pin pin(String relative) throws IOException {
        Path path = root.resolve(relative); return new Pin(relative, hash(path), Files.size(path));
    }
    private void sourceManifest(Map<String, String> overrides) throws IOException {
        Map<String, String> values = new LinkedHashMap<>(Map.of("Main-Class", "net.minecraftforge.server.ServerMain",
                "Class-Path", SOURCE_LIBRARY, "ServerLaunchArgs", SOURCE_ARGS));
        values.putAll(overrides); jar(template(SOURCE).resolve(launch(SOURCE)), values);
    }
    private void targetArguments(List<String> lines) throws IOException {
        Path path = template(TARGET).resolve(launch(TARGET)); Files.createDirectories(path.getParent());
        Files.writeString(path, String.join("\n", lines) + "\n");
    }
    private static List<String> targetLines() {
        return new ArrayList<>(List.of("-p " + MODULE,
                "--add-modules ALL-MODULE-PATH", "--add-opens java.base/java.util.jar=cpw.mods.securejarhandler",
                "--add-opens java.base/java.lang.invoke=cpw.mods.securejarhandler",
                "--add-exports java.base/sun.security.util=cpw.mods.securejarhandler",
                "--add-exports jdk.naming.dns/com.sun.jndi.dns=java.naming", "-Djava.net.preferIPv6Addresses=system",
                "-DignoreList=module-1.0.jar", "-DlibraryDirectory=libraries", "-DlegacyClassPath=" + LEGACY,
                "cpw.mods.bootstraplauncher.BootstrapLauncher", "--launchTarget forgeserver", "--fml.forgeVersion 47.4.23",
                "--fml.mcVersion 1.20.1", "--fml.forgeGroup net.minecraftforge", "--fml.mcpVersion 20230612.114412"));
    }
    private void refreshInventory() throws IOException {
        Map<String, Object> platforms = new LinkedHashMap<>();
        for (var role : CompositionRunPlanner.Role.values()) {
            List<Map<String, Object>> files = new ArrayList<>();
            for (String name : members.get(role)) {
                Path path = template(role).resolve(name);
                files.add(new LinkedHashMap<>(Map.of("path", name, "sha256", hash(path), "bytes", Files.size(path))));
            }
            platforms.put(role.name(), new LinkedHashMap<>(Map.of("templateRoot", templateName(role), "launchFile", launch(role), "files", files)));
        }
        inventory = new LinkedHashMap<>(Map.of("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_PLATFORM_INVENTORY",
                "provenance", "Synthetic inert fixture, never a native launch proof", "platforms", platforms));
        writeInventory();
    }
    private void writeInventory() throws IOException { Files.writeString(root.resolve(INVENTORY), CorpusJson.canonicalJson(inventory)); }
    private Map<String, Object> platform(CompositionRunPlanner.Role role) throws IOException {
        return object(object(inventory.get("platforms")).get(role.name()));
    }
    @SuppressWarnings("unchecked") private static List<Object> entries(Map<String, Object> platform) { return (List<Object>) platform.get("files"); }
    private static void jar(Path path, Map<String, String> values) throws IOException {
        Files.createDirectories(path.getParent()); Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        values.forEach((key, value) -> manifest.getMainAttributes().putValue(key, value));
        try (var zip = new JarOutputStream(Files.newOutputStream(path), manifest)) { /* Inert manifest, no class entries. */ }
    }
    private static void rawManifest(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
    }
    private Map<String, String> snapshot() throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                String name = root.relativize(path).toString();
                result.put(name, Files.isDirectory(path) ? "DIRECTORY" : hash(path));
            }
        }
        return result;
    }
    private static void junction(Path alias, Path target) throws Exception {
        var process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", alias.toString(), target.toString()).redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }
}
