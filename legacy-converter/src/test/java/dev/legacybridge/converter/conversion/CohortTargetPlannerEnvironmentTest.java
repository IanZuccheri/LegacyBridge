package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.mapping.portable.PortableMappingBundle;
import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.service.*;
import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.CohortMetadataAdapter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Static only. The actual Forge-compiled fixture is read as data, never added to a classloader. */
@EnabledIfSystemProperty(named = "legacybridge.cohortBundle", matches = ".+")
class CohortTargetPlannerEnvironmentTest {
    @TempDir Path scratch;
    static MappingInputs inputs;
    static CohortInspectionRequest source;
    @BeforeAll static void data() throws Exception {
        var data = MappingData.bundle(Path.of(System.getProperty("legacybridge.cohortBundle")));
        inputs = PortableMappingBundle.load(data.path().orElseThrow());
        var selections = new ArrayList<CohortInspectionRequest.Artifact>();
        for (String id : List.of("consumer", "library")) {
            Path jar = Path.of(System.getProperty("legacybridge.fixtureRoot"), "dependency-cohort-1.16.5/build/libs/legacybridge-cohort-" + id + "-1.16.5-1.0.0.jar");
            selections.add(artifact(id, jar));
        }
        source = new CohortInspectionRequest(selections, data);
    }
    static CohortInspectionRequest.Artifact artifact(String id, Path file) throws IOException {
        return new CohortInspectionRequest.Artifact(id, file, SimpleContentConverter.hash(Files.readAllBytes(file)));
    }
    CohortTargetPlanner.Result plan(CohortInspectionRequest request) { return new CohortTargetPlanner().plan(new CohortPlanningRequest(request), inputs); }

