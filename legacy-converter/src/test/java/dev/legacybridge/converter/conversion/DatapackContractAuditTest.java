package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.conversion.extract.SubsetRejectionException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent regressions for resource-audit findings. No mappings, mod classes or game execution. */
class DatapackContractAuditTest {
    private static final String TAG_PREFIX = "data/auditmod/tags/items/";
    private static final ContentPlan PLAN = new ContentPlan("auditmod", "1.0.0", List.of(
            new ContentPlan.ItemDefinition("ingot", null,
                    new ContentPlan.ItemProperties(32, 0, false, "COMMON", "MISC"))), List.of(), List.of());

    @Test void childFirstMemoizationCannotHideATagChainLongerThanTheDepthLimit() {
        // The historical done-set visited each child first, and returned before
        // testing the accumulated depth when a later parent reached that child.
        Map<String, byte[]> entries = childFirstTagChain(128);
        Map<String, byte[]> before = snapshot(entries);
        var refused = assertThrows(SubsetRejectionException.class,
                () -> ContentResourceValidator.validateDatapackRecipes(entries, PLAN));
        assertTrue(refused.getMessage().contains("depth limit"), refused.getMessage());
        assertUnchanged(before, entries);
    }

    @Test void theDocumentedSixtyFourEdgeBoundaryStillAdmitsAClosedNonemptyChain() throws Exception {
        Map<String, byte[]> entries = childFirstTagChain(65);
        Map<String, byte[]> before = snapshot(entries);
        var decisions = ContentResourceValidator.validateDatapackRecipes(entries, PLAN);
        assertEquals(1, decisions.size());
        assertEquals("PRESERVE_BYTES", decisions.get(0).get("disposition"));
        assertUnchanged(before, entries);
    }

    @Test void oneEdgeBeyondTheBoundaryFailsRegardlessOfChildFirstVisitation() {
        Map<String, byte[]> entries = childFirstTagChain(66);
        var refused = assertThrows(SubsetRejectionException.class,
                () -> ContentResourceValidator.validateDatapackRecipes(entries, PLAN));
        assertTrue(refused.getMessage().contains("depth limit"), refused.getMessage());
    }

    @Test void malformedUtf8PackDescriptionMustNotBeSilentlyReplacedAndPublished() {
        var bytes = new ByteArrayOutputStream();
        bytes.writeBytes("{\"pack\":{\"pack_format\":6,\"description\":\"".getBytes(StandardCharsets.UTF_8));
        bytes.write(0xc3); // Missing UTF-8 continuation: replacement decoding used to accept this description.
        bytes.write(0x28);
        bytes.writeBytes("\"}}".getBytes(StandardCharsets.UTF_8));
        byte[] malformed = bytes.toByteArray();
        byte[] before = malformed.clone();
        var refused = assertThrows(SubsetRejectionException.class,
                () -> SimpleContentConverter.targetPack(malformed));
        assertTrue(refused.getMessage().contains("UTF-8"), refused.getMessage());
        assertArrayEquals(before, malformed);
    }

    @Test void otherwiseValidPackMetadataCannotExceedTheOneMebibyteBound() {
        byte[] oversized = new byte[1_048_577];
        Arrays.fill(oversized, (byte) ' ');
        byte[] valid = "{\"pack\":{\"pack_format\":6,\"description\":\"bounded\"}}".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(valid, 0, oversized, 0, valid.length);
        byte[] before = oversized.clone();
        assertThrows(SubsetRejectionException.class, () -> SimpleContentConverter.targetPack(oversized));
        assertArrayEquals(before, oversized);
    }

    @Test void strictUtf8DoesNotRejectOrReplaceAnOrdinaryUnicodeDescription() throws Exception {
        String description = "Receitas de fundição — 雪";
        byte[] input = ("{\"pack\":{\"pack_format\":6,\"description\":\"" + description + "\"}}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] before = input.clone();
        Map<?, ?> rewritten = (Map<?, ?>) BoundedJsonParser.parse(
                new String(SimpleContentConverter.targetPack(input), StandardCharsets.UTF_8));
        Map<?, ?> pack = (Map<?, ?>) rewritten.get("pack");
        assertEquals(description, pack.get("description"));
        assertEquals(15, ((Number) pack.get("pack_format")).intValue());
        assertArrayEquals(before, input);
    }

    private static Map<String, byte[]> childFirstTagChain(int nodeCount) {
        Map<String, byte[]> entries = new TreeMap<>();
        put(entries, "assets/auditmod/models/item/ingot.json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/iron_ingot\"}}");
        List<String> sortedPaths = new ArrayList<>();
        for (int index = 0; index < nodeCount; index++)
            sortedPaths.add(TAG_PREFIX + String.format(Locale.ROOT, "chain_%03d", index) + ".json");
        sortedPaths.sort(String::compareTo);

        // Reproduce the actual default HashMap insertion/iteration of tagEdges:
        // validator visits source entries lexically, then visits graph.keySet().
        // Values do not affect key hashing or iteration order. This is a small,
        // deterministic adversarial graph, not an intentional stack overflow.
        Map<String, Boolean> iteration = new HashMap<>();
        sortedPaths.forEach(path -> iteration.put(path, Boolean.TRUE));
        List<String> childFirst = List.copyOf(iteration.keySet());
        for (int index = 0; index < childFirst.size(); index++) {
            String value = index == 0 ? "minecraft:iron_ingot" : "#" + tagId(childFirst.get(index - 1));
            put(entries, childFirst.get(index), "{\"replace\":false,\"values\":[\"" + value + "\"]}");
        }
        String root = tagId(childFirst.get(childFirst.size() - 1));
        put(entries, "data/auditmod/recipes/smelting.json", "{\"type\":\"minecraft:smelting\","
                + "\"ingredient\":{\"tag\":\"" + root + "\"},\"result\":\"auditmod:ingot\"}");
        return entries;
    }

    private static String tagId(String path) {
        return "auditmod:" + path.substring(TAG_PREFIX.length(), path.length() - ".json".length());
    }

    private static void put(Map<String, byte[]> entries, String path, String json) {
        entries.put(path, json.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, byte[]> snapshot(Map<String, byte[]> entries) {
        Map<String, byte[]> snapshot = new TreeMap<>();
        entries.forEach((path, bytes) -> snapshot.put(path, bytes.clone()));
        return snapshot;
    }

    private static void assertUnchanged(Map<String, byte[]> before, Map<String, byte[]> after) {
        assertEquals(before.keySet(), after.keySet());
        before.forEach((path, bytes) -> assertArrayEquals(bytes, after.get(path), path));
    }
}
