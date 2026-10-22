package dev.legacybridge.validation.composition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Explicit single-use QA, compiled separately from the product and static converter. */
public final class ProbeSession {
    public static final String MOD_ID = "legacybridge_composition_probe", FIXTURE_ID = "legacybridge_composed";
    private static final Pins REVIEWED = new Pins(
            "873013592b97449b9fffd3f569cc7b7fc3309fcc9b1d241f5a230d1d45731212",
            "328c475e98e80c022aeb044d55cdf1b375e62db89c0602da8fc3c00fb495eca2",
            "4e74a86169bb05d8ce299c000d4945c1bcf3746f53c8b3684ee37fe67a9b1b84",
            "ed976a181fac59e2453d3dd110db6c0d89b3d7565a22e37d6df4ab4bf508e9bd");
    private ProbeSession() { }
    public interface Platform {
        Map<String, Object> environment(); Map<String, String> loadedMods(); Path modFile(String id);
        void assertIsolated() throws Exception; Map<String, Object> captureBehavior() throws Exception;
        void shutdown() throws Exception;
    }
    static final class Pins {
        final String source, target, runtime, receipt;
        Pins(String source, String target, String runtime, String receipt) {
            this.source = source; this.target = target; this.runtime = runtime; this.receipt = receipt;
        }
    }
    public static final class Config {
        private final Path gameDir;
        private final String role, id, authorizationHash;
        private final Pins pins;
        private boolean used;
        private Config(Path gameDir, String role, String id, Pins pins, String authorizationHash) {
            this.gameDir = gameDir; this.role = role; this.id = id; this.pins = pins;
            this.authorizationHash = authorizationHash;
        }
        private synchronized void claim() throws IOException { require(!used, "SESSION_REUSED"); used = true; }
    }
    public static Config arm(Path gameDir, String role, String id) throws IOException {
        return arm(gameDir, role, id, Paths.get("").toAbsolutePath().normalize(), REVIEWED);
    }
    // Controlled test seam: production callers always use the pinned artifacts above and actual cwd.
    static Config arm(Path gameDir, String role, String id, Path cwd, Pins pins) throws IOException {
        require("SOURCE".equals(role) || "TARGET".equals(role), "ROLE");
        require(id != null && id.matches("run-[a-z0-9][a-z0-9-]{0,40}"), "RUN_ID");
        directory(gameDir);
        require(gameDir.equals(cwd), "WORKING_DIRECTORY");
        require(gameDir.getFileName() != null && gameDir.getFileName().toString().equals(role.toLowerCase(java.util.Locale.ROOT))
                && gameDir.getParent() != null && gameDir.getParent().getFileName().toString().equals(id)
                && gameDir.getParent().getParent() != null
                && gameDir.getParent().getParent().getFileName() != null
                && gameDir.getParent().getParent().getFileName().toString().equals("composition-probe-runs"), "DISPOSABLE_LAYOUT");
        require(!Files.exists(gameDir.resolve("world"), LinkOption.NOFOLLOW_LINKS), "EXISTING_WORLD");
        Path authorization = gameDir.resolve("composition-probe-authorized.txt");
        String authHash = ProbeFiles.sha256(authorization);
        String marker = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(small(authorization))).toString();
        require(marker.equals(id + "\n") && authHash.equals(ProbeFiles.sha256(authorization)), "AUTHORIZATION_MARKER");
        Path settings = gameDir.resolve("server.properties");
        String settingsHash = ProbeFiles.sha256(settings);
        Properties properties = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("OBS_DUPLICATE_PROPERTY");
                return super.put(key, value);
            }
        };
        try { properties.load(new ByteArrayInputStream(small(settings))); }
        catch (IllegalArgumentException invalid) { throw new IOException("OBS_PROPERTIES", invalid); }
        require("world".equals(properties.getProperty("level-name")), "LEVEL_NAME");
        require("127.0.0.1".equals(properties.getProperty("server-ip")), "BIND_ADDRESS");
        require("false".equals(properties.getProperty("enable-rcon")) && "false".equals(properties.getProperty("enable-query")), "REMOTE_ACCESS");
        require(settingsHash.equals(ProbeFiles.sha256(settings)), "SETTINGS_DRIFT");
        directory(gameDir.resolve("mods"));
        directory(gameDir.resolve("composition-observations"));
        require(!Files.exists(gameDir.resolve("composition-observations").resolve(id + ".json"), LinkOption.NOFOLLOW_LINKS), "EXISTING_OBSERVATION");
        if (role.equals("TARGET")) require(pins.receipt.equals(ProbeFiles.sha256(gameDir.resolve("composition-receipt.json"))), "RECEIPT_PIN");
        else require(!Files.exists(gameDir.resolve("composition-receipt.json"), LinkOption.NOFOLLOW_LINKS), "SOURCE_RECEIPT");
        return new Config(gameDir, role, id, pins, authHash);
    }
    public static void run(Config config, Platform platform) throws Exception { run(config, platform, System.out, System.err); }
    static void run(Config config, Platform platform, PrintStream out, PrintStream error) throws Exception {
        config.claim();
        Throwable failure = null;
        try {
            require(config.authorizationHash.equals(ProbeFiles.sha256(config.gameDir.resolve("composition-probe-authorized.txt"))), "AUTHORIZATION_DRIFT");
            // The native server expands its properties on startup; do not compare the pre-load file hash here.
            platform.assertIsolated();
            Map<String, Object> environment = new LinkedHashMap<String, Object>(platform.environment());
            Map<String, String> mods = new LinkedHashMap<String, String>(platform.loadedMods());
            validateEnvironment(config, environment, mods);
            Map<String, Path> paths = loadedPaths(config, platform);
            Map<String, Object> before = bindings(config, paths);
            Map<String, Object> behavior = platform.captureBehavior();
            platform.assertIsolated();
            require(config.authorizationHash.equals(ProbeFiles.sha256(config.gameDir.resolve("composition-probe-authorized.txt"))), "AUTHORIZATION_DRIFT");
            require(environment.equals(platform.environment()) && mods.equals(platform.loadedMods()), "ENVIRONMENT_DRIFT");
            require(paths.equals(loadedPaths(config, platform)), "LOADED_PATH_DRIFT");
            Map<String, Object> after = bindings(config, paths);
            require(before.equals(after), "ARTIFACT_DRIFT");
            Map<String, Object> trace = ProbeJson.object("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_OBSERVATION",
                    "oracle", "composed-foil-cooking-v1", "runId", config.id, "role", config.role, "status", "CAPTURED",
                    "environment", environment, "loadedMods", mods, "before", before, "after", after, "behavior", behavior);
            String digest = ProbeFiles.publish(config.gameDir.resolve("composition-observations"), config.id, ProbeJson.render(trace));
            out.println("LEGACYBRIDGE_COMPOSE_CAPTURE_OK run=" + config.id + " sha256=" + digest);
            out.flush();
            require(!out.checkError(), "CAPTURE_LOG_WRITE");
        } catch (Throwable observed) {
            failure = observed;
            error.println("LEGACYBRIDGE_COMPOSE_CAPTURE_FAILED"); observed.printStackTrace(error); error.flush();
        } finally {
            try { platform.shutdown(); }
            catch (Throwable shutdownFailure) {
                error.println("LEGACYBRIDGE_COMPOSE_SHUTDOWN_FAILED"); shutdownFailure.printStackTrace(error); error.flush();
                if (failure == null) failure = shutdownFailure; else failure.addSuppressed(shutdownFailure);
            }
        }
        if (failure instanceof Exception) throw (Exception) failure;
        if (failure instanceof Error) throw (Error) failure;
        if (failure != null) throw new IllegalStateException("OBS_FAILURE", failure);
    }
    private static Map<String, Path> loadedPaths(Config config, Platform platform) throws IOException {
        Map<String, Path> result = new LinkedHashMap<String, Path>();
        result.put("fixtureSha256", platform.modFile(FIXTURE_ID)); result.put("probeSha256", platform.modFile(MOD_ID));
        if (config.role.equals("TARGET")) result.put("runtimeSha256", platform.modFile("legacybridge_runtime"));
        List<Path> seen = new ArrayList<Path>();
        for (Path path : result.values()) {
            require(path != null && config.gameDir.resolve("mods").equals(path.getParent()), "LOADED_MOD_PATH");
            ProbeFiles.sha256(path);
            for (Path prior : seen) require(!Files.isSameFile(prior, path), "MOD_ALIAS");
            seen.add(path);
        }
        return result;
    }
    private static Map<String, Object> bindings(Config config, Map<String, Path> paths) throws IOException {
        Map<String, Object> result = ProbeJson.object("fixtureSha256", ProbeFiles.sha256(paths.get("fixtureSha256")),
                "probeSha256", ProbeFiles.sha256(paths.get("probeSha256")), "runtimeSha256", null, "receiptSha256", null);
        require(result.get("fixtureSha256").equals(config.role.equals("SOURCE") ? config.pins.source : config.pins.target), "FIXTURE_PIN");
        if (config.role.equals("TARGET")) {
            result.put("runtimeSha256", ProbeFiles.sha256(paths.get("runtimeSha256")));
            result.put("receiptSha256", ProbeFiles.sha256(config.gameDir.resolve("composition-receipt.json")));
            require(config.pins.runtime.equals(result.get("runtimeSha256")) && config.pins.receipt.equals(result.get("receiptSha256")), "TARGET_PIN");
        } else require(!Files.exists(config.gameDir.resolve("composition-receipt.json"), LinkOption.NOFOLLOW_LINKS), "SOURCE_RECEIPT");
        return result;
    }
    private static void validateEnvironment(Config config, Map<String, Object> environment, Map<String, String> mods) throws IOException {
        boolean source = config.role.equals("SOURCE");
        Map<String, Object> expected = ProbeJson.object("minecraft", source ? "1.16.5" : "1.20.1", "forge", source ? "36.2.42" : "47.4.23",
                "javaMajor", 17, "physicalSide", "DEDICATED_SERVER", "logicalSide", "SERVER", "serverThread", true);
        require(environment.equals(expected), "ENVIRONMENT");
        Map<String, String> cohort = new LinkedHashMap<String, String>();
        cohort.put("minecraft", (String) expected.get("minecraft")); cohort.put("forge", (String) expected.get("forge"));
        cohort.put(FIXTURE_ID, "1.0.0"); cohort.put(MOD_ID, "1.0.0");
        if (!source) cohort.put("legacybridge_runtime", "0.1.1-SNAPSHOT");
        require(mods.equals(cohort), "MOD_COHORT");
    }
    private static byte[] small(Path path) throws IOException {
        require(Files.size(path) <= 8192, "CONFIG_SIZE");
        byte[] buffer = new byte[8193]; int count = 0;
        try (java.io.InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            while (count < buffer.length) { int read = input.read(buffer, count, buffer.length - count); if (read == -1) break; count += read; }
        }
        require(count <= 8192, "CONFIG_SIZE"); return Arrays.copyOf(buffer, count);
    }
    private static void directory(Path path) throws IOException {
        require(path != null && path.isAbsolute() && path.equals(path.normalize()), "DIRECTORY");
        for (Path current = path; current != null; current = current.getParent())
            require(Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(current)
                    && current.equals(current.toRealPath()) && current.toRealPath().equals(current.toRealPath(LinkOption.NOFOLLOW_LINKS)), "DIRECTORY_ALIAS");
    }
    private static void require(boolean condition, String code) throws IOException { if (!condition) throw new IOException("OBS_" + code); }
}
