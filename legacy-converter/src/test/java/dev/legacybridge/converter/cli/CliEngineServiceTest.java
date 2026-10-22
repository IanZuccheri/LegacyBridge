package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.mapping.portable.PortableMappingBundle;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.ConversionRequest;
import dev.legacybridge.converter.service.LegacyEngineService;
import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.converter.testutil.SyntheticForgeJarFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CliEngineServiceTest {
    @TempDir Path directory;

    @Test void analysisJsonIsIdenticalThroughTheCliAndTypedAsyncService() throws Exception {
        Path input = SyntheticForgeJarFactory.create(directory.resolve("input.jar"));
        var cli = run("analyze", input.toString());
        assertEquals(0, cli.code(), cli.errors());
        try (var engine = new LegacyEngineService()) {
            var service = engine.analyze(input).completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals("COMPLETED", service.status().name());
            assertEquals(new CanonicalReportWriter().toJson(service.value().orElseThrow()), cli.json());
        }
    }

    @Test void mappingJsonIsIdenticalThroughTheCliAndTypedAsyncService() throws Exception {
        Path input = SyntheticForgeJarFactory.create(directory.resolve("input.jar"));
        Path profile = CliEngineFixture.profile(directory);
        var cli = run("mappings", input.toString(), "--mapping-profile", profile.toString());
        assertEquals(1, cli.code(), cli.errors());
        try (var engine = new LegacyEngineService()) {
            var service = engine.mappings(input, MappingData.profile(profile)).completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals(new CanonicalReportWriter().toJsonTree(service.value().orElseThrow().toTree()), cli.json());
        }
    }

    @Test void convertedJarAndFullTransformationReportAreIdenticalAcrossFrontends() throws Exception {
        Path input = CliEngineFixture.input(directory.resolve("input.jar"), "valid");
        byte[] original = Files.readAllBytes(input);
        Path profile = CliEngineFixture.profile(directory);
        Path cliJar = Files.createDirectory(directory.resolve("cli")).resolve("converted.jar");
        Path serviceJar = Files.createDirectory(directory.resolve("service")).resolve("converted.jar");
        var cli = run("convert", input.toString(), "--mapping-profile", profile.toString(), "--output", cliJar.toString());
        assertEquals(0, cli.code(), cli.errors() + cli.json());
        try (var engine = new LegacyEngineService()) {
            var service = engine.convert(new ConversionRequest(input, serviceJar, MappingData.profile(profile)))
                    .completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertTrue(service.value().orElseThrow().converted(), service.toString());
            assertEquals(new CanonicalReportWriter().toJsonTree(service.value().orElseThrow().report()), cli.json());
        }
        assertArrayEquals(Files.readAllBytes(cliJar), Files.readAllBytes(serviceJar));
        assertArrayEquals(original, Files.readAllBytes(input));
    }

    @Test void blockedConversionKeepsExistingReportSchemaAndDoesNotPublishAJar() throws Exception {
        Path input = CliEngineFixture.input(directory.resolve("input.jar"), "reflection");
        Path profile = CliEngineFixture.profile(directory);
        Path output = directory.resolve("blocked.jar");
        var cli = run("convert", input.toString(), "--mapping-profile", profile.toString(), "--output", output.toString());
        assertEquals(1, cli.code(), cli.errors());
        Map<?, ?> report = json(cli.json());
        assertEquals("ABORTED", report.get("status"));
        assertEquals(false, report.get("bytecodeModified"));
        assertFalse(Files.exists(output));
        try (var engine = new LegacyEngineService()) {
            var service = engine.convert(new ConversionRequest(input, output, MappingData.profile(profile)))
                    .completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals("BLOCKED", service.status().name());
            assertEquals(new CanonicalReportWriter().toJsonTree(service.value().orElseThrow().report()), cli.json());
        }
    }

    @Test void broadInspectionAndDeveloperProfilesStillRequireExplicitData() throws Exception {
        Path input = SyntheticForgeJarFactory.create(directory.resolve("input.jar"));
        for (String[] args : List.of(new String[]{"mappings", input.toString()},
                new String[]{"convert", input.toString(), "--conversion-profile", "logic-preserving-v1",
                        "--output", directory.resolve("converted.jar").toString()})) {
            var result = run(args);
            assertEquals(3, result.code(), result.errors());
            assertEquals("", result.json());
            assertTrue(result.errors().contains("MAPPING_DATA_REQUIRED"), result.errors());
        }
        assertFalse(Files.exists(directory.resolve("converted.jar")));
    }

    @Test void defaultSimpleConversionUsesIncludedDataAndMatchesTypedService() throws Exception {
        Path input = CliEngineFixture.input(directory.resolve("input.jar"), "valid");
        byte[] original = Files.readAllBytes(input);
        Path cliOutput = Files.createDirectory(directory.resolve("cli")).resolve("converted.jar");
        Path serviceOutput = Files.createDirectory(directory.resolve("service")).resolve("converted.jar");
        var cli = run("convert", input.toString(), "--output", cliOutput.toString());
        assertEquals(0, cli.code(), cli.errors() + cli.json());
        try (var engine = new LegacyEngineService()) {
            var request = new ConversionRequest(input, serviceOutput, MappingData.builtinSimpleContent());
            var plan = engine.plan(request).completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals("COMPLETED", plan.status().name(), plan.toString());
            assertFalse(Files.exists(serviceOutput));
            var service = engine.convert(request).completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals("COMPLETED", service.status().name(), service.toString());
            assertEquals(new CanonicalReportWriter().toJsonTree(service.value().orElseThrow().report()), cli.json());
        }
        assertArrayEquals(Files.readAllBytes(cliOutput), Files.readAllBytes(serviceOutput));
        assertArrayEquals(original, Files.readAllBytes(input));
        assertEquals("SIMPLE_CONTENT_CONTRACT", ((Map<?, ?>) json(cli.json()).get("mappingData")).get("scope"));
    }

    @Test void explicitBadDataNeverFallsBackToIncludedContract() throws Exception {
        Path input = CliEngineFixture.input(directory.resolve("input.jar"), "valid");
        Path output = directory.resolve("converted.jar");
        var missing = directory.resolve("missing-bundle").toString();
        var explicit = run("convert", input.toString(), "--mapping-bundle", missing, "--output", output.toString());
        var environment = run(Map.of(LegacyBridgeCli.MAPPING_BUNDLE_ENV, missing), "convert", input.toString(), "--output", output.toString());
        assertEquals(3, explicit.code());
        assertEquals(3, environment.code());
        assertFalse(Files.exists(output));
    }

    @Test void preparationExportsExplicitPortableDataAndNeverOverwritesIt() throws Exception {
        Path profile = CliEngineFixture.profile(directory);
        Path bundle = directory.resolve("bundle");
        var prepared = run("prepare-mappings", "--output", bundle.toString(), "--mapping-profile", profile.toString());
        assertEquals(0, prepared.code(), prepared.errors());
        Map<?, ?> receipt = json(prepared.json());
        assertEquals("LEGACY_MAPPING_PREPARATION", receipt.get("reportType"));
        assertEquals("PREPARED", receipt.get("status"));
        assertEquals(PortableMappingBundle.DISTRIBUTION_POLICY, receipt.get("distributionPolicy"));
        assertTrue(prepared.errors().contains("local use only"));
        assertTrue(prepared.errors().contains("Redistribution is not approved"));
        byte[] manifest = Files.readAllBytes(bundle.resolve(PortableMappingBundle.MANIFEST));
        assertEquals(3, run("prepare-mappings", "--output", bundle.toString(), "--mapping-profile", profile.toString()).code());
        assertArrayEquals(manifest, Files.readAllBytes(bundle.resolve(PortableMappingBundle.MANIFEST)));
    }

    @Test void explicitBundleAndEnvironmentConfigurationUseTheSamePortableData() throws Exception {
        Path profile = CliEngineFixture.profile(directory);
        Path bundle = directory.resolve("bundle");
        assertEquals(0, run("prepare-mappings", "--output", bundle.toString(), "--mapping-profile", profile.toString()).code());
        Path input = SyntheticForgeJarFactory.create(directory.resolve("input.jar"));
        var explicit = run("mappings", input.toString(), "--mapping-bundle", bundle.toString());
        var configured = run(Map.of(LegacyBridgeCli.MAPPING_BUNDLE_ENV, bundle.toString()), "mappings", input.toString());
        assertEquals(explicit.code(), configured.code());
        assertEquals(1, explicit.code(), explicit.errors());
        assertEquals(explicit.json(), configured.json());
        try (var engine = new LegacyEngineService()) {
            var service = engine.mappings(input, MappingData.bundle(bundle)).completion().toCompletableFuture().get(20, TimeUnit.SECONDS);
            assertEquals(new CanonicalReportWriter().toJsonTree(service.value().orElseThrow().toTree()), explicit.json());
        }
    }

    @Test void explicitProfileOverridesAnEnvironmentDefaultAndAnalyzeNeedsNeither() throws Exception {
        Path input = SyntheticForgeJarFactory.create(directory.resolve("input.jar"));
        Path profile = CliEngineFixture.profile(directory);
        var environment = Map.of(LegacyBridgeCli.MAPPING_BUNDLE_ENV, directory.resolve("missing-bundle").toString());
        var result = run(environment, "mappings", input.toString(), "--mapping-profile", profile.toString());
        assertEquals(1, result.code(), result.errors());
        assertEquals(0, run(environment, "analyze", input.toString()).code());
    }

    @Test void invalidInputAndMissingMappingDataHaveStructuredDiagnosticsAndStableExitCodes() throws Exception {
        Path malformed = directory.resolve("bad.jar");
        Files.writeString(malformed, "not a zip");
        var analysis = run("analyze", malformed.toString());
        assertEquals(3, analysis.code(), analysis.errors());
        assertTrue(analysis.errors().contains("Operation failed ["));
        assertEquals("", analysis.json());
        var missing = run("mappings", malformed.toString(), "--mapping-bundle", directory.resolve("absent").toString());
        assertEquals(3, missing.code(), missing.errors());
        assertEquals("", missing.json());
    }

    @Test void parserRejectsAmbiguousSourcesAndDevelopmentCacheOutsideExplicitPreparation() {
        for (String[] args : List.of(
                new String[]{"mappings", "input.jar", "--mapping-profile", "p", "--mapping-bundle", "b"},
                new String[]{"convert", "input.jar", "--output", "out.jar", "--from-development-cache"},
                new String[]{"analyze", "input.jar", "--mapping-bundle", "b"},
                new String[]{"prepare-mappings", "--output", "b"},
                new String[]{"prepare-mappings", "--output", "b", "--mapping-profile", "p", "--from-development-cache"},
                new String[]{"prepare-mappings", "--from-development-cache"},
                new String[]{"prepare-mappings", "--output", "b", "--from-development-cache", "--overwrite"},
                new String[]{"mappings", "input.jar", "--mapping-bundle", "--report", "x"})) {
            assertEquals(2, run(args).code(), String.join(" ", args));
        }
        var explicit = CliArguments.parse(new String[]{"prepare-mappings", "--output", "new", "--from-development-cache"});
        assertTrue(explicit.fromDevelopmentCache());
        assertNull(explicit.mappingProfile());
    }

    private Result run(String... args) { return run(Map.of(), args); }
    private Result run(Map<String, String> environment, String... args) {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        int code = LegacyBridgeCli.run(args, new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8), environment);
        return new Result(code, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }
    private static Map<?, ?> json(String value) throws Exception { return (Map<?, ?>) BoundedJsonParser.parse(value); }
    private record Result(int code, String json, String errors) { }
}
