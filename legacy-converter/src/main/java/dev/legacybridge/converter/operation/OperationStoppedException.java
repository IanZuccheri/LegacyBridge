package dev.legacybridge.converter.operation;

/** Control-plane termination must propagate unchanged, never become malformed-bytecode diagnostics. */
public final class OperationStoppedException extends RuntimeException {
    public enum Code { CANCELLED, TIMEOUT, INPUT_LIMIT, INFLATED_LIMIT, CAPTURE_LIMIT, MEMORY_PRESSURE }
    private final Code code;

    public OperationStoppedException(Code code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code);
    }

    public Code code() { return code; }

    /** ASM may wrap callback failures in AnalyzerException. Keep the control-plane identity intact. */
    public static void rethrowIfPresent(Throwable failure) {
        Throwable cursor = failure;
        for (int depth = 0; cursor != null && depth < 32; depth++, cursor = cursor.getCause()) {
            if (cursor instanceof OperationStoppedException stopped) throw stopped;
        }
    }
}
