package dev.legacybridge.fixture.attributes;

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

/** Archive-only checks: neither authored class nor its original game dependencies execute. */
final class ItemAttributeFixtureArtifactTest {
    private static final String PREFIX = "assets/legacybridge_attributes/";

    @Test
    void metadataPinsOriginalForgeAndFixtureIdentity() throws IOException {
        try (JarFile jar = input()) {
            String metadata = text(jar, "META-INF/mods.toml");
            assertTrue(metadata.contains("modId=\"legacybridge_attributes\""));
            assertTrue(metadata.contains("version=\"1.0.0\""));
            assertTrue(metadata.contains("versionRange=\"[36.2.42,37)\""));
            assertTrue(metadata.contains("versionRange=\"[1.16.5,1.17)\""));
            assertTrue(text(jar, "pack.mcmeta").contains("\"pack_format\": 6"));
            assertEquals("1.0.0", jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
            assertEquals("item-attributes-v1", jar.getManifest().getMainAttributes().getValue("LegacyBridge-Fixture"));
            assertFalse(metadata.contains("legacybridge_runtime"));
        }
    }

    @Test
    void exactlyTwoOwnedJava8ClassesWithNoPlatformGuavaOrHarness() throws IOException {
        try (JarFile jar = input()) {
            Enumeration<JarEntry> entries = jar.entries();
            int classes = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                assertFalse(name.startsWith("dev/legacybridge/validation/"), name);
                assertFalse(name.startsWith("META-INF/services/"), name);
                assertFalse(name.startsWith("com/google/"), name);
                assertFalse(name.endsWith(".jar"), name);
                if (!name.endsWith(".class")) continue;
                assertTrue(name.equals("dev/legacybridge/fixture/attributes/ItemAttributeContent.class")
                    || name.equals("dev/legacybridge/fixture/attributes/AttributeContentItem.class"), name);
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
    void generatedItemModelUsesVanillaReferenceWithoutBundlingAssets() throws IOException {
        try (JarFile jar = input()) {
            String model = text(jar, PREFIX + "models/item/bridge_attribute_item.json");
            assertTrue(model.contains("minecraft:item/generated"));
            assertTrue(model.contains("minecraft:item/iron_ingot"));
            Enumeration<JarEntry> entries = jar.entries();
            int models = 0;
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertFalse(name.startsWith("assets/minecraft/"), name);
                assertFalse(name.startsWith("data/minecraft/"), name);
                assertFalse(name.endsWith(".png"), name);
                assertFalse(name.contains("/blockstates/"), name);
                assertFalse(name.contains("/loot_tables/"), name);
                if (name.startsWith(PREFIX + "models/") && name.endsWith(".json")) models++;
            }
            assertEquals(1, models);
        }
    }

    @Test
    void retainsOneLocalRecipeAndBothTranslations() throws IOException {
        try (JarFile jar = input()) {
            String recipe = text(jar, "data/legacybridge_attributes/recipes/bridge_attribute_item.json");
            assertTrue(recipe.contains("minecraft:crafting_shapeless"));
            assertTrue(recipe.contains("minecraft:iron_ingot"));
            assertTrue(recipe.contains("legacybridge_attributes:bridge_attribute_item"));
            assertTrue(text(jar, PREFIX + "lang/en_us.json").contains("Bridge Attribute Item"));
            assertTrue(text(jar, PREFIX + "lang/pt_br.json").contains("Item de atributos da ponte"));
            Enumeration<JarEntry> entries = jar.entries();
            int recipes = 0;
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("data/legacybridge_attributes/recipes/") && name.endsWith(".json")) recipes++;
            }
            assertEquals(1, recipes);
        }
    }

    private static JarFile input() throws IOException {
        String path = System.getProperty("fixture.jar");
        assertNotNull(path, "The standalone task must supply the reobfuscated artifact path");
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
