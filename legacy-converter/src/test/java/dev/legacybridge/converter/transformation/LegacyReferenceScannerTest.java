package dev.legacybridge.converter.transformation;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LineNumberNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.legacybridge.converter.transformation.LegacyReferenceScanner.ReferenceKind.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyReferenceScannerTest {
    private final LegacyReferenceScanner scanner = new LegacyReferenceScanner();

    @Test
    void retainsDebugFramesAndEveryLocatedExternalOccurrenceWithoutExecution() {
        String sentinel = "legacybridge.scanner.executed";
        assertNull(System.getProperty(sentinel));
        byte[] original = richFixture();
        var result = scanner.scan(Map.of("fixture/Test.class", original));
        var scanned = result.classes().get("fixture/Test");
        assertEquals("fixture/Test.java", scanned.location());
        assertEquals("Test.java", scanned.sourceFile());
        assertEquals("SMAP\nTest.java\nJava\n*S Java\n*E\n", scanned.sourceDebug());
        var references = result.references();
        assertTrue(references.stream().anyMatch(ref -> ref.kind() == SUPERCLASS && ref.owner().equals("java/lang/Object")));
        assertTrue(references.stream().anyMatch(ref -> ref.kind() == INTERFACE && ref.owner().equals("external/Marker")));
        assertTrue(references.stream().anyMatch(ref -> ref.kind() == FIELD && ref.owner().equals("net/minecraft/item/Item")
                && ref.name().equals("EMPTY") && ref.descriptor().equals("Lnet/minecraft/item/Item;")
                && ref.lineNumber() == 42 && ref.instructionIndex() >= 0
                && ref.enclosingMethod().equals("run()V") && ref.location().equals("fixture/Test.java:42")));
        assertEquals(2, references.stream().filter(ref -> ref.kind() == FIELD && ref.name().equals("EMPTY")).count());
        assertTrue(references.stream().anyMatch(ref -> ref.kind() == METHOD && ref.name().equals("accept")
                && ref.owner().equals("net/minecraftforge/common/MinecraftForge")));
        assertTrue(references.stream().anyMatch(ref -> ref.kind() == FRAME && ref.owner().equals("external/FrameType")));
        assertTrue(references.stream().anyMatch(ref -> ref.kind() == DESCRIPTOR && ref.owner().equals("external/Local")));
        assertTrue(references.stream().anyMatch(ref -> ref.kind() == ANNOTATION && ref.owner().equals("external/Annotation")));
        assertTrue(references.stream().anyMatch(ref -> ref.kind() == FIELD && ref.owner().equals("external/Mode") && ref.name().equals("VALUE")));
        assertTrue(scanned.readNode().methods.stream().anyMatch(method -> method.localVariables != null && !method.localVariables.isEmpty()));
        assertTrue(scanned.readNode().methods.stream().flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .anyMatch(insn -> insn instanceof LineNumberNode));
        assertNull(System.getProperty(sentinel), "Static initializer must never run");
        assertArrayEquals(original, scanned.originalBytes());
    }

    @Test
    void traversesInvokeDynamicHandlesTypesAndNestedConstantDynamic() {
        ClassWriter writer = writer("fixture/Dynamic", Opcodes.V11, null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()V", null, null);
        method.visitCode();
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, "external/Bootstrap", "link",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
        Handle target = new Handle(Opcodes.H_INVOKESTATIC, "net/minecraftforge/legacy/Event", "register",
                "(Lnet/minecraft/item/Item;)V", false);
        method.visitInvokeDynamicInsn("run", "()Ljava/lang/Runnable;", bootstrap,
                Type.getMethodType("(Lexternal/Argument;)V"), target);
        method.visitInsn(Opcodes.POP);
        Handle constantBootstrap = new Handle(Opcodes.H_INVOKESTATIC, "external/Constants", "constant",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;", false);
        ConstantDynamic nested = new ConstantDynamic("nested", "Lexternal/Nested;", constantBootstrap,
                new Handle(Opcodes.H_GETSTATIC, "external/Holder", "VALUE", "Lexternal/Value;", false));
        method.visitLdcInsn(new ConstantDynamic("outer", "Lexternal/Outer;", constantBootstrap, nested));
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(1, 0); method.visitEnd(); writer.visitEnd();
        var refs = scanner.scan(Map.of("fixture/Dynamic.class", writer.toByteArray())).references();
        assertTrue(refs.stream().anyMatch(ref -> ref.kind() == METHOD && ref.owner().equals("external/Bootstrap")
                && ref.name().equals("link") && ref.location().contains("handle tag=6")));
        assertTrue(refs.stream().anyMatch(ref -> ref.kind() == METHOD && ref.owner().equals("net/minecraftforge/legacy/Event")));
        assertTrue(refs.stream().anyMatch(ref -> ref.kind() == FIELD && ref.owner().equals("external/Holder") && ref.name().equals("VALUE")));
        for (String owner : List.of("external/Argument", "external/Nested", "external/Outer", "external/Value")) {
            assertTrue(refs.stream().anyMatch(ref -> ref.kind() == DESCRIPTOR && ref.owner().equals(owner)), owner);
        }
    }

    @Test
    void tracksGenericOuterAndInnerTypesWithoutConfusingTypeArguments() {
        ClassWriter writer = writer("fixture/Generics", Opcodes.V1_8, null);
        writer.visitField(Opcodes.ACC_PUBLIC, "value", "Lexternal/Outer$Inner;",
                "Lexternal/Outer<Lexternal/Argument;>.Inner<Lexternal/InnerArgument;>;", null).visitEnd();
        writer.visitEnd();
        var refs = scanner.scan(Map.of("fixture/Generics.class", writer.toByteArray())).references();
        for (String owner : List.of("external/Outer", "external/Outer$Inner", "external/Argument", "external/InnerArgument")) {
            assertTrue(refs.stream().anyMatch(ref -> ref.kind() == SIGNATURE && ref.owner().equals(owner)), owner);
        }
        assertFalse(refs.stream().anyMatch(ref -> ref.owner().equals("external/Argument$Inner")));
    }

    @Test
    void excludesSourceOwnedSymbolsButIncludesTheirExternalDescriptorTypes() {
        ClassWriter first = writer("fixture/First", Opcodes.V1_8, null);
        MethodVisitor method = first.visitMethod(Opcodes.ACC_STATIC, "run", "()V", null, null);
        method.visitCode(); method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "fixture/Second", "accept", "(Lexternal/Argument;)V", false);
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(1, 0); method.visitEnd(); first.visitEnd();
        var refs = scanner.scan(Map.of("fixture/First.class", first.toByteArray(), "fixture/Second.class", empty("fixture/Second"))).references();
        assertFalse(refs.stream().anyMatch(ref -> ref.owner().startsWith("fixture/")));
        assertTrue(refs.stream().anyMatch(ref -> ref.owner().equals("external/Argument")));
    }

    @Test
    void scanResultIsDeeplyImmutableAndReturnsIndependentTrees() {
        byte[] original = empty("fixture/Immutable");
        var result = scanner.scan(Map.of("fixture/Immutable.class", original));
        original[0] = 0;
        var scanned = result.classes().get("fixture/Immutable");
        byte[] returned = scanned.originalBytes(); returned[0] = 0;
        assertEquals((byte) 0xca, scanned.originalBytes()[0]);
        var first = result.classNodes(); first.get("fixture/Immutable").name = "changed";
        assertEquals("fixture/Immutable", result.classNodes().get("fixture/Immutable").name);
        assertThrows(UnsupportedOperationException.class, () -> result.classes().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.references().clear());
        assertThrows(UnsupportedOperationException.class, () -> scanned.references().clear());
    }

    @Test
    void refusesProtectedDefinitionsEvenWhenEntryNameDisguisesOwnership() {
        for (String prefix : List.of("net/minecraft/", "net/minecraftforge/", "com/mojang/", "cpw/mods/",
                "java/", "javax/", "jdk/", "sun/", "dev/legacybridge/runtime/")) {
            var failure = assertThrows(TransformationException.class,
                    () -> scanner.scan(Map.of("fixture/Disguised.class", empty(prefix + "Intruder"))));
            assertTrue(failure.getMessage().contains("protected class definition"), prefix);
        }
    }

    @Test
    void rejectsEntryDeclarationMismatchesAndMultiReleaseAliases() {
        byte[] bytes = empty("fixture/Example");
        for (String path : List.of("fixture/Alias.class", "META-INF/versions/17/fixture/Example.class", "../fixture/Example.class")) {
            assertThrows(TransformationException.class, () -> scanner.scan(Map.of(path, bytes)), path);
        }
    }

    @Test
    void rejectsMissingMalformedOversizedAndTooManyClasses() {
        assertThrows(TransformationException.class, () -> scanner.scan(Map.of()));
        assertThrows(TransformationException.class, () -> scanner.scan(Map.of("Broken.class", new byte[]{1, 2, 3})));
        byte[] badMagic = empty("fixture/BadMagic"); badMagic[0] = 0;
        assertThrows(TransformationException.class, () -> scanner.scan(Map.of("fixture/BadMagic.class", badMagic)));
        Map<String, byte[]> nullPath = new LinkedHashMap<>(); nullPath.put(null, empty("fixture/Null"));
        assertThrows(TransformationException.class, () -> scanner.scan(nullPath));
        assertThrows(TransformationException.class,
                () -> scanner.scan(Map.of("Huge.class", new byte[LegacyReferenceScanner.MAX_CLASS_BYTES + 1])));
        Map<String, byte[]> tooMany = new LinkedHashMap<>();
        for (int index = 0; index <= LegacyReferenceScanner.MAX_CLASSES; index++) {
            tooMany.put("fixture/Class" + index + ".class", new byte[0]);
        }
        assertThrows(TransformationException.class, () -> scanner.scan(tooMany));
    }

    @Test
    void abortsRatherThanSilentlyTruncatingLargeReferenceInventory() {
        ClassWriter writer = writer("fixture/Many", Opcodes.V1_8, null);
        for (int index = 0; index < 70; index++) {
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_STATIC, "method" + index, "()V", null, null);
            method.visitCode();
            for (int instruction = 0; instruction < 800; instruction++) {
                method.visitFieldInsn(Opcodes.GETSTATIC, "external/Holder", "value", "Lexternal/Value;");
                method.visitInsn(Opcodes.POP);
            }
            method.visitInsn(Opcodes.RETURN); method.visitMaxs(1, 0); method.visitEnd();
        }
        writer.visitEnd();
        var failure = assertThrows(TransformationException.class,
                () -> scanner.scan(Map.of("fixture/Many.class", writer.toByteArray())));
        assertTrue(failure.getMessage().contains("100000"));
        assertTrue(failure.getMessage().contains("no references were silently truncated"));
    }

    @Test
    void untrustedSourceFileCannotEscapePackageLocation() {
        ClassWriter writer = writer("fixture/BadSource", Opcodes.V1_8, null);
        writer.visitSource("../../outside.java", null); writer.visitEnd();
        assertEquals("fixture/BadSource.class", scanner.scan(Map.of("fixture/BadSource.class", writer.toByteArray()))
                .classes().get("fixture/BadSource").location());
    }

    private static byte[] richFixture() {
        ClassWriter writer = writer("fixture/Test", Opcodes.V1_8, new String[]{"external/Marker"});
        writer.visitSource("Test.java", "SMAP\nTest.java\nJava\n*S Java\n*E\n");
        AnnotationVisitor annotation = writer.visitAnnotation("Lexternal/Annotation;", true);
        annotation.visit("type", Type.getType("Lexternal/AnnotatedType;"));
        annotation.visitEnum("mode", "Lexternal/Mode;", "VALUE"); annotation.visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()V", null, null);
        Label start = new Label(); Label end = new Label();
        method.visitCode(); method.visitLabel(start); method.visitLineNumber(42, start);
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/item/Item", "EMPTY", "Lnet/minecraft/item/Item;");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraftforge/common/MinecraftForge", "accept", "(Lnet/minecraft/item/Item;)V", false);
        method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/item/Item", "EMPTY", "Lnet/minecraft/item/Item;");
        method.visitInsn(Opcodes.POP); method.visitInsn(Opcodes.RETURN);
        method.visitLabel(end); method.visitFrame(Opcodes.F_FULL, 0, new Object[0], 1, new Object[]{"external/FrameType"});
        method.visitInsn(Opcodes.POP); method.visitInsn(Opcodes.RETURN);
        method.visitLocalVariable("debug", "Lexternal/Local;", null, start, end, 0);
        method.visitMaxs(1, 1); method.visitEnd();
        MethodVisitor initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode(); initializer.visitLdcInsn("legacybridge.scanner.executed"); initializer.visitLdcInsn("true");
        initializer.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        initializer.visitInsn(Opcodes.POP); initializer.visitInsn(Opcodes.RETURN); initializer.visitMaxs(2, 0); initializer.visitEnd();
        writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] empty(String name) {
        ClassWriter writer = writer(name, Opcodes.V1_8, null); writer.visitEnd(); return writer.toByteArray();
    }

    private static ClassWriter writer(String name, int version, String[] interfaces) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(version, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", interfaces);
        return writer;
    }
}
