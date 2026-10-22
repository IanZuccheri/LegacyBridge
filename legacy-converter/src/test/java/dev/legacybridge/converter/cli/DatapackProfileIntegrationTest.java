package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.conversion.SimpleContentConverter;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real Forge-compiled authored input is read as inert archive bytes, never loaded. */
@EnabledIfSystemProperty(named = "legacybridge.verifyDatapackEnvironment", matches = "true")
class DatapackProfileIntegrationTest {
    private static final String RECIPE = "data/legacybridge_datapack/recipes/extra.json";
    @TempDir Path temporary;

    @Test void cliServicePlanAndBytesAgreeWithoutRuntimeClaims() throws Exception {
        byte[] before = Files.readAllBytes(fixture());
        Path cli = Files.createDirectory(temporary.resolve("cli")).resolve("converted.jar");
        Path api = Files.createDirectory(temporary.resolve("api")).resolve("converted.jar");
        Path report = temporary.resolve("report.json");
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        int code = LegacyBridgeCli.run(new String[]{"convert", fixture().toString(), "--conversion-profile", "datapack-recipes-v1",
                "--mapping-profile", profile().toString(), "--output", cli.toString(), "--report", report.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8), Map.of());
        assertEquals(0, code, stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8));
        try (var service = new LegacyEngineService()) {
            var request = new ConversionRequest(fixture(), api, MappingData.profile(profile()), Optional.of(hash(before)), ConversionProfile.DATAPACK_RECIPES_V1);
            var planning = service.plan(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, planning.status(), planning.toString());
            var plan = planning.value().orElseThrow();
            assertTrue(plan.convertible(), plan.report().toString());
            assertFalse(Files.exists(api));
            assertEquals(false, plan.report().get("transformationsCommitted"));
            var outcome = service.convert(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, outcome.status(), outcome.toString());
            var result = outcome.value().orElseThrow();
            assertTrue(result.converted(), result.report().toString());
            assertEquals(Files.readString(report), new CanonicalReportWriter().toJsonTree(result.report()));
            assertEquals("datapack-recipes-v1", result.report().get("supportProfile"));
            assertEquals(2, result.report().get("schemaVersion"));
            assertEquals(false, result.report().get("bytecodeExecuted"));
            var data = (Map<?, ?>) result.report().get("resourceValidation");
            assertEquals(1, data.get("schemaVersion"));
            assertEquals("PASSED_STATIC", data.get("status"));
            assertEquals("NOT_RUN", data.get("runtimeValidation"));
            var recipes = (List<?>) data.get("recipes");
            assertEquals(6, recipes.size());
            recipes.forEach(raw -> assertEquals("PRESERVE_BYTES", ((Map<?, ?>) raw).get("disposition")));
            assertEquals(plan.candidateSha256().orElseThrow(), hash(Files.readAllBytes(api)));
            var trace = (Map<?, ?>) result.report().get("transformationLog");
            assertEquals(1, trace.get("schemaVersion"));
            var changes = (List<?>) trace.get("entries");
            assertTrue(changes.stream().map(raw -> (Map<?, ?>) raw).anyMatch(change ->
                    "RESOURCE_REWRITE".equals(change.get("kind")) && "pack.mcmeta".equals(change.get("location"))));
            for (Object raw : changes) {
                var change = (Map<?, ?>) raw;
                assertEquals("VERIFIED", change.get("confidence"));
                assertNotNull(change.get("adapter"));
                for (Object recipe : recipes)
                    assertNotEquals(((Map<?, ?>) recipe).get("path"), change.get("location"), "Unchanged recipe is not a rewrite");
            }
        }
        assertArrayEquals(Files.readAllBytes(cli), Files.readAllBytes(api));
        Map<String, byte[]> original = entries(fixture()), target = entries(api);
        original.forEach((path, bytes) -> {
            if (path.startsWith("data/") || path.startsWith("assets/")) assertArrayEquals(bytes, target.get(path), path);
        });
        assertArrayEquals(before, Files.readAllBytes(fixture()));
        assertNotEquals(hash(original.get("pack.mcmeta")), hash(target.get("pack.mcmeta")));
        assertTrue(new String(target.get("pack.mcmeta"), StandardCharsets.UTF_8).contains("15"));
    }

    @Test void originalProfileStillRefusesNewFixtureAndBadProfileRoutingIsImpossible() throws Exception {
        var mappings = new MappingInputsLoader().load(profile());
        Path output = temporary.resolve("old.jar");
        var result = new SimpleContentConverter().convert(fixture(), output, mappings);
        assertFalse(result.converted());
        assertTrue(result.report().get("diagnostics").toString().contains("recipe serializer is unsupported"));
        assertFalse(Files.exists(output));
        assertThrows(IllegalArgumentException.class, () -> new SimpleContentConverter(ConversionProfile.FOOD_ITEM_V1));
        assertThrows(IllegalArgumentException.class, () -> new dev.legacybridge.converter.conversion.LogicPreservingConverter(ConversionProfile.DATAPACK_RECIPES_V1));
    }

    @Test void negativeResourcesAbortEntireArchiveWithoutDroppingAnything() throws Exception {
        var mappings = new MappingInputsLoader().load(profile());
        Map<String, String> negatives = new LinkedHashMap<>();
        negatives.put("missing-result", "{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"legacybridge_datapack:bridge_ingot\"},\"result\":\"legacybridge_datapack:missing\"}");
        negatives.put("false-condition", "{\"type\":\"minecraft:smelting\",\"conditions\":[{\"type\":\"forge:false\"}],\"ingredient\":{\"item\":\"legacybridge_datapack:bridge_ingot\"},\"result\":\"legacybridge_datapack:bridge_block\"}");
        negatives.put("custom-serializer", "{\"type\":\"legacybridge_datapack:custom\"}");
        negatives.put("custom-ingredient", "{\"type\":\"minecraft:smelting\",\"ingredient\":{\"type\":\"forge:nbt\",\"item\":\"legacybridge_datapack:bridge_ingot\"},\"result\":\"legacybridge_datapack:bridge_block\"}");
        negatives.put("parser-truncated", "{\"type\":");
        negatives.put("parser-duplicate", "{\"type\":\"minecraft:smelting\",\"type\":\"minecraft:smoking\"}");
        negatives.put("wrong-version", "{\"type\":\"minecraft:smithing_transform\"}");
        byte[] original = Files.readAllBytes(fixture());
        for (var negative : negatives.entrySet()) {
            Map<String, byte[]> archive = entries(fixture());
            archive.put(RECIPE, negative.getValue().getBytes(StandardCharsets.UTF_8));
            Path input = write(negative.getKey() + ".jar", archive), output = temporary.resolve(negative.getKey() + "-converted.jar");
            byte[] before = Files.readAllBytes(input);
            var result = converter().convert(input, output, mappings);
            assertFalse(result.converted(), negative.getKey());
            assertEquals("ABORTED", result.report().get("status"));
            assertEquals("UNSUPPORTED", result.report().get("compatibilityStatus"));
            assertEquals(false, result.report().get("transformationsCommitted"));
            assertNull(result.report().get("output"));
            assertEquals("BLOCKED", ((Map<?, ?>) result.report().get("resourceValidation")).get("status"));
            assertTrue(result.report().get("diagnostics").toString().contains(RECIPE), result.report().toString());
            assertDoesNotThrow(() -> BoundedJsonParser.parse(new CanonicalReportWriter().toJsonTree(result.report())));
            assertFalse(Files.exists(output));
            assertArrayEquals(before, Files.readAllBytes(input));
        }
        assertArrayEquals(original, Files.readAllBytes(fixture()));
    }

    @Test void acceptedCancellationAtPublicationLeavesNoResult() throws Exception {
        var mappings = new MappingInputsLoader().load(profile());
        Path directory = Files.createDirectory(temporary.resolve("cancelled")), output = directory.resolve("converted.jar");
        var reference = new AtomicReference<OperationControl>();
        var control = new OperationControl(OperationLimits.defaults(), progress -> {
            if (progress.stage() == OperationStage.PRE_PUBLICATION) assertTrue(reference.get().requestCancel());
        });
        reference.set(control);
        byte[] before = Files.readAllBytes(fixture());
        try (var ignored = control.enter()) {
            assertEquals(OperationStoppedException.Code.CANCELLED,
                    assertThrows(OperationStoppedException.class, () -> converter().convert(fixture(), output, mappings)).code());
        }
        try (var paths = Files.list(directory)) { assertEquals(0, paths.count()); }
        assertArrayEquals(before, Files.readAllBytes(fixture()));
    }

    private SimpleContentConverter converter() { return new SimpleContentConverter(ConversionProfile.DATAPACK_RECIPES_V1); }
    private static Path profile() { return Path.of(System.getProperty("legacybridge.datapackMappingProfile")); }
    private static Path fixture() { return Path.of(System.getProperty("legacybridge.fixtureRoot"), "datapack-recipes-1.16.5/build/libs/legacybridge-datapack-recipes-1.16.5-1.0.0.jar"); }
    private static String hash(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception ex) { throw new AssertionError(ex); } }
    private static Map<String, byte[]> entries(Path path) throws IOException {
        assertTrue(Files.size(path) < 2 * 1024 * 1024);
        Map<String, byte[]> entries = new TreeMap<>();
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                var entry = iterator.nextElement();
                if (entry.isDirectory()) continue;
                try (var input = zip.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes(1_048_577);
                    assertTrue(bytes.length <= 1_048_576);
                    assertNull(entries.put(entry.getName(), bytes));
                }
            }
        }
        return entries;
    }
    private Path write(String name, Map<String, byte[]> entries) throws IOException {
        Path path = temporary.resolve(name);
        try (var output = new ZipOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            for (var entry : entries.entrySet()) { output.putNextEntry(new ZipEntry(entry.getKey())); output.write(entry.getValue()); output.closeEntry(); }
        }
        return path;
    }
}
