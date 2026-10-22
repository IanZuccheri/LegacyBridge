package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationLimits;
import dev.legacybridge.converter.operation.OperationStoppedException;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;
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

/** Inert synthetic trees; these tests never define or execute source mod/game classes. */
class BooleanToggleBlockContractTest {
    private static final String ENTRY = "example/ToggleContent";
    private static final String BLOCK = "example/ToggleBlock";
    private static final String MOD = "toggle_fixture";
    private static final String DEFERRED = "net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    private static final String REGISTRIES = "net/minecraftforge/registries/ForgeRegistries";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";
    private static final Handle METAFACTORY = new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);

    @Test void independentlyProvesSourceAndTargetWithoutMutationOrExecution() {
        var source = fixture(false); var target = fixture(true);
        var sourceBytes = bytes(source); var targetBytes = bytes(target);
        var proof = BooleanToggleBlockContract.inspect(source);
        assertEquals(proof, BooleanToggleBlockContract.inspect(target));
        assertEquals(ENTRY, proof.entry()); assertEquals(BLOCK, proof.block()); assertEquals("ACTIVE", proof.propertyField());
        assertEquals(MOD, proof.plainProof().modId()); assertEquals("bridge_block", proof.plainProof().registryName());
        assertEquals(false, proof.toTree().get("classesExecuted")); assertEquals("NOT_RUN", proof.toTree().get("runtimeValidation"));
        var state = (Map<?,?>) proof.toTree().get("state");
        assertEquals("active", state.get("property")); assertEquals(false, state.get("default"));
        var interaction = (Map<?,?>)proof.toTree().get("interaction");
        assertEquals("SUCCESS_WITHOUT_WRITE", interaction.get("client")); assertEquals(3, interaction.get("updateFlags"));
        assertEquals("IGNORED_NOT_ASSUMED_SUCCESS", interaction.get("setBlockResult"));
        assertBytes(sourceBytes, source); assertBytes(targetBytes, target);
        var content = BooleanToggleBlockContract.validate(target, MOD, "1.0.0");
        assertEquals(1, content.blocks().size()); assertEquals(1, content.items().size());
        assertEquals(MOD + ":bridge_block", content.items().get(0).block());
    }

    @Test void checkedPlainProjectionNeverBecomesAnEmittedSubclassReplacement() {
        for (boolean target : new boolean[]{false, true}) {
            var nodes = fixture(target); var before = bytes(nodes); var proof = BooleanToggleBlockContract.inspect(nodes);
            var view = BooleanToggleBlockContract.plainProjection(nodes);
            assertEquals(Set.of(ENTRY), view.keySet()); assertEquals(proof.plainProof(), PlainBlockContract.inspect(view));
            var factory = method(view.get(ENTRY), "createBlock");
            assertEquals(nativeBlock(target), ((TypeInsnNode)real(factory).get(0)).desc);
            assertEquals(BLOCK, ((TypeInsnNode)real(method(nodes.get(ENTRY), "createBlock")).get(0)).desc);
            factory.instructions.clear(); assertBytes(before, nodes);
            assertEquals(proof, BooleanToggleBlockContract.inspect(nodes));
        }
        var invalid = fixture(false); use(invalid).instructions.insert(new InsnNode(NOP));
        assertThrows(TransformationException.class, () -> BooleanToggleBlockContract.plainProjection(invalid));
    }

    @Test void contentViewRequiresTargetNamespaceAndMatchingIdentity() {
        assertThrows(TransformationException.class, () -> BooleanToggleBlockContract.validate(fixture(false), MOD, "1"));
        assertThrows(TransformationException.class, () -> BooleanToggleBlockContract.validate(fixture(true), "other", "1"));
        assertThrows(TransformationException.class, () -> BooleanToggleBlockContract.validate(fixture(true), MOD, "../unsafe"));
    }

    @Test void structuralOwnedRolesAcceptRenamingWithoutFingerprintAllowlist() {
        for (boolean target : new boolean[]{false, true}) {
            var renamed = new LinkedHashMap<String,ClassNode>();
            var remapper = new Remapper() {
                @Override public String map(String name) {
                    return name.equals(ENTRY) ? "other/Registration" : name.equals(BLOCK) ? "other/InteractiveCube" : name;
                }
                @Override public String mapFieldName(String owner, String name, String descriptor) {
                    if (owner.equals(BLOCK) && name.equals("ACTIVE")) return "MODE";
                    return owner.equals(ENTRY) ? "registry_" + name : name;
                }
            };
            for (var original : fixture(target).values()) {
                var changed = new ClassNode(); original.accept(new ClassRemapper(changed, remapper)); renamed.put(changed.name, changed);
            }
            var proof = BooleanToggleBlockContract.inspect(renamed);
            assertEquals("other/Registration", proof.entry()); assertEquals("other/InteractiveCube", proof.block());
            assertEquals("MODE", proof.propertyField()); assertEquals(MOD, proof.plainProof().modId());
        }
    }

    @Test void debugLabelsFramesAndMethodOrderArePreservedWithoutChangingBranchDestination() {
        for (boolean target : new boolean[]{false, true}) {
            var nodes = fixture(target); var callback = use(nodes); var branch = (JumpInsnNode)real(callback).get(2);
            callback.instructions.insert(branch.label, new FrameNode(F_SAME, 0, null, 0, null));
            callback.instructions.insert(branch.label, new LineNumberNode(123, branch.label));
            for (var node : nodes.values()) {
                node.sourceFile = "Authored.java"; node.sourceDebug = "SMAP\nAuthored.java\n";
                Collections.reverse(node.methods);
            }
            var before = bytes(nodes); BooleanToggleBlockContract.inspect(nodes); assertBytes(before, nodes);
        }
    }

    @Test void onlyReviewedInnerAndBuilderGenericMetadataAreAccepted() {
        for (boolean target : new boolean[]{false, true}) {
            var n = fixture(target); var a = new Api(target); var block = n.get(BLOCK);
            builder(n).signature = "(L" + a.builder + "<L" + a.block + ";L" + a.state + ";>;)V";
            block.innerClasses.add(new InnerClassNode(a.builder, a.container, "Builder", ACC_PUBLIC | ACC_STATIC));
            block.innerClasses.add(new InnerClassNode(a.props, a.props.substring(0, a.props.indexOf('$')), "Properties", ACC_PUBLIC | ACC_STATIC));
            BooleanToggleBlockContract.inspect(n);
        }
    }

    @Test void cancellationPropagatesBeforeProjectionOrScanning() {
        var control = new OperationControl(OperationLimits.defaults(), ignored -> { });
        assertTrue(control.requestCancel());
        try (var ignored = control.enter()) {
            assertThrows(OperationStoppedException.class, () -> BooleanToggleBlockContract.inspect(fixture(false)));
        }
    }

    @Test void rejectsMissingGraphAndForeignClassIdentity() {
        assertThrows(TransformationException.class, () -> BooleanToggleBlockContract.inspect(null));
        assertThrows(TransformationException.class, () -> BooleanToggleBlockContract.inspect(Map.of()));
        var missing = fixture(false); missing.remove(BLOCK);
        assertThrows(TransformationException.class, () -> BooleanToggleBlockContract.inspect(missing));
        var wrong = fixture(false); var block = wrong.remove(BLOCK); wrong.put("different/key", block);
        assertThrows(TransformationException.class, () -> BooleanToggleBlockContract.inspect(wrong));
    }

    @TestFactory Stream<DynamicTest> rejectsEveryAlternativeBranchDestination() {
        return Stream.of(false, true).flatMap(target -> IntStream.range(0, 16).filter(index -> index != 5).mapToObj(index ->
            DynamicTest.dynamicTest((target ? "target" : "source") + " branch to instruction " + index, () -> {
                var n = fixture(target); var m = use(n); var label = new LabelNode();
                m.instructions.insertBefore(real(m).get(index), label); ((JumpInsnNode)real(m).get(2)).label = label;
                rejected(n);
            })));
    }

    @TestFactory Stream<DynamicTest> rejectsUnknownMetadataEffectsAndIncorrectContext() {
        var cases = new LinkedHashMap<String,Consumer<Map<String,ClassNode>>>();
        cases.put("nonfinal subclass", n -> n.get(BLOCK).access &= ~ACC_FINAL);
        cases.put("abstract subclass", n -> n.get(BLOCK).access |= ACC_ABSTRACT);
        cases.put("wrong bytecode version", n -> n.get(BLOCK).version = V17);
        cases.put("extra interface", n -> n.get(BLOCK).interfaces.add("unknown/Hook"));
        cases.put("foreign superclass", n -> n.get(BLOCK).superName = "unknown/Block");
        cases.put("extra class", n -> n.put("another/Class", new ClassNode()));
        cases.put("class signature", n -> n.get(BLOCK).signature = "Lunknown/Super;");
        cases.put("class annotation", n -> n.get(BLOCK).visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        cases.put("nest metadata", n -> n.get(BLOCK).nestHostClass = "unknown/Owner");
        cases.put("outer metadata", n -> n.get(BLOCK).outerClass = ENTRY);
        cases.put("record metadata", n -> n.get(BLOCK).recordComponents = new ArrayList<>(List.of(new RecordComponentNode("value", "I", null))));
        cases.put("sealed metadata", n -> n.get(BLOCK).permittedSubclasses = new ArrayList<>(List.of("unknown/Child")));
        cases.put("extra field", n -> n.get(BLOCK).fields.add(new FieldNode(ACC_STATIC, "counter", "I", null, null)));
        cases.put("mutable property", n -> n.get(BLOCK).fields.get(0).access &= ~ACC_FINAL);
        cases.put("instance property", n -> n.get(BLOCK).fields.get(0).access &= ~ACC_STATIC);
        cases.put("private property", n -> n.get(BLOCK).fields.get(0).access = ACC_PRIVATE | ACC_STATIC | ACC_FINAL);
        cases.put("wrong property descriptor", n -> n.get(BLOCK).fields.get(0).desc = "Ljava/lang/Boolean;");
        cases.put("property generic signature", n -> n.get(BLOCK).fields.get(0).signature = "Ljava/lang/Object;");
        cases.put("constant property", n -> n.get(BLOCK).fields.get(0).value = "active");
        cases.put("field annotation", n -> n.get(BLOCK).fields.get(0).visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        cases.put("property renamed literal", n -> ((LdcInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(0)).cst = "powered");
        cases.put("property nonstring literal", n -> ((LdcInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(0)).cst = 1);
        cases.put("property instance factory", n -> ((MethodInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(1)).setOpcode(INVOKEVIRTUAL));
        cases.put("property foreign factory", n -> ((MethodInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(1)).owner = "unknown/Property");
        cases.put("property foreign write", n -> ((FieldInsnNode)real(method(n.get(BLOCK), "<clinit>")).get(2)).owner = ENTRY);
        cases.put("static extra effect", n -> method(n.get(BLOCK), "<clinit>").instructions.insert(new InsnNode(NOP)));
        cases.put("true default", n -> ((FieldInsnNode)real(method(n.get(BLOCK), "<init>")).get(9)).name = "TRUE");
        cases.put("unknown boxed default", n -> ((FieldInsnNode)real(method(n.get(BLOCK), "<init>")).get(9)).owner = "unknown/Boolean");
        cases.put("default property receiver", n -> ((FieldInsnNode)real(method(n.get(BLOCK), "<init>")).get(8)).owner = ENTRY);
        cases.put("wrong constructor super", n -> ((MethodInsnNode)real(method(n.get(BLOCK), "<init>")).get(2)).owner = "unknown/Block");
        cases.put("constructor wrong opcode", n -> ((MethodInsnNode)real(method(n.get(BLOCK), "<init>")).get(2)).setOpcode(INVOKEVIRTUAL));
        cases.put("constructor early state access", n -> ((VarInsnNode)real(method(n.get(BLOCK), "<init>")).get(1)).var = 0);
        cases.put("constructor extra effect", n -> method(n.get(BLOCK), "<init>").instructions.insert(new InsnNode(NOP)));
        cases.put("two properties", n -> replace(builder(n), 1, new InsnNode(ICONST_2)));
        cases.put("wrong property index", n -> replace(builder(n), 4, new InsnNode(ICONST_1)));
        cases.put("wrong builder parameter", n -> ((VarInsnNode)real(builder(n)).get(0)).var = 0);
        cases.put("wrong builder property", n -> ((FieldInsnNode)real(builder(n)).get(5)).name = "OTHER");
        cases.put("builder private", n -> builder(n).access = ACC_PRIVATE);
        cases.put("builder static", n -> builder(n).access |= ACC_STATIC);
        cases.put("builder wrong generic", n -> builder(n).signature = "(Lunknown/Builder;)V");
        cases.put("builder wrong opcode", n -> ((MethodInsnNode)real(builder(n)).get(7)).setOpcode(INVOKESTATIC));
        cases.put("builder unknown metadata", n -> n.get(BLOCK).innerClasses.add(new InnerClassNode("unknown/Inner", "unknown", "Inner", ACC_PUBLIC | ACC_STATIC)));
        cases.put("use private", n -> use(n).access = ACC_PRIVATE);
        cases.put("use static", n -> use(n).access |= ACC_STATIC);
        cases.put("use synchronized", n -> use(n).access |= ACC_SYNCHRONIZED);
        cases.put("unknown use override", n -> use(n).name = "otherUse");
        cases.put("wrong use descriptor", n -> use(n).desc = "()Ljava/lang/Object;");
        cases.put("use generic", n -> use(n).signature = "()Ljava/lang/Object;");
        cases.put("use declared exception", n -> use(n).exceptions.add("java/lang/Exception"));
        cases.put("use handler", n -> use(n).tryCatchBlocks.add(new TryCatchBlockNode(new LabelNode(), new LabelNode(), new LabelNode(), "java/lang/Exception")));
        cases.put("use annotation", n -> use(n).visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lunknown/Hook;"))));
        cases.put("use parameters", n -> use(n).parameters = new ArrayList<>(List.of(new ParameterNode("world", 0))));
        cases.put("instruction annotation", n -> real(use(n)).get(0).visibleTypeAnnotations = new ArrayList<>(List.of(new TypeAnnotationNode(0, null, "Lunknown/Hook;"))));
        cases.put("guard wrong local", n -> ((VarInsnNode)real(use(n)).get(0)).var = 0);
        cases.put("guard static field", n -> ((FieldInsnNode)real(use(n)).get(1)).setOpcode(GETSTATIC));
        cases.put("guard unknown field", n -> ((FieldInsnNode)real(use(n)).get(1)).name = "isRemoteOther");
        cases.put("guard wrong owner", n -> ((FieldInsnNode)real(use(n)).get(1)).owner = "unknown/World");
        cases.put("guard wrong descriptor", n -> ((FieldInsnNode)real(use(n)).get(1)).desc = "I");
        cases.put("reversed client/server branch", n -> ((JumpInsnNode)real(use(n)).get(2)).setOpcode(IFNE));
        cases.put("unconditional server branch", n -> ((JumpInsnNode)real(use(n)).get(2)).setOpcode(GOTO));
        cases.put("dangling server label", n -> ((JumpInsnNode)real(use(n)).get(2)).label = new LabelNode());
        cases.put("foreign method label", n -> { var l = new LabelNode(); method(n.get(BLOCK), "<init>").instructions.insert(l); ((JumpInsnNode)real(use(n)).get(2)).label = l; });
        cases.put("client returns consume", n -> ((FieldInsnNode)real(use(n)).get(3)).name = "CONSUME");
        cases.put("server returns success", n -> ((FieldInsnNode)real(use(n)).get(14)).name = "SUCCESS");
        cases.put("server unknown result", n -> ((FieldInsnNode)real(use(n)).get(14)).name = "CONSUME_PARTIAL");
        cases.put("wrong server world", n -> ((VarInsnNode)real(use(n)).get(5)).var = 0);
        cases.put("wrong callback position", n -> ((VarInsnNode)real(use(n)).get(6)).var = 4);
        cases.put("wrong callback state", n -> ((VarInsnNode)real(use(n)).get(7)).var = 0);
        cases.put("foreign cycled property", n -> ((FieldInsnNode)real(use(n)).get(8)).owner = ENTRY);
        cases.put("other cycled property", n -> ((FieldInsnNode)real(use(n)).get(8)).name = "OTHER");
        cases.put("cycle unknown owner", n -> ((MethodInsnNode)real(use(n)).get(9)).owner = "unknown/State");
        cases.put("cycle wrong opcode", n -> ((MethodInsnNode)real(use(n)).get(9)).setOpcode(INVOKESTATIC));
        cases.put("cycle wrong interface flag", n -> ((MethodInsnNode)real(use(n)).get(9)).itf = true);
        cases.put("cycle unknown method", n -> ((MethodInsnNode)real(use(n)).get(9)).name = "otherCycle");
        cases.put("missing cast", n -> replace(use(n), 10, new InsnNode(NOP)));
        cases.put("cast to unknown state", n -> ((TypeInsnNode)real(use(n)).get(10)).desc = "unknown/State");
        cases.put("flags zero", n -> replace(use(n), 11, new InsnNode(ICONST_0)));
        cases.put("flags clients only", n -> replace(use(n), 11, new InsnNode(ICONST_2)));
        cases.put("flags bypass", n -> replace(use(n), 11, new IntInsnNode(BIPUSH, 18)));
        cases.put("setBlock wrong opcode", n -> ((MethodInsnNode)real(use(n)).get(12)).setOpcode(INVOKESTATIC));
        cases.put("setBlock interface flag", n -> ((MethodInsnNode)real(use(n)).get(12)).itf = true);
        cases.put("setBlock unknown owner", n -> ((MethodInsnNode)real(use(n)).get(12)).owner = "unknown/Level");
        cases.put("setBlock unknown method", n -> ((MethodInsnNode)real(use(n)).get(12)).name = "otherWrite");
        cases.put("setBlock wrong result", n -> ((MethodInsnNode)real(use(n)).get(12)).desc = ((MethodInsnNode)real(use(n)).get(12)).desc.replace(")Z", ")V"));
        cases.put("setBlock result retained", n -> replace(use(n), 13, new InsnNode(DUP)));
        cases.put("write before guard", n -> use(n).instructions.insert(new MethodInsnNode(INVOKESTATIC, "unknown/World", "write", "()V", false)));
        cases.put("duplicate world write", n -> use(n).instructions.insert(real(use(n)).get(12), real(use(n)).get(12).clone(new HashMap<>())));
        cases.put("player inventory effect", n -> use(n).instructions.insert(new MethodInsnNode(INVOKESTATIC, "unknown/Player", "consume", "()V", false)));
        cases.put("async effect", n -> use(n).instructions.insert(new MethodInsnNode(INVOKESTATIC, "unknown/Executor", "schedule", "()V", false)));
        cases.put("extra helper", n -> add(n.get(BLOCK), ACC_PRIVATE | ACC_STATIC, "helper", "()V").instructions.add(new InsnNode(RETURN)));
        cases.put("missing state builder", n -> n.get(BLOCK).methods.remove(builder(n)));
        cases.put("duplicate use", n -> n.get(BLOCK).methods.add(use(n)));
        cases.put("native new bypass", n -> ((TypeInsnNode)real(method(n.get(ENTRY), "createBlock")).get(0)).desc = n.get(BLOCK).superName);
        cases.put("native constructor bypass", n -> { var c = real(method(n.get(ENTRY), "createBlock")); ((MethodInsnNode)c.get(c.size() - 2)).owner = n.get(BLOCK).superName; });
        cases.put("early registry access", n -> method(n.get(ENTRY), "<init>").instructions.insert(new InsnNode(NOP)));
        return cases.entrySet().stream().flatMap(e -> Stream.of(false, true).map(target -> DynamicTest.dynamicTest(
            (target ? "target " : "source ") + e.getKey(), () -> { var n = fixture(target); e.getValue().accept(n); rejected(n); })));
    }

    private static void rejected(Map<String,ClassNode> nodes) {
        var error = assertThrows(TransformationException.class, () -> BooleanToggleBlockContract.inspect(nodes));
        assertTrue(error.getMessage().startsWith("BOOLEAN_TOGGLE_BLOCK_UNSUPPORTED:"), error.getMessage());
    }

    private static Map<String,ClassNode> fixture(boolean target) {
        var a = new Api(target); var entry = entryFixture(target); var factory = real(method(entry, "createBlock"));
        ((TypeInsnNode)factory.get(0)).desc = BLOCK; ((MethodInsnNode)factory.get(factory.size() - 2)).owner = BLOCK;
        var block = new ClassNode(); block.name = BLOCK; block.superName = a.block; block.version = V1_8;
        block.access = ACC_PUBLIC | ACC_FINAL | ACC_SUPER;
        block.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "ACTIVE", "L" + a.booleanProperty + ";", null, null));
        var clinit = add(block, ACC_STATIC, "<clinit>", "()V"); clinit.instructions.add(new LdcInsnNode("active"));
        call(clinit, INVOKESTATIC, a.booleanProperty, target ? "m_61465_" : "func_177716_a", "(Ljava/lang/String;)L" + a.booleanProperty + ";");
        clinit.instructions.add(new FieldInsnNode(PUTSTATIC, BLOCK, "ACTIVE", "L" + a.booleanProperty + ";")); clinit.instructions.add(new InsnNode(RETURN));
        var ctor = add(block, ACC_PUBLIC, "<init>", "(L" + a.props + ";)V");
        ctor.instructions.add(new VarInsnNode(ALOAD, 0)); ctor.instructions.add(new VarInsnNode(ALOAD, 1));
        call(ctor, INVOKESPECIAL, a.block, "<init>", "(L" + a.props + ";)V");
        ctor.instructions.add(new VarInsnNode(ALOAD, 0)); ctor.instructions.add(new VarInsnNode(ALOAD, 0));
        ctor.instructions.add(new FieldInsnNode(GETFIELD, BLOCK, target ? "f_49792_" : "field_176227_L", "L" + a.container + ";"));
        call(ctor, INVOKEVIRTUAL, a.container, target ? "m_61090_" : "func_177621_b", "()L" + a.holder + ";");
        ctor.instructions.add(new TypeInsnNode(CHECKCAST, a.state)); ctor.instructions.add(new FieldInsnNode(GETSTATIC, BLOCK, "ACTIVE", "L" + a.booleanProperty + ";"));
        ctor.instructions.add(new FieldInsnNode(GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;"));
        call(ctor, INVOKEVIRTUAL, a.state, target ? "m_61124_" : "func_206870_a", "(L" + a.property + ";Ljava/lang/Comparable;)Ljava/lang/Object;");
        ctor.instructions.add(new TypeInsnNode(CHECKCAST, a.state)); call(ctor, INVOKEVIRTUAL, BLOCK, target ? "m_49959_" : "func_180632_j", "(L" + a.state + ";)V");
        ctor.instructions.add(new InsnNode(RETURN));
        var create = add(block, ACC_PROTECTED, target ? "m_7926_" : "func_206840_a", "(L" + a.builder + ";)V");
        create.instructions.add(new VarInsnNode(ALOAD, 1)); create.instructions.add(new InsnNode(ICONST_1)); create.instructions.add(new TypeInsnNode(ANEWARRAY, a.property));
        create.instructions.add(new InsnNode(DUP)); create.instructions.add(new InsnNode(ICONST_0));
        create.instructions.add(new FieldInsnNode(GETSTATIC, BLOCK, "ACTIVE", "L" + a.booleanProperty + ";")); create.instructions.add(new InsnNode(AASTORE));
        call(create, INVOKEVIRTUAL, a.builder, target ? "m_61104_" : "func_206894_a", "([L" + a.property + ";)L" + a.builder + ";");
        create.instructions.add(new InsnNode(POP)); create.instructions.add(new InsnNode(RETURN));
        var use = add(block, ACC_PUBLIC, target ? "m_6227_" : "func_225533_a_", "(L" + a.state + ";L" + a.world + ";L" + a.pos + ";L" + a.player + ";L" + a.hand + ";L" + a.hit + ";)L" + a.result + ";");
        use.maxLocals = 7;
        use.instructions.add(new VarInsnNode(ALOAD, 2)); use.instructions.add(new FieldInsnNode(GETFIELD, a.world, target ? "f_46443_" : "field_72995_K", "Z"));
        var server = new LabelNode(); use.instructions.add(new JumpInsnNode(IFEQ, server));
        use.instructions.add(new FieldInsnNode(GETSTATIC, a.result, "SUCCESS", "L" + a.result + ";")); use.instructions.add(new InsnNode(ARETURN));
        use.instructions.add(server); use.instructions.add(new VarInsnNode(ALOAD, 2)); use.instructions.add(new VarInsnNode(ALOAD, 3)); use.instructions.add(new VarInsnNode(ALOAD, 1));
        use.instructions.add(new FieldInsnNode(GETSTATIC, BLOCK, "ACTIVE", "L" + a.booleanProperty + ";"));
        call(use, INVOKEVIRTUAL, a.state, target ? "m_61122_" : "func_235896_a_", "(L" + a.property + ";)Ljava/lang/Object;");
        use.instructions.add(new TypeInsnNode(CHECKCAST, a.state)); use.instructions.add(new InsnNode(ICONST_3));
        call(use, INVOKEVIRTUAL, a.world, target ? "m_7731_" : "func_180501_a", "(L" + a.pos + ";L" + a.state + ";I)Z");
        use.instructions.add(new InsnNode(POP)); use.instructions.add(new FieldInsnNode(GETSTATIC, a.result, "CONSUME", "L" + a.result + ";")); use.instructions.add(new InsnNode(ARETURN));
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


    private static final class Api {
        final String block, props, state, container, holder, builder, booleanProperty, property, world, pos, player, hand, hit, result;
        Api(boolean target) {
            block = nativeBlock(target); props = target ? "net/minecraft/world/level/block/state/BlockBehaviour$Properties" : "net/minecraft/block/AbstractBlock$Properties";
            state = target ? "net/minecraft/world/level/block/state/BlockState" : "net/minecraft/block/BlockState";
            container = target ? "net/minecraft/world/level/block/state/StateDefinition" : "net/minecraft/state/StateContainer";
            holder = target ? "net/minecraft/world/level/block/state/StateHolder" : "net/minecraft/state/StateHolder"; builder = container + "$Builder";
            booleanProperty = target ? "net/minecraft/world/level/block/state/properties/BooleanProperty" : "net/minecraft/state/BooleanProperty";
            property = target ? "net/minecraft/world/level/block/state/properties/Property" : "net/minecraft/state/Property";
            world = target ? "net/minecraft/world/level/Level" : "net/minecraft/world/World";
            pos = target ? "net/minecraft/core/BlockPos" : "net/minecraft/util/math/BlockPos";
            player = target ? "net/minecraft/world/entity/player/Player" : "net/minecraft/entity/player/PlayerEntity";
            hand = target ? "net/minecraft/world/InteractionHand" : "net/minecraft/util/Hand";
            hit = target ? "net/minecraft/world/phys/BlockHitResult" : "net/minecraft/util/math/BlockRayTraceResult";
            result = target ? "net/minecraft/world/InteractionResult" : "net/minecraft/util/ActionResultType";
        }
    }

    private static String nativeBlock(boolean target) { return target ? "net/minecraft/world/level/block/Block" : "net/minecraft/block/Block"; }
    private static String object(boolean target) { return "L" + (target ? "net/minecraftforge/registries/RegistryObject" : "net/minecraftforge/fml/RegistryObject") + ";"; }
    private static MethodNode add(ClassNode n, int access, String name, String descriptor) { var m = new MethodNode(access, name, descriptor, null, null); m.maxStack = 16; m.maxLocals = 2; n.methods.add(m); return m; }
    private static void call(MethodNode m, int opcode, String owner, String name, String descriptor) { m.instructions.add(new MethodInsnNode(opcode, owner, name, descriptor, false)); }
    private static MethodNode method(ClassNode n, String name) { return n.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static MethodNode use(Map<String,ClassNode> n) { return n.get(BLOCK).methods.stream().filter(m -> Set.of("func_225533_a_", "m_6227_").contains(m.name)).findFirst().orElseThrow(); }
    private static MethodNode builder(Map<String,ClassNode> n) { return n.get(BLOCK).methods.stream().filter(m -> Set.of("func_206840_a", "m_7926_").contains(m.name)).findFirst().orElseThrow(); }
    private static List<AbstractInsnNode> real(MethodNode m) { return ClientConfigContract.code(m); }
    private static void replace(MethodNode m, int index, AbstractInsnNode replacement) { m.instructions.set(real(m).get(index), replacement); }
    private static Map<String,byte[]> bytes(Map<String,ClassNode> nodes) { var out = new TreeMap<String,byte[]>(); nodes.forEach((name, node) -> { var w = new ClassWriter(0); node.accept(w); out.put(name, w.toByteArray()); }); return out; }
    private static void assertBytes(Map<String,byte[]> expected, Map<String,ClassNode> nodes) { var actual = bytes(nodes); assertEquals(expected.keySet(), actual.keySet()); expected.forEach((name, value) -> assertArrayEquals(value, actual.get(name), name)); }
}

