package dev.legacybridge.product.install;

import dev.legacybridge.converter.archive.ArchiveScan;
import dev.legacybridge.converter.archive.JarEntryVisitor;
import dev.legacybridge.converter.archive.SafeJarReader;
import dev.legacybridge.converter.config.BoundedJsonParser;
import dev.legacybridge.converter.conversion.ContentResourceValidator;
import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.metadata.ForgeMetadataAnalyzer;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.report.MetadataSummary;
import dev.legacybridge.converter.transformation.BytecodeValidator;
import dev.legacybridge.runtime.SimpleContentPlan;
import dev.legacybridge.runtime.compat.AdapterBindings;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.registry.ModMetadataAdapter;
import dev.legacybridge.runtime.compat.world.BlockMiningAdapter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Manifest;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Rechecks the existing generated profile, not a second converter or a class loader. */
final class StagedArtifactValidator {
    private StagedArtifactValidator() { }
    record ValidatedCandidate(String modId, String modVersion) { }

    static ValidatedCandidate validate(Path payload, String expectedSha) throws Exception {
        Map<String, byte[]> entries = new TreeMap<>();
        ArchiveScan scan = new SafeJarReader().scan(payload, new JarEntryVisitor() {
            private long captured;
            public boolean requiresBytes(String name) { return true; }
            public long maxCapturedBytes(String name) { return 16L << 20; }
            public void visit(String name, byte[] bytes) throws IOException {
                if ((captured += bytes.length) > (64L << 20)) throw new IOException("Staged captured payload exceeds 64 MiB");
                entries.put(name, bytes);
            }
        });
        require(scan.input().sha256().equals(expectedSha), "Staged hash differs from the session conversion result");
        require(scan.archive().specialEntryCount() == 0 && !scan.archive().specialEntriesTruncated(), "Staged archive has special loader features");
        var analyzed = new ForgeMetadataAnalyzer().analyze(required(entries, "META-INF/mods.toml"));
        // The exact implemented runtime range contains SNAPSHOT and is outside the
        // analyzer's numeric-only heuristic. Its literal ABI range is verified below.
        require(analyzed.valid() && analyzed.findings().stream().allMatch(finding -> finding.code().equals("LB-M019")),
                "Staged metadata is invalid, unknown or truncated");
        MetadataSummary metadata = analyzed.summary();
        require(metadata.mods().size() == 1 && metadata.modLoader().equals("javafml"), "Only one generated JavaFML mod can be installed");
        String modId = metadata.mods().get(0).modId();
        require(!List.of("minecraft", "forge", "legacybridge_runtime").contains(modId), "Reserved mod identity");
        Map<String, String> ranges = ModMetadataAdapter.targetRanges("1.16.5", "36.2.42");
        require(metadata.loaderVersion().equals(ranges.get("loaderVersion")) && metadata.dependencies().size() == 3,
                "Generated target dependencies differ from the implemented adapter");
        var dependencies = new java.util.HashSet<String>();
        for (var dependency : metadata.dependencies()) {
            require(dependencies.add(dependency.modId()) && dependency.ownerModId().equals(modId)
                    && dependency.mandatoryDeclared() && dependency.mandatory() && dependency.side().equals("BOTH")
                    && dependency.versionRange().equals(ranges.get(dependency.modId()))
                    && dependency.ordering().equals(dependency.modId().equals("legacybridge_runtime") ? "AFTER" : "NONE"),
                    "Generated dependency identity, range, ordering or side differs");
        }
        require(dependencies.equals(java.util.Set.of("minecraft", "forge", "legacybridge_runtime")), "Unsupported dependency closure");
        Manifest manifest = new Manifest(new ByteArrayInputStream(required(entries, "META-INF/MANIFEST.MF")));
        require("simple-content-v1".equals(manifest.getMainAttributes().getValue("LegacyBridge-Profile")), "Missing supported profile marker");
        require(Arrays.equals(required(entries, "META-INF/MANIFEST.MF"), ModMetadataAdapter.targetManifest(metadata.mods().get(0).version())),
                "Generated manifest differs from the implemented adapter");
        String planPath = "META-INF/legacybridge/" + modId + "/content.json";
        SimpleContentPlan plan = SimpleContentPlan.read(new ByteArrayInputStream(required(entries, planPath)), modId);
        require(plan.modVersion().equals(metadata.mods().get(0).version()), "Plan version differs from metadata");
        String bindingPath = "META-INF/legacybridge/" + modId + "/adapters.json";
        AdapterBindings.verify(required(entries, bindingPath), plan);
        String owner = "dev/legacybridge/generated/" + modId + "/ConvertedMod";
        require(entries.keySet().stream().filter(name -> name.endsWith(".class")).toList().equals(List.of(owner + ".class")),
                "Staged output must contain only its one generated mod bootstrap");
        byte[] bootstrap = required(entries, owner + ".class");
        BytecodeValidator.validate(bootstrap, Map.of(owner, bootstrap));
        verifyBootstrap(bootstrap, owner, modId, planPath);
        ContentPlan content = content(plan);
        Map<String, byte[]> resources = new TreeMap<>(entries);
        resources.remove(planPath); resources.remove(bindingPath);
        if (!plan.blocks().isEmpty()) {
            String expected = new CanonicalReportWriter().toJsonTree(new TreeMap<>(BlockMiningAdapter.targetTag(modId,
                    plan.blocks().stream().map(SimpleContentPlan.BlockDefinition::name).toList())));
            require(json(required(entries, BlockMiningAdapter.TARGET_PATH)).equals(BoundedJsonParser.parse(expected)), "Generated mining tag differs from its adapter");
            resources.remove(BlockMiningAdapter.TARGET_PATH);
        }
        ContentResourceValidator.validate(resources, content);
        Object pack = json(required(entries, "pack.mcmeta"));
        require(pack instanceof Map<?, ?> root && root.keySet().equals(java.util.Set.of("pack"))
                && root.get("pack") instanceof Map<?, ?> data && data.keySet().equals(java.util.Set.of("description", "pack_format"))
                && data.get("description") instanceof String && data.get("pack_format") instanceof Number format
                && new java.math.BigDecimal(format.toString()).compareTo(java.math.BigDecimal.valueOf(15)) == 0,
                "Target pack format is not the supported format 15");
        return new ValidatedCandidate(modId, plan.modVersion());
    }

