package dev.legacybridge.runtime.compat;

/** No verified implementation covers this behavior. Callers must abort, not attempt linkage. */
public final class UnsupportedCompatibilityException extends IllegalArgumentException {
    public UnsupportedCompatibilityException(String reason) { super("UNSUPPORTED: " + reason); }
}
