package dev.legacybridge.converter.report;

import dev.legacybridge.converter.archive.ArchivePolicy;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Collections;
import java.util.Set;

public record ArchiveSummary(
        int entryCount,
        long totalUncompressedBytes,
        boolean signed,
        boolean multiRelease,
        int specialEntryCount,
        boolean specialEntriesTruncated,
        List<String> specialEntries,
        Map<String, Long> specialEntryCounts
) {
    private static final Set<String> SPECIAL_ENTRY_KINDS = Set.of(
            "SIGNATURE", "ACCESS_TRANSFORMER", "COREMOD", "TRANSFORMATION_SERVICE",
            "SERVICE_PROVIDER", "MIXIN_CONFIGURATION", "NESTED_JAR", "NATIVE_LIBRARY",
            "MODULE_DESCRIPTOR", "MULTI_RELEASE"
    );

    public ArchiveSummary(
            int entryCount,
            long totalUncompressedBytes,
            boolean signed,
            boolean multiRelease,
            int specialEntryCount,
            boolean specialEntriesTruncated,
            List<String> specialEntries
    ) {
        this(entryCount, totalUncompressedBytes, signed, multiRelease, specialEntryCount,
                specialEntriesTruncated, specialEntries, Map.of());
    }

    public ArchiveSummary {
        if (entryCount < 0 || totalUncompressedBytes < 0 || specialEntryCount < 0) {
            throw new IllegalArgumentException("Archive counts must not be negative");
        }
        Objects.requireNonNull(specialEntries, "specialEntries");
        if (specialEntries.size() > ArchivePolicy.MAX_SPECIAL_ENTRY_SAMPLES) {
            throw new IllegalArgumentException("Special-entry sample limit exceeded");
        }
        specialEntries = specialEntries.stream().distinct().sorted().toList();
        if (specialEntries.size() > specialEntryCount) {
            throw new IllegalArgumentException("Special-entry samples cannot exceed their total count");
        }
        if (specialEntriesTruncated != (specialEntries.size() < specialEntryCount)) {
            throw new IllegalArgumentException("Special-entry truncation flag does not match its counts");
        }
        Objects.requireNonNull(specialEntryCounts, "specialEntryCounts");
        TreeMap<String, Long> counts = new TreeMap<>();
        specialEntryCounts.forEach((kind, count) -> {
            if (kind == null || !SPECIAL_ENTRY_KINDS.contains(kind)
                    || count == null || count < 1 || count > specialEntryCount) {
                throw new IllegalArgumentException("Special-entry kind counts must be positive and named");
            }
            counts.put(kind, count);
        });
        specialEntryCounts = Collections.unmodifiableMap(counts);
    }
}
