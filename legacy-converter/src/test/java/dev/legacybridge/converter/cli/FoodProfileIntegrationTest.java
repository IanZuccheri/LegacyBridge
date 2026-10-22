package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.LogicPreservingConverter;
import dev.legacybridge.converter.conversion.SimpleContentConverter;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationLimits;
import dev.legacybridge.converter.operation.OperationStage;
import dev.legacybridge.converter.operation.OperationStoppedException;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.ConversionProfile;
import dev.legacybridge.converter.service.ConversionRequest;
import dev.legacybridge.converter.service.EngineOutcome;
import dev.legacybridge.converter.service.LegacyEngineService;
import dev.legacybridge.converter.service.MappingData;
import dev.legacybridge.converter.transformation.BytecodeValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Genuine Forge 36 source archive, never imported, defined, initialized or executed.
 * Negative archives are inert, bounded ASM copies. These are static conversion tests,
 * not evidence of native eating, client/server initialization or multiplayer behavior.
 */
@EnabledIfSystemProperty(named = "legacybridge.verifyFoodEnvironment", matches = "true")
class FoodProfileIntegrationTest {
    private static final String ENTRY = "dev/legacybridge/fixture/food/FoodContent";
    private static final String RATION = "dev/legacybridge/fixture/food/RationItem";
    private static final String ITEM = "net/minecraft/world/item/Item";
    private static final String PROPS = ITEM + "$Properties";
    private static final String FOOD = "net/minecraft/world/food/FoodProperties";
    private static final String BUILDER = FOOD + "$Builder";
    private static final String REGISTRY_OBJECT = "net/minecraftforge/fml/RegistryObject";
    private static final int MAX_ARCHIVE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 2 * 1024 * 1024;
    private MappingInputs inputs;

    @TempDir Path temporary;

    @AfterEach void releaseMappingGraphs() {
        // Only direct-converter tests need these declarations. Never retain a second
        // graph while the CLI/service loads its own inputs, or between test cases.
        inputs = null;
    }

    private MappingInputs inputs() {
        if (inputs == null) {
            MappingInputs loaded = assertDoesNotThrow(() -> new MappingInputsLoader().load(profile()),
                    "Could not load the explicit reviewed fixture mapping profile");
            assertTrue(loaded.complete(), "The genuine fixture requires an explicit complete reviewed mapping profile");
            inputs = loaded;
        }
        return inputs;
    }

