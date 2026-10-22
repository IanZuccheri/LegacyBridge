package dev.legacybridge.validation.composition;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic files only: no Forge, Minecraft, fixture class loading, or native execution. */
class ProbeFilesTest {
    @TempDir Path temporary;
    Path directory;
    byte[] observation = "{\"note\":\"ol\u00e1\",\"schemaVersion\":1}\n".getBytes(StandardCharsets.UTF_8);

    @BeforeEach void setup() throws IOException {
        directory = Files.createDirectory(temporary.resolve("composition-observations"));
    }

    private List<Path> entries() throws IOException {
        try (Stream<Path> files = Files.list(directory)) { return files.collect(Collectors.toList()); }
    }

    private String publish(ProbeFiles.Writer writer) throws IOException {
        return ProbeFiles.publish(directory, "run-unit", observation, writer);
    }

    @Test void publishesExactBytesAndDigestWithoutPrivateRemainders() throws Exception {
        String hash = ProbeFiles.publish(directory, "run-unit", observation);
        Path result = directory.resolve("run-unit.json");
        assertEquals(1, entries().size());
        assertArrayEquals(observation, Files.readAllBytes(result));
        assertEquals(ProbeFiles.sha256(result), hash);
        assertTrue(hash.matches("[a-f0-9]{64}"));
    }

    @Test void knownSha256IsCalculatedWithoutExecutingContent() throws Exception {
        Path file = Files.write(temporary.resolve("mod.jar"), "abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ProbeFiles.sha256(file));
        Path empty = Files.createFile(temporary.resolve("empty"));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ProbeFiles.sha256(empty));
    }

    @Test void oversizedHashInputIsRejectedBeforeStreamingIt() throws Exception {
        Path file = temporary.resolve("oversized.jar");
        try (RandomAccessFile sparse = new RandomAccessFile(file.toFile(), "rw")) {
            sparse.setLength(ProbeFiles.MAX_HASH_BYTES + 1);
        }
        assertTrue(assertThrows(IOException.class, () -> ProbeFiles.sha256(file)).getMessage().contains("OBS_HASH_SIZE_LIMIT"));
    }

    @Test void hashRefusesRelativeUnnormalizedMissingDirectoryAndNullInputs() throws Exception {
        assertThrows(IOException.class, () -> ProbeFiles.sha256(Paths.get("mod.jar")));
        assertThrows(IOException.class, () -> ProbeFiles.sha256(temporary.resolve("x/../mod.jar")));
        assertThrows(IOException.class, () -> ProbeFiles.sha256(temporary.resolve("missing.jar")));
        assertThrows(IOException.class, () -> ProbeFiles.sha256(directory));
        assertThrows(IOException.class, () -> ProbeFiles.sha256(null));
    }

