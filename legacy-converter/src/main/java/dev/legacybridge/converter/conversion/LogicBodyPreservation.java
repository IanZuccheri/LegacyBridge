package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Additional invariant, not semantic equivalence proof: a symbol remap cannot replace executable bodies. */
final class LogicBodyPreservation {
    private LogicBodyPreservation() { }

    static void verify(ClassNode before, ClassNode after) {
        require(before.name.equals(after.name) && before.version == after.version && before.access == after.access,
                "Owned class identity/version/access changed");
        require(Objects.equals(before.sourceFile, after.sourceFile) && Objects.equals(before.sourceDebug, after.sourceDebug), "Debug source lost");
        require(before.fields.size() == after.fields.size() && before.methods.size() == after.methods.size(), "Owned members lost or generated");
        for (int i = 0; i < before.fields.size(); i++) {
            var a = before.fields.get(i); var b = after.fields.get(i);
            require(a.access == b.access && Objects.equals(a.value, b.value), "Field state/constant changed");
        }
        for (int i = 0; i < before.methods.size(); i++) {
            var a = before.methods.get(i); var b = after.methods.get(i);
            require(a.access == b.access && shape(a).equals(shape(b)), "Executable body/control flow changed: " + before.name + "." + a.name + a.desc);
        }
    }

    private static List<Object> shape(MethodNode method) {
        List<Object> shape = new ArrayList<>();
        Map<LabelNode, Integer> labels = new IdentityHashMap<>();
        int next = 0;
        for (var instruction : method.instructions) if (instruction instanceof LabelNode label) labels.put(label, next++);
        for (var instruction : method.instructions) {
            List<Object> entry = new ArrayList<>(); entry.add(instruction.getOpcode());
            if (instruction instanceof LabelNode label) entry.add(labels.get(label));
            else if (instruction instanceof FrameNode) continue; // ClassWriter recomputes stack map types separately.
            else if (instruction instanceof LineNumberNode line) { entry.add(line.line); entry.add(labels.get(line.start)); }
            else if (instruction instanceof VarInsnNode variable) entry.add(variable.var);
            else if (instruction instanceof IntInsnNode integer) entry.add(integer.operand);
            else if (instruction instanceof IincInsnNode increment) { entry.add(increment.var); entry.add(increment.incr); }
            else if (instruction instanceof JumpInsnNode jump) entry.add(labels.get(jump.label));
            else if (instruction instanceof TableSwitchInsnNode table) {
                entry.add(table.min); entry.add(table.max); entry.add(labels.get(table.dflt)); entry.add(table.labels.stream().map(labels::get).toList());
            } else if (instruction instanceof LookupSwitchInsnNode lookup) {
                entry.add(lookup.keys); entry.add(labels.get(lookup.dflt)); entry.add(lookup.labels.stream().map(labels::get).toList());
            } else if (instruction instanceof LdcInsnNode constant) entry.add(constantShape(constant.cst));
            else if (instruction instanceof MethodInsnNode call) entry.add(call.itf);
            else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                entry.add(dynamic.name); entry.add(constantShape(dynamic.bsm));
                entry.add(Arrays.stream(dynamic.bsmArgs).map(LogicBodyPreservation::constantShape).toList());
            } else if (instruction instanceof MultiANewArrayInsnNode array) entry.add(array.dims);
            shape.add(entry);
        }
        for (var handler : method.tryCatchBlocks) shape.add(Arrays.asList("handler", labels.get(handler.start), labels.get(handler.end), labels.get(handler.handler), handler.type == null));
        if (method.localVariables != null) for (var local : method.localVariables)
            shape.add(Arrays.asList("local", local.name, local.index, labels.get(local.start), labels.get(local.end)));
        return shape;
    }
    private static Object constantShape(Object value) {
        if (value instanceof Type type) return List.of("mapped-type", type.getSort());
        if (value instanceof Handle handle) return List.of("mapped-handle", handle.getTag(), handle.isInterface());
        return value; // All literals, including strings, must stay byte-for-byte equivalent.
    }
    private static void require(boolean condition, String message) { if (!condition) throw new TransformationException(message); }
}
