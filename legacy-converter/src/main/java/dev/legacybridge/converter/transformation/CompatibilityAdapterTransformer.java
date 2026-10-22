package dev.legacybridge.converter.transformation;

import dev.legacybridge.converter.conversion.TransformationBindings;
import dev.legacybridge.converter.mapping.MappingConfidence;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import java.util.Arrays;

/** Inserts a real cataloged adapter call into the proven, lowered bootstrap. No unresolved-call stubs. */
final class CompatibilityAdapterTransformer {
    void transform(ClassNode target, String modId, String planResource, String sourceLocation, TransformationBindings bindings, TransformationLog log) {
        var adapter = bindings.require(AdapterCatalog.BOOTSTRAP);
        if (!adapter.descriptor().equals("(Ljava/lang/String;Ljava/lang/ClassLoader;Ljava/lang/String;)V"))
            throw new TransformationException("Bootstrap adapter ABI changed without a verified instruction recipe");
        if (target.methods.size() != 1 || !target.fields.isEmpty()) throw new TransformationException("Adapter insertion requires the finite bootstrap");
        MethodNode method = target.methods.get(0);
        var executable = Arrays.stream(method.instructions.toArray()).filter(i -> i.getOpcode() >= 0).toList();
        if (!method.name.equals("<init>") || !method.desc.equals("()V") || executable.size() != 3
                || !(executable.get(0) instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD || load.var != 0
                || !(executable.get(1) instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
                || !call.owner.equals("java/lang/Object") || !call.name.equals("<init>") || !call.desc.equals("()V")
                || executable.get(2).getOpcode() != Opcodes.RETURN)
            throw new TransformationException("Unexpected code before adapter insertion; no speculative patch applied");
        var instructions = new InsnList();
        instructions.add(new LdcInsnNode(modId));
        instructions.add(new LdcInsnNode(Type.getObjectType(target.name)));
        instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false));
        instructions.add(new LdcInsnNode(planResource));
        instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, adapter.owner(), adapter.method(), adapter.descriptor(), false));
        method.instructions.insertBefore(executable.get(2), instructions);
        log.add(TransformationLog.Kind.ADAPTER_CALL, sourceLocation + "#<init>()V",
                "<no target registration call>", adapter.owner() + "." + adapter.method() + adapter.descriptor(),
                "simple-content-v1:insert-bootstrap-call", MappingConfidence.VERIFIED,
                "Exact public static adapter entrypoint; runtime validates plan coverage and bindings before native registration.", adapter.id());
    }
}
