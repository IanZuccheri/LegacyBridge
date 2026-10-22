package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.*;
import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Separate optional native enum-transform branch: never loads or executes fixture classes. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment", matches="true")
class HorizontalTransformsIntegrationTest {
    private static final String OWNED="dev/legacybridge/fixture/horizontaltransforms/HorizontalTransformsBlock";
    private static final String STATE="net/minecraft/block/BlockState";
    private static final String ROTATION="net/minecraft/util/Rotation";
    private static final String MIRROR="net/minecraft/util/Mirror";
    private static final String DIRECTION="net/minecraft/util/Direction";
    private static final String PROPERTY="net/minecraft/state/DirectionProperty";
    @TempDir Path directory;
    private static MappingInputs mappings;
    private static Path profile(){return Path.of(System.getProperty("legacybridge.logicMappingProfile"));}
    private static MappingInputs inputs()throws Exception{if(mappings==null)mappings=new MappingInputsLoader().load(profile());return mappings;}
    @AfterAll static void release(){mappings=null;}
    private static Path fixture(){return Path.of(System.getProperty("legacybridge.fixtureRoot"),
        "horizontal-transforms-1.16.5/build/libs/legacybridge-horizontal-transforms-1.16.5-1.0.0.jar");}

    @Test void preservesBothNativeOverridesThroughSerializedReadbackWithoutExport()throws Exception{
        var source=Files.readAllBytes(fixture());var out=directory.resolve("absent.jar");
        var result=LogicPreservingConverter.planHorizontalBlock(fixture(),out,inputs());assertTrue(result.validated(),()->result.report().toString());
        assertFalse(result.publicationAllowed());assertTrue(result.candidateSha256().isPresent());assertFalse(Files.exists(out));
        assertArrayEquals(source,Files.readAllBytes(fixture()));var report=result.report();
        assertEquals("horizontal-block-candidate-v1",report.get("candidateIdentity"));
        assertEquals(2,report.get("preservedClassCount"));assertEquals(10,report.get("preservedMethodCount"));
        assertEquals(0,report.get("replacedClassCount"));assertEquals(false,report.get("bytecodeExecuted"));assertEquals("NOT_RUN",report.get("runtimeValidation"));
        assertEquals(report.get("sourceHorizontalBlockContract"),report.get("horizontalBlockContract"));
        assertEquals(report.get("horizontalBlockContract"),report.get("reopenedHorizontalBlockContract"));
        assertEquals(true,((Map<?,?>)report.get("horizontalBlockContract")).get("orientationTransforms"));
        assertEquals("PASSED_SCOPED",((Map<?,?>)report.get("serializedOfflineLinkage")).get("status"));
        assertEquals(true,((Map<?,?>)report.get("candidateArchiveValidation")).get("scratchDeleted"));
        for(var raw:(List<?>)((Map<?,?>)report.get("artifactPlan")).get("entries")){
            var e=(Map<?,?>)raw;String name=(String)e.get("path");
            if((name.startsWith("assets/")||name.startsWith("data/"))&&!name.equals("data/minecraft/tags/blocks/mineable/pickaxe.json")){
                assertEquals("PRESERVE_BYTES",e.get("disposition"));assertEquals(e.get("sourceSha256"),e.get("targetSha256"));
            }
        }
        for(var raw:(List<?>)((Map<?,?>)report.get("transformationLog")).get("entries")){
            var e=(Map<?,?>)raw;assertEquals("VERIFIED",e.get("confidence"));assertNotNull(e.get("adapter"));
        }
        assertEquals(result,LogicPreservingConverter.planHorizontalBlock(fixture(),out,inputs()));
        System.out.println("HORIZONTAL_TRANSFORMS_CANDIDATE "+new CanonicalReportWriter().toJsonTree(report));
    }

