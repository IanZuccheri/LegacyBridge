package dev.legacybridge.corpus;

import dev.legacybridge.converter.conversion.ConversionResult;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.report.CompatibilityReport;
import dev.legacybridge.converter.service.ConversionRequest;
import dev.legacybridge.converter.service.EngineJob;
import dev.legacybridge.converter.service.EngineOptions;
import dev.legacybridge.converter.service.EngineOutcome;
import dev.legacybridge.converter.service.LegacyEngineService;
import dev.legacybridge.converter.service.MappingData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Explicit, development-only static observation of one already-reviewed corpus record.
 * Reuses the engine API, never CLI.main, a mod classloader, a launcher or a downloader.
 * New observations do not overwrite the versioned record or inherit runtime evidence.
 */
public final class CorpusRunner {
    private static final long MAX_INPUT_BYTES = 64L << 20;
    private static final long MAX_HASH_BYTES = 256L << 20;

    private CorpusRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 5 || !args[0].equals("observe")) {
            throw new IllegalArgumentException("Usage: CorpusRunner observe <corpus-record.json> <original.jar> <explicit-mapping-bundle> <new-directory-under-build/corpus-runs>");
        }
        Map<String, Object> result = observe(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), Path.of(args[4]));
        System.out.print(CorpusJson.canonicalJson(result));
    }

    public static Map<String, Object> observe(Path recordFile, Path original, Path bundleDirectory,
                                               Path outputDirectory) throws Exception {
        Path recordPath = CorpusJson.regular(recordFile);
        Path corpus = CorpusJson.directory(recordPath.getParent().getParent());
        Path catalogPath = corpus.resolve("catalog.json");
        String recordHash = hash(recordPath);
        String catalogHash = hash(catalogPath);
        CorpusLock.verify(corpus);
        new CorpusVerifier().verify(corpus);
        Map<String, Object> catalog = CorpusJson.read(catalogPath);
        Path lockPath = CorpusVerifier.resolve(corpus, "snapshots/" + catalog.get("corpusVersion") + ".json", false);
        String lockHash = hash(lockPath);
        CorpusLock.verify(corpus);
        String relativeRecord = corpus.relativize(recordPath).toString().replace('\\', '/');
        if (!(catalog.get("records") instanceof List<?> records) || !records.contains(relativeRecord))
            throw new IOException("Record is not in the reviewed corpus catalog");
        Map<String, Object> record = CorpusJson.read(recordPath);
        if (!recordHash.equals(hash(recordPath)) || !catalogHash.equals(hash(catalogPath)))
            throw new IOException("Reviewed record or catalog changed during verification");
        Map<?, ?> identity = (Map<?, ?>) record.get("artifact");
        String expectedHash = (String) identity.get("sha256");
        Path input = CorpusJson.regular(original);
        long size = Files.size(input);
        if (size > MAX_INPUT_BYTES || size != ((Number) identity.get("sizeBytes")).longValue()
                || !expectedHash.equals(hash(input))) throw new IOException("Original does not match reviewed size/SHA-256 or exceeds 64 MiB");
        Path bundle = CorpusJson.directory(bundleDirectory);
        Path manifest = CorpusJson.regular(bundle.resolve("manifest.json"));
        String manifestHash = hash(manifest);
        Path project = corpus.getParent();
        Path output = outputDirectory.toAbsolutePath().normalize();
        Path allowed = project.resolve("build/corpus-runs");
        if (!output.startsWith(allowed) || output.equals(allowed))
            throw new IOException("Observations must stay in ignored build/corpus-runs, outside the public corpus");
        CorpusJson.directory(output.getParent());
        // CREATE_NEW semantics: no old observation, original, result or unrelated directory is replaced.
        Files.createDirectory(output);
        CorpusJson.directory(output);
        Map<String, Object> observation = new TreeMap<>();
        observation.put("schemaVersion", 1);
        observation.put("reportType", "LEGACYBRIDGE_CORPUS_STATIC_OBSERVATION");
        observation.put("corpusVersion", catalog.get("corpusVersion"));
        observation.put("recordId", record.get("id"));
        observation.put("recordSha256", recordHash);
        observation.put("catalogSha256", catalogHash);
        observation.put("snapshotLockSha256", lockHash);
        observation.put("sourceSha256", expectedHash);
        observation.put("mappingManifestSha256", manifestHash);
        observation.put("runtimeEvidenceInherited", false);
        observation.put("originalClassesExecuted", false);
        observation.put("networkAcquisition", false);
        Map<String, Object> proofs = new TreeMap<>();
        observation.put("reports", proofs);
        Map<String, Object> stages = new TreeMap<>();
        for (String stage : List.of("analyzable", "convertible", "bytecodeValidated", "clientInitializes",
                "serverInitializes", "clientBehavior", "serverBehavior", "multiplayer")) stages.put(stage, "NOT_RUN");
        observation.put("stages", stages);
        CanonicalReportWriter writer = new CanonicalReportWriter();
        EngineOptions defaults = EngineOptions.defaults();
        try (LegacyEngineService engine = new LegacyEngineService(new EngineOptions(1, 1, defaults.operationLimits()))) {
            EngineOutcome<CompatibilityReport> analysis = await(engine.analyze(input));
            observation.put("analysisOperationStatus", analysis.status().name());
            observation.put("analysisErrorCode", analysis.error().map(error -> error.code()).orElse(null));
            boolean complete = false;
            if (analysis.value().isPresent()) {
                CompatibilityReport report = analysis.value().get();
                if (!expectedHash.equals(report.input().sha256()) || report.input().sizeBytes() != size)
                    throw new IOException("Analysis fingerprint differs from the reviewed original");
                Path analysisFile = output.resolve("analysis.json");
                writer.writeAtomically(analysisFile, report, false, input);
                proofs.put("analysis", proof(analysisFile));
                complete = report.assessment().analysisStatus().name().equals("COMPLETE");
                stages.put("analyzable", complete ? "PASSED" :
                        report.assessment().analysisStatus().name().equals("REJECTED") ? "FAILED" : "PARTIAL");
                observation.put("classCount", report.analysis().classCount());
                observation.put("detectedFeatures", report.analysis().unsupportedFeatures().stream().map(feature -> feature.id()).toList());
                observation.put("analyzerConversionAssessmentIsNotConversionResult", true);
            } else stages.put("analyzable", "FAILED");
            if (complete) {
                Path jar = output.resolve("converted.jar");
                EngineOutcome<ConversionResult> conversion = await(engine.convert(
                        new ConversionRequest(input, jar, MappingData.bundle(bundle), Optional.of(expectedHash))));
                observation.put("conversionOperationStatus", conversion.status().name());
                observation.put("conversionErrorCode", conversion.error().map(error -> error.code()).orElse(null));
                if (conversion.value().isPresent()) {
                    ConversionResult result = conversion.value().get();
                    Path reportFile = output.resolve("conversion.json");
                    writer.writeJsonAtomically(reportFile, writer.toJsonTree(result.report()), false, input);
                    proofs.put("conversion", proof(reportFile));
                    observation.putAll(diagnosticSummary(result));
                    if (result.converted()) {
                        Object validation = result.report().get("bytecodeValidation");
                        if (!(validation instanceof Map<?, ?> checks) || !"PASSED".equals(checks.get("status")))
                            throw new IOException("Engine result lacks explicit bytecode-validation proof; no observation published");
                        Object outputIdentity = result.report().get("output");
                        if (!(outputIdentity instanceof Map<?, ?> fingerprint)
                                || !hash(jar).equals(fingerprint.get("sha256")))
                            throw new IOException("Converted file differs from the engine's validated output fingerprint");
                        stages.put("convertible", "PASSED");
                        stages.put("bytecodeValidated", "PASSED");
                        observation.put("outputSha256", fingerprint.get("sha256"));
                        observation.put("outputStatus", "CONVERTED");
                    } else {
                        requireNoOutput(jar);
                        stages.put("convertible", "BLOCKED");
                        observation.put("outputStatus", "BLOCKED");
                    }
                } else {
                    requireNoOutput(jar);
                    stages.put("convertible", conversion.status() == EngineOutcome.Status.BLOCKED ? "BLOCKED" : "FAILED");
                    observation.put("outputStatus", conversion.status().name());
                }
            } else observation.put("outputStatus", "NOT_RUN");
        }
        if (!expectedHash.equals(hash(input)) || Files.size(input) != size || !manifestHash.equals(hash(manifest))
                || !recordHash.equals(hash(recordPath)) || !catalogHash.equals(hash(catalogPath))
                || !lockHash.equals(hash(lockPath)))
            throw new IOException("Original, mapping manifest or reviewed record/catalog changed; observation is incomplete and must not be imported");
        for (Object value : proofs.values()) {
            Map<?, ?> proof = (Map<?, ?>) value;
            if (!proof.get("sha256").equals(hash(output.resolve((String) proof.get("fileName")))))
                throw new IOException("Raw report changed before the observation receipt was completed");
        }
        if (observation.get("outputSha256") != null
                && !observation.get("outputSha256").equals(hash(output.resolve("converted.jar"))))
            throw new IOException("Converted output changed before the observation receipt was completed");
        observation.put("originalHashUnchanged", true);
        observation.putIfAbsent("outputSha256", null);
        observation.put("scope", "Static engine observation only. No game startup, gameplay, installation or multiplayer test.");
        // This completion receipt is published last; an interrupted directory is not a completed observation.
        CorpusJson.writeCanonicalCreateNew(output.resolve("observation.json"), observation);
        return observation;
    }

    private static void requireNoOutput(Path jar) throws IOException {
        if (Files.exists(jar, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Refused operation unexpectedly has an artifact; no observation published");
    }

    static Map<String, Object> diagnosticSummary(ConversionResult result) {
        Object diagnostics = result.report().getOrDefault("diagnostics", List.of());
        // Successful conversion can contain scope warnings. They are not refusal blockers.
        return Map.of("conversionDiagnostics", diagnostics, "blockers", result.converted() ? List.of() : diagnostics);
    }

    private static Map<String, Object> proof(Path file) throws IOException {
        return Map.of("fileName", file.getFileName().toString(), "sha256", hash(file));
    }

    private static <T> EngineOutcome<T> await(EngineJob<T> job) throws Exception {
        try { return job.completion().toCompletableFuture().get(150, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { job.cancel(); Thread.currentThread().interrupt(); throw interrupted; }
        catch (TimeoutException | ExecutionException failed) { job.cancel(); throw failed; }
    }

    static String hash(Path file) throws IOException {
        Path path = CorpusJson.regular(file);
        if (Files.size(path) > MAX_HASH_BYTES) throw new IOException("Hash input exceeds 256 MiB");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = new byte[65536]; long total = 0;
                for (int length; (length = input.read(bytes)) != -1;) {
                    if ((total += length) > MAX_HASH_BYTES) throw new IOException("Hash input grew beyond limit");
                    digest.update(bytes, 0, length);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
