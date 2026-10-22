package dev.legacybridge.corpus;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

import static dev.legacybridge.corpus.CompositionRunFiles.*;
import static dev.legacybridge.corpus.CompositionRunPlanner.Role;

/**
 * Read-only finite file-layout review, NOT a native session, sandbox or content validator.
 * Captures only a verified unarmed stage. Does not arm, launch, publish or mutate anything.
 * Unknown paths fail closed; recognized world/config/log/observation bytes remain unvalidated.
 * See docs/COMPOSITION_NATIVE_OUTPUT_POLICY.md for the exact source audit and remaining gates.
 */
public final class CompositionNativeOutputPolicy {
    private static final String VERSION = "native-output-layout-v1";
    private static final int PROPERTIES_MAX = 65536, ENTRIES_MAX = 4096;
    private static final long OUTPUT_MAX = 256L << 20;
    private static final Set<String> SOURCE_KEYS = Set.of((
            "allow-flight allow-nether announce-player-achievements broadcast-console-to-ops broadcast-rcon-to-ops "
            + "difficulty enable-command-block enable-jmx-monitoring enable-query enable-rcon enable-status enforce-whitelist "
            + "entity-broadcast-range-percentage force-gamemode function-permission-level gamemode hardcore level-name "
            + "max-build-height max-players max-tick-time max-world-size motd network-compression-threshold online-mode "
            + "op-permission-level player-idle-timeout prevent-proxy-connections pvp query.port rate-limit rcon.password "
            + "rcon.port resource-pack resource-pack-hash resource-pack-sha1 server-ip server-port snooper-enabled "
            + "spawn-animals spawn-monsters spawn-npcs spawn-protection sync-chunk-writes text-filtering-config "
            + "use-native-transport view-distance white-list generator-settings level-seed generate-structures level-type").split(" "));
    private static final Set<String> TARGET_KEYS = targetKeys();
    private static final Set<String> EMPTY_LISTS = Set.of("banned-players.json", "banned-ips.json", "ops.json", "whitelist.json", "usercache.json");
    public enum Category { IMMUTABLE, DIRECTORY, SERVER_PROPERTIES, CONFIGURATION, LOG, WORLD, EMPTY_PLAYER_LIST, OBSERVATION }
    private CompositionNativeOutputPolicy() { }

    public static final class Policy {
        private final CompositionStagedRunVerifier.Snapshot snapshot;
        private final Map<Path, CompositionProcessCollector.Input> immutable;
        private final Set<String> baselineDirectories;
        private final Map<String, String> explicitProperties;
        private final Map<String, Object> tree;
        private final String sha256;
        private Policy(CompositionStagedRunVerifier.Snapshot snapshot, Set<String> directories,
                       Map<String, String> properties) throws IOException {
            this.snapshot = snapshot; this.baselineDirectories = Set.copyOf(directories);
            this.explicitProperties = Map.copyOf(properties);
            Map<Path, CompositionProcessCollector.Input> pins = new LinkedHashMap<>();
            for (var pin : snapshot.immutableInputs()) pins.put(pin.path(), pin);
            this.immutable = Map.copyOf(pins);
            Map<String, Object> fields = new LinkedHashMap<>(flags("PLANNED_LAYOUT_ONLY"));
            fields.put("policyVersion", VERSION); fields.put("role", snapshot.plan().role().name());
            fields.put("runId", snapshot.plan().runId()); fields.put("planSha256", snapshot.plan().sha256());
            fields.put("stageReceiptSha256", snapshot.receiptSha256()); fields.put("immutableInputCount", pins.size());
            fields.put("fixedProperties", this.explicitProperties);
            fields.put("limits", Map.of("maxEntries", ENTRIES_MAX, "maxGeneratedBytes", OUTPUT_MAX,
                    "maxPropertiesBytes", PROPERTIES_MAX, "maxFileBytes", 64L << 20, "maxDepth", 12));
            fields.put("pending", List.of("NATIVE_CONTENT_VALIDATION", "NATIVE_LATE_LOOKUP_CLOSURE", "NATIVE_ARMING_CONTRACT",
                    "NATIVE_EXECUTION_AUTHORIZATION", "NATIVE_SOURCE_JAVA17", "NATIVE_ORIGINAL_TARGET_COMPARISON"));
            tree = Map.copyOf(fields); sha256 = digest(CorpusJson.canonicalJson(tree).getBytes(StandardCharsets.UTF_8));
        }
        public String sha256() { return sha256; }
        public Map<String, Object> toTree() { return tree; }

