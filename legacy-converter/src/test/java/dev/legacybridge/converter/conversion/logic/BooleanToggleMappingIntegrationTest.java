package dev.legacybridge.converter.conversion.logic;

import dev.legacybridge.converter.mapping.*;
import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.transformation.*;
import dev.legacybridge.converter.transformation.linkage.OfflineLinkageVerifier;
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

/** Real fixture/archive/API declarations only: no original class or game code is executed. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class BooleanToggleMappingIntegrationTest {
    private static final String OWN="dev/legacybridge/fixture/booleantoggle/BooleanToggleBlock";
    private static final String OLD_USE="(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/math/BlockRayTraceResult;)Lnet/minecraft/util/ActionResultType;";
    private static final String NEW_USE="(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;";
    private static MappingInputs inputs;
    @AfterAll static void release(){inputs=null;}

    @Test void exactUseStateAliasesAndWorldFieldAreVerifiedWithoutGenericWorldAuthority()throws Exception{
        var scan=scan();var result=VerifiedLogicMappings.compileBooleanToggleBlock(scan.classNodes(),scan,data());
        assertRule(result,LegacySymbol.method(OWN,"func_225533_a_",OLD_USE),TargetSymbol.method(OWN,"m_6227_",NEW_USE));
        assertRule(result,LegacySymbol.method("net/minecraft/block/BlockState","func_235896_a_","(Lnet/minecraft/state/Property;)Ljava/lang/Object;"),
                TargetSymbol.method("net/minecraft/world/level/block/state/BlockState","m_61122_","(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Object;"));
        assertRule(result,LegacySymbol.field("net/minecraft/world/World","field_72995_K","Z"),TargetSymbol.field("net/minecraft/world/level/Level","f_46443_","Z"));
        assertRule(result,LegacySymbol.method("net/minecraft/world/World","func_180501_a","(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"),
                TargetSymbol.method("net/minecraft/world/level/Level","m_7731_","(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"));
        assertRule(result,LegacySymbol.method("net/minecraft/state/BooleanProperty","func_177716_a","(Ljava/lang/String;)Lnet/minecraft/state/BooleanProperty;"),
                TargetSymbol.method("net/minecraft/world/level/block/state/properties/BooleanProperty","m_61465_","(Ljava/lang/String;)Lnet/minecraft/world/level/block/state/properties/BooleanProperty;"));
        assertRule(result,LegacySymbol.field("java/lang/Boolean","FALSE","Ljava/lang/Boolean;"),TargetSymbol.field("java/lang/Boolean","FALSE","Ljava/lang/Boolean;"));
        assertEquals(AdapterCatalog.PLAIN_BLOCK_PROPERTIES,result.plainBlockRecipe().adapterId());
        var linked=new OfflineLinkageVerifier().verify(bytes(scan),result.sourceApi(),OfflineLinkageVerifier.CallbackPolicy.BOOLEAN_TOGGLE_BLOCK);
        assertTrue(linked.limitations().stream().anyMatch(l->l.contains("ignored boolean result")));
        assertTrue(linked.limitations().stream().anyMatch(l->l.contains("No arbitrary interaction")));
        assertFalse(result.rules().stream().anyMatch(r->String.valueOf(r.get("contract")).startsWith("horizontal.")));
    }

    @ParameterizedTest @ValueSource(strings={"logic","plain","horizontal","shape","durable"})
    void existingCompilersKeepTheirPreviousAuthority(String profile)throws Exception{
        var scan=scan();var data=data();assertThrows(TransformationException.class,()->{
            switch(profile){case "plain"->VerifiedLogicMappings.compilePlainBlock(scan.classNodes(),scan,data);
                case "horizontal"->VerifiedLogicMappings.compileHorizontalBlock(scan.classNodes(),scan,data);
                case "shape"->VerifiedLogicMappings.compileStaticShapeBlock(scan.classNodes(),scan,data);
                case "durable"->VerifiedLogicMappings.compileDurableItem(scan.classNodes(),scan,data);
                default->VerifiedLogicMappings.compile(scan.classNodes(),scan,data);}
        });
    }
    static Stream<Arguments> drifts(){return Stream.of(true,false).flatMap(old->Stream.concat(
            Stream.of("use","definition","cycle","setValue","write","create","any","add")
                    .flatMap(member->Stream.of("missing","private","static","final").map(change->Arguments.of(old,member,change))),
            Stream.of("side","success","consume","stateDefinition","defaultState")
                    .flatMap(member->Stream.of("private","static","final").map(change->Arguments.of(old,member,change)))));}
    @ParameterizedTest @MethodSource("drifts")
    void actualSourceAndTargetMembersRequireExactAccessKindFinalityAndPresence(boolean old,String member,String change)throws Exception{
        var original=data();var index=new TreeMap<>((old?original.legacyApi():original.targetApi()).classes());
        var spec=spec(old,member,false);alter(index,spec,change);
        var changed=new MappingInputs(original.legacyOfficial(),original.targetOfficial(),original.legacySrg(),original.targetSrg(),
                old?new ApiIndex(index):original.legacyApi(),old?original.targetApi():new ApiIndex(index),original.sources(),original.diagnostics(),original.complete(),original.scope());
        var scan=scan();assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileBooleanToggleBlock(scan.classNodes(),scan,changed),old+" "+member+" "+change);
    }
    @ParameterizedTest @ValueSource(strings={"useMissing","usePrivate","definitionMissing","definitionPrivate",
            "sideFinal","successFinal","stateDefinitionFinal","defaultStateFinal","cycleFinal","cycleStatic","writeStatic","writePrivate"})
    void independentOfflinePolicyRechecksSemanticDeclarationsNotOnlyJvmValidity(String mutation)throws Exception{
        var scan=scan();var approved=VerifiedLogicMappings.compileBooleanToggleBlock(scan.classNodes(),scan,data());
        var index=new TreeMap<>(approved.sourceApi().classes());String change=mutation.endsWith("Missing")?"missing":mutation.endsWith("Private")?"private":mutation.endsWith("Static")?"static":"final";
        String member=mutation.substring(0,mutation.length()-change.length());alter(index,spec(true,member,true),change);
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(bytes(scan),new ApiIndex(index),OfflineLinkageVerifier.CallbackPolicy.BOOLEAN_TOGGLE_BLOCK));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void nearerBlockDeclarationCannotChangeNativeUseDispatch(boolean old)throws Exception{
        var original=data();var index=new TreeMap<>((old?original.legacyApi():original.targetApi()).classes());
        String block=old?"net/minecraft/block/Block":"net/minecraft/world/level/block/Block";
        var parent=index.get(spec(old,"use",false).owner);var method=parent.methods().stream().filter(m->m.name().equals("use")).findFirst().orElseThrow();
        var type=index.get(block);var methods=new ArrayList<>(type.methods());methods.add(method);
        index.put(block,new ApiIndex.ApiClass(type.name(),type.access(),type.superName(),type.interfaces(),methods,type.fields()));
        var changed=new MappingInputs(original.legacyOfficial(),original.targetOfficial(),original.legacySrg(),original.targetSrg(),
                old?new ApiIndex(index):original.legacyApi(),old?original.targetApi():new ApiIndex(index),original.sources(),original.diagnostics(),original.complete(),original.scope());
        var scan=scan();assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileBooleanToggleBlock(scan.classNodes(),scan,changed));
    }

    private record Spec(String owner,String name,boolean field,int arity){}
    private static Spec spec(boolean old,String member,boolean production){
        String block=old?"net/minecraft/block/Block":"net/minecraft/world/level/block/Block";
        String definition=old?"net/minecraft/state/StateContainer":"net/minecraft/world/level/block/state/StateDefinition";
        String holder=old?"net/minecraft/state/StateHolder":"net/minecraft/world/level/block/state/StateHolder";
        String world=old?"net/minecraft/world/World":"net/minecraft/world/level/Level";
        return switch(member){
            case "use"->new Spec(old?"net/minecraft/block/AbstractBlock":"net/minecraft/world/level/block/state/BlockBehaviour",production?"func_225533_a_":"use",false,6);
            case "definition"->new Spec(block,production?"func_206840_a":"createBlockStateDefinition",false,1);
            case "cycle"->new Spec(holder,production?"func_235896_a_":"cycle",false,1);
            case "setValue"->new Spec(holder,production?"func_206870_a":"setValue",false,2);
            case "write"->new Spec(world,production?"func_180501_a":"setBlock",false,3);
            case "create"->new Spec(old?"net/minecraft/state/BooleanProperty":"net/minecraft/world/level/block/state/properties/BooleanProperty",production?"func_177716_a":"create",false,1);
            case "any"->new Spec(definition,production?"func_177621_b":"any",false,0);
            case "add"->new Spec(definition+"$Builder",production?"func_206894_a":"add",false,1);
            case "side"->new Spec(world,production?"field_72995_K":"isClientSide",true,-1);
            case "success","consume"->new Spec(old?"net/minecraft/util/ActionResultType":"net/minecraft/world/InteractionResult",member.toUpperCase(Locale.ROOT),true,-1);
            case "stateDefinition"->new Spec(block,production?"field_176227_L":"stateDefinition",true,-1);
            case "defaultState"->new Spec(block,production?"func_180632_j":"registerDefaultState",false,1);
            default->throw new AssertionError(member);
        };
    }
    private static void alter(Map<String,ApiIndex.ApiClass> index,Spec spec,String change){
        var type=index.get(spec.owner);assertNotNull(type);var methods=new ArrayList<>(type.methods());var fields=new ArrayList<>(type.fields());var members=spec.field?fields:methods;
        var matches=members.stream().filter(m->m.name().equals(spec.name)&&(spec.field||org.objectweb.asm.Type.getArgumentTypes(m.descriptor()).length==spec.arity)).toList();
        assertEquals(1,matches.size(),spec.toString());var member=matches.get(0);members.remove(member);
        int access=switch(change){case "private"->(member.access()&~(ACC_PUBLIC|ACC_PROTECTED))|ACC_PRIVATE;case "static"->member.access()^ACC_STATIC;case "final"->member.access()^ACC_FINAL;default->member.access();};
        if(!change.equals("missing"))members.add(new ApiIndex.ApiMember(member.name(),member.descriptor(),access));
        index.put(spec.owner,new ApiIndex.ApiClass(type.name(),type.access(),type.superName(),type.interfaces(),methods,fields));
    }
    private static void assertRule(VerifiedLogicMappings.Result result,LegacySymbol from,TargetSymbol to){var rule=result.database().resolve(from);
        assertEquals(MappingStatus.RESOLVED,rule.status());assertEquals(MappingConfidence.VERIFIED,rule.confidence());assertEquals(to,rule.target());assertEquals(AdapterCatalog.LOGIC,result.adapterBindings().get(from));}
    private static MappingInputs data()throws Exception{if(inputs==null)inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));return inputs;}
    private static Map<String,byte[]> bytes(LegacyReferenceScanner.ScanResult scan){var result=new TreeMap<String,byte[]>();scan.classes().forEach((n,c)->result.put(n,c.originalBytes()));return result;}
    private static LegacyReferenceScanner.ScanResult scan()throws Exception{
        var path=Path.of(System.getProperty("legacybridge.fixtureRoot"),"boolean-toggle-block-1.16.5/build/libs/legacybridge-boolean-toggle-block-1.16.5-1.0.0.jar");
        var classes=new TreeMap<String,byte[]>();try(var zip=new ZipFile(path.toFile())){for(var e:Collections.list(zip.entries()))if(e.getName().endsWith(".class"))try(var in=zip.getInputStream(e)){classes.put(e.getName(),in.readAllBytes());}}
        return new LegacyReferenceScanner().scan(classes);
    }
}
