package dev.legacybridge.converter.transformation;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BytecodeValidatorTest {
    @Test
    void checksValidBytecodeWithoutInitializingTheClass() {
        String property = "legacybridge.test.offline.validation.initialized";
        System.clearProperty(property);
        ClassWriter writer = type("example/DoNotInitialize");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        method.visitCode();
        method.visitLdcInsn(property);
        method.visitLdcInsn("bad");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 0);
        method.visitEnd();
        byte[] bytes = writer.toByteArray();
        assertDoesNotThrow(() -> BytecodeValidator.validate(bytes));
        assertDoesNotThrow(() -> BytecodeValidator.validate(bytes, Map.of()));
        assertNull(System.getProperty(property));
    }

    @Test
    void rejectsWrongReturnOpcode() {
        ClassWriter writer = type("example/WrongReturn");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "broken", "()I", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        assertThrows(TransformationException.class, () -> BytecodeValidator.validate(writer.toByteArray()));
    }

    @Test
    void rejectsStackUnderflowAndIncorrectMaxStack() {
        ClassWriter underflow = type("example/Underflow");
        MethodVisitor method = underflow.visitMethod(Opcodes.ACC_STATIC, "broken", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        assertThrows(TransformationException.class, () -> BytecodeValidator.validate(underflow.toByteArray()));
        ClassWriter tooSmall = type("example/TooSmall");
        method = tooSmall.visitMethod(Opcodes.ACC_STATIC, "broken", "()I", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        assertThrows(TransformationException.class, () -> BytecodeValidator.validate(tooSmall.toByteArray()));
    }

    @Test
    void rejectsDuplicateFieldsAndMethods() {
        ClassWriter fields = type("example/DuplicateFields");
        fields.visitField(Opcodes.ACC_PUBLIC, "value", "I", null, null).visitEnd();
        fields.visitField(Opcodes.ACC_PUBLIC, "value", "I", null, null).visitEnd();
        assertTrue(assertThrows(TransformationException.class,
                () -> BytecodeValidator.validate(fields.toByteArray())).getMessage().contains("Duplicate field"));
        ClassWriter methods = type("example/DuplicateMethods");
        returnVoid(methods, "value");
        returnVoid(methods, "value");
        assertTrue(assertThrows(TransformationException.class,
                () -> BytecodeValidator.validate(methods.toByteArray())).getMessage().contains("Duplicate method"));
    }

    @Test
    void rejectsMissingFramesAndInvalidDeclaredFrameTypes() {
        for (boolean supplyBadFrame : new boolean[] {false, true}) {
            ClassWriter writer = type("example/BadFrame");
            MethodVisitor method = writer.visitMethod(Opcodes.ACC_STATIC, "broken", "(I)I", null, null);
            method.visitCode();
            method.visitVarInsn(Opcodes.ILOAD, 0);
            Label target = new Label();
            method.visitJumpInsn(Opcodes.IFEQ, target);
            method.visitInsn(Opcodes.ICONST_1);
            method.visitInsn(Opcodes.IRETURN);
            method.visitLabel(target);
            if (supplyBadFrame) method.visitFrame(Opcodes.F_FULL, 1, new Object[] {Opcodes.FLOAT}, 0, new Object[0]);
            method.visitInsn(Opcodes.ICONST_0);
            method.visitInsn(Opcodes.IRETURN);
            method.visitMaxs(1, 1);
            method.visitEnd();
            assertThrows(TransformationException.class, () -> BytecodeValidator.validate(writer.toByteArray()));
        }
    }

    @Test
    void rejectsDeclaredTopLocalWhenCodeStillReadsIt() {
        ClassWriter writer = type("example/DiscardedLocal");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_STATIC, "broken", "(I)I", null, null);
        method.visitCode();
        Label target = new Label();
        method.visitJumpInsn(Opcodes.GOTO, target);
        method.visitLabel(target);
        method.visitFrame(Opcodes.F_FULL, 0, new Object[0], 0, new Object[0]);
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        assertThrows(TransformationException.class, () -> BytecodeValidator.validate(writer.toByteArray()));
    }

    @Test
    void strictValidationRejectsIncompatibleReferenceReturnWithoutLoadingIt() {
        ClassWriter writer = type("example/WrongReference");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_STATIC, "broken", "(Ljava/lang/Object;)Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        byte[] bytes = writer.toByteArray();
        assertDoesNotThrow(() -> BytecodeValidator.validate(bytes));
        assertThrows(TransformationException.class, () -> BytecodeValidator.validate(bytes, Map.of()));
    }

    @Test
    void strictValidationRejectsMissingRequiredHierarchy() {
        ClassWriter writer = type("example/MissingHierarchy");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_STATIC, "broken", "(Labsent/Bad;)Ljava/lang/Object;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        byte[] bytes = writer.toByteArray();
        assertDoesNotThrow(() -> BytecodeValidator.validate(bytes));
        assertTrue(assertThrows(TransformationException.class,
                () -> BytecodeValidator.validate(bytes, Map.of())).getMessage().contains("Missing offline hierarchy"));
    }

    @Test
    void rejectsUnreadableClassFiles() {
        assertThrows(TransformationException.class, () -> BytecodeValidator.validate(new byte[] {1, 2, 3}));
        assertThrows(TransformationException.class, () -> BytecodeValidator.validate(null));
        byte[] wrongMagic = type("fixture/WrongMagic").toByteArray();
        wrongMagic[0] = 0;
        assertThrows(TransformationException.class, () -> BytecodeValidator.validate(wrongMagic));
    }

    static ClassWriter type(String name) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        return writer;
    }

    private static void returnVoid(ClassWriter writer, String name) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }
}
