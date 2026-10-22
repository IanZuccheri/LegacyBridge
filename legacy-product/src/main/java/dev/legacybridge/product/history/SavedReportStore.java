package dev.legacybridge.product.history;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.EngineError;
import dev.legacybridge.converter.service.EngineOutcome;
import dev.legacybridge.workflow.BridgeWorkspace;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Worker-only report index/snapshot. No JAR, OS opener, path search, network or installation. */
public final class SavedReportStore {
    public static final int REPORT_BYTES = 1 << 20;
    private static final int INDEX_BYTES = 16384, RECEIPT_BYTES = 65536;
    private static final Set<String> INDEX_FIELDS = Set.of("schemaVersion", "reportType", "jobId", "inputSha256", "reports");

    /** New jobs only. Existing indexes are never overwritten, migrated or retroactively invented. */
    public void writeIndex(Path gameDir, UUID job, String inputSha, Path directory,
                           Map<SavedReport.Kind, Path> reports, Path original) throws IOException {
        require(inputSha.matches("[a-f0-9]{64}"), "REPORT_IDENTITY_MISMATCH");
        require(!reports.isEmpty() && reports.size() <= 3, "REPORT_INDEX_INVALID");
        var access = new Access(gameDir, () -> false);
        access.directory(directory);
        var rows = new ArrayList<Map<String, Object>>();
        var snapshots = new LinkedHashMap<Path, String>();
        for (var kind : SavedReport.Kind.values()) {
            Path path = reports.get(kind);
            if (path == null) continue;
            String relative = access.relative(path, kind.fileName());
            byte[] content = access.read(path, REPORT_BYTES);
            String sha = hash(content); snapshots.put(path, sha);
            rows.add(Map.of("kind", kind.name(), "path", relative, "sha256", sha));
        }
        var index = new TreeMap<String, Object>();
        index.put("schemaVersion", 1); index.put("reportType", "LEGACYBRIDGE_SAVED_REPORTS");
        index.put("jobId", job.toString()); index.put("inputSha256", inputSha); index.put("reports", rows);
        for (var entry : snapshots.entrySet()) require(hash(access.read(entry.getKey(), REPORT_BYTES)).equals(entry.getValue()), "REPORT_CHANGED");
        access.directory(directory);
        var writer = new CanonicalReportWriter();
        writer.writeJsonAtomically(directory.resolve("reports-" + job + ".json"), writer.toJsonTree(index), false, original);
    }

    /** Revalidates only explicit index references. Legacy receipts without an index remain readable as receipts. */
    public EngineOutcome<SavedReport> read(Path gameDir, JobHistory.Receipt receipt, SavedReport.Kind kind,
                                         BooleanSupplier cancelled) {
        try {
            var access = new Access(gameDir, cancelled);
            String[] location = receipt.location().split("/", -1);
            require(location.length == 4 && location[0].equals("legacybridge") && location[1].equals("reports"), "REPORT_PATH_UNSAFE");
            uuid(location[2]);
            require(location[3].matches("job-[a-f0-9-]{36}\\.json"), "REPORT_PATH_UNSAFE");
            uuid(location[3].substring(4, 40));
            Path directory = access.workspace.reports().resolve(location[2]);
            Path receiptPath = directory.resolve(location[3]);
            byte[] receiptBytes = access.read(receiptPath, RECEIPT_BYTES);
            var receiptTree = object(parse(receiptBytes));
            require("LEGACYBRIDGE_JOB".equals(receiptTree.get("reportType"))
                    && receipt.jobId().toString().equals(receiptTree.get("jobId"))
                    && receipt.inputSha256().equals(receiptTree.get("inputSha256")), "REPORT_IDENTITY_MISMATCH");
            Path indexPath = directory.resolve("reports-" + receipt.jobId() + ".json");
            byte[] indexBytes;
            try { indexBytes = access.read(indexPath, INDEX_BYTES); }
            catch (NoSuchFileException missing) { throw new Refused("REPORT_INDEX_MISSING"); }
            var index = object(parse(indexBytes));
            require(index.keySet().equals(INDEX_FIELDS) && "LEGACYBRIDGE_SAVED_REPORTS".equals(index.get("reportType")), "REPORT_INDEX_INVALID");
            require(new java.math.BigDecimal("1").equals(index.get("schemaVersion")), "REPORT_INDEX_SCHEMA");
            require(receipt.jobId().toString().equals(index.get("jobId")) && receipt.inputSha256().equals(index.get("inputSha256")), "REPORT_IDENTITY_MISMATCH");
            require(index.get("reports") instanceof List<?> rows && !rows.isEmpty() && rows.size() <= 3, "REPORT_INDEX_INVALID");
            Path selected = null; String expected = null; Set<SavedReport.Kind> seen = EnumSet.noneOf(SavedReport.Kind.class);
            for (Object item : (List<?>) index.get("reports")) {
                var row = object(item);
                require(row.keySet().equals(Set.of("kind", "path", "sha256")), "REPORT_INDEX_INVALID");
                SavedReport.Kind entryKind;
                try { entryKind = SavedReport.Kind.valueOf(string(row.get("kind"))); }
                catch (IllegalArgumentException invalid) { throw new Refused("REPORT_INDEX_INVALID"); }
                require(seen.add(entryKind), "REPORT_INDEX_INVALID");
                String relative = string(row.get("path")), sha = string(row.get("sha256"));
                String[] parts = relative.split("/", -1);
                require(parts.length == 4 && parts[0].equals("legacybridge") && parts[1].equals("reports")
                        && parts[3].equals(entryKind.fileName()), "REPORT_PATH_UNSAFE");
                uuid(parts[2]); require(sha.matches("[a-f0-9]{64}"), "REPORT_INDEX_INVALID");
                if (entryKind == kind) { selected = access.workspace.reports().resolve(parts[2]).resolve(parts[3]); expected = sha; }
            }
            require(selected != null, "REPORT_NOT_RECORDED");
            byte[] content = access.read(selected, REPORT_BYTES);
            require(hash(content).equals(expected), "REPORT_HASH_MISMATCH");
            // JSON remains inert. Syntax/bounds checked, not accepted as an installable conversion contract.
            String raw = utf8(content); require(parse(content) instanceof Map<?, ?>, "REPORT_JSON_INVALID");
            StringBuilder display = new StringBuilder(raw.length());
            raw.codePoints().forEach(cp -> {
                int type = Character.getType(cp);
                if (cp == '\n' || cp == '\t' || cp != 0xA7 && type != Character.CONTROL && type != Character.FORMAT && type != Character.SURROGATE) display.appendCodePoint(cp);
            });
            require(Arrays.equals(receiptBytes, access.read(receiptPath, RECEIPT_BYTES))
                    && Arrays.equals(indexBytes, access.read(indexPath, INDEX_BYTES)), "REPORT_CHANGED");
            access.checkpoint();
            return EngineOutcome.completed(new SavedReport(receipt.jobId(), kind, expected, display.toString(), !raw.contentEquals(display)));
        } catch (Refused refused) {
            return EngineOutcome.failed(new EngineError(refused.getMessage(), "Saved report unavailable: " + refused.getMessage(),
                    "Refresh history or inspect the saved files. This view never authorizes Apply; old jobs may have no report index."));
        } catch (IOException | SecurityException | IllegalArgumentException error) {
            return EngineOutcome.failed(new EngineError("REPORT_READ_FAILED", "Saved report could not be read safely.",
                    "Check the report directory and permissions; do not edit hashes to bypass a refusal."));
        }
    }

