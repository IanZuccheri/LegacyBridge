package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.mapping.portable.PortableMappingBundle;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MappingDataInspectionCliTest {
    @TempDir Path temp;
    @Test void cliManifestPinRejectsDriftBeforeReadingMissingModOrPublishingOutput() throws Exception {
        Path bundle = PortableMappingBundle.export(new MappingInputsLoader().load(CliEngineFixture.profile(temp)), temp.resolve("bundle"));
        String sha = PortableMappingBundle.manifestFingerprint(bundle);
        Files.writeString(bundle.resolve("manifest.json"), "\n", StandardOpenOption.APPEND);
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream(); Path output = temp.resolve("must-not-exist.jar");
        assertEquals(3, LegacyBridgeCli.run(new String[]{"convert", temp.resolve("missing.jar").toString(), "--output", output.toString(),
                "--mapping-bundle", bundle.toString(), "--mapping-sha256", sha}, new PrintStream(out), new PrintStream(err), Map.of()));
        assertTrue(err.toString().contains("MAPPING_DATA_CHANGED")); assertEquals(0, out.size()); assertFalse(Files.exists(output));
    }
    @Test void cliPinsMustBeExplicitWellFormedUniqueAndNotUsedForPreparationOrAnalysis() {
        for (String[] args : List.of(new String[]{"mappings", "input.jar", "--mapping-sha256", "a".repeat(64)},
                new String[]{"analyze", "input.jar", "--mapping-sha256", "a".repeat(64)},
                new String[]{"mappings", "input.jar", "--mapping-bundle", "data", "--mapping-sha256", "wrong"},
                new String[]{"mappings", "input.jar", "--mapping-bundle", "data", "--mapping-sha256", "a".repeat(64), "--mapping-sha256", "b".repeat(64)})) {
            assertThrows(IllegalArgumentException.class, () -> CliArguments.parse(args));
        }
    }
    @Test void builtinReviewsAreCanonicalAcrossCliAndServiceAndNeverApproval() throws Exception {
        for (String id : List.of("simple-content-v1", "cohort-preview-v1")) {
            var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
            assertEquals(0, LegacyBridgeCli.run(new String[]{"inspect-data", "--builtin", id}, new PrintStream(out), new PrintStream(err), Map.of("LEGACYBRIDGE_MAPPING_BUNDLE", "missing")), err.toString());
            try (var engine = new LegacyEngineService()) {
                var result = engine.inspectMappingData(id.startsWith("simple") ? MappingData.builtinSimpleContent() : MappingData.builtinCohortPreview()).completion().toCompletableFuture().get().value().orElseThrow();
                assertEquals(new CanonicalReportWriter().toJsonTree(result.toTree()), out.toString(java.nio.charset.StandardCharsets.UTF_8));
                assertFalse(result.complete()); assertEquals(false, result.toTree().get("conversionAuthorized"));
                assertEquals(false, result.toTree().get("publisherAuthenticated"));
                assertThrows(UnsupportedOperationException.class, () -> result.indexes().clear());
            }
        }
    }
    @Test void reviewedBundleRevalidatesPayloadsAndPinsManifestChanges() throws Exception {
        Path bundle = PortableMappingBundle.export(new MappingInputsLoader().load(CliEngineFixture.profile(temp)), temp.resolve("bundle"));
        try (var engine = new LegacyEngineService()) {
            var review = engine.inspectMappingData(MappingData.bundle(bundle)).completion().toCompletableFuture().get().value().orElseThrow();
            assertTrue(review.complete()); assertTrue(review.selection().expectedManifestSha256().isPresent());
            assertEquals(6, review.indexes().size());
            assertEquals(review, engine.inspectMappingData(review.selection()).completion().toCompletableFuture().get().value().orElseThrow());
            Files.writeString(bundle.resolve("manifest.json"), "\n", StandardOpenOption.APPEND);
            var stale = engine.inspectMappingData(review.selection()).completion().toCompletableFuture().get();
            assertEquals("MAPPING_DATA_CHANGED", stale.error().orElseThrow().code()); assertTrue(stale.value().isEmpty());
            var refreshed = engine.inspectMappingData(MappingData.bundle(bundle)).completion().toCompletableFuture().get().value().orElseThrow();
            assertNotEquals(review.selection().expectedManifestSha256(), refreshed.selection().expectedManifestSha256());
            Files.write(bundle.resolve("legacy-api.lbapi"), new byte[]{0}, StandardOpenOption.APPEND);
            var bad = engine.inspectMappingData(refreshed.selection()).completion().toCompletableFuture().get();
            assertTrue(bad.value().isEmpty()); assertEquals("IO_ERROR", bad.error().orElseThrow().code());
        }
    }
    @Test void invalidSelectionDoesNotReadEnvironmentOrWriteAnOutput() {
        for (String[] args : List.of(new String[]{"inspect-data"}, new String[]{"inspect-data", "--builtin", "unknown"},
                new String[]{"inspect-data", "--mapping-profile", "anything"}, new String[]{"inspect-data", "--mapping-bundle", ""})) {
            var out = new ByteArrayOutputStream();
            assertEquals(2, LegacyBridgeCli.run(args, new PrintStream(out), new PrintStream(new ByteArrayOutputStream()), Map.of()));
            assertEquals(0, out.size());
        }
    }
    @Test void missingExplicitDataDoesNotBecomeBuiltinSuccess() {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        assertEquals(3, LegacyBridgeCli.run(new String[]{"inspect-data", "--mapping-bundle", temp.resolve("missing").toString()}, new PrintStream(out), new PrintStream(err), Map.of()));
        assertEquals(0, out.size()); assertTrue(err.toString().contains("IO_ERROR"));
    }
    @Test void manifestPinCannotBeAttachedToOtherDataKinds() {
        assertThrows(IllegalArgumentException.class, () -> new MappingData(MappingData.Kind.BUILTIN_SIMPLE_CONTENT, Optional.empty(), Optional.of("a".repeat(64))));
        assertThrows(IllegalArgumentException.class, () -> MappingData.reviewedBundle(temp, "bad"));
    }
}
