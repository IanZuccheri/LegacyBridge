package dev.legacybridge.product.install;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** No replace-capable moves: complete files are published through same-filesystem hard links. */
final class TransactionFiles {
    static final String DURABILITY = "FILE_CONTENTS_FORCED_DIRECTORY_FSYNC_NOT_GUARANTEED";
    private TransactionFiles() { }
    record Guard(Path directory, Path marker, Path reference) { }
    record DirectoryIdentities(Map<Path, Object> keys, java.util.List<Guard> guards) implements AutoCloseable {
        void verify() throws IOException {
            for (var entry : keys.entrySet()) {
                directory(entry.getKey());
                require(entry.getValue().equals(Files.readAttributes(entry.getKey(), java.nio.file.attribute.BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS).fileKey()), "Workspace/transaction directory identity changed during commit");
            }
            for (Guard guard : guards) {
                directory(guard.directory()); AddOnlyInstaller.regular(guard.marker()); AddOnlyInstaller.regular(guard.reference());
                require(Files.isSameFile(guard.marker(), guard.reference()), "Workspace canary identity changed during commit");
            }
        }
        @Override public void close() throws IOException {
            IOException failure = null;
            for (Guard guard : guards) {
                try { cleanGuard(guard); }
                catch (IOException cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
            }
            if (failure != null) throw failure;
        }
    }
    static DirectoryIdentities pinDirectories(Path... paths) throws IOException {
        Map<Path, Object> keys = new TreeMap<>(); java.util.List<Guard> guards = new java.util.ArrayList<>();
        Path transaction = paths[paths.length - 1]; String owner = transaction.getFileName().toString(), operation = UUID.randomUUID().toString();
        try {
            for (int index = 0; index < paths.length; index++) {
                Path path = paths[index]; directory(path);
                Object key = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
                if (key != null) { keys.put(path, key); continue; }
                // JDK 17 WindowsFileAttributes.fileKey() returns null. A unique, owned
                // canary and a retained hard-link reference detect replacement without
                // private JDK/native access. This is not a hostile-process sandbox.
                Path marker = path.resolve(".legacybridge-guard-" + owner + "-" + operation + "-" + index);
                Path reference = transaction.resolve("guard-" + operation + "-" + index + ".ref");
                Files.createFile(marker); Guard guard = new Guard(path, marker, reference); guards.add(guard);
                link(reference, marker);
            }
            return new DirectoryIdentities(Map.copyOf(keys), java.util.List.copyOf(guards));
        } catch (IOException failure) {
            try { new DirectoryIdentities(keys, guards).close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    private static void cleanGuard(Guard guard) throws IOException {
        directory(guard.directory()); AddOnlyInstaller.regular(guard.marker()); AddOnlyInstaller.regular(guard.reference());
        require(Files.isSameFile(guard.marker(), guard.reference()), "Owned canary cleanup refused: marker identity changed");
        Files.delete(guard.marker()); Files.delete(guard.reference());
    }
    static void cleanupRetainedGuards(Path transaction, Path... directories) throws IOException {
        var pattern = java.util.regex.Pattern.compile("guard-([a-f0-9-]{36})-([0-6])\\.ref");
        java.util.List<Guard> guards = new java.util.ArrayList<>();
        try (var files = Files.newDirectoryStream(transaction)) {
            int count = 0;
            for (Path reference : files) {
                require(++count <= 128, "Transaction guard inventory exceeds its bound");
                var match = pattern.matcher(reference.getFileName().toString()); if (!match.matches()) continue;
                require(uuid(match.group(1)), "Malformed retained guard identity"); int index = Integer.parseInt(match.group(2));
                Path directory = directories[index];
                guards.add(new Guard(directory, directory.resolve(".legacybridge-guard-" + transaction.getFileName() + "-" + match.group(1) + "-" + index), reference));
            }
        }
        new DirectoryIdentities(Map.of(), guards).close();
    }
    static boolean knownTransactionCanary(Path marker, Path transactions) throws IOException {
        var match = java.util.regex.Pattern.compile("\\.legacybridge-guard-([a-f0-9-]{36})-([a-f0-9-]{36})-4").matcher(marker.getFileName().toString());
        if (!match.matches()) return false;
        require(uuid(match.group(1)) && uuid(match.group(2)), "Invalid retained transaction canary");
        Path transaction = transaction(transactions.resolve(match.group(1)), transactions);
        Path reference = transaction.resolve("guard-" + match.group(2) + "-4.ref");
        AddOnlyInstaller.regular(marker); AddOnlyInstaller.regular(reference);
        require(Files.isSameFile(marker, reference), "Retained transaction canary identity differs"); return true;
    }
    static void require(boolean condition, String detail) throws IOException { StagedArtifactValidator.require(condition, detail); }
    static boolean exists(Path path) { return Files.exists(path, LinkOption.NOFOLLOW_LINKS); }
    static void directory(Path path) throws IOException {
        require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
                && path.equals(path.toRealPath()), "Expected canonical real directory: " + path.getFileName());
    }
    static Path descendant(Path path, Path root) throws IOException {
        Path value = path.toAbsolutePath().normalize();
        require(value.startsWith(root) && !value.equals(root), "File lies outside its permitted workspace area");
        for (Path parent = value.getParent(); parent != null && parent.startsWith(root); parent = parent.getParent()) directory(parent);
        AddOnlyInstaller.regular(value); return value;
    }
    static String relative(Path path, Path root) throws IOException {
        String value = root.relativize(descendant(path, root)).toString().replace('\\', '/');
        require(value.length() <= 1024, "Recovery path exceeds its bounded grammar"); return value;
    }
    static Path resolveRelative(Path root, String value) throws IOException {
        require(value != null && !value.isBlank() && value.length() <= 1024 && !value.startsWith("/")
                && !value.contains("\\") && !value.contains(":") && !value.contains("//")
                && value.codePoints().noneMatch(Character::isISOControl), "Invalid journal relative path");
        for (String part : value.split("/")) require(!part.equals(".") && !part.equals(".."), "Journal path traversal");
        return descendant(root.resolve(value), root);
    }
    static void digest(String value) throws IOException { require(value != null && value.matches("[a-f0-9]{64}"), "Invalid journal/session SHA-256"); }
    static boolean uuid(String value) { try { return UUID.fromString(value).toString().equals(value); } catch (IllegalArgumentException invalid) { return false; } }
    static Path transaction(Path requested, Path transactions) throws IOException {
        Path path = requested.toAbsolutePath().normalize();
        require(transactions.equals(path.getParent()) && uuid(path.getFileName().toString()), "Unknown transaction ownership");
        directory(path); return path;
    }
    static void copy(Path source, Path destination, TransactionalInstaller.Hook hook,
                     TransactionalInstaller.Boundary boundary, int index) throws IOException {
        AddOnlyInstaller.regular(source);
        try (var input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS);
             var output = FileChannel.open(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = new byte[65536]; long total = 0;
            for (int size; (size = input.read(bytes)) >= 0;) {
                OperationControl.checkpoint(); require((total += size) <= (64L << 20), "Managed artifact exceeds 64 MiB");
                ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, size); while (buffer.hasRemaining()) output.write(buffer);
                hook.reached(boundary, index);
            }
            output.force(true);
        }
        require(!Files.isSameFile(source, destination), "Backup/payload must be an independent copy");
    }
    static void link(Path destination, Path source) throws IOException {
        AddOnlyInstaller.regular(source); directory(destination.getParent());
        require(Files.getFileStore(source).equals(Files.getFileStore(destination.getParent())), "Same-filesystem hard links are required");
        try { Files.createLink(destination, source); }
        catch (UnsupportedOperationException unsupported) { throw new IOException("Hard-link publication is unsupported", unsupported); }
        require(Files.isSameFile(source, destination), "Hard-link publication identity differs");
    }
    static byte[] json(Map<String, Object> fields) { return new CanonicalReportWriter().toJsonTree(new TreeMap<>(fields)).getBytes(StandardCharsets.UTF_8); }
    static Map<String, Object> read(Path path) throws Exception {
        AddOnlyInstaller.regular(path); byte[] bytes;
        try (var stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { bytes = stream.readNBytes(65_537); }
        require(bytes.length <= 65_536, "Journal exceeds 64 KiB");
        Object parsed = BoundedJsonParser.parse(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());
        require(parsed instanceof Map<?, ?>, "Journal must be an object");
        @SuppressWarnings("unchecked") var value = (Map<String, Object>) parsed;
        return value;
    }
    static void publishJson(Path destination, Map<String, Object> value, TransactionalInstaller.Hook hook) throws Exception {
        byte[] bytes = json(value); require(bytes.length <= 65_536, "Generated journal exceeds 64 KiB");
        if (exists(destination)) { require(read(destination).equals(BoundedJsonParser.parse(new String(bytes, StandardCharsets.UTF_8))), "Existing immutable journal checkpoint differs"); return; }
        Path pending = destination.getParent().resolve("pending-" + UUID.randomUUID() + ".json");
        try (FileChannel file = FileChannel.open(pending, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            // The unpublished name may contain a partial tail after a crash; it is never authoritative.
            int split = Math.max(1, bytes.length / 2); ByteBuffer first = ByteBuffer.wrap(bytes, 0, split);
            while (first.hasRemaining()) file.write(first);
            hook.reached(TransactionalInstaller.Boundary.JOURNAL_WRITE, -1);
            ByteBuffer rest = ByteBuffer.wrap(bytes, split, bytes.length - split); while (rest.hasRemaining()) file.write(rest);
            file.force(true);
        }
        link(destination, pending);
    }
    static void budget(Path transactions) throws IOException {
        int count = 0, directories = 0;
        try (var stream = Files.newDirectoryStream(transactions)) {
            for (Path path : stream) { require(++count <= 128, "Transaction inventory exceeds its bounded limit"); if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) directories++; }
        }
        require(directories < 32, "Retained transaction limit reached; recovery evidence needs explicit review");
    }
    static void journalBudget(Path transaction) throws IOException {
        int count = 0;
        try (var stream = Files.newDirectoryStream(transaction)) {
            for (Path path : stream) { require(++count <= 128, "Transaction file inventory exceeds its bound"); AddOnlyInstaller.regular(path); }
        }
    }
}
