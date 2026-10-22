package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.LogicPreservingConverter;
import dev.legacybridge.converter.conversion.logic.VerifiedLogicMappings;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.converter.transformation.BytecodeValidator;
import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import dev.legacybridge.converter.transformation.LoggedClassRemapper;
import dev.legacybridge.converter.transformation.MappingResolver;
import dev.legacybridge.converter.transformation.TransformationException;
import dev.legacybridge.converter.transformation.TransformationLog;
import dev.legacybridge.converter.transformation.linkage.OfflineLinkageVerifier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Genuine reobfuscated Forge 36 fixtures, inspected/mutated only as archive and ASM data.
 * No fixture class is imported, defined, initialized or executed. A static success is not runtime QA.
 */
@EnabledIfSystemProperty(named = "legacybridge.verifyLifecycleEnvironment", matches = "true")
class LifecycleNegativeJarIntegrationTest {
    private static final String ENTRY = "dev/legacybridge/fixture/lifecycle/LifecycleContent";
    private static final String COMMANDS = "dev/legacybridge/fixture/lifecycle/LifecycleCommands";
    private static final String COMMON_EVENT = "net/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent";
    private static final String CLIENT_EVENT = "net/minecraftforge/fml/event/lifecycle/FMLClientSetupEvent";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final String SUBSCRIBER = "Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;";
    private static final String CONFIG_TYPE = "net/minecraftforge/fml/config/ModConfig$Type";
    private static final String LEGACY_ENTRY = "net/minecraftforge/registries/IForgeRegistryEntry";
    private static final String FUTURE = "Ljava/util/concurrent/CompletableFuture;";
    private static final int MAX_ARCHIVE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 2 * 1024 * 1024;
    private static MappingInputs inputs;

    @TempDir Path temporary;

    @BeforeAll static void explicitMappingInputs() throws Exception {
        inputs = new MappingInputsLoader().load(Path.of(System.getProperty("legacybridge.lifecycleMappingProfile")));
        assertTrue(inputs.complete(), "Integration tests require the explicit, complete reviewed mapping profile");
    }

    @AfterAll static void releaseExplicitMappingInputs() {
        // JUnit retains this test class for the worker lifetime. Do not retain full
        // official mapping graphs while subsequent integration classes load theirs.
        inputs = null;
    }

    @Test void clientSetupClassLiteralDoesNotBecomeACommonPhysicalSideContract() throws Exception {
        withMutation("client-event", ENTRY, node -> {
            List<LdcInsnNode> literals = code(method(node, "<init>", "()V")).stream()
                    .filter(LdcInsnNode.class::isInstance).map(LdcInsnNode.class::cast)
                    .filter(literal -> Type.getObjectType(COMMON_EVENT).equals(literal.cst)).toList();
            assertEquals(1, literals.size(), "The genuine explicit listener must contain exactly one event token");
            literals.get(0).cst = Type.getObjectType(CLIENT_EVENT);
        }, jar -> assertRejected(jar, "LOGIC_UNSUPPORTED_TYPE", CLIENT_EVENT));
    }

    @Test void commandSubscriberOnModBusIsRejectedRatherThanSilentlyMoved() throws Exception {
        withMutation("mod-bus", COMMANDS, node -> {
            List<AnnotationNode> subscribers = annotations(node).stream()
                    .filter(annotation -> annotation.desc.equals(SUBSCRIBER)).toList();
            assertEquals(1, subscribers.size());
            AnnotationNode subscriber = subscribers.get(0);
            assertNotNull(subscriber.values);
            int replaced = 0;
            for (int index = 0; index < subscriber.values.size(); index += 2) {
                if ("bus".equals(subscriber.values.get(index))) {
                    String[] original = (String[]) subscriber.values.get(index + 1);
                    assertEquals("FORGE", original[1]);
                    subscriber.values.set(index + 1, new String[]{original[0], "MOD"});
                    replaced++;
                }
            }
            assertEquals(1, replaced);
        }, jar -> assertRejected(jar, "LOGIC_UNSUPPORTED_MEMBER", "EventBusSubscriber$Bus.MOD"));
    }

