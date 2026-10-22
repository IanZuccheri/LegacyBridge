package dev.legacybridge.converter.namespace;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Collects class definitions and statically discovered type references.
 *
 * <p>Names are kept in JVM internal form (for example
 * {@code net/minecraft/world/item/Item}); names are classified immediately
 * into a fixed set of report categories. Primitive and void descriptors do not
 * create entries.</p>
 */
public final class NamespaceCollector {
    private final Map<NamespaceCategory, MutableUsage> definitions =
            new EnumMap<>(NamespaceCategory.class);
    private final Map<NamespaceCategory, MutableUsage> references =
            new EnumMap<>(NamespaceCategory.class);

    public void recordDefinition(String internalName) {
        record(definitions, normalizeInternalName(internalName));
    }

    public void recordReference(String internalName) {
        Objects.requireNonNull(internalName, "internalName");
        if (internalName.startsWith("[") || (internalName.startsWith("L") && internalName.endsWith(";"))) {
            recordDescriptor(internalName);
            return;
        }
        record(references, normalizeInternalName(internalName));
    }

    /** Records every object type embedded in a field, method, or array descriptor. */
    public void recordDescriptor(String descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        recordType(Type.getType(descriptor));
    }

    /** Records types from a class or method generic signature. */
    public void recordClassOrMethodSignature(String signature) {
        if (signature == null) {
            return;
        }
        new SignatureReader(signature).accept(new TypeCollectingSignatureVisitor(this));
    }

    /** Records types from a field, record component, or local-variable generic signature. */
    public void recordTypeSignature(String signature) {
        if (signature == null) {
            return;
        }
        new SignatureReader(signature).acceptType(new TypeCollectingSignatureVisitor(this));
    }

    public NamespaceSummary snapshot() {
        return new NamespaceSummary(freeze(definitions), freeze(references));
    }

    private void recordType(Type type) {
        switch (type.getSort()) {
            case Type.ARRAY -> recordType(type.getElementType());
            case Type.OBJECT -> recordReference(type.getInternalName());
            case Type.METHOD -> {
                for (Type argument : type.getArgumentTypes()) {
                    recordType(argument);
                }
                recordType(type.getReturnType());
            }
            default -> {
                // Primitive, void, and method type variables have no class namespace.
            }
        }
    }

    private static void record(Map<NamespaceCategory, MutableUsage> target, String internalName) {
        NamespaceCategory category = NamespaceCategory.classifyInternalName(internalName);
        target.computeIfAbsent(category, ignored -> new MutableUsage()).record(internalName);
    }

    private static String normalizeInternalName(String internalName) {
        Objects.requireNonNull(internalName, "internalName");
        String normalized = internalName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("internalName must not be blank");
        }
        if (normalized.indexOf('.') >= 0) {
            throw new IllegalArgumentException("Expected a JVM internal name, got: " + internalName);
        }
        return normalized;
    }

    private static Map<NamespaceCategory, NamespaceUsage> freeze(
            Map<NamespaceCategory, MutableUsage> source
    ) {
        Map<NamespaceCategory, NamespaceUsage> result = new EnumMap<>(NamespaceCategory.class);
        source.forEach((category, usage) -> result.put(category, usage.freeze()));
        return result;
    }

    private static final class MutableUsage {
        private long occurrences;
        private final TreeSet<String> types = new TreeSet<>();
        private boolean truncated;

        private void record(String internalName) {
            occurrences = Math.addExact(occurrences, 1);
            retain(internalName);
        }

        private void retain(String internalName) {
            if (!types.add(internalName)) {
                return;
            }
            if (types.size() > NamespaceUsage.RETAINED_TYPE_LIMIT) {
                types.pollLast();
                truncated = true;
            }
        }

        private NamespaceUsage freeze() {
            return new NamespaceUsage(occurrences, types, truncated);
        }
    }

    private static final class TypeCollectingSignatureVisitor extends SignatureVisitor {
        private final NamespaceCollector collector;
        private String currentClassName;

        private TypeCollectingSignatureVisitor(NamespaceCollector collector) {
            super(Opcodes.ASM9);
            this.collector = collector;
        }

        @Override
        public void visitClassType(String name) {
            currentClassName = name;
            collector.recordReference(name);
        }

        @Override
        public void visitInnerClassType(String name) {
            currentClassName = currentClassName == null ? name : currentClassName + '$' + name;
            collector.recordReference(currentClassName);
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
            return new TypeCollectingSignatureVisitor(collector);
        }
    }
}
