package dev.legacybridge.product.controller;

import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.product.install.RecoveryAction;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Explicit developer selection, not approval. No data path or consent is recovered from receipts. */
public record PreservedInstallSelection(Path admission, String admissionSha256, MappingData data,
                                        Optional<Recovery> recovery) {
    public PreservedInstallSelection {
        path(admission); hash(admissionSha256); Objects.requireNonNull(data); Objects.requireNonNull(recovery);
        if (data.kind() != MappingData.Kind.PORTABLE_BUNDLE || data.expectedManifestSha256().isEmpty())
            throw new IllegalArgumentException("Explicit reviewed full data and manifest pin required");
    }
    public record Recovery(Path transaction, String planSha256, UUID priorApprovalId, RecoveryAction action) {
        public Recovery { path(transaction); hash(planSha256); Objects.requireNonNull(priorApprovalId); Objects.requireNonNull(action); }
    }
    private static void path(Path value) {
        if (value == null || !value.isAbsolute() || !value.equals(value.normalize()) || value.toString().length() > 4096)
            throw new IllegalArgumentException("Explicit bounded absolute canonical selection required");
    }
    private static void hash(String value) {
        if (value == null || !value.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Exact SHA-256 required");
    }
}
