package dev.legacybridge.converter.transformation.linkage;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.ApiIndex.ApiClass;
import dev.legacybridge.converter.transformation.ReviewedNetworkLambda;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

/** Synthetic linkage/SAM checks only, never proof of protocol effects or actual packet delivery. */
class NetworkOfflineLinkageTest {
    private static final String OWN = "networkfixture/Callbacks";
    private static final String MESSAGE = "networkfixture/SnapshotMessage";
    private static final String BUFFER_OLD = "net/minecraft/network/PacketBuffer";
    private static final String BUFFER_NEW = "net/minecraft/network/FriendlyByteBuf";
    private static final String CONTEXT_OLD = "net/minecraftforge/fml/network/NetworkEvent$Context";
    private static final String CONTEXT_NEW = "net/minecraftforge/network/NetworkEvent$Context";
    private static final String PLAYER_OLD = "net/minecraft/entity/player/ServerPlayerEntity";
    private static final String PLAYER_NEW = "net/minecraft/server/level/ServerPlayer";
    private static final String SUPPLIER = "java/util/function/Supplier";
    private static final Handle METAFACTORY = new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
    private static final OfflineLinkageVerifier.CallbackPolicy NETWORK = OfflineLinkageVerifier.CallbackPolicy.NETWORK_SNAPSHOT;
    private final OfflineLinkageVerifier verifier = new OfflineLinkageVerifier();

    private record Lambda(String contract, String name, String sam, String instantiated, String captures) {
        String factory() { return "(" + captures + ")L" + contract + ";"; }
        String implementation() { return "(" + captures + instantiated.substring(1); }
        Lambda instantiated(String descriptor) { return new Lambda(contract, name, sam, descriptor, captures); }
        Lambda captures(String descriptor) { return new Lambda(contract, name, sam, instantiated, descriptor); }
        Lambda sam(String descriptor) { return new Lambda(contract, name, descriptor, instantiated, captures); }
    }

    @Test
    void acceptsTheExactSourceAndTargetEncoderDecoderHandlerAndQueuedCaptureSamsWithoutLoadingClasses() {
        for (String buffer : List.of(BUFFER_OLD, BUFFER_NEW)) {
            for (Lambda lambda : List.of(encoder(buffer), decoder(buffer), consumer())) {
                var result = verify(lambda);
                assertEquals("PASSED_SCOPED", result.status());
                assertEquals(1, result.lambdaSiteCount());
                assertFalse((Boolean) result.toTree().get("classesExecuted"));
                assertTrue(result.limitations().stream().anyMatch(value -> value.contains("protocol safety")));
            }
        }
        for (String context : List.of(CONTEXT_OLD, CONTEXT_NEW)) assertEquals(1, verify(runnable(context)).lambdaSiteCount());
    }

    @Test
    void acceptsOnlyZeroCaptureStringProtocolSupplierAndStringPredicate() {
        assertEquals(1, verify(protocol()).lambdaSiteCount());
        assertEquals(1, verify(predicate()).lambdaSiteCount());
        rejected(protocol().captures("Ljava/lang/String;"));
        rejected(predicate().captures("Ljava/lang/String;"));
        rejected(protocol().instantiated("()Ljava/lang/Object;"));
        rejected(predicate().instantiated("(Ljava/lang/Object;)Z"));
    }

    @Test
    void permitsPlayerTargetSupplierOnlyWithExactlyOneSamePlayerCaptureAndReturn() {
        for (String player : List.of(PLAYER_OLD, PLAYER_NEW)) {
            Lambda lambda = player(player);
            assertEquals(1, verify(lambda).lambdaSiteCount());
            rejected(lambda.captures(""));
            rejected(lambda.captures(object(player) + object(player)));
            rejected(lambda.instantiated("()Ljava/lang/Object;"));
            rejected(lambda.instantiated("()" + object(player.equals(PLAYER_OLD) ? PLAYER_NEW : PLAYER_OLD)));
        }
        rejected(new Lambda(SUPPLIER, "get", "()Ljava/lang/Object;", "()" + object(MESSAGE), object(MESSAGE)));
        rejected(new Lambda(SUPPLIER, "get", "()Ljava/lang/Object;", "()" + object(CONTEXT_NEW), object(CONTEXT_NEW)));
    }

