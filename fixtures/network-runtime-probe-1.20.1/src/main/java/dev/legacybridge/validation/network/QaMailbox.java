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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** One daemon worker/process, one in-flight command, <=96 commands, 2 KiB input/16 KiB output.
 * No disk reads, JSON parsing or waiting occurs on Minecraft's client/server threads.
 * Files are test control only. Snapshot values travel solely through the native game connection.
 */
final class QaMailbox {
    record Pending(QaCommand command, CompletableFuture<JsonObject> response) {}
    private final Path directory;
    private final String role;
    private final Consumer<Pending> submit;
    private final Runnable stop;

    QaMailbox(String role, Consumer<Pending> submit, Runnable stop) throws IOException {
        if (!Set.of("server", "a", "b").contains(role)) throw new IOException("Closed QA role required");
        this.role = role;
        this.submit = submit;
        this.stop = stop;
        String configured = System.getProperty("legacybridge.netqa.control", "");
        if (configured.isEmpty()) throw new IOException("Explicit isolated QA control directory required");
        Path control = Path.of(configured).toAbsolutePath().normalize();
        if (!control.getFileName().toString().equals("control") || !control.getParent().getParent().getFileName().toString().equals("network-snapshot-v1")) {
            throw new IOException("Control directory outside the owned network scenario");
        }
        directory = control.resolve(role);
    }
    void start() {
        Thread worker = new Thread(this::run, "legacybridge-network-qa-" + role);
        worker.setDaemon(true);
        worker.start();
    }
    private void run() {
        int current = 0;
        try {
            assertPlain(directory);
            JsonObject ready = base("READY", 0);
            ready.addProperty("fixtureVersion", FixtureAccess.version());
            write(0, ready);
            for (int id = 1; id <= 96; id++) {
                current = id;
                Path input = directory.resolve(String.format("command-%04d.json", id));
                long deadline = System.nanoTime() + Duration.ofMinutes(8).toNanos();
                while (!Files.exists(input, LinkOption.NOFOLLOW_LINKS)) {
                    if (System.nanoTime() > deadline) throw new IOException("QA orchestration deadline");
                    Thread.sleep(50);
                }
                assertPlain(input);
                if (Files.size(input) > 2048) throw new IOException("Command input exceeds limit");
                QaCommand command = QaCommand.parse(Files.readString(input, StandardCharsets.UTF_8));
                if (command.id() != id) throw new IOException("Command identity/sequence mismatch");
                CompletableFuture<JsonObject> response = new CompletableFuture<>();
                submit.accept(new Pending(command, response));
                JsonObject data = response.get(120, TimeUnit.SECONDS);
                data.addProperty("status", "PASSED");
                data.addProperty("schemaVersion", 1);
                data.addProperty("id", id);
                data.addProperty("role", role);
                data.addProperty("action", command.action());
                write(id, data);
                if (command.action().equals("STOP")) {
                    stop.run(); // Receipt is forced and atomically published before native shutdown.
                    return;
                }
            }
            throw new IOException("QA command limit reached without STOP");
        } catch (Exception failure) {
            System.err.println("LEGACYBRIDGE_NETWORK_QA_FAILED role=" + role + " command=" + current);
            failure.printStackTrace(System.err);
            JsonObject result = base("FAILED", current);
            result.addProperty("causeType", failure.getClass().getName());
            String message = String.valueOf(failure.getMessage());
            result.addProperty("cause", message.substring(0, Math.min(message.length(), 512)));
            try { write(current, result); } catch (IOException writeFailure) { writeFailure.printStackTrace(System.err); }
            stop.run();
        }
    }
    private JsonObject base(String status, int id) {
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", 1); result.addProperty("role", role);
        result.addProperty("status", status); result.addProperty("id", id);
        return result;
    }
    private void write(int id, JsonObject result) throws IOException {
        byte[] bytes = (new Gson().toJson(result) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 16384) throw new IOException("Response exceeds limit");
        assertPlain(directory);
        Path temporary = directory.resolve(String.format("receipt-%04d.tmp", id));
        Path target = directory.resolve(String.format("receipt-%04d.json", id));
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Existing receipt preserved");
        try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) file.write(buffer);
            file.force(true);
        }
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    }
    static void assertPlain(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().equals(path.toAbsolutePath().normalize())) {
            throw new IOException("Missing or aliased test path");
        }
        for (Path part = path; part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part) || Files.readAttributes(part, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther()) {
                throw new IOException("Symlink/reparse path forbidden");
            }
        }
    }
}
