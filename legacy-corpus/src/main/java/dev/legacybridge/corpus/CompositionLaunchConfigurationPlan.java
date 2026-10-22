package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

import static dev.legacybridge.corpus.CompositionRunFiles.*;
import static dev.legacybridge.corpus.CompositionRunPlanner.Role;

/**
 * Immutable, read-only proposal bound to a currently verified UNARMED developer stage.
 * This is not a new stage receipt, launch permission, settings installer or native execution contract.
 * Existing stage properties/argv/receipts are deliberately never changed. See the restricted-settings audit.
 */
public final class CompositionLaunchConfigurationPlan {
    private CompositionLaunchConfigurationPlan() { }

    public static final class Plan {
        private final CompositionStagedRunVerifier.Snapshot snapshot;
        private final Map<String, String> files;
        private final Map<String, Object> tree;
        private final String sha256;
        private Plan(CompositionStagedRunVerifier.Snapshot snapshot, Map<String, String> files, Map<String, Object> tree) throws IOException {
            this.snapshot = snapshot; this.files = Map.copyOf(files); this.tree = Map.copyOf(tree);
            this.sha256 = digest(CorpusJson.canonicalJson(this.tree).getBytes(StandardCharsets.UTF_8));
        }
        public Map<String, String> files() { return files; }
        public Map<String, Object> toTree() { return tree; }
        public String sha256() { return sha256; }
        /** Reconstructs the same proposal, rechecking original/candidate/platform/JDK/receipt/tree pins. Never writes. */
        public Plan recheck(BooleanSupplier cancelled) throws IOException {
            Plan checked = prepare(snapshot, cancelled);
            ensure(sha256.equals(checked.sha256), "PROPOSAL_DRIFT"); return checked;
        }
    }

