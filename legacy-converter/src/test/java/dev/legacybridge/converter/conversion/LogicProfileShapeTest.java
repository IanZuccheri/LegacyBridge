package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.transformation.TransformationException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** Inert target-shape tests. Native names are real official names, not fabricated SRG guesses. */
class LogicProfileShapeTest {
    private static final String ENTRY = "example/Entry";
    private static final String OWN_ITEM = "example/LogicItem";
    private static final String HELPER = "example/Helper";
    private static final String MOD_ID = "shape_fixture";
    private static final String ITEM = "net/minecraft/world/item/Item";
    private static final String PROPERTIES = ITEM + "$Properties";
    private static final String DEFERRED = "net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    private static final String OBJECT = "net/minecraftforge/registries/RegistryObject";
    private static final String REGISTRIES = "net/minecraftforge/registries/ForgeRegistries";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final Handle METAFACTORY = new Handle(Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory", "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);

    @Test
    void acceptsOneNativeRegistrationAndPreservesEveryOwnBodyAndDebugNode() {
        Map<String, ClassNode> nodes = fixture(16);
        Map<String, byte[]> before = bytes(nodes);
        var plan = LogicProfileShape.validate(nodes, MOD_ID, "1.0.0");
        assertEquals(MOD_ID, plan.modId());
        assertEquals("1.0.0", plan.modVersion());
        assertEquals(1, plan.items().size());
        assertEquals("parity_token", plan.items().get(0).name());
        assertEquals(16, plan.items().get(0).properties().stackSize());
        assertNull(plan.items().get(0).block());
        assertNull(plan.items().get(0).properties().creativeTab());
        assertTrue(plan.blocks().isEmpty());
        assertTrue(plan.sounds().isEmpty());
        Map<String, byte[]> after = bytes(nodes);
        before.forEach((name, original) -> assertArrayEquals(original, after.get(name), name));
    }

    @Test
    void acceptsBoundedLiteralStackLimitsWithoutInterpretingOwnHelperLogic() {
        for (int limit : new int[]{1, 2, 5, 16, 32, 64}) {
            assertEquals(limit, LogicProfileShape.validate(fixture(limit), MOD_ID, "1.0.0")
                    .items().get(0).properties().stackSize());
        }
    }

    @Test
    void acceptsNativeItemSupplierReturnFromTheGenuineForgeCompiledFixture() {
        Map<String, ClassNode> nodes = fixture(16);
        dynamic(nodes).bsmArgs[2] = Type.getMethodType("()L" + ITEM + ";");
        assertEquals("parity_token", LogicProfileShape.validate(nodes, MOD_ID, "1.0.0")
                .items().get(0).name());
        dynamic(nodes).bsmArgs[2] = Type.getMethodType("()Ljava/lang/Object;");
        refused(nodes, "zero-capture LambdaMetafactory constructor shape");
    }

    @Test
    void refusesFactorySupplierEvenWhenItReturnsTheSameDeclaredItemType() {
        Map<String, ClassNode> nodes = fixture(16);
        InvokeDynamicInsnNode supplier = dynamic(nodes);
        supplier.bsmArgs[1] = new Handle(Opcodes.H_INVOKESTATIC, HELPER, "factory", "()L" + OWN_ITEM + ";", false);
        MethodNode factory = method(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "factory", "()L" + OWN_ITEM + ";");
        factory.instructions.add(new TypeInsnNode(Opcodes.NEW, OWN_ITEM));
        factory.instructions.add(new InsnNode(Opcodes.DUP));
        factory.instructions.add(call(Opcodes.INVOKESPECIAL, OWN_ITEM, "<init>", "()V"));
        factory.instructions.add(new InsnNode(Opcodes.ARETURN));
        nodes.get(HELPER).methods.add(factory);
        refused(nodes, "Registry factories");
    }

    @Test
    void refusesExtraEntrypointEffects() {
        Map<String, ClassNode> nodes = fixture(16);
        MethodNode initializer = named(nodes.get(ENTRY), "<clinit>");
        InsnList extra = new InsnList();
        extra.add(new InsnNode(Opcodes.ICONST_1));
        extra.add(new FieldInsnNode(Opcodes.PUTSTATIC, HELPER, "state", "I"));
        initializer.instructions.insertBefore(initializer.instructions.getLast(), extra);
        refused(nodes, "exact finite envelope");
    }

    @Test
    void refusesUnknownExternalSuperclass() {
        Map<String, ClassNode> nodes = fixture(16);
        nodes.get(HELPER).superName = "foreign/UnknownBase";
        refused(nodes, "Unknown superclass or interface");
    }

