package dev.legacybridge.validation.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** One worker, one READY, one terminal result; no I/O or waiting on a game thread.
 * Fixed finish/stop markers are orchestration only, never packet transport.
 */
final class HandshakeControl {
    private final Path directory;
    private final Runnable finish;
    private final Runnable close;
    private final String role;
    private final HandshakeScenario scenario;
    private final AtomicReference<JsonObject> ready = new AtomicReference<>();
    private final AtomicReference<JsonObject> diagnostic = new AtomicReference<>();
    private final CompletableFuture<JsonObject> result = new CompletableFuture<>();
    private volatile boolean recordingReady = !Boolean.getBoolean("legacybridge.handshake.packetRecording");

    HandshakeControl(String role, HandshakeScenario scenario, Runnable finish, Runnable close) {
        HandshakeScenario.checkRole(role);
        this.role = role; this.scenario = scenario; this.finish = finish; this.close = close;
        String configured = System.getProperty("legacybridge.netqa.control", "");
        if (configured.isEmpty()) throw new IllegalStateException("Explicit control directory required");
        Path control = Path.of(configured).toAbsolutePath().normalize();
        if (!control.getFileName().toString().equals("control") || control.getParent() == null ||
                control.getParent().getParent() == null || !control.getParent().getParent().getFileName().toString().equals("network-snapshot-v1")) {
            throw new IllegalStateException("Control outside owned network QA installation");
        }
        directory = control.resolve(role);
    }
    void start() { Thread worker = new Thread(this::run, "legacybridge-handshake-qa-" + role); worker.setDaemon(true); worker.start(); }
    boolean recordingReady() { return recordingReady; }
    void ready(JsonObject snapshot) { if (!ready.compareAndSet(null, snapshot)) throw new IllegalStateException("Duplicate READY"); }
    void complete(JsonObject snapshot) { result.complete(snapshot); }
    void diagnostic(JsonObject snapshot) { if (!diagnostic.compareAndSet(null, snapshot)) throw new IllegalStateException("Duplicate bounded diagnostic"); }
    void fail(Exception failure) {
        failure.printStackTrace(System.err);
        JsonObject data = new JsonObject();
        data.addProperty("status", "FAILED"); data.addProperty("causeType", failure.getClass().getName());
        String message = String.valueOf(failure.getMessage());
        data.addProperty("cause", message.substring(0, Math.min(512, message.length())));
        result.complete(data);
    }
    private void run() {
        try (HandshakePacketRecording recording = Boolean.getBoolean("legacybridge.handshake.packetRecording") ? new HandshakePacketRecording() : null) {
            assertPlain(directory);
            if (recording != null) recording.start();
            recordingReady = true;
            boolean readyWritten = false, finishSubmitted = false, diagnosticWritten = false;
            long deadline = System.nanoTime() + Duration.ofMinutes(8).toNanos();
            while (System.nanoTime() < deadline) {
                JsonObject first = ready.get();
                if (!readyWritten && first != null) { write("ready", first, "READY"); readyWritten = true; }
                JsonObject sample = diagnostic.get();
                if (!diagnosticWritten && sample != null) { write("diagnostic", sample, "OBSERVED"); diagnosticWritten = true; }
                if (result.isDone()) {
                    if (recording != null) write("packet-diagnostic", recording.finish(directory), "OBSERVED");
                    write("result", result.getNow(null), "PASSED");
                    if (role.equals("a")) close.run(); // Only after durable publication.
                    return;
                }
                if (marker("stop")) { throw new IOException("Orchestrator requested abort"); }
                if (!finishSubmitted && marker("finish")) { finishSubmitted = true; finish.run(); }
                Thread.sleep(50);
            }
            throw new IOException("Handshake QA orchestration deadline");
        } catch (Exception failure) {
            System.err.println("LEGACYBRIDGE_HANDSHAKE_QA_FAILED role=" + role);
            failure.printStackTrace(System.err);
            close.run();
        }
    }
    private boolean marker(String name) throws IOException {
        Path path = directory.resolve(name + ".request");
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return false;
        assertPlain(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != 1 || Files.readAllBytes(path)[0] != '1') {
            throw new IOException("Invalid fixed control marker");
        }
        return true;
    }
    private void write(String name, JsonObject data, String status) throws IOException {
        data.addProperty("schemaVersion", 1); data.addProperty("role", role); data.addProperty("scenario", scenario.name());
        if (!data.has("status")) data.addProperty("status", status);
        byte[] bytes = (new Gson().toJson(data) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 16384) throw new IOException("Bounded receipt exceeded");
        assertPlain(directory);
        Path temporary = directory.resolve(name + ".tmp"), destination = directory.resolve(name + ".json");
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Existing receipt preserved");
        try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) file.write(buffer);
            file.force(true);
        }
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
    }
    private static void assertPlain(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().equals(path.toAbsolutePath().normalize())) throw new IOException("Missing/aliased QA path");
        for (Path part = path; part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part) || Files.readAttributes(part, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther()) throw new IOException("Reparse path forbidden");
        }
    }
}
