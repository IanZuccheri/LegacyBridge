package dev.legacybridge.converter.policy;

import dev.legacybridge.converter.report.AnalysisStatus;
import dev.legacybridge.converter.report.Assessment;
import dev.legacybridge.converter.report.BytecodeSummary;
import dev.legacybridge.converter.report.ConversionStatus;
import dev.legacybridge.converter.report.DependencyDeclaration;
import dev.legacybridge.converter.report.ExecutionStatus;
import dev.legacybridge.converter.report.FeatureMarker;
import dev.legacybridge.converter.report.Finding;
import dev.legacybridge.converter.report.MetadataSummary;
import dev.legacybridge.converter.report.NamespaceUsage;
import dev.legacybridge.converter.report.Severity;
import dev.legacybridge.converter.report.SourceProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Conservative analyzer policy: inventory facts now, authorize no conversion. */
public final class ProgressiveSupportPolicy {
    private static final int JAVA_17_CLASS_MAJOR = 61;

    private static final Map<String, MarkerRule> MARKER_RULES = Map.ofEntries(
            Map.entry("SIGNED_JAR", new MarkerRule("LB-F001", Severity.BLOCKER,
                    "Signed JARs cannot be transformed without invalidating their signatures.")),
            Map.entry("ACCESS_TRANSFORMER", new MarkerRule("LB-F002", Severity.BLOCKER,
                    "Access transformers require explicit symbol remapping and are not supported.")),
            Map.entry("MIXIN_CONFIGURATION", new MarkerRule("LB-F003", Severity.BLOCKER,
                    "Mixin configurations target version-specific internals and are not supported.")),
            Map.entry("COREMOD", new MarkerRule("LB-F004", Severity.BLOCKER,
                    "Coremods are outside the initial compatibility subset.")),
            Map.entry("TRANSFORMATION_SERVICE", new MarkerRule("LB-F005", Severity.BLOCKER,
                    "ModLauncher transformation services are outside the initial subset.")),
            Map.entry("NATIVE_LIBRARY", new MarkerRule("LB-F006", Severity.BLOCKER,
                    "Bundled native libraries require platform-specific review.")),
            Map.entry("NATIVE_METHOD", new MarkerRule("LB-F007", Severity.BLOCKER,
                    "Native methods require platform-specific review.")),
            Map.entry("NESTED_JAR", new MarkerRule("LB-F008", Severity.BLOCKER,
                    "Nested JARs are inventoried but not recursively analyzed.")),
            Map.entry("MULTI_RELEASE", new MarkerRule("LB-F009", Severity.ERROR,
                    "Multi-release JAR semantics are not supported by the current analyzer.")),
            Map.entry("MODULE_DESCRIPTOR", new MarkerRule("LB-F010", Severity.ERROR,
                    "Java module descriptors require manual review.")),
            Map.entry("SERVICE_PROVIDER", new MarkerRule("LB-F011", Severity.WARNING,
                    "Service providers are inventoried but never loaded during analysis.")),
            Map.entry("EXTERNAL_CLASS_PATH", new MarkerRule("LB-F012", Severity.BLOCKER,
                    "Manifest Class-Path dependencies are not followed or analyzed.")),
            Map.entry("LEGACY_BOOTSTRAP", new MarkerRule("LB-F013", Severity.BLOCKER,
                    "Legacy LaunchWrapper/bootstrap manifest hooks cannot run on Forge 47."))
    );

    private static final Set<String> RESERVED_DEFINITION_NAMESPACES = Set.of(
            "JAVA", "JAVAX", "MINECRAFT", "MINECRAFT_CLIENT", "FORGE", "MODLAUNCHER", "MIXIN"
    );

