package dev.legacybridge.converter.operation;

import java.time.Duration;
import java.util.Objects;

/** Per-job payload/work limits; these are not a hard JVM heap or allocation ceiling. */
public record OperationLimits(long maxInputBytes, long maxInflatedBytes, long maxCapturedBytes, Duration timeout) {
    public OperationLimits {
        Objects.requireNonNull(timeout, "timeout");
        if (maxInputBytes <= 0 || maxInflatedBytes <= 0 || maxCapturedBytes <= 0)
            throw new IllegalArgumentException("Byte limits must be positive");
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofDays(1)) > 0)
            throw new IllegalArgumentException("Timeout must be positive and at most one day");
    }

    public static OperationLimits defaults() {
        return new OperationLimits(256L * 1024 * 1024, 1024L * 1024 * 1024,
                128L * 1024 * 1024, Duration.ofMinutes(5));
    }
}
