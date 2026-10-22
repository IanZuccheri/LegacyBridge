package dev.legacybridge.converter.conversion;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import dev.legacybridge.converter.conversion.dependency.DependencyGraphVerifier;
import dev.legacybridge.converter.conversion.dependency.DependencyGraphVerifier.*;
import dev.legacybridge.converter.conversion.extract.SubsetRejectionException;
import dev.legacybridge.converter.metadata.ForgeMetadataAnalyzer;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.policy.NumericVersionRange;
import dev.legacybridge.converter.transformation.TransformationException;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Platform-range rewrite bound to ModMetadataAdapter.targetRanges; every original edge is retained. */
final class LifecycleMetadata {
    private LifecycleMetadata() { }
    static void verify(byte[] bytes, String version, boolean target, Map<String,Object> report) {
        require(report != null, "INVALID_REQUEST: Report map is required");
        require(validVersion(version), "INVALID_REQUEST: Exact bounded mod version is required");
        byte[] snapshot = snapshot(bytes);
        var verifier = new DependencyGraphVerifier();
        var parsed = verifier.parseMetadata("converted-mod", snapshot, Optional.of(version));
        report.put(target ? "targetDependencyMetadata" : "sourceDependencyMetadata", parsed.toTree());
        if (!parsed.valid()) throw new TransformationException("LIFECYCLE_DEPENDENCY_METADATA: " + summarize(parsed.diagnostics()));
        require(parsed.mods().size() == 1, "SINGLE_MOD_REQUIRED: Lifecycle metadata admits exactly one mod per artifact");
        require(parsed.mods().get(0).version().equals(version), "VERSION_CHANGED: Supplied exact version differs from source metadata");
        require("javafml".equals(parsed.modLoader()) && NumericVersionRange.contains(parsed.loaderVersion(), target ? "47" : "36"),
                "LOADER_CONTRACT_UNSUPPORTED: Metadata excludes the pinned JavaFML major or uses unsupported range grammar");
        var platform = target ? Platform.target() : Platform.source();
        var result = verifier.verify(List.of(new Installation(Side.CLIENT, List.of(), parsed.mods(), platform),
                new Installation(Side.SERVER, List.of(), parsed.mods(), platform)));
        report.put(target ? "targetDependencyGraph" : "sourceDependencyGraph", result.toTree());
        if (!result.passed()) {
            List<Diagnostic> errors = new ArrayList<>(result.diagnostics());
            result.sides().values().forEach(side -> errors.addAll(side.diagnostics()));
            throw new TransformationException("LIFECYCLE_DEPENDENCY_BLOCKED: " + summarize(errors));
        }
        List<Dependency> dependencies = parsed.mods().get(0).dependencies();
        require(dependencies.stream().anyMatch(dependency -> dependency.modId().equals("minecraft") && dependency.mandatory()),
                "MISSING_MINECRAFT_CONTRACT: Mandatory Minecraft metadata is required for this finite profile");
        List<Dependency> runtime = dependencies.stream().filter(dependency -> dependency.modId().equals("legacybridge_runtime")).toList();
        if (target) {
            require(runtime.equals(List.of(runtimeDependency())), "RUNTIME_CONTRACT_CHANGED: Exactly the generated mandatory BOTH/AFTER runtime dependency is required");
        } else require(runtime.isEmpty(), "RUNTIME_MIGRATION_REQUIRED: Original already declares the runtime; explicit migration is required");
    }
    static byte[] rewrite(byte[] bytes, String modId, String version) throws SubsetRejectionException {
        require(ForgeMetadataAnalyzer.isValidModId(modId) && !Set.of("minecraft", "forge", "legacybridge_runtime").contains(modId),
                "INVALID_MOD_ID: Exact non-platform mod identity is required");
        byte[] original = snapshot(bytes);
        // This method is safe when called independently: validation is not inferred from a prior caller.
        verify(original, version, false, new TreeMap<>());
        var verifier = new DependencyGraphVerifier();
        var before = verifier.parseMetadata("converted-mod", original, Optional.of(version));
        require(before.mods().get(0).modId().equals(modId), "MOD_ID_CHANGED: Requested owner differs from the metadata identity");
        // Reuse existing active metadata validation, not its dependency-replacing result.
        SimpleContentConverter.targetMetadata(original, modId, version);
        Config config = new TomlParser().parse(new StringReader(new String(original, StandardCharsets.UTF_8)));
        Map<String,String> ranges = ModMetadataAdapter.targetRanges("1.16.5", "36.2.42");
        List<Config> mods = config.get("mods"); mods.get(0).set("version", version);
        config.set("loaderVersion", ranges.get("loaderVersion"));
        Config dependencyGroups = config.get("dependencies");
        require(dependencyGroups != null, "MISSING_DEPENDENCY_GROUP: Source declarations are required");
        List<Config> source = dependencyGroups.get(modId);
        require(source != null, "MISSING_DEPENDENCY_GROUP: Source owner has no dependency group");
        List<Config> preserved = new ArrayList<>(source);
        for (Config dependency : preserved) {
            OperationControl.checkpoint();
            String id = dependency.get("modId");
            if (id.equals("minecraft") || id.equals("forge")) dependency.set("versionRange", ranges.get(id));
            if (id.equals("legacybridge_runtime")) throw new TransformationException("Original already requires runtime; explicit migration required");
        }
        Config runtime = Config.inMemory(); runtime.set("modId", "legacybridge_runtime");
        runtime.set("mandatory", true); runtime.set("versionRange", ranges.get("legacybridge_runtime"));
        runtime.set("side", "BOTH"); runtime.set("ordering", "AFTER"); preserved.add(runtime);
        dependencyGroups.set(modId, preserved);
        StringWriter result = new StringWriter(); TomlWriter writer = new TomlWriter(); writer.setNewline("\n"); writer.write(config, result);
        byte[] output = result.toString().getBytes(StandardCharsets.UTF_8);
        verify(output, version, true, new TreeMap<>());
        var after = verifier.parseMetadata("converted-mod", output, Optional.of(version));
        require(after.mods().get(0).modId().equals(modId), "MOD_ID_CHANGED: Serialized metadata identity changed");
        List<Dependency> expected = new ArrayList<>();
        for (Dependency dependency : before.mods().get(0).dependencies()) expected.add(new Dependency(dependency.modId(), dependency.mandatory(),
                Set.of("minecraft", "forge").contains(dependency.modId()) ? ranges.get(dependency.modId()) : dependency.versionRange(), dependency.side(), dependency.ordering()));
        expected.add(runtimeDependency());
        require(frequencies(expected).equals(frequencies(after.mods().get(0).dependencies())),
                "DEPENDENCY_PRESERVATION_FAILED: Serialized output lost or changed an unapproved edge component");
        return output;
    }

