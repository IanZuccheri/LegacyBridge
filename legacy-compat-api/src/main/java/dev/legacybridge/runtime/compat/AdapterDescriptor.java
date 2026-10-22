package dev.legacybridge.runtime.compat;

import java.util.List;
import java.util.Objects;

/** Immutable documentation and exact entrypoint of an implemented adapter, never a loader stub. */
public record AdapterDescriptor(String id, int revision, String implementationClass, String method,
        String descriptor, String phase, String legacyApi, String targetApi, String transformation,
        String behaviorDifferences, String knownLimitations, List<String> fixtures) {
    public AdapterDescriptor {
        for (String value:List.of(id,implementationClass,method,descriptor,phase,legacyApi,targetApi,
                transformation,behaviorDifferences,knownLimitations)) {
            Objects.requireNonNull(value);
            if(value.isBlank()) throw new IllegalArgumentException("Adapter documentation must not be blank");
        }
        if(revision!=1 || !id.matches("[a-z][a-z0-9.-]+")) throw new IllegalArgumentException("Invalid adapter identity");
        fixtures=List.copyOf(fixtures);
        if(fixtures.isEmpty()) throw new IllegalArgumentException("Adapter must identify motivating fixture evidence");
    }
    public String owner() { return implementationClass.replace('.','/'); }
    public java.util.Map<String,Object> referenceTree() {
        return java.util.Map.of("id",id,"revision",revision,"implementationClass",implementationClass,"method",method,"descriptor",descriptor);
    }
    public java.util.Map<String,Object> documentationTree() {
        java.util.Map<String,Object> result=new java.util.TreeMap<>(referenceTree());
        result.put("phase",phase);result.put("legacyApi",legacyApi);result.put("targetApi",targetApi);
        result.put("transformation",transformation);result.put("behaviorDifferences",behaviorDifferences);
        result.put("knownLimitations",knownLimitations);result.put("fixtures",fixtures);
        return java.util.Collections.unmodifiableMap(result);
    }
}
