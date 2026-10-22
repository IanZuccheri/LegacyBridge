package dev.legacybridge.product.history;

import dev.legacybridge.product.controller.JobState;
import dev.legacybridge.product.install.TransactionResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryPresentationTest {
    private final UUID id = UUID.fromString("bee93a32-8bf9-4e83-b0a0-e3c9be12e325");

    @Test void searchesAnyReceiptCaseInsensitivelyWithoutInferringLatestOrModIdentity() {
        var job = job(); var history = history(job);
        for (String query : List.of("", "beE93A32", "SAMPLE.JAR", "pending_restart", "aa".repeat(32), "ambiguous"))
            assertEquals(List.of(job), HistoryPresentation.search(history, query), query);
        assertTrue(HistoryPresentation.search(history, "unrecorded-mod-id").isEmpty());
        assertTrue(job.highestRecorded().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> HistoryPresentation.search(history, "").clear());
    }

    @Test void receiptPaginationPreservesBothOutcomesAndOnlyLiteralRecordedValues() {
        var job = job();
        var first = HistoryPresentation.receipt(job, 0);
        var second = HistoryPresentation.receipt(job, 1);
        assertEquals("CONVERTED", field(first, "state"));
        assertEquals("PENDING_RESTART", field(second, "state"));
        assertEquals("1 / 2", field(first, "receipt")); assertEquals("2 / 2", field(second, "receipt"));
        assertEquals("NOT_OBSERVED", field(second, "activation"));
        assertEquals("AMBIGUOUS", field(second, "ordering"));
        assertEquals("legacybridge/staging/result.jar", field(second, "artifact"));
        assertThrows(IndexOutOfBoundsException.class, () -> HistoryPresentation.receipt(job, 2));
        assertThrows(UnsupportedOperationException.class, () -> first.clear());
    }

    @Test void readerDiagnosticsAndRecordedErrorsRemainDistinctAndOmittedCountVisible() {
        var diagnostics = HistoryPresentation.diagnostics(history(job()));
        assertEquals("PARTIAL", field(diagnostics, "scan_status"));
        assertEquals("INVALID_JSON", field(diagnostics, "reader_issue"));
        assertEquals("3", field(diagnostics, "omitted"));
        assertFalse(diagnostics.stream().anyMatch(field -> field.key().equals("recorded_error")));
        var receipt = HistoryPresentation.receipt(job(), 0);
        assertEquals("UNTRUSTED", field(receipt, "recorded_error"));
        assertFalse(receipt.stream().anyMatch(field -> field.key().equals("reader_issue")));
    }

    @Test void displaySanitizesControlsFormattingSurrogatesAndExplicitlyMarksTruncation() {
        assertEquals("a b c d e f", HistoryPresentation.safe("a\nb\u00a7c\u202ed\ud800e\u0000f"));
        assertEquals("x".repeat(1024) + "…", HistoryPresentation.safe("x".repeat(1025)));
        assertEquals("🙂".repeat(1024) + "…", HistoryPresentation.safe("🙂".repeat(1025)));
        assertEquals("áé São Paulo", HistoryPresentation.safe("áé São Paulo"));
    }

    @Test void recordedInstallationNeverBecomesActivationOrLiveCloseAuthority() {
        var base = receipt(JobState.PENDING_RESTART);
        var install = new JobHistory.Installation(id, JobHistory.Action.APPLY, Optional.of("legacybridge/transactions/old"),
                Optional.of(TransactionResult.Status.COMMITTED), Optional.of(0), Optional.empty(), true);
        var record = new JobHistory.Receipt(base.location(), 2, id, Optional.of(1), JobHistory.Event.INSTALLATION,
                base.recordedState(), base.input(), base.inputSha256(), base.artifactSha256(), base.artifact(),
                Optional.of("mods/old.jar"), base.transaction(), Optional.of(install), Optional.empty());
        var page = HistoryPresentation.receipt(new JobHistory.Job(id, JobHistory.Ordering.SEQUENCED, List.of(record), Optional.of(record)), 0);
        assertEquals("true", field(page, "safe_close"));
        assertEquals("NOT_OBSERVED", field(page, "activation"));
        assertEquals("APPLY", field(page, "action"));
        assertEquals("mods/old.jar", field(page, "installed"));
    }

    private JobHistory.Job job() {
        return new JobHistory.Job(id, JobHistory.Ordering.AMBIGUOUS,
                List.of(receipt(JobState.CONVERTED), receipt(JobState.PENDING_RESTART)), Optional.empty());
    }
    private JobHistory.Receipt receipt(JobState state) {
        return new JobHistory.Receipt("legacybridge/reports/receipt.json", 1, id, Optional.empty(),
                JobHistory.Event.LEGACY_UNSPECIFIED, state, "legacybridge/input/sample.jar", "aa".repeat(32),
                Optional.of("bb".repeat(32)), Optional.of("legacybridge/staging/result.jar"), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(new JobHistory.RecordedError("UNTRUSTED", "literal message", "do not execute")));
    }
    private JobHistory history(JobHistory.Job job) {
        return new JobHistory(JobHistory.Status.PARTIAL, List.of(job),
                List.of(new JobHistory.Issue(JobHistory.Code.INVALID_JSON, "receipt.json", "Invalid receipt")), 4, 2, 420, 3);
    }
    private static String field(List<HistoryPresentation.Field> fields, String key) {
        return fields.stream().filter(value -> value.key().equals(key)).findFirst().orElseThrow().value();
    }
}
