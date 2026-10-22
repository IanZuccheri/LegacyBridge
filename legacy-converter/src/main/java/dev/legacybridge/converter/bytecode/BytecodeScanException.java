package dev.legacybridge.converter.bytecode;

/** Signals that a class-file entry could not be parsed statically. */
public final class BytecodeScanException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String entryName;

    public BytecodeScanException(String entryName, String message) {
        super(message);
        this.entryName = entryName;
    }

    public BytecodeScanException(String entryName, String message, Throwable cause) {
        super(message, cause);
        this.entryName = entryName;
    }

    public String entryName() {
        return entryName;
    }
}
