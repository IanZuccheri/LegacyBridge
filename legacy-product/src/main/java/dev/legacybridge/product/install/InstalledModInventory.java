package dev.legacybridge.product.install;

import dev.legacybridge.converter.archive.JarEntryVisitor;
import dev.legacybridge.converter.archive.SafeJarReader;
import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.metadata.ForgeMetadataAnalyzer;
import dev.legacybridge.converter.policy.NumericVersionRange;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/** Complete bounded direct-mod inventory. Unknown nested closure is a refusal, never skipped. */
final class InstalledModInventory {
    private static final String SRG_SHA = "f10b366cdc4df20de8a50511f49fded5ea0fe8f7a476ff984a12f65a1602533e";
    record Snapshot(Map<Path, String> jars, Set<String> ids) { }

    static Snapshot read(Path mods, Set<String> loaded, StagedArtifactValidator.ValidatedCandidate candidate) throws Exception {
        return readFuture(mods, loaded, java.util.List.of(candidate), Map.of(), Set.of());
    }

    /** Exclusions are only individually validated, managed artifacts being replaced/removed. */
    static Snapshot readFuture(Path mods, Set<String> loaded,
                               java.util.List<StagedArtifactValidator.ValidatedCandidate> candidates,
                               Map<Path, String> excluded, Set<String> removedIds) throws Exception {
        return readFuture(mods, loaded, candidates, excluded, removedIds, Set.of());
    }

