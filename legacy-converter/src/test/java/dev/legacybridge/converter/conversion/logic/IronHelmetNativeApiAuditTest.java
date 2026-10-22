package dev.legacybridge.converter.conversion.logic;

import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.transformation.TransformationException;
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

/** Read-only declarations and bounded native bytecode data. The small interpreter below uses
 * synthetic predicates/stacks, never Minecraft, original classes or a world. It establishes a
 * counterexample in the reviewed bodies, not equipment/gameplay/multiplayer qualification. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class IronHelmetNativeApiAuditTest {
    private static final String ITEM="net/minecraft/world/item/Item",ARMOR="net/minecraft/world/item/ArmorItem",
            MATERIAL="net/minecraft/world/item/ArmorMaterial",MATERIALS="net/minecraft/world/item/ArmorMaterials",
            TYPE=ARMOR+"$Type",PROPS=ITEM+"$Properties",STACK="net/minecraft/world/item/ItemStack",
            SLOT="net/minecraft/world/entity/EquipmentSlot",EQUIPABLE="net/minecraft/world/item/Equipable",
            PLAYER="net/minecraft/world/entity/player/Player",LEVEL="net/minecraft/world/level/Level",
            HAND="net/minecraft/world/InteractionHand",HOLDER="net/minecraft/world/InteractionResultHolder",
            MOB="net/minecraft/world/entity/Mob",INGREDIENT="net/minecraft/world/item/crafting/Ingredient";
    private static MappingInputs inputs;
    private static final Map<Boolean,NativeApi> APIS=new HashMap<>();
    @AfterAll static void close()throws Exception{for(var a:APIS.values())a.close();APIS.clear();inputs=null;}

    @Test void declarationsAreCheckedButContextualConstructionAndEquipmentUseStayUnsupported()throws Exception {
        var report=IronHelmetApiAudit.inspect(data());var tree=report.toTree();
        assertEquals("UNSUPPORTED",tree.get("status"));assertEquals("CHECKED_EXACT_DECLARATIONS",tree.get("declarationStatus"));
        assertEquals(false,tree.get("candidateGenerated"));assertEquals(false,tree.get("classesExecuted"));assertEquals(0,tree.get("mappingRulesEmitted"));
        assertEquals("NOT_RUN",tree.get("nativeValidation"));assertEquals(3,((List<?>)tree.get("semanticBlockers")).size());
        assertEquals(9,report.hierarchy().size());assertEquals(8,report.targetOnlyDeclarations().size());
        var argument=report.declarations().stream().filter(d->"contextualConstructorEquipmentArgument".equals(d.get("role"))).findFirst().orElseThrow();
        assertEquals(false,argument.get("globalRenameAllowed"));
        assertTrue(report.declarations().stream().allMatch(d->"DECLARATION_ONLY".equals(d.get("evidenceKind"))));
        assertEquals("PINNED_SOURCE_REVIEW_NOT_DERIVED_FROM_API_INDEX",((Map<?,?>)tree.get("knownFacts")).get("evidenceKind"));
        assertEquals(165,((Map<?,?>)((Map<?,?>)tree.get("knownFacts")).get("ironHelmet")).get("maxDamage"));
        var index=IronHelmetApiAudit.sourceLinkageApi(data());assertTrue(index.hasClass("net/minecraft/item/ArmorItem"));
        assertTrue(index.hasClass("net/minecraftforge/registries/IForgeRegistry"));assertFalse(index.hasClass("net/minecraft/world/World"));
        assertTrue(index.classes().size()<512);assertThrows(UnsupportedOperationException.class,()->tree.put("status","RESOLVED"));
    }
    @Test void reportDoesNotRetainMutableCallerCollections() {
        var values=new ArrayList<Object>(List.of("before"));var nested=new LinkedHashMap<String,Object>();nested.put("values",values);
        var report=new IronHelmetApiAudit.Report(List.of(nested),List.of(),List.of(),List.of());values.add("after");nested.put("added",true);
        assertEquals(Map.of("values",List.of("before")),report.hierarchy().get(0));
        assertThrows(UnsupportedOperationException.class,()->report.hierarchy().get(0).put("status","RESOLVED"));
    }
    @Test void incompleteOrFiniteInputsCannotBePresentedAsTheFullNativeAudit()throws Exception {
        var i=data();
        assertThrows(IllegalArgumentException.class,()->new MappingInputs(i.legacyOfficial(),i.targetOfficial(),i.legacySrg(),i.targetSrg(),
                i.legacyApi(),i.targetApi(),i.sources(),i.diagnostics(),true,MappingInputs.Scope.FINITE_COHORT_CONTRACT));
        for(var scope:List.of(MappingInputs.Scope.FULL_ENVIRONMENT,MappingInputs.Scope.FINITE_COHORT_CONTRACT)){
            var limited=new MappingInputs(i.legacyOfficial(),i.targetOfficial(),i.legacySrg(),i.targetSrg(),i.legacyApi(),i.targetApi(),i.sources(),i.diagnostics(),false,scope);
            assertThrows(TransformationException.class,()->IronHelmetApiAudit.inspect(limited));assertThrows(TransformationException.class,()->IronHelmetApiAudit.sourceLinkageApi(limited));
        }
    }
    static Stream<Arguments> memberDrifts(){return Stream.of(true,false).flatMap(old->Stream.of("constructor","iron","equipment","materialDurability","use","repair")
            .flatMap(member->Stream.of("missing","private","static","final","duplicate").map(change->Arguments.of(old,member,change))));}
    @ParameterizedTest @MethodSource("memberDrifts")
    void exactNativeDeclarationsCannotDrift(boolean old,String member,String change)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());var a=api(old);
        String canonical=switch(member){case "iron"->MATERIALS;case "equipment"->old?SLOT:TYPE;case "materialDurability"->MATERIAL;default->ARMOR;};
        String name=switch(member){case "constructor"->"<init>";case "iron"->"IRON";case "equipment"->old?"HEAD":"HELMET";
            case "materialDurability"->old?"getDurabilityForSlot":"getDurabilityForType";case "repair"->"isValidRepairItem";default->"use";};
        var c=index.get(a.owner(canonical));boolean field=member.equals("iron")||member.equals("equipment");
        var methods=new ArrayList<>(c.methods());var fields=new ArrayList<>(c.fields());var list=field?fields:methods;
        var found=list.stream().filter(m->m.name().equals(name)).toList();assertEquals(1,found.size());var m=found.get(0);list.remove(m);
        int access=switch(change){case "private"->(m.access()&~(ACC_PUBLIC|ACC_PROTECTED))|ACC_PRIVATE;case "static"->m.access()^ACC_STATIC;case "final"->m.access()^ACC_FINAL;default->m.access();};
        if(!change.equals("missing"))list.add(new ApiIndex.ApiMember(m.name(),m.descriptor(),access));if(change.equals("duplicate"))list.add(m);
        index.put(c.name(),new ApiIndex.ApiClass(c.name(),c.access(),c.superName(),c.interfaces(),methods,fields));
        assertThrows(TransformationException.class,()->IronHelmetApiAudit.inspect(changed(i,old,index)));
    }
    static Stream<Arguments> hierarchyDrifts(){return Stream.of(true,false).flatMap(old->Stream.of("armor","material","materials","slot")
            .flatMap(type->Stream.of("parent","interfaces","nonpublic").map(change->Arguments.of(old,type,change))));}
    @ParameterizedTest @MethodSource("hierarchyDrifts")
    void hierarchyAndAccessAreNotInferredFromNames(boolean old,String type,String change)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());String name=api(old).owner(switch(type){case "armor"->ARMOR;case "material"->MATERIAL;case "materials"->MATERIALS;default->SLOT;});var c=index.get(name);
        var interfaces=new ArrayList<>(c.interfaces());if(interfaces.isEmpty())interfaces.add("java/io/Serializable");else interfaces.add(interfaces.get(0));
        index.put(name,new ApiIndex.ApiClass(name,change.equals("nonpublic")?c.access()&~ACC_PUBLIC:c.access(),change.equals("parent")?"java/lang/String":c.superName(),
                change.equals("interfaces")?interfaces:c.interfaces(),c.methods(),c.fields()));
        assertThrows(TransformationException.class,()->IronHelmetApiAudit.inspect(changed(i,old,index)));
    }
    @ParameterizedTest @ValueSource(strings={TYPE,EQUIPABLE,INGREDIENT})
    void targetNewOrDescriptorOnlyTypesMustBeExplicit(String type)throws Exception {
        var i=data();var index=new TreeMap<>(i.targetApi().classes());assertNotNull(index.remove(type));
        assertThrows(TransformationException.class,()->IronHelmetApiAudit.inspect(changed(i,false,index)));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void archivesMatchThePinnedBodyReview(boolean old)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(api(old).path)){byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))>=0;)digest.update(buffer,0,n);}
        assertEquals(old?"1a3e71d6ccb8461fe9d94f4c63551fe52a8c0c01116572c49a0c3253d0506b03":"b820f2243c37f060b17678f3a23c868da14d35ebf82ee47b4035b1718c0952ee",HexFormat.of().formatHex(digest.digest()));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void constructorReadsNativeMaterialAndRegistersRealDispenserBehavior(boolean old)throws Exception {
        var a=api(old);String argument=old?SLOT:TYPE;var ctor=a.method(ARMOR,"<init>","("+ref(MATERIAL)+ref(argument)+ref(PROPS)+")V");var code=a.code(ctor);
        assertEquals(List.of("ALOAD 0","ALOAD 3","ALOAD 1","ALOAD 2",call(INVOKEINTERFACE,MATERIAL,old?"getDurabilityForSlot":"getDurabilityForType","("+ref(argument)+")I",true),
                call(INVOKEVIRTUAL,PROPS,"defaultDurability","(I)"+ref(PROPS),false),call(INVOKESPECIAL,ITEM,"<init>","("+ref(PROPS)+")V",false)),code.subList(0,7));
        assertTrue(code.contains(call(INVOKESTATIC,"net/minecraft/world/level/block/DispenserBlock","registerBehavior","(Lnet/minecraft/world/level/ItemLike;Lnet/minecraft/core/dispenser/DispenseItemBehavior;)V",false)));
        assertTrue(code.contains(call(INVOKEINTERFACE,MATERIAL,old?"getDefenseForSlot":"getDefenseForType","("+ref(argument)+")I",true)));
        assertTrue(code.contains(call(INVOKEINTERFACE,MATERIAL,"getToughness","()F",true)));
        assertTrue(code.contains(call(INVOKEINTERFACE,MATERIAL,"getKnockbackResistance","()F",true)));
        assertTrue(a.code(a.method(ARMOR,"isValidRepairItem","("+ref(STACK)+ref(STACK)+")Z")).contains(call(INVOKESPECIAL,ITEM,"isValidRepairItem","("+ref(STACK)+ref(STACK)+")Z",false)));
    }
    @Test void targetUseReallyDelegatesToTheChangedDefaultRatherThanTheLegacyBody()throws Exception {
        var a=api(false);assertEquals(List.of("ALOAD 0","ALOAD 0","ALOAD 1","ALOAD 2","ALOAD 3",
                call(INVOKEVIRTUAL,ARMOR,"swapWithEquipmentSlot","("+ref(ITEM)+ref(LEVEL)+ref(PLAYER)+ref(HAND)+")"+ref(HOLDER),false),"ARETURN"),a.code(a.method(ARMOR,"use",useDesc())));
        assertFalse(api(true).node(ARMOR).methods.stream().anyMatch(m->m.name.equals("swapWithEquipmentSlot")));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void occupiedUnboundDifferentStackIsAConcreteCounterexampleOnEachSide(boolean client)throws Exception {
        var source=api(true).simulate(false,false,false,client);var target=api(false).simulate(false,false,false,client);
        assertEquals("FAIL",source.result);assertEquals(1,source.handCount);assertEquals("old",source.equipped);assertEquals(0,source.writes);assertEquals(0,source.stats);
        assertEquals(client?"SUCCESS":"CONSUME",target.result);assertEquals(0,target.handCount);assertEquals("new",target.equipped);
        assertEquals("old",target.returned);assertEquals(1,target.returnCount);assertFalse(target.returnedSameHand);assertEquals(1,target.writes);assertEquals(client?0:1,target.stats);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void emptySlotRetainsClearedHandAliasButOnlyTargetBodyAwardsServerStat(boolean client)throws Exception {
        var source=api(true).simulate(true,false,false,client);var target=api(false).simulate(true,false,false,client);
        for(var outcome:List.of(source,target)) {assertEquals(client?"SUCCESS":"CONSUME",outcome.result);assertEquals(0,outcome.handCount);
            assertEquals("new",outcome.equipped);assertEquals(1,outcome.equipCount);assertEquals(0,outcome.returnCount);assertTrue(outcome.returnedSameHand);assertEquals(1,outcome.writes);}
        assertEquals(0,source.stats);assertEquals(client?0:1,target.stats);
    }
    @ParameterizedTest @CsvSource({"true,false,true","true,false,false","false,true,true","false,true,false","true,true,true","true,true,false"})
    void targetBindingOrMatchGuardPreventsWritesAndStats(boolean binding,boolean matches,boolean client)throws Exception {
        var outcome=api(false).simulate(false,binding,matches,client);assertEquals("FAIL",outcome.result);assertEquals(1,outcome.handCount);
        assertEquals("old",outcome.equipped);assertEquals(0,outcome.writes);assertEquals(0,outcome.stats);assertTrue(outcome.returnedSameHand);
    }
    @Test void oracleRejectsUnknownCallsAndChangedInvocationKinds()throws Exception {
        var a=api(false);var original=a.useBody();var unknown=copy(original);
        unknown.instructions.insert(new MethodInsnNode(INVOKESTATIC,"java/lang/System","nanoTime","()J",false));
        assertThrows(AssertionError.class,()->a.simulate(unknown,false,false,false,false));
        var changed=copy(original);var call=(MethodInsnNode)Arrays.stream(changed.instructions.toArray()).filter(n->n instanceof MethodInsnNode m&&m.name.equals("matches")).findFirst().orElseThrow();
        call.setOpcode(INVOKEVIRTUAL);var wrong=changed;assertThrows(AssertionError.class,()->a.simulate(wrong,false,false,false,false));
    }

    private record Outcome(String result,int handCount,String equipped,int equipCount,String returned,int returnCount,boolean returnedSameHand,int writes,int stats){}
    private static final class Stack {final String kind;int count;Stack(String kind,int count){this.kind=kind;this.count=count;}Stack copy(){return new Stack(kind,count);}Stack clearCopy(){var copy=copy();count=0;return copy;}}
    private record Result(String result,Stack stack){}
    private static final class Player {final Stack hand=new Stack("new",1);Stack equipment;int writes,stats;Player(boolean empty){equipment=new Stack("old",empty?0:1);}}
    private static final class NativeApi implements AutoCloseable {
        final Path path;final ZipFile zip;final LogicApiNamespace ns;final boolean old;final Map<String,ClassNode> nodes=new HashMap<>();
        NativeApi(Path path,LogicApiNamespace ns,boolean old)throws Exception{this.path=path;this.ns=ns;this.old=old;zip=new ZipFile(path.toFile());}
        String owner(String canonical){return ns.productionClass(canonical);}
        ClassNode node(String canonical)throws Exception{String owner=owner(canonical);if(!nodes.containsKey(owner)){
            var entry=Objects.requireNonNull(zip.getEntry(owner+".class"));assertTrue(entry.getSize()>0&&entry.getSize()<=2*1024*1024);
            try(var in=zip.getInputStream(entry)){byte[] bytes=in.readNBytes(2*1024*1024+1);assertTrue(bytes.length<=2*1024*1024);var node=new ClassNode();new ClassReader(bytes).accept(node,0);nodes.put(owner,node);}
        }return nodes.get(owner);}
        MethodNode method(String owner,String name,String desc)throws Exception{return node(owner).methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(ns.productionDescriptor(desc))).findFirst().orElseThrow(()->new AssertionError(owner+"."+name+desc));}
        MethodNode useBody()throws Exception{return old?method(ARMOR,"use",useDesc()):method(EQUIPABLE,"swapWithEquipmentSlot","("+ref(ITEM)+ref(LEVEL)+ref(PLAYER)+ref(HAND)+")"+ref(HOLDER));}
        List<String> code(MethodNode method){var out=new ArrayList<String>();for(var n:method.instructions){if(n.getOpcode()<0)continue;String s=org.objectweb.asm.util.Printer.OPCODES[n.getOpcode()];
            if(n instanceof VarInsnNode v)s+=" "+v.var;else if(n instanceof MethodInsnNode m)s=call(m.getOpcode(),ns.canonicalClass(m.owner),m.name,ns.canonicalDescriptor(m.desc),m.itf);
            else if(n instanceof FieldInsnNode f)s+=" "+ns.canonicalClass(f.owner)+"."+f.name+":"+ns.canonicalDescriptor(f.desc);
            else if(n instanceof LdcInsnNode l)s+=" "+l.cst;else if(n instanceof TypeInsnNode t)s+=" "+ns.canonicalClass(t.desc);
            else if(n instanceof IntInsnNode i)s+=" "+i.operand;else if(n instanceof JumpInsnNode)s+=" branch";out.add(s);}return List.copyOf(out);}
        Outcome simulate(boolean empty,boolean binding,boolean matches,boolean client)throws Exception{return simulate(useBody(),empty,binding,matches,client);}
        Outcome simulate(MethodNode body,boolean empty,boolean binding,boolean matches,boolean client) {
            assertTrue(body.tryCatchBlocks.isEmpty());var p=new Player(empty);Object[] locals=new Object[body.maxLocals];
            locals[old?1:2]=client;locals[old?2:3]=p;locals[old?3:4]="HAND";if(!old)locals[1]="ITEM";
            var stack=new ArrayList<Object>();
            for(int pc=0,steps=0;pc<body.instructions.size()&&steps++<256;pc++) {var n=body.instructions.get(pc);int op=n.getOpcode();if(op<0)continue;
                if(op==ALOAD)stack.add(locals[((VarInsnNode)n).var]);else if(op==ASTORE)locals[((VarInsnNode)n).var]=pop(stack);else if(op==ICONST_0)stack.add(0);
                else if(op==GETSTATIC){var f=(FieldInsnNode)n;assertFalse(old);assertEquals("net/minecraft/stats/Stats",ns.canonicalClass(f.owner));assertEquals("ITEM_USED",f.name);assertEquals("Lnet/minecraft/stats/StatType;",ns.canonicalDescriptor(f.desc));stack.add("ITEM_USED");}
                else if(n instanceof MethodInsnNode m) {
                    assertFalse(m.itf);String owner=ns.canonicalClass(m.owner),desc=ns.canonicalDescriptor(m.desc),name=m.name;
                    Object value=null;boolean push=true;
                    if(owner.equals(PLAYER)&&name.equals("getItemInHand")){exact(m,INVOKEVIRTUAL,"("+ref(HAND)+")"+ref(STACK));assertEquals("HAND",pop(stack));assertSame(p,pop(stack));value=p.hand;}
                    else if(owner.equals(MOB)&&name.equals("getEquipmentSlotForItem")){exact(m,INVOKESTATIC,"("+ref(STACK)+")"+ref(SLOT));assertSame(p.hand,pop(stack));value="HEAD";}
                    else if(owner.equals(PLAYER)&&name.equals("getItemBySlot")){exact(m,INVOKEVIRTUAL,"("+ref(SLOT)+")"+ref(STACK));assertEquals("HEAD",pop(stack));assertSame(p,pop(stack));value=p.equipment;}
                    else if(owner.equals(STACK)&&name.equals("isEmpty")){exact(m,INVOKEVIRTUAL,"()Z");value=((Stack)pop(stack)).count==0;}
                    else if(owner.equals(STACK)&&name.equals("copy")){assertTrue(old);exact(m,INVOKEVIRTUAL,"()"+ref(STACK));value=((Stack)pop(stack)).copy();}
                    else if(owner.equals(STACK)&&name.equals("copyAndClear")){assertFalse(old);exact(m,INVOKEVIRTUAL,"()"+ref(STACK));value=((Stack)pop(stack)).clearCopy();}
                    else if(owner.equals(STACK)&&name.equals("setCount")){assertTrue(old);exact(m,INVOKEVIRTUAL,"(I)V");int count=(Integer)pop(stack);((Stack)pop(stack)).count=count;push=false;}
                    else if(owner.equals(PLAYER)&&name.equals("setItemSlot")){exact(m,INVOKEVIRTUAL,"("+ref(SLOT)+ref(STACK)+")V");var next=(Stack)pop(stack);assertEquals("HEAD",pop(stack));assertSame(p,pop(stack));assertNotSame(p.hand,next);p.equipment=next;p.writes++;push=false;}
                    else if(owner.equals(LEVEL)&&name.equals("isClientSide")){exact(m,INVOKEVIRTUAL,"()Z");value=pop(stack);assertEquals(client,value);}
                    else if(owner.equals(HOLDER)&&name.equals("sidedSuccess")){exact(m,INVOKESTATIC,"(Ljava/lang/Object;Z)"+ref(HOLDER));boolean side=(Boolean)pop(stack);value=new Result(side?"SUCCESS":"CONSUME",(Stack)pop(stack));}
                    else if(owner.equals(HOLDER)&&name.equals("fail")){exact(m,INVOKESTATIC,"(Ljava/lang/Object;)"+ref(HOLDER));value=new Result("FAIL",(Stack)pop(stack));}
                    else if(owner.equals("net/minecraft/world/item/enchantment/EnchantmentHelper")&&name.equals("hasBindingCurse")){assertFalse(old);exact(m,INVOKESTATIC,"("+ref(STACK)+")Z");assertSame(p.equipment,pop(stack));value=binding;}
                    else if(owner.equals(STACK)&&name.equals("matches")){assertFalse(old);exact(m,INVOKESTATIC,"("+ref(STACK)+ref(STACK)+")Z");assertSame(p.equipment,pop(stack));assertSame(p.hand,pop(stack));value=matches;}
                    else if(owner.equals("net/minecraft/stats/StatType")&&name.equals("get")){assertFalse(old);exact(m,INVOKEVIRTUAL,"(Ljava/lang/Object;)Lnet/minecraft/stats/Stat;");assertEquals("ITEM",pop(stack));assertEquals("ITEM_USED",pop(stack));value="STAT";}
                    else if(owner.equals(PLAYER)&&name.equals("awardStat")){assertFalse(old);exact(m,INVOKEVIRTUAL,"(Lnet/minecraft/stats/Stat;)V");assertEquals("STAT",pop(stack));assertSame(p,pop(stack));assertFalse(client);p.stats++;push=false;}
                    else throw new AssertionError("Unreviewed native call "+owner+"."+name+desc);
                    if(push)stack.add(value);
                }else if(op==IFEQ||op==IFNE){boolean value=(Boolean)pop(stack);if(op==IFEQ?!value:value)pc=body.instructions.indexOf(((JumpInsnNode)n).label)-1;}
                else if(op==GOTO)pc=body.instructions.indexOf(((JumpInsnNode)n).label)-1;
                else if(op==ARETURN){var r=(Result)pop(stack);assertTrue(stack.isEmpty());return new Outcome(r.result,p.hand.count,p.equipment.kind,p.equipment.count,r.stack.kind,r.stack.count,r.stack==p.hand,p.writes,p.stats);}
                else throw new AssertionError("Unreviewed native opcode "+op);
            }throw new AssertionError("Native use exceeded bounded synthetic path");
        }
        void exact(MethodInsnNode m,int opcode,String descriptor){assertEquals(opcode,m.getOpcode());assertEquals(descriptor,ns.canonicalDescriptor(m.desc));}
        @Override public void close()throws Exception{zip.close();}
    }
    private static MappingInputs data()throws Exception{if(inputs==null)inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));return inputs;}
    private static NativeApi api(boolean old)throws Exception{if(!APIS.containsKey(old)){var p=new Properties();try(var in=Files.newInputStream(Path.of(System.getProperty("legacybridge.logicMappingProfile")))){p.load(in);}var i=data();
        APIS.put(old,new NativeApi(Path.of(p.getProperty(old?"legacy.apiJars":"target.apiJars").split(";")[0]),new LogicApiNamespace(old?i.legacyOfficial():i.targetOfficial(),old?i.legacySrg():i.targetSrg(),old?i.legacyApi():i.targetApi(),old),old));}return APIS.get(old);}
    private static MappingInputs changed(MappingInputs i,boolean old,Map<String,ApiIndex.ApiClass> index){return new MappingInputs(i.legacyOfficial(),i.targetOfficial(),i.legacySrg(),i.targetSrg(),old?new ApiIndex(index):i.legacyApi(),old?i.targetApi():new ApiIndex(index),i.sources(),i.diagnostics(),i.complete(),i.scope());}
    private static MethodNode copy(MethodNode method){var n=new MethodNode(ASM9,method.access,method.name,method.desc,method.signature,method.exceptions.toArray(String[]::new));method.accept(n);return n;}
    private static Object pop(List<Object> stack){return stack.remove(stack.size()-1);}
    private static String useDesc(){return "("+ref(LEVEL)+ref(PLAYER)+ref(HAND)+")"+ref(HOLDER);}
    private static String ref(String owner){return "L"+owner+";";}
    private static String call(int opcode,String owner,String name,String desc,boolean itf){return org.objectweb.asm.util.Printer.OPCODES[opcode]+" "+owner+"."+name+desc+(itf?" interface":"");}
}
