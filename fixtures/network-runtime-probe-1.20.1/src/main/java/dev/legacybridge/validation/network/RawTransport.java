package dev.legacybridge.validation.network;

import io.netty.buffer.Unpooled;
import java.net.InetSocketAddress;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;

/** Native bounded negative transport; never invokes a fixture encoder, decoder or handler. */
final class RawTransport {
    static void assertLoopback(Connection connection) {
        if (!connection.isConnected() || connection.isMemoryConnection() ||
                !(connection.getRemoteAddress() instanceof InetSocketAddress remote) ||
                remote.getAddress() == null || !remote.getAddress().isLoopbackAddress()) {
            throw new IllegalStateException("Expected a live native loopback socket, not an integrated-memory connection");
        }
    }
    static CompletableFuture<Void> send(Connection connection, String hex, boolean clientbound) {
        assertLoopback(connection);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(HexFormat.of().parseHex(hex)));
        ResourceLocation channel = new ResourceLocation("legacybridge_network", "snapshot");
        Packet<?> packet = clientbound ? new ClientboundCustomPayloadPacket(channel, buffer) : new ServerboundCustomPayloadPacket(channel, buffer);
        CompletableFuture<Void> sent = new CompletableFuture<>();
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> { if (released.compareAndSet(false, true)) buffer.release(); };
        try {
            connection.send(packet, new PacketSendListener() {
                @Override public void onSuccess() { release.run(); sent.complete(null); }
                @Override public Packet<?> onFailure() {
                    release.run(); sent.completeExceptionally(new IllegalStateException("Native packet send failed")); return null;
                }
            });
        } catch (RuntimeException failure) { release.run(); sent.completeExceptionally(failure); }
        return sent;
    }
}
