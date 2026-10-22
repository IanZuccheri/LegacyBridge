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

/** Actual reobfuscated archive and API inventories; neither game nor original classes execute. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class StaticShapeMappingIntegrationTest {
    private static final String OWN="dev/legacybridge/fixture/staticshapeblock/StaticShapeBlock";
    private static final String OLD_DESC="(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/IBlockReader;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/shapes/ISelectionContext;)Lnet/minecraft/util/math/shapes/VoxelShape;";
    private static final String NEW_DESC="(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;";
    private static MappingInputs inputs;
    @AfterAll static void release(){inputs=null;}

    @Test void literalFactoryAndBothOwnedOverridesHaveExactVerifiedRulesAndDistinctStoneContext()throws Exception{
        var scan=scan();var result=VerifiedLogicMappings.compileStaticShapeBlock(scan.classNodes(),scan,data());
        assertRule(result,LegacySymbol.method("net/minecraft/block/Block","func_208617_a","(DDDDDD)Lnet/minecraft/util/math/shapes/VoxelShape;"),
                TargetSymbol.method("net/minecraft/world/level/block/Block","m_49796_","(DDDDDD)Lnet/minecraft/world/phys/shapes/VoxelShape;"));
        assertRule(result,LegacySymbol.method(OWN,"func_220053_a",OLD_DESC),TargetSymbol.method(OWN,"m_5940_",NEW_DESC));
        assertRule(result,LegacySymbol.method(OWN,"func_220071_b",OLD_DESC),TargetSymbol.method(OWN,"m_5939_",NEW_DESC));
        assertEquals(AdapterCatalog.STATIC_SHAPE_PROPERTIES,result.plainBlockRecipe().adapterId());
        var propertyRule=result.plainBlockRecipe().review();
        assertEquals(AdapterCatalog.STATIC_SHAPE_PROPERTIES,propertyRule.get("adapter"));
        assertEquals("static-shape.contextual-stone-properties.v1",propertyRule.get("contract"));
        assertFalse(result.rules().stream().anyMatch(r->AdapterCatalog.PLAIN_BLOCK_PROPERTIES.equals(r.get("adapter"))),
                "A bottom-half shape must not inherit the full-cube semantic adapter");
        var bytes=new TreeMap<String,byte[]>();scan.classes().forEach((n,c)->bytes.put(n,c.originalBytes()));
        var linked=new OfflineLinkageVerifier().verify(bytes,result.sourceApi(),OfflineLinkageVerifier.CallbackPolicy.STATIC_SHAPE_BLOCK);
        assertTrue(linked.limitations().stream().anyMatch(l->l.contains("bottom-half")));
        assertTrue(linked.limitations().stream().anyMatch(l->l.contains("native cache/default/lighting qualification")));
    }

    @ParameterizedTest @ValueSource(strings={"logic","plain","horizontal","durable"})
    void previousCompilersDoNotAcquireShapeAuthority(String profile)throws Exception{
        var scan=scan();var original=data();
        assertThrows(TransformationException.class,()->{
            switch(profile){
                case "plain"->VerifiedLogicMappings.compilePlainBlock(scan.classNodes(),scan,original);
                case "horizontal"->VerifiedLogicMappings.compileHorizontalBlock(scan.classNodes(),scan,original);
                case "durable"->VerifiedLogicMappings.compileDurableItem(scan.classNodes(),scan,original);
                default->VerifiedLogicMappings.compile(scan.classNodes(),scan,original);
            }
        });
    }

    static Stream<Arguments> drifts(){return Stream.of(true,false).flatMap(old->Stream.of("box","getShape","getCollisionShape")
            .flatMap(member->Stream.of("missing","private","static","final","abstract","native")
                    .map(change->Arguments.of(old,member,change))));}
    @ParameterizedTest @MethodSource("drifts")
    void nativeContractRejectsSourceOrTargetDeclarationDrift(boolean old,String member,String change)throws Exception{
        var original=data();var index=new TreeMap<>((old?original.legacyApi():original.targetApi()).classes());
        String owner=member.equals("box")?old?"net/minecraft/block/Block":"net/minecraft/world/level/block/Block":
                old?"net/minecraft/block/AbstractBlock":"net/minecraft/world/level/block/state/BlockBehaviour";
        alter(index,owner,member,change);
        var changed=new MappingInputs(original.legacyOfficial(),original.targetOfficial(),original.legacySrg(),original.targetSrg(),
                old?new ApiIndex(index):original.legacyApi(),old?original.targetApi():new ApiIndex(index),
                original.sources(),original.diagnostics(),original.complete(),original.scope());
        var scan=scan();assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileStaticShapeBlock(scan.classNodes(),scan,changed));
    }

    @ParameterizedTest @ValueSource(strings={"boxPrivate","boxMissing","shapeFinal","collisionStatic",
            "shapeMissing","shapePrivate","collisionMissing","collisionPrivate"})
    void independentOfflineGateRechecksAccessResolutionAndOverrideDispatch(String mutation)throws Exception{
        var scan=scan();var approved=VerifiedLogicMappings.compileStaticShapeBlock(scan.classNodes(),scan,data());
        var index=new TreeMap<>(approved.sourceApi().classes());
        String owner=mutation.startsWith("box")?"net/minecraft/block/Block":"net/minecraft/block/AbstractBlock";
        String name=mutation.startsWith("box")?"func_208617_a":mutation.startsWith("shape")?"func_220053_a":"func_220071_b";
        String change=mutation.endsWith("Private")?"private":mutation.endsWith("Missing")?"missing":mutation.endsWith("Final")?"final":"static";
        alter(index,owner,name,change);
        var bytes=new TreeMap<String,byte[]>();scan.classes().forEach((n,c)->bytes.put(n,c.originalBytes()));
        assertThrows(TransformationException.class,()->new OfflineLinkageVerifier().verify(bytes,new ApiIndex(index),
                OfflineLinkageVerifier.CallbackPolicy.STATIC_SHAPE_BLOCK));
    }

    private static void alter(Map<String,ApiIndex.ApiClass> index,String owner,String name,String change){
        var type=index.get(owner);assertNotNull(type,owner);var methods=new ArrayList<>(type.methods());
        var matches=methods.stream().filter(m->m.name().equals(name)).toList();assertEquals(1,matches.size(),name);
        var method=matches.get(0);methods.remove(method);
        int access=switch(change){case "private"->(method.access()&~(ACC_PUBLIC|ACC_PROTECTED))|ACC_PRIVATE;
            case "static"->method.access()^ACC_STATIC;case "final"->method.access()|ACC_FINAL;
            case "abstract"->method.access()|ACC_ABSTRACT;case "native"->method.access()|ACC_NATIVE;default->method.access();};
        if(!change.equals("missing"))methods.add(new ApiIndex.ApiMember(method.name(),method.descriptor(),access));
        index.put(owner,new ApiIndex.ApiClass(type.name(),type.access(),type.superName(),type.interfaces(),methods,type.fields()));
    }
    private static void assertRule(VerifiedLogicMappings.Result result,LegacySymbol from,TargetSymbol to){
        var rule=result.database().resolve(from);assertEquals(MappingStatus.RESOLVED,rule.status());
        assertEquals(MappingConfidence.VERIFIED,rule.confidence());assertEquals(to,rule.target());
        assertEquals(AdapterCatalog.LOGIC,result.adapterBindings().get(from));
    }
    private static MappingInputs data()throws Exception{if(inputs==null)inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));return inputs;}
    private static LegacyReferenceScanner.ScanResult scan()throws Exception{
        var path=Path.of(System.getProperty("legacybridge.fixtureRoot"),"static-shape-block-1.16.5/build/libs/legacybridge-static-shape-block-1.16.5-1.0.0.jar");
        var classes=new TreeMap<String,byte[]>();try(var zip=new ZipFile(path.toFile())){
            for(var entry:Collections.list(zip.entries()))if(entry.getName().endsWith(".class"))try(var stream=zip.getInputStream(entry)){classes.put(entry.getName(),stream.readAllBytes());}
        }return new LegacyReferenceScanner().scan(classes);
    }
}
