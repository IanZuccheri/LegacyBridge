package dev.legacybridge.converter.mapping;

import dev.legacybridge.converter.mapping.source.MappingSource;
import dev.legacybridge.converter.report.InputFingerprint;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Separate versioned mapping report; analyzer schema v2 remains unchanged. */
public record MappingReport(
        InputFingerprint input,
        boolean sourcesComplete,
        List<MappingSource> sources,
        List<String> diagnostics,
        int readableClassCount,
        int unreadableClassCount,
        long observedReferenceCount,
        boolean referencesTruncated,
        List<Entry> entries
) {
    public MappingReport {
        sources = List.copyOf(sources);
        diagnostics = List.copyOf(diagnostics);
        entries = List.copyOf(entries);
    }

    public boolean requiresReview() {
        return !sourcesComplete || unreadableClassCount > 0 || referencesTruncated
                || entries.stream().anyMatch(entry -> entry.rule().status() != MappingStatus.RESOLVED);
    }

    public Map<String, Object> toTree() {
        Map<String, Object> root = new java.util.LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("reportType", "LEGACY_MAPPINGS");
        root.put("legacyMinecraft", "1.16.5");
        root.put("legacyForge", "36.2.42");
        root.put("targetMinecraft", "1.20.1");
        root.put("targetForge", "47.4.23");
        root.put("targetNamespace", "MOJANG_CLASS_NAMES_WITH_SRG_MEMBERS");
        root.put("input", Map.of("fileName", input.fileName(), "sizeBytes", input.sizeBytes(), "sha256", input.sha256()));
        root.put("analysisStatus", sourcesComplete && unreadableClassCount == 0 && !referencesTruncated ? "COMPLETE" : "PARTIAL");
        root.put("mappingSourcesComplete", sourcesComplete);
        root.put("sources", sources.stream().map(source -> Map.of(
                "id", source.id(), "sha256", source.sha256(), "origin", source.origin())).toList());
        root.put("diagnostics", diagnostics);
        root.put("readableClassCount", readableClassCount);
        root.put("unreadableClassCount", unreadableClassCount);
        root.put("observedReferenceCount", observedReferenceCount);
        root.put("referenceScope", "External non-JDK owners; member descriptors are also reported separately.");
        root.put("referencesTruncated", referencesTruncated);
        root.put("reportedSymbolCount", entries.size());
        TreeMap<String, Long> counts = new TreeMap<>();
        for (MappingStatus status : MappingStatus.values()) counts.put(status.name(), 0L);
        for (Entry entry : entries) counts.merge(entry.rule().status().name(), 1L, Long::sum);
        root.put("statusCounts", counts);
        root.put("unresolvedMappingCount", entries.stream().filter(entry -> entry.rule().status() != MappingStatus.RESOLVED).count());
        root.put("conversionStatus", "NOT_IMPLEMENTED");
        root.put("bytecodeModified", false);
        root.put("entries", entries.stream().map(Entry::toTree).toList());
        return sortedObject(root);
    }

    private static Map<String, Object> sortedObject(Map<String, Object> input) {
        TreeMap<String, Object> output = new TreeMap<>();
        input.forEach((key, value) -> output.put(key, sorted(value)));
        return output;
    }

    private static Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> result = new TreeMap<>();
            map.forEach((key, item) -> result.put(key.toString(), sorted(item)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(MappingReport::sorted).toList();
        return value;
    }

    private static Map<String, Object> symbol(SymbolKind kind, String owner, String name, String descriptor) {
        return Map.of("kind", kind.name(), "owner", owner, "name", name, "descriptor", descriptor);
    }

    public record Entry(MappingRule rule, long occurrenceCount, List<String> classEntries, boolean evidenceTruncated) {
        public Entry { classEntries = List.copyOf(classEntries); }
        Map<String, Object> toTree() {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            LegacySymbol old = rule.legacy();
            result.put("legacy", symbol(old.kind(), old.owner(), old.name(), old.descriptor()));
            TargetSymbol target = rule.target();
            result.put("target", target == null ? null : symbol(target.kind(), target.owner(), target.name(), target.descriptor()));
            result.put("status", rule.status().name());
            result.put("category", rule.category().name());
            result.put("confidence", rule.confidence().name());
            result.put("automaticRewriteAllowed", rule.automaticRewriteAllowed());
            result.put("evidence", rule.evidence());
            result.put("occurrenceCount", occurrenceCount);
            result.put("classEntries", classEntries);
            result.put("evidenceTruncated", evidenceTruncated);
            return result;
        }
    }
}
