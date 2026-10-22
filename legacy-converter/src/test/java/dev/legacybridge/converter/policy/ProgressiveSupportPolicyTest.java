package dev.legacybridge.converter.policy;

import dev.legacybridge.converter.report.BytecodeSummary;
import dev.legacybridge.converter.report.DependencyDeclaration;
import dev.legacybridge.converter.report.MetadataSummary;
import dev.legacybridge.converter.report.ModDeclaration;
import dev.legacybridge.converter.report.SourceProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressiveSupportPolicyTest {
    @Test
    void candidateRequiresMandatoryMinecraftRangeOwnedByDeclaredMod() {
        assertEquals(SourceProfile.FORGE_1_16_5_CANDIDATE,
                assess(metadata("example", true, "[1.16,1.17)")));
        assertEquals(SourceProfile.AMBIGUOUS,
                assess(metadata("other", true, "[1.16,1.17)")));
        assertEquals(SourceProfile.AMBIGUOUS,
                assess(metadata("example", false, "[1.16,1.17)")));
    }

    @Test
    void substringLookalikeDoesNotBecomeCandidate() {
        assertEquals(SourceProfile.AMBIGUOUS,
                assess(metadata("example", true, "[1.16.50,)")));
    }

    @Test
    void forgeDependencyCannotSubstituteForLoaderVersionThatExcludesForge36() {
        MetadataSummary metadata = new MetadataSummary(
                true,
                true,
                "javafml",
                "[47,48)",
                List.of(new ModDeclaration("example", "1.0.0", "Example")),
                List.of(
                        dependency("example", "forge", true, "[36,)"),
                        dependency("example", "minecraft", true, "[1.16.5,1.17)")
                )
        );

        assertEquals(SourceProfile.AMBIGUOUS, assess(metadata));
    }

    @Test
    void invalidMetadataOrAnyUnreadableClassCannotBeCandidate() {
        MetadataSummary metadata = metadata("example", true, "[1.16.5,1.17)");
        BytecodeSummary unreadable = new BytecodeSummary(1, 1, 0, 0, new TreeMap<>(java.util.Map.of(52, 1)), List.of());

        assertEquals(SourceProfile.AMBIGUOUS, new ProgressiveSupportPolicy()
                .assess(metadata, false, BytecodeSummary.empty(), List.of())
                .assessment().sourceProfile());
        assertEquals(SourceProfile.AMBIGUOUS, new ProgressiveSupportPolicy()
                .assess(metadata, true, unreadable, List.of())
                .assessment().sourceProfile());
    }

    private static SourceProfile assess(MetadataSummary metadata) {
        return new ProgressiveSupportPolicy()
                .assess(metadata, true, BytecodeSummary.empty(), List.of())
                .assessment()
                .sourceProfile();
    }

    private static MetadataSummary metadata(String owner, boolean mandatory, String minecraftRange) {
        return new MetadataSummary(
                true,
                true,
                "javafml",
                "[36,)",
                List.of(new ModDeclaration("example", "1.0.0", "Example")),
                List.of(new DependencyDeclaration(
                        owner,
                        "minecraft",
                        true,
                        mandatory,
                        minecraftRange,
                        "NONE",
                        "BOTH"
                ))
        );
    }

    private static DependencyDeclaration dependency(
            String owner,
            String modId,
            boolean mandatory,
            String versionRange
    ) {
        return new DependencyDeclaration(owner, modId, true, mandatory, versionRange, "NONE", "BOTH");
    }
}
