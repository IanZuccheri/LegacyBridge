package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.conversion.extract.ContentPlan;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import dev.legacybridge.converter.transformation.TransformationException;
import dev.legacybridge.converter.transformation.linkage.OwnedConstructorGraph;
import dev.legacybridge.converter.transformation.linkage.OwnedItemHierarchy;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structural admission for the first body-preserving profile, never a body interpreter.
 * Call only after VerifiedLogicMappings has proved every external member and native override.
 * Target trees use production names: this class deliberately does not guess SRG method names.
 * The sole approved Properties(int)->Properties call is identified structurally; its actual
 * member-name/semantic contract is established by that preceding mapping compiler.
 * The returned plan indexes resources only. It must never replace the original Item subclass.
 */
final class LogicProfileShape {
    private static final String ITEM = "net/minecraft/world/item/Item";
    private static final String PROPERTIES = ITEM + "$Properties";
    private static final String DEFERRED = "net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRIES = "net/minecraftforge/registries/ForgeRegistries";
    private static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRY_OBJECT = "net/minecraftforge/registries/RegistryObject";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final String MOD = "Lnet/minecraftforge/fml/common/Mod;";
    private static final String METAFACTORY_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
    private static final Set<String> REGISTRATION_TYPES = Set.of(DEFERRED, REGISTRIES, REGISTRY,
            REGISTRY_OBJECT, CONTEXT, BUS);
    private static final Set<String> FUNCTIONAL_INTERFACES = Set.of("java/util/function/Supplier",
            "java/util/function/IntUnaryOperator");
    private static final int MAX_CLASSES = 32;
    private static final int MAX_TOTAL_METHODS = 256;
    private static final int MAX_METHOD_INSTRUCTIONS = 4096;
    private static final int MAX_TOTAL_INSTRUCTIONS = 32768;

    private LogicProfileShape() { }

    static ContentPlan validate(Map<String, ClassNode> targetNodes, String modId, String version) {
        return validate(targetNodes, modId, version, false);
    }
    /** Private candidate admission; ordinary profiles retain the single-constructor grammar. */
    static ContentPlan validateOwnedConstructors(Map<String, ClassNode> targetNodes, String modId, String version) {
        return validate(targetNodes, modId, version, true);
    }
    private static ContentPlan validate(Map<String, ClassNode> targetNodes, String modId, String version, boolean delegation) {
        return validate(targetNodes, modId, version, delegation, null);
    }
    static ContentPlan validateOwnedHierarchy(Map<String, ClassNode> targetNodes, String modId, String version, OwnedItemHierarchy.Proof proof) {
        require(proof != null && ITEM.equals(proof.nativeItem()), "Target hierarchy proof is required");
        return validate(targetNodes, modId, version, false, proof);
    }
    private static ContentPlan validate(Map<String, ClassNode> targetNodes, String modId, String version,
                                        boolean delegation, OwnedItemHierarchy.Proof proof) {
        require(targetNodes != null && !targetNodes.isEmpty() && targetNodes.size() <= MAX_CLASSES,
                "Expected 1..32 owned classes");
        require(modId != null && modId.matches("[a-z][a-z0-9_]{1,63}"), "Invalid finite-profile modId");
        require(version != null && version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}"),
                "Invalid exact mod version");
        ClassNode entry = null;
        ClassNode item = null;
        int methods = 0;
        int instructions = 0;
        for (var pair : targetNodes.entrySet()) {
            OperationControl.checkpoint();
            ClassNode node = pair.getValue();
            require(node != null && pair.getKey().equals(node.name)
                            && !LegacyReferenceScanner.isProtectedDefinition(node.name),
                    "Classes must be owned, uniquely named map entries");
            require(node.version == Opcodes.V1_8, "Only genuine Java 8 source classfiles are admitted");
            require(node.methods != null && node.fields != null && node.interfaces != null,
                    "Incomplete class tree");
            require(node.methods.size() <= 128 && node.fields.size() <= 128, "Class member limit exceeded");
            methods += node.methods.size();
            require(methods <= MAX_TOTAL_METHODS, "Total method limit exceeded");
            Set<String> signatures = new HashSet<>();
            for (MethodNode method : node.methods) {
                require(method != null && method.instructions != null
                                && signatures.add(method.name + method.desc), "Duplicate or incomplete method");
                require(method.instructions.size() <= MAX_METHOD_INSTRUCTIONS,
                        "Per-method instruction/debug-node limit exceeded");
                require(method.tryCatchBlocks == null || method.tryCatchBlocks.size() <= 128,
                        "Exception-handler limit exceeded");
                instructions += method.instructions.size();
                require(instructions <= MAX_TOTAL_INSTRUCTIONS, "Total instruction/debug-node limit exceeded");
            }
            Set<String> fields = new HashSet<>();
            for (FieldNode field : node.fields) require(fields.add(field.name + ":" + field.desc),
                    "Duplicate field declaration");
            for (AnnotationNode annotation : annotations(node)) {
                if (!MOD.equals(annotation.desc)) continue;
                require(entry == null, "Exactly one Forge entrypoint is permitted");
                require(List.of("value", modId).equals(annotation.values), "Forge annotation and metadata modId differ");
                entry = node;
            }
            if (ITEM.equals(node.superName)) {
                require(item == null, "Exactly one direct native Item subclass is permitted");
                item = node;
            }
        }
        require(entry != null && item != null && entry != item, "One entrypoint and one distinct Item subclass are required");
        if (proof != null) {
            require(item.name.equals(proof.base()) && targetNodes.containsKey(proof.leaf())
                    && item.name.equals(targetNodes.get(proof.leaf()).superName), "Hierarchy proof/owned nodes mismatch");
            item = targetNodes.get(proof.leaf());
        }
        for (String owner : targetNodes.keySet()) hierarchy(owner, targetNodes, new HashSet<>());
        require("java/lang/Object".equals(entry.superName) && entry.interfaces.isEmpty()
                        && (entry.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) == Opcodes.ACC_PUBLIC,
                "Entrypoint must be a concrete public direct Object subclass");
        require((item.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) == Opcodes.ACC_PUBLIC,
                "Registered Item subclass must be concrete and public");
        require(entry.methods.size() == 2 && entry.fields.size() == 3,
                "Entrypoint admits only its constructor, initializer and three proven identity/registration fields");

