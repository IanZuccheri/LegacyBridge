package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.transformation.TransformationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Inert, independently authored post-remap trees. FoodProfileShape runs AFTER the exact recipe
 * compiler: these tests prove its shape/literal/no-mutation gate, not production member names,
 * valid linkage, food consumption, Forge discovery, or runtime equivalence. No fixture is loaded.
 */
class FoodProfileShapeTest {
    private static final String ENTRY = "example/food/FoodContent";
    private static final String OWN_ITEM = "example/food/RationItem";
    private static final String MOD_ID = "shape_food";
    private static final String ITEM = "net/minecraft/world/item/Item";
    private static final String STACK = "net/minecraft/world/item/ItemStack";
    private static final String PROPS = ITEM + "$Properties";
    private static final String FOOD = "net/minecraft/world/food/FoodProperties";
    private static final String BUILDER = FOOD + "$Builder";
    private static final String DEFERRED = "net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRY_OBJECT = "net/minecraftforge/registries/RegistryObject";
    private static final String REGISTRIES = "net/minecraftforge/registries/ForgeRegistries";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final Handle METAFACTORY = new Handle(Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory", "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            false);

    @Test void acceptsLiteralNativeFoodWithoutMutatingAnyOwnedTreeOrDebugNode() {
        Map<String, ClassNode> nodes = fixture(16, integer(3), new LdcInsnNode(0.5F));
        Map<String, byte[]> before = bytes(nodes);
        ClassNode originalEntry = nodes.get(ENTRY), originalItem = nodes.get(OWN_ITEM);
        MethodNode originalConstructor = constructor(nodes, OWN_ITEM);
        List<AbstractInsnNode> originalInstructions = instructions(originalConstructor);
        // An unmodifiable map must work: the proof projection belongs to the validator, not the input.
        var result = FoodProfileShape.validate(Map.copyOf(nodes), MOD_ID, "1.0.0");

        assertEquals(3, result.nutrition());
        assertEquals(Float.floatToRawIntBits(0.5F), Float.floatToRawIntBits(result.saturationModifier()));
        var plan = result.resources();
        assertEquals(MOD_ID, plan.modId());
        assertEquals("1.0.0", plan.modVersion());
        assertEquals(1, plan.items().size());
        assertEquals("ration", plan.items().get(0).name());
        assertEquals(16, plan.items().get(0).properties().stackSize());
        assertNull(plan.items().get(0).properties().creativeTab());
        assertNull(plan.items().get(0).block());
        assertTrue(plan.blocks().isEmpty());
        assertTrue(plan.sounds().isEmpty());
        assertEquals(17, code(originalConstructor).size(), "The food calls must not be removed from the real body");
        assertSame(originalEntry, nodes.get(ENTRY));
        assertSame(originalItem, nodes.get(OWN_ITEM));
        assertSame(originalConstructor, constructor(nodes, OWN_ITEM));
        assertEquals(originalInstructions.size(), instructions(originalConstructor).size());
        for (int index = 0; index < originalInstructions.size(); index++)
            assertSame(originalInstructions.get(index), instructions(originalConstructor).get(index));
        Map<String, byte[]> after = bytes(nodes);
        before.forEach((name, value) -> assertArrayEquals(value, after.get(name), name));

        Map<String, Object> description = result.foodTree();
        assertEquals(1, description.get("schemaVersion"));
        for (String flag : List.of("meat", "alwaysEat", "fast", "customOverrides")) assertEquals(false, description.get(flag));
        assertEquals(0, description.get("effectCount"));
        assertEquals("NONE", description.get("remainder"));
        assertTrue(description.get("behaviorMigration").toString().contains("TARGET_NATIVE_EAT_AND_ITEM_INTERACT_GAME_EVENTS"));
        assertThrows(UnsupportedOperationException.class, () -> description.put("effectCount", 1));
    }

