package dev.legacybridge.validation.composition;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** JDK-only bounded observation serialization, not a parser or gameplay oracle. */
public final class ProbeJson {
    private ProbeJson() { }
    public static LinkedHashMap<String, Object> object(Object... pairs) {
        if (pairs == null || pairs.length % 2 != 0 || pairs.length > 64) throw new IllegalArgumentException("OBS_OBJECT");
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (!(pairs[i] instanceof String) || result.containsKey(pairs[i])) throw new IllegalArgumentException("OBS_KEY");
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }
    public static byte[] render(Map<String, Object> value) {
        StringBuilder result = new StringBuilder();
        append(result, value, 0, new int[1]);
        result.append('\n');
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static void append(StringBuilder out, Object value, int depth, int[] nodes) {
        if (depth > 16 || ++nodes[0] > 10_000 || out.length() > 1_000_000) throw new IllegalArgumentException("OBS_JSON_LIMIT");
        if (value == null || value instanceof Boolean || value instanceof Integer || value instanceof Long) out.append(value);
        else if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) throw new IllegalArgumentException("OBS_NONFINITE");
            out.append(value);
        } else if (value instanceof String) {
            String text = (String) value;
            if (!text.matches("[A-Za-z0-9_.:+-]{1,256}")) throw new IllegalArgumentException("OBS_IDENTIFIER");
            out.append('"').append(text).append('"');
        } else if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.size() > 32) throw new IllegalArgumentException("OBS_OBJECT_LIMIT");
            TreeMap<String, Object> sorted = new TreeMap<String, Object>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) throw new IllegalArgumentException("OBS_KEY");
                sorted.put((String) entry.getKey(), entry.getValue());
            }
            out.append('{'); boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) out.append(','); first = false;
                append(out, entry.getKey(), depth + 1, nodes); out.append(':');
                append(out, entry.getValue(), depth + 1, nodes);
            }
            out.append('}');
        } else if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            if (list.size() > 256) throw new IllegalArgumentException("OBS_ARRAY_LIMIT");
            out.append('[');
            for (int i = 0; i < list.size(); i++) { if (i != 0) out.append(','); append(out, list.get(i), depth + 1, nodes); }
            out.append(']');
        } else throw new IllegalArgumentException("OBS_VALUE");
    }
}
