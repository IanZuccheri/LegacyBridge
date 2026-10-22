package dev.legacybridge.product.history;

import java.util.Objects;
import java.util.UUID;

/** A bounded literal snapshot, not an install/discovery capability or proof of authenticity. */
public record SavedReport(UUID jobId, Kind kind, String sha256, String text, boolean displaySanitized) {
    public static final int PAGE_CHARS = 4096;
    public enum Kind {
        COMPATIBILITY("analysis.json"), CONVERSION("conversion.json"), TRANSFORMATIONS("transformation-log.json");
        private final String fileName;
        Kind(String fileName) { this.fileName = fileName; }
        public String fileName() { return fileName; }
    }
    public SavedReport {
        Objects.requireNonNull(jobId); Objects.requireNonNull(kind); Objects.requireNonNull(text);
        if (!sha256.matches("[a-f0-9]{64}") || text.length() > SavedReportStore.REPORT_BYTES)
            throw new IllegalArgumentException("Invalid bounded report snapshot");
    }
    public int pages() { return Math.max(1, (text.length() + PAGE_CHARS - 1) / PAGE_CHARS); }
    public String page(int index) {
        if (index < 0 || index >= pages()) throw new IllegalArgumentException("Invalid report page");
        int start = index * PAGE_CHARS, end = Math.min(text.length(), start + PAGE_CHARS);
        // Assign a crossing surrogate pair to the earlier page, without losing or duplicating it.
        if (start > 0 && start < text.length() && Character.isLowSurrogate(text.charAt(start))) start++;
        if (end < text.length() && Character.isLowSurrogate(text.charAt(end))) end++;
        return text.substring(start, end);
    }
}
