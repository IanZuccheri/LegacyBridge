package dev.legacybridge.corpus;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Internal developer process mechanics, not a native launcher, permission boundary or public API.
 * No CLI, shell, raw-log persistence or gameplay assertion. Only its own Process is stopped.
 * A supplied plan digest is recorded, not interpreted as permission or verified plan provenance.
 * JVM heap bounds do not bound OS/native memory; filesystem/start/close calls are not real-time APIs.
 * Unknown descendants are never killed; a surviving child/reader keeps the single slot quarantined.
 */
final class CompositionProcessCollector {
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    private static final long MAX_FILE = 128L << 20, MAX_INPUTS = 512L << 20, MAX_HEAP = 2L << 30;
    private static final Pattern HASH = Pattern.compile("[a-f0-9]{64}");
    private static final Pattern SECRET = Pattern.compile("(?i)(?:access[_-]?token|refresh[_-]?token|client[_-]?token|authorization\\s*[:=]|"
            + "bearer\\s+[A-Za-z0-9._-]+|--(?:username|uuid|clientid|xuid|userproperties|password|token|auth(?:entication)?|secret)\\b|"
            + "(?:password|passwd|secret)\\s*[:=]|-D(?:auth|token|password)[.=]|Picked up (?:JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS))");
    private static final Set<String> PROPERTIES = Set.of("user.home", "java.io.tmpdir", "legacybridge.compositionProbe.run",
            "java.net.preferIPv6Addresses", "ignoreList", "libraryDirectory", "legacyClassPath");

    enum State { PROCESS_COLLECTED_ONLY, FAILED, CANCELLED, TIMED_OUT, REJECTED }
    enum Code { BUSY, INVALID_LAUNCH, INVALID_LIMITS, INVALID_ARGUMENTS, INPUT_REJECTED, INPUT_DRIFT, START_FAILED,
        STDIN_CLOSE_FAILED, STDOUT_IO, STDERR_IO, OUTPUT_LIMIT, INVALID_UTF8, SENSITIVE_OUTPUT, CANCELLED,
        CANCELLATION_CALLBACK_FAILED, COMPLETION_CALLBACK_FAILED, TIMED_OUT, INTERRUPTED, NONZERO_EXIT, INCOMPLETE_STREAMS, DESTROY_FAILED,
        OWNED_PROCESS_STILL_ALIVE, READERS_STILL_ALIVE, STREAM_CLOSE_FAILED, ENVIRONMENT_REJECTED }
    record Input(Path path, String sha256, long bytes) { }
    record Limits(long timeoutMillis, long graceMillis, int streamBytes) {
        static Limits defaults() { return new Limits(240_000, 60_000, 8 << 20); }
    }
    record Launch(Path cwd, List<String> arguments, String planSha256, List<Input> inputs, Limits limits) {
        Launch { arguments = List.copyOf(arguments); inputs = List.copyOf(inputs); }
        @Override public String toString() { return "CompositionProcessLaunch[arguments and environment omitted]"; }
    }

