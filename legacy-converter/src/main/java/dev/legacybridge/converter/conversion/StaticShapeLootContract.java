package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.conversion.extract.SubsetRejectionException;
import dev.legacybridge.converter.operation.OperationControl;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Legacy API: one block loot pool, one local item, one survives_explosion condition.
 * Target API: the same finite JSON nodes; serialized source bytes are retained, not regenerated.
 * Transformation: prove optional exact self-drop data and record its hash/context boundary.
 * Behavior differences: target TNT explosion-decay policy and random sources differ; the same
 * condition formula does not imply identical explosion outcomes or equivalence of game contexts.
 * Known limitations: only the proved static half-block's own item, one roll, absent bonus_rolls,
 * no functions, references, alternatives, extra conditions, global modifiers or native QA claim.
 */
public final class StaticShapeLootContract {
    private static final int MAX_JSON_BYTES = 1_048_576;

    private StaticShapeLootContract() { }

    /** Absence is preserved as absence; this operation never invents a loot table or drops. */
    public static Optional<Map<String,Object>> inspect(Map<String,byte[]> entries, ContentPlan plan)
            throws SubsetRejectionException {
        OperationControl.checkpoint();
        require(entries != null && entries.size() <= 4096, "Bounded resource inventory required");
        require(plan != null && plan.modId() != null && plan.modId().matches("[a-z][a-z0-9_]{1,63}")
            && plan.blocks().size() == 1 && plan.items().size() == 1 && plan.sounds().isEmpty(),
            "Exactly one proved Block/BlockItem and no registered sounds");
        var block = plan.blocks().get(0); var item = plan.items().get(0);
        require(block != null && block.name() != null && block.name().matches("[a-z0-9_]{1,128}")
            && item != null && block.name().equals(item.name())
            && (plan.modId() + ":" + block.name()).equals(item.block()), "Exact owned BlockItem identity");
        String itemId = plan.modId() + ":" + block.name();
        String path = "data/" + plan.modId() + "/loot_tables/blocks/" + block.name() + ".json";
        for (String candidate : entries.keySet()) {
            OperationControl.checkpoint();
            require(candidate != null, "Null resource path");
            if (lootLike(candidate) && !path.equals(candidate))
                reject("Unsupported loot table/modifier or ambiguous/future path: " + candidate);
        }
        if (!entries.containsKey(path)) return Optional.empty();
        byte[] bytes = entries.get(path);
        require(bytes != null && bytes.length <= MAX_JSON_BYTES, "Loot JSON absent or exceeds 1 MiB");
        Map<?,?> root;
        try {
            var json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            root = object(BoundedJsonParser.parse(json), "root");
        } catch (CharacterCodingException | BoundedJsonParser.JsonSyntaxException failure) {
            throw new SubsetRejectionException("STATIC_SHAPE_LOOT_UNSUPPORTED: Invalid bounded UTF-8 JSON: " + failure.getMessage());
        }
        require(root.keySet().equals(Set.of("type", "pools")) && "minecraft:block".equals(root.get("type")),
            "Only an explicit block table and pools; no functions, sequence or unknown root members");
        var pool = object(single(root.get("pools"), "pools"), "pool");
        require(pool.keySet().equals(Set.of("rolls", "entries", "conditions")),
            "Only rolls, entries and conditions; bonus_rolls must remain omitted");
        require(pool.get("rolls") instanceof BigDecimal rolls && rolls.compareTo(BigDecimal.ONE) == 0,
            "Exactly one numeric roll; no providers, fractions, booleans or strings");
        var entry = object(single(pool.get("entries"), "entries"), "entry");
        require(entry.equals(Map.of("type", "minecraft:item", "name", itemId)),
            "Exactly the local BlockItem, with no count, weight, quality, functions or conditions");
        var condition = object(single(pool.get("conditions"), "conditions"), "condition");
        require(condition.equals(Map.of("condition", "minecraft:survives_explosion")),
            "Exactly the reviewed survives_explosion condition");
        OperationControl.checkpoint();
        return Optional.of(Map.ofEntries(
            Map.entry("schemaVersion", 1), Map.entry("kind", "STATIC_SHAPE_SELF_DROP"),
            Map.entry("path", path), Map.entry("sha256", SimpleContentConverter.hash(bytes)),
            Map.entry("item", itemId), Map.entry("rolls", 1), Map.entry("bonusRolls", "OMITTED_NATIVE_ZERO"),
            Map.entry("probability", Map.of("withoutExplosionRadius", "TRUE",
                "withExplosionRadius", "nextFloat() <= 1.0F / explosionRadius",
                "outcome", "ZERO_OR_ONE_LOCAL_ITEM", "contextRequired", "NATIVE_LOOT_CONTEXT")),
            Map.entry("contextDifferences", List.of(
                "Target TNT explosion-decay game rule defaults to false; source/target radius presence is not assumed identical",
                "Native random sources differ; matching formula does not promise matching random outcomes")),
            Map.entry("resourceBytesModified", false), Map.entry("runtimeValidation", "NOT_RUN"),
            Map.entry("classesExecuted", false)));
    }

    private static boolean lootLike(String path) {
        // Ambiguous separators/case and the newer singular directory are not aliases to accept.
        String[] segments = path.replace('\\', '/').split("/", -1);
        for (int index = 0; index < segments.length; index++) {
            String lower = segments[index].toLowerCase(Locale.ROOT);
            if (lower.equals("loot_tables") || lower.equals("loot_table") || lower.equals("loot_modifiers")
                || lower.equals("global_loot_modifiers.json")
                || index == 2 && (lower.startsWith("loot_table") || lower.startsWith("loot_modifier"))) return true;
        }
        return false;
    }

    private static Map<?,?> object(Object value, String role) throws SubsetRejectionException {
        require(value instanceof Map<?,?>, role + " must be an object");
        return (Map<?,?>)value;
    }

    private static Object single(Object value, String role) throws SubsetRejectionException {
        require(value instanceof List<?> values && values.size() == 1, role + " must contain exactly one element");
        return ((List<?>)value).get(0);
    }

    private static void require(boolean accepted, String message) throws SubsetRejectionException {
        if (!accepted) reject(message);
    }

    private static void reject(String message) throws SubsetRejectionException {
        throw new SubsetRejectionException("STATIC_SHAPE_LOOT_UNSUPPORTED: " + message);
    }
}
