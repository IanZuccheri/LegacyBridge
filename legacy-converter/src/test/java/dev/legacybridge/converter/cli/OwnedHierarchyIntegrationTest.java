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

/** Genuine reobfuscated Forge input, treated strictly as inert bytes. No original or game execution. */
@EnabledIfSystemProperty(named = "legacybridge.verifyCompositionEnvironment", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OwnedHierarchyIntegrationTest {
    private static final String BASE = "dev/legacybridge/fixture/hierarchy/base/BaseTokenItem";
    private static final String LEAF = "dev/legacybridge/fixture/hierarchy/leaf/HierarchyItem";
    private static final String ITEM = "net/minecraft/item/Item";
    @TempDir Path directory;
    private static MappingInputs mappings;
    private static Path profile() { return Path.of(System.getProperty("legacybridge.logicMappingProfile")); }
    private static MappingInputs inputs() throws Exception { if (mappings == null) mappings = new MappingInputsLoader().load(profile()); return mappings; }
    @AfterAll static void release() { mappings = null; }
    private static Path fixture() { return Path.of(System.getProperty("legacybridge.fixtureRoot"), "owned-hierarchy-1.16.5/build/libs/legacybridge-owned-hierarchy-1.16.5-1.0.0.jar"); }

    @Test @Order(1) void sharedWorkerAgreesAndExistingAdmissionRoutesRemainClosed() throws Exception {
        Path output = directory.resolve("absent.jar"); CompositionCandidate serviceProof;
        try (var service = new LegacyEngineService()) {
            var request = new ConversionRequest(fixture(), output, MappingData.profile(profile()), Optional.of(hash(Files.readAllBytes(fixture()))), ConversionProfile.LOGIC_PRESERVING_V1);
            var result = service.planOwnedHierarchy(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, result.status(), result.toString()); serviceProof = result.value().orElseThrow();
            assertTrue(serviceProof.validated(), serviceProof.report().toString());
            assertThrows(IllegalArgumentException.class, () -> service.planOwnedHierarchy(new ConversionRequest(fixture(), output, MappingData.profile(profile()))));
        }
        assertEquals(serviceProof, LogicPreservingConverter.planOwnedHierarchy(fixture(), output, inputs()));
        for (var existing : ConversionProfile.values()) {
            var ordinary = switch (existing) {
                case SIMPLE_CONTENT_V1, DATAPACK_RECIPES_V1 -> new SimpleContentConverter(existing).convert(fixture(), output, inputs());
                default -> new LogicPreservingConverter(existing).convert(fixture(), output, inputs());
            };
            assertFalse(ordinary.converted(), existing.toString());
        }
        assertFalse(LogicPreservingConverter.planLogicWithRecipes(fixture(), output, inputs()).validated());
        assertFalse(LogicPreservingConverter.planOwnedConstructors(fixture(), output, inputs()).validated());
        assertFalse(Files.exists(output)); assertEquals(6, ConversionProfile.values().length);
    }

    @Test void actualHierarchyAndDispatchCommuteWithMappingsAndSurviveReopening() throws Exception {
        byte[] original = Files.readAllBytes(fixture()); var output = directory.resolve("not-published.jar");
        var candidate = LogicPreservingConverter.planOwnedHierarchy(fixture(), output, inputs());
        assertTrue(candidate.validated(), candidate.report().toString()); assertFalse(candidate.publicationAllowed());
        assertTrue(candidate.candidateSha256().isPresent()); assertFalse(Files.exists(output)); assertArrayEquals(original, Files.readAllBytes(fixture()));
        var report = candidate.report(); assertEquals("LEGACY_OWNED_HIERARCHY_CANDIDATE", report.get("reportType"));
        assertEquals("owned-hierarchy-candidate-v1", report.get("candidateIdentity"));
        assertEquals("NOT_RUN", report.get("runtimeValidation")); assertEquals(4, report.get("preservedClassCount"));
        assertEquals(13, report.get("preservedMethodCount")); assertEquals(0, report.get("replacedClassCount"));
        assertEquals("PASSED_EXACT_RULES", report.get("hierarchyMappingCommutation"));
        assertEquals(report.get("ownedHierarchy"), report.get("reopenedOwnedHierarchy"));
        var proof = (Map<?,?>) report.get("ownedHierarchy");
        assertEquals(BASE, proof.get("base")); assertEquals(LEAF, proof.get("leaf"));
        assertEquals("NON_NULL_REGISTERED_LEAF", proof.get("receiverAssumption"));
        var sites = ((List<?>) proof.get("sites")).stream().map(Map.class::cast).toList();
        assertTrue(sites.stream().anyMatch(s -> s.get("opcode").equals(INVOKEVIRTUAL)
                && ((Map<?,?>)s.get("resolved")).get("owner").equals(BASE)
                && ((Map<?,?>)s.get("selectedForRegisteredLeaf")).get("owner").equals(LEAF)));
        assertTrue(sites.stream().anyMatch(s -> s.get("opcode").equals(INVOKESPECIAL)
                && ((Map<?,?>)s.get("selectedForRegisteredLeaf")).get("owner").equals(BASE)));
        assertTrue(sites.stream().anyMatch(s -> ((Map<?,?>)s.get("symbolic")).get("owner").equals(LEAF)
                && ((Map<?,?>)s.get("resolved")).get("name").equals("projected")
                && ((Map<?,?>)s.get("resolved")).get("owner").equals(BASE)));
        assertEquals("PASSED_SCOPED", ((Map<?,?>)report.get("serializedOfflineLinkage")).get("status"));
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
        assertEquals(candidate, LogicPreservingConverter.planOwnedHierarchy(fixture(), output, inputs()));
        System.out.println("OWNED_HIERARCHY_CANDIDATE " + new CanonicalReportWriter().toJsonTree(report));
    }

    @ParameterizedTest @ValueSource(strings = {"MISSING_BASE", "CYCLE", "FINAL_BASE", "ABSTRACT_LEAF", "MISSING_OVERRIDE", "WEAKER_ACCESS", "STATIC_OVERRIDE", "FINAL_OVERRIDE", "PRIVATE_CONSTRUCTOR", "WRONG_OPCODE", "INDIRECT_SUPER", "BAD_PROTECTED_RECEIVER", "UNKNOWN_EXTERNAL", "SECOND_LEAF", "WRONG_CONSTRUCTOR", "STACK_LIMIT", "ARGUMENT_CONSTRUCTOR", "NATIVE_OVERRIDE_ACCESS"})
    void unsafeHierarchyAbortsWholeCandidate(String defect) throws Exception {
        var entries = read(); var base = node(entries, BASE); var leaf = node(entries, LEAF);
        var score = method(leaf, "score"); var ctor = method(leaf, "<init>");
        switch (defect) {
            case "MISSING_BASE" -> entries.remove(BASE + ".class");
            case "CYCLE" -> base.superName = LEAF;
            case "FINAL_BASE" -> base.access |= ACC_FINAL;
            case "ABSTRACT_LEAF" -> { leaf.access &= ~ACC_FINAL; leaf.access |= ACC_ABSTRACT; }
            case "MISSING_OVERRIDE" -> leaf.methods.remove(score);
            case "WEAKER_ACCESS" -> score.access = ACC_PRIVATE;
            case "STATIC_OVERRIDE" -> { score.access = ACC_PUBLIC | ACC_STATIC; clear(score); score.instructions.add(new InsnNode(ICONST_0)); score.instructions.add(new InsnNode(IRETURN)); }
            case "FINAL_OVERRIDE" -> { method(base,"projected").access |= ACC_FINAL; var m=new MethodNode(ACC_PUBLIC,"projected","(I)I",null,null); m.instructions.add(new VarInsnNode(ILOAD,1)); m.instructions.add(new InsnNode(IRETURN)); m.maxStack=1; m.maxLocals=2; leaf.methods.add(m); }
            case "PRIVATE_CONSTRUCTOR" -> method(base,"<init>").access = ACC_PRIVATE;
            case "WRONG_OPCODE" -> { for (var i : leaf.methods) for (var insn : i.instructions) if(insn instanceof MethodInsnNode c && c.owner.equals(BASE) && !c.name.equals("<init>")) c.setOpcode(INVOKEVIRTUAL); }
            case "INDIRECT_SUPER" -> { for(var i : leaf.methods) for(var insn : i.instructions) if(insn instanceof MethodInsnNode c && c.owner.equals(BASE) && !c.name.equals("<init>")) c.owner=ITEM; }
            case "BAD_PROTECTED_RECEIVER" -> { var m=new MethodNode(ACC_PUBLIC|ACC_STATIC,"unsafe","(L"+BASE+";)I",null,null); m.instructions.add(new VarInsnNode(ALOAD,0)); m.instructions.add(new FieldInsnNode(GETFIELD,BASE,"seed","I")); m.instructions.add(new InsnNode(IRETURN)); m.maxStack=1; m.maxLocals=1; leaf.methods.add(m); }
            case "UNKNOWN_EXTERNAL" -> ctor.instructions.insertBefore(ctor.instructions.getLast(),new MethodInsnNode(INVOKESTATIC,"unknown/Api","run","()V",false));
            case "SECOND_LEAF" -> { var second=node(entries,LEAF); second.name=LEAF+"Other"; entries.put(second.name+".class",bytes(second)); }
            case "WRONG_CONSTRUCTOR" -> { for(var insn : ctor.instructions) if(insn instanceof MethodInsnNode c && c.name.equals("<init>")) c.owner=ITEM; }
            case "STACK_LIMIT" -> { for(var insn : method(base,"<init>").instructions) if(insn instanceof IntInsnNode c) c.operand=65; }
            case "ARGUMENT_CONSTRUCTOR" -> method(base,"<init>").desc="(I)V";
            case "NATIVE_OVERRIDE_ACCESS" -> { for(var m:leaf.methods) if(m.desc.equals("(Lnet/minecraft/item/ItemStack;)Z")) m.access=ACC_PROTECTED; }
        }
        if(!defect.equals("MISSING_BASE")) entries.put(BASE+".class",bytes(base)); entries.put(LEAF+".class",bytes(leaf));
        var input=directory.resolve(defect+".jar"); var output=directory.resolve("absent.jar");
        try(var zip=new ZipOutputStream(Files.newOutputStream(input))){for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}}
        var candidate=LogicPreservingConverter.planOwnedHierarchy(input,output,inputs());
        assertFalse(candidate.validated(),defect); assertTrue(candidate.candidateSha256().isEmpty()); assertFalse(Files.exists(output));
    }

    @ParameterizedTest @ValueSource(strings = {"SERIALIZATION", "REOPEN", "FINAL_LINKAGE", "PREPARED"})
    void cancellationCannotPublish(String barrier) throws Exception {
        var baseline=inputs(); var ref=new AtomicReference<OperationControl>(); int[] scans={0}, checks={0};
        var control=new OperationControl(OperationLimits.defaults(),progress->{
            boolean stop=switch(barrier){case "SERIALIZATION"->progress.stage()==OperationStage.SERIALIZATION;
                case "REOPEN"->progress.stage()==OperationStage.ARCHIVE_SCAN && progress.completed()==0
                        && progress.detail().equals("Scanning private input snapshot") && ++scans[0]==2;
                case "FINAL_LINKAGE"->progress.stage()==OperationStage.BYTECODE_VALIDATION && ++checks[0]==3;
                default->progress.stage()==OperationStage.PREPARED;};
            if(stop)ref.get().requestCancel();
        }); ref.set(control);
        try(var scope=control.enter()){assertThrows(OperationStoppedException.class,()->LogicPreservingConverter.planOwnedHierarchy(fixture(),directory.resolve("absent.jar"),baseline));}
        try(var files=Files.list(directory)){assertEquals(0,files.count());}
    }
    private static MethodNode method(ClassNode node,String name){return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static void clear(MethodNode method){method.instructions.clear();method.tryCatchBlocks.clear();method.localVariables.clear();}
    private static ClassNode node(Map<String,byte[]> entries,String name){var node=new ClassNode();new ClassReader(entries.get(name+".class")).accept(node,0);return node;}
    private static byte[] bytes(ClassNode node){var writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();}
    private static Map<String,byte[]> read() throws Exception {
        var entries=new TreeMap<String,byte[]>();
        try(var zip=new ZipFile(fixture().toFile())){for(var e:zip.stream().filter(v->!v.isDirectory()).toList())try(var in=zip.getInputStream(e)){entries.put(e.getName(),in.readAllBytes());}}
        return entries;
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
