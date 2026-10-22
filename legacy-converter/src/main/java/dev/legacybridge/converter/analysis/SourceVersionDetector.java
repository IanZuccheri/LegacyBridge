package dev.legacybridge.converter.analysis;

import dev.legacybridge.converter.policy.NumericVersionRange;
import dev.legacybridge.converter.report.DependencyDeclaration;
import dev.legacybridge.converter.report.MetadataSummary;

import java.util.Locale;

/** Conservative baseline detection; range acceptance is evidence, never an exact build fingerprint. */
public final class SourceVersionDetector {
    public static final String UNKNOWN = "UNKNOWN";

    public String minecraftVersion(MetadataSummary metadata, boolean metadataValid) {
        if (!metadataValid) {
            return UNKNOWN;
        }
        return metadata.dependencies().stream()
                .filter(dependency -> "minecraft".equals(lower(dependency.modId())))
                .filter(DependencyDeclaration::mandatoryDeclared)
                .filter(DependencyDeclaration::mandatory)
                .map(DependencyDeclaration::versionRange)
                .anyMatch(range -> NumericVersionRange.contains(range, "1.16.5"))
                ? "1.16.5" : UNKNOWN;
    }

    public String forgeVersion(MetadataSummary metadata, boolean metadataValid) {
        if (!metadataValid) {
            return UNKNOWN;
        }
        boolean loaderAccepts = "javafml".equals(lower(metadata.modLoader()))
                && NumericVersionRange.intersectsMajor(metadata.loaderVersion(), 36);
        boolean forgeDependencyAccepts = metadata.dependencies().stream()
                .filter(dependency -> "forge".equals(lower(dependency.modId())))
                .filter(DependencyDeclaration::mandatoryDeclared)
                .filter(DependencyDeclaration::mandatory)
                .map(DependencyDeclaration::versionRange)
                .anyMatch(range -> NumericVersionRange.intersectsMajor(range, 36));
        return loaderAccepts || forgeDependencyAccepts ? "36.x" : UNKNOWN;
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
