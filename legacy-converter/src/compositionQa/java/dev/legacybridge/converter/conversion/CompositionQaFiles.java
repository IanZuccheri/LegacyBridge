package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStage;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Dev-only, local QA handoff, NOT the product installer or a general transaction/recovery system. */
final class CompositionQaFiles {
    private static final List<String> FILES = List.of("candidate.jar", "proof.json", "receipt.json");
    private static final long MAX_FILE = 128L * 1024 * 1024;
    @FunctionalInterface interface Writer { void write(Path path, byte[] bytes) throws IOException; }
    private CompositionQaFiles() { }

    static void requireHash(String hash) {
        if (hash == null || !hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("QA_HASH_REQUIRED: exact lowercase SHA-256 required");
    }
    static void preflightRoot(Path root, String runId) throws IOException {
        rootGrammar(root, runId);
        directories(root);
        if (Files.exists(root.resolve(runId), LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(root.resolve(runId).toString());
    }
    private static void rootGrammar(Path root, String runId) throws IOException {
        if (runId == null || !runId.matches("run-[a-z0-9][a-z0-9-]{0,40}"))
            throw new IllegalArgumentException("QA_RUN_ID: use run- followed by at most 41 lowercase letters/digits/hyphens");
        if (!root.isAbsolute() || !root.equals(root.normalize()) || root.getFileName() == null || !"composition-qa".equals(root.getFileName().toString()))
            throw new IOException("QA_ROOT: explicit normalized composition-qa directory required");
        for (Path part : root) if (Set.of("mods", "staging", "input", "transactions", "saves").contains(part.toString().toLowerCase(Locale.ROOT)))
            throw new IOException("QA_ROOT: game/installation directories are not QA export destinations");
    }
    static void ensureRoot(Path root, String runId) throws IOException {
        rootGrammar(root, runId);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            directories(root.getParent());
            Files.createDirectory(root); // Only this leaf. Never create an arbitrary parent hierarchy.
        }
        preflightRoot(root, runId);
    }
    private static void directories(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            var attrs = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isDirectory() || attrs.isSymbolicLink() || attrs.isOther()
                    || !current.toRealPath().equals(current.toRealPath(LinkOption.NOFOLLOW_LINKS)))
                throw new IOException("QA_PATH_ALIAS: directory link/reparse point refused");
        }
    }
    static void requireInput(Path input, String expected) throws IOException {
        if (!input.isAbsolute() || !input.equals(input.normalize())) throw new IOException("QA_INPUT: absolute normalized path required");
        directories(input.getParent());
        if (!hashFile(input).equals(expected)) throw new IOException("QA_INPUT_CHANGED: original differs from the selected SHA-256");
    }

    static Path publish(Path input, String sourceSha, String candidateSha, Path root, String runId,
                        byte[] candidate, Map<String, Object> proof, Writer writer) throws IOException {
        preflightRoot(root, runId); requireInput(input, sourceSha);
        if (!SimpleContentConverter.hash(candidate).equals(candidateSha)) throw new IOException("QA_CANDIDATE_CHANGED");
        byte[] proofBytes = json(proof);
        byte[] receipt = json(Map.of("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_QA_EXPORT",
                "status", "EXPORTED_FOR_QA", "installationAllowed", false, "runtimeValidation", "NOT_RUN",
                "sourceSha256", sourceSha, "candidateSha256", candidateSha,
                "proofSha256", SimpleContentConverter.hash(proofBytes), "files", FILES));
        Path pending = Files.createTempDirectory(root, ".pending-" + runId + "-");
        Path destination = root.resolve(runId);
        // JDK 17 Windows returns null fileKey. Real hard-link witnesses, not hashes, prove cleanup ownership.
        var owned = new LinkedHashMap<Path, Path>();
        Path marker = pending.resolve(".owner");
        boolean moved = false, guardsReleased = false;
        try {
            createOwned(marker, root, owned);
            byte[][] content = {candidate, proofBytes, receipt};
            for (int i = 0; i < FILES.size(); i++) {
                OperationControl.checkpoint(); directories(pending);
                Path path = pending.resolve(FILES.get(i));
                createOwned(path, root, owned);
                writer.write(path, content[i]);
                sameFile(path, owned.get(path));
                if (!hashFile(path).equals(SimpleContentConverter.hash(content[i]))) throw new IOException("QA_WRITE_MISMATCH: " + FILES.get(i));
            }
            exactFiles(pending);
            for (int i = 0; i < FILES.size(); i++) {
                Path path = pending.resolve(FILES.get(i)); sameFile(path, owned.get(path));
                if (!hashFile(path).equals(SimpleContentConverter.hash(content[i]))) throw new IOException("QA_WRITE_MISMATCH: final recheck");
            }
            OperationControl.stage(OperationStage.PRE_PUBLICATION, "Developer QA export ready; not an installable conversion result");
            requireInput(input, sourceSha); preflightRoot(root, runId); sameFile(marker, owned.get(marker));
            // Witness cleanup is also inside the cancellable/private phase. No refs enter the exported directory.
            for (var entry : owned.entrySet()) sameFile(entry.getKey(), entry.getValue());
            guardsReleased = true; // On cleanup/move failure retain complete private data; never guess ownership.
            for (var entry : owned.entrySet()) Files.delete(entry.getValue());
            Files.delete(marker);
            OperationControl.checkpoint();
            OperationControl.beginPublication(); // After this point cancellation is refused, not reported as successful.
            // No REPLACE_EXISTING or ATOMIC_MOVE (which may replace on some providers). Same-parent local move only.
            Files.move(pending, destination);
            moved = true;
            return destination;
        } catch (IOException | RuntimeException | Error failure) {
            if (!moved && !guardsReleased) {
                try { cleanup(pending, marker, owned); }
                catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            }
            if (!moved && guardsReleased) failure.addSuppressed(new IOException("QA_PRIVATE_REMAINDER: retain " + pending + "; no completed export"));
            throw failure;
        }
    }

