package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;
import static dev.legacybridge.converter.conversion.ClientConfigContract.*;

/**
 * Legacy API: BooleanProperty, native Block state builder and the AbstractBlock use callback.
 * Target API: the corresponding state APIs and BlockBehaviour use callback on Level.
 * Transformation: preserve the complete owned graph and exact client/server control flow;
 * the caller independently proves native declarations/linkage and the bounded STONE recipe.
 * Behavior differences: native dispatch, prediction/acknowledgements and neighbor reactions
 * differ between versions. CONSUME does not assert success of the ignored setBlock result.
 * Known limitations: one active=false property, client SUCCESS without writes, and one
 * server setBlock(callbackPosition, callbackState.cycle(property), 3) before CONSUME.
 * No other world/player/inventory effects, networking, callbacks or runtime acceptance.
 * Production spellings below guard structure; real mapping/API proof is separately mandatory.
 */
public final class BooleanToggleBlockContract {
    private static final String OLD_BLOCK = "net/minecraft/block/Block";
    private static final String TARGET_BLOCK = "net/minecraft/world/level/block/Block";

    private BooleanToggleBlockContract() { }

    public record Proof(String entry, String block, String propertyField, PlainBlockContract.Proof plainProof) {
        public Map<String,Object> toTree() {
            return Map.of("schemaVersion", 1, "contract", "boolean-toggle-block-with-item-v1",
                "entry", entry, "block", block, "propertyField", propertyField,
                "plainContent", plainProof.toTree(),
                "state", Map.of("property", "active", "values", List.of(false, true), "default", false,
                    "stateBuilder", "SINGLE_OWNED_STATIC_NATIVE_PROPERTY"),
                "interaction", Map.of("client", "SUCCESS_WITHOUT_WRITE", "server", "CYCLE_CALLBACK_STATE_ONCE",
                    "world", "CALLBACK_ARGUMENT", "position", "CALLBACK_ARGUMENT", "updateFlags", 3,
                    "writes", 1, "serverResult", "CONSUME", "setBlockResult", "IGNORED_NOT_ASSUMED_SUCCESS",
                    "dispatch", "NATIVE_USE_CALLBACK", "additionalEffects", "NONE"),
                "runtimeValidation", "NOT_RUN", "classesExecuted", false);
        }
    }

    public static Proof inspect(Map<String,ClassNode> nodes) {
        try {
            return inspectClosed(nodes);
        } catch (TransformationException refusal) {
            if (refusal.getMessage().startsWith("BOOLEAN_TOGGLE_BLOCK_UNSUPPORTED:")) throw refusal;
            throw new TransformationException("BOOLEAN_TOGGLE_BLOCK_UNSUPPORTED: " + refusal.getMessage(), refusal);
        }
    }

    private static Proof inspectClosed(Map<String,ClassNode> nodes) {
        OperationControl.checkpoint();
        require(nodes != null && nodes.size() == 2, "Exactly one entry and one owned native Block subclass");
        for (var pair : nodes.entrySet()) {
            var n = pair.getValue();
            require(n != null && n.name != null && n.name.equals(pair.getKey())
                && !LegacyReferenceScanner.isProtectedDefinition(n.name), "Owned class identity");
        }
        var entries = nodes.values().stream().filter(n -> "java/lang/Object".equals(n.superName)).toList();
        var blocks = nodes.values().stream().filter(n -> OLD_BLOCK.equals(n.superName) || TARGET_BLOCK.equals(n.superName)).toList();
        require(entries.size() == 1 && blocks.size() == 1, "One Object entry and one direct native Block subclass");
        var entry = entries.get(0); var block = blocks.get(0);
        var api = new Names(TARGET_BLOCK.equals(block.superName));
        checkSubclass(block, api);
        var projected = projectedEntry(entry, block, api);
        var plainProof = PlainBlockContract.inspect(Map.of(projected.name, projected));
        return new Proof(entry.name, block.name, block.fields.get(0).name, plainProof);
    }

