package dev.legacybridge.validation.trace;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.EventRequest;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Explicit local QA debugger. Reads only three pinned scalar fields, a setter
 * argument, pinned boolean/protocol locals and bounded frame identities. Never invokes target methods, writes
 * target fields, pops frames, forces returns, redefines classes or exits the VM.
 * Breakpoints suspend the event thread: this intentionally changes timing and
 * can NEVER certify default-mode behavior. Not included in the Forge probe JAR.
 */
public final class HandshakeReadTrace {
    private static final String CONFIG = "io.netty.channel.DefaultChannelConfig";
    private static final String NIO = "io.netty.channel.nio.AbstractNioChannel";
    private static final String SOCKET = "io.netty.channel.socket.nio.NioSocketChannel";
    private static final String KEY = "sun.nio.ch.SelectionKeyImpl";
    private static final Map<String, String[]> POINTS = Map.of(
            CONFIG, new String[]{"setAutoRead", "(Z)Lio/netty/channel/ChannelConfig;"},
            KEY, new String[]{"interestOps", "(I)Ljava/nio/channels/SelectionKey;"});
    private static final Map<String, String[]> CLEAR_POINTS = Map.of(NIO, new String[]{"clearReadPending0", "()V"});
    private final boolean clearOnly;
    private final ClearReturnTrace clear = new ClearReturnTrace();
    private HandshakeReadTrace(boolean clearOnly) { this.clearOnly = clearOnly; }
    private final JsonArray records = new JsonArray();
    private final ReadTraceWitness witness = new ReadTraceWitness();
    private final Set<String> installed = new HashSet<>();
    private ObjectReference channel;
    private ObjectReference config;
    private ObjectReference key;
    private long lastSample;
    private int events;
    private boolean disconnected;

