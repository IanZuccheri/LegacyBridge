package dev.legacybridge.runtime;

import dev.legacybridge.runtime.compat.AdapterCatalog;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagedCapabilityCatalogTest {
    @Test
    void packagedCatalogTruthfullyDeclaresSimpleContentWithoutTransformation() throws IOException {
        InputStream resource = RuntimeCapabilityCatalog.class.getClassLoader()
                .getResourceAsStream(RuntimeCapabilityCatalog.RESOURCE_PATH);
        assertNotNull(resource, "runtime capability catalog must be on the runtime classpath");

        final String compactJson;
        try (resource) {
            compactJson = new String(resource.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", "");
        }

        assertFalse(compactJson.contains("${"), "processed catalog must not contain Gradle placeholders");
        assertTrue(compactJson.contains("\"schemaVersion\":" + RuntimeCapabilityCatalog.SCHEMA_VERSION));
        assertTrue(compactJson.contains("\"runtimeModId\":\"legacybridge_runtime\""));
        assertTrue(compactJson.contains("\"runtimeVersion\":\"" + AdapterCatalog.RUNTIME_VERSION + "\""));
        assertTrue(compactJson.contains("\"adapterCatalog\":\"META-INF/legacybridge/adapters.json\""));
        assertTrue(compactJson.contains("\"minecraftVersion\":\"1.20.1\""));
        assertTrue(compactJson.contains("\"forgeVersion\":\"47.4.23\""));
        assertTrue(compactJson.contains("\"transformationsEnabled\":false"));
        assertTrue(compactJson.contains("\"legacyApiShims\":[\"simple-content-v1\"]"));
        assertTrue(compactJson.contains("\"bytecodeTransformations\":[]"));
    }
}
