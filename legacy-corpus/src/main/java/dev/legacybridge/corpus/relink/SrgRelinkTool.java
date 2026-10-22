package dev.legacybridge.corpus.relink;

import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.transformation.BytecodeValidator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.Manifest;
import javax.tools.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static dev.legacybridge.corpus.relink.RelinkFiles.*;

/** Explicit developer-only source rebuild/relink. No game, mod class execution, download or installation. */
public final class SrgRelinkTool {
    public static final String BINARY_SHA = "f10b366cdc4df20de8a50511f49fded5ea0fe8f7a476ff984a12f65a1602533e";
    public static final String SOURCE_SHA = "3f0c9f692d6243ef6c854408aa93b47ccb77343cc6d80d8a5d85c16b46f93dec";
    public static final String LICENSE_SHA = "20c17d8b8c48a600800dfd14f95d5cb9ff47066a9641ddeab48dc54aec96e331";
    public static final String ANNOTATIONS_SHA = "27a770dc7ce50500918bb8c3c0660c98290630ec796b5e3cf6b90f403b3033c6";
    public static final String LIBRARY = "META-INF/jarjar/srgutils-0.6.2.jar";
    public static final String SOURCES = "META-INF/legacybridge/legal/srgutils-0.6.2-sources.zip";
    public static final String LICENSE = "META-INF/legacybridge/legal/LGPL-2.1.txt";
    public static final String NOTICES = "META-INF/legacybridge/legal/THIRD_PARTY_NOTICES.md";
    private static final String PREFIX = "net/minecraftforge/srgutils/";
    private static final List<String> SOURCE_NAMES = List.of("IMappingBuilder", "IMappingFile", "INamedMappingFile", "IRenamer",
            "InternalUtils", "MappingFile", "MinecraftVersion", "NamedMappingFile");
    private SrgRelinkTool() { }

    public static void main(String[] args) throws Exception {
        Map<String, Object> report = switch (args.length == 0 ? "" : args[0]) {
            case "prepare" -> { usage(args.length == 4); yield prepare(Path.of(args[1]), args[2], Path.of(args[3])); }
            case "build" -> { usage(args.length == 4); yield build(Path.of(args[1]), Path.of(args[2]), Path.of(args[3])); }
            case "replace" -> { usage(args.length == 6); yield replace(Path.of(args[1]), args[2], Path.of(args[3]), args[4], Path.of(args[5])); }
            default -> throw new IllegalArgumentException("prepare <product.jar> <sha256> <new-kit> | build <kit> <annotations-24.1.0.jar> <new-build> | replace <product.jar> <sha256> <build> <library-sha256> <new-copy>");
        };
        System.out.print(new CanonicalReportWriter().toJsonTree(report));
    }
    private static void usage(boolean valid) { if (!valid) throw new IllegalArgumentException("Invalid explicit relink arguments; no environment defaults"); }
    private static byte[] required(Map<String, byte[]> entries, String name) throws IOException {
        byte[] value = entries.get(name); require(value != null, "MISSING_ENTRY: " + name); return value;
    }
    private static Map<String, Object> report(String status) {
        var result = new TreeMap<String, Object>();
        result.put("schemaVersion", 1); result.put("reportType", "LEGACY_SRG_RELINK"); result.put("recipe", "srgutils-0.6.2-javac17-release8-v1");
        result.put("status", status); result.put("modClassesExecuted", false); result.put("libraryBehaviorValidated", false);
        result.put("forgeLoading", "NOT_RUN"); result.put("rightsClearance", "REVIEW_REQUIRED"); result.put("installationAllowed", false);
        result.put("publicReleaseApproved", false); return result;
    }
    private static void json(Path stage, String name, Map<String, Object> report) throws IOException {
        write(stage, name, new CanonicalReportWriter().toJsonTree(report).getBytes(StandardCharsets.UTF_8));
    }
    private static SortedMap<String, byte[]> product(byte[] bytes) throws IOException {
        var entries = unzip(bytes, 4096, MAX_BYTES);
        pin(required(entries, LIBRARY), BINARY_SHA); pin(required(entries, SOURCES), SOURCE_SHA); pin(required(entries, LICENSE), LICENSE_SHA);
        var manifest = new Manifest(new ByteArrayInputStream(required(entries, "META-INF/MANIFEST.MF")));
        require("LegacyBridge".equals(manifest.getMainAttributes().getValue("Implementation-Title"))
                && "0.1.1-SNAPSHOT".equals(manifest.getMainAttributes().getValue("LegacyBridge-Adapter-Version")), "PRODUCT_IDENTITY");
        Map<String, Object> metadata;
        try { metadata = object(BoundedJsonParser.parse(new String(required(entries, "META-INF/jarjar/metadata.json"), StandardCharsets.UTF_8))); }
        catch (BoundedJsonParser.JsonSyntaxException | RuntimeException malformed) { throw new IOException("JARJAR_METADATA", malformed); }
        require(metadata.get("jars") instanceof List<?> jars && jars.size() == 1, "JARJAR_COUNT");
        var jar = object(((List<?>) metadata.get("jars")).get(0));
        require(object(jar.get("identifier")).equals(Map.of("group", "net.minecraftforge", "artifact", "srgutils"))
                && object(jar.get("version")).equals(Map.of("range", "[0.6.2,0.6.3)", "artifactVersion", "0.6.2"))
                && LIBRARY.equals(jar.get("path")) && Boolean.FALSE.equals(jar.get("isObfuscated")), "JARJAR_IDENTITY");
        for (String name : entries.keySet()) require(!(name.endsWith(".jar") && !name.equals(LIBRARY))
                && !name.startsWith("META-INF/services/") && !name.matches("(?i)META-INF/[^/]+\\.(SF|RSA|DSA|EC)")
                && !name.equals("module-info.class") && !name.startsWith("META-INF/versions/"), "UNSUPPORTED_OUTER_METADATA");
        return entries;
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) throws IOException {
        require(value instanceof Map<?, ?>, "EXPECTED_OBJECT"); return (Map<String, Object>) value;
    }

