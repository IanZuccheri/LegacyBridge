package dev.legacybridge.converter.transformation.linkage;

import dev.legacybridge.converter.mapping.LegacySymbol;
import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.MappingResolver;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Finite two-owned-level proof. All receivers are conditional on the registered leaf;
 * this is not execution, general JVM method selection, or installation authority. */
public final class OwnedItemHierarchy {
    private OwnedItemHierarchy() { }
    public record Member(String owner, String name, String descriptor) {
        Member remap(MappingResolver resolver) {
            var symbol = name.equals("<init>") ? LegacySymbol.constructor(owner, descriptor) : LegacySymbol.method(owner, name, descriptor);
            var target = resolver.requireResolved(symbol).target();
            return new Member(target.owner(), target.name(), target.descriptor());
        }
        Map<String,Object> toTree() { return Map.of("owner", owner, "name", name, "descriptor", descriptor); }
    }
    public record Site(Member caller, int instruction, int opcode, Member symbolic, Member resolved, Member selected) {
        Site remap(MappingResolver resolver) { return new Site(caller.remap(resolver), instruction, opcode,
                symbolic.remap(resolver), resolved.remap(resolver), selected.remap(resolver)); }
        Map<String,Object> toTree() { return Map.of("caller", caller.toTree(), "instruction", instruction,
                "opcode", opcode, "symbolic", symbolic.toTree(), "resolved", resolved.toTree(),
                "selectedForRegisteredLeaf", selected.toTree()); }
    }
    public record Proof(String base, String leaf, String nativeItem, List<Site> sites) {
        public Proof { sites = List.copyOf(sites); }
        public Proof remap(MappingResolver resolver) { return new Proof(resolver.mapClassName(base), resolver.mapClassName(leaf),
                resolver.mapClassName(nativeItem), sites.stream().map(s -> s.remap(resolver)).toList()); }
        public Map<String,Object> toTree() { return Map.of("schemaVersion", 1, "contract", "owned-item-hierarchy-v1",
                "base", base, "leaf", leaf, "nativeItem", nativeItem,
                "sites", sites.stream().map(Site::toTree).toList(), "receiverAssumption", "NON_NULL_REGISTERED_LEAF",
                "classesExecuted", false, "publicationAllowed", false); }
    }

    /** Caller also runs full offline linkage/access, exact external mappings and native property admission. */
    public static Proof inspect(Map<String,ClassNode> nodes, ApiIndex external, String nativeItem) {
        require(Set.of("net/minecraft/item/Item", "net/minecraft/world/item/Item").contains(nativeItem), "NATIVE_BASE");
        require(nodes != null && !nodes.isEmpty() && nodes.size() <= 32, "CLASS_BOUND");
        int total = 0;
        for (var node : nodes.values()) {
            require(node.methods.size() <= 128, "METHOD_BOUND");
            for (var method : node.methods) { total += method.instructions.size(); require(method.instructions.size() <= 4096 && total <= 32768, "CODE_BOUND"); }
        }
        var roots = nodes.values().stream().filter(n -> nativeItem.equals(n.superName)).toList();
        require(roots.size() == 1, "ONE_NATIVE_ROOT"); var base = roots.get(0);
        require(base.access == (Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER) && base.interfaces.isEmpty(), "PUBLIC_ABSTRACT_BASE_ONLY");
        var leaves = nodes.values().stream().filter(n -> base.name.equals(n.superName)).toList();
        require(leaves.size() == 1, "ONE_OWNED_LEAF"); var leaf = leaves.get(0);
        require(leaf.access == (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER) && leaf.interfaces.isEmpty(), "PUBLIC_FINAL_LEAF_ONLY");
        require(nodes.values().stream().noneMatch(n -> leaf.name.equals(n.superName)), "EXTRA_DESCENDANT");
        for (var node : List.of(base, leaf)) {
            require(node.methods.stream().noneMatch(m -> m.name.equals("<clinit>")), "ITEM_STATIC_INITIALIZATION_UNSUPPORTED");
            var constructors = node.methods.stream().filter(m -> m.name.equals("<init>")).toList();
            require(constructors.size() == 1 && constructors.get(0).desc.equals("()V")
                    && constructors.get(0).access == (node == base ? Opcodes.ACC_PROTECTED : Opcodes.ACC_PUBLIC), "EXACT_CONSTRUCTOR");
        }
        var ctor = leaf.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        var code = OwnedConstructorGraph.code(ctor);
        require(code.size() >= 3 && code.get(0) instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD && v.var == 0
                && code.get(1) instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL && !call.itf
                && call.owner.equals(base.name) && call.name.equals("<init>") && call.desc.equals("()V"), "DIRECT_OWNED_SUPER_PREFIX");
        var hierarchy = new LinkageHierarchy(nodes, external); hierarchy.graph(leaf.name);
        List<Site> sites = new ArrayList<>();
        for (var node : List.of(base, leaf)) for (var method : node.methods) {
            int index = 0;
            for (var instruction : method.instructions) {
                OperationControl.checkpoint(); if (instruction.getOpcode() < 0) continue;
                if (instruction instanceof MethodInsnNode call && !call.name.equals("<init>")
                        && Set.of(base.name, leaf.name, nativeItem).contains(call.owner)) {
                    require(!call.itf && (call.getOpcode() == Opcodes.INVOKEVIRTUAL || call.getOpcode() == Opcodes.INVOKESPECIAL), "VIRTUAL_OR_DIRECT_SUPER_ONLY");
                    var resolved = hierarchy.method(call.owner, call.name, call.desc);
                    require((resolved.member().access() & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0
                            && (resolved.member().access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0, "PUBLIC_PROTECTED_SLOT_ONLY");
                    LinkageHierarchy.Member selected;
                    if (call.getOpcode() == Opcodes.INVOKESPECIAL) {
                        require(call.owner.equals(node.superName), "EXACT_DIRECT_SUPER_CALL"); selected = resolved;
                    } else selected = hierarchy.method(leaf.name, call.name, call.desc);
                    require((selected.member().access() & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0, "CONCRETE_SELECTION_REQUIRED");
                    sites.add(new Site(new Member(node.name, method.name, method.desc), index, call.getOpcode(),
                            new Member(call.owner, call.name, call.desc), member(resolved), member(selected)));
                }
                index++;
            }
        }
        require(sites.stream().anyMatch(s -> s.opcode == Opcodes.INVOKEVIRTUAL && s.resolved.owner.equals(base.name) && s.selected.owner.equals(leaf.name)), "OWNED_DISPATCH_WITNESS_REQUIRED");
        require(sites.stream().anyMatch(s -> s.opcode == Opcodes.INVOKESPECIAL && s.symbolic.owner.equals(base.name)), "OWNED_SUPER_WITNESS_REQUIRED");
        return new Proof(base.name, leaf.name, nativeItem, sites);
    }
    private static Member member(LinkageHierarchy.Member value) { return new Member(value.owner().name(), value.member().name(), value.member().descriptor()); }
    private static void require(boolean condition, String reason) { if (!condition) throw new TransformationException("OWNED_HIERARCHY: " + reason); }
}
