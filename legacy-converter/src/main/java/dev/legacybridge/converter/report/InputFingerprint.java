package dev.legacybridge.converter.report;

import java.util.Locale;
import java.util.Objects;

public record InputFingerprint(String fileName, long sizeBytes, String sha256) {
    public InputFingerprint {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(sha256, "sha256");
        if (fileName.isBlank() || sizeBytes < 0 || !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Invalid input fingerprint");
        }
        sha256 = sha256.toLowerCase(Locale.ROOT);
    }
}

