package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.conversion.*;
import dev.legacybridge.converter.mapping.source.MappingInputsLoader;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** The genuine Forge fixture is read inertly. Native multiplayer is a separate test stage. */
@EnabledIfSystemProperty(named = "legacybridge.verifyNetworkEnvironment", matches = "true")
class NetworkProfileIntegrationTest {
    @TempDir Path temporary;
    @Test void sourceRecipesExactlyMatchAuditedMethods() throws Exception {
        var nodes = nodes(entries(fixture()));
        System.out.println(new CanonicalReportWriter().toJsonTree(new TreeMap<>(NetworkSnapshotRecipe.fingerprints(nodes))));
        assertEquals("PLAY_TO_CLIENT", NetworkSnapshotRecipe.validate(nodes, "legacybridge_network", "1.0.0").get("direction"));
    }
    @Test void cliServiceAndPrivatePlanAgreeWithOfflineLinkageNotRuntimeClaims() throws Exception {
        byte[] original = Files.readAllBytes(fixture());
        Path cli = Files.createDirectory(temporary.resolve("cli")).resolve("converted.jar");
        Path api = Files.createDirectory(temporary.resolve("api")).resolve("converted.jar");
        Path report = temporary.resolve("conversion.json");
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        int code = LegacyBridgeCli.run(new String[]{"convert", fixture().toString(), "--conversion-profile", "network-snapshot-v1",
                "--mapping-profile", profile().toString(), "--output", cli.toString(), "--report", report.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8), Map.of());
        assertEquals(0, code, out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8)
                + (Files.exists(report) ? Files.readString(report) : "No report"));
        try (var service = new LegacyEngineService()) {
            var request = new ConversionRequest(fixture(), api, MappingData.profile(profile()), Optional.of(hash(original)), ConversionProfile.NETWORK_SNAPSHOT_V1);
            var planned = service.plan(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, planned.status(), planned.toString());
            var plan = planned.value().orElseThrow(); assertTrue(plan.convertible(), plan.report().toString()); assertFalse(Files.exists(api));
            var outcome = service.convert(request).completion().toCompletableFuture().get(90, TimeUnit.SECONDS);
            assertEquals(EngineOutcome.Status.COMPLETED, outcome.status(), outcome.toString());
            var result = outcome.value().orElseThrow(); assertTrue(result.converted(), result.report().toString());
            assertEquals(Files.readString(report), new CanonicalReportWriter().toJsonTree(result.report()));
            assertEquals("network-snapshot-v1", result.report().get("supportProfile"));
            assertEquals("NOT_RUN", result.report().get("runtimeValidation")); assertEquals(false, result.report().get("bytecodeExecuted"));
            assertEquals(true, ((Map<?, ?>) result.report().get("bytecodeValidation")).get("offlineLinkageVerified"));
            assertEquals(2, result.report().get("preservedClassCount")); assertEquals(0, result.report().get("replacedClassCount"));
            assertEquals(0, result.report().get("itemCount"));
            for (Object raw : (List<?>) ((Map<?, ?>) result.report().get("transformationLog")).get("entries")) {
                var entry = (Map<?, ?>) raw; assertEquals("VERIFIED", entry.get("confidence")); assertNotNull(entry.get("adapter"));
            }
            assertEquals(plan.candidateSha256().orElseThrow(), hash(Files.readAllBytes(api)));
        }
        assertArrayEquals(Files.readAllBytes(cli), Files.readAllBytes(api));
        assertArrayEquals(original, Files.readAllBytes(fixture()));
    }
    @Test void everyChangedExecutableGuardAbortsWithoutArtifact() throws Exception {
        var mappings = new MappingInputsLoader().load(profile());
        var archive = entries(fixture());
        var base = nodes(archive);
        int index = 0;
        for (var node : base.values()) for (var method : node.methods) {
            var altered = entries(fixture()); var changed = nodes(altered).get(node.name);
            MethodNode target = changed.methods.stream().filter(m -> m.name.equals(method.name) && m.desc.equals(method.desc)).findFirst().orElseThrow();
            // Even a harmless new executable opcode has not had the reviewed contract proved.
            target.instructions.insert(new InsnNode(Opcodes.NOP));
            ClassWriter writer = new ClassWriter(0); changed.accept(writer); altered.put(changed.name + ".class", writer.toByteArray());
            Path input = write("changed-" + index + ".jar", altered), output = temporary.resolve("changed-" + index++ + "-converted.jar");
            var result = converter().convert(input, output, mappings);
            assertFalse(result.converted(), method.name); assertFalse(Files.exists(output));
            assertTrue(result.report().get("diagnostics").toString().contains("NETWORK_RECIPE_UNSUPPORTED"), result.report().toString());
        }
    }
    @Test void unknownExternalReferenceAndRequiredDependencyBlock() throws Exception {
        var mappings = new MappingInputsLoader().load(profile());
        var extra = entries(fixture()); var owner = nodes(extra).values().iterator().next();
        owner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "foreign", "Lunknown/External;", null, null));
        ClassWriter writer = new ClassWriter(0); owner.accept(writer); extra.put(owner.name + ".class", writer.toByteArray());
        var dependency = entries(fixture());
        dependency.put("META-INF/mods.toml", (new String(dependency.get("META-INF/mods.toml"), StandardCharsets.UTF_8)
                + "\n[[dependencies.legacybridge_network]]\nmodId=\"unknown_dependency\"\nmandatory=true\nversionRange=\"[1,)\"\nordering=\"AFTER\"\nside=\"BOTH\"\n").getBytes(StandardCharsets.UTF_8));
        int id = 0;
        for (var archive : List.of(extra, dependency)) {
            Path input = write("unknown" + id + ".jar", archive), output = temporary.resolve("blocked" + id++ + ".jar");
            var result = converter().convert(input, output, mappings);
            assertFalse(result.converted()); assertEquals("UNSUPPORTED", result.report().get("compatibilityStatus")); assertFalse(Files.exists(output));
        }
    }
    @Test void acceptedCancellationDoesNotPublishCandidate() throws Exception {
        var mappings = new MappingInputsLoader().load(profile());
        var reference = new AtomicReference<OperationControl>();
        var control = new OperationControl(OperationLimits.defaults(), p -> { if (p.stage() == OperationStage.PRE_PUBLICATION) assertTrue(reference.get().requestCancel()); });
        reference.set(control); Path dir = Files.createDirectory(temporary.resolve("cancel")), output = dir.resolve("converted.jar");
        try (var ignored = control.enter()) {
            assertEquals(OperationStoppedException.Code.CANCELLED, assertThrows(OperationStoppedException.class, () -> converter().convert(fixture(), output, mappings)).code());
        }
        try (var paths = Files.list(dir)) { assertEquals(0, paths.count()); }
    }
    private static LogicPreservingConverter converter() { return new LogicPreservingConverter(ConversionProfile.NETWORK_SNAPSHOT_V1); }
    private static Path fixture() { return Path.of(System.getProperty("legacybridge.fixtureRoot"), "network-snapshot-1.16.5/build/libs/legacybridge-network-1.16.5-1.0.0.jar"); }
    private static Path profile() { return Path.of(System.getProperty("legacybridge.networkMappingProfile")); }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static Map<String, ClassNode> nodes(Map<String, byte[]> entries) { var classes = new TreeMap<String, byte[]>(); entries.forEach((n, b) -> { if (n.endsWith(".class")) classes.put(n, b); }); return new LegacyReferenceScanner().scan(classes).classNodes(); }
    private static Map<String, byte[]> entries(Path path) throws IOException {
        assertTrue(Files.size(path) < 2 * 1024 * 1024); Map<String, byte[]> result = new TreeMap<>();
        try (var zip = new ZipFile(path.toFile())) { var iterator = zip.entries(); while (iterator.hasMoreElements()) {
            var entry = iterator.nextElement(); if (entry.isDirectory()) continue;
            try (var input = zip.getInputStream(entry)) { byte[] bytes = input.readNBytes(1_048_577); assertTrue(bytes.length <= 1_048_576); assertNull(result.put(entry.getName(), bytes)); }
        } } return result;
    }
    private Path write(String name, Map<String, byte[]> entries) throws IOException {
        Path path = temporary.resolve(name); try (var output = new ZipOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            for (var entry : entries.entrySet()) { output.putNextEntry(new ZipEntry(entry.getKey())); output.write(entry.getValue()); output.closeEntry(); }
        } return path;
    }
}
