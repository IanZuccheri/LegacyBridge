package dev.legacybridge.converter.service;

import java.util.*;

/** Inert scoped evidence. There is deliberately no installed path, journal, publication or activation authority. */
public record PreservedArtifactInspection(boolean validated, String originalSha256, String candidateSha256,
                                         Optional<Identity> identity, int classCount, int methodCount,
                                         List<EngineError> blockers, Map<String, Object> checks) {
    public record Identity(String modId, String modVersion) { }
    public PreservedArtifactInspection {
        identity = Objects.requireNonNull(identity); blockers = List.copyOf(blockers); checks = Map.copyOf(checks);
        if (validated != blockers.isEmpty() || validated && identity.isEmpty() || classCount < 0 || methodCount < 0)
            throw new IllegalArgumentException("Inconsistent inspection evidence");
    }
    public Map<String,Object> toTree() {
        var tree = new TreeMap<String,Object>();
        tree.put("schemaVersion", 1); tree.put("reportType", "LEGACY_PRESERVED_ARTIFACT_INSPECTION");
        tree.put("status", validated ? "PASSED_SCOPED" : "UNSUPPORTED"); tree.put("profile", "logic-preserving-v1");
        tree.put("originalSha256", originalSha256); tree.put("candidateSha256", candidateSha256);
        tree.put("modId", identity.map(Identity::modId).orElse(null)); tree.put("modVersion", identity.map(Identity::modVersion).orElse(null));
        tree.put("classCount", classCount); tree.put("methodCount", methodCount);
        tree.put("checks", new TreeMap<>(checks));
        tree.put("blockers", blockers.stream().map(e -> Map.of("code",e.code(),"message",e.message(),"remediation",e.remediation())).toList());
        tree.put("installationAllowed", false); tree.put("publicationAllowed", false); tree.put("classesExecuted", false);
        tree.put("runtimeValidation", "NOT_RUN"); return Collections.unmodifiableMap(tree);
    }
}
