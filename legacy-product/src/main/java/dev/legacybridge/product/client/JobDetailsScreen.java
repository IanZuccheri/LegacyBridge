package dev.legacybridge.product.client;

import dev.legacybridge.converter.service.InventoryResult;
import dev.legacybridge.product.controller.JobPathKind;
import dev.legacybridge.product.controller.JobView;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

/** Bounded, scrollable technical view of typed summaries. It never reads/parses report files. */
@OnlyIn(Dist.CLIENT)
public final class JobDetailsScreen extends Screen {
    private final ConversionScreen owner;
    private final Optional<UUID> jobId;
    private final Optional<InventoryResult.Entry> inventory;
    private final List<Button> reportButtons = new ArrayList<>();
    private DetailList lines;
    private String lastText = "";
    private String notice = "";
    private boolean returning;
    private int focusIndex;

    JobDetailsScreen(ConversionScreen owner, Optional<UUID> jobId, Optional<InventoryResult.Entry> inventory) {
        super(ConversionScreen.text("details_title"));
        this.owner = owner;
        this.jobId = jobId;
        this.inventory = inventory;
    }

    boolean isOwnedBy(ConversionScreen screen) { return owner == screen; }

    @Override protected void init() {
        double scroll = lines == null ? 0 : lines.getScrollAmount();
        lines = addRenderableWidget(new DetailList());
        lines.setLeftPos(8);
        reportButtons.clear();
        int content = width - 16;
        int fourth = (content - 12) / 4;
        addReportButton("open_compatibility", JobPathKind.COMPATIBILITY_REPORT, 8, fourth);
        addReportButton("open_transformations", JobPathKind.TRANSFORMATION_LOG, 8 + fourth + 4, fourth);
        addReportButton("open_conversion", JobPathKind.CONVERSION_REPORT, 8 + (fourth + 4) * 2, fourth);
        addReportButton("open_result", JobPathKind.RESULT_FOLDER, 8 + (fourth + 4) * 3, fourth);
        addRenderableWidget(Button.builder(ConversionScreen.text("copy_paths"), button ->
                minecraft.keyboardHandler.setClipboard(String.join("\n", details())))
                .bounds(8, height - 29, (content - 4) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(12 + (content - 4) / 2, height - 29, (content - 4) / 2, 20).build());
        lastText = "";
        update();
        lines.setScrollAmount(scroll);
        setInitialFocus(children().get(Math.max(0, Math.min(focusIndex, children().size() - 1))));
    }

    private void addReportButton(String key, JobPathKind kind, int x, int width) {
        Button button = addRenderableWidget(Button.builder(ConversionScreen.text(key), pressed -> jobId.ifPresent(id ->
                owner.openJobPath(id, kind, message -> notice = message))).bounds(x, height - 53, width, 20)
                .tooltip(Tooltip.create(ConversionScreen.text(key + "_hint"))).build());
        reportButtons.add(button);
    }

    private Optional<JobView> job() {
        return jobId.flatMap(id -> owner.controller().jobs().stream().filter(value -> value.id().equals(id)).findFirst());
    }

    @Override public void tick() { update(); }

    private void update() {
        List<String> details = details();
        String text = String.join("\n", details);
        if (!text.equals(lastText)) {
            lastText = text;
            lines.update(details);
        }
        Optional<JobView> value = job();
        reportButtons.get(0).active = value.flatMap(JobView::compatibilityReport).isPresent();
        reportButtons.get(1).active = value.flatMap(JobView::transformationLog).isPresent();
        reportButtons.get(2).active = value.flatMap(JobView::conversionReport).isPresent();
        reportButtons.get(3).active = value.filter(job -> job.artifact().isPresent() || job.installed().isPresent()).isPresent();
    }

    private List<String> details() {
        List<String> text = new ArrayList<>();
        text.add(ConversionScreen.text("profile_limit").getString());
        owner.controller().globalError().ifPresent(error -> {
            text.add("SCREEN ERROR: " + safe(error.code()));
            text.add(safe(error.message()));
            text.add(safe(error.remediation()));
        });
        inventory.ifPresent(entry -> {
            text.add("FILE: " + safe(entry.path().getFileName().toString()));
            text.add("PATH: " + safe(entry.path().toString()));
            text.add("MODS: " + ConversionScreen.identities(entry));
            entry.mods().stream().limit(8).forEach(mod -> text.add("NAME: " + safe(mod.displayName())));
            text.add(ConversionScreen.inventoryText(dev.legacybridge.product.controller.ConversionController.classify(entry)).getString());
            text.add("SOURCE: " + entry.sourceProfile());
            text.add("COMPLEXITY: " + entry.complexity() + " / RISK SCORE: " + entry.riskScore());
            entry.fingerprint().ifPresent(fingerprint -> {
                text.add("SIZE: " + fingerprint.sizeBytes() + " bytes");
                text.add("SHA-256: " + fingerprint.sha256());
            });
            entry.error().ifPresent(error -> { text.add(error.code() + ": " + safe(error.message())); text.add(safe(error.remediation())); });
        });
        job().ifPresent(job -> {
            text.add("JOB: " + job.id());
            text.add("FILE: " + safe(job.displayName()));
            text.add("INPUT: " + safe(job.input().toString()));
            text.add(Component.translatable(job.summaryKey()).getString());
            job.progress().ifPresent(progress -> {
                text.add("STAGE: " + progress.stage() + " " + progress.completed() + (progress.total() < 0 ? "/?" : "/" + progress.total()));
                text.add(safe(progress.detail()));
            });
            job.error().ifPresent(error -> { text.add(error.code() + ": " + safe(error.message())); text.add(safe(error.remediation())); });
            addPath(text, "STAGED JAR", job.artifact());
            addPath(text, "COMPATIBILITY REPORT", job.compatibilityReport());
            addPath(text, "TRANSFORMATION LOG", job.transformationLog());
            addPath(text, "CONVERSION REPORT", job.conversionReport());
            addPath(text, "INSTALLED FOR NEXT LAUNCH", job.installed());
        });
        if (jobId.isPresent() && job().isEmpty()) text.add(ConversionScreen.text("job_not_retained").getString());
        text.add(ConversionScreen.text("risk_not_percentage").getString());
        text.add(ConversionScreen.text("restart_manual").getString());
        return List.copyOf(text);
    }

    private static void addPath(List<String> text, String label, Optional<Path> path) { path.ifPresent(value -> text.add(label + ": " + safe(value.toString()))); }
    private static String safe(String text) { return ConversionScreen.safe(text); }

    @Override public void resize(Minecraft minecraft, int width, int height) {
        focusIndex = children().indexOf(getFocused());
        super.resize(minecraft, width, height);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        graphics.fill(8, 26, width - 8, height - 59, 0x88000000);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, font.plainSubstrByWidth(notice.isBlank() ? ConversionScreen.text("details_help").getString() : safe(notice),
                width - 16), 8, height - 8, 0xBBBBBB);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (getFocused() == lines) {
            if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
                lines.setScrollAmount(lines.getScrollAmount() + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * (height - 100));
                return true;
            }
            if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
                lines.setScrollAmount(key == GLFW.GLFW_KEY_HOME ? 0 : lines.getMaxScroll());
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void onClose() { returning = true; owner.returnFromChild(); }
    @Override public void removed() { if (!returning) owner.closeSession(); }

    @OnlyIn(Dist.CLIENT)
    private final class DetailList extends ObjectSelectionList<DetailLine> {
        private DetailList() {
            super(JobDetailsScreen.this.minecraft, JobDetailsScreen.this.width - 16, JobDetailsScreen.this.height,
                    26, JobDetailsScreen.this.height - 59, 12);
            setRenderBackground(false);
            setRenderTopAndBottom(false);
        }
        @Override public int getRowWidth() { return width - 14; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
        private void update(List<String> paragraphs) {
            double scroll = getScrollAmount();
            int selectedIndex = children().indexOf(getSelected());
            boolean hadFocus = getFocused() != null;
            setFocused(null);
            clearEntries();
            setSelected(null);
            int count = 0;
            for (String paragraph : paragraphs) {
                // Split using the native font; store bounded literal line text for narration.
                String remaining = safe(paragraph);
                while (!remaining.isEmpty() && count < 256) {
                    String line = font.plainSubstrByWidth(remaining, Math.max(30, getRowWidth() - 10));
                    if (line.isEmpty()) break;
                    addEntry(new DetailLine(line));
                    remaining = remaining.substring(line.length());
                    count++;
                }
                if (count >= 256) break;
            }
            if (selectedIndex >= 0 && !children().isEmpty()) {
                DetailLine selection = children().get(Math.min(selectedIndex, children().size() - 1));
                setSelected(selection);
                if (hadFocus) setFocused(selection);
            }
            setScrollAmount(scroll);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private final class DetailLine extends ObjectSelectionList.Entry<DetailLine> {
        private final String text;
        private DetailLine(String text) { this.text = text; }
        @Override public Component getNarration() { return Component.literal(text); }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int rowWidth, int rowHeight,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(font, text, x + 3, y + 2, 0xDDDDDD);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) return false;
            lines.setFocused(this);
            return true;
        }
    }
}
