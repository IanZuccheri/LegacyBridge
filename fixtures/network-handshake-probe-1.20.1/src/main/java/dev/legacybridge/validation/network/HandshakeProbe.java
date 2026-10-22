package dev.legacybridge.validation.network;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.List;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkRegistry;

/** Adversarial peer is QA only: never an adapter, codec, renamed legacy mod or admission rule. */
@Mod(HandshakeProbe.MOD_ID)
public final class HandshakeProbe {
    public static final String MOD_ID = "legacybridge_handshake_probe";
    static final HandshakeScenario SCENARIO = HandshakeScenario.parse(System.getProperty("legacybridge.handshake.case"));
    static final String ROLE = System.getProperty("legacybridge.netqa.role", "");
    private MinecraftServer server;
    private HandshakeControl control;
    private int logins, sends, maxPlayers;
    private boolean finishRequested, finished;
    private long connectionObserved;
    private boolean socketSampleRequested;
    public HandshakeProbe() {
        HandshakeScenario.checkRole(ROLE);
        if (SCENARIO.platformBaseline() && ROLE.equals("server")) {
            // Explicit platform control, NOT the converted mod or its predicate.
            // No codec, runtime adapter, original or converted class is involved.
            NetworkRegistry.newSimpleChannel(new ResourceLocation(MOD_ID, "required"),
                    () -> "1", "1"::equals, "1"::equals);
        }
        if (Boolean.getBoolean("legacybridge.handshake.packetRecording")) {
            // Register the target's existing events; never invoke /jfr start's broad config.
            require(net.minecraft.util.profiling.jfr.JvmProfiler.INSTANCE.isAvailable(), "Native JFR events unavailable");
        }
        if (SCENARIO.emulateProtocol(ROLE)) {
            // The bad peer advertises 2 but accepts the real fixture's 1. Consequently
            // the unmodified converted fixture, not this peer, makes the refusal.
            NetworkRegistry.newSimpleChannel(new ResourceLocation("legacybridge_network", "snapshot"),
                    () -> "2", "1"::equals, "1"::equals);
        }
        if (ROLE.equals("server")) {
            MinecraftForge.EVENT_BUS.addListener(this::started);
            MinecraftForge.EVENT_BUS.addListener(this::tick);
            MinecraftForge.EVENT_BUS.addListener(this::login);
        }
    }
    static boolean verifyFixture() {
        SCENARIO.validateInventory(ModList.get().getMods().stream().map(info -> info.getModId())
                .collect(java.util.stream.Collectors.toSet()), ROLE);
        boolean present = ModList.get().isLoaded("legacybridge_network");
        require(present == SCENARIO.fixturePresent(ROLE), "Fixture inventory differs from the explicit scenario");
        if (present) FixtureAccess.version();
        if (!SCENARIO.platformBaseline())
            require(ModList.get().getModContainerById("legacybridge_runtime").orElseThrow().getModInfo().getVersion().toString().equals("0.1.1-SNAPSHOT"), "Wrong public adapter identity");
        return present;
    }
    static void require(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
    private void started(ServerStartedEvent event) {
        server = event.getServer();
        require(server.isDedicatedServer() && server.isSameThread(), "Real dedicated server required");
        control = new HandshakeControl(ROLE, SCENARIO, () -> server.execute(this::finish), () -> server.execute(() -> server.halt(false)));
        control.ready(snapshot()); control.start();
    }
    private void login(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer) logins++; }
    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || control == null || finished || event.getServer() != server) return;
        try {
            maxPlayers = Math.max(maxPlayers, server.getPlayerCount());
            require(maxPlayers <= 1, "Only the single authorized native peer may connect");
            if (SCENARIO != HandshakeScenario.MATCHED) require(logins == 0 && maxPlayers == 0, "Rejected peer entered play");
            if (Boolean.getBoolean("legacybridge.handshake.socketSnapshot") && !socketSampleRequested) observeSocket();
            if (SCENARIO == HandshakeScenario.MATCHED && sends == 0 && server.getPlayerCount() == 1) {
                ServerPlayer player = server.getPlayerList().getPlayerByName("LegacyNetA");
                require(player != null, "Unknown native peer"); RawTransport.assertLoopback(player.connection.connection);
                FixtureAccess.send(player, 1, 427); sends++;
            }
            if (finishRequested && server.getPlayerCount() == 0) {
                require(logins == (SCENARIO == HandshakeScenario.MATCHED ? 1 : 0), "Unexpected login count");
                require(sends == (SCENARIO == HandshakeScenario.MATCHED ? 1 : 0), "Unexpected native send count");
                control.complete(snapshot()); finished = true;
            }
        } catch (RuntimeException failure) { finished = true; control.fail(failure); }
    }
    private void observeSocket() {
        require(server.isSameThread() && server.getConnection() != null, "Native server connection listener required");
        List<Connection> connections = server.getConnection().getConnections();
        Connection connection;
        synchronized (connections) { // Pinned listener uses this same monitor for iteration.
            require(connections.size() <= 1, "Unexpected handshake socket inventory");
            if (connections.isEmpty()) return;
            connection = connections.get(0);
        }
        if (!connection.isConnected()) return;
        if (connectionObserved == 0) connectionObserved = System.nanoTime();
        if (System.nanoTime() - connectionObserved < Duration.ofSeconds(5).toNanos()) return;
        HandshakeTransportDiagnostic.request(connection, control);
        socketSampleRequested = true;
    }
    private void finish() {
        require(server.isSameThread(), "Finish request escaped actual server thread");
        finishRequested = true; // Observe native disconnect on a later tick, never fake/remove a player.
    }
    private JsonObject snapshot() {
        require(server.isSameThread(), "Observation escaped actual server thread");
        JsonObject data = new JsonObject();
        data.addProperty("nativeMainThread", true); data.addProperty("dedicatedServer", server.isDedicatedServer());
        data.addProperty("players", server.getPlayerCount()); data.addProperty("maxPlayers", maxPlayers);
        data.addProperty("logins", logins); data.addProperty("nativeSends", sends);
        boolean fixture = verifyFixture(); data.addProperty("fixturePresent", fixture);
        if (fixture) {
            FixtureAccess.snapshot(data);
            require(FixtureAccess.read("accepted") == 0 && FixtureAccess.read("rejected") == 0, "S2C fixture handler ran on server");
        }
        return data;
    }
}
