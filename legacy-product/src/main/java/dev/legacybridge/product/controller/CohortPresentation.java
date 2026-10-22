package dev.legacybridge.product.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

/** Text-only projection shared with headless checks; never an installation receipt. */
public final class CohortPresentation {
    private CohortPresentation() { }
    public static List<String> paragraphs(CohortInspectionView view, BiFunction<String, Object[], String> translate) {
        var result = new ArrayList<String>();
        var t = new Text(result, translate);
        t.add("warning"); t.add("data"); t.add("limits");
        t.add("status." + view.status().name().toLowerCase(Locale.ROOT));
        t.add("selection", view.inputs().size());
        for (var input : view.inputs()) {
            t.add("input", input.artifactId(), safe(input.path().toString()), safe(input.observedIdentity()));
            result.add("SHA-256: " + input.sha256().map(CohortPresentation::safe).orElse("?"));
        }
        t.add("runtime", view.runtimeModId(), view.runtimeVersion());
        for (var source : view.sources()) {
            t.add("source", safe(source.artifactId()), safe(source.modId()), safe(source.version()), source.classes());
            t.add(source.linkageChecked() ? "linkage_checked" : "linkage_not_checked");
            for (var dependency : source.dependencies()) t.add("dependency", safe(dependency.modId()), safe(dependency.range()),
                    dependency.mandatory(), safe(dependency.side()), safe(dependency.ordering()));
        }
        for (var candidate : view.candidates()) {
            t.add("candidate", safe(candidate.artifactId()), safe(candidate.modId()), candidate.bytes());
            result.add("SHA-256: " + safe(candidate.sha256()));
        }
        for (var diagnostic : view.diagnostics()) {
            result.add(safe(diagnostic.artifactId()) + " " + safe(diagnostic.code()) + ": " + safe(diagnostic.message()));
            result.add(safe(diagnostic.remediation()));
        }
        if (view.omittedDiagnostics() > 0) t.add("omitted", view.omittedDiagnostics());
        view.error().ifPresent(e -> { result.add(safe(e.code()) + ": " + safe(e.message())); result.add(safe(e.remediation())); });
        t.add("gates");
        view.remainingGates().forEach(g -> result.add(translate.apply("legacybridge.composition.gate." + g.name().toLowerCase(Locale.ROOT), new Object[0])));
        view.report().ifPresent(p -> t.add("report", safe(p.toString())));
        t.add("report_hint");
        return List.copyOf(result);
    }
    private static String safe(String value) { return ConversionController.safeText(value); }
    private record Text(List<String> lines, BiFunction<String, Object[], String> translate) {
        void add(String key, Object... args) { lines.add(translate.apply("legacybridge.cohort." + key, args)); }
    }
}
