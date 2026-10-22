package dev.legacybridge.product.controller;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class InstallationShutdownBarrierTest {
    @TempDir Path directory;

    @Test void registersOnceBeforeAcceptingAndReusesTheSameHookAcrossLeaseCycles() throws Exception {
        AtomicInteger registrations = new AtomicInteger();
        AtomicReference<Thread> hook = new AtomicReference<>();
        var state = new InstallationShutdownBarrier.State(value -> {
            assertEquals(1, registrations.incrementAndGet());
            assertFalse(value.isAlive());
            hook.set(value);
        });
        try (var first = state.acquire(); var second = state.acquire()) {
            assertEquals(1, registrations.get());
            assertNotNull(hook.get(), "Required hook must exist before either lease is accepted");
            first.close(); first.close();
        }
        try (var later = state.acquire()) { assertEquals(1, registrations.get()); }
        hook.get().start(); hook.get().join(2_000);
        assertFalse(hook.get().isAlive());
        assertThrows(IllegalStateException.class, state::acquire);
    }

    @Test void shutdownRegistrationFailureAcceptsNothingAndBecomesPermanentlyClosed() {
        AtomicInteger attempts = new AtomicInteger();
        var state = new InstallationShutdownBarrier.State(hook -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("Shutdown in progress");
        });
        assertThrows(IllegalStateException.class, state::acquire);
        assertThrows(IllegalStateException.class, state::acquire);
        assertEquals(1, attempts.get());
    }

    @Test void deniedHookPermissionCannotReturnAnUnprotectedLease() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<Thread> hook = new AtomicReference<>();
        var state = new InstallationShutdownBarrier.State(value -> {
            if (attempts.incrementAndGet() == 1) throw new SecurityException("shutdownHooks denied");
            hook.set(value);
        });
        assertThrows(SecurityException.class, state::acquire);
        try (var acceptedAfterPermissionRestored = state.acquire()) { assertEquals(2, attempts.get()); }
        hook.get().start(); hook.get().join(2_000);
        assertFalse(hook.get().isAlive(), "A failed registration must not retain an invisible lease");
    }

    @Test void hookRejectsNewWorkAndWaitsForEveryLeaseDespiteInterruption() throws Exception {
        AtomicReference<Thread> hook = new AtomicReference<>();
        var state = new InstallationShutdownBarrier.State(hook::set);
        var first = state.acquire(); var second = state.acquire();
        Thread waiting = hook.get();
        try {
            waiting.start(); awaitWaiting(waiting);
            assertThrows(IllegalStateException.class, state::acquire);
            first.close(); first.close();
            waiting.interrupt();
            long interruptDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (waiting.isAlive() && (waiting.isInterrupted() || waiting.getState() != Thread.State.WAITING)
                    && System.nanoTime() < interruptDeadline) Thread.sleep(1);
            assertFalse(waiting.isInterrupted(), "Hook must consume the interrupt and resume waiting");
            assertEquals(Thread.State.WAITING, waiting.getState());
            assertTrue(waiting.isAlive(), "Interrupt must not pretend that the second installation completed");
            second.close(); waiting.join(2_000);
            assertFalse(waiting.isAlive());
        } finally { first.close(); second.close(); waiting.join(2_000); }
    }

    @Test void concurrentAcceptanceAndRepeatedConcurrentCloseDoNotLeakOrUnderflowLeases() throws Exception {
        AtomicInteger registrations = new AtomicInteger();
        AtomicReference<Thread> hook = new AtomicReference<>();
        var state = new InstallationShutdownBarrier.State(value -> { registrations.incrementAndGet(); hook.set(value); });
        var workers = Executors.newFixedThreadPool(8);
        List<InstallationShutdownBarrier.Lease> leases = new ArrayList<>();
        try {
            List<java.util.concurrent.Future<InstallationShutdownBarrier.Lease>> acquisitions = new ArrayList<>();
            for (int index = 0; index < 64; index++) acquisitions.add(workers.submit(state::acquire));
            for (var acquisition : acquisitions) leases.add(acquisition.get(5, TimeUnit.SECONDS));
            assertEquals(1, registrations.get());
            hook.get().start(); awaitWaiting(hook.get());
            List<java.util.concurrent.Future<?>> releases = new ArrayList<>();
            for (var lease : leases) for (int repetition = 0; repetition < 3; repetition++) releases.add(workers.submit(lease::close));
            for (var release : releases) release.get(5, TimeUnit.SECONDS);
            hook.get().join(2_000); assertFalse(hook.get().isAlive());
            assertThrows(IllegalStateException.class, state::acquire);
        } finally {
            leases.forEach(InstallationShutdownBarrier.Lease::close);
            workers.shutdownNow(); assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void realSystemExitWaitsForAcceptedDaemonInstallationUntilItsCompletionMarker() throws Exception {
        Process child = child("hold-exit");
        try {
            awaitFile(child, "accepted"); awaitFile(child, "shutdown-entered");
            assertFalse(child.waitFor(250, TimeUnit.MILLISECONDS), "Orderly exit bypassed the installation lease");
            assertFalse(Files.exists(directory.resolve("operation-complete")));
            Files.writeString(directory.resolve("release"), "release operation", StandardOpenOption.CREATE_NEW);
            assertTrue(child.waitFor(15, TimeUnit.SECONDS), "Child never completed after its installation was released");
            assertEquals(0, child.exitValue(), childLog());
            assertEquals("completed before lease release", Files.readString(directory.resolve("operation-complete")));
        } finally { cleanup(child); }
    }

    @Test void firstPublicAcquisitionFromAnAlreadyRunningShutdownHookIsRefused() throws Exception {
        Process child = child("reject-first-during-exit");
        try {
            assertTrue(child.waitFor(15, TimeUnit.SECONDS), "Refusal child did not exit");
            assertEquals(0, child.exitValue(), childLog());
            assertEquals("refused", Files.readString(directory.resolve("shutdown-refused")));
            assertFalse(Files.exists(directory.resolve("unexpected-acceptance")));
        } finally { cleanup(child); }
    }

    private Process child(String mode) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        String classes = Path.of(InstallationShutdownChild.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + File.pathSeparator + Path.of(InstallationShutdownBarrier.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return new ProcessBuilder(java.toString(), "-cp", classes, InstallationShutdownChild.class.getName(), mode, directory.toString())
                .redirectErrorStream(true).redirectOutput(directory.resolve("child.log").toFile()).start();
    }

    private void awaitFile(Process child, String name) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!Files.exists(directory.resolve(name)) && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(Files.exists(directory.resolve(name)), "Missing child marker " + name + ": " + childLog());
    }

    private String childLog() throws IOException {
        try (var input = Files.newInputStream(directory.resolve("child.log"))) { return new String(input.readNBytes(8_192), StandardCharsets.UTF_8); }
    }

    private void cleanup(Process child) throws Exception {
        if (!child.isAlive()) return;
        if (!Files.exists(directory.resolve("release"))) Files.writeString(directory.resolve("release"), "test cleanup", StandardOpenOption.CREATE_NEW);
        if (!child.waitFor(5, TimeUnit.SECONDS)) {
            // Only this owned test process; a forced cleanup is never evidence of a successful barrier.
            child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private static void awaitWaiting(Thread thread) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (thread.getState() != Thread.State.WAITING && thread.isAlive() && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(Thread.State.WAITING, thread.getState(), "Hook must reach its lease wait");
    }
}

/** JDK-only subprocess entry point. System.exit is deliberately confined to this test helper. */
final class InstallationShutdownChild {
    private InstallationShutdownChild() { }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[1]).toRealPath();
        if (args[0].equals("reject-first-during-exit")) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try (var unexpected = InstallationShutdownBarrier.acquire()) { marker(directory, "unexpected-acceptance", "unsafe"); }
                catch (IllegalStateException expected) { marker(directory, "shutdown-refused", "refused"); }
            }, "qa-first-acquisition-after-shutdown"));
            System.exit(0);
            return;
        }
        if (!args[0].equals("hold-exit")) throw new IllegalArgumentException("Unknown QA mode");
        var lease = InstallationShutdownBarrier.acquire();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> marker(directory, "shutdown-entered", "orderly exit started"), "qa-shutdown-observer"));
        CountDownLatch accepted = new CountDownLatch(1);
        Thread operation = new Thread(() -> {
            try {
                marker(directory, "accepted", "lease retained"); accepted.countDown();
                while (!Files.exists(directory.resolve("release"))) Thread.sleep(10);
                marker(directory, "operation-complete", "completed before lease release");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new IllegalStateException("QA operation was interrupted", interrupted);
            } finally { lease.close(); }
        }, "qa-accepted-daemon-installation");
        operation.setDaemon(true); operation.start(); accepted.await();
        System.exit(0);
    }

    private static void marker(Path directory, String name, String value) {
        try { Files.writeString(directory.resolve(name), value, StandardOpenOption.CREATE_NEW); }
        catch (IOException failure) { throw new IllegalStateException("Cannot write QA marker " + name, failure); }
    }
}
