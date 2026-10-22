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
class OwnedInterfaceIntegrationTest {
    private static final String CONTRACT = "dev/legacybridge/fixture/defaults/api/Scoring";
    private static final String HELPER = "dev/legacybridge/fixture/defaults/impl/Counter";
    private static final String ITEM = "dev/legacybridge/fixture/defaults/DefaultItem";
    @TempDir Path directory;
    private static MappingInputs mappings;
    private static Path profile() { return Path.of(System.getProperty("legacybridge.logicMappingProfile")); }
    private static MappingInputs inputs() throws Exception { if (mappings == null) mappings = new MappingInputsLoader().load(profile()); return mappings; }
    @AfterAll static void release() { mappings = null; }
    private static Path fixture() { return Path.of(System.getProperty("legacybridge.fixtureRoot"), "owned-interface-1.16.5/build/libs/legacybridge-owned-interface-1.16.5-1.0.0.jar"); }

    @Test @Order(1) void boundedServiceAgreesAndAllExistingRoutesRemainClosed() throws Exception {
        var output = directory.resolve("never-published.jar"); CompositionCandidate serviceProof;
        try (var service = new LegacyEngineService()) {
            var request = new ConversionRequest(fixture(), output, MappingData.profile(profile()), Optional.of(hash(Files.readAllBytes(fixture()))), ConversionProfile.LOGIC_PRESERVING_V1);
            var result = service.planOwnedInterfaceDefaults(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, result.status(), result.toString()); serviceProof = result.value().orElseThrow();
            assertTrue(serviceProof.validated(), serviceProof.report().toString());
            assertThrows(IllegalArgumentException.class, () -> service.planOwnedInterfaceDefaults(new ConversionRequest(fixture(), output, MappingData.profile(profile()))));
        }
        assertEquals(serviceProof, LogicPreservingConverter.planOwnedInterfaceDefaults(fixture(), output, inputs()));
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
        assertFalse(Files.exists(output)); assertEquals(6, ConversionProfile.values().length);
    }

    @Test void realDefaultOverrideSuperHookAndCapturedLambdaSurviveReadback() throws Exception {
        byte[] original = Files.readAllBytes(fixture()); var output = directory.resolve("absent.jar");
        var candidate = LogicPreservingConverter.planOwnedInterfaceDefaults(fixture(), output, inputs());
        assertTrue(candidate.validated(), candidate.report().toString()); assertFalse(candidate.publicationAllowed());
        assertTrue(candidate.candidateSha256().isPresent()); assertFalse(Files.exists(output)); assertArrayEquals(original, Files.readAllBytes(fixture()));
        var report = candidate.report(); assertEquals("LEGACY_OWNED_INTERFACE_CANDIDATE", report.get("reportType"));
        assertEquals("owned-interface-candidate-v1", report.get("candidateIdentity"));
        assertEquals("NOT_RUN", report.get("runtimeValidation")); assertEquals(4, report.get("preservedClassCount"));
        assertEquals(12, report.get("preservedMethodCount")); assertEquals(0, report.get("replacedClassCount"));
        assertEquals("PASSED_EXACT_RULES", report.get("interfaceMappingCommutation"));
        assertEquals(report.get("ownedInterface"), report.get("reopenedOwnedInterface"));
        var proof = (Map<?,?>) report.get("ownedInterface");
        assertEquals(CONTRACT, proof.get("interface")); assertEquals(HELPER, proof.get("helper"));
        assertEquals("NON_NULL_OWNED_HELPER", proof.get("receiverAssumption"));
        var sites = ((List<?>) proof.get("sites")).stream().map(Map.class::cast).toList();
        assertTrue(sites.stream().anyMatch(s -> s.get("opcode").equals(INVOKESPECIAL)
                && ((Map<?,?>)s.get("selectedForHelper")).get("owner").equals(CONTRACT)));
        for (String name : List.of("score", "bias")) assertTrue(sites.stream().anyMatch(s -> s.get("opcode").equals(INVOKEINTERFACE)
                && ((Map<?,?>)s.get("selectedForHelper")).get("owner").equals(HELPER)
                && ((Map<?,?>)s.get("symbolic")).get("name").equals(name)));
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
        assertEquals(candidate, LogicPreservingConverter.planOwnedInterfaceDefaults(fixture(), output, inputs()));
        System.out.println("OWNED_INTERFACE_CANDIDATE " + new CanonicalReportWriter().toJsonTree(report));
    }

