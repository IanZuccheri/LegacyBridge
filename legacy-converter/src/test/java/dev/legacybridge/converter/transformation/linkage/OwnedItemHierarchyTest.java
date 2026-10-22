package dev.legacybridge.converter.transformation.linkage;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Structural proof tests use inert declaration stubs, not Minecraft API equivalence or class loading. */
class OwnedItemHierarchyTest {
    private static final String ITEM = "net/minecraft/item/Item", BASE = "fixture/base/Base", LEAF = "fixture/leaf/Leaf";
    private static final ApiIndex API = new ApiIndex(Map.of(ITEM,
            new ApiIndex.ApiClass(ITEM, ACC_PUBLIC | ACC_SUPER, "java/lang/Object", List.of(), List.of(), List.of())));

    @Test void distinguishesResolutionVirtualSelectionAndDirectSuperWithoutExecution() {
        var proof = OwnedItemHierarchy.inspect(nodes(), API, ITEM);
        var virtual = proof.sites().stream().filter(s -> s.opcode() == INVOKEVIRTUAL).findFirst().orElseThrow();
        assertEquals(BASE, virtual.resolved().owner()); assertEquals(LEAF, virtual.selected().owner());
        var direct = proof.sites().stream().filter(s -> s.opcode() == INVOKESPECIAL).findFirst().orElseThrow();
        assertEquals(BASE, direct.resolved().owner()); assertEquals(direct.resolved(), direct.selected());
        assertEquals("NON_NULL_REGISTERED_LEAF", proof.toTree().get("receiverAssumption"));
        assertEquals(false, proof.toTree().get("classesExecuted"));
        assertEquals(false, proof.toTree().get("publicationAllowed"));
        assertThrows(UnsupportedOperationException.class, () -> proof.sites().clear());
        var mutable = new ArrayList<>(proof.sites());
        var copy = new OwnedItemHierarchy.Proof(BASE, LEAF, ITEM, mutable); mutable.clear();
        assertEquals(proof, copy);
    }

    @Test void debugNodesDoNotChangeInstructionIdentity() {
        var nodes = nodes(); var before = OwnedItemHierarchy.inspect(nodes, API, ITEM);
        for (var node : nodes.values()) for (var method : node.methods) {
            var label = new LabelNode(); method.instructions.insert(label);
            method.instructions.insert(label, new LineNumberNode(123, label));
        }
        assertEquals(before, OwnedItemHierarchy.inspect(nodes, API, ITEM));
    }

    @Test void admissionIsNotAReplacementForOfflineAccessAndBytecodeVerification() {
        var nodes = nodes(); nodes.get(LEAF).methods.stream().filter(m -> m.name.equals("score"))
                .findFirst().orElseThrow().access = ACC_PUBLIC;
        // Visibility broadening preserves this finite selection; the full candidate still validates every body/access.
        assertNotNull(OwnedItemHierarchy.inspect(nodes, API, ITEM));
        assertThrows(TransformationException.class, () -> OwnedItemHierarchy.inspect(nodes, ApiIndex.empty(), ITEM));
        assertThrows(TransformationException.class, () -> OwnedItemHierarchy.inspect(nodes, API, "invented/Item"));
    }

