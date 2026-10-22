package dev.legacybridge.converter.bytecode;

import dev.legacybridge.converter.namespace.NamespaceCategory;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BytecodeScannerTest {
    private final BytecodeScanner scanner = new BytecodeScanner();

    @Test
    void scansEveryStandardReferenceBearingClassStructure() {
        ClassBytecodeScan scan = scanner.scan("fixture/FeatureCarrier.class", featureClass());

        assertEquals("fixture/FeatureCarrier", scan.internalName());
        assertEquals(61, scan.majorVersion());
        assertEquals(0xFFFF, scan.minorVersion());
        assertTrue(scan.preview());
        assertEquals(1, scan.features().nativeMethods());
        assertEquals(1, scan.features().recordComponents());
        assertEquals(1, scan.features().nestHosts());
        assertEquals(1, scan.features().nestMembers());
        assertEquals(1, scan.features().permittedSubclasses());
        assertEquals(1, scan.features().invokeDynamicInstructions());
        assertTrue(scan.features().handles() >= 4);
        assertTrue(scan.features().constantDynamics() >= 2);
        assertTrue(scan.features().annotationSites() >= 8);
        assertTrue(scan.features().instructions() >= 10);
        assertReference(scan, "signature", "signature/Bound");
        assertReference(scan, "annotation", "annotation/ValueType");
        assertReference(scan, "record", "record/Component");
        assertReference(scan, "instruction", "instruction/FieldOwner");
        assertReference(scan, "indy", "indy/CallArgument");
        assertReference(scan, "bootstrap", "bootstrap/Owner");
        assertReference(scan, "dynamic", "dynamic/Value");
        assertReference(scan, "frame", "frame/Local");
        assertReference(scan, "array", "array/Element");
        assertReference(scan, "trycatch", "trycatch/Problem");
        assertReference(scan, "local", "local/Generic");
        assertReference(scan, "sealed", "sealed/Child");
    }

    @Test
    void scansModuleDirectivesAndServiceTypes() {
        ClassBytecodeScan scan = scanner.scan("module-info.class", moduleInfoClass());

        assertEquals(1, scan.features().moduleDescriptors());
        assertEquals(7, scan.features().moduleDirectives());
        assertReference(scan, "module", "module/Main");
        assertReference(scan, "service", "service/Api");
        assertReference(scan, "service", "service/Impl");
    }

    @Test
    void aggregateKeepsDefinitionAndReferenceCountsSeparate() {
        ClassBytecodeScan modern = scanner.scan("fixture/FeatureCarrier.class", featureClass());
        ClassBytecodeScan legacy = scanner.scan("fixture/Legacy.class", emptyClass(Opcodes.V1_8, "fixture/Legacy"));

        BytecodeScanAccumulator accumulator = new BytecodeScanAccumulator();
        accumulator.accept(modern);
        accumulator.accept(legacy);
        BytecodeScanAggregate aggregate = accumulator.snapshot();

        assertEquals(2, aggregate.classCount());
        assertEquals(Map.of(52, 1L, 61, 1L), aggregate.classMajorVersions());
        assertEquals(1, aggregate.previewClassCount());
        assertEquals(2, aggregate.namespaces().definition(NamespaceCategory.OTHER).occurrences());
        assertEquals(2, aggregate.namespaces().definition(NamespaceCategory.OTHER).retainedTypeCount());
        assertFalse(aggregate.namespaces().references().isEmpty());
        assertEquals(1, aggregate.features().nativeMethods());
    }

    @Test
    void rejectsDataThatIsNotAClassWithoutTryingToLoadIt() {
        BytecodeScanException exception = assertThrows(
                BytecodeScanException.class,
                () -> scanner.scan("broken.class", new byte[8])
        );

        assertEquals("broken.class", exception.entryName());
        assertTrue(exception.getMessage().contains("CAFEBABE"));
    }

    @Test
    void readsTargetRelevantHeaderBeforeTheClassBodyCanBeParsed() {
        byte[] malformedJava18PreviewClass = rawHeader(0xFFFF, 62);

        ClassFileHeader header = scanner.readHeader("broken/TooNew.class", malformedJava18PreviewClass);

        assertEquals(62, header.majorVersion());
        assertEquals(0xFFFF, header.minorVersion());
        assertTrue(header.preview());
        assertThrows(
                BytecodeScanException.class,
                () -> scanner.scan("broken/TooNew.class", malformedJava18PreviewClass)
        );
    }

    private static void assertReference(ClassBytecodeScan scan, String namespace, String type) {
        assertTrue(
                scan.namespaces().reference(NamespaceCategory.classifyInternalName(type)).types().contains(type),
                () -> "Missing " + type + " in namespace " + namespace
        );
    }

    private static byte[] emptyClass(int version, String name) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(version, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] rawHeader(int minor, int major) {
        return new byte[]{
                (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                (byte) (minor >>> 8), (byte) minor,
                (byte) (major >>> 8), (byte) major
        };
    }

    private static byte[] moduleInfoClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V9, Opcodes.ACC_MODULE, "module-info", null, null, null);
        ModuleVisitor module = writer.visitModule("fixture.module", 0, "1.0");
        module.visitMainClass("module/Main");
        module.visitPackage("module/api");
        module.visitRequire("java.base", Opcodes.ACC_MANDATED, null);
        module.visitExport("module/api", 0, "consumer.one");
        module.visitOpen("module/internal", 0, "consumer.two");
        module.visitUse("service/Api");
        module.visitProvide("service/Api", "service/Impl");
        module.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] featureClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V17 | Opcodes.V_PREVIEW,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_RECORD,
                "fixture/FeatureCarrier",
                "<T:Lsignature/Bound;>Ljava/lang/Record;Lsignature/Contract<TT;>;",
                "java/lang/Record",
                new String[]{"api/Contract"}
        );
        writer.visitNestHost("nest/Host");
        writer.visitNestMember("nest/Member");
        writer.visitPermittedSubclass("sealed/Child");
        writer.visitOuterClass("outer/Owner", "create", "(Louter/Argument;)Louter/Result;");
        writer.visitInnerClass(
                "fixture/FeatureCarrier$Inner",
                "fixture/FeatureCarrier",
                "Inner",
                Opcodes.ACC_PUBLIC
        );

        AnnotationVisitor classAnnotation = writer.visitAnnotation("Lannotation/ClassMarker;", true);
        classAnnotation.visit("type", Type.getType("Lannotation/ValueType;"));
        classAnnotation.visitEnum("kind", "Lannotation/Kind;", "ONE");
        AnnotationVisitor nested = classAnnotation.visitAnnotation("nested", "Lannotation/Nested;");
        nested.visit("nestedType", Type.getType("Lannotation/NestedValue;"));
        nested.visitEnd();
        AnnotationVisitor array = classAnnotation.visitArray("types");
        array.visit(null, Type.getType("Lannotation/ArrayValue;"));
        array.visitEnd();
        classAnnotation.visitEnd();
        writer.visitTypeAnnotation(
                TypeReference.newSuperTypeReference(-1).getValue(),
                null,
                "Lannotation/ClassTypeMarker;",
                true
        ).visitEnd();

        RecordComponentVisitor component = writer.visitRecordComponent(
                "component",
                "Lrecord/Component;",
                "Ljava/util/List<Lrecord/Generic;>;"
        );
        component.visitAnnotation("Lannotation/RecordMarker;", true).visitEnd();
        component.visitTypeAnnotation(
                TypeReference.newTypeReference(TypeReference.FIELD).getValue(),
                null,
                "Lannotation/RecordTypeMarker;",
                true
        ).visitEnd();
        component.visitEnd();

        FieldVisitor field = writer.visitField(
                Opcodes.ACC_PRIVATE,
                "field",
                "Lfield/FieldType;",
                "Ljava/util/List<Lfield/Generic;>;",
                null
        );
        field.visitAnnotation("Lannotation/FieldMarker;", true).visitEnd();
        field.visitTypeAnnotation(
                TypeReference.newTypeReference(TypeReference.FIELD).getValue(),
                null,
                "Lannotation/FieldTypeMarker;",
                true
        ).visitEnd();
        field.visitEnd();

        writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE,
                "nativeCall",
                "(Lnativeapi/Input;)Lnativeapi/Output;",
                null,
                new String[]{"nativeapi/Problem"}
        ).visitEnd();

        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "exercise",
                "(Lmethod/Argument;)Lmethod/Return;",
                "<M:Lgeneric/MethodBound;>(Ljava/util/List<TM;>;)Lgeneric/MethodReturn;^Lgeneric/MethodException;",
                new String[]{"method/Checked"}
        );
        method.visitAnnotation("Lannotation/MethodMarker;", true).visitEnd();
        method.visitTypeAnnotation(
                TypeReference.newTypeReference(TypeReference.METHOD_RETURN).getValue(),
                null,
                "Lannotation/MethodTypeMarker;",
                true
        ).visitEnd();
        method.visitParameterAnnotation(0, "Lannotation/ParameterMarker;", true).visitEnd();

        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        method.visitCode();
        method.visitTryCatchBlock(start, end, handler, "trycatch/Problem");
        method.visitLabel(start);
        method.visitFrame(
                Opcodes.F_FULL,
                1,
                new Object[]{"frame/Local"},
                1,
                new Object[]{"frame/Stack"}
        );
        method.visitTypeInsn(Opcodes.NEW, "instruction/NewType");
        method.visitInsnAnnotation(
                TypeReference.newTypeReference(TypeReference.NEW).getValue(),
                null,
                "Lannotation/InstructionMarker;",
                true
        ).visitEnd();
        method.visitInsn(Opcodes.POP);
        method.visitFieldInsn(
                Opcodes.GETSTATIC,
                "instruction/FieldOwner",
                "VALUE",
                "Linstruction/FieldType;"
        );
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "instruction/MethodOwner",
                "call",
                "(Linstruction/Argument;)Linstruction/Result;",
                false
        );
        method.visitInsn(Opcodes.POP);

        Handle bootstrap = new Handle(
                Opcodes.H_INVOKESTATIC,
                "bootstrap/Owner",
                "bootstrap",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false
        );
        Handle fieldHandle = new Handle(
                Opcodes.H_GETSTATIC,
                "bootstrap/FieldOwner",
                "VALUE",
                "Lbootstrap/FieldValue;",
                false
        );
        Handle dynamicBootstrap = new Handle(
                Opcodes.H_INVOKESTATIC,
                "dynamic/Bootstrap",
                "constant",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
                false
        );
        ConstantDynamic dynamic = new ConstantDynamic(
                "constant",
                "Ldynamic/Value;",
                dynamicBootstrap,
                Type.getType("Ldynamic/Argument;")
        );
        method.visitInvokeDynamicInsn(
                "run",
                "(Lindy/CallArgument;)Lindy/CallResult;",
                bootstrap,
                Type.getMethodType("(Lbootstrap/MethodTypeArgument;)Lbootstrap/MethodTypeResult;"),
                fieldHandle,
                dynamic
        );
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn(Type.getMethodType("(Lldc/Argument;)Lldc/Result;"));
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn(new Handle(
                Opcodes.H_INVOKESTATIC,
                "ldc/HandleOwner",
                "call",
                "(Lldc/HandleArgument;)Lldc/HandleResult;",
                false
        ));
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn(dynamic);
        method.visitInsn(Opcodes.POP);
        method.visitMultiANewArrayInsn("[[Larray/Element;", 2);
        method.visitInsn(Opcodes.POP);
        method.visitLabel(end);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitLabel(handler);
        method.visitInsn(Opcodes.ATHROW);
        method.visitTryCatchAnnotation(
                TypeReference.newTryCatchReference(0).getValue(),
                null,
                "Lannotation/TryCatchMarker;",
                true
        ).visitEnd();
        method.visitLocalVariable(
                "local",
                "Llocal/Value;",
                "Ljava/util/List<Llocal/Generic;>;",
                start,
                end,
                0
        );
        method.visitLocalVariableAnnotation(
                TypeReference.newTypeReference(TypeReference.LOCAL_VARIABLE).getValue(),
                null,
                new Label[]{start},
                new Label[]{end},
                new int[]{0},
                "Lannotation/LocalMarker;",
                true
        ).visitEnd();
        method.visitMaxs(4, 1);
        method.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }
}
