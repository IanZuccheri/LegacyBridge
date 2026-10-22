package dev.legacybridge.corpus;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Offline integrity/claim-chain verifier for a development-only corpus. It never loads a mod
 * or fetches a URL. PASSED remains the record author's historical attestation, not a test run
 * by this verifier. Missing local evidence and unverified URL content remain explicit.
 */
public final class CorpusVerifier {
    private static final int MAX_RECORDS = 256;
    private static final long MAX_EVIDENCE_BYTES = 256L * 1024 * 1024;
    private static final Map<String, String> STAGES = Map.of(
            "analyzable", "ANALYSIS", "convertible", "CONVERSION", "bytecodeValidated", "BYTECODE",
            "clientInitializes", "CLIENT_INITIALIZATION", "serverInitializes", "SERVER_INITIALIZATION",
            "clientBehavior", "CLIENT_BEHAVIOR", "serverBehavior", "SERVER_BEHAVIOR", "multiplayer", "MULTIPLAYER");
    private static final Set<String> STATUSES = Set.of("PASSED", "FAILED", "BLOCKED", "PARTIAL", "NOT_RUN");
    private static final Set<String> EVIDENCE_TYPES = Set.of("AUTHORIZATION", "ANALYSIS", "CONVERSION", "BYTECODE",
            "CLIENT_INITIALIZATION", "SERVER_INITIALIZATION", "CLIENT_BEHAVIOR", "SERVER_BEHAVIOR", "MULTIPLAYER", "SCREENSHOT");
    // An intentionally small SPDX-identifier subset, not an expression parser or a legal clearance list.
    private static final Set<String> SPDX_IDS = Set.of("MIT", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause", "ISC", "MPL-2.0",
            "GPL-2.0-only", "GPL-2.0-or-later", "GPL-3.0-only", "GPL-3.0-or-later", "LGPL-2.1-only", "LGPL-2.1-or-later",
            "LGPL-3.0-only", "LGPL-3.0-or-later", "CC0-1.0", "Unlicense");

    public record Summary(String corpusVersion, int records, int projectFixtures, int realArtifacts,
                          int distinctRealProjects, Map<String, Integer> realPassedStages,
                          Map<String, Integer> fixturePassedStages, List<String> missingLocalEvidence) {
        public Summary {
            realPassedStages = Map.copyOf(realPassedStages);
            fixturePassedStages = Map.copyOf(fixturePassedStages);
            missingLocalEvidence = List.copyOf(missingLocalEvidence);
        }
        public Map<String, Object> toTree() {
            var result = new TreeMap<String, Object>();
            result.put("schemaVersion", 1); result.put("reportType", "LEGACYBRIDGE_CORPUS_VERIFICATION");
            result.put("corpusVersion", corpusVersion); result.put("records", records);
            result.put("projectFixtures", projectFixtures); result.put("realArtifacts", realArtifacts);
            result.put("distinctRealProjects", distinctRealProjects); result.put("realPassedStages", realPassedStages);
            result.put("fixturePassedStages", fixturePassedStages); result.put("missingLocalEvidence", missingLocalEvidence);
            result.put("interpretation", "Verified record structure and available local evidence hashes; stage counts are historical claims, not newly executed tests or compatibility rates.");
            result.put("urlContentVerified", false); result.put("authorizationLegallyCertified", false);
            return result;
        }
    }

    public Summary verify(Path corpusRoot) throws IOException {
        Path root = CorpusJson.directory(corpusRoot);
        Path project = CorpusJson.directory(root.getParent());
        Map<String, Object> catalog = CorpusJson.read(root.resolve("catalog.json"));
        keys(catalog, Set.of("schemaVersion", "corpusVersion", "records"), "catalog");
        schema(catalog);
        String version = text(catalog.get("corpusVersion"), "corpusVersion", 128);
        List<?> records = list(catalog.get("records"), "catalog records", MAX_RECORDS);
        Set<String> paths = new HashSet<>(), ids = new HashSet<>(), hashes = new HashSet<>(), realProjects = new HashSet<>();
        var realPassed = counters(); var fixturePassed = counters();
        List<String> missing = new ArrayList<>();
        int fixtures = 0, real = 0;
        for (Object recordPath : records) {
            String relative = text(recordPath, "record path", 1024);
            require(paths.add(relative), "Duplicate record path");
            Path path = resolve(root, relative, false);
            require(path.getFileName().toString().endsWith(".json"), "Record path must identify JSON");
            Map<String, Object> record = CorpusJson.read(path);
            RecordSummary summary = verifyRecord(project, record, missing);
            require(ids.add(summary.id), "Duplicate record id");
            require(hashes.add(summary.sha256), "Duplicate artifact SHA-256 across records");
            Map<String, Integer> counts;
            if (summary.real) { real++; realProjects.add(summary.projectKey); counts = realPassed; }
            else { fixtures++; counts = fixturePassed; }
            summary.passedStages.forEach(stage -> counts.compute(stage, (key, count) -> count + 1));
        }
        return new Summary(version, records.size(), fixtures, real, realProjects.size(), realPassed, fixturePassed,
                missing.stream().distinct().sorted().toList());
    }

