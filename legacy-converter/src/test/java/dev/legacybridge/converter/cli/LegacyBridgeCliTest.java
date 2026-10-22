package dev.legacybridge.converter.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import dev.legacybridge.converter.testutil.SyntheticForgeJarFactory;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBridgeCliTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void helpDocumentsAnalysisOnlyCommand() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int exitCode = LegacyBridgeCli.run(
                new String[]{"--help"},
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );

        assertEquals(0, exitCode);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("legacy-converter analyze"));
    }

    @Test
    void rejectsUnknownCommands() {
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int exitCode = LegacyBridgeCli.run(
                new String[]{"convert"},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8)
        );

        assertEquals(2, exitCode);
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("Invalid arguments"));
    }

    @Test
    void shortAnalyzeCommandWritesSyntheticLegacyJarReportToStdout() throws Exception {
        Path input = SyntheticForgeJarFactory.create(temporaryDirectory.resolve("fixture.jar"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int exitCode = LegacyBridgeCli.run(
                new String[]{"analyze", input.toString()},
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8)
        );

        assertEquals(0, exitCode);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("\"conversionStatus\": \"NOT_IMPLEMENTED\""));
        assertEquals("", error.toString(StandardCharsets.UTF_8));
    }

    @Test
    void willNotOverwriteReportWithoutExplicitFlag() throws Exception {
        Path input = SyntheticForgeJarFactory.create(temporaryDirectory.resolve("fixture.jar"));
        Path report = temporaryDirectory.resolve("report.json");
        java.nio.file.Files.writeString(report, "existing", StandardCharsets.UTF_8);
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int exitCode = LegacyBridgeCli.run(
                new String[]{"analyze", input.toString(), "--report", report.toString()},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8)
        );

        assertEquals(4, exitCode);
        assertEquals("existing", java.nio.file.Files.readString(report, StandardCharsets.UTF_8));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("use --overwrite"));
        assertTrue(!error.toString(StandardCharsets.UTF_8).contains(temporaryDirectory.toString()));
    }

    @Test
    void overwriteCannotReplaceInputThroughHardLinkAlias() throws Exception {
        Path input = SyntheticForgeJarFactory.create(temporaryDirectory.resolve("fixture.jar"));
        byte[] original = Files.readAllBytes(input);
        Path alias = Files.createLink(temporaryDirectory.resolve("report.json"), input);

        int exitCode = LegacyBridgeCli.run(
                new String[]{"analyze", input.toString(), "--report", alias.toString(), "--overwrite"},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );

        assertEquals(4, exitCode);
        assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(input)));
    }

    @Test
    void semanticallyInvalidForgeMetadataProducesReviewExitCode() throws Exception {
        String invalidMetadata = """
                modLoader="javafml"
                loaderVersion=36
                [[mods]]
                modId="fixture"
                """;
        Path input = SyntheticForgeJarFactory.create(
                temporaryDirectory.resolve("invalid-metadata.jar"),
                Map.of("META-INF/mods.toml", invalidMetadata.getBytes(StandardCharsets.UTF_8))
        );

        int exitCode = LegacyBridgeCli.run(
                new String[]{"analyze", input.toString(), "--report", "-"},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );

        assertEquals(1, exitCode);
    }
}
