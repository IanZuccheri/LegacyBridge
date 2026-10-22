package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.transformation.TransformationException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class NetworkRecipeTest {
    @Test void debugDifferencesAreNotExecutableApproval() {
        ClassNode node = node();
        var before = NetworkSnapshotRecipe.fingerprints(Map.of(node.name, node));
        var label = new LabelNode();
        node.methods.get(0).instructions.insert(label);
        node.methods.get(0).instructions.insert(label, new LineNumberNode(1234, label));
        node.sourceFile = "DebugOnly.java";
        assertEquals(before, NetworkSnapshotRecipe.fingerprints(Map.of(node.name, node)));
        assertThrows(TransformationException.class, () -> NetworkSnapshotRecipe.validate(Map.of(node.name, node), "legacybridge_network", "1.0.0"));
    }
    @Test void constantsCallsFieldsAndAccessAreAllRecipeInput() {
        ClassNode node = node();
        var before = NetworkSnapshotRecipe.fingerprints(Map.of(node.name, node));
        node.methods.get(0).instructions.insert(new InsnNode(Opcodes.NOP));
        assertNotEquals(before, NetworkSnapshotRecipe.fingerprints(Map.of(node.name, node)));
        node = node(); node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "unexpected", "I", null, 7));
        assertNotEquals(before, NetworkSnapshotRecipe.fingerprints(Map.of(node.name, node)));
        node = node(); node.access |= Opcodes.ACC_FINAL;
        assertNotEquals(before, NetworkSnapshotRecipe.fingerprints(Map.of(node.name, node)));
    }
    @Test void unboundBranchesAndAnnotationsDoNotReceiveFingerprints() {
        ClassNode node = node();
        node.methods.get(0).instructions.insert(new JumpInsnNode(Opcodes.GOTO, new LabelNode()));
        assertThrows(TransformationException.class, () -> NetworkSnapshotRecipe.fingerprints(Map.of(node.name, node)));
        ClassNode other = node(); other.visibleAnnotations = java.util.List.of(new AnnotationNode("Lforeign/Callback;"));
        assertThrows(TransformationException.class, () -> NetworkSnapshotRecipe.fingerprints(Map.of(other.name, other)));
    }
    @Test void overlargeGraphsAndNativeBodiesBlock() {
        ClassNode node = node(); node.methods.get(0).access |= Opcodes.ACC_NATIVE;
        assertThrows(TransformationException.class, () -> NetworkSnapshotRecipe.fingerprints(Map.of(node.name, node)));
        ClassNode large = node();
        for (int i = 0; i < 4097; i++) large.methods.get(0).instructions.insert(new InsnNode(Opcodes.NOP));
        assertThrows(TransformationException.class, () -> NetworkSnapshotRecipe.fingerprints(Map.of(large.name, large)));
    }
    private static ClassNode node() {
        ClassNode n = new ClassNode(); n.version = Opcodes.V1_8; n.access = Opcodes.ACC_PUBLIC;
        n.name = "example/Unknown"; n.superName = "java/lang/Object";
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "()I", null, null);
        m.instructions.add(new InsnNode(Opcodes.ICONST_0)); m.instructions.add(new InsnNode(Opcodes.IRETURN)); n.methods.add(m);
        return n;
    }
}
