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
 * Legacy API: Block.box and AbstractBlock outline/collision overrides returning a static VoxelShape.
 * Target API: Block.box and BlockBehaviour outline/collision overrides with target context types.
 * Transformation: retain the owned subclass, its final field, native factory and both overrides;
 * the caller independently proves API access/dispatch and the exact STONE-properties expansion.
 * Behavior differences: target box rejects reversed bounds and solidity depends on cached shape,
 * unlike legacy Material. Only the audited positive bottom half-cuboid is admitted here; this
 * proof never generalizes the previous full-cube defaults to arbitrary geometry or contexts.
 * Known limitations: one constant (0,0,0)-(16,8,16) box, no state/context/world access, dynamic
 * shape, offset, other callbacks, shapes, properties or additional effects. Native QA is NOT_RUN.
 * Production names are closed structural guards, never a substitute for real mapping/API proof.
 */
public final class StaticShapeBlockContract {
    private static final String OLD_BLOCK = "net/minecraft/block/Block";
    private static final String TARGET_BLOCK = "net/minecraft/world/level/block/Block";

    private StaticShapeBlockContract() { }

    public record Proof(String entry, String block, String shapeField, PlainBlockContract.Proof plainProof) {
        public Map<String,Object> toTree() {
            return Map.of("schemaVersion", 1, "contract", "static-bottom-half-shape-with-item-v1",
                "entry", entry, "block", block, "shapeField", shapeField,
                "plainContent", plainProof.toTree(),
                "shape", Map.of("factory", "Block.box", "minimum", List.of(0.0D, 0.0D, 0.0D),
                    "maximum", List.of(16.0D, 8.0D, 16.0D), "units", "SIXTEENTHS",
                    "outlineAndCollision", "SAME_STATIC_FINAL_FIELD", "contextUse", "NONE",
                    "dynamicShape", false, "offset", "NONE"),
                "runtimeValidation", "NOT_RUN", "classesExecuted", false);
        }
    }

    public static Proof inspect(Map<String,ClassNode> nodes) {
        try {
            return inspectClosed(nodes);
        } catch (TransformationException refusal) {
            if (refusal.getMessage().startsWith("STATIC_SHAPE_BLOCK_UNSUPPORTED:")) throw refusal;
            throw new TransformationException("STATIC_SHAPE_BLOCK_UNSUPPORTED: " + refusal.getMessage(), refusal);
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
     * Checked deep-copied admission view: only the sole owned NEW/constructor owner becomes
     * native Block to reuse registration/property checks. Never serialize this projection;
     * converted bytes must retain the subclass, static shape and both actual override bodies.
     */
    public static Map<String,ClassNode> plainProjection(Map<String,ClassNode> nodes) {
        var proof = inspect(nodes);
        var block = nodes.get(proof.block());
        var projection = projectedEntry(nodes.get(proof.entry()), block, new Names(TARGET_BLOCK.equals(block.superName)));
        return Map.of(projection.name, projection);
    }

    /** Content identity only; the caller must also validate the distinct half-cuboid resources. */
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
            && block.methods != null && block.methods.size() == 4, "Final Java-8 subclass with one field and four methods");
        require(block.signature == null && block.outerClass == null && block.outerMethod == null
            && block.outerMethodDesc == null && block.nestHostClass == null && empty(block.nestMembers)
            && empty(block.permittedSubclasses) && empty(block.recordComponents) && block.module == null
            && empty(block.attrs) && empty(block.visibleAnnotations) && empty(block.invisibleAnnotations)
            && empty(block.visibleTypeAnnotations) && empty(block.invisibleTypeAnnotations), "No additional subclass metadata contracts");
        var shape = block.fields.get(0);
        require(shape != null && shape.name != null && !shape.name.isBlank()
            && shape.access == (ACC_PRIVATE | ACC_STATIC | ACC_FINAL) && ("L" + api.shape + ";").equals(shape.desc)
            && shape.signature == null && shape.value == null && empty(shape.visibleAnnotations)
            && empty(shape.invisibleAnnotations) && empty(shape.visibleTypeAnnotations)
            && empty(shape.invisibleTypeAnnotations) && empty(shape.attrs), "Only one private static final native shape");
        var signatures = new HashSet<String>();
        for (var method : block.methods) {
            OperationControl.checkpoint();
            require(method != null && signatures.add(method.name + method.desc)
                && method.instructions != null && method.instructions.size() <= 128, "Bounded unique subclass methods");
            require(method.signature == null && empty(method.tryCatchBlocks) && empty(method.exceptions)
                && empty(method.visibleAnnotations) && empty(method.invisibleAnnotations)
                && empty(method.visibleTypeAnnotations) && empty(method.invisibleTypeAnnotations)
                && empty(method.visibleLocalVariableAnnotations) && empty(method.invisibleLocalVariableAnnotations)
                && method.visibleParameterAnnotations == null && method.invisibleParameterAnnotations == null
                && method.annotationDefault == null && empty(method.attrs) && empty(method.parameters),
                "No handlers, annotations, generic or additional method contracts");
            for (var instruction : method.instructions) require(empty(instruction.visibleTypeAnnotations)
                && empty(instruction.invisibleTypeAnnotations), "No instruction annotation contracts");
        }
        innerMetadata(block, api);
        exact(method(block, "<init>", "(L" + api.properties + ";)V", ACC_PUBLIC),
            v(ALOAD, 0), v(ALOAD, 1), m(INVOKESPECIAL, api.block, "<init>", "(L" + api.properties + ";)V"), op(RETURN));
        var init = code(method(block, "<clinit>", "()V", ACC_STATIC));
        require(init.size() == 9, "Only six literal coordinates and one native static shape construction");
        double[] bounds = {0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D};
        for (int index = 0; index < bounds.length; index++) exactDouble(init.get(index), bounds[index]);
        same(init.get(6), m(INVOKESTATIC, api.block, api.box, "(DDDDDD)L" + api.shape + ";"));
        same(init.get(7), f(PUTSTATIC, block.name, shape.name, shape.desc)); same(init.get(8), op(RETURN));
        String descriptor = "(L" + api.state + ";L" + api.reader + ";L" + api.position + ";L" + api.context + ";)L" + api.shape + ";";
        for (String name : List.of(api.outline, api.collision)) {
            exact(method(block, name, descriptor, ACC_PUBLIC), f(GETSTATIC, block.name, shape.name, shape.desc), op(ARETURN));
        }
    }

