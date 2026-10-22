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

/** Real reobfuscated pair and trusted declaration mutations, never original class execution. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class HorizontalTransformsMappingIntegrationTest {
    private static MappingInputs inputs;
    @AfterAll static void release(){inputs=null;}
    @Test void twoOverridesAndInheritedGetValueUseActualDeclaringSymbols()throws Exception{
        var scan=scan();var rules=VerifiedLogicMappings.compileHorizontalBlock(scan.classNodes(),scan,data());
        var own="dev/legacybridge/fixture/horizontaltransforms/HorizontalTransformsBlock";
        assertRule(rules,LegacySymbol.method(own,"func_185499_a","(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/Rotation;)Lnet/minecraft/block/BlockState;"),
                TargetSymbol.method(own,"m_6843_","(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/Rotation;)Lnet/minecraft/world/level/block/state/BlockState;"));
        assertRule(rules,LegacySymbol.method(own,"func_185471_a","(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/Mirror;)Lnet/minecraft/block/BlockState;"),
                TargetSymbol.method(own,"m_6943_","(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/Mirror;)Lnet/minecraft/world/level/block/state/BlockState;"));
        assertRule(rules,LegacySymbol.method("net/minecraft/block/BlockState","func_177229_b","(Lnet/minecraft/state/Property;)Ljava/lang/Comparable;"),
                TargetSymbol.method("net/minecraft/world/level/block/state/BlockState","m_61143_","(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;"));
        var bytes=new TreeMap<String,byte[]>();scan.classes().forEach((name,value)->bytes.put(name,value.originalBytes()));
        var offline=new OfflineLinkageVerifier().verify(bytes,rules.sourceApi(),OfflineLinkageVerifier.CallbackPolicy.HORIZONTAL_BLOCK);
        assertTrue(offline.limitations().stream().anyMatch(l->l.contains("complete rotate/mirror override pair")));
        assertFalse(offline.limitations().stream().anyMatch(l->l.contains("Rotation/mirror overrides are absent")));
    }
    static Stream<Arguments> drifts(){return Stream.of(true,false).flatMap(old->Stream.of("rotateOverride","mirrorOverride","rotateEnum","mirrorEnum","getValue")
            .flatMap(member->Stream.of("final","static").map(change->Arguments.of(old,member,change))));}
    @ParameterizedTest @MethodSource("drifts")
    void newNativeContractRequiresExactAccessKindAndFinality(boolean old,String member,String change)throws Exception{
        var original=data();var index=new TreeMap<>((old?original.legacyApi():original.targetApi()).classes());
        String owner=member.endsWith("Override")?old?"net/minecraft/block/AbstractBlock":"net/minecraft/world/level/block/state/BlockBehaviour":
                member.equals("getValue")?old?"net/minecraft/state/StateHolder":"net/minecraft/world/level/block/state/StateHolder":
                member.equals("rotateEnum")?old?"net/minecraft/util/Rotation":"net/minecraft/world/level/block/Rotation":
                old?"net/minecraft/util/Mirror":"net/minecraft/world/level/block/Mirror";
        var type=index.get(owner);assertNotNull(type);var methods=new ArrayList<>(type.methods());
        String name=member.startsWith("rotate")?"rotate":member.startsWith("mirror")?"mirror":"getValue";
        var found=methods.stream().filter(m->m.name().equals(name)&&!m.descriptor().equals("(II)I")).toList();assertEquals(1,found.size());
        var method=found.get(0);methods.remove(method);methods.add(new ApiIndex.ApiMember(method.name(),method.descriptor(),method.access()|(change.equals("final")?ACC_FINAL:ACC_STATIC)));
        index.put(owner,new ApiIndex.ApiClass(type.name(),type.access(),type.superName(),type.interfaces(),methods,type.fields()));
        var changed=new MappingInputs(original.legacyOfficial(),original.targetOfficial(),original.legacySrg(),original.targetSrg(),
                old?new ApiIndex(index):original.legacyApi(),old?original.targetApi():new ApiIndex(index),original.sources(),original.diagnostics(),original.complete(),original.scope());
        var scan=scan();assertThrows(TransformationException.class,()->VerifiedLogicMappings.compileHorizontalBlock(scan.classNodes(),scan,changed));
    }
    private static void assertRule(VerifiedLogicMappings.Result result,LegacySymbol from,TargetSymbol to){var rule=result.database().resolve(from);
        assertEquals(MappingStatus.RESOLVED,rule.status());assertEquals(MappingConfidence.VERIFIED,rule.confidence());assertEquals(to,rule.target());assertEquals(AdapterCatalog.LOGIC,result.adapterBindings().get(from));}
    private static MappingInputs data()throws Exception{if(inputs==null)inputs=new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.logicMappingProfile")));return inputs;}
    private static LegacyReferenceScanner.ScanResult scan()throws Exception{
        var path=Path.of(System.getProperty("legacybridge.fixtureRoot"),"horizontal-transforms-1.16.5/build/libs/legacybridge-horizontal-transforms-1.16.5-1.0.0.jar");
        var classes=new TreeMap<String,byte[]>();try(var zip=new ZipFile(path.toFile())){
            for(var entry:Collections.list(zip.entries()))if(entry.getName().endsWith(".class"))try(var stream=zip.getInputStream(entry)){classes.put(entry.getName(),stream.readAllBytes());}
        }return new LegacyReferenceScanner().scan(classes);
    }
}
