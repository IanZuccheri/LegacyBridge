package dev.legacybridge.corpus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static dev.legacybridge.corpus.CompositionRunPlanner.Role;

/**
 * Closed developer-QA settings, not a general properties/TOML compatibility layer.
 * Legacy API: PropertyManager/ServerProperties + FMLConfig/ForgeConfig (36.2.42).
 * Target API: Settings/DedicatedServerProperties + FMLConfig/ForgeConfig (47.4.23).
 * Transformation: authored configuration proposal only, never mod bytecode or stage mutation.
 * Behavior differences: seed 1 and restricted services are deliberate QA choices, not world equivalence.
 * Known limitations: no native save/consumption proof, process-wide thread quota or network isolation.
 * Provenance and exact consumer audit: docs/COMPOSITION_RESTRICTED_SETTINGS.md.
 */
final class CompositionRestrictedSettings {
    static final String VERSION = "composition-restricted-settings-v1";
    static final String TEMPLATE = "defaultconfigs/forge-server.toml";
    static final String SERVER = "world/serverconfig/forge-server.toml";
    private CompositionRestrictedSettings() { }

    static Map<String, String> files(Role role) throws IOException {
        require(role != null);
        var files = new TreeMap<String, String>();
        files.put("server.properties", render(properties(role), ""));
        files.put("config/fml.toml", render(toml(role, "config/fml.toml"), ""));
        if (role == Role.SOURCE) files.put("config/forge-common.toml", render(toml(role, "config/forge-common.toml"), "general"));
        files.put(TEMPLATE, render(toml(role, SERVER), "server"));
        return Map.copyOf(files);
    }

    static void validatePlannedFile(Role role, String path, byte[] bytes) throws IOException {
        validate(role, path, bytes, true);
    }
    static void validateGeneratedFile(Role role, String path, byte[] bytes) throws IOException {
        validate(role, path, bytes, false);
    }
    private static void validate(Role role, String path, byte[] bytes, boolean planned) throws IOException {
        try {
            require(role != null && path != null && bytes != null && bytes.length <= 65536);
            if (path.equals("server.properties")) {
                Map<String, String> expected = properties(role);
                // Existing parser supplies version-specific encoding, decoded duplicate rejection and budgets.
                // Here even EXTRA KNOWN keys are rejected: the complete active set is exactly 50/56.
                require(CompositionNativeOutputPolicy.validateProperties(role, bytes, expected).equals(expected));
            } else {
                require(path.equals("config/fml.toml") || role == Role.SOURCE && path.equals("config/forge-common.toml")
                        || path.equals(planned ? TEMPLATE : SERVER));
                String registered = path.equals(TEMPLATE) ? SERVER : path;
                CompositionNativeConfigValidator.requireExact(role, registered, bytes, toml(role, registered));
            }
        } catch (IOException | RuntimeException invalid) { throw new IOException("COMPOSE_SETTINGS_INVALID"); }
    }

