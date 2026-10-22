package dev.legacybridge.converter.transformation.linkage;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.ApiIndex.ApiClass;
import dev.legacybridge.converter.mapping.source.ApiIndex.ApiMember;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Linkage-only synthetic null arguments are never executed or semantically approved as registration. */
class NetworkOpaqueReturnTest {
    private static final String OWN = "networkfixture/OpaqueCaller";
    private static final String OLD = "net/minecraftforge/fml/network/";
    private static final String NEW = "net/minecraftforge/network/";
    private static final OfflineLinkageVerifier.CallbackPolicy NETWORK = OfflineLinkageVerifier.CallbackPolicy.NETWORK_SNAPSHOT;
    private final OfflineLinkageVerifier verifier = new OfflineLinkageVerifier();

    @Test void exactPublicRegistrationMayDiscardItsActuallyPackagePrivateSourceAndTargetReturn() {
        for (String prefix : List.of(OLD, NEW)) {
            var api = api(prefix, ACC_SUPER, ACC_PUBLIC);
            assertEquals(0, api.getClass(opaque(prefix)).access() & ACC_PUBLIC);
            var result = verifier.verify(bytes(node(prefix)), api, NETWORK);
            assertEquals("PASSED_SCOPED", result.status());
            assertFalse((Boolean) result.toTree().get("classesExecuted"));
            assertTrue(result.limitations().stream().anyMatch(v -> v.contains("immediately discarded with POP")));
            assertEquals(0, api.getClass(opaque(prefix)).access() & ACC_PUBLIC, "No fabricated public flags");
        }
    }

    @Test void oldLogicAndLifecyclePoliciesDoNotGainOpaqueAccess() {
        for (var policy : List.of(OfflineLinkageVerifier.CallbackPolicy.LOGIC, OfflineLinkageVerifier.CallbackPolicy.LIFECYCLE))
            assertThrows(TransformationException.class, () -> verifier.verify(bytes(node(OLD)), api(OLD, ACC_SUPER, ACC_PUBLIC), policy));
    }

    @Test void resultCannotBeStoredDuplicatedCastOrReturnedEvenAsObject() {
        for (int opcode : List.of(ASTORE, DUP, CHECKCAST, ARETURN)) reject(node -> {
            var m = exercise(node); var pop = registration(node).getNext();
            AbstractInsnNode replacement = switch (opcode) {
                case ASTORE -> new VarInsnNode(ASTORE, 1);
                case CHECKCAST -> new TypeInsnNode(CHECKCAST, "java/lang/Object");
                default -> new InsnNode(opcode);
            };
            m.instructions.set(pop, replacement);
        });
    }

    @Test void evenANopBetweenInvocationAndDiscardIsOutsideTheExactReturnEnvelope() {
        reject(node -> exercise(node).instructions.insert(registration(node), new InsnNode(NOP)));
    }

    @Test void nonPublicStaticOrAbstractRegistrationCannotUseTheException() {
        for (int methodAccess : List.of(0, ACC_PUBLIC | ACC_STATIC, ACC_PUBLIC | ACC_ABSTRACT))
            assertThrows(TransformationException.class, () -> verifier.verify(bytes(node(OLD)), api(OLD, ACC_SUPER, methodAccess), NETWORK));
    }

    @Test void fakePublicOrInterfaceOpaqueApiDeclarationDoesNotSatisfyThePinnedContract() {
        for (int access : List.of(ACC_PUBLIC | ACC_SUPER, ACC_INTERFACE | ACC_ABSTRACT, ACC_PROTECTED | ACC_SUPER))
            assertThrows(TransformationException.class, () -> verifier.verify(bytes(node(OLD)), api(OLD, access, ACC_PUBLIC), NETWORK));
    }

    @Test void unreferencedOpaqueInnerMetadataWithoutAnActualDiscardCallIsRejected() {
        var n = node(OLD);
        var m = exercise(n); m.instructions.clear(); m.instructions.add(new InsnNode(RETURN));
        assertThrows(TransformationException.class, () -> verifier.verify(bytes(n), api(OLD, ACC_SUPER, ACC_PUBLIC), NETWORK));
    }

    @Test void anApprovedCallCannotAuthorizeOpaqueFieldOrMethodDescriptors() {
        reject(n -> n.fields.add(new FieldNode(ACC_PRIVATE, "escape", "L" + opaque(OLD) + ";", null, null)));
        reject(n -> {
            var m = new MethodNode(ACC_PUBLIC | ACC_STATIC, "escape", "(L" + opaque(OLD) + ";)V", null, null);
            m.instructions.add(new InsnNode(RETURN)); n.methods.add(m);
        });
    }

    @Test void anApprovedCallCannotAuthorizeOpaqueGenericSignatures() {
        reject(n -> n.fields.add(new FieldNode(ACC_PRIVATE, "escape", "Ljava/util/List;", "Ljava/util/List<L" + opaque(OLD) + ";>;", null)));
    }

