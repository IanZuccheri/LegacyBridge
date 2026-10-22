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
 * Legacy API: final owned Item, native durability(128), guarded native ItemStack damage hooks.
 * Target API: the corresponding exact native calls, independently verified by mapping/linkage.
 * Transformation: this complete structural proof changes no bytecode or classes and emits no view.
 * Behavior differences: damage APIs retain native Forge hook/NBT coercion semantics; they are not
 * raw NBT access. No automatic gameplay caller, thread/authority/synchronization contract is added.
 * Known limitations: exactly one public owned helper, same received stack, five guards, one
 * decrement/write; no hooks, wear, breakage, inventory/count changes, anvil, mending or world access.
 * Native qualification and malformed-NBT handling are not established by this structural proof.
 */
public final class OwnedStackDamageContract {
    private static final String DEFERRED="net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRY="net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRIES="net/minecraftforge/registries/ForgeRegistries";
    private static final String CONTEXT="net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS="net/minecraftforge/eventbus/api/IEventBus";
    private static final String OLD_ITEM="net/minecraft/item/Item", TARGET_ITEM="net/minecraft/world/item/Item";
    private OwnedStackDamageContract() { }

    public record Proof(String entry,String item,String modId,String registryName,String factory,String helper,int durability) {
        public Proof {
            for (var value : List.of(entry,item,modId,registryName,factory,helper))
                if (value.isBlank()) throw new IllegalArgumentException("Proof identities must be explicit");
            if (entry.equals(item) || !modId.matches("[a-z][a-z0-9_]{1,63}")
                || !registryName.matches("[a-z0-9_]{1,128}") || durability != 128)
                throw new IllegalArgumentException("Exact owned-stack identity and capacity required");
        }
        public Map<String,Object> toTree() {
            return Map.ofEntries(Map.entry("schemaVersion",1),Map.entry("contract","owned-stack-damage-v1"),
                Map.entry("entry",entry),Map.entry("item",item),Map.entry("modId",modId),Map.entry("registryName",registryName),
                Map.entry("factory",factory),Map.entry("helper",helper),Map.entry("durability",durability),Map.entry("maxStackSize",1),
                Map.entry("guards",List.of("RECEIVED_STACK_NOT_NULL","RECEIVED_STACK_ITEM_IS_THIS","NATIVE_IS_DAMAGED",
                    "OBSERVED_DAMAGE_GREATER_THAN_ZERO","OBSERVED_DAMAGE_AT_MOST_NATIVE_MAXIMUM")),
                Map.entry("mutation","EXACTLY_ONE_SET_DAMAGE_VALUE_OF_OBSERVED_DAMAGE_MINUS_ONE_ON_RECEIVED_STACK"),
                Map.entry("returns","FALSE_BEFORE_WRITE_ON_ANY_FAILED_GUARD; TRUE_AFTER_SINGLE_WRITE"),
                Map.entry("nativeData","FORGE_HOOKS_AND_NATIVE_NUMERIC_NBT_COERCION; NOT_RAW_NBT_OR_MALFORMED_DATA_VALIDATION"),
                Map.entry("trigger","NONE; PUBLIC_OWNED_HELPER_ONLY"),
                Map.entry("threadAuthorityAndSynchronization","CALLER_CONTRACT_NOT_IMPLEMENTED_OR_QUALIFIED"),
                Map.entry("wearBreakageAnvilMending","NOT_IMPLEMENTED"),
                Map.entry("externalDeclarations","REQUIRE_SEPARATE_VERIFICATION"),
                Map.entry("runtimeValidation","NOT_RUN"),Map.entry("classesExecuted",false));
        }
    }
    public static Proof inspect(Map<String,ClassNode> nodes) {
        try { return inspectClosed(nodes); }
        catch(TransformationException refused) {
            if(refused.getMessage().startsWith("OWNED_STACK_DAMAGE_UNSUPPORTED:"))throw refused;
            throw new TransformationException("OWNED_STACK_DAMAGE_UNSUPPORTED: "+refused.getMessage(),refused);
        }
    }
    private static Proof inspectClosed(Map<String,ClassNode> nodes) {
        OperationControl.checkpoint();
        require(nodes!=null&&nodes.size()==2,"Exactly an owned entry and final native Item");
        for(var pair:nodes.entrySet()) {
            var n=pair.getValue();
            require(n!=null&&n.name!=null&&n.name.equals(pair.getKey())&&!LegacyReferenceScanner.isProtectedDefinition(n.name),"Owned class identity");
            require(n.version==V1_8&&n.access==(ACC_PUBLIC|ACC_FINAL|ACC_SUPER)&&empty(n.interfaces)
                &&n.fields!=null&&n.fields.size()<=3&&n.methods!=null&&n.methods.size()<=3,"Bounded final Java-8 graph");
            metadata(n);
        }
        var entry=one(nodes,n->"java/lang/Object".equals(n.superName),"entry");
        var item=one(nodes,n->OLD_ITEM.equals(n.superName)||TARGET_ITEM.equals(n.superName),"native Item");
        require(entry!=item&&entry.fields.size()==3&&entry.methods.size()==3&&item.fields.isEmpty()&&item.methods.size()==2,
            "Exactly three registration fields/three entry methods and a stateless item/two methods");
        var api=new Names(TARGET_ITEM.equals(item.superName));
        innerMetadata(entry,api);innerMetadata(item,api);
        require(empty(item.visibleAnnotations)&&empty(item.invisibleAnnotations),"No item annotation or subscriber");
        var factories=entry.methods.stream().filter(m->m.desc.equals("()L"+api.item+";")).toList();
        require(factories.size()==1&&factories.get(0).access==(ACC_PRIVATE|ACC_STATIC)
            &&!factories.get(0).name.isBlank()&&!factories.get(0).name.startsWith("<"),"One private static Item factory");
        var factory=factories.get(0);
        var identity = fieldOf(entry, "Ljava/lang/String;");
        require(identity.value instanceof String value && value.matches("[a-z][a-z0-9_]{1,63}"), "Literal mod identity");
        String modId = (String)identity.value;
        var register = fieldOf(entry, "L" + DEFERRED + ";");
        var reference = fieldOf(entry, "L" + api.reference + ";");
        require(new HashSet<>(List.of(identity.name, register.name, reference.name)).size() == 3,
            "Three distinct field roles");
        checkField(identity, null); checkField(register, generic(DEFERRED, api.item));
        checkField(reference, generic(api.reference, api.item));
        require(register.value == null && reference.value == null, "Registry fields have no constant value");
        require(entry.visibleAnnotations != null && entry.visibleAnnotations.size() == 1 && empty(entry.invisibleAnnotations),
            "Only runtime-visible @Mod");
        var annotation = entry.visibleAnnotations.get(0);
        require(annotation != null && "Lnet/minecraftforge/fml/common/Mod;".equals(annotation.desc)
            && List.of("value", modId).equals(annotation.values), "Exact annotated mod identity");

        var init = code(method(entry, "<clinit>", "()V", ACC_STATIC));
        require(init.size() == 10, "Only one deferred registry declaration and one supplier registration");
        same(init.get(0), f(GETSTATIC, REGISTRIES, "ITEMS", "L" + REGISTRY + ";"));
        require(init.get(1) instanceof LdcInsnNode id && modId.equals(id.cst), "Exact modId registration literal");
        same(init.get(2), m(INVOKESTATIC, DEFERRED, "create", "(L" + REGISTRY + ";Ljava/lang/String;)L" + DEFERRED + ";"));
        same(init.get(3), f(PUTSTATIC, entry.name, register.name, register.desc));
        same(init.get(4), f(GETSTATIC, entry.name, register.name, register.desc));
        require(init.get(5) instanceof LdcInsnNode name && name.cst instanceof String value
            && value.matches("[a-z0-9_]{1,128}"), "Exact simple registry path literal");
        String registryName = (String)((LdcInsnNode)init.get(5)).cst;
        var handle = callback(init.get(6), "java/util/function/Supplier", "get", "()Ljava/lang/Object;", factory.desc, entry.name);
        require(handle.getName().equals(factory.name), "Supplier names the proved factory, not another callback");
        same(init.get(7), m(INVOKEVIRTUAL, DEFERRED, "register", "(Ljava/lang/String;Ljava/util/function/Supplier;)L" + api.reference + ";"));
        same(init.get(8), f(PUTSTATIC, entry.name, reference.name, reference.desc)); same(init.get(9), op(RETURN));
        exact(method(entry, "<init>", "()V", ACC_PUBLIC), v(ALOAD, 0), m(INVOKESPECIAL, "java/lang/Object", "<init>", "()V"),
            f(GETSTATIC, entry.name, register.name, register.desc), m(INVOKESTATIC, CONTEXT, "get", "()L" + CONTEXT + ";"),
            m(INVOKEVIRTUAL, CONTEXT, "getModEventBus", "()L" + BUS + ";"),
            m(INVOKEVIRTUAL, DEFERRED, "register", "(L" + BUS + ";)V"), op(RETURN));
        exact(factory,type(NEW,item.name),op(DUP),m(INVOKESPECIAL,item.name,"<init>","()V"),op(ARETURN));
        var ctor=method(item,"<init>","()V",ACC_PUBLIC);var c=code(ctor);
        require(c.size()==8,"Only fresh Properties and one capacity call before super");
        same(c.get(0),v(ALOAD,0));same(c.get(1),type(NEW,api.properties));same(c.get(2),op(DUP));
        same(c.get(3),m(INVOKESPECIAL,api.properties,"<init>","()V"));
        require(c.get(4) instanceof IntInsnNode literal&&literal.getOpcode()==SIPUSH&&literal.operand==128,"Exact SIPUSH128 capacity");
        same(c.get(5),m(INVOKEVIRTUAL,api.properties,api.durability,"(I)L"+api.properties+";"));
        same(c.get(6),m(INVOKESPECIAL,api.item,"<init>","(L"+api.properties+";)V"));same(c.get(7),op(RETURN));
        var helpers=item.methods.stream().filter(m->!m.name.startsWith("<")).toList();
        require(helpers.size()==1&&helpers.get(0).access==ACC_PUBLIC&&!helpers.get(0).name.isBlank()
            &&helpers.get(0).desc.equals("(L"+api.stack+";)Z"),"One public owned stack helper; external override collisions require independent rejection");
        var helper=helpers.get(0);var h=code(helper);
        require(h.size()==29,"Only five guards and one bounded decrement/write");
        same(h.get(0),v(ALOAD,1));branch(helper,h,1,IFNULL,9);
        same(h.get(2),v(ALOAD,1));same(h.get(3),m(INVOKEVIRTUAL,api.stack,api.getItem,"()L"+api.item+";"));
        same(h.get(4),v(ALOAD,0));branch(helper,h,5,IF_ACMPNE,9);
        same(h.get(6),v(ALOAD,1));same(h.get(7),m(INVOKEVIRTUAL,api.stack,api.isDamaged,"()Z"));branch(helper,h,8,IFNE,11);
        same(h.get(9),op(ICONST_0));same(h.get(10),op(IRETURN));
        same(h.get(11),v(ALOAD,1));same(h.get(12),m(INVOKEVIRTUAL,api.stack,api.getDamage,"()I"));same(h.get(13),v(ISTORE,2));
        same(h.get(14),v(ILOAD,2));branch(helper,h,15,IFLE,20);
        same(h.get(16),v(ILOAD,2));same(h.get(17),v(ALOAD,1));same(h.get(18),m(INVOKEVIRTUAL,api.stack,api.getMax,"()I"));
        branch(helper,h,19,IF_ICMPLE,22);same(h.get(20),op(ICONST_0));same(h.get(21),op(IRETURN));
        same(h.get(22),v(ALOAD,1));same(h.get(23),v(ILOAD,2));same(h.get(24),op(ICONST_1));same(h.get(25),op(ISUB));
        same(h.get(26),m(INVOKEVIRTUAL,api.stack,api.setDamage,"(I)V"));same(h.get(27),op(ICONST_1));same(h.get(28),op(IRETURN));
        OperationControl.checkpoint();
        return new Proof(entry.name,item.name,modId,registryName,factory.name,helper.name,128);
    }
    /** Target resource inventory only; not a bootstrap, generic interpreter or caller implementation. */
    public static ContentPlan validate(Map<String,ClassNode> nodes,String modId,String version) {
        var proof=inspect(nodes);
        require(proof.modId().equals(modId),"Exact metadata identity");
        require(version!=null&&version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}"),"Exact finite version");
        require(TARGET_ITEM.equals(nodes.get(proof.item()).superName),"Target-only resource inventory");
        return new ContentPlan(modId,version,List.of(new ContentPlan.ItemDefinition(proof.registryName(),null,
            new ContentPlan.ItemProperties(1,128,false,"COMMON",null))),List.of(),List.of());
    }
    private static void branch(MethodNode method,List<AbstractInsnNode> code,int at,int opcode,int destination) {
        require(code.get(at) instanceof JumpInsnNode jump&&jump.getOpcode()==opcode,"Exact guard opcode");
        var cursor=(AbstractInsnNode)((JumpInsnNode)code.get(at)).label;
        require(cursor!=null&&method.instructions.indexOf(cursor)>=0,"Owned guard label");
        while(cursor!=null&&cursor.getOpcode()<0)cursor=cursor.getNext();
        require(cursor==code.get(destination),"Exact guard destination; no bypass or additional cycle");
    }
    private static void metadata(ClassNode entry) {
        require(entry.signature == null && entry.outerClass == null && entry.outerMethod == null && entry.outerMethodDesc == null
            && entry.nestHostClass == null && empty(entry.nestMembers) && empty(entry.permittedSubclasses)
            && empty(entry.recordComponents) && entry.module == null && empty(entry.attrs)
            && empty(entry.visibleTypeAnnotations) && empty(entry.invisibleTypeAnnotations), "No additional class metadata contracts");
        var fields = new HashSet<String>();
        for (var field : entry.fields) require(field != null && field.name != null && !field.name.isBlank()
            && field.desc != null && fields.add(field.name), "Unique named field roles");
        var methods = new HashSet<String>();
        for (var method : entry.methods) {
            OperationControl.checkpoint();
            require(method != null && method.name != null && method.desc != null && methods.add(method.name + method.desc)
                && method.instructions != null && method.instructions.size() <= 128, "Bounded unique methods");
            require(method.maxStack >= 0 && method.maxStack <= 64 && method.maxLocals >= 0 && method.maxLocals <= 64,
                "Bounded declared stack and local slots before offline frame verification");
            require(method.signature == null && empty(method.tryCatchBlocks) && empty(method.exceptions)
                && empty(method.visibleAnnotations) && empty(method.invisibleAnnotations)
                && empty(method.visibleTypeAnnotations) && empty(method.invisibleTypeAnnotations)
                && empty(method.visibleLocalVariableAnnotations) && empty(method.invisibleLocalVariableAnnotations)
                && method.visibleParameterAnnotations == null && method.invisibleParameterAnnotations == null
                && method.visibleAnnotableParameterCount == 0 && method.invisibleAnnotableParameterCount == 0
                && method.annotationDefault == null && empty(method.attrs) && empty(method.parameters),
                "No handlers, generic, annotation or additional method contracts");
            for (var instruction : method.instructions) require(empty(instruction.visibleTypeAnnotations)
                && empty(instruction.invisibleTypeAnnotations), "No instruction annotation contracts");
        }
    }

