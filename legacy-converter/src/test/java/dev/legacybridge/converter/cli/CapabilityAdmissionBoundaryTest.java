package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.service.ConversionProfile;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** A draft profile must not silently route to a different implemented converter.
 * Replace these refusals only alongside its exact recipes, fixtures and runtime evidence. */
class CapabilityAdmissionBoundaryTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"energy-item-v1", "inventory-capability-v1", "fluid-capability-v1", "player-capability-v1"})
    void unimplementedCapabilityProfileCannotFallBackOrPublish(String profile) throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ConversionProfile.parse(profile));
        Path original = temporary.resolve("original.jar"), output = temporary.resolve("converted.jar");
        byte[] inert = {0, 1, 2, 3};
        Files.write(original, inert);
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        int exit = LegacyBridgeCli.run(new String[]{"convert", original.toString(), "--output", output.toString(),
                "--conversion-profile", profile}, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), Map.of());
        assertEquals(2, exit, out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Unknown conversion profile"));
        assertFalse(Files.exists(output));
        assertArrayEquals(inert, Files.readAllBytes(original));
        try (var paths = Files.list(temporary)) { assertEquals(1, paths.count()); }
    }

    @Test void catalogDoesNotAdvertiseAnUnimplementedProviderOrFixture() {
        assertTrue(AdapterCatalog.unsupportedAreas().contains("capability"));
        assertTrue(AdapterCatalog.all().stream().noneMatch(a -> a.id().startsWith("capability.")));
        assertTrue(AdapterCatalog.all().stream().flatMap(a -> a.fixtures().stream())
                .noneMatch(path -> path.equals("fixtures/energy-item-1.16.5")));
    }
}
