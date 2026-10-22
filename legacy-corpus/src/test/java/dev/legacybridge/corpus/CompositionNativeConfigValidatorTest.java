package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.legacybridge.corpus.CompositionRunPlanner.Role.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent source-derived config documents; no files, processes, games or generated mod classes. */
final class CompositionNativeConfigValidatorTest {
    private static final String FML = "config/fml.toml";
    private static final String COMMON = "config/forge-common.toml";
    private static final String SERVER = "world/serverconfig/forge-server.toml";
    private static final String SECRET = "PRIVATE_PAYLOAD_MUST_NOT_APPEAR";

    // FMLConfig definitions, not the incomplete packaged default files (3/10 keys).
    private static final String SOURCE_FML = """
            splashscreen=true
            maxThreads=-1
            versionCheck=true
            defaultConfigPath="defaultconfigs"
            """;
    private static final String TARGET_FML = """
            earlyWindowControl=true
            maxThreads=-1
            versionCheck=true
            defaultConfigPath="defaultconfigs"
            disableOptimizedDFU=true
            earlyWindowProvider="fmlearlywindow"
            earlyWindowWidth=854
            earlyWindowHeight=480
            earlyWindowFBScale=1
            earlyWindowMaximized=false
            earlyWindowSkipGLVersions=[]
            earlyWindowSquir=false
            earlyWindowShowCPU=false
            earlyWindowLogHelpMessage=true
            """;
    // Exact ForgeConfig declarations in the pinned 36.2.42 / 47.4.23 mapped source JARs.
    private static final String SOURCE_COMMON = """
            [general]
            defaultWorldType="default"
            """;
    private static final String SOURCE_SERVER = """
            [server]
            removeErroringEntities=false
            removeErroringTileEntities=false
            fullBoundingBoxLadders=false
            zombieBaseSummonChance=0.1
            zombieBabyChance=0.05
            logCascadingWorldGeneration=true
            fixVanillaCascading=false
            dimensionUnloadQueueDelay=0
            treatEmptyTagsAsAir=false
            fixAdvancementLoading=true
            """;
    private static final String TARGET_SERVER = """
            [server]
            removeErroringBlockEntities=false
            removeErroringEntities=false
            fullBoundingBoxLadders=false
            zombieBaseSummonChance=0.1
            zombieBabyChance=0.05
            permissionHandler="forge:default_handler"
            advertiseDedicatedServerToLan=true
            useItemWithDurationZero=false
            """;

    private record Fixture(CompositionRunPlanner.Role role, String path, String text, int values) {
        @Override public String toString() { return role + ":" + path; }
    }

    private static final List<Fixture> FIXTURES = List.of(
            new Fixture(SOURCE, FML, SOURCE_FML, 4),
            new Fixture(TARGET, FML, TARGET_FML, 14),
            new Fixture(SOURCE, COMMON, SOURCE_COMMON, 1),
            new Fixture(SOURCE, SERVER, SOURCE_SERVER, 10),
            new Fixture(TARGET, SERVER, TARGET_SERVER, 8));

    static Stream<Fixture> fixtures() { return FIXTURES.stream(); }

    @ParameterizedTest @MethodSource("fixtures")
    void independentlyDeclaredNativeDefaultsAreRecognizedWithoutGrantingAuthority(Fixture fixture) throws Exception {
        byte[] input = bytes(fixture.text());
        byte[] original = input.clone();
        Map<String, Object> result = validate(fixture, input);
        assertEquals(1, result.get("schemaVersion"));
        assertEquals("native-config-defaults-v1", result.get("policyVersion"));
        assertEquals(fixture.role().name(), result.get("role"));
        assertEquals(fixture.path(), result.get("path"));
        assertEquals("RECORDED_NATIVE_DEFAULTS_ONLY", result.get("status"));
        assertEquals(fixture.values(), result.get("valueCount"));
        assertEquals(false, result.get("executionAllowed"));
        assertEquals(false, result.get("installationAllowed"));
        assertArrayEquals(original, input, "Read-only content validation must not normalize input bytes");
        assertFalse(result.toString().contains("defaultconfigs"), "Do not echo accepted config values");
        assertFalse(result.toString().contains("forge:default_handler"));
        assertThrows(UnsupportedOperationException.class, () -> result.put("executionAllowed", true));
        assertTrue(result.get("launchConcerns") instanceof List<?>);
        List<?> concerns = (List<?>) result.get("launchConcerns");
        assertThrows(UnsupportedOperationException.class, concerns::clear);
        if (fixture.path().equals(FML)) assertTrue(concerns.contains("GLOBAL_VERSION_CHECK_ENABLED"));
        if (fixture.role() == TARGET && fixture.path().equals(SERVER))
            assertTrue(concerns.contains("LAN_ADVERTISEMENT_ENABLED"));
    }

