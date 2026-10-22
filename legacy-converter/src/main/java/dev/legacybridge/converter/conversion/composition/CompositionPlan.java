package dev.legacybridge.converter.conversion.composition;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.TransformationException;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.util.*;

/** Immutable structural operation graph, NOT semantic admission or publication authority. */
public record CompositionPlan(List<Operation> operations) {
    public enum Kind { PRESERVE_OWNED_BODIES, PRESERVE_RECIPE_RESOURCES, REPLACE_REGISTRATION_GRAPH }
    public record Claim(String path, String sourceSha256) {
        public Claim {
            Objects.requireNonNull(path); Objects.requireNonNull(sourceSha256);
            if (path.length() > 512 || path.isBlank() || path.startsWith("/") || path.contains("\\")
                    || Arrays.stream(path.split("/", -1)).anyMatch(s -> s.isEmpty() || s.equals(".") || s.equals(".."))
                    || path.codePoints().anyMatch(c -> Character.isISOControl(c) || c == ':')
                    || !sourceSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid archive claim");
        }
    }
    /** Definition is selected by trusted engine code, never supplied by a mod/service provider. */
    public record Operation(String id, Kind kind, Set<String> requires, List<Claim> claims) {
        public Operation {
            Objects.requireNonNull(kind);
            if (id == null || !id.matches("[a-z][a-z0-9.-]{0,95}")) throw new IllegalArgumentException("Invalid operation ID");
            if (requires.size() > 8 || claims.size() > 4096) throw new IllegalArgumentException("Operation bound exceeded");
            requires = Collections.unmodifiableSet(new TreeSet<>(requires));
            claims = claims.stream().sorted(Comparator.comparing(Claim::path)).toList();
            if (claims.isEmpty()) throw new IllegalArgumentException("Empty operation is not implemented work");
            for (Claim claim : claims) {
                boolean code = claim.path().endsWith(".class");
                if ((kind == Kind.PRESERVE_RECIPE_RESOURCES) == code)
                    throw new IllegalArgumentException("Operation does not own this file kind");
                if (!code && !claim.path().startsWith("assets/") && !claim.path().startsWith("data/"))
                    throw new IllegalArgumentException("Recipe policy does not own metadata");
            }
        }
        public String implementation() {
            return switch (kind) {
                case PRESERVE_OWNED_BODIES -> "LogicPreservingConverter/LogicBodyPreservation/OfflineLinkageVerifier";
                case PRESERVE_RECIPE_RESOURCES -> "ContentResourceValidator.validateDatapackRecipes";
                case REPLACE_REGISTRATION_GRAPH -> "TransformationPipeline/StaticContentExtractor";
            };
        }
        public Map<String, Object> toTree() {
            return Map.ofEntries(Map.entry("id", id), Map.entry("kind", kind.name()), Map.entry("revision", 1),
                    Map.entry("requires", List.copyOf(requires)), Map.entry("implementation", implementation()),
                    Map.entry("claims", claims.stream().map(c -> Map.of("path", c.path(), "sourceSha256", c.sourceSha256())).toList()),
                    Map.entry("phase", kind == Kind.PRESERVE_RECIPE_RESOURCES ? "RESOURCE_VALIDATION" : "OWNED_CLASS_TRANSFORMATION"),
                    Map.entry("executionThread", "BOUNDED_ENGINE_WORKER_NO_MOD_EXECUTION"),
                    Map.entry("runtimeContext", kind == Kind.PRESERVE_RECIPE_RESOURCES
                            ? "Native datapack loading/reload; registry IDs from proven body envelope; no thread relocation"
                            : "Original static initialization/supplier/constructor order; native MOD event bus; BOTH physical sides; original virtual dispatch caller"),
                    Map.entry("preconditions", kind == Kind.PRESERVE_RECIPE_RESOURCES
                            ? List.of("Proven item registry inventory", "Closed versioned JSON/resource policy", "No byte changes")
                            : List.of("Mod-owned classes only", "Exact reviewed mapping recipes", "Source and target offline linkage", "Finite envelope and body proof")));
        }
    }

    public CompositionPlan {
        if (operations.isEmpty() || operations.size() > 8) throw new IllegalArgumentException("Composition admits 1..8 operations");
        Map<String, Operation> remaining = new TreeMap<>(); Set<String> paths = new HashSet<>();
        int claims = 0; boolean bodies = false, replacement = false;
        for (Operation operation : operations) {
            OperationControl.checkpoint();
            if (remaining.putIfAbsent(operation.id(), operation) != null) reject("DUPLICATE_OPERATION");
            bodies |= operation.kind() == Kind.PRESERVE_OWNED_BODIES;
            replacement |= operation.kind() == Kind.REPLACE_REGISTRATION_GRAPH;
            for (Claim claim : operation.claims()) {
                if (++claims > 4096) reject("CLAIM_LIMIT");
                if (!paths.add(claim.path())) reject("OWNERSHIP_CONFLICT: " + claim.path());
                if (claim.path().endsWith(".class") && List.of("net/minecraft/", "net/minecraftforge/", "com/mojang/",
                        "cpw/mods/", "java/", "javax/", "jdk/", "sun/", "dev/legacybridge/runtime/").stream().anyMatch(claim.path()::startsWith))
                    reject("PLATFORM_OWNERSHIP: " + claim.path());
            }
        }
        if (bodies && replacement) reject("SEMANTIC_CONFLICT: preserved behavior cannot compose with bootstrap replacement");
        for (Operation operation : operations) for (String required : operation.requires())
            if (!remaining.containsKey(required)) reject("MISSING_OPERATION: " + required);
        List<Operation> ordered = new ArrayList<>(); Set<String> resolved = new HashSet<>();
        while (!remaining.isEmpty()) {
            OperationControl.checkpoint();
            Operation next = remaining.values().stream().filter(op -> resolved.containsAll(op.requires())).findFirst().orElse(null);
            if (next == null) reject("OPERATION_CYCLE");
            ordered.add(next); resolved.add(next.id()); remaining.remove(next.id());
        }
        operations = List.copyOf(ordered);
    }
    public Map<String, Object> toTree() {
        return Map.of("schemaVersion", 1, "kind", "STRUCTURAL_GRAPH_NOT_AUTHORITY",
                "runtimeAbi", AdapterCatalog.RUNTIME_VERSION, "operations", operations.stream().map(Operation::toTree).toList(),
                "publicationAllowed", false);
    }
    private static void reject(String reason) { throw new TransformationException("COMPOSITION_" + reason); }
}
