package dev.legacybridge.converter.operation;

/** Advisory shared-heap admission/checkpoint brake, NOT an allocator or native-memory sandbox. */
public final class HeapPressure {
    public static final long RESERVE_BYTES = 32L << 20;
    private HeapPressure() { }
    public static long availableBytes() {
        Runtime runtime = Runtime.getRuntime();
        long maximum = runtime.maxMemory();
        return Math.max(0, Math.min(maximum, maximum - runtime.totalMemory() + runtime.freeMemory()));
    }
    public static void check() { require(availableBytes(), RESERVE_BYTES); }
    public static void beforeMappingParse(long payloadBytes) {
        // Capacity policy only: uncollected garbage must not be mistaken for live retained data.
        // The factor is an admission heuristic, NOT an allocation/retained-size guarantee.
        long capacity = Runtime.getRuntime().maxMemory();
        long minimumCapacity = Math.addExact(RESERVE_BYTES, Math.multiplyExact(payloadBytes, 6L));
        if (capacity < minimumCapacity) throw new OperationStoppedException(OperationStoppedException.Code.MEMORY_PRESSURE,
                "JVM heap capacity below full-data admission policy: maxHeapBytes=" + capacity + ", required=" + minimumCapacity
                        + ". Use a separately configured development CLI or included finite data; no automatic heap change.");
        check();
    }
    static void require(long available, long required) {
        if (available < required) throw new OperationStoppedException(OperationStoppedException.Code.MEMORY_PRESSURE,
                "Insufficient shared JVM heap headroom: available=" + available + ", required=" + required
                        + ". No automatic GC, heap change, download or retry; retry after memory is available.");
    }
}
