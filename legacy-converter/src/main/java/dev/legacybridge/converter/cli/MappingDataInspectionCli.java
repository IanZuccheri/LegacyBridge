package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import java.io.PrintStream;
import java.nio.file.Path;

/** Presentation only. Data review is read-only, canonical stdout; no overwrite/output/mod execution. */
final class MappingDataInspectionCli {
    private MappingDataInspectionCli() { }
    static int run(String[] args, PrintStream out, PrintStream err) {
        final MappingData data;
        try {
            if (args.length != 3) throw new IllegalArgumentException("inspect-data (--mapping-bundle <directory> | --builtin <simple-content-v1|cohort-preview-v1>)");
            data = switch (args[1]) {
                case "--mapping-bundle" -> MappingData.bundle(Path.of(args[2]));
                case "--builtin" -> switch (args[2]) {
                    case "simple-content-v1" -> MappingData.builtinSimpleContent();
                    case "cohort-preview-v1" -> MappingData.builtinCohortPreview();
                    default -> throw new IllegalArgumentException("Unknown included data scope");
                };
                default -> throw new IllegalArgumentException("Select local data explicitly; no environment/cache/download fallback");
            };
            if (args[2].isBlank()) throw new IllegalArgumentException("Data selection is blank");
        } catch (IllegalArgumentException invalid) { err.println(invalid.getMessage()); return ExitCode.INVALID_USAGE.value(); }
        try (var engine = new LegacyEngineService()) {
            var outcome = engine.inspectMappingData(data).completion().toCompletableFuture().join();
            if (outcome.value().isEmpty()) {
                var error = outcome.error().orElseThrow(); err.println("[" + error.code() + "] " + error.message() + " " + error.remediation());
                return ExitCode.INPUT_REJECTED.value();
            }
            out.print(new CanonicalReportWriter().toJsonTree(outcome.value().orElseThrow().toTree()));
            return out.checkError() ? ExitCode.REPORT_WRITE_FAILED.value() : ExitCode.ANALYSIS_COMPLETE.value();
        }
    }
}
