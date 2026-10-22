package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.report.CanonicalReportWriter;
import dev.legacybridge.converter.transformation.TransformationException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.objectweb.asm.Handle;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * Closed, independently reviewed executable-method recipes, NOT general CFG inference.
 * Every instruction operand, branch target, exception region, annotation, field and
 * declaration must match the reviewed source recipe. Debug labels/lines/locals and
 * recomputed frames are not executable recipe input; normal scanner/linkage/debug
 * preservation checks still apply to those attributes. No source class is loaded.
 * A compiler/body change requires another review, not an automatic digest update.
 */
public final class NetworkSnapshotRecipe {
    public static final String MOD_ID = "legacybridge_network";
    public static final String VERSION = "1.0.0";
    // Reviewed against the original Java 8 source AND reobfuscated instruction listing,
    // 2026-09-06. These are executable recipes, never input-JAR or generated target hashes.
    // See docs/NETWORK_SNAPSHOT_PROFILE.md; changing any entry requires a fresh semantic review.
    private static final String OWNER = "dev/legacybridge/fixture/network/NetworkSnapshot#";
    private static final Map<String, String> REVIEWED = Map.ofEntries(
        Map.entry(OWNER + "<clinit>()V", "3b25b7839b42f5ef96831750117ad0290fe2f3864b17ead0906e48188916a55f"),
        Map.entry(OWNER + "<init>()V", "35b34a53099c3d9cf6be3d599184346aca934538014762d524d910a55c133b62"),
        Map.entry(OWNER + "accepted()I", "e37b5c29a65c233a6da208ac6b377e08435eb8b7f3be457adf4497dcc6dfeb1f"),
        Map.entry(OWNER + "accepts(Ljava/lang/String;)Z", "7b0e39bf868cc8b7fca63b83cc8558e31f5b3b87661cf3a99eae4d34c457b614"),
        Map.entry(OWNER + "apply(Ldev/legacybridge/fixture/network/SnapshotMessage;Lnet/minecraft/network/NetworkManager;)V", "9d7a2383efda2139d4ae97fc7b163b3deea97cf2a7cf84521a9d09a69196e39e"),
        Map.entry(OWNER + "decode(Lnet/minecraft/network/PacketBuffer;)Ldev/legacybridge/fixture/network/SnapshotMessage;", "6ed666733e076f9329290c1ad20bb7d4895fe08cb29130f2aaa2c5de84a55f70"),
        Map.entry(OWNER + "encode(Ldev/legacybridge/fixture/network/SnapshotMessage;Lnet/minecraft/network/PacketBuffer;)V", "bd5f0f3865efa3c110b927e7c070c3334b479223fee7eaf7b1682974d9c04bdf"),
        Map.entry(OWNER + "handle(Ldev/legacybridge/fixture/network/SnapshotMessage;Ljava/util/function/Supplier;)V", "613b0e527dbc59fc3860d3925eee725d5d82489ccd8318ab0a59219309dd883e"),
        Map.entry(OWNER + "header", "749c3929d3364d87043130f778fab05897fdce122a73d10db0880dad3c4a6b05"),
        Map.entry(OWNER + "lambda$handle$0(Ldev/legacybridge/fixture/network/SnapshotMessage;Lnet/minecraftforge/fml/network/NetworkEvent$Context;)V", "e68c81fcf99a5b5f5a554a98e365edd00e5f35539d6add27d7c2359490e20519"),
        Map.entry(OWNER + "lambda$sendTo$1(Lnet/minecraft/entity/player/ServerPlayerEntity;)Lnet/minecraft/entity/player/ServerPlayerEntity;", "4e75d87b0c540e84776accdb2aedc92df80d285cfe217851555d89dab68b2f1a"),
        Map.entry(OWNER + "lastThreadId()J", "fd2ae710a4c7a413583c020c29f26e4d256236420598906ec63b80f42e1f9a76"),
        Map.entry(OWNER + "protocol()Ljava/lang/String;", "227c868085b0afac59f5bb4083c2ecea522d4c65822ccda01c193859bdbf847b"),
        Map.entry(OWNER + "reject(Ljava/lang/String;)V", "190d3e5320052f82dfdc8ea6a0782340fc65cd2e5068796db0bb491bd5b64bee"),
        Map.entry(OWNER + "rejected()I", "d9553f123e1848b0debd46a09d7c78c6f82bfcd2a474b13710a78e30da08c37f"),
        Map.entry(OWNER + "sendTo(Lnet/minecraft/entity/player/ServerPlayerEntity;II)V", "b5838faefce3f0e0c30822d8028771f766037fd589ad997e9120ebf5f5a78ab7"),
        Map.entry(OWNER + "sequence()I", "e06120613cf06f99a00d6f046f92c1c93537b3095c9fcb47b624d4c81c123236"),
        Map.entry(OWNER + "value()I", "d705cfa4080d8933ac23843b8935da809599bb51c676e6ca0ee7fa806b12394a"),
        Map.entry("dev/legacybridge/fixture/network/SnapshotMessage#<init>(II)V", "517328857a7fd49397ce9e388b8c4e233f9eaccf5dcf1f48a2e29984342f4b24"),
        Map.entry("dev/legacybridge/fixture/network/SnapshotMessage#header", "e3fd388077a6c0daccfa607d99a4c0e0e68ff5c375e3db2ce6ad7cf703e5ae73")
    );
    private NetworkSnapshotRecipe() { }

