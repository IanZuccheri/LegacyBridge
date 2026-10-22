package dev.legacybridge.product.install;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationLimits;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.workflow.BridgeWorkspace;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One explicit, add-only installation. The trusted session controller supplies BOTH engine
 * fingerprints; arbitrary JSON reports are not authority. Must run off the rendering thread.
 * A completed, forced private copy is hard-linked into mods without replacement, or refused.
 * No auto-recovery, activation, replacement, delete, classloading, download or restart occurs.
 * File contents are forced; Java/Windows cannot promise parent-directory fsync/power-loss
 * durability. Receipts allow process-crash inspection, not an invented universal crash guarantee.
 * At most 32 transaction directories are retained, including refused private payloads. These
 * are recovery evidence, not anonymous temporaries; further installs require explicit review.
 * Cooperative file/JVM locks cannot sandbox another process that replaces trusted directories.
 */
public final class AddOnlyInstaller {
    private static final long MAX_JAR_BYTES = 64L << 20;
    private static final String DURABILITY = "FILE_CONTENTS_FORCED_DIRECTORY_FSYNC_NOT_GUARANTEED";
    private final BoundaryHook hook;
    public AddOnlyInstaller() { this(boundary -> { }); }
    AddOnlyInstaller(BoundaryHook hook) { this.hook = hook; }
    enum Boundary { PREPARED, PUBLISHED }
    @FunctionalInterface interface BoundaryHook { void reached(Boundary boundary) throws IOException; }

    public InstallResult apply(Path gameDir, Path stagedJar, String expectedSha256, Path original,
                               String expectedOriginalSha256, Set<String> loadedModIds) throws InstallException {
        Path transaction = null; Path installed = null; boolean published = false;
        var control = new OperationControl(new OperationLimits(MAX_JAR_BYTES, 512L << 20, 256L << 20, Duration.ofMinutes(3)), progress -> { });
        try (var scope = control.enter()) {
            digest(expectedSha256); digest(expectedOriginalSha256);
            Set<String> loaded = Set.copyOf(loadedModIds);
            BridgeWorkspace workspace = BridgeWorkspace.inspect(gameDir);
            requireDirectory(workspace.root()); requireDirectory(workspace.staging()); requireDirectory(workspace.input());
            requireDirectory(workspace.transactions()); requireDirectory(workspace.mods());
            Path staged = descendant(stagedJar, workspace.staging());
            Path source = descendant(original, workspace.input());
            StagedArtifactValidator.require(!Files.isSameFile(staged, source), "Staged output aliases original");
            StagedArtifactValidator.require(hash(staged).equals(expectedSha256), "Staged hash differs from session result");
            StagedArtifactValidator.require(hash(source).equals(expectedOriginalSha256), "Original changed since conversion");
            try (InstallationLock held = InstallationLock.acquire(workspace.transactions())) {
                workspace.revalidate();
                transactionBudget(workspace.transactions());
                transaction = workspace.transactions().resolve(UUID.randomUUID().toString());
                Files.createDirectory(transaction); requireDirectory(transaction);
                Path payload = transaction.resolve("payload.jar");
                StagedArtifactValidator.require(Files.getFileStore(transaction).getUsableSpace() >= Files.size(staged) + (1L << 20), "Insufficient space for private installation payload");
                copyForced(staged, payload);
                var candidate = StagedArtifactValidator.validate(payload, expectedSha256);
                String modId = candidate.modId();
                InstalledModInventory.Snapshot inventory = InstalledModInventory.read(workspace.mods(), loaded, candidate);
                installed = workspace.mods().resolve("legacybridge-" + modId + "-" + transaction.getFileName() + ".jar");
                Map<String, Object> receipt = receipt(transaction, installed, expectedSha256, expectedOriginalSha256, modId);
                writeForced(transaction.resolve("prepared.json"), receipt);
                hook.reached(Boundary.PREPARED);
                workspace.revalidate(); requireDirectory(transaction); regular(payload);
                descendant(staged, workspace.staging()); descendant(source, workspace.input());
                StagedArtifactValidator.require(hash(payload).equals(expectedSha256) && hash(staged).equals(expectedSha256)
                        && hash(source).equals(expectedOriginalSha256), "Source/staged/payload changed before publication");
                InstalledModInventory.unchanged(workspace.mods(), inventory);
                StagedArtifactValidator.require(Files.getFileStore(payload).equals(Files.getFileStore(workspace.mods())), "Hard-link installation requires one filesystem");
                OperationControl.beginPublication();
                try { Files.createLink(installed, payload); }
                catch (UnsupportedOperationException unavailable) { throw new IOException("Atomic add-only hard-link publication is unavailable", unavailable); }
                published = true;
                hook.reached(Boundary.PUBLISHED);
                StagedArtifactValidator.require(Files.isSameFile(installed, payload) && hash(installed).equals(expectedSha256), "Published file identity changed");
                writeForced(transaction.resolve("committed.json"), receipt);
                return new InstallResult(installed, transaction, expectedSha256, List.of(modId));
            }
        } catch (InstallException known) {
            throw known;
        } catch (Exception failure) {
            throw failure(published ? "PUBLISHED_RECEIPT_INCOMPLETE" : "INSTALL_REFUSED",
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(),
                    published ? installed : null, transaction);
        }
    }