    /**
     * Checked deep-copied admission view. Only the sole owned NEW/constructor owner becomes
     * native Block to reuse registration/property checks. Never serialize this projection;
     * conversion must retain the subclass, boolean state and guarded world-write body.
     */
    public static Map<String,ClassNode> plainProjection(Map<String,ClassNode> nodes) {
        var proof = inspect(nodes);
        var block = nodes.get(proof.block());
        var projection = projectedEntry(nodes.get(proof.entry()), block, new Names(TARGET_BLOCK.equals(block.superName)));
        return Map.of(projection.name, projection);
    }

    /** Content identity only; two-state resources and external native APIs need separate checks. */
    public static ContentPlan validate(Map<String,ClassNode> nodes, String modId, String version) {
        return PlainBlockContract.validate(plainProjection(nodes), modId, version);
    }

    private static ClassNode projectedEntry(ClassNode entry, ClassNode block, Names api) {
        require(entry.methods != null, "Entry methods");
        var factories = entry.methods.stream().filter(m -> m != null && ("()L" + api.block + ";").equals(m.desc)).toList();
        require(factories.size() == 1, "One native-returning Block factory");
        var originalCode = code(factories.get(0));
        require(originalCode.size() == (api.target ? 12 : 9), "Exact registration factory shape");
        same(originalCode.get(0), type(NEW, block.name));
        same(originalCode.get(originalCode.size() - 2), m(INVOKESPECIAL, block.name, "<init>", "(L" + api.properties + ";)V"));
        var copy = new ClassNode(); entry.accept(copy);
        var factory = copy.methods.get(entry.methods.indexOf(factories.get(0)));
        var instructions = code(factory);
        ((TypeInsnNode) instructions.get(0)).desc = api.block;
        ((MethodInsnNode) instructions.get(instructions.size() - 2)).owner = api.block;
        return copy;
    }

