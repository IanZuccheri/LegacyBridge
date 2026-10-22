package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static dev.legacybridge.corpus.CompositionRunFiles.*;

/**
 * Read-only admission of an existing, unarmed developer stage. Reconstructs current reviewed
 * selectors; neither a receipt nor the returned snapshot authorizes a process or installation.
 * Uses bounded hashes on a trusted quiescent filesystem, not locks, attestations or a hostile-writer
 * sandbox. Unknown hard links outside the selected set and equivalent-byte replacement are not proven away.
 */
public final class CompositionStagedRunVerifier {
    private static final String ARM = "composition-probe-authorized.txt";
    private static final String RECEIPT = "stage-receipt.json";
    private static final List<String> CREATE = List.of("mods", "composition-observations", "empty-user-home", "empty-user-home/absent-gradle", "tmp");
    private CompositionStagedRunVerifier() { }

    /** Immutable read-only binding, not a launch capability. Only this verifier can construct it. */
    public static final class Snapshot {
        private final Path repository, runRoot;
        private final CompositionRunPlanner.Plan plan;
        private final CompositionRunPlanner.Request request;
        private final String receiptSha256;
        private final List<CompositionProcessCollector.Input> inputs;
        private Snapshot(Path repository, Path runRoot, CompositionRunPlanner.Plan plan,
                         CompositionRunPlanner.Request request, String receiptSha256,
                         List<CompositionProcessCollector.Input> inputs) {
            this.repository = repository; this.runRoot = runRoot; this.plan = plan;
            this.request = request; this.receiptSha256 = receiptSha256; this.inputs = List.copyOf(inputs);
        }
        public Path repository() { return repository; }
        public Path runRoot() { return runRoot; }
        public CompositionRunPlanner.Plan plan() { return plan; }
        public String receiptSha256() { return receiptSha256; }
        public Snapshot recheck(BooleanSupplier cancelled) throws IOException {
            return request == null ? inspect(repository, plan, receiptSha256, cancelled)
                    : verify(repository, request, plan.sha256(), receiptSha256, cancelled);
        }
        List<CompositionProcessCollector.Input> immutableInputs() { return inputs; }
        public Map<String, Object> report() { return toTree(); }
        public Map<String, Object> toTree() {
            return Map.ofEntries(Map.entry("schemaVersion", 1), Map.entry("reportType", "LEGACY_COMPOSITION_STAGED_VERIFICATION"),
                    Map.entry("status", "STAGED_INPUTS_VERIFIED_ONLY"), Map.entry("role", plan.role().name()),
                    Map.entry("runId", plan.runId()), Map.entry("planSha256", plan.sha256()), Map.entry("receiptSha256", receiptSha256),
                    Map.entry("immutableInputCount", inputs.size()), Map.entry("runtimeValidation", "NOT_RUN"),
                    Map.entry("executionAllowed", false), Map.entry("gameExecutionVerified", false),
                    Map.entry("installationAllowed", false), Map.entry("launchContractVerified", false));
        }
    }

    public static Snapshot verify(Path repository, CompositionRunPlanner.Request request, String planSha256,
                                  String receiptSha256, BooleanSupplier cancelled) throws IOException {
        try {
            check(cancelled); sha(planSha256); sha(receiptSha256);
            var plan = CompositionRunPlanner.reconstructMaterialized(repository, request);
            ensure(plan.sha256().equals(planSha256), "REVIEWED_PLAN"); check(cancelled);
            return inspectInternal(repository, plan, request, receiptSha256, cancelled);
        } catch (IOException | RuntimeException invalid) { throw sanitized(invalid); }
    }

    // Inert tests only: a caller-supplied Plan never bypasses public reviewed selection.
    static Snapshot inspect(Path repository, CompositionRunPlanner.Plan trustedPlan, String receiptSha256,
                            BooleanSupplier cancelled) throws IOException {
        try { return inspectInternal(repository, trustedPlan, null, receiptSha256, cancelled); }
        catch (IOException | RuntimeException invalid) { throw sanitized(invalid); }
    }

