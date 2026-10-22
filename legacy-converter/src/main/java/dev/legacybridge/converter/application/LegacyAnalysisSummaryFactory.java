package dev.legacybridge.converter.application;

import dev.legacybridge.converter.analysis.DependencyAnalyzer;
import dev.legacybridge.converter.analysis.LegacyFeatureUsageCollector;
import dev.legacybridge.converter.analysis.PackageUsageAnalyzer;
import dev.legacybridge.converter.analysis.PlatformReferenceCollector;
import dev.legacybridge.converter.analysis.RiskScoreCalculator;
import dev.legacybridge.converter.analysis.SourceVersionDetector;
import dev.legacybridge.converter.bytecode.BytecodeScanAggregate;
import dev.legacybridge.converter.bytecode.ArchiveReferenceInventory;
import dev.legacybridge.converter.bytecode.BytecodeDefinitionIndex;
import dev.legacybridge.converter.config.AccessTransformerSummary;
import dev.legacybridge.converter.config.ArtifactDiagnostic;
import dev.legacybridge.converter.config.CoremodConfigurationSummary;
import dev.legacybridge.converter.config.LegacyConfigInventory;
import dev.legacybridge.converter.config.MixinConfigurationSummary;
import dev.legacybridge.converter.content.ContentInventory;
import dev.legacybridge.converter.metadata.ForgeMetadataAnalyzer;
import dev.legacybridge.converter.report.BytecodeSummary;
import dev.legacybridge.converter.report.LegacyAnalysisSummary;
import dev.legacybridge.converter.report.MetadataSummary;
import dev.legacybridge.converter.report.ManifestSummary;
import dev.legacybridge.converter.report.ModDeclaration;
import dev.legacybridge.converter.report.StaticArtifact;
import dev.legacybridge.converter.report.UnsupportedFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Builds the schema-v2 facade from independently collected static facts. */
public final class LegacyAnalysisSummaryFactory {
    private final DependencyAnalyzer dependencyAnalyzer = new DependencyAnalyzer();
    private final PackageUsageAnalyzer packageUsageAnalyzer = new PackageUsageAnalyzer();
    private final SourceVersionDetector versionDetector = new SourceVersionDetector();
    private final RiskScoreCalculator riskScoreCalculator = new RiskScoreCalculator();

    public LegacyAnalysisSummary create(
            MetadataSummary metadata,
            boolean metadataValid,
            BytecodeSummary bytecode,
            BytecodeScanAggregate aggregate,
            ContentInventory content,
            LegacyConfigInventory config,
            PlatformReferenceCollector platformReferences,
            LegacyFeatureUsageCollector featureUsages
    ) {
        var referenceInventory = withUnreadableDefinitionGap(
                aggregate.referenceInventory(), bytecode.unreadableClassCount());
        var dependencies = dependencyAnalyzer.analyze(
                metadata,
                referenceInventory,
                config.manifest().attributes()
        );
        var packages = packageUsageAnalyzer.analyze(referenceInventory);
        List<UnsupportedFeature> features = new ArrayList<>(featureUsages.snapshot());
        appendMetadataFeatures(metadata, metadataValid, features);
        var risk = riskScoreCalculator.calculate(features, dependencies.dependencies(), bytecode.classCount());

        ModDeclaration primary = metadata.mods().stream()
                .filter(mod -> ForgeMetadataAnalyzer.isValidModId(mod.modId()))
                .findFirst().orElse(null);
        String modId = primary == null ? "UNKNOWN" : primary.modId();
        String modVersion = primary == null ? "UNKNOWN" : resolvedVersion(
                primary, config.manifest().attributes()
        );

        return new LegacyAnalysisSummary(
                modId,
                modVersion,
                versionDetector.minecraftVersion(metadata, metadataValid),
                versionDetector.forgeVersion(metadata, metadataValid),
                bytecode.classCount(),
                platformReferences.minecraft(referenceInventory.definitions()),
                platformReferences.forge(referenceInventory.definitions()),
                dependencies.dependencies(),
                dependencies.truncated() || config.manifest().valuesTruncated()
                        || bytecode.unreadableClassCount() > 0,
                mixinArtifacts(config),
                accessTransformerArtifacts(config),
                coremodArtifacts(config),
                features,
                risk.riskScore(),
                risk.complexity(),
                packages.packages(),
                packages.truncated() || bytecode.unreadableClassCount() > 0,
                referenceInventory,
                content,
                new ManifestSummary(
                        config.manifest().present(),
                        config.manifest().parsed(),
                        config.manifest().attributes(),
                        config.manifest().valuesTruncated()
                )
        );
    }

    private static void appendMetadataFeatures(
            MetadataSummary metadata,
            boolean metadataValid,
            List<UnsupportedFeature> features
    ) {
        boolean validIdentity = metadata.mods().stream()
                .anyMatch(mod -> ForgeMetadataAnalyzer.isValidModId(mod.modId()));
        if (metadata.parsed() && !metadata.modLoader().isBlank()
                && !"javafml".equals(metadata.modLoader())) {
            features.add(new UnsupportedFeature(
                    dev.legacybridge.converter.analysis.LegacyFeatureId.UNSUPPORTED_LOADER.name(),
                    1,
                    List.of("modLoader=" + metadata.modLoader()),
                    false
            ));
        } else if (!metadataValid || !metadata.present() || !metadata.parsed() || !validIdentity
                || metadata.loaderVersion().isBlank()) {
            features.add(new UnsupportedFeature(
                    dev.legacybridge.converter.analysis.LegacyFeatureId.METADATA_UNCERTAINTY.name(),
                    1,
                    List.of("META-INF/mods.toml"),
                    false
            ));
        }
    }

