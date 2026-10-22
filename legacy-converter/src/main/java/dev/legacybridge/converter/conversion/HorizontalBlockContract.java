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
 * Legacy API: native Block, StateContainer.Builder, HORIZONTAL_FACING and placement context.
 * Target API: native Block, StateDefinition.Builder and the corresponding exact state/context APIs.
 * Transformation: preserve the two owned classes and both real overrides; the caller proves
 * exact declarations/overrides and lowers only the already bounded STONE-properties expression.
 * Behavior differences: target native placement emits its normal game event; no event is suppressed.
 * Known limitations: one horizontal property, NORTH default and opposite-context placement;
 * optionally both exact native direction rotate/mirror overrides, never arbitrary transforms.
 * No extra state, shapes, effects, handlers, ticks or runtime claim.
 * Pinned production spellings are structural guards, not a substitute for real mapping/API proof.
 */
public final class HorizontalBlockContract {
    private static final String OLD_BLOCK = "net/minecraft/block/Block";
    private static final String TARGET_BLOCK = "net/minecraft/world/level/block/Block";

    private HorizontalBlockContract() { }

    public record Proof(String entry, String block, String propertyField, PlainBlockContract.Proof plainProof,
                        boolean orientationTransforms) {
        public Proof(String entry, String block, String propertyField, PlainBlockContract.Proof plainProof) {
            this(entry, block, propertyField, plainProof, false);
        }
        public Map<String,Object> toTree() {
            return Map.of("schemaVersion", 1, "contract", "horizontal-block-with-item-v1",
                "entry", entry, "block", block, "propertyField", propertyField,
                "plainContent", plainProof.toTree(),
                "state", Map.of("property", "facing", "values", List.of("north", "east", "south", "west"),
                    "default", "north", "placement", "OPPOSITE_CONTEXT_HORIZONTAL",
                    "stateBuilder", "SINGLE_STATIC_NATIVE_PROPERTY", "additionalEffects", "NONE",
                    "orientationMode", orientationTransforms ? "NATIVE_DIRECTION_ROTATE_AND_MIRROR" : "INHERITED_NO_OP"),
                "orientationTransforms", orientationTransforms,
                "runtimeValidation", "NOT_RUN", "classesExecuted", false);
        }
    }

