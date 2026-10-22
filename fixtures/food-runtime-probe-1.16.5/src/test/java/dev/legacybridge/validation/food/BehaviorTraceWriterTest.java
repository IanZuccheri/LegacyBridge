package dev.legacybridge.validation.food;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests only the neutral writer and archive boundaries; no game or original mod class is loaded. */
final class BehaviorTraceWriterTest {
    @TempDir Path temporary;

    @Test
    void serializesOnlyDeterministicPrimitiveObservations() {
        LinkedHashMap<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("schemaVersion", 1);
        values.put("item", "legacybridge_food:ration");
        values.put("foodLevels", new int[]{10, 13});
        values.put("removedDrops", new boolean[]{true, true});
        assertEquals("{\"schemaVersion\":1,\"item\":\"legacybridge_food:ration\",\"foodLevels\":[10,13],\"removedDrops\":[true,true]}\n",
                BehaviorTraceWriter.render(values));
    }

    @Test
    void refusesUnsafeOrUnsupportedTraceValues() {
        LinkedHashMap<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("text", "bad\\value");
        assertThrows(IllegalArgumentException.class, () -> BehaviorTraceWriter.render(values));
        values.put("text", new Object());
        assertThrows(IllegalArgumentException.class, () -> BehaviorTraceWriter.render(values));
    }

    @Test
    void createsTraceWithoutOverwritingAnExistingResult() throws IOException {
        Path target = temporary.resolve("food-trace.json");
        BehaviorTraceWriter.writeNew(target, "{\"schemaVersion\":1}\n");
        assertThrows(IOException.class, () -> BehaviorTraceWriter.writeNew(target, "replacement"));
        assertEquals("{\"schemaVersion\":1}\n",
                new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
    }

    @Test
    void probeArtifactDoesNotEmbedFixtureOrPlatformClasses() throws IOException {
        String path = System.getProperty("probe.jar");
        assertNotNull(path);
        try (JarFile jar = new JarFile(path)) {
            assertNotNull(jar.getJarEntry("dev/legacybridge/validation/food/FoodRuntimeProbe.class"));
            assertNotNull(jar.getJarEntry("dev/legacybridge/validation/food/BehaviorTraceWriter.class"));
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                assertFalse(name.startsWith("dev/legacybridge/fixture/"), name);
                assertFalse(name.startsWith("net/minecraft/"), name);
                assertFalse(name.startsWith("net/minecraftforge/"), name);
                assertFalse(name.endsWith(".jar"), name);
            }
        }
    }
}
