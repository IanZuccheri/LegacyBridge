package dev.legacybridge.converter.conversion.dependency;

import dev.legacybridge.converter.archive.InputRejectedException;
import dev.legacybridge.converter.archive.JarEntryVisitor;
import dev.legacybridge.converter.archive.SafeJarReader;
import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.ApiIndex.ApiClass;
import dev.legacybridge.converter.mapping.source.ApiIndex.ApiMember;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStage;
import dev.legacybridge.converter.policy.NumericVersionRange;
import dev.legacybridge.converter.service.CohortInspectionRequest;
import dev.legacybridge.converter.service.EngineError;
import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import dev.legacybridge.converter.transformation.TransformationException;
import dev.legacybridge.converter.transformation.linkage.OfflineLinkageVerifier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.Manifest;

/**
 * DEPS-COHORT source gate. Each JAR owns only its own bodies; other JARs contribute
 * declarations, never executable classes. No remapping, target equivalence or publishing.
 * Legacy API: ordinary javafml 36 mod files and direct public library references.
 * Target API: no converted dependency contract yet; only duplicate-package preflight.
 * Transformation: NONE. Behavior differences: NONE asserted; source linkage is not behavior.
 * Known limitations: JDK17 declaration view, @Mod only from Forge, BOTH-side complete set,
 * no native ports/JiJ/services/reflection or optional/side-conditional linkage admission.
 */
public final class CohortSourceInspector {
    private static final String MOD = "net/minecraftforge/fml/common/Mod";
    private static final int MAX_CLASSES = 64, MAX_REFERENCES = 8192;
    private static final long MAX_CLASSES_BYTES = 4L * 1024 * 1024;
    private static final List<String> LIMITATIONS = List.of(
            "SOURCE_LINKAGE_ONLY: not a conversion profile or semantic equivalence proof.",
            "All selected artifacts are explicitly present on BOTH physical sides; no native ports or inferred installed set.",
            "Only JDK17 declarations and the pinned Forge @Mod declaration are external platform roots in this first slice; no general Minecraft/Forge API admission.",
            "Every cross-artifact reference needs a direct mandatory BOTH dependency. Lazy optional/side-conditional linkage is not implemented.",
            "Resources are inventoried, not schema/behavior validated. Target contracts, conversion, classloader wiring, discovery and behavior remain NOT_RUN.",
            "Original snapshots are temporary and removed by SafeJarReader. No result JAR, staging or installation is created.",
            "Bounds are payload/work limits, not a hard JVM heap limit or protection against hostile concurrent filesystem replacement.");

