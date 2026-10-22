package dev.legacybridge.converter.service;

import java.util.Objects;

/** Bounded presentation diagnostic; never a command, class name to load, or filesystem authority. */
public record EngineError(String code, String message, String remediation) {
    public EngineError {
        Objects.requireNonNull(code, "code");
        if (!code.matches("[A-Z][A-Z0-9_]{0,79}")) throw new IllegalArgumentException("Invalid error code");
        message = safeText(message);
        remediation = safeText(remediation);
    }

    private static String safeText(String value) {
        Objects.requireNonNull(value, "diagnostic");
        StringBuilder result = new StringBuilder();
        value.codePoints().limit(512).forEach(cp -> result.appendCodePoint(
                Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT ? ' ' : cp));
        String bounded = result.toString().strip();
        return bounded.isEmpty() ? "No further details available." : bounded;
    }
}
