package dev.legacybridge.runtime;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SimpleContentPlanTest {
    private static final String PLAN = """
            {"schemaVersion":1,"modId":"legacybridge_example","modVersion":"1.0.0",
             "items":[
              {"name":"bridge_ingot","block":null,"properties":{"stackSize":32,"durability":0,"fireResistant":false,"rarity":"COMMON","creativeTab":"MISC"}},
              {"name":"bridge_block","block":"legacybridge_example:bridge_block","properties":{"stackSize":64,"durability":0,"fireResistant":false,"rarity":"COMMON","creativeTab":"BUILDING_BLOCKS"}}
             ],"blocks":[{"name":"bridge_block","properties":{"material":"STONE","hardness":2,"resistance":6,"sound":"STONE","requiresCorrectTool":false,"noOcclusion":false}}],
             "sounds":[{"name":"bridge_ping","resourceLocation":"legacybridge_example:bridge_ping"}]}
            """;

    @Test
    void parsesExactLocalContentAndPreservesImmutableDefinitions() throws IOException {
        SimpleContentPlan plan = parse(PLAN);
        assertEquals("1.0.0", plan.modVersion());
        assertEquals(32, plan.items().get(0).properties().stackSize());
        assertEquals("legacybridge_example:bridge_block", plan.items().get(1).block());
        assertEquals(2, plan.blocks().get(0).properties().hardness());
        assertEquals(6, plan.blocks().get(0).properties().resistance());
        assertEquals("legacybridge_example:bridge_ping", plan.sounds().get(0).resourceLocation());
        assertThrows(UnsupportedOperationException.class, () -> plan.items().clear());
    }

    @Test
    void rejectsUnknownMissingAndDuplicateFieldsAndTrailingContent() {
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"execute\":true")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"modVersion\":\"1.0.0\",", "")));
        assertThrows(IOException.class, () -> parse(PLAN + "{}"));
    }

    @Test
    void rejectsForeignNamespaceAndUnknownBlockReferences() {
        assertThrows(IOException.class, () -> SimpleContentPlan.read(input(PLAN), "another_mod"));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"block\":\"legacybridge_example:bridge_block\"", "\"block\":\"minecraft:stone\"")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"block\":\"legacybridge_example:bridge_block\"", "\"block\":\"legacybridge_example:missing\"")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("legacybridge_example:bridge_ping", "minecraft:block.note_block.pling")));
    }

    @Test
    void rejectsInventedPropertyTokensAndWrongScalarTypes() {
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"material\":\"STONE\"", "\"material\":\"CUSTOM\"")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"creativeTab\":\"MISC\"", "\"creativeTab\":\"SEARCH\"")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"stackSize\":32", "\"stackSize\":\"32\"")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"fireResistant\":false", "\"fireResistant\":\"false\"")));
    }

    @Test
    void refusesUnverifiedKnownMaterialsPropertiesAndCreativeGroups() {
        for (String material : new String[]{"WOOD", "METAL", "DIRT", "GLASS"}) {
            assertThrows(IOException.class, () -> parse(PLAN.replace("\"material\":\"STONE\"", "\"material\":\"" + material + "\"")));
        }
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"sound\":\"STONE\"", "\"sound\":\"WOOD\"")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"rarity\":\"COMMON\"", "\"rarity\":\"RARE\"")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"fireResistant\":false", "\"fireResistant\":true")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"requiresCorrectTool\":false", "\"requiresCorrectTool\":true")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"noOcclusion\":false", "\"noOcclusion\":true")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"creativeTab\":\"MISC\"", "\"creativeTab\":\"TOOLS\"")));
    }

    @Test
    void rejectsIncompatibleDurabilityAndOutOfRangeNumbers() {
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"stackSize\":32", "\"stackSize\":65")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"durability\":0", "\"durability\":250")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"hardness\":2", "\"hardness\":-1")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"hardness\":2", "\"hardness\":1e999")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"stackSize\":32", "\"stackSize\":1.25")));
    }

    @Test
    void rejectsDuplicateDefinitionsTraversalAndMalformedUtf8() {
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"name\":\"bridge_ingot\"", "\"name\":\"bridge_block\"")));
        assertThrows(IOException.class, () -> parse(PLAN.replace("\"name\":\"bridge_ingot\"", "\"name\":\"../bridge_ingot\"")));
        assertThrows(IOException.class, () -> SimpleContentPlan.read(new ByteArrayInputStream(new byte[]{(byte) 0xc3, 0x28}), "legacybridge_example"));
    }

    @Test
    void rejectsOversizedPlansAndExcessiveNestingBeforeRegistration() {
        assertThrows(IOException.class, () -> SimpleContentPlan.read(new ByteArrayInputStream(new byte[SimpleContentPlan.MAX_PLAN_BYTES + 1]), "legacybridge_example"));
        assertThrows(IOException.class, () -> parse("[".repeat(20) + "0" + "]".repeat(20)));
    }

    private static SimpleContentPlan parse(String value) throws IOException { return SimpleContentPlan.read(input(value), "legacybridge_example"); }
    private static ByteArrayInputStream input(String value) { return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); }
}
