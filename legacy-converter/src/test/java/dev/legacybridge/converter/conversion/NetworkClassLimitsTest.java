package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.transformation.TransformationException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** No ASM import or analyzer: hostile Code maxima/lengths must be refused before those stages. */
class NetworkClassLimitsTest {
    private record Sample(byte[] bytes, int interfaces, int fields, int methods, int methodName,
                          int methodAttributes, int codeName, int codeLength, int body,
                          int exceptionCount, int nestedAttributes, int classAttributes) { }

    @Test void boundedJava8ContainerPassesWithoutChangingInput() throws Exception {
        var sample = sample(false); byte[] before = sample.bytes.clone();
        assertDoesNotThrow(() -> NetworkClassLimits.check(sample.bytes));
        assertArrayEquals(before, sample.bytes);
        u2(sample.bytes, sample.body, 64); u2(sample.bytes, sample.body + 2, 64);
        assertDoesNotThrow(() -> NetworkClassLimits.check(sample.bytes), "64-slot maxima remain bounded, not semantically certified");
    }

    @Test void oversizedLocalsAreRejectedBeforeAnyFrameAllocationOrInstructionParsing() throws Exception {
        for (int slots : List.of(65, 65535)) {
            var sample = sample(false);
            u2(sample.bytes, sample.body + 2, slots);
            sample.bytes[sample.body + 8] = (byte) 255; // Also an invalid opcode, deliberately never interpreted here.
            rejected(sample.bytes, "max_stack/max_locals");
        }
    }

    @Test void oversizedStackIsRejectedBeforeAnyFrameAllocation() throws Exception {
        for (int slots : List.of(65, 65535)) {
            var sample = sample(false); u2(sample.bytes, sample.body, slots);
            rejected(sample.bytes, "max_stack/max_locals");
        }
    }

    @Test void hugeUnsignedCodeLengthCannotWrapOrAllocate() throws Exception {
        for (long length : List.of(0L, 65536L, 0xffff_ffffL)) {
            var sample = sample(false); u4(sample.bytes, sample.body + 4, length);
            rejected(sample.bytes, "Code length");
        }
    }

    @Test void legalLookingCodeLengthStillMustFitItsOwnAttribute() throws Exception {
        var sample = sample(false); u4(sample.bytes, sample.body + 4, 65535);
        rejected(sample.bytes, "Truncated");
    }

    @Test void declaredAttributeCannotWrapOrReadBeyondClassBytes() throws Exception {
        for (long length : List.of(65536L, 65537L, 0xffff_ffffL)) {
            var sample = sample(false); u4(sample.bytes, sample.codeLength, length);
            assertThrows(TransformationException.class, () -> NetworkClassLimits.check(sample.bytes));
        }
    }

    @Test void codeParserCannotBorrowFollowingClassBytesOutsideItsAttributeLength() throws Exception {
        var sample = sample(false); u4(sample.bytes, sample.codeLength, 2);
        rejected(sample.bytes, "Truncated");
    }

    @Test void allTruncatedContainerPrefixesFailWithAStructuredLimitError() throws Exception {
        var sample = sample(false);
        for (int length = 0; length < sample.bytes.length; length++) {
            byte[] truncated = Arrays.copyOf(sample.bytes, length);
            var failure = assertThrows(TransformationException.class, () -> NetworkClassLimits.check(truncated), "Prefix length " + length);
            assertTrue(failure.getMessage().startsWith("NETWORK_CLASS_LIMIT:"));
        }
    }

    @Test void trailingAndOversizedClassDataAreNotAccepted() throws Exception {
        var sample = sample(false);
        rejected(Arrays.copyOf(sample.bytes, sample.bytes.length + 1), "Trailing");
        rejected(Arrays.copyOf(sample.bytes, NetworkClassLimits.MAX_CLASS_BYTES + 1), "10..65536");
        rejected(null, "10..65536");
    }

