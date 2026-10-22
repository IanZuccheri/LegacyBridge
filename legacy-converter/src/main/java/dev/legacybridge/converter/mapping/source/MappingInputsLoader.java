package dev.legacybridge.converter.mapping.source;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStoppedException;

import net.minecraftforge.srgutils.IMappingBuilder;
import net.minecraftforge.srgutils.IMappingFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Loads local development data, never downloads or executes code from those inputs. */
public final class MappingInputsLoader {
    private static final Map<String, String> VERSIONS = Map.of(
            "legacy.minecraft", "1.16.5", "target.minecraft", "1.20.1",
            "legacy.forge", "36.2.42", "target.forge", "47.4.23");
    private static final List<String> MAPPING_KEYS = List.of(
            "legacy.clientMappings", "legacy.serverMappings", "legacy.srg",
            "target.clientMappings", "target.serverMappings", "target.srg");
    private static final Map<String, String> PINNED_SHA256 = Map.of(
            "legacy.clientMappings", "7931ed6d723eceb1d621d05a76e10ddf643bf468c6ecf1c4ecf377bd72cf8b8c",
            "legacy.serverMappings", "b3732b0f031abce7083cac8e594427e70d2e19674b8de2c682630c39b64228a7",
            "target.clientMappings", "67bfc33ef0c7103f002d6ac134f8eddd3d67e2ce45d266e83816d1a2e2c9b01d",
            "target.serverMappings", "dca153d20defb32cfac3f069c3bf77b3e13c30ae63847637479d3137e099bb72",
            "legacy.srg", "80216a0f4ae79d81a5e11ceccaa97582a1f6a2cf079b7703b88a99a26547b342",
            "target.srg", "f46d1050d8bed9046886b90a0d2fb80b4c6b5120fc35d32e5d37498dbe0a6d2a");
    private final Path gradleHome;

    public MappingInputsLoader() {
        this(defaultGradleHome());
    }

    MappingInputsLoader(Path gradleHome) {
        this.gradleHome = Objects.requireNonNull(gradleHome, "gradleHome");
    }

    public MappingInputs load(Path profileOrNull) throws IOException {
        OperationControl.checkpoint();
        boolean explicit = profileOrNull != null;
        List<String> diagnostics = new ArrayList<>();
        Properties paths = explicit ? profile(profileOrNull) : defaults(diagnostics);
        Path relativeTo = explicit ? profileOrNull.toAbsolutePath().normalize().getParent() : gradleHome;
        String origin = explicit ? "LOCAL_PROFILE" : "PINNED_GRADLE_CACHE";
        List<MappingSource> sources = new ArrayList<>();
        Map<String, IMappingFile> mappings = new java.util.LinkedHashMap<>();
        for (String key : MAPPING_KEYS) {
            OperationControl.checkpoint();
            Path path = resolve(relativeTo, paths.getProperty(key));
            if (!Files.exists(path)) {
                if (explicit) throw new IOException("Mapping profile input " + key + " does not exist: " + path);
                diagnostics.add("MISSING_INPUT: " + key + "; populate the pinned ForgeGradle development cache or supply --mapping-profile");
                mappings.put(key, emptyMapping());
                continue;
            }
            byte[] bytes;
            try (InputStream input = BoundedInputs.open(path, BoundedInputs.MAPPING_BYTES, key)) {
                bytes = BoundedInputs.read(input, BoundedInputs.MAPPING_BYTES, key);
            }
            String digest = BoundedInputs.hash(bytes);
            if (!explicit && !digest.equals(PINNED_SHA256.get(key))) {
                throw new IOException("Pinned mapping input " + key + " has an unexpected SHA-256; refresh the exact development artifact");
            }
            sources.add(new MappingSource(key, digest, origin));
            mappings.put(key, parseMapping(bytes, key));
        }
        IMappingFile legacyOfficial = mergeOfficial(mappings.get("legacy.clientMappings"),
                mappings.get("legacy.serverMappings"), "legacy");
        IMappingFile targetOfficial = mergeOfficial(mappings.get("target.clientMappings"),
                mappings.get("target.serverMappings"), "target");
        String apiOrigin = explicit ? origin : "LOCAL_GRADLE_CACHE";
        ApiIndex legacyApi = readApi(paths, "legacy.apiJars", relativeTo, explicit, apiOrigin, sources, diagnostics);
        ApiIndex targetApi = readApi(paths, "target.apiJars", relativeTo, explicit, apiOrigin, sources, diagnostics);
        if (explicit) {
            diagnostics.add("LOCAL_PROFILE: supplied mappings/API declarations are trusted local development inputs; hashes record provenance, not publisher authentication");
        }
        return new MappingInputs(legacyOfficial, targetOfficial,
                mappings.get("legacy.srg"), mappings.get("target.srg"), legacyApi, targetApi,
                sources, diagnostics, diagnostics.stream().noneMatch(value -> value.startsWith("MISSING_INPUT:")));
    }

