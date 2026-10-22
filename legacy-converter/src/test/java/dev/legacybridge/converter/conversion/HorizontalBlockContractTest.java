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

/** Inert trees only: no fixture/game classes are defined or executed by these structural tests. */
class HorizontalBlockContractTest {
    private static final String ENTRY = "example/HorizontalContent";
    private static final String BLOCK = "example/HorizontalBlock";
    private static final String MOD = "horizontal_fixture";
    private static final String DEFERRED = "net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRIES = "net/minecraftforge/registries/ForgeRegistries";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final Handle METAFACTORY = new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);

    @Test void provesBothVersionsWithoutExecutingOrMutatingOriginalTrees() {
        var source = fixture(false); var target = fixture(true);
        var sourceBefore = bytes(source); var targetBefore = bytes(target);
        var proof = HorizontalBlockContract.inspect(source);
        assertEquals(proof, HorizontalBlockContract.inspect(target));
        assertEquals(ENTRY, proof.entry()); assertEquals(BLOCK, proof.block()); assertEquals("FACING", proof.propertyField());
        assertEquals(MOD, proof.plainProof().modId()); assertEquals("bridge_block", proof.plainProof().registryName());
        assertEquals(false, proof.toTree().get("classesExecuted")); assertEquals("NOT_RUN", proof.toTree().get("runtimeValidation"));
        assertBytes(sourceBefore, source); assertBytes(targetBefore, target);
        var content = HorizontalBlockContract.validate(target, MOD, "1.0.0");
        assertEquals(1, content.blocks().size()); assertEquals(1, content.items().size());
        assertEquals(MOD + ":bridge_block", content.items().get(0).block());
        assertEquals(2.0F, content.blocks().get(0).properties().hardness());
        assertEquals(6.0F, content.blocks().get(0).properties().resistance());
    }

    @Test void plainProjectionIsCheckedDeepCopiedAdmissionOnlyAndNeverMutatesOriginal() {
        for (boolean target : new boolean[]{false, true}) {
            var nodes = fixture(target); var before = bytes(nodes); var proof = HorizontalBlockContract.inspect(nodes);
            var view = HorizontalBlockContract.plainProjection(nodes);
            assertEquals(Set.of(ENTRY), view.keySet());
            assertEquals(proof.plainProof(), PlainBlockContract.inspect(view));
            var factory = method(view.get(ENTRY), "createBlock");
            assertEquals(nativeBlock(target), ((TypeInsnNode) real(factory).get(0)).desc);
            assertEquals(nativeBlock(target), ((MethodInsnNode) real(factory).get(real(factory).size() - 2)).owner);
            assertEquals(BLOCK, ((TypeInsnNode) real(method(nodes.get(ENTRY), "createBlock")).get(0)).desc);
            factory.instructions.clear();
            assertBytes(before, nodes);
            assertEquals(proof, HorizontalBlockContract.inspect(nodes));
        }
        var malformed = fixture(false);
        method(malformed.get(BLOCK), "func_196258_a").instructions.insert(new InsnNode(NOP));
        assertThrows(TransformationException.class, () -> HorizontalBlockContract.plainProjection(malformed));
    }

    @Test void requiresTargetResourceViewAndExactMetadata() {
        assertThrows(TransformationException.class, () -> HorizontalBlockContract.validate(fixture(false), MOD, "1.0.0"));
        assertThrows(TransformationException.class, () -> HorizontalBlockContract.validate(fixture(true), "other", "1.0.0"));
        assertThrows(TransformationException.class, () -> HorizontalBlockContract.validate(fixture(true), MOD, "../other"));
    }

    @Test void consistentOwnedClassAndFieldRenamingIsNotFixtureFingerprinting() {
        for (boolean target : new boolean[]{false, true}) {
            var nodes = fixture(target); var renamed = new LinkedHashMap<String,ClassNode>();
            var remapper = new Remapper() {
                @Override public String map(String name) {
                    return name.equals(ENTRY) ? "another/Registration" : name.equals(BLOCK) ? "another/FacingCube" : name;
                }
                @Override public String mapFieldName(String owner, String name, String descriptor) {
                    if (owner.equals(BLOCK) && name.equals("FACING")) return "ORIENTATION";
                    return owner.equals(ENTRY) ? "registration_" + name : name;
                }
            };
            for (var original : nodes.values()) {
                var n = new ClassNode(); original.accept(new ClassRemapper(n, remapper)); renamed.put(n.name, n);
            }
            var proof = HorizontalBlockContract.inspect(renamed);
            assertEquals("another/Registration", proof.entry());
            assertEquals("another/FacingCube", proof.block()); assertEquals("ORIENTATION", proof.propertyField());
            assertEquals("bridge_block", proof.plainProof().registryName());
            assertEquals(MOD, proof.plainProof().modId());
        }
    }

