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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only integrity checks for the development prompt contract, never a conversion gate.
 * ACCEPTED is the author's recorded disposition: existence of an evidence path does not prove
 * its truth, freshness, native execution or artifact identity. Evidence contents are not read.
 * This tool never downloads, runs mods, changes a status, advances a cursor or skips a task.
 */
public final class PromptContractVerifier {
    static final int MAX_STATUS_BYTES = 512 * 1024;
    static final int MAX_PROMPT_BYTES = 256 * 1024;
    static final int MAX_README_BYTES = 512 * 1024;
    static final int MAX_CUTS = 512;
    private static final Set<String> STATUSES = Set.of("ACCEPTED", "PARTIAL", "BLOCKED", "NOT_STARTED",
            "MANUAL", "UNSUPPORTED", "DEFERRED");
    private static final Set<String> EXECUTABLE_UNRESOLVED = Set.of("PARTIAL", "BLOCKED", "NOT_STARTED");
    private static final Map<String, String> BASELINE = Map.of("sourceMinecraft", "1.16.5", "sourceForge", "36.2.42",
            "targetMinecraft", "1.20.1", "targetForge", "47.4.23", "runtimeModId", "legacybridge_runtime",
            "runtimeVersion", "0.1.1-SNAPSHOT");

    public record Summary(String contractVersion, String currentPrompt, String currentCut,
                          int promptCount, int cutCount, List<String> resumeOrder) {
        public Summary { resumeOrder = List.copyOf(resumeOrder); }
    }

