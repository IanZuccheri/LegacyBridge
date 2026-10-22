package dev.legacybridge.fixture.network;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

/**
 * Original Java 8 fixture. Only actual S2C delivery invokes the snapshot effect.
 * All state access is a native client-main-thread contract; no game/world state is read.
 * Content errors are counted and logged, not disconnected. Wrong direction is rejected by Forge.
 */
@Mod("legacybridge_network")
public final class NetworkSnapshot {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("legacybridge_network", "snapshot"),
            NetworkSnapshot::protocol, NetworkSnapshot::accepts, NetworkSnapshot::accepts);

    private static NetworkManager activeConnection;
    private static int lastSequence;
    private static int currentValue;
    private static int acceptedCount;
    private static int rejectedCount;
    private static int rejectionLogs;
    private static long appliedThreadId;

    public NetworkSnapshot() {
        CHANNEL.registerMessage(0, SnapshotMessage.class,
                NetworkSnapshot::encode, NetworkSnapshot::decode, NetworkSnapshot::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static String protocol() {
        return "1";
    }

    private static boolean accepts(String remoteProtocol) {
        return "1".equals(remoteProtocol);
    }

    private static void encode(SnapshotMessage message, PacketBuffer buffer) {
        buffer.writeInt(message.sequence);
        buffer.writeInt(message.value);
    }

    private static SnapshotMessage decode(PacketBuffer buffer) {
        if (buffer.readableBytes() != 8) {
            return new SnapshotMessage(-1, -1);
        }
        return new SnapshotMessage(buffer.readInt(), buffer.readInt());
    }

    private static void handle(SnapshotMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> apply(message, context.getNetworkManager()));
        context.setPacketHandled(true);
    }

    private static void apply(SnapshotMessage message, NetworkManager connection) {
        if (!connection.isConnected()) {
            reject("NETWORK_CLOSED_CONTEXT");
            return;
        }
        if (message.sequence <= 0 || message.value < 0 || message.value > 1000) {
            reject("NETWORK_INVALID_PAYLOAD");
            return;
        }
        if (activeConnection == null || !activeConnection.isConnected()) {
            if (message.sequence != 1) {
                reject("NETWORK_SESSION_MUST_START_AT_ONE");
                return;
            }
            activeConnection = connection;
            lastSequence = 0;
            currentValue = 0;
            rejectionLogs = 0;
        }
        if (activeConnection != connection) {
            reject("NETWORK_DIFFERENT_LIVE_CONNECTION");
            return;
        }
        // Never overflow lastSequence + 1 and never advance across gaps/duplicates.
        if (lastSequence == Integer.MAX_VALUE || message.sequence != lastSequence + 1) {
            reject("NETWORK_OUT_OF_ORDER");
            return;
        }
        lastSequence = message.sequence;
        currentValue = message.value;
        if (acceptedCount != Integer.MAX_VALUE) {
            acceptedCount++;
        }
        appliedThreadId = Thread.currentThread().getId();
    }

    private static void reject(String code) {
        if (rejectedCount != Integer.MAX_VALUE) {
            rejectedCount++;
        }
        if (rejectionLogs < 20) {
            System.err.println(code);
            rejectionLogs++;
        } else if (rejectionLogs == 20) {
            System.err.println("NETWORK_REJECTIONS_RATE_LIMITED");
            rejectionLogs++;
        }
    }

    /** Native server-thread endpoint for the separately authorized runtime probe. */
    public static void sendTo(ServerPlayerEntity player, int sequence, int value) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SnapshotMessage(sequence, value));
    }

    /** Zero sequence means no visible snapshot; a legitimate zero value is distinguished by sequence. */
    public static int sequence() {
        return activeConnection == null || !activeConnection.isConnected() ? 0 : lastSequence;
    }

    public static int value() {
        return activeConnection == null || !activeConnection.isConnected() ? 0 : currentValue;
    }

    /** Cumulative, saturating process-lifetime count, including previous connections. */
    public static int accepted() {
        return acceptedCount;
    }

    /** Does not include unknown discriminators or wrong directions rejected before this handler. */
    public static int rejected() {
        return rejectedCount;
    }

    /** Native thread that performed the most recent valid application; zero before the first one. */
    public static long lastThreadId() {
        return appliedThreadId;
    }
}
