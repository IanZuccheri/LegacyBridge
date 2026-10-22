package dev.legacybridge.converter.transformation.linkage;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Inert class trees only. No fixture class is defined or executed. */
class OwnedConstructorGraphTest {
    private static final String OWNER = "fixture/Constructed";
    @Test void resolvesFiniteArgumentsWithoutExecutingBodiesAndKeepsOldVerifierClosed() {
        var node = chain(); var proof = OwnedConstructorGraph.inspect(node);
        assertEquals(List.of("()V", "(I)V", "(II)V"), proof.chain().stream().map(OwnedConstructorGraph.Step::descriptor).toList());
        assertEquals(List.of(16, 3), proof.terminal().arguments());
        assertEquals(false, proof.toTree().get("classesExecuted"));
        assertThrows(UnsupportedOperationException.class, () -> proof.chain().clear());
        var bytes = bytes(node); var verifier = new OfflineLinkageVerifier();
        assertThrows(TransformationException.class, () -> verifier.verify(bytes, new ApiIndex(Map.of())));
        assertEquals("PASSED_SCOPED", verifier.verifyOwnedConstructors(bytes, new ApiIndex(Map.of()), OWNER).status());
        assertThrows(TransformationException.class, () -> verifier.verifyOwnedConstructors(bytes, new ApiIndex(Map.of()), "fixture/Other"));
    }

    @ParameterizedTest @ValueSource(strings = {"CYCLE", "OPCODE", "INTERFACE", "ACCESS", "MISSING", "REFERENCE", "WIDE", "UNREACHABLE", "COMPUTED", "BAD_SLOT", "RECONSTRUCTION", "RECEIVER", "SECOND_CALL"})
    void blocksUnprovedDelegation(String defect) {
        var node = chain(); var root = node.methods.get(0); var terminal = node.methods.get(2);
        var edge = (MethodInsnNode) OwnedConstructorGraph.code(root).get(2);
        switch (defect) {
            case "CYCLE" -> { terminal.instructions.clear(); terminal.instructions.add(new VarInsnNode(ALOAD, 0)); terminal.instructions.add(new MethodInsnNode(INVOKESPECIAL, OWNER, "<init>", "()V", false)); terminal.instructions.add(new InsnNode(RETURN)); }
            case "OPCODE" -> edge.setOpcode(INVOKEVIRTUAL);
            case "INTERFACE" -> edge.itf = true;
            case "ACCESS" -> node.methods.get(1).access = ACC_PUBLIC;
            case "MISSING" -> node.methods.remove(1);
            case "REFERENCE" -> terminal.desc = "(Ljava/lang/Object;)V";
            case "WIDE" -> terminal.desc = "(J)V";
            case "UNREACHABLE" -> { var extra = new MethodNode(ACC_PRIVATE, "<init>", "(III)V", null, null); extra.instructions.add(new VarInsnNode(ALOAD, 0)); extra.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)); extra.instructions.add(new InsnNode(RETURN)); node.methods.add(extra); }
            case "COMPUTED" -> root.instructions.set(OwnedConstructorGraph.code(root).get(1), new MethodInsnNode(INVOKESTATIC, OWNER, "number", "()I", false));
            case "BAD_SLOT" -> root.instructions.set(OwnedConstructorGraph.code(root).get(1), new VarInsnNode(ILOAD, 2));
            case "RECONSTRUCTION" -> terminal.instructions.insert(new TypeInsnNode(NEW, OWNER));
            case "RECEIVER" -> ((VarInsnNode) root.instructions.getFirst()).var = 1;
            case "SECOND_CALL" -> root.instructions.insertBefore(root.instructions.getLast(), new MethodInsnNode(INVOKESPECIAL, OWNER, "<init>", "()V", false));
        }
        assertThrows(TransformationException.class, () -> OwnedConstructorGraph.inspect(node), defect);
    }

    @Test void graphAloneDoesNotAuthorizeInvalidInitializationOrExternalCalls() {
        for (boolean uninitialized : List.of(true, false)) {
            var node = chain(); var terminal = node.methods.get(2);
            if (uninitialized) terminal.instructions.remove(OwnedConstructorGraph.code(terminal).get(1));
            else terminal.instructions.insertBefore(terminal.instructions.getLast(), new MethodInsnNode(INVOKESTATIC, "unknown/Api", "run", "()V", false));
            assertNotNull(OwnedConstructorGraph.inspect(node));
            assertThrows(TransformationException.class, () -> new OfflineLinkageVerifier().verifyOwnedConstructors(bytes(node), new ApiIndex(Map.of()), OWNER));
        }
    }

    @Test void delegationPermissionDoesNotLeakToOtherOwners() {
        var other = chain(); other.name = "fixture/Other";
        for (var method : other.methods) for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(OWNER)) call.owner = other.name;
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); other.accept(writer);
        var both = new HashMap<>(bytes(chain())); both.put(other.name + ".class", writer.toByteArray());
        assertThrows(TransformationException.class, () -> new OfflineLinkageVerifier().verifyOwnedConstructors(both, new ApiIndex(Map.of()), OWNER));
    }

    private static ClassNode chain() {
        var node = new ClassNode(); node.version = V1_8; node.access = ACC_PUBLIC | ACC_SUPER;
        node.name = OWNER; node.superName = "java/lang/Object";
        var root = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        root.instructions.add(new VarInsnNode(ALOAD, 0)); root.instructions.add(new IntInsnNode(BIPUSH, 16));
        root.instructions.add(new MethodInsnNode(INVOKESPECIAL, OWNER, "<init>", "(I)V", false)); root.instructions.add(new InsnNode(RETURN));
        var middle = new MethodNode(ACC_PRIVATE, "<init>", "(I)V", null, null);
        middle.instructions.add(new VarInsnNode(ALOAD, 0)); middle.instructions.add(new VarInsnNode(ILOAD, 1)); middle.instructions.add(new InsnNode(ICONST_3));
        middle.instructions.add(new MethodInsnNode(INVOKESPECIAL, OWNER, "<init>", "(II)V", false)); middle.instructions.add(new InsnNode(RETURN));
        var end = new MethodNode(ACC_PRIVATE, "<init>", "(II)V", null, null);
        end.instructions.add(new VarInsnNode(ALOAD, 0)); end.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)); end.instructions.add(new InsnNode(RETURN));
        node.methods.addAll(List.of(root, middle, end)); return node;
    }
    private static Map<String,byte[]> bytes(ClassNode node) {
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
        return Map.of(OWNER + ".class", writer.toByteArray());
    }
}