    @Test void acceptsNutritionAndSaturationEndpointsWithTheirOriginalLiteralBits() {
        for (int nutrition : new int[]{1, 3, 20}) {
            for (float saturation : new float[]{0.0F, -0.0F, Float.MIN_VALUE, 0.5F, 1.0F}) {
                var result = FoodProfileShape.validate(fixture(16, integer(nutrition), new LdcInsnNode(saturation)), MOD_ID, "1.0.0");
                assertEquals(nutrition, result.nutrition());
                assertEquals(Float.floatToRawIntBits(saturation), Float.floatToRawIntBits(result.saturationModifier()));
            }
        }
        assertEquals(0.0F, FoodProfileShape.validate(fixture(1, integer(1), new InsnNode(Opcodes.FCONST_0)), MOD_ID, "1.0.0").saturationModifier());
        assertEquals(1.0F, FoodProfileShape.validate(fixture(64, integer(20), new InsnNode(Opcodes.FCONST_1)), MOD_ID, "1.0.0").saturationModifier());
    }

    @Test void acceptsEquivalentDirectIntegerLiteralEncodingsWithoutChangingTheInput() {
        for (AbstractInsnNode literal : List.of(new InsnNode(Opcodes.ICONST_3), new IntInsnNode(Opcodes.BIPUSH, 3),
                new IntInsnNode(Opcodes.SIPUSH, 3), new LdcInsnNode(3))) {
            var nodes = fixture(16, literal, new LdcInsnNode(0.5F));
            Map<String, byte[]> before = bytes(nodes);
            assertEquals(3, FoodProfileShape.validate(nodes, MOD_ID, "1.0.0").nutrition());
            Map<String, byte[]> after = bytes(nodes);
            before.forEach((name, value) -> assertArrayEquals(value, after.get(name)));
        }
    }

