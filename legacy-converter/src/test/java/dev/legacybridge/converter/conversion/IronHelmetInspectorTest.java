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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Archive-only inspection of the independently built original; no fixture/game classes execute. */
@EnabledIfSystemProperty(named="legacybridge.verifyCompositionEnvironment",matches="true")
class IronHelmetInspectorTest {
    private static final String MOD="legacybridge_iron_helmet", NAME="bridge_helmet";
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

    @AfterAll static void releaseSelectedData() {
        // A full native inventory is a per-suite fixture, not a process-lifetime test cache.
        // Other services in the shared regression JVM must retain their real memory guard.
        mappings=null;
        original=null;
    }

    @Test void fullClosedOriginalHasEvidenceButNeverAnEligibleCandidate()throws Exception {
        Path input=write(original); var before=Files.readAllBytes(input);
        var result=inspect(input,mappings);
        assertTrue(result.constructorRecognized(), result.toTree().toString());
        assertEquals(MOD,result.identity().orElseThrow().modId());
        assertEquals(NAME,result.identity().orElseThrow().registryName());
        assertEquals(List.of("HELMET_EQUIPMENT_TYPE_CONTRACT_CHANGED","HELMET_USE_CONTRACT_CHANGED","HELMET_INHERITED_BEHAVIOR_UNQUALIFIED"),
            result.blockers().stream().map(EngineError::code).toList(), result.toTree().toString());
        assertEquals(Set.of("construction","classCount","methodCount","sourceBytecode","sourceLinkage","apiAudit","resources",
            "sourceDependencyMetadata","sourceDependencyGraph"), result.checks().keySet(), result.toTree().toString());
        alwaysUnsupported(result);
        var apiAudit=(Map<?,?>)result.checks().get("apiAudit");
        var operation=(Map<?,?>)apiAudit.get("availableRuntimeOperation");
        assertEquals(dev.legacybridge.runtime.compat.AdapterCatalog.require(
                dev.legacybridge.runtime.compat.AdapterCatalog.IRON_HELMET_EMPTY_SLOT).referenceTree(),operation.get("adapter"));
        assertEquals(false,operation.get("conversionRecipeImplemented"));
        assertEquals(false,operation.get("runtimeArtifactVerified"));
        assertEquals("NOT_RUN",operation.get("nativeValidation"));
        assertThrows(UnsupportedOperationException.class,()->operation.clear());
        var headEffect=(Map<?,?>)apiAudit.get("availableHeadEquipEffect");
        assertEquals(dev.legacybridge.runtime.compat.AdapterCatalog.require(
                dev.legacybridge.runtime.compat.AdapterCatalog.IRON_HELMET_HEAD_EQUIP_EFFECTS).referenceTree(),headEffect.get("adapter"));
        assertEquals(false,headEffect.get("conversionRecipeImplemented"));
        assertEquals(false,headEffect.get("composedWithUse"));
        assertEquals(false,headEffect.get("runtimeArtifactVerified"));
        assertEquals("NOT_RUN",headEffect.get("nativeValidation"));
        assertEquals(4,((List<?>)headEffect.get("preconditions")).size());
        assertThrows(UnsupportedOperationException.class,()->headEffect.clear());
        assertThrows(UnsupportedOperationException.class,()->((List<?>)headEffect.get("preconditions")).clear());
        var dispenser=(Map<?,?>)apiAudit.get("availableDispenserOperation");
        assertEquals(dev.legacybridge.runtime.compat.AdapterCatalog.require(
                dev.legacybridge.runtime.compat.AdapterCatalog.IRON_HELMET_DISPENSER).referenceTree(),dispenser.get("adapter"));
        assertEquals(headEffect.get("adapter"),dispenser.get("usesPlayerHeadEffect"));
        assertEquals(false,dispenser.get("conversionRecipeImplemented"));
        assertEquals(false,dispenser.get("registeredForItem"));
        assertEquals(false,dispenser.get("runtimeArtifactVerified"));
        assertEquals("NOT_RUN",dispenser.get("nativeValidation"));
        assertEquals(5,((List<?>)dispenser.get("preconditions")).size());
        assertThrows(UnsupportedOperationException.class,()->dispenser.clear());
        assertThrows(UnsupportedOperationException.class,()->((List<?>)dispenser.get("preconditions")).clear());
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
        assertEquals(List.of("HELMET_DATA_SCOPE"),result.blockers().stream().map(EngineError::code).toList());
        alwaysUnsupported(result);
    }

