package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;

import static dev.legacybridge.corpus.CompositionRunFiles.*;
import static dev.legacybridge.corpus.CompositionRunPlanner.Role;

/** Read-only config-content and necessary-file inventory. Not a complete output, process or arming validator. */
public final class CompositionNativeContentReview {
    private CompositionNativeContentReview() { }

    public static final class Review {
        private final Map<String, Object> tree;
        private final String sha256;
        private Review(Map<String, Object> tree) throws IOException {
            this.tree = Map.copyOf(tree);
            this.sha256 = digest(CorpusJson.canonicalJson(this.tree).getBytes(StandardCharsets.UTF_8));
        }
        public Map<String, Object> toTree() { return tree; }
        public String sha256() { return sha256; }
    }

    // Package-private: public entry is the private-constructor bound Policy, not arbitrary recorded JSON.
    static Review review(CompositionNativeOutputPolicy.Policy policy, Path cwd, BooleanSupplier cancelled) throws IOException {
        try {
            check(cancelled);
            Map<String, Object> first = policy.reviewRecordedLayout(cancelled).toTree();
            Role role = Role.valueOf(string(first.get("role"))); String runId = string(first.get("runId"));
            String prefix = role.name().toLowerCase(Locale.ROOT) + "/";
            Set<String> present = new TreeSet<>(); List<Map<String, Object>> checked = new ArrayList<>();
            Set<String> checkedPaths = new TreeSet<>(); Set<String> concerns = new TreeSet<>();
            for (Object value : (List<?>) first.get("files")) {
                check(cancelled); Map<String, Object> file = object(value); String path = string(file.get("path"));
                if (!path.startsWith(prefix)) continue;
                String relativePath = path.substring(prefix.length()); present.add(relativePath);
                if (!"CONFIGURATION".equals(file.get("category"))) continue;
                long size = integer(file.get("bytes"));
                ensure(size <= CompositionNativeConfigValidator.MAX_BYTES, "CONFIG_SIZE");
                Path selected = cwd.resolve(relativePath); canonical(selected);
                byte[] bytes;
                try (var input = Files.newInputStream(selected, LinkOption.NOFOLLOW_LINKS)) {
                    bytes = input.readNBytes(CompositionNativeConfigValidator.MAX_BYTES + 1);
                }
                ensure(bytes.length == size && digest(bytes).equals(file.get("sha256")), "CONFIG_DRIFT");
                Map<String, Object> content = CompositionNativeConfigValidator.validate(role, relativePath, bytes);
                ensure(hash(selected).equals(file.get("sha256")), "CONFIG_DRIFT");
                checked.add(Map.of("path", relativePath, "sha256", file.get("sha256"), "bytes", size, "review", content));
                checkedPaths.add(path);
                for (Object concern : (List<?>) content.get("launchConcerns")) concerns.add(string(concern));
            }
            check(cancelled);
            Map<String, Object> second = policy.reviewRecordedLayout(cancelled).toTree();
            ensure(first.equals(second), "TREE_DRIFT");
            List<String> required = requiredFiles(role, runId);
            List<String> missing = required.stream().filter(path -> !present.contains(path)).toList();
            Map<String, Object> result = new LinkedHashMap<>(flags("BOUNDED_CONFIG_REVIEW_ONLY"));
            result.put("policyVersion", "native-config-review-v1"); result.put("role", role.name()); result.put("runId", runId);
            result.put("layoutPolicySha256", policy.sha256()); result.put("planSha256", first.get("planSha256"));
            result.put("stageReceiptSha256", first.get("stageReceiptSha256"));
            result.put("layoutReviewSha256", digest(CorpusJson.canonicalJson(first).getBytes(StandardCharsets.UTF_8)));
            result.put("configurations", List.copyOf(checked)); result.put("launchConcerns", List.copyOf(concerns));
            result.put("requiredOutputs", Map.of("policyVersion", "native-necessary-files-v1", "paths", required,
                    "missing", missing, "status", missing.isEmpty() ? "PRESENT_ONLY" : "MISSING",
                    "successfulOutputSetVerified", false));
            result.put("contentUnchecked", ((List<?>) first.get("contentUnchecked")).stream().filter(p -> !checkedPaths.contains(p)).toList());
            result.put("propertiesValidation", "FIXED_KEYS_PRESERVED_OTHER_KNOWN_VALUES_UNVALIDATED");
            result.put("pending", List.of("NONDEFAULT_CONFIGURATION_CONTRACT", "NATIVE_PROPERTIES_SEMANTICS",
                    "WORLD_LOG_OBSERVATION_CONTENT", "NATIVE_LATE_LOOKUP_CLOSURE", "NATIVE_ARMING_CONTRACT",
                    "NATIVE_EXECUTION_AUTHORIZATION", "NATIVE_SOURCE_JAVA17", "NATIVE_ORIGINAL_TARGET_COMPARISON"));
            check(cancelled); return new Review(result);
        } catch (IOException | RuntimeException failure) { throw sanitized(failure); }
    }

