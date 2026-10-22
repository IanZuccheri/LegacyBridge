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
 * Legacy API: finite native Item.use(World,PlayerEntity,Hand), inherited hand lookup,
 * World.isClientSide and erased ActionResult factories, plus the owned guarded damage helper.
 * Target API: Item.use(Level,Player,InteractionHand), corresponding inherited lookup,
 * Level.isClientSide and InteractionResultHolder factories; declarations verified separately.
 * Transformation: NONE here. A fully checked deep admission view reuses the helper grammar;
 * the original six methods and all metadata must be transformed and preserved, never the view.
 * Behavior differences: client SUCCESS predicts consumption/swing even on rejected repair;
 * native creative restoration, event/cooldown/spectator bypass and synchronization remain native.
 * Known limitations: one lookup of the received player's received hand, no client helper call,
 * one server helper call on this and same-stack CONSUME/PASS. No arbitrary caller thread/world
 * authority, packet replay, native behavior, synchronization or multiplayer qualification follows.
 */
public final class OwnedStackRepairUseContract {
    private static final String OLD_ITEM="net/minecraft/item/Item", TARGET_ITEM="net/minecraft/world/item/Item";
    private OwnedStackRepairUseContract() { }

    public record Proof(String entry,String item,String modId,String registryName,String factory,String helper,int durability) {
        public Proof { new OwnedStackDamageContract.Proof(entry,item,modId,registryName,factory,helper,durability); }
        /** A proved helper sub-contract, not permission to emit a callback-free replacement. */
        public OwnedStackDamageContract.Proof stackProof() {
            return new OwnedStackDamageContract.Proof(entry,item,modId,registryName,factory,helper,durability);
        }
        public Map<String,Object> toTree() {
            return Map.ofEntries(Map.entry("schemaVersion",1),Map.entry("contract","owned-stack-repair-use-v1"),
                Map.entry("entry",entry),Map.entry("item",item),Map.entry("modId",modId),Map.entry("registryName",registryName),
                Map.entry("factory",factory),Map.entry("helper",helper),Map.entry("durability",durability),Map.entry("maxStackSize",1),
                Map.entry("helperProof",stackProof().toTree()),
                Map.entry("handLookup","EXACTLY_ONCE_ON_RECEIVED_PLAYER_WITH_RECEIVED_HAND"),
                Map.entry("client","SUCCESS_WITH_SAME_STACK; NO_HELPER_OR_MUTATION; PREDICTED_CONSUMPTION_AND_SWING"),
                Map.entry("server","ONE_THIS_HELPER_CALL; TRUE_CONSUME_OR_FALSE_PASS_WITH_SAME_STACK"),
                Map.entry("nativeCreativeUse","NATIVE_CALLER_RESTORES_PRIOR_DAMAGE; HELPER_TRUE_IS_NOT_PERSISTENT_CREATIVE_REPAIR"),
                Map.entry("nativeBypass","FORGE_CANCELLATION_COOLDOWN_SPECTATOR_AND_PRIOR_BLOCK_ENTITY_INTERACTION_RETAINED"),
                Map.entry("threadAuthorityAndSynchronization","NATIVE_CALLER_PATH_UNMODIFIED; ARBITRARY_CALLER_AUTHORITY_AND_SYNC_NOT_QUALIFIED"),
                Map.entry("normalization","CHECKED_DEEP_ADMISSION_VIEW_ONLY; NEVER_TRANSFORMED_OR_EMITTED"),
                Map.entry("externalDeclarations","REQUIRE_SEPARATE_VERIFICATION"),
                Map.entry("runtimeValidation","NOT_RUN"),Map.entry("classesExecuted",false));
        }
    }

