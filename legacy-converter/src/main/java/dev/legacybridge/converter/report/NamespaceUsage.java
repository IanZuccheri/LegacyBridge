package dev.legacybridge.converter.report;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record NamespaceUsage(
        String namespace,
        long definitionCount,
        long referenceCount,
        List<String> definitionSamples,
        List<String> referenceSamples,
        boolean definitionSamplesTruncated,
        boolean referenceSamplesTruncated
) {
    public static final int SAMPLE_LIMIT = 12;
    public static final Set<String> NAMESPACES = Set.of(
            "JAVA", "JAVAX", "MINECRAFT", "MINECRAFT_CLIENT",
            "FORGE", "MODLAUNCHER", "MIXIN", "OTHER"
    );

    public NamespaceUsage {
        namespace = Objects.requireNonNull(namespace, "namespace");
        if (!NAMESPACES.contains(namespace)) {
            throw new IllegalArgumentException("unknown namespace: " + namespace);
        }
        if (definitionCount < 0 || referenceCount < 0) {
            throw new IllegalArgumentException("Namespace counts must not be negative");
        }
        Objects.requireNonNull(definitionSamples, "definitionSamples");
        Objects.requireNonNull(referenceSamples, "referenceSamples");
        if (definitionSamples.size() > SAMPLE_LIMIT || referenceSamples.size() > SAMPLE_LIMIT) {
            throw new IllegalArgumentException("Namespace sample limit exceeded");
        }
        definitionSamples = definitionSamples.stream()
                .map(value -> Objects.requireNonNull(value, "definitionSamples must not contain null"))
                .distinct().sorted().toList();
        referenceSamples = referenceSamples.stream()
                .map(value -> Objects.requireNonNull(value, "referenceSamples must not contain null"))
                .distinct().sorted().toList();
        if (definitionCount < definitionSamples.size() || referenceCount < referenceSamples.size()) {
            throw new IllegalArgumentException("Namespace samples cannot exceed their occurrence counts");
        }
    }
}
