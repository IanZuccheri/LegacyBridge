package dev.legacybridge.converter.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.TreeMap;

/** Only explicitly allowlisted main-section manifest attributes are retained. */
public record ManifestAttributesSummary(
        String path,
        boolean present,
        boolean parsed,
        Map<String, String> attributes,
        boolean valuesTruncated
) {
    public ManifestAttributesSummary {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(attributes, "attributes");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(attributes)));
    }

    static ManifestAttributesSummary absent() {
        return new ManifestAttributesSummary("META-INF/MANIFEST.MF", false, false, Map.of(), false);
    }

    static ManifestAttributesSummary unreadable(String path) {
        return new ManifestAttributesSummary(path, true, false, Map.of(), false);
    }

    /** Returns manifest-declared Mixin configs without reading any referenced entry. */
    public List<String> declaredMixinConfigurationPaths() {
        String value = attributes.get("MixinConfigs");
        if (value == null) {
            return List.of();
        }
        TreeSet<String> paths = new TreeSet<>();
        for (String item : value.split(",")) {
            String path = item.trim();
            if (!path.isEmpty()) {
                paths.add(path);
            }
        }
        return paths.stream()
                .limit(LegacyConfigInventoryCollector.MAX_DECLARATION_SAMPLES)
                .toList();
    }
}
