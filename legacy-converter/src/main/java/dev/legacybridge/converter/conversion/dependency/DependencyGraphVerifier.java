package dev.legacybridge.converter.conversion.dependency;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import dev.legacybridge.converter.metadata.ForgeMetadataAnalyzer;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStoppedException;
import dev.legacybridge.converter.policy.NumericVersionRange;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Metadata-only closure and ModFile ordering. Does not discover files, download dependencies,
 * choose duplicate versions, rewrite metadata, load classes or authorize any conversion.
 * Caller supplies the complete installed/selected cohort separately for each physical side.
 */
public final class DependencyGraphVerifier {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_MODS = 256;
    public static final int MAX_DEPENDENCIES_PER_MOD = 64;
    public static final int MAX_METADATA_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DIAGNOSTICS = 1024;
    private static final Set<String> RESERVED = Set.of("minecraft", "forge", "legacybridge_runtime");
    private static final List<String> LIMITATIONS = List.of(
            "Metadata-only result for the explicitly supplied complete cohort; no class loading, conversion, installation, discovery or runtime behavior was tested.",
            "NumericVersionRange is deliberately stricter than general Maven ranges: no unions/qualifier ordering/support-matrix aliases or development NONE versions; bare numeric versions are treated as exact requirements.",
            "Only the exact catalog runtime version and its exact catalog-generated range are recognized for the qualified runtime baseline.",
            "Version/presence checks follow dependency side, but present BEFORE/AFTER ordering is side-independent on both reviewed Forge versions.",
            "Ordering is grouped by explicit artifact identity. Target ordering towards a secondary modId of another artifact is refused because Forge36 and Forge47 lookup contracts differ.",
            "A deterministic valid topological order is not a prediction of Forge discovery tie-breaking. Missing optional or side-inactive declarations are retained, not removed.");

    public enum Side { CLIENT, SERVER }
    public enum DependencySide { BOTH, CLIENT, SERVER;
        boolean applies(Side side) { return this == BOTH || name().equals(side.name()); }
    }
    public enum Ordering { NONE, BEFORE, AFTER }

    public record Dependency(String modId, boolean mandatory, String versionRange,
                             DependencySide side, Ordering ordering) {
        public Dependency { Objects.requireNonNull(modId); Objects.requireNonNull(versionRange); Objects.requireNonNull(side); Objects.requireNonNull(ordering); }
        public Map<String, Object> toTree() { return Map.of("modId", modId, "mandatory", mandatory, "versionRange", versionRange, "side", side.name(), "ordering", ordering.name()); }
    }

    /** artifactId is an explicit opaque ownership label, not a filesystem path to open. */
    public record Mod(String artifactId, int declarationIndex, String modId, String version, List<Dependency> dependencies) {
        public Mod { Objects.requireNonNull(artifactId); Objects.requireNonNull(modId); Objects.requireNonNull(version); dependencies = List.copyOf(dependencies); }
        public Mod(String artifactId, String modId, String version, List<Dependency> dependencies) { this(artifactId, 0, modId, version, dependencies); }
        public Map<String, Object> toTree() { return Map.of("artifactId", artifactId, "declarationIndex", declarationIndex, "modId", modId, "version", version, "dependencies", dependencies.stream().map(Dependency::toTree).toList()); }
    }

    public record Platform(String minecraftVersion, String forgeVersion, Optional<String> runtimeVersion) {
        public Platform { Objects.requireNonNull(minecraftVersion); Objects.requireNonNull(forgeVersion); Objects.requireNonNull(runtimeVersion); }
        public static Platform source() { return new Platform("1.16.5", "36.2.42", Optional.empty()); }
        public static Platform target() { return new Platform("1.20.1", "47.4.23", Optional.of(AdapterCatalog.RUNTIME_VERSION)); }
        public Map<String, Object> toTree() { return Map.of("minecraft", minecraftVersion, "forge", forgeVersion, "runtime", runtimeVersion.orElse("ABSENT")); }
    }

