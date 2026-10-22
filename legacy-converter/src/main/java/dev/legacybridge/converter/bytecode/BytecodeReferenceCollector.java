package dev.legacybridge.converter.bytecode;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.Objects;
import java.util.function.Consumer;

/** Package-local collector used alongside the namespace collector during an ASM visit. */
final class BytecodeReferenceCollector {
    private final BytecodeReferenceInventory.Accumulator references =
            BytecodeReferenceInventory.accumulator();
    private final Consumer<BytecodeReference> observer;

    BytecodeReferenceCollector(Consumer<BytecodeReference> observer) {
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    void recordHierarchy(BytecodeReferenceKind kind, String owner) {
        if (owner != null) {
            record(BytecodeReference.hierarchy(kind, owner));
        }
    }

    void recordMember(
            BytecodeReferenceKind kind,
            String owner,
            String memberName,
            String descriptor
    ) {
        record(BytecodeReference.member(kind, owner, memberName, descriptor));
    }

    void recordTypeReference(String typeName, String sourceDescriptor) {
        if (typeName == null) {
            return;
        }
        if (typeName.startsWith("[") || (typeName.startsWith("L") && typeName.endsWith(";"))) {
            recordDescriptor(typeName);
            return;
        }
        record(BytecodeReference.type(typeName, sourceDescriptor));
    }

    void recordDescriptor(String descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        recordType(Type.getType(descriptor), descriptor);
    }

    void recordClassOrMethodSignature(String signature) {
        if (signature != null) {
            new SignatureReader(signature).accept(new ReferenceSignatureVisitor(this, signature));
        }
    }

    void recordTypeSignature(String signature) {
        if (signature != null) {
            new SignatureReader(signature).acceptType(new ReferenceSignatureVisitor(this, signature));
        }
    }

    void recordHandle(Handle handle) {
        Objects.requireNonNull(handle, "handle");
        BytecodeReferenceKind kind = switch (handle.getTag()) {
            case Opcodes.H_GETFIELD, Opcodes.H_GETSTATIC, Opcodes.H_PUTFIELD, Opcodes.H_PUTSTATIC ->
                    BytecodeReferenceKind.FIELD;
            case Opcodes.H_INVOKEVIRTUAL, Opcodes.H_INVOKESTATIC, Opcodes.H_INVOKESPECIAL,
                    Opcodes.H_NEWINVOKESPECIAL, Opcodes.H_INVOKEINTERFACE ->
                    BytecodeReferenceKind.METHOD;
            default -> throw new IllegalArgumentException("Unsupported ASM handle tag: " + handle.getTag());
        };
        recordMember(kind, handle.getOwner(), handle.getName(), handle.getDesc());
    }

    BytecodeReferenceInventory snapshot() {
        return references.snapshot();
    }

    private void record(BytecodeReference reference) {
        references.record(reference);
        observer.accept(reference);
    }

    private void recordType(Type type, String sourceDescriptor) {
        switch (type.getSort()) {
            case Type.ARRAY -> recordType(type.getElementType(), sourceDescriptor);
            case Type.OBJECT -> record(BytecodeReference.type(type.getInternalName(), sourceDescriptor));
            case Type.METHOD -> {
                for (Type argument : type.getArgumentTypes()) {
                    recordType(argument, sourceDescriptor);
                }
                recordType(type.getReturnType(), sourceDescriptor);
            }
            default -> {
                // Primitive and void values do not identify an external owner.
            }
        }
    }

    private static final class ReferenceSignatureVisitor extends SignatureVisitor {
        private final BytecodeReferenceCollector collector;
        private final String sourceSignature;
        private String currentClassName;

        private ReferenceSignatureVisitor(BytecodeReferenceCollector collector, String sourceSignature) {
            super(Opcodes.ASM9);
            this.collector = collector;
            this.sourceSignature = sourceSignature;
        }

        @Override
        public void visitClassType(String name) {
            currentClassName = name;
            collector.recordTypeReference(name, sourceSignature);
        }

        @Override
        public void visitInnerClassType(String name) {
            currentClassName = currentClassName == null ? name : currentClassName + '$' + name;
            collector.recordTypeReference(currentClassName, sourceSignature);
        }

        @Override
        public SignatureVisitor visitClassBound() {
            return child();
        }

        @Override
        public SignatureVisitor visitInterfaceBound() {
            return child();
        }

        @Override
        public SignatureVisitor visitSuperclass() {
            return child();
        }

        @Override
        public SignatureVisitor visitInterface() {
            return child();
        }

        @Override
        public SignatureVisitor visitParameterType() {
            return child();
        }

        @Override
        public SignatureVisitor visitReturnType() {
            return child();
        }

        @Override
        public SignatureVisitor visitExceptionType() {
            return child();
        }

        @Override
        public SignatureVisitor visitArrayType() {
            return child();
        }

        @Override
        public SignatureVisitor visitTypeArgument(char wildcard) {
            return child();
        }

        private SignatureVisitor child() {
            return new ReferenceSignatureVisitor(collector, sourceSignature);
        }
    }
}