    public static Plan prepare(CompositionStagedRunVerifier.Snapshot snapshot, BooleanSupplier cancelled) throws IOException {
        try {
            check(cancelled); ensure(snapshot != null, "BINDING");
            var first = snapshot.recheck(cancelled); Role role = first.plan().role();
            Map<String, String> files = CompositionRestrictedSettings.files(role);
            String baseline = string(object(object(first.plan().report().get("plan")).get("generatedFiles")).get("server.properties"));
            var required = Map.of("server-ip", "127.0.0.1", "server-port", role == Role.SOURCE ? "25597" : "25598", "level-name", "world");
            Map<String, String> explicit = CompositionNativeOutputPolicy.validateProperties(role, baseline.getBytes(StandardCharsets.UTF_8), required);
            CompositionNativeOutputPolicy.validateProperties(role, files.get("server.properties").getBytes(StandardCharsets.UTF_8), explicit);
            List<Map<String, Object>> proposed = new ArrayList<>();
            for (var entry : new TreeMap<>(files).entrySet()) {
                check(cancelled); byte[] bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
                CompositionRestrictedSettings.validatePlannedFile(role, entry.getKey(), bytes);
                String generatedPath = entry.getKey().equals(CompositionRestrictedSettings.TEMPLATE) ? CompositionRestrictedSettings.SERVER : entry.getKey();
                CompositionRestrictedSettings.validateGeneratedFile(role, generatedPath, bytes);
                proposed.add(Map.of("path", entry.getKey(), "content", entry.getValue(), "bytes", bytes.length, "sha256", digest(bytes),
                        "expectedNativePath", generatedPath, "nativeConsumptionVerified", false));
            }
            check(cancelled); var second = first.recheck(cancelled);
            ensure(first.toTree().equals(second.toTree()), "STAGE_DRIFT");
            var tree = new LinkedHashMap<>(flags("RESTRICTED_CONFIGURATION_PLANNED_ONLY"));
            tree.put("policyVersion", CompositionRestrictedSettings.VERSION); tree.put("role", role.name()); tree.put("runId", first.plan().runId());
            tree.put("planSha256", first.plan().sha256()); tree.put("stageReceiptSha256", first.receiptSha256());
            tree.put("stageVerification", second.toTree()); tree.put("immutableInputCount", first.immutableInputs().size());
            tree.put("proposedFiles", List.copyOf(proposed)); tree.put("changesFromNativeDefaults", CompositionRestrictedSettings.changes(role));
            tree.put("baselinePropertiesSha256", digest(baseline.getBytes(StandardCharsets.UTF_8)));
            tree.put("preservedExplicitPropertyCount", explicit.size());
            tree.put("preconditions", List.of("CURRENT_REVIEWED_UNARMED_STAGE", "NO_EXISTING_WORLD_OR_NATIVE_CONFIGS",
                    "DEFAULT_CONFIG_PATH_DEFAULTCONFIGS", "ALL_REVIEWED_EXPLICIT_PROPERTIES_PRESERVED"));
            tree.put("limits", Map.of("maximumProposedFileBytes", 65536, "maximumProposedFiles", 4,
                    "activeProperties", role == Role.SOURCE ? 50 : 56, "stagedFilesWritten", 0));
            tree.put("pending", List.of("RESTRICTED_SETTINGS_MATERIALIZATION_CONTRACT", "BOUND_NATIVE_SAVE_CONTENT_VALIDATION",
                    "NATIVE_LATE_LOOKUP_CLOSURE", "OS_EGRESS_AND_PROCESS_CONTAINMENT", "NATIVE_ARMING_CONTRACT",
                    "NATIVE_EXECUTION_AUTHORIZATION", "NATIVE_SOURCE_JAVA17", "NATIVE_ORIGINAL_TARGET_COMPARISON"));
            check(cancelled); return new Plan(second, files, tree);
        } catch (IOException | RuntimeException invalid) { throw sanitized(invalid); }
    }
    private static Map<String, Object> flags(String status) {
        var tree = new LinkedHashMap<String, Object>();
        tree.put("schemaVersion", 1); tree.put("reportType", "LEGACY_COMPOSITION_LAUNCH_CONFIGURATION_PLAN");
        tree.put("status", status); tree.put("runtimeValidation", "NOT_RUN");
        for (String flag : List.of("materialized", "executionAllowed", "installationAllowed", "gameExecutionVerified",
                "nativeLaunchContractVerified", "networkIsolationVerified", "nativeSaveVerified")) tree.put(flag, false);
        return tree;
    }
    private static void ensure(boolean valid, String code) throws IOException { if (!valid) throw new IOException("COMPOSE_SETTINGS_" + code); }
    private static void check(BooleanSupplier cancelled) throws IOException {
        ensure(cancelled != null, "CANCELLATION"); boolean stop;
        try { stop = cancelled.getAsBoolean(); }
        catch (RuntimeException invalid) { throw new IOException("COMPOSE_SETTINGS_CANCELLATION_CALLBACK"); }
        ensure(!stop && !Thread.currentThread().isInterrupted(), "CANCELLED");
    }
    private static IOException sanitized(Exception failure) {
        String code = failure.getMessage();
        if (code != null && code.endsWith("_CANCELLED")) return new IOException("COMPOSE_SETTINGS_CANCELLED");
        if (code != null && code.endsWith("_CANCELLATION_CALLBACK")) return new IOException("COMPOSE_SETTINGS_CANCELLATION_CALLBACK");
        return new IOException(code != null && code.matches("COMPOSE_SETTINGS_[A-Z0-9_]+") ? code : "COMPOSE_SETTINGS_REJECTED");
    }
    public static void main(String[] args) throws IOException {
        try {
            ensure(args != null && args.length == 6, "USAGE_REPOSITORY_ROLE_RUNID_JAVAHOME_PLAN_RECEIPT");
            var request = new CompositionRunPlanner.Request(Role.valueOf(args[1]), args[2], Path.of(args[3]));
            var snapshot = CompositionStagedRunVerifier.verify(Path.of(args[0]), request, args[4], args[5], () -> false);
            System.out.print(CorpusJson.canonicalJson(prepare(snapshot, () -> false).toTree()));
        } catch (IOException | RuntimeException failure) {
            IOException clean = sanitized(failure); var tree = new LinkedHashMap<>(flags("BLOCKED"));
            tree.put("error", clean.getMessage()); System.out.print(CorpusJson.canonicalJson(tree)); throw clean;
        }
    }
}
