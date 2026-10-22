package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.AdapterDescriptor;
import dev.legacybridge.runtime.compat.AdapterSelection;
import dev.legacybridge.runtime.compat.UnsupportedCompatibilityException;
import dev.legacybridge.runtime.compat.world.BlockMiningAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Reads compiled adapters as data. Forge/Minecraft classes are never loaded in converter tests. */
class AdapterContractIntegrationTest {
    @TempDir Path temporary;

    @Test void everyCatalogEntrypointExistsAsConcretePublicStaticCodeInTheFinalRuntimeJar() throws Exception {
        try (ZipFile runtime = runtimeJar()) {
            for (AdapterDescriptor adapter : AdapterCatalog.all()) {
                byte[] bytecode = entry(runtime, adapter.owner() + ".class");
                List<Integer> instructions = new ArrayList<>();
                int[] found = {0}, calls = {0};
                new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public void visit(int version, int access, String name, String signature, String superclass, String[] interfaces) {
                        assertEquals(adapter.owner(), name);
                        assertNotEquals(0, access & Opcodes.ACC_PUBLIC, adapter.id());
                    }
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        if (!name.equals(adapter.method()) || !descriptor.equals(adapter.descriptor())) return null;
                        found[0]++;
                        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC), adapter.id());
                        assertEquals(0, access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT), adapter.id());
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitInsn(int opcode) { instructions.add(opcode); }
                            @Override public void visitIntInsn(int opcode, int operand) { instructions.add(opcode); }
                            @Override public void visitVarInsn(int opcode, int variable) { instructions.add(opcode); }
                            @Override public void visitTypeInsn(int opcode, String type) { instructions.add(opcode); }
                            @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) { instructions.add(opcode); }
                            @Override public void visitJumpInsn(int opcode, Label target) { instructions.add(opcode); }
                            @Override public void visitLdcInsn(Object value) { instructions.add(Opcodes.LDC); }
                            @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) { instructions.add(opcode); calls[0]++; }
                            @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) { instructions.add(Opcodes.INVOKEDYNAMIC); calls[0]++; }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                assertEquals(1, found[0], adapter.id() + " exact descriptor absent or duplicated");
                assertTrue(instructions.size() > 3, adapter.id() + " cannot be a bare void/null/default-value stub");
                assertTrue(calls[0] > 0, adapter.id() + " must perform implemented validation/adaptation, not return a constant");
            }
        }
    }

    @Test void packagedCatalogMatchesEveryDescriptorAndDocumentsOnlyImplementedAreas() throws Exception {
        try (ZipFile runtime = runtimeJar()) {
            Map<?, ?> catalog = json(entry(runtime, "META-INF/legacybridge/adapters.json"));
            assertEquals(AdapterCatalog.SCHEMA_VERSION, ((Number) catalog.get("schemaVersion")).intValue());
            assertEquals(AdapterCatalog.RUNTIME_VERSION, catalog.get("runtimeVersion"));
            assertEquals(AdapterCatalog.unsupportedAreas(), catalog.get("unsupportedAreas"));
            List<?> entries = (List<?>) catalog.get("adapters");
            assertEquals(AdapterCatalog.all().size(), entries.size());
            Set<String> seen = new HashSet<>();
            for (Object raw : entries) {
                Map<?, ?> descriptor = (Map<?, ?>) raw;
                String id = (String) descriptor.get("id");
                assertTrue(seen.add(id));
                AdapterDescriptor expected = AdapterCatalog.require(id);
                assertEquivalent(expected.documentationTree(), descriptor);
                for (String field : List.of("legacyApi", "targetApi", "transformation", "behaviorDifferences", "knownLimitations")) {
                    assertInstanceOf(String.class, descriptor.get(field));
                    assertFalse(((String) descriptor.get(field)).isBlank(), id + ": " + field);
                }
                assertFalse(((List<?>) descriptor.get("fixtures")).isEmpty(), id);
            }
            var files = runtime.stream().map(entry -> entry.getName()).toList();
            for (String area : AdapterCatalog.unsupportedAreas()) {
                String prefix = "dev/legacybridge/runtime/compat/" + area + "/";
                assertTrue(files.stream().noneMatch(name -> name.startsWith(prefix) && name.endsWith(".class")
                        && !name.endsWith("/package-info.class")), "Unsupported area contains a loader-satisfying dummy class: " + area);
                assertTrue(AdapterCatalog.all().stream().noneMatch(adapter -> adapter.owner().startsWith(prefix)), area);
            }
            assertTrue(files.stream().noneMatch(name -> name.startsWith("net/minecraft/") || name.startsWith("net/minecraftforge/")
                    || name.startsWith("cpw/mods/")), "Runtime must not shadow platform classes");
        }
    }

    @Test void sharedConverterContractDoesNotIntroduceForgeOrMinecraftClassesOntoItsClasspath() throws Exception {
        ClassLoader loader = AdapterCatalog.class.getClassLoader();
        for (String forbidden : List.of("net/minecraft/world/item/Item.class", "net/minecraft/client/Minecraft.class",
                "net/minecraftforge/fml/common/Mod.class", "net/minecraftforge/registries/DeferredRegister.class")) {
            assertNull(loader.getResource(forbidden), "Game implementation unexpectedly present on converter classpath: " + forbidden);
        }
        Path contractLocation = Path.of(AdapterCatalog.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isRegularFile(contractLocation)) {
            try (ZipFile contract = new ZipFile(contractLocation.toFile())) {
                assertTrue(contract.stream().map(entry -> entry.getName()).filter(name -> name.endsWith(".class"))
                        .allMatch(name -> name.startsWith("dev/legacybridge/runtime/compat/")));
                assertNull(contract.getEntry("dev/legacybridge/runtime/SimpleContentPlan.class"));
                assertNull(contract.getEntry("dev/legacybridge/runtime/compat/registry/SimpleContentAdapter.class"));
            }
        }
    }

    @Test void planSelectionAndTraceCoverExactlyTheRequiredConcreteAdapters() {
        ContentPlan plan = plan("STONE", "MISC");
        TransformationBindings bindings = TransformationBindings.forPlan(plan);
        Set<String> required = Set.copyOf(AdapterSelection.required(true, true, true, false, true));
        assertEquals(required, ids(bindings.bindingTree()));
        Set<String> traceIds = new TreeSet<>();
        for (Map<String, Object> trace : bindings.report()) {
            Map<?, ?> reference = (Map<?, ?>) trace.get("adapter");
            String id = (String) reference.get("id");
            assertTrue(traceIds.add(id));
            AdapterDescriptor descriptor = bindings.require(id);
            assertEquivalent(descriptor.referenceTree(), reference);
            assertEquals(descriptor.transformation(), trace.get("operation"));
            assertEquals(descriptor.phase(), trace.get("phase"));
            assertEquals(descriptor.legacyApi(), trace.get("legacyApi"));
            assertEquals(descriptor.targetApi(), trace.get("targetApi"));
        }
        assertEquals(required, traceIds);
        assertTrue(required.containsAll(Set.of(AdapterCatalog.BOOTSTRAP, AdapterCatalog.METADATA,
                AdapterCatalog.MANIFEST, AdapterCatalog.PACK, AdapterCatalog.MINING)));
    }

    @Test void unknownOrOmittedAdaptersFailInsteadOfInventingAnImplementation() {
        assertThrows(UnsupportedCompatibilityException.class, () -> AdapterCatalog.require("networking.guessed.v1"));
        assertTrue(AdapterCatalog.find("networking.guessed.v1").isEmpty());
        TransformationBindings bindings = TransformationBindings.forPlan(plan("STONE", "MISC"));
        assertThrows(UnsupportedCompatibilityException.class, () -> bindings.require(AdapterCatalog.SOUNDS));
        assertThrows(UnsupportedCompatibilityException.class, () -> bindings.require("networking.guessed.v1"));
    }

    @Test void unprovenMaterialAndCreativeTabSemanticsAreUnsupported() {
        var material = assertThrows(UnsupportedCompatibilityException.class,
                () -> TransformationBindings.forPlan(plan("WOOD", "MISC")));
        assertTrue(material.getMessage().startsWith("UNSUPPORTED:"));
        var tab = assertThrows(UnsupportedCompatibilityException.class,
                () -> TransformationBindings.forPlan(plan("STONE", "COMBAT")));
        assertTrue(tab.getMessage().startsWith("UNSUPPORTED:"));
    }

    @Test
    @EnabledIfSystemProperty(named = "legacybridge.verifyMappingEnvironment", matches = "true")
    void actualConvertedFixtureCarriesExactBindingsMiningSemanticsAndMatchingBootstrap() throws Exception {
        Path source = Path.of(System.getProperty("legacybridge.fixtureRoot"))
                .resolve("simple-content-1.16.5/build/libs/legacybridge-example-1.16.5-1.0.0.jar");
        assertTrue(Files.isRegularFile(source), "Build the real Forge 36 fixture first");
        Path output = temporary.resolve("adapter-backed-fixture.jar");
        byte[] original = Files.readAllBytes(source);
        ConversionResult result = new SimpleContentConverter().convert(source, output, new MappingInputsLoader().load(null));
        assertTrue(result.converted(), result.report().toString());
        assertArrayEquals(original, Files.readAllBytes(source));
        assertEquals(AdapterCatalog.RUNTIME_VERSION, result.report().get("runtimeVersion"));
        try (ZipFile converted = new ZipFile(output.toFile())) {
            Map<?, ?> declared = json(entry(converted, "META-INF/legacybridge/legacybridge_example/adapters.json"));
            assertEquivalent((Map<?, ?>) result.report().get("adapterBindings"), declared);
            Set<String> required = Set.copyOf(AdapterSelection.required(true, true, true, false, true));
            assertEquals(required, ids(declared));
            Set<String> operations = new HashSet<>();
            for (Object value : (List<?>) result.report().get("transformations")) {
                Map<?, ?> trace = (Map<?, ?>) value;
                Map<?, ?> reference = (Map<?, ?>) trace.get("adapter");
                String id = (String) reference.get("id");
                assertTrue(operations.add(id));
                assertEquivalent(AdapterCatalog.require(id).referenceTree(), reference);
                assertEquals(AdapterCatalog.require(id).transformation(), trace.get("operation"));
            }
            assertEquals(required, operations);
            Map<?, ?> mining = json(entry(converted, BlockMiningAdapter.TARGET_PATH));
            assertEquals(false, mining.get("replace"));
            assertEquals(List.of("legacybridge_example:bridge_block"), mining.get("values"));
            List<String> staticCalls = new ArrayList<>();
            new ClassReader(entry(converted, "dev/legacybridge/generated/legacybridge_example/ConvertedMod.class"))
                    .accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                                    if (opcode == Opcodes.INVOKESTATIC) staticCalls.add(owner + "." + method + descriptor);
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            AdapterDescriptor bootstrap = AdapterCatalog.require(AdapterCatalog.BOOTSTRAP);
            assertEquals(List.of(bootstrap.owner() + "." + bootstrap.method() + bootstrap.descriptor()), staticCalls);
        }
    }

    private static ContentPlan plan(String material, String tab) {
        var properties = new ContentPlan.ItemProperties(32, 0, false, "COMMON", tab);
        return new ContentPlan("contract_fixture", "1.0.0", List.of(
                new ContentPlan.ItemDefinition("ingot", null, properties),
                new ContentPlan.ItemDefinition("block", "contract_fixture:block", properties)),
                List.of(new ContentPlan.BlockDefinition("block", new ContentPlan.BlockProperties(material, 2, 6, "STONE", false, false))), List.of());
    }
    private static ZipFile runtimeJar() throws Exception {
        String artifact = System.getProperty("legacybridge.runtimeArtifact");
        assertNotNull(artifact, "The Gradle test task must provide the final runtime JAR");
        assertTrue(Files.isRegularFile(Path.of(artifact)), artifact);
        return new ZipFile(artifact);
    }
    private static byte[] entry(ZipFile jar, String name) throws Exception {
        var entry = jar.getEntry(name); assertNotNull(entry, jar.getName() + " missing " + name);
        try (var input = jar.getInputStream(entry)) { return input.readAllBytes(); }
    }
    private static Map<?, ?> json(byte[] bytes) throws Exception { return (Map<?, ?>) BoundedJsonParser.parse(new String(bytes, StandardCharsets.UTF_8)); }
    private static Set<String> ids(Map<?, ?> bindings) {
        Set<String> result = new TreeSet<>();
        for (Object entry : (List<?>) bindings.get("adapters")) assertTrue(result.add((String) ((Map<?, ?>) entry).get("id")));
        return result;
    }
    private static void assertEquivalent(Object expected, Object actual) {
        if (expected instanceof Number first) { assertInstanceOf(Number.class, actual); assertEquals(first.toString(), actual.toString()); }
        else if (expected instanceof Map<?, ?> map) {
            assertInstanceOf(Map.class, actual); Map<?, ?> other = (Map<?, ?>) actual; assertEquals(map.keySet(), other.keySet());
            map.forEach((key, value) -> assertEquivalent(value, other.get(key)));
        } else if (expected instanceof List<?> list) {
            assertInstanceOf(List.class, actual); List<?> other = (List<?>) actual; assertEquals(list.size(), other.size());
            for (int index = 0; index < list.size(); index++) assertEquivalent(list.get(index), other.get(index));
        } else assertEquals(expected, actual);
    }
}
