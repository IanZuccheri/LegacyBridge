package dev.legacybridge.converter.report;

import dev.legacybridge.converter.bytecode.ArchiveReferenceInventory;
import dev.legacybridge.converter.content.ContentCategorySummary;
import dev.legacybridge.converter.content.ContentInventory;
import dev.legacybridge.converter.content.ContentNamespaceSummary;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Schema-v2 analyzer facts requested by the Legacy Mod Analyzer CLI. */
public record LegacyAnalysisSummary(
        String modId,
        String modVersion,
        String detectedMinecraftVersion,
        String detectedForgeVersion,
        int classCount,
        ReferenceSummary minecraftReferences,
        ReferenceSummary forgeReferences,
        List<ThirdPartyDependency> thirdPartyDependencies,
        boolean thirdPartyDependenciesTruncated,
        List<StaticArtifact> mixins,
        List<StaticArtifact> accessTransformers,
        List<StaticArtifact> coremods,
        List<UnsupportedFeature> unsupportedFeatures,
        int riskScore,
        Complexity complexity,
        List<String> packagesUsed,
        boolean packagesUsedTruncated,
        ArchiveReferenceInventory classReferences,
        ContentInventory content,
        ManifestSummary manifest
) {
    public static final int PACKAGE_LIMIT = 256;
    public static final int CONTENT_SAMPLE_LIMIT = 16;

    private static final Set<String> MINECRAFT_VERSIONS = Set.of("1.16.5", "UNKNOWN");
    private static final Set<String> FORGE_VERSIONS = Set.of("36.x", "UNKNOWN");

    public static LegacyAnalysisSummary empty() {
        return new LegacyAnalysisSummary(
                "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", 0,
                ReferenceSummary.EMPTY, ReferenceSummary.EMPTY,
                List.of(), false, List.of(), List.of(), List.of(), List.of(),
                0, Complexity.SIMPLE, List.of(), false,
                ArchiveReferenceInventory.EMPTY, ContentInventory.empty(), ManifestSummary.ABSENT
        );
    }

    public LegacyAnalysisSummary {
        modId = nonBlankOrUnknown(modId);
        modVersion = nonBlankOrUnknown(modVersion);
        detectedMinecraftVersion = knownVersion(
                detectedMinecraftVersion, MINECRAFT_VERSIONS, "detectedMinecraftVersion");
        detectedForgeVersion = knownVersion(
                detectedForgeVersion, FORGE_VERSIONS, "detectedForgeVersion");
        if (classCount < 0) throw new IllegalArgumentException("classCount must not be negative");
        Objects.requireNonNull(minecraftReferences, "minecraftReferences");
        Objects.requireNonNull(forgeReferences, "forgeReferences");
        Objects.requireNonNull(thirdPartyDependencies, "thirdPartyDependencies");
        thirdPartyDependencies = thirdPartyDependencies.stream()
                .map(value -> Objects.requireNonNull(
                        value, "thirdPartyDependencies must not contain null"))
                .sorted(Comparator.comparing(ThirdPartyDependency::source)
                        .thenComparing(ThirdPartyDependency::identifier)
                .thenComparing(ThirdPartyDependency::versionRange))
                .toList();
        mixins = sortedArtifacts(mixins, "mixins");
        accessTransformers = sortedArtifacts(accessTransformers, "accessTransformers");
        coremods = sortedArtifacts(coremods, "coremods");
        Objects.requireNonNull(unsupportedFeatures, "unsupportedFeatures");
        unsupportedFeatures = unsupportedFeatures.stream()
                .map(value -> Objects.requireNonNull(
                        value, "unsupportedFeatures must not contain null"))
                .sorted(Comparator.comparing(UnsupportedFeature::id)).toList();
        if (riskScore < 0 || riskScore > 100) throw new IllegalArgumentException("riskScore must be 0..100");
        complexity = Objects.requireNonNull(complexity, "complexity");
        Objects.requireNonNull(packagesUsed, "packagesUsed");
        packagesUsed = packagesUsed.stream()
                .map(value -> requireNonBlank(value, "packagesUsed must not contain blank values"))
                .distinct().sorted().toList();
        if (packagesUsed.size() > PACKAGE_LIMIT) {
            throw new IllegalArgumentException("packagesUsed sample limit exceeded");
        }
        Objects.requireNonNull(classReferences, "classReferences");
        Objects.requireNonNull(content, "content");
        validateContentSamples(content);
        Objects.requireNonNull(manifest, "manifest");
    }

    private static List<StaticArtifact> sortedArtifacts(List<StaticArtifact> values, String name) {
        Objects.requireNonNull(values, name);
        return values.stream()
                .map(value -> Objects.requireNonNull(value, name + " must not contain null"))
                .sorted(Comparator.comparing(StaticArtifact::path))
                .toList();
    }

    private static String nonBlankOrUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private static String knownVersion(String value, Set<String> allowed, String name) {
        String normalized = Objects.requireNonNullElse(value, "UNKNOWN");
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(name + " has an unsupported report value: " + normalized);
        }
        return normalized;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static void validateContentSamples(ContentInventory inventory) {
        validateContentNamespace(inventory.assetNamespaces(), "assetNamespaces");
        validateContentNamespace(inventory.dataNamespaces(), "dataNamespaces");
        for (ContentCategorySummary category : inventory.categories().values()) {
            if (category.samples().size() > CONTENT_SAMPLE_LIMIT) {
                throw new IllegalArgumentException("content category sample limit exceeded");
            }
        }
    }

    private static void validateContentNamespace(ContentNamespaceSummary summary, String name) {
        if (summary.samples().size() > CONTENT_SAMPLE_LIMIT) {
            throw new IllegalArgumentException(name + " sample limit exceeded");
        }
    }
}
