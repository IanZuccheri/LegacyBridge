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
 * Legacy API: owned Item.getDefaultAttributeModifiers(EquipmentSlotType), native UUID objects,
 * AttributeModifier(UUID,String,double,ADDITION), and the audited immutable Guava builder.
 * Target API: the corresponding EquipmentSlot endpoint, native UUID objects and immutable map.
 * Transformation: this class only proves the complete finite owned graph; external declarations,
 * exact mappings, offline linkage and emitted readback are separate mandatory checks.
 * Behavior differences: native ItemStack NBT/event processing remains outside this callback;
 * this proof does not establish combat, equipment swaps, tooltips or multiplayer equivalence.
 * Known limitations: one native Item subclass, two fixed additive MAINHAND modifiers, no other
 * property customization, mutable state, sword/tool inheritance, callbacks or execution effects.
 * Production names are audited guards, not a mapping database. No original class is loaded.
 */
public final class ItemAttributeContract {
    private static final String DEFERRED = "net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRIES = "net/minecraftforge/registries/ForgeRegistries";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final String MULTIMAP = "com/google/common/collect/Multimap";
    private static final String IMMUTABLE = "com/google/common/collect/ImmutableMultimap";
    private static final String BUILDER = IMMUTABLE + "$Builder";
    private static final String OLD_ITEM = "net/minecraft/item/Item";
    private static final String TARGET_ITEM = "net/minecraft/world/item/Item";
    private ItemAttributeContract() { }

    public record Proof(String entry, String item, String modId, String registryName, String factory,
                        String modifiersField, String damageName, String speedName) {
        public Map<String,Object> toTree() {
            return Map.ofEntries(Map.entry("schemaVersion",1), Map.entry("contract","owned-item-attribute-modifiers-v1"),
                Map.entry("entry",entry), Map.entry("item",item), Map.entry("modId",modId),
                Map.entry("registryName",registryName), Map.entry("factory",factory), Map.entry("modifiersField",modifiersField),
                Map.entry("slot","MAINHAND"), Map.entry("otherSlots","EMPTY_IMMUTABLE_MULTIMAP"),
                Map.entry("modifiers",List.of(
                    Map.of("attribute","ATTACK_DAMAGE","uuidSource","NATIVE_ITEM_BASE_ATTACK_DAMAGE_UUID_OBJECT",
                        "name",damageName,"amount",2.0D,"operation","ADDITION"),
                    Map.of("attribute","ATTACK_SPEED","uuidSource","NATIVE_ITEM_BASE_ATTACK_SPEED_UUID_OBJECT",
                        "name",speedName,"amount",-1.0D,"operation","ADDITION"))),
                Map.entry("state","ONE_STATIC_FINAL_IMMUTABLE_MULTIMAP; NO_PLAYER_OR_INSTANCE_STATE"),
                Map.entry("properties","FRESH_NATIVE_PROPERTIES_WITHOUT_CUSTOMIZATION"),
                Map.entry("registration","ONE_ZERO_CAPTURE_STATIC_SUPPLIER_ON_MOD_BUS"),
                Map.entry("externalDeclarations","REQUIRE_SEPARATE_VERIFICATION"),
                Map.entry("nativeNbtAndEvents","NOT_BYPASSED; NATIVE_BEHAVIOR_NOT_QUALIFIED"),
                Map.entry("runtimeValidation","NOT_RUN"), Map.entry("classesExecuted",false));
        }
    }

    public static Proof inspect(Map<String,ClassNode> nodes) {
        try { return inspectClosed(nodes); }
        catch (TransformationException refused) {
            if (refused.getMessage().startsWith("ITEM_ATTRIBUTES_UNSUPPORTED:")) throw refused;
            throw new TransformationException("ITEM_ATTRIBUTES_UNSUPPORTED: " + refused.getMessage(),refused);
        }
    }

