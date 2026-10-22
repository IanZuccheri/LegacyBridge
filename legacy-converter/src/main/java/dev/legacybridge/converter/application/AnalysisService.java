package dev.legacybridge.converter.application;

import dev.legacybridge.converter.archive.ArchivePolicy;
import dev.legacybridge.converter.archive.ArchiveScan;
import dev.legacybridge.converter.archive.InputRejectedException;
import dev.legacybridge.converter.archive.JarEntryVisitor;
import dev.legacybridge.converter.archive.SafeJarReader;
import dev.legacybridge.converter.analysis.LegacyFeatureUsageCollector;
import dev.legacybridge.converter.analysis.PlatformReferenceCollector;
import dev.legacybridge.converter.bytecode.BytecodeScanAggregate;
import dev.legacybridge.converter.bytecode.BytecodeScanAccumulator;
import dev.legacybridge.converter.bytecode.BytecodeScanException;
import dev.legacybridge.converter.bytecode.BytecodeScanner;
import dev.legacybridge.converter.bytecode.ClassFileHeader;
import dev.legacybridge.converter.config.ArtifactDiagnosticSeverity;
import dev.legacybridge.converter.config.LegacyConfigInventory;
import dev.legacybridge.converter.config.LegacyConfigInventoryCollector;
import dev.legacybridge.converter.content.ContentInventory;
import dev.legacybridge.converter.content.ContentInventoryCollector;
import dev.legacybridge.converter.metadata.ForgeMetadataAnalyzer;
import dev.legacybridge.converter.metadata.ManifestAnalysis;
import dev.legacybridge.converter.metadata.ManifestAnalyzer;
import dev.legacybridge.converter.metadata.MetadataAnalysis;
import dev.legacybridge.converter.policy.FeatureMarkerDetector;
import dev.legacybridge.converter.policy.PolicyResult;
import dev.legacybridge.converter.policy.ProgressiveSupportPolicy;
import dev.legacybridge.converter.report.BytecodeSummary;
import dev.legacybridge.converter.report.CompatibilityReport;
import dev.legacybridge.converter.report.Assessment;
import dev.legacybridge.converter.report.AnalysisStatus;
import dev.legacybridge.converter.report.FeatureMarker;
import dev.legacybridge.converter.report.Finding;
import dev.legacybridge.converter.report.ToolInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;

/** Coordinates the static, analysis-only pipeline. */
public final class AnalysisService {
    private static final String MANIFEST = "META-INF/MANIFEST.MF";

    private final SafeJarReader jarReader;
    private final BytecodeScanner bytecodeScanner;
    private final ForgeMetadataAnalyzer metadataAnalyzer;
    private final ManifestAnalyzer manifestAnalyzer;
    private final FeatureMarkerDetector markerDetector;
    private final ProgressiveSupportPolicy supportPolicy;
    private final BytecodeReportAdapter bytecodeReportAdapter;
    private final LegacyAnalysisSummaryFactory analysisSummaryFactory;
    private final String toolVersion;

    public AnalysisService() {
        this(
                new SafeJarReader(),
                new BytecodeScanner(),
                new ForgeMetadataAnalyzer(),
                new ManifestAnalyzer(),
                new FeatureMarkerDetector(),
                new ProgressiveSupportPolicy(),
                new BytecodeReportAdapter(),
                new LegacyAnalysisSummaryFactory(),
                ToolVersions.current()
        );
    }

    AnalysisService(
            SafeJarReader jarReader,
            BytecodeScanner bytecodeScanner,
            ForgeMetadataAnalyzer metadataAnalyzer,
            ManifestAnalyzer manifestAnalyzer,
            FeatureMarkerDetector markerDetector,
            ProgressiveSupportPolicy supportPolicy,
            BytecodeReportAdapter bytecodeReportAdapter,
            LegacyAnalysisSummaryFactory analysisSummaryFactory,
            String toolVersion
    ) {
        this.jarReader = jarReader;
        this.bytecodeScanner = bytecodeScanner;
        this.metadataAnalyzer = metadataAnalyzer;
        this.manifestAnalyzer = manifestAnalyzer;
        this.markerDetector = markerDetector;
        this.supportPolicy = supportPolicy;
        this.bytecodeReportAdapter = bytecodeReportAdapter;
        this.analysisSummaryFactory = analysisSummaryFactory;
        this.toolVersion = toolVersion;
    }

