package dev.legacybridge.validation.network;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Closed, bounded test orchestration language; never arbitrary console commands or code. */
record QaCommand(int id, String action, Map<String, String> values) {
    static QaCommand parse(String json) throws IOException {
        if (json.length() > 2048) throw new IOException("Command exceeds 2048 characters");
        Map<String, String> values = new HashMap<>();
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (values.containsKey(key) || values.size() >= 10) throw new IOException("Duplicate/excessive fields");
                JsonToken type = reader.peek();
                if (type != JsonToken.STRING && type != JsonToken.NUMBER && type != JsonToken.BOOLEAN) {
                    throw new IOException("Only primitive command fields are permitted");
                }
                values.put(key, type == JsonToken.BOOLEAN ? Boolean.toString(reader.nextBoolean()) : reader.nextString());
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing command data");
        }
        int id = integer(values, "id", 1, 96);
        String action = values.get("action");
        if (action == null) throw new IOException("Missing action");
        Set<String> fields = switch (action) {
            case "CONNECT", "DISCONNECT", "STATE", "STOP" -> Set.of("id", "action");
            case "EXPECT" -> Set.of("id", "action", "sequence", "value", "accepted", "rejected", "connected");
            case "EXPECT_PEERS" -> Set.of("id", "action", "a", "b");
            case "SEND" -> Set.of("id", "action", "target", "sequence", "value");
            case "RAW_S2C" -> Set.of("id", "action", "target", "payload");
            case "RAW_C2S" -> Set.of("id", "action", "payload");
            default -> throw new IOException("Unknown action");
        };
        if (!values.keySet().equals(fields)) throw new IOException("Unexpected/missing command fields");
        if (fields.contains("target") && !Set.of("a", "b").contains(values.get("target"))) throw new IOException("Unknown target");
        for (String key : Set.of("sequence", "value", "accepted", "rejected")) {
            if (fields.contains(key)) integer(values, key, 0, 1000);
        }
        for (String key : Set.of("a", "b", "connected")) {
            if (fields.contains(key) && !Set.of("true", "false").contains(values.get(key))) throw new IOException("Invalid boolean");
        }
        if (fields.contains("payload") && !values.get("payload").matches("(?:[0-9a-f]{2}){1,32}")) {
            throw new IOException("Raw payload must be 1..32 bytes of lowercase hex");
        }
        return new QaCommand(id, action, Map.copyOf(values));
    }

    private static int integer(Map<String, String> values, String key, int min, int max) throws IOException {
        String value = values.get(key);
        if (value == null || !value.matches("0|[1-9][0-9]{0,3}")) throw new IOException("Invalid integer " + key);
        int number = Integer.parseInt(value);
        if (number < min || number > max) throw new IOException("Out of bounds " + key);
        return number;
    }
    int number(String key) { return Integer.parseInt(values.get(key)); }
    boolean flag(String key) { return Boolean.parseBoolean(values.get(key)); }
    String text(String key) { return values.get(key); }
}
