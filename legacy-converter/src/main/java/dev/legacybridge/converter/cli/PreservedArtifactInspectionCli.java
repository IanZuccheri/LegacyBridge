package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;

/** Explicit developer readback; stdout is presentation, not a channel used by the Minecraft UI. */
final class PreservedArtifactInspectionCli {
    private PreservedArtifactInspectionCli() { }
    static int run(String[] args, PrintStream out, PrintStream err) {
        PreservedArtifactRequest request;
        try {
            if(args.length != 11) throw new IllegalArgumentException("inspect-preserved <original.jar> <candidate.jar> --original-sha256 <hash> --candidate-sha256 <hash> --mapping-bundle <directory> --mapping-sha256 <manifest-hash>");
            var options = new HashMap<String,String>();
            for(int i=3;i<args.length;i+=2) if(args[i+1].isBlank() || options.putIfAbsent(args[i],args[i+1]) != null)
                throw new IllegalArgumentException("Duplicate or blank option");
            if(args[1].isBlank() || args[2].isBlank() || !options.keySet().equals(Set.of("--original-sha256","--candidate-sha256","--mapping-bundle","--mapping-sha256")))
                throw new IllegalArgumentException("Explicit original, candidate and reviewed local bundle required; no environment/cache/download fallback");
            request = new PreservedArtifactRequest(Path.of(args[1]),options.get("--original-sha256"),Path.of(args[2]),options.get("--candidate-sha256"),
                    MappingData.reviewedBundle(Path.of(options.get("--mapping-bundle")),options.get("--mapping-sha256")));
        } catch(IllegalArgumentException invalid) { err.println(invalid.getMessage()); return ExitCode.INVALID_USAGE.value(); }
        try(var engine = new LegacyEngineService()) {
            var outcome = engine.inspectPreservedArtifact(request).completion().toCompletableFuture().join();
            if(outcome.value().isEmpty()) {
                var e=outcome.error().orElseThrow(); err.println("["+e.code()+"] "+e.message()+" "+e.remediation());
                return ExitCode.INPUT_REJECTED.value();
            }
            out.print(new CanonicalReportWriter().toJsonTree(outcome.value().orElseThrow().toTree()));
            if(out.checkError()) return ExitCode.REPORT_WRITE_FAILED.value();
            return outcome.value().orElseThrow().validated() ? ExitCode.ANALYSIS_COMPLETE.value() : ExitCode.INPUT_REJECTED.value();
        }
    }
}
