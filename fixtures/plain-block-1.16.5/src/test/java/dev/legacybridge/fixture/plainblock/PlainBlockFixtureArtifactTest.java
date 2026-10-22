package dev.legacybridge.fixture.plainblock;

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
final class PlainBlockFixtureArtifactTest {
    private static final String PREFIX = "assets/legacybridge_plain_block/";

    @Test
    void metadataPinsRealForgeSource() throws IOException {
        try (JarFile jar = input()) {
            String metadata = text(jar, "META-INF/mods.toml");
            assertTrue(metadata.contains("modId=\"legacybridge_plain_block\""));
            assertTrue(metadata.contains("version=\"1.0.0\""));
            assertTrue(metadata.contains("versionRange=\"[36.2.42,37)\""));
            assertTrue(metadata.contains("versionRange=\"[1.16.5,1.17)\""));
            assertTrue(text(jar, "pack.mcmeta").contains("\"pack_format\": 6"));
            assertEquals("1.0.0", jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
            assertEquals("plain-block-v1", jar.getManifest().getMainAttributes().getValue("LegacyBridge-Fixture"));
        }
    }

    @Test
    void soleOriginalClassIsJava8WithNoPlatformClassesOrHarness() throws IOException {
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
                assertEquals("dev/legacybridge/fixture/plainblock/PlainBlockContent.class", name);
                try (DataInputStream bytes = new DataInputStream(jar.getInputStream(entry))) {
                    assertEquals(0xcafebabe, bytes.readInt());
                    assertEquals(0, bytes.readUnsignedShort());
                    assertEquals(52, bytes.readUnsignedShort());
                }
                classes++;
            }
            assertEquals(1, classes);
        }
    }

    @Test
    void resourcesResolveToVanillaTextureWithoutBundlingGameAssets() throws IOException {
        try (JarFile jar = input()) {
            assertTrue(text(jar, PREFIX + "blockstates/bridge_block.json")
                    .contains("legacybridge_plain_block:block/bridge_block"));
            String model = text(jar, PREFIX + "models/block/bridge_block.json");
            assertTrue(model.contains("minecraft:block/cube_all"));
            assertTrue(model.contains("minecraft:block/stone"));
            assertTrue(text(jar, PREFIX + "models/item/bridge_block.json")
                    .contains("legacybridge_plain_block:block/bridge_block"));
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
            String recipe = text(jar, "data/legacybridge_plain_block/recipes/bridge_block.json");
            assertTrue(recipe.contains("minecraft:crafting_shapeless"));
            assertTrue(recipe.contains("minecraft:iron_ingot"));
            assertTrue(recipe.contains("legacybridge_plain_block:bridge_block"));
            assertTrue(text(jar, PREFIX + "lang/en_us.json").contains("Bridge Block"));
            assertTrue(text(jar, PREFIX + "lang/pt_br.json").contains("Bloco de ponte"));
            Enumeration<JarEntry> entries = jar.entries();
            int recipes = 0;
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("data/legacybridge_plain_block/recipes/") && name.endsWith(".json")) recipes++;
            }
            assertEquals(1, recipes);
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
