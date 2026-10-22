package dev.legacybridge.validation.composition;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Fake-platform tests only. Every fixture/artifact and captured behavior here is synthetic, native NOT_RUN. */
class ProbeSessionTest {
    private static final String ID = "run-unit";
    private static final byte[] SOURCE = bytes("synthetic-source-NOT_RUN");
    private static final byte[] TARGET = bytes("synthetic-target-NOT_RUN");
    private static final byte[] RUNTIME = bytes("synthetic-runtime-NOT_RUN");
    private static final byte[] RECEIPT = bytes("synthetic-receipt-NOT_RUN");
    private static final String SETTINGS = "level-name=world\nserver-ip=127.0.0.1\nenable-rcon=false\nenable-query=false\n";
    @TempDir Path temporary;
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String read(Path file) throws IOException { return new String(Files.readAllBytes(file), StandardCharsets.UTF_8); }
    private static String hash(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }

    private final class Fixture {
        final String role;
        final Path game, fixture, probe, runtime;
        final ProbeSession.Pins pins;
        final FakePlatform platform;
        final ByteArrayOutputStream outBytes = new ByteArrayOutputStream(), errorBytes = new ByteArrayOutputStream();
        final PrintStream out, error;
        Fixture(String role) throws Exception {
            this.role = role;
            game = Files.createDirectories(temporary.resolve("composition-probe-runs").resolve(ID).resolve(role.toLowerCase(java.util.Locale.ROOT)));
            Files.createDirectory(game.resolve("mods")); Files.createDirectory(game.resolve("composition-observations"));
            Files.write(game.resolve("composition-probe-authorized.txt"), bytes(ID + "\n"));
            Files.write(game.resolve("server.properties"), bytes(SETTINGS));
            fixture = Files.write(game.resolve("mods/fixture.jar"), role.equals("SOURCE") ? SOURCE : TARGET);
            probe = Files.write(game.resolve("mods/probe.jar"), bytes("synthetic-probe-NOT_RUN"));
            runtime = game.resolve("mods/runtime.jar");
            if (role.equals("TARGET")) {
                Files.write(runtime, RUNTIME); Files.write(game.resolve("composition-receipt.json"), RECEIPT);
            }
            pins = new ProbeSession.Pins(hash(SOURCE), hash(TARGET), hash(RUNTIME), hash(RECEIPT));
            platform = new FakePlatform(this);
            out = new PrintStream(outBytes, true, "UTF-8"); error = new PrintStream(errorBytes, true, "UTF-8");
        }
        ProbeSession.Config arm() throws IOException { return ProbeSession.arm(game, role, ID, game, pins); }
        void run(ProbeSession.Config config) throws Exception { ProbeSession.run(config, platform, out, error); }
        Path result() { return game.resolve("composition-observations").resolve(ID + ".json"); }
        String output() { return new String(outBytes.toByteArray(), StandardCharsets.UTF_8); }
        String errors() { return new String(errorBytes.toByteArray(), StandardCharsets.UTF_8); }
        IOException blockedBeforeCapture(ProbeSession.Config config) {
            IOException failure = assertThrows(IOException.class, () -> run(config));
            assertEquals(0, platform.captures); assertEquals(1, platform.shutdowns);
            assertFalse(Files.exists(result())); assertFalse(output().contains("CAPTURE_OK"));
            assertTrue(errors().contains("LEGACYBRIDGE_COMPOSE_CAPTURE_FAILED"));
            return failure;
        }
    }

