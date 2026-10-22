package dev.legacybridge.workflow;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable, side-effect-free contract for one conversion and installation attempt.
 *
 * <p>This model does not inspect files, execute mod code, convert bytecode, install files,
 * restart the game, or register mods. Entering an action stage marks work started;
 * advancing from it to a successful outcome requires the corresponding service to
 * have actually completed its work. Analysis alone does not prove that the conversion
 * is supported: conversion-time proof or validation can still block the attempt. In particular,
 * {@link WorkflowStage#READY_TO_APPLY} means static conversion and validation completed;
 * it does not mean that gameplay has been tested or that Forge loaded the result.</p>
 *
 * <p>Cancellation is available only before installation begins. An interrupted or failed
 * installation requires transaction recovery, not a claim that cancellation rolled it
 * back. Recovery is deliberately not modeled as an automatic transition: a future
 * installer must first verify its journal and exact artifacts.</p>
 *
 * <p>The originating launch ID must be unique for a real game process launch. Generating
 * another UUID inside the same process is not evidence of a restart. The caller owns
 * that lifecycle invariant and the independently verified activation observation.</p>
 */
public final class ConversionWorkflow {
    private final UUID originatingLaunchId;
    private final WorkflowStage stage;

    private ConversionWorkflow(UUID originatingLaunchId, WorkflowStage stage) {
        this.originatingLaunchId = Objects.requireNonNull(originatingLaunchId, "originatingLaunchId");
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    public static ConversionWorkflow discovered(UUID originatingLaunchId) {
        return new ConversionWorkflow(originatingLaunchId, WorkflowStage.DISCOVERED);
    }

    public UUID originatingLaunchId() {
        return originatingLaunchId;
    }

    public WorkflowStage stage() {
        return stage;
    }

    /**
     * Ordinary state transitions; activation is intentionally absent from this set.
     * Returned sets are immutable. Terminal outcomes require a new attempt, not a
     * silent retry of an installation or of a failed conversion.
     */
    public Set<WorkflowStage> allowedTransitions() {
        return switch (stage) {
            case DISCOVERED -> Set.of(WorkflowStage.ANALYZING, WorkflowStage.FAILED,
                    WorkflowStage.CANCELLED);
            case ANALYZING -> Set.of(WorkflowStage.ANALYZED, WorkflowStage.BLOCKED,
                    WorkflowStage.FAILED, WorkflowStage.CANCELLED);
            case ANALYZED -> Set.of(WorkflowStage.CONVERTING, WorkflowStage.FAILED,
                    WorkflowStage.CANCELLED);
            case CONVERTING -> Set.of(WorkflowStage.VALIDATING, WorkflowStage.BLOCKED,
                    WorkflowStage.FAILED, WorkflowStage.CANCELLED);
            case VALIDATING -> Set.of(WorkflowStage.READY_TO_APPLY, WorkflowStage.BLOCKED,
                    WorkflowStage.FAILED, WorkflowStage.CANCELLED);
            case READY_TO_APPLY -> Set.of(WorkflowStage.APPLYING, WorkflowStage.FAILED,
                    WorkflowStage.CANCELLED);
            case APPLYING -> Set.of(WorkflowStage.PENDING_RESTART, WorkflowStage.RECOVERY_REQUIRED);
            case PENDING_RESTART, ACTIVATED, BLOCKED, FAILED, CANCELLED, RECOVERY_REQUIRED -> Set.of();
        };
    }

    public ConversionWorkflow transitionTo(WorkflowStage nextStage) {
        Objects.requireNonNull(nextStage, "nextStage");
        if (!allowedTransitions().contains(nextStage)) {
            throw new IllegalStateException("Unsupported workflow transition: " + stage + " -> " + nextStage);
        }
        return new ConversionWorkflow(originatingLaunchId, nextStage);
    }

    /**
     * Records a trusted caller's activation observation on a later real launch.
     *
     * <p>The caller MUST independently confirm that Forge has discovered and activated
     * the exact expected installed artifact, including its identity, version and hash,
     * and that this ID belongs to a genuinely different launch. A different UUID alone
     * does not prove any of those facts. This method does not implement that verification
     * or demonstrate correct mod behavior; it only guards the workflow sequencing.</p>
     */
    public ConversionWorkflow observeActivated(UUID observedLaunchId) {
        Objects.requireNonNull(observedLaunchId, "observedLaunchId");
        if (stage != WorkflowStage.PENDING_RESTART) {
            throw new IllegalStateException("Activation observation requires PENDING_RESTART, not " + stage);
        }
        if (originatingLaunchId.equals(observedLaunchId)) {
            throw new IllegalArgumentException("A converted mod cannot be activated in its originating launch");
        }
        return new ConversionWorkflow(originatingLaunchId, WorkflowStage.ACTIVATED);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ConversionWorkflow workflow
                && originatingLaunchId.equals(workflow.originatingLaunchId) && stage == workflow.stage;
    }

    @Override
    public int hashCode() {
        return Objects.hash(originatingLaunchId, stage);
    }

    @Override
    public String toString() {
        return "ConversionWorkflow[originatingLaunchId=" + originatingLaunchId + ", stage=" + stage + "]";
    }
}
