package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.PreservedArtifactInspector;
import dev.legacybridge.converter.mapping.portable.PortableMappingBundle;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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

/** Pinned real reobfuscated originals and outputs, consumed only as data. No game or original class loading. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named="legacybridge.admissionBundle",matches=".+")
class PreservedArtifactInspectionEnvironmentTest {
    @TempDir Path temp;
    private static final String SOURCE="49dfbd7c65918c16ce8c057df992f7e9b7c188a3fe5634adb42bb1bc5dbfc19f";
    private static final String TARGET="78de0e2fd62ed83315518a1980ae0738b354531736a5eea73195d332aecc186d";
    private static final String DATA="0edd3ba641dbf858b7464096794a3caf4efca9d04ef4484a740a9599e9aec2bb";
    private Path original,candidate,bundle; private MappingInputs inputs;
    @BeforeAll void loadExplicitData() throws Exception {
        original=Path.of(System.getProperty("legacybridge.fixtureRoot"),"logic-preservation-1.16.5/build/libs/legacybridge-logic-1.16.5-1.0.0.jar");
        candidate=Path.of(System.getProperty("legacybridge.admissionCandidate")); bundle=Path.of(System.getProperty("legacybridge.admissionBundle"));
        assertEquals(SOURCE,sha(original)); assertEquals(TARGET,sha(candidate)); assertEquals(DATA,sha(bundle.resolve("manifest.json")));
        inputs=PortableMappingBundle.load(bundle,DATA);
    }
    private PreservedArtifactRequest request(Path source,Path target) throws Exception {
        return new PreservedArtifactRequest(source,sha(source),target,sha(target),MappingData.reviewedBundle(bundle,DATA));
    }
    @Test void realReadbackIsIdenticalAcrossCliAndServiceAndChangesNoFiles() throws Exception {
        var source=Files.readAllBytes(original); var target=Files.readAllBytes(candidate);
        var direct=new PreservedArtifactInspector().inspect(request(original,candidate),inputs);
        assertTrue(direct.validated(),direct.toTree().toString()); assertEquals(6,direct.classCount()); assertEquals(25,direct.methodCount());
        assertFalse((boolean)direct.toTree().get("installationAllowed")); assertEquals("NOT_RUN",direct.toTree().get("runtimeValidation"));
        var out=new ByteArrayOutputStream(); var err=new ByteArrayOutputStream();
        assertEquals(0,LegacyBridgeCli.run(argv(),new PrintStream(out),new PrintStream(err),Map.of()),err.toString());
        try(var engine=new LegacyEngineService()) {
            var outcome=engine.inspectPreservedArtifact(request(original,candidate)).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED,outcome.status(),outcome.toString());
            assertEquals(out.toString(StandardCharsets.UTF_8),new CanonicalReportWriter().toJsonTree(outcome.value().orElseThrow().toTree()));
            assertEquals(out.toString(StandardCharsets.UTF_8),new CanonicalReportWriter().toJsonTree(direct.toTree()));
        }
        assertArrayEquals(source,Files.readAllBytes(original)); assertArrayEquals(target,Files.readAllBytes(candidate));
        try(var files=Files.list(temp)) { assertEquals(0,files.count()); }
    }
    @ParameterizedTest @ValueSource(strings={"BODY_LITERAL","LAMBDA_HANDLE","MISSING_HELPER","EXTRA_CLASS","ASSET","PROFILE","ADAPTER_ABI","DEPENDENCY","ANNOTATION","MAX_STACK","SUPER","OPAQUE_ATTRIBUTE"})
    void matchingHashAndForgedSuccessDoNotAuthorizeAnAlteredArtifact(String mutation) throws Exception {
        var entries=read(candidate); String math="dev/legacybridge/fixture/logic/LogicMath.class";
        switch(mutation) {
            case "BODY_LITERAL" -> change(entries,math,n -> {
                var m=n.methods.stream().filter(v -> v.name.equals("twice")).findFirst().orElseThrow();
                for(var i:m.instructions.toArray()) if(i.getOpcode()==Opcodes.ICONST_2) m.instructions.set(i,new InsnNode(Opcodes.ICONST_3));
            });
            case "LAMBDA_HANDLE" -> change(entries,math,n -> {
                for(var m:n.methods) for(var i:m.instructions) if(i instanceof InvokeDynamicInsnNode d) {
                    var h=(Handle)d.bsmArgs[1]; d.bsmArgs[1]=new Handle(h.getTag(),h.getOwner(),"otherOwnedMethod",h.getDesc(),h.isInterface());
                }
            });
            case "MISSING_HELPER" -> entries.remove("dev/legacybridge/fixture/logic/ScoreRule.class");
            case "EXTRA_CLASS" -> entries.put("net/minecraft/Fake.class",entries.get(math));
            case "ASSET" -> entries.put("assets/legacybridge_logic/lang/en_us.json","{}".getBytes(StandardCharsets.UTF_8));
            case "PROFILE" -> entries.put("META-INF/MANIFEST.MF",new String(entries.get("META-INF/MANIFEST.MF"),StandardCharsets.UTF_8).replace("logic-preserving-v1","simple-content-v1").getBytes(StandardCharsets.UTF_8));
            case "ADAPTER_ABI" -> entries.put("META-INF/legacybridge/legacybridge_logic/adapters.json","{}".getBytes(StandardCharsets.UTF_8));
            case "DEPENDENCY" -> entries.put("META-INF/mods.toml",(new String(entries.get("META-INF/mods.toml"),StandardCharsets.UTF_8)+"\n[[dependencies.legacybridge_logic]]\nmodId=\"missing_lib\"\nmandatory=true\nversionRange=\"[1,)\"\nordering=\"NONE\"\nside=\"BOTH\"\n").getBytes(StandardCharsets.UTF_8));
            case "ANNOTATION" -> change(entries,math,n -> { n.visibleAnnotations=new ArrayList<>(); n.visibleAnnotations.add(new AnnotationNode("Ljava/lang/Deprecated;")); });
            case "MAX_STACK" -> change(entries,math,n -> n.methods.stream().filter(m -> m.name.equals("twice")).findFirst().orElseThrow().maxStack=0);
            case "SUPER" -> change(entries,"dev/legacybridge/fixture/logic/LogicItem.class",n -> n.superName="java/lang/Object");
            case "OPAQUE_ATTRIBUTE" -> change(entries,math,n -> { n.attrs=new ArrayList<>(); n.attrs.add(new Attribute("Unreviewed") {
                @Override protected ByteVector write(ClassWriter w,byte[] c,int l,int s,int v) { return new ByteVector().putByte(1); }
            }); });
            default -> throw new AssertionError(mutation);
        }
        Path edited=write(entries); byte[] before=Files.readAllBytes(edited);
        var result=new PreservedArtifactInspector().inspect(request(original,edited),inputs);
        assertFalse(result.validated(),mutation); assertFalse(result.blockers().isEmpty());
        assertFalse((boolean)result.toTree().get("installationAllowed")); assertArrayEquals(before,Files.readAllBytes(edited));
    }
    @Test void unknownOriginalDependencyCannotBeErasedByTargetMetadata() throws Exception {
        var entries=read(original);
        entries.put("META-INF/mods.toml",(new String(entries.get("META-INF/mods.toml"),StandardCharsets.UTF_8)+"\n[[dependencies.legacybridge_logic]]\nmodId=\"missing_lib\"\nmandatory=true\nversionRange=\"[1,)\"\nordering=\"NONE\"\nside=\"BOTH\"\n").getBytes(StandardCharsets.UTF_8));
        var result=new PreservedArtifactInspector().inspect(request(write(entries),candidate),inputs);
        assertFalse(result.validated()); assertEquals("ADMISSION_DEPENDENCY",result.blockers().get(0).code());
    }
    @Test void selectedHashAndSameFileAliasAreRefused() throws Exception {
        var wrong=new PreservedArtifactRequest(original,"a".repeat(64),candidate,TARGET,MappingData.bundle(bundle));
        assertEquals("ADMISSION_HASH",new PreservedArtifactInspector().inspect(wrong,inputs).blockers().get(0).code());
        Path local=Files.copy(original,temp.resolve("local-source.jar"));
        Path alias=Files.createLink(temp.resolve("alias.jar"),local);
        assertEquals("ADMISSION_PATH",new PreservedArtifactInspector().inspect(request(local,alias),inputs).blockers().get(0).code());
    }
    @Test void cancelledReadbackCannotReturnSuccessOrCreateAnArtifact() throws Exception {
        var selected=request(original,candidate); var reference=new AtomicReference<OperationControl>();
        var control=new OperationControl(OperationLimits.defaults(),p -> { if(p.stage()==OperationStage.BYTECODE_VALIDATION) reference.get().requestCancel(); }); reference.set(control);
        try(var scope=control.enter()) { assertThrows(OperationStoppedException.class,()->new PreservedArtifactInspector().inspect(selected,inputs)); }
        assertEquals(SOURCE,sha(original)); assertEquals(TARGET,sha(candidate));
        try(var files=Files.list(temp)) { assertEquals(0,files.count()); }
    }
    @Test void corruptInputAndChangedReviewedDataFailStructured() throws Exception {
        Path corrupt=Files.writeString(temp.resolve("corrupt.jar"),"not a zip");
        try(var engine=new LegacyEngineService()) {
            var bad=engine.inspectPreservedArtifact(request(original,corrupt)).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.FAILED,bad.status()); assertTrue(bad.value().isEmpty()); assertNotEquals("INTERNAL_ERROR",bad.error().orElseThrow().code());
            var changed=new PreservedArtifactRequest(original,SOURCE,candidate,TARGET,MappingData.reviewedBundle(bundle,"a".repeat(64)));
            var data=engine.inspectPreservedArtifact(changed).completion().toCompletableFuture().get(90,TimeUnit.SECONDS);
            assertEquals("MAPPING_DATA_CHANGED",data.error().orElseThrow().code());
        }
    }
    private String[] argv() { return new String[]{"inspect-preserved",original.toString(),candidate.toString(),"--original-sha256",SOURCE,"--candidate-sha256",TARGET,"--mapping-bundle",bundle.toString(),"--mapping-sha256",DATA}; }
    private static String sha(Path path) throws Exception { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static Map<String,byte[]> read(Path path) throws IOException {
        var result=new TreeMap<String,byte[]>(); try(var zip=new ZipFile(path.toFile())) { for(var e:zip.stream().toList()) if(!e.isDirectory()) try(var in=zip.getInputStream(e)) { result.put(e.getName(),in.readAllBytes()); } } return result;
    }
    private Path write(Map<String,byte[]> entries) throws IOException {
        Path path=temp.resolve(UUID.randomUUID()+".jar"); try(var out=new ZipOutputStream(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW))) { for(var e:entries.entrySet()) { out.putNextEntry(new ZipEntry(e.getKey())); out.write(e.getValue()); out.closeEntry(); } } return path;
    }
    private static void change(Map<String,byte[]> entries,String path,java.util.function.Consumer<ClassNode> edit) {
        var node=new ClassNode(); new ClassReader(entries.get(path)).accept(node,0); edit.accept(node); var writer=new ClassWriter(0); node.accept(writer); entries.put(path,writer.toByteArray());
    }
}
