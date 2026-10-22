package dev.legacybridge.converter.conversion.logic;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import dev.legacybridge.converter.transformation.TransformationException;
import dev.legacybridge.runtime.compat.AdapterCatalog;
import dev.legacybridge.runtime.compat.logic.PlainBlockArtifactAdapter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolved, declaration-checked form of the implemented STONE instruction recipe.
 * Material has no fabricated target class/member mapping. Only the proven adjacent
 * field/factory pair is consumed; any surviving Material reference is unsupported.
 * The enclosing PlainBlockContract (or separate StaticShapeBlockContract context)
 * must have proved the complete owned program. Identical target operations do not
 * imply identical geometry/default semantics; those contexts use distinct adapters.
 */
public record PlainBlockApiRecipe(String sourceMaterial, Op sourceStone, Op sourceFactory,
                                  List<Op> loweringOperations, String adapterId) {
    static final String BLOCK = "net/minecraft/world/level/block/Block";
    static final String BLOCK_ITEM = "net/minecraft/world/item/BlockItem";
    static final String BEHAVIOUR = "net/minecraft/world/level/block/state/BlockBehaviour";
    static final String PROPERTIES = BEHAVIOUR + "$Properties";
    static final String MATERIAL = "net/minecraft/world/level/material/Material";
    static final String COLOR = "net/minecraft/world/level/material/MapColor";
    static final String INSTRUMENT = "net/minecraft/world/level/block/state/properties/NoteBlockInstrument";

    public PlainBlockApiRecipe(String sourceMaterial, Op sourceStone, Op sourceFactory, List<Op> loweringOperations) {
        this(sourceMaterial,sourceStone,sourceFactory,loweringOperations,AdapterCatalog.PLAIN_BLOCK_PROPERTIES);
    }

    public PlainBlockApiRecipe {
        Objects.requireNonNull(sourceMaterial);
        Objects.requireNonNull(sourceStone);
        Objects.requireNonNull(sourceFactory);
        loweringOperations = List.copyOf(loweringOperations);
        Objects.requireNonNull(adapterId);
        if (!adapterId.equals(AdapterCatalog.PLAIN_BLOCK_PROPERTIES) && !adapterId.equals(AdapterCatalog.STATIC_SHAPE_PROPERTIES))
            throw failure("Unreviewed STONE context adapter: " + adapterId);
    }

    /** GETSTATIC fields and non-interface INVOKESTATIC/INVOKEVIRTUAL calls only. */
    public record Op(int opcode, String owner, String name, String descriptor) {
        public Op {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(name);
            Objects.requireNonNull(descriptor);
            if (opcode != Opcodes.GETSTATIC && opcode != Opcodes.INVOKESTATIC && opcode != Opcodes.INVOKEVIRTUAL)
                throw new IllegalArgumentException("Unsupported plain-block recipe opcode");
        }
        public boolean matches(AbstractInsnNode instruction) {
            if (instruction == null || instruction.getOpcode() != opcode) return false;
            if (instruction instanceof FieldInsnNode field)
                return opcode == Opcodes.GETSTATIC && owner.equals(field.owner) && name.equals(field.name) && descriptor.equals(field.desc);
            return instruction instanceof MethodInsnNode call && !call.itf
                    && owner.equals(call.owner) && name.equals(call.name) && descriptor.equals(call.desc);
        }
        public Map<String,Object> toTree() {
            return Map.of("opcode",opcode,"owner",owner,"name",name,"descriptor",descriptor);
        }
    }

    static PlainBlockApiRecipe resolve(LogicApiNamespace source, LogicApiNamespace target,
                                       ApiIndex sourceApi, ApiIndex targetApi) {
        return resolve(source,target,sourceApi,targetApi,false);
    }

    static PlainBlockApiRecipe resolve(LogicApiNamespace source, LogicApiNamespace target,
                                       ApiIndex sourceApi, ApiIndex targetApi, boolean staticShape) {
        String adapterId=staticShape?AdapterCatalog.STATIC_SHAPE_PROPERTIES:AdapterCatalog.PLAIN_BLOCK_PROPERTIES;
        AdapterCatalog.require(adapterId);
        String material = source.productionClass(MATERIAL);
        String materialDescriptor = "L" + MATERIAL + ";";
        Op stone = new Op(Opcodes.GETSTATIC, material,
                source.productionField(MATERIAL, "STONE", materialDescriptor), source.productionDescriptor(materialDescriptor));
        String factoryDescriptor = "(L" + MATERIAL + ";)L" + PROPERTIES + ";";
        Op factory = new Op(Opcodes.INVOKESTATIC, source.productionClass(PROPERTIES),
                source.productionMethod(PROPERTIES, "of", factoryDescriptor), source.productionDescriptor(factoryDescriptor));
        checkDeclaration(sourceApi, stone);
        checkDeclaration(sourceApi, factory);
        if ((sourceApi.getClass(material).access() & Opcodes.ACC_FINAL) == 0)
            throw failure("Source Material is no longer the reviewed final class");
        var operations = new ArrayList<Op>();
        var canonicalCalls=staticShape?dev.legacybridge.runtime.compat.logic.StaticShapeBlockArtifactAdapter.stonePropertiesCalls()
                :PlainBlockArtifactAdapter.stonePropertiesCalls();
        for (var call : canonicalCalls) {
            int opcode = switch (call.get("kind")) {
                case "STATIC" -> Opcodes.INVOKESTATIC;
                case "VIRTUAL" -> Opcodes.INVOKEVIRTUAL;
                case "FIELD" -> Opcodes.GETSTATIC;
                default -> throw failure("Unimplemented canonical recipe operation");
            };
            String owner = call.get("owner"), name = call.get("name"), descriptor = call.get("descriptor");
            String resolvedName = opcode == Opcodes.GETSTATIC ? target.productionField(owner, name, descriptor)
                    : target.productionMethod(owner, name, descriptor);
            Op operation = new Op(opcode, target.productionClass(owner), resolvedName, target.productionDescriptor(descriptor));
            checkDeclaration(targetApi, operation);
            operations.add(operation);
        }
        if (operations.size() != 5) throw failure("STONE recipe must remain the reviewed five-operation contract");
        return new PlainBlockApiRecipe(material, stone, factory, operations, adapterId);
    }

    private static void checkDeclaration(ApiIndex index, Op operation) {
        var type = index.getClass(operation.owner());
        if (type == null || (type.access() & Opcodes.ACC_PUBLIC) == 0 || (type.access() & Opcodes.ACC_INTERFACE) != 0)
            throw failure("Absent/nonpublic/interface operation owner: " + operation.owner());
        var members = operation.opcode() == Opcodes.GETSTATIC ? type.fields() : type.methods();
        var matches = members.stream().filter(m -> m.name().equals(operation.name())
                && m.descriptor().equals(operation.descriptor())).toList();
        if (matches.size() != 1) throw failure("Exact operation declaration missing/ambiguous: " + operation);
        int access = matches.get(0).access();
        int expectedStatic = operation.opcode() == Opcodes.INVOKEVIRTUAL ? 0 : Opcodes.ACC_STATIC;
        if ((access & Opcodes.ACC_PUBLIC) == 0 || (access & Opcodes.ACC_STATIC) != expectedStatic
                || (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0
                || operation.opcode() == Opcodes.GETSTATIC && (access & Opcodes.ACC_FINAL) == 0)
            throw failure("Operation access/static/final contract changed: " + operation);
        Type descriptor = Type.getType(operation.descriptor());
        if (descriptor.getSort() == Type.METHOD) {
            checkType(index, descriptor.getReturnType());
            for (Type argument : descriptor.getArgumentTypes()) checkType(index, argument);
        } else checkType(index, descriptor);
    }

    private static void checkType(ApiIndex index, Type type) {
        if (type.getSort() == Type.ARRAY) { checkType(index, type.getElementType()); return; }
        if (type.getSort() != Type.OBJECT) return;
        var declaration = index.getClass(type.getInternalName());
        if (declaration == null || (declaration.access() & Opcodes.ACC_PUBLIC) == 0)
            throw failure("Absent/nonpublic operation descriptor type: " + type.getInternalName());
    }

    /** Only this original instruction pair can bypass ordinary symbol remapping. */
    public boolean consumesInstruction(AbstractInsnNode instruction) {
        return sourceStone.matches(instruction) && sourceFactory.matches(nextCode(instruction))
                || sourceFactory.matches(instruction) && sourceStone.matches(previousCode(instruction));
    }

    boolean consumesReference(LegacyReferenceScanner.ExternalReference reference, Map<String, ClassNode> own) {
        if (reference.instructionIndex() < 0) return false;
        ClassNode owner = own.get(reference.sourceClass());
        if (owner == null) return false;
        MethodNode method = owner.methods.stream().filter(m -> (m.name + m.desc).equals(reference.enclosingMethod())).findFirst().orElse(null);
        if (method == null || reference.instructionIndex() >= method.instructions.size()) return false;
        AbstractInsnNode instruction = method.instructions.get(reference.instructionIndex());
        if (!consumesInstruction(instruction)) return false;
        Op operation = sourceStone.matches(instruction) ? sourceStone : sourceFactory;
        if (reference.kind() == LegacyReferenceScanner.ReferenceKind.DESCRIPTOR)
            return reference.descriptor().equals(operation.descriptor())
                    && (reference.owner().equals(sourceMaterial) || reference.owner().equals(sourceFactory.owner()));
        return reference.kind() == (operation.opcode() == Opcodes.GETSTATIC
                ? LegacyReferenceScanner.ReferenceKind.FIELD : LegacyReferenceScanner.ReferenceKind.METHOD)
                && reference.owner().equals(operation.owner()) && reference.name().equals(operation.name())
                && reference.descriptor().equals(operation.descriptor());
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
        do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }
    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        do { instruction = instruction.getPrevious(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    public Map<String,Object> review() {
        boolean staticShape=adapterId.equals(AdapterCatalog.STATIC_SHAPE_PROPERTIES);
        return Map.of("contract", staticShape?"static-shape.contextual-stone-properties.v1":"plain-block.contextual-stone-properties.v1",
                "adapter", adapterId, "confidence", "VERIFIED",
                "source", List.of(sourceStone.toTree(),sourceFactory.toTree()),
                "target", loweringOperations.stream().map(Op::toTree).toList(),
                "kind", "CONTRACT_REWRITE", "materialClassMapping", "NONE",
                "reason", staticShape?"Closed literal bottom-half outline/collision graph and audited geometry/default/cache context plus exact source/target declarations; not full-cube or general-shape approval"
                        :"Closed plain-block graph plus independent exact source/target declarations; no generic mapping promotion");
    }

    private static TransformationException failure(String reason) {
        return new TransformationException("PLAIN_BLOCK_API_RECIPE: " + reason);
    }
}
