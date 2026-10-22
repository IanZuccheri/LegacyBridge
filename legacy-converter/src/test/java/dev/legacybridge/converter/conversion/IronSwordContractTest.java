package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationLimits;
import dev.legacybridge.converter.operation.OperationStoppedException;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Inert construction/registration trees only; recognizing them never approves inherited behavior. */
class IronSwordContractTest {
    private static final String ENTRY = "example/IronSwordContent", MOD = "iron_fixture";
    private static final String DEFERRED = "net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRIES = "net/minecraftforge/registries/ForgeRegistries";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final Handle METAFACTORY = new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);

    @Test void recognizesBothNamespacesWithoutClaimingSemanticEquivalenceOrMutatingInput() {
        var source = fixture(false); var target = fixture(true);
        var sourceBefore = bytes(source); var targetBefore = bytes(target);
        var proof = IronSwordContract.inspect(Map.of(ENTRY, source));
        assertEquals(proof, IronSwordContract.inspect(Map.of(ENTRY, target)));
        assertEquals(ENTRY, proof.entry()); assertEquals(MOD, proof.modId());
        assertEquals("bridge_sword", proof.registryName()); assertEquals("createSword", proof.factory());
        var tree = proof.toTree();
        assertEquals(true, tree.get("constructionRecognized"));
        assertEquals("UNSUPPORTED", tree.get("semanticMigration"));
        assertEquals(false, tree.get("inheritedBehaviorEquivalent"));
        assertEquals(false, tree.get("conversionAllowed")); assertEquals(false, tree.get("publicationAllowed"));
        assertEquals(false, tree.get("classesExecuted")); assertEquals(false, tree.get("bytecodeModified"));
        assertEquals("NOT_RUN", tree.get("runtimeValidation"));
        assertEquals("REQUIRE_SEPARATE_VERIFICATION", tree.get("externalDeclarations"));
        assertArrayEquals(sourceBefore, bytes(source)); assertArrayEquals(targetBefore, bytes(target));
        assertFalse(tree.containsKey("candidate")); assertFalse(tree.containsKey("output"));
    }

    @Test void constructorInputsPreserveFloatBitsWithoutInventingNativeObservations() {
        var inputs = (Map<?,?>)IronSwordContract.inspect(Map.of(ENTRY, fixture(false))).toTree().get("constructorInputs");
        assertEquals("IRON", inputs.get("tier")); assertEquals(3, inputs.get("attackLiteral"));
        assertEquals(-2.4F, inputs.get("speedLiteral")); assertEquals(Float.floatToIntBits(-2.4F), inputs.get("speedFloatBits"));
        assertFalse(inputs.containsKey("durability")); assertFalse(inputs.containsKey("attackDamage"));
        assertFalse(inputs.containsKey("repairValidated"));
        assertThrows(UnsupportedOperationException.class, inputs::clear);
    }

    @Test void metadataValidationIsIdentityOnlyInEitherNamespace() {
        for (boolean target : new boolean[]{false, true}) {
            var nodes = Map.of(ENTRY, fixture(target));
            assertEquals(IronSwordContract.inspect(nodes), IronSwordContract.validate(nodes, MOD, "1.2.3-beta+4"));
            assertThrows(TransformationException.class, () -> IronSwordContract.validate(nodes, "other", "1"));
            assertThrows(TransformationException.class, () -> IronSwordContract.validate(nodes, MOD, "../unsafe"));
            assertThrows(TransformationException.class, () -> IronSwordContract.validate(nodes, MOD, null));
        }
    }

    @Test void ownedRolesCanBeRenamedTogetherWithoutUsingFixtureFingerprints() {
        for (boolean target : new boolean[]{false, true}) {
            var remapper = new Remapper() {
                @Override public String map(String name) { return name.equals(ENTRY) ? "another/Items" : name; }
                @Override public String mapFieldName(String owner, String name, String desc) {
                    return owner.equals(ENTRY) ? "renamed_" + name : name;
                }
                @Override public String mapMethodName(String owner, String name, String desc) {
                    return owner.equals(ENTRY) && name.equals("createSword") ? "swordFactory" : name;
                }
            };
            var renamed = new ClassNode(); fixture(target).accept(new ClassRemapper(renamed, remapper));
            var proof = IronSwordContract.inspect(Map.of(renamed.name, renamed));
            assertEquals("another/Items", proof.entry()); assertEquals("swordFactory", proof.factory());
            assertEquals("bridge_sword", proof.registryName());
        }
    }

