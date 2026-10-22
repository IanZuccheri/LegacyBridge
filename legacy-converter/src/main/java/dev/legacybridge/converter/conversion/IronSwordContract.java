package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;
import static dev.legacybridge.converter.conversion.ClientConfigContract.*;

/**
 * Legacy API: DeferredRegister<Item> and native SwordItem(IItemTier,int,float,Item.Properties).
 * Target API: the corresponding registration graph and SwordItem(Tier,int,float,Item.Properties).
 * Transformation: NONE. This read-only structural recognition does not authorize remapping,
 * generate a candidate or substitute SwordItem with an Item-shaped bootstrap/projection.
 * Behavior differences: inherited material-based mining becomes tag-driven; native ToolActions
 * and dispatch differ. Constructor recognition proves neither these behaviors nor API identity.
 * Known limitations: one exact IRON/3/-2.4F/fresh-properties factory and one deferred item;
 * external declarations require independent audit, semantic migration remains UNSUPPORTED.
 * No class loading, runtime validation, public profile or publication authority is granted.
 */
public final class IronSwordContract {
    private static final String DEFERRED = "net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRIES = "net/minecraftforge/registries/ForgeRegistries";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final String OLD_ITEM = "net/minecraft/item/Item";
    private static final String TARGET_ITEM = "net/minecraft/world/item/Item";

    private IronSwordContract() { }

    public record Proof(String entry, String modId, String registryName, String factory) {
        public Map<String,Object> toTree() {
            return Map.ofEntries(Map.entry("schemaVersion", 1), Map.entry("contract", "iron-sword-construction-shape-v1"),
                Map.entry("entry", entry), Map.entry("modId", modId), Map.entry("registryName", registryName),
                Map.entry("factory", factory), Map.entry("constructionRecognized", true),
                Map.entry("constructorInputs", Map.of("nativeType", "SwordItem", "tier", "IRON",
                    "attackLiteral", 3, "speedLiteral", -2.4F, "speedFloatBits", Float.floatToIntBits(-2.4F),
                    "properties", "FRESH_NATIVE_PROPERTIES_WITHOUT_CUSTOMIZATION")),
                Map.entry("registration", "ONE_ZERO_CAPTURE_STATIC_SUPPLIER_ON_MOD_BUS"),
                Map.entry("externalDeclarations", "REQUIRE_SEPARATE_VERIFICATION"),
                Map.entry("inheritedBehaviorEquivalent", false), Map.entry("semanticMigration", "UNSUPPORTED"),
                Map.entry("conversionAllowed", false), Map.entry("publicationAllowed", false),
                Map.entry("bytecodeModified", false), Map.entry("classesExecuted", false),
                Map.entry("runtimeValidation", "NOT_RUN"));
        }
    }

    public static Proof inspect(Map<String,ClassNode> nodes) {
        try {
            return inspectClosed(nodes);
        } catch (TransformationException refusal) {
            if (refusal.getMessage().startsWith("IRON_SWORD_UNSUPPORTED:")) throw refusal;
            throw new TransformationException("IRON_SWORD_UNSUPPORTED: " + refusal.getMessage(), refusal);
        }
    }

    /** Checks source or target metadata identity only; never produces a conversion ContentPlan. */
    public static Proof validate(Map<String,ClassNode> nodes, String modId, String version) {
        var proof = inspect(nodes);
        require(proof.modId().equals(modId), "Metadata modId must match the inspected literal identity");
        require(version != null && version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}"), "Exact finite mod version");
        return proof;
    }

