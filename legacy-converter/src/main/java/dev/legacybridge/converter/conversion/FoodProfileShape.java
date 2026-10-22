package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Closed native-food envelope, AFTER exact source/target symbol and opcode approval.
 * Reuses the existing registration proof with a private proof-only constructor projection.
 * Neither the projection nor its resource-only ContentPlan may replace original executable bodies.
 * This admits no use/food-property overrides, effects, remainders, helper state or late registry reads.
 */
final class FoodProfileShape {
    private static final String ITEM = "net/minecraft/world/item/Item";
    private static final String PROPS = ITEM + "$Properties";
    private static final String FOOD = "net/minecraft/world/food/FoodProperties";
    private static final String BUILDER = FOOD + "$Builder";

    record Result(ContentPlan resources, int nutrition, float saturationModifier) {
        Map<String, Object> foodTree() {
            return Map.of("schemaVersion", 1, "nutrition", nutrition, "saturationModifier", saturationModifier,
                    "meat", false, "alwaysEat", false, "fast", false, "effectCount", 0,
                    "customOverrides", false, "remainder", "NONE",
                    "behaviorMigration", "TARGET_NATIVE_EAT_AND_ITEM_INTERACT_GAME_EVENTS; no equivalence of old/new world reactions claimed");
        }
    }
    private FoodProfileShape() { }

    static Result validate(Map<String, ClassNode> nodes, String modId, String version) {
        require(nodes != null && nodes.size() == 2, "Exactly entrypoint and native-food Item subclass required");
        ClassNode item = null;
        for (var pair : nodes.entrySet()) {
            OperationControl.checkpoint();
            ClassNode node = pair.getValue();
            require(node != null && pair.getKey().equals(node.name) && node.version == Opcodes.V1_8
                            && node.interfaces != null && node.interfaces.isEmpty()
                            && (node.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT))
                            == (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL), "Only two final public Java 8 classes");
            require(node.methods != null && node.methods.size() <= 2 && node.fields != null && node.fields.size() <= 3,
                    "No extra methods, overrides or helper state");
            for (MethodNode method : node.methods) {
                require(method.instructions != null && method.instructions.size() <= 256,
                        "Bounded closed constructors/initializer only");
                require(method.tryCatchBlocks == null || method.tryCatchBlocks.isEmpty(), "No exception handlers");
                require((method.access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SYNCHRONIZED)) == 0,
                        "No alternative execution contract");
                for (AbstractInsnNode op : method.instructions) require(!(op instanceof JumpInsnNode)
                                && !(op instanceof TableSwitchInsnNode) && !(op instanceof LookupSwitchInsnNode),
                        "No constructor or initializer control-flow alternatives");
            }
            if (ITEM.equals(node.superName)) { require(item == null, "One direct Item only"); item = node; }
        }
        require(item != null && item.fields.isEmpty() && item.methods.size() == 1,
                "FOOD_CUSTOM_BEHAVIOR: Item must contain only its constructor, with no fields or overrides");
        require((item.visibleAnnotations == null || item.visibleAnnotations.isEmpty())
                && (item.invisibleAnnotations == null || item.invisibleAnnotations.isEmpty()), "No Item annotations");
        MethodNode ctor = item.methods.get(0);
        require(ctor.name.equals("<init>") && ctor.desc.equals("()V")
                && (ctor.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_PUBLIC,
                "Public zero-argument constructor only");
        List<AbstractInsnNode> code = code(ctor);
        require(code.size() == 17, "FOOD_CONSTRUCTOR_ENVELOPE: exact stack/food builder prefix, super and return only");
        // Instructions 0..5 and the original Item super call are checked by the reused registration proof.
        require(code.get(6) instanceof TypeInsnNode n && n.getOpcode() == Opcodes.NEW && BUILDER.equals(n.desc),
                "Native FoodProperties.Builder allocation required");
        opcode(code.get(7), Opcodes.DUP);
        call(code.get(8), Opcodes.INVOKESPECIAL, BUILDER, "<init>", "()V");
        int nutrition = integer(code.get(9));
        require(nutrition >= 1 && nutrition <= 20, "FOOD_LITERAL_RANGE: nutrition must be a literal in 1..20");
        nativeCall(code.get(10), BUILDER, "(I)L" + BUILDER + ";");
        float saturation = floating(code.get(11));
        require(Float.isFinite(saturation) && saturation >= 0 && saturation <= 1,
                "FOOD_LITERAL_RANGE: saturation modifier must be finite and in 0..1");
        nativeCall(code.get(12), BUILDER, "(F)L" + BUILDER + ";");
        nativeCall(code.get(13), BUILDER, "()L" + FOOD + ";");
        nativeCall(code.get(14), PROPS, "(L" + FOOD + ";)L" + PROPS + ";");
        call(code.get(15), Opcodes.INVOKESPECIAL, ITEM, "<init>", "(L" + PROPS + ";)V");
        opcode(code.get(16), Opcodes.RETURN);

        ClassNode projected = new ClassNode(); item.accept(projected);
        MethodNode projectedCtor = projected.methods.get(0);
        List<AbstractInsnNode> projectedCode = code(projectedCtor);
        for (int index = 6; index <= 14; index++) projectedCtor.instructions.remove(projectedCode.get(index));
        Map<String, ClassNode> proofNodes = new TreeMap<>(nodes); proofNodes.put(projected.name, projected);
        ContentPlan resources = LogicProfileShape.validate(proofNodes, modId, version);
        return new Result(resources, nutrition, saturation);
    }

    private static List<AbstractInsnNode> code(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode op : method.instructions) if (op.getOpcode() >= 0) result.add(op);
        return result;
    }
    private static void nativeCall(AbstractInsnNode op, String owner, String descriptor) {
        // The preceding recipe compiler proved the actual official->production member name.
        require(op instanceof MethodInsnNode m && m.getOpcode() == Opcodes.INVOKEVIRTUAL && !m.itf
                && owner.equals(m.owner) && descriptor.equals(m.desc), "Unreviewed native property call");
    }
    private static void call(AbstractInsnNode op, int opcode, String owner, String name, String descriptor) {
        require(op instanceof MethodInsnNode m && m.getOpcode() == opcode && !m.itf
                && owner.equals(m.owner) && name.equals(m.name) && descriptor.equals(m.desc), "Unexpected constructor call");
    }
    private static int integer(AbstractInsnNode op) {
        if (op.getOpcode() >= Opcodes.ICONST_M1 && op.getOpcode() <= Opcodes.ICONST_5) return op.getOpcode() - Opcodes.ICONST_0;
        if (op instanceof IntInsnNode n && (n.getOpcode() == Opcodes.BIPUSH || n.getOpcode() == Opcodes.SIPUSH)) return n.operand;
        if (op instanceof LdcInsnNode n && n.cst instanceof Integer i) return i;
        throw failure("Expected literal nutrition integer");
    }
    private static float floating(AbstractInsnNode op) {
        if (op.getOpcode() >= Opcodes.FCONST_0 && op.getOpcode() <= Opcodes.FCONST_2) return op.getOpcode() - Opcodes.FCONST_0;
        if (op instanceof LdcInsnNode n && n.cst instanceof Float f) return f;
        throw failure("Expected literal saturation float");
    }
    private static void opcode(AbstractInsnNode op, int opcode) { require(op.getOpcode() == opcode, "Unexpected constructor effect"); }
    private static void require(boolean condition, String reason) { if (!condition) throw failure(reason); }
    private static TransformationException failure(String reason) { return new TransformationException("FOOD_PROFILE_SHAPE: " + reason); }
}
