package dev.legacybridge.converter.conversion;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import dev.legacybridge.converter.archive.*;
import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.conversion.extract.*;
import dev.legacybridge.converter.mapping.source.*;
import dev.legacybridge.converter.metadata.ForgeMetadataAnalyzer;
import dev.legacybridge.converter.policy.NumericVersionRange;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.runtime.compat.*;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import dev.legacybridge.runtime.compat.client.ResourcePackAdapter;
import dev.legacybridge.runtime.compat.world.BlockMiningAdapter;
import dev.legacybridge.converter.transformation.*;
import dev.legacybridge.converter.mapping.MappingConfidence;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStage;
import dev.legacybridge.converter.service.ConversionProfile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.Manifest;
import java.util.zip.*;

/** Offline, all-or-nothing lowering of a proven finite registration language. Not a general remapper. */
public final class SimpleContentConverter {
    private final ConversionProfile profile;
    public SimpleContentConverter() { this(ConversionProfile.SIMPLE_CONTENT_V1); }
    public SimpleContentConverter(ConversionProfile profile) {
        if (profile != ConversionProfile.SIMPLE_CONTENT_V1 && profile != ConversionProfile.DATAPACK_RECIPES_V1)
            throw new IllegalArgumentException("Not a bootstrap content profile: " + profile);
        this.profile = profile;
    }

    public ConversionResult convert(Path input, Path output, Path profile) throws IOException, InputRejectedException {
        if (profile == null) throw new IllegalArgumentException("Explicit mapping profile required; prepare local data separately");
        return convert(input, output, new MappingInputsLoader().load(profile));
    }

    public ConversionResult convert(Path input, Path output, MappingInputs mappings) throws IOException, InputRejectedException {
        try (PreparedConversion prepared = prepare(input, output, mappings)) {
            return publish(prepared);
        }
    }

