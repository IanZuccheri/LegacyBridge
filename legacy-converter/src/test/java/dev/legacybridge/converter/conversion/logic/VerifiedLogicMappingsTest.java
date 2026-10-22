package dev.legacybridge.converter.conversion.logic;

import dev.legacybridge.converter.mapping.*;
import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import dev.legacybridge.converter.transformation.TransformationException;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import net.minecraftforge.srgutils.IMappingFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Original declaration-only synthetic fixtures; no Minecraft/mod class is loaded. */
class VerifiedLogicMappingsTest {
    private static final String OLD = "net/minecraft/item/Item";
    private static final String TARGET = "net/minecraft/world/item/Item";
    private static final String STACK = "net/minecraft/item/ItemStack";
    private static final String NEW_STACK = "net/minecraft/world/item/ItemStack";
    private static final String BASE = "fixture/LogicItem";
    private static final String CHILD = "fixture/ChildItem";
    private static final String FOIL = "(L" + STACK + ";)Z";
    private static final String METAFACTORY_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";

    @Test
    void normalizesOnlyReviewedAncestryAndDoesNotGuessMissingRequiredRows() throws Exception {
        String reviewed = "net/minecraft/example/Reviewed", unrelated = "net/minecraft/example/Unrelated";
        String official = "net.minecraft.example.Reviewed -> a:\n    1:2:int value() -> v\n"
                + "net.minecraft.example.Unrelated -> b:\n    1:2:void missingSrgRow() -> m\n";
        String srg = "a " + reviewed + "\n\tv ()I func_value\nb " + unrelated + "\n";
        var api = new ApiIndex(Map.of(reviewed, type(reviewed, List.of(member("value", "()I"))),
                unrelated, type(unrelated, List.of(member("missingSrgRow", "()V")))));
        var namespace = new LogicApiNamespace(mapping(official), mapping(srg), api, true);
        assertEquals(Set.of(reviewed), namespace.normalize(Set.of(reviewed)).classes().keySet());
        assertThrows(TransformationException.class, () -> namespace.normalize(Set.of(unrelated)));
        var missingAncestor = new ApiIndex(Map.of(reviewed, new ApiIndex.ApiClass(reviewed, Opcodes.ACC_PUBLIC,
                "net/minecraftforge/missing/RequiredAncestor", List.of(), List.of(), List.of())));
        assertTrue(assertThrows(TransformationException.class, () -> new LogicApiNamespace(mapping(official), mapping(srg), missingAncestor, true)
                .normalize(Set.of(reviewed))).getMessage().contains("Required reviewed API ancestor declaration absent"));
    }

    @Test
    void sourceDevelopmentAndCanonicalNamespacesMayLegitimatelyReuseDifferentClassNames() throws Exception {
        String canonicalA = "net/minecraft/official/Renderer";
        String overlappingB = "net/minecraft/legacy/Renderer";
        String productionB = "net/minecraft/legacy/OtherRenderer";
        String official = "net.minecraft.official.Renderer -> a:\n"
                + "    1:2:net.minecraft.official.Renderer first() -> f\n"
                + "net.minecraft.legacy.Renderer -> b:\n"
                + "    1:2:net.minecraft.legacy.Renderer second() -> s\n";
        String srg = "a " + overlappingB + "\n\tf ()La; func_first\nb " + productionB + "\n\ts ()Lb; func_second\n";
        var api = new ApiIndex(Map.of(overlappingB, type(overlappingB, List.of(member("first", "()L" + overlappingB + ";"))),
                productionB, type(productionB, List.of(member("second", "()L" + productionB + ";")))));
        var namespace = new LogicApiNamespace(mapping(official), mapping(srg), api, true);
        assertEquals(overlappingB, namespace.productionClass(canonicalA));
        assertEquals(productionB, namespace.productionClass(overlappingB));
        var normalized = namespace.normalize();
        assertEquals("func_first", normalized.getClass(overlappingB).methods().get(0).name());
        assertEquals("()L" + overlappingB + ";", normalized.getClass(overlappingB).methods().get(0).descriptor());
        assertEquals("func_second", normalized.getClass(productionB).methods().get(0).name());
    }

