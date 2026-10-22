package dev.legacybridge.converter.transformation.linkage;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.MappingResolver;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Finite bound-receiver declaration proof. Actual this provenance is separately checked by dataflow. */
public final class OwnedReceiverHandles {
    private OwnedReceiverHandles() { }
    public record Proof(OwnedItemHierarchy.Member factory, int instruction,
                        OwnedItemHierarchy.Member implementation, String captureDescriptor) {
        public Proof remap(MappingResolver resolver) {
            return new Proof(factory.remap(resolver), instruction, implementation.remap(resolver),
                    "L" + resolver.mapClassName(implementation.owner()) + ";");
        }
        public Map<String,Object> toTree() {
            return Map.of("schemaVersion", 1, "contract", "owned-receiver-handles-v1",
                    "factory", factory.toTree(), "instruction", instruction, "implementation", implementation.toTree(),
                    "captureDescriptor", captureDescriptor, "handleKind", Opcodes.H_INVOKEVIRTUAL,
                    "receiverRequirement", "PROVEN_NON_NULL_THIS", "classesExecuted", false, "publicationAllowed", false);
        }
        boolean permits(String owner, InvokeDynamicInsnNode dynamic) {
            return owner.equals(factory.owner()) && dynamic.bsmArgs.length == 3
                    && dynamic.bsmArgs[1] instanceof Handle h && h.getTag() == Opcodes.H_INVOKEVIRTUAL
                    && !h.isInterface() && h.getOwner().equals(implementation.owner())
                    && h.getName().equals(implementation.name()) && h.getDesc().equals(implementation.descriptor());
        }
    }

    /** Recomputed from source, target and reopened declarations; never accepts a stored proof as authority. */
    public static Proof inspect(Map<String,ClassNode> nodes) {
        require(nodes != null && !nodes.isEmpty() && nodes.size() <= 32, "CLASS_BOUND");
        int methods = 0, instructions = 0;
        Proof proof = null;
        for (var node : new TreeMap<>(nodes).values()) {
            OperationControl.checkpoint();
            require(node.methods.size() <= 128 && (methods += node.methods.size()) <= 256, "METHOD_BOUND");
            for (var method : node.methods) {
                require(method.instructions.size() <= 4096 && (instructions += method.instructions.size()) <= 32768, "CODE_BOUND");
                int index = 0;
                for (var instruction : method.instructions) {
                    OperationControl.checkpoint();
                    if (instruction.getOpcode() < 0) continue;
                    if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                        for (Object argument : dynamic.bsmArgs) if (argument instanceof Handle handle
                                && handle.getTag() != Opcodes.H_INVOKESTATIC && handle.getTag() != Opcodes.H_NEWINVOKESPECIAL) {
                            require(proof == null, "ONE_RECEIVER_SITE");
                            require(handle.getTag() == Opcodes.H_INVOKEVIRTUAL && !handle.isInterface()
                                    && handle.getOwner().equals(node.name), "OWNED_VIRTUAL_THIS_ONLY");
                            require(node.access == (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER)
                                    && "java/lang/Object".equals(node.superName) && node.interfaces.isEmpty(), "FINAL_OBJECT_HELPER_ONLY");
                            require(nodes.values().stream().noneMatch(n -> node.name.equals(n.superName)), "EXTRA_DESCENDANT");
                            require(node.methods.stream().noneMatch(m -> m.name.equals("<clinit>")), "STATIC_INITIALIZATION_UNSUPPORTED");
                            var constructors = node.methods.stream().filter(m -> m.name.equals("<init>")).toList();
                            require(constructors.size() == 1 && constructors.get(0).access == Opcodes.ACC_PUBLIC
                                    && constructors.get(0).desc.equals("()V"), "HELPER_CONSTRUCTOR");
                            require(method.access == Opcodes.ACC_PUBLIC && !method.name.startsWith("<")
                                    && method.desc.equals("()Ljava/util/function/IntUnaryOperator;"), "INSTANCE_FACTORY_ONLY");
                            require(dynamic.name.equals("applyAsInt") && dynamic.desc.equals("(L"+node.name+";)Ljava/util/function/IntUnaryOperator;")
                                    && dynamic.bsmArgs.length == 3 && dynamic.bsmArgs[1] == handle
                                    && Type.getMethodType("(I)I").equals(dynamic.bsmArgs[0])
                                    && Type.getMethodType("(I)I").equals(dynamic.bsmArgs[2]), "EXACT_INT_UNARY_CAPTURE");
                            var targets = node.methods.stream().filter(m -> m.name.equals(handle.getName()) && m.desc.equals(handle.getDesc())).toList();
                            require(targets.size() == 1 && targets.get(0).access == Opcodes.ACC_PUBLIC
                                    && !handle.getName().startsWith("<") && handle.getDesc().equals("(I)I"), "DECLARED_PUBLIC_INT_IMPLEMENTATION");
                            proof = new Proof(member(node.name, method), index,
                                    member(node.name, targets.get(0)), "L"+node.name+";");
                        }
                    }
                    index++;
                }
            }
        }
        require(proof != null, "RECEIVER_SITE_REQUIRED");
        return proof;
    }
    private static OwnedItemHierarchy.Member member(String owner, MethodNode method) { return new OwnedItemHierarchy.Member(owner,method.name,method.desc); }
    private static void require(boolean condition, String reason) { if (!condition) throw new TransformationException("OWNED_RECEIVER: " + reason); }
}
