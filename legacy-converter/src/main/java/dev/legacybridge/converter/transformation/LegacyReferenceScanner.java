package dev.legacybridge.converter.transformation;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStoppedException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Full-occurrence, static pre-transformation scan. No classes are loaded and no
 * bootstrap, annotation, static initializer or other source code is executed.
 * Debug attributes and frames are retained. Unknown custom attributes remain
 * opaque; this scanner does not claim to understand their payloads.
 */
public final class LegacyReferenceScanner {
    public static final int MAX_CLASSES = 512;
    public static final int MAX_CLASS_BYTES = 16 * 1024 * 1024;
    public static final long MAX_TOTAL_CLASS_BYTES = 128L * 1024 * 1024;
    public static final int MAX_REFERENCES = 100_000;
    private static final int MAX_NESTING = 128;
    private static final List<String> PROTECTED_PREFIXES = List.of(
            "net/minecraft/", "net/minecraftforge/", "com/mojang/", "cpw/mods/",
            "java/", "javax/", "jdk/", "sun/", "dev/legacybridge/runtime/");

    public enum ReferenceKind {
        CLASS, METHOD, FIELD, DESCRIPTOR, SUPERCLASS, INTERFACE, ANNOTATION, SIGNATURE, FRAME
    }

    /**
     * One external symbol occurrence, never a sampled or truncated aggregate.
     * instructionIndex is the original ASM InsnList index, including debug and
     * frame nodes; -1 denotes non-instruction metadata. Handles use METHOD or
     * FIELD according to their JVM handle tag. Descriptors retain their full
     * original spelling while owner identifies each referenced object type.
     */
    public record ExternalReference(String sourceClass, ReferenceKind kind, String owner,
                                    String name, String descriptor, String enclosingMethod,
                                    int instructionIndex, int lineNumber, String location) {
    }

    /** Immutable scan data; callers receive fresh trees and defensive byte copies. */
    public record ScannedClass(String entryPath, String internalName, String sourceFile,
                               String sourceDebug, String location, List<ExternalReference> references,
                               byte[] originalBytes) {
        public ScannedClass {
            references = List.copyOf(references);
            originalBytes = originalBytes.clone();
        }

        @Override public byte[] originalBytes() { return originalBytes.clone(); }

        public ClassNode readNode() { return read(entryPath, originalBytes); }
    }

    public record ScanResult(Map<String, ScannedClass> classes, List<ExternalReference> references) {
        public ScanResult {
            classes = Collections.unmodifiableMap(new LinkedHashMap<>(classes));
            references = List.copyOf(references);
        }

        /** A fresh mutable tree per mod class, never a protected platform definition. */
        public Map<String, ClassNode> classNodes() {
            Map<String, ClassNode> result = new LinkedHashMap<>();
            classes.forEach((name, scanned) -> result.put(name, scanned.readNode()));
            return Collections.unmodifiableMap(result);
        }
    }

    public ScanResult scan(Map<String, byte[]> classEntries) {
        Objects.requireNonNull(classEntries, "classEntries");
        if (classEntries.isEmpty() || classEntries.size() > MAX_CLASSES) {
            throw new TransformationException("Class count must be 1.." + MAX_CLASSES);
        }
        if (classEntries.keySet().stream().anyMatch(Objects::isNull)) {
            throw new TransformationException("Class entry path must not be null");
        }
        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        long totalBytes = 0;
        for (var entry : new TreeMap<>(classEntries).entrySet()) {
            OperationControl.checkpoint();
            String path = entry.getKey();
            byte[] original = entry.getValue();
            if (path == null || original == null || original.length > MAX_CLASS_BYTES) {
                throw new TransformationException("Missing class bytes/path or class exceeds 16 MiB: " + path);
            }
            totalBytes += original.length;
            if (totalBytes > MAX_TOTAL_CLASS_BYTES) {
                throw new TransformationException("Class bytecode exceeds 128 MiB scan limit");
            }
            ClassNode node = read(path, original);
            if (isProtectedDefinition(node.name)) {
                throw new TransformationException("UNSUPPORTED: protected class definition must never be transformed: "
                        + node.name + " in " + path);
            }
            if (!validInternalName(node.name) || !path.equals(node.name + ".class")) {
                throw new TransformationException("Class entry does not match declared internal name: "
                        + path + " declares " + node.name);
            }
            if (nodes.putIfAbsent(node.name, node) != null) {
                throw new TransformationException("Duplicate source class definition: " + node.name);
            }
            bytes.put(node.name, original.clone());
        }
        Budget budget = new Budget();
        Map<String, ScannedClass> classes = new LinkedHashMap<>();
        List<ExternalReference> allReferences = new ArrayList<>();
        for (var entry : nodes.entrySet()) {
            OperationControl.checkpoint();
            ClassNode node = entry.getValue();
            Collector collector = new Collector(node, nodes.keySet(), budget);
            try {
                collector.scan();
            } catch (OperationStoppedException stopped) {
                throw stopped;
            } catch (TransformationException exception) {
                throw exception;
            } catch (RuntimeException | StackOverflowError exception) {
                throw new TransformationException("Malformed or excessively nested class references in "
                        + node.name + ": " + exception.getMessage(), exception);
            }
            ScannedClass scanned = new ScannedClass(node.name + ".class", node.name, node.sourceFile,
                    node.sourceDebug, collector.baseLocation, collector.references, bytes.get(node.name));
            classes.put(node.name, scanned);
            allReferences.addAll(scanned.references());
        }
        return new ScanResult(classes, allReferences);
    }