    public static void main(String[] args) throws Exception {
        if (args.length != 1 && (args.length != 2 || !args[1].equals("clear-only")))
            throw new IllegalArgumentException("One explicit owned output and optional clear-only mode required");
        boolean clearOnly = args.length == 2;
        String filename = clearOnly ? "read-clear-trace.json" : "read-interest-trace.json";
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        if (!output.getFileName().toString().equals(filename)
                || !output.getParent().getFileName().toString().equals("a")
                || !output.getParent().getParent().getFileName().toString().equals("control")
                || !output.getParent().equals(output.getParent().toRealPath())
                || Files.exists(output) || Files.exists(output.resolveSibling(filename + ".tmp")))
            throw new IllegalArgumentException("Fresh real QA control/a output required");
        var trace = new HandshakeReadTrace(clearOnly);
        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", clearOnly ? 1 : 2);
        report.addProperty("reportType", clearOnly ? "LEGACYBRIDGE_CLEAR_RETURN_TRACE" : "LEGACYBRIDGE_READ_INTEREST_TRACE");
        report.addProperty("normalModePassClaimed", false);
        report.addProperty("executionSuspended", true);
        report.addProperty("gameDataMutated", false);
        report.addProperty("targetMethodsInvoked", false);
        report.addProperty("payloadOrCredentialsRead", false);
        VirtualMachine vm = null;
        boolean failed = false;
        try {
            var connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                    .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
            var arguments = connector.defaultArguments();
            arguments.get("hostname").setValue("127.0.0.1");
            arguments.get("port").setValue("25592");
            arguments.get("timeout").setValue("10000");
            vm = connector.attach(arguments);
            // suspend=n at startup: a failed controller cannot strand the client before main.
            for (String type : (clearOnly ? CLEAR_POINTS : POINTS).keySet()) {
                var request = vm.eventRequestManager().createClassPrepareRequest();
                request.addClassFilter(type);
                request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                request.enable();
                for (ReferenceType loaded : vm.classesByName(type))
                    if (loaded.isPrepared()) trace.install(vm, loaded);
            }
            long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
            while (System.nanoTime() < deadline && !Files.exists(output.getParent().resolve("trace-stop.request"))) {
                if (trace.lastSample != 0 && System.nanoTime() - trace.lastSample > Duration.ofSeconds(3).toNanos()) break;
                EventSet set = vm.eventQueue().remove(250);
                if (set == null) continue;
                try {
                    for (Event event : set) {
                        if (++trace.events > 4096) throw new IllegalStateException("Event bound exceeded");
                        if (event instanceof ClassPrepareEvent prepared) trace.install(vm, prepared.referenceType());
                        else if (event instanceof BreakpointEvent hit) trace.sample(hit);
                        else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) trace.disconnected = true;
                    }
                } finally {
                    // A natural disconnect is expected; every other failure propagates.
                    try { set.resume(); } catch (VMDisconnectedException closed) { trace.disconnected = true; }
                }
                if (trace.disconnected || (!clearOnly && trace.witness.witnessed())) break;
            }
            if (trace.records.isEmpty()) throw new IllegalStateException("No selected native connection observed");
            if (clearOnly) report.add("settledObservation", trace.clear.finish(
                    Duration.ofNanos(System.nanoTime() - trace.lastSample).toMillis(), trace.disconnected));
            report.addProperty("status", trace.witnessed() ? "DIAGNOSTIC_WITNESS" : "OBSERVED_NO_WITNESS");
        } catch (Exception failure) {
            failed = true;
            report.addProperty("status", "FAILED_DIAGNOSTIC");
            report.addProperty("errorType", failure.getClass().getSimpleName()); // No arbitrary message/locals export.
        } finally {
            if (vm != null && !trace.disconnected) {
                try { vm.dispose(); }
                catch (VMDisconnectedException closed) { trace.disconnected = true; }
            }
        }
        report.addProperty("eventCount", trace.events);
        report.addProperty("witness", trace.witnessed());
        report.add("records", trace.records);
        byte[] bytes = (report + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65536) throw new IllegalStateException("Trace report bound exceeded");
        Path temp = output.resolveSibling(filename + ".tmp");
        try (var file = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer data = ByteBuffer.wrap(bytes);
            while (data.hasRemaining()) file.write(data);
            file.force(true);
        }
        Files.move(temp, output); // No replacement. Controller must exit before the runner reads.
        if (failed) throw new IllegalStateException("Read-interest diagnostic failed; see bounded report");
    }
    private boolean witnessed() { return clearOnly ? clear.witnessed() : witness.witnessed(); }
    private void install(VirtualMachine vm, ReferenceType type) {
        if (!installed.add(type.name())) return;
        String[] point = (clearOnly ? CLEAR_POINTS : POINTS).get(type.name());
        var methods = type.methodsByName(point[0], point[1]);
        if (methods.size() != 1) throw new IllegalStateException("Pinned method differs");
        Method method = methods.get(0);
        Location location = method.location();
        if (clearOnly) {
            byte[] code = method.bytecodes();
            if (code.length != 16 || (code[15] & 255) != 177 || (code[2] & 255) != 181 || (code[12] & 255) != 182)
                throw new IllegalStateException("Pinned clear return differs");
            location = method.locationOfCodeIndex(15); // After native state mutations; no send/config breakpoint.
        }
        if (type.name().equals(CONFIG)) {
            // javap on pinned Netty 4.1.82: sole ARETURN at BCI 60, AFTER the
            // atomic flag update and read/clear callback. No entry suspension.
            byte[] code = method.bytecodes();
            if (code.length != 61 || (code[60] & 255) != 176)
                throw new IllegalStateException("Pinned setAutoRead return differs");
            location = method.locationOfCodeIndex(60);
        }
        var request = vm.eventRequestManager().createBreakpointRequest(location);
        request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        request.enable();
    }
    private void sample(BreakpointEvent hit) throws Exception {
        if (clearOnly) {
            if (clear.observe(hit, records)) lastSample = System.nanoTime();
            return;
        }
        var thread = hit.thread();
        var frames = thread.frames(0, Math.min(20, thread.frameCount()));
        var receiver = frames.get(0).thisObject();
        boolean configuration = hit.location().declaringType().name().equals(CONFIG);
        if (configuration) {
            ObjectReference candidate = (ObjectReference) field(receiver, CONFIG, "channel", "Lio/netty/channel/Channel;");
            if (!candidate.referenceType().name().equals(SOCKET)) return;
            if (channel == null) {
                if (frames.stream().noneMatch(f -> f.location().declaringType().name().equals("net.minecraft.network.Connection"))) return;
                channel = candidate; config = receiver;
                key = (ObjectReference) field(channel, NIO, "selectionKey", "Ljava/nio/channels/SelectionKey;");
                if (key == null || !key.referenceType().name().equals(KEY)) throw new IllegalStateException("Pinned selector key absent");
            }
            if (candidate.uniqueID() != channel.uniqueID()) return;
        } else if (key == null || receiver.uniqueID() != key.uniqueID()) return;
        if (records.size() >= 64) throw new IllegalStateException("Sample bound exceeded");
        List<Value> arguments = frames.get(0).getArgumentValues();
        if (arguments.size() != 1) throw new IllegalStateException("Pinned argument differs");
        int argument = configuration ? (((BooleanValue) arguments.get(0)).value() ? 1 : 0) : ((IntegerValue) arguments.get(0)).value();
        int autoRead = ((IntegerValue) field(config, CONFIG, "autoRead", "I")).value();
        if (autoRead != 0 && autoRead != 1) throw new IllegalStateException("Invalid autoRead state");
        int interestOps = ((IntegerValue) field(key, KEY, "interestOps", "I")).value();
        boolean pending = ((BooleanValue) field(channel, NIO, "readPending", "Z")).value();
        boolean clearCaller = frames.stream().anyMatch(f -> f.location().declaringType().name().equals(NIO)
                && f.location().method().name().equals("clearReadPending0"));
        String kind = configuration ? "CONFIG_SET" : "SELECTOR_WRITE";
        Boolean oldAutoRead = null;
        if (configuration) {
            var local = frames.get(0).visibleVariableByName("oldAutoRead");
            if (local == null || !local.signature().equals("Z")) throw new IllegalStateException("Pinned boolean local differs");
            oldAutoRead = ((BooleanValue) frames.get(0).getValue(local)).value();
        }
        // Distinguish a real false->true transition from an already-true no-op.
        if (configuration && argument == 1 && !oldAutoRead) witness.invalidateCandidate();
        else witness.observe(kind, argument, autoRead == 1, interestOps, clearCaller);
        JsonObject record = new JsonObject();
        record.addProperty("ordinal", records.size() + 1);
        record.addProperty("kind", kind); record.addProperty("argument", argument);
        record.addProperty("phase", configuration ? "BEFORE_RETURN" : "BEFORE_WRITE");
        if (configuration) record.addProperty("oldAutoRead", oldAutoRead);
        if (configuration && argument == 0) {
            var send = SendProtocolObservation.capture(frames);
            if (send.isPresent()) {
                record.addProperty("priorProtocol", send.get().priorProtocol().name());
                record.addProperty("packetProtocol", send.get().packetProtocol().name());
            }
        }
        record.addProperty("threadId", thread.uniqueID());
        String name = thread.name();
        record.addProperty("threadKind", name.matches("Netty Client IO #[0-9]+") ? "NETTY_CLIENT"
                : name.matches("Server Connector #[0-9]+") ? "SERVER_CONNECTOR" : "OTHER");
        record.addProperty("autoRead", autoRead == 1); record.addProperty("interestOps", interestOps);
        record.addProperty("readPending", pending); record.addProperty("clearCaller", clearCaller);
        JsonArray stack = new JsonArray();
        for (var frame : frames) {
            String owner = frame.location().declaringType().name();
            // Frame identities only: no packet/credential locals, strings or method invocation.
            if (owner.startsWith("io.netty.") || owner.startsWith("net.minecraft.network.Connection")
                    || owner.startsWith("net.minecraft.client.gui.screens.ConnectScreen") || owner.equals(KEY)) {
                String identity = owner + "#" + frame.location().method().name() + ":" + frame.location().lineNumber();
                if (identity.length() > 256) throw new IllegalStateException("Frame identity too large");
                stack.add(identity);
                if (stack.size() == 8) break;
            }
        }
        record.add("stack", stack); records.add(record); lastSample = System.nanoTime();
    }
    private static Value field(ObjectReference object, String owner, String name, String signature) {
        var matches = object.referenceType().allFields().stream()
                .filter(f -> f.declaringType().name().equals(owner) && f.name().equals(name) && f.signature().equals(signature)).toList();
        if (matches.size() != 1) throw new IllegalStateException("Pinned scalar field differs");
        return object.getValue(matches.get(0));
    }
}
