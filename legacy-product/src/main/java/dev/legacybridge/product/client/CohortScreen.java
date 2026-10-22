package dev.legacybridge.product.client;

import dev.legacybridge.converter.service.InventoryResult;
import dev.legacybridge.product.controller.CohortInspectionView;
import dev.legacybridge.product.controller.CohortPresentation;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/** Frozen explicit review -> private static proof. No enqueue, Convert, Apply or filesystem operations. */
@OnlyIn(Dist.CLIENT)
public final class CohortScreen extends Screen {
    private final ConversionScreen owner;
    private final List<InventoryResult.Entry> selection;
    private final String explicitBundle;
    private CohortInspectionView view;
    private Lines lines;
    private Button inspect, cancel;
    private boolean requested, returning;
    private String notice = "";
    private int focusIndex = 1;

    CohortScreen(ConversionScreen owner, List<InventoryResult.Entry> selected, String explicitBundle) {
        super(text("title")); this.owner = owner; this.selection = List.copyOf(selected); this.explicitBundle = explicitBundle;
        view = CohortInspectionView.review(selection);
    }
    boolean isOwnedBy(ConversionScreen screen) { return owner == screen; }
    @Override protected void init() {
        double scroll = lines == null ? 0 : lines.getScrollAmount();
        lines = addRenderableWidget(new Lines()); lines.setLeftPos(8);
        int fourth = (width - 28) / 4;
        inspect = addRenderableWidget(Button.builder(text("inspect"), b -> {
            if (owner.controller().inspectCohort(selection, explicitBundle)) { requested = true; notice = ""; }
            else notice = text("refused").getString();
            update();
        }).bounds(8, height - 30, fourth, 20).build());
        cancel = addRenderableWidget(Button.builder(ConversionScreen.text("cancel"), b ->
                notice = ConversionScreen.text(owner.controller().cancel() ? "cancel_requested" : "cancel_unavailable").getString())
                .bounds(12 + fourth, height - 30, fourth, 20).build());
        addRenderableWidget(Button.builder(CompositionScreen.text("copy"), b ->
                minecraft.keyboardHandler.setClipboard(String.join("\n", paragraphs())))
                .bounds(16 + fourth * 2, height - 30, fourth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(20 + fourth * 3, height - 30, fourth, 20).build());
        lines.update(paragraphs()); update(); lines.setScrollAmount(scroll);
        setInitialFocus(children().get(Math.max(0, Math.min(focusIndex, children().size() - 1))));
    }
    @Override public void tick() { update(); }
    private void update() {
        if (requested) {
            var observed = owner.controller().cohortInspection();
            if (observed != view) { view = observed; lines.update(paragraphs()); }
        }
        inspect.active = !owner.controller().snapshot().busy();
        cancel.active = requested && view.status() == CohortInspectionView.Status.RUNNING && owner.controller().canCancel();
    }
    private List<String> paragraphs() {
        return CohortPresentation.paragraphs(view, (key, args) -> Component.translatable(key, args).getString());
    }
    static Component text(String key, Object... args) { return Component.translatable("legacybridge.cohort." + key, args); }
    @Override public void resize(Minecraft minecraft, int width, int height) {
        focusIndex = children().indexOf(getFocused()); super.resize(minecraft, width, height);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); graphics.drawCenteredString(font, title, width / 2, 7, 0xFFFFFF);
        String progress = requested && view.status() == CohortInspectionView.Status.RUNNING
                ? owner.controller().snapshot().progress().map(p -> p.stage() + " " + p.completed() + (p.total() < 0 ? "/?" : "/" + p.total())).orElse(notice) : notice;
        graphics.drawString(font, font.plainSubstrByWidth(ConversionScreen.safe(progress), Math.max(20, width - 16)), 8, 20, 0xCCCCCC);
        graphics.fill(8, 34, width - 8, height - 36, 0x88000000); super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (getFocused() == lines) {
            if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
                lines.setScrollAmount(lines.getScrollAmount() + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * Math.max(12, height - 85)); return true;
            }
            if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
                lines.setScrollAmount(key == GLFW.GLFW_KEY_HOME ? 0 : lines.getMaxScroll()); return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void onClose() { returning = true; owner.returnFromChild(); }
    @Override public void removed() { if (!returning) owner.closeSession(); }
    @OnlyIn(Dist.CLIENT)
    private final class Lines extends ObjectSelectionList<Line> {
        private Lines() {
            super(CohortScreen.this.minecraft, CohortScreen.this.width - 16, CohortScreen.this.height, 34, CohortScreen.this.height - 36, 12);
            setRenderBackground(false); setRenderTopAndBottom(false);
        }
        @Override public int getRowWidth() { return width - 14; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
        private void update(List<String> paragraphs) {
            double scroll = getScrollAmount(); setFocused(null); clearEntries(); setSelected(null); int count = 0;
            for (String paragraph : paragraphs) {
                String remaining = paragraph;
                while (!remaining.isEmpty()) {
                    if (count++ >= 1024) { addEntry(new Line(HistoryScreen.text("display_limit").getString())); setScrollAmount(scroll); return; }
                    String line = font.plainSubstrByWidth(remaining, Math.max(30, getRowWidth() - 10));
                    if (line.isEmpty()) { addEntry(new Line(HistoryScreen.text("display_limit").getString())); return; }
                    addEntry(new Line(line)); remaining = remaining.substring(line.length());
                }
            }
            setScrollAmount(scroll);
        }
    }
    @OnlyIn(Dist.CLIENT)
    private final class Line extends ObjectSelectionList.Entry<Line> {
        private final String value;
        private Line(String value) { this.value = value; }
        @Override public Component getNarration() { return Component.literal(value); }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, value, x + 3, y + 2, 0xDDDDDD);
        }
        @Override public boolean mouseClicked(double x, double y, int button) { if (button != 0) return false; lines.setFocused(this); return true; }
    }
}
