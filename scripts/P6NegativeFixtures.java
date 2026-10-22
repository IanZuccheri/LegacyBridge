import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.LegacyEngineService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Local, never-packaged QA fixture generator. Its only input class operation is ASM inspection
 * and string-constant rewriting of a hash-pinned, genuine Forge 36 fixture. The original JAR
 * is never put on this process's or the standalone CLI's classpath, defined, or initialized.
 * This does not add supported converter rules or claim game behavior from static qualification.
 */
public final class P6NegativeFixtures {
    private static final String SOURCE = "fixtures/simple-content-1.16.5/build/libs/legacybridge-example-1.16.5-1.0.0.jar";
    private static final String SOURCE_SHA = "00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947";
    private static final String OLD_ID = "legacybridge_example";
    private static final String MAIN_CLASS = "dev/legacybridge/fixture/LegacyBridgeExample.class";
    private static final String CLI = "dev.legacybridge.converter.cli.LegacyBridgeCli";
    private static final CanonicalReportWriter JSON = new CanonicalReportWriter();
    private static final int MAX_BYTES = 16 * 1024 * 1024;

    private P6NegativeFixtures() { }

    public static void main(String[] args) throws Exception {
        require(Runtime.version().feature() == 17, "Use Java 17 for reproducible qualification.");
        require(args.length == 1, "Usage: P6NegativeFixtures <existing-project-root>");
        Path project = Path.of(args[0]).toAbsolutePath().normalize();
        directory(project);
        Path original = project.resolve(SOURCE);
        regular(original);
        require(hash(original).equals(SOURCE_SHA), "Pinned original fixture hash does not match; refusing to invent a baseline.");
        Path output = project.resolve("build/p6-public/negative-fixtures");
        directory(output);
        Path libraries = project.resolve("legacy-converter/build/install/legacy-converter/lib");
        directory(libraries);
        Path bundle = project.resolve("build/p5-transactions/client/legacybridge/cache/mapping-data");
        directory(bundle);
        regular(bundle.resolve("manifest.json"));

        var source = readSource(original);
        var fixtures = new TreeMap<String, byte[]>();
        fixtures.put("corrupt.jar", "This is an intentionally corrupt P6 QA JAR, not a ZIP archive.\n".getBytes(StandardCharsets.UTF_8));
        fixtures.put("p6-unsupported.jar", clone(source, "p6_unsupported", true, false));
        fixtures.put("p6-dependency.jar", clone(source, "p6_dependency", false, true));
        fixtures.put("p6-cancel.jar", clone(source, "p6_cancel", false, false));
        byte[] duplicate = clone(source, "p6_duplicate", false, false);
        fixtures.put("p6-duplicate-a.jar", duplicate);
        fixtures.put("p6-duplicate-b.jar", duplicate);
        var fingerprints = new ArrayList<Map<String, Object>>();
        for (var entry : fixtures.entrySet()) {
            publishOrVerify(output.resolve(entry.getKey()), entry.getValue());
            fingerprints.add(Map.of("fileName", entry.getKey(), "sha256", hash(entry.getValue()), "sizeBytes", entry.getValue().length));
        }
        publishOrVerify(output.resolve("fixture-manifest.json"), json(Map.of(
                "schemaVersion", 1, "reportType", "LEGACYBRIDGE_P6_NEGATIVE_FIXTURES",
                "source", Map.of("path", SOURCE, "sha256", SOURCE_SHA, "minecraft", "1.16.5", "forge", "36.2.42"),
                "bytecodeExecuted", false, "zipTimestamp", "1980-01-01T00:00:00", "fixtures", fingerprints,
                "modifications", List.of("ASM string constants/@Mod values and matching resource namespaces only",
                        "p6_unsupported: adds data/p6_unsupported/worldgen/configured_feature/p6_probe.json, inert {} sentinel",
                        "p6_dependency: adds mandatory absent p6_missing_dependency [1.0.0,) BOTH/NONE",
                        "p6_cancel: valid control clone; cancellation/write denial must be exercised separately",
                        "p6_duplicate: two byte-identical JARs; inventory must report both duplicate identities"))));
        require(hash(original).equals(SOURCE_SHA), "Original changed during fixture preparation.");

        Path evidence = Files.createDirectory(output.resolve("qualification-" + UUID.randomUUID()));
        var cliResults = new ArrayList<Map<String, Object>>();
        for (String name : List.of("corrupt.jar", "p6-unsupported.jar", "p6-dependency.jar", "p6-cancel.jar", "p6-duplicate-a.jar")) {
            int expectedExit = name.equals("corrupt.jar") ? 3 : name.equals("p6-unsupported.jar") || name.equals("p6-dependency.jar") ? 1 : 0;
            cliResults.add(qualifyCli(libraries, bundle, output.resolve(name), evidence, expectedExit));
        }
        Map<String, Object> inventoryEvidence = inventory(output);
        var libraryHashes = new TreeMap<String, String>();
        try (var entries = Files.newDirectoryStream(libraries, "*.jar")) {
            for (Path library : entries) libraryHashes.put(library.getFileName().toString(), hash(library));
        }
        publishOrVerify(evidence.resolve("qualification.json"), json(Map.of(
                "schemaVersion", 1, "reportType", "LEGACYBRIDGE_P6_STATIC_QUALIFICATION",
                "sourceSha256", SOURCE_SHA, "mappingBundleSelected", bundle.toString(),
                "mappingManifestSha256", hash(bundle.resolve("manifest.json")), "cliLibraries", libraryHashes,
                "cliResults", cliResults, "inventory", inventoryEvidence,
                "limitations", List.of("Not a Minecraft/client UI/runtime proof", "No cancellation or disk-write failure was injected here",
                        "Original classes were not on any execution classpath", "Locally imported mapping data is not copied or redistributed"))));
        for (var entry : fixtures.entrySet()) require(hash(output.resolve(entry.getKey())).equals(hash(entry.getValue())), "Fixture changed during qualification.");
        require(hash(original).equals(SOURCE_SHA), "Original changed during qualification.");
        System.out.println("P6_STATIC_FIXTURES_OK " + evidence);
        for (var entry : fingerprints) System.out.println(entry.get("fileName") + " " + entry.get("sha256"));
    }