    @Test void invalidMagicOrNonJava8VersionIsRefusedEarly() throws Exception {
        var badMagic = sample(false); badMagic.bytes[0] = 0; rejected(badMagic.bytes, "magic");
        var badMajor = sample(false); u2(badMajor.bytes, 6, 61); rejected(badMajor.bytes, "Java 8");
        var badMinor = sample(false); u2(badMinor.bytes, 4, 65535); rejected(badMinor.bytes, "Java 8");
    }

    @Test void unknownOrNewerConstantPoolTagsCannotBeSkipped() throws Exception {
        for (int tag : List.of(0, 2, 17, 19, 20, 255)) {
            var sample = sample(false); sample.bytes[10] = (byte) tag;
            rejected(sample.bytes, "constant-pool tag");
        }
    }

    @Test void constantPoolCountsAndUtfLengthsAreBoundedBeforeAllocation() throws Exception {
        for (int count : List.of(0, 4097, 65535)) {
            var sample = sample(false); u2(sample.bytes, 8, count); rejected(sample.bytes, "Constant-pool count");
        }
        var utf = sample(false); u2(utf.bytes, 11, 65535); rejected(utf.bytes, "Truncated");
        var lastLong = sample(false); u2(lastLong.bytes, 8, 2); lastLong.bytes[10] = 5;
        rejected(lastLong.bytes, "reserved following slot");
    }

    @Test void structuralConstantPoolIndexesMustUseTheCorrectTag() throws Exception {
        var sample = sample(false); u2(sample.bytes, sample.methodName, 2);
        rejected(sample.bytes, "constant-pool index/tag");
        var codeName = sample(false); u2(codeName.bytes, codeName.codeName, 0);
        rejected(codeName.bytes, "constant-pool index/tag");
    }

    @Test void overlongUtf8SpellingOfCodeCannotHideTheFrameMaximaFromPreflight() throws Exception {
        var sample = sample(false);
        int codeText = sample.interfaces - 10; // Last pool entry is the four-byte ASCII string Code.
        assertEquals('C', sample.bytes[codeText]);
        byte[] altered = new byte[sample.bytes.length + 1];
        System.arraycopy(sample.bytes, 0, altered, 0, codeText);
        altered[codeText] = (byte) 0xC1; altered[codeText + 1] = (byte) 0x83;
        System.arraycopy(sample.bytes, codeText + 1, altered, codeText + 2, sample.bytes.length - codeText - 1);
        u2(altered, codeText - 2, 5);
        u2(altered, sample.body + 1 + 2, 65535);
        rejected(altered, "Noncanonical modified UTF-8");
    }

    @Test void memberInterfaceAttributeAndExceptionCountsAreFinite() throws Exception {
        var original = sample(false);
        for (int offset : List.of(original.interfaces, original.fields, original.methods,
                original.methodAttributes, original.nestedAttributes, original.classAttributes, original.exceptionCount)) {
            var sample = sample(false); u2(sample.bytes, offset, 65);
            var failure = assertThrows(TransformationException.class, () -> NetworkClassLimits.check(sample.bytes));
            assertTrue(failure.getMessage().contains("count exceeds"), failure.getMessage());
        }
    }

    @Test void duplicateCodeAndNestedCodeAttributesAreNotValidContainers() throws Exception {
        var sample = sample(false);
        byte[] attribute = Arrays.copyOfRange(sample.bytes, sample.codeName, sample.classAttributes);
        byte[] doubled = new byte[sample.bytes.length + attribute.length];
        System.arraycopy(sample.bytes, 0, doubled, 0, sample.classAttributes);
        System.arraycopy(attribute, 0, doubled, sample.classAttributes, attribute.length);
        System.arraycopy(sample.bytes, sample.classAttributes, doubled, sample.classAttributes + attribute.length, 2);
        u2(doubled, sample.methodAttributes, 2);
        rejected(doubled, "duplicate or not on a method");
        rejected(sample(true).bytes, "duplicate or not on a method");
    }

    @Test void passingTheAllocationGateDoesNotMeanOpcodeSemanticsWereChecked() throws Exception {
        var sample = sample(false); sample.bytes[sample.body + 8] = (byte) 255;
        assertDoesNotThrow(() -> NetworkClassLimits.check(sample.bytes), "ASM validation remains a separate mandatory gate");
    }

