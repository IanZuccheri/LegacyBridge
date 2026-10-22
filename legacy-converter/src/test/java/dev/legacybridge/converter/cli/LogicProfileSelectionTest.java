package dev.legacybridge.converter.cli;

import dev.legacybridge.converter.service.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LogicProfileSelectionTest {
    @Test void existingCliAndServiceKeepSimpleDefault() {
        assertEquals(ConversionProfile.SIMPLE_CONTENT_V1, CliArguments.parse(new String[]{"convert", "in.jar", "--output", "out.jar"}).conversionProfile());
        assertEquals(ConversionProfile.SIMPLE_CONTENT_V1, new ConversionRequest(Path.of("in.jar"), Path.of("out.jar"), MappingData.bundle(Path.of("bundle"))).profile());
    }
    @Test void explicitOptInSelectsNewProfileOnlyForConversion() {
        assertEquals(ConversionProfile.LOGIC_PRESERVING_V1, CliArguments.parse(new String[]{"convert", "in.jar", "--output", "out.jar", "--conversion-profile", "logic-preserving-v1"}).conversionProfile());
        assertThrows(IllegalArgumentException.class, () -> CliArguments.parse(new String[]{"analyze", "in.jar", "--conversion-profile", "logic-preserving-v1"}));
    }
    @Test void typosAndRepeatedSelectionCannotFallBackSilently() {
        assertThrows(IllegalArgumentException.class, () -> CliArguments.parse(new String[]{"convert", "in.jar", "--output", "out.jar", "--conversion-profile", "arbitrary"}));
        assertThrows(IllegalArgumentException.class, () -> CliArguments.parse(new String[]{"convert", "in.jar", "--output", "out.jar", "--conversion-profile", "logic-preserving-v1", "--conversion-profile", "simple-content-v1"}));
    }
}
