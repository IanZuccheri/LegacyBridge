package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.testutil.SyntheticForgeJarFactory;
import dev.legacybridge.converter.testutil.TestJarBuilder;
import dev.legacybridge.converter.config.BoundedJsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MappingCliTest {
    @TempDir Path directory;

    @Test void mappingsCommandReportsUnresolvedSymbolsWithoutChangingTheJar() throws Exception {
        Path input = SyntheticForgeJarFactory.create(directory.resolve("mod.jar"));
        Path profile = profile();
        byte[] original = Files.readAllBytes(input);
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int code = LegacyBridgeCli.run(new String[]{"mappings", input.toString(), "--mapping-profile", profile.toString()},
                stream(output), stream(errors));
        String json = output.toString(StandardCharsets.UTF_8);
        assertEquals(1, code);
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
        assertTrue(json.startsWith("{"));
        for (String status : new String[]{"RESOLVED", "ADAPTER_REQUIRED", "MANUAL", "UNSUPPORTED"}) assertTrue(json.contains("\"" + status + "\""));
        assertTrue(json.contains("\"reportType\": \"LEGACY_MAPPINGS\""));
        assertTrue(json.contains("\"bytecodeModified\": false"));
        assertFalse(json.contains("\"automaticRewriteAllowed\": true"));
        assertArrayEquals(original, Files.readAllBytes(input));
        assertFalse(json.contains(directory.toString()));
        var second = new ByteArrayOutputStream();
        assertEquals(code, LegacyBridgeCli.run(new String[]{"mappings", input.toString(), "--mapping-profile", profile.toString()}, stream(second), stream(errors)));
        assertEquals(json, second.toString(StandardCharsets.UTF_8));
    }

    @Test void mappingOutputRetainsExistingFileAndProtectsInputAlias() throws Exception {
        Path input = SyntheticForgeJarFactory.create(directory.resolve("mod.jar"));
        Path profile = profile();
        Path output = directory.resolve("report.json");
        Files.writeString(output, "existing");
        assertEquals(4, run("mappings", input.toString(), "--mapping-profile", profile.toString(), "--report", output.toString()));
        assertEquals("existing", Files.readString(output));
        Path alias = Files.createLink(directory.resolve("alias.json"), input);
        byte[] original = Files.readAllBytes(input);
        assertEquals(4, run("mappings", input.toString(), "--mapping-profile", profile.toString(), "--report", alias.toString(), "--overwrite"));
        assertArrayEquals(original, Files.readAllBytes(input));
    }

    @Test void knownLegacyApiWithoutImplementedDirectAdapterIsReportedUnsupported() throws Exception {
        Path profile = profile();
        String capability = "net/minecraftforge/common/capabilities/CapabilityInject";
        ClassWriter api = new ClassWriter(0);
        api.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, capability, null, "java/lang/Object", null);
        api.visitEnd();
        TestJarBuilder.write(directory.resolve("api.jar"), Map.of(capability + ".class", api.toByteArray()));
        ClassWriter mod = new ClassWriter(0);
        mod.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "sample/CapabilityUser", null, "java/lang/Object", null);
        mod.visitField(Opcodes.ACC_PUBLIC, "injection", "L" + capability + ";", null, null).visitEnd();
        mod.visitEnd();
        Path input = SyntheticForgeJarFactory.create(directory.resolve("unsupported.jar"),
                Map.of("sample/CapabilityUser.class", mod.toByteArray()));
        byte[] original = Files.readAllBytes(input);
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int code = LegacyBridgeCli.run(new String[]{"mappings", input.toString(), "--mapping-profile", profile.toString()}, stream(output), stream(errors));
        assertEquals(1, code);
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
        Map<?, ?> report = (Map<?, ?>) BoundedJsonParser.parse(output.toString(StandardCharsets.UTF_8));
        assertEquals(0, ((Number) ((Map<?, ?>) report.get("statusCounts")).get("ADAPTER_REQUIRED")).intValue());
        Map<?, ?> entry = ((List<?>) report.get("entries")).stream().map(value -> (Map<?, ?>) value)
                .filter(value -> {
                    var symbol = (Map<?, ?>) value.get("legacy");
                    return symbol.get("kind").equals("CLASS") && symbol.get("owner").equals(capability);
                }).findFirst().orElseThrow();
        assertEquals("UNSUPPORTED", entry.get("status"));
        assertEquals("NO_AUTOMATIC_EQUIVALENT", entry.get("category"));
        assertEquals(false, entry.get("automaticRewriteAllowed"));
        assertNull(entry.get("target"));
        assertTrue(entry.get("evidence").toString().contains("No implemented semantically equivalent direct-call adapter"));
        assertArrayEquals(original, Files.readAllBytes(input));
    }

    @Test void profileOptionIsExclusiveToMappingsAndInvalidProfileHasActionableExit() throws Exception {
        Path input = SyntheticForgeJarFactory.create(directory.resolve("mod.jar"));
        assertEquals(2, run("analyze", input.toString(), "--mapping-profile", "anything"));
        assertEquals(2, run("mappings", input.toString(), "--mapping-profile"));
        assertEquals(3, run("mappings", input.toString(), "--mapping-profile", directory.resolve("missing.properties").toString()));
    }

    private int run(String... args) {
        return LegacyBridgeCli.run(args, stream(new ByteArrayOutputStream()), stream(new ByteArrayOutputStream()));
    }
    private static PrintStream stream(ByteArrayOutputStream output) { return new PrintStream(output, true, StandardCharsets.UTF_8); }

    private Path profile() throws Exception {
        Files.writeString(directory.resolve("official.txt"), "example.Library -> a:\n    void <init>() -> <init>\n");
        Files.writeString(directory.resolve("mapping.tsrg"), "a example/Library\n");
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "example/Library", null, "java/lang/Object", null);
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null).visitEnd();
        writer.visitEnd();
        TestJarBuilder.write(directory.resolve("api.jar"), Map.of("example/Library.class", writer.toByteArray()));
        Path profile = directory.resolve("mappings.properties");
        Files.writeString(profile, """
                legacy.minecraft=1.16.5
                target.minecraft=1.20.1
                legacy.forge=36.2.42
                target.forge=47.4.23
                legacy.clientMappings=official.txt
                legacy.serverMappings=official.txt
                target.clientMappings=official.txt
                target.serverMappings=official.txt
                legacy.srg=mapping.tsrg
                target.srg=mapping.tsrg
                legacy.apiJars=api.jar
                target.apiJars=api.jar
                """);
        return profile;
    }
}
