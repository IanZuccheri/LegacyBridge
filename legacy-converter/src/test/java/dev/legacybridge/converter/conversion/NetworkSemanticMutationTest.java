package dev.legacybridge.converter.conversion;

import dev.legacybridge.converter.transformation.LegacyReferenceScanner;
import dev.legacybridge.converter.transformation.TransformationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Semantic-name mutation tests of the actual, inert Java 8 fixture's admission gate.
 * No mapping inputs, original class loading, output publication or network runtime.
 * Mutated ASM trees exercise the recipe boundary, not claims that a modified mod
 * is valid bytecode or that a rejected packet was delivered by a native transport.
 */
@EnabledIfSystemProperty(named = "legacybridge.verifyNetworkEnvironment", matches = "true")
class NetworkSemanticMutationTest {
    private static final String OWNER = "dev/legacybridge/fixture/network/NetworkSnapshot";
    private static final String MESSAGE = "dev/legacybridge/fixture/network/SnapshotMessage";
    private static final String CONTEXT = "net/minecraftforge/fml/network/NetworkEvent$Context";
    private static final String DIRECTION = "net/minecraftforge/fml/network/NetworkDirection";

    @Test void unchangedGenuineSourceIsAdmittedWithoutChangingOriginalBytesOrTrees() throws Exception {
        byte[] original = Files.readAllBytes(fixture());
        var nodes = fresh();
        var before = NetworkSnapshotRecipe.fingerprints(nodes);
        var report = validate(nodes);
        assertEquals("PLAY_TO_CLIENT", report.get("direction"));
        assertEquals(8, report.get("payloadBytes"));
        assertEquals("NOT_RUN", report.get("runtimeValidation"));
        assertEquals(20, before.size());
        assertEquals(before, NetworkSnapshotRecipe.fingerprints(nodes));
        assertArrayEquals(original, Files.readAllBytes(fixture()));
    }

    @Test void genuineSourceDebugChangesDoNotGrantAnyNewExecutableBehavior() throws Exception {
        var nodes = fresh();
        var before = NetworkSnapshotRecipe.fingerprints(nodes);
        for (var node : nodes.values()) {
            node.sourceFile = "DebugOnly.java";
            for (var method : node.methods) for (var instruction : method.instructions) {
                if (instruction instanceof LineNumberNode line) line.line += 100;
            }
        }
        assertEquals(before, NetworkSnapshotRecipe.fingerprints(nodes));
        assertEquals("PLAY_TO_CLIENT", validate(nodes).get("direction"));
    }

    @Test void wildcardProtocolPredicateCannotReplaceLiteralEquality() throws Exception {
        rejected(nodes -> {
            var m = method(nodes, "accepts");
            m.instructions.clear();
            m.instructions.add(new InsnNode(Opcodes.ICONST_1));
            m.instructions.add(new InsnNode(Opcodes.IRETURN));
        });
    }

    @Test void changingAdvertisedProtocolRequiresNewReview() throws Exception {
        rejected(nodes -> literal(method(nodes, "protocol"), "1").cst = "*");
    }

    @Test void c2sDirectionIsNotAnS2cRecipe() throws Exception {
        rejected(nodes -> direction(method(nodes, "<init>")).name = "PLAY_TO_SERVER");
    }

    @Test void discriminator256CannotAliasReviewedUnsignedByteZero() throws Exception {
        rejected(nodes -> {
            var m = method(nodes, "<init>");
            var id = unique(m, i -> i.getOpcode() == Opcodes.ICONST_0);
            m.instructions.set(id, new IntInsnNode(Opcodes.SIPUSH, 256));
        });
    }

    @Test void secondRegistrationCannotOverwriteTheReviewedIdOrMessageClass() throws Exception {
        rejected(nodes -> {
            var m = method(nodes, "<init>");
            var code = executable(m);
            int start = -1;
            for (int i = 0; i < code.size(); i++) if (code.get(i) instanceof FieldInsnNode f && f.name.equals("CHANNEL")) start = i;
            assertTrue(start > 0);
            var extra = new InsnList();
            for (int i = start; i < code.size() - 1; i++) extra.add(code.get(i).clone(Map.of()));
            m.instructions.insertBefore(unique(m, i -> i.getOpcode() == Opcodes.RETURN), extra);
        });
    }

    @Test void emptyExpectedDirectionCannotReplaceExplicitS2cOptional() throws Exception {
        rejected(nodes -> {
            var m = method(nodes, "<init>");
            m.instructions.remove(direction(m));
            var optional = call(m, "java/util/Optional", "of");
            optional.name = "empty";
            optional.desc = "()Ljava/util/Optional;";
        });
    }