    public record Diagnostic(String artifactId, EngineError error) {
        public Map<String, Object> toTree() { return Map.of("artifactId", artifactId, "code", error.code(), "message", error.message(), "remediation", error.remediation()); }
    }
    public record OwnedClass(String name, String sha256, ApiClass sourceApi) {
        public Map<String, Object> toTree() {
            return Map.of("name", name, "sha256", sha256, "access", sourceApi.access(),
                    "superclass", Objects.toString(sourceApi.superName(), ""), "interfaces", sourceApi.interfaces(),
                    "methods", sourceApi.methods().stream().map(CohortSourceInspector::memberTree).toList(),
                    "fields", sourceApi.fields().stream().map(CohortSourceInspector::memberTree).toList());
        }
    }
    public record ArtifactInventory(String id, String sha256, long bytes, DependencyGraphVerifier.Mod mod,
                                    List<OwnedClass> classes, List<String> packages, List<String> resources) {
        public ArtifactInventory { classes = List.copyOf(classes); packages = List.copyOf(packages); resources = resources.stream().sorted().toList(); }
        public Map<String, Object> toTree() {
            return Map.of("artifactId", id, "sha256", sha256, "bytes", bytes, "mod", mod.toTree(),
                    "classes", classes.stream().map(OwnedClass::toTree).toList(), "packages", packages, "resources", resources,
                    "dependencyTreatment", "SEPARATE_CONVERSION_REQUIRED", "targetApiStatus", "NOT_PLANNED");
        }
    }
    public record ReferenceBinding(String consumerArtifact, String providerArtifact,
                                   LegacyReferenceScanner.ExternalReference reference) {
        public Map<String, Object> toTree() {
            return Map.of("consumerArtifact", consumerArtifact, "providerArtifact", providerArtifact,
                    "sourceClass", reference.sourceClass(), "kind", reference.kind().name(), "owner", reference.owner(),
                    "name", reference.name(), "descriptor", reference.descriptor(), "enclosingMethod", reference.enclosingMethod(),
                    "instructionIndex", reference.instructionIndex(), "location", reference.location());
        }
    }
    public record Result(boolean sourceLinkagePassed, List<ArtifactInventory> artifacts,
                         List<ReferenceBinding> crossArtifactReferences, Optional<DependencyGraphVerifier.Result> graph,
                         Map<String, OfflineLinkageVerifier.Summary> linkage, List<Diagnostic> diagnostics,
                         List<dev.legacybridge.converter.mapping.source.MappingSource> dataSources,
                         List<CohortInspectionRequest.Artifact> selections, MappingInputs.Scope dataScope, boolean completeData) {
        public Result {
            artifacts = List.copyOf(artifacts); crossArtifactReferences = List.copyOf(crossArtifactReferences);
            linkage = Collections.unmodifiableMap(new TreeMap<>(linkage)); diagnostics = List.copyOf(diagnostics); dataSources = List.copyOf(dataSources); selections = List.copyOf(selections);
        }
        public Map<String, Object> toTree() {
            var tree = new TreeMap<String, Object>();
            tree.put("schemaVersion", 1); tree.put("reportType", "LEGACY_COHORT_SOURCE_INSPECTION");
            tree.put("status", sourceLinkagePassed ? "SOURCE_LINKAGE_ONLY" : "BLOCKED");
            tree.put("selectedArtifacts", selections.stream().map(a -> Map.of("artifactId", a.id(), "expectedSha256", a.expectedSha256())).toList());
            tree.put("artifacts", artifacts.stream().map(ArtifactInventory::toTree).toList());
            tree.put("crossArtifactReferences", crossArtifactReferences.stream().map(ReferenceBinding::toTree).toList());
            tree.put("sourceDependencyGraph", graph.map(DependencyGraphVerifier.Result::toTree).orElse(Map.of()));
            var checks = new TreeMap<String, Object>(); linkage.forEach((id, summary) -> checks.put(id, summary.toTree()));
            tree.put("sourceOfflineLinkage", checks); tree.put("diagnostics", diagnostics.stream().map(Diagnostic::toTree).toList());
            tree.put("mappingDataSources", dataSources.stream().map(s -> Map.of("id", s.id(), "sha256", s.sha256(), "origin", s.origin())).toList());
            tree.put("mappingData", Map.of("scope", dataScope.name(), "completeMappingEnvironment", completeData,
                    "nameMappingTablesUsed", false, "platformRoot", MOD, "jdkDeclarations", "RUNNING_JAVA17_PLATFORM_NOT_EMBEDDED"));
            tree.put("conversionAuthorized", false); tree.put("publicationAllowed", false); tree.put("classesExecuted", false);
            tree.put("targetLinkage", "NOT_RUN"); tree.put("runtimeValidation", "NOT_RUN"); tree.put("limitations", LIMITATIONS);
            return Collections.unmodifiableMap(tree);
        }
    }

