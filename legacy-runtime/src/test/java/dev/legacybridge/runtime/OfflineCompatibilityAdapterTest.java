package dev.legacybridge.runtime;

import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.UnsupportedCompatibilityException;
import dev.legacybridge.runtime.compat.client.ResourcePackAdapter;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import dev.legacybridge.runtime.compat.world.BlockMiningAdapter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

/** Offline adapters operate on data, never loading Minecraft or executing input mod classes. */
class OfflineCompatibilityAdapterTest {
    @Test
    void onlyTheAuditedSourceBaselineProducesVersionedTargetDependencies() {
        var ranges = ModMetadataAdapter.targetRanges("1.16.5", "36.2.42");
        assertEquals(Map.of("loaderVersion", "[47,48)", "forge", "[47.4.23,48)",
                "minecraft", "[1.20.1,1.20.2)", "legacybridge_runtime", "[" + AdapterCatalog.RUNTIME_VERSION + ",0.2.0)"), ranges);
        assertThrows(UnsupportedOperationException.class, () -> ranges.put("minecraft", "*"));
        assertThrows(UnsupportedCompatibilityException.class, () -> ModMetadataAdapter.targetRanges("1.16.4", "36.2.42"));
        assertThrows(UnsupportedCompatibilityException.class, () -> ModMetadataAdapter.targetRanges("1.16.5", "36.2.34"));
        assertThrows(UnsupportedCompatibilityException.class, () -> ModMetadataAdapter.targetRanges(null, "36.2.42"));
    }

    @Test
    void manifestPreservesTheVersionWithValidLongLineFolding() throws IOException {
        String version = "1.0.0-" + "fixture".repeat(16);
        byte[] bytes = ModMetadataAdapter.targetManifest(version);
        var manifest = new Manifest(new ByteArrayInputStream(bytes));
        assertEquals("1.0", manifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION));
        assertEquals(version, manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION));
        assertEquals("simple-content-v1", manifest.getMainAttributes().getValue("LegacyBridge-Profile"));
        assertEquals(Integer.toString(AdapterCatalog.SCHEMA_VERSION), manifest.getMainAttributes().getValue("LegacyBridge-Adapter-Catalog"));
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(text.contains("\r\n "), "Long manifest values must use continuation lines");
        for (String line : text.split("\r\n")) assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 72);
        assertArrayEquals(bytes, ModMetadataAdapter.targetManifest(version));
    }

    @Test
    void manifestRejectsHeaderInjectionInvalidVersionsAndOversizedValues() {
        for (String value : List.of("", " ", "1.0.0\r\nMain-Class: malicious.Entry", "x".repeat(129), "1.0.0/other")) {
            assertThrows(UnsupportedCompatibilityException.class, () -> ModMetadataAdapter.targetManifest(value));
        }
        assertThrows(UnsupportedCompatibilityException.class, () -> ModMetadataAdapter.targetManifest(null));
    }

    @Test
    void packFormatConversionRefusesAnyUnverifiedSourceFormat() {
        assertEquals(15, ResourcePackAdapter.targetFormat(6));
        for (int value : new int[]{-1, 0, 5, 7, 15, Integer.MAX_VALUE}) {
            assertThrows(UnsupportedCompatibilityException.class, () -> ResourcePackAdapter.targetFormat(value));
        }
    }

    @Test
    void stoneMiningProducesDeterministicAdditiveTargetTagWithoutChangingLocalNames() {
        var result = BlockMiningAdapter.targetTag("legacybridge_example", List.of("z_stone", "bridge_block"));
        assertEquals("data/minecraft/tags/blocks/mineable/pickaxe.json", BlockMiningAdapter.TARGET_PATH);
        assertEquals(Boolean.FALSE, result.get("replace"));
        assertEquals(List.of("legacybridge_example:bridge_block", "legacybridge_example:z_stone"), result.get("values"));
        assertThrows(UnsupportedOperationException.class, () -> result.put("replace", true));
        assertEquals(List.of(), BlockMiningAdapter.targetTag("legacybridge_example", List.of()).get("values"));
    }

    @Test
    void stoneMiningRejectsForeignPathsDuplicatesAndDeclarationBoundViolations() {
        for (List<String> names : List.of(List.of("minecraft:stone"), List.of("../stone"), List.of("/stone"),
                List.of("bridge_block", "bridge_block"), Collections.nCopies(1025, "bridge_block"))) {
            assertThrows(UnsupportedCompatibilityException.class, () -> BlockMiningAdapter.targetTag("legacybridge_example", names));
        }
        assertThrows(UnsupportedCompatibilityException.class, () -> BlockMiningAdapter.targetTag("Bad Namespace", List.of("bridge_block")));
        assertThrows(UnsupportedCompatibilityException.class, () -> BlockMiningAdapter.targetTag(null, List.of("bridge_block")));
        assertThrows(UnsupportedCompatibilityException.class, () -> BlockMiningAdapter.targetTag("legacybridge_example", null));
    }
}
