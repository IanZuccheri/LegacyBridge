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

/** Real reobfuscated fixture and explicit pinned API data, never original/native class execution. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class ItemAttributeMappingIntegrationTest {
    private static final String OWN="dev/legacybridge/fixture/attributes/AttributeContentItem";
    private static final String MULTIMAP="com/google/common/collect/Multimap", IMMUTABLE="com/google/common/collect/ImmutableMultimap", BUILDER=IMMUTABLE+"$Builder";
    private static MappingInputs inputs;
    @AfterAll static void release(){inputs=null;}

    @Test void callbackAndInheritedUuidAliasesHaveExactRulesBoundToAnExistingOperation()throws Exception {
        var scan=scan();var result=VerifiedLogicMappings.compileItemAttributes(scan.classNodes(),scan,data());
        assertRule(result,LegacySymbol.method(OWN,"func_111205_h","(Lnet/minecraft/inventory/EquipmentSlotType;)L"+MULTIMAP+";"),
                TargetSymbol.method(OWN,"m_7167_","(Lnet/minecraft/world/entity/EquipmentSlot;)L"+MULTIMAP+";"));
        for(var pair:Map.of("field_111210_e","f_41374_","field_185050_h","f_41375_").entrySet()) {
            assertRule(result,LegacySymbol.field(OWN,pair.getKey(),"Ljava/util/UUID;"),TargetSymbol.field(OWN,pair.getValue(),"Ljava/util/UUID;"));
            assertRule(result,LegacySymbol.field("net/minecraft/item/Item",pair.getKey(),"Ljava/util/UUID;"),
                    TargetSymbol.field("net/minecraft/world/item/Item",pair.getValue(),"Ljava/util/UUID;"));
        }
        for(var pair:Map.of("field_233823_f_","f_22281_","field_233825_h_","f_22283_").entrySet())
            assertRule(result,LegacySymbol.field("net/minecraft/entity/ai/attributes/Attributes",pair.getKey(),"Lnet/minecraft/entity/ai/attributes/Attribute;"),
                    TargetSymbol.field("net/minecraft/world/entity/ai/attributes/Attributes",pair.getValue(),"Lnet/minecraft/world/entity/ai/attributes/Attribute;"));
        assertRule(result,LegacySymbol.method(BUILDER,"put","(Ljava/lang/Object;Ljava/lang/Object;)L"+BUILDER+";"),
                TargetSymbol.method(BUILDER,"put","(Ljava/lang/Object;Ljava/lang/Object;)L"+BUILDER+";"));
        assertEquals(MappingStatus.MANUAL,result.database().resolve(LegacySymbol.method(IMMUTABLE,"put","(Ljava/lang/Object;Ljava/lang/Object;)Z")).status());
        assertEquals(MappingStatus.MANUAL,result.database().resolve(LegacySymbol.method("java/util/UUID","fromString","(Ljava/lang/String;)Ljava/util/UUID;")).status());
        assertTrue(result.rules().stream().anyMatch(r->String.valueOf(r.get("contract")).equals("item-attributes.mainhand-override.v1")));
        assertNull(result.plainBlockRecipe());
    }
    @Test void guavaLinkageUsesSelectedSourceAndTargetAncestorsRatherThanTheConverterClasspath()throws Exception {
        var scan=scan();var result=VerifiedLogicMappings.compileItemAttributes(scan.classNodes(),scan,data());
        assertEquals("com/google/common/collect/AbstractMultimap",result.sourceApi().getClass(IMMUTABLE).superName());
        assertEquals("com/google/common/collect/BaseImmutableMultimap",result.targetApi().getClass(IMMUTABLE).superName());
        assertTrue(result.targetApi().hasClass("com/google/common/collect/AbstractMultimap"));
        assertTrue(result.sourceApi().hasClass(MULTIMAP));assertTrue(result.targetApi().hasClass(MULTIMAP));
        assertTrue(result.sourceApi().classes().size()<512);assertTrue(result.targetApi().classes().size()<512);
        assertFalse(result.sourceApi().hasClass("com/google/common/collect/ImmutableSet"));
        assertFalse(result.targetApi().hasClass("net/minecraft/world/item/SwordItem"));
    }
    @ParameterizedTest @ValueSource(strings={"logic","durable","plain","horizontal","shape","toggle","food","lifecycle"})
    void olderCompilersDoNotAcquireAttributeOrGuavaAuthority(String old)throws Exception {
        var scan=scan();var i=data();assertThrows(TransformationException.class,()->{
            switch(old){case "durable"->VerifiedLogicMappings.compileDurableItem(scan.classNodes(),scan,i);
                case "plain"->VerifiedLogicMappings.compilePlainBlock(scan.classNodes(),scan,i);
                case "horizontal"->VerifiedLogicMappings.compileHorizontalBlock(scan.classNodes(),scan,i);
                case "shape"->VerifiedLogicMappings.compileStaticShapeBlock(scan.classNodes(),scan,i);
                case "toggle"->VerifiedLogicMappings.compileBooleanToggleBlock(scan.classNodes(),scan,i);
                case "food"->VerifiedLogicMappings.compileFood(scan.classNodes(),scan,i);
                case "lifecycle"->VerifiedLogicMappings.compile(scan.classNodes(),scan,i,true);
                default->VerifiedLogicMappings.compile(scan.classNodes(),scan,i);}
        });
    }
    static Stream<Arguments> memberDrifts() {
        return Stream.of(true,false).flatMap(old->Stream.of("override","itemConstructor","propertiesConstructor","modifierConstructor","uuidDamage","uuidSpeed","damage","speed","slot","addition","builder","put","build","empty")
                .flatMap(member->Stream.of("missing","private","static","final","native").map(change->Arguments.of(old,member,change))));
    }
    @ParameterizedTest @MethodSource("memberDrifts")
    void exactMemberPresenceAccessAndDispatchAreMandatoryOnBothSelectedApis(boolean old,String member,String change)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());var spec=spec(old,member);var type=index.get(spec.owner);assertNotNull(type);
        var methods=new ArrayList<>(type.methods());var fields=new ArrayList<>(type.fields());var list=spec.field?fields:methods;
        var matches=list.stream().filter(m->m.name().equals(spec.name)&&m.descriptor().equals(spec.desc)).toList();assertEquals(1,matches.size(),spec.toString());
        var m=matches.get(0);list.remove(m);int access=switch(change){case "private"->(m.access()&~(ACC_PUBLIC|ACC_PROTECTED))|ACC_PRIVATE;case "static"->m.access()^ACC_STATIC;
            case "final"->m.access()^ACC_FINAL;case "native"->m.access()|ACC_NATIVE;default->m.access();};
        if(!change.equals("missing"))list.add(new ApiIndex.ApiMember(m.name(),m.descriptor(),access));
        index.put(type.name(),new ApiIndex.ApiClass(type.name(),type.access(),type.superName(),type.interfaces(),methods,fields));
        var scan=scan();assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileItemAttributes(scan.classNodes(),scan,changed(i,old,index)),old+" "+member+" "+change);
    }
    static Stream<Arguments> typeDrifts(){return Stream.of(true,false).flatMap(old->Stream.of("item","attribute","modifier","attributes","slot","operation","multimap","immutable","builder")
            .flatMap(type->Stream.of("missing","nonpublic","kind","parent").map(change->Arguments.of(old,type,change))));}
    @ParameterizedTest @MethodSource("typeDrifts")
    void exactTypeAndHierarchyMustNotBeInferredFromMatchingNames(boolean old,String name,String change)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());String owner=type(old,name);var c=index.get(owner);assertNotNull(c);
        if(change.equals("missing"))index.remove(owner);else index.put(owner,new ApiIndex.ApiClass(owner,
                change.equals("nonpublic")?c.access()&~ACC_PUBLIC:change.equals("kind")?c.access()^ACC_INTERFACE:c.access(),
                change.equals("parent")?(c.superName().equals("java/lang/Object")?"java/lang/Number":"java/lang/Object"):c.superName(),c.interfaces(),c.methods(),c.fields()));
        var scan=scan();assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileItemAttributes(scan.classNodes(),scan,changed(i,old,index)));
    }
    @ParameterizedTest @CsvSource({"true,remove","false,remove","true,hash","false,hash","true,side","false,side"})
    void reviewedGuavaProvenanceCannotFallBackToTheEngineClasspath(boolean old,String change)throws Exception {
        var i=data();String hash=old?"972139718abc8a4893fa78cba8cf7b2c903f35c97aaf44fa3031b0669948b480":"a42edc9cab792e39fe39bb94f3fca655ed157ff87a8af78e1d6ba5b07c4a00ab";
        var sources=new ArrayList<>(i.sources());var prior=sources.stream().filter(s->s.sha256().equals(hash)).findFirst().orElseThrow();sources.remove(prior);
        if(!change.equals("remove"))sources.add(new MappingSource(change.equals("side")?(old?"target.apiJars[99]":"legacy.apiJars[99]"):prior.id(),change.equals("hash")?"0".repeat(64):prior.sha256(),prior.origin()));
        var edited=new MappingInputs(i.legacyOfficial(),i.targetOfficial(),i.legacySrg(),i.targetSrg(),i.legacyApi(),i.targetApi(),sources,i.diagnostics(),i.complete(),i.scope());
        var scan=scan();var error=assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileItemAttributes(scan.classNodes(),scan,edited));assertTrue(error.getMessage().contains("ITEM_ATTRIBUTES_GUAVA_DATA_REQUIRED"));
    }
    private record Spec(String owner,String name,String desc,boolean field){}
    private static Spec spec(boolean old,String member) {
        String item=type(old,"item"),modifier=type(old,"modifier"),attrs=type(old,"attributes"),attribute=type(old,"attribute"),slot=type(old,"slot"),op=modifier+"$Operation",props=item+"$Properties";
        return switch(member){
            case "override"->new Spec(item,"getDefaultAttributeModifiers","(L"+slot+";)L"+MULTIMAP+";",false);
            case "itemConstructor"->new Spec(item,"<init>","(L"+props+";)V",false);
            case "propertiesConstructor"->new Spec(props,"<init>","()V",false);
            case "modifierConstructor"->new Spec(modifier,"<init>","(Ljava/util/UUID;Ljava/lang/String;DL"+op+";)V",false);
            case "uuidDamage","uuidSpeed"->new Spec(item,member.equals("uuidDamage")?"BASE_ATTACK_DAMAGE_UUID":"BASE_ATTACK_SPEED_UUID","Ljava/util/UUID;",true);
            case "damage","speed"->new Spec(attrs,member.equals("damage")?"ATTACK_DAMAGE":"ATTACK_SPEED","L"+attribute+";",true);
            case "slot"->new Spec(slot,"MAINHAND","L"+slot+";",true);
            case "addition"->new Spec(op,"ADDITION","L"+op+";",true);
            case "builder"->new Spec(IMMUTABLE,"builder","()L"+BUILDER+";",false);
            case "put"->new Spec(BUILDER,"put","(Ljava/lang/Object;Ljava/lang/Object;)L"+BUILDER+";",false);
            case "build"->new Spec(BUILDER,"build","()L"+IMMUTABLE+";",false);
            case "empty"->new Spec(IMMUTABLE,"of","()L"+IMMUTABLE+";",false);
            default->throw new AssertionError(member);};
    }
    private static String type(boolean old,String name) {
        String prefix=old?"net/minecraft/entity/ai/attributes/":"net/minecraft/world/entity/ai/attributes/";
        return switch(name){case "item"->old?"net/minecraft/item/Item":"net/minecraft/world/item/Item";case "attribute"->prefix+"Attribute";case "attributes"->prefix+"Attributes";
            case "modifier"->prefix+"AttributeModifier";case "operation"->prefix+"AttributeModifier$Operation";case "slot"->old?"net/minecraft/inventory/EquipmentSlotType":"net/minecraft/world/entity/EquipmentSlot";
            case "multimap"->MULTIMAP;case "immutable"->IMMUTABLE;case "builder"->BUILDER;default->throw new AssertionError(name);};
    }
    private static MappingInputs changed(MappingInputs i,boolean old,Map<String,ApiIndex.ApiClass> index){return new MappingInputs(i.legacyOfficial(),i.targetOfficial(),i.legacySrg(),i.targetSrg(),
            old?new ApiIndex(index):i.legacyApi(),old?i.targetApi():new ApiIndex(index),i.sources(),i.diagnostics(),i.complete(),i.scope());}
    private static void assertRule(VerifiedLogicMappings.Result result,LegacySymbol source,TargetSymbol target){var rule=result.database().resolve(source);
        assertEquals(MappingStatus.RESOLVED,rule.status());assertEquals(MappingConfidence.VERIFIED,rule.confidence());assertEquals(target,rule.target());assertEquals(AdapterCatalog.LOGIC,result.adapterBindings().get(source));}
    private static MappingInputs data()throws Exception{if(inputs==null)inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));return inputs;}
    private static LegacyReferenceScanner.ScanResult scan()throws Exception {
        var path=Path.of(System.getProperty("legacybridge.fixtureRoot"),"item-attributes-1.16.5/build/libs/legacybridge-item-attributes-1.16.5-1.0.0.jar");var classes=new TreeMap<String,byte[]>();
        try(var zip=new ZipFile(path.toFile())){for(var entry:Collections.list(zip.entries()))if(entry.getName().endsWith(".class"))try(var in=zip.getInputStream(entry)){classes.put(entry.getName(),in.readAllBytes());}}
        return new LegacyReferenceScanner().scan(classes);
    }
}
