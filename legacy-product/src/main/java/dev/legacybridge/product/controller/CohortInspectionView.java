package dev.legacybridge.product.controller;

import dev.legacybridge.converter.conversion.CohortTargetPlanner;
import dev.legacybridge.converter.service.*;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import java.nio.file.Path;
import java.util.*;

/** Bounded display-only evidence. Never a JobRecord, staged artifact or installation selection. */
public record CohortInspectionView(Status status, List<Input> inputs, List<Source> sources, List<Candidate> candidates,
                                   List<Diagnostic> diagnostics, int omittedDiagnostics,
                                   Optional<Path> report, Optional<EngineError> error) {
    public static final int SCHEMA_VERSION = 1, MAX_INPUTS = 4, MAX_DIAGNOSTICS = 32;
    public enum Status { NOT_REQUESTED, REVIEW, RUNNING, STATIC_PAIR_VALIDATED, BLOCKED, FAILED, CANCELLED }
    public record Input(String artifactId, Path path, Optional<String> sha256, String observedIdentity) { }
    public record Dependency(String modId, boolean mandatory, String range, String side, String ordering) { }
    public record Source(String artifactId, String modId, String version, int classes, boolean linkageChecked, List<Dependency> dependencies) {
        public Source { dependencies = List.copyOf(dependencies); if (dependencies.size() > 64) throw new IllegalArgumentException("Dependency summary limit"); }
    }
    public record Candidate(String artifactId, String modId, String sha256, long bytes) { }
    public record Diagnostic(String artifactId, String code, String message, String remediation) { }
    public CohortInspectionView {
        Objects.requireNonNull(status); Objects.requireNonNull(report); Objects.requireNonNull(error);
        inputs = List.copyOf(inputs); sources = List.copyOf(sources); candidates = List.copyOf(candidates); diagnostics = List.copyOf(diagnostics);
        if (inputs.size() > MAX_INPUTS || sources.size() > MAX_INPUTS || candidates.size() > 2 || diagnostics.size() > MAX_DIAGNOSTICS || omittedDiagnostics < 0)
            throw new IllegalArgumentException("Cohort display bounds exceeded");
        if (status != Status.STATIC_PAIR_VALIDATED && !candidates.isEmpty()) throw new IllegalArgumentException("No partial target approval");
    }
    public static CohortInspectionView notRequested() {
        return new CohortInspectionView(Status.NOT_REQUESTED, List.of(), List.of(), List.of(), List.of(), 0, Optional.empty(), Optional.empty());
    }
    /** Pure display of a frozen explicit selection; controller revalidates membership before starting. */
    public static CohortInspectionView review(List<InventoryResult.Entry> selected) {
        if (selected.isEmpty() || selected.size() > MAX_INPUTS) throw new IllegalArgumentException("Select 1..4 explicit inputs");
        var inputs = new ArrayList<Input>();
        for (var entry : selected) inputs.add(new Input("input-" + (inputs.size() + 1), entry.path(), entry.fingerprint().map(f -> f.sha256()),
                safe(entry.mods().stream().limit(4).map(m -> m.modId() + " " + m.version()).reduce((a, b) -> a + ", " + b).orElse("?"))));
        return new CohortInspectionView(Status.REVIEW, inputs, List.of(), List.of(), List.of(), 0, Optional.empty(), Optional.empty());
    }
    CohortInspectionView state(Status next, Optional<EngineError> error) {
        return new CohortInspectionView(next, inputs, List.of(), List.of(), List.of(), 0, Optional.empty(), error);
    }
    static CohortInspectionView from(CohortInspectionView selected, CohortTargetPlanner.Result result, Path report, Optional<EngineError> error) {
        var sources = result.source().artifacts().stream().map(a -> new Source(a.id(), safe(a.mod().modId()), safe(a.mod().version()), a.classes().size(),
                result.source().linkage().containsKey(a.id()), a.mod().dependencies().stream().map(d ->
                new Dependency(safe(d.modId()), d.mandatory(), safe(d.versionRange()), d.side().name(), d.ordering().name())).toList())).toList();
        var candidates = result.candidates().stream().map(a -> new Candidate(a.artifactId(), safe(a.modId()), a.sha256(), a.bytes())).toList();
        var diagnostics = new ArrayList<Diagnostic>();
        result.source().diagnostics().forEach(d -> diagnostics.add(diagnostic(d.artifactId(), d.error())));
        result.source().graph().ifPresent(g -> g.diagnostics().forEach(d -> diagnostics.add(new Diagnostic(safe(d.artifactId()), safe(d.code()), safe(d.message()),
                safe(d.ownerModId() + " -> " + d.dependencyModId() + " requires " + d.requestedRange() + "; found " + d.actualVersion())))));
        result.diagnostics().forEach(d -> diagnostics.add(diagnostic("", d)));
        result.targetGraph().ifPresent(g -> g.diagnostics().forEach(d -> diagnostics.add(new Diagnostic(safe(d.artifactId()), safe(d.code()), safe(d.message()),
                safe(d.ownerModId() + " -> " + d.dependencyModId() + " requires " + d.requestedRange() + "; found " + d.actualVersion())))));
        return new CohortInspectionView(result.validated() ? Status.STATIC_PAIR_VALIDATED : Status.BLOCKED, selected.inputs(), sources, candidates,
                diagnostics.stream().limit(MAX_DIAGNOSTICS).toList(), Math.max(0, diagnostics.size() - MAX_DIAGNOSTICS), Optional.of(report), error);
    }
    private static Diagnostic diagnostic(String artifactId, EngineError error) { return new Diagnostic(safe(artifactId), safe(error.code()), safe(error.message()), safe(error.remediation())); }
    private static String safe(String value) { return ConversionController.safeText(value); }
    public boolean publicConversionAllowed() { return false; }
    public boolean installationAllowed() { return false; }
    public String runtimeModId() { return "legacybridge_runtime"; }
    public String runtimeVersion() { return AdapterCatalog.RUNTIME_VERSION; }
    public List<CompositionInspectionView.Gate> remainingGates() {
        return Arrays.stream(CompositionInspectionView.Gate.values()).filter(g -> status != Status.STATIC_PAIR_VALIDATED
                || g != CompositionInspectionView.Gate.STATIC_PROOF_REQUIRED).toList();
    }
}
