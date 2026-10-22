package dev.legacybridge.converter.conversion.logic;

import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.transformation.BytecodeValidator;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Actual reobfuscated runtime adapter and pinned legacy/native archives, all read as bytes.
 * No original, adapter or Minecraft class is defined or executed. The bounded synthetic caller
 * model proves only the exact endpoint's argument/ordering/alias contract, not native equipment,
 * inventory hooks, NBT cloning, constructor lifecycle, event handling or gameplay equivalence. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class IronHelmetEmptySlotAdapterTest {
    private static final String ADAPTER="dev/legacybridge/runtime/compat/item/LegacyIronHelmetItem";
    private static final String ITEM="net/minecraft/world/item/Item", ARMOR="net/minecraft/world/item/ArmorItem",
            PROPS=ITEM+"$Properties", MATERIAL="net/minecraft/world/item/ArmorMaterial", MATERIALS="net/minecraft/world/item/ArmorMaterials",
            ARMOR_TYPE=ARMOR+"$Type", STACK="net/minecraft/world/item/ItemStack", SLOT="net/minecraft/world/entity/EquipmentSlot",
            LIVING="net/minecraft/world/entity/LivingEntity", MOB="net/minecraft/world/entity/Mob", PLAYER="net/minecraft/world/entity/player/Player",
            LEVEL="net/minecraft/world/level/Level", HAND="net/minecraft/world/InteractionHand", HOLDER="net/minecraft/world/InteractionResultHolder";
    private static final int METHOD_MASK=ACC_PUBLIC|ACC_PROTECTED|ACC_PRIVATE|ACC_STATIC|ACC_FINAL|ACC_ABSTRACT|ACC_NATIVE|ACC_SYNCHRONIZED|ACC_BRIDGE|ACC_VARARGS|ACC_SYNTHETIC;
    private static MappingInputs inputs;
    private static Side source,target;
    private static byte[] adapterBytes;
    @AfterAll static void release()throws Exception {
        try { if(source!=null)source.close(); } finally { try { if(target!=null)target.close(); }
            finally { source=null;target=null;inputs=null;adapterBytes=null; } }
    }

    @Test void compiledRuntimeContainsOnlyTheFixedRealOperationAndNativeOverride()throws Exception {
        var node=adapter();assertAdapterShape(node);BytecodeValidator.validate(adapterBytes);
        var to=side(false);assertEquals(to.ns.productionClass(ARMOR),node.superName);
        var ctor=find(node,"<init>","()V");assertEquals(List.of("ALOAD 0","GETSTATIC "+MATERIALS+".IRON:"+ref(MATERIALS),
                "GETSTATIC "+ARMOR_TYPE+".HELMET:"+ref(ARMOR_TYPE),"NEW "+PROPS,"DUP",
                "INVOKESPECIAL "+PROPS+".<init>()V","INVOKESPECIAL "+ARMOR+".<init>("+ref(MATERIAL)+ref(ARMOR_TYPE)+ref(PROPS)+")V","RETURN"),
                constructorCode(ctor,to));
        var factory=find(node,"create","()"+ref(ADAPTER));assertEquals(ACC_PUBLIC|ACC_STATIC,factory.access);
        assertEquals(List.of("NEW "+ADAPTER,"DUP","INVOKESPECIAL "+ADAPTER+".<init>()V","ARETURN"),factoryCode(factory));
        assertEquals(useSignature(),callback(node).signature);
        assertEquals(0,node.fields.size());assertEquals(3,node.methods.size());
    }

    @Test void actualAdapterAndPinnedOriginalHaveIdenticalFiniteCanonicalUseBodies()throws Exception {
        var old=side(true);var modern=side(false);var original=old.nativeMethod(ARMOR,"use",useDescriptor());var use=callback(adapter());
        assertEquals(expectedBody(),canonicalBody(original,old,false));assertEquals(expectedBody(),canonicalBody(use,modern,true));
        assertEquals(30,expectedBody().size());assertTrue(use.tryCatchBlocks.isEmpty());
        assertTrue(use.exceptions==null||use.exceptions.isEmpty());assertTrue(use.maxLocals<=16&&use.maxStack<=8);
        assertNotEquals("use",use.name,"Read the production reobfuscated adapter, not development classes");
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void explicitNativeArchivePinsMatchReviewedBodies(boolean old)throws Exception {
        var api=side(old);var digest=MessageDigest.getInstance("SHA-256");
        try(var stream=Files.newInputStream(api.path)){byte[] buffer=new byte[65536];for(int n;(n=stream.read(buffer))>=0;)digest.update(buffer,0,n);}
        assertEquals(old?"1a3e71d6ccb8461fe9d94f4c63551fe52a8c0c01116572c49a0c3253d0506b03":"b820f2243c37f060b17678f3a23c868da14d35ebf82ee47b4035b1718c0952ee",HexFormat.of().formatHex(digest.digest()));
    }

    @Test void allActualExternalCallsLinkToExactReviewedNativeDeclarationsAndKinds()throws Exception {
        for(var api:List.of(side(true),side(false)))api.verifyCalls();
        assertEquals(MOB,CALLS.get(1).declarer(true));assertEquals(LIVING,CALLS.get(1).declarer(false),
                "The static equipment resolver moved to LivingEntity; the target Mob callsite is inherited");
        var report=IronHelmetApiAudit.inspect(data());assertEquals("UNSUPPORTED",report.toTree().get("status"));
        assertEquals(165,((Map<?,?>)((Map<?,?>)report.toTree().get("knownFacts")).get("ironHelmet")).get("maxDamage"));
        var modern=side(false);
        modern.exact(ARMOR,"<init>","("+ref(MATERIAL)+ref(ARMOR_TYPE)+ref(PROPS)+")V",ACC_PUBLIC,INVOKESPECIAL);
        modern.exact(PROPS,"<init>","()V",ACC_PUBLIC,INVOKESPECIAL);
        modern.exact(ARMOR,"use",useDescriptor(),ACC_PUBLIC,INVOKEVIRTUAL);
        modern.exactField(MATERIALS,"IRON",ref(MATERIALS));modern.exactField(ARMOR_TYPE,"HELMET",ref(ARMOR_TYPE));
    }

    static Stream<Arguments> cases(){return Stream.of(true,false).flatMap(client->Stream.of(Hand.values()).flatMap(hand->
            Stream.of(true,false).flatMap(occupied->Stream.of(Equipment.values()).map(slot->Arguments.of(client,hand,occupied,slot)))));}
    @ParameterizedTest @MethodSource("cases")
    void realBodiesPreserveBothHandsSidesSlotsAndReturnAlias(boolean client,Hand hand,boolean occupied,Equipment slot)throws Exception {
        var before=new Context(client,hand,occupied,slot);var after=new Context(client,hand,occupied,slot);
        var original=run(side(true).nativeMethod(ARMOR,"use",useDescriptor()),side(true),false,before);
        var converted=run(callback(adapter()),side(false),true,after);
        assertEquals(original,converted);assertEquals(1,converted.handReads);assertEquals(1,converted.slotReads);
        assertEquals(0,converted.stats);assertEquals(5,converted.otherHandCount);assertTrue(converted.returnedOriginalHand);
        if(occupied){assertEquals("FAIL",converted.result);assertEquals(1,converted.selectedHandCount);assertEquals(0,converted.writes);assertEquals(0,converted.copies);
            assertEquals("previous",converted.equippedKind);assertEquals(List.of("getItemInHand","getEquipmentSlotForItem","getItemBySlot","isEmpty","fail"),converted.trace);}
        else{assertEquals(client?"SUCCESS":"CONSUME",converted.result);assertEquals(0,converted.selectedHandCount);assertEquals(1,converted.writes);assertEquals(1,converted.copies);
            assertEquals("held",converted.equippedKind);assertEquals(1,converted.equippedCount);assertTrue(converted.equippedIsCopy);
            assertEquals(List.of("getItemInHand","getEquipmentSlotForItem","getItemBySlot","isEmpty","copy","setItemSlot","setCount","isClientSide","sidedSuccess"),converted.trace);}
    }

    @Test void separatePlayersAndSequentialUseDoNotShareStateOrLoseEarlierEquipment()throws Exception {
        var method=callback(adapter());var api=side(false);var one=new Context(false,Hand.MAIN_HAND,false,Equipment.HEAD);var two=new Context(true,Hand.OFF_HAND,false,Equipment.HEAD);
        assertEquals("CONSUME",run(method,api,true,one).result);assertEquals(1,two.selected().count);assertEquals(0,two.equipment.get(Equipment.HEAD).count);
        assertEquals("SUCCESS",run(method,api,true,two).result);assertNotSame(one.equipment.get(Equipment.HEAD),two.equipment.get(Equipment.HEAD));
        one.hands.put(Hand.MAIN_HAND,new Stack("held",1));one.resetTrace();var repeated=run(method,api,true,one);
        assertEquals("FAIL",repeated.result);assertEquals(1,repeated.selectedHandCount);assertEquals(0,repeated.writes);assertEquals("held",repeated.equippedKind);
    }

    @ParameterizedTest @ValueSource(strings={"getItemInHand","getEquipmentSlotForItem","getItemBySlot","isEmpty","copy","setItemSlot","setCount","isClientSide","sidedSuccess"})
    void uncaughtEndpointExceptionsPreserveOrderingAndPartialEffects(String failure)throws Exception {
        var old=new Context(false,Hand.OFF_HAND,false,Equipment.HEAD);var modern=new Context(false,Hand.OFF_HAND,false,Equipment.HEAD);old.failAt=failure;modern.failAt=failure;
        assertThrows(EndpointFailure.class,()->run(side(true).nativeMethod(ARMOR,"use",useDescriptor()),side(true),false,old));
        assertThrows(EndpointFailure.class,()->run(callback(adapter()),side(false),true,modern));
        assertEquals(old.state(),modern.state());assertEquals(old.trace,modern.trace);
        if(failure.equals("setCount")){assertEquals(1,modern.selected().count);assertEquals(1,modern.writes);assertEquals(1,modern.equipment.get(Equipment.HEAD).count);}
    }

    @ParameterizedTest @ValueSource(strings={"hand","player","world","setCount","copyAndClear","owner","descriptor","opcode","interfaceFlag","branch","newCall","noCopy","extraReturn"})
    void alteredAdapterCodeCannotPassTheExactOriginalBodyContract(String change)throws Exception {
        var method=copy(callback(adapter()));var body=real(method);
        switch(change){
            case "hand"->((VarInsnNode)body.get(1)).var=2;
            case "player"->((VarInsnNode)body.get(0)).var=1;
            case "world"->((VarInsnNode)body.get(23)).var=0;
            case "setCount"->method.instructions.set(body.get(20),new InsnNode(ICONST_1));
            case "copyAndClear"->{var call=(MethodInsnNode)body.get(17);call.name=side(false).ns.productionMethod(STACK,"copyAndClear","()"+ref(STACK));}
            case "owner"->((MethodInsnNode)body.get(2)).owner=side(false).ns.productionClass(LIVING);
            case "descriptor"->((MethodInsnNode)body.get(2)).desc="()"+ref(STACK);
            case "opcode"->((MethodInsnNode)body.get(2)).setOpcode(INVOKESTATIC);
            case "interfaceFlag"->((MethodInsnNode)body.get(2)).itf=true;
            case "branch"->((JumpInsnNode)body.get(13)).setOpcode(IFNE);
            case "newCall"->method.instructions.insert(new MethodInsnNode(INVOKESTATIC,"java/lang/System","nanoTime","()J",false));
            case "noCopy"->method.instructions.remove(body.get(17));
            case "extraReturn"->method.instructions.add(new InsnNode(RETURN));
            default->throw new AssertionError(change);
        }
        assertThrows(AssertionError.class,()->assertEquals(expectedBody(),canonicalBody(method,side(false),true)));
    }

    @ParameterizedTest @ValueSource(strings={"nonfinal","field","constructorOverload","callbackStatic","factorySubstitute"})
    void adapterAllocationShapeIsNotAGenericArmorExtension(String change)throws Exception {
        var node=new ClassNode();adapter().accept(node);
        switch(change){case "nonfinal"->node.access&=~ACC_FINAL;
            case "field"->node.fields.add(new FieldNode(ACC_PRIVATE,"state","I",null,null));
            case "constructorOverload"->node.methods.add(new MethodNode(ACC_PUBLIC,"<init>","(I)V",null,null));
            case "callbackStatic"->callback(node).access|=ACC_STATIC;
            case "factorySubstitute"->find(node,"create","()"+ref(ADAPTER)).desc="()"+ref(ITEM);
            default->throw new AssertionError(change);}
        assertThrows(AssertionError.class,()->assertAdapterShape(node));
    }

    static Stream<Arguments> declarationDrifts(){return Stream.of(true,false).flatMap(old->Stream.of(0,1,2,4,5,6,7,8,9)
            .flatMap(call->Stream.of("missing","private","static").map(change->Arguments.of(old,call,change))));}
    @ParameterizedTest @MethodSource("declarationDrifts")
    void exactNativeCallLinkageRejectsAbsentInaccessibleOrWrongKindDeclarations(boolean old,int callIndex,String change)throws Exception {
        var api=side(old);var role=CALLS.get(callIndex);var classes=new TreeMap<>(api.index.classes());String declarer=api.ns.productionClass(role.declarer(old));var type=classes.get(declarer);
        String name=api.ns.productionMethod(role.declarer(old),role.name,role.desc),desc=api.ns.productionDescriptor(role.desc);
        var methods=new ArrayList<>(type.methods());var matching=methods.stream().filter(m->m.name().equals(name)&&m.descriptor().equals(desc)).toList();assertEquals(1,matching.size());var method=matching.get(0);methods.remove(method);
        if(!change.equals("missing"))methods.add(new ApiIndex.ApiMember(name,desc,change.equals("private")?(method.access()&~ACC_PUBLIC)|ACC_PRIVATE:method.access()^ACC_STATIC));
        classes.put(declarer,new ApiIndex.ApiClass(type.name(),type.access(),type.superName(),type.interfaces(),methods,type.fields()));
        assertThrows(AssertionError.class,()->api.verifyCall(role,new ApiIndex(classes)));
    }

    private record Role(String owner,String sourceDeclarer,String targetDeclarer,String name,String desc,int opcode) {
        String declarer(boolean old){return old?sourceDeclarer:targetDeclarer;}
        String token(){return org.objectweb.asm.util.Printer.OPCODES[opcode]+" "+owner+"."+name+desc;}
    }
    private static final List<Role> CALLS=List.of(
        new Role(PLAYER,LIVING,LIVING,"getItemInHand","("+ref(HAND)+")"+ref(STACK),INVOKEVIRTUAL),
        new Role(MOB,MOB,LIVING,"getEquipmentSlotForItem","("+ref(STACK)+")"+ref(SLOT),INVOKESTATIC),
        new Role(PLAYER,PLAYER,PLAYER,"getItemBySlot","("+ref(SLOT)+")"+ref(STACK),INVOKEVIRTUAL),
        new Role(STACK,STACK,STACK,"isEmpty","()Z",INVOKEVIRTUAL),
        new Role(STACK,STACK,STACK,"copy","()"+ref(STACK),INVOKEVIRTUAL),
        new Role(PLAYER,PLAYER,PLAYER,"setItemSlot","("+ref(SLOT)+ref(STACK)+")V",INVOKEVIRTUAL),
        new Role(STACK,STACK,STACK,"setCount","(I)V",INVOKEVIRTUAL),
        new Role(LEVEL,LEVEL,LEVEL,"isClientSide","()Z",INVOKEVIRTUAL),
        new Role(HOLDER,HOLDER,HOLDER,"sidedSuccess","(Ljava/lang/Object;Z)"+ref(HOLDER),INVOKESTATIC),
        new Role(HOLDER,HOLDER,HOLDER,"fail","(Ljava/lang/Object;)"+ref(HOLDER),INVOKESTATIC));
    private static List<String> expectedBody(){return List.of("ALOAD 2","ALOAD 3",CALLS.get(0).token(),"ASTORE 4","ALOAD 4",CALLS.get(1).token(),"ASTORE 5","ALOAD 2","ALOAD 5",CALLS.get(2).token(),"ASTORE 6",
            "ALOAD 6",CALLS.get(3).token(),"IFEQ @27","ALOAD 2","ALOAD 5","ALOAD 4",CALLS.get(4).token(),CALLS.get(5).token(),"ALOAD 4","ICONST_0",CALLS.get(6).token(),"ALOAD 4","ALOAD 1",CALLS.get(7).token(),CALLS.get(8).token(),"ARETURN","ALOAD 4",CALLS.get(9).token(),"ARETURN");}
    private static void assertAdapterShape(ClassNode node)throws Exception {
        assertEquals(ADAPTER,node.name);assertEquals(V17,node.version);assertEquals(ACC_PUBLIC|ACC_FINAL|ACC_SUPER,node.access);assertEquals(ARMOR,node.superName);
        assertTrue(node.interfaces.isEmpty());assertTrue(node.fields.isEmpty());assertEquals(3,node.methods.size());
        assertEquals(ACC_PUBLIC,find(node,"<init>","()V").access);assertEquals(ACC_PUBLIC|ACC_STATIC,find(node,"create","()"+ref(ADAPTER)).access);
        assertEquals(ACC_PUBLIC,callback(node).access);
    }
    private static List<String> canonicalBody(MethodNode method,Side api,boolean production)throws Exception {
        assertTrue(method.tryCatchBlocks.isEmpty());assertTrue(method.instructions.size()<128);assertTrue(method.maxLocals<=16&&method.maxStack<=8);
        var out=new ArrayList<String>();for(var n:method.instructions){int op=n.getOpcode();if(op<0)continue;
            String s=org.objectweb.asm.util.Printer.OPCODES[op];if(n instanceof VarInsnNode v)s+=" "+v.var;
            else if(n instanceof MethodInsnNode m)s=api.role(m,production).token();
            else if(n instanceof JumpInsnNode j){assertTrue(op==IFEQ||op==GOTO);s+=" @"+position(method,j.label);}
            else if(!(n instanceof InsnNode))throw new AssertionError("Unreviewed adapter body instruction "+n);
            out.add(s);
        }return List.copyOf(out);
    }
    private static List<String> constructorCode(MethodNode method,Side api)throws Exception {
        var out=new ArrayList<String>();for(var n:method.instructions){int op=n.getOpcode();if(op<0)continue;String s=org.objectweb.asm.util.Printer.OPCODES[op];
            if(n instanceof VarInsnNode v)s+=" "+v.var;
            else if(n instanceof FieldInsnNode f){String owner=api.ns.canonicalClass(f.owner),name=owner.equals(MATERIALS)?"IRON":owner.equals(ARMOR_TYPE)?"HELMET":"?";
                assertEquals(api.ns.productionField(owner,name,ref(owner)),f.name);assertEquals(ref(owner),f.desc);s+=" "+owner+"."+name+":"+ref(owner);}
            else if(n instanceof TypeInsnNode t)s+=" "+api.ns.canonicalClass(t.desc);
            else if(n instanceof MethodInsnNode m){assertFalse(m.itf);assertEquals("<init>",m.name);s+=" "+api.ns.canonicalClass(m.owner)+"."+m.name+api.ns.canonicalDescriptor(m.desc);}
            else if(!(n instanceof InsnNode))throw new AssertionError("Unreviewed constructor instruction "+n);
            out.add(s);
        }return List.copyOf(out);
    }
    private static List<String> factoryCode(MethodNode method){var out=new ArrayList<String>();for(var n:method.instructions){int op=n.getOpcode();if(op<0)continue;String s=org.objectweb.asm.util.Printer.OPCODES[op];
        if(n instanceof TypeInsnNode t)s+=" "+t.desc;else if(n instanceof MethodInsnNode m){assertFalse(m.itf);s+=" "+m.owner+"."+m.name+m.desc;}else if(!(n instanceof InsnNode))throw new AssertionError(n);out.add(s);}return List.copyOf(out);}

    private enum Hand { MAIN_HAND, OFF_HAND }
    private enum Equipment { HEAD, CHEST }
    private static final class Stack {final String kind;int count;Stack(String kind,int count){this.kind=kind;this.count=count;}Stack copy(){return new Stack(kind,count);}}
    private static final class Context {
        final boolean client;final Hand selectedHand;final Equipment resolved;final Map<Hand,Stack> hands=new EnumMap<>(Hand.class);final Map<Equipment,Stack> equipment=new EnumMap<>(Equipment.class);
        final List<String> trace=new ArrayList<>();int copies,writes,handReads,slotReads;String failAt;
        Context(boolean client,Hand hand,boolean occupied,Equipment resolved){this.client=client;selectedHand=hand;this.resolved=resolved;
            for(Hand h:Hand.values())hands.put(h,new Stack(h==hand?"held":"unselected",h==hand?1:5));
            for(Equipment e:Equipment.values())equipment.put(e,new Stack(e==resolved?"previous":"untouched",e==resolved?(occupied?1:0):3));}
        Stack selected(){return hands.get(selectedHand);}void call(String name){trace.add(name);if(name.equals(failAt))throw new EndpointFailure(name);}
        void resetTrace(){trace.clear();copies=0;writes=0;handReads=0;slotReads=0;}
        List<Object> state(){return List.of(selected().count,equipment.get(resolved).kind,equipment.get(resolved).count,copies,writes,handReads,slotReads);}
    }
    private static final class EndpointFailure extends RuntimeException {EndpointFailure(String message){super(message);}}
    private record Holder(String result,Stack stack){}
    private record Outcome(String result,int selectedHandCount,int otherHandCount,String equippedKind,int equippedCount,boolean equippedIsCopy,
                           boolean returnedOriginalHand,int copies,int writes,int handReads,int slotReads,int stats,List<String> trace){}
    private static Outcome run(MethodNode body,Side api,boolean production,Context context)throws Exception {
        assertEquals(expectedBody(),canonicalBody(body,api,production));Object[] locals=new Object[body.maxLocals];locals[1]=context.client;locals[2]=context;locals[3]=context.selectedHand;
        var stack=new ArrayList<Object>();Stack original=context.selected();
        for(int pc=0,steps=0;pc<body.instructions.size()&&steps++<192;pc++){var n=body.instructions.get(pc);int op=n.getOpcode();if(op<0)continue;
            if(op==ALOAD)stack.add(locals[((VarInsnNode)n).var]);else if(op==ASTORE)locals[((VarInsnNode)n).var]=pop(stack);else if(op==ICONST_0)stack.add(0);
            else if(n instanceof MethodInsnNode m){var role=api.role(m,production);context.call(role.name);Object value=null;boolean push=true;
                switch(role.name){
                    case "getItemInHand"->{assertEquals(context.selectedHand,pop(stack));assertSame(context,pop(stack));context.handReads++;value=context.selected();}
                    case "getEquipmentSlotForItem"->{assertSame(original,pop(stack));value=context.resolved;}
                    case "getItemBySlot"->{assertEquals(context.resolved,pop(stack));assertSame(context,pop(stack));context.slotReads++;value=context.equipment.get(context.resolved);}
                    case "isEmpty"->{assertSame(context.equipment.get(context.resolved),stack.get(stack.size()-1));value=((Stack)pop(stack)).count==0;}
                    case "copy"->{assertSame(original,stack.get(stack.size()-1));value=((Stack)pop(stack)).copy();context.copies++;}
                    case "setItemSlot"->{var copy=(Stack)pop(stack);assertNotSame(original,copy);assertEquals(original.kind,copy.kind);assertEquals(original.count,copy.count);
                        assertEquals(context.resolved,pop(stack));assertSame(context,pop(stack));context.equipment.put(context.resolved,copy);context.writes++;push=false;}
                    case "setCount"->{assertEquals(0,pop(stack));assertSame(original,stack.get(stack.size()-1));((Stack)pop(stack)).count=0;push=false;}
                    case "isClientSide"->{value=pop(stack);assertEquals(context.client,value);}
                    case "sidedSuccess"->{boolean client=(Boolean)pop(stack);value=new Holder(client?"SUCCESS":"CONSUME",(Stack)pop(stack));}
                    case "fail"->value=new Holder("FAIL",(Stack)pop(stack));
                    default->throw new AssertionError("Unknown endpoint "+role.name);
                }if(push)stack.add(value);
            }else if(op==IFEQ){if(!(Boolean)pop(stack))pc=body.instructions.indexOf(((JumpInsnNode)n).label)-1;}
            else if(op==ARETURN){var result=(Holder)pop(stack);assertTrue(stack.isEmpty());var equipped=context.equipment.get(context.resolved);
                var other=context.hands.get(context.selectedHand==Hand.MAIN_HAND?Hand.OFF_HAND:Hand.MAIN_HAND);
                for(var entry:context.equipment.entrySet())if(entry.getKey()!=context.resolved){assertEquals("untouched",entry.getValue().kind);assertEquals(3,entry.getValue().count);}
                return new Outcome(result.result,original.count,other.count,equipped.kind,equipped.count,equipped!=original,result.stack==original,context.copies,context.writes,context.handReads,context.slotReads,0,List.copyOf(context.trace));}
            else throw new AssertionError("Unknown adapter opcode "+op);
        }throw new AssertionError("Endpoint path exceeded finite budget");
    }

    private static final class Side implements AutoCloseable {
        final boolean old;final Path path;final ZipFile zip;final LogicApiNamespace ns;final ApiIndex index;final Map<String,ClassNode> classes=new HashMap<>();
        Side(boolean old)throws Exception{this.old=old;var i=data();ns=new LogicApiNamespace(old?i.legacyOfficial():i.targetOfficial(),old?i.legacySrg():i.targetSrg(),old?i.legacyApi():i.targetApi(),old);
            var p=new Properties();try(var in=Files.newInputStream(Path.of(System.getProperty("legacybridge.logicMappingProfile")))){p.load(in);}
            path=Path.of(p.getProperty(old?"legacy.apiJars":"target.apiJars").split(";")[0]);zip=new ZipFile(path.toFile());
            var roots=new TreeSet<String>();for(String root:List.of(ARMOR,PROPS,MATERIALS,PLAYER,MOB,LEVEL,STACK,HOLDER))roots.add(ns.productionClass(root));if(!old)roots.add(ARMOR_TYPE);
            index=ns.normalize(roots);
        }
        ClassNode nativeClass(String owner)throws Exception {String name=ns.productionClass(owner);if(!classes.containsKey(name))classes.put(name,read(zip,name+".class"));return classes.get(name);}
        MethodNode nativeMethod(String owner,String name,String desc)throws Exception{return find(nativeClass(owner),name,ns.productionDescriptor(desc));}
        Role role(MethodInsnNode method,boolean production) {
            assertFalse(method.itf);for(var role:CALLS){String name=production?ns.productionMethod(role.declarer(old),role.name,role.desc):role.name;
                if(method.owner.equals(ns.productionClass(role.owner))&&method.name.equals(name)&&method.desc.equals(ns.productionDescriptor(role.desc))){assertEquals(role.opcode,method.getOpcode());return role;}}
            throw new AssertionError("Unreviewed native call "+method.owner+"."+method.name+method.desc);
        }
        void verifyCalls(){for(var role:CALLS)verifyCall(role,index);}
        void verifyCall(Role role,ApiIndex api){String name=ns.productionMethod(role.declarer(old),role.name,role.desc),desc=ns.productionDescriptor(role.desc),owner=ns.productionClass(role.owner),decl=ns.productionClass(role.declarer(old));
            var type=Objects.requireNonNull(api.getClass(decl));assertEquals(ACC_PUBLIC,type.access()&(ACC_PUBLIC|ACC_PRIVATE|ACC_PROTECTED));assertEquals(0,type.access()&ACC_INTERFACE);
            var matches=type.methods().stream().filter(m->m.name().equals(name)&&m.descriptor().equals(desc)).toList();assertEquals(1,matches.size());
            assertEquals(ACC_PUBLIC|(role.opcode==INVOKESTATIC?ACC_STATIC:0),matches.get(0).access()&METHOD_MASK);
            assertEquals(decl,resolve(api,owner,name,desc,new HashSet<>()));
        }
        void exact(String owner,String name,String desc,int access,int opcode){String productionName=ns.productionMethod(owner,name,desc);var c=Objects.requireNonNull(index.getClass(ns.productionClass(owner)));
            var found=c.methods().stream().filter(m->m.name().equals(productionName)&&m.descriptor().equals(ns.productionDescriptor(desc))).toList();assertEquals(1,found.size());assertEquals(access,found.get(0).access()&METHOD_MASK);
            assertEquals(opcode==INVOKEINTERFACE,(c.access()&ACC_INTERFACE)!=0);}
        void exactField(String owner,String name,String desc){var c=Objects.requireNonNull(index.getClass(ns.productionClass(owner)));String productionName=ns.productionField(owner,name,desc);
            var found=c.fields().stream().filter(f->f.name().equals(productionName)&&f.descriptor().equals(ns.productionDescriptor(desc))).toList();assertEquals(1,found.size());assertEquals(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_ENUM,found.get(0).access());}
        String resolve(ApiIndex api,String owner,String name,String desc,Set<String> visited){assertTrue(visited.add(owner));assertTrue(visited.size()<128);
            var c=Objects.requireNonNull(api.getClass(owner));if(c.methods().stream().anyMatch(m->m.name().equals(name)&&m.descriptor().equals(desc)))return owner;
            if(c.superName()!=null&&!c.superName().startsWith("java/")){String result=resolve(api,c.superName(),name,desc,new HashSet<>(visited));if(result!=null)return result;}
            for(String type:c.interfaces()){String result=resolve(api,type,name,desc,new HashSet<>(visited));if(result!=null)return result;}return null;}
        @Override public void close()throws Exception{zip.close();classes.clear();}
    }
    private static Side side(boolean old)throws Exception {if(old){if(source==null)source=new Side(true);return source;}if(target==null)target=new Side(false);return target;}
    private static MappingInputs data()throws Exception {if(inputs==null)inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));return inputs;}
    private static ClassNode adapter()throws Exception {if(adapterBytes==null){String path=System.getProperty("legacybridge.runtimeArtifact");assertNotNull(path);try(var zip=new ZipFile(path)){adapterBytes=bytes(zip,ADAPTER+".class");}}
        var node=new ClassNode();new ClassReader(adapterBytes).accept(node,0);return node;}
    private static MethodNode callback(ClassNode node)throws Exception{return find(node,side(false).ns.productionMethod(ARMOR,"use",useDescriptor()),useDescriptor());}
    private static ClassNode read(ZipFile zip,String name)throws Exception{var n=new ClassNode();new ClassReader(bytes(zip,name)).accept(n,0);return n;}
    private static byte[] bytes(ZipFile zip,String name)throws Exception {var entry=zip.getEntry(name);assertNotNull(entry,zip.getName()+" missing "+name);assertTrue(entry.getSize()>0&&entry.getSize()<=2*1024*1024);
        try(var in=zip.getInputStream(entry)){var bytes=in.readNBytes(2*1024*1024+1);assertTrue(bytes.length<=2*1024*1024);return bytes;}}
    private static MethodNode find(ClassNode node,String name,String desc){var matches=node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).toList();assertEquals(1,matches.size(),node.name+"."+name+desc);return matches.get(0);}
    private static int position(MethodNode method,LabelNode label){int count=0;for(var n:method.instructions){if(n==label)return count;if(n.getOpcode()>=0)count++;}throw new AssertionError("Foreign branch");}
    private static List<AbstractInsnNode> real(MethodNode method){return Arrays.stream(method.instructions.toArray()).filter(n->n.getOpcode()>=0).toList();}
    private static MethodNode copy(MethodNode method){var n=new MethodNode(ASM9,method.access,method.name,method.desc,method.signature,method.exceptions.toArray(String[]::new));method.accept(n);return n;}
    private static Object pop(List<Object> stack){return stack.remove(stack.size()-1);}
    private static String ref(String owner){return "L"+owner+";";}
    private static String useDescriptor(){return "("+ref(LEVEL)+ref(PLAYER)+ref(HAND)+")"+ref(HOLDER);}
    private static String useSignature(){return "("+ref(LEVEL)+ref(PLAYER)+ref(HAND)+")L"+HOLDER+"<L"+STACK+";>;";}
}