    @Test void debugMetadataAndMethodOrderingRemainUnmodified() {
        for (boolean target : new boolean[]{false, true}) {
            var nodes = fixture(target);
            for (var n : nodes.values()) {
                n.sourceFile = "Authored.java"; n.sourceDebug = "SMAP\nAuthored.java\n";
                for (var m : n.methods) {
                    var label = new LabelNode(); m.instructions.insert(label);
                    m.instructions.insert(label, new LineNumberNode(77, label));
                }
                Collections.reverse(n.methods);
            }
            var before = bytes(nodes); HorizontalBlockContract.inspect(nodes); assertBytes(before, nodes);
        }
    }

    @Test void acceptsOnlyExactBuilderGenericMetadataAndKnownInnerClassMetadata() {
        for (boolean target : new boolean[]{false, true}) {
            var nodes = fixture(target); var block = nodes.get(BLOCK);
            String container = container(target), builder = container + "$Builder";
            method(block, target ? "m_7926_" : "func_206840_a").signature =
                "(L" + builder + "<L" + nativeBlock(target) + ";L" + state(target) + ";>;)V";
            block.innerClasses.add(new InnerClassNode(builder, container, "Builder", ACC_PUBLIC | ACC_STATIC));
            String properties = properties(target);
            block.innerClasses.add(new InnerClassNode(properties, properties.substring(0, properties.indexOf('$')), "Properties", ACC_PUBLIC | ACC_STATIC));
            HorizontalBlockContract.inspect(nodes);
        }
    }

    @Test void cancellationPropagatesBeforeAnyProjection() {
        var control = new OperationControl(OperationLimits.defaults(), event -> { });
        assertTrue(control.requestCancel());
        try (var ignored = control.enter()) {
            var failure = assertThrows(OperationStoppedException.class, () -> HorizontalBlockContract.inspect(fixture(false)));
            assertTrue(failure.getMessage().contains("cancelled"));
        }
    }

    @Test void requiresExactlyOwnedTwoClassGraph() {
        assertThrows(TransformationException.class, () -> HorizontalBlockContract.inspect(null));
        assertThrows(TransformationException.class, () -> HorizontalBlockContract.inspect(Map.of()));
        var nodes = fixture(false);
        assertThrows(TransformationException.class, () -> HorizontalBlockContract.inspect(Map.of(ENTRY, nodes.get(ENTRY))));
        nodes.put("unknown/Extra", new ClassNode());
        assertThrows(TransformationException.class, () -> HorizontalBlockContract.inspect(nodes));
        var alias = fixture(false); alias.put("alias", alias.remove(BLOCK));
        assertThrows(TransformationException.class, () -> HorizontalBlockContract.inspect(alias));
    }

    @Test void exactOrientationPairHasDistinctSourceTargetInvariantProof() {
        var source = orientationFixture(false); var target = orientationFixture(true);
        var beforeSource = bytes(source); var beforeTarget = bytes(target);
        var oriented = HorizontalBlockContract.inspect(source);
        var baseline = HorizontalBlockContract.inspect(fixture(false));
        assertTrue(oriented.orientationTransforms()); assertFalse(baseline.orientationTransforms());
        assertEquals(true, oriented.toTree().get("orientationTransforms"));
        assertEquals(false, baseline.toTree().get("orientationTransforms"));
        assertEquals(oriented, HorizontalBlockContract.inspect(target));
        assertNotEquals(baseline, oriented); assertEquals(baseline.plainProof(), oriented.plainProof());
        assertEquals(baseline, new HorizontalBlockContract.Proof(baseline.entry(), baseline.block(),
            baseline.propertyField(), baseline.plainProof()));
        assertEquals("NATIVE_DIRECTION_ROTATE_AND_MIRROR", ((Map<?,?>)oriented.toTree().get("state")).get("orientationMode"));
        assertEquals("INHERITED_NO_OP", ((Map<?,?>)baseline.toTree().get("state")).get("orientationMode"));
        assertEquals("NOT_RUN", oriented.toTree().get("runtimeValidation"));
        assertEquals(HorizontalBlockContract.validate(fixture(true), MOD, "1.0.0"),
            HorizontalBlockContract.validate(target, MOD, "1.0.0"));
        assertBytes(beforeSource, source); assertBytes(beforeTarget, target);
    }

    @Test void orientationProjectionIsAdmissionOnlyAndPairRemovalChangesProof() {
        for (boolean target : new boolean[]{false, true}) {
            var nodes = orientationFixture(target); var before = bytes(nodes);
            var oriented = HorizontalBlockContract.inspect(nodes);
            assertEquals(oriented.plainProof(), PlainBlockContract.inspect(HorizontalBlockContract.plainProjection(nodes)));
            assertEquals(6, nodes.get(BLOCK).methods.size()); assertBytes(before, nodes);
            nodes.get(BLOCK).methods.remove(orientation(nodes, false));
            nodes.get(BLOCK).methods.remove(orientation(nodes, true));
            var baseline = HorizontalBlockContract.inspect(nodes);
            assertFalse(baseline.orientationTransforms()); assertNotEquals(oriented, baseline);
            assertEquals(oriented.plainProof(), baseline.plainProof());
        }
    }

