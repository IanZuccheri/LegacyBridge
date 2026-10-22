package dev.legacybridge.validation.network;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** JDK-only loopback/recording checks, never native Minecraft handshake approval. */
@org.junit.jupiter.api.Timeout(30)
final class HandshakePacketRecordingTest {
    @TempDir Path temporary;
    @Test void exactSettingsExcludeArgumentsAndPayloadEvents() throws Exception {
        try (var recorder = new HandshakePacketRecording()) {
            recorder.start();
            recorder.settings().forEach((key, value) -> {
                if (!HandshakePacketRecording.EVENTS.contains(key.split("#")[0])) assertEquals("false", value, key);
            });
            var summary = recorder.finish(temporary);
            assertFalse(summary.get("rawDumpRetained").getAsBoolean());
        }
        try (var files = Files.list(temporary)) { assertEquals(0, files.count()); }
    }
    @Test void realJdkLoopbackBytesAreObservedWithoutGameOrPayloadExport() throws Exception {
        try (var recorder = new HandshakePacketRecording(); var server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            recorder.start();
            try (var client = SocketChannel.open(server.getLocalAddress()); var accepted = server.accept()) {
                assertEquals(4, client.write(ByteBuffer.wrap(new byte[]{1, 2, 3, 4})));
                ByteBuffer data = ByteBuffer.allocate(4);
                while (data.hasRemaining()) assertTrue(accepted.read(data) > 0);
                var summary = recorder.finish(temporary);
                assertEquals(4, summary.get("loopbackWriteBytes").getAsLong());
                assertEquals(4, summary.get("loopbackReadBytes").getAsLong());
                assertFalse(summary.get("payloadPersisted").getAsBoolean());
                assertEquals(0, summary.getAsJsonArray("packetGroups").size());
            }
        }
        try (var files = Files.list(temporary)) { assertEquals(0, files.count()); }
    }
    @Name("legacybridge.UnapprovedMetadata") static class Unapproved extends Event { }
    @Test void preRegisteredDefaultEnabledCustomEventIsExplicitlyDisabled() throws Exception {
        jdk.jfr.FlightRecorder.register(Unapproved.class);
        try (var recorder = new HandshakePacketRecording()) {
            recorder.start();
            assertEquals("false", recorder.settings().get("legacybridge.UnapprovedMetadata#enabled"));
            new Unapproved().commit();
            assertEquals(0, recorder.finish(temporary).get("events").getAsInt());
        }
    }
    @Test void unexpectedEventFailsInsteadOfExportingUnknownFields() throws Exception {
        Path file = temporary.resolve("owned-negative.jfr");
        try (Recording recording = new Recording()) {
            recording.enable(Unapproved.class); recording.start(); new Unapproved().commit(); recording.stop(); recording.dump(file);
        }
        assertThrows(java.io.IOException.class, () -> HandshakePacketRecording.summarize(file));
    }
}
