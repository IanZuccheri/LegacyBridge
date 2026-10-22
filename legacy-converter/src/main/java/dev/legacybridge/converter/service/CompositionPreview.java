package dev.legacybridge.converter.service;

import dev.legacybridge.converter.conversion.composition.CompositionPlan;
import java.util.*;

/** No candidate bytes/path or executable plan are exposed. Admission is static and finite only. */
public record CompositionPreview(boolean staticallyAdmitted, String inputSha256,
                                 Optional<CompositionPlan> operations, Map<String, Object> report) {
    public CompositionPreview {
        Objects.requireNonNull(inputSha256); Objects.requireNonNull(operations);
        if (!inputSha256.matches("[0-9a-f]{64}") || staticallyAdmitted != operations.isPresent())
            throw new IllegalArgumentException("Invalid composition evidence");
        report = Collections.unmodifiableMap(new TreeMap<>(report));
    }
    public boolean publicationAllowed() { return false; }
}
