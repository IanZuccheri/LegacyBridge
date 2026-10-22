package dev.legacybridge.product.history;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.config.BoundedJsonParser.JsonSyntaxException;
import dev.legacybridge.product.controller.JobState;
import dev.legacybridge.product.install.TransactionResult;
import dev.legacybridge.workflow.BridgeWorkspace;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import static dev.legacybridge.product.history.JobHistory.*;

/**
 * Worker-only, bounded, read-only schemas 1/2 reader. No JAR is opened and no
 * receipt is migrated, repaired or promoted to approval. The scan is not an OS
 * sandbox or an atomic multi-file snapshot. Concurrent appends may require refresh.
 */
public final class JobHistoryReader {
    public record Limits(int entries, int receipts, int fileBytes, int totalBytes, int issues, long timeoutMillis) {
        public Limits {
            if (entries < 1 || entries > 1024 || receipts < 1 || receipts > 128
                    || fileBytes < 1 || fileBytes > 65536 || totalBytes < 1 || totalBytes > 2097152
                    || issues < 1 || issues > 64 || timeoutMillis < 1 || timeoutMillis > 10000)
                throw new IllegalArgumentException("History limits must be positive and may only tighten the defaults");
        }
    }
    public static final Limits DEFAULT_LIMITS = new Limits(1024, 128, 65536, 2097152, 64, 10000);
    private static final Set<String> V1 = Set.of("schemaVersion", "reportType", "jobId", "state", "input",
            "inputSha256", "artifactSha256", "artifact", "installed", "transaction", "error");
    private static final Set<String> V2;
    static {
        Set<String> fields = new HashSet<>(V1);
        fields.addAll(Set.of("sequence", "event", "installation", "activation"));
        V2 = Set.copyOf(fields);
    }
    private final Limits limits;
    public JobHistoryReader() { this(DEFAULT_LIMITS); }
    public JobHistoryReader(Limits limits) { this.limits = Objects.requireNonNull(limits); }

    /** Caller supplies the trusted installation root and runs this off the render thread. */
    public JobHistory read(Path gameDir, BooleanSupplier cancelled) {
        return new Scan(Objects.requireNonNull(cancelled)).read(gameDir);
    }

    private final class Scan {
        private final BooleanSupplier cancelled;
        private final long started = System.nanoTime();
        private final List<Receipt> receipts = new ArrayList<>();
        private final List<Issue> issues = new ArrayList<>();
        private final Set<Object> fileKeys = new HashSet<>();
        private final List<Path> observedFiles = new ArrayList<>();
        private final List<Path> filesWithoutKey = new ArrayList<>();
        private BridgeWorkspace workspace;
        private int entries, files, omittedIssues;
        private long bytes;
        private Scan(BooleanSupplier cancelled) { this.cancelled = cancelled; }

        private JobHistory read(Path gameDir) {
            Status status = Status.COMPLETE;
            try {
                checkpoint();
                try { workspace = BridgeWorkspace.inspect(gameDir); }
                catch (IOException | IllegalArgumentException | SecurityException error) {
                    throw new Rejected(Code.WORKSPACE_UNSAFE);
                }
                if (present(workspace.reports())) scanDirectories();
                checkpoint();
                workspace.revalidate();
            } catch (Stopped stop) {
                issue(stop.code, "");
                status = stop.code == Code.CANCELLED ? Status.CANCELLED : Status.PARTIAL;
            } catch (Rejected error) {
                issue(error.code, ""); status = Status.FAILED;
            } catch (IOException | DirectoryIteratorException | SecurityException error) {
                issue(Code.READ_FAILED, ""); status = Status.FAILED;
            }
            // Cancellation never publishes an apparently current history or partial selection.
            if (status == Status.CANCELLED) return snapshot(status, List.of());
            List<Job> jobs = group();
            if (status == Status.COMPLETE && !issues.isEmpty()) status = Status.PARTIAL;
            if (status != Status.COMPLETE) jobs = jobs.stream()
                    .map(job -> new Job(job.jobId(), job.ordering(), job.receipts(), Optional.empty())).toList();
            return snapshot(status, jobs);
        }

