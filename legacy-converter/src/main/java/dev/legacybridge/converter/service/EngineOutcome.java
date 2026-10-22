package dev.legacybridge.converter.service;

import java.util.Objects;
import java.util.Optional;

/** Typed service envelope. Existing report schemas inside values are unchanged. */
public record EngineOutcome<T>(Status status, Optional<T> value, Optional<EngineError> error) {
    public enum Status { COMPLETED, BLOCKED, FAILED, CANCELLED }

    public EngineOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(error, "error");
        if (status == Status.COMPLETED && (value.isEmpty() || error.isPresent()))
            throw new IllegalArgumentException("Completed operation requires a value and no error");
        if (status != Status.COMPLETED && error.isEmpty())
            throw new IllegalArgumentException("Non-completed operation requires a diagnostic");
        if ((status == Status.FAILED || status == Status.CANCELLED) && value.isPresent())
            throw new IllegalArgumentException("Failed or cancelled operation cannot expose a result");
    }

    public static <T> EngineOutcome<T> completed(T value) {
        return new EngineOutcome<>(Status.COMPLETED, Optional.of(value), Optional.empty());
    }

    public static <T> EngineOutcome<T> blocked(T value, EngineError error) {
        return new EngineOutcome<>(Status.BLOCKED, Optional.of(value), Optional.of(error));
    }

    public static <T> EngineOutcome<T> failed(EngineError error) {
        return new EngineOutcome<>(Status.FAILED, Optional.empty(), Optional.of(error));
    }

    public static <T> EngineOutcome<T> cancelled() {
        return new EngineOutcome<>(Status.CANCELLED, Optional.empty(), Optional.of(new EngineError(
                "CANCELLED", "Operation cancelled before publication.", "Start a new job when ready.")));
    }
}