    public Summary verify(Path repository) throws IOException {
        Path root = CorpusJson.directory(repository);
        Path contract = CorpusJson.directory(safeFile(root, "docs/contract/status.json").getParent());
        Path promptDirectory = CorpusJson.directory(contract.resolve("prompts"));
        readText(safeFile(root, "docs/contract/README.md"), MAX_README_BYTES);
        Map<String, Object> document;
        try {
            document = object(BoundedJsonParser.parse(readText(contract.resolve("status.json"), MAX_STATUS_BYTES)), "contract");
        } catch (BoundedJsonParser.JsonSyntaxException invalid) {
            throw new IOException("Invalid bounded prompt contract JSON: " + invalid.getMessage(), invalid);
        }
        require(integer(document.get("schemaVersion"), "schemaVersion") == 1, "Unsupported prompt contract schemaVersion");
        String version = text(document.get("contractVersion"), "contractVersion", 64);
        require(version.equals("2026.09.10.1"), "Unsupported prompt contract version; update its validator deliberately");
        Map<String, Object> baseline = object(document.get("baseline"), "baseline");
        for (var entry : BASELINE.entrySet())
            require(entry.getValue().equals(text(baseline.get(entry.getKey()), "baseline." + entry.getKey(), 128)),
                    "Pinned prompt baseline changed: " + entry.getKey());
        require(integer(baseline.get("targetJava"), "baseline.targetJava") == 17, "Pinned prompt Java baseline changed");
        List<?> prompts = list(document.get("prompts"), "prompts", 23);
        require(prompts.size() == 23, "Prompt contract must contain exactly 23 prompts numbered 0 through 22");
        Map<String, Prompt> promptIds = new LinkedHashMap<>();
        Map<String, Cut> cuts = new LinkedHashMap<>();
        Set<Integer> numbers = new HashSet<>();
        Set<Path> files = new HashSet<>();
        for (Object item : prompts) {
            Map<String, Object> prompt = object(item, "prompt");
            int number = integer(prompt.get("number"), "prompt.number");
            require(number >= 0 && number <= 22 && numbers.add(number), "Duplicate or out-of-range prompt number");
            String id = text(prompt.get("id"), "prompt.id", 32);
            require(id.equals(String.format(Locale.ROOT, "LB-%02d", number)), "Prompt ID does not match its number");
            String title = text(prompt.get("title"), "prompt.title", 256);
            String state = status(prompt.get("status"));
            String relative = text(prompt.get("file"), "prompt.file", 1024);
            require(relative.startsWith("prompts/") && relative.endsWith(".md"), "Prompt file must be Markdown under contract/prompts");
            Path file = safeFile(contract, relative);
            require(file.startsWith(promptDirectory) && files.add(file), "Duplicate or escaped prompt file");
            String body = readText(file, MAX_PROMPT_BYTES);
            int newline = body.indexOf('\n');
            String first = newline < 0 ? body : body.substring(0, newline);
            if (first.endsWith("\r")) first = first.substring(0, first.length() - 1);
            require(first.equals("# " + id + " — " + title), "Prompt header does not match ID/title: " + id);
            evidence(root, prompt.get("evidence"), "prompt " + id);
            List<?> declaredCuts = list(prompt.get("cuts"), "prompt.cuts", 128);
            require(!declaredCuts.isEmpty(), "Every prompt must declare at least one bounded cut");
            List<String> ownedCuts = new ArrayList<>();
            for (Object candidate : declaredCuts) {
                Map<String, Object> cut = object(candidate, "cut");
                String cutId = identifier(cut.get("id"), "cut.id");
                String cutStatus = status(cut.get("status"));
                text(cut.get("summary"), "cut.summary", 4096);
                List<String> dependencies = strings(cut.get("requires"), "cut.requires", 64);
                for (String dependency : dependencies) identifier(dependency, "cut dependency");
                List<String> cutEvidence = evidence(root, cut.get("evidence"), "cut " + cutId);
                require(!cutStatus.equals("ACCEPTED") || !cutEvidence.isEmpty(), "ACCEPTED cut requires existing evidence: " + cutId);
                require(cuts.size() < MAX_CUTS && cuts.putIfAbsent(cutId, new Cut(id, cutStatus, dependencies)) == null,
                        "Duplicate or excessive cut IDs: " + cutId);
                ownedCuts.add(cutId);
            }
            require(promptIds.putIfAbsent(id, new Prompt(state, ownedCuts)) == null, "Duplicate prompt ID");
            if (state.equals("ACCEPTED")) for (String cutId : ownedCuts)
                require(cuts.get(cutId).status.equals("ACCEPTED"), "ACCEPTED prompt contains an unresolved/dispositioned cut: " + cutId);
        }
        for (String cutId : cuts.keySet()) require(!promptIds.containsKey(cutId), "A cut ID collides with a prompt ID: " + cutId);
        graph(cuts);
        List<String> resume = strings(document.get("resumeOrder"), "resumeOrder", MAX_CUTS);
        for (String cutId : resume) {
            Cut cut = cuts.get(cutId);
            require(cut != null && EXECUTABLE_UNRESOLVED.contains(cut.status), "Resume cut must exist and be unresolved/executable: " + cutId);
        }
        require(document.containsKey("current"), "Current cursor must be explicitly present");
        if (document.get("current") == null) {
            require(resume.isEmpty() && promptIds.values().stream().allMatch(prompt -> prompt.status.equals("ACCEPTED"))
                    && cuts.values().stream().allMatch(cut -> cut.status.equals("ACCEPTED")),
                    "A closed cursor requires every prompt and cut ACCEPTED and an empty resumeOrder");
            return new Summary(version, null, null, prompts.size(), cuts.size(), resume);
        }
        Map<String, Object> current = object(document.get("current"), "current");
        String currentPrompt = text(current.get("prompt"), "current.prompt", 32);
        String currentCut = identifier(current.get("cut"), "current.cut");
        require(promptIds.containsKey(currentPrompt), "Current prompt does not exist");
        require(EXECUTABLE_UNRESOLVED.contains(promptIds.get(currentPrompt).status),
                "Current prompt must be unresolved and executable, not accepted or dispositioned");
        Cut active = cuts.get(currentCut);
        require(active != null && active.prompt.equals(currentPrompt), "Current prompt does not own the current cut");
        require(EXECUTABLE_UNRESOLVED.contains(active.status), "Current cut must be unresolved and executable, not accepted or dispositioned");
        return new Summary(version, currentPrompt, currentCut, prompts.size(), cuts.size(), resume);
    }

    private static void graph(Map<String, Cut> cuts) throws IOException {
        Map<String, Integer> incoming = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (var entry : cuts.entrySet()) {
            incoming.put(entry.getKey(), entry.getValue().requires.size());
            for (String dependency : entry.getValue().requires) {
                require(cuts.containsKey(dependency), "Unknown cut prerequisite: " + dependency);
                require(!entry.getValue().status.equals("ACCEPTED") || cuts.get(dependency).status.equals("ACCEPTED"),
                        "ACCEPTED cut has an unaccepted prerequisite: " + entry.getKey() + " -> " + dependency);
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(entry.getKey());
            }
        }
        ArrayDeque<String> ready = new ArrayDeque<>();
        incoming.forEach((id, degree) -> { if (degree == 0) ready.add(id); });
        int visited = 0;
        while (!ready.isEmpty()) {
            String next = ready.removeFirst(); visited++;
            for (String dependent : dependents.getOrDefault(next, List.of()))
                if (incoming.compute(dependent, (id, degree) -> degree - 1) == 0) ready.add(dependent);
        }
        require(visited == cuts.size(), "Prompt cut prerequisites contain a cycle");
    }

