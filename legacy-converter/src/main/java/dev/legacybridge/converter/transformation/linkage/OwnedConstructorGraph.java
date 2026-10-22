package dev.legacybridge.converter.transformation.linkage;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Finite this(...) proof, not an interpreter, transformation, or installation authority. */
public final class OwnedConstructorGraph {
    private OwnedConstructorGraph() { }
    public record Step(String descriptor, List<Integer> arguments, Optional<String> delegatesTo) {
        public Step { arguments = List.copyOf(arguments); }
        public Map<String,Object> toTree() { return Map.of("descriptor", descriptor, "arguments", arguments,
                "delegatesTo", delegatesTo.orElse("DIRECT_SUPER")); }
    }
    public record Proof(String owner, List<Step> chain) {
        public Proof { chain = List.copyOf(chain); }
        public Step terminal() { return chain.get(chain.size() - 1); }
        public Map<String,Object> toTree() { return Map.of("schemaVersion", 1, "contract", "owned-int-constructor-chain-v1",
                "owner", owner, "chain", chain.stream().map(Step::toTree).toList(), "bodiesReplaced", false,
                "classesExecuted", false, "publicationAllowed", false); }
    }
    public static Proof inspect(ClassNode owner) {
        require(owner != null && owner.methods != null && owner.methods.size() <= 128, "CLASS_BOUND");
        Map<String,MethodNode> constructors = new TreeMap<>();
        Map<String,MethodInsnNode> edges = new TreeMap<>();
        for (MethodNode method : owner.methods) {
            OperationControl.checkpoint();
            require(method.instructions != null && method.instructions.size() <= 4096, "METHOD_BOUND");
            if (!method.name.equals("<init>")) continue;
            require(method.desc.matches("\\(I{0,4}\\)V"), "INT_ARGUMENTS_ONLY");
            int visibility = method.desc.equals("()V") ? Opcodes.ACC_PUBLIC : Opcodes.ACC_PRIVATE;
            require(method.access == visibility && constructors.putIfAbsent(method.desc, method) == null,
                    "EXACT_CONSTRUCTOR_ACCESS_OR_DUPLICATE");
            var code = code(method);
            require(!code.isEmpty() && code.get(0) instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD && v.var == 0,
                    "THIS_MUST_BE_FIRST_RECEIVER");
            for (int i = 1; i < code.size(); i++) if (code.get(i) instanceof MethodInsnNode call && call.owner.equals(owner.name) && call.name.equals("<init>")) {
                require(!edges.containsKey(method.desc) && call.getOpcode() == Opcodes.INVOKESPECIAL && !call.itf
                        && call.desc.matches("\\(I{0,4}\\)V") && i == call.desc.length() - 2, "DELEGATION_PREFIX_OR_OPCODE");
                for (int a = 1; a < i; a++) argument(code.get(a), Collections.nCopies(method.desc.length() - 3, 0));
                edges.put(method.desc, call);
            }
        }
        require(constructors.size() >= 2 && constructors.size() <= 5 && constructors.containsKey("()V"), "EXPECTED_CHAIN_2_TO_5");
        // No hidden second entry, reflective handle, or recursive re-construction of this item in owned bodies.
        for (MethodNode method : owner.methods) for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.equals(owner.name))
                require(false, "RECONSTRUCTION_UNSUPPORTED");
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner.name) && call.name.equals("<init>"))
                require(edges.get(method.desc) == call && method.name.equals("<init>"), "UNREVIEWED_CONSTRUCTOR_SITE");
        }
        List<Step> chain = new ArrayList<>(); Set<String> seen = new HashSet<>();
        String descriptor = "()V"; List<Integer> values = List.of();
        while (true) {
            OperationControl.checkpoint();
            require(constructors.containsKey(descriptor), "MISSING_CONSTRUCTOR");
            require(seen.add(descriptor), "CONSTRUCTOR_CYCLE");
            MethodInsnNode edge = edges.get(descriptor);
            chain.add(new Step(descriptor, values, Optional.ofNullable(edge).map(e -> e.desc)));
            if (edge == null) break;
            List<Integer> next = new ArrayList<>(); var instructions = code(constructors.get(descriptor));
            for (int a = 1; a < edge.desc.length() - 2; a++) next.add(argument(instructions.get(a), values));
            descriptor = edge.desc; values = List.copyOf(next);
        }
        require(seen.size() == constructors.size(), "UNREACHABLE_CONSTRUCTOR");
        return new Proof(owner.name, chain);
    }
    public static List<AbstractInsnNode> code(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (var instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }
    public static int argument(AbstractInsnNode instruction, List<Integer> values) {
        if (instruction instanceof VarInsnNode v && v.getOpcode() == Opcodes.ILOAD && v.var > 0 && v.var <= values.size())
            return values.get(v.var - 1);
        int opcode = instruction.getOpcode();
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) return opcode - Opcodes.ICONST_0;
        if (instruction instanceof IntInsnNode v && (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH)) return v.operand;
        if (instruction instanceof LdcInsnNode v && v.cst instanceof Integer number) return number;
        throw new TransformationException("OWNED_CONSTRUCTORS: ARGUMENT_NOT_LITERAL_OR_PARAMETER");
    }
    private static void require(boolean condition, String reason) {
        if (!condition) throw new TransformationException("OWNED_CONSTRUCTORS: " + reason);
    }
}