    @Test
    void networkingCallbacksDoNotWidenEitherExistingPolicy() {
        for (Lambda lambda : List.of(encoder(BUFFER_NEW), decoder(BUFFER_NEW), consumer(), predicate(), runnable(CONTEXT_NEW))) {
            Map<String, byte[]> classes = classes(lambda);
            assertThrows(TransformationException.class, () -> verifier.verify(classes, api()));
            assertThrows(TransformationException.class, () -> verifier.verify(classes, api(), false));
            assertThrows(TransformationException.class, () -> verifier.verify(classes, api(), true));
            assertThrows(TransformationException.class, () -> verifier.verify(classes, api(), OfflineLinkageVerifier.CallbackPolicy.LOGIC));
            assertThrows(TransformationException.class, () -> verifier.verify(classes, api(), OfflineLinkageVerifier.CallbackPolicy.LIFECYCLE));
        }
    }

    @Test
    void oldSupplierCaptureBehaviorRemainsAvailableOnlyInItsExistingPolicy() {
        Lambda lambda = protocol().captures("Ljava/lang/String;");
        Map<String, byte[]> classes = classes(lambda);
        assertEquals(1, verifier.verify(classes, api()).lambdaSiteCount());
        assertEquals(1, verifier.verify(classes, api(), true).lambdaSiteCount());
        assertThrows(TransformationException.class, () -> verifier.verify(classes, api(), NETWORK));
    }

    @Test
    void networkModeDoesNotImplicitlyInheritIntUnaryOrLifecycleRunnablePermission() {
        Lambda operator = new Lambda("java/util/function/IntUnaryOperator", "applyAsInt", "(I)I", "(I)I", "");
        Map<String, byte[]> classes = classes(operator);
        assertEquals(1, verifier.verify(classes, api()).lambdaSiteCount());
        assertThrows(TransformationException.class, () -> verifier.verify(classes, api(), NETWORK));
        Lambda noCaptureWork = new Lambda("java/lang/Runnable", "run", "()V", "()V", "");
        assertEquals(1, verifier.verify(classes(noCaptureWork), api(), true).lambdaSiteCount());
        rejected(noCaptureWork);
    }

    @Test
    void rejectsNullExplicitPolicyInsteadOfFallingBackToAnExistingPolicy() {
        var error = assertThrows(TransformationException.class, () -> verifier.verify(classes(protocol()), api(),
                (OfflineLinkageVerifier.CallbackPolicy) null));
        assertTrue(error.getMessage().contains("CALLBACK_POLICY_REQUIRED"), error.getMessage());
    }

    @Test
    void rejectsErasedObjectArgumentsEvenWhenTheFunctionalInterfaceErasureMatches() {
        rejected(encoder(BUFFER_NEW).instantiated("(Ljava/lang/Object;" + object(BUFFER_NEW) + ")V"));
        rejected(encoder(BUFFER_NEW).instantiated("(" + object(MESSAGE) + "Ljava/lang/Object;)V"));
        rejected(decoder(BUFFER_NEW).instantiated("(Ljava/lang/Object;)" + object(MESSAGE)));
        rejected(decoder(BUFFER_NEW).instantiated("(" + object(BUFFER_NEW) + ")Ljava/lang/Object;"));
        rejected(consumer().instantiated("(" + object(MESSAGE) + "Ljava/lang/Object;)V"));
    }

