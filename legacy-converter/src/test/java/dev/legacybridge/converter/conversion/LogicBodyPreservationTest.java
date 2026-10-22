package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.transformation.TransformationException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class LogicBodyPreservationTest {
    @Test void identicalBodyAndDebugAreRetained() { var source = fixture(); var copy = copy(source); assertDoesNotThrow(() -> LogicBodyPreservation.verify(source, copy)); }
    @Test void changedLiteralIsNotANameMapping() { var source = fixture(); var copy = copy(source); ((LdcInsnNode)copy.methods.get(0).instructions.getFirst()).cst = 8; assertThrows(TransformationException.class, () -> LogicBodyPreservation.verify(source, copy)); }
    @Test void generatedBootstrapCannotReplaceOriginalBody() { var source = fixture(); var copy = copy(source); copy.methods.get(0).instructions.clear(); assertThrows(TransformationException.class, () -> LogicBodyPreservation.verify(source, copy)); }
    @Test void memberLossAndStateMutationReject() { var source = fixture(); var copy = copy(source); copy.fields.get(0).value = 9; assertThrows(TransformationException.class, () -> LogicBodyPreservation.verify(source, copy)); copy.fields.clear(); assertThrows(TransformationException.class, () -> LogicBodyPreservation.verify(source, copy)); }
    private static ClassNode fixture() {
        var node = new ClassNode(); node.version = Opcodes.V1_8; node.name = "owned/Test"; node.superName = "java/lang/Object"; node.sourceFile = "Test.java";
        node.fields.add(new FieldNode(Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "STATE", "I", null, 7));
        var method = new MethodNode(Opcodes.ACC_STATIC, "score", "()I", null, null); method.instructions.add(new LdcInsnNode(7)); method.instructions.add(new InsnNode(Opcodes.IRETURN)); node.methods.add(method); return node;
    }
    private static ClassNode copy(ClassNode source) { var copy = new ClassNode(); source.accept(copy); return copy; }
}
