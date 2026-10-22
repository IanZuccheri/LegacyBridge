package dev.legacybridge.product.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/** Physical-client subscriber: Forge must not resolve this class on a dedicated server. */
@Mod.EventBusSubscriber(modid = "legacybridge_runtime", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientMenuHooks {
    private static ConversionScreen activeScreen;
    private static PreservedDeveloperScreen preservedScreen;
    private static final boolean PRESERVED_DEVELOPER_UI = Boolean.getBoolean("legacybridge.developer.preservedUi");
    private ClientMenuHooks() { }

    /** Normal owned-screen navigation only; not a veto of OS close, QuickPlay or direct world APIs. */
    @SubscribeEvent
    public static void beforeScreenOpen(ScreenEvent.Opening event) {
        if (activeScreen != null && activeScreen.controller().isInstalling()
                && activeScreen.owns(event.getCurrentScreen()) && !activeScreen.owns(event.getNewScreen())) {
            event.setCanceled(true);
        }
        if (preservedScreen != null && preservedScreen.isInstalling()
                && preservedScreen.owns(event.getCurrentScreen()) && !preservedScreen.owns(event.getNewScreen()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void afterClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || preservedScreen == null) return;
        if (preservedScreen.isStopped()) preservedScreen = null;
        else preservedScreen.publishSession();
    }

    @SubscribeEvent
    public static void afterTitleInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen title)) return;
        if (event.getListenersList().stream().anyMatch(ConversionMenuButton.class::isInstance)) return;
        event.addListener(Button.builder(Component.translatable("legacybridge.menu.convert"), button -> {
            if (!available()) return;
            activeScreen = new ConversionScreen(title, FMLPaths.GAMEDIR.get());
            Minecraft.getInstance().setScreen(activeScreen);
        })
                .bounds(Math.max(4, title.width - 128), 6, 122, 20).build(ConversionMenuButton::new));
        if (PRESERVED_DEVELOPER_UI) event.addListener(Button.builder(PreservedDeveloperScreen.text("menu"), button -> {
            if (!available()) return;
            preservedScreen = new PreservedDeveloperScreen(title, FMLPaths.GAMEDIR.get());
            Minecraft.getInstance().setScreen(preservedScreen);
        }).bounds(Math.max(4, title.width - 128), 30, 122, 20).build(PreservedMenuButton::new));
    }

    @SubscribeEvent
    public static void afterTitleRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen title)) return;
        boolean available = available();
        title.children().forEach(listener -> {
            if (listener instanceof ConversionMenuButton button) {
                button.active = available;
                button.setMessage(Component.translatable(available ? "legacybridge.menu.convert" : "legacybridge.menu.closing"));
            }
            if (listener instanceof PreservedMenuButton button) button.active = available;
        });
    }

    private static boolean available() {
        if (activeScreen != null && activeScreen.isStopped()) activeScreen = null;
        if (preservedScreen != null && preservedScreen.isStopped()) preservedScreen = null;
        return activeScreen == null && preservedScreen == null;
    }

    /** Marker prevents duplicate insertion if another integration emits init twice. */
    private static final class ConversionMenuButton extends Button {
        private ConversionMenuButton(Builder builder) { super(builder); }
    }
    private static final class PreservedMenuButton extends Button {
        private PreservedMenuButton(Builder builder) { super(builder); }
    }
}