    private static Map<String, byte[]> readSource(Path original) throws Exception {
        require(Files.size(original) <= MAX_BYTES, "Unexpected fixture archive size.");
        var entries = new TreeMap<String, byte[]>();
        int captured = 0;
        try (var zip = new ZipFile(original.toFile(), StandardCharsets.UTF_8)) {
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (entry.isDirectory()) continue;
                require(entries.size() < 256 && !entry.getName().contains("..") && !entry.getName().startsWith("/"), "Unexpected fixture entry.");
                byte[] bytes;
                try (var stream = zip.getInputStream(entry)) { bytes = stream.readNBytes(MAX_BYTES + 1); }
                captured += bytes.length;
                require(captured <= MAX_BYTES, "Fixture capture limit exceeded.");
                require(entries.put(entry.getName(), bytes) == null, "Duplicate source entry.");
            }
        }
        require(entries.keySet().stream().filter(name -> name.endsWith(".class")).toList().equals(List.of(MAIN_CLASS)),
                "Expected exactly the genuine simple-content fixture class.");
        verifyAnnotation(entries.get(MAIN_CLASS), OLD_ID);
        return entries;
    }

    private static byte[] clone(Map<String, byte[]> source, String id, boolean worldgen, boolean missingDependency) throws Exception {
        var entries = new TreeMap<String, byte[]>();
        for (var entry : source.entrySet()) {
            String path = entry.getKey().replace(OLD_ID, id);
            byte[] bytes = entry.getValue();
            if (path.endsWith(".class")) {
                ClassWriter writer = new ClassWriter(0);
                Remapper literals = new Remapper(Opcodes.ASM9) {
                    @Override public Object mapValue(Object value) {
                        return value instanceof String text ? text.replace(OLD_ID, id) : super.mapValue(value);
                    }
                };
                new ClassReader(bytes).accept(new ClassRemapper(writer, literals), 0);
                bytes = writer.toByteArray();
                verifyAnnotation(bytes, id);
            } else if (path.endsWith(".json") || path.endsWith(".toml") || path.endsWith(".mcmeta") || path.endsWith(".MF")) {
                bytes = new String(bytes, StandardCharsets.UTF_8).replace(OLD_ID, id).getBytes(StandardCharsets.UTF_8);
            }
            require(entries.put(path, bytes) == null, "Rewritten resource collision.");
        }
        if (worldgen) entries.put("data/" + id + "/worldgen/configured_feature/p6_probe.json", "{}\n".getBytes(StandardCharsets.UTF_8));
        if (missingDependency) {
            String metadata = new String(entries.get("META-INF/mods.toml"), StandardCharsets.UTF_8);
            metadata += "\n[[dependencies." + id + "]]\nmodId=\"p6_missing_dependency\"\nmandatory=true\nversionRange=\"[1.0.0,)\"\nordering=\"NONE\"\nside=\"BOTH\"\n";
            entries.put("META-INF/mods.toml", metadata.getBytes(StandardCharsets.UTF_8));
        }
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (var entry : entries.entrySet()) {
                ZipEntry member = new ZipEntry(entry.getKey());
                member.setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0));
                zip.putNextEntry(member);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void verifyAnnotation(byte[] bytes, String expectedId) {
        var ids = new ArrayList<String>();
        ClassReader reader = new ClassReader(bytes);
        require(reader.readUnsignedShort(6) == Opcodes.V1_8, "Source fixture must remain Java 8 bytecode.");
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!descriptor.equals("Lnet/minecraftforge/fml/common/Mod;")) return null;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override public void visit(String name, Object value) { if (name.equals("value")) ids.add(String.valueOf(value)); }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        require(ids.equals(List.of(expectedId)), "Unexpected @Mod identity after static clone.");
    }

    private static Map<String, Object> qualifyCli(Path libraries, Path bundle, Path input, Path evidence, int expectedExit) throws Exception {
        String base = input.getFileName().toString().replace(".jar", "");
        Path target = evidence.resolve(base + "-converted.jar");
        Path report = evidence.resolve(base + "-conversion.json");
        Path stdout = evidence.resolve(base + "-stdout.log");
        Path stderr = evidence.resolve(base + "-stderr.log");
        var command = List.of(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Xmx512m", "-cp", libraries + "/*", CLI, "convert", input.toString(),
                "--mapping-bundle", bundle.toString(), "--output", target.toString(), "--report", report.toString());
        var builder = new ProcessBuilder(command).directory(evidence.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        for (String variable : List.of("LEGACYBRIDGE_MAPPING_BUNDLE", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) builder.environment().remove(variable);
        Process process = builder.start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            require(process.waitFor(10, TimeUnit.SECONDS), "Timed-out CLI did not terminate.");
            throw new IOException("Standalone CLI timed out; inspect retained evidence " + evidence);
        }
        int exit = process.exitValue();
        require(exit == expectedExit, "Unexpected CLI exit for " + input.getFileName() + ": " + exit + "; expected " + expectedExit + "; see " + stderr);
        require(Files.exists(target) == (exit == 0), "Unexpected converted artifact publication for " + input.getFileName());
        var result = new TreeMap<String, Object>();
        result.put("input", input.getFileName().toString());
        result.put("inputSha256", hash(input));
        result.put("command", command);
        result.put("exitCode", exit);
        result.put("stderr", Files.readString(stderr));
        result.put("outputPublished", Files.exists(target));
        if (Files.exists(target)) result.put("outputSha256", hash(target));
        if (Files.exists(report)) {
            var parsed = (Map<?, ?>) BoundedJsonParser.parse(Files.readString(report));
            result.put("status", parsed.get("status"));
            result.put("diagnostics", parsed.getOrDefault("diagnostics", null));
            result.put("report", report.getFileName().toString());
        }
        return result;
    }

    private static Map<String, Object> inventory(Path output) throws Exception {
        try (var engine = new LegacyEngineService()) {
            var outcome = engine.inventory(output).completion().toCompletableFuture().get(60, TimeUnit.SECONDS);
            require(outcome.value().isPresent() && outcome.error().isEmpty(), "Fixture inventory did not complete: " + outcome);
            var result = outcome.value().orElseThrow();
            require(!result.truncated() && result.entries().size() == 6, "Unexpected/truncated fixture inventory.");
            var entries = new ArrayList<Map<String, Object>>();
            for (var entry : result.entries()) {
                String name = entry.path().getFileName().toString();
                boolean duplicate = name.startsWith("p6-duplicate-");
                require(entry.duplicateModId() == duplicate, "Incorrect duplicate identity classification: " + name);
                require(entry.error().isPresent() == name.equals("corrupt.jar"), "Unexpected inventory error: " + entry);
                entries.add(Map.of("fileName", name, "modIds", entry.mods().stream().map(mod -> mod.modId()).toList(),
                        "duplicateModId", entry.duplicateModId(), "sourceProfile", entry.sourceProfile().name(),
                        "errorCode", entry.error().map(error -> error.code()).orElse("")));
            }
            return Map.of("status", outcome.status().name(), "truncated", result.truncated(), "entries", entries);
        }
    }

    private static void publishOrVerify(Path path, byte[] bytes) throws Exception {
        directory(path.getParent());
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            regular(path);
            byte[] existing = Files.readAllBytes(path);
            boolean matching = Arrays.equals(existing, bytes);
            // Older local evidence may differ only in JSON key order. Preserve it rather than overwrite it.
            if (!matching && path.getFileName().toString().endsWith(".json")) {
                matching = BoundedJsonParser.parse(new String(existing, StandardCharsets.UTF_8))
                        .equals(BoundedJsonParser.parse(new String(bytes, StandardCharsets.UTF_8)));
            }
            require(matching, "Refusing to replace nonmatching existing QA file: " + path);
        } else Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    private static byte[] json(Map<String, ?> value) {
        @SuppressWarnings("unchecked") var canonical = (Map<String, Object>) canonical(value);
        return JSON.toJsonTree(canonical).getBytes(StandardCharsets.UTF_8);
    }
    private static Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((key, item) -> result.put((String) key, canonical(item)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(P6NegativeFixtures::canonical).toList();
        return value;
    }
    private static String hash(Path path) throws Exception { regular(path); return hash(Files.readAllBytes(path)); }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void directory(Path path) throws Exception { require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && path.equals(path.toRealPath()), "Expected canonical real directory: " + path); }
    private static void regular(Path path) throws Exception { require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && path.equals(path.toRealPath()), "Expected canonical regular file: " + path); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