    private static Proof inspectClosed(Map<String,ClassNode> nodes) {
        OperationControl.checkpoint();
        require(nodes != null && nodes.size() == 2,"Exactly an entry and a final native Item subclass");
        for (var pair : nodes.entrySet()) {
            var n = pair.getValue();
            require(n != null && n.name != null && n.name.equals(pair.getKey())
                && !LegacyReferenceScanner.isProtectedDefinition(n.name),"Owned class identity");
            require(n.version == V1_8 && n.access == (ACC_PUBLIC | ACC_FINAL | ACC_SUPER)
                && empty(n.interfaces) && n.fields != null && n.methods != null && n.methods.size() == 3,
                "Two final Java-8 classes with exactly three methods each");
            metadata(n);
        }
        var entry = one(nodes,n -> "java/lang/Object".equals(n.superName),"entry");
        var item = one(nodes,n -> OLD_ITEM.equals(n.superName) || TARGET_ITEM.equals(n.superName),"native Item subclass");
        require(entry != item && entry.fields.size() == 3 && item.fields.size() == 1,"Distinct finite registration and item roles");
        var api = new Names(TARGET_ITEM.equals(item.superName));
        innerMetadata(entry,Map.of(api.properties,ACC_PUBLIC | ACC_STATIC,
            "java/lang/invoke/MethodHandles$Lookup",ACC_PUBLIC | ACC_STATIC | ACC_FINAL));
        innerMetadata(item,Map.of(api.properties,ACC_PUBLIC | ACC_STATIC,BUILDER,ACC_PUBLIC | ACC_STATIC,
            api.operation,ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_ENUM));
        require(empty(item.visibleAnnotations) && empty(item.invisibleAnnotations),"No item annotations or subscribers");

        var identity = fieldOf(entry,"Ljava/lang/String;");
        require(identity.value instanceof String value && value.matches("[a-z][a-z0-9_]{1,63}"),"Literal mod identity");
        String modId = (String)identity.value;
        var register = fieldOf(entry,"L" + DEFERRED + ";");
        var reference = fieldOf(entry,"L" + api.reference + ";");
        field(identity,ACC_PUBLIC | ACC_STATIC | ACC_FINAL,null);
        field(register,ACC_PUBLIC | ACC_STATIC | ACC_FINAL,generic(DEFERRED,api.item));
        field(reference,ACC_PUBLIC | ACC_STATIC | ACC_FINAL,generic(api.reference,api.item));
        require(register.value == null && reference.value == null,"No native registry constants");
        require(entry.visibleAnnotations != null && entry.visibleAnnotations.size() == 1 && empty(entry.invisibleAnnotations),
            "Only runtime-visible Mod annotation");
        var annotation = entry.visibleAnnotations.get(0);
        require(annotation != null && "Lnet/minecraftforge/fml/common/Mod;".equals(annotation.desc)
            && List.of("value",modId).equals(annotation.values),"Exact annotated mod identity");

        var factories = entry.methods.stream().filter(m -> m.desc.equals("()L" + api.item + ";")).toList();
        require(factories.size() == 1 && factories.get(0).access == (ACC_PRIVATE | ACC_STATIC)
            && !factories.get(0).name.startsWith("<"),"One private static native Item factory");
        var factory = factories.get(0);
        for (var method : entry.methods) require(method.signature == null,"No extra generic entry method contracts");
        exact(factory,type(NEW,item.name),op(DUP),type(NEW,api.properties),op(DUP),
            m(INVOKESPECIAL,api.properties,"<init>","()V"),m(INVOKESPECIAL,item.name,"<init>","(L"+api.properties+";)V"),op(ARETURN));
        exact(method(entry,"<init>","()V",ACC_PUBLIC),v(ALOAD,0),m(INVOKESPECIAL,"java/lang/Object","<init>","()V"),
            f(GETSTATIC,entry.name,register.name,register.desc),m(INVOKESTATIC,CONTEXT,"get","()L"+CONTEXT+";"),
            m(INVOKEVIRTUAL,CONTEXT,"getModEventBus","()L"+BUS+";"),m(INVOKEVIRTUAL,DEFERRED,"register","(L"+BUS+";)V"),op(RETURN));
        var init = code(method(entry,"<clinit>","()V",ACC_STATIC));
        require(init.size() == 10,"Only one deferred declaration and registration");
        same(init.get(0),f(GETSTATIC,REGISTRIES,"ITEMS","L"+REGISTRY+";"));
        require(init.get(1) instanceof LdcInsnNode id && modId.equals(id.cst),"Exact modId registration literal");
        same(init.get(2),m(INVOKESTATIC,DEFERRED,"create","(L"+REGISTRY+";Ljava/lang/String;)L"+DEFERRED+";"));
        same(init.get(3),f(PUTSTATIC,entry.name,register.name,register.desc));
        same(init.get(4),f(GETSTATIC,entry.name,register.name,register.desc));
        require(init.get(5) instanceof LdcInsnNode name && name.cst instanceof String value
            && value.matches("[a-z0-9_]{1,128}"),"Literal simple registry path");
        String registryName = (String)((LdcInsnNode)init.get(5)).cst;
        var supplier = callback(init.get(6),"java/util/function/Supplier","get","()Ljava/lang/Object;",factory.desc,entry.name);
        require(supplier.getName().equals(factory.name),"Supplier invokes exactly the proven factory");
        same(init.get(7),m(INVOKEVIRTUAL,DEFERRED,"register","(Ljava/lang/String;Ljava/util/function/Supplier;)L"+api.reference+";"));
        same(init.get(8),f(PUTSTATIC,entry.name,reference.name,reference.desc)); same(init.get(9),op(RETURN));

        var modifiers = item.fields.get(0);
        require(modifiers.desc.equals("L"+MULTIMAP+";") && modifiers.value == null,"One nonconstant immutable map reference");
        field(modifiers,ACC_PRIVATE | ACC_STATIC | ACC_FINAL,api.mapSignature());
        var ctor = method(item,"<init>","(L"+api.properties+";)V",ACC_PUBLIC);
        var clinit = method(item,"<clinit>","()V",ACC_STATIC);
        var endpoint = method(item,api.endpoint,"(L"+api.slot+";)L"+MULTIMAP+";",ACC_PUBLIC);
        require(ctor.signature == null && clinit.signature == null
            && (endpoint.signature == null || endpoint.signature.equals("(L"+api.slot+";)"+api.mapSignature())),
            "Only the exact optional native attribute generic signature");
        exact(ctor,v(ALOAD,0),v(ALOAD,1),m(INVOKESPECIAL,api.item,"<init>","(L"+api.properties+";)V"),op(RETURN));
        var code = code(clinit);
        require(code.size() == 22,"Exactly two fixed immutable builder entries and one owned assignment");
        same(code.get(0),m(INVOKESTATIC,IMMUTABLE,"builder","()L"+BUILDER+";"));
        String damageName = modifier(code,1,item.name,api,api.damageAttribute,api.damageUuid,2.0D);
        String speedName = modifier(code,10,item.name,api,api.speedAttribute,api.speedUuid,-1.0D);
        same(code.get(19),m(INVOKEVIRTUAL,BUILDER,"build","()L"+IMMUTABLE+";"));
        same(code.get(20),f(PUTSTATIC,item.name,modifiers.name,modifiers.desc)); same(code.get(21),op(RETURN));
        var body = code(endpoint); require(body.size() == 7,"Only MAINHAND identity selection and empty immutable fallback");
        same(body.get(0),v(ALOAD,1)); same(body.get(1),f(GETSTATIC,api.slot,"MAINHAND","L"+api.slot+";"));
        branch(endpoint,body,2,IF_ACMPNE,5);
        same(body.get(3),f(GETSTATIC,item.name,modifiers.name,modifiers.desc)); branch(endpoint,body,4,GOTO,6);
        same(body.get(5),m(INVOKESTATIC,IMMUTABLE,"of","()L"+IMMUTABLE+";")); same(body.get(6),op(ARETURN));
        OperationControl.checkpoint();
        return new Proof(entry.name,item.name,modId,registryName,factory.name,modifiers.name,damageName,speedName);
    }