    @Test void samePrivateServiceAndClosedPublicAuthorityApplyToOptionalPair()throws Exception{
        var out=directory.resolve("absent.jar");var direct=LogicPreservingConverter.planHorizontalBlock(fixture(),out,inputs());
        try(var service=new LegacyEngineService()){
            var request=new ConversionRequest(fixture(),out,MappingData.profile(profile()),
                Optional.of(hash(Files.readAllBytes(fixture()))),ConversionProfile.LOGIC_PRESERVING_V1);
            var result=service.planHorizontalBlock(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED,result.status(),()->result.toString());assertEquals(direct,result.value().orElseThrow());
        }
        assertFalse(LogicPreservingConverter.planPlainBlock(fixture(),out,inputs()).validated());
        for(var p:ConversionProfile.values()){
            if(p==ConversionProfile.SIMPLE_CONTENT_V1||p==ConversionProfile.DATAPACK_RECIPES_V1)continue;
            assertFalse(new LogicPreservingConverter(p).convert(fixture(),out,inputs()).converted(),p.toString());
        }
        assertFalse(Files.exists(out));
    }

    @Test void existingPlacementOnlyFixtureDoesNotAcquireOrientationBehavior()throws Exception{
        var original=Path.of(System.getProperty("legacybridge.fixtureRoot"),"horizontal-block-1.16.5/build/libs/legacybridge-horizontal-block-1.16.5-1.0.0.jar");
        var result=LogicPreservingConverter.planHorizontalBlock(original,directory.resolve("absent.jar"),inputs());
        assertTrue(result.validated(),()->result.report().toString());assertEquals(8,result.report().get("preservedMethodCount"));
        assertEquals(false,((Map<?,?>)result.report().get("horizontalBlockContract")).get("orientationTransforms"));
    }

    @ParameterizedTest @ValueSource(strings={"onlyRotate","onlyMirror","rotationNoop","mirrorNoop","fixedRotation","fixedMirror",
        "unknownRotationCall","unknownMirrorCall","wrongRotationOverload","wrongMirrorOverload","staticRotation","staticMirror",
        "rotateProtected","mirrorProtected","rotateStatic","mirrorStatic","wrongRotateProperty","wrongMirrorProperty",
        "rotateSuper","mirrorSuper","rotateExtraEffect","mirrorExtraEffect","rotateException","mirrorException",
        "rotateMissingDirectionCast","mirrorMissingDirectionCast"})
    void unsupportedOrientationContractsAreNotSilentlyPassedThrough(String mutation)throws Exception{
        var entries=read();var n=node(entries);var rotate=operation(n,ROTATION);var mirror=operation(n,MIRROR);
        var chosen=mutation.toLowerCase(Locale.ROOT).contains("mirror")?mirror:rotate;var enumOwner=chosen==mirror?MIRROR:ROTATION;
        switch(mutation){
            case "onlyRotate"->n.methods.remove(mirror);
            case "onlyMirror"->n.methods.remove(rotate);
            case "rotationNoop","mirrorNoop"->{chosen.instructions.clear();chosen.tryCatchBlocks.clear();chosen.localVariables=null;
                chosen.instructions.add(new VarInsnNode(ALOAD,1));chosen.instructions.add(new InsnNode(ARETURN));}
            case "fixedRotation","fixedMirror"->{
                var argument=code(chosen).stream().filter(i->i instanceof VarInsnNode v&&v.getOpcode()==ALOAD&&v.var==2).findFirst().orElseThrow();
                chosen.instructions.set(argument,new FieldInsnNode(GETSTATIC,enumOwner,chosen==mirror?"NONE":"CLOCKWISE_90","L"+enumOwner+";"));
            }
            case "unknownRotationCall","unknownMirrorCall"->enumCall(chosen,enumOwner).name="unreviewed";
            case "wrongRotationOverload","wrongMirrorOverload"->enumCall(chosen,enumOwner).desc="(II)I";
            case "staticRotation","staticMirror"->{var m=enumCall(chosen,enumOwner);chosen.instructions.set(m,new MethodInsnNode(INVOKESTATIC,m.owner,m.name,m.desc,false));}
            case "rotateProtected","mirrorProtected"->chosen.access=(chosen.access&~ACC_PUBLIC)|ACC_PROTECTED;
            case "rotateStatic","mirrorStatic"->chosen.access|=ACC_STATIC;
            case "wrongRotateProperty","wrongMirrorProperty"->{
                var f=code(chosen).stream().filter(i->i instanceof FieldInsnNode).map(i->(FieldInsnNode)i).findFirst().orElseThrow();
                f.owner="net/minecraft/state/properties/BlockStateProperties";f.name="field_208155_H";f.desc="L"+PROPERTY+";";
            }
            case "rotateSuper","mirrorSuper"->{chosen.instructions.clear();chosen.tryCatchBlocks.clear();chosen.localVariables=null;
                chosen.instructions.add(new VarInsnNode(ALOAD,0));chosen.instructions.add(new VarInsnNode(ALOAD,1));chosen.instructions.add(new VarInsnNode(ALOAD,2));
                chosen.instructions.add(new MethodInsnNode(INVOKESPECIAL,"net/minecraft/block/Block",chosen.name,chosen.desc,false));chosen.instructions.add(new InsnNode(ARETURN));}
            case "rotateExtraEffect","mirrorExtraEffect"->chosen.instructions.insertBefore(code(chosen).get(0),new MethodInsnNode(INVOKESTATIC,"java/lang/System","gc","()V",false));
            case "rotateException","mirrorException"->chosen.exceptions.add("java/lang/Exception");
            case "rotateMissingDirectionCast","mirrorMissingDirectionCast"->chosen.instructions.remove(code(chosen).stream()
                .filter(i->i instanceof TypeInsnNode t&&t.getOpcode()==CHECKCAST&&t.desc.equals(DIRECTION)).findFirst().orElseThrow());
            default->fail(mutation);
        }
        entries.put(OWNED+".class",bytes(n));var path=directory.resolve(mutation+".jar");write(path,entries);var original=Files.readAllBytes(path);
        var output=directory.resolve("absent.jar");var result=LogicPreservingConverter.planHorizontalBlock(path,output,inputs());
        assertFalse(result.validated(),mutation);assertTrue(result.candidateSha256().isEmpty());assertFalse(result.publicationAllowed());
        assertFalse(Files.exists(output));assertArrayEquals(original,Files.readAllBytes(path));
        assertEquals(false,result.report().get("transformationsCommitted"));
    }

