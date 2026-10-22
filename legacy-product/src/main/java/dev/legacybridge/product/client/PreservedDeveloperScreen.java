package dev.legacybridge.product.client;

import dev.legacybridge.product.controller.*;
import dev.legacybridge.product.history.PreservedInstallReceipts;
import dev.legacybridge.product.history.PreservedUpdateReceipts;
import dev.legacybridge.product.install.InstallSession;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

/** Developer opt-in host, not public profile promotion. No file reads, installs or game calls on a worker. */
@OnlyIn(Dist.CLIENT)
public final class PreservedDeveloperScreen extends Screen {
    private enum Page { SOURCE, PREDECESSOR, RECOVERY, RESULT, HISTORY }
    private final Screen parent;
    private final PreservedDeveloperForm form = new PreservedDeveloperForm();
    private final PreservedHostSession session = new PreservedHostSession();
    private final ConversionController controller;
    private final Set<String> loadedIds = new LinkedHashSet<>();
    private final Set<Path> loadedFiles = new LinkedHashSet<>();
    private final Map<PreservedDeveloperForm.Field, EditBox> fields = new EnumMap<>(PreservedDeveloperForm.Field.class);
    private final Map<PreservedDeveloperForm.Field, Integer> cursors = new EnumMap<>(PreservedDeveloperForm.Field.class);
    private final List<Button> tabs = new ArrayList<>();
    private Page page = Page.SOURCE;
    private Button review, confirm, applyClose, back, mode, refresh, previous, next, kind, prepare;
    private EditBox search;
    private Lines lines;
    private Consent confirmation;
    private PreservedPreparationScreen preparationScreen;
    private UUID closeOperation;
    private int historyIndex, focusIndex;
    private boolean childNavigation, closed, stopRequested;
    private String notice = "", query = "", lineSignature = "";

