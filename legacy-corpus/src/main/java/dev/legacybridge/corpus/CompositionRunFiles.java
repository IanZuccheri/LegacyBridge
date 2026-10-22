package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded read-only planning inputs. Not a hostile-writer sandbox or a filesystem snapshot. */
final class CompositionRunFiles {
    record Pin(String path, String sha256, long bytes) {
        Map<String, Object> tree() { return Map.of("path", path, "sha256", sha256, "bytes", bytes); }
    }
    private final Path root;
    private final Map<Path, Pin> selected = new LinkedHashMap<>();
    private long total;
    CompositionRunFiles(Path root) throws IOException { directory(root); this.root = root; }

    static void require(boolean condition, String code) throws IOException {
        if (!condition) throw new IOException("COMPOSE_PLAN_" + code);
    }
    static void relative(String value) throws IOException {
        require(value != null && value.length() <= 1024 && value.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]*(/[A-Za-z0-9_-][A-Za-z0-9_.-]*)*"), "PATH");
        for (String part : value.split("/")) require(!part.endsWith(".") && !part.matches("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(\\..*)?"), "PATH_DEVICE");
    }
    static void canonical(Path path) throws IOException {
        require(path != null && path.isAbsolute() && path.equals(path.normalize()), "ABSOLUTE_PATH");
        for (Path current = path; current != null; current = current.getParent()) {
            BasicFileAttributes attrs = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            require(!attrs.isSymbolicLink() && !attrs.isOther() && current.equals(current.toRealPath())
                    && current.toRealPath().equals(current.toRealPath(LinkOption.NOFOLLOW_LINKS)), "PATH_ALIAS");
        }
    }
    static void directory(Path path) throws IOException {
        canonical(path); require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS), "DIRECTORY");
    }
    static void absentTree(Path path) throws IOException {
        require(path != null && path.isAbsolute() && path.equals(path.normalize()), "ABSOLUTE_PATH");
        require(!Files.exists(path, LinkOption.NOFOLLOW_LINKS), "DESTINATION_EXISTS");
        Path parent = path.getParent();
        while (parent != null && !Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) parent = parent.getParent();
        directory(parent);
    }
    Path resolve(String relative) throws IOException { relative(relative); return root.resolve(relative); }
    Pin read(String relative) throws IOException {
        Path path = resolve(relative);
        if (selected.containsKey(path)) { verify(selected.get(path)); return selected.get(path); }
        require(selected.size() < 512, "FILE_BUDGET");
        canonical(path);
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        require(attrs.isRegularFile() && attrs.size() > 0 && attrs.size() <= (128L << 20), "FILE_SIZE");
        require(total + attrs.size() <= (512L << 20), "BYTE_BUDGET");
        Pin pin = new Pin(relative, hash(path), attrs.size());
        for (var prior : selected.entrySet()) {
            if (prior.getValue().sha256.equals(pin.sha256)) require(!Files.isSameFile(path, prior.getKey()), "FILE_ALIAS");
        }
        selected.put(path, pin); total += pin.bytes;
        return pin;
    }
    Pin select(String relative, String sha256, long bytes) throws IOException {
        require(sha256 != null && sha256.matches("[0-9a-f]{64}") && bytes > 0 && bytes <= (128L << 20), "PIN_FORMAT");
        Pin actual = read(relative);
        require(actual.bytes == bytes && actual.sha256.equals(sha256), "PIN_DRIFT");
        return actual;
    }
    void verify(Pin pin) throws IOException {
        Path path = resolve(pin.path);
        require(Files.size(path) == pin.bytes && hash(path).equals(pin.sha256), "PIN_DRIFT");
    }
    void verifyAll() throws IOException { for (Pin pin : selected.values()) verify(pin); }
    List<Pin> pins() { return List.copyOf(selected.values()); }
    String text(Pin pin, int maxBytes) throws IOException {
        require(pin.bytes <= maxBytes, "TEXT_SIZE");
        byte[] bytes;
        try (var input = Files.newInputStream(resolve(pin.path), LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(maxBytes + 1); }
        require(bytes.length == pin.bytes && digest(bytes).equals(pin.sha256), "TEXT_DRIFT");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    static String hash(Path path) throws IOException {
        canonical(path);
        var before = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        require(before.isRegularFile() && before.size() <= (128L << 20), "HASH_SIZE");
        MessageDigest digest = sha256(); long count = 0;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536]; int read;
            while ((read = input.read(buffer)) != -1) { count += read; require(count <= (128L << 20), "HASH_SIZE"); digest.update(buffer, 0, read); }
        }
        canonical(path);
        var after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        require(count == before.size() && count == after.size() && before.creationTime().equals(after.creationTime())
                && before.lastModifiedTime().equals(after.lastModifiedTime())
                && java.util.Objects.equals(before.fileKey(), after.fileKey()), "HASH_DRIFT");
        return HexFormat.of().formatHex(digest.digest());
    }
    static String digest(byte[] bytes) { return HexFormat.of().formatHex(sha256().digest(bytes)); }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static Map<String, Object> object(Object value) throws IOException {
        require(value instanceof Map<?, ?>, "OBJECT");
        @SuppressWarnings("unchecked") var result = (Map<String, Object>) value;
        return result;
    }
    static void keys(Map<String, Object> object, String... expected) throws IOException {
        require(object.keySet().equals(java.util.Set.of(expected)), "KEYS");
    }
    static String string(Object value) throws IOException { require(value instanceof String, "STRING"); return (String) value; }
    static long integer(Object value) throws IOException {
        require(value instanceof Number, "INTEGER");
        try { return new java.math.BigDecimal(value.toString()).longValueExact(); }
        catch (NumberFormatException | ArithmeticException bad) { throw new IOException("COMPOSE_PLAN_INTEGER", bad); }
    }
}