    private RecordSummary verifyRecord(Path project, Map<String, Object> record, List<String> missing) throws IOException {
        Set<String> required = Set.of("schemaVersion", "id", "kind", "projectKey", "version", "artifact", "authorization",
                "source", "dependencies", "detectedFeatures", "blockers", "output", "stages", "evidence");
        Set<String> presentCore = new HashSet<>(record.keySet());
        presentCore.removeAll(Set.of("structuredMetadata", "analysisSummary"));
        require(presentCore.equals(required), "Unexpected or missing fields in record");
        for (String optional : List.of("structuredMetadata", "analysisSummary")) {
            if (record.containsKey(optional)) object(record.get(optional), optional);
        }
        schema(record);
        String id = identifier(record.get("id"), "record id");
        String kind = choice(record.get("kind"), Set.of("PROJECT_FIXTURE", "REAL_MOD"), "kind");
        boolean real = kind.equals("REAL_MOD");
        String projectKey = text(record.get("projectKey"), "projectKey", 256);
        text(record.get("version"), "version", 128);
        Map<String, Object> artifact = object(record.get("artifact"), "artifact");
        keys(artifact, Set.of("fileName", "sha256", "sizeBytes"), "artifact");
        String fileName = text(artifact.get("fileName"), "artifact filename", 256);
        require(fileName.matches("[A-Za-z0-9][A-Za-z0-9._+ -]{0,250}\\.jar") && !fileName.contains(".."), "Unsafe artifact filename");
        String sourceSha = sha(artifact.get("sha256"), false);
        require(integer(artifact.get("sizeBytes"), "artifact size") > 0, "Artifact size must be positive");
        Map<String, Object> source = object(record.get("source"), "source");
        keys(source, Set.of("loader", "minecraftVersions", "forgeBuild", "forgeVersionEvidence"), "source");
        text(source.get("loader"), "source loader", 64);
        require(!strings(source.get("minecraftVersions"), "minecraftVersions", 32).isEmpty(), "Minecraft version evidence is required");
        if (source.get("forgeBuild") != null) text(source.get("forgeBuild"), "forgeBuild", 64);
        text(source.get("forgeVersionEvidence"), "forgeVersionEvidence", 2048);
        strings(record.get("dependencies"), "dependencies", 256);
        strings(record.get("detectedFeatures"), "detectedFeatures", 256);
        strings(record.get("blockers"), "blockers", 256);
        Map<String, Object> output = object(record.get("output"), "output");
        keys(output, Set.of("sha256", "status"), "output");
        String targetSha = sha(output.get("sha256"), true);
        String outputStatus = choice(output.get("status"), Set.of("CONVERTED", "BLOCKED", "FAILED", "NOT_RUN"), "output status");
        require(outputStatus.equals("CONVERTED") == (targetSha != null), "Only CONVERTED output may have an output hash, and it requires one");
        Map<String, Evidence> evidence = evidence(project, record.get("evidence"), id, sourceSha, targetSha, missing);
        authorization(record.get("authorization"), real, evidence);
        Map<String, Object> stages = object(record.get("stages"), "stages");
        keys(stages, STAGES.keySet(), "stages");
        Set<String> passed = new HashSet<>();
        for (var entry : STAGES.entrySet()) {
            Map<String, Object> stage = object(stages.get(entry.getKey()), "stage " + entry.getKey());
            keys(stage, Set.of("status", "evidence"), "stage");
            String status = choice(stage.get("status"), STATUSES, "stage status");
            List<String> references = references(stage.get("evidence"), evidence);
            if (status.equals("NOT_RUN")) require(references.isEmpty(), "NOT_RUN stage cannot cite completed evidence");
            if (status.equals("PASSED")) {
                passed.add(entry.getKey());
                require(references.stream().map(evidence::get).anyMatch(proof -> proof.type.equals(entry.getValue())
                                && sourceSha.equals(proof.sourceSha)
                                && (entry.getKey().equals("analyzable") || targetSha != null && targetSha.equals(proof.targetSha))),
                        "PASSED stage requires matching typed, source/output-bound evidence: " + entry.getKey());
            }
        }
        if (passed.contains("convertible")) require(passed.contains("analyzable") && outputStatus.equals("CONVERTED"), "Conversion proof requires analysis and converted output");
        if (passed.contains("bytecodeValidated")) require(passed.contains("convertible"), "Bytecode proof requires a successful conversion");
        if (outputStatus.equals("CONVERTED")) require(passed.containsAll(Set.of("analyzable", "convertible", "bytecodeValidated")), "CONVERTED output requires the complete static proof chain");
        for (String stage : passed) {
            if (!Set.of("analyzable", "convertible", "bytecodeValidated").contains(stage))
                require(targetSha != null && passed.containsAll(Set.of("convertible", "bytecodeValidated")), "Post-conversion proof requires exact output and static validation");
        }
        if (passed.contains("clientBehavior")) require(passed.contains("clientInitializes"), "Client behavior requires client initialization");
        if (passed.contains("serverBehavior")) require(passed.contains("serverInitializes"), "Server behavior requires server initialization");
        if (passed.contains("multiplayer")) require(passed.containsAll(Set.of("clientInitializes", "serverInitializes")), "Multiplayer requires both initializations");
        return new RecordSummary(id, projectKey, sourceSha, real, passed);
    }

