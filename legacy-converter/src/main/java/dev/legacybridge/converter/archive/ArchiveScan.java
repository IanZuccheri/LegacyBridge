package dev.legacybridge.converter.archive;

import dev.legacybridge.converter.report.ArchiveSummary;
import dev.legacybridge.converter.report.InputFingerprint;

public record ArchiveScan(InputFingerprint input, ArchiveSummary archive) {
}

