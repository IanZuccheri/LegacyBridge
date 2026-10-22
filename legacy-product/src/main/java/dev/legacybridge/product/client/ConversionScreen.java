package dev.legacybridge.product.client;

import dev.legacybridge.converter.service.InventoryResult;
import dev.legacybridge.converter.service.EngineError;
import dev.legacybridge.product.controller.ConversionController;
import dev.legacybridge.product.controller.InventoryKind;
import dev.legacybridge.product.controller.JobPathKind;
import dev.legacybridge.product.controller.JobState;
import dev.legacybridge.product.controller.JobView;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/** Physical-client presentation. Filesystem, conversion, reports and installation stay in the controller. */
@OnlyIn(Dist.CLIENT)
public final class ConversionScreen extends Screen {
    private enum Tab { INPUT, JOBS }
    private final Screen parent;
    private final ConversionController controller;
    private final Set<String> checked = new LinkedHashSet<>();
    private final Map<String, Button> buttons = new HashMap<>();
    private final Map<GuiEventListener, String> focusNames = new HashMap<>();
    private EditBox mappingPath;
    private EditBox search;
    private ConversionEntryList rows;
    private Tab tab = Tab.INPUT;
    private String mappingValue = "";
    private String searchValue = "";
    private String focusName = "search";
    private String rowSignature = "";
    private String notice = "";
    private double inputScroll;
    private double jobsScroll;
    private int mappingCursor;
    private int searchCursor;
    private boolean started;
    private boolean childNavigation;
    private TransactionScreen transactions;
    private HistoryScreen history;

    public ConversionScreen(Screen parent, Path gameDir) {
        super(Component.translatable("legacybridge.screen.title"));
        this.parent = parent;
        controller = new ConversionController(gameDir);
    }

    public boolean isStopped() { return controller.isStopped(); }
    ConversionController controller() { return controller; }