    private static Snapshot inspectInternal(Path root, CompositionRunPlanner.Plan plan, CompositionRunPlanner.Request request,
                                            String receiptSha256, BooleanSupplier cancelled) throws IOException {
        check(cancelled); directory(root); sha(receiptSha256);
        ensure(plan != null && plan.role() != null && plan.runId() != null
                && plan.runId().matches("run-[a-z0-9][a-z0-9-]{0,40}"), "PLAN");
        sha(plan.sha256());
        Map<String, Object> tree = object(plan.report().get("plan"));
        keys(tree, "schemaVersion", "reportType", "status", "role", "runId", "workingDirectory", "runtimeValidation",
                "executionAllowed", "installationAllowed", "java", "arguments", "copyFiles", "generatedFiles",
                "createDirectories", "removeEnvironment", "setEnvironment", "limits", "inputs", "pending");
        ensure(CorpusJson.canonicalJson(tree).equals(plan.canonicalJson()), "PLAN_CANONICAL");
        ensure(integer(tree.get("schemaVersion")) == 1 && "LEGACY_COMPOSITION_RUN_PLAN".equals(tree.get("reportType"))
                && "PLANNED_ONLY".equals(tree.get("status")) && "NOT_RUN".equals(tree.get("runtimeValidation"))
                && Boolean.FALSE.equals(tree.get("executionAllowed")) && Boolean.FALSE.equals(tree.get("installationAllowed")), "PLAN_STATE");
        String role = plan.role().name().toLowerCase(Locale.ROOT), runRelative = "build/composition-probe-runs/" + plan.runId();
        Path run = root.resolve(runRelative), cwd = run.resolve(role); directory(run); directory(cwd);
        ensure(plan.workingDirectory().equals(cwd) && (runRelative + "/" + role).equals(tree.get("workingDirectory"))
                && plan.role().name().equals(tree.get("role")) && plan.runId().equals(tree.get("runId")), "PLAN_BINDING");
        equal(tree.get("arguments"), plan.arguments(), "ARGUMENT_BINDING");
        equal(tree.get("copyFiles"), plan.copies().stream().map(CompositionRunPlanner.Copy::tree).toList(), "COPY_BINDING");
        ensure(CREATE.equals(tree.get("createDirectories")), "DIRECTORIES");
        ensure(!Files.exists(cwd.resolve(ARM), LinkOption.NOFOLLOW_LINKS), "ARMED_STAGE");

        ReadSet selected = new ReadSet(cancelled);
        List<?> originals = list(tree.get("inputs"), 512); ensure(!originals.isEmpty(), "INPUTS");
        Map<String, Pin> originalPins = new LinkedHashMap<>(); Set<String> originalNames = new LinkedHashSet<>();
        for (Object raw : originals) {
            Map<String, Object> pin = object(raw); keys(pin, "path", "sha256", "bytes");
            String name = string(pin.get("path")); relative(name);
            ensure(originalNames.add(name.toLowerCase(Locale.ROOT)), "INPUT_DUPLICATE");
            Pin value = new Pin(name, string(pin.get("sha256")), integer(pin.get("bytes")));
            selected.select(root.resolve(name), value.sha256(), value.bytes()); originalPins.put(name, value);
        }
        Map<String, Object> java = object(tree.get("java"));
        keys(java, "executable", "sha256", "releaseSha256", "declaredVersion", "executed");
        Path executable = Path.of(string(java.get("executable")));
        ensure(executable.getParent() != null && executable.getParent().getParent() != null && !plan.arguments().isEmpty()
                && plan.arguments().get(0).equals(executable.toString()) && Boolean.FALSE.equals(java.get("executed")), "JAVA_BINDING");
        Path release = executable.getParent().getParent().resolve("release");
        selected.select(executable, string(java.get("sha256")), Files.size(executable));
        selected.select(release, string(java.get("releaseSha256")), Files.size(release));
        ensure(Files.size(release) <= 8192, "JAVA_RELEASE_SIZE");

        Path receiptPath = run.resolve(RECEIPT);
        selected.select(receiptPath, receiptSha256, Files.size(receiptPath));
        Map<String, Object> receipt = selected.json(receiptPath);
        keys(receipt, "schemaVersion", "reportType", "status", "operationId", "runId", "role", "planSha256",
                "files", "directories", "withheldFiles", "executionAllowed", "gameExecutionVerified", "installationAllowed");
        String operation = string(receipt.get("operationId"));
        ensure(operation.matches("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
                && UUID.fromString(operation).toString().equals(operation), "OPERATION_ID");

        Expected expected = new Expected(run);
        expected.json("stage-intent.json", Map.of("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_STAGE_INTENT",
                "status", "PREPARING_UNARMED", "operationId", operation, "runId", plan.runId(), "role", plan.role().name(),
                "planSha256", plan.sha256(), "executionAllowed", false, "installationAllowed", false));
        expected.text("stage-plan.json", plan.canonicalJson());
        expected.directory("journal"); expected.directory(role);
        for (String name : CREATE) expected.directory(role + "/" + name);
        ensure(!plan.copies().isEmpty() && plan.copies().size() <= 261, "COPY_BUDGET");
        int sequence = 0; long copied = 0;
        for (var copy : plan.copies()) {
            check(cancelled); relative(copy.source()); relative(copy.destination());
            Pin source = originalPins.get(copy.source());
            ensure(source != null && source.sha256().equals(copy.sha256()) && source.bytes() == copy.bytes(), "UNDECLARED_COPY");
            ensure(copy.bytes() > 0 && copy.bytes() <= (128L << 20) && (copied += copy.bytes()) <= (512L << 20), "COPY_SIZE");
            ensure(copy.destination().endsWith(".jar") || copy.destination().equals("eula.txt") || copy.destination().equals("composition-receipt.json")
                    || copy.destination().equals("libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt"), "COPY_DESTINATION");
            expected.pin(role + "/" + copy.destination(), copy.sha256(), copy.bytes());
            expected.json(String.format(Locale.ROOT, "journal/%04d.json", sequence++),
                    Map.of("schemaVersion", 1, "operationId", operation, "planSha256", plan.sha256(), "event", "COPIED",
                            "destination", role + "/" + copy.destination(), "sha256", copy.sha256(), "bytes", copy.bytes()));
        }
        Map<String, Object> generated = object(tree.get("generatedFiles")); keys(generated, ARM, "server.properties");
        ensure((plan.runId() + "\n").equals(generated.get(ARM)), "AUTHORIZATION_DIRECTIVE");
        String properties = string(generated.get("server.properties")); ensure(properties.getBytes(StandardCharsets.UTF_8).length <= 8192, "PROPERTIES_SIZE");
        expected.text(role + "/server.properties", properties);
        expected.json(String.format(Locale.ROOT, "journal/%04d.json", sequence), Map.of("schemaVersion", 1,
                "operationId", operation, "planSha256", plan.sha256(), "event", "GENERATED_UNARMED", "withheldFile", role + "/" + ARM));
        Map<String, Object> expectedReceipt = new LinkedHashMap<>();
        expectedReceipt.put("schemaVersion", 1); expectedReceipt.put("reportType", "LEGACY_COMPOSITION_STAGE_RECEIPT");
        expectedReceipt.put("status", "STAGED_UNARMED"); expectedReceipt.put("operationId", operation);
        expectedReceipt.put("runId", plan.runId()); expectedReceipt.put("role", plan.role().name()); expectedReceipt.put("planSha256", plan.sha256());
        expectedReceipt.put("files", expected.files.values().stream().map(Pin::tree).toList());
        expectedReceipt.put("directories", List.copyOf(expected.directories)); expectedReceipt.put("withheldFiles", List.of(role + "/" + ARM));
        expectedReceipt.put("executionAllowed", false); expectedReceipt.put("gameExecutionVerified", false); expectedReceipt.put("installationAllowed", false);
        equal(receipt, expectedReceipt, "RECEIPT_CONTENT");
        expected.pin(RECEIPT, receiptSha256, Files.size(receiptPath));
        expected.audit(cancelled);
        for (Pin pin : expected.files.values()) selected.select(run.resolve(pin.path()), pin.sha256(), pin.bytes());
        selected.recheck(); expected.audit(cancelled);
        List<CompositionProcessCollector.Input> immutable = new ArrayList<>(); long total = 0;
        for (var entry : selected.files.entrySet()) {
            if (entry.getKey().equals(cwd.resolve("server.properties"))) continue;
            var pin = entry.getValue(); total += pin.bytes();
            immutable.add(new CompositionProcessCollector.Input(entry.getKey(), pin.sha256(), pin.bytes()));
        }
        ensure(immutable.size() <= 512 && total <= (512L << 20), "COLLECTOR_INPUT_BUDGET");
        check(cancelled);
        return new Snapshot(root, run, plan, request, receiptSha256, immutable);
    }

    private static final class Expected {
        final Path root;
        final Map<String, Pin> files = new LinkedHashMap<>();
        final Set<String> directories = new LinkedHashSet<>(Set.of(""));
        final Set<String> names = new LinkedHashSet<>();
        Expected(Path root) { this.root = root; }
        void directory(String name) throws IOException {
            relative(name); String parent = "";
            for (String part : name.split("/")) {
                parent = parent.isEmpty() ? part : parent + "/" + part;
                ensure(!files.containsKey(parent), "PATH_COLLISION"); directories.add(parent);
            }
        }
        void pin(String name, String hash, long bytes) throws IOException {
            relative(name); sha(hash); ensure(bytes > 0 && bytes <= (128L << 20), "FILE_SIZE");
            int slash = name.lastIndexOf('/'); if (slash >= 0) directory(name.substring(0, slash));
            ensure(names.add(name.toLowerCase(Locale.ROOT)) && !directories.contains(name), "PATH_COLLISION");
            files.put(name, new Pin(name, hash, bytes));
        }
        void text(String name, String text) throws IOException { byte[] bytes = text.getBytes(StandardCharsets.UTF_8); pin(name, digest(bytes), bytes.length); }
        void json(String name, Map<String, Object> tree) throws IOException { text(name, CorpusJson.canonicalJson(tree)); }
        void audit(BooleanSupplier cancelled) throws IOException {
            CompositionRunFiles.directory(root); Set<String> actualFiles = new LinkedHashSet<>(), actualDirs = new LinkedHashSet<>();
            try (var walk = Files.walk(root, 32)) {
                var it = walk.iterator(); int count = 0;
                while (it.hasNext()) {
                    check(cancelled); Path path = it.next(); ensure(++count <= 2048, "TREE_SIZE"); canonical(path);
                    String name = root.relativize(path).toString().replace('\\', '/');
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) { ensure(directories.contains(name), "EXTRA_DIRECTORY"); actualDirs.add(name); }
                    else { ensure(files.containsKey(name), "EXTRA_FILE"); actualFiles.add(name); }
                }
            }
            ensure(actualFiles.equals(files.keySet()) && actualDirs.equals(directories), "TREE_MISSING");
        }
    }

