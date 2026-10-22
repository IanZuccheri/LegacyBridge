package dev.legacybridge.validation.network;

import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure inventory and inert archive tests; no game or original class is loaded. */
final class HandshakeScenarioTest {
    @Test void matchingRequiresTheRealFixtureOnBothSides() {
        HandshakeScenario mode = HandshakeScenario.parse("MATCHED");
        assertTrue(mode.fixturePresent("a")); assertTrue(mode.fixturePresent("server"));
        assertFalse(mode.emulateProtocol("a")); assertFalse(mode.emulateProtocol("server"));
        assertEquals("NONE", mode.rejectingRole());
    }
    @Test void absentClientLeavesOriginalValidatorOnServer() {
        assertInventory(HandshakeScenario.CLIENT_ABSENT, true, false, false, false, "server");
    }
    @Test void divergentClientDoesNotMutateServerValidator() {
        assertInventory(HandshakeScenario.CLIENT_PROTOCOL, true, false, false, true, "server");
    }
    @Test void absentServerLeavesOriginalValidatorOnClient() {
        assertInventory(HandshakeScenario.SERVER_ABSENT, false, true, false, false, "a");
    }
    @Test void divergentServerDoesNotMutateClientValidator() {
        assertInventory(HandshakeScenario.SERVER_PROTOCOL, false, true, true, false, "a");
    }
    @Test void unknownScenarioAndRolesRefuse() {
        for (String value : new String[]{null, "", "matched", "MATCHED ", "UNIVERSAL", "CLIENT_MISSING"}) assertThrows(IllegalArgumentException.class, () -> HandshakeScenario.parse(value));
        for (String role : new String[]{null, "", "b", "client", "SERVER"}) assertThrows(IllegalArgumentException.class, () -> HandshakeScenario.MATCHED.fixturePresent(role));
        assertEquals(6, HandshakeScenario.values().length);
    }
    @Test void platformBaselineCannotContainProductFixtureOrUnknownMod() {
        var mode = HandshakeScenario.PLATFORM_CLIENT_ABSENT;
        var expected = Set.of("minecraft", "forge", "legacybridge_handshake_probe");
        assertTrue(mode.platformBaseline());
        assertInventory(mode, false, false, false, false, "server");
        for (String role : Set.of("a", "server")) {
            assertDoesNotThrow(() -> mode.validateInventory(expected, role));
            for (String extra : Set.of("legacybridge_runtime", "legacybridge_network", "unknown")) {
                var changed = new java.util.HashSet<>(expected); changed.add(extra);
                assertThrows(IllegalStateException.class, () -> mode.validateInventory(changed, role));
            }
            assertThrows(IllegalStateException.class, () -> mode.validateInventory(Set.of("minecraft", "forge"), role));
        }
    }
    @Test void normalCasesStillRequireRuntimeAndTheirExactFixtureInventory() {
        for (var mode : HandshakeScenario.values()) {
            if (mode.platformBaseline()) continue;
            for (String role : Set.of("a", "server")) {
                var ids = new java.util.HashSet<>(Set.of("minecraft", "forge", "legacybridge_handshake_probe", "legacybridge_runtime"));
                if (mode.fixturePresent(role)) ids.add("legacybridge_network");
                assertDoesNotThrow(() -> mode.validateInventory(ids, role));
                ids.remove("legacybridge_runtime");
                assertThrows(IllegalStateException.class, () -> mode.validateInventory(ids, role));
                ids.add("legacybridge_runtime");
                if (!ids.remove("legacybridge_network")) ids.add("legacybridge_network");
                assertThrows(IllegalStateException.class, () -> mode.validateInventory(ids, role));
            }
        }
    }
    @Test void standaloneQaArtifactDoesNotRequireMissingFixtureOrEmbedProduct() throws Exception {
        try (ZipFile jar = new ZipFile(Path.of(System.getProperty("probe.jar")).toFile())) {
            String metadata = new String(jar.getInputStream(jar.getEntry("META-INF/mods.toml")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(metadata.contains("modId=\"legacybridge_handshake_probe\""));
            assertFalse(metadata.contains("modId=\"legacybridge_network\""));
            assertTrue(metadata.substring(metadata.indexOf("modId=\"legacybridge_runtime\""))
                    .contains("mandatory=false")); // QA-only; ordinary scenarios enforce it above.
            for (var entry : java.util.Collections.list(jar.entries())) {
                assertFalse(entry.getName().startsWith("net/")); assertFalse(entry.getName().startsWith("cpw/"));
                assertFalse(entry.getName().startsWith("dev/legacybridge/converter/"));
                assertFalse(entry.getName().startsWith("dev/legacybridge/fixture/"));
                assertFalse(entry.getName().startsWith("dev/legacybridge/validation/trace/"));
                assertFalse(entry.getName().endsWith(".jar"));
            }
            for (String type : Set.of("HandshakeProbe", "HandshakeClient", "HandshakeControl", "HandshakeScenario", "HandshakeSocketDiagnostic", "HandshakeTransportDiagnostic", "HandshakeFramePrefix", "FixtureAccess"))
                assertNotNull(jar.getEntry("dev/legacybridge/validation/network/" + type + ".class"));
            assertNull(jar.getEntry("dev/legacybridge/validation/network/NetworkRuntimeProbe.class"));
            assertNull(jar.getEntry("dev/legacybridge/validation/network/NetworkClientProbe.class"));
        }
    }
    @Test void sharedObserverHasNoClientClassReferences() throws Exception {
        try (ZipFile jar = new ZipFile(Path.of(System.getProperty("probe.jar")).toFile())) {
            for (String type : Set.of("HandshakeProbe", "HandshakeControl", "HandshakeTransportDiagnostic", "HandshakePacketRecording")) {
                // Inert classfile constant-pool substring boundary, not loading game classes.
                var entry = jar.getEntry("dev/legacybridge/validation/network/" + type + ".class");
                assertNotNull(entry);
                try (var input = jar.getInputStream(entry)) {
                    String bytes = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                    assertFalse(bytes.contains("net/minecraft/client/"), type);
                    assertFalse(bytes.contains("net/minecraftforge/client/"), type);
                    assertFalse(bytes.contains("HandshakeSocketDiagnostic"), type);
                    assertFalse(bytes.contains("HandshakeClient"), type);
                }
            }
        }
    }
    private static void assertInventory(HandshakeScenario mode, boolean serverFixture, boolean clientFixture, boolean serverPeer, boolean clientPeer, String rejects) {
        assertEquals(serverFixture, mode.fixturePresent("server")); assertEquals(clientFixture, mode.fixturePresent("a"));
        assertEquals(serverPeer, mode.emulateProtocol("server")); assertEquals(clientPeer, mode.emulateProtocol("a"));
        assertEquals(rejects, mode.rejectingRole());
    }
}