    private static void cleanup(Path pending, Path marker, Map<Path, Path> owned) throws IOException {
        directories(pending); sameFile(marker, owned.get(marker));
        for (var file : owned.entrySet()) sameFile(file.getKey(), file.getValue());
        for (var file : owned.entrySet()) { Files.delete(file.getKey()); Files.delete(file.getValue()); }
        Files.delete(pending); // Non-recursive; unknown contents prevent removal and surface as a suppressed failure.
    }
    private static void createOwned(Path path, Path root, Map<Path, Path> owned) throws IOException {
        Files.createFile(path);
        Path reference = root.resolve(".qa-owner-" + UUID.randomUUID() + ".ref");
        owned.put(path, reference);
        Files.createLink(reference, path); // No fallback to a copy: equality of bytes does not prove file identity.
        sameFile(path, reference);
    }
    private static BasicFileAttributes regular(Path path) throws IOException {
        var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attrs.isSymbolicLink() || attrs.isOther() || !attrs.isRegularFile())
            throw new IOException("QA_FILE_IDENTITY: regular local file required");
        return attrs;
    }
    private static void sameFile(Path path, Path reference) throws IOException {
        regular(path); regular(reference);
        if (!Files.isSameFile(path, reference)) throw new IOException("QA_FILE_REPLACED: refusing to consume/remove an unknown file");
    }
    private static void exactFiles(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            Set<String> names = new HashSet<>();
            var iterator = stream.iterator();
            while (iterator.hasNext()) { names.add(iterator.next().getFileName().toString()); if (names.size() > FILES.size() + 1) break; }
            var expected = new HashSet<>(FILES); expected.add(".owner");
            if (!names.equals(expected)) throw new IOException("QA_FILE_SET: unexpected/incomplete export files");
        }
    }
    static void write(Path path, byte[] bytes) throws IOException {
        if (bytes.length > MAX_FILE) throw new IOException("QA_SIZE_LIMIT");
        try (var channel = FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) { OperationControl.checkpoint(); channel.write(buffer); }
            channel.force(true);
        }
    }
    static String hashFile(Path path) throws IOException {
        var before = regular(path);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); long total = 0;
            try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) {
                    OperationControl.checkpoint(); total += count;
                    if (total > MAX_FILE) throw new IOException("QA_SIZE_LIMIT");
                    digest.update(buffer, 0, count);
                }
            }
            var after = regular(path);
            if (!Objects.equals(before.fileKey(), after.fileKey()) || !before.creationTime().equals(after.creationTime())
                    || !before.lastModifiedTime().equals(after.lastModifiedTime()) || before.size() != after.size())
                throw new IOException("QA_FILE_CHANGED_DURING_READ");
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static byte[] json(Map<String, ?> value) {
        return new CanonicalReportWriter().toJsonTree(SimpleContentConverter.canonicalTree(value)).getBytes(StandardCharsets.UTF_8);
    }
}
