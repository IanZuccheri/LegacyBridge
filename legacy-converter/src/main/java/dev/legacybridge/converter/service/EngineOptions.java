package dev.legacybridge.converter.service;

import dev.legacybridge.converter.operation.OperationLimits;
import java.time.Duration;
import java.util.Objects;

/** One worker per service. Payload budgets are not a hard cap on a shared Minecraft JVM's heap. */
public record EngineOptions(int queueCapacity, int inventoryLimit, OperationLimits operationLimits) {
    public EngineOptions {
        if (queueCapacity < 1 || queueCapacity > 32) throw new IllegalArgumentException("Queue capacity must be 1..32");
        if (inventoryLimit < 1 || inventoryLimit > 256) throw new IllegalArgumentException("Inventory limit must be 1..256");
        Objects.requireNonNull(operationLimits, "operationLimits");
    }

    public static EngineOptions defaults() {
        return new EngineOptions(8, 128, new OperationLimits(64L << 20, 128L << 20, 64L << 20, Duration.ofMinutes(2)));
    }
}
