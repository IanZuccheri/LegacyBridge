package dev.legacybridge.corpus;

import dev.legacybridge.converter.config.BoundedJsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only developer evidence checker, never a conversion or installation gate.
 * Checks recorded assertions, not whether their author actually ran Minecraft.
 * Use an independently reviewed index on a quiescent local evidence tree.
 */
public final class CompositionObservationVerifier {
    public static final String ORACLE = "composed-foil-cooking-v1";
    private static final String MOD = "legacybridge_composed";
    private static final String TOKEN = MOD + ":count_token";
    private static final String RUNTIME_VERSION = "0.1.1-SNAPSHOT";
    private static final long MAX_FILE = 128L << 20, MAX_TOTAL = 512L << 20;
    private static final int MAX_LOG = 8 << 20;

    public enum State { NOT_RUN, RECORDED_MATCH_ONLY }
    public record Summary(State status, int files, long bytes, String sourceSha256, String candidateSha256) {
        public Map<String, Object> toTree() {
            return Map.of("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_OBSERVATION_CHECK",
                    "status", status.name(), "filesChecked", files, "bytesChecked", bytes,
                    "sourceSha256", sourceSha256, "candidateSha256", candidateSha256,
                    "installationAllowed", false, "gameExecutionVerified", false,
                    "pending", List.of("INDEPENDENT_NATIVE_EVIDENCE_REVIEW", "CLIENT_ASSETS_TRANSLATIONS",
                            "RESOURCE_RELOAD", "MULTIPLAYER", "COMPOSE_01_ACCEPTANCE"));
        }
    }

    private record Artifact(Path path, String sha256, long bytes) { }
    private final List<Artifact> files = new ArrayList<>();
    private long total;

    /** A fresh invocation has independent budgets and no retained mutable verification state. */
    public static Summary verify(Path repository, String indexPath) throws IOException {
        return new CompositionObservationVerifier().check(CorpusJson.directory(repository), indexPath);
    }

