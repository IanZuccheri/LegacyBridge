package dev.legacybridge.converter.service;

import java.util.*;

/** Recognizing a constructor is not proof of inherited behavior. Every current result remains UNSUPPORTED. */
public record IronSwordInspection(String inputSha256, boolean constructorRecognized,
                                  Optional<Identity> identity, List<EngineError> blockers, Map<String,Object> checks) {
    public record Identity(String modId, String modVersion, String registryName) { }
    public IronSwordInspection {
        if (inputSha256 == null || !inputSha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid selected hash");
        identity = Objects.requireNonNull(identity);
        blockers = List.copyOf(blockers);
        if (blockers.isEmpty() || constructorRecognized && identity.isEmpty())
            throw new IllegalArgumentException("An inspection cannot erase the missing semantic recipe");
        checks = freezeMap(checks);
    }
    public Map<String,Object> toTree() {
        var result = new TreeMap<String,Object>();
        result.put("schemaVersion",1); result.put("reportType","LEGACY_IRON_SWORD_SEMANTIC_INSPECTION");
        result.put("status","UNSUPPORTED"); result.put("inputSha256",inputSha256);
        result.put("constructorRecognized",constructorRecognized);
        result.put("modId",identity.map(Identity::modId).orElse(null));
        result.put("modVersion",identity.map(Identity::modVersion).orElse(null));
        result.put("registryName",identity.map(Identity::registryName).orElse(null));
        result.put("checks",checks);
        result.put("blockers",blockers.stream().map(b -> Map.of("code",b.code(),"message",b.message(),"remediation",b.remediation())).toList());
        result.put("convertible",false); result.put("candidateGenerated",false); result.put("classesExecuted",false);
        result.put("publicationAllowed",false); result.put("installationAllowed",false); result.put("installed",false);
        result.put("runtimeValidation","NOT_RUN"); result.put("transformationCount",0);
        return Collections.unmodifiableMap(result);
    }
    private static Map<String,Object> freezeMap(Map<String,Object> values) {
        var copy = new TreeMap<String,Object>();
        Objects.requireNonNull(values).forEach((k,v) -> copy.put(Objects.requireNonNull(k),freeze(v)));
        return Collections.unmodifiableMap(copy);
    }
    private static Object freeze(Object value) {
        if (value instanceof Map<?,?> map) {
            var copy = new TreeMap<String,Object>();
            map.forEach((k,v) -> { if (!(k instanceof String key)) throw new IllegalArgumentException("Non-string evidence key"); copy.put(key,freeze(v)); });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) return list.stream().map(IronSwordInspection::freeze).toList();
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double || value instanceof java.math.BigInteger
                || value instanceof java.math.BigDecimal) return value;
        throw new IllegalArgumentException("Non-JSON evidence value");
    }
}