    private static void verifyBootstrap(byte[] bytes, String owner, String modId, String planPath) throws IOException {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        require(node.name.equals(owner) && node.superName.equals("java/lang/Object") && node.version == Opcodes.V17
                && node.access == (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER)
                && node.fields.isEmpty() && node.interfaces.isEmpty() && node.methods.size() == 1,
                "Generated bootstrap header differs from the supported recipe");
        require(node.visibleAnnotations != null && node.visibleAnnotations.size() == 1
                && node.visibleAnnotations.get(0).desc.equals("Lnet/minecraftforge/fml/common/Mod;")
                && node.visibleAnnotations.get(0).values.equals(List.of("value", modId)), "Bootstrap @Mod identity differs");
        var method = node.methods.get(0);
        var code = Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList();
        var adapter = AdapterCatalog.require(AdapterCatalog.BOOTSTRAP);
        require(method.name.equals("<init>") && method.desc.equals("()V") && method.access == Opcodes.ACC_PUBLIC
                && method.tryCatchBlocks.isEmpty() && code.size() == 8
                && code.get(0) instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 0
                && call(code.get(1), Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V")
                && code.get(2) instanceof LdcInsnNode id && modId.equals(id.cst)
                && code.get(3) instanceof LdcInsnNode type && Type.getObjectType(owner).equals(type.cst)
                && call(code.get(4), Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;")
                && code.get(5) instanceof LdcInsnNode resource && planPath.equals(resource.cst)
                && call(code.get(6), Opcodes.INVOKESTATIC, adapter.owner(), adapter.method(), adapter.descriptor())
                && code.get(7).getOpcode() == Opcodes.RETURN, "Bootstrap contains calls outside its implemented adapter recipe");
    }

    private static boolean call(Object instruction, int opcode, String owner, String name, String descriptor) {
        return instruction instanceof MethodInsnNode call && call.getOpcode() == opcode && !call.itf
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor);
    }
    private static ContentPlan content(SimpleContentPlan plan) {
        return new ContentPlan(plan.modId(), plan.modVersion(), plan.items().stream().map(item -> {
            var p = item.properties(); return new ContentPlan.ItemDefinition(item.name(), item.block(),
                    new ContentPlan.ItemProperties(p.stackSize(), p.durability(), p.fireResistant(), p.rarity(), p.creativeTab()));
        }).toList(), plan.blocks().stream().map(block -> {
            var p = block.properties(); return new ContentPlan.BlockDefinition(block.name(),
                    new ContentPlan.BlockProperties(p.material(), p.hardness(), p.resistance(), p.sound(), p.requiresCorrectTool(), p.noOcclusion()));
        }).toList(), plan.sounds().stream().map(sound -> new ContentPlan.SoundDefinition(sound.name(), sound.resourceLocation())).toList());
    }
    private static Object json(byte[] bytes) throws Exception { return BoundedJsonParser.parse(new String(bytes, StandardCharsets.UTF_8)); }
    private static byte[] required(Map<String, byte[]> entries, String name) throws IOException {
        byte[] bytes = entries.get(name); require(bytes != null, "Missing generated resource: " + name); return bytes;
    }
    static void require(boolean condition, String reason) throws IOException { if (!condition) throw new IOException(reason); }
}