    @Test
    void verifiesOnlyCuratedCandidatesAndMapsOwnedOverrideAndInheritedDispatch() throws Exception {
        MappingInputs inputs = inputs();
        var bytes = new TreeMap<String, byte[]>();
        bytes.put(BASE + ".class", item(false, false));
        bytes.put(CHILD + ".class", child());
        var result = compile(bytes, inputs);
        var override = result.database().resolve(LegacySymbol.method(BASE, "func_77636_d", FOIL));
        assertEquals("m_5812_", override.target().name());
        assertEquals("(L" + NEW_STACK + ";)Z", override.target().descriptor());
        assertEquals(MappingConfidence.VERIFIED, override.confidence());
        assertEquals("m_5812_", result.database().resolve(LegacySymbol.method(CHILD, "func_77636_d", FOIL)).target().name());
        assertEquals("helper", result.database().resolve(LegacySymbol.method(BASE, "helper", "(I)Z")).target().name());
        assertEquals(AdapterCatalog.LOGIC, result.adapterBindings().get(override.legacy()));
        assertTrue(result.sourceApi().getClass(OLD).methods().stream().anyMatch(m -> m.name().equals("func_77636_d")));
        assertTrue(result.targetApi().getClass(TARGET).methods().stream().anyMatch(m -> m.name().equals("m_5812_")));
        assertFalse(result.targetApi().classes().keySet().stream().anyMatch(n -> n.contains("/src/C_")));
        var candidate = new MappingDatabaseGenerator(inputs).generate(List.of(LegacySymbol.method(OLD, "func_77636_d", FOIL))).rules().iterator().next();
        assertEquals(MappingConfidence.HIGH, candidate.confidence(), "Generic generator is intentionally unchanged");
        assertFalse(candidate.automaticRewriteAllowed());
        assertTrue(result.rules().stream().anyMatch(r -> r.get("contract").equals("logic.item-foil-override.v1.owned-override")));
    }

