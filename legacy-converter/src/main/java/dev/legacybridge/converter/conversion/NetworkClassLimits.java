package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.TransformationException;

/**
 * Allocation preflight for the closed network recipe, before any ASM ClassReader
 * or frame analyzer. This bounded cursor checks container lengths and Code maxima;
 * it is NOT a class-file, linkage, semantic, or StackMap verifier. The unchanged
 * ASM/scanner/recipe checks remain mandatory after this succeeds.
 */
public final class NetworkClassLimits {
    public static final int MAX_CLASS_BYTES = 64 * 1024;
    public static final int MAX_FRAME_SLOTS = 64;
    private static final int MAX_CONSTANT_POOL_COUNT = 4096;
    private static final int MAX_MEMBERS = 64;
    private static final int MAX_ATTRIBUTES_PER_TABLE = 64;
    private static final int MAX_ATTRIBUTES_TOTAL = 1024;
    private static final int MAX_ANNOTATION_DEPTH = 16;
    private static final int MAX_ANNOTATION_VALUES = 1024;

    private NetworkClassLimits() { }

    public static void check(byte[] bytes) {
        OperationControl.checkpoint();
        require(bytes != null && bytes.length >= 10 && bytes.length <= MAX_CLASS_BYTES, "Class must contain 10..65536 bytes");
        new Parser(bytes).check();
    }

    private enum Location { CLASS, FIELD, METHOD, CODE }

    private static final class Parser {
        private final byte[] bytes;
        private final Cursor cursor;
        private byte[] tags;
        private int[] utfOffsets;
        private int[] utfLengths;
        private int attributeCount;
        private int annotationValues;

        Parser(byte[] bytes) { this.bytes = bytes; this.cursor = new Cursor(bytes, 0, bytes.length); }

        void check() {
            require(cursor.u4() == 0xCAFEBABEL, "Missing class-file magic");
            require(cursor.u2() == 0 && cursor.u2() == 52, "Only non-preview Java 8 class files are reviewed");
            int poolCount = cursor.u2();
            require(poolCount >= 1 && poolCount <= MAX_CONSTANT_POOL_COUNT, "Constant-pool count exceeds profile bound");
            tags = new byte[poolCount]; utfOffsets = new int[poolCount]; utfLengths = new int[poolCount];
            for (int index = 1; index < poolCount; index++) {
                OperationControl.checkpoint();
                int tag = cursor.u1(); tags[index] = (byte) tag;
                switch (tag) {
                    case 1 -> {
                        int length = cursor.u2(); utfOffsets[index] = cursor.position; utfLengths[index] = length; cursor.skip(length);
                        modifiedUtf8(utfOffsets[index], length);
                    }
                    case 3, 4, 9, 10, 11, 12, 18 -> cursor.skip(4);
                    case 5, 6 -> {
                        cursor.skip(8);
                        require(++index < poolCount, "Double-slot constant has no reserved following slot");
                    }
                    case 7, 8, 16 -> cursor.skip(2);
                    case 15 -> cursor.skip(3);
                    default -> throw failure("Unreviewed Java 8 constant-pool tag: " + tag);
                }
            }
            cursor.u2(); // access flags: the recipe and ASM validate their meaning.
            constant(cursor.u2(), 7);
            int parent = cursor.u2(); if (parent != 0) constant(parent, 7);
            int interfaces = cursor.u2(); require(interfaces <= MAX_MEMBERS, "Interface count exceeds profile bound");
            for (int index = 0; index < interfaces; index++) constant(cursor.u2(), 7);
            members(Location.FIELD);
            members(Location.METHOD);
            attributes(cursor, Location.CLASS);
            require(cursor.finished(), "Trailing bytes after class attributes");
        }

        private void members(Location location) {
            int count = cursor.u2(); require(count <= MAX_MEMBERS, "Member count exceeds profile bound");
            for (int index = 0; index < count; index++) {
                OperationControl.checkpoint();
                cursor.u2(); constant(cursor.u2(), 1); constant(cursor.u2(), 1);
                attributes(cursor, location);
            }
        }

        private void attributes(Cursor input, Location location) {
            int count = input.u2();
            require(count <= MAX_ATTRIBUTES_PER_TABLE && (attributeCount += count) <= MAX_ATTRIBUTES_TOTAL,
                    "Attribute count exceeds profile bound");
            boolean codeSeen = false;
            for (int index = 0; index < count; index++) {
                OperationControl.checkpoint();
                int name = input.u2(); constant(name, 1);
                long length = input.u4();
                require(length <= MAX_CLASS_BYTES, "Attribute length exceeds class bound");
                Cursor attribute = input.slice((int) length);
                if (nameIs(name, "Code")) {
                    require(location == Location.METHOD && !codeSeen, "Code attribute is duplicate or not on a method");
                    codeSeen = true;
                    code(attribute);
                    require(attribute.finished(), "Trailing bytes in Code attribute");
                } else if (nameIs(name, "RuntimeVisibleAnnotations") || nameIs(name, "RuntimeInvisibleAnnotations")) {
                    int annotations = attribute.u2(); require(annotations <= 64, "Annotation count exceeds profile bound");
                    for (int item = 0; item < annotations; item++) annotation(attribute, 0);
                    require(attribute.finished(), "Trailing bytes in annotation attribute");
                } else if (nameIs(name, "RuntimeVisibleTypeAnnotations") || nameIs(name, "RuntimeInvisibleTypeAnnotations")
                        || nameIs(name, "RuntimeVisibleParameterAnnotations") || nameIs(name, "RuntimeInvisibleParameterAnnotations")
                        || nameIs(name, "AnnotationDefault")) {
                    throw failure("Type/parameter/default annotation attributes are outside the reviewed profile");
                }
                // Other bounded attributes are inert here; scanner/ASM still validate them.
            }
        }

