package dev.legacybridge.converter.conversion.logic;

import dev.legacybridge.converter.mapping.*;
import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.transformation.*;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Explicit source/target inventories and reobfuscated owned bytecode as data, never game/mod execution. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class ItemAttributeDurabilityMappingIntegrationTest {
    private static final String OWN="dev/legacybridge/fixture/attributedurable/AttributeDurableItem";
    private static final String OLD_ITEM="net/minecraft/item/Item",ITEM="net/minecraft/world/item/Item";
    private static final String OLD_STACK="net/minecraft/item/ItemStack",STACK="net/minecraft/world/item/ItemStack";
    private static final String MULTIMAP="com/google/common/collect/Multimap",IMMUTABLE="com/google/common/collect/ImmutableMultimap",BUILDER=IMMUTABLE+"$Builder";
    private static final String FIXTURE="item-attribute-durability";
    private static MappingInputs inputs;
    @AfterAll static void release(){inputs=null;}

    @Test void bothCallbacksAndTheExactDurabilityRepairAndNativeUuidContractsAreMandatory()throws Exception {
        var scan=scan(FIXTURE);var result=VerifiedLogicMappings.compileItemAttributeDurability(scan.classNodes(),scan,data());
        assertRule(result,LegacySymbol.method(OWN,"func_111205_h","(Lnet/minecraft/inventory/EquipmentSlotType;)L"+MULTIMAP+";"),
                TargetSymbol.method(OWN,"m_7167_","(Lnet/minecraft/world/entity/EquipmentSlot;)L"+MULTIMAP+";"));
        for(String owner:List.of(OWN,OLD_ITEM))assertRule(result,LegacySymbol.method(owner,"func_82789_a","(L"+OLD_STACK+";L"+OLD_STACK+";)Z"),
                TargetSymbol.method(owner.equals(OWN)?OWN:ITEM,"m_6832_","(L"+STACK+";L"+STACK+";)Z"));
        assertRule(result,LegacySymbol.method(OLD_ITEM+"$Properties","func_200918_c","(I)L"+OLD_ITEM+"$Properties;"),
                TargetSymbol.method(ITEM+"$Properties","m_41503_","(I)L"+ITEM+"$Properties;"));
        assertRule(result,LegacySymbol.method(OLD_STACK,"func_77973_b","()L"+OLD_ITEM+";"),TargetSymbol.method(STACK,"m_41720_","()L"+ITEM+";"));
        assertRule(result,LegacySymbol.field("net/minecraft/item/Items","field_151042_j","L"+OLD_ITEM+";"),
                TargetSymbol.field("net/minecraft/world/item/Items","f_42416_","L"+ITEM+";"));
        for(var pair:Map.of("field_111210_e","f_41374_","field_185050_h","f_41375_").entrySet()) {
            assertRule(result,LegacySymbol.field(OWN,pair.getKey(),"Ljava/util/UUID;"),TargetSymbol.field(OWN,pair.getValue(),"Ljava/util/UUID;"));
            assertRule(result,LegacySymbol.field(OLD_ITEM,pair.getKey(),"Ljava/util/UUID;"),TargetSymbol.field(ITEM,pair.getValue(),"Ljava/util/UUID;"));
        }
        assertRule(result,LegacySymbol.method(BUILDER,"put","(Ljava/lang/Object;Ljava/lang/Object;)L"+BUILDER+";"),
                TargetSymbol.method(BUILDER,"put","(Ljava/lang/Object;Ljava/lang/Object;)L"+BUILDER+";"));
        var contracts=new HashSet<String>();result.rules().forEach(r->contracts.add(String.valueOf(r.get("contract"))));
        assertTrue(contracts.containsAll(Set.of("item-attribute-durability.native-capacity-128-stack-one.v1",
                "item-attribute-durability.iron-ingredient-virtual-override.v1","item-attribute-durability.second-argument-item-identity.v1",
                "item-attribute-durability.native-iron-ingredient.v1","item-attributes.mainhand-override.v1")));
        assertFalse(contracts.stream().anyMatch(c->c.startsWith("durable.")),"No implicit durable-only profile authority");
        assertNull(result.plainBlockRecipe());
        assertTrue(result.sourceApi().classes().size()<512);assertTrue(result.targetApi().classes().size()<512);
        assertEquals("com/google/common/collect/AbstractMultimap",result.sourceApi().getClass(IMMUTABLE).superName());
        assertEquals("com/google/common/collect/BaseImmutableMultimap",result.targetApi().getClass(IMMUTABLE).superName());
        assertFalse(result.targetApi().hasClass("net/minecraft/world/item/SwordItem"));
    }
    @Test void unknownMembersDoNotBecomeIdentityRulesOrDurabilityConsumptionSupport()throws Exception {
        var scan=scan(FIXTURE);var result=VerifiedLogicMappings.compileItemAttributeDurability(scan.classNodes(),scan,data());
        for(var symbol:List.of(LegacySymbol.method(IMMUTABLE,"put","(Ljava/lang/Object;Ljava/lang/Object;)Z"),
                LegacySymbol.method("java/util/UUID","fromString","(Ljava/lang/String;)Ljava/util/UUID;"),
                LegacySymbol.method(OLD_ITEM,"func_77644_a","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/LivingEntity;)Z")))
            assertEquals(MappingStatus.MANUAL,result.database().resolve(symbol).status(),symbol.toString());
    }
    @ParameterizedTest @ValueSource(strings={"logic","attributes","durable","plain","horizontal","shape","toggle","food","lifecycle"})
    void oldCompilersStillRefuseTheCombinedGraph(String old)throws Exception {
        var scan=scan(FIXTURE);var i=data();assertThrows(TransformationException.class,()->{
            switch(old){case "attributes"->VerifiedLogicMappings.compileItemAttributes(scan.classNodes(),scan,i);
                case "durable"->VerifiedLogicMappings.compileDurableItem(scan.classNodes(),scan,i);
                case "plain"->VerifiedLogicMappings.compilePlainBlock(scan.classNodes(),scan,i);
                case "horizontal"->VerifiedLogicMappings.compileHorizontalBlock(scan.classNodes(),scan,i);
                case "shape"->VerifiedLogicMappings.compileStaticShapeBlock(scan.classNodes(),scan,i);
                case "toggle"->VerifiedLogicMappings.compileBooleanToggleBlock(scan.classNodes(),scan,i);
                case "food"->VerifiedLogicMappings.compileFood(scan.classNodes(),scan,i);
                case "lifecycle"->VerifiedLogicMappings.compile(scan.classNodes(),scan,i,true);
                default->VerifiedLogicMappings.compile(scan.classNodes(),scan,i);}
        });
    }
    @ParameterizedTest @ValueSource(strings={"item-attributes","durable-item"})
    void newCompilerDoesNotSilentlyComposeOrDropEitherSingleFeature(String fixture)throws Exception {
        var scan=scan(fixture);assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileItemAttributeDurability(scan.classNodes(),scan,data()));
    }
    @Test void oldAttributeRecipeSetStillContainsNoCapacityOrRepairPermission()throws Exception {
        var scan=scan("item-attributes");var result=VerifiedLogicMappings.compileItemAttributes(scan.classNodes(),scan,data());
        assertFalse(result.rules().stream().anyMatch(r->String.valueOf(r.get("contract")).startsWith("item-attribute-durability.")));
        assertEquals(MappingStatus.MANUAL,result.database().resolve(LegacySymbol.method(OLD_ITEM,"func_82789_a","(L"+OLD_STACK+";L"+OLD_STACK+";)Z")).status());
    }

    static Stream<Arguments> memberDrifts(){return Stream.of(true,false).flatMap(old->Stream.of(
            "attributesOverride","repairOverride","durability","getItem","iron","uuidDamage","uuidSpeed",
            "itemConstructor","propertiesConstructor","modifierConstructor","builder","put","build","empty")
            .flatMap(member->Stream.of("missing","private","static","final","abstract","native").map(change->Arguments.of(old,member,change))));}
    @ParameterizedTest @MethodSource("memberDrifts")
    void bothNativeEndpointsAndAllSharedConstructionAndGuavaCallsRetainTheirExactAbi(boolean old,String member,String change)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());var spec=spec(old,member);var type=index.get(spec.owner);assertNotNull(type);
        var methods=new ArrayList<>(type.methods());var fields=new ArrayList<>(type.fields());var list=spec.field?fields:methods;
        var matches=list.stream().filter(m->m.name().equals(spec.name)&&m.descriptor().equals(spec.desc)).toList();assertEquals(1,matches.size(),spec.toString());
        var m=matches.get(0);list.remove(m);int access=switch(change){case "private"->(m.access()&~(ACC_PUBLIC|ACC_PROTECTED))|ACC_PRIVATE;
            case "static"->m.access()^ACC_STATIC;case "final"->m.access()^ACC_FINAL;case "abstract"->m.access()|ACC_ABSTRACT;case "native"->m.access()|ACC_NATIVE;default->m.access();};
        if(!change.equals("missing"))list.add(new ApiIndex.ApiMember(m.name(),m.descriptor(),access));
        index.put(type.name(),new ApiIndex.ApiClass(type.name(),type.access(),type.superName(),type.interfaces(),methods,fields));
        var scan=scan(FIXTURE);assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileItemAttributeDurability(scan.classNodes(),scan,changed(i,old,index)),old+" "+member+" "+change);
    }
    static Stream<Arguments> typeDrifts(){return Stream.of(true,false).flatMap(old->Stream.of("stack","items","properties","multimap","immutable","builder")
            .flatMap(type->Stream.of("missing","nonpublic","kind").map(change->Arguments.of(old,type,change))));}
    @ParameterizedTest @MethodSource("typeDrifts")
    void exactRepairAndGuavaTypesCannotBeInferredFromNames(boolean old,String name,String change)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());String owner=type(old,name);var c=index.get(owner);assertNotNull(c);
        if(change.equals("missing"))index.remove(owner);else index.put(owner,new ApiIndex.ApiClass(owner,
                change.equals("nonpublic")?c.access()&~ACC_PUBLIC:c.access()^ACC_INTERFACE,c.superName(),c.interfaces(),c.methods(),c.fields()));
        var scan=scan(FIXTURE);assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileItemAttributeDurability(scan.classNodes(),scan,changed(i,old,index)));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void ordinaryNativeStacksMustRemainFinalOnBothSides(boolean old)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());String owner=type(old,"stack");var c=index.get(owner);assertTrue((c.access()&ACC_FINAL)!=0);
        index.put(owner,new ApiIndex.ApiClass(owner,c.access()&~ACC_FINAL,c.superName(),c.interfaces(),c.methods(),c.fields()));
        var scan=scan(FIXTURE);assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileItemAttributeDurability(scan.classNodes(),scan,changed(i,old,index)));
    }
    @ParameterizedTest @CsvSource({"true,remove","false,remove","true,hash","false,hash","true,side","false,side"})
    void composedProfileStillRequiresExplicitPinnedGuavaProvenance(boolean old,String change)throws Exception {
        var i=data();String hash=old?"972139718abc8a4893fa78cba8cf7b2c903f35c97aaf44fa3031b0669948b480":"a42edc9cab792e39fe39bb94f3fca655ed157ff87a8af78e1d6ba5b07c4a00ab";
        var sources=new ArrayList<>(i.sources());var prior=sources.stream().filter(s->s.sha256().equals(hash)).findFirst().orElseThrow();sources.remove(prior);
        if(!change.equals("remove"))sources.add(new MappingSource(change.equals("side")?(old?"target.apiJars[99]":"legacy.apiJars[99]"):prior.id(),change.equals("hash")?"0".repeat(64):prior.sha256(),prior.origin()));
        var edited=new MappingInputs(i.legacyOfficial(),i.targetOfficial(),i.legacySrg(),i.targetSrg(),i.legacyApi(),i.targetApi(),sources,i.diagnostics(),i.complete(),i.scope());
        var scan=scan(FIXTURE);var error=assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileItemAttributeDurability(scan.classNodes(),scan,edited));
        assertTrue(error.getMessage().contains("ITEM_ATTRIBUTES_GUAVA_DATA_REQUIRED"));
    }
    @Test void incompleteApiInputsNeverBorrowDataFromThisTestClasspath()throws Exception {
        var i=data();var edited=new MappingInputs(i.legacyOfficial(),i.targetOfficial(),i.legacySrg(),i.targetSrg(),i.legacyApi(),i.targetApi(),i.sources(),i.diagnostics(),false,i.scope());
        var scan=scan(FIXTURE);assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileItemAttributeDurability(scan.classNodes(),scan,edited));
    }

    private record Spec(String owner,String name,String desc,boolean field){}
    private static Spec spec(boolean old,String member) {
        String item=old?OLD_ITEM:ITEM,stack=old?OLD_STACK:STACK,props=item+"$Properties",modifier=(old?"net/minecraft/entity/ai/attributes/":"net/minecraft/world/entity/ai/attributes/")+"AttributeModifier";
        return switch(member){
            case "attributesOverride"->new Spec(item,"getDefaultAttributeModifiers","(L"+(old?"net/minecraft/inventory/EquipmentSlotType":"net/minecraft/world/entity/EquipmentSlot")+";)L"+MULTIMAP+";",false);
            case "repairOverride"->new Spec(item,"isValidRepairItem","(L"+stack+";L"+stack+";)Z",false);
            case "durability"->new Spec(props,"durability","(I)L"+props+";",false);
            case "getItem"->new Spec(stack,"getItem","()L"+item+";",false);
            case "iron"->new Spec(type(old,"items"),"IRON_INGOT","L"+item+";",true);
            case "uuidDamage","uuidSpeed"->new Spec(item,member.equals("uuidDamage")?"BASE_ATTACK_DAMAGE_UUID":"BASE_ATTACK_SPEED_UUID","Ljava/util/UUID;",true);
            case "itemConstructor"->new Spec(item,"<init>","(L"+props+";)V",false);
            case "propertiesConstructor"->new Spec(props,"<init>","()V",false);
            case "modifierConstructor"->new Spec(modifier,"<init>","(Ljava/util/UUID;Ljava/lang/String;DL"+modifier+"$Operation;)V",false);
            case "builder"->new Spec(IMMUTABLE,"builder","()L"+BUILDER+";",false);
            case "put"->new Spec(BUILDER,"put","(Ljava/lang/Object;Ljava/lang/Object;)L"+BUILDER+";",false);
            case "build"->new Spec(BUILDER,"build","()L"+IMMUTABLE+";",false);
            case "empty"->new Spec(IMMUTABLE,"of","()L"+IMMUTABLE+";",false);
            default->throw new AssertionError(member);};
    }
    private static String type(boolean old,String name){return switch(name){case "stack"->old?OLD_STACK:STACK;case "items"->old?"net/minecraft/item/Items":"net/minecraft/world/item/Items";
        case "properties"->(old?OLD_ITEM:ITEM)+"$Properties";case "multimap"->MULTIMAP;case "immutable"->IMMUTABLE;case "builder"->BUILDER;default->throw new AssertionError(name);};}
    private static MappingInputs changed(MappingInputs i,boolean old,Map<String,ApiIndex.ApiClass> index){return new MappingInputs(i.legacyOfficial(),i.targetOfficial(),i.legacySrg(),i.targetSrg(),
            old?new ApiIndex(index):i.legacyApi(),old?i.targetApi():new ApiIndex(index),i.sources(),i.diagnostics(),i.complete(),i.scope());}
    private static void assertRule(VerifiedLogicMappings.Result result,LegacySymbol source,TargetSymbol target){var rule=result.database().resolve(source);
        assertEquals(MappingStatus.RESOLVED,rule.status());assertEquals(MappingConfidence.VERIFIED,rule.confidence());assertEquals(target,rule.target());assertEquals(AdapterCatalog.LOGIC,result.adapterBindings().get(source));}
    private static MappingInputs data()throws Exception{if(inputs==null)inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));return inputs;}
    private static LegacyReferenceScanner.ScanResult scan(String fixture)throws Exception {
        String artifact=fixture.equals("durable-item")?"durable":fixture;
        var path=Path.of(System.getProperty("legacybridge.fixtureRoot"),fixture+"-1.16.5/build/libs/legacybridge-"+artifact+"-1.16.5-1.0.0.jar");var classes=new TreeMap<String,byte[]>();
        try(var zip=new ZipFile(path.toFile())){for(var entry:Collections.list(zip.entries()))if(entry.getName().endsWith(".class"))try(var in=zip.getInputStream(entry)){classes.put(entry.getName(),in.readAllBytes());}}
        return new LegacyReferenceScanner().scan(classes);
    }
}
