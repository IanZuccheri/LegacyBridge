package dev.legacybridge.converter.mapping;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MappingModelTest {
    @Test
    void validatesJvmSymbolShapesWithoutLoadingClasses() {
        assertEquals(SymbolKind.CONSTRUCTOR, LegacySymbol.constructor("example/Old", "(I)V").kind());
        assertEquals("[Lexternal/Type;", TargetSymbol.field("example/New", "items", "[Lexternal/Type;").descriptor());
        assertEquals("([[I[Ljava/lang/String;)Lexternal/Type;",
                LegacySymbol.descriptor("([[I[Ljava/lang/String;)Lexternal/Type;").descriptor());
        assertThrows(IllegalArgumentException.class, () -> LegacySymbol.classSymbol("example.Old"));
        assertThrows(IllegalArgumentException.class, () -> LegacySymbol.classSymbol("example//Old"));
        assertThrows(IllegalArgumentException.class, () -> new LegacySymbol(SymbolKind.CLASS, "example/Old", "extra", ""));
        assertThrows(IllegalArgumentException.class, () -> LegacySymbol.method("example/Old", "<init>", "()V"));
        assertThrows(IllegalArgumentException.class, () -> LegacySymbol.constructor("example/Old", "()I"));
        assertThrows(IllegalArgumentException.class, () -> LegacySymbol.field("example/Old", "field", "V"));
    }

    @Test
    void rejectsMalformedDescriptorsThatAnAsmTypeFactoryMayAccept() {
        for (String malformed : List.of("", "V", "Q", "Iextra", "L;", "Lfoo", "Lfoo.bar;",
                "[V", "()", "(V)V", "(I", "(I)Vtrailing", "((I))V", "(Lfoo;)VV",
                "[".repeat(256) + "I", "(" + "J".repeat(128) + ")V")) {
            assertThrows(IllegalArgumentException.class, () -> LegacySymbol.descriptor(malformed), malformed);
            assertThrows(IllegalArgumentException.class, () -> TargetSymbol.descriptor(malformed), malformed);
        }
        assertDoesNotThrow(() -> LegacySymbol.descriptor("[".repeat(255) + "I"));
        assertDoesNotThrow(() -> LegacySymbol.descriptor("(" + "I".repeat(255) + ")V"));
    }

    @Test
    void rejectsIncoherentRuleClaims() {
        LegacySymbol source = LegacySymbol.classSymbol("example/Old");
        assertThrows(IllegalArgumentException.class, () -> new MappingRule(source, null,
                MappingCategory.IDENTITY, MappingConfidence.VERIFIED, MappingStatus.RESOLVED, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MappingRule(source, TargetSymbol.classSymbol("example/New"),
                MappingCategory.IDENTITY, MappingConfidence.HIGH, MappingStatus.RESOLVED, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MappingRule(source, TargetSymbol.classSymbol("example/New"),
                MappingCategory.PURE_RENAME, MappingConfidence.NONE, MappingStatus.RESOLVED, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MappingRule(source, TargetSymbol.classSymbol("example/New"),
                MappingCategory.REMOVED_API, MappingConfidence.VERIFIED, MappingStatus.UNSUPPORTED, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MappingRule(source, TargetSymbol.field("example/New", "x", "I"),
                MappingCategory.PURE_RENAME, MappingConfidence.VERIFIED, MappingStatus.RESOLVED, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MappingRule(source, null,
                MappingCategory.REMOVED_API, MappingConfidence.HIGH, MappingStatus.ADAPTER_REQUIRED, List.of()));
    }

    @Test
    void rewriteGateRequiresSemanticVerificationAndSafeCategory() {
        LegacySymbol source = LegacySymbol.method("example/Old", "before", "(I)V");
        TargetSymbol target = TargetSymbol.method("example/New", "after", "(I)V");
        for (MappingConfidence confidence : MappingConfidence.values()) {
            if (confidence != MappingConfidence.NONE) {
                MappingRule rule = new MappingRule(source, target, MappingCategory.PURE_RENAME,
                        confidence, MappingStatus.RESOLVED, List.of("Test semantic evidence"));
                assertEquals(confidence == MappingConfidence.VERIFIED, rule.automaticRewriteAllowed());
            }
        }
        assertFalse(new MappingRule(source, TargetSymbol.method("example/New", "after", "(J)V"),
                MappingCategory.DESCRIPTOR_CHANGE, MappingConfidence.VERIFIED, MappingStatus.RESOLVED,
                List.of("Changed descriptor")).automaticRewriteAllowed());
        assertFalse(new MappingRule(source, TargetSymbol.method("example/New", "after", "(J)V"),
                MappingCategory.PURE_RENAME, MappingConfidence.VERIFIED, MappingStatus.RESOLVED,
                List.of("Misclassified descriptor must still fail the rewrite gate")).automaticRewriteAllowed());
        assertFalse(new MappingRule(source, target, MappingCategory.ADAPTER_REQUIRED,
                MappingConfidence.VERIFIED, MappingStatus.ADAPTER_REQUIRED, List.of("Adapter needed")).automaticRewriteAllowed());
        assertFalse(new MappingRule(source, target, MappingCategory.PURE_RENAME,
                MappingConfidence.HEURISTIC, MappingStatus.MANUAL, List.of("Candidate only")).automaticRewriteAllowed());
        assertFalse(new MappingRule(source, TargetSymbol.method("example/New", "after", "(J)V"),
                MappingCategory.DESCRIPTOR_CHANGE, MappingConfidence.HIGH, MappingStatus.ADAPTER_REQUIRED,
                List.of("Descriptor change needs an adapter")).automaticRewriteAllowed());
    }

    @Test
    void evidenceIsImmutableSortedAndBounded() {
        var mutable = new ArrayList<>(List.of("z", "a", "z"));
        var rule = new MappingRule(LegacySymbol.classSymbol("old/A"), TargetSymbol.classSymbol("new/A"),
                MappingCategory.PACKAGE_MOVE, MappingConfidence.HIGH, MappingStatus.RESOLVED, mutable);
        mutable.clear();
        assertEquals(List.of("a", "z"), rule.evidence());
        assertThrows(UnsupportedOperationException.class, () -> rule.evidence().add("x"));
        var many = IntStream.range(0, 1000).mapToObj(i -> "evidence " + i + "x".repeat(3000)).toList();
        var bounded = new MappingRule(rule.legacy(), rule.target(), rule.category(), rule.confidence(), rule.status(), many);
        assertEquals(MappingRule.MAX_EVIDENCE, bounded.evidence().size());
        assertTrue(bounded.evidence().contains("[additional evidence omitted]"));
        assertTrue(bounded.evidence().stream().allMatch(s -> s.length() <= MappingRule.MAX_EVIDENCE_LENGTH));
    }
}
