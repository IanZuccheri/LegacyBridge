package dev.legacybridge.converter.bytecode;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Exact occurrence counts plus a bounded deterministic sample of references.
 *
 * <p>Every occurrence contributes to {@link #occurrences()} and
 * {@link #occurrencesByKind()}. At most
 * {@value #RETAINED_REFERENCE_LIMIT} lexicographically first distinct details
 * are retained, so adversarial class files cannot make archive-wide detail
 * retention unbounded.</p>
 */
public record BytecodeReferenceInventory(
        long occurrences,
        Map<BytecodeReferenceKind, Long> occurrencesByKind,
        Set<BytecodeReference> samples,
        boolean truncated
) {
    public static final int RETAINED_REFERENCE_LIMIT = 256;
    public static final int RETENTION_ATTEMPT_LIMIT = 4096;
    public static final BytecodeReferenceInventory EMPTY = new BytecodeReferenceInventory(
            0,
            Map.of(),
            Set.of(),
            false
    );

    public BytecodeReferenceInventory {
        if (occurrences < 0) {
            throw new IllegalArgumentException("occurrences must not be negative");
        }
        occurrencesByKind = immutableCounts(occurrencesByKind);
        samples = immutableSamples(samples);

        long kindTotal = 0;
        for (long count : occurrencesByKind.values()) {
            kindTotal = Math.addExact(kindTotal, count);
        }
        if (kindTotal != occurrences) {
            throw new IllegalArgumentException("occurrencesByKind must account for every occurrence");
        }
        if (occurrences < samples.size()) {
            throw new IllegalArgumentException("occurrences cannot be smaller than retained samples");
        }
    }

    public long occurrences(BytecodeReferenceKind kind) {
        Objects.requireNonNull(kind, "kind");
        return occurrencesByKind.getOrDefault(kind, 0L);
    }

    /** Filters only the retained samples; exact totals remain available separately. */
    public Set<BytecodeReference> externalSamples(Set<String> internalDefinitions) {
        Objects.requireNonNull(internalDefinitions, "internalDefinitions");
        TreeSet<BytecodeReference> external = new TreeSet<>();
        for (BytecodeReference reference : samples) {
            if (!reference.ownerIsDefinedBy(internalDefinitions)) {
                external.add(reference);
            }
        }
        return Collections.unmodifiableSet(external);
    }

    static Accumulator accumulator() {
        return new Accumulator();
    }

    private static Map<BytecodeReferenceKind, Long> immutableCounts(
            Map<BytecodeReferenceKind, Long> source
    ) {
        Objects.requireNonNull(source, "occurrencesByKind");
        EnumMap<BytecodeReferenceKind, Long> copy = new EnumMap<>(BytecodeReferenceKind.class);
        source.forEach((kind, count) -> {
            Objects.requireNonNull(kind, "occurrencesByKind key");
            if (count == null || count < 0) {
                throw new IllegalArgumentException("occurrencesByKind contains an invalid count");
            }
            if (count != 0) {
                copy.put(kind, count);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Set<BytecodeReference> immutableSamples(Set<BytecodeReference> source) {
        Objects.requireNonNull(source, "samples");
        if (source.size() > RETAINED_REFERENCE_LIMIT) {
            throw new IllegalArgumentException("samples exceeds the retained-reference limit");
        }
        TreeSet<BytecodeReference> copy = new TreeSet<>();
        for (BytecodeReference reference : source) {
            copy.add(Objects.requireNonNull(reference, "samples must not contain null"));
        }
        return Collections.unmodifiableSet(copy);
    }

    static final class Accumulator {
        private long occurrences;
        private final EnumMap<BytecodeReferenceKind, Long> occurrencesByKind =
                new EnumMap<>(BytecodeReferenceKind.class);
        private final TreeSet<BytecodeReference> samples = new TreeSet<>();
        private int retentionAttempts;
        private boolean truncated;

        void record(BytecodeReference reference) {
            Objects.requireNonNull(reference, "reference");
            occurrences = Math.addExact(occurrences, 1);
            occurrencesByKind.merge(reference.kind(), 1L, Math::addExact);
            retain(reference);
        }

        void accept(BytecodeReferenceInventory inventory) {
            Objects.requireNonNull(inventory, "inventory");
            occurrences = Math.addExact(occurrences, inventory.occurrences());
            inventory.occurrencesByKind().forEach((kind, count) ->
                    occurrencesByKind.merge(kind, count, Math::addExact));
            truncated |= inventory.truncated();
            inventory.samples().forEach(this::retain);
        }

        BytecodeReferenceInventory snapshot() {
            return new BytecodeReferenceInventory(
                    occurrences,
                    occurrencesByKind,
                    samples,
                    truncated
            );
        }

        private void retain(BytecodeReference reference) {
            boolean hierarchy = reference.kind() == BytecodeReferenceKind.SUPERCLASS
                    || reference.kind() == BytecodeReferenceKind.INTERFACE;
            if (!hierarchy) {
                if (retentionAttempts >= RETENTION_ATTEMPT_LIMIT) {
                    truncated = true;
                    return;
                }
                retentionAttempts = Math.incrementExact(retentionAttempts);
            }
            BytecodeReference bounded = reference.boundedForReport();
            truncated |= bounded != reference;
            if (!samples.add(bounded)) {
                return;
            }
            if (samples.size() > RETAINED_REFERENCE_LIMIT) {
                samples.remove(evictionCandidate());
                truncated = true;
            }
        }

        /** Hierarchy facts outrank ordinary samples; ordering within a rank remains deterministic. */
        private BytecodeReference evictionCandidate() {
            int lowestPriority = samples.stream()
                    .mapToInt(Accumulator::retentionPriority)
                    .min()
                    .orElseThrow();
            return samples.descendingSet().stream()
                    .filter(reference -> retentionPriority(reference) == lowestPriority)
                    .findFirst()
                    .orElseThrow();
        }

        private static int retentionPriority(BytecodeReference reference) {
            return switch (reference.kind()) {
                case SUPERCLASS -> 2;
                case INTERFACE -> 1;
                case FIELD, METHOD, TYPE -> 0;
            };
        }
    }
}
