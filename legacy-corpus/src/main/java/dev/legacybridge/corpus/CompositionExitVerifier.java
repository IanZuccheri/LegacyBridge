package dev.legacybridge.corpus;

import dev.legacybridge.converter.config.BoundedJsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only checks of explicitly selected, unsigned local post-exit records.
 * Never starts/observes a process, writes a receipt, attests native execution or authorizes installation.
 * A recorded exit flag is an assertion, not independent evidence that a process really terminated.
 * Use a quiescent trusted evidence tree; ordinary drift checks are not a hostile-writer sandbox.
 */
public final class CompositionExitVerifier {
    private static final long MAX_FILE = 128L << 20, MAX_TOTAL = 512L << 20;
    private static final int MAX_EXIT = 64 << 10, MAX_LOG = 8 << 20, MAX_LINE = 64 << 10;
    private static final Pattern NATIVE = Pattern.compile("^\\[[0-9]{2}:[0-9]{2}:[0-9]{2}\\] \\[Server thread/INFO\\] \\[minecraft/(DedicatedServer|MinecraftServer|ChunkManager)\\]: (.*)$");
    private static final Pattern DONE = Pattern.compile("Done \\([0-9]+\\.[0-9]+s\\)! For help, type \"help\"");
    private static final Pattern CHUNKS = Pattern.compile("ThreadedAnvilChunkStorage \\(([^)]+)\\): All chunks are saved");
    private static final Pattern SEVERE = Pattern.compile("(?i)(?:^|\\s|/|\\[)(?:ERROR|FATAL)\\b");
    private static final Pattern EXCEPTION = Pattern.compile("(?:Exception in thread|(?:[A-Za-z_$][A-Za-z0-9_$]*\\.)+[A-Za-z_$][A-Za-z0-9_$]*(?:Exception|Error)(?::|$))");
    private static final Pattern SENSITIVE = Pattern.compile("(?i)(?:access[_-]?token|refresh[_-]?token|client[_-]?token|"
            + "authorization\\s*[:=]|bearer\\s+[A-Za-z0-9._-]+|--(?:username|uuid|clientid|xuid|userproperties|password|token|auth(?:entication)?|secret)\\b|"
            + "(?:password|passwd|secret)\\s*[:=]|-D(?:auth|token|password)[.=]|"
            + "Picked up (?:JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS))");
    private static final Set<String> DIMENSIONS = Set.of("world", "DIM-1", "DIM1");
    private static final List<String> FAILURES = List.of("Encountered an unexpected exception", "Exception stopping the server",
            "Failed to unlock level", "Failed to start the minecraft server", "Exception closing the level");

    public enum State { RECORDED_EXIT_ONLY }

