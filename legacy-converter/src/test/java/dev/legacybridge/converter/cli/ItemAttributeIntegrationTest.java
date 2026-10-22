package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.*;
import dev.legacybridge.converter.conversion.logic.VerifiedLogicMappings;
import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import dev.legacybridge.converter.transformation.*;
import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.logic.ItemAttributeArtifactAdapter;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
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
import org.objectweb.asm.commons.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class ItemAttributeIntegrationTest {
    private static final String ENTRY="dev/legacybridge/fixture/attributes/ItemAttributeContent";
    private static final String ITEM="dev/legacybridge/fixture/attributes/AttributeContentItem";
    @TempDir Path directory;
    private static MappingInputs mappings;
    private static Path profile(){return Path.of(System.getProperty("legacybridge.logicMappingProfile"));}
    private static MappingInputs inputs()throws Exception{if(mappings==null)mappings=new MappingInputsLoader().load(profile());return mappings;}
    @AfterAll static void release(){mappings=null;}
    private static Path fixture(){return Path.of(System.getProperty("legacybridge.fixtureRoot"),
        "item-attributes-1.16.5/build/libs/legacybridge-item-attributes-1.16.5-1.0.0.jar");}

    @Test void serviceParityAndPublicProfilesRemainClosed()throws Exception{
        var output=directory.resolve("absent.jar");CompositionCandidate proof;
        try(var service=new LegacyEngineService()){
            var request=new ConversionRequest(fixture(),output,MappingData.profile(profile()),
                Optional.of(hash(Files.readAllBytes(fixture()))),ConversionProfile.LOGIC_PRESERVING_V1);
            var outcome=service.planItemAttributes(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED,outcome.status(),()->outcome.toString());
            proof=outcome.value().orElseThrow();
            assertThrows(IllegalArgumentException.class,()->service.planItemAttributes(new ConversionRequest(fixture(),output,MappingData.profile(profile()))));
        }
        assertEquals(proof,LogicPreservingConverter.planItemAttributes(fixture(),output,inputs()));
        assertEquals(6,ConversionProfile.values().length);
        for(var p:ConversionProfile.values()){
            var result=switch(p){
                case SIMPLE_CONTENT_V1,DATAPACK_RECIPES_V1->new SimpleContentConverter(p).convert(fixture(),output,inputs());
                default->new LogicPreservingConverter(p).convert(fixture(),output,inputs());
            };assertFalse(result.converted(),p.toString());
        }
        assertFalse(LogicPreservingConverter.planLogicWithRecipes(fixture(),output,inputs()).validated());
        assertFalse(LogicPreservingConverter.planDurableItem(fixture(),output,inputs()).validated());
        assertFalse(Files.exists(output));
    }
    @Test void candidatePreservesAllBodiesResourcesAndProofAcrossReadback()throws Exception{
        var original=Files.readAllBytes(fixture());var output=directory.resolve("absent.jar");
        var proof=LogicPreservingConverter.planItemAttributes(fixture(),output,inputs());
        assertTrue(proof.validated(),()->proof.report().toString());
        assertFalse(proof.publicationAllowed());assertTrue(proof.candidateSha256().isPresent());assertFalse(Files.exists(output));
        assertArrayEquals(original,Files.readAllBytes(fixture()));
        var r=proof.report();
        assertEquals("LEGACY_ITEM_ATTRIBUTE_CANDIDATE",r.get("reportType"));
        assertEquals(ItemAttributeArtifactAdapter.ID,r.get("candidateIdentity"));
        assertEquals("NOT_RUN",r.get("runtimeValidation"));assertEquals(false,r.get("bytecodeExecuted"));
        assertEquals(2,r.get("preservedClassCount"));assertEquals(6,r.get("preservedMethodCount"));assertEquals(0,r.get("replacedClassCount"));
        assertEquals("PASSED_EXACT_RULES",r.get("itemAttributeMappingCommutation"));
        assertEquals(r.get("sourceItemAttributeContract"),r.get("itemAttributeContract"));
        assertEquals(r.get("itemAttributeContract"),r.get("reopenedItemAttributeContract"));
        assertEquals("PASSED_SCOPED",((Map<?,?>)r.get("serializedOfflineLinkage")).get("status"));
        assertEquals(true,((Map<?,?>)r.get("candidateArchiveValidation")).get("scratchDeleted"));
        for(var raw:(List<?>)((Map<?,?>)r.get("artifactPlan")).get("entries")){
            var e=(Map<?,?>)raw;var name=(String)e.get("path");
            if(name.startsWith("assets/")||name.startsWith("data/")){
                assertEquals("PRESERVE_BYTES",e.get("disposition"));assertEquals(e.get("sourceSha256"),e.get("targetSha256"));
            }
        }
        for(var raw:(List<?>)((Map<?,?>)r.get("transformationLog")).get("entries")){
            var e=(Map<?,?>)raw;assertEquals("VERIFIED",e.get("confidence"));assertNotNull(e.get("adapter"));
        }
        assertEquals(proof,LogicPreservingConverter.planItemAttributes(fixture(),output,inputs()));
        System.out.println("ITEM_ATTRIBUTE_CANDIDATE "+new CanonicalReportWriter().toJsonTree(r));
    }
    @Test void declaredOperationMetadataHasRealImplementationsAndNoNativeAuthority()throws Exception{
        assertEquals(List.of(AdapterCatalog.LOGIC,AdapterCatalog.ATTRIBUTE_MANIFEST,AdapterCatalog.ATTRIBUTE_BINDINGS,
            AdapterCatalog.METADATA,AdapterCatalog.PACK),ItemAttributeArtifactAdapter.requiredAdapters());
        var manifest=new java.util.jar.Manifest(new java.io.ByteArrayInputStream(ItemAttributeArtifactAdapter.targetManifest("1.0.0")));
        assertEquals(ItemAttributeArtifactAdapter.ID,manifest.getMainAttributes().getValue("LegacyBridge-Profile"));
        assertEquals("STATIC-CANDIDATE-ONLY",manifest.getMainAttributes().getValue("LegacyBridge-Qualification"));
        assertEquals("1",manifest.getMainAttributes().getValue("LegacyBridge-Item-Attributes-Revision"));
        assertEquals(ItemAttributeArtifactAdapter.bindingTree(),TransformationBindings.forItemAttributes().bindingTree());
        for(var id:List.of(AdapterCatalog.ATTRIBUTE_MANIFEST,AdapterCatalog.ATTRIBUTE_BINDINGS)){
            var descriptor=AdapterCatalog.require(id);assertEquals("OFFLINE",descriptor.phase());
            assertFalse(descriptor.knownLimitations().isBlank());
        }
    }
    @Test void renamedOwnedSymbolsAreNotAHashWhitelist()throws Exception{
        var entries=read();var names=Map.of(ENTRY,"example/AttributeMod",ITEM,"example/AttributeItem");
        var fields=Set.of("MOD_ID","ITEMS","ATTRIBUTE_ITEM","MODIFIERS");
        for(var name:List.of(ENTRY,ITEM)){
            var mapped=new ClassNode();node(entries,name).accept(new ClassRemapper(mapped,new Remapper(){
                @Override public String map(String n){return names.getOrDefault(n,n);}
                @Override public String mapFieldName(String owner,String name,String desc){
                    return names.containsKey(owner)&&fields.contains(name)?"own_"+name:name;
                }
                @Override public String mapMethodName(String owner,String name,String desc){
                    return owner.equals(ENTRY)&&name.equals("createItem")?"constructContent":name;
                }
            }));entries.remove(name+".class");entries.put(mapped.name+".class",bytes(mapped));
        }
        var path=directory.resolve("renamed.jar");write(path,entries);
        var result=LogicPreservingConverter.planItemAttributes(path,directory.resolve("absent.jar"),inputs());
        assertTrue(result.validated(),()->result.report().toString());
    }
    @ParameterizedTest @ValueSource(strings={"missingRecipe","missingModel","unknownResource","unknownSerializer",
        "missingItem","foreignDependency","clientDependency","sourceExcluded","reservedId","signedMarker"})
    void archiveContractFailuresCannotProduceCandidate(String mutation)throws Exception{
        var entries=read();String toml="META-INF/mods.toml";
        switch(mutation){
            case "missingRecipe"->entries.keySet().removeIf(p->p.startsWith("data/")&&p.contains("/recipes/"));
            case "missingModel"->entries.keySet().removeIf(p->p.contains("/models/item/"));
            case "unknownResource"->entries.put("data/legacybridge_attributes/functions/unreviewed.mcfunction","say hello".getBytes(StandardCharsets.UTF_8));
            case "unknownSerializer"->{var path=entries.keySet().stream().filter(p->p.contains("/recipes/")).findFirst().orElseThrow();
                entries.put(path,"{\"type\":\"foreign:serializer\"}".getBytes(StandardCharsets.UTF_8));}
            case "missingItem"->entries.remove(ITEM+".class");
            case "foreignDependency"->entries.put(toml,(new String(entries.get(toml),StandardCharsets.UTF_8)+
                "\n[[dependencies.legacybridge_attributes]]\nmodId=\"foreign\"\nmandatory=true\nversionRange=\"[1,)\"\nordering=\"AFTER\"\nside=\"BOTH\"\n").getBytes(StandardCharsets.UTF_8));
            case "clientDependency"->entries.put(toml,new String(entries.get(toml),StandardCharsets.UTF_8).replace("\"BOTH\"","\"CLIENT\"").getBytes(StandardCharsets.UTF_8));
            case "sourceExcluded"->entries.put(toml,new String(entries.get(toml),StandardCharsets.UTF_8).replace("[1.16.5,1.17)","[1.20.1,1.21)").getBytes(StandardCharsets.UTF_8));
            case "reservedId"->entries.put(toml,new String(entries.get(toml),StandardCharsets.UTF_8).replace("legacybridge_attributes","minecraft").getBytes(StandardCharsets.UTF_8));
            case "signedMarker"->entries.put("META-INF/UNKNOWN.SF","Signature-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            default->fail(mutation);
        }
        var path=directory.resolve(mutation+".jar");write(path,entries);var before=Files.readAllBytes(path);
        var output=directory.resolve("absent.jar");var result=LogicPreservingConverter.planItemAttributes(path,output,inputs());
        assertFalse(result.validated(),mutation+" "+result.report());assertTrue(result.candidateSha256().isEmpty());
        assertFalse(Files.exists(output));assertArrayEquals(before,Files.readAllBytes(path));
    }
    @Test void missingGuavaIsExplicitFailureNotEngineClasspathFallback()throws Exception{
        var original=inputs();
        for(boolean old:List.of(true,false)){
            var api=old?original.legacyApi():original.targetApi();var index=new TreeMap<>(api.classes());
            index.keySet().removeIf(n->n.startsWith("com/google/common/"));
            var changed=new MappingInputs(original.legacyOfficial(),original.targetOfficial(),original.legacySrg(),original.targetSrg(),
                old?new ApiIndex(index):original.legacyApi(),old?original.targetApi():new ApiIndex(index),
                original.sources(),original.diagnostics(),original.complete());
            var result=LogicPreservingConverter.planItemAttributes(fixture(),directory.resolve("absent.jar"),changed);
            assertFalse(result.validated());assertTrue(result.candidateSha256().isEmpty());
        }
    }
    @ParameterizedTest @ValueSource(strings={"stack","locals"})
    void hostileFrameBoundsAreBlockedBeforeDataflowAllocation(String bound)throws Exception{
        var entries=read();var item=node(entries,ITEM);
        var ctor=item.methods.stream().filter(m->m.name.equals("<init>")).findFirst().orElseThrow();
        if(bound.equals("stack"))ctor.maxStack=65535;else ctor.maxLocals=65535;
        var writer=new ClassWriter(0);item.accept(writer);entries.put(ITEM+".class",writer.toByteArray());
        var path=directory.resolve("frames.jar");write(path,entries);var output=directory.resolve("absent.jar");
        var result=LogicPreservingConverter.planItemAttributes(path,output,inputs());
        assertFalse(result.validated());assertTrue(result.candidateSha256().isEmpty());assertFalse(Files.exists(output));
        assertTrue(result.report().toString().contains("Bounded unique methods and frames"),()->result.report().toString());
    }
    @Test void staleSelectionCannotAuthorizeCandidate()throws Exception{
        try(var service=new LegacyEngineService()){
            var path=directory.resolve("absent.jar");
            var request=new ConversionRequest(fixture(),path,MappingData.profile(profile()),Optional.of("0".repeat(64)),ConversionProfile.LOGIC_PRESERVING_V1);
            var result=service.planItemAttributes(request).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertNotEquals(EngineOutcome.Status.COMPLETED,result.status());assertFalse(Files.exists(path));
        }
    }
    @ParameterizedTest @ValueSource(strings={"SERIALIZATION","REOPEN","FINAL_LINKAGE","PREPARED"})
    void cancellationCannotPublishOrLeaveScratch(String point)throws Exception{
        inputs();int[] validations={0};var ref=new AtomicReference<OperationControl>();
        var observed=new AtomicReference<Set<Path>>(Set.of());var before=scratch();
        var control=new OperationControl(OperationLimits.defaults(),e->{
            boolean stop=e.stage()==OperationStage.SERIALIZATION&&point.equals("SERIALIZATION")
                ||e.stage()==OperationStage.PREPARED&&point.equals("PREPARED");
            if(point.equals("REOPEN")&&e.stage()==OperationStage.ARCHIVE_SCAN){
                var now=new HashSet<>(scratch());now.removeAll(before);if(!now.isEmpty()){observed.set(now);stop=true;}
            }
            if(e.stage()==OperationStage.BYTECODE_VALIDATION&&++validations[0]==3&&point.equals("FINAL_LINKAGE"))stop=true;
            if(stop)ref.get().requestCancel();
        });ref.set(control);var output=directory.resolve("absent.jar");
        try(var scope=control.enter()){
            assertThrows(OperationStoppedException.class,()->LogicPreservingConverter.planItemAttributes(fixture(),output,inputs()));
        }
        assertFalse(Files.exists(output));
        if(point.equals("REOPEN")){assertFalse(observed.get().isEmpty());for(var p:observed.get())assertFalse(Files.exists(p));}
    }
    private static Set<Path> scratch(){
        try(var files=Files.list(Path.of(System.getProperty("java.io.tmpdir")))){
            return files.filter(p->p.getFileName().toString().startsWith("legacybridge-compose-")).collect(java.util.stream.Collectors.toSet());
        }catch(java.io.IOException e){throw new AssertionError(e);}
    }
    private static ClassNode node(Map<String,byte[]> entries,String name){var n=new ClassNode();new ClassReader(entries.get(name+".class")).accept(n,0);return n;}
    private static byte[] bytes(ClassNode n){var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);n.accept(w);return w.toByteArray();}
    private static Map<String,byte[]> read()throws Exception{
        var entries=new TreeMap<String,byte[]>();try(var zip=new ZipFile(fixture().toFile())){
            for(var e:Collections.list(zip.entries()))if(!e.isDirectory())try(var input=zip.getInputStream(e)){entries.put(e.getName(),input.readAllBytes());}
        }return entries;
    }
    private static void write(Path path,Map<String,byte[]> entries)throws Exception{
        try(var zip=new ZipOutputStream(Files.newOutputStream(path))){
            for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}
        }
    }
    private static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
}
