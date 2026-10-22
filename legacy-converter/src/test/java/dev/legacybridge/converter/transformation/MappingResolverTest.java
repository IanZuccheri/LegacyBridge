package dev.legacybridge.converter.transformation;

import dev.legacybridge.converter.mapping.*;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MappingResolverTest {
    @Test void unlistedReferencesPassThroughButRequestedMissingMappingsFail() {
        MappingResolver resolver = resolver();
        assertEquals("java/lang/String", resolver.mapClassName("java/lang/String"));
        assertTrue(resolver.findRule(LegacySymbol.classSymbol("other/Type")).isEmpty());
        assertThrows(TransformationException.class, () -> resolver.requireResolved(LegacySymbol.classSymbol("other/Type")));
    }

    @Test void highConfidenceCandidatesAreNeverAutomaticallyUsed() {
        MappingRule rule = new MappingRule(LegacySymbol.classSymbol("mod/Old"), TargetSymbol.classSymbol("mod/New"),
                MappingCategory.PURE_RENAME, MappingConfidence.HIGH, MappingStatus.RESOLVED, List.of("Source mapping candidate only"));
        MappingResolver resolver = resolver(rule);
        assertThrows(TransformationException.class, () -> resolver.mapClassName("mod/Old"));
    }

    @Test void unresolvedRulesFailEvenIfAdapterIdExists() {
        MappingRule rule = new MappingRule(LegacySymbol.classSymbol("mod/Old"), null,
                MappingCategory.NO_AUTOMATIC_EQUIVALENT, MappingConfidence.NONE, MappingStatus.UNSUPPORTED, List.of("No semantic equivalent"));
        assertThrows(TransformationException.class, () -> resolver(rule).mapClassName("mod/Old"));
    }

    @Test void unknownOrMissingAdaptersCannotAuthorizeMutation() {
        MappingRule rule = classRule("mod/Old", "mod/New");
        assertThrows(TransformationException.class, () -> new MappingResolver(MappingDatabase.builder().add(rule).build(),
                Map.of(rule.legacy(), "registry.nonexistent.v1"), Set.of("mod/Old")));
        MappingResolver resolver = new MappingResolver(MappingDatabase.builder().add(rule).build(), Map.of(), Set.of("mod/Old"));
        assertThrows(TransformationException.class, () -> resolver.mapClassName("mod/Old"));
    }

    @Test void descriptorsOnlySubstituteVerifiedClassComponentsAndRemainIdempotent() {
        DescriptorTransformer transformer = new DescriptorTransformer(resolver(classRule("mod/Old", "mod/New")));
        var result = transformer.remap("([Lmod/Old;ILjava/lang/String;)Lmod/Old;");
        assertEquals("([Lmod/New;ILjava/lang/String;)Lmod/New;", result.after());
        assertEquals(1, result.rules().size());
        assertEquals(result.after(), transformer.remap(result.after()).after());
    }

    @Test void explicitDescriptorCannotAlterArityPrimitivesOrArrayRank() {
        for (String target : List.of("(J)V", "(II)V", "([I)V", "(I)I")) {
            MappingRule rule = verified(LegacySymbol.descriptor("(I)V"), TargetSymbol.descriptor(target), MappingCategory.DESCRIPTOR_CHANGE);
            assertThrows(TransformationException.class, () -> new DescriptorTransformer(resolver(rule)).remap("(I)V"));
        }
    }

    @Test void aDescriptorMappingCannotInventAnUnmappedClassComponent() {
        MappingRule rule = verified(LegacySymbol.descriptor("Lmod/Old;"), TargetSymbol.descriptor("Lmod/New;"), MappingCategory.DESCRIPTOR_CHANGE);
        assertThrows(TransformationException.class, () -> new DescriptorTransformer(resolver(rule)).remap("Lmod/Old;"));
    }

    @Test void explicitDescriptorAndComponentRulesMustAgree() {
        MappingRule descriptor = verified(LegacySymbol.descriptor("Lmod/Old;"), TargetSymbol.descriptor("Lmod/New;"), MappingCategory.DESCRIPTOR_CHANGE);
        assertEquals("Lmod/New;", new DescriptorTransformer(resolver(classRule("mod/Old", "mod/New"), descriptor)).remap("Lmod/Old;").after());
    }

    @Test void independentMemberOwnerRelocationIsNotANameRemap() {
        MappingRule rule = verified(LegacySymbol.method("mod/Old", "value", "()I"),
                TargetSymbol.method("unrelated/Factory", "value", "()I"), MappingCategory.PACKAGE_MOVE);
        assertThrows(TransformationException.class, () -> resolver(rule).memberRule("mod/Old", "value", "()I", true));
    }

    @Test void externalClassRenameDoesNotAuthorizeUnresolvedMethodFieldOrConstructorCalls() {
        MappingResolver resolver = resolver(classRule("net/minecraft/Old", "net/minecraft/New"));
        assertThrows(TransformationException.class,
                () -> resolver.memberRule("net/minecraft/Old", "value", "()I", true));
        assertThrows(TransformationException.class,
                () -> resolver.memberRule("net/minecraft/Old", "value", "I", false));
        assertThrows(TransformationException.class,
                () -> resolver.memberRule("net/minecraft/Old", "<init>", "()V", true));
    }

    @Test void changedExternalMemberDescriptorRequiresAnExactMemberRuleEvenWhenOwnerStaysPut() {
        MappingResolver resolver = resolver(classRule("mod/Old", "mod/New"));
        assertThrows(TransformationException.class,
                () -> resolver.memberRule("external/Library", "accept", "(Lmod/Old;)V", true));
        assertThrows(TransformationException.class,
                () -> resolver.memberRule("external/Library", "value", "Lmod/Old;", false));
        assertTrue(resolver.memberRule("mod/Old", "self", "()Lmod/Old;", true).isEmpty(),
                "Owned definitions and their callers are rewritten together");
    }

    @Test void exactVerifiedExternalMemberRuleCanAuthorizeTheSameStructuralRelocation() {
        MappingResolver resolver = resolver(classRule("net/minecraft/Old", "net/minecraft/New"),
                verified(LegacySymbol.method("net/minecraft/Old", "oldValue", "()I"),
                        TargetSymbol.method("net/minecraft/New", "newValue", "()I"), MappingCategory.PURE_RENAME));
        assertEquals("newValue", resolver.memberRule("net/minecraft/Old", "oldValue", "()I", true).orElseThrow().target().name());
    }

    @Test void memberDescriptorMustMatchOnlyTheClassComponentSubstitution() {
        MappingRule rule = verified(LegacySymbol.method("mod/Old", "value", "()I"),
                TargetSymbol.method("mod/Old", "value", "()J"), MappingCategory.DESCRIPTOR_CHANGE);
        assertThrows(TransformationException.class, () -> resolver(rule).memberRule("mod/Old", "value", "()I", true));
    }

    @Test void constructorMappingRetainsInitAndChecksItsStructuralDescriptor() {
        MappingRule constructor = verified(LegacySymbol.constructor("mod/Old", "(Lmod/Old;)V"),
                TargetSymbol.constructor("mod/New", "(Lmod/New;)V"), MappingCategory.DESCRIPTOR_CHANGE);
        MappingRule mapped = resolver(classRule("mod/Old", "mod/New"), constructor)
                .memberRule("mod/Old", "<init>", "(Lmod/Old;)V", true).orElseThrow();
        assertEquals("<init>", mapped.target().name());
    }

    @Test void protectedAndForeignClassesCannotBeTransformedOrBecomeModDeclarations() {
        for (String prefix : List.of("net/minecraft/", "net/minecraftforge/", "cpw/mods/", "com/mojang/", "java/", "javax/",
                "jdk/", "sun/", "dev/legacybridge/runtime/")) {
            MappingResolver resolver = new MappingResolver(MappingDatabase.builder().build(), Map.of(), Set.of(prefix + "Forbidden"));
            assertThrows(TransformationException.class, () -> resolver.requireTransformableClass(prefix + "Forbidden"));
            assertThrows(TransformationException.class, () -> resolver(classRule("mod/Old", prefix + "Forbidden")).requireTransformableClass("mod/Old"));
        }
        assertThrows(TransformationException.class, () -> resolver().requireTransformableClass("thirdparty/Other"));
    }

    @Test void chainsAndManyToOneMappingsCannotSilentlyCascadeOrCollide() {
        assertThrows(TransformationException.class, () -> resolver(classRule("mod/Old", "mod/New"), classRule("mod/New", "mod/Third")));
        assertThrows(TransformationException.class, () -> resolver(classRule("mod/Old", "mod/New"), classRule("mod/Other", "mod/New")));
        MappingRule rule = classRule("mod/Old", "mod/New");
        MappingResolver resolver = new MappingResolver(MappingDatabase.builder().add(rule).build(),
                Map.of(rule.legacy(), AdapterCatalog.BOOTSTRAP), Set.of("mod/Old", "mod/New"));
        assertThrows(TransformationException.class, () -> resolver.requireTransformableClass("mod/Old"));
    }

    @Test void malformedDescriptorsDoNotSlipThroughAsmTypeFactory() {
        DescriptorTransformer transformer = new DescriptorTransformer(resolver());
        for (String descriptor : List.of("Lmod/Old;garbage", "(I)Vgarbage", "([V)V", "()", "V")) {
            assertThrows(IllegalArgumentException.class, () -> transformer.remap(descriptor));
        }
    }

    static MappingResolver resolver(MappingRule... rules) {
        MappingDatabase.Builder builder = MappingDatabase.builder();
        var bindings = new java.util.LinkedHashMap<LegacySymbol, String>();
        for (MappingRule rule : rules) {
            builder.add(rule);
            bindings.put(rule.legacy(), AdapterCatalog.BOOTSTRAP);
        }
        return new MappingResolver(builder.build(), bindings, Set.of("mod/Old", "mod/Caller"));
    }

    static MappingRule classRule(String before, String after) {
        return verified(LegacySymbol.classSymbol(before), TargetSymbol.classSymbol(after), MappingCategory.PURE_RENAME);
    }

    static MappingRule verified(LegacySymbol legacy, TargetSymbol target, MappingCategory category) {
        return new MappingRule(legacy, target, category, MappingConfidence.VERIFIED, MappingStatus.RESOLVED,
                List.of("Synthetic fixture structural recipe, not a general API compatibility claim"));
    }
}