    public static Map<String, Object> prepare(Path product, String expectedHash, Path output) throws IOException {
        byte[] snapshot = read(product, MAX_BYTES); pin(snapshot, expectedHash); var entries = product(snapshot);
        var sources = unzip(required(entries, SOURCES), 32, 1 << 20);
        var result = report("SOURCE_KIT_PREPARED"); result.put("productSha256", expectedHash);
        result.put("publishedSourceSha256", SOURCE_SHA); result.put("originalLibrarySha256", BINARY_SHA);
        result.put("compileOnlyDependency", "org.jetbrains:annotations:24.1.0"); result.put("compileOnlyDependencySha256", ANNOTATIONS_SHA);
        result.put("upstreamBuildReproduced", false);
        publishDirectory(output, stage -> {
            for (String name : SOURCE_NAMES) write(stage, "src/" + PREFIX + name + ".java", required(sources, PREFIX + name + ".java"));
            write(stage, "original-library.jar", required(entries, LIBRARY));
            write(stage, "published-sources.zip", required(entries, SOURCES)); write(stage, "LICENSE-LGPL-2.1.txt", required(entries, LICENSE));
            json(stage, "kit.json", result); pin(read(product, MAX_BYTES), expectedHash);
        });
        return Collections.unmodifiableMap(result);
    }

