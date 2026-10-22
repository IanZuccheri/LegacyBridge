package dev.legacybridge.converter.config;

import java.util.List;
import java.util.Objects;

/** Declarative facts from one Mixin JSON configuration. */
public record MixinConfigurationSummary(
        String path,
        boolean parsed,
        String mixinPackage,
        String plugin,
        Boolean required,
        String compatibilityLevel,
        int declaredMixinCount,
        List<String> mixins,
        int declaredClientCount,
        List<String> client,
        int declaredServerCount,
        List<String> server,
        boolean samplesTruncated,
        boolean partial
) {
    public MixinConfigurationSummary {
        path = requirePath(path);
        mixins = List.copyOf(mixins);
        client = List.copyOf(client);
        server = List.copyOf(server);
        if (declaredMixinCount < 0 || declaredClientCount < 0 || declaredServerCount < 0) {
            throw new IllegalArgumentException("declared counts must be non-negative");
        }
    }

    static MixinConfigurationSummary unreadable(String path) {
        return new MixinConfigurationSummary(
                path, false, null, null, null, null,
                0, List.of(), 0, List.of(), 0, List.of(), false, false
        );
    }

    private static String requirePath(String value) {
        Objects.requireNonNull(value, "path");
        if (value.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        return value;
    }
}