    @Test
    void refusesUnknownExternalMethodEvenWithAnExactHighCandidate() throws Exception {
        var original = item(false, false);
        var node = read(original);
        var method = node.visitMethod(Opcodes.ACC_PUBLIC, "unknown", "()I", null, null);
        method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OLD, "func_unreviewed", "()I", false);
        method.visitInsn(Opcodes.IRETURN); method.visitMaxs(1, 1); method.visitEnd();
        var symbol = LegacySymbol.method(OLD, "func_unreviewed", "()I");
        assertEquals(MappingConfidence.HIGH, new MappingDatabaseGenerator(inputs()).generate(List.of(symbol)).resolve(symbol).confidence());
        assertFailure("LOGIC_UNSUPPORTED_MEMBER", Map.of(BASE + ".class", write(node)), inputs());
    }

    @Test
    void refusesUnknownExternalOverrideWithoutAnyInvocation() throws Exception {
        var node = read(item(false, false));
        var method = node.visitMethod(Opcodes.ACC_PUBLIC, "func_unreviewed", "()I", null, null);
        method.visitCode(); method.visitInsn(Opcodes.ICONST_0); method.visitInsn(Opcodes.IRETURN); method.visitMaxs(1, 1); method.visitEnd();
        assertFailure("LOGIC_UNREVIEWED_EXTERNAL_OVERRIDE", Map.of(BASE + ".class", write(node)), inputs());
    }

    @Test
    void refusesMissingTargetDeclarationsInsteadOfInventingApi() throws Exception {
        MappingInputs valid = inputs();
        Map<String, ApiIndex.ApiClass> target = new TreeMap<>(valid.targetApi().classes());
        target.remove(NEW_STACK);
        MappingInputs incompleteApi = new MappingInputs(valid.legacyOfficial(), valid.targetOfficial(), valid.legacySrg(), valid.targetSrg(),
                valid.legacyApi(), new ApiIndex(target), List.of(), List.of(), true);
        assertFailure("LOGIC_MAPPING_DATA_REQUIRED", Map.of(BASE + ".class", item(false, false)), incompleteApi);
    }

    @Test
    void refusesFinalTargetOverride() throws Exception {
        MappingInputs valid = inputs();
        Map<String, ApiIndex.ApiClass> target = new TreeMap<>(valid.targetApi().classes());
        ApiIndex.ApiClass item = target.get(TARGET);
        target.put(TARGET, new ApiIndex.ApiClass(TARGET, item.access(), item.superName(), item.interfaces(),
                item.methods().stream().map(m -> m.name().equals("isFoil") ? new ApiIndex.ApiMember(m.name(), m.descriptor(), m.access() | Opcodes.ACC_FINAL) : m).toList(), item.fields()));
        assertFailure("LOGIC_OVERRIDE_TARGET_CHANGED", Map.of(BASE + ".class", item(false, false)),
                new MappingInputs(valid.legacyOfficial(), valid.targetOfficial(), valid.legacySrg(), valid.targetSrg(), valid.legacyApi(), new ApiIndex(target), List.of(), List.of(), true));
    }

    @Test
    void refusesRegistryObjectGetAndDirectLegacyRegistryEntry() throws Exception {
        ClassNode node = plainNode("fixture/Plain");
        var method = node.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "get", "(Lnet/minecraftforge/fml/RegistryObject;)Ljava/lang/Object;", null, null);
        method.visitCode(); method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraftforge/fml/RegistryObject", "get", "()Ljava/lang/Object;", false);
        method.visitInsn(Opcodes.ARETURN); method.visitMaxs(1, 1); method.visitEnd();
        assertFailure("LOGIC_UNSUPPORTED_MEMBER", Map.of(node.name + ".class", write(node)), inputs());
        ClassNode entry = plainNode("fixture/Entry");
        entry.visitField(Opcodes.ACC_PUBLIC, "forbidden", "Lnet/minecraftforge/registries/IForgeRegistryEntry;", null, null).visitEnd();
        assertFailure("LOGIC_UNSUPPORTED_TYPE", Map.of(entry.name + ".class", write(entry)), inputs());
    }

    @Test
    void detectsUnreviewedTypesInGenericSignaturesAndAnnotations() throws Exception {
        ClassNode node = plainNode("fixture/Signature");
        node.visitField(Opcodes.ACC_PUBLIC, "value", "Ljava/lang/Object;", "Ljava/util/List<Ljava/lang/String;>;", null).visitEnd();
        assertFailure("LOGIC_UNSUPPORTED_TYPE", Map.of(node.name + ".class", write(node)), inputs());
        ClassNode annotated = plainNode("fixture/Annotated");
        annotated.visitAnnotation("Lfixture/OwnAnnotation;", true).visitEnd();
        ClassNode annotation = plainNode("fixture/OwnAnnotation");
        assertFailure("LOGIC_UNSUPPORTED_ANNOTATION", Map.of(annotated.name + ".class", write(annotated), annotation.name + ".class", write(annotation)), inputs());
    }

    @Test
    void allowsOnlyExactStandardOwnedLambdaAndKeepsBootstrapTypesMetadataOnly() throws Exception {
        var compiled = compile(Map.of(BASE + ".class", item(true, false)), inputs());
        assertTrue(compiled.rules().stream().anyMatch(r -> r.get("contract").equals("logic.owned-lambda-metafactory.v1")));
        assertFailure("LOGIC_UNSUPPORTED_BOOTSTRAP", Map.of(BASE + ".class", item(true, true)), inputs());
        ClassNode node = read(item(true, false));
        node.visitField(Opcodes.ACC_PUBLIC, "forbiddenHandle", "Ljava/lang/invoke/MethodHandle;", null, null).visitEnd();
        assertFailure("LOGIC_BOOTSTRAP_ONLY_TYPE", Map.of(BASE + ".class", write(node)), inputs());
    }

    @Test
    void rejectsModAnnotationsOnFieldsMethodsParametersAndTypeUses() throws Exception {
        for (String placement : List.of("field", "method", "parameter", "type")) {
            ClassNode node = plainNode("fixture/HiddenEntrypoint");
            AnnotationVisitor annotation;
            if (placement.equals("field")) {
                var field = node.visitField(Opcodes.ACC_PUBLIC, "value", "I", null, null);
                annotation = field.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true);
            } else {
                var method = node.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "helper", "(I)V", null, null);
                annotation = switch (placement) {
                    case "method" -> method.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true);
                    case "parameter" -> method.visitParameterAnnotation(0, "Lnet/minecraftforge/fml/common/Mod;", true);
                    default -> method.visitTypeAnnotation(TypeReference.newTypeReference(TypeReference.METHOD_RETURN).getValue(), null,
                            "Lnet/minecraftforge/fml/common/Mod;", true);
                };
            }
            annotation.visit("value", "hidden_mod"); annotation.visitEnd();
            assertFailure("LOGIC_UNSUPPORTED_ANNOTATION", Map.of(node.name + ".class", write(node)), inputs());
        }
    }

    @Test
    void functionalInterfaceAnnotationRequiresARealInterfaceClassTarget() throws Exception {
        ClassNode ordinary = plainNode("fixture/NotAnInterface");
        ordinary.visitAnnotation("Ljava/lang/FunctionalInterface;", true).visitEnd();
        assertFailure("LOGIC_UNSUPPORTED_ANNOTATION", Map.of(ordinary.name + ".class", write(ordinary)), inputs());
        ClassNode real = plainNode("fixture/Function");
        real.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
        real.visitAnnotation("Ljava/lang/FunctionalInterface;", true).visitEnd();
        real.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "apply", "(I)I", null, null).visitEnd();
        assertNotNull(compile(Map.of(real.name + ".class", write(real)), inputs()));
    }

    @Test
    void rejectsMutationAfterScanAndIncompleteMappingInputs() throws Exception {
        var scan = new LegacyReferenceScanner().scan(Map.of(BASE + ".class", item(false, false)));
        var nodes = scan.classNodes();
        nodes.get(BASE).sourceFile = "changed.java";
        assertTrue(assertThrows(TransformationException.class, () -> VerifiedLogicMappings.compile(nodes, scan, inputs())).getMessage().contains("LOGIC_SOURCE_MISMATCH"));
        var valid = inputs();
        var incomplete = new MappingInputs(valid.legacyOfficial(), valid.targetOfficial(), valid.legacySrg(), valid.targetSrg(), valid.legacyApi(), valid.targetApi(), List.of(), List.of(), false);
        assertFailure("LOGIC_MAPPING_DATA_REQUIRED", Map.of(BASE + ".class", item(false, false)), incomplete);
    }

    private static VerifiedLogicMappings.Result compile(Map<String, byte[]> entries, MappingInputs inputs) {
        var scan = new LegacyReferenceScanner().scan(entries);
        return VerifiedLogicMappings.compile(scan.classNodes(), scan, inputs);
    }
    private static void assertFailure(String code, Map<String, byte[]> entries, MappingInputs inputs) {
        var failure = assertThrows(TransformationException.class, () -> compile(entries, inputs));
        assertTrue(failure.getMessage().contains(code), failure.getMessage());
    }
    private static ClassNode plainNode(String name) {
        ClassNode node = new ClassNode(); node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null); return node;
    }
    private static ClassNode read(byte[] bytes) { var node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static byte[] write(ClassNode node) { var writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray(); }

    private static byte[] item(boolean lambda, boolean badBootstrap) {
        ClassWriter w = new ClassWriter(0);
        w.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, BASE, null, OLD, null);
        w.visitSource("LogicItem.java", null);
        w.visitField(Opcodes.ACC_PRIVATE, "threshold", "I", null, null).visitEnd();
        var ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0); ctor.visitTypeInsn(Opcodes.NEW, OLD + "$Properties"); ctor.visitInsn(Opcodes.DUP);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, OLD + "$Properties", "<init>", "()V", false);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, OLD, "<init>", "(L" + OLD + "$Properties;)V", false);
        ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(3, 1); ctor.visitEnd();
        var helper = w.visitMethod(Opcodes.ACC_PUBLIC, "helper", "(I)Z", null, null);
        helper.visitCode(); helper.visitVarInsn(Opcodes.ILOAD, 1); helper.visitInsn(Opcodes.ICONST_1); helper.visitInsn(Opcodes.IAND);
        helper.visitInsn(Opcodes.IRETURN); helper.visitMaxs(2, 2); helper.visitEnd();
        var foil = w.visitMethod(Opcodes.ACC_PUBLIC, "func_77636_d", FOIL, null, null);
        foil.visitCode(); foil.visitVarInsn(Opcodes.ALOAD, 0); foil.visitVarInsn(Opcodes.ALOAD, 1);
        foil.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STACK, "func_190916_E", "()I", false);
        foil.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BASE, "helper", "(I)Z", false);
        foil.visitInsn(Opcodes.IRETURN); foil.visitMaxs(2, 2); foil.visitEnd();
        if (lambda) {
            var factory = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "factory", "()Ljava/util/function/Supplier;", null, null);
            factory.visitCode(); factory.visitInvokeDynamicInsn("get", "()Ljava/util/function/Supplier;",
                    new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", badBootstrap ? "altMetafactory" : "metafactory", METAFACTORY_DESC, false),
                    Type.getMethodType("()Ljava/lang/Object;"), new Handle(Opcodes.H_NEWINVOKESPECIAL, BASE, "<init>", "()V", false), Type.getMethodType("()L" + OLD + ";"));
            factory.visitInsn(Opcodes.ARETURN); factory.visitMaxs(1, 0); factory.visitEnd();
            w.visitInnerClass("java/lang/invoke/MethodHandles$Lookup", "java/lang/invoke/MethodHandles", "Lookup", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);
        }
        w.visitEnd(); return w.toByteArray();
    }
    private static byte[] child() {
        ClassWriter w = new ClassWriter(0); w.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, CHILD, null, BASE, null);
        var ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0); ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, BASE, "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(1, 1); ctor.visitEnd();
        var call = w.visitMethod(Opcodes.ACC_PUBLIC, "callInherited", FOIL, null, null);
        call.visitCode(); call.visitVarInsn(Opcodes.ALOAD, 0); call.visitVarInsn(Opcodes.ALOAD, 1);
        call.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CHILD, "func_77636_d", FOIL, false);
        call.visitInsn(Opcodes.IRETURN); call.visitMaxs(2, 2); call.visitEnd(); w.visitEnd(); return w.toByteArray();
    }

    private static MappingInputs inputs() throws IOException {
        String sourceOfficial = official("a", "b", "c");
        String targetOfficial = official("x", "y", "z");
        String sourceSrg = "a " + OLD + "\n\tf (Lb;)Z func_77636_d\n\tu ()I func_unreviewed\nb " + STACK + "\n\tc ()I func_190916_E\nc " + OLD + "$Properties\n\ts (I)Lc; func_200917_a\n";
        String targetSrg = "x net/minecraft/src/C_1_\n\tf (Ly;)Z m_5812_\n\tu ()I m_unreviewed_\ny net/minecraft/src/C_2_\n\tc ()I m_41613_\nz net/minecraft/src/C_3_\n\ts (I)Lz; m_41487_\n";
        return new MappingInputs(mapping(sourceOfficial), mapping(targetOfficial), mapping(sourceSrg), mapping(targetSrg), api(false), api(true), List.of(), List.of(), true);
    }
    private static String official(String item, String stack, String properties) {
        return "net.minecraft.world.item.Item -> " + item + ":\n"
                + "    1:2:void <init>(net.minecraft.world.item.Item$Properties) -> <init>\n"
                + "    3:4:boolean isFoil(net.minecraft.world.item.ItemStack) -> f\n"
                + "    5:6:int unreviewed() -> u\n"
                + "net.minecraft.world.item.ItemStack -> " + stack + ":\n"
                + "    1:2:int getCount() -> c\n"
                + "net.minecraft.world.item.Item$Properties -> " + properties + ":\n"
                + "    1:2:void <init>() -> <init>\n"
                + "    3:4:net.minecraft.world.item.Item$Properties stacksTo(int) -> s\n";
    }
    private static IMappingFile mapping(String text) throws IOException { return IMappingFile.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))); }
    private static ApiIndex api(boolean target) {
        String item = target ? TARGET : OLD, stack = target ? NEW_STACK : STACK;
        Map<String, ApiIndex.ApiClass> types = new TreeMap<>();
        types.put(item, type(item, List.of(member("<init>", "(L" + item + "$Properties;)V"), member("isFoil", "(L" + stack + ";)Z"), member("unreviewed", "()I"))));
        types.put(stack, type(stack, List.of(member("getCount", "()I"))));
        types.put(item + "$Properties", type(item + "$Properties", List.of(member("<init>", "()V"), member("stacksTo", "(I)L" + item + "$Properties;"))));
        String registryObject = target ? "net/minecraftforge/registries/RegistryObject" : "net/minecraftforge/fml/RegistryObject";
        types.put(registryObject, type(registryObject, List.of(member("get", "()Ljava/lang/Object;"))));
        String mod = "net/minecraftforge/fml/common/Mod";
        types.put(mod, new ApiIndex.ApiClass(mod, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION | Opcodes.ACC_ABSTRACT,
                "java/lang/Object", List.of("java/lang/annotation/Annotation"), List.of(member("value", "()Ljava/lang/String;")), List.of()));
        return new ApiIndex(types);
    }
    private static ApiIndex.ApiClass type(String name, List<ApiIndex.ApiMember> methods) { return new ApiIndex.ApiClass(name, Opcodes.ACC_PUBLIC, "java/lang/Object", List.of(), methods, List.of()); }
    private static ApiIndex.ApiMember member(String name, String desc) { return new ApiIndex.ApiMember(name, desc, Opcodes.ACC_PUBLIC); }
}
