package dev.legacybridge.converter.report;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Small strict JSON reader and the schema-v2 keyword subset used by report tests. */
final class JsonSchemaTestSupport {
    private JsonSchemaTestSupport() {
    }

    static Object parse(String json) {
        return new Parser(json).parseDocument();
    }

    static void validate(Object instance, Map<String, Object> schema) {
        validate(instance, schema, schema, "$");
    }

    @SuppressWarnings("unchecked")
    private static void validate(
            Object instance,
            Map<String, Object> schema,
            Map<String, Object> root,
            String path
    ) {
        Object reference = schema.get("$ref");
        if (reference instanceof String pointer) {
            validate(instance, resolve(root, pointer), root, path);
        }

        for (Object branch : listValue(schema.get("allOf"))) {
            validate(instance, objectValue(branch), root, path);
        }
        List<Object> alternatives = listValue(schema.get("oneOf"));
        if (!alternatives.isEmpty()) {
            int matches = 0;
            for (Object alternative : alternatives) {
                try {
                    validate(instance, objectValue(alternative), root, path);
                    matches++;
                } catch (AssertionError ignored) {
                    // A oneOf branch is expected to reject non-matching shapes.
                }
            }
            assertEquals(1, matches, path + " must match exactly one oneOf branch");
        }

        if (schema.containsKey("const")) {
            assertJsonEquals(schema.get("const"), instance, path + " violates const");
        }
        List<Object> allowed = listValue(schema.get("enum"));
        if (!allowed.isEmpty()) {
            assertTrue(allowed.stream().anyMatch(candidate -> jsonEquals(candidate, instance)),
                    path + " is not an allowed enum value: " + instance);
        }

        Object declaredType = schema.get("type");
        if (declaredType != null) {
            List<Object> types = declaredType instanceof List<?> list ? List.copyOf(list) : List.of(declaredType);
            assertTrue(types.stream().map(String::valueOf).anyMatch(type -> hasType(instance, type)),
                    path + " has the wrong JSON type");
        }

        if (instance instanceof Map<?, ?> rawObject) {
            Map<String, Object> object = (Map<String, Object>) rawObject;
            Number maximumProperties = (Number) schema.get("maxProperties");
            if (maximumProperties != null) {
                assertTrue(object.size() <= maximumProperties.intValue(), path + " has too many properties");
            }
            for (Object required : listValue(schema.get("required"))) {
                assertTrue(object.containsKey(String.valueOf(required)),
                        path + " is missing required property " + required);
            }

            Map<String, Object> properties = schema.get("properties") instanceof Map<?, ?> value
                    ? (Map<String, Object>) value : Map.of();
            for (Map.Entry<String, Object> property : properties.entrySet()) {
                if (object.containsKey(property.getKey())) {
                    validate(object.get(property.getKey()), objectValue(property.getValue()), root,
                            path + "." + property.getKey());
                }
            }

            Object propertyNames = schema.get("propertyNames");
            if (propertyNames != null) {
                for (String key : object.keySet()) {
                    validate(key, objectValue(propertyNames), root, path + "{propertyName}");
                }
            }

            Object additional = schema.get("additionalProperties");
            for (Map.Entry<String, Object> property : object.entrySet()) {
                if (properties.containsKey(property.getKey()) || additional == null || Boolean.TRUE.equals(additional)) {
                    continue;
                }
                if (Boolean.FALSE.equals(additional)) {
                    fail(path + " contains unexpected property " + property.getKey());
                }
                validate(property.getValue(), objectValue(additional), root,
                        path + "." + property.getKey());
            }
        }

        if (instance instanceof List<?> values) {
            Number maximumItems = (Number) schema.get("maxItems");
            if (maximumItems != null) {
                assertTrue(values.size() <= maximumItems.intValue(), path + " has too many items");
            }
            if (Boolean.TRUE.equals(schema.get("uniqueItems"))) {
                assertEquals(values.size(), new LinkedHashSet<>(values).size(), path + " has duplicate items");
            }
            Object itemSchema = schema.get("items");
            if (itemSchema != null) {
                for (int index = 0; index < values.size(); index++) {
                    validate(values.get(index), objectValue(itemSchema), root, path + "[" + index + "]");
                }
            }
        }

        if (instance instanceof String value) {
            Number minimumLength = (Number) schema.get("minLength");
            if (minimumLength != null) {
                assertTrue(value.length() >= minimumLength.intValue(), path + " is too short");
            }
            Number maximumLength = (Number) schema.get("maxLength");
            if (maximumLength != null) {
                assertTrue(value.length() <= maximumLength.intValue(), path + " is too long");
            }
            Object pattern = schema.get("pattern");
            if (pattern != null) {
                assertTrue(Pattern.compile(String.valueOf(pattern)).matcher(value).find(),
                        path + " does not match " + pattern);
            }
        }

        if (instance instanceof BigDecimal number) {
            Number minimum = (Number) schema.get("minimum");
            if (minimum != null) {
                assertTrue(number.compareTo(decimal(minimum)) >= 0, path + " is below minimum");
            }
            Number maximum = (Number) schema.get("maximum");
            if (maximum != null) {
                assertTrue(number.compareTo(decimal(maximum)) <= 0, path + " is above maximum");
            }
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> objectValue(Object value) {
        assertTrue(value instanceof Map<?, ?>, "expected JSON object but got " + value);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> listValue(Object value) {
        return value == null ? List.of() : (List<Object>) value;
    }

    private static Map<String, Object> resolve(Map<String, Object> root, String pointer) {
        assertTrue(pointer.startsWith("#/"), "only local JSON pointers are supported: " + pointer);
        Object current = root;
        for (String rawPart : pointer.substring(2).split("/")) {
            String part = rawPart.replace("~1", "/").replace("~0", "~");
            current = objectValue(current).get(part);
            assertNotNull(current, "unresolved JSON pointer: " + pointer);
        }
        return objectValue(current);
    }

    private static boolean hasType(Object value, String type) {
        return switch (type) {
            case "null" -> value == null;
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            case "string" -> value instanceof String;
            case "integer" -> value instanceof BigDecimal number && number.stripTrailingZeros().scale() <= 0;
            case "number" -> value instanceof BigDecimal;
            case "boolean" -> value instanceof Boolean;
            default -> throw new AssertionError("unsupported test schema type: " + type);
        };
    }

    private static void assertJsonEquals(Object expected, Object actual, String message) {
        assertTrue(jsonEquals(expected, actual), message + ": expected " + expected + " but got " + actual);
    }

    private static boolean jsonEquals(Object first, Object second) {
        if (first instanceof BigDecimal left && second instanceof BigDecimal right) {
            return left.compareTo(right) == 0;
        }
        return java.util.Objects.equals(first, second);
    }

    private static BigDecimal decimal(Number number) {
        return number instanceof BigDecimal value ? value : new BigDecimal(number.toString());
    }

    private static final class Parser {
        private final String input;
        private int index;

        private Parser(String input) {
            this.input = java.util.Objects.requireNonNull(input, "input");
        }

        private Object parseDocument() {
            Object value = parseValue();
            skipWhitespace();
            assertEquals(input.length(), index, "trailing content after JSON document");
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            assertTrue(index < input.length(), "unexpected end of JSON");
            return switch (input.charAt(index)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) return values;
            do {
                skipWhitespace();
                String key = parseString();
                assertFalse(values.containsKey(key), "duplicate JSON object key: " + key);
                skipWhitespace();
                expect(':');
                values.put(key, parseValue());
                skipWhitespace();
            } while (consume(','));
            expect('}');
            return values;
        }

        private List<Object> parseArray() {
            expect('[');
            ArrayList<Object> values = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) return values;
            do {
                values.add(parseValue());
                skipWhitespace();
            } while (consume(','));
            expect(']');
            return values;
        }

        private String parseString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < input.length()) {
                char character = input.charAt(index++);
                if (character == '"') return value.toString();
                if (character == '\\') {
                    assertTrue(index < input.length(), "unterminated JSON escape");
                    char escaped = input.charAt(index++);
                    switch (escaped) {
                        case '"', '\\', '/' -> value.append(escaped);
                        case 'b' -> value.append('\b');
                        case 'f' -> value.append('\f');
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> value.append(parseUnicode());
                        default -> fail("invalid JSON escape: " + escaped);
                    }
                } else {
                    assertTrue(character >= 0x20, "unescaped control character in JSON string");
                    value.append(character);
                }
            }
            throw new AssertionError("unterminated JSON string");
        }

        private char parseUnicode() {
            assertTrue(index + 4 <= input.length(), "incomplete JSON unicode escape");
            int value = 0;
            for (int offset = 0; offset < 4; offset++) {
                int digit = Character.digit(input.charAt(index++), 16);
                assertTrue(digit >= 0, "invalid JSON unicode escape");
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private Object parseLiteral(String literal, Object value) {
            assertTrue(input.startsWith(literal, index), "invalid JSON literal at " + index);
            index += literal.length();
            return value;
        }

        private BigDecimal parseNumber() {
            int start = index;
            if (consume('-')) {
                assertTrue(index < input.length(), "incomplete JSON number");
            }
            if (consume('0')) {
                assertFalse(index < input.length() && Character.isDigit(input.charAt(index)),
                        "leading zero in JSON number");
            } else {
                requireDigit();
                while (index < input.length() && Character.isDigit(input.charAt(index))) index++;
            }
            if (consume('.')) {
                requireDigit();
                while (index < input.length() && Character.isDigit(input.charAt(index))) index++;
            }
            if (index < input.length() && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
                index++;
                if (index < input.length() && (input.charAt(index) == '+' || input.charAt(index) == '-')) index++;
                requireDigit();
                while (index < input.length() && Character.isDigit(input.charAt(index))) index++;
            }
            try {
                return new BigDecimal(input.substring(start, index));
            } catch (NumberFormatException exception) {
                throw new AssertionError("invalid JSON number at " + start, exception);
            }
        }

        private void requireDigit() {
            assertTrue(index < input.length() && Character.isDigit(input.charAt(index)),
                    "expected digit at " + index);
        }

        private void skipWhitespace() {
            while (index < input.length() && " \t\r\n".indexOf(input.charAt(index)) >= 0) index++;
        }

        private void expect(char expected) {
            assertTrue(consume(expected), "expected '" + expected + "' at " + index);
        }

        private boolean consume(char expected) {
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }
    }
}