    private static String string(Object value) throws Refused { require(value instanceof String, "REPORT_INDEX_INVALID"); return (String) value; }
    private static Map<?, ?> object(Object value) throws Refused { require(value instanceof Map<?, ?>, "REPORT_INDEX_INVALID"); return (Map<?, ?>) value; }
    private static Object parse(byte[] bytes) throws Refused {
        try { return BoundedJsonParser.parse(utf8(bytes)); }
        catch (BoundedJsonParser.JsonSyntaxException malformed) { throw new Refused("REPORT_JSON_INVALID"); }
    }
    private static String utf8(byte[] bytes) throws Refused {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (java.nio.charset.CharacterCodingException invalid) { throw new Refused("REPORT_UTF8_INVALID"); }
    }
    private static void uuid(String value) throws Refused {
        try { require(UUID.fromString(value).toString().equals(value), "REPORT_PATH_UNSAFE"); }
        catch (IllegalArgumentException invalid) { throw new Refused("REPORT_PATH_UNSAFE"); }
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void require(boolean valid, String code) throws Refused { if (!valid) throw new Refused(code); }
    private static final class Refused extends IOException { private Refused(String code) { super(code); } }

    private static final class Access {
        private final BridgeWorkspace workspace;
        private final BooleanSupplier cancelled;
        private final long started = System.nanoTime();
        private Access(Path gameDir, BooleanSupplier cancelled) throws IOException {
            this.cancelled = Objects.requireNonNull(cancelled); checkpoint(); workspace = BridgeWorkspace.inspect(gameDir);
        }
        private void checkpoint() throws Refused {
            require(!cancelled.getAsBoolean() && !Thread.currentThread().isInterrupted(), "CANCELLED");
            require(System.nanoTime() - started <= 10_000_000_000L, "REPORT_TIME_LIMIT");
        }
        private void directory(Path path) throws IOException {
            require(path.getParent() != null && path.getParent().equals(workspace.reports()), "REPORT_PATH_UNSAFE");
            uuid(path.getFileName().toString()); checked(path, true);
        }
        private String relative(Path path, String name) throws IOException {
            directory(path.getParent()); require(path.getFileName().toString().equals(name), "REPORT_PATH_UNSAFE");
            return workspace.gameDir().relativize(path).toString().replace('\\', '/');
        }
        private BasicFileAttributes checked(Path path, boolean directory) throws IOException {
            checkpoint(); workspace.revalidate();
            require(path.startsWith(workspace.reports()) && !path.equals(workspace.reports()), "REPORT_PATH_UNSAFE");
            Path cursor = workspace.gameDir(); BasicFileAttributes result = null;
            for (Path part : workspace.gameDir().relativize(path)) {
                cursor = cursor.resolve(part); checkpoint();
                result = Files.readAttributes(cursor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                require(!result.isSymbolicLink() && cursor.equals(cursor.toRealPath())
                        && (cursor.equals(path) && !directory ? result.isRegularFile() : result.isDirectory()), "REPORT_PATH_UNSAFE");
            }
            return Objects.requireNonNull(result);
        }
        private byte[] read(Path path, int limit) throws IOException {
            var before = checked(path, false); require(before.size() <= limit, "REPORT_TOO_LARGE");
            var output = new java.io.ByteArrayOutputStream((int) before.size());
            try (var stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = stream.read(buffer, 0, Math.min(buffer.length, limit - output.size() + 1))) >= 0) {
                    checkpoint(); require(output.size() + count <= limit, "REPORT_TOO_LARGE"); output.write(buffer, 0, count);
                }
            }
            var after = checked(path, false);
            require(before.size() == output.size() && before.size() == after.size()
                    && before.lastModifiedTime().equals(after.lastModifiedTime()) && Objects.equals(before.fileKey(), after.fileKey()), "REPORT_CHANGED");
            return output.toByteArray();
        }
    }
}
