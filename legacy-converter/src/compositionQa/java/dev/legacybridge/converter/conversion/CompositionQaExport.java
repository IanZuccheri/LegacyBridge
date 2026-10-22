package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.archive.InputRejectedException;
import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.CompositionCandidate;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Developer source set only. Re-proves a selected candidate; never invokes CLI.main or loads original classes. */
public final class CompositionQaExport {
    private CompositionQaExport() { }
    public record Result(Optional<Path> directory, CompositionCandidate proof) {
        public boolean exported() { return directory.isPresent(); }
        public Map<String, Object> toTree() {
            return Map.of("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_QA_EXPORT_RESULT",
                    "status", exported() ? "EXPORTED_FOR_QA" : "BLOCKED", "installationAllowed", false,
                    "runtimeValidation", "NOT_RUN", "directory", directory.map(Path::toString).orElse(""),
                    "proof", proof.report());
        }
    }

    public static Result export(Path input, String sourceSha256, String candidateSha256,
                                MappingInputs mappings, Path qaRoot, String runId) throws IOException, InputRejectedException {
        return export(input, sourceSha256, candidateSha256, mappings, qaRoot, runId, CompositionQaFiles::write);
    }

    static Result export(Path input, String sourceSha256, String candidateSha256, MappingInputs mappings,
                         Path qaRoot, String runId, CompositionQaFiles.Writer writer) throws IOException, InputRejectedException {
        CompositionQaFiles.requireHash(sourceSha256); CompositionQaFiles.requireHash(candidateSha256);
        CompositionQaFiles.preflightRoot(qaRoot, runId);
        CompositionQaFiles.requireInput(input, sourceSha256);
        // The ordinary service still returns hash/proof only. The borrow exists solely on this trusted dev classpath.
        Path[] exported = {null};
        var proof = LogicPreservingConverter.inspectLogicWithRecipesCandidate(input, qaRoot.resolve("candidate.jar"), mappings,
                (bytes, accepted) -> {
                    if (!sourceSha256.equals(accepted.inputSha256()) || !candidateSha256.equals(accepted.candidateSha256().orElse(null)))
                        throw new IOException("QA_SELECTION_CHANGED: freshly proved source/candidate differs from explicit selection");
                    exported[0] = CompositionQaFiles.publish(input, sourceSha256, candidateSha256, qaRoot, runId,
                            bytes, accepted.report(), writer);
                });
        return new Result(Optional.ofNullable(exported[0]), proof);
    }

    /** Called only by the opt-in Gradle task, with explicit paths and both independently reviewed hashes. */
    public static void main(String[] args) throws Exception {
        if (args.length != 6) throw new IllegalArgumentException("input source-sha256 candidate-sha256 mapping-profile qa-root run-id required");
        var control = new OperationControl(OperationLimits.defaults(), progress -> { });
        try (var scope = control.enter()) {
            Path root = Path.of(args[4]);
            CompositionQaFiles.ensureRoot(root, args[5]);
            Result result = export(Path.of(args[0]), args[1], args[2], new MappingInputsLoader().load(Path.of(args[3])), root, args[5]);
            control.complete();
            System.out.println(new CanonicalReportWriter().toJsonTree(result.toTree()));
            if (!result.exported()) throw new IOException("QA_BLOCKED: see structured proof diagnostics; no candidate exported");
        }
    }
}