    private static Proof inspectClosed(Map<String,ClassNode> nodes) {
        OperationControl.checkpoint();
        require(nodes != null && nodes.size() == 1, "Exactly one owned entry; no sword subclass or helpers");
        var pair = nodes.entrySet().iterator().next(); var entry = pair.getValue();
        require(entry != null && entry.name != null && entry.name.equals(pair.getKey())
            && !LegacyReferenceScanner.isProtectedDefinition(entry.name), "Owned class identity");
        require(entry.version == V1_8 && entry.access == (ACC_PUBLIC | ACC_FINAL | ACC_SUPER)
            && "java/lang/Object".equals(entry.superName) && empty(entry.interfaces)
            && entry.fields != null && entry.fields.size() == 3 && entry.methods != null && entry.methods.size() == 3,
            "Final direct Java-8 Object entry with exactly three fields and three methods");
        metadata(entry);
        var factories = entry.methods.stream().filter(m -> m.desc.equals("()L" + OLD_ITEM + ";")
            || m.desc.equals("()L" + TARGET_ITEM + ";")).toList();
        require(factories.size() == 1 && factories.get(0).access == (ACC_PRIVATE | ACC_STATIC)
            && !factories.get(0).name.isBlank() && !factories.get(0).name.startsWith("<"),
            "One private static native Item-returning factory");
        var factory = factories.get(0); var api = new Names(factory.desc.equals("()L" + TARGET_ITEM + ";"));
        innerMetadata(entry, api);
        var identity = fieldOf(entry, "Ljava/lang/String;");
        require(identity.value instanceof String value && value.matches("[a-z][a-z0-9_]{1,63}"), "Literal mod identity");
        String modId = (String)identity.value;
        var register = fieldOf(entry, "L" + DEFERRED + ";");
        var reference = fieldOf(entry, "L" + api.reference + ";");
        require(new HashSet<>(List.of(identity.name, register.name, reference.name)).size() == 3,
            "Three distinct field roles");
        checkField(identity, null); checkField(register, generic(DEFERRED, api.item));
        checkField(reference, generic(api.reference, api.item));
        require(register.value == null && reference.value == null, "Registry fields have no constant value");
        require(entry.visibleAnnotations != null && entry.visibleAnnotations.size() == 1 && empty(entry.invisibleAnnotations),
            "Only runtime-visible @Mod");
        var annotation = entry.visibleAnnotations.get(0);
        require(annotation != null && "Lnet/minecraftforge/fml/common/Mod;".equals(annotation.desc)
            && List.of("value", modId).equals(annotation.values), "Exact annotated mod identity");

        var init = code(method(entry, "<clinit>", "()V", ACC_STATIC));
        require(init.size() == 10, "Only one deferred registry declaration and one supplier registration");
        same(init.get(0), f(GETSTATIC, REGISTRIES, "ITEMS", "L" + REGISTRY + ";"));
        require(init.get(1) instanceof LdcInsnNode id && modId.equals(id.cst), "Exact modId registration literal");
        same(init.get(2), m(INVOKESTATIC, DEFERRED, "create", "(L" + REGISTRY + ";Ljava/lang/String;)L" + DEFERRED + ";"));
        same(init.get(3), f(PUTSTATIC, entry.name, register.name, register.desc));
        same(init.get(4), f(GETSTATIC, entry.name, register.name, register.desc));
        require(init.get(5) instanceof LdcInsnNode name && name.cst instanceof String value
            && value.matches("[a-z0-9_]{1,128}"), "Exact simple registry path literal");
        String registryName = (String)((LdcInsnNode)init.get(5)).cst;
        var handle = callback(init.get(6), "java/util/function/Supplier", "get", "()Ljava/lang/Object;", factory.desc, entry.name);
        require(handle.getName().equals(factory.name), "Supplier names the proved factory, not another callback");
        same(init.get(7), m(INVOKEVIRTUAL, DEFERRED, "register", "(Ljava/lang/String;Ljava/util/function/Supplier;)L" + api.reference + ";"));
        same(init.get(8), f(PUTSTATIC, entry.name, reference.name, reference.desc)); same(init.get(9), op(RETURN));
        exact(method(entry, "<init>", "()V", ACC_PUBLIC), v(ALOAD, 0), m(INVOKESPECIAL, "java/lang/Object", "<init>", "()V"),
            f(GETSTATIC, entry.name, register.name, register.desc), m(INVOKESTATIC, CONTEXT, "get", "()L" + CONTEXT + ";"),
            m(INVOKEVIRTUAL, CONTEXT, "getModEventBus", "()L" + BUS + ";"),
            m(INVOKEVIRTUAL, DEFERRED, "register", "(L" + BUS + ";)V"), op(RETURN));
        var body = code(factory);
        require(body.size() == 10, "Only literal IRON sword construction; no customized properties or callbacks");
        require(body.get(4) instanceof LdcInsnNode speed && speed.cst instanceof Float number
            && Float.floatToIntBits(number) == Float.floatToIntBits(-2.4F), "Exact negative native float speed literal");
        exact(factory, type(NEW, api.sword), op(DUP), f(GETSTATIC, api.tiers, "IRON", "L" + api.tiers + ";"),
            op(ICONST_3), ldc(-2.4F), type(NEW, api.properties), op(DUP), m(INVOKESPECIAL, api.properties, "<init>", "()V"),
            m(INVOKESPECIAL, api.sword, "<init>", "(L" + api.tier + ";IFL" + api.properties + ";)V"), op(ARETURN));
        OperationControl.checkpoint();
        return new Proof(entry.name, modId, registryName, factory.name);
    }

