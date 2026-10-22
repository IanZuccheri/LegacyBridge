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

/** Actual source archive and declaration metadata only: no game or original class is loaded. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class HorizontalMappingIntegrationTest {
    private static final String OWN="dev/legacybridge/fixture/horizontalblock/HorizontalContentBlock";
    private static final String OLD_BLOCK="net/minecraft/block/Block";
    private static final String NEW_BLOCK="net/minecraft/world/level/block/Block";
    private static final String OLD_STATE="net/minecraft/block/BlockState";
    private static final String NEW_STATE="net/minecraft/world/level/block/state/BlockState";
    private static final String OLD_PROPERTY="net/minecraft/state/Property";
    private static final String NEW_PROPERTY="net/minecraft/world/level/block/state/properties/Property";
    private static final String OLD_HOLDER="net/minecraft/state/StateHolder";
    private static final String NEW_HOLDER="net/minecraft/world/level/block/state/StateHolder";
    private static final String OLD_CONTEXT="net/minecraft/item/BlockItemUseContext";
    private static final String NEW_CONTEXT="net/minecraft/world/item/context/BlockPlaceContext";
    private static final String OLD_DIRECTION="net/minecraft/util/Direction";
    private static final String NEW_DIRECTION="net/minecraft/core/Direction";
    private static MappingInputs data;
    @AfterAll static void release(){data=null;}

    @Test void inheritedRecipesResolveExactDeclarationsAndOwnedDispatchAliases()throws Exception {
        var scan=scan();var result=VerifiedLogicMappings.compileHorizontalBlock(scan.classNodes(),scan,inputs());
        assertMapping(result,LegacySymbol.method(OLD_STATE,"func_206870_a","(L"+OLD_PROPERTY+";Ljava/lang/Comparable;)Ljava/lang/Object;"),
                TargetSymbol.method(NEW_STATE,"m_61124_","(L"+NEW_PROPERTY+";Ljava/lang/Comparable;)Ljava/lang/Object;"));
        assertMapping(result,LegacySymbol.method(OLD_CONTEXT,"func_195992_f","()L"+OLD_DIRECTION+";"),
                TargetSymbol.method(NEW_CONTEXT,"m_8125_","()L"+NEW_DIRECTION+";"));
        assertMapping(result,LegacySymbol.field(OWN,"field_176227_L","Lnet/minecraft/state/StateContainer;"),
                TargetSymbol.field(OWN,"f_49792_","Lnet/minecraft/world/level/block/state/StateDefinition;"));
        assertMapping(result,LegacySymbol.method(OWN,"func_180632_j","(L"+OLD_STATE+";)V"),
                TargetSymbol.method(OWN,"m_49959_","(L"+NEW_STATE+";)V"));
        assertMapping(result,LegacySymbol.method(OWN,"func_206840_a","(Lnet/minecraft/state/StateContainer$Builder;)V"),
                TargetSymbol.method(OWN,"m_7926_","(Lnet/minecraft/world/level/block/state/StateDefinition$Builder;)V"));
        assertMapping(result,LegacySymbol.method(OWN,"func_196258_a","(L"+OLD_CONTEXT+";)L"+OLD_STATE+";"),
                TargetSymbol.method(OWN,"m_5573_","(L"+NEW_CONTEXT+";)L"+NEW_STATE+";"));
        for(var rule:result.rules())if(String.valueOf(rule.get("contract")).startsWith("horizontal.")){
            assertEquals("VERIFIED",rule.get("confidence"));
            assertEquals(AdapterCatalog.LOGIC,rule.get("adapter"),"Exact native renames use the implemented descriptor operation, not a placeholder shim");
        }
        assertTrue(result.rules().toString().contains("horizontal.protected-definition-override.v1.owned-override"));
        assertTrue(result.rules().toString().contains("horizontal.placement-override.v1.owned-override"));
    }

    @ParameterizedTest @ValueSource(strings={"logic","plain","durable"})
    void existingCompilersDoNotAcquireHorizontalAuthority(String profile)throws Exception {
        var scan=scan();var input=inputs();
        assertThrows(TransformationException.class,()->{
            switch(profile){
                case "plain"->VerifiedLogicMappings.compilePlainBlock(scan.classNodes(),scan,input);
                case "durable"->VerifiedLogicMappings.compileDurableItem(scan.classNodes(),scan,input);
                default->VerifiedLogicMappings.compile(scan.classNodes(),scan,input);
            }
        });
    }

    static Stream<Arguments> drifts(){
        return Stream.of("stateDefinitionMutable","registerDefaultMutable","registerDefaultStatic","registerDefaultPrivate",
                "builderAddStatic","setValueStatic","northMutable","contextShadow","stateShadow","directionNotFinal")
                .flatMap(m->Stream.of(Arguments.of(true,m),Arguments.of(false,m)));
    }
    @ParameterizedTest @MethodSource("drifts")
    void declarationDriftCannotUseAnInheritedOrProtectedShortcut(boolean legacy,String mutation)throws Exception {
        var scan=scan();var changed=changeInputs(legacy,mutation);
        assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileHorizontalBlock(scan.classNodes(),scan,changed),mutation);
    }

    @ParameterizedTest @ValueSource(strings={"privateField","privateDefaultSetter","finalDefinitionOverride","missingSetValue"})
    void independentOfflineGateChecksAccessAndDispatchAfterMappingApproval(String mutation)throws Exception {
        var scan=scan();var approved=VerifiedLogicMappings.compileHorizontalBlock(scan.classNodes(),scan,inputs());
        var index=new TreeMap<>(approved.sourceApi().classes());
        String owner=mutation.equals("missingSetValue")?OLD_HOLDER:OLD_BLOCK;
        var type=index.get(owner);assertNotNull(type);
        var methods=new ArrayList<>(type.methods());var fields=new ArrayList<>(type.fields());
        var members=mutation.equals("privateField")?fields:methods;
        String name=switch(mutation){case "privateField"->"field_176227_L";case "privateDefaultSetter"->"func_180632_j";
            case "finalDefinitionOverride"->"func_206840_a";default->"func_206870_a";};
        var member=members.stream().filter(m->m.name().equals(name)).findFirst().orElseThrow();members.remove(member);
        if(!mutation.equals("missingSetValue")){
            int access=mutation.equals("finalDefinitionOverride")?member.access()|ACC_FINAL:
                    (member.access()&~(ACC_PUBLIC|ACC_PROTECTED))|ACC_PRIVATE;
            members.add(new ApiIndex.ApiMember(member.name(),member.descriptor(),access));
        }
        index.put(owner,new ApiIndex.ApiClass(type.name(),type.access(),type.superName(),type.interfaces(),methods,fields));
        var bytes=new TreeMap<String,byte[]>();scan.classes().forEach((n,c)->bytes.put(n,c.originalBytes()));
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(bytes,new ApiIndex(index),
                OfflineLinkageVerifier.CallbackPolicy.HORIZONTAL_BLOCK),mutation);
    }

    private static void assertMapping(VerifiedLogicMappings.Result result,LegacySymbol source,TargetSymbol target){
        var rule=result.database().resolve(source);
        assertEquals(MappingStatus.RESOLVED,rule.status(),source.toString());
        assertEquals(MappingConfidence.VERIFIED,rule.confidence(),source.toString());
        assertEquals(target,rule.target(),source.toString());
        assertEquals(AdapterCatalog.LOGIC,result.adapterBindings().get(source));
    }

    private static MappingInputs changeInputs(boolean old,String mutation)throws Exception {
        var original=inputs();var index=new TreeMap<>((old?original.legacyApi():original.targetApi()).classes());
        String direction=old?OLD_DIRECTION:NEW_DIRECTION,holder=old?OLD_HOLDER:NEW_HOLDER;
        String owner=mutation.startsWith("stateDefinition")||mutation.startsWith("registerDefault")?old?OLD_BLOCK:NEW_BLOCK:
                mutation.equals("builderAddStatic")?old?"net/minecraft/state/StateContainer$Builder":"net/minecraft/world/level/block/state/StateDefinition$Builder":
                mutation.equals("setValueStatic")?holder:mutation.equals("contextShadow")?old?OLD_CONTEXT:NEW_CONTEXT:
                mutation.equals("stateShadow")?old?OLD_STATE:NEW_STATE:direction;
        var type=index.get(owner);assertNotNull(type,owner);
        var methods=new ArrayList<>(type.methods());var fields=new ArrayList<>(type.fields());int typeAccess=type.access();
        if(mutation.equals("directionNotFinal"))typeAccess &= ~ACC_FINAL;
        else if(mutation.equals("contextShadow"))methods.add(new ApiIndex.ApiMember("getHorizontalDirection","()L"+direction+";",ACC_PUBLIC));
        else if(mutation.equals("stateShadow"))methods.add(new ApiIndex.ApiMember("setValue","(L"+(old?OLD_PROPERTY:NEW_PROPERTY)+";Ljava/lang/Comparable;)Ljava/lang/Object;",ACC_PUBLIC));
        else {
            var members=mutation.equals("stateDefinitionMutable")||mutation.equals("northMutable")?fields:methods;
            String name=mutation.equals("stateDefinitionMutable")?"stateDefinition":mutation.startsWith("registerDefault")?"registerDefaultState":
                    mutation.equals("builderAddStatic")?"add":mutation.equals("setValueStatic")?"setValue":"NORTH";
            var member=members.stream().filter(m->m.name().equals(name)).findFirst().orElseThrow();members.remove(member);
            int access=member.access();
            if(mutation.endsWith("Mutable"))access &= ~ACC_FINAL;
            else if(mutation.endsWith("Static"))access |= ACC_STATIC;
            else if(mutation.endsWith("Private"))access=(access&~(ACC_PUBLIC|ACC_PROTECTED))|ACC_PRIVATE;
            members.add(new ApiIndex.ApiMember(member.name(),member.descriptor(),access));
        }
        index.put(owner,new ApiIndex.ApiClass(type.name(),typeAccess,type.superName(),type.interfaces(),methods,fields));
        var modified=new ApiIndex(index);
        return new MappingInputs(original.legacyOfficial(),original.targetOfficial(),original.legacySrg(),original.targetSrg(),
                old?modified:original.legacyApi(),old?original.targetApi():modified,original.sources(),original.diagnostics(),original.complete(),original.scope());
    }
    private static MappingInputs inputs()throws Exception {
        if(data==null)data=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));
        return data;
    }
    private static LegacyReferenceScanner.ScanResult scan()throws Exception {
        var path=Path.of(System.getProperty("legacybridge.fixtureRoot"),"horizontal-block-1.16.5/build/libs/legacybridge-horizontal-block-1.16.5-1.0.0.jar");
        var classes=new TreeMap<String,byte[]>();
        try(var zip=new ZipFile(path.toFile())){
            for(var entry:Collections.list(zip.entries()))if(entry.getName().endsWith(".class"))
                try(var stream=zip.getInputStream(entry)){classes.put(entry.getName(),stream.readAllBytes());}
        }
        return new LegacyReferenceScanner().scan(classes);
    }
}
