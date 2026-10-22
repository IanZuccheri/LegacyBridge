package dev.legacybridge.corpus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Starts only the inert JDK-only helper; these tests must never launch native game/Forge classes. */
@Execution(ExecutionMode.SAME_THREAD)
class CompositionProcessCollectorTest {
    @TempDir Path cwd;
    Path java, helper, input;
    List<CompositionProcessCollector.Input> inputs;
    private static final String PLAN = "1".repeat(64);
    private static final CompositionProcessCollector.Limits NORMAL = new CompositionProcessCollector.Limits(10_000, 2_000, 1 << 20);

    @BeforeEach void prepareInertHelper() throws Exception {
        Files.createDirectories(cwd.resolve("empty-user-home/absent-gradle")); Files.createDirectory(cwd.resolve("tmp"));
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
        java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toRealPath();
        helper = cwd.resolve("inert-helper.jar");
        Manifest manifest = new Manifest(); manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, CompositionCollectorHelper.class.getName());
        String entry = CompositionCollectorHelper.class.getName().replace('.', '/') + ".class";
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(helper), manifest);
             InputStream bytecode = CompositionCollectorHelper.class.getClassLoader().getResourceAsStream(entry)) {
            assertNotNull(bytecode); output.putNextEntry(new JarEntry(entry)); bytecode.transferTo(output); output.closeEntry();
        }
        input = Files.writeString(cwd.resolve("input.dat"), "original");
        inputs = new ArrayList<>(List.of(pin(java), pin(helper), pin(input)));
    }

    private CompositionProcessCollector.Launch launch(String mode) { return launch(mode, NORMAL); }
    private CompositionProcessCollector.Launch launch(String mode, CompositionProcessCollector.Limits limits) {
        return new CompositionProcessCollector.Launch(cwd, args(mode), PLAN, inputs, limits);
    }
    private List<String> args(String mode) {
        return new ArrayList<>(List.of(java.toString(), "-Xms16M", "-Xmx32M", "-Duser.home=" + cwd.resolve("empty-user-home"),
                "-Djava.io.tmpdir=" + cwd.resolve("tmp"), "-jar", helper.toString(), mode));
    }
    private CompositionProcessCollector.Result collect(String mode) { return CompositionProcessCollector.collect(launch(mode), () -> false); }
    private static CompositionProcessCollector.Input pin(Path path) throws Exception {
        return new CompositionProcessCollector.Input(path, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))), Files.size(path));
    }
    private static String stdout(CompositionProcessCollector.Result result) { return new String(result.stdout().orElseThrow(), StandardCharsets.UTF_8); }
    private static void failedWithoutRawBytes(CompositionProcessCollector.Result result, CompositionProcessCollector.Code code) {
        assertTrue(result.errors().contains(code), result.toString()); assertTrue(result.stdout().isEmpty()); assertTrue(result.stderr().isEmpty());
        assertEquals(false, result.toTree().get("gameExecutionVerified")); assertEquals(false, result.toTree().get("installationAllowed"));
        assertEquals(false, result.toTree().get("launchContractVerified")); assertEquals(false, result.toTree().get("executionAllowed"));
        if (result.pid() > 0) assertFalse(ProcessHandle.of(result.pid()).map(ProcessHandle::isAlive).orElse(false), "Only the inert owned child must be stopped");
    }

    @Test void capturesActualChildExitAndBothEofsWithoutNativeClaimsOrRawLogFiles() throws Exception {
        long filesBefore; try (var files = Files.walk(cwd)) { filesBefore = files.filter(Files::isRegularFile).count(); }
        var result = collect("ok"); assertEquals(CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY, result.status(), result.toString());
        assertEquals("OUT_OK\n", stdout(result)); assertEquals("ERR_OK\n", new String(result.stderr().orElseThrow(), StandardCharsets.UTF_8));
        assertTrue(result.processExited()); assertTrue(result.stdoutComplete()); assertTrue(result.stderrComplete()); assertEquals(0, result.exitCode());
        assertTrue(result.errors().isEmpty()); assertEquals(PLAN, result.planSha256());
        assertEquals(false, result.toTree().get("nativeBehaviorVerified")); assertEquals(false, result.toTree().get("launchContractVerified"));
        assertEquals(false, result.toTree().get("gameExecutionVerified")); assertEquals(false, result.toTree().get("installationAllowed"));
        assertEquals(false, result.toTree().get("executionAllowed"));
        try (var files = Files.walk(cwd)) { assertEquals(filesBefore, files.filter(Files::isRegularFile).count()); }
        assertEquals("original", Files.readString(input));
    }
    @Test void resultCopiesRawBytesDefensively() {
        var result = collect("ok"); byte[] copy = result.stdout().orElseThrow(); copy[0] = 'X'; assertEquals("OUT_OK\n", stdout(result));
        assertThrows(UnsupportedOperationException.class, () -> result.toTree().put("extra", true));
    }
    @Test void closesStdinSoChildActuallyObservesEof() {
        var result = collect("stdin"); assertEquals(CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY, result.status(), result.toString());
        assertEquals("STDIN_EOF\n", stdout(result)); assertEquals(0, result.stderr().orElseThrow().length);
    }
    @Test void drainsBothPipesConcurrentlyWithoutDeadlock() {
        var result = collect("both"); assertEquals(CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY, result.status(), result.toString());
        assertEquals(10_000, stdout(result).lines().count()); assertEquals(10_000, new String(result.stderr().orElseThrow(), StandardCharsets.UTF_8).lines().count());
    }
    @Test void nonZeroExitIsExplicitAndRawBytesAreNotReturned() {
        var result = collect("exit"); assertEquals(CompositionProcessCollector.State.FAILED, result.status()); assertEquals(7, result.exitCode());
        failedWithoutRawBytes(result, CompositionProcessCollector.Code.NONZERO_EXIT);
    }
    @Test void invalidUtf8IsRejectedAfterActualEof() {
        var result = collect("invalid"); failedWithoutRawBytes(result, CompositionProcessCollector.Code.INVALID_UTF8);
        assertTrue(result.stdoutComplete()); assertTrue(result.stderrComplete());
    }
    @Test void sensitiveOutputNeverEscapesInBytesDiagnosticsOrMetadata() {
        var result = collect("sensitive"); failedWithoutRawBytes(result, CompositionProcessCollector.Code.SENSITIVE_OUTPUT);
        assertFalse(result.toString().contains("synthetic-private")); assertFalse(result.toTree().toString().contains("synthetic-private"));
    }
    @Test void outputOverflowStopsOnlyTheOwnedChildAndDiscardsPartialCapture() {
        var result = CompositionProcessCollector.collect(launch("spam", new CompositionProcessCollector.Limits(10_000, 2_000, 4096)), () -> false);
        failedWithoutRawBytes(result, CompositionProcessCollector.Code.OUTPUT_LIMIT);
    }
    @Test void timeoutIsDistinctAndWaitsForOwnedChildDeath() {
        var result = CompositionProcessCollector.collect(launch("sleep", new CompositionProcessCollector.Limits(500, 2_000, 4096)), () -> false);
        assertEquals(CompositionProcessCollector.State.TIMED_OUT, result.status()); failedWithoutRawBytes(result, CompositionProcessCollector.Code.TIMED_OUT);
        assertTrue(result.processExited()); assertEquals(true, result.toTree().get("terminationRequested"));
    }
    @Test void cancellationBeforeStartCreatesNoChild() {
        var result = CompositionProcessCollector.collect(launch("ok"), () -> true);
        assertEquals(CompositionProcessCollector.State.CANCELLED, result.status()); assertEquals(-1, result.pid());
        failedWithoutRawBytes(result, CompositionProcessCollector.Code.CANCELLED);
    }
    @Test void existingThreadInterruptBlocksLaunchAndRemainsVisibleToCaller() {
        CompositionProcessCollector.Result result;
        Thread.currentThread().interrupt();
        try {
            result = collect("ok");
            assertTrue(Thread.currentThread().isInterrupted()); assertEquals(CompositionProcessCollector.State.CANCELLED, result.status());
            assertEquals(-1, result.pid()); failedWithoutRawBytes(result, CompositionProcessCollector.Code.INTERRUPTED);
        } finally { Thread.interrupted(); } // Restore the JUnit worker; collector itself must not clear caller interruption.
    }
    @Test void cancellationDuringChildExecutionIsSafeAndDoesNotReturnPartialOutput() {
        AtomicInteger polls = new AtomicInteger();
        var result = CompositionProcessCollector.collect(launch("sleep"), () -> polls.incrementAndGet() >= 3);
        assertEquals(CompositionProcessCollector.State.CANCELLED, result.status()); assertTrue(result.pid() > 0);
        failedWithoutRawBytes(result, CompositionProcessCollector.Code.CANCELLED);
    }
    @Test void cancellationCallbackFailureCannotLeakItsExceptionMessage() {
        var result = CompositionProcessCollector.collect(launch("ok"), () -> { throw new IllegalStateException("synthetic-private"); });
        failedWithoutRawBytes(result, CompositionProcessCollector.Code.CANCELLATION_CALLBACK_FAILED);
        assertFalse(result.toTree().toString().contains("synthetic-private"));
    }
    @Test void cancellationAtCompletionBarrierWithholdsSuccessfulChildOutputAndReleasesSlot() {
        AtomicBoolean atCompletion = new AtomicBoolean();
        var result = CompositionProcessCollector.collect(launch("ok"), atCompletion::get, () -> atCompletion.set(true));
        assertTrue(atCompletion.get()); assertEquals(CompositionProcessCollector.State.CANCELLED, result.status());
        assertTrue(result.processExited()); assertEquals(0, result.exitCode());
        assertTrue(result.stdoutComplete()); assertTrue(result.stderrComplete());
        failedWithoutRawBytes(result, CompositionProcessCollector.Code.CANCELLED);
        assertEquals(CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY, collect("ok").status());
    }
    @Test void cancellationCallbackFailureAtCompletionBarrierIsSanitizedAndWithholdsOutput() {
        AtomicBoolean atCompletion = new AtomicBoolean();
        var result = CompositionProcessCollector.collect(launch("ok"), () -> {
            if (atCompletion.get()) throw new IllegalStateException("synthetic-private"); return false;
        }, () -> atCompletion.set(true));
        assertTrue(atCompletion.get()); assertEquals(CompositionProcessCollector.State.FAILED, result.status());
        assertTrue(result.processExited()); assertEquals(0, result.exitCode());
        failedWithoutRawBytes(result, CompositionProcessCollector.Code.CANCELLATION_CALLBACK_FAILED);
        assertFalse(result.toString().contains("synthetic-private")); assertFalse(result.toTree().toString().contains("synthetic-private"));
        assertEquals(CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY, collect("ok").status());
    }
    @Test void interruptAtCompletionBarrierIsPreservedAndWithholdsOutput() {
        try {
            var result = CompositionProcessCollector.collect(launch("ok"), () -> false, () -> Thread.currentThread().interrupt());
            assertTrue(Thread.currentThread().isInterrupted()); assertEquals(CompositionProcessCollector.State.CANCELLED, result.status());
            assertTrue(result.processExited()); assertEquals(0, result.exitCode());
            failedWithoutRawBytes(result, CompositionProcessCollector.Code.INTERRUPTED);
        } finally { Thread.interrupted(); }
        assertEquals(CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY, collect("ok").status());
    }
    @Test void completionHookFailureIsSanitizedAndDoesNotLeakTheProcessSlot() {
        var result = CompositionProcessCollector.collect(launch("ok"), () -> false, () -> { throw new IllegalStateException("synthetic-private"); });
        assertEquals(CompositionProcessCollector.State.FAILED, result.status()); assertEquals(0, result.exitCode());
        failedWithoutRawBytes(result, CompositionProcessCollector.Code.COMPLETION_CALLBACK_FAILED);
        assertFalse(result.toTree().toString().contains("synthetic-private"));
        assertEquals(CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY, collect("ok").status());
    }
    @Test void concurrentCollectorIsRefusedWithoutASecondChildOrQueue() throws Exception {
        CountDownLatch started = new CountDownLatch(1); AtomicInteger polls = new AtomicInteger(); AtomicBoolean cancel = new AtomicBoolean();
        ExecutorService executor = Executors.newSingleThreadExecutor(); Future<CompositionProcessCollector.Result> first = null;
        try {
            first = executor.submit(() -> CompositionProcessCollector.collect(launch("sleep"), () -> {
                if (polls.incrementAndGet() >= 2) started.countDown(); return cancel.get();
            }));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var second = collect("ok"); assertEquals(CompositionProcessCollector.State.REJECTED, second.status());
            failedWithoutRawBytes(second, CompositionProcessCollector.Code.BUSY); assertEquals(-1, second.pid());
        } finally {
            cancel.set(true); if (first != null) failedWithoutRawBytes(first.get(5, TimeUnit.SECONDS), CompositionProcessCollector.Code.CANCELLED);
            executor.shutdown(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY, collect("ok").status());
    }
    @Test void externallyInterruptedCollectorStopsItsChildAndPreservesInterruptFlag() throws Exception {
        CountDownLatch started = new CountDownLatch(1); AtomicInteger polls = new AtomicInteger();
        var result = new java.util.concurrent.atomic.AtomicReference<CompositionProcessCollector.Result>(); AtomicBoolean flag = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            result.set(CompositionProcessCollector.collect(launch("sleep"), () -> { if (polls.incrementAndGet() >= 2) started.countDown(); return false; }));
            flag.set(Thread.currentThread().isInterrupted());
        });
        worker.start(); assertTrue(started.await(5, TimeUnit.SECONDS)); worker.interrupt(); worker.join(5_000);
        assertFalse(worker.isAlive()); assertTrue(flag.get()); assertEquals(CompositionProcessCollector.State.CANCELLED, result.get().status());
        failedWithoutRawBytes(result.get(), CompositionProcessCollector.Code.INTERRUPTED);
    }
    @Test void postExitInputDriftBlocksSuccessfulCollection() {
        var result = collect("drift"); failedWithoutRawBytes(result, CompositionProcessCollector.Code.INPUT_DRIFT);
        assertEquals(0, result.exitCode()); assertTrue(result.processExited());
    }
    @Test void preLaunchInputDriftPreventsStartingAnyChild() throws Exception {
        Files.writeString(input, "changed"); var result = collect("ok"); assertEquals(-1, result.pid()); failedWithoutRawBytes(result, CompositionProcessCollector.Code.INPUT_REJECTED);
    }
    @Test void selectedJavaExecutableHashIsMandatoryAndNotTakenFromNameAlone() {
        inputs.remove(0); var result = collect("ok"); assertEquals(-1, result.pid()); failedWithoutRawBytes(result, CompositionProcessCollector.Code.INVALID_ARGUMENTS);
    }
    @Test void failedExecutableStartIsSanitizedAndReleasesTheSlot() throws Exception {
        Path inert = Files.writeString(cwd.resolve(java.getFileName()), "not-an-executable");
        List<String> arguments = args("ok"); arguments.set(0, inert.toString()); inputs.add(pin(inert));
        var result = CompositionProcessCollector.collect(new CompositionProcessCollector.Launch(cwd, arguments, PLAN, inputs, NORMAL), () -> false);
        assertEquals(-1, result.pid()); failedWithoutRawBytes(result, CompositionProcessCollector.Code.START_FAILED);
        assertEquals(CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY, collect("ok").status());
    }
    @Test void hardLinkedInputAliasesBlockBeforeAnyChild() throws Exception {
        Path alias = Files.createLink(cwd.resolve("alias.dat"), input); inputs.add(pin(alias));
        var result = collect("ok"); assertEquals(-1, result.pid()); failedWithoutRawBytes(result, CompositionProcessCollector.Code.INPUT_REJECTED);
    }
    @Test void minimalEnvironmentAndOwnedHomesAreAppliedNotOnlyDeclared() {
        var result = collect("environment"); assertEquals(CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY, result.status(), result.toString());
        assertTrue(stdout(result).contains("MINIMAL_ENVIRONMENT=true;OWNED_HOMES=true"));
    }
    @Test void immutableLaunchCopiesPreventExternalArgumentAndInputMutation() {
        List<String> args = args("ok"); var launch = new CompositionProcessCollector.Launch(cwd, args, PLAN, inputs, NORMAL);
        args.clear(); inputs.clear(); assertFalse(launch.arguments().isEmpty()); assertFalse(launch.inputs().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> launch.arguments().clear());
        assertEquals(CompositionProcessCollector.State.PROCESS_COLLECTED_ONLY, CompositionProcessCollector.collect(launch, () -> false).status());
    }
    @ParameterizedTest @ValueSource(strings = {"-Xmx3G", "-Xmx0M", "-Xmxinvalid", "-XX:MaxHeapSize=9999999999", "-javaagent:unknown.jar", "@external.args"})
    void unsafeHeapOrInjectedJvmOptionsAreRejectedBeforeStart(String bad) {
        List<String> args = args("ok"); args.set(2, bad);
        var result = CompositionProcessCollector.collect(new CompositionProcessCollector.Launch(cwd, args, PLAN, inputs, NORMAL), () -> false);
        assertEquals(-1, result.pid()); failedWithoutRawBytes(result, CompositionProcessCollector.Code.INVALID_ARGUMENTS);
    }
    @ParameterizedTest @ValueSource(strings = {"--world", "--world=outside", "--universe", "--gameDir", "--accessToken=synthetic-private"})
    void unsafeGameOrSensitiveArgumentsAreNotForwarded(String bad) {
        List<String> args = args("ok"); args.add(bad);
        var result = CompositionProcessCollector.collect(new CompositionProcessCollector.Launch(cwd, args, PLAN, inputs, NORMAL), () -> false);
        assertEquals(-1, result.pid()); failedWithoutRawBytes(result, CompositionProcessCollector.Code.INVALID_ARGUMENTS);
    }
    @Test void maxInitialHeapCannotExceedMaximumHeap() {
        List<String> args = args("ok"); args.set(1, "-Xms64M");
        var result = CompositionProcessCollector.collect(new CompositionProcessCollector.Launch(cwd, args, PLAN, inputs, NORMAL), () -> false);
        failedWithoutRawBytes(result, CompositionProcessCollector.Code.INVALID_ARGUMENTS);
    }
    @Test void callerCannotRedirectHomeOutsideOwnedCwd() {
        List<String> args = args("ok"); args.set(3, "-Duser.home=" + cwd.getParent());
        failedWithoutRawBytes(CompositionProcessCollector.collect(new CompositionProcessCollector.Launch(cwd, args, PLAN, inputs, NORMAL), () -> false), CompositionProcessCollector.Code.INVALID_ARGUMENTS);
    }
    @Test void missingOwnedDirectoryIsNotCreatedByCollector() throws Exception {
        Files.delete(cwd.resolve("tmp")); var result = collect("ok"); failedWithoutRawBytes(result, CompositionProcessCollector.Code.INPUT_REJECTED);
        assertFalse(Files.exists(cwd.resolve("tmp")));
    }
    @Test void invalidLimitsAreNotSilentlyClamped() {
        var limits = List.of(new CompositionProcessCollector.Limits(0, 1, 1), new CompositionProcessCollector.Limits(240001, 1, 1),
                new CompositionProcessCollector.Limits(1, 60001, 1), new CompositionProcessCollector.Limits(1, 0, 1),
                new CompositionProcessCollector.Limits(1, 1, 0), new CompositionProcessCollector.Limits(1, 1, (8 << 20) + 1));
        for (var limit : limits) failedWithoutRawBytes(CompositionProcessCollector.collect(launch("ok", limit), () -> false), CompositionProcessCollector.Code.INVALID_LIMITS);
    }
    @Test void suppliedPlanDigestIsOnlyABindingAndMalformedDigestIsRejected() {
        var result = CompositionProcessCollector.collect(new CompositionProcessCollector.Launch(cwd, args("ok"), "not-a-hash", inputs, NORMAL), () -> false);
        failedWithoutRawBytes(result, CompositionProcessCollector.Code.INVALID_LAUNCH); assertNull(result.planSha256());
    }
    @ParameterizedTest @ValueSource(strings = {"-XX:+UsePerfData", "-XX:UsePerfData=false", "-XX:-UsePerfData -Xmx2G"})
    void permittingExactPerfDataSuppressionDoesNotPermitOtherVmOptions(String option) {
        List<String> args = args("ok"); args.add(1, option);
        var result = CompositionProcessCollector.collect(new CompositionProcessCollector.Launch(cwd, args, PLAN, inputs, NORMAL), () -> false);
        assertEquals(-1, result.pid()); failedWithoutRawBytes(result, CompositionProcessCollector.Code.INVALID_ARGUMENTS);
    }
    @Test void repeatedPerfDataSuppressionIsRejectedBeforeStarting() {
        List<String> args = args("ok"); args.add(1, "-XX:-UsePerfData"); args.add(1, "-XX:-UsePerfData");
        var result = CompositionProcessCollector.collect(new CompositionProcessCollector.Launch(cwd, args, PLAN, inputs, NORMAL), () -> false);
        assertEquals(-1, result.pid()); failedWithoutRawBytes(result, CompositionProcessCollector.Code.INVALID_ARGUMENTS);
    }
}