    public CompatibilityReport analyze(Path input) throws InputRejectedException {
        CollectedEntries collected = new CollectedEntries(bytecodeScanner);
        ArchiveScan archiveScan = jarReader.scan(input, collected);

        MetadataAnalysis metadata = metadataAnalyzer.analyze(collected.modsToml);
        ManifestAnalysis manifest = manifestAnalyzer.analyze(collected.manifest);
        BytecodeScanAggregate aggregate = collected.bytecode.snapshot();
        ContentInventory content = collected.content.snapshot();
        captureManifestDeclaredMixins(input, archiveScan, collected);
        LegacyConfigInventory config = collected.config.snapshot();
        BytecodeSummary bytecode = bytecodeReportAdapter.adapt(
                aggregate,
                collected.unreadableClassCount,
                collected.unreadableClassMajorVersions,
                collected.unreadablePreviewClassCount
        );
        List<FeatureMarker> markers = mergeMarkers(
                markerDetector.detect(archiveScan.archive(), bytecode),
                manifest.markers(),
                configMarkers(config)
        );
        collected.featureUsages.accept(content);
        collected.featureUsages.acceptMarkers(markers);
        collected.featureUsages.acceptBytecode(bytecode);
        PolicyResult policy = supportPolicy.assess(metadata.summary(), metadata.valid(), bytecode, markers);

        List<Finding> findings = new ArrayList<>();
        findings.addAll(metadata.findings());
        if (metadata.summary().mods().size() > 1) {
            findings.add(new Finding(
                    "LB-M020",
                    dev.legacybridge.converter.report.Severity.WARNING,
                    "metadata",
                    "The JAR declares multiple mods; top-level identity selects the first valid sorted modId.",
                    metadata.summary().mods().stream().map(value -> value.modId()).toList()
            ));
        }
        findings.addAll(manifest.findings());
        findings.addAll(configFindings(config));
        findings.addAll(policy.findings());

        Assessment assessment = policy.assessment();
        if (configIsPartial(config)) {
            assessment = new Assessment(
                    AnalysisStatus.PARTIAL,
                    assessment.sourceProfile(),
                    assessment.conversionStatus(),
                    assessment.executionStatus()
            );
        }

        var analysis = analysisSummaryFactory.create(
                metadata.summary(), metadata.valid(), bytecode, aggregate, content, config,
                collected.platformReferences, collected.featureUsages
        );

        return new CompatibilityReport(
                CompatibilityReport.SCHEMA_VERSION,
                analysis,
                new ToolInfo("LegacyBridge Converter", toolVersion, ArchivePolicy.VERSION),
                archiveScan.input(),
                archiveScan.archive(),
                metadata.summary(),
                bytecode,
                markers,
                findings,
                assessment
        );
    }

    private void captureManifestDeclaredMixins(
            Path input,
            ArchiveScan firstScan,
            CollectedEntries collected
    ) throws InputRejectedException {
        LegacyConfigInventory initial = collected.config.snapshot();
        Set<String> alreadyCaptured = initial.mixins().stream()
                .map(value -> value.path())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> missing = new HashSet<>(initial.manifest().declaredMixinConfigurationPaths());
        missing.removeAll(alreadyCaptured);
        if (missing.isEmpty()) {
            return;
        }

        Set<String> visited = new HashSet<>();
        ArchiveScan secondScan = jarReader.scan(input, new JarEntryVisitor() {
            @Override
            public boolean requiresBytes(String entryName) {
                return missing.contains(entryName);
            }

            @Override
            public long maxCapturedBytes(String entryName) {
                return LegacyConfigInventoryCollector.MAX_ARTIFACT_BYTES;
            }

            @Override
            public void visit(String entryName, byte[] contents) {
                visited.add(entryName);
                collected.config.acceptDeclaredMixinConfiguration(entryName, contents);
            }
        });
        if (!firstScan.input().equals(secondScan.input())) {
            throw new InputRejectedException(
                    "INPUT_CHANGED",
                    "Input changed between static analysis passes"
            );
        }
        missing.removeAll(visited);
        missing.stream().sorted()
                .forEach(collected.config::recordMissingDeclaredMixinConfiguration);
    }

