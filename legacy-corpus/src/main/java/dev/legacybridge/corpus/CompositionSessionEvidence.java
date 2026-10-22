package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static dev.legacybridge.corpus.CompositionRunFiles.*;

/**
 * Dev-only, unsigned evidence for an inert helper session. Never launches, installs, arms,
 * repairs or deletes anything. Failure retains the create-new directory and any partial files.
 * Closed-tree/hash rechecks assume a quiescent filesystem; they are not a hostile-writer
 * sandbox, authenticated provenance or a promise of power-loss durability/directory fsync.
 */
final class CompositionSessionEvidence {
    private static final int LOG_MAX = 8 << 20, JSON_MAX = 64 << 10;
    private static final String RECEIPT = "session-receipt.json", PENDING = "session-receipt.pending";
    private static final List<String> CONTENT = List.of("stdout.log", "stderr.log", "collection.json", "binding.json");
    private static final Set<String> ALLOWED = Set.of("stdout.log", "stderr.log", "collection.json", "binding.json", RECEIPT, PENDING);
    private static final List<String> FLAGS = List.of("gameExecutionVerified", "nativeBehaviorVerified", "installationAllowed", "launchContractVerified", "executionAllowed");
    record Binding(String sessionId, String role, String stagePlanSha256, String stageReceiptSha256,
                   String sessionPlanSha256, String helperSha256) { }
    record Publication(Path directory, String receiptSha256) {
        Map<String, Object> toTree() {
            Map<String, Object> tree = envelope("LEGACY_COMPOSITION_SESSION_PUBLICATION", "PUBLISHED_INERT_ONLY");
            tree.put("directory", directory.toString()); tree.put("receiptSha256", receiptSha256);
            return Collections.unmodifiableMap(tree);
        }
    }
    private CompositionSessionEvidence() { }

    static Publication publish(Path directory, Binding binding, CompositionProcessCollector.Result collection,
                               BooleanSupplier cancel) throws IOException {
        try {
            validateBinding(binding); destination(directory); check(cancel);
            ensure(collection != null && collection.status() == CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY
                    && collection.errors().isEmpty() && collection.processExited() && collection.stdoutComplete()
                    && collection.stderrComplete() && Integer.valueOf(0).equals(collection.exitCode())
                    && binding.sessionPlanSha256().equals(collection.planSha256()), "COLLECTION");
            byte[] stdout = collection.stdout().orElseThrow(() -> error("STDOUT_MISSING"));
            byte[] stderr = collection.stderr().orElseThrow(() -> error("STDERR_MISSING"));
            ensure(stdout.length <= LOG_MAX && stderr.length <= LOG_MAX, "LOG_SIZE");
            utf8(stdout); utf8(stderr);
            Map<String, Object> collected = collection.toTree();
            validateCollection(collected, binding, digest(stdout), digest(stderr));
            byte[] collectionBytes = json(collected), bindingBytes = json(bindingTree(binding));
            check(cancel); destination(directory); Files.createDirectory(directory);
            Map<String, Pin> written = new LinkedHashMap<>();
            write(directory, "stdout.log", stdout, written, cancel);
            write(directory, "stderr.log", stderr, written, cancel);
            write(directory, "collection.json", collectionBytes, written, cancel);
            write(directory, "binding.json", bindingBytes, written, cancel);
            Map<String, Object> receipt = envelope("LEGACY_COMPOSITION_SESSION_EVIDENCE", "PUBLISHED_INERT_ONLY");
            receipt.put("sessionId", binding.sessionId()); receipt.put("role", binding.role());
            receipt.put("files", written.values().stream().map(Pin::tree).toList());
            write(directory, PENDING, json(receipt), written, cancel);
            check(cancel); audit(directory, written);
            ensure(!Files.exists(directory.resolve(RECEIPT), LinkOption.NOFOLLOW_LINKS), "RECEIPT_EXISTS");
            // No ATOMIC_MOVE or REPLACE_EXISTING: neither a concurrent destination nor an old receipt is overwritten.
            Files.move(directory.resolve(PENDING), directory.resolve(RECEIPT));
            Pin pending = written.remove(PENDING);
            written.put(RECEIPT, new Pin(RECEIPT, pending.sha256(), pending.bytes()));
            // Publication is the barrier: later cancellation does not relabel a completed, rechecked publication.
            audit(directory, written);
            Map<String, Object> inspected = inspectChecked(directory, binding);
            ensure("RECORDED_INERT_ONLY".equals(inspected.get("status")), "FINAL_INSPECTION");
            ensure(pending.sha256().equals(inspected.get("receiptSha256")), "FINAL_RECEIPT_DRIFT");
            return new Publication(directory, pending.sha256());
        } catch (IOException | RuntimeException invalid) { throw sanitized(invalid); }
    }