    /** Read-only receipt inspection. Never retries publication or rolls back a potentially loaded file. */
    public InstallResult inspect(Path gameDir, Path transactionDirectory) throws InstallException {
        Path transaction = null; Path installed = null;
        try {
            BridgeWorkspace workspace = BridgeWorkspace.inspect(gameDir);
            transaction = transactionDirectory.toAbsolutePath().normalize();
            StagedArtifactValidator.require(transaction.getParent().equals(workspace.transactions()) && uuid(transaction.getFileName().toString()), "Invalid transaction identity");
            requireDirectory(transaction);
            Path prepared = transaction.resolve("prepared.json"); regular(prepared);
            byte[] receiptBytes;
            try (var input = Files.newInputStream(prepared, LinkOption.NOFOLLOW_LINKS)) { receiptBytes = input.readNBytes(16_385); }
            StagedArtifactValidator.require(receiptBytes.length <= 16_384, "Oversized prepared receipt");
            Object parsed = BoundedJsonParser.parse(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(ByteBuffer.wrap(receiptBytes)).toString());
            StagedArtifactValidator.require(parsed instanceof Map<?, ?>, "Malformed prepared receipt");
            Map<?, ?> receipt = (Map<?, ?>) parsed;
            StagedArtifactValidator.require(receipt.keySet().equals(Set.of("schemaVersion", "reportType", "transactionId", "state", "installedFile", "sha256", "originalSha256", "modIds", "durability"))
                    && new java.math.BigDecimal("1").equals(receipt.get("schemaVersion")) && "LEGACY_INSTALL_TRANSACTION".equals(receipt.get("reportType"))
                    && transaction.getFileName().toString().equals(receipt.get("transactionId"))
                    && "PREPARED_ADD_ONLY".equals(receipt.get("state")) && DURABILITY.equals(receipt.get("durability"))
                    && receipt.get("modIds") instanceof List<?> ids && ids.size() == 1 && ids.get(0) instanceof String,
                    "Unknown prepared receipt schema or identity");
            String sha = (String) receipt.get("sha256"); digest(sha);
            digest((String) receipt.get("originalSha256"));
            String modId = (String) ((List<?>) receipt.get("modIds")).get(0);
            StagedArtifactValidator.require(modId.matches("[a-z][a-z0-9_]{1,63}"), "Invalid receipt mod ID");
            String name = "legacybridge-" + modId + "-" + transaction.getFileName() + ".jar";
            StagedArtifactValidator.require(name.equals(receipt.get("installedFile")), "Receipt destination is not operation-owned");
            installed = workspace.mods().resolve(name);
            Path payload = transaction.resolve("payload.jar"); regular(payload);
            if (!Files.exists(installed, LinkOption.NOFOLLOW_LINKS)) throw failure("NOT_PUBLISHED", "No complete destination exists; no automatic installation was attempted", null, transaction);
            regular(installed);
            StagedArtifactValidator.require(Files.isSameFile(payload, installed) && hash(installed).equals(sha), "Receipt does not prove installed identity/hash");
            StagedArtifactValidator.require(StagedArtifactValidator.validate(payload, sha).modId().equals(modId), "Receipt mod ID differs from the installed payload");
            return new InstallResult(installed, transaction, sha, List.of(modId));
        } catch (InstallException known) { throw known; }
        catch (Exception failure) { throw failure("RECOVERY_REQUIRED", "Receipt inspection could not prove completion: " + failure.getMessage(), null, transaction); }
    }

