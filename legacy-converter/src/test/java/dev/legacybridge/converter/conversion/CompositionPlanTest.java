package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.composition.CompositionPlan;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static dev.legacybridge.converter.conversion.composition.CompositionPlan.*;
import static org.junit.jupiter.api.Assertions.*;

class CompositionPlanTest {
    @Test void deterministicGraphIsDeeplyImmutableAndDoesNotAuthorizeExecution() {
        var claims = new ArrayList<>(List.of(claim("owned/Z.class"), claim("owned/A.class")));
        var dependencies = new HashSet<>(Set.of("bodies"));
        var body = new Operation("bodies", Kind.PRESERVE_OWNED_BODIES, Set.of(), claims);
        var data = new Operation("data", Kind.PRESERVE_RECIPE_RESOURCES, dependencies, List.of(claim("data/example/recipes/a.json")));
        var list = new ArrayList<>(List.of(data, body));
        var plan = new CompositionPlan(list); claims.clear(); dependencies.clear(); list.clear();
        assertEquals(List.of("bodies", "data"), plan.operations().stream().map(Operation::id).toList());
        assertEquals(List.of("owned/A.class", "owned/Z.class"), plan.operations().get(0).claims().stream().map(Claim::path).toList());
        assertEquals(plan, new CompositionPlan(List.of(body, data)));
        assertThrows(UnsupportedOperationException.class, () -> plan.operations().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.operations().get(0).claims().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.operations().get(1).requires().clear());
        assertEquals(false, plan.toTree().get("publicationAllowed"));
        assertEquals("STRUCTURAL_GRAPH_NOT_AUTHORITY", plan.toTree().get("kind"));
    }
    @ParameterizedTest @ValueSource(strings = {"DUPLICATE", "OVERLAP", "MISSING", "CYCLE", "BOOTSTRAP", "SELF"})
    void rejectsConflictingOrIncompleteOperationsBeforeExecution(String defect) {
        Operation body = op("body", Kind.PRESERVE_OWNED_BODIES, Set.of(), "owned/A.class");
        Operation other = switch (defect) {
            case "DUPLICATE" -> op("body", Kind.PRESERVE_OWNED_BODIES, Set.of(), "owned/B.class");
            case "OVERLAP" -> op("other", Kind.PRESERVE_OWNED_BODIES, Set.of(), "owned/A.class");
            case "MISSING" -> op("other", Kind.PRESERVE_OWNED_BODIES, Set.of("missing"), "owned/B.class");
            case "BOOTSTRAP" -> op("other", Kind.REPLACE_REGISTRATION_GRAPH, Set.of(), "owned/B.class");
            case "SELF" -> op("other", Kind.PRESERVE_OWNED_BODIES, Set.of("other"), "owned/B.class");
            default -> op("other", Kind.PRESERVE_OWNED_BODIES, Set.of("body"), "owned/B.class");
        };
        if (defect.equals("CYCLE")) body = op("body", Kind.PRESERVE_OWNED_BODIES, Set.of("other"), "owned/A.class");
        var entries = List.of(body, other);
        assertThrows(TransformationException.class, () -> new CompositionPlan(entries));
    }
    @ParameterizedTest @ValueSource(strings = {"net/minecraft/Item.class", "net/minecraftforge/Mod.class", "java/lang/Object.class",
            "com/mojang/X.class", "dev/legacybridge/runtime/X.class"})
    void rejectsPlatformOwnership(String path) {
        assertThrows(TransformationException.class, () -> new CompositionPlan(List.of(op("body", Kind.PRESERVE_OWNED_BODIES, Set.of(), path))));
    }
    @ParameterizedTest @ValueSource(strings = {"../A.class", "owned/../A.class", "C:/A.class", "owned\\A.class", "/A.class", "owned//A.class"})
    void rejectsUnsafeClaims(String path) { assertThrows(IllegalArgumentException.class, () -> claim(path)); }
    @Test void rejectsEmptyWorkWrongDomainsAndBounds() {
        assertThrows(IllegalArgumentException.class, () -> new CompositionPlan(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Operation("empty", Kind.PRESERVE_OWNED_BODIES, Set.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> op("data", Kind.PRESERVE_RECIPE_RESOURCES, Set.of(), "META-INF/mods.toml"));
        assertThrows(IllegalArgumentException.class, () -> op("body", Kind.PRESERVE_OWNED_BODIES, Set.of(), "data/x.json"));
        assertThrows(IllegalArgumentException.class, () -> new Claim("owned/A.class", "not-a-hash"));
        var operations = java.util.stream.IntStream.range(0, 9).mapToObj(i -> op("body" + i, Kind.PRESERVE_OWNED_BODIES, Set.of(), "owned/C" + i + ".class")).toList();
        assertThrows(IllegalArgumentException.class, () -> new CompositionPlan(operations));
    }
    private static Operation op(String id, Kind kind, Set<String> requires, String path) {
        return new Operation(id, kind, requires, List.of(claim(path)));
    }
    private static Claim claim(String path) { return new Claim(path, "a".repeat(64)); }
}