    private static boolean configIsPartial(LegacyConfigInventory config) {
        return config.manifest().valuesTruncated()
                || config.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.severity() == ArtifactDiagnosticSeverity.ERROR)
                || config.mixins().stream().anyMatch(value -> value.partial() || !value.parsed())
                || config.coremods().stream().anyMatch(value -> value.partial() || !value.parsed())
                || config.accessTransformers().stream().anyMatch(value -> !value.parsed());
    }

    private static List<FeatureMarker> configMarkers(LegacyConfigInventory config) {
        List<FeatureMarker> markers = new ArrayList<>();
        TreeSet<String> mixinPaths = config.mixins().stream()
                .map(value -> value.path())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        mixinPaths.addAll(config.manifest().declaredMixinConfigurationPaths());
        if (!mixinPaths.isEmpty()) {
            markers.add(new FeatureMarker(
                    "MIXIN_CONFIGURATION",
                    mixinPaths.size(),
                    List.copyOf(mixinPaths),
                    false
            ));
        }
        if (!config.accessTransformers().isEmpty()) {
            markers.add(new FeatureMarker(
                    "ACCESS_TRANSFORMER",
                    config.accessTransformers().stream().map(value -> value.path()).toList()
            ));
        }
        if (!config.coremods().isEmpty()) {
            markers.add(new FeatureMarker(
                    "COREMOD",
                    config.coremods().stream().map(value -> value.path()).toList()
            ));
        }
        return List.copyOf(markers);
    }

    private static List<Finding> configFindings(LegacyConfigInventory config) {
        return config.diagnostics().stream().map(diagnostic -> new Finding(
                diagnostic.code(),
                diagnostic.severity() == ArtifactDiagnosticSeverity.ERROR
                        ? dev.legacybridge.converter.report.Severity.ERROR
                        : dev.legacybridge.converter.report.Severity.WARNING,
                "configuration",
                diagnostic.message(),
                List.of(diagnostic.path())
        )).toList();
    }

    @SafeVarargs
    private static List<FeatureMarker> mergeMarkers(List<FeatureMarker>... markerLists) {
        Map<String, MarkerAccumulator> byId = new TreeMap<>();
        for (List<FeatureMarker> markers : markerLists) {
            for (FeatureMarker marker : markers) {
                byId.computeIfAbsent(marker.id(), ignored -> new MarkerAccumulator())
                        .accept(marker);
            }
        }
        return byId.entrySet().stream()
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .toList();
    }

    private static final class MarkerAccumulator {
        private long occurrenceCount;
        private final TreeSet<String> evidence = new TreeSet<>();
        private boolean truncated;

        private void accept(FeatureMarker marker) {
            occurrenceCount = Math.addExact(occurrenceCount, marker.occurrenceCount());
            truncated |= marker.evidenceTruncated();
            for (String value : marker.evidence()) {
                evidence.add(value);
                if (evidence.size() > FeatureMarker.EVIDENCE_LIMIT) {
                    evidence.pollLast();
                    truncated = true;
                }
            }
        }

        private FeatureMarker snapshot(String id) {
            return new FeatureMarker(id, occurrenceCount, List.copyOf(evidence), truncated);
        }
    }

    private static final class CollectedEntries implements JarEntryVisitor {
        private final BytecodeScanner scanner;
        private final BytecodeScanAccumulator bytecode = new BytecodeScanAccumulator();
        private final ContentInventoryCollector content = new ContentInventoryCollector();
        private final LegacyConfigInventoryCollector config = new LegacyConfigInventoryCollector();
        private final LegacyFeatureUsageCollector featureUsages = new LegacyFeatureUsageCollector();
        private final PlatformReferenceCollector platformReferences = new PlatformReferenceCollector();
        private int unreadableClassCount;
        private final Map<Integer, Integer> unreadableClassMajorVersions = new TreeMap<>();
        private int unreadablePreviewClassCount;
        private byte[] modsToml;
        private byte[] manifest;

        private CollectedEntries(BytecodeScanner scanner) {
            this.scanner = scanner;
        }

        @Override
        public void visitEntry(String entryName, boolean directory) {
            if (!directory) {
                content.accept(entryName);
            }
        }

        @Override
        public boolean requiresBytes(String entryName) {
            return entryName.endsWith(".class")
                    || ForgeMetadataAnalyzer.MODS_TOML.equals(entryName)
                    || MANIFEST.equals(entryName)
                    || config.requiresBytes(entryName);
        }

        @Override
        public long maxCapturedBytes(String entryName) {
            return config.requiresBytes(entryName)
                    ? LegacyConfigInventoryCollector.MAX_ARTIFACT_BYTES
                    : Long.MAX_VALUE;
        }

        @Override
        public void visit(String entryName, byte[] contents) throws IOException {
            if (config.requiresBytes(entryName)) {
                config.accept(entryName, contents);
            }
            if (ForgeMetadataAnalyzer.MODS_TOML.equals(entryName)) {
                modsToml = contents;
            } else if (MANIFEST.equals(entryName)) {
                manifest = contents;
            } else if (entryName.endsWith(".class")) {
                try {
                    LegacyFeatureUsageCollector classFeatures = new LegacyFeatureUsageCollector();
                    PlatformReferenceCollector classPlatforms = new PlatformReferenceCollector();
                    var scan = scanner.scan(entryName, contents, reference -> {
                        classPlatforms.accept(reference);
                        classFeatures.accept(entryName, reference);
                    });
                    bytecode.accept(scan);
                    platformReferences.merge(classPlatforms);
                    featureUsages.merge(classFeatures);
                } catch (BytecodeScanException exception) {
                    unreadableClassCount = Math.incrementExact(unreadableClassCount);
                    retainReadableHeader(entryName, contents);
                }
            }
        }

        private void retainReadableHeader(String entryName, byte[] contents) {
            try {
                ClassFileHeader header = scanner.readHeader(entryName, contents);
                unreadableClassMajorVersions.merge(header.majorVersion(), 1, Math::addExact);
                if (header.preview()) {
                    unreadablePreviewClassCount = Math.incrementExact(unreadablePreviewClassCount);
                }
            } catch (BytecodeScanException ignored) {
                // A missing/invalid fixed header has no trustworthy version fact to retain.
            }
        }
    }
}
