package dev.legacybridge.corpus;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.legacybridge.corpus.CompositionRunPlanner.Role;
import static dev.legacybridge.corpus.CompositionRunPlanner.Role.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent audited settings, not native execution or fixtures derived from production factories. */
final class CompositionRestrictedSettingsTest {
    private static final String PROPERTIES = "server.properties";
    private static final String FML = "config/fml.toml";
    private static final String COMMON = "config/forge-common.toml";
    private static final String TEMPLATE = "defaultconfigs/forge-server.toml";
    private static final String SERVER = "world/serverconfig/forge-server.toml";
    private static final String SECRET = "PRIVATE_PAYLOAD_MUST_NOT_APPEAR";

    // DedicatedServerProperties 36.2.42 / 47.4.23: independently audited active keys.
    // getLegacy-remove keys announce-player-achievements/resource-pack-hash are deliberately absent.
    private static final String SHARED_PROPERTIES = """
            allow-flight=false
            allow-nether=true
            broadcast-console-to-ops=false
            broadcast-rcon-to-ops=false
            difficulty=easy
            enable-command-block=false
            enable-jmx-monitoring=false
            enable-query=false
            enable-rcon=false
            enable-status=false
            enforce-whitelist=true
            entity-broadcast-range-percentage=100
            force-gamemode=false
            function-permission-level=2
            gamemode=survival
            generate-structures=false
            generator-settings=@GENERATOR@
            hardcore=false
            level-name=world
            level-seed=1
            level-type=@TYPE@
            max-players=1
            max-tick-time=60000
            max-world-size=29999984
            motd=LegacyBridge composition qualification
            network-compression-threshold=256
            online-mode=true
            op-permission-level=4
            player-idle-timeout=0
            prevent-proxy-connections=false
            pvp=false
            query.port=25565
            rate-limit=0
            rcon.password=
            rcon.port=25575
            resource-pack=
            resource-pack-sha1=
            server-ip=127.0.0.1
            server-port=@PORT@
            spawn-animals=false
            spawn-monsters=false
            spawn-npcs=false
            spawn-protection=16
            sync-chunk-writes=true
            text-filtering-config=
            use-native-transport=false
            view-distance=2
            white-list=true
            """;
    private static final String SOURCE_PROPERTIES = SHARED_PROPERTIES.replace("@GENERATOR@", "")
            .replace("@TYPE@", "default").replace("@PORT@", "25597") + """
            max-build-height=256
            snooper-enabled=false
            """;
    private static final String TARGET_PROPERTIES = SHARED_PROPERTIES.replace("@GENERATOR@", "{}")
            .replace("@TYPE@", "minecraft:normal").replace("@PORT@", "25598") + """
            enforce-secure-profile=true
            hide-online-players=true
            initial-disabled-packs=
            initial-enabled-packs=vanilla
            max-chained-neighbor-updates=1000000
            require-resource-pack=false
            resource-pack-prompt=
            simulation-distance=2
            """;

    // Pinned FMLConfig declarations, with only the explicit restricted-policy deltas.
    private static final String SOURCE_FML = """
            splashscreen=true
            maxThreads=1
            versionCheck=false
            defaultConfigPath="defaultconfigs"
            """;
    private static final String TARGET_FML = """
            earlyWindowControl=true
            maxThreads=1
            versionCheck=false
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
            advertiseDedicatedServerToLan=false
            useItemWithDurationZero=false
            """;

    private record Fixture(Role role, String path, String text) {
        @Override public String toString() { return role + ":" + path; }
        boolean properties() { return path.equals(PROPERTIES); }
        String plannedPath() { return path.equals(SERVER) ? TEMPLATE : path; }
    }

