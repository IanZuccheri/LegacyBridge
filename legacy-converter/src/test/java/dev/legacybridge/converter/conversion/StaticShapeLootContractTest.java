package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.conversion.extract.SubsetRejectionException;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationLimits;
import dev.legacybridge.converter.operation.OperationStoppedException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Static JSON/hash checks only; never resolves an item, invokes loot RNG or runs a game fixture. */
class StaticShapeLootContractTest {
    private static final String PATH = "data/example/loot_tables/blocks/bridge_block.json";
    private static final String ENTRY = "{\"type\":\"minecraft:item\",\"name\":\"example:bridge_block\"}";
    private static final String CONDITION = "{\"condition\":\"minecraft:survives_explosion\"}";

    @Test void absenceRemainsAbsentWithoutCreatingDataOrFakeDrops() throws Exception {
        var entries = new LinkedHashMap<String,byte[]>(); entries.put("data/example/recipes/block.json", utf8("{}"));
        var before = entries.get("data/example/recipes/block.json").clone();
        assertTrue(StaticShapeLootContract.inspect(entries, plan()).isEmpty());
        assertEquals(Set.of("data/example/recipes/block.json"), entries.keySet());
        assertArrayEquals(before, entries.values().iterator().next());
        // A normal recipe/model filename is not a loot directory or authority to parse that resource here.
        assertTrue(StaticShapeLootContract.inspect(Map.of("data/example/recipes/loot_table_recipe.json", utf8("{}"),
            "assets/example/models/block/loot_table_statue.json", utf8("{}")), plan()).isEmpty());
    }

    @Test void exactSelfDropHasImmutableHashBoundProofAndExplicitRuntimeBoundary() throws Exception {
        var bytes = utf8(valid("1")); var before = bytes.clone();
        var proof = StaticShapeLootContract.inspect(Map.of(PATH, bytes), plan()).orElseThrow();
        assertEquals(1, proof.get("schemaVersion")); assertEquals("STATIC_SHAPE_SELF_DROP", proof.get("kind"));
        assertEquals(PATH, proof.get("path")); assertEquals(SimpleContentConverter.hash(bytes), proof.get("sha256"));
        assertEquals("example:bridge_block", proof.get("item")); assertEquals(1, proof.get("rolls"));
        assertEquals("OMITTED_NATIVE_ZERO", proof.get("bonusRolls"));
        assertEquals(false, proof.get("resourceBytesModified")); assertEquals(false, proof.get("classesExecuted"));
        assertEquals("NOT_RUN", proof.get("runtimeValidation"));
        var probability = (Map<?,?>)proof.get("probability");
        assertEquals("TRUE", probability.get("withoutExplosionRadius"));
        assertEquals("nextFloat() <= 1.0F / explosionRadius", probability.get("withExplosionRadius"));
        assertEquals("ZERO_OR_ONE_LOCAL_ITEM", probability.get("outcome"));
        assertTrue(((List<?>)proof.get("contextDifferences")).stream().anyMatch(x -> x.toString().contains("TNT")));
        assertTrue(((List<?>)proof.get("contextDifferences")).stream().anyMatch(x -> x.toString().contains("random")));
        assertThrows(UnsupportedOperationException.class, () -> proof.put("runtimeValidation", "PASSED"));
        assertArrayEquals(before, bytes);
    }

    @Test void resourceWhitespaceIsPreservedAndProofHashDistinguishesIt() throws Exception {
        byte[] plain = utf8(valid("1")), spaced = utf8(" \n\t" + valid("1") + "\n");
        var first = StaticShapeLootContract.inspect(Map.of(PATH, plain), plan()).orElseThrow();
        var second = StaticShapeLootContract.inspect(Map.of(PATH, spaced), plan()).orElseThrow();
        assertNotEquals(first.get("sha256"), second.get("sha256"));
        assertEquals(first.get("item"), second.get("item")); assertEquals(first.get("probability"), second.get("probability"));
        assertEquals(" \n\t" + valid("1") + "\n", new String(spaced, StandardCharsets.UTF_8));
        String hash = (String)first.get("sha256"); plain[0] = ' ';
        assertEquals(hash, first.get("sha256"));
    }

    @ParameterizedTest @ValueSource(strings={"1", "1.0", "1e0", "1.000000", "1.0E+0"})
    void acceptsExactlyNumericOneWithoutRewritingItsEncoding(String rolls) throws Exception {
        var bytes = utf8(valid(rolls)); var before = bytes.clone();
        assertEquals(1, StaticShapeLootContract.inspect(Map.of(PATH, bytes), plan()).orElseThrow().get("rolls"));
        assertArrayEquals(before, bytes);
    }

    @ParameterizedTest @ValueSource(strings={"0", "-1", "2", "0.5", "1.00000000000000000001", "1e9", "true", "\"1\"", "null", "[]", "{}", "NaN"})
    void refusesOtherRollCountsOrProviders(String rolls) { refused(valid(rolls)); }

