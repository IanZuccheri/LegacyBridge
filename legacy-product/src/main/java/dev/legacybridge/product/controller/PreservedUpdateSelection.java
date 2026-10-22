package dev.legacybridge.product.controller;

import java.nio.file.Path;
import java.util.Objects;

/** Explicit two-sided selection, never consent. Recovery in replacement refers to the update, not the predecessor. */
public record PreservedUpdateSelection(PreservedInstallSelection replacement, Path previousInstallation,
                                      String previousPlanSha256) {
    public PreservedUpdateSelection {
        Objects.requireNonNull(replacement);
        if (previousInstallation == null || !previousInstallation.isAbsolute()
                || !previousInstallation.equals(previousInstallation.normalize()) || previousInstallation.toString().length() > 4096
                || previousPlanSha256 == null || !previousPlanSha256.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Explicit bounded canonical predecessor and SHA-256 required");
    }
}