    @Test void wholePairPreservesBodiesDependenciesVersionsAndReturnsOnlyStaticEvidence() throws Exception {
        var archives = new TreeMap<String, Map<String, byte[]>>();
        var result = new CohortTargetPlanner().plan(new CohortPlanningRequest(source), inputs, (id, jar) -> {
            var entries = CohortTargetPlanner.reopen(jar, scratch); archives.put(id, entries); return entries;
        });
        assertTrue(result.validated(), result.toTree().toString()); assertEquals(2, result.candidates().size());
        assertEquals(6, result.transformationLog().get("entryCount"));
        for (var selected : source.artifacts()) {
            var original = read(selected.input()); var target = archives.get(selected.id());
            for (var entry : original.entrySet()) if (entry.getKey().endsWith(".class")) assertArrayEquals(entry.getValue(), target.get(entry.getKey()));
            String toml = new String(target.get("META-INF/mods.toml"), StandardCharsets.UTF_8);
            assertTrue(toml.contains("version=\"1.0.0\"")); assertTrue(toml.contains("loaderVersion=\"[47,48)\""));
            assertTrue(toml.contains("modId=\"legacybridge_runtime\""));
            var manifest = new Manifest(new ByteArrayInputStream(target.get("META-INF/MANIFEST.MF")));
            assertEquals(CohortMetadataAdapter.ID, manifest.getMainAttributes().getValue("LegacyBridge-Profile"));
            assertEquals("STATIC-CANDIDATE-ONLY", manifest.getMainAttributes().getValue("LegacyBridge-Qualification"));
        }
        for (var side : result.targetGraph().orElseThrow().sides().values()) {
            assertTrue(side.modOrder().indexOf("lb_cohort_library") < side.modOrder().indexOf("lb_cohort_consumer"));
            assertTrue(side.dependencies().stream().anyMatch(d -> d.ownerModId().equals("lb_cohort_consumer")
                    && d.declaration().modId().equals("lb_cohort_library") && d.declaration().mandatory() && d.declaration().versionRange().equals("[1.0.0]")));
        }
        assertEquals("NOT_RUN", result.toTree().get("runtimeValidation")); assertEquals(false, result.toTree().get("installationAllowed"));
        assertEmptyScratch();
    }
    @Test void missingProviderCannotProducePartialCandidate() { blocked(plan(new CohortInspectionRequest(List.of(source.artifacts().get(0)), source.mappingData())), "COHORT_SOURCE_BLOCKED"); }
    @Test void libraryAloneHasSourceLinkageButNoWholePairRecipe() { blocked(plan(new CohortInspectionRequest(List.of(source.artifacts().get(1)), source.mappingData())), "FINITE_COHORT_RECIPE_REQUIRED"); }
    @Test void metadataChangeNeedsAnotherExactRecipe() throws Exception {
        var entries = read(source.artifacts().get(0).input());
        entries.put("META-INF/mods.toml", new String(entries.get("META-INF/mods.toml"), StandardCharsets.UTF_8).replace("version=\"1.0.0\"", "version=\"1.0.1\"").getBytes(StandardCharsets.UTF_8));
        blocked(plan(mutant(entries)), "FINITE_COHORT_RECIPE_REQUIRED");
    }
    @Test void unknownResourceIsNotCopiedSilently() throws Exception {
        var entries = read(source.artifacts().get(0).input()); entries.put("assets/unreviewed.json", "{}".getBytes(StandardCharsets.UTF_8));
        blocked(plan(mutant(entries)), "FINITE_COHORT_RECIPE_REQUIRED");
    }
    @Test void validButDifferentBodyIsNotAcceptedByLinkageAlone() throws Exception {
        var entries = read(source.artifacts().get(0).input()); String name = "dev/legacybridge/fixture/cohort/consumer/ConsumerMod.class";
        var node = new org.objectweb.asm.tree.ClassNode(); new org.objectweb.asm.ClassReader(entries.get(name)).accept(node, 0);
        for (var method : node.methods) for (var instruction : method.instructions.toArray())
            if (instruction.getOpcode() == org.objectweb.asm.Opcodes.ICONST_5) method.instructions.set(instruction, new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ICONST_4));
        var writer = new org.objectweb.asm.ClassWriter(0); node.accept(writer); entries.put(name, writer.toByteArray());
        blocked(plan(mutant(entries)), "FINITE_COHORT_RECIPE_REQUIRED");
    }
    @Test void wrongSelectedHashNeverReachesSerialization() {
        var old = source.artifacts().get(0);
        var wrong = new CohortInspectionRequest.Artifact(old.id(), old.input(), "0".repeat(64));
        blocked(plan(new CohortInspectionRequest(List.of(wrong, source.artifacts().get(1)), source.mappingData())), "COHORT_SOURCE_BLOCKED");
    }
    @Test void secondArchiveWriteFailureDiscardsFirstCandidate() throws Exception {
        var count = new AtomicInteger();
        var result = new CohortTargetPlanner().plan(new CohortPlanningRequest(source), inputs, (id, jar) -> {
            if (count.incrementAndGet() == 2) throw new IOException("simulated disk full or locked output");
            return CohortTargetPlanner.reopen(jar, scratch);
        });
        assertEquals(2, count.get()); blocked(result, "COHORT_TARGET_IO"); assertEmptyScratch();
    }
    @Test void alteredReopenedBodyBlocksTheEntirePair() {
        var result = new CohortTargetPlanner().plan(new CohortPlanningRequest(source), inputs, (id, jar) -> {
            var entries = CohortTargetPlanner.reopen(jar, scratch);
            if (id.equals("library")) entries.put("dev/legacybridge/fixture/cohort/library/Score.class", new byte[]{1, 2});
            return entries;
        });
        blocked(result, "TARGET_ENTRY_DRIFT");
    }
    @Test void missingReopenedDependencyMetadataBlocksTheEntirePair() {
        var result = new CohortTargetPlanner().plan(new CohortPlanningRequest(source), inputs, (id, jar) -> {
            var entries = CohortTargetPlanner.reopen(jar, scratch); entries.remove("META-INF/mods.toml"); return entries;
        });
        blocked(result, "TARGET_ENTRY_DRIFT");
    }
    @Test void cancelAfterFirstPrivateCandidateNeverReturnsAnApprovedPair() throws Exception {
        var control = new OperationControl(OperationLimits.defaults(), p -> {}); var count = new AtomicInteger();
        try (var scope = control.enter()) {
            assertThrows(OperationStoppedException.class, () -> new CohortTargetPlanner().plan(new CohortPlanningRequest(source), inputs, (id, jar) -> {
                var entries = CohortTargetPlanner.reopen(jar, scratch); count.incrementAndGet(); control.requestCancel(); return entries;
            }));
        }
        assertEquals(1, count.get()); assertEmptyScratch();
    }
    @Test void sourceChangedAfterSerializationInvalidatesWholePair() throws Exception {
        Path copy = scratch.resolve("consumer.jar"); Files.copy(source.artifacts().get(0).input(), copy);
        var selected = artifact("consumer", copy);
        var request = new CohortInspectionRequest(List.of(selected, source.artifacts().get(1)), source.mappingData());
        var result = new CohortTargetPlanner().plan(new CohortPlanningRequest(request), inputs, (id, jar) -> {
            var entries = CohortTargetPlanner.reopen(jar, scratch);
            if (id.equals("library")) Files.write(copy, new byte[]{0}); return entries;
        });
        assertFalse(result.validated()); assertTrue(result.candidates().isEmpty()); assertFalse(result.diagnostics().isEmpty());
    }
    @Test void mismatchedTargetApiCannotApproveUnchangedBytes() {
        var changed = new MappingInputs(inputs.legacyOfficial(), inputs.targetOfficial(), inputs.legacySrg(), inputs.targetSrg(), inputs.legacyApi(),
                new ApiIndex(Map.of()), inputs.sources(), List.of(), true);
        blocked(new CohortTargetPlanner().plan(new CohortPlanningRequest(source), changed), "COHORT_SOURCE_BLOCKED");
    }
    @Test void finiteOfflineAdaptersRejectUnpinnedMetadataAndRemainConcrete() {
        assertThrows(UnsupportedCompatibilityException.class, () -> CohortMetadataAdapter.targetToml("lb_cohort_library", new byte[0]));
        assertThrows(UnsupportedCompatibilityException.class, () -> CohortMetadataAdapter.targetManifest("unknown", new byte[0]));
        assertEquals(3, ((List<?>) CohortMetadataAdapter.bindingTree().get("adapters")).size());
        assertEquals("OFFLINE", AdapterCatalog.require(AdapterCatalog.COHORT_TOML).phase());
    }
    @Test void ordinarySingleModConverterDoesNotAcceptTheCohortConsumer() throws Exception {
        Path output = scratch.resolve("must-not-exist.jar");
        var result = new SimpleContentConverter().convert(source.artifacts().get(0).input(), output, inputs);
        assertFalse(result.converted()); assertFalse(Files.exists(output));
    }
    private CohortInspectionRequest mutant(Map<String, byte[]> entries) throws IOException {
        Path jar = scratch.resolve("mutant.jar"); Files.write(jar, SimpleContentConverter.jar(entries));
        return new CohortInspectionRequest(List.of(artifact("consumer", jar), source.artifacts().get(1)), source.mappingData());
    }
    private static Map<String, byte[]> read(Path jar) throws IOException {
        var entries = new TreeMap<String, byte[]>();
        try (var zip = new ZipFile(jar.toFile())) { var iterator = zip.entries(); while (iterator.hasMoreElements()) {
            var entry = iterator.nextElement(); if (!entry.isDirectory()) try (var stream = zip.getInputStream(entry)) { entries.put(entry.getName(), stream.readAllBytes()); }
        } }
        return entries;
    }
    private static void blocked(CohortTargetPlanner.Result result, String code) {
        assertFalse(result.validated()); assertTrue(result.candidates().isEmpty()); assertTrue(result.diagnostics().stream().anyMatch(e -> e.code().equals(code)), result.toTree().toString());
    }
    private void assertEmptyScratch() throws IOException { try (var files = Files.list(scratch)) { assertEquals(0, files.count()); } }
}
