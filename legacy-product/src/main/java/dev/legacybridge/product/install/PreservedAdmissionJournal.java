package dev.legacybridge.product.install;

import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static dev.legacybridge.product.install.TransactionJournal.*;

/** Separate schema, not V3 installation. No destination, update/predecessor, activation or stored PASS. */
final class PreservedAdmissionJournal {
    static final String PLAN = "admission-v1.json", CHECKED = "admission-checked-v1.json", PAYLOAD = "admission-payload.jar";
    static final String RECIPE = "INDEPENDENT_LOGIC_READBACK_V1";
    record Plan(String transactionId, String original, String originalSha256, String staged,
                String candidateSha256, String modId, String modVersion, String mappingManifestSha256) { }
    private PreservedAdmissionJournal() { }

    static Map<String, Object> tree(Plan plan) {
        var root = new TreeMap<String, Object>();
        root.put("schemaVersion", 1); root.put("reportType", "LEGACY_PRESERVED_ADMISSION_BINDING");
        root.put("transactionId", plan.transactionId()); root.put("profile", "logic-preserving-v1");
        root.put("recipe", RECIPE); root.put("inspectionSchemaVersion", 1);
        root.put("sourceMinecraft", "1.16.5"); root.put("sourceForge", "36.2.42");
        root.put("targetMinecraft", "1.20.1"); root.put("targetForge", "47.4.23"); root.put("targetJava", 17);
        root.put("runtimeModId", "legacybridge_runtime"); root.put("runtimeVersion", AdapterCatalog.RUNTIME_VERSION);
        root.put("durability", TransactionFiles.DURABILITY); root.put("installationAllowed", false);
        root.put("original", plan.original()); root.put("originalSha256", plan.originalSha256());
        root.put("staged", plan.staged()); root.put("candidateSha256", plan.candidateSha256());
        root.put("payload", PAYLOAD); root.put("modId", plan.modId()); root.put("modVersion", plan.modVersion());
        root.put("mappingKind", "REVIEWED_LOCAL_BUNDLE"); root.put("mappingManifestSha256", plan.mappingManifestSha256());
        return root;
    }

    static Plan read(Path transaction) throws Exception {
        TransactionFiles.journalBudget(transaction);
        require(!TransactionFiles.exists(transaction.resolve(TransactionJournal.PLAN))
                && !TransactionFiles.exists(transaction.resolve("prepared.json")), "Mixed legacy/admission journal; no implicit migration");
        Map<String, Object> row = TransactionFiles.read(transaction.resolve(PLAN));
        String id = text(row.get("transactionId"), 36);
        require(TransactionFiles.uuid(id) && id.equals(transaction.getFileName().toString()), "Admission transaction identity differs");
        Plan plan = new Plan(id, text(row.get("original"), 1024), text(row.get("originalSha256"), 64),
                text(row.get("staged"), 1024), text(row.get("candidateSha256"), 64),
                text(row.get("modId"), 64), text(row.get("modVersion"), 128), text(row.get("mappingManifestSha256"), 64));
        require(plan.modId().matches("[a-z][a-z0-9_]{1,63}") && !Set.of("minecraft", "forge", "legacybridge_runtime").contains(plan.modId())
                && plan.modVersion().matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}"), "Invalid admission mod identity");
        TransactionFiles.digest(plan.originalSha256()); TransactionFiles.digest(plan.candidateSha256());
        TransactionFiles.digest(plan.mappingManifestSha256());
        Object expected = dev.legacybridge.converter.config.BoundedJsonParser.parse(
                new String(TransactionFiles.json(tree(plan)), java.nio.charset.StandardCharsets.UTF_8));
        require(row.equals(expected), "Unknown admission schema/profile/recipe/ABI/baseline or fields");
        return plan;
    }

    static Map<String, Object> checked(Plan plan, String planSha256) {
        return Map.of("schemaVersion", 1, "reportType", "LEGACY_PRESERVED_ADMISSION_OBSERVATION",
                "transactionId", plan.transactionId(), "planSha256", planSha256,
                "candidateSha256", plan.candidateSha256(), "installationAllowed", false);
    }
    static boolean checkpointMatches(Path transaction, Plan plan, String planSha256) throws Exception {
        Path checked = transaction.resolve(CHECKED);
        if (!TransactionFiles.exists(checked)) return false;
        Object expected = dev.legacybridge.converter.config.BoundedJsonParser.parse(
                new String(TransactionFiles.json(checked(plan, planSha256)), java.nio.charset.StandardCharsets.UTF_8));
        require(TransactionFiles.read(checked).equals(expected), "Unknown/tampered admission observation");
        return true;
    }
}