    @Test void fiveArgumentRegistrationDoesNotInheritSixArgumentDirectionApproval() throws Exception {
        rejected(nodes -> {
            var m = method(nodes, "<init>");
            m.instructions.remove(direction(m));
            m.instructions.remove(call(m, "java/util/Optional", "of"));
            var registration = call(m, "net/minecraftforge/fml/network/simple/SimpleChannel", "registerMessage");
            assertTrue(registration.desc.contains("Ljava/util/Optional;"));
            registration.desc = registration.desc.replace("Ljava/util/Optional;", "");
        });
    }

    @Test void eightBytePrefixAcceptanceCannotIgnoreTrailingPayloadBytes() throws Exception {
        rejected(nodes -> {
            var guard = (JumpInsnNode) unique(method(nodes, "decode"), i -> i instanceof JumpInsnNode);
            assertEquals(Opcodes.IF_ICMPEQ, guard.getOpcode());
            guard.setOpcode(Opcodes.IF_ICMPGE);
        });
    }

    @Test void encoderValueAndSequenceOrderCannotBeSwapped() throws Exception {
        rejected(nodes -> {
            var m = method(nodes, "encode");
            var sequence = field(m, MESSAGE, "sequence");
            var value = field(m, MESSAGE, "value");
            sequence.name = "value";
            value.name = "sequence";
        });
    }

    @Test void negativeSnapshotValueGuardCannotBeInverted() throws Exception {
        rejected(nodes -> ((JumpInsnNode) unique(method(nodes, "apply"), i -> i.getOpcode() == Opcodes.IFLT)).setOpcode(Opcodes.IFGE));
    }

    @Test void upperSnapshotValueBoundCannotBeIncreased() throws Exception {
        rejected(nodes -> ((IntInsnNode) unique(method(nodes, "apply"), i -> i instanceof IntInsnNode n && n.operand == 1000)).operand = 1001);
    }

    @Test void decoderWrongLengthCannotManufactureAValidFirstSnapshot() throws Exception {
        rejected(nodes -> {
            var m = method(nodes, "decode");
            var sentinels = executable(m).stream().filter(i -> i.getOpcode() == Opcodes.ICONST_M1).toList();
            assertEquals(2, sentinels.size());
            m.instructions.set(sentinels.get(0), new InsnNode(Opcodes.ICONST_1));
            m.instructions.set(sentinels.get(1), new InsnNode(Opcodes.ICONST_0));
        });
    }

