package dev.legacybridge.product.history;

import dev.legacybridge.product.controller.PreservedInstallView;
import dev.legacybridge.product.controller.PreservedUpdateSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Separate update envelope, never replayed as install permission. Embedded observation retains its schema-1 contract. */
public final class PreservedUpdateReceipts {
    private static final String PREFIX = "preserved-update-job-", TYPE = "LEGACYBRIDGE_PRESERVED_UPDATE_JOB";
    private static final Set<String> FIELDS = Set.of("schemaVersion", "reportType", "previousInstallationId", "previousPlanSha256", "observation");
    public record Receipt(UUID previousInstallationId, String previousPlanSha256, PreservedInstallReceipts.Receipt observation) {
        public boolean installationAllowed() { return false; }
        public boolean safeToClose() { return false; }
        public String activation() { return "NOT_OBSERVED"; }
    }
    public record History(PreservedInstallReceipts.Status status, List<Receipt> receipts, Optional<String> diagnostic) {
        public History { receipts = List.copyOf(receipts); }
        public static History notRead() { return new History(PreservedInstallReceipts.Status.NOT_READ, List.of(), Optional.empty()); }
    }
    public Path write(Path root, PreservedUpdateSelection update, PreservedInstallReceipts.Action action,
                      UUID approval, PreservedInstallView view) throws IOException {
        var s = update.replacement(); UUID operation = view.operationId().orElseThrow();
        var observation = new PreservedInstallReceipts.Receipt(operation, view.reviewId().orElseThrow(), action,
                id(s.admission().getFileName().toString()), s.admissionSha256(), s.data().expectedManifestSha256().orElseThrow(),
                Optional.ofNullable(approval), view.state(), view.result().flatMap(r -> r.transaction()).map(p -> UUID.fromString(p.getFileName().toString())),
                view.result().flatMap(r -> r.planSha256()), view.admission().flatMap(r -> r.inspection()).map(r -> r.originalSha256()),
                view.admission().flatMap(r -> r.inspection()).map(r -> r.candidateSha256()), view.error().map(e -> e.code()));
        var tree = new LinkedHashMap<String, Object>();
        tree.put("schemaVersion", 1); tree.put("reportType", TYPE);
        tree.put("previousInstallationId", update.previousInstallation().getFileName().toString());
        tree.put("previousPlanSha256", update.previousPlanSha256()); tree.put("observation", PreservedInstallReceipts.tree(observation));
        parse(tree);
        return PreservedInstallReceipts.publish(root, operation, PREFIX, tree);
    }
    public History read(Path root, BooleanSupplier cancelled) {
        var result = PreservedInstallReceipts.read(root, cancelled, PREFIX, PreservedUpdateReceipts::parse, r -> r.observation().operationId());
        return new History(result.status(), result.receipts(), result.diagnostic());
    }
    private static Receipt parse(Map<?, ?> tree) throws IOException {
        Object schema = tree.get("schemaVersion");
        if (!tree.keySet().equals(FIELDS) || !(schema instanceof Integer || schema instanceof java.math.BigDecimal)
                || !schema.toString().equals("1") || !TYPE.equals(tree.get("reportType"))
                || !(tree.get("previousInstallationId") instanceof String previous)
                || !(tree.get("previousPlanSha256") instanceof String pin) || !pin.matches("[a-f0-9]{64}")
                || !(tree.get("observation") instanceof Map<?, ?> observation))
            throw new IOException("Invalid preserved-update receipt schema");
        return new Receipt(id(previous), pin, PreservedInstallReceipts.parse(observation));
    }
    private static UUID id(String value) throws IOException {
        try { UUID id = UUID.fromString(value); if (!id.toString().equals(value)) throw new IllegalArgumentException(); return id; }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid canonical update UUID", invalid); }
    }
}