    public static Map<String, Object> build(Path kit, Path annotations, Path output) throws IOException {
        require(Runtime.version().feature() == 17 && ToolProvider.getSystemJavaCompiler() != null, "JDK17_COMPILER_REQUIRED");
        Path root = real(kit); byte[] original = read(root.resolve("original-library.jar"), 2 << 20); pin(original, BINARY_SHA);
        byte[] published = read(root.resolve("published-sources.zip"), 1 << 20); pin(published, SOURCE_SHA);
        byte[] license = read(root.resolve("LICENSE-LGPL-2.1.txt"), 64 << 10); pin(license, LICENSE_SHA);
        byte[] annotationBytes = read(annotations, 2 << 20); pin(annotationBytes, ANNOTATIONS_SHA);
        var originalSources = unzip(published, 32, 1 << 20); var sources = new TreeMap<String, byte[]>(); var changed = new TreeMap<String, String>();
        var selectedNames = new HashSet<String>();
        for (String name : SOURCE_NAMES) selectedNames.add(PREFIX + name + ".java");
        // Enumerate before invoking javac; no implicit source-path discovery or annotation processors.
        Path sourceRoot = real(root.resolve("src"));
        try (var paths = Files.walk(sourceRoot, 8)) {
            var iterator = paths.iterator(); int count = 0;
            while (iterator.hasNext()) {
                Path path = iterator.next(); require(++count <= 24, "SOURCE_TREE_LIMIT"); real(path);
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                String name = sourceRoot.relativize(path).toString().replace('\\', '/'); require(selectedNames.contains(name), "UNREVIEWED_SOURCE_PATH");
                byte[] value = read(path, 512 << 10); String text = new String(value, StandardCharsets.UTF_8);
                require(text.contains("SPDX-License-Identifier: LGPL-2.1-only")
                        && text.contains("Copyright (c) Forge Development LLC and contributors"), "SOURCE_NOTICE_REMOVED");
                if (!Arrays.equals(value, required(originalSources, name))) {
                    require(text.matches("(?s).*LegacyBridge local modification: [0-9]{4}-[0-9]{2}-[0-9]{2}.*"), "MODIFIED_SOURCE_NOTICE_REQUIRED");
                    changed.put(name, hash(value));
                }
                sources.put(name, value);
            }
        }
        require(sources.keySet().equals(selectedNames), "MISSING_SOURCE_FILE");
        var result = report("LIBRARY_REBUILT_STATICALLY");
        result.put("publishedSourceSha256", SOURCE_SHA); result.put("originalLibrarySha256", BINARY_SHA);
        result.put("compiler", System.getProperty("java.runtime.version")); result.put("compilerOptions", List.of("--release", "8", "-proc:none", "-g", "-encoding", "UTF-8", "-implicit:none"));
        result.put("annotationsSha256", ANNOTATIONS_SHA); result.put("changedSourceFiles", changed);
        var sourceHashes = new TreeMap<String, String>(); sources.forEach((name, value) -> sourceHashes.put(name, hash(value)));
        result.put("sourceHashes", sourceHashes); result.put("upstreamBuildReproduced", false);
        publishDirectory(output, stage -> {
            Path scratch = Files.createDirectory(stage.resolve("compile")); Path classes = Files.createDirectory(scratch.resolve("classes"));
            var compilationUnits = new ArrayList<File>();
            for (var source : sources.entrySet()) { write(scratch, "src/" + source.getKey(), source.getValue()); compilationUnits.add(scratch.resolve("src/" + source.getKey()).toFile()); }
            write(scratch, "annotations.jar", annotationBytes);
            var compiler = ToolProvider.getSystemJavaCompiler(); var diagnostics = new ArrayList<String>();
            try (var manager = compiler.getStandardFileManager(d -> { if (diagnostics.size() < 32) diagnostics.add(d.getKind() + ": " + d.getMessage(Locale.ROOT).substring(0, Math.min(1024, d.getMessage(Locale.ROOT).length()))); }, Locale.ROOT, StandardCharsets.UTF_8)) {
                var options = List.of("--release", "8", "-proc:none", "-g", "-encoding", "UTF-8", "-implicit:none", "-classpath", scratch.resolve("annotations.jar").toString(),
                        "-sourcepath", scratch.resolve("src").toString(), "-d", classes.toString());
                boolean ok = compiler.getTask(new LimitedWriter(), manager, d -> { if (diagnostics.size() < 32) diagnostics.add(d.getKind() + ": " + d.getMessage(Locale.ROOT).substring(0, Math.min(1024, d.getMessage(Locale.ROOT).length()))); }, options, null, manager.getJavaFileObjectsFromFiles(compilationUnits)).call();
                require(ok, "COMPILATION_FAILED: " + String.join(" | ", diagnostics));
            }
            var compiled = new TreeMap<String, byte[]>();
            try (var paths = Files.walk(classes)) {
                var iterator = paths.iterator(); int count = 0;
                while (iterator.hasNext()) { Path path = iterator.next(); require(++count <= 128, "COMPILED_TREE_LIMIT");
                    if (Files.isDirectory(path)) continue;
                    String name = classes.relativize(path).toString().replace('\\', '/');
                    require(name.startsWith(PREFIX) && name.endsWith(".class"), "FOREIGN_COMPILED_CLASS"); compiled.put(name, read(path, 2 << 20));
                }
            }
            result.put("staticAbi", verifyAbi(unzip(original, 128, 2 << 20), compiled));
            var sourceArchive = new TreeMap<>(sources); sourceArchive.put("LICENSE-LGPL-2.1.txt", license);
            sourceArchive.put("RELINK-BUILD.json", new CanonicalReportWriter().toJsonTree(result).getBytes(StandardCharsets.UTF_8));
            byte[] sourceZip = zip(sourceArchive); result.put("rebuiltSourcesSha256", hash(sourceZip));
            var manifest = new Manifest(new ByteArrayInputStream(required(unzip(original, 128, 2 << 20), "META-INF/MANIFEST.MF")));
            manifest.getMainAttributes().putValue("LegacyBridge-Local-Rebuild", "true");
            manifest.getMainAttributes().putValue("LegacyBridge-Rebuilt-Sources-SHA256", hash(sourceZip));
            var manifestBytes = new ByteArrayOutputStream(); manifest.write(manifestBytes);
            compiled.put("META-INF/MANIFEST.MF", manifestBytes.toByteArray()); compiled.put("META-INF/LICENSE-LGPL-2.1.txt", license);
            byte[] jar = zip(compiled); result.put("rebuiltLibrarySha256", hash(jar)); result.put("binaryIdenticalToPublished", Arrays.equals(jar, original));
            write(stage, "srgutils-local.jar", jar); write(stage, "srgutils-local-sources.zip", sourceZip);
            // Delete only this invocation's known compilation files, no user/source trees.
            try (var paths = Files.walk(scratch)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
            json(stage, "build-report.json", result);
        });
        return Collections.unmodifiableMap(result);
    }

    static Map<String, Object> verifyAbi(Map<String, byte[]> original, Map<String, byte[]> replacement) throws IOException {
        var expected = surface(original); var actual = surface(replacement);
        for (var entry : expected.entrySet()) require(actual.containsKey(entry.getKey()) && actual.get(entry.getKey()).containsAll(entry.getValue()), "ABI_REMOVED_OR_CHANGED: " + entry.getKey());
        // Nested Map.of iteration order is randomized across JVMs; these bytes also bind the source archive.
        return Collections.unmodifiableSortedMap(new TreeMap<>(Map.of("status", "PASSED_DECLARATIONS_AND_ASM", "baselineClasses", expected.size(), "rebuiltClasses", actual.size(),
                "classesLoaded", false, "behaviorEquivalent", false, "fullExternalLinkage", false)));
    }
    private static Map<String, Set<String>> surface(Map<String, byte[]> entries) throws IOException {
        var result = new TreeMap<String, Set<String>>();
        for (var entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) continue;
            require(entry.getKey().startsWith(PREFIX), "FOREIGN_LIBRARY_CLASS");
            try {
                BytecodeValidator.validate(entry.getValue()); var node = new ClassNode(); new ClassReader(entry.getValue()).accept(node, 0);
                require(entry.getKey().equals(node.name + ".class") && node.version == Opcodes.V1_8, "LIBRARY_CLASS_IDENTITY_OR_VERSION");
                var members = new TreeSet<String>(); members.add("type " + node.access + " " + node.superName + " " + node.interfaces);
                for (var field : node.fields) if ((field.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0)
                    members.add("field " + field.name + " " + field.desc + " " + field.access + " " + field.value);
                for (var method : node.methods) if ((method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0)
                    members.add("method " + method.name + " " + method.desc + " " + method.access + " " + method.exceptions);
                result.put(node.name, members);
            } catch (RuntimeException malformed) { throw new IOException("INVALID_LIBRARY_BYTECODE: " + entry.getKey(), malformed); }
        }
        require(!result.isEmpty(), "NO_LIBRARY_CLASSES"); return result;
    }

    public static Map<String, Object> replace(Path product, String productHash, Path build, String libraryHash, Path output) throws IOException {
        byte[] snapshot = read(product, MAX_BYTES); pin(snapshot, productHash); var original = product(snapshot);
        Path root = real(build); byte[] library = read(root.resolve("srgutils-local.jar"), 2 << 20); pin(library, libraryHash);
        byte[] sources = read(root.resolve("srgutils-local-sources.zip"), 1 << 20);
        var libraryEntries = unzip(library, 128, 2 << 20); var manifest = new Manifest(new ByteArrayInputStream(required(libraryEntries, "META-INF/MANIFEST.MF")));
        require("net.minecraftforge.srgutils".equals(manifest.getMainAttributes().getValue("Automatic-Module-Name"))
                && "true".equals(manifest.getMainAttributes().getValue("LegacyBridge-Local-Rebuild")), "REBUILD_MODULE_IDENTITY");
        pin(sources, manifest.getMainAttributes().getValue("LegacyBridge-Rebuilt-Sources-SHA256"));
        for (String name : libraryEntries.keySet()) require(name.endsWith(".class") || name.equals("META-INF/MANIFEST.MF") || name.equals("META-INF/LICENSE-LGPL-2.1.txt"), "UNREVIEWED_LIBRARY_RESOURCE");
        var sourceEntries = unzip(sources, 32, 1 << 20); pin(required(sourceEntries, "LICENSE-LGPL-2.1.txt"), LICENSE_SHA);
        for (String name : SOURCE_NAMES) required(sourceEntries, PREFIX + name + ".java");
        var allowedSources = new HashSet<String>(); SOURCE_NAMES.forEach(name -> allowedSources.add(PREFIX + name + ".java"));
        allowedSources.add("LICENSE-LGPL-2.1.txt"); allowedSources.add("RELINK-BUILD.json");
        require(sourceEntries.keySet().equals(allowedSources), "UNREVIEWED_SOURCE_ARCHIVE_ENTRY");
        var result = report("PRIVATE_COPY_RELINKED_STATICALLY"); result.put("productSha256", productHash); result.put("rebuiltLibrarySha256", libraryHash);
        result.put("rebuiltSourcesSha256", hash(sources)); result.put("staticAbi", verifyAbi(unzip(required(original, LIBRARY), 128, 2 << 20), libraryEntries));
        result.put("sourceCorrespondence", "HASH_BOUND_BUILD_RECIPE_NOT_UPSTREAM_RELEASE_ATTESTATION");
        var changed = new TreeMap<>(original); changed.put(LIBRARY, library); changed.put(SOURCES, sources);
        String caveat = "# Private local relinking experiment\n\nThe SRG Utils binary and source archive in this copy are LOCAL REBUILDS, not the original publication described in the historical notice below. See RELINK.json and the rebuilt source archive for hashes/build changes. No native Forge validation or public rights clearance. This does not amend LegacyBridge's license.\n\n## Historical baseline notice (not a claim of unchanged SRG Utils in this copy)\n\n";
        changed.put(NOTICES, (caveat + new String(required(original, NOTICES), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
        changed.put("META-INF/legacybridge/legal/RELINK.json", new CanonicalReportWriter().toJsonTree(result).getBytes(StandardCharsets.UTF_8));
        byte[] converted = zip(changed); var reopened = unzip(converted, 4096, MAX_BYTES);
        int unchanged = 0;
        for (String name : original.keySet()) if (!Set.of(LIBRARY, SOURCES, NOTICES).contains(name)) {
            require(Arrays.equals(original.get(name), reopened.get(name)), "UNRELATED_ENTRY_CHANGED"); unchanged++;
        }
        result.put("unrelatedEntriesByteIdentical", unchanged); result.put("changedEntries", List.of(LIBRARY, SOURCES, NOTICES));
        result.put("addedEntries", List.of("META-INF/legacybridge/legal/RELINK.json")); result.put("privateProductSha256", hash(converted));
        publishDirectory(output, stage -> {
            write(stage, "LegacyBridge-relinked-PRIVATE.jar", converted); json(stage, "relink-report.json", result);
            pin(read(product, MAX_BYTES), productHash); pin(read(root.resolve("srgutils-local.jar"), 2 << 20), libraryHash);
            pin(read(root.resolve("srgutils-local-sources.zip"), 1 << 20), hash(sources));
        });
        return Collections.unmodifiableMap(result);
    }
    private static final class LimitedWriter extends Writer {
        private int count;
        @Override public void write(char[] chars, int offset, int length) throws IOException { count += length; require(count <= 32768, "COMPILER_OUTPUT_LIMIT"); }
        @Override public void flush() { }
        @Override public void close() { }
    }
}