    @Test void ordinaryShallowAnnotationHasBoundedParsingWithoutSemanticApproval() throws Exception {
        byte[] bytes = withAnnotationAttribute("RuntimeVisibleAnnotations", annotationPayload(0, false));
        assertDoesNotThrow(() -> NetworkClassLimits.check(bytes));
    }

    @Test void deeplyNestedAnnotationsAreRejectedBeforeAsmWithoutOverflowingTheJvmStack() throws Exception {
        // Iterative raw construction: neither ASM writer recursion nor a deliberately overflowing JVM is used.
        byte[] bytes = withAnnotationAttribute("RuntimeVisibleAnnotations", annotationPayload(2000, false));
        assertTrue(bytes.length < NetworkClassLimits.MAX_CLASS_BYTES);
        rejected(bytes, "Annotation nesting depth");
        var failure = assertThrows(TransformationException.class, () -> NetworkSnapshotRecipe.validateRaw(
                java.util.Map.of("allocationfixture/OnlyStatic.class", bytes), "legacybridge_network", "1.0.0"));
        assertTrue(failure.getMessage().startsWith("NETWORK_CLASS_LIMIT:"), "Raw bounded rejection must precede ClassReader and recipe matching");
    }

    @Test void deeplyNestedAnnotationArraysHaveTheSamePreAsmBound() throws Exception {
        byte[] bytes = withAnnotationAttribute("RuntimeInvisibleAnnotations", annotationPayload(2000, true));
        assertTrue(bytes.length < NetworkClassLimits.MAX_CLASS_BYTES);
        rejected(bytes, "Annotation nesting depth");
    }

    @Test void annotationTablePairAndArrayCountsCannotCauseUnboundedRecursionOrAllocation() throws Exception {
        byte[] tooManyAnnotations = {0, 65};
        rejected(withAnnotationAttribute("RuntimeVisibleAnnotations", tooManyAnnotations), "Annotation count");
        byte[] tooManyPairs = {0, 1, 0, 9, 0, 65};
        rejected(withAnnotationAttribute("RuntimeVisibleAnnotations", tooManyPairs), "Annotation pair count");
        byte[] tooManyArrayValues = {0, 1, 0, 9, 0, 1, 0, 10, '[', 0, 65};
        rejected(withAnnotationAttribute("RuntimeVisibleAnnotations", tooManyArrayValues), "Annotation array count");
    }

    @Test void unknownAnnotationTagsAndTrailingContentRemainStructuredFailures() throws Exception {
        byte[] unknownTag = {0, 1, 0, 9, 0, 1, 0, 10, '!'};
        rejected(withAnnotationAttribute("RuntimeVisibleAnnotations", unknownTag), "Unknown annotation");
        byte[] valid = annotationPayload(0, false);
        rejected(withAnnotationAttribute("RuntimeVisibleAnnotations", Arrays.copyOf(valid, valid.length + 1)), "Trailing bytes in annotation");
    }

    @Test void individuallySmallAnnotationsCannotExceedTheCombinedValueBudget() throws Exception {
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        out.writeShort(17);
        for (int annotation = 0; annotation < 17; annotation++) {
            out.writeShort(9); out.writeShort(64);
            for (int pair = 0; pair < 64; pair++) { out.writeShort(10); out.writeByte('s'); out.writeShort(1); }
        }
        out.flush();
        rejected(withAnnotationAttribute("RuntimeVisibleAnnotations", bytes.toByteArray()), "Annotation value count");
    }

    @Test void unreviewedTypeParameterAndDefaultAnnotationAttributesAreRefusedBeforeAsm() throws Exception {
        for (String name : List.of("RuntimeVisibleTypeAnnotations", "RuntimeInvisibleTypeAnnotations",
                "RuntimeVisibleParameterAnnotations", "RuntimeInvisibleParameterAnnotations", "AnnotationDefault"))
            rejected(withAnnotationAttribute(name, new byte[0]), "outside the reviewed profile");
    }

