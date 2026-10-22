package dev.legacybridge.product.install;

import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.converter.service.PreservedArtifactRequest;
import java.nio.file.Files;
import java.nio.file.Path;

/** Developer-only child JVM, compiled separately against the public JAR. Never packaged or run by the product. */
public final class PreservedAdmissionRecoveryProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 6 && args.length != 8) throw new IllegalArgumentException(
                "prepare-stop|resume gameDir bundle manifest-sha original-sha candidate-sha [transaction plan-sha]");
        var location = PreservedAdmissionStore.class.getProtectionDomain().getCodeSource().getLocation();
        if (!Path.of(location.toURI()).getFileName().toString().equals("LegacyBridge.jar"))
            throw new IllegalStateException("Actual public-JAR store required: " + location);
        System.out.println("STORE_SOURCE=" + location);
        Path game = Path.of(args[1]).toRealPath();
        var data = MappingData.reviewedBundle(Path.of(args[2]), args[3]);
        Path original = game.resolve("legacybridge/input/original.jar"), staged = game.resolve("legacybridge/staging/candidate.jar");
        if (!AddOnlyInstaller.hash(original).equals(args[4]) || !AddOnlyInstaller.hash(staged).equals(args[5]))
            throw new IllegalStateException("Selected fixture hashes differ");
        try (var paths = Files.list(game.resolve("mods"))) {
            if (paths.findAny().isPresent()) throw new IllegalStateException("This disposable probe requires initially empty mods");
        }
        if (args[0].equals("prepare-stop") && args.length == 6) {
            var result = new PreservedAdmissionStore((boundary, transaction) -> {
                if (boundary == PreservedAdmissionStore.Boundary.BOUND) {
                    System.out.println("TRANSACTION=" + transaction);
                    System.out.println("PLAN_SHA256=" + AddOnlyInstaller.hash(transaction.resolve(PreservedAdmissionJournal.PLAN)));
                    System.out.flush();
                    Runtime.getRuntime().halt(81); // Controlled helper only: tests lost finally/guard cleanup.
                }
            }).prepare(game, new PreservedArtifactRequest(original, args[4], staged, args[5], data));
            throw new IllegalStateException("Expected controlled halt after complete binding, got " + result);
        }
        if (!args[0].equals("resume") || args.length != 8) throw new IllegalArgumentException("Unknown action/arguments");
        Path transaction = Path.of(args[6]);
        var result = new PreservedAdmissionStore().resume(game, transaction, args[7], data);
        if (result.state() != PreservedAdmissionResult.State.RECHECKED_NOT_INSTALLABLE
                || result.installationAllowed() || result.safeToClose()) throw new IllegalStateException(result.toString());
        if (!AddOnlyInstaller.hash(original).equals(args[4]) || !AddOnlyInstaller.hash(staged).equals(args[5]))
            throw new IllegalStateException("Original or staged fixture changed");
        try (var paths = Files.list(game.resolve("mods"))) {
            if (paths.findAny().isPresent()) throw new IllegalStateException("Admission wrote to mods");
        }
        System.out.println("ADMISSION_RECOVERY_OK classes=" + result.inspection().orElseThrow().classCount()
                + " methods=" + result.inspection().orElseThrow().methodCount() + " installationAllowed=false runtime=NOT_RUN");
    }
}
