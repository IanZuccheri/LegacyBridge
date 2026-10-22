package dev.legacybridge.converter.mapping.source;

import net.minecraftforge.srgutils.IMappingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MappingInputsLoaderTest {
    @TempDir Path temporary;

    @Test
    void loadsRelativeProfileAndMcpZipWithCompleteProvenance() throws Exception {
        Path profile = profile();
        MappingInputs inputs = new MappingInputsLoader(temporary).load(profile);

        assertTrue(inputs.complete());
        assertEquals("a", inputs.legacyOfficial().getClass("fixture/Api").getMapped());
        assertEquals("fixture/LegacyApi", inputs.legacySrg().getClass("a").getMapped());
        assertTrue(inputs.legacyApi().hasClass("fixture/Api"));
        assertEquals(8, inputs.sources().size());
        assertTrue(inputs.sources().stream().allMatch(source -> source.origin().equals("LOCAL_PROFILE")));
        assertTrue(inputs.sources().stream().noneMatch(source -> source.toString().contains(temporary.toString())));
        assertTrue(inputs.diagnostics().stream().anyMatch(value -> value.startsWith("LOCAL_PROFILE:")));
    }

    @Test
    void missingDefaultCacheReturnsExplicitlyIncompleteEmptyInputs() throws Exception {
        MappingInputs inputs = new MappingInputsLoader(temporary).load(null);
        assertFalse(inputs.complete());
        assertEquals(8, inputs.diagnostics().stream().filter(value -> value.startsWith("MISSING_INPUT:")).count());
        assertTrue(inputs.sources().isEmpty());
        assertTrue(inputs.legacyOfficial().getClasses().isEmpty());
        assertTrue(inputs.targetApi().classes().isEmpty());
    }

    @Test
    void wrongVersionUnknownKeysAndMissingFilesAreActionableErrors() throws Exception {
        Path profile = profile();
        Properties values = properties(profile);
        values.setProperty("target.minecraft", "1.21.1");
        write(profile, values);
        assertTrue(assertThrows(IOException.class, () -> new MappingInputsLoader(temporary).load(profile))
                .getMessage().contains("target.minecraft=1.20.1"));
        values.setProperty("target.minecraft", "1.20.1");
        values.setProperty("target.typo", "yes");
        write(profile, values);
        assertTrue(assertThrows(IOException.class, () -> new MappingInputsLoader(temporary).load(profile))
                .getMessage().contains("Unknown mapping profile property"));
        values.remove("target.typo");
        values.setProperty("target.clientMappings", "does-not-exist.txt");
        write(profile, values);
        assertTrue(assertThrows(IOException.class, () -> new MappingInputsLoader(temporary).load(profile))
                .getMessage().contains("target.clientMappings does not exist"));
    }

    @Test
    void defaultCacheRejectsUnpinnedMappingBytes() throws Exception {
        Path cached = temporary.resolve("caches/forge_gradle/minecraft_repo/versions/1.16.5/client_mappings.txt");
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, "fixture.Api -> a:\n");
        assertTrue(assertThrows(IOException.class, () -> new MappingInputsLoader(temporary).load(null))
                .getMessage().contains("unexpected SHA-256"));
    }

    @Test
    void mergesAdditiveOfficialMembersAndRejectsClassMethodAndFieldConflicts() throws Exception {
        IMappingFile client = mappings("fixture.Api -> a:\n    int count -> b\n    void run() -> c\n");
        IMappingFile server = mappings("fixture.Api -> a:\n    int count -> b\n    void extra() -> d\n");
        IMappingFile merged = MappingInputsLoader.mergeOfficial(client, server, "fixture");
        assertEquals(2, merged.getClass("fixture/Api").getMethods().size());
        assertThrows(IOException.class, () -> MappingInputsLoader.mergeOfficial(client,
                mappings("fixture.Api -> z:\n"), "fixture"));
        assertThrows(IOException.class, () -> MappingInputsLoader.mergeOfficial(client,
                mappings("fixture.Api -> a:\n    void run() -> x\n"), "fixture"));
        assertThrows(IOException.class, () -> MappingInputsLoader.mergeOfficial(client,
                mappings("fixture.Api -> a:\n    long count -> b\n"), "fixture"));
        assertThrows(IOException.class, () -> MappingInputsLoader.mergeOfficial(client,
                mappings("fixture.Other -> a:\n"), "fixture"));
    }

    @Test
    void boundsMappingFilesBeforeAllocatingAndRejectsEmptyMappingDocuments() throws Exception {
        Path profile = profile();
        Path input = temporary.resolve("client.txt");
        try (FileChannel channel = FileChannel.open(input, StandardOpenOption.WRITE)) {
            channel.position(BoundedInputs.MAPPING_BYTES);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }
        assertTrue(assertThrows(IOException.class, () -> new MappingInputsLoader(temporary).load(profile))
                .getMessage().contains("exceeds byte limit"));
        Files.writeString(input, "# no class mappings\n");
        assertThrows(IOException.class, () -> new MappingInputsLoader(temporary).load(profile));
    }

    @Test
    void rejectsMcpArchiveWithoutRequiredMappingEntry() throws Exception {
        Path profile = profile();
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(temporary.resolve("mcp.zip")))) {
            output.putNextEntry(new JarEntry("config/wrong.tsrg"));
            output.write("a fixture/LegacyApi\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        assertTrue(assertThrows(IOException.class, () -> new MappingInputsLoader(temporary).load(profile))
                .getMessage().contains("missing config/joined.tsrg"));
    }

    private Path profile() throws IOException {
        Files.writeString(temporary.resolve("client.txt"), "fixture.Api -> a:\n    void run() -> b\n");
        Files.writeString(temporary.resolve("server.txt"), "fixture.Api -> a:\n    int count -> c\n");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(temporary.resolve("mcp.zip")))) {
            output.putNextEntry(new JarEntry("config/joined.tsrg"));
            output.write("a fixture/LegacyApi\n\tb ()V func_123_run\n\tc field_123_count\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(temporary.resolve("api.jar")))) {
            output.putNextEntry(new JarEntry("fixture/Api.class"));
            output.write(ApiIndexTest.api("fixture/Api", "run", "legacybridge.loader.fixture.executed"));
            output.closeEntry();
        }
        Properties values = new Properties();
        for (String side : new String[]{"legacy", "target"}) {
            values.setProperty(side + ".minecraft", side.equals("legacy") ? "1.16.5" : "1.20.1");
            values.setProperty(side + ".forge", side.equals("legacy") ? "36.2.42" : "47.4.23");
            values.setProperty(side + ".clientMappings", "client.txt");
            values.setProperty(side + ".serverMappings", "server.txt");
            values.setProperty(side + ".srg", "mcp.zip");
            values.setProperty(side + ".apiJars", "api.jar");
        }
        Path path = temporary.resolve("inputs.properties");
        write(path, values);
        return path;
    }

    private static IMappingFile mappings(String text) throws IOException {
        return IMappingFile.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static Properties properties(Path path) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(path)) { properties.load(input); }
        return properties;
    }

    private static void write(Path path, Properties values) throws IOException {
        try (var output = Files.newOutputStream(path)) { values.store(output, "Synthetic mapping fixture profile"); }
    }
}
