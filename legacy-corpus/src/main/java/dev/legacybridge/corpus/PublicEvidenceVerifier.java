package dev.legacybridge.corpus;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Development-only, read-only integrity check of a reviewed evidence index.
 * Hashes and exact JSON assertions detect drift, not truthful gameplay claims.
 * Never loads a JAR, decodes an image, runs games, downloads, writes or approves a cut.
 * Run only on a quiescent, locally controlled evidence tree, not as a security sandbox.
 */
public final class PublicEvidenceVerifier {
    static final long MAX_FILE_BYTES = 128L << 20;
    static final long MAX_TOTAL_BYTES = 512L << 20;
    private static final Set<String> KINDS = Set.of("JAR", "LEDGER", "SCREENSHOT", "LOG", "TEST_RESULT", "SOURCE");
    private static final Set<String> DIMENSIONS = Set.of("STATIC", "NATIVE_CLIENT", "NATIVE_CLIENT_LOG", "NATIVE_SERVER", "HEADLESS", "REVIEW");
    private static final Set<String> STATES = Set.of("RECORDED_PASS", "PENDING", "NOT_CLAIMED");

    public record Summary(int files, long bytes, int assertions, int recordedObservations,
                          List<String> pending, List<String> notClaimed) {
        public Summary { pending = List.copyOf(pending); notClaimed = List.copyOf(notClaimed); }
    }

