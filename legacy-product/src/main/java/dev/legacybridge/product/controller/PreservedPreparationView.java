package dev.legacybridge.product.controller;

import dev.legacybridge.converter.operation.OperationProgress;
import dev.legacybridge.converter.service.EngineError;
import dev.legacybridge.product.install.PreservedAdmissionResult;
import java.nio.file.Path;
import java.util.*;

/** One bounded operation observation. Admission readiness is neither installation consent nor runtime proof. */
public record PreservedPreparationView(State state, Optional<UUID> operationId,
        Optional<PreservedPreparationRequest.Candidate> candidate, Optional<PreservedAdmissionResult> admission,
        Map<String, Path> reports, Optional<OperationProgress> progress, Optional<EngineError> error) {
    public enum State { DISABLED, IDLE, ANALYZING, CONVERTING, INSPECTING, ADMITTING, WRITING_REPORTS, READY, BLOCKED, FAILED, CANCELLED }
    public PreservedPreparationView {
        Objects.requireNonNull(state); Objects.requireNonNull(operationId); Objects.requireNonNull(candidate);
        Objects.requireNonNull(admission); Objects.requireNonNull(progress); Objects.requireNonNull(error);
        reports = Map.copyOf(reports);
        if (reports.size() > 5) throw new IllegalArgumentException("Bounded report set required");
        if (state == State.READY && (operationId.isEmpty() || candidate.isEmpty() || error.isPresent()
                || !reports.containsKey("preparation") || admission.filter(a -> a.state() == PreservedAdmissionResult.State.RECORDED_NOT_INSTALLABLE && a.error().isEmpty()).isEmpty()))
            throw new IllegalArgumentException("Readiness requires complete admission and saved reports, never consent");
    }
    public static PreservedPreparationView initial(boolean enabled) {
        return new PreservedPreparationView(enabled ? State.IDLE : State.DISABLED, Optional.empty(), Optional.empty(), Optional.empty(), Map.of(), Optional.empty(), Optional.empty());
    }
    public boolean installationAllowed() { return false; }
    public boolean safeToClose() { return false; }
    public String activation() { return "NOT_OBSERVED"; }
}
