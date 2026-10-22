package dev.legacybridge.corpus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;
import static dev.legacybridge.corpus.CompositionRunFiles.*;

/** Read-only developer plan: never stages files, accepts EULA, executes a command or promotes a profile. */
public final class CompositionRunPlanner {
    public static final String INDEX = "docs/evidence/compose-observation-index-2026-09-07.json";
    public static final String INVENTORY = "docs/evidence/compose-platform-inventory-2026-09-07.json";
    private static final String INVENTORY_HASH = "ec9a5abb69cf7cde7b16d3c756aae0eeef322fef387425ca3b119b47a1f796c1";
    private static final String SOURCE_LAUNCH_HASH = "dfeab9c7d582fce5b1112e9d3038fa0e7075296e467031ce1e2c0ba9fdcf441f";
    private static final String TARGET_LAUNCH_HASH = "659e71b783f09de7cfc05591472191b73ea5321864412ce63f49013c266781b8";
    private static final Map<String, String> INPUT_HASHES = Map.of(
            "original", "873013592b97449b9fffd3f569cc7b7fc3309fcc9b1d241f5a230d1d45731212",
            "candidate", "328c475e98e80c022aeb044d55cdf1b375e62db89c0602da8fc3c00fb495eca2",
            "proof", "ea1624ac6ff59363272da0b04f1f94a11f11a9324cde35591bf41db0e193b2d1",
            "receipt", "ed976a181fac59e2453d3dd110db6c0d89b3d7565a22e37d6df4ab4bf508e9bd",
            "runtime", "4e74a86169bb05d8ce299c000d4945c1bcf3746f53c8b3684ee37fe67a9b1b84");
    public enum Role { SOURCE, TARGET }
    public record Request(Role role, String runId, Path javaHome) { }
    public record Copy(String source, String destination, String sha256, long bytes) {
        Map<String, Object> tree() { return Map.of("source", source, "destination", destination, "sha256", sha256, "bytes", bytes); }
    }
    /** Canonical JSON is the immutable value; its digest excludes the outer report envelope. */
    public record Plan(Role role, String runId, Path workingDirectory, List<String> arguments,
                       List<Copy> copies, String canonicalJson, String sha256) {
        public Plan { arguments = List.copyOf(arguments); copies = List.copyOf(copies); }
        public Map<String, Object> report() throws IOException {
            Object tree;
            try { tree = dev.legacybridge.converter.config.BoundedJsonParser.parse(canonicalJson); }
            catch (dev.legacybridge.converter.config.BoundedJsonParser.JsonSyntaxException bad) { throw new IOException("COMPOSE_PLAN_JSON", bad); }
            require(digest(canonicalJson.getBytes(StandardCharsets.UTF_8)).equals(sha256), "PLAN_DIGEST");
            return Map.of("schemaVersion", 1, "reportType", "LEGACY_COMPOSITION_RUN_PLAN_REPORT",
                    "status", "PLANNED_ONLY", "planSha256", sha256, "plan", tree,
                    "executionAllowed", false, "gameExecutionVerified", false, "installationAllowed", false);
        }
    }
    private CompositionRunPlanner() { }

    public static Plan plan(Path repository, Request request) throws IOException {
        return select(repository, request, false);
    }

    // Read-only staged verification only: reviewed selectors remain identical to public planning.
    // This does not turn an existing run into a reusable/create-new destination.
    static Plan reconstructMaterialized(Path repository, Request request) throws IOException {
        return select(repository, request, true);
    }