    public static Proof inspect(Map<String,ClassNode> nodes) {
        try { return inspectClosed(nodes); }
        catch(TransformationException refused) {
            if(refused.getMessage().startsWith("OWNED_STACK_REPAIR_USE_UNSUPPORTED:"))throw refused;
            throw new TransformationException("OWNED_STACK_REPAIR_USE_UNSUPPORTED: "+refused.getMessage(),refused);
        }
    }
    private static Proof inspectClosed(Map<String,ClassNode> nodes) {
        OperationControl.checkpoint();
        require(nodes!=null&&nodes.size()==2,"Exactly one entry and one final native Item subclass");
        for(var pair:nodes.entrySet()) {
            var n=pair.getValue();
            require(n!=null&&n.name!=null&&n.name.equals(pair.getKey())&&!LegacyReferenceScanner.isProtectedDefinition(n.name),"Owned class identity");
            require(n.version==V1_8&&n.access==(ACC_PUBLIC|ACC_FINAL|ACC_SUPER)&&empty(n.interfaces)
                &&n.fields!=null&&n.fields.size()<=3&&n.methods!=null&&n.methods.size()==3,"Bounded final Java-8 graph with three methods per class");
            var fields=new HashSet<String>();
            for(var f:n.fields)require(f!=null&&f.name!=null&&!f.name.isBlank()&&f.desc!=null&&fields.add(f.name),"Unique original fields before copying");
            var methods=new HashSet<String>();
            for(var m:n.methods) {
                OperationControl.checkpoint();
                require(m!=null&&m.name!=null&&!m.name.isBlank()&&m.desc!=null&&methods.add(m.name+m.desc)
                    &&m.instructions!=null&&m.instructions.size()<=128&&m.maxStack>=0&&m.maxStack<=64&&m.maxLocals>=0&&m.maxLocals<=64,
                    "Bounded unique original methods before frame allocation or copying");
                for(var instruction:m.instructions)require(empty(instruction.visibleTypeAnnotations)&&empty(instruction.invisibleTypeAnnotations),
                    "No original instruction annotation may disappear in the admission view");
            }
        }
        var entry=one(nodes,n->"java/lang/Object".equals(n.superName),"entry");
        var item=one(nodes,n->OLD_ITEM.equals(n.superName)||TARGET_ITEM.equals(n.superName),"native Item subclass");
        require(entry!=item&&entry.fields.size()==3&&item.fields.isEmpty(),"Only three entry registration fields; stateless item");
        var api=new Names(TARGET_ITEM.equals(item.superName));
        var use=method(item,api.use,api.useDesc(),ACC_PUBLIC);
        useMetadata(use,item.name,api);
        var helpers=item.methods.stream().filter(m->!m.name.startsWith("<")&&m!=use).toList();
        require(helpers.size()==1&&helpers.get(0).access==ACC_PUBLIC&&helpers.get(0).desc.equals("(L"+api.stack+";)Z"),"Exactly one public owned stack helper beside the native callback");
        var helper=helpers.get(0);var body=code(use);
        require(body.size()==20,"Only one hand lookup, logical-side branch and one server helper call");
        same(body.get(0),v(ALOAD,2));same(body.get(1),v(ALOAD,3));
        same(body.get(2),m(INVOKEVIRTUAL,api.player,api.getHand,"(L"+api.hand+";)L"+api.stack+";"));
        same(body.get(3),v(ASTORE,4));same(body.get(4),v(ALOAD,1));same(body.get(5),f(GETFIELD,api.world,api.clientSide,"Z"));
        branch(use,body,6,IFEQ,10);same(body.get(7),v(ALOAD,4));same(body.get(8),m(INVOKESTATIC,api.holder,api.success,api.factoryDesc()));same(body.get(9),op(ARETURN));
        same(body.get(10),v(ALOAD,0));same(body.get(11),v(ALOAD,4));same(body.get(12),m(INVOKEVIRTUAL,item.name,helper.name,helper.desc));
        branch(use,body,13,IFEQ,17);same(body.get(14),v(ALOAD,4));same(body.get(15),m(INVOKESTATIC,api.holder,api.consume,api.factoryDesc()));
        branch(use,body,16,GOTO,19);same(body.get(17),v(ALOAD,4));same(body.get(18),m(INVOKESTATIC,api.holder,api.pass,api.factoryDesc()));same(body.get(19),op(ARETURN));

        // Nothing unknown disappears: every removed callback instruction and metadata contract
        // is checked above. The independent helper proof checks everything remaining in this
        // deep view. This map is not returned and must never become converted output.
        var view=new TreeMap<String,ClassNode>();
        nodes.forEach((name,node)->{var copy=new ClassNode();node.accept(copy);view.put(name,copy);});
        var viewItem=view.get(item.name);viewItem.methods.remove(method(viewItem,use.name,use.desc,ACC_PUBLIC));
        var proof=OwnedStackDamageContract.inspect(view);
        require(proof.entry().equals(entry.name)&&proof.item().equals(item.name)&&proof.helper().equals(helper.name),"Admission roles match the original six-method program");
        OperationControl.checkpoint();
        return new Proof(proof.entry(),proof.item(),proof.modId(),proof.registryName(),proof.factory(),proof.helper(),proof.durability());
    }

