package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.archive.InputRejectedException;
import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.service.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import static org.junit.jupiter.api.Assertions.*;

/** Archive-only inspection of the independently built original; no fixture/game classes execute. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class IronSwordInspectorTest {
    private static final String MOD="legacybridge_iron_sword", NAME="bridge_sword";
    private static final String MODEL="assets/"+MOD+"/models/item/"+NAME+".json";
    private static final String RECIPE="data/"+MOD+"/recipes/"+NAME+".json";
    private static final String EN="assets/"+MOD+"/lang/en_us.json";
    private static MappingInputs mappings;
    private static byte[] original;
    @TempDir Path directory;

    @BeforeAll static void selectedExplicitData()throws Exception {
        mappings=new MappingInputsLoader().load(profile());
        original=Files.readAllBytes(fixture());
        assertTrue(mappings.complete());
    }

    @Test void fullClosedOriginalHasEvidenceButNeverAnEligibleCandidate()throws Exception {
        Path input=write(original); var before=Files.readAllBytes(input);
        var result=inspect(input,mappings);
        assertTrue(result.constructorRecognized(), result.toTree().toString());
        assertEquals(MOD,result.identity().orElseThrow().modId());
        assertEquals(NAME,result.identity().orElseThrow().registryName());
        assertEquals(List.of("SWORD_MINING_CONTRACT_CHANGED","SWORD_TOOL_ACTION_CONTRACT_CHANGED","SWORD_INHERITED_BEHAVIOR_UNQUALIFIED"),
            result.blockers().stream().map(EngineError::code).toList(), result.toTree().toString());
        assertEquals(Set.of("construction","classCount","methodCount","sourceBytecode","sourceLinkage","apiAudit","resources",
            "sourceDependencyMetadata","sourceDependencyGraph"), result.checks().keySet(), result.toTree().toString());
        alwaysUnsupported(result);
        assertArrayEquals(before,Files.readAllBytes(input)); assertArrayEquals(original,Files.readAllBytes(fixture()));
        try(var paths=Files.list(directory)){assertEquals(List.of(input),paths.toList());}
        assertThrows(UnsupportedOperationException.class,()->result.checks().clear());
        assertThrows(UnsupportedOperationException.class,()->((Map<?,?>)result.checks().get("construction")).clear());
        assertThrows(UnsupportedOperationException.class,()->result.blockers().clear());
    }

    @Test void insufficientExplicitDataDoesNotAcquireAnImplicitFallback()throws Exception {
        var incomplete=new MappingInputs(mappings.legacyOfficial(),mappings.targetOfficial(),mappings.legacySrg(),mappings.targetSrg(),
            mappings.legacyApi(),mappings.targetApi(),mappings.sources(),List.of("incomplete test data"),false);
        var result=inspect(write(original),incomplete);
        assertFalse(result.constructorRecognized()); assertTrue(result.identity().isEmpty());
        assertEquals(List.of("SWORD_DATA_SCOPE"),result.blockers().stream().map(EngineError::code).toList());
        alwaysUnsupported(result);
    }

    @Test void missingLegacyRegistryObjectDeclarationCannotBeInferredFromTheTarget()throws Exception {
        var legacy=new TreeMap<>(mappings.legacyApi().classes());
        assertNotNull(legacy.remove("net/minecraftforge/fml/RegistryObject"));
        var changed=new MappingInputs(mappings.legacyOfficial(),mappings.targetOfficial(),mappings.legacySrg(),mappings.targetSrg(),
            new ApiIndex(legacy),mappings.targetApi(),mappings.sources(),mappings.diagnostics(),true);
        var result=inspect(write(original),changed);
        assertTrue(result.constructorRecognized());
        assertEquals(List.of("SWORD_CONTRACT_REJECTED"),result.blockers().stream().map(EngineError::code).toList());
        assertFalse(result.checks().containsKey("sourceLinkage")); assertFalse(result.checks().containsKey("resources"));
        alwaysUnsupported(result);
    }

    @Test void targetNamespaceCannotMasqueradeAsTheSourceSelection()throws Exception {
        var entries=entries(); String classPath=entries.keySet().stream().filter(p->p.endsWith(".class")).findFirst().orElseThrow();
        var remapper=new Remapper(){
            @Override public String map(String owner){
                if(owner.equals("net/minecraftforge/fml/RegistryObject"))return "net/minecraftforge/registries/RegistryObject";
                if(owner.equals("net/minecraft/item/ItemTier"))return "net/minecraft/world/item/Tiers";
                if(owner.equals("net/minecraft/item/IItemTier"))return "net/minecraft/world/item/Tier";
                return owner.startsWith("net/minecraft/item/")?owner.replace("net/minecraft/item/","net/minecraft/world/item/"):owner;
            }
        };
        var writer=new ClassWriter(0); new ClassReader(entries.get(classPath)).accept(new ClassRemapper(writer,remapper),0);
        entries.put(classPath,writer.toByteArray());
        var result=inspect(write(jar(entries)),mappings);
        assertFalse(result.constructorRecognized()); assertTrue(result.identity().isEmpty());
        assertEquals(List.of("SWORD_SOURCE_NAMESPACE"),result.blockers().stream().map(EngineError::code).toList());
        alwaysUnsupported(result);
    }

    @Test void archiveCaptureLimitsReturnStructuredInputRejection()throws Exception {
        var entries=entries();
        for(int index=0;entries.size()<17;index++)entries.put("extra/"+index+".txt",new byte[]{1});
        Path tooMany=write(jar(entries));
        var countError=assertThrows(InputRejectedException.class,()->inspect(tooMany,mappings));
        assertEquals("SWORD_CAPTURE_LIMIT",countError.reasonCode());
        var oversized=entries(); oversized.put(EN,new byte[(64<<10)+1]);
        Path tooLarge=directory.resolve("large.jar"); Files.write(tooLarge,jar(oversized));
        var sizeError=assertThrows(InputRejectedException.class,()->inspect(tooLarge,mappings));
        assertEquals("ENTRY_SIZE_LIMIT",sizeError.reasonCode());
    }

    @Test void corruptedAndStaleFilesCannotProduceACompatibilityResult()throws Exception {
        Path corrupt=write(new byte[]{1,2,3,4});
        assertThrows(InputRejectedException.class,()->inspect(corrupt,mappings));
        Path input=directory.resolve("valid.jar");Files.write(input,original);
        var request=new IronSwordInspectionRequest(input,"0".repeat(64),MappingData.profile(profile()));
        var error=assertThrows(InputRejectedException.class,()->new IronSwordInspector().inspect(request,mappings));
        assertEquals("INPUT_CHANGED",error.reasonCode());
        assertArrayEquals(original,Files.readAllBytes(input));
    }

    @Test void cancellationDuringInspectionCannotPublishAnUnsupportedPartialReport()throws Exception {
        Path input=write(original); var reference=new AtomicReference<OperationControl>();
        var control=new OperationControl(OperationLimits.defaults(),progress->{
            if(progress.stage()==OperationStage.RESOURCE_VALIDATION)reference.get().requestCancel();
        }); reference.set(control);
        try(var ignored=control.enter()){
            assertThrows(OperationStoppedException.class,()->inspect(input,mappings));
        }
        assertArrayEquals(original,Files.readAllBytes(input));
        try(var paths=Files.list(directory)){assertEquals(List.of(input),paths.toList());}
    }

    @Test void replacingTheSourceDuringInspectionInvalidatesEvenARejectedSnapshot()throws Exception {
        Path input=write(original); var changed=entries();
        changed.put(EN,utf8("{\"item."+MOD+"."+NAME+"\":\"Changed during inspection\"}"));
        byte[] replacement=jar(changed);
        assertNotEquals(SimpleContentConverter.hash(original),SimpleContentConverter.hash(replacement));
        var control=new OperationControl(OperationLimits.defaults(),progress->{
            if(progress.stage()==OperationStage.RESOURCE_VALIDATION)try{Files.write(input,replacement);}catch(IOException failed){throw new UncheckedIOException(failed);}
        });
        try(var ignored=control.enter()){
            var error=assertThrows(InputRejectedException.class,()->inspect(input,mappings));
            assertEquals("INPUT_CHANGED",error.reasonCode());
        }
        assertArrayEquals(replacement,Files.readAllBytes(input));
        assertArrayEquals(original,Files.readAllBytes(fixture()));
    }

    @TestFactory Stream<DynamicTest> resourceEvidenceNeverSilentlyDropsUnknownOrMalformedData() {
        var mutations=new LinkedHashMap<String,Consumer<Map<String,byte[]>>>();
        mutations.put("missing model",e->e.remove(MODEL));
        mutations.put("unknown recipe sibling",e->e.put("data/"+MOD+"/recipes/extra.json",utf8("{}")));
        mutations.put("foreign loot table",e->e.put("data/other/loot_tables/blocks/extra.json",utf8("{}")));
        mutations.put("unknown manifest sibling",e->e.put("META-INF/unknown.json",utf8("{}")));
        mutations.put("model namespace",e->e.put(MODEL,utf8("{\"parent\":\"other:item/handheld\",\"textures\":{\"layer0\":\"minecraft:item/iron_sword\"}}")));
        mutations.put("model override",e->e.put(MODEL,utf8("{\"parent\":\"minecraft:item/handheld\",\"textures\":{\"layer0\":\"minecraft:item/iron_sword\"},\"overrides\":[]}")));
        mutations.put("duplicate model key",e->e.put(MODEL,utf8("{\"parent\":\"wrong\",\"parent\":\"minecraft:item/handheld\",\"textures\":{\"layer0\":\"minecraft:item/iron_sword\"}}")));
        mutations.put("malformed UTF8",e->e.put(MODEL,new byte[]{(byte)0xc3,(byte)0x28}));
        mutations.put("unknown recipe condition",e->e.put(RECIPE,utf8("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:iron_ingot\"}],\"result\":{\"item\":\""+MOD+":"+NAME+"\",\"count\":1},\"conditions\":[]}")));
        mutations.put("recipe extra output",e->e.put(RECIPE,utf8("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:iron_ingot\"}],\"result\":{\"item\":\""+MOD+":"+NAME+"\",\"count\":2}}")));
        mutations.put("translation unknown key",e->e.put(EN,utf8("{\"unknown\":\"Sword\"}")));
        mutations.put("translation formatting control",e->e.put(EN,utf8("{\"item."+MOD+"."+NAME+"\":\"Sword\\u202e\"}")));
        mutations.put("wrong pack version",e->e.put("pack.mcmeta",utf8("{\"pack\":{\"pack_format\":15,\"description\":\"wrong version\"}}")));
        return mutations.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            var archive=entries();e.getValue().accept(archive);Path input=write(jar(archive));byte[] before=Files.readAllBytes(input);
            var result=inspect(input,mappings);alwaysUnsupported(result);
            assertTrue(result.constructorRecognized(),result.toTree().toString());
            assertEquals(1,result.blockers().size());assertTrue(result.blockers().get(0).code().startsWith("SWORD_"));
            assertFalse(result.checks().containsKey("resources"));
            assertFalse(result.blockers().stream().anyMatch(b->b.code().equals("SWORD_MINING_CONTRACT_CHANGED")),"No successful resource audit is fabricated");
            assertArrayEquals(before,Files.readAllBytes(input));
        }));
    }

    private static void alwaysUnsupported(IronSwordInspection result){
        var tree=result.toTree();assertEquals("UNSUPPORTED",tree.get("status"));assertEquals(0,tree.get("transformationCount"));
        for(String key:List.of("convertible","candidateGenerated","classesExecuted","publicationAllowed","installationAllowed","installed"))assertEquals(false,tree.get(key),key);
        assertEquals("NOT_RUN",tree.get("runtimeValidation"));assertFalse(tree.containsKey("candidate"));assertFalse(tree.containsKey("output"));
    }
    private IronSwordInspection inspect(Path input,MappingInputs selected)throws Exception {
        return new IronSwordInspector().inspect(new IronSwordInspectionRequest(input,SimpleContentConverter.hash(Files.readAllBytes(input)),MappingData.profile(profile())),selected);
    }
    private Path write(byte[] bytes)throws IOException {Path path=directory.resolve(UUID.randomUUID()+".jar");Files.write(path,bytes,StandardOpenOption.CREATE_NEW);return path.toRealPath();}
    private static byte[] utf8(String text){return text.getBytes(StandardCharsets.UTF_8);}
    private static Map<String,byte[]> entries()throws IOException {
        var result=new TreeMap<String,byte[]>();
        try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(original))){ZipEntry e;while((e=zip.getNextEntry())!=null)if(!e.isDirectory())result.put(e.getName(),zip.readAllBytes());}
        return result;
    }
    private static byte[] jar(Map<String,byte[]> entries)throws IOException {
        var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){
            for(var e:new TreeMap<>(entries).entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}
        }return bytes.toByteArray();
    }
    private static Path profile(){return Path.of(System.getProperty("legacybridge.logicMappingProfile"));}
    private static Path fixture(){return Path.of(System.getProperty("legacybridge.fixtureRoot"),"iron-sword-1.16.5/build/libs/legacybridge-iron-sword-1.16.5-1.0.0.jar");}
}