    @Test void rejectsNutritionBelowOneOrAboveTwenty() {
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, 21, Integer.MAX_VALUE})
            refused(fixture(16, integer(value), new LdcInsnNode(0.5F)), "FOOD_LITERAL_RANGE: nutrition");
    }

    @Test void rejectsComputedOrWronglyTypedNutritionInsteadOfEvaluatingIt() {
        for (AbstractInsnNode operand : List.of(new VarInsnNode(Opcodes.ILOAD, 1), new LdcInsnNode(3L), new LdcInsnNode("3")))
            refused(fixture(16, operand, new LdcInsnNode(0.5F)), "Expected literal nutrition integer");
    }

    @Test void rejectsNanInfinityNegativeAndAboveOneSaturation() {
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.01F, 1.0001F, 2.0F})
            refused(fixture(16, integer(3), new LdcInsnNode(value)), "FOOD_LITERAL_RANGE: saturation");
        refused(fixture(16, integer(3), new InsnNode(Opcodes.FCONST_2)), "FOOD_LITERAL_RANGE: saturation");
    }

    @Test void rejectsNonFloatOrComputedSaturationWithoutCoercingNumbers() {
        for (AbstractInsnNode operand : List.of(new LdcInsnNode(0.5D), new LdcInsnNode(0), new VarInsnNode(Opcodes.FLOAD, 1)))
            refused(fixture(16, integer(3), operand), "Expected literal saturation float");
    }

    @Test void rejectsOutOfRangeStackSizeThroughTheExistingRegistrationProof() {
        for (int size : new int[]{0, 65}) refused(fixture(size, integer(3), new LdcInsnNode(0.5F)), "literal in 1..64");
    }

    @Test void rejectsMeatAlwaysEatAndFastInsteadOfTreatingThemAsDefaults() {
        // These are actual target Builder methods; none belongs to the selected default-flag profile.
        for (String flag : List.of("meat", "alwaysEat", "fast")) {
            var nodes = fixture();
            MethodNode ctor = constructor(nodes, OWN_ITEM);
            ctor.instructions.insertBefore(code(ctor).get(13), call(Opcodes.INVOKEVIRTUAL, BUILDER, flag, "()L" + BUILDER + ";"));
            refused(nodes, "FOOD_CONSTRUCTOR_ENVELOPE");
        }
    }

    @Test void rejectsNativeEffectDeclarationIncludingZeroProbability() {
        for (float probability : new float[]{0.0F, 1.0F}) {
            var nodes = fixture();
            MethodNode ctor = constructor(nodes, OWN_ITEM);
            InsnList effects = new InsnList();
            effects.add(new InsnNode(Opcodes.ACONST_NULL));
            effects.add(new LdcInsnNode(probability));
            effects.add(call(Opcodes.INVOKEVIRTUAL, BUILDER, "effect",
                    "(Lnet/minecraft/world/effect/MobEffectInstance;F)L" + BUILDER + ";"));
            ctor.instructions.insertBefore(code(ctor).get(13), effects);
            refused(nodes, "FOOD_CONSTRUCTOR_ENVELOPE");
        }
    }

    @Test void rejectsContainerRemainderInsteadOfDiscardingItFromTheResourcePlan() {
        var nodes = fixture();
        MethodNode ctor = constructor(nodes, OWN_ITEM);
        InsnList remainder = new InsnList();
        remainder.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/item/Items", "BOWL", "L" + ITEM + ";"));
        remainder.add(call(Opcodes.INVOKEVIRTUAL, PROPS, "craftRemainder", "(L" + ITEM + ";)L" + PROPS + ";"));
        ctor.instructions.insertBefore(code(ctor).get(15), remainder);
        refused(nodes, "FOOD_CONSTRUCTOR_ENVELOPE");
    }

    @Test void rejectsCustomUseDurationOverrideEvenIfItReturnsTheNativeDefault() {
        var nodes = fixture();
        MethodNode override = method(Opcodes.ACC_PUBLIC, "getUseDuration", "(L" + STACK + ";)I");
        override.instructions.add(integer(32));
        override.instructions.add(new InsnNode(Opcodes.IRETURN));
        nodes.get(OWN_ITEM).methods.add(override);
        refused(nodes, "FOOD_CUSTOM_BEHAVIOR");
    }

    @Test void rejectsAdditionalOwnHelperMethodEvenWhenItOnlyReturnsALiteral() {
        var nodes = fixture();
        MethodNode helper = method(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "calories", "()I");
        helper.instructions.add(integer(3));
        helper.instructions.add(new InsnNode(Opcodes.IRETURN));
        nodes.get(OWN_ITEM).methods.add(helper);
        refused(nodes, "FOOD_CUSTOM_BEHAVIOR");
    }

    @Test void rejectsThirdHelperClassInsteadOfCopyingUnknownOwnedLogic() {
        var nodes = fixture();
        ClassNode helper = node("example/food/Helper", "java/lang/Object");
        nodes.put(helper.name, helper);
        refused(nodes, "Exactly entrypoint and native-food Item subclass");
    }

    @Test void rejectsItemInitializerEvenIfItsBodyIsEmpty() {
        var nodes = fixture();
        MethodNode initializer = method(Opcodes.ACC_STATIC, "<clinit>", "()V");
        initializer.instructions.add(new InsnNode(Opcodes.RETURN));
        nodes.get(OWN_ITEM).methods.add(initializer);
        refused(nodes, "FOOD_CUSTOM_BEHAVIOR");
    }

    @Test void rejectsItemStateEvenIfNoInstructionReadsTheField() {
        var nodes = fixture();
        nodes.get(OWN_ITEM).fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "bites", "I", null, null));
        refused(nodes, "FOOD_CUSTOM_BEHAVIOR");
    }

    @Test void rejectsAdditionalEntrypointInitializerInstructionsWithoutMutatingTheInput() {
        var nodes = fixture();
        MethodNode initializer = named(nodes.get(ENTRY), "<clinit>");
        InsnList extra = new InsnList();
        extra.add(new InsnNode(Opcodes.ICONST_0));
        extra.add(new InsnNode(Opcodes.POP));
        initializer.instructions.insertBefore(initializer.instructions.getLast(), extra);
        Map<String, byte[]> before = bytes(nodes);
        refused(nodes, "exact finite envelope");
        Map<String, byte[]> after = bytes(nodes);
        before.forEach((name, value) -> assertArrayEquals(value, after.get(name)));
    }

    @Test void rejectsAdditionalConstructorEffectsEvenAfterTheNativeSuperCall() {
        var nodes = fixture();
        MethodNode ctor = constructor(nodes, OWN_ITEM);
        InsnList extra = new InsnList();
        extra.add(call(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J"));
        extra.add(new InsnNode(Opcodes.POP2));
        ctor.instructions.insertBefore(code(ctor).get(16), extra);
        refused(nodes, "FOOD_CONSTRUCTOR_ENVELOPE");
    }

    @Test void rejectsEarlyRegistryReadFromEitherConstructor() {
        for (String owner : List.of(ENTRY, OWN_ITEM)) {
            var nodes = fixture();
            MethodNode ctor = constructor(nodes, owner);
            InsnList read = new InsnList();
            read.add(new FieldInsnNode(Opcodes.GETSTATIC, ENTRY, "RATION", "L" + REGISTRY_OBJECT + ";"));
            read.add(call(Opcodes.INVOKEVIRTUAL, REGISTRY_OBJECT, "get", "()Ljava/lang/Object;"));
            read.add(new InsnNode(Opcodes.POP));
            ctor.instructions.insertBefore(code(ctor).get(code(ctor).size() - 1), read);
            refused(nodes, owner.equals(ENTRY) ? "exact finite envelope" : "FOOD_CONSTRUCTOR_ENVELOPE");
        }
    }

    @Test void rejectsAdditionalStackConsumptionRatherThanExecutingOrDroppingIt() {
        var nodes = fixture();
        MethodNode ctor = constructor(nodes, OWN_ITEM);
        InsnList consume = new InsnList();
        consume.add(new InsnNode(Opcodes.ACONST_NULL));
        consume.add(new InsnNode(Opcodes.ICONST_1));
        consume.add(call(Opcodes.INVOKEVIRTUAL, STACK, "shrink", "(I)V"));
        ctor.instructions.insertBefore(code(ctor).get(16), consume);
        refused(nodes, "FOOD_CONSTRUCTOR_ENVELOPE");
    }

    @Test void rejectsConstructorBranchesEvenIfTheyRejoinAtTheNextInstruction() {
        for (String owner : List.of(ENTRY, OWN_ITEM)) {
            var nodes = fixture();
            MethodNode ctor = constructor(nodes, owner);
            LabelNode next = new LabelNode();
            InsnList branch = new InsnList();
            branch.add(new JumpInsnNode(Opcodes.GOTO, next));
            branch.add(next);
            ctor.instructions.insert(branch);
            refused(nodes, "No constructor or initializer control-flow alternatives");
        }
    }

    @Test void rejectsBothSwitchEncodingsInRegistrationInitialization() {
        for (boolean table : List.of(false, true)) {
            var nodes = fixture();
            MethodNode init = named(nodes.get(ENTRY), "<clinit>");
            LabelNode target = new LabelNode();
            InsnList branch = new InsnList();
            branch.add(new InsnNode(Opcodes.ICONST_0));
            branch.add(table ? new TableSwitchInsnNode(0, 0, target, target)
                    : new LookupSwitchInsnNode(target, new int[]{0}, new LabelNode[]{target}));
            branch.add(target);
            init.instructions.insert(branch);
            refused(nodes, "No constructor or initializer control-flow alternatives");
        }
    }

    @Test void rejectsConstructorExceptionHandlersInsteadOfIgnoringExceptionalEffects() {
        var nodes = fixture();
        MethodNode ctor = constructor(nodes, OWN_ITEM);
        LabelNode start = (LabelNode) ctor.instructions.getFirst();
        LabelNode end = (LabelNode) ctor.instructions.getLast();
        ctor.tryCatchBlocks.add(new TryCatchBlockNode(start, end, end, "java/lang/RuntimeException"));
        refused(nodes, "No exception handlers");
    }

    @Test void rejectsBuilderAllocationOrConstructorSubstitution() {
        for (Consumer<Map<String, ClassNode>> mutation : List.<Consumer<Map<String, ClassNode>>>of(
                nodes -> ((TypeInsnNode) itemCode(nodes).get(6)).desc = OWN_ITEM,
                nodes -> ((MethodInsnNode) itemCode(nodes).get(8)).setOpcode(Opcodes.INVOKESTATIC),
                nodes -> ((MethodInsnNode) itemCode(nodes).get(8)).owner = PROPS)) {
            var nodes = fixture();
            mutation.accept(nodes);
            refused(nodes, "FOOD_PROFILE_SHAPE");
        }
    }

    @Test void rejectsNativeFoodCallOwnerDescriptorOpcodeAndInterfaceMutations() {
        // Member-name equivalence itself is the earlier exact compiler's responsibility.
        for (Consumer<Map<String, ClassNode>> mutation : List.<Consumer<Map<String, ClassNode>>>of(
                nodes -> ((MethodInsnNode) itemCode(nodes).get(10)).owner = OWN_ITEM,
                nodes -> ((MethodInsnNode) itemCode(nodes).get(10)).desc = "(J)L" + BUILDER + ";",
                nodes -> ((MethodInsnNode) itemCode(nodes).get(12)).setOpcode(Opcodes.INVOKESTATIC),
                nodes -> ((MethodInsnNode) itemCode(nodes).get(13)).itf = true,
                nodes -> ((MethodInsnNode) itemCode(nodes).get(14)).owner = BUILDER)) {
            var nodes = fixture();
            mutation.accept(nodes);
            refused(nodes, "Unreviewed native property call");
        }
    }

    @Test void rejectsWrongModBusWithoutChangingItToTheExpectedBus() {
        var nodes = fixture();
        MethodNode ctor = constructor(nodes, ENTRY);
        List<AbstractInsnNode> ops = code(ctor);
        // Stack-valid alternative with the same opcode count: the global Forge bus is not the mod bus.
        ctor.instructions.set(ops.get(3), new FieldInsnNode(Opcodes.GETSTATIC,
                "net/minecraftforge/common/MinecraftForge", "EVENT_BUS", "L" + BUS + ";"));
        ctor.instructions.set(ops.get(4), new InsnNode(Opcodes.NOP));
        refused(nodes, "Unexpected native registration/constructor call");
    }

    @Test void rejectsDifferentRegistryRatherThanTreatingItsGenericErasureAsItems() {
        var nodes = fixture();
        ((FieldInsnNode) code(named(nodes.get(ENTRY), "<clinit>")).get(0)).name = "BLOCKS";
        refused(nodes, "Unexpected registration field instruction");
    }

    @Test void rejectsAlternateSupplierHandleInsteadOfInferringItsFactorySemantics() {
        var nodes = fixture();
        InvokeDynamicInsnNode supplier = (InvokeDynamicInsnNode) code(named(nodes.get(ENTRY), "<clinit>")).get(6);
        supplier.bsmArgs[1] = new Handle(Opcodes.H_INVOKESTATIC, ENTRY, "makeRation", "()L" + OWN_ITEM + ";", false);
        refused(nodes, "Registry factories or alternate supplier implementations");
    }

    @Test void rejectsDifferentModIdentityAndNonLocalRegistryName() {
        var nodes = fixture();
        assertThrows(TransformationException.class, () -> FoodProfileShape.validate(nodes, "another_mod", "1.0.0"));
        ((LdcInsnNode) code(named(nodes.get(ENTRY), "<clinit>")).get(5)).cst = "foreign:ration";
        refused(nodes, "Registry name must be a simple local path");
    }

    @Test void rejectsMismatchedMapIdentityAndProtectedDefinitionNamespaces() {
        var mismatched = fixture();
        ClassNode item = mismatched.remove(OWN_ITEM);
        mismatched.put("example/food/Alias", item);
        refused(mismatched, "Only two final public Java 8 classes");
        var protectedDefinition = fixture();
        item = protectedDefinition.remove(OWN_ITEM);
        item.name = "net/minecraft/world/item/OwnedReplacement";
        protectedDefinition.put(item.name, item);
        refused(protectedDefinition, "Classes must be owned, uniquely named map entries");
    }

    @Test void rejectsItemAnnotationsInsteadOfDiscardingThemDuringProjection() {
        var nodes = fixture();
        nodes.get(OWN_ITEM).visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Ljava/lang/Deprecated;")));
        refused(nodes, "No Item annotations");
    }

    @Test void rejectsNonJava8NonFinalAndInterfaceClassShapes() {
        for (Consumer<Map<String, ClassNode>> mutation : List.<Consumer<Map<String, ClassNode>>>of(
                nodes -> nodes.get(ENTRY).version = Opcodes.V17,
                nodes -> nodes.get(OWN_ITEM).access &= ~Opcodes.ACC_FINAL,
                nodes -> nodes.get(OWN_ITEM).interfaces.add("java/util/function/Supplier"))) {
            var nodes = fixture();
            mutation.accept(nodes);
            refused(nodes, "Only two final public Java 8 classes");
        }
    }

    @Test void rejectsNativeAbstractAndSynchronizedConstructorExecutionContracts() {
        for (int flag : new int[]{Opcodes.ACC_NATIVE, Opcodes.ACC_ABSTRACT, Opcodes.ACC_SYNCHRONIZED}) {
            var nodes = fixture();
            constructor(nodes, OWN_ITEM).access |= flag;
            refused(nodes, "No alternative execution contract");
        }
    }

    @Test void boundsDebugNodesAsWellAsExecutableInstructions() {
        var nodes = fixture();
        MethodNode ctor = constructor(nodes, OWN_ITEM);
        for (int index = 0; index < 256; index++) ctor.instructions.add(new LabelNode());
        refused(nodes, "Bounded closed constructors/initializer only");
    }

    @Test void rejectsMissingOrAdditionalClassRolesWithoutPublishingAProjection() {
        refused(null, "Exactly entrypoint and native-food Item subclass");
        refused(Map.of(), "Exactly entrypoint and native-food Item subclass");
        var nodes = fixture();
        nodes.remove(OWN_ITEM);
        refused(nodes, "Exactly entrypoint and native-food Item subclass");
    }

    private static Map<String, ClassNode> fixture() {
        return fixture(16, integer(3), new LdcInsnNode(0.5F));
    }

    private static Map<String, ClassNode> fixture(int stackSize, AbstractInsnNode nutrition, AbstractInsnNode saturation) {
        ClassNode entry = node(ENTRY, "java/lang/Object");
        AnnotationNode mod = new AnnotationNode("Lnet/minecraftforge/fml/common/Mod;");
        mod.values = new ArrayList<>(List.of("value", MOD_ID));
        entry.visibleAnnotations = new ArrayList<>(List.of(mod));
        int fieldAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
        entry.fields.add(new FieldNode(fieldAccess, "MOD_ID", "Ljava/lang/String;", null, MOD_ID));
        entry.fields.add(new FieldNode(fieldAccess, "ITEMS", "L" + DEFERRED + ";", null, null));
        entry.fields.add(new FieldNode(fieldAccess, "RATION", "L" + REGISTRY_OBJECT + ";", null, null));

        MethodNode ctor = method(Opcodes.ACC_PUBLIC, "<init>", "()V");
        LabelNode entryStart = debugStart(ctor, 20);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.add(call(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V"));
        ctor.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, ENTRY, "ITEMS", "L" + DEFERRED + ";"));
        ctor.instructions.add(call(Opcodes.INVOKESTATIC, CONTEXT, "get", "()L" + CONTEXT + ";"));
        ctor.instructions.add(call(Opcodes.INVOKEVIRTUAL, CONTEXT, "getModEventBus", "()L" + BUS + ";"));
        ctor.instructions.add(call(Opcodes.INVOKEVIRTUAL, DEFERRED, "register", "(L" + BUS + ";)V"));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));
        debugEnd(ctor, entryStart, ENTRY);
        entry.methods.add(ctor);

        MethodNode init = method(Opcodes.ACC_STATIC, "<clinit>", "()V");
        init.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, REGISTRIES, "ITEMS", "L" + REGISTRY + ";"));
        init.instructions.add(new LdcInsnNode(MOD_ID));
        init.instructions.add(call(Opcodes.INVOKESTATIC, DEFERRED, "create", "(L" + REGISTRY + ";Ljava/lang/String;)L" + DEFERRED + ";"));
        init.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, ENTRY, "ITEMS", "L" + DEFERRED + ";"));
        init.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, ENTRY, "ITEMS", "L" + DEFERRED + ";"));
        init.instructions.add(new LdcInsnNode("ration"));
        init.instructions.add(new InvokeDynamicInsnNode("get", "()Ljava/util/function/Supplier;", METAFACTORY,
                Type.getMethodType("()Ljava/lang/Object;"), new Handle(Opcodes.H_NEWINVOKESPECIAL, OWN_ITEM, "<init>", "()V", false),
                Type.getMethodType("()L" + ITEM + ";")));
        init.instructions.add(call(Opcodes.INVOKEVIRTUAL, DEFERRED, "register", "(Ljava/lang/String;Ljava/util/function/Supplier;)L" + REGISTRY_OBJECT + ";"));
        init.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, ENTRY, "RATION", "L" + REGISTRY_OBJECT + ";"));
        init.instructions.add(new InsnNode(Opcodes.RETURN));
        entry.methods.add(init);

        ClassNode item = node(OWN_ITEM, ITEM);
        MethodNode itemCtor = method(Opcodes.ACC_PUBLIC, "<init>", "()V");
        LabelNode itemStart = debugStart(itemCtor, 8);
        itemCtor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        itemCtor.instructions.add(new TypeInsnNode(Opcodes.NEW, PROPS));
        itemCtor.instructions.add(new InsnNode(Opcodes.DUP));
        itemCtor.instructions.add(call(Opcodes.INVOKESPECIAL, PROPS, "<init>", "()V"));
        itemCtor.instructions.add(integer(stackSize));
        // Real official names document the source of the test, not production SRG name proof.
        itemCtor.instructions.add(call(Opcodes.INVOKEVIRTUAL, PROPS, "stacksTo", "(I)L" + PROPS + ";"));
        itemCtor.instructions.add(new TypeInsnNode(Opcodes.NEW, BUILDER));
        itemCtor.instructions.add(new InsnNode(Opcodes.DUP));
        itemCtor.instructions.add(call(Opcodes.INVOKESPECIAL, BUILDER, "<init>", "()V"));
        itemCtor.instructions.add(nutrition);
        itemCtor.instructions.add(call(Opcodes.INVOKEVIRTUAL, BUILDER, "nutrition", "(I)L" + BUILDER + ";"));
        itemCtor.instructions.add(saturation);
        itemCtor.instructions.add(call(Opcodes.INVOKEVIRTUAL, BUILDER, "saturationMod", "(F)L" + BUILDER + ";"));
        itemCtor.instructions.add(call(Opcodes.INVOKEVIRTUAL, BUILDER, "build", "()L" + FOOD + ";"));
        itemCtor.instructions.add(call(Opcodes.INVOKEVIRTUAL, PROPS, "food", "(L" + FOOD + ";)L" + PROPS + ";"));
        itemCtor.instructions.add(call(Opcodes.INVOKESPECIAL, ITEM, "<init>", "(L" + PROPS + ";)V"));
        itemCtor.instructions.add(new InsnNode(Opcodes.RETURN));
        debugEnd(itemCtor, itemStart, OWN_ITEM);
        item.methods.add(itemCtor);
        Map<String, ClassNode> nodes = new TreeMap<>();
        nodes.put(entry.name, entry);
        nodes.put(item.name, item);
        return nodes;
    }

    private static ClassNode node(String name, String parent) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER;
        node.name = name;
        node.superName = parent;
        node.sourceFile = name.substring(name.lastIndexOf('/') + 1) + ".java";
        return node;
    }

    private static MethodNode method(int access, String name, String descriptor) {
        MethodNode method = new MethodNode(access, name, descriptor, null, null);
        method.maxStack = 12;
        method.maxLocals = 2;
        return method;
    }

    private static LabelNode debugStart(MethodNode method, int line) {
        LabelNode start = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new LineNumberNode(line, start));
        return start;
    }

    private static void debugEnd(MethodNode method, LabelNode start, String owner) {
        LabelNode end = new LabelNode();
        method.instructions.add(end);
        method.localVariables.add(new LocalVariableNode("this", "L" + owner + ";", null, start, end, 0));
    }

    private static AbstractInsnNode integer(int value) {
        if (value >= -1 && value <= 5) return new InsnNode(Opcodes.ICONST_0 + value);
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) return new IntInsnNode(Opcodes.BIPUSH, value);
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) return new IntInsnNode(Opcodes.SIPUSH, value);
        return new LdcInsnNode(value);
    }

    private static MethodInsnNode call(int opcode, String owner, String name, String descriptor) {
        return new MethodInsnNode(opcode, owner, name, descriptor, false);
    }

    private static MethodNode named(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static MethodNode constructor(Map<String, ClassNode> nodes, String owner) { return named(nodes.get(owner), "<init>"); }
    private static List<AbstractInsnNode> itemCode(Map<String, ClassNode> nodes) { return code(constructor(nodes, OWN_ITEM)); }
    private static List<AbstractInsnNode> code(MethodNode method) {
        return instructions(method).stream().filter(node -> node.getOpcode() >= 0).toList();
    }
    private static List<AbstractInsnNode> instructions(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) result.add(instruction);
        return result;
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
        TransformationException error = assertThrows(TransformationException.class,
                () -> FoodProfileShape.validate(nodes, MOD_ID, "1.0.0"));
        assertTrue(error.getMessage().contains(diagnostic), error.getMessage());
    }
}
