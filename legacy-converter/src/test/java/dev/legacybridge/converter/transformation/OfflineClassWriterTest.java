package dev.legacybridge.converter.transformation;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineClassWriterTest {
    @Test
    void computesBranchFramesFromSuppliedClassHeadersAndPreservesDebugInformation() {
        Map<String, byte[]> hierarchy = Map.of("example/Parent", header("example/Parent", "java/lang/Object"),
                "example/Left", header("example/Left", "example/Parent"),
                "example/Right", header("example/Right", "example/Parent"));
        byte[] bytes = branchingClass(hierarchy, "example/Left", "example/Right", "example/Parent");
        assertDoesNotThrow(() -> BytecodeValidator.validate(bytes, hierarchy));
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        assertEquals("Choice.java", node.sourceFile);
        assertTrue(node.methods.get(0).localVariables.stream().anyMatch(local -> local.name.equals("choice")));
        assertTrue(java.util.Arrays.stream(node.methods.get(0).instructions.toArray())
                .filter(FrameNode.class::isInstance).map(FrameNode.class::cast)
                .anyMatch(frame -> frame.stack != null && frame.stack.contains("example/Parent")));
    }

    @Test
    void refusesUnknownHierarchyInsteadOfGuessingObject() {
        assertTrue(assertThrows(TransformationException.class,
                () -> branchingClass(Map.of(), "absent/Left", "absent/Right", "java/lang/Object"))
                .getMessage().contains("Missing offline hierarchy"));
    }

    @Test
    void resolvesJdkHierarchyWithoutReflectionOrApplicationClasses() {
        ClassHierarchy hierarchy = new ClassHierarchy(Map.of());
        assertEquals("java/util/AbstractList", hierarchy.commonSuperClass("java/util/ArrayList", "java/util/LinkedList"));
        assertEquals("[Ljava/lang/Object;", hierarchy.commonSuperClass("[Ljava/lang/String;", "[Ljava/lang/Integer;"));
        assertEquals("java/lang/Object", hierarchy.commonSuperClass("[I", "[J"));
        assertEquals("[Ljava/lang/Object;", hierarchy.commonSuperClass("[[I", "[[J"));
        assertEquals("java/lang/Cloneable", hierarchy.commonSuperClass("[I", "java/lang/Cloneable"));
    }

    @Test
    void suppliedClassCannotShadowJdkHierarchy() {
        ClassHierarchy hierarchy = new ClassHierarchy(Map.of("java/lang/String", header("java/lang/String", "absent/Forged")));
        assertEquals("java/lang/Object", hierarchy.commonSuperClass("java/lang/String", "java/lang/Integer"));
    }

    @Test
    void rejectsMismatchedClassEntryNames() {
        assertThrows(TransformationException.class,
                () -> new OfflineClassWriter(ClassWriter.COMPUTE_FRAMES, Map.of("example/Claimed", header("example/Actual", "java/lang/Object"))));
    }

    private static byte[] header(String name, String parent) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, parent, null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] branchingClass(Map<String, byte[]> hierarchy, String left, String right, String parent) {
        OfflineClassWriter writer = new OfflineClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, hierarchy);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "example/Choice", null, "java/lang/Object", null);
        writer.visitSource("Choice.java", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "choose",
                "(ZL" + left + ";L" + right + ";)L" + parent + ";", null, null);
        method.visitCode();
        Label start = new Label();
        Label other = new Label();
        Label merge = new Label();
        Label end = new Label();
        method.visitLabel(start);
        method.visitLineNumber(42, start);
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitJumpInsn(Opcodes.IFEQ, other);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitJumpInsn(Opcodes.GOTO, merge);
        method.visitLabel(other);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitLabel(merge);
        method.visitInsn(Opcodes.ARETURN);
        method.visitLabel(end);
        method.visitLocalVariable("choice", "Z", null, start, end, 0);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