    static Snapshot readFuture(Path mods, Set<String> loaded,
                               java.util.List<StagedArtifactValidator.ValidatedCandidate> candidates,
                               Map<Path, String> excluded, Set<String> removedIds,
                               Set<String> independentUpdateIds) throws Exception {
        Map<String, StagedArtifactValidator.ValidatedCandidate> incoming = new TreeMap<>();
        StagedArtifactValidator.require(loaded.containsAll(Set.of("minecraft", "forge", "legacybridge_runtime")), "Active pinned runtime baseline is missing");
        for (var candidate : candidates) {
            StagedArtifactValidator.require(incoming.putIfAbsent(candidate.modId(), candidate) == null, "Duplicate batch mod ID");
            StagedArtifactValidator.require(!loaded.contains(candidate.modId()), "Incoming mod ID is already loaded");
        }
        Map<Path, String> jars = new TreeMap<>();
        Set<String> ids = new HashSet<>();
        Map<String, String> versions = new TreeMap<>(Map.of("minecraft", "1.20.1", "forge", "47.4.23"));
        Map<String, String> nodes = new TreeMap<>(Map.of("minecraft", "platform:minecraft", "forge", "platform:forge"));
        var requirements = new java.util.ArrayList<dev.legacybridge.converter.report.DependencyDeclaration>();
        int entries = 0;
        try (var files = Files.newDirectoryStream(mods)) {
            for (Path path : files) {
                StagedArtifactValidator.require(++entries <= 1024, "Mods directory inventory limit exceeded");
                AddOnlyInstaller.regular(path);
                if (!path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) continue;
                StagedArtifactValidator.require(jars.size() < 256, "Installed JAR inventory limit exceeded");
                if (excluded.containsKey(path)) {
                    String sha = AddOnlyInstaller.hash(path);
                    StagedArtifactValidator.require(sha.equals(excluded.get(path)), "Managed artifact changed during inventory");
                    jars.put(path, sha); continue;
                }
                Collector collector = new Collector();
                var scan = new SafeJarReader().scan(path, collector);
                StagedArtifactValidator.require(!scan.archive().specialEntriesTruncated(), "Installed archive feature inventory is truncated");
                var metadata = new ForgeMetadataAnalyzer().analyze(collector.metadata);
                StagedArtifactValidator.require(metadata.valid() && metadata.findings().stream().allMatch(finding -> finding.code().equals("LB-M019")), "Installed JAR metadata is absent, ambiguous or truncated: " + path.getFileName());
                var summary = metadata.summary();
                StagedArtifactValidator.require(summary.modLoader().equals("javafml") && NumericVersionRange.contains(summary.loaderVersion(), "47"), "Installed loader is outside verified JavaFML grammar");
                Set<String> declared = new HashSet<>();
                for (var mod : summary.mods()) {
                    StagedArtifactValidator.require(!Set.of("minecraft", "forge").contains(mod.modId()), "MANUAL: installed JAR declares a reserved platform mod ID");
                    StagedArtifactValidator.require(!Set.of("minecraft", "forge").contains(mod.modId()), "Installed JAR illegally duplicates a platform mod ID");
                    StagedArtifactValidator.require(mod.version().length() <= 1024 && mod.displayName().length() <= 1024, "Installed identity metadata was truncated");
                    StagedArtifactValidator.require(declared.add(mod.modId()) && ids.add(mod.modId()) && !incoming.containsKey(mod.modId()), "Duplicate installed/incoming mod ID: " + mod.modId());
                    versions.put(mod.modId(), mod.version()); nodes.put(mod.modId(), path.toString());
                    if (mod.modId().equals("legacybridge_runtime")) StagedArtifactValidator.require(mod.version().equals(AdapterCatalog.RUNTIME_VERSION), "Installed runtime adapter version differs");
                }
                StagedArtifactValidator.require(collector.annotated.equals(declared), "Installed @Mod classes and TOML identities differ");
                for (var dependency : summary.dependencies()) {
                    StagedArtifactValidator.require(requirements.size() < 4096, "Installed dependency inventory exceeds its bound"); requirements.add(dependency);
                    StagedArtifactValidator.require(!dependency.mandatory() || !independentUpdateIds.contains(dependency.modId()),
                            "MANUAL: updating/restoring a required installed dependency needs verified pre-discovery recovery");
                    StagedArtifactValidator.require(dependency.versionRange().isEmpty() || NumericVersionRange.isSupported(dependency.versionRange())
                            || (dependency.modId().equals("legacybridge_runtime") && Set.of("[" + AdapterCatalog.RUNTIME_VERSION + "]",
                            dev.legacybridge.runtime.compat.registry.ModMetadataAdapter.targetRanges("1.16.5", "36.2.42").get("legacybridge_runtime")).contains(dependency.versionRange())),
                            "Unknown installed dependency range grammar");
                    if (incoming.containsKey(dependency.modId())) {
                        var candidate = incoming.get(dependency.modId());
                        // Forge 47.4.23 ModSorter also checks optional dependencies once their
                        // target is installed. Inspect both sides conservatively: this installer
                        // has no side-dependent dependency-solver contract.
                        StagedArtifactValidator.require(dependency.ordering().equals("NONE"),
                                "MANUAL: inbound ordering requires a verified dependency-order solver");
                        StagedArtifactValidator.require(dependency.versionRange().isEmpty()
                                        || NumericVersionRange.contains(dependency.versionRange(), candidate.modVersion()),
                                "Installed dependency on incoming mod excludes its version or cannot be compared: "
                                        + dependency.ownerModId() + " -> " + candidate.modId() + " " + dependency.versionRange()
                                        + " (candidate " + candidate.modVersion() + ")");
                    }
                    StagedArtifactValidator.require(!removedIds.contains(dependency.modId()) || !dependency.mandatory()
                                    || incoming.containsKey(dependency.modId()),
                            "MANUAL: restoring the previous set would remove a required installed dependency");
                    if (dependency.modId().equals("minecraft") || dependency.modId().equals("forge")) {
                        StagedArtifactValidator.require(NumericVersionRange.contains(dependency.versionRange(), dependency.modId().equals("minecraft") ? "1.20.1" : "47.4.23"), "Installed platform dependency excludes the pinned target");
                    }
                }
                if (!collector.nested.isEmpty() || collector.jij != null) {
                    StagedArtifactValidator.require(declared.equals(Set.of("legacybridge_runtime")) && collector.jij != null && collector.nested.size() == 1,
                            "MANUAL: nested dependency closure is not supported for this installed mod");
                    Object parsed = BoundedJsonParser.parse(new String(collector.jij, StandardCharsets.UTF_8));
                    StagedArtifactValidator.require(parsed instanceof Map<?, ?> root && root.get("jars") instanceof java.util.List<?> list && list.size() == 1,
                            "Malformed installed runtime Jar-in-Jar metadata");
                    Map<?, ?> row = (Map<?, ?>) ((java.util.List<?>) ((Map<?, ?>) parsed).get("jars")).get(0);
                    StagedArtifactValidator.require(row.get("identifier") instanceof Map<?, ?> identity
                            && "net.minecraftforge".equals(identity.get("group")) && "srgutils".equals(identity.get("artifact"))
                            && row.get("version") instanceof Map<?, ?> version && "0.6.2".equals(version.get("artifactVersion"))
                            && "[0.6.2,0.6.3)".equals(version.get("range")) && collector.nested.containsKey(row.get("path"))
                            && SRG_SHA.equals(collector.nested.get(row.get("path"))), "Unverified installed runtime nested dependency");
                }
                jars.put(path, scan.input().sha256());
            }
        }
        StagedArtifactValidator.require(jars.keySet().containsAll(excluded.keySet()), "A managed artifact disappeared during inventory");
        StagedArtifactValidator.require(ids.contains("legacybridge_runtime"), "The matching runtime JAR is absent from mods for the next launch");
        for (String removed : removedIds) StagedArtifactValidator.require(!ids.contains(removed), "MANUAL: another installed artifact still owns the ID selected for restoration/removal");
        for (var candidate : candidates) { versions.put(candidate.modId(), candidate.modVersion()); nodes.put(candidate.modId(), "candidate:" + candidate.modId()); }
        Map<String, Set<String>> graph = new TreeMap<>(); nodes.values().forEach(node -> graph.computeIfAbsent(node, key -> new HashSet<>()));
        for (var dependency : requirements) {
            String target = versions.get(dependency.modId());
            StagedArtifactValidator.require(target != null || !dependency.mandatory(), "MANUAL: required installed dependency is absent: " + dependency.modId());
            if (target == null) continue;
            boolean knownRuntime = dependency.modId().equals("legacybridge_runtime") && target.equals(AdapterCatalog.RUNTIME_VERSION)
                    && (dependency.versionRange().equals("[" + AdapterCatalog.RUNTIME_VERSION + "]")
                    || dependency.versionRange().equals(dev.legacybridge.runtime.compat.registry.ModMetadataAdapter.targetRanges("1.16.5", "36.2.42").get("legacybridge_runtime")));
            StagedArtifactValidator.require(dependency.versionRange().isEmpty() || knownRuntime
                            || NumericVersionRange.contains(dependency.versionRange(), target),
                    "MANUAL: installed dependency closure has an incompatible or uncomparable version: " + dependency.ownerModId() + " -> " + dependency.modId());
            String ownerNode = nodes.get(dependency.ownerModId()), targetNode = nodes.get(dependency.modId());
            if (ownerNode.equals(targetNode)) continue; // Forge sorts owning JARs, not individual IDs within one JAR.
            if (dependency.ordering().equals("BEFORE")) graph.get(ownerNode).add(targetNode);
            if (dependency.ordering().equals("AFTER")) graph.get(targetNode).add(ownerNode);
        }
        for (var candidate : candidates) graph.get(nodes.get("legacybridge_runtime")).add(nodes.get(candidate.modId()));
        Map<String, Integer> indegree = new TreeMap<>(); graph.keySet().forEach(node -> indegree.put(node, 0));
        graph.values().forEach(edges -> edges.forEach(node -> indegree.merge(node, 1, Integer::sum)));
        var ready = new java.util.ArrayDeque<String>(); indegree.forEach((node, degree) -> { if (degree == 0) ready.add(node); });
        int ordered = 0;
        while (!ready.isEmpty()) { String node = ready.remove(); ordered++; for (String target : graph.get(node)) if (indegree.merge(target, -1, Integer::sum) == 0) ready.add(target); }
        StagedArtifactValidator.require(ordered == graph.size(), "MANUAL: installed dependency ordering contains a cycle");
        return new Snapshot(Map.copyOf(jars), Set.copyOf(ids));
    }

