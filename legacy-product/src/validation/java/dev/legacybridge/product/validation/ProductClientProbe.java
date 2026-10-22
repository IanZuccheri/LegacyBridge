package dev.legacybridge.product.validation;

import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.legacybridge.product.client.ConversionScreen;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import static dev.legacybridge.product.validation.ProductProbeSupport.require;

/** Client-only test automation through real widgets; no reflection or production test hooks. */
@Mod.EventBusSubscriber(modid = LegacyBridgeProductProbe.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProductClientProbe {
    private enum Phase { TITLE, TITLE_SCREENSHOT, INVENTORY, ANALYSIS, CONVERSION, SERVICE, SERVICE_SCREENSHOT, DONE }
    private static Phase phase = Phase.TITLE;
    private static long deadline;
    private static int ticks;
    private static int transitionTick;
    private static int conversionTicks;
    private static int conversionFrames;
    private static int titleVisibleTicks;
    private static List<String> loadedBefore;
    private static CompletableFuture<String> evidence;
    private static CompletableFuture<Component> screenshot;
    private static String serviceEvidence;

    private ProductClientProbe() { }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase == Phase.DONE || !ProductProbeSupport.enabled("client")) return;
        Minecraft game = Minecraft.getInstance();
        try {
            if (deadline == 0) deadline = ProductProbeSupport.deadline();
            require(System.nanoTime() <= deadline, "Client probe timeout in phase " + phase);
            ticks++;
            if (game.getOverlay() != null || game.screen == null) return;
            switch (phase) {
                case TITLE -> {
                    // Actual 1.20.1 first-run path: GUI_CONTINUE invokes onClose(),
                    // persists onboardAccessibility=false, then opens a native TitleScreen.
                    // Never alter options directly or bypass the title menu under test.
                    if (game.screen instanceof AccessibilityOnboardingScreen) {
                        Button proceed = button(game.screen, "gui.continue");
                        require(proceed.active, "Native onboarding Continue button is inactive");
                        proceed.onPress();
                        require(game.screen instanceof TitleScreen, "Native onboarding did not open the title screen");
                        LogUtils.getLogger().info("LEGACYBRIDGE_PRODUCT_ONBOARDING_OK nativeContinue=pressed");
                        return;
                    }
                    if (!(game.screen instanceof TitleScreen) || ++titleVisibleTicks < 50) return;
                    verifyAssets(game);
                    loadedBefore = loadedMods();
                    require(game.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                            .filter(button -> label(button, "legacybridge.menu.convert")).count() == 1,
                            "Expected exactly one native conversion menu button");
                    screenshot = capture(game, "title");
                    transition(Phase.TITLE_SCREENSHOT);
                }
                case TITLE_SCREENSHOT -> {
                    if (!screenshot.isDone()) return;
                    requireScreenshot();
                    require(game.screen instanceof TitleScreen, "Title screen changed during screenshot capture");
                    button(game.screen, "legacybridge.menu.convert").onPress();
                    require(game.screen instanceof ConversionScreen, "Native title button did not open ConversionScreen");
                    var field = game.screen.children().stream().filter(EditBox.class::isInstance)
                            .map(EditBox.class::cast).findFirst().orElseThrow();
                    field.setValue(ProductProbeSupport.bundle().toString());
                    transition(Phase.INVENTORY);
                }
                case INVENTORY -> {
                    require(game.screen instanceof ConversionScreen, "Conversion screen unexpectedly closed");
                    if (ticks - transitionTick < 3 || !button(game.screen, "legacybridge.screen.analyze").active) return;
                    button(game.screen, "legacybridge.screen.analyze").onPress();
                    transition(Phase.ANALYSIS);
                }
                case ANALYSIS -> {
                    if (ticks - transitionTick < 3 || !button(game.screen, "legacybridge.screen.copy_paths").active
                            || !button(game.screen, "legacybridge.screen.convert").active) return;
                    button(game.screen, "legacybridge.screen.convert").onPress();
                    transition(Phase.CONVERSION);
                }
                case CONVERSION -> {
                    conversionTicks++;
                    if (ticks - transitionTick < 3 || !button(game.screen, "legacybridge.screen.copy_paths").active
                            || !button(game.screen, "legacybridge.screen.convert").active) return;
                    require(conversionTicks >= 3, "No client ticks observed while conversion ran");
                    require(conversionFrames > 0, "No rendered frame observed while conversion ran");
                    require(loadedBefore.equals(loadedMods()), "GUI operations changed the loaded-mod set");
                    evidence = ProductProbeSupport.run("client", deadline, true);
                    transition(Phase.SERVICE);
                }
                case SERVICE -> {
                    require(game.screen instanceof ConversionScreen, "Conversion screen closed during background service proof");
                    if (!evidence.isDone()) return;
                    // Completion is already observable; no blocking wait or filesystem work occurs on this tick.
                    serviceEvidence = evidence.getNow(null);
                    require(serviceEvidence != null, "Background proof returned no result");
                    require(loadedBefore.equals(loadedMods()), "Background service changed the client loaded-mod set");
                    screenshot = capture(game, "conversion-complete");
                    transition(Phase.SERVICE_SCREENSHOT);
                }
                case SERVICE_SCREENSHOT -> {
                    if (!screenshot.isDone()) return;
                    requireScreenshot();
                    LogUtils.getLogger().info("LEGACYBRIDGE_PRODUCT_CLIENT_OK titleButton=1 nativeScreen=opened guiAnalysis=passed guiConversion=passed conversionTicks={} conversionFrames={} {}",
                            conversionTicks, conversionFrames, serviceEvidence);
                    finish(game);
                }
                case DONE -> { }
            }
        } catch (Throwable failure) {
            LogUtils.getLogger().error("LEGACYBRIDGE_PRODUCT_CLIENT_FAILED phase=" + phase, failure);
            finish(game);
        }
    }

    private static CompletableFuture<Component> capture(Minecraft game, String label) {
        require(RenderSystem.isOnRenderThread(), "Framebuffer screenshot must be requested on the render thread");
        String filename = "legacybridge-product-" + label + "-" + UUID.randomUUID() + ".png";
        var completion = new CompletableFuture<Component>();
        // Exact target API captures the framebuffer now and writes PNG on Util.ioPool().
        // The unique operation-owned filename is beneath this isolated gameDir/screenshots.
        // Only the completed callback is observed by subsequent ticks; no render-thread wait.
        Screenshot.grab(game.gameDirectory, filename, game.getMainRenderTarget(), message -> {
            LogUtils.getLogger().info("LEGACYBRIDGE_PRODUCT_SCREENSHOT file={} result={}", filename, message.getString());
            completion.complete(message);
        });
        return completion;
    }

    private static void requireScreenshot() {
        Component message = screenshot.getNow(null);
        require(message != null && message.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals("screenshot.success"), "Native screenshot failed or was cancelled: " + message);
    }

    @SubscribeEvent
    public static void rendered(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END && phase == Phase.CONVERSION) conversionFrames++;
    }

    private static List<String> loadedMods() {
        return ModList.get().getMods().stream().map(info -> info.getModId() + ":" + info.getVersion()).sorted().toList();
    }

    private static void transition(Phase next) { phase = next; transitionTick = ticks; }

    private static void finish(Minecraft game) {
        phase = Phase.DONE;
        ProductProbeSupport.close();
        if (game.screen instanceof ConversionScreen screen) screen.onClose();
        game.stop();
    }

    private static Button button(Screen screen, String key) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(value -> label(value, key)).findFirst().orElseThrow(() -> new IllegalStateException("Missing button " + key));
    }

    private static boolean label(Button button, String key) {
        return button.getMessage().getString().equals(Component.translatable(key).getString());
    }

    private static void verifyAssets(Minecraft game) {
        var ingot = ForgeRegistries.ITEMS.getValue(id("bridge_ingot"));
        var block = ForgeRegistries.BLOCKS.getValue(id("bridge_block"));
        require(ingot != null && block != null, "Client fixture registrations missing");
        var models = game.getModelManager();
        require(models.getModel(new ModelResourceLocation(id("bridge_ingot"), "inventory")) != models.getMissingModel(), "Item model missing");
        require(models.getModel(new ModelResourceLocation(id("bridge_block"), "inventory")) != models.getMissingModel(), "Block item model missing");
        require(game.getBlockRenderer().getBlockModel(block.defaultBlockState()) != models.getMissingModel(), "Blockstate model missing");
        for (String texture : new String[]{"item/bridge_ingot", "block/bridge_block"}) {
            var sprite = game.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id(texture));
            require(sprite.contents().name().equals(id(texture)), "Texture atlas sprite missing");
        }
        require(!I18n.get("item.legacybridge_example.bridge_ingot").equals("item.legacybridge_example.bridge_ingot"), "Item localization missing");
        require(game.getSoundManager().getSoundEvent(id("bridge_ping")) != null, "Simple sound event missing");
        LogUtils.getLogger().info("LEGACYBRIDGE_PRODUCT_CLIENT_ASSETS_OK models=3 sprites=2 language=resolved soundEvent=resolved");
    }

    private static ResourceLocation id(String path) { return new ResourceLocation(LegacyBridgeProductProbe.FIXTURE_ID, path); }
}
