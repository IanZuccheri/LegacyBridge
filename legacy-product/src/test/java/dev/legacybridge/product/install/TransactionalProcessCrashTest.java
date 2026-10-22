package dev.legacybridge.product.install;

import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.io.File;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static dev.legacybridge.product.install.TransactionResult.Status;
import static org.junit.jupiter.api.Assertions.*;

/** These helpers execute only installer code and inert files, never original/converted mod classes. */
public class TransactionalProcessCrashTest {
    @TempDir Path game;
    private static final InstallSession SESSION = new InstallSession(Set.of("minecraft", "forge", "legacybridge_runtime"), Set.of(), false);

    @BeforeEach void prepare() throws Exception {
        for (String area : List.of("input", "staging", "reports", "cache", "transactions"))
            Files.createDirectories(game.resolve("legacybridge").resolve(area));
        Files.createDirectory(game.resolve("mods"));
        Files.createDirectory(game.resolve("empty-user-home"));
        AddOnlyInstallerTest.installedMod(game.resolve("mods/LegacyBridge.jar"), "legacybridge_runtime", AdapterCatalog.RUNTIME_VERSION);
    }

    @Test void actualProcessHaltAfterFirstPublicationIsResumedIdempotentlyInAnotherJvm() throws Exception {
        var first = fixture("crash_first", "1.0");
        var second = fixture("crash_second", "1.0");
        child(81, "halt-published");
        var partial = new TransactionalInstaller().list(game, SESSION);
        assertEquals(1, partial.size()); assertEquals(Status.PARTIAL, partial.get(0).status());
        Path transaction = partial.get(0).transaction();
        assertFalse(Files.exists(transaction.resolve("checkpoint-committed--1.json")));
        assertTrue(partial.get(0).entries().get(0).installed().isPresent());
        assertTrue(partial.get(0).entries().get(1).installed().isEmpty());
        child(0, "recover", transaction.getFileName().toString(), "RESUME", "COMMITTED");
        var committed = new TransactionalInstaller().inspect(game, transaction, SESSION);
        assertEquals(Status.COMMITTED, committed.status()); assertTrue(committed.safeToClose());
        assertEquals(2, committed.entries().size());
        for (var entry : committed.entries()) assertEquals(entry.sha256(), AddOnlyInstaller.hash(entry.installed().orElseThrow()));
        assertEquals(first.originalSha256(), AddOnlyInstaller.hash(first.original()));
        assertEquals(second.originalSha256(), AddOnlyInstaller.hash(second.original()));
        assertEquals(first.resultSha256(), AddOnlyInstaller.hash(first.staged()));
        assertEquals(second.resultSha256(), AddOnlyInstaller.hash(second.staged()));
    }

    @Test void actualProcessHaltAfterRetirementRestoresPriorIdentityInAnotherJvm() throws Exception {
        var original = fixture("crash_update", "1.0");
        var next = fixture("crash_update", "2.0");
        var prior = new TransactionalInstaller().apply(game, List.of(original), SESSION);
        assertTrue(prior.safeToClose());
        Path old = prior.entries().get(0).installed().orElseThrow();
        child(81, "halt-retired", prior.transaction().getFileName().toString());
        assertFalse(Files.exists(old));
        var changed = new TransactionalInstaller().list(game, SESSION).stream()
                .filter(value -> !value.transaction().equals(prior.transaction())).findFirst().orElseThrow();
        assertEquals(Status.PARTIAL, changed.status()); assertFalse(changed.safeToClose());
        Path backup = changed.transaction().resolve("backup-0.jar");
        assertEquals(original.resultSha256(), AddOnlyInstaller.hash(backup));
        assertFalse(Files.isSameFile(backup, prior.transaction().resolve("payload-0.jar")));
        child(0, "recover", changed.transaction().getFileName().toString(), "RESTORE", "RESTORED");
        assertEquals(Status.RESTORED, new TransactionalInstaller().inspect(game, changed.transaction(), SESSION).status());
        assertEquals(original.resultSha256(), AddOnlyInstaller.hash(old));
        assertTrue(Files.isSameFile(old, prior.transaction().resolve("payload-0.jar")));
        assertEquals(prior, new TransactionalInstaller().inspect(game, prior.transaction(), SESSION));
        assertEquals(next.originalSha256(), AddOnlyInstaller.hash(next.original()));
        assertEquals(next.resultSha256(), AddOnlyInstaller.hash(next.staged()));
    }

