package dev.legacybridge.converter.report;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Objects;

public record BytecodeSummary(
        int classCount,
        int unreadableClassCount,
        int previewClassCount,
        int nativeMethodCount,
        SortedMap<Integer, Integer> classMajorVersions,
        List<NamespaceUsage> namespaces
) {
    public BytecodeSummary {
        if (classCount < 0 || unreadableClassCount < 0 || previewClassCount < 0 || nativeMethodCount < 0) {
            throw new IllegalArgumentException("Bytecode counts must not be negative");
        }
        Objects.requireNonNull(classMajorVersions, "classMajorVersions");
        TreeMap<Integer, Integer> checkedVersions = new TreeMap<>();
        classMajorVersions.forEach((major, count) -> {
            if (major == null || major < 0 || count == null || count < 1) {
                throw new IllegalArgumentException("classMajorVersions contains an invalid entry");
            }
            checkedVersions.put(major, count);
        });
        classMajorVersions = Collections.unmodifiableSortedMap(checkedVersions);
        Objects.requireNonNull(namespaces, "namespaces");
        namespaces = namespaces.stream()
                .map(value -> Objects.requireNonNull(value, "namespaces must not contain null"))
                .sorted(Comparator.comparing(NamespaceUsage::namespace)).toList();
    }

    public static BytecodeSummary empty() {
        return new BytecodeSummary(0, 0, 0, 0, new TreeMap<>(), List.of());
    }
}
