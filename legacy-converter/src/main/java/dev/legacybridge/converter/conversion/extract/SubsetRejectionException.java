package dev.legacybridge.converter.conversion.extract;

import java.util.List;

/** A fail-closed diagnostic: the caller must not emit a converted JAR. */
public final class SubsetRejectionException extends Exception {
    private final List<String> diagnostics;

    public SubsetRejectionException(String diagnostic) {
        super(diagnostic);
        this.diagnostics = List.of(diagnostic);
    }

    public List<String> diagnostics() { return diagnostics; }
}