    @Test void orientationPairUsesStructuralOwnedFieldAndClassIdentity() {
        for (boolean target : new boolean[]{false, true}) {
            var renamed = new LinkedHashMap<String,ClassNode>();
            var remapper = new Remapper() {
                @Override public String map(String name) { return name.equals(BLOCK) ? "other/RotatingCube" : name; }
                @Override public String mapFieldName(String owner, String name, String descriptor) {
                    return owner.equals(BLOCK) && name.equals("FACING") ? "ROTATION_STATE" : name;
                }
            };
            for (var n : orientationFixture(target).values()) {
                var copy = new ClassNode(); n.accept(new ClassRemapper(copy, remapper)); renamed.put(copy.name, copy);
            }
            var proof = HorizontalBlockContract.inspect(renamed);
            assertTrue(proof.orientationTransforms()); assertEquals("other/RotatingCube", proof.block());
            assertEquals("ROTATION_STATE", proof.propertyField());
        }
    }

    @TestFactory Stream<DynamicTest> rejectsPartialOrUnverifiedOrientationContracts() {
        var mutations = new LinkedHashMap<String, Consumer<Map<String,ClassNode>>>();
        mutations.put("missing mirror", n -> n.get(BLOCK).methods.remove(orientation(n, true)));
        mutations.put("missing rotate", n -> n.get(BLOCK).methods.remove(orientation(n, false)));
        mutations.put("mirror replaced with helper", n -> orientation(n, true).name = "unverifiedHelper");
        mutations.put("static rotate", n -> orientation(n, false).access |= ACC_STATIC);
        mutations.put("private mirror", n -> orientation(n, true).access = ACC_PRIVATE);
        mutations.put("final rotate", n -> orientation(n, false).access |= ACC_FINAL);
        mutations.put("wrong mirror signature", n -> orientation(n, true).desc = "()V");
        mutations.put("rotate generic metadata", n -> orientation(n, false).signature = "()Ljava/lang/Object;");
        mutations.put("mirror annotation", n -> orientation(n, true).visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        mutations.put("rotate exception", n -> orientation(n, false).exceptions.add("java/lang/Exception"));
        mutations.put("wrong output state receiver", n -> ((VarInsnNode)real(orientation(n, false)).get(0)).var = 0);
        mutations.put("wrong input state receiver", n -> ((VarInsnNode)real(orientation(n, false)).get(3)).var = 0);
        mutations.put("wrong enum receiver", n -> ((VarInsnNode)real(orientation(n, true)).get(2)).var = 1);
        mutations.put("wrong output property", n -> ((FieldInsnNode)real(orientation(n, false)).get(1)).name = "OTHER");
        mutations.put("wrong input property", n -> ((FieldInsnNode)real(orientation(n, true)).get(4)).name = "OTHER");
        mutations.put("foreign property owner", n -> ((FieldInsnNode)real(orientation(n, false)).get(4)).owner = ENTRY);
        mutations.put("instance property access", n -> ((FieldInsnNode)real(orientation(n, true)).get(1)).setOpcode(GETFIELD));
        mutations.put("static state lookup", n -> ((MethodInsnNode)real(orientation(n, false)).get(5)).setOpcode(INVOKESTATIC));
        mutations.put("unproven state lookup", n -> ((MethodInsnNode)real(orientation(n, true)).get(5)).name = "unknown");
        mutations.put("wrong enum cast", n -> ((TypeInsnNode)real(orientation(n, false)).get(6)).desc = "java/lang/Enum");
        mutations.put("missing direction cast", n -> replace(orientation(n, true), 6, new InsnNode(NOP)));
        mutations.put("static rotation invocation", n -> ((MethodInsnNode)real(orientation(n, false)).get(7)).setOpcode(INVOKESTATIC));
        mutations.put("unknown mirror invocation", n -> ((MethodInsnNode)real(orientation(n, true)).get(7)).name = "unknown");
        mutations.put("unknown enum owner", n -> ((MethodInsnNode)real(orientation(n, false)).get(7)).owner = "unknown/Rotation");
        mutations.put("invalid enum interface flag", n -> ((MethodInsnNode)real(orientation(n, true)).get(7)).itf = true);
        mutations.put("unknown state write", n -> ((MethodInsnNode)real(orientation(n, false)).get(8)).name = "unknown");
        mutations.put("missing state cast", n -> replace(orientation(n, true), 9, new InsnNode(NOP)));
        mutations.put("wrong state cast", n -> ((TypeInsnNode)real(orientation(n, false)).get(9)).desc = "java/lang/Object");
        mutations.put("extra rotate effect", n -> orientation(n, false).instructions.insert(new InsnNode(NOP)));
        mutations.put("extra mirror control flow", n -> orientation(n, true).instructions.insert(new JumpInsnNode(GOTO, new LabelNode())));
        mutations.put("extra helper", n -> add(n.get(BLOCK), ACC_PRIVATE, "another", "()V").instructions.add(new InsnNode(RETURN)));
        mutations.put("mixed duplicate pair", n -> { var rotate = orientation(n, false); var mirror = orientation(n, true); mirror.name = rotate.name; mirror.desc = rotate.desc; });
        return mutations.entrySet().stream().flatMap(e -> Stream.of(false, true).map(target -> DynamicTest.dynamicTest(
            (target ? "target " : "source ") + e.getKey(), () -> {
                var nodes = orientationFixture(target); e.getValue().accept(nodes);
                var refusal = assertThrows(TransformationException.class, () -> HorizontalBlockContract.inspect(nodes));
                assertTrue(refusal.getMessage().startsWith("HORIZONTAL_BLOCK_UNSUPPORTED:"), refusal.getMessage());
            })));
    }

    @TestFactory Stream<DynamicTest> rejectsUnprovenSubclassEffectsOnBothVersions() {
        var mutations = new LinkedHashMap<String, Consumer<Map<String,ClassNode>>>();
        mutations.put("nonfinal subclass", n -> n.get(BLOCK).access &= ~ACC_FINAL);
        mutations.put("arbitrary superclass", n -> n.get(BLOCK).superName = "unknown/Block");
        mutations.put("interface contract", n -> n.get(BLOCK).interfaces.add("unknown/Hook"));
        mutations.put("extra class annotation", n -> n.get(BLOCK).visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        mutations.put("generic subclass", n -> n.get(BLOCK).signature = "Lunknown/Block;");
        mutations.put("nested owner", n -> n.get(BLOCK).outerClass = ENTRY);
        mutations.put("nest host", n -> n.get(BLOCK).nestHostClass = ENTRY);
        mutations.put("permitted subtype", n -> n.get(BLOCK).permittedSubclasses = new ArrayList<>(List.of("unknown/Subtype")));
        mutations.put("unknown inner metadata", n -> n.get(BLOCK).innerClasses.add(new InnerClassNode("unknown/X$Y", "unknown/X", "Y", ACC_PUBLIC | ACC_STATIC)));
        mutations.put("extra state field", n -> n.get(BLOCK).fields.add(new FieldNode(ACC_PRIVATE, "state", "I", null, null)));
        mutations.put("nonfinal property", n -> n.get(BLOCK).fields.get(0).access &= ~ACC_FINAL);
        mutations.put("instance property", n -> n.get(BLOCK).fields.get(0).access &= ~ACC_STATIC);
        mutations.put("annotated property", n -> n.get(BLOCK).fields.get(0).invisibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        mutations.put("generic property", n -> n.get(BLOCK).fields.get(0).signature = "Lunknown/Property;");
        mutations.put("constant property", n -> n.get(BLOCK).fields.get(0).value = "unknown");
        mutations.put("vertical default", n -> ((FieldInsnNode)real(method(n.get(BLOCK), "<init>")).get(9)).name = "UP");
        mutations.put("different horizontal default", n -> ((FieldInsnNode)real(method(n.get(BLOCK), "<init>")).get(9)).name = "SOUTH");
        mutations.put("wrong constructor super", n -> ((MethodInsnNode)real(method(n.get(BLOCK), "<init>")).get(2)).owner = "unknown/Block");
        mutations.put("wrong super opcode", n -> ((MethodInsnNode)real(method(n.get(BLOCK), "<init>")).get(2)).setOpcode(INVOKEVIRTUAL));
        mutations.put("static definition access", n -> ((FieldInsnNode)real(method(n.get(BLOCK), "<init>")).get(5)).setOpcode(GETSTATIC));
        mutations.put("foreign definition owner", n -> ((FieldInsnNode)real(method(n.get(BLOCK), "<init>")).get(5)).owner = "unknown/Block");
        mutations.put("missing default cast", n -> replace(method(n.get(BLOCK), "<init>"), 11, new InsnNode(NOP)));
        mutations.put("extra constructor effect", n -> method(n.get(BLOCK), "<init>").instructions.insert(new InsnNode(NOP)));
        mutations.put("unknown static property", n -> ((FieldInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(0)).name = "unknown");
        mutations.put("foreign property assignment", n -> ((FieldInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(1)).owner = ENTRY);
        mutations.put("extra static initialization", n -> method(n.get(BLOCK), "<clinit>").instructions.insert(new InsnNode(NOP)));
        mutations.put("wrong placement access", n -> placement(n).access = ACC_PRIVATE);
        mutations.put("static placement", n -> placement(n).access |= ACC_STATIC);
        mutations.put("unknown placement contract", n -> placement(n).name = "unknownPlacement");
        mutations.put("method annotation", n -> placement(n).visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        mutations.put("declared exception", n -> placement(n).exceptions.add("java/lang/Exception"));
        mutations.put("catch handler", n -> placement(n).tryCatchBlocks.add(new TryCatchBlockNode(new LabelNode(), new LabelNode(), new LabelNode(), "java/lang/Exception")));
        mutations.put("wrong context local", n -> ((VarInsnNode)real(placement(n)).get(3)).var = 0);
        mutations.put("nonvirtual context invocation", n -> ((MethodInsnNode)real(placement(n)).get(4)).setOpcode(INVOKESTATIC));
        mutations.put("unknown direction computation", n -> ((MethodInsnNode)real(placement(n)).get(5)).name = "unknown");
        mutations.put("wrong invocation interface flag", n -> ((MethodInsnNode)real(placement(n)).get(5)).itf = true);
        mutations.put("missing state cast", n -> replace(placement(n), 7, new InsnNode(NOP)));
        mutations.put("extra placement branch", n -> placement(n).instructions.insert(new JumpInsnNode(GOTO, new LabelNode())));
        mutations.put("wrong builder visibility", n -> builder(n).access = ACC_PUBLIC);
        mutations.put("wrong builder generic", n -> builder(n).signature = "(Lunknown/Builder;)V");
        mutations.put("two properties", n -> replace(builder(n), 1, new InsnNode(ICONST_2)));
        mutations.put("wrong property array", n -> ((TypeInsnNode)real(builder(n)).get(2)).desc = "unknown/Property");
        mutations.put("wrong property field", n -> ((FieldInsnNode)real(builder(n)).get(5)).name = "OTHER");
        mutations.put("wrong builder opcode", n -> ((MethodInsnNode)real(builder(n)).get(7)).setOpcode(INVOKESTATIC));
        mutations.put("extra helper", n -> add(n.get(BLOCK), ACC_PRIVATE | ACC_STATIC, "extra", "()V").instructions.add(new InsnNode(RETURN)));
        mutations.put("entry creates native block", n -> ((TypeInsnNode)real(method(n.get(ENTRY), "createBlock")).get(0)).desc = n.get(BLOCK).superName);
        mutations.put("entry mismatched constructor", n -> { var c = real(method(n.get(ENTRY), "createBlock")); ((MethodInsnNode)c.get(c.size() - 2)).owner = n.get(BLOCK).superName; });
        mutations.put("entry extra callback", n -> method(n.get(ENTRY), "<init>").instructions.insert(new InsnNode(NOP)));
        return mutations.entrySet().stream().flatMap(e -> Stream.of(false, true).map(target -> DynamicTest.dynamicTest(
            (target ? "target " : "source ") + e.getKey(), () -> {
                var nodes = fixture(target); e.getValue().accept(nodes);
                var refusal = assertThrows(TransformationException.class, () -> HorizontalBlockContract.inspect(nodes));
                assertTrue(refusal.getMessage().startsWith("HORIZONTAL_BLOCK_UNSUPPORTED:"), refusal.getMessage());
            })));
    }

    private static Map<String,ClassNode> orientationFixture(boolean target) {
        var nodes = fixture(target); var block = nodes.get(BLOCK); String state = state(target);
        String direction = target ? "net/minecraft/core/Direction" : "net/minecraft/util/Direction";
        String directionProperty = target ? "net/minecraft/world/level/block/state/properties/DirectionProperty" : "net/minecraft/state/DirectionProperty";
        String property = target ? "net/minecraft/world/level/block/state/properties/Property" : "net/minecraft/state/Property";
        for (boolean mirror : new boolean[]{false, true}) {
            String enumOwner = (target ? "net/minecraft/world/level/block/" : "net/minecraft/util/") + (mirror ? "Mirror" : "Rotation");
            String override = mirror ? (target ? "m_6943_" : "func_185471_a") : (target ? "m_6843_" : "func_185499_a");
            String directionMethod = mirror ? (target ? "m_54848_" : "func_185803_b") : (target ? "m_55954_" : "func_185831_a");
            var m = add(block, ACC_PUBLIC, override, "(L" + state + ";L" + enumOwner + ";)L" + state + ";");
            m.maxLocals = 3;
            m.instructions.add(new VarInsnNode(ALOAD, 1));
            m.instructions.add(new FieldInsnNode(GETSTATIC, BLOCK, "FACING", "L" + directionProperty + ";"));
            m.instructions.add(new VarInsnNode(ALOAD, 2)); m.instructions.add(new VarInsnNode(ALOAD, 1));
            m.instructions.add(new FieldInsnNode(GETSTATIC, BLOCK, "FACING", "L" + directionProperty + ";"));
            call(m, INVOKEVIRTUAL, state, target ? "m_61143_" : "func_177229_b", "(L" + property + ";)Ljava/lang/Comparable;");
            m.instructions.add(new TypeInsnNode(CHECKCAST, direction));
            call(m, INVOKEVIRTUAL, enumOwner, directionMethod, "(L" + direction + ";)L" + direction + ";");
            call(m, INVOKEVIRTUAL, state, target ? "m_61124_" : "func_206870_a", "(L" + property + ";Ljava/lang/Comparable;)Ljava/lang/Object;");
            m.instructions.add(new TypeInsnNode(CHECKCAST, state)); m.instructions.add(new InsnNode(ARETURN));
        }
        return nodes;
    }

    private static MethodNode orientation(Map<String,ClassNode> nodes, boolean mirror) {
        return nodes.get(BLOCK).methods.stream().filter(m -> mirror ? Set.of("func_185471_a", "m_6943_").contains(m.name)
            : Set.of("func_185499_a", "m_6843_").contains(m.name)).findFirst().orElseThrow();
    }

    private static Map<String,ClassNode> fixture(boolean target) {
        var entry = entryFixture(target); var factory = real(method(entry, "createBlock"));
        ((TypeInsnNode)factory.get(0)).desc = BLOCK;
        ((MethodInsnNode)factory.get(factory.size() - 2)).owner = BLOCK;
        String nativeBlock = nativeBlock(target), props = properties(target), state = state(target), container = container(target);
        String builder = container + "$Builder";
        String holder = target ? "net/minecraft/world/level/block/state/StateHolder" : "net/minecraft/state/StateHolder";
        String property = target ? "net/minecraft/world/level/block/state/properties/Property" : "net/minecraft/state/Property";
        String directionProperty = target ? "net/minecraft/world/level/block/state/properties/DirectionProperty" : "net/minecraft/state/DirectionProperty";
        String blockProperties = target ? "net/minecraft/world/level/block/state/properties/BlockStateProperties" : "net/minecraft/state/properties/BlockStateProperties";
        String direction = target ? "net/minecraft/core/Direction" : "net/minecraft/util/Direction";
        String context = target ? "net/minecraft/world/item/context/BlockPlaceContext" : "net/minecraft/item/BlockItemUseContext";
        var block = new ClassNode(); block.name = BLOCK; block.superName = nativeBlock; block.version = V1_8;
        block.access = ACC_PUBLIC | ACC_FINAL | ACC_SUPER;
        block.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "FACING", "L" + directionProperty + ";", null, null));
        var clinit = add(block, ACC_STATIC, "<clinit>", "()V");
        clinit.instructions.add(new FieldInsnNode(GETSTATIC, blockProperties, target ? "f_61374_" : "field_208157_J", "L" + directionProperty + ";"));
        clinit.instructions.add(new FieldInsnNode(PUTSTATIC, BLOCK, "FACING", "L" + directionProperty + ";")); clinit.instructions.add(new InsnNode(RETURN));
        var ctor = add(block, ACC_PUBLIC, "<init>", "(L" + props + ";)V");
        ctor.instructions.add(new VarInsnNode(ALOAD, 0)); ctor.instructions.add(new VarInsnNode(ALOAD, 1));
        call(ctor, INVOKESPECIAL, nativeBlock, "<init>", "(L" + props + ";)V");
        ctor.instructions.add(new VarInsnNode(ALOAD, 0)); ctor.instructions.add(new VarInsnNode(ALOAD, 0));
        ctor.instructions.add(new FieldInsnNode(GETFIELD, BLOCK, target ? "f_49792_" : "field_176227_L", "L" + container + ";"));
        call(ctor, INVOKEVIRTUAL, container, target ? "m_61090_" : "func_177621_b", "()L" + holder + ";");
        ctor.instructions.add(new TypeInsnNode(CHECKCAST, state));
        ctor.instructions.add(new FieldInsnNode(GETSTATIC, BLOCK, "FACING", "L" + directionProperty + ";"));
        ctor.instructions.add(new FieldInsnNode(GETSTATIC, direction, "NORTH", "L" + direction + ";"));
        call(ctor, INVOKEVIRTUAL, state, target ? "m_61124_" : "func_206870_a", "(L" + property + ";Ljava/lang/Comparable;)Ljava/lang/Object;");
        ctor.instructions.add(new TypeInsnNode(CHECKCAST, state));
        call(ctor, INVOKEVIRTUAL, BLOCK, target ? "m_49959_" : "func_180632_j", "(L" + state + ";)V"); ctor.instructions.add(new InsnNode(RETURN));
        var create = add(block, ACC_PROTECTED, target ? "m_7926_" : "func_206840_a", "(L" + builder + ";)V");
        create.instructions.add(new VarInsnNode(ALOAD, 1)); create.instructions.add(new InsnNode(ICONST_1)); create.instructions.add(new TypeInsnNode(ANEWARRAY, property));
        create.instructions.add(new InsnNode(DUP)); create.instructions.add(new InsnNode(ICONST_0));
        create.instructions.add(new FieldInsnNode(GETSTATIC, BLOCK, "FACING", "L" + directionProperty + ";")); create.instructions.add(new InsnNode(AASTORE));
        call(create, INVOKEVIRTUAL, builder, target ? "m_61104_" : "func_206894_a", "([L" + property + ";)L" + builder + ";");
        create.instructions.add(new InsnNode(POP)); create.instructions.add(new InsnNode(RETURN));
        var place = add(block, ACC_PUBLIC, target ? "m_5573_" : "func_196258_a", "(L" + context + ";)L" + state + ";");
        place.instructions.add(new VarInsnNode(ALOAD, 0));
        call(place, INVOKEVIRTUAL, BLOCK, target ? "m_49966_" : "func_176223_P", "()L" + state + ";");
        place.instructions.add(new FieldInsnNode(GETSTATIC, BLOCK, "FACING", "L" + directionProperty + ";")); place.instructions.add(new VarInsnNode(ALOAD, 1));
        call(place, INVOKEVIRTUAL, context, target ? "m_8125_" : "func_195992_f", "()L" + direction + ";");
        call(place, INVOKEVIRTUAL, direction, target ? "m_122424_" : "func_176734_d", "()L" + direction + ";");
        call(place, INVOKEVIRTUAL, state, target ? "m_61124_" : "func_206870_a", "(L" + property + ";Ljava/lang/Comparable;)Ljava/lang/Object;");
        place.instructions.add(new TypeInsnNode(CHECKCAST, state)); place.instructions.add(new InsnNode(ARETURN));
        return new LinkedHashMap<>(Map.of(ENTRY, entry, BLOCK, block));
    }

    private static ClassNode entryFixture(boolean target) {
        String block=target?"net/minecraft/world/level/block/Block":"net/minecraft/block/Block";
        String item=target?"net/minecraft/world/item/Item":"net/minecraft/item/Item";
        String blockItem=target?"net/minecraft/world/item/BlockItem":"net/minecraft/item/BlockItem";
        String props=target?"net/minecraft/world/level/block/state/BlockBehaviour$Properties":"net/minecraft/block/AbstractBlock$Properties";
        String itemProps=item+"$Properties", object=object(target);
        var n=new ClassNode(); n.version=V1_8; n.access=ACC_PUBLIC|ACC_FINAL|ACC_SUPER; n.name=ENTRY; n.superName="java/lang/Object"; n.sourceFile="PlainBlockContent.java";
        n.visibleAnnotations=new ArrayList<>(); var mod=new AnnotationNode("Lnet/minecraftforge/fml/common/Mod;"); mod.values=new ArrayList<>(List.of("value",MOD)); n.visibleAnnotations.add(mod);
        n.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"MOD_ID","Ljava/lang/String;",null,MOD));
        for(String name:List.of("BLOCKS","ITEMS"))n.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,name,"L"+DEFERRED+";",null,null));
        for(String name:List.of("BLOCK","BLOCK_ITEM"))n.fields.add(new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,name,object,null,null));
        var init=add(n,ACC_STATIC,"<clinit>","()V");
        for(String field:List.of("BLOCKS","ITEMS")){
            init.instructions.add(new FieldInsnNode(GETSTATIC,REGISTRIES,field,"L"+REGISTRY+";"));init.instructions.add(new LdcInsnNode(MOD));
            call(init,INVOKESTATIC,DEFERRED,"create","(L"+REGISTRY+";Ljava/lang/String;)L"+DEFERRED+";");init.instructions.add(new FieldInsnNode(PUTSTATIC,ENTRY,field,"L"+DEFERRED+";"));
        }
        for(String[] role:new String[][]{{"BLOCKS","BLOCK","createBlock",block},{"ITEMS","BLOCK_ITEM","createBlockItem",blockItem}}){
            init.instructions.add(new FieldInsnNode(GETSTATIC,ENTRY,role[0],"L"+DEFERRED+";"));init.instructions.add(new LdcInsnNode("bridge_block"));
            init.instructions.add(new InvokeDynamicInsnNode("get","()Ljava/util/function/Supplier;",METAFACTORY,Type.getMethodType("()Ljava/lang/Object;"),
                new Handle(H_INVOKESTATIC,ENTRY,role[2],"()L"+role[3]+";",false),Type.getMethodType("()L"+role[3]+";")));
            call(init,INVOKEVIRTUAL,DEFERRED,"register","(Ljava/lang/String;Ljava/util/function/Supplier;)"+object);init.instructions.add(new FieldInsnNode(PUTSTATIC,ENTRY,role[1],object));
        }init.instructions.add(new InsnNode(RETURN));
        var ctor=add(n,ACC_PUBLIC,"<init>","()V");ctor.instructions.add(new VarInsnNode(ALOAD,0));call(ctor,INVOKESPECIAL,"java/lang/Object","<init>","()V");
        for(String field:List.of("BLOCKS","ITEMS")){
            ctor.instructions.add(new FieldInsnNode(GETSTATIC,ENTRY,field,"L"+DEFERRED+";"));call(ctor,INVOKESTATIC,CONTEXT,"get","()L"+CONTEXT+";");
            call(ctor,INVOKEVIRTUAL,CONTEXT,"getModEventBus","()L"+BUS+";");call(ctor,INVOKEVIRTUAL,DEFERRED,"register","(L"+BUS+";)V");
        }ctor.instructions.add(new InsnNode(RETURN));
        var b=add(n,ACC_PRIVATE|ACC_STATIC,"createBlock","()L"+block+";");b.instructions.add(new TypeInsnNode(NEW,block));b.instructions.add(new InsnNode(DUP));
        if(target){
            call(b,INVOKESTATIC,props,"m_284310_","()L"+props+";");String color="net/minecraft/world/level/material/MapColor",instrument="net/minecraft/world/level/block/state/properties/NoteBlockInstrument";
            b.instructions.add(new FieldInsnNode(GETSTATIC,color,"f_283947_","L"+color+";"));call(b,INVOKEVIRTUAL,props,"m_284180_","(L"+color+";)L"+props+";");
            b.instructions.add(new FieldInsnNode(GETSTATIC,instrument,"BASEDRUM","L"+instrument+";"));call(b,INVOKEVIRTUAL,props,"m_280658_","(L"+instrument+";)L"+props+";");
        }else{String material="net/minecraft/block/material/Material";b.instructions.add(new FieldInsnNode(GETSTATIC,material,"field_151576_e","L"+material+";"));call(b,INVOKESTATIC,props,"func_200945_a","(L"+material+";)L"+props+";");}
        b.instructions.add(new InsnNode(FCONST_2));b.instructions.add(new LdcInsnNode(6.0F));call(b,INVOKEVIRTUAL,props,target?"m_60913_":"func_200948_a","(FF)L"+props+";");call(b,INVOKESPECIAL,block,"<init>","(L"+props+";)V");b.instructions.add(new InsnNode(ARETURN));
        var i=add(n,ACC_PRIVATE|ACC_STATIC,"createBlockItem","()L"+blockItem+";");i.instructions.add(new TypeInsnNode(NEW,blockItem));i.instructions.add(new InsnNode(DUP));i.instructions.add(new FieldInsnNode(GETSTATIC,ENTRY,"BLOCK",object));
        call(i,INVOKEVIRTUAL,object.substring(1,object.length()-1),"get","()L"+(target?"java/lang/Object":"net/minecraftforge/registries/IForgeRegistryEntry")+";");i.instructions.add(new TypeInsnNode(CHECKCAST,block));
        i.instructions.add(new TypeInsnNode(NEW,itemProps));i.instructions.add(new InsnNode(DUP));call(i,INVOKESPECIAL,itemProps,"<init>","()V");i.instructions.add(new IntInsnNode(BIPUSH,32));
        call(i,INVOKEVIRTUAL,itemProps,target?"m_41487_":"func_200917_a","(I)L"+itemProps+";");call(i,INVOKESPECIAL,blockItem,"<init>","(L"+block+";L"+itemProps+";)V");i.instructions.add(new InsnNode(ARETURN));
        return n;
    }

    private static String nativeBlock(boolean target) { return target ? "net/minecraft/world/level/block/Block" : "net/minecraft/block/Block"; }
    private static String properties(boolean target) { return target ? "net/minecraft/world/level/block/state/BlockBehaviour$Properties" : "net/minecraft/block/AbstractBlock$Properties"; }
    private static String state(boolean target) { return target ? "net/minecraft/world/level/block/state/BlockState" : "net/minecraft/block/BlockState"; }
    private static String container(boolean target) { return target ? "net/minecraft/world/level/block/state/StateDefinition" : "net/minecraft/state/StateContainer"; }
    private static String object(boolean target) { return "L" + (target ? "net/minecraftforge/registries/RegistryObject" : "net/minecraftforge/fml/RegistryObject") + ";"; }
    private static MethodNode add(ClassNode n, int access, String name, String descriptor) { var m = new MethodNode(access, name, descriptor, null, null); m.maxStack = 16; m.maxLocals = 2; n.methods.add(m); return m; }
    private static void call(MethodNode m, int opcode, String owner, String name, String descriptor) { m.instructions.add(new MethodInsnNode(opcode, owner, name, descriptor, false)); }
    private static MethodNode method(ClassNode n, String name) { return n.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static MethodNode placement(Map<String,ClassNode> n) { var b = n.get(BLOCK); return b.methods.stream().filter(m -> m.name.equals("func_196258_a") || m.name.equals("m_5573_")).findFirst().orElseThrow(); }
    private static MethodNode builder(Map<String,ClassNode> n) { var b = n.get(BLOCK); return b.methods.stream().filter(m -> m.name.equals("func_206840_a") || m.name.equals("m_7926_")).findFirst().orElseThrow(); }
    private static List<AbstractInsnNode> real(MethodNode m) { return ClientConfigContract.code(m); }
    private static void replace(MethodNode m, int index, AbstractInsnNode replacement) { m.instructions.set(real(m).get(index), replacement); }
    private static Map<String,byte[]> bytes(Map<String,ClassNode> nodes) { var out = new TreeMap<String,byte[]>(); nodes.forEach((name, node) -> { var w = new ClassWriter(0); node.accept(w); out.put(name, w.toByteArray()); }); return out; }
    private static void assertBytes(Map<String,byte[]> expected, Map<String,ClassNode> nodes) { var actual = bytes(nodes); assertEquals(expected.keySet(), actual.keySet()); expected.forEach((name, value) -> assertArrayEquals(value, actual.get(name), name)); }
}

