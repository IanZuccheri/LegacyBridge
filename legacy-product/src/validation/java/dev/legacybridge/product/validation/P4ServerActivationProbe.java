package dev.legacybridge.product.validation;

import com.mojang.logging.LogUtils;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import static dev.legacybridge.product.validation.ProductProbeSupport.require;

/** A separate normal Forge startup, not a way to activate a staged mod inside the first client. */
@Mod.EventBusSubscriber(modid = LegacyBridgeProductProbe.MOD_ID, value = Dist.DEDICATED_SERVER,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class P4ServerActivationProbe {
    private static final AtomicBoolean FINISHED = new AtomicBoolean();
    private static MinecraftServer server;
    private static long deadline;

    private P4ServerActivationProbe() { }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("legacybridge.product.p4") || !Boolean.getBoolean("legacybridge.product.p4.relaunch")) return;
        server = event.getServer();
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        try {
            require(ModList.get().getMods().stream().filter(mod -> mod.getModId().equals("legacybridge_runtime")).count() == 1,
                    "Relaunch needs exactly one runtime identity");
            require(ModList.get().getModContainerById("legacybridge_runtime").orElseThrow().getModInfo().getVersion()
                    .toString().equals("0.1.1-SNAPSHOT"), "Runtime ABI version changed");
            require(System.getProperty(P4FixturePreflight.EXECUTED_PROPERTY) == null, "An original negative fixture was loaded");
            for (String originalId : java.util.List.of("p4_unsupported", "p4_first", "p4_second", "p4_ambiguous", "p4_target", "p4_duplicate"))
                require(!ModList.get().isLoaded(originalId), "Original input was discovered as a mod: " + originalId);
            ProductServerProbe.verifyContent(server);
            P4ProbeFiles.verifyRelaunch().whenComplete((evidence, failure) -> server.execute(() -> finish(evidence, failure)));
        } catch (Throwable failure) { finish(null, failure); }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && server != null && !FINISHED.get() && System.nanoTime() > deadline)
            finish(null, new IllegalStateException("P4 activation timeout"));
    }

    private static void finish(String evidence, Throwable failure) {
        if (!FINISHED.compareAndSet(false, true)) return;
        if (failure == null) LogUtils.getLogger().info("LEGACYBRIDGE_P4_ACTIVATION_OK item=bridge_ingot block=bridge_block recipe=9_to_1 negativeRecipe=passed tags=passed separateBootstrap=true {}", evidence);
        else LogUtils.getLogger().error("LEGACYBRIDGE_P4_ACTIVATION_FAILED", failure);
        P4ProbeFiles.close();
        server.execute(() -> server.halt(false));
    }
}
