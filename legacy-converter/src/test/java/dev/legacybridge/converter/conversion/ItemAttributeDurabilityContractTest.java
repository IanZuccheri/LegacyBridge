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

/** No native classes execute: this checks the whole original before a private deep admission view. */
class ItemAttributeDurabilityContractTest {
    private static final String ENTRY="example/AttributeEntry", ITEM="example/AttributeItem", MOD="attribute_fixture";
    private static final String DEFERRED="net/minecraftforge/registries/DeferredRegister", REGISTRY="net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRIES="net/minecraftforge/registries/ForgeRegistries", CONTEXT="net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS="net/minecraftforge/eventbus/api/IEventBus", MULTIMAP="com/google/common/collect/Multimap";
    private static final String IMMUTABLE="com/google/common/collect/ImmutableMultimap", BUILDER=IMMUTABLE+"$Builder";
    private static final Handle METAFACTORY=new Handle(H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",false);

    @Test void sourceAndTargetCompositionMatchWithoutErasingAnOriginalBody() {
        var source=fixture(false);var target=fixture(true);
        var originalSource=snapshot(source);var originalTarget=snapshot(target);
        var proof=ItemAttributeDurabilityContract.inspect(source);
        assertEquals(proof,ItemAttributeDurabilityContract.inspect(target));
        assertEquals(128,proof.durability());assertEquals(ENTRY,proof.attributeProof().entry());
        assertEquals(ITEM,proof.attributeProof().item());assertEquals("createItem",proof.attributeProof().factory());
        assertEquals(7,source.values().stream().mapToInt(n->n.methods.size()).sum());
        assertEquals(9,real(method(source,ENTRY,"createItem")).size());assertEquals(8,real(repair(source)).size());
        sameBytes(originalSource,source);sameBytes(originalTarget,target);
        var tree=proof.toTree();assertEquals("NOT_RUN",tree.get("runtimeValidation"));assertEquals(false,tree.get("classesExecuted"));
        assertEquals(1,tree.get("maxStackSize"));assertEquals("minecraft:iron_ingot",tree.get("repairIngredient"));
        assertEquals("NOT_IMPLEMENTED_BY_THIS_PROGRAM",tree.get("wearOnHitOrMining"));
        assertEquals("NOT_QUALIFIED",tree.get("anvilConsumptionAndCost"));
        assertEquals("CHECKED_ADMISSION_VIEW_ONLY; ORIGINAL_HAS_DURABILITY_128",
            ((Map<?,?>)tree.get("attributeProof")).get("properties"));
        assertFalse(tree.containsKey("candidate"));assertThrows(UnsupportedOperationException.class,tree::clear);
        assertThrows(UnsupportedOperationException.class,()->((Map<?,?>)tree.get("attributeProof")).clear());
        assertThrows(IllegalArgumentException.class,()->new ItemAttributeDurabilityContract.Proof(proof.attributeProof(),64));
    }

    @Test void independentOlderContractsDoNotGainImplicitCompositionSupport() {
        for(boolean target:new boolean[]{false,true}) {
            var combined=fixture(target);var before=snapshot(combined);
            assertThrows(TransformationException.class,()->ItemAttributeContract.inspect(combined));
            assertThrows(TransformationException.class,()->DurableItemContract.inspect(combined));
            ItemAttributeDurabilityContract.inspect(combined);sameBytes(before,combined);
            var attributeOnly=attributeOnlyFixture(target);
            ItemAttributeContract.inspect(attributeOnly);
            assertThrows(TransformationException.class,()->ItemAttributeDurabilityContract.inspect(attributeOnly));
        }
    }

    @Test void targetOnlyInventoryPreservesCapacityAndNonstackableDeclaration() {
        var target=fixture(true);var before=snapshot(target);
        var plan=ItemAttributeDurabilityContract.validate(target,MOD,"1.2.3-beta");
        assertEquals(1,plan.items().size());assertTrue(plan.blocks().isEmpty());assertTrue(plan.sounds().isEmpty());
        var properties=plan.items().get(0).properties();
        assertEquals(128,properties.durability());assertEquals(1,properties.stackSize());assertEquals("COMMON",properties.rarity());
        assertNull(properties.creativeTab());assertFalse(properties.fireResistant());assertNull(plan.items().get(0).block());
        sameBytes(before,target);
        assertThrows(TransformationException.class,()->ItemAttributeDurabilityContract.validate(fixture(false),MOD,"1"));
        assertThrows(TransformationException.class,()->ItemAttributeDurabilityContract.validate(target,"other","1"));
        for(String version:new String[]{null,"","../unsafe","x".repeat(129)})
            assertThrows(TransformationException.class,()->ItemAttributeDurabilityContract.validate(target,MOD,version));
    }

    @Test void rolesAndDebugDataRemainOwnedAndUnchanged() {
        for(boolean target:new boolean[]{false,true}) {
            var nodes=fixture(target);
            for(var n:nodes.values()) {
                n.sourceFile="Original.java";n.sourceDebug="SMAP\nOriginal.java\n";
                for(var m:n.methods) {
                    var begin=new LabelNode();var end=new LabelNode();m.instructions.insert(begin);
                    m.instructions.insert(begin,new LineNumberNode(21,begin));m.instructions.add(end);
                    m.localVariables.add(new LocalVariableNode("debug","Ljava/lang/Object;",null,begin,end,0));
                }
                Collections.reverse(n.methods);Collections.reverse(n.fields);
            }
            var remapper=new Remapper(){
                @Override public String map(String name){return name.equals(ENTRY)?"different/Main":name.equals(ITEM)?"different/OwnItem":name;}
                @Override public String mapFieldName(String owner,String name,String desc){
                    return owner.equals(ENTRY)||owner.equals(ITEM)&&name.equals("MODIFIERS")?"role_"+name:name;
                }
                @Override public String mapMethodName(String owner,String name,String desc){return owner.equals(ENTRY)&&name.equals("createItem")?"make":name;}
            };
            var renamed=new TreeMap<String,ClassNode>();nodes.values().forEach(n->{var copy=new ClassNode();n.accept(new ClassRemapper(copy,remapper));renamed.put(copy.name,copy);});
            var before=snapshot(renamed);var proof=ItemAttributeDurabilityContract.inspect(renamed);sameBytes(before,renamed);
            assertEquals("different/Main",proof.attributeProof().entry());assertEquals("different/OwnItem",proof.attributeProof().item());
            assertEquals("make",proof.attributeProof().factory());assertEquals("role_MODIFIERS",proof.attributeProof().modifiersField());
        }
    }

    @Test void boundsCoverEveryOriginalMethodBeforeCopyOrFrameVerification() {
        for(boolean target:new boolean[]{false,true})for(String owner:List.of(ENTRY,ITEM)) {
            for(var method:fixture(target).get(owner).methods)for(int limit:new int[]{-1,65,65535}) {
                var nodes=fixture(target);method(nodes,owner,method.name).maxStack=limit;
                assertThrows(TransformationException.class,()->ItemAttributeDurabilityContract.inspect(nodes));
                var locals=fixture(target);method(locals,owner,method.name).maxLocals=limit;
                assertThrows(TransformationException.class,()->ItemAttributeDurabilityContract.inspect(locals));
            }
        }
    }

    @Test void graphBoundsAndCancellationNeverReturnAProjection() {
        assertThrows(TransformationException.class,()->ItemAttributeDurabilityContract.inspect(null));
        assertThrows(TransformationException.class,()->ItemAttributeDurabilityContract.inspect(Map.of()));
        var extra=fixture(false);extra.put("extra/Helper",new ClassNode());
        assertThrows(TransformationException.class,()->ItemAttributeDurabilityContract.inspect(extra));
        var missing=fixture(false);missing.remove(ITEM);
        assertThrows(TransformationException.class,()->ItemAttributeDurabilityContract.inspect(missing));
        var wrong=fixture(false);wrong.put("wrong/Key",wrong.remove(ITEM));
        assertThrows(TransformationException.class,()->ItemAttributeDurabilityContract.inspect(wrong));
        var nullNode=fixture(false);nullNode.put(ITEM,null);
        assertThrows(TransformationException.class,()->ItemAttributeDurabilityContract.inspect(nullNode));
        var control=new OperationControl(OperationLimits.defaults(),ignored->{});assertTrue(control.requestCancel());
        try(var ignored=control.enter()){assertThrows(OperationStoppedException.class,()->ItemAttributeDurabilityContract.inspect(fixture(false)));}
    }

    @Test
    @EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
    void archivedRealOriginalHasSevenMethodsAndNoClassIsLoaded()throws Exception {
        var nodes=new TreeMap<String,ClassNode>();var path=Path.of(System.getProperty("legacybridge.fixtureRoot"),
            "item-attribute-durability-1.16.5/build/libs/legacybridge-item-attribute-durability-1.16.5-1.0.0.jar");
        try(var zip=new ZipFile(path.toFile())) {
            for(var entries=zip.entries();entries.hasMoreElements();) {
                var e=entries.nextElement();if(e.getName().endsWith(".class"))try(var stream=zip.getInputStream(e)){
                    var node=new ClassNode();new ClassReader(stream.readAllBytes()).accept(node,0);nodes.put(node.name,node);
                }
            }
        }
        var before=snapshot(nodes);var proof=ItemAttributeDurabilityContract.inspect(nodes);sameBytes(before,nodes);
        assertEquals("legacybridge_attr_durable",proof.attributeProof().modId());assertEquals(128,proof.durability());
        assertEquals("bridge_durable_attribute_item",proof.attributeProof().registryName());
        assertEquals(7,nodes.values().stream().mapToInt(n->n.methods.size()).sum());
    }

    @TestFactory Stream<DynamicTest> allUnprovedEffectsIncludingRemovedMetadataBlockBothNamespaces() {
        var changes=new LinkedHashMap<String,Consumer<Map<String,ClassNode>>>();
        changes.put("capacity127",n->((IntInsnNode)op(n,ENTRY,"createItem",5)).operand=127);
        changes.put("capacity0",n->((IntInsnNode)op(n,ENTRY,"createItem",5)).operand=0);
        changes.put("capacitynegative",n->((IntInsnNode)op(n,ENTRY,"createItem",5)).operand=-128);
        changes.put("nonreviewed literal encoding",n->replace(n,ENTRY,"createItem",5,new LdcInsnNode(128)));
        changes.put("float capacity",n->replace(n,ENTRY,"createItem",5,new LdcInsnNode(128F)));
        changes.put("defaultDurability instead",n->((MethodInsnNode)op(n,ENTRY,"createItem",6)).name=n.get(ITEM).superName.equals("net/minecraft/item/Item")?"func_200916_b":"m_41499_");
        changes.put("stacksTo instead",n->((MethodInsnNode)op(n,ENTRY,"createItem",6)).name=n.get(ITEM).superName.equals("net/minecraft/item/Item")?"func_200917_a":"m_41487_");
        changes.put("durability static opcode",n->((MethodInsnNode)op(n,ENTRY,"createItem",6)).setOpcode(INVOKESTATIC));
        changes.put("durability wrong owner",n->((MethodInsnNode)op(n,ENTRY,"createItem",6)).owner="unknown/Properties");
        changes.put("durability wrong descriptor",n->((MethodInsnNode)op(n,ENTRY,"createItem",6)).desc="(F)Ljava/lang/Object;");
        changes.put("durability interface bit",n->((MethodInsnNode)op(n,ENTRY,"createItem",6)).itf=true);
        changes.put("property call order",n->{var body=real(method(n,ENTRY,"createItem"));method(n,ENTRY,"createItem").instructions.remove(body.get(4));method(n,ENTRY,"createItem").instructions.insert(body.get(6),body.get(4));});
        changes.put("extra noRepair property",n->method(n,ENTRY,"createItem").instructions.insertBefore(op(n,ENTRY,"createItem",7),new MethodInsnNode(INVOKEVIRTUAL,n.get(ITEM).superName+"$Properties","setNoRepair","()L"+n.get(ITEM).superName+"$Properties;",false)));
        changes.put("capacity instruction annotation",n->op(n,ENTRY,"createItem",5).visibleTypeAnnotations=new ArrayList<>(List.of(new TypeAnnotationNode(0,null,"Lunknown/Hook;"))));
        changes.put("durability instruction annotation",n->op(n,ENTRY,"createItem",6).invisibleTypeAnnotations=new ArrayList<>(List.of(new TypeAnnotationNode(0,null,"Lunknown/Hook;"))));
        changes.put("factory handler",n->method(n,ENTRY,"createItem").tryCatchBlocks.add(new TryCatchBlockNode(new LabelNode(),new LabelNode(),new LabelNode(),"java/lang/Exception")));
        changes.put("factory annotation",n->method(n,ENTRY,"createItem").visibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        changes.put("repair missing",n->n.get(ITEM).methods.remove(repair(n)));
        changes.put("repair private",n->repair(n).access=ACC_PRIVATE);
        changes.put("repair static",n->repair(n).access=ACC_PUBLIC|ACC_STATIC);
        changes.put("repair synchronized",n->repair(n).access|=ACC_SYNCHRONIZED);
        changes.put("repair descriptor",n->repair(n).desc="(Ljava/lang/Object;Ljava/lang/Object;)Z");
        changes.put("damaged argument read",n->((VarInsnNode)real(repair(n)).get(0)).var=1);
        changes.put("this argument read",n->((VarInsnNode)real(repair(n)).get(0)).var=0);
        changes.put("getItem static",n->((MethodInsnNode)real(repair(n)).get(1)).setOpcode(INVOKESTATIC));
        changes.put("getItem foreign owner",n->((MethodInsnNode)real(repair(n)).get(1)).owner="unknown/Stack");
        changes.put("getItem interface bit",n->((MethodInsnNode)real(repair(n)).get(1)).itf=true);
        changes.put("different ingredient",n->((FieldInsnNode)real(repair(n)).get(2)).name="anotherIngredient");
        changes.put("different ingredient owner",n->((FieldInsnNode)real(repair(n)).get(2)).owner="unknown/Items");
        changes.put("ingredient instance field",n->((FieldInsnNode)real(repair(n)).get(2)).setOpcode(GETFIELD));
        changes.put("repair reversed branch",n->((JumpInsnNode)real(repair(n)).get(3)).setOpcode(IF_ACMPEQ));
        changes.put("repair foreign branch",n->((JumpInsnNode)real(repair(n)).get(3)).label=new LabelNode());
        changes.put("repair wrong branch target",n->((JumpInsnNode)real(repair(n)).get(3)).label=((JumpInsnNode)real(repair(n)).get(5)).label);
        changes.put("repair false success",n->repair(n).instructions.set(real(repair(n)).get(4),new InsnNode(ICONST_0)));
        changes.put("repair true failure",n->repair(n).instructions.set(real(repair(n)).get(6),new InsnNode(ICONST_1)));
        changes.put("repair side effect",n->repair(n).instructions.insert(new InsnNode(NOP)));
        changes.put("repair exception handler",n->repair(n).tryCatchBlocks.add(new TryCatchBlockNode(new LabelNode(),new LabelNode(),new LabelNode(),"java/lang/Exception")));
        changes.put("repair declared exception",n->repair(n).exceptions.add("java/lang/Exception"));
        changes.put("repair generic",n->repair(n).signature="(Ljava/lang/Object;Ljava/lang/Object;)Z");
        changes.put("repair visible annotation",n->repair(n).visibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        changes.put("repair invisible annotation",n->repair(n).invisibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        changes.put("repair type annotation",n->repair(n).visibleTypeAnnotations=new ArrayList<>(List.of(new TypeAnnotationNode(0,null,"Lunknown/Hook;"))));
        changes.put("repair annotable count",n->repair(n).visibleAnnotableParameterCount=2);
        changes.put("repair invisible annotable count",n->repair(n).invisibleAnnotableParameterCount=2);
        changes.put("repair annotation default",n->repair(n).annotationDefault="unknown");
        changes.put("repair parameters",n->repair(n).parameters=new ArrayList<>(List.of(new ParameterNode("unknown",0))));
        changes.put("repair custom attribute",n->repair(n).attrs=new ArrayList<>(List.of(new Attribute("Unknown"){})));
        changes.put("repair instruction annotation",n->real(repair(n)).get(1).visibleTypeAnnotations=new ArrayList<>(List.of(new TypeAnnotationNode(0,null,"Lunknown/Hook;"))));
        changes.put("wear callback",n->add(n.get(ITEM),ACC_PUBLIC,"hurtEnemy","()Z").instructions.add(new InsnNode(IRETURN)));
        changes.put("mutable player field",n->n.get(ITEM).fields.add(new FieldNode(ACC_PRIVATE,"player","Ljava/lang/Object;",null,null)));
        changes.put("mutable modifiers field",n->field(n,ITEM,"MODIFIERS").access&=~ACC_FINAL);
        changes.put("retained builder field",n->field(n,ITEM,"MODIFIERS").desc="L"+BUILDER+";");
        changes.put("changed damage amount",n->((LdcInsnNode)op(n,ITEM,"<clinit>",6)).cst=3.0D);
        changes.put("changed damage numeric type",n->((LdcInsnNode)op(n,ITEM,"<clinit>",6)).cst=2.0F);
        changes.put("changed speed amount",n->((LdcInsnNode)op(n,ITEM,"<clinit>",15)).cst=-2.0D);
        changes.put("UUID duplicated",n->((FieldInsnNode)op(n,ITEM,"<clinit>",13)).name=((FieldInsnNode)op(n,ITEM,"<clinit>",4)).name);
        changes.put("UUID swapped",n->{var first=(FieldInsnNode)op(n,ITEM,"<clinit>",4);var second=(FieldInsnNode)op(n,ITEM,"<clinit>",13);String name=first.name;first.name=second.name;second.name=name;});
        changes.put("UUID reconstructed",n->replace(n,ITEM,"<clinit>",4,new MethodInsnNode(INVOKESTATIC,"java/util/UUID","randomUUID","()Ljava/util/UUID;",false)));
        changes.put("wrong slot",n->((FieldInsnNode)real(endpoint(n)).get(1)).name="OFFHAND");
        changes.put("reversed attribute branch",n->((JumpInsnNode)real(endpoint(n)).get(2)).setOpcode(IF_ACMPEQ));
        changes.put("nonfinal subclass",n->n.get(ITEM).access&=~ACC_FINAL);
        changes.put("unknown interface",n->n.get(ITEM).interfaces.add("unknown/Hook"));
        changes.put("subclass generic",n->n.get(ITEM).signature="Lunknown/Base;");
        changes.put("unknown class annotation",n->n.get(ITEM).invisibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        changes.put("different supertype",n->n.get(ITEM).superName="net/minecraft/item/SwordItem");
        changes.put("protected platform owner",n->n.get(ITEM).name="java/lang/FakeMod");
        changes.put("null repair instructions",n->repair(n).instructions=null);
        changes.put("extra repair instructions",n->{for(int i=0;i<130;i++)repair(n).instructions.insert(new InsnNode(NOP));});
        return changes.entrySet().stream().flatMap(e->Stream.of(false,true).map(target->DynamicTest.dynamicTest(
            (target?"target ":"source ")+e.getKey(),()->{
                var nodes=fixture(target);e.getValue().accept(nodes);
                var error=assertThrows(TransformationException.class,()->ItemAttributeDurabilityContract.inspect(nodes));
                assertTrue(error.getMessage().startsWith("ITEM_ATTRIBUTE_DURABILITY_UNSUPPORTED:"),error.getMessage());
            })));
    }

    private static Map<String,ClassNode> fixture(boolean target) {
        var nodes=attributeOnlyFixture(target);var api=new Api(target);
        var factory=method(nodes,ENTRY,"createItem");var ctorCall=real(factory).get(5);
        factory.instructions.insertBefore(ctorCall,new IntInsnNode(SIPUSH,128));
        factory.instructions.insertBefore(ctorCall,new MethodInsnNode(INVOKEVIRTUAL,api.props,target?"m_41503_":"func_200918_c","(I)L"+api.props+";",false));
        String stack=target?"net/minecraft/world/item/ItemStack":"net/minecraft/item/ItemStack";
        String items=target?"net/minecraft/world/item/Items":"net/minecraft/item/Items";
        var repair=add(nodes.get(ITEM),ACC_PUBLIC,target?"m_6832_":"func_82789_a","(L"+stack+";L"+stack+";)Z");repair.maxLocals=3;
        repair.instructions.add(new VarInsnNode(ALOAD,2));call(repair,INVOKEVIRTUAL,stack,target?"m_41720_":"func_77973_b","()L"+api.item+";");
        get(repair,items,target?"f_42416_":"field_151042_j","L"+api.item+";");var mismatch=new LabelNode();var end=new LabelNode();
        repair.instructions.add(new JumpInsnNode(IF_ACMPNE,mismatch));repair.instructions.add(new InsnNode(ICONST_1));repair.instructions.add(new JumpInsnNode(GOTO,end));
        repair.instructions.add(mismatch);repair.instructions.add(new InsnNode(ICONST_0));repair.instructions.add(end);repair.instructions.add(new InsnNode(IRETURN));
        return nodes;
    }
    private static MethodNode repair(Map<String,ClassNode> nodes) {
        return nodes.get(ITEM).methods.stream().filter(m->m.name.equals("func_82789_a")||m.name.equals("m_6832_")).findFirst().orElseThrow();
    }

    private static Map<String,ClassNode> attributeOnlyFixture(boolean target) {
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
    private static MethodNode endpoint(Map<String,ClassNode> n){return n.get(ITEM).methods.stream().filter(m->m.name.equals("func_111205_h")||m.name.equals("m_7167_")).findFirst().orElseThrow();}
    private static List<AbstractInsnNode> real(MethodNode m){return ClientConfigContract.code(m);}
    private static AbstractInsnNode op(Map<String,ClassNode> nodes,String owner,String method,int offset){return real(method(nodes,owner,method)).get(offset);}
    private static void replace(Map<String,ClassNode> nodes,String owner,String method,int offset,AbstractInsnNode node){method(nodes,owner,method).instructions.set(op(nodes,owner,method,offset),node);}
    private static InvokeDynamicInsnNode supplier(Map<String,ClassNode> nodes){return (InvokeDynamicInsnNode)op(nodes,ENTRY,"<clinit>",6);}
    private static Map<String,byte[]> snapshot(Map<String,ClassNode> nodes){var bytes=new TreeMap<String,byte[]>();nodes.forEach((name,node)->{var w=new ClassWriter(0);node.accept(w);bytes.put(name,w.toByteArray());});return bytes;}
    private static void sameBytes(Map<String,byte[]> before,Map<String,ClassNode> after){var bytes=snapshot(after);assertEquals(before.keySet(),bytes.keySet());before.forEach((name,value)->assertArrayEquals(value,bytes.get(name),name));}
}