        MethodNode initializer = method(entry, "<clinit>", "()V");
        MethodNode constructor = method(entry, "<init>", "()V");
        require((initializer.access & Opcodes.ACC_STATIC) != 0, "Static registration initializer is required");
        require((constructor.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_PUBLIC,
                "Entrypoint requires a public zero-argument constructor");
        noHandlers(initializer);
        noHandlers(constructor);
        List<AbstractInsnNode> init = code(initializer);
        List<AbstractInsnNode> ctor = code(constructor);
        require(init.size() == 10 && ctor.size() == 7,
                "Registration initializer/constructor must match the exact finite envelope without extra effects");
        field(init.get(0), Opcodes.GETSTATIC, REGISTRIES, "ITEMS", "L" + REGISTRY + ";");
        constant(init.get(1), modId);
        call(init.get(2), Opcodes.INVOKESTATIC, DEFERRED, "create",
                "(L" + REGISTRY + ";Ljava/lang/String;)L" + DEFERRED + ";");
        FieldInsnNode registerField = ownedField(init.get(3), Opcodes.PUTSTATIC, entry, "L" + DEFERRED + ";");
        field(init.get(4), Opcodes.GETSTATIC, entry.name, registerField.name, registerField.desc);
        require(init.get(5) instanceof LdcInsnNode name && name.cst instanceof String,
                "Registry name must be one literal string");
        String registryName = (String) ((LdcInsnNode) init.get(5)).cst;
        require(registryName.matches("[a-z0-9_]{1,128}"), "Registry name must be a simple local path");
        supplier(init.get(6), item.name);
        call(init.get(7), Opcodes.INVOKEVIRTUAL, DEFERRED, "register",
                "(Ljava/lang/String;Ljava/util/function/Supplier;)L" + REGISTRY_OBJECT + ";");
        FieldInsnNode objectField = ownedField(init.get(8), Opcodes.PUTSTATIC, entry, "L" + REGISTRY_OBJECT + ";");
        opcode(init.get(9), Opcodes.RETURN);
        require(!registerField.name.equals(objectField.name), "Registration fields must be distinct");
        int identityFields = 0;
        for (FieldNode field : entry.fields) {
            require((field.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL))
                            == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL),
                    "Entrypoint fields must be public static final");
            if (field.name.equals(registerField.name) || field.name.equals(objectField.name)) {
                require(field.value == null, "Registration fields cannot have constant values");
            } else {
                require("Ljava/lang/String;".equals(field.desc) && modId.equals(field.value),
                        "The remaining entrypoint field must retain the literal mod identity");
                identityFields++;
            }
        }
        require(identityFields == 1, "Exactly one literal mod identity field is required");

        variable(ctor.get(0), Opcodes.ALOAD, 0);
        call(ctor.get(1), Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        field(ctor.get(2), Opcodes.GETSTATIC, entry.name, registerField.name, registerField.desc);
        call(ctor.get(3), Opcodes.INVOKESTATIC, CONTEXT, "get", "()L" + CONTEXT + ";");
        call(ctor.get(4), Opcodes.INVOKEVIRTUAL, CONTEXT, "getModEventBus", "()L" + BUS + ";");
        call(ctor.get(5), Opcodes.INVOKEVIRTUAL, DEFERRED, "register", "(L" + BUS + ";)V");
        opcode(ctor.get(6), Opcodes.RETURN);

        int stackSize = proof != null ? itemConstructor(targetNodes.get(proof.base()), Opcodes.ACC_PROTECTED)
                : delegation ? delegatedItemConstructor(item) : itemConstructor(item);
        for (ClassNode node : targetNodes.values()) if (node != entry) forbidRegistrationEscape(node, entry);
        return new ContentPlan(modId, version,
                List.of(new ContentPlan.ItemDefinition(registryName, null,
                        new ContentPlan.ItemProperties(stackSize, 0, false, "COMMON", null))),
                List.of(), List.of());
    }

    private static int itemConstructor(ClassNode item) {
        return itemConstructor(item, Opcodes.ACC_PUBLIC);
    }
    private static int itemConstructor(ClassNode item, int access) {
        require(item.methods.stream().filter(method -> "<init>".equals(method.name)).count() == 1,
                "Registered Item admits one constructor only");
        MethodNode constructor = method(item, "<init>", "()V");
        require(access == Opcodes.ACC_PUBLIC ? (constructor.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_PUBLIC
                        : constructor.access == Opcodes.ACC_PROTECTED,
                "Native Item constructor must have the exact admitted visibility and zero arguments");
        List<AbstractInsnNode> instructions = code(constructor);
        require(instructions.size() >= 8, "Item constructor lacks its required native properties prefix");
        variable(instructions.get(0), Opcodes.ALOAD, 0);
        require(instructions.get(1) instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW
                        && PROPERTIES.equals(type.desc), "Item constructor must create native Properties directly");
        opcode(instructions.get(2), Opcodes.DUP);
        call(instructions.get(3), Opcodes.INVOKESPECIAL, PROPERTIES, "<init>", "()V");
        int stackSize = integer(instructions.get(4));
        require(stackSize >= 1 && stackSize <= 64, "Native stack limit must be a literal in 1..64");
        // Exact member name has already been proved by VerifiedLogicMappings. There is only
        // one approved native method with this owner/descriptor/opcode in that closed catalog.
        require(instructions.get(5) instanceof MethodInsnNode property
                        && property.getOpcode() == Opcodes.INVOKEVIRTUAL && !property.itf
                        && PROPERTIES.equals(property.owner) && ("(I)L" + PROPERTIES + ";").equals(property.desc),
                "Item constructor requires the previously verified native stack-size property call");
        call(instructions.get(6), Opcodes.INVOKESPECIAL, ITEM, "<init>", "(L" + PROPERTIES + ";)V");
        // All subsequent own field/helper instructions remain uninterpreted and unmodified.
        return stackSize;
    }

    static OwnedConstructorGraph.Proof constructorProof(Map<String, ClassNode> nodes) {
        var items = nodes.values().stream().filter(n -> ITEM.equals(n.superName)).toList();
        require(items.size() == 1, "Exactly one direct Item constructor owner is required");
        return OwnedConstructorGraph.inspect(items.get(0));
    }
    private static int delegatedItemConstructor(ClassNode item) {
        var proof = OwnedConstructorGraph.inspect(item);
        var terminal = proof.terminal(); var instructions = code(method(item, "<init>", terminal.descriptor()));
        require(instructions.size() >= 8, "Delegated Item terminal lacks native properties prefix");
        variable(instructions.get(0), Opcodes.ALOAD, 0);
        require(instructions.get(1) instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW
                && PROPERTIES.equals(type.desc), "Delegated Item must construct native Properties directly");
        opcode(instructions.get(2), Opcodes.DUP);
        call(instructions.get(3), Opcodes.INVOKESPECIAL, PROPERTIES, "<init>", "()V");
        int stackSize = OwnedConstructorGraph.argument(instructions.get(4), terminal.arguments());
        require(stackSize >= 1 && stackSize <= 64, "Proven terminal native stack limit must be in 1..64");
        require(instructions.get(5) instanceof MethodInsnNode property && property.getOpcode() == Opcodes.INVOKEVIRTUAL
                && !property.itf && PROPERTIES.equals(property.owner) && ("(I)L" + PROPERTIES + ";").equals(property.desc),
                "Delegated property call must have its separately verified native mapping");
        call(instructions.get(6), Opcodes.INVOKESPECIAL, ITEM, "<init>", "(L" + PROPERTIES + ";)V");
        return stackSize;
    }

    private static void forbidRegistrationEscape(ClassNode node, ClassNode entry) {
        for (FieldNode field : node.fields) require(!registrationDescriptor(field.desc),
                "Registration state cannot escape into owned helpers");
        for (MethodNode method : node.methods) {
            require(!registrationDescriptor(method.desc), "Helpers cannot expose registration/lifecycle contracts");
            for (AbstractInsnNode instruction : method.instructions) {
                OperationControl.checkpoint();
                if (instruction instanceof MethodInsnNode call) {
                    require(!REGISTRATION_TYPES.contains(call.owner) && !entry.name.equals(call.owner)
                                    && !registrationDescriptor(call.desc),
                            "Registration/lifecycle calls outside the entrypoint are unsupported");
                } else if (instruction instanceof FieldInsnNode field) {
                    require(!REGISTRATION_TYPES.contains(field.owner) && !registrationDescriptor(field.desc),
                            "Registration field access outside the entrypoint is unsupported");
                } else if (instruction instanceof TypeInsnNode type) {
                    require(!REGISTRATION_TYPES.contains(type.desc) && !entry.name.equals(type.desc),
                            "Registration/entrypoint objects cannot be constructed or cast by helpers");
                } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    for (Object argument : dynamic.bsmArgs) if (argument instanceof Handle handle) {
                        require(!REGISTRATION_TYPES.contains(handle.getOwner()) && !entry.name.equals(handle.getOwner())
                                        && !registrationDescriptor(handle.getDesc()),
                                "Registration cannot escape through a method handle");
                    }
                }
            }
        }
    }

    private static boolean registrationDescriptor(String descriptor) {
        return REGISTRATION_TYPES.stream().anyMatch(owner -> descriptor.contains("L" + owner + ";"));
    }

    private static void supplier(AbstractInsnNode instruction, String itemOwner) {
        require(instruction instanceof InvokeDynamicInsnNode, "Registration requires a direct constructor supplier");
        InvokeDynamicInsnNode dynamic = (InvokeDynamicInsnNode) instruction;
        require("get".equals(dynamic.name) && "()Ljava/util/function/Supplier;".equals(dynamic.desc)
                        && dynamic.bsm.getTag() == Opcodes.H_INVOKESTATIC && !dynamic.bsm.isInterface()
                        && "java/lang/invoke/LambdaMetafactory".equals(dynamic.bsm.getOwner())
                        && "metafactory".equals(dynamic.bsm.getName()) && METAFACTORY_DESC.equals(dynamic.bsm.getDesc())
                        && dynamic.bsmArgs.length == 3
                        && Type.getMethodType("()Ljava/lang/Object;").equals(dynamic.bsmArgs[0])
                        && dynamic.bsmArgs[1] instanceof Handle
                        && (Type.getMethodType("()L" + itemOwner + ";").equals(dynamic.bsmArgs[2])
                            || Type.getMethodType("()L" + ITEM + ";").equals(dynamic.bsmArgs[2])),
                "Registration must use the exact zero-capture LambdaMetafactory constructor shape");
        Handle implementation = (Handle) dynamic.bsmArgs[1];
        require(implementation.getTag() == Opcodes.H_NEWINVOKESPECIAL && !implementation.isInterface()
                        && itemOwner.equals(implementation.getOwner()) && "<init>".equals(implementation.getName())
                        && "()V".equals(implementation.getDesc()),
                "Registry factories or alternate supplier implementations are unsupported");
    }

    private static void hierarchy(String owner, Map<String, ClassNode> nodes, Set<String> visiting) {
        if ("java/lang/Object".equals(owner) || ITEM.equals(owner) || FUNCTIONAL_INTERFACES.contains(owner)) return;
        require(visiting.add(owner), "Owned hierarchy cycle");
        ClassNode node = nodes.get(owner);
        require(node != null, "Unknown superclass or interface: " + owner);
        require(node.superName != null, "Owned classes/interfaces must retain their declared superclass");
        hierarchy(node.superName, nodes, new HashSet<>(visiting));
        for (String contract : node.interfaces) hierarchy(contract, nodes, new HashSet<>(visiting));
    }

    private static List<AnnotationNode> annotations(ClassNode node) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
        if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
        return annotations;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream().filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
                .findFirst().orElseThrow(() -> failure("Required method absent: " + owner.name + "." + name + descriptor));
    }

    private static List<AbstractInsnNode> code(MethodNode method) {
        List<AbstractInsnNode> code = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) code.add(instruction);
        return code;
    }

    private static void noHandlers(MethodNode method) {
        require(method.tryCatchBlocks == null || method.tryCatchBlocks.isEmpty(),
                "Registration envelope cannot contain exception handlers");
    }

    private static FieldInsnNode ownedField(AbstractInsnNode instruction, int opcode, ClassNode owner, String descriptor) {
        require(instruction instanceof FieldInsnNode, "Expected owned registration field write");
        FieldInsnNode field = (FieldInsnNode) instruction;
        field(instruction, opcode, owner.name, field.name, descriptor);
        require(owner.fields.stream().anyMatch(declaration -> declaration.name.equals(field.name)
                        && declaration.desc.equals(descriptor)), "Missing owned registration field declaration");
        return field;
    }

    private static void field(AbstractInsnNode instruction, int opcode, String owner, String name, String descriptor) {
        require(instruction instanceof FieldInsnNode field && field.getOpcode() == opcode
                        && owner.equals(field.owner) && name.equals(field.name) && descriptor.equals(field.desc),
                "Unexpected registration field instruction");
    }

    private static void call(AbstractInsnNode instruction, int opcode, String owner, String name, String descriptor) {
        require(instruction instanceof MethodInsnNode call && call.getOpcode() == opcode && !call.itf
                        && owner.equals(call.owner) && name.equals(call.name) && descriptor.equals(call.desc),
                "Unexpected native registration/constructor call");
    }

    private static void constant(AbstractInsnNode instruction, String value) {
        require(instruction instanceof LdcInsnNode constant && value.equals(constant.cst),
                "Unexpected literal registration identity");
    }

    private static void variable(AbstractInsnNode instruction, int opcode, int index) {
        require(instruction instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == index,
                "Unexpected constructor local-variable instruction");
    }

    private static void opcode(AbstractInsnNode instruction, int opcode) {
        require(instruction.getOpcode() == opcode, "Unexpected envelope opcode");
    }

    private static int integer(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) return opcode - Opcodes.ICONST_0;
        if (instruction instanceof IntInsnNode value && (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH)) return value.operand;
        if (instruction instanceof LdcInsnNode value && value.cst instanceof Integer integer) return integer;
        throw failure("Stack limit must be a direct integer literal");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    private static TransformationException failure(String message) {
        return new TransformationException("LOGIC_PROFILE_SHAPE: " + message);
    }
}
