package dev.legacybridge.converter.conversion.logic;

import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.transformation.BytecodeValidator;
import java.nio.charset.StandardCharsets;
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

/** Reads real original and production bytecode; never defines game, original or adapter classes.
 * The bounded interpreter observes sound-call/list-write arguments and order only. Its synthetic
 * receiver/stack/list are not implementations or qualification of Minecraft sound, inventories,
 * client event hooks, synchronization, stack-copy provenance or gameplay. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class IronHelmetHeadEquipEffectsTest {
    private static final String OP="dev/legacybridge/runtime/compat/item/LegacyIronHelmetHeadEquipEffects",
            CLIENT=OP+"$ClientReceivers", HELMET="dev/legacybridge/runtime/compat/item/LegacyIronHelmetItem",
            PLAYER="net/minecraft/world/entity/player/Player", SERVER="net/minecraft/server/level/ServerPlayer",
            LOCAL="net/minecraft/client/player/LocalPlayer", REMOTE="net/minecraft/client/player/RemotePlayer",
            ABSTRACT_CLIENT="net/minecraft/client/player/AbstractClientPlayer", LIVING="net/minecraft/world/entity/LivingEntity",
            ENTITY="net/minecraft/world/entity/Entity", STACK="net/minecraft/world/item/ItemStack", ITEM="net/minecraft/world/item/Item",
            ARMOR="net/minecraft/world/item/ArmorItem", MATERIAL="net/minecraft/world/item/ArmorMaterial",
            MATERIALS="net/minecraft/world/item/ArmorMaterials", INVENTORY="net/minecraft/world/entity/player/Inventory",
            LIST="net/minecraft/core/NonNullList", SLOT="net/minecraft/world/entity/EquipmentSlot", GROUP=SLOT+"$Type",
            SOUND="net/minecraft/sounds/SoundEvent", SOUNDS="net/minecraft/sounds/SoundEvents", ITEMS="net/minecraft/world/item/Items",
            ENV="net/minecraftforge/fml/loading/FMLEnvironment", DIST="net/minecraftforge/api/distmarker/Dist";
    private static MappingInputs inputs;
    private static Side source,target;
    private static Map<String,byte[]> runtime;

    @AfterAll static void release()throws Exception {
        try{if(source!=null)source.close();}finally{try{if(target!=null)target.close();}
            finally{source=null;target=null;inputs=null;runtime=null;}}
    }

    @Test void actualProductionOperationAndClientGuardHaveNoStateOrSharedClientLinkage()throws Exception {
        ClassNode outer=production(OP),client=production(CLIENT);
        assertEquals(V17,outer.version);assertEquals(ACC_PUBLIC|ACC_FINAL|ACC_SUPER,outer.access);
        assertEquals("java/lang/Object",outer.superName);assertTrue(outer.interfaces.isEmpty());assertTrue(outer.fields.isEmpty());
        assertEquals(Set.of("<init>()V","equip"+equipDesc()),identities(outer));
        assertEquals(ACC_PRIVATE,method(outer,"<init>","()V").access);
        assertEquals(ACC_PUBLIC|ACC_STATIC,operation(outer).access);assertTrue(operation(outer).tryCatchBlocks.isEmpty());
        assertFalse(new String(runtime.get(OP),StandardCharsets.ISO_8859_1).contains("net/minecraft/client/"));
        assertTrue(client.fields.isEmpty());assertEquals("java/lang/Object",client.superName);
        assertEquals(Set.of("<init>()V","isKnown(Ljava/lang/Class;)Z"),identities(client));
        assertTrue((method(client,"isKnown","(Ljava/lang/Class;)Z").access&ACC_STATIC)!=0);
        var literals=real(method(client,"isKnown","(Ljava/lang/Class;)Z")).stream().filter(LdcInsnNode.class::isInstance)
                .map(LdcInsnNode.class::cast).map(n->n.cst).toList();
        assertEquals(List.of(Type.getObjectType(LOCAL),Type.getObjectType(REMOTE)),literals);
        for(byte[] bytes:runtime.values())BytecodeValidator.validate(bytes);
        validate(operation(outer),side(false),true);validate(method(client,"isKnown","(Ljava/lang/Class;)Z"),side(false),true);
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void nativeArchivesAreTheReviewedExactBaselines(boolean old)throws Exception {
        Side api=side(old);var hash=MessageDigest.getInstance("SHA-256");
        try(var in=Files.newInputStream(api.path)){byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))>=0;)hash.update(buffer,0,n);}
        assertEquals(old?"1a3e71d6ccb8461fe9d94f4c63551fe52a8c0c01116572c49a0c3253d0506b03":"b820f2243c37f060b17678f3a23c868da14d35ebf82ee47b4035b1718c0952ee",HexFormat.of().formatHex(hash.digest()));
    }

    @Test void nativeDeclarationsAndRealReceiverAncestorsPermitOnlyReviewedDispatch()throws Exception {
        for(Side api:List.of(side(true),side(false))){
            api.exactMethod(PLAYER,"setItemSlot","("+ref(SLOT)+ref(STACK)+")V",ACC_PUBLIC);
            api.exactMethod(PLAYER,"playSound","("+ref(SOUND)+"FF)V",ACC_PUBLIC);
            api.exactMethod(STACK,"isEmpty","()Z",ACC_PUBLIC);
            api.exactMethod(STACK,"getItem","()"+ref(ITEM),ACC_PUBLIC);
            api.exactMethod(SLOT,"getIndex","()I",ACC_PUBLIC);
            api.exactMethod(LIST,"set","(ILjava/lang/Object;)Ljava/lang/Object;",ACC_PUBLIC);
            api.exactField(INVENTORY,"armor",ref(LIST),ACC_PUBLIC|ACC_FINAL);
            assertTrue((api.type(STACK).access()&ACC_FINAL)!=0,"Stack receiver cannot be an unreviewed subclass");
            assertEquals(api.ns.productionClass(PLAYER),api.type(SERVER).superName());
            for(String owner:List.of(SERVER,ABSTRACT_CLIENT,LOCAL,REMOTE)){
                assertTrue(api.type(owner).methods().stream().noneMatch(m->m.name().equals(api.name(PLAYER,"setItemSlot","("+ref(SLOT)+ref(STACK)+")V"))&&m.descriptor().equals(api.desc("("+ref(SLOT)+ref(STACK)+")V"))),owner+" must not shadow setter dispatch");
                if(api.old)assertTrue(api.type(owner).methods().stream().noneMatch(m->m.name().equals(api.name(LIVING,"playEquipSound","("+ref(STACK)+")V"))&&m.descriptor().equals(api.desc("("+ref(STACK)+")V"))),owner+" must not shadow protected sound helper");
                else assertTrue(api.type(owner).methods().stream().noneMatch(m->m.name().equals(api.name(PLAYER,"getInventory","()"+ref(INVENTORY)))&&m.descriptor().equals(api.desc("()"+ref(INVENTORY)))),owner+" must not shadow inventory access");
            }
        }
        side(true).exactMethod(LIVING,"playEquipSound","("+ref(STACK)+")V",ACC_PROTECTED);
        side(true).exactMethod(ARMOR,"getMaterial","()"+ref(MATERIAL),ACC_PUBLIC);
        side(true).exactMethod(MATERIAL,"getEquipSound","()"+ref(SOUND),ACC_PUBLIC|ACC_ABSTRACT);
        side(true).exactMethod(ENTITY,"playSound","("+ref(SOUND)+"FF)V",ACC_PUBLIC);
        side(true).exactField(PLAYER,"inventory",ref(INVENTORY),ACC_PUBLIC|ACC_FINAL);
        assertTrue((side(true).type(MATERIAL).access()&ACC_INTERFACE)!=0);
        assertEquals(side(true).ns.productionClass(ENTITY),side(true).type(LIVING).superName());
        assertEquals(side(true).ns.productionClass(LIVING),side(true).type(PLAYER).superName());
        assertTrue(side(true).type(LIVING).methods().stream().noneMatch(m->m.name().equals(sideName(true,ENTITY,"playSound","("+ref(SOUND)+"FF)V"))
                &&m.descriptor().equals(source.desc("("+ref(SOUND)+"FF)V"))),"Source helper's LivingEntity-owned sound call is inherited");
        side(false).exactMethod(PLAYER,"getInventory","()"+ref(INVENTORY),ACC_PUBLIC);
        side(false).exactMethod(STACK,"getCount","()I",ACC_PUBLIC);
        side(false).exactMethod(MATERIALS,"getEquipSound","()"+ref(SOUND),ACC_PUBLIC);
        side(false).exactField(MATERIALS,"IRON",ref(MATERIALS),ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_ENUM);
        side(false).exactField(SLOT,"HEAD",ref(SLOT),ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_ENUM);
        for(String owner:List.of(LOCAL,REMOTE))assertEquals(ABSTRACT_CLIENT,side(false).type(owner).superName());
        var getter=side(false).nativeMethod(PLAYER,"getInventory","()"+ref(INVENTORY));var instructions=real(getter);
        assertEquals(List.of(ALOAD,GETFIELD,ARETURN),instructions.stream().map(AbstractInsnNode::getOpcode).toList());
        assertEquals(PLAYER,side(false).ns.canonicalClass(((FieldInsnNode)instructions.get(1)).owner));
        assertEquals("inventory",((FieldInsnNode)instructions.get(1)).name);
    }

    @ParameterizedTest @ValueSource(strings={"sourceInventoryPrivate","sourceMaterialStatic","sourceSoundMissing","targetArmorStatic","targetInventoryMissing","targetSoundProtected"})
    void exactExternalAccessAndDispatchDeclarationsCannotDrift(String change)throws Exception {
        boolean old=change.startsWith("source");Side api=side(old);boolean field=change.equals("sourceInventoryPrivate")||change.equals("targetArmorStatic");
        String owner=change.equals("sourceMaterialStatic")?ARMOR:change.equals("sourceSoundMissing")?MATERIAL:change.equals("targetArmorStatic")?INVENTORY:PLAYER;
        String name=switch(change){case "sourceInventoryPrivate"->"inventory";case "sourceMaterialStatic"->"getMaterial";case "sourceSoundMissing"->"getEquipSound";
            case "targetArmorStatic"->"armor";case "targetInventoryMissing"->"getInventory";default->"playSound";};
        String descriptor=switch(change){case "sourceInventoryPrivate"->ref(INVENTORY);case "sourceMaterialStatic"->"()"+ref(MATERIAL);case "sourceSoundMissing"->"()"+ref(SOUND);
            case "targetArmorStatic"->ref(LIST);case "targetInventoryMissing"->"()"+ref(INVENTORY);default->"("+ref(SOUND)+"FF)V";};
        String mappedName=field?api.ns.productionField(owner,name,descriptor):api.name(owner,name,descriptor);var type=api.type(owner);
        var members=new ArrayList<>(field?type.fields():type.methods());var member=members.stream().filter(m->m.name().equals(mappedName)&&m.descriptor().equals(api.desc(descriptor))).findFirst().orElseThrow();members.remove(member);
        if(!change.endsWith("Missing")){int access=change.endsWith("Static")?member.access()|ACC_STATIC:change.endsWith("Private")?(member.access()&~ACC_PUBLIC)|ACC_PRIVATE:(member.access()&~ACC_PUBLIC)|ACC_PROTECTED;
            members.add(new ApiIndex.ApiMember(member.name(),member.descriptor(),access));}
        var classes=new TreeMap<>(api.api.classes());classes.put(type.name(),new ApiIndex.ApiClass(type.name(),type.access(),type.superName(),type.interfaces(),field?type.methods():members,field?members:type.fields()));
        ApiIndex drift=new ApiIndex(classes);
        if(field)assertThrows(AssertionError.class,()->api.exactField(drift,owner,name,descriptor,member.access()));
        else assertThrows(AssertionError.class,()->api.exactMethod(drift,owner,name,descriptor,member.access()));
    }

    static Stream<Arguments> traces(){return Stream.of(SERVER,LOCAL,REMOTE).flatMap(receiver->Stream.of(true,false).flatMap(logicalClient->
            Stream.of(true,false).map(empty->Arguments.of(receiver,logicalClient,empty))));}
    @ParameterizedTest @MethodSource("traces")
    void compiledOperationMatchesOriginalHeadEffectsOnEachAdmittedReceiver(String receiver,boolean logicalClient,boolean empty)throws Exception {
        Context old=new Context(receiver,logicalClient,empty),modern=new Context(receiver,logicalClient,empty);
        runSource(old);runProduction(modern);
        assertEquals(old.effects,modern.effects);assertSame(old.input,old.inventory.armor[3]);assertSame(modern.input,modern.inventory.armor[3]);
        assertEquals(empty?List.of("write:3"):List.of("sound:IRON:1.0:1.0","write:3"),modern.effects);
        assertEquals(empty?List.of("getInventory","write"):List.of("sound","getInventory","write"),modern.nativeEffects);
        assertEquals(empty?0:1,modern.sounds);assertEquals(1,modern.writes);assertEquals(empty?0:1,modern.input.count);
        for(int i=0;i<3;i++){assertEquals("untouched"+i,modern.inventory.armor[i].item);assertEquals(i+2,modern.inventory.armor[i].count);}
        assertEquals(!receiver.equals(SERVER),modern.clientHelperCalls==1);
    }

    @Test void independentPlayersKeepReceivedStacksAndInventoriesDistinct()throws Exception {
        Context one=new Context(SERVER,false,false),two=new Context(LOCAL,true,false);
        runProduction(one);assertEquals(0,two.writes);assertNotSame(one.input,two.input);runProduction(two);
        assertSame(one.input,one.inventory.armor[3]);assertSame(two.input,two.inventory.armor[3]);assertNotSame(one.inventory.armor[3],two.inventory.armor[3]);
        one.input=new StackValue("helmet",0);one.effects.clear();one.nativeEffects.clear();runProduction(one);
        assertSame(one.input,one.inventory.armor[3]);assertEquals(List.of("write:3"),one.effects);assertEquals(1,two.inventory.armor[3].count);
    }

    @Test void integratedServerReceiverDoesNotEnterTheClientClassGuard()throws Exception {
        Context context=new Context(SERVER,false,false);context.physicalClient=true;runProduction(context);
        assertEquals(0,context.clientHelperCalls);assertEquals(List.of("sound:IRON:1.0:1.0","write:3"),context.effects);
    }

    @ParameterizedTest @ValueSource(strings={"sound","write"})
    void exceptionsKeepOriginalSoundBeforeWriteBoundaryAndAreNotSwallowed(String failure)throws Exception {
        Context old=new Context(SERVER,false,false),modern=new Context(SERVER,false,false);old.failAt=failure;modern.failAt=failure;
        assertThrows(EndpointFailure.class,()->runSource(old));assertThrows(EndpointFailure.class,()->runProduction(modern));
        assertEquals(old.effects,modern.effects);assertEquals(old.sounds,modern.sounds);assertEquals(0,modern.writes);
        assertEquals("previous",modern.inventory.armor[3].item);assertEquals(1,modern.input.count);
    }

    @Test void newVirtualInventoryAccessorFailureIsExplicitlyNotNativeEquivalence()throws Exception {
        Context modern=new Context(SERVER,false,false);modern.failAt="getInventory";
        assertThrows(EndpointFailure.class,()->runProduction(modern));assertEquals(1,modern.sounds);assertEquals(0,modern.writes);
        assertEquals(List.of("sound","getInventory"),modern.nativeEffects);
    }

    @ParameterizedTest @ValueSource(strings={"nullPlayer","nullSlot","nullStack","wrongSlot","foreignItem","countTwo","unknownServerSubclass","unknownClientSubclass","clientOnDedicated"})
    void inadmissibleArgumentsNeverReachSoundOrInventoryWrites(String change)throws Exception {
        Context context=new Context(SERVER,false,false);Object player=context,slot="HEAD",stack=context.input;
        switch(change){case "nullPlayer"->player=null;case "nullSlot"->slot=null;case "nullStack"->stack=null;case "wrongSlot"->slot="CHEST";
            case "foreignItem"->context.input.item="foreign";case "countTwo"->context.input.count=2;
            case "unknownServerSubclass"->context.receiver="example/OtherServerPlayer";
            case "unknownClientSubclass"->{context.receiver="example/OtherLocalPlayer";context.physicalClient=true;}
            case "clientOnDedicated"->{context.receiver=LOCAL;context.physicalClient=false;}
            default->throw new AssertionError(change);}
        Object finalPlayer=player,finalSlot=slot,finalStack=stack;
        assertThrows(GuardRejected.class,()->execute(operation(production(OP)),side(false),true,new Object[]{finalPlayer,finalSlot,finalStack},context));
        assertTrue(context.effects.isEmpty());assertTrue(context.nativeEffects.isEmpty());assertEquals(0,context.writes);
        if(change.equals("clientOnDedicated"))assertEquals(0,context.clientHelperCalls,"Dedicated path cannot resolve the client guard");
    }

    @ParameterizedTest @ValueSource(strings={"unknownCall","soundStatic","soundOwner","soundDescriptor","soundInterface","unknownField","writeIndex","wrongReceiverLiteral","clientWrongLiteral","writeBeforeSound"})
    void mutationsCannotSatisfyTheFiniteEffectAndAdmissionContract(String change)throws Exception {
        MethodNode body=copy(operation(production(OP)));MethodNode client=copy(method(production(CLIENT),"isKnown","(Ljava/lang/Class;)Z"));
        var api=side(false);var calls=real(body).stream().filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        MethodInsnNode sound=calls.stream().filter(m->api.match(m,PLAYER,"playSound","("+ref(SOUND)+"FF)V",INVOKEVIRTUAL,true)).findFirst().orElseThrow();
        switch(change){case "unknownCall"->sound.name="unknown";case "soundStatic"->sound.setOpcode(INVOKESTATIC);case "soundOwner"->sound.owner=ENTITY;
            case "soundDescriptor"->sound.desc="("+ref(SOUND)+"F)V";case "soundInterface"->sound.itf=true;
            case "unknownField"->{var field=real(body).stream().filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast).filter(f->f.owner.equals(INVENTORY)).findFirst().orElseThrow();field.name="unknown";}
            case "writeIndex"->{var index=calls.stream().filter(m->api.match(m,SLOT,"getIndex","()I",INVOKEVIRTUAL,true)).findFirst().orElseThrow();body.instructions.set(index,new InsnNode(ICONST_0));}
            case "wrongReceiverLiteral"->{var literal=real(body).stream().filter(LdcInsnNode.class::isInstance).map(LdcInsnNode.class::cast).filter(l->Type.getObjectType(SERVER).equals(l.cst)).findFirst().orElseThrow();literal.cst=Type.getObjectType(PLAYER);}
            case "clientWrongLiteral"->{var literal=real(client).stream().filter(LdcInsnNode.class::isInstance).map(LdcInsnNode.class::cast).findFirst().orElseThrow();literal.cst=Type.getObjectType(PLAYER);}
            case "writeBeforeSound"->{var getInventory=calls.stream().filter(m->api.match(m,PLAYER,"getInventory","()"+ref(INVENTORY),INVOKEVIRTUAL,true)).findFirst().orElseThrow();
                AbstractInsnNode start=getInventory.getPrevious();while(start.getOpcode()<0)start=start.getPrevious();var segment=new InsnList();AbstractInsnNode at=start;
                while(at!=null){AbstractInsnNode next=at.getNext();body.instructions.remove(at);segment.add(at);if(at.getOpcode()==POP)break;at=next;}
                body.instructions.insert(segment);}
            default->throw new AssertionError(change);}
        assertThrows(AssertionError.class,()->{
            validate(body,api,true);validate(client,api,true);
            for(String receiver:List.of(SERVER,LOCAL)){Context context=new Context(receiver,false,false);context.clientBody=client;
                execute(body,api,true,new Object[]{context,"HEAD",context.input},context);
                assertEquals(List.of("sound:IRON:1.0:1.0","write:3"),context.effects);}
        });
    }

    private static final class Context {
        String receiver;final boolean logicalClient;boolean physicalClient;StackValue input;final InventoryValue inventory=new InventoryValue();
        final List<String> effects=new ArrayList<>(),nativeEffects=new ArrayList<>();int sounds,writes,clientHelperCalls;String failAt;MethodNode clientBody;
        Context(String receiver,boolean logicalClient,boolean empty){this.receiver=receiver;this.logicalClient=logicalClient;physicalClient=!receiver.equals(SERVER);input=new StackValue("helmet",empty?0:1);}
        void effect(String name){nativeEffects.add(name);if(name.equals(failAt))throw new EndpointFailure(name);}
    }
    private static final class StackValue {String item;int count;StackValue(String item,int count){this.item=item;this.count=count;}}
    private static final class InventoryValue {final StackValue[] armor={new StackValue("untouched0",2),new StackValue("untouched1",3),new StackValue("untouched2",4),new StackValue("previous",1)};}
    private record TypeValue(String owner){}
    private record ItemValue(String kind){}
    private static final class GuardRejected extends RuntimeException {GuardRejected(String message){super(message);}}
    private static final class EndpointFailure extends RuntimeException {EndpointFailure(String message){super(message);}}
    private static final class NewValue {final String type;String message;NewValue(String type){this.type=type;}}

    private static void runSource(Context context)throws Exception {execute(side(true).nativeMethod(PLAYER,"setItemSlot","("+ref(SLOT)+ref(STACK)+")V"),side(true),false,new Object[]{context,"HEAD",context.input},context);}
    private static void runProduction(Context context)throws Exception {execute(operation(production(OP)),side(false),true,new Object[]{context,"HEAD",context.input},context);}

    private static Object execute(MethodNode method,Side api,boolean production,Object[] arguments,Context context)throws Exception {
        validate(method,api,production);Object[] locals=new Object[Math.max(method.maxLocals,arguments.length)];System.arraycopy(arguments,0,locals,0,arguments.length);var stack=new ArrayList<Object>();
        for(int pc=0,steps=0;pc<method.instructions.size()&&steps++<384;pc++){
            AbstractInsnNode n=method.instructions.get(pc);int op=n.getOpcode();if(op<0)continue;
            if(n instanceof VarInsnNode v){if(op==ALOAD||op==ILOAD)stack.add(locals[v.var]);else if(op==ASTORE||op==ISTORE)locals[v.var]=pop(stack);else throw new AssertionError(op);}
            else if(n instanceof LdcInsnNode l)stack.add(l.cst instanceof Type t?new TypeValue(t.getInternalName()):l.cst);
            else if(n instanceof FieldInsnNode f){String owner=api.canonical(f.owner);String name=api.fieldName(f,production);Object value;
                if(op==GETSTATIC){value=owner.equals(ENV)?(context.physicalClient?"CLIENT":"DEDICATED_SERVER"):name;}
                else if(op==GETFIELD){Object receiver=pop(stack);if(owner.equals(PLAYER)&&name.equals("inventory")){assertSame(context,receiver);value=context.inventory;}
                    else if(owner.equals(INVENTORY)&&name.equals("armor")){assertSame(context.inventory,receiver);value=context.inventory.armor;}else throw new AssertionError(owner+"."+name);}
                else throw new AssertionError(op);stack.add(value);
            }else if(n instanceof TypeInsnNode t){String owner=api.canonical(t.desc);
                if(op==NEW)stack.add(new NewValue(owner));
                else if(op==INSTANCEOF){Object value=pop(stack);stack.add((owner.equals(ARMOR)||owner.equals(HELMET))&&value instanceof ItemValue item&&item.kind.equals("helmet")?1:0);}
                else if(op==CHECKCAST){assertTrue(owner.equals(ARMOR)||owner.equals(HELMET));assertTrue(stack.get(stack.size()-1) instanceof ItemValue);}
                else throw new AssertionError(op);
            }else if(n instanceof MethodInsnNode m){String name=api.methodName(m,production),owner=api.canonical(m.owner);Object value=null;boolean push=true;
                if(owner.equals("java/util/Objects")){assertInstanceOf(String.class,pop(stack));Object object=pop(stack);if(object==null)throw new GuardRejected("null");value=object;}
                else if(owner.equals("java/lang/Object")&&name.equals("getClass")){Object object=pop(stack);assertSame(context,object);value=new TypeValue(context.receiver);}
                else if(owner.equals("java/lang/IllegalArgumentException")||owner.equals("java/lang/UnsupportedOperationException")){String message=(String)pop(stack);((NewValue)pop(stack)).message=message;push=false;}
                else if(owner.equals(CLIENT)){context.clientHelperCalls++;Object type=pop(stack);assertTrue(context.physicalClient);
                    value=execute(context.clientBody==null?method(production(CLIENT),"isKnown","(Ljava/lang/Class;)Z"):context.clientBody,api,true,new Object[]{type},context);}
                else switch(name){
                    case "playEquipSound"->{Object received=pop(stack),receiver=pop(stack);assertSame(context.input,received);assertSame(context,receiver);
                        execute(side(true).nativeMethod(LIVING,"playEquipSound","("+ref(STACK)+")V"),side(true),false,new Object[]{receiver,received},context);push=false;}
                    case "isEmpty"->{assertSame(context.input,stack.get(stack.size()-1));value=((StackValue)pop(stack)).count==0?1:0;}
                    case "getItem"->{assertSame(context.input,stack.get(stack.size()-1));value=new ItemValue(((StackValue)pop(stack)).item);}
                    case "getCount"->{assertSame(context.input,stack.get(stack.size()-1));value=((StackValue)pop(stack)).count;}
                    case "getMaterial"->{assertEquals(new ItemValue("helmet"),pop(stack));value="IRON";}
                    case "getEquipSound"->{assertEquals("IRON",pop(stack));value="IRON";}
                    case "playSound"->{assertEquals(1.0f,pop(stack));assertEquals(1.0f,pop(stack));assertEquals("IRON",pop(stack));assertSame(context,pop(stack));
                        context.effects.add("sound:IRON:1.0:1.0");context.effect("sound");context.sounds++;push=false;}
                    case "getInventory"->{assertSame(context,pop(stack));context.effect("getInventory");value=context.inventory;}
                    case "getType"->{assertEquals("HEAD",pop(stack));value="ARMOR";}
                    case "getIndex"->{assertEquals("HEAD",pop(stack));value=3;}
                    case "set"->{Object received=pop(stack);assertSame(context.input,received);assertEquals(3,pop(stack));assertSame(context.inventory.armor,pop(stack));
                        context.effects.add("write:3");context.effect("write");value=context.inventory.armor[3];context.inventory.armor[3]=(StackValue)received;context.writes++;}
                    default->throw new AssertionError("Unknown endpoint "+owner+"."+name);
                }if(push)stack.add(value);
            }else if(n instanceof JumpInsnNode j){boolean jump=switch(op){case GOTO->true;case IFEQ->((Integer)pop(stack))==0;case IFNE->((Integer)pop(stack))!=0;
                    case IF_ACMPEQ->{Object b=pop(stack),a=pop(stack);yield identityEquals(a,b);}case IF_ACMPNE->{Object b=pop(stack),a=pop(stack);yield !identityEquals(a,b);}
                    case IF_ICMPEQ->{Object b=pop(stack),a=pop(stack);yield a.equals(b);}case IF_ICMPNE->{Object b=pop(stack),a=pop(stack);yield !a.equals(b);}default->throw new AssertionError(op);};
                if(jump)pc=method.instructions.indexOf(j.label)-1;
            }else switch(op){case ICONST_0->stack.add(0);case ICONST_1->stack.add(1);case FCONST_1->stack.add(1.0f);case DUP->stack.add(stack.get(stack.size()-1));case POP->pop(stack);
                case ATHROW->{NewValue failure=(NewValue)pop(stack);throw new GuardRejected(failure.type+":"+failure.message);}
                case RETURN->{assertTrue(stack.isEmpty());return null;}case IRETURN->{Object value=pop(stack);assertTrue(stack.isEmpty());return value;}default->throw new AssertionError("Unknown opcode "+op);}
        }throw new AssertionError("Finite interpreter instruction budget exceeded");
    }
    private static boolean identityEquals(Object a,Object b){return a==b||a instanceof String&&a.equals(b)||a instanceof TypeValue&&a.equals(b);}

    private static final List<Call> CALLS=List.of(
        new Call(PLAYER,"playEquipSound","("+ref(STACK)+")V",INVOKEVIRTUAL),new Call(LIVING,"playSound","("+ref(SOUND)+"FF)V",INVOKEVIRTUAL),
        new Call(PLAYER,"playSound","("+ref(SOUND)+"FF)V",INVOKEVIRTUAL),new Call(PLAYER,"getInventory","()"+ref(INVENTORY),INVOKEVIRTUAL),
        new Call(STACK,"isEmpty","()Z",INVOKEVIRTUAL),new Call(STACK,"getItem","()"+ref(ITEM),INVOKEVIRTUAL),new Call(STACK,"getCount","()I",INVOKEVIRTUAL),
        new Call(ARMOR,"getMaterial","()"+ref(MATERIAL),INVOKEVIRTUAL),new Call(MATERIAL,"getEquipSound","()"+ref(SOUND),INVOKEINTERFACE),
        new Call(MATERIALS,"getEquipSound","()"+ref(SOUND),INVOKEVIRTUAL),new Call(SLOT,"getType","()"+ref(GROUP),INVOKEVIRTUAL),new Call(SLOT,"getIndex","()I",INVOKEVIRTUAL),
        new Call(LIST,"set","(ILjava/lang/Object;)Ljava/lang/Object;",INVOKEVIRTUAL),new Call("java/lang/Object","getClass","()Ljava/lang/Class;",INVOKEVIRTUAL),
        new Call("java/util/Objects","requireNonNull","(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",INVOKESTATIC),new Call(CLIENT,"isKnown","(Ljava/lang/Class;)Z",INVOKESTATIC),
        new Call("java/lang/IllegalArgumentException","<init>","(Ljava/lang/String;)V",INVOKESPECIAL),new Call("java/lang/UnsupportedOperationException","<init>","(Ljava/lang/String;)V",INVOKESPECIAL));
    private record Call(String owner,String name,String desc,int opcode){}
    private static final List<Field> FIELDS=List.of(new Field(PLAYER,"inventory",ref(INVENTORY),GETFIELD),new Field(INVENTORY,"armor",ref(LIST),GETFIELD),
        new Field(INVENTORY,"items",ref(LIST),GETFIELD),new Field(INVENTORY,"offhand",ref(LIST),GETFIELD),new Field(INVENTORY,"selected","I",GETFIELD),
        new Field(SLOT,"MAINHAND",ref(SLOT),GETSTATIC),new Field(SLOT,"OFFHAND",ref(SLOT),GETSTATIC),new Field(SLOT,"HEAD",ref(SLOT),GETSTATIC),
        new Field(GROUP,"ARMOR",ref(GROUP),GETSTATIC),new Field(MATERIALS,"IRON",ref(MATERIALS),GETSTATIC),
        new Field(SOUNDS,"ARMOR_EQUIP_GENERIC",ref(SOUND),GETSTATIC),new Field(SOUNDS,"ARMOR_EQUIP_ELYTRA",ref(SOUND),GETSTATIC),new Field(ITEMS,"ELYTRA",ref(ITEM),GETSTATIC),
        new Field(ENV,"dist",ref(DIST),GETSTATIC),new Field(DIST,"CLIENT",ref(DIST),GETSTATIC));
    private record Field(String owner,String name,String desc,int opcode){}
    private static void validate(MethodNode method,Side api,boolean production){
        assertTrue(method.tryCatchBlocks.isEmpty());assertTrue(method.instructions.size()<384);assertTrue(method.maxLocals<16&&method.maxStack<16);
        for(AbstractInsnNode n:method.instructions){if(n.getOpcode()<0)continue;
            if(n instanceof MethodInsnNode m)api.methodName(m,production);else if(n instanceof FieldInsnNode f)api.fieldName(f,production);
            else if(n instanceof TypeInsnNode t)assertTrue(Set.of(ARMOR,HELMET,"java/lang/IllegalArgumentException","java/lang/UnsupportedOperationException").contains(api.canonical(t.desc)),t.desc);
            else if(n instanceof LdcInsnNode l){if(l.cst instanceof Type t)assertTrue(Set.of(SERVER,LOCAL,REMOTE).contains(t.getInternalName()),"Unknown receiver literal "+t);else assertInstanceOf(String.class,l.cst);}
            else assertTrue(n instanceof VarInsnNode||n instanceof JumpInsnNode||n instanceof InsnNode,"Unreviewed bytecode "+n);
        }
    }

    private static final class Side implements AutoCloseable {
        final boolean old;final LogicApiNamespace ns;final ApiIndex api;final Path path;final ZipFile zip;final Map<String,ClassNode> classes=new HashMap<>();
        Side(boolean old)throws Exception{this.old=old;MappingInputs data=data();ns=new LogicApiNamespace(old?data.legacyOfficial():data.targetOfficial(),old?data.legacySrg():data.targetSrg(),old?data.legacyApi():data.targetApi(),old);
            var roots=new TreeSet<String>();for(String owner:List.of(PLAYER,SERVER,LOCAL,REMOTE,STACK,ARMOR,MATERIAL,MATERIALS,INVENTORY,LIST,SLOT))roots.add(ns.productionClass(owner));api=ns.normalize(roots);
            var props=new Properties();try(var in=Files.newInputStream(Path.of(System.getProperty("legacybridge.logicMappingProfile")))){props.load(in);}path=Path.of(props.getProperty(old?"legacy.apiJars":"target.apiJars").split(";")[0]);zip=new ZipFile(path.toFile());}
        String canonical(String owner){return ns.canonicalClass(owner);}String desc(String descriptor){return ns.productionDescriptor(descriptor);}
        String name(String owner,String name,String descriptor){if(owner.equals(LIST)&&name.equals("set")&&descriptor.equals("(ILjava/lang/Object;)Ljava/lang/Object;"))return "set";
            return owner.startsWith("net/minecraft/")?ns.productionMethod(owner,name,descriptor):name;}
        ApiIndex.ApiClass type(String owner){return Objects.requireNonNull(api.getClass(ns.productionClass(owner)),owner);}
        MethodNode nativeMethod(String owner,String name,String descriptor)throws Exception{return method(nativeClass(owner),name,desc(descriptor));}
        ClassNode nativeClass(String owner)throws Exception{String name=ns.productionClass(owner);if(!classes.containsKey(name))classes.put(name,read(bytes(zip,name+".class")));return classes.get(name);}
        boolean match(MethodInsnNode m,String owner,String name,String descriptor,int opcode,boolean production){
            String mappedOwner=owner.startsWith("net/minecraft/")?ns.productionClass(owner):owner;
            return m.owner.equals(mappedOwner)&&m.name.equals(production?name(owner,name,descriptor):name)&&m.desc.equals(desc(descriptor))&&m.getOpcode()==opcode&&m.itf==(opcode==INVOKEINTERFACE);}
        String methodName(MethodInsnNode m,boolean production){for(Call call:CALLS){if(!old&&call.name.equals("playEquipSound"))continue;
                if(match(m,call.owner,call.name,call.desc,call.opcode,production))return call.name;}
            throw new AssertionError("Unreviewed method "+m.owner+"."+m.name+m.desc+" opcode="+m.getOpcode());}
        String fieldName(FieldInsnNode f,boolean production){for(Field field:FIELDS){String owner=field.owner.startsWith("net/minecraft/")?ns.productionClass(field.owner):field.owner;
                if(f.owner.equals(owner)&&f.desc.equals(desc(field.desc))&&f.getOpcode()==field.opcode){String name=production&&field.owner.startsWith("net/minecraft/")?ns.productionField(field.owner,field.name,field.desc):field.name;if(f.name.equals(name))return field.name;}}
            throw new AssertionError("Unreviewed field "+f.owner+"."+f.name+f.desc);}
        void exactMethod(String owner,String name,String descriptor,int access){exactMethod(api,owner,name,descriptor,access);}
        void exactMethod(ApiIndex index,String owner,String name,String descriptor,int access){var matches=index.getClass(ns.productionClass(owner)).methods().stream().filter(m->m.name().equals(name(owner,name,descriptor))&&m.descriptor().equals(desc(descriptor))).toList();assertEquals(1,matches.size(),owner+"."+name);
            assertEquals(access,matches.get(0).access()&(ACC_PUBLIC|ACC_PROTECTED|ACC_PRIVATE|ACC_FINAL|ACC_STATIC|ACC_ABSTRACT|ACC_NATIVE|ACC_SYNCHRONIZED));}
        void exactField(String owner,String name,String descriptor,int access){exactField(api,owner,name,descriptor,access);}
        void exactField(ApiIndex index,String owner,String name,String descriptor,int access){var matches=index.getClass(ns.productionClass(owner)).fields().stream().filter(f->f.name().equals(ns.productionField(owner,name,descriptor))&&f.descriptor().equals(desc(descriptor))).toList();assertEquals(1,matches.size());assertEquals(access,matches.get(0).access());}
        @Override public void close()throws Exception{zip.close();classes.clear();}
    }
    private static Side side(boolean old)throws Exception{if(old){if(source==null)source=new Side(true);return source;}if(target==null)target=new Side(false);return target;}
    private static String sideName(boolean old,String owner,String name,String descriptor){return (old?source:target).name(owner,name,descriptor);}
    private static MappingInputs data()throws Exception{if(inputs==null)inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));return inputs;}
    private static ClassNode production(String owner)throws Exception{if(runtime==null){var loaded=new HashMap<String,byte[]>();try(var zip=new ZipFile(System.getProperty("legacybridge.runtimeArtifact"))){for(String name:List.of(OP,CLIENT))loaded.put(name,bytes(zip,name+".class"));}runtime=loaded;}return read(runtime.get(owner));}
    private static byte[] bytes(ZipFile zip,String name)throws Exception{var entry=zip.getEntry(name);assertNotNull(entry,name);assertTrue(entry.getSize()>0&&entry.getSize()<2*1024*1024);try(var in=zip.getInputStream(entry)){byte[] bytes=in.readNBytes(2*1024*1024+1);assertTrue(bytes.length<=2*1024*1024);return bytes;}}
    private static ClassNode read(byte[] bytes){var node=new ClassNode();new ClassReader(bytes).accept(node,0);return node;}
    private static Set<String> identities(ClassNode node){var names=new TreeSet<String>();node.methods.forEach(m->assertTrue(names.add(m.name+m.desc)));return names;}
    private static MethodNode operation(ClassNode owner){return method(owner,"equip",equipDesc());}
    private static MethodNode method(ClassNode owner,String name,String desc){var methods=owner.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).toList();assertEquals(1,methods.size(),owner.name+"."+name+desc);return methods.get(0);}
    private static MethodNode copy(MethodNode original){var method=new MethodNode(ASM9,original.access,original.name,original.desc,original.signature,original.exceptions.toArray(String[]::new));original.accept(method);return method;}
    private static List<AbstractInsnNode> real(MethodNode method){return Arrays.stream(method.instructions.toArray()).filter(n->n.getOpcode()>=0).toList();}
    private static Object pop(List<Object> stack){return stack.remove(stack.size()-1);}private static String ref(String owner){return "L"+owner+";";}
    private static String equipDesc(){return "("+ref(PLAYER)+ref(SLOT)+ref(STACK)+")V";}
}
