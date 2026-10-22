package dev.legacybridge.converter.config;

import java.util.List;
import java.util.Objects;

/** Immutable aggregate of legacy loader configuration artifacts. */
public record LegacyConfigInventory(
        List<MixinConfigurationSummary> mixins,
        List<AccessTransformerSummary> accessTransformers,
        List<CoremodConfigurationSummary> coremods,
        ManifestAttributesSummary manifest,
        List<ArtifactDiagnostic> diagnostics
) {
    public LegacyConfigInventory {
        mixins = List.copyOf(mixins);
        accessTransformers = List.copyOf(accessTransformers);
        coremods = List.copyOf(coremods);
        manifest = Objects.requireNonNull(manifest, "manifest");
        diagnostics = List.copyOf(diagnostics);
    }
}
