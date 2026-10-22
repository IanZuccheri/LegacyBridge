package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.archive.InputRejectedException;
import dev.legacybridge.converter.conversion.composition.CompositionPlan;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.transformation.TransformationException;
import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.logic.ComposedArtifactAdapter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Manifest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic structural plan/ZIP tests, NOT source API, bytecode or gameplay qualification. */
class CompositionArtifactPlanTest {
    @TempDir Path directory;
    private static final String OWNED = "owned/Mod.class", DATA = "data/example/recipes/a.json";
    private static Map<String, byte[]> source() {
        var entries = new TreeMap<String, byte[]>();
        for (String path : List.of(OWNED, DATA, "META-INF/mods.toml", "META-INF/MANIFEST.MF", "pack.mcmeta", "LICENSE"))
            entries.put(path, path.getBytes(StandardCharsets.UTF_8));
        return entries;
    }
    private static CompositionPlan base(Map<String, byte[]> entries) {
        return new CompositionPlan(List.of(
                new CompositionPlan.Operation("logic.owned-bodies", CompositionPlan.Kind.PRESERVE_OWNED_BODIES, Set.of(), List.of(claim(entries, OWNED))),
                new CompositionPlan.Operation("data.recipe-resources", CompositionPlan.Kind.PRESERVE_RECIPE_RESOURCES, Set.of("logic.owned-bodies"), List.of(claim(entries, DATA)))));
    }
    private static CompositionPlan.Claim claim(Map<String, byte[]> entries, String path) {
        return new CompositionPlan.Claim(path, SimpleContentConverter.hash(entries.get(path)));
    }
    private static Map<String, byte[]> target(Map<String, byte[]> source) {
        var target = new TreeMap<>(source);
        target.put("META-INF/legacybridge/example/adapters.json", "bindings".getBytes(StandardCharsets.UTF_8));
        target.put(OWNED, "rewritten".getBytes(StandardCharsets.UTF_8));
        return target;
    }