    private Map<String, Evidence> evidence(Path project, Object value, String recordId, String sourceSha,
                                            String targetSha, List<String> missing) throws IOException {
        Map<String, Evidence> result = new LinkedHashMap<>();
        for (Object item : list(value, "evidence", 256)) {
            Map<String, Object> proof = object(item, "evidence entry");
            Set<String> fields = Set.of("id", "type", "sourceSha256", "targetSha256", "sha256", "path");
            if (proof.containsKey("url")) fields = Set.of("id", "type", "sourceSha256", "targetSha256", "sha256", "url");
            keys(proof, fields, "evidence entry (exactly one path or url)");
            String id = identifier(proof.get("id"), "evidence id");
            String type = choice(proof.get("type"), EVIDENCE_TYPES, "evidence type");
            String source = sha(proof.get("sourceSha256"), type.equals("AUTHORIZATION"));
            String target = sha(proof.get("targetSha256"), true);
            String hash = sha(proof.get("sha256"), false);
            if (!type.equals("AUTHORIZATION")) require(sourceSha.equals(source), "Evidence is bound to a different source artifact");
            if (target != null) require(target.equals(targetSha), "Evidence is bound to a different or absent converted artifact");
            boolean url = proof.containsKey("url");
            if (url) validateUrl(text(proof.get("url"), "evidence URL", 2048));
            else {
                String relative = text(proof.get("path"), "evidence path", 1024);
                Path local = resolve(project, relative, true);
                if (Files.exists(local, LinkOption.NOFOLLOW_LINKS)) require(hash.equals(hash(local)), "Evidence file SHA-256 mismatch: " + recordId + "/" + id);
                else missing.add(recordId + "/" + id + ":" + relative);
            }
            require(result.put(id, new Evidence(type, source, target, url)) == null, "Duplicate evidence id within record");
        }
        return result;
    }

    private void authorization(Object value, boolean real, Map<String, Evidence> evidence) throws IOException {
        Map<String, Object> authorization = object(value, "authorization");
        keys(authorization, Set.of("basis", "license", "redistributeBinary", "evidence"), "authorization");
        String basis = choice(authorization.get("basis"), Set.of("PROJECT_LOCAL_REQUEST", "SPDX_LICENSE"), "authorization basis");
        String license = text(authorization.get("license"), "license", 128);
        require(Boolean.FALSE.equals(authorization.get("redistributeBinary")), "Corpus never grants binary redistribution");
        List<String> refs = references(authorization.get("evidence"), evidence);
        require(!refs.isEmpty() && refs.stream().allMatch(id -> evidence.get(id).type.equals("AUTHORIZATION")), "Authorization requires authorization evidence references");
        if (basis.equals("SPDX_LICENSE")) require(SPDX_IDS.contains(license), "SPDX identifier is outside the explicitly supported identifier subset");
        if (real) require(basis.equals("SPDX_LICENSE") && refs.stream().anyMatch(id -> evidence.get(id).url), "REAL_MOD requires SPDX and a reviewable HTTPS authorization source");
    }

    private static void validateUrl(String text) throws IOException {
        try {
            URI url = new URI(text);
            require("https".equals(url.getScheme()) && url.getHost() != null && !url.getHost().isBlank()
                            && url.getRawUserInfo() == null && url.getRawQuery() == null && url.getRawFragment() == null
                            && (url.getPort() == -1 || url.getPort() == 443),
                    "Evidence URL must be HTTPS without credentials, query, fragment or nonstandard port");
        } catch (URISyntaxException invalid) { throw new IOException("Malformed evidence URL", invalid); }
    }