    private static Dependency runtimeDependency() {
        return new Dependency("legacybridge_runtime", true, ModMetadataAdapter.targetRanges("1.16.5", "36.2.42").get("legacybridge_runtime"), DependencySide.BOTH, Ordering.AFTER);
    }
    private static Map<Dependency, Integer> frequencies(List<Dependency> dependencies) {
        Map<Dependency, Integer> result = new HashMap<>(); dependencies.forEach(dependency -> result.merge(dependency, 1, Integer::sum)); return result;
    }
    private static byte[] snapshot(byte[] bytes) {
        OperationControl.checkpoint();
        require(bytes != null && bytes.length <= DependencyGraphVerifier.MAX_METADATA_BYTES, "INVALID_METADATA: Expected at most 2 MiB of metadata bytes");
        return bytes.clone();
    }
    private static boolean validVersion(String version) { return version != null && version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}"); }
    private static String summarize(List<Diagnostic> errors) {
        String summary = errors.stream().limit(8).map(error -> error.code() + " " + error.ownerModId() + " -> " + error.dependencyModId()
                + " requested=" + error.requestedRange() + " actual=" + error.actualVersion()).collect(java.util.stream.Collectors.joining("; "));
        if (errors.size() > 8) summary += "; additional diagnostics retained in the graph report";
        return summary.length() <= 4096 ? summary : summary.substring(0, 4096) + "... (full diagnostics retained in report)";
    }
    private static void require(boolean condition, String message) { if (!condition) throw new TransformationException("LIFECYCLE_METADATA: " + message); }
}