    public Summary verify(Path repository, String indexPath) throws IOException {
        Path root = CorpusJson.directory(repository);
        Map<String, Object> index = CorpusJson.read(safe(root, indexPath));
        require(number(index.get("schemaVersion")) == 1, "Unsupported evidence index schema");
        require("LEGACYBRIDGE_PUBLIC_EVIDENCE_INDEX".equals(index.get("reportType")), "Invalid evidence index type");
        // This first index is explicitly a partial review, never a release authorization.
        require("PARTIAL".equals(index.get("qualification")), "Evidence integrity cannot approve qualification");
        String publicHash = digest(index.get("publicSha256"));
        Map<String, Entry> entries = new HashMap<>();
        Set<Path> paths = new HashSet<>();
        long total = 0;
        for (Object raw : list(index.get("files"), 512)) {
            Map<String, Object> file = object(raw);
            String id = identifier(file.get("id"));
            String kind = text(file.get("kind"));
            require(KINDS.contains(kind), "Unknown evidence kind: " + kind);
            Path path = safe(root, text(file.get("path")));
            long size = number(file.get("bytes"));
            require(size >= 0 && size <= MAX_FILE_BYTES, "Evidence file exceeds byte bound");
            total += size;
            require(total <= MAX_TOTAL_BYTES, "Evidence aggregate exceeds byte bound");
            require(paths.add(path), "Duplicate evidence path");
            require(entries.putIfAbsent(id, new Entry(path, kind, digest(file.get("sha256")), size)) == null,
                    "Duplicate evidence ID");
        }
        require(!entries.isEmpty(), "Empty evidence inventory");
        Entry publicJar = entry(entries, text(index.get("publicArtifact")));
        require(publicJar.kind.equals("JAR") && publicJar.sha256.equals(publicHash), "Public identity mismatch");
        for (Entry entry : entries.values()) verifyBytes(entry);
        Map<String, Map<String, Object>> ledgers = new HashMap<>();
        Set<String> assertionIds = new HashSet<>();
        Map<String, String> assertionSources = new HashMap<>();
        boolean publicBound = false;
        long ledgerBytes = 0;
        for (Object raw : list(index.get("assertions"), 512)) {
            Map<String, Object> assertion = object(raw);
            String id = identifier(assertion.get("id"));
            require(assertionIds.add(id), "Duplicate assertion ID");
            String source = text(assertion.get("file"));
            Entry ledger = entry(entries, source);
            require(ledger.kind.equals("LEDGER"), "JSON assertion must reference a ledger");
            if (!ledgers.containsKey(source)) {
                ledgerBytes += ledger.bytes;
                require(ledgers.size() < 16 && ledgerBytes <= (8L << 20), "Parsed ledger budget exceeded");
                ledgers.put(source, CorpusJson.read(ledger.path));
            }
            require(assertion.containsKey("expected"), "Assertion expected value must be explicit");
            Object expected = assertion.get("expected");
            require(expected == null || expected instanceof String || expected instanceof Boolean || expected instanceof BigDecimal,
                    "Only scalar assertion values are supported");
            Object observed = pointer(ledgers.get(source), text(assertion.get("pointer")));
            require(equal(expected, observed), "Recorded assertion changed: " + id);
            if (publicHash.equals(expected)) publicBound = true;
            assertionSources.put(id, source);
        }
        require(publicBound, "At least one ledger must bind the exact public hash");
        Set<String> observationIds = new HashSet<>();
        List<String> pending = new ArrayList<>(), notClaimed = new ArrayList<>();
        int recorded = 0;
        for (Object raw : list(index.get("observations"), 128)) {
            Map<String, Object> observation = object(raw);
            String id = identifier(observation.get("id"));
            require(observationIds.add(id), "Duplicate observation ID");
            String dimension = text(observation.get("dimension")), state = text(observation.get("state"));
            require(DIMENSIONS.contains(dimension) && STATES.contains(state), "Unknown evidence dimension/state");
            text(observation.get("boundary"));
            Set<String> evidence = new HashSet<>(), kinds = new HashSet<>();
            for (Object reference : list(observation.get("evidence"), 128)) {
                String file = text(reference);
                require(evidence.add(file), "Duplicate observation evidence");
                kinds.add(entry(entries, file).kind);
            }
            Set<String> checks = new HashSet<>();
            for (Object reference : list(observation.get("assertions"), 128)) {
                String assertion = text(reference);
                require(checks.add(assertion) && assertionIds.contains(assertion), "Unknown/duplicate observation assertion");
                require(evidence.contains(assertionSources.get(assertion)), "Assertion ledger absent from observation evidence");
            }
            if (state.equals("RECORDED_PASS")) {
                require(!checks.isEmpty(), "Recorded observation needs a checked ledger assertion");
                if (dimension.equals("NATIVE_CLIENT"))
                    require(kinds.contains("SCREENSHOT") && kinds.contains("LOG"), "Native client needs actual image and log evidence");
                if (dimension.equals("NATIVE_SERVER") || dimension.equals("NATIVE_CLIENT_LOG"))
                    require(kinds.contains("LOG"), "Native server/client-log needs log evidence");
                recorded++;
            } else if (state.equals("PENDING")) pending.add(id);
            else notClaimed.add(id);
        }
        require(!pending.isEmpty(), "Partial evidence index must retain its pending gates");
        return new Summary(entries.size(), total, assertionIds.size(), recorded, pending, notClaimed);
    }

    private static void verifyBytes(Entry entry) throws IOException {
        require(Files.size(entry.path) == entry.bytes, "Evidence size mismatch: " + entry.path);
        MessageDigest hash;
        try { hash = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IOException("SHA-256 unavailable", impossible); }
        long count = 0;
        byte[] buffer = new byte[64 * 1024];
        try (var stream = Files.newInputStream(entry.path, LinkOption.NOFOLLOW_LINKS)) {
            for (int read; (read = stream.read(buffer)) != -1;) {
                count += read;
                require(count <= entry.bytes, "Evidence grew during hash read");
                hash.update(buffer, 0, read);
            }
        }
        require(count == entry.bytes && HexFormat.of().formatHex(hash.digest()).equals(entry.sha256),
                "Evidence hash mismatch: " + entry.path);
        CorpusJson.regular(entry.path);
    }

