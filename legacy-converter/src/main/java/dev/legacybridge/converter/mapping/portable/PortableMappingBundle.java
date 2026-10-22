package dev.legacybridge.converter.mapping.portable;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.mapping.source.MappingSource;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStage;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import net.minecraftforge.srgutils.IMappingFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Explicit, offline local export/import of inert mapping and API declarations.
 * No cache discovery, downloads, Java serialization, class loading or bytecode.
 *
 * <p>Files and counts are bounded. SHA-256 detects corruption, not publisher
 * authenticity: a user-selected bundle is trusted local development data.
 * Export does not grant redistribution rights. See PORTABLE_MAPPING_DATA.md.</p>
 */
public final class PortableMappingBundle {
    public static final int SCHEMA_VERSION = 1;
    public static final String PROFILE = "simple-content-v1";
    public static final String DISTRIBUTION_POLICY = "LOCAL_USE_ONLY_NOT_APPROVED_FOR_REDISTRIBUTION";
    public static final String MANIFEST = "manifest.json";
    static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 192L * 1024 * 1024;
    static final int MAX_MANIFEST_BYTES = 256 * 1024;
    static final List<String> FILES = List.of("legacy-official.lbmap", "target-official.lbmap",
            "legacy-srg.lbmap", "target-srg.lbmap", "legacy-api.lbapi", "target-api.lbapi");
    private static final Map<String, String> BASELINES = Map.of(
            "legacyMinecraft", "1.16.5", "legacyForge", "36.2.42",
            "targetMinecraft", "1.20.1", "targetForge", "47.4.23", "targetJava", "17");
    private static final Map<String, String> NAMESPACES = Map.of(
            "official", "MOJANG_TO_OBFUSCATED",
            "legacySrg", "OBFUSCATED_TO_LEGACY_MCP_SRG",
            "targetSrg", "OBFUSCATED_TO_TARGET_INTERMEDIATE",
            "legacyApi", "LEGACY_MCP_CLASSES_WITH_MOJANG_MEMBERS",
            "targetApi", "MOJANG_CLASSES_AND_MEMBERS",
            "output", "MOJANG_CLASS_NAMES_WITH_SRG_MEMBERS");
    private static final Set<String> REQUIRED_SOURCES = Set.of("legacy.clientMappings", "legacy.serverMappings",
            "legacy.srg", "target.clientMappings", "target.serverMappings", "target.srg",
            "legacy.apiJars[0]", "target.apiJars[0]");
    private static final Set<String> ROOT_KEYS = Set.of("schemaVersion", "bundleType", "profile",
            "baselines", "namespaces", "distributionPolicy", "complete", "sources", "diagnostics", "files");

    private PortableMappingBundle() { }