    private static final class FakePlatform implements ProbeSession.Platform {
        final Map<String, Object> environment;
        final Map<String, String> mods = new LinkedHashMap<String, String>();
        final Map<String, Path> paths = new LinkedHashMap<String, Path>();
        Map<String, Object> behavior = ProbeJson.object("syntheticOnly", true, "nativeExecution", "NOT_RUN");
        Action onCapture = () -> { }, onIsolation = () -> { }, onShutdown = () -> { };
        int captures, shutdowns, isolationChecks;
        FakePlatform(Fixture fixture) {
            boolean source = fixture.role.equals("SOURCE");
            environment = ProbeJson.object("minecraft", source ? "1.16.5" : "1.20.1", "forge", source ? "36.2.42" : "47.4.23",
                    "javaMajor", 17, "physicalSide", "DEDICATED_SERVER", "logicalSide", "SERVER", "serverThread", true);
            mods.put("minecraft", (String) environment.get("minecraft")); mods.put("forge", (String) environment.get("forge"));
            mods.put(ProbeSession.FIXTURE_ID, "1.0.0"); mods.put(ProbeSession.MOD_ID, "1.0.0");
            paths.put(ProbeSession.FIXTURE_ID, fixture.fixture); paths.put(ProbeSession.MOD_ID, fixture.probe);
            if (!source) { mods.put("legacybridge_runtime", "0.1.1-SNAPSHOT"); paths.put("legacybridge_runtime", fixture.runtime); }
        }
        @Override public Map<String, Object> environment() { return environment; }
        @Override public Map<String, String> loadedMods() { return mods; }
        @Override public Path modFile(String id) { return paths.get(id); }
        @Override public void assertIsolated() throws Exception { isolationChecks++; onIsolation.run(); }
        @Override public Map<String, Object> captureBehavior() throws Exception { captures++; onCapture.run(); return behavior; }
        @Override public void shutdown() throws Exception { shutdowns++; onShutdown.run(); }
    }

