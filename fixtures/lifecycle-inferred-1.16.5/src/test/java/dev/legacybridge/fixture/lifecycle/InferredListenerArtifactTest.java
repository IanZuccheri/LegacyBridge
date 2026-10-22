package dev.legacybridge.fixture.lifecycle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads inert constant-pool bytes only; native generic inference belongs to the separate runtime gate. */
final class InferredListenerArtifactTest {
    @Test
    void genuineVariantUsesConsumerOverloadWithoutTheExplicitEventClassToken() throws IOException {
        String path = System.getProperty("fixture.jar");
        assertNotNull(path);
        try (JarFile jar = new JarFile(path)) {
            JarEntry entry = jar.getJarEntry("dev/legacybridge/fixture/lifecycle/LifecycleContent.class");
            assertNotNull(entry);
            assertTrue(entry.getSize() >= 0 && entry.getSize() <= 65536);
            try (InputStream input = jar.getInputStream(entry);
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                    assertTrue(bytes.size() <= 65536);
                }
                String pool = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                assertTrue(pool.contains("(Ljava/util/function/Consumer;)V"));
                assertFalse(pool.contains("(Lnet/minecraftforge/eventbus/api/EventPriority;ZLjava/lang/Class;Ljava/util/function/Consumer;)V"));
                assertTrue(pool.contains("net/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent"));
            }
        }
    }
}
