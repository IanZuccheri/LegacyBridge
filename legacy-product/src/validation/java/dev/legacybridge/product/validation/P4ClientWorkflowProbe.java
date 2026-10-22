package dev.legacybridge.product.validation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import dev.legacybridge.product.client.ConversionEntryList;
import dev.legacybridge.product.client.ConversionScreen;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import static dev.legacybridge.product.validation.ProductProbeSupport.require;

/** Separate P4 native-widget workflow probe. All filesystem assertions run on its bounded QA worker. */
@Mod.EventBusSubscriber(modid = LegacyBridgeProductProbe.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class P4ClientWorkflowProbe {
    private enum Phase {
        PREPARE, TITLE, INVENTORY, FILTER_INVALID, INVALID_SELECTED, SCALE, SELECT_POSITIVE, SELECT_NEGATIVE,
        QUEUE, JOBS, VERIFY_STAGED, DETAILS, RETURN_DETAILS, DECLINE, VERIFY_DECLINE,
        APPLY, PENDING, VERIFY_APPLIED, CAPTURE, CANCEL_QUEUE, START_CANCEL_JOB, CANCEL_CLOSE, WAIT_STOP, REOPEN, VERIFY_CLEANUP, DONE
    }
    private static Phase phase = Phase.PREPARE;
    private static CompletableFuture<P4FixturePreflight.Prepared> preparing;
    private static P4FixturePreflight.Prepared fixtures;
    private static CompletableFuture<String> fileCheck;
    private static CompletableFuture<Component> screenshot;
    private static long deadline;
    private static int ticks;
    private static int phaseTick;
    private static int titleTicks;
    private static int workTicks;
    private static int workFrames;
    private static int originalScale;
    private static int nextScale = 1;
    private static List<String> loadedBefore;
    private static ConversionScreen closing;
    private static boolean cancelClicked;
    private static String appliedEvidence;

    private P4ClientWorkflowProbe() { }

    private static boolean enabled() {
        return Boolean.getBoolean("legacybridge.product.p4") && !Boolean.getBoolean("legacybridge.product.p4.relaunch")
                && !Boolean.getBoolean("legacybridge.product.p5");
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled() || phase == Phase.DONE) return;
        Minecraft game = Minecraft.getInstance();
        try {
            if (deadline == 0) {
                int seconds = Integer.getInteger("legacybridge.product.p4.timeoutSeconds", 180);
                require(seconds >= 60 && seconds <= 300, "P4 timeout must be 60..300 seconds");
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
                originalScale = game.options.guiScale().get();
                loadedBefore = loadedMods();
                require(!ModList.get().isLoaded(LegacyBridgeProductProbe.FIXTURE_ID), "Fresh P4 apply scenario must not start with converted fixture installed");
                preparing = P4ProbeFiles.prepare();
            }
            require(System.nanoTime() < deadline, "P4 UI timeout in " + phase);
            ticks++;
            if (game.getOverlay() != null || game.screen == null) return;
            switch (phase) {
                case PREPARE -> {
                    if (!preparing.isDone()) return;
                    fixtures = preparing.getNow(null);
                    require(fixtures != null, "Preflight produced no fixture evidence");
                    LogUtils.getLogger().info("LEGACYBRIDGE_P4_INVENTORY_FIXTURES_OK count=8 corrupt=1 multipleIds=2 incompatible=2 duplicates=2 originalClassesExecuted=false");
                    move(Phase.TITLE);
                }
                case TITLE -> {
                    if (game.screen instanceof AccessibilityOnboardingScreen) {
                        press(game.screen, "gui.continue");
                        return;
                    }
                    if (!(game.screen instanceof TitleScreen) || ++titleTicks < 35) return;
                    require(menuButtons(game.screen) == 1, "Duplicate/missing title button");
                    press(game.screen, "legacybridge.menu.convert");
                    require(game.screen instanceof ConversionScreen, "Native menu did not open conversion UI");
                    field(game.screen, "mapping").setValue(ProductProbeSupport.bundle().toString());
                    move(Phase.INVENTORY);
                }
                case INVENTORY -> {
                    if (list(game.screen).children().size() < 8) return;
                    require(list(game.screen).children().size() == 8, "Unexpected P4 input rows");
                    typeSearch(game.screen, P4FixturePreflight.CORRUPT);
                    move(Phase.FILTER_INVALID);
                }
                case FILTER_INVALID -> {
                    if (!after(2)) return;
                    require(list(game.screen).children().size() == 1, "Search did not filter corrupt input");
                    String row = narration(list(game.screen).children().get(0));
                    require(row.contains(P4FixturePreflight.CORRUPT)
                            && row.contains(Component.translatable("legacybridge.inventory.invalid").getString()), "Corrupt row lacks invalid evidence");
                    focusRow(game.screen, list(game.screen).children().get(0));
                    game.screen.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0);
                    game.screen.keyReleased(GLFW.GLFW_KEY_SPACE, 0, 0);
                    move(Phase.INVALID_SELECTED);
                }
                case INVALID_SELECTED -> {
                    if (!after(2)) return;
                    require(!button(game.screen, "legacybridge.screen.convert").active, "Invalid input became convertible");
                    typeSearch(game.screen, "bridge_ingot_no_match");
                    move(Phase.SCALE);
                }
                case SCALE -> {
                    if (!after(2)) return;
                    require(list(game.screen).children().isEmpty(), "No-match search did not remain bounded/empty");
                    if (nextScale <= 3) {
                        game.options.guiScale().set(nextScale++);
                        game.resizeDisplay();
                        require(field(game.screen, "search").getValue().equals("bridge_ingot_no_match"), "Search state lost on scale resize");
                        require(field(game.screen, "mapping").getValue().equals(ProductProbeSupport.bundle().toString()), "Mapping field lost on scale resize");
                        assertBounds(game.screen);
                        phaseTick = ticks;
                        return;
                    }
                    game.options.guiScale().set(originalScale);
                    game.resizeDisplay();
                    typeSearch(game.screen, fixtures.original().getFileName().toString());
                    move(Phase.SELECT_POSITIVE);
                }
                case SELECT_POSITIVE -> {
                    if (!after(2)) return;
                    require(list(game.screen).children().size() == 1, "Positive search mismatch");
                    toggleFirst(game.screen);
                    typeSearch(game.screen, P4FixturePreflight.UNSUPPORTED);
                    move(Phase.SELECT_NEGATIVE);
                }
                case SELECT_NEGATIVE -> {
                    if (!after(2)) return;
                    require(list(game.screen).children().size() == 1, "Unsupported search mismatch");
                    toggleFirst(game.screen);
                    typeSearch(game.screen, "");
                    move(Phase.QUEUE);
                }
                case QUEUE -> {
                    if (!after(2)) return;
                    long marked = list(game.screen).children().stream().filter(row -> narration(row)
                            .endsWith(". " + Component.translatable("legacybridge.screen.checked").getString())).count();
                    require(marked == 2, "Expected two independently selected inputs");
                    press(game.screen, "legacybridge.screen.convert");
                    move(Phase.JOBS);
                }
                case JOBS -> {
                    workTicks++;
                    if (list(game.screen).children().size() != 2) return;
                    boolean positive = hasJob(game.screen, "converted");
                    boolean blocked = hasJob(game.screen, "blocked");
                    if (!positive || !blocked) return;
                    require(workTicks > 0 && workFrames > 0, "No live ticks/frames observed during queue processing");
                    require(loadedBefore.equals(loadedMods()), "Conversion queue changed loaded mods");
                    fileCheck = P4ProbeFiles.verifyStaged(fixtures);
                    move(Phase.VERIFY_STAGED);
                }
                case VERIFY_STAGED -> {
                    if (!fileCheck.isDone()) return;
                    LogUtils.getLogger().info("LEGACYBRIDGE_P4_QUEUE_OK {} workTicks={} workFrames={}", fileCheck.getNow(null), workTicks, workFrames);
                    var positive = list(game.screen).children().stream().filter(row -> jobState(row, "converted")).findFirst().orElseThrow();
                    focusRow(game.screen, positive);
                    move(Phase.DETAILS);
                }
                case DETAILS -> {
                    if (!after(2)) return;
                    press(game.screen, "legacybridge.screen.details");
                    require(!(game.screen instanceof ConversionScreen), "Details button did not open a child screen");
                    move(Phase.RETURN_DETAILS);
                }
                case RETURN_DETAILS -> {
                    if (!after(2)) return;
                    for (String key : List.of("open_compatibility", "open_transformations", "open_conversion", "open_result"))
                        require(button(game.screen, "legacybridge.screen." + key).active, "Successful job report/result is not accessible: " + key);
                    // Do not start desktop applications as part of this bounded game probe.
                    press(game.screen, "gui.back");
                    require(game.screen instanceof ConversionScreen, "Details back navigation lost the session");
                    move(Phase.DECLINE);
                }
                case DECLINE -> {
                    if (!after(2)) return;
                    press(game.screen, "legacybridge.screen.apply");
                    require(game.screen instanceof ConfirmScreen, "Apply skipped explicit native confirmation");
                    press(game.screen, "gui.cancel");
                    fileCheck = P4ProbeFiles.verifyNotInstalled();
                    move(Phase.VERIFY_DECLINE);
                }
                case VERIFY_DECLINE -> {
                    if (!fileCheck.isDone()) return;
                    LogUtils.getLogger().info("LEGACYBRIDGE_P4_APPLY_DECLINED_OK {}", fileCheck.getNow(null));
                    move(Phase.APPLY);
                }
                case APPLY -> {
                    if (!after(2)) return;
                    press(game.screen, "legacybridge.screen.apply");
                    require(game.screen instanceof ConfirmScreen, "Second Apply skipped confirmation");
                    press(game.screen, "legacybridge.screen.confirm_apply");
                    move(Phase.PENDING);
                }
                case PENDING -> {
                    if (!hasJob(game.screen, "pending_restart")) return;
                    require(loadedBefore.equals(loadedMods()) && !ModList.get().isLoaded(LegacyBridgeProductProbe.FIXTURE_ID),
                            "Apply activated a mod in the already-started session");
                    fileCheck = P4ProbeFiles.verifyApplied(fixtures);
                    move(Phase.VERIFY_APPLIED);
                }
                case VERIFY_APPLIED -> {
                    if (!fileCheck.isDone()) return;
                    appliedEvidence = fileCheck.getNow(null);
                    screenshot = capture(game, "pending-restart");
                    move(Phase.CAPTURE);
                }
                case CAPTURE -> {
                    if (!screenshot.isDone()) return;
                    verifyScreenshot();
                    press(game.screen, "legacybridge.screen.tab_input");
                    typeSearch(game.screen, fixtures.original().getFileName().toString());
                    move(Phase.CANCEL_QUEUE);
                }
                case CANCEL_QUEUE -> {
                    if (!after(2)) return;
                    focusRow(game.screen, list(game.screen).children().get(0));
                    move(Phase.START_CANCEL_JOB);
                }
                case START_CANCEL_JOB -> {
                    // Native widget enablement is updated by the screen's next tick,
                    // not synchronously by this probe's direct focus event.
                    if (!after(2)) return;
                    press(game.screen, "legacybridge.screen.convert");
                    closing = (ConversionScreen) game.screen;
                    move(Phase.CANCEL_CLOSE);
                }
                case CANCEL_CLOSE -> {
                    if (!after(2)) return;
                    Button cancel = button(game.screen, "legacybridge.screen.cancel");
                    cancelClicked = cancel.active;
                    if (cancelClicked) cancel.onPress();
                    game.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    require(game.screen instanceof TitleScreen, "Escape failed to return to native title screen");
                    move(Phase.WAIT_STOP);
                }
                case WAIT_STOP -> {
                    if (!closing.isStopped()) return;
                    // The title hook updates its lease label/active flag after a rendered
                    // frame. A stopped worker alone does not imply that widget update ran.
                    if (game.screen.children().stream().noneMatch(child -> child instanceof Button button && button.active
                            && button.getMessage().getContents() instanceof TranslatableContents translated
                            && translated.getKey().equals("legacybridge.menu.convert"))) return;
                    require(loadedBefore.equals(loadedMods()), "Closing queue changed loaded mods");
                    press(game.screen, "legacybridge.menu.convert");
                    require(game.screen instanceof ConversionScreen, "UI cannot be reopened after closing/cancelling work");
                    move(Phase.REOPEN);
                }
                case REOPEN -> {
                    if (list(game.screen).children().size() != 8) return;
                    require(!ModList.get().isLoaded(LegacyBridgeProductProbe.FIXTURE_ID), "Reopening hot-loaded installed fixture");
                    fileCheck = P4ProbeFiles.verifyAfterClose(fixtures);
                    move(Phase.VERIFY_CLEANUP);
                }
                case VERIFY_CLEANUP -> {
                    if (!fileCheck.isDone()) return;
                    LogUtils.getLogger().info("LEGACYBRIDGE_P4_CLIENT_OK nativeSearch=passed focusTab=passed scales=1,2,3 queue=converted+blocked reportDetails=accessible declinedApply=noWrites pendingRestart=true cancelButtonClicked={} closeCleanup=stopped reopened=true cancellationRaceWinner=not_asserted {}",
                            cancelClicked, appliedEvidence + " " + fileCheck.getNow(null));
                    finish(game);
                }
                case DONE -> { }
            }
        } catch (Throwable failure) {
            LogUtils.getLogger().error("LEGACYBRIDGE_P4_CLIENT_FAILED phase=" + phase, failure);
            finish(game);
        }
    }

    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event) {
        if (enabled() && phase == Phase.JOBS && event.phase == TickEvent.Phase.END) workFrames++;
    }

    private static void typeSearch(Screen screen, String query) {
        EditBox search = field(screen, "search");
        search.setValue("");
        require(screen.mouseClicked(search.getX() + 4, search.getY() + 6, 0), "Search mouse focus was not handled");
        screen.mouseReleased(search.getX() + 4, search.getY() + 6, 0);
        require(screen.getFocused() == search, "Search did not acquire native focus");
        for (char character : query.toCharArray()) require(screen.charTyped(character, 0), "Native text event was rejected");
        require(search.getValue().equals(query), "Native search text differs");
        screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        require(screen.getFocused() != null && screen.getFocused() != search, "Tab failed to move focus");
    }

    private static void focusRow(Screen screen, ConversionEntryList.Row row) {
        ConversionEntryList list = list(screen);
        screen.setFocused(list);
        list.setFocused(row);
    }

    private static void toggleFirst(Screen screen) {
        focusRow(screen, list(screen).children().get(0));
        require(screen.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0), "Native row selection key was unhandled");
        screen.keyReleased(GLFW.GLFW_KEY_SPACE, 0, 0);
    }

    private static boolean hasJob(Screen screen, String status) { return list(screen).children().stream().anyMatch(row -> jobState(row, status)); }
    private static boolean jobState(ConversionEntryList.Row row, String status) {
        return narration(row).contains(Component.translatable("legacybridge.job." + status).getString());
    }
    private static String narration(ConversionEntryList.Row row) { return row.getNarration().getString(); }
    private static ConversionEntryList list(Screen screen) {
        return screen.children().stream().filter(ConversionEntryList.class::isInstance).map(ConversionEntryList.class::cast).findFirst().orElseThrow();
    }
    private static EditBox field(Screen screen, String key) {
        String label = Component.translatable("legacybridge.screen." + key).getString();
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(value -> value.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static Button button(Screen screen, String key) {
        String label = Component.translatable(key).getString();
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(value -> value.getMessage().getString().equals(label)).findFirst().orElseThrow(() -> new IllegalStateException("Missing native button " + key));
    }
    private static void press(Screen screen, String key) { Button button = button(screen, key); require(button.active, "Inactive native button " + key); button.onPress(); }
    private static long menuButtons(Screen screen) { return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(button -> button.getMessage().getString().equals(Component.translatable("legacybridge.menu.convert").getString())).count(); }
    private static void assertBounds(Screen screen) {
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible)
            require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getX() + widget.getWidth() <= screen.width
                    && widget.getY() + widget.getHeight() <= screen.height, "Widget left scaled GUI bounds");
        require(list(screen).getTop() >= 0 && list(screen).getBottom() <= screen.height
                && list(screen).getTop() < list(screen).getBottom(), "List bounds invalid after scale resize");
    }
    private static List<String> loadedMods() { return ModList.get().getMods().stream().map(mod -> mod.getModId() + ":" + mod.getVersion()).sorted().toList(); }
    private static boolean after(int count) { return ticks - phaseTick >= count; }
    private static void move(Phase next) { phase = next; phaseTick = ticks; }

    private static CompletableFuture<Component> capture(Minecraft game, String label) {
        require(RenderSystem.isOnRenderThread(), "Screenshot requested outside render thread");
        var completion = new CompletableFuture<Component>();
        String filename = "legacybridge-p4-" + label + "-" + UUID.randomUUID() + ".png";
        Screenshot.grab(game.gameDirectory, filename, game.getMainRenderTarget(), message -> {
            LogUtils.getLogger().info("LEGACYBRIDGE_P4_SCREENSHOT file={} result={}", filename, message.getString());
            completion.complete(message);
        });
        return completion;
    }
    private static void verifyScreenshot() {
        Component message = screenshot.getNow(null);
        require(message != null && message.getContents() instanceof TranslatableContents value
                && value.getKey().equals("screenshot.success"), "P4 screenshot failed: " + message);
    }
    private static void finish(Minecraft game) {
        phase = Phase.DONE;
        P4ProbeFiles.close();
        if (game.screen instanceof ConversionScreen screen) screen.onClose();
        game.options.guiScale().set(originalScale);
        game.resizeDisplay();
        game.stop();
    }
}