    private static Path safe(Path root, String relative) throws IOException {
        require(relative.length() <= 1024 && !relative.contains("\\") && !relative.contains(":")
                && !relative.startsWith("/") && relative.chars().noneMatch(c -> c < 32 || c == 127), "Unsafe evidence path");
        for (String segment : relative.split("/", -1)) require(!segment.isBlank() && !segment.equals(".")
                && !segment.equals("..") && !segment.endsWith(".") && !segment.endsWith(" "), "Unsafe evidence component");
        try {
            Path path = root.resolve(relative).normalize();
            require(path.startsWith(root), "Evidence escapes repository");
            return CorpusJson.regular(path);
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid evidence path", invalid); }
    }

    /** Deliberately restricted JSON pointer: object keys or canonical non-negative array indices. */
    private static Object pointer(Object value, String pointer) throws IOException {
        require(pointer.startsWith("/") && pointer.length() <= 1024 && !pointer.contains("~"), "Unsupported evidence pointer");
        String[] segments = pointer.substring(1).split("/", -1);
        require(segments.length <= 32, "Evidence pointer depth exceeded");
        for (String segment : segments) {
            require(!segment.isEmpty(), "Empty evidence pointer segment");
            if (value instanceof Map<?, ?> map) {
                require(map.containsKey(segment), "Missing evidence pointer key"); value = map.get(segment);
            } else if (value instanceof List<?> list) {
                require(segment.matches("0|[1-9][0-9]{0,4}"), "Invalid evidence array index");
                int index = Integer.parseInt(segment);
                require(index < list.size(), "Evidence array index out of range"); value = list.get(index);
            } else throw new IOException("Evidence pointer traverses a scalar");
        }
        return value;
    }

    private static boolean equal(Object expected, Object observed) {
        if (expected instanceof BigDecimal left && observed instanceof BigDecimal right) return left.compareTo(right) == 0;
        return java.util.Objects.equals(expected, observed);
    }
    private static Entry entry(Map<String, Entry> entries, String id) throws IOException {
        require(entries.containsKey(id), "Missing evidence reference: " + id); return entries.get(id);
    }
    private static String digest(Object value) throws IOException {
        String hash = text(value); require(hash.matches("[a-f0-9]{64}"), "Invalid SHA-256"); return hash;
    }
    private static String identifier(Object value) throws IOException {
        String id = text(value); require(id.matches("[A-Z][A-Z0-9_-]{0,95}"), "Invalid evidence ID"); return id;
    }
    private static String text(Object value) throws IOException {
        require(value instanceof String, "Evidence string required"); String text = (String) value;
        require(!text.isBlank() && text.length() <= 4096 && text.chars().noneMatch(c -> c < 32 || c == 127), "Invalid evidence text");
        return text;
    }
    private static long number(Object value) throws IOException {
        require(value instanceof BigDecimal, "Evidence integer required");
        try { return ((BigDecimal) value).longValueExact(); }
        catch (ArithmeticException invalid) { throw new IOException("Invalid evidence integer", invalid); }
    }
    private static Map<String, Object> object(Object value) throws IOException {
        require(value instanceof Map<?, ?>, "Evidence object required");
        @SuppressWarnings("unchecked") var map = (Map<String, Object>) value; return map;
    }
    private static List<?> list(Object value, int max) throws IOException {
        require(value instanceof List<?>, "Evidence array required"); var list = (List<?>) value;
        require(list.size() <= max, "Evidence array limit exceeded"); return list;
    }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
    private record Entry(Path path, String kind, String sha256, long bytes) { }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Usage: PublicEvidenceVerifier <repository-root> <relative-index-path>");
        Summary result = new PublicEvidenceVerifier().verify(Path.of(args[0]), args[1]);
        System.out.println("PUBLIC_EVIDENCE_INTEGRITY_VERIFIED files=" + result.files + " bytes=" + result.bytes
                + " assertions=" + result.assertions + " recordedObservations=" + result.recordedObservations
                + " pending=" + String.join(",", result.pending) + " notClaimed=" + String.join(",", result.notClaimed)
                + " qualification=PARTIAL_REVIEW_REQUIRED gamesLaunched=false statusesChanged=false");
    }
}
