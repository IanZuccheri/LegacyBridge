package dev.legacybridge.corpus;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.legacybridge.corpus.CompositionRunFiles.*;
import static dev.legacybridge.corpus.CompositionRunPlanner.Role;
import static org.junit.jupiter.api.Assertions.*;

/** Inert synthetic stage; the fake java.exe must NEVER be executed. No Minecraft/native parser is loaded. */
final class CompositionLaunchConfigurationPlanTest {
    @TempDir Path root;

    @ParameterizedTest @EnumSource(Role.class)
    void proposalIsBoundDeterministicImmutableAndDoesNotMaterializeAnything(Role role) throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, role, null); var before = tree(root);
        var proposed = CompositionLaunchConfigurationPlan.prepare(snapshot, () -> false); var report = proposed.toTree();
        assertEquals("RESTRICTED_CONFIGURATION_PLANNED_ONLY", report.get("status"));
        assertEquals("composition-restricted-settings-v1", report.get("policyVersion"));
        assertEquals(1, report.get("schemaVersion")); assertEquals(role.name(), report.get("role"));
        assertEquals(snapshot.plan().sha256(), report.get("planSha256")); assertEquals(snapshot.receiptSha256(), report.get("stageReceiptSha256"));
        assertEquals(snapshot.toTree(), report.get("stageVerification")); assertEquals(3, report.get("preservedExplicitPropertyCount"));
        assertEquals(role == Role.SOURCE ? 4 : 3, proposed.files().size()); assertFalse(proposed.files().containsKey("world/serverconfig/forge-server.toml"));
        assertTrue(proposed.files().containsKey("defaultconfigs/forge-server.toml"));
        assertEquals(digest(CorpusJson.canonicalJson(report).getBytes(StandardCharsets.UTF_8)), proposed.sha256());
        assertEquals(proposed.sha256(), proposed.recheck(() -> false).sha256()); assertEquals(before, tree(root));
        assertFalse(Files.exists(snapshot.plan().workingDirectory().resolve("world")));
        assertFalse(Files.exists(snapshot.plan().workingDirectory().resolve("composition-probe-authorized.txt")));
        assertFalse(Files.exists(snapshot.plan().workingDirectory().resolve("defaultconfigs")));
        for (Object raw : (List<?>) report.get("proposedFiles")) {
            var file = object(raw); String text = proposed.files().get(file.get("path"));
            assertEquals(text, file.get("content")); assertEquals(text.length(), file.get("bytes"));
            assertEquals(digest(text.getBytes(StandardCharsets.UTF_8)), file.get("sha256"));
            assertEquals(false, file.get("nativeConsumptionVerified"));
        }
        assertAuthorityFalse(report); assertImmutable(report); assertImmutable(proposed.files());
        for (var constructor : CompositionLaunchConfigurationPlan.Plan.class.getDeclaredConstructors()) assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertTrue(((List<?>) report.get("pending")).contains("OS_EGRESS_AND_PROCESS_CONTAINMENT"));
    }

    @ParameterizedTest @ValueSource(strings = {"inputs/fixture.jar", "jdk/bin/java.exe", "jdk/release"})
    void changedOriginalOrJdkCannotReuseBoundProposal(String path) throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, Role.SOURCE, null);
        var plan = CompositionLaunchConfigurationPlan.prepare(snapshot, () -> false);
        Files.writeString(root.resolve(path), "PRIVATE_CHANGED_INPUT"); var before = tree(root);
        cleanFailure(() -> plan.recheck(() -> false)); assertEquals(before, tree(root));
    }

    @ParameterizedTest @ValueSource(strings = {"server.properties", "mods/fixture.jar", "composition-probe-authorized.txt", "world/serverconfig/forge-server.toml", "defaultconfigs/forge-server.toml"})
    void stageMutationOrPrematureMaterializationInvalidatesProposalWithoutRepair(String path) throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, Role.SOURCE, null);
        var plan = CompositionLaunchConfigurationPlan.prepare(snapshot, () -> false);
        Path selected = snapshot.plan().workingDirectory().resolve(path); Files.createDirectories(selected.getParent());
        Files.writeString(selected, "PRIVATE_UNAPPROVED_DATA"); var before = tree(root);
        cleanFailure(() -> plan.recheck(() -> false)); assertEquals(before, tree(root));
    }

    @Test void receiptMutationBlocksPreviouslyPreparedProposal() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, Role.TARGET, null);
        var plan = CompositionLaunchConfigurationPlan.prepare(snapshot, () -> false);
        Files.writeString(snapshot.runRoot().resolve("stage-receipt.json"), "PRIVATE_FORGED_RECEIPT"); var before = tree(root);
        cleanFailure(() -> plan.recheck(() -> false)); assertEquals(before, tree(root));
    }

    @ParameterizedTest @ValueSource(strings = {"online-mode=false\n", "max-tick-time=0\n", "resource-pack=https://private.invalid/secret\n"})
    void additionalExplicitBaselineValuesCannotBeSilentlyOverridden(String additional) throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, Role.SOURCE, null, additional); var before = tree(root);
        cleanFailure(() -> CompositionLaunchConfigurationPlan.prepare(snapshot, () -> false)); assertEquals(before, tree(root));
    }

    @Test void allAdditionalMatchingBaselinePropertiesRemainBoundAndCounted() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, Role.TARGET, null,
                "online-mode=true\nmax-tick-time=60000\nresource-pack=\nenable-rcon=false\n"); var before = tree(root);
        assertEquals(7, CompositionLaunchConfigurationPlan.prepare(snapshot, () -> false).toTree().get("preservedExplicitPropertyCount"));
        assertEquals(before, tree(root));
    }

    @Test void cancellationAndCallbacksAbortEarlyAndDuringRecheckWithoutPublishingOrClearingInterrupt() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, Role.SOURCE, null);
        var plan = CompositionLaunchConfigurationPlan.prepare(snapshot, () -> false); var before = tree(root);
        assertEquals("COMPOSE_SETTINGS_CANCELLED", assertThrows(IOException.class, () -> plan.recheck(() -> true)).getMessage());
        var calls = new AtomicInteger();
        assertEquals("COMPOSE_SETTINGS_CANCELLED", assertThrows(IOException.class,
                () -> plan.recheck(() -> calls.incrementAndGet() > 5)).getMessage());
        IOException callback = assertThrows(IOException.class, () -> plan.recheck(() -> { throw new IllegalStateException("PRIVATE_CALLBACK"); }));
        assertEquals("COMPOSE_SETTINGS_CANCELLATION_CALLBACK", callback.getMessage()); assertNull(callback.getCause());
        cleanFailure(() -> plan.recheck(null)); cleanFailure(() -> CompositionLaunchConfigurationPlan.prepare(null, () -> false));
        try {
            Thread.currentThread().interrupt();
            assertEquals("COMPOSE_SETTINGS_CANCELLED", assertThrows(IOException.class, () -> plan.recheck(() -> false)).getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertEquals(before, tree(root));
    }

    @Test void publicSelectorCannotAdoptSyntheticInputsOrUnreviewedJson() throws Exception {
        var snapshot = CompositionStagedRunVerifierTest.fixture(root, Role.SOURCE, null); var before = tree(root);
        assertThrows(IOException.class, () -> CompositionStagedRunVerifier.verify(root,
                new CompositionRunPlanner.Request(Role.SOURCE, snapshot.plan().runId(), root.resolve("jdk")),
                snapshot.plan().sha256(), snapshot.receiptSha256(), () -> false));
        assertEquals(before, tree(root));
    }

    private static void cleanFailure(org.junit.jupiter.api.function.Executable action) {
        IOException failure = assertThrows(IOException.class, action);
        assertTrue(failure.getMessage().matches("COMPOSE_SETTINGS_[A-Z0-9_]+")); assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("PRIVATE"));
    }
    private static void assertAuthorityFalse(Map<String, Object> report) {
        for (String flag : List.of("materialized", "executionAllowed", "installationAllowed", "gameExecutionVerified",
                "nativeLaunchContractVerified", "networkIsolationVerified", "nativeSaveVerified")) assertEquals(false, report.get(flag));
        assertEquals("NOT_RUN", report.get("runtimeValidation"));
    }
    private static Map<String, String> tree(Path root) throws IOException {
        var tree = new LinkedHashMap<String, String>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) tree.put(root.relativize(path).toString(), Files.isDirectory(path) ? "DIRECTORY" : hash(path));
        }
        return tree;
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertImmutable(Object value) {
        if (value instanceof Map map) { assertThrows(UnsupportedOperationException.class, () -> map.put("forged", true)); map.values().forEach(CompositionLaunchConfigurationPlanTest::assertImmutable); }
        else if (value instanceof List list) { assertThrows(UnsupportedOperationException.class, () -> list.add("forged")); list.forEach(CompositionLaunchConfigurationPlanTest::assertImmutable); }
    }
}
