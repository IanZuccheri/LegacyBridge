package dev.legacybridge.product.install;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/** The same cooperative lock for V1 and V2. It is not a sandbox for unrelated processes. */
final class InstallationLock implements AutoCloseable {
    private static final ReentrantLock PROCESS = new ReentrantLock();
    private final FileChannel channel;
    private final FileLock lock;
    private InstallationLock(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }
    static InstallationLock acquire(Path directory) throws IOException, InstallException {
        if (!PROCESS.tryLock()) throw busy();
        FileChannel channel = null;
        try {
            Path path = directory.resolve("add-only.lock");
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                try { Files.createFile(path); }
                catch (java.nio.file.FileAlreadyExistsException raced) { AddOnlyInstaller.regular(path); }
            }
            AddOnlyInstaller.regular(path);
            channel = FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            FileLock lock = channel.tryLock();
            if (lock == null) throw busy();
            return new InstallationLock(channel, lock);
        } catch (IOException | RuntimeException | InstallException failure) {
            if (channel != null) try { channel.close(); } catch (IOException close) { failure.addSuppressed(close); }
            PROCESS.unlock(); throw failure;
        }
    }
    private static InstallException busy() {
        return new InstallException("INSTALL_BUSY", "Another installation holds the shared lock",
                "Wait for the active installation or recovery to finish.", Optional.empty(), Optional.empty());
    }
    @Override public void close() throws IOException {
        try { try { lock.close(); } finally { channel.close(); } } finally { PROCESS.unlock(); }
    }
}
