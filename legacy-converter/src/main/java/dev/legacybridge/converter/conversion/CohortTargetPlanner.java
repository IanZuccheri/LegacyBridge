package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.archive.*;
import dev.legacybridge.converter.conversion.dependency.*;
import dev.legacybridge.converter.mapping.MappingConfidence;
import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import dev.legacybridge.converter.transformation.*;
import dev.legacybridge.converter.transformation.linkage.OfflineLinkageVerifier;
import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.CohortMetadataAdapter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Finite target recipe, not a general JDK/Forge compatibility rule. The reviewed complete
 * original archives are pinned, so a changed body, resource or dependency requires a new review.
 * Classes (including cross-JAR calls and debug data) remain byte-identical. Only concrete
 * metadata adapters run. Both reopened target candidates must pass before a result is returned.
 * No original or target class is defined/executed and no candidate is published or installed.
 */
public final class CohortTargetPlanner {
    private static final String MOD = "net/minecraftforge/fml/common/Mod";
    private static final String TOML = "META-INF/mods.toml", MANIFEST = "META-INF/MANIFEST.MF";
    private static final Map<String, String> REVIEWED = FiniteCohortContract.REVIEWED;
    private static final Map<String, Set<String>> CLASSES = FiniteCohortContract.CLASSES;

    public record Candidate(String artifactId, String modId, String sourceSha256, String sha256, long bytes,
                            Map<String, Object> proof) {
        public Candidate { proof = SimpleContentConverter.canonicalTree(proof); }
        public Map<String, Object> toTree() { return Map.of("artifactId", artifactId, "modId", modId, "sourceSha256", sourceSha256,
                "sha256", sha256, "bytes", bytes, "kind", "PRIVATE_SERIALIZED_JAR", "proof", proof); }
    }

    public record Result(boolean validated, CohortSourceInspector.Result source, List<Candidate> candidates,
                         Optional<DependencyGraphVerifier.Result> targetGraph, Map<String, Object> transformationLog,
                         List<EngineError> diagnostics) {
        public Result {
            candidates = List.copyOf(candidates); diagnostics = List.copyOf(diagnostics);
            transformationLog = SimpleContentConverter.canonicalTree(transformationLog);
            if (validated != (candidates.size() == 2 && diagnostics.isEmpty() && source.sourceLinkagePassed()
                    && targetGraph.isPresent() && targetGraph.orElseThrow().passed()))
                throw new IllegalArgumentException("A target result requires the entire validated pair");
            if (!validated && !candidates.isEmpty()) throw new IllegalArgumentException("Partial candidates cannot escape");
        }
        public Map<String, Object> toTree() {
            var tree = new TreeMap<String, Object>();
            tree.put("schemaVersion", 1); tree.put("reportType", "LEGACY_COHORT_TARGET_PLAN");
            tree.put("recipe", CohortMetadataAdapter.ID); tree.put("recipeRevision", 1);
            tree.put("status", validated ? "STATIC_PAIR_VALIDATED" : "BLOCKED");
            tree.put("sourceInspection", source.toTree()); tree.put("candidates", candidates.stream().map(Candidate::toTree).toList());
            tree.put("targetDependencyGraph", targetGraph.map(DependencyGraphVerifier.Result::toTree).orElse(Map.of()));
            tree.put("transformationLog", transformationLog);
            tree.put("transformationDisposition", validated ? "VALIDATED_PRIVATE_ONLY" : "DISCARDED_ATTEMPT");
            tree.put("diagnostics", diagnostics.stream().map(e -> Map.of("code", e.code(), "message", e.message(), "remediation", e.remediation())).toList());
            tree.put("publicationAllowed", false); tree.put("installationAllowed", false); tree.put("classesExecuted", false);
            tree.put("runtimeValidation", "NOT_RUN"); tree.put("multiplayerValidation", "NOT_RUN");
            tree.put("limitations", List.of("Exactly the two reviewed authored 1.0.0 source archives; no arbitrary library or gameplay admission.",
                    "Only metadata changes. Target bodies/debug/constant pools remain byte-identical; no HIGH mapping is promoted.",
                    "Target linkage is checked separately per JAR using only required providers' declarations and JDK17/@Mod roots.",
                    "Source metadata inspection remains a source-only subreport. This enclosing report adds static target evidence, not game discovery or behavior.",
                    "Both private candidates are serialized, reopened and removed; no output paths, Apply authority or installation journal.",
                    "Data scope/provenance is explicit in sourceInspection; included data admits only the reviewed originals, not broader APIs. No hidden lookup/download. Forge module wiring and isolated execution remain untested."));
            return SimpleContentConverter.canonicalTree(tree);
        }
    }

