package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.*;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
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

/** Real reobfuscated Forge 36 JAR + explicitly selected real mappings; no original execution. */
@EnabledIfSystemProperty(named = "legacybridge.verifyCompositionEnvironment", matches = "true")
class CompositionPreviewIntegrationTest {
    @TempDir Path directory;
    private static MappingInputs mappings;
    private static MappingInputs inputs() throws Exception {
        if (mappings == null) mappings = new MappingInputsLoader().load(profile());
        return mappings;
    }
    @AfterAll static void releaseInputs() { mappings = null; }
    private static Path profile() { return Path.of(System.getProperty("legacybridge.logicMappingProfile")); }
    private static Path fixture() { return Path.of(System.getProperty("legacybridge.fixtureRoot"),
            "composed-logic-recipes-1.16.5/build/libs/legacybridge-composed-logic-recipes-1.16.5-1.0.0.jar"); }
    private static final String RECIPE = "data/legacybridge_composed/recipes/count_token.json";
    private static final String ITEM = "dev/legacybridge/fixture/composed/ComposedItem.class";

    @Test void privateCandidateSerializesAndReopensEveryEntryWithDistinctMetadata() throws Exception {
        byte[] original = Files.readAllBytes(fixture()); Path output = directory.resolve("not-published.jar");
        var candidate = LogicPreservingConverter.planLogicWithRecipes(fixture(), output, inputs());
        assertTrue(candidate.validated(), candidate.report().toString()); assertFalse(candidate.publicationAllowed());
        assertTrue(candidate.candidateSha256().isPresent()); assertFalse(Files.exists(output));
        assertArrayEquals(original, Files.readAllBytes(fixture()));
        var report = candidate.report(); assertEquals("LEGACY_COMPOSITION_CANDIDATE", report.get("reportType"));
        assertEquals("CANDIDATE_VALIDATED", report.get("status")); assertEquals("composed-logic-recipes-v1", report.get("candidateIdentity"));
        assertEquals(false, report.get("publicationAllowed")); assertEquals(false, report.get("transformationsCommitted"));
        assertEquals("NOT_RUN", report.get("runtimeValidation")); assertNull(report.get("output"));
        assertEquals(4, report.get("preservedClassCount")); assertEquals(10, report.get("preservedMethodCount"));
        assertEquals("PASSED_STATIC_REOPENED", ((Map<?, ?>) report.get("bytecodeValidation")).get("status"));
        assertEquals("PASSED_REOPENED", ((Map<?, ?>) report.get("candidateArchiveValidation")).get("status"));
        assertEquals(true, ((Map<?, ?>) report.get("candidateArchiveValidation")).get("scratchDeleted"));
        assertEquals("PASSED_SCOPED", ((Map<?, ?>) report.get("serializedOfflineLinkage")).get("status"));
        var source = read(fixture()); var plan = (Map<?, ?>) report.get("artifactPlan");
        assertEquals(source.size() + 1, plan.get("entryCount"));
        var entries = (List<?>) plan.get("entries");
        for (var raw : entries) {
            var entry = (Map<?, ?>) raw; String path = (String) entry.get("path");
            if (source.containsKey(path)) assertEquals(hash(source.get(path)), entry.get("sourceSha256"));
            if (path.startsWith("assets/") || path.startsWith("data/")) {
                assertEquals("PRESERVE_BYTES", entry.get("disposition"));
                assertEquals(hash(source.get(path)), entry.get("targetSha256"));
            }
        }
        var changes = (List<?>) ((Map<?, ?>) report.get("transformationLog")).get("entries");
        assertEquals(4, changes.stream().map(Map.class::cast).filter(e -> e.get("kind").equals("RESOURCE_REWRITE")).count());
        for (var raw : changes) { var change = (Map<?, ?>) raw; assertEquals("VERIFIED", change.get("confidence")); assertNotNull(change.get("adapter")); }
        assertThrows(UnsupportedOperationException.class, entries::clear);
        assertEquals(candidate, LogicPreservingConverter.planLogicWithRecipes(fixture(), output, inputs()));
        System.out.println("COMPOSE_01_PRIVATE_CANDIDATE " + new CanonicalReportWriter().toJsonTree(report));
    }

