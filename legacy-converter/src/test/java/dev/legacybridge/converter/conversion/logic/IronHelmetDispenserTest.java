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

/** Actual native/archive and reobfuscated operation bytes only; no game/original class loading.
 * The finite interpreter qualifies query arguments, returned-list selection and effect boundaries.
 * It does NOT qualify native world query ordering, chunks, gameplay, copied NBT/capabilities,
 * sound/events or multiplayer. The separately tested HEAD operation remains a delegation boundary. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class IronHelmetDispenserTest {
    private static final String OP="dev/legacybridge/runtime/compat/item/LegacyIronHelmetDispenser",
        HELMET="dev/legacybridge/runtime/compat/item/LegacyIronHelmetItem", HEAD_EFFECT="dev/legacybridge/runtime/compat/item/LegacyIronHelmetHeadEquipEffects",
        ARMOR="net/minecraft/world/item/ArmorItem", STACK="net/minecraft/world/item/ItemStack", ITEM="net/minecraft/world/item/Item",
        PLAYER="net/minecraft/world/entity/player/Player", SERVER="net/minecraft/server/level/ServerPlayer", ZOMBIE="net/minecraft/world/entity/monster/Zombie",
        MOB="net/minecraft/world/entity/Mob", LIVING="net/minecraft/world/entity/LivingEntity", ENTITY="net/minecraft/world/entity/Entity",
        SOURCE="net/minecraft/core/BlockSource", POS="net/minecraft/core/BlockPos", STATE="net/minecraft/world/level/block/state/BlockState",
        STATE_HOLDER="net/minecraft/world/level/block/state/StateHolder", DISPENSER="net/minecraft/world/level/block/DispenserBlock",
        DIRECTION="net/minecraft/core/Direction", DIRECTION_PROPERTY="net/minecraft/world/level/block/state/properties/DirectionProperty",
        PROPERTY="net/minecraft/world/level/block/state/properties/Property", LEVEL="net/minecraft/server/level/ServerLevel",
        BASE_LEVEL="net/minecraft/world/level/Level", ENTITY_GETTER="net/minecraft/world/level/EntityGetter", BOX="net/minecraft/world/phys/AABB",
        SELECTORS="net/minecraft/world/entity/EntitySelector", SELECTOR=SELECTORS+"$MobCanWearArmorEntitySelector",
        SLOT="net/minecraft/world/entity/EquipmentSlot", LIST="net/minecraft/core/NonNullList";
    private static MappingInputs inputs;private static Side source,target;private static byte[] operationBytes;
    @AfterAll static void release()throws Exception{try{if(source!=null)source.close();}finally{try{if(target!=null)target.close();}finally{source=null;target=null;inputs=null;operationBytes=null;}}}

    @Test void compiledOperationHasOnlyTheClosedPublicEntryPointAndNoClientOrHiddenState()throws Exception{
        ClassNode node=operationClass();assertEquals(V17,node.version);assertEquals(ACC_PUBLIC|ACC_FINAL|ACC_SUPER,node.access);assertEquals("java/lang/Object",node.superName);
        assertTrue(node.fields.isEmpty());assertTrue(node.interfaces.isEmpty());assertEquals(2,node.methods.size());assertEquals(ACC_PRIVATE,find(node,"<init>","()V").access);
        assertEquals(ACC_PUBLIC|ACC_STATIC,operation(node).access);assertTrue(operation(node).tryCatchBlocks.isEmpty());BytecodeValidator.validate(operationBytes);
        assertFalse(new String(operationBytes,StandardCharsets.ISO_8859_1).contains("net/minecraft/client/"));validate(operation(node),side(false),true);
        var calls=real(operation(node)).stream().filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        Side api=side(false);assertTrue(calls.stream().noneMatch(m->List.of("setItemSlot","verifyEquippedItem","onEquipItem","registerBehavior").contains(api.readName(m,true))));
        assertEquals(1,calls.stream().filter(m->m.owner.equals(HEAD_EFFECT)).count());
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void exactNativeArchivePinsBindReviewedCode(boolean old)throws Exception{Side api=side(old);var digest=MessageDigest.getInstance("SHA-256");
        try(var in=Files.newInputStream(api.path)){byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))>=0;)digest.update(buffer,0,n);}
        assertEquals(old?"1a3e71d6ccb8461fe9d94f4c63551fe52a8c0c01116572c49a0c3253d0506b03":"b820f2243c37f060b17678f3a23c868da14d35ebf82ee47b4035b1718c0952ee",HexFormat.of().formatHex(digest.digest()));}

    @Test void declarationsAndZombieInheritanceUseRealAccessibleNativeOperations()throws Exception{
        for(Side api:List.of(side(true),side(false))){
            api.exactMethod(ARMOR,"dispenseArmor",dispenseDesc(),ACC_PUBLIC|ACC_STATIC);
            api.exactMethod(SOURCE,"getPos","()"+ref(POS),ACC_PUBLIC|ACC_ABSTRACT);
            api.exactMethod(SOURCE,"getBlockState","()"+ref(STATE),ACC_PUBLIC|ACC_ABSTRACT);
            api.exactMethod(SOURCE,"getLevel","()"+ref(LEVEL),ACC_PUBLIC|ACC_ABSTRACT);
            api.exactMethod(STATE_HOLDER,"getValue","("+ref(PROPERTY)+")Ljava/lang/Comparable;",ACC_PUBLIC);
            api.exactMethod(STACK,"split","(I)"+ref(STACK),ACC_PUBLIC);
            api.exactMethod(MOB,"getArmorSlots","()Ljava/lang/Iterable;",ACC_PUBLIC);
            api.exactMethod(MOB,"setDropChance","("+ref(SLOT)+"F)V",ACC_PUBLIC);
            api.exactMethod(MOB,"setPersistenceRequired","()V",ACC_PUBLIC);
            api.exactMethod(LIST,"set","(ILjava/lang/Object;)Ljava/lang/Object;",ACC_PUBLIC);
            api.exactMethod(PLAYER,"canTakeItem","("+ref(STACK)+")Z",ACC_PUBLIC);
            api.exactMethod(MOB,"canTakeItem","("+ref(STACK)+")Z",ACC_PUBLIC);
            api.exactMethod(api.old?BASE_LEVEL:ENTITY_GETTER,"getEntitiesOfClass",queryDesc(),ACC_PUBLIC);
            assertEquals(api.old?0:ACC_INTERFACE,api.type(api.old?BASE_LEVEL:ENTITY_GETTER).access()&ACC_INTERFACE);
            String owner=api.ns.productionClass(ZOMBIE);Set<String> seen=new HashSet<>();
            while(!owner.equals(api.ns.productionClass(MOB))){assertTrue(seen.add(owner));var type=api.api.getClass(owner);assertNotNull(type);
                for(String name:List.of("getArmorSlots","setItemSlot","getItemBySlot","canTakeItem","setDropChance","setPersistenceRequired")){
                    String desc=switch(name){case "getArmorSlots"->"()Ljava/lang/Iterable;";case "setItemSlot"->"("+ref(SLOT)+ref(STACK)+")V";
                        case "getItemBySlot"->"("+ref(SLOT)+")"+ref(STACK);case "canTakeItem"->"("+ref(STACK)+")Z";case "setDropChance"->"("+ref(SLOT)+"F)V";default->"()V";};
                    assertTrue(type.methods().stream().noneMatch(m->m.name().equals(api.name(MOB,name,desc))&&m.descriptor().equals(api.desc(desc))),type.name()+" shadows "+name);}
                owner=type.superName();}
            MethodNode getter=api.nativeMethod(MOB,"getArmorSlots","()Ljava/lang/Iterable;");var body=real(getter);
            assertEquals(List.of(ALOAD,GETFIELD,ARETURN),body.stream().map(AbstractInsnNode::getOpcode).toList());
            assertEquals("armorItems",((FieldInsnNode)body.get(1)).name);assertEquals(api.ns.productionClass(MOB),((FieldInsnNode)body.get(1)).owner);
        }
        var setter=side(true).nativeMethod(MOB,"setItemSlot","("+ref(SLOT)+ref(STACK)+")V");
        var calls=real(setter).stream().filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertTrue(calls.stream().allMatch(m->Set.of("getType","ordinal","getIndex","set").contains(m.name)),"The source mob setter contains no player sound or target equipment effects");
        var armorField=real(setter).stream().filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast).filter(f->f.name.equals("armorItems")).findFirst().orElseThrow();
        var tail=new ArrayList<AbstractInsnNode>();for(AbstractInsnNode at=armorField;at!=null;at=at.getNext())if(at.getOpcode()>=0)tail.add(at);
        assertEquals(List.of(GETFIELD,ALOAD,INVOKEVIRTUAL,ALOAD,INVOKEVIRTUAL,POP,RETURN),tail.stream().map(AbstractInsnNode::getOpcode).toList());
        assertEquals("getIndex",((MethodInsnNode)tail.get(2)).name);assertEquals("set",((MethodInsnNode)tail.get(4)).name);
    }

    static Stream<Arguments> successes(){return Stream.of(SERVER,ZOMBIE).flatMap(receiver->Stream.of(1,2,64).map(count->Arguments.of(receiver,count)));}
    @ParameterizedTest @MethodSource("successes")
    void actualOperationPreservesFirstReceiverSplitIdentityAndFiniteEffects(String receiver,int count)throws Exception{
        Context old=new Context(receiver,count),modern=new Context(receiver,count);old.entities.add(new Receiver("second",ZOMBIE));modern.entities.add(new Receiver("second",ZOMBIE));
        assertEquals(1,runOriginal(old));assertEquals(1,runConverted(modern));assertEquals(old.effects,modern.effects);
        assertEquals(count-1,modern.input.count);assertNotSame(modern.input,modern.split);assertEquals(1,modern.split.count);assertEquals(modern.input.payload,modern.split.payload);
        assertSame(modern.split,modern.entities.get(0).armor.values[3]);assertEquals(0,modern.entities.get(1).writes);assertEquals(1,modern.splits);
        assertEquals(receiver.equals(ZOMBIE)?2.0f:0.0f,modern.entities.get(0).dropChance);assertEquals(receiver.equals(ZOMBIE),modern.entities.get(0).persistent);
        assertEquals(receiver.equals(SERVER)?1:0,modern.playerDelegations);assertEquals(1,modern.queryCalls);assertTrue(modern.input.count+modern.split.count==count);
        assertEquals(receiver.equals(ZOMBIE)?List.of("query","getArmorSlots","split","equip","dropChance","persistence"):List.of("query","split","equip"),modern.calls);
    }

    @ParameterizedTest @ValueSource(strings={"NORTH","SOUTH","EAST","WEST","UP","DOWN"})
    void realFacingExpressionQueriesExactlyTheAdjacentUnitBox(String facing)throws Exception{
        Context context=new Context(ZOMBIE,1);context.facing=facing;context.entities.clear();assertEquals(0,runConverted(context));
        assertEquals(new Point(11,23,37).relative(facing),context.queriedBox.origin);assertEquals(0,context.splits);assertEquals(1,context.input.count);
    }

    @ParameterizedTest @ValueSource(strings={"none","dead","spectator","occupied","cannotPickUp"})
    void noEligibleReceiverReturnsFalseWithoutConsumption(String condition)throws Exception{
        Context old=new Context(ZOMBIE,2),modern=new Context(ZOMBIE,2);
        for(Context context:List.of(old,modern))switch(condition){case "none"->context.entities.clear();case "dead"->context.entities.get(0).alive=false;
            case "spectator"->context.entities.get(0).spectator=true;case "occupied"->context.entities.get(0).armor.values[3]=new StackValue(1,"previous");
            case "cannotPickUp"->context.entities.get(0).canPickUp=false;default->throw new AssertionError(condition);}
        assertEquals(0,runOriginal(old));assertEquals(0,runConverted(modern));assertEquals(old.effects,modern.effects);assertEquals(2,modern.input.count);assertEquals(0,modern.splits);
    }

    @Test void playerNativeEligibilityDoesNotRequireMobLootPickup()throws Exception{
        Context old=new Context(SERVER,1),modern=new Context(SERVER,1);old.entities.get(0).canPickUp=false;modern.entities.get(0).canPickUp=false;
        assertEquals(1,runOriginal(old));assertEquals(1,runConverted(modern));assertEquals(old.effects,modern.effects);
    }

    @Test void sourceAndTargetSelectorAndCanTakeBodiesAreInterpretedNotInventedPredicates()throws Exception{
        for(Side api:List.of(side(true),side(false))){
            var generic=real(api.nativeMethod(LIVING,"canTakeItem","("+ref(STACK)+")Z"));assertEquals(List.of(ICONST_0,IRETURN),generic.stream().map(AbstractInsnNode::getOpcode).toList());
            var playerCalls=real(api.nativeMethod(PLAYER,"canTakeItem","("+ref(STACK)+")Z")).stream().filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).map(m->api.readName(m,false)).toList();
            assertEquals(List.of("getEquipmentSlotForItem","getItemBySlot","isEmpty"),playerCalls);
            var mobCalls=real(api.nativeMethod(MOB,"canTakeItem","("+ref(STACK)+")Z")).stream().filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).map(m->api.readName(m,false)).toList();
            assertEquals(List.of("getEquipmentSlotForItem","getItemBySlot","isEmpty","canPickUpLoot"),mobCalls);
        }
    }

    @ParameterizedTest @ValueSource(strings={"unknownFirst","nonHead","wrongArmorType","shortArmorList","nullArmorList","foreignStack","emptyStack","nullSource","nullStack"})
    void unsupportedIsNotFalseAndCannotConsumeOrSelectAnotherReceiver(String change)throws Exception{
        Context context=new Context(ZOMBIE,2);context.entities.add(new Receiver("second",ZOMBIE));Object sourceArg=context,stackArg=context.input;
        switch(change){case "unknownFirst"->context.entities.get(0).type="example/UnknownMob";case "nonHead"->context.slot="CHEST";
            case "wrongArmorType"->context.entities.get(0).armor.type="example/CustomArmorList";case "shortArmorList"->context.entities.get(0).armor.size=3;case "nullArmorList"->context.nullArmorSlots=true;
            case "foreignStack"->context.input.item="foreign";case "emptyStack"->context.input.count=0;case "nullSource"->sourceArg=null;case "nullStack"->stackArg=null;default->throw new AssertionError(change);}
        Object passedSource=sourceArg,passedStack=stackArg;int before=context.input.count;
        assertThrows(Rejected.class,()->execute(operation(operationClass()),side(false),true,new Object[]{passedSource,passedStack},context));
        assertEquals(before,context.input.count);assertEquals(0,context.splits);assertEquals(0,context.entities.get(0).writes);assertEquals(0,context.entities.get(1).writes);
        assertEquals(List.of(),context.effects);assertFalse(context.calls.contains("equip"));
        if(Set.of("foreignStack","emptyStack","nullSource","nullStack").contains(change)){
            assertEquals(0,context.queryCalls,"Invalid inputs must fail before the native query");assertTrue(context.calls.isEmpty());
        }
    }

    @ParameterizedTest @ValueSource(strings={"query","split","equip","dropChance","persistence"})
    void exceptionsPreserveConsumptionAndPriorEffectOrdering(String fail)throws Exception{
        Context old=new Context(ZOMBIE,2),modern=new Context(ZOMBIE,2);old.failAt=fail;modern.failAt=fail;
        assertThrows(EndpointFailure.class,()->runOriginal(old));assertThrows(EndpointFailure.class,()->runConverted(modern));
        assertEquals(old.effects,modern.effects);assertEquals(old.input.count,modern.input.count);assertEquals(old.entities.get(0).writes,modern.entities.get(0).writes);
        assertEquals(old.entities.get(0).dropChance,modern.entities.get(0).dropChance);assertEquals(old.entities.get(0).persistent,modern.entities.get(0).persistent);
    }

    @Test void newArmorGetterFailurePrecedesSplitAndIndependentCallsDoNotShareState()throws Exception{
        Context first=new Context(ZOMBIE,2);first.failAt="getArmorSlots";assertThrows(EndpointFailure.class,()->runConverted(first));assertEquals(2,first.input.count);assertEquals(0,first.splits);
        Context second=new Context(ZOMBIE,2);assertEquals(1,runConverted(second));assertNotSame(first.input,second.input);assertNotSame(first.entities.get(0).armor,second.entities.get(0).armor);
        assertEquals(0,first.entities.get(0).writes);assertEquals(1,second.entities.get(0).writes);
    }

    @ParameterizedTest @ValueSource(strings={"unknownCall","splitTwo","listGetSecond","queryOpcode","queryDescriptor","queryOwner","queryInterface","unknownField","foreignReceiverLiteral","dropChanceOne"})
    void unreviewedBytecodeOrSemanticMutationsCannotPass(String change)throws Exception{
        MethodNode body=copy(operation(operationClass()));Side api=side(false);var instructions=real(body);
        var calls=instructions.stream().filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        var query=calls.stream().filter(m->api.readName(m,true).equals("getEntitiesOfClass")).findFirst().orElseThrow();
        switch(change){case "unknownCall"->query.name="unreviewed";case "queryOpcode"->query.setOpcode(INVOKESTATIC);case "queryDescriptor"->query.desc="()Ljava/util/List;";
            case "queryOwner"->query.owner=api.ns.productionClass(BASE_LEVEL);case "queryInterface"->query.itf=true;
            case "splitTwo"->{for(var call:calls)if(api.readName(call,true).equals("split"))body.instructions.set(previousReal(call),new InsnNode(ICONST_2));}
            case "listGetSecond"->{var get=calls.stream().filter(m->m.owner.equals("java/util/List")&&m.name.equals("get")).findFirst().orElseThrow();body.instructions.set(previousReal(get),new InsnNode(ICONST_1));}
            case "unknownField"->{var f=instructions.stream().filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast).findFirst().orElseThrow();f.name="unreviewed";}
            case "foreignReceiverLiteral"->{var l=instructions.stream().filter(LdcInsnNode.class::isInstance).map(LdcInsnNode.class::cast).filter(n->Type.getObjectType(ZOMBIE).equals(n.cst)).findFirst().orElseThrow();l.cst=Type.getObjectType(MOB);}
            case "dropChanceOne"->{var drop=calls.stream().filter(m->api.readName(m,true).equals("setDropChance")).findFirst().orElseThrow();body.instructions.set(previousReal(drop),new InsnNode(FCONST_1));}
            default->throw new AssertionError(change);}
        assertThrows(AssertionError.class,()->{validate(body,api,true);Context context=new Context(ZOMBIE,2);context.entities.add(new Receiver("second",ZOMBIE));
            assertEquals(1,execute(body,api,true,new Object[]{context,context.input},context));assertEquals(1,context.splits);assertEquals(1,context.input.count);assertEquals(1,context.entities.get(0).writes);
            assertEquals(0,context.entities.get(1).writes);assertEquals(2.0f,context.entities.get(0).dropChance);});
    }

    private static final class StackValue {int count;String item="helmet";final String payload;StackValue(int count,String payload){this.count=count;this.payload=payload;}}
    private static final class ArmorList {String type=LIST;int size=4;final StackValue[] values={new StackValue(0,"feet"),new StackValue(0,"legs"),new StackValue(0,"chest"),new StackValue(0,"head")};}
    private static final class Receiver {final String id;String type;boolean alive=true,spectator=false,canPickUp=true,persistent;final ArmorList armor=new ArmorList();int writes;float dropChance;
        Receiver(String id,String type){this.id=id;this.type=type;}boolean player(){return type.equals(SERVER);}}
    private static final class Context {
        final StackValue input;final List<Receiver> entities=new ArrayList<>();final List<String> effects=new ArrayList<>(),calls=new ArrayList<>();
        String facing="NORTH",slot="HEAD",failAt;StackValue split;BoxValue queriedBox;int splits,queryCalls,playerDelegations;boolean nullArmorSlots;
        Context(String receiver,int count){input=new StackValue(count,"tag-and-capabilities-marker");entities.add(new Receiver("first",receiver));}
        void call(String name){calls.add(name);if(name.equals(failAt))throw new EndpointFailure(name);}
    }
    private record Point(int x,int y,int z){Point relative(String direction){return switch(direction){case "NORTH"->new Point(x,y,z-1);case "SOUTH"->new Point(x,y,z+1);case "EAST"->new Point(x+1,y,z);case "WEST"->new Point(x-1,y,z);case "UP"->new Point(x,y+1,z);case "DOWN"->new Point(x,y-1,z);default->throw new AssertionError(direction);};}}
    private static final class BoxValue {final Point origin;BoxValue(Point origin){this.origin=origin;}}
    private record TypeValue(String owner){}
    private static final class Constructed {final String owner;Object value;Constructed(String owner){this.owner=owner;}}
    private record PredicateValue(StackValue input){}
    private static final class Rejected extends RuntimeException {Rejected(String message){super(message);}}
    private static final class EndpointFailure extends RuntimeException {EndpointFailure(String message){super(message);}}

    private static int runOriginal(Context c)throws Exception{return (Integer)execute(side(true).nativeMethod(ARMOR,"dispenseArmor",dispenseDesc()),side(true),false,new Object[]{c,c.input},c);}
    private static int runConverted(Context c)throws Exception{return (Integer)execute(operation(operationClass()),side(false),true,new Object[]{c,c.input},c);}
    private static Object execute(MethodNode method,Side api,boolean production,Object[] arguments,Context context)throws Exception{
        validate(method,api,production);Object[] locals=new Object[Math.max(method.maxLocals,arguments.length)];System.arraycopy(arguments,0,locals,0,arguments.length);var stack=new ArrayList<Object>();
        for(int pc=0,steps=0;pc<method.instructions.size()&&steps++<768;pc++){
            var n=method.instructions.get(pc);int op=n.getOpcode();if(op<0)continue;
            if(n instanceof VarInsnNode v){if(op==ALOAD||op==ILOAD)stack.add(locals[v.var]);else if(op==ASTORE||op==ISTORE)locals[v.var]=pop(stack);else throw new AssertionError(op);}
            else if(n instanceof LdcInsnNode l)stack.add(l.cst instanceof Type t?new TypeValue(api.canonical(t.getInternalName())):l.cst);
            else if(n instanceof FieldInsnNode f){String name=api.readField(f,production);if(op==GETSTATIC)stack.add(name);else{assertEquals(SELECTOR,api.canonical(f.owner));Constructed selector=(Constructed)pop(stack);stack.add(selector.value);}}
            else if(n instanceof TypeInsnNode t){String type=api.canonical(t.desc);if(op==NEW)stack.add(new Constructed(type));
                else if(op==CHECKCAST){Object value=stack.get(stack.size()-1);assertTrue(castFits(type,value),"Wrong cast "+type);}
                else if(op==INSTANCEOF){Object value=pop(stack);stack.add(castFits(type,value)?1:0);}else throw new AssertionError(op);}
            else if(n instanceof MethodInsnNode m){String name=api.readName(m,production),owner=api.canonical(m.owner);Object value=null;boolean push=true;
                if(name.equals("<init>")){if(owner.equals(BOX)){Point point=(Point)pop(stack);((Constructed)pop(stack)).value=new BoxValue(point);}
                    else if(owner.equals(SELECTOR)){StackValue input=(StackValue)pop(stack);assertSame(context.input,input);((Constructed)pop(stack)).value=input;}
                    else {String message=(String)pop(stack);((Constructed)pop(stack)).value=message;}push=false;}
                else if(owner.equals("java/util/Objects")){assertInstanceOf(String.class,pop(stack));value=pop(stack);if(value==null)throw new Rejected("null");}
                else if(name.equals("getClass")){Object object=pop(stack);value=new TypeValue(object instanceof Receiver r?r.type:((ArmorList)object).type);}
                else if(name.equals("equip")&&owner.equals(HEAD_EFFECT)){StackValue received=(StackValue)pop(stack);assertEquals("HEAD",pop(stack));Receiver receiver=(Receiver)pop(stack);assertTrue(receiver.player());context.playerDelegations++;
                    equip(context,receiver,received);push=false;}
                else switch(name){
                    case "getPos"->{assertSame(context,pop(stack));value=new Point(11,23,37);}
                    case "getBlockState","getLevel"->{assertSame(context,pop(stack));value=context;}
                    case "getValue"->{assertEquals("FACING",pop(stack));assertSame(context,pop(stack));value=context.facing;}
                    case "relative"->{String direction=(String)pop(stack);value=((Point)pop(stack)).relative(direction);}
                    case "and"->{Constructed selector=(Constructed)pop(stack);assertEquals(SELECTOR,selector.owner);assertSame(context.input,selector.value);assertEquals("NO_SPECTATORS",pop(stack));value=new PredicateValue(context.input);}
                    case "getEntitiesOfClass"->{PredicateValue predicate=(PredicateValue)pop(stack);assertSame(context.input,predicate.input);Constructed box=(Constructed)pop(stack);assertEquals(BOX,box.owner);
                        context.queriedBox=(BoxValue)box.value;assertEquals(new Point(11,23,37).relative(context.facing),context.queriedBox.origin);assertEquals(new TypeValue(LIVING),pop(stack));assertSame(context,pop(stack));
                        context.call("query");context.queryCalls++;var found=new ArrayList<Receiver>();for(Receiver receiver:context.entities){Constructed selector=new Constructed(SELECTOR);selector.value=context.input;
                            if(((Integer)execute(api.noSpectators(),api,false,new Object[]{receiver},context))!=0
                                    &&((Integer)execute(api.nativeMethod(SELECTOR,"test","("+ref(ENTITY)+")Z"),api,false,new Object[]{selector,receiver},context))!=0)found.add(receiver);}value=found;}
                    case "isEmpty"->{Object object=pop(stack);value=object instanceof List<?> list?(list.isEmpty()?1:0):((StackValue)object).count<=0?1:0;}
                    case "getItem"->{assertSame(context.input,stack.get(stack.size()-1));value=((StackValue)pop(stack)).item;}
                    case "get"->{assertEquals(0,pop(stack));value=((List<?>)pop(stack)).get(0);}
                    case "getEquipmentSlotForItem"->{assertSame(context.input,pop(stack));value=context.slot;}
                    case "split"->{assertEquals(1,pop(stack));assertSame(context.input,pop(stack));context.call("split");int count=Math.min(1,context.input.count);context.input.count-=count;
                        context.split=new StackValue(count,context.input.payload);context.splits++;context.effects.add("split:1");value=context.split;}
                    case "setItemSlot"->{StackValue received=(StackValue)pop(stack);assertEquals(context.slot,pop(stack));Receiver receiver=(Receiver)pop(stack);equip(context,receiver,received);push=false;}
                    case "getArmorSlots"->{Receiver receiver=(Receiver)pop(stack);assertEquals(ZOMBIE,receiver.type);context.call("getArmorSlots");value=context.nullArmorSlots?null:receiver.armor;}
                    case "size"->value=((ArmorList)pop(stack)).size;
                    case "getIndex"->{assertEquals("HEAD",pop(stack));value=3;}
                    case "set"->{StackValue received=(StackValue)pop(stack);assertEquals(3,pop(stack));ArmorList armor=(ArmorList)pop(stack);Receiver receiver=context.entities.stream().filter(e->e.armor==armor).findFirst().orElseThrow();
                        value=armor.values[3];equip(context,receiver,received);}
                    case "setDropChance"->{assertEquals(2.0f,pop(stack));assertEquals("HEAD",pop(stack));Receiver receiver=(Receiver)pop(stack);context.call("dropChance");receiver.dropChance=2.0f;context.effects.add(receiver.id+":drop:2.0");push=false;}
                    case "setPersistenceRequired"->{Receiver receiver=(Receiver)pop(stack);context.call("persistence");receiver.persistent=true;context.effects.add(receiver.id+":persistent");push=false;}
                    case "isAlive"->value=((Receiver)pop(stack)).alive?1:0;
                    case "isSpectator"->value=((Receiver)pop(stack)).spectator?1:0;
                    case "canTakeItem"->{assertSame(context.input,pop(stack));Receiver receiver=(Receiver)pop(stack);value=execute(api.nativeMethod(receiver.player()?PLAYER:MOB,"canTakeItem","("+ref(STACK)+")Z"),api,false,new Object[]{receiver,context.input},context);}
                    case "getItemBySlot"->{assertEquals(context.slot,pop(stack));value=((Receiver)pop(stack)).armor.values[3];}
                    case "canPickUpLoot"->value=((Receiver)pop(stack)).canPickUp?1:0;
                    default->throw new AssertionError("Unknown endpoint "+owner+"."+name);
                }if(push)stack.add(value);
            }else if(n instanceof JumpInsnNode j){boolean jump=switch(op){case GOTO->true;case IFEQ->((Integer)pop(stack))==0;case IFNE->((Integer)pop(stack))!=0;
                    case IF_ACMPEQ->{Object b=pop(stack),a=pop(stack);yield equalIdentity(a,b);}case IF_ACMPNE->{Object b=pop(stack),a=pop(stack);yield !equalIdentity(a,b);}
                    case IF_ICMPEQ->{Object b=pop(stack),a=pop(stack);yield a.equals(b);}case IF_ICMPNE->{Object b=pop(stack),a=pop(stack);yield !a.equals(b);}
                    case IFNULL->pop(stack)==null;case IFNONNULL->pop(stack)!=null;default->throw new AssertionError(op);};if(jump)pc=method.instructions.indexOf(j.label)-1;
            }else if(n instanceof IntInsnNode i){assertEquals(BIPUSH,op);stack.add(i.operand);}
            else switch(op){case ICONST_0->stack.add(0);case ICONST_1->stack.add(1);case ICONST_2->stack.add(2);case ICONST_4->stack.add(4);case FCONST_1->stack.add(1.0f);case FCONST_2->stack.add(2.0f);case DUP->stack.add(stack.get(stack.size()-1));case POP->pop(stack);
                case ATHROW->{Constructed exception=(Constructed)pop(stack);throw new Rejected(exception.owner+":"+exception.value);}case IRETURN->{Object result=pop(stack);assertTrue(stack.isEmpty());return result;}default->throw new AssertionError("Unknown opcode "+op);}
        }throw new AssertionError("Finite interpreter exhausted");
    }
    private static void equip(Context context,Receiver receiver,StackValue received){assertSame(context.split,received);assertNotSame(context.input,received);assertEquals(1,received.count);context.call("equip");receiver.armor.values[3]=received;receiver.writes++;context.effects.add(receiver.id+(receiver.player()?":player-equip":":silent-mob-equip"));}
    private static boolean castFits(String type,Object value){return switch(type){case HELMET->"helmet".equals(value);case LIVING,ENTITY,MOB->value instanceof Receiver r&&(!type.equals(MOB)||!r.player());case SERVER,PLAYER->value instanceof Receiver r&&r.player();case ZOMBIE->value instanceof Receiver r&&r.type.equals(ZOMBIE);case LIST->value instanceof ArmorList;case DIRECTION->value instanceof String;default->throw new AssertionError(type);};}
    private static boolean equalIdentity(Object a,Object b){return a==b||a instanceof String&&a.equals(b)||a instanceof TypeValue&&a.equals(b);}

    private record Call(String owner,String oldDeclaration,String newDeclaration,String name,String descriptor,int opcode){}
    private static Call c(String owner,String name,String descriptor,int opcode){return new Call(owner,owner,owner,name,descriptor,opcode);}
    private static final List<Call> CALLS=List.of(
        c(SOURCE,"getPos","()"+ref(POS),INVOKEINTERFACE),c(SOURCE,"getBlockState","()"+ref(STATE),INVOKEINTERFACE),c(SOURCE,"getLevel","()"+ref(LEVEL),INVOKEINTERFACE),
        new Call(STATE,STATE_HOLDER,STATE_HOLDER,"getValue","("+ref(PROPERTY)+")Ljava/lang/Comparable;",INVOKEVIRTUAL),c(POS,"relative","("+ref(DIRECTION)+")"+ref(POS),INVOKEVIRTUAL),
        c(BOX,"<init>","("+ref(POS)+")V",INVOKESPECIAL),c(SELECTOR,"<init>","("+ref(STACK)+")V",INVOKESPECIAL),c("java/util/function/Predicate","and","(Ljava/util/function/Predicate;)Ljava/util/function/Predicate;",INVOKEINTERFACE),
        new Call(LEVEL,BASE_LEVEL,ENTITY_GETTER,"getEntitiesOfClass",queryDesc(),INVOKEVIRTUAL),c("java/util/List","isEmpty","()Z",INVOKEINTERFACE),c("java/util/List","get","(I)Ljava/lang/Object;",INVOKEINTERFACE),
        new Call(MOB,MOB,LIVING,"getEquipmentSlotForItem","("+ref(STACK)+")"+ref(SLOT),INVOKESTATIC),c(STACK,"split","(I)"+ref(STACK),INVOKEVIRTUAL),c(STACK,"isEmpty","()Z",INVOKEVIRTUAL),c(STACK,"getItem","()"+ref(ITEM),INVOKEVIRTUAL),
        c(LIVING,"setItemSlot","("+ref(SLOT)+ref(STACK)+")V",INVOKEVIRTUAL),c(MOB,"setDropChance","("+ref(SLOT)+"F)V",INVOKEVIRTUAL),c(MOB,"setPersistenceRequired","()V",INVOKEVIRTUAL),
        new Call(ZOMBIE,MOB,MOB,"getArmorSlots","()Ljava/lang/Iterable;",INVOKEVIRTUAL),new Call(ZOMBIE,MOB,MOB,"setDropChance","("+ref(SLOT)+"F)V",INVOKEVIRTUAL),new Call(ZOMBIE,MOB,MOB,"setPersistenceRequired","()V",INVOKEVIRTUAL),
        c(LIST,"size","()I",INVOKEVIRTUAL),c(LIST,"set","(ILjava/lang/Object;)Ljava/lang/Object;",INVOKEVIRTUAL),c(SLOT,"getIndex","()I",INVOKEVIRTUAL),
        c("java/lang/Object","getClass","()Ljava/lang/Class;",INVOKEVIRTUAL),c("java/util/Objects","requireNonNull","(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",INVOKESTATIC),
        c("java/lang/IllegalArgumentException","<init>","(Ljava/lang/String;)V",INVOKESPECIAL),c("java/lang/UnsupportedOperationException","<init>","(Ljava/lang/String;)V",INVOKESPECIAL),
        c(HEAD_EFFECT,"equip","("+ref(PLAYER)+ref(SLOT)+ref(STACK)+")V",INVOKESTATIC),c(ENTITY,"isAlive","()Z",INVOKEVIRTUAL),c(ENTITY,"isSpectator","()Z",INVOKEVIRTUAL),c(LIVING,"canTakeItem","("+ref(STACK)+")Z",INVOKEVIRTUAL),
        c(PLAYER,"getItemBySlot","("+ref(SLOT)+")"+ref(STACK),INVOKEVIRTUAL),c(MOB,"getItemBySlot","("+ref(SLOT)+")"+ref(STACK),INVOKEVIRTUAL),c(MOB,"canPickUpLoot","()Z",INVOKEVIRTUAL));
    private record Field(String owner,String name,String descriptor,int opcode){}
    private static final List<Field> FIELDS=List.of(new Field(DISPENSER,"FACING",ref(DIRECTION_PROPERTY),GETSTATIC),new Field(SELECTORS,"NO_SPECTATORS","Ljava/util/function/Predicate;",GETSTATIC),
        new Field(SLOT,"HEAD",ref(SLOT),GETSTATIC),new Field(SELECTOR,"itemStack",ref(STACK),GETFIELD));
    private static void validate(MethodNode method,Side api,boolean production){assertTrue(method.tryCatchBlocks.isEmpty());assertTrue(method.instructions.size()<768);assertTrue(method.maxLocals<24&&method.maxStack<24);
        for(var n:method.instructions){if(n.getOpcode()<0)continue;if(n instanceof MethodInsnNode m)api.readName(m,production);else if(n instanceof FieldInsnNode f)api.readField(f,production);
            else if(n instanceof TypeInsnNode t)assertTrue(Set.of(BOX,SELECTOR,LIVING,ENTITY,MOB,ZOMBIE,SERVER,PLAYER,LIST,DIRECTION,HELMET,"java/lang/IllegalArgumentException","java/lang/UnsupportedOperationException").contains(api.canonical(t.desc)),t.desc);
            else if(n instanceof LdcInsnNode l){if(l.cst instanceof Type t)assertTrue(Set.of(LIVING,SERVER,ZOMBIE,LIST).contains(api.canonical(t.getInternalName())),"Unreviewed type literal "+t);else assertInstanceOf(String.class,l.cst);}
            else assertTrue(n instanceof VarInsnNode||n instanceof JumpInsnNode||n instanceof InsnNode||n instanceof IntInsnNode,"Unreviewed instruction "+n);}}

    private static final class Side implements AutoCloseable {
        final boolean old;final LogicApiNamespace ns;final ApiIndex api;final Path path;final ZipFile zip;final Map<String,ClassNode> classes=new HashMap<>();
        Side(boolean old)throws Exception{this.old=old;var d=data();ns=new LogicApiNamespace(old?d.legacyOfficial():d.targetOfficial(),old?d.legacySrg():d.targetSrg(),old?d.legacyApi():d.targetApi(),old);
            var roots=new TreeSet<String>();for(String owner:List.of(ARMOR,STACK,SERVER,ZOMBIE,SOURCE,POS,STATE,STATE_HOLDER,DISPENSER,LEVEL,ENTITY_GETTER,SELECTOR,SELECTORS,SLOT,LIST))roots.add(ns.productionClass(owner));api=ns.normalize(roots);
            var p=new Properties();try(var in=Files.newInputStream(Path.of(System.getProperty("legacybridge.logicMappingProfile")))){p.load(in);}path=Path.of(p.getProperty(old?"legacy.apiJars":"target.apiJars").split(";")[0]);zip=new ZipFile(path.toFile());}
        String canonical(String owner){return ns.canonicalClass(owner);}String desc(String descriptor){return ns.productionDescriptor(descriptor);}
        String owner(String owner){return owner.startsWith("net/minecraft/")?ns.productionClass(owner):owner;}
        String name(String owner,String name,String descriptor){if(owner.equals(LIST)&&(name.equals("set")||name.equals("size")))return name;return owner.startsWith("net/minecraft/")?ns.productionMethod(owner,name,descriptor):name;}
        String readName(MethodInsnNode m,boolean production){for(Call c:CALLS)if(m.owner.equals(owner(c.owner))&&m.desc.equals(desc(c.descriptor))){String expected=production?name(old?c.oldDeclaration:c.newDeclaration,c.name,c.descriptor):c.name;
                if(m.name.equals(expected)){assertEquals(c.opcode,m.getOpcode(),c.owner+"."+c.name);assertEquals(c.opcode==INVOKEINTERFACE,m.itf);return c.name;}}
            throw new AssertionError("Unreviewed call "+m.owner+"."+m.name+m.desc);}
        String readField(FieldInsnNode f,boolean production){for(Field field:FIELDS)if(f.owner.equals(owner(field.owner))&&f.desc.equals(desc(field.descriptor))){String name=production?ns.productionField(field.owner,field.name,field.descriptor):field.name;
                if(f.name.equals(name)){assertEquals(field.opcode,f.getOpcode());return field.name;}}throw new AssertionError("Unreviewed field "+f.owner+"."+f.name+f.desc);}
        ApiIndex.ApiClass type(String owner){return Objects.requireNonNull(api.getClass(owner(owner)),owner);}
        void exactMethod(String owner,String name,String descriptor,int access){var methods=type(owner).methods().stream().filter(m->m.name().equals(name(owner,name,descriptor))&&m.descriptor().equals(desc(descriptor))).toList();assertEquals(1,methods.size(),owner+"."+name);
            assertEquals(access,methods.get(0).access()&(ACC_PUBLIC|ACC_PROTECTED|ACC_PRIVATE|ACC_STATIC|ACC_FINAL|ACC_ABSTRACT|ACC_NATIVE|ACC_SYNCHRONIZED));}
        ClassNode nativeClass(String owner)throws Exception{String name=ns.productionClass(owner);if(!classes.containsKey(name))classes.put(name,read(bytes(zip,name+".class")));return classes.get(name);}
        MethodNode nativeMethod(String owner,String name,String descriptor)throws Exception{return find(nativeClass(owner),name,desc(descriptor));}
        MethodNode noSpectators()throws Exception{ClassNode selectors=nativeClass(SELECTORS);MethodNode initializer=find(selectors,"<clinit>","()V");
            var assignments=real(initializer).stream().filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
                    .filter(f->f.getOpcode()==PUTSTATIC&&f.owner.equals(owner(SELECTORS))&&f.name.equals("NO_SPECTATORS")&&f.desc.equals("Ljava/util/function/Predicate;")).toList();
            assertEquals(1,assignments.size());var bootstrap=assertInstanceOf(InvokeDynamicInsnNode.class,previousReal(assignments.get(0)));
            assertEquals("java/lang/invoke/LambdaMetafactory",bootstrap.bsm.getOwner());assertEquals("metafactory",bootstrap.bsm.getName());
            assertEquals("()Ljava/util/function/Predicate;",bootstrap.desc);assertEquals(3,bootstrap.bsmArgs.length);
            var implementation=assertInstanceOf(Handle.class,bootstrap.bsmArgs[1]);assertEquals(H_INVOKESTATIC,implementation.getTag());assertEquals(owner(SELECTORS),implementation.getOwner());
            assertEquals(desc("("+ref(ENTITY)+")Z"),implementation.getDesc());return find(selectors,implementation.getName(),implementation.getDesc());}
        @Override public void close()throws Exception{zip.close();classes.clear();}
    }
    private static Side side(boolean old)throws Exception{if(old){if(source==null)source=new Side(true);return source;}if(target==null)target=new Side(false);return target;}
    private static MappingInputs data()throws Exception{if(inputs==null)inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));return inputs;}
    private static ClassNode operationClass()throws Exception{if(operationBytes==null)try(var zip=new ZipFile(System.getProperty("legacybridge.runtimeArtifact"))){operationBytes=bytes(zip,OP+".class");}return read(operationBytes);}
    private static ClassNode read(byte[] bytes){var node=new ClassNode();new ClassReader(bytes).accept(node,0);return node;}
    private static byte[] bytes(ZipFile zip,String name)throws Exception{var entry=zip.getEntry(name);assertNotNull(entry,name);assertTrue(entry.getSize()>0&&entry.getSize()<2*1024*1024);try(var in=zip.getInputStream(entry)){byte[] bytes=in.readNBytes(2*1024*1024+1);assertTrue(bytes.length<=2*1024*1024);return bytes;}}
    private static MethodNode operation(ClassNode node){return find(node,"dispense",dispenseDesc());}
    private static MethodNode find(ClassNode node,String name,String desc){var matches=node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).toList();assertEquals(1,matches.size(),node.name+"."+name+desc);return matches.get(0);}
    private static MethodNode copy(MethodNode original){var n=new MethodNode(ASM9,original.access,original.name,original.desc,original.signature,original.exceptions.toArray(String[]::new));original.accept(n);return n;}
    private static List<AbstractInsnNode> real(MethodNode m){return Arrays.stream(m.instructions.toArray()).filter(n->n.getOpcode()>=0).toList();}
    private static AbstractInsnNode previousReal(AbstractInsnNode n){do{n=n.getPrevious();}while(n.getOpcode()<0);return n;}
    private static Object pop(List<Object> stack){return stack.remove(stack.size()-1);}private static String ref(String owner){return "L"+owner+";";}
    private static String dispenseDesc(){return "("+ref(SOURCE)+ref(STACK)+")Z";}private static String queryDesc(){return "(Ljava/lang/Class;"+ref(BOX)+"Ljava/util/function/Predicate;)Ljava/util/List;";}
}
