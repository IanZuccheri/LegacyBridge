package dev.legacybridge.product.install;

import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.workflow.BridgeWorkspace;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Strict bounded inert ownership records. Paths in a journal never select an arbitrary destination. */
final class TransactionJournal {
    static final String PLAN = "plan-v2.json";
    record Previous(String transactionId, int schema, int index, String sha256, String version) { }
    record Entry(int index, String modId, String version, String original, String originalSha256,
                 String staged, String sha256, Previous previous) {
        String installedName(String id) { return name(modId, id, 2, index); }
        String payloadName() { return "payload-" + index + ".jar"; }
        String backupName() { return "backup-" + index + ".jar"; }
        String retiredName() { return "retired-" + index + ".jar"; }
        String previousName() { return previous == null ? null : name(modId, previous.transactionId(), previous.schema(), previous.index()); }
        StagedArtifactValidator.ValidatedCandidate candidate() { return new StagedArtifactValidator.ValidatedCandidate(modId, version); }
    }
    record Plan(String transactionId, List<Entry> entries) { Plan { entries = List.copyOf(entries); } }
    record Managed(Path installed, Path identity, String transactionId, int schema, int index, String sha256) { }
    private TransactionJournal() { }

    static String name(String modId, String id, int schema, int index) {
        return "legacybridge-" + modId + "-" + id + (schema == 1 ? "" : "-" + index) + ".jar";
    }
    static Map<String, Object> tree(Plan plan) {
        Map<String, Object> root = new TreeMap<>();
        root.put("schemaVersion", 2); root.put("reportType", "LEGACY_INSTALL_BATCH");
        root.put("transactionId", plan.transactionId()); root.put("profile", "simple-content-v1");
        root.put("minecraftVersion", "1.20.1"); root.put("forgeVersion", "47.4.23");
        root.put("runtimeVersion", AdapterCatalog.RUNTIME_VERSION); root.put("durability", TransactionFiles.DURABILITY);
        root.put("entries", plan.entries().stream().map(entry -> {
            Map<String, Object> row = new TreeMap<>(); row.put("index", entry.index()); row.put("modId", entry.modId());
            row.put("version", entry.version()); row.put("original", entry.original()); row.put("originalSha256", entry.originalSha256());
            row.put("staged", entry.staged()); row.put("sha256", entry.sha256()); row.put("installedFile", entry.installedName(plan.transactionId()));
            row.put("previous", entry.previous() == null ? null : Map.of("transactionId", entry.previous().transactionId(),
                    "schema", entry.previous().schema(), "index", entry.previous().index(), "sha256", entry.previous().sha256(), "version", entry.previous().version()));
            return row;
        }).toList()); return root;
    }
    static Plan read(Path transaction) throws Exception {
        TransactionFiles.journalBudget(transaction);
        Map<String, Object> root = TransactionFiles.read(transaction.resolve(PLAN));
        require(root.keySet().equals(Set.of("schemaVersion", "reportType", "transactionId", "profile", "minecraftVersion", "forgeVersion", "runtimeVersion", "durability", "entries")), "Unknown V2 plan fields");
        require(integer(root.get("schemaVersion")) == 2 && "LEGACY_INSTALL_BATCH".equals(root.get("reportType"))
                && "simple-content-v1".equals(root.get("profile")) && "1.20.1".equals(root.get("minecraftVersion"))
                && "47.4.23".equals(root.get("forgeVersion")) && AdapterCatalog.RUNTIME_VERSION.equals(root.get("runtimeVersion"))
                && TransactionFiles.DURABILITY.equals(root.get("durability")), "Unknown V2 plan baseline/schema");
        String id = text(root.get("transactionId"), 36); require(id.equals(transaction.getFileName().toString()) && TransactionFiles.uuid(id), "Plan transaction identity differs");
        require(root.get("entries") instanceof List<?> list && !list.isEmpty() && list.size() <= 4, "Expected 1-4 independent plan entries");
        List<Entry> entries = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (Object raw : (List<?>) root.get("entries")) {
            Map<String, Object> row = object(raw);
            require(row.keySet().equals(Set.of("index", "modId", "version", "original", "originalSha256", "staged", "sha256", "installedFile", "previous")), "Unknown V2 entry fields");
            int index = integer(row.get("index")); require(index == entries.size(), "Entry indexes must be contiguous and ordered");
            String modId = text(row.get("modId"), 64); require(modId.matches("[a-z][a-z0-9_]{1,63}")
                    && !Set.of("minecraft", "forge", "legacybridge_runtime").contains(modId) && ids.add(modId), "Invalid/duplicate/reserved batch mod ID");
            String version = text(row.get("version"), 128), sha = text(row.get("sha256"), 64), sourceSha = text(row.get("originalSha256"), 64);
            TransactionFiles.digest(sha); TransactionFiles.digest(sourceSha);
            Previous previous = null;
            if (row.get("previous") != null) {
                Map<String, Object> old = object(row.get("previous"));
                require(old.keySet().equals(Set.of("transactionId", "schema", "index", "sha256", "version")), "Unknown previous-artifact fields");
                String oldId = text(old.get("transactionId"), 36); int schema = integer(old.get("schema")), oldIndex = integer(old.get("index"));
                require(TransactionFiles.uuid(oldId) && !id.equals(oldId) && (schema == 1 || schema == 2)
                        && oldIndex >= 0 && oldIndex < 4 && (schema != 1 || oldIndex == 0), "Unknown previous transaction identity");
                String oldSha = text(old.get("sha256"), 64); TransactionFiles.digest(oldSha);
                previous = new Previous(oldId, schema, oldIndex, oldSha, text(old.get("version"), 128));
            }
            Entry entry = new Entry(index, modId, version, text(row.get("original"), 1024), sourceSha,
                    text(row.get("staged"), 1024), sha, previous);
            require(entry.installedName(id).equals(row.get("installedFile")), "Unowned installed filename"); entries.add(entry);
        }
        return new Plan(id, entries);
    }
    static Managed managed(BridgeWorkspace workspace, Path requested, String modId) throws Exception {
        Path transaction = TransactionFiles.transaction(requested, workspace.transactions()); String id = transaction.getFileName().toString();
        if (TransactionFiles.exists(transaction.resolve(PLAN))) {
            Plan plan = read(transaction); Entry entry = plan.entries().stream().filter(row -> row.modId().equals(modId)).findFirst()
                    .orElseThrow(() -> new java.io.IOException("Replacement receipt does not own the incoming ID"));
            return new Managed(workspace.mods().resolve(entry.installedName(id)), transaction.resolve(entry.payloadName()), id, 2, entry.index(), entry.sha256());
        }
        Map<String, Object> row = TransactionFiles.read(transaction.resolve("prepared.json"));
        require(row.keySet().equals(Set.of("schemaVersion", "reportType", "transactionId", "state", "installedFile", "sha256", "originalSha256", "modIds", "durability"))
                && integer(row.get("schemaVersion")) == 1 && "LEGACY_INSTALL_TRANSACTION".equals(row.get("reportType"))
                && id.equals(row.get("transactionId")) && "PREPARED_ADD_ONLY".equals(row.get("state"))
                && TransactionFiles.DURABILITY.equals(row.get("durability")) && List.of(modId).equals(row.get("modIds")), "Unknown legacy ownership receipt");
        String sha = text(row.get("sha256"), 64); TransactionFiles.digest(sha); TransactionFiles.digest(text(row.get("originalSha256"), 64));
        String name = name(modId, id, 1, 0); require(name.equals(row.get("installedFile")), "Unowned legacy installed filename");
        return new Managed(workspace.mods().resolve(name), transaction.resolve("payload.jar"), id, 1, 0, sha);
    }
    static void verifyPrevious(BridgeWorkspace workspace, Entry entry) throws Exception {
        if (entry.previous() == null) return;
        Previous old = entry.previous(); Managed managed = managed(workspace, workspace.transactions().resolve(old.transactionId()), entry.modId());
        require(managed.schema() == old.schema() && managed.index() == old.index() && managed.sha256().equals(old.sha256())
                && managed.installed().getFileName().toString().equals(entry.previousName()), "Previous artifact ownership changed");
    }
    static Map<String, Object> checkpoint(Path transaction, Plan plan, String state, int index) throws Exception {
        return Map.of("schemaVersion", 2, "reportType", "LEGACY_INSTALL_CHECKPOINT", "transactionId", plan.transactionId(),
                "planSha256", AddOnlyInstaller.hash(transaction.resolve(PLAN)), "state", state, "index", index);
    }
    static boolean checkpointMatches(Path path, Path transaction, Plan plan, String state, int index) throws Exception {
        if (!TransactionFiles.exists(path)) return false;
        Map<String, Object> expected = checkpoint(transaction, plan, state, index);
        Object normalized = dev.legacybridge.converter.config.BoundedJsonParser.parse(new String(TransactionFiles.json(expected), java.nio.charset.StandardCharsets.UTF_8));
        require(TransactionFiles.read(path).equals(normalized), "Unknown/tampered immutable checkpoint"); return true;
    }
    static String text(Object value, int bound) throws Exception { require(value instanceof String text && !text.isBlank() && text.length() <= bound, "Invalid bounded journal text"); return (String) value; }
    static int integer(Object value) throws Exception { require(value instanceof BigDecimal, "Journal integer required"); return ((BigDecimal) value).intValueExact(); }
    static Map<String, Object> object(Object value) throws Exception { require(value instanceof Map<?, ?>, "Journal object required"); @SuppressWarnings("unchecked") var map = (Map<String, Object>) value; return map; }
    static void require(boolean condition, String message) throws Exception { TransactionFiles.require(condition, message); }
}
