package dev.legacybridge.product.install;

import com.sun.nio.file.ExtendedOpenOption;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static dev.legacybridge.product.install.TransactionResult.Status;
import static org.junit.jupiter.api.Assertions.*;

/** Real Windows share-mode refusals, not advisory FileLock or injected IOException. */
@EnabledOnOs(OS.WINDOWS)
class TransactionalWindowsLockTest {
    @TempDir Path game;
    private static final InstallSession SESSION = new InstallSession(Set.of("minecraft", "forge", "legacybridge_runtime"), Set.of(), false);

    @BeforeEach void prepare() throws Exception {
        for (String area : List.of("input", "staging", "reports", "cache", "transactions"))
            Files.createDirectories(game.resolve("legacybridge").resolve(area));
        Files.createDirectory(game.resolve("mods"));
        AddOnlyInstallerTest.installedMod(game.resolve("mods/LegacyBridge.jar"), "legacybridge_runtime", AdapterCatalog.RUNTIME_VERSION);
    }

    @Test void realDenyDeleteHandlePreservesOldFileAndAllowsExplicitResumeAfterRelease() throws Exception {
        var first = fixture("1.0", Optional.empty());
        var installer = new TransactionalInstaller();
        var prior = installer.apply(game, List.of(first), SESSION); assertTrue(prior.safeToClose());
        Path old = prior.entries().get(0).installed().orElseThrow();
        var update = fixture("2.0", Optional.of(prior.transaction()));
        TransactionResult refused;
        try (var held = FileChannel.open(old, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
            assertTrue(held.isOpen());
            refused = installer.apply(game, List.of(update), SESSION);
            assertFalse(refused.safeToClose()); assertTrue(refused.error().isPresent());
            assertEquals(first.resultSha256(), AddOnlyInstaller.hash(old));
            Path backup = refused.transaction().resolve("backup-0.jar");
            assertTrue(Files.isRegularFile(backup)); assertEquals(first.resultSha256(), AddOnlyInstaller.hash(backup));
            assertFalse(Files.isSameFile(old, backup));
            assertEquals(2, installedCount());
        }
        var resumed = installer.recover(game, refused.transaction(), SESSION, RecoveryAction.RESUME);
        assertEquals(Status.COMMITTED, resumed.status()); assertTrue(resumed.safeToClose());
        assertFalse(Files.exists(old)); assertEquals(2, installedCount());
        assertEquals(update.resultSha256(), AddOnlyInstaller.hash(resumed.entries().get(0).installed().orElseThrow()));
        assertEquals(first.originalSha256(), AddOnlyInstaller.hash(first.original()));
        assertEquals(update.originalSha256(), AddOnlyInstaller.hash(update.original()));
    }

    @Test void realDenyReadHandleRefusesPreflightWithoutRetiringOldFile() throws Exception {
        var first = fixture("1.0", Optional.empty());
        var installer = new TransactionalInstaller();
        var prior = installer.apply(game, List.of(first), SESSION); assertTrue(prior.safeToClose());
        Path old = prior.entries().get(0).installed().orElseThrow();
        var update = fixture("2.0", Optional.of(prior.transaction()));
        try (var held = FileChannel.open(old, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_READ)) {
            assertTrue(held.isOpen());
            var refused = installer.apply(game, List.of(update), SESSION);
            assertEquals(Status.MANUAL, refused.status()); assertFalse(refused.safeToClose());
            assertTrue(refused.error().isPresent()); assertEquals(2, installedCount());
            // Files.exists also returns false when Windows denies the attribute read.
            // Prove the actual sharing violation, then check identity/bytes after release.
            assertThrows(java.io.IOException.class, () -> Files.readAllBytes(old));
        }
        assertTrue(Files.exists(old));
        assertEquals(first.resultSha256(), AddOnlyInstaller.hash(old));
        var retried = installer.apply(game, List.of(update), SESSION);
        assertEquals(Status.COMMITTED, retried.status()); assertTrue(retried.safeToClose());
        assertEquals(update.originalSha256(), AddOnlyInstaller.hash(update.original()));
    }

    private InstallSelection fixture(String version, Optional<Path> prior) throws Exception {
        Path original = Files.writeString(game.resolve("legacybridge/input/locked-" + version + ".jar"), "inert original " + version);
        Path staged = AddOnlyInstallerTest.jar(game.resolve("legacybridge/staging/locked-" + version + ".jar"),
                AddOnlyInstallerTest.stagedEntries("locked_content", version));
        return new InstallSelection(original, AddOnlyInstaller.hash(original), staged, AddOnlyInstaller.hash(staged), prior);
    }
    private long installedCount() throws Exception { try (var files = Files.list(game.resolve("mods"))) { return files.count(); } }
}