    static void unchanged(Path mods, Snapshot before) throws IOException {
        Set<Path> current = new HashSet<>();
        int entries = 0;
        try (var files = Files.newDirectoryStream(mods)) {
            for (Path path : files) {
                StagedArtifactValidator.require(++entries <= 1024, "Mods directory changed beyond inventory limit");
                AddOnlyInstaller.regular(path);
                if (path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) current.add(path);
            }
        }
        StagedArtifactValidator.require(current.equals(before.jars().keySet()), "Installed JAR set changed during preflight");
        for (var entry : before.jars().entrySet()) StagedArtifactValidator.require(AddOnlyInstaller.hash(entry.getKey()).equals(entry.getValue()), "Installed JAR changed during preflight");
    }

    private static final class Collector implements JarEntryVisitor {
        byte[] metadata; byte[] jij;
        final Set<String> annotated = new HashSet<>();
        final Map<String, String> nested = new TreeMap<>();
        public boolean requiresBytes(String name) { return name.equals("META-INF/mods.toml") || name.equals("META-INF/jarjar/metadata.json") || name.endsWith(".class") || name.endsWith(".jar"); }
        public long maxCapturedBytes(String name) { return name.endsWith(".class") || name.endsWith(".jar") ? 16L << 20 : 1L << 20; }
        public void visit(String name, byte[] bytes) throws IOException {
            if (name.equals("META-INF/mods.toml")) metadata = bytes;
            else if (name.equals("META-INF/jarjar/metadata.json")) jij = bytes;
            else if (name.endsWith(".jar")) nested.put(name, AddOnlyInstaller.hash(bytes));
            else if (name.endsWith(".class")) {
                ClassReader reader = new ClassReader(bytes);
                StagedArtifactValidator.require(name.equals(reader.getClassName() + ".class"), "Installed class name differs from its path");
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (!descriptor.equals("Lnet/minecraftforge/fml/common/Mod;")) return null;
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override public void visit(String key, Object value) {
                                if (!key.equals("value") || !(value instanceof String id) || !id.matches(ForgeMetadataAnalyzer.MOD_ID_PATTERN) || !annotated.add(id))
                                    throw new IllegalArgumentException("Unknown or duplicate installed @Mod identity");
                            }
                        };
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
    }
}
