package dev.legacybridge.converter.transformation.linkage;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.MappingResolver;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Finite owned interface/default selection proof. No class loading or general interface resolution. */
public final class OwnedInterfaceDefaults {
    private OwnedInterfaceDefaults() { }
    public record Site(OwnedItemHierarchy.Member caller, int instruction, int opcode,
                       OwnedItemHierarchy.Member symbolic, OwnedItemHierarchy.Member selected) {
        Site remap(MappingResolver resolver) { return new Site(caller.remap(resolver), instruction, opcode,
                symbolic.remap(resolver), selected.remap(resolver)); }
        Map<String,Object> toTree() { return Map.of("caller", caller.toTree(), "instruction", instruction, "opcode", opcode,
                "symbolic", symbolic.toTree(), "selectedForHelper", selected.toTree()); }
    }
    public record Proof(String contract, String helper, OwnedItemHierarchy.Member defaultMethod,
                        OwnedItemHierarchy.Member abstractMethod, List<Site> sites) {
        public Proof { sites = List.copyOf(sites); }
        public Proof remap(MappingResolver resolver) { return new Proof(resolver.mapClassName(contract), resolver.mapClassName(helper),
                defaultMethod.remap(resolver), abstractMethod.remap(resolver), sites.stream().map(s -> s.remap(resolver)).toList()); }
        public Map<String,Object> toTree() { return Map.of("schemaVersion", 1, "contract", "owned-interface-defaults-v1",
                "interface", contract, "helper", helper, "defaultMethod", defaultMethod.toTree(), "abstractMethod", abstractMethod.toTree(),
                "sites", sites.stream().map(Site::toTree).toList(), "receiverAssumption", "NON_NULL_OWNED_HELPER",
                "classesExecuted", false, "publicationAllowed", false); }
        boolean permitsDefault(String owner, String name, String descriptor) {
            return defaultMethod.equals(new OwnedItemHierarchy.Member(owner, name, descriptor));
        }
        boolean permitsSpecial(String caller, MethodInsnNode call) {
            return caller.equals(helper) && call.itf && permitsDefault(call.owner, call.name, call.desc);
        }
    }

