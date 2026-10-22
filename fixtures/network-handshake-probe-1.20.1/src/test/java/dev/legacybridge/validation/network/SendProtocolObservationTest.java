package dev.legacybridge.validation.network;

import com.sun.jdi.*;
import dev.legacybridge.validation.trace.SendProtocolObservation;
import dev.legacybridge.validation.trace.SendProtocolObservation.Protocol;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

/** JDI mirror doubles refuse every access except the allowlist. No game executes. */
final class SendProtocolObservationTest {
    @Test void capturesUnsetIntentionAndHandshakeHelloWithoutPacketAccess() throws Exception {
        var first = new Mirrors(null, "HANDSHAKING");
        assertEquals(new SendProtocolObservation(Protocol.UNSET, Protocol.HANDSHAKING),
                SendProtocolObservation.capture(List.of(first.frame())).orElseThrow());
        var hello = new Mirrors("HANDSHAKING", "LOGIN");
        assertEquals(new SendProtocolObservation(Protocol.HANDSHAKING, Protocol.LOGIN),
                SendProtocolObservation.capture(List.of(hello.frame())).orElseThrow());
    }
    @Test void absentSendIsNotInventedEvidence() throws Exception {
        assertTrue(SendProtocolObservation.capture(List.of()).isEmpty());
    }
    @Test void changedMethodDescriptorRefuses() {
        var mirror = new Mirrors(null, "HANDSHAKING"); mirror.methodSignature = "()V";
        assertThrows(IllegalStateException.class, () -> SendProtocolObservation.capture(List.of(mirror.frame())));
    }
    @Test void missingOrArgumentLocalRefuses() {
        for (boolean missing : List.of(false, true)) {
            var mirror = new Mirrors(null, "HANDSHAKING"); mirror.missingLocal = missing; mirror.argumentLocal = !missing;
            assertThrows(IllegalStateException.class, () -> SendProtocolObservation.capture(List.of(mirror.frame())));
        }
    }
    @Test void unknownEnumSingletonRefuses() {
        var mirror = new Mirrors(null, "HANDSHAKING"); mirror.unknownSingleton = true;
        assertThrows(IllegalStateException.class, () -> SendProtocolObservation.capture(List.of(mirror.frame())));
    }
    @Test void nonEnumConstantRefuses() {
        var mirror = new Mirrors(null, "HANDSHAKING"); mirror.enumConstant = false;
        assertThrows(IllegalStateException.class, () -> SendProtocolObservation.capture(List.of(mirror.frame())));
    }
    @Test void duplicateSendFrameRefuses() {
        var mirror = new Mirrors(null, "HANDSHAKING");
        assertThrows(IllegalStateException.class, () -> SendProtocolObservation.capture(List.of(mirror.frame(), mirror.frame())));
    }
    @Test void unchangedOrUnknownPacketProtocolCannotExplainDisabledSend() {
        assertThrows(IllegalArgumentException.class, () -> new SendProtocolObservation(Protocol.LOGIN, Protocol.LOGIN));
        assertThrows(IllegalArgumentException.class, () -> new SendProtocolObservation(Protocol.LOGIN, Protocol.UNSET));
        assertThrows(IllegalArgumentException.class, () -> new SendProtocolObservation(null, Protocol.LOGIN));
    }

    private static <T> T mirror(Class<T> type, BiFunction<String, Object[], Object> action) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> action.apply(method.getName(), args)));
    }
    private static Object denied(String method) { throw new AssertionError("Unexpected JDI access: " + method); }

    private static final class Mirrors {
        private static final String TYPE = "net.minecraft.network.ConnectionProtocol";
        private static final String DESC = "Lnet/minecraft/network/ConnectionProtocol;";
        private final Map<String, Field> fields = new HashMap<>();
        private final Map<Field, ObjectReference> constants = new IdentityHashMap<>();
        private final Map<String, ObjectReference> values = new HashMap<>();
        private final String before, packet;
        private boolean missingLocal, argumentLocal, unknownSingleton;
        private boolean enumConstant = true;
        private String methodSignature = "(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V";
        private final ReferenceType protocolType;

        Mirrors(String before, String packet) {
            this.before = before; this.packet = packet;
            protocolType = mirror(ReferenceType.class, (method, args) -> switch (method) {
                case "name" -> TYPE;
                case "fieldByName" -> fields.get((String) args[0]);
                case "getValue" -> constants.get((Field) args[0]);
                default -> denied(method);
            });
            long id = 1;
            for (String name : List.of("HANDSHAKING", "PLAY", "STATUS", "LOGIN")) {
                final long identity = id++;
                Field field = mirror(Field.class, (method, args) -> switch (method) {
                    case "isStatic" -> true;
                    case "isEnumConstant" -> enumConstant;
                    case "signature" -> DESC;
                    default -> denied(method);
                });
                ObjectReference value = object(identity);
                fields.put(name, field); constants.put(field, value); values.put(name, value);
            }
        }
        private ObjectReference object(long id) {
            return mirror(ObjectReference.class, (method, args) -> switch (method) {
                case "uniqueID" -> id;
                case "referenceType" -> protocolType;
                default -> denied(method);
            });
        }
        StackFrame frame() {
            ReferenceType connection = mirror(ReferenceType.class, (method, args) ->
                    method.equals("name") ? "net.minecraft.network.Connection" : denied(method));
            Method send = mirror(Method.class, (method, args) -> switch (method) {
                case "name" -> "m_129520_";
                case "signature" -> methodSignature;
                default -> denied(method);
            });
            Location location = mirror(Location.class, (method, args) -> switch (method) {
                case "declaringType" -> connection;
                case "method" -> send;
                default -> denied(method);
            });
            Map<LocalVariable, Value> locals = new IdentityHashMap<>();
            Map<String, LocalVariable> names = new HashMap<>();
            for (String name : List.of("connectionprotocol", "connectionprotocol1")) {
                LocalVariable local = mirror(LocalVariable.class, (method, args) -> switch (method) {
                    case "signature" -> DESC;
                    case "isArgument" -> argumentLocal;
                    default -> denied(method);
                });
                names.put(name, local);
                locals.put(local, unknownSingleton ? object(99) : values.get(name.equals("connectionprotocol") ? packet : before));
            }
            return mirror(StackFrame.class, (method, args) -> switch (method) {
                case "location" -> location;
                case "visibleVariableByName" -> missingLocal ? null : names.get((String) args[0]);
                case "getValue" -> locals.get((LocalVariable) args[0]);
                default -> denied(method); // Especially getArgumentValues/invokeMethod/toString.
            });
        }
    }
}