    /**
     * Read-only recovery classification; never resumes publication or gives a partial tree authority.
     * Recorded binding verification is equality of supplied hash assertions, not proof that this
     * inspector observed a process or independently acquired the selected stage/helper artifacts.
     */
    static Map<String, Object> inspect(Path directory, Binding binding) throws IOException {
        try { validateBinding(binding); return inspectChecked(directory, binding); }
        catch (IOException | RuntimeException invalid) { throw sanitized(invalid); }
    }

    private static Map<String, Object> inspectChecked(Path directory, Binding binding) throws IOException {
        Map<String, Pin> found = scan(directory);
        if (!found.containsKey(RECEIPT)) {
            Map<String, Object> incomplete = envelope("LEGACY_COMPOSITION_SESSION_INSPECTION", "INCOMPLETE");
            incomplete.put("sessionId", binding.sessionId()); incomplete.put("role", binding.role());
            incomplete.put("receiptSha256", null); incomplete.put("recordedBindingVerified", false);
            incomplete.put("filesPresent", List.copyOf(found.keySet()));
            audit(directory, found);
            return Collections.unmodifiableMap(incomplete);
        }
        ensure(found.keySet().equals(Set.of("stdout.log", "stderr.log", "collection.json", "binding.json", RECEIPT)), "COMPLETE_TREE");
        Map<String, Object> receipt = readJson(directory, found.get(RECEIPT));
        keys(receipt, "schemaVersion", "reportType", "status", "sessionKind", "sessionId", "role", "files",
                "gameExecutionVerified", "nativeBehaviorVerified", "installationAllowed", "launchContractVerified", "executionAllowed");
        validateEnvelope(receipt, "LEGACY_COMPOSITION_SESSION_EVIDENCE", "PUBLISHED_INERT_ONLY");
        ensure(binding.sessionId().equals(receipt.get("sessionId")) && binding.role().equals(receipt.get("role")), "RECEIPT_BINDING");
        ensure(receipt.get("files") instanceof List<?> && ((List<?>) receipt.get("files")).size() == CONTENT.size(), "RECEIPT_FILES");
        Set<String> seen = new java.util.HashSet<>();
        for (Object raw : (List<?>) receipt.get("files")) {
            Map<String, Object> entry = object(raw); keys(entry, "path", "sha256", "bytes");
            String name = string(entry.get("path"));
            ensure(CONTENT.contains(name) && seen.add(name), "RECEIPT_PATH");
            Pin actual = found.get(name);
            ensure(actual != null && actual.sha256().equals(entry.get("sha256")) && actual.bytes() == integer(entry.get("bytes")), "RECEIPT_PIN");
        }
        ensure(seen.equals(Set.copyOf(CONTENT)), "RECEIPT_FILES");
        Map<String, Object> recordedBinding = readJson(directory, found.get("binding.json"));
        ensure(CorpusJson.canonicalJson(recordedBinding).equals(CorpusJson.canonicalJson(bindingTree(binding))), "BINDING_DRIFT");
        Map<String, Object> collection = readJson(directory, found.get("collection.json"));
        validateCollection(collection, binding, found.get("stdout.log").sha256(), found.get("stderr.log").sha256());
        utf8(read(directory, found.get("stdout.log"))); utf8(read(directory, found.get("stderr.log")));
        audit(directory, found);
        Map<String, Object> result = envelope("LEGACY_COMPOSITION_SESSION_INSPECTION", "RECORDED_INERT_ONLY");
        result.put("sessionId", binding.sessionId()); result.put("role", binding.role());
        result.put("receiptSha256", found.get(RECEIPT).sha256()); result.put("recordedBindingVerified", true);
        return Collections.unmodifiableMap(result);
    }