    private static final List<Fixture> CONFIGS = List.of(
            new Fixture(SOURCE, FML, SOURCE_FML), new Fixture(TARGET, FML, TARGET_FML),
            new Fixture(SOURCE, COMMON, SOURCE_COMMON),
            new Fixture(SOURCE, SERVER, SOURCE_SERVER), new Fixture(TARGET, SERVER, TARGET_SERVER));
    private static final List<Fixture> PROPERTY_FIXTURES = List.of(
            new Fixture(SOURCE, PROPERTIES, SOURCE_PROPERTIES), new Fixture(TARGET, PROPERTIES, TARGET_PROPERTIES));

    static Stream<Fixture> configurations() { return CONFIGS.stream(); }
    static Stream<Fixture> properties() { return PROPERTY_FIXTURES.stream(); }
    static Stream<Fixture> fixtures() { return Stream.concat(CONFIGS.stream(), PROPERTY_FIXTURES.stream()); }

    @ParameterizedTest @EnumSource(Role.class)
    void authoredFilesHaveOnlyAuditedSettingsAndNoExecutableOrWorldPayload(Role role) throws Exception {
        Map<String, String> files = CompositionRestrictedSettings.files(role);
        assertEquals(role == SOURCE ? Set.of(PROPERTIES, FML, COMMON, TEMPLATE) : Set.of(PROPERTIES, FML, TEMPLATE), files.keySet());
        for (Fixture fixture : fixtures().filter(f -> f.role() == role).toList()) {
            String content = files.get(fixture.plannedPath());
            assertNotNull(content);
            assertTrue(content.chars().allMatch(c -> c < 128), "Authored templates must be deterministic ASCII");
            if (fixture.properties()) {
                assertEquals(independentProperties(fixture.text()), independentProperties(content));
                assertEquals(role == SOURCE ? 50 : 56, independentProperties(content).size());
            } else {
                assertEquals(independentToml(fixture.text()), independentToml(content));
            }
            CompositionRestrictedSettings.validatePlannedFile(role, fixture.plannedPath(), bytes(content));
        }
        assertThrows(UnsupportedOperationException.class, () -> files.put("run.bat", SECRET));
        assertEquals(files, CompositionRestrictedSettings.files(role));
    }

    @ParameterizedTest @MethodSource("configurations")
    void sourceAuditedRestrictedConfigsAreAcceptedReadOnly(Fixture fixture) throws Exception {
        byte[] input = bytes(fixture.text());
        byte[] untouched = input.clone();
        generated(fixture, input);
        CompositionRestrictedSettings.validatePlannedFile(fixture.role(), fixture.plannedPath(), input);
        assertArrayEquals(untouched, input);
    }

    @ParameterizedTest @MethodSource("configurations")
    void generatedTomlCanReorderAndAddCommentsWithoutChangingItsMeaning(Fixture fixture) throws Exception {
        List<String> lines = new ArrayList<>(fixture.text().lines().toList());
        String table = lines.get(0).startsWith("[") ? lines.remove(0) : null;
        Collections.reverse(lines);
        StringBuilder rewritten = new StringBuilder("# Native rewrite, not execution evidence\r\n\r\n");
        if (table != null) rewritten.append(" \t").append(table).append(" # table\r\n");
        for (String line : lines)
            rewritten.append(" \t").append(line.replace("=", " \t=\t ")).append(" # field\r\n");
        generated(fixture, bytes(rewritten.toString()));
        CompositionRestrictedSettings.validatePlannedFile(fixture.role(), fixture.plannedPath(), bytes(rewritten.toString()));
    }

    @ParameterizedTest @MethodSource("properties")
    void independentlyAuditedPropertyValuesAreAcceptedWithoutRewritingBytes(Fixture fixture) throws Exception {
        byte[] input = bytes(fixture.text()), original = input.clone();
        generated(fixture, input);
        CompositionRestrictedSettings.validatePlannedFile(fixture.role(), PROPERTIES, input);
        assertArrayEquals(original, input);
        assertEquals(fixture.role() == SOURCE ? 50 : 56, independentProperties(fixture.text()).size());
    }

