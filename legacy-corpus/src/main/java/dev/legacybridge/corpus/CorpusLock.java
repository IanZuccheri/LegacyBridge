package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exact record-byte lock for one corpus version. The snapshot is governed by review/version
 * control, not a signature or an authenticated publisher database. No self-hash, network access,
 * mutation, mod loading or inference of runtime success is performed here.
 */
public final class CorpusLock {
    private CorpusLock() { }

    public static void verify(Path corpusRoot) throws IOException {
        Path root = CorpusJson.directory(corpusRoot);
        Map<String, Object> catalog = CorpusJson.read(root.resolve("catalog.json"));
        require(catalog.keySet().equals(Set.of("schemaVersion", "corpusVersion", "records")), "Unexpected catalog fields for corpus lock");
        schema(catalog.get("schemaVersion"));
        String version = version(catalog.get("corpusVersion"));
        String snapshotName = "snapshots/" + version + ".json";
        Path snapshotPath = CorpusVerifier.resolve(root, snapshotName, false);
        Map<String, Object> snapshot = CorpusJson.read(snapshotPath);
        require(snapshot.keySet().equals(Set.of("schemaVersion", "corpusVersion", "records")), "Unexpected corpus snapshot fields");
        schema(snapshot.get("schemaVersion"));
        require(version.equals(version(snapshot.get("corpusVersion"))), "Corpus snapshot version does not match catalog version");
        require(catalog.get("records") instanceof List<?> paths && paths.size() <= 256, "Corpus lock supports at most 256 catalog records");
        require(snapshot.get("records") instanceof Map<?, ?> hashes && hashes.size() <= 256, "Invalid or overlong corpus snapshot records");
        Map<?, ?> hashes = (Map<?, ?>) snapshot.get("records");
        Set<String> selected = new HashSet<>();
        for (Object value : (List<?>) catalog.get("records")) {
            require(value instanceof String relative && !relative.isBlank() && relative.length() <= 1024, "Invalid catalog record path");
            String relative = (String) value;
            require(relative.endsWith(".json") && !relative.equals(snapshotName) && !relative.equals("catalog.json"), "A corpus record cannot be the catalog/snapshot itself or a non-JSON file");
            CorpusVerifier.resolve(root, relative, false);
            require(selected.add(relative), "Duplicate catalog record path in corpus lock");
        }
        require(hashes.keySet().equals(selected), "Corpus record set differs from its versioned snapshot");
        for (String relative : selected) {
            Object expected = hashes.get(relative);
            require(expected instanceof String sha && sha.matches("[0-9a-f]{64}"), "Corpus snapshot requires lowercase SHA-256 values");
            Path record = CorpusVerifier.resolve(root, relative, false);
            require(expected.equals(hash(record)), "Corpus record bytes differ from their versioned snapshot: " + relative);
        }
    }

    private static String hash(Path file) throws IOException {
        Path path = CorpusJson.regular(file);
        require(Files.size(path) <= CorpusJson.MAX_BYTES, "Corpus record exceeds the 2 MiB lock limit");
        byte[] bytes;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(CorpusJson.MAX_BYTES + 1); }
        require(bytes.length <= CorpusJson.MAX_BYTES, "Corpus record grew beyond the 2 MiB lock limit");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String version(Object value) throws IOException {
        require(value instanceof String version && version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"), "Unsafe or missing corpus version token");
        return (String) value;
    }

    private static void schema(Object value) throws IOException {
        require(value instanceof Number, "Corpus lock schemaVersion must be a JSON number");
        try { require(new java.math.BigDecimal(value.toString()).intValueExact() == 1, "Unsupported corpus lock schema version"); }
        catch (NumberFormatException | ArithmeticException invalid) { throw new IOException("Invalid corpus lock schemaVersion", invalid); }
    }

    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
