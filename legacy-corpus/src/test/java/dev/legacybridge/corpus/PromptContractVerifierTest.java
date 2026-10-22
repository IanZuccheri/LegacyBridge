package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PromptContractVerifierTest {
    @TempDir Path root;
    Path contract;
    Map<String, Object> document;
    List<Map<String, Object>> prompts;

    @BeforeEach void prepare() throws IOException {
        contract = Files.createDirectories(root.resolve("docs/contract"));
        Files.createDirectory(contract.resolve("prompts"));
        Files.writeString(contract.resolve("README.md"), "# Contract\nHistorical statuses are not execution evidence.\n");
        Files.writeString(contract.resolve("audit.md"), "# Existing audit\n");
        prompts = new ArrayList<>();
        for (int index = 0; index < 23; index++) {
            String id = String.format(java.util.Locale.ROOT, "LB-%02d", index);
            String cutId = String.format(java.util.Locale.ROOT, "CUT-%02d", index);
            String title = "Stage " + index;
            String file = String.format(java.util.Locale.ROOT, "prompts/%02d-stage.md", index);
            Files.writeString(contract.resolve(file), "# " + id + " — " + title + "\n\nBounded task.\n", StandardCharsets.UTF_8);
            Map<String, Object> cut = new LinkedHashMap<>();
            cut.put("id", cutId); cut.put("summary", "A bounded test cut");
            cut.put("status", index == 0 ? "ACCEPTED" : index == 4 ? "PARTIAL" : "NOT_STARTED");
            cut.put("requires", index == 0 ? List.of() : List.of(String.format(java.util.Locale.ROOT, "CUT-%02d", index - 1)));
            cut.put("evidence", index == 0 ? List.of("docs/contract/audit.md") : List.of());
            Map<String, Object> prompt = new LinkedHashMap<>();
            prompt.put("id", id); prompt.put("number", index); prompt.put("title", title); prompt.put("file", file);
            prompt.put("status", cut.get("status")); prompt.put("evidence", List.of());
            prompt.put("cuts", new ArrayList<>(List.of(cut))); prompts.add(prompt);
        }
        Map<String, Object> baseline = new LinkedHashMap<>(Map.of("sourceMinecraft", "1.16.5", "sourceForge", "36.2.42",
                "targetMinecraft", "1.20.1", "targetForge", "47.4.23", "targetJava", 17,
                "runtimeModId", "legacybridge_runtime", "runtimeVersion", "0.1.1-SNAPSHOT"));
        document = new LinkedHashMap<>();
        document.put("schemaVersion", 1); document.put("contractVersion", "2026.09.10.1");
        document.put("baseline", baseline); document.put("prompts", prompts);
        document.put("current", new LinkedHashMap<>(Map.of("prompt", "LB-04", "cut", "CUT-04")));
        document.put("resumeOrder", List.of("CUT-04", "CUT-12"));
    }

    @Test void validatesRecordedStructureWithoutChangingOrPromotingIt() throws Exception {
        document.put("scopeNote", "Additional fields are allowed");
        prompts.get(5).put("risk", "high"); cut(6).put("nextAction", "Requires human review");
        write(); byte[] before = Files.readAllBytes(contract.resolve("status.json"));
        var summary = new PromptContractVerifier().verify(root);
        assertEquals("LB-04", summary.currentPrompt()); assertEquals("CUT-04", summary.currentCut());
        assertEquals(23, summary.promptCount()); assertEquals(23, summary.cutCount());
        assertEquals(List.of("CUT-04", "CUT-12"), summary.resumeOrder());
        assertThrows(UnsupportedOperationException.class, () -> summary.resumeOrder().add("CUT-13"));
        assertArrayEquals(before, Files.readAllBytes(contract.resolve("status.json")));
        try (var paths = Files.walk(contract)) { assertEquals(26, paths.filter(Files::isRegularFile).count()); }
    }

    @Test void evidenceExistenceDoesNotReadOrInterpretItsPrivateBinaryContents() throws Exception {
        Path binary = root.resolve("private-evidence.bin");
        Files.write(binary, new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xC3, 0x28, 0});
        cut(0).put("evidence", List.of("private-evidence.bin"));
        write(); assertEquals("CUT-04", new PromptContractVerifier().verify(root).currentCut());
        assertArrayEquals(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xC3, 0x28, 0}, Files.readAllBytes(binary));
    }

    @Test void acceptedCutCannotBypassAnUnacceptedPrerequisite() throws Exception {
        cut(5).put("status", "ACCEPTED"); cut(5).put("evidence", List.of("docs/contract/audit.md"));
        write();
        assertTrue(assertThrows(IOException.class, () -> new PromptContractVerifier().verify(root))
                .getMessage().contains("unaccepted prerequisite"));
    }

    @ParameterizedTest @ValueSource(strings = {"MANUAL", "UNSUPPORTED", "DEFERRED"})
    void activeCutCannotHideInsideADispositionedParent(String state) throws Exception {
        prompts.get(4).put("status", state); write();
        assertTrue(assertThrows(IOException.class, () -> new PromptContractVerifier().verify(root))
                .getMessage().contains("Current prompt must be unresolved"));
    }

    @Test void explicitNullCursorClosesOnlyAnEntirelyAcceptedContract() throws Exception {
        acceptAll(); write();
        var summary = new PromptContractVerifier().verify(root);
        assertNull(summary.currentPrompt()); assertNull(summary.currentCut()); assertTrue(summary.resumeOrder().isEmpty());
        assertEquals(23, summary.promptCount()); assertEquals(23, summary.cutCount());
    }

    @Test void nullCursorCannotCloseAnIncompleteContract() throws Exception {
        document.put("current", null); document.put("resumeOrder", List.of()); write();
        assertTrue(assertThrows(IOException.class, () -> new PromptContractVerifier().verify(root))
                .getMessage().contains("closed cursor requires every prompt and cut ACCEPTED"));
    }

    @Test void closedContractCannotRetainResumeEntries() throws Exception {
        acceptAll(); document.put("resumeOrder", List.of("CUT-04")); write();
        assertThrows(IOException.class, () -> new PromptContractVerifier().verify(root));
    }

    @Test void missingCursorIsNotAnExplicitClosedDisposition() throws Exception {
        acceptAll(); document.remove("current"); write();
        assertTrue(assertThrows(IOException.class, () -> new PromptContractVerifier().verify(root))
                .getMessage().contains("explicitly present"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-prompt", "extra-prompt", "number-duplicate", "number-string", "number-fraction",
            "id-mismatch", "id-duplicate", "unknown-status", "empty-cuts", "cut-duplicate", "cut-id-invalid", "prompt-cut-collision",
            "unknown-prerequisite", "duplicate-prerequisite", "self-cycle", "cycle", "accepted-without-evidence",
            "accepted-parent-unresolved", "missing-evidence", "evidence-not-array", "evidence-not-string",
            "current-unknown", "current-wrong-owner", "current-accepted", "resume-unknown", "resume-duplicate",
            "resume-accepted", "schema-string", "schema-version", "contract-version", "java-string", "java-version",
            "forge-drift", "prompt-file-outside", "prompt-file-traversal", "prompt-file-duplicate", "title-missing",
            "title-control", "summary-type", "requires-type"})
    void rejectsInconsistentOrMalformedStatus(String mutation) throws Exception {
        switch (mutation) {
            case "missing-prompt" -> prompts.remove(22);
            case "extra-prompt" -> prompts.add(new LinkedHashMap<>(prompts.get(22)));
            case "number-duplicate" -> prompts.get(5).put("number", 4);
            case "number-string" -> prompts.get(5).put("number", "5");
            case "number-fraction" -> prompts.get(5).put("number", 5.5);
            case "id-mismatch" -> prompts.get(5).put("id", "LB-99");
            case "id-duplicate" -> prompts.get(5).put("id", "LB-04");
            case "unknown-status" -> cut(5).put("status", "DONE");
            case "empty-cuts" -> prompts.get(5).put("cuts", List.of());
            case "cut-duplicate" -> cut(5).put("id", "CUT-04");
            case "cut-id-invalid" -> cut(5).put("id", "bad cut");
            case "prompt-cut-collision" -> cut(5).put("id", "LB-05");
            case "unknown-prerequisite" -> cut(5).put("requires", List.of("MISSING"));
            case "duplicate-prerequisite" -> cut(5).put("requires", List.of("CUT-04", "CUT-04"));
            case "self-cycle" -> cut(5).put("requires", List.of("CUT-05"));
            case "cycle" -> cut(0).put("requires", List.of("CUT-01"));
            case "accepted-without-evidence" -> cut(5).put("status", "ACCEPTED");
            case "accepted-parent-unresolved" -> prompts.get(5).put("status", "ACCEPTED");
            case "missing-evidence" -> cut(0).put("evidence", List.of("missing.md"));
            case "evidence-not-array" -> cut(0).put("evidence", "docs/contract/audit.md");
            case "evidence-not-string" -> cut(0).put("evidence", List.of(1));
            case "current-unknown" -> current().put("prompt", "LB-99");
            case "current-wrong-owner" -> current().put("cut", "CUT-05");
            case "current-accepted" -> document.put("current", Map.of("prompt", "LB-00", "cut", "CUT-00"));
            case "resume-unknown" -> document.put("resumeOrder", List.of("MISSING"));
            case "resume-duplicate" -> document.put("resumeOrder", List.of("CUT-04", "CUT-04"));
            case "resume-accepted" -> document.put("resumeOrder", List.of("CUT-00"));
            case "schema-string" -> document.put("schemaVersion", "1");
            case "schema-version" -> document.put("schemaVersion", 2);
            case "contract-version" -> document.put("contractVersion", "later-unreviewed");
            case "java-string" -> baseline().put("targetJava", "17");
            case "java-version" -> baseline().put("targetJava", 21);
            case "forge-drift" -> baseline().put("targetForge", "47.4.24");
            case "prompt-file-outside" -> prompts.get(5).put("file", "README.md");
            case "prompt-file-traversal" -> prompts.get(5).put("file", "prompts/../README.md");
            case "prompt-file-duplicate" -> prompts.get(5).put("file", prompts.get(4).get("file"));
            case "title-missing" -> prompts.get(5).remove("title");
            case "title-control" -> prompts.get(5).put("title", "Stage\n5");
            case "summary-type" -> cut(5).put("summary", true);
            case "requires-type" -> cut(5).put("requires", "CUT-04");
            default -> throw new AssertionError(mutation);
        }
        write(); assertThrows(IOException.class, () -> new PromptContractVerifier().verify(root), mutation);
    }

    @ParameterizedTest @ValueSource(strings = {"MANUAL", "UNSUPPORTED", "DEFERRED"})
    void dispositionedCutsCannotBecomeCurrentOrResumeAutomatically(String state) throws Exception {
        cut(4).put("status", state); write();
        assertThrows(IOException.class, () -> new PromptContractVerifier().verify(root));
        current().put("prompt", "LB-05"); current().put("cut", "CUT-05"); write();
        assertThrows(IOException.class, () -> new PromptContractVerifier().verify(root));
        document.put("resumeOrder", List.of("CUT-05")); write();
        assertEquals("CUT-05", new PromptContractVerifier().verify(root).currentCut());
        prompts.get(4).put("status", "ACCEPTED"); write();
        assertThrows(IOException.class, () -> new PromptContractVerifier().verify(root));
    }

    @ParameterizedTest @ValueSource(strings = {"../outside", "/absolute", "C:/absolute", "docs\\contract\\audit.md",
            "docs//contract/audit.md", "docs/./contract/audit.md", "docs/contract/audit.md:stream", "docs/contract/audit.md.",
            "docs/contract/audit.md ", "docs/contract"})
    void rejectsUnsafeEvidencePaths(String path) throws Exception {
        cut(0).put("evidence", List.of(path)); write();
        assertThrows(IOException.class, () -> new PromptContractVerifier().verify(root));
    }

    @ParameterizedTest @ValueSource(strings = {"missing-readme", "missing-prompt", "header-id", "header-title", "utf8-status", "utf8-prompt",
            "malformed-json", "duplicate-json-key", "status-too-large", "prompt-too-large", "readme-too-large"})
    void rejectsBrokenFilesAndReadLimits(String mutation) throws Exception {
        write();
        Path prompt = contract.resolve((String) prompts.get(5).get("file"));
        Path status = contract.resolve("status.json");
        switch (mutation) {
            case "missing-readme" -> Files.delete(contract.resolve("README.md"));
            case "missing-prompt" -> Files.delete(prompt);
            case "header-id" -> Files.writeString(prompt, "# LB-99 — Stage 5\n");
            case "header-title" -> Files.writeString(prompt, "# LB-05 — Wrong title\n");
            case "utf8-status" -> Files.write(status, new byte[]{(byte) 0xC3, 0x28});
            case "utf8-prompt" -> Files.write(prompt, new byte[]{(byte) 0xC3, 0x28});
            case "malformed-json" -> Files.writeString(status, "{ not JSON }");
            case "duplicate-json-key" -> Files.writeString(status, "{\"schemaVersion\":1,\"schemaVersion\":1}");
            case "status-too-large" -> Files.write(status, new byte[PromptContractVerifier.MAX_STATUS_BYTES + 1]);
            case "prompt-too-large" -> Files.write(prompt, new byte[PromptContractVerifier.MAX_PROMPT_BYTES + 1]);
            case "readme-too-large" -> Files.write(contract.resolve("README.md"), new byte[PromptContractVerifier.MAX_README_BYTES + 1]);
            default -> throw new AssertionError(mutation);
        }
        assertThrows(IOException.class, () -> new PromptContractVerifier().verify(root), mutation);
    }

    @Test void rejectsSymbolicEvidenceWithoutFollowingIt() throws Exception {
        Path link = root.resolve("evidence-link");
        boolean available;
        try { Files.createSymbolicLink(link, contract.resolve("audit.md")); available = true; }
        catch (IOException | UnsupportedOperationException | SecurityException unavailable) { available = false; }
        assumeTrue(available, "Creating symlinks needs platform privileges; no simulated native pass");
        cut(0).put("evidence", List.of("evidence-link")); write();
        assertThrows(IOException.class, () -> new PromptContractVerifier().verify(root));
    }

    @Test void rejectsSymbolicPromptDirectory() throws Exception {
        Path original = contract.resolve("prompts");
        Path relocated = root.resolve("real-prompts");
        Files.move(original, relocated);
        boolean available;
        try { Files.createSymbolicLink(original, relocated); available = true; }
        catch (IOException | UnsupportedOperationException | SecurityException unavailable) { available = false; }
        assumeTrue(available, "Creating symlinks needs platform privileges; no simulated native pass");
        write(); assertThrows(IOException.class, () -> new PromptContractVerifier().verify(root));
    }

    @Test void mainRequiresOneExplicitRepositoryRoot() {
        assertThrows(IllegalArgumentException.class, () -> PromptContractVerifier.main(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> PromptContractVerifier.main(new String[]{"one", "two"}));
    }

    private void write() throws IOException { Files.writeString(contract.resolve("status.json"), CorpusJson.canonicalJson(document), StandardCharsets.UTF_8); }
    private void acceptAll() {
        for (int index = 0; index < prompts.size(); index++) {
            prompts.get(index).put("status", "ACCEPTED"); cut(index).put("status", "ACCEPTED");
            cut(index).put("evidence", List.of("docs/contract/audit.md"));
        }
        document.put("current", null); document.put("resumeOrder", List.of());
    }
    @SuppressWarnings("unchecked") private Map<String, Object> cut(int index) { return ((List<Map<String, Object>>) prompts.get(index).get("cuts")).get(0); }
    @SuppressWarnings("unchecked") private Map<String, Object> current() { return (Map<String, Object>) document.get("current"); }
    @SuppressWarnings("unchecked") private Map<String, Object> baseline() { return (Map<String, Object>) document.get("baseline"); }
}
