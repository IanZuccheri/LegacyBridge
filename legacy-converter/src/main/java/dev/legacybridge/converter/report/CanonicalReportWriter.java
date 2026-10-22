package dev.legacybridge.converter.report;

import dev.legacybridge.converter.bytecode.ArchiveReferenceInventory;
import dev.legacybridge.converter.bytecode.BytecodeReference;
import dev.legacybridge.converter.bytecode.BytecodeReferenceKind;
import dev.legacybridge.converter.bytecode.ClassReferenceDetail;
import dev.legacybridge.converter.content.ContentCategorySummary;
import dev.legacybridge.converter.content.ContentInventory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes schema v2 with stable field order, UTF-8, LF, and no environment data. */
public final class CanonicalReportWriter {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public String toJson(CompatibilityReport report) {
        return toJsonTree(reportTree(report));
    }

    public String toJsonTree(Map<String, Object> tree) {
        StringBuilder output = new StringBuilder(4_096);
        writeValue(output, tree, 0);
        output.append('\n');
        return output.toString();
    }

    public void writeAtomically(
            Path target,
            CompatibilityReport report,
            boolean overwrite,
            Path protectedInput
    ) throws IOException {
        writeJsonAtomically(target, toJson(report), overwrite, protectedInput);
    }

    public void writeJsonAtomically(Path target, String json, boolean overwrite, Path protectedInput) throws IOException {
        Path protectedRealPath = protectedInput.toRealPath();
        Path absoluteTarget = resolveTarget(target);
        Path parent = absoluteTarget.getParent();
        if (parent == null) {
            throw new IOException("Report parent directory does not exist");
        }
        requireDistinctRegularDestination(absoluteTarget, protectedRealPath);

        Path temporary = Files.createTempFile(parent, ".legacybridge-report-", ".tmp");
        boolean moved = false;
        try {
            Files.writeString(
                    temporary,
                    json,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            // Revalidate immediately before commit so an alias cannot replace
            // the source JAR after analysis and before report publication.
            requireDistinctRegularDestination(absoluteTarget, protectedRealPath);
            if (overwrite) {
                try {
                    move(temporary, absoluteTarget, true, true);
                } catch (AtomicMoveNotSupportedException ignored) {
                    move(temporary, absoluteTarget, true, false);
                }
            } else {
                // On Windows, ATOMIC_MOVE may replace an existing target even
                // without REPLACE_EXISTING. A plain same-directory move keeps
                // the no-overwrite contract enforced by the filesystem.
                move(temporary, absoluteTarget, false, false);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static Path resolveTarget(Path target) throws IOException {
        Path absoluteTarget = target.toAbsolutePath().normalize();
        Path parent = absoluteTarget.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("Report parent directory does not exist");
        }
        Path realParent = parent.toRealPath();
        return realParent.resolve(absoluteTarget.getFileName()).normalize();
    }

    private static void requireDistinctRegularDestination(Path target, Path protectedRealPath) throws IOException {
        if (target.equals(protectedRealPath)) {
            throw new IOException("Report target resolves to the input JAR");
        }
        if (Files.isSymbolicLink(target)) {
            throw new IOException("Report target must not be a symbolic link");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSameFile(target, protectedRealPath)) {
            throw new IOException("Report target aliases the input JAR");
        }
    }

    private static void move(Path source, Path target, boolean overwrite, boolean atomic) throws IOException {
        List<StandardCopyOption> options = new ArrayList<>();
        if (atomic) {
            options.add(StandardCopyOption.ATOMIC_MOVE);
        }
        if (overwrite) {
            options.add(StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(source, target, options.toArray(StandardCopyOption[]::new));
    }

    private static Map<String, Object> reportTree(CompatibilityReport report) {
        Map<String, Object> root = object();
        root.put("schemaVersion", report.schemaVersion());
        LegacyAnalysisSummary analysis = report.analysis();
        root.put("modId", analysis.modId());
        root.put("modVersion", analysis.modVersion());
        root.put("detectedMinecraftVersion", analysis.detectedMinecraftVersion());
        root.put("detectedForgeVersion", analysis.detectedForgeVersion());
        root.put("classCount", analysis.classCount());
        root.put("minecraftReferences", analysis.minecraftReferences().references().stream()
                .map(CanonicalReportWriter::referenceTree).toList());
        root.put("minecraftReferenceCount", analysis.minecraftReferences().occurrenceCount());
        root.put("minecraftReferencesTruncated", analysis.minecraftReferences().referencesTruncated());
        root.put("minecraftReferenceExternalityComplete", analysis.minecraftReferences().externalityComplete());
        root.put("forgeReferences", analysis.forgeReferences().references().stream()
                .map(CanonicalReportWriter::referenceTree).toList());
        root.put("forgeReferenceCount", analysis.forgeReferences().occurrenceCount());
        root.put("forgeReferencesTruncated", analysis.forgeReferences().referencesTruncated());
        root.put("forgeReferenceExternalityComplete", analysis.forgeReferences().externalityComplete());
        root.put("thirdPartyDependencies", analysis.thirdPartyDependencies().stream()
                .map(CanonicalReportWriter::dependencyTree).toList());
        root.put("thirdPartyDependenciesTruncated", analysis.thirdPartyDependenciesTruncated());
        root.put("mixins", analysis.mixins().stream().map(CanonicalReportWriter::artifactTree).toList());
        root.put("accessTransformers", analysis.accessTransformers().stream()
                .map(CanonicalReportWriter::artifactTree).toList());
        root.put("coremods", analysis.coremods().stream().map(CanonicalReportWriter::artifactTree).toList());
        root.put("unsupportedFeatures", analysis.unsupportedFeatures().stream()
                .map(CanonicalReportWriter::unsupportedFeatureTree).toList());
        root.put("riskScore", analysis.riskScore());
        root.put("complexity", analysis.complexity().name());
        root.put("packagesUsed", analysis.packagesUsed());
        root.put("packagesUsedTruncated", analysis.packagesUsedTruncated());
        root.put("classReferences", classReferencesTree(analysis.classReferences()));
        root.put("content", contentTree(analysis.content()));
        root.put("manifest", manifestTree(analysis.manifest()));
        root.put("tool", toolTree(report.tool()));
        root.put("input", inputTree(report.input()));
        root.put("archive", archiveTree(report.archive()));
        root.put("metadata", metadataTree(report.metadata()));
        root.put("bytecode", bytecodeTree(report.bytecode()));
        root.put("markers", report.markers().stream().map(CanonicalReportWriter::markerTree).toList());
        root.put("findings", report.findings().stream().map(CanonicalReportWriter::findingTree).toList());
        root.put("assessment", assessmentTree(report.assessment()));
        return root;
    }

    private static Map<String, Object> dependencyTree(ThirdPartyDependency dependency) {
        Map<String, Object> value = object();
        value.put("identifier", dependency.identifier());
        value.put("source", dependency.source());
        value.put("mandatory", dependency.mandatory());
        value.put("versionRange", dependency.versionRange());
        return value;
    }

    private static Map<String, Object> manifestTree(ManifestSummary manifest) {
        Map<String, Object> value = object();
        value.put("present", manifest.present());
        value.put("parsed", manifest.parsed());
        value.put("attributes", manifest.attributes());
        value.put("valuesTruncated", manifest.valuesTruncated());
        return value;
    }

    private static Map<String, Object> artifactTree(StaticArtifact artifact) {
        Map<String, Object> value = object();
        value.put("path", artifact.path());
        value.put("parsed", artifact.parsed());
        value.put("declarationCount", artifact.declarationCount());
        value.put("attributes", artifact.attributes());
        value.put("diagnostics", artifact.diagnostics());
        value.put("truncated", artifact.truncated());
        value.put("partial", artifact.partial());
        return value;
    }

    private static Map<String, Object> unsupportedFeatureTree(UnsupportedFeature feature) {
        Map<String, Object> value = object();
        value.put("id", feature.id());
        value.put("occurrenceCount", feature.occurrenceCount());
        value.put("evidence", feature.evidence());
        value.put("evidenceTruncated", feature.evidenceTruncated());
        return value;
    }

    private static Map<String, Object> referenceTree(BytecodeReference reference) {
        Map<String, Object> value = object();
        value.put("kind", reference.kind().name());
        value.put("owner", reference.owner());
        value.put("method", reference.kind() == BytecodeReferenceKind.METHOD
                ? reference.memberName() : null);
        value.put("descriptor", reference.descriptor());
        value.put("field", reference.kind() == BytecodeReferenceKind.FIELD
                ? reference.memberName() : null);
        return value;
    }

    private static Map<String, Object> classReferencesTree(ArchiveReferenceInventory inventory) {
        Map<String, Object> value = object();
        value.put("referenceOccurrenceCount", inventory.references().occurrences());
        value.put("referenceOccurrencesByKind", enumCountTree(inventory));
        value.put("reportedClassCount", inventory.classDetails().size());
        value.put("classDetailsTruncated", inventory.classDetailsTruncated());
        value.put("definitionIndexComplete", inventory.definitionIndexComplete());
        value.put("externalSamplesComplete", inventory.externalSamplesComplete());
        value.put("classes", inventory.classDetails().stream()
                .map(detail -> classReferenceTree(detail, inventory)).toList());
        return value;
    }

    private static Map<String, Object> enumCountTree(ArchiveReferenceInventory inventory) {
        Map<String, Object> counts = object();
        for (BytecodeReferenceKind kind : BytecodeReferenceKind.values()) {
            counts.put(kind.name(), inventory.references().occurrences(kind));
        }
        return counts;
    }

    private static Map<String, Object> classReferenceTree(
            ClassReferenceDetail detail,
            ArchiveReferenceInventory inventory
    ) {
        List<BytecodeReference> external = detail.references().externalSamples(
                inventory.definitions().owners()).stream().toList();
        Map<String, Object> value = object();
        value.put("entryName", detail.entryName());
        value.put("className", detail.internalName());
        value.put("referenceOccurrenceCount", detail.references().occurrences());
        value.put("referencesTruncated", detail.references().truncated());
        value.put("superclass", external.stream()
                .filter(reference -> reference.kind() == BytecodeReferenceKind.SUPERCLASS)
                .map(BytecodeReference::owner).findFirst().orElse(null));
        value.put("interfaces", external.stream()
                .filter(reference -> reference.kind() == BytecodeReferenceKind.INTERFACE)
                .map(BytecodeReference::owner).distinct().sorted().toList());
        value.put("methods", external.stream()
                .filter(reference -> reference.kind() == BytecodeReferenceKind.METHOD)
                .map(CanonicalReportWriter::referenceTree).toList());
        value.put("fields", external.stream()
                .filter(reference -> reference.kind() == BytecodeReferenceKind.FIELD)
                .map(CanonicalReportWriter::referenceTree).toList());
        value.put("types", external.stream()
                .filter(reference -> reference.kind() == BytecodeReferenceKind.TYPE)
                .map(CanonicalReportWriter::referenceTree).toList());
        return value;
    }

    private static Map<String, Object> contentTree(ContentInventory content) {
        Map<String, Object> value = object();
        value.put("observedFileCount", content.observedFileCount());
        value.put("assetEntryCount", content.assetEntryCount());
        value.put("dataPackEntryCount", content.dataPackEntryCount());
        value.put("assetNamespaces", namespaceTree(content.assetNamespaces().namespaceCount(),
                content.assetNamespaces().samples(), content.assetNamespaces().samplesTruncated()));
        value.put("dataNamespaces", namespaceTree(content.dataNamespaces().namespaceCount(),
                content.dataNamespaces().samples(), content.dataNamespaces().samplesTruncated()));
        Map<String, Object> categories = object();
        content.categories().forEach((category, summary) ->
                categories.put(category.name(), contentCategoryTree(summary)));
        value.put("categories", categories);
        return value;
    }

    private static Map<String, Object> namespaceTree(long count, List<String> samples, boolean truncated) {
        Map<String, Object> value = object();
        value.put("namespaceCount", count);
        value.put("samples", samples);
        value.put("samplesTruncated", truncated);
        return value;
    }

    private static Map<String, Object> contentCategoryTree(ContentCategorySummary summary) {
        Map<String, Object> value = object();
        value.put("entryCount", summary.entryCount());
        value.put("samples", summary.samples());
        value.put("samplesTruncated", summary.samplesTruncated());
        return value;
    }

    private static Map<String, Object> toolTree(ToolInfo tool) {
        Map<String, Object> value = object();
        value.put("name", tool.name());
        value.put("version", tool.version());
        value.put("policyVersion", tool.policyVersion());
        return value;
    }

    private static Map<String, Object> inputTree(InputFingerprint input) {
        Map<String, Object> value = object();
        value.put("fileName", input.fileName());
        value.put("sizeBytes", input.sizeBytes());
        value.put("sha256", input.sha256());
        return value;
    }

    private static Map<String, Object> archiveTree(ArchiveSummary archive) {
        Map<String, Object> value = object();
        value.put("entryCount", archive.entryCount());
        value.put("totalUncompressedBytes", archive.totalUncompressedBytes());
        value.put("signed", archive.signed());
        value.put("multiRelease", archive.multiRelease());
        value.put("specialEntryCount", archive.specialEntryCount());
        value.put("specialEntriesTruncated", archive.specialEntriesTruncated());
        value.put("specialEntries", archive.specialEntries());
        value.put("specialEntryCounts", archive.specialEntryCounts());
        return value;
    }

    private static Map<String, Object> metadataTree(MetadataSummary metadata) {
        Map<String, Object> value = object();
        value.put("present", metadata.present());
        value.put("parsed", metadata.parsed());
        value.put("modLoader", metadata.modLoader());
        value.put("loaderVersion", metadata.loaderVersion());
        value.put("mods", metadata.mods().stream().map(mod -> {
            Map<String, Object> item = object();
            item.put("modId", mod.modId());
            item.put("version", mod.version());
            item.put("displayName", mod.displayName());
            return item;
        }).toList());
        value.put("dependencies", metadata.dependencies().stream().map(dependency -> {
            Map<String, Object> item = object();
            item.put("ownerModId", dependency.ownerModId());
            item.put("modId", dependency.modId());
            item.put("mandatoryDeclared", dependency.mandatoryDeclared());
            item.put("mandatory", dependency.mandatory());
            item.put("versionRange", dependency.versionRange());
            item.put("ordering", dependency.ordering());
            item.put("side", dependency.side());
            return item;
        }).toList());
        return value;
    }

    private static Map<String, Object> bytecodeTree(BytecodeSummary bytecode) {
        Map<String, Object> value = object();
        value.put("classCount", bytecode.classCount());
        value.put("unreadableClassCount", bytecode.unreadableClassCount());
        value.put("previewClassCount", bytecode.previewClassCount());
        value.put("nativeMethodCount", bytecode.nativeMethodCount());
        Map<String, Object> versions = object();
        bytecode.classMajorVersions().forEach((major, count) -> versions.put(String.valueOf(major), count));
        value.put("classMajorVersions", versions);
        value.put("namespaces", bytecode.namespaces().stream().map(namespace -> {
            Map<String, Object> item = object();
            item.put("namespace", namespace.namespace());
            item.put("definitionCount", namespace.definitionCount());
            item.put("referenceCount", namespace.referenceCount());
            item.put("definitionSamples", namespace.definitionSamples());
            item.put("referenceSamples", namespace.referenceSamples());
            item.put("definitionSamplesTruncated", namespace.definitionSamplesTruncated());
            item.put("referenceSamplesTruncated", namespace.referenceSamplesTruncated());
            return item;
        }).toList());
        return value;
    }

    private static Map<String, Object> markerTree(FeatureMarker marker) {
        Map<String, Object> value = object();
        value.put("id", marker.id());
        value.put("occurrenceCount", marker.occurrenceCount());
        value.put("evidence", marker.evidence());
        value.put("evidenceTruncated", marker.evidenceTruncated());
        return value;
    }

    private static Map<String, Object> findingTree(Finding finding) {
        Map<String, Object> value = object();
        value.put("code", finding.code());
        value.put("severity", finding.severity().name());
        value.put("category", finding.category());
        value.put("message", finding.message());
        value.put("evidence", finding.evidence());
        return value;
    }

    private static Map<String, Object> assessmentTree(Assessment assessment) {
        Map<String, Object> value = object();
        value.put("analysisStatus", assessment.analysisStatus().name());
        value.put("sourceProfile", assessment.sourceProfile().name());
        value.put("conversionStatus", assessment.conversionStatus().name());
        value.put("executionStatus", assessment.executionStatus().name());
        return value;
    }

    private static Map<String, Object> object() {
        return new LinkedHashMap<>();
    }

    private static void writeValue(StringBuilder output, Object value, int depth) {
        if (value instanceof Map<?, ?> map) {
            writeObject(output, map, depth);
        } else if (value instanceof Iterable<?> iterable) {
            writeArray(output, iterable, depth);
        } else if (value instanceof String string) {
            writeString(output, string);
        } else if (value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value == null) {
            output.append("null");
        } else {
            throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass().getName());
        }
    }

    private static void writeObject(StringBuilder output, Map<?, ?> map, int depth) {
        output.append('{');
        if (!map.isEmpty()) {
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                output.append(index++ == 0 ? '\n' : ",\n");
                indent(output, depth + 1);
                writeString(output, String.valueOf(entry.getKey()));
                output.append(": ");
                writeValue(output, entry.getValue(), depth + 1);
            }
            output.append('\n');
            indent(output, depth);
        }
        output.append('}');
    }

    private static void writeArray(StringBuilder output, Iterable<?> values, int depth) {
        List<Object> materialized = new ArrayList<>();
        values.forEach(materialized::add);
        output.append('[');
        for (int index = 0; index < materialized.size(); index++) {
            output.append(index == 0 ? '\n' : ",\n");
            indent(output, depth + 1);
            writeValue(output, materialized.get(index), depth + 1);
        }
        if (!materialized.isEmpty()) {
            output.append('\n');
            indent(output, depth);
        }
        output.append(']');
    }

    private static void writeString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    int type = Character.getType(character);
                    if (character < 0x20
                            || type == Character.FORMAT
                            || type == Character.LINE_SEPARATOR
                            || type == Character.PARAGRAPH_SEPARATOR
                            || (Character.isSurrogate(character)
                            && !isValidSurrogate(value, index))) {
                        appendUnicodeEscape(output, character);
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }

    private static boolean isValidSurrogate(String value, int index) {
        char current = value.charAt(index);
        if (Character.isHighSurrogate(current)) {
            return index + 1 < value.length() && Character.isLowSurrogate(value.charAt(index + 1));
        }
        return index > 0 && Character.isHighSurrogate(value.charAt(index - 1));
    }

    private static void appendUnicodeEscape(StringBuilder output, char character) {
        output.append("\\u")
                .append(HEX[(character >>> 12) & 0xF])
                .append(HEX[(character >>> 8) & 0xF])
                .append(HEX[(character >>> 4) & 0xF])
                .append(HEX[character & 0xF]);
    }

    private static void indent(StringBuilder output, int depth) {
        output.append("  ".repeat(depth));
    }
}
