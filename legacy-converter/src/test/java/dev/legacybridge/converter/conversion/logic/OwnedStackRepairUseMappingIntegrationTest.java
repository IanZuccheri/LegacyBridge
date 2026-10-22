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
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Trusted API indexes and owned class bytes only. No original or game class is loaded. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class OwnedStackRepairUseMappingIntegrationTest {
    private static final String OWN="dev/legacybridge/fixture/stackuse/StackRepairUseItem";
    private static final String OLD_ITEM="net/minecraft/item/Item",ITEM="net/minecraft/world/item/Item";
    private static final String OLD_STACK="net/minecraft/item/ItemStack",STACK="net/minecraft/world/item/ItemStack";
    private static final String FORGE="net/minecraftforge/common/extensions/IForgeItem";
    private static final String LEVEL="net/minecraft/world/level/Level",PLAYER="net/minecraft/world/entity/player/Player";
    private static final String LIVING="net/minecraft/world/entity/LivingEntity",HAND="net/minecraft/world/InteractionHand",HOLDER="net/minecraft/world/InteractionResultHolder";
    private static MappingInputs inputs;
    @AfterAll static void release(){inputs=null;}

    @Test void exactRecipesPreserveOwnedEndpointAndComposeOfficialSrgNames()throws Exception {
        var scan=scan("owned-stack-repair-use");var result=VerifiedLogicMappings.compileOwnedStackRepairUse(scan.classNodes(),scan,data());
        assertRule(result,LegacySymbol.method(OWN,"repairOne","(L"+OLD_STACK+";)Z"),TargetSymbol.method(OWN,"repairOne","(L"+STACK+";)Z"));
        for(var pair:Map.of("func_77952_i","m_41773_","func_77958_k","m_41776_").entrySet())
            assertRule(result,LegacySymbol.method(OLD_STACK,pair.getKey(),"()I"),TargetSymbol.method(STACK,pair.getValue(),"()I"));
        assertRule(result,LegacySymbol.method(OLD_STACK,"func_77951_h","()Z"),TargetSymbol.method(STACK,"m_41768_","()Z"));
        assertRule(result,LegacySymbol.method(OLD_STACK,"func_196085_b","(I)V"),TargetSymbol.method(STACK,"m_41721_","(I)V"));
        assertRule(result,LegacySymbol.method(OLD_STACK,"func_77973_b","()L"+OLD_ITEM+";"),TargetSymbol.method(STACK,"m_41720_","()L"+ITEM+";"));
        assertRule(result,LegacySymbol.method(OLD_ITEM+"$Properties","func_200918_c","(I)L"+OLD_ITEM+"$Properties;"),
                TargetSymbol.method(ITEM+"$Properties","m_41503_","(I)L"+ITEM+"$Properties;"));
        var contracts=new HashSet<String>();result.rules().forEach(r->contracts.add(String.valueOf(r.get("contract"))));
        assertTrue(contracts.containsAll(Set.of("owned-stack-damage.native-item-super.v1","owned-stack-damage.fresh-properties.v1",
                "owned-stack-damage.capacity-128-stack-one.v1","owned-stack-damage.received-item-identity-guard.v1",
                "owned-stack-damage.native-damaged-guard.v1","owned-stack-damage.observed-positive-damage.v1",
                "owned-stack-damage.observed-maximum-guard.v1","owned-stack-damage.single-guarded-decrement.v1")));
        assertFalse(contracts.stream().anyMatch(c->c.startsWith("durable.")||c.startsWith("item-attributes.")||c.startsWith("item-attribute-durability.")));
        assertNull(result.plainBlockRecipe());
        assertTrue(result.sourceApi().classes().size()<512);assertTrue(result.targetApi().classes().size()<512);
        assertNotNull(result.sourceApi().getClass(FORGE));assertNotNull(result.targetApi().getClass(FORGE));
        assertFalse(result.targetApi().hasClass("net/minecraft/world/item/SwordItem"));
        assertFalse(result.targetApi().hasClass("com/google/common/collect/ImmutableMultimap"));
    }

    @Test void observedNativeHooksDoNotGrantArbitraryHookCountWearOrNbtCalls()throws Exception {
        var scan=scan("owned-stack-repair-use");var result=VerifiedLogicMappings.compileOwnedStackRepairUse(scan.classNodes(),scan,data());
        for(var symbol:List.of(LegacySymbol.method(FORGE,"getDamage","(L"+OLD_STACK+";)I"),
                LegacySymbol.method(FORGE,"setDamage","(L"+OLD_STACK+";I)V"),
                LegacySymbol.method(OLD_STACK,"func_190916_E","()I"),
                LegacySymbol.method(OLD_STACK,"func_190918_g","(I)V"),
                LegacySymbol.method(OLD_ITEM,"func_77644_a","(L"+OLD_STACK+";Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/LivingEntity;)Z")))
            assertEquals(MappingStatus.MANUAL,result.database().resolve(symbol).status(),symbol.toString());
    }

    @Test void exactUseOverrideInheritedHandAndSameStackFactoriesHaveImplementedBindings()throws Exception {
        var scan=scan("owned-stack-repair-use");var result=VerifiedLogicMappings.compileOwnedStackRepairUse(scan.classNodes(),scan,data());
        var from=new LogicApiNamespace(data().legacyOfficial(),data().legacySrg(),data().legacyApi(),true);
        var to=new LogicApiNamespace(data().targetOfficial(),data().targetSrg(),data().targetApi(),false);
        String use="(L"+LEVEL+";L"+PLAYER+";L"+HAND+";)L"+HOLDER+";";
        assertEquals("func_77659_a",from.productionMethod(ITEM,"use",use));
        assertEquals("m_7203_",to.productionMethod(ITEM,"use",use));
        assertRule(result,LegacySymbol.method(OWN,from.productionMethod(ITEM,"use",use),from.productionDescriptor(use)),
                TargetSymbol.method(OWN,to.productionMethod(ITEM,"use",use),to.productionDescriptor(use)));
        assertRule(result,LegacySymbol.method(from.productionClass(ITEM),from.productionMethod(ITEM,"use",use),from.productionDescriptor(use)),
                TargetSymbol.method(to.productionClass(ITEM),to.productionMethod(ITEM,"use",use),to.productionDescriptor(use)));
        String hand="(L"+HAND+";)L"+STACK+";";
        for(String owner:List.of(PLAYER,LIVING))assertRule(result,
                LegacySymbol.method(from.productionClass(owner),"func_184586_b",from.productionDescriptor(hand)),
                TargetSymbol.method(to.productionClass(owner),"m_21120_",to.productionDescriptor(hand)));
        assertRule(result,LegacySymbol.field(from.productionClass(LEVEL),"field_72995_K","Z"),
                TargetSymbol.field(to.productionClass(LEVEL),"f_46443_","Z"));
        String factory="(Ljava/lang/Object;)L"+HOLDER+";";
        for(var pair:Map.of("func_226248_a_","m_19090_","func_226249_b_","m_19096_","func_226250_c_","m_19098_").entrySet())
            assertRule(result,LegacySymbol.method(from.productionClass(HOLDER),pair.getKey(),from.productionDescriptor(factory)),
                    TargetSymbol.method(to.productionClass(HOLDER),pair.getValue(),to.productionDescriptor(factory)));
        var contracts=new HashSet<String>();result.rules().forEach(r->contracts.add(String.valueOf(r.get("contract"))));
        assertTrue(contracts.containsAll(Set.of("owned-stack-repair-use.native-use-override.v1",
                "owned-stack-repair-use.received-player-hand.v1","owned-stack-repair-use.logical-side-field.v1",
                "owned-stack-repair-use.same-stack-success.v1","owned-stack-repair-use.same-stack-consume.v1",
                "owned-stack-repair-use.same-stack-pass.v1")));
        for(var symbol:List.of(LegacySymbol.method(from.productionClass(HOLDER),"func_226251_d_",from.productionDescriptor(factory)),
                LegacySymbol.method(from.productionClass(PLAYER),"func_184611_a","(L"+from.productionClass(HAND)+";L"+OLD_STACK+";)V")))
            assertEquals(MappingStatus.MANUAL,result.database().resolve(symbol).status());
    }

    static Stream<Arguments> memberDrifts(){return Stream.of(true,false).flatMap(old->Stream.of(
            "itemConstructor","propertiesConstructor","durability","getItem","isDamaged","getDamageValue","setDamageValue","getMaxDamage",
            "isDamageableItem","itemMaximum","canBeDepleted","hookDamage","hookSet","hookMaximum","hookDamaged","hookDamageable","hookSelf",
            "use","getItemInHand","success","consume","pass")
            .flatMap(member->Stream.of("missing","visibility","static","final","abstract","native","synchronized","bridge","varargs","synthetic")
                    .map(change->Arguments.of(old,member,change))));}
    @ParameterizedTest @MethodSource("memberDrifts")
    void directEndpointsAndDefaultHookAbiCannotDrift(boolean old,String name,String change)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());var s=spec(old,name);var c=index.get(s.owner);assertNotNull(c,s.toString());
        var methods=new ArrayList<>(c.methods());var found=methods.stream().filter(m->m.name().equals(s.name)&&m.descriptor().equals(s.desc)).toList();
        assertEquals(1,found.size(),s.toString());var m=found.get(0);methods.remove(m);
        int access=switch(change){case "visibility"->(m.access()&~(ACC_PUBLIC|ACC_PRIVATE|ACC_PROTECTED))|ACC_PROTECTED;
            case "static"->m.access()^ACC_STATIC;case "final"->m.access()^ACC_FINAL;case "abstract"->m.access()|ACC_ABSTRACT;
            case "native"->m.access()|ACC_NATIVE;case "synchronized"->m.access()|ACC_SYNCHRONIZED;case "bridge"->m.access()|ACC_BRIDGE;
            case "varargs"->m.access()|ACC_VARARGS;case "synthetic"->m.access()|ACC_SYNTHETIC;default->m.access();};
        if(!change.equals("missing"))methods.add(new ApiIndex.ApiMember(m.name(),m.descriptor(),access));
        index.put(c.name(),new ApiIndex.ApiClass(c.name(),c.access(),c.superName(),c.interfaces(),methods,c.fields()));
        reject(changed(i,old,index));
    }

    static Stream<Arguments> typeDrifts(){return Stream.of(true,false).flatMap(old->Stream.of("item","properties","stack","forge","level","player","living","hand","holder")
            .flatMap(type->Stream.of("missing","nonpublic","kind","final","parent","interfaces").map(change->Arguments.of(old,type,change))));}
    @ParameterizedTest @MethodSource("typeDrifts")
    void exactNativeClassKindsAndAncestryAreRequired(boolean old,String name,String change)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());String owner=type(old,name);var c=index.get(owner);assertNotNull(c);
        if(change.equals("missing"))index.remove(owner);else {
            int access=switch(change){case "nonpublic"->c.access()&~ACC_PUBLIC;case "kind"->c.access()^ACC_INTERFACE;case "final"->c.access()^ACC_FINAL;default->c.access();};
            index.put(owner,new ApiIndex.ApiClass(owner,access,change.equals("parent")?"java/lang/String":c.superName(),
                    change.equals("interfaces")?List.of("java/io/Serializable"):c.interfaces(),c.methods(),c.fields()));
        }
        reject(changed(i,old,index));
    }

    @ParameterizedTest @CsvSource({"true,hookDamage","true,hookSet","true,hookMaximum","true,hookDamaged","true,hookDamageable","true,hookSelf",
            "false,hookDamage","false,hookSet","false,hookMaximum","false,hookDamaged","false,hookDamageable"})
    void nativeItemCannotShadowReviewedDefaultHooks(boolean old,String hook)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());var c=index.get(type(old,"item"));var s=spec(old,hook);
        var methods=new ArrayList<>(c.methods());methods.add(new ApiIndex.ApiMember(s.name,s.desc,ACC_PUBLIC));
        index.put(c.name(),new ApiIndex.ApiClass(c.name(),c.access(),c.superName(),c.interfaces(),methods,c.fields()));reject(changed(i,old,index));
    }

    @ParameterizedTest @CsvSource({"true,item","false,item","true,stack","false,stack"})
    void duplicateNativeInterfacesAreNotEquivalentToTheReviewedHierarchy(boolean old,String type)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());var c=index.get(type(old,type));
        var interfaces=new ArrayList<>(c.interfaces());assertFalse(interfaces.isEmpty());interfaces.add(interfaces.get(0));
        index.put(c.name(),new ApiIndex.ApiClass(c.name(),c.access(),c.superName(),interfaces,c.methods(),c.fields()));reject(changed(i,old,index));
    }

    static Stream<Arguments> sideDrifts(){return Stream.of(true,false).flatMap(old->Stream.of("missing","descriptor","private","protected","static","notFinal","volatile","transient","synthetic","enum","duplicate")
            .map(change->Arguments.of(old,change)));}
    @ParameterizedTest @MethodSource("sideDrifts")
    void logicalSideRequiresTheExactPublicFinalInstanceBoolean(boolean old,String change)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());var c=index.get(type(old,"level"));
        var fields=new ArrayList<>(c.fields());var found=fields.stream().filter(f->f.name().equals("isClientSide")&&f.descriptor().equals("Z")).toList();
        assertEquals(1,found.size());var f=found.get(0);fields.remove(f);
        int access=switch(change){case "private"->(f.access()&~ACC_PUBLIC)|ACC_PRIVATE;case "protected"->(f.access()&~ACC_PUBLIC)|ACC_PROTECTED;
            case "static"->f.access()|ACC_STATIC;case "notFinal"->f.access()&~ACC_FINAL;case "volatile"->f.access()|ACC_VOLATILE;
            case "transient"->f.access()|ACC_TRANSIENT;case "synthetic"->f.access()|ACC_SYNTHETIC;case "enum"->f.access()|ACC_ENUM;default->f.access();};
        if(!change.equals("missing"))fields.add(new ApiIndex.ApiMember(f.name(),change.equals("descriptor")?"I":f.descriptor(),access));
        if(change.equals("duplicate"))fields.add(f);
        index.put(c.name(),new ApiIndex.ApiClass(c.name(),c.access(),c.superName(),c.interfaces(),c.methods(),fields));reject(changed(i,old,index));
    }

    @ParameterizedTest @CsvSource({"true,false","true,true","false,false","false,true"})
    void callsitePlayerCannotShadowTheLivingEntityHandDeclaration(boolean old,boolean makePrivate)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());var c=index.get(type(old,"player"));var s=spec(old,"getItemInHand");
        var methods=new ArrayList<>(c.methods());methods.add(new ApiIndex.ApiMember(s.name,s.desc,makePrivate?ACC_PRIVATE:ACC_PUBLIC));
        index.put(c.name(),new ApiIndex.ApiClass(c.name(),c.access(),c.superName(),c.interfaces(),methods,c.fields()));reject(changed(i,old,index));
    }

    static Stream<Arguments> hiddenHandDeclarations(){return Stream.of(true,false).flatMap(old->
            (old?Stream.of("entity","IForgeEntity"):Stream.of("entity","IForgeEntity","IForgePlayer","IForgeLivingEntity"))
                    .flatMap(owner->Stream.of(true,false).flatMap(official->Stream.of(true,false)
                            .map(makePrivate->Arguments.of(old,owner,official,makePrivate)))));}
    @ParameterizedTest @MethodSource("hiddenHandDeclarations")
    void hiddenAncestorAndInterfaceHandDeclarationsNeverPassNearestLookup(boolean old,String owner,boolean official,boolean makePrivate)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());
        String path=owner.equals("entity")?(old?"net/minecraft/entity/Entity":"net/minecraft/world/entity/Entity")
                :"net/minecraftforge/common/extensions/"+owner;
        var c=index.get(path);assertNotNull(c,path);var s=spec(old,"getItemInHand");
        var ns=new LogicApiNamespace(old?i.legacyOfficial():i.targetOfficial(),old?i.legacySrg():i.targetSrg(),old?i.legacyApi():i.targetApi(),old);
        String name=official?s.name:ns.productionMethod(LIVING,"getItemInHand","(L"+HAND+";)L"+STACK+";");
        assertTrue(c.methods().stream().noneMatch(m->m.name().equals(name)&&m.descriptor().equals(s.desc)),"Mutation must add a new shadow");
        var methods=new ArrayList<>(c.methods());methods.add(new ApiIndex.ApiMember(name,s.desc,makePrivate?ACC_PRIVATE:ACC_PUBLIC));
        index.put(c.name(),new ApiIndex.ApiClass(c.name(),c.access(),c.superName(),c.interfaces(),methods,c.fields()));reject(changed(i,old,index));
    }

    @ParameterizedTest @CsvSource({"true,level","false,level","false,player","false,living"})
    void duplicateUseHierarchyInterfacesAreNotASetEquivalent(boolean old,String type)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());var c=index.get(type(old,type));
        var interfaces=new ArrayList<>(c.interfaces());assertFalse(interfaces.isEmpty());interfaces.add(interfaces.get(0));
        index.put(c.name(),new ApiIndex.ApiClass(c.name(),c.access(),c.superName(),interfaces,c.methods(),c.fields()));reject(changed(i,old,index));
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void helperCannotAccidentallyCollideWithNewNativeDeclaration(boolean old)throws Exception {
        var i=data();var index=new TreeMap<>((old?i.legacyApi():i.targetApi()).classes());var c=index.get(type(old,"item"));
        var methods=new ArrayList<>(c.methods());methods.add(new ApiIndex.ApiMember("repairOne","(L"+type(old,"stack")+";)Z",ACC_PUBLIC));
        index.put(c.name(),new ApiIndex.ApiClass(c.name(),c.access(),c.superName(),c.interfaces(),methods,c.fields()));reject(changed(i,old,index));
    }

    @ParameterizedTest @ValueSource(strings={"isDamaged","isDamageable","sourceFoil","targetFoil"})
    void renamedOwnedHelperNeverBecomesAForgeHookOrPreviouslyReviewedCallback(String name)throws Exception {
        var i=data();var nodes=scan("owned-stack-repair-use").classNodes();
        String renamed=name;
        if(name.endsWith("Foil")) {
            boolean old=name.startsWith("source");var ns=new LogicApiNamespace(old?i.legacyOfficial():i.targetOfficial(),old?i.legacySrg():i.targetSrg(),old?i.legacyApi():i.targetApi(),old);
            renamed=ns.productionMethod(ITEM,"isFoil","(L"+STACK+";)Z");
        }
        var helper=nodes.get(OWN).methods.stream().filter(m->m.name.equals("repairOne")).findFirst().orElseThrow();helper.name=renamed;
        for(var method:nodes.get(OWN).methods) for(var instruction:method.instructions)
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call && call.owner.equals(OWN) && call.name.equals("repairOne"))call.name=renamed;
        var altered=rescan(nodes);var error=assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileOwnedStackRepairUse(altered.classNodes(),altered,i));
        assertTrue(error.getMessage().contains("OWNED_STACK_DAMAGE_HELPER_COLLISION"),error.getMessage());
    }

    @ParameterizedTest @ValueSource(strings={"logic","durable","attributes","attributeDurability","food","lifecycle","helperOnly"})
    void oldCompilerRoutesDoNotAcquireTheNewHelper(String name)throws Exception {
        var scan=scan("owned-stack-repair-use");var i=data();assertThrows(TransformationException.class,()->{
            switch(name){case "helperOnly"->VerifiedLogicMappings.compileOwnedStackDamage(scan.classNodes(),scan,i);
                case "durable"->VerifiedLogicMappings.compileDurableItem(scan.classNodes(),scan,i);
                case "attributes"->VerifiedLogicMappings.compileItemAttributes(scan.classNodes(),scan,i);
                case "attributeDurability"->VerifiedLogicMappings.compileItemAttributeDurability(scan.classNodes(),scan,i);
                case "food"->VerifiedLogicMappings.compileFood(scan.classNodes(),scan,i);
                case "lifecycle"->VerifiedLogicMappings.compile(scan.classNodes(),scan,i,true);
                default->VerifiedLogicMappings.compile(scan.classNodes(),scan,i);}
        });
    }

    @ParameterizedTest @ValueSource(strings={"item-attributes","item-attribute-durability","durable-item","owned-stack-damage"})
    void newCompilerDoesNotImplyCompositionWithOtherPrivateGraphs(String name)throws Exception {
        var scan=scan(name);assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileOwnedStackRepairUse(scan.classNodes(),scan,data()));
    }

    @Test void explicitCompleteInputsRemainRequiredAndUnrelatedGuavaIsNotRequired()throws Exception {
        var i=data();var guavaHashes=Set.of("972139718abc8a4893fa78cba8cf7b2c903f35c97aaf44fa3031b0669948b480",
                "a42edc9cab792e39fe39bb94f3fca655ed157ff87a8af78e1d6ba5b07c4a00ab");
        var sources=i.sources().stream().filter(s->!guavaHashes.contains(s.sha256())).toList();
        assertEquals(i.sources().size()-2,sources.size(),"Both explicit Guava inputs must really be removed");
        var reduced=new MappingInputs(i.legacyOfficial(),i.targetOfficial(),i.legacySrg(),i.targetSrg(),withoutGuava(i.legacyApi()),withoutGuava(i.targetApi()),
                sources,i.diagnostics(),i.complete(),i.scope());
        var scan=scan("owned-stack-repair-use");assertNotNull(VerifiedLogicMappings.compileOwnedStackRepairUse(scan.classNodes(),scan,reduced));
        var incomplete=new MappingInputs(i.legacyOfficial(),i.targetOfficial(),i.legacySrg(),i.targetSrg(),i.legacyApi(),i.targetApi(),i.sources(),i.diagnostics(),false,i.scope());
        reject(incomplete);
    }

    private static ApiIndex withoutGuava(ApiIndex api){var classes=new TreeMap<>(api.classes());classes.keySet().removeIf(n->n.startsWith("com/google/common/"));return new ApiIndex(classes);}

    private record Spec(String owner,String name,String desc){}
    private static Spec spec(boolean old,String name){String item=type(old,"item"),stack=type(old,"stack"),props=item+"$Properties";
        return switch(name){
            case "itemConstructor"->new Spec(item,"<init>","(L"+props+";)V");
            case "propertiesConstructor"->new Spec(props,"<init>","()V");
            case "durability"->new Spec(props,"durability","(I)L"+props+";");
            case "getItem"->new Spec(stack,name,"()L"+item+";");
            case "isDamaged","isDamageableItem"->new Spec(stack,name,"()Z");
            case "getDamageValue","getMaxDamage"->new Spec(stack,name,"()I");
            case "setDamageValue"->new Spec(stack,name,"(I)V");
            case "itemMaximum"->new Spec(item,"getMaxDamage","()I");
            case "canBeDepleted"->new Spec(item,name,"()Z");
            case "hookDamage"->new Spec(FORGE,"getDamage","(L"+stack+";)I");
            case "hookSet"->new Spec(FORGE,"setDamage","(L"+stack+";I)V");
            case "hookMaximum"->new Spec(FORGE,"getMaxDamage","(L"+stack+";)I");
            case "hookDamaged"->new Spec(FORGE,"isDamaged","(L"+stack+";)Z");
            case "hookDamageable"->new Spec(FORGE,"isDamageable","(L"+stack+";)Z");
            case "hookSelf"->new Spec(FORGE,old?"getItem":"self","()L"+item+";");
            case "use"->new Spec(item,name,"(L"+type(old,"level")+";L"+type(old,"player")+";L"+type(old,"hand")+";)L"+type(old,"holder")+";");
            case "getItemInHand"->new Spec(type(old,"living"),name,"(L"+type(old,"hand")+";)L"+stack+";");
            case "success","consume","pass"->new Spec(type(old,"holder"),name,"(Ljava/lang/Object;)L"+type(old,"holder")+";");
            default->throw new AssertionError(name);};}
    private static String type(boolean old,String name){return switch(name){case "item"->old?OLD_ITEM:ITEM;case "properties"->(old?OLD_ITEM:ITEM)+"$Properties";
        case "stack"->old?OLD_STACK:STACK;case "forge"->FORGE;
        case "level"->old?"net/minecraft/world/World":LEVEL;case "player"->old?"net/minecraft/entity/player/PlayerEntity":PLAYER;
        case "living"->old?"net/minecraft/entity/LivingEntity":LIVING;case "hand"->old?"net/minecraft/util/Hand":HAND;
        case "holder"->old?"net/minecraft/util/ActionResult":HOLDER;default->throw new AssertionError(name);};}
    private static MappingInputs changed(MappingInputs i,boolean old,Map<String,ApiIndex.ApiClass> index){return new MappingInputs(i.legacyOfficial(),i.targetOfficial(),i.legacySrg(),i.targetSrg(),
            old?new ApiIndex(index):i.legacyApi(),old?i.targetApi():new ApiIndex(index),i.sources(),i.diagnostics(),i.complete(),i.scope());}
    private static void reject(MappingInputs i)throws Exception {var scan=scan("owned-stack-repair-use");assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileOwnedStackRepairUse(scan.classNodes(),scan,i));}
    private static void assertRule(VerifiedLogicMappings.Result result,LegacySymbol source,TargetSymbol target){var rule=result.database().resolve(source);
        assertEquals(MappingStatus.RESOLVED,rule.status());assertEquals(MappingConfidence.VERIFIED,rule.confidence());assertEquals(target,rule.target());assertEquals(AdapterCatalog.LOGIC,result.adapterBindings().get(source));}
    private static MappingInputs data()throws Exception{if(inputs==null)inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));return inputs;}
    private static LegacyReferenceScanner.ScanResult rescan(Map<String,ClassNode> nodes){var bytes=new TreeMap<String,byte[]>();nodes.values().forEach(n->{var writer=new ClassWriter(0);n.accept(writer);bytes.put(n.name+".class",writer.toByteArray());});return new LegacyReferenceScanner().scan(bytes);}
    private static LegacyReferenceScanner.ScanResult scan(String fixture)throws Exception {
        String artifact=fixture.equals("durable-item")?"durable":fixture;
        var path=Path.of(System.getProperty("legacybridge.fixtureRoot"),fixture+"-1.16.5/build/libs/legacybridge-"+artifact+"-1.16.5-1.0.0.jar");var classes=new TreeMap<String,byte[]>();
        try(var zip=new ZipFile(path.toFile())){for(var entry:Collections.list(zip.entries()))if(entry.getName().endsWith(".class"))try(var in=zip.getInputStream(entry)){classes.put(entry.getName(),in.readAllBytes());}}
        return new LegacyReferenceScanner().scan(classes);
    }
}
