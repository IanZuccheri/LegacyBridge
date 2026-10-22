package dev.legacybridge.validation.network;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

/** Dedicated-server-safe entry point: no client imports or client class resolution. */
@Mod(NetworkRuntimeProbe.MOD_ID)
public final class NetworkRuntimeProbe {
    public static final String MOD_ID = "legacybridge_network_probe";
    private ServerActor actor;
    private MinecraftServer server;
    public NetworkRuntimeProbe() {
        String role = System.getProperty("legacybridge.netqa.role", "");
        if (!Set.of("server", "a", "b").contains(role)) throw new IllegalStateException("Explicit isolated test role is mandatory");
        if (role.equals("server")) {
            MinecraftForge.EVENT_BUS.addListener(this::started);
            MinecraftForge.EVENT_BUS.addListener(this::tick);
        }
    }
    private void started(ServerStartedEvent event) {
        if (server != null) throw new IllegalStateException("Second server is outside the finite QA scenario");
        server = event.getServer();
        QaActor.require(server.isDedicatedServer() && server.isSameThread(), "Actual dedicated server thread required");
        actor = new ServerActor(server);
        try {
            new QaMailbox("server", command -> server.execute(() -> actor.accept(command)),
                    () -> server.execute(() -> server.halt(false))).start();
        } catch (IOException failure) { throw new IllegalStateException("QA mailbox initialization failed", failure); }
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && actor != null && event.getServer() == server) actor.tick();
    }
    private static final class ServerActor extends QaActor {
        private final MinecraftServer server;
        private CompletableFuture<Void> sent;
        ServerActor(MinecraftServer server) { this.server = server; }
        private ServerPlayer player(String target) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(target.equals("a") ? "LegacyNetA" : "LegacyNetB");
            require(player != null, "Required native player not connected");
            RawTransport.assertLoopback(player.connection.connection);
            return player;
        }
        @Override void begin(QaCommand command) {
            require(server.isSameThread(), "Server operation escaped native main thread");
            sent = null;
            switch (command.action()) {
                case "SEND" -> FixtureAccess.send(player(command.text("target")), command.number("sequence"), command.number("value"));
                case "RAW_S2C" -> sent = RawTransport.send(player(command.text("target")).connection.connection, command.text("payload"), true);
                case "EXPECT_PEERS", "STATE", "STOP" -> { }
                default -> throw new IllegalStateException("Client-only action on dedicated server");
            }
        }
        @Override boolean satisfied(QaCommand command) {
            require(server.isSameThread(), "Server observation escaped native main thread");
            if (sent != null) { if (!sent.isDone()) return false; sent.getNow(null); }
            if (command.action().equals("EXPECT_PEERS")) {
                return (server.getPlayerList().getPlayerByName("LegacyNetA") != null) == command.flag("a") &&
                        (server.getPlayerList().getPlayerByName("LegacyNetB") != null) == command.flag("b");
            }
            return true;
        }
        @Override JsonObject snapshot() {
            JsonObject result = new JsonObject();
            result.addProperty("dedicatedServer", server.isDedicatedServer());
            result.addProperty("nativeMainThread", server.isSameThread());
            result.addProperty("threadId", Thread.currentThread().getId());
            result.addProperty("players", server.getPlayerList().getPlayerCount());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                require(Set.of("LegacyNetA", "LegacyNetB").contains(player.getGameProfile().getName()), "Unexpected player in isolated QA server");
                RawTransport.assertLoopback(player.connection.connection);
            }
            result.addProperty("a", server.getPlayerList().getPlayerByName("LegacyNetA") != null);
            result.addProperty("b", server.getPlayerList().getPlayerByName("LegacyNetB") != null);
            result.addProperty("allConnectionsNativeLoopback", true);
            FixtureAccess.snapshot(result);
            require(FixtureAccess.read("accepted") == 0 && FixtureAccess.read("rejected") == 0,
                    "S2C handler must not apply or reject on server, including wrong-direction test");
            return result;
        }
    }
}
