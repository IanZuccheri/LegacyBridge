package dev.legacybridge.converter.namespace;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** Separates fixed namespace categories owned by definitions from references. */
public record NamespaceSummary(
        Map<NamespaceCategory, NamespaceUsage> definitions,
        Map<NamespaceCategory, NamespaceUsage> references
) {
    public static final NamespaceSummary EMPTY = new NamespaceSummary(Map.of(), Map.of());

    public NamespaceSummary {
        definitions = immutableSortedCopy(definitions, "definitions");
        references = immutableSortedCopy(references, "references");
    }

    public NamespaceUsage definition(NamespaceCategory category) {
        return definitions.getOrDefault(category, NamespaceUsage.EMPTY);
    }

    public NamespaceUsage reference(NamespaceCategory category) {
        return references.getOrDefault(category, NamespaceUsage.EMPTY);
    }

    public long definitionOccurrences() {
        return totalOccurrences(definitions);
    }

    public long referenceOccurrences() {
        return totalOccurrences(references);
    }

    public static NamespaceSummary merge(Collection<NamespaceSummary> summaries) {
        Objects.requireNonNull(summaries, "summaries");
        Accumulator accumulator = accumulator();
        for (NamespaceSummary summary : summaries) {
            accumulator.accept(Objects.requireNonNull(summary, "summaries must not contain null"));
        }
        return accumulator.snapshot();
    }

    public static Accumulator accumulator() {
        return new Accumulator();
    }

    private static long totalOccurrences(Map<NamespaceCategory, NamespaceUsage> usages) {
        long total = 0;
        for (NamespaceUsage usage : usages.values()) {
            total = Math.addExact(total, usage.occurrences());
        }
        return total;
    }

    private static Map<NamespaceCategory, NamespaceUsage> immutableSortedCopy(
            Map<NamespaceCategory, NamespaceUsage> source,
            String name
    ) {
        Objects.requireNonNull(source, name);
        EnumMap<NamespaceCategory, NamespaceUsage> copy = new EnumMap<>(NamespaceCategory.class);
        source.forEach((category, usage) -> {
            copy.put(
                    Objects.requireNonNull(category, name + " category"),
                    Objects.requireNonNull(usage, name + " usage")
            );
        });
        return Collections.unmodifiableMap(copy);
    }

    private static void mergeInto(
            Map<NamespaceCategory, MutableUsage> target,
            Map<NamespaceCategory, NamespaceUsage> source
    ) {
        source.forEach((category, usage) ->
                target.computeIfAbsent(category, ignored -> new MutableUsage()).merge(usage));
    }

    private static Map<NamespaceCategory, NamespaceUsage> freeze(
            Map<NamespaceCategory, MutableUsage> source
    ) {
        EnumMap<NamespaceCategory, NamespaceUsage> result = new EnumMap<>(NamespaceCategory.class);
        source.forEach((category, usage) -> result.put(category, usage.freeze()));
        return result;
    }

    /** Incremental bounded merge used by the archive-wide bytecode accumulator. */
    public static final class Accumulator {
        private final Map<NamespaceCategory, MutableUsage> definitions =
                new EnumMap<>(NamespaceCategory.class);
        private final Map<NamespaceCategory, MutableUsage> references =
                new EnumMap<>(NamespaceCategory.class);

        private Accumulator() {
        }

        public void accept(NamespaceSummary summary) {
            Objects.requireNonNull(summary, "summary");
            mergeInto(definitions, summary.definitions);
            mergeInto(references, summary.references);
        }

        public NamespaceSummary snapshot() {
            return new NamespaceSummary(freeze(definitions), freeze(references));
        }
    }

    private static final class MutableUsage {
        private long occurrences;
        private final TreeSet<String> types = new TreeSet<>();
        private boolean truncated;

        private void merge(NamespaceUsage usage) {
            occurrences = Math.addExact(occurrences, usage.occurrences());
            truncated |= usage.truncated();
            for (String type : usage.types()) {
                types.add(type);
                if (types.size() > NamespaceUsage.RETAINED_TYPE_LIMIT) {
                    types.pollLast();
                    truncated = true;
                }
            }
        }

        private NamespaceUsage freeze() {
            return new NamespaceUsage(occurrences, types, truncated);
        }
    }
}
