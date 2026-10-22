package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.ConversionResult;
import dev.legacybridge.converter.mapping.MappingReport;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.report.CompatibilityReport;
import dev.legacybridge.converter.report.Severity;
import dev.legacybridge.converter.service.ConversionPlan;
import dev.legacybridge.converter.service.ConversionRequest;
import dev.legacybridge.converter.service.ConversionProfile;
import dev.legacybridge.converter.service.EngineJob;
import dev.legacybridge.converter.service.EngineOutcome;
import dev.legacybridge.converter.service.InventoryResult;
import dev.legacybridge.converter.service.LegacyEngineService;
import dev.legacybridge.converter.service.MappingData;

import java.io.ByteArrayOutputStream;
import java.io.FilePermission;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketPermission;
import java.net.URLPermission;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Standalone Java 17 validation helper, not a JUnit test or shipped component.
 * Run only with copied installed CLI libraries and this test class below an
 * isolated directory outside the repository. Does not call the CLI launcher.
 *
 * <p>The deprecated SecurityManager is a deliberately test-only tripwire for
 * Java-level filesystem/network/process access, not a production sandbox,
 * protection from native code, or proof of gameplay behavior.</p>
 */
@SuppressWarnings("removal")
public final class CachelessEngineProbe {
    private static final String ORIGINAL = "legacybridge-example-1.16.5-1.0.0.jar";
    private static final int MAX_CAPTURE = 4 * 1024 * 1024;

