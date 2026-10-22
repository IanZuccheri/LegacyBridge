package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;

/** Developer-only diagnostic. Explicit data, no environment/cache discovery, output publication, conversion or Apply. */
final class IronHelmetInspectionCli {
    private IronHelmetInspectionCli() { }
    static int run(String[] args, PrintStream out, PrintStream err) {
        IronHelmetInspectionRequest request;
        try {
            if (args.length != 6) throw new IllegalArgumentException("inspect-iron-helmet <jar> --input-sha256 <hash> --mapping-profile <path>");
            var options = new HashMap<String,String>();
            for (int i=2;i<args.length;i+=2) if (args[i+1].isBlank() || options.putIfAbsent(args[i],args[i+1]) != null)
                throw new IllegalArgumentException("Duplicate or blank option");
            if (args[1].isBlank() || !options.keySet().equals(Set.of("--input-sha256","--mapping-profile")))
                throw new IllegalArgumentException("Select the exact original and full local mapping profile explicitly");
            request = new IronHelmetInspectionRequest(Path.of(args[1]),options.get("--input-sha256"),MappingData.profile(Path.of(options.get("--mapping-profile"))));
        } catch (IllegalArgumentException invalid) { err.println(invalid.getMessage()); return ExitCode.INVALID_USAGE.value(); }
        try (var service = new LegacyEngineService()) {
            var outcome = service.inspectIronHelmet(request).completion().toCompletableFuture().join();
            if (outcome.value().isEmpty()) {
                var e = outcome.error().orElseThrow(); err.println("["+e.code()+"] "+e.message()+" "+e.remediation());
                return ExitCode.INPUT_REJECTED.value();
            }
            out.print(new CanonicalReportWriter().toJsonTree(outcome.value().orElseThrow().toTree()));
            return out.checkError() ? ExitCode.REPORT_WRITE_FAILED.value() : ExitCode.INPUT_REJECTED.value();
        }
    }
}