    private static Plan select(Path repository, Request request, boolean materialized) throws IOException {
        validateRequest(repository, request);
        require(CompositionObservationVerifier.verify(repository, INDEX).status() == CompositionObservationVerifier.State.NOT_RUN, "INDEX_NOT_FRESH");
        CompositionRunFiles files = new CompositionRunFiles(repository);
        Pin indexPin = files.read(INDEX);
        Map<String, Object> index = CorpusJson.read(files.resolve(INDEX));
        Map<String, Object> inputs = object(index.get("artifacts"));
        Map<String, Pin> reviewed = new LinkedHashMap<>();
        for (String key : List.of("original", "candidate", "proof", "receipt", "runtime")) {
            Map<String, Object> value = object(inputs.get(key));
            require(INPUT_HASHES.get(key).equals(value.get("sha256")), "REVIEWED_INPUT");
            reviewed.put(key, files.select(string(value.get("path")), string(value.get("sha256")), integer(value.get("bytes"))));
        }
        String version = request.role == Role.SOURCE ? "1.16.5" : "1.20.1";
        String probePath = "fixtures/composition-runtime-probe-" + version + "/build/libs/legacybridge-composition-probe-" + version + "-1.0.0.jar";
        reviewed.put("probe", files.select(probePath, request.role == Role.SOURCE
                ? "2b377a93d803ef74ef581539d9ffcc38bb05eefe5577f489ea66ccf8b8c91a4d"
                : "67610871517d8adca83ff7ca028995542d9800b2bb899a01c708e8d2d8459bf2", 39895));
        files.verify(indexPin);
        require(files.read(INVENTORY).sha256().equals(INVENTORY_HASH), "INVENTORY_REVIEW");
        return inspect(repository, request, files, reviewed, INVENTORY,
                request.role == Role.SOURCE ? SOURCE_LAUNCH_HASH : TARGET_LAUNCH_HASH, materialized);
    }

    // Package-private inert-input seam for tests. Public/CLI entrypoints always enforce reviewed proofs and pins above.
    static Plan inspect(Path repository, Request request, CompositionRunFiles files, Map<String, Pin> inputs,
                        String inventoryPath, String launchHash) throws IOException {
        return inspect(repository, request, files, inputs, inventoryPath, launchHash, false);
    }

