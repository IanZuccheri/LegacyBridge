package dev.legacybridge.converter.testutil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class TestJarBuilder {
    private TestJarBuilder() {
    }

    public static Path write(Path target, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(target.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(target))) {
            for (Map.Entry<String, byte[]> entry : new TreeMap<>(entries).entrySet()) {
                JarEntry jarEntry = new JarEntry(entry.getKey());
                jarEntry.setTime(0L);
                output.putNextEntry(jarEntry);
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return target;
    }
}

