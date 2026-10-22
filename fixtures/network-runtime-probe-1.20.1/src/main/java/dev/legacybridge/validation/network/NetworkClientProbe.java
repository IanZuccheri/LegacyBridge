package dev.legacybridge.validation.network;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Actual client-side connection observer, isolated by Forge's verified Dist.CLIENT subscription. */
@Mod.EventBusSubscriber(modid = NetworkRuntimeProbe.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NetworkClientProbe {
    private static ClientActor actor;
    private static int logins;
    private static int logouts;
    private static long clientTicks;
    private NetworkClientProbe() { }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) { logins++; }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { if (event.getConnection() != null) logouts++; }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        clientTicks++;
        Minecraft game = Minecraft.getInstance();
        if (actor == null) {
            String role = System.getProperty("legacybridge.netqa.role", "");
            if (!Set.of("a", "b").contains(role)) throw new IllegalStateException("Actual client requires a/b role");
            actor = new ClientActor(game, role);
            try {
                new QaMailbox(role, command -> game.execute(() -> actor.accept(command)),
                        () -> game.execute(() -> { actor.disconnect(); game.stop(); })).start();
            } catch (IOException failure) { throw new IllegalStateException("Client QA mailbox initialization failed", failure); }
        }
        actor.tick();
    }
    private static final class ClientActor extends QaActor {
        private final Minecraft game;
        private final String role;
        private boolean connectionRequested;
        private Connection previous;
        private CompletableFuture<Void> sent;
        ClientActor(Minecraft game, String role) { this.game = game; this.role = role; }
        private Connection connection() { return game.getConnection() == null ? null : game.getConnection().getConnection(); }
        private boolean connected() { return game.level != null && game.player != null && connection() != null && connection().isConnected(); }
        void disconnect() {
            require(game.isSameThread(), "Disconnect escaped client thread");
            if (game.level != null) game.level.disconnect(); // Native transport disconnect precedes clearLevel, exactly as PauseScreen.
            game.clearLevel();
            game.setScreen(new TitleScreen());
        }
        @Override void begin(QaCommand command) {
            require(game.isSameThread(), "Client operation escaped native main thread");
            sent = null;
            switch (command.action()) {
                case "CONNECT" -> { require(!connected(), "Client already connected"); connectionRequested = false; }
                case "DISCONNECT" -> { previous = connection(); require(previous != null, "No connection to disconnect"); disconnect(); }
                case "RAW_C2S" -> { require(connected(), "No actual connection for wrong-direction packet"); sent = RawTransport.send(connection(), command.text("payload"), false); }
                case "EXPECT", "STATE", "STOP" -> { }
                default -> throw new IllegalStateException("Server-only action on client");
            }
        }
        @Override boolean satisfied(QaCommand command) {
            require(game.isSameThread(), "Client observation escaped native main thread");
            if (sent != null) { if (!sent.isDone()) return false; sent.getNow(null); }
            switch (command.action()) {
                case "CONNECT":
                    if (!connectionRequested && game.getOverlay() == null && game.screen instanceof TitleScreen) {
                        require(game.getSingleplayerServer() == null, "Integrated server is outside this test");
                        ConnectScreen.startConnecting(game.screen, game, ServerAddress.parseString("127.0.0.1:25591"),
                                new ServerData("LegacyBridge isolated QA", "127.0.0.1:25591", false), false);
                        connectionRequested = true;
                    }
                    return connected();
                case "DISCONNECT": return !connected() && !previous.isConnected() && FixtureAccess.read("sequence") == 0 && FixtureAccess.read("value") == 0;
                case "EXPECT": return connected() == command.flag("connected") && FixtureAccess.expected(command);
                default: return true;
            }
        }
        @Override JsonObject snapshot() {
            JsonObject result = new JsonObject();
            boolean connected = connected();
            // Observation only: never tick/replace a native connection to make a test pass.
            // Retained on deadline failures as well as successful command receipts.
            result.addProperty("screen", game.screen == null ? "NONE" : game.screen.getClass().getName());
            result.addProperty("overlay", game.getOverlay() == null ? "NONE" : game.getOverlay().getClass().getName());
            result.addProperty("clientTicks", clientTicks);
            result.addProperty("connectionRequested", connectionRequested);
            result.addProperty("connected", connected);
            result.addProperty("nativeMainThread", game.isSameThread());
            result.addProperty("threadId", Thread.currentThread().getId());
            result.addProperty("logins", logins); result.addProperty("logouts", logouts);
            result.addProperty("integratedServer", game.getSingleplayerServer() != null);
            result.addProperty("nativeLoopback", connected);
            if (connected) {
                RawTransport.assertLoopback(connection());
                String expected = role.equals("a") ? "LegacyNetA" : "LegacyNetB";
                require(game.player.getGameProfile().getName().equals(expected), "Synthetic client identity mismatch");
                result.addProperty("player", game.player.getGameProfile().getName());
            }
            FixtureAccess.snapshot(result);
            if (FixtureAccess.read("accepted") > 0) require(FixtureAccess.appliedThread() == Thread.currentThread().getId(), "Native S2C application did not run on this client's main thread");
            return result;
        }
    }
}