    static final class Result {
        private final State status;
        private final List<Code> errors;
        private final String planSha256, commandSha256;
        private final long elapsedMillis, pid;
        private final Integer exitCode;
        private final boolean started, processExited, stdoutComplete, stderrComplete, terminationRequested, forcedTermination;
        private final byte[] stdout, stderr;
        private Result(State status, Set<Code> errors, String plan, String command, long elapsed, Process process,
                       boolean outComplete, boolean errComplete, boolean terminated, boolean forced, byte[] out, byte[] err) {
            this.status = status; this.errors = List.copyOf(errors); this.planSha256 = plan; this.commandSha256 = command;
            this.elapsedMillis = elapsed; this.started = process != null; this.pid = started ? process.pid() : -1;
            this.processExited = started && !process.isAlive(); this.exitCode = processExited ? process.exitValue() : null;
            this.stdoutComplete = outComplete; this.stderrComplete = errComplete; this.terminationRequested = terminated; this.forcedTermination = forced;
            this.stdout = out == null ? null : out.clone(); this.stderr = err == null ? null : err.clone();
        }
        State status() { return status; }
        List<Code> errors() { return errors; }
        String planSha256() { return planSha256; }
        long pid() { return pid; }
        boolean processExited() { return processExited; }
        boolean stdoutComplete() { return stdoutComplete; }
        boolean stderrComplete() { return stderrComplete; }
        Integer exitCode() { return exitCode; }
        Optional<byte[]> stdout() { return stdout == null ? Optional.empty() : Optional.of(stdout.clone()); }
        Optional<byte[]> stderr() { return stderr == null ? Optional.empty() : Optional.of(stderr.clone()); }
        Map<String, Object> toTree() {
            Map<String, Object> tree = new LinkedHashMap<>();
            tree.put("schemaVersion", 1); tree.put("reportType", "LEGACY_COMPOSITION_PROCESS_COLLECTION"); tree.put("status", status.name());
            tree.put("planSha256", planSha256); tree.put("commandSha256", commandSha256); tree.put("elapsedMillis", elapsedMillis);
            tree.put("started", started); tree.put("pid", pid); tree.put("processExited", processExited); tree.put("exitCode", exitCode);
            tree.put("stdoutComplete", stdoutComplete); tree.put("stderrComplete", stderrComplete);
            tree.put("terminationRequested", terminationRequested); tree.put("forcedTermination", forcedTermination);
            tree.put("stdoutSha256", stdout == null ? null : hash(stdout)); tree.put("stderrSha256", stderr == null ? null : hash(stderr));
            tree.put("errors", errors.stream().map(Enum::name).toList());
            tree.put("gameExecutionVerified", false); tree.put("nativeBehaviorVerified", false);
            tree.put("installationAllowed", false); tree.put("executionAllowed", false); tree.put("launchContractVerified", false);
            return java.util.Collections.unmodifiableMap(tree);
        }
        @Override public String toString() { return "CompositionProcessResult[" + status + ", errors=" + errors + "]"; }
    }
    private CompositionProcessCollector() { }

    static Result collect(Launch launch, BooleanSupplier cancelled) {
        return collect(launch, cancelled, () -> { });
    }

