package dev.legacybridge.workflow;

/**
 * Stages of one conversion/install attempt, not the state of the running Forge mod list.
 * A converted and validated file is not an activated mod.
 */
public enum WorkflowStage {
    DISCOVERED,
    ANALYZING,
    ANALYZED,
    CONVERTING,
    VALIDATING,
    READY_TO_APPLY,
    APPLYING,
    PENDING_RESTART,
    ACTIVATED,
    BLOCKED,
    FAILED,
    CANCELLED,
    RECOVERY_REQUIRED
}
