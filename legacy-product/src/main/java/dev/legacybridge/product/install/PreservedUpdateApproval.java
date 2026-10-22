package dev.legacybridge.product.install;

import java.nio.file.Path;
import java.util.Objects;

/** Explicit selection of a new admission and an owned, unloaded add-only predecessor. Not saved consent. */
public record PreservedUpdateApproval(PreservedInstallApproval replacement, Path previousInstallation, String previousPlanSha256) {
    public PreservedUpdateApproval {
        Objects.requireNonNull(replacement); Objects.requireNonNull(previousInstallation);
        if (!previousInstallation.isAbsolute() || !previousInstallation.equals(previousInstallation.normalize())
                || previousPlanSha256 == null || !previousPlanSha256.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Explicit canonical predecessor selection and plan SHA-256 required");
    }
}
