package dev.legacybridge.converter.archive;

import dev.legacybridge.converter.report.ArchiveSummary;
import dev.legacybridge.converter.report.InputFingerprint;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Reads untrusted JARs without extracting or loading any contained class. */
public final class SafeJarReader {
    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

    public ArchiveScan scan(Path input, JarEntryVisitor visitor) throws InputRejectedException {
        OperationControl.checkpoint();
        Path normalizedInput = validateInput(input);
        size(normalizedInput);
        try (InputSnapshot snapshot = snapshotInput(normalizedInput)) {
            return scanSnapshot(normalizedInput, snapshot, visitor);
        }
    }

    private ArchiveScan scanSnapshot(
            Path normalizedInput,
            InputSnapshot snapshot,
            JarEntryVisitor visitor
    ) throws InputRejectedException {
        long inputSize = snapshot.sizeBytes();
        ZipCentralDirectoryPreflight.Summary centralDirectory = preflight(snapshot.path());
        OperationControl.stage(OperationStage.ARCHIVE_SCAN, "Scanning private input snapshot");
        InputFingerprint fingerprint = new InputFingerprint(
                normalizedInput.getFileName().toString(),
                inputSize,
                snapshot.sha256()
        );

        int entryCount = 0;
        int mixinConfigurationCount = 0;
        long totalUncompressed = 0;
        long totalEntryNameChars = 0;
        boolean signed = false;
        boolean multiRelease = false;
        SpecialEntryAccumulator specialEntries = new SpecialEntryAccumulator();
        Set<String> exactNames = new HashSet<>();
        Map<String, String> canonicalNames = new HashMap<>();

        try (ZipFile zip = new ZipFile(snapshot.path().toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                OperationControl.checkpoint();
                ZipEntry entry = entries.nextElement();
                entryCount++;
                OperationControl.progress(OperationStage.ARCHIVE_SCAN, entryCount, -1, "Inspecting archive entry");
                if (entryCount > ArchivePolicy.MAX_ENTRY_COUNT) {
                    throw reject("ENTRY_COUNT_LIMIT", "Archive contains too many entries");
                }

                String rawName = entry.getName();
                if (rawName != null) {
                    totalEntryNameChars = Math.addExact(totalEntryNameChars, rawName.length());
                    if (totalEntryNameChars > ArchivePolicy.MAX_TOTAL_ENTRY_NAME_CHARS) {
                        throw reject("ENTRY_NAME_BUDGET", "Archive entry names exceed the aggregate policy limit");
                    }
                }
                String name = validateEntryName(rawName, exactNames, canonicalNames);
                EnumSet<SpecialEntryKind> specialKinds = SpecialEntryKind.classify(name);
                if (!entry.isDirectory() && specialKinds.contains(SpecialEntryKind.MIXIN_CONFIGURATION)) {
                    mixinConfigurationCount = Math.incrementExact(mixinConfigurationCount);
                    if (mixinConfigurationCount > ArchivePolicy.MAX_MIXIN_CONFIGURATION_COUNT) {
                        throw reject("MIXIN_CONFIG_COUNT_LIMIT",
                                "Archive contains too many Mixin configuration candidates");
                    }
                }
                visitor.visitEntry(name, entry.isDirectory());
                specialEntries.record(name, specialKinds);
                signed |= specialKinds.contains(SpecialEntryKind.SIGNATURE);
                multiRelease |= specialKinds.contains(SpecialEntryKind.MULTI_RELEASE);

                if (entry.isDirectory()) {
                    continue;
                }

                long declaredSize = entry.getSize();
                boolean visitorCapture = visitor.requiresBytes(name);
                long visitorLimit = visitorCapture ? visitor.maxCapturedBytes(name) : Long.MAX_VALUE;
                if (visitorLimit < 0) {
                    throw reject("VISITOR_LIMIT_INVALID", "Analyzer supplied an invalid capture limit");
                }
                long perEntryLimit = Math.min(perEntryLimit(name), visitorLimit);
                if (declaredSize > perEntryLimit) {
                    throw reject("ENTRY_SIZE_LIMIT", "Entry exceeds policy limit: " + name);
                }

                boolean capture = visitorCapture || MANIFEST_PATH.equals(name);
                EntryBytes read = readEntry(zip, entry, name, perEntryLimit, totalUncompressed, capture);
                totalUncompressed += read.size();
                if (totalUncompressed > ArchivePolicy.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw reject("TOTAL_SIZE_LIMIT", "Archive exceeds total uncompressed size policy");
                }
                validateCompressionRatio(entry, read.size(), name);

                if (MANIFEST_PATH.equals(name) && read.bytes() != null) {
                    multiRelease |= isMultiRelease(read.bytes());
                }
                if (visitorCapture) {
                    visitor.visit(name, read.bytes());
                }
            }
            if (entryCount != centralDirectory.entryCount()) {
                throw reject("CENTRAL_DIRECTORY_MISMATCH",
                        "ZIP entry enumeration differs from the validated central directory");
            }
        } catch (InputRejectedException exception) {
            throw exception;
        } catch (ZipException exception) {
            throw new InputRejectedException("MALFORMED_ZIP", "Input is not a valid JAR/ZIP archive", exception);
        } catch (IOException | SecurityException exception) {
            throw new InputRejectedException("ARCHIVE_READ_FAILED", "Could not read the input archive", exception);
        }

        if (size(normalizedInput) != inputSize || !fingerprint.sha256().equals(sha256(normalizedInput))) {
            throw reject("INPUT_CHANGED", "Input changed while it was being analyzed");
        }

        return new ArchiveScan(
                fingerprint,
                new ArchiveSummary(
                        entryCount,
                        totalUncompressed,
                        signed,
                        multiRelease,
                        specialEntries.count(),
                        specialEntries.truncated(),
                        specialEntries.samples(),
                        specialEntries.counts()
                )
        );
    }