    @Test void constructorCannotRunDeferredWorkBeforeInitializationAndRegistryAttachment() throws Exception {
        withMutation("premature-work", ENTRY, node -> {
            MethodNode work = method(node, "finishSetup", "()V");
            assertNotEquals(0, work.access & Opcodes.ACC_STATIC);
            // Valid static bytecode even before Object.<init>; forbidden because it reads a registry too early.
            method(node, "<init>", "()V").instructions.insert(
                    new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, work.name, work.desc, false));
        }, jar -> assertRejected(jar, "LIFECYCLE_PROFILE_SHAPE", "Entrypoint effects/order"));
    }

    @Test void clientConfigIsNotPresentedAsTheReviewedCommonConfig() throws Exception {
        withMutation("client-config", ENTRY, node -> {
            List<FieldInsnNode> types = code(method(node, "<init>", "()V")).stream()
                    .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
                    .filter(field -> field.owner.equals(CONFIG_TYPE) && field.name.equals("COMMON")).toList();
            assertEquals(1, types.size());
            types.get(0).name = "CLIENT";
        }, jar -> assertRejected(jar, "LOGIC_UNSUPPORTED_MEMBER", "ModConfig$Type.CLIENT"));
    }

    @Test void stackValidImplicitArithmeticFailureInQueuedWorkIsRejected() throws Exception {
        withMutation("queued-division", ENTRY, node -> {
            MethodNode work = method(node, "finishSetup", "()V");
            AbstractInsnNode returned = code(work).get(code(work).size() - 1);
            assertEquals(Opcodes.RETURN, returned.getOpcode());
            InsnList division = new InsnList();
            division.add(new InsnNode(Opcodes.ICONST_1));
            division.add(new InsnNode(Opcodes.ICONST_0));
            division.add(new InsnNode(Opcodes.IDIV));
            division.add(new InsnNode(Opcodes.POP));
            work.instructions.insertBefore(returned, division);
        }, jar -> assertRejected(jar, "LIFECYCLE_WORK_ENVELOPE", "implicit-throw"));
    }

    @Test void enqueueFutureCannotEscapeIntoAnAdditionalField() throws Exception {
        withMutation("stored-future", ENTRY, node -> {
            assertTrue(node.fields.stream().noneMatch(field -> field.name.equals("retainedFuture")));
            node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                    "retainedFuture", FUTURE, null, null));
            MethodNode setup = method(node, "commonSetup", "(L" + COMMON_EVENT + ";)V");
            List<AbstractInsnNode> instructions = code(setup);
            List<MethodInsnNode> enqueue = instructions.stream().filter(MethodInsnNode.class::isInstance)
                    .map(MethodInsnNode.class::cast).filter(call -> call.name.equals("enqueueWork")).toList();
            assertEquals(1, enqueue.size());
            AbstractInsnNode pop = instructions.get(instructions.indexOf(enqueue.get(0)) + 1);
            assertEquals(Opcodes.POP, pop.getOpcode());
            setup.instructions.set(pop, new FieldInsnNode(Opcodes.PUTSTATIC, node.name, "retainedFuture", FUTURE));
        }, jar -> assertRejected(jar, "LIFECYCLE_PROFILE_SHAPE", "Unexpected lifecycle opcode"));
    }

    @Test void retainedLegacyRegistryEntryFieldIsNotGloballyErasedAndTargetLinkageRejectsIt() throws Exception {
        withMutation("retained-registry-entry", ENTRY, node -> node.fields.add(new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "retainedLegacyEntry", "L" + LEGACY_ENTRY + ";", null, null)), jar -> {
            // The full converter rejects the extra state before its final target linkage stage.
            assertRejected(jar, "LIFECYCLE_STATE_ESCAPE");

            // Independently exercise the real compiler/remapper/linkage gates on these same input bytes.
            // RegistryObject.get's reviewed return adjustment is not a class-wide Object substitution.
            Map<String, byte[]> source = classEntries(jar.entries());
            var scanner = new LegacyReferenceScanner().scan(source);
            var originals = scanner.classNodes();
            var mappings = VerifiedLogicMappings.compile(originals, scanner, inputs, true);
            new OfflineLinkageVerifier().verify(source, mappings.sourceApi(), true);
            var resolver = new MappingResolver(mappings.database(), mappings.adapterBindings(), originals.keySet());
            assertEquals(LEGACY_ENTRY, resolver.mapClassName(LEGACY_ENTRY));
            Map<String, byte[]> target = new TreeMap<>();
            for (ClassNode original : originals.values()) {
                ClassNode mapped = LoggedClassRemapper.remap(original, resolver, new TransformationLog());
                target.put(mapped.name + ".class", bytes(mapped));
            }
            FieldNode retained = node(target.get(ENTRY + ".class")).fields.stream()
                    .filter(field -> field.name.equals("retainedLegacyEntry")).findFirst().orElseThrow();
            assertEquals("L" + LEGACY_ENTRY + ";", retained.desc);
            TransformationException refused = assertThrows(TransformationException.class,
                    () -> new OfflineLinkageVerifier().verify(target, mappings.targetApi(), true));
            assertTrue(refused.getMessage().contains("MISSING_CLASS"), refused.getMessage());
            assertTrue(refused.getMessage().contains(LEGACY_ENTRY), refused.getMessage());
        });
    }

    @Test void genuineInferredListenerPreservesConsumerOnlyOverloadWithoutInventingClassArgument() throws Exception {
        Path fixture = Path.of(System.getProperty("legacybridge.fixtureRoot"),
                "lifecycle-inferred-1.16.5/build/libs/legacybridge-lifecycle-inferred-1.16.5-1.0.0.jar");
        Snapshot original = Snapshot.capture(fixture);
        try {
            Map<String, byte[]> before = entries(original.bytes());
            MethodNode originalCtor = method(node(before.get(ENTRY + ".class")), "<init>", "()V");
            assertConsumerOnlyListener(originalCtor);
            Path output = temporary.resolve("inferred-converted.jar");
            var converted = new LogicPreservingConverter(true).convert(fixture, output, inputs);
            assertTrue(converted.converted(), converted.report().toString());
            assertEquals("NOT_RUN", converted.report().get("runtimeValidation"));
            assertEquals(false, converted.report().get("bytecodeExecuted"));
            assertEquals("legacybridge_lifecycle", converted.report().get("modId"));
            assertEquals(0, converted.report().get("replacedClassCount"));
            assertEquals(false, ((Map<?, ?>) converted.report().get("bytecodeValidation")).get("modClassesLoaded"));
            Map<String, byte[]> after = entries(Files.readAllBytes(output));
            assertConsumerOnlyListener(method(node(after.get(ENTRY + ".class")), "<init>", "()V"));
            assertEquals(classEntries(before).keySet(), classEntries(after).keySet());
            for (var entry : before.entrySet()) {
                if (entry.getKey().startsWith("assets/") || entry.getKey().startsWith("data/"))
                    assertArrayEquals(entry.getValue(), after.get(entry.getKey()), entry.getKey());
            }
            assertFalse(after.keySet().stream().anyMatch(name -> name.endsWith("content.json")));
        } finally {
            original.assertUnchanged();
        }
    }

    private void assertRejected(MutatedJar jar, String... diagnosticFragments) throws Exception {
        Path outputDirectory = Files.createDirectory(temporary.resolve(jar.name() + "-output"));
        Path output = outputDirectory.resolve("converted.jar");
        var converted = new LogicPreservingConverter(true).convert(jar.input().path(), output, inputs);
        Map<String, Object> report = converted.report();
        assertFalse(converted.converted(), report.toString());
        assertEquals("ABORTED", report.get("status"), report.toString());
        assertEquals("UNSUPPORTED", report.get("compatibilityStatus"), report.toString());
        assertEquals(false, report.get("bytecodeExecuted"));
        assertEquals("NOT_RUN", report.get("runtimeValidation"));
        assertEquals(false, report.get("transformationsCommitted"));
        assertNull(report.get("output"));
        for (String fragment : diagnosticFragments)
            assertTrue(report.get("diagnostics").toString().contains(fragment), fragment + ": " + report);
        assertFalse(Files.exists(output), "A refused input must not publish a JAR");
        try (var children = Files.list(outputDirectory)) {
            assertEquals(0, children.count(), "A refusal must not leave partial output or temporary artifacts");
        }
        jar.input().assertUnchanged();
    }

    private void withMutation(String name, String owner, Consumer<ClassNode> mutation,
                              CheckedConsumer<MutatedJar> assertions) throws Exception {
        Path fixture = Path.of(System.getProperty("legacybridge.fixtureRoot"),
                "lifecycle-content-1.16.5/build/libs/legacybridge-lifecycle-1.16.5-1.0.0.jar");
        Snapshot original = Snapshot.capture(fixture);
        Snapshot input = null;
        try {
            Map<String, byte[]> before = entries(original.bytes());
            Map<String, byte[]> mutated = new TreeMap<>(before);
            String classEntry = owner + ".class";
            ClassNode edited = node(before.get(classEntry));
            mutation.accept(edited);
            byte[] modified = bytes(edited);
            assertFalse(Arrays.equals(before.get(classEntry), modified), "The requested mutation must actually change bytecode");
            BytecodeValidator.validate(modified); // Offline stack/structure check; no class resolution or execution.
            mutated.put(classEntry, modified);
            Path inputPath = temporary.resolve(name + "-input.jar");
            write(inputPath, mutated);
            input = Snapshot.capture(inputPath);
            Map<String, byte[]> reread = entries(input.bytes());
            assertEquals(before.keySet(), reread.keySet());
            for (var entry : before.entrySet()) {
                if (!entry.getKey().equals(classEntry)) assertArrayEquals(entry.getValue(), reread.get(entry.getKey()),
                        "Every other owned class, metadata and resource must remain identical: " + entry.getKey());
            }
            assertions.accept(new MutatedJar(name, input, reread));
        } finally {
            if (input != null) input.assertUnchanged();
            original.assertUnchanged();
        }
    }

    private static void assertConsumerOnlyListener(MethodNode constructor) {
        List<MethodInsnNode> listeners = code(constructor).stream().filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).filter(call -> call.owner.equals(BUS) && call.name.equals("addListener")).toList();
        assertEquals(1, listeners.size());
        assertEquals(Opcodes.INVOKEINTERFACE, listeners.get(0).getOpcode());
        assertTrue(listeners.get(0).itf);
        assertEquals("(Ljava/util/function/Consumer;)V", listeners.get(0).desc);
        assertTrue(code(constructor).stream().noneMatch(instruction -> instruction instanceof LdcInsnNode literal
                && literal.cst instanceof Type), "The inferred listener must not gain a Class literal");
    }

    private static ClassNode node(byte[] bytes) {
        assertNotNull(bytes, "Expected a real owned fixture class");
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] bytes(ClassNode node) {
        // Only straight-line, balanced mutations: existing debug/frames are retained.
        // COMPUTE_MAXS never asks a ClassLoader for a common superclass.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst().orElseThrow(() -> new AssertionError("Missing genuine fixture method " + node.name + "." + name + descriptor));
    }

    private static List<AbstractInsnNode> code(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }

    private static List<AnnotationNode> annotations(ClassNode node) {
        List<AnnotationNode> result = new ArrayList<>();
        if (node.visibleAnnotations != null) result.addAll(node.visibleAnnotations);
        if (node.invisibleAnnotations != null) result.addAll(node.invisibleAnnotations);
        return result;
    }

    private static Map<String, byte[]> classEntries(Map<String, byte[]> entries) {
        Map<String, byte[]> classes = new TreeMap<>();
        entries.forEach((name, bytes) -> { if (name.endsWith(".class")) classes.put(name, bytes); });
        return classes;
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
                    throw new IOException("Genuine fixture exceeds test archive bounds");
                if (entries.putIfAbsent(entry.getName(), content) != null) throw new IOException("Duplicate fixture entry");
            }
        }
        if (entries.isEmpty()) throw new IOException("Empty fixture archive");
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
            assertTrue(Files.size(path) <= MAX_ARCHIVE_BYTES, "Fixture archive limit");
            byte[] bytes = Files.readAllBytes(path);
            return new Snapshot(path, bytes, LifecycleNegativeJarIntegrationTest.sha256(bytes));
        }
        void assertUnchanged() throws Exception {
            byte[] after = Files.readAllBytes(path);
            assertArrayEquals(bytes, after, "Input bytes must remain unchanged: " + path);
            assertEquals(sha256, LifecycleNegativeJarIntegrationTest.sha256(after), "Input hash must remain unchanged: " + path);
        }
    }

    private record MutatedJar(String name, Snapshot input, Map<String, byte[]> entries) { }
    @FunctionalInterface private interface CheckedConsumer<T> { void accept(T value) throws Exception; }
}