    private static void checkField(FieldNode field, String signature) {
        require(field.access == (ACC_PUBLIC | ACC_STATIC | ACC_FINAL)
            && (field.signature == null || Objects.equals(field.signature, signature))
            && empty(field.visibleAnnotations) && empty(field.invisibleAnnotations)
            && empty(field.visibleTypeAnnotations) && empty(field.invisibleTypeAnnotations) && empty(field.attrs),
            "Only final constant identity and native registry fields with exact optional generic signature");
    }

    private static void innerMetadata(ClassNode entry, Names api) {
        if (entry.innerClasses == null) return;
        var names = new HashSet<String>();
        for (var inner : entry.innerClasses) {
            require(inner != null && inner.name != null && names.add(inner.name)
                && (entry.superName.equals("java/lang/Object") ? Set.of(api.properties, "java/lang/invoke/MethodHandles$Lookup") : Set.of(api.properties)).contains(inner.name),
                "Only native Properties and method-handle Lookup inner metadata");
            int split = inner.name.lastIndexOf('$');
            require(inner.name.substring(0, split).equals(inner.outerName) && inner.name.substring(split + 1).equals(inner.innerName)
                && inner.access == (ACC_PUBLIC | ACC_STATIC | (inner.name.endsWith("$Lookup") ? ACC_FINAL : 0)),
                "Exact known native inner-class metadata");
        }
    }

    private static String generic(String owner,String parameter) { return "L"+owner+"<L"+parameter+";>;"; }
    private static void require(boolean accepted,String message) {
        if(!accepted)throw new TransformationException("OWNED_STACK_DAMAGE_UNSUPPORTED: "+message);
    }
    private static final class Names {
        final String item,properties,reference,stack,durability,getItem,isDamaged,getDamage,getMax,setDamage;
        Names(boolean target) {
            item=target?TARGET_ITEM:OLD_ITEM;properties=item+"$Properties";
            reference=target?"net/minecraftforge/registries/RegistryObject":"net/minecraftforge/fml/RegistryObject";
            stack=target?"net/minecraft/world/item/ItemStack":"net/minecraft/item/ItemStack";
            durability=target?"m_41503_":"func_200918_c";getItem=target?"m_41720_":"func_77973_b";
            isDamaged=target?"m_41768_":"func_77951_h";getDamage=target?"m_41773_":"func_77952_i";
            getMax=target?"m_41776_":"func_77958_k";setDamage=target?"m_41721_":"func_196085_b";
        }
    }
}