    public Result inspect(CohortInspectionRequest request, MappingInputs inputs) {
        OperationControl.checkpoint();
        var inventories = new ArrayList<ArtifactInventory>();
        var references = new ArrayList<ReferenceBinding>();
        var diagnostics = new ArrayList<Diagnostic>();
        var checks = new TreeMap<String, OfflineLinkageVerifier.Summary>();
        DependencyGraphVerifier.Result graph = null;
        String active = "";
        try {
            boolean finite = inputs.scope() == MappingInputs.Scope.FINITE_COHORT_CONTRACT;
            require(inputs.complete() && inputs.scope() == MappingInputs.Scope.FULL_ENVIRONMENT || finite && !inputs.complete(),
                    "MAPPING_SCOPE", "Complete explicit data or the exact finite cohort contract required");
            if (finite) require(inputs.legacyApi().classes().size() == 1 && inputs.targetApi().classes().size() == 1,
                    "MAPPING_SCOPE", "Finite cohort data must not impersonate a general API inventory");
            ApiClass modApi = inputs.legacyApi().getClass(MOD);
            require(modApi != null && modApi.equals(inputs.targetApi().getClass(MOD)), "FORGE_DECLARATION_MISMATCH", "Pinned @Mod declarations must match; this is not a behavioral mapping");
            var platform = new ApiIndex(Map.of(MOD, modApi));
            var classOwners = new TreeMap<String, String>();
            var packageOwners = new TreeMap<String, String>();
            var allApi = new TreeMap<String, ApiClass>();
            var scans = new TreeMap<String, LegacyReferenceScanner.ScanResult>();
            var classes = new TreeMap<String, Map<String, byte[]>>();
            var paths = new ArrayList<Path>();
            long totalClassBytes = 0, inflated = 0;
            int totalClasses = 0, totalReferences = 0, totalMembers = 0;
            for (var selected : request.artifacts()) {
                active = selected.id(); OperationControl.checkpoint(); safePath(selected.input());
                for (Path prior : paths) require(!Files.isSameFile(prior, selected.input()), "ARTIFACT_ALIAS", "Two selections identify the same file");
                paths.add(selected.input());
                require(Files.size(selected.input()) <= 16L * 1024 * 1024, "COHORT_INPUT_LIMIT", "Each source JAR must be at most 16 MiB");
                Capture capture = new Capture();
                var archive = new SafeJarReader().scan(selected.input(), capture);
                require(archive.input().sha256().equals(selected.expectedSha256()), "INPUT_CHANGED", "Selected SHA-256 differs from inspected bytes");
                // Complete inventories previously protected against ownership collisions beyond reserved namespaces.
                // With finite data, only actual reviewed archive bytes may proceed to any class/API inspection.
                if (finite) require(FiniteCohortContract.reviewedHash(archive.input().sha256()), "FINITE_COHORT_DATA_SCOPE",
                        "Included data supports only the reviewed source archives. Select explicit full data for broader source inspection; target recipes remain restricted.");
                require(!archive.archive().signed() && !archive.archive().multiRelease(), "ARCHIVE_CONTRACT", "Signed or multi-release input is unsupported");
                require(archive.archive().specialEntryCount() == 0, "ARCHIVE_CONTRACT", "Special loading/native/archive entries unsupported");
                inflated += archive.archive().totalUncompressedBytes();
                require(inflated <= 64L * 1024 * 1024, "COHORT_INPUT_LIMIT", "Cohort inflated bytes exceed 64 MiB");
                Manifest manifest = manifest(capture.metadata.get("META-INF/MANIFEST.MF"));
                var parsed = new DependencyGraphVerifier().parseMetadata(active, capture.metadata.get("META-INF/mods.toml"),
                        Optional.ofNullable(manifest.getMainAttributes().getValue("Implementation-Version")));
                require(parsed.valid() && parsed.mods().size() == 1, "COHORT_METADATA", "Exactly one valid mod declaration per JAR: " + parsed.diagnostics());
                require(parsed.modLoader().equals("javafml") && NumericVersionRange.contains(parsed.loaderVersion(), "36"), "SOURCE_LOADER", "Expected javafml 36 metadata");
                var mod = parsed.mods().get(0);
                require(mod.dependencies().stream().anyMatch(d -> d.modId().equals("minecraft") && d.mandatory() && d.side() == DependencyGraphVerifier.DependencySide.BOTH),
                        "SOURCE_VERSION_REQUIRED", "Mandatory BOTH Minecraft dependency is required");
                totalClasses += capture.classes.size(); totalClassBytes += capture.classBytes;
                require(totalClasses <= MAX_CLASSES && totalClassBytes <= MAX_CLASSES_BYTES, "COHORT_CLASS_LIMIT", "At most 64 classes / 4 MiB bytecode per cohort");
                var scan = new LegacyReferenceScanner().scan(capture.classes);
                totalReferences += scan.references().size();
                require(totalReferences <= MAX_REFERENCES, "COHORT_REFERENCE_LIMIT", "At most 8192 external reference occurrences");
                var owned = new ArrayList<OwnedClass>(); var packages = new TreeSet<String>();
                for (var node : scan.classNodes().values()) {
                    OperationControl.checkpoint();
                    totalMembers += node.methods.size() + node.fields.size();
                    require(totalMembers <= 8192, "COHORT_MEMBER_LIMIT", "At most 8192 owned declarations");
                    require(!inputs.legacyApi().hasClass(node.name) && !inputs.targetApi().hasClass(node.name), "PLATFORM_OWNERSHIP", node.name);
                    require(classOwners.putIfAbsent(node.name, active) == null, "DUPLICATE_CLASS", node.name);
                    String pkg = node.name.substring(0, Math.max(0, node.name.lastIndexOf('/')));
                    require(!pkg.isEmpty(), "DEFAULT_PACKAGE", node.name);
                    String previous = packageOwners.putIfAbsent(pkg, active);
                    require(previous == null || previous.equals(active), "SPLIT_PACKAGE", pkg);
                    packages.add(pkg);
                    var api = new ApiClass(node.name, node.access, node.superName, node.interfaces,
                            node.methods.stream().map(m -> new ApiMember(m.name, m.desc, m.access)).toList(),
                            node.fields.stream().map(f -> new ApiMember(f.name, f.desc, f.access)).toList());
                    allApi.put(node.name, api);
                    owned.add(new OwnedClass(node.name, hash(capture.classes.get(node.name + ".class")), api));
                }
                inventories.add(new ArtifactInventory(active, archive.input().sha256(), archive.input().sizeBytes(), mod,
                        owned, List.copyOf(packages), capture.resources));
                classes.put(active, capture.classes); scans.put(active, scan);
            }
            active = "";
            OperationControl.stage(OperationStage.SEMANTIC_VALIDATION, "Checking complete source cohort on both physical sides");
            var mods = inventories.stream().map(ArtifactInventory::mod).toList();
            graph = new DependencyGraphVerifier().verify(Arrays.stream(DependencyGraphVerifier.Side.values())
                    .map(side -> new DependencyGraphVerifier.Installation(side, List.of(), mods, DependencyGraphVerifier.Platform.source())).toList());
            require(graph.passed(), "COHORT_DEPENDENCY_GRAPH", "Source closure/version/order rejected; inspect complete dependency graph");
            var byId = new TreeMap<String, ArtifactInventory>(); inventories.forEach(a -> byId.put(a.id(), a));
            for (var artifact : inventories) {
                active = artifact.id();
                for (var ref : scans.get(active).references()) {
                    OperationControl.checkpoint();
                    String provider = classOwners.get(ref.owner());
                    if (provider == null) {
                        require(jdk(ref.owner()) || ref.owner().equals(MOD), "UNKNOWN_EXTERNAL_CONTRACT", ref.owner());
                    } else {
                        references.add(new ReferenceBinding(active, provider, ref));
                        String providerMod = byId.get(provider).mod().modId();
                        require(artifact.mod().dependencies().stream().anyMatch(d -> d.modId().equals(providerMod)
                                        && d.mandatory() && d.side() == DependencyGraphVerifier.DependencySide.BOTH),
                                "CROSS_ARTIFACT_DEPENDENCY_REQUIRED", active + " -> " + provider + ": direct mandatory BOTH dependency required");
                    }
                }
            }
            OperationControl.stage(OperationStage.BYTECODE_VALIDATION, "Checking each owner against declaration-only dependencies");
            for (var artifact : inventories) {
                active = artifact.id(); OperationControl.checkpoint();
                var external = new TreeMap<>(platform.classes());
                // Critically NOT a union of owned bodies: access/constructor/override checks retain the JAR boundary.
                Set<String> permittedProviders = new HashSet<>();
                for (var dependency : artifact.mod().dependencies())
                    if (dependency.mandatory() && dependency.side() == DependencyGraphVerifier.DependencySide.BOTH)
                        for (var provider : inventories) if (provider.mod().modId().equals(dependency.modId())) permittedProviders.add(provider.id());
                // Also closes unused constant-pool references checked by OfflineLinkageVerifier:
                // merely selecting an unrelated provider must not grant its declarations.
                allApi.forEach((name, api) -> { if (!classOwners.get(name).equals(artifact.id()) && permittedProviders.contains(classOwners.get(name))) external.put(name, api); });
                checks.put(active, new OfflineLinkageVerifier().verify(classes.get(active), new ApiIndex(external)));
            }
        } catch (InputRejectedException error) { diagnostics.add(diagnostic(active, error.reasonCode(), error.getMessage())); }
        catch (TransformationException error) { diagnostics.add(diagnostic(active, "SOURCE_LINKAGE_REJECTED", error.getMessage())); }
        catch (Rejection error) { diagnostics.add(diagnostic(active, error.code, error.getMessage())); }
        catch (IOException error) { diagnostics.add(diagnostic(active, "COHORT_IO", error.getMessage())); }
        OperationControl.checkpoint();
        return new Result(diagnostics.isEmpty(), inventories, references, Optional.ofNullable(graph), checks, diagnostics, inputs.sources(), request.artifacts(), inputs.scope(), inputs.complete());
    }

