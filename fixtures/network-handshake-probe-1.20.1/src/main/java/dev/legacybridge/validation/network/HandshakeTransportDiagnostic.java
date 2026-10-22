package dev.legacybridge.validation.network;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.lang.reflect.Field;
import net.minecraft.network.Connection;

/** One read-only event-loop observation per owned actor; no client class references. */
final class HandshakeTransportDiagnostic {
    private HandshakeTransportDiagnostic() { }
    static void request(Connection connection, HandshakeControl control) {
        RawTransport.assertLoopback(connection);
        Channel channel = connection.channel();
        channel.eventLoop().execute(() -> {
            try {
                if (!channel.eventLoop().inEventLoop()) throw new IllegalStateException("Socket observation escaped native event loop");
                JsonObject data = new JsonObject();
                data.addProperty("nativeEventLoop", true);
                data.addProperty("threadId", Thread.currentThread().getId());
                data.addProperty("channelClass", channel.getClass().getName());
                data.addProperty("active", channel.isActive());
                data.addProperty("open", channel.isOpen());
                data.addProperty("autoRead", channel.config().isAutoRead());
                data.addProperty("writable", channel.isWritable());
                data.addProperty("protocol", String.valueOf(channel.attr(Connection.ATTRIBUTE_PROTOCOL).get()));
                data.add("selector", HandshakeSelectorSnapshot.capture(channel));
                var outbound = channel.unsafe().outboundBuffer();
                if (outbound != null) data.addProperty("pendingOutboundBytes", outbound.totalPendingWriteBytes());
                if (channel.pipeline().names().size() > 24) throw new IllegalStateException("Unexpected pipeline bound");
                data.addProperty("pipeline", String.join(",", channel.pipeline().names()));
                inspectBuffer(data, channel, "splitter", true);
                inspectBuffer(data, channel, "decoder", false);
                data.addProperty("stateMutated", false);
                data.addProperty("payloadPersisted", false);
                control.diagnostic(data);
            } catch (ReflectiveOperationException | RuntimeException failure) { control.fail(failure); }
        });
    }
    private static void inspectBuffer(JsonObject data, Channel channel, String handlerName, boolean framePrefix) throws ReflectiveOperationException {
        Object handler = channel.pipeline().get(handlerName);
        if (!(handler instanceof ByteToMessageDecoder)) throw new IllegalStateException("Unexpected pinned decoder type");
        Field field = ByteToMessageDecoder.class.getDeclaredField("cumulation");
        if (field.getType() != ByteBuf.class) throw new IllegalStateException("Pinned Netty cumulation type changed");
        field.setAccessible(true);
        ByteBuf buffer = (ByteBuf) field.get(handler);
        data.addProperty(handlerName + "Class", handler.getClass().getName());
        data.addProperty(handlerName + "BufferedBytes", buffer == null ? 0 : buffer.readableBytes());
        if (framePrefix && buffer != null && buffer.isReadable()) {
            byte[] prefix = new byte[Math.min(3, buffer.readableBytes())];
            buffer.getBytes(buffer.readerIndex(), prefix); // Absolute read, no readerIndex/refcount mutation.
            int length = HandshakeFramePrefix.length(prefix);
            data.addProperty("framePrefixState", length == -2 ? "INVALID" : length == -1 ? "INCOMPLETE" : "COMPLETE");
            if (length >= 0) data.addProperty("expectedFrameBytes", length);
        }
    }
}