        private JobHistory snapshot(Status status, List<Job> jobs) {
            return new JobHistory(status, jobs, issues, entries, files, bytes, omittedIssues);
        }
        private void scanDirectories() throws IOException, Rejected {
            checked(workspace.reports(), true);
            try (var directories = Files.newDirectoryStream(workspace.reports())) {
                for (Path directory : directories) {
                    visit();
                    try {
                        uuid(directory.getFileName().toString());
                        checked(directory, true);
                        try (var children = Files.newDirectoryStream(directory)) {
                            for (Path file : children) {
                                visit();
                                String name = file.getFileName().toString();
                                // Other reports/temporary files are not job receipts. Still count entries.
                                if (!name.startsWith("job-")) continue;
                                if (files == limits.receipts()) throw new Stopped(Code.FILE_LIMIT);
                                files++;
                                try {
                                    if (!name.endsWith(".json")) throw new Rejected(Code.INVALID_NAME);
                                    uuid(name.substring(4, name.length() - 5));
                                    receipts.add(parse(file, readBytes(file)));
                                } catch (Rejected error) { issue(error.code, location(file)); }
                                catch (IOException | SecurityException error) { issue(Code.READ_FAILED, location(file)); }
                            }
                        }
                    } catch (Rejected error) { issue(error.code, location(directory)); }
                    catch (IOException | DirectoryIteratorException | SecurityException error) { issue(Code.READ_FAILED, location(directory)); }
                }
            }
        }

