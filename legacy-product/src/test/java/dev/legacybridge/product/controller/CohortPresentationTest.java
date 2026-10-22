package dev.legacybridge.product.controller;

import dev.legacybridge.converter.config.BoundedJsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CohortPresentationTest {
    @Test void evenSuccessfulDisplayCannotAuthorizeAnythingAndCannotHoldPartialTargets() {
        var view = view(CohortInspectionView.Status.STATIC_PAIR_VALIDATED, List.of(), List.of());
        assertFalse(view.publicConversionAllowed()); assertFalse(view.installationAllowed());
        assertFalse(view.remainingGates().contains(CompositionInspectionView.Gate.STATIC_PROOF_REQUIRED));
        assertEquals(4, view.remainingGates().size());
        assertThrows(UnsupportedOperationException.class, () -> view.remainingGates().clear());
        assertThrows(IllegalArgumentException.class, () -> new CohortInspectionView(CohortInspectionView.Status.BLOCKED, List.of(), List.of(),
                List.of(new CohortInspectionView.Candidate("1", "mod", "aa", 1)), List.of(), 0, Optional.empty(), Optional.empty()));
    }
    @Test void displayCollectionsAreBoundedAndImmutable() {
        var input = new CohortInspectionView.Input("one", Path.of("source.jar"), Optional.empty(), "unknown");
        var mutable = new ArrayList<>(List.of(input)); var view = view(CohortInspectionView.Status.REVIEW, mutable, List.of()); mutable.clear();
        assertEquals(1, view.inputs().size()); assertThrows(UnsupportedOperationException.class, () -> view.inputs().clear());
        assertThrows(IllegalArgumentException.class, () -> view(CohortInspectionView.Status.REVIEW, Collections.nCopies(5, input), List.of()));
        var diagnostic = new CohortInspectionView.Diagnostic("1", "CODE", "cause", "fix");
        assertThrows(IllegalArgumentException.class, () -> view(CohortInspectionView.Status.BLOCKED, List.of(), Collections.nCopies(33, diagnostic)));
    }
    @Test void allStatesAndDynamicKeysHaveBothLocalesAndSanitizeUntrustedDisplay() throws Exception {
        for (String locale : List.of("en_us", "pt_br")) {
            Map<?, ?> translations;
            try (var stream = getClass().getResourceAsStream("/assets/legacybridge_runtime/lang/" + locale + ".json")) {
                assertNotNull(stream); translations = (Map<?, ?>) BoundedJsonParser.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
            for (var status : CohortInspectionView.Status.values()) {
                var view = new CohortInspectionView(status,
                        List.of(new CohortInspectionView.Input("input-1", Path.of("source.jar"), Optional.empty(), "a\nb\u00a7c\u202ed")),
                        List.of(new CohortInspectionView.Source("input-1", "mod", "1", 0, true, List.of(new CohortInspectionView.Dependency("lib", true, "[1]", "BOTH", "AFTER"))),
                                new CohortInspectionView.Source("input-2", "other", "2", 0, false, List.of())),
                        status == CohortInspectionView.Status.STATIC_PAIR_VALIDATED ? List.of(new CohortInspectionView.Candidate("1", "mod", "aa".repeat(32), 12)) : List.of(),
                        List.of(new CohortInspectionView.Diagnostic("input-1", "CODE", "x".repeat(800), "fix")), 3, Optional.of(Path.of("report.json")), Optional.empty());
                var paragraphs = CohortPresentation.paragraphs(view, (key, args) -> {
                    assertTrue(translations.containsKey(key), locale + ": missing " + key);
                    return String.format(Locale.ROOT, translations.get(key).toString(), args);
                });
                assertTrue(paragraphs.stream().anyMatch(p -> p.contains("a b c d")));
                assertTrue(paragraphs.stream().anyMatch(p -> p.equals("SHA-256: ?")));
                assertFalse(paragraphs.stream().anyMatch(p -> p.contains("x".repeat(513))));
                assertThrows(UnsupportedOperationException.class, () -> paragraphs.clear());
            }
        }
    }
    private static CohortInspectionView view(CohortInspectionView.Status status, List<CohortInspectionView.Input> inputs, List<CohortInspectionView.Diagnostic> diagnostics) {
        return new CohortInspectionView(status, inputs, List.of(), List.of(), diagnostics, 0, Optional.empty(), Optional.empty());
    }
}
