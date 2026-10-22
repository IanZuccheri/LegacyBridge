package dev.legacybridge.converter.service;

import dev.legacybridge.converter.report.Complexity;
import dev.legacybridge.converter.report.InputFingerprint;
import dev.legacybridge.converter.report.ModDeclaration;
import dev.legacybridge.converter.report.SourceProfile;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bounded inventory summaries, never retained full bytecode reports or an installed-mod list. */
public record InventoryResult(Path directory, List<Entry> entries, boolean truncated) {
    public InventoryResult {
        Objects.requireNonNull(directory, "directory");
        entries = List.copyOf(entries);
    }

    public record Entry(Path path, Optional<InputFingerprint> fingerprint, List<ModDeclaration> mods,
                        SourceProfile sourceProfile, Complexity complexity, int riskScore,
                        boolean duplicateModId, Optional<EngineError> error, boolean alreadyConverted) {
        public Entry(Path path, Optional<InputFingerprint> fingerprint, List<ModDeclaration> mods,
                     SourceProfile sourceProfile, Complexity complexity, int riskScore,
                     boolean duplicateModId, Optional<EngineError> error) {
            this(path, fingerprint, mods, sourceProfile, complexity, riskScore, duplicateModId, error, false);
        }
        public Entry {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(fingerprint, "fingerprint");
            mods = List.copyOf(mods);
            Objects.requireNonNull(sourceProfile, "sourceProfile");
            Objects.requireNonNull(complexity, "complexity");
            Objects.requireNonNull(error, "error");
            if (riskScore < 0 || riskScore > 100) throw new IllegalArgumentException("Invalid risk score");
        }

        Entry withDuplicate(boolean duplicate) {
            return new Entry(path, fingerprint, mods, sourceProfile, complexity, riskScore, duplicate, error, alreadyConverted);
        }
    }
}
