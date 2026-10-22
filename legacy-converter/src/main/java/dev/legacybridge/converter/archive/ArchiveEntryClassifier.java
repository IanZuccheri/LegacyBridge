package dev.legacybridge.converter.archive;

import java.util.Locale;

/** Shared classifiers for security-sensitive archive entry handling. */
public final class ArchiveEntryClassifier {
    private ArchiveEntryClassifier() {
    }

    public static boolean isMixinConfiguration(String entryName) {
        String normalized = entryName.toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf('/');
        String fileName = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        if (!fileName.endsWith(".json") || fileName.endsWith(".refmap.json")) {
            return false;
        }
        return fileName.startsWith("mixins.")
                || fileName.endsWith(".mixins.json")
                || fileName.endsWith(".mixin.json");
    }
}
