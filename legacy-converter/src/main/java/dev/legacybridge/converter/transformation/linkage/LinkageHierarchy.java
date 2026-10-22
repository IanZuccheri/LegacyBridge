package dev.legacybridge.converter.transformation.linkage;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.ApiIndex.ApiClass;
import dev.legacybridge.converter.mapping.source.ApiIndex.ApiMember;
import dev.legacybridge.converter.operation.OperationControl;
import dev.legacybridge.converter.transformation.TransformationException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/** Declaration-only graph. Platform resources must originate in jrt:, never the application/mod classpath. */
final class LinkageHierarchy {
    static final int MAX_TYPES = 65_536;
    static final int MAX_WALK = 8192;
    static final Type NULL = Type.getObjectType("null");
    private final ApiIndex external;
    private final Map<String, ApiClass> owned;
    private final Map<String, ApiClass> resolved = new HashMap<>();
    private final Set<String> graphChecked = new HashSet<>();
    private final Map<String, List<String>> platformPermits = new HashMap<>();

    LinkageHierarchy(Map<String, ClassNode> classes, ApiIndex external) {
        if (external == null) fail("MISSING_API_INDEX", "External declarations are required");
        this.external = external;
        owned = new TreeMap<>();
        for (ClassNode node : classes.values()) {
            owned.put(node.name, new ApiClass(node.name, node.access, node.superName, node.interfaces,
                    node.methods.stream().map(method -> new ApiMember(method.name, method.desc, method.access)).toList(),
                    node.fields.stream().map(field -> new ApiMember(field.name, field.desc, field.access)).toList()));
        }
    }

    boolean owned(String name) { return owned.containsKey(name); }
    int resolvedCount() { return resolved.size(); }
    boolean isInterface(String name) { return (type(name).access() & Opcodes.ACC_INTERFACE) != 0; }

    ApiClass type(String name) {
        OperationControl.checkpoint();
        if (name == null || name.startsWith("[") || name.indexOf('.') >= 0 || name.isBlank()) fail("INVALID_CLASS_NAME", String.valueOf(name));
        ApiClass cached = resolved.get(name);
        if (cached != null) return cached;
        if (resolved.size() >= MAX_TYPES) fail("LINKAGE_LIMIT", "Resolved declaration limit");
        ApiClass value = platform(name) ? platformType(name) : owned.getOrDefault(name, external.getClass(name));
        if (value == null) fail("MISSING_CLASS", name);
        if (!name.equals(value.name())) fail("INVALID_API_DECLARATION", name);
        unique(value.methods(), name, "method"); unique(value.fields(), name, "field");
        resolved.put(name, value);
        return value;
    }

    void graph(String name) { walkGraph(name, new HashSet<>(), 0); }
    private void walkGraph(String name, Set<String> pending, int depth) {
        OperationControl.checkpoint();
        if (graphChecked.contains(name)) return;
        if (depth > 256 || pending.size() >= MAX_WALK) fail("LINKAGE_LIMIT", "Hierarchy depth");
        if (!pending.add(name)) fail("HIERARCHY_CYCLE", name);
        ApiClass declaration = type(name);
        if (declaration.superName() == null) {
            if (!name.equals("java/lang/Object")) fail("INVALID_HIERARCHY", "Missing superclass: " + name);
        } else {
            ApiClass parent = type(declaration.superName());
            if ((parent.access() & Opcodes.ACC_INTERFACE) != 0) fail("INVALID_HIERARCHY", "Superclass is an interface: " + name);
            if ((parent.access() & Opcodes.ACC_FINAL) != 0) fail("FINAL_SUPERCLASS", name + " extends " + parent.name());
            checkPlatformSealedEdge(name, parent.name());
            if ((declaration.access() & Opcodes.ACC_INTERFACE) != 0 && !parent.name().equals("java/lang/Object"))
                fail("INVALID_HIERARCHY", "Interface superclass is not Object: " + name);
            walkGraph(parent.name(), pending, depth + 1);
        }
        Set<String> interfaces = new HashSet<>();
        for (String parent : declaration.interfaces()) {
            if (!interfaces.add(parent) || !isInterface(parent)) fail("INVALID_HIERARCHY", "Invalid/duplicate interface on " + name);
            checkPlatformSealedEdge(name, parent);
            walkGraph(parent, pending, depth + 1);
        }
        pending.remove(name); graphChecked.add(name);
    }