    @ParameterizedTest @MethodSource("properties")
    void propertiesAcceptNativeOrderCommentsSeparatorsEscapesAndContinuation(Fixture fixture) throws Exception {
        List<String> lines = new ArrayList<>(fixture.text().lines().toList());
        Collections.reverse(lines);
        StringBuilder rewritten = new StringBuilder("#Minecraft server properties\r\n! timestamp\r\n\r\n");
        for (String line : lines) {
            int equals = line.indexOf('=');
            rewritten.append(" \t").append(line, 0, equals).append(" \t: ")
                    .append(line.substring(equals + 1).replace(":", "\\:")).append("\r\n");
        }
        String escaped = rewritten.toString().replace("server-ip", "server-\\u0069p")
                .replace("127.0.0.1", "127.0.\\\r\n  0.1")
                .replace("level-seed", "level-\\u0073eed");
        generated(fixture, bytes(escaped));
        CompositionRestrictedSettings.validatePlannedFile(fixture.role(), PROPERTIES, bytes(escaped));
    }

    static Stream<Arguments> propertyAssignments() {
        return PROPERTY_FIXTURES.stream().flatMap(fixture -> fixture.text().lines()
                .map(assignment -> Arguments.of(fixture, assignment)));
    }

    @ParameterizedTest @MethodSource("propertyAssignments")
    void everyPropertyValueIsRequiredExactAndCannotBeChanged(Fixture fixture, String assignment) {
        String key = assignment.substring(0, assignment.indexOf('='));
        byte[] missing = bytes(fixture.text().replace(assignment + "\n", ""));
        byte[] changed = bytes(fixture.text().replace(assignment + "\n", key + "=" + SECRET + "\n"));
        failure(() -> generated(fixture, missing));
        failure(() -> CompositionRestrictedSettings.validatePlannedFile(fixture.role(), PROPERTIES, missing));
        failure(() -> generated(fixture, changed));
        failure(() -> CompositionRestrictedSettings.validatePlannedFile(fixture.role(), PROPERTIES, changed));
    }

    @ParameterizedTest @MethodSource("propertyAssignments")
    void everyEqualValueDuplicatePropertyIsRejectedIncludingUnicodeAliases(Fixture fixture, String assignment) {
        failure(() -> generated(fixture, bytes(fixture.text() + assignment + "\n")));
        String escapedFirst = String.format("\\u%04x", (int) assignment.charAt(0)) + assignment.substring(1);
        failure(() -> generated(fixture, bytes(fixture.text() + escapedFirst + "\n")));
        failure(() -> CompositionRestrictedSettings.validatePlannedFile(fixture.role(), PROPERTIES,
                bytes(fixture.text() + escapedFirst + "\n")));
    }

    @ParameterizedTest @ValueSource(strings = {
            "unknown=PRIVATE_PAYLOAD_MUST_NOT_APPEAR", "announce-player-achievements=true", "resource-pack-hash=",
            "announce-player-achievements=", "resource-pack-hash=PRIVATE_PAYLOAD_MUST_NOT_APPEAR",
            "version-check=false", "allow-nether-extra=true", "server-ip-extra=127.0.0.1"
    })
    void unknownAndRemovedLegacyPropertiesAreNotAdopted(String assignment) {
        for (Fixture fixture : PROPERTY_FIXTURES) {
            failure(() -> generated(fixture, bytes(fixture.text() + assignment + "\n")));
            failure(() -> CompositionRestrictedSettings.validatePlannedFile(fixture.role(), PROPERTIES,
                    bytes(fixture.text() + assignment + "\n")));
        }
    }

    @ParameterizedTest @MethodSource("fixtures")
    void roleSpecificSettingsCannotBeImportedIntoTheOtherRole(Fixture fixture) {
        Role other = fixture.role() == SOURCE ? TARGET : SOURCE;
        failure(() -> CompositionRestrictedSettings.validateGeneratedFile(other, fixture.path(), bytes(fixture.text())));
        failure(() -> CompositionRestrictedSettings.validatePlannedFile(other, fixture.plannedPath(), bytes(fixture.text())));
    }

