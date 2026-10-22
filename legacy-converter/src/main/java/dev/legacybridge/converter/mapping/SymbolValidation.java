package dev.legacybridge.converter.mapping;

import java.util.Objects;

/** Strict JVM syntax checks: ASM's Type factory alone accepts some malformed descriptors. */
final class SymbolValidation {
    private SymbolValidation() {}

    static void validate(SymbolKind kind, String owner, String name, String descriptor) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
        switch (kind) {
            case CLASS -> {
                internalName(owner);
                require(name.isEmpty() && descriptor.isEmpty(), "Class symbols only contain an owner");
            }
            case DESCRIPTOR -> {
                require(owner.isEmpty() && name.isEmpty(), "Descriptor symbols have no owner or name");
                descriptor(descriptor, descriptor.startsWith("("));
            }
            case FIELD -> {
                internalName(owner);
                memberName(name, false);
                descriptor(descriptor, false);
            }
            case METHOD -> {
                internalName(owner);
                memberName(name, true);
                descriptor(descriptor, true);
            }
            case CONSTRUCTOR -> {
                internalName(owner);
                require(name.equals("<init>"), "Constructor name must be <init>");
                descriptor(descriptor, true);
                require(descriptor.endsWith(")V"), "Constructors must return void");
            }
        }
    }

    static void internalName(String value) {
        require(!value.isEmpty() && !value.startsWith("/") && !value.endsWith("/")
                && !value.contains("//"), "Invalid internal class name: " + value);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            require(c != '.' && c != ';' && c != '[' && c != '\\'
                    && !Character.isWhitespace(c) && !Character.isISOControl(c),
                    "Invalid internal class name: " + value);
        }
    }

    private static void memberName(String value, boolean method) {
        require(!value.isEmpty(), "Member name must not be empty");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            require(c != '.' && c != ';' && c != '[' && c != '/' && c != '\\'
                    && c != '<' && c != '>' && !Character.isWhitespace(c)
                    && !Character.isISOControl(c), "Invalid " + (method ? "method" : "field") + " name");
        }
    }

    private static void descriptor(String value, boolean method) {
        require(!value.isEmpty() && value.length() <= 65535, "Invalid descriptor length");
        int cursor;
        if (method) {
            require(value.charAt(0) == '(', "Expected method descriptor");
            cursor = 1;
            int parameterSlots = 0;
            while (cursor < value.length() && value.charAt(cursor) != ')') {
                char first = value.charAt(cursor);
                cursor = type(value, cursor, false);
                parameterSlots += first == 'D' || first == 'J' ? 2 : 1;
                require(parameterSlots <= 255, "Too many method parameter slots");
            }
            require(cursor < value.length() && value.charAt(cursor) == ')', "Unclosed method descriptor");
            cursor = type(value, cursor + 1, true);
        } else {
            require(value.charAt(0) != '(', "Expected field descriptor");
            cursor = type(value, 0, false);
        }
        require(cursor == value.length(), "Trailing descriptor content");
    }

    private static int type(String value, int cursor, boolean allowVoid) {
        require(cursor < value.length(), "Missing descriptor type");
        char c = value.charAt(cursor++);
        if ("ZBCSIJFD".indexOf(c) >= 0 || (c == 'V' && allowVoid)) {
            return cursor;
        }
        if (c == '[') {
            int dimensions = 1;
            while (cursor < value.length() && value.charAt(cursor) == '[') {
                dimensions++;
                cursor++;
            }
            require(dimensions <= 255, "Too many array dimensions");
            return type(value, cursor, false);
        }
        if (c == 'L') {
            int end = value.indexOf(';', cursor);
            require(end >= 0, "Unclosed object descriptor");
            internalName(value.substring(cursor, end));
            return end + 1;
        }
        throw new IllegalArgumentException("Invalid descriptor type");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
