package dev.legacybridge.validation.lifecycle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Test-only deterministic primitive JSON. No fixture class or Forge API is referenced here. */
final class BehaviorTraceWriter {
    private static final int MAX_BYTES = 65536;

    private BehaviorTraceWriter() {
    }

    static String render(LinkedHashMap<String, Object> observations) {
        if (observations == null || observations.size() > 32) {
            throw new IllegalArgumentException("Invalid trace field count");
        }
        StringBuilder json = new StringBuilder(2048);
        json.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : observations.entrySet()) {
            if (!first) json.append(',');
            first = false;
            string(json, entry.getKey());
            json.append(':');
            Object value = entry.getValue();
            if (value instanceof Integer || value instanceof Boolean) {
                json.append(value);
            } else if (value instanceof String) {
                string(json, (String) value);
            } else if (value instanceof int[]) {
                int[] values = (int[]) value;
                if (values.length > 128) throw new IllegalArgumentException("Trace array too long");
                json.append('[');
                for (int index = 0; index < values.length; index++) {
                    if (index != 0) json.append(',');
                    json.append(values[index]);
                }
                json.append(']');
            } else if (value instanceof boolean[]) {
                boolean[] values = (boolean[]) value;
                if (values.length > 128) throw new IllegalArgumentException("Trace array too long");
                json.append('[');
                for (int index = 0; index < values.length; index++) {
                    if (index != 0) json.append(',');
                    json.append(values[index]);
                }
                json.append(']');
            } else {
                throw new IllegalArgumentException("Unsupported trace value");
            }
        }
        json.append("}\n");
        if (json.length() > MAX_BYTES) throw new IllegalArgumentException("Trace exceeds limit");
        return json.toString();
    }

    private static void string(StringBuilder json, String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.:-]{1,200}")) {
            throw new IllegalArgumentException("Trace text is not a bounded identifier");
        }
        json.append('"').append(value).append('"');
    }

    /** Writes only a new complete trace; a failed write attempts removal of its own partial file. */
    static void writeNew(Path destination, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Trace exceeds byte limit");
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                || !parent.equals(parent.toRealPath())) {
            throw new IOException("Trace requires an existing canonical parent");
        }
        boolean created = false;
        boolean complete = false;
        try {
            try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                created = true;
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            complete = true;
        } finally {
            if (created && !complete) Files.deleteIfExists(target);
        }
    }
}