    /** Proves and serializes a candidate privately. Does not create a destination or claim publication. */
    public PreparedConversion prepare(Path input, Path output, MappingInputs mappings) throws IOException, InputRejectedException {
        OperationControl.checkpoint();
        Path destination = destination(input, output);
        CapturedArchive captured = new CapturedArchive();
        var archive = new SafeJarReader().scan(input, captured);
        Map<String,Object> report = new TreeMap<>();
        report.put("schemaVersion", 1);
        report.put("reportType", "LEGACY_CONVERSION_PLAN");
        report.put("supportProfile", profile.id());
        if (profile == ConversionProfile.DATAPACK_RECIPES_V1)
            report.put("resourceValidation", DatapackRecipeContract.report("NOT_RUN", List.of()));
        report.put("input", Map.of("fileName", archive.input().fileName(), "sha256", archive.input().sha256()));
        report.put("targetMinecraft", "1.20.1");
        report.put("targetForge", "47.4.23");
        report.put("runtimeRequired", "legacybridge_runtime");
        report.put("runtimeVersion", AdapterCatalog.RUNTIME_VERSION);
        report.put("sources", mappings.sources().stream().map(s -> Map.of("id",s.id(),"sha256",s.sha256(),"origin",s.origin())).toList());
        if (mappings.scope() == MappingInputs.Scope.SIMPLE_CONTENT_CONTRACT)
            report.put("mappingData", Map.of("scope", mappings.scope().name(), "completeMappingEnvironment", false,
                    "supportProfile", "simple-content-v1"));
        report.put("bytecodeExecuted", false);
        TransformationLog transformationLog = new TransformationLog();
        report.put("pipeline", TransformationPipeline.STAGES);
        try {
            if (mappings.scope() == MappingInputs.Scope.FINITE_COHORT_CONTRACT)
                reject("Finite cohort data supports private cohort inspection only, not a conversion profile.");
            if (mappings.scope() == MappingInputs.Scope.SIMPLE_CONTENT_CONTRACT && profile != ConversionProfile.SIMPLE_CONTENT_V1)
                reject("Embedded source contracts authorize simple-content-v1 only; select explicit data for other profiles.");
            OperationControl.stage(OperationStage.SEMANTIC_VALIDATION, "Checking simple-content-v1 metadata and dependencies");
            if (archive.archive().signed() || archive.archive().multiRelease()) reject("Signed and multi-release JARs are outside simple-content-v1.");
            var metadata = new ForgeMetadataAnalyzer().analyze(captured.entries.get("META-INF/mods.toml"));
            if (!metadata.valid() || metadata.summary().mods().size() != 1) reject("Exactly one valid Forge mod declaration is required.");
            var mod = metadata.summary().mods().get(0);
            if (Set.of("minecraft","forge","legacybridge_runtime").contains(mod.modId())) reject("Reserved modId.");
            if (!"javafml".equals(metadata.summary().modLoader())) reject("Only javafml is supported.");
            if (!NumericVersionRange.contains(metadata.summary().loaderVersion(), "36")) reject("Loader range must include Forge 36.");
            boolean minecraft = false;
            for (var dependency : metadata.summary().dependencies()) {
                OperationControl.checkpoint();
                if (!Set.of("forge","minecraft").contains(dependency.modId())) reject("Third-party dependency requires manual migration: " + dependency.modId());
                if (!"BOTH".equals(dependency.side())) reject("Side-specific dependencies require manual review.");
                String expected = dependency.modId().equals("forge") ? "36.2.42" : "1.16.5";
                if (!NumericVersionRange.contains(dependency.versionRange(), expected)) reject("Dependency does not admit the pinned source: " + dependency.modId());
                minecraft |= dependency.modId().equals("minecraft") && dependency.mandatory();
            }
            if (!minecraft) reject("A mandatory Minecraft dependency including 1.16.5 is required.");
            Manifest manifest = validateManifest(captured.entries.get("META-INF/MANIFEST.MF"));
            String version = mod.version();
            if (version.equals("${file.jarVersion}")) version = manifest.getMainAttributes().getValue("Implementation-Version");
            if (version == null || version.isBlank() || version.length() > 128 || version.contains("${")) reject("Mod version cannot be resolved safely.");
            if (!version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}")) reject("Mod version must be a bounded printable version token; manifest control characters are forbidden.");
            report.put("modId", mod.modId());
            report.put("modVersion", version);
            Map<String,byte[]> classes = new TreeMap<>();
            captured.entries.forEach((name,bytes) -> { if (name.endsWith(".class")) classes.put(name,bytes); });
            TransformationPipeline.Result transformed = new TransformationPipeline().transform(classes, mappings, mod.modId(), version, transformationLog);
            ContentPlan plan = transformed.plan();
            TransformationBindings bindings = transformed.bindings();
            if (plan.items().size() + plan.blocks().size() + plan.sounds().size() > 2048) reject("Runtime definition limit exceeded.");
            OperationControl.stage(OperationStage.RESOURCE_VALIDATION, "Validating resources without running input classes");
            if (profile == ConversionProfile.DATAPACK_RECIPES_V1) {
                var resources = ContentResourceValidator.validateDatapackRecipes(captured.entries, plan);
                report.put("resourceValidation", DatapackRecipeContract.report("PASSED_STATIC", resources));
            } else ContentResourceValidator.validate(captured.entries,plan);
            OperationControl.checkpoint();
            Map<String,byte[]> target = new TreeMap<>(captured.entries);
            classes.keySet().forEach(target::remove);
            bindings.require(AdapterCatalog.METADATA);
            target.put("META-INF/mods.toml", targetMetadata(captured.entries.get("META-INF/mods.toml"), mod.modId(),version));
            bindings.require(AdapterCatalog.PACK);
            target.put("pack.mcmeta", targetPack(captured.entries.get("pack.mcmeta")));
            bindings.require(AdapterCatalog.MANIFEST);
            target.put("META-INF/MANIFEST.MF", ModMetadataAdapter.targetManifest(version));
            String resource = "META-INF/legacybridge/" + mod.modId() + "/content.json";
            byte[] planBytes = new CanonicalReportWriter().toJsonTree(planTree(plan)).getBytes(StandardCharsets.UTF_8);
            if (planBytes.length > 1_048_576) reject("Runtime plan size limit exceeded.");
            target.put(resource, planBytes);
            target.put("META-INF/legacybridge/"+mod.modId()+"/adapters.json",new CanonicalReportWriter()
                    .toJsonTree(canonicalTree(bindings.bindingTree())).getBytes(StandardCharsets.UTF_8));
            if(!plan.blocks().isEmpty()){
                bindings.require(AdapterCatalog.MINING);
                if(target.containsKey(BlockMiningAdapter.TARGET_PATH))reject("Target mining tag collides with source data; no verified merge adapter.");
                target.put(BlockMiningAdapter.TARGET_PATH,new CanonicalReportWriter().toJsonTree(canonicalTree(
                        BlockMiningAdapter.targetTag(mod.modId(),plan.blocks().stream().map(ContentPlan.BlockDefinition::name).toList()))).getBytes(StandardCharsets.UTF_8));
            }
            target.putAll(transformed.classes());
            logResourceChanges(captured.entries, target, mod.modId(), bindings, transformationLog);
            // Final gate immediately before archive serialization/commit; validates the actual candidate bytes.
            OperationControl.stage(OperationStage.BYTECODE_VALIDATION, "Validating final candidate class bytes");
            for (byte[] candidate : transformed.classes().values()) {
                OperationControl.checkpoint();
                BytecodeValidator.validate(candidate, transformed.classes());
            }
            // The entire candidate exists in memory before any output path is created.
            OperationControl.stage(OperationStage.SERIALIZATION, "Serializing a private candidate archive");
            byte[] jar = jar(target);
            report.put("status", "PLANNED");
            report.put("compatibilityStatus", "SUPPORTED_FIXTURE_PROFILE");
            report.put("bytecodeModified", false);
            report.put("replacedClassCount", classes.size());
            report.put("itemCount", plan.items().size());
            report.put("blockCount", plan.blocks().size());
            report.put("soundCount", plan.sounds().size());
            report.put("output", null);
            report.put("candidate", Map.of("fileName",output.getFileName().toString(),"sha256",hash(jar)));
            report.put("preservedResources", captured.entries.entrySet().stream()
                    .filter(e -> Arrays.equals(e.getValue(),target.get(e.getKey())))
                    .map(e -> Map.of("path",e.getKey(),"sha256",hash(e.getValue()))).toList());
            report.put("transformations", bindings.report());
            report.put("adapterBindings", bindings.bindingTree());
            report.put("transformationLog", transformationLog.toTree());
            report.put("transformationsCommitted", false);
            report.put("bytecodeValidation", Map.of("status", "PASSED", "inputClasses", transformed.scannedClassCount(),
                    "outputClasses", transformed.classes().size(), "scannedReferences", transformed.scannedReferenceCount(),
                    "checks", List.of("CheckClassAdapter dataflow", "offline target reference-type analysis"),
                    "modClassesLoaded", false, "jvmLinkageVerified", false));
            report.put("diagnostics", List.of("Only the accepted finite registration grammar is supported; this is not general legacy binary compatibility."));
            OperationControl.stage(OperationStage.PREPARED, "Validated candidate is private; no output has been published");
            return new PreparedConversion(input.toAbsolutePath().normalize(), destination,
                    archive.input().sha256(), jar, canonicalTree(report));
        } catch (SubsetRejectionException | UnsupportedCompatibilityException | TransformationException rejected) {
            if (profile == ConversionProfile.DATAPACK_RECIPES_V1)
                report.put("resourceValidation", DatapackRecipeContract.report("BLOCKED", List.of()));
            report.put("status", "ABORTED");
            report.put("compatibilityStatus", "UNSUPPORTED");
            report.put("bytecodeModified", false);
            report.put("output", null);
            report.put("transformations",List.of());
            report.put("transformationLog", transformationLog.toTree());
            report.put("transformationsCommitted", false);
            report.put("diagnostics", rejected instanceof SubsetRejectionException subset ? subset.diagnostics() : List.of(rejected.getMessage()));
            return new PreparedConversion(input.toAbsolutePath().normalize(), destination,
                    archive.input().sha256(), null, canonicalTree(report));
        }
    }

