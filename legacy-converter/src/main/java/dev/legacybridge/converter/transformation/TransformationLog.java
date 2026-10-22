package dev.legacybridge.converter.transformation;

import dev.legacybridge.converter.mapping.MappingConfidence;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Ordered, bounded and lossless change journal. Overflow aborts instead of hiding modifications. */
public final class TransformationLog {
    public enum Kind { CLASS_REMAP, METHOD_REMAP, FIELD_REMAP, DESCRIPTOR_REMAP, ADAPTER_CALL, RESOURCE_REWRITE }
    public static final int MAX_ENTRIES = 100_000;
    private final List<Entry> entries = new ArrayList<>();

    public record Entry(int sequence, Kind kind, String location, String before, String after,
                        String rule, MappingConfidence confidence, String reason, String adapterId) {
        public Map<String, Object> toTree() {
            return Map.of("sequence", sequence, "kind", kind.name(), "location", location,
                    "before", before, "after", after, "rule", rule, "confidence", confidence.name(),
                    "reason", reason, "adapter", AdapterCatalog.require(adapterId).referenceTree());
        }
    }

    public void add(Kind kind, String location, String before, String after, String rule,
                    MappingConfidence confidence, String reason, String adapterId) {
        Objects.requireNonNull(kind, "kind");
        if (confidence != MappingConfidence.VERIFIED)
            throw new TransformationException("Only semantically VERIFIED transformations may be journaled/applied");
        AdapterCatalog.require(adapterId);
        for (String text : List.of(location, before, after, rule, reason)) {
            if (text.isBlank() || text.length() > 8192)
                throw new TransformationException("Transformation log values must be nonblank and bounded");
        }
        if (before.equals(after)) return;
        if (entries.size() >= MAX_ENTRIES) throw new TransformationException("Transformation log limit exceeded; no changes omitted");
        entries.add(new Entry(entries.size() + 1, kind, location, before, after, rule, confidence, reason, adapterId));
    }

    public List<Entry> entries() { return List.copyOf(entries); }
    public Map<String, Object> toTree() {
        return Map.of("schemaVersion", 1, "entryCount", entries.size(),
                "entries", entries.stream().map(Entry::toTree).toList());
    }
}
