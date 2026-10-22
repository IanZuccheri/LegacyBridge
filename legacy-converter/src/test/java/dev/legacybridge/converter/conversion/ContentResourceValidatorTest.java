package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.conversion.extract.SubsetRejectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class ContentResourceValidatorTest {
    private static final String ASSETS = "assets/fixturemod/";
    private static final ContentPlan PLAN = new ContentPlan("fixturemod", "1.0.0", List.of(
            new ContentPlan.ItemDefinition("ingot", null, new ContentPlan.ItemProperties(32, 0, false, "COMMON", "MISC")),
            new ContentPlan.ItemDefinition("block", "fixturemod:block", new ContentPlan.ItemProperties(64, 0, false, "COMMON", "BUILDING_BLOCKS"))),
            List.of(new ContentPlan.BlockDefinition("block", new ContentPlan.BlockProperties("STONE", 2, 6, "STONE", false, false))),
            List.of());

    @Test
    void acceptsCompletePlainContentAndDoesNotMutateInputBytes() throws Exception {
        Map<String, byte[]> entries = resources();
        Map<String, byte[]> before = new HashMap<>();
        entries.forEach((path, bytes) -> before.put(path, bytes.clone()));
        ContentResourceValidator.validate(entries, PLAN);
        assertEquals(before.keySet(), entries.keySet());
        before.forEach((path, bytes) -> assertArrayEquals(bytes, entries.get(path)));
    }

    @Test
    void acceptsShapelessRecipeWithExistingForgeIngredientTag() throws Exception {
        Map<String, byte[]> entries = resources();
        put(entries, "data/forge/tags/items/ingots/bridge.json", "{\"values\":[\"fixturemod:ingot\"]}");
        put(entries, "data/fixturemod/recipes/block.json", "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"tag\":\"forge:ingots/bridge\"}],\"result\":{\"item\":\"fixturemod:block\"}}");
        assertDoesNotThrow(() -> ContentResourceValidator.validate(entries, PLAN));
    }

    @Test
    void rejectsStoneBlockAddedToVanillaWoolBecauseNoteInstrumentSemanticsChanged() {
        assertEquals("STONE", PLAN.blocks().get(0).properties().material());
        Map<String, byte[]> entries = resources();
        put(entries, "data/minecraft/tags/blocks/wool.json", "{\"replace\":false,\"values\":[\"fixturemod:block\"]}");
        assertRejected(entries, "Source Minecraft tag declarations are unsupported");
        assertRejected(entries, "behavior-changing global tags from 1.16.5 cannot be proven equivalent in 1.20.1");
    }

    @Test
    void rejectsEverySourceMinecraftTagFamilyIncludingFutureToolAndOreTags() {
        for (String path : List.of("data/minecraft/tags/items/iron_ores.json",
                "data/minecraft/tags/items/wool.json", "data/minecraft/tags/blocks/mineable/pickaxe.json",
                "data/minecraft/tags/blocks/needs_iron_tool.json", "data/minecraft/tags/entity_types/test.json")) {
            Map<String, byte[]> entries = resources();
            put(entries, path, "{\"values\":[]}");
            assertRejected(entries, "Source Minecraft tag declarations are unsupported");
        }
    }

    @Test
    void preservesOwnAndForgeCustomTagDeclarations() throws Exception {
        Map<String, byte[]> entries = resources();
        put(entries, "data/fixturemod/tags/blocks/bridge_blocks.json", "{\"values\":[\"fixturemod:block\"]}");
        put(entries, "data/forge/tags/blocks/bridge_blocks.json", "{\"values\":[\"#fixturemod:bridge_blocks\"]}");
        byte[] own = entries.get("data/fixturemod/tags/blocks/bridge_blocks.json").clone();
        byte[] forge = entries.get("data/forge/tags/blocks/bridge_blocks.json").clone();
        ContentResourceValidator.validate(entries, PLAN);
        assertArrayEquals(own, entries.get("data/fixturemod/tags/blocks/bridge_blocks.json"));
        assertArrayEquals(forge, entries.get("data/forge/tags/blocks/bridge_blocks.json"));
    }

    @Test
    void rejectsUnsupportedDataAndUnknownResources() {
        for (String path : List.of("data/fixturemod/worldgen/configured_feature/tree.json",
                "data/fixturemod/dimension/realm.json", "data/fixturemod/functions/tick.mcfunction",
                "data/fixturemod/loot_tables/blocks/block.json", "META-INF/services/test.Service",
                "assets/fixturemod/shaders/core/custom.json", "payload.jar")) {
            Map<String, byte[]> entries = resources();
            put(entries, path, "{}");
            assertRejected(entries, path);
        }
    }

    @Test
    void rejectsCustomModelLoaderOverrideAndGeometry() {
        for (String member : List.of("\"loader\":\"forge:obj\"", "\"overrides\":[]", "\"elements\":[]", "\"display\":{}")) {
            Map<String, byte[]> entries = resources();
            put(entries, ASSETS + "models/item/ingot.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/iron_ingot\"}," + member + "}");
            assertRejected(entries, "unsupported JSON member");
        }
    }

    @Test
    void rejectsMissingTextureParentAndRegisteredItemModel() {
        Map<String, byte[]> entries = resources();
        put(entries, ASSETS + "models/item/ingot.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"fixturemod:item/missing\"}}");
        assertRejected(entries, "missing required local resource");
        entries = resources();
        put(entries, ASSETS + "models/item/ingot.json", "{\"parent\":\"fixturemod:item/missing\"}");
        assertRejected(entries, "missing or foreign parent model");
        entries = resources();
        entries.remove(ASSETS + "models/item/ingot.json");
        assertRejected(entries, "models/item/ingot.json");
    }

    @Test
    void rejectsForeignAndUnknownVanillaDependencies() {
        for (String item : List.of("othermod:ingot", "fixturemod:missing", "minecraft:invented_item")) {
            Map<String, byte[]> entries = resources();
            put(entries, "data/fixturemod/recipes/block.json", "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"" + item + "\"}],\"result\":{\"item\":\"fixturemod:block\"}}");
            assertRejected(entries, "reference");
        }
        Map<String, byte[]> entries = resources();
        put(entries, "assets/minecraft/lang/en_us.json", "{}");
        assertRejected(entries, "assets must belong");
    }

    @Test
    void rejectsModelParentAndTextureAliasCycles() {
        Map<String, byte[]> entries = resources();
        put(entries, ASSETS + "models/item/ingot.json", "{\"parent\":\"fixturemod:item/ingot\"}");
        assertRejected(entries, "model parent cycle");
        entries = resources();
        put(entries, ASSETS + "models/item/ingot.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"#other\",\"other\":\"#layer0\"}}");
        assertRejected(entries, "cyclic texture alias");
    }

    @Test
    void rejectsIncompleteTextureBindingsAndMalformedJson() {
        Map<String, byte[]> entries = resources();
        put(entries, ASSETS + "models/item/ingot.json", "{\"parent\":\"minecraft:item/generated\"}");
        assertRejected(entries, "requires texture variable");
        entries = resources();
        put(entries, ASSETS + "lang/en_us.json", "{\"key\":\"one\",\"key\":\"two\"}");
        assertRejected(entries, "duplicate object key");
        entries = resources();
        entries.put(ASSETS + "lang/en_us.json", new byte[] {(byte) 0xc0, (byte) 0xaf});
        assertRejected(entries, "RESOURCE_INVALID");
    }

    @Test
    void rejectsInvalidRecipesAndSerializerExtensions() {
        for (String recipe : List.of(
                "{\"type\":\"forge:conditional\"}",
                "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"XX\",\"X\"],\"key\":{},\"result\":{\"item\":\"fixturemod:block\"}}",
                "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[],\"result\":{\"item\":\"fixturemod:block\"}}",
                "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"fixturemod:ingot\",\"nbt\":\"{}\"}],\"result\":{\"item\":\"fixturemod:block\"}}")) {
            Map<String, byte[]> entries = resources();
            put(entries, "data/fixturemod/recipes/block.json", recipe);
            assertRejected(entries, "data/fixturemod/recipes/block.json");
        }
    }

    @Test
    void rejectsTagCyclesAndMissingRequiredTags() {
        Map<String, byte[]> entries = resources();
        put(entries, "data/fixturemod/tags/items/ingots.json", "{\"values\":[\"#fixturemod:ingots\"]}");
        assertRejected(entries, "tag reference cycle");
        entries = resources();
        put(entries, "data/fixturemod/tags/items/ingots.json", "{\"values\":[\"#forge:nonexistent\"]}");
        assertRejected(entries, "required tag is absent");
    }

    @Test
    void acceptsUnconditionalMultipartButRejectsStatesAndMissingApply() throws Exception {
        Map<String, byte[]> entries = resources();
        put(entries, ASSETS + "blockstates/block.json", "{\"multipart\":[{\"apply\":{\"model\":\"fixturemod:block/block\"}}]}");
        ContentResourceValidator.validate(entries, PLAN);
        put(entries, ASSETS + "blockstates/block.json", "{\"variants\":{\"facing=north\":{\"model\":\"fixturemod:block/block\"}}}");
        assertRejected(entries, "plain blocks support only the default state");
        put(entries, ASSETS + "blockstates/block.json", "{\"multipart\":[{}]}");
        assertRejected(entries, "definition is missing");
    }

    @Test
    void rejectsTruncatedPngAndOggAndAnimationMetadata() {
        Map<String, byte[]> entries = resources();
        entries.put(ASSETS + "textures/item/broken.png", new byte[] {1, 2, 3});
        assertRejected(entries, "truncated PNG");
        entries = resources();
        entries.put(ASSETS + "sounds/broken.ogg", new byte[60]);
        assertRejected(entries, "Ogg/Vorbis");
        entries = resources();
        put(entries, ASSETS + "textures/item/animated.png.mcmeta", "{\"animation\":{}}");
        assertRejected(entries, "animation metadata");
    }

    @Test
    void rejectsMissingForeignAndCyclicSounds() {
        for (String target : List.of("fixturemod:absent", "othermod:sound", "fixturemod:ping")) {
            Map<String, byte[]> entries = resources();
            put(entries, ASSETS + "sounds.json", "{\"ping\":{\"sounds\":[{\"name\":\"" + target + "\",\"type\":\"event\"}]}}");
            assertRejected(entries, target.equals("fixturemod:ping") ? "sound event reference cycle" : "sound event");
        }
    }

    @Test
    void rejectsAllOggAssetsAndFileSoundEntriesWithoutPretendingHeaderChecksDecodeAudio() {
        Map<String, byte[]> entries = resources();
        entries.put(ASSETS + "sounds/header_only.ogg", "OggS plausible header is still unsupported".getBytes(StandardCharsets.US_ASCII));
        assertRejected(entries, "Local Ogg/Vorbis files are unsupported until bounded audio decoding is implemented");
        entries = resources();
        put(entries, ASSETS + "sounds.json", "{\"ping\":{\"sounds\":[{\"name\":\"fixturemod:ping\",\"type\":\"file\"}]}}");
        assertRejected(entries, "Sound type=file is unsupported until bounded audio decoding is implemented");
        put(entries, ASSETS + "sounds.json", "{\"ping\":{\"sounds\":[\"fixturemod:ping\"]}}");
        assertRejected(entries, "Sound type=file is unsupported");
    }

    @Test
    void acceptsRegisteredEventAndLocalAliasChainEndingAtAuditedVanillaEvent() throws Exception {
        Map<String, byte[]> entries = resources();
        put(entries, ASSETS + "sounds.json", "{\"ping\":{\"sounds\":[{\"name\":\"fixturemod:alias\",\"type\":\"event\"}]},\"alias\":{\"sounds\":[{\"name\":\"minecraft:block.note_block.pling\",\"type\":\"event\"}]}}");
        ContentPlan sounds = new ContentPlan(PLAN.modId(), PLAN.modVersion(), PLAN.items(), PLAN.blocks(),
                List.of(new ContentPlan.SoundDefinition("ping", "fixturemod:ping")));
        assertDoesNotThrow(() -> ContentResourceValidator.validate(entries, sounds));
    }

    @Test
    @EnabledIfSystemProperty(named = "legacybridge.verifyMappingEnvironment", matches = "true")
    void validatesRealForgeFixtureResourcesWhenEnvironmentVerificationIsRequested() throws Exception {
        Path jarPath = Path.of(System.getProperty("legacybridge.fixtureRoot"), "simple-content-1.16.5", "build", "libs",
                "legacybridge-example-1.16.5-1.0.0.jar");
        Map<String, byte[]> entries = new HashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            for (var iterator = jar.entries().asIterator(); iterator.hasNext();) {
                var entry = iterator.next();
                if (entry.isDirectory()) continue;
                try (var input = jar.getInputStream(entry)) { entries.put(entry.getName(), input.readAllBytes()); }
            }
        }
        ContentPlan real = new ContentPlan("legacybridge_example", "1.0.0", List.of(
                new ContentPlan.ItemDefinition("bridge_ingot", null, PLAN.items().get(0).properties()),
                new ContentPlan.ItemDefinition("bridge_block", "legacybridge_example:bridge_block", PLAN.items().get(1).properties())),
                List.of(new ContentPlan.BlockDefinition("bridge_block", PLAN.blocks().get(0).properties())), List.of());
        assertDoesNotThrow(() -> ContentResourceValidator.validate(entries, real));
    }

    private static Map<String, byte[]> resources() {
        Map<String, byte[]> entries = new HashMap<>();
        put(entries, "META-INF/mods.toml", "root validates metadata");
        put(entries, "META-INF/MANIFEST.MF", "root validates manifest");
        put(entries, "pack.mcmeta", "root validates pack metadata");
        put(entries, ASSETS + "models/item/ingot.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/iron_ingot\"}}");
        put(entries, ASSETS + "models/item/block.json", "{\"parent\":\"fixturemod:block/block\"}");
        put(entries, ASSETS + "models/block/block.json", "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/stone\"}}");
        put(entries, ASSETS + "blockstates/block.json", "{\"variants\":{\"\":{\"model\":\"fixturemod:block/block\"}}}");
        put(entries, ASSETS + "lang/en_us.json", "{\"item.fixturemod.ingot\":\"Fixture Ingot\"}");
        put(entries, ASSETS + "sounds.json", "{\"ping\":{\"sounds\":[{\"name\":\"minecraft:block.note_block.pling\",\"type\":\"event\"}]}}");
        put(entries, "data/fixturemod/tags/items/ingots.json", "{\"values\":[\"fixturemod:ingot\"]}");
        put(entries, "data/fixturemod/recipes/block.json", "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"II\",\"II\"],\"key\":{\"I\":{\"item\":\"minecraft:iron_ingot\"}},\"result\":{\"item\":\"fixturemod:ingot\",\"count\":1}}");
        return entries;
    }

    private static void put(Map<String, byte[]> entries, String path, String json) { entries.put(path, json.getBytes(StandardCharsets.UTF_8)); }
    private static void assertRejected(Map<String, byte[]> entries, String diagnostic) {
        SubsetRejectionException error = assertThrows(SubsetRejectionException.class, () -> ContentResourceValidator.validate(entries, PLAN));
        assertTrue(error.getMessage().contains(diagnostic), error::getMessage);
    }
}
