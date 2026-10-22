package dev.legacybridge.corpus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static dev.legacybridge.corpus.CompositionRunPlanner.Role;

/**
 * Finite generated-config dialect, NOT a general TOML reader or an arming policy.
 * Exact native defaults can enable networking; such defaults are reported, not made safe.
 * No Forge class, original mod, file-config watcher or correction/save routine is invoked.
 * See docs/COMPOSITION_NATIVE_CONFIG_REVIEW.md for the pinned source audit and exclusions.
 */
final class CompositionNativeConfigValidator {
    static final int MAX_BYTES = 65536;
    private CompositionNativeConfigValidator() { }

    static Map<String, Object> validate(Role role, String path, byte[] bytes) throws IOException {
        try {
            require(role != null && path != null && bytes != null && bytes.length <= MAX_BYTES, "INPUT");
            Map<String, String> expected = nativeDefaults(role, path);
            requireExact(role, path, bytes, expected);
            var concerns = new ArrayList<String>();
            if (path.equals("config/fml.toml")) {
                concerns.add("GLOBAL_VERSION_CHECK_ENABLED");
                concerns.add("NATIVE_LOADING_THREADS_AUTOMATIC");
            }
            if (role == Role.TARGET && path.equals("world/serverconfig/forge-server.toml"))
                concerns.add("LAN_ADVERTISEMENT_ENABLED");
            return Map.of("schemaVersion", 1, "policyVersion", "native-config-defaults-v1", "role", role.name(),
                    "path", path, "status", "RECORDED_NATIVE_DEFAULTS_ONLY", "valueCount", expected.size(),
                    "launchConcerns", java.util.List.copyOf(concerns), "executionAllowed", false, "installationAllowed", false);
        } catch (IOException | RuntimeException failure) {
            String code = failure.getMessage();
            throw new IOException(code != null && code.matches("COMPOSE_CONFIG_[A-Z0-9_]+") ? code : "COMPOSE_CONFIG_REJECTED");
        }
    }

    // Shared finite lexer only; the caller must select one of the closed authored schemas.
    // No public API accepts an arbitrary expected map and the default review above is unchanged.
    static void requireExact(Role role, String path, byte[] bytes, Map<String, String> expected) throws IOException {
        require(role != null && path != null && bytes != null && bytes.length <= MAX_BYTES, "INPUT");
        String table = path.equals("config/forge-common.toml") ? "general"
                : path.equals("world/serverconfig/forge-server.toml") ? "server" : "";
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        String[] lines = text.split("\n", -1);
        require(lines.length <= 512, "LINE_COUNT");
        require(!text.endsWith("\r"), "CONTROL");
        Map<String, String> found = new LinkedHashMap<>(); Set<String> headers = new HashSet<>();
        String section = "";
        for (String raw : lines) {
            require(raw.length() <= 4096, "LINE_SIZE");
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            for (int i = 0; i < line.length(); i++)
                require(line.charAt(i) != 127 && (line.charAt(i) >= 32 || line.charAt(i) == '\t'), "CONTROL");
            // Remove comments only outside simple double-quoted strings. Escapes are not in this dialect.
            boolean quoted = false; int end = line.length();
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '#' && !quoted) { end = i; break; }
                if (c == '"') quoted = !quoted;
            }
            line = line.substring(0, end).trim();
            if (line.isEmpty()) continue;
            require(!quoted && line.indexOf('\\') < 0, "DIALECT");
            if (line.startsWith("[")) {
                require(!expected.isEmpty() && !table.isEmpty() && line.equals("[" + table + "]") && headers.add(table), "TABLE");
                section = table; continue;
            }
            require(section.equals(table), "TABLE");
            int equals = line.indexOf('='); require(equals > 0, "ASSIGNMENT");
            String key = line.substring(0, equals).trim(), value = line.substring(equals + 1).trim();
            require(key.matches("[A-Za-z][A-Za-z0-9]*"), "KEY");
            require(value.matches("true|false|-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?|\"[A-Za-z0-9_:/.-]*\"|\\[[ \\t]*]"), "VALUE_SYNTAX");
            if (value.startsWith("[")) value = "[]";
            String full = section.isEmpty() ? key : section + "." + key;
            require(found.putIfAbsent(full, value) == null, "DUPLICATE_KEY");
            require(expected.containsKey(full), "UNKNOWN_KEY");
            require(expected.get(full).equals(value), "NONDEFAULT_VALUE");
        }
        require(found.keySet().equals(expected.keySet()), "MISSING_KEY");
    }

    static Map<String, String> nativeDefaults(Role role, String path) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        switch (path) {
            case "config/fml.toml" -> {
                values.put("versionCheck", "true"); values.put("defaultConfigPath", "\"defaultconfigs\"");
                values.put("maxThreads", "-1");
                if (role == Role.SOURCE) values.put("splashscreen", "true");
                else {
                    values.put("earlyWindowControl", "true"); values.put("earlyWindowProvider", "\"fmlearlywindow\"");
                    values.put("earlyWindowWidth", "854"); values.put("earlyWindowHeight", "480");
                    values.put("earlyWindowFBScale", "1"); values.put("earlyWindowMaximized", "false");
                    values.put("disableOptimizedDFU", "true"); values.put("earlyWindowSkipGLVersions", "[]");
                    values.put("earlyWindowLogHelpMessage", "true"); values.put("earlyWindowSquir", "false");
                    values.put("earlyWindowShowCPU", "false");
                }
            }
            case "config/forge-common.toml" -> {
                require(role == Role.SOURCE, "UNREGISTERED_CONFIGURATION");
                values.put("general.defaultWorldType", "\"default\"");
            }
            case "world/serverconfig/forge-server.toml" -> {
                values.put("server.removeErroringEntities", "false"); values.put("server.fullBoundingBoxLadders", "false");
                values.put("server.zombieBaseSummonChance", "0.1"); values.put("server.zombieBabyChance", "0.05");
                if (role == Role.SOURCE) {
                    values.put("server.removeErroringTileEntities", "false"); values.put("server.logCascadingWorldGeneration", "true");
                    values.put("server.fixVanillaCascading", "false"); values.put("server.dimensionUnloadQueueDelay", "0");
                    values.put("server.treatEmptyTagsAsAir", "false"); values.put("server.fixAdvancementLoading", "true");
                } else {
                    values.put("server.removeErroringBlockEntities", "false");
                    values.put("server.permissionHandler", "\"forge:default_handler\"");
                    values.put("server.advertiseDedicatedServerToLan", "true"); values.put("server.useItemWithDurationZero", "false");
                }
            }
            default -> throw new IOException("COMPOSE_CONFIG_PATH");
        }
        return Map.copyOf(values);
    }
    private static void require(boolean condition, String code) throws IOException {
        if (!condition) throw new IOException("COMPOSE_CONFIG_" + code);
    }
}
