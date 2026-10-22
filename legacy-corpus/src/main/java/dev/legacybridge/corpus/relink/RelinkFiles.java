package dev.legacybridge.corpus.relink;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Bounded snapshots/new-directory publication for an offline developer tool, not a hostile-FS sandbox. */
final class RelinkFiles {
    static final int MAX_BYTES = 32 << 20;
    private RelinkFiles() { }
    static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static void pin(byte[] bytes, String hash) throws IOException {
        require(hash != null && hash.matches("[a-f0-9]{64}") && hash(bytes).equals(hash), "HASH_MISMATCH");
    }
    static Path real(Path value) throws IOException {
        Path absolute = value.toAbsolutePath().normalize();
        for (Path part = absolute; part != null; part = part.getParent())
            require(!Files.isSymbolicLink(part) && absoluteRoot(part).equals(part), "UNSAFE_PATH_ALIAS");
        require(absolute.equals(absolute.toRealPath()), "UNSAFE_PATH_ALIAS");
        return absolute;
    }
    private static Path absoluteRoot(Path path) throws IOException { return path.toRealPath(); }
    static byte[] read(Path path, int limit) throws IOException {
        Path checked = real(path);
        require(Files.isRegularFile(checked, LinkOption.NOFOLLOW_LINKS), "NOT_REGULAR_FILE");
        var before = Files.readAttributes(checked, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        require(before.size() <= limit, "FILE_LIMIT");
        byte[] bytes;
        try (var input = Files.newInputStream(checked, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(limit + 1);
        }
        var after = Files.readAttributes(checked, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        require(bytes.length <= limit && bytes.length == before.size() && before.size() == after.size()
                && before.lastModifiedTime().equals(after.lastModifiedTime())
                && Objects.equals(before.fileKey(), after.fileKey()) && checked.equals(real(checked)), "FILE_CHANGED_OR_LIMIT");
        return bytes;
    }
    static void safeName(String name) throws IOException {
        require(!name.isEmpty() && name.length() <= 256 && !name.startsWith("/") && !name.contains("\\")
                && !name.contains(":") && name.chars().noneMatch(c -> c < 32 || c == 127), "UNSAFE_ENTRY");
        String value = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        for (String part : value.split("/", -1)) require(!part.isEmpty() && !part.equals(".") && !part.equals("..")
                && !part.endsWith(".") && !part.endsWith(" ")
                && !part.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"), "UNSAFE_ENTRY");
    }
    static SortedMap<String, byte[]> unzip(byte[] bytes, int entries, int inflated) throws IOException {
        require(bytes.length <= MAX_BYTES && bytes.length >= 22, "ARCHIVE_LIMIT_OR_TRUNCATED");
        // Require an EOCD, including its declared comment, to finish this exact snapshot.
        int end = -1;
        for (int i = Math.max(0, bytes.length - 65557); i <= bytes.length - 22; i++)
            if (bytes[i] == 0x50 && bytes[i+1] == 0x4b && bytes[i+2] == 5 && bytes[i+3] == 6
                    && i + 22 + (bytes[i+20] & 255) + ((bytes[i+21] & 255) << 8) == bytes.length) end = i;
        require(end >= 0, "TRUNCATED_ARCHIVE");
        var central = central(bytes, end, entries);
        var result = new TreeMap<String, byte[]>(); var names = new HashSet<String>();
        int count = 0, total = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                safeName(entry.getName());
                require(++count <= entries && names.add(entry.getName().toLowerCase(Locale.ROOT)), "DUPLICATE_OR_ENTRY_LIMIT");
                byte[] payload = zip.readNBytes(Math.min(2 << 20, inflated - total) + 1);
                total += payload.length;
                require(payload.length <= (2 << 20) && total <= inflated, "INFLATED_LIMIT");
                var expected = central.get(entry.getName()); var crc = new CRC32(); crc.update(payload);
                require(expected != null && expected[0] == payload.length && expected[1] == crc.getValue(), "CENTRAL_LOCAL_MISMATCH");
                if (entry.isDirectory()) require(payload.length == 0, "DIRECTORY_WITH_PAYLOAD");
                result.put(entry.getName(), payload); zip.closeEntry(); // includes CRC verification
            }
        }
        require(count > 0 && count == central.size(), "EMPTY_OR_INCONSISTENT_ARCHIVE");
        return result;
    }
    private static Map<String, long[]> central(byte[] bytes, int end, int limit) throws IOException {
        var b = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        require(b.getShort(end + 4) == 0 && b.getShort(end + 6) == 0, "MULTIDISK_UNSUPPORTED");
        int count = Short.toUnsignedInt(b.getShort(end + 10));
        long length = Integer.toUnsignedLong(b.getInt(end + 12)), start = Integer.toUnsignedLong(b.getInt(end + 16));
        require(count > 0 && count <= limit && count == Short.toUnsignedInt(b.getShort(end + 8))
                && start + length == end && start < end, "CENTRAL_LIMIT_OR_ZIP64_UNSUPPORTED");
        var result = new HashMap<String, long[]>(); int cursor = (int) start;
        for (int i = 0; i < count; i++) {
            require(cursor <= end - 46 && b.getInt(cursor) == 0x02014b50, "CENTRAL_HEADER");
            int nameSize = Short.toUnsignedInt(b.getShort(cursor + 28));
            int next = cursor + 46 + nameSize + Short.toUnsignedInt(b.getShort(cursor + 30)) + Short.toUnsignedInt(b.getShort(cursor + 32));
            long offset = Integer.toUnsignedLong(b.getInt(cursor + 42));
            require(next <= end && nameSize <= 256 && b.getShort(cursor + 34) == 0
                    && (b.getShort(cursor + 8) & 1) == 0 && offset < start - 4
                    && b.getInt((int) offset) == 0x04034b50, "CENTRAL_LOCAL_HEADER");
            String name = new String(bytes, cursor + 46, nameSize, java.nio.charset.StandardCharsets.UTF_8);
            safeName(name);
            require(result.putIfAbsent(name, new long[]{Integer.toUnsignedLong(b.getInt(cursor + 24)),
                    Integer.toUnsignedLong(b.getInt(cursor + 16))}) == null, "DUPLICATE_CENTRAL_ENTRY");
            cursor = next;
        }
        require(cursor == end, "CENTRAL_TRAILING_BYTES"); return result;
    }
    static byte[] zip(Map<String, byte[]> entries) throws IOException {
        var out = new ByteArrayOutputStream(); int total = 0;
        try (var zip = new ZipOutputStream(out)) {
            for (var entry : new TreeMap<>(entries).entrySet()) {
                safeName(entry.getKey()); total = Math.addExact(total, entry.getValue().length);
                require(total <= MAX_BYTES, "OUTPUT_LIMIT");
                var member = new ZipEntry(entry.getKey()); member.setTime(0);
                zip.putNextEntry(member); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        require(out.size() <= MAX_BYTES, "OUTPUT_LIMIT"); return out.toByteArray();
    }
    static void write(Path root, String name, byte[] bytes) throws IOException {
        safeName(name); Path target = root.resolve(name);
        Files.createDirectories(target.getParent()); Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
    }
    @FunctionalInterface interface Action { void run(Path stage) throws IOException; }
    static void publishDirectory(Path destination, Action action) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        require(target.getParent() != null && target.getFileName() != null, "OUTPUT_PARENT_REQUIRED");
        Path parent = real(target.getParent());
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS), "OUTPUT_EXISTS");
        Path stage = Files.createTempDirectory(parent, ".legacybridge-relink-");
        try {
            action.run(stage);
            require(real(parent).equals(parent), "OUTPUT_PARENT_CHANGED");
            Files.move(stage, target); // no ATOMIC_MOVE: that option can ignore no-replace semantics
        } finally {
            if (Files.exists(stage, LinkOption.NOFOLLOW_LINKS)) {
                require(stage.getParent().equals(parent) && stage.getFileName().toString().startsWith(".legacybridge-relink-")
                        && real(stage).equals(stage), "UNSAFE_CLEANUP_TARGET");
                Files.walkFileTree(stage, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file); return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                        if (error != null) throw error; Files.delete(dir); return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }
}