        private void code(Cursor input) {
            int maxStack = input.u2(), maxLocals = input.u2();
            require(maxStack <= MAX_FRAME_SLOTS && maxLocals <= MAX_FRAME_SLOTS, "Code max_stack/max_locals exceeds 64 before frame allocation");
            long length = input.u4();
            require(length > 0 && length <= 65535, "Code length must be 1..65535");
            input.skip((int) length);
            int exceptions = input.u2(); require(exceptions <= 64, "Exception-region count exceeds profile bound");
            input.skip(exceptions * 8);
            attributes(input, Location.CODE);
        }

        private void constant(int index, int tag) {
            require(index > 0 && index < tags.length && tags[index] == tag, "Invalid constant-pool index/tag in class structure");
        }

        private boolean nameIs(int index, String ascii) {
            int start = utfOffsets[index];
            if (utfLengths[index] != ascii.length()) return false;
            for (int offset = 0; offset < ascii.length(); offset++) if (bytes[start + offset] != ascii.charAt(offset)) return false;
            return true;
        }

        private void annotation(Cursor input, int depth) {
            annotationBudget(depth);
            constant(input.u2(), 1);
            int pairs = input.u2(); require(pairs <= 64, "Annotation pair count exceeds profile bound");
            for (int index = 0; index < pairs; index++) { constant(input.u2(), 1); annotationValue(input, depth + 1); }
        }

        private void annotationValue(Cursor input, int depth) {
            annotationBudget(depth);
            switch (input.u1()) {
                case 'B', 'C', 'I', 'S', 'Z' -> constant(input.u2(), 3);
                case 'D' -> constant(input.u2(), 6);
                case 'F' -> constant(input.u2(), 4);
                case 'J' -> constant(input.u2(), 5);
                case 's', 'c' -> constant(input.u2(), 1);
                case 'e' -> { constant(input.u2(), 1); constant(input.u2(), 1); }
                case '@' -> annotation(input, depth + 1);
                case '[' -> {
                    int count = input.u2(); require(count <= 64, "Annotation array count exceeds profile bound");
                    for (int index = 0; index < count; index++) annotationValue(input, depth + 1);
                }
                default -> throw failure("Unknown annotation element_value tag");
            }
        }

        private void annotationBudget(int depth) {
            OperationControl.checkpoint();
            require(depth <= MAX_ANNOTATION_DEPTH, "Annotation nesting depth exceeds 16 before ASM parsing");
            require(++annotationValues <= MAX_ANNOTATION_VALUES, "Annotation value count exceeds profile bound");
        }

        /** Canonical modified UTF-8 prevents an overlong spelling of Code from bypassing raw maxima checks. */
        private void modifiedUtf8(int start, int length) {
            int end = start + length;
            for (int index = start; index < end;) {
                OperationControl.checkpoint();
                int first = bytes[index++] & 255;
                if (first >= 1 && first <= 127) continue;
                if (first >= 0xC0 && first <= 0xDF) {
                    require(index < end, "Truncated modified UTF-8 constant");
                    int second = bytes[index++] & 255;
                    require((second & 0xC0) == 0x80 && (first >= 0xC2 || first == 0xC0 && second == 0x80),
                            "Noncanonical modified UTF-8 constant");
                } else if (first >= 0xE0 && first <= 0xEF) {
                    require(index + 1 < end, "Truncated modified UTF-8 constant");
                    int second = bytes[index++] & 255, third = bytes[index++] & 255;
                    require((second & 0xC0) == 0x80 && (third & 0xC0) == 0x80 && (first != 0xE0 || second >= 0xA0),
                            "Noncanonical modified UTF-8 constant");
                    // UTF-16 surrogate code units are intentionally legal in modified UTF-8.
                } else throw failure("Noncanonical modified UTF-8 constant");
            }
        }
    }

    private static final class Cursor {
        private final byte[] bytes;
        private final int end;
        private int position;
        Cursor(byte[] bytes, int start, int end) { this.bytes = bytes; this.position = start; this.end = end; }
        int u1() { available(1); return bytes[position++] & 255; }
        int u2() { return (u1() << 8) | u1(); }
        long u4() { return ((long) u2() << 16) | u2(); }
        void skip(int count) { available(count); position += count; }
        Cursor slice(int count) { available(count); int start = position; position += count; return new Cursor(bytes, start, position); }
        boolean finished() { return position == end; }
        private void available(int count) { require(count >= 0 && count <= end - position, "Truncated class or attribute container"); }
    }

    private static void require(boolean condition, String reason) { if (!condition) throw failure(reason); }
    private static TransformationException failure(String reason) { return new TransformationException("NETWORK_CLASS_LIMIT: " + reason); }
}