        /** Additional finite config review; never grants native execution or validates world/save content. */
        public CompositionNativeContentReview.Review reviewRecordedContent(BooleanSupplier cancelled) throws IOException {
            return CompositionNativeContentReview.review(this, snapshot.plan().workingDirectory(), cancelled);
        }

        /** Caller must provide a quiescent tree; this does not observe exit/EOF or grant arming authority. */
        public Review reviewRecordedLayout(BooleanSupplier cancelled) throws IOException {
            try {
                check(cancelled);
                Layout first = scan(cancelled);
                check(cancelled);
                Layout second = scan(cancelled);
                ensure(first.equals(second), "TREE_DRIFT");
                List<Map<String, Object>> files = first.files().values().stream().map(FileRecord::tree).toList();
                Map<String, Object> result = new LinkedHashMap<>(flags("BOUNDED_LAYOUT_REVIEW_ONLY"));
                result.put("policyVersion", VERSION); result.put("policySha256", sha256);
                result.put("planSha256", snapshot.plan().sha256()); result.put("stageReceiptSha256", snapshot.receiptSha256());
                result.put("role", snapshot.plan().role().name()); result.put("runId", snapshot.plan().runId());
                result.put("files", files); result.put("fileCount", files.size());
                result.put("directories", first.directories().stream().sorted().toList());
                result.put("immutableInputCount", immutable.size());
                result.put("contentUnchecked", files.stream().filter(f -> "NOT_VALIDATED".equals(f.get("contentValidation")))
                        .map(f -> f.get("path")).toList());
                result.put("exitObserved", false); result.put("runtimeValidation", "NOT_RUN");
                check(cancelled); return new Review(Map.copyOf(result));
            } catch (IOException | RuntimeException failure) { throw sanitized(failure); }
        }