    private static Map<String, Object> receipt(Path transaction, Path installed, String sha, String sourceSha, String modId) {
        return new TreeMap<>(Map.of("schemaVersion", 1, "reportType", "LEGACY_INSTALL_TRANSACTION",
                "transactionId", transaction.getFileName().toString(), "state", "PREPARED_ADD_ONLY",
                "installedFile", installed.getFileName().toString(), "sha256", sha, "originalSha256", sourceSha,
                "modIds", List.of(modId), "durability", DURABILITY));
    }
    private static void writeForced(Path path, Map<String, Object> receipt) throws IOException {
        Map<String, Object> record = new TreeMap<>(receipt);
        if (path.getFileName().toString().equals("committed.json")) record.put("state", "PENDING_RESTART");
        byte[] bytes = new CanonicalReportWriter().toJsonTree(record).getBytes(StandardCharsets.UTF_8);
        try (FileChannel file = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) file.write(buffer); file.force(true);
        }
    }
    private static void transactionBudget(Path directory) throws IOException {
        int directories = 0; int entries = 0;
        try (var files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                StagedArtifactValidator.require(++entries <= 128, "Transaction inventory exceeds its bounded review limit");
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) directories++;
            }
        }
        StagedArtifactValidator.require(directories < 32, "Retained transaction limit reached; inspect recovery evidence before further installation");
    }
    private static void copyForced(Path source, Path target) throws IOException {
        try (var input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS);
             var output = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = new byte[65536]; long total = 0;
            for (int count; (count = input.read(bytes)) != -1;) {
                OperationControl.checkpoint(); StagedArtifactValidator.require((total += count) <= MAX_JAR_BYTES, "Input exceeds installation limit");
                ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, count); while (buffer.hasRemaining()) output.write(buffer);
            }
            output.force(true);
        }
    }
    static String hash(Path path) throws IOException {
        regular(path);
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            MessageDigest digest = sha256(); byte[] bytes = new byte[65536]; long total = 0;
            for (int count; (count = input.read(bytes)) != -1;) {
                OperationControl.checkpoint(); StagedArtifactValidator.require((total += count) <= MAX_JAR_BYTES, "JAR exceeds 64 MiB installation limit"); digest.update(bytes, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
    }
    static String hash(byte[] bytes) { return HexFormat.of().formatHex(sha256().digest(bytes)); }
    private static MessageDigest sha256() { try { return MessageDigest.getInstance("SHA-256"); } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    static void regular(Path path) throws IOException { StagedArtifactValidator.require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) && path.equals(path.toRealPath()), "Expected real regular file: " + path.getFileName()); }
    private static void requireDirectory(Path path) throws IOException { StagedArtifactValidator.require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) && path.equals(path.toRealPath()), "Expected real directory: " + path.getFileName()); }
    private static Path descendant(Path path, Path root) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        StagedArtifactValidator.require(normalized.startsWith(root) && !normalized.equals(root), "Path lies outside its owned workspace area");
        for (Path parent = normalized.getParent(); parent != null && parent.startsWith(root); parent = parent.getParent()) requireDirectory(parent);
        regular(normalized); return normalized;
    }
    private static void digest(String digest) throws IOException { StagedArtifactValidator.require(digest != null && digest.matches("[a-f0-9]{64}"), "Expected fingerprint from the completed session engine result"); }
    private static boolean uuid(String value) { try { return UUID.fromString(value).toString().equals(value); } catch (IllegalArgumentException invalid) { return false; } }
    private static InstallException failure(String code, String message, Path installed, Path transaction) {
        return new InstallException(code, message, installed == null
                ? "No installation was authorized by this failure. Keep originals; inspect the transaction and correct the preflight issue."
                : "A complete JAR was added. Pending restart, not activation; inspect its transaction without deleting or reinstalling it.",
                Optional.ofNullable(installed), Optional.ofNullable(transaction));
    }
}