    @ParameterizedTest @MethodSource("fixtures")
    void commentsWhitespaceCrLfAndKeyOrderDoNotChangeDefaultMeaning(Fixture fixture) throws Exception {
        List<String> lines = new ArrayList<>(fixture.text().lines().toList());
        String table = !lines.isEmpty() && lines.get(0).startsWith("[") ? lines.remove(0) : null;
        Collections.reverse(lines);
        StringBuilder equivalent = new StringBuilder("# Native defaults only; no execution claim\r\n\r\n");
        if (table != null) equivalent.append(" \t").append(table).append(" \t # table comment\r\n");
        for (String line : lines)
            equivalent.append("\t ").append(line.replace("=", " \t=\t ")).append(" \t# field comment\r\n");
        Map<String, Object> result = validate(fixture, bytes(equivalent.toString()));
        assertEquals(fixture.values(), result.get("valueCount"));
        assertEquals(false, result.get("executionAllowed"));
    }

    @ParameterizedTest @MethodSource("requiredValues")
    void everyRequiredDefaultMustRemainPresentAndUnchanged(Fixture fixture, String assignment) {
        int equals = assignment.indexOf('=');
        String key = assignment.substring(0, equals);
        String value = assignment.substring(equals + 1);
        String different = value.equals("true") ? "false" : value.equals("false") ? "true"
                : value.equals("[]") ? "[\"4.6\"]" : value.startsWith("\"") ? '"' + SECRET + '"'
                : value.contains(".") ? "0.5" : "2147483647";
        configFailure(() -> validate(fixture, bytes(fixture.text().replace(assignment, key + "=" + different))));
        configFailure(() -> validate(fixture, bytes(fixture.text().replace(assignment + "\n", ""))));
    }

    static Stream<Arguments> requiredValues() {
        return FIXTURES.stream().flatMap(fixture -> fixture.text().lines().filter(line -> line.contains("="))
                .map(line -> Arguments.of(fixture, line)));
    }

    @ParameterizedTest @MethodSource("requiredValues")
    void equalValueDuplicateKeysRemainAmbiguousAndAreRefused(Fixture fixture, String assignment) {
        configFailure(() -> validate(fixture, bytes(fixture.text() + assignment + "\n")));
    }

    @ParameterizedTest @MethodSource("fixtures")
    void unknownValuesNeverBecomeAcceptedOnlyBecauseOtherDefaultsAreValid(Fixture fixture) {
        configFailure(() -> validate(fixture, bytes(fixture.text() + "unknownKey=\"" + SECRET + "\"\n")));
        configFailure(() -> validate(fixture, bytes(fixture.text() + "[unknown]\n")));
        configFailure(() -> validate(fixture, bytes(fixture.text() + "[unknown]\nvalue=true\n")));
    }

    @ParameterizedTest @MethodSource("fixtures")
    void versionSpecificConfigCannotBeAdoptedByTheOtherRole(Fixture fixture) {
        var other = fixture.role() == SOURCE ? TARGET : SOURCE;
        configFailure(() -> CompositionNativeConfigValidator.validate(other, fixture.path(), bytes(fixture.text())));
    }

    @Test void targetCommonIsNeverRegisteredAndItsPresenceIsRefusedEvenWhenEmpty() {
        for (String text : List.of("", "\n", " \t\r\n# Empty Forge common spec\n",
                "[general]\n", "general={}\n", "defaultWorldType=\"default\"\n",
                "[general]\ndefaultWorldType=\"default\"\n", "[server]\n"))
            configFailure(() -> CompositionNativeConfigValidator.validate(TARGET, COMMON, bytes(text)));
    }

