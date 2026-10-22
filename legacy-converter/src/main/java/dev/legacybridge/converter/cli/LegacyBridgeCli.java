package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.mapping.MappingReport;
import dev.legacybridge.converter.mapping.portable.PortableMappingBundle;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.report.CompatibilityReport;
import dev.legacybridge.converter.report.Severity;
import dev.legacybridge.converter.service.ConversionRequest;
import dev.legacybridge.converter.service.EngineOutcome;
import dev.legacybridge.converter.service.LegacyEngineService;
import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.converter.service.MappingPreparationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** Standalone presentation adapter for the reusable asynchronous engine. Never executes input code. */
public final class LegacyBridgeCli {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyBridgeCli.class);
    static final String MAPPING_BUNDLE_ENV = "LEGACYBRIDGE_MAPPING_BUNDLE";

    private LegacyBridgeCli() { }

    /** Process termination belongs only to this standalone launcher, never to the engine or game UI. */
    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) System.exit(exitCode);
    }

    static int run(String[] args, PrintStream stdout, PrintStream stderr) {
        return run(args, stdout, stderr, System.getenv());
    }

    static int run(String[] args, PrintStream stdout, PrintStream stderr, Map<String, String> environment) {
        if (args.length == 0 || (args.length == 1 && isHelp(args[0]))) {
            printHelp(stdout);
            return ExitCode.ANALYSIS_COMPLETE.value();
        }
        CliArguments arguments;
        if (args[0].equals("inspect-data")) return MappingDataInspectionCli.run(args, stdout, stderr);
        if (args[0].equals("inspect-iron-sword")) return IronSwordInspectionCli.run(args, stdout, stderr);
        if (args[0].equals("inspect-iron-helmet")) return IronHelmetInspectionCli.run(args, stdout, stderr);
        if (args[0].equals("inspect-preserved")) return PreservedArtifactInspectionCli.run(args, stdout, stderr);
        if (args[0].equals("inspect-cohort") || args[0].equals("plan-cohort")) return CohortInspectionCli.run(args, stdout, stderr);
        MappingData mappingData;
        try {
            arguments = CliArguments.parse(args);
            if (arguments.input() != null && !arguments.reportToStdout()
                    && samePath(arguments.input(), arguments.reportPath())) {
                throw new IllegalArgumentException("The report path must differ from the input JAR");
            }
            mappingData = mappingData(arguments, environment);
        } catch (IllegalArgumentException exception) {
            stderr.println("Invalid arguments: " + exception.getMessage());
            stderr.println("Run with --help for usage.");
            return ExitCode.INVALID_USAGE.value();
        }

        try (LegacyEngineService engine = new LegacyEngineService()) {
            return switch (arguments.command()) {
                case "convert" -> runConversion(engine, arguments, mappingData, stdout, stderr);
                case "mappings" -> runMappings(engine, arguments, mappingData, stdout, stderr);
                case "prepare-mappings" -> runPreparation(engine, arguments, stdout, stderr);
                default -> runAnalysis(engine, arguments, stdout, stderr);
            };
        } catch (RuntimeException exception) {
            LOGGER.error("Unexpected operation failure", exception);
            stderr.println("Unexpected operation failure; inspect the diagnostic log before using any output.");
            return ExitCode.INTERNAL_ERROR.value();
        }
    }

    private static MappingData mappingData(CliArguments arguments, Map<String, String> environment) {
        if (!"convert".equals(arguments.command()) && !"mappings".equals(arguments.command())) return MappingData.unavailable();
        if (arguments.mappingProfile() != null) return MappingData.profile(arguments.mappingProfile());
        if (arguments.mappingBundle() != null) return arguments.mappingSha256() == null ? MappingData.bundle(arguments.mappingBundle())
                : MappingData.reviewedBundle(arguments.mappingBundle(), arguments.mappingSha256());
        String configured = environment.get(MAPPING_BUNDLE_ENV);
        if (configured != null && !configured.isBlank()) return MappingData.bundle(Path.of(configured));
        return "convert".equals(arguments.command())
                && arguments.conversionProfile() == dev.legacybridge.converter.service.ConversionProfile.SIMPLE_CONTENT_V1
                ? MappingData.builtinSimpleContent() : MappingData.unavailable();
    }

    private static int runAnalysis(LegacyEngineService engine, CliArguments arguments, PrintStream stdout, PrintStream stderr) {
        var outcome = engine.analyze(arguments.input()).completion().toCompletableFuture().join();
        if (outcome.value().isEmpty()) return operationFailure(outcome, stderr);
        CompatibilityReport report = outcome.value().orElseThrow();
        CanonicalReportWriter writer = new CanonicalReportWriter();
        if (arguments.reportToStdout()) {
            stdout.print(writer.toJson(report));
            if (stdout.checkError()) {
                stderr.println("Could not write the compatibility report to stdout.");
                return ExitCode.REPORT_WRITE_FAILED.value();
            }
        } else {
            try {
                writer.writeAtomically(arguments.reportPath(), report, arguments.overwrite(), arguments.input());
            } catch (IOException exception) {
                stderr.println("Could not write compatibility report: " + reportWriteReason(exception));
                return ExitCode.REPORT_WRITE_FAILED.value();
            }
        }
        LOGGER.info("Analysis complete for {} (sourceProfile={}, conversionStatus={})", report.input().fileName(),
                report.assessment().sourceProfile(), report.assessment().conversionStatus());
        return report.hasAtLeast(Severity.ERROR) ? ExitCode.FINDINGS_REQUIRE_REVIEW.value() : ExitCode.ANALYSIS_COMPLETE.value();
    }

    private static int runConversion(LegacyEngineService engine, CliArguments arguments, MappingData mappings,
                                     PrintStream stdout, PrintStream stderr) {
        try {
            preflightConversionReport(arguments);
            var outcome = engine.convert(new ConversionRequest(arguments.input(), arguments.output(), mappings,
                    Optional.empty(), arguments.conversionProfile()))
                    .completion().toCompletableFuture().join();
            if (outcome.value().isEmpty()) return operationFailure(outcome, stderr);
            var result = outcome.value().orElseThrow();
            CanonicalReportWriter writer = new CanonicalReportWriter();
            String json = writer.toJsonTree(result.report());
            if (arguments.reportToStdout()) {
                stdout.print(json);
                if (stdout.checkError()) return ExitCode.REPORT_WRITE_FAILED.value();
            } else {
                try {
                    writer.writeJsonAtomically(arguments.reportPath(), json, arguments.overwrite(), arguments.input());
                } catch (IOException error) {
                    stderr.println("Could not write conversion report: " + reportWriteReason(error));
                    return ExitCode.REPORT_WRITE_FAILED.value();
                }
            }
            LOGGER.info("Subset conversion {} for {}", result.converted() ? "completed" : "ABORTED", arguments.input().getFileName());
            return result.converted() ? ExitCode.ANALYSIS_COMPLETE.value() : ExitCode.FINDINGS_REQUIRE_REVIEW.value();
        } catch (IOException error) {
            stderr.println("Conversion I/O refused: " + error.getMessage() + ". Converted output is never overwritten.");
            return ExitCode.INPUT_REJECTED.value();
        }
    }

    private static void preflightConversionReport(CliArguments arguments) throws IOException {
        if (arguments.reportToStdout()) return;
        Path report = arguments.reportPath().toAbsolutePath().normalize();
        Path output = arguments.output().toAbsolutePath().normalize();
        if (Files.isDirectory(report.getParent())) report = report.getParent().toRealPath().resolve(report.getFileName());
        if (Files.isDirectory(output.getParent())) output = output.getParent().toRealPath().resolve(output.getFileName());
        if (samePath(report, output) || (Files.exists(report) && Files.exists(output) && Files.isSameFile(report, output))) {
            throw new IOException("Report must differ from output JAR");
        }
        if (Files.isSymbolicLink(report) || (Files.exists(report) && Files.isSameFile(report, arguments.input()))) {
            throw new IOException("Report aliases input or is a symbolic link");
        }
        if (!Files.isDirectory(report.getParent())) throw new IOException("Report parent directory does not exist");
        if (Files.exists(report) && !arguments.overwrite()) throw new FileAlreadyExistsException(report.toString());
    }

    private static int runMappings(LegacyEngineService engine, CliArguments arguments, MappingData mappings,
                                   PrintStream stdout, PrintStream stderr) {
        var outcome = engine.mappings(arguments.input(), mappings).completion().toCompletableFuture().join();
        if (outcome.value().isEmpty()) return operationFailure(outcome, stderr);
        MappingReport report = outcome.value().orElseThrow();
        CanonicalReportWriter writer = new CanonicalReportWriter();
        String json = writer.toJsonTree(report.toTree());
        if (arguments.reportToStdout()) {
            stdout.print(json);
            if (stdout.checkError()) {
                stderr.println("Could not write the mapping report to stdout.");
                return ExitCode.REPORT_WRITE_FAILED.value();
            }
        } else {
            try {
                writer.writeJsonAtomically(arguments.reportPath(), json, arguments.overwrite(), arguments.input());
            } catch (IOException exception) {
                stderr.println("Could not write mapping report: " + reportWriteReason(exception));
                return ExitCode.REPORT_WRITE_FAILED.value();
            }
        }
        LOGGER.info("Mappings inspected for {} (symbols={}, requiresReview={})", report.input().fileName(), report.entries().size(), report.requiresReview());
        return report.requiresReview() ? ExitCode.FINDINGS_REQUIRE_REVIEW.value() : ExitCode.ANALYSIS_COMPLETE.value();
    }

    private static int runPreparation(LegacyEngineService engine, CliArguments arguments, PrintStream stdout, PrintStream stderr) {
        var request = new MappingPreparationRequest(arguments.output(), Optional.ofNullable(arguments.mappingProfile()), arguments.fromDevelopmentCache());
        var outcome = engine.prepareMappings(request).completion().toCompletableFuture().join();
        if (outcome.value().isEmpty()) return operationFailure(outcome, stderr);
        String receipt = new CanonicalReportWriter().toJsonTree(new java.util.TreeMap<>(Map.of(
                "schemaVersion", 1,
                "reportType", "LEGACY_MAPPING_PREPARATION",
                "status", "PREPARED",
                "bundleDirectory", outcome.value().orElseThrow().toAbsolutePath().normalize().toString(),
                "distributionPolicy", PortableMappingBundle.DISTRIBUTION_POLICY)));
        stdout.print(receipt);
        stderr.println("Mapping data prepared for local use only. Redistribution is not approved; consult the bundle provenance and licensing notice. No network downloads were performed.");
        if (stdout.checkError()) {
            stderr.println("The mapping bundle was prepared, but its receipt could not be written to stdout.");
            return ExitCode.REPORT_WRITE_FAILED.value();
        }
        return ExitCode.ANALYSIS_COMPLETE.value();
    }

    private static int operationFailure(EngineOutcome<?> outcome, PrintStream stderr) {
        if (outcome.error().isPresent()) {
            var error = outcome.error().orElseThrow();
            stderr.println("Operation failed [" + error.code() + "]: " + error.message());
            if (!error.remediation().isBlank()) stderr.println("Next step: " + error.remediation());
            if ("INTERNAL_ERROR".equals(error.code())) return ExitCode.INTERNAL_ERROR.value();
        } else {
            stderr.println("Operation did not produce a result (" + outcome.status() + ").");
        }
        return "CANCELLED".equals(outcome.status().name())
                ? ExitCode.FINDINGS_REQUIRE_REVIEW.value() : ExitCode.INPUT_REJECTED.value();
    }

    private static boolean samePath(Path first, Path second) {
        return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize());
    }

    private static String reportWriteReason(IOException exception) {
        if (exception instanceof FileAlreadyExistsException) return "target already exists; use --overwrite to replace it";
        String message = exception.getMessage();
        if ("Report parent directory does not exist".equals(message) || "Report target resolves to the input JAR".equals(message)
                || "Report target must not be a symbolic link".equals(message) || "Report target aliases the input JAR".equals(message)) return message;
        return exception.getClass().getSimpleName();
    }

    private static boolean isHelp(String argument) {
        return "--help".equals(argument) || "-h".equals(argument) || "help".equals(argument);
    }

    private static void printHelp(PrintStream output) {
        output.println("LegacyBridge Converter (schema " + CompatibilityReport.SCHEMA_VERSION + ")");
        output.println("Offline analysis and strict simple-content-v1 conversion by default; input code is never executed.");
        output.println();
        output.println("Usage:");
        output.println("  legacy-converter inspect-data (--mapping-bundle <directory> | --builtin <simple-content-v1|cohort-preview-v1>)");
        output.println("  legacy-converter inspect-iron-sword <jar> --input-sha256 <hash> --mapping-profile <path>");
        output.println("  inspect-iron-sword reports inherited semantic blockers; never emits a candidate or changes profiles.");
        output.println("  legacy-converter inspect-iron-helmet <jar> --input-sha256 <hash> --mapping-profile <path>");
        output.println("  inspect-iron-helmet reports contextual construction/use blockers; never emits a candidate or changes profiles.");
        output.println("  legacy-converter inspect-preserved <original.jar> <candidate.jar> --original-sha256 <hash> --candidate-sha256 <hash> --mapping-bundle <directory> --mapping-sha256 <manifest-hash>");
        output.println("  inspect-preserved independently rechecks logic-preserving-v1 bytes; stdout JSON only, never Apply or runtime acceptance.");
        output.println("  mappings/convert accept --mapping-sha256 <reviewed-manifest-sha256> with explicit --mapping-bundle; changed data aborts without fallback.");
        output.println("  legacy-converter analyze <forge-1.16.5-mod.jar> [--report <report.json|->] [--overwrite]");
        output.println("  legacy-converter mappings <mod.jar> [--mapping-bundle <directory> | --mapping-profile <profile.properties>] [--report <report.json|->] [--overwrite]");
        output.println("  legacy-converter convert <mod.jar> --output <new.jar> [--conversion-profile <simple-content-v1|logic-preserving-v1|lifecycle-preserving-v1|food-item-v1|datapack-recipes-v1|network-snapshot-v1>] [--mapping-bundle <directory> | --mapping-profile <profile.properties>] [--report <report.json|->] [--overwrite]");
        output.println("  legacy-converter prepare-mappings --output <new-directory> (--mapping-profile <profile.properties> | --from-development-cache)");
        output.println("  legacy-converter inspect-cohort [--mapping-bundle <directory>] --artifact <id> <sha256> <jar> [--artifact <id> <sha256> <jar> ...]");
        output.println("  inspect-cohort checks source ownership/dependencies/linkage on BOTH sides; stdout JSON only, never conversion or installation.");
        output.println("  legacy-converter plan-cohort [--mapping-bundle <directory>] --artifact <id> <sha256> <jar> --artifact <id> <sha256> <jar>");
        output.println("  plan-cohort builds/reopens the exact authored pair privately; stdout proof only, no output JAR or Apply authority.");
        output.println("  Cohort commands default to included exact-pair data. Explicit bundle errors never fall back; general mappings/other previews still require full data.");
        output.println("  convert aborts outside its finite registration/resource grammar; --overwrite applies only to reports.");
        output.println("  --conversion-profile logic-preserving-v1 explicitly selects the experimental item-foil/owned-logic profile; unknown external contracts abort.");
        output.println("  --conversion-profile lifecycle-preserving-v1 selects the separate finite COMMON setup/config/server-command fixture slices; not general lifecycle or client support.");
        output.println("  --conversion-profile food-item-v1 selects literal native food only; no effects, remainder, overrides, tools or blocks; target eating GameEvent semantics are an explicit migration.");
        output.println("  --conversion-profile network-snapshot-v1 selects exact reviewed authored S2C snapshot method recipes only; no server mutations, general SimpleChannel support or cross-version connections.");
        output.println("  --conversion-profile datapack-recipes-v1 adds four vanilla cooking formats and stonecutting to the bootstrap grammar; local tags only, no conditions/custom codecs. Static success is not reload or gameplay validation.");
        output.println("  Mapping statuses: RESOLVED, ADAPTER_REQUIRED, MANUAL, UNSUPPORTED.");
        output.println("  simple-content-v1 conversion uses included finite contracts when no mapping source is configured.");
        output.println("  Broad mappings inspection and other profiles require --mapping-bundle, --mapping-profile, or LEGACYBRIDGE_MAPPING_BUNDLE.");
        output.println("  Explicit data overrides the built-in contract; invalid selected data never silently falls back.");
        output.println("  No implicit Gradle cache lookup or network downloads. --from-development-cache is preparation-only.");
        output.println("  Prepared mapping data is local-use only, not approved for redistribution.");
        output.println("  Without --report, canonical JSON is written to stdout.");
        output.println();
        output.println("Exit 0 means the requested operation completed; conversion requires the matching legacy-runtime.");
        output.println("Analysis and RESOLVED mappings never independently authorize conversion or prove runtime behavior.");
    }
}
