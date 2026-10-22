package dev.legacybridge.validation.network;

import com.google.gson.JsonObject;
import java.time.Duration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.ModMismatchDisconnectedScreen;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Native UI/connection observer; no fabricated disconnect screen or forced handshake outcome. */
@Mod.EventBusSubscriber(modid = HandshakeProbe.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HandshakeClient {
    private static HandshakeControl control;
    private static boolean requested, finished;
    private static int logins;
    private static long deadline;
    private static long connectStarted;
    private static boolean socketSampleRequested;
    private HandshakeClient() { }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) { logins++; }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        Minecraft game = Minecraft.getInstance();
        try {
            if (control == null) {
                HandshakeProbe.require(HandshakeProbe.ROLE.equals("a"), "Client role must be a");
                control = new HandshakeControl("a", HandshakeProbe.SCENARIO,
                        () -> game.execute(() -> { throw new IllegalStateException("Client cannot consume server finish request"); }),
                        () -> game.execute(() -> close(game)));
                control.ready(snapshot(game)); control.start();
                deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
            }
            HandshakeProbe.require(game.getSingleplayerServer() == null, "Integrated server is forbidden");
            if (System.nanoTime() > deadline) throw new IllegalStateException("Native handshake deadline: " + snapshot(game));
            if (!requested && control.recordingReady() && game.getOverlay() == null && game.screen instanceof TitleScreen) {
                ConnectScreen.startConnecting(game.screen, game, ServerAddress.parseString("127.0.0.1:25591"),
                        new ServerData("LegacyBridge handshake QA", "127.0.0.1:25591", false), false);
                requested = true;
                connectStarted = System.nanoTime();
            }
            if (!requested) return;
            if (Boolean.getBoolean("legacybridge.handshake.socketSnapshot") && !socketSampleRequested
                    && System.nanoTime() - connectStarted >= Duration.ofSeconds(5).toNanos()
                    && game.screen instanceof ConnectScreen connecting) {
                socketSampleRequested = HandshakeSocketDiagnostic.request(connecting, control);
            }
            if (HandshakeProbe.SCENARIO == HandshakeScenario.MATCHED) {
                if (connected(game) && FixtureAccess.read("sequence") == 1 && FixtureAccess.read("value") == 427) {
                    HandshakeProbe.require(logins == 1 && FixtureAccess.read("accepted") == 1 && FixtureAccess.read("rejected") == 0, "Unexpected positive snapshot counters");
                    HandshakeProbe.require(FixtureAccess.appliedThread() == Thread.currentThread().getId(), "Application escaped client main thread");
                    RawTransport.assertLoopback(game.getConnection().getConnection());
                    complete(snapshot(game));
                }
            } else {
                HandshakeProbe.require(logins == 0 && !connected(game), "Rejected peer entered native play");
                if (game.screen instanceof ModMismatchDisconnectedScreen || game.screen instanceof DisconnectedScreen) complete(snapshot(game));
            }
        } catch (RuntimeException failure) {
            finished = true;
            if (control == null) throw failure;
            control.fail(failure);
        }
    }
    private static void complete(JsonObject data) { finished = true; control.complete(data); }
    private static boolean connected(Minecraft game) { return game.level != null && game.player != null && game.getConnection() != null && game.getConnection().getConnection().isConnected(); }
    private static JsonObject snapshot(Minecraft game) {
        HandshakeProbe.require(game.isSameThread(), "Client observation escaped main thread");
        JsonObject data = new JsonObject();
        data.addProperty("nativeMainThread", true); data.addProperty("threadId", Thread.currentThread().getId());
        data.addProperty("screen", game.screen == null ? "NONE" : game.screen.getClass().getName());
        data.addProperty("connected", connected(game)); data.addProperty("logins", logins); data.addProperty("connectionRequested", requested);
        data.addProperty("integratedServer", game.getSingleplayerServer() != null);
        boolean fixture = HandshakeProbe.verifyFixture(); data.addProperty("fixturePresent", fixture);
        if (fixture) FixtureAccess.snapshot(data); // Absent is NOT represented by fictitious zero counters.
        return data;
    }
    private static void close(Minecraft game) {
        if (game.level != null) game.level.disconnect();
        game.clearLevel(); game.stop();
    }
}
