package dev.legacybridge.converter.bytecode;

import dev.legacybridge.converter.namespace.NamespaceCategory;
import dev.legacybridge.converter.namespace.NamespaceSummary;
import dev.legacybridge.converter.namespace.NamespaceUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BytecodeScanAccumulatorTest {
    @Test
    void incrementallyKeepsExactCountsAndOnlyBoundedDeterministicSamples() {
        List<ClassBytecodeScan> scans = new ArrayList<>();
        for (int index = 0; index < 512; index++) {
            scans.add(scan(index));
        }

        BytecodeScanAccumulator forward = new BytecodeScanAccumulator();
        scans.forEach(forward::accept);

        Collections.reverse(scans);
        BytecodeScanAccumulator reverse = new BytecodeScanAccumulator();
        scans.forEach(reverse::accept);

        BytecodeScanAggregate aggregate = forward.snapshot();
        assertEquals(aggregate, reverse.snapshot());
        assertEquals(512, aggregate.classCount());
        assertEquals(256, aggregate.previewClassCount());
        assertEquals(Map.of(52, 256L, 61, 256L), aggregate.classMajorVersions());
        assertEquals(1_024, aggregate.features().nativeMethods());

        NamespaceUsage references = aggregate.namespaces().reference(NamespaceCategory.OTHER);
        assertEquals(1_536, references.occurrences());
        assertEquals(NamespaceUsage.RETAINED_TYPE_LIMIT, references.retainedTypeCount());
        assertTrue(references.truncated());
        assertEquals("external/Type0000", references.samples().get(0));
        assertEquals("external/Type0127", references.samples().get(NamespaceUsage.RETAINED_TYPE_LIMIT - 1));
    }

    private static ClassBytecodeScan scan(int index) {
        boolean preview = (index & 1) != 0;
        String reference = "external/Type" + String.format(Locale.ROOT, "%04d", index);
        NamespaceSummary namespaces = new NamespaceSummary(
                Map.of(),
                Map.of(NamespaceCategory.OTHER, new NamespaceUsage(3, Set.of(reference), false))
        );
        return new ClassBytecodeScan(
                "classes/Entry" + index + ".class",
                "classes/Entry" + index,
                preview ? 0xFFFF : 0,
                preview ? 61 : 52,
                preview,
                0,
                new BytecodeFeatureCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2),
                namespaces
        );
    }
}
