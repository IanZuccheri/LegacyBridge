package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** All child JVMs contain only the inert helper: no native Minecraft, Forge or original mod code. */
@Execution(ExecutionMode.SAME_THREAD)
class CompositionInertSessionTest {
    @TempDir Path root;
    static final String ID = "session-inert-test";
    static final CompositionProcessCollector.Limits LIMITS = new CompositionProcessCollector.Limits(10_000, 2_000, 1 << 20);

    private CompositionStagedRunVerifier.Snapshot fixture() throws Exception {
        return CompositionStagedRunVerifierTest.fixture(root, CompositionRunPlanner.Role.SOURCE,
                Path.of(System.getProperty("java.home")).toRealPath());
    }
    private Path jar(String attribute, String extra, boolean tamper) throws IOException {
        Path file = root.resolve("test-helper.jar");
        Manifest manifest = new Manifest(); manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, CompositionInertSession.HELPER);
        if (attribute != null) manifest.getMainAttributes().putValue(attribute, "synthetic.NotAnAgent");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file), manifest)) {
            String name = CompositionInertSession.HELPER.replace('.', '/') + ".class";
            try (var input = getClass().getClassLoader().getResourceAsStream(name)) {
                assertNotNull(input); byte[] bytes = input.readAllBytes(); if (tamper) bytes[bytes.length - 1] ^= 1;
                output.putNextEntry(new JarEntry(name)); output.write(bytes); output.closeEntry();
            }
            if (extra != null) { output.putNextEntry(new JarEntry(extra)); output.write(1); output.closeEntry(); }
        }
        return file;
    }
    private CompositionInertSession.Outcome run(CompositionInertSession.Mode mode) throws Exception {
        return CompositionInertSession.run(fixture(), jar(null, null, false), ID, mode, LIMITS, () -> false);
    }
    private void notPublished(CompositionInertSession.Outcome outcome) {
        assertNotEquals("PUBLISHED_INERT_ONLY", outcome.status(), outcome.toString()); assertNull(outcome.receiptSha256());
        assertFalse(outcome.errors().isEmpty());
        assertFalse(Files.exists(root.resolve("build/composition-inert-sessions/" + ID + "/evidence/session-receipt.json")));
        assertEquals(false, outcome.toTree().get("gameExecutionVerified")); assertEquals(false, outcome.toTree().get("installationAllowed"));
        assertEquals(false, outcome.toTree().get("launchContractVerified"));
    }
    @ParameterizedTest @EnumSource(value = CompositionInertSession.Mode.class, names = {"OK", "STDIN", "BOTH", "ENVIRONMENT"})
    void completeClosedSessionPublishesOnlyActualInertExitAndKeepsStageUnarmed(CompositionInertSession.Mode mode) throws Exception {
        var stage = fixture(); String receiptBefore = CompositionRunFiles.hash(stage.runRoot().resolve("stage-receipt.json"));
        var outcome = CompositionInertSession.run(stage, jar(null, null, false), ID, mode, LIMITS, () -> false);
        assertEquals("PUBLISHED_INERT_ONLY", outcome.status(), outcome.toString()); assertTrue(outcome.errors().isEmpty());
        assertEquals(0, outcome.collection().get("exitCode")); assertEquals(true, outcome.collection().get("stdoutComplete"));
        assertEquals(true, outcome.collection().get("stderrComplete"));
        Path dir = outcome.directory(); assertEquals(outcome.receiptSha256(), CompositionRunFiles.hash(dir.resolve("evidence/session-receipt.json")));
        assertEquals(receiptBefore, CompositionRunFiles.hash(stage.runRoot().resolve("stage-receipt.json")));
        stage.recheck(() -> false); assertFalse(Files.exists(stage.plan().workingDirectory().resolve("composition-probe-authorized.txt")));
        var plan = CorpusJson.read(dir.resolve("session-plan.json"));
        assertEquals("INERT_HELPER_ONLY", plan.get("sessionKind")); assertEquals(false, plan.get("nativeArgumentsUsed"));
        assertEquals(stage.plan().sha256(), plan.get("stagePlanSha256"));
        assertNotEquals(stage.plan().sha256(), outcome.collection().get("planSha256"));
        if (mode == CompositionInertSession.Mode.OK) assertEquals("OUT_OK\n", Files.readString(dir.resolve("evidence/stdout.log")));
        if (mode == CompositionInertSession.Mode.ENVIRONMENT)
            assertTrue(Files.readString(dir.resolve("evidence/stdout.log")).contains("MINIMAL_ENVIRONMENT=true;OWNED_HOMES=true"));
        assertEquals(false, outcome.toTree().get("nativeBehaviorVerified"));
    }
    @ParameterizedTest @EnumSource(value = CompositionInertSession.Mode.class, names = {"EXIT", "SPAM", "INVALID", "SENSITIVE", "DRIFT"})
    void failedOrUnsafeCollectorNeverPublishes(CompositionInertSession.Mode mode) throws Exception {
        var outcome = run(mode); notPublished(outcome);
        assertTrue(outcome.errors().contains(switch (mode) {
            case EXIT -> "NONZERO_EXIT"; case SPAM -> "OUTPUT_LIMIT"; case INVALID -> "INVALID_UTF8";
            case SENSITIVE -> "SENSITIVE_OUTPUT"; case DRIFT -> "INPUT_DRIFT"; default -> throw new AssertionError();
        }), outcome.toString());
    }

    @ParameterizedTest @ValueSource(strings = {"Launcher-Agent-Class", "Premain-Class", "Agent-Class", "Class-Path", "Boot-Class-Path", "Multi-Release", "Add-Opens", "Add-Exports"})
    void manifestContractRejectsAgentsAndImplicitLookupBeforeStarting(String attribute) throws Exception {
        var outcome = CompositionInertSession.run(fixture(), jar(attribute, null, false), ID, CompositionInertSession.Mode.OK, LIMITS, () -> false);
        notPublished(outcome); assertNull(outcome.directory()); assertTrue(outcome.collection().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"META-INF/INDEX.LIST", "META-INF/services/unknown", "META-INF/versions/17/Extra.class", "module-info.class", "extra.jar", "../escape", "other.class"})
    void extraJarEntriesCannotBeApprovedByNameOrCallerHash(String extra) throws Exception {
        var outcome = CompositionInertSession.run(fixture(), jar(null, extra, false), ID, CompositionInertSession.Mode.OK, LIMITS, () -> false);
        notPublished(outcome); assertNull(outcome.directory());
    }
    @Test void differentBytecodeIsNotAcceptedEvenWithTheExpectedClassName() throws Exception {
        var outcome = CompositionInertSession.run(fixture(), jar(null, null, true), ID, CompositionInertSession.Mode.OK, LIMITS, () -> false);
        notPublished(outcome); assertNull(outcome.directory());
    }
    @Test void cancellationBeforePreparationDoesNotWrite() throws Exception {
        var outcome = CompositionInertSession.run(fixture(), jar(null, null, false), ID, CompositionInertSession.Mode.OK, LIMITS, () -> true);
        assertEquals("CANCELLED", outcome.status()); notPublished(outcome); assertNull(outcome.directory());
    }
    @ParameterizedTest @EnumSource(value = CompositionInertSession.Phase.class, names = {"PREPARED", "COLLECTED", "BEFORE_PUBLICATION"})
    void cancellationAtEverySessionBarrierWithholdsPublication(CompositionInertSession.Phase phase) throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        var outcome = CompositionInertSession.run(fixture(), jar(null, null, false), ID, CompositionInertSession.Mode.OK, LIMITS, cancelled::get,
                (current, dir) -> { if (current == phase) cancelled.set(true); });
        assertEquals("CANCELLED", outcome.status()); notPublished(outcome);
    }
    @ParameterizedTest @EnumSource(value = CompositionInertSession.Phase.class, names = {"PREPARED", "COLLECTED", "BEFORE_PUBLICATION"})
    void stageDriftIncludingNativeMutablePropertiesBlocksInertSession(CompositionInertSession.Phase phase) throws Exception {
        var stage = fixture(); AtomicBoolean reached = new AtomicBoolean();
        var outcome = CompositionInertSession.run(stage, jar(null, null, false), ID, CompositionInertSession.Mode.OK, LIMITS, () -> false,
                (current, dir) -> { if (current == phase) { reached.set(true); Files.writeString(stage.plan().workingDirectory().resolve("server.properties"), "changed"); } });
        notPublished(outcome); assertTrue(reached.get(), outcome.toString());
    }
    @Test void unexpectedWorkFileBlocksWithoutRemovingIt() throws Exception {
        var outcome = CompositionInertSession.run(fixture(), jar(null, null, false), ID, CompositionInertSession.Mode.OK, LIMITS, () -> false,
                (phase, dir) -> { if (phase == CompositionInertSession.Phase.PREPARED) Files.writeString(dir.resolve("work/unknown"), "user-data"); });
        notPublished(outcome); assertEquals("user-data", Files.readString(outcome.directory().resolve("work/unknown")));
    }
    @Test void publicationConflictRetainsUnknownFileAndDoesNotReplaceIt() throws Exception {
        var outcome = CompositionInertSession.run(fixture(), jar(null, null, false), ID, CompositionInertSession.Mode.OK, LIMITS, () -> false,
                (phase, dir) -> { if (phase == CompositionInertSession.Phase.BEFORE_PUBLICATION) Files.writeString(dir.resolve("evidence"), "user-data"); });
        notPublished(outcome); assertEquals("user-data", Files.readString(outcome.directory().resolve("evidence")));
    }
    @Test void timeoutStopsOnlyTheInertChildAndDoesNotPublish() throws Exception {
        var outcome = CompositionInertSession.run(fixture(), jar(null, null, false), ID, CompositionInertSession.Mode.SLEEP,
                new CompositionProcessCollector.Limits(200, 2_000, 1024), () -> false);
        notPublished(outcome); assertEquals("TIMED_OUT", outcome.status()); assertEquals(true, outcome.collection().get("processExited"));
    }
    @Test void onlyOneSessionRunsAndThereIsNoQueue() throws Exception {
        var stage = fixture(); Path helper = jar(null, null, false); CountDownLatch ready = new CountDownLatch(1), release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> CompositionInertSession.run(stage, helper, ID, CompositionInertSession.Mode.OK, LIMITS, () -> false,
                    (phase, dir) -> { if (phase == CompositionInertSession.Phase.PREPARED) { ready.countDown();
                        try { if (!release.await(60, TimeUnit.SECONDS)) throw new IOException(); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(); } } }));
            assertTrue(ready.await(60, TimeUnit.SECONDS));
            var second = CompositionInertSession.run(stage, helper, "session-second", CompositionInertSession.Mode.OK, LIMITS, () -> false);
            assertEquals("REJECTED", second.status()); assertEquals(java.util.List.of("BUSY"), second.errors()); assertNull(second.directory());
            // Test-host allowance includes repeated filesystem audits/publication, not only the child.
            // Collector timeout/grace, single-slot admission and the no-queue assertion remain unchanged.
            release.countDown(); var completed = first.get(60, TimeUnit.SECONDS);
            assertEquals("PUBLISHED_INERT_ONLY", completed.status(), completed.toString());
        } finally { release.countDown(); executor.shutdown(); assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS)); }
    }
    @Test void usedSessionIdCannotReplaceOrResumeExistingEvidence() throws Exception {
        var stage = fixture(); Path helper = jar(null, null, false);
        var first = CompositionInertSession.run(stage, helper, ID, CompositionInertSession.Mode.OK, LIMITS, () -> false);
        assertEquals("PUBLISHED_INERT_ONLY", first.status());
        var second = CompositionInertSession.run(stage, helper, ID, CompositionInertSession.Mode.OK, LIMITS, () -> false);
        assertEquals("REJECTED", second.status()); assertNull(second.directory());
        assertEquals(first.receiptSha256(), CompositionRunFiles.hash(first.directory().resolve("evidence/session-receipt.json")));
    }
    @Test void callbackFailureIsSanitized() throws Exception {
        var outcome = CompositionInertSession.run(fixture(), jar(null, null, false), ID, CompositionInertSession.Mode.OK, LIMITS,
                () -> { throw new IllegalStateException("synthetic-private"); });
        notPublished(outcome); assertFalse(outcome.toTree().toString().contains("synthetic-private"));
    }
    @ParameterizedTest @ValueSource(strings = {"work/late-extra", "late-extra", "evidence/late-extra"})
    void lateExtraFileCannotReceiveApprovalEvenThoughPrivateReceiptExists(String name) throws Exception {
        var outcome = CompositionInertSession.run(fixture(), jar(null, null, false), ID, CompositionInertSession.Mode.OK, LIMITS, () -> false,
                (phase, dir) -> { if (phase == CompositionInertSession.Phase.PUBLISHED) Files.writeString(dir.resolve(name), "unapproved"); });
        assertEquals("FAILED", outcome.status()); assertNull(outcome.receiptSha256()); assertFalse(outcome.errors().isEmpty());
        assertTrue(Files.exists(outcome.directory().resolve("evidence/session-receipt.json")));
        assertEquals("unapproved", Files.readString(outcome.directory().resolve(name)));
    }
    @Test void cancellationAfterCompletedPublicationIsNotRetroactivelyReportedAsCancelled() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        var outcome = CompositionInertSession.run(fixture(), jar(null, null, false), ID, CompositionInertSession.Mode.OK, LIMITS, cancelled::get,
                (phase, dir) -> { if (phase == CompositionInertSession.Phase.PUBLISHED) cancelled.set(true); });
        assertEquals("PUBLISHED_INERT_ONLY", outcome.status()); assertNotNull(outcome.receiptSha256());
    }
    @Test void cancellationInsideVerifierKeepsCancelledClassification() throws Exception {
        var stage = fixture(); Path helper = jar(null, null, false); var polls = new java.util.concurrent.atomic.AtomicInteger();
        var outcome = CompositionInertSession.run(stage, helper, ID, CompositionInertSession.Mode.OK, LIMITS, () -> polls.incrementAndGet() >= 2);
        assertEquals("CANCELLED", outcome.status()); notPublished(outcome); assertNull(outcome.directory());
    }
    @Test void cancellationDuringPostExitPublicationRetainsIncompleteEvidenceOnly() throws Exception {
        var stage = fixture(); Path helper = jar(null, null, false);
        Path evidence = root.resolve("build/composition-inert-sessions/" + ID + "/evidence");
        var outcome = CompositionInertSession.run(stage, helper, ID, CompositionInertSession.Mode.OK, LIMITS,
                () -> Files.exists(evidence.resolve("stdout.log")));
        assertEquals("CANCELLED", outcome.status()); notPublished(outcome); assertTrue(Files.isDirectory(evidence));
    }
    @Test void completedHelperLeavesOnlyTheClosedWorkTree() throws Exception {
        var observed = new java.util.concurrent.atomic.AtomicReference<java.util.List<String>>(java.util.List.of());
        var outcome = CompositionInertSession.run(fixture(), jar(null, null, false), ID, CompositionInertSession.Mode.OK, LIMITS, () -> false,
                (phase, dir) -> { if (phase == CompositionInertSession.Phase.COLLECTED) {
                    try (var paths = Files.walk(dir)) { observed.set(paths.map(dir::relativize).map(Path::toString).sorted().toList()); }
                } });
        assertEquals("PUBLISHED_INERT_ONLY", outcome.status(), outcome + "; actual post-exit paths=" + observed.get());
    }
    @Test void interruptionAfterPublicationDoesNotApproveAnUncheckedReceiptOrClaimRetroactiveCancellation() throws Exception {
        var stage = fixture(); Path helper = jar(null, null, false);
        try {
            var outcome = CompositionInertSession.run(stage, helper, ID, CompositionInertSession.Mode.OK, LIMITS, () -> false,
                    (phase, dir) -> { if (phase == CompositionInertSession.Phase.PUBLISHED) Thread.currentThread().interrupt(); });
            assertEquals("FAILED", outcome.status()); assertNull(outcome.receiptSha256());
            assertEquals(java.util.List.of("COMPOSE_INERT_INTEGRITY_RECHECK_INTERRUPTED"), outcome.errors());
            assertTrue(Thread.currentThread().isInterrupted()); assertTrue(Files.exists(outcome.directory().resolve("evidence/session-receipt.json")));
        } finally { Thread.interrupted(); }
    }
}
