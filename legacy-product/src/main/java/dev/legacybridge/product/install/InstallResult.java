package dev.legacybridge.product.install;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Completed add-only publication. PENDING_RESTART is never activation or a gameplay claim. */
public record InstallResult(Path installed, Path transaction, String sha256, List<String> modIds) {
    public InstallResult {
        Objects.requireNonNull(installed); Objects.requireNonNull(transaction); Objects.requireNonNull(sha256);
        modIds = List.copyOf(modIds);
    }
    public String status() { return "PENDING_RESTART"; }
}