    @Test void partialPublicationCanResumeWithFirstArtifactDeclaredLoadedWithoutRewritingIt() throws Exception {
        var selections = crashBatch("halt-published");
        var partial = incompleteBatch(1);
        assertFalse(Files.exists(partial.transaction().resolve("checkpoint-installed-0.json")));
        var proofs = protectKnownFiles(selections, partial);
        child(0, "resume-loaded", partial.transaction().getFileName().toString(), "BOTH");
        var committed = new TransactionalInstaller().inspect(game, partial.transaction(), SESSION);
        assertEquals(Status.COMMITTED, committed.status()); assertTrue(committed.safeToClose());
        assertEquals(2, committed.entries().stream().filter(entry -> entry.installed().isPresent()).count());
        for (var entry : committed.entries()) assertEquals(entry.sha256(), AddOnlyInstaller.hash(entry.installed().orElseThrow()));
        verifyUnchanged(proofs);
    }

    @Test void haltBeforeCommitWithAllArtifactsDeclaredLoadedStillRequiresReceiptRecovery() throws Exception {
        var selections = crashBatch("halt-before-commit");
        var partial = incompleteBatch(2);
        for (int index = 0; index < 2; index++)
            assertTrue(Files.exists(partial.transaction().resolve("checkpoint-installed-" + index + ".json")));
        var proofs = protectKnownFiles(selections, partial);
        child(0, "resume-loaded", partial.transaction().getFileName().toString(), "BOTH");
        var committed = new TransactionalInstaller().inspect(game, partial.transaction(), SESSION);
        assertEquals(Status.COMMITTED, committed.status()); assertTrue(committed.safeToClose());
        verifyUnchanged(proofs);
    }

    @ParameterizedTest @ValueSource(strings = {"IDS", "FILES", "BOTH"})
    void restoreAfterHaltRefusesDeclaredLoadedIdOrFileAliasWithoutRemovingProtectedArtifacts(String snapshotMode) throws Exception {
        var selections = crashBatch("halt-published");
        var partial = incompleteBatch(1);
        var proofs = protectKnownFiles(selections, partial);
        // This separate path forces the FILES case to exercise isSameFile, not path-string equality.
        Files.createLink(game.resolve("loaded-file-alias.jar"), partial.entries().get(0).installed().orElseThrow());
        child(0, "restore-loaded-refused", partial.transaction().getFileName().toString(), snapshotMode);
        assertEquals(partial, new TransactionalInstaller().inspect(game, partial.transaction(), SESSION));
        assertFalse(Files.exists(partial.transaction().resolve("checkpoint-restored--1.json")));
        verifyUnchanged(proofs);
    }

    @Test void resumeAfterHaltRefusesAnOpenWorldSnapshotBeforePublication() throws Exception {
        var selections = crashBatch("halt-published");
        var partial = incompleteBatch(1);
        var proofs = protectKnownFiles(selections, partial);
        child(0, "world-open-refused", partial.transaction().getFileName().toString(), "BOTH");
        assertEquals(partial, new TransactionalInstaller().inspect(game, partial.transaction(), SESSION));
        verifyUnchanged(proofs);
    }

    @Test void resumeAfterHaltRefusesMissingEntryWhoseIdIsAlreadyDeclaredLoaded() throws Exception {
        var selections = crashBatch("halt-published");
        var partial = incompleteBatch(1);
        var proofs = protectKnownFiles(selections, partial);
        child(0, "missing-loaded-refused", partial.transaction().getFileName().toString(), "BOTH");
        assertEquals(partial, new TransactionalInstaller().inspect(game, partial.transaction(), SESSION));
        verifyUnchanged(proofs);
    }

    private List<InstallSelection> crashBatch(String mode) throws Exception {
        var selections = List.of(fixture("crash_first", "1.0"), fixture("crash_second", "1.0"));
        child(81, mode);
        return selections;
    }

