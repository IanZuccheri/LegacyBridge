package dev.legacybridge.converter.analysis;

import dev.legacybridge.converter.bytecode.ArchiveReferenceInventory;
import dev.legacybridge.converter.bytecode.BytecodeReference;
import dev.legacybridge.converter.report.DependencyDeclaration;
import dev.legacybridge.converter.report.MetadataSummary;
import dev.legacybridge.converter.report.ThirdPartyDependency;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Combines exact metadata declarations with explicitly-labelled package inference. */
public final class DependencyAnalyzer {
    public static final int INFERRED_PACKAGE_LIMIT = 128;
    public static final int MANIFEST_DEPENDENCY_LIMIT = 256;

    public Result analyze(MetadataSummary metadata, ArchiveReferenceInventory references) {
        return analyze(metadata, references, Map.of());
    }

    public Result analyze(
            MetadataSummary metadata,
            ArchiveReferenceInventory references,
            Map<String, String> manifestAttributes
    ) {
        Set<String> ownModIds = new HashSet<>();
        metadata.mods().forEach(mod -> ownModIds.add(mod.modId().toLowerCase(Locale.ROOT)));

        List<ThirdPartyDependency> result = new ArrayList<>();
        Set<String> declaredKeys = new HashSet<>();
        for (DependencyDeclaration dependency : metadata.dependencies()) {
            String id = dependency.modId().toLowerCase(Locale.ROOT);
            if (id.isBlank() || id.equals("minecraft") || id.equals("forge") || ownModIds.contains(id)) {
                continue;
            }
            String key = id + "\u0000" + dependency.mandatory() + "\u0000" + dependency.versionRange();
            if (declaredKeys.add(key)) {
                result.add(new ThirdPartyDependency(
                        id,
                        "DECLARED_MOD",
                        dependency.mandatoryDeclared() && dependency.mandatory(),
                        dependency.versionRange()
                ));
            }
        }

        ManifestDependencies manifestDependencies = manifestDependencies(manifestAttributes);
        result.addAll(manifestDependencies.dependencies());

        TreeSet<String> inferred = new TreeSet<>();
        boolean truncated = !references.externalSamplesComplete() || manifestDependencies.truncated();
        for (BytecodeReference reference : references.externalSamples()) {
            if (!isThirdPartyOwner(reference.owner())) continue;
            String packageName = packageName(reference.owner());
            if (!packageName.isBlank()) {
                inferred.add(packageName);
                if (inferred.size() > INFERRED_PACKAGE_LIMIT) {
                    inferred.pollLast();
                    truncated = true;
                }
            }
        }
        for (String packageName : inferred) {
            result.add(new ThirdPartyDependency(packageName, "INFERRED_PACKAGE", false, ""));
        }
        return new Result(result, truncated);
    }

    private static ManifestDependencies manifestDependencies(Map<String, String> attributes) {
        TreeSet<ThirdPartyDependency> dependencies = new TreeSet<>(
                java.util.Comparator.comparing(ThirdPartyDependency::source)
                        .thenComparing(ThirdPartyDependency::identifier)
        );
        collectManifestAttribute(
                attributes.get("Class-Path"),
                "MANIFEST_CLASS_PATH",
                "\\s+",
                dependencies
        );
        collectManifestAttribute(
                attributes.get("ContainedDeps"),
                "MANIFEST_CONTAINED_DEPS",
                "[\\s,]+",
                dependencies
        );
        boolean truncated = dependencies.size() > MANIFEST_DEPENDENCY_LIMIT;
        return new ManifestDependencies(
                dependencies.stream().limit(MANIFEST_DEPENDENCY_LIMIT).toList(),
                truncated
        );
    }

    private static void collectManifestAttribute(
            String value,
            String source,
            String separator,
            Set<ThirdPartyDependency> target
    ) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String item : value.trim().split(separator)) {
            String identifier = item.trim();
            if (!identifier.isEmpty()) {
                target.add(new ThirdPartyDependency(identifier, source, false, ""));
            }
        }
    }

    private static boolean isThirdPartyOwner(String owner) {
        return !owner.startsWith("java/")
                && !owner.startsWith("javax/")
                && !owner.startsWith("jdk/")
                && !owner.startsWith("sun/")
                && !owner.startsWith("net/minecraft/")
                && !owner.startsWith("com/mojang/")
                && !owner.startsWith("net/minecraftforge/")
                && !owner.startsWith("cpw/mods/")
                && !owner.startsWith("org/spongepowered/asm/");
    }

    private static String packageName(String owner) {
        int separator = owner.lastIndexOf('/');
        return separator <= 0 ? "" : owner.substring(0, separator).replace('/', '.');
    }

    public record Result(List<ThirdPartyDependency> dependencies, boolean truncated) {
        public Result {
            dependencies = List.copyOf(dependencies);
        }
    }

    private record ManifestDependencies(List<ThirdPartyDependency> dependencies, boolean truncated) {
    }
}
