package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.conversion.extract.SubsetRejectionException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** Static resource-policy tests only: no original classes, native serializer or game is executed. */
class DatapackResourceValidatorTest {
    private static final String RECIPE = "data/fixturemod/recipes/example.json";
    private static final String TAG = "data/fixturemod/tags/items/inputs.json";
    private static final String MODEL = "assets/fixturemod/models/item/ingot.json";
    private static final List<String> COOKING = List.of("smelting", "blasting", "smoking", "campfire_cooking");
    private static final ContentPlan PLAN = new ContentPlan("fixturemod", "1.0.0", List.of(
            new ContentPlan.ItemDefinition("ingot", null,
                    new ContentPlan.ItemProperties(32, 0, false, "COMMON", "MISC")),
            new ContentPlan.ItemDefinition("block", "fixturemod:block",
                    new ContentPlan.ItemProperties(64, 0, false, "COMMON", "BUILDING_BLOCKS"))),
            List.of(new ContentPlan.BlockDefinition("block",
                    new ContentPlan.BlockProperties("STONE", 2, 6, "STONE", false, false))), List.of());

    @Test
    void preservesAllFiveNewFormatsAndExistingCraftingBytesWithImmutableAuditableDecisions() throws Exception {
        Map<String, byte[]> entries = resources();
        for (String type : COOKING) put(entries, recipePath(type), cooking(type, "{\"item\":\"minecraft:iron_ingot\"}",
                "fixturemod:ingot", ",\"group\":\"fixture_group\",\"experience\":0.5,\"cookingtime\":37"));
        put(entries, recipePath("stonecutting"), stonecutting("{\"item\":\"minecraft:stone\"}", "fixturemod:block", "2"));
        put(entries, recipePath("shaped"), "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"II\"],\"key\":{\"I\":{\"item\":\"fixturemod:ingot\"}},\"result\":{\"item\":\"fixturemod:block\"}}");
        put(entries, recipePath("shapeless"), "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:iron_ingot\"}],\"result\":{\"item\":\"fixturemod:ingot\",\"count\":2}}");
        Map<String, byte[]> snapshots = new HashMap<>();
        entries.forEach((path, bytes) -> snapshots.put(path, bytes.clone()));
        Map<String, byte[]> identities = new HashMap<>(entries);

        List<Map<String, Object>> decisions = ContentResourceValidator.validateDatapackRecipes(Map.copyOf(entries), PLAN);

        assertEquals(7, decisions.size());
        assertEquals(snapshots.keySet(), entries.keySet());
        snapshots.forEach((path, bytes) -> {
            assertSame(identities.get(path), entries.get(path));
            assertArrayEquals(bytes, entries.get(path));
        });
        Set<String> observed = new TreeSet<>();
        for (Map<String, Object> decision : decisions) {
            String path = (String) decision.get("path");
            String type = (String) decision.get("format");
            observed.add(type);
            assertEquals("PRESERVE_BYTES", decision.get("disposition"));
            assertEquals("resource.recipe." + type.substring("minecraft:".length()) + ".preserve.v1", decision.get("rule"));
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(entries.get(path))), decision.get("sha256"));
            assertFalse(((String) decision.get("reason")).isBlank());
            assertThrows(UnsupportedOperationException.class, () -> decision.put("runtimeValidation", "PASSED"));
        }
        assertEquals(Set.of("minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking",
                "minecraft:stonecutting", "minecraft:crafting_shaped", "minecraft:crafting_shapeless"), observed);
        assertThrows(UnsupportedOperationException.class, () -> decisions.add(Map.of()));
    }

    @Test
    void oldPolicyStillRejectsAllFiveNewFormats() {
        for (String type : COOKING) {
            Map<String, byte[]> entries = withRecipe(cooking(type, item(), "fixturemod:ingot", ""));
            assertThrows(SubsetRejectionException.class, () -> ContentResourceValidator.validate(entries, PLAN), type);
        }
        Map<String, byte[]> entries = withRecipe(stonecutting(item(), "fixturemod:ingot", "1"));
        assertThrows(SubsetRejectionException.class, () -> ContentResourceValidator.validate(entries, PLAN));
    }

    @Test
    void sourceDefaultsArePinnedAndOmittedNumericFieldsAreNotInjected() throws Exception {
        assertEquals(Map.of("minecraft:smelting", 200, "minecraft:blasting", 100,
                "minecraft:smoking", 100, "minecraft:campfire_cooking", 100), DatapackRecipeContract.COOKING_DEFAULT_TICKS);
        for (String type : COOKING) {
            Map<String, byte[]> entries = withRecipe(cooking(type, item(), "fixturemod:ingot", ""));
            byte[] before = entries.get(RECIPE).clone();
            assertEquals(1, ContentResourceValidator.validateDatapackRecipes(entries, PLAN).size());
            assertArrayEquals(before, entries.get(RECIPE));
        }
    }

    @Test
    void acceptsBoundedCookingNumbersIncludingExactIntegerExponentAndNegativeZeroExperience() throws Exception {
        for (String experience : List.of("0", "-0.0", "0.125", "1e-20", "100", "1e2")) {
            for (String time : List.of("1", "37", "72000", "1e2", "100.0")) {
                assertDoesNotThrow(() -> validate(cooking("smelting", item(), "fixturemod:ingot",
                        ",\"experience\":" + experience + ",\"cookingtime\":" + time)));
            }
        }
    }

    @Test
    void rejectsNegativeNonfiniteHugeWrongTypeOrExcessiveExperience() {
        for (String value : List.of("-0.01", "100.0001", "1e400", "NaN", "Infinity", "-Infinity", "\"0.5\"", "true", "null", "[]", "{}"))
            rejected(cooking("smelting", item(), "fixturemod:ingot", ",\"experience\":" + value));
    }

    @Test
    void rejectsNonpositiveFractionalOverflowOrWrongTypeCookingTime() {
        for (String value : List.of("0", "-1", "72001", "1.5", "2147483648", "1e400", "\"200\"", "false", "null"))
            rejected(cooking("smelting", item(), "fixturemod:ingot", ",\"cookingtime\":" + value));
    }

    @Test
    void stonecuttingRequiresCountWithinTheActualLocalResultStackBound() throws Exception {
        assertDoesNotThrow(() -> validate(stonecutting(item(), "fixturemod:ingot", "1")));
        assertDoesNotThrow(() -> validate(stonecutting(item(), "fixturemod:ingot", "32")));
        assertDoesNotThrow(() -> validate(stonecutting(item(), "fixturemod:block", "64")));
        assertDoesNotThrow(() -> validate(stonecutting(item(), "minecraft:stone", "64")));
        for (String value : List.of("0", "-1", "33", "64", "1.5", "1e400", "\"1\"", "null"))
            rejected(stonecutting(item(), "fixturemod:ingot", value));
        rejected("{\"type\":\"minecraft:stonecutting\",\"ingredient\":" + item() + ",\"result\":\"fixturemod:ingot\"}");
    }

    @Test
    void rejectsMissingResultOrIngredientRatherThanAssumingRegistryFallback() {
        for (String type : List.of("smelting", "stonecutting")) {
            rejected("{\"type\":\"minecraft:" + type + "\",\"ingredient\":" + item() + "}");
            rejected("{\"type\":\"minecraft:" + type + "\",\"result\":\"fixturemod:ingot\",\"count\":1}");
        }
    }

    @Test
    void rejectsMissingForeignAirAndUnauditedVanillaIdsForBothInputAndOutput() {
        for (String id : List.of("fixturemod:missing", "othermod:ingot", "minecraft:invented_item", "minecraft:air", "minecraft:apple")) {
            rejected(cooking("smelting", "{\"item\":\"" + id + "\"}", "fixturemod:ingot", ""));
            rejected(cooking("smelting", item(), id, ""));
            rejected(stonecutting(item(), id, "1"));
        }
    }

    @Test
    void acceptsUnqualifiedAuditedVanillaIdsWithoutRewritingTheirSpelling() throws Exception {
        Map<String, byte[]> entries = withRecipe(cooking("smelting", "{\"item\":\"iron_ingot\"}", "stone", ""));
        byte[] before = entries.get(RECIPE).clone();
        assertDoesNotThrow(() -> ContentResourceValidator.validateDatapackRecipes(entries, PLAN));
        assertArrayEquals(before, entries.get(RECIPE));
    }

    @Test
    void rejectsMalformedResourceLocationsAndEmptyOrForgeIgnoredRecipeIds() {
        for (String id : List.of("", "Fixturemod:ingot", "fixturemod:", "fixturemod:../ingot", "fixturemod:/ingot", "fixturemod:a//b", "fixturemod:a:b"))
            rejected(cooking("smelting", item(), id, ""));
        for (String path : List.of("data/fixturemod/recipes/.json", "data/fixturemod/recipes/_ignored.json",
                "data/fixturemod/recipes/_metadata/ignored.json", "data/fixturemod/recipes/../escape.json")) {
            Map<String, byte[]> entries = resources();
            put(entries, path, cooking("smelting", item(), "fixturemod:ingot", ""));
            rejected(entries);
        }
    }

    @Test
    void rejectsAllConditionalWrappersAndEvenFalseOrEmptyConditionsWithoutStrippingThem() {
        for (String conditions : List.of("[]", "[{\"type\":\"forge:false\"}]", "[{\"type\":\"forge:true\"}]",
                "[{\"type\":\"forge:mod_loaded\",\"modid\":\"missing\"}]", "null", "false"))
            rejected(cooking("smelting", item(), "fixturemod:ingot", ",\"conditions\":" + conditions));
        rejected("{\"type\":\"forge:conditional\",\"recipes\":[{\"conditions\":[{\"type\":\"forge:false\"}],\"recipe\":"
                + cooking("smelting", item(), "fixturemod:ingot", "") + "}]}");
        rejected("{\"type\":\"minecraft:crafting_shapeless\",\"conditions\":[],\"ingredients\":[" + item()
                + "],\"result\":{\"item\":\"fixturemod:ingot\"}}");
    }

    @Test
    void rejectsTargetOnlyCategoryCustomSerializersAndUnknownRecipeFields() {
        for (String member : List.of("\"category\":\"misc\"", "\"count\":1", "\"show_notification\":false",
                "\"nbt\":\"{}\"", "\"hidden\":true", "\"advancement\":null"))
            rejected(cooking("smelting", item(), "fixturemod:ingot", "," + member));
        for (String type : List.of("forge:smelting", "fixturemod:smelting", "minecraft:smithing", "minecraft:smithing_transform", "minecraft:crafting_special_repairitem"))
            rejected(cooking("smelting", item(), "fixturemod:ingot", "").replace("minecraft:smelting", type));
        for (String member : List.of("\"experience\":0", "\"cookingtime\":1")) {
            String recipe = stonecutting(item(), "fixturemod:ingot", "1");
            rejected(recipe.substring(0, recipe.length() - 1) + "," + member + "}");
        }
    }

    @Test
    void rejectsObjectNbtCountOrNullResultFormsEvenIfForgeHasAnExtendedSerializer() {
        for (String result : List.of("{\"item\":\"fixturemod:ingot\"}", "{\"item\":\"fixturemod:ingot\",\"count\":2}",
                "{\"item\":\"fixturemod:ingot\",\"nbt\":\"{}\"}", "null", "1", "[]"))
            rejected(cooking("smelting", item(), "fixturemod:ingot", "").replace("\"result\":\"fixturemod:ingot\"", "\"result\":" + result));
    }

    @Test
    void acceptsFlatIngredientAlternativesAndClosedNonemptyLocalTagChains() throws Exception {
        Map<String, byte[]> entries = withRecipe(cooking("smelting", "[{\"item\":\"minecraft:stone\"},{\"tag\":\"fixturemod:inputs\"}]", "fixturemod:ingot", ""));
        put(entries, TAG, "{\"values\":[\"#fixturemod:middle\"]}");
        put(entries, "data/fixturemod/tags/items/middle.json", "{\"values\":[{\"id\":\"#fixturemod:leaf\",\"required\":false}]}");
        put(entries, "data/fixturemod/tags/items/leaf.json", "{\"values\":[{\"id\":\"fixturemod:ingot\",\"required\":false}]}");
        assertDoesNotThrow(() -> ContentResourceValidator.validateDatapackRecipes(entries, PLAN));
    }

    @Test
    void rejectsEmptyNestedMalformedCustomAndNbtIngredients() {
        for (String ingredient : List.of("[]", "[[]]", "[[{\"item\":\"minecraft:stone\"}]]", "{}", "null", "\"minecraft:stone\"",
                "{\"item\":\"minecraft:stone\",\"tag\":\"fixturemod:inputs\"}", "{\"item\":\"minecraft:stone\",\"nbt\":\"{}\"}",
                "{\"type\":\"forge:nbt\",\"item\":\"minecraft:stone\",\"nbt\":\"{}\"}", "{\"item\":\"minecraft:stone\",\"count\":1}"))
            rejected(cooking("smelting", ingredient, "fixturemod:ingot", ""));
    }

    @Test
    void requiresMissingOptionalTagsAndOptionalItemsToBeResolvedToo() {
        Map<String, byte[]> missing = withRecipe(cooking("smelting", "{\"tag\":\"fixturemod:missing\"}", "fixturemod:ingot", ""));
        rejected(missing);
        for (String reference : List.of("#fixturemod:missing", "fixturemod:missing", "othermod:missing")) {
            Map<String, byte[]> entries = resources();
            put(entries, TAG, "{\"values\":[\"fixturemod:ingot\",{\"id\":\"" + reference + "\",\"required\":false}]}");
            rejected(entries);
        }
    }

    @Test
    void rejectsEmptyAndTransitivelyEmptyTagsEvenIfUnreferencedByAnyRecipe() {
        for (boolean referenced : List.of(false, true)) {
            Map<String, byte[]> entries = resources();
            put(entries, TAG, "{\"values\":[]}");
            if (referenced) put(entries, RECIPE, cooking("smelting", "{\"tag\":\"fixturemod:inputs\"}", "fixturemod:ingot", ""));
            rejected(entries);
            put(entries, TAG, "{\"values\":[\"#fixturemod:empty\"]}");
            put(entries, "data/fixturemod/tags/items/empty.json", "{\"values\":[]}");
            rejected(entries);
        }
    }

    @Test
    void rejectsTagCyclesEvenWhenCycleAlsoContainsAConcreteItem() {
        Map<String, byte[]> entries = resources();
        put(entries, TAG, "{\"values\":[\"fixturemod:ingot\",\"#fixturemod:inputs\"]}");
        rejected(entries);
        put(entries, TAG, "{\"values\":[\"#fixturemod:other\"]}");
        put(entries, "data/fixturemod/tags/items/other.json", "{\"values\":[\"fixturemod:ingot\",\"#fixturemod:inputs\"]}");
        rejected(entries);
    }

    @Test
    void preservesLocalReplaceTrueAndFalseWithoutFlatteningOrChangingOptionalEntries() throws Exception {
        for (boolean replace : List.of(false, true)) {
            Map<String, byte[]> entries = resources();
            put(entries, TAG, "{\"replace\":" + replace + ",\"values\":[{\"id\":\"fixturemod:ingot\",\"required\":false}]}");
            byte[] before = entries.get(TAG).clone();
            assertDoesNotThrow(() -> ContentResourceValidator.validateDatapackRecipes(entries, PLAN));
            assertArrayEquals(before, entries.get(TAG));
        }
    }

    @Test
    void narrowsExternalTagCompositionWithoutChangingTheOldForgeTagPolicy() throws Exception {
        Map<String, byte[]> entries = resources();
        put(entries, "data/forge/tags/items/ingots/fixture.json", "{\"replace\":true,\"values\":[\"fixturemod:ingot\"]}");
        assertDoesNotThrow(() -> ContentResourceValidator.validate(entries, PLAN));
        rejected(entries);
        for (String namespace : List.of("minecraft", "forge", "othermod")) {
            Map<String, byte[]> declaration = resources();
            put(declaration, "data/" + namespace + "/tags/items/inputs.json", "{\"values\":[\"fixturemod:ingot\"]}");
            rejected(declaration);
            rejected(cooking("smelting", "{\"tag\":\"" + namespace + ":planks\"}", "fixturemod:ingot", ""));
        }
    }

    @Test
    void rejectsTagSchemaExtensionsWrongKindsAndMalformedEntries() {
        for (String tag : List.of("{\"replace\":\"false\",\"values\":[\"fixturemod:ingot\"]}",
                "{\"values\":[\"fixturemod:ingot\"],\"remove\":[]}", "{\"values\":[\"fixturemod:ingot\"],\"conditions\":[]}",
                "{\"values\":[{\"id\":\"fixturemod:ingot\",\"required\":1}]}", "{\"values\":[{}]}", "{\"values\":null}")) {
            Map<String, byte[]> entries = resources();
            put(entries, TAG, tag);
            rejected(entries);
        }
        Map<String, byte[]> entries = resources();
        put(entries, "data/fixturemod/tags/blocks/inputs.json", "{\"values\":[\"fixturemod:ingot\"]}");
        rejected(entries);
    }

    @Test
    void rejectsDuplicateKeysTrailingContentAndLenientNonJsonSyntax() {
        String valid = cooking("smelting", item(), "fixturemod:ingot", "");
        for (String malformed : List.of(valid + "{}", valid + " garbage", "/*comment*/" + valid,
                valid.substring(0, valid.length() - 1) + ",}", valid.replace("\"type\"", "type"),
                valid.replace('"', '\''), valid.replace("\"result\":", "\"result\":\"fixturemod:ingot\",\"result\":"),
                valid.replace("\"ingredient\":", "\"ingredient\":null,\"ingredient\":"), "[]", "null"))
            rejected(malformed);
    }

    @Test
    void rejectsUnicodeDigitsInIntegerFractionAndExponentTokensInsteadOfApprovingInvalidJson() {
        for (String number : List.of("\u0661", "\uff11", "1\u0660", "1e\u0662", "1.\u0665", "-\u0661")) {
            rejected(cooking("smelting", item(), "fixturemod:ingot", ",\"cookingtime\":" + number));
            rejected(cooking("smelting", item(), "fixturemod:ingot", ",\"experience\":" + number));
            rejected(stonecutting(item(), "fixturemod:ingot", number));
        }
    }

    @Test
    void rejectsBrokenUtf8BomOversizeAndDepthBoundBeforeClaimingRecipeValidity() {
        Map<String, byte[]> entries = resources();
        entries.put(RECIPE, new byte[] {(byte) 0xc0, (byte) 0xaf});
        rejected(entries);
        put(entries, RECIPE, "\ufeff" + cooking("smelting", item(), "fixturemod:ingot", ""));
        rejected(entries);
        entries.put(RECIPE, new byte[1_048_577]);
        rejected(entries);
        put(entries, RECIPE, "[".repeat(70) + "0" + "]".repeat(70));
        rejected(entries);
    }

    @Test
    void retainsAllUnimplementedDatapackAndAssetExclusions() {
        for (String path : List.of("data/fixturemod/loot_tables/blocks/block.json", "data/forge/loot_modifiers/global_loot_modifiers.json",
                "data/fixturemod/advancements/recipes/example.json", "data/fixturemod/functions/tick.mcfunction",
                "data/fixturemod/worldgen/configured_feature/tree.json", "data/fixturemod/dimension/realm.json",
                "data/fixturemod/structures/example.nbt", "data/fixturemod/tags/fluids/water.json",
                "assets/fixturemod/textures/item/animated.png.mcmeta", "assets/fixturemod/shaders/core/test.json",
                "assets/fixturemod/sounds/test.ogg", "assets/othermod/lang/en_us.json", "META-INF/services/test.Service")) {
            Map<String, byte[]> entries = resources();
            put(entries, path, "{}");
            rejected(entries);
        }
        for (String member : List.of("\"loader\":\"forge:obj\"", "\"elements\":[]", "\"overrides\":[]", "\"display\":{}")) {
            Map<String, byte[]> entries = resources();
            put(entries, MODEL, "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/iron_ingot\"}," + member + "}");
            rejected(entries);
        }
    }

    @Test
    void failedValidationDoesNotMutatePreviouslyAcceptedRecipeOrAssetBytes() {
        Map<String, byte[]> entries = withRecipe(cooking("smelting", item(), "fixturemod:ingot", ""));
        put(entries, "data/fixturemod/recipes/zz_invalid.json", cooking("smelting", item(), "fixturemod:missing", ""));
        Map<String, byte[]> before = new HashMap<>();
        entries.forEach((path, bytes) -> before.put(path, bytes.clone()));
        rejected(entries);
        assertEquals(before.keySet(), entries.keySet());
        before.forEach((path, bytes) -> assertArrayEquals(bytes, entries.get(path)));
    }

    private static Map<String, byte[]> resources() {
        Map<String, byte[]> entries = new HashMap<>();
        put(entries, MODEL, "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/iron_ingot\"}}");
        put(entries, "assets/fixturemod/models/item/block.json", "{\"parent\":\"fixturemod:block/block\"}");
        put(entries, "assets/fixturemod/models/block/block.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/stone\"}}");
        put(entries, "assets/fixturemod/blockstates/block.json", "{\"variants\":{\"\":{\"model\":\"fixturemod:block/block\"}}}");
        put(entries, "assets/fixturemod/lang/en_us.json", "{\"item.fixturemod.ingot\":\"Fixture Ingot\"}");
        return entries;
    }

    private static String item() { return "{\"item\":\"minecraft:iron_ingot\"}"; }
    private static String recipePath(String name) { return "data/fixturemod/recipes/" + name + ".json"; }
    private static String cooking(String type, String ingredient, String result, String optional) {
        return "{\"type\":\"minecraft:" + type + "\",\"ingredient\":" + ingredient + ",\"result\":\"" + result + "\"" + optional + "}";
    }
    private static String stonecutting(String ingredient, String result, String count) {
        return "{\"type\":\"minecraft:stonecutting\",\"ingredient\":" + ingredient + ",\"result\":\"" + result + "\",\"count\":" + count + "}";
    }
    private static Map<String, byte[]> withRecipe(String json) {
        Map<String, byte[]> entries = resources();
        put(entries, RECIPE, json);
        return entries;
    }
    private static List<Map<String, Object>> validate(String json) throws SubsetRejectionException {
        return ContentResourceValidator.validateDatapackRecipes(withRecipe(json), PLAN);
    }
    private static void rejected(String json) { rejected(withRecipe(json)); }
    private static void rejected(Map<String, byte[]> entries) {
        assertThrows(SubsetRejectionException.class, () -> ContentResourceValidator.validateDatapackRecipes(entries, PLAN));
    }
    private static void put(Map<String, byte[]> entries, String path, String json) {
        entries.put(path, json.getBytes(StandardCharsets.UTF_8));
    }
}
