package dev.legacybridge.converter.service;

import java.util.Objects;

/** Explicit private target planning. No output path, installation or publication authority. */
public record CohortPlanningRequest(CohortInspectionRequest source) {
    public CohortPlanningRequest { Objects.requireNonNull(source); }
}
