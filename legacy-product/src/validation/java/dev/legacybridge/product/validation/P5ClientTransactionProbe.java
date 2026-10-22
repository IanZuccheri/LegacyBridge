package dev.legacybridge.product.validation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import dev.legacybridge.product.client.ConversionScreen;
import dev.legacybridge.product.client.TransactionScreen;
import dev.legacybridge.product.controller.ConversionController;
import dev.legacybridge.product.controller.JobState;
import dev.legacybridge.product.install.InstallSession;
import dev.legacybridge.product.install.TransactionResult;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import static dev.legacybridge.product.validation.ProductProbeSupport.require;

/**
 * Separate normal-client QA. Native widgets cover apply/restore/resume/manual close;
 * the version-only update and one explicit boundary exception use the packaged API.
 */
@Mod.EventBusSubscriber(modid = LegacyBridgeProductProbe.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class P5ClientTransactionProbe {
    private enum Phase {
        PREPARE, TITLE, INVENTORY, CONVERTING, OPEN_INSTALL, MARK_FIRST, APPLY_FIRST, WAIT_FIRST, CHECK_FIRST,
        RESTORE_FIRST, SELECT_FIRST_RESTORE, WAIT_FIRST_RESTORE, CHECK_FIRST_RESTORE, MARK_SECOND, APPLY_SECOND, WAIT_SECOND, CHECK_SECOND,
        UPDATE_API, REFRESH_PARTIAL, SELECT_PARTIAL, RESUME, WAIT_RESUME, CHECK_RESUME, CAPTURE_UPDATE,
        RESTORE_UPDATE, WAIT_UPDATE_RESTORE, CHECK_UPDATE_RESTORE, REFRESH_BASELINE, SELECT_BASELINE,
        REMOVE_BASELINE, WAIT_BASELINE_REMOVED, CHECK_BASELINE_REMOVED, MARK_FINAL, SELECT_FINAL, CAPTURE_FINAL, FINAL_CLOSING, DONE
    }
    private static Phase phase = Phase.PREPARE;
    private static long deadline;
    private static int ticks, phaseTick, titleTicks, frames, workTicks;
    private static ConversionController controller;
    private static CompletableFuture<P5ProbeFiles.Prepared> preparation;
    private static P5ProbeFiles.Prepared prepared;
    private static CompletableFuture<P5ProbeFiles.Update> updateWork;
    private static P5ProbeFiles.Update update;
    private static CompletableFuture<String> fileCheck;
    private static CompletableFuture<Component> screenshot;
    private static TransactionResult first, baseline, observed;
    private static UUID expectedOperation;
    private static List<String> loadedBefore;
    private static volatile boolean finalCloseArmed;
    private static final CompletableFuture<Void> SHUTDOWN_EVIDENCE = new CompletableFuture<>();

    private P5ClientTransactionProbe() { }
    private static boolean enabled() { return Boolean.getBoolean("legacybridge.product.p5"); }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled() || phase == Phase.DONE) return;
        Minecraft game = Minecraft.getInstance();
        try {
            if (deadline == 0) {
                int seconds = Integer.getInteger("legacybridge.product.p5.timeoutSeconds", 300);
                require(seconds >= 90 && seconds <= 600, "P5 QA timeout must be 90..600 seconds");
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
                loadedBefore = loadedMods();
                require(!ModList.get().isLoaded(LegacyBridgeProductProbe.FIXTURE_ID), "P5 requires a fresh instance without converted fixture loaded");
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    // Separate QA observer: no Minecraft/render calls and no publication guarantee.
                    // The product's own barrier independently protects accepted installation work.
                    if (finalCloseArmed) try { SHUTDOWN_EVIDENCE.get(15, TimeUnit.SECONDS); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); shutdownEvidenceFailed(failure); }
                    catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) { shutdownEvidenceFailed(failure); }
                    P5ProbeFiles.close();
                }, "legacybridge-p5-qa-final-evidence"));
                preparation = P5ProbeFiles.prepare();
            }
            require(System.nanoTime() < deadline, "P5 UI timeout in " + phase);
            ticks++;
            if (controller != null && (controller.snapshot().busy() || updateWork != null && !updateWork.isDone())) workTicks++;
            if (game.getOverlay() != null || game.screen == null) return;
            switch (phase) {
                case PREPARE -> {
                    if (!preparation.isDone()) return;
                    prepared = preparation.getNow(null); require(prepared != null, "Missing P5 preparation evidence"); move(Phase.TITLE);
                }
                case TITLE -> {
                    if (game.screen instanceof AccessibilityOnboardingScreen) { press(game.screen, "gui.continue"); return; }
                    if (!(game.screen instanceof TitleScreen) || ++titleTicks < 30) return;
                    press(game.screen, "legacybridge.menu.convert");
                    require(game.screen instanceof ConversionScreen, "Native title action did not open conversion");
                    controller = observedController((ConversionScreen) game.screen);
                    field(game.screen, "legacybridge.screen.mapping").setValue(ProductProbeSupport.bundle().toString());
                    move(Phase.INVENTORY);
                }
                case INVENTORY -> {
                    if (controller.snapshot().busy() || rows(game.screen).children().size() != 1) return;
                    focusRow(game.screen, 0);
                    press(game.screen, "legacybridge.screen.convert"); move(Phase.CONVERTING);
                }
                case CONVERTING -> {
                    if (controller.snapshot().busy()) return;
                    require(controller.jobs().size() == 1 && controller.jobs().get(0).status() == JobState.CONVERTED, "Baseline UI conversion did not succeed");
                    unchangedLoaded(); move(Phase.OPEN_INSTALL);
                }
                case OPEN_INSTALL -> {
                    if (!after(2)) return;
                    press(game.screen, "legacybridge.screen.apply");
                    require(game.screen instanceof TransactionScreen, "Apply did not open the native transaction screen");
                    move(Phase.MARK_FIRST);
                }
                case MARK_FIRST -> {
                    if (!idle() || !after(2)) return;
                    markOnlyJob(game.screen); move(Phase.APPLY_FIRST);
                }
                case APPLY_FIRST -> { if (after(2)) { beginAction(game.screen, "apply"); move(Phase.WAIT_FIRST); } }
                case WAIT_FIRST -> {
                    if (!operationDone()) return;
                    first = result(TransactionResult.Status.COMMITTED);
                    fileCheck = P5ProbeFiles.verifyInstalled(prepared, first, ProductProbeSupport.OUTPUT_SHA); move(Phase.CHECK_FIRST);
                }
                case CHECK_FIRST -> {
                    if (!checkFiles("FIRST_APPLY")) return;
                    move(Phase.RESTORE_FIRST);
                }
                case RESTORE_FIRST -> { if (after(2)) { selectTransaction(game.screen, first.transaction()); move(Phase.SELECT_FIRST_RESTORE); } }
                case SELECT_FIRST_RESTORE -> { if (after(2)) { beginAction(game.screen, "restore"); move(Phase.WAIT_FIRST_RESTORE); } }
                case WAIT_FIRST_RESTORE -> {
                    if (!operationDone()) return;
                    fileCheck = P5ProbeFiles.verifyRemoved(prepared, result(TransactionResult.Status.RESTORED)); move(Phase.CHECK_FIRST_RESTORE);
                }
                case CHECK_FIRST_RESTORE -> {
                    if (!checkFiles("FIRST_RESTORE")) return;
                    press(game.screen, "legacybridge.install.jobs"); move(Phase.MARK_SECOND);
                }
                case MARK_SECOND -> { if (after(2) && idle()) { markOnlyJob(game.screen); move(Phase.APPLY_SECOND); } }
                case APPLY_SECOND -> { if (after(2)) { beginAction(game.screen, "apply"); move(Phase.WAIT_SECOND); } }
                case WAIT_SECOND -> {
                    if (!operationDone()) return;
                    baseline = result(TransactionResult.Status.COMMITTED);
                    require(!baseline.transaction().equals(first.transaction()), "Reapply reused a restored transaction instead of explicit new authority");
                    fileCheck = P5ProbeFiles.verifyInstalled(prepared, baseline, ProductProbeSupport.OUTPUT_SHA); move(Phase.CHECK_SECOND);
                }
                case CHECK_SECOND -> {
                    if (!checkFiles("SECOND_APPLY")) return;
                    updateWork = P5ProbeFiles.prepareInterruptedUpdate(prepared, baseline, session(game)); move(Phase.UPDATE_API);
                }
                case UPDATE_API -> {
                    if (!updateWork.isDone()) return;
                    update = updateWork.getNow(null); require(update != null, "Missing interrupted update evidence");
                    unchangedLoaded();
                    LogUtils.getLogger().info("LEGACYBRIDGE_P5_API_PARTIAL_UPDATE_OK transaction={} version=1.0.1 boundary=BEFORE_PUBLISH oldRetired=true independentBackup=true fault=injected_IOException_not_powercut", update.interrupted().transaction().getFileName());
                    press(game.screen, "legacybridge.install.refresh"); move(Phase.REFRESH_PARTIAL);
                }
                case REFRESH_PARTIAL -> {
                    if (!idle() || !after(2)) return;
                    selectTransaction(game.screen, update.interrupted().transaction()); move(Phase.SELECT_PARTIAL);
                }
                case SELECT_PARTIAL -> {
                    if (!after(2)) return;
                    require(button(game.screen, "legacybridge.install.resume").active, "Inspected partial update does not offer explicit Resume"); move(Phase.RESUME);
                }
                case RESUME -> { beginAction(game.screen, "resume"); move(Phase.WAIT_RESUME); }
                case WAIT_RESUME -> {
                    if (!operationDone()) return;
                    observed = result(TransactionResult.Status.COMMITTED);
                    require(observed.transaction().equals(update.interrupted().transaction()) && observed.entries().get(0).version().equals("1.0.1"), "Native Resume did not complete the exact interrupted update");
                    fileCheck = P5ProbeFiles.verifyInstalled(prepared, observed, update.stagedSha()); move(Phase.CHECK_RESUME);
                }
                case CHECK_RESUME -> {
                    if (!checkFiles("NATIVE_RESUME")) return;
                    screenshot = capture(game, "resumed-update"); move(Phase.CAPTURE_UPDATE);
                }
                case CAPTURE_UPDATE -> { if (screenshotDone()) move(Phase.RESTORE_UPDATE); }
                case RESTORE_UPDATE -> { if (after(2)) { beginAction(game.screen, "restore"); move(Phase.WAIT_UPDATE_RESTORE); } }
                case WAIT_UPDATE_RESTORE -> {
                    if (!operationDone()) return;
                    fileCheck = P5ProbeFiles.verifyUpdateRestored(update, result(TransactionResult.Status.RESTORED)); move(Phase.CHECK_UPDATE_RESTORE);
                }
                case CHECK_UPDATE_RESTORE -> {
                    if (!checkFiles("UPDATE_RESTORED")) return;
                    press(game.screen, "legacybridge.install.refresh"); move(Phase.REFRESH_BASELINE);
                }
                case REFRESH_BASELINE -> {
                    if (!idle() || !after(2)) return;
                    selectTransaction(game.screen, baseline.transaction()); move(Phase.SELECT_BASELINE);
                }
                case SELECT_BASELINE -> { if (after(2)) move(Phase.REMOVE_BASELINE); }
                case REMOVE_BASELINE -> { beginAction(game.screen, "restore"); move(Phase.WAIT_BASELINE_REMOVED); }
                case WAIT_BASELINE_REMOVED -> {
                    if (!operationDone()) return;
                    fileCheck = P5ProbeFiles.verifyRemoved(prepared, result(TransactionResult.Status.RESTORED)); move(Phase.CHECK_BASELINE_REMOVED);
                }
                case CHECK_BASELINE_REMOVED -> {
                    if (!checkFiles("BASELINE_REMOVED")) return;
                    press(game.screen, "legacybridge.install.jobs"); move(Phase.MARK_FINAL);
                }
                case MARK_FINAL -> {
                    if (!after(2) || !idle()) return;
                    markOnlyJob(game.screen);
                    move(Phase.SELECT_FINAL);
                }
                case SELECT_FINAL -> {
                    if (!after(2) || !idle()) return;
                    require(button(game.screen, "legacybridge.install.apply_close").active, "Marked result did not enable native Apply-and-close");
                    String checked = ". " + Component.translatable("legacybridge.install.checked").getString();
                    require(rows(game.screen).children().size() == 1
                            && rows(game.screen).children().get(0).getNarration().getString().endsWith(checked), "Final screenshot would not show the checked installation result");
                    require(frames > 0 && workTicks > 0, "No rendered frames/ticks observed across the real workflow");
                    screenshot = capture(game, "manual-apply-close"); move(Phase.CAPTURE_FINAL);
                }
                case CAPTURE_FINAL -> {
                    if (!screenshotDone() || !after(2)) return;
                    finalCloseArmed = true;
                    move(Phase.FINAL_CLOSING);
                    beginAction(game.screen, "apply_close");
                }
                case FINAL_CLOSING -> { /* Native TransactionScreen owns safe completion and Minecraft.stop(). */ }
                case DONE -> { }
            }
        } catch (Throwable failure) { fail(game, failure); }
    }

    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event) {
        if (enabled() && event.phase == TickEvent.Phase.END && phase != Phase.DONE) frames++;
    }

    @SubscribeEvent public static void shuttingDown(GameShuttingDownEvent event) {
        if (!enabled() || phase == Phase.DONE) return;
        try {
            require(phase == Phase.FINAL_CLOSING && finalCloseArmed, "Game closed outside the native Apply-and-close test boundary");
            require(operationDone(), "Native Apply-and-close stopped the game before its operation completed");
            TransactionResult finalResult = result(TransactionResult.Status.COMMITTED);
            require(!finalResult.transaction().equals(baseline.transaction()) && finalResult.safeToClose(), "Final operation does not prove its own new completed publication");
            phase = Phase.DONE;
            P5ProbeFiles.verifyInstalled(prepared, finalResult, ProductProbeSupport.OUTPUT_SHA).whenComplete((evidence, failure) -> {
                try {
                    if (failure != null) throw new java.util.concurrent.CompletionException(failure);
                    synchronized (SHUTDOWN_EVIDENCE) {
                        // A bounded shutdown observer may already have failed; never emit late success.
                        if (SHUTDOWN_EVIDENCE.isDone()) return;
                        LogUtils.getLogger().info("LEGACYBRIDGE_P5_CLIENT_OK native=convert,apply,restore,reapply,refresh,resume,restore,apply_close updateApi=1.0.1 injectedFault=BEFORE_PUBLISH originalPathAndBytes=unchanged currentLoadedMods=unchanged finalBaselinePending=true automaticRestart=false workTicks={} renderedFrames={} {}", workTicks, frames, evidence);
                        SHUTDOWN_EVIDENCE.complete(null);
                    }
                } catch (Throwable invalid) {
                    shutdownEvidenceFailed(invalid);
                } finally { P5ProbeFiles.close(); }
            });
        } catch (Throwable failure) {
            phase = Phase.DONE; SHUTDOWN_EVIDENCE.completeExceptionally(failure);
            LogUtils.getLogger().error("LEGACYBRIDGE_P5_CLIENT_FAILED shutdown", failure); P5ProbeFiles.close();
        }
    }

    private static void shutdownEvidenceFailed(Throwable failure) {
        synchronized (SHUTDOWN_EVIDENCE) {
            // Completion can race the observer's timeout; an already successful result stays successful.
            if (SHUTDOWN_EVIDENCE.isDone() && !SHUTDOWN_EVIDENCE.isCompletedExceptionally()) return;
            SHUTDOWN_EVIDENCE.completeExceptionally(failure);
            LogUtils.getLogger().error("LEGACYBRIDGE_P5_CLIENT_FAILED shutdown evidence", failure);
        }
    }

    private static ConversionController observedController(ConversionScreen screen) throws Exception {
        // Read-only access to the real owner for typed evidence; no fabricated state/test API.
        var field = ConversionScreen.class.getDeclaredField("controller");
        require(field.trySetAccessible(), "Own product module refuses QA snapshot access; do not modify exports/opens");
        return (ConversionController) field.get(screen);
    }

    private static void beginAction(Screen screen, String action) {
        UUID before = controller.installation().operationId().orElse(null);
        press(screen, "legacybridge.install." + action);
        Minecraft game = Minecraft.getInstance(); require(game.screen instanceof ConfirmScreen, "Native " + action + " skipped confirmation");
        press(game.screen, "legacybridge.install.confirm");
        expectedOperation = controller.installation().operationId().orElseThrow();
        require(!expectedOperation.equals(before), "Native " + action + " was not accepted as a new operation");
    }
    private static boolean operationDone() { return idle() && controller.installation().operationId().filter(expectedOperation::equals).isPresent(); }
    private static boolean idle() { return controller != null && !controller.snapshot().busy() && !controller.installation().busy(); }
    private static TransactionResult result(TransactionResult.Status status) {
        unchangedLoaded();
        var view = controller.installation();
        require(view.error().isEmpty(), "Installation error: " + view.error());
        var value = view.result().orElseThrow();
        require(value.status() == status && value.safeToClose(), "Unexpected transaction result: " + value.status());
        return value;
    }
    private static boolean checkFiles(String stage) {
        if (!fileCheck.isDone()) return false;
        LogUtils.getLogger().info("LEGACYBRIDGE_P5_{}_OK {}", stage, fileCheck.getNow(null)); return true;
    }
    private static void markOnlyJob(Screen screen) {
        require(rows(screen).children().size() == 1, "Expected exactly one native staged-job row");
        focusRow(screen, 0);
        require(screen.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0), "Native job checkbox did not accept Space");
        screen.keyReleased(GLFW.GLFW_KEY_SPACE, 0, 0);
    }
    private static void selectTransaction(Screen screen, Path transaction) {
        var rows = rows(screen); int index = -1;
        for (int current = 0; current < rows.children().size(); current++) if (rows.children().get(current).getNarration().getString().contains(transaction.getFileName().toString())) { index = current; break; }
        require(index >= 0, "Native transaction list did not expose the inspected UUID " + transaction.getFileName());
        focusRow(screen, index);
    }
    private static void focusRow(Screen screen, int index) {
        var list = rows(screen); screen.setFocused(list); list.setFocused(list.children().get(index));
    }
    private static ObjectSelectionList<?> rows(Screen screen) {
        return screen.children().stream().filter(ObjectSelectionList.class::isInstance).map(value -> (ObjectSelectionList<?>) value).findFirst().orElseThrow();
    }
    private static EditBox field(Screen screen, String key) {
        String label = Component.translatable(key).getString();
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).filter(value -> value.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static Button button(Screen screen, String key) {
        String label = Component.translatable(key).getString();
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(value -> value.getMessage().getString().equals(label))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing native control " + key));
    }
    private static void press(Screen screen, String key) { Button value = button(screen, key); require(value.active, "Inactive native control " + key); value.onPress(); }
    private static InstallSession session(Minecraft game) {
        var list = ModList.get(); Set<String> ids = new LinkedHashSet<>(); Set<Path> paths = new LinkedHashSet<>();
        list.getMods().forEach(mod -> ids.add(mod.getModId())); list.getModFiles().forEach(info -> paths.add(info.getFile().getFilePath().toAbsolutePath().normalize()));
        return new InstallSession(ids, paths, game.level != null || game.getSingleplayerServer() != null || game.getConnection() != null);
    }
    private static List<String> loadedMods() { return ModList.get().getMods().stream().map(mod -> mod.getModId() + ":" + mod.getVersion()).sorted().toList(); }
    private static void unchangedLoaded() { require(loadedBefore.equals(loadedMods()) && !ModList.get().isLoaded(LegacyBridgeProductProbe.FIXTURE_ID), "Filesystem operation activated a fixture in the running session"); }
    private static boolean after(int count) { return ticks - phaseTick >= count; }
    private static void move(Phase next) { phase = next; phaseTick = ticks; LogUtils.getLogger().info("LEGACYBRIDGE_P5_PHASE {}", next); }
    private static CompletableFuture<Component> capture(Minecraft game, String stage) {
        require(RenderSystem.isOnRenderThread(), "Screenshot requested outside render thread");
        var completion = new CompletableFuture<Component>(); String name = "legacybridge-p5-" + stage + "-" + UUID.randomUUID() + ".png";
        Screenshot.grab(game.gameDirectory, name, game.getMainRenderTarget(), message -> {
            LogUtils.getLogger().info("LEGACYBRIDGE_P5_SCREENSHOT file={} result={}", name, message.getString()); completion.complete(message);
        }); return completion;
    }
    private static boolean screenshotDone() {
        if (!screenshot.isDone()) return false;
        Component message = screenshot.getNow(null);
        require(message != null && message.getContents() instanceof TranslatableContents value && value.getKey().equals("screenshot.success"), "P5 screenshot failed"); return true;
    }
    private static void fail(Minecraft game, Throwable failure) {
        LogUtils.getLogger().error("LEGACYBRIDGE_P5_CLIENT_FAILED phase=" + phase, failure);
        phase = Phase.DONE; SHUTDOWN_EVIDENCE.completeExceptionally(failure);
        if (controller != null) controller.close();
        P5ProbeFiles.close(); game.stop();
    }
}
