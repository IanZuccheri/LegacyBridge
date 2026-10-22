package dev.legacybridge.product.client;

import dev.legacybridge.converter.service.EngineError;
import dev.legacybridge.product.controller.ConversionController;
import dev.legacybridge.product.controller.JobState;
import dev.legacybridge.product.controller.JobView;
import dev.legacybridge.product.install.InstallSession;
import dev.legacybridge.product.install.RecoveryAction;
import dev.legacybridge.product.install.TransactionResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only transaction presentation. All paths shown here originate in typed controller
 * snapshots; the screen never reads a journal, hashes a file, or mutates the filesystem.
 * Closing through the launcher is manual, not an automatic restart provider.
 */
@OnlyIn(Dist.CLIENT)
public final class TransactionScreen extends Screen {
    private enum Mode { JOBS, TRANSACTIONS, DETAILS }
    private final ConversionScreen owner;
    private final Map<UUID, Optional<Path>> approvals = new LinkedHashMap<>();
    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private final Map<Mode, Double> scroll = new LinkedHashMap<>();
    private Mode mode = Mode.JOBS;
    private Mode detailSource = Mode.JOBS;
    private EntryList rows;
    private UUID selectedJob;
    private Path selectedTransaction;
    private UUID closeOperation;
    private boolean stopRequested;
    private boolean started;
    private boolean returning;
    private boolean childNavigation;
    private TransactionConfirmation confirmation;
    private String notice = "";
    private String signature = "";
    private String focusName = "rows";

    TransactionScreen(ConversionScreen owner) {
        super(text("title"));
        this.owner = owner;
        owner.controller().selectedJob().filter(TransactionScreen::eligible).ifPresent(job -> selectedJob = job.id());
    }

    boolean owns(Screen screen) { return screen != null && (screen == this || screen == confirmation); }
    private ConversionController controller() { return owner.controller(); }
    static Component text(String key) { return Component.translatable("legacybridge.install." + key); }
    private static String safe(String value) { return ConversionScreen.safe(value); }

    /** No filesystem calls: these are actual loaded Forge mod files, not input/report metadata. */
    private InstallSession session() {
        var list = ModList.get();
        Set<String> ids = new LinkedHashSet<>();
        list.getMods().forEach(mod -> ids.add(mod.getModId()));
        Set<Path> paths = new LinkedHashSet<>();
        list.getModFiles().forEach(info -> paths.add(info.getFile().getFilePath().toAbsolutePath().normalize()));
        return new InstallSession(ids, paths, !worldClosed());
    }

    private boolean worldClosed() {
        return minecraft.level == null && minecraft.getSingleplayerServer() == null && minecraft.getConnection() == null;
    }