    @Test
    @EnabledIfSystemProperty(named = "legacybridge.verifyNetworkEnvironment", matches = "true")
    void genuineJava8FixtureClassesPassTheRawGateWithoutLoadingOriginalCode() throws Exception {
        Path path = Path.of(System.getProperty("legacybridge.fixtureRoot"), "network-snapshot-1.16.5/build/libs/legacybridge-network-1.16.5-1.0.0.jar");
        int count = 0;
        try (var zip = new ZipFile(path.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); if (!entry.getName().endsWith(".class")) continue;
                try (var input = zip.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes(NetworkClassLimits.MAX_CLASS_BYTES + 1);
                    assertDoesNotThrow(() -> NetworkClassLimits.check(bytes), entry.getName());
                }
                count++;
            }
        }
        assertEquals(2, count);
    }

    private static void rejected(byte[] bytes, String message) {
        var failure = assertThrows(TransformationException.class, () -> NetworkClassLimits.check(bytes));
        assertTrue(failure.getMessage().startsWith("NETWORK_CLASS_LIMIT:"), failure.getMessage());
        assertTrue(failure.getMessage().contains(message), failure.getMessage());
    }

    private static Sample sample(boolean nestedCode) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        out.writeInt(0xCAFEBABE); out.writeShort(0); out.writeShort(52); out.writeShort(8);
        utf(out, "allocationfixture/OnlyStatic");
        out.writeByte(7); out.writeShort(1);
        utf(out, "java/lang/Object");
        out.writeByte(7); out.writeShort(3);
        utf(out, "run"); utf(out, "()V"); utf(out, "Code");
        out.writeShort(0x21); out.writeShort(2); out.writeShort(4);
        int interfaces = bytes.size(); out.writeShort(0);
        int fields = bytes.size(); out.writeShort(0);
        int methods = bytes.size(); out.writeShort(1);
        out.writeShort(0x09);
        int methodName = bytes.size(); out.writeShort(5); out.writeShort(6);
        int methodAttributes = bytes.size(); out.writeShort(1);
        int codeName = bytes.size(); out.writeShort(7);
        int codeLength = bytes.size(); out.writeInt(nestedCode ? 19 : 13);
        int body = bytes.size(); out.writeShort(0); out.writeShort(0); out.writeInt(1); out.writeByte(177);
        int exceptionCount = bytes.size(); out.writeShort(0);
        int nestedAttributes = bytes.size(); out.writeShort(nestedCode ? 1 : 0);
        if (nestedCode) { out.writeShort(7); out.writeInt(0); }
        int classAttributes = bytes.size(); out.writeShort(0);
        out.flush();
        return new Sample(bytes.toByteArray(), interfaces, fields, methods, methodName, methodAttributes,
                codeName, codeLength, body, exceptionCount, nestedAttributes, classAttributes);
    }

    private static byte[] annotationPayload(int depth, boolean arrays) throws IOException {
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        out.writeShort(1); out.writeShort(9); out.writeShort(1); out.writeShort(10);
        for (int index = 0; index < depth; index++) {
            if (arrays) { out.writeByte('['); out.writeShort(1); }
            else { out.writeByte('@'); out.writeShort(9); out.writeShort(1); out.writeShort(10); }
        }
        out.writeByte('s'); out.writeShort(1); out.flush();
        return bytes.toByteArray();
    }

    private static byte[] withAnnotationAttribute(String name, byte[] payload) throws IOException {
        var sample = sample(false);
        int classHeader = sample.interfaces - 6;
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        out.write(sample.bytes, 0, classHeader);
        utf(out, name); utf(out, "Lallocationfixture/Annotation;"); utf(out, "value"); // CP slots 8, 9, 10.
        out.write(sample.bytes, classHeader, sample.classAttributes - classHeader);
        out.writeShort(1); out.writeShort(8); out.writeInt(payload.length); out.write(payload); out.flush();
        byte[] result = bytes.toByteArray(); u2(result, 8, 11); return result;
    }

    private static void utf(DataOutputStream out, String value) throws IOException { out.writeByte(1); out.writeUTF(value); }
    private static void u2(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static void u4(byte[] bytes, int at, long value) { u2(bytes, at, (int) (value >>> 16)); u2(bytes, at + 2, (int) value); }
}
