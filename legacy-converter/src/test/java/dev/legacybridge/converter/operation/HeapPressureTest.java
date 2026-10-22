package dev.legacybridge.converter.operation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeapPressureTest {
    @Test void headroomIsAnEstimateNotTheAmountOfInstalledPhysicalRam() {
        long available = HeapPressure.availableBytes();
        assertTrue(available >= 0 && available <= Runtime.getRuntime().maxMemory());
    }
    @Test void lowHeadroomHasStructuredCodeWithoutAllocatingAnOomFixture() {
        var error = assertThrows(OperationStoppedException.class, () -> HeapPressure.require(31L << 20, HeapPressure.RESERVE_BYTES));
        assertEquals(OperationStoppedException.Code.MEMORY_PRESSURE, error.code());
    }
    @Test void exactThresholdIsAccepted() { HeapPressure.require(HeapPressure.RESERVE_BYTES, HeapPressure.RESERVE_BYTES); }
    @Test void fullDataCapacityIsCheckedBeforeAnyLargeAllocation() {
        var error = assertThrows(OperationStoppedException.class,
                () -> HeapPressure.beforeMappingParse(Runtime.getRuntime().maxMemory() / 6 + 1));
        assertEquals(OperationStoppedException.Code.MEMORY_PRESSURE, error.code()); assertTrue(error.getMessage().contains("maxHeapBytes"));
    }
}