    public static Proof inspect(Map<String,ClassNode> nodes) {
        try {
            return inspectClosed(nodes);
        } catch (TransformationException refusal) {
            if (refusal.getMessage().startsWith("HORIZONTAL_BLOCK_UNSUPPORTED:")) throw refusal;
            throw new TransformationException("HORIZONTAL_BLOCK_UNSUPPORTED: " + refusal.getMessage(), refusal);
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
        boolean target = TARGET_BLOCK.equals(block.superName);
        var api = new Names(target);
        checkSubclass(block, api);
        var projected = projectedEntry(entry, block, api);
        var plainProof = PlainBlockContract.inspect(Map.of(projected.name, projected));
        return new Proof(entry.name, block.name, block.fields.get(0).name, plainProof, block.methods.size() == 6);
    }

    /**
     * Checked, deep-copied admission view for reusing the original registration/property proof.
     * Only the sole owned NEW/constructor owner becomes native Block. This view must NEVER
     * be serialized as converted bytecode: actual conversion preserves the subclass and overrides.
     */
    public static Map<String,ClassNode> plainProjection(Map<String,ClassNode> nodes) {
        var proof = inspect(nodes);
        var block = nodes.get(proof.block());
        var projection = projectedEntry(nodes.get(proof.entry()), block, new Names(TARGET_BLOCK.equals(block.superName)));
        return Map.of(projection.name, projection);
    }

    public static ContentPlan validate(Map<String,ClassNode> nodes, String modId, String version) {
        // PlainBlockContract independently enforces target spelling, metadata identity and version bounds.
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
            && block.methods != null && (block.methods.size() == 4 || block.methods.size() == 6),
            "Final Java-8 subclass with one field and exactly four methods or the six-method rotate/mirror branch");
        require(block.signature == null && block.outerClass == null && block.outerMethod == null
            && block.outerMethodDesc == null && block.nestHostClass == null && empty(block.nestMembers)
            && empty(block.permittedSubclasses) && empty(block.recordComponents) && block.module == null
            && empty(block.attrs) && empty(block.visibleAnnotations) && empty(block.invisibleAnnotations)
            && empty(block.visibleTypeAnnotations) && empty(block.invisibleTypeAnnotations), "No additional subclass metadata contracts");
        var facing = block.fields.get(0);
        require(facing != null && facing.name != null && !facing.name.isBlank()
            && facing.access == (ACC_PUBLIC | ACC_STATIC | ACC_FINAL)
            && ("L" + api.directionProperty + ";").equals(facing.desc)
            && facing.signature == null && facing.value == null && empty(facing.visibleAnnotations)
            && empty(facing.invisibleAnnotations) && empty(facing.visibleTypeAnnotations)
            && empty(facing.invisibleTypeAnnotations) && empty(facing.attrs), "Only final native direction-property identity");
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
        exact(method(block, "<clinit>", "()V", ACC_STATIC),
            f(GETSTATIC, api.blockProperties, api.horizontal, "L" + api.directionProperty + ";"),
            f(PUTSTATIC, block.name, facing.name, facing.desc), op(RETURN));
        exact(method(block, "<init>", "(L" + api.properties + ";)V", ACC_PUBLIC),
            v(ALOAD, 0), v(ALOAD, 1), m(INVOKESPECIAL, api.block, "<init>", "(L" + api.properties + ";)V"),
            v(ALOAD, 0), v(ALOAD, 0), f(GETFIELD, block.name, api.definition, "L" + api.container + ";"),
            m(INVOKEVIRTUAL, api.container, api.any, "()L" + api.holder + ";"), type(CHECKCAST, api.state),
            f(GETSTATIC, block.name, facing.name, facing.desc), f(GETSTATIC, api.direction, "NORTH", "L" + api.direction + ";"),
            m(INVOKEVIRTUAL, api.state, api.set, "(L" + api.property + ";Ljava/lang/Comparable;)Ljava/lang/Object;"),
            type(CHECKCAST, api.state), m(INVOKEVIRTUAL, block.name, api.register, "(L" + api.state + ";)V"), op(RETURN));
        exact(method(block, api.create, builderDescriptor, ACC_PROTECTED),
            v(ALOAD, 1), op(ICONST_1), type(ANEWARRAY, api.property), op(DUP), op(ICONST_0),
            f(GETSTATIC, block.name, facing.name, facing.desc), op(AASTORE),
            m(INVOKEVIRTUAL, api.builder, api.add, "([L" + api.property + ";)L" + api.builder + ";"), op(POP), op(RETURN));
        exact(method(block, api.placement, "(L" + api.context + ";)L" + api.state + ";", ACC_PUBLIC),
            v(ALOAD, 0), m(INVOKEVIRTUAL, block.name, api.defaultState, "()L" + api.state + ";"),
            f(GETSTATIC, block.name, facing.name, facing.desc), v(ALOAD, 1),
            m(INVOKEVIRTUAL, api.context, api.horizontalDirection, "()L" + api.direction + ";"),
            m(INVOKEVIRTUAL, api.direction, api.opposite, "()L" + api.direction + ";"),
            m(INVOKEVIRTUAL, api.state, api.set, "(L" + api.property + ";Ljava/lang/Comparable;)Ljava/lang/Object;"),
            type(CHECKCAST, api.state), op(ARETURN));
        if (block.methods.size() == 6) {
            orientationMethod(block, facing, api, api.rotate, api.rotation, api.rotateDirection);
            orientationMethod(block, facing, api, api.mirror, api.mirrorType, api.mirrorDirection);
        }
    }

