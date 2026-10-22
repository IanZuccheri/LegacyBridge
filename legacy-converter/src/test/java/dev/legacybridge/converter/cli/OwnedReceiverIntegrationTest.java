package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.*;
import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
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

/** Real Forge 36 reobfuscated archive, never defined/initialized or executed. */
@EnabledIfSystemProperty(named = "legacybridge.verifyCompositionEnvironment", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OwnedReceiverIntegrationTest {
    private static final String CONTRACT = "dev/legacybridge/fixture/receiver/api/Scoring";
    private static final String HELPER = "dev/legacybridge/fixture/receiver/impl/Counter";
    private static final String ITEM = "dev/legacybridge/fixture/receiver/ReceiverItem";
    @TempDir Path directory;
    private static MappingInputs mappings;
    private static Path profile() { return Path.of(System.getProperty("legacybridge.logicMappingProfile")); }
    private static MappingInputs inputs() throws Exception { if (mappings == null) mappings = new MappingInputsLoader().load(profile()); return mappings; }
    @AfterAll static void release() { mappings = null; }
    private static Path fixture() { return Path.of(System.getProperty("legacybridge.fixtureRoot"), "owned-receiver-1.16.5/build/libs/legacybridge-owned-receiver-1.16.5-1.0.0.jar"); }

    @Test @Order(1) void boundedServiceAgreesAndAllExistingRoutesRemainClosed() throws Exception {
        var output = directory.resolve("never-published.jar"); CompositionCandidate serviceProof;
        try (var service = new LegacyEngineService()) {
            var request = new ConversionRequest(fixture(), output, MappingData.profile(profile()), Optional.of(hash(Files.readAllBytes(fixture()))), ConversionProfile.LOGIC_PRESERVING_V1);
            var result = service.planOwnedReceiverHandles(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, result.status(), result.toString()); serviceProof = result.value().orElseThrow();
            assertTrue(serviceProof.validated(), serviceProof.report().toString());
            assertThrows(IllegalArgumentException.class, () -> service.planOwnedReceiverHandles(new ConversionRequest(fixture(), output, MappingData.profile(profile()))));
        }
        assertEquals(serviceProof, LogicPreservingConverter.planOwnedReceiverHandles(fixture(), output, inputs()));
        for (var existing : ConversionProfile.values()) {
            var ordinary = switch (existing) {
                case SIMPLE_CONTENT_V1, DATAPACK_RECIPES_V1 -> new SimpleContentConverter(existing).convert(fixture(), output, inputs());
                default -> new LogicPreservingConverter(existing).convert(fixture(), output, inputs());
            };
            assertFalse(ordinary.converted(), existing.toString());
        }
        assertFalse(LogicPreservingConverter.planLogicWithRecipes(fixture(), output, inputs()).validated());
        assertFalse(LogicPreservingConverter.planOwnedConstructors(fixture(), output, inputs()).validated());
        assertFalse(LogicPreservingConverter.planOwnedHierarchy(fixture(), output, inputs()).validated());
        assertFalse(LogicPreservingConverter.planOwnedInterfaceDefaults(fixture(), output, inputs()).validated());
        assertFalse(Files.exists(output)); assertEquals(6, ConversionProfile.values().length);
    }

    @Test void realBoundReceiverAndCounterSurviveReadback() throws Exception {
        byte[] original = Files.readAllBytes(fixture()); var output = directory.resolve("absent.jar");
        var candidate = LogicPreservingConverter.planOwnedReceiverHandles(fixture(), output, inputs());
        assertTrue(candidate.validated(), candidate.report().toString()); assertFalse(candidate.publicationAllowed());
        assertTrue(candidate.candidateSha256().isPresent()); assertFalse(Files.exists(output)); assertArrayEquals(original, Files.readAllBytes(fixture()));
        var report = candidate.report(); assertEquals("LEGACY_OWNED_RECEIVER_CANDIDATE", report.get("reportType"));
        assertEquals("owned-receiver-candidate-v1", report.get("candidateIdentity"));
        assertEquals("NOT_RUN", report.get("runtimeValidation")); assertEquals(3, report.get("preservedClassCount"));
        assertEquals(9, report.get("preservedMethodCount")); assertEquals(0, report.get("replacedClassCount"));
        assertEquals("PASSED_EXACT_RULES", report.get("receiverMappingCommutation"));
        assertEquals(report.get("ownedReceiver"), report.get("reopenedOwnedReceiver"));
        var proof = (Map<?,?>) report.get("ownedReceiver");
        assertEquals(HELPER, ((Map<?,?>)proof.get("implementation")).get("owner"));
        assertEquals("score", ((Map<?,?>)proof.get("implementation")).get("name"));
        assertEquals("PROVEN_NON_NULL_THIS", proof.get("receiverRequirement"));
        assertEquals(H_INVOKEVIRTUAL, proof.get("handleKind"));
        assertEquals("PASSED_SCOPED", ((Map<?,?>)report.get("serializedOfflineLinkage")).get("status"));
        assertEquals(2, ((Map<?,?>)report.get("serializedOfflineLinkage")).get("lambdaSiteCount"));
        assertEquals("PASSED_STATIC_REOPENED", ((Map<?,?>)report.get("bytecodeValidation")).get("status"));
        assertEquals(true, ((Map<?,?>)report.get("candidateArchiveValidation")).get("scratchDeleted"));
        for (var raw : (List<?>) ((Map<?,?>)report.get("artifactPlan")).get("entries")) {
            var entry = (Map<?,?>)raw; var path = (String)entry.get("path");
            if (path.startsWith("assets/") || path.startsWith("data/")) {
                assertEquals("PRESERVE_BYTES", entry.get("disposition")); assertEquals(entry.get("sourceSha256"), entry.get("targetSha256"));
            }
        }
        for (var raw : (List<?>) ((Map<?,?>)report.get("transformationLog")).get("entries")) {
            var change = (Map<?,?>)raw; assertEquals("VERIFIED", change.get("confidence")); assertNotNull(change.get("adapter"));
        }
        assertEquals(candidate, LogicPreservingConverter.planOwnedReceiverHandles(fixture(), output, inputs()));
        System.out.println("OWNED_RECEIVER_CANDIDATE " + new CanonicalReportWriter().toJsonTree(report));
    }

    @ParameterizedTest @ValueSource(strings = {"OPEN_HELPER", "PRIVATE_TARGET", "STATIC_TARGET", "MISSING_TARGET",
            "SPECIAL_HANDLE", "OWNER_KIND", "CAPTURE_TYPE", "NULL_RECEIVER", "OTHER_RECEIVER", "ALT_META",
            "BOXING", "UNKNOWN_EXTERNAL", "EARLY_REGISTRY", "SECOND_SITE", "STACK_LIMIT", "HELPER_INTERFACE"})
    void unsafeReceiverCandidateIsWhollyBlocked(String defect) throws Exception {
        var entries=read();var helper=node(entries,HELPER);var item=node(entries,ITEM);var factory=method(helper,"operation");
        InvokeDynamicInsnNode dynamic=null;
        for(var i:factory.instructions)if(i instanceof InvokeDynamicInsnNode d)dynamic=d;
        assertNotNull(dynamic);
        var handle=(Handle)dynamic.bsmArgs[1];
        switch(defect) {
            case "OPEN_HELPER" -> helper.access&=~ACC_FINAL;
            case "PRIVATE_TARGET" -> method(helper,"score").access=ACC_PRIVATE;
            case "STATIC_TARGET" -> method(helper,"score").access|=ACC_STATIC;
            case "MISSING_TARGET" -> helper.methods.remove(method(helper,"score"));
            case "SPECIAL_HANDLE" -> dynamic.bsmArgs[1]=new Handle(H_INVOKESPECIAL,handle.getOwner(),handle.getName(),handle.getDesc(),false);
            case "OWNER_KIND" -> dynamic.bsmArgs[1]=new Handle(H_INVOKEVIRTUAL,handle.getOwner(),handle.getName(),handle.getDesc(),true);
            case "CAPTURE_TYPE" -> dynamic.desc="(Ljava/lang/Object;)Ljava/util/function/IntUnaryOperator;";
            case "NULL_RECEIVER" -> {for(var i:factory.instructions)if(i instanceof VarInsnNode v&&v.getOpcode()==ALOAD){factory.instructions.set(v,new InsnNode(ACONST_NULL));break;}}
            case "OTHER_RECEIVER" -> {
                for(var i:factory.instructions)if(i instanceof VarInsnNode v&&v.getOpcode()==ALOAD){
                    var n=new TypeInsnNode(NEW,HELPER);factory.instructions.set(v,n);
                    factory.instructions.insert(n,new InsnNode(DUP));
                    factory.instructions.insert(n.getNext(),new MethodInsnNode(INVOKESPECIAL,HELPER,"<init>","()V",false));break;
                } factory.maxStack=2;
            }
            case "ALT_META" -> dynamic.bsm=new Handle(H_INVOKESTATIC,dynamic.bsm.getOwner(),"altMetafactory",dynamic.bsm.getDesc(),false);
            case "BOXING" -> dynamic.bsmArgs[2]=Type.getMethodType("(Ljava/lang/Integer;)Ljava/lang/Integer;");
            case "UNKNOWN_EXTERNAL" -> method(helper,"score").instructions.insert(new MethodInsnNode(INVOKESTATIC,"unknown/Api","run","()V",false));
            case "EARLY_REGISTRY" -> method(helper,"score").instructions.insert(new MethodInsnNode(INVOKESTATIC,"net/minecraftforge/fml/RegistryObject","get","()Ljava/lang/Object;",false));
            case "SECOND_SITE" -> factory.instructions.insertBefore(dynamic,dynamic.clone(new HashMap<>()));
            case "STACK_LIMIT" -> {for(var i:method(item,"<init>").instructions)if(i instanceof IntInsnNode c)c.operand=65;}
            case "HELPER_INTERFACE" -> helper.interfaces.add("java/io/Serializable");
            default -> throw new AssertionError(defect);
        }
        entries.put(HELPER+".class",bytes(helper));entries.put(ITEM+".class",bytes(item));
        var input=directory.resolve(defect+".jar");write(input,entries);var output=directory.resolve("absent.jar");
        byte[] before=Files.readAllBytes(input);var candidate=LogicPreservingConverter.planOwnedReceiverHandles(input,output,inputs());
        assertFalse(candidate.validated(),defect+candidate.report());assertTrue(candidate.candidateSha256().isEmpty());
        assertFalse(Files.exists(output));assertArrayEquals(before,Files.readAllBytes(input));
    }

    @ParameterizedTest @ValueSource(strings = {"SERIALIZATION", "REOPEN", "FINAL_LINKAGE", "PREPARED"})
    void cancellationDoesNotPublish(String point) throws Exception {
        var reference=new AtomicReference<OperationControl>(); int[] archive={0},linkage={0};
        var control=new OperationControl(OperationLimits.defaults(), event -> {
            boolean cancel=point.equals(event.stage().name());
            if(event.stage()==OperationStage.ARCHIVE_SCAN && event.completed()==0
                    && event.detail().equals("Scanning private input snapshot") && ++archive[0]==2 && point.equals("REOPEN")) cancel=true;
            if(event.stage()==OperationStage.BYTECODE_VALIDATION && ++linkage[0]==3 && point.equals("FINAL_LINKAGE")) cancel=true;
            if(cancel) reference.get().requestCancel();
        }); reference.set(control);
        inputs(); var output=directory.resolve("absent.jar");
        try(var scope=control.enter()) {
            assertThrows(OperationStoppedException.class, () -> LogicPreservingConverter.planOwnedReceiverHandles(fixture(),output,inputs()));
        }
        assertFalse(Files.exists(output));
    }
    private static ClassNode node(Map<String,byte[]> entries,String name){var node=new ClassNode();new ClassReader(entries.get(name+".class")).accept(node,0);return node;}
    private static MethodNode method(ClassNode node,String name){return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static void clear(MethodNode method){method.instructions.clear();method.tryCatchBlocks.clear();if(method.localVariables!=null)method.localVariables.clear();}
    private static byte[] bytes(ClassNode node){var writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();}
    private static Map<String,byte[]> read()throws Exception{var entries=new TreeMap<String,byte[]>();try(var zip=new ZipFile(fixture().toFile())){for(var entry:Collections.list(zip.entries()))if(!entry.isDirectory())try(var input=zip.getInputStream(entry)){entries.put(entry.getName(),input.readAllBytes());}}return entries;}
    private static void write(Path path,Map<String,byte[]> entries)throws Exception{try(var zip=new ZipOutputStream(Files.newOutputStream(path))){for(var entry:entries.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}}
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