        private byte[] readBytes(Path file) throws IOException, Rejected {
            checkpoint();
            BasicFileAttributes before = checked(file, false);
            if (before.fileKey() != null && !fileKeys.add(before.fileKey())) throw new Rejected(Code.ALIASED_RECEIPT);
            // Windows/providers may return null fileKey even for NTFS hard links.
            // At most 128 files: bounded pairwise fallback, never a filesystem-wide search.
            for (Path previous : before.fileKey() == null ? observedFiles : filesWithoutKey) {
                checkpoint();
                if (Files.isSameFile(file, previous)) throw new Rejected(Code.ALIASED_RECEIPT);
            }
            observedFiles.add(file);
            if (before.fileKey() == null) filesWithoutKey.add(file);
            if (before.size() > limits.fileBytes()) throw new Rejected(Code.FILE_TOO_LARGE);
            if (before.size() > limits.totalBytes() - bytes) throw new Stopped(Code.BYTE_LIMIT);
            ByteArrayOutputStream output = new ByteArrayOutputStream((int) before.size());
            try (var channel = Files.newByteChannel(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.allocate(4096);
                while (true) {
                    checkpoint();
                    // Never read even one byte beyond the total I/O budget.
                    int allowance = (int) Math.min(buffer.capacity(), limits.totalBytes() - bytes);
                    if (allowance == 0) {
                        if (channel.position() == before.size() && channel.size() == before.size()) break;
                        throw new Stopped(Code.BYTE_LIMIT);
                    }
                    buffer.clear().limit(allowance);
                    int count = channel.read(buffer);
                    if (count < 0) break;
                    bytes += count;
                    if (output.size() + count > limits.fileBytes()) throw new Rejected(Code.FILE_TOO_LARGE);
                    output.write(buffer.array(), 0, count);
                }
            }
            BasicFileAttributes after = checked(file, false);
            if (!Objects.equals(before.fileKey(), after.fileKey()) || before.size() != after.size()
                    || before.size() != output.size() || !before.lastModifiedTime().equals(after.lastModifiedTime()))
                throw new Rejected(Code.CHANGED_DURING_READ);
            return output.toByteArray();
        }

        private Receipt parse(Path file, byte[] contents) throws Rejected, IOException {
            checkpoint();
            String json;
            try {
                json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(contents)).toString();
            } catch (CharacterCodingException invalid) { throw new Rejected(Code.INVALID_UTF8); }
            Map<?, ?> tree;
            try { tree = object(BoundedJsonParser.parse(json)); }
            catch (JsonSyntaxException invalid) { throw new Rejected(Code.INVALID_JSON); }
            int schema = integer(tree.get("schemaVersion"), 1, Integer.MAX_VALUE);
            if (schema != 1 && schema != 2) throw new Rejected(Code.UNKNOWN_SCHEMA);
            fields(tree, schema == 1 ? V1 : V2);
            equal(tree.get("reportType"), "LEGACYBRIDGE_JOB");
            UUID job = uuid(string(tree.get("jobId"), 36));
            JobState state = enumeration(JobState.class, tree.get("state"));
            String input = string(tree.get("input"), 240); component(input);
            String inputHash = hash(tree.get("inputSha256"), true);
            Optional<String> outputHash = optionalHash(tree.get("artifactSha256"));
            Optional<String> artifact = recordedPath(tree.get("artifact"), "artifact");
            Optional<String> installed = recordedPath(tree.get("installed"), "installed");
            Optional<String> transaction = recordedPath(tree.get("transaction"), "transaction");
            Optional<RecordedError> error = Optional.empty();
            if (tree.get("error") != null) {
                var details = object(tree.get("error")); fields(details, Set.of("code", "message", "remediation"));
                error = Optional.of(new RecordedError(display(string(details.get("code"), 512)),
                        display(string(details.get("message"), 4096)), display(string(details.get("remediation"), 4096))));
            }
            Optional<Integer> sequence = Optional.empty();
            Event event = Event.LEGACY_UNSPECIFIED;
            Optional<Installation> installation = Optional.empty();
            if (schema == 2) {
                sequence = Optional.of(integer(tree.get("sequence"), 1, Integer.MAX_VALUE));
                event = enumeration(Event.class, tree.get("event"));
                if (event == Event.LEGACY_UNSPECIFIED) throw new Rejected(Code.INVALID_RECEIPT);
                equal(tree.get("activation"), "NOT_OBSERVED");
                if (tree.get("installation") != null) {
                    var observation = object(tree.get("installation"));
                    fields(observation, Set.of("operationId", "action", "transaction", "transactionStatus",
                            "entryIndex", "entryState", "transactionSafeToClose"));
                    if (!(observation.get("transactionSafeToClose") instanceof Boolean safe)) throw new Rejected(Code.INVALID_RECEIPT);
                    installation = Optional.of(new Installation(uuid(string(observation.get("operationId"), 36)),
                            enumeration(Action.class, observation.get("action")), recordedPath(observation.get("transaction"), "transaction"),
                            optionalEnum(TransactionResult.Status.class, observation.get("transactionStatus")),
                            observation.get("entryIndex") == null ? Optional.empty()
                                    : Optional.of(integer(observation.get("entryIndex"), 0, 31)),
                            optionalEnum(TransactionResult.EntryState.class, observation.get("entryState")), safe));
                }
                if ((event == Event.INSTALLATION) != installation.isPresent()) throw new Rejected(Code.INVALID_RECEIPT);
            }
            checkpoint();
            return new Receipt(location(file), schema, job, sequence, event, state, input, inputHash,
                    outputHash, artifact, installed, transaction, installation, error);
        }

        /** Validate recorded locations without opening JARs or reading journals. Missing files are legitimate history. */
        private Optional<String> recordedPath(Object value, String kind) throws Rejected, IOException {
            if (value == null) return Optional.empty();
            String text = string(value, 4096);
            Path path;
            try { path = Path.of(text); }
            catch (IllegalArgumentException error) { throw new Rejected(Code.PATH_UNSAFE); }
            if (!path.isAbsolute() || !path.equals(path.normalize()) || !path.startsWith(workspace.gameDir()))
                throw new Rejected(Code.PATH_UNSAFE);
            Path relative = workspace.gameDir().relativize(path);
            for (Path part : relative) component(part.toString());
            boolean valid = switch (kind) {
                case "artifact" -> relative.getNameCount() == 4 && path.startsWith(workspace.staging())
                        && path.getFileName().toString().equals("converted.jar");
                case "installed" -> relative.getNameCount() == 2 && path.startsWith(workspace.mods())
                        && path.getFileName().toString().endsWith(".jar");
                case "transaction" -> relative.getNameCount() == 3 && path.startsWith(workspace.transactions());
                default -> false;
            };
            if (!valid) throw new Rejected(Code.PATH_UNSAFE);
            if (!kind.equals("installed")) uuid(kind.equals("artifact") ? path.getParent().getFileName().toString() : path.getFileName().toString());
            Path cursor = workspace.gameDir();
            for (Path part : relative) {
                checkpoint(); cursor = cursor.resolve(part);
                if (!present(cursor)) break;
                checked(cursor, !cursor.equals(path) || kind.equals("transaction"));
            }
            return Optional.of(relative.toString().replace('\\', '/'));
        }

