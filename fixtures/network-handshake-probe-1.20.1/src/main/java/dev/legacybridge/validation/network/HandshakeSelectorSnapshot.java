package dev.legacybridge.validation.network;

import com.google.gson.JsonObject;
import io.netty.channel.Channel;
import io.netty.channel.nio.AbstractNioChannel;
import java.lang.reflect.Field;
import java.nio.channels.SelectionKey;

/** Pinned Netty read-only selector state; does not register, read, flush or update interest ops. */
final class HandshakeSelectorSnapshot {
    private HandshakeSelectorSnapshot() { }
    static JsonObject capture(Channel channel) throws ReflectiveOperationException {
        if (!(channel instanceof AbstractNioChannel) || !channel.eventLoop().inEventLoop())
            throw new IllegalStateException("Selector observation requires native NIO event loop");
        Field keyField = AbstractNioChannel.class.getDeclaredField("selectionKey");
        Field pendingField = AbstractNioChannel.class.getDeclaredField("readPending");
        Field interestField = AbstractNioChannel.class.getDeclaredField("readInterestOp");
        if (keyField.getType() != SelectionKey.class || pendingField.getType() != boolean.class || interestField.getType() != int.class)
            throw new IllegalStateException("Pinned NIO field types changed");
        keyField.setAccessible(true); pendingField.setAccessible(true); interestField.setAccessible(true);
        SelectionKey key = (SelectionKey) keyField.get(channel);
        if (key == null || !key.isValid()) throw new IllegalStateException("Valid native selector key required");
        int readOp = interestField.getInt(channel);
        if (readOp != SelectionKey.OP_READ) throw new IllegalStateException("Expected socket OP_READ, not acceptor");
        JsonObject data = new JsonObject();
        data.addProperty("valid", true); data.addProperty("interestOps", key.interestOps());
        data.addProperty("readyOps", key.readyOps()); data.addProperty("readPending", pendingField.getBoolean(channel));
        data.addProperty("readInterested", (key.interestOps() & readOp) != 0);
        data.addProperty("autoRead", channel.config().isAutoRead());
        return data;
    }
}
