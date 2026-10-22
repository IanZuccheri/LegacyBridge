package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;
import static dev.legacybridge.converter.conversion.ClientConfigContract.*;

/**
 * Legacy API: the finite native Item attribute endpoint plus Properties.durability(128)
 * and isValidRepairItem(damaged,ingredient) testing only the ingredient's native item identity.
 * Target API: the corresponding three exact native contracts, verified separately against inventories.
 * Transformation: NONE here. A deep, checked admission-only view reuses the immutable attribute
 * grammar; the converter must preserve and independently validate every ORIGINAL body instead.
 * Behavior differences: this supplies capacity and an ingredient predicate, not wear on hit/mining,
 * anvil costs/consumption, break notifications, tooltip or native event/equipment qualification.
 * Known limitations: exactly two classes/seven methods, capacity 128, stack 1, MAINHAND modifiers
 * fixed at 2.0D/-1.0D using native UUID objects, ordinary native iron-ingot ingredient identity.
 * No original classes are loaded, no public profile or implicit composition is authorized.
 */
public final class ItemAttributeDurabilityContract {
    private static final String OLD_ITEM="net/minecraft/item/Item", TARGET_ITEM="net/minecraft/world/item/Item";
    private ItemAttributeDurabilityContract() { }

    /** attributeProof describes a checked admission view, never a serialized replacement program. */
    public record Proof(ItemAttributeContract.Proof attributeProof,int durability) {
        public Proof { Objects.requireNonNull(attributeProof);if(durability!=128)throw new IllegalArgumentException("Finite capacity must be 128"); }
        public Map<String,Object> toTree() {
            var attributes=new TreeMap<>(attributeProof.toTree());
            attributes.put("properties","CHECKED_ADMISSION_VIEW_ONLY; ORIGINAL_HAS_DURABILITY_128");
            return Map.ofEntries(Map.entry("schemaVersion",1),Map.entry("contract","owned-item-attribute-durability-v1"),
                Map.entry("attributeProof",Collections.unmodifiableMap(attributes)),Map.entry("durability",durability),
                Map.entry("maxStackSize",1),Map.entry("properties","FRESH_NATIVE_PROPERTIES_THEN_DURABILITY_128"),
                Map.entry("repairIngredient","minecraft:iron_ingot"),
                Map.entry("repairPredicate","SECOND_ARGUMENT_NATIVE_ITEM_IDENTITY; DAMAGED_ARGUMENT_UNUSED; NO_MUTATION"),
                Map.entry("normalization","CHECKED_DEEP_ADMISSION_VIEW_ONLY; NEVER_TRANSFORMED_OR_EMITTED"),
                Map.entry("wearOnHitOrMining","NOT_IMPLEMENTED_BY_THIS_PROGRAM"),
                Map.entry("anvilConsumptionAndCost","NOT_QUALIFIED"),Map.entry("runtimeValidation","NOT_RUN"),
                Map.entry("classesExecuted",false));
        }
    }