    @Override protected void init() {
        returning = false;
        buttons.clear();
        int area = width - 16;
        int third = (area - 8) / 3;
        addButton("jobs", 8, 33, third, () -> changeMode(Mode.JOBS));
        addButton("transactions", 12 + third, 33, third, () -> changeMode(Mode.TRANSACTIONS));
        addButton("refresh", 16 + third * 2, 33, third, this::refresh);
        rows = addRenderableWidget(new EntryList());
        rows.setLeftPos(8);
        if (mode != Mode.DETAILS) {
            addButton("details", 8, height - 106, third, this::showDetails);
            addButton("associate", 12 + third, height - 106, third, this::associateUpdate)
                    .setTooltip(Tooltip.create(text("associate_hint")));
            addButton("clear_updates", 16 + third * 2, height - 106, third, () -> {
                approvals.replaceAll((id, prior) -> Optional.empty());
                notice = text("updates_cleared").getString();
                signature = "";
            });
            addButton("apply", 8, height - 82, third, () -> confirmApply(false));
            addButton("resume", 12 + third, height - 82, third, () -> confirmRecovery(RecoveryAction.RESUME));
            addButton("restore", 16 + third * 2, height - 82, third, () -> confirmRecovery(RecoveryAction.RESTORE));
            addButton("apply_close", 8, height - 58, area, () -> confirmApply(true))
                    .setTooltip(Tooltip.create(text("apply_close_hint")));
        }
        addButton("copy_details", 8, height - 34, (area - 4) / 2,
                () -> minecraft.keyboardHandler.setClipboard(String.join("\n", details())));
        Button back = addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(12 + (area - 4) / 2, height - 34, (area - 4) / 2, 20).build());
        buttons.put("back", back);
        if (!started) { started = true; refresh(); }
        signature = "";
        update();
        rows.setScrollAmount(scroll.getOrDefault(mode, 0.0));
        setInitialFocus(focusName.equals("rows") || !buttons.containsKey(focusName) ? rows : buttons.get(focusName));
    }

    private Button addButton(String key, int x, int y, int width, Runnable action) {
        Button button = addRenderableWidget(Button.builder(text(key), ignored -> action.run()).bounds(x, y, width, 20).build());
        buttons.put(key, button);
        return button;
    }

    private void refresh() {
        if (!worldClosed()) { notice = text("world_open").getString(); return; }
        notice = controller().refreshTransactions(session()) ? "" : text("request_refused").getString();
    }

    private void rememberFocus() {
        if (getFocused() == rows) focusName = "rows";
        else buttons.entrySet().stream().filter(entry -> entry.getValue() == getFocused()).findFirst()
                .ifPresent(entry -> focusName = entry.getKey());
    }

    private void changeMode(Mode next) {
        if (mode == next) return;
        scroll.put(mode, rows.getScrollAmount());
        rememberFocus();
        mode = next;
        rebuildWidgets();
    }

    @Override public void resize(Minecraft minecraft, int width, int height) {
        if (rows != null) scroll.put(mode, rows.getScrollAmount());
        rememberFocus();
        super.resize(minecraft, width, height);
    }

    @Override public void tick() {
        update();
        var installation = controller().installation();
        if (closeOperation == null || installation.busy() || controller().snapshot().busy()) return;
        UUID expected = closeOperation;
        closeOperation = null;
        if (installation.operationId().filter(expected::equals).isEmpty() || installation.error().isPresent()
                || installation.result().filter(TransactionResult::safeToClose).isEmpty()) {
            notice = text("close_refused").getString();
            return;
        }
        if (!worldClosed()) { notice = text("world_open").getString(); return; }
        if (!stopRequested) {
            stopRequested = true;
            owner.closeSession();
            minecraft.stop();
        }
    }

    private void update() {
        var installation = controller().installation();
        List<JobView> jobs = controller().jobs();
        Set<UUID> available = new LinkedHashSet<>();
        jobs.stream().filter(TransactionScreen::eligible).forEach(job -> available.add(job.id()));
        // Keep the confirmed selection while its entries move through APPLYING.
        if (!installation.busy()) approvals.keySet().retainAll(available);
        if (selectedJob == null) jobs.stream().filter(TransactionScreen::eligible).findFirst().ifPresent(job -> selectedJob = job.id());
        if (selectedTransaction == null) installation.result().ifPresent(result -> selectedTransaction = result.transaction());
        String current = mode + "|" + approvals + "|" + selectedJob + "|" + selectedTransaction + "|"
                + jobs + "|" + installation + "|" + controller().globalError();
        if (!current.equals(signature)) {
            signature = current;
            List<RowData> data = new ArrayList<>();
            String selected = "";
            if (mode == Mode.JOBS) {
                selected = selectedJob == null ? "" : selectedJob.toString();
                for (JobView job : jobs) {
                    boolean enabled = eligible(job) && !installation.busy();
                    data.add(new RowData(job.id().toString(), List.of(safe(job.displayName()),
                            Component.translatable(job.summaryKey()).getString(),
                            approvals.getOrDefault(job.id(), Optional.empty()).map(path -> "UPDATE: " + path.getFileName())
                                    .orElse("ADD | " + job.id().toString().substring(0, 8)),
                            safe(job.artifact().map(Path::toString).orElseGet(() -> job.error().map(EngineError::code).orElse("")))),
                            true, approvals.containsKey(job.id()), enabled,
                            () -> selectedJob = job.id(), () -> toggle(job)));
                }
            } else if (mode == Mode.TRANSACTIONS) {
                selected = selectedTransaction == null ? "" : selectedTransaction.toString();
                for (TransactionResult transaction : transactions()) {
                    data.add(new RowData(transaction.transaction().toString(), List.of(transaction.status() + " | " + transaction.transaction().getFileName(),
                            safe(transaction.entries().stream().map(entry -> entry.modId() + " " + entry.version())
                                    .reduce((first, second) -> first + ", " + second).orElse("?")),
                            safe(transaction.transaction().toString()), transaction.error().map(EngineError::code).orElse(text("not_active").getString())),
                            false, false, true, () -> selectedTransaction = transaction.transaction(), this::showDetails));
                }
            } else {
                int lineIndex = 0;
                for (String paragraph : details()) {
                    String remaining = safe(paragraph);
                    while (!remaining.isEmpty() && lineIndex < 256) {
                        String line = font.plainSubstrByWidth(remaining, Math.max(30, rows.getRowWidth() - 12));
                        if (line.isEmpty()) break;
                        data.add(new RowData("line-" + lineIndex++, List.of(line), false, false, true, () -> {}, () -> {}));
                        remaining = remaining.substring(line.length());
                    }
                }
            }
            rows.replace(data, selected);
        }
        boolean idle = !installation.busy() && !controller().snapshot().busy() && closeOperation == null;
        boolean allowed = idle && worldClosed();
        buttons.get("jobs").active = mode != Mode.JOBS;
        buttons.get("transactions").active = mode != Mode.TRANSACTIONS;
        buttons.get("refresh").active = allowed;
        buttons.get("back").active = !controller().isInstalling();
        if (mode != Mode.DETAILS) {
            buttons.get("details").active = selectedResult().isPresent() || selectedJob().isPresent()
                    || installation.error().isPresent() || controller().globalError().isPresent();
            buttons.get("associate").active = allowed && mode == Mode.TRANSACTIONS && approvals.size() == 1
                    && selectedResult().filter(this::replaceable).isPresent();
            buttons.get("clear_updates").active = idle && approvals.values().stream().anyMatch(Optional::isPresent);
            buttons.get("apply").active = allowed && !approvals.isEmpty();
            buttons.get("apply_close").active = allowed && !approvals.isEmpty();
            buttons.get("resume").active = allowed && mode == Mode.TRANSACTIONS && selectedResult().filter(result ->
                    result.status() == TransactionResult.Status.PREPARED || result.status() == TransactionResult.Status.PARTIAL
                            || result.status() == TransactionResult.Status.RECOVERY_REQUIRED).isPresent();
            buttons.get("restore").active = allowed && mode == Mode.TRANSACTIONS && selectedResult().filter(result ->
                    result.status() != TransactionResult.Status.RESTORED && result.status() != TransactionResult.Status.MANUAL).isPresent();
        }
    }

    private List<TransactionResult> transactions() {
        var view = controller().installation();
        Map<Path, TransactionResult> result = new LinkedHashMap<>();
        view.transactions().stream().limit(32).forEach(value -> result.put(value.transaction(), value));
        // A newer disk inspection must not be overwritten by an earlier operation receipt.
        view.result().ifPresent(value -> result.putIfAbsent(value.transaction(), value));
        return result.values().stream().limit(32).toList();
    }

    private Optional<TransactionResult> selectedResult() {
        return transactions().stream().filter(value -> value.transaction().equals(selectedTransaction)).findFirst();
    }

    private Optional<JobView> selectedJob() { return controller().jobs().stream().filter(job -> job.id().equals(selectedJob)).findFirst(); }

    private static boolean eligible(JobView job) {
        return job.status() == JobState.CONVERTED && job.artifact().isPresent() && job.compatibilityReport().isPresent()
                && job.transformationLog().isPresent() && job.conversionReport().isPresent();
    }

    private void toggle(JobView job) {
        if (!eligible(job) || controller().installation().busy()) return;
        if (approvals.remove(job.id()) == null) {
            if (approvals.size() >= 4) { notice = text("selection_limit").getString(); return; }
            approvals.put(job.id(), Optional.empty());
        }
        signature = "";
    }

    private boolean replaceable(TransactionResult result) {
        boolean legacyReceipt = result.status() == TransactionResult.Status.MANUAL
                && result.error().filter(error -> error.code().equals("LEGACY_V1_RECEIPT")).isPresent();
        if ((!legacyReceipt && (result.status() != TransactionResult.Status.COMMITTED || result.error().isPresent()))
                || result.entries().isEmpty() || result.entries().size() > 4) return false;
        InstallSession observed = session();
        return result.entries().stream().allMatch(entry -> entry.state() == TransactionResult.EntryState.INSTALLED
                && entry.installed().isPresent() && entry.error().isEmpty()
                && !observed.loadedModIds().contains(entry.modId())
                && !observed.loadedModFiles().contains(entry.installed().orElseThrow().toAbsolutePath().normalize()));
    }

    private void associateUpdate() {
        if (!worldClosed() || controller().installation().busy() || approvals.size() != 1) { notice = text("request_refused").getString(); return; }
        selectedResult().filter(this::replaceable).ifPresent(prior -> {
            UUID jobId = approvals.keySet().iterator().next();
            JobView job = controller().jobs().stream().filter(value -> value.id().equals(jobId)).findFirst().orElseThrow();
            confirm(text("associate"), Component.translatable("legacybridge.install.associate_warning", shortName(job.displayName()),
                    prior.transaction().getFileName().toString(), prior.entries().stream().map(entry -> shortName(entry.modId()))
                            .reduce((first, second) -> first + ", " + second).orElse("?")), () -> {
                if (!worldClosed() || controller().installation().busy() || !approvals.containsKey(jobId) || !replaceable(prior)) {
                    notice = text("request_refused").getString(); return;
                }
                approvals.put(jobId, Optional.of(prior.transaction()));
                notice = text("update_associated").getString();
                signature = "";
            });
        });
    }

    private void confirmApply(boolean close) {
        if (!worldClosed()) { notice = text("world_open").getString(); return; }
        if (approvals.isEmpty() || approvals.size() > 4 || controller().installation().busy()) { notice = text("request_refused").getString(); return; }
        Map<UUID, Optional<Path>> approved = Collections.unmodifiableMap(new LinkedHashMap<>(approvals));
        String names = controller().jobs().stream().filter(job -> approved.containsKey(job.id()))
                .map(job -> shortName(job.displayName()) + (approved.get(job.id()).isPresent() ? " [UPDATE]" : " [ADD]"))
                .reduce((first, second) -> first + "\n" + second).orElse("?");
        confirm(text(close ? "apply_close" : "apply"), Component.translatable("legacybridge.install.apply_warning", names), () -> {
            if (!worldClosed()) { notice = text("world_open").getString(); return; }
            Optional<UUID> operation = controller().applyJobs(approved, session());
            closeOperation = close ? operation.orElse(null) : null;
            notice = operation.isPresent() ? "" : text("request_refused").getString();
            if (operation.isPresent()) { selectedTransaction = null; changeMode(Mode.TRANSACTIONS); }
        });
    }

    private void confirmRecovery(RecoveryAction action) {
        if (!worldClosed()) { notice = text("world_open").getString(); return; }
        selectedResult().ifPresent(result -> confirm(text(action == RecoveryAction.RESUME ? "resume" : "restore"),
                Component.translatable("legacybridge.install.recovery_warning", action.name(), result.transaction().getFileName().toString()), () -> {
                    if (!worldClosed()) { notice = text("world_open").getString(); return; }
                    closeOperation = null;
                    Optional<UUID> accepted = controller().recoverTransaction(result.transaction(), action, session());
                    notice = accepted.isPresent() ? "" : text("request_refused").getString();
                }));
    }

    private void confirm(Component title, Component warning, Runnable action) {
        childNavigation = true;
        confirmation = new TransactionConfirmation(this, title, warning, action);
        minecraft.setScreen(confirmation);
    }

    private void finishConfirmation(boolean accepted, Runnable action) {
        confirmation.returning = true;
        childNavigation = false;
        minecraft.setScreen(this);
        if (accepted) action.run();
    }

    private void showDetails() {
        detailSource = mode == Mode.DETAILS ? detailSource : mode;
        changeMode(Mode.DETAILS);
    }

    private List<String> details() {
        List<String> result = new ArrayList<>();
        result.add(text("not_active").getString());
        result.add(text("shutdown_limit").getString());
        var installation = controller().installation();
        installation.operationId().ifPresent(id -> result.add("OPERATION: " + id));
        controller().globalError().ifPresent(error -> addError(result, "SCREEN ERROR", error));
        installation.error().ifPresent(error -> addError(result, "INSTALL ERROR", error));
        if ((mode == Mode.DETAILS ? detailSource : mode) == Mode.JOBS) {
            selectedJob().ifPresent(job -> {
                result.add("JOB: " + job.id()); result.add(safe(job.displayName()));
                result.add(Component.translatable(job.summaryKey()).getString());
                result.add("INPUT: " + safe(job.input().toString()));
                job.artifact().ifPresent(path -> result.add("STAGED: " + safe(path.toString())));
                approvals.getOrDefault(job.id(), Optional.empty()).ifPresent(path -> result.add("EXPLICIT REPLACEMENT: " + safe(path.toString())));
                job.error().ifPresent(error -> addError(result, "JOB ERROR", error));
            });
        } else selectedResult().ifPresent(transaction -> {
            result.add("TRANSACTION: " + safe(transaction.transaction().toString()));
            result.add("STATUS: " + transaction.status());
            result.add("SAFE TO CLOSE: " + transaction.safeToClose());
            transaction.error().ifPresent(error -> addError(result, "TRANSACTION ERROR", error));
            transaction.entries().stream().limit(4).forEach(entry -> {
                result.add("FILE " + entry.index() + ": " + safe(entry.modId()) + " " + safe(entry.version()));
                result.add("STATE: " + entry.state());
                result.add("SHA-256: " + safe(entry.sha256()));
                entry.installed().ifPresent(path -> result.add("INSTALLED: " + safe(path.toString())));
                entry.error().ifPresent(error -> addError(result, "FILE ERROR", error));
            });
        });
        return List.copyOf(result);
    }

    private static void addError(List<String> lines, String label, EngineError error) {
        lines.add(label + ": " + safe(error.code())); lines.add(safe(error.message())); lines.add(safe(error.remediation()));
    }

    private static String shortName(String value) {
        String name = safe(value);
        return name.length() <= 42 ? name : name.substring(0, 42) + "...";
    }

    @Override public void onClose() {
        if (controller().isInstalling()) return;
        if (mode == Mode.DETAILS) { changeMode(detailSource); return; }
        closeOperation = null;
        returning = true;
        owner.returnFromChild();
    }

    @Override public void removed() { if (!returning && !childNavigation) owner.closeSession(); }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (getFocused() == rows && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
            rows.setScrollAmount(rows.getScrollAmount() + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * (rows.getBottom() - rows.getTop()));
            return true;
        }
        if (getFocused() == rows && (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END)) {
            rows.setScrollAmount(key == GLFW.GLFW_KEY_HOME ? 0 : rows.getMaxScroll()); return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        String hint = mode == Mode.JOBS ? Component.translatable("legacybridge.install.selection", approvals.size()).getString()
                : mode == Mode.TRANSACTIONS ? text("transactions_hint").getString() : text("details_hint").getString();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(hint, width - 16), width / 2, 20, 0xBBBBBB);
        graphics.fill(8, rows.getTop(), width - 8, rows.getBottom(), 0x88000000);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (rows.children().isEmpty()) graphics.drawCenteredString(font, font.plainSubstrByWidth(text("empty").getString(), width - 24), width / 2, 66, 0xBBBBBB);
        var installation = controller().installation();
        var view = controller().snapshot();
        // Processing text is live state, never a notice retained after a completed operation.
        String status = controller().globalError().or(installation::error).map(error -> error.code() + ": " + safe(error.message())).orElseGet(() ->
                installation.busy() ? text(closeOperation == null ? "busy" : "closing_after_commit").getString()
                        : view.busy() ? view.message() : !notice.isBlank() ? notice
                        : installation.result().map(value -> value.status() + " | " + text("not_active").getString()).orElse(text("not_active").getString()));
        graphics.drawString(font, font.plainSubstrByWidth(safe(status), width - 16), 8, height - 9, 0xD7C4AB);
        if (mouseY >= rows.getTop() && mouseY <= rows.getBottom())
            rows.hoveredDescription().ifPresent(description -> renderRowTooltip(graphics, description, mouseX, mouseY));
    }

    /** Hover is a bounded preview; narration, the scrollable details and copied details stay complete. */
    private void renderRowTooltip(GuiGraphics graphics, Component description, int mouseX, int mouseY) {
        int tooltipWidth = Math.max(1, Math.min(360, width - 24));
        int maxLines = Math.max(1, Math.min(12, (height - 24) / 10));
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(description, tooltipWidth));
        if (lines.size() > maxLines) {
            lines.subList(maxLines - 1, lines.size()).clear();
            lines.add(Component.literal("... ").append(text("details")).getVisualOrderText());
        }
        graphics.renderTooltip(font, lines, mouseX, mouseY);
    }

    private record RowData(String key, List<String> lines, boolean checkable, boolean checked, boolean enabled,
                           Runnable select, Runnable activate) { }

    @OnlyIn(Dist.CLIENT)
    private final class EntryList extends ObjectSelectionList<EntryRow> {
        private boolean rebuilding;
        private EntryList() {
            super(TransactionScreen.this.minecraft, TransactionScreen.this.width - 16, TransactionScreen.this.height,
                    61, TransactionScreen.this.height - (mode == Mode.DETAILS ? 39 : 111), mode == Mode.DETAILS ? 12 : 50);
            setRenderBackground(false); setRenderTopAndBottom(false);
        }
        @Override public int getRowWidth() { return width - 14; }
        @Override protected int getScrollbarPosition() { return getRight() - 6; }
        private Optional<Component> hoveredDescription() { return Optional.ofNullable(getHovered()).map(EntryRow::getNarration); }
        private void replace(List<RowData> data, String selectedKey) {
            double scroll = getScrollAmount();
            String oldKey = getSelected() == null ? "" : getSelected().data.key();
            boolean focused = getFocused() != null;
            rebuilding = true; setFocused(null); clearEntries(); setSelected(null);
            for (RowData row : data) {
                EntryRow entry = new EntryRow(row); addEntry(entry);
                if (row.key().equals(selectedKey.isEmpty() ? oldKey : selectedKey)) { setSelected(entry); if (focused) setFocused(entry); }
            }
            rebuilding = false; setScrollAmount(scroll);
        }
        @Override public void setSelected(EntryRow row) {
            EntryRow old = getSelected(); super.setSelected(row);
            if (!rebuilding && row != null && row != old) row.data.select().run();
        }
    }

    @OnlyIn(Dist.CLIENT)
    private final class EntryRow extends ObjectSelectionList.Entry<EntryRow> {
        private final RowData data;
        private final Checkbox check;
        private EntryRow(RowData data) {
            this.data = data;
            check = new Checkbox(0, 0, 20, 20, Component.literal(data.lines().get(0)), data.checked(), false);
            check.active = data.enabled();
        }
        @Override public Component getNarration() {
            Component description = Component.literal(String.join(". ", data.lines()));
            return data.checkable() ? description.copy().append(". ").append(text(!data.enabled() ? "ineligible"
                    : data.checked() ? "checked" : "unchecked")) : description;
        }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int rowWidth, int rowHeight,
                                     int mouseX, int mouseY, boolean hovered, float partialTick) {
            if (data.checkable()) { check.setX(x); check.setY(y + 5); check.setFocused(isFocused()); check.render(graphics, mouseX, mouseY, partialTick); }
            int textX = x + (data.checkable() ? 25 : 4);
            for (int i = 0; i < data.lines().size(); i++) graphics.drawString(font,
                    font.plainSubstrByWidth(data.lines().get(i), Math.max(20, rowWidth - (textX - x) - 7)), textX, y + 2 + i * 12,
                    i == 0 ? 0xDEEBFF : 0xBBBBBB);
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) return false;
            rows.setFocused(this);
            if (data.checkable() && data.enabled() && x < check.getX() + 21) data.activate().run();
            return true;
        }
        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (data.enabled()) data.activate().run(); return true;
            }
            return false;
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static final class TransactionConfirmation extends ConfirmScreen {
        private final TransactionScreen owner;
        private boolean returning;
        private TransactionConfirmation(TransactionScreen owner, Component title, Component warning, Runnable accepted) {
            super(answer -> owner.finishConfirmation(answer, accepted), title, warning, text("confirm"), Component.translatable("gui.cancel"));
            this.owner = owner;
        }
        @Override public void onClose() { callback.accept(false); }
        @Override public void removed() { if (!returning) owner.owner.closeSession(); }
    }
}
