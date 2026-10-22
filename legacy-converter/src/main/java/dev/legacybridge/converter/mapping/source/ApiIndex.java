package dev.legacybridge.converter.mapping.source;

import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.operation.OperationStoppedException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Immutable declaration-only index. Never loads classes or visits method bodies. */
public final class ApiIndex {
    private static final int MAX_MEMBERS = 2_000_000;
    private static final Comparator<ApiMember> MEMBER_ORDER = Comparator.comparing(ApiMember::name)
            .thenComparing(ApiMember::descriptor).thenComparingInt(ApiMember::access);
    private final Map<String, ApiClass> classes;

    public ApiIndex(Map<String, ApiClass> classes) {
        TreeMap<String, ApiClass> copy = new TreeMap<>(classes);
        copy.forEach((name, value) -> {
            OperationControl.checkpoint();
            if (!name.equals(value.name())) throw new IllegalArgumentException("API class key differs from its name");
        });
        this.classes = Collections.unmodifiableMap(copy);
    }

    public static ApiIndex empty() { return new ApiIndex(Map.of()); }
    public boolean hasClass(String internalName) { return classes.containsKey(internalName); }
    public ApiClass getClass(String internalName) { return classes.get(internalName); }
    public Map<String, ApiClass> classes() { return classes; }

    public static ApiIndex read(List<Path> jars) throws IOException {
        return readWithSources(jars, "api", "LOCAL_PROFILE").index();
    }

    static ReadResult readWithSources(List<Path> jars, String id, String origin) throws IOException {
        Map<String, ApiClass> classes = new TreeMap<>();
        List<MappingSource> sources = new ArrayList<>();
        long inflated = 0;
        int entries = 0;
        int members = 0;
        for (int jarIndex = 0; jarIndex < jars.size(); jarIndex++) {
            OperationControl.checkpoint();
            String label = id + "[" + jarIndex + "]";
            MessageDigest digest = BoundedInputs.sha256();
            Set<String> names = new HashSet<>();
            boolean anyEntry = false;
            try (InputStream raw = BoundedInputs.open(jars.get(jarIndex), BoundedInputs.JAR_BYTES, label);
                 DigestInputStream hashed = new DigestInputStream(raw, digest);
                 ZipInputStream zip = new ZipInputStream(hashed)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    OperationControl.checkpoint();
                    anyEntry = true;
                    if (++entries > BoundedInputs.ENTRY_COUNT) throw new IOException("API entry limit exceeded");
                    if (entry.getName().length() > 4096 || !names.add(entry.getName())) {
                        throw new IOException(label + " has an oversized or duplicate entry name");
                    }
                    long remaining = BoundedInputs.INFLATED_BYTES - inflated;
                    if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                        if (entry.getName().startsWith("META-INF/versions/")) {
                            throw new IOException(label + " contains multi-release API classes; select one Java 17 API view");
                        }
                        byte[] bytes = BoundedInputs.read(zip, Math.min(remaining, BoundedInputs.CLASS_BYTES), label + " class");
                        inflated += bytes.length;
                        ApiClass declaration = parse(bytes, label);
                        if (!entry.getName().equals(declaration.name() + ".class")) {
                            throw new IOException(label + " class name does not match its JAR entry");
                        }
                        members += declaration.methods().size() + declaration.fields().size();
                        if (members > MAX_MEMBERS) throw new IOException("API member limit exceeded");
                        ApiClass old = classes.putIfAbsent(declaration.name(), declaration);
                        if (old != null && !old.equals(declaration)) {
                            throw new IOException("Conflicting API declarations for " + declaration.name());
                        }
                    } else {
                        inflated += BoundedInputs.transfer(zip, remaining, label + " resource", null);
                    }
                }
                // Finish hashing bytes after the local-file entries (including the central directory).
                BoundedInputs.transfer(hashed, BoundedInputs.JAR_BYTES, label, null);
            } catch (IllegalArgumentException malformed) {
                throw new IOException(label + " is not a readable API JAR", malformed);
            }
            if (!anyEntry) throw new IOException(label + " has no readable ZIP entries");
            sources.add(new MappingSource(label, HexFormat.of().formatHex(digest.digest()), origin));
        }
        return new ReadResult(new ApiIndex(classes), sources);
    }

    private static ApiClass parse(byte[] bytes, String label) throws IOException {
        List<ApiMember> methods = new ArrayList<>();
        List<ApiMember> fields = new ArrayList<>();
        ApiClass[] declaration = new ApiClass[1];
        try {
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    OperationControl.checkpoint();
                    declaration[0] = new ApiClass(name, access, superName, List.of(interfaces), List.of(), List.of());
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    OperationControl.checkpoint();
                    methods.add(new ApiMember(name, descriptor, access));
                    return null;
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    OperationControl.checkpoint();
                    fields.add(new ApiMember(name, descriptor, access));
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (OperationStoppedException stopped) {
            throw stopped;
        } catch (RuntimeException malformed) {
            throw new IOException(label + " contains malformed or unsupported class bytecode", malformed);
        }
        if (declaration[0] == null) throw new IOException(label + " has no class declaration");
        ApiClass result = declaration[0];
        requireUniqueMembers(methods, label + " methods");
        requireUniqueMembers(fields, label + " fields");
        return new ApiClass(result.name(), result.access(), result.superName(), result.interfaces(), methods, fields);
    }

    private static void requireUniqueMembers(List<ApiMember> members, String label) throws IOException {
        Set<String> signatures = new HashSet<>();
        for (ApiMember member : members) {
            OperationControl.checkpoint();
            if (!signatures.add(member.name() + "\u0000" + member.descriptor())) {
                throw new IOException(label + " contain duplicate declarations for " + member.name() + member.descriptor());
            }
        }
    }

    record ReadResult(ApiIndex index, List<MappingSource> sources) { }

    public record ApiClass(String name, int access, String superName, List<String> interfaces,
                           List<ApiMember> methods, List<ApiMember> fields) {
        public ApiClass {
            Objects.requireNonNull(name, "name");
            interfaces = List.copyOf(interfaces);
            methods = methods.stream().sorted(MEMBER_ORDER).toList();
            fields = fields.stream().sorted(MEMBER_ORDER).toList();
        }
    }

    public record ApiMember(String name, String descriptor, int access) {
        public ApiMember {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(descriptor, "descriptor");
        }
    }
}
