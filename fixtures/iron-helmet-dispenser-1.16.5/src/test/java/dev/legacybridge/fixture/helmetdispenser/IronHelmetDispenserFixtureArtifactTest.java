package dev.legacybridge.fixture.helmetdispenser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Reads the JAR and test declarations as data; never loads the entrypoint or game classes. */
final class IronHelmetDispenserFixtureArtifactTest {
    private static final String OWNER = "dev/legacybridge/fixture/helmetdispenser/IronHelmetDispenserContent";
    private static final String PREFIX = "assets/legacybridge_helmet_dispenser/";

    @Test void metadataPinsOriginalLoaderAndSeparateFixtureIdentity() throws IOException {
        try (JarFile jar = input()) {
            String metadata = text(jar, "META-INF/mods.toml");
            assertTrue(metadata.contains("modId=\"legacybridge_helmet_dispenser\""));
            assertTrue(metadata.contains("version=\"1.0.0\""));
            assertTrue(metadata.contains("versionRange=\"[36.2.42,37)\""));
            assertTrue(metadata.contains("versionRange=\"[1.16.5,1.17)\""));
            assertTrue(text(jar, "pack.mcmeta").contains("\"pack_format\": 6"));
            assertEquals("1.0.0", jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
            assertEquals("iron-helmet-dispenser-v1", jar.getManifest().getMainAttributes().getValue("LegacyBridge-Fixture"));
            assertFalse(metadata.contains("legacybridge_runtime"));
        }
    }

    @Test void onlyOwnedJava8ClassIsBundledWithoutScenariosHarnessOrLibraries() throws IOException {
        try (JarFile jar = input()) {
            int classes = 0;
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertFalse(name.startsWith("META-INF/services/"), name);
                assertFalse(name.endsWith(".jar"), name);
                assertFalse(name.contains("scenarios"), name);
                if (!name.endsWith(".class")) continue;
                assertEquals(OWNER + ".class", name);
                ClassNode node = readClass(jar);
                assertEquals(V1_8, node.version);
                assertEquals(ACC_PUBLIC | ACC_FINAL | ACC_SUPER, node.access);
                assertEquals("java/lang/Object", node.superName);
                assertTrue(node.interfaces.isEmpty());
                assertEquals(3, node.fields.size());
                assertEquals(4, node.methods.size());
                classes++;
            }
            assertEquals(1, classes);
        }
    }

    @Test void helperIsExactlyTheNativeStaticDispenseArmorCallWithReceivedArguments() throws IOException {
        try (JarFile jar = input()) {
            List<MethodNode> matches = new ArrayList<MethodNode>();
            for (MethodNode method : readClass(jar).methods) if (method.name.equals("dispense")) matches.add(method);
            assertEquals(1, matches.size());
            MethodNode method = matches.get(0);
            String descriptor = "(Lnet/minecraft/dispenser/IBlockSource;Lnet/minecraft/item/ItemStack;)Z";
            assertEquals(descriptor, method.desc);
            assertEquals(ACC_PUBLIC | ACC_STATIC, method.access);
            assertTrue(method.tryCatchBlocks.isEmpty());
            List<AbstractInsnNode> code = new ArrayList<AbstractInsnNode>();
            List<Integer> opcodes = new ArrayList<Integer>();
            for (AbstractInsnNode node : method.instructions.toArray()) {
                if (node.getOpcode() < 0) continue;
                code.add(node); opcodes.add(node.getOpcode());
            }
            assertEquals(Arrays.asList(ALOAD, ALOAD, INVOKESTATIC, IRETURN), opcodes);
            assertEquals(0, ((VarInsnNode) code.get(0)).var);
            assertEquals(1, ((VarInsnNode) code.get(1)).var);
            MethodInsnNode call = (MethodInsnNode) code.get(2);
            assertEquals("net/minecraft/item/ArmorItem", call.owner);
            assertEquals("func_226626_a_", call.name);
            assertEquals(descriptor, call.desc);
            assertFalse(call.itf);
        }
    }

