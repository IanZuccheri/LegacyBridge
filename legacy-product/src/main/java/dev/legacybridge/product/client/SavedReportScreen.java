package dev.legacybridge.product.client;

import dev.legacybridge.product.history.SavedReport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/** Literal, paged report data only. Never executes JSON chat components or opens historical paths. */
@OnlyIn(Dist.CLIENT)
public final class SavedReportScreen extends Screen {
    private final HistoryScreen owner;
    private final UUID job;
    private final List<Button> selectors = new ArrayList<>();
    private SavedReport report;
    private Lines lines;
    private Button previous, next, copy, cancel;
    private int page, focusIndex;
    private boolean reading, returning;
    private String notice = "";

    SavedReportScreen(HistoryScreen owner, UUID job) { super(text("title")); this.owner = owner; this.job = job; }
    boolean isOwnedBy(HistoryScreen screen) { return owner == screen; }
    private static Component text(String key, Object... args) { return Component.translatable("legacybridge.saved_reports." + key, args); }

    @Override protected void init() {
        double scroll = lines == null ? 0 : lines.getScrollAmount();
        selectors.clear(); int third = (width - 24) / 3;
        int index = 0;
        for (var kind : SavedReport.Kind.values()) {
            selectors.add(addRenderableWidget(Button.builder(text(kind.name()), button -> read(kind))
                    .bounds(8 + (third + 4) * index++, 43, third, 20).build()));
        }
        lines = addRenderableWidget(new Lines()); lines.setLeftPos(8);
        previous = addRenderableWidget(Button.builder(HistoryScreen.text("previous"), button -> changePage(-1))
                .bounds(8, height - 54, third, 20).build());
        next = addRenderableWidget(Button.builder(HistoryScreen.text("next"), button -> changePage(1))
                .bounds(12 + third, height - 54, third, 20).build());
        cancel = addRenderableWidget(Button.builder(ConversionScreen.text("cancel"), button -> {
            if (reading) notice = ConversionScreen.text(owner.controller().cancel() ? "cancel_requested" : "cancel_unavailable").getString();
        }).bounds(16 + third * 2, height - 54, third, 20).build());
        copy = addRenderableWidget(Button.builder(text("copy_page"), button -> {
            if (report != null) minecraft.keyboardHandler.setClipboard(report.page(page));
        }).bounds(8, height - 30, (width - 20) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(12 + (width - 20) / 2, height - 30, (width - 20) / 2, 20).build());
        showPage(); update(); lines.setScrollAmount(scroll);
        setInitialFocus(children().get(Math.max(0, Math.min(focusIndex, children().size() - 1))));
    }

    private void read(SavedReport.Kind kind) {
        if (reading) return;
        if (!owner.controller().readSavedReport(job, kind)) { notice = text("busy").getString(); return; }
        reading = true; report = null; page = 0; notice = text("reading").getString(); showPage(); update();
    }
    @Override public void tick() { update(); }
    private void update() {
        if (reading && !owner.controller().snapshot().busy()) {
            reading = false;
            report = owner.controller().savedReport().filter(value -> value.jobId().equals(job)).orElse(null);
            if (report == null) {
                String code = owner.controller().globalError().map(error -> error.code()).orElse("CANCELLED");
                String advice = switch (code) {
                    case "REPORT_INDEX_MISSING", "REPORT_NOT_RECORDED" -> "missing";
                    case "REPORT_CHANGED", "REPORT_HASH_MISMATCH" -> "changed";
                    case "REPORT_TOO_LARGE", "REPORT_TIME_LIMIT" -> "limit";
                    case "REPORT_JSON_INVALID", "REPORT_UTF8_INVALID", "REPORT_INDEX_INVALID", "REPORT_INDEX_SCHEMA" -> "invalid";
                    default -> "retry";
                };
                notice = text("error", code, text(advice).getString()).getString();
            } else notice = text("hash_match").getString();
            if (report != null && report.displaySanitized()) notice += " | " + text("sanitized").getString();
            showPage();
        }
        for (Button selector : selectors) selector.active = !reading && !owner.controller().snapshot().busy();
        previous.active = report != null && page > 0; next.active = report != null && page + 1 < report.pages();
        copy.active = report != null; cancel.active = reading && owner.controller().canCancel();
    }
    private void changePage(int delta) { if (report == null) return; page = Math.max(0, Math.min(report.pages() - 1, page + delta)); showPage(); lines.setScrollAmount(0); update(); }
    private void showPage() { if (lines != null) lines.update(report == null ? (notice.isBlank() ? text("select").getString() : notice) : report.page(page)); }

    @Override public void resize(Minecraft minecraft, int width, int height) { focusIndex = children().indexOf(getFocused()); super.resize(minecraft, width, height); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics); graphics.drawCenteredString(font, title, width / 2, 7, 0xFFFFFF);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(text("warning").getString(), Math.max(20, width - 16)), width / 2, 21, 0xFFD2A0);
        String state = report == null ? notice : text("page", page + 1, report.pages()).getString() + " | " + notice;
        graphics.drawString(font, font.plainSubstrByWidth(state, Math.max(20, width - 16)), 8, 67, 0xBBBBBB);
        if (!selectors.isEmpty()) selectors.get(0).setTooltip(Tooltip.create(Component.literal(state + (report == null ? "" : " | SHA-256: " + report.sha256()))));
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (getFocused() == lines && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
            lines.setScrollAmount(lines.getScrollAmount() + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * Math.max(12, height - 145)); return true;
        }
        if (getFocused() == lines && (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END)) {
            lines.setScrollAmount(key == GLFW.GLFW_KEY_HOME ? 0 : lines.getMaxScroll()); return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void onClose() { if (reading) owner.controller().cancel(); returning = true; owner.returnFromDetails(); }
    @Override public void removed() { if (!returning) owner.closeSession(); }

    @OnlyIn(Dist.CLIENT)
    private final class Lines extends ObjectSelectionList<Line> {
        private Lines() { super(SavedReportScreen.this.minecraft, SavedReportScreen.this.width - 16,
                SavedReportScreen.this.height, 81, SavedReportScreen.this.height - 60, 12); setRenderBackground(false); setRenderTopAndBottom(false); }
        @Override public int getRowWidth() { return width - 14; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
        private void update(String page) {
            setFocused(null); clearEntries(); setSelected(null);
            for (String paragraph : page.split("\n", -1)) {
                String remaining = paragraph.replace("\t", "    ");
                do {
                    String line = font.plainSubstrByWidth(remaining, Math.max(20, getRowWidth() - 10));
                    addEntry(new Line(line));
                    if (line.isEmpty()) break;
                    remaining = remaining.substring(line.length());
                } while (!remaining.isEmpty());
            }
        }
    }
    @OnlyIn(Dist.CLIENT)
    private final class Line extends ObjectSelectionList.Entry<Line> {
        private final String value;
        private Line(String value) { this.value = value; }
        @Override public Component getNarration() { return Component.literal(value); }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) { graphics.drawString(font, value, x + 3, y + 2, 0xDDDDDD); }
        @Override public boolean mouseClicked(double x, double y, int button) { if (button != 0) return false; lines.setFocused(this); return true; }
    }
}
