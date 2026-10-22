package dev.legacybridge.validation.composition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded, Java-8-compatible I/O for explicitly requested, developer-only native observations.
 * This class neither loads fixture classes nor validates the observation's semantic schema.
 *
 * <p>Publication uses a forced, re-read private file and a same-parent, non-replacing move.
 * A hard-link witness proves ordinary cleanup ownership even when Windows returns null fileKey.
 * Unsupported hard links block publication; byte equality is never ownership evidence.
 * This is not a sandbox against a hostile concurrent process, a directory-fsync guarantee,
 * or crash recovery. A crash or lost ownership can leave explicitly reported private files.
 */
public final class ProbeFiles {
    static final long MAX_HASH_BYTES = 128L * 1024 * 1024;
    static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    @FunctionalInterface interface Writer { void write(Path file, byte[] bytes) throws IOException; }

    private ProbeFiles() { }

    /** Hashes one canonical regular file without executing or retaining its contents in memory. */
    public static String sha256(Path file) throws IOException {
        requirePath(file);
        directories(file.getParent());
        BasicFileAttributes before = regular(file);
        if (before.size() > MAX_HASH_BYTES) throw new IOException("OBS_HASH_SIZE_LIMIT: maximum 128 MiB");
        MessageDigest digest = digest();
        long total = 0;
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_HASH_BYTES) throw new IOException("OBS_HASH_SIZE_LIMIT: maximum 128 MiB");
                digest.update(buffer, 0, count);
            }
        }
        directories(file.getParent());
        BasicFileAttributes after = regular(file);
        if (total != before.size() || before.size() != after.size()
                || !Objects.equals(before.fileKey(), after.fileKey())
                || !before.creationTime().equals(after.creationTime())
                || !before.lastModifiedTime().equals(after.lastModifiedTime()))
            throw new IOException("OBS_HASH_DRIFT: file changed while being read");
        return hex(digest.digest());
    }

    /**
     * Publishes existingDirectory/runId.json and returns its verified SHA-256.
     * The caller must create the explicit composition-observations directory beforehand.
     * Existing files and unknown replacements are never overwritten or deliberately removed.
     */
    public static String publish(Path existingDirectory, String runId, byte[] utf8Json) throws IOException {
        return publish(existingDirectory, runId, utf8Json, ProbeFiles::write);
    }

    static String publish(Path directory, String runId, byte[] utf8Json, Writer writer) throws IOException {
        if (runId == null || !runId.matches("run-[a-z0-9][a-z0-9-]{0,40}"))
            throw new IOException("OBS_RUN_ID: expected run- followed by 1 to 41 lowercase letters/digits/hyphens");
        requirePath(directory);
        if (directory.getFileName() == null || !"composition-observations".equals(directory.getFileName().toString()))
            throw new IOException("OBS_DIRECTORY: explicit existing composition-observations directory required");
        directories(directory);
        if (utf8Json == null || utf8Json.length > MAX_JSON_BYTES)
            throw new IOException("OBS_JSON_SIZE_LIMIT: non-null UTF-8 input of at most 2 MiB required");
        byte[] snapshot = utf8Json.clone();
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(snapshot));
        } catch (CharacterCodingException invalid) {
            throw new IOException("OBS_UTF8: malformed observation encoding", invalid);
        }
        String expected = hex(digest().digest(snapshot));
        Path destination = directory.resolve(runId + ".json");
        absent(destination);
        Path pending = Files.createTempFile(directory, ".pending-" + runId + "-", ".json");
        Path witness = directory.resolve(".obs-owner-" + UUID.randomUUID() + ".ref");
        boolean witnessCreated = false;
        boolean moved = false;
        try {
            Files.createLink(witness, pending);
            witnessCreated = true;
            sameFile(pending, witness);
            writer.write(pending, snapshot);
            directories(directory);
            sameFile(pending, witness);
            if (!expected.equals(sha256(pending))) throw new IOException("OBS_WRITE_MISMATCH: private bytes differ");
            absent(destination);
            // Deliberately no ATOMIC_MOVE (provider-dependent replacement) and no REPLACE_EXISTING.
            Files.move(pending, destination);
            moved = true;
            directories(directory);
            sameFile(destination, witness);
            if (!expected.equals(sha256(destination))) throw new IOException("OBS_WRITE_MISMATCH: published bytes differ");
            Files.delete(witness);
            return expected;
        } catch (IOException | RuntimeException | Error failure) {
            if (moved) {
                failure.addSuppressed(new IOException("OBS_PUBLICATION_UNCERTAIN: final path exists; inspect "
                        + destination + " and witness " + witness + "; no completion reported"));
            } else if (!witnessCreated) {
                failure.addSuppressed(remainder(pending, witness));
            } else {
                try {
                    directories(directory);
                    sameFile(pending, witness);
                    Files.delete(pending);
                    Files.delete(witness);
                } catch (IOException | RuntimeException cleanupFailure) {
                    IOException retained = remainder(pending, witness);
                    retained.addSuppressed(cleanupFailure);
                    failure.addSuppressed(retained);
                }
            }
            throw failure;
        }
    }

    static void write(Path file, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static IOException remainder(Path pending, Path witness) {
        return new IOException("OBS_PRIVATE_REMAINDER: ownership or cleanup uncertain; retain "
                + pending + " and " + witness + "; no observation published");
    }

    private static void absent(Path file) throws IOException {
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(file.toString());
    }

    private static void requirePath(Path path) throws IOException {
        if (path == null || !path.isAbsolute() || !path.equals(path.normalize()))
            throw new IOException("OBS_PATH: explicit absolute normalized path required");
    }

    private static void directories(Path directory) throws IOException {
        if (directory == null) throw new IOException("OBS_DIRECTORY: directory required");
        for (Path current = directory; current != null; current = current.getParent()) {
            BasicFileAttributes attrs = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isDirectory() || attrs.isSymbolicLink() || attrs.isOther())
                throw new IOException("OBS_PATH_ALIAS: regular directory required; links/reparse points refused");
            canonical(current);
        }
    }

    private static BasicFileAttributes regular(Path file) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isRegularFile() || attrs.isSymbolicLink() || attrs.isOther())
            throw new IOException("OBS_FILE_IDENTITY: regular file required; links/reparse points refused");
        canonical(file);
        return attrs;
    }

    private static void canonical(Path path) throws IOException {
        Path real = path.toRealPath();
        if (!path.equals(real) || !real.equals(path.toRealPath(LinkOption.NOFOLLOW_LINKS)))
            throw new IOException("OBS_PATH_ALIAS: non-canonical path or linked parent refused");
    }

    private static void sameFile(Path file, Path witness) throws IOException {
        regular(file);
        regular(witness);
        if (!Files.isSameFile(file, witness))
            throw new IOException("OBS_FILE_REPLACED: refusing to consume/remove an unknown file");
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 255;
            result[i * 2] = digits[value >>> 4];
            result[i * 2 + 1] = digits[value & 15];
        }
        return new String(result);
    }
}
