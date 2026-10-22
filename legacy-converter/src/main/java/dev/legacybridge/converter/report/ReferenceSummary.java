package dev.legacybridge.converter.report;

import dev.legacybridge.converter.bytecode.BytecodeReference;
import dev.legacybridge.converter.bytecode.BytecodeReferenceInventory;
import dev.legacybridge.converter.bytecode.BytecodeReferenceKind;

import java.util.List;
import java.util.Objects;

/** Exact prefix occurrence total plus a deterministic detail sample. */
public record ReferenceSummary(
        long occurrenceCount,
        List<BytecodeReference> references,
        boolean referencesTruncated,
        boolean externalityComplete
) {
    public static final ReferenceSummary EMPTY = new ReferenceSummary(0, List.of(), false, true);

    public ReferenceSummary {
        if (occurrenceCount < 0) {
            throw new IllegalArgumentException("occurrenceCount must not be negative");
        }
        Objects.requireNonNull(references, "references");
        if (references.size() > BytecodeReferenceInventory.RETAINED_REFERENCE_LIMIT) {
            throw new IllegalArgumentException("reference sample limit exceeded");
        }
        references = references.stream()
                .map(reference -> Objects.requireNonNull(
                        reference, "references must not contain null"))
                .peek(reference -> {
                    if ((reference.kind() == BytecodeReferenceKind.SUPERCLASS
                            || reference.kind() == BytecodeReferenceKind.INTERFACE)
                            && !reference.descriptor().isEmpty()) {
                        throw new IllegalArgumentException(
                                "hierarchy reference descriptors must be empty in reports");
                    }
                })
                .distinct().sorted().toList();
        if (references.size() > occurrenceCount) {
            throw new IllegalArgumentException("reference samples cannot exceed occurrences");
        }
    }
}