    public static boolean isProtectedDefinition(String internalName) {
        return internalName != null && PROTECTED_PREFIXES.stream().anyMatch(internalName::startsWith);
    }

    private static boolean validInternalName(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.endsWith("/")) return false;
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.indexOf('.') >= 0
                    || part.indexOf(';') >= 0 || part.indexOf('[') >= 0 || part.indexOf('\\') >= 0
                    || part.indexOf('\0') >= 0) return false;
        }
        return true;
    }

    private static ClassNode read(String entryPath, byte[] bytes) {
        OperationControl.checkpoint();
        if (bytes.length < 8 || bytes[0] != (byte) 0xca || bytes[1] != (byte) 0xfe
                || bytes[2] != (byte) 0xba || bytes[3] != (byte) 0xbe) {
            throw new TransformationException("Invalid CAFEBABE class header: " + entryPath);
        }
        try {
            ClassNode result = new ClassNode(Opcodes.ASM9);
            new ClassReader(bytes).accept(result, 0); // Deliberately do not SKIP_DEBUG or SKIP_FRAMES.
            return result;
        } catch (RuntimeException | StackOverflowError exception) {
            throw new TransformationException("Cannot parse class " + entryPath + ": "
                    + exception.getMessage(), exception);
        }
    }

    private static final class Budget {
        int occurrences;
        void count() {
            OperationControl.checkpoint();
            if (++occurrences > MAX_REFERENCES) {
                throw new TransformationException("Reference scan exceeds " + MAX_REFERENCES
                        + " occurrences; no references were silently truncated");
            }
        }
    }

    private static final class Collector {
        private final ClassNode node;
        private final Set<String> localClasses;
        private final Budget budget;
        private final List<ExternalReference> references = new ArrayList<>();
        private final String baseLocation;
        private String method = "";
        private int instruction = -1;
        private int line = -1;

        Collector(ClassNode node, Set<String> localClasses, Budget budget) {
            this.node = node;
            this.localClasses = localClasses;
            this.budget = budget;
            String source = node.sourceFile;
            // SourceFile is untrusted metadata, never a filesystem path to open.
            boolean simpleSourceName = source != null && !source.isBlank() && !source.contains("/")
                    && !source.contains("\\") && !source.equals(".") && !source.equals("..");
            baseLocation = simpleSourceName ? node.name.substring(0, node.name.lastIndexOf('/') + 1) + source
                    : node.name + ".class";
        }

        void scan() {
            owner(ReferenceKind.SUPERCLASS, node.superName);
            each(node.interfaces, name -> owner(ReferenceKind.INTERFACE, name));
            signature(node.signature, false);
            owner(ReferenceKind.CLASS, node.outerClass);
            if (node.outerMethod != null) member(ReferenceKind.METHOD, node.outerClass, node.outerMethod, node.outerMethodDesc, "");
            owner(ReferenceKind.CLASS, node.nestHostClass);
            each(node.nestMembers, name -> owner(ReferenceKind.CLASS, name));
            each(node.permittedSubclasses, name -> owner(ReferenceKind.CLASS, name));
            each(node.innerClasses, inner -> {
                owner(ReferenceKind.CLASS, inner.name);
                owner(ReferenceKind.CLASS, inner.outerName);
            });
            annotations(node.visibleAnnotations); annotations(node.invisibleAnnotations);
            annotations(node.visibleTypeAnnotations); annotations(node.invisibleTypeAnnotations);
            if (node.module != null) {
                owner(ReferenceKind.CLASS, node.module.mainClass);
                each(node.module.uses, name -> owner(ReferenceKind.CLASS, name));
                each(node.module.provides, provide -> {
                    owner(ReferenceKind.CLASS, provide.service);
                    each(provide.providers, name -> owner(ReferenceKind.CLASS, name));
                });
            }
            each(node.recordComponents, component -> {
                descriptor(component.descriptor); signature(component.signature, true);
                annotations(component.visibleAnnotations); annotations(component.invisibleAnnotations);
                annotations(component.visibleTypeAnnotations); annotations(component.invisibleTypeAnnotations);
            });
            each(node.fields, field -> {
                descriptor(field.desc); signature(field.signature, true); constant(field.value, 0);
                annotations(field.visibleAnnotations); annotations(field.invisibleAnnotations);
                annotations(field.visibleTypeAnnotations); annotations(field.invisibleTypeAnnotations);
            });
            each(node.methods, this::scanMethod);
        }

        private void scanMethod(MethodNode current) {
            method = current.name + current.desc;
            instruction = -1; line = -1;
            descriptor(current.desc); signature(current.signature, false);
            each(current.exceptions, exception -> owner(ReferenceKind.CLASS, exception));
            annotations(current.visibleAnnotations); annotations(current.invisibleAnnotations);
            annotations(current.visibleTypeAnnotations); annotations(current.invisibleTypeAnnotations);
            if (current.visibleParameterAnnotations != null) {
                for (var annotations : current.visibleParameterAnnotations) annotations(annotations);
            }
            if (current.invisibleParameterAnnotations != null) {
                for (var annotations : current.invisibleParameterAnnotations) annotations(annotations);
            }
            annotationValue(current.annotationDefault, 0);
            each(current.tryCatchBlocks, block -> {
                owner(ReferenceKind.CLASS, block.type);
                annotations(block.visibleTypeAnnotations); annotations(block.invisibleTypeAnnotations);
            });
            each(current.localVariables, local -> { descriptor(local.desc); signature(local.signature, true); });
            annotations(current.visibleLocalVariableAnnotations); annotations(current.invisibleLocalVariableAnnotations);
            for (AbstractInsnNode insn : current.instructions) {
                OperationControl.checkpoint();
                instruction++;
                if (insn instanceof LineNumberNode number) line = number.line;
                else if (insn instanceof TypeInsnNode type) owner(ReferenceKind.CLASS, type.desc);
                else if (insn instanceof FieldInsnNode field) member(ReferenceKind.FIELD, field.owner, field.name, field.desc, "");
                else if (insn instanceof MethodInsnNode call) member(ReferenceKind.METHOD, call.owner, call.name, call.desc, "");
                else if (insn instanceof InvokeDynamicInsnNode dynamic) {
                    descriptor(dynamic.desc); handle(dynamic.bsm);
                    for (Object argument : dynamic.bsmArgs) constant(argument, 0);
                } else if (insn instanceof LdcInsnNode ldc) constant(ldc.cst, 0);
                else if (insn instanceof MultiANewArrayInsnNode array) descriptor(array.desc);
                else if (insn instanceof FrameNode frame) {
                    frame(frame.local); frame(frame.stack);
                }
                annotations(insn.visibleTypeAnnotations); annotations(insn.invisibleTypeAnnotations);
            }
        }

        private void frame(List<Object> entries) {
            each(entries, entry -> { if (entry instanceof String name) owner(ReferenceKind.FRAME, name); });
        }

        private void owner(ReferenceKind kind, String name) {
            if (name == null) return;
            if (name.startsWith("[")) {
                type(Type.getType(name), name, kind);
            } else add(kind, name, "", "", "");
        }

        private void member(ReferenceKind kind, String owner, String name, String desc, String context) {
            add(kind, owner, name, desc, context);
            descriptor(desc);
        }

        private void descriptor(String descriptor) {
            if (descriptor != null) type(Type.getType(descriptor), descriptor, ReferenceKind.DESCRIPTOR);
        }

        private void type(Type type, String descriptor, ReferenceKind kind) {
            switch (type.getSort()) {
                case Type.OBJECT -> add(kind, type.getInternalName(), "", descriptor, "");
                case Type.ARRAY -> type(type.getElementType(), descriptor, kind);
                case Type.METHOD -> {
                    for (Type argument : type.getArgumentTypes()) type(argument, descriptor, kind);
                    type(type.getReturnType(), descriptor, kind);
                }
                default -> { }
            }
        }

        private void handle(Handle handle) {
            int tag = handle.getTag();
            ReferenceKind kind = tag >= Opcodes.H_GETFIELD && tag <= Opcodes.H_PUTSTATIC
                    ? ReferenceKind.FIELD : ReferenceKind.METHOD;
            member(kind, handle.getOwner(), handle.getName(), handle.getDesc(), "handle tag=" + tag);
        }

        private void constant(Object value, int depth) {
            nesting(depth);
            if (value instanceof Type type) type(type, type.getDescriptor(), ReferenceKind.DESCRIPTOR);
            else if (value instanceof Handle handle) handle(handle);
            else if (value instanceof ConstantDynamic dynamic) {
                descriptor(dynamic.getDescriptor()); handle(dynamic.getBootstrapMethod());
                for (int index = 0; index < dynamic.getBootstrapMethodArgumentCount(); index++) {
                    constant(dynamic.getBootstrapMethodArgument(index), depth + 1);
                }
            }
        }

        private void annotations(List<? extends AnnotationNode> annotations) {
            each(annotations, annotation -> annotation(annotation, 0));
        }

        private void annotation(AnnotationNode annotation, int depth) {
            nesting(depth);
            type(Type.getType(annotation.desc), annotation.desc, ReferenceKind.ANNOTATION);
            if (annotation.values != null) {
                for (int index = 1; index < annotation.values.size(); index += 2) {
                    annotationValue(annotation.values.get(index), depth + 1);
                }
            }
        }

        private void annotationValue(Object value, int depth) {
            nesting(depth);
            if (value instanceof AnnotationNode annotation) annotation(annotation, depth + 1);
            else if (value instanceof List<?> values) {
                for (Object element : values) annotationValue(element, depth + 1);
            } else if (value instanceof String[] enumValue && enumValue.length == 2) {
                String enumOwner = Type.getType(enumValue[0]).getInternalName();
                member(ReferenceKind.FIELD, enumOwner, enumValue[1], enumValue[0], "annotation enum");
            } else constant(value, depth + 1);
        }

        private void signature(String signature, boolean typeSignature) {
            if (signature == null) return;
            SignatureReader reader = new SignatureReader(signature);
            SignatureVisitor visitor = signatureVisitor(signature, 0);
            if (typeSignature) reader.acceptType(visitor); else reader.accept(visitor);
        }

        private SignatureVisitor signatureVisitor(String signature, int depth) {
            nesting(depth);
            return new SignatureVisitor(Opcodes.ASM9) {
                private String outer;
                @Override public void visitClassType(String name) {
                    outer = name; add(ReferenceKind.SIGNATURE, name, "", signature, "");
                }
                @Override public void visitInnerClassType(String name) {
                    outer += "$" + name; add(ReferenceKind.SIGNATURE, outer, "", signature, "");
                }
                private SignatureVisitor child() { return signatureVisitor(signature, depth + 1); }
                @Override public SignatureVisitor visitClassBound() { return child(); }
                @Override public SignatureVisitor visitInterfaceBound() { return child(); }
                @Override public SignatureVisitor visitSuperclass() { return child(); }
                @Override public SignatureVisitor visitInterface() { return child(); }
                @Override public SignatureVisitor visitParameterType() { return child(); }
                @Override public SignatureVisitor visitReturnType() { return child(); }
                @Override public SignatureVisitor visitExceptionType() { return child(); }
                @Override public SignatureVisitor visitArrayType() { return child(); }
                @Override public SignatureVisitor visitTypeArgument(char wildcard) { return child(); }
            };
        }

        private void add(ReferenceKind kind, String owner, String name, String descriptor, String context) {
            if (owner == null) return;
            budget.count();
            if (localClasses.contains(owner)) return;
            String location = baseLocation + (line >= 0 ? ":" + line : "")
                    + (context.isEmpty() ? "" : " (" + context + ")");
            references.add(new ExternalReference(node.name, kind, owner, name, descriptor, method,
                    instruction, line, location));
        }

        private static void nesting(int depth) {
            if (depth > MAX_NESTING) {
                throw new TransformationException("Reference nesting exceeds " + MAX_NESTING);
            }
        }

        private static <T> void each(List<T> values, java.util.function.Consumer<T> visitor) {
            if (values != null) values.forEach(visitor);
        }
    }
}