    @ParameterizedTest @ValueSource(strings = {"BASE_CONCRETE", "LEAF_OPEN", "INTERFACE", "STATIC_INIT", "CTOR_ACCESS",
            "CTOR_ARGS", "CTOR_OPCODE", "METHOD_BOUND", "CODE_BOUND", "CLASS_BOUND", "NO_VIRTUAL", "NO_SUPER",
            "ABSTRACT_TARGET", "PRIVATE_SLOT", "STATIC_SLOT", "INDIRECT_SUPER", "INTERFACE_CALL", "STATIC_CALL"})
    void refusesUnprovedShapeOrDispatch(String defect) {
        var nodes = nodes(); var base = nodes.get(BASE); var leaf = nodes.get(LEAF);
        var virtual = (MethodInsnNode) base.methods.get(2).instructions.get(2);
        var direct = (MethodInsnNode) leaf.methods.get(2).instructions.get(2);
        switch (defect) {
            case "BASE_CONCRETE" -> base.access &= ~ACC_ABSTRACT;
            case "LEAF_OPEN" -> leaf.access &= ~ACC_FINAL;
            case "INTERFACE" -> base.interfaces.add("fixture/Unknown");
            case "STATIC_INIT" -> base.methods.add(new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null));
            case "CTOR_ACCESS" -> base.methods.get(0).access = ACC_PUBLIC;
            case "CTOR_ARGS" -> leaf.methods.get(0).desc = "(I)V";
            case "CTOR_OPCODE" -> ((MethodInsnNode) leaf.methods.get(0).instructions.get(1)).setOpcode(INVOKEVIRTUAL);
            case "METHOD_BOUND" -> { while (base.methods.size() <= 128) base.methods.add(new MethodNode(ACC_PUBLIC, "unused", "()V", null, null)); }
            case "CODE_BOUND" -> { for (int i = 0; i < 4097; i++) base.methods.get(0).instructions.add(new InsnNode(NOP)); }
            case "CLASS_BOUND" -> { for (int i = 0; i < 32; i++) { var extra = node("fixture/Other" + i, "java/lang/Object", ACC_PUBLIC | ACC_SUPER); nodes.put(extra.name, extra); } }
            case "NO_VIRTUAL" -> base.methods.get(2).instructions.remove(virtual);
            case "NO_SUPER" -> leaf.methods.get(2).instructions.remove(direct);
            case "ABSTRACT_TARGET" -> leaf.methods.get(1).access |= ACC_ABSTRACT;
            case "PRIVATE_SLOT" -> base.methods.get(1).access = ACC_PRIVATE;
            case "STATIC_SLOT" -> base.methods.get(1).access = ACC_PUBLIC | ACC_STATIC;
            case "INDIRECT_SUPER" -> { direct.owner = LEAF; }
            case "INTERFACE_CALL" -> virtual.itf = true;
            case "STATIC_CALL" -> virtual.setOpcode(INVOKESTATIC);
            default -> throw new AssertionError(defect);
        }
        assertThrows(TransformationException.class, () -> OwnedItemHierarchy.inspect(nodes, API, ITEM), defect);
    }

    private static Map<String, ClassNode> nodes() {
        var base = node(BASE, ITEM, ACC_PUBLIC | ACC_ABSTRACT | ACC_SUPER);
        var leaf = node(LEAF, BASE, ACC_PUBLIC | ACC_FINAL | ACC_SUPER);
        base.methods.add(constructor(ACC_PROTECTED, ITEM)); leaf.methods.add(constructor(ACC_PUBLIC, BASE));
        base.methods.add(new MethodNode(ACC_PROTECTED | ACC_ABSTRACT, "score", "(I)I", null, null));
        var score = new MethodNode(ACC_PROTECTED, "score", "(I)I", null, null);
        score.instructions.add(new VarInsnNode(ILOAD, 1)); score.instructions.add(new InsnNode(IRETURN)); leaf.methods.add(score);
        base.methods.add(call("projected", BASE, "score", INVOKEVIRTUAL));
        leaf.methods.add(call("direct", BASE, "projected", INVOKESPECIAL));
        var result = new TreeMap<String,ClassNode>(); result.put(BASE, base); result.put(LEAF, leaf); return result;
    }
    private static ClassNode node(String name, String parent, int access) {
        var node = new ClassNode(); node.version = V1_8; node.name = name; node.superName = parent; node.access = access; return node;
    }
    private static MethodNode constructor(int access, String parent) {
        var method = new MethodNode(access, "<init>", "()V", null, null);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new MethodInsnNode(INVOKESPECIAL, parent, "<init>", "()V", false));
        method.instructions.add(new InsnNode(RETURN)); return method;
    }
    private static MethodNode call(String name, String owner, String target, int opcode) {
        var method = new MethodNode(ACC_PUBLIC, name, "(I)I", null, null);
        method.instructions.add(new VarInsnNode(ALOAD, 0)); method.instructions.add(new VarInsnNode(ILOAD, 1));
        method.instructions.add(new MethodInsnNode(opcode, owner, target, "(I)I", false));
        method.instructions.add(new InsnNode(IRETURN)); return method;
    }
}