    public Result plan(CohortPlanningRequest request, MappingInputs inputs) {
        return plan(request, inputs, CohortTargetPlanner::reopen);
    }

    // Trusted package-private IO seam. Returned bytes still undergo complete equality/linkage validation.
    interface RoundTrip { Map<String, byte[]> reopen(String artifactId, byte[] jar) throws IOException, InputRejectedException; }

    Result plan(CohortPlanningRequest request, MappingInputs inputs, RoundTrip roundTrip) {
        var source = new CohortSourceInspector().inspect(request.source(), inputs);
        var log = new TransformationLog();
        DependencyGraphVerifier.Result graph = null;
        try {
            require(source.sourceLinkagePassed(), "COHORT_SOURCE_BLOCKED", "Source closure/linkage failed; inspect source diagnostics");
            require(source.artifacts().size() == 2 && source.artifacts().stream().map(a -> a.mod().modId()).collect(java.util.stream.Collectors.toSet()).equals(REVIEWED.keySet()),
                    "FINITE_COHORT_RECIPE_REQUIRED", "Only the complete reviewed library+consumer pair is admitted");
            // Whole-set admission before any target serialization. Labels/filenames are not proof.
            for (var artifact : source.artifacts()) require(artifact.sha256().equals(REVIEWED.get(artifact.mod().modId())),
                    "FINITE_COHORT_RECIPE_REQUIRED", "No verified recipe for selected bytes: " + artifact.id());
            var originals = new TreeMap<String, Map<String, byte[]>>();
            for (var selected : request.source().artifacts()) {
                OperationControl.checkpoint();
                var captured = new Capture();
                var scan = new SafeJarReader().scan(selected.input(), captured);
                require(scan.input().sha256().equals(selected.expectedSha256()), "INPUT_CHANGED", selected.id());
                originals.put(selected.id(), captured.entries);
            }
            var reopened = new TreeMap<String, Map<String, byte[]>>();
            var serialized = new TreeMap<String, byte[]>();
            var targetMods = new ArrayList<DependencyGraphVerifier.Mod>();
            int processed = 0;
            for (var artifact : source.artifacts()) {
                OperationControl.progress(OperationStage.ADAPTER_TRANSFORMATION, processed, 2, "Rewriting finite cohort metadata: " + artifact.id());
                String id = artifact.id(), modId = artifact.mod().modId();
                var original = originals.get(id);
                Set<String> allowed = new HashSet<>(CLASSES.get(modId)); allowed.add(TOML); allowed.add(MANIFEST);
                require(original.keySet().equals(allowed), "FINITE_ENTRY_OWNERSHIP", id);
                var target = new TreeMap<>(original);
                String bindings = "META-INF/legacybridge/" + modId + "/adapters.json";
                target.put(TOML, CohortMetadataAdapter.targetToml(modId, original.get(TOML)));
                target.put(MANIFEST, CohortMetadataAdapter.targetManifest(modId, original.get(MANIFEST)));
                target.put(bindings, new CanonicalReportWriter().toJsonTree(SimpleContentConverter.canonicalTree(CohortMetadataAdapter.bindingTree())).getBytes(StandardCharsets.UTF_8));
                var rules = new TreeMap<>(Map.of(TOML, AdapterCatalog.COHORT_TOML, MANIFEST, AdapterCatalog.COHORT_MANIFEST, bindings, AdapterCatalog.COHORT_BINDINGS));
                rules.forEach((path, rule) -> log.add(TransformationLog.Kind.RESOURCE_REWRITE, id + "!/" + path,
                        fingerprint(original.get(path)), fingerprint(target.get(path)), rule + ":revision-1", MappingConfidence.VERIFIED,
                        AdapterCatalog.require(rule).transformation(), rule));
                OperationControl.progress(OperationStage.SERIALIZATION, processed, 2, "Serializing private cohort candidate: " + id);
                byte[] jar = SimpleContentConverter.jar(target);
                var actual = roundTrip.reopen(id, jar);
                verifyEntries(target, actual);
                var metadata = new DependencyGraphVerifier().parseMetadata(id, actual.get(TOML), Optional.of("1.0.0"));
                require(metadata.valid() && metadata.mods().size() == 1 && metadata.modLoader().equals("javafml")
                        && metadata.loaderVersion().equals("[47,48)"), "TARGET_METADATA", id);
                var mod = metadata.mods().get(0);
                require(mod.modId().equals(modId) && mod.version().equals(artifact.mod().version()), "TARGET_IDENTITY", id);
                targetMods.add(mod); reopened.put(id, actual); serialized.put(id, jar); processed++;
            }
            OperationControl.stage(OperationStage.SEMANTIC_VALIDATION, "Verifying both reopened target dependencies and ordering");
            graph = new DependencyGraphVerifier().verify(Arrays.stream(DependencyGraphVerifier.Side.values()).map(side ->
                    new DependencyGraphVerifier.Installation(side, List.of(), targetMods, DependencyGraphVerifier.Platform.target())).toList());
            require(graph.passed(), "TARGET_DEPENDENCY_GRAPH", "Reopened target pair has invalid closure/version/order");
            var result = new ArrayList<Candidate>();
            for (var artifact : source.artifacts()) {
                OperationControl.stage(OperationStage.BYTECODE_VALIDATION, "Validating reopened target owner: " + artifact.id());
                var external = new TreeMap<String, ApiIndex.ApiClass>();
                external.put(MOD, inputs.targetApi().getClass(MOD));
                var mod = targetMods.stream().filter(m -> m.artifactId().equals(artifact.id())).findFirst().orElseThrow();
                // A dependency contributes declarations, never its bodies as owned classes.
                for (var provider : source.artifacts()) if (!provider.id().equals(artifact.id()) && mod.dependencies().stream().anyMatch(d ->
                        d.modId().equals(provider.mod().modId()) && d.mandatory() && d.side() == DependencyGraphVerifier.DependencySide.BOTH))
                    for (var owned : provider.classes()) external.put(owned.name(), owned.sourceApi()); // exact byte preservation proven above
                var entries = reopened.get(artifact.id());
                Map<String, byte[]> classes = new TreeMap<>();
                for (String name : CLASSES.get(mod.modId())) classes.put(name, entries.get(name));
                new LegacyReferenceScanner().scan(classes);
                var api = new ApiIndex(external);
                var hierarchy = new HashMap<>(OfflineLinkageVerifier.declarationHierarchy(api));
                classes.forEach((path, bytes) -> hierarchy.put(path.substring(0, path.length() - 6), bytes));
                for (byte[] bytes : classes.values()) BytecodeValidator.validate(bytes, hierarchy);
                var linkage = new OfflineLinkageVerifier().verify(classes, api);
                var ownedHashes = new TreeMap<String, String>(); classes.forEach((name, bytes) -> ownedHashes.put(name, SimpleContentConverter.hash(bytes)));
                byte[] jar = serialized.get(artifact.id());
                result.add(new Candidate(artifact.id(), mod.modId(), artifact.sha256(), SimpleContentConverter.hash(jar), jar.length,
                        Map.of("targetMetadata", mod.toTree(), "offlineLinkage", linkage.toTree(), "ownedClassSha256", ownedHashes,
                                "unchangedClassBytes", true, "asmValidation", "PASSED_REOPENED", "entryCount", entries.size(),
                                "archiveCrcAndHashes", "PASSED_REOPENED", "scratchDeleted", true)));
            }
            // Recheck original identities after all work, including the second candidate. No original is written.
            for (var selected : request.source().artifacts()) {
                var scan = new SafeJarReader().scan(selected.input(), new Capture());
                require(scan.input().sha256().equals(selected.expectedSha256()), "INPUT_CHANGED", selected.id());
            }
            OperationControl.progress(OperationStage.PREPARED, 2, 2, "Both private candidates validated; publication remains disabled");
            return new Result(true, source, result, Optional.of(graph), log.toTree(), List.of());
        } catch (Rejected rejected) { return blocked(source, graph, log, rejected.code, rejected.getMessage()); }
        catch (InputRejectedException rejected) { return blocked(source, graph, log, rejected.reasonCode(), rejected.getMessage()); }
        catch (TransformationException | UnsupportedCompatibilityException rejected) { return blocked(source, graph, log, "TARGET_CONTRACT_REJECTED", rejected.getMessage()); }
        catch (IOException failure) { return blocked(source, graph, log, "COHORT_TARGET_IO", failure.getMessage()); }
    }

