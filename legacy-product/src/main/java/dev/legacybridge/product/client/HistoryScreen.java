package dev.legacybridge.product.client;

import dev.legacybridge.product.history.HistoryPresentation;
import dev.legacybridge.product.history.JobHistory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Read-only persisted receipts, deliberately separate from the actionable session queue. */
@OnlyIn(Dist.CLIENT)
public final class HistoryScreen extends Screen {
    private final ConversionScreen owner;
    private JobHistory snapshot = JobHistory.notRead();
    private EditBox search;
    private ConversionEntryList rows;
    private Button refresh, cancel, details, reports;
    private String query = "", notice = "";
    private UUID selected;
    private int cursor, focusIndex;
    private boolean initialized, dirty, reading, navigating;

    HistoryScreen(ConversionScreen owner) { super(text("title")); this.owner = owner; }

    @Override protected void init() {
        navigating = false;
        double scroll = rows == null ? 0 : rows.getScrollAmount();
        if (search != null) { query = search.getValue(); cursor = search.getCursorPosition(); }
        search = addRenderableWidget(new EditBox(font, 8, 42, width - 16, 18, text("search")));
        search.setMaxLength(128);
        search.setValue(query);
        search.setCursorPosition(cursor);
        search.setHint(text("search"));
        search.setResponder(value -> { query = value; dirty = true; if (rows != null) rows.setScrollAmount(0); });
        int third = (width - 24) / 3;
        refresh = addRenderableWidget(Button.builder(ConversionScreen.text("refresh"), button -> refresh())
                .bounds(8, 65, third, 20).build());
        cancel = addRenderableWidget(Button.builder(ConversionScreen.text("cancel"), button -> {
            notice = ConversionScreen.text(owner.controller().cancel() ? "cancel_requested" : "cancel_unavailable").getString();
        }).bounds(12 + third, 65, third, 20).build());
        addRenderableWidget(Button.builder(text("diagnostics"), button -> openDetails(Optional.empty()))
                .bounds(16 + third * 2, 65, third, 20).build());
        rows = addRenderableWidget(new ConversionEntryList(minecraft, width - 16, height, 101, height - 37));
        rows.setLeftPos(8);
        details = addRenderableWidget(Button.builder(ConversionScreen.text("details"), button -> openDetails(selectedJob()))
                .bounds(8, height - 30, third, 20).build());
        reports = addRenderableWidget(Button.builder(Component.translatable("legacybridge.saved_reports.title"), button -> {
            selectedJob().ifPresent(job -> {
                navigating = true; minecraft.setScreen(new SavedReportScreen(this, job.jobId()));
            });
        }).bounds(12 + third, height - 30, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(16 + third * 2, height - 30, third, 20).build());
        dirty = true;
        if (!initialized) { initialized = true; refresh(); }
        update();
        rows.setScrollAmount(scroll);
        setInitialFocus(children().get(Math.max(0, Math.min(focusIndex, children().size() - 1))));
    }

    private void refresh() {
        reading = owner.controller().refreshHistory();
        notice = reading ? "" : text("busy").getString();
    }

    @Override public void tick() { search.tick(); update(); }

    private void update() {
        JobHistory observed = owner.controller().history();
        if (snapshot != observed) { snapshot = observed; dirty = true; }
        var view = owner.controller().snapshot();
        boolean busy = view.busy();
        if (reading && !busy) {
            if (view.phase() == dev.legacybridge.product.controller.ConversionController.Phase.CANCELLED)
                notice = text("cancelled").getString();
            else notice = owner.controller().globalError().map(error -> text("failed", error.code(),
                    HistoryPresentation.safe(error.message())).getString()).orElse("");
            reading = false;
        }
        refresh.active = !busy;
        cancel.active = reading && owner.controller().canCancel();
        if (dirty) {
            dirty = false;
            List<JobHistory.Job> visible = HistoryPresentation.search(snapshot, query);
            if (visible.stream().noneMatch(job -> job.jobId().equals(selected))) selected = null;
            List<ConversionEntryList.RowModel> models = new ArrayList<>();
            for (JobHistory.Job job : visible) {
                String input = job.receipts().isEmpty() ? "—" : HistoryPresentation.safe(job.receipts().get(0).input());
                String state = job.highestRecorded().map(receipt -> text("recorded", receipt.recordedState()).getString())
                        .orElse(text("no_latest").getString());
                models.add(new ConversionEntryList.RowModel(job.jobId().toString(), input,
                        job.jobId().toString(), text("row", job.receipts().size(), job.ordering()).getString(), state,
                        false, false, true, 0xD6EDFF, () -> selected = job.jobId(), () -> openDetails(Optional.of(job))));
            }
            rows.rows(models, selected == null ? "" : selected.toString());
        }
        details.active = selectedJob().filter(job -> !job.receipts().isEmpty()).isPresent();
        reports.active = !busy && snapshot.status() == JobHistory.Status.COMPLETE
                && selectedJob().flatMap(JobHistory.Job::highestRecorded).isPresent();
    }

    private Optional<JobHistory.Job> selectedJob() { return snapshot.jobs().stream().filter(job -> job.jobId().equals(selected)).findFirst(); }

    private void openDetails(Optional<JobHistory.Job> job) {
        navigating = true;
        minecraft.setScreen(new HistoryDetailsScreen(this, snapshot, job));
    }
    void returnFromDetails() { navigating = false; minecraft.setScreen(this); }
    void closeSession() { owner.closeSession(); }
    dev.legacybridge.product.controller.ConversionController controller() { return owner.controller(); }
    boolean owns(Screen screen) { return screen == this || screen instanceof HistoryDetailsScreen child && child.isOwnedBy(this)
            || screen instanceof SavedReportScreen report && report.isOwnedBy(this); }

    @Override public void resize(Minecraft minecraft, int width, int height) {
        focusIndex = children().indexOf(getFocused());
        super.resize(minecraft, width, height);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        graphics.drawCenteredString(font, fit(text("warning").getString()), width / 2, 20, 0xFFD2A0);
        graphics.drawCenteredString(font, fit(text("inert_paths").getString()), width / 2, 31, 0xAAAAAA);
        String status = reading ? text("reading").getString() : text("scan", snapshot.status(), snapshot.jobs().size()).getString();
        if (!notice.isEmpty()) status += " | " + notice;
        graphics.drawString(font, fit(status), 8, 89, 0xCCCCCC);
        refresh.setTooltip(Tooltip.create(Component.literal(status)));
        graphics.fill(8, 101, width - 8, height - 37, 0x77000000);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (rows.children().isEmpty()) graphics.drawCenteredString(font, fit(text("empty").getString()), width / 2, 109, 0xAAAAAA);
        if (mouseY >= rows.getTop() && mouseY <= rows.getBottom())
            rows.hoveredDescription().ifPresent(value -> graphics.renderTooltip(font, value, mouseX, mouseY));
    }

    private String fit(String value) { return font.plainSubstrByWidth(value, Math.max(20, width - 16)); }
    static Component text(String key, Object... args) { return Component.translatable("legacybridge.history." + key, args); }
    @Override public void onClose() { navigating = true; owner.returnFromChild(); }
    @Override public void removed() {
        focusIndex = children().indexOf(getFocused());
        if (!navigating) owner.closeSession();
    }
}