    @Test void completePlanPinsEveryByteAndDoesNotOwnMutableMaps() {
        var source = source(); var target = target(source);
        var plan = CompositionArtifactPlan.resolve(source, target, base(source), "example");
        plan.verify(source, target);
        var before = plan.toTree(); assertEquals(7, before.get("entryCount"));
        assertEquals(false, before.get("publicationAllowed"));
        Set<Object> declared = new HashSet<>(Set.of("logic.owned-bodies", "data.recipe-resources"));
        for (String group : List.of("metadataOperations", "inertOperations")) for (Object raw : (List<?>) before.get(group))
            assertTrue(declared.add(((Map<?, ?>) raw).get("id")));
        for (Object raw : (List<?>) before.get("entries")) assertTrue(declared.contains(((Map<?, ?>) raw).get("operation")));
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) before.get("entries")).clear());
        source.clear(); target.clear(); assertEquals(before, plan.toTree());
        assertThrows(TransformationException.class, () -> plan.verify(source, target));
    }

    @ParameterizedTest @ValueSource(strings = {"SOURCE_LOSS", "DATA_MUTATION", "INERT_MUTATION", "UNKNOWN_METADATA", "MISSING_METADATA", "NEW_CLASS", "STALE_CLAIM", "EXTRA_DATA"})
    void resolutionRefusesIncompleteOrUnimplementedWork(String defect) {
        var source = source(); var target = target(source); var base = base(source);
        switch (defect) {
            case "SOURCE_LOSS" -> target.remove("LICENSE");
            case "DATA_MUTATION" -> target.put(DATA, new byte[]{1});
            case "INERT_MUTATION" -> target.put("LICENSE", new byte[]{1});
            case "UNKNOWN_METADATA" -> { source.put("META-INF/unknown.bin", new byte[]{1}); target.put("META-INF/unknown.bin", new byte[]{1}); }
            case "MISSING_METADATA" -> target.remove("pack.mcmeta");
            case "NEW_CLASS" -> target.put("owned/New.class", new byte[]{1});
            case "STALE_CLAIM" -> source.put(OWNED, new byte[]{1});
            case "EXTRA_DATA" -> target.put("data/example/recipes/extra.json", new byte[]{1});
        }
        assertThrows(TransformationException.class, () -> CompositionArtifactPlan.resolve(source, target, base, "example"));
    }

    @ParameterizedTest @ValueSource(strings = {"SOURCE", "TARGET", "EXTRA", "MISSING"})
    void reopeningRequiresExactSourceAndOutputHashes(String mutation) {
        var source = source(); var target = target(source);
        var plan = CompositionArtifactPlan.resolve(source, target, base(source), "example");
        switch (mutation) {
            case "SOURCE" -> source.get(OWNED)[0] ^= 1;
            case "TARGET" -> target.get(OWNED)[0] ^= 1;
            case "EXTRA" -> target.put("extra", new byte[]{1});
            case "MISSING" -> target.remove(DATA);
        }
        assertThrows(TransformationException.class, () -> plan.verify(source, target));
    }

    @Test void realZipReopenedAndScratchRemovedOnSuccessFailureAndCancel() throws Exception {
        var payload = Map.of("README", "inert test data".getBytes(StandardCharsets.UTF_8));
        byte[] jar = SimpleContentConverter.jar(payload);
        assertArrayEquals(payload.get("README"), CompositionCandidateBuilder.reopen(jar, directory).get("README"));
        assertEmpty();
        assertThrows(InputRejectedException.class, () -> CompositionCandidateBuilder.reopen(Arrays.copyOf(jar, jar.length - 12), directory));
        assertEmpty();
        AtomicReference<OperationControl> ref = new AtomicReference<>();
        var control = new OperationControl(OperationLimits.defaults(), progress -> {
            if (progress.stage() == OperationStage.ARCHIVE_SCAN) ref.get().requestCancel();
        }); ref.set(control);
        try (var scope = control.enter()) {
            assertThrows(OperationStoppedException.class, () -> CompositionCandidateBuilder.reopen(jar, directory));
        }
        assertEmpty();
    }

    @Test void newOfflineMetadataUsesItsOwnIdentityAndImplementedBindings() throws Exception {
        var bytes = ComposedArtifactAdapter.targetManifest("1.2.3");
        var headers = new Manifest(new ByteArrayInputStream(bytes)).getMainAttributes();
        assertEquals("1.2.3", headers.getValue("Implementation-Version"));
        assertEquals("composed-logic-recipes-v1", headers.getValue("LegacyBridge-Profile"));
        assertEquals("1", headers.getValue("LegacyBridge-Composition-Revision"));
        assertEquals("STATIC-CANDIDATE-ONLY", headers.getValue("LegacyBridge-Qualification"));
        assertFalse(headers.containsKey(new java.util.jar.Attributes.Name("Main-Class")));
        assertArrayEquals(bytes, ComposedArtifactAdapter.targetManifest("1.2.3"));
        assertEquals(TransformationBindings.forComposition().bindingTree(), ComposedArtifactAdapter.bindingTree());
        for (String id : ComposedArtifactAdapter.requiredAdapters()) {
            assertEquals("OFFLINE", AdapterCatalog.require(id).phase());
            assertFalse(AdapterCatalog.require(id).knownLimitations().isBlank());
        }
        assertThrows(UnsupportedCompatibilityException.class, () -> ComposedArtifactAdapter.targetManifest("1\r\nMain-Class: bad"));
    }
    @Test void scratchIoFailureAndWorkLimitDoNotLeaveCandidateFiles() throws Exception {
        byte[] jar = SimpleContentConverter.jar(Map.of("README", new byte[]{1, 2, 3}));
        Path nonDirectory = directory.resolve("not-a-directory"); Files.write(nonDirectory, new byte[]{42});
        assertThrows(IOException.class, () -> CompositionCandidateBuilder.reopen(jar, nonDirectory));
        assertArrayEquals(new byte[]{42}, Files.readAllBytes(nonDirectory));
        var control = new OperationControl(new OperationLimits(1, 4096, 4096, java.time.Duration.ofMinutes(1)), ignored -> { });
        try (var scope = control.enter()) {
            assertThrows(OperationStoppedException.class, () -> CompositionCandidateBuilder.reopen(jar, directory));
        }
        try (var files = Files.list(directory)) { assertEquals(List.of(nonDirectory), files.toList()); }
    }
    private void assertEmpty() throws IOException { try (var files = Files.list(directory)) { assertEquals(0, files.count()); } }
}
