package dev.legacybridge.fixture.staticshapedrop;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Archive-only validation: no original class is referenced, loaded or executed. */
final class StaticShapeDropFixtureArtifactTest {
    private static final String PREFIX = "assets/legacybridge_static_shape_drop/";

    @Test
    void metadataPinsRealForgeSource() throws IOException {
        try (JarFile jar = input()) {
            String metadata = text(jar, "META-INF/mods.toml");
            assertTrue(metadata.contains("modId=\"legacybridge_static_shape_drop\""));
            assertTrue(metadata.contains("version=\"1.0.0\""));
            assertTrue(metadata.contains("versionRange=\"[36.2.42,37)\""));
            assertTrue(metadata.contains("versionRange=\"[1.16.5,1.17)\""));
            assertTrue(text(jar, "pack.mcmeta").contains("\"pack_format\": 6"));
            assertEquals("1.0.0", jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
            assertEquals("static-shape-drop-v1", jar.getManifest().getMainAttributes().getValue("LegacyBridge-Fixture"));
        }
    }

    @Test
    void bothOriginalClassesAreJava8WithNoPlatformClassesOrHarness() throws IOException {
        try (JarFile jar = input()) {
            Enumeration<JarEntry> entries = jar.entries();
            int classes = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                assertFalse(name.startsWith("dev/legacybridge/validation/"), name);
                assertFalse(name.startsWith("META-INF/services/"), name);
                assertFalse(name.endsWith(".jar"), name);
                if (!name.endsWith(".class")) continue;
                assertTrue(name.equals("dev/legacybridge/fixture/staticshapedrop/StaticShapeDropContent.class")
                        || name.equals("dev/legacybridge/fixture/staticshapedrop/StaticShapeDropBlock.class"), name);
                try (DataInputStream bytes = new DataInputStream(jar.getInputStream(entry))) {
                    assertEquals(0xcafebabe, bytes.readInt());
                    assertEquals(0, bytes.readUnsignedShort());
                    assertEquals(52, bytes.readUnsignedShort());
                }
                classes++;
            }
            assertEquals(2, classes);
        }
    }

    @Test
    void resourcesResolveToVanillaTextureWithoutBundlingGameAssets() throws IOException {
        try (JarFile jar = input()) {
            assertTrue(text(jar, PREFIX + "blockstates/bridge_block.json")
                    .contains("legacybridge_static_shape_drop:block/bridge_block"));
            String model = text(jar, PREFIX + "models/block/bridge_block.json");
            assertTrue(model.contains("minecraft:block/block"));
            assertTrue(model.contains("\"from\": [0, 0, 0]"));
            assertTrue(model.contains("\"to\": [16, 8, 16]"));
            for (String direction : new String[] {"down", "up", "north", "south", "west", "east"})
                assertTrue(model.contains("\"" + direction + "\""), direction);
            assertTrue(model.contains("minecraft:block/stone"));
            assertTrue(text(jar, PREFIX + "models/item/bridge_block.json")
                    .contains("legacybridge_static_shape_drop:block/bridge_block"));
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertFalse(name.startsWith("assets/minecraft/"), name);
                assertFalse(name.startsWith("data/minecraft/"), name);
                assertFalse(name.endsWith(".png"), name);
            }
        }
    }

    @Test
    void retainsSingleRecipeAndBothLocalizations() throws IOException {
        try (JarFile jar = input()) {
            String recipe = text(jar, "data/legacybridge_static_shape_drop/recipes/bridge_block.json");
            assertTrue(recipe.contains("minecraft:crafting_shapeless"));
            assertTrue(recipe.contains("minecraft:iron_ingot"));
            assertTrue(recipe.contains("legacybridge_static_shape_drop:bridge_block"));
            assertTrue(text(jar, PREFIX + "lang/en_us.json").contains("Bridge Block"));
            assertTrue(text(jar, PREFIX + "lang/pt_br.json").contains("Bloco de ponte"));
            Enumeration<JarEntry> entries = jar.entries();
            int recipes = 0;
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("data/legacybridge_static_shape_drop/recipes/") && name.endsWith(".json")) recipes++;
            }
            assertEquals(1, recipes);
        }
    }

    @Test
    void authoredSelfDropTableIsPresentWithoutInventedExtraBehavior() throws IOException {
        try (JarFile jar = input()) {
            String loot = text(jar, "data/legacybridge_static_shape_drop/loot_tables/blocks/bridge_block.json");
            assertTrue(loot.contains("\"type\": \"minecraft:block\""));
            assertTrue(loot.contains("\"rolls\": 1"));
            assertTrue(loot.contains("\"type\": \"minecraft:item\""));
            assertTrue(loot.contains("\"name\": \"legacybridge_static_shape_drop:bridge_block\""));
            assertTrue(loot.contains("\"condition\": \"minecraft:survives_explosion\""));
            assertFalse(loot.contains("functions"));
            assertFalse(loot.contains("random_sequence"));
            assertFalse(loot.contains("bonus_rolls"));
        }
    }

    private static JarFile input() throws IOException {
        String path = System.getProperty("fixture.jar");
        assertNotNull(path, "The standalone test task must supply the reobfuscated artifact path");
        return new JarFile(path);
    }

    private static String text(JarFile jar, String name) throws IOException {
        JarEntry entry = jar.getJarEntry(name);
        assertNotNull(entry, name);
        assertTrue(entry.getSize() >= 0 && entry.getSize() <= 16_384, "Bounded authored resource: " + name);
        try (InputStream input = jar.getInputStream(entry);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                assertTrue(bytes.size() + count <= 16_384, "Resource exceeded bounded read: " + name);
                bytes.write(buffer, 0, count);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