    /** Before scanner frame expansion or Analyzer allocation; ignores frames ONLY for this recipe check. */
    public static Map<String, Object> validateRaw(Map<String, byte[]> entries, String modId, String version) {
        require(entries != null && !entries.isEmpty() && entries.size() <= 8, "Expected 1..8 reviewed classes");
        Map<String, ClassNode> nodes = new TreeMap<>();
        for (var entry : entries.entrySet()) {
            OperationControl.checkpoint();
            NetworkClassLimits.check(entry.getValue());
            ClassNode node = new ClassNode();
            try { new ClassReader(entry.getValue()).accept(node, ClassReader.SKIP_FRAMES); }
            catch (dev.legacybridge.converter.operation.OperationStoppedException stopped) { throw stopped; }
            catch (RuntimeException malformed) { throw failure("Malformed original class structure"); }
            catch (StackOverflowError excessiveNesting) { throw failure("Excessively nested original class structure"); }
            require(entry.getKey().equals(node.name + ".class") && nodes.putIfAbsent(node.name, node) == null,
                    "Duplicate or misplaced original class");
        }
        return validate(nodes, modId, version);
    }

    public static Map<String, Object> validate(Map<String, ClassNode> nodes, String modId, String version) {
        require(MOD_ID.equals(modId) && VERSION.equals(version), "Unreviewed mod identity/version");
        Map<String, String> actual = fingerprints(nodes);
        require(!REVIEWED.isEmpty() && actual.keySet().equals(REVIEWED.keySet()), "Unreviewed class/member set");
        for (var entry : actual.entrySet()) require(entry.getValue().equals(REVIEWED.get(entry.getKey())),
                "Unreviewed executable recipe: " + entry.getKey());
        Map<String, Object> result = new TreeMap<>(Map.of("schemaVersion", 1, "profile", "network-snapshot-v1",
                "admission", "EXACT_REVIEWED_EXECUTABLE_METHOD_RECIPES_NOT_GENERAL_PATTERN_INFERENCE",
                "recipes", actual, "direction", "PLAY_TO_CLIENT", "protocol", "1",
                "discriminator", 0, "payloadBytes", 8,
                "serverMutation", "UNSUPPORTED", "runtimeValidation", "NOT_RUN"));
        result.put("channels", List.of(Map.of("id", "legacybridge_network:snapshot", "protocol", "1",
                "clientPredicate", "EXACT_STRING_EQUALITY", "serverPredicate", "EXACT_STRING_EQUALITY",
                "requiredOnBothPeers", true, "messages", List.of(Map.of("id", 0,
                        "class", "dev/legacybridge/fixture/network/SnapshotMessage", "direction", "PLAY_TO_CLIENT",
                        "codec", "EXACT_EIGHT_BYTES_BIG_ENDIAN_INT_SEQUENCE_THEN_INT_VALUE",
                        "recipient", "ONE_EXPLICIT_PLAYER_VIA_PLAYER_WITH_SUPPLIER")))));
        result.put("context", Map.of("capture", "IMMUTABLE_MESSAGE_AND_NATIVE_CONTEXT_NO_BUFFER_ESCAPE",
                "thread", "EXPLICIT_ENQUEUE_WORK_TO_NATIVE_RECEPTION_MAIN_THREAD",
                "connection", "LIVE_IDENTITY_BOUND_INSIDE_QUEUED_TASK",
                "sequence", "FIRST_ONE_THEN_EXACT_SUCCESSOR_NO_WRAP",
                "pendingWorkBound", "NATIVE_QUEUE_NOT_APPLICATION_CAPACITY_LIMITED"));
        result.put("rejections", Map.of("malformedBody", "COUNTED_LOGGED_NO_APPLY_NO_DISCONNECT",
                "unknownId", "NATIVE_FORGE_ERROR_NO_HANDLER_NO_PROMISED_DISCONNECT",
                "wrongDirection", "NATIVE_FORGE_VALIDATION_BEFORE_DECODE",
                "duplicateOrGap", "COUNTED_LOGGED_NO_STATE_ADVANCE"));
        result.put("unsupported", List.of("C2S state mutation", "permission/distance/world/chunk access",
                "arbitrary dependencies/codecs/recipients", "cross-version connections", "flood resistance"));
        return Collections.unmodifiableMap(result);
    }