    public static Proof inspect(Map<String,ClassNode> nodes) {
        try { return inspectClosed(nodes); }
        catch(TransformationException refused) {
            if(refused.getMessage().startsWith("ITEM_ATTRIBUTE_DURABILITY_UNSUPPORTED:"))throw refused;
            throw new TransformationException("ITEM_ATTRIBUTE_DURABILITY_UNSUPPORTED: "+refused.getMessage(),refused);
        }
    }
    private static Proof inspectClosed(Map<String,ClassNode> nodes) {
        OperationControl.checkpoint();
        require(nodes!=null&&nodes.size()==2,"Exactly an owned entry and direct native Item subclass");
        for(var pair:nodes.entrySet()) {
            var n=pair.getValue();
            require(n!=null&&n.name!=null&&n.name.equals(pair.getKey())&&!LegacyReferenceScanner.isProtectedDefinition(n.name),"Owned class identity");
            require(n.version==V1_8&&n.access==(ACC_PUBLIC|ACC_FINAL|ACC_SUPER)&&empty(n.interfaces)
                &&n.fields!=null&&n.fields.size()<=3&&n.methods!=null&&n.methods.size()<=4,"Bounded final Java-8 class graph");
            var fields=new HashSet<String>();
            for(var field:n.fields)require(field!=null&&field.name!=null&&!field.name.isBlank()&&field.desc!=null&&fields.add(field.name),
                "Unique named original field roles before copying");
            var methods=new HashSet<String>();
            for(var m:n.methods) {
                OperationControl.checkpoint();
                require(m!=null&&m.name!=null&&m.desc!=null&&methods.add(m.name+m.desc)&&m.instructions!=null&&m.instructions.size()<=128
                    &&m.maxStack>=0&&m.maxStack<=64&&m.maxLocals>=0&&m.maxLocals<=64,"Bounded unique original methods before frame allocation or copying");
                for(var instruction:m.instructions)require(empty(instruction.visibleTypeAnnotations)&&empty(instruction.invisibleTypeAnnotations),
                    "No original instruction annotation contract may disappear in the admission view");
            }
        }
        var entry=one(nodes,n->"java/lang/Object".equals(n.superName),"entry");
        var item=one(nodes,n->OLD_ITEM.equals(n.superName)||TARGET_ITEM.equals(n.superName),"native Item subclass");
        require(entry!=item&&entry.methods.size()==3&&item.methods.size()==4,"Exactly three entry and four item methods");
        boolean target=TARGET_ITEM.equals(item.superName);
        String nativeItem=item.superName,props=nativeItem+"$Properties",stack=target?"net/minecraft/world/item/ItemStack":"net/minecraft/item/ItemStack";
        String items=target?"net/minecraft/world/item/Items":"net/minecraft/item/Items";
        var factories=entry.methods.stream().filter(m->m.desc.equals("()L"+nativeItem+";")).toList();
        require(factories.size()==1&&factories.get(0).access==(ACC_PRIVATE|ACC_STATIC),"One private static native Item-returning factory");
        var factory=factories.get(0);var f=code(factory);
        require(f.size()==9,"Only fresh native properties followed by literal durability and owned constructor");
        same(f.get(0),type(NEW,item.name));same(f.get(1),op(DUP));same(f.get(2),type(NEW,props));same(f.get(3),op(DUP));
        same(f.get(4),m(INVOKESPECIAL,props,"<init>","()V"));
        require(f.get(5) instanceof IntInsnNode capacity&&capacity.getOpcode()==SIPUSH&&capacity.operand==128,"Exact audited SIPUSH 128 capacity");
        same(f.get(6),m(INVOKEVIRTUAL,props,target?"m_41503_":"func_200918_c","(I)L"+props+";"));
        same(f.get(7),m(INVOKESPECIAL,item.name,"<init>","(L"+props+";)V"));same(f.get(8),op(ARETURN));
        var repair=method(item,target?"m_6832_":"func_82789_a","(L"+stack+";L"+stack+";)Z",ACC_PUBLIC);
        removedMethodMetadata(repair);
        var r=code(repair);require(r.size()==8,"Only second-argument native iron-ingot identity predicate");
        same(r.get(0),v(ALOAD,2));same(r.get(1),m(INVOKEVIRTUAL,stack,target?"m_41720_":"func_77973_b","()L"+nativeItem+";"));
        same(r.get(2),f(GETSTATIC,items,target?"f_42416_":"field_151042_j","L"+nativeItem+";"));
        branch(repair,r,3,IF_ACMPNE,6);same(r.get(4),op(ICONST_1));branch(repair,r,5,GOTO,7);same(r.get(6),op(ICONST_0));same(r.get(7),op(IRETURN));

        // The only removed behavior is completely checked above, including metadata that would
        // otherwise disappear. All remaining original metadata/effects survive in the deep view
        // and must pass the independent attribute grammar. This map is never returned or emitted.
        var view=new TreeMap<String,ClassNode>();
        nodes.forEach((name,node)->{var copy=new ClassNode();node.accept(copy);view.put(name,copy);});
        var viewFactory=method(view.get(entry.name),factory.name,factory.desc,ACC_PRIVATE|ACC_STATIC);
        var viewCode=code(viewFactory);viewFactory.instructions.remove(viewCode.get(6));viewFactory.instructions.remove(viewCode.get(5));
        var viewItem=view.get(item.name);viewItem.methods.remove(method(viewItem,repair.name,repair.desc,ACC_PUBLIC));
        var attributeProof=ItemAttributeContract.inspect(view);
        require(attributeProof.entry().equals(entry.name)&&attributeProof.item().equals(item.name)&&attributeProof.factory().equals(factory.name),"Admission roles must match the original program");
        OperationControl.checkpoint();return new Proof(attributeProof,128);
    }

    /** Target resource inventory, not a bootstrap or permission to discard any owned bytecode. */
    public static ContentPlan validate(Map<String,ClassNode> nodes,String modId,String version) {
        var proof=inspect(nodes);var attributes=proof.attributeProof();
        require(attributes.modId().equals(modId),"Metadata identity differs from proven original registration");
        require(version!=null&&version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}"),"Exact finite mod version");
        require(TARGET_ITEM.equals(nodes.get(attributes.item()).superName),"Target-only resource inventory");
        return new ContentPlan(modId,version,List.of(new ContentPlan.ItemDefinition(attributes.registryName(),null,
            new ContentPlan.ItemProperties(1,proof.durability(),false,"COMMON",null))),List.of(),List.of());
    }
    private static void removedMethodMetadata(MethodNode method) {
        require(method.signature==null&&empty(method.tryCatchBlocks)&&empty(method.exceptions)
            &&empty(method.visibleAnnotations)&&empty(method.invisibleAnnotations)&&empty(method.visibleTypeAnnotations)&&empty(method.invisibleTypeAnnotations)
            &&empty(method.visibleLocalVariableAnnotations)&&empty(method.invisibleLocalVariableAnnotations)
            &&method.visibleParameterAnnotations==null&&method.invisibleParameterAnnotations==null
            &&method.visibleAnnotableParameterCount==0&&method.invisibleAnnotableParameterCount==0
            &&method.annotationDefault==null&&empty(method.attrs)&&empty(method.parameters),"No removed generic, annotation, handler or method metadata contract");
        for(var instruction:method.instructions)require(empty(instruction.visibleTypeAnnotations)&&empty(instruction.invisibleTypeAnnotations),"No removed instruction annotation contracts");
    }
    private static void branch(MethodNode method,List<AbstractInsnNode> body,int at,int opcode,int target) {
        require(body.get(at) instanceof JumpInsnNode jump&&jump.getOpcode()==opcode,"Exact ingredient branch opcode");
        var cursor=(AbstractInsnNode)((JumpInsnNode)body.get(at)).label;
        require(cursor!=null&&method.instructions.indexOf(cursor)>=0,"Owned ingredient branch label");
        while(cursor!=null&&cursor.getOpcode()<0)cursor=cursor.getNext();
        require(cursor==body.get(target),"Exact ingredient branch target");
    }
    private static void require(boolean value,String message) {
        if(!value)throw new TransformationException("ITEM_ATTRIBUTE_DURABILITY_UNSUPPORTED: "+message);
    }
}
