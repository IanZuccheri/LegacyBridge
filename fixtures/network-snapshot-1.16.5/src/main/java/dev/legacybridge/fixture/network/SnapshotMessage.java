package dev.legacybridge.fixture.network;

/** Immutable wire DTO. Negative values are an explicit invalid-decode sentinel, never state. */
public final class SnapshotMessage {
    public final int sequence;
    public final int value;

    public SnapshotMessage(int sequence, int value) {
        this.sequence = sequence;
        this.value = value;
    }
}
