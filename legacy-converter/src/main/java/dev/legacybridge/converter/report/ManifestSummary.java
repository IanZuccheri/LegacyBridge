package dev.legacybridge.converter.report;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Allowlisted, inert facts from the manifest main section. */
public record ManifestSummary(
        boolean present,
        boolean parsed,
        Map<String, String> attributes,
        boolean valuesTruncated
) {
    public static final int MAX_VALUE_CHARS = 4096;
    public static final Set<String> ALLOWED_ATTRIBUTES = Set.of(
            "Automatic-Module-Name",
            "Class-Path",
            "ContainedDeps",
            "FMLCorePlugin",
            "FMLCorePluginContainsFMLMod",
            "ForceLoadAsMod",
            "Implementation-Title",
            "Implementation-Version",
            "MixinConfigs",
            "TweakClass"
    );
    public static final ManifestSummary ABSENT = new ManifestSummary(false, false, Map.of(), false);

    public ManifestSummary {
        Objects.requireNonNull(attributes, "attributes");
        TreeMap<String, String> copy = new TreeMap<>();
        attributes.forEach((key, value) -> {
            String checkedKey = Objects.requireNonNull(key, "manifest attribute key");
            String checkedValue = Objects.requireNonNull(value, "manifest attribute value");
            if (!ALLOWED_ATTRIBUTES.contains(checkedKey)) {
                throw new IllegalArgumentException("manifest attribute is not allowlisted: " + checkedKey);
            }
            if (checkedValue.length() > MAX_VALUE_CHARS) {
                throw new IllegalArgumentException("manifest attribute value exceeds the report limit");
            }
            copy.put(checkedKey, checkedValue);
        });
        attributes = Collections.unmodifiableMap(copy);
        if (!present && parsed) throw new IllegalArgumentException("an absent manifest cannot be parsed");
        if (!parsed && !attributes.isEmpty()) throw new IllegalArgumentException("unparsed manifest has no attributes");
        if (!present && valuesTruncated) {
            throw new IllegalArgumentException("an absent manifest cannot have truncated values");
        }
    }
}
