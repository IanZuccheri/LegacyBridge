package dev.legacybridge.product.install;

import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.legacybridge.product.install.TransactionResult.Status;
import static org.junit.jupiter.api.Assertions.*;

class TransactionalInstallerTest {
    @TempDir Path game;
    private static final Set<String> BASELINE = Set.of("minecraft", "forge", "legacybridge_runtime");
    private static final InstallSession SESSION = new InstallSession(BASELINE, Set.of(), false);

    @BeforeEach void prepare() throws Exception {
        for (String name : List.of("input", "staging", "reports", "cache", "transactions")) Files.createDirectories(game.resolve("legacybridge").resolve(name));
        Files.createDirectory(game.resolve("mods"));
        AddOnlyInstallerTest.installedMod(game.resolve("mods/LegacyBridge.jar"), "legacybridge_runtime", AdapterCatalog.RUNTIME_VERSION);
    }

    @Test void commitsTwoIndependentEntriesWithInertOriginalsAndReadOnlyInspection() throws Exception {
        var first = selection("first_content", "1.0"); var second = selection("second_content", "1.0");
        var installer = new TransactionalInstaller(); var result = installer.apply(game, List.of(first, second), SESSION);
        assertEquals(Status.COMMITTED, result.status(), result.toString()); assertTrue(result.safeToClose(), result.toString()); assertEquals(2, result.entries().size());
        assertEquals(3, installedCount());
        for (var entry : result.entries()) {
            assertEquals(entry.sha256(), AddOnlyInstaller.hash(entry.installed().orElseThrow()));
            assertTrue(Files.isSameFile(entry.installed().orElseThrow(), result.transaction().resolve("payload-" + entry.index() + ".jar")));
        }
        assertFalse(Files.isSameFile(first.staged(), result.entries().get(0).installed().orElseThrow()));
        assertEquals(first.originalSha256(), AddOnlyInstaller.hash(first.original()));
        assertEquals(second.originalSha256(), AddOnlyInstaller.hash(second.original()));
        assertEquals(result, installer.inspect(game, result.transaction(), SESSION));
        assertEquals(result, installer.list(game, SESSION).get(0));
    }

    @Test void duplicateCandidateOrChangedSourceRefusesWholeBatchBeforeAnyPublication() throws Exception {
        var first = selection("same_content", "1.0"); var second = selection("same_content", "2.0");
        var duplicate = new TransactionalInstaller().apply(game, List.of(first, second), SESSION);
        assertEquals(Status.MANUAL, duplicate.status()); assertFalse(duplicate.safeToClose()); assertEquals(1, installedCount());
        Files.writeString(second.original(), "external change");
        var changed = new TransactionalInstaller().apply(game, List.of(first, second), SESSION);
        assertEquals(Status.MANUAL, changed.status()); assertEquals(1, installedCount());
    }

    @Test void limitAndWorldOpenRefuseBeforeCreatingARealTransaction() throws Exception {
        var first = selection("limited_content", "1.0");
        assertThrows(InstallException.class, () -> new TransactionalInstaller().apply(game, List.of(first, first, first, first, first), SESSION));
        assertThrows(InstallException.class, () -> new TransactionalInstaller().apply(game, List.of(first), new InstallSession(BASELINE, Set.of(), true)));
        assertEquals(1, installedCount());
    }

    @Test void stopsAfterFirstPublishedEntryAndExplicitResumeIsIdempotent() throws Exception {
        var first = selection("first_content", "1.0"); var second = selection("second_content", "1.0");
        var interrupted = fail(TransactionalInstaller.Boundary.PUBLISHED, 0).apply(game, List.of(first, second), SESSION);
        assertEquals(Status.PARTIAL, interrupted.status(), interrupted.toString()); assertFalse(interrupted.safeToClose()); assertEquals(2, installedCount());
        Path installed = interrupted.entries().get(0).installed().orElseThrow(); String sha = AddOnlyInstaller.hash(installed);
        var fresh = new TransactionalInstaller(); assertEquals(Status.PARTIAL, fresh.inspect(game, interrupted.transaction(), SESSION).status());
        var resumed = fresh.recover(game, interrupted.transaction(), SESSION, RecoveryAction.RESUME);
        assertEquals(Status.COMMITTED, resumed.status()); assertTrue(resumed.safeToClose()); assertEquals(3, installedCount());
        assertEquals(sha, AddOnlyInstaller.hash(installed));
        assertEquals(resumed, fresh.recover(game, interrupted.transaction(), SESSION, RecoveryAction.RESUME));
    }

