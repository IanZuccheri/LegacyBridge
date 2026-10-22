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

/** Structural admission only. These tests never define or execute an original mod or native class. */
class OwnedStackDamageContractTest {
    private static final String ENTRY="example/StackEntry", ITEM="example/StackItem", MOD="stack_fixture";
    private static final String DEFERRED="net/minecraftforge/registries/DeferredRegister", REGISTRY="net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRIES="net/minecraftforge/registries/ForgeRegistries", CONTEXT="net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS="net/minecraftforge/eventbus/api/IEventBus";
    private static final Handle METAFACTORY=new Handle(H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",false);

    @Test void exactSourceAndTargetProofMatchesWithoutChangingAnyBody() {
        var source=fixture(false);var target=fixture(true);var sourceBytes=snapshot(source);var targetBytes=snapshot(target);
        var proof=OwnedStackDamageContract.inspect(source);
        assertEquals(proof,OwnedStackDamageContract.inspect(target));assertEquals(ENTRY,proof.entry());assertEquals(ITEM,proof.item());
        assertEquals("createItem",proof.factory());assertEquals("repairOne",proof.helper());assertEquals("stack_item",proof.registryName());
        assertEquals(128,proof.durability());assertEquals(5,source.values().stream().mapToInt(n->n.methods.size()).sum());
        assertEquals(29,real(helper(source)).size());sameBytes(sourceBytes,source);sameBytes(targetBytes,target);
        var tree=proof.toTree();assertEquals("NOT_RUN",tree.get("runtimeValidation"));assertEquals(false,tree.get("classesExecuted"));
        assertEquals("NONE; PUBLIC_OWNED_HELPER_ONLY",tree.get("trigger"));assertEquals(1,tree.get("maxStackSize"));
        assertEquals("CALLER_CONTRACT_NOT_IMPLEMENTED_OR_QUALIFIED",tree.get("threadAuthorityAndSynchronization"));
        assertEquals("REQUIRE_SEPARATE_VERIFICATION",tree.get("externalDeclarations"));assertEquals(5,((List<?>)tree.get("guards")).size());
        assertFalse(tree.containsKey("candidate"));assertThrows(UnsupportedOperationException.class,tree::clear);
        assertThrows(UnsupportedOperationException.class,()->((List<?>)tree.get("guards")).clear());
        assertThrows(IllegalArgumentException.class,()->new OwnedStackDamageContract.Proof(ENTRY,ITEM,MOD,"stack_item","createItem","repairOne",127));
    }

    @Test void targetInventoryOnlyDescribesCapacityNotWearOrAutomaticRepair() {
        var target=fixture(true);var before=snapshot(target);var plan=OwnedStackDamageContract.validate(target,MOD,"1.2.3-beta");
        assertEquals(1,plan.items().size());assertTrue(plan.blocks().isEmpty());assertTrue(plan.sounds().isEmpty());
        var item=plan.items().get(0);assertEquals("stack_item",item.name());assertNull(item.block());
        assertEquals(128,item.properties().durability());assertEquals(1,item.properties().stackSize());
        assertEquals("COMMON",item.properties().rarity());assertFalse(item.properties().fireResistant());assertNull(item.properties().creativeTab());
        sameBytes(before,target);
        assertThrows(TransformationException.class,()->OwnedStackDamageContract.validate(fixture(false),MOD,"1"));
        assertThrows(TransformationException.class,()->OwnedStackDamageContract.validate(target,"different","1"));
        for(String version:new String[]{null,"","../unsafe","v".repeat(129)})
            assertThrows(TransformationException.class,()->OwnedStackDamageContract.validate(target,MOD,version));
    }

    @Test void oldIndependentGrammarsDoNotAcquireDamageMutationSupport() {
        for(boolean target:new boolean[]{false,true}) {
            var nodes=fixture(target);var before=snapshot(nodes);OwnedStackDamageContract.inspect(nodes);
            assertThrows(TransformationException.class,()->DurableItemContract.inspect(nodes));
            assertThrows(TransformationException.class,()->ItemAttributeContract.inspect(nodes));
            assertThrows(TransformationException.class,()->ItemAttributeDurabilityContract.inspect(nodes));sameBytes(before,nodes);
        }
    }

    @Test void ownedClassFieldFactoryAndHelperNamesCanChangeWithoutErasingDebugData() {
        for(boolean target:new boolean[]{false,true}) {
            var nodes=fixture(target);
            for(var n:nodes.values()) {
                n.sourceFile="Original.java";n.sourceDebug="SMAP\nOriginal.java\n";
                for(var m:n.methods) {
                    var begin=new LabelNode();var end=new LabelNode();m.instructions.insert(begin);
                    m.instructions.insert(begin,new LineNumberNode(23,begin));m.instructions.add(end);
                    m.localVariables.add(new LocalVariableNode("debug","Ljava/lang/Object;",null,begin,end,0));
                }
                Collections.reverse(n.methods);Collections.reverse(n.fields);
            }
            var remapper=new Remapper() {
                @Override public String map(String name){return name.equals(ENTRY)?"different/Main":name.equals(ITEM)?"different/OwnItem":name;}
                @Override public String mapFieldName(String owner,String name,String desc){return owner.equals(ENTRY)?"role_"+name:name;}
                @Override public String mapMethodName(String owner,String name,String desc){
                    return owner.equals(ENTRY)&&name.equals("createItem")?"make":owner.equals(ITEM)&&name.equals("repairOne")?"boundedDecrement":name;
                }
            };
            var renamed=new TreeMap<String,ClassNode>();nodes.values().forEach(n->{var copy=new ClassNode();n.accept(new ClassRemapper(copy,remapper));renamed.put(copy.name,copy);});
            var before=snapshot(renamed);var proof=OwnedStackDamageContract.inspect(renamed);sameBytes(before,renamed);
            assertEquals("different/Main",proof.entry());assertEquals("different/OwnItem",proof.item());assertEquals("make",proof.factory());assertEquals("boundedDecrement",proof.helper());
        }
    }

    @Test void allOriginalMethodsHaveBoundsBeforeAnyOfflineFrameAllocation() {
        for(boolean target:new boolean[]{false,true})for(String owner:List.of(ENTRY,ITEM))for(var m:fixture(target).get(owner).methods) {
            for(int limit:new int[]{-1,65,65535}) {
                var stack=fixture(target);method(stack,owner,m.name).maxStack=limit;refused(stack);
                var locals=fixture(target);method(locals,owner,m.name).maxLocals=limit;refused(locals);
            }
            var instructions=fixture(target);for(int i=0;i<130;i++)method(instructions,owner,m.name).instructions.add(new LabelNode());refused(instructions);
        }
    }

    @Test void malformedGraphAndCancellationAreStructuredAndNeverReturnProof() {
        refused(null);refused(Map.of());var missing=fixture(false);missing.remove(ITEM);refused(missing);
        var extra=fixture(false);extra.put("extra/Helper",new ClassNode());refused(extra);
        var wrong=fixture(false);wrong.put("wrong/Key",wrong.remove(ITEM));refused(wrong);
        var nullNode=fixture(false);nullNode.put(ITEM,null);refused(nullNode);
        var control=new OperationControl(OperationLimits.defaults(),ignored->{});assertTrue(control.requestCancel());
        try(var ignored=control.enter()){assertThrows(OperationStoppedException.class,()->OwnedStackDamageContract.inspect(fixture(false)));}
    }

    @Test
    @EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
    void realOriginalArchiveHasFiveMethodsAndRemainsInert()throws Exception {
        var nodes=new TreeMap<String,ClassNode>();var path=Path.of(System.getProperty("legacybridge.fixtureRoot"),
            "owned-stack-damage-1.16.5/build/libs/legacybridge-owned-stack-damage-1.16.5-1.0.0.jar");
        try(var zip=new ZipFile(path.toFile())) {
            for(var entries=zip.entries();entries.hasMoreElements();) {
                var entry=entries.nextElement();if(entry.getName().endsWith(".class"))try(var input=zip.getInputStream(entry)) {
                    var node=new ClassNode();new ClassReader(input.readAllBytes()).accept(node,0);nodes.put(node.name,node);
                }
            }
        }
        var before=snapshot(nodes);var proof=OwnedStackDamageContract.inspect(nodes);sameBytes(before,nodes);
        assertEquals("legacybridge_stack_damage",proof.modId());assertEquals("bridge_stack_item",proof.registryName());
        assertEquals(128,proof.durability());assertEquals("repairOne",proof.helper());assertEquals(5,nodes.values().stream().mapToInt(n->n.methods.size()).sum());
    }

    @TestFactory Stream<DynamicTest> unknownEffectsAndContractsBlockBothNamespaces() {
        var changes=new LinkedHashMap<String,Consumer<Map<String,ClassNode>>>();
        changes.put("capacity changed",n->((IntInsnNode)op(n,ITEM,"<init>",4)).operand=127);
        changes.put("capacity zero",n->((IntInsnNode)op(n,ITEM,"<init>",4)).operand=0);
        changes.put("capacity negative",n->((IntInsnNode)op(n,ITEM,"<init>",4)).operand=-128);
        changes.put("nonreviewed literal encoding",n->replace(n,ITEM,"<init>",4,new LdcInsnNode(128)));
        changes.put("default durability",n->((MethodInsnNode)op(n,ITEM,"<init>",5)).name=n.get(ITEM).superName.contains("world")?"m_41499_":"func_200916_b");
        changes.put("stack size property",n->((MethodInsnNode)op(n,ITEM,"<init>",5)).name=n.get(ITEM).superName.contains("world")?"m_41487_":"func_200917_a");
        changes.put("foreign properties",n->((TypeInsnNode)op(n,ITEM,"<init>",1)).desc="unknown/Properties");
        changes.put("capacity static opcode",n->((MethodInsnNode)op(n,ITEM,"<init>",5)).setOpcode(INVOKESTATIC));
        changes.put("capacity interface bit",n->((MethodInsnNode)op(n,ITEM,"<init>",5)).itf=true);
        changes.put("capacity descriptor",n->((MethodInsnNode)op(n,ITEM,"<init>",5)).desc="(J)Ljava/lang/Object;");
        changes.put("virtual super constructor",n->((MethodInsnNode)op(n,ITEM,"<init>",6)).setOpcode(INVOKEVIRTUAL));
        changes.put("delegating own constructor",n->((MethodInsnNode)op(n,ITEM,"<init>",6)).owner=ITEM);
        changes.put("unknown property inserted",n->method(n,ITEM,"<init>").instructions.insertBefore(op(n,ITEM,"<init>",6),new InsnNode(NOP)));
        changes.put("helper missing",n->n.get(ITEM).methods.remove(helper(n)));
        changes.put("helper private",n->helper(n).access=ACC_PRIVATE);
        changes.put("helper static",n->helper(n).access|=ACC_STATIC);
        changes.put("helper synchronized",n->helper(n).access|=ACC_SYNCHRONIZED);
        changes.put("helper generic",n->helper(n).signature="(Ljava/lang/Object;)Z");
        changes.put("helper descriptor",n->helper(n).desc="(Ljava/lang/Object;)Z");
        changes.put("helper empty name",n->helper(n).name="");
        changes.put("helper return changed",n->replaceHelper(n,28,new InsnNode(RETURN)));
        changes.put("false return after write",n->replaceHelper(n,27,new InsnNode(ICONST_0)));
        changes.put("true return without write",n->replaceHelper(n,9,new InsnNode(ICONST_1)));
        changes.put("true return after range fail",n->replaceHelper(n,20,new InsnNode(ICONST_1)));
        changes.put("increment instead decrement",n->replaceHelper(n,25,new InsnNode(IADD)));
        changes.put("different amount",n->replaceHelper(n,24,new InsnNode(ICONST_2)));
        changes.put("overwrite observed damage",n->helper(n).instructions.insertBefore(real(helper(n)).get(22),new IincInsnNode(2,1)));
        changes.put("extra mutation",n->helper(n).instructions.insertBefore(real(helper(n)).get(27),new MethodInsnNode(INVOKEVIRTUAL,new Api(n.get(ITEM).superName.contains("world")).stack,"unknownWrite","()V",false)));
        changes.put("count mutation",n->((MethodInsnNode)real(helper(n)).get(26)).name="shrink");
        changes.put("different stack source",n->replaceHelper(n,22,new FieldInsnNode(GETSTATIC,ITEM,"shared","Ljava/lang/Object;")));
        changes.put("new stack source",n->replaceHelper(n,22,new TypeInsnNode(NEW,new Api(n.get(ITEM).superName.contains("world")).stack)));
        changes.put("null instructions",n->helper(n).instructions=null);
        changes.put("exception handler",n->helper(n).tryCatchBlocks.add(new TryCatchBlockNode(new LabelNode(),new LabelNode(),new LabelNode(),"java/lang/Exception")));
        changes.put("declared exception",n->helper(n).exceptions.add("java/lang/Exception"));
        changes.put("visible method annotation",n->helper(n).visibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        changes.put("invisible method annotation",n->helper(n).invisibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        changes.put("method type annotation",n->helper(n).visibleTypeAnnotations=new ArrayList<>(List.of(new TypeAnnotationNode(0,null,"Lunknown/Hook;"))));
        changes.put("method parameter annotation",n->helper(n).visitParameterAnnotation(0,"Lunknown/Hook;",true));
        changes.put("visible annotable count",n->helper(n).visibleAnnotableParameterCount=1);
        changes.put("invisible annotable count",n->helper(n).invisibleAnnotableParameterCount=1);
        changes.put("annotation default",n->helper(n).annotationDefault="unknown");
        changes.put("method parameters",n->helper(n).parameters=new ArrayList<>(List.of(new ParameterNode("unknown",0))));
        changes.put("method attribute",n->helper(n).attrs=new ArrayList<>(List.of(new Attribute("Unknown"){})));
        changes.put("instruction annotation",n->real(helper(n)).get(26).invisibleTypeAnnotations=new ArrayList<>(List.of(new TypeAnnotationNode(0,null,"Lunknown/Hook;"))));
        changes.put("retained per-instance stack",n->n.get(ITEM).fields.add(new FieldNode(ACC_PRIVATE,"stack","Ljava/lang/Object;",null,null)));
        changes.put("shared static state",n->n.get(ITEM).fields.add(new FieldNode(ACC_PRIVATE|ACC_STATIC,"shared","Ljava/lang/Object;",null,null)));
        changes.put("custom hook additional",n->add(n.get(ITEM),ACC_PUBLIC,"getDamage","()I").instructions.add(new InsnNode(IRETURN)));
        changes.put("item static initialization",n->add(n.get(ITEM),ACC_STATIC,"<clinit>","()V").instructions.add(new InsnNode(RETURN)));
        changes.put("nonfinal item",n->n.get(ITEM).access&=~ACC_FINAL);
        changes.put("nonfinal entry",n->n.get(ENTRY).access&=~ACC_FINAL);
        changes.put("unknown interface",n->n.get(ITEM).interfaces.add("unknown/Hook"));
        changes.put("different superclass",n->n.get(ITEM).superName="unknown/Item");
        changes.put("newer bytecode",n->n.get(ITEM).version=V17);
        changes.put("class generic",n->n.get(ITEM).signature="Lunknown/Base;");
        changes.put("item annotation",n->n.get(ITEM).invisibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        changes.put("outer class",n->n.get(ITEM).outerClass="unknown/Owner");
        changes.put("nest host",n->n.get(ITEM).nestHostClass="unknown/Owner");
        changes.put("record components",n->n.get(ITEM).recordComponents=new ArrayList<>(List.of(new RecordComponentNode("x","I",null))));
        changes.put("class attribute",n->n.get(ITEM).attrs=new ArrayList<>(List.of(new Attribute("Unknown"){})));
        changes.put("unknown inner class",n->n.get(ITEM).innerClasses.add(new InnerClassNode("unknown/Outer$Inner","unknown/Outer","Inner",ACC_PUBLIC|ACC_STATIC)));
        changes.put("duplicate inner class",n->n.get(ITEM).innerClasses.add(n.get(ITEM).innerClasses.get(0)));
        changes.put("wrong inner access",n->n.get(ITEM).innerClasses.get(0).access=ACC_PRIVATE);
        changes.put("protected platform owner",n->n.get(ITEM).name="java/lang/FakeMod");
        changes.put("registry identity mismatch",n->((LdcInsnNode)op(n,ENTRY,"<clinit>",1)).cst="different");
        changes.put("invalid registry name",n->((LdcInsnNode)op(n,ENTRY,"<clinit>",5)).cst="../unsafe");
        changes.put("registry namespace override",n->((LdcInsnNode)op(n,ENTRY,"<clinit>",5)).cst="other:item");
        changes.put("mutable registry field",n->field(n,"ITEMS").access&=~ACC_FINAL);
        changes.put("registry constant value",n->field(n,"ITEMS").value="invalid");
        changes.put("registry signature unknown",n->field(n,"ITEMS").signature="Lunknown/Registry;");
        changes.put("registry field annotation",n->field(n,"ITEMS").visibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        changes.put("duplicate field",n->n.get(ENTRY).fields.set(1,n.get(ENTRY).fields.get(0)));
        changes.put("null field",n->n.get(ENTRY).fields.set(1,null));
        changes.put("duplicate method",n->n.get(ITEM).methods.set(1,n.get(ITEM).methods.get(0)));
        changes.put("null method",n->n.get(ITEM).methods.set(1,null));
        changes.put("annotation mod mismatch",n->n.get(ENTRY).visibleAnnotations.get(0).values=new ArrayList<>(List.of("value","other")));
        changes.put("annotation additional",n->n.get(ENTRY).visibleAnnotations.add(new AnnotationNode("Lunknown/Hook;")));
        changes.put("factory public",n->method(n,ENTRY,"createItem").access=ACC_PUBLIC|ACC_STATIC);
        changes.put("factory wrong item",n->((TypeInsnNode)op(n,ENTRY,"createItem",0)).desc="unknown/Item");
        changes.put("early registry lookup",n->method(n,ENTRY,"<clinit>").instructions.insertBefore(op(n,ENTRY,"<clinit>",9),new MethodInsnNode(INVOKEVIRTUAL,"unknown/RegistryObject","get","()Ljava/lang/Object;",false)));
        changes.put("wrong event bus call",n->((MethodInsnNode)op(n,ENTRY,"<init>",4)).name="getForgeBus");
        changes.put("supplier wrong callback",n->{var d=supplier(n);var h=(Handle)d.bsmArgs[1];d.bsmArgs[1]=new Handle(h.getTag(),h.getOwner(),"unknown",h.getDesc(),false);});
        changes.put("supplier foreign callback",n->{var d=supplier(n);var h=(Handle)d.bsmArgs[1];d.bsmArgs[1]=new Handle(h.getTag(),"unknown/Owner",h.getName(),h.getDesc(),false);});
        changes.put("supplier virtual callback",n->{var d=supplier(n);var h=(Handle)d.bsmArgs[1];d.bsmArgs[1]=new Handle(H_INVOKEVIRTUAL,h.getOwner(),h.getName(),h.getDesc(),false);});
        changes.put("supplier interface callback",n->{var d=supplier(n);var h=(Handle)d.bsmArgs[1];d.bsmArgs[1]=new Handle(h.getTag(),h.getOwner(),h.getName(),h.getDesc(),true);});
        changes.put("supplier capture",n->supplier(n).desc="(Ljava/lang/Object;)Ljava/util/function/Supplier;");
        changes.put("supplier unknown bootstrap",n->supplier(n).bsm=new Handle(H_INVOKESTATIC,"unknown/Bootstrap","create","()V",false));
        for(int offset:new int[]{0,2,4,6,11,13,14,16,17,22,23}) {
            int index=offset;changes.put("helper wrong local "+index,n->((VarInsnNode)real(helper(n)).get(index)).var=4);
        }
        for(int offset:new int[]{3,7,12,18,26}) {
            int index=offset;changes.put("helper invocation owner "+index,n->((MethodInsnNode)real(helper(n)).get(index)).owner="unknown/Stack");
            changes.put("helper invocation name "+index,n->((MethodInsnNode)real(helper(n)).get(index)).name="unreviewed");
            changes.put("helper invocation descriptor "+index,n->((MethodInsnNode)real(helper(n)).get(index)).desc="()Ljava/lang/Object;");
            changes.put("helper invocation opcode "+index,n->((MethodInsnNode)real(helper(n)).get(index)).setOpcode(INVOKESTATIC));
            changes.put("helper invocation interface bit "+index,n->((MethodInsnNode)real(helper(n)).get(index)).itf=true);
        }
        for(int offset:new int[]{1,5,8,15,19}) {
            int index=offset;changes.put("reversed guard "+index,n->{var j=(JumpInsnNode)real(helper(n)).get(index);j.setOpcode(j.getOpcode()==IFNE?IFEQ:j.getOpcode()==IFLE?IFGT:j.getOpcode()==IFNULL?IFNONNULL:j.getOpcode()==IF_ACMPNE?IF_ACMPEQ:IF_ICMPGT);});
            changes.put("external branch label "+index,n->((JumpInsnNode)real(helper(n)).get(index)).label=new LabelNode());
            changes.put("loop branch "+index,n->{var begin=new LabelNode();helper(n).instructions.insert(begin);((JumpInsnNode)real(helper(n)).get(index)).label=begin;});
            changes.put("guard bypass "+index,n->{var write=new LabelNode();helper(n).instructions.insertBefore(real(helper(n)).get(index==19?27:22),write);((JumpInsnNode)real(helper(n)).get(index)).label=write;});
        }
        return changes.entrySet().stream().flatMap(e->Stream.of(false,true).map(target->DynamicTest.dynamicTest(
            (target?"target ":"source ")+e.getKey(),()->{var nodes=fixture(target);e.getValue().accept(nodes);refused(nodes);})));
    }

    private static Map<String,ClassNode> fixture(boolean target) {
        var api=new Api(target);var entry=node(ENTRY,"java/lang/Object");var item=node(ITEM,api.item);
        var annotation=new AnnotationNode("Lnet/minecraftforge/fml/common/Mod;");annotation.values=new ArrayList<>(List.of("value",MOD));
        entry.visibleAnnotations=new ArrayList<>(List.of(annotation));
        entry.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"MOD_ID","Ljava/lang/String;",null,MOD));
        entry.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"ITEMS","L"+DEFERRED+";","L"+DEFERRED+"<L"+api.item+";>;",null));
        entry.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"STACK_ITEM","L"+api.reference+";","L"+api.reference+"<L"+api.item+";>;",null));
        entry.innerClasses.add(new InnerClassNode("java/lang/invoke/MethodHandles$Lookup","java/lang/invoke/MethodHandles","Lookup",ACC_PUBLIC|ACC_STATIC|ACC_FINAL));
        item.innerClasses.add(new InnerClassNode(api.props,api.item,"Properties",ACC_PUBLIC|ACC_STATIC));
        var ctor=add(entry,ACC_PUBLIC,"<init>","()V");varLoad(ctor,ALOAD,0);call(ctor,INVOKESPECIAL,"java/lang/Object","<init>","()V");
        get(ctor,ENTRY,"ITEMS","L"+DEFERRED+";");call(ctor,INVOKESTATIC,CONTEXT,"get","()L"+CONTEXT+";");
        call(ctor,INVOKEVIRTUAL,CONTEXT,"getModEventBus","()L"+BUS+";");call(ctor,INVOKEVIRTUAL,DEFERRED,"register","(L"+BUS+";)V");insn(ctor,RETURN);
        var factory=add(entry,ACC_PRIVATE|ACC_STATIC,"createItem","()L"+api.item+";");factory.instructions.add(new TypeInsnNode(NEW,ITEM));insn(factory,DUP);
        call(factory,INVOKESPECIAL,ITEM,"<init>","()V");insn(factory,ARETURN);
        var init=add(entry,ACC_STATIC,"<clinit>","()V");get(init,REGISTRIES,"ITEMS","L"+REGISTRY+";");init.instructions.add(new LdcInsnNode(MOD));
        call(init,INVOKESTATIC,DEFERRED,"create","(L"+REGISTRY+";Ljava/lang/String;)L"+DEFERRED+";");
        init.instructions.add(new FieldInsnNode(PUTSTATIC,ENTRY,"ITEMS","L"+DEFERRED+";"));get(init,ENTRY,"ITEMS","L"+DEFERRED+";");init.instructions.add(new LdcInsnNode("stack_item"));
        init.instructions.add(new InvokeDynamicInsnNode("get","()Ljava/util/function/Supplier;",METAFACTORY,Type.getMethodType("()Ljava/lang/Object;"),
            new Handle(H_INVOKESTATIC,ENTRY,"createItem",factory.desc,false),Type.getMethodType(factory.desc)));
        call(init,INVOKEVIRTUAL,DEFERRED,"register","(Ljava/lang/String;Ljava/util/function/Supplier;)L"+api.reference+";");
        init.instructions.add(new FieldInsnNode(PUTSTATIC,ENTRY,"STACK_ITEM","L"+api.reference+";"));insn(init,RETURN);
        var itemCtor=add(item,ACC_PUBLIC,"<init>","()V");varLoad(itemCtor,ALOAD,0);itemCtor.instructions.add(new TypeInsnNode(NEW,api.props));insn(itemCtor,DUP);
        call(itemCtor,INVOKESPECIAL,api.props,"<init>","()V");itemCtor.instructions.add(new IntInsnNode(SIPUSH,128));
        call(itemCtor,INVOKEVIRTUAL,api.props,api.durability,"(I)L"+api.props+";");call(itemCtor,INVOKESPECIAL,api.item,"<init>","(L"+api.props+";)V");insn(itemCtor,RETURN);
        var helper=add(item,ACC_PUBLIC,"repairOne","(L"+api.stack+";)Z");var earlyFalse=new LabelNode();var observed=new LabelNode();var rangeFalse=new LabelNode();var write=new LabelNode();
        varLoad(helper,ALOAD,1);helper.instructions.add(new JumpInsnNode(IFNULL,earlyFalse));varLoad(helper,ALOAD,1);
        call(helper,INVOKEVIRTUAL,api.stack,api.getItem,"()L"+api.item+";");varLoad(helper,ALOAD,0);helper.instructions.add(new JumpInsnNode(IF_ACMPNE,earlyFalse));
        varLoad(helper,ALOAD,1);call(helper,INVOKEVIRTUAL,api.stack,api.isDamaged,"()Z");helper.instructions.add(new JumpInsnNode(IFNE,observed));
        helper.instructions.add(earlyFalse);insn(helper,ICONST_0);insn(helper,IRETURN);helper.instructions.add(observed);
        varLoad(helper,ALOAD,1);call(helper,INVOKEVIRTUAL,api.stack,api.getDamage,"()I");varLoad(helper,ISTORE,2);varLoad(helper,ILOAD,2);
        helper.instructions.add(new JumpInsnNode(IFLE,rangeFalse));varLoad(helper,ILOAD,2);varLoad(helper,ALOAD,1);call(helper,INVOKEVIRTUAL,api.stack,api.getMax,"()I");
        helper.instructions.add(new JumpInsnNode(IF_ICMPLE,write));helper.instructions.add(rangeFalse);insn(helper,ICONST_0);insn(helper,IRETURN);
        helper.instructions.add(write);varLoad(helper,ALOAD,1);varLoad(helper,ILOAD,2);insn(helper,ICONST_1);insn(helper,ISUB);
        call(helper,INVOKEVIRTUAL,api.stack,api.setDamage,"(I)V");insn(helper,ICONST_1);insn(helper,IRETURN);
        var nodes=new TreeMap<String,ClassNode>();nodes.put(ENTRY,entry);nodes.put(ITEM,item);return nodes;
    }

    private static final class Api {
        final String item,props,reference,stack,durability,getItem,isDamaged,getDamage,getMax,setDamage;
        Api(boolean target) {
            item=target?"net/minecraft/world/item/Item":"net/minecraft/item/Item";props=item+"$Properties";
            reference=target?"net/minecraftforge/registries/RegistryObject":"net/minecraftforge/fml/RegistryObject";
            stack=target?"net/minecraft/world/item/ItemStack":"net/minecraft/item/ItemStack";
            durability=target?"m_41503_":"func_200918_c";getItem=target?"m_41720_":"func_77973_b";
            isDamaged=target?"m_41768_":"func_77951_h";getDamage=target?"m_41773_":"func_77952_i";
            getMax=target?"m_41776_":"func_77958_k";setDamage=target?"m_41721_":"func_196085_b";
        }
    }
    private static ClassNode node(String name,String parent){var n=new ClassNode();n.version=V1_8;n.name=name;n.superName=parent;n.access=ACC_PUBLIC|ACC_FINAL|ACC_SUPER;return n;}
    private static MethodNode add(ClassNode n,int access,String name,String desc){var m=new MethodNode(access,name,desc,null,null);m.maxStack=8;m.maxLocals=3;n.methods.add(m);return m;}
    private static void call(MethodNode m,int opcode,String owner,String name,String desc){m.instructions.add(new MethodInsnNode(opcode,owner,name,desc,false));}
    private static void get(MethodNode m,String owner,String name,String desc){m.instructions.add(new FieldInsnNode(GETSTATIC,owner,name,desc));}
    private static void varLoad(MethodNode m,int opcode,int index){m.instructions.add(new VarInsnNode(opcode,index));}
    private static void insn(MethodNode m,int opcode){m.instructions.add(new InsnNode(opcode));}
    private static MethodNode method(Map<String,ClassNode> n,String owner,String name){return n.get(owner).methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static FieldNode field(Map<String,ClassNode> n,String name){return n.get(ENTRY).fields.stream().filter(f->f.name.equals(name)).findFirst().orElseThrow();}
    private static MethodNode helper(Map<String,ClassNode> n){return method(n,ITEM,"repairOne");}
    private static List<AbstractInsnNode> real(MethodNode m){return ClientConfigContract.code(m);}
    private static AbstractInsnNode op(Map<String,ClassNode> n,String owner,String name,int index){return real(method(n,owner,name)).get(index);}
    private static void replace(Map<String,ClassNode> n,String owner,String name,int index,AbstractInsnNode replacement){method(n,owner,name).instructions.set(op(n,owner,name,index),replacement);}
    private static void replaceHelper(Map<String,ClassNode> n,int index,AbstractInsnNode replacement){replace(n,ITEM,"repairOne",index,replacement);}
    private static InvokeDynamicInsnNode supplier(Map<String,ClassNode> n){return (InvokeDynamicInsnNode)op(n,ENTRY,"<clinit>",6);}
    private static void refused(Map<String,ClassNode> n){var error=assertThrows(TransformationException.class,()->OwnedStackDamageContract.inspect(n));assertTrue(error.getMessage().startsWith("OWNED_STACK_DAMAGE_UNSUPPORTED:"),error.getMessage());}
    private static Map<String,byte[]> snapshot(Map<String,ClassNode> n){var bytes=new TreeMap<String,byte[]>();n.forEach((name,node)->{var w=new ClassWriter(0);node.accept(w);bytes.put(name,w.toByteArray());});return bytes;}
    private static void sameBytes(Map<String,byte[]> before,Map<String,ClassNode> after){var bytes=snapshot(after);assertEquals(before.keySet(),bytes.keySet());before.forEach((name,value)->assertArrayEquals(value,bytes.get(name),name));}
}
