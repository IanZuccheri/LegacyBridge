package dev.legacybridge.converter.bytecode;

import dev.legacybridge.converter.namespace.NamespaceCollector;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStoppedException;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * ASM 9 class-file scanner. It only visits existing structures: it never loads
 * classes, performs reflection, creates a {@code ClassWriter}, or computes
 * stack-map frames. Standard JVM attributes exposed by ASM are covered;
 * payloads of unknown custom attributes intentionally remain opaque.
 */
public final class BytecodeScanner {
    private static final int CLASS_HEADER_LENGTH = 8;
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;

    public ClassBytecodeScan scan(String entryName, byte[] classBytes) {
        return scan(entryName, classBytes, ignored -> { });
    }

    /**
     * Scans one class and synchronously emits every reference occurrence before
     * bounded sample retention. The callback enables exact feature-family or
     * prefix counters without retaining an unbounded archive-wide list.
     */
    public ClassBytecodeScan scan(
            String entryName,
            byte[] classBytes,
            Consumer<BytecodeReference> referenceObserver
    ) {
        if (entryName == null || entryName.isBlank()) {
            throw new IllegalArgumentException("entryName must not be blank");
        }
        Objects.requireNonNull(classBytes, "classBytes");
        Objects.requireNonNull(referenceObserver, "referenceObserver");
        OperationControl.checkpoint();
        ClassFileHeader header = readHeader(entryName, classBytes);

        try {
            ClassReader reader = new ClassReader(classBytes);
            ScanningClassVisitor visitor = new ScanningClassVisitor(
                    entryName,
                    header.minorVersion(),
                    header.majorVersion(),
                    referenceObserver
            );

            // No skip or expand flags: references in original stack-map frames are visited as-is.
            reader.accept(visitor, 0);
            OperationControl.checkpoint();
            return visitor.result();
        } catch (OperationStoppedException stopped) {
            throw stopped;
        } catch (BytecodeScanException exception) {
            throw exception;
        } catch (StackOverflowError error) {
            throw new BytecodeScanException(
                    entryName,
                    "Class structure exceeded the analyzer recursion limit",
                    error
            );
        } catch (RuntimeException exception) {
            throw new BytecodeScanException(
                    entryName,
                    "Could not parse class entry '" + entryName + "': " + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * Reads only the fixed JVM header and does not ask ASM to parse the class.
     * This method therefore remains usable for diagnostic classification after
     * a full {@link #scan(String, byte[])} failure.
     */
    public ClassFileHeader readHeader(String entryName, byte[] classBytes) {
        if (entryName == null || entryName.isBlank()) {
            throw new IllegalArgumentException("entryName must not be blank");
        }
        Objects.requireNonNull(classBytes, "classBytes");
        validateHeader(entryName, classBytes);
        return new ClassFileHeader(readUnsignedShort(classBytes, 4), readUnsignedShort(classBytes, 6));
    }

    public BytecodeScanAggregate scanAll(Map<String, byte[]> classEntries) {
        Objects.requireNonNull(classEntries, "classEntries");
        BytecodeScanAccumulator accumulator = new BytecodeScanAccumulator();
        classEntries.forEach((entryName, bytes) -> accumulator.accept(scan(entryName, bytes)));
        return accumulator.snapshot();
    }

    private static void validateHeader(String entryName, byte[] classBytes) {
        if (classBytes.length < CLASS_HEADER_LENGTH) {
            throw new BytecodeScanException(entryName, "Class entry is shorter than the JVM header");
        }
        int magic = (classBytes[0] & 0xFF) << 24
                | (classBytes[1] & 0xFF) << 16
                | (classBytes[2] & 0xFF) << 8
                | classBytes[3] & 0xFF;
        if (magic != CLASS_FILE_MAGIC) {
            throw new BytecodeScanException(entryName, "Class entry has an invalid CAFEBABE header");
        }
    }

    private static int readUnsignedShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 8 | bytes[offset + 1] & 0xFF;
    }

    private static final class ScanningClassVisitor extends ClassVisitor {
        private final String entryName;
        private final int minorVersion;
        private final int majorVersion;
        private final NamespaceCollector namespaces = new NamespaceCollector();
        private final BytecodeReferenceCollector references;
        private final FeatureCounter features = new FeatureCounter();
        private String internalName;
        private int accessFlags;

        private ScanningClassVisitor(
                String entryName,
                int minorVersion,
                int majorVersion,
                Consumer<BytecodeReference> referenceObserver
        ) {
            super(Opcodes.ASM9);
            this.entryName = entryName;
            this.minorVersion = minorVersion;
            this.majorVersion = majorVersion;
            this.references = new BytecodeReferenceCollector(referenceObserver);
        }

        @Override
        public void visit(
                int version,
                int access,
                String name,
                String signature,
                String superName,
                String[] interfaces
        ) {
            this.internalName = name;
            this.accessFlags = access;
            namespaces.recordDefinition(name);
            hierarchyReference(BytecodeReferenceKind.SUPERCLASS, superName);
            if (interfaces != null) {
                for (String interfaceName : interfaces) {
                    hierarchyReference(BytecodeReferenceKind.INTERFACE, interfaceName);
                }
            }
            classOrMethodSignature(signature);
        }

        @Override
        public ModuleVisitor visitModule(String name, int access, String version) {
            features.moduleDescriptors++;
            return new ScanningModuleVisitor(this);
        }

        @Override
        public void visitNestHost(String nestHost) {
            features.nestHosts++;
            reference(nestHost);
        }

        @Override
        public void visitOuterClass(String owner, String name, String descriptor) {
            if (name != null && descriptor != null) {
                memberReference(BytecodeReferenceKind.METHOD, owner, name, descriptor);
            } else {
                reference(owner);
            }
            descriptor(descriptor);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return annotation(descriptor);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                int typeRef,
                TypePath typePath,
                String descriptor,
                boolean visible
        ) {
            return annotation(descriptor);
        }

        @Override
        public void visitNestMember(String nestMember) {
            features.nestMembers++;
            reference(nestMember);
        }

        @Override
        public void visitPermittedSubclass(String permittedSubclass) {
            features.permittedSubclasses++;
            reference(permittedSubclass);
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            reference(name);
            reference(outerName);
        }

        @Override
        public RecordComponentVisitor visitRecordComponent(
                String name,
                String descriptor,
                String signature
        ) {
            features.recordComponents++;
            descriptor(descriptor);
            typeSignature(signature);
            return new ScanningRecordComponentVisitor(this);
        }

        @Override
        public FieldVisitor visitField(
                int access,
                String name,
                String descriptor,
                String signature,
                Object value
        ) {
            descriptor(descriptor);
            typeSignature(signature);
            constant(value);
            return new ScanningFieldVisitor(this);
        }

        @Override
        public MethodVisitor visitMethod(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions
        ) {
            descriptor(descriptor);
            classOrMethodSignature(signature);
            if (exceptions != null) {
                for (String exception : exceptions) {
                    reference(exception);
                }
            }
            if ((access & Opcodes.ACC_NATIVE) != 0) {
                features.nativeMethods++;
            }
            return new ScanningMethodVisitor(this);
        }

        private ClassBytecodeScan result() {
            if (internalName == null) {
                throw new BytecodeScanException(entryName, "Class entry did not declare a class name");
            }
            return new ClassBytecodeScan(
                    entryName,
                    internalName,
                    minorVersion,
                    majorVersion,
                    minorVersion == 0xFFFF,
                    accessFlags,
                    features.snapshot(),
                    namespaces.snapshot(),
                    references.snapshot()
            );
        }

        private void reference(String internalName) {
            OperationControl.checkpoint();
            if (internalName != null) {
                namespaces.recordReference(internalName);
                references.recordTypeReference(internalName, "");
            }
        }

        private void hierarchyReference(BytecodeReferenceKind kind, String internalName) {
            if (internalName != null) {
                namespaces.recordReference(internalName);
                references.recordHierarchy(kind, internalName);
            }
        }

        private void memberReference(
                BytecodeReferenceKind kind,
                String owner,
                String name,
                String descriptor
        ) {
            namespaces.recordReference(owner);
            references.recordMember(kind, owner, name, descriptor);
        }

        private void descriptor(String descriptor) {
            OperationControl.checkpoint();
            if (descriptor != null) {
                features.descriptorSites++;
                namespaces.recordDescriptor(descriptor);
                references.recordDescriptor(descriptor);
            }
        }

        private void classOrMethodSignature(String signature) {
            if (signature != null) {
                features.signatureSites++;
                namespaces.recordClassOrMethodSignature(signature);
                references.recordClassOrMethodSignature(signature);
            }
        }

        private void typeSignature(String signature) {
            if (signature != null) {
                features.signatureSites++;
                namespaces.recordTypeSignature(signature);
                references.recordTypeSignature(signature);
            }
        }

        private AnnotationVisitor annotation(String descriptor) {
            features.annotationSites++;
            descriptor(descriptor);
            return new ScanningAnnotationVisitor(this);
        }

        private void handle(Handle handle) {
            features.handles++;
            namespaces.recordReference(handle.getOwner());
            references.recordHandle(handle);
            descriptor(handle.getDesc());
        }

        private void constant(Object value) {
            if (value instanceof Type type) {
                descriptor(type.getDescriptor());
            } else if (value instanceof Handle handle) {
                handle(handle);
            } else if (value instanceof ConstantDynamic dynamic) {
                features.constantDynamics++;
                descriptor(dynamic.getDescriptor());
                handle(dynamic.getBootstrapMethod());
                for (int index = 0; index < dynamic.getBootstrapMethodArgumentCount(); index++) {
                    constant(dynamic.getBootstrapMethodArgument(index));
                }
            }
        }

        private void frame(Object[] frameEntries, int count) {
            if (frameEntries == null) {
                return;
            }
            for (int index = 0; index < count; index++) {
                Object frameEntry = frameEntries[index];
                if (frameEntry instanceof String typeName) {
                    reference(typeName);
                }
            }
        }

    }

    private static final class ScanningAnnotationVisitor extends AnnotationVisitor {
        private final ScanningClassVisitor owner;

        private ScanningAnnotationVisitor(ScanningClassVisitor owner) {
            super(Opcodes.ASM9);
            this.owner = owner;
        }

        @Override
        public void visit(String name, Object value) {
            owner.constant(value);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            owner.descriptor(descriptor);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            return owner.annotation(descriptor);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            return new ScanningAnnotationVisitor(owner);
        }
    }

    private static final class ScanningFieldVisitor extends FieldVisitor {
        private final ScanningClassVisitor owner;

        private ScanningFieldVisitor(ScanningClassVisitor owner) {
            super(Opcodes.ASM9);
            this.owner = owner;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return owner.annotation(descriptor);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                int typeRef,
                TypePath typePath,
                String descriptor,
                boolean visible
        ) {
            return owner.annotation(descriptor);
        }
    }

    private static final class ScanningRecordComponentVisitor extends RecordComponentVisitor {
        private final ScanningClassVisitor owner;

        private ScanningRecordComponentVisitor(ScanningClassVisitor owner) {
            super(Opcodes.ASM9);
            this.owner = owner;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return owner.annotation(descriptor);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                int typeRef,
                TypePath typePath,
                String descriptor,
                boolean visible
        ) {
            return owner.annotation(descriptor);
        }
    }

    private static final class ScanningMethodVisitor extends MethodVisitor {
        private final ScanningClassVisitor owner;

        private ScanningMethodVisitor(ScanningClassVisitor owner) {
            super(Opcodes.ASM9);
            this.owner = owner;
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            return new ScanningAnnotationVisitor(owner);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return owner.annotation(descriptor);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                int typeRef,
                TypePath typePath,
                String descriptor,
                boolean visible
        ) {
            return owner.annotation(descriptor);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
            return owner.annotation(descriptor);
        }

        @Override
        public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
            owner.frame(local, numLocal);
            owner.frame(stack, numStack);
        }

        @Override
        public void visitInsn(int opcode) {
            OperationControl.checkpoint();
            owner.features.instructions++;
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            OperationControl.checkpoint();
            owner.features.instructions++;
        }

        @Override
        public void visitVarInsn(int opcode, int variable) {
            OperationControl.checkpoint();
            owner.features.instructions++;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            owner.features.instructions++;
            owner.reference(type);
        }

        @Override
        public void visitFieldInsn(int opcode, String ownerName, String name, String descriptor) {
            owner.features.instructions++;
            owner.memberReference(BytecodeReferenceKind.FIELD, ownerName, name, descriptor);
            owner.descriptor(descriptor);
        }

        @Override
        public void visitMethodInsn(
                int opcode,
                String ownerName,
                String name,
                String descriptor,
                boolean isInterface
        ) {
            owner.features.instructions++;
            owner.memberReference(BytecodeReferenceKind.METHOD, ownerName, name, descriptor);
            owner.descriptor(descriptor);
        }

        @Override
        public void visitInvokeDynamicInsn(
                String name,
                String descriptor,
                Handle bootstrapMethodHandle,
                Object... bootstrapMethodArguments
        ) {
            owner.features.instructions++;
            owner.features.invokeDynamicInstructions++;
            owner.descriptor(descriptor);
            owner.handle(bootstrapMethodHandle);
            for (Object argument : bootstrapMethodArguments) {
                owner.constant(argument);
            }
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            owner.features.instructions++;
        }

        @Override
        public void visitLdcInsn(Object value) {
            owner.features.instructions++;
            owner.constant(value);
        }

        @Override
        public void visitIincInsn(int variable, int increment) {
            owner.features.instructions++;
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            owner.features.instructions++;
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            owner.features.instructions++;
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            owner.features.instructions++;
            owner.descriptor(descriptor);
        }

        @Override
        public AnnotationVisitor visitInsnAnnotation(
                int typeRef,
                TypePath typePath,
                String descriptor,
                boolean visible
        ) {
            return owner.annotation(descriptor);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            owner.reference(type);
        }

        @Override
        public AnnotationVisitor visitTryCatchAnnotation(
                int typeRef,
                TypePath typePath,
                String descriptor,
                boolean visible
        ) {
            return owner.annotation(descriptor);
        }

        @Override
        public void visitLocalVariable(
                String name,
                String descriptor,
                String signature,
                Label start,
                Label end,
                int index
        ) {
            owner.descriptor(descriptor);
            owner.typeSignature(signature);
        }

        @Override
        public AnnotationVisitor visitLocalVariableAnnotation(
                int typeRef,
                TypePath typePath,
                Label[] start,
                Label[] end,
                int[] index,
                String descriptor,
                boolean visible
        ) {
            return owner.annotation(descriptor);
        }
    }

    private static final class ScanningModuleVisitor extends ModuleVisitor {
        private final ScanningClassVisitor owner;

        private ScanningModuleVisitor(ScanningClassVisitor owner) {
            super(Opcodes.ASM9);
            this.owner = owner;
        }

        @Override
        public void visitMainClass(String mainClass) {
            owner.features.moduleDirectives++;
            owner.reference(mainClass);
        }

        @Override
        public void visitPackage(String packageName) {
            owner.features.moduleDirectives++;
        }

        @Override
        public void visitRequire(String module, int access, String version) {
            owner.features.moduleDirectives++;
        }

        @Override
        public void visitExport(String packageName, int access, String... modules) {
            owner.features.moduleDirectives++;
        }

        @Override
        public void visitOpen(String packageName, int access, String... modules) {
            owner.features.moduleDirectives++;
        }

        @Override
        public void visitUse(String service) {
            owner.features.moduleDirectives++;
            owner.reference(service);
        }

        @Override
        public void visitProvide(String service, String... providers) {
            owner.features.moduleDirectives++;
            owner.reference(service);
            if (providers != null) {
                for (String provider : providers) {
                    owner.reference(provider);
                }
            }
        }

    }

    private static final class FeatureCounter {
        private long descriptorSites;
        private long signatureSites;
        private long annotationSites;
        private long instructions;
        private long invokeDynamicInstructions;
        private long handles;
        private long constantDynamics;
        private long nestHosts;
        private long nestMembers;
        private long recordComponents;
        private long moduleDescriptors;
        private long moduleDirectives;
        private long permittedSubclasses;
        private long nativeMethods;

        private BytecodeFeatureCounts snapshot() {
            return new BytecodeFeatureCounts(
                    descriptorSites,
                    signatureSites,
                    annotationSites,
                    instructions,
                    invokeDynamicInstructions,
                    handles,
                    constantDynamics,
                    nestHosts,
                    nestMembers,
                    recordComponents,
                    moduleDescriptors,
                    moduleDirectives,
                    permittedSubclasses,
                    nativeMethods
            );
        }
    }
}
