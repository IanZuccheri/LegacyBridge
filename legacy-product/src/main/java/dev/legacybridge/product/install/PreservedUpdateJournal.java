package dev.legacybridge.product.install;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static dev.legacybridge.product.install.TransactionFiles.require;
import static dev.legacybridge.product.install.TransactionJournal.text;

/** Separate exact schema, never a reinterpretation of preserved add-only or simple V1/V2 records. */
final class PreservedUpdateJournal {
    static final String PLAN="preserved-update-v1.json", PAYLOAD="update-payload.jar", BACKUP="update-backup.jar", RETIRED="update-retired.jar";
    enum Mark { RETIRE_STARTED, COMMITTED, RESTORE_STARTED, RESTORED }
    record Plan(String transactionId, String previousId, String previousSha256, String admissionId, String admissionSha256,
                String approvalId, String modId, String previousVersion, String modVersion, String previousArtifactSha256, String candidateSha256) {
        String installedName() { return "legacybridge-preserved-update-"+modId+"-"+transactionId+".jar"; }
    }
    static Map<String,Object> tree(Plan p) {
        var row=new TreeMap<String,Object>(); row.put("schemaVersion",1); row.put("reportType","LEGACY_PRESERVED_MANAGED_UPDATE");
        row.put("transactionId",p.transactionId()); row.put("previousId",p.previousId()); row.put("previousSha256",p.previousSha256());
        row.put("previousSchema","LEGACY_PRESERVED_ADD_ONLY_INSTALL:1"); row.put("admissionId",p.admissionId()); row.put("admissionSha256",p.admissionSha256());
        row.put("approvalId",p.approvalId()); row.put("approvalKind","DEVELOPER_EXPLICIT_UPDATE_SELECTION"); row.put("modId",p.modId());
        row.put("previousVersion",p.previousVersion()); row.put("modVersion",p.modVersion()); row.put("previousArtifactSha256",p.previousArtifactSha256());
        row.put("candidateSha256",p.candidateSha256()); row.put("payload",PAYLOAD); row.put("backup",BACKUP); row.put("retired",RETIRED);
        row.put("installedName",p.installedName()); row.put("profile","logic-preserving-v1"); row.put("admissionRecipe",PreservedAdmissionJournal.RECIPE);
        row.put("sourceMinecraft","1.16.5"); row.put("sourceForge","36.2.42"); row.put("targetMinecraft","1.20.1"); row.put("targetForge","47.4.23"); row.put("targetJava",17);
        row.put("runtimeModId","legacybridge_runtime"); row.put("runtimeVersion",AdapterCatalog.RUNTIME_VERSION);
        row.put("activation","NEXT_LAUNCH_ONLY"); row.put("durability",TransactionFiles.DURABILITY); return row;
    }
    static Plan read(Path tx) throws Exception {
        TransactionFiles.journalBudget(tx);
        for(String other:Set.of(TransactionJournal.PLAN,"prepared.json",PreservedAdmissionJournal.PLAN,PreservedInstallJournal.PLAN))
            require(!TransactionFiles.exists(tx.resolve(other)),"Mixed update schema; no implicit migration");
        var r=TransactionFiles.read(tx.resolve(PLAN));
        var p=new Plan(text(r.get("transactionId"),36),text(r.get("previousId"),36),text(r.get("previousSha256"),64),
                text(r.get("admissionId"),36),text(r.get("admissionSha256"),64),text(r.get("approvalId"),36),text(r.get("modId"),64),
                text(r.get("previousVersion"),128),text(r.get("modVersion"),128),text(r.get("previousArtifactSha256"),64),text(r.get("candidateSha256"),64));
        for(String id:List.of(p.transactionId(),p.previousId(),p.admissionId(),p.approvalId())) require(TransactionFiles.uuid(id),"Invalid update identity");
        require(p.transactionId().equals(tx.getFileName().toString()) && Set.of(p.transactionId(),p.previousId(),p.admissionId()).size()==3,"Aliased update identities");
        require(p.modId().matches("[a-z][a-z0-9_]{1,63}") && !Set.of("minecraft","forge","legacybridge_runtime").contains(p.modId()),"Reserved/invalid update ID");
        for(String hash:List.of(p.previousSha256(),p.admissionSha256(),p.previousArtifactSha256(),p.candidateSha256())) TransactionFiles.digest(hash);
        require(r.equals(parsed(tree(p))),"Unknown update schema/profile/ABI/approval/fields"); return p;
    }
    static Path path(Path tx,Mark mark) {return tx.resolve("preserved-update-"+mark.name().toLowerCase(Locale.ROOT)+"-v1.json");}
    static Map<String,Object> checkpoint(Plan p,String pin,Mark mark) {return Map.of("schemaVersion",1,"reportType","LEGACY_PRESERVED_UPDATE_CHECKPOINT",
            "transactionId",p.transactionId(),"planSha256",pin,"state",mark.name(),"candidateSha256",p.candidateSha256(),"previousArtifactSha256",p.previousArtifactSha256());}
    static boolean matches(Path tx,Plan p,String pin,Mark mark) throws Exception {
        if(!TransactionFiles.exists(path(tx,mark))) return false;
        require(TransactionFiles.read(path(tx,mark)).equals(parsed(checkpoint(p,pin,mark))),"Unknown/tampered update checkpoint"); return true;
    }
    private static Object parsed(Map<String,Object> tree) throws Exception {return BoundedJsonParser.parse(new String(TransactionFiles.json(tree),StandardCharsets.UTF_8));}
}