    @ParameterizedTest @ValueSource(strings={
        "data/other/loot_tables/blocks/bridge_block.json", "data/minecraft/loot_tables/blocks/stone.json",
        "data/example/loot_tables/blocks/other.json", "data/example/loot_tables/chests/bridge_block.json",
        "data/example/loot_table/blocks/bridge_block.json", "data/example/loot_tables_future/blocks/bridge_block.json",
        "data/example/loot_tables.json", "data/example/loot_tables/blocks/bridge_block.JSON",
        "data/example/LOOT_TABLES/blocks/bridge_block.json", "data/example/loot_tables/blocks/../blocks/bridge_block.json",
        "data/example//loot_tables/blocks/bridge_block.json", "data/example/loot_tables/blocks/bridge_block.json/extra",
        "data/example/loot_tables", "data/example/loot_tables/", "data/example/loot_modifiers/custom.json",
        "data/forge/loot_modifiers/global_loot_modifiers.json", "data/example/global_loot_modifiers.json",
        "data\\example\\loot_tables\\blocks\\bridge_block.json", "assets/example/loot_tables/blocks/bridge_block.json"})
    void unsupportedOtherLootPathsNeverDisappearIntoAbsenceOrPermitCoexistence(String path) {
        assertThrows(SubsetRejectionException.class, () -> StaticShapeLootContract.inspect(Map.of(path, utf8(valid("1"))), plan()));
        assertThrows(SubsetRejectionException.class, () -> StaticShapeLootContract.inspect(Map.of(PATH, utf8(valid("1")), path, utf8(valid("1"))), plan()));
    }

