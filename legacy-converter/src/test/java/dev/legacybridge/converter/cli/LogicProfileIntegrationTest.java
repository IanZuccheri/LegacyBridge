package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.LogicPreservingConverter;
import dev.legacybridge.converter.mapping.portable.PortableMappingBundle;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine reobfuscated Forge36 artifact; read as inert bytes, never loaded by these tests. */
@EnabledIfSystemProperty(named = "legacybridge.verifyLogicEnvironment", matches = "true")
class LogicProfileIntegrationTest {
    @TempDir Path directory;
    private Path fixture() { return Path.of(System.getProperty("legacybridge.fixtureRoot"), "logic-preservation-1.16.5/build/libs/legacybridge-logic-1.16.5-1.0.0.jar"); }
    private Path profile() { return Path.of(System.getProperty("legacybridge.logicMappingProfile")); }

    @Test void genuineFixturePreservesEveryClassAndBehaviorBodyAcrossCliAndService() throws Exception {
        Path original = fixture(); byte[] untouched = Files.readAllBytes(original);
        var inputs = new MappingInputsLoader().load(profile());
        Path bundle = directory.resolve("portable"); PortableMappingBundle.export(inputs, bundle);
        Path cliJar = Files.createDirectory(directory.resolve("cli")).resolve("converted.jar");
        Path serviceJar = Files.createDirectory(directory.resolve("service")).resolve("converted.jar");
        var stdout = new ByteArrayOutputStream(); var stderr = new ByteArrayOutputStream();
        int code = LegacyBridgeCli.run(new String[]{"convert", original.toString(), "--mapping-bundle", bundle.toString(), "--conversion-profile", "logic-preserving-v1", "--output", cliJar.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8), Map.of());
        assertEquals(0, code, stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8));
        try (var service = new LegacyEngineService()) {
            var request = new ConversionRequest(original, serviceJar, MappingData.bundle(bundle), Optional.empty(), ConversionProfile.LOGIC_PRESERVING_V1);
            var plan = service.plan(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, plan.status(), plan.toString()); assertFalse(Files.exists(serviceJar));
            var result = service.convert(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, result.status(), result.toString());
            assertEquals(stdout.toString(StandardCharsets.UTF_8), new CanonicalReportWriter().toJsonTree(result.value().orElseThrow().report()));
            assertEquals(0, result.value().orElseThrow().report().get("replacedClassCount"));
            assertEquals("NOT_RUN", result.value().orElseThrow().report().get("runtimeValidation"));
        }
        assertArrayEquals(Files.readAllBytes(cliJar), Files.readAllBytes(serviceJar));
        assertArrayEquals(untouched, Files.readAllBytes(original));
        var source = read(original); var target = read(cliJar);
        assertEquals(source.keySet().stream().filter(name -> name.endsWith(".class")).toList(), target.keySet().stream().filter(name -> name.endsWith(".class")).toList());
        assertEquals(6, target.keySet().stream().filter(name -> name.endsWith(".class")).count());
        assertFalse(target.keySet().stream().anyMatch(name -> name.endsWith("content.json") || name.startsWith("dev/legacybridge/generated/")));
        for (String name : source.keySet()) if (name.startsWith("assets/")) assertArrayEquals(source.get(name), target.get(name), name);
    }

    @Test void unknownExternalMethodAndDependencyProduceNoPartialJar() throws Exception {
        var inputs = new MappingInputsLoader().load(profile());
        var source = read(fixture());
        String owned = "dev/legacybridge/fixture/logic/LogicItem.class";
        var node = new ClassNode(); new ClassReader(source.get(owned)).accept(node, 0);
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "unknownDependency", "()V", null, null);
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "unknown/dependency/Library", "run", "()V", false)); method.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(method);
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); source.put(owned, writer.toByteArray());
        var unknown = write("unknown.jar", source); var out = directory.resolve("unknown-output.jar");
        var result = new LogicPreservingConverter().convert(unknown, out, inputs);
        assertFalse(result.converted()); assertFalse(Files.exists(out)); assertTrue(result.report().toString().contains("unknown/dependency/Library"), result.report().toString());
        source = read(fixture());
        String toml = new String(source.get("META-INF/mods.toml"), StandardCharsets.UTF_8) + "\n[[dependencies.legacybridge_logic]]\nmodId=\"missinglib\"\nmandatory=true\nversionRange=\"[1,)\"\nordering=\"NONE\"\nside=\"BOTH\"\n";
        source.put("META-INF/mods.toml", toml.getBytes(StandardCharsets.UTF_8));
        var dependency = write("dependency.jar", source); var dependencyOut = directory.resolve("dependency-output.jar");
        assertFalse(new LogicPreservingConverter().convert(dependency, dependencyOut, inputs).converted()); assertFalse(Files.exists(dependencyOut));
    }

    @Test void originalProfileDoesNotAcceptLogicFixtureAndCancelledPlanCannotPublish() throws Exception {
        var inputs = new MappingInputsLoader().load(profile()); var out = directory.resolve("default.jar");
        assertFalse(new dev.legacybridge.converter.conversion.SimpleContentConverter().convert(fixture(), out, inputs).converted()); assertFalse(Files.exists(out));
        var converter = new LogicPreservingConverter();
        try (var prepared = converter.prepare(fixture(), directory.resolve("not-published.jar"), inputs)) { assertTrue(prepared.convertible(), prepared.report().toString()); }
        assertFalse(Files.exists(directory.resolve("not-published.jar")));
        var ref = new java.util.concurrent.atomic.AtomicReference<OperationControl>();
        var control = new OperationControl(OperationLimits.defaults(), progress -> { if (progress.stage() == OperationStage.CLASS_REMAP) ref.get().requestCancel(); });
        ref.set(control);
        try (var scope = control.enter()) {
            assertThrows(OperationStoppedException.class, () -> converter.convert(fixture(), directory.resolve("cancelled.jar"), inputs));
        }
        assertFalse(Files.exists(directory.resolve("cancelled.jar")));
    }

    private static Map<String, byte[]> read(Path path) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        try (var zip = new ZipFile(path.toFile())) { for (var entry : zip.stream().toList()) if (!entry.isDirectory()) try(var input=zip.getInputStream(entry)) { entries.put(entry.getName(), input.readAllBytes()); } }
        return entries;
    }
    private Path write(String name, Map<String, byte[]> entries) throws IOException {
        Path path = directory.resolve(name); try (var zip = new ZipOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            for (var entry : entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); }
        } return path;
    }
}