    /** Target-only resource inventory. This is not a bootstrap or permission to discard logic. */
    public static ContentPlan validate(Map<String,ClassNode> nodes,String modId,String version) {
        var proof=inspect(nodes);
        require(proof.modId().equals(modId),"Exact metadata identity");
        require(version!=null&&version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}"),"Exact finite version");
        require(TARGET_ITEM.equals(nodes.get(proof.item()).superName),"Target-only resource inventory");
        return new ContentPlan(modId,version,List.of(new ContentPlan.ItemDefinition(proof.registryName(),null,
            new ContentPlan.ItemProperties(1,proof.durability(),false,"COMMON",null))),List.of(),List.of());
    }

    private static void useMetadata(MethodNode method,String owner,Names api) {
        require(api.useSignature().equals(method.signature)&&empty(method.tryCatchBlocks)&&empty(method.exceptions)
            &&empty(method.visibleAnnotations)&&empty(method.invisibleAnnotations)&&empty(method.visibleTypeAnnotations)&&empty(method.invisibleTypeAnnotations)
            &&empty(method.visibleLocalVariableAnnotations)&&empty(method.invisibleLocalVariableAnnotations)
            &&method.visibleParameterAnnotations==null&&method.invisibleParameterAnnotations==null
            &&method.visibleAnnotableParameterCount==0&&method.invisibleAnnotableParameterCount==0
            &&method.annotationDefault==null&&empty(method.attrs)&&empty(method.parameters),
            "Only exact native callback generic signature; no extra annotation, handler or method metadata contract");
        var locals=List.of(owner,api.world,api.player,api.hand,api.stack);
        if(method.localVariables!=null)for(var local:method.localVariables)require(local!=null&&local.index>=0&&local.index<locals.size()
            &&("L"+locals.get(local.index)+";").equals(local.desc)&&local.signature==null&&local.name!=null
            &&ownedLabel(method,local.start)&&ownedLabel(method,local.end)&&method.instructions.indexOf(local.start)<method.instructions.indexOf(local.end),
            "Only role-correct native callback debug locals with owned ranges");
        var references=new HashSet<>(locals);references.add(api.holder);
        for(var instruction:method.instructions) {
            if(instruction instanceof FrameNode frame) {
                frameValues(frame.local,references);frameValues(frame.stack,references);
            } else if(instruction instanceof LineNumberNode line)require(ownedLabel(method,line.start),"Owned callback line-number label");
        }
    }
    private static void frameValues(List<Object> values,Set<String> allowed) {
        if(values!=null)for(var value:values)require(value instanceof String name&&allowed.contains(name)
            ||value instanceof Integer tag&&(tag==TOP||tag==NULL),"Only reviewed callback reference frame types");
    }
    private static boolean ownedLabel(MethodNode method,LabelNode label) { return label!=null&&method.instructions.indexOf(label)>=0; }
    private static void branch(MethodNode method,List<AbstractInsnNode> body,int at,int opcode,int destination) {
        require(body.get(at) instanceof JumpInsnNode jump&&jump.getOpcode()==opcode,"Exact callback branch opcode");
        var cursor=(AbstractInsnNode)((JumpInsnNode)body.get(at)).label;
        require(cursor instanceof LabelNode label&&ownedLabel(method,label),"Owned callback branch label");
        while(cursor!=null&&cursor.getOpcode()<0)cursor=cursor.getNext();
        require(cursor==body.get(destination),"Exact callback destination; no bypass, wrong side or cycle");
    }
    private static void require(boolean value,String message) {
        if(!value)throw new TransformationException("OWNED_STACK_REPAIR_USE_UNSUPPORTED: "+message);
    }
    private static final class Names {
        final String stack,world,player,hand,holder,use,getHand,clientSide,success,consume,pass;
        Names(boolean target) {
            stack=target?"net/minecraft/world/item/ItemStack":"net/minecraft/item/ItemStack";
            world=target?"net/minecraft/world/level/Level":"net/minecraft/world/World";
            player=target?"net/minecraft/world/entity/player/Player":"net/minecraft/entity/player/PlayerEntity";
            hand=target?"net/minecraft/world/InteractionHand":"net/minecraft/util/Hand";
            holder=target?"net/minecraft/world/InteractionResultHolder":"net/minecraft/util/ActionResult";
            use=target?"m_7203_":"func_77659_a";getHand=target?"m_21120_":"func_184586_b";clientSide=target?"f_46443_":"field_72995_K";
            success=target?"m_19090_":"func_226248_a_";consume=target?"m_19096_":"func_226249_b_";pass=target?"m_19098_":"func_226250_c_";
        }
        String useDesc(){return "(L"+world+";L"+player+";L"+hand+";)L"+holder+";";}
        String useSignature(){return "(L"+world+";L"+player+";L"+hand+";)L"+holder+"<L"+stack+";>;";}
        String factoryDesc(){return "(Ljava/lang/Object;)L"+holder+";";}
    }
}