    @Test void debugMetadataAndOrderingAreUntouched() {
        for (boolean target : new boolean[]{false, true}) {
            var n = fixture(target); n.sourceFile = "Authored.java"; n.sourceDebug = "SMAP\nAuthored.java\n";
            for (var m : n.methods) {
                var start = new LabelNode(); var end = new LabelNode();
                m.instructions.insert(start); m.instructions.insert(start, new LineNumberNode(13, start)); m.instructions.add(end);
                m.localVariables.add(new LocalVariableNode("debug", "Ljava/lang/Object;", null, start, end, 0));
            }
            Collections.reverse(n.fields); Collections.reverse(n.methods);
            var before = bytes(n); IronSwordContract.inspect(Map.of(ENTRY, n)); assertArrayEquals(before, bytes(n));
        }
    }

    @Test void reviewedOptionalGenericsAndInnerMetadataAreAllowed() {
        for (boolean target : new boolean[]{false, true}) {
            var n = fixture(target); var a = new Api(target);
            field(n, "ITEMS").signature = "L" + DEFERRED + "<L" + a.item + ";>;";
            field(n, "SWORD").signature = "L" + a.reference + "<L" + a.item + ";>;";
            n.innerClasses.add(new InnerClassNode(a.props, a.item, "Properties", ACC_PUBLIC | ACC_STATIC));
            n.innerClasses.add(new InnerClassNode("java/lang/invoke/MethodHandles$Lookup", "java/lang/invoke/MethodHandles", "Lookup", ACC_PUBLIC | ACC_STATIC | ACC_FINAL));
            IronSwordContract.inspect(Map.of(ENTRY, n));
        }
    }

    @Test void cancellationPropagatesWithoutClassExecution() {
        var control = new OperationControl(OperationLimits.defaults(), ignored -> { }); assertTrue(control.requestCancel());
        try (var ignored = control.enter()) {
            assertThrows(OperationStoppedException.class, () -> IronSwordContract.inspect(Map.of(ENTRY, fixture(false))));
        }
    }

    @Test void graphBoundsAndPlatformOwnershipAreMandatory() {
        assertThrows(TransformationException.class, () -> IronSwordContract.inspect(null));
        assertThrows(TransformationException.class, () -> IronSwordContract.inspect(Map.of()));
        assertThrows(TransformationException.class, () -> IronSwordContract.inspect(Map.of("wrong/key", fixture(false))));
        assertThrows(TransformationException.class, () -> IronSwordContract.inspect(Map.of(ENTRY, fixture(false), "extra/Helper", new ClassNode())));
        var bad = fixture(false); bad.name = "net/minecraft/ForgedMod";
        assertThrows(TransformationException.class, () -> IronSwordContract.inspect(Map.of(bad.name, bad)));
    }

    @Test void frameLimitsApplyIndependentlyToEveryMethodInEitherNamespace() {
        for (boolean target : new boolean[]{false, true}) for (String name : List.of("<init>", "<clinit>", "createSword")) {
            for (int declared : new int[]{-1, 65, 65535, Integer.MAX_VALUE}) {
                var stack = fixture(target); method(stack, name).maxStack = declared;
                assertThrows(TransformationException.class, () -> IronSwordContract.inspect(Map.of(ENTRY, stack)), name + " stack " + declared);
                var locals = fixture(target); method(locals, name).maxLocals = declared;
                assertThrows(TransformationException.class, () -> IronSwordContract.inspect(Map.of(ENTRY, locals)), name + " locals " + declared);
            }
            var boundary = fixture(target); method(boundary, name).maxStack = 64; method(boundary, name).maxLocals = 64;
            IronSwordContract.inspect(Map.of(ENTRY, boundary));
        }
    }

    @Test void inspectionEvidenceRejectsMutableNumberObjectsWithoutEnvironmentData() {
        var error = new dev.legacybridge.converter.service.EngineError("UNSUPPORTED", "No semantic recipe", "Keep original unchanged");
        assertThrows(IllegalArgumentException.class, () -> new dev.legacybridge.converter.service.IronSwordInspection(
            "0".repeat(64), false, Optional.empty(), List.of(error),
            Map.of("nested", List.of(Map.of("value", new java.util.concurrent.atomic.AtomicInteger(1))))));
        assertThrows(IllegalArgumentException.class, () -> new dev.legacybridge.converter.service.IronSwordInspection(
            "0".repeat(64), false, Optional.empty(), List.of(error),
            Map.of("nested", new java.util.concurrent.atomic.AtomicLong(1))));
    }