    public record Installation(Side side, List<Mod> installed, List<Mod> selected, Platform platform) {
        public Installation { Objects.requireNonNull(side); installed = List.copyOf(installed); selected = List.copyOf(selected); Objects.requireNonNull(platform); }
        public Map<String, Object> toTree() { return Map.of("side", side.name(), "platform", platform.toTree(), "installed", installed.stream().map(Mod::toTree).toList(), "selected", selected.stream().map(Mod::toTree).toList()); }
    }

    /** All diagnostics are blocking; an empty actualVersion means no available matching declaration. */
    public record Diagnostic(String code, String artifactId, String ownerModId, String dependencyModId,
                             String requestedRange, String actualVersion, String message) {
        public Map<String, Object> toTree() { return Map.of("code", code, "artifactId", artifactId, "ownerModId", ownerModId,
                "dependencyModId", dependencyModId, "requestedRange", requestedRange, "actualVersion", actualVersion, "message", message); }
    }

    public record ParsedMetadata(String artifactId, String modLoader, String loaderVersion,
                                 List<Mod> mods, List<Diagnostic> diagnostics) {
        public ParsedMetadata { mods = List.copyOf(mods); diagnostics = List.copyOf(diagnostics); }
        public boolean valid() { return diagnostics.isEmpty() && !mods.isEmpty(); }
        public Map<String, Object> toTree() { return Map.of("schemaVersion", SCHEMA_VERSION, "artifactId", artifactId,
                "modLoader", modLoader, "loaderVersion", loaderVersion, "mods", mods.stream().map(Mod::toTree).toList(),
                "status", valid() ? "PARSED" : "BLOCKED", "diagnostics", diagnostics.stream().map(Diagnostic::toTree).toList()); }
    }

    /** Every input dependency occurs once, including inactive/optional-absent declarations. */
    public record EvaluatedDependency(String ownerModId, String artifactId, Dependency declaration,
                                      boolean versionCheckApplies, Optional<String> availableVersion,
                                      Optional<String> availableArtifactId, boolean orderingEdgeAdded) {
        public EvaluatedDependency { Objects.requireNonNull(declaration); Objects.requireNonNull(availableVersion); Objects.requireNonNull(availableArtifactId); }
        public Map<String, Object> toTree() { return Map.of("ownerModId", ownerModId, "artifactId", artifactId, "declaration", declaration.toTree(),
                "versionCheckApplies", versionCheckApplies, "availableVersion", availableVersion.orElse("ABSENT"),
                "availableArtifactId", availableArtifactId.orElse("ABSENT"), "orderingEdgeAdded", orderingEdgeAdded); }
    }

    public record SideResult(Side side, boolean passed, List<String> artifactOrder, List<String> modOrder,
                             List<EvaluatedDependency> dependencies, List<Diagnostic> diagnostics) {
        public SideResult { artifactOrder = List.copyOf(artifactOrder); modOrder = List.copyOf(modOrder); dependencies = List.copyOf(dependencies); diagnostics = List.copyOf(diagnostics); }
        public Map<String, Object> toTree() { return Map.of("side", side.name(), "status", passed ? "PASSED_METADATA_ONLY" : "BLOCKED", "artifactOrder", artifactOrder,
                "modOrder", modOrder, "dependencies", dependencies.stream().map(EvaluatedDependency::toTree).toList(), "diagnostics", diagnostics.stream().map(Diagnostic::toTree).toList()); }
    }

    public record Result(boolean passed, Map<Side, SideResult> sides, List<Diagnostic> diagnostics,
                         List<Installation> declarations, List<String> limitations) {
        public Result { Map<Side, SideResult> copy = new EnumMap<>(Side.class); copy.putAll(sides); sides = Collections.unmodifiableMap(copy); diagnostics = List.copyOf(diagnostics); declarations = List.copyOf(declarations); limitations = List.copyOf(limitations); }
        public Map<String, Object> toTree() {
            Map<String, Object> bySide = new TreeMap<>(); sides.forEach((side, value) -> bySide.put(side.name(), value.toTree()));
            return Map.of("schemaVersion", SCHEMA_VERSION, "reportType", "LEGACY_DEPENDENCY_GRAPH", "status", passed ? "PASSED_METADATA_ONLY" : "BLOCKED",
                    "sides", Collections.unmodifiableMap(bySide), "diagnostics", diagnostics.stream().map(Diagnostic::toTree).toList(),
                    "declarations", declarations.stream().map(Installation::toTree).toList(), "limitations", limitations,
                    "classesExecuted", false, "conversionAuthorized", false);
        }
    }