    /** Publishes once, after a fresh source hash and the cancellation/commit linearization point. */
    public ConversionResult publish(PreparedConversion prepared) throws IOException {
        Objects.requireNonNull(prepared, "prepared");
        synchronized (prepared) {
            if (prepared.closed) throw new IllegalStateException("Prepared conversion is closed or already consumed");
            try {
                OperationControl.checkpoint();
                if (!prepared.convertible()) return new ConversionResult(false, conversionReport(prepared.report));
                var committed = new TreeMap<>(prepared.report);
                committed.put("status", "CONVERTED");
                committed.put("bytecodeModified", true);
                committed.put("output", committed.remove("candidate"));
                committed.put("transformationsCommitted", true);
                ConversionResult result = new ConversionResult(true, conversionReport(committed));
                try {
                    commit(prepared.input, prepared.output, prepared.inputSha256, prepared.bytes);
                } catch (SubsetRejectionException changed) {
                    var aborted = new TreeMap<>(prepared.report);
                    aborted.remove("candidate");
                    aborted.put("status", "ABORTED");
                    aborted.put("compatibilityStatus", "UNSUPPORTED");
                    aborted.put("transformations", List.of());
                    aborted.put("diagnostics", changed.diagnostics());
                    return new ConversionResult(false, conversionReport(aborted));
                }
                return result;
            } finally { prepared.close(); }
        }
    }

