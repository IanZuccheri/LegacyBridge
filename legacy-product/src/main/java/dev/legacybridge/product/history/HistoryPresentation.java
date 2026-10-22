package dev.legacybridge.product.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Inert display projection. No filesystem, class loading, installer or Minecraft dependency. */
public final class HistoryPresentation {
    private HistoryPresentation() { }

    /** key is authored here, value is literal display text, never a clickable path or authority. */
    public record Field(String key, String value) { }

    public static List<JobHistory.Job> search(JobHistory history, String query) {
        String needle = safe(query).strip().toLowerCase(Locale.ROOT);
        return history.jobs().stream().filter(job -> needle.isEmpty()
                || job.jobId().toString().contains(needle)
                || job.ordering().name().toLowerCase(Locale.ROOT).contains(needle)
                || job.receipts().stream().anyMatch(receipt -> (safe(receipt.input()) + " "
                        + receipt.inputSha256() + " " + receipt.artifactSha256().orElse("") + " "
                        + receipt.recordedState() + " " + receipt.event()).toLowerCase(Locale.ROOT).contains(needle))).toList();
    }

    /** Does not choose a 'latest' receipt when the reader could not establish ordering. */
    public static List<Field> receipt(JobHistory.Job job, int index) {
        JobHistory.Receipt receipt = job.receipts().get(index);
        List<Field> fields = new ArrayList<>();
        add(fields, "job", job.jobId());
        add(fields, "ordering", job.ordering());
        add(fields, "receipt", (index + 1) + " / " + job.receipts().size());
        add(fields, "location", receipt.location());
        add(fields, "schema", receipt.schemaVersion());
        add(fields, "sequence", receipt.sequence().map(Object::toString).orElse("—"));
        add(fields, "event", receipt.event());
        add(fields, "state", receipt.recordedState());
        add(fields, "activation", receipt.activation());
        add(fields, "input", receipt.input());
        add(fields, "input_hash", receipt.inputSha256());
        receipt.artifactSha256().ifPresent(value -> add(fields, "artifact_hash", value));
        receipt.artifact().ifPresent(value -> add(fields, "artifact", value));
        receipt.installed().ifPresent(value -> add(fields, "installed", value));
        receipt.transaction().ifPresent(value -> add(fields, "transaction", value));
        receipt.installation().ifPresent(value -> {
            add(fields, "operation", value.operationId());
            add(fields, "action", value.action());
            value.transaction().ifPresent(path -> add(fields, "transaction", path));
            value.status().ifPresent(status -> add(fields, "transaction_status", status));
            value.entryIndex().ifPresent(entry -> add(fields, "entry_index", entry));
            value.entryState().ifPresent(state -> add(fields, "entry_state", state));
            add(fields, "safe_close", value.recordedTransactionSafeToClose());
        });
        receipt.recordedError().ifPresent(value -> {
            add(fields, "recorded_error", value.code());
            add(fields, "message", value.message());
            add(fields, "remediation", value.remediation());
        });
        return List.copyOf(fields);
    }

    public static List<Field> diagnostics(JobHistory history) {
        List<Field> fields = new ArrayList<>();
        add(fields, "scan_status", history.status());
        add(fields, "entries", history.entriesVisited());
        add(fields, "examined", history.receiptsExamined());
        add(fields, "bytes", history.bytesRead());
        add(fields, "omitted", history.omittedIssues());
        for (JobHistory.Issue issue : history.issues()) {
            add(fields, "reader_issue", issue.code());
            add(fields, "location", issue.location());
            add(fields, "message", issue.message());
        }
        return List.copyOf(fields);
    }

    private static void add(List<Field> fields, String key, Object value) {
        fields.add(new Field(key, safe(String.valueOf(value))));
    }

    /** Defence in depth, including malformed surrogate values supplied by non-reader callers. */
    public static String safe(String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().limit(1024).forEach(cp -> result.appendCodePoint(Character.isISOControl(cp)
                || Character.getType(cp) == Character.FORMAT || cp == 0xA7 || cp >= 0xD800 && cp <= 0xDFFF ? ' ' : cp));
        if (value.codePointCount(0, value.length()) > 1024) result.append("…");
        return result.toString();
    }
}
