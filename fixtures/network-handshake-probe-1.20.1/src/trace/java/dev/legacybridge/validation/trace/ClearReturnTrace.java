package dev.legacybridge.validation.trace;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jdi.*;
import com.sun.jdi.event.BreakpointEvent;
import java.util.*;

/** QA only: observe AFTER native clearReadPending0, never suspend the sending thread. */
public final class ClearReturnTrace {
    private static final String NIO = "io.netty.channel.nio.AbstractNioChannel";
    private static final String SOCKET = "io.netty.channel.socket.nio.NioSocketChannel";
    private static final String CONTEXT = "io.netty.channel.AbstractChannelHandlerContext";
    private static final String PIPELINE = "io.netty.channel.DefaultChannelPipeline";
    private static final String CONNECTION = "net.minecraft.network.Connection";
    private final Map<String, Field> fields = new HashMap<>();
    private ObjectReference channel, config, key;
    private boolean inconsistentReturn, persistent;

    public record State(boolean autoRead, int interestOps, boolean readPending, boolean keyValid) {
        public State {
            if (interestOps < 0 || interestOps > 31) throw new IllegalArgumentException("Invalid selector mask");
        }
        public boolean inconsistent() { return keyValid && autoRead && (interestOps & 1) == 0 && !readPending; }
        JsonObject json() {
            var data = new JsonObject();
            data.addProperty("autoRead", autoRead); data.addProperty("interestOps", interestOps);
            data.addProperty("readPending", readPending); data.addProperty("keyValid", keyValid);
            data.addProperty("inconsistent", inconsistent());
            return data;
        }
    }

    public boolean observe(BreakpointEvent hit, JsonArray records) throws Exception {
        var thread = hit.thread();
        if (!thread.name().matches("Netty Client IO #[0-9]+")) return false;
        var candidate = thread.frame(0).thisObject();
        if (candidate == null || !candidate.referenceType().name().equals(SOCKET)) return false;
        if (channel == null) {
            // Bound actual Minecraft ownership through handler identities, not filenames or socket guesses.
            if (!hasNativeConnection(candidate)) return false;
            channel = candidate;
            config = (ObjectReference) value(channel, SOCKET, "config", "Lio/netty/channel/socket/SocketChannelConfig;");
            key = (ObjectReference) value(channel, NIO, "selectionKey", "Ljava/nio/channels/SelectionKey;");
            if (config == null || key == null || !key.referenceType().name().equals("sun.nio.ch.SelectionKeyImpl"))
                throw new IllegalStateException("Pinned clear context differs");
        } else if (candidate.uniqueID() != channel.uniqueID()) {
            if (hasNativeConnection(candidate)) throw new IllegalStateException("Multiple native connections");
            return false;
        }
        if (records.size() >= 16) throw new IllegalStateException("Clear sample bound exceeded");
        State state = state();
        inconsistentReturn |= state.inconsistent();
        var record = state.json();
        record.addProperty("ordinal", records.size() + 1);
        record.addProperty("phase", "CLEAR_RETURN"); record.addProperty("bytecodeIndex", 15);
        record.addProperty("threadId", thread.uniqueID()); record.addProperty("threadKind", "NETTY_CLIENT");
        record.addProperty("nativeConnectionVerified", true);
        records.add(record);
        return true;
    }

    public JsonObject finish(long millisecondsAfterLastClear, boolean disconnected) {
        var data = new JsonObject();
        data.addProperty("observed", false);
        data.addProperty("eventThreadSuspended", false);
        data.addProperty("millisecondsAfterLastClear", millisecondsAfterLastClear);
        if (channel == null || disconnected || millisecondsAfterLastClear < 3000) {
            data.addProperty("reason", "NO_LIVE_QUIET_PERIOD");
            return data;
        }
        // Object field reads do not require thread suspension. Multiple fields are NOT an atomic snapshot.
        State later = state();
        data.addProperty("observed", true); data.add("state", later.json());
        persistent = inconsistentReturn && later.inconsistent();
        return data;
    }
    public boolean witnessed() { return persistent; }

    private State state() {
        int flag = ((IntegerValue) value(config, "io.netty.channel.DefaultChannelConfig", "autoRead", "I")).value();
        if (flag != 0 && flag != 1) throw new IllegalStateException("Invalid autoRead scalar");
        boolean auto = flag == 1;
        int ops = ((IntegerValue) value(key, "sun.nio.ch.SelectionKeyImpl", "interestOps", "I")).value();
        boolean pending = ((BooleanValue) value(channel, NIO, "readPending", "Z")).value();
        boolean invalid = ((BooleanValue) value(key, "java.nio.channels.spi.AbstractSelectionKey", "invalid", "Z")).value();
        return new State(auto, ops, pending, !invalid);
    }

    private boolean hasNativeConnection(ObjectReference candidate) {
        ObjectReference pipeline = (ObjectReference) value(candidate, "io.netty.channel.AbstractChannel", "pipeline", "Lio/netty/channel/DefaultChannelPipeline;");
        ObjectReference context = (ObjectReference) value(pipeline, PIPELINE, "head", "Lio/netty/channel/AbstractChannelHandlerContext;");
        Set<Long> seen = new HashSet<>();
        int nativeHandlers = 0;
        while (context != null) {
            if (seen.size() >= 32 || !seen.add(context.uniqueID())) throw new IllegalStateException("Pipeline bound/cycle");
            String type = context.referenceType().name();
            if (type.equals("io.netty.channel.DefaultChannelHandlerContext")) {
                ObjectReference handler = (ObjectReference) value(context, type, "handler", "Lio/netty/channel/ChannelHandler;");
                if (handler == null) throw new IllegalStateException("Null pipeline handler");
                if (handler.referenceType().name().equals(CONNECTION)) nativeHandlers++;
            } else if (!type.equals(PIPELINE + "$HeadContext") && !type.equals(PIPELINE + "$TailContext")) {
                throw new IllegalStateException("Unknown pipeline context");
            }
            context = (ObjectReference) value(context, CONTEXT, "next", "Lio/netty/channel/AbstractChannelHandlerContext;");
        }
        if (nativeHandlers > 1) throw new IllegalStateException("Ambiguous native handler");
        return nativeHandlers == 1;
    }
    private Value value(ObjectReference object, String owner, String name, String signature) {
        if (object == null) throw new IllegalStateException("Missing pinned object");
        String identity = object.referenceType().name() + "/" + owner + "/" + name + "/" + signature;
        Field field = fields.get(identity);
        if (field == null) {
            if (fields.size() >= 64) throw new IllegalStateException("Field cache bound");
            var matches = object.referenceType().allFields().stream().filter(f -> !f.isStatic()
                    && f.declaringType().name().equals(owner) && f.name().equals(name) && f.signature().equals(signature)).toList();
            if (matches.size() != 1) throw new IllegalStateException("Pinned observation field differs");
            field = matches.get(0); fields.put(identity, field);
        }
        return object.getValue(field);
    }
}