    private static Plan inspect(Path repository, Request request, CompositionRunFiles files, Map<String, Pin> inputs,
                                String inventoryPath, String launchHash, boolean materialized) throws IOException {
        validateRequest(repository, request);
        String game = "build/composition-probe-runs/" + request.runId + "/" + request.role.name().toLowerCase(java.util.Locale.ROOT);
        // Check the entire run identity, not just one child. No reservation/write occurs here.
        checkRun(repository, request, materialized);
        Path cwd = repository.resolve(game);
        Pin inventoryPin = files.read(inventoryPath);
        Map<String, Object> inventory = CorpusJson.read(files.resolve(inventoryPath));
        keys(inventory, "schemaVersion", "reportType", "provenance", "platforms");
        require(integer(inventory.get("schemaVersion")) == 1 && "LEGACY_COMPOSITION_PLATFORM_INVENTORY".equals(inventory.get("reportType")), "INVENTORY_SCHEMA");
        require(!string(inventory.get("provenance")).isBlank(), "PROVENANCE");
        Map<String, Object> platforms = object(inventory.get("platforms")); keys(platforms, "SOURCE", "TARGET");
        Map<String, Object> platform = object(platforms.get(request.role.name())); keys(platform, "templateRoot", "launchFile", "files");
        String template = string(platform.get("templateRoot")); relative(template);
        require(template.startsWith("build/") && !template.startsWith("build/composition-probe-runs/"), "TEMPLATE_ROOT");
        directory(files.resolve(template));
        String launchFile = string(platform.get("launchFile")); relative(launchFile);
        require(launchFile.equals(request.role == Role.SOURCE ? "forge-1.16.5-36.2.42.jar"
                : "libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt"), "LAUNCH_FILE");
        require(platform.get("files") instanceof List<?>, "PLATFORM_FILES");
        List<?> entries = (List<?>) platform.get("files"); require(!entries.isEmpty() && entries.size() <= 256, "PLATFORM_BUDGET");
        Map<String, Pin> selected = new TreeMap<>();
        for (Object entry : entries) {
            Map<String, Object> value = object(entry); keys(value, "path", "sha256", "bytes");
            String name = string(value.get("path")); relative(name);
            require(name.equals(launchFile) || name.equals("minecraft_server.1.16.5.jar") && request.role == Role.SOURCE
                    || name.startsWith("libraries/") && name.endsWith(".jar"), "PLATFORM_PATH");
            require(!name.contains("-sources.jar") && !name.contains("-installer.jar") && !name.contains("-userdev.jar"), "DEVELOPMENT_ARTIFACT");
            require(!selected.containsKey(name), "DUPLICATE_PLATFORM_PATH");
            selected.put(name, files.select(template + "/" + name, string(value.get("sha256")), integer(value.get("bytes"))));
        }
        Pin launch = selected.get(launchFile);
        require(launch != null && launch.sha256().equals(launchHash), "LAUNCH_PIN");
        List<String> bootstrap = request.role == Role.SOURCE ? sourceBootstrap(files, selected, launch)
                : targetBootstrap(files.text(launch, 65536), selected);
        for (String dynamic : dynamicPaths(request.role)) require(selected.containsKey(dynamic), "DYNAMIC_DEPENDENCY");
        auditManifestReferences(files, selected);

        Pin eula = files.read(template + "/eula.txt");
        require(properties(files.text(eula, 8192)).getProperty("eula", "").equals("true"), "EULA_NOT_ACCEPTED");
        // This reads release metadata; only future actual runtime observations can prove the executed Java version.
        Path javaHome = request.javaHome; directory(javaHome);
        Path executable = javaHome.resolve("bin/java.exe"), release = javaHome.resolve("release");
        String javaHash = hash(executable), releaseHash = hash(release);
        require(Files.size(executable) > 0, "JAVA_EXECUTABLE_SIZE");
        require(Files.size(release) <= 8192, "JAVA_RELEASE_SIZE");
        byte[] releaseBytes;
        try (var input = Files.newInputStream(release, java.nio.file.LinkOption.NOFOLLOW_LINKS)) { releaseBytes = input.readNBytes(8193); }
        require(releaseBytes.length <= 8192 && digest(releaseBytes).equals(releaseHash), "JAVA_RELEASE_DRIFT");
        String releaseText = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(releaseBytes)).toString();
        String javaVersion = properties(releaseText).getProperty("JAVA_VERSION", "");
        require(javaVersion.matches("\"17(?:\\.[0-9]+)*(?:[+_][A-Za-z0-9._-]+)?\""), "JAVA_17_REQUIRED");
        List<String> argv = new ArrayList<>(List.of(executable.toString(), "-Xms512M", "-Xmx2G",
                "-Duser.home=" + cwd.resolve("empty-user-home"), "-Djava.io.tmpdir=" + cwd.resolve("tmp"),
                "-Dlegacybridge.compositionProbe.run=" + request.runId));
        argv.addAll(bootstrap); argv.add("nogui");
        require(argv.size() <= 64 && argv.stream().noneMatch(a -> a.indexOf('\n') >= 0 || a.indexOf('\r') >= 0 || a.indexOf('\0') >= 0 || a.startsWith("@")), "ARGV");
        List<Copy> copies = new ArrayList<>();
        for (var entry : selected.entrySet()) copy(copies, entry.getValue(), entry.getKey());
        copy(copies, eula, "eula.txt");
        copy(copies, inputs.get(request.role == Role.SOURCE ? "original" : "candidate"), "mods/fixture.jar");
        copy(copies, inputs.get("probe"), "mods/composition-probe.jar");
        if (request.role == Role.TARGET) {
            copy(copies, inputs.get("runtime"), "mods/LegacyBridge.jar");
            copy(copies, inputs.get("receipt"), "composition-receipt.json");
        }
        for (Pin input : inputs.values()) files.select(input.path(), input.sha256(), input.bytes());
        Map<String, String> generated = Map.of("composition-probe-authorized.txt", request.runId + "\n",
                "server.properties", "server-ip=127.0.0.1\nserver-port=" + (request.role == Role.SOURCE ? "25597" : "25598")
                        + "\nlevel-name=world\nenable-rcon=false\nenable-query=false\nmax-players=1\nwhite-list=true\nenforce-whitelist=true\n"
                        + "view-distance=2\n" + (request.role == Role.TARGET ? "simulation-distance=2\n" : "") + "generate-structures=false\n");
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("schemaVersion", 1); tree.put("reportType", "LEGACY_COMPOSITION_RUN_PLAN"); tree.put("status", "PLANNED_ONLY");
        tree.put("role", request.role.name()); tree.put("runId", request.runId); tree.put("workingDirectory", game);
        tree.put("runtimeValidation", "NOT_RUN"); tree.put("executionAllowed", false); tree.put("installationAllowed", false);
        tree.put("java", Map.of("executable", executable.toString(), "sha256", javaHash, "releaseSha256", releaseHash,
                "declaredVersion", javaVersion, "executed", false));
        tree.put("arguments", argv); tree.put("copyFiles", copies.stream().map(Copy::tree).toList()); tree.put("generatedFiles", generated);
        tree.put("createDirectories", List.of("mods", "composition-observations", "empty-user-home", "empty-user-home/absent-gradle", "tmp"));
        tree.put("removeEnvironment", List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"));
        tree.put("setEnvironment", Map.of("GRADLE_USER_HOME", cwd.resolve("empty-user-home/absent-gradle").toString()));
        tree.put("limits", Map.of("maxHeapMiB", 2048, "maxConcurrentGames", 1, "timeoutMillis", 240000,
                "stopGraceMillis", 60000, "stdoutMaxBytes", 8388608, "stderrMaxBytes", 8388608));
        tree.put("inputs", files.pins().stream().map(Pin::tree).toList());
        tree.put("pending", List.of("OWNED_CREATE_NEW_STAGING", "APPROVED_PLAN_TO_PROCESS_BINDING", "BOUNDED_LIVE_CAPTURE",
                "EXPLICIT_NATIVE_AUTHORIZATION", "NATIVE_JAVA17_SOURCE_VALIDATION", "INDEPENDENT_NATIVE_REVIEW"));
        files.verify(inventoryPin); files.verifyAll();
        require(javaHash.equals(hash(executable)) && releaseHash.equals(hash(release)), "JAVA_DRIFT");
        checkRun(repository, request, materialized);
        String json = CorpusJson.canonicalJson(tree);
        return new Plan(request.role, request.runId, cwd, argv, copies, json, digest(json.getBytes(StandardCharsets.UTF_8)));
    }
    private static void validateRequest(Path root, Request request) throws IOException {
        directory(root); require(request != null && request.role != null && request.javaHome != null, "REQUEST");
        require(request.runId != null && request.runId.matches("run-[a-z0-9][a-z0-9-]{0,40}"), "RUN_ID");
    }
    private static void checkRun(Path repository, Request request, boolean materialized) throws IOException {
        Path run = repository.resolve("build/composition-probe-runs/" + request.runId);
        if (materialized) {
            directory(run);
            directory(run.resolve(request.role.name().toLowerCase(java.util.Locale.ROOT)));
        } else absentTree(run);
    }
    private static void copy(List<Copy> copies, Pin pin, String destination) throws IOException {
        require(pin != null, "MISSING_INPUT"); relative(destination);
        require(copies.stream().noneMatch(copy -> copy.destination.equalsIgnoreCase(destination)), "COPY_COLLISION");
        copies.add(new Copy(pin.path(), destination, pin.sha256(), pin.bytes()));
    }
    private static java.util.Properties properties(String text) throws IOException {
        java.util.Properties props = new java.util.Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate property");
                return super.put(key, value);
            }
        };
        try { props.load(new java.io.StringReader(text)); }
        catch (IllegalArgumentException bad) { throw new IOException("COMPOSE_PLAN_PROPERTIES", bad); }
        return props;
    }
    private static List<String> sourceBootstrap(CompositionRunFiles files, Map<String, Pin> selected, Pin launch) throws IOException {
        Manifest manifest = mainManifest(files.resolve(launch.path())); require(manifest != null, "SOURCE_MANIFEST");
        var attrs = manifest.getMainAttributes();
        require("net.minecraftforge.server.ServerMain".equals(attrs.getValue("Main-Class")), "SOURCE_MAIN");
        require(("--gameDir . --launchTarget fmlserver --fml.forgeVersion 36.2.42 --fml.mcpVersion 20210115.111550 "
                + "--fml.mcVersion 1.16.5 --fml.forgeGroup net.minecraftforge").equals(attrs.getValue("ServerLaunchArgs")), "SOURCE_ARGUMENTS");
        String classPath = attrs.getValue("Class-Path"); require(classPath != null && !classPath.isBlank(), "SOURCE_CLASSPATH");
        List<String> names = Arrays.asList(classPath.trim().split("\\s+"));
        require(Set.copyOf(names).size() == names.size(), "SOURCE_CLASSPATH_DUPLICATE");
        for (String name : names) { relative(name); require(selected.containsKey(name), "SOURCE_DEPENDENCY"); }
        return List.of("-jar", "forge-1.16.5-36.2.42.jar");
    }
    private static void auditManifestReferences(CompositionRunFiles files, Map<String, Pin> selected) throws IOException {
        // The finite inventory already fixes the reviewed closure. Check every selected JAR, including
        // dynamically located ones, so a manifest cannot introduce a hidden unselected URL/classpath.
        for (var entry : selected.entrySet()) {
            if (!entry.getKey().endsWith(".jar")) continue;
            Manifest manifest = mainManifest(files.resolve(entry.getValue().path()));
            if (manifest == null) continue;
            String cp = manifest.getMainAttributes().getValue("Class-Path");
            if (cp == null) continue;
            require(!cp.isBlank(), "MANIFEST_CLASSPATH");
            List<String> paths = Arrays.asList(cp.trim().split("\\s+"));
            require(paths.size() <= 128 && Set.copyOf(paths).size() == paths.size(), "MANIFEST_CLASSPATH");
            int slash = entry.getKey().lastIndexOf('/');
            String parent = slash < 0 ? "" : entry.getKey().substring(0, slash + 1);
            for (String path : paths) { relative(path); require(selected.containsKey(parent + path), "MANIFEST_DEPENDENCY"); }
        }
    }
    private static Manifest mainManifest(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("META-INF/MANIFEST.MF"); if (entry == null) return null;
            // Signed native manifests can exceed 1 MiB. Only the main section controls Class-Path;
            // read it up to its first blank line, never decompress all per-entry digest sections.
            var bytes = new java.io.ByteArrayOutputStream(); int line = 0; boolean ended = false;
            try (var input = zip.getInputStream(entry)) {
                int value;
                while ((value = input.read()) != -1) {
                    require(bytes.size() < 65536, "MANIFEST_SIZE"); bytes.write(value);
                    if (value == '\n') { if (line == 0) { ended = true; break; } line = 0; }
                    else if (value != '\r') line++;
                }
            }
            // jopt-simple 5.0.4 in both pinned installations ends with a complete newline,
            // not an extra empty line. EOF after a complete line is also a bounded main section.
            require(ended || bytes.size() > 0 && line == 0, "MANIFEST_MAIN_TRUNCATED");
            return new Manifest(new ByteArrayInputStream(bytes.toByteArray()));
        }
    }
    private static List<String> targetBootstrap(String text, Map<String, Pin> selected) throws IOException {
        List<String> lines = text.replace("\r\n", "\n").stripTrailing().lines().toList(); require(lines.size() == 16, "ARGFILE_LINES");
        Map<Integer, String> fixed = Map.ofEntries(Map.entry(1, "--add-modules ALL-MODULE-PATH"),
                Map.entry(2, "--add-opens java.base/java.util.jar=cpw.mods.securejarhandler"),
                Map.entry(3, "--add-opens java.base/java.lang.invoke=cpw.mods.securejarhandler"),
                Map.entry(4, "--add-exports java.base/sun.security.util=cpw.mods.securejarhandler"),
                Map.entry(5, "--add-exports jdk.naming.dns/com.sun.jndi.dns=java.naming"),
                Map.entry(6, "-Djava.net.preferIPv6Addresses=system"), Map.entry(8, "-DlibraryDirectory=libraries"),
                Map.entry(10, "cpw.mods.bootstraplauncher.BootstrapLauncher"), Map.entry(11, "--launchTarget forgeserver"),
                Map.entry(12, "--fml.forgeVersion 47.4.23"), Map.entry(13, "--fml.mcVersion 1.20.1"),
                Map.entry(14, "--fml.forgeGroup net.minecraftforge"), Map.entry(15, "--fml.mcpVersion 20230612.114412"));
        for (var entry : fixed.entrySet()) require(lines.get(entry.getKey()).equals(entry.getValue()), "ARGFILE_ORDER");
        List<String> module = pathList(lines.get(0), "-p ", selected);
        pathList(lines.get(9), "-DlegacyClassPath=", selected);
        String ignores = "-DignoreList=" + String.join(",", module.stream().map(p -> p.substring(p.lastIndexOf('/') + 1)).toList());
        require(lines.get(7).equals(ignores), "IGNORE_LIST");
        List<String> args = new ArrayList<>();
        for (String line : lines) args.addAll(Arrays.asList(line.split(" ", -1)));
        return List.copyOf(args);
    }
    private static List<String> pathList(String line, String prefix, Map<String, Pin> selected) throws IOException {
        require(line.startsWith(prefix), "CLASSPATH_PREFIX"); List<String> paths = List.of(line.substring(prefix.length()).split(";", -1));
        require(!paths.isEmpty() && paths.size() <= 128 && Set.copyOf(paths).size() == paths.size(), "CLASSPATH_SIZE");
        for (String path : paths) { relative(path); require(path.startsWith("libraries/") && path.endsWith(".jar") && selected.containsKey(path), "CLASSPATH_DEPENDENCY"); }
        return paths;
    }
    static List<String> dynamicPaths(Role role) {
        String version = role == Role.SOURCE ? "1.16.5" : "1.20.1";
        String forge = version + (role == Role.SOURCE ? "-36.2.42" : "-47.4.23");
        String mcp = version + (role == Role.SOURCE ? "-20210115.111550" : "-20230612.114412");
        List<String> result = new ArrayList<>();
        for (String type : List.of("srg", "extra")) result.add("libraries/net/minecraft/server/" + mcp + "/server-" + mcp + "-" + type + ".jar");
        for (String type : List.of("server", "universal")) result.add("libraries/net/minecraftforge/forge/" + forge + "/forge-" + forge + "-" + type + ".jar");
        if (role == Role.TARGET) for (String language : List.of("fmlcore", "javafmllanguage", "lowcodelanguage", "mclanguage"))
            result.add("libraries/net/minecraftforge/" + language + "/" + forge + "/" + language + "-" + forge + ".jar");
        return List.copyOf(result);
    }
    public static void main(String[] args) throws IOException {
        try {
            require(args.length == 4, "USAGE_REPOSITORY_ROLE_RUNID_JAVAHOME");
            Role role;
            try { role = Role.valueOf(args[1]); } catch (IllegalArgumentException bad) { throw new IOException("COMPOSE_PLAN_ROLE"); }
            Path repository, javaHome;
            try { repository = Path.of(args[0]); javaHome = Path.of(args[3]); }
            catch (java.nio.file.InvalidPathException bad) { throw new IOException("COMPOSE_PLAN_PATH"); }
            Plan plan = plan(repository, new Request(role, args[2], javaHome));
            System.out.print(CorpusJson.canonicalJson(plan.report()));
        } catch (IOException invalid) {
            String code = invalid.getMessage();
            if (code == null || !code.matches("COMPOSE_PLAN_[A-Z0-9_]+")) code = "COMPOSE_PLAN_IO_REJECTED";
            System.out.print(CorpusJson.canonicalJson(Map.of("schemaVersion", 1,
                    "reportType", "LEGACY_COMPOSITION_RUN_PLAN_REPORT", "status", "BLOCKED",
                    "executionAllowed", false, "gameExecutionVerified", false, "installationAllowed", false, "error", code)));
            // Do not carry arbitrary path/parser input or captured content in a printed exception cause.
            throw new IOException(code);
        }
    }
}