    private static ApiIndex readApi(Properties paths, String key, Path relativeTo, boolean explicit,
                                    String origin, List<MappingSource> sources, List<String> diagnostics) throws IOException {
        List<Path> jars = new ArrayList<>();
        for (String value : paths.getProperty(key).split(";", -1)) {
            OperationControl.checkpoint();
            if (value.isBlank()) throw new IOException("Mapping profile " + key + " contains an empty JAR path");
            Path jar = resolve(relativeTo, value);
            if (!Files.exists(jar)) {
                if (explicit) throw new IOException("Mapping profile input " + key + " does not exist: " + jar);
                diagnostics.add("MISSING_INPUT: " + key + "; build the pinned official-mapped Forge development baseline or supply --mapping-profile");
                continue;
            }
            jars.add(jar);
        }
        if (jars.size() > 16) throw new IOException(key + " exceeds the 16 API JAR limit");
        ApiIndex.ReadResult result = ApiIndex.readWithSources(jars, key, origin);
        sources.addAll(result.sources());
        if (!jars.isEmpty() && result.index().classes().isEmpty()) {
            throw new IOException(key + " contains no API class declarations");
        }
        return result.index();
    }

    private static IMappingFile parseMapping(byte[] bytes, String key) throws IOException {
        byte[] text = bytes;
        if (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K') {
            if (!key.endsWith(".srg")) throw new IOException(key + " must be an official ProGuard mapping text file");
            text = joinedTsrg(bytes, key);
        }
        try {
            OperationControl.checkpoint();
            IMappingFile mapping = IMappingFile.load(new ByteArrayInputStream(text) {
                @Override public synchronized int read() {
                    OperationControl.checkpoint(); return super.read();
                }
                @Override public synchronized int read(byte[] buffer, int offset, int length) {
                    OperationControl.checkpoint(); return super.read(buffer, offset, length);
                }
            });
            OperationControl.checkpoint();
            if (mapping.getClasses().isEmpty()) throw new IOException(key + " contains no class mappings");
            if (mapping.getClasses().size() > BoundedInputs.ENTRY_COUNT) throw new IOException(key + " has too many class mappings");
            validateInjectiveClasses(mapping, key);
            return mapping;
        } catch (OperationStoppedException stopped) {
            throw stopped;
        } catch (RuntimeException malformed) {
            OperationStoppedException.rethrowIfPresent(malformed);
            throw new IOException("Cannot parse mapping input " + key + ": " + malformed.getClass().getSimpleName(), malformed);
        }
    }

