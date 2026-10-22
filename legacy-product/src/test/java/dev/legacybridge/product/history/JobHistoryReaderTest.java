package dev.legacybridge.product.history;

import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.product.controller.JobState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import com.sun.nio.file.ExtendedOpenOption;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static dev.legacybridge.product.history.JobHistory.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic inert receipt files only; no game, discovery or journal reconciliation. */
class JobHistoryReaderTest {
    @TempDir Path game;
    private final UUID job = UUID.randomUUID();
    private final UUID staging = UUID.randomUUID();
    private final UUID transaction = UUID.randomUUID();
    private final UUID directory = UUID.randomUUID();

    @Test void missingWorkspaceIsEmptyWithoutCreatingAnything() throws Exception {
        assertEquals(List.of(), children(game));
        var result = read();
        assertEquals(Status.COMPLETE, result.status()); assertTrue(result.jobs().isEmpty());
        assertEquals(List.of(), children(game)); assertEquals(0, result.bytesRead());
        assertThrows(UnsupportedOperationException.class, () -> result.jobs().clear());
    }

    @Test void schemaTwoOrdersOnlyBySequenceAndKeepsPathsInertAndOriginalBytes() throws Exception {
        var first = receipt(2, 1); var second = receipt(2, 2);
        second.put("state", "PENDING_RESTART"); second.put("event", "INSTALLATION");
        second.put("installed", game.resolve("mods/managed.jar").toString());
        second.put("transaction", transactionPath()); second.put("installation", observation());
        Path newer = write(second); Path older = write(first);
        Files.setLastModifiedTime(older, FileTime.fromMillis(System.currentTimeMillis() + 60000));
        byte[] original = Files.readAllBytes(newer);
        var result = read(); assertEquals(Status.COMPLETE, result.status(), result.issues().toString());
        var history = result.jobs().get(0);
        assertEquals(Ordering.SEQUENCED, history.ordering());
        assertEquals(List.of(1, 2), history.receipts().stream().map(r -> r.sequence().orElseThrow()).toList());
        var highest = history.highestRecorded().orElseThrow();
        assertEquals(JobState.PENDING_RESTART, highest.recordedState());
        assertEquals(Activation.NOT_OBSERVED, highest.activation());
        assertEquals("mods/managed.jar", highest.installed().orElseThrow());
        assertEquals(Action.APPLY, highest.installation().orElseThrow().action());
        assertFalse(Files.exists(game.resolve("mods"))); assertFalse(Files.exists(game.resolve("legacybridge/staging")));
        assertArrayEquals(original, Files.readAllBytes(newer));
        assertThrows(UnsupportedOperationException.class, () -> history.receipts().clear());
    }