    private static final class ReadSet {
        final BooleanSupplier cancelled;
        final Map<Path, Pin> files = new LinkedHashMap<>();
        long total;
        ReadSet(BooleanSupplier cancelled) { this.cancelled = cancelled; }
        void select(Path path, String sha256, long bytes) throws IOException {
            check(cancelled); sha(sha256); ensure(bytes > 0 && bytes <= (128L << 20), "FILE_SIZE");
            Pin prior = files.get(path);
            if (prior != null) { ensure(prior.sha256().equals(sha256) && prior.bytes() == bytes, "PIN_COLLISION"); return; }
            ensure(files.size() < 1536 && (total += bytes) <= (1040L << 20), "READ_BUDGET");
            ensure(hash(path).equals(sha256) && Files.size(path) == bytes, "INPUT_DRIFT"); check(cancelled);
            for (var entry : files.entrySet()) if (entry.getValue().sha256().equals(sha256))
                ensure(!Files.isSameFile(path, entry.getKey()), "FILE_ALIAS");
            files.put(path, new Pin(path.toString(), sha256, bytes));
        }
        Map<String, Object> json(Path path) throws IOException {
            Pin pin = files.get(path); ensure(pin != null && pin.bytes() <= CorpusJson.MAX_BYTES, "JSON_SIZE");
            Map<String, Object> json = CorpusJson.read(path);
            ensure(digest(CorpusJson.canonicalJson(json).getBytes(StandardCharsets.UTF_8)).equals(pin.sha256()), "JSON_CANONICAL");
            ensure(hash(path).equals(pin.sha256()), "INPUT_DRIFT"); return json;
        }
        void recheck() throws IOException {
            for (var entry : files.entrySet()) {
                check(cancelled); ensure(hash(entry.getKey()).equals(entry.getValue().sha256()) && Files.size(entry.getKey()) == entry.getValue().bytes(), "INPUT_DRIFT");
            }
            List<Path> paths = List.copyOf(files.keySet());
            for (int i = 0; i < paths.size(); i++) {
                check(cancelled);
                for (int j = 0; j < i; j++) if (files.get(paths.get(i)).sha256().equals(files.get(paths.get(j)).sha256()))
                    ensure(!Files.isSameFile(paths.get(i), paths.get(j)), "FILE_ALIAS");
            }
        }
    }
    private static void equal(Object actual, Object expected, String code) throws IOException {
        ensure(CorpusJson.canonicalJson(Map.of("value", actual)).equals(CorpusJson.canonicalJson(Map.of("value", expected))), code);
    }
    private static List<?> list(Object value, int bound) throws IOException { ensure(value instanceof List<?> && ((List<?>) value).size() <= bound, "LIST"); return (List<?>) value; }
    private static void sha(String value) throws IOException { ensure(value != null && value.matches("[a-f0-9]{64}"), "SHA256"); }
    private static void check(BooleanSupplier cancelled) throws IOException {
        ensure(cancelled != null, "CANCELLATION");
        boolean stop;
        try { stop = cancelled.getAsBoolean(); } catch (RuntimeException invalid) { throw new IOException("STAGED_VERIFY_CANCELLATION_CALLBACK"); }
        ensure(!stop && !Thread.currentThread().isInterrupted(), "CANCELLED");
    }
    private static void ensure(boolean condition, String code) throws IOException { if (!condition) throw new IOException("STAGED_VERIFY_" + code); }
    private static IOException sanitized(Exception failure) {
        String code = failure.getMessage();
        return new IOException(code != null && code.matches("STAGED_VERIFY_[A-Z0-9_]+") ? code : "STAGED_VERIFY_REJECTED");
    }

    /** Six explicit developer inputs; inspection only, never stages, arms or executes the saved arguments. */
    public static void main(String[] args) throws IOException {
        try {
            ensure(args != null && args.length == 6, "USAGE_REPOSITORY_ROLE_RUNID_JAVAHOME_PLAN_RECEIPT");
            var request = new CompositionRunPlanner.Request(CompositionRunPlanner.Role.valueOf(args[1]), args[2], Path.of(args[3]));
            Snapshot result = verify(Path.of(args[0]), request, args[4], args[5], () -> false);
            System.out.print(CorpusJson.canonicalJson(result.toTree()));
        } catch (IOException | RuntimeException invalid) {
            IOException failure = sanitized(invalid);
            System.out.print(CorpusJson.canonicalJson(Map.of("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_STAGED_VERIFICATION",
                    "status", "BLOCKED", "error", failure.getMessage(), "executionAllowed", false,
                    "gameExecutionVerified", false, "installationAllowed", false, "launchContractVerified", false)));
            throw failure;
        }
    }
}