    private static void validateCollection(Map<String, Object> value, Binding binding, String stdout, String stderr) throws IOException {
        keys(value, "schemaVersion", "reportType", "status", "planSha256", "commandSha256", "elapsedMillis", "started", "pid",
                "processExited", "exitCode", "stdoutComplete", "stderrComplete", "terminationRequested", "forcedTermination",
                "stdoutSha256", "stderrSha256", "errors", "gameExecutionVerified", "nativeBehaviorVerified", "installationAllowed",
                "executionAllowed", "launchContractVerified");
        ensure(integer(value.get("schemaVersion")) == 1 && "LEGACY_COMPOSITION_PROCESS_COLLECTION".equals(value.get("reportType"))
                && "PROCESS_COLLECTED_ONLY".equals(value.get("status")), "COLLECTION_SCHEMA");
        ensure(binding.sessionPlanSha256().equals(value.get("planSha256")) && validHash(value.get("commandSha256")), "COLLECTION_BINDING");
        ensure(integer(value.get("elapsedMillis")) > 0 && integer(value.get("pid")) > 0 && integer(value.get("exitCode")) == 0, "COLLECTION_EXIT");
        for (String flag : List.of("started", "processExited", "stdoutComplete", "stderrComplete")) ensure(Boolean.TRUE.equals(value.get(flag)), "COLLECTION_COMPLETION");
        for (String flag : List.of("terminationRequested", "forcedTermination")) ensure(Boolean.FALSE.equals(value.get(flag)), "COLLECTION_TERMINATION");
        ensure(List.of().equals(value.get("errors")) && stdout.equals(value.get("stdoutSha256")) && stderr.equals(value.get("stderrSha256")), "COLLECTION_OUTPUT");
        falseFlags(value);
    }
    private static Map<String, Object> bindingTree(Binding binding) {
        Map<String, Object> tree = envelope("LEGACY_COMPOSITION_SESSION_BINDING", "BOUND_INERT_ONLY");
        tree.put("sessionId", binding.sessionId()); tree.put("role", binding.role());
        tree.put("stagePlanSha256", binding.stagePlanSha256()); tree.put("stageReceiptSha256", binding.stageReceiptSha256());
        tree.put("sessionPlanSha256", binding.sessionPlanSha256()); tree.put("helperSha256", binding.helperSha256());
        return tree;
    }
    private static void validateBinding(Binding binding) throws IOException {
        ensure(binding != null && binding.sessionId() != null && binding.sessionId().matches("session-[a-z0-9][a-z0-9-]{0,40}"), "SESSION_ID");
        ensure("SOURCE".equals(binding.role()) || "TARGET".equals(binding.role()), "ROLE");
        ensure(validHash(binding.stagePlanSha256()) && validHash(binding.stageReceiptSha256())
                && validHash(binding.sessionPlanSha256()) && validHash(binding.helperSha256()), "BINDING_HASH");
    }
    private static Map<String, Object> envelope(String type, String status) {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("schemaVersion", 1); tree.put("reportType", type); tree.put("status", status); tree.put("sessionKind", "INERT_HELPER_ONLY");
        FLAGS.forEach(flag -> tree.put(flag, false)); return tree;
    }
    private static void validateEnvelope(Map<String, Object> value, String type, String status) throws IOException {
        ensure(integer(value.get("schemaVersion")) == 1 && type.equals(value.get("reportType")) && status.equals(value.get("status"))
                && "INERT_HELPER_ONLY".equals(value.get("sessionKind")), "ENVELOPE"); falseFlags(value);
    }
    private static void falseFlags(Map<String, Object> value) throws IOException {
        for (String flag : FLAGS) ensure(Boolean.FALSE.equals(value.get(flag)), "AUTHORITY_FLAG");
    }
    private static void destination(Path directory) throws IOException {
        ensure(directory != null && directory.isAbsolute() && directory.equals(directory.normalize()) && directory.getParent() != null, "DIRECTORY");
        CompositionRunFiles.directory(directory.getParent());
        ensure(!Files.exists(directory, LinkOption.NOFOLLOW_LINKS), "DESTINATION_EXISTS");
    }
    private static void write(Path directory, String name, byte[] bytes, Map<String, Pin> written, BooleanSupplier cancel) throws IOException {
        check(cancel); audit(directory, written);
        ensure(ALLOWED.contains(name) && !written.containsKey(name) && bytes.length <= cap(name), "WRITE_LIMIT");
        try (var output = FileChannel.open(directory.resolve(name), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer data = ByteBuffer.wrap(bytes);
            while (data.hasRemaining()) { check(cancel); output.write(data); }
            output.force(true);
        }
        Pin pin = new Pin(name, digest(bytes), bytes.length); verify(directory, pin); written.put(name, pin);
    }
    private static Map<String, Pin> scan(Path directory) throws IOException {
        CompositionRunFiles.directory(directory); Map<String, Pin> found = new LinkedHashMap<>();
        try (var listing = Files.newDirectoryStream(directory)) {
            for (Path path : listing) {
                String name = path.getFileName().toString(); ensure(found.size() < ALLOWED.size() && ALLOWED.contains(name), "UNKNOWN_ENTRY");
                canonical(path); ensure(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "REGULAR_FILE");
                long size = Files.size(path); ensure(size >= 0 && size <= cap(name), "FILE_SIZE");
                Pin pin = new Pin(name, hash(path), size); verify(directory, pin);
                for (Pin previous : found.values()) if (previous.sha256().equals(pin.sha256()))
                    ensure(!Files.isSameFile(path, directory.resolve(previous.path())), "FILE_ALIAS");
                found.put(name, pin);
            }
        }
        return found;
    }
    private static void audit(Path directory, Map<String, Pin> expected) throws IOException {
        Map<String, Pin> observed = scan(directory);
        ensure(observed.equals(expected), "TREE_DRIFT");
    }
    private static byte[] read(Path directory, Pin pin) throws IOException {
        ensure(pin != null && pin.bytes() <= cap(pin.path()), "READ_LIMIT"); verify(directory, pin);
        byte[] bytes;
        try (var input = Files.newInputStream(directory.resolve(pin.path()), LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(cap(pin.path()) + 1); }
        ensure(bytes.length == pin.bytes() && digest(bytes).equals(pin.sha256()), "READ_DRIFT"); verify(directory, pin); return bytes;
    }
    private static Map<String, Object> readJson(Path directory, Pin pin) throws IOException {
        ensure(pin != null && pin.bytes() > 0, "JSON_EMPTY"); String text = utf8(read(directory, pin));
        Object value;
        try { value = dev.legacybridge.converter.config.BoundedJsonParser.parse(text); }
        catch (dev.legacybridge.converter.config.BoundedJsonParser.JsonSyntaxException invalid) { throw error("JSON_INVALID"); }
        Map<String, Object> tree = object(value);
        ensure(CorpusJson.canonicalJson(tree).equals(text), "JSON_CANONICAL"); return tree;
    }
    private static void verify(Path directory, Pin pin) throws IOException {
        Path path = directory.resolve(pin.path()); canonical(path);
        ensure(Files.size(path) == pin.bytes() && hash(path).equals(pin.sha256()), "FILE_DRIFT");
    }
    private static byte[] json(Map<String, Object> value) throws IOException {
        byte[] bytes = CorpusJson.canonicalJson(value).getBytes(StandardCharsets.UTF_8); ensure(bytes.length <= JSON_MAX, "JSON_SIZE"); return bytes;
    }
    private static String utf8(byte[] bytes) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static int cap(String name) { return name.endsWith(".log") ? LOG_MAX : JSON_MAX; }
    private static boolean validHash(Object value) { return value instanceof String hash && hash.matches("[a-f0-9]{64}"); }
    private static void check(BooleanSupplier cancelled) throws IOException {
        ensure(cancelled != null, "CANCELLATION");
        boolean stop;
        try { stop = cancelled.getAsBoolean(); } catch (RuntimeException invalid) { throw error("CANCELLATION_CALLBACK"); }
        ensure(!stop && !Thread.currentThread().isInterrupted(), "CANCELLED");
    }
    private static void ensure(boolean value, String code) throws IOException { if (!value) throw error(code); }
    private static IOException error(String code) { return new IOException("SESSION_EVIDENCE_" + code); }
    private static IOException sanitized(Exception invalid) {
        String message = invalid.getMessage();
        return message != null && message.matches("SESSION_EVIDENCE_[A-Z0-9_]+") ? new IOException(message) : error("IO_REJECTED");
    }
}