    static Path resolve(Path root, String relative, boolean allowMissing) throws IOException {
        require(relative.length() <= 1024 && !relative.startsWith("/") && !relative.contains("\\") && !relative.contains(":"), "Unsafe relative corpus path");
        String[] parts = relative.split("/", -1);
        for (String part : parts) require(!part.isEmpty() && !part.equals(".") && !part.equals("..")
                        && !part.endsWith(".") && !part.endsWith(" ") && part.chars().noneMatch(c -> c < 32 || c == 127 || "<>\"|?*".indexOf(c) >= 0)
                        && !part.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?"),
                "Unsafe relative corpus path component");
        Path target = root.resolve(relative).normalize();
        require(target.startsWith(root) && !target.equals(root), "Corpus path escapes its root");
        CorpusJson.checkParents(target);
        for (Path parent = target.getParent(); parent != null && parent.startsWith(root); parent = parent.getParent()) {
            if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) require(Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS), "Evidence parent is not a directory");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) CorpusJson.regular(target);
        else require(allowMissing, "Referenced corpus record does not exist");
        return target;
    }

    private static String hash(Path path) throws IOException {
        CorpusJson.regular(path);
        if (Files.size(path) > MAX_EVIDENCE_BYTES) throw new IOException("Evidence file exceeds the 256 MiB hashing limit");
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = new byte[65536]; long total = 0;
                for (int count; (count = input.read(bytes)) != -1;) {
                    if ((total += count) > MAX_EVIDENCE_BYTES) throw new IOException("Evidence grew beyond the hashing limit");
                    hash.update(bytes, 0, count);
                }
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static List<String> references(Object value, Map<String, Evidence> evidence) throws IOException {
        List<String> refs = strings(value, "evidence references", 256);
        for (String ref : refs) require(evidence.containsKey(ref), "Unknown evidence reference");
        return refs;
    }
    private static Map<String, Integer> counters() { var result = new TreeMap<String, Integer>(); STAGES.keySet().forEach(key -> result.put(key, 0)); return result; }
    private static void keys(Map<String, Object> value, Set<String> expected, String name) throws IOException { require(value.keySet().equals(expected), "Unexpected or missing fields in " + name); }
    private static void schema(Map<String, Object> value) throws IOException { require(integer(value.get("schemaVersion"), "schemaVersion") == 1, "Unsupported corpus schema version"); }
    private static long integer(Object value, String name) throws IOException {
        require(value instanceof Number, name + " must be a JSON number");
        try { return new java.math.BigDecimal(String.valueOf(value)).longValueExact(); }
        catch (NumberFormatException | ArithmeticException invalid) { throw new IOException(name + " must be an exact bounded integer", invalid); }
    }
    private static String sha(Object value, boolean nullable) throws IOException {
        if (value == null && nullable) return null;
        require(value instanceof String text && text.matches("[0-9a-f]{64}"), "Expected lowercase SHA-256"); return (String) value;
    }
    private static String identifier(Object value, String name) throws IOException { String result = text(value, name, 128); require(result.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"), "Invalid " + name); return result; }
    private static String choice(Object value, Set<String> choices, String name) throws IOException { String result = text(value, name, 128); require(choices.contains(result), "Unknown " + name); return result; }
    private static String text(Object value, String name, int limit) throws IOException {
        require(value instanceof String text && !text.isBlank() && text.length() <= limit && text.chars().noneMatch(c -> c < 32 || c == 127), "Invalid or overlong " + name); return (String) value;
    }
    private static List<String> strings(Object value, String name, int limit) throws IOException {
        var result = new ArrayList<String>(); var unique = new HashSet<String>();
        for (Object item : list(value, name, limit)) { String text = text(item, name, 2048); require(unique.add(text), "Duplicate value in " + name); result.add(text); }
        return result;
    }
    private static List<?> list(Object value, String name, int limit) throws IOException { require(value instanceof List<?> items && items.size() <= limit, "Invalid or overlong " + name); return (List<?>) value; }
    private static Map<String, Object> object(Object value, String name) throws IOException {
        require(value instanceof Map<?, ?>, "Expected object: " + name); @SuppressWarnings("unchecked") var result = (Map<String, Object>) value; return result;
    }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
    private record Evidence(String type, String sourceSha, String targetSha, boolean url) { }
    private record RecordSummary(String id, String projectKey, String sha256, boolean real, Set<String> passedStages) { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[0].equals("verify")) throw new IllegalArgumentException("Usage: legacy-corpus verify <corpusDir>");
        CorpusLock.verify(Path.of(args[1]));
        System.out.print(CorpusJson.canonicalJson(new CorpusVerifier().verify(Path.of(args[1])).toTree()));
    }
}