    public PolicyResult assess(
            MetadataSummary metadata,
            boolean metadataValid,
            BytecodeSummary bytecode,
            List<FeatureMarker> markers
    ) {
        List<Finding> findings = new ArrayList<>();
        for (FeatureMarker marker : markers) {
            MarkerRule rule = MARKER_RULES.get(marker.id());
            if (rule != null) {
                findings.add(new Finding(
                        rule.code(), rule.severity(), "feature", rule.message(), marker.evidence()
                ));
            }
        }

        if (bytecode.classCount() == 0) {
            findings.add(new Finding(
                    "LB-B001", Severity.INFO, "bytecode",
                    "The archive contains no class files; resource-only conversion is a future tier.",
                    List.of()
            ));
        }
        if (bytecode.unreadableClassCount() > 0) {
            findings.add(new Finding(
                    "LB-B002", Severity.ERROR, "bytecode",
                    "One or more class files could not be parsed statically.",
                    List.of("unreadableClassCount=" + bytecode.unreadableClassCount())
            ));
        }
        List<String> tooNew = bytecode.classMajorVersions().keySet().stream()
                .filter(major -> major > JAVA_17_CLASS_MAJOR)
                .map(major -> "classMajor=" + major)
                .toList();
        if (!tooNew.isEmpty()) {
            findings.add(new Finding(
                    "LB-B003", Severity.BLOCKER, "bytecode",
                    "Class files newer than Java 17 cannot run on the target JVM.", tooNew
            ));
        }
        if (bytecode.previewClassCount() > 0) {
            findings.add(new Finding(
                    "LB-B004", Severity.BLOCKER, "bytecode",
                    "Preview class files are not accepted for the Java 17 runtime target.",
                    List.of("previewClassCount=" + bytecode.previewClassCount())
            ));
        }

        List<String> reservedDefinitions = bytecode.namespaces().stream()
                .filter(usage -> RESERVED_DEFINITION_NAMESPACES.contains(usage.namespace()))
                .filter(usage -> usage.definitionCount() > 0)
                .flatMap(usage -> usage.definitionSamples().stream())
                .sorted()
                .limit(20)
                .toList();
        if (!reservedDefinitions.isEmpty()) {
            findings.add(new Finding(
                    "LB-B005", Severity.BLOCKER, "namespace",
                    "The input defines classes inside platform-owned namespaces.", reservedDefinitions
            ));
        }

        bytecode.namespaces().stream()
                .filter(usage -> "MINECRAFT_CLIENT".equals(usage.namespace()))
                .filter(usage -> usage.referenceCount() > 0)
                .findFirst()
                .ifPresent(usage -> findings.add(new Finding(
                        "LB-B006", Severity.WARNING, "sides",
                        "References to net.minecraft.client require physical-side review.",
                        usage.referenceSamples()
                )));

        SourceProfile sourceProfile = sourceProfile(metadata, metadataValid, bytecode);
        if (sourceProfile == SourceProfile.FORGE_1_16_5_CANDIDATE) {
            findings.add(new Finding(
                    "LB-P001", Severity.INFO, "profile",
                    "Metadata and bytecode are consistent with a Forge 1.16.5 candidate; this is not proof of compatibility.",
                    List.of("loaderVersionIntersectsMajor=36", "minecraftDependency=1.16.5", "javaClassMajor<=52")
            ));
        } else {
            findings.add(new Finding(
                    "LB-P002", Severity.WARNING, "profile",
                    "The archive could not be classified as a Forge 1.16.5 candidate.", List.of()
            ));
        }

        findings.add(new Finding(
                "LB-P003", Severity.INFO, "conversion",
                "Conversion is intentionally not implemented in report schema v2.", List.of()
        ));

        AnalysisStatus analysisStatus = (!metadata.parsed() || bytecode.unreadableClassCount() > 0)
                ? AnalysisStatus.PARTIAL
                : AnalysisStatus.COMPLETE;
        return new PolicyResult(
                new Assessment(
                        analysisStatus,
                        sourceProfile,
                        ConversionStatus.NOT_IMPLEMENTED,
                        ExecutionStatus.NOT_TESTED
                ),
                findings
        );
    }

    private static SourceProfile sourceProfile(
            MetadataSummary metadata,
            boolean metadataValid,
            BytecodeSummary bytecode
    ) {
        if (!metadata.parsed() || !"javafml".equals(metadata.modLoader())) {
            return metadata.parsed() ? SourceProfile.OTHER : SourceProfile.AMBIGUOUS;
        }
        if (!metadataValid || bytecode.unreadableClassCount() > 0) {
            return SourceProfile.AMBIGUOUS;
        }
        Set<String> declaredModIds = metadata.mods().stream()
                .map(mod -> mod.modId().toLowerCase(Locale.ROOT))
                .filter(modId -> !modId.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean loaderAcceptsForge36 = NumericVersionRange.intersectsMajor(metadata.loaderVersion(), 36);
        boolean sourceDependenciesMatch = loaderAcceptsForge36
                && declaredModIds.stream().anyMatch(ownerModId -> mandatoryDependencyAccepts(
                        metadata.dependencies(), ownerModId, "minecraft", "1.16.5"));
        boolean java8OrResourcesOnly = bytecode.classMajorVersions().isEmpty()
                || bytecode.classMajorVersions().lastKey() <= 52;
        if (sourceDependenciesMatch && java8OrResourcesOnly) {
            return SourceProfile.FORGE_1_16_5_CANDIDATE;
        }
        return SourceProfile.AMBIGUOUS;
    }

    private static boolean mandatoryDependencyAccepts(
            List<DependencyDeclaration> dependencies,
            String ownerModId,
            String modId,
            String targetVersion
    ) {
        return dependencies.stream().anyMatch(dependency ->
                dependency.mandatoryDeclared()
                        && dependency.mandatory()
                        && ownerModId.equals(dependency.ownerModId().toLowerCase(Locale.ROOT))
                        && modId.equals(dependency.modId().toLowerCase(Locale.ROOT))
                        && NumericVersionRange.contains(dependency.versionRange(), targetVersion));
    }

    private record MarkerRule(String code, Severity severity, String message) {
    }
}
