package dev.legacybridge.converter.transformation;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Class-file-only hierarchy queries. No class is defined, linked, or initialized. */
final class ClassHierarchy {
    private static final int MAX_CLASS_BYTES = 16 * 1024 * 1024;
    private static final Type OBJECT = Type.getObjectType("java/lang/Object");
    private final Map<String, byte[]> classes = new HashMap<>();
    private final Map<String, Header> headers = new HashMap<>();

    ClassHierarchy(Map<String, byte[]> suppliedClasses) {
        suppliedClasses.forEach((key, bytes) -> {
            ClassReader reader = new ClassReader(bytes);
            String name = reader.getClassName();
            if (!key.equals(name) && !key.equals(name + ".class")) {
                throw new TransformationException("Hierarchy entry name differs from its class file: " + key);
            }
            if (classes.putIfAbsent(name, bytes.clone()) != null) {
                throw new TransformationException("Duplicate hierarchy class: " + name);
            }
        });
    }

    boolean isInterface(Type type) {
        return type.getSort() == Type.OBJECT && header(type.getInternalName()).isInterface();
    }

    Type superType(Type type) {
        if (type.getSort() == Type.ARRAY) return OBJECT;
        String parent = header(type.getInternalName()).superName();
        return parent == null ? null : Type.getObjectType(parent);
    }

    boolean isAssignable(Type target, Type source) {
        if (target.equals(source)) return true;
        if (!reference(target) || !reference(source)) return false;
        if (source.getSort() == Type.ARRAY) {
            if (target.getSort() == Type.ARRAY) {
                return isAssignable(component(target), component(source));
            }
            header(target.getInternalName());
            return target.equals(OBJECT) || target.getInternalName().equals("java/lang/Cloneable")
                    || target.getInternalName().equals("java/io/Serializable");
        }
        if (target.getSort() == Type.ARRAY) return false;
        // Resolve both ends before returning a broad Object match: an absent dependency is not proof.
        header(target.getInternalName());
        header(source.getInternalName());
        var queue = new ArrayDeque<String>();
        var visited = new HashSet<String>();
        queue.add(source.getInternalName());
        while (!queue.isEmpty()) {
            String next = queue.removeFirst();
            if (!visited.add(next)) continue;
            if (next.equals(target.getInternalName())) return true;
            Header current = header(next);
            if (current.superName() != null) queue.add(current.superName());
            queue.addAll(current.interfaces());
            if (visited.size() > 8192) throw new TransformationException("Hierarchy traversal limit exceeded");
        }
        return false;
    }

    String commonSuperClass(String first, String second) {
        return common(type(first), type(second)).getInternalName();
    }

    private Type common(Type first, Type second) {
        ensureAvailable(first);
        ensureAvailable(second);
        if (isAssignable(first, second)) return first;
        if (isAssignable(second, first)) return second;
        if (first.getSort() == Type.ARRAY && second.getSort() == Type.ARRAY) {
            Type firstElement = component(first);
            Type secondElement = component(second);
            if (reference(firstElement) && reference(secondElement)) {
                return Type.getType("[" + common(firstElement, secondElement).getDescriptor());
            }
            return OBJECT;
        }
        if (first.getSort() == Type.ARRAY || second.getSort() == Type.ARRAY
                || isInterface(first) || isInterface(second)) return OBJECT;
        Set<Type> visited = new HashSet<>();
        Type candidate = first;
        while ((candidate = superType(candidate)) != null) {
            if (!visited.add(candidate)) throw new TransformationException("Cyclic hierarchy at " + candidate);
            if (isAssignable(candidate, second)) return candidate;
        }
        throw new TransformationException("No proven common superclass for " + first + " and " + second);
    }

    private void ensureAvailable(Type type) {
        if (type.getSort() == Type.ARRAY) ensureAvailable(component(type));
        else if (type.getSort() == Type.OBJECT) header(type.getInternalName());
    }

    private Header header(String name) {
        Header cached = headers.get(name);
        if (cached != null) return cached;
        // Platform metadata cannot be shadowed by bytes from a mod JAR.
        byte[] bytes = platformName(name) ? readPlatform(name) : classes.get(name);
        if (bytes == null && name.startsWith("dev/legacybridge/runtime/")) {
            bytes = readRuntime(name);
        }
        if (bytes == null) {
            throw new TransformationException("Missing offline hierarchy class: " + name
                    + "; refusing to guess java/lang/Object or load mod classes");
        }
        ClassReader reader = new ClassReader(bytes);
        if (!reader.getClassName().equals(name)) {
            throw new TransformationException("Hierarchy resource has a different class name: " + name);
        }
        Header result = new Header(reader.getSuperName(), List.of(reader.getInterfaces()),
                (reader.getAccess() & Opcodes.ACC_INTERFACE) != 0);
        headers.put(name, result);
        return result;
    }

    private static boolean platformName(String name) {
        return name.startsWith("java/") || name.startsWith("javax/")
                || name.startsWith("jdk/") || name.startsWith("sun/");
    }

    private static byte[] readPlatform(String name) {
        return read(ClassLoader.getPlatformClassLoader().getResourceAsStream(name + ".class"), name);
    }

    private static byte[] readRuntime(String name) {
        return read(ClassHierarchy.class.getClassLoader().getResourceAsStream(name + ".class"), name);
    }

    private static byte[] read(InputStream input, String name) {
        if (input == null) return null;
        try (input) {
            byte[] bytes = input.readNBytes(MAX_CLASS_BYTES + 1);
            if (bytes.length > MAX_CLASS_BYTES) throw new TransformationException("Oversized hierarchy class: " + name);
            return bytes;
        } catch (IOException exception) {
            throw new TransformationException("Cannot read offline hierarchy class: " + name + ": " + exception.getMessage());
        }
    }

    private static Type type(String name) {
        return name.startsWith("[") ? Type.getType(name) : Type.getObjectType(name);
    }

    private static Type component(Type array) {
        return Type.getType(array.getDescriptor().substring(1));
    }

    private static boolean reference(Type type) {
        return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
    }

    private record Header(String superName, List<String> interfaces, boolean isInterface) { }
}