    static List<Map<String, Object>> changes(Role role) throws IOException {
        require(role != null); var changes = new ArrayList<Map<String, Object>>();
        Map<String, String> selected = properties(role);
        for (var entry : new TreeMap<>(nativePropertyDifferences(role)).entrySet()) {
            String key = entry.getKey();
            changes.add(change("server.properties", key, entry.getValue(), selected.get(key),
                    "restricted-properties-" + key, propertyReason(key)));
        }
        changes.add(change("config/fml.toml", "versionCheck", "true", "false", "restricted-forge-version-check",
                "Return before Forge update HTTP work; authlib and other outbound traffic remain outside this contract."));
        changes.add(change("config/fml.toml", "maxThreads", "-1", "1", "restricted-forge-mod-loading",
                "One mod-loading ForkJoinPool worker; not a process-wide thread limit."));
        if (role == Role.TARGET) changes.add(change(TEMPLATE, "server.advertiseDedicatedServerToLan", "true", "false",
                "restricted-forge-lan-advertisement", "Disable this Forge LAN multicast pinger; no OS egress isolation is claimed."));
        return List.copyOf(changes);
    }
    private static Map<String, Object> change(String path, String key, String before, String after, String rule, String reason) {
        return Map.of("path", path, "key", key, "before", before, "after", after, "rule", rule,
                "confidence", "VERIFIED", "reason", reason);
    }
    private static String propertyReason(String key) {
        return switch (key) {
            case "level-seed" -> "Use fixed nonzero seed 1; SOURCE treats zero as random. Terrain equivalence is NOT verified.";
            case "server-ip", "server-port" -> "Bind the selected role to loopback and its reserved QA port; outbound traffic is not isolated.";
            case "white-list", "enforce-whitelist", "max-players" -> "Retain the reviewed one-slot whitelisted stage policy; empty player lists and zero players still need independent checks.";
            case "spawn-animals", "spawn-monsters", "spawn-npcs", "pvp", "generate-structures" -> "Restrict unrelated QA activity, not converted-mod semantics or a claim of equivalent world generation.";
            case "view-distance", "simulation-distance" -> "Select finite small QA distances; no promise of total memory, chunk or CPU bounds.";
            case "snooper-enabled" -> "Pin the SOURCE stored flag false; the pinned native runtime field is already forced false.";
            case "use-native-transport" -> "Select the native server's Java transport branch, not an OS networking sandbox.";
            case "enable-status", "hide-online-players", "broadcast-console-to-ops", "broadcast-rcon-to-ops" -> "Restrict server status/player visibility and broadcasts; keep online authentication enabled.";
            case "motd" -> "Identify the disposable developer qualification environment.";
            default -> throw new IllegalArgumentException("unregistered settings recipe");
        };
    }
    // Only differences from the exact active native defaults. Legacy keys consumed and removed
    // by getLegacy are deliberately absent, not advertised as retained settings.
    private static Map<String, String> nativePropertyDifferences(Role role) {
        var values = new TreeMap<String, String>();
        for (String key : List.of("broadcast-console-to-ops", "broadcast-rcon-to-ops", "enable-status", "generate-structures",
                "pvp", "spawn-animals", "spawn-monsters", "spawn-npcs", "use-native-transport")) values.put(key, "true");
        for (String key : List.of("enforce-whitelist", "white-list")) values.put(key, "false");
        values.put("level-seed", ""); values.put("max-players", "20"); values.put("motd", "A Minecraft Server");
        values.put("server-ip", ""); values.put("server-port", "25565"); values.put("view-distance", "10");
        if (role == Role.SOURCE) values.put("snooper-enabled", "true");
        else { values.put("hide-online-players", "false"); values.put("simulation-distance", "10"); }
        return Map.copyOf(values);
    }
    private static Map<String, String> toml(Role role, String path) throws IOException {
        var values = new TreeMap<>(CompositionNativeConfigValidator.nativeDefaults(role, path));
        if (path.equals("config/fml.toml")) { values.put("versionCheck", "false"); values.put("maxThreads", "1"); }
        if (role == Role.TARGET && path.equals(SERVER)) values.put("server.advertiseDedicatedServerToLan", "false");
        return Map.copyOf(values);
    }
    private static Map<String, String> properties(Role role) {
        var values = new TreeMap<String, String>();
        String shared = """
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
                hardcore=false
                level-name=world
                level-seed=1
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
        shared.lines().forEach(line -> { int split = line.indexOf('='); values.put(line.substring(0, split), line.substring(split + 1)); });
        values.put("server-port", role == Role.SOURCE ? "25597" : "25598");
        values.put("generator-settings", role == Role.SOURCE ? "" : "{}");
        values.put("level-type", role == Role.SOURCE ? "default" : "minecraft:normal");
        if (role == Role.SOURCE) { values.put("max-build-height", "256"); values.put("snooper-enabled", "false"); }
        else {
            values.put("enforce-secure-profile", "true"); values.put("hide-online-players", "true");
            values.put("initial-disabled-packs", ""); values.put("initial-enabled-packs", "vanilla");
            values.put("max-chained-neighbor-updates", "1000000"); values.put("require-resource-pack", "false");
            values.put("resource-pack-prompt", ""); values.put("simulation-distance", "2");
        }
        return Map.copyOf(values);
    }
    private static String render(Map<String, String> values, String table) {
        var text = new StringBuilder(); if (!table.isEmpty()) text.append('[').append(table).append("]\n");
        for (var entry : new TreeMap<>(values).entrySet()) {
            String key = table.isEmpty() ? entry.getKey() : entry.getKey().substring(table.length() + 1);
            text.append(key).append('=').append(entry.getValue()).append('\n');
        }
        return text.toString();
    }
    private static void require(boolean valid) throws IOException { if (!valid) throw new IOException("COMPOSE_SETTINGS_INVALID"); }
}