    /** Test/audit utility: returns measurements only, never registers or approves a recipe. */
    public static Map<String, String> fingerprints(Map<String, ClassNode> nodes) {
        require(nodes != null && !nodes.isEmpty() && nodes.size() <= 8, "Expected 1..8 reviewed classes");
        Map<String, String> result = new TreeMap<>();
        int total = 0;
        for (var pair : new TreeMap<>(nodes).entrySet()) {
            OperationControl.checkpoint();
            ClassNode n = pair.getValue();
            require(n != null && pair.getKey().equals(n.name) && n.version == Opcodes.V1_8,
                    "Invalid Java 8 class identity");
            require(n.module == null && empty(n.attrs) && empty(n.recordComponents) && empty(n.permittedSubclasses)
                    && n.nestHostClass == null && empty(n.nestMembers), "Unsupported class metadata");
            require(n.fields.size() <= 64 && n.methods.size() <= 64, "Member bound exceeded");
            Map<String, Object> header = new TreeMap<>();
            header.put("name", n.name); header.put("version", n.version); header.put("access", n.access);
            header.put("super", n.superName); header.put("interfaces", n.interfaces); header.put("signature", n.signature);
            header.put("outer", Arrays.asList(n.outerClass, n.outerMethod, n.outerMethodDesc));
            header.put("inner", n.innerClasses.stream().map(i -> Arrays.asList(i.name, i.outerName, i.innerName, i.access)).toList());
            header.put("visible", annotations(n.visibleAnnotations)); header.put("invisible", annotations(n.invisibleAnnotations));
            require(empty(n.visibleTypeAnnotations) && empty(n.invisibleTypeAnnotations), "Unsupported class type annotation");
            List<Object> fields = new ArrayList<>();
            Set<String> fieldKeys = new HashSet<>();
            for (FieldNode f : n.fields) {
                require(fieldKeys.add(f.name + f.desc) && empty(f.attrs) && empty(f.visibleTypeAnnotations)
                        && empty(f.invisibleTypeAnnotations), "Duplicate/unreviewed field metadata");
                fields.add(Arrays.asList(f.access, f.name, f.desc, f.signature, constant(f.value),
                        annotations(f.visibleAnnotations), annotations(f.invisibleAnnotations)));
            }
            header.put("fields", fields);
            result.put(n.name + "#header", digest(header));
            for (MethodNode m : n.methods) {
                require(m.maxStack >= 0 && m.maxStack <= 64 && m.maxLocals >= 0 && m.maxLocals <= 64,
                        "Method frame allocation bound exceeded");
                require((m.access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SYNCHRONIZED)) == 0,
                        "No native/abstract/synchronized method");
                require(m.instructions.size() <= 4096 && (total += m.instructions.size()) <= 16384, "Instruction bound exceeded");
                String key = n.name + "#" + m.name + m.desc;
                require(result.putIfAbsent(key, digest(method(m))) == null, "Duplicate method");
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Object> method(MethodNode m) {
        require(empty(m.attrs) && m.annotationDefault == null && empty(m.visibleTypeAnnotations)
                && empty(m.invisibleTypeAnnotations) && empty(m.visibleLocalVariableAnnotations)
                && empty(m.invisibleLocalVariableAnnotations), "Unsupported method metadata");
        require(m.visibleParameterAnnotations == null && m.invisibleParameterAnnotations == null,
                "Parameter annotations are not reviewed");
        Map<LabelNode, Integer> labels = new IdentityHashMap<>();
        int index = 0;
        for (var i : m.instructions) {
            if (i instanceof LabelNode l) labels.put(l, index);
            if (i.getOpcode() >= 0) index++;
        }
        List<Object> code = new ArrayList<>();
        for (var i : m.instructions) {
            require(empty(i.visibleTypeAnnotations) && empty(i.invisibleTypeAnnotations), "Instruction type annotations are not reviewed");
            if (i instanceof LabelNode || i instanceof FrameNode || i instanceof LineNumberNode) continue;
            List<Object> value = new ArrayList<>(); value.add(i.getOpcode());
            if (i instanceof InsnNode) { /* operand-free instruction */ }
            else if (i instanceof IntInsnNode x) value.add(x.operand);
            else if (i instanceof VarInsnNode x) value.add(x.var);
            else if (i instanceof TypeInsnNode x) value.add(x.desc);
            else if (i instanceof FieldInsnNode x) value.addAll(List.of(x.owner, x.name, x.desc));
            else if (i instanceof MethodInsnNode x) value.addAll(List.of(x.owner, x.name, x.desc, x.itf));
            else if (i instanceof InvokeDynamicInsnNode x) {
                value.addAll(List.of(x.name, x.desc, constant(x.bsm), Arrays.stream(x.bsmArgs).map(NetworkSnapshotRecipe::constant).toList()));
            } else if (i instanceof JumpInsnNode x) value.add(label(labels, x.label));
            else if (i instanceof LdcInsnNode x) value.add(constant(x.cst));
            else if (i instanceof IincInsnNode x) value.addAll(List.of(x.var, x.incr));
            else if (i instanceof TableSwitchInsnNode x) value.addAll(List.of(x.min, x.max, label(labels, x.dflt), x.labels.stream().map(l -> label(labels, l)).toList()));
            else if (i instanceof LookupSwitchInsnNode x) value.addAll(List.of(x.keys, label(labels, x.dflt), x.labels.stream().map(l -> label(labels, l)).toList()));
            else if (i instanceof MultiANewArrayInsnNode x) value.addAll(List.of(x.desc, x.dims));
            else throw failure("Unknown instruction node");
            code.add(value);
        }
        List<Object> handlers = new ArrayList<>();
        require(m.tryCatchBlocks.size() <= 64, "Exception-region bound exceeded");
        for (var h : m.tryCatchBlocks) {
            require(empty(h.visibleTypeAnnotations) && empty(h.invisibleTypeAnnotations), "Handler annotations unsupported");
            handlers.add(Arrays.asList(label(labels, h.start), label(labels, h.end), label(labels, h.handler), h.type));
        }
        Map<String, Object> tree = new TreeMap<>();
        tree.put("access", m.access); tree.put("name", m.name); tree.put("descriptor", m.desc); tree.put("signature", m.signature);
        tree.put("exceptions", m.exceptions); tree.put("handlers", handlers); tree.put("instructions", code);
        tree.put("visible", annotations(m.visibleAnnotations)); tree.put("invisible", annotations(m.invisibleAnnotations));
        tree.put("parameters", m.parameters == null ? null : m.parameters.stream().map(p -> Arrays.asList(p.name, p.access)).toList());
        return tree;
    }

    private static Object constant(Object value) {
        if (value == null || value instanceof String || value instanceof Integer || value instanceof Long || value instanceof Boolean) return value;
        if (value instanceof Float f) return List.of("floatBits", Float.floatToRawIntBits(f));
        if (value instanceof Double d) return List.of("doubleBits", Double.doubleToRawLongBits(d));
        if (value instanceof Type t) return List.of("type", t.getDescriptor());
        if (value instanceof Handle h) return List.of("handle", h.getTag(), h.getOwner(), h.getName(), h.getDesc(), h.isInterface());
        throw failure("Unreviewed constant kind: " + value.getClass().getName());
    }
    private static Object annotations(List<AnnotationNode> annotations) {
        if (annotations == null) return List.of();
        List<Object> result = new ArrayList<>();
        for (var a : annotations) {
            require(a.desc.equals("Lnet/minecraftforge/fml/common/Mod;") && a.values != null && a.values.size() == 2
                    && a.values.get(0).equals("value") && a.values.get(1) instanceof String, "Only literal @Mod is reviewed");
            result.add(List.of(a.desc, a.values));
        }
        return result;
    }
    private static int label(Map<LabelNode, Integer> labels, LabelNode label) {
        Integer result = labels.get(label); require(result != null, "Unknown branch/handler label"); return result;
    }
    private static String digest(Map<String, Object> tree) {
        return SimpleContentConverter.hash(new CanonicalReportWriter().toJsonTree(tree).getBytes(StandardCharsets.UTF_8));
    }
    private static boolean empty(Collection<?> values) { return values == null || values.isEmpty(); }
    private static void require(boolean condition, String reason) { if (!condition) throw failure(reason); }
    private static TransformationException failure(String reason) { return new TransformationException("NETWORK_RECIPE_UNSUPPORTED: " + reason); }
}