    @Test void missingLegacyRegistryObjectDeclarationCannotBeInferredFromTheTarget()throws Exception {
        var legacy=new TreeMap<>(mappings.legacyApi().classes());
        assertNotNull(legacy.remove("net/minecraftforge/fml/RegistryObject"));
        var changed=new MappingInputs(mappings.legacyOfficial(),mappings.targetOfficial(),mappings.legacySrg(),mappings.targetSrg(),
            new ApiIndex(legacy),mappings.targetApi(),mappings.sources(),mappings.diagnostics(),true);
        var result=inspect(write(original),changed);
        assertTrue(result.constructorRecognized());
        assertEquals(List.of("HELMET_CONTRACT_REJECTED"),result.blockers().stream().map(EngineError::code).toList());
        assertFalse(result.checks().containsKey("sourceLinkage")); assertFalse(result.checks().containsKey("resources"));
        alwaysUnsupported(result);
    }

    @Test void targetNamespaceCannotMasqueradeAsTheSourceSelection()throws Exception {
        var entries=entries(); String classPath=entries.keySet().stream().filter(p->p.endsWith(".class")).findFirst().orElseThrow();
        var remapper=new Remapper(){
            @Override public String map(String owner){
                if(owner.equals("net/minecraftforge/fml/RegistryObject"))return "net/minecraftforge/registries/RegistryObject";
                if(owner.equals("net/minecraft/item/ArmorMaterial"))return "net/minecraft/world/item/ArmorMaterials";
                if(owner.equals("net/minecraft/item/IArmorMaterial"))return "net/minecraft/world/item/ArmorMaterial";
                if(owner.equals("net/minecraft/inventory/EquipmentSlotType"))return "net/minecraft/world/item/ArmorItem$Type";
                return owner.startsWith("net/minecraft/item/")?owner.replace("net/minecraft/item/","net/minecraft/world/item/"):owner;
            }
            @Override public String mapFieldName(String owner,String name,String descriptor){
                return owner.equals("net/minecraft/inventory/EquipmentSlotType")&&name.equals("HEAD")?"HELMET":name;
            }
        };
        var writer=new ClassWriter(0); new ClassReader(entries.get(classPath)).accept(new ClassRemapper(writer,remapper),0);
        entries.put(classPath,writer.toByteArray());
        var result=inspect(write(jar(entries)),mappings);
        assertFalse(result.constructorRecognized()); assertTrue(result.identity().isEmpty());
        assertEquals(List.of("HELMET_SOURCE_NAMESPACE"),result.blockers().stream().map(EngineError::code).toList());
        alwaysUnsupported(result);
    }

    @Test void archiveCaptureLimitsReturnStructuredInputRejection()throws Exception {
        var entries=entries();
        for(int index=0;entries.size()<17;index++)entries.put("extra/"+index+".txt",new byte[]{1});
        Path tooMany=write(jar(entries));
        var countError=assertThrows(InputRejectedException.class,()->inspect(tooMany,mappings));
        assertEquals("HELMET_CAPTURE_LIMIT",countError.reasonCode());
        var oversized=entries(); oversized.put(EN,new byte[(64<<10)+1]);
        Path tooLarge=directory.resolve("large.jar"); Files.write(tooLarge,jar(oversized));
        var sizeError=assertThrows(InputRejectedException.class,()->inspect(tooLarge,mappings));
        assertEquals("ENTRY_SIZE_LIMIT",sizeError.reasonCode());
    }