    public record Summary(State status, int files, long bytes, String observationIndexSha256, String exitIndexSha256,
                          String sourceSha256, String candidateSha256) {
        public Map<String, Object> toTree() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("schemaVersion", 1); result.put("reportType", "LEGACY_COMPOSITION_EXIT_CHECK");
            result.put("status", status.name()); result.put("filesChecked", files); result.put("bytesChecked", bytes);
            result.put("observationIndexSha256", observationIndexSha256); result.put("exitIndexSha256", exitIndexSha256);
            result.put("sourceSha256", sourceSha256); result.put("candidateSha256", candidateSha256);
            result.put("gameExecutionVerified", false); result.put("installationAllowed", false); result.put("launchContractVerified", false);
            result.put("pending", List.of("APPROVED_LAUNCH_PLAN_ASSOCIATION", "INDEPENDENT_PROCESS_OBSERVATION",
                    "INDEPENDENT_NATIVE_EVIDENCE_REVIEW", "CLIENT_ASSETS_TRANSLATIONS", "RESOURCE_RELOAD", "MULTIPLAYER", "COMPOSE_01_ACCEPTANCE"));
            return java.util.Collections.unmodifiableMap(result);
        }
    }

    private record Artifact(Path path, String sha256, long bytes) { }
    private final List<Artifact> files = new ArrayList<>();
    private long total;

    private CompositionExitVerifier() { }

    /** First requires a complete recorded observation pair; all selected files remain untouched. */
    public static Summary verify(Path repository, String observationIndexPath, String exitIndexPath) throws IOException {
        try {
            return new CompositionExitVerifier().check(directory(repository), observationIndexPath, exitIndexPath);
        } catch (IOException invalid) {
            // Never include captured contents, arbitrary parser details or file contents in diagnostics.
            if (invalid.getMessage() != null && invalid.getMessage().matches("EXIT_[A-Z0-9_]+")) throw invalid;
            throw new IOException("EXIT_IO_REJECTED");
        }
    }

    private Summary check(Path root, String observationIndexPath, String exitIndexPath) throws IOException {
        CompositionObservationVerifier.Summary observations = observations(root, observationIndexPath);
        require(observations.status() == CompositionObservationVerifier.State.RECORDED_MATCH_ONLY, "OBSERVATIONS_NOT_RECORDED");
        Artifact observationIndex = selected(root, observationIndexPath, CorpusJson.MAX_BYTES);
        Map<String, Object> observationTree = json(observationIndex, CorpusJson.MAX_BYTES);
        Artifact exitIndex = selected(root, exitIndexPath, CorpusJson.MAX_BYTES);
        Map<String, Object> index = json(exitIndex, CorpusJson.MAX_BYTES);
        keys(index, "schemaVersion", "reportType", "oracle", "observationIndexSha256", "runs");
        eq(index.get("schemaVersion"), 1, "INDEX_SCHEMA");
        eq(index.get("reportType"), "LEGACY_COMPOSITION_EXIT_INDEX", "INDEX_TYPE");
        eq(index.get("oracle"), CompositionObservationVerifier.ORACLE, "ORACLE");
        eq(index.get("observationIndexSha256"), observationIndex.sha256, "OBSERVATION_INDEX_HASH");
        Map<String, Object> artifacts = object(observationTree.get("artifacts"));
        for (String role : List.of("original", "candidate", "proof", "receipt", "runtime"))
            pinned(root, artifacts.get(role), MAX_FILE, false);
        Map<String, Object> observationRuns = object(observationTree.get("runs")), exitRuns = object(index.get("runs"));
        keys(observationRuns, "source", "target"); keys(exitRuns, "source", "target");
        Set<String> seenIds = new HashSet<>();
        for (String role : List.of("source", "target")) {
            Map<String, Object> observationRun = object(observationRuns.get(role)), run = object(exitRuns.get(role));
            keys(run, "id", "exit", "stderr");
            String id = text(run.get("id"));
            require(id.matches("run-[a-z0-9][a-z0-9-]{0,40}") && seenIds.add(id), "RUN_ID");
            eq(observationRun.get("id"), id, "RUN_BINDING");
            pinned(root, observationRun.get("probe"), MAX_FILE, false);
            Artifact observation = pinned(root, observationRun.get("observation"), CorpusJson.MAX_BYTES, false);
            Artifact stdout = pinned(root, observationRun.get("log"), MAX_LOG, false);
            Artifact exit = pinned(root, run.get("exit"), MAX_EXIT, false);
            Artifact stderr = pinned(root, run.get("stderr"), MAX_LOG, true);
            Map<String, Object> record = json(exit, MAX_EXIT);
            keys(record, "schemaVersion", "reportType", "runId", "role", "stdoutSha256", "stderrSha256", "observationSha256",
                    "processExited", "exitCode", "stdoutComplete", "stderrComplete", "timedOut", "cancelled", "forcedTermination", "elapsedMillis");
            eq(record.get("schemaVersion"), 1, "RECORD_SCHEMA");
            eq(record.get("reportType"), "LEGACY_COMPOSITION_PROCESS_EXIT", "RECORD_TYPE");
            eq(record.get("runId"), id, "RECORD_RUN"); eq(record.get("role"), role.toUpperCase(java.util.Locale.ROOT), "RECORD_ROLE");
            eq(record.get("stdoutSha256"), stdout.sha256, "STDOUT_HASH"); eq(record.get("stderrSha256"), stderr.sha256, "STDERR_HASH");
            eq(record.get("observationSha256"), observation.sha256, "OBSERVATION_HASH");
            for (String key : List.of("processExited", "stdoutComplete", "stderrComplete")) eq(record.get(key), true, "INCOMPLETE_EXIT");
            for (String key : List.of("timedOut", "cancelled", "forcedTermination")) eq(record.get(key), false, "ABORTED_EXIT");
            eq(record.get("exitCode"), 0, "EXIT_CODE");
            long elapsed = number(record.get("elapsedMillis")); require(elapsed >= 1 && elapsed <= 300_000, "ELAPSED");
            nativeLog(stdout, stderr, id, observation.sha256, role.equals("source"));
        }
        // Reuse semantic checking on the selected bytes, including a race between the initial check and selection.
        require(observations.equals(observations(root, observationIndexPath)), "OBSERVATION_DRIFT");
        for (Artifact file : files) hash(file);
        return new Summary(State.RECORDED_EXIT_ONLY, files.size(), total, observationIndex.sha256, exitIndex.sha256,
                observations.sourceSha256(), observations.candidateSha256());
    }

    private static CompositionObservationVerifier.Summary observations(Path root, String index) throws IOException {
        try { return CompositionObservationVerifier.verify(root, index); }
        catch (IOException invalid) { throw new IOException("EXIT_OBSERVATION_REJECTED"); }
    }

    private static void nativeLog(Artifact stdout, Artifact stderr, String id, String observationSha, boolean source) throws IOException {
        String marker = "LEGACYBRIDGE_COMPOSE_CAPTURE_OK run=" + id + " sha256=" + observationSha;
        int stage = 0, captures = 0;
        Set<String> saved = new HashSet<>();
        for (String line : lines(stdout)) {
            rejectFailure(line);
            int probe = line.indexOf("LEGACYBRIDGE_COMPOSE_");
            if (probe >= 0) {
                require(line.substring(probe).equals(marker), "LOG_MARKER");
                require(stage == 1 && ++captures == 1, "LOG_ORDER"); stage = 2;
                continue;
            }
            Matcher nativeLine = NATIVE.matcher(line);
            if (!nativeLine.matches()) continue;
            String logger = nativeLine.group(1), message = nativeLine.group(2);
            if (DONE.matcher(message).matches()) {
                require(logger.equals("DedicatedServer") && stage == 0, "LOG_ORDER"); stage = 1;
            } else if (message.equals("Stopping server")) {
                require(logger.equals("MinecraftServer") && stage == 2, "LOG_ORDER"); stage = 3;
            } else if (message.equals("Saving players")) {
                require(logger.equals("MinecraftServer") && stage == 3, "LOG_ORDER"); stage = 4;
            } else if (message.equals("Saving worlds")) {
                require(logger.equals("MinecraftServer") && stage == 4, "LOG_ORDER"); stage = 5;
            } else if (message.equals("ThreadedAnvilChunkStorage: All dimensions are saved")) {
                require(!source && logger.equals("MinecraftServer") && stage == 5 && saved.equals(DIMENSIONS), "LOG_ORDER"); stage = 6;
            } else {
                Matcher chunks = CHUNKS.matcher(message);
                if (chunks.matches()) {
                    require(logger.equals(source ? "ChunkManager" : "MinecraftServer") && stage >= 5, "LOG_ORDER");
                    require(DIMENSIONS.contains(chunks.group(1)), "LOG_WORLD");
                    saved.add(chunks.group(1)); // Source normally repeats these during close; duplicates are not extra worlds.
                }
            }
        }
        require(captures == 1 && saved.equals(DIMENSIONS) && stage == (source ? 5 : 6), "STOP_INCOMPLETE");
        for (String line : lines(stderr)) {
            rejectFailure(line);
            require(!line.contains("LEGACYBRIDGE_COMPOSE_"), "STDERR_PROBE_MARKER");
        }
    }

    private static void rejectFailure(String line) throws IOException {
        sensitive(line);
        require(!SEVERE.matcher(line).find() && !EXCEPTION.matcher(line).find(), "LOG_FAILURE");
        for (String failure : FAILURES) require(!line.contains(failure), "LOG_FAILURE");
        if (line.contains("LEGACYBRIDGE_COMPOSE_") && !line.contains("LEGACYBRIDGE_COMPOSE_CAPTURE_OK"))
            throw new IOException("EXIT_LOG_FAILURE");
    }

    /** Capture is bounded before decoding; iterate lazily rather than materializing a list of all lines. */
    private static Iterable<String> lines(Artifact file) throws IOException {
        String contents = utf8(capture(file, MAX_LOG));
        int count = 0;
        var iterator = contents.lines().iterator();
        while (iterator.hasNext()) {
            String line = iterator.next(); require(++count <= 100_000, "LOG_LINE_BUDGET");
            require(line.length() <= MAX_LINE && line.getBytes(StandardCharsets.UTF_8).length <= MAX_LINE, "LOG_LINE_SIZE");
        }
        return contents.lines()::iterator;
    }

    private Artifact selected(Path root, String relative, int maximum) throws IOException {
        Path path = safe(root, relative);
        long size = Files.size(path); require(size > 0 && size <= maximum, "BYTE_BUDGET");
        Artifact file = new Artifact(path, digest(path, size), size); add(file); return file;
    }

    private Artifact pinned(Path root, Object raw, long maximum, boolean emptyAllowed) throws IOException {
        Map<String, Object> entry = object(raw); keys(entry, "path", "sha256", "bytes");
        Path path = safe(root, text(entry.get("path"))); String sha = text(entry.get("sha256"));
        require(sha.matches("[a-f0-9]{64}"), "HASH_FORMAT");
        long size = number(entry.get("bytes"));
        require(size >= (emptyAllowed ? 0 : 1) && size <= maximum && size <= MAX_FILE, "BYTE_BUDGET");
        Artifact file = new Artifact(path, sha, size); add(file); hash(file); return file;
    }

    private void add(Artifact file) throws IOException {
        require(files.size() < 32 && total + file.bytes <= MAX_TOTAL, "TOTAL_BUDGET");
        for (Artifact previous : files) require(!Files.isSameFile(file.path, previous.path), "FILE_ALIAS");
        files.add(file); total += file.bytes;
    }

    private static void hash(Artifact file) throws IOException { require(digest(file.path, file.bytes).equals(file.sha256), "FILE_HASH"); }

    private static String digest(Path path, long expectedBytes) throws IOException {
        BasicFileAttributes before = regular(path);
        require(before.size() == expectedBytes && expectedBytes <= MAX_FILE, "FILE_SIZE");
        MessageDigest digest = sha256(); long total = 0;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536];
            for (int read; (read = input.read(buffer)) != -1;) {
                total += read; require(total <= expectedBytes, "FILE_GROWTH"); digest.update(buffer, 0, read);
            }
        }
        BasicFileAttributes after = regular(path);
        require(total == expectedBytes && before.size() == after.size() && Objects.equals(before.fileKey(), after.fileKey())
                && before.creationTime().equals(after.creationTime()) && before.lastModifiedTime().equals(after.lastModifiedTime()), "FILE_DRIFT");
        return HexFormat.of().formatHex(digest.digest());
    }

    private static byte[] capture(Artifact file, int maximum) throws IOException {
        require(file.bytes <= maximum, "CAPTURE_BUDGET"); regular(file.path);
        byte[] bytes;
        try (var input = Files.newInputStream(file.path, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(maximum + 1); }
        require(bytes.length == file.bytes && HexFormat.of().formatHex(sha256().digest(bytes)).equals(file.sha256), "CAPTURE_HASH");
        regular(file.path); return bytes;
    }

    private static Map<String, Object> json(Artifact file, int maximum) throws IOException {
        String contents = utf8(capture(file, maximum)); sensitive(contents);
        try { return object(BoundedJsonParser.parse(contents)); }
        catch (BoundedJsonParser.JsonSyntaxException invalid) { throw new IOException("EXIT_JSON"); }
    }

    private static String utf8(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException invalid) { throw new IOException("EXIT_UTF8"); }
    }

    private static void sensitive(String contents) throws IOException { require(!SENSITIVE.matcher(contents).find(), "SENSITIVE_DATA"); }
    private static Path safe(Path root, String relative) throws IOException {
        require(relative != null && relative.length() <= 1024 && relative.matches("[A-Za-z0-9_./-]+") && !relative.startsWith("/"), "PATH");
        for (String part : relative.split("/", -1)) require(!part.isEmpty() && !part.equals(".") && !part.equals("..") && !part.endsWith("."), "PATH");
        Path path = root.resolve(relative); require(path.startsWith(root), "PATH"); regular(path); return path;
    }

    private static Path directory(Path path) throws IOException {
        require(path != null && path.isAbsolute() && path.equals(path.normalize()), "DIRECTORY");
        for (Path current = path; current != null; current = current.getParent()) {
            var attrs = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            require(attrs.isDirectory() && !attrs.isSymbolicLink() && !attrs.isOther(), "PATH_ALIAS"); canonical(current);
        }
        return path;
    }

    private static BasicFileAttributes regular(Path path) throws IOException {
        require(path != null && path.isAbsolute() && path.equals(path.normalize()), "PATH"); directory(path.getParent());
        var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        require(attrs.isRegularFile() && !attrs.isSymbolicLink() && !attrs.isOther(), "FILE_IDENTITY"); canonical(path); return attrs;
    }

    private static void canonical(Path path) throws IOException {
        Path real = path.toRealPath(); require(path.equals(real) && real.equals(path.toRealPath(LinkOption.NOFOLLOW_LINKS)), "PATH_ALIAS");
    }

    private static MessageDigest sha256() throws IOException {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IOException("EXIT_SHA256"); }
    }
    private static String text(Object value) throws IOException { require(value instanceof String, "TEXT"); return (String) value; }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) throws IOException {
        require(value instanceof Map<?, ?>, "OBJECT"); return (Map<String, Object>) value;
    }
    private static long number(Object value) throws IOException {
        require(value instanceof BigDecimal, "NUMBER");
        try { return ((BigDecimal) value).longValueExact(); }
        catch (ArithmeticException invalid) { throw new IOException("EXIT_NUMBER"); }
    }
    private static void keys(Map<String, Object> value, String... expected) throws IOException { require(value.keySet().equals(Set.of(expected)), "KEYS"); }
    private static void eq(Object actual, Object expected, String code) throws IOException {
        if (expected instanceof Number) require(actual instanceof BigDecimal && ((BigDecimal) actual).compareTo(new BigDecimal(expected.toString())) == 0, code);
        else require(Objects.equals(actual, expected), code);
    }
    private static void require(boolean condition, String code) throws IOException { if (!condition) throw new IOException("EXIT_" + code); }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) throw new IOException("Usage: CompositionExitVerifier repository observation-index exit-index");
        try {
            Path repository;
            try { repository = Path.of(args[0]); }
            catch (java.nio.file.InvalidPathException invalid) { throw new IOException("EXIT_PATH"); }
            System.out.print(CorpusJson.canonicalJson(verify(repository, args[1], args[2]).toTree()));
        }
        catch (IOException invalid) {
            System.out.print(CorpusJson.canonicalJson(Map.of("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_EXIT_CHECK", "status", "BLOCKED",
                    "gameExecutionVerified", false, "installationAllowed", false, "launchContractVerified", false, "error", invalid.getMessage())));
            throw invalid;
        }
    }
}
