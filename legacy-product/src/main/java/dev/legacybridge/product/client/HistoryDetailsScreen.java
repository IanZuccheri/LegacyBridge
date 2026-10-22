package dev.legacybridge.product.client;

import dev.legacybridge.product.history.HistoryPresentation;
import dev.legacybridge.product.history.JobHistory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/** A captured receipt page, never a live status, file opener, installer or restart action. */
@OnlyIn(Dist.CLIENT)
public final class HistoryDetailsScreen extends Screen {
    private final HistoryScreen owner;
    private final JobHistory snapshot;
    private final Optional<JobHistory.Job> job;
    private Lines lines;
    private Button previous, next;
    private int receiptIndex, focusIndex;
    private boolean returning;

    HistoryDetailsScreen(HistoryScreen owner, JobHistory snapshot, Optional<JobHistory.Job> job) {
        super(HistoryScreen.text(job.isPresent() ? "receipt_title" : "diagnostics"));
        this.owner = owner; this.snapshot = snapshot; this.job = job;
    }

    boolean isOwnedBy(HistoryScreen screen) { return owner == screen; }

    @Override protected void init() {
        double scroll = lines == null ? 0 : lines.getScrollAmount();
        lines = addRenderableWidget(new Lines());
        lines.setLeftPos(8);
        int half = (width - 20) / 2;
        previous = addRenderableWidget(Button.builder(HistoryScreen.text("previous"), button -> page(-1))
                .bounds(8, height - 54, half, 20).build());
        next = addRenderableWidget(Button.builder(HistoryScreen.text("next"), button -> page(1))
                .bounds(12 + half, height - 54, half, 20).build());
        addRenderableWidget(Button.builder(HistoryScreen.text("copy"), button ->
                minecraft.keyboardHandler.setClipboard(String.join("\n", paragraphs())))
                .bounds(8, height - 30, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(12 + half, height - 30, half, 20).build());
        update();
        lines.setScrollAmount(scroll);
        setInitialFocus(children().get(Math.max(0, Math.min(focusIndex, children().size() - 1))));
    }

    private void page(int delta) {
        int total = job.map(value -> value.receipts().size()).orElse(0);
        receiptIndex = Math.max(0, Math.min(Math.max(0, total - 1), receiptIndex + delta));
        update();
        lines.setScrollAmount(0);
    }

    private void update() {
        previous.active = job.isPresent() && receiptIndex > 0;
        next.active = job.isPresent() && receiptIndex + 1 < job.orElseThrow().receipts().size();
        lines.update(paragraphs());
    }

    private List<String> paragraphs() {
        List<String> result = new ArrayList<>();
        result.add(HistoryScreen.text("warning").getString());
        result.add(HistoryScreen.text("details_warning").getString());
        result.add(HistoryScreen.text("inert_paths").getString());
        result.add(HistoryScreen.text("snapshot_warning").getString());
        if (job.isPresent()) result.add(HistoryScreen.text("ordering_hint").getString());
        List<HistoryPresentation.Field> fields = job.filter(value -> !value.receipts().isEmpty())
                .map(value -> HistoryPresentation.receipt(value, receiptIndex)).orElseGet(() -> HistoryPresentation.diagnostics(snapshot));
        fields.forEach(field -> result.add(HistoryScreen.text("field." + field.key()).getString() + ": " + field.value()));
        return List.copyOf(result);
    }

    @Override public void resize(Minecraft minecraft, int width, int height) {
        focusIndex = children().indexOf(getFocused());
        super.resize(minecraft, width, height);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        graphics.fill(8, 26, width - 8, height - 60, 0x88000000);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (getFocused() == lines) {
            if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
                lines.setScrollAmount(lines.getScrollAmount() + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * Math.max(12, height - 100));
                return true;
            }
            if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
                lines.setScrollAmount(key == GLFW.GLFW_KEY_HOME ? 0 : lines.getMaxScroll());
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void onClose() { returning = true; owner.returnFromDetails(); }
    @Override public void removed() { if (!returning) owner.closeSession(); }

    @OnlyIn(Dist.CLIENT)
    private final class Lines extends ObjectSelectionList<Line> {
        private Lines() {
            super(HistoryDetailsScreen.this.minecraft, HistoryDetailsScreen.this.width - 16,
                    HistoryDetailsScreen.this.height, 26, HistoryDetailsScreen.this.height - 60, 12);
            setRenderBackground(false); setRenderTopAndBottom(false);
        }
        @Override public int getRowWidth() { return width - 14; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
        private void update(List<String> paragraphs) {
            setFocused(null); clearEntries(); setSelected(null);
            int count = 0;
            for (String paragraph : paragraphs) {
                String remaining = paragraph;
                while (!remaining.isEmpty()) {
                    // Explicit cap on render objects, independent of the reader's disk/JSON limits.
                    if (count++ >= 2048) {
                        addEntry(new Line(HistoryScreen.text("display_limit").getString())); return;
                    }
                    String line = font.plainSubstrByWidth(remaining, Math.max(30, getRowWidth() - 10));
                    if (line.isEmpty()) { addEntry(new Line(HistoryScreen.text("display_limit").getString())); return; }
                    addEntry(new Line(line));
                    remaining = remaining.substring(line.length());
                }
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private final class Line extends ObjectSelectionList.Entry<Line> {
        private final String value;
        private Line(String value) { this.value = value; }
        @Override public Component getNarration() { return Component.literal(value); }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int rowWidth, int rowHeight,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, value, x + 3, y + 2, 0xDDDDDD);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) return false;
            lines.setFocused(this); return true;
        }
    }
}