    @Test void candidateServiceAgreesAndStillCannotPublishThroughNormalConversion() throws Exception {
        Path output = directory.resolve("not-installed.jar");
        var direct = LogicPreservingConverter.planLogicWithRecipes(fixture(), output, inputs()); mappings = null;
        try (var service = new LegacyEngineService()) {
            var request = new ConversionRequest(fixture(), output, MappingData.profile(profile()), Optional.of(hash(Files.readAllBytes(fixture()))), ConversionProfile.LOGIC_PRESERVING_V1);
            var result = service.planLogicWithRecipes(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, result.status(), result.toString()); assertEquals(direct, result.value().orElseThrow());
            assertEquals(EngineOutcome.Status.BLOCKED, service.convert(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS).status());
            assertThrows(IllegalArgumentException.class, () -> service.planLogicWithRecipes(new ConversionRequest(fixture(), output, MappingData.profile(profile()))));
        }
        assertFalse(Files.exists(output)); assertEquals(6, ConversionProfile.values().length);
    }

    @ParameterizedTest @ValueSource(strings = {"PACK_FORMAT", "UNKNOWN_DEPENDENCY_FIELD"})
    void candidateCannotInheritPreviewAcceptanceForUnmigratedMetadata(String defect) throws Exception {
        var contents = read(fixture());
        if (defect.equals("PACK_FORMAT")) contents.put("pack.mcmeta", "{\"pack\":{\"description\":\"bad\",\"pack_format\":15}}".getBytes(StandardCharsets.UTF_8));
        else contents.put("META-INF/mods.toml", (new String(contents.get("META-INF/mods.toml"), StandardCharsets.UTF_8) + "\nunknownField=true\n").getBytes(StandardCharsets.UTF_8));
        var input = write(defect + ".jar", contents); var output = directory.resolve("blocked.jar");
        var candidate = LogicPreservingConverter.planLogicWithRecipes(input, output, inputs());
        assertFalse(candidate.validated()); assertTrue(candidate.candidateSha256().isEmpty()); assertFalse(Files.exists(output));
        assertEquals("UNSUPPORTED", candidate.report().get("compatibilityStatus"));
    }

