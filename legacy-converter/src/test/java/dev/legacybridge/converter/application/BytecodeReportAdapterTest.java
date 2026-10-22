package dev.legacybridge.converter.application;

import dev.legacybridge.converter.bytecode.BytecodeFeatureCounts;
import dev.legacybridge.converter.bytecode.BytecodeScanAggregate;
import dev.legacybridge.converter.bytecode.BytecodeScanAccumulator;
import dev.legacybridge.converter.bytecode.ClassBytecodeScan;
import dev.legacybridge.converter.namespace.NamespaceCategory;
import dev.legacybridge.converter.namespace.NamespaceSummary;
import dev.legacybridge.converter.report.BytecodeSummary;
import dev.legacybridge.converter.report.NamespaceUsage;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BytecodeReportAdapterTest {
    @Test
    void reportSamplesAreCappedAndExposeTruncationWithoutReducingCounts() {
        Set<String> retainedReferences = new LinkedHashSet<>();
        for (int index = 0; index < 20; index++) {
            retainedReferences.add("example/Type" + String.format(Locale.ROOT, "%02d", index));
        }
        dev.legacybridge.converter.namespace.NamespaceUsage internalUsage =
                new dev.legacybridge.converter.namespace.NamespaceUsage(37, retainedReferences, false);
        NamespaceSummary namespaces = new NamespaceSummary(
                Map.of(),
                Map.of(NamespaceCategory.OTHER, internalUsage)
        );
        BytecodeScanAggregate aggregate = new BytecodeScanAggregate(
                0,
                0,
                Map.of(),
                BytecodeFeatureCounts.EMPTY,
                namespaces
        );

        BytecodeSummary summary = new BytecodeReportAdapter().adapt(aggregate, 0, Map.of(), 0);
        NamespaceUsage reportUsage = summary.namespaces().get(0);

        assertEquals(37, reportUsage.referenceCount());
        assertEquals(NamespaceUsage.SAMPLE_LIMIT, reportUsage.referenceSamples().size());
        assertEquals("example/Type00", reportUsage.referenceSamples().get(0));
        assertEquals("example/Type11", reportUsage.referenceSamples().get(11));
        assertTrue(reportUsage.referenceSamplesTruncated());
    }

    @Test
    void classAndMethodCountsDoNotDeduplicateMultiReleaseNames() {
        ClassBytecodeScan base = previewScan("example/Base.class");
        ClassBytecodeScan versioned = previewScan("META-INF/versions/17/example/Base.class");
        BytecodeScanAccumulator accumulator = new BytecodeScanAccumulator();
        accumulator.accept(base);
        accumulator.accept(versioned);
        BytecodeScanAggregate aggregate = accumulator.snapshot();

        BytecodeSummary summary = new BytecodeReportAdapter().adapt(aggregate, 0, Map.of(), 0);

        assertEquals(2, summary.previewClassCount());
        assertEquals(2, summary.nativeMethodCount());
    }

    private static ClassBytecodeScan previewScan(String entryName) {
        return new ClassBytecodeScan(
                entryName,
                "example/Base",
                0xFFFF,
                61,
                true,
                0,
                new BytecodeFeatureCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
                NamespaceSummary.EMPTY
        );
    }
}