    @ParameterizedTest @EnumSource(Role.class)
    void templateIsOnlyPlannedAndWorldCopyIsOnlyGenerated(Role role) throws Exception {
        byte[] content = bytes(role == SOURCE ? SOURCE_SERVER : TARGET_SERVER);
        CompositionRestrictedSettings.validatePlannedFile(role, TEMPLATE, content);
        CompositionRestrictedSettings.validateGeneratedFile(role, SERVER, content);
        failure(() -> CompositionRestrictedSettings.validatePlannedFile(role, SERVER, content));
        failure(() -> CompositionRestrictedSettings.validateGeneratedFile(role, TEMPLATE, content));
    }

    static Stream<Arguments> requiredConfigAssignments() {
        return CONFIGS.stream().flatMap(fixture -> fixture.text().lines().filter(line -> line.contains("="))
                .map(line -> Arguments.of(fixture, line)));
    }

    @ParameterizedTest @MethodSource("requiredConfigAssignments")
    void everyRestrictedConfigValueIsRequiredAndCannotBeChanged(Fixture fixture, String assignment) {
        String key = assignment.substring(0, assignment.indexOf('='));
        String value = assignment.substring(assignment.indexOf('=') + 1);
        String changed = value.equals("true") ? "false" : value.equals("false") ? "true"
                : value.equals("[]") ? "[\"4.6\"]" : value.startsWith("\"") ? '"' + SECRET + '"'
                : value.contains(".") ? "0.7" : "2147483647";
        byte[] missing = bytes(fixture.text().replace(assignment + "\n", ""));
        byte[] drifted = bytes(fixture.text().replace(assignment, key + "=" + changed));
        failure(() -> generated(fixture, missing));
        failure(() -> generated(fixture, drifted));
        failure(() -> CompositionRestrictedSettings.validatePlannedFile(fixture.role(), fixture.plannedPath(), missing));
        failure(() -> CompositionRestrictedSettings.validatePlannedFile(fixture.role(), fixture.plannedPath(), drifted));
    }

    @ParameterizedTest @MethodSource("requiredConfigAssignments")
    void duplicateConfigValuesRemainAmbiguousEvenWhenEqual(Fixture fixture, String assignment) {
        failure(() -> generated(fixture, bytes(fixture.text() + assignment + "\n")));
        failure(() -> CompositionRestrictedSettings.validatePlannedFile(fixture.role(), fixture.plannedPath(),
                bytes(fixture.text() + assignment + "\n")));
    }

    @ParameterizedTest @MethodSource("configurations")
    void unknownConfigKeysAndTablesCannotHideInsideAValidFile(Fixture fixture) {
        failure(() -> generated(fixture, bytes(fixture.text() + "unknown=\"" + SECRET + "\"\n")));
        failure(() -> generated(fixture, bytes(fixture.text() + "[unknown]\n")));
        failure(() -> generated(fixture, bytes(fixture.text() + "[unknown]\nvalue=true\n")));
    }

    @ParameterizedTest @ValueSource(strings = {
            "\"maxThreads\"=1", "'maxThreads'=1", "maxThreads.value=1", "maxThreads=01", "maxThreads=+1",
            "maxThreads=0x1", "maxThreads=1_0", "maxThreads=1.0", "maxThreads=1e0", "maxThreads=\"1\"",
            "maxThreads=[1]", "maxThreads={value=1}", "maxThreads=1;", "maxThreads=1 extra"
    })
    void restrictedSettingsDoNotWidenTheExistingFiniteTomlDialect(String assignment) {
        failure(() -> CompositionRestrictedSettings.validateGeneratedFile(SOURCE, FML,
                bytes(SOURCE_FML.replace("maxThreads=1", assignment))));
    }