    private CachelessEngineProbe() { }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 2 && arguments[1].equals("--composition")) { composition(arguments[0]); return; }
        if (arguments.length == 2 && arguments[1].equals("--builtin-cohort")) { cohort(arguments[0]); return; }
        boolean builtin = arguments.length == 4 && arguments[3].equals("--builtin-simple-content");
        require(arguments.length == 1 || arguments.length == 3 || builtin,
                "Expected isolated root, optionally exact profile/input and explicit --builtin-simple-content");
        ConversionProfile profile = arguments.length == 1 ? ConversionProfile.SIMPLE_CONTENT_V1 : ConversionProfile.parse(arguments[1]);
        require(!builtin || profile == ConversionProfile.SIMPLE_CONTENT_V1, "Included data is scoped to simple-content-v1");
        String originalName = arguments.length == 1 ? ORIGINAL : arguments[2];
        require(originalName.matches("[A-Za-z0-9_.-]+\\.jar"), "Input filename must be a single safe JAR basename");
        require(Runtime.version().feature() == 17, "Probe is specifically a Java 17 test");
        Path root = Path.of(arguments[0]).toAbsolutePath().normalize().toRealPath();
        require(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().equals(root),
                "Working directory must be the isolated root");
        Path javaHome = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize().toRealPath();
        Path userHome = requireEmptyDirectory(root, System.getProperty("user.home"), "user.home");
        Path javaTemp = requireEmptyDirectory(root, System.getProperty("java.io.tmpdir"), "java.io.tmpdir");
        Path gradleHome = requireEmptyDirectory(root, System.getenv("GRADLE_USER_HOME"), "GRADLE_USER_HOME");
        require(!userHome.equals(javaTemp) && !userHome.equals(gradleHome) && !javaTemp.equals(gradleHome),
                "Isolated home/temp/Gradle directories must be distinct");
        require(System.getenv("LEGACYBRIDGE_MAPPING_BUNDLE") == null
                        || System.getenv("LEGACYBRIDGE_MAPPING_BUNDLE").isBlank(),
                "Unset LEGACYBRIDGE_MAPPING_BUNDLE to verify missing-data behavior");
        require(System.getSecurityManager() == null, "Probe requires its own test-only guard");
        requireClasspathInsideRoot(root);

        Path input = root.resolve("input/" + originalName);
        Path bundle = root.resolve("bundle");
        Path cliOutput = root.resolve("cli/converted.jar");
        Path serviceOutput = root.resolve("service/converted.jar");
        Path reports = root.resolve("reports");
        require(Files.isRegularFile(input), "Fixture must be present inside input/");
        require(builtin ? !Files.exists(bundle) : Files.isDirectory(bundle),
                builtin ? "Built-in validation must have no external bundle" : "Explicit mapping bundle must be present inside bundle/");
        Files.createDirectories(cliOutput.getParent());
        Files.createDirectories(serviceOutput.getParent());
        Files.createDirectories(reports);
        require(!Files.exists(cliOutput) && !Files.exists(serviceOutput), "Probe conversion outputs must be new");

        ProbeGuard guard = new ProbeGuard(root, javaHome);
        System.setSecurityManager(guard);
        guard.selfTest();
        String originalHash = hash(input);
        CanonicalReportWriter writer = new CanonicalReportWriter();
        MappingData mappingData = builtin ? MappingData.builtinSimpleContent() : MappingData.bundle(bundle);
        ConversionRequest request = new ConversionRequest(input, serviceOutput, mappingData, Optional.of(originalHash), profile);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schemaVersion", 1);
        evidence.put("reportType", "LEGACY_CACHELESS_ENGINE_PROBE");
        evidence.put("supportProfile", profile.id());
        evidence.put("javaFeatureVersion", Runtime.version().feature());
        evidence.put("inputSha256", originalHash);
        evidence.put("testOnlySecurityManager", true);
        evidence.put("productionSandboxClaim", false);
        evidence.put("gameplayValidated", false);
        evidence.put("runtimeLaunched", false);
        evidence.put("javaHomeReadException", true);
        evidence.put("exactAncestorMetadataReadException", true);
        evidence.put("developerCacheRequired", false);
        evidence.put("initialUserHomeEmpty", true);
        evidence.put("initialJavaTempEmpty", true);
        evidence.put("initialGradleHomeEmpty", true);
        evidence.put("dataMode", builtin ? "BUILTIN_SIMPLE_CONTENT" : "EXPLICIT_PORTABLE_BUNDLE");
        evidence.put("externalMappingBundlePresent", !builtin);

        try (LegacyEngineService engine = new LegacyEngineService()) {
            var dataReviewJob = engine.inspectMappingData(mappingData);
            var dataReview = completed(dataReviewJob);
            var cliReview = builtin ? cli("inspect-data", "--builtin", "simple-content-v1")
                    : cli("inspect-data", "--mapping-bundle", bundle.toString());
            require(cliReview.exitCode() == 0, "Data inspection CLI failed");
            equivalent(reports, "data", writer.toJsonTree(dataReview.toTree()), cliReview.stdout());
            evidence.put("mappingDataReview", dataReview.toTree());
            evidence.put("dataReviewMetrics", dataReviewJob.metrics().toTree());
            // If the engine tried the real environment's cache, the guard would fail.
            EngineOutcome<MappingReport> missing = await(engine.mappings(input, MappingData.unavailable()));
            require(missing.status() == EngineOutcome.Status.FAILED && missing.value().isEmpty()
                            && missing.error().orElseThrow().code().equals("MAPPING_DATA_REQUIRED"),
                    "Unavailable mapping data must be a typed MAPPING_DATA_REQUIRED failure");
            CliResult missingCli = cli("mappings", input.toString());
            require(missingCli.exitCode() == ExitCode.INPUT_REJECTED.value()
                            && missingCli.stdout().isEmpty() && missingCli.stderr().contains("[MAPPING_DATA_REQUIRED]"),
                    "Unconfigured CLI must report MAPPING_DATA_REQUIRED without fallback");
            evidence.put("missingMappingDataCode", missing.error().orElseThrow().code());
            evidence.put("missingMappingDataCliExitCode", missingCli.exitCode());

            InventoryResult inventory = completed(engine.inventory(input.getParent()));
            require(!inventory.truncated() && inventory.entries().size() == 1
                            && inventory.entries().get(0).error().isEmpty()
                            && !inventory.entries().get(0).duplicateModId(),
                    "Fixture inventory must be complete and unambiguous");
            evidence.put("inventoryEntries", inventory.entries().size());

            CompatibilityReport analysis = completed(engine.analyze(input));
            CliResult cliAnalysis = cli("analyze", input.toString());
            require(cliAnalysis.exitCode() == (analysis.hasAtLeast(Severity.ERROR) ? 1 : 0), "Analysis exit-code mismatch");
            equivalent(reports, "analyze", writer.toJson(analysis), cliAnalysis.stdout());
            evidence.put("analysisCanonicalJsonEqual", true);
            evidence.put("analysisCliExitCode", cliAnalysis.exitCode());
            evidence.put("analyzedClassCount", analysis.analysis().classCount());
            evidence.put("modId", analysis.modId());
            evidence.put("modVersion", analysis.modVersion());

            if (!builtin) {
                MappingReport mappings = completed(engine.mappings(input, mappingData));
                CliResult cliMappings = cli("mappings", input.toString(), "--mapping-bundle", bundle.toString());
                require(cliMappings.exitCode() == (mappings.requiresReview() ? 1 : 0), "Mappings exit-code mismatch");
                equivalent(reports, "mappings", writer.toJsonTree(mappings.toTree()), cliMappings.stdout());
                require(mappings.sourcesComplete() && !mappings.referencesTruncated() && mappings.unreadableClassCount() == 0,
                        "Pinned fixture mapping inventory must be complete; unresolved rules remain reviewable");
                evidence.put("mappingsCanonicalJsonEqual", true);
                evidence.put("mappingsCliExitCode", cliMappings.exitCode());
                evidence.put("mappingSourceCount", mappings.sources().size());
                evidence.put("mappingSymbolCount", mappings.entries().size());
                evidence.put("mappingObservedReferences", mappings.observedReferenceCount());
                evidence.put("mappingStatusCounts", mappings.toTree().get("statusCounts"));
            }

            ConversionPlan plan = completed(engine.plan(request));
            require(plan.convertible() && plan.candidateSha256().isPresent()
                            && plan.inputSha256().equals(originalHash) && !Files.exists(serviceOutput),
                    "Planning must prove the fixture without publishing it");
            evidence.put("planConvertible", true);
            evidence.put("planPublishedOutput", false);

            CliResult cliConversion = builtin
                    ? cli("convert", input.toString(), "--output", cliOutput.toString())
                    : cli("convert", input.toString(), "--mapping-bundle", bundle.toString(),
                            "--conversion-profile", profile.id(), "--output", cliOutput.toString());
            require(cliConversion.exitCode() == 0, "Packaged CLI conversion failed: " + cliConversion.stderr());
            var conversionJob = engine.convert(request);
            ConversionResult converted = completed(conversionJob);
            evidence.put("conversionMetrics", conversionJob.metrics().toTree());
            require(converted.converted(), "Typed service refused the fixture");
            equivalent(reports, "convert", writer.toJsonTree(converted.report()), cliConversion.stdout());
            require(Arrays.equals(boundedBytes(cliOutput), boundedBytes(serviceOutput)), "CLI/service JAR bytes differ");
            String convertedHash = hash(serviceOutput);
            require(convertedHash.equals(hash(cliOutput)) && convertedHash.equals(plan.candidateSha256().orElseThrow()),
                    "Planned and published candidate fingerprints differ");
            require(originalHash.equals(hash(input)), "Original fixture bytes changed");
            evidence.put("conversionCanonicalJsonEqual", true);
            evidence.put("convertedJarBytesEqual", true);
            evidence.put("convertedJarSha256", convertedHash);
            evidence.put("convertedJarBytes", Files.size(serviceOutput));
            evidence.put("originalUnchanged", true);
            evidence.put("conversionCliExitCode", cliConversion.exitCode());
            evidence.put("conversionStatus", converted.report().get("status"));
            evidence.put("bytecodeValidation", converted.report().get("bytecodeValidation"));
        }

        requireEmptyDirectory(root, gradleHome.toString(), "GRADLE_USER_HOME after operations");
        requireEmptyDirectory(root, userHome.toString(), "user.home after operations");
        requireEmptyDirectory(root, javaTemp.toString(), "java.io.tmpdir after operations");
        guard.assertNoUnexpectedAttempts();
        evidence.put("forbiddenFileAttempts", guard.fileAttempts.get());
        evidence.put("networkAttempts", guard.networkAttempts.get());
        evidence.put("processExecutionAttempts", guard.executionAttempts.get());
        evidence.put("processExitAttempts", guard.exitAttempts.get());
        evidence.put("gradleHomeStillEmpty", true);
        evidence.put("userHomeStillEmpty", true);
        evidence.put("ownedTemporaryDirectoryEmpty", true);
        evidence.put("status", "PASSED");
        Files.writeString(reports.resolve("cacheless-engine-evidence.json"), writer.toJsonTree(evidence),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println("CACHELESS_ENGINE_EQUIVALENCE_OK modId=" + evidence.get("modId")
                + " sha256=" + evidence.get("convertedJarSha256")
                + " canonicalReports=" + (builtin ? 3 : 4)
                + " originalUnchanged=true externalFileAttempts=0 networkAttempts=0");
    }

    private static Path requireEmptyDirectory(Path root, String value, String label) throws IOException {
        require(value != null && !value.isBlank(), label + " must be explicitly configured");
        Path directory = Path.of(value).toAbsolutePath().normalize();
        require(directory.startsWith(root) && !directory.equals(root) && Files.isDirectory(directory),
                label + " must be an existing directory inside the isolated root");
        require(directory.equals(directory.toRealPath()), label + " must not redirect elsewhere");
        try (var files = Files.list(directory)) { require(files.findAny().isEmpty(), label + " must be empty"); }
        return directory;
    }

    private static void requireClasspathInsideRoot(Path root) throws IOException {
        String[] entries = System.getProperty("java.class.path").split(Pattern.quote(System.getProperty("path.separator")));
        require(entries.length > 1, "Use copied probe classes and actual installed CLI libraries");
        for (String entry : entries) {
            Path path = Path.of(entry).toAbsolutePath().normalize().toRealPath();
            require(path.startsWith(root) && !path.startsWith(root.resolve("input"))
                            && !path.startsWith(root.resolve("bundle")),
                    "Classpath must contain only copied probe/CLI code, not input mods or developer files");
        }
    }

    private static CliResult cli(String... arguments) {
        BoundedCapture stdout = new BoundedCapture();
        BoundedCapture stderr = new BoundedCapture();
        int exit;
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            // This test presentation check is not an interface/controller implementation.
            exit = LegacyBridgeCli.run(arguments, out, err);
        }
        return new CliResult(exit, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    private static <T> EngineOutcome<T> await(EngineJob<T> job) throws Exception {
        return job.completion().toCompletableFuture().get(180, TimeUnit.SECONDS);
    }

    private static <T> T completed(EngineJob<T> job) throws Exception {
        EngineOutcome<T> outcome = await(job);
        require(outcome.status() == EngineOutcome.Status.COMPLETED && outcome.error().isEmpty(),
                "Typed service failed: " + outcome.status() + " " + outcome.error());
        return outcome.value().orElseThrow();
    }

    private static void equivalent(Path directory, String operation, String service, String cli) throws IOException {
        Files.writeString(directory.resolve(operation + "-service.json"), service, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(directory.resolve(operation + "-cli.json"), cli, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        require(service.equals(cli), operation + " full canonical JSON differs between CLI and typed service");
    }

    private static byte[] boundedBytes(Path path) throws IOException {
        require(Files.size(path) <= MAX_CAPTURE, "Probe fixture/artifact is unexpectedly large");
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(MAX_CAPTURE + 1);
            require(bytes.length <= MAX_CAPTURE, "Probe fixture/artifact grew past limit");
            return bytes;
        }
    }

    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(boundedBytes(path)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record CliResult(int exitCode, String stdout, String stderr) { }

    private static final class BoundedCapture extends ByteArrayOutputStream {
        @Override public synchronized void write(int value) {
            require(count < MAX_CAPTURE, "Probe CLI capture byte limit exceeded");
            super.write(value);
        }
        @Override public synchronized void write(byte[] bytes, int offset, int length) {
            require(length <= MAX_CAPTURE - count, "Probe CLI capture byte limit exceeded");
            super.write(bytes, offset, length);
        }
    }

    /** Reuses the existing guarded isolated-process path; not included in the engine or product archive. */
    private static void composition(String selectedRoot) throws Exception {
        require(Runtime.version().feature() == 17, "Composition probe requires Java17");
        Path root = Path.of(selectedRoot).toAbsolutePath().normalize().toRealPath();
        require(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().equals(root), "Use isolated root");
        for (String property : List.of("user.home", "java.io.tmpdir")) requireEmptyDirectory(root, System.getProperty(property), property);
        requireEmptyDirectory(root, System.getenv("GRADLE_USER_HOME"), "GRADLE_USER_HOME");
        requireClasspathInsideRoot(root);
        var guard = new ProbeGuard(root, Path.of(System.getProperty("java.home")).toRealPath());
        System.setSecurityManager(guard); guard.selfTest();
        Path input = root.resolve("input/legacybridge-composed-logic-recipes-1.16.5-1.0.0.jar");
        String original = hash(input);
        var evidence = new LinkedHashMap<String, Object>();
        try (var engine = new LegacyEngineService()) {
            var reviewed = completed(engine.inspectMappingData(MappingData.bundle(root.resolve("bundle"))));
            var request = new ConversionRequest(input, root.resolve("never-published.jar"), reviewed.selection(), Optional.of(original), ConversionProfile.LOGIC_PRESERVING_V1);
            var job = engine.planLogicWithRecipes(request); var candidate = completed(job);
            require(candidate.validated() && !candidate.publicationAllowed(), "Private composition candidate must validate without publication authority");
            evidence.put("candidate", candidate.report()); evidence.put("metrics", job.metrics().toTree());
            evidence.put("dataReview", reviewed.toTree());
        }
        require(original.equals(hash(input)), "Original changed");
        require(!Files.exists(root.resolve("never-published.jar")) && !Files.exists(root.resolve("mods")), "No publication permitted");
        for (String property : List.of("user.home", "java.io.tmpdir")) requireEmptyDirectory(root, System.getProperty(property), property);
        requireEmptyDirectory(root, System.getenv("GRADLE_USER_HOME"), "GRADLE_USER_HOME");
        guard.assertNoUnexpectedAttempts();
        evidence.put("schemaVersion", 1); evidence.put("native", "NOT_RUN"); evidence.put("originalUnchanged", true);
        evidence.put("forbiddenFileNetworkProcessExitAttempts", 0);
        Files.writeString(root.resolve("composition-proof.json"), new CanonicalReportWriter().toJsonTree(evidence), StandardOpenOption.CREATE_NEW);
        System.out.println("CACHELESS_COMPOSITION_OK privateCandidate=true originalUnchanged=true native=NOT_RUN");
    }

    /** Reuses the existing guarded isolated-process path; not included in the engine or product archive. */
    private static void cohort(String selectedRoot) throws Exception {
        require(Runtime.version().feature() == 17, "Cohort probe requires Java17");
        Path root = Path.of(selectedRoot).toAbsolutePath().normalize().toRealPath();
        require(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().equals(root), "Use the isolated working directory");
        for (String property : List.of("user.home", "java.io.tmpdir")) requireEmptyDirectory(root, System.getProperty(property), property);
        requireEmptyDirectory(root, System.getenv("GRADLE_USER_HOME"), "GRADLE_USER_HOME");
        require(!Files.exists(root.resolve("bundle")), "No mapping bundle may be present");
        requireClasspathInsideRoot(root);
        var guard = new ProbeGuard(root, Path.of(System.getProperty("java.home")).toRealPath());
        System.setSecurityManager(guard); guard.selfTest();
        var selections = new ArrayList<dev.legacybridge.converter.service.CohortInspectionRequest.Artifact>();
        var args = new ArrayList<String>(); args.add("plan-cohort");
        for (String id : List.of("consumer", "library")) {
            Path input = root.resolve("input/legacybridge-cohort-" + id + "-1.16.5-1.0.0.jar");
            var artifact = new dev.legacybridge.converter.service.CohortInspectionRequest.Artifact(id, input, hash(input));
            selections.add(artifact); args.addAll(List.of("--artifact", id, artifact.expectedSha256(), input.toString()));
        }
        String json;
        try (var engine = new LegacyEngineService()) {
            var source = new dev.legacybridge.converter.service.CohortInspectionRequest(selections, MappingData.builtinCohortPreview());
            var result = completed(engine.planCohort(new dev.legacybridge.converter.service.CohortPlanningRequest(source)));
            require(result.validated() && result.candidates().size() == 2, "Whole finite pair must validate");
            json = new CanonicalReportWriter().toJsonTree(result.toTree());
            var cli = cli(args.toArray(String[]::new));
            require(cli.exitCode() == 0 && cli.stdout().equals(json), "Included-data CLI/service canonical JSON differs");
        }
        for (var selection : selections) require(selection.expectedSha256().equals(hash(selection.input())), "Original changed");
        try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) { require(files.findAny().isEmpty(), "Scratch data leaked"); }
        require(!Files.exists(root.resolve("mods")) && !Files.exists(root.resolve("staging")), "No installable artifacts permitted");
        Files.writeString(root.resolve("cohort-proof.json"), json, StandardOpenOption.CREATE_NEW);
        guard.assertNoUnexpectedAttempts();
        System.out.println("CACHELESS_COHORT_OK cliServiceEqual=true originalHashesPreserved=true forbiddenFileNetworkProcessExitAttempts=0 native=NOT_RUN");
    }

    private static final class ProbeGuard extends SecurityManager {
        private final Path root;
        private final Path javaHome;
        private final Set<Path> ancestors;
        private final AtomicInteger fileAttempts = new AtomicInteger();
        private final AtomicInteger networkAttempts = new AtomicInteger();
        private final AtomicInteger executionAttempts = new AtomicInteger();
        private final AtomicInteger exitAttempts = new AtomicInteger();
        private volatile boolean selfTesting = true;

        private ProbeGuard(Path root, Path javaHome) {
            this.root = root;
            this.javaHome = javaHome;
            List<Path> parents = new ArrayList<>();
            for (Path current = root.getParent(); current != null; current = current.getParent()) parents.add(current);
            ancestors = Set.copyOf(parents);
        }

        @Override public void checkRead(String file) { readable(file); }
        @Override public void checkRead(String file, Object context) { readable(file); }
        @Override public void checkWrite(String file) { writable(file); }
        @Override public void checkDelete(String file) { writable(file); }
        @Override public void checkExec(String command) {
            executionAttempts.incrementAndGet();
            throw new SecurityException("Test-only probe denies subprocess execution");
        }
        @Override public void checkConnect(String host, int port) { denyNetwork(); }
        @Override public void checkConnect(String host, int port, Object context) { denyNetwork(); }
        @Override public void checkListen(int port) { denyNetwork(); }
        @Override public void checkAccept(String host, int port) { denyNetwork(); }
        @Override public void checkMulticast(java.net.InetAddress address) { denyNetwork(); }
        @Override public void checkExit(int status) {
            exitAttempts.incrementAndGet();
            throw new SecurityException("Engine/CLI run must not call System.exit");
        }

        @Override public void checkPermission(Permission permission) {
            if (permission instanceof SocketPermission || permission instanceof URLPermission) denyNetwork();
            if (permission instanceof FilePermission file) {
                for (String action : file.getActions().split(",")) {
                    switch (action) {
                        case "read", "readlink" -> readable(file.getName());
                        case "write", "delete" -> writable(file.getName());
                        case "execute" -> checkExec(file.getName());
                        default -> throw new SecurityException("Unrecognized file permission in test-only probe");
                    }
                }
            }
            if (permission instanceof RuntimePermission && permission.getName().equals("setSecurityManager")) {
                throw new SecurityException("Test-only guard may not be disabled");
            }
        }

        @Override public void checkPermission(Permission permission, Object context) { checkPermission(permission); }

        private void readable(String file) {
            Path path = normalized(file);
            if (path.startsWith(root) || path.startsWith(javaHome)) return;
            // PortableMappingBundle verifies its ancestors. Allow metadata only,
            // never enumeration or file contents at these exact outside directories.
            if (ancestors.contains(path) && calledFromPathMetadata()) return;
            fileAttempts.incrementAndGet();
            if (!selfTesting) System.err.println("CACHELESS_PROBE_DENIED_READ " + path);
            throw new SecurityException("Test-only probe denies external file read: " + path);
        }

        private void writable(String file) {
            Path path = normalized(file);
            if (path.startsWith(root)) return;
            fileAttempts.incrementAndGet();
            throw new SecurityException("Test-only probe denies external filesystem mutation: " + path);
        }

        private Path normalized(String file) {
            if (file.equals("<<ALL FILES>>")) {
                fileAttempts.incrementAndGet();
                throw new SecurityException("Test-only probe denies global file permissions");
            }
            return Path.of(file).toAbsolutePath().normalize();
        }

        private static boolean calledFromPathMetadata() {
            for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                if (frame.getClassName().equals("java.nio.file.Files") && frame.getMethodName().equals("readAttributes")) return true;
                if (frame.getClassName().startsWith("sun.nio.fs.") && frame.getMethodName().equals("toRealPath")) return true;
            }
            return false;
        }

        private void denyNetwork() {
            networkAttempts.incrementAndGet();
            throw new SecurityException("Test-only probe denies networking and DNS");
        }

        private void selfTest() throws IOException {
            try {
                Files.readAllBytes(root.getParent().resolve("legacybridge-forbidden-read-tripwire"));
                throw new IllegalStateException("External file guard did not fire");
            } catch (SecurityException expected) { require(fileAttempts.get() == 1, "File tripwire count mismatch"); }
            try (Socket ignored = new Socket("127.0.0.1", 9)) {
                throw new IllegalStateException("Network guard did not fire");
            } catch (SecurityException expected) { require(networkAttempts.get() > 0, "Network tripwire did not fire"); }
            try {
                new ProcessBuilder("legacybridge-forbidden-process-tripwire").start();
                throw new IllegalStateException("Process guard did not fire");
            } catch (SecurityException expected) { require(executionAttempts.get() == 1, "Process tripwire count mismatch"); }
            fileAttempts.set(0);
            networkAttempts.set(0);
            executionAttempts.set(0);
            selfTesting = false;
        }

        private void assertNoUnexpectedAttempts() {
            require(fileAttempts.get() == 0 && networkAttempts.get() == 0 && executionAttempts.get() == 0 && exitAttempts.get() == 0,
                    "An engine operation attempted forbidden external access, networking, execution or process termination");
        }
    }
}