    @Test
    void rejectsWrongSamNamesErasuresAndReturnKinds() {
        Lambda good = encoder(BUFFER_NEW);
        rejected(new Lambda(good.contract(), "apply", good.sam(), good.instantiated(), ""));
        rejected(good.sam("(Ljava/lang/Object;)V"));
        rejected(good.sam("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));
        rejected(good.instantiated("(" + object(MESSAGE) + object(BUFFER_NEW) + ")I"));
        rejected(decoder(BUFFER_NEW).sam("(Ljava/lang/Object;)V"));
        rejected(predicate().instantiated("(Ljava/lang/String;)Ljava/lang/Boolean;"));
    }

    @Test
    void rejectsReversedEncoderOrderExternalDtoAndArrayDto() {
        rejected(encoder(BUFFER_NEW).instantiated("(" + object(BUFFER_NEW) + object(MESSAGE) + ")V"));
        rejected(encoder(BUFFER_NEW).instantiated("(Ljava/lang/String;" + object(BUFFER_NEW) + ")V"));
        rejected(decoder(BUFFER_NEW).instantiated("(" + object(BUFFER_NEW) + ")Ljava/lang/String;"));
        rejected(encoder(BUFFER_NEW).instantiated("([" + object(MESSAGE) + object(BUFFER_NEW) + ")V"));
        rejected(decoder(BUFFER_NEW).instantiated("(" + object(BUFFER_NEW) + ")[" + object(MESSAGE)));
    }

    @Test
    void rejectsUnreviewedDirectContextAndBufferSubstitutes() {
        rejected(encoder(BUFFER_NEW).instantiated("(" + object(MESSAGE) + "Ljava/nio/ByteBuffer;)V"));
        rejected(decoder(BUFFER_NEW).instantiated("(Ljava/nio/ByteBuffer;)" + object(MESSAGE)));
        rejected(consumer().instantiated("(" + object(MESSAGE) + object(CONTEXT_NEW) + ")V"));
    }

    @Test
    void rejectsCapturesOnCodecsAndConsumers() {
        for (Lambda lambda : List.of(encoder(BUFFER_NEW), decoder(BUFFER_NEW), consumer())) {
            rejected(lambda.captures(object(MESSAGE)));
            rejected(lambda.captures(object(CONTEXT_NEW)));
        }
    }

    @Test
    void rejectsWrongQueuedRunnableCaptureCountOrderAndTypes() {
        Lambda lambda = runnable(CONTEXT_NEW);
        for (String captures : List.of("", object(MESSAGE), object(CONTEXT_NEW) + object(MESSAGE),
                object(MESSAGE) + object(SUPPLIER), object(MESSAGE) + object(BUFFER_NEW),
                object(MESSAGE) + object(CONTEXT_NEW) + "I", "Ljava/lang/Object;" + object(CONTEXT_NEW)))
            rejected(lambda.captures(captures));
        rejected(lambda.instantiated("(I)V"));
    }

    @Test
    void rejectsImplementationArityAndReturnAdaptationEvenForAnAcceptedSamShape() {
        Lambda lambda = decoder(BUFFER_NEW);
        rejected(classes(lambda, "(" + object(BUFFER_NEW) + ")Ljava/lang/Object;", H_INVOKESTATIC, false, ACC_PRIVATE | ACC_STATIC), "NETWORK_CALLBACK_ADAPTATION_UNSUPPORTED");
        rejected(classes(lambda, "(" + object(BUFFER_NEW) + "I)" + object(MESSAGE), H_INVOKESTATIC, false, ACC_PRIVATE | ACC_STATIC), "NETWORK_CALLBACK_ADAPTATION_UNSUPPORTED");
        rejected(classes(predicate(), "(Ljava/lang/String;)Ljava/lang/Boolean;", H_INVOKESTATIC, false, ACC_PRIVATE | ACC_STATIC), "NETWORK_CALLBACK_ADAPTATION_UNSUPPORTED");
    }

    @Test
    void rejectsImplementationParameterWideningAndWrongCaptureProvenanceTypes() {
        Lambda encoder = encoder(BUFFER_NEW);
        rejected(classes(encoder, "(Ljava/lang/Object;" + object(BUFFER_NEW) + ")V", H_INVOKESTATIC, false, ACC_PRIVATE | ACC_STATIC), "NETWORK_ARGUMENT_TYPE");
        Lambda work = runnable(CONTEXT_NEW);
        rejected(classes(work, "(" + object(CONTEXT_NEW) + object(MESSAGE) + ")V", H_INVOKESTATIC, false, ACC_PRIVATE | ACC_STATIC), "NETWORK_CAPTURE_TYPE");
        Lambda destination = player(PLAYER_NEW);
        rejected(classes(destination, "(Ljava/lang/Object;)" + object(PLAYER_NEW), H_INVOKESTATIC, false, ACC_PRIVATE | ACC_STATIC), "NETWORK_CAPTURE_TYPE");
    }

    @Test
    void rejectsVirtualReceiverHandleAndStaticHandleToInstanceMethod() {
        rejected(classes(encoder(BUFFER_NEW), encoder(BUFFER_NEW).implementation(), H_INVOKEVIRTUAL, false, ACC_PUBLIC), "NETWORK_IMPLEMENTATION_UNSUPPORTED");
        rejected(classes(encoder(BUFFER_NEW), encoder(BUFFER_NEW).implementation(), H_INVOKESTATIC, false, ACC_PUBLIC), "NETWORK_IMPLEMENTATION_UNSUPPORTED");
    }

    @Test
    void rejectsMissingImplementationRatherThanAcceptingItsDescriptorAlone() {
        Map<String, byte[]> classes = classes(encoder(BUFFER_NEW));
        classes.put(OWN, type(OWN, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, cw -> factory(cw, encoder(BUFFER_NEW),
                new Handle(H_INVOKESTATIC, OWN, "missing", encoder(BUFFER_NEW).implementation(), false), METAFACTORY)));
        rejected(classes, "MISSING_METHOD");
    }

    @Test
    void rejectsExternalStaticImplementationEvenWhenItsExactSamAbiMatches() {
        Lambda lambda = predicate();
        Map<String, byte[]> classes = classes(lambda);
        classes.put(OWN, type(OWN, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, cw -> factory(cw, lambda,
                new Handle(H_INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z", false), METAFACTORY)));
        rejected(classes, "NETWORK_IMPLEMENTATION_UNSUPPORTED");
    }

    @Test
    void rejectsAbstractOrInterfaceMessageCandidatesEvenWhenOwned() {
        for (int access : List.of(ACC_PUBLIC | ACC_ABSTRACT | ACC_SUPER, ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE)) {
            Map<String, byte[]> classes = classes(encoder(BUFFER_NEW));
            classes.put(MESSAGE, type(MESSAGE, access, cw -> { }));
            rejected(classes, "NETWORK_MESSAGE_KIND_UNSUPPORTED");
        }
    }

    @Test
    void rejectsAlternateMetafactoryAndStandaloneHandleConstants() {
        Lambda lambda = protocol();
        Map<String, byte[]> classes = classes(lambda);
        classes.put(OWN, type(OWN, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, cw -> {
            method(cw, ACC_PRIVATE | ACC_STATIC, "impl", lambda.implementation(), NetworkOfflineLinkageTest::defaultReturn);
            factory(cw, lambda, new Handle(H_INVOKESTATIC, OWN, "impl", lambda.implementation(), false),
                    new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "altMetafactory",
                            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false));
        }));
        rejected(classes, "BOOTSTRAP_UNSUPPORTED");
        classes.put(OWN, type(OWN, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, cw -> {
            method(cw, ACC_PUBLIC | ACC_STATIC, "freeHandle", "()Ljava/lang/Object;", (mv, descriptor) -> {
                mv.visitLdcInsn(new Handle(H_INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z", false));
                mv.visitInsn(ARETURN);
            });
        }));
        rejected(classes, "STANDALONE_METHOD_HANDLE_UNSUPPORTED");
    }

    @Test
    void malformedSharedSamHelperInputsReturnFalseAndCannotAuthorizeAnything() {
        Set<String> names = Set.of(MESSAGE);
        assertFalse(ReviewedNetworkLambda.accepts(null, "get", Type.getMethodType("()Ljava/lang/Object;"), Type.getMethodType("()Ljava/lang/String;"), Type.getMethodType("()Ljava/util/function/Supplier;"), names));
        assertFalse(ReviewedNetworkLambda.accepts(SUPPLIER, "get", Type.INT_TYPE, Type.getMethodType("()Ljava/lang/String;"), Type.getMethodType("()Ljava/util/function/Supplier;"), names));
        assertFalse(ReviewedNetworkLambda.accepts(SUPPLIER, "get", Type.getMethodType("()Ljava/lang/Object;"), Type.getMethodType("()Ljava/lang/String;"), Type.getMethodType("()Ljava/util/function/Predicate;"), names));
        assertFalse(ReviewedNetworkLambda.accepts(SUPPLIER, "get", Type.getMethodType("()Ljava/lang/Object;"), Type.getMethodType("()Ljava/lang/String;"), Type.getMethodType("()Ljava/util/function/Supplier;"), null));
    }

    @Test
    void verifiesWithoutExecutingOwnedStaticInitializerOrMutatingInputBytes() {
        String marker = "legacybridge.network.linkage.never.execute";
        assertNull(System.getProperty(marker));
        Map<String, byte[]> classes = classes(protocol());
        classes.put(OWN, type(OWN, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, cw -> {
            method(cw, ACC_STATIC, "<clinit>", "()V", (mv, descriptor) -> {
                mv.visitLdcInsn(marker); mv.visitLdcInsn("EXECUTED");
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                mv.visitInsn(POP); mv.visitInsn(RETURN);
            });
            method(cw, ACC_PRIVATE | ACC_STATIC, "impl", protocol().implementation(), NetworkOfflineLinkageTest::defaultReturn);
            factory(cw, protocol(), new Handle(H_INVOKESTATIC, OWN, "impl", protocol().implementation(), false), METAFACTORY);
        }));
        Map<String, byte[]> before = new HashMap<>();
        classes.forEach((name, bytes) -> before.put(name, bytes.clone()));
        assertEquals("PASSED_SCOPED", verifier.verify(Map.copyOf(classes), api(), NETWORK).status());
        before.forEach((name, bytes) -> assertArrayEquals(bytes, classes.get(name)));
        assertNull(System.getProperty(marker));
    }

    private OfflineLinkageVerifier.Summary verify(Lambda lambda) { return verifier.verify(classes(lambda), api(), NETWORK); }
    private void rejected(Lambda lambda) { assertThrows(TransformationException.class, () -> verify(lambda)); }
    private void rejected(Map<String, byte[]> classes, String diagnostic) {
        var failure = assertThrows(TransformationException.class, () -> verifier.verify(classes, api(), NETWORK));
        assertTrue(failure.getMessage().contains(diagnostic), failure.getMessage());
    }
    private static Lambda encoder(String buffer) { return new Lambda("java/util/function/BiConsumer", "accept", "(Ljava/lang/Object;Ljava/lang/Object;)V", "(" + object(MESSAGE) + object(buffer) + ")V", ""); }
    private static Lambda decoder(String buffer) { return new Lambda("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", "(" + object(buffer) + ")" + object(MESSAGE), ""); }
    private static Lambda consumer() { return new Lambda("java/util/function/BiConsumer", "accept", "(Ljava/lang/Object;Ljava/lang/Object;)V", "(" + object(MESSAGE) + object(SUPPLIER) + ")V", ""); }
    private static Lambda runnable(String context) { return new Lambda("java/lang/Runnable", "run", "()V", "()V", object(MESSAGE) + object(context)); }
    private static Lambda protocol() { return new Lambda(SUPPLIER, "get", "()Ljava/lang/Object;", "()Ljava/lang/String;", ""); }
    private static Lambda predicate() { return new Lambda("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z", "(Ljava/lang/String;)Z", ""); }
    private static Lambda player(String owner) { return new Lambda(SUPPLIER, "get", "()Ljava/lang/Object;", "()" + object(owner), object(owner)); }
    private static String object(String name) { return "L" + name + ";"; }
    private static Map<String, byte[]> classes(Lambda lambda) { return classes(lambda, lambda.implementation(), H_INVOKESTATIC, false, ACC_PRIVATE | ACC_STATIC); }
    private static Map<String, byte[]> classes(Lambda lambda, String implementation, int handleKind, boolean ownerInterface, int access) {
        Map<String, byte[]> classes = new HashMap<>();
        classes.put(MESSAGE, type(MESSAGE, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, cw -> { }));
        classes.put(OWN, type(OWN, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, cw -> {
            method(cw, access, "impl", implementation, NetworkOfflineLinkageTest::defaultReturn);
            factory(cw, lambda, new Handle(handleKind, OWN, "impl", implementation, ownerInterface), METAFACTORY);
        }));
        return classes;
    }
    private static byte[] type(String name, int access, Consumer<ClassWriter> body) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String left, String right) { throw new AssertionError("No runtime hierarchy loading permitted"); }
        };
        cw.visit(V1_8, access, name, null, "java/lang/Object", new String[0]);
        cw.visitSource("SyntheticNetwork.java", null); body.accept(cw); cw.visitEnd(); return cw.toByteArray();
    }
    private interface Body { void accept(MethodVisitor method, String descriptor); }
    private static void method(ClassWriter cw, int access, String name, String descriptor, Body body) {
        MethodVisitor mv = cw.visitMethod(access, name, descriptor, null, null);
        mv.visitCode(); body.accept(mv, descriptor); mv.visitMaxs(0, 0); mv.visitEnd();
    }
    private static void defaultReturn(MethodVisitor mv, String descriptor) {
        Type result = Type.getReturnType(descriptor);
        if (result.equals(Type.VOID_TYPE)) mv.visitInsn(RETURN);
        else if (result.getSort() == Type.OBJECT || result.getSort() == Type.ARRAY) { mv.visitInsn(ACONST_NULL); mv.visitInsn(ARETURN); }
        else { mv.visitInsn(ICONST_0); mv.visitInsn(IRETURN); }
    }
    private static void factory(ClassWriter cw, Lambda lambda, Handle implementation, Handle bootstrap) {
        method(cw, ACC_PUBLIC | ACC_STATIC, "factory", lambda.factory(), (mv, descriptor) -> {
            int slot = 0;
            for (Type capture : Type.getArgumentTypes(descriptor)) { mv.visitVarInsn(capture.getOpcode(ILOAD), slot); slot += capture.getSize(); }
            mv.visitInvokeDynamicInsn(lambda.name(), lambda.factory(), bootstrap, Type.getMethodType(lambda.sam()), implementation, Type.getMethodType(lambda.instantiated()));
            mv.visitInsn(ARETURN);
        });
    }
    private static ApiIndex api() {
        Map<String, ApiClass> declarations = new HashMap<>();
        for (String type : List.of(BUFFER_OLD, BUFFER_NEW, CONTEXT_OLD, CONTEXT_NEW, PLAYER_OLD, PLAYER_NEW))
            declarations.put(type, new ApiClass(type, ACC_PUBLIC | ACC_SUPER, "java/lang/Object", List.of(), List.of(), List.of()));
        return new ApiIndex(declarations);
    }
}
