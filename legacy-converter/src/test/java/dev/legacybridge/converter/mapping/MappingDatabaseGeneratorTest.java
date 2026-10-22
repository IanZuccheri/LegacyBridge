package dev.legacybridge.converter.mapping;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.testutil.TestJarBuilder;
import net.minecraftforge.srgutils.IMappingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MappingDatabaseGeneratorTest {
    private static final String OLD = "net/minecraft/legacy/Token";
    private static final String CANONICAL = "net/minecraft/example/Token";
    private static final String FORGE_REGISTRY = "net/minecraftforge/registries/IForgeRegistryEntry";
    private static final String SOURCE_OFFICIAL = """
            net.minecraft.example.Token -> a:
                java.lang.String value -> n
                int removedField -> r
                int changedField -> c
                1:2:void <init>(java.lang.String) -> <init>
                3:4:java.lang.String getValue() -> a
                5:6:void removed() -> b
                7:8:void changed(int) -> c
                9:10:net.minecraft.example.Token copy(net.minecraft.example.Token) -> d
            """;
    private static final String TARGET_OFFICIAL = """
            net.minecraft.example.Token -> x:
                java.lang.String value -> n
                long changedField -> c
                1:2:void <init>(java.lang.String) -> <init>
                3:4:java.lang.String getValue() -> a
                7:8:void changed(long) -> c
                9:10:net.minecraft.example.Token copy(net.minecraft.example.Token) -> d
            """;
    private static final String SOURCE_SRG = """
            a net/minecraft/legacy/Token
            	n field_1
            	r field_2
            	c field_3
            	a ()Ljava/lang/String; func_1
            	b ()V func_2
            	c (I)V func_3
            	d (La;)La; func_4
            """;
    private static final String TARGET_SRG = """
            tsrg2 obf srg id
            x net/minecraft/src/C_100_ 100
            	n f_101_ 101
            	c f_102_ 102
            	<init> (Ljava/lang/String;)V <init> 103
            		0 o p_104_ 104
            	a ()Ljava/lang/String; m_105_ 105
            	c (J)V m_106_ 106
            	d (Lx;)Lx; m_107_ 107
            """;

    @TempDir
    Path temporary;

    @Test
    void composesPureSymbolNamesFromProguardAndBothTsrgVersionsWithoutEmittingPlaceholderOwners() throws IOException {
        MappingInputs inputs = standardInputs();
        LegacySymbol owner = LegacySymbol.classSymbol(OLD);
        LegacySymbol getter = LegacySymbol.method(OLD, "func_1", "()Ljava/lang/String;");
        LegacySymbol field = LegacySymbol.field(OLD, "field_1", "Ljava/lang/String;");
        LegacySymbol constructor = LegacySymbol.constructor(OLD, "(Ljava/lang/String;)V");
        MappingDatabase database = new MappingDatabaseGenerator(inputs).generate(List.of(owner, getter, field, constructor));

        assertResolved(database.resolve(owner), SymbolKind.CLASS, CANONICAL, "", "");
        assertResolved(database.resolve(getter), SymbolKind.METHOD, CANONICAL, "m_105_", "()Ljava/lang/String;");
        assertResolved(database.resolve(field), SymbolKind.FIELD, CANONICAL, "f_101_", "Ljava/lang/String;");
        assertResolved(database.resolve(constructor), SymbolKind.CONSTRUCTOR, CANONICAL, "<init>", "(Ljava/lang/String;)V");
        assertEquals(MappingCategory.PACKAGE_MOVE, database.resolve(owner).category());
        assertTrue(database.rules().stream().noneMatch(MappingRule::automaticRewriteAllowed));
        assertTrue(database.rules().stream().filter(rule -> rule.target() != null)
                .noneMatch(rule -> rule.target().owner().contains("/src/C_")));
    }

    @Test
    void acceptsTheHybridOfficialDevelopmentNamespaceAndFullyOfficialNames() throws IOException {
        LegacySymbol hybrid = LegacySymbol.method(OLD, "getValue", "()Ljava/lang/String;");
        LegacySymbol official = LegacySymbol.method(CANONICAL, "getValue", "()Ljava/lang/String;");
        MappingDatabase database = new MappingDatabaseGenerator(standardInputs()).generate(List.of(hybrid, official));
        assertEquals(database.resolve(hybrid).target(), database.resolve(official).target());
        assertResolved(database.resolve(official), SymbolKind.METHOD, CANONICAL, "m_105_", "()Ljava/lang/String;");
        assertEquals(MappingCategory.PURE_RENAME, database.resolve(official).category());
    }

    @Test
    void mapsArrayAndMethodDescriptorComponentsThroughClassRules() throws IOException {
        String oldDescriptor = "([[L" + OLD + ";I)L" + OLD + ";";
        LegacySymbol descriptor = LegacySymbol.descriptor(oldDescriptor);
        LegacySymbol copy = LegacySymbol.method(OLD, "func_4", "(L" + OLD + ";)L" + OLD + ";");
        MappingDatabase database = new MappingDatabaseGenerator(standardInputs()).generate(List.of(descriptor, copy));
        MappingRule descriptorRule = database.resolve(descriptor);

        assertEquals(MappingStatus.RESOLVED, descriptorRule.status());
        assertEquals("([[L" + CANONICAL + ";I)L" + CANONICAL + ";", descriptorRule.target().descriptor());
        assertEquals(MappingCategory.DESCRIPTOR_CHANGE, descriptorRule.category());
        assertFalse(descriptorRule.automaticRewriteAllowed());
        assertResolved(database.resolve(copy), SymbolKind.METHOD, CANONICAL, "m_107_",
                "(L" + CANONICAL + ";)L" + CANONICAL + ";");
    }

    @Test
    void reportsRemovedMethodsAndFieldsWithoutGuessingAnotherMember() throws IOException {
        LegacySymbol method = LegacySymbol.method(OLD, "func_2", "()V");
        LegacySymbol field = LegacySymbol.field(OLD, "field_2", "I");
        MappingDatabase database = new MappingDatabaseGenerator(standardInputs()).generate(List.of(method, field));
        for (LegacySymbol symbol : List.of(method, field)) {
            MappingRule rule = database.resolve(symbol);
            assertEquals(MappingStatus.UNSUPPORTED, rule.status());
            assertEquals(MappingCategory.REMOVED_API, rule.category());
            assertNull(rule.target());
            assertFalse(rule.automaticRewriteAllowed());
        }
    }

    @Test
    void descriptorChangesWithoutImplementedAdaptersAreUnsupportedAndDoNotSelectANearbyOverload() throws IOException {
        LegacySymbol method = LegacySymbol.method(OLD, "func_3", "(I)V");
        LegacySymbol field = LegacySymbol.field(OLD, "field_3", "I");
        MappingDatabase database = new MappingDatabaseGenerator(standardInputs()).generate(List.of(method, field));
        for (LegacySymbol symbol : List.of(method, field)) {
            MappingRule rule = database.resolve(symbol);
            assertNoImplementedAdapter(rule);
            assertTrue(rule.evidence().stream().anyMatch(line -> line.toLowerCase(java.util.Locale.ROOT).contains("descriptor")));
        }
    }

    @Test
    void registryRefusalPolicyIsUnsupportedWithoutAnAdapterEvenIfANameCouldAppearUnchanged() throws IOException {
        MappingInputs baseline = standardInputs();
        byte[] registry = apiClass(FORGE_REGISTRY, List.of(member("getRegistryName", "()L" + OLD + ";")), List.of());
        ApiIndex source = readApi("registry-source.jar", Map.of(FORGE_REGISTRY + ".class", registry));
        ApiIndex target = readApi("registry-target.jar", Map.of(FORGE_REGISTRY + ".class", registry));
        LegacySymbol registryClass = LegacySymbol.classSymbol(FORGE_REGISTRY);
        LegacySymbol registryMember = LegacySymbol.method(FORGE_REGISTRY, "getRegistryName", "()L" + OLD + ";");
        MappingDatabase database = new MappingDatabaseGenerator(withApis(baseline, source, target, true))
                .generate(List.of(registryClass, registryMember));
        assertNoImplementedAdapter(database.resolve(registryClass));
        assertNoImplementedAdapter(database.resolve(registryMember));
        assertTrue(database.rules().stream().noneMatch(MappingRule::automaticRewriteAllowed));
    }

    @Test
    void everySemanticRefusalPolicyRemainsUnsupportedDespiteTheDeclarativeContentAdapter() throws IOException {
        MappingInputs baseline = standardInputs();
        List<String> owners = List.of(FORGE_REGISTRY,
                "net/minecraftforge/event/RegistryEvent$Register",
                "net/minecraftforge/common/capabilities/CapabilityInject",
                "net/minecraftforge/fml/client/registry/RenderingRegistry",
                "net/minecraftforge/common/loot/GlobalLootModifierSerializer");
        Map<String, byte[]> declarations = new java.util.TreeMap<>();
        for (String owner : owners) declarations.put(owner + ".class", apiClass(owner, List.of(), List.of()));
        ApiIndex source = readApi("policy-source.jar", declarations);
        ApiIndex target = readApi("policy-target.jar", declarations);
        var symbols = owners.stream().map(LegacySymbol::classSymbol).toList();
        MappingDatabase database = new MappingDatabaseGenerator(withApis(baseline, source, target, true)).generate(symbols);
        for (LegacySymbol symbol : symbols) assertNoImplementedAdapter(database.resolve(symbol));
        assertTrue(database.rules().stream().noneMatch(rule -> rule.status() == MappingStatus.ADAPTER_REQUIRED));
    }

    @Test
    void changedForgeMethodsFieldsAndConstructorsAreUnsupportedWithoutEquivalentAdapters() throws IOException {
        MappingInputs baseline = standardInputs();
        String owner = "net/minecraftforge/example/EvolvingApi";
        ApiIndex source = readApi("evolving-source.jar", Map.of(owner + ".class", apiClass(owner,
                List.of(member("<init>", "(I)V"), member("accept", "(I)V")), List.of(member("value", "I")))));
        ApiIndex target = readApi("evolving-target.jar", Map.of(owner + ".class", apiClass(owner,
                List.of(member("<init>", "(J)V"), member("accept", "(J)V")), List.of(member("value", "J")))));
        List<LegacySymbol> symbols = List.of(LegacySymbol.constructor(owner, "(I)V"),
                LegacySymbol.method(owner, "accept", "(I)V"), LegacySymbol.field(owner, "value", "I"));
        MappingDatabase database = new MappingDatabaseGenerator(withApis(baseline, source, target, true)).generate(symbols);
        for (LegacySymbol symbol : symbols) assertNoImplementedAdapter(database.resolve(symbol));
        MappingRule ownerRule = database.resolve(LegacySymbol.classSymbol(owner));
        assertEquals(MappingStatus.RESOLVED, ownerRule.status());
        assertEquals(MappingConfidence.HIGH, ownerRule.confidence());
        assertFalse(ownerRule.automaticRewriteAllowed());
    }

    private static void assertNoImplementedAdapter(MappingRule rule) {
        assertEquals(MappingStatus.UNSUPPORTED, rule.status());
        assertEquals(MappingCategory.NO_AUTOMATIC_EQUIVALENT, rule.category());
        assertEquals(MappingConfidence.HIGH, rule.confidence());
        assertNull(rule.target());
        assertFalse(rule.automaticRewriteAllowed());
        assertTrue(rule.evidence().contains("No implemented semantically equivalent direct-call adapter; declarative fixture adapter cannot satisfy arbitrary call."));
    }

    @Test
    void sameSimpleNameInADifferentForgePackageIsOnlyManualEvidence() throws IOException {
        MappingInputs baseline = standardInputs();
        String oldOwner = "net/minecraftforge/old/Channel";
        String candidate = "net/minecraftforge/newapi/Channel";
        ApiIndex source = readApi("move-source.jar", Map.of(oldOwner + ".class", apiClass(oldOwner, List.of(), List.of())));
        ApiIndex target = readApi("move-target.jar", Map.of(candidate + ".class", apiClass(candidate, List.of(), List.of())));
        LegacySymbol symbol = LegacySymbol.classSymbol(oldOwner);
        MappingRule rule = new MappingDatabaseGenerator(withApis(baseline, source, target, true))
                .generate(List.of(symbol)).resolve(symbol);
        assertEquals(MappingStatus.MANUAL, rule.status());
        assertEquals(new TargetSymbol(SymbolKind.CLASS, candidate, "", ""), rule.target());
        assertEquals(MappingCategory.PACKAGE_MOVE, rule.category());
        assertEquals(MappingConfidence.HEURISTIC, rule.confidence());
        assertTrue(rule.evidence().stream().anyMatch(line -> line.contains(candidate)));
        assertFalse(rule.automaticRewriteAllowed());
    }

    @Test
    void severalSameSimpleNameCandidatesNeverSelectAnArbitraryTarget() throws IOException {
        MappingInputs baseline = standardInputs();
        String oldOwner = "net/minecraftforge/old/Channel";
        String first = "net/minecraftforge/first/Channel";
        String second = "net/minecraftforge/second/Channel";
        ApiIndex source = readApi("ambiguous-move-source.jar", Map.of(oldOwner + ".class", apiClass(oldOwner, List.of(), List.of())));
        ApiIndex target = readApi("ambiguous-move-target.jar", Map.of(
                first + ".class", apiClass(first, List.of(), List.of()),
                second + ".class", apiClass(second, List.of(), List.of())));
        LegacySymbol symbol = LegacySymbol.classSymbol(oldOwner);
        MappingRule rule = new MappingDatabaseGenerator(withApis(baseline, source, target, true))
                .generate(List.of(symbol)).resolve(symbol);
        assertEquals(MappingStatus.MANUAL, rule.status());
        assertNull(rule.target());
        assertTrue(rule.evidence().stream().anyMatch(line -> line.contains(first) && line.contains(second)));
        assertFalse(rule.automaticRewriteAllowed());
    }

    @Test
    void aResolvedClassDoesNotInventAMissingTargetConstructor() throws IOException {
        MappingInputs baseline = standardInputs();
        IMappingFile targetOfficial = mappings(TARGET_OFFICIAL.replace("    1:2:void <init>(java.lang.String) -> <init>\n", ""));
        MappingInputs missingConstructor = new MappingInputs(baseline.legacyOfficial(), targetOfficial,
                baseline.legacySrg(), baseline.targetSrg(), baseline.legacyApi(), baseline.targetApi(), List.of(), List.of(), true);
        LegacySymbol constructor = LegacySymbol.constructor(OLD, "(Ljava/lang/String;)V");
        MappingDatabase database = new MappingDatabaseGenerator(missingConstructor).generate(List.of(constructor));
        assertEquals(MappingStatus.RESOLVED, database.resolve(LegacySymbol.classSymbol(OLD)).status());
        assertEquals(MappingStatus.UNSUPPORTED, database.resolve(constructor).status());
        assertNull(database.resolve(constructor).target());
        assertFalse(database.resolve(constructor).automaticRewriteAllowed());
    }

    @Test
    void conflictingCanonicalAndRuntimeClassAliasesFailClosed() throws IOException {
        MappingInputs baseline = standardInputs();
        IMappingFile official = mappings("""
                net.minecraft.example.One -> a:
                net.minecraft.example.Two -> b:
                """);
        IMappingFile srg = mappings("""
                a net/minecraft/example/Two
                b net/minecraft/legacy/Other
                """);
        MappingInputs conflicting = new MappingInputs(official, baseline.targetOfficial(), srg, baseline.targetSrg(),
                baseline.legacyApi(), baseline.targetApi(), List.of(), List.of(), true);
        LegacySymbol symbol = LegacySymbol.classSymbol("net/minecraft/example/Two");
        MappingRule rule = new MappingDatabaseGenerator(conflicting).generate(List.of(symbol)).resolve(symbol);
        assertEquals(MappingStatus.MANUAL, rule.status());
        assertNull(rule.target());
        assertTrue(rule.evidence().stream().anyMatch(line -> line.contains("Conflicting source namespace aliases")));
    }

    @Test
    void conflictingAliasesOnlyInsideAMethodDescriptorAlsoFailClosed() throws IOException {
        String ambiguous = "net/minecraft/example/Two";
        String canonicalParameter = "net/minecraft/example/One";
        String otherRuntime = "net/minecraft/legacy/Other";
        IMappingFile sourceOfficial = mappings("""
                net.minecraft.example.Token -> a:
                    1:2:net.minecraft.example.One echo(net.minecraft.example.One) -> e
                net.minecraft.example.One -> b:
                net.minecraft.example.Two -> c:
                """);
        IMappingFile targetOfficial = mappings("""
                net.minecraft.example.Token -> x:
                    1:2:net.minecraft.example.One echo(net.minecraft.example.One) -> e
                net.minecraft.example.One -> y:
                net.minecraft.example.Two -> z:
                """);
        IMappingFile sourceSrg = mappings("""
                a net/minecraft/legacy/Token
                	e (Lb;)Lb; func_echo
                b net/minecraft/example/Two
                c net/minecraft/legacy/Other
                """);
        IMappingFile targetSrg = mappings("""
                tsrg2 obf srg id
                x net/minecraft/src/C_100_ 100
                	e (Ly;)Ly; m_100_ 100
                y net/minecraft/src/C_200_ 200
                z net/minecraft/src/C_300_ 300
                """);
        String oldDescriptor = "(L" + ambiguous + ";)L" + ambiguous + ";";
        String targetDescriptor = "(L" + canonicalParameter + ";)L" + canonicalParameter + ";";
        ApiIndex source = readApi("descriptor-alias-source.jar", Map.of(
                OLD + ".class", apiClass(OLD, List.of(member("echo", oldDescriptor)), List.of()),
                ambiguous + ".class", apiClass(ambiguous, List.of(), List.of()),
                otherRuntime + ".class", apiClass(otherRuntime, List.of(), List.of())));
        ApiIndex target = readApi("descriptor-alias-target.jar", Map.of(
                CANONICAL + ".class", apiClass(CANONICAL, List.of(member("echo", targetDescriptor)), List.of()),
                canonicalParameter + ".class", apiClass(canonicalParameter, List.of(), List.of()),
                ambiguous + ".class", apiClass(ambiguous, List.of(), List.of())));
        MappingInputs inputs = new MappingInputs(sourceOfficial, targetOfficial, sourceSrg, targetSrg,
                source, target, List.of(), List.of(), true);
        LegacySymbol symbol = LegacySymbol.method(OLD, "func_echo", oldDescriptor);
        MappingRule rule = new MappingDatabaseGenerator(inputs).generate(List.of(symbol)).resolve(symbol);
        assertEquals(MappingStatus.MANUAL, rule.status());
        assertNull(rule.target());
        assertFalse(rule.automaticRewriteAllowed());
    }

    @Test
    void ambiguousMemberAliasesDoNotChooseTheFirstProguardEntry() throws IOException {
        MappingInputs baseline = standardInputs();
        IMappingFile official = mappings(SOURCE_OFFICIAL + "    11:12:java.lang.String func_1() -> z\n");
        IMappingFile srg = mappings(SOURCE_SRG + "\tz ()Ljava/lang/String; func_9\n");
        MappingInputs conflicting = new MappingInputs(official, baseline.targetOfficial(), srg, baseline.targetSrg(),
                baseline.legacyApi(), baseline.targetApi(), List.of(), List.of(), true);
        LegacySymbol symbol = LegacySymbol.method(OLD, "func_1", "()Ljava/lang/String;");
        MappingRule rule = new MappingDatabaseGenerator(conflicting).generate(List.of(symbol)).resolve(symbol);
        assertEquals(MappingStatus.MANUAL, rule.status());
        assertNull(rule.target());
        assertTrue(rule.evidence().stream().anyMatch(line -> line.contains("ambiguous")));
    }

    @Test
    void exactNamesDoNotResolveWhenStaticnessChanged() throws IOException {
        MappingInputs baseline = standardInputs();
        ApiIndex target = readApi("static-target.jar", Map.of(CANONICAL + ".class", apiClass(CANONICAL,
                List.of(new Member("getValue", "()Ljava/lang/String;", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)), List.of())));
        LegacySymbol getter = LegacySymbol.method(OLD, "func_1", "()Ljava/lang/String;");
        MappingRule rule = new MappingDatabaseGenerator(withApis(baseline, baseline.legacyApi(), target, true))
                .generate(List.of(getter)).resolve(getter);
        assertEquals(MappingStatus.MANUAL, rule.status());
        assertNull(rule.target());
    }

    @Test
    void absenceOfTargetSrgDataDoesNotLeakObfuscatedMethodNames() throws IOException {
        MappingInputs baseline = standardInputs();
        MappingInputs missingSrg = new MappingInputs(baseline.legacyOfficial(), baseline.targetOfficial(),
                baseline.legacySrg(), mappings("tsrg2 obf srg id\ny net/minecraft/src/C_200_ 200\n"),
                baseline.legacyApi(), baseline.targetApi(), List.of(), List.of(), true);
        LegacySymbol getter = LegacySymbol.method(OLD, "func_1", "()Ljava/lang/String;");
        MappingRule rule = new MappingDatabaseGenerator(missingSrg).generate(List.of(getter)).resolve(getter);
        assertEquals(MappingStatus.MANUAL, rule.status());
        assertNull(rule.target());
    }

    @Test
    void incompleteInputsAndUncoveredDescriptorClassesNeverGetAnIdentityFallback() throws IOException {
        MappingInputs baseline = standardInputs();
        LegacySymbol symbol = LegacySymbol.classSymbol(OLD);
        MappingRule incomplete = new MappingDatabaseGenerator(withApis(baseline, baseline.legacyApi(), baseline.targetApi(), false))
                .generate(List.of(symbol)).resolve(symbol);
        assertEquals(MappingStatus.MANUAL, incomplete.status());
        assertNull(incomplete.target());

        LegacySymbol descriptor = LegacySymbol.descriptor("(L" + OLD + ";Lsample/mod/Unknown;)V");
        MappingRule unresolvedDescriptor = new MappingDatabaseGenerator(baseline).generate(List.of(descriptor)).resolve(descriptor);
        assertEquals(MappingStatus.MANUAL, unresolvedDescriptor.status());
        assertNull(unresolvedDescriptor.target());
        assertFalse(unresolvedDescriptor.automaticRewriteAllowed());
    }

    private MappingInputs standardInputs() throws IOException {
        List<Member> sourceMethods = List.of(member("<init>", "(Ljava/lang/String;)V"), member("getValue", "()Ljava/lang/String;"),
                member("removed", "()V"), member("changed", "(I)V"), member("copy", "(L" + OLD + ";)L" + OLD + ";"));
        List<Member> targetMethods = List.of(member("<init>", "(Ljava/lang/String;)V"), member("getValue", "()Ljava/lang/String;"),
                member("changed", "(J)V"), member("copy", "(L" + CANONICAL + ";)L" + CANONICAL + ";"));
        ApiIndex source = readApi("source.jar", Map.of(OLD + ".class", apiClass(OLD, sourceMethods,
                List.of(member("value", "Ljava/lang/String;"), member("removedField", "I"), member("changedField", "I")))));
        ApiIndex target = readApi("target.jar", Map.of(CANONICAL + ".class", apiClass(CANONICAL, targetMethods,
                List.of(member("value", "Ljava/lang/String;"), member("changedField", "J")))));
        return new MappingInputs(mappings(SOURCE_OFFICIAL), mappings(TARGET_OFFICIAL), mappings(SOURCE_SRG), mappings(TARGET_SRG),
                source, target, List.of(), List.of(), true);
    }

    private static MappingInputs withApis(MappingInputs original, ApiIndex source, ApiIndex target, boolean complete) {
        return new MappingInputs(original.legacyOfficial(), original.targetOfficial(), original.legacySrg(), original.targetSrg(),
                source, target, original.sources(), original.diagnostics(), complete);
    }

    private ApiIndex readApi(String filename, Map<String, byte[]> classes) throws IOException {
        return ApiIndex.read(List.of(TestJarBuilder.write(temporary.resolve(filename), classes)));
    }

    private static IMappingFile mappings(String contents) throws IOException {
        return IMappingFile.load(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)));
    }

    private static Member member(String name, String descriptor) {
        return new Member(name, descriptor, Opcodes.ACC_PUBLIC);
    }

    private static byte[] apiClass(String owner, List<Member> methods, List<Member> fields) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        for (Member field : fields) {
            writer.visitField(field.access(), field.name(), field.descriptor(), null, null).visitEnd();
        }
        for (Member method : methods) {
            var code = writer.visitMethod(method.access(), method.name(), method.descriptor(), null, null);
            code.visitCode();
            if (method.name().equals("<init>")) {
                code.visitVarInsn(Opcodes.ALOAD, 0);
                code.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            }
            Type returnType = Type.getReturnType(method.descriptor());
            switch (returnType.getSort()) {
                case Type.VOID -> code.visitInsn(Opcodes.RETURN);
                case Type.OBJECT, Type.ARRAY -> {
                    code.visitInsn(Opcodes.ACONST_NULL);
                    code.visitInsn(Opcodes.ARETURN);
                }
                default -> throw new IllegalArgumentException("Fixture helper needs an explicit primitive return body");
            }
            code.visitMaxs(0, 0);
            code.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void assertResolved(MappingRule rule, SymbolKind kind, String owner, String name, String descriptor) {
        assertEquals(MappingStatus.RESOLVED, rule.status(), rule.evidence().toString());
        assertEquals(new TargetSymbol(kind, owner, name, descriptor), rule.target());
        assertEquals(MappingConfidence.HIGH, rule.confidence());
        assertFalse(rule.automaticRewriteAllowed());
    }

    private record Member(String name, String descriptor, int access) { }
}