    private static void checkSubclass(ClassNode block, Names api) {
        require(block.version == V1_8 && block.access == (ACC_PUBLIC | ACC_FINAL | ACC_SUPER)
            && empty(block.interfaces) && block.fields != null && block.fields.size() == 1
            && block.methods != null && block.methods.size() == 4,
            "Final Java-8 subclass with one field and exactly four methods");
        require(block.signature == null && block.outerClass == null && block.outerMethod == null
            && block.outerMethodDesc == null && block.nestHostClass == null && empty(block.nestMembers)
            && empty(block.permittedSubclasses) && empty(block.recordComponents) && block.module == null
            && empty(block.attrs) && empty(block.visibleAnnotations) && empty(block.invisibleAnnotations)
            && empty(block.visibleTypeAnnotations) && empty(block.invisibleTypeAnnotations), "No additional subclass metadata contracts");
        var active = block.fields.get(0);
        require(active != null && active.name != null && !active.name.isBlank()
            && active.access == (ACC_PUBLIC | ACC_STATIC | ACC_FINAL)
            && ("L" + api.booleanProperty + ";").equals(active.desc)
            && active.signature == null && active.value == null && empty(active.visibleAnnotations)
            && empty(active.invisibleAnnotations) && empty(active.visibleTypeAnnotations)
            && empty(active.invisibleTypeAnnotations) && empty(active.attrs), "Only final native boolean-property identity");
        String builderDescriptor = "(L" + api.builder + ";)V";
        String builderSignature = "(L" + api.builder + "<L" + api.block + ";L" + api.state + ";>;)V";
        var signatures = new HashSet<String>();
        for (var method : block.methods) {
            OperationControl.checkpoint();
            require(method != null && signatures.add(method.name + method.desc)
                && method.instructions != null && method.instructions.size() <= 128, "Bounded unique subclass methods");
            require(method.signature == null || method.name.equals(api.create) && method.desc.equals(builderDescriptor)
                && method.signature.equals(builderSignature), "Only exact native builder generic signature");
            require(empty(method.tryCatchBlocks) && empty(method.exceptions) && empty(method.visibleAnnotations)
                && empty(method.invisibleAnnotations) && empty(method.visibleTypeAnnotations) && empty(method.invisibleTypeAnnotations)
                && empty(method.visibleLocalVariableAnnotations) && empty(method.invisibleLocalVariableAnnotations)
                && method.visibleParameterAnnotations == null && method.invisibleParameterAnnotations == null
                && method.annotationDefault == null && empty(method.attrs) && empty(method.parameters),
                "No exception, annotation or additional method contracts");
            for (var instruction : method.instructions) require(empty(instruction.visibleTypeAnnotations)
                && empty(instruction.invisibleTypeAnnotations), "No instruction annotation contracts");
        }
        innerMetadata(block, api);
        var clinit = method(block, "<clinit>", "()V", ACC_STATIC);
        var initCode = code(clinit);
        require(!initCode.isEmpty() && initCode.get(0) instanceof LdcInsnNode value && "active".equals(value.cst),
            "Only the exact active property literal");
        exact(clinit, ldc("active"), m(INVOKESTATIC, api.booleanProperty, api.propertyCreate, "(Ljava/lang/String;)L" + api.booleanProperty + ";"),
            f(PUTSTATIC, block.name, active.name, active.desc), op(RETURN));
        exact(method(block, "<init>", "(L" + api.properties + ";)V", ACC_PUBLIC),
            v(ALOAD, 0), v(ALOAD, 1), m(INVOKESPECIAL, api.block, "<init>", "(L" + api.properties + ";)V"),
            v(ALOAD, 0), v(ALOAD, 0), f(GETFIELD, block.name, api.definition, "L" + api.container + ";"),
            m(INVOKEVIRTUAL, api.container, api.any, "()L" + api.holder + ";"), type(CHECKCAST, api.state),
            f(GETSTATIC, block.name, active.name, active.desc), f(GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;"),
            m(INVOKEVIRTUAL, api.state, api.set, "(L" + api.property + ";Ljava/lang/Comparable;)Ljava/lang/Object;"),
            type(CHECKCAST, api.state), m(INVOKEVIRTUAL, block.name, api.register, "(L" + api.state + ";)V"), op(RETURN));
        exact(method(block, api.create, builderDescriptor, ACC_PROTECTED),
            v(ALOAD, 1), op(ICONST_1), type(ANEWARRAY, api.property), op(DUP), op(ICONST_0),
            f(GETSTATIC, block.name, active.name, active.desc), op(AASTORE),
            m(INVOKEVIRTUAL, api.builder, api.add, "([L" + api.property + ";)L" + api.builder + ";"), op(POP), op(RETURN));
        interaction(block, active, api);
    }

    private static void interaction(ClassNode block, FieldNode active, Names api) {
        String descriptor = "(L" + api.state + ";L" + api.world + ";L" + api.position + ";L" + api.player
            + ";L" + api.hand + ";L" + api.hit + ";)L" + api.result + ";";
        var use = method(block, api.use, descriptor, ACC_PUBLIC);
        var instructions = code(use);
        require(instructions.size() == 16, "Only the native logical-side guard and one server-local write");
        String[] expected = {
            v(ALOAD, 2), f(GETFIELD, api.world, api.clientSide, "Z"), null,
            f(GETSTATIC, api.result, "SUCCESS", "L" + api.result + ";"), op(ARETURN),
            v(ALOAD, 2), v(ALOAD, 3), v(ALOAD, 1), f(GETSTATIC, block.name, active.name, active.desc),
            m(INVOKEVIRTUAL, api.state, api.cycle, "(L" + api.property + ";)Ljava/lang/Object;"), type(CHECKCAST, api.state),
            op(ICONST_3), m(INVOKEVIRTUAL, api.world, api.setBlock, "(L" + api.position + ";L" + api.state + ";I)Z"),
            op(POP), f(GETSTATIC, api.result, "CONSUME", "L" + api.result + ";"), op(ARETURN)
        };
        for (int index = 0; index < expected.length; index++) if (expected[index] != null) same(instructions.get(index), expected[index]);
        require(instructions.get(2) instanceof JumpInsnNode branch && branch.getOpcode() == IFEQ,
            "False isClientSide must select the only server branch");
        var branch = (JumpInsnNode)instructions.get(2);
        require(branch.label != null && use.instructions.indexOf(branch.label) >= 0,
            "Branch target must belong to the same method");
        AbstractInsnNode target = branch.label;
        while (target != null && target.getOpcode() < 0) target = target.getNext();
        require(target == instructions.get(5), "Client returns before any world write; server starts at callback world");
    }

    private static void innerMetadata(ClassNode block, Names api) {
        if (block.innerClasses == null) return;
        var seen = new HashSet<String>();
        for (var inner : block.innerClasses) {
            require(inner != null && seen.add(inner.name) && Set.of(api.properties, api.builder).contains(inner.name),
                "Only native properties and state-builder inner metadata");
            int split = inner.name.lastIndexOf('$');
            require(inner.name.substring(0, split).equals(inner.outerName)
                && inner.name.substring(split + 1).equals(inner.innerName)
                && inner.access == (ACC_PUBLIC | ACC_STATIC), "Exact native inner metadata");
        }
    }

    private static void require(boolean accepted, String reason) {
        if (!accepted) throw new TransformationException("BOOLEAN_TOGGLE_BLOCK_UNSUPPORTED: " + reason);
    }

    private static final class Names {
        final boolean target;
        final String block, properties, state, container, holder, builder, booleanProperty, property,
            definition, create, register, any, add, set, propertyCreate, world, position, player, hand, hit,
            result, use, clientSide, cycle, setBlock;
        Names(boolean target) {
            this.target = target; block = target ? TARGET_BLOCK : OLD_BLOCK;
            properties = target ? "net/minecraft/world/level/block/state/BlockBehaviour$Properties" : "net/minecraft/block/AbstractBlock$Properties";
            state = target ? "net/minecraft/world/level/block/state/BlockState" : "net/minecraft/block/BlockState";
            container = target ? "net/minecraft/world/level/block/state/StateDefinition" : "net/minecraft/state/StateContainer";
            holder = target ? "net/minecraft/world/level/block/state/StateHolder" : "net/minecraft/state/StateHolder";
            builder = container + "$Builder";
            booleanProperty = target ? "net/minecraft/world/level/block/state/properties/BooleanProperty" : "net/minecraft/state/BooleanProperty";
            property = target ? "net/minecraft/world/level/block/state/properties/Property" : "net/minecraft/state/Property";
            definition = target ? "f_49792_" : "field_176227_L";
            create = target ? "m_7926_" : "func_206840_a";
            register = target ? "m_49959_" : "func_180632_j";
            any = target ? "m_61090_" : "func_177621_b";
            add = target ? "m_61104_" : "func_206894_a";
            set = target ? "m_61124_" : "func_206870_a";
            propertyCreate = target ? "m_61465_" : "func_177716_a";
            world = target ? "net/minecraft/world/level/Level" : "net/minecraft/world/World";
            position = target ? "net/minecraft/core/BlockPos" : "net/minecraft/util/math/BlockPos";
            player = target ? "net/minecraft/world/entity/player/Player" : "net/minecraft/entity/player/PlayerEntity";
            hand = target ? "net/minecraft/world/InteractionHand" : "net/minecraft/util/Hand";
            hit = target ? "net/minecraft/world/phys/BlockHitResult" : "net/minecraft/util/math/BlockRayTraceResult";
            result = target ? "net/minecraft/world/InteractionResult" : "net/minecraft/util/ActionResultType";
            use = target ? "m_6227_" : "func_225533_a_";
            clientSide = target ? "f_46443_" : "field_72995_K";
            cycle = target ? "m_61122_" : "func_235896_a_";
            setBlock = target ? "m_7731_" : "func_180501_a";
        }
    }
}
