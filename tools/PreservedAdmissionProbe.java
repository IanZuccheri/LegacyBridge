import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.LegacyEngineService;
import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.converter.service.PreservedArtifactRequest;
import java.nio.file.Path;

/** Developer-only source launcher. Never packaged; never defines an original/candidate mod class. */
public final class PreservedAdmissionProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 6) throw new IllegalArgumentException(
                "original original-sha256 candidate candidate-sha256 bundle bundle-manifest-sha256");
        var engineLocation = LegacyEngineService.class.getProtectionDomain().getCodeSource().getLocation();
        if (!Path.of(engineLocation.toURI()).getFileName().toString().equals("LegacyBridge.jar"))
            throw new IllegalStateException("Probe requires the actual public-JAR engine: " + engineLocation);
        System.err.println("ENGINE_SOURCE=" + engineLocation);
        var request = new PreservedArtifactRequest(Path.of(args[0]), args[1], Path.of(args[2]), args[3],
                MappingData.reviewedBundle(Path.of(args[4]), args[5]));
        try (var service = new LegacyEngineService()) {
            var outcome = service.inspectPreservedArtifact(request).completion().toCompletableFuture().join();
            if (outcome.value().isEmpty()) throw new IllegalStateException(outcome.error().toString());
            var inspection = outcome.value().orElseThrow();
            System.out.print(new CanonicalReportWriter().toJsonTree(inspection.toTree()));
            if (!inspection.validated()) throw new IllegalStateException("Inspection refused: " + inspection.blockers());
            if (System.out.checkError()) throw new IllegalStateException("Report output failed");
        }
    }
}
