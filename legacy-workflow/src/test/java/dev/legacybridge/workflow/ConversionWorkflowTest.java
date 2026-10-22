package dev.legacybridge.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionWorkflowTest {
    private static final UUID ORIGIN = UUID.fromString("ded68347-edb5-43e3-b264-8f8380c86ea8");
    private static final UUID NEXT_LAUNCH = UUID.fromString("fb7af9aa-e856-4bf6-8015-846d5ca3b481");
    private static final List<WorkflowStage> HAPPY_PATH = List.of(
            WorkflowStage.DISCOVERED, WorkflowStage.ANALYZING, WorkflowStage.ANALYZED,
            WorkflowStage.CONVERTING, WorkflowStage.VALIDATING, WorkflowStage.READY_TO_APPLY,
            WorkflowStage.APPLYING, WorkflowStage.PENDING_RESTART);

    @Test
    void newAttemptStartsAtDiscoveryWithTheRealOriginatingLaunchIdentity() {
        ConversionWorkflow workflow = ConversionWorkflow.discovered(ORIGIN);
        assertEquals(WorkflowStage.DISCOVERED, workflow.stage());
        assertEquals(ORIGIN, workflow.originatingLaunchId());
        assertThrows(NullPointerException.class, () -> ConversionWorkflow.discovered(null));
    }

    @Test
    void happyPathSeparatesAnalysisConversionValidationInstallationAndActivation() {
        ConversionWorkflow workflow = ConversionWorkflow.discovered(ORIGIN);
        for (WorkflowStage stage : HAPPY_PATH.subList(1, HAPPY_PATH.size())) {
            workflow = workflow.transitionTo(stage);
            assertEquals(stage, workflow.stage());
            assertEquals(ORIGIN, workflow.originatingLaunchId());
            assertNotEquals(WorkflowStage.ACTIVATED, workflow.stage());
        }
        assertEquals(WorkflowStage.ACTIVATED, workflow.observeActivated(NEXT_LAUNCH).stage());
    }

    @Test
    void transitionsReturnNewValuesAndDoNotMutateEarlierSnapshots() {
        ConversionWorkflow discovered = ConversionWorkflow.discovered(ORIGIN);
        ConversionWorkflow analyzing = discovered.transitionTo(WorkflowStage.ANALYZING);
        assertNotSame(discovered, analyzing);
        assertEquals(WorkflowStage.DISCOVERED, discovered.stage());
        assertEquals(WorkflowStage.ANALYZING, analyzing.stage());
        assertEquals(ConversionWorkflow.discovered(ORIGIN), discovered);
        assertEquals(ConversionWorkflow.discovered(ORIGIN).hashCode(), discovered.hashCode());
        assertNotEquals(discovered, analyzing);
        assertNotEquals(discovered, ConversionWorkflow.discovered(NEXT_LAUNCH));
    }

    @Test
    void everyOrdinaryTransitionMatchesTheFiniteContract() {
        Map<WorkflowStage, Set<WorkflowStage>> expected = Map.ofEntries(
                Map.entry(WorkflowStage.DISCOVERED, Set.of(WorkflowStage.ANALYZING,
                        WorkflowStage.FAILED, WorkflowStage.CANCELLED)),
                Map.entry(WorkflowStage.ANALYZING, Set.of(WorkflowStage.ANALYZED,
                        WorkflowStage.BLOCKED, WorkflowStage.FAILED, WorkflowStage.CANCELLED)),
                Map.entry(WorkflowStage.ANALYZED, Set.of(WorkflowStage.CONVERTING,
                        WorkflowStage.FAILED, WorkflowStage.CANCELLED)),
                Map.entry(WorkflowStage.CONVERTING, Set.of(WorkflowStage.VALIDATING,
                        WorkflowStage.BLOCKED, WorkflowStage.FAILED, WorkflowStage.CANCELLED)),
                Map.entry(WorkflowStage.VALIDATING, Set.of(WorkflowStage.READY_TO_APPLY,
                        WorkflowStage.BLOCKED, WorkflowStage.FAILED, WorkflowStage.CANCELLED)),
                Map.entry(WorkflowStage.READY_TO_APPLY, Set.of(WorkflowStage.APPLYING,
                        WorkflowStage.FAILED, WorkflowStage.CANCELLED)),
                Map.entry(WorkflowStage.APPLYING, Set.of(WorkflowStage.PENDING_RESTART,
                        WorkflowStage.RECOVERY_REQUIRED)));
        for (ConversionWorkflow workflow : everyReachableStage()) {
            Set<WorkflowStage> legal = expected.getOrDefault(workflow.stage(), Set.of());
            assertEquals(legal, workflow.allowedTransitions(), workflow.stage().name());
            for (WorkflowStage target : WorkflowStage.values()) {
                if (legal.contains(target)) {
                    assertEquals(target, workflow.transitionTo(target).stage());
                } else {
                    assertThrows(IllegalStateException.class, () -> workflow.transitionTo(target),
                            workflow.stage() + " -> " + target);
                }
            }
        }
    }

    @Test
    void discoveryCannotSkipAnalysisOrConversion() {
        ConversionWorkflow discovered = ConversionWorkflow.discovered(ORIGIN);
        assertThrows(IllegalStateException.class, () -> discovered.transitionTo(WorkflowStage.CONVERTING));
        assertThrows(IllegalStateException.class, () -> discovered.transitionTo(WorkflowStage.READY_TO_APPLY));
        assertThrows(IllegalStateException.class, () -> discovered.transitionTo(WorkflowStage.APPLYING));
    }

    @Test
    void staticValidationMustCompleteBeforeInstallation() {
        ConversionWorkflow converting = at(WorkflowStage.CONVERTING);
        assertThrows(IllegalStateException.class, () -> converting.transitionTo(WorkflowStage.READY_TO_APPLY));
        assertThrows(IllegalStateException.class, () -> converting.transitionTo(WorkflowStage.APPLYING));
        assertThrows(IllegalStateException.class,
                () -> at(WorkflowStage.VALIDATING).transitionTo(WorkflowStage.APPLYING));
    }

    @Test
    void unsupportedAnalysisConversionProofAndValidationEndAsBlocked() {
        for (WorkflowStage stage : List.of(WorkflowStage.ANALYZING, WorkflowStage.CONVERTING,
                WorkflowStage.VALIDATING)) {
            ConversionWorkflow blocked = at(stage).transitionTo(WorkflowStage.BLOCKED);
            assertEquals(WorkflowStage.BLOCKED, blocked.stage());
            assertTrue(blocked.allowedTransitions().isEmpty());
        }
    }

    @Test
    void allPreInstallStagesCanCancelOrFailWithoutEnteringInstallation() {
        for (WorkflowStage stage : HAPPY_PATH.subList(0, 6)) {
            assertEquals(WorkflowStage.CANCELLED, at(stage).transitionTo(WorkflowStage.CANCELLED).stage());
            assertEquals(WorkflowStage.FAILED, at(stage).transitionTo(WorkflowStage.FAILED).stage());
        }
    }

    @Test
    void installationFailureRequiresRecoveryInsteadOfClaimingCancellationOrRollback() {
        ConversionWorkflow applying = at(WorkflowStage.APPLYING);
        assertThrows(IllegalStateException.class, () -> applying.transitionTo(WorkflowStage.CANCELLED));
        assertThrows(IllegalStateException.class, () -> applying.transitionTo(WorkflowStage.FAILED));
        assertThrows(IllegalStateException.class, () -> applying.transitionTo(WorkflowStage.READY_TO_APPLY));
        ConversionWorkflow recovery = applying.transitionTo(WorkflowStage.RECOVERY_REQUIRED);
        assertTrue(recovery.allowedTransitions().isEmpty());
        assertThrows(IllegalStateException.class, () -> recovery.transitionTo(WorkflowStage.PENDING_RESTART));
    }

    @Test
    void pendingRestartCannotActivateUsingAnOrdinaryTransition() {
        ConversionWorkflow pending = at(WorkflowStage.PENDING_RESTART);
        assertTrue(pending.allowedTransitions().isEmpty());
        assertThrows(IllegalStateException.class, () -> pending.transitionTo(WorkflowStage.ACTIVATED));
    }

    @Test
    void activationCannotBeObservedInTheOriginatingLaunch() {
        ConversionWorkflow pending = at(WorkflowStage.PENDING_RESTART);
        assertThrows(IllegalArgumentException.class, () -> pending.observeActivated(ORIGIN));
        assertThrows(NullPointerException.class, () -> pending.observeActivated(null));
        assertEquals(WorkflowStage.PENDING_RESTART, pending.stage());
    }

    @Test
    void observationRequiresBothPendingRestartAndAnotherLaunch() {
        for (ConversionWorkflow workflow : everyReachableStage()) {
            if (workflow.stage() != WorkflowStage.PENDING_RESTART) {
                assertThrows(IllegalStateException.class, () -> workflow.observeActivated(NEXT_LAUNCH),
                        workflow.stage().name());
            }
        }
        ConversionWorkflow pending = at(WorkflowStage.PENDING_RESTART);
        ConversionWorkflow activated = pending.observeActivated(NEXT_LAUNCH);
        assertEquals(WorkflowStage.ACTIVATED, activated.stage());
        assertEquals(ORIGIN, activated.originatingLaunchId());
        assertEquals(WorkflowStage.PENDING_RESTART, pending.stage());
    }

    @Test
    void continuingWithAnotherAttemptCannotActivateAnExistingResult() {
        ConversionWorkflow ready = at(WorkflowStage.READY_TO_APPLY);
        ConversionWorkflow second = ConversionWorkflow.discovered(ORIGIN)
                .transitionTo(WorkflowStage.ANALYZING);
        assertEquals(WorkflowStage.READY_TO_APPLY, ready.stage());
        assertEquals(WorkflowStage.ANALYZING, second.stage());
        assertFalse(ready.allowedTransitions().contains(WorkflowStage.ACTIVATED));
    }

    @Test
    void exposedTransitionSetsAreImmutableAndNullStagesAreRejected() {
        ConversionWorkflow workflow = ConversionWorkflow.discovered(ORIGIN);
        assertThrows(UnsupportedOperationException.class,
                () -> workflow.allowedTransitions().add(WorkflowStage.ACTIVATED));
        assertThrows(NullPointerException.class, () -> workflow.transitionTo(null));
    }

    private static ConversionWorkflow at(WorkflowStage desired) {
        ConversionWorkflow workflow = ConversionWorkflow.discovered(ORIGIN);
        for (WorkflowStage stage : HAPPY_PATH) {
            if (stage != WorkflowStage.DISCOVERED) {
                workflow = workflow.transitionTo(stage);
            }
            if (stage == desired) {
                return workflow;
            }
        }
        throw new IllegalArgumentException("Not on the ordinary happy path: " + desired);
    }

    private static List<ConversionWorkflow> everyReachableStage() {
        List<ConversionWorkflow> workflows = new ArrayList<>();
        HAPPY_PATH.forEach(stage -> workflows.add(at(stage)));
        workflows.add(at(WorkflowStage.PENDING_RESTART).observeActivated(NEXT_LAUNCH));
        workflows.add(at(WorkflowStage.ANALYZING).transitionTo(WorkflowStage.BLOCKED));
        workflows.add(at(WorkflowStage.ANALYZING).transitionTo(WorkflowStage.FAILED));
        workflows.add(at(WorkflowStage.ANALYZING).transitionTo(WorkflowStage.CANCELLED));
        workflows.add(at(WorkflowStage.APPLYING).transitionTo(WorkflowStage.RECOVERY_REQUIRED));
        assertEquals(WorkflowStage.values().length, workflows.size());
        return workflows;
    }
}