    private void checkPlatformSealedEdge(String child, String parent) {
        List<String> permitted = platformPermits.get(parent);
        if (owned(child) && permitted != null && !permitted.contains(child)) fail("SEALED_PLATFORM_PARENT", child + " -> " + parent);
    }

    boolean subtype(String child, String parent) {
        if (child.equals("null")) return true;
        graph(child); graph(parent);
        for (ApiClass candidate : ancestors(child)) if (candidate.name().equals(parent)) return true;
        return false;
    }

    List<ApiClass> ancestors(String name) {
        ArrayDeque<String> queue = new ArrayDeque<>(); Set<String> visited = new HashSet<>(); List<ApiClass> result = new ArrayList<>();
        queue.add(name);
        while (!queue.isEmpty()) {
            OperationControl.checkpoint();
            String current = queue.removeFirst();
            if (!visited.add(current)) continue;
            if (visited.size() > MAX_WALK) fail("LINKAGE_LIMIT", "Ancestor traversal");
            ApiClass value = type(current); result.add(value);
            if (value.superName() != null) queue.add(value.superName());
            queue.addAll(value.interfaces());
        }
        return result;
    }

    boolean assignable(Type target, Type actual) {
        if (target == null || actual == null) return target == actual;
        if (actual.equals(NULL) && reference(target)) return true;
        available(target); available(actual);
        if (target.equals(actual)) return true;
        if (!reference(target) || !reference(actual)) return false;
        if (actual.getSort() == Type.ARRAY) {
            if (target.getSort() == Type.ARRAY) return assignable(component(target), component(actual));
            return Set.of("java/lang/Object", "java/lang/Cloneable", "java/io/Serializable").contains(target.getInternalName());
        }
        if (target.getSort() == Type.ARRAY) return false;
        return subtype(actual.getInternalName(), target.getInternalName());
    }

    void available(Type value) {
        if (value.equals(NULL)) return;
        if (value.getSort() == Type.OBJECT) graph(value.getInternalName());
        else if (value.getSort() == Type.ARRAY) available(value.getElementType());
        else if (value.getSort() == Type.METHOD) {
            for (Type argument : value.getArgumentTypes()) available(argument);
            available(value.getReturnType());
        }
    }

    void accessibleClass(String caller, String name) {
        if (name.startsWith("[")) { accessibleType(caller, Type.getType(name)); return; }
        ApiClass declaration = type(name); graph(name);
        if ((declaration.access() & Opcodes.ACC_PUBLIC) == 0 && !(owned(name) && samePackage(caller, name)))
            fail("INACCESSIBLE_CLASS", caller + " -> " + name);
    }

    void accessibleType(String caller, Type value) {
        if (value.getSort() == Type.OBJECT) accessibleClass(caller, value.getInternalName());
        else if (value.getSort() == Type.ARRAY) accessibleType(caller, value.getElementType());
        else if (value.getSort() == Type.METHOD) {
            for (Type argument : value.getArgumentTypes()) accessibleType(caller, argument);
            accessibleType(caller, value.getReturnType());
        }
    }

    boolean sameRuntimePackage(String first, String second) { return owned(first) && owned(second) && samePackage(first, second); }
    static boolean samePackage(String first, String second) { return packageName(first).equals(packageName(second)); }
    private static String packageName(String name) { int slash = name.lastIndexOf('/'); return slash < 0 ? "" : name.substring(0, slash); }

