package dev.legacybridge.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict, bounded declarative input. Parsing has no dependency on initialized game registries. */
public record SimpleContentPlan(String modId, String modVersion, List<ItemDefinition> items,
                                List<BlockDefinition> blocks, List<SoundDefinition> sounds) {
    public static final int MAX_PLAN_BYTES = 1_048_576;
    private static final Set<String> MATERIALS = Set.of("STONE");
    private static final Set<String> SOUNDS = Set.of("STONE");
    private static final Set<String> TABS = Set.of("MISC", "BUILDING_BLOCKS");

    public SimpleContentPlan {
        items = List.copyOf(items);
        blocks = List.copyOf(blocks);
        sounds = List.copyOf(sounds);
    }

    public static SimpleContentPlan read(InputStream input, String expectedModId) throws IOException {
        byte[] bytes = input.readNBytes(MAX_PLAN_BYTES + 1);
        if (bytes.length > MAX_PLAN_BYTES) throw new IOException("Simple content plan exceeds 1 MiB");
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonObject root = object(readJson(reader, 0, new int[]{0}),
                    Set.of("schemaVersion", "modId", "modVersion", "items", "blocks", "sounds"));
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("Trailing JSON content");
            if (integer(root, "schemaVersion", 1, 1) != 1) throw invalid("Unsupported schemaVersion");
            String modId = string(root, "modId");
            if (!modId.matches("[a-z][a-z0-9_]{1,63}") || !modId.equals(expectedModId)
                    || Set.of("minecraft", "forge", "legacybridge_runtime").contains(modId)) {
                throw invalid("Plan modId does not match the active converted mod");
            }
            String version = string(root, "modVersion");
            if (version.isBlank() || version.length() > 128) throw invalid("Invalid modVersion");
            List<ItemDefinition> items = new ArrayList<>();
            List<BlockDefinition> blocks = new ArrayList<>();
            List<SoundDefinition> sounds = new ArrayList<>();
            Set<String> itemNames = new HashSet<>();
            Set<String> blockNames = new HashSet<>();
            Set<String> soundNames = new HashSet<>();
            for (JsonElement element : array(root, "blocks")) {
                JsonObject block = object(element, Set.of("name", "properties"));
                String name = uniqueName(block, blockNames);
                JsonObject properties = object(block.get("properties"), Set.of("material", "hardness", "resistance", "sound", "requiresCorrectTool", "noOcclusion"));
                if (bool(properties, "requiresCorrectTool") || bool(properties, "noOcclusion")) {
                    throw invalid("Only default stone occlusion/tool properties have verified adapters");
                }
                blocks.add(new BlockDefinition(name, new BlockProperties(token(properties, "material", MATERIALS),
                        finite(properties, "hardness"), finite(properties, "resistance"), token(properties, "sound", SOUNDS),
                        bool(properties, "requiresCorrectTool"), bool(properties, "noOcclusion"))));
            }
            for (JsonElement element : array(root, "items")) {
                JsonObject item = object(element, Set.of("name", "block", "properties"));
                String name = uniqueName(item, itemNames);
                String block = nullableString(item, "block");
                if (block != null && (!block.startsWith(modId + ":") || !blockNames.contains(block.substring(modId.length() + 1)))) {
                    throw invalid("BlockItem references an undeclared local block");
                }
                JsonObject properties = object(item.get("properties"), Set.of("stackSize", "durability", "fireResistant", "rarity", "creativeTab"));
                int stackSize = integer(properties, "stackSize", 1, 64);
                int durability = integer(properties, "durability", 0, 0);
                if (bool(properties, "fireResistant")) throw invalid("Fire-resistant items have no verified adapter in this subset");
                String tab = nullableString(properties, "creativeTab");
                if (tab != null && !TABS.contains(tab)) throw invalid("Unsupported creativeTab");
                items.add(new ItemDefinition(name, block, new ItemProperties(stackSize, durability, bool(properties, "fireResistant"),
                        token(properties, "rarity", Set.of("COMMON")), tab)));
            }
            for (JsonElement element : array(root, "sounds")) {
                JsonObject sound = object(element, Set.of("name", "resourceLocation"));
                String name = uniqueName(sound, soundNames);
                String id = string(sound, "resourceLocation");
                if (!id.startsWith(modId + ":") || !validPath(id.substring(modId.length() + 1))) {
                    throw invalid("Sound location must be a valid local resource location");
                }
                sounds.add(new SoundDefinition(name, id));
            }
            if (items.size() + blocks.size() + sounds.size() > 2048) throw invalid("Too many content definitions");
            return new SimpleContentPlan(modId, version, items, blocks, sounds);
        } catch (IllegalStateException | NumberFormatException exception) {
            throw new IOException("Malformed simple content plan", exception);
        }
    }

    private static JsonElement readJson(JsonReader reader, int depth, int[] count) throws IOException {
        if (depth > 16 || ++count[0] > 50_000) throw invalid("JSON structure exceeds plan limits");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                JsonObject object = new JsonObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (name.length() > 128 || object.has(name)) throw invalid("Duplicate or oversized JSON field");
                    object.add(name, readJson(reader, depth + 1, count));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) {
                    if (array.size() >= 1024) throw invalid("Array exceeds plan limits");
                    array.add(readJson(reader, depth + 1, count));
                }
                reader.endArray();
                yield array;
            }
            case STRING -> {
                String value = reader.nextString();
                if (value.length() > 1024) throw invalid("Oversized JSON string");
                yield new JsonPrimitive(value);
            }
            case NUMBER -> {
                String value = reader.nextString();
                if (value.length() > 64) throw invalid("Oversized JSON number");
                yield new JsonPrimitive(new BigDecimal(value));
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw invalid("Unexpected JSON token");
        };
    }

    private static JsonObject object(JsonElement element, Set<String> fields) throws IOException {
        if (element == null || !element.isJsonObject() || !element.getAsJsonObject().keySet().equals(fields)) {
            throw invalid("Object fields differ from the supported plan schema");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String key) throws IOException {
        if (!object.get(key).isJsonArray()) throw invalid("Expected array " + key);
        return object.getAsJsonArray(key);
    }

    private static String uniqueName(JsonObject object, Set<String> names) throws IOException {
        String name = string(object, "name");
        if (!validPath(name) || !names.add(name)) throw invalid("Invalid or duplicate registry name");
        return name;
    }

    private static boolean validPath(String value) {
        return !value.isEmpty() && value.length() <= 256 && value.matches("[a-z0-9_./-]+")
                && !value.startsWith("/") && !value.endsWith("/") && !value.contains("//")
                && !List.of(value.split("/")).contains("..") && !List.of(value.split("/")).contains(".");
    }

    private static String string(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("Expected string " + key);
        return value.getAsString();
    }

    private static String nullableString(JsonObject object, String key) throws IOException {
        return object.get(key).isJsonNull() ? null : string(object, key);
    }

    private static String token(JsonObject object, String key, Set<String> permitted) throws IOException {
        String value = string(object, key);
        if (!permitted.contains(value)) throw invalid("Unsupported " + key);
        return value;
    }

    private static boolean bool(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw invalid("Expected boolean " + key);
        return value.getAsBoolean();
    }

    private static int integer(JsonObject object, String key, int min, int max) throws IOException {
        BigDecimal number = number(object, key);
        try {
            int value = number.intValueExact();
            if (value < min || value > max) throw invalid("Out of range " + key);
            return value;
        } catch (ArithmeticException exception) { throw invalid("Expected bounded integer " + key); }
    }

    private static float finite(JsonObject object, String key) throws IOException {
        float value = number(object, key).floatValue();
        if (!Float.isFinite(value) || value < 0) throw invalid("Expected finite nonnegative " + key);
        return value;
    }

    private static BigDecimal number(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("Expected number " + key);
        return value.getAsBigDecimal();
    }

    private static IOException invalid(String reason) { return new IOException(reason); }

    public record ItemDefinition(String name, String block, ItemProperties properties) { }
    public record ItemProperties(int stackSize, int durability, boolean fireResistant, String rarity, String creativeTab) { }
    public record BlockDefinition(String name, BlockProperties properties) { }
    public record BlockProperties(String material, float hardness, float resistance, String sound, boolean requiresCorrectTool, boolean noOcclusion) { }
    public record SoundDefinition(String name, String resourceLocation) { }
}
