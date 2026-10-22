package dev.legacybridge.product.client;

import dev.legacybridge.product.controller.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/** Developer-only view of the existing worker pipeline; not another converter or installer. */
@OnlyIn(Dist.CLIENT)
public final class PreservedPreparationScreen extends Screen {
    private enum Page { INPUT, DATA, RESULT }
    private final PreservedDeveloperScreen owner;
    private final PreservedPreparationForm form = new PreservedPreparationForm();
    private final Map<PreservedPreparationForm.Field, EditBox> fields = new EnumMap<>(PreservedPreparationForm.Field.class);
    private final Map<PreservedPreparationForm.Field, Integer> cursors = new EnumMap<>(PreservedPreparationForm.Field.class);
    private Page page = Page.INPUT;
    private Button mode, prepare, cancel, use;
    private Lines lines;
    private boolean returning;
    private int focus;
    private String notice = "", signature = "";
    PreservedPreparationScreen(PreservedDeveloperScreen owner) { super(text("title")); this.owner = owner; }
    static Component text(String key, Object... args) { return Component.translatable("legacybridge.preparation." + key, args); }
    private ConversionController controller() { return owner.controller(); }
    private boolean busy() { return controller().snapshot().busy(); }
    private void saveCursors() { fields.forEach((key, box) -> cursors.put(key, box.getCursorPosition())); }
    @Override protected void init() {
        returning = false; saveCursors(); double scroll = lines == null ? 0 : lines.getScrollAmount();
        fields.clear(); lines = null; mode = null; int third = (width - 24) / 3, half = (width - 24) / 2;
        for (Page tab : Page.values()) addRenderableWidget(Button.builder(text(tab.name()), b -> changePage(tab))
                .bounds(8 + (third + 4) * tab.ordinal(), 33, third, 20).build());
        if (page == Page.INPUT) {
            field(PreservedPreparationForm.Field.ORIGINAL, 8, 72, half);
            field(PreservedPreparationForm.Field.ORIGINAL_HASH, 16 + half, 72, half);
            field(PreservedPreparationForm.Field.CANDIDATE, 8, 111, half);
            field(PreservedPreparationForm.Field.CANDIDATE_HASH, 16 + half, 111, half);
            mode = addRenderableWidget(Button.builder(text(form.convert() ? "convert" : "existing"), b -> { form.convert(!form.convert()); update(); })
                    .bounds(8, 136, width - 16, 20).build());
        } else if (page == Page.DATA) {
            field(PreservedPreparationForm.Field.DATA, 8, 76, width - 16);
            field(PreservedPreparationForm.Field.DATA_HASH, 8, 115, width - 16);
        } else { lines = addRenderableWidget(new Lines()); lines.setLeftPos(8); }
        prepare = addRenderableWidget(Button.builder(text("prepare"), b -> prepare()).bounds(8, height - 80, half, 20).build());
        cancel = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> notice = text(controller().cancel() ? "cancel_requested" : "refused").getString())
                .bounds(16 + half, height - 80, half, 20).build());
        use = addRenderableWidget(Button.builder(text("use"), b -> {
            var selected = form.consume(controller().preparation(), busy()).flatMap(controller()::preparedSelection);
            if (selected.isEmpty()) { notice = text("refused").getString(); return; }
            returning = true; owner.acceptPrepared(selected.orElseThrow());
        }).bounds(8, height - 56, half, 20).build());
        addRenderableWidget(Button.builder(PreservedDeveloperScreen.text("copy"), b -> minecraft.keyboardHandler.setClipboard(String.join("\n", details())))
                .bounds(16 + half, height - 56, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(8, height - 32, width - 16, 20).build());
        signature = ""; update(); if (lines != null) lines.setScrollAmount(scroll);
        setInitialFocus(children().get(Math.max(0, Math.min(focus, children().size() - 1))));
    }
    private void field(PreservedPreparationForm.Field field, int x, int y, int w) {
        EditBox box = addRenderableWidget(new EditBox(font, x, y, w, 20, text(field.name())));
        box.setMaxLength(field.limit()); box.setValue(form.get(field)); box.setCursorPosition(cursors.getOrDefault(field, 0));
        box.setResponder(value -> { form.set(field, value); notice = ""; }); box.setTooltip(Tooltip.create(text(field.name()))); fields.put(field, box);
    }
    private void changePage(Page value) { saveCursors(); page = value; focus = 0; rebuildWidgets(); }
    private void prepare() {
        owner.publishSession();
        try {
            var operation = controller().preparePreserved(form.request()); operation.ifPresent(form::submitted);
            notice = operation.isPresent() ? "" : text("refused").getString(); if (operation.isPresent()) changePage(Page.RESULT);
        } catch (PreservedPreparationForm.InvalidField invalid) {
            notice = text("invalid", text(invalid.field().name()).getString()).getString();
            changePage(invalid.field().ordinal() < 4 ? Page.INPUT : Page.DATA); setFocused(fields.get(invalid.field()));
        }
    }
    @Override public void tick() { owner.publishSession(); fields.values().forEach(EditBox::tick); update(); }
    private void update() {
        boolean busy = busy(); prepare.active = !busy; cancel.active = controller().canCancel(); use.active = form.canUse(controller().preparation(), busy);
        if (mode != null) { mode.active = !busy; mode.setMessage(text(form.convert() ? "convert" : "existing")); }
        fields.forEach((field, box) -> box.setEditable(!busy && (!form.convert() || field != PreservedPreparationForm.Field.CANDIDATE && field != PreservedPreparationForm.Field.CANDIDATE_HASH)));
        if (lines != null) { String content = String.join("\n", details()); if (!signature.equals(content)) { signature = content; lines.update(content); } }
    }
    private List<String> details() {
        var view = controller().preparation(); List<String> out = new ArrayList<>(); out.add(text("warning").getString());
        out.add(text("state." + view.state()).getString()); out.add(text(form.convert() ? "convert" : "existing").getString());
        view.operationId().ifPresent(id -> out.add("OPERATION: " + id));
        view.progress().ifPresent(p -> out.add(p.stage() + ": " + p.detail() + " (" + p.completed() + "/" + (p.total() < 0 ? "?" : p.total()) + ")"));
        for (var field : PreservedPreparationForm.Field.values()) if (!form.convert() || field != PreservedPreparationForm.Field.CANDIDATE && field != PreservedPreparationForm.Field.CANDIDATE_HASH)
            out.add(text(field.name()).getString() + ": " + form.get(field));
        view.candidate().ifPresent(c -> { out.add("CANDIDATE: " + c.path()); out.add("SHA-256: " + c.sha256()); });
        view.admission().ifPresent(a -> { out.add("ADMISSION: " + a.state()); a.transaction().ifPresent(p -> out.add(p.toString())); a.planSha256().ifPresent(out::add); });
        new TreeMap<>(view.reports()).forEach((name, path) -> out.add(name + ": " + path));
        view.error().or(controller()::globalError).ifPresent(e -> { out.add(e.code()); out.add(e.message()); out.add(e.remediation()); });
        out.add(text("separate_approval").getString()); if (!notice.isBlank()) out.add(notice);
        return out.stream().map(PreservedDeveloperScreen::safe).toList();
    }
    @Override public void resize(Minecraft game, int width, int height) { focus = children().indexOf(getFocused()); saveCursors(); super.resize(game, width, height); }
    @Override public void onClose() { if (busy()) controller().cancel(); returning = true; owner.returnFromPreparation(); }
    @Override public void removed() { if (!returning) owner.closeSession(); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (lines != null && getFocused() == lines) {
            if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) { lines.setScrollAmount(lines.getScrollAmount() + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * Math.max(12, height - 160)); return true; }
            if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) { lines.setScrollAmount(key == GLFW.GLFW_KEY_HOME ? 0 : lines.getMaxScroll()); return true; }
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
        renderBackground(graphics); graphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(text("warning").getString(), width - 16), width / 2, 20, 0xFFD2A0);
        fields.forEach((field, box) -> graphics.drawString(font, font.plainSubstrByWidth(text(field.name()).getString(), box.getWidth()), box.getX(), box.getY() - 10, 0xCCCCCC));
        super.render(graphics, mouseX, mouseY, tick);
        graphics.drawString(font, font.plainSubstrByWidth(PreservedDeveloperScreen.safe(notice), width - 16), 8, height - 10, 0xD7C4AB);
    }
    @OnlyIn(Dist.CLIENT)
    private final class Lines extends ObjectSelectionList<Line> {
        Lines() { super(PreservedPreparationScreen.this.minecraft, PreservedPreparationScreen.this.width - 16, PreservedPreparationScreen.this.height,
                60, PreservedPreparationScreen.this.height - 86, 12); setRenderBackground(false); setRenderTopAndBottom(false); }
        @Override public int getRowWidth() { return width - 14; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
        void update(String content) { double scroll = getScrollAmount(); setFocused(null); clearEntries(); setSelected(null);
            for (String paragraph : content.split("\n", -1)) { String remaining = paragraph;
                do { String line = font.plainSubstrByWidth(remaining, Math.max(20, getRowWidth() - 10)); addEntry(new Line(line));
                    if (line.isEmpty()) break; remaining = remaining.substring(line.length()); } while (!remaining.isEmpty()); }
            setScrollAmount(scroll);
        }
    }
    @OnlyIn(Dist.CLIENT)
    private final class Line extends ObjectSelectionList.Entry<Line> {
        final String value; Line(String value) { this.value = value; }
        @Override public Component getNarration() { return Component.literal(value); }
        @Override public void render(GuiGraphics g, int i, int y, int x, int w, int h, int mx, int my, boolean hovered, float tick) { g.drawString(font, value, x + 3, y + 2, 0xDDDDDD); }
        @Override public boolean mouseClicked(double x, double y, int button) { if (button != 0) return false; lines.setFocused(this); return true; }
    }
}
