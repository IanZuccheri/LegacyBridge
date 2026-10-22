package dev.legacybridge.converter.policy;

import dev.legacybridge.converter.report.ArchiveSummary;
import dev.legacybridge.converter.report.BytecodeSummary;
import dev.legacybridge.converter.report.FeatureMarker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class FeatureMarkerDetector {
    public List<FeatureMarker> detect(ArchiveSummary archive, BytecodeSummary bytecode) {
        Map<String, List<String>> evidenceByMarker = new TreeMap<>();
        Map<String, Long> occurrencesByMarker = archiveMarkerCounts(archive);
        for (String entry : archive.specialEntries()) {
            String lower = entry.toLowerCase(Locale.ROOT);
            if (isSignature(entry)) {
                add(evidenceByMarker, "SIGNED_JAR", entry);
            }
            if (entry.equals("META-INF/services/cpw.mods.modlauncher.api.ITransformationService")) {
                add(evidenceByMarker, "TRANSFORMATION_SERVICE", entry);
            } else if (lower.startsWith("meta-inf/services/")) {
                add(evidenceByMarker, "SERVICE_PROVIDER", entry);
            }
            if (lower.endsWith(".jar")) {
                add(evidenceByMarker, "NESTED_JAR", entry);
            }
            if (lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib")) {
                add(evidenceByMarker, "NATIVE_LIBRARY", entry);
            }
            if (lower.equals("module-info.class")) {
                add(evidenceByMarker, "MODULE_DESCRIPTOR", entry);
            }
            if (lower.startsWith("meta-inf/versions/")) {
                add(evidenceByMarker, "MULTI_RELEASE", entry);
            }
        }
        if (archive.signed()) {
            evidenceByMarker.computeIfAbsent("SIGNED_JAR", ignored -> new ArrayList<>());
        }
        if (archive.multiRelease()) {
            add(evidenceByMarker, "MULTI_RELEASE", "META-INF/MANIFEST.MF#Multi-Release");
        }
        if (bytecode.nativeMethodCount() > 0) {
            add(evidenceByMarker, "NATIVE_METHOD", "nativeMethodCount=" + bytecode.nativeMethodCount());
            occurrencesByMarker.put("NATIVE_METHOD", (long) bytecode.nativeMethodCount());
        }

        Set<String> markerIds = new TreeSet<>(evidenceByMarker.keySet());
        markerIds.addAll(occurrencesByMarker.keySet());
        return markerIds.stream()
                .map(id -> {
                    List<String> evidence = evidenceByMarker.getOrDefault(id, List.of());
                    long occurrences = Math.max(
                            occurrencesByMarker.getOrDefault(id, 0L),
                            Math.max(1, evidence.size())
                    );
                    return new FeatureMarker(
                            id,
                            occurrences,
                            evidence,
                            occurrences > evidence.stream().distinct().count()
                    );
                })
                .toList();
    }

    private static Map<String, Long> archiveMarkerCounts(ArchiveSummary archive) {
        Map<String, Long> result = new TreeMap<>();
        archive.specialEntryCounts().forEach((kind, count) -> {
            String marker = switch (kind) {
                case "SIGNATURE" -> "SIGNED_JAR";
                case "TRANSFORMATION_SERVICE" -> "TRANSFORMATION_SERVICE";
                case "SERVICE_PROVIDER" -> "SERVICE_PROVIDER";
                case "NESTED_JAR" -> "NESTED_JAR";
                case "NATIVE_LIBRARY" -> "NATIVE_LIBRARY";
                case "MODULE_DESCRIPTOR" -> "MODULE_DESCRIPTOR";
                case "MULTI_RELEASE" -> "MULTI_RELEASE";
                default -> null;
            };
            if (marker != null) {
                result.merge(marker, count, Math::addExact);
            }
        });
        return result;
    }

    private static void add(Map<String, List<String>> evidence, String marker, String value) {
        evidence.computeIfAbsent(marker, ignored -> new ArrayList<>()).add(value);
    }

    private static boolean isSignature(String entry) {
        return entry.toUpperCase(Locale.ROOT).matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)");
    }
}
