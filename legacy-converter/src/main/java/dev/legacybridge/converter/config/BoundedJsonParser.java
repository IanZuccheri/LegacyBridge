package dev.legacybridge.converter.config;

import dev.legacybridge.converter.operation.OperationControl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON parser with explicit resource bounds and no polymorphic object construction. */
public final class BoundedJsonParser {
    static final int MAX_DEPTH = 64;
    static final int MAX_NODES = 20_000;
    static final int MAX_STRING_CHARS = 16_384;
    static final int MAX_NUMBER_CHARS = 1024;

    private final String input;
    private int index;
    private int nodes;

    private BoundedJsonParser(String input) {
        this.input = input;
    }

    public static Object parse(String input) throws JsonSyntaxException {
        BoundedJsonParser parser = new BoundedJsonParser(input);
        parser.skipWhitespace();
        Object result = parser.parseValue(0);
        parser.skipWhitespace();
        if (parser.index != input.length()) {
            throw parser.error("trailing content");
        }
        return result;
    }

    private Object parseValue(int depth) throws JsonSyntaxException {
        OperationControl.checkpoint();
        if (depth > MAX_DEPTH) {
            throw error("nesting depth limit exceeded");
        }
        if (++nodes > MAX_NODES) {
            throw error("node count limit exceeded");
        }
        if (index >= input.length()) {
            throw error("unexpected end of input");
        }
        return switch (input.charAt(index)) {
            case '{' -> parseObject(depth + 1);
            case '[' -> parseArray(depth + 1);
            case '"' -> parseString();
            case 't' -> parseLiteral("true", Boolean.TRUE);
            case 'f' -> parseLiteral("false", Boolean.FALSE);
            case 'n' -> parseLiteral("null", null);
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObject(int depth) throws JsonSyntaxException {
        index++;
        skipWhitespace();
        Map<String, Object> result = new LinkedHashMap<>();
        if (consume('}')) {
            return result;
        }
        while (true) {
            if (index >= input.length() || input.charAt(index) != '"') {
                throw error("object key must be a string");
            }
            String key = parseString();
            if (result.containsKey(key)) {
                throw error("duplicate object key");
            }
            skipWhitespace();
            expect(':');
            skipWhitespace();
            result.put(key, parseValue(depth));
            skipWhitespace();
            if (consume('}')) {
                return result;
            }
            expect(',');
            skipWhitespace();
        }
    }

    private List<Object> parseArray(int depth) throws JsonSyntaxException {
        index++;
        skipWhitespace();
        List<Object> result = new ArrayList<>();
        if (consume(']')) {
            return result;
        }
        while (true) {
            result.add(parseValue(depth));
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            expect(',');
            skipWhitespace();
        }
    }

    private String parseString() throws JsonSyntaxException {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (index < input.length()) {
            if ((index & 1023) == 0) OperationControl.checkpoint();
            char current = input.charAt(index++);
            if (current == '"') {
                return result.toString();
            }
            if (current < 0x20) {
                throw error("unescaped control character in string");
            }
            if (current == '\\') {
                if (index >= input.length()) {
                    throw error("unterminated escape sequence");
                }
                current = switch (input.charAt(index++)) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '/' -> '/';
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'u' -> parseUnicodeEscape();
                    default -> throw error("invalid escape sequence");
                };
            }
            result.append(current);
            if (result.length() > MAX_STRING_CHARS) {
                throw error("string length limit exceeded");
            }
        }
        throw error("unterminated string");
    }

    private char parseUnicodeEscape() throws JsonSyntaxException {
        if (input.length() - index < 4) {
            throw error("incomplete unicode escape");
        }
        int value = 0;
        for (int offset = 0; offset < 4; offset++) {
            char hex = input.charAt(index++);
            int digit = asciiDigit(hex) ? hex - '0' : hex >= 'a' && hex <= 'f' ? hex - 'a' + 10
                    : hex >= 'A' && hex <= 'F' ? hex - 'A' + 10 : -1;
            if (digit < 0) {
                throw error("invalid unicode escape");
            }
            value = (value << 4) | digit;
        }
        return (char) value;
    }

    private Object parseLiteral(String literal, Object value) throws JsonSyntaxException {
        if (!input.startsWith(literal, index)) {
            throw error("invalid literal");
        }
        index += literal.length();
        return value;
    }

    private BigDecimal parseNumber() throws JsonSyntaxException {
        int start = index;
        consume('-');
        if (consume('0')) {
            if (index < input.length() && asciiDigit(input.charAt(index))) {
                throw error("leading zero in number");
            }
        } else {
            requireDigit();
            while (index < input.length() && asciiDigit(input.charAt(index))) {
                index++;
            }
        }
        if (consume('.')) {
            requireDigit();
            while (index < input.length() && asciiDigit(input.charAt(index))) {
                index++;
            }
        }
        if (index < input.length() && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
            index++;
            if (index < input.length() && (input.charAt(index) == '+' || input.charAt(index) == '-')) {
                index++;
            }
            requireDigit();
            while (index < input.length() && asciiDigit(input.charAt(index))) {
                index++;
            }
        }
        if (start == index) {
            throw error("expected JSON value");
        }
        if (index - start > MAX_NUMBER_CHARS) {
            throw error("number length limit exceeded");
        }
        try {
            return new BigDecimal(input.substring(start, index));
        } catch (NumberFormatException exception) {
            throw error("invalid number");
        }
    }

    private void requireDigit() throws JsonSyntaxException {
        if (index >= input.length() || !asciiDigit(input.charAt(index))) {
            throw error("digit expected");
        }
    }

    private static boolean asciiDigit(char value) { return value >= '0' && value <= '9'; }

    private void expect(char expected) throws JsonSyntaxException {
        if (!consume(expected)) {
            throw error("expected '" + expected + "'");
        }
    }

    private boolean consume(char expected) {
        if (index < input.length() && input.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void skipWhitespace() {
        OperationControl.checkpoint();
        while (index < input.length()) {
            char current = input.charAt(index);
            if (current != ' ' && current != '\t' && current != '\r' && current != '\n') {
                return;
            }
            index++;
        }
    }

    private JsonSyntaxException error(String reason) {
        return new JsonSyntaxException(reason + " at character " + index);
    }

    public static final class JsonSyntaxException extends Exception {
        private static final long serialVersionUID = 1L;

        JsonSyntaxException(String message) {
            super(message);
        }
    }
}
