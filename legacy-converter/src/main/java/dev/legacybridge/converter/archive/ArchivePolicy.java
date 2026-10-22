package dev.legacybridge.converter.archive;

public final class ArchivePolicy {
    public static final String VERSION = "2026-09-v2";

    public static final long MAX_INPUT_BYTES = 256L * 1024 * 1024;
    public static final int MAX_ENTRY_COUNT = 50_000;
    public static final long MAX_CENTRAL_DIRECTORY_BYTES = 64L * 1024 * 1024;
    public static final long MAX_ENTRY_BYTES = 128L * 1024 * 1024;
    public static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 1024L * 1024 * 1024;
    public static final int MAX_CLASSFILE_BYTES = 16 * 1024 * 1024;
    public static final int MAX_METADATA_BYTES = 1024 * 1024;
    public static final long MIN_RATIO_CHECK_BYTES = 1024L * 1024;
    public static final int MAX_COMPRESSION_RATIO = 200;
    public static final int MAX_ENTRY_NAME_CHARS = 4_096;
    public static final long MAX_TOTAL_ENTRY_NAME_CHARS = 4_000_000L;
    public static final int MAX_MIXIN_CONFIGURATION_COUNT = 256;
    public static final int MAX_SPECIAL_ENTRY_SAMPLES_PER_KIND = 4;
    public static final int MAX_SPECIAL_ENTRY_SAMPLES = 40;

    private ArchivePolicy() {
    }
}
