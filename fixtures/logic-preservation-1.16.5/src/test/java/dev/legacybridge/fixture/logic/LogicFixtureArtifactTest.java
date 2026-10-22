package dev.legacybridge.fixture.logic;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads the production-SRG archive as data. No original fixture class is referenced or loaded. */
final class LogicFixtureArtifactTest {
    @Test
    void metadataPinsRealForgeSource() throws IOException {
        try (JarFile jar = input()) {
            String metadata = text(jar, "META-INF/mods.toml");
            assertTrue(metadata.contains("modId=\"legacybridge_logic\""));
            assertTrue(metadata.contains("version=\"1.0.0\""));
            assertTrue(metadata.contains("versionRange=\"[36.2.42,37)\""));
            assertTrue(metadata.contains("versionRange=\"[1.16.5,1.17)\""));
            assertEquals("1.0.0", jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
        }
    }

    @Test
    void allSixOriginalClassesAreJava8AndNoPlatformClassesAreBundled() throws IOException {
        Set<String> expected = new HashSet<String>(Arrays.asList(
                "LegacyBridgeLogic", "LogicItem", "ScoreRule", "BaseRule", "ParityRule", "LogicMath"));
        try (JarFile jar = input()) {
            Enumeration<JarEntry> entries = jar.entries();
            int classes = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class")) continue;
                assertTrue(name.startsWith("dev/legacybridge/fixture/logic/"), name);
                assertTrue(expected.remove(name.substring(name.lastIndexOf('/') + 1, name.length() - 6)), name);
                try (DataInputStream bytes = new DataInputStream(jar.getInputStream(entry))) {
                    assertEquals(0xcafebabe, bytes.readInt());
                    assertEquals(0, bytes.readUnsignedShort());
                    assertEquals(52, bytes.readUnsignedShort());
                }
                classes++;
            }
            assertEquals(6, classes);
            assertTrue(expected.isEmpty());
        }
    }

    @Test
    void modelReferencesVanillaTextureWithoutCopyingMinecraftAssets() throws IOException {
        try (JarFile jar = input()) {
            String model = text(jar, "assets/legacybridge_logic/models/item/parity_token.json");
            assertTrue(model.contains("minecraft:item/generated"));
            assertTrue(model.contains("minecraft:item/iron_ingot"));
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertFalse(name.startsWith("assets/minecraft/"), name);
                assertFalse(name.endsWith(".png"), name);
            }
        }
    }

    @Test
    void retainsBothLocalizationsAndNoExecutableHarness() throws IOException {
        try (JarFile jar = input()) {
            assertTrue(text(jar, "assets/legacybridge_logic/lang/en_us.json").contains("Parity Token"));
            assertTrue(text(jar, "assets/legacybridge_logic/lang/pt_br.json").contains("Ficha de Paridade"));
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertFalse(name.startsWith("dev/legacybridge/validation/"), name);
                assertFalse(name.startsWith("META-INF/services/"), name);
                assertFalse(name.endsWith(".jar"), name);
            }
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
        try (InputStream input = jar.getInputStream(entry);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
