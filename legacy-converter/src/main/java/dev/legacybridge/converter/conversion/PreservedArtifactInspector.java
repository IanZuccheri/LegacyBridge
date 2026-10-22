package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.archive.*;
import dev.legacybridge.converter.conversion.extract.SubsetRejectionException;
import dev.legacybridge.converter.conversion.logic.VerifiedLogicMappings;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.metadata.ForgeMetadataAnalyzer;
import dev.legacybridge.converter.operation.*;
import dev.legacybridge.converter.policy.NumericVersionRange;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.service.*;
import dev.legacybridge.converter.transformation.*;
import dev.legacybridge.converter.transformation.linkage.OfflineLinkageVerifier;
import dev.legacybridge.runtime.compat.UnsupportedCompatibilityException;
import dev.legacybridge.runtime.compat.logic.PreservedLogicAdapter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

/** Independent readback of an existing pair. Never invokes conversion, trusts a conversion report or emits a JAR. */
public final class PreservedArtifactInspector {
    public static final int CAPTURE_BYTES = 8 << 20, CLASS_BYTES = 512 << 10;

    public PreservedArtifactInspection inspect(PreservedArtifactRequest request, MappingInputs inputs) throws IOException, InputRejectedException {
        var checks = new TreeMap<String,Object>();
        PreservedArtifactInspection.Identity identity = null; int classes = 0, methods = 0;
        try {
            require(inputs.complete(), "ADMISSION_DATA_REQUIRED", "Complete explicit mapping/API inputs are required");
            safePath(request.original()); safePath(request.candidate());
            require(!Files.isSameFile(request.original(), request.candidate()), "ADMISSION_PATH", "Original and candidate alias the same file");
            var source = capture(request.original(), request.originalSha256());
            var target = capture(request.candidate(), request.candidateSha256());
            checks.put("fingerprints", "MATCHED_SELECTED_BYTES");
            OperationControl.stage(OperationStage.SEMANTIC_VALIDATION, "Inspecting preserved artifact identity and dependencies");
            var metadata = new ForgeMetadataAnalyzer().analyze(required(source,"META-INF/mods.toml"));
            require(metadata.valid() && metadata.summary().mods().size() == 1 && "javafml".equals(metadata.summary().modLoader())
                    && NumericVersionRange.contains(metadata.summary().loaderVersion(),"36"), "ADMISSION_METADATA", "Expected one Forge36 JavaFML original");
            var mod = metadata.summary().mods().get(0);
            require(!Set.of("minecraft","forge","legacybridge_runtime").contains(mod.modId()), "ADMISSION_METADATA", "Reserved mod identity");
            Set<String> dependencies = new HashSet<>(); boolean minecraft = false;
            for (var dep : metadata.summary().dependencies()) {
                require(Set.of("minecraft","forge").contains(dep.modId()) && dependencies.add(dep.modId())
                        && dep.ownerModId().equals(mod.modId()) && dep.mandatoryDeclared() && dep.mandatory()
                        && dep.side().equals("BOTH") && dep.ordering().equals("NONE")
                        && NumericVersionRange.contains(dep.versionRange(),dep.modId().equals("minecraft") ? "1.16.5" : "36.2.42"),
                        "ADMISSION_DEPENDENCY", "Unknown, optional, duplicate, side/order-specific or incompatible original dependency");
                minecraft |= dep.modId().equals("minecraft");
            }
            require(minecraft,"ADMISSION_DEPENDENCY","Mandatory Minecraft source dependency is required");
            var manifest = SimpleContentConverter.validateManifest(source.get("META-INF/MANIFEST.MF"));
            String version = mod.version().equals("${file.jarVersion}") ? manifest.getMainAttributes().getValue("Implementation-Version") : mod.version();
            require(version != null && version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}"),"ADMISSION_METADATA","Unresolved mod version");
            identity = new PreservedArtifactInspection.Identity(mod.modId(), version);

            String bindingPath = "META-INF/legacybridge/" + mod.modId() + "/adapters.json";
            var changed = new TreeMap<String,byte[]>();
            changed.put("META-INF/mods.toml", SimpleContentConverter.targetMetadata(required(source,"META-INF/mods.toml"),mod.modId(),version));
            changed.put("META-INF/MANIFEST.MF", PreservedLogicAdapter.targetManifest(version));
            changed.put("pack.mcmeta", SimpleContentConverter.targetPack(required(source,"pack.mcmeta")));
            changed.put(bindingPath, new CanonicalReportWriter().toJsonTree(SimpleContentConverter.canonicalTree(PreservedLogicAdapter.bindingTree())).getBytes(StandardCharsets.UTF_8));
            require(!source.containsKey(bindingPath),"ADMISSION_BINDING","Original already has converted adapter bindings");
            Set<String> expectedEntries = new TreeSet<>(source.keySet()); expectedEntries.addAll(changed.keySet());
            require(expectedEntries.equals(target.keySet()),"ADMISSION_ENTRIES","Target contains added/removed entries outside the exact profile recipe");
            for (var entry : target.entrySet()) if (!entry.getKey().endsWith(".class")) {
                byte[] expected = changed.containsKey(entry.getKey()) ? changed.get(entry.getKey()) : source.get(entry.getKey());
                require(Arrays.equals(expected,entry.getValue()),"ADMISSION_RESOURCE","Resource/metadata/binding differs from the implemented recipe: " + entry.getKey());
            }
            checks.put("metadataResourcesAdapters", "EXACT_IMPLEMENTED_RECIPES_AND_UNCHANGED_BYTES");

            var sourceClasses = classes(source); var targetClasses = classes(target);
            var scan = new LegacyReferenceScanner().scan(sourceClasses); var originals = scan.classNodes();
            var targets = new LegacyReferenceScanner().scan(targetClasses).classNodes();
            boundedNodes(originals); boundedNodes(targets);
            OperationControl.stage(OperationStage.MAPPING_RESOLUTION,"Recompiling exact reviewed rules from selected API data");
            var mappings = VerifiedLogicMappings.compile(originals,scan,inputs,false);
            var resourcePlan = LogicProfileShape.validate(targets,mod.modId(),version);
            ContentResourceValidator.validate(source,resourcePlan);
            var resolver = new MappingResolver(mappings.database(),mappings.adapterBindings(),originals.keySet());
            for (String path : sourceClasses.keySet()) {
                OperationControl.checkpoint();
                require(Arrays.equals(canonical(sourceClasses.get(path),resolver),canonical(targetClasses.get(path),null)),
                        "ADMISSION_BODY","Actual class differs from exact source symbol projection: " + path);
            }
            checks.put("ownedBodies", "EXACT_CANONICAL_CLASS_PROJECTION_EXCLUDING_FRAMES_AND_MAXS");
            OperationControl.stage(OperationStage.BYTECODE_VALIDATION,"Verifying actual original and candidate bytecode/linkage without class loading");
            for(byte[] bytes : sourceClasses.values()) BytecodeValidator.validate(bytes);
            for(byte[] bytes : targetClasses.values()) BytecodeValidator.validate(bytes);
            var verifier = new OfflineLinkageVerifier();
            checks.put("sourceLinkage",verifier.verify(sourceClasses,mappings.sourceApi(),OfflineLinkageVerifier.CallbackPolicy.LOGIC).toTree());
            checks.put("targetLinkage",verifier.verify(targetClasses,mappings.targetApi(),OfflineLinkageVerifier.CallbackPolicy.LOGIC).toTree());
            checks.put("mappingRecipes",mappings.rules());
            checks.put("dataSources",inputs.sources().stream().map(s -> Map.of("id",s.id(),"sha256",s.sha256(),"origin",s.origin())).toList());
            classes = originals.size(); methods = originals.values().stream().mapToInt(n -> n.methods.size()).sum();
            // Return a snapshot only after re-reading both exact selected files. No install/Apply TOCTOU promise.
            rehash(request.original(),request.originalSha256()); rehash(request.candidate(),request.candidateSha256());
            OperationControl.checkpoint();
            return result(request,true,identity,classes,methods,List.of(),checks);
        } catch (Refused refused) {
            return result(request,false,identity,classes,methods,List.of(error(refused.code,refused.getMessage())),checks);
        } catch (SubsetRejectionException | TransformationException | UnsupportedCompatibilityException refused) {
            return result(request,false,identity,classes,methods,List.of(error("ADMISSION_UNSUPPORTED",refused.getMessage())),checks);
        }
    }

