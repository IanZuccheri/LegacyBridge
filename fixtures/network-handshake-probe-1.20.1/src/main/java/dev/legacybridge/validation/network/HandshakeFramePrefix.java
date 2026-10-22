package dev.legacybridge.validation.network;

/** Non-consuming 21-bit frame-prefix interpretation for QA. No payload is copied. */
final class HandshakeFramePrefix {
    private HandshakeFramePrefix() { }
    static int length(byte[] prefix) {
        if (prefix.length > 3) throw new IllegalArgumentException("At most three prefix bytes");
        int value = 0;
        for (int i = 0; i < prefix.length; i++) {
            int current = prefix[i] & 255;
            value |= (current & 127) << (7 * i);
            if ((current & 128) == 0) return value;
        }
        return prefix.length == 3 ? -2 : -1; // Invalid / incomplete, never invented frame length.
    }
}
