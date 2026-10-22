package dev.legacybridge.validation.network;

import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Inert parser/archive tests. No Minecraft startup and no fixture class loading. */
final class QaCommandTest {
    @Test void finiteExpectation() throws Exception {
        QaCommand command = QaCommand.parse("{\"id\":1,\"action\":\"EXPECT\",\"sequence\":2,\"value\":0,\"accepted\":2,\"rejected\":1,\"connected\":true}");
        assertEquals(0, command.number("value")); assertTrue(command.flag("connected"));
    }
    @Test void refusesUnknownAndDuplicateFields() {
        assertThrows(IOException.class, () -> QaCommand.parse("{\"id\":1,\"action\":\"STOP\",\"token\":\"x\"}"));
        assertThrows(IOException.class, () -> QaCommand.parse("{\"id\":1,\"id\":2,\"action\":\"STOP\"}"));
    }
    @Test void boundedInputAndFiniteActions() {
        assertThrows(IOException.class, () -> QaCommand.parse(" ".repeat(2049)));
        assertThrows(IOException.class, () -> QaCommand.parse("{\"id\":97,\"action\":\"STOP\"}"));
        assertThrows(IOException.class, () -> QaCommand.parse("{\"id\":1,\"action\":\"EXECUTE\"}"));
    }
    @Test void boundedRawTransport() throws Exception {
        assertEquals("000000000100000065", QaCommand.parse("{\"id\":1,\"action\":\"RAW_C2S\",\"payload\":\"000000000100000065\"}").text("payload"));
        assertThrows(IOException.class, () -> QaCommand.parse("{\"id\":1,\"action\":\"RAW_C2S\",\"payload\":\"\"}"));
        assertThrows(IOException.class, () -> QaCommand.parse("{\"id\":1,\"action\":\"RAW_C2S\",\"payload\":\"" + "00".repeat(33) + "\"}"));
    }
    @Test void rejectsNestedFractionalTrailingAndNegative() {
        assertThrows(IOException.class, () -> QaCommand.parse("{\"id\":1.5,\"action\":\"STOP\"}"));
        assertThrows(IOException.class, () -> QaCommand.parse("{\"id\":-1,\"action\":\"STOP\"}"));
        assertThrows(IOException.class, () -> QaCommand.parse("{\"id\":{},\"action\":\"STOP\"}"));
        assertThrows(IOException.class, () -> QaCommand.parse("{\"id\":1,\"action\":\"STOP\"}{}"));
    }
    @Test void artifactIsOnlyTestProbe() throws Exception {
        try (ZipFile jar = new ZipFile(Path.of(System.getProperty("probe.jar")).toFile())) {
            assertNotNull(jar.getEntry("dev/legacybridge/validation/network/NetworkRuntimeProbe.class"));
            assertNotNull(jar.getEntry("dev/legacybridge/validation/network/NetworkClientProbe.class"));
            assertNotNull(jar.getEntry("META-INF/mods.toml"));
            assertTrue(jar.stream().noneMatch(entry -> entry.getName().startsWith("net/minecraft/") ||
                    entry.getName().startsWith("net/minecraftforge/") || entry.getName().startsWith("dev/legacybridge/fixture/") ||
                    entry.getName().startsWith("com/google/gson/") || entry.getName().endsWith(".java")));
        }
    }
}
