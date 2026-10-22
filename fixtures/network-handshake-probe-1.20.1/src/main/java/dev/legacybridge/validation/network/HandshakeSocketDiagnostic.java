package dev.legacybridge.validation.network;

import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.Connection;

/** Opt-in read-only QA. No handler injection, reads, flushes, protocol or index mutations.
 * Accesses only pinned Minecraft/Netty fields, never mod fields or credentials.
 */
final class HandshakeSocketDiagnostic {
    private HandshakeSocketDiagnostic() { }
    static boolean request(ConnectScreen screen, HandshakeControl control) {
        try {
            Field[] candidates = Arrays.stream(ConnectScreen.class.getDeclaredFields())
                    .filter(field -> field.getType() == Connection.class).toArray(Field[]::new);
            if (candidates.length != 1) throw new IllegalStateException("Pinned ConnectScreen connection field inventory changed");
            Field field = candidates[0]; field.setAccessible(true);
            Connection connection = (Connection) field.get(screen);
            if (connection == null || !connection.isConnected()) return false;
            HandshakeTransportDiagnostic.request(connection, control);
            return true;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned socket observation inaccessible", failure); }
    }
}