    private TransactionResult incompleteBatch(int published) throws Exception {
        var transactions = new TransactionalInstaller().list(game, SESSION);
        assertEquals(1, transactions.size());
        var result = transactions.get(0);
        assertEquals(Status.PARTIAL, result.status()); assertFalse(result.safeToClose());
        assertEquals("RECOVERY_REQUIRED", result.error().orElseThrow().code());
        assertEquals(2, result.entries().size());
        assertEquals(published, result.entries().stream().filter(entry -> entry.installed().isPresent()).count());
        assertFalse(Files.exists(result.transaction().resolve("checkpoint-committed--1.json")));
        return result;
    }

    /** Independent retained links detect replacement; hashes and mtimes also detect ordinary in-place writes. */
    private List<FileProof> protectKnownFiles(List<InstallSelection> selections, TransactionResult partial) throws Exception {
        Set<Path> paths = new LinkedHashSet<>();
        paths.add(game.resolve("mods/LegacyBridge.jar"));
        for (var selection : selections) { paths.add(selection.original()); paths.add(selection.staged()); }
        try (var entries = Files.list(partial.transaction())) {
            entries.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().equals("plan-v2.json")
                    || path.getFileName().toString().startsWith("payload-")
                    || path.getFileName().toString().startsWith("checkpoint-")).forEach(paths::add);
        }
        for (var entry : partial.entries()) entry.installed().ifPresent(paths::add);
        Path anchors = Files.createDirectory(game.resolve("retained-test-identities"));
        List<FileProof> result = new ArrayList<>();
        for (Path path : paths) {
            Path anchor = Files.createLink(anchors.resolve("identity-" + result.size()), path);
            result.add(new FileProof(path, anchor, AddOnlyInstaller.hash(path), Files.getLastModifiedTime(path)));
        }
        return List.copyOf(result);
    }

    private static void verifyUnchanged(List<FileProof> proofs) throws Exception {
        for (var proof : proofs) {
            assertTrue(Files.isSameFile(proof.path(), proof.anchor()), "Owned identity was replaced: " + proof.path());
            assertEquals(proof.sha256(), AddOnlyInstaller.hash(proof.path()));
            assertEquals(proof.modified(), Files.getLastModifiedTime(proof.path()));
        }
    }

    private record FileProof(Path path, Path anchor, String sha256, java.nio.file.attribute.FileTime modified) { }

    private InstallSelection fixture(String id, String version) throws Exception {
        Files.writeString(game.resolve("legacybridge/input").resolve(id + "-" + version + ".jar"), "inert source " + id + " " + version);
        AddOnlyInstallerTest.jar(game.resolve("legacybridge/staging").resolve(id + "-" + version + ".jar"),
                AddOnlyInstallerTest.stagedEntries(id, version));
        return observed(game, id, version, Optional.empty());
    }

    private static InstallSelection observed(Path root, String id, String version, Optional<Path> previous) throws Exception {
        Path original = root.resolve("legacybridge/input").resolve(id + "-" + version + ".jar");
        Path staged = root.resolve("legacybridge/staging").resolve(id + "-" + version + ".jar");
        return new InstallSelection(original, AddOnlyInstaller.hash(original), staged, AddOnlyInstaller.hash(staged), previous);
    }

    private void child(int expectedExit, String... mode) throws Exception {
        String id = UUID.randomUUID().toString();
        Path arguments = game.resolve("child-" + id + ".args");
        Path output = game.resolve("child-" + id + ".log");
        List<String> args = new ArrayList<>(List.of("-Xmx128m", "-Djava.awt.headless=true",
                "-Duser.home=" + game.resolve("empty-user-home"), "-cp", childClasspath(), Child.class.getName(), game.toString()));
        args.addAll(List.of(mode));
        Files.writeString(arguments, args.stream().map(TransactionalProcessCrashTest::quoteArgument)
                .reduce((first, second) -> first + "\n" + second).orElseThrow(), StandardCharsets.UTF_8);
        Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Process child = new ProcessBuilder(java.toString(), "@" + arguments).directory(game.toFile())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        child.getOutputStream().close();
        boolean finished = child.waitFor(30, TimeUnit.SECONDS);
        if (!finished) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); }
        String log;
        try (var input = Files.newInputStream(output)) { log = new String(input.readNBytes(16_384), StandardCharsets.UTF_8); }
        assertTrue(finished, "Owned installer child exceeded 30 seconds: " + log);
        assertEquals(expectedExit, child.exitValue(), log);
        System.out.println("HEADLESS_INSTALLER_CHILD_EXIT_CONFIRMED mode=" + mode[0] + " exitCode=" + child.exitValue());
        log.lines().filter(line -> line.startsWith("SYNTHETIC_NEXT_SESSION_")).forEach(System.out::println);
    }

    private static String quoteArgument(String argument) { return "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    static String childClasspath() throws Exception {
        Set<String> paths = new LinkedHashSet<>();
        // Gradle's worker java.class.path alone contains only its bootstrap. Use the actual test loader URLs.
        for (ClassLoader loader = TransactionalProcessCrashTest.class.getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) for (var url : urls.getURLs())
                if (url.getProtocol().equals("file")) paths.add(Path.of(url.toURI()).toString());
        }
        for (Class<?> anchor : List.of(TransactionalProcessCrashTest.class, TransactionalInstaller.class,
                AdapterCatalog.class, dev.legacybridge.runtime.SimpleContentPlan.class,
                dev.legacybridge.converter.report.CompatibilityReport.class,
                dev.legacybridge.workflow.BridgeWorkspace.class, org.objectweb.asm.ClassReader.class,
                org.objectweb.asm.util.CheckClassAdapter.class, org.objectweb.asm.tree.ClassNode.class,
                org.objectweb.asm.tree.analysis.Analyzer.class, org.objectweb.asm.commons.ClassRemapper.class,
                com.electronwill.nightconfig.core.Config.class, com.electronwill.nightconfig.toml.TomlParser.class,
                // Fixed runtime-only library, not an input mod; resolve its code source without initialization.
                Class.forName("net.minecraftforge.srgutils.IMappingBuilder", false, TransactionalProcessCrashTest.class.getClassLoader()),
                com.google.gson.Gson.class, org.slf4j.LoggerFactory.class)) {
            var source = anchor.getProtectionDomain().getCodeSource();
            if (source != null) paths.add(Path.of(source.getLocation().toURI()).toString());
        }
        assertFalse(paths.isEmpty());
        return String.join(File.pathSeparator, paths);
    }

    /** Deliberately killed helper; never invokes CLI.main or defines any fixture mod class. */
    public static final class Child {
        public static void main(String[] args) throws Exception {
            Path root = Path.of(args[0]).toAbsolutePath().normalize();
            if (args[1].equals("halt-published") || args[1].equals("halt-before-commit")) {
                var installer = args[1].equals("halt-published") ? halting(TransactionalInstaller.Boundary.PUBLISHED)
                        : new TransactionalInstaller((boundary, index) -> {
                            if (boundary == TransactionalInstaller.Boundary.BEFORE_COMMIT && index == -1)
                                Runtime.getRuntime().halt(81);
                        });
                installer.apply(root, List.of(observed(root, "crash_first", "1.0", Optional.empty()),
                        observed(root, "crash_second", "1.0", Optional.empty())), SESSION);
                throw new IllegalStateException("Publication halt boundary was not reached");
            }
            if (args[1].equals("halt-retired")) {
                halting(TransactionalInstaller.Boundary.RETIRED).apply(root, List.of(observed(root, "crash_update", "2.0",
                        Optional.of(root.resolve("legacybridge/transactions").resolve(args[2])))), SESSION);
                throw new IllegalStateException("Retirement halt boundary was not reached");
            }
            if (Set.of("resume-loaded", "restore-loaded-refused", "world-open-refused", "missing-loaded-refused").contains(args[1])) {
                recoverWithSyntheticNextSession(root, args);
                return;
            }
            if (!args[1].equals("recover")) throw new IllegalArgumentException("Unknown owned helper mode");
            Path transaction = root.resolve("legacybridge/transactions").resolve(args[2]);
            RecoveryAction action = RecoveryAction.valueOf(args[3]);
            var installer = new TransactionalInstaller();
            var before = installer.inspect(root, transaction, SESSION);
            if (before.safeToClose()) throw new IllegalStateException("Expected incomplete state before recovery");
            var result = installer.recover(root, transaction, SESSION, action);
            if (!result.safeToClose() || !result.status().name().equals(args[4])) throw new IllegalStateException("Recovery refused: " + result);
            if (!result.equals(installer.recover(root, transaction, SESSION, action))) throw new IllegalStateException("Recovery was not idempotent");
            if (!result.equals(installer.inspect(root, transaction, SESSION))) throw new IllegalStateException("Inspection differs from recovered state");
        }

        /** Supplied test snapshots, NOT actual Forge discovery, world opening or fixture class loading. */
        private static void recoverWithSyntheticNextSession(Path root, String[] args) throws Exception {
            Path transaction = root.resolve("legacybridge/transactions").resolve(args[2]);
            var installer = new TransactionalInstaller();
            var before = installer.inspect(root, transaction, SESSION);
            if (before.status() != Status.PARTIAL || before.safeToClose()) throw new IllegalStateException("Expected incomplete batch");
            Set<String> ids = new LinkedHashSet<>(SESSION.loadedModIds());
            Set<Path> paths = new LinkedHashSet<>();
            for (var entry : before.entries()) if (entry.installed().isPresent()) {
                if (!args[3].equals("FILES")) ids.add(entry.modId());
                if (!args[3].equals("IDS")) paths.add(args[1].equals("restore-loaded-refused")
                        ? root.resolve("loaded-file-alias.jar") : entry.installed().orElseThrow());
            }
            if (args[1].equals("missing-loaded-refused")) ids.add("crash_second");
            var session = new InstallSession(ids, paths, args[1].equals("world-open-refused"));
            if (session.worldOpen()) {
                try {
                    installer.recover(root, transaction, session, RecoveryAction.RESUME);
                    throw new IllegalStateException("Open-world recovery was accepted");
                } catch (InstallException refused) {
                    if (!refused.code().equals("INSTALL_MANUAL") || !refused.getMessage().contains("close the current world"))
                        throw new IllegalStateException("Wrong world-open refusal", refused);
                }
                System.out.println("SYNTHETIC_NEXT_SESSION_WORLD_OPEN_REFUSED");
                return;
            }
            RecoveryAction action = args[1].equals("restore-loaded-refused") ? RecoveryAction.RESTORE : RecoveryAction.RESUME;
            var result = installer.recover(root, transaction, session, action);
            if (!args[1].equals("resume-loaded")) {
                if (result.safeToClose() || result.status() != Status.PARTIAL || result.error().isEmpty()
                        || !result.error().orElseThrow().code().equals("TRANSACTION_REFUSED")
                        || !result.error().orElseThrow().message().contains("loaded mod"))
                    throw new IllegalStateException("Expected loaded-id/file refusal: " + result);
                System.out.println("SYNTHETIC_NEXT_SESSION_LOADED_REFUSED mode=" + args[3]);
                return;
            }
            if (!result.safeToClose() || result.status() != Status.COMMITTED) throw new IllegalStateException("Resume refused: " + result);
            if (!result.equals(installer.recover(root, transaction, session, action))) throw new IllegalStateException("Resume not idempotent");
            if (!result.equals(installer.inspect(root, transaction, session))) throw new IllegalStateException("Inspection differs");
            if (session.loadedModIds().contains("crash_second") != (before.entries().get(1).installed().isPresent()))
                throw new IllegalStateException("Recovery changed the supplied loaded-set snapshot");
            System.out.println("SYNTHETIC_NEXT_SESSION_RESUME_COMMITTED");
        }
        private static TransactionalInstaller halting(TransactionalInstaller.Boundary target) {
            return new TransactionalInstaller((boundary, index) -> { if (boundary == target && index == 0) Runtime.getRuntime().halt(81); });
        }
    }
}