    /** Evidence is checked by attributes/path only: no hash/content reads, class loading or private artifact ingestion. */
    private static List<String> evidence(Path root, Object value, String label) throws IOException {
        List<String> paths = strings(value, label + " evidence", 64);
        for (String path : paths) safeFile(root, path);
        return paths;
    }

    private static Path safeFile(Path base, String relative) throws IOException {
        require(!relative.isBlank() && relative.length() <= 1024 && !relative.contains("\\") && !relative.contains(":")
                && !relative.startsWith("/") && !relative.endsWith("/") && relative.chars().noneMatch(c -> c < 32 || c == 127),
                "Unsafe contract/evidence relative path");
        for (String segment : relative.split("/", -1)) require(!segment.isEmpty() && !segment.equals(".") && !segment.equals("..")
                && !segment.endsWith(" ") && !segment.endsWith("."), "Unsafe contract/evidence path component");
        Path path;
        try { path = base.resolve(relative).toAbsolutePath().normalize(); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid contract/evidence path", invalid); }
        require(path.startsWith(base), "Contract/evidence path escapes its root");
        return CorpusJson.regular(path);
    }

    private static String readText(Path path, int maxBytes) throws IOException {
        Path selected = CorpusJson.regular(path);
        require(Files.size(selected) <= maxBytes, "Prompt contract file exceeds byte limit");
        byte[] bytes;
        try (var input = Files.newInputStream(selected, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(maxBytes + 1); }
        require(bytes.length <= maxBytes, "Prompt contract file grew beyond byte limit");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static Map<String, Object> object(Object value, String label) throws IOException {
        require(value instanceof Map<?, ?>, label + " must be a JSON object");
        @SuppressWarnings("unchecked") Map<String, Object> object = (Map<String, Object>) value;
        return object;
    }

    private static List<?> list(Object value, String label, int max) throws IOException {
        require(value instanceof List<?>, label + " must be a JSON array");
        List<?> list = (List<?>) value;
        require(list.size() <= max, label + " exceeds entry limit");
        return list;
    }

    private static List<String> strings(Object value, String label, int max) throws IOException {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object item : list(value, label, max)) {
            String text = text(item, label, 1024);
            require(seen.add(text), "Duplicate entry in " + label); result.add(text);
        }
        return List.copyOf(result);
    }

    private static String text(Object value, String label, int max) throws IOException {
        require(value instanceof String, label + " must be a JSON string");
        String text = (String) value;
        require(!text.isBlank() && text.length() <= max && text.chars().noneMatch(c -> c < 32 || c == 127),
                label + " is blank, oversized or contains control characters");
        return text;
    }

    private static String identifier(Object value, String label) throws IOException {
        String id = text(value, label, 128);
        require(id.matches("[A-Z][A-Z0-9]*(?:[-_][A-Z0-9]+)*"), "Invalid stable cut ID: " + id);
        return id;
    }

    private static String status(Object value) throws IOException {
        String status = text(value, "status", 32);
        require(STATUSES.contains(status), "Unknown prompt/cut status");
        return status;
    }

    private static int integer(Object value, String label) throws IOException {
        require(value instanceof BigDecimal, label + " must be a JSON number");
        try { return ((BigDecimal) value).intValueExact(); }
        catch (ArithmeticException invalid) { throw new IOException(label + " must be a bounded integer", invalid); }
    }

    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usage: PromptContractVerifier <repository-root>");
        Summary summary = new PromptContractVerifier().verify(Path.of(args[0]));
        String cursor = summary.currentPrompt == null ? "CLOSED" : summary.currentPrompt + "/" + summary.currentCut;
        System.out.println("PROMPT_CONTRACT_VERIFIED prompts=" + summary.promptCount + " cuts=" + summary.cutCount
                + " current=" + cursor
                + " evidence=PATHS_ONLY_NO_EXECUTION_OR_FRESHNESS_INFERENCE");
    }

    private record Prompt(String status, List<String> cuts) { }
    private record Cut(String prompt, String status, List<String> requires) { }
}