    @ParameterizedTest @ValueSource(strings = {
            "[server]\n[server]\n", "[general]\n", "[server.nested]\n", "[[server]]\n",
            "[\"server\"]\n", "['server']\n", "[server ]\n", "server.unknown=true\n",
            "[server] trailing", "[server] # comment\n[server]\n"
    })
    void unsupportedTableAndDottedFormsCannotBypassExactSchema(String text) {
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, SERVER, bytes(text + SOURCE_SERVER)));
    }

    @ParameterizedTest @ValueSource(strings = {
            "\"maxThreads\"=-1", "'maxThreads'=-1", "maxThreads.value=-1", "maxThreads=-01",
            "maxThreads=+1", "maxThreads=0xFF", "maxThreads=0o10", "maxThreads=0b1", "maxThreads=1_000",
            "maxThreads=-1.0", "maxThreads=-1e0", "maxThreads=\"-1\"", "maxThreads=-2147483649",
            "maxThreads=9223372036854775808", "maxThreads=null", "maxThreads={ value=-1 }",
            "maxThreads=[-1]", "maxThreads=-1;", "maxThreads=-1 extra", "maxThreads=-1\nunknown=true"
    })
    void integerSyntaxIsNotASecondGeneralTomlParser(String replacement) {
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, FML,
                bytes(SOURCE_FML.replace("maxThreads=-1", replacement))));
    }

    @ParameterizedTest @ValueSource(strings = {
            "TRUE", "True", "FALSE", "1", "\"true\"", "'true'", "[true]", "{value=true}",
            "true false", "true;", "true\\", "true\u0000", "true\u000B", "true\u000C", "true\u007F"
    })
    void booleansRejectCoercionsAndControls(String value) {
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, FML,
                bytes(SOURCE_FML.replace("versionCheck=true", "versionCheck=" + value))));
    }

    @ParameterizedTest @ValueSource(strings = {
            ".1", "1e-1", "1E-1", "+0.1", "0.10", "00.1", "0_0.1", "0.1_0", "nan", "inf", "-inf",
            "\"0.1\"", "0.1f", "0.1d", "[0.1]", "{value=0.1}", "0.1 true", "0.1;"
    })
    void doubleDefaultsUseTheNarrowGeneratedLiteralForm(String value) {
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, SERVER,
                bytes(SOURCE_SERVER.replace("zombieBaseSummonChance=0.1", "zombieBaseSummonChance=" + value))));
    }

    @ParameterizedTest @ValueSource(strings = {
            "'defaultconfigs'", "\"\"\"defaultconfigs\"\"\"", "'''defaultconfigs'''",
            "\"default\\u0063onfigs\"", "\"default\\configs\"", "\"defaultconfigs\\n\"",
            "\"defaultconfigs\"suffix", "defaultconfigs", "[\"defaultconfigs\"]", "{path=\"defaultconfigs\"}",
            "\"default#configs\"", "\"defaultconfigs", "\"defaultconfigs\n\"", "\"defaultconfigs\";"
    })
    void stringDefaultsHaveNoEscapeExpansionInterpolationOrMultilineSupport(String value) {
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, FML,
                bytes(SOURCE_FML.replace("defaultConfigPath=\"defaultconfigs\"", "defaultConfigPath=" + value))));
    }

    @ParameterizedTest @ValueSource(strings = {
            "[\"4.6\"]", "[1]", "[[]]", "[, ]", "[true]", "{}", "\"[]\"", "[\n]", "[ # note\n]"
    })
    void theSingleEmptyListCannotAdmitElementsOrMultilineValues(String value) {
        configFailure(() -> CompositionNativeConfigValidator.validate(TARGET, FML,
                bytes(TARGET_FML.replace("earlyWindowSkipGLVersions=[]", "earlyWindowSkipGLVersions=" + value))));
    }

    @ParameterizedTest @ValueSource(strings = {
            "", ".", "../config/fml.toml", "./config/fml.toml", "/config/fml.toml", "config\\fml.toml",
            "CONFIG/fml.toml", "config/FML.toml", "config//fml.toml", "config/fml.toml/", "config/fml.toml.",
            "config/fml.toml ", "config/fml.toml:stream", "config/fml.toml\u0000", "config/private.toml",
            "defaultconfigs/forge-server.toml", "world/DIM1/serverconfig/forge-server.toml", "world/serverconfig/private.toml"
    })
    void onlyThreeExactRelativePathsAreEligible(String path) {
        for (var role : CompositionRunPlanner.Role.values())
            configFailure(() -> CompositionNativeConfigValidator.validate(role, path, bytes(SOURCE_FML)));
    }

    @Test void missingInputsUseCheckedSanitizedDiagnostics() {
        configFailure(() -> CompositionNativeConfigValidator.validate(null, FML, bytes(SOURCE_FML)));
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, null, bytes(SOURCE_FML)));
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, FML, null));
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, SECRET, bytes(SECRET)));
    }

    @ParameterizedTest @MethodSource("malformedEncodings")
    void strictUtf8DoesNotRepairReplaceOrIgnoreMalformedBytes(byte[] invalid) {
        byte[] prefix = bytes(SOURCE_FML + "# " + SECRET);
        byte[] joined = Arrays.copyOf(prefix, prefix.length + invalid.length);
        System.arraycopy(invalid, 0, joined, prefix.length, invalid.length);
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, FML, joined));
    }

    static Stream<byte[]> malformedEncodings() {
        return Stream.of(new byte[]{(byte) 0x80}, new byte[]{(byte) 0xC0, (byte) 0xAF},
                new byte[]{(byte) 0xC3}, new byte[]{(byte) 0xC3, 0x28},
                new byte[]{(byte) 0xE2, (byte) 0x82}, new byte[]{(byte) 0xED, (byte) 0xA0, (byte) 0x80},
                new byte[]{(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80}, new byte[]{(byte) 0xFF});
    }

    @ParameterizedTest @ValueSource(strings = {"\u0000", "\u0001", "\u000B", "\u000C", "\u001B", "\u007F"})
    void commentsCannotHideForbiddenControls(String control) {
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, FML,
                bytes(SOURCE_FML + "# " + SECRET + control + "\n")));
    }

    @Test void bomDoesNotCreateAnotherDocumentDialect() {
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, FML, bytes("\uFEFF" + SOURCE_FML)));
    }

    @Test void emptyDocumentsDoNotSatisfyNonemptySpecs() {
        for (Fixture fixture : FIXTURES) if (fixture.values() != 0)
            for (String empty : List.of("", "\n", "# private comment only\n"))
                configFailure(() -> validate(fixture, bytes(empty)));
    }

    @Test void boundedCommentsCanContainUtf8WithoutLeakingTheirContent() throws Exception {
        var result = CompositionNativeConfigValidator.validate(SOURCE, FML,
                bytes(SOURCE_FML + "# " + SECRET + " çã日本語 😀\n"));
        assertFalse(result.toString().contains(SECRET));
        assertFalse(result.toString().contains("日本語"));
        assertEquals(4, result.get("valueCount"));
    }

    @Test void noLineCanExceedTheFiniteCharacterBudget() throws Exception {
        assertEquals(4, CompositionNativeConfigValidator.validate(SOURCE, FML,
                bytes(SOURCE_FML + "#".repeat(4096) + "\n")).get("valueCount"));
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, FML,
                bytes(SOURCE_FML + "#".repeat(4097) + "\n")));
    }

    @Test void commentsStillRespectLineCountLimit() {
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, FML,
                bytes(SOURCE_FML + String.join("\n", Collections.nCopies(513, "# finite native spec")))));
    }

    @Test void byteBudgetIsEnforcedEvenWhenEveryLineAndValueWouldBeAccepted() throws Exception {
        String atLimit = padWithBoundedComments(SOURCE_FML, 64 * 1024);
        assertEquals(64 * 1024, bytes(atLimit).length);
        assertEquals(4, CompositionNativeConfigValidator.validate(SOURCE, FML, bytes(atLimit)).get("valueCount"));
        String aboveLimit = padWithBoundedComments(SOURCE_FML, 64 * 1024 + 1);
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, FML, bytes(aboveLimit)));
    }

    @Test void mutationsDoNotPoisonSubsequentValidationOrShareReturnedState() throws Exception {
        var before = CompositionNativeConfigValidator.validate(SOURCE, FML, bytes(SOURCE_FML));
        configFailure(() -> CompositionNativeConfigValidator.validate(SOURCE, FML,
                bytes(SOURCE_FML.replace("splashscreen=true", "splashscreen=false"))));
        var after = CompositionNativeConfigValidator.validate(SOURCE, FML, bytes(SOURCE_FML));
        assertEquals(before, after);
        assertEquals(false, after.get("executionAllowed"));
    }

    private static String padWithBoundedComments(String content, int totalBytes) {
        StringBuilder padded = new StringBuilder(content);
        int remaining = totalBytes - bytes(content).length;
        while (remaining > 0) {
            int length = Math.min(remaining, 4096);
            if (length == 1) padded.append('\n');
            else padded.append('#').append("x".repeat(length - 2)).append('\n');
            remaining -= length;
        }
        return padded.toString();
    }

    private static Map<String, Object> validate(Fixture fixture, byte[] content) throws IOException {
        return CompositionNativeConfigValidator.validate(fixture.role(), fixture.path(), content);
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static void configFailure(Executable action) {
        IOException failure = assertThrows(IOException.class, action);
        assertNotNull(failure.getMessage());
        assertTrue(failure.getMessage().startsWith("COMPOSE_CONFIG_"), failure.getMessage());
        assertNull(failure.getCause(), "Parser exceptions must not retain arbitrary config excerpts");
        assertFalse(failure.getMessage().contains(SECRET));
        assertFalse(failure.getMessage().contains("defaultconfigs"));
        assertFalse(failure.getMessage().contains("forge:default_handler"));
        assertFalse(failure.getMessage().contains("\n"));
        assertFalse(failure.getMessage().contains("\r"));
    }
}