    @TestFactory Stream<DynamicTest> rejectsUnknownRootPoolEntryAndConditionContracts() {
        var json = new LinkedHashMap<String,String>();
        String pool = pool("1");
        json.put("root functions", valid("1").replace("\"type\":\"minecraft:block\"", "\"type\":\"minecraft:block\",\"functions\":[]"));
        json.put("random sequence", valid("1").replace("\"type\":\"minecraft:block\"", "\"type\":\"minecraft:block\",\"random_sequence\":\"example:sequence\""));
        json.put("seed", valid("1").replace("\"type\":\"minecraft:block\"", "\"type\":\"minecraft:block\",\"seed\":0"));
        json.put("forge root conditions", valid("1").replace("\"type\":\"minecraft:block\"", "\"type\":\"minecraft:block\",\"conditions\":[]"));
        json.put("wrong root type", valid("1").replace("minecraft:block", "minecraft:entity"));
        json.put("implicit namespace", valid("1").replace("minecraft:block", "block"));
        json.put("missing table type", "{\"pools\":[" + pool + "]}");
        json.put("two pools", "{\"type\":\"minecraft:block\",\"pools\":[" + pool + "," + pool + "]}");
        json.put("empty pools", "{\"type\":\"minecraft:block\",\"pools\":[]}");
        for (String member : List.of("\"bonus_rolls\":0", "\"functions\":[]", "\"name\":\"main\"", "\"unknown\":false"))
            json.put("extra pool " + member, valid("1").replace("\"rolls\":1", "\"rolls\":1," + member));
        json.put("missing rolls", valid("1").replace("\"rolls\":1,", ""));
        json.put("wrong item", valid("1").replace("example:bridge_block", "example:other"));
        json.put("foreign item", valid("1").replace("example:bridge_block", "minecraft:stone"));
        json.put("entry reference", valid("1").replace("minecraft:item", "minecraft:loot_table"));
        json.put("entry tag", valid("1").replace("minecraft:item", "minecraft:tag"));
        json.put("entry alternative", valid("1").replace("minecraft:item", "minecraft:alternatives"));
        for (String member : List.of("\"weight\":1", "\"quality\":0", "\"count\":1", "\"functions\":[]", "\"conditions\":[]"))
            json.put("extra entry " + member, valid("1").replace(ENTRY, ENTRY.substring(0, ENTRY.length() - 1) + "," + member + "}"));
        json.put("two entries", valid("1").replace("[" + ENTRY + "]", "[" + ENTRY + "," + ENTRY + "]"));
        json.put("zero entries", valid("1").replace("[" + ENTRY + "]", "[]"));
        json.put("missing condition", valid("1").replace("[" + CONDITION + "]", "[]"));
        json.put("two conditions", valid("1").replace("[" + CONDITION + "]", "[" + CONDITION + "," + CONDITION + "]"));
        json.put("other condition", valid("1").replace("minecraft:survives_explosion", "minecraft:random_chance"));
        json.put("condition parameters", valid("1").replace(CONDITION, "{\"condition\":\"minecraft:survives_explosion\",\"chance\":1}"));
        json.put("missing pool conditions", valid("1").replace(",\"conditions\":[" + CONDITION + "]", ""));
        json.put("null pool", "{\"type\":\"minecraft:block\",\"pools\":[null]}");
        json.put("object pools", "{\"type\":\"minecraft:block\",\"pools\":{}}");
        return json.entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey(), () -> refused(e.getValue())));
    }

    @ParameterizedTest @ValueSource(strings={"{}", "[]", "null", "true", "1", "\"table\"", "{", "", " ", "{}{}"})
    void rejectsNonTableAndMalformedRoots(String json) { refused(json); }

    @Test void rejectsDuplicateObjectMembersAtEveryDepth() {
        refused(valid("1").replace("\"type\":\"minecraft:block\"", "\"type\":\"minecraft:block\",\"type\":\"minecraft:block\""));
        refused(valid("1").replace("\"rolls\":1", "\"rolls\":1,\"rolls\":1"));
        refused(valid("1").replace("\"name\":\"example:bridge_block\"", "\"name\":\"example:bridge_block\",\"name\":\"example:bridge_block\""));
        refused(valid("1").replace(CONDITION, "{\"condition\":\"minecraft:survives_explosion\",\"condition\":\"minecraft:survives_explosion\"}"));
    }

    @Test void boundsBytesDepthNodesStringsNumbersAndRequiresStrictUtf8() {
        var oversized = new byte[1_048_577]; Arrays.fill(oversized, (byte)' ');
        assertThrows(SubsetRejectionException.class, () -> StaticShapeLootContract.inspect(Map.of(PATH, oversized), plan()));
        assertThrows(SubsetRejectionException.class, () -> StaticShapeLootContract.inspect(Map.of(PATH, new byte[]{(byte)0xc3, (byte)0x28}), plan()));
        refused("\ufeff" + valid("1"));
        refused("[".repeat(70) + "0" + "]".repeat(70));
        refused("[" + "0,".repeat(20_001) + "0]");
        refused("\"" + "x".repeat(16_385) + "\"");
        refused(valid("1".repeat(1025)));
        refused(valid("1e999999999999999999999999"));
    }

    @Test void missingPayloadNullPathOrUnboundedInventoryAreRejected() {
        var missing = new HashMap<String,byte[]>(); missing.put(PATH, null);
        assertThrows(SubsetRejectionException.class, () -> StaticShapeLootContract.inspect(missing, plan()));
        var nullPath = new HashMap<String,byte[]>(); nullPath.put(null, utf8("{}"));
        assertThrows(SubsetRejectionException.class, () -> StaticShapeLootContract.inspect(nullPath, plan()));
        assertThrows(SubsetRejectionException.class, () -> StaticShapeLootContract.inspect(null, plan()));
        var huge = new HashMap<String,byte[]>(); for (int i = 0; i < 4097; i++) huge.put("data/example/recipes/r" + i + ".json", utf8("{}"));
        assertThrows(SubsetRejectionException.class, () -> StaticShapeLootContract.inspect(huge, plan()));
    }

    @Test void planIdentityIsRequiredEvenIfNoLootTableExists() {
        var good = plan(); var empty = Map.<String,byte[]>of();
        assertThrows(SubsetRejectionException.class, () -> StaticShapeLootContract.inspect(empty, null));
        var plans = List.of(
            new ContentPlan("invalid/id", "1", good.items(), good.blocks(), List.of()),
            new ContentPlan("example", "1", List.of(), good.blocks(), List.of()),
            new ContentPlan("example", "1", good.items(), List.of(), List.of()),
            new ContentPlan("example", "1", good.items(), good.blocks(), List.of(new ContentPlan.SoundDefinition("sound", "example:sound"))),
            new ContentPlan("example", "1", List.of(new ContentPlan.ItemDefinition("other", "example:bridge_block", good.items().get(0).properties())), good.blocks(), List.of()),
            new ContentPlan("example", "1", List.of(new ContentPlan.ItemDefinition("bridge_block", "other:bridge_block", good.items().get(0).properties())), good.blocks(), List.of()));
        for (var p : plans) assertThrows(SubsetRejectionException.class, () -> StaticShapeLootContract.inspect(empty, p));
    }

    @Test void cooperativeCancellationRemainsControlPlaneFailure() {
        var control = new OperationControl(OperationLimits.defaults(), progress -> { }); control.requestCancel();
        try (var ignored = control.enter()) {
            var failure = assertThrows(OperationStoppedException.class, () -> StaticShapeLootContract.inspect(Map.of(PATH, utf8(valid("1"))), plan()));
            assertEquals(OperationStoppedException.Code.CANCELLED, failure.code());
        }
    }

    private static ContentPlan plan() {
        return new ContentPlan("example", "1.0.0",
            List.of(new ContentPlan.ItemDefinition("bridge_block", "example:bridge_block", new ContentPlan.ItemProperties(32, 0, false, "COMMON", null))),
            List.of(new ContentPlan.BlockDefinition("bridge_block", new ContentPlan.BlockProperties("STONE", 2, 6, "STONE", false, false))), List.of());
    }
    private static String pool(String rolls) { return "{\"rolls\":" + rolls + ",\"entries\":[" + ENTRY + "],\"conditions\":[" + CONDITION + "]}"; }
    private static String valid(String rolls) { return "{\"type\":\"minecraft:block\",\"pools\":[" + pool(rolls) + "]}"; }
    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static void refused(String json) {
        var failure = assertThrows(SubsetRejectionException.class, () -> StaticShapeLootContract.inspect(Map.of(PATH, utf8(json)), plan()));
        assertTrue(failure.getMessage().startsWith("STATIC_SHAPE_LOOT_UNSUPPORTED:"), failure.getMessage());
    }
}