    @Test void existingOutputIsNeverOverwritten() throws Exception {
        Path result = Files.write(directory.resolve("run-unit.json"), new byte[]{7});
        assertThrows(FileAlreadyExistsException.class, () -> publish(ProbeFiles::write));
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(result));
        assertEquals(1, entries().size());
    }

    @Test void existingDirectoryAtDestinationIsNotOverwritten() throws Exception {
        Path result = Files.createDirectory(directory.resolve("run-unit.json"));
        Files.write(result.resolve("user.txt"), new byte[]{7});
        assertThrows(FileAlreadyExistsException.class, () -> publish(ProbeFiles::write));
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(result.resolve("user.txt")));
    }

    @Test void lateDestinationConflictPreservesUnknownFileAndCleansOwnedPrivateFile() throws Exception {
        assertThrows(FileAlreadyExistsException.class, () -> publish((file, bytes) -> {
            ProbeFiles.write(file, bytes);
            Files.write(directory.resolve("run-unit.json"), new byte[]{7}, StandardOpenOption.CREATE_NEW);
        }));
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(directory.resolve("run-unit.json")));
        assertEquals(1, entries().size());
    }

    @Test void failedWriteCleansOnlyOwnedPrivateFiles() throws Exception {
        Path unknown = Files.write(directory.resolve("unrelated.json"), new byte[]{7});
        IOException failure = assertThrows(IOException.class, () -> publish((file, bytes) -> {
            ProbeFiles.write(file, new byte[]{1});
            throw new IOException("injected disk-full/permission/locked-write failure");
        }));
        assertTrue(failure.getMessage().contains("injected"));
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(1, entries().size());
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(unknown));
    }

    @Test void failureBeforeWritingCleansOwnedEmptyFile() throws Exception {
        assertThrows(IOException.class, () -> publish((file, bytes) -> { throw new IOException("injected open failure"); }));
        assertTrue(entries().isEmpty());
    }

    @Test void runtimeFailureAlsoCleansOwnedFilesWithoutHidingTheFailure() throws Exception {
        assertThrows(IllegalStateException.class, () -> publish((file, bytes) -> { throw new IllegalStateException("injected"); }));
        assertTrue(entries().isEmpty());
    }

    @Test void truncatedWriterIsNotPublished() throws Exception {
        IOException failure = assertThrows(IOException.class, () -> publish((file, bytes) -> ProbeFiles.write(file, new byte[]{1})));
        assertTrue(failure.getMessage().contains("OBS_WRITE_MISMATCH"));
        assertTrue(entries().isEmpty());
    }

    @Test void byteIdenticalReplacementIsNotDeletedOrPublished() throws Exception {
        AtomicReference<Path> replaced = new AtomicReference<Path>();
        IOException failure = assertThrows(IOException.class, () -> publish((file, bytes) -> {
            Files.delete(file);
            Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
            replaced.set(file);
        }));
        assertTrue(failure.getMessage().contains("OBS_FILE_REPLACED"));
        assertArrayEquals(observation, Files.readAllBytes(replaced.get()));
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("OBS_PRIVATE_REMAINDER"));
        assertFalse(Files.exists(directory.resolve("run-unit.json")));
    }

    @Test void missingWitnessRetainsPrivateFileAndMakesCleanupFailureExplicit() throws Exception {
        AtomicReference<Path> pending = new AtomicReference<Path>();
        IOException failure = assertThrows(IOException.class, () -> publish((file, bytes) -> {
            ProbeFiles.write(file, bytes);
            pending.set(file);
            for (Path entry : entries()) {
                if (entry.getFileName().toString().startsWith(".obs-owner-")) Files.delete(entry);
            }
        }));
        assertArrayEquals(observation, Files.readAllBytes(pending.get()));
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("OBS_PRIVATE_REMAINDER"));
        assertFalse(Files.exists(directory.resolve("run-unit.json")));
    }

    @Test void callerMutationCannotChangeTheCapturedSnapshot() throws Exception {
        byte[] expected = observation.clone();
        publish((file, bytes) -> {
            observation[0] = 'X';
            ProbeFiles.write(file, bytes);
        });
        assertArrayEquals(expected, Files.readAllBytes(directory.resolve("run-unit.json")));
    }

    @Test void oversizedAndNullJsonAreRejectedBeforeAnyPrivateFile() throws Exception {
        assertThrows(IOException.class, () -> ProbeFiles.publish(directory, "run-test", new byte[ProbeFiles.MAX_JSON_BYTES + 1]));
        assertThrows(IOException.class, () -> ProbeFiles.publish(directory, "run-test", null));
        assertTrue(entries().isEmpty());
    }

    @Test void exactJsonByteLimitIsAcceptedWithoutClaimingSemanticSchemaValidation() throws Exception {
        byte[] bytes = new byte[ProbeFiles.MAX_JSON_BYTES];
        java.util.Arrays.fill(bytes, (byte) ' ');
        ProbeFiles.publish(directory, "run-limit", bytes);
        assertEquals(ProbeFiles.MAX_JSON_BYTES, Files.size(directory.resolve("run-limit.json")));
    }

    @Test void invalidUtf8SequencesAreRejectedBeforeAnyPrivateFile() throws Exception {
        byte[][] invalid = {{(byte) 0xc3, 0x28}, {(byte) 0xc0, (byte) 0x80},
                {(byte) 0xed, (byte) 0xa0, (byte) 0x80}, {(byte) 0xe2, (byte) 0x82},
                {(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80}};
        for (byte[] bytes : invalid) {
            IOException failure = assertThrows(IOException.class, () -> ProbeFiles.publish(directory, "run-invalid", bytes));
            assertTrue(failure.getMessage().contains("OBS_UTF8"));
        }
        assertTrue(entries().isEmpty());
    }

    @Test void malformedRunIdsAreRejectedBeforeAnyPrivateFile() throws Exception {
        String[] invalid = {null, "", "run-", "run-UPPER", "../escape", "run-a/b", "run-a\\b", "run-a.",
                "run-abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"};
        for (String id : invalid) assertThrows(IOException.class, () -> ProbeFiles.publish(directory, id, observation));
        assertTrue(entries().isEmpty());
    }

    @Test void outputRequiresExistingAbsoluteCanonicalDirectoryWithExactLeaf() throws Exception {
        Path other = Files.createDirectory(temporary.resolve("other"));
        Path absent = other.resolve("composition-observations");
        assertThrows(IOException.class, () -> ProbeFiles.publish(absent, "run-test", observation));
        assertFalse(Files.exists(absent));
        assertThrows(IOException.class, () -> ProbeFiles.publish(other, "run-test", observation));
        assertThrows(IOException.class, () -> ProbeFiles.publish(null, "run-test", observation));
        assertThrows(IOException.class, () -> ProbeFiles.publish(Paths.get("composition-observations"), "run-test", observation));
        assertThrows(IOException.class, () -> ProbeFiles.publish(directory.resolve("x/../"), "run-test", observation));
        assertThrows(IOException.class, () -> ProbeFiles.publish(directory.getRoot(), "run-test", observation));
        Path fake = Files.write(absent, new byte[]{7});
        assertThrows(IOException.class, () -> ProbeFiles.publish(fake, "run-test", observation));
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(fake));
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void genuineWindowsJunctionCannotAliasOutputDirectoryOrHashParents() throws Exception {
        Path other = Files.createDirectory(temporary.resolve("alias-parent"));
        Path alias = other.resolve("composition-observations");
        Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", alias.toString(), directory.toString())
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), "Creating the local test-only junction must succeed");
        try {
            Files.write(directory.resolve("source.jar"), new byte[]{1});
            assertThrows(IOException.class, () -> ProbeFiles.publish(alias, "run-junction", observation));
            assertThrows(IOException.class, () -> ProbeFiles.sha256(alias.resolve("source.jar")));
            assertFalse(Files.exists(directory.resolve("run-junction.json")));
        } finally {
            Files.delete(alias); // Delete the verified junction itself, never recursively traverse its target.
        }
    }
}
