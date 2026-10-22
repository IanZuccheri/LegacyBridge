package dev.legacybridge.converter.namespace;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamespaceCollectorTest {
    @Test
    void separatesDefinitionsFromDescriptorAndSignatureReferences() {
        NamespaceCollector collector = new NamespaceCollector();
        collector.recordDefinition("example/mod/Main");
        collector.recordDefinition("example/mod/Main");
        collector.recordDescriptor(
                "(Lnet/minecraft/world/level/Level;[Lnet/minecraft/world/item/Item;)Ljava/util/List;"
        );
        collector.recordClassOrMethodSignature(
                "<T:Lsignature/Bound;>Ljava/lang/Object;Lsignature/Contract<TT;>;"
        );
        collector.recordTypeSignature("Louter/Container<Lmodel/Argument;>.Inner;");

        NamespaceSummary summary = collector.snapshot();

        assertEquals(2, summary.definition(NamespaceCategory.OTHER).occurrences());
        assertEquals(1, summary.definition(NamespaceCategory.OTHER).retainedTypeCount());

        assertEquals(2, summary.reference(NamespaceCategory.MINECRAFT).occurrences());
        assertTrue(summary.reference(NamespaceCategory.OTHER).types().contains("signature/Bound"));
        assertTrue(summary.reference(NamespaceCategory.OTHER).types().contains("outer/Container$Inner"));
        assertTrue(summary.reference(NamespaceCategory.OTHER).types().contains("model/Argument"));
    }

    @Test
    void samplesAreSortedAndBounded() {
        NamespaceCollector collector = new NamespaceCollector();
        collector.recordReference("sample/Zed");
        collector.recordReference("sample/Alpha");
        collector.recordReference("sample/Middle");

        NamespaceUsage usage = collector.snapshot().reference(NamespaceCategory.OTHER);

        assertEquals(List.of("sample/Alpha", "sample/Middle", "sample/Zed"), usage.samples());
        assertEquals(3, usage.occurrences());
        assertEquals(3, usage.retainedTypeCount());
    }

    @Test
    void retentionIsBoundedBeforeSnapshotWhileOccurrenceCountRemainsExact() {
        NamespaceCollector collector = new NamespaceCollector();
        for (int index = 0; index < NamespaceUsage.RETAINED_TYPE_LIMIT + 12; index++) {
            collector.recordReference("sample/Type" + String.format(Locale.ROOT, "%03d", index));
        }
        collector.recordReference("sample/Type000");

        NamespaceUsage usage = collector.snapshot().reference(NamespaceCategory.OTHER);

        assertEquals(NamespaceUsage.RETAINED_TYPE_LIMIT + 13L, usage.occurrences());
        assertEquals(NamespaceUsage.RETAINED_TYPE_LIMIT, usage.retainedTypeCount());
        assertEquals("sample/Type000", usage.samples().get(0));
        assertEquals("sample/Type127", usage.samples().get(NamespaceUsage.RETAINED_TYPE_LIMIT - 1));
        assertTrue(usage.truncated());
    }
}
