package dev.legacybridge.converter.bytecode;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Bounded index of class owners defined by the analyzed archive. */
public record BytecodeDefinitionIndex(
        long occurrences,
        Set<String> owners,
        boolean truncated
) {
    /** Above the safe-reader entry ceiling, so normal analyzer inputs remain complete. */
    public static final int RETAINED_OWNER_LIMIT = 65_536;
    public static final BytecodeDefinitionIndex EMPTY = new BytecodeDefinitionIndex(0, Set.of(), false);

    public BytecodeDefinitionIndex {
        if (occurrences < 0) {
            throw new IllegalArgumentException("occurrences must not be negative");
        }
        Objects.requireNonNull(owners, "owners");
        if (owners.size() > RETAINED_OWNER_LIMIT) {
            throw new IllegalArgumentException("owners exceeds the retained-owner limit");
        }
        TreeSet<String> copy = new TreeSet<>();
        for (String owner : owners) {
            if (owner == null || owner.isBlank()) {
                throw new IllegalArgumentException("owners must not contain blank names");
            }
            copy.add(owner);
        }
        if (occurrences < copy.size()) {
            throw new IllegalArgumentException("occurrences cannot be smaller than distinct owners");
        }
        owners = Collections.unmodifiableSet(copy);
    }

    public boolean complete() {
        return !truncated;
    }

    static Accumulator accumulator() {
        return new Accumulator();
    }

    static final class Accumulator {
        private long occurrences;
        private final TreeSet<String> owners = new TreeSet<>();
        private boolean truncated;

        void record(String owner) {
            if (owner == null || owner.isBlank()) {
                throw new IllegalArgumentException("owner must not be blank");
            }
            occurrences = Math.addExact(occurrences, 1);
            if (!owners.add(owner)) {
                return;
            }
            if (owners.size() > RETAINED_OWNER_LIMIT) {
                owners.pollLast();
                truncated = true;
            }
        }

        BytecodeDefinitionIndex snapshot() {
            return new BytecodeDefinitionIndex(occurrences, owners, truncated);
        }
    }
}
