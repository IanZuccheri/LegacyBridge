package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.conversion.extract.SyntheticContentFixture;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.converter.metadata.ForgeMetadataAnalyzer;
import dev.legacybridge.converter.testutil.TestJarBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;
import dev.legacybridge.converter.transformation.TransformationPipeline;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.converter.operation.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class SimpleContentConverterTest {
    private static final String CLASS = "fixture/SimpleMod.class";
    private static final String PLAN = "META-INF/legacybridge/fixturemod/content.json";
    private static final String BOOTSTRAP = "dev/legacybridge/generated/fixturemod/ConvertedMod.class";
    private static final String METADATA = """
            modLoader="javafml"
            loaderVersion="[36,)"
            license="MIT"
            [[mods]]
            modId="fixturemod"
            version="1.2.3"
            displayName="Inert Synthetic Content"
            description="A test fixture"
            [[dependencies.fixturemod]]
            modId="forge"
            mandatory=true
            versionRange="[36.2.42,37)"
            ordering="NONE"
            side="BOTH"
            [[dependencies.fixturemod]]
            modId="minecraft"
            mandatory=true
            versionRange="[1.16.5,1.17)"
            ordering="NONE"
            side="BOTH"
            """;
    @TempDir Path temporary;

    @Test void convertsToMinimalNativeBootstrapAndPreservesEveryContentResourceByte() throws Exception {
        Map<String, byte[]> source = resources("valid");
        Path input = write("input.jar", source), output = temporary.resolve("converted.jar");
        byte[] original = Files.readAllBytes(input);
        ConversionResult result = convert(input, output, source);
        assertTrue(result.converted(), result.report().toString());
        assertEquals("CONVERTED", result.report().get("status"));
        assertEquals(false, result.report().get("bytecodeExecuted"));
        assertEquals(true, result.report().get("bytecodeModified"));
        assertEquals(1, result.report().get("replacedClassCount"));
        assertEquals(2, result.report().get("itemCount"));
        assertEquals(1, result.report().get("blockCount"));
        assertEquals(1, result.report().get("soundCount"));
        Map<String, byte[]> target = read(output);
        assertFalse(target.containsKey(CLASS));
        assertEquals(List.of(BOOTSTRAP), target.keySet().stream().filter(key -> key.endsWith(".class")).toList());
        for (var entry : source.entrySet()) {
            if (entry.getKey().startsWith("assets/") || entry.getKey().startsWith("data/")) {
                assertArrayEquals(entry.getValue(), target.get(entry.getKey()), entry.getKey());
            }
        }
        Map<?, ?> plan = json(target.get(PLAN));
        assertEquals("fixturemod", plan.get("modId"));
        assertEquals("1.2.3", plan.get("modVersion"));
        assertEquals(2, ((List<?>) plan.get("items")).size());
        Map<?, ?> item = ((List<?>) plan.get("items")).stream().map(value -> (Map<?, ?>) value)
                .filter(value -> value.get("name").equals("ingot")).findFirst().orElseThrow();
        assertEquals("MISC", ((Map<?, ?>) item.get("properties")).get("creativeTab"));
        assertEquals(32, ((Number) ((Map<?, ?>) item.get("properties")).get("stackSize")).intValue());
        assertEquals(15, ((Number) ((Map<?, ?>) json(target.get("pack.mcmeta")).get("pack")).get("pack_format")).intValue());
        var metadata = new ForgeMetadataAnalyzer().analyze(target.get("META-INF/mods.toml"));
        assertTrue(metadata.valid());
        assertEquals("fixturemod", metadata.summary().mods().get(0).modId());
        assertEquals("1.2.3", metadata.summary().mods().get(0).version());
        assertTrue(metadata.summary().dependencies().stream().anyMatch(value -> value.modId().equals("legacybridge_runtime") && value.mandatory()));
        assertBootstrap(target.get(BOOTSTRAP), "fixturemod");
        assertArrayEquals(original, Files.readAllBytes(input));
    }

    @Test void repeatedConversionProducesIdenticalJarBytes() throws Exception {
        Map<String, byte[]> source = resources("valid"); Path input = write("input.jar", source);
        Path one = temporary.resolve("one.jar"), two = temporary.resolve("two.jar");
        assertTrue(convert(input, one, source).converted());
        assertTrue(convert(input, two, source).converted());
        assertArrayEquals(Files.readAllBytes(one), Files.readAllBytes(two));
        assertEquals(List.of("input.jar", "one.jar", "two.jar"), fileNames());
    }

    @Test void prepareIsNonPublishingAndPublishUsesExactlyTheExistingConversionRecipe() throws Exception {
        var source = resources("valid");
        var input = write("input.jar", source);
        var first = temporary.resolve("first.jar");
        var mappings = SyntheticContentFixture.mappingInputs(source.get(CLASS));
        var converter = new SimpleContentConverter();
        try (var prepared = converter.prepare(input, first, mappings)) {
            assertTrue(prepared.convertible());
            assertEquals("PLANNED", prepared.report().get("status"));
            assertEquals("LEGACY_CONVERSION_PLAN", prepared.report().get("reportType"));
            assertEquals(1, prepared.report().get("schemaVersion"));
            assertEquals(false, prepared.report().get("bytecodeModified"));
            assertEquals(false, prepared.report().get("transformationsCommitted"));
            assertNull(prepared.report().get("output"));
            assertNotNull(prepared.candidateSha256());
            assertEquals(List.of("input.jar"), fileNames());
            var result = converter.publish(prepared);
            assertTrue(result.converted());
            assertEquals("LEGACY_CONVERSION", result.report().get("reportType"));
            assertEquals(2, result.report().get("schemaVersion"));
            assertEquals(prepared.candidateSha256(), ((Map<?,?>)result.report().get("output")).get("sha256"));
            assertFalse(result.report().containsKey("candidate"));
            assertThrows(IllegalStateException.class, () -> converter.publish(prepared));
            var second = temporary.resolve("second.jar");
            var direct = converter.convert(input, second, mappings);
            assertTrue(direct.converted());
            assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
            var comparable = new TreeMap<>(result.report());
            comparable.put("output", direct.report().get("output"));
            assertEquals(comparable, direct.report());
        }
    }

    @Test void closingPreparedCandidateNeverPublishesIt() throws Exception {
        var source = resources("valid"); var input = write("input.jar", source);
        var converter = new SimpleContentConverter();
        var prepared = converter.prepare(input, temporary.resolve("out.jar"), SyntheticContentFixture.mappingInputs(source.get(CLASS)));
        prepared.close();
        assertFalse(prepared.convertible());
        assertThrows(IllegalStateException.class, () -> converter.publish(prepared));
        assertEquals(List.of("input.jar"), fileNames());
    }

    @Test void refusedPreparationReturnsTheExistingRefusalAndNoCandidate() throws Exception {
        var source = resources("reflection"); var input = write("input.jar", source);
        var mappings = SyntheticContentFixture.mappingInputs(source.get(CLASS));
        var converter = new SimpleContentConverter();
        try (var prepared = converter.prepare(input, temporary.resolve("planned.jar"), mappings)) {
            assertFalse(prepared.convertible());
            assertEquals("ABORTED", prepared.report().get("status"));
            assertNull(prepared.candidateSha256());
            var expected = new TreeMap<>(prepared.report());
            expected.put("reportType", "LEGACY_CONVERSION"); expected.put("schemaVersion", 2);
            assertEquals(expected, converter.convert(input, temporary.resolve("converted.jar"), mappings).report());
        }
        assertEquals(List.of("input.jar"), fileNames());
    }

    @Test void preparedSourceIsRehashedBeforePublication() throws Exception {
        var source = resources("valid"); var input = write("input.jar", source);
        var converter = new SimpleContentConverter();
        try (var prepared = converter.prepare(input, temporary.resolve("out.jar"), SyntheticContentFixture.mappingInputs(source.get(CLASS)))) {
            source.put("marker.txt", utf8("changed since preparation"));
            write("input.jar", source);
            var result = converter.publish(prepared);
            assertFalse(result.converted());
            assertEquals("ABORTED", result.report().get("status"));
            assertEquals(false, result.report().get("transformationsCommitted"));
            assertTrue(result.report().get("diagnostics").toString().contains("Input changed"));
            assertEquals(List.of("input.jar"), fileNames());
        }
    }

    @Test void cancellationAtEveryObservedPreparationStagePublishesNothing() throws Exception {
        var source = resources("valid"); var input = write("input.jar", source);
        var mappings = SyntheticContentFixture.mappingInputs(source.get(CLASS));
        for (var stage : List.of(OperationStage.INPUT_SNAPSHOT, OperationStage.ARCHIVE_SCAN,
                OperationStage.ANALYSIS, OperationStage.SEMANTIC_VALIDATION, OperationStage.CLASS_REMAP,
                OperationStage.API_TRANSFORMATION, OperationStage.DESCRIPTOR_TRANSFORMATION,
                OperationStage.ADAPTER_TRANSFORMATION, OperationStage.BYTECODE_VALIDATION,
                OperationStage.RESOURCE_VALIDATION, OperationStage.SERIALIZATION,
                OperationStage.PREPARED, OperationStage.PRE_PUBLICATION)) {
            var holder = new AtomicReference<OperationControl>();
            var control = new OperationControl(OperationLimits.defaults(), event -> {
                if (event.stage() == stage) holder.get().requestCancel();
            });
            holder.set(control);
            try (var scope = control.enter()) {
                var error = assertThrows(OperationStoppedException.class, () -> new SimpleContentConverter().convert(
                        input, temporary.resolve("out.jar"), mappings), stage.name());
                assertEquals(OperationStoppedException.Code.CANCELLED, error.code());
            }
            assertEquals(List.of("input.jar"), fileNames(), stage.name());
        }
    }

    @Test void cancellationAfterPublicationBarrierIsRefusedAndSuccessfulResultIsPreserved() throws Exception {
        var source = resources("valid"); var input = write("input.jar", source);
        var mappings = SyntheticContentFixture.mappingInputs(source.get(CLASS));
        var holder = new AtomicReference<OperationControl>();
        var attempted = new AtomicBoolean();
        var accepted = new AtomicBoolean(true);
        var control = new OperationControl(OperationLimits.defaults(), event -> {
            if (event.stage() == OperationStage.PUBLICATION) {
                attempted.set(true); accepted.set(holder.get().requestCancel());
                throw new IllegalStateException("Observer must not invalidate committed conversion");
            }
        });
        holder.set(control);
        try (var scope = control.enter()) {
            var result = new SimpleContentConverter().convert(input, temporary.resolve("out.jar"), mappings);
            assertTrue(result.converted());
            assertTrue(attempted.get());
            assertFalse(accepted.get());
            assertEquals(true, result.report().get("transformationsCommitted"));
        }
        assertEquals(List.of("input.jar", "out.jar"), fileNames());
    }

    @Test void scopedConversionHasIdenticalBytesAndReportToDirectConversion() throws Exception {
        var source = resources("valid"); var input = write("input.jar", source);
        var mappings = SyntheticContentFixture.mappingInputs(source.get(CLASS));
        var direct = new SimpleContentConverter().convert(input, temporary.resolve("direct.jar"), mappings);
        var stages = new ArrayList<OperationStage>();
        var control = new OperationControl(OperationLimits.defaults(), event -> stages.add(event.stage()));
        try (var scope = control.enter()) {
            var scoped = new SimpleContentConverter().convert(input, temporary.resolve("scoped.jar"), mappings);
            assertTrue(scoped.converted());
            var expected = new TreeMap<>(direct.report()); expected.put("output", scoped.report().get("output"));
            assertEquals(expected, scoped.report());
        }
        assertArrayEquals(Files.readAllBytes(temporary.resolve("direct.jar")), Files.readAllBytes(temporary.resolve("scoped.jar")));
        assertTrue(stages.containsAll(List.of(OperationStage.ARCHIVE_SCAN, OperationStage.SERIALIZATION,
                OperationStage.PRE_PUBLICATION, OperationStage.PUBLICATION)));
    }

    @Test void pipelineAndEveryCommittedChangeAreExplicitlyJournaled() throws Exception {
        var source = resources("valid");
        var input = write("journal-source.jar", source);
        var result = convert(input, temporary.resolve("journal-target.jar"), source);
        assertTrue(result.converted(), result.report().toString());
        assertEquals(TransformationPipeline.STAGES, result.report().get("pipeline"));
        assertEquals(true, result.report().get("transformationsCommitted"));
        Map<?, ?> validation = (Map<?, ?>) result.report().get("bytecodeValidation");
        assertEquals("PASSED", validation.get("status"));
        assertEquals(false, validation.get("modClassesLoaded"));
        assertEquals(false, validation.get("jvmLinkageVerified"));
        Map<?, ?> journal = (Map<?, ?>) result.report().get("transformationLog");
        List<?> entries = (List<?>) journal.get("entries");
        assertFalse(entries.isEmpty());
        assertEquals(entries.size(), journal.get("entryCount"));
        int sequence = 0, resources = 0;
        for (Object value : entries) {
            Map<?, ?> change = (Map<?, ?>) value;
            assertEquals(++sequence, change.get("sequence"));
            assertEquals("VERIFIED", change.get("confidence"));
            for (String key : List.of("before", "after", "rule", "reason", "location"))
                assertFalse(((String) change.get(key)).isBlank(), key);
            assertNotEquals(change.get("before"), change.get("after"));
            Map<?, ?> adapter = (Map<?, ?>) change.get("adapter");
            assertEquals(AdapterCatalog.require((String) adapter.get("id")).referenceTree(), adapter);
            if (change.get("kind").equals("RESOURCE_REWRITE")) resources++;
        }
        assertEquals(6, resources, "Every rewritten/generated non-class entry has exactly one resource log");
        var again = convert(input, temporary.resolve("journal-repeat.jar"), source);
        assertEquals(journal, again.report().get("transformationLog"));
    }

    @Test void preservesSourceDebugAndConstructorLineAndThisLocalWhenLowering() throws Exception {
        var source = resources("valid");
        ClassNode original = node(source.get(CLASS));
        original.sourceFile = "SimpleMod.java";
        original.sourceDebug = "SMAP\nSimpleMod.java\nJava\n*S Java\n*F\n1 SimpleMod.java\n*L\n42#1:42\n*E\n";
        MethodNode constructor = original.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        LabelNode start = new LabelNode(), end = new LabelNode();
        constructor.instructions.insert(start);
        constructor.instructions.insert(start, new LineNumberNode(42, start));
        constructor.instructions.add(end);
        constructor.localVariables.add(new LocalVariableNode("this", "Lfixture/SimpleMod;", null, start, end, 0));
        source.put(CLASS, bytes(original));
        Path input = write("debug-source.jar", source), output = temporary.resolve("debug-target.jar");
        var result = convert(input, output, source);
        assertTrue(result.converted(), result.report().toString());
        ClassNode target = node(read(output).get(BOOTSTRAP));
        assertEquals(original.sourceFile, target.sourceFile);
        assertEquals(original.sourceDebug, target.sourceDebug);
        assertTrue(Arrays.stream(target.methods.get(0).instructions.toArray()).anyMatch(i -> i instanceof LineNumberNode line && line.line == 42));
        assertEquals("L" + target.name + ";", target.methods.get(0).localVariables.get(0).desc);
        assertTrue(result.report().get("transformationLog").toString().contains("fixture/SimpleMod.java"));
    }

    @Test void invalidSourceReturnCannotBeLaunderedIntoAValidBootstrap() throws Exception {
        var source = resources("valid");
        ClassNode original = node(source.get(CLASS));
        var constructor = original.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        var last = Arrays.stream(constructor.instructions.toArray()).filter(i -> i.getOpcode() == Opcodes.RETURN).findFirst().orElseThrow();
        constructor.instructions.set(last, new InsnNode(Opcodes.IRETURN));
        source.put(CLASS, bytes(original));
        assertAborted(source, "Invalid bytecode");
    }

    @Test void duplicateSourceMembersAreRejectedBeforeWritingAnything() throws Exception {
        var source = resources("valid");
        ClassNode original = node(source.get(CLASS));
        var first = original.fields.get(0);
        original.fields.add(new FieldNode(first.access, first.name, first.desc, first.signature, first.value));
        source.put(CLASS, bytes(original));
        assertAborted(source, "Duplicate field");
    }

    @Test void bundledPlatformDefinitionAbortsEvenWhenTheModItselfIsValid() throws Exception {
        var source = resources("valid");
        ClassNode platform = new ClassNode();
        platform.version = Opcodes.V1_8; platform.access = Opcodes.ACC_PUBLIC;
        platform.name = "net/minecraft/Injected"; platform.superName = "java/lang/Object";
        source.put(platform.name + ".class", bytes(platform));
        assertAborted(source, null);
        source.remove(platform.name + ".class");
        source.put("fixture/Disguised.class", bytes(platform));
        assertAborted(source, null);
    }

    private static ClassNode node(byte[] bytes) { var node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static byte[] bytes(ClassNode node) { var writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray(); }

    @Test void unsupportedBytecodeAbortsWithoutCreatingOutputOrChangingInput() throws Exception {
        assertAborted(resources("reflection"), "Unsupported API/type");
    }

    @Test void unknownResourceAndCustomShadersAbort() throws Exception {
        var unknown = resources("valid"); unknown.put("META-INF/unrecognized.json", utf8("{}"));
        assertAborted(unknown, null);
        var shaders = resources("valid"); shaders.put("assets/fixturemod/shaders/core/custom.json", utf8("{}"));
        assertAborted(shaders, null);
    }

    @Test void unknownMetadataRootAndModKeysAbort() throws Exception {
        var root = resources("valid"); root.put("META-INF/mods.toml", utf8("unrecognizedBehavior=true\n" + METADATA));
        assertAborted(root, "Unapproved mods.toml root field");
        var mod = resources("valid"); mod.put("META-INF/mods.toml", utf8(METADATA.replace("description=\"A test fixture\"", "description=\"A test fixture\"\ncustomEntrypoint=\"example.Main\"")));
        assertAborted(mod, "Unapproved mods.toml mod field");
    }

    @Test void unknownDependencyMetadataIsNotSilentlyDiscarded() throws Exception {
        var source = resources("valid"); source.put("META-INF/mods.toml", utf8(METADATA + "customBehavior=true\n"));
        assertAborted(source, null);
    }

    @Test void manifestHeaderInjectionThroughVersionIsRejected() throws Exception {
        var source = resources("valid");
        source.put("META-INF/mods.toml", utf8(METADATA.replace("version=\"1.2.3\"", "version=\"1.2.3\\r\\nFMLCorePlugin: example.Core\"")));
        assertAborted(source, null);
    }

    @Test void thirdPartyDependenciesAbortEvenWhenOptional() throws Exception {
        var source = resources("valid"); source.put("META-INF/mods.toml", utf8(METADATA + """
                [[dependencies.fixturemod]]
                modId="example_library"
                mandatory=false
                versionRange="[1,)"
                ordering="NONE"
                side="BOTH"
                """));
        assertAborted(source, "Third-party dependency");
    }

    @Test void manifestCoremodDeclarationsAbort() throws Exception {
        var source = resources("valid");
        source.put("META-INF/MANIFEST.MF", utf8("Manifest-Version: 1.0\r\nFMLCorePlugin: example.Core\r\n\r\n"));
        assertAborted(source, "Unapproved manifest attribute");
    }

    @Test void existingOutputAndInputPathAliasAreNeverOverwritten() throws Exception {
        var source = resources("valid"); Path input = write("input.jar", source), output = temporary.resolve("existing.jar");
        byte[] sentinel = utf8("user-owned existing data"); Files.write(output, sentinel);
        assertThrows(IOException.class, () -> convert(input, output, source));
        assertArrayEquals(sentinel, Files.readAllBytes(output));
        byte[] original = Files.readAllBytes(input);
        assertThrows(IOException.class, () -> convert(input, input, source));
        assertArrayEquals(original, Files.readAllBytes(input));
    }

    @Test void hardLinkInputAliasIsNeverOverwritten() throws Exception {
        var source = resources("valid"); Path input = write("input.jar", source), alias = temporary.resolve("alias.jar");
        Files.createLink(alias, input);
        byte[] original = Files.readAllBytes(input);
        assertThrows(IOException.class, () -> convert(input, alias, source));
        assertArrayEquals(original, Files.readAllBytes(input));
        assertArrayEquals(original, Files.readAllBytes(alias));
    }

    @Test void incompatiblePackFormatAborts() throws Exception {
        var source = resources("valid"); source.put("pack.mcmeta", utf8("{\"pack\":{\"pack_format\":15,\"description\":\"Already modern\"}}"));
        assertAborted(source, "pack_format");
    }

    @Test
    @EnabledIfSystemProperty(named = "legacybridge.verifyMappingEnvironment", matches = "true")
    void convertsActualReobfuscatedForge36FixtureUsingRealMappingInputs() throws Exception {
        Path fixtures = Path.of(System.getProperty("legacybridge.fixtureRoot"));
        Path input = fixtures.resolve("simple-content-1.16.5/build/libs/legacybridge-example-1.16.5-1.0.0.jar");
        assertTrue(Files.isRegularFile(input), "Build the source Forge fixture before environment verification");
        MappingInputs mappings = new MappingInputsLoader().load(null);
        assertTrue(mappings.complete(), mappings.diagnostics().toString());
        byte[] original = Files.readAllBytes(input);
        Path output = temporary.resolve("real-converted.jar");
        ConversionResult result = new SimpleContentConverter().convert(input, output, mappings);
        assertTrue(result.converted(), result.report().toString());
        assertEquals("legacybridge_example", result.report().get("modId"));
        assertEquals(2, result.report().get("itemCount"));
        assertEquals(1, result.report().get("blockCount"));
        var sourceEntries = read(input); var targetEntries = read(output);
        for (var entry : sourceEntries.entrySet()) if (entry.getKey().startsWith("assets/") || entry.getKey().startsWith("data/"))
            assertArrayEquals(entry.getValue(), targetEntries.get(entry.getKey()), entry.getKey());
        assertArrayEquals(original, Files.readAllBytes(input));
        assertBootstrap(targetEntries.get("dev/legacybridge/generated/legacybridge_example/ConvertedMod.class"), "legacybridge_example");
    }

    private void assertAborted(Map<String, byte[]> source, String fragment) throws Exception {
        String id = "refused-" + fileNames().size();
        Path input = write(id + ".jar", source), output = temporary.resolve(id + "-converted.jar");
        byte[] original = Files.readAllBytes(input);
        ConversionResult result = convert(input, output, source);
        assertFalse(result.converted(), result.report().toString());
        assertEquals("ABORTED", result.report().get("status"));
        assertEquals(false, result.report().get("bytecodeModified"));
        assertEquals(false, result.report().get("transformationsCommitted"));
        assertNull(result.report().get("output"));
        if (fragment != null) assertTrue(result.report().get("diagnostics").toString().contains(fragment), result.report().toString());
        assertFalse(Files.exists(output));
        assertArrayEquals(original, Files.readAllBytes(input));
        assertTrue(fileNames().stream().noneMatch(name -> name.startsWith(".legacybridge-conversion-")));
    }
    private ConversionResult convert(Path input, Path output, Map<String, byte[]> source) throws Exception {
        return new SimpleContentConverter().convert(input, output, SyntheticContentFixture.mappingInputs(source.get(CLASS)));
    }
    private Path write(String name, Map<String, byte[]> source) throws IOException { return TestJarBuilder.write(temporary.resolve(name), source); }
    private List<String> fileNames() throws IOException { try (var files = Files.list(temporary)) { return files.map(path -> path.getFileName().toString()).sorted().toList(); } }
    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static Map<?, ?> json(byte[] bytes) throws Exception { assertNotNull(bytes); return (Map<?, ?>) BoundedJsonParser.parse(new String(bytes, StandardCharsets.UTF_8)); }
    private static Map<String, byte[]> read(Path path) throws IOException {
        Map<String, byte[]> result = new TreeMap<>();
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var entries = zip.entries(); while (entries.hasMoreElements()) { var entry = entries.nextElement(); if (!entry.isDirectory()) try (var stream = zip.getInputStream(entry)) { result.put(entry.getName(), stream.readAllBytes()); } }
        }
        return result;
    }
    private static void assertBootstrap(byte[] bytecode, String modId) {
        assertNotNull(bytecode);
        List<String> annotations = new ArrayList<>(), calls = new ArrayList<>();
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public void visit(int version, int access, String name, String signature, String superclass, String[] interfaces) { assertEquals(Opcodes.V17, version); assertEquals("java/lang/Object", superclass); }
            @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                assertEquals("Lnet/minecraftforge/fml/common/Mod;", descriptor); assertTrue(visible);
                return new AnnotationVisitor(Opcodes.ASM9) { @Override public void visit(String key, Object value) { assertEquals("value", key); annotations.add(value.toString()); } };
            }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                assertEquals("<init>", name); assertEquals("()V", descriptor);
                return new MethodVisitor(Opcodes.ASM9) { @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) { calls.add(owner + "." + name + descriptor); } };
            }
        }, 0);
        assertEquals(List.of(modId), annotations);
        assertEquals(List.of("java/lang/Object.<init>()V", "java/lang/Class.getClassLoader()Ljava/lang/ClassLoader;",
                "dev/legacybridge/runtime/compat/registry/SimpleContentAdapter.register(Ljava/lang/String;Ljava/lang/ClassLoader;Ljava/lang/String;)V"), calls);
    }
    private static Map<String, byte[]> resources(String shape) {
        Map<String, byte[]> source = new TreeMap<>(); source.put(CLASS, SyntheticContentFixture.bytecode(shape));
        source.put("META-INF/mods.toml", utf8(METADATA));
        source.put("META-INF/MANIFEST.MF", utf8("Manifest-Version: 1.0\r\nImplementation-Version: 1.2.3\r\n\r\n"));
        source.put("pack.mcmeta", utf8("{\"pack\":{\"pack_format\":6,\"description\":\"Synthetic content fixture\"}}"));
        source.put("assets/fixturemod/models/item/ingot.json", utf8("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/iron_ingot\"}}"));
        source.put("assets/fixturemod/models/item/block.json", utf8("{\"parent\":\"fixturemod:block/block\"}"));
        source.put("assets/fixturemod/models/block/block.json", utf8("{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"minecraft:block/stone\"}}"));
        source.put("assets/fixturemod/blockstates/block.json", utf8("{\"variants\":{\"\":{\"model\":\"fixturemod:block/block\"}}}"));
        source.put("assets/fixturemod/lang/en_us.json", utf8("{\"item.fixturemod.ingot\":\"Fixture Ingot\",\"block.fixturemod.block\":\"Fixture Block\"}"));
        source.put("assets/fixturemod/sounds.json", utf8("{\"ping\":{\"sounds\":[{\"name\":\"minecraft:block.note_block.pling\",\"type\":\"event\"}]}}"));
        source.put("data/fixturemod/recipes/ingot.json", utf8("{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"II\",\"II\"],\"key\":{\"I\":{\"item\":\"minecraft:iron_ingot\"}},\"result\":{\"item\":\"fixturemod:ingot\",\"count\":1}}"));
        source.put("data/fixturemod/tags/items/ingots.json", utf8("{\"replace\":false,\"values\":[\"fixturemod:ingot\"]}"));
        return source;
    }
}
