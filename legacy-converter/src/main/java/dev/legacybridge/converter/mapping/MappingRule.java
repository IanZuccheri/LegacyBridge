package dev.legacybridge.converter.mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public record MappingRule(LegacySymbol legacy, TargetSymbol target,
                          MappingCategory category, MappingConfidence confidence,
                          MappingStatus status, List<String> evidence) {
    public static final int MAX_EVIDENCE = 32;
    public static final int MAX_EVIDENCE_LENGTH = 2048;

    public MappingRule {
        Objects.requireNonNull(legacy, "legacy");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(status, "status");
        evidence = boundedEvidence(evidence);
        if (target != null && legacy.kind() != target.kind()) {
            throw new IllegalArgumentException("Mapping must preserve symbol kind");
        }
        if (status == MappingStatus.RESOLVED) {
            if (target == null || confidence == MappingConfidence.NONE
                    || category == MappingCategory.REMOVED_API
                    || category == MappingCategory.ADAPTER_REQUIRED
                    || category == MappingCategory.NO_AUTOMATIC_EQUIVALENT) {
                throw new IllegalArgumentException("Resolved mappings require a target and a resolved category");
            }
            if (category == MappingCategory.IDENTITY && (!legacy.owner().equals(target.owner())
                    || !legacy.name().equals(target.name()) || !legacy.descriptor().equals(target.descriptor()))) {
                throw new IllegalArgumentException("Identity mapping must preserve the complete symbol");
            }
        } else if (status == MappingStatus.ADAPTER_REQUIRED) {
            if (category != MappingCategory.ADAPTER_REQUIRED && category != MappingCategory.DESCRIPTOR_CHANGE) {
                throw new IllegalArgumentException("Adapter mappings require an adapter or descriptor-change category");
            }
        } else if (status == MappingStatus.UNSUPPORTED) {
            if (target != null || (category != MappingCategory.REMOVED_API
                    && category != MappingCategory.NO_AUTOMATIC_EQUIVALENT)) {
                throw new IllegalArgumentException("Unsupported mappings cannot claim a target or resolved category");
            }
        } else {
            boolean candidate = target != null && (category == MappingCategory.PURE_RENAME
                    || category == MappingCategory.PACKAGE_MOVE || category == MappingCategory.DESCRIPTOR_CHANGE);
            if (!candidate && (target != null || category != MappingCategory.NO_AUTOMATIC_EQUIVALENT)) {
                throw new IllegalArgumentException("Manual mappings require an explicit candidate or no automatic equivalent");
            }
        }
    }

    /** Conservative symbol-only gate; the ASM pipeline additionally requires a concrete adapter recipe. */
    public boolean automaticRewriteAllowed() {
        return status == MappingStatus.RESOLVED && confidence == MappingConfidence.VERIFIED
                && legacy.descriptor().equals(target.descriptor())
                && (category == MappingCategory.IDENTITY || category == MappingCategory.PURE_RENAME
                || category == MappingCategory.PACKAGE_MOVE);
    }

    static List<String> boundedEvidence(List<String> entries) {
        Objects.requireNonNull(entries, "evidence");
        var sorted = new TreeSet<String>();
        boolean truncated = false;
        for (String entry : entries) {
            Objects.requireNonNull(entry, "evidence entry");
            if (entry.isBlank()) {
                throw new IllegalArgumentException("Evidence must not be blank");
            }
            String bounded = entry.length() <= MAX_EVIDENCE_LENGTH ? entry
                    : entry.substring(0, MAX_EVIDENCE_LENGTH - 32) + " [evidence text truncated]";
            sorted.add(bounded);
            if (sorted.size() > MAX_EVIDENCE) {
                sorted.pollLast();
                truncated = true;
            }
        }
        if (truncated) {
            while (sorted.size() >= MAX_EVIDENCE) {
                sorted.pollLast();
            }
            sorted.add("[additional evidence omitted]");
        }
        return List.copyOf(sorted);
    }

    static List<String> combineEvidence(List<String> first, List<String> second, String extra) {
        var result = new ArrayList<>(first);
        result.addAll(second);
        if (extra != null) {
            result.add(extra);
        }
        return boundedEvidence(result);
    }
}
