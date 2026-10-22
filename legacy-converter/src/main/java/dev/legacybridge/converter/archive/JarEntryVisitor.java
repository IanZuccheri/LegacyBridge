package dev.legacybridge.converter.archive;

import java.io.IOException;

public interface JarEntryVisitor {
    /**
     * Observes a validated archive entry name before any bytes are read.
     * Implementations must treat the name as inert data.
     */
    default void visitEntry(String entryName, boolean directory) throws IOException {
        // Optional hook for path-only inventories.
    }

    boolean requiresBytes(String entryName);

    /** Optional stricter bound for entries captured by this visitor. */
    default long maxCapturedBytes(String entryName) {
        return Long.MAX_VALUE;
    }

    void visit(String entryName, byte[] contents) throws IOException;
}