    @Test void explicitRestoreOfPartialIndependentBatchIsIdempotentAndDoesNotReapply() throws Exception {
        var first = selection("first_content", "1.0"); var second = selection("second_content", "1.0");
        var partial = fail(TransactionalInstaller.Boundary.PUBLISHED, 0).apply(game, List.of(first, second), SESSION);
        var installer = new TransactionalInstaller(); var restored = installer.recover(game, partial.transaction(), SESSION, RecoveryAction.RESTORE);
        assertEquals(Status.RESTORED, restored.status()); assertTrue(restored.safeToClose()); assertEquals(1, installedCount());
        assertEquals(restored, installer.recover(game, partial.transaction(), SESSION, RecoveryAction.RESTORE));
        assertFalse(installer.recover(game, partial.transaction(), SESSION, RecoveryAction.RESUME).safeToClose());
        assertEquals(1, installedCount()); assertEquals(first.originalSha256(), AddOnlyInstaller.hash(first.original()));
    }

    @Test void unloadedManagedV1UpgradeHasIndependentBackupAndRestoresExactOldIdentity() throws Exception {
        var oldSelection = selection("upgrade_content", "1.0");
        var old = new AddOnlyInstaller().apply(game, oldSelection.staged(), oldSelection.resultSha256(), oldSelection.original(), oldSelection.originalSha256(), BASELINE);
        var replacement = replacing(selection("upgrade_content", "2.0"), old.transaction());
        var installer = new TransactionalInstaller(); var updated = installer.apply(game, List.of(replacement), SESSION);
        assertEquals(Status.COMMITTED, updated.status()); assertFalse(Files.exists(old.installed())); assertEquals(2, installedCount());
        Path backup = updated.transaction().resolve("backup-0.jar"), retired = updated.transaction().resolve("retired-0.jar");
        assertEquals(old.sha256(), AddOnlyInstaller.hash(backup)); assertFalse(Files.isSameFile(backup, retired));
        var restored = installer.recover(game, updated.transaction(), SESSION, RecoveryAction.RESTORE);
        assertEquals(Status.RESTORED, restored.status()); assertEquals(old.sha256(), AddOnlyInstaller.hash(old.installed()));
        assertEquals(old, new AddOnlyInstaller().inspect(game, old.transaction())); assertEquals(2, installedCount());
    }

    @Test void unloadedManagedV2UpgradeAndRetirementFailureAreRecoverable() throws Exception {
        var installer = new TransactionalInstaller(); var first = installer.apply(game, List.of(selection("upgrade_content", "1.0")), SESSION);
        var replacement = replacing(selection("upgrade_content", "2.0"), first.transaction());
        var interrupted = fail(TransactionalInstaller.Boundary.RETIRED, 0).apply(game, List.of(replacement), SESSION);
        assertEquals(Status.PARTIAL, interrupted.status()); assertEquals(1, installedCount()); assertFalse(interrupted.safeToClose());
        var restored = installer.recover(game, interrupted.transaction(), SESSION, RecoveryAction.RESTORE);
        assertEquals(Status.RESTORED, restored.status()); assertEquals(2, installedCount());
        assertEquals(first, installer.inspect(game, first.transaction(), SESSION));
    }

    @Test void loadedIdOrLoadedFileMakesUpgradeManualWithoutRemovingAnyJar() throws Exception {
        var installer = new TransactionalInstaller(); var first = installer.apply(game, List.of(selection("upgrade_content", "1.0")), SESSION);
        var replacement = replacing(selection("upgrade_content", "2.0"), first.transaction());
        Path old = first.entries().get(0).installed().orElseThrow(); String before = AddOnlyInstaller.hash(old);
        var loadedIds = new InstallSession(Set.of("minecraft", "forge", "legacybridge_runtime", "upgrade_content"), Set.of(), false);
        assertEquals(Status.MANUAL, installer.apply(game, List.of(replacement), loadedIds).status());
        assertEquals(Status.MANUAL, installer.apply(game, List.of(replacement), new InstallSession(BASELINE, Set.of(old), false)).status());
        assertEquals(before, AddOnlyInstaller.hash(old)); assertEquals(2, installedCount());
        assertFalse(installer.recover(game, first.transaction(), loadedIds, RecoveryAction.RESTORE).safeToClose());
        assertEquals(before, AddOnlyInstaller.hash(old));
    }