    Member method(String owner, String name, String descriptor) {
        ApiClass declaration = type(owner); graph(owner);
        if (name.equals("<init>") || name.equals("<clinit>")) {
            ApiMember member = declared(declaration.methods(), name, descriptor);
            if (member == null) fail("MISSING_METHOD", owner + "." + name + descriptor);
            return new Member(declaration, member);
        }
        if (isInterface(owner)) {
            ApiMember direct = declared(declaration.methods(), name, descriptor);
            if (direct != null) return new Member(declaration, direct);
            ApiMember object = declared(type("java/lang/Object").methods(), name, descriptor);
            if (object != null && (object.access() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_PUBLIC)
                return new Member(type("java/lang/Object"), object);
        } else {
            for (ApiClass current = declaration; current != null; current = current.superName() == null ? null : type(current.superName())) {
                ApiMember member = declared(current.methods(), name, descriptor);
                if (member != null) return new Member(current, member);
            }
        }
        List<Member> candidates = new ArrayList<>();
        for (ApiClass candidate : ancestors(owner)) {
            if ((candidate.access() & Opcodes.ACC_INTERFACE) == 0) continue;
            ApiMember member = declared(candidate.methods(), name, descriptor);
            if (member != null && (member.access() & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) candidates.add(new Member(candidate, member));
        }
        if (candidates.isEmpty()) fail("MISSING_METHOD", owner + "." + name + descriptor);
        for (Member candidate : candidates) if ((candidate.member.access() & Opcodes.ACC_ABSTRACT) == 0)
            fail("DEFAULT_INTERFACE_DISPATCH_UNSUPPORTED", owner + "." + name + descriptor);
        candidates.sort(java.util.Comparator.comparing(value -> value.owner.name()));
        return candidates.get(0);
    }

    Member field(String owner, String name, String descriptor) {
        graph(owner);
        Member value = field(owner, name, descriptor, new HashSet<>());
        if (value == null) fail("MISSING_FIELD", owner + "." + name + descriptor);
        return value;
    }
    private Member field(String owner, String name, String descriptor, Set<String> seen) {
        OperationControl.checkpoint();
        if (!seen.add(owner)) return null;
        if (seen.size() > MAX_WALK) fail("LINKAGE_LIMIT", "Field resolution");
        ApiClass declaration = type(owner);
        ApiMember direct = declared(declaration.fields(), name, descriptor);
        if (direct != null) return new Member(declaration, direct);
        Member interfaceMatch = null;
        for (String parent : declaration.interfaces()) {
            Member candidate = field(parent, name, descriptor, seen);
            if (candidate != null) {
                if (interfaceMatch != null && !interfaceMatch.owner.name().equals(candidate.owner.name())) fail("AMBIGUOUS_INTERFACE_FIELD", owner + "." + name);
                interfaceMatch = candidate;
            }
        }
        if (interfaceMatch != null) return interfaceMatch;
        return declaration.superName() == null ? null : field(declaration.superName(), name, descriptor, seen);
    }

    void accessibleMember(String caller, Member resolvedMember, Type receiver, boolean requireReceiver) {
        int access = resolvedMember.member.access(); String owner = resolvedMember.owner.name();
        if ((access & Opcodes.ACC_PUBLIC) != 0) return;
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            if (!caller.equals(owner)) fail("PRIVATE_ACCESS", caller + " -> " + owner + "." + resolvedMember.member.name());
            return;
        }
        if (sameRuntimePackage(caller, owner)) return;
        if ((access & Opcodes.ACC_PROTECTED) == 0 || !subtype(caller, owner)) fail("MEMBER_ACCESS", caller + " -> " + owner + "." + resolvedMember.member.name());
        if (requireReceiver && (access & Opcodes.ACC_STATIC) == 0
                && (receiver == null || !assignable(Type.getObjectType(caller), receiver)))
            fail("PROTECTED_RECEIVER", caller + " -> " + owner + "." + resolvedMember.member.name());
    }

    Map<String, byte[]> declarationBytes(boolean allExternal) {
        Map<String, byte[]> result = new TreeMap<>();
        Map<String, ApiClass> declarations = new TreeMap<>();
        if (allExternal) declarations.putAll(external.classes());
        declarations.putAll(resolved); declarations.putAll(owned);
        if (declarations.size() > MAX_TYPES) fail("LINKAGE_LIMIT", "Declaration-only hierarchy size");
        for (ApiClass declaration : declarations.values()) {
            OperationControl.checkpoint();
            if (platform(declaration.name())) continue;
            ClassWriter writer = new ClassWriter(0);
            writer.visit(Opcodes.V17, declaration.access(), declaration.name(), null, declaration.superName(), declaration.interfaces().toArray(String[]::new));
            writer.visitEnd(); result.put(declaration.name(), writer.toByteArray());
        }
        return result;
    }

    static ApiMember declared(List<ApiMember> members, String name, String descriptor) {
        for (ApiMember member : members) if (member.name().equals(name) && member.descriptor().equals(descriptor)) return member;
        return null;
    }
    static boolean platform(String name) { return name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("jdk/") || name.startsWith("sun/"); }
    static boolean reference(Type value) { return value.getSort() == Type.OBJECT || value.getSort() == Type.ARRAY; }
    private static Type component(Type type) { return Type.getType(type.getDescriptor().substring(1)); }
    static void fail(String code, String detail) { throw new TransformationException(code + ": " + detail); }
    record Member(ApiClass owner, ApiMember member) { }

    private ApiClass platformType(String name) {
        if (name.startsWith("jdk/") || name.startsWith("sun/")) fail("JDK_INTERNAL_API_UNSUPPORTED", name);
        URL resource = ClassLoader.getPlatformClassLoader().getResource(name + ".class");
        if (resource == null || !resource.getProtocol().equals("jrt")) fail("MISSING_PLATFORM_CLASS", name);
        String path = resource.getPath(); int slash = path.indexOf('/', 1);
        if (slash < 0) fail("UNTRUSTED_PLATFORM_RESOURCE", name);
        String moduleName = path.substring(1, slash);
        Module module = ModuleLayer.boot().findModule(moduleName).orElseThrow(() -> new TransformationException("Missing boot module for " + name));
        if (!module.isExported(packageName(name).replace('/', '.'))) fail("UNEXPORTED_PLATFORM_PACKAGE", name);
        try (var stream = resource.openStream()) {
            byte[] bytes = stream.readNBytes(2 * 1024 * 1024 + 1);
            if (bytes.length > 2 * 1024 * 1024) fail("LINKAGE_LIMIT", "Platform class bytes");
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (!node.name.equals(name)) fail("UNTRUSTED_PLATFORM_RESOURCE", name);
            if (node.permittedSubclasses != null && !node.permittedSubclasses.isEmpty()) platformPermits.put(name, List.copyOf(node.permittedSubclasses));
            return new ApiClass(node.name, node.access, node.superName, node.interfaces,
                    node.methods.stream().map(method -> new ApiMember(method.name, method.desc, method.access)).toList(),
                    node.fields.stream().map(field -> new ApiMember(field.name, field.desc, field.access)).toList());
        } catch (IOException error) { throw new TransformationException("Cannot read trusted platform declaration: " + name, error); }
    }
    private static void unique(List<ApiMember> members, String owner, String kind) {
        if (members.size() > 65_536) fail("LINKAGE_LIMIT", "Member declaration count");
        Set<String> signatures = new HashSet<>();
        for (ApiMember member : members) if (!signatures.add(member.name() + "\u0000" + member.descriptor())) fail("DUPLICATE_API_MEMBER", owner + " " + kind);
    }
}