    PreservedDeveloperScreen(Screen parent, Path gameDir) {
        super(text("title")); this.parent = parent;
        // Forge's completed loading list, not an inventory inferred from files in mods/input.
        ModList.get().getMods().forEach(mod -> loadedIds.add(mod.getModId()));
        ModList.get().getModFiles().forEach(info -> loadedFiles.add(info.getFile().getFilePath().toAbsolutePath().normalize()));
        controller = ConversionController.forPreservedDevelopment(gameDir, session);
    }
    static Component text(String key, Object... args) { return Component.translatable("legacybridge.preserved." + key, args); }
    boolean owns(Screen screen) { return screen != null && (screen == this || screen == confirmation || screen == preparationScreen); }
    ConversionController controller() { return controller; }
    void returnFromPreparation() { childNavigation = false; minecraft.setScreen(this); }
    void acceptPrepared(PreservedInstallSelection selected) {
        form.invalidate(); form.mode(PreservedDeveloperForm.Mode.APPLY);
        form.set(PreservedDeveloperForm.Field.ADMISSION, selected.admission().toString());
        form.set(PreservedDeveloperForm.Field.ADMISSION_HASH, selected.admissionSha256());
        form.set(PreservedDeveloperForm.Field.DATA, selected.data().path().orElseThrow().toString());
        form.set(PreservedDeveloperForm.Field.DATA_HASH, selected.data().expectedManifestSha256().orElseThrow());
        page = Page.SOURCE; notice = text("fresh_review").getString();
        returnFromPreparation(); // No review/confirmation/install is performed by this transfer.
    }
    boolean isStopped() { return controller.isStopped(); }
    boolean isInstalling() { return controller.isInstalling(); }
    /** Invoked on the client thread even while a confirmation screen owns focus. No filesystem I/O. */
    void publishSession() {
        Minecraft game = Minecraft.getInstance();
        if (closed || !owns(game.screen)) { session.invalidate(); return; }
        session.publish(new InstallSession(loadedIds, loadedFiles,
                game.level != null || game.getSingleplayerServer() != null || game.getConnection() != null));
    }
    private boolean worldClosed() {
        return minecraft.level == null && minecraft.getSingleplayerServer() == null && minecraft.getConnection() == null;
    }
    private boolean busy() { return controller.snapshot().busy() || controller.isInstalling(); }
    private boolean updating() { return form.kind() == PreservedDeveloperForm.Kind.UPDATE; }
    private boolean canConfirm() {
        return updating() ? form.canConfirm(controller.preservedUpdate(), busy())
                : form.canConfirm(controller.preservedInstallation(), busy());
    }
    private Component action() { return text(updating() && form.mode() == PreservedDeveloperForm.Mode.APPLY ? "update.apply" : form.mode().name()); }
    private void saveCursors() { fields.forEach((field, box) -> cursors.put(field, box.getCursorPosition())); }
    @Override protected void init() {
        saveCursors(); double scroll = lines == null ? 0 : lines.getScrollAmount();
        fields.clear(); tabs.clear(); mode = null; kind = null; prepare = null; search = null; refresh = null; previous = null; next = null; lines = null;
        int tabWidth = (width - 16 - 4 * (Page.values().length - 1)) / Page.values().length;
        int half = (width - 24) / 2, third = (width - 24) / 3;
        for (Page tab : Page.values()) tabs.add(addRenderableWidget(Button.builder(text(tab.name()), b -> changePage(tab))
                .bounds(8 + tab.ordinal() * (tabWidth + 4), 33, tabWidth, 20).build()));
        if (page == Page.SOURCE) {
            field(PreservedDeveloperForm.Field.ADMISSION, 8, 74, half);
            field(PreservedDeveloperForm.Field.ADMISSION_HASH, 16 + half, 74, half);
            field(PreservedDeveloperForm.Field.DATA, 8, 115, half);
            field(PreservedDeveloperForm.Field.DATA_HASH, 16 + half, 115, half);
            prepare = addRenderableWidget(Button.builder(PreservedPreparationScreen.text("open"), b -> {
                if (busy()) return;
                form.invalidate(); closeOperation = null; childNavigation = true;
                if (preparationScreen == null) preparationScreen = new PreservedPreparationScreen(this);
                minecraft.setScreen(preparationScreen);
            }).bounds(8, 145, half, 20).build());
            kind = addRenderableWidget(Button.builder(text("kind." + form.kind().name()), b -> {
                if (busy()) return;
                form.kind(updating() ? PreservedDeveloperForm.Kind.ADD : PreservedDeveloperForm.Kind.UPDATE);
                closeOperation = null; historyIndex = 0; notice = text("fresh_review").getString(); update();
            }).bounds(16 + half, 145, half, 20).build());
            kind.setTooltip(Tooltip.create(text("update.scope")));
        } else if (page == Page.PREDECESSOR) {
            field(PreservedDeveloperForm.Field.PREVIOUS_INSTALLATION, 8, 74, width - 16);
            field(PreservedDeveloperForm.Field.PREVIOUS_PLAN_HASH, 8, 115, width - 16);
        } else if (page == Page.RECOVERY) {
            mode = addRenderableWidget(Button.builder(text(form.mode().name()), b -> {
                form.mode(PreservedDeveloperForm.Mode.values()[(form.mode().ordinal() + 1) % 3]); update();
            }).bounds(8, 59, width - 16, 20).build());
            field(PreservedDeveloperForm.Field.TRANSACTION, 8, 97, half);
            field(PreservedDeveloperForm.Field.PLAN_HASH, 16 + half, 97, half);
            field(PreservedDeveloperForm.Field.APPROVAL, 8, 137, width - 16);
        } else {
            if (page == Page.HISTORY) {
                search = addRenderableWidget(new EditBox(font, 8, 59, Math.max(30, width - 174), 20, text("search")));
                search.setMaxLength(128); search.setValue(query); search.setHint(text("search"));
                search.setResponder(value -> { query = value; historyIndex = 0; lineSignature = ""; });
                previous = addRenderableWidget(Button.builder(text("previous"), b -> { historyIndex = Math.max(0, historyIndex - 1); lineSignature = ""; }).bounds(width - 160, 59, 24, 20).build());
                next = addRenderableWidget(Button.builder(text("next"), b -> { historyIndex++; lineSignature = ""; }).bounds(width - 132, 59, 24, 20).build());
                previous.setTooltip(Tooltip.create(text("previous"))); next.setTooltip(Tooltip.create(text("next")));
                refresh = addRenderableWidget(Button.builder(text("refresh"), b -> {
                    if (busy()) { notice = text(controller.cancel() ? "cancel_requested" : "refused").getString(); return; }
                    form.invalidate();
                    boolean accepted = updating() ? controller.refreshPreservedUpdateHistory() : controller.refreshPreservedHistory();
                    notice = accepted ? "" : text("refused").getString();
                }).bounds(width - 104, 59, 96, 20).build());
            }
            lines = addRenderableWidget(new Lines(page == Page.HISTORY ? 86 : 60)); lines.setLeftPos(8);
        }
        review = addRenderableWidget(Button.builder(text("review"), b -> review()).bounds(8, height - 60, third, 20).build());
        confirm = addRenderableWidget(Button.builder(action(), b -> confirm(false)).bounds(12 + third, height - 60, third, 20).build());
        applyClose = addRenderableWidget(Button.builder(text("apply_close"), b -> confirm(true)).bounds(16 + third * 2, height - 60, third, 20).build());
        applyClose.setTooltip(Tooltip.create(text("manual_restart")));
        addRenderableWidget(Button.builder(text("copy"), b -> minecraft.keyboardHandler.setClipboard(String.join("\n", details())))
                .bounds(8, height - 36, half, 20).build());
        back = addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(16 + half, height - 36, half, 20).build());
        lineSignature = ""; update(); if (lines != null) lines.setScrollAmount(scroll);
        setInitialFocus(children().get(Math.max(0, Math.min(focusIndex, children().size() - 1))));
    }
    private void field(PreservedDeveloperForm.Field field, int x, int y, int w) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, w, 20, text(field.name())));
        box.setMaxLength(field.limit()); box.setValue(form.get(field)); box.setCursorPosition(cursors.getOrDefault(field, 0));
        box.setResponder(value -> { form.set(field, value); notice = ""; });
        box.setTooltip(Tooltip.create(text(field.name()))); fields.put(field, box);
    }
    private void changePage(Page nextPage) { saveCursors(); page = nextPage; focusIndex = 0; rebuildWidgets(); }
    private void review() {
        publishSession(); form.invalidate(); closeOperation = null;
        try {
            var accepted = updating() ? controller.reviewPreservedUpdate(form.updateSelection())
                    : controller.reviewPreservedInstall(form.selection());
            accepted.ifPresent(form::reviewStarted);
            notice = accepted.isPresent() ? "" : text("refused").getString();
            if (accepted.isPresent()) changePage(Page.RESULT);
        } catch (PreservedDeveloperForm.InvalidField invalid) {
            notice = text("invalid", text(invalid.field().name()).getString()).getString();
            changePage(switch (invalid.field()) {
                case PREVIOUS_INSTALLATION, PREVIOUS_PLAN_HASH -> Page.PREDECESSOR;
                case TRANSACTION, PLAN_HASH, APPROVAL -> Page.RECOVERY;
                default -> Page.SOURCE;
            });
            setFocused(fields.get(invalid.field()));
        }
    }
    private void confirm(boolean close) {
        if (!canConfirm() || !worldClosed()) return;
        UUID id = controller.preservedInstallation().reviewId().orElseThrow();
        boolean update = updating();
        Component warning = text("confirm_warning", form.mode().name(), id.toString());
        if (update) {
            var preview = controller.preservedUpdate().preview().flatMap(p -> p.identity());
            warning = form.mode() == PreservedDeveloperForm.Mode.APPLY && preview.isPresent()
                    ? text("update.confirm_warning", safe(preview.get().modId()), safe(preview.get().previousVersion()),
                            safe(preview.get().replacementVersion()), id.toString())
                    : text("update.recovery_warning", form.mode().name(), id.toString());
        }
        childNavigation = true;
        confirmation = new Consent(this, close ? text("apply_close") : text(update ? "update.confirm_title" : "confirm_title"), warning, answer -> {
            confirmation.returning = true; childNavigation = false; minecraft.setScreen(this); publishSession();
            if (!answer) return;
            var token = update ? form.consume(controller.preservedUpdate(), busy()) : form.consume(controller.preservedInstallation(), busy());
            if (token.isEmpty() || !token.get().equals(id) || !worldClosed()) { notice = text("refused").getString(); return; }
            var operation = update ? controller.confirmPreservedUpdate(token.get()) : controller.confirmPreservedInstall(token.get());
            closeOperation = close ? operation.orElse(null) : null;
            notice = operation.isPresent() ? "" : text("refused").getString(); changePage(Page.RESULT);
        });
        minecraft.setScreen(confirmation);
    }
    @Override public void tick() { publishSession(); fields.values().forEach(EditBox::tick); if (search != null) search.tick(); update(); }
    private void update() {
        var view = controller.preservedInstallation(); boolean busy = busy();
        review.active = !closed && !busy; confirm.active = !closed && canConfirm() && worldClosed();
        confirm.setMessage(action()); applyClose.active = confirm.active && form.mode() == PreservedDeveloperForm.Mode.APPLY;
        tabs.get(Page.PREDECESSOR.ordinal()).active = updating();
        if (kind != null) { kind.active = !busy; kind.setMessage(text("kind." + form.kind().name())); }
        if (prepare != null) prepare.active = !busy;
        back.active = !controller.isInstalling(); fields.values().forEach(box -> box.setEditable(!busy));
        if (mode != null) { mode.active = !busy; mode.setMessage(text(form.mode().name())); }
        if (refresh != null) { refresh.active = !busy || controller.canCancel();
            refresh.setMessage(busy ? Component.translatable("gui.cancel") : text("refresh")); }
        if (lines != null) {
            String content = String.join("\n", details());
            if (!content.equals(lineSignature)) { lineSignature = content; lines.update(content); }
        }
        if (closeOperation != null && !busy) {
            UUID expected = closeOperation; closeOperation = null;
            if (!stopRequested && view.operationId().filter(expected::equals).isPresent() && view.safeToClose() && worldClosed()) {
                stopRequested = true; closeSession(); minecraft.stop();
            } else notice = text("close_refused").getString();
        }
    }
    private List<PreservedInstallReceipts.Receipt> filteredHistory() {
        String needle = query.toLowerCase(Locale.ROOT);
        return controller.preservedHistory().receipts().stream().filter(r -> r.toString().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    private List<PreservedUpdateReceipts.Receipt> filteredUpdates() {
        String needle = query.toLowerCase(Locale.ROOT);
        return controller.preservedUpdateHistory().receipts().stream().filter(r -> r.toString().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    private List<String> details() {
        List<String> out = new ArrayList<>(); out.add(text("warning").getString()); out.add(text("not_active").getString());
        out.add(text("kind." + form.kind().name()).getString());
        if (updating()) out.add(text("update.scope").getString());
        if (page == Page.HISTORY) {
            var history = controller.preservedHistory(); var updates = filteredUpdates();
            var filtered = updating() ? updates.stream().map(PreservedUpdateReceipts.Receipt::observation).toList() : filteredHistory();
            historyIndex = Math.min(historyIndex, Math.max(0, filtered.size() - 1));
            if (previous != null) previous.active = historyIndex > 0;
            if (next != null) next.active = historyIndex + 1 < filtered.size();
            out.add(text("history_inert").getString());
            if (updating()) {
                var updateHistory = controller.preservedUpdateHistory();
                out.add(updateHistory.status().name()); updateHistory.diagnostic().ifPresent(out::add);
                if (!updates.isEmpty()) {
                    out.add("PREDECESSOR: " + updates.get(historyIndex).previousInstallationId());
                    out.add("PREDECESSOR PLAN SHA-256: " + updates.get(historyIndex).previousPlanSha256());
                }
            } else { out.add(history.status().name()); history.diagnostic().ifPresent(out::add); }
            out.add(text("page", filtered.isEmpty() ? 0 : historyIndex + 1, filtered.size()).getString());
            if (!filtered.isEmpty()) {
                var r = filtered.get(historyIndex);
                out.add("OPERATION: " + r.operationId()); out.add("REVIEW: " + r.reviewId()); out.add("ACTION: " + r.action());
                out.add("STATE: " + r.state()); out.add("ADMISSION: " + r.admissionId()); out.add("ADMISSION SHA-256: " + r.admissionSha256());
                out.add("DATA SHA-256: " + r.dataSha256()); r.approvalId().ifPresent(v -> out.add("PRIOR APPROVAL: " + v));
                r.transactionId().ifPresent(v -> out.add("TRANSACTION: " + v)); r.planSha256().ifPresent(v -> out.add("PLAN SHA-256: " + v));
                r.originalSha256().ifPresent(v -> out.add("ORIGINAL SHA-256: " + v)); r.candidateSha256().ifPresent(v -> out.add("CANDIDATE SHA-256: " + v));
                r.errorCode().ifPresent(v -> out.add("ERROR: " + v));
            }
        } else {
            var view = controller.preservedInstallation(); out.add(text("state", text("state." + view.state().name()).getString()).getString());
            if (view.operationId().isPresent()) out.add(text("last_observation_kind",
                    text(controller.preservedUpdate().selection().isPresent() ? "kind.UPDATE" : "kind.ADD").getString()).getString());
            if (view.state() == PreservedInstallView.State.REVIEWED && !canConfirm()) out.add(text("fresh_review").getString());
            if (updating()) {
                var update = controller.preservedUpdate();
                if (form.mode() != PreservedDeveloperForm.Mode.APPLY) out.add(text("update.recovery_observation").getString());
                update.preview().flatMap(p -> p.identity()).ifPresent(id -> {
                    out.add(text("update.comparison", id.modId(), id.previousVersion(), id.replacementVersion()).getString());
                    out.add(text("update.previous_hash", id.previousArtifactSha256()).getString());
                    out.add(text("update.replacement_hash", id.replacementArtifactSha256()).getString());
                });
            }
            out.add("ACTION: " + form.mode());
            for (var field : PreservedDeveloperForm.Field.values()) {
                boolean predecessor = field == PreservedDeveloperForm.Field.PREVIOUS_INSTALLATION || field == PreservedDeveloperForm.Field.PREVIOUS_PLAN_HASH;
                if (predecessor ? updating() : field.ordinal() < 4 || form.mode() != PreservedDeveloperForm.Mode.APPLY)
                    out.add(text(field.name()).getString() + ": " + form.get(field));
            }
            view.operationId().ifPresent(id -> out.add("OPERATION: " + id)); view.reviewId().ifPresent(id -> out.add("REVIEW: " + id));
            view.admission().flatMap(a -> a.inspection()).ifPresent(inspection -> {
                inspection.identity().ifPresent(id -> out.add("MOD: " + id.modId() + " " + id.modVersion()));
                out.add("ORIGINAL SHA-256: " + inspection.originalSha256()); out.add("CANDIDATE SHA-256: " + inspection.candidateSha256());
            });
            view.result().ifPresent(r -> { out.add("INSTALL: " + r.state()); r.transaction().ifPresent(p -> out.add("TRANSACTION: " + p));
                r.planSha256().ifPresent(hash -> out.add("PLAN SHA-256: " + hash)); r.installed().ifPresent(p -> out.add("INSTALLED: " + p)); });
            view.receipt().ifPresent(path -> out.add("RECEIPT: " + path));
            view.error().or(controller::globalError).ifPresent(error -> { out.add("ERROR: " + error.code()); out.add(error.message()); out.add(error.remediation()); });
        }
        if (!notice.isBlank()) out.add(notice);
        return out.stream().map(PreservedDeveloperScreen::safe).toList();
    }
    /** Bounded literal display, no formatting/control/bidi injection and no clickable JSON components. */
    static String safe(String text) {
        StringBuilder result = new StringBuilder();
        text.codePoints().limit(8192).forEach(c -> { if (c == '\t') result.append(' ');
            else if (c != 0xA7 && !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT) result.appendCodePoint(c); });
        return result.toString();
    }
    @Override public void resize(Minecraft game, int width, int height) {
        focusIndex = children().indexOf(getFocused()); saveCursors(); super.resize(game, width, height);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (lines != null && getFocused() == lines) {
            if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
                lines.setScrollAmount(lines.getScrollAmount() + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * Math.max(12, height - 150)); return true;
            }
            if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) { lines.setScrollAmount(key == GLFW.GLFW_KEY_HOME ? 0 : lines.getMaxScroll()); return true; }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void onClose() { if (controller.isInstalling()) return; closeSession(); minecraft.setScreen(parent); }
    @Override public void removed() { if (!childNavigation) closeSession(); }
    void closeSession() { if (!closed) { closed = true; form.invalidate(); session.invalidate(); controller.close(); } }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); graphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(text("warning").getString(), width - 16), width / 2, 20, 0xFFD2A0);
        fields.forEach((field, box) -> graphics.drawString(font, font.plainSubstrByWidth(text(field.name()).getString(), box.getWidth()), box.getX(), box.getY() - 11, 0xCCCCCC));
        super.render(graphics, mouseX, mouseY, partialTick);
        String status = !notice.isBlank() ? notice : controller.isInstalling() ? text("busy").getString()
                : busy() ? text("reading_history").getString()
                : text("state", text("state." + controller.preservedInstallation().state().name()).getString()).getString();
        graphics.drawString(font, font.plainSubstrByWidth(safe(status), width - 16), 8, height - 10, 0xD7C4AB);
    }
    @OnlyIn(Dist.CLIENT)
    private final class Lines extends ObjectSelectionList<Line> {
        Lines(int top) { super(PreservedDeveloperScreen.this.minecraft, PreservedDeveloperScreen.this.width - 16,
                PreservedDeveloperScreen.this.height, top, PreservedDeveloperScreen.this.height - 66, 12); setRenderBackground(false); setRenderTopAndBottom(false); }
        @Override public int getRowWidth() { return width - 14; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
        void update(String content) {
            double scroll = getScrollAmount(); setFocused(null); clearEntries(); setSelected(null);
            for (String paragraph : content.split("\n", -1)) {
                String remaining = paragraph;
                do { String line = font.plainSubstrByWidth(remaining, Math.max(20, getRowWidth() - 10)); addEntry(new Line(line));
                    if (line.isEmpty()) break; remaining = remaining.substring(line.length());
                } while (!remaining.isEmpty());
            }
            setScrollAmount(scroll);
        }
    }
    @OnlyIn(Dist.CLIENT)
    private final class Line extends ObjectSelectionList.Entry<Line> {
        final String text;
        Line(String text) { this.text = text; }
        @Override public Component getNarration() { return Component.literal(text); }
        @Override public void render(GuiGraphics g, int i, int y, int x, int w, int h, int mx, int my, boolean hovered, float tick) { g.drawString(font, text, x + 3, y + 2, 0xDDDDDD); }
        @Override public boolean mouseClicked(double x, double y, int button) { if (button != 0) return false; lines.setFocused(this); return true; }
    }
    @OnlyIn(Dist.CLIENT)
    private static final class Consent extends ConfirmScreen {
        private final PreservedDeveloperScreen owner;
        private boolean returning;
        Consent(PreservedDeveloperScreen owner, Component title, Component warning, it.unimi.dsi.fastutil.booleans.BooleanConsumer accepted) {
            super(accepted, title, warning, text("confirm"), Component.translatable("gui.cancel")); this.owner = owner;
        }
        @Override public void onClose() { callback.accept(false); }
        @Override public void removed() { if (!returning) owner.closeSession(); }
    }
}
