package dev.legacybridge.validation.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HandshakeFramePrefixTest {
    @Test void finiteHeaderLengthsWithoutGameOrPacketExecution() {
        assertEquals(0, HandshakeFramePrefix.length(new byte[]{0}));
        assertEquals(1, HandshakeFramePrefix.length(new byte[]{1}));
        assertEquals(128, HandshakeFramePrefix.length(new byte[]{(byte)128, 1}));
        assertEquals(2097151, HandshakeFramePrefix.length(new byte[]{(byte)255, (byte)255, 127}));
    }
    @Test void incompleteInvalidAndOversizedAreNotInventedLengths() {
        assertEquals(-1, HandshakeFramePrefix.length(new byte[]{}));
        assertEquals(-1, HandshakeFramePrefix.length(new byte[]{(byte)128}));
        assertEquals(-2, HandshakeFramePrefix.length(new byte[]{(byte)128, (byte)128, (byte)128}));
        assertThrows(IllegalArgumentException.class, () -> HandshakeFramePrefix.length(new byte[4]));
        byte[] input = {(byte)129, 1}; byte[] before = input.clone();
        assertEquals(129, HandshakeFramePrefix.length(input)); assertArrayEquals(before, input);
    }
}