    @ParameterizedTest @ValueSource(strings = {
            "[server]\n[server]\n", "[[server]]\n", "[server.nested]\n", "[\"server\"]\n",
            "['server']\n", "[server ]\n", "server.value=true\n"
    })
    void repeatedOrAlternativeTablesCannotBypassTheRestrictedSchema(String prefix) {
        failure(() -> CompositionRestrictedSettings.validateGeneratedFile(SOURCE, SERVER, bytes(prefix + SOURCE_SERVER)));
    }

    @Test void emptyTargetCommonNeverBecomesAnImplementedConfiguration() {
        for (String content : List.of("", "\n", "# no registered target Common spec\n", SOURCE_COMMON)) {
            failure(() -> CompositionRestrictedSettings.validatePlannedFile(TARGET, COMMON, bytes(content)));
            failure(() -> CompositionRestrictedSettings.validateGeneratedFile(TARGET, COMMON, bytes(content)));
        }
    }

    @Test void settingsDoNotRelaxTheOlderNativeDefaultsReviewer() throws Exception {
        for (Role role : Role.values()) {
            String restricted = role == SOURCE ? SOURCE_FML : TARGET_FML;
            failure(() -> CompositionNativeConfigValidator.validate(role, FML, bytes(restricted)));
            String defaults = restricted.replace("maxThreads=1", "maxThreads=-1").replace("versionCheck=false", "versionCheck=true");
            assertEquals("RECORDED_NATIVE_DEFAULTS_ONLY", CompositionNativeConfigValidator.validate(role, FML, bytes(defaults)).get("status"));
            failure(() -> CompositionRestrictedSettings.validateGeneratedFile(role, FML, bytes(defaults)));
        }
        String targetDefaults = TARGET_SERVER.replace("advertiseDedicatedServerToLan=false", "advertiseDedicatedServerToLan=true");
        failure(() -> CompositionRestrictedSettings.validateGeneratedFile(TARGET, SERVER, bytes(targetDefaults)));
        failure(() -> CompositionNativeConfigValidator.validate(TARGET, SERVER, bytes(TARGET_SERVER)));
        assertEquals("RECORDED_NATIVE_DEFAULTS_ONLY", CompositionNativeConfigValidator.validate(TARGET, SERVER,
                bytes(targetDefaults)).get("status"));
    }

    @ParameterizedTest @ValueSource(strings = {
            "", ".", "../server.properties", "./server.properties", "/server.properties", "SERVER.PROPERTIES",
            "C:/server.properties", "server.properties:private", "server.properties/", "server.properties.",
            "server.properties ", "config\\fml.toml", "config//fml.toml", "config/FML.toml", "config/fml.toml\u0000",
            "world/DIM1/serverconfig/forge-server.toml", "world/serverconfig/private.toml", "defaultconfigs/private.toml",
            "user_jvm_args.txt", "eula.txt", "run.bat", "mods/private.jar"
    })
    void pathsAreExactRelativePolicyKeysNotFilesystemAliases(String path) {
        for (Role role : Role.values()) {
            failure(() -> CompositionRestrictedSettings.validatePlannedFile(role, path, bytes(SECRET)));
            failure(() -> CompositionRestrictedSettings.validateGeneratedFile(role, path, bytes(SECRET)));
        }
    }

    @Test void missingInputsProduceCheckedSanitizedErrors() {
        failure(() -> CompositionRestrictedSettings.validatePlannedFile(null, FML, bytes(SOURCE_FML)));
        failure(() -> CompositionRestrictedSettings.validateGeneratedFile(null, FML, bytes(SOURCE_FML)));
        failure(() -> CompositionRestrictedSettings.validatePlannedFile(SOURCE, null, bytes(SOURCE_FML)));
        failure(() -> CompositionRestrictedSettings.validateGeneratedFile(SOURCE, null, bytes(SOURCE_FML)));
        failure(() -> CompositionRestrictedSettings.validatePlannedFile(SOURCE, FML, null));
        failure(() -> CompositionRestrictedSettings.validateGeneratedFile(SOURCE, FML, null));
        failure(() -> CompositionRestrictedSettings.validateGeneratedFile(SOURCE, SECRET, bytes(SECRET)));
    }

