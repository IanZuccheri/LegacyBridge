package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.legacybridge.corpus.CompositionRunFiles.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Two reused, real inert helper collections; never launches Minecraft/Forge or any original mod. */
@Execution(ExecutionMode.SAME_THREAD)
final class CompositionSessionEvidenceTest {
    private static final String PLAN = "1".repeat(64), RECEIPT = "session-receipt.json", PENDING = "session-receipt.pending";
    @TempDir static Path helperRoot;
    @TempDir Path root;
    private static CompositionProcessCollector.Result collected, emptyStderr;
    private static CompositionSessionEvidence.Binding binding;
    private Path evidence;

    @BeforeAll static void collectOnlyTwoInertHelpers() throws Exception {
        var fixture = new CompositionProcessCollectorTest();
        fixture.cwd = Files.createDirectory(helperRoot.resolve("collector")).toRealPath();
        fixture.prepareInertHelper();
        binding = new CompositionSessionEvidence.Binding("session-evidence", "SOURCE", "2".repeat(64), "3".repeat(64), PLAN, hash(fixture.helper));
        collected = collect(fixture, "ok"); emptyStderr = collect(fixture, "stdin");
        assertEquals(CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY, collected.status(), collected.toString());
        assertEquals(CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY, emptyStderr.status(), emptyStderr.toString());
    }
    private static CompositionProcessCollector.Result collect(CompositionProcessCollectorTest fixture, String mode) {
        var arguments = List.of(fixture.java.toString(), "-Xms16M", "-Xmx32M", "-Duser.home=" + fixture.cwd.resolve("empty-user-home"),
                "-Djava.io.tmpdir=" + fixture.cwd.resolve("tmp"), "-jar", fixture.helper.toString(), mode);
        return CompositionProcessCollector.collect(new CompositionProcessCollector.Launch(fixture.cwd, arguments, PLAN,
                fixture.inputs, new CompositionProcessCollector.Limits(10_000, 2_000, 1 << 20)), () -> false);
    }
    @BeforeEach void prepare() throws Exception { root = root.toRealPath(); evidence = root.resolve("evidence"); }