    /** Package-private deterministic test seam; runs only after successful cleanup and input revalidation. */
    static Result collect(Launch launch, BooleanSupplier cancelled, Runnable beforeCompletionCheck) {
        long start = System.nanoTime(); Set<Code> errors = new LinkedHashSet<>();
        String plan = launch != null && launch.planSha256 != null && HASH.matcher(launch.planSha256).matches() ? launch.planSha256 : null;
        if (!ACTIVE.compareAndSet(false, true)) {
            errors.add(Code.BUSY); return result(State.REJECTED, errors, plan, null, start, null, null, null, false, false, null, null);
        }
        Process process = null; Capture out = null, err = null; Thread outThread = null, errThread = null;
        boolean interrupted = false, terminated = false, forced = false, release = true;
        String command = null; State state = State.REJECTED;
        byte[] stdout = null, stderr = null;
        try {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            require(launch != null && cancelled != null && beforeCompletionCheck != null && plan != null, Code.INVALID_LAUNCH);
            validateLimits(launch.limits);
            validateInputs(launch);
            validateArguments(launch);
            command = hash(CorpusJson.canonicalJson(Map.of("arguments", launch.arguments)).getBytes(StandardCharsets.UTF_8));
            if (cancellation(cancelled)) { errors.add(Code.CANCELLED); state = State.CANCELLED; }
            else {
                ProcessBuilder builder = new ProcessBuilder(launch.arguments).directory(launch.cwd.toFile());
                builder.redirectInput(ProcessBuilder.Redirect.PIPE).redirectOutput(ProcessBuilder.Redirect.PIPE).redirectError(ProcessBuilder.Redirect.PIPE);
                configureEnvironment(builder, launch.cwd);
                try { process = builder.start(); } catch (IOException | RuntimeException invalid) { throw new Rejected(Code.START_FAILED); }
                state = State.FAILED;
                out = new Capture(process.getInputStream(), launch.limits.streamBytes, Code.STDOUT_IO);
                err = new Capture(process.getErrorStream(), launch.limits.streamBytes, Code.STDERR_IO);
                outThread = thread(out, "legacybridge-composition-stdout"); errThread = thread(err, "legacybridge-composition-stderr");
                try { process.getOutputStream().close(); } catch (IOException invalid) { errors.add(Code.STDIN_CLOSE_FAILED); }
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(launch.limits.timeoutMillis);
                while (errors.isEmpty() && (process.isAlive() || !out.done() || !err.done())) {
                    if (out.failure != null) errors.add(out.failure); if (err.failure != null) errors.add(err.failure);
                    if (cancellation(cancelled)) { errors.add(Code.CANCELLED); state = State.CANCELLED; break; }
                    if (System.nanoTime() >= deadline) { errors.add(Code.TIMED_OUT); state = State.TIMED_OUT; break; }
                    if (process.isAlive()) process.waitFor(20, TimeUnit.MILLISECONDS);
                    else if (!out.done()) out.finished.await(20, TimeUnit.MILLISECONDS);
                    else err.finished.await(20, TimeUnit.MILLISECONDS);
                }
                if (out.failure != null) errors.add(out.failure); if (err.failure != null) errors.add(err.failure);
                if (errors.isEmpty() && process.exitValue() != 0) errors.add(Code.NONZERO_EXIT);
                if (errors.isEmpty()) {
                    stdout = out.snapshot(); stderr = err.snapshot();
                    validateOutput(stdout); validateOutput(stderr);
                    if (cancellation(cancelled)) { errors.add(Code.CANCELLED); state = State.CANCELLED; }
                    else if (errors.isEmpty()) state = State.PROCESS_COLLECTED_ONLY;
                }
            }
        } catch (Rejected invalid) { errors.add(invalid.code); if (process != null) state = State.FAILED; }
        catch (InterruptedException cancelledThread) { interrupted = true; errors.add(Code.INTERRUPTED); state = State.CANCELLED; }
        catch (IOException | RuntimeException unexpected) { errors.add(Code.INVALID_LAUNCH); if (process != null) state = State.FAILED; }
        finally {
            // This control block never enumerates/kills unrelated processes or descendants.
            if (process != null && (process.isAlive() || out == null || err == null || !out.done() || !err.done()
                    || (outThread != null && outThread.isAlive()) || (errThread != null && errThread.isAlive()))) {
                long grace = launch.limits.graceMillis, deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(grace);
                if (out != null) out.discard(); if (err != null) err.discard();
                if (process.isAlive()) {
                    terminated = true;
                    try { forced = !process.supportsNormalTermination(); process.destroy(); }
                    catch (RuntimeException invalid) { errors.add(Code.DESTROY_FAILED); }
                    try { process.waitFor(Math.max(1, grace / 2), TimeUnit.MILLISECONDS); }
                    catch (InterruptedException invalid) { interrupted = true; }
                    if (process.isAlive()) {
                        forced = true;
                        try { process.destroyForcibly(); } catch (RuntimeException invalid) { errors.add(Code.DESTROY_FAILED); }
                        interrupted |= awaitProcess(process, deadline);
                    }
                }
                interrupted |= awaitReader(outThread, deadline); interrupted |= awaitReader(errThread, deadline);
                if (process.isAlive()) { errors.add(Code.OWNED_PROCESS_STILL_ALIVE); release = false; }
                if ((outThread != null && outThread.isAlive()) || (errThread != null && errThread.isAlive())) {
                    errors.add(Code.READERS_STILL_ALIVE); release = false;
                }
                if (!errors.isEmpty() && state == State.PROCESS_COLLECTED_ONLY) state = State.FAILED;
            }
            if (process != null && !process.isAlive()) {
                close(process.getOutputStream(), errors);
                // Closing a pipe synchronously while another thread remains in read is not a bounded API.
                // Retain/quarantine uncertainty instead of blocking cleanup or killing unowned descendants.
                if (outThread == null || !outThread.isAlive()) close(process.getInputStream(), errors);
                if (errThread == null || !errThread.isAlive()) close(process.getErrorStream(), errors);
                try { validateInputs(launch); } catch (Rejected drift) { errors.add(Code.INPUT_DRIFT); }
            }
            if (out != null && !out.eof) errors.add(Code.INCOMPLETE_STREAMS);
            if (err != null && !err.eof) errors.add(Code.INCOMPLETE_STREAMS);
            if (out != null) out.discard(); if (err != null) err.discard();
            // Revalidation can be lengthy: cancellation before it is not permission to publish afterward.
            // Hold the slot through this cooperative completion barrier. Cancellation arriving after the
            // final consultation cannot retroactively retract the returned immutable result.
            if (state == State.PROCESS_COLLECTED_ONLY && errors.isEmpty()) {
                try { beforeCompletionCheck.run(); }
                catch (RuntimeException invalid) { errors.add(Code.COMPLETION_CALLBACK_FAILED); }
                if (errors.isEmpty()) {
                    try { if (cancellation(cancelled)) { errors.add(Code.CANCELLED); state = State.CANCELLED; } }
                    catch (Rejected invalid) { errors.add(invalid.code); }
                }
            }
            interrupted |= Thread.currentThread().isInterrupted();
            if (interrupted) { errors.add(Code.INTERRUPTED); if (state == State.PROCESS_COLLECTED_ONLY) state = State.CANCELLED; }
            if (state == State.PROCESS_COLLECTED_ONLY && !errors.isEmpty()) state = State.FAILED;
            if (release) ACTIVE.set(false);
            if (interrupted) Thread.currentThread().interrupt();
        }
        Result result = result(state, errors, plan, command, start, process, out, err, terminated, forced,
                state == State.PROCESS_COLLECTED_ONLY ? stdout : null, state == State.PROCESS_COLLECTED_ONLY ? stderr : null);
        if (stdout != null) Arrays.fill(stdout, (byte) 0); if (stderr != null) Arrays.fill(stderr, (byte) 0);
        return result;
    }