    private static void exactDouble(AbstractInsnNode node, double expected) {
        double value;
        if (node.getOpcode() == DCONST_0) value = 0.0D;
        else if (node.getOpcode() == DCONST_1) value = 1.0D;
        else if (node instanceof LdcInsnNode ldc && ldc.cst instanceof Double number) value = number;
        else throw new TransformationException("STATIC_SHAPE_BLOCK_UNSUPPORTED: Exact native double literal required");
        require(Double.doubleToLongBits(value) == Double.doubleToLongBits(expected),
            "Only positive-zero ordered bottom-half literal coordinates; no generic geometry");
    }

    private static void innerMetadata(ClassNode block, Names api) {
        if (block.innerClasses == null) return;
        var seen = new HashSet<String>();
        for (var inner : block.innerClasses) {
            require(inner != null && seen.add(inner.name) && api.properties.equals(inner.name), "Only native Properties inner metadata");
            int split = inner.name.lastIndexOf('$');
            require(inner.name.substring(0, split).equals(inner.outerName)
                && "Properties".equals(inner.innerName) && inner.access == (ACC_PUBLIC | ACC_STATIC), "Exact Properties inner metadata");
        }
    }

    private static void require(boolean accepted, String reason) {
        if (!accepted) throw new TransformationException("STATIC_SHAPE_BLOCK_UNSUPPORTED: " + reason);
    }

    private static final class Names {
        final boolean target;
        final String block, properties, shape, state, reader, position, context, box, outline, collision;
        Names(boolean target) {
            this.target = target; block = target ? TARGET_BLOCK : OLD_BLOCK;
            properties = target ? "net/minecraft/world/level/block/state/BlockBehaviour$Properties" : "net/minecraft/block/AbstractBlock$Properties";
            shape = target ? "net/minecraft/world/phys/shapes/VoxelShape" : "net/minecraft/util/math/shapes/VoxelShape";
            state = target ? "net/minecraft/world/level/block/state/BlockState" : "net/minecraft/block/BlockState";
            reader = target ? "net/minecraft/world/level/BlockGetter" : "net/minecraft/world/IBlockReader";
            position = target ? "net/minecraft/core/BlockPos" : "net/minecraft/util/math/BlockPos";
            context = target ? "net/minecraft/world/phys/shapes/CollisionContext" : "net/minecraft/util/math/shapes/ISelectionContext";
            box = target ? "m_49796_" : "func_208617_a";
            outline = target ? "m_5940_" : "func_220053_a";
            collision = target ? "m_5939_" : "func_220071_b";
        }
    }
}
