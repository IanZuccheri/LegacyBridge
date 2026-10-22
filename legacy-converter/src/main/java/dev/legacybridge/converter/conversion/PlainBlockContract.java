package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;
import static dev.legacybridge.converter.conversion.ClientConfigContract.*;

/**
 * Closed, non-executing proof of one native STONE Block and its deferred BlockItem.
 * Legacy API: Forge 36 DeferredRegister, Material.STONE and AbstractBlock.Properties.
 * Target API: Forge 47 DeferredRegister and native BlockBehaviour.Properties with explicit
 * STONE map colour and BASEDRUM note instrument. The caller must separately prove the
 * mining-tag resource migration and the real source/target member mappings.
 * Transformation: original registration/factory bodies remain owned; only the proven
 * material-properties expression may be lowered by the implemented contextual recipe.
 * Behavior differences: removed Material defaults are explicit, and pickaxe effectiveness
 * requires a target tag. This structural proof is not native behavioral qualification.
 * Known limitations: no Block subclass, state, interaction, ticks, extra properties,
 * creative tab, early RegistryObject.get, or arbitrary registration/lifecycle effects.
 *
 * Production spellings below are pinned structural guards, NOT a mapping database.
 * VerifiedLogicMappings must independently derive and verify all mapping rules.
 */
public final class PlainBlockContract {
    private static final String DEFERRED = "net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRIES = "net/minecraftforge/registries/ForgeRegistries";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final String OLD_BLOCK = "net/minecraft/block/Block";
    private static final String TARGET_BLOCK = "net/minecraft/world/level/block/Block";
    private static final String TARGET_PROPERTIES = "net/minecraft/world/level/block/state/BlockBehaviour$Properties";
    private static final String MAP_COLOR = "net/minecraft/world/level/material/MapColor";
    private static final String INSTRUMENT = "net/minecraft/world/level/block/state/properties/NoteBlockInstrument";

    private PlainBlockContract() { }

    public record Proof(String entry, String modId, String registryName,
                        float hardness, float resistance, int stackSize) {
        public Map<String,Object> toTree() {
            return Map.of("schemaVersion", 1, "contract", "plain-block-with-item-v1", "entry", entry,
                "modId", modId, "registryName", registryName,
                "block", Map.of("material", "STONE", "hardness", hardness, "resistance", resistance,
                    "sound", "STONE", "requiresCorrectTool", false, "noOcclusion", false,
                    "targetMapColor", "STONE", "targetInstrument", "BASEDRUM",
                    "requiredTargetTag", "minecraft:mineable/pickaxe"),
                "blockItem", Map.of("stackSize", stackSize, "blockLookup", "DEFERRED_ITEM_FACTORY_ONLY",
                    "creativeTab", "NONE"),
                "registration", "BLOCKS_THEN_ITEMS_ON_MOD_BUS; ZERO_CAPTURE_STATIC_SUPPLIERS",
                "runtimeValidation", "NOT_RUN", "classesExecuted", false);
        }
    }

    public static Proof inspect(Map<String,ClassNode> nodes) {
        try {
            return inspectClosed(nodes);
        } catch (TransformationException refusal) {
            if (refusal.getMessage().startsWith("PLAIN_BLOCK_UNSUPPORTED:")) throw refusal;
            throw new TransformationException("PLAIN_BLOCK_UNSUPPORTED: " + refusal.getMessage(), refusal);
        }
    }