    @TestFactory Stream<DynamicTest> unknownShapesAndEffectsAreRefusedInBothNamespaces() {
        var changes = new LinkedHashMap<String,Consumer<ClassNode>>();
        changes.put("nonfinal class", n -> n.access &= ~ACC_FINAL);
        changes.put("abstract class", n -> n.access |= ACC_ABSTRACT);
        changes.put("nonpublic class", n -> n.access &= ~ACC_PUBLIC);
        changes.put("wrong version", n -> n.version = V17);
        changes.put("sword subclass", n -> n.superName = "net/minecraft/item/SwordItem");
        changes.put("interface", n -> n.interfaces.add("unknown/Hook"));
        changes.put("class signature", n -> n.signature = "Lunknown/Base;");
        changes.put("outer class", n -> n.outerClass = "unknown/Outer");
        changes.put("nest host", n -> n.nestHostClass = "unknown/Host");
        changes.put("permitted subclass", n -> n.permittedSubclasses = new ArrayList<>(List.of("unknown/Child")));
        changes.put("record metadata", n -> n.recordComponents = new ArrayList<>(List.of(new RecordComponentNode("value", "I", null))));
        changes.put("unknown inner metadata", n -> n.innerClasses.add(new InnerClassNode("unknown/Inner", "unknown", "Inner", ACC_PUBLIC)));
        changes.put("null inner name", n -> n.innerClasses.add(new InnerClassNode(null, null, null, 0)));
        changes.put("missing annotation", n -> n.visibleAnnotations.clear());
        changes.put("extra annotation", n -> n.visibleAnnotations.add(new AnnotationNode("Lunknown/Hook;")));
        changes.put("invisible annotation", n -> n.invisibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        changes.put("annotation identity drift", n -> n.visibleAnnotations.get(0).values = new ArrayList<>(List.of("value", "other")));
        changes.put("extra annotation value", n -> n.visibleAnnotations.get(0).values.addAll(List.of("other", "value")));
        changes.put("extra field", n -> n.fields.add(new FieldNode(ACC_STATIC, "counter", "I", null, null)));
        changes.put("missing field", n -> n.fields.remove(field(n, "SWORD")));
        changes.put("duplicate role name", n -> field(n, "SWORD").name = "ITEMS");
        changes.put("mutable registry", n -> field(n, "ITEMS").access &= ~ACC_FINAL);
        changes.put("instance registry", n -> field(n, "ITEMS").access &= ~ACC_STATIC);
        changes.put("private registry", n -> field(n, "ITEMS").access = ACC_PRIVATE | ACC_STATIC | ACC_FINAL);
        changes.put("constant registry", n -> field(n, "ITEMS").value = 1);
        changes.put("constant sword", n -> field(n, "SWORD").value = 1);
        changes.put("identity not constant", n -> field(n, "MOD_ID").value = null);
        changes.put("invalid identity", n -> field(n, "MOD_ID").value = "UPPER" );
        changes.put("generic unknown tier", n -> field(n, "SWORD").signature = "Lunknown/Registry<Lunknown/Tier;>;" );
        changes.put("identity generic metadata", n -> field(n, "MOD_ID").signature = "Ljava/lang/String;" );
        changes.put("field annotation", n -> field(n, "SWORD").visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        changes.put("unknown helper", n -> add(n, ACC_PRIVATE | ACC_STATIC, "extra", "()V").instructions.add(new InsnNode(RETURN)));
        changes.put("missing factory", n -> n.methods.remove(factory(n)));
        changes.put("public factory", n -> factory(n).access = ACC_PUBLIC | ACC_STATIC);
        changes.put("instance factory", n -> factory(n).access = ACC_PRIVATE);
        changes.put("synchronized factory", n -> factory(n).access |= ACC_SYNCHRONIZED);
        changes.put("native factory", n -> factory(n).access |= ACC_NATIVE);
        changes.put("special factory name", n -> factory(n).name = "<clinit>");
        changes.put("unknown factory descriptor", n -> factory(n).desc = "()Ljava/lang/Object;");
        changes.put("generic factory", n -> factory(n).signature = "()Ljava/lang/Object;");
        changes.put("exception declaration", n -> factory(n).exceptions.add("java/lang/Exception"));
        changes.put("exception handler", n -> factory(n).tryCatchBlocks.add(new TryCatchBlockNode(new LabelNode(), new LabelNode(), new LabelNode(), "java/lang/Exception")));
        changes.put("method annotation", n -> factory(n).visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        changes.put("method parameters", n -> factory(n).parameters = new ArrayList<>(List.of(new ParameterNode("unknown", 0))));
        changes.put("instruction annotation", n -> real(factory(n)).get(0).visibleTypeAnnotations = new ArrayList<>(List.of(new TypeAnnotationNode(0, null, "Lunknown/Hook;"))));
        changes.put("wrong new type", n -> ((TypeInsnNode)real(factory(n)).get(0)).desc = "unknown/Sword");
        changes.put("other tier", n -> ((FieldInsnNode)real(factory(n)).get(2)).name = "DIAMOND");
        changes.put("custom tier owner", n -> ((FieldInsnNode)real(factory(n)).get(2)).owner = "unknown/CustomTier");
        changes.put("tier instance field", n -> ((FieldInsnNode)real(factory(n)).get(2)).setOpcode(GETFIELD));
        changes.put("tier wrong descriptor", n -> ((FieldInsnNode)real(factory(n)).get(2)).desc = "Ljava/lang/Object;");
        changes.put("different damage", n -> replace(factory(n), 3, new InsnNode(ICONST_4)));
        changes.put("alternate unreviewed damage encoding", n -> replace(factory(n), 3, new IntInsnNode(BIPUSH, 3)));
        changes.put("different speed", n -> ((LdcInsnNode)real(factory(n)).get(4)).cst = -2.3F);
        changes.put("nearby speed", n -> ((LdcInsnNode)real(factory(n)).get(4)).cst = Math.nextUp(-2.4F));
        changes.put("double speed", n -> ((LdcInsnNode)real(factory(n)).get(4)).cst = -2.4D);
        changes.put("NaN speed", n -> ((LdcInsnNode)real(factory(n)).get(4)).cst = Float.NaN);
        changes.put("infinite speed", n -> ((LdcInsnNode)real(factory(n)).get(4)).cst = Float.POSITIVE_INFINITY);
        changes.put("negative zero speed", n -> ((LdcInsnNode)real(factory(n)).get(4)).cst = -0.0F);
        changes.put("foreign properties", n -> ((TypeInsnNode)real(factory(n)).get(5)).desc = "unknown/Properties");
        changes.put("properties constructor opcode", n -> ((MethodInsnNode)real(factory(n)).get(7)).setOpcode(INVOKESTATIC));
        changes.put("foreign constructor", n -> ((MethodInsnNode)real(factory(n)).get(8)).owner = "unknown/Sword");
        changes.put("constructor opcode", n -> ((MethodInsnNode)real(factory(n)).get(8)).setOpcode(INVOKEVIRTUAL));
        changes.put("constructor interface bit", n -> ((MethodInsnNode)real(factory(n)).get(8)).itf = true);
        changes.put("constructor descriptor", n -> ((MethodInsnNode)real(factory(n)).get(8)).desc = "(Ljava/lang/Object;)V");
        changes.put("custom property call", n -> factory(n).instructions.insertBefore(real(factory(n)).get(8), new MethodInsnNode(INVOKEVIRTUAL, "unknown/Properties", "durability", "(I)Lunknown/Properties;", false)));
        changes.put("extra factory side effect", n -> factory(n).instructions.insert(new InsnNode(NOP)));
        changes.put("unknown branch", n -> factory(n).instructions.insert(new JumpInsnNode(GOTO, new LabelNode())));
        changes.put("different registry type", n -> ((FieldInsnNode)real(method(n, "<clinit>")).get(0)).name = "BLOCKS");
        changes.put("different registration id", n -> ((LdcInsnNode)real(method(n, "<clinit>")).get(1)).cst = "other");
        changes.put("foreign registry write", n -> ((FieldInsnNode)real(method(n, "<clinit>")).get(3)).owner = "unknown/Entry");
        changes.put("invalid registry path", n -> ((LdcInsnNode)real(method(n, "<clinit>")).get(5)).cst = "../sword");
        changes.put("nonliteral registry name", n -> ((LdcInsnNode)real(method(n, "<clinit>")).get(5)).cst = 1);
        changes.put("early lookup", n -> method(n, "<clinit>").instructions.insert(new MethodInsnNode(INVOKEVIRTUAL, "unknown/RegistryObject", "get", "()Ljava/lang/Object;", false)));
        changes.put("foreign mod bus", n -> ((MethodInsnNode)real(method(n, "<init>")).get(4)).name = "getForgeEventBus");
        changes.put("registry attach wrong opcode", n -> ((MethodInsnNode)real(method(n, "<init>")).get(5)).setOpcode(INVOKESTATIC));
        changes.put("extra constructor effect", n -> method(n, "<init>").instructions.insert(new InsnNode(NOP)));
        changes.put("captured supplier", n -> supplier(n).desc = "(Ljava/lang/Object;)Ljava/util/function/Supplier;");
        changes.put("alternate SAM", n -> supplier(n).name = "apply");
        changes.put("alternate metafactory", n -> supplier(n).bsm = new Handle(H_INVOKESTATIC, METAFACTORY.getOwner(), "altMetafactory", METAFACTORY.getDesc(), false));
        changes.put("foreign factory handle", n -> replaceHandle(n, "unknown/Factory", "createSword", H_INVOKESTATIC, false));
        changes.put("wrong factory handle", n -> replaceHandle(n, ENTRY, "otherFactory", H_INVOKESTATIC, false));
        changes.put("nonstatic factory handle", n -> replaceHandle(n, ENTRY, "createSword", H_INVOKEVIRTUAL, false));
        changes.put("interface factory handle", n -> replaceHandle(n, ENTRY, "createSword", H_INVOKESTATIC, true));
        changes.put("unknown SAM descriptor", n -> supplier(n).bsmArgs[0] = Type.getMethodType("()Ljava/lang/String;"));
        changes.put("unknown instantiated descriptor", n -> supplier(n).bsmArgs[2] = Type.getMethodType("()Ljava/lang/Object;"));
        return changes.entrySet().stream().flatMap(e -> Stream.of(false, true).map(target -> DynamicTest.dynamicTest(
            (target ? "target " : "source ") + e.getKey(), () -> {
                var n = fixture(target); e.getValue().accept(n);
                var error = assertThrows(TransformationException.class, () -> IronSwordContract.inspect(Map.of(ENTRY, n)));
                assertTrue(error.getMessage().startsWith("IRON_SWORD_UNSUPPORTED:"), error.getMessage());
            })));
    }

    private static ClassNode fixture(boolean target) {
        var a = new Api(target); var n = new ClassNode(); n.version = V1_8;
        n.name = ENTRY; n.superName = "java/lang/Object"; n.access = ACC_PUBLIC | ACC_FINAL | ACC_SUPER;
        var annotation = new AnnotationNode("Lnet/minecraftforge/fml/common/Mod;"); annotation.values = new ArrayList<>(List.of("value", MOD));
        n.visibleAnnotations = new ArrayList<>(List.of(annotation));
        n.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "MOD_ID", "Ljava/lang/String;", null, MOD));
        n.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "ITEMS", "L" + DEFERRED + ";", null, null));
        n.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "SWORD", "L" + a.reference + ";", null, null));
        var init = add(n, ACC_STATIC, "<clinit>", "()V");
        init.instructions.add(new FieldInsnNode(GETSTATIC, REGISTRIES, "ITEMS", "L" + REGISTRY + ";")); init.instructions.add(new LdcInsnNode(MOD));
        call(init, INVOKESTATIC, DEFERRED, "create", "(L" + REGISTRY + ";Ljava/lang/String;)L" + DEFERRED + ";");
        init.instructions.add(new FieldInsnNode(PUTSTATIC, ENTRY, "ITEMS", "L" + DEFERRED + ";"));
        init.instructions.add(new FieldInsnNode(GETSTATIC, ENTRY, "ITEMS", "L" + DEFERRED + ";")); init.instructions.add(new LdcInsnNode("bridge_sword"));
        init.instructions.add(new InvokeDynamicInsnNode("get", "()Ljava/util/function/Supplier;", METAFACTORY, Type.getMethodType("()Ljava/lang/Object;"),
            new Handle(H_INVOKESTATIC, ENTRY, "createSword", "()L" + a.item + ";", false), Type.getMethodType("()L" + a.item + ";")));
        call(init, INVOKEVIRTUAL, DEFERRED, "register", "(Ljava/lang/String;Ljava/util/function/Supplier;)L" + a.reference + ";");
        init.instructions.add(new FieldInsnNode(PUTSTATIC, ENTRY, "SWORD", "L" + a.reference + ";")); init.instructions.add(new InsnNode(RETURN));
        var ctor = add(n, ACC_PUBLIC, "<init>", "()V"); ctor.instructions.add(new VarInsnNode(ALOAD, 0));
        call(ctor, INVOKESPECIAL, "java/lang/Object", "<init>", "()V"); ctor.instructions.add(new FieldInsnNode(GETSTATIC, ENTRY, "ITEMS", "L" + DEFERRED + ";"));
        call(ctor, INVOKESTATIC, CONTEXT, "get", "()L" + CONTEXT + ";"); call(ctor, INVOKEVIRTUAL, CONTEXT, "getModEventBus", "()L" + BUS + ";");
        call(ctor, INVOKEVIRTUAL, DEFERRED, "register", "(L" + BUS + ";)V"); ctor.instructions.add(new InsnNode(RETURN));
        var factory = add(n, ACC_PRIVATE | ACC_STATIC, "createSword", "()L" + a.item + ";");
        factory.instructions.add(new TypeInsnNode(NEW, a.sword)); factory.instructions.add(new InsnNode(DUP));
        factory.instructions.add(new FieldInsnNode(GETSTATIC, a.tiers, "IRON", "L" + a.tiers + ";")); factory.instructions.add(new InsnNode(ICONST_3));
        factory.instructions.add(new LdcInsnNode(-2.4F)); factory.instructions.add(new TypeInsnNode(NEW, a.props)); factory.instructions.add(new InsnNode(DUP));
        call(factory, INVOKESPECIAL, a.props, "<init>", "()V"); call(factory, INVOKESPECIAL, a.sword, "<init>", "(L" + a.tier + ";IFL" + a.props + ";)V");
        factory.instructions.add(new InsnNode(ARETURN)); return n;
    }

    private static final class Api {
        final String item, sword, tier, tiers, props, reference;
        Api(boolean target) {
            item = target ? "net/minecraft/world/item/Item" : "net/minecraft/item/Item";
            sword = target ? "net/minecraft/world/item/SwordItem" : "net/minecraft/item/SwordItem";
            tier = target ? "net/minecraft/world/item/Tier" : "net/minecraft/item/IItemTier";
            tiers = target ? "net/minecraft/world/item/Tiers" : "net/minecraft/item/ItemTier";
            props = item + "$Properties"; reference = target ? "net/minecraftforge/registries/RegistryObject" : "net/minecraftforge/fml/RegistryObject";
        }
    }
    private static MethodNode add(ClassNode n, int access, String name, String desc) { var m = new MethodNode(access, name, desc, null, null); m.maxLocals = 1; m.maxStack = 10; n.methods.add(m); return m; }
    private static void call(MethodNode n, int opcode, String owner, String name, String desc) { n.instructions.add(new MethodInsnNode(opcode, owner, name, desc, false)); }
    private static MethodNode method(ClassNode n, String name) { return n.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static MethodNode factory(ClassNode n) { return method(n, "createSword"); }
    private static FieldNode field(ClassNode n, String name) { return n.fields.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow(); }
    private static List<AbstractInsnNode> real(MethodNode n) { return ClientConfigContract.code(n); }
    private static void replace(MethodNode n, int index, AbstractInsnNode replacement) { n.instructions.set(real(n).get(index), replacement); }
    private static InvokeDynamicInsnNode supplier(ClassNode n) { return (InvokeDynamicInsnNode)real(method(n, "<clinit>")).get(6); }
    private static void replaceHandle(ClassNode n, String owner, String name, int kind, boolean itf) {
        supplier(n).bsmArgs[1] = new Handle(kind, owner, name, factory(n).desc, itf);
    }
    private static byte[] bytes(ClassNode n) { var writer = new ClassWriter(0); n.accept(writer); return writer.toByteArray(); }
}
