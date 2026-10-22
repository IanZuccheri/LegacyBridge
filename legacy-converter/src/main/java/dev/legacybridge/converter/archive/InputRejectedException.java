package dev.legacybridge.converter.archive;

public final class InputRejectedException extends Exception {
    private final String reasonCode;

    public InputRejectedException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public InputRejectedException(String reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}

