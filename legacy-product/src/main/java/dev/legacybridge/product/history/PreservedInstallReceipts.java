package dev.legacybridge.product.history;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.product.controller.PreservedInstallSelection;
import dev.legacybridge.product.controller.PreservedInstallView;
import dev.legacybridge.workflow.BridgeWorkspace;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Separate schema-1 presentation receipts, never an admission, approval, journal or activation oracle. */
public final class PreservedInstallReceipts {
    private static final String PREFIX = "preserved-job-", TYPE = "LEGACYBRIDGE_PRESERVED_JOB";
    private static final Set<String> FIELDS = Set.of("schemaVersion", "reportType", "operationId", "reviewId", "action",
            "admissionId", "admissionSha256", "dataSha256", "approvalId", "state", "transactionId", "planSha256",
            "originalSha256", "candidateSha256", "errorCode", "activation");
    public enum Action { REVIEW_APPLY, REVIEW_RESUME, REVIEW_RESTORE, APPLY, RESUME, RESTORE }
    public record Receipt(UUID operationId, UUID reviewId, Action action, UUID admissionId, String admissionSha256,
            String dataSha256, Optional<UUID> approvalId, PreservedInstallView.State state,
            Optional<UUID> transactionId, Optional<String> planSha256, Optional<String> originalSha256,
            Optional<String> candidateSha256, Optional<String> errorCode) {
        public String activation() { return "NOT_OBSERVED"; }
        public boolean installationAllowed() { return false; }
        public boolean safeToClose() { return false; }
    }
    public enum Status { NOT_READ, COMPLETE, PARTIAL, CANCELLED }
    /** No latest selection: UUID order is display order, not time or transaction order. */
    public record History(Status status, List<Receipt> receipts, Optional<String> diagnostic) {
        public History { receipts = List.copyOf(receipts); }
        public static History notRead() { return new History(Status.NOT_READ, List.of(), Optional.empty()); }
    }

    /** Worker only, no-overwrite publication followed by force. The journal remains authoritative if this write fails. */
    public Path write(Path gameDir, PreservedInstallSelection selection, Action action, UUID approval,
                      PreservedInstallView view) throws IOException {
        var operation = view.operationId().orElseThrow();
        var receipt = new Receipt(operation, view.reviewId().orElseThrow(), action,
                id(selection.admission().getFileName().toString()), selection.admissionSha256(),
                selection.data().expectedManifestSha256().orElseThrow(), Optional.ofNullable(approval), view.state(),
                view.result().flatMap(r -> r.transaction()).map(p -> UUID.fromString(p.getFileName().toString())),
                view.result().flatMap(r -> r.planSha256()),
                view.admission().flatMap(r -> r.inspection()).map(r -> r.originalSha256()),
                view.admission().flatMap(r -> r.inspection()).map(r -> r.candidateSha256()),
                view.error().map(e -> e.code()));
        var tree = tree(receipt);
        parse(tree); // Writer and reader share the exact schema, including nullable failure observations.
        return publish(gameDir, operation, PREFIX, tree);
    }