    /** Parsing preserves raw dependency values and original [[mods]] order; no input file is read. */
    public ParsedMetadata parseMetadata(String artifactId, byte[] modsToml, Optional<String> exactJarVersion) {
        Objects.requireNonNull(artifactId); Objects.requireNonNull(exactJarVersion); OperationControl.checkpoint();
        if (!validArtifact(artifactId) || modsToml == null || modsToml.length > MAX_METADATA_BYTES)
            return parsedFailure(artifactId, "METADATA_LIMIT", "Expected a bounded artifact identity and at most 2 MiB mods.toml");
        var analysis = new ForgeMetadataAnalyzer().analyze(modsToml);
        OperationControl.checkpoint();
        if (!analysis.valid()) return parsedFailure(artifactId, "INVALID_METADATA", "Existing Forge metadata parser rejected input: "
                + analysis.findings().stream().map(finding -> finding.code()).limit(16).toList());
        try {
            var config = new TomlParser().parse(new StringReader(new String(modsToml, StandardCharsets.UTF_8)));
            List<?> originalOrder = config.get("mods");
            UnmodifiableConfig dependencyTables = config.get("dependencies");
            if (dependencyTables != null) for (Object table : dependencyTables.valueMap().values()) for (Object value : (List<?>) table) {
                OperationControl.checkpoint();
                UnmodifiableConfig dependency = (UnmodifiableConfig) value;
                if (!Set.of("modId", "mandatory", "versionRange", "side", "ordering").containsAll(dependency.valueMap().keySet()))
                    return parsedFailure(artifactId, "UNSUPPORTED_DEPENDENCY_METADATA", "Unknown dependency fields cannot be silently discarded");
            }
            if (originalOrder.size() > MAX_MODS || analysis.summary().dependencies().size() > MAX_MODS * MAX_DEPENDENCIES_PER_MOD)
                return parsedFailure(artifactId, "METADATA_LIMIT", "Mod/dependency count exceeds graph bounds");
            List<Mod> mods = new ArrayList<>(); List<Diagnostic> diagnostics = new ArrayList<>();
            for (int index = 0; index < originalOrder.size(); index++) {
                OperationControl.checkpoint();
                UnmodifiableConfig raw = (UnmodifiableConfig) originalOrder.get(index);
                String id = raw.get("modId");
                if (raw.valueMap().containsKey("version") && !(raw.get("version") instanceof String))
                    return parsedFailure(artifactId, "INVALID_METADATA", "Declared mod version must be a string, not coerced from another TOML type");
                String version = analysis.summary().mods().stream().filter(mod -> mod.modId().equals(id)).findFirst().orElseThrow().version();
                if (version.equals("${file.jarVersion}")) {
                    if (exactJarVersion.isEmpty()) return parsedFailure(artifactId, "UNRESOLVED_VERSION", "Explicit exact manifest version is required for ${file.jarVersion}");
                    version = exactJarVersion.orElseThrow();
                }
                List<Dependency> dependencies = analysis.summary().dependencies().stream().filter(dep -> dep.ownerModId().equals(id))
                        .map(dep -> new Dependency(dep.modId(), dep.mandatory(), dep.versionRange(), DependencySide.valueOf(dep.side()), Ordering.valueOf(dep.ordering()))).toList();
                Mod mod = new Mod(artifactId, index, id, version, dependencies); mods.add(mod); validateMod(mod, diagnostics);
            }
            return new ParsedMetadata(artifactId, analysis.summary().modLoader(), analysis.summary().loaderVersion(), mods, diagnostics);
        } catch (OperationStoppedException stopped) { throw stopped; }
        catch (RuntimeException malformed) { return parsedFailure(artifactId, "INVALID_METADATA", "Cannot preserve bounded metadata declarations: " + malformed.getClass().getSimpleName()); }
    }

