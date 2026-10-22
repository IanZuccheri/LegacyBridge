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
 * Closed structural proof, not a remapping database or an interpreter.
 * Exact production spellings are pinned-source guards; VerifiedLogicMappings independently
 * composes official/SRG inputs and checks declarations/opcodes before approving any rule.
 * All projections are admission-only and never emitted. See docs/DURABLE_ITEM_CONTRACT.md.
 */
public final class DurableItemContract {
    private DurableItemContract() { }
    public record Proof(String entry, String item, String modId, String registryName, int durability) {
        public Map<String,Object> toTree() {
            return Map.of("schemaVersion",1,"entry",entry,"item",item,"modId",modId,"registryName",registryName,
                "durability",durability,"maxStackSize",1,"repairIngredient","minecraft:iron_ingot",
                "predicate","SECOND_ARGUMENT_ITEM_IDENTITY; no stack mutation",
                "runtimeValidation","NOT_RUN");
        }
    }
    public static Proof inspect(Map<String,ClassNode> nodes) {
        require(nodes!=null && nodes.size()==2,"Exactly entry and final native Item");
        for(var p:nodes.entrySet()) {
            OperationControl.checkpoint();
            var n=p.getValue();
            require(n!=null && p.getKey().equals(n.name) && !LegacyReferenceScanner.isProtectedDefinition(n.name)
                && n.version==V1_8 && n.access==(ACC_PUBLIC|ACC_FINAL|ACC_SUPER) && n.interfaces.isEmpty()
                && n.methods.size()==2 && n.fields.size()<=3,"Closed Java-8 declaration set");
            for(var m:n.methods)require(m.instructions.size()<=128 && empty(m.tryCatchBlocks)
                && empty(m.exceptions) && (m.access & (ACC_NATIVE|ACC_ABSTRACT|ACC_SYNCHRONIZED))==0,
                "No exceptional or extra execution effects");
        }
        var item=one(nodes,n->Set.of("net/minecraft/item/Item","net/minecraft/world/item/Item").contains(n.superName),"native Item");
        var entry=one(nodes,n->"java/lang/Object".equals(n.superName),"entry");
        boolean old=item.superName.equals("net/minecraft/item/Item");
        String base=item.superName, props=base+"$Properties", stack=old?"net/minecraft/item/ItemStack":"net/minecraft/world/item/ItemStack";
        String items=old?"net/minecraft/item/Items":"net/minecraft/world/item/Items";
        require(item.fields.isEmpty() && annotations(item).isEmpty() && entry.fields.size()==3,"No owned item state or annotation");
        var identity=fieldOf(entry,"Ljava/lang/String;");
        require(identity.value instanceof String id && id.matches("[a-z][a-z0-9_]{1,63}"),"Literal mod identity");
        var ann=annotations(entry);
        require(ann.size()==1 && ann.get(0).desc.equals("Lnet/minecraftforge/fml/common/Mod;")
            && List.of("value",identity.value).equals(ann.get(0).values),"Exact mod identity annotation");
        var ctor=method(item,"<init>","()V",ACC_PUBLIC);
        var c=code(ctor); require(c.size()==8,"Only native durability construction");
        int damage=literal(c.get(4)); require(damage>=1 && damage<=32767,"Durability literal in 1..32767");
        same(c.get(0),v(ALOAD,0));same(c.get(1),type(NEW,props));same(c.get(2),op(DUP));
        same(c.get(3),m(INVOKESPECIAL,props,"<init>","()V"));
        same(c.get(5),m(INVOKEVIRTUAL,props,old?"func_200918_c":"m_41503_","(I)L"+props+";"));
        same(c.get(6),m(INVOKESPECIAL,base,"<init>","(L"+props+";)V"));same(c.get(7),op(RETURN));
        var predicate=method(item,old?"func_82789_a":"m_6832_","(L"+stack+";L"+stack+";)Z",ACC_PUBLIC);
        var p=code(predicate);require(p.size()==8,"Only ingredient identity predicate");
        same(p.get(0),v(ALOAD,2));
        same(p.get(1),m(INVOKEVIRTUAL,stack,old?"func_77973_b":"m_41720_","()L"+base+";"));
        same(p.get(2),f(GETSTATIC,items,old?"field_151042_j":"f_42416_","L"+base+";"));
        branch(predicate,p,3,IF_ACMPNE,6);same(p.get(4),op(ICONST_1));
        branch(predicate,p,5,GOTO,7);same(p.get(6),op(ICONST_0));same(p.get(7),op(IRETURN));
        var ec=code(method(entry,"<init>","()V",ACC_PUBLIC));require(ec.size()==7,"Only mod-bus registration");same(ec.get(6),op(RETURN));
        // Reuse the registration grammar with a fully checked constructor-only admission view.
        // No node/array in the caller's trees is mutated, and no projection is transformed/emitted.
        var view=project(item,old);
        registrationEnvelope(entry,view,ec);
        var init=code(method(entry,"<clinit>","()V",ACC_STATIC));
        String name=(String)((LdcInsnNode)init.get(5)).cst;
        require(name.matches("[a-z0-9_]{1,128}"),"Simple registry path");
        return new Proof(entry.name,item.name,(String)identity.value,name,damage);
    }
    static ContentPlan validate(Map<String,ClassNode> nodes,String modId,String version) {
        var proof=inspect(nodes);require(proof.modId().equals(modId),"Metadata identity");
        require(nodes.get(proof.item()).superName.equals("net/minecraft/world/item/Item"),"Target-only resource view");
        return LogicProfileShape.validate(Map.of(proof.entry(),nodes.get(proof.entry()),
            proof.item(),project(nodes.get(proof.item()),false)),modId,version);
    }
    private static ClassNode project(ClassNode item,boolean old) {
        var copy=new ClassNode();item.accept(copy);
        copy.methods.removeIf(m->!m.name.equals("<init>"));
        var c=code(copy.methods.get(0));copy.methods.get(0).instructions.set(c.get(4),new InsnNode(ICONST_1));
        ((MethodInsnNode)c.get(5)).name=old?"func_200917_a":"m_41487_";
        return copy;
    }
    private static void branch(MethodNode method,List<AbstractInsnNode> code,int index,int opcode,int target) {
        require(code.get(index) instanceof JumpInsnNode j && j.getOpcode()==opcode,"Exact predicate branch");
        var jump=(JumpInsnNode)code.get(index);var cursor=(AbstractInsnNode)jump.label;
        require(method.instructions.indexOf(cursor)>=0,"Owned branch label");
        while(cursor!=null && cursor.getOpcode()<0)cursor=cursor.getNext();
        require(cursor==code.get(target),"Exact predicate branch target");
    }
    private static void require(boolean ok,String reason) {
        if(!ok)throw new TransformationException("DURABLE_ITEM_UNSUPPORTED: "+reason);
    }
}