    @Test void anApprovedCallCannotAuthorizeOpaqueClassLiteralsCastsArraysOrNew() {
        for (AbstractInsnNode value : List.of(new LdcInsnNode(Type.getObjectType(opaque(OLD))),
                new TypeInsnNode(CHECKCAST, opaque(OLD)), new TypeInsnNode(ANEWARRAY, opaque(OLD)), new TypeInsnNode(org.objectweb.asm.Opcodes.NEW, opaque(OLD)))) {
            reject(n -> exercise(n).instructions.insertBefore(exercise(n).instructions.getLast(), value));
        }
    }

    @Test void anApprovedCallCannotAuthorizeOpaqueStandaloneHandles() {
        reject(n -> exercise(n).instructions.insertBefore(exercise(n).instructions.getLast(),
                new LdcInsnNode(new Handle(H_INVOKEVIRTUAL, channel(OLD), "registerMessage", descriptor(OLD), false))));
    }

    @Test void alteredInnerMetadataDoesNotInheritTheInertMetadataException() {
        reject(n -> n.innerClasses.get(0).outerName = "java/lang/Object");
        reject(n -> n.innerClasses.get(0).innerName = "SomethingElse");
        reject(n -> n.innerClasses.get(0).access = ACC_PUBLIC);
    }

    @Test void wrongOverloadOwnerOrInvocationKindDoesNotReceiveOpaquePermission() {
        reject(n -> registration(n).desc = descriptor(OLD).replace("Ljava/util/Optional;", ""));
        reject(n -> registration(n).owner = "unknown/SimpleChannel");
        reject(n -> registration(n).setOpcode(INVOKESTATIC));
        reject(n -> registration(n).itf = true);
    }

    @Test void oneApprovedClassCannotAuthorizeAnotherClassOpaqueMetadata() {
        var first = node(OLD);
        var second = new ClassNode(); second.version = V1_8; second.access = ACC_PUBLIC;
        second.name = "networkfixture/Other"; second.superName = "java/lang/Object";
        second.innerClasses.add(new InnerClassNode(opaque(OLD), outer(OLD), "MessageHandler", 0));
        var classes = new java.util.TreeMap<>(bytes(first)); classes.putAll(bytes(second));
        assertThrows(TransformationException.class, () -> verifier.verify(classes, api(OLD, ACC_SUPER, ACC_PUBLIC), NETWORK));
    }

    private void reject(Consumer<ClassNode> mutation) {
        var n = node(OLD); mutation.accept(n);
        assertThrows(TransformationException.class, () -> verifier.verify(bytes(n), api(OLD, ACC_SUPER, ACC_PUBLIC), NETWORK));
    }

    private static ClassNode node(String prefix) {
        var n = new ClassNode(); n.version = V1_8; n.access = ACC_PUBLIC | ACC_SUPER;
        n.name = OWN; n.superName = "java/lang/Object";
        n.innerClasses.add(new InnerClassNode(opaque(prefix), outer(prefix), "MessageHandler", 0));
        var m = new MethodNode(ACC_PUBLIC | ACC_STATIC, "exercise", "(L" + channel(prefix) + ";)V", null, null);
        m.instructions.add(new VarInsnNode(ALOAD, 0));
        m.instructions.add(new InsnNode(ICONST_0));
        m.instructions.add(new LdcInsnNode(Type.getObjectType(OWN)));
        for (int i = 0; i < 4; i++) m.instructions.add(new InsnNode(ACONST_NULL));
        m.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, channel(prefix), "registerMessage", descriptor(prefix), false));
        m.instructions.add(new InsnNode(POP)); m.instructions.add(new InsnNode(RETURN));
        m.maxStack = 7; m.maxLocals = 2; n.methods.add(m); return n;
    }

    private static ApiIndex api(String prefix, int opaqueAccess, int methodAccess) {
        return new ApiIndex(Map.of(
                channel(prefix), new ApiClass(channel(prefix), ACC_PUBLIC | ACC_SUPER, "java/lang/Object", List.of(),
                        List.of(new ApiMember("registerMessage", descriptor(prefix), methodAccess)), List.of()),
                outer(prefix), new ApiClass(outer(prefix), ACC_PUBLIC | ACC_SUPER, "java/lang/Object", List.of(), List.of(), List.of()),
                opaque(prefix), new ApiClass(opaque(prefix), opaqueAccess, "java/lang/Object", List.of(), List.of(), List.of())));
    }

    private static Map<String, byte[]> bytes(ClassNode n) {
        // Explicit max values avoid any runtime/common-superclass lookup even for malformed mutations.
        var writer = new ClassWriter(0); n.accept(writer); return Map.of(n.name + ".class", writer.toByteArray());
    }
    private static MethodNode exercise(ClassNode n) { return n.methods.get(0); }
    private static MethodInsnNode registration(ClassNode n) {
        for (var instruction : exercise(n).instructions) if (instruction instanceof MethodInsnNode call) return call;
        throw new AssertionError("Missing synthetic registration");
    }
    private static String channel(String prefix) { return prefix + "simple/SimpleChannel"; }
    private static String outer(String prefix) { return prefix + "simple/IndexedMessageCodec"; }
    private static String opaque(String prefix) { return outer(prefix) + "$MessageHandler"; }
    private static String descriptor(String prefix) {
        return "(ILjava/lang/Class;Ljava/util/function/BiConsumer;Ljava/util/function/Function;Ljava/util/function/BiConsumer;Ljava/util/Optional;)L" + opaque(prefix) + ";";
    }
}
