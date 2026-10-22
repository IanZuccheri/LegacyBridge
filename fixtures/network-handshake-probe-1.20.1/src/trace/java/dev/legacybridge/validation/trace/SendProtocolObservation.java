package dev.legacybridge.validation.trace;

import com.sun.jdi.*;
import java.util.List;
import java.util.Optional;

/** Pinned Forge 47.4.23 sendPacket locals; metadata only, never packet arguments. */
public record SendProtocolObservation(Protocol priorProtocol, Protocol packetProtocol) {
    private static final String CONNECTION = "net.minecraft.network.Connection";
    private static final String PROTOCOL = "net.minecraft.network.ConnectionProtocol";
    private static final String SIGNATURE = "(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V";
    public enum Protocol { UNSET, HANDSHAKING, PLAY, STATUS, LOGIN }

    public SendProtocolObservation {
        if (priorProtocol == null || packetProtocol == null || packetProtocol == Protocol.UNSET
                || priorProtocol == packetProtocol)
            throw new IllegalArgumentException("A disabled send requires a known changed packet protocol");
    }

    public static Optional<SendProtocolObservation> capture(List<StackFrame> frames) throws AbsentInformationException {
        var sends = frames.stream().filter(f -> f.location().declaringType().name().equals(CONNECTION)
                && f.location().method().name().equals("m_129520_")).toList();
        if (sends.isEmpty()) return Optional.empty(); // Explicitly not a send sample.
        if (sends.size() != 1 || !sends.get(0).location().method().signature().equals(SIGNATURE))
            throw new IllegalStateException("Pinned send method differs");
        var frame = sends.get(0);
        // Verified in the executed Forge client JAR, NOT the unpatched vanilla SRG JAR:
        // connectionprotocol slot 3, live BCI 5..129; connectionprotocol1 slot 4, 11..129.
        return Optional.of(new SendProtocolObservation(local(frame, "connectionprotocol1"), local(frame, "connectionprotocol")));
    }

    private static Protocol local(StackFrame frame, String name) throws AbsentInformationException {
        var local = frame.visibleVariableByName(name);
        if (local == null || local.isArgument() || !local.signature().equals("L" + PROTOCOL.replace('.', '/') + ";"))
            throw new IllegalStateException("Pinned protocol local differs");
        Value value = frame.getValue(local);
        if (value == null) return Protocol.UNSET;
        if (!(value instanceof ObjectReference object) || !object.referenceType().name().equals(PROTOCOL))
            throw new IllegalStateException("Unknown protocol type");
        // Compare native enum singleton references. Do not call name()/toString(),
        // read arbitrary String contents, packet fields, credentials or getCurrentProtocol().
        var type = object.referenceType();
        Protocol match = null;
        for (var protocol : Protocol.values()) {
            if (protocol == Protocol.UNSET) continue;
            var field = type.fieldByName(protocol.name());
            if (field == null || !field.isStatic() || !field.isEnumConstant()
                    || !field.signature().equals(local.signature()))
                throw new IllegalStateException("Pinned protocol constants differ");
            if (!(type.getValue(field) instanceof ObjectReference constant))
                throw new IllegalStateException("Uninitialized protocol constants");
            if (constant.uniqueID() == object.uniqueID()) {
                if (match != null) throw new IllegalStateException("Aliased protocol constants");
                match = protocol;
            }
        }
        if (match == null) throw new IllegalStateException("Unknown protocol singleton");
        return match;
    }
}
