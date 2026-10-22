package dev.legacybridge.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Reads artifact bytes only: no source mod classes are loaded or initialized. */
class FixtureArtifactTest {
    private JarFile fixture() throws IOException {
        return new JarFile(Paths.get(System.getProperty("fixture.jar")).toFile());
    }

    @Test
    void containsOneJava8ProductionClassAndNoAssetGenerator() throws IOException {
        try (JarFile jar = fixture()) {
            assertEquals(1L, jar.stream().filter(entry -> entry.getName().endsWith(".class")).count());
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().contains("fixtureassets")));
            byte[] bytes = read(jar, "dev/legacybridge/fixture/LegacyBridgeExample.class");
            try (DataInputStream header = new DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
                assertEquals(0xCAFEBABE, header.readInt());
                header.readUnsignedShort();
                assertEquals(52, header.readUnsignedShort(), "input must actually use Java 8 bytecode");
            }
            String constants = new String(bytes, StandardCharsets.ISO_8859_1);
            assertTrue(constants.contains("func_"), "ForgeGradle must reobfuscate Minecraft members to SRG");
            assertTrue(constants.contains("net/minecraft/item/Item$Properties"));
            assertFalse(constants.contains("net/minecraft/world/item/Item$Properties"));
        }
    }

    @Test
    void retainsExactLegacyIdentityAndOneRecipe() throws IOException {
        try (JarFile jar = fixture()) {
            String metadata = text(jar, "META-INF/mods.toml");
            assertTrue(metadata.contains("modId=\"legacybridge_example\""));
            assertTrue(metadata.contains("version=\"1.0.0\""));
            assertTrue(metadata.contains("versionRange=\"[1.16.5,1.17)\""));
            assertEquals(1L, jar.stream().filter(entry -> entry.getName().contains("/recipes/")
                    && entry.getName().endsWith(".json")).count());
            String recipe = text(jar, "data/legacybridge_example/recipes/bridge_block.json");
            assertTrue(recipe.contains("minecraft:crafting_shaped"));
            assertTrue(recipe.contains("legacybridge_example:bridge_ingot"));
            assertTrue(recipe.contains("legacybridge_example:bridge_block"));
            assertEquals(3, recipe.split("\"III\"", -1).length - 1);
        }
    }

    @Test
    void containsReadableOriginalTexturesAndLinkedModels() throws IOException {
        try (JarFile jar = fixture()) {
            for (String asset : new String[] {"block/bridge_block", "item/bridge_ingot"}) {
                try (InputStream input = jar.getInputStream(jar.getJarEntry(
                        "assets/legacybridge_example/textures/" + asset + ".png"))) {
                    BufferedImage image = ImageIO.read(input);
                    assertNotNull(image);
                    assertEquals(16, image.getWidth());
                    assertEquals(16, image.getHeight());
                }
            }
            assertTrue(text(jar, "assets/legacybridge_example/models/block/bridge_block.json")
                    .contains("legacybridge_example:block/bridge_block"));
            assertTrue(text(jar, "assets/legacybridge_example/models/item/bridge_ingot.json")
                    .contains("legacybridge_example:item/bridge_ingot"));
            assertTrue(text(jar, "assets/legacybridge_example/models/item/bridge_block.json")
                    .contains("legacybridge_example:block/bridge_block"));
            assertNotNull(jar.getJarEntry("assets/legacybridge_example/blockstates/bridge_block.json"));
        }
    }

    @Test
    void containsTagsTranslationsAndSimpleVanillaSoundReference() throws IOException {
        try (JarFile jar = fixture()) {
            assertTrue(text(jar, "data/legacybridge_example/tags/items/bridge_ingots.json")
                    .contains("legacybridge_example:bridge_ingot"));
            assertTrue(text(jar, "data/legacybridge_example/tags/blocks/bridge_blocks.json")
                    .contains("legacybridge_example:bridge_block"));
            assertTrue(text(jar, "assets/legacybridge_example/lang/en_us.json").contains("Bridge Ingot"));
            assertTrue(text(jar, "assets/legacybridge_example/lang/pt_br.json").contains("Lingote da Ponte"));
            assertTrue(text(jar, "assets/legacybridge_example/sounds.json").contains("minecraft:block.note_block.pling"));
        }
    }

    private static String text(JarFile jar, String path) throws IOException {
        return new String(read(jar, path), StandardCharsets.UTF_8);
    }

    private static byte[] read(JarFile jar, String path) throws IOException {
        assertNotNull(jar.getJarEntry(path), "Missing " + path);
        try (InputStream input = jar.getInputStream(jar.getJarEntry(path));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
