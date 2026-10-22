package dev.legacybridge.corpus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

import static dev.legacybridge.corpus.CompositionRunFiles.*;

/**
 * Closed dev-only integration exerciser. NEVER uses the native plan's argv or arms a stage.
 * The sole executable payload must equal the inert helper resource on the trusted test classpath;
 * that resource is absent from all shipped artifacts, so this API is unavailable there.
 * Not a sandbox for an untrusted parent JVM, OS, JDK installation or concurrent filesystem writer.
 * Success concerns this inert session, never Forge discovery, behavior or installation.
 */
final class CompositionInertSession {
    static final String HELPER = "dev.legacybridge.corpus.CompositionCollectorHelper";
    private static final String CLASS = HELPER.replace('.', '/') + ".class";
    private static final int HELPER_LIMIT = 1 << 20;
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    enum Mode { OK, STDIN, EXIT, SLEEP, SPAM, BOTH, INVALID, SENSITIVE, DRIFT, ENVIRONMENT }
    enum Phase { PREPARED, COLLECTED, BEFORE_PUBLICATION, PUBLISHED }
    @FunctionalInterface interface Hook { void at(Phase phase, Path root) throws IOException; }
    record Outcome(String status, Path directory, String receiptSha256, List<String> errors,
                   Map<String, Object> collection) {
        Outcome { errors = List.copyOf(errors); collection = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(collection)); }
        Map<String, Object> toTree() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("schemaVersion", 1); result.put("reportType", "LEGACY_COMPOSITION_INERT_SESSION");
            result.put("status", status); result.put("sessionKind", "INERT_HELPER_ONLY");
            result.put("directory", directory == null ? null : directory.toString()); result.put("receiptSha256", receiptSha256);
            result.put("errors", errors); result.put("collection", collection);
            result.put("runtimeValidation", "NOT_RUN"); result.put("executionAllowed", false);
            result.put("gameExecutionVerified", false); result.put("nativeBehaviorVerified", false);
            result.put("installationAllowed", false); result.put("launchContractVerified", false);
            return java.util.Collections.unmodifiableMap(result);
        }
    }
    private CompositionInertSession() { }

    static Outcome run(CompositionStagedRunVerifier.Snapshot snapshot, Path helper, String sessionId,
                       Mode mode, CompositionProcessCollector.Limits limits, BooleanSupplier cancelled) {
        return run(snapshot, helper, sessionId, mode, limits, cancelled, (phase, root) -> { });
    }

    // Deterministic failure injection only; never exposed by a CLI, service or product UI.
    static Outcome run(CompositionStagedRunVerifier.Snapshot snapshot, Path helper, String sessionId,
                       Mode mode, CompositionProcessCollector.Limits limits, BooleanSupplier cancelled, Hook hook) {
        if (!ACTIVE.compareAndSet(false, true)) return failure("REJECTED", null, "BUSY", Map.of());
        Path operation = null; Map<String, Object> collection = Map.of(); boolean publishedReceipt = false;
        try {
            check(cancelled); ensure(snapshot != null && mode != null && hook != null, "REQUEST");
            ensure(sessionId != null && sessionId.matches("session-[a-z0-9][a-z0-9-]{0,40}"), "SESSION_ID");
            ensure(limits != null && limits.timeoutMillis() > 0 && limits.timeoutMillis() <= 240_000
                    && limits.graceMillis() > 0 && limits.graceMillis() <= 60_000
                    && limits.streamBytes() > 0 && limits.streamBytes() <= (8 << 20), "LIMITS");
            var verified = snapshot.recheck(cancelled);
            byte[] helperBytes = closedHelper(helper); check(cancelled);
            var helperPin = pin(helper);
            ensure(helperPin.sha256().equals(digest(helperBytes)), "HELPER_DRIFT");
            Path parent = verified.repository().resolve("build/composition-inert-sessions");
            if (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) { directory(parent.getParent()); Files.createDirectory(parent); }
            directory(parent); Path destination = parent.resolve(sessionId); absentTree(destination);
            Files.createDirectory(destination); operation = destination; directory(operation);
            Path cwd = operation.resolve("work"); Files.createDirectory(cwd);
            Files.createDirectory(cwd.resolve("tmp")); Files.createDirectory(cwd.resolve("empty-user-home"));
            Files.createDirectory(cwd.resolve("empty-user-home/absent-gradle"));
            Path copied = cwd.resolve("inert-helper.jar"); write(copied, helperBytes); Arrays.fill(helperBytes, (byte) 0);
            Path sentinel = cwd.resolve("input.dat"); write(sentinel, "original".getBytes(StandardCharsets.UTF_8));
            Map<String, Object> nativePlan = object(verified.plan().report().get("plan"));
            Map<String, Object> java = object(nativePlan.get("java"));
            List<String> arguments = List.of(string(java.get("executable")), "-Xms16M", "-Xmx32M", "-XX:-UsePerfData",
                    "-Duser.home=" + cwd.resolve("empty-user-home"), "-Djava.io.tmpdir=" + cwd.resolve("tmp"),
                    "-jar", copied.toString(), mode.name().toLowerCase(Locale.ROOT));
            List<CompositionProcessCollector.Input> pins = new ArrayList<>(verified.immutableInputs());
            pins.add(helperPin); pins.add(pin(copied)); pins.add(pin(sentinel));
            Map<String, Object> sessionPlan = new LinkedHashMap<>();
            sessionPlan.put("schemaVersion", 1); sessionPlan.put("reportType", "LEGACY_COMPOSITION_INERT_SESSION_PLAN");
            sessionPlan.put("sessionKind", "INERT_HELPER_ONLY"); sessionPlan.put("sessionId", sessionId);
            sessionPlan.put("role", verified.plan().role().name()); sessionPlan.put("runId", verified.plan().runId());
            sessionPlan.put("stagePlanSha256", verified.plan().sha256()); sessionPlan.put("stageReceiptSha256", verified.receiptSha256());
            sessionPlan.put("launchContract", "closed-test-helper-v1"); sessionPlan.put("workingDirectory", cwd.toString());
            sessionPlan.put("arguments", arguments); sessionPlan.put("java", java);
            sessionPlan.put("helperSha256", helperPin.sha256()); sessionPlan.put("helperClassSha256", digest(trustedHelper()));
            sessionPlan.put("environmentPolicy", "collector-minimum-owned-homes-v1");
            sessionPlan.put("environment", environment(cwd));
            sessionPlan.put("limits", Map.of("timeoutMillis", limits.timeoutMillis(), "graceMillis", limits.graceMillis(),
                    "streamBytes", limits.streamBytes(), "heapBytes", 32 << 20, "concurrency", 1, "queue", 0));
            sessionPlan.put("immutableFiles", pins.stream().map(p -> Map.of("path", p.path().toString(), "sha256", p.sha256(), "bytes", p.bytes())).toList());
            sessionPlan.put("nativeArgumentsUsed", false); sessionPlan.put("nativeExecutionAllowed", false);
            sessionPlan.put("gameExecutionVerified", false); sessionPlan.put("installationAllowed", false);
            Path storedPlan = operation.resolve("session-plan.json"); write(storedPlan, CorpusJson.canonicalJson(sessionPlan).getBytes(StandardCharsets.UTF_8));
            var sessionPin = pin(storedPlan); pins.add(sessionPin);
            hook.at(Phase.PREPARED, operation); check(cancelled);
            verified.recheck(cancelled); closedHelper(copied); closedWork(operation); verifyPins(pins, cancelled);
            var result = CompositionProcessCollector.collect(new CompositionProcessCollector.Launch(cwd, arguments, sessionPin.sha256(), pins, limits), cancelled);
            collection = result.toTree(); hook.at(Phase.COLLECTED, operation);
            if (result.status() != CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY)
                return new Outcome(result.status().name(), operation, null, result.errors().stream().map(Enum::name).toList(), collection);
            check(cancelled); verified.recheck(cancelled); closedWork(operation); verifyPins(pins, cancelled);
            var binding = new CompositionSessionEvidence.Binding(sessionId, verified.plan().role().name(), verified.plan().sha256(),
                    verified.receiptSha256(), sessionPin.sha256(), helperPin.sha256());
            hook.at(Phase.BEFORE_PUBLICATION, operation); check(cancelled);
            verified.recheck(cancelled); closedWork(operation); verifyPins(pins, cancelled);
            var published = CompositionSessionEvidence.publish(operation.resolve("evidence"), binding, result, cancelled);
            publishedReceipt = true;
            hook.at(Phase.PUBLISHED, operation);
            // A late integrity failure may leave a private final-named record; it is not approved by this result.
            // Publication owns its final cancellation barrier; cancellation afterward is not retroactive.
            verified.recheck(() -> false); verifyPins(pins, () -> false); closedWork(operation, true);
            ensure(published.receiptSha256().equals(CompositionSessionEvidence.inspect(operation.resolve("evidence"), binding).get("receiptSha256")), "EVIDENCE_DRIFT");
            return new Outcome("PUBLISHED_INERT_ONLY", operation, published.receiptSha256(), List.of(), collection);
        } catch (IOException | RuntimeException invalid) {
            String code = invalid.getMessage();
            if ("STAGED_VERIFY_CANCELLED".equals(code) || "SESSION_EVIDENCE_CANCELLED".equals(code)) code = "COMPOSE_INERT_CANCELLED";
            if (publishedReceipt && (Thread.currentThread().isInterrupted() || "COMPOSE_INERT_CANCELLED".equals(code)))
                code = "COMPOSE_INERT_INTEGRITY_RECHECK_INTERRUPTED";
            if (code == null || !code.matches("COMPOSE_INERT_[A-Z_]+")) code = "COMPOSE_INERT_REJECTED";
            return failure(code.equals("COMPOSE_INERT_CANCELLED") ? "CANCELLED" : operation == null ? "REJECTED" : "FAILED", operation, code, collection);
        } finally { ACTIVE.set(false); }
    }

    private static Outcome failure(String status, Path root, String error, Map<String, Object> collection) {
        return new Outcome(status, root, null, List.of(error), collection);
    }
    private static void check(BooleanSupplier cancelled) throws IOException {
        ensure(cancelled != null, "CANCEL_CALLBACK");
        if (Thread.currentThread().isInterrupted()) throw new IOException("COMPOSE_INERT_CANCELLED");
        final boolean value;
        try { value = cancelled.getAsBoolean(); } catch (RuntimeException invalid) { throw new IOException("COMPOSE_INERT_CANCEL_CALLBACK"); }
        ensure(!value, "CANCELLED");
    }
    private static void ensure(boolean value, String code) throws IOException { if (!value) throw new IOException("COMPOSE_INERT_" + code); }
    private static Map<String, String> environment(Path cwd) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            String selected = System.getenv("SystemRoot"); ensure(selected != null, "ENVIRONMENT");
            Path windows = Path.of(selected); directory(windows);
            result.put("SystemRoot", windows.toString()); result.put("windir", windows.toString());
        }
        result.put("TEMP", cwd.resolve("tmp").toString()); result.put("TMP", cwd.resolve("tmp").toString());
        result.put("HOME", cwd.resolve("empty-user-home").toString()); result.put("USERPROFILE", cwd.resolve("empty-user-home").toString());
        result.put("GRADLE_USER_HOME", cwd.resolve("empty-user-home/absent-gradle").toString()); return Map.copyOf(result);
    }
    private static CompositionProcessCollector.Input pin(Path path) throws IOException {
        return new CompositionProcessCollector.Input(path, hash(path), Files.size(path));
    }
    private static void verifyPins(List<CompositionProcessCollector.Input> pins, BooleanSupplier cancelled) throws IOException {
        ensure(pins.size() <= 512, "INPUT_LIMIT"); long total = 0; List<CompositionProcessCollector.Input> seen = new ArrayList<>();
        for (var pin : pins) {
            check(cancelled); total += pin.bytes(); ensure(total <= (512L << 20), "INPUT_LIMIT");
            ensure(pin.bytes() == Files.size(pin.path()) && pin.sha256().equals(hash(pin.path())), "INPUT_DRIFT");
            for (var prior : seen) if (prior.sha256().equals(pin.sha256())) ensure(!Files.isSameFile(pin.path(), prior.path()), "INPUT_ALIAS");
            seen.add(pin);
        }
    }
    private static byte[] trustedHelper() throws IOException {
        // Read bytes, never load a mod class. Trusted resource is deliberately test-only, not an accepted caller hash.
        try (var input = CompositionInertSession.class.getClassLoader().getResourceAsStream(CLASS)) {
            ensure(input != null, "HELPER_UNAVAILABLE"); byte[] bytes = input.readNBytes(HELPER_LIMIT + 1);
            ensure(bytes.length > 0 && bytes.length <= HELPER_LIMIT, "HELPER_SIZE"); return bytes;
        }
    }
    static byte[] closedHelper(Path helper) throws IOException {
        byte[] trusted = trustedHelper(); canonical(helper); ensure(Files.size(helper) <= HELPER_LIMIT, "HELPER_SIZE");
        String sha = hash(helper); Set<String> names = new LinkedHashSet<>();
        Manifest manifest = new Manifest(); manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, HELPER);
        ByteArrayOutputStream expectedManifest = new ByteArrayOutputStream(); manifest.write(expectedManifest);
        try (ZipFile zip = new ZipFile(helper.toFile(), StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); String name = entry.getName();
                ensure(names.add(name) && Set.of("META-INF/MANIFEST.MF", CLASS).contains(name) && !entry.isDirectory(), "HELPER_ENTRIES");
                ensure(entry.getSize() > 0 && entry.getSize() <= HELPER_LIMIT, "HELPER_SIZE");
                try (var input = zip.getInputStream(entry)) {
                    byte[] actual = input.readNBytes(HELPER_LIMIT + 1);
                    ensure(actual.length == entry.getSize() && Arrays.equals(actual, name.equals(CLASS) ? trusted : expectedManifest.toByteArray()), "HELPER_CONTENT");
                }
            }
        }
        ensure(names.equals(Set.of("META-INF/MANIFEST.MF", CLASS)), "HELPER_ENTRIES");
        byte[] bytes;
        try (var input = Files.newInputStream(helper, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(HELPER_LIMIT + 1); }
        ensure(bytes.length <= HELPER_LIMIT && digest(bytes).equals(sha) && hash(helper).equals(sha), "HELPER_DRIFT");
        return bytes;
    }
    private static void write(Path path, byte[] bytes) throws IOException {
        ensure(bytes.length <= (2 << 20), "WRITE_LIMIT"); directory(path.getParent());
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
        }
        ensure(Files.size(path) == bytes.length && hash(path).equals(digest(bytes)), "WRITE_DRIFT");
    }
    private static void closedWork(Path root) throws IOException {
        closedWork(root, false);
    }
    private static void closedWork(Path root, boolean published) throws IOException {
        Set<String> directories = new LinkedHashSet<>(Set.of("work", "work/tmp", "work/empty-user-home", "work/empty-user-home/absent-gradle"));
        Set<String> files = new LinkedHashSet<>(Set.of("session-plan.json", "work/inert-helper.jar", "work/input.dat"));
        if (published) {
            directories.add("evidence");
            for (String file : List.of("stdout.log", "stderr.log", "binding.json", "collection.json", "session-receipt.json")) files.add("evidence/" + file);
        }
        Set<String> found = new LinkedHashSet<>(); directory(root);
        try (var paths = Files.walk(root, 5)) {
            var iterator = paths.iterator(); int count = 0;
            while (iterator.hasNext()) {
                Path path = iterator.next(); ensure(++count <= 1 + directories.size() + files.size(), "WORK_TREE"); canonical(path); if (path.equals(root)) continue;
                String name = root.relativize(path).toString().replace('\\', '/');
                ensure(found.add(name), "WORK_TREE");
                ensure(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ? directories.contains(name)
                        : Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && files.contains(name), "WORK_TREE");
            }
        }
        ensure(found.size() == directories.size() + files.size(), "WORK_TREE");
    }
}
