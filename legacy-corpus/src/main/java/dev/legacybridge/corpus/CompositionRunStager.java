package dev.legacybridge.corpus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static dev.legacybridge.corpus.CompositionRunFiles.*;

/**
 * Developer-only materialization, never an installer or launcher. Public calls replan from
 * reviewed local inputs and require the caller's exact plan digest before any write.
 * No authorization marker is materialized. Failures retain private, untrusted partial trees;
 * neither unknown files nor completed copies are deleted, replaced or resumed.
 * Use a quiescent trusted local filesystem: canonical rechecks are not a hostile-writer sandbox,
 * and forcing file contents is not directory fsync or a multi-file atomic transaction.
 */
public final class CompositionRunStager {
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    private static final String ARM = "composition-probe-authorized.txt";
    private static final String RECEIPT = "stage-receipt.json", PENDING = "stage-receipt.pending";
    private static final Hook NO_HOOK = (point, path) -> { };
    public enum State { STAGED_UNARMED, BLOCKED, FAILED, CANCELLED, BUSY }
    public record Outcome(State status, String runDirectory, String planSha256,
                          String receiptSha256, String error, boolean remainderRetained) {
        public Map<String, Object> toTree() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("schemaVersion", 1); result.put("reportType", "LEGACY_COMPOSITION_STAGE_RESULT");
            result.put("status", status.name()); result.put("runDirectory", runDirectory);
            result.put("planSha256", planSha256); result.put("receiptSha256", receiptSha256);
            result.put("error", error); result.put("remainderRetained", remainderRetained);
            result.put("executionAllowed", false); result.put("gameExecutionVerified", false);
            result.put("installationAllowed", false);
            return java.util.Collections.unmodifiableMap(result);
        }
    }
    @FunctionalInterface interface Hook { void checkpoint(String point, Path runRoot) throws IOException; }
    private static final class Cancelled extends IOException { }
    private record Admission(Map<String, Object> tree, CompositionRunFiles inputs, Path javaExecutable,
                             String javaSha256, Path javaRelease, String releaseSha256, String runRelative) { }
    private CompositionRunStager() { }

    public static Outcome stage(Path repository, CompositionRunPlanner.Request request,
                                String reviewedPlanSha256, BooleanSupplier cancelled) {
        if (!ACTIVE.compareAndSet(false, true)) return failure(State.BUSY, null, null, "STAGE_BUSY", false);
        try {
            check(cancelled);
            // Plan records are publicly constructible; only this rechecked public path selects native inputs.
            var plan = CompositionRunPlanner.plan(repository, request);
            return perform(repository, plan, reviewedPlanSha256, cancelled, NO_HOOK);
        } catch (Cancelled stop) { return failure(State.CANCELLED, null, null, "STAGE_CANCELLED", false); }
        catch (IOException | RuntimeException invalid) { return failure(State.BLOCKED, null, null, code(invalid), false); }
        finally { ACTIVE.set(false); }
    }

    // Inert test seam only. No public caller may provide a fabricated Plan to the materializer.
    static Outcome stagePlan(Path repository, CompositionRunPlanner.Plan plan, String expectedSha,
                             BooleanSupplier cancelled, Hook hook) {
        if (!ACTIVE.compareAndSet(false, true)) return failure(State.BUSY, null, null, "STAGE_BUSY", false);
        try { return perform(repository, plan, expectedSha, cancelled, hook); }
        finally { ACTIVE.set(false); }
    }

    private static Outcome perform(Path repository, CompositionRunPlanner.Plan plan, String expectedSha,
                                   BooleanSupplier cancelled, Hook hook) {
        boolean acquired = false;
        boolean[] createdParents = {false};
        String run = null, sha = null;
        try {
            check(cancelled); ensure(hook != null, "HOOK");
            Admission admission = admit(repository, plan, expectedSha);
            run = admission.runRelative; sha = plan.sha256();
            check(cancelled);
            Path runRoot = repository.resolve(run);
            ensureParents(runRoot.getParent(), createdParents);
            check(cancelled);
            // Atomic acquisition of this name only, not of the whole tree. Existing IDs are never reused.
            directory(runRoot.getParent()); Files.createDirectory(runRoot); acquired = true;
            OwnedTree tree = new OwnedTree(runRoot, cancelled);
            String operationId = UUID.randomUUID().toString();
            tree.writeJson("stage-intent.json", Map.of("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_STAGE_INTENT",
                    "status", "PREPARING_UNARMED", "operationId", operationId, "runId", plan.runId(),
                    "role", plan.role().name(), "planSha256", sha, "executionAllowed", false, "installationAllowed", false));
            tree.writeText("stage-plan.json", plan.canonicalJson());
            tree.mkdir("journal");
            hook.checkpoint("AFTER_INTENT", runRoot); check(cancelled); tree.audit(admission);
            String role = plan.role().name().toLowerCase(Locale.ROOT);
            tree.mkdir(role);
            for (Object raw : list(admission.tree.get("createDirectories"), 16)) tree.mkdir(role + "/" + string(raw));
            int sequence = 0;
            for (CompositionRunPlanner.Copy copy : plan.copies()) {
                check(cancelled);
                Pin source = admission.inputs.select(copy.source(), copy.sha256(), copy.bytes());
                try (InputStream input = Files.newInputStream(repository.resolve(copy.source()), LinkOption.NOFOLLOW_LINKS)) {
                    tree.write(role + "/" + copy.destination(), input, copy.bytes(), copy.sha256());
                }
                admission.inputs.verify(source);
                tree.writeJson(String.format(Locale.ROOT, "journal/%04d.json", sequence++),
                        Map.of("schemaVersion", 1, "operationId", operationId, "planSha256", sha,
                                "event", "COPIED", "destination", role + "/" + copy.destination(),
                                "sha256", copy.sha256(), "bytes", copy.bytes()));
                hook.checkpoint("AFTER_COPY", runRoot);
                // Recheck metadata anchors between steps; full closed-tree/hash validation gates the receipt.
                tree.verify("stage-intent.json"); tree.verify("stage-plan.json");
            }
            Map<String, Object> generated = object(admission.tree.get("generatedFiles"));
            // Arming is a distinct, currently unauthorized operation; do not silently write this directive.
            tree.writeText(role + "/server.properties", string(generated.get("server.properties")));
            tree.writeJson(String.format(Locale.ROOT, "journal/%04d.json", sequence),
                    Map.of("schemaVersion", 1, "operationId", operationId, "planSha256", sha,
                            "event", "GENERATED_UNARMED", "withheldFile", role + "/" + ARM));
            hook.checkpoint("BEFORE_RECEIPT", runRoot); check(cancelled);
            revalidate(admission); tree.audit(admission);
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("schemaVersion", 1); receipt.put("reportType", "LEGACY_COMPOSITION_STAGE_RECEIPT");
            receipt.put("status", "STAGED_UNARMED"); receipt.put("operationId", operationId);
            receipt.put("runId", plan.runId()); receipt.put("role", plan.role().name()); receipt.put("planSha256", sha);
            receipt.put("files", tree.files.values().stream().map(Pin::tree).toList());
            receipt.put("directories", List.copyOf(tree.directories));
            receipt.put("withheldFiles", List.of(role + "/" + ARM));
            receipt.put("executionAllowed", false); receipt.put("gameExecutionVerified", false); receipt.put("installationAllowed", false);
            tree.writeJson(PENDING, receipt);
            revalidate(admission); tree.audit(admission); check(cancelled);
            // Never use ATOMIC_MOVE: the Windows provider can replace an existing destination with it.
            directory(runRoot); ensure(!Files.exists(runRoot.resolve(RECEIPT), LinkOption.NOFOLLOW_LINKS), "RECEIPT_EXISTS");
            Pin pending = tree.files.get(PENDING); tree.verify(PENDING);
            Files.move(runRoot.resolve(PENDING), runRoot.resolve(RECEIPT));
            tree.files.remove(PENDING); tree.files.put(RECEIPT, new Pin(RECEIPT, pending.sha256(), pending.bytes()));
            // After publication begins, late cancellation cannot relabel completed staging as CANCELLED.
            tree.publicationStarted = true;
            hook.checkpoint("AFTER_RECEIPT_MOVE", runRoot);
            tree.audit(admission); revalidate(admission);
            return new Outcome(State.STAGED_UNARMED, run, sha, pending.sha256(), null, false);
        } catch (Cancelled stop) { return failure(State.CANCELLED, run, sha, "STAGE_CANCELLED", acquired || createdParents[0]); }
        catch (IOException | RuntimeException invalid) {
            boolean touched = acquired || createdParents[0];
            return failure(touched ? State.FAILED : State.BLOCKED, run, sha, code(invalid), touched);
        }
    }

    private static Admission admit(Path root, CompositionRunPlanner.Plan plan, String expectedSha) throws IOException {
        directory(root); ensure(plan != null && plan.role() != null && plan.runId() != null, "PLAN");
        ensure(plan.runId().matches("run-[a-z0-9][a-z0-9-]{0,40}"), "RUN_ID");
        ensure(expectedSha != null && expectedSha.matches("[a-f0-9]{64}") && expectedSha.equals(plan.sha256()), "REVIEWED_PLAN");
        ensure(plan.canonicalJson() != null && plan.canonicalJson().getBytes(StandardCharsets.UTF_8).length <= CorpusJson.MAX_BYTES, "PLAN_SIZE");
        Map<String, Object> tree = object(plan.report().get("plan"));
        keys(tree, "schemaVersion", "reportType", "status", "role", "runId", "workingDirectory",
                "runtimeValidation", "executionAllowed", "installationAllowed", "java", "arguments", "copyFiles",
                "generatedFiles", "createDirectories", "removeEnvironment", "setEnvironment", "limits", "inputs", "pending");
        ensure(CorpusJson.canonicalJson(tree).equals(plan.canonicalJson()), "CANONICAL_PLAN");
        ensure(integer(tree.get("schemaVersion")) == 1 && "LEGACY_COMPOSITION_RUN_PLAN".equals(tree.get("reportType"))
                && "PLANNED_ONLY".equals(tree.get("status")) && "NOT_RUN".equals(tree.get("runtimeValidation"))
                && Boolean.FALSE.equals(tree.get("executionAllowed"))
                && Boolean.FALSE.equals(tree.get("installationAllowed")), "PLAN_STATE");
        String run = "build/composition-probe-runs/" + plan.runId();
        String cwd = run + "/" + plan.role().name().toLowerCase(Locale.ROOT);
        ensure(cwd.equals(tree.get("workingDirectory")) && root.resolve(cwd).equals(plan.workingDirectory())
                && plan.role().name().equals(tree.get("role")) && plan.runId().equals(tree.get("runId")), "PLAN_BINDING");
        ensure(plan.arguments().equals(tree.get("arguments")), "ARGUMENT_BINDING");
        ensure(CorpusJson.canonicalJson(Map.of("files", plan.copies().stream().map(CompositionRunPlanner.Copy::tree).toList()))
                .equals(CorpusJson.canonicalJson(Map.of("files", tree.get("copyFiles")))), "COPY_BINDING");
        ensure(!plan.copies().isEmpty() && plan.copies().size() <= 261, "COPY_BUDGET");
        Set<String> destinations = new LinkedHashSet<>(); long total = 0;
        for (var copy : plan.copies()) {
            relative(copy.source()); relative(copy.destination());
            ensure(copy.bytes() > 0 && copy.bytes() <= (128L << 20) && (total += copy.bytes()) <= (512L << 20), "COPY_SIZE");
            ensure(!copy.destination().equals(ARM) && (copy.destination().endsWith(".jar") || copy.destination().equals("eula.txt")
                    || copy.destination().equals("composition-receipt.json")
                    || copy.destination().equals("libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt")), "COPY_DESTINATION");
            ensure(destinations.add(copy.destination().toLowerCase(Locale.ROOT)), "COPY_COLLISION");
        }
        Map<String, Object> generated = object(tree.get("generatedFiles")); keys(generated, ARM, "server.properties");
        ensure((plan.runId() + "\n").equals(generated.get(ARM)), "AUTHORIZATION_DIRECTIVE");
        ensure(string(generated.get("server.properties")).getBytes(StandardCharsets.UTF_8).length <= 8192, "PROPERTIES_SIZE");
        ensure(List.of("mods", "composition-observations", "empty-user-home", "empty-user-home/absent-gradle", "tmp")
                .equals(tree.get("createDirectories")), "DIRECTORIES");
        CompositionRunFiles inputs = new CompositionRunFiles(root);
        List<?> inputList = list(tree.get("inputs"), 512); ensure(!inputList.isEmpty(), "INPUTS");
        Set<String> names = new LinkedHashSet<>();
        for (Object raw : inputList) {
            Map<String, Object> pin = object(raw); keys(pin, "path", "sha256", "bytes");
            String name = string(pin.get("path")); ensure(names.add(name.toLowerCase(Locale.ROOT)), "INPUT_DUPLICATE");
            inputs.select(name, string(pin.get("sha256")), integer(pin.get("bytes")));
        }
        for (var copy : plan.copies()) {
            ensure(names.contains(copy.source().toLowerCase(Locale.ROOT)), "UNDECLARED_INPUT");
            inputs.select(copy.source(), copy.sha256(), copy.bytes());
        }
        Map<String, Object> java = object(tree.get("java"));
        Path executable;
        try { executable = Path.of(string(java.get("executable"))); }
        catch (java.nio.file.InvalidPathException bad) { throw new IOException("STAGE_JAVA_PATH"); }
        ensure(executable.getParent() != null && executable.getParent().getParent() != null, "JAVA_PATH");
        Path release = executable.getParent().getParent().resolve("release");
        ensure(!plan.arguments().isEmpty() && plan.arguments().get(0).equals(executable.toString()), "JAVA_BINDING");
        Admission admission = new Admission(tree, inputs, executable, string(java.get("sha256")), release,
                string(java.get("releaseSha256")), run);
        revalidate(admission); absentTree(root.resolve(run)); return admission;
    }

    private static void revalidate(Admission value) throws IOException {
        value.inputs.verifyAll();
        List<Pin> pins = value.inputs.pins();
        for (int i = 0; i < pins.size(); i++) for (int j = 0; j < i; j++)
            if (pins.get(i).sha256().equals(pins.get(j).sha256()))
                ensure(!Files.isSameFile(value.inputs.resolve(pins.get(i).path()),
                        value.inputs.resolve(pins.get(j).path())), "INPUT_ALIAS");
        ensure(hash(value.javaExecutable).equals(value.javaSha256) && hash(value.javaRelease).equals(value.releaseSha256), "JAVA_DRIFT");
    }
    private static void ensureParents(Path directory, boolean[] created) throws IOException {
        List<Path> missing = new ArrayList<>(); Path cursor = directory;
        while (cursor != null && !Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) { missing.add(cursor); cursor = cursor.getParent(); }
        directory(cursor);
        for (int i = missing.size() - 1; i >= 0; i--) {
            Path next = missing.get(i); directory(next.getParent()); Files.createDirectory(next); created[0] = true; directory(next);
        }
    }

    private static final class OwnedTree {
        final Path root;
        final Map<String, Pin> files = new LinkedHashMap<>();
        final Set<String> directories = new LinkedHashSet<>(Set.of(""));
        BooleanSupplier cancelled;
        boolean publicationStarted;
        OwnedTree(Path root, BooleanSupplier cancelled) throws IOException { this.root = root; this.cancelled = cancelled; directory(root); }
        void mkdir(String name) throws IOException {
            relative(name); check(cancelled);
            String parent = "";
            for (String part : name.split("/")) {
                String next = parent.isEmpty() ? part : parent + "/" + part;
                directory(root.resolve(parent));
                if (!directories.contains(next)) { Files.createDirectory(root.resolve(next)); directories.add(next); }
                directory(root.resolve(next)); parent = next;
            }
        }
        void writeJson(String name, Map<String, Object> json) throws IOException { writeText(name, CorpusJson.canonicalJson(json)); }
        void writeText(String name, String contents) throws IOException {
            byte[] bytes = contents.getBytes(StandardCharsets.UTF_8); ensure(bytes.length <= CorpusJson.MAX_BYTES, "METADATA_SIZE");
            write(name, new ByteArrayInputStream(bytes), bytes.length, digest(bytes));
        }
        void write(String name, InputStream input, long size, String sha) throws IOException {
            relative(name); check(cancelled); ensure(size >= 0 && size <= (128L << 20), "FILE_SIZE");
            int slash = name.lastIndexOf('/'); String parent = slash < 0 ? "" : name.substring(0, slash);
            if (!parent.isEmpty()) mkdir(parent); directory(root.resolve(parent));
            ensure(files.size() < 1024 && !files.containsKey(name), "FILE_COUNT");
            long copied = 0;
            try (FileChannel output = FileChannel.open(root.resolve(name), StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[65536]; int read;
                while ((read = input.read(buffer)) != -1) {
                    check(cancelled); ensure((copied += read) <= size, "SOURCE_GROWTH");
                    ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, read);
                    while (bytes.hasRemaining()) { check(cancelled); output.write(bytes); }
                }
                ensure(copied == size, "SOURCE_TRUNCATED"); output.force(true);
            }
            Pin pin = new Pin(name, sha, size); verify(pin); files.put(name, pin);
        }
        void verify(String name) throws IOException { verify(files.get(name)); }
        void verify(Pin pin) throws IOException {
            ensure(pin != null && Files.size(root.resolve(pin.path())) == pin.bytes()
                    && hash(root.resolve(pin.path())).equals(pin.sha256()), "TARGET_DRIFT");
        }
        void audit(Admission admission) throws IOException {
            directory(root); Set<String> seenFiles = new LinkedHashSet<>(), seenDirs = new LinkedHashSet<>();
            try (var walk = Files.walk(root, 32)) {
                var iterator = walk.iterator(); int count = 0;
                while (iterator.hasNext()) {
                    if (!publicationStarted) check(cancelled);
                    Path path = iterator.next(); ensure(++count <= 2048, "TREE_SIZE"); canonical(path);
                    String name = root.relativize(path).toString().replace('\\', '/');
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        ensure(directories.contains(name), "UNKNOWN_DIRECTORY"); seenDirs.add(name);
                    } else { ensure(files.containsKey(name), "UNKNOWN_FILE"); verify(name); seenFiles.add(name); }
                }
            }
            ensure(seenDirs.equals(directories) && seenFiles.equals(files.keySet()), "TREE_MISSING");
            List<Pin> selected = List.copyOf(files.values());
            for (int i = 0; i < selected.size(); i++) {
                Pin pin = selected.get(i); Path target = root.resolve(pin.path());
                for (int j = 0; j < i; j++) if (pin.sha256().equals(selected.get(j).sha256()))
                    ensure(!Files.isSameFile(target, root.resolve(selected.get(j).path())), "TARGET_ALIAS");
                for (Pin input : admission.inputs.pins()) if (pin.sha256().equals(input.sha256()))
                    ensure(!Files.isSameFile(target, admission.inputs.resolve(input.path())), "SOURCE_ALIAS");
            }
        }
    }
    private static List<?> list(Object value, int max) throws IOException {
        ensure(value instanceof List<?> && ((List<?>) value).size() <= max, "LIST"); return (List<?>) value;
    }
    private static void ensure(boolean condition, String code) throws IOException { if (!condition) throw new IOException("STAGE_" + code); }
    private static void check(BooleanSupplier cancelled) throws IOException {
        ensure(cancelled != null, "CANCELLATION"); if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new Cancelled();
    }
    private static Outcome failure(State state, String run, String sha, String error, boolean retained) {
        return new Outcome(state, run, sha, null, error, retained);
    }
    private static String code(Exception invalid) {
        String message = invalid.getMessage();
        return message != null && message.matches("STAGE_[A-Z0-9_]+") ? message : "STAGE_IO_REJECTED";
    }

    /** Explicit developer command: copies only, never arms or launches the returned plan. */
    public static void main(String[] args) throws IOException {
        Outcome outcome;
        try {
            ensure(args != null && args.length == 5, "USAGE_REPOSITORY_ROLE_RUNID_JAVAHOME_PLAN_SHA256");
            var request = new CompositionRunPlanner.Request(CompositionRunPlanner.Role.valueOf(args[1]),
                    args[2], Path.of(args[3]));
            outcome = stage(Path.of(args[0]), request, args[4], () -> false);
        } catch (IOException | RuntimeException invalid) {
            outcome = failure(State.BLOCKED, null, null, code(invalid), false);
        }
        System.out.print(CorpusJson.canonicalJson(outcome.toTree()));
        if (outcome.status() != State.STAGED_UNARMED) throw new IOException(outcome.error());
    }
}