    @Override protected void init() {
        if (mappingPath != null) { mappingValue = mappingPath.getValue(); mappingCursor = mappingPath.getCursorPosition(); }
        if (search != null) { searchValue = search.getValue(); searchCursor = search.getCursorPosition(); }
        if (rows != null) rememberScroll();
        buttons.clear();
        focusNames.clear();
        int left = 8;
        int content = width - 16;
        int tabWidth = (content - 12) / 4;
        button("tab_input", left, 29, tabWidth, () -> changeTab(Tab.INPUT));
        button("tab_jobs", left + tabWidth + 4, 29, tabWidth, () -> changeTab(Tab.JOBS)).setMessage(text("tab_jobs_compact"));
        buttons.get("tab_jobs").setTooltip(Tooltip.create(text("tab_jobs")));
        button("history", left + (tabWidth + 4) * 2, 29, tabWidth, () -> {
            if (history == null) history = new HistoryScreen(this);
            showChild(history);
        }).setTooltip(Tooltip.create(HistoryScreen.text("details_warning")));
        button("refresh", left + (tabWidth + 4) * 3, 29, tabWidth, () -> {
            notice = "";
            if (controller.refresh()) rowSignature = "";
        });
        search = addRenderableWidget(new EditBox(font, left, 53, content, 18, text("search")));
        search.setMaxLength(128);
        search.setValue(searchValue);
        search.setCursorPosition(searchCursor);
        search.setHint(text("search_hint"));
        search.setResponder(value -> { searchValue = value; rowSignature = ""; if (rows != null) rows.setScrollAmount(0); });
        focusNames.put(search, "search");
        mappingPath = addRenderableWidget(new EditBox(font, left, 76, content - 168, 18, text("mapping")));
        button("composition_preview", left + content - 164, 75, 88,
                () -> showChild(new CompositionScreen(this, mappingValue)))
                .setTooltip(Tooltip.create(CompositionScreen.text("warning")));
        button("cohort_preview", left + content - 72, 75, 72,
                () -> showChild(new CohortScreen(this, cohortSelection(controller.snapshot()), mappingValue)))
                .setTooltip(Tooltip.create(CohortScreen.text("selection_hint")));
        mappingPath.setMaxLength(4096);
        mappingPath.setValue(mappingValue);
        mappingPath.setCursorPosition(mappingCursor);
        mappingPath.setHint(text("mapping_hint"));
        mappingPath.setTooltip(Tooltip.create(text("mapping_details")));
        mappingPath.setResponder(value -> mappingValue = value);
        focusNames.put(mappingPath, "mapping");
        rows = addRenderableWidget(new ConversionEntryList(minecraft, content, height, 110, height - 62));
        rows.setLeftPos(left);
        focusNames.put(rows, "rows");
        int fourth = (content - 12) / 4;
        button("analyze", left, height - 56, fourth, () -> {
            notice = "";
            if (controller.analyzeSelected()) changeTab(Tab.JOBS);
        });
        button("convert", left + fourth + 4, height - 56, fourth, this::enqueueChecked);
        button("details", left + (fourth + 4) * 2, height - 56, fourth, this::showDetails);
        button("apply", left + (fourth + 4) * 3, height - 56, fourth, this::confirmApply);
        button("open_input", left, height - 30, fourth, this::openInput);
        button("cancel", left + fourth + 4, height - 30, fourth, () -> {
            Optional<JobView> job = controller.selectedJob();
            boolean accepted = job.isPresent() ? controller.cancelJob(job.orElseThrow().id()) : controller.cancel();
            notice = text(accepted ? "cancel_requested" : "cancel_unavailable").getString();
        });
        button("clear_finished", left + (fourth + 4) * 2, height - 30, fourth, () -> {
            controller.clearFinished();
            rowSignature = "";
        }).setTooltip(Tooltip.create(text("clear_finished_hint")));
        Button back = addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left + (fourth + 4) * 3, height - 30, fourth, 20).build());
        focusNames.put(back, "back");
        if (!started) { started = true; controller.refresh(); }
        rowSignature = "";
        update();
        rows.setScrollAmount(tab == Tab.INPUT ? inputScroll : jobsScroll);
        focusNames.entrySet().stream().filter(entry -> entry.getValue().equals(focusName)).findFirst()
                .ifPresent(entry -> setInitialFocus(entry.getKey()));
    }

    private Button button(String key, int x, int y, int width, Runnable action) {
        Button button = addRenderableWidget(Button.builder(text(key), pressed -> action.run()).bounds(x, y, width, 20).build());
        buttons.put(key, button);
        focusNames.put(button, key);
        return button;
    }

    private void changeTab(Tab next) {
        if (tab == next) return;
        rememberScroll();
        tab = next;
        rowSignature = "";
        update();
        rows.setScrollAmount(tab == Tab.INPUT ? inputScroll : jobsScroll);
    }

    private void rememberScroll() {
        if (tab == Tab.INPUT) inputScroll = rows.getScrollAmount(); else jobsScroll = rows.getScrollAmount();
    }

    @Override public void resize(Minecraft minecraft, int width, int height) {
        focusName = focusNames.getOrDefault(getFocused(), focusName);
        super.resize(minecraft, width, height);
    }

    @Override public void tick() {
        search.tick();
        mappingPath.tick();
        focusName = focusNames.getOrDefault(getFocused(), focusName);
        update();
    }

    private void update() {
        ConversionController.View view = controller.snapshot();
        List<JobView> jobs = controller.jobs();
        Set<String> present = new LinkedHashSet<>();
        view.entries().stream().filter(entry -> ConversionController.classify(entry) == InventoryKind.CANDIDATE)
                .forEach(entry -> present.add(inputKey(entry)));
        checked.retainAll(present);
        String signature = tab + "|" + searchValue + "|" + checked + "|" + view.selected() + "|"
                + view.entries().stream().map(entry -> inputKey(entry) + ConversionController.classify(entry)).toList()
                + jobs.stream().map(job -> job.id() + ":" + job.status()).toList()
                + controller.selectedJob().map(job -> job.id().toString()).orElse("");
        if (!signature.equals(rowSignature)) {
            rowSignature = signature;
            List<ConversionEntryList.RowModel> models = new ArrayList<>();
            String selectedKey;
            if (tab == Tab.INPUT) {
                selectedKey = view.selection().map(ConversionScreen::inputKey).orElse("");
                for (int index = 0; index < view.entries().size(); index++) {
                    InventoryResult.Entry entry = view.entries().get(index);
                    if (!matches(entry)) continue;
                    int observedIndex = index;
                    InventoryKind kind = ConversionController.classify(entry);
                    String key = inputKey(entry);
                    String names = entry.mods().stream().limit(4).map(mod -> safe(mod.displayName())).filter(name -> !name.isBlank())
                            .reduce((first, second) -> first + ", " + second).orElse("");
                    String title = safe(entry.path().getFileName().toString() + (names.isBlank() ? "" : " | " + names));
                    String source = entry.sourceProfile() + " | " + entry.fingerprint().map(value -> value.sizeBytes() + " bytes")
                            .orElse(text("size_unknown").getString());
                    String hash = entry.fingerprint().map(value -> "SHA-256: " + value.sha256()).orElse(text("hash_unknown").getString());
                    models.add(new ConversionEntryList.RowModel(key, title,
                            identities(entry) + " | " + inventoryText(kind).getString(), source, hash, true, checked.contains(key),
                            kind == InventoryKind.CANDIDATE, kind == InventoryKind.CANDIDATE ? 0xD6EDFF : 0xE8C59D,
                            () -> controller.select(observedIndex), () -> toggle(entry)));
                }
            } else {
                selectedKey = controller.selectedJob().map(job -> job.id().toString()).orElse("");
                for (JobView job : jobs) {
                    if (!matches(job)) continue;
                    models.add(new ConversionEntryList.RowModel(job.id().toString(), safe(job.displayName()),
                            Component.translatable(job.summaryKey()).getString() + " | " + job.id().toString().substring(0, 8),
                            safe(job.input().getFileName().toString()), job.error().map(error -> error.code())
                                    .orElse(job.installed().isPresent() ? "PENDING_RESTART" : job.artifact().isPresent() ? "STAGED" : ""),
                            false, false, true, job.error().isPresent() ? 0xFFD2A0 : 0xD6EDFF,
                            () -> controller.selectJob(job.id()), () -> showJobDetails(job.id())));
                }
            }
            rows.rows(models, selectedKey);
        }
        Optional<JobView> selected = controller.selectedJob();
        buttons.get("tab_input").active = tab != Tab.INPUT;
        buttons.get("tab_jobs").active = tab != Tab.JOBS;
        buttons.get("refresh").active = !view.busy();
        buttons.get("history").active = !controller.isInstalling();
        buttons.get("composition_preview").active = tab == Tab.INPUT && !view.busy()
                && view.selection().filter(entry -> matches(entry) && ConversionController.classify(entry) == InventoryKind.CANDIDATE).isPresent();
        int cohortSize = cohortSelection(view).size();
        buttons.get("cohort_preview").active = tab == Tab.INPUT && !view.busy() && cohortSize > 0
                && cohortSize <= dev.legacybridge.product.controller.CohortInspectionView.MAX_INPUTS;
        buttons.get("analyze").active = tab == Tab.INPUT && !view.busy() && view.selection()
                .filter(entry -> matches(entry) && entry.fingerprint().isPresent() && entry.error().isEmpty()).isPresent();
        buttons.get("convert").active = tab == Tab.INPUT && jobs.size() < 32 && !conversionSelection(view).isEmpty();
        buttons.get("details").active = controller.globalError().isPresent()
                || (tab == Tab.INPUT ? view.selection().filter(this::matches).isPresent() : selected.filter(this::matches).isPresent());
        // Recovery must remain reachable after reopening, even with no retained conversion jobs.
        buttons.get("apply").active = !view.busy();
        buttons.get("apply").setTooltip(Tooltip.create(TransactionScreen.text("entry_hint")));
        buttons.get("cancel").active = selected.map(job -> job.status() == JobState.QUEUED
                || (job.status() == JobState.ANALYZING || job.status() == JobState.CONVERTING) && controller.canCancel())
                .orElse(view.busy() && controller.canCancel());
        buttons.get("clear_finished").active = jobs.stream().anyMatch(job -> terminal(job.status()));
    }

    private void toggle(InventoryResult.Entry entry) {
        if (ConversionController.classify(entry) != InventoryKind.CANDIDATE) return;
        String key = inputKey(entry);
        if (!checked.remove(key)) {
            if (checked.size() == 32) { notice = text("selection_limit").getString(); return; }
            checked.add(key);
        }
        rowSignature = "";
    }

    private List<Integer> conversionSelection(ConversionController.View view) {
        List<Integer> selected = new ArrayList<>();
        for (int index = 0; index < view.entries().size(); index++) {
            InventoryResult.Entry entry = view.entries().get(index);
            if (ConversionController.classify(entry) == InventoryKind.CANDIDATE && checked.contains(inputKey(entry))) selected.add(index);
        }
        if (selected.isEmpty() && checked.isEmpty() && view.selection()
                .filter(entry -> matches(entry) && ConversionController.classify(entry) == InventoryKind.CANDIDATE).isPresent()) selected.add(view.selected());
        return selected;
    }

    /** Explicit checked rows only, including rows hidden by search. The child reviews every row before work starts. */
    private List<InventoryResult.Entry> cohortSelection(ConversionController.View view) {
        return view.entries().stream().filter(entry -> checked.contains(inputKey(entry))).toList();
    }

    private void enqueueChecked() {
        List<Integer> indices = conversionSelection(controller.snapshot());
        if (controller.enqueue(indices, mappingValue)) {
            checked.clear();
            notice = "";
            changeTab(Tab.JOBS);
        } else notice = text("queue_refused").getString();
    }

    private void showDetails() {
        if (controller.globalError().isPresent()) showChild(new JobDetailsScreen(this, Optional.empty(), Optional.empty()));
        else if (tab == Tab.JOBS) controller.selectedJob().ifPresent(job -> showJobDetails(job.id()));
        else controller.snapshot().selection().ifPresent(entry -> showChild(new JobDetailsScreen(this, Optional.empty(), Optional.of(entry))));
    }

    private void showJobDetails(UUID id) { showChild(new JobDetailsScreen(this, Optional.of(id), Optional.empty())); }

    private void confirmApply() {
        notice = "";
        if (transactions == null) transactions = new TransactionScreen(this);
        showChild(transactions);
    }

    void showChild(Screen child) {
        childNavigation = true;
        minecraft.setScreen(child);
    }

    void returnFromChild() {
        childNavigation = false;
        minecraft.setScreen(this);
    }

    void closeSession() { controller.close(); }

    boolean owns(Screen screen) {
        return screen == this || screen instanceof JobDetailsScreen details && details.isOwnedBy(this)
                || screen instanceof CompositionScreen composition && composition.isOwnedBy(this)
                || screen instanceof CohortScreen cohort && cohort.isOwnedBy(this)
                || history != null && history.owns(screen)
                || transactions != null && transactions.owns(screen);
    }

    private void openInput() {
        if (!controller.openInput(path -> Util.getPlatform().openFile(path.toFile()), outcome -> minecraft.execute(() -> {
            notice = outcome.error().map(error -> error.code() + ": " + safe(error.message()))
                    .orElse(text("open_requested").getString());
        }))) notice = text("open_unavailable").getString();
    }

    void openJobPath(UUID jobId, JobPathKind kind, java.util.function.Consumer<String> status) {
        if (!controller.openJobPath(jobId, kind, path -> Util.getPlatform().openFile(path.toFile()), outcome -> minecraft.execute(() ->
                status.accept(outcome.error().map(error -> error.code() + ": " + safe(error.message()))
                        .orElse(text("open_requested").getString()))))) status.accept(text("open_unavailable").getString());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        ConversionController.View view = controller.snapshot();
        graphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF);
        graphics.drawCenteredString(font, fit(text("input_short").getString(), width - 16), width / 2, 17, 0xAAAAAA);
        String summary = tab == Tab.INPUT ? Component.translatable("legacybridge.screen.inventory_count", view.entries().size(), checked.size()).getString()
                : Component.translatable("legacybridge.screen.jobs_count", controller.jobs().size()).getString();
        if (view.inventoryTruncated() && tab == Tab.INPUT) summary += " | " + text("inventory_truncated").getString();
        graphics.drawString(font, fit(summary, width - 16), 8, 98, 0xCCCCCC);
        graphics.fill(8, 110, width - 8, height - 62, 0x77000000);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (rows.children().isEmpty()) graphics.drawCenteredString(font, fit(text("no_matches").getString(), width - 30), width / 2, 119, 0xAAAAAA);
        Optional<EngineError> globalError = controller.globalError();
        Optional<JobView> selectedJob = controller.selectedJob();
        String status = globalError.map(error -> "SCREEN ERROR: " + error.code() + ": " + safe(error.message())).orElse(notice);
        if (status.isBlank()) status = selectedJob.map(job -> Component.translatable(job.summaryKey()).getString()
                + job.error().map(error -> " | " + error.code() + ": " + safe(error.message()))
                        .orElseGet(() -> job.progress().map(progress -> " | " + progress.stage() + " " + progress.completed()
                                + (progress.total() < 0 ? "/?" : "/" + progress.total())).orElse("")))
                .orElseGet(() -> view.progress().map(progress -> progress.stage() + " " + progress.completed()
                        + (progress.total() < 0 ? "/?" : "/" + progress.total()) + " | " + safe(progress.detail()))
                        .orElse(text("selection_help").getString()));
        graphics.drawString(font, fit(status, width - 16), 8, height - 9,
                globalError.isPresent() || selectedJob.flatMap(JobView::error).isPresent() ? 0xFFD2A0 : 0xAAAAAA);
        if (mouseY >= rows.getTop() && mouseY <= rows.getBottom())
            rows.hoveredDescription().ifPresent(description -> graphics.renderTooltip(font, description, mouseX, mouseY));
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (tab == Tab.INPUT && getFocused() == rows && hasControlDown() && key == GLFW.GLFW_KEY_A) {
            checked.clear();
            for (InventoryResult.Entry entry : controller.snapshot().entries())
                if (checked.size() < 32 && matches(entry) && ConversionController.classify(entry) == InventoryKind.CANDIDATE) checked.add(inputKey(entry));
            rowSignature = "";
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void onClose() {
        if (controller.isInstalling()) { notice = TransactionScreen.text("busy").getString(); return; }
        controller.close();
        minecraft.setScreen(parent);
    }
    @Override public void removed() { if (!childNavigation) controller.close(); }

    private boolean matches(InventoryResult.Entry entry) {
        String query = searchValue.strip().toLowerCase(Locale.ROOT);
        return query.isEmpty() || (entry.path().getFileName() + " " + identities(entry) + " "
                + entry.mods().stream().map(mod -> mod.displayName()).toList()).toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matches(JobView job) {
        String query = searchValue.strip().toLowerCase(Locale.ROOT);
        return query.isEmpty() || (job.displayName() + " " + job.input().getFileName() + " " + job.status()).toLowerCase(Locale.ROOT).contains(query);
    }

    private static String inputKey(InventoryResult.Entry entry) { return entry.path() + "|" + entry.fingerprint().map(value -> value.sha256()).orElse("invalid"); }
    static String identities(InventoryResult.Entry entry) {
        return safe(entry.mods().stream().limit(4).map(mod -> mod.modId() + " " + mod.version()).reduce((a, b) -> a + ", " + b).orElse("?"));
    }
    static Component inventoryText(InventoryKind kind) { return Component.translatable("legacybridge.inventory." + kind.name().toLowerCase(Locale.ROOT)); }
    static Component text(String key) { return Component.translatable("legacybridge.screen." + key); }
    static String safe(String value) { return ConversionController.safeText(value); }
    private String fit(String value, int available) { return font.plainSubstrByWidth(safe(value), Math.max(20, available)); }
    private static boolean terminal(JobState state) {
        return state == JobState.ANALYZED || state == JobState.CONVERTED || state == JobState.BLOCKED || state == JobState.FAILED
                || state == JobState.CANCELLED || state == JobState.PENDING_RESTART;
    }

}
