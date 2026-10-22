package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.CohortInspectionRequest;
import dev.legacybridge.converter.service.CohortPlanningRequest;
import dev.legacybridge.converter.service.LegacyEngineService;
import dev.legacybridge.converter.service.MappingData;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;

/** Small presentation adapter, stdout-only to avoid multi-input report overwrite authority. */
final class CohortInspectionCli {
    static int run(String[] args, PrintStream out, PrintStream err) {
        CohortInspectionRequest request;
        try {
            if (args.length > 21) throw new IllegalArgumentException("At most four artifacts");
            var artifacts = new ArrayList<CohortInspectionRequest.Artifact>();
            MappingData data = null;
            for (int i = 1; i < args.length; i++) {
                if (args[i].equals("--mapping-bundle") && data == null && i + 1 < args.length) {
                    data = MappingData.bundle(Path.of(args[++i]));
                } else if (args[i].equals("--artifact") && i + 3 < args.length) {
                    String id = args[++i], sha = args[++i];
                    artifacts.add(new CohortInspectionRequest.Artifact(id, Path.of(args[++i]), sha));
                } else throw new IllegalArgumentException("Expected --mapping-bundle <directory> and --artifact <id> <sha256> <jar>; stdout only");
            }
            if (data == null) data = MappingData.builtinCohortPreview();
            request = new CohortInspectionRequest(artifacts, data);
        } catch (IllegalArgumentException invalid) {
            err.println("Invalid cohort arguments: " + invalid.getMessage()); return ExitCode.INVALID_USAGE.value();
        }
        try (var engine = new LegacyEngineService()) {
            if (args[0].equals("plan-cohort")) {
                var result = engine.planCohort(new CohortPlanningRequest(request)).completion().toCompletableFuture().join();
                if (result.value().isEmpty()) {
                    result.error().ifPresent(error -> err.println(error.code() + ": " + error.message()));
                    return ExitCode.INPUT_REJECTED.value();
                }
                var candidate = result.value().orElseThrow();
                out.print(new CanonicalReportWriter().toJsonTree(candidate.toTree()));
                if (out.checkError()) return ExitCode.REPORT_WRITE_FAILED.value();
                return candidate.validated() ? 0 : ExitCode.FINDINGS_REQUIRE_REVIEW.value();
            }
            var result = engine.inspectCohort(request).completion().toCompletableFuture().join();
            if (result.value().isEmpty()) {
                result.error().ifPresent(error -> err.println(error.code() + ": " + error.message()));
                return ExitCode.INPUT_REJECTED.value();
            }
            var inspection = result.value().orElseThrow();
            out.print(new CanonicalReportWriter().toJsonTree(inspection.toTree()));
            if (out.checkError()) return ExitCode.REPORT_WRITE_FAILED.value();
            return inspection.sourceLinkagePassed() ? 0 : ExitCode.FINDINGS_REQUIRE_REVIEW.value();
        }
    }
}
