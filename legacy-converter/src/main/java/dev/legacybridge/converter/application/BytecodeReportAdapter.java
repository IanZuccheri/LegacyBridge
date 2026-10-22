package dev.legacybridge.converter.application;

import dev.legacybridge.converter.bytecode.BytecodeScanAggregate;
import dev.legacybridge.converter.namespace.NamespaceCategory;
import dev.legacybridge.converter.namespace.NamespaceSummary;
import dev.legacybridge.converter.report.BytecodeSummary;
import dev.legacybridge.converter.report.NamespaceUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class BytecodeReportAdapter {
    BytecodeSummary adapt(
            BytecodeScanAggregate aggregate,
            int unreadableClassCount,
            Map<Integer, Integer> unreadableClassMajorVersions,
            int unreadablePreviewClassCount
    ) {
        if (unreadableClassCount < 0 || unreadablePreviewClassCount < 0
                || unreadablePreviewClassCount > unreadableClassCount) {
            throw new IllegalArgumentException("Unreadable class counts are inconsistent");
        }
        NamespaceSummary summary = aggregate.namespaces();
        List<NamespaceUsage> namespaces = new ArrayList<>();
        for (NamespaceCategory category : NamespaceCategory.values()) {
            dev.legacybridge.converter.namespace.NamespaceUsage definitions = summary.definition(category);
            dev.legacybridge.converter.namespace.NamespaceUsage references = summary.reference(category);
            if (definitions.occurrences() == 0 && references.occurrences() == 0) {
                continue;
            }
            namespaces.add(new NamespaceUsage(
                    category.name(),
                    definitions.occurrences(),
                    references.occurrences(),
                    definitions.samples().stream().limit(NamespaceUsage.SAMPLE_LIMIT).toList(),
                    references.samples().stream().limit(NamespaceUsage.SAMPLE_LIMIT).toList(),
                    definitions.truncated()
                            || definitions.retainedTypeCount() > NamespaceUsage.SAMPLE_LIMIT,
                    references.truncated()
                            || references.retainedTypeCount() > NamespaceUsage.SAMPLE_LIMIT
            ));
        }
        TreeMap<Integer, Integer> versions = new TreeMap<>();
        aggregate.classMajorVersions().forEach((major, count) ->
                versions.put(major, Math.toIntExact(count)));
        unreadableClassMajorVersions.forEach((major, count) -> {
            if (major == null || major < 0 || major > 0xFFFF || count == null || count < 0) {
                throw new IllegalArgumentException("Unreadable class header facts are invalid");
            }
            versions.merge(major, count, Math::addExact);
        });

        return new BytecodeSummary(
                Math.toIntExact(Math.addExact(aggregate.classCount(), unreadableClassCount)),
                unreadableClassCount,
                Math.addExact(
                        Math.toIntExact(aggregate.previewClassCount()),
                        unreadablePreviewClassCount
                ),
                Math.toIntExact(aggregate.features().nativeMethods()),
                versions,
                namespaces
        );
    }

}
