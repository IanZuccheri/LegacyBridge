package dev.legacybridge.corpus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompositionRunStagerCommandTest {
    @Test void missingReviewedDigestNeverStagesAndReportsSanitizedFailure() throws Exception {
        rejected(new String[] {"synthetic-private", "SOURCE", "run-no-digest", "java"});
    }
    @Test void invalidRoleNeverEchoesUntrustedArguments() throws Exception {
        rejected(new String[] {"synthetic-private", "synthetic-private", "run-a", "java", "1".repeat(64)});
    }
    @Test void invalidPathNeverEchoesUntrustedArguments() throws Exception {
        rejected(new String[] {"synthetic-private\0", "SOURCE", "run-a", "java", "1".repeat(64)});
    }
    private static void rejected(String[] args) throws Exception {
        PrintStream previous = System.out; var bytes = new ByteArrayOutputStream();
        try (var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            IOException error = assertThrows(IOException.class, () -> CompositionRunStager.main(args));
            assertTrue(error.getMessage().startsWith("STAGE_"));
            assertFalse(error.toString().contains("synthetic-private")); assertNull(error.getCause());
        } finally { System.setOut(previous); }
        String json = bytes.toString(StandardCharsets.UTF_8);
        var tree = (java.util.Map<?, ?>) dev.legacybridge.converter.config.BoundedJsonParser.parse(json);
        assertEquals("BLOCKED", tree.get("status"));
        assertEquals(false, tree.get("executionAllowed"));
        assertFalse(json.contains("synthetic-private"));
    }
}
