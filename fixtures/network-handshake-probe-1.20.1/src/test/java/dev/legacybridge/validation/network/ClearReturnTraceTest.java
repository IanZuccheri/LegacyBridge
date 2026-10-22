package dev.legacybridge.validation.network;

import dev.legacybridge.validation.trace.ClearReturnTrace;
import dev.legacybridge.validation.trace.ClearReturnTrace.State;
import dev.legacybridge.validation.trace.HandshakeReadTrace;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Inert state/invocation tests; no debugger attaches and no game executes. */
final class ClearReturnTraceTest {
    @Test void validEnabledKeyWithoutReadOrPendingIsInconsistent() {
        assertTrue(new State(true, 0, false, true).inconsistent());
        assertTrue(new State(true, 4, false, true).inconsistent());
    }
    @Test void readInterestIsNotInconsistent() {
        assertFalse(new State(true, 1, false, true).inconsistent());
        assertFalse(new State(true, 5, false, true).inconsistent());
    }
    @Test void disabledOrCancelledKeyIsNotStaleEvidence() {
        assertFalse(new State(false, 0, false, true).inconsistent());
        assertFalse(new State(true, 0, false, false).inconsistent());
    }
    @Test void pendingReadIsNotClaimedAsSettledLoss() {
        assertFalse(new State(true, 0, true, true).inconsistent());
    }
    @Test void invalidMasksRefuse() {
        assertThrows(IllegalArgumentException.class, () -> new State(true, -1, false, true));
        assertThrows(IllegalArgumentException.class, () -> new State(true, 32, false, true));
    }
    @Test void noSelectedConnectionDoesNotInventSettledState() {
        var trace = new ClearReturnTrace();
        var result = trace.finish(3000, false);
        assertFalse(result.get("observed").getAsBoolean());
        assertFalse(result.has("state"));
        assertFalse(trace.witnessed());
    }
    @Test void unknownInvocationFailsBeforeAnyAttach() {
        assertThrows(IllegalArgumentException.class, () -> HandshakeReadTrace.main(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> HandshakeReadTrace.main(new String[]{"invalid", "unknown-mode"}));
    }
}