    private Summary check(Path root, String indexPath) throws IOException {
        Map<String, Object> index = CorpusJson.read(safe(root, indexPath));
        keys(index, "schemaVersion", "reportType", "oracle", "artifacts", "runs");
        eq(index.get("schemaVersion"), 1, "INDEX_SCHEMA");
        eq(index.get("reportType"), "LEGACY_COMPOSITION_OBSERVATION_INDEX", "INDEX_TYPE");
        eq(index.get("oracle"), ORACLE, "ORACLE");
        Map<String, Object> artifacts = object(index.get("artifacts"));
        keys(artifacts, "original", "candidate", "proof", "receipt", "runtime");
        Artifact original = artifact(root, artifacts.get("original"));
        Artifact candidate = artifact(root, artifacts.get("candidate"));
        Artifact proofFile = artifact(root, artifacts.get("proof"));
        Artifact receiptFile = artifact(root, artifacts.get("receipt"));
        Artifact runtime = artifact(root, artifacts.get("runtime"));
        Path run = candidate.path.getParent();
        require(run.getFileName().toString().matches("run-[a-z0-9][a-z0-9-]{0,40}")
                && run.getParent() != null && run.getParent().getFileName() != null
                && run.getParent().getFileName().toString().equals("composition-qa"), "QA_HANDOFF");
        require(candidate.path.getFileName().toString().equals("candidate.jar")
                && proofFile.path.equals(run.resolve("proof.json"))
                && receiptFile.path.equals(run.resolve("receipt.json")), "QA_HANDOFF");
        try (var paths = Files.list(run)) {
            require(paths.limit(4).map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet())
                    .equals(Set.of("candidate.jar", "proof.json", "receipt.json")), "QA_ENTRY_SET");
        }
        Map<String, Object> receipt = json(receiptFile);
        keys(receipt, "schemaVersion", "reportType", "status", "sourceSha256", "candidateSha256",
                "proofSha256", "files", "installationAllowed", "runtimeValidation");
        eq(receipt.get("schemaVersion"), 1, "RECEIPT_SCHEMA");
        eq(receipt.get("reportType"), "LEGACY_COMPOSITION_QA_EXPORT", "RECEIPT_TYPE");
        eq(receipt.get("status"), "EXPORTED_FOR_QA", "RECEIPT_STATUS");
        eq(receipt.get("sourceSha256"), original.sha256, "RECEIPT_SOURCE");
        eq(receipt.get("candidateSha256"), candidate.sha256, "RECEIPT_CANDIDATE");
        eq(receipt.get("proofSha256"), proofFile.sha256, "RECEIPT_PROOF");
        eq(receipt.get("files"), List.of("candidate.jar", "proof.json", "receipt.json"), "RECEIPT_FILES");
        eq(receipt.get("installationAllowed"), false, "RECEIPT_AUTHORITY");
        eq(receipt.get("runtimeValidation"), "NOT_RUN", "RECEIPT_RUNTIME");
        Map<String, Object> proof = json(proofFile);
        eq(proof.get("schemaVersion"), 1, "PROOF_SCHEMA");
        eq(proof.get("reportType"), "LEGACY_COMPOSITION_CANDIDATE", "PROOF_TYPE");
        eq(proof.get("status"), "CANDIDATE_VALIDATED", "PROOF_STATUS");
        eq(proof.get("candidateIdentity"), "composed-logic-recipes-v1", "PROOF_IDENTITY");
        eq(proof.get("modId"), MOD, "PROOF_MOD");
        eq(proof.get("modVersion"), "1.0.0", "PROOF_VERSION");
        eq(object(proof.get("input")).get("sha256"), original.sha256, "PROOF_SOURCE");
        eq(object(proof.get("candidate")).get("sha256"), candidate.sha256, "PROOF_CANDIDATE");
        eq(object(proof.get("candidate")).get("bytes"), candidate.bytes, "PROOF_SIZE");
        eq(proof.get("runtimeRequired"), "legacybridge_runtime", "PROOF_RUNTIME");
        eq(proof.get("runtimeVersion"), RUNTIME_VERSION, "PROOF_RUNTIME_VERSION");
        eq(proof.get("targetMinecraft"), "1.20.1", "PROOF_BASELINE");
        eq(proof.get("targetForge"), "47.4.23", "PROOF_BASELINE");
        for (String key : List.of("publicationAllowed", "bytecodeExecuted", "transformationsCommitted"))
            eq(proof.get(key), false, "PROOF_AUTHORITY");
        eq(proof.get("runtimeValidation"), "NOT_RUN", "PROOF_NATIVE");
        eq(proof.get("preservedClassCount"), 4, "PROOF_CLASS_COUNT");
        eq(proof.get("preservedMethodCount"), 10, "PROOF_METHOD_COUNT");
        eq(proof.get("replacedClassCount"), 0, "PROOF_REPLACEMENT");
        Map<String, Object> bytecode = object(proof.get("bytecodeValidation"));
        eq(bytecode.get("status"), "PASSED_STATIC_REOPENED", "PROOF_BYTECODE");
        eq(bytecode.get("modClassesLoaded"), false, "PROOF_BYTECODE");
        eq(bytecode.get("offlineLinkageVerified"), true, "PROOF_BYTECODE");
        Map<String, Object> archive = object(proof.get("candidateArchiveValidation"));
        eq(archive.get("status"), "PASSED_REOPENED", "PROOF_ARCHIVE");
        eq(archive.get("ownershipAndHashes"), true, "PROOF_ARCHIVE");
        eq(archive.get("centralDirectoryAndCrc"), true, "PROOF_ARCHIVE");
        for (String phase : List.of("sourceOfflineLinkage", "targetOfflineLinkage")) {
            Map<String, Object> linkage = object(proof.get(phase));
            eq(linkage.get("status"), "PASSED_SCOPED", "PROOF_LINKAGE");
            eq(linkage.get("classesExecuted"), false, "PROOF_LINKAGE");
        }
        State state = State.NOT_RUN;
        if (index.get("runs") != null) {
            Map<String, Object> runs = object(index.get("runs"));
            keys(runs, "source", "target");
            String sourceRun = observation(root, object(runs.get("source")), "SOURCE", original, runtime, receiptFile);
            String targetRun = observation(root, object(runs.get("target")), "TARGET", candidate, runtime, receiptFile);
            require(!sourceRun.equals(targetRun), "RUN_REUSE");
            state = State.RECORDED_MATCH_ONLY;
        }
        // Detect ordinary drift while reading. This is not a sandbox against a racing local writer.
        for (Artifact file : files) hash(file);
        return new Summary(state, files.size(), total, original.sha256, candidate.sha256);
    }

    private String observation(Path root, Map<String, Object> run, String role, Artifact fixture,
                               Artifact runtime, Artifact receipt) throws IOException {
        keys(run, "id", "probe", "observation", "log");
        String id = text(run.get("id"));
        require(id.matches("run-[a-z0-9][a-z0-9-]{0,40}"), "RUN_ID");
        Artifact probe = artifact(root, run.get("probe"));
        Artifact trace = artifact(root, run.get("observation"));
        Artifact log = artifact(root, run.get("log"));
        Map<String, Object> value = json(trace);
        keys(value, "schemaVersion", "reportType", "oracle", "runId", "role", "status", "environment",
                "before", "after", "loadedMods", "behavior");
        eq(value.get("schemaVersion"), 1, "TRACE_SCHEMA");
        eq(value.get("reportType"), "LEGACY_COMPOSITION_OBSERVATION", "TRACE_TYPE");
        eq(value.get("oracle"), ORACLE, "TRACE_ORACLE");
        eq(value.get("runId"), id, "TRACE_RUN");
        eq(value.get("role"), role, "TRACE_ROLE");
        eq(value.get("status"), "CAPTURED", "TRACE_STATUS");
        boolean source = role.equals("SOURCE");
        Map<String, Object> environment = object(value.get("environment"));
        keys(environment, "minecraft", "forge", "javaMajor", "physicalSide", "logicalSide", "serverThread");
        eq(environment.get("minecraft"), source ? "1.16.5" : "1.20.1", "BASELINE");
        eq(environment.get("forge"), source ? "36.2.42" : "47.4.23", "BASELINE");
        // Both controlled QA installations use Java 17, not an untested Java 8 runtime.
        eq(environment.get("javaMajor"), 17, "JAVA_BASELINE");
        eq(environment.get("physicalSide"), "DEDICATED_SERVER", "SIDE");
        eq(environment.get("logicalSide"), "SERVER", "SIDE");
        eq(environment.get("serverThread"), true, "THREAD");
        for (String phase : List.of("before", "after")) {
            Map<String, Object> bindings = object(value.get(phase));
            keys(bindings, "fixtureSha256", "probeSha256", "runtimeSha256", "receiptSha256");
            eq(bindings.get("fixtureSha256"), fixture.sha256, "TRACE_FIXTURE");
            eq(bindings.get("probeSha256"), probe.sha256, "TRACE_PROBE");
            eq(bindings.get("runtimeSha256"), source ? null : runtime.sha256, "TRACE_RUNTIME");
            eq(bindings.get("receiptSha256"), source ? null : receipt.sha256, "TRACE_RECEIPT");
        }
        Map<String, Object> mods = object(value.get("loadedMods"));
        Set<String> expected = source ? Set.of("minecraft", "forge", MOD, "legacybridge_composition_probe")
                : Set.of("minecraft", "forge", MOD, "legacybridge_composition_probe", "legacybridge_runtime");
        require(mods.keySet().equals(expected), "LOADED_COHORT");
        eq(mods.get("minecraft"), environment.get("minecraft"), "LOADED_BASELINE");
        eq(mods.get("forge"), environment.get("forge"), "LOADED_BASELINE");
        eq(mods.get(MOD), "1.0.0", "LOADED_FIXTURE");
        eq(mods.get("legacybridge_composition_probe"), "1.0.0", "LOADED_PROBE");
        if (!source) eq(mods.get("legacybridge_runtime"), RUNTIME_VERSION, "LOADED_RUNTIME");
        behavior(object(value.get("behavior")));
        String contents = utf8(capture(log, MAX_LOG));
        String marker = "LEGACYBRIDGE_COMPOSE_CAPTURE_OK run=" + id + " sha256=" + trace.sha256;
        int successes = 0;
        var lines = contents.lines().iterator();
        int lineCount = 0;
        while (lines.hasNext()) {
            require(++lineCount <= 100_000, "LOG_LINE_BUDGET");
            String line = lines.next();
            int position = line.indexOf("LEGACYBRIDGE_COMPOSE_");
            if (position >= 0) {
                eq(line.substring(position), marker, "LOG_MARKER");
                successes++;
            }
        }
        require(successes == 1, "LOG_COMPLETION");
        return id;
    }

    private static void behavior(Map<String, Object> value) throws IOException {
        keys(value, "registryId", "itemClass", "maxStackSize", "initialCalls", "foil", "enchanted", "cooking");
        eq(value.get("registryId"), TOKEN, "REGISTRY_ID");
        eq(value.get("itemClass"), "dev.legacybridge.fixture.composed.ComposedItem", "OWNED_CLASS");
        eq(value.get("maxStackSize"), 16, "STACK_SIZE");
        eq(value.get("initialCalls"), 0, "INITIAL_CALLS");
        List<?> foil = list(value.get("foil"), 4);
        for (int i = 0; i < 4; i++) {
            Map<String, Object> row = object(foil.get(i));
            keys(row, "count", "foil", "callsBefore", "callsAfter");
            eq(row.get("count"), i + 1, "FOIL_INPUT");
            eq(row.get("foil"), i % 2 == 0, "FOIL_RESULT");
            eq(row.get("callsBefore"), i, "HELPER_CALLS");
            eq(row.get("callsAfter"), i + 1, "HELPER_CALLS");
        }
        Map<String, Object> enchanted = object(value.get("enchanted"));
        keys(enchanted, "count", "enchantment", "level", "isEnchanted", "foil", "callsBefore", "callsAfter");
        eq(enchanted.get("count"), 2, "SUPER_INPUT");
        eq(enchanted.get("enchantment"), "minecraft:unbreaking", "SUPER_INPUT");
        eq(enchanted.get("level"), 1, "SUPER_INPUT");
        eq(enchanted.get("isEnchanted"), true, "SUPER_INPUT");
        eq(enchanted.get("foil"), true, "SUPER_RESULT");
        eq(enchanted.get("callsBefore"), 4, "SUPER_SHORT_CIRCUIT");
        eq(enchanted.get("callsAfter"), 4, "SUPER_SHORT_CIRCUIT");
        Map<String, Object> cooking = object(value.get("cooking"));
        keys(cooking, "recipeId", "recipeType", "ingredientTag", "tagItems", "recipeTicks", "recipeExperience", "samples");
        eq(cooking.get("recipeId"), TOKEN, "RECIPE_ID");
        eq(cooking.get("recipeType"), "minecraft:smelting", "RECIPE_TYPE");
        eq(cooking.get("ingredientTag"), MOD + ":token_material", "TAG_ID");
        eq(cooking.get("tagItems"), List.of("minecraft:iron_ingot"), "TAG_CONTENTS");
        eq(cooking.get("recipeTicks"), 40, "RECIPE_TICKS");
        eq(cooking.get("recipeExperience"), 0, "RECIPE_XP");
        // Each actual native furnace tick is sampled, including 80 post-completion ticks.
        List<?> samples = list(cooking.get("samples"), 121);
        for (int tick = 0; tick <= 120; tick++) {
            Map<String, Object> sample = object(samples.get(tick));
            keys(sample, "tick", "inputId", "inputCount", "fuelId", "fuelCount", "outputId", "outputCount");
            eq(sample.get("tick"), tick, "TICK_ORDER");
            eq(sample.get("inputId"), tick < 40 ? "minecraft:iron_ingot" : "minecraft:air", "COOK_INPUT");
            eq(sample.get("inputCount"), tick < 40 ? 1 : 0, "COOK_CONSUMPTION");
            eq(sample.get("fuelId"), tick == 0 ? "minecraft:coal" : "minecraft:air", "COOK_FUEL");
            eq(sample.get("fuelCount"), tick == 0 ? 1 : 0, "COOK_FUEL");
            eq(sample.get("outputId"), tick < 40 ? "minecraft:air" : TOKEN, "COOK_OUTPUT");
            eq(sample.get("outputCount"), tick < 40 ? 0 : 1, "COOK_OUTPUT");
        }
    }

    private Artifact artifact(Path root, Object raw) throws IOException {
        Map<String, Object> entry = object(raw);
        keys(entry, "path", "sha256", "bytes");
        Path path = safe(root, text(entry.get("path")));
        String digest = text(entry.get("sha256"));
        require(digest.matches("[a-f0-9]{64}"), "HASH_FORMAT");
        long bytes = number(entry.get("bytes"));
        require(bytes > 0 && bytes <= MAX_FILE && total + bytes <= MAX_TOTAL, "BYTE_BUDGET");
        for (Artifact previous : files) require(!Files.isSameFile(path, previous.path), "FILE_ALIAS");
        Artifact file = new Artifact(path, digest, bytes);
        hash(file); files.add(file); total += bytes;
        return file;
    }

    private static void hash(Artifact file) throws IOException {
        CorpusJson.regular(file.path);
        require(Files.size(file.path) == file.bytes, "FILE_SIZE");
        MessageDigest digest = sha256();
        long count = 0;
        try (var stream = Files.newInputStream(file.path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536];
            for (int read; (read = stream.read(buffer)) != -1;) {
                count += read; require(count <= file.bytes, "FILE_GROWTH"); digest.update(buffer, 0, read);
            }
        }
        require(count == file.bytes && HexFormat.of().formatHex(digest.digest()).equals(file.sha256), "FILE_HASH");
    }

    private static byte[] capture(Artifact file, int maximum) throws IOException {
        require(file.bytes <= maximum, "CAPTURE_BUDGET");
        byte[] bytes;
        CorpusJson.regular(file.path);
        try (var stream = Files.newInputStream(file.path, LinkOption.NOFOLLOW_LINKS)) { bytes = stream.readNBytes(maximum + 1); }
        require(bytes.length == file.bytes && HexFormat.of().formatHex(sha256().digest(bytes)).equals(file.sha256), "CAPTURE_HASH");
        return bytes;
    }

    private static Map<String, Object> json(Artifact file) throws IOException {
        try { return object(BoundedJsonParser.parse(utf8(capture(file, CorpusJson.MAX_BYTES)))); }
        catch (BoundedJsonParser.JsonSyntaxException invalid) { throw new IOException("OBS_JSON: " + invalid.getMessage(), invalid); }
    }

    private static String utf8(byte[] bytes) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static MessageDigest sha256() throws IOException {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IOException("OBS_SHA256", impossible); }
    }

    private static Path safe(Path root, String relative) throws IOException {
        require(relative.length() <= 1024 && relative.matches("[A-Za-z0-9_./-]+") && !relative.startsWith("/"), "PATH");
        for (String part : relative.split("/", -1)) require(!part.isEmpty() && !part.equals(".")
                && !part.equals("..") && !part.endsWith("."), "PATH");
        return CorpusJson.regular(root.resolve(relative));
    }
    private static String text(Object value) throws IOException {
        require(value instanceof String, "TEXT"); return (String) value;
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) throws IOException {
        require(value instanceof Map<?, ?>, "OBJECT"); return (Map<String, Object>) value;
    }
    private static List<?> list(Object value, int size) throws IOException {
        require(value instanceof List<?> && ((List<?>) value).size() == size, "ARRAY_SIZE"); return (List<?>) value;
    }
    private static long number(Object value) throws IOException {
        require(value instanceof BigDecimal, "NUMBER");
        try { return ((BigDecimal) value).longValueExact(); }
        catch (ArithmeticException invalid) { throw new IOException("OBS_NUMBER", invalid); }
    }
    private static void keys(Map<String, Object> value, String... expected) throws IOException {
        require(value.keySet().equals(Set.of(expected)), "KEYS");
    }
    private static void eq(Object actual, Object expected, String code) throws IOException {
        if (expected instanceof Number) require(actual instanceof BigDecimal
                && ((BigDecimal) actual).compareTo(new BigDecimal(expected.toString())) == 0, code);
        else require(java.util.Objects.equals(actual, expected), code);
    }
    private static void require(boolean condition, String code) throws IOException {
        if (!condition) throw new IOException("OBS_" + code);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IOException("Usage: CompositionObservationVerifier repository index-relative-path");
        try { System.out.print(CorpusJson.canonicalJson(verify(Path.of(args[0]), args[1]).toTree())); }
        catch (IOException invalid) {
            System.out.print(CorpusJson.canonicalJson(Map.of("schemaVersion", 1,
                    "reportType", "LEGACY_COMPOSITION_OBSERVATION_CHECK", "status", "BLOCKED",
                    "installationAllowed", false, "gameExecutionVerified", false,
                    "error", Map.of("code", "OBSERVATION_REJECTED", "reason", invalid.getMessage()))));
            throw invalid;
        }
    }
}