    private static InputSnapshot snapshotInput(Path input) throws InputRejectedException {
        OperationControl.stage(OperationStage.INPUT_SNAPSHOT, "Reading an inert private input snapshot");
        Path snapshot = null;
        try {
            snapshot = Files.createTempFile("legacybridge-input-", ".jar");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long count = 0;
            byte[] buffer = new byte[64 * 1024];
            try (InputStream source = Files.newInputStream(
                    input,
                    StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS
            );
                 OutputStream target = Files.newOutputStream(
                         snapshot,
                         StandardOpenOption.WRITE,
                         StandardOpenOption.TRUNCATE_EXISTING
                 )) {
                int read;
                while ((read = source.read(buffer)) != -1) {
                    count += read;
                    OperationControl.inputBytes(count);
                    OperationControl.progress(OperationStage.INPUT_SNAPSHOT, count, -1, "Snapshot bytes read");
                    if (count > ArchivePolicy.MAX_INPUT_BYTES) {
                        throw reject("INPUT_SIZE_LIMIT", "Input exceeds the compressed-size policy");
                    }
                    digest.update(buffer, 0, read);
                    target.write(buffer, 0, read);
                }
            }
            return new InputSnapshot(snapshot, count, HexFormat.of().formatHex(digest.digest()));
        } catch (InputRejectedException exception) {
            deleteFailedSnapshot(snapshot);
            throw exception;
        } catch (IOException | SecurityException exception) {
            deleteFailedSnapshot(snapshot);
            throw new InputRejectedException(
                    "INPUT_SNAPSHOT_FAILED",
                    "Could not create a stable private snapshot of the input",
                    exception
            );
        } catch (NoSuchAlgorithmException exception) {
            deleteFailedSnapshot(snapshot);
            throw new IllegalStateException("Every Java 17 runtime must provide SHA-256", exception);
        } catch (RuntimeException | Error interrupted) {
            deleteFailedSnapshot(snapshot);
            throw interrupted;
        }
    }

    private static void deleteFailedSnapshot(Path snapshot) {
        if (snapshot == null) {
            return;
        }
        try {
            Files.deleteIfExists(snapshot);
        } catch (IOException ignored) {
            snapshot.toFile().deleteOnExit();
        }
    }

    private static ZipCentralDirectoryPreflight.Summary preflight(Path input)
            throws InputRejectedException {
        try {
            return ZipCentralDirectoryPreflight.inspect(input);
        } catch (InputRejectedException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw new InputRejectedException(
                    "ARCHIVE_PREFLIGHT_FAILED",
                    "Could not validate the ZIP central directory",
                    exception
            );
        }
    }