    @Test void publishesClosedHashBoundEvidenceWithoutNativeOrInstallationClaims() throws Exception {
        var publication = publish();
        assertEquals(evidence, publication.directory());
        assertEquals(hash(evidence.resolve(RECEIPT)), publication.receiptSha256());
        assertEquals("PUBLISHED_INERT_ONLY", publication.toTree().get("status"));
        assertNoAuthority(publication.toTree());
        assertThrows(UnsupportedOperationException.class, () -> publication.toTree().put("installationAllowed", true));
        assertEquals("OUT_OK\n", Files.readString(evidence.resolve("stdout.log")));
        assertEquals("ERR_OK\n", Files.readString(evidence.resolve("stderr.log")));
        assertEquals(CorpusJson.canonicalJson(collected.toTree()), Files.readString(evidence.resolve("collection.json")));
        try (var files = Files.list(evidence)) {
            assertEquals(java.util.Set.of("stdout.log", "stderr.log", "binding.json", "collection.json", RECEIPT),
                    files.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
        }
        var inspection = CompositionSessionEvidence.inspect(evidence, binding);
        assertEquals("RECORDED_INERT_ONLY", inspection.get("status"));
        assertEquals(publication.receiptSha256(), inspection.get("receiptSha256"));
        assertEquals(true, inspection.get("recordedBindingVerified"));
        assertNoAuthority(inspection);
        assertThrows(UnsupportedOperationException.class, () -> inspection.put("executionAllowed", true));
        assertFalse(Files.exists(root.resolve("composition-probe-authorized.txt")));
    }

    @Test void emptyStderrIsPreservedAndVerifiable() throws Exception {
        CompositionSessionEvidence.publish(evidence, binding, emptyStderr, () -> false);
        assertEquals(0, Files.size(evidence.resolve("stderr.log")));
        assertEquals("RECORDED_INERT_ONLY", CompositionSessionEvidence.inspect(evidence, binding).get("status"));
    }

    @Test void inspectionNeverChangesACompleteEvidenceSet() throws Exception {
        publish(); Map<String, String> before = snapshot();
        assertEquals(CompositionSessionEvidence.inspect(evidence, binding), CompositionSessionEvidence.inspect(evidence, binding));
        assertEquals(before, snapshot());
    }

    @Test void existingUnknownDirectoryAndSecondPublicationAreNeverOverwritten() throws Exception {
        Files.createDirectory(evidence); Files.writeString(evidence.resolve("user.txt"), "foreign");
        rejected(() -> publish());
        assertEquals("foreign", Files.readString(evidence.resolve("user.txt")));
        Path clean = root.resolve("other"); var publication = CompositionSessionEvidence.publish(clean, binding, collected, () -> false);
        String receipt = hash(clean.resolve(RECEIPT));
        rejected(() -> CompositionSessionEvidence.publish(clean, binding, collected, () -> false));
        assertEquals(receipt, hash(clean.resolve(RECEIPT))); assertEquals(receipt, publication.receiptSha256());
    }

    @Test void rejectedCollectorAndMismatchingSessionPlanCannotCreateEvidence() throws Exception {
        var failure = CompositionProcessCollector.collect(null, () -> false);
        rejected(() -> CompositionSessionEvidence.publish(evidence, binding, failure, () -> false));
        assertFalse(Files.exists(evidence));
        var mismatch = new CompositionSessionEvidence.Binding(binding.sessionId(), "SOURCE", binding.stagePlanSha256(),
                binding.stageReceiptSha256(), "9".repeat(64), binding.helperSha256());
        rejected(() -> CompositionSessionEvidence.publish(evidence, mismatch, collected, () -> false));
        assertFalse(Files.exists(evidence));
    }

    @ParameterizedTest @ValueSource(strings = {"sessionId", "role", "stagePlan", "stageReceipt", "sessionPlan", "helper"})
    void bindingDriftCannotBeAcceptedByRecovery(String field) throws Exception {
        publish();
        var changed = new CompositionSessionEvidence.Binding(field.equals("sessionId") ? "session-other" : binding.sessionId(),
                field.equals("role") ? "TARGET" : binding.role(), field.equals("stagePlan") ? "8".repeat(64) : binding.stagePlanSha256(),
                field.equals("stageReceipt") ? "8".repeat(64) : binding.stageReceiptSha256(),
                field.equals("sessionPlan") ? "8".repeat(64) : binding.sessionPlanSha256(),
                field.equals("helper") ? "8".repeat(64) : binding.helperSha256());
        rejected(() -> CompositionSessionEvidence.inspect(evidence, changed));
    }

    @Test void invalidBindingAndNoncanonicalOrMissingParentPathsBlockBeforeWrites() throws Exception {
        var invalid = new CompositionSessionEvidence.Binding("../escape", "SERVER", "unknown", "x", "x", "x");
        rejected(() -> CompositionSessionEvidence.publish(evidence, invalid, collected, () -> false));
        rejected(() -> CompositionSessionEvidence.publish(root.resolve("missing/evidence"), binding, collected, () -> false));
        rejected(() -> CompositionSessionEvidence.publish(root.resolve("alias/../evidence"), binding, collected, () -> false));
        assertFalse(Files.exists(evidence)); assertFalse(Files.exists(root.resolve("missing")));
    }

    @Test void preCancellationAndFailedCallbackNeverCreateFilesOrLeakCallbackText() throws Exception {
        rejected(() -> CompositionSessionEvidence.publish(evidence, binding, collected, () -> true));
        assertFalse(Files.exists(evidence));
        var failure = rejected(() -> CompositionSessionEvidence.publish(evidence, binding, collected,
                () -> { throw new IllegalStateException("synthetic-private-value"); }));
        assertEquals("SESSION_EVIDENCE_CANCELLATION_CALLBACK", failure.getMessage());
        assertNull(failure.getCause()); assertFalse(Files.exists(evidence));
    }

    @ParameterizedTest @ValueSource(strings = {"stdout.log", "stderr.log", "collection.json", "binding.json", PENDING})
    void cancellationAtEachPublicationBoundaryRetainsOnlyIncompletePrivateEvidence(String boundary) throws Exception {
        AtomicBoolean reached = new AtomicBoolean();
        rejected(() -> CompositionSessionEvidence.publish(evidence, binding, collected, () -> {
            boolean present = Files.exists(evidence.resolve(boundary)); if (present) reached.set(true); return present;
        }));
        assertTrue(reached.get()); assertTrue(Files.isDirectory(evidence)); assertFalse(Files.exists(evidence.resolve(RECEIPT)));
        Map<String, String> before = snapshot();
        var inspection = CompositionSessionEvidence.inspect(evidence, binding);
        assertEquals("INCOMPLETE", inspection.get("status")); assertEquals(false, inspection.get("recordedBindingVerified"));
        assertNull(inspection.get("receiptSha256")); assertNoAuthority(inspection); assertEquals(before, snapshot());
    }

    @Test void lateCallbackFailureRetainsThePartialTreeWithSanitizedError() throws Exception {
        var error = rejected(() -> CompositionSessionEvidence.publish(evidence, binding, collected, () -> {
            if (Files.exists(evidence.resolve("binding.json"))) throw new IllegalStateException("synthetic-private-value"); return false;
        }));
        assertEquals("SESSION_EVIDENCE_CANCELLATION_CALLBACK", error.getMessage()); assertNull(error.getCause());
        assertTrue(Files.isRegularFile(evidence.resolve("binding.json"))); assertFalse(Files.exists(evidence.resolve(RECEIPT)));
        assertEquals("INCOMPLETE", CompositionSessionEvidence.inspect(evidence, binding).get("status"));
    }

    @Test void callerInterruptionCancelsWithoutClearingTheThreadFlag() throws Exception {
        Thread.currentThread().interrupt();
        try {
            IOException error = rejected(() -> publish());
            assertEquals("SESSION_EVIDENCE_CANCELLED", error.getMessage());
            assertTrue(Thread.currentThread().isInterrupted()); assertFalse(Files.exists(evidence));
        } finally { Thread.interrupted(); } // Restore the JUnit worker, not production caller state.
    }

    @Test void cancellationIsNotConsultedAfterThePublicationBarrier() throws Exception {
        AtomicBoolean afterPublication = new AtomicBoolean();
        var result = CompositionSessionEvidence.publish(evidence, binding, collected, () -> {
            if (Files.exists(evidence.resolve(RECEIPT))) { afterPublication.set(true); return true; } return false;
        });
        assertFalse(afterPublication.get()); assertEquals("PUBLISHED_INERT_ONLY", result.toTree().get("status")); assertNoAuthority(result.toTree());
    }

    @ParameterizedTest @ValueSource(strings = {"intruder.txt", RECEIPT, PENDING})
    void callbackInjectedUnknownOrCollidingFileIsPreservedButNotPublished(String name) throws Exception {
        AtomicBoolean injected = new AtomicBoolean();
        rejected(() -> CompositionSessionEvidence.publish(evidence, binding, collected, () -> {
            if (Files.exists(evidence.resolve("binding.json")) && injected.compareAndSet(false, true)) {
                try { Files.writeString(evidence.resolve(name), "foreign bytes"); }
                catch (IOException invalid) { throw new IllegalStateException(invalid); }
            }
            return false;
        }));
        assertTrue(injected.get()); assertEquals("foreign bytes", Files.readString(evidence.resolve(name)));
    }

    @ParameterizedTest @ValueSource(strings = {"stdout.log", "stderr.log", "collection.json", "binding.json", RECEIPT})
    void modifiedFileCannotBeSilentlyAcceptedOrRepaired(String name) throws Exception {
        publish(); Files.writeString(evidence.resolve(name), "tampered bytes");
        Map<String, String> before = snapshot(); rejected(() -> CompositionSessionEvidence.inspect(evidence, binding)); assertEquals(before, snapshot());
    }

    @ParameterizedTest @ValueSource(strings = {"stdoutComplete", "processExited", "exitCode", "errors", "status", "gameExecutionVerified", "launchContractVerified", "extra", "planSha256", "stdoutSha256"})
    void forgedCollectionFieldsFailEvenIfReceiptFilePinWasUpdated(String field) throws Exception {
        publish(); Map<String, Object> tree = new LinkedHashMap<>(CorpusJson.read(evidence.resolve("collection.json")));
        Object value = switch (field) {
            case "stdoutComplete", "processExited" -> false;
            case "exitCode" -> 7;
            case "errors" -> List.of("NONZERO_EXIT");
            case "status" -> "FAILED";
            case "planSha256", "stdoutSha256" -> "9".repeat(64);
            default -> true;
        };
        tree.put(field, value); Files.writeString(evidence.resolve("collection.json"), CorpusJson.canonicalJson(tree));
        refreshReceiptPin("collection.json"); rejected(() -> CompositionSessionEvidence.inspect(evidence, binding));
    }

    @Test void invalidUtf8LogIsRejectedEvenWithSelfConsistentUnsignedHashAssertions() throws Exception {
        publish(); Files.write(evidence.resolve("stdout.log"), new byte[] {(byte) 0xc3, 0x28});
        Map<String, Object> collection = new LinkedHashMap<>(CorpusJson.read(evidence.resolve("collection.json")));
        collection.put("stdoutSha256", hash(evidence.resolve("stdout.log")));
        Files.writeString(evidence.resolve("collection.json"), CorpusJson.canonicalJson(collection));
        refreshReceiptPin("stdout.log"); refreshReceiptPin("collection.json");
        rejected(() -> CompositionSessionEvidence.inspect(evidence, binding));
    }

    @Test void receiptWithUnknownFieldsOrDuplicatePathsCannotAuthorizeEvidence() throws Exception {
        publish(); Map<String, Object> receipt = new LinkedHashMap<>(CorpusJson.read(evidence.resolve(RECEIPT)));
        List<Object> files = new ArrayList<>((List<?>) receipt.get("files")); files.set(1, files.get(0)); receipt.put("files", files);
        Files.writeString(evidence.resolve(RECEIPT), CorpusJson.canonicalJson(receipt)); rejected(() -> CompositionSessionEvidence.inspect(evidence, binding));
        receipt.put("unexpected", true); Files.writeString(evidence.resolve(RECEIPT), CorpusJson.canonicalJson(receipt));
        rejected(() -> CompositionSessionEvidence.inspect(evidence, binding));
    }

    @Test void emptyOrInterruptedKnownFilesAreIncompleteButUnknownFilesAreNotAdopted() throws Exception {
        Files.createDirectory(evidence); Files.writeString(evidence.resolve("binding.json"), "{truncated");
        assertEquals("INCOMPLETE", CompositionSessionEvidence.inspect(evidence, binding).get("status"));
        Files.createDirectory(evidence.resolve("unknown")); rejected(() -> CompositionSessionEvidence.inspect(evidence, binding));
    }

    @ParameterizedTest @ValueSource(strings = {"stdout.log", "collection.json"})
    void oversizedFilesAreRejectedWithBoundedReads(String name) throws Exception {
        Files.createDirectory(evidence); long cap = name.endsWith(".log") ? 8L << 20 : 64L << 10;
        try (var sparse = FileChannel.open(evidence.resolve(name), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            sparse.position(cap); sparse.write(ByteBuffer.wrap(new byte[] {1}));
        }
        rejected(() -> CompositionSessionEvidence.inspect(evidence, binding));
    }

    @Test void aliasesBetweenSelectedFilesAreRejectedEvenForAnIncompleteTree() throws Exception {
        Files.createDirectory(evidence); Path first = Files.writeString(evidence.resolve("stdout.log"), "same");
        Files.createLink(evidence.resolve("stderr.log"), first);
        rejected(() -> CompositionSessionEvidence.inspect(evidence, binding));
        assertTrue(Files.isSameFile(first, evidence.resolve("stderr.log")));
    }

    @Test void symlinkedEvidenceCannotRedirectReadOrWriteOwnership() throws Exception {
        Path outside = Files.createDirectory(root.resolve("outside"));
        try { Files.createSymbolicLink(evidence, outside); }
        catch (IOException | UnsupportedOperationException denied) { assumeTrue(false, "Symbolic links unavailable: " + denied.getClass().getSimpleName()); }
        try {
            rejected(() -> CompositionSessionEvidence.inspect(evidence, binding)); rejected(() -> publish());
            try (var contents = Files.list(outside)) { assertEquals(0, contents.count()); }
        } finally { Files.delete(evidence); }
    }

    private CompositionSessionEvidence.Publication publish() throws IOException { return CompositionSessionEvidence.publish(evidence, binding, collected, () -> false); }
    private static IOException rejected(org.junit.jupiter.api.function.Executable operation) {
        IOException error = assertThrows(IOException.class, operation);
        assertTrue(error.getMessage().matches("SESSION_EVIDENCE_[A-Z0-9_]+"), error.getMessage()); return error;
    }
    private static void assertNoAuthority(Map<String, Object> result) {
        assertEquals("INERT_HELPER_ONLY", result.get("sessionKind"));
        for (String flag : List.of("gameExecutionVerified", "nativeBehaviorVerified", "installationAllowed", "launchContractVerified", "executionAllowed")) assertEquals(false, result.get(flag), flag);
    }
    private Map<String, String> snapshot() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (var listing = Files.list(evidence)) { for (Path path : listing.toList()) files.put(path.getFileName().toString(), hash(path)); }
        return files;
    }
    private void refreshReceiptPin(String name) throws IOException {
        Map<String, Object> receipt = new LinkedHashMap<>(CorpusJson.read(evidence.resolve(RECEIPT)));
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Object raw : (List<?>) receipt.get("files")) {
            Map<String, Object> entry = new LinkedHashMap<>(object(raw));
            if (name.equals(entry.get("path"))) { entry.put("sha256", hash(evidence.resolve(name))); entry.put("bytes", Files.size(evidence.resolve(name))); }
            entries.add(entry);
        }
        receipt.put("files", entries); Files.writeString(evidence.resolve(RECEIPT), CorpusJson.canonicalJson(receipt));
    }
}