    @ParameterizedTest @ValueSource(strings = {"MISSING_INTERFACE", "CYCLE", "EXTRA_INTERFACE", "DEFAULT_ABSTRACT", "DEFAULT_STATIC", "MISSING_OVERRIDE",
            "PRIVATE_OVERRIDE", "STATIC_OVERRIDE", "OPEN_HELPER", "SECOND_IMPLEMENTATION", "WRONG_OPCODE", "OWNER_KIND", "UNKNOWN_EXTERNAL", "INTERFACE_FIELD",
            "INTERFACE_HANDLE", "CAPTURE_MISMATCH", "FOREIGN_SUPER_RECEIVER", "STACK_LIMIT"})
    void unsafeInterfaceCandidateIsWhollyBlocked(String defect) throws Exception {
        var entries = read(); var contract = node(entries, CONTRACT); var helper = node(entries, HELPER); var item = node(entries, ITEM);
        var score = method(helper, "score");
        switch (defect) {
            case "MISSING_INTERFACE" -> entries.remove(CONTRACT + ".class");
            case "CYCLE" -> contract.interfaces.add(CONTRACT);
            case "EXTRA_INTERFACE" -> helper.interfaces.add("java/io/Serializable");
            case "DEFAULT_ABSTRACT" -> { var m=method(contract,"score"); m.access|=ACC_ABSTRACT; clear(m); }
            case "DEFAULT_STATIC" -> method(contract,"score").access|=ACC_STATIC;
            case "MISSING_OVERRIDE" -> helper.methods.remove(score);
            case "PRIVATE_OVERRIDE" -> score.access=ACC_PRIVATE;
            case "STATIC_OVERRIDE" -> score.access|=ACC_STATIC;
            case "OPEN_HELPER" -> helper.access&=~ACC_FINAL;
            case "SECOND_IMPLEMENTATION" -> { var second=node(entries,HELPER); second.name=HELPER+"Other"; entries.put(second.name+".class",bytes(second)); }
            case "WRONG_OPCODE" -> { for(var i:score.instructions) if(i instanceof MethodInsnNode c && c.owner.equals(CONTRACT)) c.setOpcode(INVOKEVIRTUAL); }
            case "OWNER_KIND" -> { for(var i:score.instructions) if(i instanceof MethodInsnNode c && c.owner.equals(CONTRACT)) c.itf=false; }
            case "UNKNOWN_EXTERNAL" -> method(item,"<init>").instructions.insertBefore(method(item,"<init>").instructions.getLast(),new MethodInsnNode(INVOKESTATIC,"unknown/Api","run","()V",false));
            case "INTERFACE_FIELD" -> contract.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"UNREVIEWED","I",null,1));
            case "INTERFACE_HANDLE" -> { for(var m:item.methods) for(var i:m.instructions) if(i instanceof InvokeDynamicInsnNode d && d.name.equals("applyAsInt")) d.bsmArgs[1]=new Handle(H_INVOKEINTERFACE,CONTRACT,"score","(I)I",true); }
            case "CAPTURE_MISMATCH" -> { for(var m:item.methods) for(var i:m.instructions) if(i instanceof InvokeDynamicInsnNode d && d.name.equals("applyAsInt")) d.desc="(L"+HELPER+";)Ljava/util/function/IntUnaryOperator;"; }
            case "FOREIGN_SUPER_RECEIVER" -> { var m=new MethodNode(ACC_PUBLIC,"foreign","(L"+HELPER+";I)I",null,null); m.instructions.add(new VarInsnNode(ALOAD,1)); m.instructions.add(new VarInsnNode(ILOAD,2)); m.instructions.add(new MethodInsnNode(INVOKESPECIAL,CONTRACT,"score","(I)I",true)); m.instructions.add(new InsnNode(IRETURN)); m.maxLocals=3; m.maxStack=2; helper.methods.add(m); }
            case "STACK_LIMIT" -> { for(var i:method(item,"<init>").instructions) if(i instanceof IntInsnNode c) c.operand=65; }
            default -> throw new AssertionError(defect);
        }
        if (!defect.equals("MISSING_INTERFACE")) entries.put(CONTRACT+".class",bytes(contract));
        entries.put(HELPER+".class",bytes(helper)); entries.put(ITEM+".class",bytes(item));
        var input=directory.resolve(defect+".jar"); write(input,entries); var output=directory.resolve("absent.jar");
        var candidate=LogicPreservingConverter.planOwnedInterfaceDefaults(input,output,inputs());
        assertFalse(candidate.validated(), defect+candidate.report()); assertTrue(candidate.candidateSha256().isEmpty()); assertFalse(Files.exists(output));
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
            assertThrows(OperationStoppedException.class, () -> LogicPreservingConverter.planOwnedInterfaceDefaults(fixture(),output,inputs()));
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