    static List<String> requiredFiles(Role role, String runId) throws IOException {
        ensure(role != null && runId != null && runId.matches("run-[a-z0-9][a-z0-9-]{0,40}"), "BINDING");
        var paths = new TreeSet<>(List.of("server.properties", "config/fml.toml", "world/serverconfig/forge-server.toml",
                "banned-players.json", "banned-ips.json", "ops.json", "whitelist.json", "world/level.dat",
                "world/session.lock", "logs/latest.log", "logs/debug.log", "usercache.json", "composition-observations/" + runId + ".json"));
        if (role == Role.SOURCE) paths.add("config/forge-common.toml");
        return List.copyOf(paths);
    }
    private static Map<String, Object> flags(String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1); result.put("reportType", "LEGACY_COMPOSITION_NATIVE_CONFIG_REVIEW");
        result.put("status", status); result.put("runtimeValidation", "NOT_RUN");
        for (String flag : List.of("executionAllowed", "installationAllowed", "gameExecutionVerified", "nativeContentVerified",
                "nativeLaunchContractVerified", "exitObserved")) result.put(flag, false);
        return result;
    }
    private static void ensure(boolean condition, String code) throws IOException {
        if (!condition) throw new IOException("COMPOSE_CONTENT_" + code);
    }
    private static void check(BooleanSupplier cancelled) throws IOException {
        ensure(cancelled != null, "CANCELLATION"); boolean stop;
        try { stop = cancelled.getAsBoolean(); }
        catch (RuntimeException failure) { throw new IOException("COMPOSE_CONTENT_CANCELLATION_CALLBACK"); }
        ensure(!stop && !Thread.currentThread().isInterrupted(), "CANCELLED");
    }
    private static IOException sanitized(Exception failure) {
        String code = failure.getMessage();
        if (code != null && code.endsWith("_CANCELLED")) return new IOException("COMPOSE_CONTENT_CANCELLED");
        return new IOException(code != null && code.matches("COMPOSE_(?:CONTENT|CONFIG|OUTPUT)_[A-Z0-9_]+") ? code : "COMPOSE_CONTENT_REJECTED");
    }
    public static void main(String[] args) throws IOException {
        try {
            ensure(args != null && args.length == 6, "USAGE_REPOSITORY_ROLE_RUNID_JAVAHOME_PLAN_RECEIPT");
            var request = new CompositionRunPlanner.Request(Role.valueOf(args[1]), args[2], Path.of(args[3]));
            var snapshot = CompositionStagedRunVerifier.verify(Path.of(args[0]), request, args[4], args[5], () -> false);
            var policy = CompositionNativeOutputPolicy.prepare(snapshot, () -> false);
            System.out.print(CorpusJson.canonicalJson(policy.reviewRecordedContent(() -> false).toTree()));
        } catch (IOException | RuntimeException failure) {
            IOException clean = sanitized(failure); Map<String, Object> report = new LinkedHashMap<>(flags("BLOCKED"));
            report.put("error", clean.getMessage()); System.out.print(CorpusJson.canonicalJson(report)); throw clean;
        }
    }
}
