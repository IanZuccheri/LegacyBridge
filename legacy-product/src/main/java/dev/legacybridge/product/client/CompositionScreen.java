package dev.legacybridge.product.client;

import dev.legacybridge.product.controller.CompositionInspectionView;
import java.util.ArrayList;
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

/** Advanced finite static inspection. No Convert, Apply, path opener or candidate JAR action. */
@OnlyIn(Dist.CLIENT)
public final class CompositionScreen extends Screen {
    private final ConversionScreen owner;
    private final String explicitBundle;
    private CompositionInspectionView view;
    private dev.legacybridge.product.controller.MappingDataView dataView;
    private Lines lines;
    private Button inspect, cancel, reviewData;
    private boolean started, returning;
    private String notice = "";
    private int focusIndex;

    CompositionScreen(ConversionScreen owner, String explicitBundle) {
        super(text("title")); this.owner = owner; this.explicitBundle = explicitBundle;
    }
    boolean isOwnedBy(ConversionScreen screen) { return owner == screen; }

    @Override protected void init() {
        double scroll = lines == null ? 0 : lines.getScrollAmount();
        lines = addRenderableWidget(new Lines()); lines.setLeftPos(8);
        int fourth = (width - 32) / 5;
        inspect = addRenderableWidget(Button.builder(text("inspect"), button -> inspect())
                .bounds(8, height - 30, fourth, 20).build());
        cancel = addRenderableWidget(Button.builder(ConversionScreen.text("cancel"), button -> {
            notice = ConversionScreen.text(owner.controller().cancel() ? "cancel_requested" : "cancel_unavailable").getString();
        }).bounds(12 + fourth, height - 30, fourth, 20).build());
        addRenderableWidget(Button.builder(text("copy"), button -> minecraft.keyboardHandler.setClipboard(String.join("\n", paragraphs())))
                .bounds(16 + fourth * 2, height - 30, fourth, 20).build());
        reviewData = addRenderableWidget(Button.builder(text("review_data"), button -> {
            notice = owner.controller().reviewMappingData(explicitBundle) ? "" : text("busy").getString();
        }).bounds(20 + fourth * 3, height - 30, fourth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(24 + fourth * 4, height - 30, fourth, 20).build());
        if (!started) { started = true; inspect(); }
        view = null; update(); lines.setScrollAmount(scroll);
        setInitialFocus(children().get(Math.max(0, Math.min(focusIndex, children().size() - 1))));
    }

    private void inspect() {
        notice = owner.controller().inspectSelectedComposition(explicitBundle) ? "" : text("busy").getString();
    }
    @Override public void tick() { update(); }
    private void update() {
        var observed = owner.controller().compositionInspection();
        var observedData = owner.controller().mappingDataReview();
        if (observed != view || observedData != dataView) { view = observed; dataView = observedData; lines.update(paragraphs()); }
        boolean busy = owner.controller().snapshot().busy();
        inspect.active = !busy;
        reviewData.active = !busy;
        cancel.active = owner.controller().canCancel();
    }
    private List<String> paragraphs() {
        List<String> result = new ArrayList<>();
        result.add(text("warning").getString());
        result.add(text("data").getString());
        result.add(text("limits").getString());
        result.add(text("review_hint").getString());
        if (dataView != null) {
            result.add(text("review_status", dataView.status().name()).getString());
            dataView.review().ifPresent(review -> {
                result.add(text("review_scope", review.scope(), review.complete()).getString());
                review.selection().expectedManifestSha256().ifPresent(hash -> result.add("Data SHA-256: " + hash));
                result.add(safe(review.distributionPolicy()));
                for (var index : review.indexes()) result.add(index.id() + ": classes=" + index.classes() + ", members=" + index.members());
            });
            dataView.error().ifPresent(error -> result.add(safe(error.code() + ": " + error.message() + " " + error.remediation())));
        }
        if (view == null) return result;
        result.add(text("status." + view.status().name().toLowerCase(java.util.Locale.ROOT)).getString());
        view.input().ifPresent(value -> result.add(text("input", safe(value.toString())).getString()));
        view.inputSha256().ifPresent(value -> result.add("SHA-256: " + value));
        result.add(text("runtime", view.runtimeModId(), view.runtimeVersion()).getString());
        for (var operation : view.operations()) {
            result.add(text("operation", operation.id(), operation.kind(), operation.claimedEntries()).getString());
            result.add(text("implementation", operation.implementation()).getString());
            result.add(text("requires", String.join(", ", operation.requires())).getString());
        }
        view.error().ifPresent(error -> {
            result.add(safe(error.code()) + ": " + safe(error.message())); result.add(safe(error.remediation()));
        });
        result.add(text("gates").getString());
        view.remainingGates().forEach(gate -> result.add(text("gate." + gate.name().toLowerCase(java.util.Locale.ROOT)).getString()));
        view.report().ifPresent(path -> result.add(text("report", safe(path.toString())).getString()));
        result.add(text("report_hint").getString());
        return List.copyOf(result);
    }
    static Component text(String key, Object... args) { return Component.translatable("legacybridge.composition." + key, args); }
    private static String safe(String value) { return ConversionScreen.safe(value); }
    @Override public void resize(Minecraft minecraft, int width, int height) {
        focusIndex = children().indexOf(getFocused()); super.resize(minecraft, width, height);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 7, 0xFFFFFF);
        String progress = owner.controller().snapshot().progress().map(value -> value.stage() + " " + value.completed()
                + (value.total() < 0 ? "/?" : "/" + value.total())).orElse(notice);
        graphics.drawString(font, font.plainSubstrByWidth(safe(progress), Math.max(20, width - 16)), 8, 20, 0xCCCCCC);
        graphics.fill(8, 34, width - 8, height - 36, 0x88000000);
        super.render(graphics, mouseX, mouseY, partialTick);
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
            super(CompositionScreen.this.minecraft, CompositionScreen.this.width - 16, CompositionScreen.this.height,
                    34, CompositionScreen.this.height - 36, 12);
            setRenderBackground(false); setRenderTopAndBottom(false);
        }
        @Override public int getRowWidth() { return width - 14; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
        private void update(List<String> paragraphs) {
            double scroll = getScrollAmount(); setFocused(null); clearEntries(); setSelected(null);
            int count = 0;
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
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int rowWidth, int rowHeight,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) { graphics.drawString(font, value, x + 3, y + 2, 0xDDDDDD); }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) return false; lines.setFocused(this); return true;
        }
    }
}