    /** Shared filesystem transport only; each caller retains its own exact schema and prefix. */
    static Path publish(Path gameDir, UUID operation, String prefix, Map<String, Object> tree) throws IOException {
        BridgeWorkspace w = BridgeWorkspace.inspect(gameDir); w.revalidate();
        Path directory = w.reports().resolve(operation.toString());
        w.revalidate(); Files.createDirectory(directory); checked(w, directory, true);
        Path file = directory.resolve(prefix + operation + ".json");
        var writer = new CanonicalReportWriter();
        // This diagnostic must survive a missing/rejected admission. Its fresh, canonical reports
        // directory and no-overwrite destination are disjoint from all input/transaction paths.
        writer.writeJsonAtomically(file, writer.toJsonTree(tree), false, w.gameDir());
        checked(w, directory, true); checked(w, file, false);
        try (var channel = java.nio.channels.FileChannel.open(file, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
        checked(w, file, false);
        return file;
    }

    /** Bounded direct scan. On first invalid/over-limit entry stop with PARTIAL; never silently skip a bad receipt. */
    public History read(Path gameDir, BooleanSupplier cancelled) {
        var result = read(gameDir, cancelled, PREFIX, PreservedInstallReceipts::parse, Receipt::operationId);
        return new History(result.status(), result.receipts(), result.diagnostic());
    }
    @FunctionalInterface interface Parser<T> { T parse(Map<?, ?> tree) throws IOException; }
    record ReadResult<T>(Status status, List<T> receipts, Optional<String> diagnostic) {
        ReadResult { receipts = List.copyOf(receipts); }
    }
    static <T> ReadResult<T> read(Path gameDir, BooleanSupplier cancelled, String prefix, Parser<T> parser, Function<T, UUID> identity) {
        List<T> receipts = new ArrayList<>(); List<Path> files = new ArrayList<>(); Set<UUID> operations = new HashSet<>();
        long start = System.nanoTime(); int entries = 0, bytes = 0;
        try {
            checkpoint(cancelled, start); BridgeWorkspace w = BridgeWorkspace.inspect(gameDir); w.revalidate();
            try { checked(w, w.reports(), true); }
            catch (NoSuchFileException missing) { w.revalidate(); return new ReadResult<>(Status.COMPLETE, receipts, Optional.empty()); }
            try (var directories = Files.newDirectoryStream(w.reports())) {
                for (Path directory : directories) {
                    checkpoint(cancelled, start); require(++entries <= 1024, "HISTORY_ENTRY_LIMIT");
                    id(directory.getFileName().toString()); checked(w, directory, true);
                    try (var children = Files.newDirectoryStream(directory)) {
                        for (Path file : children) {
                            checkpoint(cancelled, start); require(++entries <= 1024, "HISTORY_ENTRY_LIMIT");
                            String name = file.getFileName().toString(); if (!name.startsWith(prefix)) continue;
                            require(files.size() < 128, "HISTORY_FILE_LIMIT");
                            require(name.endsWith(".json"), "HISTORY_NAME_INVALID");
                            UUID operation = id(name.substring(prefix.length(), name.length() - 5));
                            require(operation.toString().equals(directory.getFileName().toString()), "HISTORY_ID_MISMATCH");
                            var before = checked(w, file, false);
                            for (Path previous : files) {
                                checkpoint(cancelled, start); require(!Files.isSameFile(previous, file), "HISTORY_ALIAS");
                            }
                            require(before.size() <= 65536 && before.size() <= 2097152 - bytes, "HISTORY_BYTE_LIMIT");
                            byte[] payload;
                            try (var channel = Files.newByteChannel(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                                ByteBuffer buffer = ByteBuffer.allocate((int) before.size());
                                while (buffer.hasRemaining()) {
                                    checkpoint(cancelled, start); int count = channel.read(buffer);
                                    require(count >= 0, "HISTORY_CHANGED"); bytes += count;
                                }
                                require(channel.size() == before.size(), "HISTORY_CHANGED"); payload = buffer.array();
                            }
                            var after = checked(w, file, false);
                            require(before.size() == after.size() && Objects.equals(before.fileKey(), after.fileKey())
                                    && before.lastModifiedTime().equals(after.lastModifiedTime()), "HISTORY_CHANGED");
                            String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(payload)).toString();
                            Object parsed = BoundedJsonParser.parse(json);
                            require(parsed instanceof Map<?, ?>, "HISTORY_SCHEMA_INVALID");
                            T receipt = parser.parse((Map<?, ?>) parsed);
                            require(identity.apply(receipt).equals(operation) && operations.add(operation), "HISTORY_ID_MISMATCH");
                            files.add(file); receipts.add(receipt);
                        }
                    }
                }
            }
            checkpoint(cancelled, start); w.revalidate();
            receipts.sort(Comparator.comparing(r -> identity.apply(r).toString()));
            return new ReadResult<>(Status.COMPLETE, receipts, Optional.empty());
        } catch (Exception error) {
            boolean stopped = cancelled.getAsBoolean() || Thread.currentThread().isInterrupted();
            return new ReadResult<>(stopped ? Status.CANCELLED : Status.PARTIAL, stopped ? List.of() : receipts,
                    Optional.of(stopped ? "CANCELLED" : error instanceof Refused ? error.getMessage() : "HISTORY_READ_FAILED"));
        }
    }

    static Map<String, Object> tree(Receipt r) {
        var t = new LinkedHashMap<String, Object>(); t.put("schemaVersion", 1); t.put("reportType", TYPE);
        t.put("operationId", r.operationId().toString()); t.put("reviewId", r.reviewId().toString()); t.put("action", r.action().name());
        t.put("admissionId", r.admissionId().toString()); t.put("admissionSha256", r.admissionSha256()); t.put("dataSha256", r.dataSha256());
        t.put("approvalId", r.approvalId().map(UUID::toString).orElse(null)); t.put("state", r.state().name());
        t.put("transactionId", r.transactionId().map(UUID::toString).orElse(null)); t.put("planSha256", r.planSha256().orElse(null));
        t.put("originalSha256", r.originalSha256().orElse(null)); t.put("candidateSha256", r.candidateSha256().orElse(null));
        t.put("errorCode", r.errorCode().orElse(null)); t.put("activation", "NOT_OBSERVED"); return t;
    }
    static Receipt parse(Map<?, ?> t) throws IOException {
        require(t.keySet().equals(FIELDS) && (t.get("schemaVersion") instanceof Integer || t.get("schemaVersion") instanceof java.math.BigDecimal)
                && t.get("schemaVersion").toString().equals("1") && TYPE.equals(t.get("reportType"))
                && "NOT_OBSERVED".equals(t.get("activation")), "HISTORY_SCHEMA_INVALID");
        try {
            Action action = Action.valueOf(text(t.get("action")));
            var state = PreservedInstallView.State.valueOf(text(t.get("state")));
            require(Set.of(PreservedInstallView.State.REVIEWED, PreservedInstallView.State.BLOCKED,
                    PreservedInstallView.State.PENDING_RESTART, PreservedInstallView.State.RESTORED,
                    PreservedInstallView.State.FAILED).contains(state), "HISTORY_STATE_INVALID");
            Optional<String> error = optional(t.get("errorCode"));
            require(error.isEmpty() || error.orElseThrow().matches("[A-Z][A-Z0-9_]{0,127}"), "HISTORY_ERROR_INVALID");
            boolean review = action.name().startsWith("REVIEW_");
            boolean failed = state == PreservedInstallView.State.BLOCKED || state == PreservedInstallView.State.FAILED;
            require(failed == error.isPresent(), "HISTORY_STATE_INVALID");
            require(review ? failed || state == PreservedInstallView.State.REVIEWED
                    : state != PreservedInstallView.State.REVIEWED && state != PreservedInstallView.State.BLOCKED, "HISTORY_STATE_INVALID");
            require(review || t.get("approvalId") != null, "HISTORY_APPROVAL_MISSING");
            require(failed || t.get("originalSha256") != null && t.get("candidateSha256") != null, "HISTORY_INPUT_MISSING");
            require(failed || review && action == Action.REVIEW_APPLY
                    || t.get("transactionId") != null && t.get("planSha256") != null, "HISTORY_TRANSACTION_MISSING");
            return new Receipt(id(text(t.get("operationId"))), id(text(t.get("reviewId"))), action,
                    id(text(t.get("admissionId"))), hash(t.get("admissionSha256")), hash(t.get("dataSha256")),
                    optionalId(t.get("approvalId")), state, optionalId(t.get("transactionId")), optionalHash(t.get("planSha256")),
                    optionalHash(t.get("originalSha256")), optionalHash(t.get("candidateSha256")), error);
        } catch (IllegalArgumentException bad) { throw new Refused("HISTORY_SCHEMA_INVALID"); }
    }
    private static String text(Object v) throws IOException { require(v instanceof String, "HISTORY_TYPE_INVALID"); return (String) v; }
    private static Optional<String> optional(Object v) throws IOException { return v == null ? Optional.empty() : Optional.of(text(v)); }
    private static Optional<UUID> optionalId(Object v) throws IOException { return v == null ? Optional.empty() : Optional.of(id(text(v))); }
    private static Optional<String> optionalHash(Object v) throws IOException { return v == null ? Optional.empty() : Optional.of(hash(v)); }
    private static String hash(Object v) throws IOException { String s = text(v); require(s.matches("[a-f0-9]{64}"), "HISTORY_HASH_INVALID"); return s; }
    private static UUID id(String s) throws IOException {
        try { UUID id = UUID.fromString(s); require(id.toString().equals(s), "HISTORY_ID_INVALID"); return id; }
        catch (IllegalArgumentException bad) { throw new Refused("HISTORY_ID_INVALID"); }
    }
    private static BasicFileAttributes checked(BridgeWorkspace w, Path path, boolean directory) throws IOException {
        w.revalidate(); require(path.startsWith(w.reports()), "HISTORY_PATH_UNSAFE");
        Path cursor = w.gameDir(); BasicFileAttributes result = null;
        for (Path part : w.gameDir().relativize(path)) {
            cursor = cursor.resolve(part); result = Files.readAttributes(cursor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            require(!result.isSymbolicLink() && cursor.equals(cursor.toRealPath())
                    && (cursor.equals(path) && !directory ? result.isRegularFile() : result.isDirectory()), "HISTORY_PATH_UNSAFE");
        }
        return Objects.requireNonNull(result);
    }
    private static void checkpoint(BooleanSupplier cancelled, long start) throws IOException {
        require(!cancelled.getAsBoolean() && !Thread.currentThread().isInterrupted(), "CANCELLED");
        require(System.nanoTime() - start <= 10_000_000_000L, "HISTORY_TIME_LIMIT");
    }
    private static void require(boolean condition, String code) throws IOException { if (!condition) throw new Refused(code); }
    private static final class Refused extends IOException { private Refused(String code) { super(code); } }
}
