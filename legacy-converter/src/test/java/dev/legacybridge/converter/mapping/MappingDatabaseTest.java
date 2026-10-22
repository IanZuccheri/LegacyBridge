package dev.legacybridge.converter.mapping;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MappingDatabaseTest {
    @Test
    void indexesAllSymbolKindsByFullSignatureAndDoesNotInventOverloads() {
        LegacySymbol method = LegacySymbol.method("old/A", "doThing", "(I)V");
        LegacySymbol field = LegacySymbol.field("old/A", "value", "I");
        LegacySymbol constructor = LegacySymbol.constructor("old/A", "(I)V");
        var database = MappingDatabase.builder().add(classRule("old/A", "new/A"))
                .add(resolved(method, TargetSymbol.method("new/A", "perform", "(I)V")))
                .add(resolved(field, TargetSymbol.field("new/A", "number", "I")))
                .add(resolved(constructor, TargetSymbol.constructor("new/A", "(I)V"))).build();
        assertEquals(4, database.size());
        assertEquals("perform", database.resolve(method).target().name());
        assertEquals("number", database.resolve(field).target().name());
        assertEquals("<init>", database.resolve(constructor).target().name());
        assertEquals(MappingStatus.MANUAL, database.resolve(LegacySymbol.method("old/A", "doThing", "(J)V")).status());
        assertEquals(MappingStatus.MANUAL, database.resolve(LegacySymbol.field("old/A", "value", "J")).status());
        assertEquals(MappingStatus.MANUAL, database.resolve(LegacySymbol.constructor("old/A", "()V")).status());
        assertFalse(database.resolve(method).automaticRewriteAllowed());
    }

    @Test
    void conflictsFailClosedRegardlessOfInsertionOrder() {
        MappingRule first = classRule("old/A", "new/A");
        MappingRule second = classRule("old/A", "other/A");
        var forward = MappingDatabase.builder().add(first).add(second).build().resolve(first.legacy());
        var reverse = MappingDatabase.builder().add(second).add(first).build().resolve(first.legacy());
        assertEquals(forward, reverse);
        assertEquals(MappingStatus.MANUAL, forward.status());
        assertEquals(MappingConfidence.NONE, forward.confidence());
        assertNull(forward.target());
        assertFalse(forward.automaticRewriteAllowed());
        assertTrue(forward.evidence().stream().anyMatch(e -> e.contains("new/A")));
        assertTrue(forward.evidence().stream().anyMatch(e -> e.contains("other/A")));
    }

    @Test
    void explicitBlockingRuleOverridesAnInferredMatch() {
        MappingRule inferred = classRule("old/A", "new/A");
        MappingRule blocked = new MappingRule(inferred.legacy(), null, MappingCategory.ADAPTER_REQUIRED,
                MappingConfidence.HIGH, MappingStatus.ADAPTER_REQUIRED, List.of("Changed lifecycle contract"));
        for (List<MappingRule> order : List.of(List.of(inferred, blocked), List.of(blocked, inferred))) {
            var builder = MappingDatabase.builder();
            order.forEach(builder::add);
            MappingRule actual = builder.build().resolve(inferred.legacy());
            assertEquals(MappingStatus.ADAPTER_REQUIRED, actual.status());
            assertNull(actual.target());
            assertTrue(actual.evidence().contains("Changed lifecycle contract"));
            assertTrue(actual.evidence().stream().anyMatch(e -> e.contains("overrides")));
        }
    }

    @Test
    void contradictoryBlockersRequireManualReview() {
        LegacySymbol symbol = LegacySymbol.classSymbol("old/A");
        var database = MappingDatabase.builder()
                .add(new MappingRule(symbol, null, MappingCategory.ADAPTER_REQUIRED, MappingConfidence.HIGH,
                        MappingStatus.ADAPTER_REQUIRED, List.of("Adapter evidence")))
                .add(new MappingRule(symbol, null, MappingCategory.REMOVED_API, MappingConfidence.HIGH,
                        MappingStatus.UNSUPPORTED, List.of("Removal evidence"))).build();
        assertEquals(MappingStatus.MANUAL, database.resolve(symbol).status());
        assertNull(database.resolve(symbol).target());
    }

    @Test
    void manualCandidateDoesNotHideConflictingTargetsOrDefinitiveUnknownRules() {
        MappingRule candidate = new MappingRule(LegacySymbol.classSymbol("old/A"), TargetSymbol.classSymbol("candidate/A"),
                MappingCategory.PACKAGE_MOVE, MappingConfidence.HEURISTIC, MappingStatus.MANUAL,
                List.of("Simple-name-only candidate"));
        MappingRule conflict = MappingDatabase.builder().add(candidate).add(classRule("old/A", "new/A"))
                .build().resolve(candidate.legacy());
        assertEquals(MappingStatus.MANUAL, conflict.status());
        assertNull(conflict.target());
        assertTrue(conflict.evidence().stream().anyMatch(e -> e.contains("Conflicting target")));
        MappingRule noEquivalent = new MappingRule(candidate.legacy(), null, MappingCategory.NO_AUTOMATIC_EQUIVALENT,
                MappingConfidence.NONE, MappingStatus.MANUAL, List.of("No semantic equivalent established"));
        MappingRule blocked = MappingDatabase.builder().add(candidate).add(noEquivalent).build().resolve(candidate.legacy());
        assertEquals(MappingStatus.MANUAL, blocked.status());
        assertNull(blocked.target());
        assertEquals(MappingCategory.NO_AUTOMATIC_EQUIVALENT, blocked.category());
    }

    @Test
    void mapsNestedArrayAndMethodDescriptorsAllOrNothing() {
        var database = MappingDatabase.builder().add(classRule("old/A", "new/A"))
                .add(classRule("old/B", "new/B")).build();
        MappingRule mapped = database.resolveDescriptor("([[Lold/A;ILjava/lang/String;)[Lold/B;");
        assertEquals(MappingStatus.RESOLVED, mapped.status());
        assertEquals("([[Lnew/A;ILjava/lang/String;)[Lnew/B;", mapped.target().descriptor());
        assertEquals(MappingCategory.DESCRIPTOR_CHANGE, mapped.category());
        assertEquals(MappingConfidence.HIGH, mapped.confidence());
        assertFalse(mapped.automaticRewriteAllowed());
        MappingRule unresolved = database.resolveDescriptor("(Lold/A;Lmissing/B;)V");
        assertEquals(MappingStatus.MANUAL, unresolved.status());
        assertNull(unresolved.target());
        assertTrue(unresolved.evidence().stream().anyMatch(e -> e.contains("missing/B")));
    }

    @Test
    void descriptorBlockersRetainTheirStatusesAndNeverPublishPartialTargets() {
        var database = MappingDatabase.builder().add(classRule("old/A", "new/A"))
                .add(new MappingRule(LegacySymbol.classSymbol("old/Adapter"), TargetSymbol.classSymbol("new/Adapter"),
                        MappingCategory.ADAPTER_REQUIRED, MappingConfidence.HIGH, MappingStatus.ADAPTER_REQUIRED,
                        List.of("Requires adapter")))
                .add(new MappingRule(LegacySymbol.classSymbol("old/Removed"), null, MappingCategory.REMOVED_API,
                        MappingConfidence.HIGH, MappingStatus.UNSUPPORTED, List.of("Removed from target"))).build();
        MappingRule adapter = database.resolveDescriptor("(Lold/A;)Lold/Adapter;");
        MappingRule unsupported = database.resolveDescriptor("(Lold/A;)Lold/Removed;");
        assertEquals(MappingStatus.ADAPTER_REQUIRED, adapter.status());
        assertEquals(MappingStatus.UNSUPPORTED, unsupported.status());
        assertNull(adapter.target());
        assertNull(unsupported.target());
        assertFalse(adapter.automaticRewriteAllowed());
    }

    @Test
    void jdkPassThroughIsRestrictedToDescriptorsAndExplicitBlockersStillApply() {
        var database = MappingDatabase.builder().build();
        assertEquals(MappingStatus.RESOLVED, database.resolveDescriptor("(Ljava/lang/String;[I)Ljavax/crypto/Cipher;").status());
        assertEquals(MappingStatus.MANUAL, database.resolve(LegacySymbol.classSymbol("java/lang/String")).status());
        assertEquals(MappingStatus.MANUAL, database.resolveDescriptor("Ljavaish/Unknown;").status());
        MappingRule primitives = database.resolveDescriptor("(I)J");
        assertEquals(MappingStatus.RESOLVED, primitives.status());
        assertEquals(MappingConfidence.HIGH, primitives.confidence());
        assertFalse(primitives.automaticRewriteAllowed());
        assertFalse(database.resolveDescriptor("(Ljava/lang/String;)V").automaticRewriteAllowed());
        MappingRule explicit = new MappingRule(LegacySymbol.descriptor("(I)J"), TargetSymbol.descriptor("(I)J"),
                MappingCategory.IDENTITY, MappingConfidence.VERIFIED, MappingStatus.RESOLVED,
                List.of("Independent semantic approval"));
        assertTrue(MappingDatabase.builder().add(explicit).build().resolveDescriptor("(I)J").automaticRewriteAllowed());
        var blocked = MappingDatabase.builder().add(new MappingRule(LegacySymbol.classSymbol("javax/removed/Type"),
                null, MappingCategory.REMOVED_API, MappingConfidence.HIGH, MappingStatus.UNSUPPORTED,
                List.of("Known removed JDK type"))).build();
        assertEquals(MappingStatus.UNSUPPORTED, blocked.resolveDescriptor("Ljavax/removed/Type;").status());
    }

    @Test
    void databaseIsAnImmutableSnapshotWithStableOrderAndConservativeMergedConfidence() {
        MappingRule high = classRule("old/A", "new/A");
        MappingRule heuristic = new MappingRule(high.legacy(), high.target(), high.category(),
                MappingConfidence.HEURISTIC, high.status(), List.of("Heuristic evidence"));
        var builder = MappingDatabase.builder().add(classRule("old/Z", "new/Z")).add(high).add(heuristic);
        var database = builder.build();
        builder.add(classRule("old/B", "new/B"));
        assertEquals(2, database.size());
        assertEquals(MappingConfidence.HEURISTIC, database.resolve(high.legacy()).confidence());
        assertThrows(UnsupportedOperationException.class, () -> database.rules().clear());
        var keys = database.rules().stream().map(MappingRule::legacy).toList();
        var sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        assertEquals(sorted, keys);
    }

    private static MappingRule classRule(String oldName, String newName) {
        return new MappingRule(LegacySymbol.classSymbol(oldName), TargetSymbol.classSymbol(newName),
                MappingCategory.PACKAGE_MOVE, MappingConfidence.HIGH, MappingStatus.RESOLVED,
                List.of("Exact development artifact signature match: " + oldName + " -> " + newName));
    }

    private static MappingRule resolved(LegacySymbol source, TargetSymbol target) {
        return new MappingRule(source, target, MappingCategory.PURE_RENAME, MappingConfidence.HIGH,
                MappingStatus.RESOLVED, List.of("Pinned artifact evidence"));
    }
}
