package dev.legacybridge.converter.report;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record MetadataSummary(
        boolean present,
        boolean parsed,
        String modLoader,
        String loaderVersion,
        List<ModDeclaration> mods,
        List<DependencyDeclaration> dependencies
) {
    public MetadataSummary {
        modLoader = Objects.requireNonNullElse(modLoader, "");
        loaderVersion = Objects.requireNonNullElse(loaderVersion, "");
        Objects.requireNonNull(mods, "mods");
        mods = mods.stream()
                .map(value -> Objects.requireNonNull(value, "mods must not contain null"))
                .sorted(Comparator.comparing(ModDeclaration::modId)
                        .thenComparing(ModDeclaration::version)
                        .thenComparing(ModDeclaration::displayName))
                .toList();
        Objects.requireNonNull(dependencies, "dependencies");
        dependencies = dependencies.stream()
                .map(value -> Objects.requireNonNull(value, "dependencies must not contain null"))
                .sorted(Comparator.comparing(DependencyDeclaration::ownerModId)
                        .thenComparing(DependencyDeclaration::modId)
                        .thenComparing(DependencyDeclaration::versionRange))
                .toList();
    }

    public static MetadataSummary missing() {
        return new MetadataSummary(false, false, "", "", List.of(), List.of());
    }

    public static MetadataSummary malformed() {
        return new MetadataSummary(true, false, "", "", List.of(), List.of());
    }
}