    /**
     * Exports to a new directory under an existing real parent. No file is
     * replaced. A sibling temporary directory is published by a non-replacing
     * move only after all payloads and the manifest have been closed.
     * The caller must own the parent; this is not an adversarial filesystem sandbox.
     */
    public static Path export(MappingInputs inputs, Path newBundleDirectory) throws IOException {
        Objects.requireNonNull(inputs, "inputs");
        requireComplete(inputs);
        Path target = normalized(newBundleDirectory);
        Path parent = target.getParent();
        if (parent == null) throw new IOException("Mapping bundle cannot use a filesystem root");
        requireRealDirectory(parent);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Mapping bundle output already exists; select a new directory");
        }
        checkpoint();
        Path temporary = Files.createTempDirectory(parent, ".legacybridge-mappings-");
        try {
            List<IMappingFile> mappings = List.of(inputs.legacyOfficial(), inputs.targetOfficial(),
                    inputs.legacySrg(), inputs.targetSrg());
            List<Map<String, Object>> payloads = new ArrayList<>();
            long bytes = 0;
            for (int index = 0; index < FILES.size(); index++) {
                checkpoint();
                OperationControl.progress(OperationStage.SERIALIZATION, index, FILES.size(), "Exporting mapping index " + FILES.get(index));
                Path file = temporary.resolve(FILES.get(index));
                if (index < 4) PortableIndexCodec.writeMappings(file, mappings.get(index));
                else PortableIndexCodec.writeApi(file, index == 4 ? inputs.legacyApi() : inputs.targetApi());
                long length = Files.size(file);
                bytes += length;
                if (bytes > MAX_TOTAL_BYTES) throw new IOException("Mapping bundle exceeds total byte limit");
                payloads.add(Map.of("name", FILES.get(index), "bytes", length, "sha256", hash(file, MAX_FILE_BYTES)));
            }
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("schemaVersion", SCHEMA_VERSION);
            manifest.put("bundleType", "LEGACYBRIDGE_LOCAL_MAPPING_DATA");
            manifest.put("profile", PROFILE);
            manifest.put("baselines", BASELINES);
            manifest.put("namespaces", NAMESPACES);
            manifest.put("distributionPolicy", DISTRIBUTION_POLICY);
            manifest.put("complete", true);
            manifest.put("sources", inputs.sources().stream().map(source -> Map.<String, Object>of(
                    "id", source.id(), "sha256", source.sha256(), "origin", source.origin())).toList());
            manifest.put("diagnostics", inputs.diagnostics());
            manifest.put("files", payloads);
            byte[] manifestBytes = new CanonicalReportWriter().toJsonTree(sortedObject(manifest)).getBytes(StandardCharsets.UTF_8);
            if (manifestBytes.length > MAX_MANIFEST_BYTES) throw new IOException("Mapping manifest exceeds byte limit");
            Files.write(temporary.resolve(MANIFEST), manifestBytes, StandardOpenOption.CREATE_NEW);
            checkpoint();
            requireRealDirectory(parent);
            requireRealDirectory(temporary);
            OperationControl.stage(OperationStage.PRE_PUBLICATION, "Complete local mapping bundle ready for publication");
            // ATOMIC_MOVE may replace an existing destination on some providers.
            // The ordinary same-parent move preserves the no-replacement contract.
            OperationControl.beginPublication();
            Files.move(temporary, target);
            return target;
        } catch (IOException | RuntimeException | Error failure) {
            try { cleanupOwned(temporary); }
            catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    /** Loads only the explicitly selected bundle. Absence is an error, never a cache fallback. */
    public static MappingInputs load(Path bundleDirectory) throws IOException {
        return load(bundleDirectory, null);
    }

    /** Pins a previously reviewed manifest; payload digests and declarations are still fully revalidated. */
    public static MappingInputs load(Path bundleDirectory, String expectedManifestSha256) throws IOException {
        if (expectedManifestSha256 != null && !expectedManifestSha256.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Expected manifest SHA-256 must be lowercase hex");
        Path directory = normalized(bundleDirectory);
        requireRealDirectory(directory);
        requireExactFiles(directory);
        byte[] manifestBytes = readManifest(directory.resolve(MANIFEST));
        if (expectedManifestSha256 != null && !expectedManifestSha256.equals(HexFormat.of().formatHex(sha256().digest(manifestBytes))))
            throw new ChangedBundleException();
        Map<String, Object> manifest;
        try {
            manifest = object(BoundedJsonParser.parse(decode(manifestBytes)), "manifest");
        } catch (BoundedJsonParser.JsonSyntaxException malformed) {
            throw new IOException("Invalid mapping manifest JSON: " + malformed.getMessage(), malformed);
        }
        keys(manifest, ROOT_KEYS, "manifest");
        if (integer(manifest.get("schemaVersion"), "schemaVersion") != SCHEMA_VERSION) {
            throw new IOException("Unsupported mapping bundle schemaVersion");
        }
        equal(manifest.get("bundleType"), "LEGACYBRIDGE_LOCAL_MAPPING_DATA", "bundleType");
        equal(manifest.get("profile"), PROFILE, "profile");
        equal(manifest.get("distributionPolicy"), DISTRIBUTION_POLICY, "distributionPolicy");
        equal(manifest.get("complete"), Boolean.TRUE, "complete");
        exactStrings(manifest.get("baselines"), BASELINES, "baselines");
        exactStrings(manifest.get("namespaces"), NAMESPACES, "namespaces");
        List<MappingSource> sources = readSources(manifest.get("sources"));
        List<String> diagnostics = strings(manifest.get("diagnostics"), 64, "diagnostics");
        List<?> entries = array(manifest.get("files"), "files");
        if (entries.size() != FILES.size()) throw new IOException("Mapping bundle requires exactly six index files");
        Map<String, Payload> payloads = new LinkedHashMap<>();
        long total = 0;
        for (Object value : entries) {
            Map<String, Object> entry = object(value, "file");
            keys(entry, Set.of("name", "bytes", "sha256"), "file");
            String name = string(entry.get("name"), "file name");
            if (!FILES.contains(name) || payloads.containsKey(name)) {
                throw new IOException("Unknown, unsafe or duplicate mapping bundle file name");
            }
            long length = integer(entry.get("bytes"), "file bytes");
            String sha256 = string(entry.get("sha256"), "file SHA-256");
            if (length < 8 || length > MAX_FILE_BYTES || !sha256.matches("[a-f0-9]{64}")) {
                throw new IOException("Invalid mapping bundle file size or SHA-256");
            }
            total += length;
            if (total > MAX_TOTAL_BYTES) throw new IOException("Mapping bundle exceeds total byte limit");
            Path file = directory.resolve(name);
            if (regularFile(file, MAX_FILE_BYTES).size() != length) throw new IOException("Mapping bundle file size mismatch: " + name);
            payloads.put(name, new Payload(length, sha256));
        }
        // Verify every file before parsing any potentially expensive declarations.
        for (String name : FILES) {
            checkpoint();
            if (!hash(directory.resolve(name), MAX_FILE_BYTES).equals(payloads.get(name).sha256())) {
                throw new IOException("Mapping bundle SHA-256 mismatch: " + name);
            }
        }
        dev.legacybridge.converter.operation.HeapPressure.beforeMappingParse(total);
        List<IMappingFile> mappings = new ArrayList<>();
        List<ApiIndex> apis = new ArrayList<>();
        PortableIndexCodec.Budget budget = new PortableIndexCodec.Budget();
        for (int index = 0; index < FILES.size(); index++) {
            checkpoint();
            String name = FILES.get(index);
            OperationControl.progress(OperationStage.MAPPING_RESOLUTION, index, FILES.size(), "Reading local mapping index " + name);
            Payload payload = payloads.get(name);
            if (index < 4) mappings.add(PortableIndexCodec.readMappings(directory.resolve(name), payload.sha256(), budget));
            else apis.add(PortableIndexCodec.readApi(directory.resolve(name), payload.sha256(), budget));
        }
        requireRealDirectory(directory);
        requireExactFiles(directory);
        if (!MessageDigest.isEqual(manifestBytes, readManifest(directory.resolve(MANIFEST)))) {
            throw new IOException("Mapping manifest changed while loading");
        }
        MappingInputs result = new MappingInputs(mappings.get(0), mappings.get(1), mappings.get(2), mappings.get(3),
                apis.get(0), apis.get(1), sources, diagnostics, true);
        requireComplete(result);
        return result;
    }

    /** Fingerprint only, not a successful import. review/load must still validate every declaration. */
    public static String manifestFingerprint(Path selected) throws IOException {
        Path directory = normalized(selected);
        requireRealDirectory(directory); requireExactFiles(directory);
        return HexFormat.of().formatHex(sha256().digest(readManifest(directory.resolve(MANIFEST))));
    }

    public static final class ChangedBundleException extends IOException {
        public ChangedBundleException() { super("Selected mapping manifest changed since review; review the local data again. No fallback."); }
    }

    private static void requireComplete(MappingInputs inputs) throws IOException {
        if (!inputs.complete() || inputs.legacyOfficial().getClasses().isEmpty()
                || inputs.targetOfficial().getClasses().isEmpty() || inputs.legacySrg().getClasses().isEmpty()
                || inputs.targetSrg().getClasses().isEmpty() || inputs.legacyApi().classes().isEmpty()
                || inputs.targetApi().classes().isEmpty()) {
            throw new IOException("Portable mappings require complete nonempty local mapping and API inputs");
        }
        validateSources(inputs.sources());
        PortableIndexCodec.Budget budget = new PortableIndexCodec.Budget();
        for (IMappingFile mapping : List.of(inputs.legacyOfficial(), inputs.targetOfficial(), inputs.legacySrg(), inputs.targetSrg())) {
            budget.consume(mapping.getPackages().size());
            budget.consume(mapping.getClasses().size());
            for (IMappingFile.IClass type : mapping.getClasses()) {
                checkpoint();
                budget.consume(type.getFields().size());
                budget.consume(type.getMethods().size());
            }
        }
        for (ApiIndex api : List.of(inputs.legacyApi(), inputs.targetApi())) {
            budget.consume(api.classes().size());
            for (ApiIndex.ApiClass type : api.classes().values()) {
                checkpoint();
                budget.consume(type.interfaces().size());
                budget.consume(type.fields().size());
                budget.consume(type.methods().size());
            }
        }
        if (inputs.diagnostics().size() > 64) throw new IOException("Mapping diagnostics limit exceeded");
        for (String diagnostic : inputs.diagnostics()) {
            string(diagnostic, "diagnostic");
            if (diagnostic.startsWith("MISSING_INPUT:")) throw new IOException("Cannot export incomplete mapping inputs");
        }
    }

    private static List<MappingSource> readSources(Object value) throws IOException {
        List<?> entries = array(value, "sources");
        if (entries.size() > 64) throw new IOException("Mapping provenance limit exceeded");
        List<MappingSource> result = new ArrayList<>();
        for (Object element : entries) {
            Map<String, Object> source = object(element, "source");
            keys(source, Set.of("id", "sha256", "origin"), "source");
            try {
                result.add(new MappingSource(string(source.get("id"), "source id"),
                        string(source.get("sha256"), "source hash"), string(source.get("origin"), "source origin")));
            } catch (IllegalArgumentException malformed) {
                throw new IOException("Invalid mapping provenance", malformed);
            }
        }
        validateSources(result);
        return List.copyOf(result);
    }

    private static void validateSources(List<MappingSource> sources) throws IOException {
        Set<String> ids = new HashSet<>();
        if (sources.size() > 64) throw new IOException("Mapping provenance limit exceeded");
        for (MappingSource source : sources) {
            String id = string(source.id(), "source id");
            if (!id.matches("(?:legacy|target)\\.(?:clientMappings|serverMappings|srg|apiJars\\[[0-9]{1,2}\\])")
                    || !ids.add(id)) throw new IOException("Unknown or duplicate mapping source id");
            if (!Set.of("LOCAL_PROFILE", "PINNED_GRADLE_CACHE", "LOCAL_GRADLE_CACHE").contains(source.origin())) {
                throw new IOException("Unsupported mapping source origin");
            }
        }
        if (!ids.containsAll(REQUIRED_SOURCES)) throw new IOException("Mapping bundle source provenance is incomplete");
    }

    private static void requireExactFiles(Path directory) throws IOException {
        Set<String> expected = new HashSet<>(FILES);
        expected.add(MANIFEST);
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                if (!expected.remove(child.getFileName().toString())) throw new IOException("Unexpected mapping bundle entry");
                regularFile(child, child.getFileName().toString().equals(MANIFEST) ? MAX_MANIFEST_BYTES : MAX_FILE_BYTES);
            }
        }
        if (!expected.isEmpty()) throw new IOException("Mapping bundle is incomplete: missing " + expected.stream().sorted().toList());
    }