    @Test void authoredRecipeModelAndTranslationsDoNotBundleVanillaAssets() throws IOException {
        try (JarFile jar = input()) {
            String recipe = text(jar, "data/legacybridge_helmet_dispenser/recipes/bridge_helmet.json");
            assertTrue(recipe.contains("minecraft:crafting_shapeless"));
            assertTrue(recipe.contains("minecraft:iron_ingot"));
            assertTrue(recipe.contains("legacybridge_helmet_dispenser:bridge_helmet"));
            String model = text(jar, PREFIX + "models/item/bridge_helmet.json");
            assertTrue(model.contains("minecraft:item/generated"));
            assertTrue(model.contains("minecraft:item/iron_helmet"));
            assertTrue(text(jar, PREFIX + "lang/en_us.json").contains("Bridge Dispenser Helmet"));
            assertTrue(text(jar, PREFIX + "lang/pt_br.json").contains("Capacete de ponte do ejetor"));
            Enumeration<JarEntry> entries = jar.entries();
            int recipes = 0, models = 0;
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertFalse(name.startsWith("assets/minecraft/") || name.startsWith("data/minecraft/"), name);
                assertFalse(name.endsWith(".png") || name.contains("/blockstates/") || name.contains("/loot_tables/"), name);
                if (name.startsWith("data/legacybridge_helmet_dispenser/recipes/") && name.endsWith(".json")) recipes++;
                if (name.startsWith(PREFIX + "models/") && name.endsWith(".json")) models++;
            }
            assertEquals(1, recipes); assertEquals(1, models);
        }
    }

    @Test void scenarioDeclarationsStayExplicitlyUnexecutedAndKeepUnsupportedDistinctFromNoResult() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/dispenser-scenarios.json")) {
            assertNotNull(input);
            String scenarios = new String(bounded(input, 16_384), StandardCharsets.UTF_8);
            assertTrue(scenarios.contains("\"evidenceKind\": \"AUTHORED_SCENARIO_DECLARATIONS_ONLY\""));
            for (String dimension : Arrays.asList("nativeClient", "nativeServer", "nativeBehavior")) {
                assertTrue(scenarios.contains("\"" + dimension + "\": \"NOT_RUN\""));
            }
            for (String id : Arrays.asList("native-player-first", "exact-zombie-first", "no-result",
                    "spectator-no-result", "dead-no-result", "zombie-loot-disabled-no-result", "unsupported-first-before-known")) {
                assertEquals(1, occurrences(scenarios, "\"id\": \"" + id + "\""));
            }
            assertEquals(7, occurrences(scenarios, "\"observed\": \"NOT_RUN\""));
            assertTrue(scenarios.contains("FALSE_NO_ELIGIBLE_TARGET"));
            assertTrue(scenarios.contains("UNSUPPORTED_BEFORE_SPLIT_NOT_FALSE_AND_NOT_SELECT_SECOND"));
            assertTrue(scenarios.contains("zombie canPickUpLoot=false"));
            assertTrue(scenarios.contains("zombie canPickUpLoot=true"));
        }
    }

    private static int occurrences(String text, String token) {
        int count = 0, at = 0;
        while ((at = text.indexOf(token, at)) >= 0) { count++; at += token.length(); }
        return count;
    }

    private static JarFile input() throws IOException {
        String path = System.getProperty("fixture.jar");
        assertNotNull(path, "The standalone task supplies the reobfuscated original path");
        return new JarFile(path);
    }

    private static ClassNode readClass(JarFile jar) throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(bytes(jar, OWNER + ".class")).accept(node, 0);
        return node;
    }

    private static String text(JarFile jar, String name) throws IOException {
        return new String(bytes(jar, name), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(JarFile jar, String name) throws IOException {
        JarEntry entry = jar.getJarEntry(name);
        assertNotNull(entry, name);
        assertTrue(entry.getSize() >= 0 && entry.getSize() <= 16_384, name);
        try (InputStream input = jar.getInputStream(entry)) { return bounded(input, 16_384); }
    }

    private static byte[] bounded(InputStream input, int limit) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                assertTrue(bytes.size() + count <= limit, "Authored resource exceeded bounded read");
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        }
    }
}
