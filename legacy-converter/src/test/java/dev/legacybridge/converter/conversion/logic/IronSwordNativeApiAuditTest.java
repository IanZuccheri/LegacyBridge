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

/**
 * Explicit-profile native archives are read only as bounded bytecode data. No game/mod class
 * is defined or executed. Straight-line bodies and branches are checked exactly; the one
 * bounded interpreter uses synthetic block predicates, not a world, tag manager or ItemStack.
 * No combat, mining, reload, repair, durability lifecycle or multiplayer validation is claimed.
 */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class IronSwordNativeApiAuditTest {
    private static final String ITEM="net/minecraft/world/item/Item",SWORD="net/minecraft/world/item/SwordItem",
            TIERED="net/minecraft/world/item/TieredItem",TIER="net/minecraft/world/item/Tier",TIERS="net/minecraft/world/item/Tiers",
            PROPS=ITEM+"$Properties",STACK="net/minecraft/world/item/ItemStack",STATE="net/minecraft/world/level/block/state/BlockState",
            INGREDIENT="net/minecraft/world/item/crafting/Ingredient",SLOT="net/minecraft/world/entity/EquipmentSlot",
            ATTRIBUTE="net/minecraft/world/entity/ai/attributes/Attribute",MODIFIER="net/minecraft/world/entity/ai/attributes/AttributeModifier",
            OPERATION=MODIFIER+"$Operation",ATTRIBUTES="net/minecraft/world/entity/ai/attributes/Attributes",
            BUILDER="com/google/common/collect/ImmutableMultimap$Builder",ACTIONS="net/minecraftforge/common/ToolActions";
    private static MappingInputs inputs;
    private static final Map<Boolean,NativeApi> APIS=new HashMap<>();
    @AfterAll static void close()throws Exception{for(var api:APIS.values())api.close();APIS.clear();inputs=null;}

    @Test void matchingDeclarationsStillProduceOnlyAnExplicitlyUnsupportedAudit()throws Exception {
        var report=IronSwordApiAudit.inspect(data());var tree=report.toTree();
        assertEquals("UNSUPPORTED",tree.get("status"));assertEquals("CHECKED_EXACT_DECLARATIONS",tree.get("declarationStatus"));
        assertEquals(false,tree.get("candidateGenerated"));assertEquals(false,tree.get("classesExecuted"));
        assertEquals(0,tree.get("mappingRulesEmitted"));assertEquals("NOT_RUN",tree.get("nativeValidation"));
        assertEquals(3,((List<?>)tree.get("semanticBlockers")).size());assertEquals(32,report.declarations().size());
        assertEquals(6,report.targetOnlyDeclarations().size());assertEquals(7,report.hierarchy().size());
        assertTrue(report.declarations().stream().allMatch(d->d.get("evidenceKind").equals("DECLARATION_ONLY")));
        assertThrows(UnsupportedOperationException.class,()->tree.put("status","RESOLVED"));
        var facts=(Map<?,?>)tree.get("knownFacts");assertEquals("PINNED_SOURCE_REVIEW_NOT_DERIVED_FROM_API_INDEX",facts.get("evidenceKind"));
        assertNotEquals(-2.4D,((Map<?,?>)facts.get("construction")).get("attackSpeedAsDouble"));
        var source=IronSwordApiAudit.sourceLinkageApi(data());assertTrue(source.hasClass("net/minecraft/item/SwordItem"));
        assertFalse(source.hasClass("net/minecraft/world/World"));assertTrue(source.classes().size()<512);
    }

    static Stream<Arguments> memberDrifts(){return Stream.of(true,false).flatMap(old->Stream.of("constructor","iron","damageUuid","tierUses","repair","speed","durability")
            .flatMap(member->Stream.of("missing","private","static","final").map(change->Arguments.of(old,member,change))));}
    @ParameterizedTest @MethodSource("memberDrifts")
    void missingOrChangedDeclarationIsNotAcceptedOnEitherSide(boolean old,String member,String change)throws Exception {
        var original=data();var index=new TreeMap<>((old?original.legacyApi():original.targetApi()).classes());
        NativeApi a=api(old);String canonical=switch(member){case "constructor","speed"->SWORD;case "iron"->TIERS;case "damageUuid"->ITEM;
            case "tierUses"->TIER;case "repair"->TIERED;default->PROPS;};
        String name=switch(member){case "constructor"->"<init>";case "iron"->"IRON";case "damageUuid"->"BASE_ATTACK_DAMAGE_UUID";
            case "tierUses"->"getUses";case "repair"->"isValidRepairItem";case "speed"->"getDestroySpeed";default->"defaultDurability";};
        var type=index.get(a.owner(canonical));boolean field=member.equals("iron")||member.equals("damageUuid");
        var methods=new ArrayList<>(type.methods());var fields=new ArrayList<>(type.fields());var list=field?fields:methods;
        var found=list.stream().filter(m->m.name().equals(name)).toList();assertEquals(1,found.size());var value=found.get(0);list.remove(value);
        int access=switch(change){case "private"->(value.access()&~(ACC_PUBLIC|ACC_PROTECTED))|ACC_PRIVATE;case "static"->value.access()^ACC_STATIC;
            case "final"->value.access()^ACC_FINAL;default->value.access();};
        if(!change.equals("missing"))list.add(new ApiIndex.ApiMember(name,value.descriptor(),access));
        index.put(type.name(),new ApiIndex.ApiClass(type.name(),type.access(),type.superName(),type.interfaces(),methods,fields));
        assertThrows(TransformationException.class,()->IronSwordApiAudit.inspect(changed(original,old,index)),old+" "+member+" "+change);
    }
    @ParameterizedTest @CsvSource({"true,parent","false,parent","true,marker","false,marker","true,missing","false,missing","true,nonpublic","false,nonpublic"})
    void hierarchyAndVisibilityCannotBeGuessed(boolean old,String change)throws Exception {
        var original=data();var index=new TreeMap<>((old?original.legacyApi():original.targetApi()).classes());var name=api(old).owner(SWORD);var type=index.get(name);
        if(change.equals("missing"))index.remove(name);
        else index.put(name,new ApiIndex.ApiClass(name,change.equals("nonpublic")?type.access()&~ACC_PUBLIC:type.access(),
                change.equals("parent")?"java/lang/Object":type.superName(),change.equals("marker")?List.of():type.interfaces(),type.methods(),type.fields()));
        assertThrows(TransformationException.class,()->IronSwordApiAudit.inspect(changed(original,old,index)));
    }
    @Test void incompleteFiniteDataCannotImpersonateRealApis()throws Exception {
        var i=data();var partial=new MappingInputs(i.legacyOfficial(),i.targetOfficial(),i.legacySrg(),i.targetSrg(),i.legacyApi(),i.targetApi(),i.sources(),List.of(),false,MappingInputs.Scope.FINITE_COHORT_CONTRACT);
        assertThrows(TransformationException.class,()->IronSwordApiAudit.inspect(partial));assertThrows(TransformationException.class,()->IronSwordApiAudit.sourceLinkageApi(partial));
    }
    @ParameterizedTest @CsvSource({"true,missing","false,missing","true,nonpublic","false,nonpublic"})
    void signatureReferencedTypesAlsoRequireRealPublicDeclarations(boolean old,String change)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());String name=api(old).owner(INGREDIENT);var type=index.get(name);
        if(change.equals("missing"))index.remove(name);else index.put(name,new ApiIndex.ApiClass(name,type.access()&~ACC_PUBLIC,type.superName(),type.interfaces(),type.methods(),type.fields()));
        assertThrows(TransformationException.class,()->IronSwordApiAudit.inspect(changed(i,old,index)));
    }
    @Test void reportDefensivelyCopiesCallerProvidedNestedCollections() {
        var nested=new ArrayList<Object>(List.of("first"));var entry=new LinkedHashMap<String,Object>();entry.put("nested",nested);
        var report=new IronSwordApiAudit.Report(List.of(entry),List.of(),List.of(),List.of());nested.add("later");entry.put("other","value");
        assertEquals(Map.of("nested",List.of("first")),report.hierarchy().get(0));
        assertThrows(UnsupportedOperationException.class,()->report.hierarchy().get(0).put("status","RESOLVED"));
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void explicitNativeArchiveMatchesPinnedReviewHash(boolean old)throws Exception {
        var a=api(old);var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(a.path)){byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))>=0;)digest.update(buffer,0,n);}
        assertEquals(old?"1a3e71d6ccb8461fe9d94f4c63551fe52a8c0c01116572c49a0c3253d0506b03":"b820f2243c37f060b17678f3a23c868da14d35ebf82ee47b4035b1718c0952ee",HexFormat.of().formatHex(digest.digest()));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void constructorBytecodeUsesExactInheritedUuidsAdditionAndFloatToDoubleConversion(boolean old)throws Exception {
        var a=api(old);assertSwordConstructor(a,a.method(SWORD,"<init>","("+ref(TIER)+"IF"+ref(PROPS)+")V"));
        // Arithmetic follows the verified I2F/FADD/F2D expression; this does not execute SwordItem.
        assertEquals(5.0F,(float)3+2.0F);assertEquals(-2.4000000953674316D,(double)-2.4F);assertNotEquals(-2.4D,(double)-2.4F);
        var clinit=a.code(a.method(ITEM,"<clinit>","()V"));
        for(var uuid:Map.of("BASE_ATTACK_DAMAGE_UUID","CB3F55D3-645C-4F38-A497-9C13A33DB5CF","BASE_ATTACK_SPEED_UUID","FA233E1C-4180-4865-B01B-BCCE9785ACA3").entrySet()) {
            int index=clinit.indexOf("PUTSTATIC "+ITEM+"."+uuid.getKey()+":Ljava/util/UUID;");assertTrue(index>=2);
            assertEquals(List.of("LDC "+uuid.getValue(),call(INVOKESTATIC,"java/util/UUID","fromString","(Ljava/lang/String;)Ljava/util/UUID;",false)),clinit.subList(index-2,index));
        }
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void ironEnumConstructionAndRepairSupplierAreExactNativeData(boolean old)throws Exception {
        var a=api(old);var body=a.code(a.method(TIERS,"<clinit>","()V"));int put=body.indexOf("PUTSTATIC "+TIERS+".IRON:"+ref(TIERS));assertTrue(put>=11);
        assertEquals(List.of("NEW "+TIERS,"DUP","LDC IRON","ICONST_2","ICONST_2","SIPUSH 250","LDC 6.0","FCONST_2","BIPUSH 14",
                "INDY get()Ljava/util/function/Supplier; "+TIERS+".lambda$static$2()"+ref(INGREDIENT),
                call(INVOKESPECIAL,TIERS,"<init>","(Ljava/lang/String;IIIFFILjava/util/function/Supplier;)V",false)),body.subList(put-11,put));
        String itemLike="net/minecraft/world/level/ItemLike";
        assertEquals(List.of("ICONST_1","ANEWARRAY "+itemLike,"DUP","ICONST_0","GETSTATIC net/minecraft/world/item/Items.IRON_INGOT:"+ref(ITEM),"AASTORE",
                call(INVOKESTATIC,INGREDIENT,"of","(["+ref(itemLike)+")"+ref(INGREDIENT),false),"ARETURN"),a.code(a.method(TIERS,"lambda$static$2","()"+ref(INGREDIENT))));
        String lazy="net/minecraft/util/LazyLoadedValue";
        assertEquals(List.of("ALOAD 0","ALOAD 1","ILOAD 2",call(INVOKESPECIAL,"java/lang/Enum","<init>","(Ljava/lang/String;I)V",false),
                "ALOAD 0","ILOAD 3","PUTFIELD "+TIERS+".level:I","ALOAD 0","ILOAD 4","PUTFIELD "+TIERS+".uses:I",
                "ALOAD 0","FLOAD 5","PUTFIELD "+TIERS+".speed:F","ALOAD 0","FLOAD 6","PUTFIELD "+TIERS+".damage:F",
                "ALOAD 0","ILOAD 7","PUTFIELD "+TIERS+".enchantmentValue:I","ALOAD 0","NEW "+lazy,"DUP","ALOAD 8",
                call(INVOKESPECIAL,lazy,"<init>","(Ljava/util/function/Supplier;)V",false),"PUTFIELD "+TIERS+".repairIngredient:"+ref(lazy),"RETURN"),
                a.code(a.method(TIERS,"<init>","(Ljava/lang/String;IIIFFILjava/util/function/Supplier;)V")));
        for(var fact:Map.of("getUses","uses:I","getSpeed","speed:F","getAttackDamageBonus","damage:F","getLevel","level:I","getEnchantmentValue","enchantmentValue:I").entrySet()) {
            String type=fact.getValue().substring(fact.getValue().length()-1);
            assertEquals(List.of("ALOAD 0","GETFIELD "+TIERS+"."+fact.getValue(),type.equals("F")?"FRETURN":"IRETURN"),a.code(a.method(TIERS,fact.getKey(),"()"+type)));
        }
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void durabilityAndRepairOnlyProveLocalNativeCallContracts(boolean old)throws Exception {
        var a=api(old);
        var freshProperties=new ArrayList<>(List.of("ALOAD 0",call(INVOKESPECIAL,"java/lang/Object","<init>","()V",false),
                "ALOAD 0","BIPUSH 64","PUTFIELD "+PROPS+".maxStackSize:I","ALOAD 0",
                "GETSTATIC net/minecraft/world/item/Rarity.COMMON:Lnet/minecraft/world/item/Rarity;","PUTFIELD "+PROPS+".rarity:Lnet/minecraft/world/item/Rarity;"));
        if(!old)freshProperties.addAll(List.of("ALOAD 0","GETSTATIC net/minecraft/world/flag/FeatureFlags.VANILLA_SET:Lnet/minecraft/world/flag/FeatureFlagSet;",
                "PUTFIELD "+PROPS+".requiredFeatures:Lnet/minecraft/world/flag/FeatureFlagSet;"));
        freshProperties.addAll(List.of("ALOAD 0","ICONST_1","PUTFIELD "+PROPS+".canRepair:Z"));
        if(old)freshProperties.addAll(List.of("ALOAD 0",call(INVOKESTATIC,"com/google/common/collect/Maps","newHashMap","()Ljava/util/HashMap;",false),"PUTFIELD "+PROPS+".toolClasses:Ljava/util/Map;"));
        freshProperties.add("RETURN");assertEquals(freshProperties,a.code(a.method(PROPS,"<init>","()V")));
        var maxDamage=a.node(PROPS).fields.stream().filter(f->f.name.equals("maxDamage")&&f.desc.equals("I")).findFirst().orElseThrow();
        assertEquals(0,maxDamage.access&ACC_STATIC);assertNull(maxDamage.value); // Unwritten instance field has the JVM default zero.
        assertEquals(List.of("ALOAD 0","ALOAD 2","ALOAD 1",call(INVOKEINTERFACE,TIER,"getUses","()I",true),call(INVOKEVIRTUAL,PROPS,"defaultDurability","(I)"+ref(PROPS),false),
                call(INVOKESPECIAL,ITEM,"<init>","("+ref(PROPS)+")V",false),"ALOAD 0","ALOAD 1","PUTFIELD "+TIERED+".tier:"+ref(TIER),"RETURN"),a.code(a.method(TIERED,"<init>","("+ref(TIER)+ref(PROPS)+")V")));
        assertEquals(List.of("ALOAD 0","GETFIELD "+PROPS+".maxDamage:I","IFNE @7","ALOAD 0","ILOAD 1",call(INVOKEVIRTUAL,PROPS,"durability","(I)"+ref(PROPS),false),"GOTO @8","ALOAD 0","ARETURN"),a.code(a.method(PROPS,"defaultDurability","(I)"+ref(PROPS))));
        assertEquals(List.of("ALOAD 0","ILOAD 1","PUTFIELD "+PROPS+".maxDamage:I","ALOAD 0","ICONST_1","PUTFIELD "+PROPS+".maxStackSize:I","ALOAD 0","ARETURN"),a.code(a.method(PROPS,"durability","(I)"+ref(PROPS))));
        assertEquals(List.of("ALOAD 0","GETFIELD "+TIERED+".tier:"+ref(TIER),call(INVOKEINTERFACE,TIER,"getRepairIngredient","()"+ref(INGREDIENT),true),"ALOAD 2",
                call(INVOKEVIRTUAL,INGREDIENT,"test","("+ref(STACK)+")Z",false),"IFNE @11","ALOAD 0","ALOAD 1","ALOAD 2",
                call(INVOKESPECIAL,ITEM,"isValidRepairItem","("+ref(STACK)+ref(STACK)+")Z",false),"IFEQ @13","ICONST_1","GOTO @14","ICONST_0","IRETURN"),
                a.code(a.method(TIERED,"isValidRepairItem","("+ref(STACK)+ref(STACK)+")Z")));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void defaultModifiersSelectMainHandAndNativeSuperFallbackOnly(boolean old)throws Exception {
        var a=api(old);var expected=new ArrayList<>(List.of("ALOAD 1","GETSTATIC "+SLOT+".MAINHAND:"+ref(SLOT),old?"IF_ACMPNE @6":"IF_ACMPNE @6",
                "ALOAD 0","GETFIELD "+SWORD+".defaultModifiers:Lcom/google/common/collect/Multimap;",old?"ARETURN":"GOTO @9","ALOAD 0","ALOAD 1",
                call(INVOKESPECIAL,TIERED,"getDefaultAttributeModifiers","("+ref(SLOT)+")Lcom/google/common/collect/Multimap;",false),"ARETURN"));
        assertEquals(expected,a.code(a.method(SWORD,"getDefaultAttributeModifiers","("+ref(SLOT)+")Lcom/google/common/collect/Multimap;")));
    }
    @Test void realMiningBodiesExposeADataReloadCounterexampleNotAnEquivalence()throws Exception {
        var old=api(true);var target=api(false);var legacy=old.method(SWORD,"getDestroySpeed","("+ref(STACK)+ref(STATE)+")F");var modern=target.method(SWORD,"getDestroySpeed","("+ref(STACK)+ref(STATE)+")F");
        for(String material:List.of("PLANT","REPLACEABLE_PLANT","CORAL","VEGETABLE","STONE"))for(boolean leaves:List.of(false,true))for(boolean efficient:List.of(false,true))for(boolean cobweb:List.of(false,true)) {
            var block=new Block(cobweb,material,leaves,efficient);assertEquals(cobweb?15F:(!material.equals("STONE")||leaves?1.5F:1F),old.speed(legacy,block));
            assertEquals(cobweb?15F:(efficient?1.5F:1F),target.speed(modern,block));
        }
        // Synthetic PLANT with a false target tag predicate models a replacing datapack. No reload runs.
        var counterexample=new Block(false,"PLANT",false,false);assertEquals(1.5F,old.speed(legacy,counterexample));assertEquals(1F,target.speed(modern,counterexample));
        assertEquals("UNSUPPORTED",IronSwordApiAudit.inspect(data()).toTree().get("status"));
    }
    @Test void targetToolActionsAreAnAdditionalContractNotAnAssumedSourceMethod()throws Exception {
        var old=api(true);var target=api(false);assertTrue(old.node(SWORD).methods.stream().noneMatch(m->m.name.equals("canPerformAction")));
        assertEquals(List.of("GETSTATIC "+ACTIONS+".DEFAULT_SWORD_ACTIONS:Ljava/util/Set;","ALOAD 2",call(INVOKEINTERFACE,"java/util/Set","contains","(Ljava/lang/Object;)Z",true),"IRETURN"),
                target.code(target.method(SWORD,"canPerformAction","("+ref(STACK)+"Lnet/minecraftforge/common/ToolAction;)Z")));
        var code=target.code(target.method(ACTIONS,"<clinit>","()V"));int end=code.indexOf("PUTSTATIC "+ACTIONS+".DEFAULT_SWORD_ACTIONS:Ljava/util/Set;");assertTrue(end>=11);
        assertEquals(List.of("ICONST_2","ANEWARRAY net/minecraftforge/common/ToolAction","DUP","ICONST_0","GETSTATIC "+ACTIONS+".SWORD_DIG:Lnet/minecraftforge/common/ToolAction;","AASTORE","DUP","ICONST_1",
                "GETSTATIC "+ACTIONS+".SWORD_SWEEP:Lnet/minecraftforge/common/ToolAction;","AASTORE",call(INVOKESTATIC,ACTIONS,"of","([Lnet/minecraftforge/common/ToolAction;)Ljava/util/Set;",false)),code.subList(end-11,end));
        // Evidence of changed dispatch syntax only, not simulation of Player.attack or sweeping.
        String player="net/minecraft/world/entity/player/Player",entity="net/minecraft/world/entity/Entity";
        var sourceAttack=old.method(player,"attack","("+ref(entity)+")V");var targetAttack=target.method(player,"attack","("+ref(entity)+")V");
        assertTrue(Arrays.stream(sourceAttack.instructions.toArray()).anyMatch(i->i instanceof TypeInsnNode t&&i.getOpcode()==INSTANCEOF&&t.desc.equals(old.owner(SWORD))));
        assertTrue(Arrays.stream(targetAttack.instructions.toArray()).anyMatch(i->i instanceof FieldInsnNode f&&i.getOpcode()==GETSTATIC&&f.owner.equals(ACTIONS)&&f.name.equals("SWORD_SWEEP")));
        assertTrue(Arrays.stream(targetAttack.instructions.toArray()).anyMatch(i->i instanceof MethodInsnNode m&&m.name.equals("canPerformAction")&&m.desc.equals("(Lnet/minecraftforge/common/ToolAction;)Z")));
    }
    @Test void changedNativeInvocationOpcodeAndUnknownPredicateCallAreRejectedByTheOracle()throws Exception {
        var a=api(false);var ctor=copy(a.method(SWORD,"<init>","("+ref(TIER)+"IF"+ref(PROPS)+")V"));
        var method=Arrays.stream(ctor.instructions.toArray()).filter(i->i instanceof MethodInsnNode m&&m.name.equals("getAttackDamageBonus")).map(i->(MethodInsnNode)i).findFirst().orElseThrow();
        method.setOpcode(INVOKEVIRTUAL);method.itf=false;assertThrows(AssertionError.class,()->assertSwordConstructor(a,ctor));
        var speed=copy(a.method(SWORD,"getDestroySpeed","("+ref(STACK)+ref(STATE)+")F"));speed.instructions.insert(new MethodInsnNode(INVOKESTATIC,"java/lang/System","nanoTime","()J",false));
        assertThrows(AssertionError.class,()->a.speed(speed,new Block(false,"STONE",false,false)));
    }

    private static void assertSwordConstructor(NativeApi a,MethodNode ctor) {
        var expected=new ArrayList<>(List.of("ALOAD 0","ALOAD 1","ALOAD 4",call(INVOKESPECIAL,TIERED,"<init>","("+ref(TIER)+ref(PROPS)+")V",false),
                "ALOAD 0","ILOAD 2","I2F","ALOAD 1",call(INVOKEINTERFACE,TIER,"getAttackDamageBonus","()F",true),"FADD","PUTFIELD "+SWORD+".attackDamage:F",
                call(INVOKESTATIC,"com/google/common/collect/ImmutableMultimap","builder","()L"+BUILDER+";",false),"ASTORE 5"));
        for(boolean damage:List.of(true,false)) {
            expected.addAll(List.of("ALOAD 5","GETSTATIC "+ATTRIBUTES+"."+(damage?"ATTACK_DAMAGE":"ATTACK_SPEED")+":"+ref(ATTRIBUTE),"NEW "+MODIFIER,"DUP",
                    "GETSTATIC "+SWORD+"."+(damage?"BASE_ATTACK_DAMAGE_UUID":"BASE_ATTACK_SPEED_UUID")+":Ljava/util/UUID;","LDC Weapon modifier"));
            expected.addAll(damage?List.of("ALOAD 0","GETFIELD "+SWORD+".attackDamage:F"):List.of("FLOAD 3"));
            expected.addAll(List.of("F2D","GETSTATIC "+OPERATION+".ADDITION:"+ref(OPERATION),call(INVOKESPECIAL,MODIFIER,"<init>","(Ljava/util/UUID;Ljava/lang/String;D"+ref(OPERATION)+")V",false),
                    call(INVOKEVIRTUAL,BUILDER,"put","(Ljava/lang/Object;Ljava/lang/Object;)L"+BUILDER+";",false),"POP"));
        }
        expected.addAll(List.of("ALOAD 0","ALOAD 5",call(INVOKEVIRTUAL,BUILDER,"build","()Lcom/google/common/collect/ImmutableMultimap;",false),"PUTFIELD "+SWORD+".defaultModifiers:Lcom/google/common/collect/Multimap;","RETURN"));
        assertEquals(expected,a.code(ctor));
    }
    private static MappingInputs changed(MappingInputs i,boolean old,Map<String,ApiIndex.ApiClass> index) {
        return new MappingInputs(i.legacyOfficial(),i.targetOfficial(),i.legacySrg(),i.targetSrg(),old?new ApiIndex(index):i.legacyApi(),old?i.targetApi():new ApiIndex(index),i.sources(),i.diagnostics(),i.complete(),i.scope());
    }
    private static MappingInputs data()throws Exception {if(inputs==null)inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));return inputs;}
    private static NativeApi api(boolean old)throws Exception {if(!APIS.containsKey(old)) {
        var profile=new Properties();try(var in=Files.newInputStream(Path.of(System.getProperty("legacybridge.logicMappingProfile")))){profile.load(in);}
        var i=data();APIS.put(old,new NativeApi(Path.of(profile.getProperty(old?"legacy.apiJars":"target.apiJars").split(";")[0]),
                new LogicApiNamespace(old?i.legacyOfficial():i.targetOfficial(),old?i.legacySrg():i.targetSrg(),old?i.legacyApi():i.targetApi(),old),old));
    }return APIS.get(old);}
    private record Block(boolean cobweb,String material,boolean leaves,boolean efficient) {}
    private static final class NativeApi implements AutoCloseable {
        final Path path;final ZipFile zip;final LogicApiNamespace ns;final boolean old;final Map<String,ClassNode> nodes=new HashMap<>();
        NativeApi(Path path,LogicApiNamespace ns,boolean old)throws Exception{this.path=path;this.ns=ns;this.old=old;zip=new ZipFile(path.toFile());}
        String owner(String canonical){return canonical.startsWith("net/minecraftforge/")?canonical:ns.productionClass(canonical);}
        ClassNode node(String canonical)throws Exception {String owner=owner(canonical);if(!nodes.containsKey(owner)) {
            var entry=Objects.requireNonNull(zip.getEntry(owner+".class"));assertTrue(entry.getSize()>0&&entry.getSize()<=2*1024*1024);
            try(var in=zip.getInputStream(entry)){byte[] bytes=in.readNBytes(2*1024*1024+1);assertTrue(bytes.length<=2*1024*1024);var node=new ClassNode();new ClassReader(bytes).accept(node,0);nodes.put(owner,node);}
        }return nodes.get(owner);}
        MethodNode method(String canonical,String name,String desc)throws Exception {String descriptor=ns.productionDescriptor(desc);return node(canonical).methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(descriptor)).findFirst().orElseThrow(()->new AssertionError(canonical+"."+name+descriptor));}
        List<String> code(MethodNode method) {
            assertTrue(method.tryCatchBlocks.isEmpty());var result=new ArrayList<String>();
            for(var i:method.instructions){int op=i.getOpcode();if(op<0)continue;String text=org.objectweb.asm.util.Printer.OPCODES[op];
                if(i instanceof VarInsnNode v)text+=" "+v.var;
                else if(i instanceof TypeInsnNode t)text+=" "+ns.canonicalClass(t.desc);
                else if(i instanceof LdcInsnNode l)text+=" "+l.cst;
                else if(i instanceof IntInsnNode n)text+=" "+n.operand;
                else if(i instanceof FieldInsnNode f)text+=" "+ns.canonicalClass(f.owner)+"."+f.name+":"+ns.canonicalDescriptor(f.desc);
                else if(i instanceof MethodInsnNode m)text=call(op,ns.canonicalClass(m.owner),m.name,ns.canonicalDescriptor(m.desc),m.itf);
                else if(i instanceof JumpInsnNode j)text+=" @"+codePosition(method,j.label);
                else if(i instanceof InvokeDynamicInsnNode d) {
                    assertEquals("java/lang/invoke/LambdaMetafactory",d.bsm.getOwner());assertEquals("metafactory",d.bsm.getName());assertEquals(H_INVOKESTATIC,d.bsm.getTag());
                    assertEquals(3,d.bsmArgs.length);assertInstanceOf(Handle.class,d.bsmArgs[1]);var h=(Handle)d.bsmArgs[1];assertEquals(H_INVOKESTATIC,h.getTag());assertFalse(h.isInterface());
                    assertEquals(Type.getMethodType("()Ljava/lang/Object;"),d.bsmArgs[0]);assertEquals(Type.getMethodType(h.getDesc()),d.bsmArgs[2]);
                    text="INDY "+d.name+d.desc+" "+ns.canonicalClass(h.getOwner())+"."+h.getName()+ns.canonicalDescriptor(h.getDesc());
                }else if(!(i instanceof InsnNode))throw new AssertionError("Unreviewed body instruction "+i);
                result.add(text);
            }return List.copyOf(result);
        }
        float speed(MethodNode method,Block block) {
            assertTrue(method.tryCatchBlocks.isEmpty());var stack=new ArrayList<Object>();Object[] locals=new Object[method.maxLocals];locals[2]=block;
            for(int pc=0,steps=0;pc<method.instructions.size()&&steps++<128;pc++) {
                var i=method.instructions.get(pc);int op=i.getOpcode();if(op<0)continue;
                if(op==ALOAD)stack.add(locals[((VarInsnNode)i).var]);else if(op==ASTORE)locals[((VarInsnNode)i).var]=pop(stack);
                else if(op==FCONST_1)stack.add(1F);else if(op==LDC){Object c=((LdcInsnNode)i).cst;assertTrue(c.equals(15F)||c.equals(1.5F));stack.add(c);}
                else if(op==GETSTATIC){var f=(FieldInsnNode)i;String owner=ns.canonicalClass(f.owner);
                    if(owner.equals("net/minecraft/world/level/block/Blocks")){assertEquals("COBWEB",f.name);stack.add("COBWEB");}
                    else if(old&&owner.equals("net/minecraft/world/level/material/Material")){assertTrue(Set.of("PLANT","REPLACEABLE_PLANT","CORAL","VEGETABLE").contains(f.name));stack.add(f.name);}
                    else {assertEquals("net/minecraft/tags/BlockTags",owner);assertEquals(old?"LEAVES":"SWORD_EFFICIENT",f.name);stack.add(f.name);}
                }else if(op==INVOKEVIRTUAL){var m=(MethodInsnNode)i;assertFalse(m.itf);assertEquals(owner(STATE),m.owner);
                    if(old&&m.name.equals("getMaterial")){assertEquals("()Lnet/minecraft/block/material/Material;",m.desc);assertEquals(block,pop(stack));stack.add(block.material);}
                    else {assertEquals("is",m.name);assertTrue(m.desc.equals("("+ref(owner("net/minecraft/world/level/block/Block"))+")Z")||m.desc.equals(old?"(Lnet/minecraft/tags/ITag;)Z":"(Lnet/minecraft/tags/TagKey;)Z"));
                        String what=(String)pop(stack);assertEquals(block,pop(stack));stack.add(switch(what){case "COBWEB"->block.cobweb;case "LEAVES"->block.leaves;case "SWORD_EFFICIENT"->block.efficient;default->throw new AssertionError(what);});}
                }else if(op==IF_ACMPEQ||op==IF_ACMPNE){Object right=pop(stack),left=pop(stack);boolean equal=Objects.equals(left,right);if(op==IF_ACMPEQ?equal:!equal)pc=method.instructions.indexOf(((JumpInsnNode)i).label)-1;}
                else if(op==IFEQ||op==IFNE){boolean value=(Boolean)pop(stack);if(op==IFEQ?!value:value)pc=method.instructions.indexOf(((JumpInsnNode)i).label)-1;}
                else if(op==GOTO)pc=method.instructions.indexOf(((JumpInsnNode)i).label)-1;
                else if(op==FRETURN){float value=(Float)pop(stack);assertTrue(stack.isEmpty());return value;}
                else throw new AssertionError("Unreviewed native speed opcode "+op);
            }throw new AssertionError("Native speed predicate exceeded bounded path budget");
        }
        @Override public void close()throws Exception{zip.close();}
    }
    private static int codePosition(MethodNode method,LabelNode label){int count=0;for(var i:method.instructions){if(i==label)return count;if(i.getOpcode()>=0)count++;}throw new AssertionError("Foreign label");}
    private static Object pop(List<Object> stack){return stack.remove(stack.size()-1);}
    private static MethodNode copy(MethodNode original){var result=new MethodNode(ASM9,original.access,original.name,original.desc,original.signature,original.exceptions.toArray(String[]::new));original.accept(result);return result;}
    private static String ref(String owner){return "L"+owner+";";}
    private static String call(int opcode,String owner,String name,String desc,boolean itf){return org.objectweb.asm.util.Printer.OPCODES[opcode]+" "+owner+"."+name+desc+(itf?" interface":"");}
}