    @Test void sourceSuccessIsBoundToActualPublishedBytesAndHasNullRuntimeAndReceipt() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.run(fixture.arm());
        String json = read(fixture.result());
        assertTrue(json.contains("\"role\":\"SOURCE\""));
        assertTrue(json.contains("\"runtimeSha256\":null")); assertTrue(json.contains("\"receiptSha256\":null"));
        assertTrue(json.contains("\"fixtureSha256\":\"" + hash(SOURCE) + "\""));
        assertTrue(json.contains("\"probeSha256\":\"" + ProbeFiles.sha256(fixture.probe) + "\""));
        assertTrue(json.contains("\"nativeExecution\":\"NOT_RUN\""));
        assertEquals("LEGACYBRIDGE_COMPOSE_CAPTURE_OK run=" + ID + " sha256=" + ProbeFiles.sha256(fixture.result()) + System.lineSeparator(), fixture.output());
        assertEquals("", fixture.errors()); assertEquals(1, fixture.platform.captures);
        assertEquals(2, fixture.platform.isolationChecks); assertEquals(1, fixture.platform.shutdowns);
        assertArrayEquals(SOURCE, Files.readAllBytes(fixture.fixture));
    }

    @Test void targetSuccessBindsActualRuntimeAndReceiptWithoutClaimingNativeTestExecution() throws Exception {
        Fixture fixture = new Fixture("TARGET"); fixture.run(fixture.arm());
        String json = read(fixture.result());
        assertTrue(json.contains("\"role\":\"TARGET\""));
        assertTrue(json.contains("\"runtimeSha256\":\"" + hash(RUNTIME) + "\""));
        assertTrue(json.contains("\"receiptSha256\":\"" + hash(RECEIPT) + "\""));
        assertTrue(json.contains("\"fixtureSha256\":\"" + hash(TARGET) + "\""));
        assertTrue(json.contains("\"forge\":\"47.4.23\""));
        assertTrue(fixture.output().contains("sha256=" + ProbeFiles.sha256(fixture.result())));
        assertEquals(1, fixture.platform.shutdowns); assertEquals("", fixture.errors());
    }

    @Test void armRejectsWrongRoleAndUnsafeRunIds() throws Exception {
        Fixture fixture = new Fixture("SOURCE");
        for (String role : new String[]{null, "source", "CLIENT", ""})
            assertThrows(IOException.class, () -> ProbeSession.arm(fixture.game, role, ID, fixture.game, fixture.pins));
        for (String id : new String[]{null, "run-", "../run-unit", "run-UPPER", "run-unit.json", ""})
            assertThrows(IOException.class, () -> ProbeSession.arm(fixture.game, "SOURCE", id, fixture.game, fixture.pins));
        assertEquals(0, fixture.platform.captures); assertEquals(0, fixture.platform.shutdowns);
    }

    @Test void workingDirectoryMustExactlyMatchTheExplicitDisposableGameDirectory() throws Exception {
        Fixture fixture = new Fixture("SOURCE");
        assertTrue(assertThrows(IOException.class, () -> ProbeSession.arm(fixture.game, "SOURCE", ID, temporary, fixture.pins))
                .getMessage().contains("WORKING_DIRECTORY"));
    }

    @Test void disposableParentAndRoleLayoutCannotBeBypassed() throws Exception {
        Fixture fixture = new Fixture("SOURCE");
        Path wrong = Files.createDirectories(temporary.resolve("normal-game/source"));
        assertTrue(assertThrows(IOException.class, () -> ProbeSession.arm(wrong, "SOURCE", ID, wrong, fixture.pins))
                .getMessage().contains("DISPOSABLE_LAYOUT"));
        assertTrue(assertThrows(IOException.class, () -> ProbeSession.arm(fixture.game, "TARGET", ID, fixture.game, fixture.pins))
                .getMessage().contains("DISPOSABLE_LAYOUT"));
        assertTrue(assertThrows(IOException.class, () -> ProbeSession.arm(fixture.game, "SOURCE", "run-other", fixture.game, fixture.pins))
                .getMessage().contains("DISPOSABLE_LAYOUT"));
    }

    @Test void nullRelativeUnnormalizedAndFileGameDirectoriesAreRejected() throws Exception {
        Fixture fixture = new Fixture("SOURCE");
        Path file = Files.write(temporary.resolve("not-directory"), bytes("user"));
        Path[] invalid = {null, Paths.get("source"), fixture.game.resolve("x/../"), file};
        for (Path path : invalid) assertThrows(IOException.class, () -> ProbeSession.arm(path, "SOURCE", ID, path, fixture.pins));
        assertEquals("user", read(file));
    }

    @Test void existingWorldDirectoryIsNeverUsed() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); Path world = Files.createDirectory(fixture.game.resolve("world"));
        Path sentinel = Files.write(world.resolve("user.dat"), bytes("preserve"));
        assertTrue(assertThrows(IOException.class, fixture::arm).getMessage().contains("EXISTING_WORLD"));
        assertEquals("preserve", read(sentinel));
    }

    @Test void existingFileNamedWorldAlsoBlocksWithoutModification() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); Path world = Files.write(fixture.game.resolve("world"), bytes("preserve"));
        assertTrue(assertThrows(IOException.class, fixture::arm).getMessage().contains("EXISTING_WORLD"));
        assertEquals("preserve", read(world));
    }

    @Test void missingAuthorizationDoesNotArmOrCapture() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); Files.delete(fixture.game.resolve("composition-probe-authorized.txt"));
        assertThrows(IOException.class, fixture::arm); assertEquals(0, fixture.platform.captures);
    }

    @Test void authorizationRequiresExactRunIdAndLfWithoutAdditionalConsentText() throws Exception {
        Fixture fixture = new Fixture("SOURCE");
        for (String text : new String[]{ID, ID + "\r\n", "run-other\n", ID + "\nextra", ""}) {
            Files.write(fixture.game.resolve("composition-probe-authorized.txt"), bytes(text));
            assertTrue(assertThrows(IOException.class, fixture::arm).getMessage().contains("AUTHORIZATION_MARKER"));
        }
    }

    @Test void authorizationRejectsInvalidUtf8AndOversizedConfiguration() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); Path marker = fixture.game.resolve("composition-probe-authorized.txt");
        Files.write(marker, new byte[]{(byte) 0xc3, 0x28}); assertThrows(IOException.class, fixture::arm);
        Files.write(marker, new byte[8193]);
        assertTrue(assertThrows(IOException.class, fixture::arm).getMessage().contains("CONFIG_SIZE"));
    }

    @Test void propertiesRequireLoopbackAndNoRemoteAdministration() throws Exception {
        Fixture fixture = new Fixture("SOURCE");
        String[] invalid = {SETTINGS.replace("level-name=world", "level-name=existing-world"),
                SETTINGS.replace("127.0.0.1", "0.0.0.0"), SETTINGS.replace("127.0.0.1", ""),
                SETTINGS.replace("enable-rcon=false", "enable-rcon=true"), SETTINGS.replace("enable-query=false", "enable-query=true"),
                SETTINGS.replace("enable-query=false\n", ""), SETTINGS.replace("enable-rcon=false\n", "")};
        for (String properties : invalid) {
            Files.write(fixture.game.resolve("server.properties"), bytes(properties));
            assertThrows(IOException.class, fixture::arm);
        }
    }

    @Test void duplicatePropertiesAndMalformedUnicodeAreNotSilentlyAccepted() throws Exception {
        Fixture fixture = new Fixture("SOURCE");
        for (String properties : new String[]{SETTINGS + "server-ip=127.0.0.1\n", SETTINGS + "extra=\\uZZZZ\n"}) {
            Files.write(fixture.game.resolve("server.properties"), bytes(properties));
            assertTrue(assertThrows(IOException.class, fixture::arm).getMessage().contains("OBS_PROPERTIES"));
        }
    }

    @Test void missingOrOversizedPropertiesBlockArm() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); Path properties = fixture.game.resolve("server.properties");
        Files.delete(properties); assertThrows(IOException.class, fixture::arm);
        Files.write(properties, new byte[8193]);
        assertTrue(assertThrows(IOException.class, fixture::arm).getMessage().contains("CONFIG_SIZE"));
    }

    @Test void missingOrFileObservationDirectoryIsNotCreatedOrReplaced() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); Path observations = fixture.game.resolve("composition-observations");
        Files.delete(observations); assertThrows(IOException.class, fixture::arm); assertFalse(Files.exists(observations));
        Files.write(observations, bytes("preserve")); assertThrows(IOException.class, fixture::arm); assertEquals("preserve", read(observations));
    }

    @Test void missingOrFileModsDirectoryIsNotCreatedOrReplaced() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); Path mods = fixture.game.resolve("mods");
        Path retained = fixture.game.resolve("retained-test-mods"); Files.move(mods, retained);
        assertThrows(IOException.class, fixture::arm); assertFalse(Files.exists(mods));
        Files.write(mods, bytes("preserve")); assertThrows(IOException.class, fixture::arm);
        assertEquals("preserve", read(mods)); assertArrayEquals(SOURCE, Files.readAllBytes(retained.resolve("fixture.jar")));
    }

    @Test void existingObservationBlocksBeforeNativeCaptureAndPreservesBytes() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); Files.write(fixture.result(), bytes("preserve"));
        assertTrue(assertThrows(IOException.class, fixture::arm).getMessage().contains("EXISTING_OBSERVATION"));
        assertEquals("preserve", read(fixture.result())); assertEquals(0, fixture.platform.captures);
    }

    @Test void sourceReceiptIsExplicitlyForbiddenAtArm() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); Files.write(fixture.game.resolve("composition-receipt.json"), RECEIPT);
        assertTrue(assertThrows(IOException.class, fixture::arm).getMessage().contains("SOURCE_RECEIPT"));
    }

    @Test void targetReceiptMustExistAndMatchTheReviewedHash() throws Exception {
        Fixture fixture = new Fixture("TARGET"); Path receipt = fixture.game.resolve("composition-receipt.json");
        Files.write(receipt, bytes("unknown"));
        assertTrue(assertThrows(IOException.class, fixture::arm).getMessage().contains("RECEIPT_PIN"));
        Files.delete(receipt); assertThrows(IOException.class, fixture::arm);
    }

    @Test void changedAuthorizationAfterArmBlocksBeforeCaptureAndStillShutsDown() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); ProbeSession.Config config = fixture.arm();
        Files.write(fixture.game.resolve("composition-probe-authorized.txt"), bytes("run-changed\n"));
        assertTrue(fixture.blockedBeforeCapture(config).getMessage().contains("AUTHORIZATION_DRIFT"));
    }

    @Test void changedAuthorizationDuringCaptureBlocksPublicationAndStillShutsDown() throws Exception {
        Fixture fixture = new Fixture("SOURCE");
        fixture.platform.onCapture = () -> Files.write(fixture.game.resolve("composition-probe-authorized.txt"), bytes("run-changed\n"));
        assertTrue(assertThrows(IOException.class, () -> fixture.run(fixture.arm())).getMessage().contains("AUTHORIZATION_DRIFT"));
        assertEquals(1, fixture.platform.captures); assertEquals(1, fixture.platform.shutdowns);
        assertFalse(Files.exists(fixture.result())); assertFalse(fixture.output().contains("CAPTURE_OK"));
    }

    @Test void sourceReceiptIntroducedAfterArmBlocksBeforeCapture() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); ProbeSession.Config config = fixture.arm();
        Files.write(fixture.game.resolve("composition-receipt.json"), RECEIPT);
        assertTrue(fixture.blockedBeforeCapture(config).getMessage().contains("SOURCE_RECEIPT"));
    }

    @Test void sourceReceiptIntroducedDuringCaptureBlocksPublication() throws Exception {
        Fixture fixture = new Fixture("SOURCE");
        fixture.platform.onCapture = () -> Files.write(fixture.game.resolve("composition-receipt.json"), RECEIPT);
        assertTrue(assertThrows(IOException.class, () -> fixture.run(fixture.arm())).getMessage().contains("SOURCE_RECEIPT"));
        assertEquals(1, fixture.platform.captures); assertEquals(1, fixture.platform.shutdowns);
        assertFalse(Files.exists(fixture.result())); assertFalse(fixture.output().contains("CAPTURE_OK"));
    }

    @Test void liveIsolationFailureBlocksBeforeCaptureAndShutsDown() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.onIsolation = () -> { throw new IOException("synthetic live isolation failure"); };
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("isolation failure"));
    }

    @Test void sourceVersionMismatchBlocksBeforeCapture() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.environment.put("minecraft", "1.16.4");
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("ENVIRONMENT"));
    }

    @Test void targetForgeMismatchBlocksBeforeCapture() throws Exception {
        Fixture fixture = new Fixture("TARGET"); fixture.platform.environment.put("forge", "47.4.24");
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("ENVIRONMENT"));
    }

    @Test void wrongPhysicalSideBlocksBeforeCapture() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.environment.put("physicalSide", "CLIENT");
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("ENVIRONMENT"));
    }

    @Test void wrongLogicalSideBlocksBeforeCapture() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.environment.put("logicalSide", "CLIENT");
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("ENVIRONMENT"));
    }

    @Test void wrongThreadBlocksBeforeCapture() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.environment.put("serverThread", false);
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("ENVIRONMENT"));
    }

    @Test void wrongJavaBlocksBeforeCaptureEvenForSourceClassfiles() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.environment.put("javaMajor", 8);
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("ENVIRONMENT"));
    }

    @Test void unknownEnvironmentFieldsDoNotPassAsExactEvidence() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.environment.put("unknown", true);
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("ENVIRONMENT"));
    }

    @Test void additionalLoadedModBlocksTheCohortBeforeCapture() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.mods.put("unknown", "1.0.0");
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("MOD_COHORT"));
    }

    @Test void missingLoadedFixtureBlocksTheCohortBeforeCapture() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.mods.remove(ProbeSession.FIXTURE_ID);
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("MOD_COHORT"));
    }

    @Test void wrongLoadedFixtureVersionBlocksTheCohortBeforeCapture() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.mods.put(ProbeSession.FIXTURE_ID, "2.0.0");
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("MOD_COHORT"));
    }

    @Test void missingRuntimeBlocksTargetBeforeCapture() throws Exception {
        Fixture fixture = new Fixture("TARGET"); fixture.platform.mods.remove("legacybridge_runtime");
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("MOD_COHORT"));
    }

    @Test void wrongRuntimeVersionBlocksTargetBeforeCapture() throws Exception {
        Fixture fixture = new Fixture("TARGET"); fixture.platform.mods.put("legacybridge_runtime", "0.1.0");
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("MOD_COHORT"));
    }

    @Test void loadedArtifactOutsideModsIsNotSubstitutedByMatchingBytes() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); Path outside = Files.write(temporary.resolve("fixture.jar"), SOURCE);
        fixture.platform.paths.put(ProbeSession.FIXTURE_ID, outside);
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("LOADED_MOD_PATH"));
        assertArrayEquals(SOURCE, Files.readAllBytes(outside));
    }

    @Test void nestedModPathIsNotTreatedAsTheDirectLoadedArtifact() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); Path nested = Files.createDirectory(fixture.game.resolve("mods/nested"));
        fixture.platform.paths.put(ProbeSession.FIXTURE_ID, Files.write(nested.resolve("fixture.jar"), SOURCE));
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("LOADED_MOD_PATH"));
    }

    @Test void nullLoadedPathBlocksBeforeCapture() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.paths.put(ProbeSession.FIXTURE_ID, null);
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("LOADED_MOD_PATH"));
    }

    @Test void directoryMasqueradingAsLoadedJarBlocksBeforeCapture() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); Path fake = Files.createDirectory(fixture.game.resolve("mods/directory.jar"));
        fixture.platform.paths.put(ProbeSession.FIXTURE_ID, fake);
        fixture.blockedBeforeCapture(fixture.arm()); assertTrue(Files.isDirectory(fake));
    }

    @Test void hardLinkedArtifactAliasesBlockEvenWithDifferentNames() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); Path alias = fixture.game.resolve("mods/alias.jar");
        Files.createLink(alias, fixture.fixture); fixture.platform.paths.put(ProbeSession.MOD_ID, alias);
        assertTrue(fixture.blockedBeforeCapture(fixture.arm()).getMessage().contains("MOD_ALIAS"));
        assertArrayEquals(SOURCE, Files.readAllBytes(alias));
    }

    @Test void changedFixtureBeforeCaptureDoesNotPassItsReviewedPin() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); ProbeSession.Config config = fixture.arm(); Files.write(fixture.fixture, bytes("changed"));
        assertTrue(fixture.blockedBeforeCapture(config).getMessage().contains("FIXTURE_PIN"));
    }

    @Test void changedRuntimeBeforeCaptureDoesNotPassItsReviewedPin() throws Exception {
        Fixture fixture = new Fixture("TARGET"); ProbeSession.Config config = fixture.arm(); Files.write(fixture.runtime, bytes("changed"));
        assertTrue(fixture.blockedBeforeCapture(config).getMessage().contains("TARGET_PIN"));
    }

    @Test void changedReceiptAfterArmBlocksBeforeCapture() throws Exception {
        Fixture fixture = new Fixture("TARGET"); ProbeSession.Config config = fixture.arm();
        Files.write(fixture.game.resolve("composition-receipt.json"), bytes("changed"));
        assertTrue(fixture.blockedBeforeCapture(config).getMessage().contains("TARGET_PIN"));
    }

    @Test void changedFixtureDuringCaptureBlocksPublication() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.onCapture = () -> Files.write(fixture.fixture, bytes("changed"));
        assertTrue(assertThrows(IOException.class, () -> fixture.run(fixture.arm())).getMessage().contains("FIXTURE_PIN"));
        assertFalse(Files.exists(fixture.result())); assertEquals(1, fixture.platform.shutdowns);
    }

    @Test void changedProbeDuringCaptureBlocksArtifactBinding() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.onCapture = () -> Files.write(fixture.probe, bytes("changed"));
        assertTrue(assertThrows(IOException.class, () -> fixture.run(fixture.arm())).getMessage().contains("ARTIFACT_DRIFT"));
        assertFalse(Files.exists(fixture.result())); assertEquals(1, fixture.platform.shutdowns);
    }

    @Test void changedRuntimeDuringCaptureBlocksPublication() throws Exception {
        Fixture fixture = new Fixture("TARGET"); fixture.platform.onCapture = () -> Files.write(fixture.runtime, bytes("changed"));
        assertTrue(assertThrows(IOException.class, () -> fixture.run(fixture.arm())).getMessage().contains("TARGET_PIN"));
        assertFalse(Files.exists(fixture.result())); assertEquals(1, fixture.platform.shutdowns);
    }

    @Test void changedReceiptDuringCaptureBlocksPublication() throws Exception {
        Fixture fixture = new Fixture("TARGET");
        fixture.platform.onCapture = () -> Files.write(fixture.game.resolve("composition-receipt.json"), bytes("changed"));
        assertTrue(assertThrows(IOException.class, () -> fixture.run(fixture.arm())).getMessage().contains("TARGET_PIN"));
        assertFalse(Files.exists(fixture.result())); assertEquals(1, fixture.platform.shutdowns);
    }

    @Test void changedLoadedPathDuringCaptureIsRejectedEvenWithIdenticalBytes() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); Path replacement = Files.write(fixture.game.resolve("mods/other-probe.jar"), Files.readAllBytes(fixture.probe));
        fixture.platform.onCapture = () -> fixture.platform.paths.put(ProbeSession.MOD_ID, replacement);
        assertTrue(assertThrows(IOException.class, () -> fixture.run(fixture.arm())).getMessage().contains("LOADED_PATH_DRIFT"));
        assertFalse(Files.exists(fixture.result())); assertEquals(1, fixture.platform.shutdowns);
    }

    @Test void changedEnvironmentDuringCaptureIsRejected() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.onCapture = () -> fixture.platform.environment.put("serverThread", false);
        assertTrue(assertThrows(IOException.class, () -> fixture.run(fixture.arm())).getMessage().contains("ENVIRONMENT_DRIFT"));
        assertFalse(Files.exists(fixture.result())); assertEquals(1, fixture.platform.shutdowns);
    }

    @Test void changedCohortDuringCaptureIsRejected() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.onCapture = () -> fixture.platform.mods.put("unknown", "1");
        assertTrue(assertThrows(IOException.class, () -> fixture.run(fixture.arm())).getMessage().contains("ENVIRONMENT_DRIFT"));
        assertFalse(Files.exists(fixture.result())); assertEquals(1, fixture.platform.shutdowns);
    }

    @Test void isolationIsRecheckedAfterCapture() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.onIsolation = () -> {
            if (fixture.platform.isolationChecks == 2) throw new IOException("isolation changed after capture");
        };
        assertTrue(assertThrows(IOException.class, () -> fixture.run(fixture.arm())).getMessage().contains("isolation changed"));
        assertFalse(Files.exists(fixture.result())); assertEquals(1, fixture.platform.shutdowns);
    }

    @Test void captureFailureIsLoggedRethrownAndFollowedByShutdownWithoutOutput() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); IOException cause = new IOException("synthetic capture failure");
        fixture.platform.onCapture = () -> { throw cause; };
        assertSame(cause, assertThrows(IOException.class, () -> fixture.run(fixture.arm())));
        assertTrue(fixture.errors().contains("synthetic capture failure")); assertFalse(fixture.output().contains("CAPTURE_OK"));
        assertEquals(1, fixture.platform.shutdowns); assertFalse(Files.exists(fixture.result()));
    }

    @Test void captureErrorAlsoTriggersShutdownAndIsRethrownNotIgnored() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); AssertionError cause = new AssertionError("synthetic capture error");
        fixture.platform.onCapture = () -> { throw cause; };
        assertSame(cause, assertThrows(AssertionError.class, () -> fixture.run(fixture.arm())));
        assertEquals(1, fixture.platform.shutdowns); assertTrue(fixture.errors().contains("synthetic capture error"));
    }

    @Test void captureAndShutdownFailuresPreserveBothCauses() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); IOException capture = new IOException("capture failed"), shutdown = new IOException("shutdown failed");
        fixture.platform.onCapture = () -> { throw capture; }; fixture.platform.onShutdown = () -> { throw shutdown; };
        assertSame(capture, assertThrows(IOException.class, () -> fixture.run(fixture.arm())));
        assertArrayEquals(new Throwable[]{shutdown}, capture.getSuppressed());
        assertTrue(fixture.errors().contains("LEGACYBRIDGE_COMPOSE_CAPTURE_FAILED"));
        assertTrue(fixture.errors().contains("LEGACYBRIDGE_COMPOSE_SHUTDOWN_FAILED")); assertEquals(1, fixture.platform.shutdowns);
    }

    @Test void shutdownFailureIsRethrownAndLoggedWithoutRemovingCompletedCapture() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); IOException shutdown = new IOException("shutdown failed");
        fixture.platform.onShutdown = () -> { throw shutdown; };
        assertSame(shutdown, assertThrows(IOException.class, () -> fixture.run(fixture.arm())));
        assertTrue(Files.exists(fixture.result())); assertTrue(fixture.output().contains(ProbeFiles.sha256(fixture.result())));
        assertTrue(fixture.errors().contains("LEGACYBRIDGE_COMPOSE_SHUTDOWN_FAILED"));
    }

    @Test void lateObservationConflictPreservesUnknownBytesAndDoesNotReportSuccess() throws Exception {
        Fixture fixture = new Fixture("SOURCE");
        fixture.platform.onCapture = () -> Files.write(fixture.result(), bytes("preserve"), StandardOpenOption.CREATE_NEW);
        assertThrows(IOException.class, () -> fixture.run(fixture.arm()));
        assertEquals("preserve", read(fixture.result())); assertFalse(fixture.output().contains("CAPTURE_OK"));
        assertTrue(fixture.errors().contains("CAPTURE_FAILED")); assertEquals(1, fixture.platform.shutdowns);
    }

    @Test void missingOutputDirectoryAtPublicationThrowsLogsAndShutsDown() throws Exception {
        Fixture fixture = new Fixture("SOURCE");
        fixture.platform.onCapture = () -> Files.delete(fixture.game.resolve("composition-observations"));
        assertThrows(IOException.class, () -> fixture.run(fixture.arm()));
        assertFalse(Files.exists(fixture.result())); assertFalse(fixture.output().contains("CAPTURE_OK"));
        assertTrue(fixture.errors().contains("CAPTURE_FAILED")); assertEquals(1, fixture.platform.shutdowns);
    }

    @Test void unsupportedCaptureValueFailsSerializationWithoutPublishing() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); fixture.platform.behavior.put("unknown", new Object());
        assertThrows(IllegalArgumentException.class, () -> fixture.run(fixture.arm()));
        assertFalse(Files.exists(fixture.result())); assertTrue(fixture.errors().contains("OBS_VALUE"));
        assertEquals(1, fixture.platform.shutdowns);
    }

    @Test void loggingFailureAfterPublicationThrowsWithoutDeletingTheValidObservation() throws Exception {
        Fixture fixture = new Fixture("SOURCE");
        PrintStream broken = new PrintStream(new OutputStream() { @Override public void write(int value) throws IOException { throw new IOException("log write failed"); } });
        ProbeSession.Config config = fixture.arm();
        IOException failure = assertThrows(IOException.class, () -> ProbeSession.run(config, fixture.platform, broken, fixture.error));
        assertTrue(failure.getMessage().contains("CAPTURE_LOG_WRITE"));
        assertTrue(Files.exists(fixture.result())); assertEquals(1, fixture.platform.shutdowns);
        assertTrue(fixture.errors().contains("CAPTURE_FAILED")); broken.close();
    }

    @Test void successfulConfigCannotBeUsedTwiceOrOverwriteItsObservation() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); ProbeSession.Config config = fixture.arm(); fixture.run(config);
        byte[] before = Files.readAllBytes(fixture.result());
        assertTrue(assertThrows(IOException.class, () -> fixture.run(config)).getMessage().contains("SESSION_REUSED"));
        assertArrayEquals(before, Files.readAllBytes(fixture.result()));
        assertEquals(1, fixture.platform.captures); assertEquals(1, fixture.platform.shutdowns);
    }

    @Test void failedConfigCannotBeRetriedWithAChangedEnvironment() throws Exception {
        Fixture fixture = new Fixture("SOURCE"); ProbeSession.Config config = fixture.arm();
        fixture.platform.environment.put("serverThread", false); fixture.blockedBeforeCapture(config);
        fixture.platform.environment.put("serverThread", true);
        assertTrue(assertThrows(IOException.class, () -> fixture.run(config)).getMessage().contains("SESSION_REUSED"));
        assertEquals(0, fixture.platform.captures); assertEquals(1, fixture.platform.shutdowns);
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    void actualWindowsJunctionCannotAliasTheGameDirectory() throws Exception {
        Fixture fixture = new Fixture("SOURCE");
        Path aliasRoot = Files.createDirectories(temporary.resolve("aliases/composition-probe-runs").resolve(ID));
        Path alias = aliasRoot.resolve("source");
        Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", alias.toString(), fixture.game.toString())
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
        try {
            assertThrows(IOException.class, () -> ProbeSession.arm(alias, "SOURCE", ID, alias, fixture.pins));
            assertFalse(Files.exists(fixture.result()));
        } finally { Files.delete(alias); }
    }
}
