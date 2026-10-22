package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.transformation.TransformationException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Inert structural trees and archive reads only; native item/Guava classes are never executed. */
class ItemAttributeContractTest {
    private static final String ENTRY="example/AttributeEntry", ITEM="example/AttributeItem", MOD="attribute_fixture";
    private static final String DEFERRED="net/minecraftforge/registries/DeferredRegister", REGISTRY="net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRIES="net/minecraftforge/registries/ForgeRegistries", CONTEXT="net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS="net/minecraftforge/eventbus/api/IEventBus", MULTIMAP="com/google/common/collect/Multimap";
    private static final String IMMUTABLE="com/google/common/collect/ImmutableMultimap", BUILDER=IMMUTABLE+"$Builder";
    private static final Handle METAFACTORY=new Handle(H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",false);

    @Test void independentlyProvesSourceAndTargetWithoutModifyingEitherGraph() {
        var source=fixture(false); var target=fixture(true);
        var beforeSource=snapshot(source);var beforeTarget=snapshot(target);
        var proof=ItemAttributeContract.inspect(source);
        assertEquals(proof,ItemAttributeContract.inspect(target));
        assertEquals(ENTRY,proof.entry());assertEquals(ITEM,proof.item());assertEquals(MOD,proof.modId());
        assertEquals("attribute_item",proof.registryName());assertEquals("createItem",proof.factory());
        assertEquals("MODIFIERS",proof.modifiersField());
        assertEquals("Damage label",proof.damageName());assertEquals("Speed label",proof.speedName());
        sameBytes(beforeSource,source);sameBytes(beforeTarget,target);
        var tree=proof.toTree();assertEquals("NOT_RUN",tree.get("runtimeValidation"));assertEquals(false,tree.get("classesExecuted"));
        assertEquals("REQUIRE_SEPARATE_VERIFICATION",tree.get("externalDeclarations"));
        assertEquals("MAINHAND",tree.get("slot"));assertEquals("EMPTY_IMMUTABLE_MULTIMAP",tree.get("otherSlots"));
        assertFalse(tree.containsKey("combatValidated"));assertFalse(tree.containsKey("candidate"));
        assertThrows(UnsupportedOperationException.class,tree::clear);
        assertThrows(UnsupportedOperationException.class,()->((List<?>)tree.get("modifiers")).clear());
        assertThrows(UnsupportedOperationException.class,()->((Map<?,?>)((List<?>)tree.get("modifiers")).get(0)).clear());
    }

    @Test void inventoryViewCannotEraseTheSourceOrInventAConvertedBody() {
        var source=fixture(false);var target=fixture(true);var before=snapshot(target);
        assertThrows(TransformationException.class,()->ItemAttributeContract.validate(source,MOD,"1.0"));
        var plan=ItemAttributeContract.validate(target,MOD,"1.0");
        assertEquals(MOD,plan.modId());assertEquals("1.0",plan.modVersion());assertEquals(1,plan.items().size());
        var item=plan.items().get(0);assertEquals("attribute_item",item.name());assertNull(item.block());
        assertEquals(64,item.properties().stackSize());assertEquals(0,item.properties().durability());
        assertNull(item.properties().creativeTab());assertFalse(item.properties().fireResistant());
        assertTrue(plan.blocks().isEmpty());assertTrue(plan.sounds().isEmpty());sameBytes(before,target);
        assertThrows(TransformationException.class,()->ItemAttributeContract.validate(target,"other","1.0"));
        for(String version:new String[]{null,"", "../1", "v".repeat(129)})
            assertThrows(TransformationException.class,()->ItemAttributeContract.validate(target,MOD,version));
    }

    @Test void ownedClassFieldFactoryAndLiteralLabelsAreRolesNotFixtureFingerprints() {
        for(boolean target:new boolean[]{false,true}) {
            var nodes=fixture(target); ((LdcInsnNode)op(nodes,ITEM,"<clinit>",5)).cst="Different printable damage";
            ((LdcInsnNode)op(nodes,ITEM,"<clinit>",14)).cst="Another printable speed";
            var remapper=new Remapper(){
                @Override public String map(String name){return name.equals(ENTRY)?"renamed/Entry":name.equals(ITEM)?"renamed/Item":name;}
                @Override public String mapFieldName(String owner,String name,String desc){
                    return owner.equals(ENTRY)||owner.equals(ITEM)&&name.equals("MODIFIERS")?"role_"+name:name;
                }
                @Override public String mapMethodName(String owner,String name,String desc){return owner.equals(ENTRY)&&name.equals("createItem")?"factory":name;}
            };
            var renamed=new TreeMap<String,ClassNode>();
            nodes.values().forEach(n->{var copy=new ClassNode();n.accept(new ClassRemapper(copy,remapper));renamed.put(copy.name,copy);});
            var proof=ItemAttributeContract.inspect(renamed);
            assertEquals("renamed/Item",proof.item());assertEquals("renamed/Entry",proof.entry());
            assertEquals("role_MODIFIERS",proof.modifiersField());assertEquals("factory",proof.factory());
            assertEquals("Different printable damage",proof.damageName());assertEquals("Another printable speed",proof.speedName());
        }
    }

    @Test void debugMetadataOrderingAndOmittedOptionalGenericsArePreserved() {
        for(boolean target:new boolean[]{false,true}) {
            var nodes=fixture(target);
            for(var n:nodes.values()) {
                n.sourceFile="Original.java";n.sourceDebug="SMAP\nOriginal.java\n";n.innerClasses.clear();
                for(var f:n.fields)f.signature=null;
                for(var m:n.methods) {
                    m.signature=null;var start=new LabelNode();var end=new LabelNode();
                    m.instructions.insert(start);m.instructions.insert(start,new LineNumberNode(23,start));m.instructions.add(end);
                    m.localVariables.add(new LocalVariableNode("debug","Ljava/lang/Object;",null,start,end,0));
                }
                Collections.reverse(n.fields);Collections.reverse(n.methods);
            }
            var before=snapshot(nodes);ItemAttributeContract.inspect(nodes);sameBytes(before,nodes);
        }
    }

    @Test void graphOwnershipAndCancellationAreCheckedBeforeAdmission() {
        assertThrows(TransformationException.class,()->ItemAttributeContract.inspect(null));
        assertThrows(TransformationException.class,()->ItemAttributeContract.inspect(Map.of()));
        assertThrows(TransformationException.class,()->ItemAttributeContract.inspect(Map.of(ENTRY,fixture(false).get(ENTRY))));
        var extra=fixture(false);extra.put("extra/Helper",new ClassNode());
        assertThrows(TransformationException.class,()->ItemAttributeContract.inspect(extra));
        var nullClass=fixture(false);nullClass.put(ITEM,null);
        assertThrows(TransformationException.class,()->ItemAttributeContract.inspect(nullClass));
        var wrong=fixture(false);wrong.put("wrong/Key",wrong.remove(ITEM));
        assertThrows(TransformationException.class,()->ItemAttributeContract.inspect(wrong));
        var protectedClass=fixture(false);var item=protectedClass.remove(ITEM);item.name="net/minecraft/OwnedFake";protectedClass.put(item.name,item);
        assertThrows(TransformationException.class,()->ItemAttributeContract.inspect(protectedClass));
        var control=new OperationControl(OperationLimits.defaults(),ignored->{});assertTrue(control.requestCancel());
        try(var ignored=control.enter()){assertThrows(OperationStoppedException.class,()->ItemAttributeContract.inspect(fixture(false)));}
    }

    @Test
    @EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
    void independentlyBuiltOriginalMatchesWithoutExecutingIt()throws Exception {
        var nodes=new TreeMap<String,ClassNode>();
        Path path=Path.of(System.getProperty("legacybridge.fixtureRoot"),"item-attributes-1.16.5/build/libs/legacybridge-item-attributes-1.16.5-1.0.0.jar");
        try(var zip=new ZipFile(path.toFile())) {
            for(var entries=zip.entries();entries.hasMoreElements();) {
                var e=entries.nextElement();if(e.getName().endsWith(".class"))try(var stream=zip.getInputStream(e)) {
                    var n=new ClassNode();new ClassReader(stream.readAllBytes()).accept(n,0);nodes.put(n.name,n);
                }
            }
        }
        var before=snapshot(nodes);var proof=ItemAttributeContract.inspect(nodes);sameBytes(before,nodes);
        assertEquals("legacybridge_attributes",proof.modId());assertEquals("bridge_attribute_item",proof.registryName());
    }

    @TestFactory Stream<DynamicTest> unreviewedMetadataStructureAndEffectsFailClosedInBothNamespaces() {
        var changes=new LinkedHashMap<String,Consumer<Map<String,ClassNode>>>();
        for(String owner:List.of(ENTRY,ITEM)) {
            String prefix=owner.equals(ENTRY)?"entry ":"item ";
            changes.put(prefix+"nonfinal",n->n.get(owner).access&=~ACC_FINAL);
            changes.put(prefix+"interface",n->n.get(owner).interfaces.add("unknown/Hook"));
            changes.put(prefix+"version",n->n.get(owner).version=V17);
            changes.put(prefix+"class signature",n->n.get(owner).signature="Lunknown/Base;");
            changes.put(prefix+"outer metadata",n->n.get(owner).outerClass="unknown/Outer");
            changes.put(prefix+"nest metadata",n->n.get(owner).nestHostClass="unknown/Host");
            changes.put(prefix+"record metadata",n->n.get(owner).recordComponents=new ArrayList<>(List.of(new RecordComponentNode("value","I",null))));
            changes.put(prefix+"unknown inner",n->n.get(owner).innerClasses.add(new InnerClassNode("unknown/Other$Nested","unknown/Other","Nested",ACC_PUBLIC)));
            changes.put(prefix+"wrong inner access",n->n.get(owner).innerClasses.get(0).access=ACC_PRIVATE);
            changes.put(prefix+"unknown annotation",n->n.get(owner).invisibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
            changes.put(prefix+"extra method",n->add(n.get(owner),ACC_PUBLIC,"other","()V").instructions.add(new InsnNode(RETURN)));
            changes.put(prefix+"extra field",n->n.get(owner).fields.add(new FieldNode(ACC_PRIVATE,"counter","I",null,null)));
            changes.put(prefix+"null field",n->n.get(owner).fields.set(0,null));
            changes.put(prefix+"null method",n->n.get(owner).methods.set(0,null));
            changes.put(prefix+"generic constructor",n->method(n,owner,"<init>").signature="(Lunknown/Generic;)V");
            changes.put(prefix+"exception handler",n->method(n,owner,"<init>").tryCatchBlocks.add(new TryCatchBlockNode(new LabelNode(),new LabelNode(),new LabelNode(),"java/lang/Exception")));
            changes.put(prefix+"exception declaration",n->method(n,owner,"<init>").exceptions.add("java/lang/Exception"));
            changes.put(prefix+"method annotations",n->method(n,owner,"<init>").visibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
            changes.put(prefix+"oversized stack",n->method(n,owner,"<init>").maxStack=65535);
            changes.put(prefix+"oversized locals",n->method(n,owner,"<init>").maxLocals=65);
            changes.put(prefix+"negative locals",n->method(n,owner,"<clinit>").maxLocals=-1);
            changes.put(prefix+"unknown clinit effect",n->method(n,owner,"<clinit>").instructions.insert(new InsnNode(NOP)));
        }
        changes.put("sword inherited behavior",n->n.get(ITEM).superName="net/minecraft/item/SwordItem");
        changes.put("indirect inherited behavior",n->n.get(ITEM).superName="unknown/CustomItem");
        changes.put("wrong source target pair",n->n.get(ITEM).superName=n.get(ITEM).superName.equals("net/minecraft/item/Item")?"net/minecraft/world/item/Item":"net/minecraft/item/Item");
        changes.put("missing Mod annotation",n->n.get(ENTRY).visibleAnnotations.clear());
        changes.put("different Mod identity",n->n.get(ENTRY).visibleAnnotations.get(0).values=new ArrayList<>(List.of("value","other")));
        changes.put("unknown Mod values",n->n.get(ENTRY).visibleAnnotations.get(0).values.addAll(List.of("other","hook")));
        changes.put("nonconstant identity",n->field(n,ENTRY,"MOD_ID").value=null);
        changes.put("constant registry",n->field(n,ENTRY,"ITEMS").value=1);
        changes.put("mutable registry",n->field(n,ENTRY,"ITEMS").access&=~ACC_FINAL);
        changes.put("registry generic drift",n->field(n,ENTRY,"ITEMS").signature="Ljava/util/List<Ljava/lang/String;>;" );
        changes.put("duplicate field role",n->field(n,ENTRY,"ITEMS").name="MOD_ID");
        changes.put("early registry get",n->method(n,ENTRY,"<clinit>").instructions.insert(new MethodInsnNode(INVOKEVIRTUAL,"unknown/RegistryObject","get","()Ljava/lang/Object;",false)));
        changes.put("wrong registration kind",n->((FieldInsnNode)op(n,ENTRY,"<clinit>",0)).name="BLOCKS");
        changes.put("wrong registry path",n->((LdcInsnNode)op(n,ENTRY,"<clinit>",5)).cst="../item");
        changes.put("wrong Mod bus",n->((MethodInsnNode)op(n,ENTRY,"<init>",4)).name="getForgeEventBus");
        changes.put("wrong registry attach opcode",n->((MethodInsnNode)op(n,ENTRY,"<init>",5)).setOpcode(INVOKESTATIC));
        changes.put("factory public",n->method(n,ENTRY,"createItem").access=ACC_PUBLIC|ACC_STATIC);
        changes.put("factory return custom type",n->method(n,ENTRY,"createItem").desc="()L"+ITEM+";");
        changes.put("factory foreign construction",n->((TypeInsnNode)op(n,ENTRY,"createItem",0)).desc="unknown/Item");
        changes.put("factory property customization",n->method(n,ENTRY,"createItem").instructions.insertBefore(op(n,ENTRY,"createItem",5),new InsnNode(NOP)));
        changes.put("factory property ctor opcode",n->((MethodInsnNode)op(n,ENTRY,"createItem",4)).setOpcode(INVOKESTATIC));
        changes.put("factory constructor wrong owner",n->((MethodInsnNode)op(n,ENTRY,"createItem",5)).owner="unknown/Item");
        changes.put("factory constructor interface bit",n->((MethodInsnNode)op(n,ENTRY,"createItem",5)).itf=true);
        changes.put("captured supplier",n->supplier(n).desc="(Ljava/lang/Object;)Ljava/util/function/Supplier;");
        changes.put("foreign supplier",n->supplier(n).bsmArgs[1]=new Handle(H_INVOKESTATIC,"unknown/Factory","createItem",method(n,ENTRY,"createItem").desc,false));
        changes.put("wrong supplier factory",n->supplier(n).bsmArgs[1]=new Handle(H_INVOKESTATIC,ENTRY,"other",method(n,ENTRY,"createItem").desc,false));
        changes.put("alternate metafactory",n->supplier(n).bsm=new Handle(H_INVOKESTATIC,METAFACTORY.getOwner(),"altMetafactory",METAFACTORY.getDesc(),false));
        changes.put("retained builder",n->field(n,ITEM,"MODIFIERS").desc="L"+BUILDER+";");
        changes.put("instance map state",n->field(n,ITEM,"MODIFIERS").access&=~ACC_STATIC);
        changes.put("mutable map reference",n->field(n,ITEM,"MODIFIERS").access&=~ACC_FINAL);
        changes.put("externally mutable role",n->field(n,ITEM,"MODIFIERS").access=ACC_PUBLIC|ACC_STATIC|ACC_FINAL);
        changes.put("wrong map generic",n->field(n,ITEM,"MODIFIERS").signature="L"+MULTIMAP+"<Ljava/lang/String;Ljava/lang/Object;>;" );
        changes.put("map field annotation",n->field(n,ITEM,"MODIFIERS").visibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        changes.put("constant map",n->field(n,ITEM,"MODIFIERS").value=1);
        changes.put("wrong super constructor",n->((MethodInsnNode)op(n,ITEM,"<init>",2)).owner="unknown/Item");
        changes.put("wrong super argument",n->((VarInsnNode)op(n,ITEM,"<init>",1)).var=0);
        changes.put("static super invocation",n->((MethodInsnNode)op(n,ITEM,"<init>",2)).setOpcode(INVOKESTATIC));
        changes.put("mutable collection",n->((MethodInsnNode)op(n,ITEM,"<clinit>",0)).owner="com/google/common/collect/HashMultimap");
        changes.put("builder static opcode",n->((MethodInsnNode)op(n,ITEM,"<clinit>",0)).setOpcode(INVOKEVIRTUAL));
        changes.put("wrong attribute",n->((FieldInsnNode)op(n,ITEM,"<clinit>",1)).name="unknownAttribute");
        changes.put("duplicated attribute",n->((FieldInsnNode)op(n,ITEM,"<clinit>",10)).name=((FieldInsnNode)op(n,ITEM,"<clinit>",1)).name);
        changes.put("wrong attribute owner",n->((FieldInsnNode)op(n,ITEM,"<clinit>",1)).owner="unknown/Attributes");
        changes.put("custom modifier",n->((TypeInsnNode)op(n,ITEM,"<clinit>",2)).desc="unknown/Modifier");
        changes.put("random UUID",n->replace(n,ITEM,"<clinit>",4,new MethodInsnNode(INVOKESTATIC,"java/util/UUID","randomUUID","()Ljava/util/UUID;",false)));
        changes.put("recreated UUID",n->replace(n,ITEM,"<clinit>",4,new MethodInsnNode(INVOKESTATIC,"java/util/UUID","fromString","(Ljava/lang/String;)Ljava/util/UUID;",false)));
        changes.put("foreign UUID owner",n->((FieldInsnNode)op(n,ITEM,"<clinit>",4)).owner="unknown/Constants");
        changes.put("native owner instead of audited owned alias",n->((FieldInsnNode)op(n,ITEM,"<clinit>",4)).owner=n.get(ITEM).superName);
        changes.put("instance UUID read",n->((FieldInsnNode)op(n,ITEM,"<clinit>",4)).setOpcode(GETFIELD));
        changes.put("duplicated UUID",n->((FieldInsnNode)op(n,ITEM,"<clinit>",13)).name=((FieldInsnNode)op(n,ITEM,"<clinit>",4)).name);
        changes.put("swapped UUIDs",n->{var first=(FieldInsnNode)op(n,ITEM,"<clinit>",4);var second=(FieldInsnNode)op(n,ITEM,"<clinit>",13);String name=first.name;first.name=second.name;second.name=name;});
        changes.put("modifier name callback",n->replace(n,ITEM,"<clinit>",5,new MethodInsnNode(INVOKESTATIC,"unknown/Names","name","()Ljava/lang/String;",false)));
        for(String label:new String[]{"", " ", "bad\nname", "format\u202E", "isolated\uD800", "x".repeat(129)})
            changes.put("unsafe modifier name "+label.hashCode(),n->((LdcInsnNode)op(n,ITEM,"<clinit>",5)).cst=label);
        for(Object number:List.of(2.0F,"2.0",2,Double.NaN,Double.POSITIVE_INFINITY,Math.nextUp(2.0D),-0.0D))
            changes.put("wrong damage literal "+number.getClass().getSimpleName()+number,n->((LdcInsnNode)op(n,ITEM,"<clinit>",6)).cst=number);
        changes.put("wrong speed literal",n->((LdcInsnNode)op(n,ITEM,"<clinit>",15)).cst=-2.0D);
        changes.put("multiply operation",n->((FieldInsnNode)op(n,ITEM,"<clinit>",7)).name="MULTIPLY_TOTAL");
        changes.put("Supplier modifier ctor",n->((MethodInsnNode)op(n,ITEM,"<clinit>",8)).desc="(Ljava/util/UUID;Ljava/util/function/Supplier;DLunknown/Operation;)V");
        changes.put("modifier ctor opcode",n->((MethodInsnNode)op(n,ITEM,"<clinit>",8)).setOpcode(INVOKEVIRTUAL));
        changes.put("builder put boolean overload",n->((MethodInsnNode)op(n,ITEM,"<clinit>",9)).desc="(Ljava/lang/Object;Ljava/lang/Object;)Z");
        changes.put("builder put interface bit",n->((MethodInsnNode)op(n,ITEM,"<clinit>",9)).itf=true);
        changes.put("extra builder call",n->method(n,ITEM,"<clinit>").instructions.insertBefore(op(n,ITEM,"<clinit>",19),new MethodInsnNode(INVOKEVIRTUAL,BUILDER,"orderKeysBy","(Ljava/util/Comparator;)L"+BUILDER+";",false)));
        changes.put("unbuilt map",n->((MethodInsnNode)op(n,ITEM,"<clinit>",19)).name="toString");
        changes.put("foreign map assignment",n->((FieldInsnNode)op(n,ITEM,"<clinit>",20)).owner=ENTRY);
        changes.put("endpoint private",n->endpoint(n).access=ACC_PRIVATE);
        changes.put("endpoint static",n->endpoint(n).access=ACC_PUBLIC|ACC_STATIC);
        changes.put("endpoint generic drift",n->endpoint(n).signature="(Ljava/lang/Object;)Ljava/lang/Object;");
        changes.put("endpoint argument drift",n->((VarInsnNode)real(endpoint(n)).get(0)).var=0);
        changes.put("wrong slot",n->((FieldInsnNode)real(endpoint(n)).get(1)).name="OFFHAND");
        changes.put("reversed slot branch",n->((JumpInsnNode)real(endpoint(n)).get(2)).setOpcode(IF_ACMPEQ));
        changes.put("foreign branch label",n->((JumpInsnNode)real(endpoint(n)).get(2)).label=new LabelNode());
        changes.put("wrong branch target",n->((JumpInsnNode)real(endpoint(n)).get(2)).label=((JumpInsnNode)real(endpoint(n)).get(4)).label);
        changes.put("foreign map receiver",n->((FieldInsnNode)real(endpoint(n)).get(3)).owner=ENTRY);
        changes.put("wrong map role",n->((FieldInsnNode)real(endpoint(n)).get(3)).name="otherMap");
        changes.put("mutating fallback",n->((MethodInsnNode)real(endpoint(n)).get(5)).owner="com/google/common/collect/HashMultimap");
        changes.put("null fallback",n->endpoint(n).instructions.set(real(endpoint(n)).get(5),new InsnNode(ACONST_NULL)));
        return changes.entrySet().stream().flatMap(e->Stream.of(false,true).map(target->DynamicTest.dynamicTest(
            (target?"target ":"source ")+e.getKey(),()->{
                var nodes=fixture(target);e.getValue().accept(nodes);
                var error=assertThrows(TransformationException.class,()->ItemAttributeContract.inspect(nodes));
                assertTrue(error.getMessage().startsWith("ITEM_ATTRIBUTES_UNSUPPORTED:"),error.getMessage());
            })));
    }

    private static Map<String,ClassNode> fixture(boolean target) {
        var api=new Api(target);var entry=node(ENTRY,"java/lang/Object");var item=node(ITEM,api.item);
        var annotation=new AnnotationNode("Lnet/minecraftforge/fml/common/Mod;");annotation.values=new ArrayList<>(List.of("value",MOD));
        entry.visibleAnnotations=new ArrayList<>(List.of(annotation));
        entry.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"MOD_ID","Ljava/lang/String;",null,MOD));
        entry.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"ITEMS","L"+DEFERRED+";","L"+DEFERRED+"<L"+api.item+";>;",null));
        entry.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"ATTRIBUTE_ITEM","L"+api.reference+";","L"+api.reference+"<L"+api.item+";>;",null));
        entry.innerClasses.add(new InnerClassNode(api.props,api.item,"Properties",ACC_PUBLIC|ACC_STATIC));
        entry.innerClasses.add(new InnerClassNode("java/lang/invoke/MethodHandles$Lookup","java/lang/invoke/MethodHandles","Lookup",ACC_PUBLIC|ACC_STATIC|ACC_FINAL));
        var ctor=add(entry,ACC_PUBLIC,"<init>","()V");ctor.instructions.add(new VarInsnNode(ALOAD,0));
        call(ctor,INVOKESPECIAL,"java/lang/Object","<init>","()V");get(ctor,ENTRY,"ITEMS","L"+DEFERRED+";");
        call(ctor,INVOKESTATIC,CONTEXT,"get","()L"+CONTEXT+";");call(ctor,INVOKEVIRTUAL,CONTEXT,"getModEventBus","()L"+BUS+";");
        call(ctor,INVOKEVIRTUAL,DEFERRED,"register","(L"+BUS+";)V");ctor.instructions.add(new InsnNode(RETURN));
        var factory=add(entry,ACC_PRIVATE|ACC_STATIC,"createItem","()L"+api.item+";");
        factory.instructions.add(new TypeInsnNode(NEW,ITEM));factory.instructions.add(new InsnNode(DUP));
        factory.instructions.add(new TypeInsnNode(NEW,api.props));factory.instructions.add(new InsnNode(DUP));
        call(factory,INVOKESPECIAL,api.props,"<init>","()V");call(factory,INVOKESPECIAL,ITEM,"<init>","(L"+api.props+";)V");factory.instructions.add(new InsnNode(ARETURN));
        var init=add(entry,ACC_STATIC,"<clinit>","()V");get(init,REGISTRIES,"ITEMS","L"+REGISTRY+";");init.instructions.add(new LdcInsnNode(MOD));
        call(init,INVOKESTATIC,DEFERRED,"create","(L"+REGISTRY+";Ljava/lang/String;)L"+DEFERRED+";");
        init.instructions.add(new FieldInsnNode(PUTSTATIC,ENTRY,"ITEMS","L"+DEFERRED+";"));get(init,ENTRY,"ITEMS","L"+DEFERRED+";");init.instructions.add(new LdcInsnNode("attribute_item"));
        init.instructions.add(new InvokeDynamicInsnNode("get","()Ljava/util/function/Supplier;",METAFACTORY,Type.getMethodType("()Ljava/lang/Object;"),
            new Handle(H_INVOKESTATIC,ENTRY,"createItem",factory.desc,false),Type.getMethodType(factory.desc)));
        call(init,INVOKEVIRTUAL,DEFERRED,"register","(Ljava/lang/String;Ljava/util/function/Supplier;)L"+api.reference+";");
        init.instructions.add(new FieldInsnNode(PUTSTATIC,ENTRY,"ATTRIBUTE_ITEM","L"+api.reference+";"));init.instructions.add(new InsnNode(RETURN));
        String mapSignature="L"+MULTIMAP+"<L"+api.attribute+";L"+api.modifier+";>;";
        item.fields.add(new FieldNode(ACC_PRIVATE|ACC_STATIC|ACC_FINAL,"MODIFIERS","L"+MULTIMAP+";",mapSignature,null));
        item.innerClasses.add(new InnerClassNode(api.props,api.item,"Properties",ACC_PUBLIC|ACC_STATIC));
        item.innerClasses.add(new InnerClassNode(BUILDER,IMMUTABLE,"Builder",ACC_PUBLIC|ACC_STATIC));
        item.innerClasses.add(new InnerClassNode(api.operation,api.modifier,"Operation",ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_ENUM));
        var itemCtor=add(item,ACC_PUBLIC,"<init>","(L"+api.props+";)V");itemCtor.instructions.add(new VarInsnNode(ALOAD,0));itemCtor.instructions.add(new VarInsnNode(ALOAD,1));
        call(itemCtor,INVOKESPECIAL,api.item,"<init>","(L"+api.props+";)V");itemCtor.instructions.add(new InsnNode(RETURN));
        var itemInit=add(item,ACC_STATIC,"<clinit>","()V");call(itemInit,INVOKESTATIC,IMMUTABLE,"builder","()L"+BUILDER+";");
        addModifier(itemInit,api,api.damageAttribute,api.damageUuid,"Damage label",2.0D);
        addModifier(itemInit,api,api.speedAttribute,api.speedUuid,"Speed label",-1.0D);
        call(itemInit,INVOKEVIRTUAL,BUILDER,"build","()L"+IMMUTABLE+";");itemInit.instructions.add(new FieldInsnNode(PUTSTATIC,ITEM,"MODIFIERS","L"+MULTIMAP+";"));itemInit.instructions.add(new InsnNode(RETURN));
        var endpoint=add(item,ACC_PUBLIC,api.endpoint,"(L"+api.slot+";)L"+MULTIMAP+";");endpoint.signature="(L"+api.slot+";)"+mapSignature;
        var fallback=new LabelNode();var end=new LabelNode();endpoint.instructions.add(new VarInsnNode(ALOAD,1));get(endpoint,api.slot,"MAINHAND","L"+api.slot+";");
        endpoint.instructions.add(new JumpInsnNode(IF_ACMPNE,fallback));get(endpoint,ITEM,"MODIFIERS","L"+MULTIMAP+";");endpoint.instructions.add(new JumpInsnNode(GOTO,end));
        endpoint.instructions.add(fallback);call(endpoint,INVOKESTATIC,IMMUTABLE,"of","()L"+IMMUTABLE+";");endpoint.instructions.add(end);endpoint.instructions.add(new InsnNode(ARETURN));
        var nodes=new TreeMap<String,ClassNode>();nodes.put(ENTRY,entry);nodes.put(ITEM,item);return nodes;
    }
    private static void addModifier(MethodNode m,Api api,String attribute,String uuid,String label,double amount) {
        get(m,api.attributes,attribute,"L"+api.attribute+";");m.instructions.add(new TypeInsnNode(NEW,api.modifier));m.instructions.add(new InsnNode(DUP));
        get(m,ITEM,uuid,"Ljava/util/UUID;");m.instructions.add(new LdcInsnNode(label));m.instructions.add(new LdcInsnNode(amount));get(m,api.operation,"ADDITION","L"+api.operation+";");
        call(m,INVOKESPECIAL,api.modifier,"<init>","(Ljava/util/UUID;Ljava/lang/String;DL"+api.operation+";)V");call(m,INVOKEVIRTUAL,BUILDER,"put","(Ljava/lang/Object;Ljava/lang/Object;)L"+BUILDER+";");
    }
    private static final class Api {
        final String item,props,reference,attribute,attributes,modifier,operation,slot,endpoint,damageAttribute,speedAttribute,damageUuid,speedUuid;
        Api(boolean target) {
            item=target?"net/minecraft/world/item/Item":"net/minecraft/item/Item";props=item+"$Properties";
            reference=target?"net/minecraftforge/registries/RegistryObject":"net/minecraftforge/fml/RegistryObject";
            String prefix=target?"net/minecraft/world/entity/ai/attributes/":"net/minecraft/entity/ai/attributes/";
            attribute=prefix+"Attribute";attributes=prefix+"Attributes";modifier=prefix+"AttributeModifier";operation=modifier+"$Operation";
            slot=target?"net/minecraft/world/entity/EquipmentSlot":"net/minecraft/inventory/EquipmentSlotType";endpoint=target?"m_7167_":"func_111205_h";
            damageAttribute=target?"f_22281_":"field_233823_f_";speedAttribute=target?"f_22283_":"field_233825_h_";
            damageUuid=target?"f_41374_":"field_111210_e";speedUuid=target?"f_41375_":"field_185050_h";
        }
    }
    private static ClassNode node(String name,String parent){var n=new ClassNode();n.version=V1_8;n.name=name;n.superName=parent;n.access=ACC_PUBLIC|ACC_FINAL|ACC_SUPER;return n;}
    private static MethodNode add(ClassNode n,int access,String name,String desc){var m=new MethodNode(access,name,desc,null,null);m.maxStack=12;m.maxLocals=2;n.methods.add(m);return m;}
    private static void call(MethodNode m,int opcode,String owner,String name,String desc){m.instructions.add(new MethodInsnNode(opcode,owner,name,desc,false));}
    private static void get(MethodNode m,String owner,String name,String desc){m.instructions.add(new FieldInsnNode(GETSTATIC,owner,name,desc));}
    private static MethodNode method(Map<String,ClassNode> nodes,String owner,String name){return nodes.get(owner).methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static FieldNode field(Map<String,ClassNode> nodes,String owner,String name){return nodes.get(owner).fields.stream().filter(f->f.name.equals(name)).findFirst().orElseThrow();}
    private static MethodNode endpoint(Map<String,ClassNode> n){return n.get(ITEM).methods.stream().filter(m->!m.name.startsWith("<")).findFirst().orElseThrow();}
    private static List<AbstractInsnNode> real(MethodNode m){return ClientConfigContract.code(m);}
    private static AbstractInsnNode op(Map<String,ClassNode> nodes,String owner,String method,int offset){return real(method(nodes,owner,method)).get(offset);}
    private static void replace(Map<String,ClassNode> nodes,String owner,String method,int offset,AbstractInsnNode node){method(nodes,owner,method).instructions.set(op(nodes,owner,method,offset),node);}
    private static InvokeDynamicInsnNode supplier(Map<String,ClassNode> nodes){return (InvokeDynamicInsnNode)op(nodes,ENTRY,"<clinit>",6);}
    private static Map<String,byte[]> snapshot(Map<String,ClassNode> nodes){var bytes=new TreeMap<String,byte[]>();nodes.forEach((name,node)->{var w=new ClassWriter(0);node.accept(w);bytes.put(name,w.toByteArray());});return bytes;}
    private static void sameBytes(Map<String,byte[]> before,Map<String,ClassNode> after){var bytes=snapshot(after);assertEquals(before.keySet(),bytes.keySet());before.forEach((name,value)->assertArrayEquals(value,bytes.get(name),name));}
}