    @Test void schemaOneCannotInventOrderOrActivationAndIsNotMigrated() throws Exception {
        Path file = write(receipt(1, 0)); byte[] original = Files.readAllBytes(file);
        write(receipt(1, 0));
        var result = read(); assertEquals(Status.COMPLETE, result.status());
        var history = result.jobs().get(0); assertEquals(Ordering.LEGACY_UNORDERED, history.ordering());
        assertTrue(history.highestRecorded().isEmpty()); assertEquals(2, history.receipts().size());
        assertEquals(Event.LEGACY_UNSPECIFIED, history.receipts().get(0).event());
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @ParameterizedTest @ValueSource(strings = {"DUPLICATE", "GAP", "MIXED", "IDENTITY"})
    void ambiguousGroupsNeverUseLastWins(String defect) throws Exception {
        write(receipt(2, 1));
        var next = receipt(defect.equals("MIXED") ? 1 : 2, defect.equals("GAP") ? 3 : defect.equals("DUPLICATE") ? 1 : 2);
        if (defect.equals("IDENTITY")) next.put("inputSha256", "b".repeat(64));
        write(next);
        var result = read(); assertEquals(Status.PARTIAL, result.status());
        assertEquals(Ordering.AMBIGUOUS, result.jobs().get(0).ordering());
        assertTrue(result.jobs().get(0).highestRecorded().isEmpty());
        Code code = switch (defect) {
            case "DUPLICATE" -> Code.DUPLICATE_SEQUENCE;
            case "GAP" -> Code.SEQUENCE_GAP;
            case "MIXED" -> Code.MIXED_SCHEMAS;
            default -> Code.IDENTITY_CONFLICT;
        };
        has(result, code);
    }

    @ParameterizedTest @ValueSource(strings = {"FUTURE_SCHEMA", "UNKNOWN_FIELD", "BAD_HASH", "STRING_SEQUENCE",
            "FRACTIONAL_SEQUENCE", "UNKNOWN_STATE", "ACTIVATED", "UNKNOWN_EVENT", "MISSING_INSTALLATION",
            "UNKNOWN_ACTION", "STRING_BOOLEAN", "BAD_INDEX", "EMPTY_INPUT", "UNPAIRED_SURROGATE"})
    void rejectsMalformedTypedReceiptWithoutHidingItBehindAnOlderSuccess(String defect) throws Exception {
        write(receipt(2, 1)); var invalid = receipt(2, 2);
        switch (defect) {
            case "FUTURE_SCHEMA" -> invalid.put("schemaVersion", 3);
            case "UNKNOWN_FIELD" -> invalid.put("autoApply", true);
            case "BAD_HASH" -> invalid.put("inputSha256", "123");
            case "STRING_SEQUENCE" -> invalid.put("sequence", "2");
            case "FRACTIONAL_SEQUENCE" -> invalid.put("sequence", 2.5);
            case "UNKNOWN_STATE" -> invalid.put("state", "WORKS_IN_GAME");
            case "ACTIVATED" -> invalid.put("activation", "ACTIVE");
            case "UNKNOWN_EVENT" -> invalid.put("event", "LEGACY_UNSPECIFIED");
            case "MISSING_INSTALLATION" -> invalid.put("event", "INSTALLATION");
            case "UNKNOWN_ACTION", "STRING_BOOLEAN", "BAD_INDEX" -> {
                var observation = observation(); invalid.put("event", "INSTALLATION"); invalid.put("installation", observation);
                if (defect.equals("UNKNOWN_ACTION")) observation.put("action", "RESTART");
                if (defect.equals("STRING_BOOLEAN")) observation.put("transactionSafeToClose", "true");
                if (defect.equals("BAD_INDEX")) observation.put("entryIndex", -1);
            }
            case "EMPTY_INPUT" -> invalid.put("input", "");
            case "UNPAIRED_SURROGATE" -> invalid.put("error", Map.of("code", "X", "message", "placeholder", "remediation", "Inspect"));
            default -> fail(defect);
        }
        Path file = write(invalid);
        if (defect.equals("UNPAIRED_SURROGATE")) Files.writeString(file, Files.readString(file).replace("placeholder", "\\uD800"));
        var result = read(); assertEquals(Status.PARTIAL, result.status());
        assertEquals(1, result.jobs().get(0).receipts().size());
        assertTrue(result.jobs().get(0).highestRecorded().isEmpty()); assertFalse(result.issues().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"CORRUPT", "DUPLICATE_KEY", "UTF8", "OVERSIZED", "DIRECTORY", "BAD_NAME", "DEEP_JSON"})
    void diagnosesUntrustedFilesAndKeepsOtherReceipts(String defect) throws Exception {
        write(receipt(2, 1)); Path file = nextFile();
        switch (defect) {
            case "CORRUPT" -> Files.writeString(file, "{broken");
            case "DUPLICATE_KEY" -> Files.writeString(file, "{\"schemaVersion\":1,\"schemaVersion\":2}");
            case "UTF8" -> Files.write(file, new byte[] {(byte) 0xc3, (byte) 0x28});
            case "OVERSIZED" -> Files.write(file, new byte[65537]);
            case "DIRECTORY" -> Files.createDirectory(file);
            case "BAD_NAME" -> Files.writeString(file.resolveSibling("job-nope.json"), "{}");
            case "DEEP_JSON" -> Files.writeString(file, "[".repeat(70) + "0" + "]".repeat(70));
            default -> fail(defect);
        }
        var result = read(); assertEquals(Status.PARTIAL, result.status());
        assertEquals(1, result.jobs().size()); assertTrue(result.jobs().get(0).highestRecorded().isEmpty());
        has(result, switch (defect) {
            case "UTF8" -> Code.INVALID_UTF8;
            case "OVERSIZED" -> Code.FILE_TOO_LARGE;
            case "DIRECTORY" -> Code.PATH_UNSAFE;
            case "BAD_NAME" -> Code.INVALID_NAME;
            default -> Code.INVALID_JSON;
        });
    }

    @ParameterizedTest @ValueSource(strings = {"OUTSIDE", "TRAVERSAL", "RELATIVE", "WRONG_AREA", "ADS", "DEVICE", "TRAILING_DOT", "FORMAT_CONTROL"})
    void rejectsUnsafeRecordedPathsAndDoesNotReadTheirContents(String defect) throws Exception {
        var value = receipt(2, 1);
        switch (defect) {
            case "OUTSIDE" -> value.put("installed", game.getParent().resolve("foreign.jar").toString());
            case "TRAVERSAL" -> value.put("installed", game.resolve("mods/../foreign.jar").toString());
            case "RELATIVE" -> value.put("installed", "mods/x.jar");
            case "WRONG_AREA" -> value.put("artifact", game.resolve("mods/x.jar").toString());
            case "ADS" -> value.put("input", "source.jar:stream");
            case "DEVICE" -> value.put("input", "CON.jar");
            case "TRAILING_DOT" -> value.put("input", "source.jar.");
            case "FORMAT_CONTROL" -> value.put("input", "source\u202e.jar");
            default -> fail(defect);
        }
        write(value); var result = read(); has(result, Code.PATH_UNSAFE); assertTrue(result.jobs().isEmpty());
    }

    @Test void budgetsBoundIoEnumerationDiagnosticsAndNeverSelectLatestFromPartialScan() throws Exception {
        Path file = write(receipt(2, 1)); write(receipt(2, 2));
        var one = new JobHistoryReader(new JobHistoryReader.Limits(1024, 1, 65536, 2097152, 64, 10000)).read(game, () -> false);
        has(one, Code.FILE_LIMIT); assertEquals(1, one.receiptsExamined());
        assertTrue(one.jobs().stream().allMatch(j -> j.highestRecorded().isEmpty()));
        var entries = new JobHistoryReader(new JobHistoryReader.Limits(1, 128, 65536, 2097152, 64, 10000)).read(game, () -> false);
        has(entries, Code.ENTRY_LIMIT); assertEquals(1, entries.entriesVisited());
        var bytes = new JobHistoryReader(new JobHistoryReader.Limits(1024, 128, 65536, 10, 64, 10000)).read(game, () -> false);
        has(bytes, Code.BYTE_LIMIT); assertEquals(0, bytes.bytesRead());
        Files.writeString(file, "broken"); Files.writeString(nextFile(), "broken");
        var issues = new JobHistoryReader(new JobHistoryReader.Limits(1024, 128, 65536, 2097152, 1, 10000)).read(game, () -> false);
        assertEquals(1, issues.issues().size()); assertTrue(issues.omittedIssues() >= 1);
        assertThrows(IllegalArgumentException.class, () -> new JobHistoryReader.Limits(9999, 128, 65536, 2097152, 64, 10000));
    }

    @Test void unrelatedReportsAreNotParsedButStillConsumeEntryBudget() throws Exception {
        Path file = write(receipt(2, 1));
        Files.writeString(file.resolveSibling("conversion.json"), "not a job");
        Files.writeString(file.resolveSibling(".legacybridge-report-unfinished.tmp"), "unfinished");
        var result = read(); assertEquals(Status.COMPLETE, result.status());
        assertEquals(4, result.entriesVisited()); assertEquals(1, result.receiptsExamined());
        assertEquals(Files.size(file), result.bytesRead());
    }

    @Test void cancellationAndDeadlineDiscardAnyClaimOfLatestWithoutWriting() throws Exception {
        Path file = write(receipt(2, 1)); byte[] before = Files.readAllBytes(file);
        var immediate = new JobHistoryReader().read(game, () -> true);
        assertEquals(Status.CANCELLED, immediate.status()); assertTrue(immediate.jobs().isEmpty());
        AtomicInteger checks = new AtomicInteger();
        var during = new JobHistoryReader().read(game, () -> checks.incrementAndGet() >= 10);
        assertEquals(Status.CANCELLED, during.status()); assertTrue(during.jobs().isEmpty());
        var deadline = new JobHistoryReader(new JobHistoryReader.Limits(1024, 128, 65536, 2097152, 64, 1)).read(game, () -> {
            java.util.concurrent.locks.LockSupport.parkNanos(5000000); return false;
        });
        has(deadline, Code.TIME_LIMIT); assertEquals(Status.PARTIAL, deadline.status());
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    @Test void fileHardLinkCannotCountAsIndependentReceipt() throws Exception {
        Path file = write(receipt(2, 1)); Files.createLink(nextFile(), file);
        var result = read(); has(result, Code.ALIASED_RECEIPT); assertEquals(Status.PARTIAL, result.status());
        assertTrue(result.jobs().get(0).highestRecorded().isEmpty());
    }

    @Test void wrongReservedDirectoryTypeFailsWithoutTouchingIt() throws Exception {
        Files.writeString(game.resolve("legacybridge"), "sentinel");
        var result = read(); assertEquals(Status.FAILED, result.status()); has(result, Code.WORKSPACE_UNSAFE);
        assertEquals("sentinel", Files.readString(game.resolve("legacybridge")));
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void actualDenyReadHandleIsReportedAndRefreshSucceedsAfterRelease() throws Exception {
        Path file = write(receipt(2, 1)); byte[] original = Files.readAllBytes(file);
        try (var held = FileChannel.open(file, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_READ)) {
            assertTrue(held.isOpen());
            var result = read(); assertEquals(Status.PARTIAL, result.status()); has(result, Code.READ_FAILED);
            assertTrue(result.jobs().isEmpty());
        }
        assertEquals(Status.COMPLETE, read().status()); assertArrayEquals(original, Files.readAllBytes(file));
    }

    @ParameterizedTest @ValueSource(strings = {"REPORT_OPERATION", "ARTIFACT_PARENT", "RESERVED_REPORTS"})
    @EnabledOnOs(OS.WINDOWS)
    void actualWindowsJunctionsAreRejectedWithoutFollowingTheirContents(String kind) throws Exception {
        Path target = Files.createDirectory(game.resolve("foreign"));
        Path sentinel = Files.writeString(target.resolve("sentinel"), "untouched");
        Path alias;
        if (kind.equals("REPORT_OPERATION")) {
            write(receipt(2, 1));
            alias = game.resolve("legacybridge/reports/" + UUID.randomUUID());
        } else if (kind.equals("ARTIFACT_PARENT")) {
            write(receipt(2, 1)); Files.createDirectories(game.resolve("legacybridge/staging"));
            alias = game.resolve("legacybridge/staging/" + staging);
        } else {
            Files.createDirectories(game.resolve("legacybridge")); alias = game.resolve("legacybridge/reports");
        }
        // Only a disposable TempDir junction, no visible console or game process.
        Process link = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", alias.toString(), target.toString())
                .redirectErrorStream(true).start();
        assertTrue(link.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, link.exitValue(), new String(link.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        try {
            var result = read();
            assertNotEquals(Status.COMPLETE, result.status());
            has(result, kind.equals("RESERVED_REPORTS") ? Code.WORKSPACE_UNSAFE : Code.PATH_UNSAFE);
            assertEquals("untouched", Files.readString(sentinel));
            assertTrue(result.jobs().stream().allMatch(j -> j.highestRecorded().isEmpty()));
        } finally {
            // Remove only this exact test-owned junction, not its target/directory tree.
            assertTrue(alias.startsWith(game)); Files.delete(alias);
        }
    }

    @Test void recordedDiagnosticsAreSanitizedAndKeptSeparateFromReaderErrors() throws Exception {
        var value = receipt(2, 1);
        value.put("error", Map.of("code", "CLAIMED_ERROR", "message", "text\n\u00a7c\u202e", "remediation", "Inspect")); write(value);
        var result = read(); assertEquals(Status.COMPLETE, result.status()); assertTrue(result.issues().isEmpty());
        assertEquals("textc", result.jobs().get(0).receipts().get(0).recordedError().orElseThrow().message());
    }

    private Map<String, Object> receipt(int schema, int sequence) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", schema); result.put("reportType", "LEGACYBRIDGE_JOB");
        result.put("jobId", job.toString()); result.put("state", "CONVERTED"); result.put("input", "original.jar");
        result.put("inputSha256", "a".repeat(64)); result.put("artifactSha256", "c".repeat(64));
        result.put("artifact", game.resolve("legacybridge/staging/" + staging + "/converted.jar").toString());
        result.put("installed", null); result.put("transaction", null); result.put("error", null);
        if (schema == 2) {
            result.put("sequence", sequence); result.put("event", "CONVERSION"); result.put("installation", null);
            result.put("activation", "NOT_OBSERVED");
        }
        return result;
    }
    private Map<String, Object> observation() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("operationId", UUID.randomUUID().toString()); value.put("action", "APPLY");
        value.put("transaction", transactionPath()); value.put("transactionStatus", "COMMITTED");
        value.put("entryIndex", 0); value.put("entryState", "INSTALLED"); value.put("transactionSafeToClose", true);
        return value;
    }
    private String transactionPath() { return game.resolve("legacybridge/transactions/" + transaction).toString(); }
    private Path nextFile() throws Exception {
        return Files.createDirectories(game.resolve("legacybridge/reports/" + directory)).resolve("job-" + UUID.randomUUID() + ".json");
    }
    private Path write(Map<String, Object> value) throws Exception {
        return Files.writeString(nextFile(), new CanonicalReportWriter().toJsonTree(value));
    }
    private JobHistory read() { return new JobHistoryReader().read(game, () -> false); }
    private static void has(JobHistory value, Code code) {
        assertTrue(value.issues().stream().anyMatch(i -> i.code() == code), value.toString());
    }
    private static List<Path> children(Path directory) throws Exception { try (var paths = Files.list(directory)) { return paths.toList(); } }
}
