package dev.legacybridge.corpus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Invalid metadata commands only; no game, JVM child, fixture classes or selected native files. */
final class CompositionRunPlannerCommandTest {
    @Test void usageFailureIsStructuredAndCarriesNoExecutionPermission() throws Exception {
        rejected(new String[0], "COMPOSE_PLAN_USAGE_REPOSITORY_ROLE_RUNID_JAVAHOME");
    }

    @Test void invalidRoleDoesNotEchoArbitraryCallerText() throws Exception {
        rejected(new String[]{".", "private-value", "run-test", "."}, "COMPOSE_PLAN_ROLE");
    }

    @Test void invalidPathDoesNotEchoParserInputOrCause() throws Exception {
        rejected(new String[]{"private-value\0", "SOURCE", "run-test", "."}, "COMPOSE_PLAN_PATH");
    }

    private static void rejected(String[] args, String code) throws Exception {
        var bytes = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try (var capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            IOException failure = assertThrows(IOException.class, () -> CompositionRunPlanner.main(args));
            assertEquals(code, failure.getMessage());
            assertNull(failure.getCause());
        } finally { System.setOut(previous); }
        String json = bytes.toString(StandardCharsets.UTF_8);
        var report = CompositionRunFiles.object(dev.legacybridge.converter.config.BoundedJsonParser.parse(json));
        assertEquals("BLOCKED", report.get("status"));
        assertEquals(code, report.get("error"));
        for (String key : java.util.List.of("executionAllowed", "gameExecutionVerified", "installationAllowed"))
            assertEquals(false, report.get(key));
        assertFalse(json.contains("private-value"));
    }
}