    @Test void identicalIndependentReplacementOfInstalledFileIsNeverRemovedByRestore() throws Exception {
        var installer = new TransactionalInstaller(); var result = installer.apply(game, List.of(selection("own_content", "1.0")), SESSION);
        Path installed = result.entries().get(0).installed().orElseThrow(), moved = game.resolve("retained-owned.jar");
        Files.move(installed, moved); Files.copy(moved, installed);
        assertFalse(Files.isSameFile(installed, result.transaction().resolve("payload-0.jar")));
        var refused = installer.recover(game, result.transaction(), SESSION, RecoveryAction.RESTORE);
        assertFalse(refused.safeToClose()); assertTrue(Files.exists(installed)); assertEquals(AddOnlyInstaller.hash(moved), AddOnlyInstaller.hash(installed));
        assertEquals(Status.RECOVERY_REQUIRED, installer.inspect(game, result.transaction(), SESSION).status());
    }

    @Test void identicalOldFileSwapAfterPreparedAbortsUpgradeWithoutDeletingUnknownCopy() throws Exception {
        var installer = new TransactionalInstaller(); var first = installer.apply(game, List.of(selection("upgrade_content", "1.0")), SESSION);
        Path old = first.entries().get(0).installed().orElseThrow(); String hash = AddOnlyInstaller.hash(old);
        var changing = new TransactionalInstaller((phase, index) -> {
            if (phase == TransactionalInstaller.Boundary.PREPARED) { Path retained = game.resolve("retained-old.jar"); Files.move(old, retained); Files.copy(retained, old); }
        });
        var result = changing.apply(game, List.of(replacing(selection("upgrade_content", "2.0"), first.transaction())), SESSION);
        assertFalse(result.safeToClose()); assertEquals(hash, AddOnlyInstaller.hash(old)); assertEquals(2, installedCount());
    }

    @Test void loadedUnknownOrSameVersionAndDowngradeUpdatesAreRefused() throws Exception {
        var installer = new TransactionalInstaller(); var first = installer.apply(game, List.of(selection("upgrade_content", "2.0")), SESSION);
        Path old = first.entries().get(0).installed().orElseThrow();
        var same = replacing(selection("upgrade_content", "2.0.0"), first.transaction());
        var downgrade = replacing(selection("upgrade_content", "1.0"), first.transaction());
        assertEquals(Status.MANUAL, installer.apply(game, List.of(same), SESSION).status());
        assertEquals(Status.MANUAL, installer.apply(game, List.of(downgrade), SESSION).status());
        assertEquals(Status.MANUAL, installer.apply(game, List.of(selection("upgrade_content", "3.0")), SESSION).status());
        assertTrue(Files.exists(old)); assertEquals(2, installedCount());
    }

    @Test void requiredInboundConsumerRefusesRetirementGapBeforeAnyModMutation() throws Exception {
        var installer = new TransactionalInstaller(); var first = installer.apply(game, List.of(selection("upgrade_content", "1.0")), SESSION);
        installedDependent("consumer_mod", "upgrade_content", "[1,3)", true, "NONE");
        Path old = first.entries().get(0).installed().orElseThrow(); String hash = AddOnlyInstaller.hash(old);
        var result = installer.apply(game, List.of(replacing(selection("upgrade_content", "2.0"), first.transaction())), SESSION);
        assertEquals(Status.MANUAL, result.status()); assertEquals(hash, AddOnlyInstaller.hash(old)); assertEquals(3, installedCount());
    }

    @Test void replacementOfModsDirectoryInsidePublicationHookCannotReceiveTheJar() throws Exception {
        Path prior = game.resolve("retained-mods");
        var installer = new TransactionalInstaller((phase, index) -> {
            if (phase == TransactionalInstaller.Boundary.BEFORE_PUBLISH) { Files.move(game.resolve("mods"), prior); Files.createDirectory(game.resolve("mods")); }
        });
        var result = installer.apply(game, List.of(selection("new_content", "1.0")), SESSION);
        assertFalse(result.safeToClose()); assertEquals(0, installedCount());
        assertTrue(Files.exists(prior.resolve("LegacyBridge.jar")));
    }