        private BasicFileAttributes checked(Path path, boolean directory) throws IOException, Rejected {
            workspace.revalidate();
            Path cursor = workspace.gameDir();
            BasicFileAttributes attributes = null;
            for (Path part : workspace.gameDir().relativize(path)) {
                checkpoint(); cursor = cursor.resolve(part);
                attributes = Files.readAttributes(cursor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                boolean wantDirectory = !cursor.equals(path) || directory;
                if (attributes.isSymbolicLink() || (wantDirectory ? !attributes.isDirectory() : !attributes.isRegularFile())
                        || !cursor.equals(cursor.toRealPath())) throw new Rejected(Code.PATH_UNSAFE);
            }
            return Objects.requireNonNull(attributes);
        }
        private boolean present(Path path) throws IOException {
            try { Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); return true; }
            catch (NoSuchFileException absent) { return false; }
        }
        private void visit() {
            checkpoint(); if (entries == limits.entries()) throw new Stopped(Code.ENTRY_LIMIT); entries++;
        }
        private void checkpoint() {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new Stopped(Code.CANCELLED);
            if (System.nanoTime() - started > limits.timeoutMillis() * 1000000) throw new Stopped(Code.TIME_LIMIT);
        }
        private String location(Path path) { return display(workspace.gameDir().relativize(path).toString().replace('\\', '/')); }
        private void issue(Code code, String location) {
            if (issues.size() < limits.issues()) issues.add(new Issue(code, location,
                    "Receipt history: " + code + ". Inspect reports; this does not authorize Apply or prove activation."));
            else omittedIssues++;
        }

        private List<Job> group() {
            Map<UUID, List<Receipt>> groups = new TreeMap<>();
            for (Receipt receipt : receipts) groups.computeIfAbsent(receipt.jobId(), ignored -> new ArrayList<>()).add(receipt);
            List<Job> result = new ArrayList<>();
            for (var entry : groups.entrySet()) {
                var values = entry.getValue();
                values.sort(Comparator.comparing((Receipt r) -> r.sequence().orElse(0)).thenComparing(Receipt::location));
                boolean legacy = values.stream().anyMatch(r -> r.schemaVersion() == 1);
                boolean current = values.stream().anyMatch(r -> r.schemaVersion() == 2);
                Ordering order = legacy ? Ordering.LEGACY_UNORDERED : Ordering.SEQUENCED;
                String location = entry.getKey().toString();
                if (legacy && current) { issue(Code.MIXED_SCHEMAS, location); order = Ordering.AMBIGUOUS; }
                if (values.stream().map(Receipt::input).distinct().count() > 1
                        || values.stream().map(Receipt::inputSha256).distinct().count() > 1
                        || values.stream().flatMap(r -> r.artifactSha256().stream()).distinct().count() > 1
                        || values.stream().flatMap(r -> r.artifact().stream()).distinct().count() > 1) {
                    issue(Code.IDENTITY_CONFLICT, location); order = Ordering.AMBIGUOUS;
                }
                Set<Integer> sequences = new HashSet<>(); int expected = 1;
                for (Receipt value : values) if (value.sequence().isPresent()) {
                    int sequence = value.sequence().orElseThrow();
                    if (!sequences.add(sequence)) { issue(Code.DUPLICATE_SEQUENCE, location); order = Ordering.AMBIGUOUS; }
                    else if (sequence != expected) { issue(Code.SEQUENCE_GAP, location); order = Ordering.AMBIGUOUS; }
                    expected = sequence == Integer.MAX_VALUE ? sequence : sequence + 1;
                }
                result.add(new Job(entry.getKey(), order, values, order == Ordering.SEQUENCED
                        ? Optional.of(values.get(values.size() - 1)) : Optional.empty()));
            }
            return result;
        }
    }