    private static Result result(State state, Set<Code> errors, String plan, String command, long start, Process process, Capture out, Capture err,
                                 boolean terminated, boolean forced, byte[] stdout, byte[] stderr) {
        return new Result(state, errors, plan, command, Math.max(1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)), process,
                out != null && out.eof, err != null && err.eof, terminated, forced, stdout, stderr);
    }
    private static boolean cancellation(BooleanSupplier cancelled) throws Rejected {
        try { return cancelled.getAsBoolean(); } catch (RuntimeException invalid) { throw new Rejected(Code.CANCELLATION_CALLBACK_FAILED); }
    }
    private static Thread thread(Runnable reader, String name) { Thread thread = new Thread(reader, name); thread.setDaemon(true); thread.start(); return thread; }
    private static boolean awaitProcess(Process process, long deadline) {
        boolean interrupted = false;
        while (process.isAlive() && System.nanoTime() < deadline) {
            try { process.waitFor(20, TimeUnit.MILLISECONDS); } catch (InterruptedException ignoredForCleanup) { interrupted = true; }
        }
        return interrupted;
    }
    private static boolean awaitReader(Thread reader, long deadline) {
        boolean interrupted = false;
        while (reader != null && reader.isAlive() && System.nanoTime() < deadline) {
            try { reader.join(20); } catch (InterruptedException ignoredForCleanup) { interrupted = true; }
        }
        return interrupted;
    }
    private static void close(java.io.Closeable stream, Set<Code> errors) { try { stream.close(); } catch (IOException invalid) { errors.add(Code.STREAM_CLOSE_FAILED); } }

    private static final class Capture implements Runnable {
        final InputStream stream; final byte[] bytes; final Code ioCode; final CountDownLatch finished = new CountDownLatch(1);
        volatile Code failure; volatile boolean eof; private int count; private boolean discard;
        Capture(InputStream stream, int limit, Code ioCode) { this.stream = stream; this.bytes = new byte[limit]; this.ioCode = ioCode; }
        @Override public void run() {
            byte[] chunk = new byte[8192];
            try {
                for (int read; (read = stream.read(chunk)) != -1;) {
                    synchronized (this) {
                        if (discard) continue;
                        if (read > bytes.length - count) { failure = Code.OUTPUT_LIMIT; return; }
                        System.arraycopy(chunk, 0, bytes, count, read); count += read;
                    }
                }
                eof = true;
            } catch (IOException invalid) { failure = ioCode; }
            finally { Arrays.fill(chunk, (byte) 0); finished.countDown(); }
        }
        boolean done() { return finished.getCount() == 0; }
        synchronized byte[] snapshot() { return Arrays.copyOf(bytes, count); }
        synchronized void discard() { discard = true; Arrays.fill(bytes, (byte) 0); }
    }

    private static void validateLimits(Limits limits) throws Rejected {
        require(limits != null && limits.timeoutMillis >= 1 && limits.timeoutMillis <= 240_000 && limits.graceMillis >= 1
                && limits.graceMillis <= 60_000 && limits.streamBytes >= 1 && limits.streamBytes <= (8 << 20), Code.INVALID_LIMITS);
    }
    private static void validateInputs(Launch launch) throws Rejected {
        try {
            directory(launch.cwd); directory(launch.cwd.resolve("tmp")); directory(launch.cwd.resolve("empty-user-home"));
            directory(launch.cwd.resolve("empty-user-home/absent-gradle"));
            require(!launch.inputs.isEmpty() && launch.inputs.size() <= 512, Code.INPUT_REJECTED);
            long total = 0; List<Path> seen = new ArrayList<>();
            for (Input input : launch.inputs) {
                require(input != null && input.sha256 != null && HASH.matcher(input.sha256).matches()
                        && input.bytes > 0 && input.bytes <= MAX_FILE && (total += input.bytes) <= MAX_INPUTS, Code.INPUT_REJECTED);
                require(input.sha256.equals(hashFile(input.path, input.bytes)), Code.INPUT_REJECTED);
                for (Path previous : seen) require(!Files.isSameFile(previous, input.path), Code.INPUT_REJECTED); seen.add(input.path);
            }
        } catch (IOException | RuntimeException invalid) { throw new Rejected(Code.INPUT_REJECTED); }
    }
    private static void validateArguments(Launch launch) throws Rejected {
        try {
            List<String> args = launch.arguments; require(args.size() >= 4 && args.size() <= 64, Code.INVALID_ARGUMENTS);
            int total = 0;
            for (String arg : args) require(arg != null && !arg.isEmpty() && arg.length() <= 16384 && (total += arg.length() + 3) <= 32760
                    && arg.indexOf('\0') < 0 && arg.indexOf('\n') < 0 && arg.indexOf('\r') < 0 && !arg.contains("\"")
                    && !arg.startsWith("@") && !SECRET.matcher(arg).find(), Code.INVALID_ARGUMENTS);
            Path executable = Path.of(args.get(0)); regular(executable);
            require(executable.getFileName().toString().equals(isWindows() ? "java.exe" : "java") && selected(launch, executable), Code.INVALID_ARGUMENTS);
            long heap = -1, initialHeap = -1; boolean main = false, perfDataDisabled = false; Set<String> properties = new LinkedHashSet<>();
            for (int i = 1; i < args.size(); i++) {
                String arg = args.get(i);
                if (main) {
                    require(!arg.startsWith("-D") && !arg.startsWith("-X") && !Set.of("--world", "--universe", "--gameDir").contains(arg)
                            && !arg.startsWith("--world=") && !arg.startsWith("--universe=") && !arg.startsWith("--gameDir="), Code.INVALID_ARGUMENTS);
                } else if (arg.startsWith("-Xmx")) { require(heap < 0, Code.INVALID_ARGUMENTS); heap = heap(arg.substring(4)); }
                else if (arg.startsWith("-Xms")) { require(initialHeap < 0, Code.INVALID_ARGUMENTS); initialHeap = heap(arg.substring(4)); }
                // Exact opt-in used by the closed inert session: suppress HotSpot's hsperfdata directory.
                // This is not a general -XX allowlist and does not change native planner arguments.
                else if (arg.equals("-XX:-UsePerfData")) { require(!perfDataDisabled, Code.INVALID_ARGUMENTS); perfDataDisabled = true; }
                else if (arg.startsWith("-D")) {
                    int equals = arg.indexOf('='); require(equals > 2, Code.INVALID_ARGUMENTS); String key = arg.substring(2, equals), value = arg.substring(equals + 1);
                    require(PROPERTIES.contains(key) && properties.add(key), Code.INVALID_ARGUMENTS);
                    if (key.equals("user.home")) require(value.equals(launch.cwd.resolve("empty-user-home").toString()), Code.INVALID_ARGUMENTS);
                    else if (key.equals("java.io.tmpdir")) require(value.equals(launch.cwd.resolve("tmp").toString()), Code.INVALID_ARGUMENTS);
                    else if (key.equals("legacyClassPath")) classPath(launch, value);
                    else if (key.equals("libraryDirectory")) require(value.equals("libraries"), Code.INVALID_ARGUMENTS);
                } else if (Set.of("-cp", "-classpath", "--class-path", "-p", "--module-path").contains(arg)) {
                    require(++i < args.size(), Code.INVALID_ARGUMENTS); classPath(launch, args.get(i));
                } else if (Set.of("--add-modules", "--add-opens", "--add-exports").contains(arg)) {
                    require(++i < args.size() && args.get(i).matches("[A-Za-z0-9_./=,:-]+"), Code.INVALID_ARGUMENTS);
                } else if (arg.equals("-jar")) {
                    require(++i < args.size(), Code.INVALID_ARGUMENTS); boundJar(launch, args.get(i)); main = true;
                } else { require(arg.matches("[A-Za-z_$][A-Za-z0-9_.$]*"), Code.INVALID_ARGUMENTS); main = true; }
            }
            require(main && heap > 0 && heap <= MAX_HEAP && initialHeap <= heap
                    && properties.containsAll(Set.of("user.home", "java.io.tmpdir")), Code.INVALID_ARGUMENTS);
        } catch (IOException | RuntimeException invalid) { throw new Rejected(Code.INVALID_ARGUMENTS); }
    }
    private static long heap(String value) throws Rejected {
        require(value.matches("[1-9][0-9]{0,10}[kKmMgG]"), Code.INVALID_ARGUMENTS);
        long amount = Long.parseLong(value.substring(0, value.length() - 1)); char unit = Character.toLowerCase(value.charAt(value.length() - 1));
        return Math.multiplyExact(amount, unit == 'g' ? 1L << 30 : unit == 'm' ? 1L << 20 : 1L << 10);
    }
    private static void classPath(Launch launch, String value) throws IOException, Rejected {
        String[] paths = value.split(Pattern.quote(File.pathSeparator), -1); require(paths.length >= 1 && paths.length <= 256, Code.INVALID_ARGUMENTS);
        for (String path : paths) boundJar(launch, path);
    }
    private static void boundJar(Launch launch, String value) throws IOException, Rejected {
        Path path = Path.of(value); if (!path.isAbsolute()) path = launch.cwd.resolve(path);
        regular(path); require(path.startsWith(launch.cwd) && path.getFileName().toString().endsWith(".jar") && selected(launch, path), Code.INVALID_ARGUMENTS);
    }
    private static boolean selected(Launch launch, Path path) { return launch.inputs.stream().anyMatch(input -> path.equals(input.path)); }
    private static void configureEnvironment(ProcessBuilder builder, Path cwd) throws Rejected {
        try {
            Map<String, String> environment = builder.environment(); environment.clear();
            if (isWindows()) {
                String windows = System.getenv("SystemRoot"); require(windows != null, Code.ENVIRONMENT_REJECTED);
                Path system = directory(Path.of(windows)); environment.put("SystemRoot", system.toString()); environment.put("windir", system.toString());
            }
            environment.put("TEMP", cwd.resolve("tmp").toString()); environment.put("TMP", cwd.resolve("tmp").toString());
            environment.put("HOME", cwd.resolve("empty-user-home").toString()); environment.put("USERPROFILE", cwd.resolve("empty-user-home").toString());
            environment.put("GRADLE_USER_HOME", cwd.resolve("empty-user-home/absent-gradle").toString());
        } catch (IOException | RuntimeException invalid) { throw new Rejected(Code.ENVIRONMENT_REJECTED); }
    }
    private static void validateOutput(byte[] bytes) throws Rejected {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            require(!SECRET.matcher(text).find(), Code.SENSITIVE_OUTPUT);
        } catch (java.nio.charset.CharacterCodingException invalid) { throw new Rejected(Code.INVALID_UTF8); }
    }
    private static boolean isWindows() { return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows"); }
    private static Path directory(Path path) throws IOException {
        if (path == null || !path.isAbsolute() || !path.equals(path.normalize())) throw new IOException();
        for (Path current = path; current != null; current = current.getParent()) {
            var attrs = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isDirectory() || attrs.isSymbolicLink() || attrs.isOther() || !current.equals(current.toRealPath())
                    || !current.toRealPath().equals(current.toRealPath(LinkOption.NOFOLLOW_LINKS))) throw new IOException();
        }
        return path;
    }
    private static BasicFileAttributes regular(Path path) throws IOException {
        if (path == null || !path.isAbsolute() || !path.equals(path.normalize())) throw new IOException(); directory(path.getParent());
        var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isRegularFile() || attrs.isSymbolicLink() || attrs.isOther() || !path.equals(path.toRealPath())
                || !path.toRealPath().equals(path.toRealPath(LinkOption.NOFOLLOW_LINKS))) throw new IOException(); return attrs;
    }
    private static String hashFile(Path path, long size) throws IOException {
        BasicFileAttributes before = regular(path); if (before.size() != size || size > MAX_FILE) throw new IOException();
        MessageDigest digest = digest(); long total = 0;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = new byte[65536]; for (int n; (n = input.read(bytes)) != -1;) { total += n; if (total > size) throw new IOException(); digest.update(bytes, 0, n); }
        }
        BasicFileAttributes after = regular(path);
        if (total != size || before.size() != after.size() || !Objects.equals(before.fileKey(), after.fileKey())
                || !before.creationTime().equals(after.creationTime()) || !before.lastModifiedTime().equals(after.lastModifiedTime())) throw new IOException();
        return HexFormat.of().formatHex(digest.digest());
    }
    private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(); } }
    private static String hash(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    private static void require(boolean condition, Code code) throws Rejected { if (!condition) throw new Rejected(code); }
    private static final class Rejected extends Exception { final Code code; Rejected(Code code) { super(code.name()); this.code = code; } }
}
