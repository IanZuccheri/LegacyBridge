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
class OwnedStackRepairUseContractTest {
    private static final String ENTRY="example/StackEntry", ITEM="example/StackItem", MOD="stack_fixture";
    private static final String DEFERRED="net/minecraftforge/registries/DeferredRegister", REGISTRY="net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRIES="net/minecraftforge/registries/ForgeRegistries", CONTEXT="net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS="net/minecraftforge/eventbus/api/IEventBus";
    private static final Handle METAFACTORY=new Handle(H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",false);

    @Test void exactSourceAndTargetProofMatchesWithoutChangingAnyBody() {
        var source=fixture(false);var target=fixture(true);var sourceBytes=snapshot(source);var targetBytes=snapshot(target);
        var proof=OwnedStackRepairUseContract.inspect(source);
        assertEquals(proof,OwnedStackRepairUseContract.inspect(target));assertEquals(ENTRY,proof.entry());assertEquals(ITEM,proof.item());
        assertEquals("createItem",proof.factory());assertEquals("repairOne",proof.helper());assertEquals("stack_item",proof.registryName());
        assertEquals(128,proof.durability());assertEquals(6,source.values().stream().mapToInt(n->n.methods.size()).sum());
        assertEquals(29,real(helper(source)).size());sameBytes(sourceBytes,source);sameBytes(targetBytes,target);
        var tree=proof.toTree();assertEquals("NOT_RUN",tree.get("runtimeValidation"));assertEquals(false,tree.get("classesExecuted"));
        assertEquals("EXACTLY_ONCE_ON_RECEIVED_PLAYER_WITH_RECEIVED_HAND",tree.get("handLookup"));assertEquals(1,tree.get("maxStackSize"));
        assertEquals("NATIVE_CALLER_PATH_UNMODIFIED; ARBITRARY_CALLER_AUTHORITY_AND_SYNC_NOT_QUALIFIED",tree.get("threadAuthorityAndSynchronization"));
        assertEquals("REQUIRE_SEPARATE_VERIFICATION",tree.get("externalDeclarations"));assertEquals(5,((List<?>)proof.stackProof().toTree().get("guards")).size());
        assertFalse(tree.containsKey("candidate"));assertThrows(UnsupportedOperationException.class,tree::clear);
        assertThrows(UnsupportedOperationException.class,()->((List<?>)proof.stackProof().toTree().get("guards")).clear());
        assertThrows(IllegalArgumentException.class,()->new OwnedStackRepairUseContract.Proof(ENTRY,ITEM,MOD,"stack_item","createItem","repairOne",127));
    }

    @Test void targetInventoryOnlyDescribesCapacityNotWearOrAutomaticRepair() {
        var target=fixture(true);var before=snapshot(target);var plan=OwnedStackRepairUseContract.validate(target,MOD,"1.2.3-beta");
        assertEquals(1,plan.items().size());assertTrue(plan.blocks().isEmpty());assertTrue(plan.sounds().isEmpty());
        var item=plan.items().get(0);assertEquals("stack_item",item.name());assertNull(item.block());
        assertEquals(128,item.properties().durability());assertEquals(1,item.properties().stackSize());
        assertEquals("COMMON",item.properties().rarity());assertFalse(item.properties().fireResistant());assertNull(item.properties().creativeTab());
        sameBytes(before,target);
        assertThrows(TransformationException.class,()->OwnedStackRepairUseContract.validate(fixture(false),MOD,"1"));
        assertThrows(TransformationException.class,()->OwnedStackRepairUseContract.validate(target,"different","1"));
        for(String version:new String[]{null,"","../unsafe","v".repeat(129)})
            assertThrows(TransformationException.class,()->OwnedStackRepairUseContract.validate(target,MOD,version));
    }

    @Test void oldIndependentGrammarsDoNotAcquireDamageMutationSupport() {
        for(boolean target:new boolean[]{false,true}) {
            var nodes=fixture(target);var before=snapshot(nodes);OwnedStackRepairUseContract.inspect(nodes);
            assertThrows(TransformationException.class,()->OwnedStackDamageContract.inspect(nodes));
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
                    m.localVariables.add(new LocalVariableNode("debug","L"+n.name+";",null,begin,end,0));
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
            var before=snapshot(renamed);var proof=OwnedStackRepairUseContract.inspect(renamed);sameBytes(before,renamed);
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
        try(var ignored=control.enter()){assertThrows(OperationStoppedException.class,()->OwnedStackRepairUseContract.inspect(fixture(false)));}
    }

    @Test
    @EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
    void realOriginalArchiveHasSixMethodsAndRemainsInert()throws Exception {
        var nodes=new TreeMap<String,ClassNode>();var path=Path.of(System.getProperty("legacybridge.fixtureRoot"),
            "owned-stack-repair-use-1.16.5/build/libs/legacybridge-owned-stack-repair-use-1.16.5-1.0.0.jar");
        try(var zip=new ZipFile(path.toFile())) {
            for(var entries=zip.entries();entries.hasMoreElements();) {
                var entry=entries.nextElement();if(entry.getName().endsWith(".class"))try(var input=zip.getInputStream(entry)) {
                    var node=new ClassNode();new ClassReader(input.readAllBytes()).accept(node,0);nodes.put(node.name,node);
                }
            }
        }
        var before=snapshot(nodes);var proof=OwnedStackRepairUseContract.inspect(nodes);sameBytes(before,nodes);
        assertEquals("legacybridge_stack_use",proof.modId());assertEquals("bridge_stack_use_item",proof.registryName());
        assertEquals(128,proof.durability());assertEquals("repairOne",proof.helper());assertEquals(6,nodes.values().stream().mapToInt(n->n.methods.size()).sum());
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

    @Test void callbackProofStatesPlatformDifferencesWithoutQualifyingRuntime() {
        var tree=OwnedStackRepairUseContract.inspect(fixture(false)).toTree();
        assertEquals("owned-stack-repair-use-v1",tree.get("contract"));
        assertEquals("SUCCESS_WITH_SAME_STACK; NO_HELPER_OR_MUTATION; PREDICTED_CONSUMPTION_AND_SWING",tree.get("client"));
        assertEquals("ONE_THIS_HELPER_CALL; TRUE_CONSUME_OR_FALSE_PASS_WITH_SAME_STACK",tree.get("server"));
        assertTrue(tree.get("nativeCreativeUse").toString().contains("RESTORES_PRIOR_DAMAGE"));
        assertTrue(tree.get("nativeBypass").toString().contains("COOLDOWN_SPECTATOR"));
        assertTrue(tree.get("normalization").toString().contains("NEVER_TRANSFORMED_OR_EMITTED"));
        assertFalse(tree.containsKey("multiplayerValidated"));
    }

    @Test void helperOnlyProgramCannotEnterCallbackRouteAndRemovingCallbackDoesNotMutateInput() {
        for(boolean target:new boolean[]{false,true}) {
            var nodes=fixture(target);var callback=use(nodes);var original=snapshot(nodes);
            OwnedStackRepairUseContract.inspect(nodes);sameBytes(original,nodes);
            nodes.get(ITEM).methods.remove(callback);
            assertNotNull(OwnedStackDamageContract.inspect(nodes));refused(nodes);
        }
    }

    @TestFactory Stream<DynamicTest> useSideReceiverFlowAndMetadataAreAnIndependentClosedContract() {
        var changes=new LinkedHashMap<String,Consumer<Map<String,ClassNode>>>();
        changes.put("missing callback signature",n->use(n).signature=null);
        changes.put("unknown callback generic",n->use(n).signature=use(n).signature.replace("ItemStack","UnknownStack"));
        changes.put("wrong erased callback descriptor",n->use(n).desc="(Ljava/lang/Object;)Ljava/lang/Object;");
        changes.put("unknown callback name",n->use(n).name="unknownUse");
        changes.put("private callback",n->use(n).access=ACC_PRIVATE);
        changes.put("static callback",n->use(n).access|=ACC_STATIC);
        changes.put("synchronized callback",n->use(n).access|=ACC_SYNCHRONIZED);
        changes.put("bridge callback",n->use(n).access|=ACC_BRIDGE);
        changes.put("final callback flag not original",n->use(n).access|=ACC_FINAL);
        changes.put("extra callback",n->{var m=new MethodNode();use(n).accept(m);n.get(ITEM).methods.add(m);});
        changes.put("callback absent",n->n.get(ITEM).methods.remove(use(n)));
        changes.put("signature collision with helper",n->{var h=helper(n);h.name=use(n).name;h.desc=use(n).desc;});
        changes.put("second hand lookup",n->use(n).instructions.insertBefore(useOp(n,4),new MethodInsnNode(INVOKEVIRTUAL,"unknown/Player","getItemInHand","()Ljava/lang/Object;",false)));
        changes.put("constant hand",n->replaceUse(n,1,new FieldInsnNode(GETSTATIC,"net/minecraft/util/Hand","MAIN_HAND","Lnet/minecraft/util/Hand;")));
        changes.put("copied stack",n->replaceUse(n,11,new TypeInsnNode(NEW,new UseApi(n.get(ITEM).superName.contains("world")).stack)));
        changes.put("null stack returned",n->replaceUse(n,7,new InsnNode(ACONST_NULL)));
        changes.put("foreign retained stack",n->replaceUse(n,11,new FieldInsnNode(GETSTATIC,ITEM,"sharedStack","Ljava/lang/Object;")));
        changes.put("client calls helper",n->{var m=(MethodInsnNode)useOp(n,8);m.owner=ITEM;m.name="repairOne";m.desc=helper(n).desc;m.setOpcode(INVOKEVIRTUAL);});
        changes.put("super use call",n->{var m=(MethodInsnNode)useOp(n,12);m.owner=n.get(ITEM).superName;m.name=use(n).name;m.desc=use(n).desc;m.setOpcode(INVOKESPECIAL);});
        changes.put("deferred asynchronous helper",n->((MethodInsnNode)useOp(n,12)).owner="java/util/concurrent/CompletableFuture");
        changes.put("extra inventory mutation",n->use(n).instructions.insertBefore(useOp(n,19),new FieldInsnNode(PUTFIELD,"unknown/Player","inventory","Ljava/lang/Object;")));
        changes.put("discarded helper result",n->replaceUse(n,13,new InsnNode(POP)));
        changes.put("unconditional server consume",n->replaceUse(n,13,new JumpInsnNode(GOTO,((JumpInsnNode)useOp(n,16)).label)));
        changes.put("client pass changes offhand behavior",n->((MethodInsnNode)useOp(n,8)).name=new UseApi(n.get(ITEM).superName.contains("world")).pass);
        changes.put("server success changes swing",n->((MethodInsnNode)useOp(n,15)).name=new UseApi(n.get(ITEM).superName.contains("world")).success);
        changes.put("failure consumes action",n->((MethodInsnNode)useOp(n,18)).name=new UseApi(n.get(ITEM).superName.contains("world")).consume);
        changes.put("getter direct declaration alias not fixture",n->((MethodInsnNode)useOp(n,2)).owner=n.get(ITEM).superName.contains("world")?"net/minecraft/world/entity/LivingEntity":"net/minecraft/entity/LivingEntity");
        changes.put("logical side different owner",n->((FieldInsnNode)useOp(n,5)).owner="unknown/World");
        changes.put("logical side different name",n->((FieldInsnNode)useOp(n,5)).name="isDedicatedServer");
        changes.put("logical side different descriptor",n->((FieldInsnNode)useOp(n,5)).desc="I");
        changes.put("logical side static access",n->replaceUse(n,5,new FieldInsnNode(GETSTATIC,((FieldInsnNode)useOp(n,5)).owner,((FieldInsnNode)useOp(n,5)).name,"Z")));
        changes.put("callback null instructions",n->use(n).instructions=null);
        changes.put("callback exception handler",n->use(n).tryCatchBlocks.add(new TryCatchBlockNode(new LabelNode(),new LabelNode(),new LabelNode(),"java/lang/Exception")));
        changes.put("callback declared exception",n->use(n).exceptions.add("java/lang/Exception"));
        changes.put("callback visible annotation",n->use(n).visibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        changes.put("callback invisible annotation",n->use(n).invisibleAnnotations=new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        changes.put("callback visible type annotation",n->use(n).visibleTypeAnnotations=new ArrayList<>(List.of(new TypeAnnotationNode(0,null,"Lunknown/Hook;"))));
        changes.put("callback invisible type annotation",n->use(n).invisibleTypeAnnotations=new ArrayList<>(List.of(new TypeAnnotationNode(0,null,"Lunknown/Hook;"))));
        changes.put("callback parameter annotation",n->use(n).visitParameterAnnotation(1,"Lunknown/Hook;",true));
        changes.put("callback invisible parameter annotation",n->use(n).visitParameterAnnotation(1,"Lunknown/Hook;",false));
        changes.put("callback visible annotation count",n->use(n).visibleAnnotableParameterCount=1);
        changes.put("callback invisible annotation count",n->use(n).invisibleAnnotableParameterCount=1);
        changes.put("callback annotation default",n->use(n).annotationDefault="unknown");
        changes.put("callback parameters",n->use(n).parameters=new ArrayList<>(List.of(new ParameterNode("world",0))));
        changes.put("callback attribute",n->use(n).attrs=new ArrayList<>(List.of(new Attribute("Unknown"){})));
        changes.put("callback instruction annotation",n->useOp(n,12).visibleTypeAnnotations=new ArrayList<>(List.of(new TypeAnnotationNode(0,null,"Lunknown/Hook;"))));
        changes.put("callback local annotation",n->use(n).visibleLocalVariableAnnotations=new ArrayList<>(List.of(new LocalVariableAnnotationNode(0,null,new LabelNode[]{new LabelNode()},new LabelNode[]{new LabelNode()},new int[]{4},"Lunknown/Hook;"))));
        changes.put("callback invisible local annotation",n->use(n).invisibleLocalVariableAnnotations=new ArrayList<>(List.of(new LocalVariableAnnotationNode(0,null,new LabelNode[]{new LabelNode()},new LabelNode[]{new LabelNode()},new int[]{4},"Lunknown/Hook;"))));
        changes.put("unknown debug local",n->debugLocal(n,4,"Lunknown/Stack;",null));
        changes.put("wrong debug role",n->debugLocal(n,0,"L"+new UseApi(n.get(ITEM).superName.contains("world")).stack+";",null));
        changes.put("unknown debug generic",n->debugLocal(n,4,"L"+new UseApi(n.get(ITEM).superName.contains("world")).stack+";","Lunknown/Stack;"));
        changes.put("debug extra slot",n->debugLocal(n,5,"Ljava/lang/Object;",null));
        changes.put("debug external label",n->use(n).localVariables.add(new LocalVariableNode("stack","L"+new UseApi(n.get(ITEM).superName.contains("world")).stack+";",null,new LabelNode(),new LabelNode(),4)));
        changes.put("null debug local",n->use(n).localVariables.add(null));
        changes.put("unknown frame local",n->use(n).instructions.insert(new FrameNode(F_FULL,1,new Object[]{"unknown/World"},0,new Object[]{})));
        changes.put("unknown frame stack",n->use(n).instructions.insert(new FrameNode(F_FULL,0,new Object[]{},1,new Object[]{"unknown/Stack"})));
        changes.put("uninitialized frame object",n->use(n).instructions.insert(new FrameNode(F_FULL,0,new Object[]{},1,new Object[]{new LabelNode()})));
        changes.put("unexpected primitive frame",n->use(n).instructions.insert(new FrameNode(F_FULL,0,new Object[]{},1,new Object[]{INTEGER})));
        changes.put("debug unowned line",n->use(n).instructions.insert(new LineNumberNode(3,new LabelNode())));
        for(int offset:new int[]{0,1,3,4,7,10,11,14,17}) {
            int index=offset;changes.put("callback wrong local "+index,n->((VarInsnNode)useOp(n,index)).var=6);
        }
        for(int offset:new int[]{2,8,12,15,18}) {
            int index=offset;
            changes.put("callback invocation owner "+index,n->((MethodInsnNode)useOp(n,index)).owner="unknown/Owner");
            changes.put("callback invocation name "+index,n->((MethodInsnNode)useOp(n,index)).name="unknownMethod");
            changes.put("callback invocation descriptor "+index,n->((MethodInsnNode)useOp(n,index)).desc="()Ljava/lang/Object;");
            changes.put("callback invocation opcode "+index,n->((MethodInsnNode)useOp(n,index)).setOpcode(INVOKEINTERFACE));
            changes.put("callback invocation interface bit "+index,n->((MethodInsnNode)useOp(n,index)).itf=true);
        }
        for(int offset:new int[]{6,13,16}) {
            int index=offset;
            changes.put("inverted callback branch "+index,n->((JumpInsnNode)useOp(n,index)).setOpcode(IFNE));
            changes.put("external callback label "+index,n->((JumpInsnNode)useOp(n,index)).label=new LabelNode());
            changes.put("callback backward cycle "+index,n->{var start=new LabelNode();use(n).instructions.insert(start);((JumpInsnNode)useOp(n,index)).label=start;});
            changes.put("callback branch to helper "+index,n->{var label=new LabelNode();use(n).instructions.insertBefore(useOp(n,12),label);((JumpInsnNode)useOp(n,index)).label=label;});
            changes.put("callback branch to return "+index,n->{var label=new LabelNode();use(n).instructions.insertBefore(useOp(n,9),label);((JumpInsnNode)useOp(n,index)).label=label;});
        }
        return changes.entrySet().stream().flatMap(e->Stream.of(false,true).map(target->DynamicTest.dynamicTest(
            (target?"target ":"source ")+e.getKey(),()->{var nodes=fixture(target);e.getValue().accept(nodes);refused(nodes);}))); 
    }

    private static void debugLocal(Map<String,ClassNode> nodes,int slot,String desc,String signature) {
        var m=use(nodes);var begin=new LabelNode();var end=new LabelNode();m.instructions.insert(begin);m.instructions.add(end);
        m.localVariables.add(new LocalVariableNode("debug",desc,signature,begin,end,slot));
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
        addUse(item,api,target);
        var nodes=new TreeMap<String,ClassNode>();nodes.put(ENTRY,entry);nodes.put(ITEM,item);return nodes;
    }


    private static void addUse(ClassNode item,Api api,boolean target) {
        var u=new UseApi(target);var use=add(item,ACC_PUBLIC,u.use,u.desc());use.signature=u.signature();use.maxLocals=5;
        var server=new LabelNode();var pass=new LabelNode();var finish=new LabelNode();
        varLoad(use,ALOAD,2);varLoad(use,ALOAD,3);call(use,INVOKEVIRTUAL,u.player,u.getHand,"(L"+u.hand+";)L"+api.stack+";");
        varLoad(use,ASTORE,4);varLoad(use,ALOAD,1);use.instructions.add(new FieldInsnNode(GETFIELD,u.world,u.client,"Z"));
        use.instructions.add(new JumpInsnNode(IFEQ,server));varLoad(use,ALOAD,4);call(use,INVOKESTATIC,u.holder,u.success,u.factory());insn(use,ARETURN);
        use.instructions.add(server);varLoad(use,ALOAD,0);varLoad(use,ALOAD,4);call(use,INVOKEVIRTUAL,item.name,"repairOne","(L"+api.stack+";)Z");
        use.instructions.add(new JumpInsnNode(IFEQ,pass));varLoad(use,ALOAD,4);call(use,INVOKESTATIC,u.holder,u.consume,u.factory());
        use.instructions.add(new JumpInsnNode(GOTO,finish));use.instructions.add(pass);varLoad(use,ALOAD,4);
        call(use,INVOKESTATIC,u.holder,u.pass,u.factory());use.instructions.add(finish);insn(use,ARETURN);
    }
    private static final class UseApi {
        final String world,player,hand,holder,stack,use,getHand,client,success,consume,pass;
        UseApi(boolean target) {
            world=target?"net/minecraft/world/level/Level":"net/minecraft/world/World";
            player=target?"net/minecraft/world/entity/player/Player":"net/minecraft/entity/player/PlayerEntity";
            hand=target?"net/minecraft/world/InteractionHand":"net/minecraft/util/Hand";
            holder=target?"net/minecraft/world/InteractionResultHolder":"net/minecraft/util/ActionResult";
            stack=target?"net/minecraft/world/item/ItemStack":"net/minecraft/item/ItemStack";
            use=target?"m_7203_":"func_77659_a";getHand=target?"m_21120_":"func_184586_b";client=target?"f_46443_":"field_72995_K";
            success=target?"m_19090_":"func_226248_a_";consume=target?"m_19096_":"func_226249_b_";pass=target?"m_19098_":"func_226250_c_";
        }
        String desc(){return "(L"+world+";L"+player+";L"+hand+";)L"+holder+";";}
        String signature(){return "(L"+world+";L"+player+";L"+hand+";)L"+holder+"<L"+stack+";>;";}
        String factory(){return "(Ljava/lang/Object;)L"+holder+";";}
    }
    private static MethodNode use(Map<String,ClassNode> n){return n.get(ITEM).methods.stream().filter(m->m.name.equals("func_77659_a")||m.name.equals("m_7203_")).findFirst().orElseThrow();}
    private static AbstractInsnNode useOp(Map<String,ClassNode> n,int i){return real(use(n)).get(i);}
    private static void replaceUse(Map<String,ClassNode> n,int i,AbstractInsnNode value){use(n).instructions.set(useOp(n,i),value);}

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
    private static void refused(Map<String,ClassNode> n){var error=assertThrows(TransformationException.class,()->OwnedStackRepairUseContract.inspect(n));assertTrue(error.getMessage().startsWith("OWNED_STACK_REPAIR_USE_UNSUPPORTED:"),error.getMessage());}
    private static Map<String,byte[]> snapshot(Map<String,ClassNode> n){var bytes=new TreeMap<String,byte[]>();n.forEach((name,node)->{var w=new ClassWriter(0);node.accept(w);bytes.put(name,w.toByteArray());});return bytes;}
    private static void sameBytes(Map<String,byte[]> before,Map<String,ClassNode> after){var bytes=snapshot(after);assertEquals(before.keySet(),bytes.keySet());before.forEach((name,value)->assertArrayEquals(value,bytes.get(name),name));}
}
