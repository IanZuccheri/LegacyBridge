package dev.legacybridge.converter.content;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Exact category count with a bounded, deterministic entry-name sample. */
public record ContentCategorySummary(
        long entryCount,
        List<String> samples,
        boolean samplesTruncated
) {
    public ContentCategorySummary {
        if (entryCount < 0) {
            throw new IllegalArgumentException("entryCount must not be negative");
        }
        Objects.requireNonNull(samples, "samples");
        TreeSet<String> sorted = new TreeSet<>();
        for (String sample : samples) {
            String value = Objects.requireNonNull(sample, "samples must not contain null");
            if (value.isBlank()) {
                throw new IllegalArgumentException("samples must not contain blank names");
            }
            sorted.add(value);
        }
        samples = List.copyOf(sorted);
        if (samples.size() > entryCount) {
            throw new IllegalArgumentException("sample count must not exceed entryCount");
        }
        samplesTruncated |= entryCount > samples.size();
    }
}