    private static Map<String, Object> memberTree(ApiMember member) { return Map.of("name", member.name(), "descriptor", member.descriptor(), "access", member.access()); }
    private static Diagnostic diagnostic(String artifact, String code, String message) {
        return new Diagnostic(artifact, new EngineError(code, Objects.toString(message, "Input rejected"),
                "Inspect the exact selected artifacts and declarations; no dependency is downloaded, removed or converted."));
    }
    private static boolean jdk(String name) { return name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("jdk/") || name.startsWith("sun/"); }
    private static void require(boolean condition, String code, String message) { if (!condition) throw new Rejection(code, message); }
    private static final class Rejection extends RuntimeException {
        final String code;
        Rejection(String code, String message) { super(message); this.code = code; }
    }
    private static void safePath(Path input) throws IOException {
        for (Path current = input; current != null; current = current.getParent()) {
            OperationControl.checkpoint();
            if (Files.isSymbolicLink(current) || !current.equals(current.toRealPath()))
                throw new Rejection("UNSAFE_PATH", "Symlink, junction or redirected alias refused");
        }
        require(Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS), "UNSAFE_PATH", "Expected regular file");
    }
    private static Manifest manifest(byte[] bytes) throws IOException {
        var manifest = bytes == null ? new Manifest() : new Manifest(new ByteArrayInputStream(bytes));
        require(manifest.getEntries().isEmpty(), "MANIFEST_CONTRACT", "Per-entry manifest attributes unsupported");
        Set<String> allowed = Set.of("manifest-version", "implementation-title", "implementation-version", "implementation-vendor",
                "specification-title", "specification-version", "specification-vendor", "legacybridge-fixture");
        for (Object key : manifest.getMainAttributes().keySet())
            require(allowed.contains(key.toString().toLowerCase(Locale.ROOT)), "MANIFEST_CONTRACT", "Unsupported manifest attribute: " + key);
        return manifest;
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static final class Capture implements JarEntryVisitor {
        final Map<String, byte[]> classes = new TreeMap<>(), metadata = new TreeMap<>();
        final List<String> resources = new ArrayList<>();
        long classBytes; int entries;
        @Override public void visitEntry(String path, boolean directory) {
            require(++entries <= 512, "COHORT_ENTRY_LIMIT", "At most 512 entries per artifact");
            if (directory) return;
            String lower = path.toLowerCase(Locale.ROOT);
            require(!lower.endsWith(".jar") && !lower.endsWith(".zip") && !lower.endsWith(".js")
                            && !lower.startsWith("meta-inf/services/") && !lower.startsWith("meta-inf/jarjar/")
                            && !lower.startsWith("meta-inf/versions/") && !lower.contains("mixin") && !lower.contains("coremod")
                            && !lower.endsWith("accesstransformer.cfg") && !lower.endsWith("_at.cfg") && !lower.equals("module-info.class"),
                    "ARCHIVE_CONTRACT", path);
            if (!path.endsWith(".class")) resources.add(path);
        }
        @Override public boolean requiresBytes(String path) { return path.endsWith(".class") || path.equals("META-INF/mods.toml") || path.equals("META-INF/MANIFEST.MF"); }
        @Override public long maxCapturedBytes(String path) { return path.endsWith(".class") ? 256 * 1024 : 64 * 1024; }
        @Override public void visit(String path, byte[] bytes) {
            if (path.endsWith(".class")) {
                classBytes += bytes.length;
                require(classes.size() < 32 && classBytes <= MAX_CLASSES_BYTES, "COHORT_CLASS_LIMIT", "At most 32 classes per artifact / 4 MiB capture");
                classes.put(path, bytes);
            } else metadata.put(path, bytes);
        }
    }
}