    @Test void cliAndServiceAgreeAndPlanningDoesNotPublishOrExecuteAnything() throws Exception {
        assertNull(inputs, "CLI/API parity must not retain an additional test-owned mapping graph");
        Snapshot original = Snapshot.capture(fixture());
        try {
            Path cli = Files.createDirectory(temporary.resolve("cli")).resolve("converted.jar");
            Path api = Files.createDirectory(temporary.resolve("api")).resolve("converted.jar");
            Path cliReport = temporary.resolve("cli-report.json");
            var stdout = new ByteArrayOutputStream();
            var stderr = new ByteArrayOutputStream();
            int exit;
            try (var out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                 var err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
                exit = LegacyBridgeCli.run(new String[]{"convert", fixture().toString(),
                        "--mapping-profile", profile().toString(), "--conversion-profile", "food-item-v1",
                        "--output", cli.toString(), "--report", cliReport.toString()}, out, err, Map.of());
            }
            assertEquals(0, exit, stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8));
            assertTrue(Files.isRegularFile(cli));
            assertTrue(Files.size(cliReport) <= MAX_ENTRY_BYTES, "Report is bounded before reading");

            try (var service = new LegacyEngineService()) {
                var request = new ConversionRequest(fixture(), api, MappingData.profile(profile()),
                        Optional.of(original.sha256()), ConversionProfile.FOOD_ITEM_V1);
                var planOutcome = service.plan(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
                assertEquals(EngineOutcome.Status.COMPLETED, planOutcome.status(), planOutcome.toString());
                var plan = planOutcome.value().orElseThrow();
                assertTrue(plan.convertible(), plan.report().toString());
                assertEquals(original.sha256(), plan.inputSha256());
                assertEquals(api, plan.prospectiveOutput());
                assertTrue(plan.candidateSha256().isPresent());
                assertEquals("PLANNED", plan.report().get("status"));
                assertEquals(false, plan.report().get("transformationsCommitted"));
                assertNull(plan.report().get("output"));
                assertReport(plan.report(), original.sha256());
                assertAbsentAndEmpty(api);

                var outcome = service.convert(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
                assertEquals(EngineOutcome.Status.COMPLETED, outcome.status(), outcome.toString());
                var converted = outcome.value().orElseThrow();
                assertTrue(converted.converted(), converted.report().toString());
                assertEquals("CONVERTED", converted.report().get("status"));
                assertEquals(true, converted.report().get("transformationsCommitted"));
                assertReport(converted.report(), original.sha256());
                assertEquals(plan.candidateSha256().orElseThrow(), sha256(Files.readAllBytes(api)));
                assertEquals(Files.readString(cliReport, StandardCharsets.UTF_8),
                        new CanonicalReportWriter().toJsonTree(converted.report()),
                        "CLI and service share the same canonical structured result, not stdout parsing");
            }
            assertArrayEquals(Files.readAllBytes(cli), Files.readAllBytes(api));
            assertBodiesAndResourcesPreserved(entries(original.bytes()), entries(Files.readAllBytes(api)));
            assertNull(inputs, "CLI/API parity must use only the engine-owned mappings");
        } finally {
            original.assertUnchanged();
        }
    }

    @Test void cancellationAcceptedImmediatelyBeforePublicationLeavesNoJarOrTemporary() throws Exception {
        Snapshot original = Snapshot.capture(fixture());
        MappingInputs directInputs = inputs();
        Path output = Files.createDirectory(temporary.resolve("cancelled-output")).resolve("converted.jar");
        var reference = new AtomicReference<OperationControl>();
        var boundaryReached = new AtomicBoolean();
        var accepted = new AtomicBoolean();
        var control = new OperationControl(OperationLimits.defaults(), progress -> {
            if (progress.stage() == OperationStage.PRE_PUBLICATION) {
                boundaryReached.set(true);
                accepted.set(reference.get().requestCancel());
            }
        });
        reference.set(control);
        try {
            try (var scope = control.enter()) {
                var stopped = assertThrows(OperationStoppedException.class,
                        () -> converter().convert(fixture(), output, directInputs));
                assertEquals(OperationStoppedException.Code.CANCELLED, stopped.code());
            }
            assertTrue(boundaryReached.get(), "The real candidate must reach the last cancellable boundary");
            assertTrue(accepted.get(), "Accepted cancellation is the no-publication guarantee");
            assertAbsentAndEmpty(output);
        } finally {
            original.assertUnchanged();
        }
    }

    @Test void previousProfilesStillRefuseTheFoodFixtureWithoutPublishingAnything() throws Exception {
        Snapshot original = Snapshot.capture(fixture());
        try {
            for (ConversionProfile profile : List.of(ConversionProfile.SIMPLE_CONTENT_V1,
                    ConversionProfile.LOGIC_PRESERVING_V1, ConversionProfile.LIFECYCLE_PRESERVING_V1)) {
                Path output = Files.createDirectory(temporary.resolve(profile.name())).resolve("converted.jar");
                var result = profile == ConversionProfile.SIMPLE_CONTENT_V1
                        ? new SimpleContentConverter().convert(fixture(), output, inputs())
                        : new LogicPreservingConverter(profile).convert(fixture(), output, inputs());
                assertFalse(result.converted(), profile + ": " + result.report());
                assertEquals("ABORTED", result.report().get("status"));
                assertEquals("UNSUPPORTED", result.report().get("compatibilityStatus"));
                assertEquals(false, result.report().get("transformationsCommitted"));
                assertNull(result.report().get("output"));
                assertAbsentAndEmpty(output);
            }
        } finally { original.assertUnchanged(); }
    }

    @Test void knownItemOverrideIsStillOutsideTheNativeFoodEnvelope() throws Exception {
        String descriptor = sourceDescriptor("(Lnet/minecraft/world/item/ItemStack;)Z");
        String name = sourceMethod(ITEM, "isFoil", "(Lnet/minecraft/world/item/ItemStack;)Z");
        mutateClass("custom-override", RATION, node -> {
            MethodNode override = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
            override.instructions.add(new InsnNode(Opcodes.ICONST_1));
            override.instructions.add(new InsnNode(Opcodes.IRETURN));
            node.methods.add(override);
        }, "FOOD_CUSTOM_BEHAVIOR");
    }

    @Test void realFastMeatAndAlwaysEatBuilderMethodsAreNotSilentlyDiscarded() throws Exception {
        for (String flag : List.of("fast", "meat", "alwaysEat")) {
            String name = sourceMethod(BUILDER, flag, "()L" + BUILDER + ";");
            mutateClass("flag-" + flag, RATION, node -> {
                MethodNode ctor = method(node, "<init>", "()V");
                ctor.instructions.insertBefore(call(ctor, BUILDER, "build", "()L" + FOOD + ";"),
                        new MethodInsnNode(Opcodes.INVOKEVIRTUAL, sourceClass(BUILDER), name,
                                sourceDescriptor("()L" + BUILDER + ";"), false));
            }, "LOGIC_UNSUPPORTED_MEMBER", name);
        }
    }

    @Test void durabilityCannotBeAddedToTheReviewedStackableFoodProperties() throws Exception {
        String name = sourceMethod(PROPS, "durability", "(I)L" + PROPS + ";");
        mutateClass("durability", RATION, node -> {
            MethodNode ctor = method(node, "<init>", "()V");
            InsnList extra = new InsnList();
            extra.add(new InsnNode(Opcodes.ICONST_1));
            extra.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, sourceClass(PROPS), name,
                    sourceDescriptor("(I)L" + PROPS + ";"), false));
            ctor.instructions.insert(call(ctor, PROPS, "stacksTo", "(I)L" + PROPS + ";"), extra);
        }, "LOGIC_UNSUPPORTED_MEMBER", name);
    }

    @Test void craftingRemainderSetterIsUnsupportedEvenWhenItsArgumentIsNull() throws Exception {
        String name = sourceMethod(PROPS, "craftRemainder", "(L" + ITEM + ";)L" + PROPS + ";");
        mutateClass("remainder", RATION, node -> {
            MethodNode ctor = method(node, "<init>", "()V");
            InsnList extra = new InsnList();
            // Typed null avoids introducing a second, unrelated registry/API contract.
            // No invocation occurs: the unsupported setter itself must be rejected.
            extra.add(new InsnNode(Opcodes.ACONST_NULL));
            extra.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, sourceClass(PROPS), name,
                    sourceDescriptor("(L" + ITEM + ";)L" + PROPS + ";"), false));
            ctor.instructions.insert(call(ctor, PROPS, "stacksTo", "(I)L" + PROPS + ";"), extra);
        }, "LOGIC_UNSUPPORTED_MEMBER", name);
    }

    @Test void earlyRegistryGetCannotEscapeTheDeferredRegistrationContract() throws Exception {
        var declaration = inputs().legacyApi().getClass(REGISTRY_OBJECT);
        assertNotNull(declaration);
        assertTrue(declaration.methods().stream().anyMatch(m -> m.name().equals("get")
                && m.descriptor().equals("()Ljava/lang/Object;")), "The real Supplier bridge must exist");
        mutateClass("early-registry-get", ENTRY, node -> {
            assertTrue(node.fields.stream().anyMatch(f -> f.name.equals("RATION")
                    && f.desc.equals("L" + REGISTRY_OBJECT + ";")));
            InsnList extra = new InsnList();
            extra.add(new FieldInsnNode(Opcodes.GETSTATIC, ENTRY, "RATION", "L" + REGISTRY_OBJECT + ";"));
            extra.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, REGISTRY_OBJECT, "get", "()Ljava/lang/Object;", false));
            extra.add(new InsnNode(Opcodes.POP));
            method(node, "<init>", "()V").instructions.insert(extra);
        }, "LOGIC_UNSUPPORTED_MEMBER", "RegistryObject.get");
    }

    @Test void unknownExternalCallIsRejectedWithoutLoadingTheMissingClass() throws Exception {
        mutateClass("unknown-external", ENTRY, node -> method(node, "<init>", "()V").instructions.insert(
                new MethodInsnNode(Opcodes.INVOKESTATIC, "unknown/dependency/FoodApi", "run", "()V", false)),
                "LOGIC_UNSUPPORTED_TYPE", "unknown/dependency/FoodApi");
    }

    @Test void missingMandatoryServerDependencyIsNotLostDuringMetadataMigration() throws Exception {
        mutateEntry("missing-dependency", "META-INF/mods.toml", bytes -> {
            String toml = new String(bytes, StandardCharsets.UTF_8) + "\n[[dependencies.legacybridge_food]]\n"
                    + "modId=\"required_food_library\"\nmandatory=true\nversionRange=\"[2,3)\"\n"
                    + "ordering=\"AFTER\"\nside=\"SERVER\"\n";
            return toml.getBytes(StandardCharsets.UTF_8);
        }, "MISSING_MANDATORY_DEPENDENCY", "required_food_library");
    }

    @Test void nonfiniteAndNegativeSaturationLiteralsAreRejectedWithoutPublishingInvalidJson() throws Exception {
        float[] values = {Float.NaN, -0.5F, Float.POSITIVE_INFINITY};
        for (int index = 0; index < values.length; index++) {
            float value = values[index];
            mutateClass("saturation-" + index, RATION, node -> {
                MethodNode ctor = method(node, "<init>", "()V");
                MethodInsnNode call = call(ctor, BUILDER, "saturationMod", "(F)L" + BUILDER + ";");
                List<AbstractInsnNode> code = code(ctor);
                AbstractInsnNode literal = code.get(code.indexOf(call) - 1);
                assertTrue(literal instanceof LdcInsnNode ldc && Float.valueOf(0.5F).equals(ldc.cst));
                ctor.instructions.set(literal, new LdcInsnNode(value));
            }, "FOOD_LITERAL_RANGE", "saturation");
        }
    }

    @Test void nutritionOutsideTheExplicitLiteralRangeIsRejected() throws Exception {
        mutateClass("negative-nutrition", RATION, node -> {
            MethodNode ctor = method(node, "<init>", "()V");
            MethodInsnNode call = call(ctor, BUILDER, "nutrition", "(I)L" + BUILDER + ";");
            List<AbstractInsnNode> code = code(ctor);
            AbstractInsnNode literal = code.get(code.indexOf(call) - 1);
            assertEquals(Opcodes.ICONST_3, literal.getOpcode());
            ctor.instructions.set(literal, new InsnNode(Opcodes.ICONST_M1));
        }, "FOOD_LITERAL_RANGE", "nutrition");
    }

    @Test void validDescriptorDoesNotAuthorizeAnInterfaceOpcodeForAConcreteBuilder() throws Exception {
        mutateClass("wrong-invocation-kind", RATION, node -> {
            MethodInsnNode call = call(method(node, "<init>", "()V"), BUILDER, "nutrition", "(I)L" + BUILDER + ";");
            assertEquals(Opcodes.INVOKEVIRTUAL, call.getOpcode());
            call.setOpcode(Opcodes.INVOKEINTERFACE);
            call.itf = true; // Stack-balanced, but the actual owner is a class rather than an interface.
        }, "LOGIC_INVOCATION_KIND");
    }

    private static void assertReport(Map<String, Object> report, String inputSha256) {
        boolean planned = "PLANNED".equals(report.get("status"));
        assertEquals(planned ? 1 : 2, report.get("schemaVersion"));
        assertEquals(planned ? "LEGACY_CONVERSION_PLAN" : "LEGACY_CONVERSION", report.get("reportType"));
        assertEquals("food-item-v1", report.get("supportProfile"));
        assertEquals("legacybridge_food", report.get("modId"));
        assertEquals("1.0.0", report.get("modVersion"));
        assertEquals(false, report.get("bytecodeExecuted"));
        assertEquals("NOT_RUN", report.get("runtimeValidation"));
        assertEquals(inputSha256, tree(report, "input").get("sha256"));
        assertEquals(2, report.get("preservedClassCount"));
        assertEquals(3, report.get("preservedMethodCount"));
        assertEquals(0, report.get("replacedClassCount"));
        assertEquals(1, report.get("itemCount"));
        assertEquals(0, report.get("blockCount"));
        for (String side : List.of("sourceOfflineLinkage", "targetOfflineLinkage")) {
            assertEquals("PASSED_SCOPED", tree(report, side).get("status"));
            assertEquals(false, tree(report, side).get("classesExecuted"));
        }
        var bytecode = tree(report, "bytecodeValidation");
        assertEquals("PASSED", bytecode.get("status"));
        assertEquals(false, bytecode.get("modClassesLoaded"));
        assertEquals(false, bytecode.get("jvmLinkageVerified"));
        assertEquals(true, bytecode.get("offlineLinkageVerified"));
        var food = tree(report, "foodProperties");
        assertEquals(1, food.get("schemaVersion"));
        assertEquals(3, food.get("nutrition"));
        assertEquals(0.5F, ((Number) food.get("saturationModifier")).floatValue());
        assertEquals(false, food.get("fast"));
        assertEquals(false, food.get("meat"));
        assertEquals(false, food.get("alwaysEat"));
        assertEquals(false, food.get("customOverrides"));
        assertEquals(0, food.get("effectCount"));
        assertEquals("NONE", food.get("remainder"));
        assertTrue(food.get("behaviorMigration").toString().contains("no equivalence"));
        assertEquals(1, tree(report, "transformationLog").get("schemaVersion"));
        assertTrue(report.containsKey("sourceDependencyGraph"));
        assertTrue(report.containsKey("targetDependencyGraph"));
    }

    private static void assertBodiesAndResourcesPreserved(Map<String, byte[]> before, Map<String, byte[]> after) {
        Set<String> classes = new java.util.TreeSet<>();
        before.keySet().stream().filter(name -> name.endsWith(".class")).forEach(classes::add);
        assertEquals(Set.of(ENTRY + ".class", RATION + ".class"), classes);
        assertEquals(classes, after.keySet().stream().filter(name -> name.endsWith(".class"))
                .collect(java.util.stream.Collectors.toSet()));
        int methods = 0;
        for (String name : classes) {
            ClassNode source = node(before.get(name)), target = node(after.get(name));
            assertEquals(source.sourceFile, target.sourceFile, "SourceFile debug information");
            assertEquals(source.fields.stream().map(field -> field.name).toList(), target.fields.stream().map(field -> field.name).toList());
            assertEquals(source.methods.size(), target.methods.size());
            for (MethodNode original : source.methods) {
                methods++;
                MethodNode converted = method(target, original.name, original.desc);
                assertEquals(code(original).stream().map(AbstractInsnNode::getOpcode).toList(),
                        code(converted).stream().map(AbstractInsnNode::getOpcode).toList(), name + "." + original.name);
                assertEquals(literals(original), literals(converted), "Original strings/numbers must not become bootstrap data");
                assertEquals(lines(original), lines(converted), "Line-number debug information");
            }
        }
        assertEquals(3, methods);
        assertTrue(before.keySet().stream().anyMatch(name -> name.startsWith("assets/")));
        for (var entry : before.entrySet()) {
            if (entry.getKey().startsWith("assets/") || entry.getKey().startsWith("data/"))
                assertArrayEquals(entry.getValue(), after.get(entry.getKey()), entry.getKey());
        }
        assertFalse(after.keySet().stream().anyMatch(name -> name.endsWith("content.json")));
    }

    private void mutateClass(String name, String owner, Consumer<ClassNode> mutation, String... fragments) throws Exception {
        mutateEntry(name, owner + ".class", original -> {
            ClassNode edited = node(original);
            mutation.accept(edited);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            edited.accept(writer); // No class loading or ClassWriter.getCommonSuperClass resolution.
            byte[] bytes = writer.toByteArray();
            BytecodeValidator.validate(bytes); // Real offline structure/stack validation of each inert negative.
            return bytes;
        }, fragments);
    }

    private void mutateEntry(String name, String changedEntry, ByteMutation mutation, String... fragments) throws Exception {
        Snapshot original = Snapshot.capture(fixture());
        Snapshot mutatedInput = null;
        try {
            Map<String, byte[]> before = entries(original.bytes());
            Map<String, byte[]> changed = new TreeMap<>(before);
            byte[] replacement = mutation.apply(before.get(changedEntry));
            assertFalse(Arrays.equals(before.get(changedEntry), replacement));
            changed.put(changedEntry, replacement);
            Path input = temporary.resolve(name + "-input.jar");
            write(input, changed);
            mutatedInput = Snapshot.capture(input);
            Map<String, byte[]> reread = entries(mutatedInput.bytes());
            assertEquals(before.keySet(), reread.keySet());
            for (var entry : before.entrySet()) {
                if (!entry.getKey().equals(changedEntry)) assertArrayEquals(entry.getValue(), reread.get(entry.getKey()),
                        "Unrelated class, metadata or resource changed: " + entry.getKey());
            }
            Path output = Files.createDirectory(temporary.resolve(name + "-output")).resolve("converted.jar");
            var result = converter().convert(input, output, inputs());
            Map<String, Object> report = result.report();
            assertFalse(result.converted(), report.toString());
            assertEquals("ABORTED", report.get("status"), report.toString());
            assertEquals("UNSUPPORTED", report.get("compatibilityStatus"));
            assertEquals(false, report.get("bytecodeExecuted"));
            assertEquals("NOT_RUN", report.get("runtimeValidation"));
            assertEquals(false, report.get("transformationsCommitted"));
            assertEquals(false, report.get("bytecodeModified"));
            assertNull(report.get("output"));
            for (String fragment : fragments) assertTrue(report.get("diagnostics").toString().contains(fragment), fragment + ": " + report);
            // A refusal must remain serializable even when its input contains NaN/infinity.
            dev.legacybridge.converter.config.BoundedJsonParser.parse(new CanonicalReportWriter().toJsonTree(report));
            assertAbsentAndEmpty(output);
        } finally {
            if (mutatedInput != null) mutatedInput.assertUnchanged();
            original.assertUnchanged();
        }
    }

    private static Path fixture() {
        return Path.of(System.getProperty("legacybridge.fixtureRoot"),
                "food-item-1.16.5/build/libs/legacybridge-food-1.16.5-1.0.0.jar");
    }
    private static Path profile() { return Path.of(System.getProperty("legacybridge.foodMappingProfile")); }
    private static LogicPreservingConverter converter() { return new LogicPreservingConverter(ConversionProfile.FOOD_ITEM_V1); }
    private static Map<?, ?> tree(Map<String, Object> report, String key) {
        Object value = report.get(key);
        assertNotNull(value, key);
        assertInstanceOf(Map.class, value, key);
        return (Map<?, ?>) value;
    }

    /** Official -> obfuscated -> SRG, using only explicit trusted declaration data. No handwritten method map. */
    private String sourceMethod(String canonicalOwner, String name, String descriptor) {
        var type = inputs().legacyOfficial().getClass(canonicalOwner);
        assertNotNull(type, "Official owner: " + canonicalOwner);
        var member = type.getMethod(name, descriptor);
        assertNotNull(member, "Official member: " + canonicalOwner + "." + name + descriptor);
        var runtimeType = inputs().legacySrg().getClass(type.getMapped());
        assertNotNull(runtimeType, "SRG owner: " + canonicalOwner);
        var runtimeMember = runtimeType.getMethod(member.getMapped(), member.getMappedDescriptor());
        assertNotNull(runtimeMember, "SRG member: " + canonicalOwner + "." + name + descriptor);
        return runtimeMember.getMapped();
    }
    private String sourceClass(String canonical) {
        return inputs().legacySrg().remapClass(inputs().legacyOfficial().remapClass(canonical));
    }
    private String sourceDescriptor(String descriptor) {
        return new Remapper() {
            @Override public String map(String name) { return sourceClass(name); }
        }.mapMethodDesc(descriptor);
    }
    private MethodInsnNode call(MethodNode method, String owner, String name, String descriptor) {
        String runtimeOwner = sourceClass(owner), runtimeName = sourceMethod(owner, name, descriptor);
        String runtimeDescriptor = sourceDescriptor(descriptor);
        var calls = code(method).stream().filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.owner.equals(runtimeOwner) && call.name.equals(runtimeName) && call.desc.equals(runtimeDescriptor)).toList();
        assertEquals(1, calls.size(), "Exact genuine source call: " + owner + "." + name + descriptor);
        return calls.get(0);
    }
    private static ClassNode node(byte[] bytes) {
        assertNotNull(bytes, "Expected genuine fixture class bytes");
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }
    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor))
                .findFirst().orElseThrow(() -> new AssertionError(node.name + "." + name + descriptor));
    }
    private static List<AbstractInsnNode> code(MethodNode method) {
        List<AbstractInsnNode> code = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) code.add(instruction);
        return code;
    }
    private static List<Object> literals(MethodNode method) {
        return code(method).stream().filter(LdcInsnNode.class::isInstance).map(LdcInsnNode.class::cast)
                .map(literal -> literal.cst).filter(value -> value instanceof Number || value instanceof String).toList();
    }
    private static List<Integer> lines(MethodNode method) {
        List<Integer> lines = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof LineNumberNode line) lines.add(line.line);
        return lines;
    }
    private static void assertAbsentAndEmpty(Path output) throws IOException {
        assertFalse(Files.exists(output, LinkOption.NOFOLLOW_LINKS), "No partial or published JAR");
        try (var children = Files.list(output.getParent())) { assertEquals(0, children.count(), "No remaining temporary output"); }
    }
    private static Map<String, byte[]> entries(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        long expanded = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (entry.isDirectory()) continue;
                byte[] content = zip.readNBytes(MAX_ENTRY_BYTES + 1);
                expanded += content.length;
                if (content.length > MAX_ENTRY_BYTES || expanded > MAX_ARCHIVE_BYTES || entries.size() >= 128)
                    throw new IOException("Fixture exceeds bounded test archive limits");
                if (entries.putIfAbsent(entry.getName(), content) != null) throw new IOException("Duplicate fixture entry");
            }
        }
        if (entries.isEmpty()) throw new IOException("Empty fixture");
        return entries;
    }
    private static void write(Path output, Map<String, byte[]> entries) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE_NEW))) {
            for (var entry : entries.entrySet()) {
                ZipEntry target = new ZipEntry(entry.getKey());
                target.setTime(0);
                zip.putNextEntry(target);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }
    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private record Snapshot(Path path, byte[] bytes, String sha256) {
        static Snapshot capture(Path path) throws Exception {
            assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Missing genuine fixture: " + path);
            assertTrue(Files.size(path) > 0 && Files.size(path) <= MAX_ARCHIVE_BYTES);
            byte[] bytes = Files.readAllBytes(path);
            return new Snapshot(path, bytes, FoodProfileIntegrationTest.sha256(bytes));
        }
        void assertUnchanged() throws Exception {
            byte[] after = Files.readAllBytes(path);
            assertArrayEquals(bytes, after, "Original input must be immutable: " + path);
            assertEquals(sha256, FoodProfileIntegrationTest.sha256(after));
        }
    }
    @FunctionalInterface private interface ByteMutation { byte[] apply(byte[] original) throws Exception; }
}
