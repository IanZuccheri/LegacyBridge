package dev.legacybridge.converter.operation;

import java.util.Objects;

/** total=-1 means the stage has no exact measurable total. All callbacks run on the worker. */
public record OperationProgress(OperationStage stage, long completed, long total, String detail) {
    public OperationProgress {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(detail, "detail");
        if (completed < 0 || total < -1 || total >= 0 && completed > total || detail.length() > 4096)
            throw new IllegalArgumentException("Invalid bounded operation progress");
    }
}