    @Test void handlerCannotApplyStateDirectlyInsteadOfEnqueueing() throws Exception {
        rejected(nodes -> {
            var m = method(nodes, "handle");
            m.instructions.clear();
            m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            m.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/function/Supplier", "get", "()Ljava/lang/Object;", true));
            m.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, CONTEXT));
            m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CONTEXT, "getNetworkManager", "()Lnet/minecraft/network/NetworkManager;", false));
            m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "apply", "(L" + MESSAGE + ";Lnet/minecraft/network/NetworkManager;)V", false));
            m.instructions.add(new InsnNode(Opcodes.RETURN));
        });
    }

    @Test void queuedTaskCannotAcceptClosedConnectionByInvertingLivenessCheck() throws Exception {
        rejected(nodes -> {
            var code = executable(method(nodes, "apply"));
            assertInstanceOf(MethodInsnNode.class, code.get(1));
            assertEquals("net/minecraft/network/NetworkManager", ((MethodInsnNode) code.get(1)).owner);
            var guard = assertInstanceOf(JumpInsnNode.class, code.get(2));
            assertEquals(Opcodes.IFNE, guard.getOpcode());
            guard.setOpcode(Opcodes.IFEQ);
        });
    }

    @Test void aDifferentStillLiveConnectionCannotReplaceTheSelectedSession() throws Exception {
        rejected(nodes -> ((JumpInsnNode) unique(method(nodes, "apply"), i -> i.getOpcode() == Opcodes.IF_ACMPEQ)).setOpcode(Opcodes.IF_ACMPNE));
    }

    @Test void duplicateSequenceCannotReplaceStrictNextSequence() throws Exception {
        rejected(nodes -> {
            var m = method(nodes, "apply");
            var code = executable(m);
            int index = sequenceIncrement(code);
            assertEquals(Opcodes.ICONST_1, code.get(index + 1).getOpcode());
            m.instructions.set(code.get(index + 1), new InsnNode(Opcodes.ICONST_0));
        });
    }

    @Test void sequenceGapCannotPassByWeakeningEqualityToGreaterOrEqual() throws Exception {
        rejected(nodes -> {
            var code = executable(method(nodes, "apply"));
            var guard = assertInstanceOf(JumpInsnNode.class, code.get(sequenceIncrement(code) + 3));
            assertEquals(Opcodes.IF_ICMPEQ, guard.getOpcode());
            guard.setOpcode(Opcodes.IF_ICMPGE);
        });
    }

    @Test void unknownExternalFieldIsNotHarmlessUnusedCompatibility() throws Exception {
        rejected(nodes -> nodes.get(OWNER).fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "unreviewed", "Lunknown/External;", null, null)));
    }

    @Test void extraOwnedMethodCannotObtainImplicitBodyApproval() throws Exception {
        rejected(nodes -> {
            var extra = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "unreviewed", "()V", null, null);
            extra.instructions.add(new InsnNode(Opcodes.RETURN));
            nodes.get(OWNER).methods.add(extra);
        });
    }

    @Test void modAnnotationOnAHelperMethodCannotCreateACompetingEntrypoint() throws Exception {
        rejected(nodes -> {
            var annotation = new AnnotationNode("Lnet/minecraftforge/fml/common/Mod;");
            annotation.values = List.of("value", "legacybridge_network");
            method(nodes, "protocol").visibleAnnotations = new ArrayList<>(List.of(annotation));
        });
    }

    private static int sequenceIncrement(List<AbstractInsnNode> code) {
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i + 3 < code.size(); i++) {
            if (code.get(i) instanceof FieldInsnNode f && f.owner.equals(OWNER) && f.name.equals("lastSequence")
                    && f.getOpcode() == Opcodes.GETSTATIC && code.get(i + 2).getOpcode() == Opcodes.IADD) matches.add(i);
        }
        assertEquals(1, matches.size(), "Exact reviewed sequence increment must be present");
        return matches.get(0);
    }

    private static void rejected(Consumer<Map<String, ClassNode>> mutation) throws IOException {
        var nodes = fresh();
        var before = NetworkSnapshotRecipe.fingerprints(nodes);
        mutation.accept(nodes);
        assertNotEquals(before, NetworkSnapshotRecipe.fingerprints(nodes), "Mutation must affect executable recipe input");
        var failure = assertThrows(TransformationException.class, () -> validate(nodes));
        assertTrue(failure.getMessage().startsWith("NETWORK_RECIPE_UNSUPPORTED:"), failure.getMessage());
    }

    private static Map<String, Object> validate(Map<String, ClassNode> nodes) {
        return NetworkSnapshotRecipe.validate(nodes, "legacybridge_network", "1.0.0");
    }

    private static Map<String, ClassNode> fresh() throws IOException {
        Path fixture = fixture();
        assertTrue(Files.size(fixture) < 2 * 1024 * 1024);
        Map<String, byte[]> classes = new TreeMap<>();
        try (var zip = new ZipFile(fixture.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                assertTrue(classes.size() < 8);
                try (var input = zip.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes(1_048_577);
                    assertTrue(bytes.length <= 1_048_576);
                    assertNull(classes.put(entry.getName(), bytes));
                }
            }
        }
        var nodes = new LegacyReferenceScanner().scan(classes).classNodes();
        assertEquals(2, nodes.size());
        assertEquals("PLAY_TO_CLIENT", validate(nodes).get("direction"), "An invalid baseline cannot make mutation tests vacuously pass");
        return nodes;
    }

    private static Path fixture() {
        return Path.of(System.getProperty("legacybridge.fixtureRoot"), "network-snapshot-1.16.5/build/libs/legacybridge-network-1.16.5-1.0.0.jar");
    }

    private static MethodNode method(Map<String, ClassNode> nodes, String name) {
        var matches = nodes.get(OWNER).methods.stream().filter(m -> m.name.equals(name)).toList();
        assertEquals(1, matches.size(), "Exact reviewed method: " + name);
        return matches.get(0);
    }

    private static List<AbstractInsnNode> executable(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (var instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }

    private static AbstractInsnNode unique(MethodNode method, Predicate<AbstractInsnNode> predicate) {
        var matches = executable(method).stream().filter(predicate).toList();
        assertEquals(1, matches.size(), "Exact instruction in " + method.name);
        return matches.get(0);
    }

    private static MethodInsnNode call(MethodNode method, String owner, String name) {
        return (MethodInsnNode) unique(method, i -> i instanceof MethodInsnNode m && m.owner.equals(owner) && m.name.equals(name));
    }

    private static FieldInsnNode field(MethodNode method, String owner, String name) {
        return (FieldInsnNode) unique(method, i -> i instanceof FieldInsnNode f && f.owner.equals(owner) && f.name.equals(name));
    }

    private static FieldInsnNode direction(MethodNode method) { return field(method, DIRECTION, "PLAY_TO_CLIENT"); }
    private static LdcInsnNode literal(MethodNode method, String text) {
        return (LdcInsnNode) unique(method, i -> i instanceof LdcInsnNode literal && text.equals(literal.cst));
    }
}