    /** Re-derived by every private verifier invocation; this object alone never grants linkage/installation authority. */
    public static Proof inspect(Map<String,ClassNode> nodes) {
        require(nodes != null && !nodes.isEmpty() && nodes.size() <= 32, "CLASS_BOUND");
        int instructions = 0, methods = 0;
        for (var node : nodes.values()) {
            OperationControl.checkpoint();
            require(node.methods.size() <= 128 && (methods += node.methods.size()) <= 256, "METHOD_BOUND");
            for (var method : node.methods) require(method.instructions.size() <= 4096
                    && (instructions += method.instructions.size()) <= 32768, "CODE_BOUND");
        }
        var interfaces = nodes.values().stream().filter(n -> (n.access & Opcodes.ACC_INTERFACE) != 0).toList();
        require(interfaces.size() == 1, "ONE_OWNED_INTERFACE"); var contract = interfaces.get(0);
        require(contract.access == (Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT)
                && contract.superName.equals("java/lang/Object") && contract.interfaces.isEmpty()
                && contract.fields.isEmpty() && contract.methods.size() == 2, "FLAT_STATELESS_INTERFACE_ONLY");
        var defaults = contract.methods.stream().filter(m -> m.access == Opcodes.ACC_PUBLIC && m.desc.equals("(I)I") && !m.name.startsWith("<")).toList();
        var abstracts = contract.methods.stream().filter(m -> m.access == (Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT)
                && m.desc.equals("()I") && !m.name.startsWith("<")).toList();
        require(defaults.size() == 1 && abstracts.size() == 1, "ONE_INT_DEFAULT_AND_HOOK");
        var defaultMethod = member(contract.name, defaults.get(0)); var abstractMethod = member(contract.name, abstracts.get(0));
        var implementations = nodes.values().stream().filter(n -> n.interfaces.contains(contract.name)).toList();
        require(implementations.size() == 1, "ONE_DIRECT_IMPLEMENTATION"); var helper = implementations.get(0);
        require(helper.access == (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER)
                && helper.superName.equals("java/lang/Object") && helper.interfaces.equals(List.of(contract.name)), "FINAL_OBJECT_HELPER_ONLY");
        require(nodes.values().stream().noneMatch(n -> helper.name.equals(n.superName)), "EXTRA_DESCENDANT");
        require(helper.methods.stream().noneMatch(m -> m.name.equals("<clinit>")), "HELPER_STATIC_INITIALIZATION_UNSUPPORTED");
        for (var slot : List.of(defaultMethod, abstractMethod)) {
            var matches = helper.methods.stream().filter(m -> m.name.equals(slot.name()) && m.desc.equals(slot.descriptor())).toList();
            require(matches.size() == 1 && matches.get(0).access == Opcodes.ACC_PUBLIC, "EXACT_PUBLIC_OVERRIDE");
        }
        var constructors = helper.methods.stream().filter(m -> m.name.equals("<init>")).toList();
        require(constructors.size() == 1 && constructors.get(0).access == Opcodes.ACC_PUBLIC && constructors.get(0).desc.equals("()V"), "HELPER_CONSTRUCTOR");
        var sites = new ArrayList<Site>();
        for (var node : new TreeMap<>(nodes).values()) for (var method : node.methods) {
            int index = 0;
            for (var instruction : method.instructions) {
                OperationControl.checkpoint(); if (instruction.getOpcode() < 0) continue;
                if (instruction instanceof MethodInsnNode call && call.owner.equals(contract.name)) {
                    var slot = new OwnedItemHierarchy.Member(call.owner, call.name, call.desc);
                    require(slot.equals(defaultMethod) || slot.equals(abstractMethod), "UNKNOWN_INTERFACE_SLOT");
                    require(call.itf && (call.getOpcode() == Opcodes.INVOKEINTERFACE || call.getOpcode() == Opcodes.INVOKESPECIAL), "INTERFACE_OPCODE");
                    boolean direct = call.getOpcode() == Opcodes.INVOKESPECIAL;
                    require(!direct || node.name.equals(helper.name) && slot.equals(defaultMethod), "DIRECT_INTERFACE_SUPER_ONLY");
                    sites.add(new Site(member(node.name, method), index, call.getOpcode(), slot,
                            direct ? slot : new OwnedItemHierarchy.Member(helper.name, call.name, call.desc)));
                }
                if (instruction instanceof InvokeDynamicInsnNode dynamic)
                    for (Object argument : dynamic.bsmArgs) if (argument instanceof Handle handle)
                        require(!handle.getOwner().equals(contract.name) && !handle.getOwner().equals(helper.name), "INTERFACE_HELPER_HANDLE_UNSUPPORTED");
                index++;
            }
        }
        require(sites.stream().anyMatch(s -> s.opcode == Opcodes.INVOKESPECIAL), "SUPER_WITNESS_REQUIRED");
        require(sites.stream().anyMatch(s -> s.opcode == Opcodes.INVOKEINTERFACE && s.symbolic.equals(defaultMethod)), "OVERRIDE_WITNESS_REQUIRED");
        require(sites.stream().anyMatch(s -> s.caller.equals(defaultMethod) && s.symbolic.equals(abstractMethod)
                && s.opcode == Opcodes.INVOKEINTERFACE), "DEFAULT_HOOK_WITNESS_REQUIRED");
        return new Proof(contract.name, helper.name, defaultMethod, abstractMethod, sites);
    }
    private static OwnedItemHierarchy.Member member(String owner, MethodNode method) { return new OwnedItemHierarchy.Member(owner, method.name, method.desc); }
    private static void require(boolean condition, String reason) { if (!condition) throw new TransformationException("OWNED_INTERFACE: " + reason); }
}
