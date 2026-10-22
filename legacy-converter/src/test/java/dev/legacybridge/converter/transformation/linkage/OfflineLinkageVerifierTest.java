package dev.legacybridge.converter.transformation.linkage;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.ApiIndex.ApiClass;
import dev.legacybridge.converter.mapping.source.ApiIndex.ApiMember;
import dev.legacybridge.converter.transformation.TransformationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/** All owned inputs are inert, synthetic byte arrays. No defineClass, mod/game execution or downloads. */
class OfflineLinkageVerifierTest {
    private static final String OWN = "fixture/Example";
    private static final Handle METAFACTORY = new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
    private final OfflineLinkageVerifier verifier = new OfflineLinkageVerifier();

    @Test void resolvesInheritedVirtualMethodsAndFieldsThroughSymbolicSubclass() {
        byte[] input = ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "read", "(Lapi/Child;)I", mv -> {
            mv.visitVarInsn(ALOAD, 0); mv.visitMethodInsn(INVOKEVIRTUAL, "api/Child", "value", "()I", false);
            mv.visitVarInsn(ALOAD, 0); mv.visitFieldInsn(GETFIELD, "api/Child", "count", "I"); mv.visitInsn(IADD); mv.visitInsn(IRETURN);
        }));
        var summary = verifier.verify(Map.of(OWN, input), api());
        assertEquals("PASSED_SCOPED", summary.status()); assertEquals(1, summary.ownedClassCount());
        assertEquals(1, summary.fieldReferenceCount()); assertFalse((Boolean) summary.toTree().get("classesExecuted"));
    }

    @Test void resolvesInheritedAbstractInterfaceAndOwnedImplementation() {
        byte[] input = type(OWN, "java/lang/Object", ACC_PUBLIC | ACC_SUPER, new String[]{"api/ChildAction"}, cw -> {
            constructor(cw, "java/lang/Object");
            method(cw, ACC_PUBLIC, "apply", "(I)I", mv -> { mv.visitVarInsn(ILOAD, 1); mv.visitInsn(IRETURN); });
            method(cw, ACC_PUBLIC | ACC_STATIC, "call", "(Lapi/ChildAction;I)I", mv -> {
                mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ILOAD, 1);
                mv.visitMethodInsn(INVOKEINTERFACE, "api/ChildAction", "apply", "(I)I", true); mv.visitInsn(IRETURN);
            });
        });
        assertEquals("PASSED_SCOPED", verifier.verify(Map.of(OWN, input), api()).status());
    }

    @Test void permitsProtectedSuperCallsAndProtectedReceiverProvenToBeSubclass() {
        byte[] input = type(OWN, "api/Base", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> {
            constructor(cw, "api/Base");
            method(cw, ACC_PUBLIC, "read", "()I", mv -> {
                mv.visitVarInsn(ALOAD, 0); mv.visitMethodInsn(INVOKESPECIAL, "api/Base", "protectedValue", "()I", false);
                mv.visitVarInsn(ALOAD, 0); mv.visitFieldInsn(GETFIELD, "api/Base", "protectedCount", "I"); mv.visitInsn(IADD); mv.visitInsn(IRETURN);
            });
        });
        assertEquals("PASSED_SCOPED", verifier.verify(Map.of(OWN, input), api()).status());
    }

    @Test void permitsOwnedPackageHelpersAndPrivateCallsWithoutLoadingInitializer() {
        String marker = "legacybridge.linkage.must.never.execute";
        assertNull(System.getProperty(marker));
        byte[] helper = type("fixture/Helper", "java/lang/Object", ACC_SUPER, new String[0], cw -> {
            method(cw, ACC_STATIC, "number", "()I", mv -> { mv.visitInsn(ICONST_3); mv.visitInsn(IRETURN); });
            method(cw, ACC_STATIC, "<clinit>", "()V", mv -> {
                mv.visitLdcInsn(marker); mv.visitLdcInsn("executed");
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                mv.visitInsn(POP); mv.visitInsn(RETURN);
            });
        });
        byte[] input = ordinary(cw -> {
            method(cw, ACC_PRIVATE, "secret", "()I", mv -> { mv.visitMethodInsn(INVOKESTATIC, "fixture/Helper", "number", "()I", false); mv.visitInsn(IRETURN); });
            method(cw, ACC_PUBLIC, "read", "()I", mv -> { mv.visitVarInsn(ALOAD, 0); mv.visitMethodInsn(INVOKESPECIAL, OWN, "secret", "()I", false); mv.visitInsn(IRETURN); });
        });
        assertEquals(2, verifier.verify(Map.of(OWN, input, "fixture/Helper", helper), api()).ownedClassCount());
        assertNull(System.getProperty(marker));
    }

    @Test void permitsFinalFieldWritesOnlyToThisInOwnInitializers() {
        byte[] input = type(OWN, "java/lang/Object", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> {
            cw.visitField(ACC_PUBLIC | ACC_FINAL, "value", "I", null, null).visitEnd();
            cw.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "GLOBAL", "I", null, null).visitEnd();
            method(cw, ACC_PUBLIC, "<init>", "()V", mv -> {
                mv.visitVarInsn(ALOAD, 0); mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitVarInsn(ALOAD, 0); mv.visitInsn(ICONST_1); mv.visitFieldInsn(PUTFIELD, OWN, "value", "I"); mv.visitInsn(RETURN);
            });
            method(cw, ACC_STATIC, "<clinit>", "()V", mv -> { mv.visitInsn(ICONST_2); mv.visitFieldInsn(PUTSTATIC, OWN, "GLOBAL", "I"); mv.visitInsn(RETURN); });
        });
        assertEquals("PASSED_SCOPED", verifier.verify(Map.of(OWN, input), api()).status());
    }

    @Test void permitsLinearNewAndValidatedCatchAndThrowsMetadata() {
        byte[] input = ordinary(cw -> {
            method(cw, ACC_PUBLIC | ACC_STATIC, "create", "()Lapi/Child;", mv -> {
                mv.visitTypeInsn(NEW, "api/Child"); mv.visitInsn(DUP); mv.visitMethodInsn(INVOKESPECIAL, "api/Child", "<init>", "()V", false); mv.visitInsn(ARETURN);
            });
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "divide", "(I)I", null, new String[]{"java/lang/ArithmeticException"});
            Label start = new Label(), end = new Label(), handler = new Label();
            mv.visitTryCatchBlock(start, end, handler, "java/lang/ArithmeticException"); mv.visitCode(); mv.visitLabel(start);
            mv.visitInsn(ICONST_1); mv.visitVarInsn(ILOAD, 0); mv.visitInsn(IDIV); mv.visitLabel(end); mv.visitInsn(IRETURN);
            mv.visitLabel(handler); mv.visitInsn(POP); mv.visitInsn(ICONST_M1); mv.visitInsn(IRETURN); mv.visitMaxs(0, 0); mv.visitEnd();
        });
        assertEquals("PASSED_SCOPED", verifier.verify(Map.of(OWN, input), api()).status());
    }

    @Test void validatesAnnotationElementAndClassLiteralReferences() {
        byte[] input = ordinary(cw -> {
            var annotation = cw.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotation.visit("since", "test"); annotation.visit("forRemoval", false); annotation.visitEnd();
        });
        assertEquals("PASSED_SCOPED", verifier.verify(Map.of(OWN, input), api()).status());
    }

    @Test void permitsSupplierAndIntUnaryOperatorWithExactOwnedStaticCaptures() {
        byte[] input = ordinary(cw -> {
            method(cw, ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "supplierImpl", "(Ljava/lang/String;)Ljava/lang/String;", mv -> { mv.visitVarInsn(ALOAD, 0); mv.visitInsn(ARETURN); });
            method(cw, ACC_PUBLIC | ACC_STATIC, "supplier", "(Ljava/lang/String;)Ljava/util/function/Supplier;", mv -> {
                mv.visitVarInsn(ALOAD, 0); mv.visitInvokeDynamicInsn("get", "(Ljava/lang/String;)Ljava/util/function/Supplier;", METAFACTORY,
                        Type.getMethodType("()Ljava/lang/Object;"), new Handle(H_INVOKESTATIC, OWN, "supplierImpl", "(Ljava/lang/String;)Ljava/lang/String;", false), Type.getMethodType("()Ljava/lang/String;")); mv.visitInsn(ARETURN);
            });
            method(cw, ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "operatorImpl", "(II)I", mv -> { mv.visitVarInsn(ILOAD, 0); mv.visitVarInsn(ILOAD, 1); mv.visitInsn(IADD); mv.visitInsn(IRETURN); });
            method(cw, ACC_PUBLIC | ACC_STATIC, "operator", "(I)Ljava/util/function/IntUnaryOperator;", mv -> {
                mv.visitVarInsn(ILOAD, 0); mv.visitInvokeDynamicInsn("applyAsInt", "(I)Ljava/util/function/IntUnaryOperator;", METAFACTORY,
                        Type.getMethodType("(I)I"), new Handle(H_INVOKESTATIC, OWN, "operatorImpl", "(II)I", false), Type.getMethodType("(I)I")); mv.visitInsn(ARETURN);
            });
        });
        assertEquals(2, verifier.verify(Map.of(OWN, input), api()).lambdaSiteCount());
    }

    @Test void permitsZeroCaptureOwnedItemConstructorSupplierWithExactUpcast() {
        for (String instantiated : List.of("Lfixture/Example;", "Lnet/minecraft/world/item/Item;")) {
            byte[] input = constructorSupplier(OWN, "()V", "()Ljava/util/function/Supplier;", instantiated);
            assertEquals(1, verifier.verify(Map.of(OWN, input), api()).lambdaSiteCount());
        }
    }

    @Test void rejectsConstructorSupplierUnknownConstructorWrongReturnAndExternalOwner() {
        rejects("MISSING_METHOD", constructorSupplier(OWN, "(I)V", "()Ljava/util/function/Supplier;", "Lfixture/Example;"));
        rejects("CONSTRUCTOR_SUPPLIER_RETURN", constructorSupplier(OWN, "()V", "()Ljava/util/function/Supplier;", "Ljava/lang/String;"));
        rejects("CONSTRUCTOR_SUPPLIER_UNSUPPORTED", constructorSupplier("net/minecraft/world/item/Item", "()V", "()Ljava/util/function/Supplier;", "Lnet/minecraft/world/item/Item;"));
        rejects("CONSTRUCTOR_SUPPLIER_UNSUPPORTED", constructorSupplier(OWN, "()V", "(I)Ljava/util/function/Supplier;", "Lfixture/Example;"));
    }

    @Test void rejectsUnknownDescriptorAndAnnotationAndExceptionTypes() {
        rejects("MISSING_CLASS", ordinary(cw -> cw.visitField(ACC_PUBLIC, "missing", "Lmissing/Type;", null, null).visitEnd()));
        rejects("MISSING_CLASS", ordinary(cw -> cw.visitAnnotation("Lmissing/Annotation;", false).visitEnd()));
        rejects("MISSING_CLASS", ordinary(cw -> {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "x", "()V", null, new String[]{"missing/Exception"}); mv.visitCode(); mv.visitInsn(RETURN); mv.visitMaxs(0, 0); mv.visitEnd();
        }));
        rejects("NON_THROWABLE_EXCEPTION_TYPE", ordinary(cw -> {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "x", "()V", null, new String[]{"java/lang/String"}); mv.visitCode(); mv.visitInsn(RETURN); mv.visitMaxs(0, 0); mv.visitEnd();
        }));
    }

    @Test void rejectsMissingMethodFieldAndEvenUnusedConstantPoolReference() {
        rejects("MISSING_METHOD", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "()V", mv -> { mv.visitMethodInsn(INVOKESTATIC, "api/Base", "unknown", "()V", false); mv.visitInsn(RETURN); })));
        rejects("MISSING_FIELD", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "()I", mv -> { mv.visitFieldInsn(GETSTATIC, "api/Base", "unknown", "I"); mv.visitInsn(IRETURN); })));
        rejects("MISSING_METHOD", ordinary(cw -> cw.newMethod("api/Base", "unused", "()V", false)));
    }

    @Test void rejectsWrongMethodAndFieldStaticnessAndInterfaceOpcode() {
        rejects("METHOD_STATICNESS", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "()I", mv -> { mv.visitMethodInsn(INVOKESTATIC, "api/Base", "value", "()I", false); mv.visitInsn(IRETURN); })));
        rejects("FIELD_STATICNESS", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "()I", mv -> { mv.visitFieldInsn(GETSTATIC, "api/Base", "count", "I"); mv.visitInsn(IRETURN); })));
        rejects("INVALID_INVOKEVIRTUAL", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "(Lapi/Action;I)I", mv -> { mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ILOAD, 1); mv.visitMethodInsn(INVOKEVIRTUAL, "api/Action", "apply", "(I)I", true); mv.visitInsn(IRETURN); })));
        rejects("CONSTANT_POOL_OWNER_KIND", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "(Lapi/Action;I)I", mv -> { mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ILOAD, 1); mv.visitMethodInsn(INVOKEINTERFACE, "api/Action", "apply", "(I)I", false); mv.visitInsn(IRETURN); })));
    }

    @Test void rejectsPrivateExternalAndCrossLoaderPackageAccess() {
        rejects("PRIVATE_ACCESS", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "()I", mv -> { mv.visitMethodInsn(INVOKESTATIC, "api/Base", "secret", "()I", false); mv.visitInsn(IRETURN); })));
        byte[] sameTextPackage = type("api/Impostor", "java/lang/Object", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> {
            constructor(cw, "java/lang/Object"); method(cw, ACC_PUBLIC | ACC_STATIC, "x", "()I", mv -> { mv.visitMethodInsn(INVOKESTATIC, "api/Base", "packageValue", "()I", false); mv.visitInsn(IRETURN); });
        });
        var error = assertThrows(TransformationException.class, () -> verifier.verify(Map.of("api/Impostor", sameTextPackage), api()));
        assertTrue(error.getMessage().contains("MEMBER_ACCESS"), error.getMessage());
    }

    @Test void rejectsProtectedReceiverWhoseStaticTypeIsOnlyBase() {
        byte[] input = type(OWN, "api/Base", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> {
            constructor(cw, "api/Base");
            method(cw, ACC_PUBLIC, "wrong", "(Lapi/Base;)I", mv -> { mv.visitVarInsn(ALOAD, 1); mv.visitMethodInsn(INVOKEVIRTUAL, "api/Base", "protectedValue", "()I", false); mv.visitInsn(IRETURN); });
        });
        rejects("PROTECTED_RECEIVER", input);
    }

    @Test void rejectsPublicSuperInvocationWithReceiverThatIsOnlyBase() {
        rejects("SPECIAL_RECEIVER_UNPROVEN", type(OWN, "api/Base", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> {
            constructor(cw, "api/Base");
            method(cw, ACC_PUBLIC, "wrong", "(Lapi/Base;)I", mv -> {
                mv.visitVarInsn(ALOAD, 1); mv.visitMethodInsn(INVOKESPECIAL, "api/Base", "value", "()I", false); mv.visitInsn(IRETURN);
            });
        }));
    }

    @Test void rejectsFinalSuperclassFinalOverrideStaticMismatchAndWeakerAccess() {
        rejects("FINAL_SUPERCLASS", type(OWN, "api/Final", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> constructor(cw, "api/Final")));
        rejects("FINAL_METHOD_OVERRIDE", override("fixed", ACC_PUBLIC));
        rejects("OVERRIDE_STATICNESS", override("staticValue", ACC_PUBLIC));
        rejects("WEAKER_OVERRIDE_ACCESS", override("value", ACC_PROTECTED));
    }

    @Test void rejectsMissingAbstractImplementationAndProtectedInterfaceImplementation() {
        rejects("ABSTRACT_METHOD_UNIMPLEMENTED", type(OWN, "java/lang/Object", ACC_PUBLIC | ACC_SUPER, new String[]{"api/Action"}, cw -> constructor(cw, "java/lang/Object")));
        rejects("WEAKER_OVERRIDE_ACCESS", type(OWN, "java/lang/Object", ACC_PUBLIC | ACC_SUPER, new String[]{"api/Action"}, cw -> {
            constructor(cw, "java/lang/Object"); method(cw, ACC_PROTECTED, "apply", "(I)I", mv -> { mv.visitVarInsn(ILOAD, 1); mv.visitInsn(IRETURN); });
        }));
    }

    @Test void rejectsDefaultInterfaceDispatchAndHierarchyCycles() {
        rejects("DEFAULT_INTERFACE_DISPATCH_UNSUPPORTED", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "(Lapi/DefaultAction;)I", mv -> {
            mv.visitVarInsn(ALOAD, 0); mv.visitMethodInsn(INVOKEINTERFACE, "api/DefaultAction", "value", "()I", true); mv.visitInsn(IRETURN);
        })));
        byte[] a = type("fixture/A", "fixture/B", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> { });
        byte[] b = type("fixture/B", "fixture/A", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> { });
        var error = assertThrows(TransformationException.class, () -> verifier.verify(Map.of("fixture/A", a, "fixture/B", b), api()));
        assertTrue(error.getMessage().contains("HIERARCHY_CYCLE"), error.getMessage());
    }

    @Test void rejectsConstructorThatReturnsWithoutSuperAndRepeatedInitialization() {
        rejects("UNINITIALIZED_AT_EXIT", type(OWN, "java/lang/Object", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> method(cw, ACC_PUBLIC, "<init>", "()V", mv -> mv.visitInsn(RETURN))));
        rejects("INVALID_CONSTRUCTOR_RECEIVER", type(OWN, "java/lang/Object", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> method(cw, ACC_PUBLIC, "<init>", "()V", mv -> {
            mv.visitVarInsn(ALOAD, 0); mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitVarInsn(ALOAD, 0); mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); mv.visitInsn(RETURN);
        })));
    }

    @Test void rejectsInheritedConstructorAndNewWhoseConstructorOwnerDoesNotMatch() {
        rejects("MISSING_METHOD", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "()Lapi/NoConstructor;", mv -> {
            mv.visitTypeInsn(NEW, "api/NoConstructor"); mv.visitInsn(DUP); mv.visitMethodInsn(INVOKESPECIAL, "api/NoConstructor", "<init>", "()V", false); mv.visitInsn(ARETURN);
        })));
        rejects("NEW_CONSTRUCTOR_OWNER_MISMATCH", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "()Lapi/Child;", mv -> {
            mv.visitTypeInsn(NEW, "api/Child"); mv.visitInsn(DUP); mv.visitMethodInsn(INVOKESPECIAL, "api/Base", "<init>", "()V", false); mv.visitInsn(ARETURN);
        })));
    }

    @Test void rejectsUninitializedUseLocalAliasAndThisDelegation() {
        rejects("UNINITIALIZED_REFERENCE_USE", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "()I", mv -> {
            mv.visitTypeInsn(NEW, "api/Base"); mv.visitMethodInsn(INVOKEVIRTUAL, "api/Base", "value", "()I", false); mv.visitInsn(IRETURN);
        })));
        rejects("UNINITIALIZED_ALIAS_UNSUPPORTED", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "()V", mv -> {
            mv.visitTypeInsn(NEW, "api/Base"); mv.visitVarInsn(ASTORE, 0); mv.visitInsn(RETURN);
        })));
        rejects("CONSTRUCTOR_DELEGATION_UNSUPPORTED", type(OWN, "java/lang/Object", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> method(cw, ACC_PUBLIC, "<init>", "()V", mv -> {
            mv.visitVarInsn(ALOAD, 0); mv.visitMethodInsn(INVOKESPECIAL, OWN, "<init>", "()V", false); mv.visitInsn(RETURN);
        })));
    }

    @Test void rejectsUninitializedControlFlowAndExceptionHandler() {
        rejects("UNINITIALIZED_CONTROL_FLOW_UNSUPPORTED", type(OWN, "java/lang/Object", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> method(cw, ACC_PUBLIC, "<init>", "()V", mv -> {
            Label next = new Label(); mv.visitInsn(ICONST_0); mv.visitJumpInsn(IFEQ, next); mv.visitInsn(NOP); mv.visitLabel(next);
            mv.visitVarInsn(ALOAD, 0); mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); mv.visitInsn(RETURN);
        })));
        rejects("UNINITIALIZED", ordinary(cw -> {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "x", "()V", null, null); Label start = new Label(), end = new Label(), handler = new Label();
            mv.visitTryCatchBlock(start, end, handler, "java/lang/RuntimeException"); mv.visitCode(); mv.visitLabel(start);
            mv.visitTypeInsn(NEW, "api/Base"); mv.visitInsn(DUP); mv.visitMethodInsn(INVOKESPECIAL, "api/Base", "<init>", "()V", false); mv.visitInsn(POP);
            mv.visitLabel(end); mv.visitInsn(RETURN); mv.visitLabel(handler); mv.visitInsn(POP); mv.visitInsn(RETURN); mv.visitMaxs(0, 0); mv.visitEnd();
        }));
    }

    @Test void rejectsFinalFieldWritesOutsideInitializationOrOnOtherInstance() {
        rejects("ILLEGAL_FINAL_FIELD_WRITE", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "()V", mv -> { mv.visitInsn(ICONST_0); mv.visitFieldInsn(PUTSTATIC, "api/Base", "MAGIC", "I"); mv.visitInsn(RETURN); })));
        rejects("ILLEGAL_FINAL_FIELD_WRITE", type(OWN, "java/lang/Object", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> {
            cw.visitField(ACC_PUBLIC | ACC_FINAL, "value", "I", null, null).visitEnd();
            method(cw, ACC_PUBLIC, "<init>", "(Lfixture/Example;)V", mv -> {
                mv.visitVarInsn(ALOAD, 0); mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitVarInsn(ALOAD, 1); mv.visitInsn(ICONST_1); mv.visitFieldInsn(PUTFIELD, OWN, "value", "I"); mv.visitInsn(RETURN);
            });
        }));
    }

    @Test void rejectsFinalWriteWhenControlFlowMergesThisAndAnotherInstance() {
        rejects("ILLEGAL_FINAL_FIELD_WRITE", type(OWN, "java/lang/Object", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> {
            cw.visitField(ACC_PUBLIC | ACC_FINAL, "value", "I", null, null).visitEnd();
            method(cw, ACC_PUBLIC, "<init>", "(Lfixture/Example;Z)V", mv -> {
                mv.visitVarInsn(ALOAD, 0); mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ASTORE, 3);
                Label merge = new Label(); mv.visitVarInsn(ILOAD, 2); mv.visitJumpInsn(IFEQ, merge);
                mv.visitVarInsn(ALOAD, 1); mv.visitVarInsn(ASTORE, 3); mv.visitLabel(merge);
                mv.visitVarInsn(ALOAD, 3); mv.visitInsn(ICONST_1); mv.visitFieldInsn(PUTFIELD, OWN, "value", "I"); mv.visitInsn(RETURN);
            });
        }));
    }

    @Test void rejectsAltMetafactoryStandaloneHandlesAndCondy() {
        rejects("BOOTSTRAP_UNSUPPORTED", lambdaWith(new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "altMetafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false), "()Ljava/util/function/Supplier;", "()Ljava/lang/String;"));
        rejects("STANDALONE_METHOD_HANDLE_UNSUPPORTED", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "()V", mv -> {
            mv.visitLdcInsn(new Handle(H_INVOKESTATIC, "api/Base", "staticValue", "()I", false)); mv.visitInsn(POP); mv.visitInsn(RETURN);
        })));
        rejects("CONSTANT_DYNAMIC_UNSUPPORTED", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "()V", mv -> {
            mv.visitLdcInsn(new ConstantDynamic("constant", "Ljava/lang/Object;", METAFACTORY)); mv.visitInsn(POP); mv.visitInsn(RETURN);
        })));
    }

    @Test void rejectsLambdaCaptureMismatchAndUnsupportedSam() {
        rejects("LAMBDA_CAPTURE_ARITY", lambdaWith(METAFACTORY, "()Ljava/util/function/Supplier;", "(Ljava/lang/String;)Ljava/lang/String;"));
        rejects("FUNCTIONAL_INTERFACE_UNSUPPORTED", lambdaWith(METAFACTORY, "()Ljava/lang/Runnable;", "()Ljava/lang/String;"));
    }

    @Test void rejectsReflectionAndNativeMethods() {
        rejects("REFLECTIVE_LINKAGE_UNSUPPORTED", ordinary(cw -> method(cw, ACC_PUBLIC | ACC_STATIC, "x", "()Ljava/lang/Class;", mv -> {
            mv.visitLdcInsn("fixture.Example"); mv.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false); mv.visitInsn(ARETURN);
        })));
        rejects("NATIVE_METHOD_UNSUPPORTED", ordinary(cw -> cw.visitMethod(ACC_PUBLIC | ACC_NATIVE, "x", "()V", null, null).visitEnd()));
    }

    @Test void rejectsPlatformOwnershipAndMismatchedMapKeys() {
        byte[] input = type("net/minecraft/Impostor", "java/lang/Object", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> constructor(cw, "java/lang/Object"));
        assertTrue(assertThrows(TransformationException.class, () -> verifier.verify(Map.of("net/minecraft/Impostor", input), api())).getMessage().contains("PLATFORM_OR_EXTERNAL_CLASS_OWNERSHIP"));
        assertTrue(assertThrows(TransformationException.class, () -> verifier.verify(Map.of("other/Name", ordinary(cw -> { })), api())).getMessage().contains("OWNERSHIP_PATH_MISMATCH"));
    }

    @Test void rejectsOwnedNestAndUnauthorizedTrustedPlatformSealedParent() {
        rejects("CLASS_FEATURE_UNSUPPORTED", ordinary(cw -> cw.visitNestHost("fixture/Host")));
        rejects("SEALED_PLATFORM_PARENT", type(OWN, "java/lang/Object", ACC_PUBLIC | ACC_ABSTRACT | ACC_SUPER,
                new String[]{"java/lang/constant/ConstantDesc"}, cw -> constructor(cw, "java/lang/Object")));
    }

    @Test void declarationHierarchyContainsOnlyHeadersAndNeverShadowsTrustedJdk() {
        var declarations = OfflineLinkageVerifier.declarationHierarchy(api());
        assertEquals("api/Base", new ClassReader(declarations.get("api/Base")).getClassName());
        var node = new org.objectweb.asm.tree.ClassNode(); new ClassReader(declarations.get("api/Base")).accept(node, 0);
        assertTrue(node.methods.isEmpty()); assertTrue(node.fields.isEmpty()); assertFalse(declarations.containsKey("java/lang/Object"));
    }

    private void rejects(String code, byte[] input) {
        var error = assertThrows(TransformationException.class, () -> verifier.verify(Map.of(OWN, input), api()));
        assertTrue(error.getMessage().contains(code), () -> "Expected " + code + " but got " + error);
    }
    private static byte[] override(String name, int access) {
        return type(OWN, "api/Base", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> { constructor(cw, "api/Base"); method(cw, access, name, "()I", mv -> { mv.visitInsn(ICONST_0); mv.visitInsn(IRETURN); }); });
    }
    private static byte[] lambdaWith(Handle bootstrap, String factory, String implementation) {
        return ordinary(cw -> {
            method(cw, ACC_PRIVATE | ACC_STATIC, "impl", implementation, mv -> { mv.visitLdcInsn("result"); mv.visitInsn(ARETURN); });
            method(cw, ACC_PUBLIC | ACC_STATIC, "lambda", factory, mv -> {
                mv.visitInvokeDynamicInsn("get", factory, bootstrap, Type.getMethodType("()Ljava/lang/Object;"), new Handle(H_INVOKESTATIC, OWN, "impl", implementation, false), Type.getMethodType("()Ljava/lang/String;")); mv.visitInsn(ARETURN);
            });
        });
    }
    private static byte[] constructorSupplier(String implementationOwner, String constructorDescriptor, String factory, String instantiatedReturn) {
        return type(OWN, "net/minecraft/world/item/Item", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> {
            constructor(cw, "net/minecraft/world/item/Item");
            method(cw, ACC_PUBLIC | ACC_STATIC, "supplier", factory, mv -> {
                if (Type.getArgumentTypes(factory).length != 0) mv.visitVarInsn(ILOAD, 0);
                mv.visitInvokeDynamicInsn("get", factory, METAFACTORY, Type.getMethodType("()Ljava/lang/Object;"),
                        new Handle(H_NEWINVOKESPECIAL, implementationOwner, "<init>", constructorDescriptor, false), Type.getMethodType("()" + instantiatedReturn));
                mv.visitInsn(ARETURN);
            });
        });
    }
    private static byte[] ordinary(Consumer<ClassWriter> extra) {
        return type(OWN, "java/lang/Object", ACC_PUBLIC | ACC_SUPER, new String[0], cw -> { constructor(cw, "java/lang/Object"); extra.accept(cw); });
    }
    private static byte[] type(String name, String parent, int access, String[] interfaces, Consumer<ClassWriter> body) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String left, String right) { throw new AssertionError("Fixture generation must not load hierarchy: " + left + " / " + right); }
        };
        cw.visit(V1_8, access, name, null, parent, interfaces); cw.visitSource("SyntheticFixture.java", null); body.accept(cw); cw.visitEnd(); return cw.toByteArray();
    }
    private static void constructor(ClassWriter cw, String parent) {
        method(cw, ACC_PUBLIC, "<init>", "()V", mv -> { mv.visitVarInsn(ALOAD, 0); mv.visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V", false); mv.visitInsn(RETURN); });
    }
    private static void method(ClassWriter cw, int access, String name, String descriptor, Consumer<MethodVisitor> body) {
        MethodVisitor mv = cw.visitMethod(access, name, descriptor, null, null); mv.visitCode(); body.accept(mv); mv.visitMaxs(0, 0); mv.visitEnd();
    }
    private static ApiIndex api() {
        Map<String, ApiClass> values = new LinkedHashMap<>();
        values.put("net/minecraft/world/item/Item", new ApiClass("net/minecraft/world/item/Item", ACC_PUBLIC | ACC_SUPER, "java/lang/Object", List.of(), List.of(m("<init>", "()V", ACC_PUBLIC)), List.of()));
        values.put("api/Base", new ApiClass("api/Base", ACC_PUBLIC | ACC_SUPER, "java/lang/Object", List.of(), List.of(
                m("<init>", "()V", ACC_PUBLIC), m("value", "()I", ACC_PUBLIC), m("protectedValue", "()I", ACC_PROTECTED),
                m("secret", "()I", ACC_PRIVATE | ACC_STATIC), m("packageValue", "()I", ACC_STATIC), m("fixed", "()I", ACC_PUBLIC | ACC_FINAL), m("staticValue", "()I", ACC_PUBLIC | ACC_STATIC)),
                List.of(m("count", "I", ACC_PUBLIC), m("protectedCount", "I", ACC_PROTECTED), m("MAGIC", "I", ACC_PUBLIC | ACC_STATIC | ACC_FINAL))));
        values.put("api/Child", new ApiClass("api/Child", ACC_PUBLIC | ACC_SUPER, "api/Base", List.of(), List.of(m("<init>", "()V", ACC_PUBLIC)), List.of()));
        values.put("api/NoConstructor", new ApiClass("api/NoConstructor", ACC_PUBLIC | ACC_SUPER, "api/Base", List.of(), List.of(), List.of()));
        values.put("api/Final", new ApiClass("api/Final", ACC_PUBLIC | ACC_FINAL | ACC_SUPER, "java/lang/Object", List.of(), List.of(m("<init>", "()V", ACC_PUBLIC)), List.of()));
        values.put("api/Action", new ApiClass("api/Action", ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE, "java/lang/Object", List.of(), List.of(m("apply", "(I)I", ACC_PUBLIC | ACC_ABSTRACT)), List.of()));
        values.put("api/ChildAction", new ApiClass("api/ChildAction", ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE, "java/lang/Object", List.of("api/Action"), List.of(), List.of()));
        values.put("api/DefaultAction", new ApiClass("api/DefaultAction", ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE, "java/lang/Object", List.of(), List.of(m("value", "()I", ACC_PUBLIC)), List.of()));
        return new ApiIndex(values);
    }
    private static ApiMember m(String name, String descriptor, int access) { return new ApiMember(name, descriptor, access); }
}