    private static Proof inspectClosed(Map<String,ClassNode> nodes) {
        require(nodes != null && nodes.size() == 1, "Exactly one owned entry/factory class");
        var pair = nodes.entrySet().iterator().next();
        var entry = pair.getValue();
        require(entry != null && pair.getKey().equals(entry.name)
            && !LegacyReferenceScanner.isProtectedDefinition(entry.name), "Owned class identity");
        require(entry.version == V1_8 && entry.access == (ACC_PUBLIC | ACC_FINAL | ACC_SUPER)
            && "java/lang/Object".equals(entry.superName) && empty(entry.interfaces), "Final direct Java-8 Object entry");
        require(entry.fields != null && entry.fields.size() == 5
            && entry.methods != null && entry.methods.size() == 4, "Exactly five fields and four methods");
        require(entry.signature == null && entry.outerClass == null && entry.outerMethod == null
            && entry.outerMethodDesc == null && entry.nestHostClass == null && empty(entry.nestMembers)
            && empty(entry.permittedSubclasses) && empty(entry.recordComponents) && entry.module == null
            && empty(entry.attrs) && empty(entry.visibleTypeAnnotations) && empty(entry.invisibleTypeAnnotations),
            "No extra class metadata contracts");
        var methodSignatures = new HashSet<String>();
        for (var method : entry.methods) {
            OperationControl.checkpoint();
            require(method != null && methodSignatures.add(method.name + method.desc)
                && method.instructions != null && method.instructions.size() <= 128,
                "Bounded unique methods");
            require(method.signature == null && empty(method.tryCatchBlocks) && empty(method.exceptions)
                && empty(method.visibleAnnotations) && empty(method.invisibleAnnotations)
                && empty(method.visibleTypeAnnotations) && empty(method.invisibleTypeAnnotations)
                && empty(method.visibleLocalVariableAnnotations) && empty(method.invisibleLocalVariableAnnotations)
                && method.visibleParameterAnnotations == null && method.invisibleParameterAnnotations == null
                && method.annotationDefault == null && empty(method.attrs) && empty(method.parameters),
                "No handlers, declared exceptions, annotation hooks or additional method contracts");
            for (var instruction : method.instructions) require(empty(instruction.visibleTypeAnnotations)
                && empty(instruction.invisibleTypeAnnotations), "No instruction annotation contracts");
        }

        var blockMethods = entry.methods.stream()
            .filter(m -> m.desc.equals("()L" + OLD_BLOCK + ";") || m.desc.equals("()L" + TARGET_BLOCK + ";")).toList();
        require(blockMethods.size() == 1, "One native Block factory");
        var blockFactory = blockMethods.get(0);
        boolean old = blockFactory.desc.equals("()L" + OLD_BLOCK + ";");
        String block = old ? OLD_BLOCK : TARGET_BLOCK;
        String item = old ? "net/minecraft/item/Item" : "net/minecraft/world/item/Item";
        String blockItem = old ? "net/minecraft/item/BlockItem" : "net/minecraft/world/item/BlockItem";
        String props = old ? "net/minecraft/block/AbstractBlock$Properties" : TARGET_PROPERTIES;
        String itemProps = item + "$Properties";
        String registryObject = old ? "net/minecraftforge/fml/RegistryObject" : "net/minecraftforge/registries/RegistryObject";
        require(blockFactory.access == (ACC_PRIVATE | ACC_STATIC), "Private static Block factory");
        var itemFactories = entry.methods.stream().filter(m -> m.desc.equals("()L" + blockItem + ";")).toList();
        require(itemFactories.size() == 1 && itemFactories.get(0).access == (ACC_PRIVATE | ACC_STATIC),
            "One private static BlockItem factory");
        var itemFactory = itemFactories.get(0);

        var init = code(method(entry, "<clinit>", "()V", ACC_STATIC));
        require(init.size() == 19, "Only deferred registry declarations and supplier registration");
        var identities = entry.fields.stream().filter(f -> f != null && "Ljava/lang/String;".equals(f.desc)).toList();
        require(identities.size() == 1, "Unique literal mod-identity field");
        var identity = field(entry, identities.get(0).name, "Ljava/lang/String;", null);
        require(identity.value instanceof String id && id.matches("[a-z][a-z0-9_]{1,63}"), "Literal mod identity");
        String modId = (String) identity.value;
        var blocks = field(entry, roleFieldName(init.get(3), entry.name), "L" + DEFERRED + ";", generic(DEFERRED, block));
        var items = field(entry, roleFieldName(init.get(7), entry.name), "L" + DEFERRED + ";", generic(DEFERRED, item));
        var blockReference = field(entry, roleFieldName(init.get(12), entry.name), "L" + registryObject + ";", generic(registryObject, block));
        var itemReference = field(entry, roleFieldName(init.get(17), entry.name), "L" + registryObject + ";", generic(registryObject, blockItem));
        require(new HashSet<>(List.of(identity.name, blocks.name, items.name, blockReference.name, itemReference.name)).size() == 5,
            "Five distinct structurally proven field roles");
        for (var value : List.of(blocks, items, blockReference, itemReference)) require(value.value == null,
            "Native registry fields have no constant initialization");
        require(entry.visibleAnnotations != null && entry.visibleAnnotations.size() == 1
            && empty(entry.invisibleAnnotations), "Exactly runtime-visible @Mod");
        var annotation = entry.visibleAnnotations.get(0);
        require(annotation.desc.equals("Lnet/minecraftforge/fml/common/Mod;")
            && List.of("value", modId).equals(annotation.values), "Exact annotated mod identity");
        innerMetadata(entry, props, itemProps);

        registerDeclaration(init, 0, entry.name, blocks, "BLOCKS", modId);
        registerDeclaration(init, 4, entry.name, items, "ITEMS", modId);
        String name = registration(init, 8, entry.name, blocks, blockReference, blockFactory, registryObject);
        require(name.equals(registration(init, 13, entry.name, items, itemReference, itemFactory, registryObject)),
            "Block and BlockItem preserve the same registry path");
        same(init.get(18), op(RETURN));
        var ctor = code(method(entry, "<init>", "()V", ACC_PUBLIC));
        require(ctor.size() == 11, "Only two direct MOD-bus attachments");
        same(ctor.get(0), v(ALOAD, 0));
        same(ctor.get(1), m(INVOKESPECIAL, "java/lang/Object", "<init>", "()V"));
        attach(ctor, 2, entry.name, blocks);
        attach(ctor, 6, entry.name, items);
        same(ctor.get(10), op(RETURN));

        var b = code(blockFactory);
        require(b.size() == (old ? 9 : 12), "Only native stone-properties Block construction");
        same(b.get(0), type(NEW, block)); same(b.get(1), op(DUP));
        int offset;
        if (old) {
            String material = "net/minecraft/block/material/Material";
            same(b.get(2), f(GETSTATIC, material, "field_151576_e", "L" + material + ";"));
            same(b.get(3), m(INVOKESTATIC, props, "func_200945_a", "(L" + material + ";)L" + props + ";"));
            offset = 4;
        } else {
            same(b.get(2), m(INVOKESTATIC, props, "m_284310_", "()L" + props + ";"));
            same(b.get(3), f(GETSTATIC, MAP_COLOR, "f_283947_", "L" + MAP_COLOR + ";"));
            same(b.get(4), m(INVOKEVIRTUAL, props, "m_284180_", "(L" + MAP_COLOR + ";)L" + props + ";"));
            same(b.get(5), f(GETSTATIC, INSTRUMENT, "BASEDRUM", "L" + INSTRUMENT + ";"));
            same(b.get(6), m(INVOKEVIRTUAL, props, "m_280658_", "(L" + INSTRUMENT + ";)L" + props + ";"));
            offset = 7;
        }
        float hardness = finiteFloat(b.get(offset)), resistance = finiteFloat(b.get(offset + 1));
        same(b.get(offset + 2), m(INVOKEVIRTUAL, props, old ? "func_200948_a" : "m_60913_", "(FF)L" + props + ";"));
        same(b.get(offset + 3), m(INVOKESPECIAL, block, "<init>", "(L" + props + ";)V"));
        same(b.get(offset + 4), op(ARETURN));

        var i = code(itemFactory);
        require(i.size() == 12, "Only delayed Block lookup and native BlockItem construction");
        same(i.get(0), type(NEW, blockItem)); same(i.get(1), op(DUP));
        same(i.get(2), f(GETSTATIC, entry.name, blockReference.name, blockReference.desc));
        String erased = old ? "net/minecraftforge/registries/IForgeRegistryEntry" : "java/lang/Object";
        same(i.get(3), m(INVOKEVIRTUAL, registryObject, "get", "()L" + erased + ";"));
        same(i.get(4), type(CHECKCAST, block));
        same(i.get(5), type(NEW, itemProps)); same(i.get(6), op(DUP));
        same(i.get(7), m(INVOKESPECIAL, itemProps, "<init>", "()V"));
        int stackSize = literal(i.get(8)); require(stackSize >= 1 && stackSize <= 64, "Stack limit in 1..64");
        same(i.get(9), m(INVOKEVIRTUAL, itemProps, old ? "func_200917_a" : "m_41487_", "(I)L" + itemProps + ";"));
        same(i.get(10), m(INVOKESPECIAL, blockItem, "<init>", "(L" + block + ";L" + itemProps + ";)V"));
        same(i.get(11), op(ARETURN));
        return new Proof(entry.name, modId, name, hardness, resistance, stackSize);
    }

