package dev.legacybridge.product.install;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Explicit developer confirmation of one admission pin, NOT reconstructed from a saved PASS.
 * The trusted caller obtains consent and keeps the actual session stable until completion.
 * A nonce records that selection, not a signature or authentication token. No I/O here.
 */
public record PreservedInstallApproval(Path admission, String admissionSha256, UUID approvalId) {
    public PreservedInstallApproval {
        Objects.requireNonNull(admission); Objects.requireNonNull(approvalId);
        if (!admission.isAbsolute() || !admission.normalize().equals(admission)
                || admissionSha256 == null || !admissionSha256.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Explicit absolute admission and reviewed SHA-256 required");
    }
}
