package dev.legacybridge.product.install;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static dev.legacybridge.product.install.TransactionFiles.require;
import static dev.legacybridge.product.install.TransactionJournal.text;

/** Separate immutable install schema. No V1/V2/admission migration or predecessor/update fields. */
final class PreservedInstallJournal {
    static final String PLAN = "preserved-install-v1.json", PAYLOAD = "preserved-install-payload.jar";
    enum Mark { COMMITTED, RESTORE_STARTED, RESTORED }
    record Plan(String transactionId, String admissionId, String admissionSha256, String approvalId,
                String modId, String modVersion, String candidateSha256) {
        String installedName() { return "legacybridge-preserved-" + modId + "-" + transactionId + ".jar"; }
    }
    private PreservedInstallJournal() { }
    static Map<String, Object> tree(Plan plan) {
        var row = new TreeMap<String, Object>();
        row.put("schemaVersion", 1); row.put("reportType", "LEGACY_PRESERVED_ADD_ONLY_INSTALL");
        row.put("transactionId", plan.transactionId()); row.put("profile", "logic-preserving-v1");
        row.put("admissionRecipe", PreservedAdmissionJournal.RECIPE); row.put("admissionSchemaVersion", 1);
        row.put("admissionId", plan.admissionId()); row.put("admissionSha256", plan.admissionSha256());
        row.put("approvalKind", "DEVELOPER_EXPLICIT_SELECTION"); row.put("approvalId", plan.approvalId());
        row.put("modId", plan.modId()); row.put("modVersion", plan.modVersion()); row.put("candidateSha256", plan.candidateSha256());
        row.put("payload", PAYLOAD); row.put("installedName", plan.installedName());
        row.put("sourceMinecraft", "1.16.5"); row.put("sourceForge", "36.2.42");
        row.put("targetMinecraft", "1.20.1"); row.put("targetForge", "47.4.23"); row.put("targetJava", 17);
        row.put("runtimeModId", "legacybridge_runtime"); row.put("runtimeVersion", AdapterCatalog.RUNTIME_VERSION);
        row.put("durability", TransactionFiles.DURABILITY); row.put("activation", "NEXT_LAUNCH_ONLY");
        return row;
    }
    static Plan read(Path tx) throws Exception {
        TransactionFiles.journalBudget(tx);
        for (String other : Set.of(TransactionJournal.PLAN, "prepared.json", PreservedAdmissionJournal.PLAN, PreservedUpdateJournal.PLAN))
            require(!TransactionFiles.exists(tx.resolve(other)), "Mixed installation schema; no implicit migration");
        var row = TransactionFiles.read(tx.resolve(PLAN));
        var plan = new Plan(text(row.get("transactionId"), 36), text(row.get("admissionId"), 36),
                text(row.get("admissionSha256"), 64), text(row.get("approvalId"), 36),
                text(row.get("modId"), 64), text(row.get("modVersion"), 128), text(row.get("candidateSha256"), 64));
        require(TransactionFiles.uuid(plan.transactionId()) && TransactionFiles.uuid(plan.admissionId()) && TransactionFiles.uuid(plan.approvalId())
                && tx.getFileName().toString().equals(plan.transactionId()) && !plan.transactionId().equals(plan.admissionId()), "Invalid preserved installation identity");
        require(plan.modId().matches("[a-z][a-z0-9_]{1,63}") && !Set.of("minecraft", "forge", "legacybridge_runtime").contains(plan.modId())
                && plan.modVersion().matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}"), "Invalid installed mod identity");
        TransactionFiles.digest(plan.admissionSha256()); TransactionFiles.digest(plan.candidateSha256());
        require(row.equals(parsed(tree(plan))), "Unknown preserved install schema/profile/ABI/approval/fields");
        return plan;
    }
    static Path path(Path tx, Mark mark) { return tx.resolve("preserved-" + mark.name().toLowerCase(java.util.Locale.ROOT) + "-v1.json"); }
    static Map<String, Object> checkpoint(Plan plan, String sha, Mark mark) {
        return Map.of("schemaVersion", 1, "reportType", "LEGACY_PRESERVED_INSTALL_CHECKPOINT", "transactionId", plan.transactionId(),
                "planSha256", sha, "candidateSha256", plan.candidateSha256(), "state", mark.name());
    }
    static boolean matches(Path tx, Plan plan, String sha, Mark mark) throws Exception {
        if (!TransactionFiles.exists(path(tx, mark))) return false;
        require(TransactionFiles.read(path(tx, mark)).equals(parsed(checkpoint(plan, sha, mark))), "Unknown/tampered preserved checkpoint");
        return true;
    }
    private static Object parsed(Map<String, Object> tree) throws Exception {
        return BoundedJsonParser.parse(new String(TransactionFiles.json(tree), StandardCharsets.UTF_8));
    }
}
