package dev.legacybridge.converter.metadata;

import dev.legacybridge.converter.report.FeatureMarker;
import dev.legacybridge.converter.report.Finding;
import dev.legacybridge.converter.report.Severity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/** Reads inert manifest attributes only; declared classes and class paths are never followed. */
public final class ManifestAnalyzer {
    public ManifestAnalysis analyze(byte[] bytes) {
        if (bytes == null) {
            return new ManifestAnalysis(List.of(), List.of());
        }
        try {
            Attributes attributes = new Manifest(new ByteArrayInputStream(bytes)).getMainAttributes();
            List<FeatureMarker> markers = new ArrayList<>();
            addWhenPresent(markers, attributes, "FMLCorePlugin", "COREMOD");
            addWhenPresent(markers, attributes, "ContainedDeps", "NESTED_JAR");
            addWhenPresent(markers, attributes, "Class-Path", "EXTERNAL_CLASS_PATH");
            addWhenPresent(markers, attributes, "TweakClass", "LEGACY_BOOTSTRAP");
            addWhenTrue(markers, attributes, "ForceLoadAsMod", "LEGACY_BOOTSTRAP");
            return new ManifestAnalysis(markers, List.of());
        } catch (IOException exception) {
            return new ManifestAnalysis(
                    List.of(),
                    List.of(new Finding(
                            "LB-MAN001", Severity.WARNING, "manifest",
                            "META-INF/MANIFEST.MF could not be parsed.",
                            List.of("META-INF/MANIFEST.MF")
                    ))
            );
        }
    }

    private static void addWhenPresent(
            List<FeatureMarker> markers,
            Attributes attributes,
            String attribute,
            String markerId
    ) {
        String value = attributes.getValue(attribute);
        if (value != null && !value.isBlank()) {
            markers.add(new FeatureMarker(
                    markerId,
                    List.of("META-INF/MANIFEST.MF#" + attribute)
            ));
        }
    }

    private static void addWhenTrue(
            List<FeatureMarker> markers,
            Attributes attributes,
            String attribute,
            String markerId
    ) {
        if ("true".equalsIgnoreCase(attributes.getValue(attribute))) {
            markers.add(new FeatureMarker(
                    markerId,
                    List.of("META-INF/MANIFEST.MF#" + attribute)
            ));
        }
    }
}
