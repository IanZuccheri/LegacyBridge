package dev.legacybridge.corpus;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Inert, bounded UTF-8 JSON for development evidence; no polymorphic deserialization. */
public final class CorpusJson {
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private CorpusJson() { }

    public static Map<String, Object> read(Path file) throws IOException {
        Path path = regular(file);
        if (Files.size(path) > MAX_BYTES) throw new IOException("Corpus JSON exceeds the 2 MiB limit");
        byte[] bytes;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw new IOException("Corpus JSON grew beyond the 2 MiB limit");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            Object value = BoundedJsonParser.parse(text);
            if (!(value instanceof Map<?, ?>)) throw new IOException("Corpus JSON root must be an object");
            @SuppressWarnings("unchecked") var object = (Map<String, Object>) value;
            return object;
        } catch (BoundedJsonParser.JsonSyntaxException invalid) {
            throw new IOException("Invalid bounded corpus JSON: " + invalid.getMessage(), invalid);
        }
    }

    public static String canonicalJson(Map<String, ?> value) throws IOException {
        if (value == null) throw new IOException("JSON root is required");
        @SuppressWarnings("unchecked") var canonical = (Map<String, Object>) canonical(value, 0, new int[1]);
        String json = new CanonicalReportWriter().toJsonTree(canonical);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Corpus JSON exceeds the 2 MiB limit");
        try { BoundedJsonParser.parse(json); }
        catch (BoundedJsonParser.JsonSyntaxException invalid) { throw new IOException("Canonical value is not bounded JSON", invalid); }
        return json;
    }

    /** Publishes a complete canonical file without replacing an existing destination. Parent must exist. */
    public static void writeCanonicalCreateNew(Path file, Map<String, ?> value) throws IOException {
        byte[] bytes = canonicalJson(value).getBytes(StandardCharsets.UTF_8);
        Path target = file.toAbsolutePath().normalize();
        directory(target.getParent());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(target.toString());
        Path temporary = Files.createTempFile(target.getParent(), ".legacybridge-corpus-", ".tmp");
        boolean published = false;
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            directory(target.getParent());
            // ATOMIC_MOVE can replace an existing path on some providers. This overload never requests replacement.
            Files.move(temporary, target);
            published = true;
        } finally { if (!published) Files.deleteIfExists(temporary); }
    }

    static Path directory(Path input) throws IOException {
        if (input == null) throw new IOException("Directory is required");
        Path path = input.toAbsolutePath().normalize();
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || !path.equals(path.toRealPath()))
            throw new IOException("Expected a canonical, non-aliased directory");
        checkParents(path);
        return path;
    }

    static Path regular(Path input) throws IOException {
        Path path = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.equals(path.toRealPath()))
            throw new IOException("Expected a canonical, non-aliased regular file");
        checkParents(path);
        return path;
    }

    static void checkParents(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current) || (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !current.equals(current.toRealPath()))) throw new IOException("Aliased corpus paths are forbidden");
        }
    }

    private static Object canonical(Object value, int depth, int[] nodes) throws IOException {
        if (depth > 64 || ++nodes[0] > 20_000) throw new IOException("Canonical corpus JSON exceeds structural limits");
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.length() > 16_384) throw new IOException("Invalid JSON object key");
                result.put(key, canonical(entry.getValue(), depth + 1, nodes));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) result.add(canonical(item, depth + 1, nodes));
            return result;
        }
        if (value == null || value instanceof Boolean || value instanceof Number) return value;
        if (value instanceof String text && text.length() <= 16_384) return text;
        throw new IOException("Unsupported or overlong JSON value");
    }
}