    private static void orientationMethod(ClassNode block, FieldNode facing, Names api,
                                          String override, String transformType, String directionTransform) {
        exact(method(block, override, "(L" + api.state + ";L" + transformType + ";)L" + api.state + ";", ACC_PUBLIC),
            v(ALOAD, 1), f(GETSTATIC, block.name, facing.name, facing.desc), v(ALOAD, 2),
            v(ALOAD, 1), f(GETSTATIC, block.name, facing.name, facing.desc),
            m(INVOKEVIRTUAL, api.state, api.get, "(L" + api.property + ";)Ljava/lang/Comparable;"),
            type(CHECKCAST, api.direction),
            m(INVOKEVIRTUAL, transformType, directionTransform, "(L" + api.direction + ";)L" + api.direction + ";"),
            m(INVOKEVIRTUAL, api.state, api.set, "(L" + api.property + ";Ljava/lang/Comparable;)Ljava/lang/Object;"),
            type(CHECKCAST, api.state), op(ARETURN));
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
        if (!accepted) throw new TransformationException("HORIZONTAL_BLOCK_UNSUPPORTED: " + reason);
    }

    private static final class Names {
        final boolean target;
        final String block, properties, state, container, holder, builder, directionProperty, property,
            blockProperties, direction, context, definition, create, register, defaultState, placement,
            any, add, set, horizontalDirection, opposite, horizontal,
            get, rotation, mirrorType, rotate, mirror, rotateDirection, mirrorDirection;
        Names(boolean target) {
            this.target = target;
            block = target ? TARGET_BLOCK : OLD_BLOCK;
            properties = target ? "net/minecraft/world/level/block/state/BlockBehaviour$Properties" : "net/minecraft/block/AbstractBlock$Properties";
            state = target ? "net/minecraft/world/level/block/state/BlockState" : "net/minecraft/block/BlockState";
            container = target ? "net/minecraft/world/level/block/state/StateDefinition" : "net/minecraft/state/StateContainer";
            holder = target ? "net/minecraft/world/level/block/state/StateHolder" : "net/minecraft/state/StateHolder";
            builder = container + "$Builder";
            directionProperty = target ? "net/minecraft/world/level/block/state/properties/DirectionProperty" : "net/minecraft/state/DirectionProperty";
            property = target ? "net/minecraft/world/level/block/state/properties/Property" : "net/minecraft/state/Property";
            blockProperties = target ? "net/minecraft/world/level/block/state/properties/BlockStateProperties" : "net/minecraft/state/properties/BlockStateProperties";
            direction = target ? "net/minecraft/core/Direction" : "net/minecraft/util/Direction";
            context = target ? "net/minecraft/world/item/context/BlockPlaceContext" : "net/minecraft/item/BlockItemUseContext";
            definition = target ? "f_49792_" : "field_176227_L";
            create = target ? "m_7926_" : "func_206840_a";
            register = target ? "m_49959_" : "func_180632_j";
            defaultState = target ? "m_49966_" : "func_176223_P";
            placement = target ? "m_5573_" : "func_196258_a";
            any = target ? "m_61090_" : "func_177621_b";
            add = target ? "m_61104_" : "func_206894_a";
            set = target ? "m_61124_" : "func_206870_a";
            horizontalDirection = target ? "m_8125_" : "func_195992_f";
            opposite = target ? "m_122424_" : "func_176734_d";
            horizontal = target ? "f_61374_" : "field_208157_J";
            get = target ? "m_61143_" : "func_177229_b";
            rotation = target ? "net/minecraft/world/level/block/Rotation" : "net/minecraft/util/Rotation";
            mirrorType = target ? "net/minecraft/world/level/block/Mirror" : "net/minecraft/util/Mirror";
            rotate = target ? "m_6843_" : "func_185499_a";
            mirror = target ? "m_6943_" : "func_185471_a";
            rotateDirection = target ? "m_55954_" : "func_185831_a";
            mirrorDirection = target ? "m_54848_" : "func_185803_b";
        }
    }
}
