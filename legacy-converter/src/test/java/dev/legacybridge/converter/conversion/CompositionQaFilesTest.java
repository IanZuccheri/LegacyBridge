package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.operation.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic bytes exercise only the dev filesystem contract; these are NOT ASM or semantic conversion tests. */
class CompositionQaFilesTest {
    @TempDir Path directory;
    Path root, input;
    byte[] candidate = {9, 8, 7};
    String sourceSha, candidateSha;
    @BeforeEach void setup() throws Exception {
        root = Files.createDirectory(directory.resolve("composition-qa"));
        input = Files.write(directory.resolve("source.jar"), new byte[]{1, 2, 3});
        sourceSha = CompositionQaFiles.hashFile(input); candidateSha = SimpleContentConverter.hash(candidate);
    }
    Path publish(CompositionQaFiles.Writer writer) throws Exception {
        return CompositionQaFiles.publish(input, sourceSha, candidateSha, root, "run-unit", candidate,
                Map.of("scope", "SYNTHETIC_FILES_ONLY"), writer);
    }
    void emptyRoot() throws Exception { try (var files = Files.list(root)) { assertEquals(List.of(), files.toList()); } }
    @Test void exportsClosedFilesAndReceiptWithDistinctNonInstallableStatus() throws Exception {
        var result = publish(CompositionQaFiles::write);
        try (var entries = Files.list(result)) { assertEquals(Set.of("candidate.jar", "proof.json", "receipt.json"), entries.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet())); }
        assertArrayEquals(candidate, Files.readAllBytes(result.resolve("candidate.jar")));
        var receipt = (Map<?, ?>) BoundedJsonParser.parse(Files.readString(result.resolve("receipt.json")));
        assertEquals("EXPORTED_FOR_QA", receipt.get("status")); assertEquals(false, receipt.get("installationAllowed"));
        assertEquals("NOT_RUN", receipt.get("runtimeValidation")); assertEquals(sourceSha, receipt.get("sourceSha256"));
        assertEquals(candidateSha, receipt.get("candidateSha256"));
        assertEquals(CompositionQaFiles.hashFile(result.resolve("proof.json")), receipt.get("proofSha256"));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(input));
        try (var entries = Files.list(root)) { assertEquals(List.of(result), entries.toList()); }
    }
    @ParameterizedTest @ValueSource(strings = {"candidate.jar", "proof.json", "receipt.json"})
    void partialWriteFailsWithoutExportAndCleansOnlyOwnedFiles(String at) throws Exception {
        assertThrows(IOException.class, () -> publish((path, bytes) -> {
            if (path.getFileName().toString().equals(at)) { CompositionQaFiles.write(path, new byte[]{1}); throw new IOException("injected disk-full/locked-write"); }
            CompositionQaFiles.write(path, bytes);
        })); emptyRoot();
    }
    @Test void writeMismatchIsNotPublished() throws Exception {
        assertThrows(IOException.class, () -> publish((p, b) -> CompositionQaFiles.write(p, new byte[]{99}))); emptyRoot();
    }
    @Test void earlierFileChangedByLaterWriteFailsFinalRecheck() throws Exception {
        assertThrows(IOException.class, () -> publish((p, b) -> {
            CompositionQaFiles.write(p, b);
            if (p.getFileName().toString().equals("receipt.json")) Files.write(p.resolveSibling("candidate.jar"), new byte[]{3});
        })); emptyRoot();
    }
    @Test void changedOriginalAfterSerializationBlocksExport() throws Exception {
        assertThrows(IOException.class, () -> publish((p, b) -> {
            CompositionQaFiles.write(p, b);
            if (p.getFileName().toString().equals("receipt.json")) Files.write(input, new byte[]{42});
        })); emptyRoot(); assertArrayEquals(new byte[]{42}, Files.readAllBytes(input));
    }
    @Test void existingRunAndUnknownContentsAreNeverOverwritten() throws Exception {
        Path existing = Files.createDirectory(root.resolve("run-unit")); Path sentinel = Files.writeString(existing.resolve("unknown.jar"), "user data");
        assertThrows(FileAlreadyExistsException.class, () -> publish(CompositionQaFiles::write));
        assertEquals("user data", Files.readString(sentinel));
    }
    @Test void lateDestinationConflictPreservesUnknownDestination() throws Exception {
        assertThrows(FileAlreadyExistsException.class, () -> publish((p, b) -> {
            CompositionQaFiles.write(p, b);
            if (p.getFileName().toString().equals("receipt.json")) Files.writeString(root.resolve("run-unit"), "user");
        })); assertEquals("user", Files.readString(root.resolve("run-unit")));
        try (var files = Files.list(root)) { assertEquals(1, files.count()); }
    }
    @Test void unknownFileInPendingIsRetainedAndCleanupErrorVisible() throws Exception {
        AtomicReference<Path> unknown = new AtomicReference<>();
        var failure = assertThrows(IOException.class, () -> publish((p, b) -> {
            CompositionQaFiles.write(p, b);
            if (p.getFileName().toString().equals("receipt.json")) unknown.set(Files.writeString(p.resolveSibling("unknown"), "preserve"));
        }));
        assertEquals("preserve", Files.readString(unknown.get())); assertTrue(failure.getSuppressed().length > 0);
        assertFalse(Files.exists(root.resolve("run-unit")));
    }
    @Test void byteIdenticalReplacementCannotProveOwnershipForCleanup() throws Exception {
        AtomicReference<Path> replacement = new AtomicReference<>();
        var failure = assertThrows(IOException.class, () -> publish((p, b) -> {
            Files.delete(p); Files.write(p, b, StandardOpenOption.CREATE_NEW); replacement.set(p);
        }));
        assertTrue(failure.getMessage().contains("QA_FILE_REPLACED")); assertTrue(Files.exists(replacement.get()));
        assertTrue(failure.getSuppressed().length > 0); assertFalse(Files.exists(root.resolve("run-unit")));
    }
    @ParameterizedTest @ValueSource(strings = {"candidate.jar", "proof.json", "receipt.json", "PRE_PUBLICATION"})
    void acceptedCancellationDoesNotExport(String at) throws Exception {
        AtomicReference<OperationControl> ref = new AtomicReference<>();
        var control = new OperationControl(OperationLimits.defaults(), p -> {
            if (at.equals("PRE_PUBLICATION") && p.stage() == OperationStage.PRE_PUBLICATION) ref.get().requestCancel();
        }); ref.set(control);
        try (var scope = control.enter()) {
            assertThrows(OperationStoppedException.class, () -> publish((p, b) -> {
                CompositionQaFiles.write(p, b);
                if (p.getFileName().toString().equals(at)) assertTrue(control.requestCancel());
            }));
        }
        emptyRoot();
    }
    @Test void cancelAfterPublicationBoundaryIsRefusedNotCalledSuccessful() throws Exception {
        AtomicReference<OperationControl> ref = new AtomicReference<>(); List<Boolean> accepted = new ArrayList<>();
        var control = new OperationControl(OperationLimits.defaults(), p -> {
            if (p.stage() == OperationStage.PUBLICATION) accepted.add(ref.get().requestCancel());
        }); ref.set(control);
        try (var scope = control.enter()) { assertTrue(Files.isDirectory(publish(CompositionQaFiles::write))); control.complete(); }
        assertEquals(List.of(false), accepted);
    }
    @Test void failedFinalMoveRetainsPrivateRemainderAndPreservesConflictingFile() throws Exception {
        var control = new OperationControl(OperationLimits.defaults(), p -> {
            if (p.stage() == OperationStage.PUBLICATION) {
                try { Files.writeString(root.resolve("run-unit"), "unknown winner", StandardOpenOption.CREATE_NEW); }
                catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        });
        try (var scope = control.enter()) {
            var failure = assertThrows(FileAlreadyExistsException.class, () -> publish(CompositionQaFiles::write));
            assertTrue(Arrays.stream(failure.getSuppressed()).anyMatch(e -> e.getMessage().contains("QA_PRIVATE_REMAINDER")));
        }
        assertEquals("unknown winner", Files.readString(root.resolve("run-unit")));
        try (var files = Files.list(root)) {
            var pending = files.filter(p -> p.getFileName().toString().startsWith(".pending-")).findFirst().orElseThrow();
            assertArrayEquals(candidate, Files.readAllBytes(pending.resolve("candidate.jar")));
        }
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void genuineWindowsJunctionCannotAliasQaRootOrSourceParents() throws Exception {
        Path aliasParent = Files.createDirectory(directory.resolve("alias")); Path alias = aliasParent.resolve("composition-qa");
        var process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", alias.toString(), root.toString()).redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> CompositionQaFiles.preflightRoot(alias, "run-test"));
        assertThrows(IOException.class, () -> CompositionQaFiles.requireInput(alias.resolve("not-read.jar"), sourceSha));
        emptyRoot();
    }
    @ParameterizedTest @ValueSource(strings = {"../escape", "run-", "CON", "run-UPPER", "run-one/two", "run-trailing."})
    void unsafeRunIdRejected(String runId) { assertThrows(IllegalArgumentException.class, () -> CompositionQaFiles.preflightRoot(root, runId)); }
    @ParameterizedTest @ValueSource(strings = {"mods", "staging", "input", "transactions", "saves"})
    void gameDirectoriesRefusedBeforeEvenCreatingQaRoot(String name) throws Exception {
        Path parent = Files.createDirectory(directory.resolve(name)); Path forbidden = parent.resolve("composition-qa");
        assertThrows(IOException.class, () -> CompositionQaFiles.ensureRoot(forbidden, "run-test")); assertFalse(Files.exists(forbidden));
    }
    @Test void invalidHashAndCandidateMismatchRejectedWithoutPrivateFiles() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> CompositionQaFiles.requireHash("0"));
        assertThrows(IllegalArgumentException.class, () -> CompositionQaFiles.requireHash("F".repeat(64)));
        candidateSha = "0".repeat(64); assertThrows(IOException.class, () -> publish(CompositionQaFiles::write)); emptyRoot();
    }
    @Test void rootMustBeRealDirectoryAndNormalized() throws Exception {
        assertThrows(IOException.class, () -> CompositionQaFiles.ensureRoot(directory.getRoot(), "run-test"));
        assertThrows(IOException.class, () -> CompositionQaFiles.preflightRoot(Path.of("composition-qa"), "run-test"));
        assertThrows(IOException.class, () -> CompositionQaFiles.preflightRoot(root.resolve("x/../"), "run-test"));
        Path parent = Files.createDirectory(directory.resolve("other")); Path file = Files.writeString(parent.resolve("composition-qa"), "user");
        assertThrows(IOException.class, () -> CompositionQaFiles.ensureRoot(file, "run-test")); assertEquals("user", Files.readString(file));
    }
}
