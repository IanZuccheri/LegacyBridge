package dev.legacybridge.validation.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import jdk.jfr.Recording;
import jdk.jfr.FlightRecorder;
import jdk.jfr.RecordingState;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/** Explicit QA-only native JFR metadata. No agent, platform patch, payload or JVM-argument event. */
final class HandshakePacketRecording implements AutoCloseable {
    static final Set<String> EVENTS = Set.of("minecraft.PacketSent", "minecraft.PacketReceived",
            "jdk.SocketRead", "jdk.SocketWrite", "jdk.DataLoss");
    private static final long FILE_BOUND = 16L * 1024 * 1024;
    private final Recording recording = new Recording(); // No broad default/profile.jfc.
    void start() {
        recording.setName("LegacyBridge closed handshake metadata");
        recording.setDumpOnExit(false);
        recording.setMaxSize(8L * 1024 * 1024); // JDK retention is chunk-granular, not a strict allocation cap.
        recording.setMaxAge(Duration.ofMinutes(2));
        recording.setDuration(Duration.ofSeconds(120));
        // Empty recording settings still honor custom events' @Enabled(true).
        // The caller has registered all pinned Minecraft events before this worker starts.
        for (var type : FlightRecorder.getFlightRecorder().getEventTypes()) recording.disable(type.getName());
        for (String event : EVENTS) recording.enable(event).withoutStackTrace().withThreshold(Duration.ZERO);
        recording.start();
    }
    Map<String, String> settings() { return Map.copyOf(recording.getSettings()); }
    JsonObject finish(Path directory) throws IOException {
        if (recording.getState() != RecordingState.RUNNING) throw new IOException("Recording ended before observation; refuse incomplete metadata");
        recording.stop();
        if (recording.getSize() > FILE_BOUND) throw new IOException("Recording exceeds audit bound");
        Path temporary = Files.createTempFile(directory, "owned-handshake-metadata-", ".jfr");
        try {
            recording.dump(temporary); // Overwrites only this freshly created owned temporary.
            if (Files.size(temporary) > FILE_BOUND) throw new IOException("Recording dump exceeds audit bound");
            return summarize(temporary);
        } finally {
            Files.delete(temporary); // Propagate cleanup failure; do not claim private dump was removed.
        }
    }
    static JsonObject summarize(Path path) throws IOException {
        Map<String, long[]> packets = new LinkedHashMap<>();
        long socketReads = 0, readBytes = 0, socketWrites = 0, writeBytes = 0, nonLoopback = 0;
        int events = 0;
        try (RecordingFile file = new RecordingFile(path)) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                if (++events > 4096) throw new IOException("Recording event bound exceeded");
                String type = event.getEventType().getName();
                if (!EVENTS.contains(type)) throw new IOException("Unapproved recording event type: " + type);
                if (type.equals("jdk.DataLoss")) throw new IOException("JFR data loss prevents interpretation");
                if (type.startsWith("minecraft.")) {
                    String remote = event.getString("remoteAddress");
                    if (!loopback(remote)) throw new IOException("Unexpected non-loopback Minecraft peer");
                    int protocol = event.getInt("protocolId"), packet = event.getInt("packetId"), bytes = event.getInt("bytes");
                    if (protocol < -1 || protocol > 2 || packet < 0 || packet > 255 || bytes < 0 || bytes > 8388608) throw new IOException("Native packet metadata outside diagnostic bounds");
                    String key = type + "/" + protocol + "/" + packet;
                    long[] counters = packets.computeIfAbsent(key, unused -> new long[2]);
                    if (packets.size() > 64) throw new IOException("Packet group bound exceeded");
                    counters[0]++; counters[1] += bytes;
                } else {
                    if (!loopback(event.getString("address"))) { nonLoopback++; continue; }
                    if (type.equals("jdk.SocketRead")) { socketReads++; readBytes += event.getLong("bytesRead"); }
                    else { socketWrites++; writeBytes += event.getLong("bytesWritten"); }
                }
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("nativeJfr", true); result.addProperty("events", events);
        result.addProperty("loopbackSocketReads", socketReads); result.addProperty("loopbackReadBytes", readBytes);
        result.addProperty("loopbackSocketWrites", socketWrites); result.addProperty("loopbackWriteBytes", writeBytes);
        result.addProperty("otherSocketEventsCountedNotExported", nonLoopback);
        JsonArray groups = new JsonArray();
        packets.forEach((key, counts) -> {
            JsonObject group = new JsonObject(); group.addProperty("typeProtocolPacket", key);
            group.addProperty("count", counts[0]); group.addProperty("bytes", counts[1]); groups.add(group);
        });
        result.add("packetGroups", groups);
        result.addProperty("payloadPersisted", false); result.addProperty("rawDumpRetained", false);
        result.addProperty("normalPassClaimed", false);
        return result;
    }
    private static boolean loopback(String address) {
        // Closed launcher binds IPv4 literal. No DNS calls or arbitrary host/address export.
        String literal = address.substring(address.lastIndexOf('/') + 1);
        return literal.equals("127.0.0.1") || literal.matches("127\\.0\\.0\\.1:[0-9]{1,5}");
    }
    @Override public void close() { recording.close(); }
}
