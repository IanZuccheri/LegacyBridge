package dev.legacybridge.fixture.lifecycle;

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

/** Archive-only checks: do not reference, load, initialize, or invoke the original mod classes. */
final class LifecycleFixtureArtifactTest {
    @Test
    void pinsIdentityAndTheGenuineForgeSourceVersions() throws IOException {
        try (JarFile jar = input()) {
            String metadata = text(jar, "META-INF/mods.toml");
            assertTrue(metadata.contains("modId=\"legacybridge_lifecycle\""));
            assertTrue(metadata.contains("version=\"1.0.0\""));
            assertTrue(metadata.contains("versionRange=\"[36.2.42,37)\""));
            assertTrue(metadata.contains("versionRange=\"[1.16.5,1.17)\""));
            assertEquals("1.0.0", jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
            assertEquals("lifecycle-content-v1", jar.getManifest().getMainAttributes().getValue("LegacyBridge-Fixture"));
        }
    }

    @Test
    void containsExactlyFourOwnedJava8ClassesAndNoPlatformBytecode() throws IOException {
        Set<String> expected = new HashSet<String>(Arrays.asList(
                "LifecycleContent", "LifecycleItem", "LifecycleConfig", "LifecycleCommands"));
        try (JarFile jar = input()) {
            Enumeration<JarEntry> entries = jar.entries();
            int classes = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class")) continue;
                assertTrue(name.startsWith("dev/legacybridge/fixture/lifecycle/"), name);
                assertTrue(expected.remove(name.substring(name.lastIndexOf('/') + 1, name.length() - 6)), name);
                try (DataInputStream bytes = new DataInputStream(jar.getInputStream(entry))) {
                    assertEquals(0xcafebabe, bytes.readInt());
                    assertEquals(0, bytes.readUnsignedShort());
                    assertEquals(52, bytes.readUnsignedShort());
                }
                classes++;
            }
            assertEquals(4, classes);
            assertTrue(expected.isEmpty());
        }
    }

    @Test
    void containsTheNativeLifecycleConfigAndCommandReferencesAsData() throws IOException {
        try (JarFile jar = input()) {
            String entry = text(jar, "dev/legacybridge/fixture/lifecycle/LifecycleContent.class");
            assertTrue(entry.contains("net/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent"));
            assertTrue(entry.contains("net/minecraftforge/fml/ModLoadingContext"));
            assertTrue(entry.contains("java/lang/Runnable"));
            String config = text(jar, "dev/legacybridge/fixture/lifecycle/LifecycleConfig.class");
            assertTrue(config.contains("net/minecraftforge/common/ForgeConfigSpec$Builder"));
            assertTrue(config.contains("defineInRange"));
            String commands = text(jar, "dev/legacybridge/fixture/lifecycle/LifecycleCommands.class");
            assertTrue(commands.contains("net/minecraftforge/event/RegisterCommandsEvent"));
            assertTrue(commands.contains("net/minecraftforge/eventbus/api/SubscribeEvent"));
            assertTrue(commands.contains("com/mojang/brigadier/arguments/IntegerArgumentType"));
            assertTrue(commands.contains("bridgeprobe"));
            // These source-bound smoke checks do not replace ASM/linkage or runtime validation.
            for (String bytes : Arrays.asList(entry, config, commands)) {
                assertFalse(bytes.contains("java/io/"));
                assertFalse(bytes.contains("java/nio/"));
                assertFalse(bytes.contains("java/lang/reflect/"));
                assertFalse(bytes.contains("java/lang/System"));
            }
        }
    }

    @Test
    void keepsOriginalResourcesWithoutCopyingVanillaTextureBytes() throws IOException {
        try (JarFile jar = input()) {
            String model = text(jar, "assets/legacybridge_lifecycle/models/item/lifecycle_token.json");
            assertTrue(model.contains("minecraft:item/generated"));
            assertTrue(model.contains("minecraft:item/iron_ingot"));
            assertTrue(text(jar, "assets/legacybridge_lifecycle/lang/en_us.json").contains("Lifecycle Token"));
            assertTrue(text(jar, "assets/legacybridge_lifecycle/lang/pt_br.json").contains("Ficha de Ciclo de Vida"));
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertFalse(name.startsWith("assets/minecraft/"), name);
                assertFalse(name.endsWith(".png"), name);
            }
        }
    }

    @Test
    void doesNotBundleAProbeGameSourcesThirdPartyLibrariesOrCustomBootstrap() throws IOException {
        try (JarFile jar = input()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertFalse(name.startsWith("dev/legacybridge/validation/"), name);
                assertFalse(name.startsWith("META-INF/services/"), name);
                assertFalse(name.startsWith("net/minecraft"), name);
                assertFalse(name.startsWith("com/mojang/"), name);
                assertFalse(name.endsWith(".java"), name);
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
        assertTrue(entry.getSize() >= 0 && entry.getSize() <= 65536, name);
        try (InputStream input = jar.getInputStream(entry);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
                assertTrue(bytes.size() <= 65536, name);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