    @ParameterizedTest @ValueSource(strings = {
            "server-ip=0.0.0.0", "server-port=25565", "level-name=../PRIVATE_PAYLOAD_MUST_NOT_APPEAR",
            "level-seed=0", "level-seed=", "enable-rcon=true", "enable-query=true", "enable-status=true",
            "enable-jmx-monitoring=true", "online-mode=false", "white-list=false", "enforce-whitelist=false",
            "max-players=20", "max-tick-time=-1", "view-distance=10", "spawn-monsters=true", "spawn-animals=true",
            "spawn-npcs=true", "use-native-transport=true", "generate-structures=true", "rcon.password=PRIVATE_PAYLOAD_MUST_NOT_APPEAR",
            "resource-pack=https://invalid.example/PRIVATE_PAYLOAD_MUST_NOT_APPEAR", "text-filtering-config={}",
            "sync-chunk-writes=false", "query.port=0", "rate-limit=1", "pvp=true"
    })
    void nativeValidAlternativesDoNotBecomeAuthorizedLaunchSettings(String assignment) {
        String key = assignment.substring(0, assignment.indexOf('='));
        for (Fixture fixture : PROPERTY_FIXTURES) {
            String baseline = fixture.text().lines().filter(line -> line.startsWith(key + "=")).findFirst().orElseThrow();
            assertNotEquals(baseline, assignment);
            byte[] changed = bytes(fixture.text().replace(baseline + "\n", assignment + "\n"));
            failure(() -> generated(fixture, changed));
            failure(() -> CompositionRestrictedSettings.validatePlannedFile(fixture.role(), PROPERTIES, changed));
        }
    }

    @ParameterizedTest @ValueSource(strings = {
            "server-ip=127.0.0.1\nserver-\\\n ip=127.0.0.1", "server-ip=\\u00ZZ",
            "server-ip=\\u006", "server-ip=127.0.0.1\u0000", "server-ip=127.0.0.1\\u0000"
    })
    void decodedPropertyAliasesMalformedEscapesAndNulAreRefused(String replacement) {
        for (Fixture fixture : PROPERTY_FIXTURES) {
            byte[] changed = bytes(fixture.text().replace("server-ip=127.0.0.1", replacement));
            failure(() -> generated(fixture, changed));
            failure(() -> CompositionRestrictedSettings.validatePlannedFile(fixture.role(), PROPERTIES, changed));
        }
    }