    @Test void identicalNewFileSwapInsideRestoreHookCannotBeDeleted() throws Exception {
        var result = new TransactionalInstaller().apply(game, List.of(selection("new_content", "1.0")), SESSION);
        Path installed = result.entries().get(0).installed().orElseThrow();
        var restore = new TransactionalInstaller((phase, index) -> {
            if (phase == TransactionalInstaller.Boundary.BEFORE_RESTORE_REMOVE) { Path retained = game.resolve("retained-published.jar"); Files.move(installed, retained); Files.copy(retained, installed); }
        }).recover(game, result.transaction(), SESSION, RecoveryAction.RESTORE);
        assertFalse(restore.safeToClose()); assertTrue(Files.exists(installed));
        assertEquals(result.entries().get(0).sha256(), AddOnlyInstaller.hash(installed));
    }

    @Test void externallyAddedMissingDependencyAndInboundOrderingAreFailClosed() throws Exception {
        installedDependent("consumer_mod", "missing_mod", "[1,)", true, "NONE");
        var first = new TransactionalInstaller().apply(game, List.of(selection("new_content", "1.0")), SESSION);
        assertEquals(Status.MANUAL, first.status()); assertEquals(2, installedCount());
        Files.move(game.resolve("mods/consumer_mod.jar"), game.resolve("external-invalid.jar"));
        installedDependent("ordering_mod", "new_content", "[1,)", false, "AFTER");
        var second = new TransactionalInstaller().apply(game, List.of(selection("new_content", "2.0")), SESSION);
        assertEquals(Status.MANUAL, second.status()); assertEquals(2, installedCount());
    }

    @Test void interruptedPrivateCopyAndPartialJournalTailNeverPublishJar() throws Exception {
        var first = selection("new_content", "1.0");
        var copy = fail(TransactionalInstaller.Boundary.PAYLOAD_COPY, 0).apply(game, List.of(first), SESSION);
        assertEquals(Status.MANUAL, copy.status()); assertEquals(1, installedCount());
        var tail = fail(TransactionalInstaller.Boundary.JOURNAL_WRITE, -1).apply(game, List.of(first), SESSION);
        assertFalse(Files.exists(tail.transaction().resolve(TransactionJournal.PLAN))); assertFalse(tail.safeToClose());
        assertEquals(1, installedCount());
        assertFalse(new TransactionalInstaller().recover(game, tail.transaction(), SESSION, RecoveryAction.RESUME).safeToClose());
        assertTrue(new TransactionalInstaller().list(game, SESSION).stream().allMatch(result -> result.error().isPresent()));
    }

    @Test void permissionFailureBeforeCommitKeepsInstalledStateAndCannotCloseUntilResumed() throws Exception {
        var result = fail(TransactionalInstaller.Boundary.BEFORE_COMMIT, -1).apply(game, List.of(selection("new_content", "1.0")), SESSION);
        assertEquals(Status.PARTIAL, result.status()); assertEquals(2, installedCount()); assertFalse(result.safeToClose());
        assertTrue(result.entries().get(0).installed().isPresent());
        assertTrue(new TransactionalInstaller().recover(game, result.transaction(), SESSION, RecoveryAction.RESUME).safeToClose());
    }

    @Test void restoreInterruptionAfterRemovalResumesWithoutDeletingOtherFiles() throws Exception {
        var normal = new TransactionalInstaller(); var first = normal.apply(game, List.of(selection("upgrade_content", "1.0")), SESSION);
        var updated = normal.apply(game, List.of(replacing(selection("upgrade_content", "2.0"), first.transaction())), SESSION);
        var partial = fail(TransactionalInstaller.Boundary.RESTORE_REMOVED, 0).recover(game, updated.transaction(), SESSION, RecoveryAction.RESTORE);
        assertFalse(partial.safeToClose()); assertEquals(1, installedCount());
        var restored = normal.recover(game, updated.transaction(), SESSION, RecoveryAction.RESTORE);
        assertEquals(Status.RESTORED, restored.status()); assertEquals(2, installedCount());
        assertEquals(first, normal.inspect(game, first.transaction(), SESSION));
    }