    @ParameterizedTest @ValueSource(strings={"SERIALIZATION","PREPARED"})
    void optionalTransformPairUsesExistingCancellationBoundary(String point)throws Exception{
        inputs();var ref=new AtomicReference<OperationControl>();var control=new OperationControl(OperationLimits.defaults(),e->{
            if(e.stage().name().equals(point))ref.get().requestCancel();
        });ref.set(control);var output=directory.resolve("absent.jar");
        try(var scope=control.enter()){assertThrows(OperationStoppedException.class,()->LogicPreservingConverter.planHorizontalBlock(fixture(),output,inputs()));}
        assertFalse(Files.exists(output));
    }

    private static MethodNode operation(ClassNode n,String arg){return n.methods.stream().filter(m->m.desc.equals("(L"+STATE+";L"+arg+";)L"+STATE+";")).findFirst().orElseThrow();}
    private static MethodInsnNode enumCall(MethodNode method,String owner){return code(method).stream().filter(i->i instanceof MethodInsnNode m&&m.owner.equals(owner)).map(i->(MethodInsnNode)i).findFirst().orElseThrow();}
    private static List<AbstractInsnNode> code(MethodNode m){var out=new ArrayList<AbstractInsnNode>();for(var i:m.instructions)if(i.getOpcode()>=0)out.add(i);return out;}
    private static ClassNode node(Map<String,byte[]> entries){var n=new ClassNode();new ClassReader(entries.get(OWNED+".class")).accept(n,0);return n;}
    private static byte[] bytes(ClassNode n){var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);return w.toByteArray();}
    private static Map<String,byte[]> read()throws Exception{
        var entries=new TreeMap<String,byte[]>();try(var zip=new ZipFile(fixture().toFile())){
            for(var e:Collections.list(zip.entries()))if(!e.isDirectory())try(var input=zip.getInputStream(e)){entries.put(e.getName(),input.readAllBytes());}
        }return entries;
    }
    private static void write(Path path,Map<String,byte[]> entries)throws Exception{
        try(var zip=new ZipOutputStream(Files.newOutputStream(path))){for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}}
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