    /** Target-only resource inventory, never a replacement bootstrap or executable projection. */
    public static ContentPlan validate(Map<String,ClassNode> nodes, String modId, String version) {
        var proof = inspect(nodes);
        require(proof.modId().equals(modId), "Metadata identity");
        require(version != null && version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}"), "Exact finite mod version");
        require(nodes.get(proof.entry()).methods.stream().anyMatch(m -> m.desc.equals("()L" + TARGET_BLOCK + ";")),
            "Target-only resource view");
        return new ContentPlan(modId, version,
            List.of(new ContentPlan.ItemDefinition(proof.registryName(), modId + ":" + proof.registryName(),
                new ContentPlan.ItemProperties(proof.stackSize(), 0, false, "COMMON", null))),
            List.of(new ContentPlan.BlockDefinition(proof.registryName(),
                new ContentPlan.BlockProperties("STONE", proof.hardness(), proof.resistance(), "STONE", false, false))),
            List.of());
    }

    private static void registerDeclaration(List<AbstractInsnNode> code, int offset, String owner,
                                            FieldNode field, String registryName, String modId) {
        same(code.get(offset), f(GETSTATIC, REGISTRIES, registryName, "L" + REGISTRY + ";"));
        same(code.get(offset + 1), ldc(modId));
        same(code.get(offset + 2), m(INVOKESTATIC, DEFERRED, "create", "(L" + REGISTRY + ";Ljava/lang/String;)L" + DEFERRED + ";"));
        same(code.get(offset + 3), f(PUTSTATIC, owner, field.name, field.desc));
    }
    private static String registration(List<AbstractInsnNode> code, int offset, String owner, FieldNode register,
                                       FieldNode object, MethodNode factory, String registryObject) {
        same(code.get(offset), f(GETSTATIC, owner, register.name, register.desc));
        require(code.get(offset + 1) instanceof LdcInsnNode l && l.cst instanceof String name
            && name.matches("[a-z0-9_]{1,128}"), "Literal simple registry name");
        Handle handle = callback(code.get(offset + 2), "java/util/function/Supplier", "get",
            "()Ljava/lang/Object;", factory.desc, owner);
        require(handle.getName().equals(factory.name), "Exact proven owned factory callback");
        same(code.get(offset + 3), m(INVOKEVIRTUAL, DEFERRED, "register", "(Ljava/lang/String;Ljava/util/function/Supplier;)L" + registryObject + ";"));
        same(code.get(offset + 4), f(PUTSTATIC, owner, object.name, object.desc));
        return (String) ((LdcInsnNode) code.get(offset + 1)).cst;
    }
    private static void attach(List<AbstractInsnNode> code, int offset, String owner, FieldNode register) {
        same(code.get(offset), f(GETSTATIC, owner, register.name, register.desc));
        same(code.get(offset + 1), m(INVOKESTATIC, CONTEXT, "get", "()L" + CONTEXT + ";"));
        same(code.get(offset + 2), m(INVOKEVIRTUAL, CONTEXT, "getModEventBus", "()L" + BUS + ";"));
        same(code.get(offset + 3), m(INVOKEVIRTUAL, DEFERRED, "register", "(L" + BUS + ";)V"));
    }
    private static FieldNode field(ClassNode entry, String name, String descriptor, String signature) {
        var fields = entry.fields.stream().filter(f -> f != null && f.name.equals(name)).toList();
        require(fields.size() == 1, "Unique field " + name);
        var field = fields.get(0);
        require(field.access == (ACC_PUBLIC | ACC_STATIC | ACC_FINAL) && field.desc.equals(descriptor)
            && (field.signature == null || Objects.equals(field.signature, signature))
            && empty(field.visibleAnnotations) && empty(field.invisibleAnnotations)
            && empty(field.visibleTypeAnnotations) && empty(field.invisibleTypeAnnotations) && empty(field.attrs),
            "Plain final registration field " + name);
        return field;
    }
    private static String roleFieldName(AbstractInsnNode instruction, String owner) {
        require(instruction instanceof FieldInsnNode field && field.getOpcode() == PUTSTATIC && field.owner.equals(owner),
            "Owned static field declaration for registry role");
        return ((FieldInsnNode) instruction).name;
    }
    private static void innerMetadata(ClassNode entry, String props, String itemProps) {
        if (entry.innerClasses == null) return;
        var names = new HashSet<String>();
        for (var inner : entry.innerClasses) {
            require(inner != null && names.add(inner.name) && Set.of(props, itemProps, "java/lang/invoke/MethodHandles$Lookup").contains(inner.name),
                "Only known native Properties/Lookup inner metadata");
            int split = inner.name.lastIndexOf('$');
            require(inner.name.substring(0, split).equals(inner.outerName)
                && inner.name.substring(split + 1).equals(inner.innerName)
                && inner.access == (ACC_PUBLIC | ACC_STATIC | (inner.name.endsWith("$Lookup") ? ACC_FINAL : 0)),
                "Exact native inner-class metadata");
        }
    }
    private static String generic(String owner, String parameter) { return "L" + owner + "<L" + parameter + ";>;"; }
    private static float finiteFloat(AbstractInsnNode instruction) {
        float value;
        if (instruction.getOpcode() >= FCONST_0 && instruction.getOpcode() <= FCONST_2) value = instruction.getOpcode() - FCONST_0;
        else if (instruction instanceof LdcInsnNode literal && literal.cst instanceof Float number) value = number;
        else throw new TransformationException("PLAIN_BLOCK_UNSUPPORTED: Native float literal required");
        require(Float.isFinite(value) && value >= 0 && value <= 32767 && Float.floatToIntBits(value) != Float.floatToIntBits(-0.0F),
            "Finite nonnegative strength literal in 0..32767");
        return value;
    }
    private static void require(boolean accepted, String reason) {
        if (!accepted) throw new TransformationException("PLAIN_BLOCK_UNSUPPORTED: " + reason);
    }
}
