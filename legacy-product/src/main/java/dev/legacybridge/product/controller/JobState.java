package dev.legacybridge.product.controller;

/** Conversion/installation evidence, never a claim of correct in-game behavior. */
public enum JobState {
    QUEUED, ANALYZING, ANALYZED, BLOCKED, CONVERTING, CONVERTED,
    FAILED, CANCELLED, APPLYING, PENDING_RESTART
}