    @Test void sharedV1V2LockRefusesAnOverlappingPublisher() throws Exception {
        var selection = selection("new_content", "1.0");
        try (var held = InstallationLock.acquire(game.resolve("legacybridge/transactions"))) {
            var result = new TransactionalInstaller().apply(game, List.of(selection), SESSION);
            org.junit.jupiter.api.Assertions.fail("Expected checked refusal before a transaction exists: " + result);
        } catch (InstallException expected) { assertEquals(1, installedCount()); }
    }

    private InstallSelection selection(String id, String version) throws Exception {
        Path original = Files.writeString(game.resolve("legacybridge/input").resolve(id + "-" + version + ".jar"), "inert original " + id + " " + version);
        Path staged = AddOnlyInstallerTest.jar(game.resolve("legacybridge/staging").resolve(id + "-" + version + ".jar"), AddOnlyInstallerTest.stagedEntries(id, version));
        return new InstallSelection(original, AddOnlyInstaller.hash(original), staged, AddOnlyInstaller.hash(staged), Optional.empty());
    }

    @Test void reservedPlatformIdsInExternalJarsAreNeverAccepted() throws Exception {
        for (String id : List.of("minecraft", "forge")) {
            Path impostor = AddOnlyInstallerTest.installedMod(game.resolve("mods/" + id + ".jar"), id, id.equals("minecraft") ? "1.20.1" : "47.4.23");
            var refused = new TransactionalInstaller().apply(game, List.of(selection("reserved_" + id, "1.0")), SESSION);
            assertFalse(refused.safeToClose(), refused.toString());
            assertTrue(refused.error().orElseThrow().message().contains("reserved platform"), refused.toString());
            Files.move(impostor, game.resolve(id + "-test-disabled.jar"));
        }
    }

    @Test void predecessorCannotBeRecoveredAheadOfItsManagedSuccessor() throws Exception {
        var installer = new TransactionalInstaller();
        var prior = installer.apply(game, List.of(selection("upgrade_content", "1.0")), SESSION);
        assertTrue(prior.safeToClose(), prior.toString());
        var updated = installer.apply(game, List.of(replacing(selection("upgrade_content", "2.0"), prior.transaction())), SESSION);
        assertTrue(updated.safeToClose(), updated.toString());
        var predecessor = installer.inspect(game, prior.transaction(), SESSION);
        assertEquals(Status.MANUAL, predecessor.status(), predecessor.toString());
        assertEquals("ARTIFACT_SUPERSEDED", predecessor.error().orElseThrow().code());
        assertFalse(installer.recover(game, prior.transaction(), SESSION, RecoveryAction.RESTORE).safeToClose());
        assertEquals(updated.entries().get(0).sha256(), AddOnlyInstaller.hash(updated.entries().get(0).installed().orElseThrow()));
        assertTrue(installer.recover(game, updated.transaction(), SESSION, RecoveryAction.RESTORE).safeToClose());
        assertEquals(prior, installer.inspect(game, prior.transaction(), SESSION));
    }
    private static InstallSelection replacing(InstallSelection selection, Path previous) {
        return new InstallSelection(selection.original(), selection.originalSha256(), selection.staged(), selection.resultSha256(), Optional.of(previous));
    }
    private static TransactionalInstaller fail(TransactionalInstaller.Boundary target, int index) {
        AtomicBoolean failed = new AtomicBoolean();
        return new TransactionalInstaller((boundary, entry) -> { if (boundary == target && entry == index && failed.compareAndSet(false, true)) throw new IOException("simulated disk/permission/process boundary failure: " + target); });
    }
    private void installedDependent(String id, String target, String range, boolean mandatory, String ordering) throws Exception {
        String metadata = AddOnlyInstallerTest.metadata(id, "1.0", false) + "[[dependencies." + id + "]]\nmodId=\"" + target
                + "\"\nmandatory=" + mandatory + "\nversionRange=\"" + range + "\"\nordering=\"" + ordering + "\"\nside=\"BOTH\"\n";
        AddOnlyInstallerTest.jar(game.resolve("mods").resolve(id + ".jar"), Map.of("META-INF/mods.toml", metadata.getBytes(StandardCharsets.UTF_8),
                "fixture/Declared.class", AddOnlyInstallerTest.declaration("fixture/Declared", id)));
    }
    private long installedCount() throws IOException { try (var files = Files.list(game.resolve("mods"))) { return files.count(); } }
}
