package dev.legacybridge.converter.operation;

/** Observed work/heap samples, separate from deterministic conversion reports. Not retained-size attribution or a hard ceiling. */
public record OperationMetrics(long largestInputBytes, long inflatedWorkBytes, long capturedWorkBytes,
                               long peakObservedSharedHeapUsedBytes, long heapSamples) {
    public java.util.Map<String, Object> toTree() {
        return java.util.Map.of("schemaVersion", 1, "largestInputBytes", largestInputBytes,
                "inflatedWorkBytes", inflatedWorkBytes, "capturedWorkBytes", capturedWorkBytes,
                "peakObservedSharedHeapUsedBytes", peakObservedSharedHeapUsedBytes, "heapSamples", heapSamples,
                "hardHeapLimit", false, "sharedHeapAttributionToThisJobOnly", false);
    }
}