    private static String resolvedVersion(ModDeclaration mod, Map<String, String> manifest) {
        String version = mod.version();
        if ("${file.jarVersion}".equals(version)) {
            return manifest.getOrDefault("Implementation-Version", "UNKNOWN");
        }
        return version.isBlank() ? "UNKNOWN" : version;
    }

    private static List<StaticArtifact> mixinArtifacts(LegacyConfigInventory config) {
        List<StaticArtifact> artifacts = new ArrayList<>(config.mixins().stream().map(mixin -> {
            Map<String, List<String>> attributes = new TreeMap<>();
            put(attributes, "package", mixin.mixinPackage());
            put(attributes, "plugin", mixin.plugin());
            put(attributes, "required", mixin.required() == null ? null : mixin.required().toString());
            put(attributes, "compatibilityLevel", mixin.compatibilityLevel());
            attributes.put("mixins", mixin.mixins());
            attributes.put("client", mixin.client());
            attributes.put("server", mixin.server());
            long count = (long) mixin.declaredMixinCount()
                    + mixin.declaredClientCount() + mixin.declaredServerCount();
            return new StaticArtifact(
                    mixin.path(), mixin.parsed(), count, attributes,
                    diagnostics(config, mixin.path()), mixin.samplesTruncated(), mixin.partial()
            );
        }).toList());
        var parsedPaths = config.mixins().stream().map(MixinConfigurationSummary::path)
                .collect(java.util.stream.Collectors.toSet());
        for (String declaredPath : config.manifest().declaredMixinConfigurationPaths()) {
            if (!parsedPaths.contains(declaredPath)) {
                artifacts.add(new StaticArtifact(
                        declaredPath,
                        false,
                        0,
                        Map.of("declaredBy", List.of("META-INF/MANIFEST.MF#MixinConfigs")),
                        List.of("Declared Mixin config was not found or could not be captured."),
                        false,
                        true
                ));
            }
        }
        return List.copyOf(artifacts);
    }

    private static List<StaticArtifact> accessTransformerArtifacts(LegacyConfigInventory config) {
        return config.accessTransformers().stream().map(at -> new StaticArtifact(
                at.path(), at.parsed(), at.ruleCount(),
                Map.of("rules", at.normalizedRuleSamples()),
                diagnostics(config, at.path()), at.samplesTruncated(), !at.parsed()
        )).toList();
    }

    private static List<StaticArtifact> coremodArtifacts(LegacyConfigInventory config) {
        List<StaticArtifact> artifacts = new ArrayList<>(config.coremods().stream().map(coremod -> {
            List<String> declarations = coremod.coremods().stream()
                    .map(value -> value.scriptPath() == null
                            ? value.name()
                            : value.name() + "=" + value.scriptPath())
                    .toList();
            return new StaticArtifact(
                    coremod.path(), coremod.parsed(), coremod.declaredCoremodCount(),
                    Map.of("declarations", declarations),
                    diagnostics(config, coremod.path()), coremod.samplesTruncated(), coremod.partial()
            );
        }).toList());
        String manifestCorePlugin = config.manifest().attributes().get("FMLCorePlugin");
        if (manifestCorePlugin != null && !manifestCorePlugin.isBlank()) {
            artifacts.add(new StaticArtifact(
                    "META-INF/MANIFEST.MF#FMLCorePlugin",
                    true,
                    1,
                    Map.of("class", List.of(manifestCorePlugin)),
                    List.of(),
                    false,
                    false
            ));
        }
        String tweakClass = config.manifest().attributes().get("TweakClass");
        if (tweakClass != null && !tweakClass.isBlank()) {
            artifacts.add(new StaticArtifact(
                    "META-INF/MANIFEST.MF#TweakClass",
                    true,
                    1,
                    Map.of("class", List.of(tweakClass)),
                    List.of(),
                    false,
                    false
            ));
        }
        if ("true".equalsIgnoreCase(config.manifest().attributes().get("ForceLoadAsMod"))) {
            artifacts.add(new StaticArtifact(
                    "META-INF/MANIFEST.MF#ForceLoadAsMod",
                    true,
                    1,
                    Map.of("value", List.of("true")),
                    List.of(),
                    false,
                    false
            ));
        }
        return List.copyOf(artifacts);
    }

    private static List<String> diagnostics(LegacyConfigInventory config, String path) {
        List<String> values = new ArrayList<>();
        for (ArtifactDiagnostic diagnostic : config.diagnostics()) {
            if (diagnostic.path().equals(path)) {
                values.add(diagnostic.code() + ": " + diagnostic.message());
            }
        }
        return List.copyOf(values);
    }

    private static void put(Map<String, List<String>> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, List.of(value));
    }

    private static ArchiveReferenceInventory withUnreadableDefinitionGap(
            ArchiveReferenceInventory inventory,
            int unreadableClassCount
    ) {
        if (unreadableClassCount == 0) return inventory;
        BytecodeDefinitionIndex definitions = inventory.definitions();
        return new ArchiveReferenceInventory(
                inventory.references(),
                new BytecodeDefinitionIndex(
                        definitions.occurrences(),
                        definitions.owners(),
                        true
                ),
                inventory.classDetails(),
                inventory.classDetailsTruncated()
        );
    }
}