    private static Map<?, ?> object(Object value) throws Rejected {
        if (!(value instanceof Map<?, ?> map)) throw new Rejected(Code.INVALID_RECEIPT);
        return map;
    }
    private static void fields(Map<?, ?> map, Set<String> fields) throws Rejected {
        if (!map.keySet().equals(fields)) throw new Rejected(Code.INVALID_RECEIPT);
    }
    private static String string(Object value, int max) throws Rejected {
        if (!(value instanceof String text) || text.length() > max) throw new Rejected(Code.INVALID_RECEIPT);
        for (int i = 0; i < text.length(); i++) if (Character.isSurrogate(text.charAt(i))) {
            if (!Character.isHighSurrogate(text.charAt(i)) || i + 1 == text.length()
                    || !Character.isLowSurrogate(text.charAt(++i))) throw new Rejected(Code.INVALID_RECEIPT);
        }
        return text;
    }
    private static String display(String text) {
        StringBuilder result = new StringBuilder();
        text.codePoints().limit(512).forEach(c -> {
            if (!Character.isISOControl(c) && Character.getType(c) != Character.FORMAT && c != 0xA7) result.appendCodePoint(c);
        });
        return result.toString();
    }
    private static void component(String text) throws Rejected {
        if (text.isBlank() || text.equals(".") || text.equals("..") || text.endsWith(".") || text.endsWith(" ")
                || text.matches(".*[<>:\"/\\\\|?*].*") || !display(text).equals(text)
                || text.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³])(\\..*)?")) throw new Rejected(Code.PATH_UNSAFE);
    }
    private static UUID uuid(String text) throws Rejected {
        try {
            UUID value = UUID.fromString(text);
            if (!value.toString().equals(text)) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException invalid) { throw new Rejected(Code.INVALID_NAME); }
    }
    private static int integer(Object value, int min, int max) throws Rejected {
        try {
            if (!(value instanceof BigDecimal number)) throw new ArithmeticException();
            int result = number.intValueExact();
            if (result < min || result > max) throw new ArithmeticException();
            return result;
        } catch (ArithmeticException invalid) { throw new Rejected(Code.INVALID_RECEIPT); }
    }
    private static String hash(Object value, boolean unknown) throws Rejected {
        String text = string(value, 64);
        if (!text.matches("[0-9a-f]{64}") && !(unknown && text.equals("UNKNOWN"))) throw new Rejected(Code.INVALID_RECEIPT);
        return text;
    }
    private static Optional<String> optionalHash(Object value) throws Rejected {
        return value == null ? Optional.empty() : Optional.of(hash(value, false));
    }
    private static void equal(Object value, String expected) throws Rejected {
        if (!expected.equals(value)) throw new Rejected(Code.INVALID_RECEIPT);
    }
    private static <T extends Enum<T>> T enumeration(Class<T> type, Object value) throws Rejected {
        try { return Enum.valueOf(type, string(value, 64)); }
        catch (IllegalArgumentException invalid) { throw new Rejected(Code.INVALID_RECEIPT); }
    }
    private static <T extends Enum<T>> Optional<T> optionalEnum(Class<T> type, Object value) throws Rejected {
        return value == null ? Optional.empty() : Optional.of(enumeration(type, value));
    }
    private static final class Rejected extends Exception {
        final Code code;
        Rejected(Code code) { this.code = code; }
    }
    private static final class Stopped extends RuntimeException {
        final Code code;
        Stopped(Code code) { this.code = code; }
    }
}