    /** Target resource inventory only. It must never replace the original owned class bodies. */
    public static ContentPlan validate(Map<String,ClassNode> nodes,String modId,String version) {
        var proof = inspect(nodes);
        require(proof.modId().equals(modId),"Metadata modId differs from proven registration");
        require(version != null && version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}"),"Exact finite mod version");
        require(TARGET_ITEM.equals(nodes.get(proof.item()).superName),"Target-only resource inventory");
        return new ContentPlan(modId,version,List.of(new ContentPlan.ItemDefinition(proof.registryName(),null,
            new ContentPlan.ItemProperties(64,0,false,"COMMON",null))),List.of(),List.of());
    }

    private static String modifier(List<AbstractInsnNode> body,int offset,String owner,Names api,String attribute,String uuid,double amount) {
        same(body.get(offset),f(GETSTATIC,api.attributes,attribute,"L"+api.attribute+";"));
        same(body.get(offset+1),type(NEW,api.modifier)); same(body.get(offset+2),op(DUP));
        same(body.get(offset+3),f(GETSTATIC,owner,uuid,"Ljava/util/UUID;"));
        require(body.get(offset+4) instanceof LdcInsnNode name && name.cst instanceof String value && printable(value),
            "Bounded printable modifier name literal, without callbacks");
        require(body.get(offset+5) instanceof LdcInsnNode constant && constant.cst instanceof Double number
            && Double.doubleToLongBits(number) == Double.doubleToLongBits(amount),"Exact native double amount");
        same(body.get(offset+6),f(GETSTATIC,api.operation,"ADDITION","L"+api.operation+";"));
        same(body.get(offset+7),m(INVOKESPECIAL,api.modifier,"<init>","(Ljava/util/UUID;Ljava/lang/String;DL"+api.operation+";)V"));
        same(body.get(offset+8),m(INVOKEVIRTUAL,BUILDER,"put","(Ljava/lang/Object;Ljava/lang/Object;)L"+BUILDER+";"));
        return (String)((LdcInsnNode)body.get(offset+4)).cst;
    }
    private static boolean printable(String value) {
        return !value.isBlank() && value.length()<=128 && value.codePoints().noneMatch(c->Character.isISOControl(c)
            || Character.getType(c)==Character.FORMAT || Character.getType(c)==Character.SURROGATE);
    }
    private static void metadata(ClassNode node) {
        require(node.signature == null && node.outerClass == null && node.outerMethod == null && node.outerMethodDesc == null
            && node.nestHostClass == null && empty(node.nestMembers) && empty(node.permittedSubclasses)
            && empty(node.recordComponents) && node.module == null && empty(node.attrs)
            && empty(node.visibleTypeAnnotations) && empty(node.invisibleTypeAnnotations),"No additional class metadata contracts");
        var fields = new HashSet<String>();
        for (var field : node.fields) require(field != null && field.name != null && !field.name.isBlank()
            && field.desc != null && fields.add(field.name),"Unique named field roles");
        var methods = new HashSet<String>();
        for (var method : node.methods) {
            OperationControl.checkpoint();
            require(method != null && method.name != null && !method.name.isBlank() && method.desc != null
                && methods.add(method.name+method.desc) && method.instructions != null && method.instructions.size()<=128
                && method.maxStack>=0 && method.maxStack<=64 && method.maxLocals>=0 && method.maxLocals<=64,"Bounded unique methods and frames");
            require(empty(method.tryCatchBlocks) && empty(method.exceptions) && empty(method.visibleAnnotations)
                && empty(method.invisibleAnnotations) && empty(method.visibleTypeAnnotations) && empty(method.invisibleTypeAnnotations)
                && empty(method.visibleLocalVariableAnnotations) && empty(method.invisibleLocalVariableAnnotations)
                && method.visibleParameterAnnotations == null && method.invisibleParameterAnnotations == null
                && method.annotationDefault == null && empty(method.attrs) && empty(method.parameters),"No extra method contracts");
            for (var instruction : method.instructions) require(empty(instruction.visibleTypeAnnotations)
                && empty(instruction.invisibleTypeAnnotations),"No instruction annotation contracts");
        }
    }
    private static void field(FieldNode node,int access,String signature) {
        require(node.access == access && (node.signature == null || Objects.equals(node.signature,signature))
            && empty(node.visibleAnnotations) && empty(node.invisibleAnnotations) && empty(node.visibleTypeAnnotations)
            && empty(node.invisibleTypeAnnotations) && empty(node.attrs),"Exact field access and optional generic signature");
    }
    private static void innerMetadata(ClassNode node,Map<String,Integer> allowed) {
        if (node.innerClasses == null) return;
        var seen = new HashSet<String>();
        for (var inner : node.innerClasses) {
            require(inner != null && inner.name != null && seen.add(inner.name) && allowed.containsKey(inner.name),"Known unique native inner metadata only");
            int split = inner.name.lastIndexOf('$');
            require(inner.name.substring(0,split).equals(inner.outerName) && inner.name.substring(split+1).equals(inner.innerName)
                && inner.access == allowed.get(inner.name),"Exact native inner metadata access");
        }
    }
    private static void branch(MethodNode method,List<AbstractInsnNode> body,int offset,int opcode,int destination) {
        require(body.get(offset) instanceof JumpInsnNode jump && jump.getOpcode()==opcode,"Exact slot comparison branch opcode");
        var jump = (JumpInsnNode)body.get(offset); var next = (AbstractInsnNode)jump.label;
        require(next != null && method.instructions.indexOf(next)>=0,"Owned slot branch label");
        while (next != null && next.getOpcode()<0) next=next.getNext();
        require(next == body.get(destination),"Exact slot branch target");
    }
    private static String generic(String owner,String parameter) { return "L"+owner+"<L"+parameter+";>;"; }
    private static void require(boolean accepted,String message) {
        if (!accepted) throw new TransformationException("ITEM_ATTRIBUTES_UNSUPPORTED: "+message);
    }
    private static final class Names {
        final String item,properties,reference,attribute,attributes,modifier,operation,slot,endpoint;
        final String damageAttribute,speedAttribute,damageUuid,speedUuid;
        Names(boolean target) {
            item=target?TARGET_ITEM:OLD_ITEM; properties=item+"$Properties";
            reference=target?"net/minecraftforge/registries/RegistryObject":"net/minecraftforge/fml/RegistryObject";
            String prefix=target?"net/minecraft/world/entity/ai/attributes/":"net/minecraft/entity/ai/attributes/";
            attribute=prefix+"Attribute"; attributes=prefix+"Attributes"; modifier=prefix+"AttributeModifier"; operation=modifier+"$Operation";
            slot=target?"net/minecraft/world/entity/EquipmentSlot":"net/minecraft/inventory/EquipmentSlotType";
            endpoint=target?"m_7167_":"func_111205_h";
            damageAttribute=target?"f_22281_":"field_233823_f_"; speedAttribute=target?"f_22283_":"field_233825_h_";
            damageUuid=target?"f_41374_":"field_111210_e"; speedUuid=target?"f_41375_":"field_185050_h";
        }
        String mapSignature() { return "L"+MULTIMAP+"<L"+attribute+";L"+modifier+";>;"; }
    }
}
