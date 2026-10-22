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

@EnabledIfSystemProperty(named = "legacybridge.verifyCompositionEnvironment", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OwnedConstructorsIntegrationTest {
    @TempDir Path directory;
    private static MappingInputs mappings;
    private static final String ITEM = "dev/legacybridge/fixture/constructors/DelegatingItem.class";
    private static Path profile() { return Path.of(System.getProperty("legacybridge.logicMappingProfile")); }
    private static MappingInputs inputs() throws Exception { if (mappings == null) mappings = new MappingInputsLoader().load(profile()); return mappings; }
    @AfterAll static void release() { mappings = null; }
    private static Path fixture() { return Path.of(System.getProperty("legacybridge.fixtureRoot"), "owned-constructors-1.16.5/build/libs/legacybridge-owned-constructors-1.16.5-1.0.0.jar"); }

    @Test void realReobfuscatedFixtureRetainsBodiesAndResourcesThroughPrivateSerialization() throws Exception {
        byte[] original = Files.readAllBytes(fixture()); Path output = directory.resolve("not-published.jar");
        var candidate = LogicPreservingConverter.planOwnedConstructors(fixture(), output, inputs());
        assertTrue(candidate.validated(), candidate.report().toString()); assertFalse(candidate.publicationAllowed());
        assertTrue(candidate.candidateSha256().isPresent()); assertFalse(Files.exists(output));
        assertArrayEquals(original, Files.readAllBytes(fixture()));
        var report = candidate.report();
        assertEquals("LEGACY_OWNED_CONSTRUCTOR_CANDIDATE", report.get("reportType"));
        assertEquals("owned-constructors-candidate-v1", report.get("candidateIdentity"));
        assertEquals("NOT_RUN", report.get("runtimeValidation")); assertEquals(3, report.get("preservedClassCount"));
        assertEquals(11, report.get("preservedMethodCount")); assertEquals(0, report.get("replacedClassCount"));
        assertEquals("PASSED_STATIC_REOPENED", ((Map<?, ?>) report.get("bytecodeValidation")).get("status"));
        assertEquals("PASSED_SCOPED", ((Map<?, ?>) report.get("serializedOfflineLinkage")).get("status"));
        assertEquals(true, ((Map<?, ?>) report.get("candidateArchiveValidation")).get("scratchDeleted"));
        var chain = (List<?>) ((Map<?, ?>) report.get("ownedConstructorProof")).get("chain");
        assertEquals(report.get("sourceOwnedConstructorProof"), report.get("ownedConstructorProof"));
        assertEquals(List.of(16, 3), ((Map<?, ?>) chain.get(2)).get("arguments"));
        for (var raw : (List<?>) ((Map<?, ?>) report.get("artifactPlan")).get("entries")) {
            var entry = (Map<?, ?>) raw; var path = (String) entry.get("path");
            if (path.startsWith("assets/") || path.startsWith("data/")) {
                assertEquals("PRESERVE_BYTES", entry.get("disposition")); assertEquals(entry.get("sourceSha256"), entry.get("targetSha256"));
            }
        }
        assertEquals(candidate, LogicPreservingConverter.planOwnedConstructors(fixture(), output, inputs()));
        assertFalse(LogicPreservingConverter.planLogicWithRecipes(fixture(), output, inputs()).validated());
        System.out.println("OWNED_CONSTRUCTOR_CANDIDATE " + new CanonicalReportWriter().toJsonTree(report));
    }

    @Test @Order(1) void boundedServiceEqualsDirectProofWithoutChangingSixPublicProfiles() throws Exception {
        Path output = directory.resolve("absent.jar");
        // Exercise the real bounded worker before retaining a second full developer inventory
        // in the opt-in bounded test JVM. Do not disable its headroom guard or request automatic GC.
        CompositionCandidate serviceProof;
        try (var service = new LegacyEngineService()) {
            var request = new ConversionRequest(fixture(), output, MappingData.profile(profile()), Optional.of(hash(Files.readAllBytes(fixture()))), ConversionProfile.LOGIC_PRESERVING_V1);
            var result = service.planOwnedConstructors(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, result.status(), result.toString()); serviceProof = result.value().orElseThrow();
            assertThrows(IllegalArgumentException.class, () -> service.planOwnedConstructors(new ConversionRequest(fixture(), output, MappingData.profile(profile()))));
        }
        assertEquals(serviceProof, LogicPreservingConverter.planOwnedConstructors(fixture(), output, inputs()));
        for (var existing : ConversionProfile.values()) {
            var result = switch (existing) {
                case SIMPLE_CONTENT_V1, DATAPACK_RECIPES_V1 -> new SimpleContentConverter(existing).convert(fixture(), output, inputs());
                default -> new LogicPreservingConverter(existing).convert(fixture(), output, inputs());
            };
            assertFalse(result.converted(), existing.toString());
        }
        assertFalse(Files.exists(output)); assertEquals(6, ConversionProfile.values().length);
    }

    @ParameterizedTest @ValueSource(strings = {"CYCLE", "UNKNOWN_EXTERNAL", "WRONG_OPCODE", "PUBLIC_OVERLOAD", "STACK_LIMIT", "PRE_SUPER", "UNREVIEWED_PROPERTY", "MISSING_DEPENDENCY"})
    void rejectsTheWholeCandidateWithoutOutput(String defect) throws Exception {
        var entries = read(); var node = new ClassNode(); new ClassReader(entries.get(ITEM)).accept(node, 0);
        var root = node.methods.stream().filter(m -> m.name.equals("<init>") && m.desc.equals("()V")).findFirst().orElseThrow();
        var terminal = node.methods.stream().filter(m -> m.name.equals("<init>") && m.desc.equals("(II)V")).findFirst().orElseThrow();
        switch (defect) {
            case "CYCLE" -> { terminal.instructions.clear(); terminal.tryCatchBlocks.clear(); terminal.localVariables.clear(); terminal.instructions.add(new VarInsnNode(ALOAD, 0)); terminal.instructions.add(new MethodInsnNode(INVOKESPECIAL, node.name, "<init>", "()V", false)); terminal.instructions.add(new InsnNode(RETURN)); }
            case "UNKNOWN_EXTERNAL" -> terminal.instructions.insertBefore(terminal.instructions.getLast(), new MethodInsnNode(INVOKESTATIC, "unknown/Library", "run", "()V", false));
            case "WRONG_OPCODE" -> { for (var insn : root.instructions) if (insn instanceof MethodInsnNode call && call.name.equals("<init>")) call.setOpcode(INVOKEVIRTUAL); }
            case "PUBLIC_OVERLOAD" -> terminal.access = ACC_PUBLIC;
            case "STACK_LIMIT" -> { for (var insn : root.instructions) if (insn instanceof IntInsnNode push) push.operand = 65; }
            case "PRE_SUPER" -> { var insn = new InsnList(); insn.add(new VarInsnNode(ALOAD, 0)); insn.add(new InsnNode(ICONST_0)); insn.add(new FieldInsnNode(PUTFIELD, node.name, "trace", "I")); terminal.instructions.insert(insn); }
            case "UNREVIEWED_PROPERTY" -> { for (var insn : terminal.instructions) if (insn instanceof MethodInsnNode call && call.getOpcode() == INVOKEVIRTUAL) call.name = "unknownBuilder"; }
            case "MISSING_DEPENDENCY" -> entries.remove("dev/legacybridge/fixture/constructors/CountRule.class");
        }
        var writer = new ClassWriter(0); node.accept(writer); entries.put(ITEM, writer.toByteArray());
        Path input = directory.resolve(defect + ".jar"), output = directory.resolve("absent.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(input))) { for (var entry : entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); } }
        var candidate = LogicPreservingConverter.planOwnedConstructors(input, output, inputs());
        assertFalse(candidate.validated(), defect); assertTrue(candidate.candidateSha256().isEmpty()); assertFalse(Files.exists(output));
    }

    @ParameterizedTest @ValueSource(strings = {"SERIALIZATION", "REOPEN", "PREPARED"})
    void cancellationCannotPublish(String barrier) throws Exception {
        var baseline = inputs(); var ref = new AtomicReference<OperationControl>(); int[] scans = {0};
        var control = new OperationControl(OperationLimits.defaults(), progress -> {
            boolean stop = switch (barrier) {
                case "SERIALIZATION" -> progress.stage() == OperationStage.SERIALIZATION;
                case "REOPEN" -> progress.stage() == OperationStage.ARCHIVE_SCAN && progress.completed() == 0
                        && progress.detail().equals("Scanning private input snapshot") && ++scans[0] == 2;
                default -> progress.stage() == OperationStage.PREPARED;
            };
            if (stop) ref.get().requestCancel();
        }); ref.set(control);
        try (var scope = control.enter()) { assertThrows(OperationStoppedException.class, () -> LogicPreservingConverter.planOwnedConstructors(fixture(), directory.resolve("absent.jar"), baseline)); }
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    private static Map<String,byte[]> read() throws Exception {
        var entries = new TreeMap<String,byte[]>();
        try (var zip = new ZipFile(fixture().toFile())) { for (var entry : zip.stream().filter(e -> !e.isDirectory()).toList()) try (var in = zip.getInputStream(entry)) { entries.put(entry.getName(), in.readAllBytes()); } }
        return entries;
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
