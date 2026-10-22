package dev.legacybridge.product.controller;

import dev.legacybridge.converter.service.CompositionPreview;
import dev.legacybridge.converter.service.EngineError;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bounded presentation evidence, never a conversion job, executable plan or installation selection. */
public record CompositionInspectionView(Status status, Optional<Path> input, Optional<String> inputSha256,
                                        List<Operation> operations, Optional<Path> report, Optional<EngineError> error) {
    public static final int SCHEMA_VERSION = 1;
    public enum Status { NOT_REQUESTED, RUNNING, PREVIEW_VALIDATED, BLOCKED, FAILED, CANCELLED }
    public enum Gate { STATIC_PROOF_REQUIRED, PUBLIC_PROFILE_NOT_PROMOTED, INSTALLER_ADMISSION_NOT_IMPLEMENTED,
                       NATIVE_COMPARISON_PENDING, FINAL_PACKAGE_QUALIFICATION_PENDING }
    public record Operation(String id, String kind, String implementation, List<String> requires, int claimedEntries) {
        public Operation { requires = List.copyOf(requires); }
    }
    public CompositionInspectionView {
        Objects.requireNonNull(status); Objects.requireNonNull(input); Objects.requireNonNull(inputSha256);
        Objects.requireNonNull(report); Objects.requireNonNull(error);
        operations = List.copyOf(operations);
        if (operations.size() > 8) throw new IllegalArgumentException("Excessive operation summary");
    }
    public static CompositionInspectionView notRequested() {
        return new CompositionInspectionView(Status.NOT_REQUESTED, Optional.empty(), Optional.empty(), List.of(), Optional.empty(), Optional.empty());
    }
    static CompositionInspectionView from(Path input, CompositionPreview preview, Path report, Optional<EngineError> error) {
        List<Operation> operations = preview.operations().map(plan -> plan.operations().stream().map(operation ->
                new Operation(operation.id(), operation.kind().name(), operation.implementation(),
                        List.copyOf(operation.requires()), operation.claims().size())).toList()).orElse(List.of());
        return new CompositionInspectionView(preview.staticallyAdmitted() ? Status.PREVIEW_VALIDATED : Status.BLOCKED,
                Optional.of(input), Optional.of(preview.inputSha256()), operations, Optional.of(report), error);
    }
    /** Fixed product policy: even a forged Java value or successful static proof cannot promote a profile. */
    public boolean publicConversionAllowed() { return false; }
    public boolean installationAllowed() { return false; }
    public String runtimeModId() { return "legacybridge_runtime"; }
    public String runtimeVersion() { return AdapterCatalog.RUNTIME_VERSION; }
    public List<Gate> remainingGates() {
        return status == Status.PREVIEW_VALIDATED
                ? List.of(Gate.PUBLIC_PROFILE_NOT_PROMOTED, Gate.INSTALLER_ADMISSION_NOT_IMPLEMENTED,
                    Gate.NATIVE_COMPARISON_PENDING, Gate.FINAL_PACKAGE_QUALIFICATION_PENDING)
                : List.of(Gate.STATIC_PROOF_REQUIRED, Gate.PUBLIC_PROFILE_NOT_PROMOTED, Gate.INSTALLER_ADMISSION_NOT_IMPLEMENTED,
                    Gate.NATIVE_COMPARISON_PENDING, Gate.FINAL_PACKAGE_QUALIFICATION_PENDING);
    }
}