    private static Map<String,Object> conversionReport(Map<String,Object> report) {
        var conversion = new TreeMap<>(report);
        conversion.put("reportType", "LEGACY_CONVERSION");
        conversion.put("schemaVersion", 2);
        return canonicalTree(conversion);
    }

    /** Bounded private candidate lifetime. The byte array is never exposed and is released on close. */
    public static final class PreparedConversion implements AutoCloseable {
        private final Path input;
        private final Path output;
        private final String inputSha256;
        private final Map<String,Object> report;
        private byte[] bytes;
        private boolean closed;

        PreparedConversion(Path input, Path output, String inputSha256, byte[] bytes, Map<String,Object> report) {
            this.input = input; this.output = output; this.inputSha256 = inputSha256;
            this.bytes = bytes; this.report = report;
        }
        public synchronized boolean convertible() { return !closed && bytes != null; }
        public Map<String,Object> report() { return report; }
        public String inputSha256() { return inputSha256; }
        public String candidateSha256() {
            Object candidate = report.get("candidate");
            return candidate instanceof Map<?,?> map ? (String)map.get("sha256") : null;
        }
        @Override public synchronized void close() { bytes = null; closed = true; }
    }

    public static Map<String,Object> planTree(ContentPlan plan) {
        Map<String,Object> root = new TreeMap<>();
        root.put("schemaVersion",1); root.put("modId",plan.modId()); root.put("modVersion",plan.modVersion());
        root.put("items",plan.items().stream().map(i -> {
            Map<String,Object> item = new TreeMap<>(); item.put("name",i.name()); item.put("block",i.block());
            var p=i.properties(); Map<String,Object> properties=new TreeMap<>();
            properties.put("stackSize",p.stackSize()); properties.put("durability",p.durability());
            properties.put("fireResistant",p.fireResistant()); properties.put("rarity",p.rarity()); properties.put("creativeTab",p.creativeTab());
            item.put("properties",properties); return item;
        }).toList());
        root.put("blocks",plan.blocks().stream().map(b -> {
            var p=b.properties(); Map<String,Object> props=new TreeMap<>();
            props.put("material",p.material()); props.put("hardness",p.hardness()); props.put("resistance",p.resistance());
            props.put("sound",p.sound()); props.put("requiresCorrectTool",p.requiresCorrectTool()); props.put("noOcclusion",p.noOcclusion());
            return Map.of("name",b.name(),"properties",props);
        }).toList());
        root.put("sounds",plan.sounds().stream().map(s -> Map.of("name",s.name(),"resourceLocation",s.resourceLocation())).toList());
        return canonicalTree(root);
    }

