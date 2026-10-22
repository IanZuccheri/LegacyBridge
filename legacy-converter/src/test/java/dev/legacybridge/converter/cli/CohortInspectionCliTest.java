package dev.legacybridge.converter.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CohortInspectionCliTest {
    @ParameterizedTest
    @ValueSource(strings = {"inspect-cohort", "inspect-cohort --mapping-bundle", "inspect-cohort --mapping-bundle x",
            "inspect-cohort --artifact lib bad input.jar", "inspect-cohort --output converted.jar",
            "inspect-cohort --mapping-bundle x --mapping-bundle y", "inspect-cohort --report input.jar",
            "plan-cohort", "plan-cohort --output converted.jar", "plan-cohort --report input.jar",
            "plan-cohort --mapping-bundle x --artifact library bad input.jar"})
    void invalidArgumentsNeverStartAnOperationOrWriteAReport(String command) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        int exit = LegacyBridgeCli.run(command.split(" "), new PrintStream(out), new PrintStream(err), Map.of());
        assertEquals(ExitCode.INVALID_USAGE.value(), exit); assertEquals(0, out.size()); assertTrue(err.size() > 0);
    }
}
