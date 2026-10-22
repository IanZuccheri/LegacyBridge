package dev.legacybridge.converter.bytecode;

import dev.legacybridge.converter.namespace.NamespaceSummary;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic, bounded aggregate suitable for attaching to a whole-JAR analysis.
 * Only the lexicographically first fixed number of per-class reference details
 * are retained; all scalar counters remain exact.
 */
public record BytecodeScanAggregate(
        long classCount,
        long previewClassCount,
        Map<Integer, Long> classMajorVersions,
        BytecodeFeatureCounts features,
        NamespaceSummary namespaces,
        ArchiveReferenceInventory referenceInventory
) {
    /** Backward-compatible constructor for aggregate producers without detailed references. */
    public BytecodeScanAggregate(
            long classCount,
            long previewClassCount,
            Map<Integer, Long> classMajorVersions,
            BytecodeFeatureCounts features,
            NamespaceSummary namespaces
    ) {
        this(
                classCount,
                previewClassCount,
                classMajorVersions,
                features,
                namespaces,
                new ArchiveReferenceInventory(
                        BytecodeReferenceInventory.EMPTY,
                        new BytecodeDefinitionIndex(classCount, Set.of(), classCount != 0),
                        List.of(),
                        classCount != 0
                )
        );
    }

    public BytecodeScanAggregate {
        if (classCount < 0 || previewClassCount < 0 || previewClassCount > classCount) {
            throw new IllegalArgumentException("class counts are inconsistent");
        }
        classMajorVersions = immutableVersionMap(classMajorVersions);
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(namespaces, "namespaces");
        Objects.requireNonNull(referenceInventory, "referenceInventory");

        long versionCount = 0;
        for (long count : classMajorVersions.values()) {
            versionCount = Math.addExact(versionCount, count);
        }
        if (versionCount != classCount) {
            throw new IllegalArgumentException("classMajorVersions must account for every readable class");
        }
        if (referenceInventory.definitions().occurrences() != classCount) {
            throw new IllegalArgumentException("referenceInventory definitions must account for every readable class");
        }
    }

    public static BytecodeScanAggregate empty() {
        return new BytecodeScanAccumulator().snapshot();
    }

    private static Map<Integer, Long> immutableVersionMap(Map<Integer, Long> source) {
        Objects.requireNonNull(source, "classMajorVersions");
        TreeMap<Integer, Long> copy = new TreeMap<>();
        source.forEach((version, count) -> {
            if (version == null || version < 0 || version > 0xFFFF) {
                throw new IllegalArgumentException("classMajorVersions contains an invalid version");
            }
            if (count == null || count < 0) {
                throw new IllegalArgumentException("classMajorVersions contains an invalid count");
            }
            copy.put(version, count);
        });
        return Collections.unmodifiableMap(copy);
    }
}