    public Result verify(List<Installation> installations) {
        Objects.requireNonNull(installations); OperationControl.checkpoint();
        Map<Side, SideResult> results = new EnumMap<>(Side.class); List<Diagnostic> diagnostics = new ArrayList<>();
        if (installations.isEmpty() || installations.size() > 2) {
            diagnostics.add(diagnostic("INVALID_INSTALLATION_SET", "", "Expected one or two explicit, distinct physical sides"));
            return new Result(false, results, diagnostics, List.of(), LIMITATIONS);
        }
        for (Installation installation : installations) {
            Objects.requireNonNull(installation); OperationControl.checkpoint();
            if (results.containsKey(installation.side())) {
                diagnostics.add(diagnostic("DUPLICATE_INSTALLATION_SIDE", "", installation.side().name()));
                continue;
            }
            SideResult result = side(installation); results.put(installation.side(), result);
        }
        return new Result(diagnostics.isEmpty() && results.values().stream().allMatch(SideResult::passed), results, diagnostics, installations, LIMITATIONS);
    }

    private SideResult side(Installation installation) {
        List<Diagnostic> diagnostics = new ArrayList<>(); List<EvaluatedDependency> evaluated = new ArrayList<>();
        if (!installation.platform().equals(Platform.source()) && !installation.platform().equals(Platform.target()))
            diagnostics.add(diagnostic("UNVERIFIED_PLATFORM", "", "Only exact source36.2.42/1.16.5 or target47.4.23/1.20.1/catalog runtime cohorts are verified"));
        if ((long) installation.installed().size() + installation.selected().size() > MAX_MODS) {
            diagnostics.add(diagnostic("GRAPH_LIMIT", "", "At most " + MAX_MODS + " non-platform mod declarations per side"));
            return blocked(installation.side(), evaluated, diagnostics);
        }
        List<Mod> all = new ArrayList<>(installation.installed()); all.addAll(installation.selected());
        Map<String, Mod> ids = new TreeMap<>(); Map<String, List<Mod>> artifacts = new TreeMap<>();
        Set<String> installedArtifacts = new HashSet<>(); installation.installed().forEach(mod -> installedArtifacts.add(mod.artifactId()));
        Set<String> selectedArtifacts = new HashSet<>(); installation.selected().forEach(mod -> selectedArtifacts.add(mod.artifactId()));
        for (String artifact : installedArtifacts) if (selectedArtifacts.contains(artifact)) diagnostics.add(diagnostic("ARTIFACT_ORIGIN_CONFLICT", artifact, "One artifact cannot be partly installed and partly selected"));
        for (Mod mod : all) {
            OperationControl.checkpoint(); validateMod(mod, diagnostics);
            if (RESERVED.contains(mod.modId())) diagnostics.add(diagnostic("RESERVED_MOD_ID", mod.artifactId(), "Platform identity cannot be supplied as an installed/selected mod: " + mod.modId()));
            Mod previous = ids.putIfAbsent(mod.modId(), mod);
            if (previous != null) diagnostics.add(new Diagnostic("DUPLICATE_MOD_ID", mod.artifactId(), mod.modId(), mod.modId(), "", previous.version(), "Duplicate declaration, including selected versus installed; no newest-version selection"));
            artifacts.computeIfAbsent(mod.artifactId(), ignored -> new ArrayList<>()).add(mod);
        }
        for (var artifact : artifacts.entrySet()) {
            artifact.getValue().sort(Comparator.comparingInt(Mod::declarationIndex));
            for (int index = 0; index < artifact.getValue().size(); index++) if (artifact.getValue().get(index).declarationIndex() != index)
                diagnostics.add(diagnostic("INCOMPLETE_ARTIFACT_DECLARATIONS", artifact.getKey(), "Declare every mod in the artifact once, in contiguous original indices starting at zero"));
        }
        if (!diagnostics.isEmpty()) return blocked(installation.side(), evaluated, bounded(diagnostics));
        addPlatform(ids, artifacts, "minecraft", installation.platform().minecraftVersion());
        addPlatform(ids, artifacts, "forge", installation.platform().forgeVersion());
        installation.platform().runtimeVersion().ifPresent(version -> addPlatform(ids, artifacts, "legacybridge_runtime", version));
        Map<String, Set<String>> graph = new TreeMap<>(); artifacts.keySet().forEach(artifact -> graph.put(artifact, new TreeSet<>()));
        for (Mod owner : all) for (Dependency dependency : owner.dependencies()) {
            OperationControl.checkpoint(); Mod target = ids.get(dependency.modId());
            boolean applies = dependency.side().applies(installation.side()); boolean edge = false;
            if (!supportedRange(dependency)) diagnostics.add(problem("UNSUPPORTED_VERSION_RANGE", owner, dependency, target, "No supported numeric or exact catalog-runtime range grammar; declaration is retained and blocked"));
            else if (applies) {
                if (target == null && dependency.mandatory()) diagnostics.add(problem("MISSING_MANDATORY_DEPENDENCY", owner, dependency, null, "Required mod is absent from the explicit side cohort"));
                else if (target != null && !dependency.versionRange().isEmpty()) {
                    if (!target.version().matches("[0-9]+(?:\\.[0-9]+)*") && !knownRuntime(dependency, target.version()))
                        diagnostics.add(problem("VERSION_COMPARISON_UNSUPPORTED", owner, dependency, target, "Qualified/non-numeric versions require a separately verified comparison rule"));
                    else if (!matches(dependency, target.version())) diagnostics.add(problem(dependency.mandatory() ? "MANDATORY_VERSION_MISMATCH" : "OPTIONAL_VERSION_MISMATCH", owner, dependency, target, "Present dependency version does not satisfy the declared supported range"));
                }
            }
            // Forge 36 and 47 add ordering independently from the dependency side filter.
            if (target != null && dependency.ordering() != Ordering.NONE && !owner.artifactId().equals(target.artifactId())) {
                if (installation.platform().equals(Platform.target()) && target.declarationIndex() != 0)
                    diagnostics.add(problem("ORDERING_CONTRACT_DIVERGENCE", owner, dependency, target, "Forge47 ordering lookup indexes only first modIds; source Forge36 indexes all modIds. Automatic equivalence is not proved"));
                else {
                    String before = dependency.ordering() == Ordering.BEFORE ? owner.artifactId() : target.artifactId();
                    String after = dependency.ordering() == Ordering.BEFORE ? target.artifactId() : owner.artifactId();
                    graph.get(before).add(after); edge = true;
                }
            }
            evaluated.add(new EvaluatedDependency(owner.modId(), owner.artifactId(), dependency, applies,
                    Optional.ofNullable(target).map(Mod::version), Optional.ofNullable(target).map(Mod::artifactId), edge));
        }
        List<String> order = topological(graph);
        if (order.size() != graph.size()) diagnostics.add(diagnostic("ORDERING_CYCLE", "", "BEFORE/AFTER graph contains a cycle between artifact groups; no partial order is authorized"));
        if (!diagnostics.isEmpty()) return blocked(installation.side(), evaluated, bounded(diagnostics));
        List<String> modOrder = order.stream().flatMap(artifact -> artifacts.get(artifact).stream().map(Mod::modId)).toList();
        return new SideResult(installation.side(), true, order, modOrder, evaluated, List.of());
    }