    private static byte[] joinedTsrg(byte[] bytes, String key) throws IOException {
        byte[] result = null;
        int entries = 0;
        long inflated = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                OperationControl.checkpoint();
                if (++entries > BoundedInputs.ENTRY_COUNT) throw new IOException(key + " has too many ZIP entries");
                long remaining = BoundedInputs.INFLATED_BYTES - inflated;
                if (entry.getName().equals("config/joined.tsrg")) {
                    if (result != null) throw new IOException(key + " contains duplicate config/joined.tsrg entries");
                    result = BoundedInputs.read(zip, Math.min(remaining, BoundedInputs.MAPPING_BYTES), key);
                    inflated += result.length;
                } else {
                    inflated += BoundedInputs.transfer(zip, remaining, key, null);
                }
            }
        }
        if (result == null) throw new IOException(key + " ZIP is missing config/joined.tsrg");
        return result;
    }

    /** srgutils.merge keeps left-hand duplicates; check all common keys before that additive operation. */
    static IMappingFile mergeOfficial(IMappingFile client, IMappingFile server, String label) throws IOException {
        for (IMappingFile.IClass serverClass : server.getClasses()) {
            OperationControl.checkpoint();
            IMappingFile.IClass clientClass = client.getClass(serverClass.getOriginal());
            if (clientClass == null) continue;
            if (!clientClass.getMapped().equals(serverClass.getMapped())) {
                throw new IOException(label + " official mappings conflict for class " + serverClass.getOriginal());
            }
            for (IMappingFile.IMethod serverMethod : serverClass.getMethods()) {
                OperationControl.checkpoint();
                IMappingFile.IMethod clientMethod = clientClass.getMethod(serverMethod.getOriginal(), serverMethod.getDescriptor());
                if (clientMethod != null && !clientMethod.getMapped().equals(serverMethod.getMapped())) {
                    throw new IOException(label + " official mappings conflict for method " + serverClass.getOriginal()
                            + "." + serverMethod.getOriginal() + serverMethod.getDescriptor());
                }
            }
            for (IMappingFile.IField serverField : serverClass.getFields()) {
                OperationControl.checkpoint();
                IMappingFile.IField clientField = clientClass.getField(serverField.getOriginal());
                if (clientField != null && (!clientField.getMapped().equals(serverField.getMapped())
                        || !Objects.equals(clientField.getDescriptor(), serverField.getDescriptor()))) {
                    throw new IOException(label + " official mappings conflict for field " + serverClass.getOriginal()
                            + "." + serverField.getOriginal());
                }
            }
        }
        IMappingFile merged = client.merge(server);
        OperationControl.checkpoint();
        validateInjectiveClasses(merged, label + " merged official mappings");
        return merged;
    }

    private static void validateInjectiveClasses(IMappingFile mapping, String label) throws IOException {
        Set<String> mapped = new HashSet<>();
        for (IMappingFile.IClass type : mapping.getClasses()) {
            OperationControl.checkpoint();
            if (!mapped.add(type.getMapped())) {
                throw new IOException(label + " maps multiple classes to " + type.getMapped());
            }
        }
    }

    private static IMappingFile emptyMapping() {
        return IMappingBuilder.create("source", "target").build().getMap("source", "target");
    }

    private static Properties profile(Path path) throws IOException {
        Properties profile = new Properties();
        try (InputStream input = BoundedInputs.open(path, 64 * 1024, "mapping profile")) {
            profile.load(input);
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Mapping profile is not a valid Java properties file", malformed);
        }
        Set<String> expected = new HashSet<>(MAPPING_KEYS);
        expected.addAll(VERSIONS.keySet());
        expected.add("legacy.apiJars");
        expected.add("target.apiJars");
        for (String key : profile.stringPropertyNames()) {
            if (!expected.contains(key)) throw new IOException("Unknown mapping profile property: " + key);
        }
        for (String key : expected) {
            if (profile.getProperty(key, "").isBlank()) throw new IOException("Mapping profile requires property " + key);
        }
        for (Map.Entry<String, String> version : VERSIONS.entrySet()) {
            if (!version.getValue().equals(profile.getProperty(version.getKey()).trim())) {
                throw new IOException("Mapping profile requires " + version.getKey() + "=" + version.getValue());
            }
        }
        return profile;
    }

    private Properties defaults(List<String> diagnostics) throws IOException {
        Properties defaults = new Properties();
        Path forge = gradleHome.resolve("caches/forge_gradle");
        for (String side : List.of("legacy", "target")) {
            String minecraft = VERSIONS.get(side + ".minecraft");
            String forgeVersion = VERSIONS.get(side + ".forge");
            Path version = forge.resolve("minecraft_repo/versions/" + minecraft);
            defaults.setProperty(side + ".clientMappings", version.resolve("client_mappings.txt").toString());
            defaults.setProperty(side + ".serverMappings", version.resolve("server_mappings.txt").toString());
            String mcp = side.equals("legacy") ? "1.16.5-20210115.111550" : "1.20.1-20230612.114412";
            defaults.setProperty(side + ".srg", forge.resolve("maven_downloader/de/oceanlabs/mcp/mcp_config/"
                    + mcp + "/mcp_config-" + mcp + ".zip").toString());
            String artifact = minecraft + "-" + forgeVersion + "_mapped_official_" + minecraft;
            defaults.setProperty(side + ".apiJars", forge.resolve("minecraft_user_repo/net/minecraftforge/forge/"
                    + artifact + "/forge-" + artifact + ".jar").toString());
        }
        String targetApis = defaults.getProperty("target.apiJars");
        for (String artifact : List.of("javafmllanguage", "fmlcore", "fmlloader")) {
            Path jar = optionalTargetApi(artifact);
            if (jar == null) {
                diagnostics.add("OPTIONAL_API_MISSING: target " + artifact
                        + " 1.20.1-47.4.23; unresolved declarations remain unresolved");
            } else {
                targetApis += ";" + jar;
            }
        }
        defaults.setProperty("target.apiJars", targetApis);
        return defaults;
    }

    private Path optionalTargetApi(String artifact) throws IOException {
        String version = "1.20.1-47.4.23";
        Path directory = gradleHome.resolve("caches/modules-2/files-2.1/net.minecraftforge/" + artifact + "/" + version);
        if (!Files.isDirectory(directory)) return null;
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> children = Files.list(directory)) {
            List<Path> hashes = children.limit(65).toList();
            if (hashes.size() > 64) throw new IOException("Too many cache variants for target " + artifact);
            for (Path hash : hashes) {
                OperationControl.checkpoint();
                Path jar = hash.resolve(artifact + "-" + version + ".jar");
                if (Files.isRegularFile(jar)) candidates.add(jar);
            }
        }
        if (candidates.size() > 1) throw new IOException("Ambiguous target " + artifact + " cache; supply an explicit mapping profile");
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static Path resolve(Path relativeTo, String value) throws IOException {
        try {
            Path result = Path.of(value.trim());
            return (result.isAbsolute() ? result : relativeTo.resolve(result)).toAbsolutePath().normalize();
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid path in mapping profile", invalid);
        }
    }

    private static Path defaultGradleHome() {
        String configured = System.getenv("GRADLE_USER_HOME");
        return configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), ".gradle") : Path.of(configured).toAbsolutePath().normalize();
    }
}