    static void checkpoint() throws InterruptedIOException {
        OperationControl.checkpoint();
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Portable mapping operation cancelled");
    }

    static BasicFileAttributes regularFile(Path file, long limit) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink() || !file.equals(file.toRealPath())) {
            throw new IOException("Mapping bundle entries must be real regular files without links or aliases");
        }
        if (attributes.size() > limit) throw new IOException("Mapping bundle entry exceeds byte limit");
        return attributes;
    }

    private static void requireRealDirectory(Path directory) throws IOException {
        // Check ancestors too, including a link that happens to resolve back inside the selected parent.
        Path current = directory.getRoot();
        if (current == null) throw new IOException("Mapping directory must be absolute");
        for (Path component : directory) {
            current = current.resolve(component);
            BasicFileAttributes attributes = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory() || attributes.isSymbolicLink() || !current.equals(current.toRealPath())) {
                throw new IOException("Mapping bundle path must use real directories without links or aliases");
            }
        }
    }

    private static Path normalized(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Path absolute = path.toAbsolutePath();
        if (!absolute.equals(absolute.normalize())) throw new IOException("Mapping bundle path must not contain traversal components");
        return absolute;
    }

    static String hash(Path file, long limit) throws IOException {
        regularFile(file, limit);
        MessageDigest digest = sha256();
        long total = 0;
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkpoint();
                total += count;
                if (total > limit) throw new IOException("Mapping bundle entry exceeds byte limit while reading");
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static byte[] readManifest(Path path) throws IOException {
        regularFile(path, MAX_MANIFEST_BYTES);
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_MANIFEST_BYTES + 1);
            if (bytes.length > MAX_MANIFEST_BYTES) throw new IOException("Mapping manifest exceeds byte limit");
            return bytes;
        }
    }

    static String decode(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) {
            throw new IOException("Mapping bundle contains invalid UTF-8", invalid);
        }
    }

    private static void cleanupOwned(Path directory) throws IOException {
        // Fixed direct children only; never follow a substituted link or recursively delete.
        requireRealDirectory(directory);
        for (String name : FILES) Files.deleteIfExists(directory.resolve(name));
        Files.deleteIfExists(directory.resolve(MANIFEST));
        Files.delete(directory);
    }

    private static Map<String, Object> object(Object value, String label) throws IOException {
        if (!(value instanceof Map<?, ?> map)) throw new IOException("Mapping " + label + " must be an object");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new IOException("Mapping object key is not a string");
            result.put(key, entry.getValue());
        }
        return result;
    }

    /** Map.of iteration order can vary between JVM launches; canonicalize every JSON object. */
    private static Map<String, Object> sortedObject(Map<?, ?> value) {
        Map<String, Object> result = new TreeMap<>();
        value.forEach((key, item) -> result.put((String) key, sortedValue(item)));
        return result;
    }

    private static Object sortedValue(Object value) {
        if (value instanceof Map<?, ?> object) return sortedObject(object);
        if (value instanceof List<?> list) return list.stream().map(PortableMappingBundle::sortedValue).toList();
        return value;
    }

    private static void keys(Map<String, Object> value, Set<String> expected, String label) throws IOException {
        if (!value.keySet().equals(expected)) throw new IOException("Mapping " + label + " has missing or unknown fields");
    }

    private static void exactStrings(Object value, Map<String, String> expected, String label) throws IOException {
        Map<String, Object> actual = object(value, label);
        keys(actual, expected.keySet(), label);
        for (Map.Entry<String, String> entry : expected.entrySet()) equal(actual.get(entry.getKey()), entry.getValue(), label + "." + entry.getKey());
    }

    private static void equal(Object actual, Object expected, String label) throws IOException {
        if (!expected.equals(actual)) throw new IOException("Mapping bundle requires " + label + "=" + expected);
    }

    private static String string(Object value, String label) throws IOException {
        if (!(value instanceof String text) || text.isBlank() || text.length() > 4096
                || text.chars().anyMatch(character -> character < 0x20)) {
            throw new IOException("Invalid or oversized mapping " + label);
        }
        return text;
    }

    private static long integer(Object value, String label) throws IOException {
        if (!(value instanceof BigDecimal number)) throw new IOException("Mapping " + label + " must be an integer");
        try { return number.longValueExact(); }
        catch (ArithmeticException invalid) { throw new IOException("Invalid mapping integer " + label, invalid); }
    }

    private static List<?> array(Object value, String label) throws IOException {
        if (!(value instanceof List<?> list)) throw new IOException("Mapping " + label + " must be an array");
        return list;
    }

    private static List<String> strings(Object value, int limit, String label) throws IOException {
        List<?> array = array(value, label);
        if (array.size() > limit) throw new IOException("Mapping " + label + " exceeds count limit");
        List<String> result = new ArrayList<>();
        for (Object entry : array) result.add(string(entry, label));
        return List.copyOf(result);
    }

    private record Payload(long bytes, String sha256) { }
}
