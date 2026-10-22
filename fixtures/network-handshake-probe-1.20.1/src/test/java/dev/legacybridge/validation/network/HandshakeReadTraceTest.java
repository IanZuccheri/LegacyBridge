package dev.legacybridge.validation.network;

import dev.legacybridge.validation.trace.ReadTraceWitness;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Inert classifier tests; not evidence that a game followed the sequence. */
final class HandshakeReadTraceTest {
    @Test void requiresActualClearCallerAndLaterNoOpState() {
        var trace = new ReadTraceWitness();
        trace.observe("SELECTOR_WRITE", 0, true, 1, true);
        assertFalse(trace.witnessed());
        trace.observe("CONFIG_SET", 1, true, 0, false);
        assertTrue(trace.witnessed());
    }
    @Test void noOpAloneCannotProveTrigger() {
        var trace = new ReadTraceWitness();
        trace.observe("CONFIG_SET", 1, true, 0, false);
        assertFalse(trace.witnessed());
    }
    @Test void otherClearCannotProveTrigger() {
        var trace = new ReadTraceWitness();
        trace.observe("SELECTOR_WRITE", 0, true, 1, false);
        trace.observe("CONFIG_SET", 1, true, 0, false);
        assertFalse(trace.witnessed());
    }
    @Test void interveningReadOrDisableInvalidatesCandidate() {
        for (String kind : new String[]{"SELECTOR_WRITE", "CONFIG_SET"}) {
            var trace = new ReadTraceWitness();
            trace.observe("SELECTOR_WRITE", 0, true, 1, true);
            trace.observe(kind, kind.equals("CONFIG_SET") ? 0 : 1, true, 0, false);
            trace.observe("CONFIG_SET", 1, true, 0, false);
            assertFalse(trace.witnessed());
        }
    }
    @Test void ordinaryDisabledClearIsNotStale() {
        var trace = new ReadTraceWitness();
        trace.observe("SELECTOR_WRITE", 0, false, 1, true);
        trace.observe("CONFIG_SET", 1, true, 0, false);
        assertFalse(trace.witnessed());
    }
    @Test void unknownAndInvalidScalarsRefuse() {
        var trace = new ReadTraceWitness();
        assertThrows(IllegalArgumentException.class, () -> trace.observe("UNKNOWN", 0, true, 0, false));
        assertThrows(IllegalArgumentException.class, () -> trace.observe("CONFIG_SET", 2, true, 0, false));
        assertThrows(IllegalArgumentException.class, () -> trace.observe("CONFIG_SET", 1, true, -1, false));
    }
    @Test void realFalseToTrueTransitionInvalidatesStaleCandidate() {
        var trace = new ReadTraceWitness();
        trace.observe("SELECTOR_WRITE", 0, true, 1, true);
        trace.invalidateCandidate();
        trace.observe("CONFIG_SET", 1, true, 0, false);
        assertFalse(trace.witnessed());
    }
}