    static Stream<byte[]> malformedUtf8() {
        return Stream.of(new byte[]{(byte) 0x80}, new byte[]{(byte) 0xC0, (byte) 0xAF},
                new byte[]{(byte) 0xC3}, new byte[]{(byte) 0xC3, 0x28}, new byte[]{(byte) 0xE2, (byte) 0x82},
                new byte[]{(byte) 0xED, (byte) 0xA0, (byte) 0x80},
                new byte[]{(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80}, new byte[]{(byte) 0xFF});
    }

    @ParameterizedTest @MethodSource("malformedUtf8")
    void targetPropertiesAndEveryTomlFileRejectInvalidUtf8EvenInComments(byte[] invalid) {
        List<Fixture> utf8Only = Stream.concat(CONFIGS.stream(), Stream.of(PROPERTY_FIXTURES.get(1))).toList();
        for (Fixture fixture : utf8Only) {
            byte[] prefix = bytes(fixture.text() + "# " + SECRET);
            byte[] joined = Arrays.copyOf(prefix, prefix.length + invalid.length);
            System.arraycopy(invalid, 0, joined, prefix.length, invalid.length);
            failure(() -> generated(fixture, joined));
            failure(() -> CompositionRestrictedSettings.validatePlannedFile(fixture.role(), fixture.plannedPath(), joined));
        }
    }

    @Test void sourcePropertiesKeepNativeLatin1SemanticsAndTargetGeneratedPropertiesRequireUtf8() throws Exception {
        byte[] sourceLatin1 = (SOURCE_PROPERTIES + "# Caf\u00e9\n").getBytes(StandardCharsets.ISO_8859_1);
        CompositionRestrictedSettings.validateGeneratedFile(SOURCE, PROPERTIES, sourceLatin1);
        CompositionRestrictedSettings.validatePlannedFile(SOURCE, PROPERTIES, sourceLatin1);
        byte[] targetLatin1 = (TARGET_PROPERTIES + "# Caf\u00e9\n").getBytes(StandardCharsets.ISO_8859_1);
        failure(() -> CompositionRestrictedSettings.validateGeneratedFile(TARGET, PROPERTIES, targetLatin1));
        failure(() -> CompositionRestrictedSettings.validatePlannedFile(TARGET, PROPERTIES, targetLatin1));
        byte[] targetUtf8 = bytes(TARGET_PROPERTIES + "# Caf\u00e9 \u2603\n");
        CompositionRestrictedSettings.validateGeneratedFile(TARGET, PROPERTIES, targetUtf8);
        CompositionRestrictedSettings.validatePlannedFile(TARGET, PROPERTIES, targetUtf8);
    }

    @ParameterizedTest @MethodSource("fixtures")
    void byteBudgetsApplyToCommentsAsWellAsSettings(Fixture fixture) throws Exception {
        byte[] atLimit = bytes(padComments(fixture.text(), 65536));
        assertEquals(65536, atLimit.length);
        generated(fixture, atLimit);
        CompositionRestrictedSettings.validatePlannedFile(fixture.role(), fixture.plannedPath(), atLimit);
        byte[] tooLarge = bytes(padComments(fixture.text(), 65537));
        failure(() -> generated(fixture, tooLarge));
        failure(() -> CompositionRestrictedSettings.validatePlannedFile(fixture.role(), fixture.plannedPath(), tooLarge));
    }

    @ParameterizedTest @MethodSource("configurations")
    void configLineLimitsAreNotRelaxedByRestrictedPolicy(Fixture fixture) throws Exception {
        generated(fixture, bytes(fixture.text() + "#".repeat(4096) + "\n"));
        failure(() -> generated(fixture, bytes(fixture.text() + "#".repeat(4097) + "\n")));
        failure(() -> generated(fixture, bytes(fixture.text() + "# line\n".repeat(513))));
    }

    @ParameterizedTest @MethodSource("fixtures")
    void emptyAndCommentOnlyFilesNeverStandInForRequiredValues(Fixture fixture) {
        for (String content : List.of("", "\n", "# " + SECRET + "\n")) {
            failure(() -> generated(fixture, bytes(content)));
            failure(() -> CompositionRestrictedSettings.validatePlannedFile(fixture.role(), fixture.plannedPath(), bytes(content)));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"\u0000", "\u0001", "\u000B", "\u000C", "\u001B", "\u007F"})
    void tomlCommentsCannotHideControls(String control) {
        failure(() -> CompositionRestrictedSettings.validateGeneratedFile(SOURCE, FML,
                bytes(SOURCE_FML + "# " + SECRET + control + "\n")));
    }

    @Test void byteOrderMarksAreNotAnAdditionalConfigDialect() {
        for (Fixture fixture : fixtures().toList())
            failure(() -> generated(fixture, bytes("\uFEFF" + fixture.text())));
    }

    @ParameterizedTest @EnumSource(Role.class)
    void everyIntentionalNativeDefaultChangeHasAnExactImmutableRecipe(Role role) throws Exception {
        List<Map<String, Object>> changes = CompositionRestrictedSettings.changes(role);
        Map<String, Map<String, String>> expected = independentChanges(role);
        Map<String, Map<String, String>> actual = new TreeMap<>();
        for (Map<String, Object> change : changes) {
            String identity = change.get("path") + "#" + change.get("key");
            Map<String, String> values = Map.of("before", (String) change.get("before"), "after", (String) change.get("after"));
            assertNull(actual.put(identity, values), "Every file/key identity must be unique");
            assertNotEquals(change.get("before"), change.get("after"), "Unchanged defaults are not transformations");
            assertEquals("VERIFIED", change.get("confidence"));
            assertTrue(change.get("rule") instanceof String rule && !rule.isBlank());
            assertTrue(change.get("reason") instanceof String reason && !reason.isBlank());
            assertThrows(UnsupportedOperationException.class, () -> change.put("confidence", "HIGH"));
        }
        assertEquals(expected, actual, "Every deliberate nondefault and only that delta needs a documented recipe");
        assertThrows(UnsupportedOperationException.class, changes::clear);
        assertEquals(changes, CompositionRestrictedSettings.changes(role));
    }

    private static Map<String, Map<String, String>> independentChanges(Role role) throws Exception {
        Map<String, String> expectedProperties = independentProperties(role == SOURCE ? SOURCE_PROPERTIES : TARGET_PROPERTIES);
        // Source-audited native defaults, not the earlier partial planner baseline.
        Map<String, String> defaults = independentProperties("""
                broadcast-console-to-ops=true
                broadcast-rcon-to-ops=true
                enable-status=true
                enforce-whitelist=false
                generate-structures=true
                level-seed=
                max-players=20
                motd=A Minecraft Server
                pvp=true
                server-ip=
                server-port=25565
                spawn-animals=true
                spawn-monsters=true
                spawn-npcs=true
                use-native-transport=true
                view-distance=10
                white-list=false
                """);
        if (role == SOURCE) defaults.put("snooper-enabled", "true");
        else { defaults.put("hide-online-players", "false"); defaults.put("simulation-distance", "10"); }
        Map<String, Map<String, String>> result = new TreeMap<>();
        defaults.forEach((key, value) -> result.put(PROPERTIES + "#" + key,
                Map.of("before", value, "after", expectedProperties.get(key))));
        result.put(FML + "#maxThreads", Map.of("before", "-1", "after", "1"));
        result.put(FML + "#versionCheck", Map.of("before", "true", "after", "false"));
        if (role == TARGET) result.put(TEMPLATE + "#server.advertiseDedicatedServerToLan",
                Map.of("before", "true", "after", "false"));
        return result;
    }

    private static Map<String, String> independentProperties(String text) throws IOException {
        Properties parsed = new Properties();
        parsed.load(new StringReader(text));
        Map<String, String> values = new TreeMap<>();
        for (String key : parsed.stringPropertyNames()) values.put(key, parsed.getProperty(key));
        return values;
    }

    private static Map<String, String> independentToml(String text) {
        Map<String, String> values = new TreeMap<>();
        String table = "";
        for (String raw : text.lines().toList()) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[")) { table = line.substring(1, line.length() - 1) + "."; continue; }
            int equals = line.indexOf('=');
            assertTrue(equals > 0, "Authored fixture must have a simple literal assignment");
            assertNull(values.put(table + line.substring(0, equals).trim(), line.substring(equals + 1).trim()));
        }
        return values;
    }

    private static String padComments(String text, int totalBytes) {
        StringBuilder padded = new StringBuilder(text);
        int remaining = totalBytes - bytes(text).length;
        while (remaining > 0) {
            int length = Math.min(remaining, 4096);
            if (length == 1) padded.append('\n');
            else padded.append('#').append("x".repeat(length - 2)).append('\n');
            remaining -= length;
        }
        return padded.toString();
    }

    private static void generated(Fixture fixture, byte[] input) throws IOException {
        CompositionRestrictedSettings.validateGeneratedFile(fixture.role(), fixture.path(), input);
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static void failure(Executable action) {
        IOException exception = assertThrows(IOException.class, action);
        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.matches("COMPOSE_(?:SETTINGS|CONFIG)_[A-Z0-9_]+"), message);
        assertNull(exception.getCause(), "Rejected content must not survive in parser exception causes");
        assertFalse(message.contains(SECRET));
        assertFalse(message.contains("\n"));
        assertFalse(message.contains("\r"));
    }
}