    @ParameterizedTest @ValueSource(strings = {"SERIALIZATION", "REOPEN", "FINAL_LINKAGE", "PREPARED"})
    void candidateCancellationDoesNotExposeHashOrOutput(String barrier) throws Exception {
        var baseline = inputs(); var ref = new AtomicReference<OperationControl>(); int[] scans = {0}, linkage = {0};
        var control = new OperationControl(OperationLimits.defaults(), progress -> {
            boolean stop = switch (barrier) {
                case "SERIALIZATION" -> progress.stage() == OperationStage.SERIALIZATION;
                case "REOPEN" -> progress.stage() == OperationStage.ARCHIVE_SCAN && progress.completed() == 0
                        && progress.detail().equals("Scanning private input snapshot") && ++scans[0] == 2;
                case "FINAL_LINKAGE" -> progress.stage() == OperationStage.BYTECODE_VALIDATION && ++linkage[0] == 3;
                default -> progress.stage() == OperationStage.PREPARED;
            };
            if (stop) assertTrue(ref.get().requestCancel());
        }); ref.set(control);
        try (var scope = control.enter()) {
            assertThrows(OperationStoppedException.class, () -> LogicPreservingConverter.planLogicWithRecipes(fixture(), directory.resolve("cancel.jar"), baseline));
        }
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void realFixtureCombinesTwoContractsInMemoryWithoutAConvertedJar() throws Exception {
        byte[] original = Files.readAllBytes(fixture()); Path output = directory.resolve("never-published.jar");
        var preview = LogicPreservingConverter.previewLogicWithRecipes(fixture(), output, inputs());
        assertTrue(preview.staticallyAdmitted(), preview.report().toString()); assertFalse(preview.publicationAllowed());
        assertFalse(Files.exists(output)); assertArrayEquals(original, Files.readAllBytes(fixture()));
        var report = preview.report(); assertEquals("LEGACY_COMPOSITION_PREVIEW", report.get("reportType"));
        assertEquals("PREVIEW_VALIDATED", report.get("status")); assertEquals("NOT_A_CONVERSION_PROFILE", report.get("supportProfile"));
        assertEquals(4, report.get("preservedClassCount")); assertEquals(0, report.get("replacedClassCount"));
        assertEquals(false, report.get("transformationsCommitted")); assertEquals("NOT_RUN", report.get("runtimeValidation"));
        assertNull(report.get("candidate")); assertNull(report.get("output"));
        assertEquals("PASSED_STATIC_IN_MEMORY", ((Map<?, ?>) report.get("bytecodeValidation")).get("status"));
        assertEquals("PASSED_SCOPED", ((Map<?, ?>) report.get("sourceOfflineLinkage")).get("status"));
        assertEquals("PASSED_SCOPED", ((Map<?, ?>) report.get("targetOfflineLinkage")).get("status"));
        var graph = preview.operations().orElseThrow();
        assertEquals(List.of("logic.owned-bodies", "data.recipe-resources"), graph.operations().stream().map(o -> o.id()).toList());
        Map<String, byte[]> bytes = read(fixture());
        for (var op : graph.operations()) for (var claim : op.claims()) assertEquals(hash(bytes.get(claim.path())), claim.sourceSha256());
        var recipes = (List<?>) ((Map<?, ?>) report.get("resourceValidation")).get("recipes");
        assertEquals(1, recipes.size()); assertEquals("PRESERVE_BYTES", ((Map<?, ?>) recipes.get(0)).get("disposition"));
        var log = (Map<?, ?>) report.get("transformationLog");
        for (var raw : (List<?>) log.get("entries")) {
            var entry = (Map<?, ?>) raw; assertEquals("VERIFIED", entry.get("confidence")); assertNotNull(entry.get("adapter"));
            assertNotEquals("RESOURCE_REWRITE", entry.get("kind"));
        }
        assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>) report.get("operations")).clear());
        // Retain exact proof/hashes in the JUnit XML, not a synthetic gameplay receipt.
        System.out.println("COMPOSE_01_STATIC_PREVIEW " + new CanonicalReportWriter().toJsonTree(report));
    }

    @Test void boundedServiceAgreesWithDirectInspectionAndDoesNotBroadenNormalConversions() throws Exception {
        Path output = directory.resolve("output.jar");
        var direct = LogicPreservingConverter.previewLogicWithRecipes(fixture(), output, inputs());
        // The service loads its own explicit inventories. Do not retain a second full
        // developer baseline in the test's bounded 512 MiB JVM during that operation.
        mappings = null;
        try (var service = new LegacyEngineService()) {
            var request = new ConversionRequest(fixture(), output, MappingData.profile(profile()), Optional.of(hash(Files.readAllBytes(fixture()))), ConversionProfile.LOGIC_PRESERVING_V1);
            var result = service.previewLogicWithRecipes(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, result.status(), result.toString());
            assertEquals(direct.report(), result.value().orElseThrow().report());
            assertEquals(EngineOutcome.Status.BLOCKED, service.convert(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS).status());
            assertThrows(IllegalArgumentException.class, () -> service.previewLogicWithRecipes(new ConversionRequest(fixture(), output, MappingData.profile(profile()))));
        }
        assertFalse(Files.exists(output));
        // Neither bootstrap path may erase this behavior to make the resource graph load.
        assertFalse(new SimpleContentConverter().convert(fixture(), output, inputs()).converted());
        assertFalse(new SimpleContentConverter(ConversionProfile.DATAPACK_RECIPES_V1).convert(fixture(), output, inputs()).converted());
        assertEquals(6, ConversionProfile.values().length);
    }

    @ParameterizedTest @ValueSource(strings = {"UNKNOWN_CALL", "DEPENDENCY", "BAD_RECIPE", "MISSING_ID", "FALSE_CONDITION", "EARLY_REGISTRY", "PLATFORM_CLASS", "NO_RESOURCES", "NO_RECIPE"})
    void unprovenCompositionBlocksAsAWhole(String defect) throws Exception {
        Map<String, byte[]> contents = read(fixture());
        switch (defect) {
            case "NO_RESOURCES" -> contents.keySet().removeIf(p -> p.startsWith("assets/") || p.startsWith("data/"));
            case "NO_RECIPE" -> contents.remove(RECIPE);
            case "BAD_RECIPE" -> contents.put(RECIPE, "{broken".getBytes(StandardCharsets.UTF_8));
            case "MISSING_ID" -> contents.put(RECIPE, new String(contents.get(RECIPE), StandardCharsets.UTF_8).replace("legacybridge_composed:count_token", "legacybridge_composed:missing").getBytes(StandardCharsets.UTF_8));
            case "FALSE_CONDITION" -> contents.put(RECIPE, new String(contents.get(RECIPE), StandardCharsets.UTF_8).replace("\"type\":", "\"conditions\":[{\"type\":\"forge:false\"}],\"type\":").getBytes(StandardCharsets.UTF_8));
            case "DEPENDENCY" -> contents.put("META-INF/mods.toml", (new String(contents.get("META-INF/mods.toml"), StandardCharsets.UTF_8)
                    + "\n[[dependencies.legacybridge_composed]]\nmodId=\"absentlib\"\nmandatory=true\nversionRange=\"[1,)\"\nordering=\"NONE\"\nside=\"BOTH\"\n").getBytes(StandardCharsets.UTF_8));
            case "PLATFORM_CLASS" -> {
                ClassWriter writer = new ClassWriter(0); writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "net/minecraft/Injected", null, "java/lang/Object", null); writer.visitEnd();
                contents.put("net/minecraft/Injected.class", writer.toByteArray());
            }
            default -> {
                ClassNode node = new ClassNode(); new ClassReader(contents.get(ITEM)).accept(node, 0);
                MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "unreviewed", "()V", null, null);
                if (defect.equals("UNKNOWN_CALL")) method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "unreviewed/Library", "act", "()V", false));
                else {
                    method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                    method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraftforge/fml/RegistryObject", "get", "()Lnet/minecraftforge/registries/IForgeRegistryEntry;", false));
                    method.instructions.add(new InsnNode(Opcodes.POP));
                }
                method.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(method);
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); contents.put(ITEM, writer.toByteArray());
            }
        }
        Path source = write(defect + ".jar", contents); byte[] before = Files.readAllBytes(source); Path output = directory.resolve(defect + "-out.jar");
        var result = LogicPreservingConverter.previewLogicWithRecipes(source, output, inputs());
        assertFalse(result.staticallyAdmitted(), result.toString()); assertTrue(result.operations().isEmpty());
        assertEquals("UNSUPPORTED", result.report().get("compatibilityStatus")); assertEquals(false, result.report().get("publicationAllowed"));
        assertFalse(Files.exists(output)); assertArrayEquals(before, Files.readAllBytes(source));
        var candidate = LogicPreservingConverter.planLogicWithRecipes(source, output, inputs());
        assertFalse(candidate.validated()); assertTrue(candidate.candidateSha256().isEmpty());
        assertFalse(Files.exists(output)); assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test void cancellationCannotProduceOutputOrAcceptedPreview() throws Exception {
        var ref = new AtomicReference<OperationControl>();
        var control = new OperationControl(OperationLimits.defaults(), progress -> { if (progress.stage() == OperationStage.CLASS_REMAP) ref.get().requestCancel(); });
        ref.set(control); Path output = directory.resolve("cancel.jar");
        MappingInputs baseline = inputs();
        try (var scope = control.enter()) {
            assertThrows(OperationStoppedException.class, () -> LogicPreservingConverter.previewLogicWithRecipes(fixture(), output, baseline));
        }
        assertFalse(Files.exists(output));
    }
    @Test void wrongHashAndBuiltinScopeAreStructuredServiceFailures() throws Exception {
        checkServiceHashAndScope(directory.resolve("wrong-hash.jar"));
    }
    private void checkServiceHashAndScope(Path output) throws Exception {
        mappings = null;
        try (var service = new LegacyEngineService()) {
            var request = new ConversionRequest(fixture(), output, MappingData.profile(profile()), Optional.of("0".repeat(64)), ConversionProfile.LOGIC_PRESERVING_V1);
            var result = service.previewLogicWithRecipes(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.FAILED, result.status()); assertEquals("INPUT_CHANGED", result.error().orElseThrow().code());
            var candidate = service.planLogicWithRecipes(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.FAILED, candidate.status()); assertEquals("INPUT_CHANGED", candidate.error().orElseThrow().code());
            var builtin = new ConversionRequest(fixture(), output, MappingData.builtinSimpleContent(), Optional.empty(), ConversionProfile.LOGIC_PRESERVING_V1);
            assertNotEquals(EngineOutcome.Status.COMPLETED, service.previewLogicWithRecipes(builtin).completion().toCompletableFuture().get(90, TimeUnit.SECONDS).status());
            assertEquals("MAPPING_SCOPE_UNSUPPORTED", service.planLogicWithRecipes(builtin).completion().toCompletableFuture().get(90, TimeUnit.SECONDS).error().orElseThrow().code());
        }
        assertFalse(Files.exists(output));
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static Map<String, byte[]> read(Path path) throws Exception {
        Map<String, byte[]> result = new TreeMap<>();
        try (var zip = new ZipFile(path.toFile())) { for (var entry : zip.stream().toList()) if (!entry.isDirectory())
            try (var stream = zip.getInputStream(entry)) { result.put(entry.getName(), stream.readAllBytes()); } }
        return result;
    }
    private Path write(String name, Map<String, byte[]> contents) throws Exception {
        Path path = directory.resolve(name);
        try (var zip = new ZipOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            for (var entry : contents.entrySet()) { var item = new ZipEntry(entry.getKey()); item.setTime(0); zip.putNextEntry(item); zip.write(entry.getValue()); zip.closeEntry(); }
        }
        return path;
    }
}