    @Test void corruptedAndStaleFilesCannotProduceACompatibilityResult()throws Exception {
        Path corrupt=write(new byte[]{1,2,3,4});
        assertThrows(InputRejectedException.class,()->inspect(corrupt,mappings));
        Path input=directory.resolve("valid.jar");Files.write(input,original);
        var request=new IronHelmetInspectionRequest(input,"0".repeat(64),MappingData.profile(profile()));
        var error=assertThrows(InputRejectedException.class,()->new IronHelmetInspector().inspect(request,mappings));
        assertEquals("INPUT_CHANGED",error.reasonCode());
        assertArrayEquals(original,Files.readAllBytes(input));
    }

    @Test void directorySelectionIsRejectedBeforeAnyArchiveRead()throws Exception {
        var request=new IronHelmetInspectionRequest(directory.toRealPath(),"0".repeat(64),MappingData.profile(profile()));
        var error=assertThrows(InputRejectedException.class,()->new IronHelmetInspector().inspect(request,mappings));
        assertEquals("HELMET_INPUT_PATH",error.reasonCode());
    }

    @TestFactory Stream<DynamicTest> constructorVariantsCannotAcquireCandidateAuthority() {
        var mutations=new LinkedHashMap<String,Consumer<ClassNode>>();
        mutations.put("unreviewed material",node->field(node,"net/minecraft/item/ArmorMaterial").name="DIAMOND");
        mutations.put("unreviewed equipment slot",node->field(node,"net/minecraft/inventory/EquipmentSlotType").name="CHEST");
        mutations.put("foreign material field",node->field(node,"net/minecraft/item/ArmorMaterial").owner="unreviewed/CustomMaterial");
        mutations.put("extra method",node->{var method=new MethodNode(ACC_PRIVATE|ACC_STATIC,"extra","()V",null,null);method.instructions.add(new InsnNode(RETURN));node.methods.add(method);});
        mutations.put("custom superclass",node->node.superName="unreviewed/EntryBase");
        mutations.put("extra owned field",node->node.fields.add(new FieldNode(ACC_PRIVATE|ACC_STATIC,"state","I",null,null)));
        mutations.put("oversized local frame",node->node.methods.get(0).maxLocals=65535);
        mutations.put("source version differs",node->node.version=V17);
        return mutations.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            var archive=entries();String path=archive.keySet().stream().filter(p->p.endsWith(".class")).findFirst().orElseThrow();
            var node=new ClassNode();new ClassReader(archive.get(path)).accept(node,0);e.getValue().accept(node);
            var writer=new ClassWriter(0);node.accept(writer);archive.put(path,writer.toByteArray());
            Path input=write(jar(archive));var before=Files.readAllBytes(input);var result=inspect(input,mappings);
            assertFalse(result.constructorRecognized());assertTrue(result.identity().isEmpty());
            assertEquals(List.of("HELMET_CONTRACT_REJECTED"),result.blockers().stream().map(EngineError::code).toList());
            assertFalse(result.checks().containsKey("sourceBytecode"));alwaysUnsupported(result);assertArrayEquals(before,Files.readAllBytes(input));
        }));
    }

    @TestFactory Stream<DynamicTest> sourceMetadataDependenciesAndOwnershipRemainClosed() {
        var mutations=new LinkedHashMap<String,Consumer<Map<String,byte[]>>>();
        mutations.put("second owned class",entries->{var path=entries.keySet().stream().filter(p->p.endsWith(".class")).findFirst().orElseThrow();entries.put("unexpected/Additional.class",entries.get(path));});
        mutations.put("foreign mandatory dependency",entries->entries.put("META-INF/mods.toml",utf8(new String(entries.get("META-INF/mods.toml"),StandardCharsets.UTF_8)
            +"\n[[dependencies."+MOD+"]]\nmodId=\"foreign\"\nmandatory=true\nversionRange=\"[1,)\"\nordering=\"AFTER\"\nside=\"BOTH\"\n")));
        mutations.put("client-only dependency",entries->entries.put("META-INF/mods.toml",utf8(new String(entries.get("META-INF/mods.toml"),StandardCharsets.UTF_8).replace("\"BOTH\"","\"CLIENT\""))));
        mutations.put("wrong loader",entries->entries.put("META-INF/mods.toml",utf8(new String(entries.get("META-INF/mods.toml"),StandardCharsets.UTF_8).replace("javafml","kotlinforforge"))));
        mutations.put("reserved mod identity",entries->entries.put("META-INF/mods.toml",utf8(new String(entries.get("META-INF/mods.toml"),StandardCharsets.UTF_8).replace(MOD,"minecraft"))));
        mutations.put("placeholder without implementation version",entries->{
            replaceText(entries,"META-INF/mods.toml","version=\"1.0.0\"","version=\"${file.jarVersion}\"");
            var manifest=new String(entries.get("META-INF/MANIFEST.MF"),StandardCharsets.UTF_8);
            assertTrue(manifest.contains("Implementation-Version: 1.0.0"));
            entries.put("META-INF/MANIFEST.MF",utf8(manifest.replaceAll("(?m)^Implementation-Version:[^\\r\\n]*(?:\\r?\\n|$)","")));
        });
        mutations.put("unknown version placeholder",entries->replaceText(entries,"META-INF/mods.toml","version=\"1.0.0\"","version=\"${file.otherVersion}\""));
        mutations.put("placeholder mixed with unsupported suffix",entries->replaceText(entries,"META-INF/mods.toml","version=\"1.0.0\"","version=\"${file.jarVersion}-mismatch\""));
        mutations.put("placeholder resolves invalid version",entries->{
            replaceText(entries,"META-INF/mods.toml","version=\"1.0.0\"","version=\"${file.jarVersion}\"");
            replaceText(entries,"META-INF/MANIFEST.MF","Implementation-Version: 1.0.0","Implementation-Version: ../unsafe");
        });
        mutations.put("orphan dependency owner",entries->replaceText(entries,"META-INF/mods.toml","[[dependencies."+MOD+"]]","[[dependencies.orphan_owner]]"));
        mutations.put("forge range excludes exact source",entries->replaceText(entries,"META-INF/mods.toml","versionRange=\"[36.2.42,37)\"","versionRange=\"[36.2.43,37)\""));
        mutations.put("minecraft dependency not mandatory",entries->replaceText(entries,"META-INF/mods.toml","modId=\"minecraft\"\nmandatory=true","modId=\"minecraft\"\nmandatory=false"));
        return mutations.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            var archive=entries();e.getValue().accept(archive);Path input=write(jar(archive));var before=Files.readAllBytes(input);
            var result=inspect(input,mappings);assertFalse(result.constructorRecognized());assertTrue(result.identity().isEmpty());
            assertFalse(result.checks().containsKey("resources"));alwaysUnsupported(result);assertArrayEquals(before,Files.readAllBytes(input));
        }));
    }

    @Test void exactManifestPlaceholderResolvesWithoutInventingVersionMismatchRules()throws Exception {
        for(boolean placeholder:new boolean[]{true,false}) {
            var archive=entries();
            if(placeholder)replaceText(archive,"META-INF/mods.toml","version=\"1.0.0\"","version=\"${file.jarVersion}\"");
            else replaceText(archive,"META-INF/MANIFEST.MF","Implementation-Version: 1.0.0","Implementation-Version: 2.0.0");
            Path input=write(jar(archive));var before=Files.readAllBytes(input);var result=inspect(input,mappings);
            assertTrue(result.constructorRecognized(),result.toTree().toString());
            assertEquals("1.0.0",result.identity().orElseThrow().modVersion(),"Explicit metadata version wins; only exact placeholder consults manifest");
            assertEquals(List.of("HELMET_EQUIPMENT_TYPE_CONTRACT_CHANGED","HELMET_USE_CONTRACT_CHANGED","HELMET_INHERITED_BEHAVIOR_UNQUALIFIED"),
                result.blockers().stream().map(EngineError::code).toList());
            assertTrue(result.checks().containsKey("sourceDependencyGraph"));assertTrue(result.checks().containsKey("resources"));
            alwaysUnsupported(result);assertArrayEquals(before,Files.readAllBytes(input));
        }
        assertArrayEquals(original,Files.readAllBytes(fixture()));
    }

    private static void replaceText(Map<String,byte[]> entries,String path,String before,String after) {
        String text=new String(entries.get(path),StandardCharsets.UTF_8).replace("\r\n","\n");
        assertTrue(text.contains(before),"Mutation anchor must exist: "+path+" / "+before);
        entries.put(path,utf8(text.replace(before,after)));
    }

    private static FieldInsnNode field(ClassNode node,String owner) {
        return node.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray()))
            .filter(i->i instanceof FieldInsnNode f&&f.owner.equals(owner)).map(i->(FieldInsnNode)i).findFirst().orElseThrow();
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
        mutations.put("model namespace",e->e.put(MODEL,utf8("{\"parent\":\"other:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/iron_helmet\"}}")));
        mutations.put("model override",e->e.put(MODEL,utf8("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/iron_helmet\"},\"overrides\":[]}")));
        mutations.put("duplicate model key",e->e.put(MODEL,utf8("{\"parent\":\"wrong\",\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/iron_helmet\"}}")));
        mutations.put("malformed UTF8",e->e.put(MODEL,new byte[]{(byte)0xc3,(byte)0x28}));
        mutations.put("unknown recipe condition",e->e.put(RECIPE,utf8("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:iron_ingot\"}],\"result\":{\"item\":\""+MOD+":"+NAME+"\",\"count\":1},\"conditions\":[]}")));
        mutations.put("recipe extra output",e->e.put(RECIPE,utf8("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:iron_ingot\"}],\"result\":{\"item\":\""+MOD+":"+NAME+"\",\"count\":2}}")));
        mutations.put("translation unknown key",e->e.put(EN,utf8("{\"unknown\":\"Helmet\"}")));
        mutations.put("translation formatting control",e->e.put(EN,utf8("{\"item."+MOD+"."+NAME+"\":\"Helmet\\u202e\"}")));
        mutations.put("wrong pack version",e->e.put("pack.mcmeta",utf8("{\"pack\":{\"pack_format\":15,\"description\":\"wrong version\"}}")));
        return mutations.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            var archive=entries();e.getValue().accept(archive);Path input=write(jar(archive));byte[] before=Files.readAllBytes(input);
            var result=inspect(input,mappings);alwaysUnsupported(result);
            assertTrue(result.constructorRecognized(),result.toTree().toString());
            assertEquals(1,result.blockers().size());assertTrue(result.blockers().get(0).code().startsWith("HELMET_"));
            assertFalse(result.checks().containsKey("resources"));
            assertFalse(result.blockers().stream().anyMatch(b->b.code().equals("HELMET_EQUIPMENT_TYPE_CONTRACT_CHANGED")),"No successful resource audit is fabricated");
            assertArrayEquals(before,Files.readAllBytes(input));
        }));
    }

    private static void alwaysUnsupported(IronHelmetInspection result){
        var tree=result.toTree();assertEquals("UNSUPPORTED",tree.get("status"));assertEquals(0,tree.get("transformationCount"));
        for(String key:List.of("convertible","candidateGenerated","classesExecuted","publicationAllowed","installationAllowed","installed"))assertEquals(false,tree.get(key),key);
        assertEquals("NOT_RUN",tree.get("runtimeValidation"));assertFalse(tree.containsKey("candidate"));assertFalse(tree.containsKey("output"));
    }
    private IronHelmetInspection inspect(Path input,MappingInputs selected)throws Exception {
        return new IronHelmetInspector().inspect(new IronHelmetInspectionRequest(input,SimpleContentConverter.hash(Files.readAllBytes(input)),MappingData.profile(profile())),selected);
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
    private static Path fixture(){return Path.of(System.getProperty("legacybridge.fixtureRoot"),"iron-helmet-1.16.5/build/libs/legacybridge-iron-helmet-1.16.5-1.0.0.jar");}
}