    private static void metadata(ClassNode entry) {
        require(entry.signature == null && entry.outerClass == null && entry.outerMethod == null && entry.outerMethodDesc == null
            && entry.nestHostClass == null && empty(entry.nestMembers) && empty(entry.permittedSubclasses)
            && empty(entry.recordComponents) && entry.module == null && empty(entry.attrs)
            && empty(entry.visibleTypeAnnotations) && empty(entry.invisibleTypeAnnotations), "No additional class metadata contracts");
        var fields = new HashSet<String>();
        for (var field : entry.fields) require(field != null && field.name != null && !field.name.isBlank()
            && field.desc != null && fields.add(field.name), "Unique named field roles");
        var methods = new HashSet<String>();
        for (var method : entry.methods) {
            OperationControl.checkpoint();
            require(method != null && method.name != null && method.desc != null && methods.add(method.name + method.desc)
                && method.instructions != null && method.instructions.size() <= 128, "Bounded unique methods");
            require(method.maxStack >= 0 && method.maxStack <= 64 && method.maxLocals >= 0 && method.maxLocals <= 64,
                "Bounded declared stack and local slots before offline frame verification");
            require(method.signature == null && empty(method.tryCatchBlocks) && empty(method.exceptions)
                && empty(method.visibleAnnotations) && empty(method.invisibleAnnotations)
                && empty(method.visibleTypeAnnotations) && empty(method.invisibleTypeAnnotations)
                && empty(method.visibleLocalVariableAnnotations) && empty(method.invisibleLocalVariableAnnotations)
                && method.visibleParameterAnnotations == null && method.invisibleParameterAnnotations == null
                && method.annotationDefault == null && empty(method.attrs) && empty(method.parameters),
                "No handlers, generic, annotation or additional method contracts");
            for (var instruction : method.instructions) require(empty(instruction.visibleTypeAnnotations)
                && empty(instruction.invisibleTypeAnnotations), "No instruction annotation contracts");
        }
    }

    private static void checkField(FieldNode field, String signature) {
        require(field.access == (ACC_PUBLIC | ACC_STATIC | ACC_FINAL)
            && (field.signature == null || Objects.equals(field.signature, signature))
            && empty(field.visibleAnnotations) && empty(field.invisibleAnnotations)
            && empty(field.visibleTypeAnnotations) && empty(field.invisibleTypeAnnotations) && empty(field.attrs),
            "Only final constant identity and native registry fields with exact optional generic signature");
    }

    private static void innerMetadata(ClassNode entry, Names api) {
        if (entry.innerClasses == null) return;
        var names = new HashSet<String>();
        for (var inner : entry.innerClasses) {
            require(inner != null && inner.name != null && names.add(inner.name)
                && Set.of(api.properties, "java/lang/invoke/MethodHandles$Lookup").contains(inner.name),
                "Only native Properties and method-handle Lookup inner metadata");
            int split = inner.name.lastIndexOf('$');
            require(inner.name.substring(0, split).equals(inner.outerName) && inner.name.substring(split + 1).equals(inner.innerName)
                && inner.access == (ACC_PUBLIC | ACC_STATIC | (inner.name.endsWith("$Lookup") ? ACC_FINAL : 0)),
                "Exact known native inner-class metadata");
        }
    }

    private static String generic(String owner, String item) { return "L" + owner + "<L" + item + ";>;"; }

    private static void require(boolean accepted, String reason) {
        if (!accepted) throw new TransformationException("IRON_SWORD_UNSUPPORTED: " + reason);
    }

    private static final class Names {
        final String item, sword, properties, tier, tiers, reference;
        Names(boolean target) {
            item = target ? TARGET_ITEM : OLD_ITEM;
            sword = target ? "net/minecraft/world/item/SwordItem" : "net/minecraft/item/SwordItem";
            properties = item + "$Properties";
            tier = target ? "net/minecraft/world/item/Tier" : "net/minecraft/item/IItemTier";
            tiers = target ? "net/minecraft/world/item/Tiers" : "net/minecraft/item/ItemTier";
            reference = target ? "net/minecraftforge/registries/RegistryObject" : "net/minecraftforge/fml/RegistryObject";
        }
    }
}
