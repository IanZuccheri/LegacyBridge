package dev.legacybridge.converter.namespace;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable usage data for one fixed namespace category.
 *
 * <p>{@code occurrences} counts every discovered type occurrence, while
 * {@code types} contains at most {@value #RETAINED_TYPE_LIMIT} lexicographically first
 * JVM internal names. The flag records whether other distinct names were
 * omitted. This keeps aggregation bounded without weakening occurrence
 * counts.</p>
 */
public record NamespaceUsage(long occurrences, Set<String> types, boolean truncated) {
    public static final int RETAINED_TYPE_LIMIT = 128;
    public static final NamespaceUsage EMPTY = new NamespaceUsage(0, Set.of(), false);

    public NamespaceUsage {
        if (occurrences < 0) {
            throw new IllegalArgumentException("occurrences must not be negative");
        }

        Objects.requireNonNull(types, "types");
        if (types.size() > RETAINED_TYPE_LIMIT) {
            throw new IllegalArgumentException("types exceeds the retained-sample limit");
        }
        TreeSet<String> sortedTypes = new TreeSet<>();
        for (String type : types) {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("types must not contain blank names");
            }
            sortedTypes.add(type);
        }
        if (occurrences < sortedTypes.size()) {
            throw new IllegalArgumentException("occurrences cannot be smaller than the distinct type count");
        }
        types = Collections.unmodifiableSet(sortedTypes);
    }

    public int retainedTypeCount() {
        return types.size();
    }

    public List<String> samples() {
        return List.copyOf(types);
    }
}
