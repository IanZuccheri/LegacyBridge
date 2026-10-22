package dev.legacybridge.converter.mapping.builtin;

import dev.legacybridge.converter.mapping.portable.PortableMappingBundle;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.service.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddedCohortDataTest {
    @TempDir Path temp;
    @Test void finiteDataHasOneDeclarationAndNoNameMappingsOrFullInventoryClaim() throws Exception {
        var data = EmbeddedCohortData.load();
        assertFalse(data.complete()); assertEquals(MappingInputs.Scope.FINITE_COHORT_CONTRACT, data.scope());
        assertEquals(data.legacyApi().classes(), data.targetApi().classes()); assertEquals(1, data.legacyApi().classes().size());
        var mod = data.legacyApi().getClass(EmbeddedCohortData.MOD);
        assertEquals(9729, mod.access()); assertEquals(1, mod.methods().size()); assertTrue(mod.fields().isEmpty());
        for (var table : List.of(data.legacyOfficial(), data.targetOfficial(), data.legacySrg(), data.targetSrg())) assertTrue(table.getClasses().isEmpty());
        assertEquals(5, data.sources().size()); assertThrows(UnsupportedOperationException.class, () -> data.sources().clear());
        assertThrows(IllegalArgumentException.class, () -> new MappingInputs(data.legacyOfficial(), data.targetOfficial(), data.legacySrg(), data.targetSrg(),
                data.legacyApi(), data.targetApi(), data.sources(), List.of(), true, data.scope()));
    }
    @ParameterizedTest @ValueSource(strings = {"empty", "truncate", "append", "change", "oversize", "invalid-utf8"})
    void missingCorruptChangedOrOversizedBytesNeverFallback(String mutation) throws Exception {
        byte[] bytes;
        try (var stream = getClass().getResourceAsStream("/" + EmbeddedCohortData.RESOURCE_PATH)) { assertNotNull(stream); bytes = stream.readAllBytes(); }
        assertEquals(537, bytes.length);
        switch (mutation) {
            case "empty" -> bytes = new byte[0];
            case "truncate" -> bytes = Arrays.copyOf(bytes, bytes.length - 1);
            case "append" -> bytes = Arrays.copyOf(bytes, bytes.length + 1);
            case "change" -> bytes[0] ^= 1;
            case "oversize" -> bytes = new byte[EmbeddedCohortData.MAX_BYTES + 1];
            case "invalid-utf8" -> bytes[0] = (byte) 0xff;
        }
        byte[] selected = bytes;
        assertThrows(IOException.class, () -> EmbeddedCohortData.load(new ByteArrayInputStream(selected)));
    }
    @Test void finiteDataCannotBeExportedAsAFullBundle() throws Exception {
        assertThrows(IOException.class, () -> PortableMappingBundle.export(EmbeddedCohortData.load(), temp.resolve("fake-full")));
        assertFalse(java.nio.file.Files.exists(temp.resolve("fake-full")));
    }
    @Test void wrongOperationsRejectScopeBeforeAccessingMissingInputOrPublishing() throws Exception {
        try (var engine = new LegacyEngineService()) {
            var data = MappingData.builtinCohortPreview(); var input = temp.resolve("missing.jar");
            var mappings = engine.mappings(input, data).completion().toCompletableFuture().get();
            assertEquals("MAPPING_SCOPE_UNSUPPORTED", mappings.error().orElseThrow().code());
            for (var profile : ConversionProfile.values()) {
                var request = new ConversionRequest(input, temp.resolve(profile.id() + ".jar"), data, Optional.empty(), profile);
                var result = engine.convert(request).completion().toCompletableFuture().get();
                assertEquals("MAPPING_SCOPE_UNSUPPORTED", result.error().orElseThrow().code());
                assertTrue(result.value().isEmpty()); assertFalse(java.nio.file.Files.exists(request.output()));
            }
            var request = new ConversionRequest(input, temp.resolve("preview.jar"), data, Optional.empty(), ConversionProfile.LOGIC_PRESERVING_V1);
            assertEquals("MAPPING_SCOPE_UNSUPPORTED", engine.previewLogicWithRecipes(request).completion().toCompletableFuture().get().error().orElseThrow().code());
        }
    }
    @Test void explicitMissingBundleDoesNotFallbackToIncludedData() throws Exception {
        try (var engine = new LegacyEngineService()) {
            var input = new CohortInspectionRequest.Artifact("a", temp.resolve("missing.jar"), "0".repeat(64));
            var result = engine.planCohort(new CohortPlanningRequest(new CohortInspectionRequest(List.of(input), MappingData.bundle(temp.resolve("missing-bundle")))))
                    .completion().toCompletableFuture().get();
            assertEquals(EngineOutcome.Status.FAILED, result.status()); assertTrue(result.value().isEmpty());
        }
    }
}