    static Map<String,Object> canonicalTree(Map<String,?> source) {
        Map<String,Object> result = new TreeMap<>();
        source.forEach((key,value) -> result.put(key,canonical(value)));
        return Collections.unmodifiableMap(result);
    }
    private static Object canonical(Object value) {
        if (value instanceof Map<?,?> map) {
            Map<String,Object> sorted=new TreeMap<>();
            map.forEach((key,item)->sorted.put((String)key,canonical(item)));
            return Collections.unmodifiableMap(sorted);
        }
        if (value instanceof List<?> list) return list.stream().map(SimpleContentConverter::canonical).toList();
        return value;
    }

    static Manifest validateManifest(byte[] bytes) throws IOException, SubsetRejectionException {
        Manifest manifest = bytes == null ? new Manifest() : new Manifest(new ByteArrayInputStream(bytes));
        Set<String> allowed=Set.of("manifest-version","implementation-title","implementation-version","implementation-vendor",
                "specification-title","specification-version","specification-vendor","built-by","created-by","build-jdk",
                "build-jdk-spec","timestamp","legacybridge-fixture");
        if (!manifest.getEntries().isEmpty()) reject("Manifest sections/signatures require manual migration.");
        for (Object key:manifest.getMainAttributes().keySet()) if (!allowed.contains(key.toString().toLowerCase(Locale.ROOT)))
            reject("Unapproved manifest attribute: " + key);
        return manifest;
    }

