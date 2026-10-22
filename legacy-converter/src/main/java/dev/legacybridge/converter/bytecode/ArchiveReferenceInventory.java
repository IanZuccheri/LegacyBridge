package dev.legacybridge.converter.bytecode;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Whole-archive reference view with exact counters and fixed retention limits.
 *
 * <p>External samples are obtained by subtracting the definition index. The
 * returned classification is exhaustive only when both
 * {@link #definitionIndexComplete()} and {@link #referenceSamplesComplete()}
 * are true.</p>
 */
public record ArchiveReferenceInventory(
        BytecodeReferenceInventory references,
        BytecodeDefinitionIndex definitions,
        List<ClassReferenceDetail> classDetails,
        boolean classDetailsTruncated
) {
    public static final int RETAINED_CLASS_DETAIL_LIMIT = 256;
    public static final ArchiveReferenceInventory EMPTY = new ArchiveReferenceInventory(
            BytecodeReferenceInventory.EMPTY,
            BytecodeDefinitionIndex.EMPTY,
            List.of(),
            false
    );

    public ArchiveReferenceInventory {
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(classDetails, "classDetails");
        if (classDetails.size() > RETAINED_CLASS_DETAIL_LIMIT) {
            throw new IllegalArgumentException("classDetails exceeds the retained-class limit");
        }
        TreeSet<ClassReferenceDetail> sorted = new TreeSet<>();
        for (ClassReferenceDetail detail : classDetails) {
            sorted.add(Objects.requireNonNull(detail, "classDetails must not contain null"));
        }
        classDetails = List.copyOf(sorted);
    }

    public Set<BytecodeReference> externalSamples() {
        return references.externalSamples(definitions.owners());
    }

    public boolean definitionIndexComplete() {
        return definitions.complete();
    }

    public boolean referenceSamplesComplete() {
        return !references.truncated();
    }

    public boolean externalSamplesComplete() {
        return definitionIndexComplete() && referenceSamplesComplete();
    }

    static Accumulator accumulator() {
        return new Accumulator();
    }

    static final class Accumulator {
        private final BytecodeReferenceInventory.Accumulator references =
                BytecodeReferenceInventory.accumulator();
        private final BytecodeDefinitionIndex.Accumulator definitions =
                BytecodeDefinitionIndex.accumulator();
        private final TreeSet<ClassReferenceDetail> classDetails = new TreeSet<>();
        private boolean classDetailsTruncated;

        void accept(ClassBytecodeScan scan) {
            Objects.requireNonNull(scan, "scan");
            references.accept(scan.references());
            definitions.record(scan.internalName());
            retain(ClassReferenceDetail.from(scan));
        }

        ArchiveReferenceInventory snapshot() {
            return new ArchiveReferenceInventory(
                    references.snapshot(),
                    definitions.snapshot(),
                    Collections.unmodifiableList(List.copyOf(classDetails)),
                    classDetailsTruncated
            );
        }

        private void retain(ClassReferenceDetail detail) {
            if (!classDetails.add(detail)) {
                return;
            }
            if (classDetails.size() > RETAINED_CLASS_DETAIL_LIMIT) {
                classDetails.pollLast();
                classDetailsTruncated = true;
            }
        }
    }
}
