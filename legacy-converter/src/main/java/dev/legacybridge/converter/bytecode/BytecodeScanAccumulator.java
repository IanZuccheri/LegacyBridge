package dev.legacybridge.converter.bytecode;

import dev.legacybridge.converter.namespace.NamespaceSummary;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Incrementally aggregates class scans with memory bounded by fixed namespace categories and
 * their retained-sample limits.
 *
 * <p>An accepted {@link ClassBytecodeScan} is fully merged during {@link #accept} and is never
 * retained. Counts remain exact; only diagnostic name samples may be truncated.</p>
 */
public final class BytecodeScanAccumulator {
    private long classCount;
    private long previewClassCount;
    private final Map<Integer, Long> classMajorVersions = new TreeMap<>();
    private BytecodeFeatureCounts features = BytecodeFeatureCounts.EMPTY;
    private final NamespaceSummary.Accumulator namespaces = NamespaceSummary.accumulator();
    private final ArchiveReferenceInventory.Accumulator referenceInventory =
            ArchiveReferenceInventory.accumulator();

    public void accept(ClassBytecodeScan scan) {
        Objects.requireNonNull(scan, "scan");
        classCount = Math.addExact(classCount, 1);
        if (scan.preview()) {
            previewClassCount = Math.addExact(previewClassCount, 1);
        }
        classMajorVersions.merge(scan.majorVersion(), 1L, Math::addExact);
        features = features.plus(scan.features());
        namespaces.accept(scan.namespaces());
        referenceInventory.accept(scan);
    }

    public BytecodeScanAggregate snapshot() {
        return new BytecodeScanAggregate(
                classCount,
                previewClassCount,
                classMajorVersions,
                features,
                namespaces.snapshot(),
                referenceInventory.snapshot()
        );
    }
}
