package dev.legacybridge.corpus;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CorpusLockTest {
    @TempDir Path temporary;
    Path corpus;
    Path first;

    @BeforeEach void prepare() throws Exception {
        corpus = Files.createDirectory(temporary.resolve("corpus"));
        Files.createDirectory(corpus.resolve("records"));
        Files.createDirectory(corpus.resolve("snapshots"));
        first = corpus.resolve("records/first.json");
        CorpusJson.writeCanonicalCreateNew(first, Map.of("ownedTestRecord", true, "revision", 1));
    }

    @Test void verifiesExactLockedVersionWithoutChangingAnyFile() throws Exception {
        locked(List.of("records/first.json"), Map.of("records/first.json", hash(first)));
        byte[] catalog = Files.readAllBytes(corpus.resolve("catalog.json"));
        byte[] snapshot = Files.readAllBytes(corpus.resolve("snapshots/v1.json"));
        byte[] record = Files.readAllBytes(first);
        assertDoesNotThrow(() -> CorpusLock.verify(corpus));
        assertArrayEquals(catalog, Files.readAllBytes(corpus.resolve("catalog.json")));
        assertArrayEquals(snapshot, Files.readAllBytes(corpus.resolve("snapshots/v1.json")));
        assertArrayEquals(record, Files.readAllBytes(first));
    }

    @Test void editingRecordWithoutNewVersionIsRefused() throws Exception {
        locked(List.of("records/first.json"), Map.of("records/first.json", hash(first)));
        Files.writeString(first, "{\"ownedTestRecord\":true,\"revision\":2}\n");
        rejected("record bytes differ");
    }

    @Test void evenWhitespaceChangesRequireMatchingVersionedRecordBytes() throws Exception {
        locked(List.of("records/first.json"), Map.of("records/first.json", hash(first)));
        Files.writeString(first, Files.readString(first) + "\n");
        rejected("record bytes differ");
    }

    @Test void addingCatalogRecordWithoutSnapshotRevisionIsRefused() throws Exception {
        locked(List.of("records/first.json"), Map.of("records/first.json", hash(first)));
        CorpusJson.writeCanonicalCreateNew(corpus.resolve("records/second.json"), Map.of("ownedTestRecord", true, "id", "second"));
        replaceCatalog("v1", List.of("records/first.json", "records/second.json"));
        rejected("record set differs");
    }

    @Test void removingCatalogRecordWithoutSnapshotRevisionIsRefused() throws Exception {
        locked(List.of("records/first.json"), Map.of("records/first.json", hash(first)));
        replaceCatalog("v1", List.of()); rejected("record set differs");
    }

    @Test void removedRecordFileCannotBeHistoricalMissingEvidence() throws Exception {
        locked(List.of("records/first.json"), Map.of("records/first.json", hash(first)));
        Files.delete(first);
        assertThrows(IOException.class, () -> CorpusLock.verify(corpus));
    }

    @Test void changingVersionWithoutProvidingItsSnapshotIsRefused() throws Exception {
        locked(List.of("records/first.json"), Map.of("records/first.json", hash(first)));
        replaceCatalog("v2", List.of("records/first.json"));
        assertThrows(IOException.class, () -> CorpusLock.verify(corpus));
    }

    @Test void staleVersionInsideSelectedSnapshotIsRefused() throws Exception {
        locked(List.of("records/first.json"), Map.of("records/first.json", hash(first)));
        Files.writeString(corpus.resolve("snapshots/v1.json"), CorpusJson.canonicalJson(Map.of("schemaVersion", 1,
                "corpusVersion", "v2", "records", Map.of("records/first.json", hash(first)))));
        rejected("snapshot version does not match");
    }

    @ParameterizedTest @ValueSource(strings = {"../outside.json", "/absolute.json", "C:/absolute.json", "records/../records/first.json", "records\\first.json", "records//first.json", "records/./first.json"})
    void refusesUnsafeOrAliasedCatalogRecordPaths(String path) throws Exception {
        locked(List.of(path), Map.of(path, hash(first)));
        rejected("Unsafe relative corpus path");
    }

    @Test void refusesNativeSymlinkRecordWhenCreationIsPermitted() throws Exception {
        Path alias = corpus.resolve("records/alias.json");
        try { Files.createSymbolicLink(alias, first.getFileName()); }
        catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
            Assumptions.abort("Native symlink support/creation permission unavailable on this test host: " + unavailable.getClass().getSimpleName());
        }
        locked(List.of("records/alias.json"), Map.of("records/alias.json", hash(first)));
        assertThrows(IOException.class, () -> CorpusLock.verify(corpus));
        assertTrue(Files.isSymbolicLink(alias)); assertTrue(Files.isRegularFile(first));
    }

    @Test void refusesDuplicateCatalogPaths() throws Exception {
        locked(List.of("records/first.json", "records/first.json"), Map.of("records/first.json", hash(first)));
        rejected("Duplicate catalog record path");
    }

    @Test void requiresLowercaseHashRatherThanLooselyMatchingStrings() throws Exception {
        locked(List.of("records/first.json"), Map.of("records/first.json", "A".repeat(64)));
        rejected("lowercase SHA-256");
    }

    @Test void refusesSnapshotSelfHash() throws Exception {
        locked(List.of("snapshots/v1.json"), Map.of("snapshots/v1.json", "a".repeat(64)));
        rejected("cannot be the catalog/snapshot itself");
    }

    @Test void rejectsMissingSnapshotRatherThanGeneratingOneImplicitly() throws Exception {
        CorpusJson.writeCanonicalCreateNew(corpus.resolve("catalog.json"), Map.of("schemaVersion", 1, "corpusVersion", "v1", "records", List.of("records/first.json")));
        assertThrows(IOException.class, () -> CorpusLock.verify(corpus));
        assertFalse(Files.exists(corpus.resolve("snapshots/v1.json")));
    }

    @Test void rejectsUnsafeVersionBeforeResolvingSnapshot() throws Exception {
        CorpusJson.writeCanonicalCreateNew(corpus.resolve("catalog.json"), Map.of("schemaVersion", 1, "corpusVersion", "../v1", "records", List.of("records/first.json")));
        rejected("Unsafe or missing corpus version");
    }

    @Test void rejectsOversizedRecordEvenIfSnapshotClaimsItsRealHash() throws Exception {
        Files.write(first, new byte[CorpusJson.MAX_BYTES + 1]);
        locked(List.of("records/first.json"), Map.of("records/first.json", hash(first)));
        rejected("exceeds the 2 MiB lock limit");
    }

    private void locked(List<String> records, Map<String, String> hashes) throws IOException {
        CorpusJson.writeCanonicalCreateNew(corpus.resolve("catalog.json"), Map.of("schemaVersion", 1, "corpusVersion", "v1", "records", records));
        CorpusJson.writeCanonicalCreateNew(corpus.resolve("snapshots/v1.json"), Map.of("schemaVersion", 1, "corpusVersion", "v1", "records", hashes));
    }
    private void replaceCatalog(String version, List<String> records) throws IOException {
        Files.writeString(corpus.resolve("catalog.json"), CorpusJson.canonicalJson(Map.of("schemaVersion", 1, "corpusVersion", version, "records", records)));
    }
    private void rejected(String message) { assertTrue(assertThrows(IOException.class, () -> CorpusLock.verify(corpus)).getMessage().contains(message)); }
    private static String hash(Path path) throws IOException { return CorpusRunner.hash(path); }
}
