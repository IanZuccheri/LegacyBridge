package dev.legacybridge.converter.content;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Exact distinct namespace count with bounded, deterministic samples. */
public record ContentNamespaceSummary(
        long namespaceCount,
        List<String> samples,
        boolean samplesTruncated
) {
    public static final ContentNamespaceSummary EMPTY =
            new ContentNamespaceSummary(0, List.of(), false);

    public ContentNamespaceSummary {
        if (namespaceCount < 0) {
            throw new IllegalArgumentException("namespaceCount must not be negative");
        }
        Objects.requireNonNull(samples, "samples");
        TreeSet<String> sorted = new TreeSet<>();
        for (String sample : samples) {
            String value = Objects.requireNonNull(sample, "samples must not contain null");
            if (value.isBlank()) {
                throw new IllegalArgumentException("samples must not contain blank namespaces");
            }
            sorted.add(value);
        }
        samples = List.copyOf(sorted);
        if (samples.size() > namespaceCount) {
            throw new IllegalArgumentException("sample count must not exceed namespaceCount");
        }
        samplesTruncated |= namespaceCount > samples.size();
    }
}
