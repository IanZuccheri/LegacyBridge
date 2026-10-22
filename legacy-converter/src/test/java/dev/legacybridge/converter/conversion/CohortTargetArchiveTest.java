package dev.legacybridge.converter.conversion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CohortTargetArchiveTest {
    @TempDir Path scratch;
    @Test void privateRoundTripDeletesItsOnlyTemporary() throws Exception {
        var expected = Map.of("example.txt", new byte[]{1, 2, 3});
        CohortTargetPlanner.verifyEntries(expected, CohortTargetPlanner.reopen(SimpleContentConverter.jar(expected), scratch));
        try (var files = Files.list(scratch)) { assertEquals(0, files.count()); }
    }
    @Test void corruptArchiveIsRejectedAndDeleted() throws Exception {
        assertThrows(Exception.class, () -> CohortTargetPlanner.reopen(new byte[]{1, 2, 3}, scratch));
        try (var files = Files.list(scratch)) { assertEquals(0, files.count()); }
    }
    @Test void missingOrChangedEntriesCannotPassRoundTripEquality() {
        var expected = Map.of("test", new byte[]{1});
        assertThrows(RuntimeException.class, () -> CohortTargetPlanner.verifyEntries(expected, Map.of()));
        assertThrows(RuntimeException.class, () -> CohortTargetPlanner.verifyEntries(expected, Map.of("test", new byte[]{2})));
    }
    @Test void unavailableScratchCannotBeReportedAsSuccess() {
        assertThrows(java.io.IOException.class, () -> CohortTargetPlanner.reopen(new byte[]{1}, scratch.resolve("missing")));
    }
    @Test void cancellationDuringSnapshotCleansTheCreatedCandidate() throws Exception {
        byte[] jar = SimpleContentConverter.jar(Map.of("test", new byte[]{1}));
        var owner = new java.util.concurrent.atomic.AtomicReference<dev.legacybridge.converter.operation.OperationControl>();
        var control = new dev.legacybridge.converter.operation.OperationControl(dev.legacybridge.converter.operation.OperationLimits.defaults(), progress -> {
            if (progress.stage() == dev.legacybridge.converter.operation.OperationStage.INPUT_SNAPSHOT) owner.get().requestCancel();
        });
        owner.set(control);
        try (var scope = control.enter()) {
            assertThrows(dev.legacybridge.converter.operation.OperationStoppedException.class, () -> CohortTargetPlanner.reopen(jar, scratch));
        }
        try (var files = Files.list(scratch)) { assertEquals(0, files.count()); }
    }
}
