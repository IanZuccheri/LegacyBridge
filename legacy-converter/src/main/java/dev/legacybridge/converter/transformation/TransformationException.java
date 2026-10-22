package dev.legacybridge.converter.transformation;

/** A fail-closed pipeline refusal. No candidate JAR may be committed after this exception. */
public final class TransformationException extends IllegalArgumentException {
    public TransformationException(String message) { super(message); }
    public TransformationException(String message, Throwable cause) { super(message, cause); }
}
