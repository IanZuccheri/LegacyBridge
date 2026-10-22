package dev.legacybridge.product.install;

import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.converter.service.PreservedArtifactRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/** Explicit disposable helper, not packaged or used by the product; never launches a game/mod class. */
public final class PreservedInstallRecoveryProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 6 && args.length != 11) throw new IllegalArgumentException(
                "apply-stop|resume|restore-stop|restore game bundle dataSHA sourceSHA targetSHA [admissionId admissionSHA approvalId installId installSHA]");
        Path product = Path.of(PreservedAddOnlyInstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!product.getFileName().toString().equals("LegacyBridge.jar")) throw new IllegalStateException("Actual public JAR required");
        System.out.println("INSTALLER_SOURCE=" + product);
        Path game = Path.of(args[1]).toRealPath();
        var data = MappingData.reviewedBundle(Path.of(args[2]), args[3]);
        Path original = game.resolve("legacybridge/input/original.jar"), staged = game.resolve("legacybridge/staging/candidate.jar");
        if (!AddOnlyInstaller.hash(original).equals(args[4]) || !AddOnlyInstaller.hash(staged).equals(args[5]))
            throw new IllegalStateException("Fixture hash mismatch");
        Path runtime = game.resolve("mods/LegacyBridge.jar");
        if (!AddOnlyInstaller.hash(runtime).equals(AddOnlyInstaller.hash(product))) throw new IllegalStateException("Next-launch public runtime mismatch");
        var session = new InstallSession(Set.of("minecraft", "forge", "legacybridge_runtime"), Set.of(runtime), false);
        if (args[0].equals("apply-stop") && args.length == 6) {
            try (var paths = Files.list(game.resolve("mods"))) {
                if (paths.count() != 1) throw new IllegalStateException("Disposable initial mods must contain only public runtime");
            }
            var admission = new PreservedAdmissionStore().prepare(game, new PreservedArtifactRequest(original, args[4], staged, args[5], data));
            if (admission.error().isPresent()) throw new IllegalStateException(admission.toString());
            var approval = new PreservedInstallApproval(admission.transaction().orElseThrow(), admission.planSha256().orElseThrow(), UUID.randomUUID());
            System.out.println("ADMISSION_ID=" + approval.admission().getFileName());
            System.out.println("ADMISSION_SHA256=" + approval.admissionSha256());
            System.out.println("APPROVAL_ID=" + approval.approvalId());
            var result = new PreservedAddOnlyInstaller((point, tx) -> {
                if (point == PreservedAddOnlyInstaller.Boundary.PUBLISHED) {
                    System.out.println("INSTALL_ID=" + tx.getFileName());
                    System.out.println("INSTALL_SHA256=" + AddOnlyInstaller.hash(tx.resolve(PreservedInstallJournal.PLAN)));
                    System.out.flush(); Runtime.getRuntime().halt(81); // Only this explicitly launched helper.
                }
            }).apply(game, approval, data, session);
            throw new IllegalStateException("Expected controlled halt at PUBLISHED, got " + result);
        }
        if (args.length != 11 || !Set.of("resume", "restore-stop", "restore").contains(args[0])) throw new IllegalArgumentException("Unknown action");
        var approval = new PreservedInstallApproval(game.resolve("legacybridge/transactions").resolve(args[6]), args[7], UUID.fromString(args[8]));
        Path tx = game.resolve("legacybridge/transactions").resolve(args[9]);
        var installer = new PreservedAddOnlyInstaller((point, owned) -> {
            if (args[0].equals("restore-stop") && point == PreservedAddOnlyInstaller.Boundary.REMOVED) {
                System.out.println("RESTORE_REMOVED=" + owned.getFileName());
                System.out.flush(); Runtime.getRuntime().halt(82);
            }
        });
        var result = installer.recover(game, tx, args[10], approval, data, session,
                args[0].equals("resume") ? RecoveryAction.RESUME : RecoveryAction.RESTORE);
        var expected = args[0].equals("resume") ? PreservedInstallResult.State.COMMITTED : PreservedInstallResult.State.RESTORED;
        if (args[0].equals("restore-stop") || result.state() != expected || !result.safeToClose()) throw new IllegalStateException(result.toString());
        if (!AddOnlyInstaller.hash(original).equals(args[4]) || !AddOnlyInstaller.hash(staged).equals(args[5]))
            throw new IllegalStateException("Original or staging changed");
        try (var paths = Files.list(game.resolve("mods"))) {
            if (paths.count() != (expected == PreservedInstallResult.State.COMMITTED ? 2 : 1)) throw new IllegalStateException("Unexpected installed set");
        }
        System.out.println("PRESERVED_INSTALL_RECOVERY_OK state=" + result.state() + " originalIntact=true activation=NOT_RUN");
    }
}