    private static void validateMod(Mod mod, List<Diagnostic> diagnostics) {
        if (!validArtifact(mod.artifactId()) || mod.artifactId().startsWith("platform:") || !ForgeMetadataAnalyzer.isValidModId(mod.modId())
                || mod.declarationIndex() < 0 || mod.declarationIndex() >= MAX_MODS || !validVersion(mod.version()))
            diagnostics.add(diagnostic("INVALID_MOD_DECLARATION", mod.artifactId(), "Invalid identity/index/exact bounded version for " + mod.modId()));
        if (mod.dependencies().size() > MAX_DEPENDENCIES_PER_MOD) {
            diagnostics.add(diagnostic("GRAPH_LIMIT", mod.artifactId(), "At most " + MAX_DEPENDENCIES_PER_MOD + " dependencies per mod")); return;
        }
        for (Dependency dependency : mod.dependencies()) {
            OperationControl.checkpoint();
            if (!ForgeMetadataAnalyzer.isValidModId(dependency.modId()) || dependency.versionRange().length() > 512
                    || dependency.versionRange().chars().anyMatch(Character::isISOControl))
                diagnostics.add(problem("INVALID_DEPENDENCY_DECLARATION", mod, dependency, null, "Invalid identity or bounded range"));
        }
    }
    private static boolean validArtifact(String id) { return !id.isBlank() && id.length() <= 256 && id.chars().noneMatch(Character::isISOControl); }
    private static boolean validVersion(String version) { return version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}") && !Set.of("NONE", "0.0NONE").contains(version); }
    private static String runtimeRange() { return ModMetadataAdapter.targetRanges("1.16.5", "36.2.42").get("legacybridge_runtime"); }
    private static boolean knownRuntime(Dependency dependency, String version) {
        return dependency.modId().equals("legacybridge_runtime") && version.equals(AdapterCatalog.RUNTIME_VERSION)
                && Set.of("[" + AdapterCatalog.RUNTIME_VERSION + "]", runtimeRange()).contains(dependency.versionRange().trim());
    }
    private static boolean supportedRange(Dependency dependency) {
        return dependency.versionRange().isEmpty() || NumericVersionRange.isSupported(dependency.versionRange()) || knownRuntime(dependency, AdapterCatalog.RUNTIME_VERSION);
    }
    private static boolean matches(Dependency dependency, String version) {
        return dependency.versionRange().isEmpty() || knownRuntime(dependency, version) || NumericVersionRange.contains(dependency.versionRange(), version);
    }
    private static void addPlatform(Map<String, Mod> ids, Map<String, List<Mod>> artifacts, String id, String version) {
        Mod mod = new Mod("platform:" + id, id, version, List.of()); ids.put(id, mod); artifacts.put(mod.artifactId(), List.of(mod));
    }
    private static List<String> topological(Map<String, Set<String>> graph) {
        Map<String, Integer> indegree = new TreeMap<>(); graph.keySet().forEach(node -> indegree.put(node, 0));
        graph.values().forEach(targets -> targets.forEach(target -> indegree.compute(target, (key, value) -> value + 1)));
        PriorityQueue<String> ready = new PriorityQueue<>(); indegree.forEach((node, degree) -> { if (degree == 0) ready.add(node); });
        List<String> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            OperationControl.checkpoint(); String node = ready.remove(); result.add(node);
            for (String target : graph.get(node)) if (indegree.compute(target, (key, value) -> value - 1) == 0) ready.add(target);
        }
        return result;
    }
    private static ParsedMetadata parsedFailure(String artifact, String code, String message) { return new ParsedMetadata(artifact, "", "", List.of(), List.of(diagnostic(code, artifact, message))); }
    private static SideResult blocked(Side side, List<EvaluatedDependency> dependencies, List<Diagnostic> diagnostics) { return new SideResult(side, false, List.of(), List.of(), dependencies, diagnostics); }
    private static Diagnostic diagnostic(String code, String artifact, String message) { return new Diagnostic(code, artifact, "", "", "", "", message); }
    private static Diagnostic problem(String code, Mod owner, Dependency dependency, Mod target, String message) { return new Diagnostic(code, owner.artifactId(), owner.modId(), dependency.modId(), dependency.versionRange(), target == null ? "" : target.version(), message); }
    private static List<Diagnostic> bounded(List<Diagnostic> diagnostics) {
        if (diagnostics.size() <= MAX_DIAGNOSTICS) return diagnostics;
        List<Diagnostic> result = new ArrayList<>(diagnostics.subList(0, MAX_DIAGNOSTICS - 1));
        result.add(diagnostic("DIAGNOSTIC_LIMIT", "", "More blocking diagnostics exist; all declarations remain in Result.declarations and no result order is returned")); return result;
    }
}