    private static Result blocked(CohortSourceInspector.Result source, DependencyGraphVerifier.Result graph, TransformationLog log, String code, String message) {
        OperationControl.checkpoint();
        return new Result(false, source, List.of(), Optional.ofNullable(graph), log.toTree(), List.of(new EngineError(code,
                Objects.toString(message, "Candidate rejected"), "No candidate approved or installed. Review the exact pair and its diagnostic; metadata rewrites logged before rejection are attempts only.")));
    }

    static void verifyEntries(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        require(expected.keySet().equals(actual.keySet()), "TARGET_ENTRY_DRIFT", "Reopened entry set differs");
        for (String name : expected.keySet()) {
            OperationControl.checkpoint();
            require(Arrays.equals(expected.get(name), actual.get(name)), "TARGET_ENTRY_DRIFT", "Reopened bytes differ: " + name);
        }
    }

    private static Map<String, byte[]> reopen(String id, byte[] jar) throws IOException, InputRejectedException {
        return reopen(jar, null);
    }

    static Map<String, byte[]> reopen(byte[] jar, Path directory) throws IOException, InputRejectedException {
        OperationControl.checkpoint();
        Path scratch = directory == null ? Files.createTempFile("legacybridge-cohort-", ".jar") : Files.createTempFile(directory, "legacybridge-cohort-", ".jar");
        try {
            try (var stream = Files.newOutputStream(scratch, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int offset = 0; offset < jar.length; offset += 65536) {
                    OperationControl.checkpoint(); stream.write(jar, offset, Math.min(65536, jar.length - offset));
                }
            }
            var capture = new Capture(); var scan = new SafeJarReader().scan(scratch, capture);
            require(scan.input().sha256().equals(SimpleContentConverter.hash(jar)), "TARGET_ARCHIVE_DRIFT", "Private candidate changed before reopening");
            return capture.entries;
        } finally { Files.deleteIfExists(scratch); } // cleanup failure blocks the whole pair; never suppressed
    }

    private static String fingerprint(byte[] bytes) { return bytes == null ? "<absent>" : "sha256:" + SimpleContentConverter.hash(bytes) + " bytes=" + bytes.length; }
    private static void require(boolean condition, String code, String message) { if (!condition) throw new Rejected(code, message); }
    private static final class Rejected extends RuntimeException {
        final String code;
        Rejected(String code, String message) { super(message); this.code = code; }
    }
    private static final class Capture implements JarEntryVisitor {
        final Map<String, byte[]> entries = new TreeMap<>(); int count;
        @Override public void visitEntry(String path, boolean directory) { require(++count <= 32, "FINITE_ENTRY_LIMIT", "At most 32 entries"); }
        @Override public boolean requiresBytes(String path) { return true; }
        @Override public long maxCapturedBytes(String path) { return 64 * 1024; }
        @Override public void visit(String path, byte[] bytes) { entries.put(path, bytes); }
    }
}