    private static PreservedArtifactInspection result(PreservedArtifactRequest r, boolean valid, PreservedArtifactInspection.Identity identity,
            int classes, int methods, List<EngineError> errors, Map<String,Object> checks) {
        return new PreservedArtifactInspection(valid,r.originalSha256(),r.candidateSha256(),Optional.ofNullable(identity),classes,methods,errors,
                SimpleContentConverter.canonicalTree(checks));
    }
    private static EngineError error(String code,String message) { return new EngineError(code,message,
            "Keep both files unchanged. Review the exact profile/data; this inspection never authorizes Apply or accepts an edited report."); }
    private static void safePath(Path path) throws IOException {
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) && path.equals(path.toRealPath()),"ADMISSION_PATH","Expected canonical regular file without observed symlink/junction aliases");
    }
    private static Map<String,byte[]> capture(Path path,String sha) throws InputRejectedException {
        var entries = new TreeMap<String,byte[]>();
        var scan = new SafeJarReader().scan(path,new JarEntryVisitor() {
            long total;
            public boolean requiresBytes(String name) { return true; }
            public long maxCapturedBytes(String name) { return name.endsWith(".class") ? CLASS_BYTES : 1 << 20; }
            public void visit(String name,byte[] bytes) { require((total += bytes.length) <= CAPTURE_BYTES,"ADMISSION_LIMIT","Pair member exceeds 8 MiB captured limit"); entries.put(name,bytes); }
        });
        require(scan.input().sha256().equals(sha),"ADMISSION_HASH","Selected JAR hash changed");
        require(!scan.archive().signed() && !scan.archive().multiRelease() && scan.archive().specialEntryCount() == 0
                && !scan.archive().specialEntriesTruncated(),"ADMISSION_ARCHIVE","Special loader features are not admitted");
        return entries;
    }
    private static void rehash(Path path,String sha) throws IOException, InputRejectedException {
        safePath(path);
        var scan = new SafeJarReader().scan(path,new JarEntryVisitor() {
            public boolean requiresBytes(String name) { return false; }
            public void visit(String name,byte[] bytes) { }
        });
        require(scan.input().sha256().equals(sha),"ADMISSION_HASH","Selected JAR changed during inspection");
    }
    private static Map<String,byte[]> classes(Map<String,byte[]> entries) {
        var result = new TreeMap<String,byte[]>(); entries.forEach((p,b) -> { if(p.endsWith(".class")) result.put(p,b); });
        require(!result.isEmpty() && result.size() <= 32,"ADMISSION_LIMIT","Expected 1..32 owned classes"); return result;
    }
    private static void boundedNodes(Map<String,ClassNode> nodes) {
        int methods=0, instructions=0;
        for(var node:nodes.values()) {
            require(node.version == Opcodes.V1_8 && empty(node.attrs) && node.fields.size() <= 128,
                    "ADMISSION_CLASS","Unsupported class version or opaque attributes/members");
            for(var field:node.fields) require(empty(field.attrs),"ADMISSION_CLASS","Opaque field attribute");
            for(var method:node.methods) {
                require(++methods <= 256 && method.instructions.size() <= 4096 && (instructions += method.instructions.size()) <= 32768
                        && method.maxStack <= 256 && method.maxLocals <= 256 && empty(method.attrs),
                        "ADMISSION_LIMIT","Method, frame or opaque attribute limit");
            }
        }
    }
    private static boolean empty(List<?> list) { return list == null || list.isEmpty(); }

    /** Canonical comparison bytes are never emitted. Actual input frames/maxs are validated separately. */
    static byte[] canonical(byte[] bytes, MappingResolver resolver) {
        ClassWriter writer = new ClassWriter(0); // No class loading or frame computation.
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9,writer) {
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9,super.visitMethod(access,name,desc,signature,exceptions)) {
                    @Override public void visitMaxs(int stack,int locals) { super.visitMaxs(0,0); }
                };
            }
        };
        if(resolver != null) visitor = new ClassRemapper(visitor,new Remapper() {
            @Override public String map(String name) { return resolver.mapClassName(name); }
            @Override public String mapMethodName(String owner,String name,String desc) {
                return resolver.memberRule(owner,name,desc,true).map(r -> r.target().name()).orElse(name);
            }
            @Override public String mapFieldName(String owner,String name,String desc) {
                return resolver.memberRule(owner,name,desc,false).map(r -> r.target().name()).orElse(name);
            }
        });
        new ClassReader(bytes).accept(visitor,ClassReader.SKIP_FRAMES);
        return writer.toByteArray();
    }
    private static byte[] required(Map<String,byte[]> entries,String path) {
        byte[] bytes=entries.get(path); require(bytes != null,"ADMISSION_ENTRY_MISSING","Missing required entry: " + path); return bytes;
    }
    private static void require(boolean pass,String code,String message) { if(!pass) throw new Refused(code,message); }
    private static final class Refused extends RuntimeException {
        private final String code;
        private Refused(String code,String message) { super(message); this.code=code; }
    }
}