    static byte[] targetMetadata(byte[] bytes,String modId,String version) throws SubsetRejectionException {
        Config config=new TomlParser().parse(new StringReader(new String(bytes,StandardCharsets.UTF_8)));
        Set<String> allowed=Set.of("modLoader","loaderVersion","license","issueTrackerURL","mods","dependencies");
        for (String key:config.valueMap().keySet()) if (!allowed.contains(key)) reject("Unapproved mods.toml root field: "+key);
        if (!(config.get("license") instanceof String license) || license.isBlank()) reject("A declared license is required and will be preserved.");
        List<Config> mods=config.get("mods"); Config mod=mods.get(0);
        Set<String> modKeys=Set.of("modId","version","displayName","description","authors","credits","logoFile","logoBlur","displayURL","updateJSONURL");
        for (String key:mod.valueMap().keySet()) if (!modKeys.contains(key)) reject("Unapproved mods.toml mod field: "+key);
        for (var entry:mod.valueMap().entrySet()) {
            if (entry.getKey().equals("logoBlur")) {
                if (!(entry.getValue() instanceof Boolean)) reject("logoBlur must be boolean.");
            } else if (!(entry.getValue() instanceof String)) reject("Mod metadata field must be a string: "+entry.getKey());
        }
        if (config.contains("issueTrackerURL") && !(config.get("issueTrackerURL") instanceof String)) reject("issueTrackerURL must be a string.");
        Config oldDependencies=config.get("dependencies");
        if (oldDependencies != null) {
            for (Object value:oldDependencies.valueMap().values()) {
                for (Object row:(List<?>)value) {
                    if (!(row instanceof Config dependency)) reject("Unsupported dependency declaration");
                    Config dependency=(Config)row;
                    for (String key:dependency.valueMap().keySet()) if (!Set.of("modId","mandatory","versionRange","ordering","side").contains(key))
                        reject("Unapproved dependency field: "+key);
                }
            }
        }
        Map<String,String> ranges=ModMetadataAdapter.targetRanges("1.16.5","36.2.42");
        mod.set("version",version); config.set("loaderVersion",ranges.get("loaderVersion"));
        List<Config> dependencies=new ArrayList<>();
        dependencies.add(dependency("forge",ranges.get("forge"),"NONE"));
        dependencies.add(dependency("minecraft",ranges.get("minecraft"),"NONE"));
        dependencies.add(dependency("legacybridge_runtime",ranges.get("legacybridge_runtime"),"AFTER"));
        Config deps=Config.inMemory(); deps.set(modId,dependencies); config.set("dependencies",deps);
        StringWriter result=new StringWriter(); TomlWriter writer=new TomlWriter(); writer.setNewline("\n"); writer.write(config,result);
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static Config dependency(String id,String range,String order) {
        Config c=Config.inMemory(); c.set("modId",id);c.set("versionRange",range);c.set("mandatory",true);c.set("ordering",order);c.set("side","BOTH"); return c;
    }
    static byte[] targetPack(byte[] bytes) throws SubsetRejectionException {
        if (bytes==null) reject("pack.mcmeta is required.");
        if (bytes.length > 1_048_576) reject("pack.mcmeta exceeds 1 MiB.");
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            Object parsed=BoundedJsonParser.parse(json);
            if (!(parsed instanceof Map<?,?> root) || !root.keySet().equals(Set.of("pack"))
                    || !(root.get("pack") instanceof Map<?,?> pack) || !pack.keySet().equals(Set.of("description","pack_format"))
                    || !(pack.get("description") instanceof String) || !(pack.get("pack_format") instanceof Number number)
                    || new java.math.BigDecimal(number.toString()).compareTo(java.math.BigDecimal.valueOf(6)) != 0)
                throw new SubsetRejectionException("Only simple pack_format 6 metadata is supported.");
            int targetFormat=ResourcePackAdapter.targetFormat(((Number)pack.get("pack_format")).intValue());
            return new CanonicalReportWriter().toJsonTree(canonicalTree(Map.of("pack",Map.of("description",pack.get("description"),"pack_format",targetFormat)))).getBytes(StandardCharsets.UTF_8);
        } catch (java.nio.charset.CharacterCodingException invalid) {
            throw new SubsetRejectionException("Malformed pack.mcmeta: invalid UTF-8");
        } catch (BoundedJsonParser.JsonSyntaxException invalid) { throw new SubsetRejectionException("Malformed pack.mcmeta: "+invalid.getMessage()); }
    }
    private static void logResourceChanges(Map<String,byte[]> source, Map<String,byte[]> target, String modId,
                                           TransformationBindings bindings, TransformationLog log) {
        Map<String,String> adapters = Map.of("META-INF/mods.toml", AdapterCatalog.METADATA,
                "META-INF/MANIFEST.MF", AdapterCatalog.MANIFEST, "pack.mcmeta", AdapterCatalog.PACK,
                "META-INF/legacybridge/" + modId + "/content.json", AdapterCatalog.BOOTSTRAP,
                "META-INF/legacybridge/" + modId + "/adapters.json", AdapterCatalog.BOOTSTRAP,
                BlockMiningAdapter.TARGET_PATH, AdapterCatalog.MINING);
        Set<String> resources = new TreeSet<>(source.keySet()); resources.addAll(target.keySet());
        for (String path : resources) {
            OperationControl.checkpoint();
            if (path.endsWith(".class") || Arrays.equals(source.get(path), target.get(path))) continue;
            String adapterId = adapters.get(path);
            if (adapterId == null) throw new TransformationException("Resource rewrite has no implemented adapter: " + path);
            var adapter = bindings.require(adapterId);
            log.add(TransformationLog.Kind.RESOURCE_REWRITE, path, resourceFingerprint(source.get(path)),
                    resourceFingerprint(target.get(path)), adapter.id() + ":revision-" + adapter.revision(),
                    MappingConfidence.VERIFIED, adapter.transformation(), adapter.id());
        }
    }
    private static String resourceFingerprint(byte[] bytes) {
        return bytes == null ? "<absent>" : "sha256:" + hash(bytes) + " bytes=" + bytes.length;
    }
    static byte[] jar(Map<String,byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        OutputStream bounded = new FilterOutputStream(bytes) {
            @Override public void write(int value) throws IOException { OperationControl.capturedBytes(1); out.write(value); }
            @Override public void write(byte[] buffer, int offset, int length) throws IOException {
                OperationControl.capturedBytes(length); out.write(buffer, offset, length);
            }
        };
        try (ZipOutputStream zip=new ZipOutputStream(bounded,StandardCharsets.UTF_8)) {
            for (var e:entries.entrySet()) {
                OperationControl.checkpoint();
                ZipEntry entry=new ZipEntry(e.getKey());entry.setTimeLocal(java.time.LocalDateTime.of(1980,1,1,0,0));
                zip.putNextEntry(entry);
                for (int start = 0; start < e.getValue().length; start += 65536) {
                    OperationControl.checkpoint();
                    zip.write(e.getValue(), start, Math.min(65536, e.getValue().length - start));
                }
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    static Path destination(Path input,Path output) throws IOException {
        Path path=output.toAbsolutePath().normalize();
        if (!path.getFileName().toString().endsWith(".jar")) throw new IOException("Output must end in .jar");
        if (Files.isSymbolicLink(path) || (Files.exists(path) && Files.isSameFile(input,path)) || input.toRealPath().equals(path))
            throw new IOException("Output aliases the input or is a symbolic link");
        if (Files.exists(path,LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(path.toString());
        if (!Files.isDirectory(path.getParent())) throw new IOException("Output parent directory must already exist");
        return path.getParent().toRealPath().resolve(path.getFileName());
    }
    private static void commit(Path input,Path output,String expectedHash,byte[] bytes) throws IOException, SubsetRejectionException {
        OperationControl.stage(OperationStage.PRE_PUBLICATION, "Preparing publication; cancellation is still accepted");
        destination(input,output);
        Path tmp=Files.createTempFile(output.getParent(),".legacybridge-conversion-",".tmp");
        boolean published = false;
        try {
            try (OutputStream target = Files.newOutputStream(tmp)) {
                for (int start = 0; start < bytes.length; start += 65536) {
                    OperationControl.checkpoint();
                    target.write(bytes, start, Math.min(65536, bytes.length - start));
                }
            }
            if (!fileHash(input).equals(expectedHash)) reject("Input changed before conversion commit.");
            destination(input,output);
            OperationControl.beginPublication();
            Files.move(tmp,output);
            published = true;
        }
        finally { if (!published) Files.deleteIfExists(tmp); }
    }
    private static String fileHash(Path path) throws IOException {
        try { MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){byte[] b=new byte[65536];int n;long count=0;while((n=in.read(b))!=-1){count+=n;OperationControl.inputBytes(count);if(count>ArchivePolicy.MAX_INPUT_BYTES)throw new IOException("Input exceeded archive byte policy during final hash");d.update(b,0,n);}}return HexFormat.of().formatHex(d.digest()); }
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    static String hash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int start = 0; start < bytes.length; start += 65536) {
                OperationControl.checkpoint();
                digest.update(bytes, start, Math.min(65536, bytes.length - start));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static void reject(String message) throws SubsetRejectionException { throw new SubsetRejectionException(message); }
    static final class CapturedArchive implements JarEntryVisitor {
        final Map<String,byte[]> entries=new TreeMap<>();long size;
        @Override public boolean requiresBytes(String name){return true;}
        @Override public long maxCapturedBytes(String name){return 16L*1024*1024;}
        @Override public void visit(String name,byte[] bytes)throws IOException {size+=bytes.length;if(size>128L*1024*1024)throw new IOException("Simple subset archive exceeds 128 MiB capture limit");entries.put(name,bytes);}
    }
}
