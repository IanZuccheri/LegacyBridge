package dev.legacybridge.product.install;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Session engine fingerprints, not values inferred from an editable compatibility report. */
public record InstallSelection(Path original, String originalSha256, Path staged,
                               String resultSha256, Optional<Path> replaceTransaction) {
    public InstallSelection {
        Objects.requireNonNull(original); Objects.requireNonNull(originalSha256);
        Objects.requireNonNull(staged); Objects.requireNonNull(resultSha256);
        replaceTransaction = Objects.requireNonNull(replaceTransaction);
    }
}