        private Layout scan(BooleanSupplier cancelled) throws IOException {
            Path root = snapshot.runRoot(), cwd = snapshot.plan().workingDirectory();
            Map<String, FileRecord> found = new TreeMap<>(); Set<String> dirs = new HashSet<>(), names = new HashSet<>();
            List<Path> identities = new ArrayList<>(); Map<Object, Path> keys = new LinkedHashMap<>();
            // Original inputs and the selected JDK remain immutable, even outside the staged tree.
            for (var input : immutable.values()) {
                check(cancelled); ensure(input.bytes() == Files.size(input.path()) && input.sha256().equals(hash(input.path())), "IMMUTABLE_DRIFT");
                remember(input.path(), identities, keys);
            }
            directory(root); long generated = 0; int entries = 0;
            try (var paths = Files.walk(root, 12)) {
                var iterator = paths.iterator();
                while (iterator.hasNext()) {
                    check(cancelled); Path path = iterator.next(); ensure(++entries <= ENTRIES_MAX, "ENTRY_LIMIT");
                    canonical(path); String name = name(root, path);
                    ensure(names.add(name.toLowerCase(Locale.ROOT)), "PATH_COLLISION");
                    var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attrs.isDirectory()) {
                        if (!baselineDirectories.contains(name)) {
                            ensure(path.startsWith(cwd) && !path.equals(cwd), "UNKNOWN_DIRECTORY");
                            classify(snapshot.plan().role(), snapshot.plan().runId(), name(cwd, path), true);
                        }
                        dirs.add(name); continue;
                    }
                    ensure(attrs.isRegularFile(), "FILE_TYPE");
                    var pin = immutable.get(path);
                    if (pin != null) {
                        ensure(pin.bytes() == attrs.size() && pin.sha256().equals(hash(path)), "IMMUTABLE_DRIFT");
                        found.put(name, new FileRecord(name, Category.IMMUTABLE, pin.sha256(), pin.bytes(), "PINNED_BYTES"));
                        continue;
                    }
                    ensure(path.startsWith(cwd), "UNKNOWN_FILE");
                    Category category = classify(snapshot.plan().role(), snapshot.plan().runId(), name(cwd, path), false);
                    ensure(attrs.size() <= sizeLimit(category) && (generated += attrs.size()) <= OUTPUT_MAX, "BYTE_LIMIT");
                    remember(path, identities, keys);
                    String before = hash(path), validation = "NOT_VALIDATED";
                    if (category == Category.SERVER_PROPERTIES) {
                        byte[] bytes = boundedRead(path, PROPERTIES_MAX);
                        ensure(digest(bytes).equals(before), "CONTENT_DRIFT");
                        validateProperties(snapshot.plan().role(), bytes, explicitProperties);
                        validation = "FIXED_KEYS_PRESERVED_OTHER_KNOWN_VALUES_UNVALIDATED";
                    } else if (category == Category.EMPTY_PLAYER_LIST) {
                        byte[] bytes = boundedRead(path, 8192); ensure(digest(bytes).equals(before), "CONTENT_DRIFT");
                        String value = strictUtf8(bytes);
                        // Exact JSON empty array with JSON whitespace; no parser/provider/remote lookups.
                        ensure(value.matches("[ \\t\\r\\n]*\\[[ \\t\\r\\n]*][ \\t\\r\\n]*"), "NONEMPTY_PLAYER_LIST");
                        validation = "EMPTY_JSON_ARRAY_ONLY";
                    }
                    ensure(attrs.size() == Files.size(path) && before.equals(hash(path)), "CONTENT_DRIFT");
                    found.put(name, new FileRecord(name, category, before, attrs.size(), validation));
                }
            }
            ensure(dirs.containsAll(baselineDirectories), "MISSING_DIRECTORY");
            ensure(found.containsKey(name(root, cwd.resolve("server.properties"))), "MISSING_PROPERTIES");
            for (Path pin : immutable.keySet()) if (pin.startsWith(root)) ensure(found.containsKey(name(root, pin)), "MISSING_IMMUTABLE");
            return new Layout(java.util.Collections.unmodifiableMap(found), Set.copyOf(dirs));
        }
    }
    private record Layout(Map<String, FileRecord> files, Set<String> directories) { }
    public static final class Review {
        private final Map<String, Object> tree;
        private Review(Map<String, Object> tree) { this.tree = tree; }
        public Map<String, Object> toTree() { return tree; }
    }
    private record FileRecord(String path, Category category, String sha256, long bytes, String contentValidation) {
        Map<String, Object> tree() { return Map.of("path", path, "category", category.name(), "sha256", sha256,
                "bytes", bytes, "contentValidation", contentValidation); }
    }

    /** No public constructor accepts arbitrary pins/receipts as an approved stage. */
    public static Policy prepare(CompositionStagedRunVerifier.Snapshot selected, BooleanSupplier cancelled) throws IOException {
        try {
            check(cancelled); ensure(selected != null, "SNAPSHOT");
            var snapshot = selected.recheck(cancelled);
            Map<String, Object> receipt = CorpusJson.read(snapshot.runRoot().resolve("stage-receipt.json"));
            ensure(snapshot.receiptSha256().equals(digest(CorpusJson.canonicalJson(receipt).getBytes(StandardCharsets.UTF_8))), "RECEIPT_DRIFT");
            Set<String> directories = new HashSet<>(); directories.add("");
            for (Object value : (List<?>) receipt.get("directories")) directories.add(string(value));
            Map<String, Object> plan = object(snapshot.plan().report().get("plan"));
            String planned = string(object(plan.get("generatedFiles")).get("server.properties"));
            Map<String, String> properties = parseProperties(snapshot.plan().role(), planned.getBytes(StandardCharsets.UTF_8));
            ensure("world".equals(properties.get("level-name")) && "127.0.0.1".equals(properties.get("server-ip"))
                    && (snapshot.plan().role() == Role.SOURCE ? "25597" : "25598").equals(properties.get("server-port")), "PLAN_PROPERTIES");
            validateProperties(snapshot.plan().role(), planned.getBytes(StandardCharsets.UTF_8), properties);
            check(cancelled); return new Policy(snapshot, directories, properties);
        } catch (IOException | RuntimeException failure) { throw sanitized(failure); }
    }

    // Pure finite classification, not blanket permission to write below world/config/logs.
    static Category classify(Role role, String runId, String path, boolean directory) throws IOException {
        try {
            ensure(role != null && runId != null && runId.matches("run-[a-z0-9][a-z0-9-]{0,40}"), "BINDING");
            relative(path);
            if (directory) {
                if (Set.of("config", "defaultconfigs", "logs", "world", "world/data", "world/datapacks", "world/serverconfig", "world/playerdata",
                        "world/region", "world/poi", "world/DIM-1", "world/DIM1", "world/DIM-1/data", "world/DIM1/data",
                        "world/DIM-1/region", "world/DIM1/region", "world/DIM-1/poi", "world/DIM1/poi").contains(path)
                        || role == Role.TARGET && path.matches("world/(?:DIM-1/|DIM1/)?entities")) return Category.DIRECTORY;
                throw new IOException("COMPOSE_OUTPUT_UNKNOWN_DIRECTORY");
            }
            if (path.equals("server.properties")) return Category.SERVER_PROPERTIES;
            if (path.equals("config/fml.toml") || path.equals("config/forge-common.toml")
                    || path.equals("world/serverconfig/forge-server.toml")) return Category.CONFIGURATION;
            if (path.equals("logs/latest.log") || path.equals("logs/debug.log")
                    || datedLog(path)
                    || path.matches("logs/debug-[1-5]\\.log\\.gz")) return Category.LOG;
            if (EMPTY_LISTS.contains(path)) return Category.EMPTY_PLAYER_LIST;
            if (path.equals("composition-observations/" + runId + ".json")) return Category.OBSERVATION;
            if (Set.of("world/level.dat", "world/level.dat_old", "world/session.lock", "world/data/scoreboard.dat",
                    "world/data/raids.dat", "world/DIM-1/data/raids.dat", "world/DIM1/data/raids_end.dat").contains(path)
                    || path.matches("world/(?:DIM-1/|DIM1/)?data/capabilities\\.dat")
                    || role == Role.TARGET && path.equals("world/data/random_sequences.dat")
                    || region(role, path)) return Category.WORLD;
            throw new IOException("COMPOSE_OUTPUT_UNKNOWN_FILE");
        } catch (IOException | RuntimeException failure) { throw sanitized(failure); }
    }
    private static boolean datedLog(String path) {
        if (!path.matches("logs/[0-9]{4}-[0-9]{2}-[0-9]{2}-[1-9][0-9]?\\.log\\.gz")) return false;
        try { return java.time.LocalDate.parse(path.substring(5, 15)).getYear() > 0; }
        catch (java.time.DateTimeException invalid) { return false; }
    }
    private static boolean region(Role role, String path) {
        String types = role == Role.SOURCE ? "region|poi" : "region|poi|entities";
        if (!path.matches("world/(?:DIM-1/|DIM1/)?(?:" + types + ")/r\\.-?(?:0|[1-9][0-9]{0,6})\\.-?(?:0|[1-9][0-9]{0,6})\\.mca")) return false;
        // Reject negative zero filename aliases; coordinates remain bounded only by filename/entry/byte budgets.
        return !path.contains(".-0.");
    }
    static Map<String, String> validateProperties(Role role, byte[] bytes, Map<String, String> explicit) throws IOException {
        try {
            ensure(explicit != null && !explicit.isEmpty() && explicit.size() <= 64, "EXPLICIT_PROPERTIES");
            Map<String, String> values = parseProperties(role, bytes);
            Set<String> known = role == Role.SOURCE ? SOURCE_KEYS : TARGET_KEYS;
            ensure(known.containsAll(values.keySet()) && known.containsAll(explicit.keySet()), "UNKNOWN_PROPERTY");
            for (var pin : explicit.entrySet()) ensure(Objects.equals(pin.getValue(), values.get(pin.getKey())), "FIXED_PROPERTY_DRIFT");
            return Map.copyOf(values);
        } catch (IOException | RuntimeException failure) { throw sanitized(failure); }
    }
    private static Map<String, String> parseProperties(Role role, byte[] bytes) throws IOException {
        ensure(role != null && bytes != null && bytes.length > 0 && bytes.length <= PROPERTIES_MAX, "PROPERTIES_SIZE");
        String text = role == Role.SOURCE ? new String(bytes, StandardCharsets.ISO_8859_1) : strictUtf8(bytes);
        ensure(text.indexOf('\0') < 0, "PROPERTIES_NUL");
        Properties properties = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("duplicate");
                return super.put(key, value);
            }
        };
        try { properties.load(new StringReader(text)); }
        catch (IllegalArgumentException failure) { throw new IOException("COMPOSE_OUTPUT_PROPERTIES_SYNTAX"); }
        ensure(properties.size() <= 64, "PROPERTY_COUNT");
        Map<String, String> values = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            ensure(key.length() <= 64 && value.length() <= 8192 && value.indexOf('\0') < 0, "PROPERTY_SIZE");
            values.put(key, value);
        }
        return Map.copyOf(values);
    }
    private static Set<String> targetKeys() {
        Set<String> keys = new HashSet<>(SOURCE_KEYS); keys.remove("max-build-height"); keys.remove("snooper-enabled");
        keys.addAll(List.of("enforce-secure-profile", "hide-online-players", "initial-disabled-packs", "initial-enabled-packs",
                "max-chained-neighbor-updates", "require-resource-pack", "resource-pack-prompt", "simulation-distance"));
        return Set.copyOf(keys);
    }
    private static long sizeLimit(Category category) {
        return switch (category) {
            case SERVER_PROPERTIES -> PROPERTIES_MAX;
            case EMPTY_PLAYER_LIST -> 8192;
            case CONFIGURATION, OBSERVATION -> 2L << 20;
            case LOG -> 8L << 20;
            case WORLD -> 64L << 20;
            default -> 0;
        };
    }
    private static void remember(Path path, List<Path> paths, Map<Object, Path> keys) throws IOException {
        var attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Object key = attrs.fileKey();
        if (key != null) ensure(keys.putIfAbsent(key, path) == null, "FILE_ALIAS");
        else for (Path prior : paths) ensure(!Files.isSameFile(prior, path), "FILE_ALIAS");
        paths.add(path);
    }
    private static byte[] boundedRead(Path path, int max) throws IOException {
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(max + 1); ensure(bytes.length <= max, "BYTE_LIMIT"); return bytes;
        }
    }
    private static String strictUtf8(byte[] bytes) throws IOException {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (java.nio.charset.CharacterCodingException bad) { throw new IOException("COMPOSE_OUTPUT_UTF8"); }
    }
    private static String name(Path root, Path path) { return root.relativize(path).toString().replace('\\', '/'); }
    private static Map<String, Object> flags(String status) {
        return Map.of("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_NATIVE_OUTPUT_LAYOUT", "status", status,
                "executionAllowed", false, "gameExecutionVerified", false, "nativeContentVerified", false,
                "installationAllowed", false, "nativeLaunchContractVerified", false);
    }
    private static void check(BooleanSupplier cancelled) throws IOException {
        ensure(cancelled != null, "CANCELLATION");
        boolean stop;
        try { stop = cancelled.getAsBoolean(); } catch (RuntimeException failure) { throw new IOException("COMPOSE_OUTPUT_CANCELLATION_CALLBACK"); }
        ensure(!stop && !Thread.currentThread().isInterrupted(), "CANCELLED");
    }
    private static void ensure(boolean value, String code) throws IOException { if (!value) throw new IOException("COMPOSE_OUTPUT_" + code); }
    private static IOException sanitized(Exception failure) {
        String code = failure.getMessage();
        if (code != null && code.endsWith("_CANCELLED")) return new IOException("COMPOSE_OUTPUT_CANCELLED");
        return new IOException(code != null && code.matches("COMPOSE_OUTPUT_[A-Z0-9_]+") ? code : "COMPOSE_OUTPUT_REJECTED");
    }
    public static void main(String[] args) throws IOException {
        try {
            ensure(args != null && args.length == 6, "USAGE_REPOSITORY_ROLE_RUNID_JAVAHOME_PLAN_RECEIPT");
            var request = new CompositionRunPlanner.Request(Role.valueOf(args[1]), args[2], Path.of(args[3]));
            var stage = CompositionStagedRunVerifier.verify(Path.of(args[0]), request, args[4], args[5], () -> false);
            var policy = prepare(stage, () -> false);
            System.out.print(CorpusJson.canonicalJson(Map.of("policy", policy.toTree(), "review", policy.reviewRecordedLayout(() -> false).toTree())));
        } catch (IOException | RuntimeException failure) {
            IOException clean = sanitized(failure); Map<String, Object> report = new LinkedHashMap<>(flags("BLOCKED"));
            report.put("error", clean.getMessage()); System.out.print(CorpusJson.canonicalJson(report)); throw clean;
        }
    }
}