    @Test
    void refusesSecondRegistryEvenWithoutASecondRegisteredItem() {
        Map<String, ClassNode> nodes = fixture(16);
        ClassNode entry = nodes.get(ENTRY);
        entry.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "EXTRA_ITEMS", "L" + DEFERRED + ";", null, null));
        InsnList extra = new InsnList();
        extra.add(new FieldInsnNode(Opcodes.GETSTATIC, REGISTRIES, "ITEMS", "L" + REGISTRY + ";"));
        extra.add(new LdcInsnNode(MOD_ID));
        extra.add(call(Opcodes.INVOKESTATIC, DEFERRED, "create", "(L" + REGISTRY + ";Ljava/lang/String;)L" + DEFERRED + ";"));
        extra.add(new FieldInsnNode(Opcodes.PUTSTATIC, ENTRY, "EXTRA_ITEMS", "L" + DEFERRED + ";"));
        MethodNode initializer = named(entry, "<clinit>");
        initializer.instructions.insertBefore(initializer.instructions.getLast(), extra);
        refused(nodes, "three proven identity/registration fields");
    }

    @Test
    void refusesRegistrationAccessInOtherwisePreservedOwnedHelpers() {
        Map<String, ClassNode> nodes = fixture(16);
        MethodNode escape = method(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "attachAgain", "()V");
        escape.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, ENTRY, "ITEMS", "L" + DEFERRED + ";"));
        escape.instructions.add(call(Opcodes.INVOKESTATIC, CONTEXT, "get", "()L" + CONTEXT + ";"));
        escape.instructions.add(call(Opcodes.INVOKEVIRTUAL, CONTEXT, "getModEventBus", "()L" + BUS + ";"));
        escape.instructions.add(call(Opcodes.INVOKEVIRTUAL, DEFERRED, "register", "(L" + BUS + ";)V"));
        escape.instructions.add(new InsnNode(Opcodes.RETURN));
        nodes.get(HELPER).methods.add(escape);
        refused(nodes, "outside the entrypoint");
    }

    @Test
    void refusesWrongModIdentityAndOutOfRangeNativeProperty() {
        assertThrows(TransformationException.class, () -> LogicProfileShape.validate(fixture(16), "different_mod", "1.0.0"));
        for (int limit : new int[]{0, 65}) refused(fixture(limit), "literal in 1..64");
    }

    @Test
    void refusesSecondNativeItemSubclassAndNonJava8Classes() {
        Map<String, ClassNode> nodes = fixture(16);
        ClassNode extra = node("example/SecondItem", ITEM);
        nodes.put(extra.name, extra);
        refused(nodes, "Exactly one direct native Item subclass");
        nodes = fixture(16);
        nodes.get(HELPER).version = Opcodes.V17;
        refused(nodes, "Only genuine Java 8 source classfiles");
    }

    @Test
    void refusesOversizedOwnedMethodAndHierarchyCycles() {
        Map<String, ClassNode> nodes = fixture(16);
        MethodNode helper = named(nodes.get(HELPER), "calculate");
        for (int index = 0; index < 4097; index++) helper.instructions.add(new InsnNode(Opcodes.NOP));
        refused(nodes, "Per-method instruction/debug-node limit");
        nodes = fixture(16);
        nodes.get(HELPER).superName = HELPER;
        refused(nodes, "Owned hierarchy cycle");
    }

    private static Map<String, ClassNode> fixture(int limit) {
        Map<String, ClassNode> nodes = new TreeMap<>();
        ClassNode entry = node(ENTRY, "java/lang/Object");
        entry.visibleAnnotations = new ArrayList<>();
        AnnotationNode mod = new AnnotationNode("Lnet/minecraftforge/fml/common/Mod;");
        mod.values = new ArrayList<>(List.of("value", MOD_ID));
        entry.visibleAnnotations.add(mod);
        int constantField = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
        entry.fields.add(new FieldNode(constantField, "MOD_ID", "Ljava/lang/String;", null, MOD_ID));
        entry.fields.add(new FieldNode(constantField, "ITEMS", "L" + DEFERRED + ";", null, null));
        entry.fields.add(new FieldNode(constantField, "TOKEN", "L" + OBJECT + ";", null, null));
        MethodNode constructor = method(Opcodes.ACC_PUBLIC, "<init>", "()V");
        LabelNode line = new LabelNode();
        constructor.instructions.add(line);
        constructor.instructions.add(new LineNumberNode(20, line));
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(call(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V"));
        constructor.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, ENTRY, "ITEMS", "L" + DEFERRED + ";"));
        constructor.instructions.add(call(Opcodes.INVOKESTATIC, CONTEXT, "get", "()L" + CONTEXT + ";"));
        constructor.instructions.add(call(Opcodes.INVOKEVIRTUAL, CONTEXT, "getModEventBus", "()L" + BUS + ";"));
        constructor.instructions.add(call(Opcodes.INVOKEVIRTUAL, DEFERRED, "register", "(L" + BUS + ";)V"));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        entry.methods.add(constructor);
        MethodNode initializer = method(Opcodes.ACC_STATIC, "<clinit>", "()V");
        initializer.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, REGISTRIES, "ITEMS", "L" + REGISTRY + ";"));
        initializer.instructions.add(new LdcInsnNode(MOD_ID));
        initializer.instructions.add(call(Opcodes.INVOKESTATIC, DEFERRED, "create", "(L" + REGISTRY + ";Ljava/lang/String;)L" + DEFERRED + ";"));
        initializer.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, ENTRY, "ITEMS", "L" + DEFERRED + ";"));
        initializer.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, ENTRY, "ITEMS", "L" + DEFERRED + ";"));
        initializer.instructions.add(new LdcInsnNode("parity_token"));
        initializer.instructions.add(new InvokeDynamicInsnNode("get", "()Ljava/util/function/Supplier;", METAFACTORY,
                Type.getMethodType("()Ljava/lang/Object;"),
                new Handle(Opcodes.H_NEWINVOKESPECIAL, OWN_ITEM, "<init>", "()V", false),
                Type.getMethodType("()L" + OWN_ITEM + ";")));
        initializer.instructions.add(call(Opcodes.INVOKEVIRTUAL, DEFERRED, "register", "(Ljava/lang/String;Ljava/util/function/Supplier;)L" + OBJECT + ";"));
        initializer.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, ENTRY, "TOKEN", "L" + OBJECT + ";"));
        initializer.instructions.add(new InsnNode(Opcodes.RETURN));
        entry.methods.add(initializer);

        ClassNode item = node(OWN_ITEM, ITEM);
        MethodNode itemConstructor = method(Opcodes.ACC_PUBLIC, "<init>", "()V");
        itemConstructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        itemConstructor.instructions.add(new TypeInsnNode(Opcodes.NEW, PROPERTIES));
        itemConstructor.instructions.add(new InsnNode(Opcodes.DUP));
        itemConstructor.instructions.add(call(Opcodes.INVOKESPECIAL, PROPERTIES, "<init>", "()V"));
        itemConstructor.instructions.add(new IntInsnNode(Opcodes.BIPUSH, limit));
        // The real official API name is sufficient for this inert structural test;
        // production SRG member-name proof belongs to VerifiedLogicMappings.
        itemConstructor.instructions.add(call(Opcodes.INVOKEVIRTUAL, PROPERTIES, "stacksTo", "(I)L" + PROPERTIES + ";"));
        itemConstructor.instructions.add(call(Opcodes.INVOKESPECIAL, ITEM, "<init>", "(L" + PROPERTIES + ";)V"));
        itemConstructor.instructions.add(new InsnNode(Opcodes.RETURN));
        item.methods.add(itemConstructor);
        ClassNode helper = node(HELPER, "java/lang/Object");
        helper.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "state", "I", null, null));
        MethodNode calculate = method(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calculate", "(I)I");
        calculate.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        calculate.instructions.add(new InsnNode(Opcodes.ICONST_3));
        calculate.instructions.add(new InsnNode(Opcodes.IMUL));
        calculate.instructions.add(new InsnNode(Opcodes.IRETURN));
        helper.methods.add(calculate);
        nodes.put(entry.name, entry);
        nodes.put(item.name, item);
        nodes.put(helper.name, helper);
        return nodes;
    }

    private static ClassNode node(String name, String parent) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
        node.name = name;
        node.superName = parent;
        return node;
    }

    private static MethodNode method(int access, String name, String descriptor) {
        MethodNode method = new MethodNode(access, name, descriptor, null, null);
        method.maxStack = 8;
        method.maxLocals = 2;
        return method;
    }

    private static MethodInsnNode call(int opcode, String owner, String name, String descriptor) {
        return new MethodInsnNode(opcode, owner, name, descriptor, false);
    }

    private static MethodNode named(ClassNode owner, String name) {
        return owner.methods.stream().filter(method -> name.equals(method.name)).findFirst().orElseThrow();
    }

    private static InvokeDynamicInsnNode dynamic(Map<String, ClassNode> nodes) {
        for (AbstractInsnNode instruction : named(nodes.get(ENTRY), "<clinit>").instructions) {
            if (instruction instanceof InvokeDynamicInsnNode dynamic) return dynamic;
        }
        throw new AssertionError("No supplier site");
    }

    private static Map<String, byte[]> bytes(Map<String, ClassNode> nodes) {
        Map<String, byte[]> result = new TreeMap<>();
        nodes.forEach((name, node) -> {
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            result.put(name, writer.toByteArray());
        });
        return result;
    }

    private static void refused(Map<String, ClassNode> nodes, String diagnostic) {
        TransformationException refused = assertThrows(TransformationException.class,
                () -> LogicProfileShape.validate(nodes, MOD_ID, "1.0.0"));
        assertTrue(refused.getMessage().contains(diagnostic), refused.getMessage());
    }
}
