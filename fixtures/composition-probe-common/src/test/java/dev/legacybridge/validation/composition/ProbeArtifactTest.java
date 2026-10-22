package dev.legacybridge.validation.composition;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads the compiled QA archive as ZIP bytes only; never resolves any native or fixture class. */
final class ProbeArtifactTest {
    private static final String PREFIX = "dev/legacybridge/validation/composition/";
    private static final Set<String> ROOT_CLASSES = new HashSet<String>(Arrays.asList(
            "CompositionRuntimeProbe", "BehaviorCapture", "ProbeSession", "ProbeFiles", "ProbeJson"));

    @Test void propertiesSelectOnlyTheTwoReviewedExactBaselines() {
        String minecraft = required("probe.minecraft"), forge = required("probe.forge");
        String major = required("probe.expectedMajor");
        if ("1.16.5".equals(minecraft)) {
            assertEquals("36.2.42", forge);
            assertEquals("52", major);
        } else {
            assertEquals("1.20.1", minecraft);
            assertEquals("47.4.23", forge);
            assertEquals("61", major);
        }
    }

    @Test void finalArchiveContainsOnlyOwnedProbeClassesAndTheTwoExpectedMetadataFiles() throws IOException {
        try (JarFile jar = archive()) {
            Set<String> names = new HashSet<String>();
            int classes = 0;
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                assertTrue(names.add(name), "Duplicate archive entry: " + name);
                assertFalse(name.contains("\\") || name.startsWith("/") || name.contains(".."), name);
                if (entry.isDirectory()) {
                    assertTrue("META-INF/".equals(name) || PREFIX.startsWith(name), "Unexpected directory: " + name);
                    continue;
                }
                assertTrue(entry.getSize() >= 0 && entry.getSize() <= 1024 * 1024, "Unbounded entry: " + name);
                if ("META-INF/MANIFEST.MF".equals(name) || "META-INF/mods.toml".equals(name)) continue;
                assertTrue(ownedClass(name), "Unowned class/resource/library/service is packaged: " + name);
                classes++;
            }
            assertTrue(classes >= ROOT_CLASSES.size());
            for (String root : ROOT_CLASSES) assertTrue(names.contains(PREFIX + root + ".class"), root);
            assertTrue(names.contains("META-INF/MANIFEST.MF"));
            assertTrue(names.contains("META-INF/mods.toml"));
        }
    }

    @Test void everyPackagedClassUsesSelectedMajorWithoutPreviewBytecode() throws IOException {
        int expectedMajor = Integer.parseInt(required("probe.expectedMajor"));
        assertTrue(expectedMajor == 52 || expectedMajor == 61);
        try (JarFile jar = archive()) {
            int inspected = 0;
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                assertTrue(ownedClass(entry.getName()), entry.getName());
                try (DataInputStream input = new DataInputStream(jar.getInputStream(entry))) {
                    assertEquals(0xCAFEBABE, input.readInt(), entry.getName());
                    assertEquals(0, input.readUnsignedShort(), "Preview/minor bytecode: " + entry.getName());
                    assertEquals(expectedMajor, input.readUnsignedShort(), entry.getName());
                }
                inspected++;
            }
            assertTrue(inspected >= ROOT_CLASSES.size());
        }
    }

    @Test void metadataExactlyPinsProbeFixtureAndVersionSpecificPlatformDependencies() throws IOException {
        String minecraft = required("probe.minecraft"), forge = required("probe.forge");
        String expected = "modLoader=\"javafml\"\nloaderVersion=\"[" + forge + "]\"\nlicense=\"All Rights Reserved\"\n\n"
                + "[[mods]]\nmodId=\"legacybridge_composition_probe\"\nversion=\"1.0.0\"\n"
                + "displayName=\"LegacyBridge Composition QA " + minecraft + "\"\nauthors=\"LegacyBridge contributors\"\n"
                + "description='''Developer-only disposable dedicated-server capture. Never install in a player profile. Native QA requires separate authorization.'''\n\n"
                + dependency("forge", forge, "NONE") + "\n"
                + dependency("minecraft", minecraft, "NONE") + "\n"
                + dependency("legacybridge_composed", "1.0.0", "AFTER");
        if ("1.20.1".equals(minecraft)) expected += "\n" + dependency("legacybridge_runtime", "0.1.1-SNAPSHOT", "AFTER");
        try (JarFile jar = archive()) {
            JarEntry metadata = jar.getJarEntry("META-INF/mods.toml");
            assertNotNull(metadata);
            // Exact finite resource comparison, not a permissive TOML parser or inferred contract.
            assertEquals(expected.trim(), utf8(jar, metadata).replace("\r\n", "\n").trim());
        }
    }

    @Test void manifestIdentifiesQAArtifactWithoutExecutableBootstrapOrClasspath() throws IOException {
        try (JarFile jar = archive()) {
            Manifest manifest = jar.getManifest();
            assertNotNull(manifest);
            Attributes values = manifest.getMainAttributes();
            assertEquals("1.0", values.getValue("Manifest-Version"));
            assertEquals("LegacyBridge Composition QA " + required("probe.minecraft"), values.getValue("Implementation-Title"));
            assertEquals("1.0.0", values.getValue("Implementation-Version"));
            assertEquals("LegacyBridge contributors", values.getValue("Implementation-Vendor"));
            for (String forbidden : Arrays.asList("Main-Class", "Class-Path", "Premain-Class", "Agent-Class",
                    "Launcher-Agent-Class", "MixinConfigs", "FMLCorePlugin", "FMLAT", "Multi-Release")) {
                assertNull(values.getValue(forbidden), "Unexpected executable/platform metadata: " + forbidden);
            }
        }
    }

    @Test void archiveAllowlistRejectsPlatformFixturesLibrariesTestsAndServices() {
        assertTrue(ownedClass(PREFIX + "CompositionRuntimeProbe.class"));
        assertTrue(ownedClass(PREFIX + "CompositionRuntimeProbe$NativePlatform$1$1.class"));
        assertTrue(ownedClass(PREFIX + "ProbeFiles$Writer.class"));
        for (String forbidden : Arrays.asList(
                "net/minecraft/world/item/Item.class", "net/minecraftforge/fml/ModList.class",
                "dev/legacybridge/fixture/composed/ComposedItem.class", "dev/legacybridge/converter/Engine.class",
                "org/objectweb/asm/ClassReader.class", "org/slf4j/Logger.class", "java/lang/Object.class",
                "META-INF/services/cpw.mods.modlauncher.api.ITransformationService", "META-INF/jarjar/engine.jar",
                PREFIX + "BehaviorCaptureTest.class", PREFIX + "ProbeArtifactTest.class",
                PREFIX + "ProbeSession.class/escape.class", PREFIX + "ProbeSession$.class",
                PREFIX + "UnreviewedAdapter.class", PREFIX + "nested/ProbeSession.class")) {
            assertFalse(ownedClass(forbidden), forbidden);
        }
    }

    private static boolean ownedClass(String name) {
        if (!name.startsWith(PREFIX) || !name.endsWith(".class")) return false;
        String simple = name.substring(PREFIX.length(), name.length() - ".class".length());
        if (!simple.matches("[A-Za-z][A-Za-z0-9]*(\\$[A-Za-z0-9_]+)*")) return false;
        int inner = simple.indexOf('$');
        return ROOT_CLASSES.contains(inner < 0 ? simple : simple.substring(0, inner));
    }
    private static String dependency(String id, String version, String ordering) {
        return "[[dependencies.legacybridge_composition_probe]]\nmodId=\"" + id + "\"\nmandatory=true\n"
                + "versionRange=\"[" + version + "]\"\nordering=\"" + ordering + "\"\nside=\"BOTH\"\n";
    }
    private static String required(String key) {
        String value = System.getProperty(key);
        assertNotNull(value, "Build must provide exact property: " + key);
        assertFalse(value.isEmpty(), key);
        return value;
    }
    private static JarFile archive() throws IOException {
        Path path = Paths.get(required("probe.jar"));
        assertTrue(Files.isRegularFile(path), "Expected completed probe archive");
        assertTrue(Files.size(path) > 0 && Files.size(path) <= 2 * 1024 * 1024, "Probe archive exceeded QA-only bounds");
        return new JarFile(path.toFile());
    }
    private static String utf8(JarFile jar, JarEntry entry) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = jar.getInputStream(entry)) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > 16 * 1024) throw new IOException("Unbounded probe metadata");
                bytes.write(buffer, 0, count);
            }
        }
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
    }
}