    private static Path validateInput(Path input) throws InputRejectedException {
        if (input == null) {
            throw reject("INPUT_MISSING", "Input path is required");
        }
        Path normalized = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
            throw reject("INPUT_NOT_REGULAR", "Input must be a readable regular file, not a symlink");
        }
        if (!Files.isReadable(normalized)) {
            throw reject("INPUT_NOT_READABLE", "Input is not readable");
        }
        String fileName = normalized.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw reject("INPUT_NOT_JAR", "Input file must use the .jar extension");
        }
        if (fileName.codePoints().anyMatch(SafeJarReader::isUnsafeNameCodePoint)) {
            throw reject("UNSAFE_FILE_NAME", "Input filename contains control or formatting characters");
        }
        return normalized;
    }

    private static long size(Path input) throws InputRejectedException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    input,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );
            if (!attributes.isRegularFile() || Files.isSymbolicLink(input)) {
                throw reject("INPUT_NOT_REGULAR", "Input changed to a non-regular file or symlink");
            }
            long size = attributes.size();
            OperationControl.inputBytes(size);
            if (size > ArchivePolicy.MAX_INPUT_BYTES) {
                throw reject("INPUT_SIZE_LIMIT", "Input exceeds the compressed-size policy");
            }
            return size;
        } catch (IOException exception) {
            throw new InputRejectedException("INPUT_STAT_FAILED", "Could not inspect the input file", exception);
        }
    }

    private static String sha256(Path input) throws InputRejectedException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream stream = new DigestInputStream(
                    Files.newInputStream(input, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS),
                    digest
            )) {
                long count = 0;
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    count += read;
                    OperationControl.inputBytes(count);
                    if (count > ArchivePolicy.MAX_INPUT_BYTES) {
                        throw reject("INPUT_SIZE_LIMIT",
                                "Input exceeds the compressed-size policy while hashing");
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new InputRejectedException("INPUT_HASH_FAILED", "Could not hash the input file", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Every Java 17 runtime must provide SHA-256", exception);
        }
    }

    private static String validateEntryName(
            String name,
            Set<String> exactNames,
            Map<String, String> canonicalNames
    ) throws InputRejectedException {
        if (name == null || name.isBlank() || name.length() > ArchivePolicy.MAX_ENTRY_NAME_CHARS) {
            throw reject("UNSAFE_ENTRY_NAME", "Archive contains an empty or oversized entry name");
        }
        if (name.codePoints().anyMatch(SafeJarReader::isUnsafeNameCodePoint)
                || name.indexOf('\\') >= 0 || name.startsWith("/")
                || name.matches("^[A-Za-z]:.*")) {
            throw reject("UNSAFE_ENTRY_NAME", "Archive contains an absolute or ambiguous entry path");
        }

        String withoutTrailingSlash = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        if (withoutTrailingSlash.isBlank() || withoutTrailingSlash.contains("//")) {
            throw reject("UNSAFE_ENTRY_NAME", "Archive contains an ambiguous entry path");
        }
        for (String segment : withoutTrailingSlash.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw reject("PATH_TRAVERSAL", "Archive contains a non-canonical entry path");
            }
        }

        if (!exactNames.add(name)) {
            throw reject("DUPLICATE_ENTRY", "Archive contains a duplicate entry: " + name);
        }
        String canonical = Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        String previous = canonicalNames.putIfAbsent(canonical, name);
        if (previous != null && !previous.equals(name)) {
            throw reject("ENTRY_NAME_COLLISION", "Archive entries collide after case/Unicode normalization");
        }
        return name;
    }

    private static boolean isUnsafeNameCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static EntryBytes readEntry(
            ZipFile zip,
            ZipEntry entry,
            String name,
            long perEntryLimit,
            long currentTotal,
            boolean capture
    ) throws IOException, InputRejectedException {
        int initialCapacity = capture && entry.getSize() > 0
                ? (int) Math.min(entry.getSize(), 64 * 1024)
                : 0;
        ByteArrayOutputStream output = capture ? new ByteArrayOutputStream(initialCapacity) : null;
        CRC32 crc = new CRC32();
        long count = 0;
        byte[] buffer = new byte[16 * 1024];

        try (InputStream stream = zip.getInputStream(entry)) {
            int read;
            while ((read = stream.read(buffer)) != -1) {
                count += read;
                OperationControl.inflatedBytes(read);
                if (count > perEntryLimit) {
                    throw reject("ENTRY_SIZE_LIMIT", "Entry exceeds policy limit while reading: " + name);
                }
                if (currentTotal + count > ArchivePolicy.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw reject("TOTAL_SIZE_LIMIT", "Archive exceeds total uncompressed size policy");
                }
                crc.update(buffer, 0, read);
                if (capture) {
                    OperationControl.capturedBytes(read);
                    output.write(buffer, 0, read);
                }
                OperationControl.progress(OperationStage.ARCHIVE_SCAN, currentTotal + count, -1, "Inflated bytes inspected");
            }
        }

        if (entry.getSize() >= 0 && entry.getSize() != count) {
            throw reject("ENTRY_SIZE_MISMATCH", "Entry size differs from its central-directory value: " + name);
        }
        if (entry.getCrc() >= 0 && entry.getCrc() != crc.getValue()) {
            throw reject("ENTRY_CRC_MISMATCH", "Entry CRC validation failed: " + name);
        }
        return new EntryBytes(count, capture ? output.toByteArray() : null);
    }

    private static long perEntryLimit(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (name.endsWith(".class")) {
            return ArchivePolicy.MAX_CLASSFILE_BYTES;
        }
        if (lower.equals("meta-inf/mods.toml") || lower.equals("meta-inf/manifest.mf")
                || ArchiveEntryClassifier.isMixinConfiguration(name)
                || lower.equals("meta-inf/coremods.json")) {
            return ArchivePolicy.MAX_METADATA_BYTES;
        }
        return ArchivePolicy.MAX_ENTRY_BYTES;
    }

    private static void validateCompressionRatio(ZipEntry entry, long actualSize, String name)
            throws InputRejectedException {
        long compressedSize = entry.getCompressedSize();
        if (actualSize >= ArchivePolicy.MIN_RATIO_CHECK_BYTES && compressedSize == 0) {
            throw reject("COMPRESSION_RATIO_LIMIT", "Entry has an unsafe compression ratio: " + name);
        }
        if (actualSize >= ArchivePolicy.MIN_RATIO_CHECK_BYTES && compressedSize > 0
                && compressedSize <= Long.MAX_VALUE / ArchivePolicy.MAX_COMPRESSION_RATIO
                && actualSize > compressedSize * ArchivePolicy.MAX_COMPRESSION_RATIO) {
            throw reject("COMPRESSION_RATIO_LIMIT", "Entry has an unsafe compression ratio: " + name);
        }
    }

    private static boolean isMultiRelease(byte[] manifestBytes) {
        try {
            Manifest manifest = new Manifest(new ByteArrayInputStream(manifestBytes));
            return "true".equalsIgnoreCase(manifest.getMainAttributes().getValue(Attributes.Name.MULTI_RELEASE));
        } catch (IOException ignored) {
            return false;
        }
    }

    private static InputRejectedException reject(String code, String message) {
        return new InputRejectedException(code, message);
    }

    private record EntryBytes(long size, byte[] bytes) {
    }

    private record InputSnapshot(Path path, long sizeBytes, String sha256) implements AutoCloseable {
        @Override
        public void close() {
            deleteFailedSnapshot(path);
        }
    }

    private enum SpecialEntryKind {
        SIGNATURE,
        ACCESS_TRANSFORMER,
        COREMOD,
        TRANSFORMATION_SERVICE,
        SERVICE_PROVIDER,
        MIXIN_CONFIGURATION,
        NESTED_JAR,
        NATIVE_LIBRARY,
        MODULE_DESCRIPTOR,
        MULTI_RELEASE;

        private static EnumSet<SpecialEntryKind> classify(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            String upper = name.toUpperCase(Locale.ROOT);
            EnumSet<SpecialEntryKind> kinds = EnumSet.noneOf(SpecialEntryKind.class);
            if (upper.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)")) {
                kinds.add(SIGNATURE);
            }
            if (name.equals("META-INF/accesstransformer.cfg")) {
                kinds.add(ACCESS_TRANSFORMER);
            }
            if (name.equals("META-INF/coremods.json")) {
                kinds.add(COREMOD);
            }
            if (name.equals("META-INF/services/cpw.mods.modlauncher.api.ITransformationService")) {
                kinds.add(TRANSFORMATION_SERVICE);
            } else if (name.startsWith("META-INF/services/")) {
                kinds.add(SERVICE_PROVIDER);
            }
            if (ArchiveEntryClassifier.isMixinConfiguration(name)) {
                kinds.add(MIXIN_CONFIGURATION);
            }
            if (lower.endsWith(".jar")) {
                kinds.add(NESTED_JAR);
            }
            if (lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib")) {
                kinds.add(NATIVE_LIBRARY);
            }
            if (name.equals("module-info.class")) {
                kinds.add(MODULE_DESCRIPTOR);
            }
            if (name.startsWith("META-INF/versions/")) {
                kinds.add(MULTI_RELEASE);
            }
            return kinds;
        }
    }

    /** Keeps only the lexicographically first names for each risk category. */
    private static final class SpecialEntryAccumulator {
        private final Map<SpecialEntryKind, TreeSet<String>> samples =
                new EnumMap<>(SpecialEntryKind.class);
        private final Map<SpecialEntryKind, Long> counts = new EnumMap<>(SpecialEntryKind.class);
        private int count;

        private void record(String name, EnumSet<SpecialEntryKind> kinds) {
            if (kinds.isEmpty()) {
                return;
            }
            count = Math.addExact(count, 1);
            for (SpecialEntryKind kind : kinds) {
                counts.merge(kind, 1L, Math::addExact);
                TreeSet<String> retained = samples.computeIfAbsent(kind, ignored -> new TreeSet<>());
                retained.add(name);
                if (retained.size() > ArchivePolicy.MAX_SPECIAL_ENTRY_SAMPLES_PER_KIND) {
                    retained.pollLast();
                }
            }
        }

        private int count() {
            return count;
        }

        private List<String> samples() {
            TreeSet<String> combined = new TreeSet<>();
            samples.values().forEach(combined::addAll);
            return List.copyOf(combined);
        }

        private boolean truncated() {
            return count > samples().size();
        }

        private Map<String, Long> counts() {
            Map<String, Long> result = new TreeMap<>();
            counts.forEach((kind, value) -> result.put(kind.name(), value));
            return Map.copyOf(result);
        }
    }

}
