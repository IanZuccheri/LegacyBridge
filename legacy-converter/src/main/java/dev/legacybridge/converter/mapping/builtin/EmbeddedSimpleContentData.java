package dev.legacybridge.converter.mapping.builtin;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import dev.legacybridge.converter.mapping.source.MappingInputs;
import dev.legacybridge.converter.mapping.source.MappingSource;
import dev.legacybridge.converter.operation.OperationControl;
import net.minecraftforge.srgutils.IMappingBuilder;
import net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * An embedded, finite source-contract vocabulary for the existing registration interpreter.
 * It is not a generic mapping table, a complete API inventory, or authority to transform calls.
 * No target methods are inferred: the existing bootstrap recipe and adapter admission still
 * establish the semantics of the entire mod graph. In particular, recognizing a legacy
 * property name does not enable a property rejected by TransformationBindings.
 *
 * <p>The direct canonical-to-SRG alias view deliberately omits obfuscated names and unrelated
 * API declarations. Exact retained class facts are evidence, not a complete hierarchy for
 * linkage verification. Source artifact hashes record development-time checks; none of those
 * artifacts are opened, located in Gradle caches, downloaded, or loaded at player runtime.</p>
 */
public final class EmbeddedSimpleContentData {
    public static final String RESOURCE_PATH = "META-INF/legacybridge/simple-content-v1/source-contract-v1.tsv";
    public static final String DATA_SHA256 = "4868d957c3da0707f1178f809f452e43022aa1205d3a803501f563dc37a91170";
    public static final String PROFILE_ID = "simple-content-v1";
    public static final int SCHEMA_VERSION = 1;
    public static final int CONTRACT_REVISION = 1;
    static final int MAX_BYTES = 32 * 1024;
    private static final String HEADER = "LEGACYBRIDGE_SIMPLE_SOURCE_CONTRACT\t1\tsimple-content-v1\t1.16.5\t36.2.42\t1.20.1\t47.4.23\t17\t1";
    private static final Set<String> SOURCE_IDS = Set.of("legacy.clientMappings", "legacy.serverMappings",
            "legacy.srg", "legacy.apiJars[0]", "legacy.apiJars[1]");

    private EmbeddedSimpleContentData() { }

    /** Loads only the byte-pinned inert resource shipped with the engine; no filesystem fallback. */
    public static MappingInputs load() throws IOException {
        try (InputStream input = EmbeddedSimpleContentData.class.getResourceAsStream("/" + RESOURCE_PATH)) {
            if (input == null) throw new IOException("Embedded simple-content source contract is missing; reinstall the matching LegacyBridge JAR");
            return load(input);
        }
    }

    static MappingInputs load(InputStream input) throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        for (int count; (count = input.read(buffer)) != -1;) {
            OperationControl.checkpoint();
            total += count;
            if (total > MAX_BYTES) throw new IOException("Embedded source contract exceeds byte limit");
            captured.write(buffer, 0, count);
        }
        byte[] bytes = captured.toByteArray();
        if (!DATA_SHA256.equals(hash(bytes))) throw new IOException("Embedded source contract SHA-256 mismatch; no mapping fallback is permitted");
        return parse(bytes);
    }

    /** Package-private parser seam for malformed-data tests; production always verifies the pinned digest first. */
    static MappingInputs parse(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Embedded source contract exceeds byte limit");
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        if (text.indexOf('\r') >= 0 || text.indexOf('\0') >= 0 || !text.endsWith("\n"))
            throw new IOException("Embedded source contract must use terminated LF text");
        String[] lines = text.split("\n", -1);
        if (lines.length > 256 || !HEADER.equals(lines[0]))
            throw new IOException("Unsupported embedded source contract schema, profile, revision or baseline");
        List<MappingSource> sources = new ArrayList<>();
        Set<String> sourceIds = new HashSet<>();
        Map<String, Declaration> declarations = new TreeMap<>();
        Set<String> canonicalOwners = new HashSet<>();
        Declaration current = null;
        int members = 0;
        for (int index = 1; index < lines.length - 1; index++) {
            OperationControl.checkpoint();
            String line = lines[index];
            if (line.isEmpty() || line.length() > 4096) throw new IOException("Empty or oversized embedded contract row");
            String[] parts = line.split("\t", -1);
            switch (parts[0]) {
                case "S" -> {
                    columns(parts, 3);
                    if (current != null || !SOURCE_IDS.contains(parts[1]) || !sourceIds.add(parts[1])
                            || !parts[2].matches("[0-9a-f]{64}")) throw new IOException("Invalid or duplicate source provenance row");
                    sources.add(new MappingSource(parts[1], parts[2], "DEVELOPMENT_VERIFICATION_ONLY_NOT_EMBEDDED"));
                }
                case "C" -> {
                    columns(parts, 6);
                    String runtime = owner(parts[1]), canonical = owner(parts[2]);
                    if (!(runtime.startsWith("net/minecraft/") || runtime.startsWith("net/minecraftforge/")))
                        throw new IOException("Embedded source contract declares a foreign namespace");
                    int access = access(parts[3]);
                    String superclass = owner(parts[4]);
                    List<String> interfaces = parts[5].equals("-") ? List.of() : List.of(parts[5].split(",", -1));
                    if (interfaces.size() > 16 || new HashSet<>(interfaces).size() != interfaces.size())
                        throw new IOException("Invalid interface list in embedded source contract");
                    for (String implemented : interfaces) owner(implemented);
                    current = new Declaration(runtime, canonical, access, superclass, interfaces);
                    if (declarations.size() >= 32 || declarations.putIfAbsent(runtime, current) != null
                            || !canonicalOwners.add(canonical)) throw new IOException("Duplicate or excessive embedded source classes");
                }
                case "M", "F" -> {
                    columns(parts, 5);
                    if (current == null || ++members > 128) throw new IOException("Missing owner or excessive embedded source members");
                    boolean method = parts[0].equals("M");
                    memberName(parts[1], method); memberName(parts[2], method);
                    validateDescriptor(parts[3], method);
                    int access = access(parts[4]);
                    if (!method && (access & Opcodes.ACC_STATIC) == 0)
                        throw new IOException("Instance fields are outside the embedded registration contract");
                    if (parts[1].equals("<init>") && (!parts[2].equals("<init>") || (access & Opcodes.ACC_STATIC) != 0
                            || Type.getReturnType(parts[3]).getSort() != Type.VOID))
                        throw new IOException("Malformed constructor in embedded source contract");
                    if (!parts[1].equals("<init>") && parts[2].equals("<init>"))
                        throw new IOException("Member aliases cannot manufacture constructors");
                    String signature = parts[0] + ":" + parts[1] + ":" + parts[3];
                    String alias = parts[0] + ":" + parts[2] + ":" + parts[3];
                    if (!current.signatures.add(signature) || !current.aliases.add(alias)
                            || current.members.size() >= 32) throw new IOException("Duplicate or ambiguous embedded source member");
                    current.members.add(new Member(method, parts[1], parts[2], parts[3], access));
                }
                default -> throw new IOException("Unknown embedded source contract row type");
            }
        }
        if (!sourceIds.equals(SOURCE_IDS) || declarations.isEmpty() || members == 0)
            throw new IOException("Incomplete embedded source contract");
        Map<String, String> toCanonical = new HashMap<>();
        declarations.values().forEach(value -> toCanonical.put(value.runtime, value.canonical));
        // Both canonical and runtime aliases must resolve unambiguously before constructing a view.
        Map<String, String> aliases = new HashMap<>();
        for (Declaration declaration : declarations.values()) {
            for (String alias : List.of(declaration.runtime, declaration.canonical)) {
                String old = aliases.putIfAbsent(alias, declaration.runtime);
                if (old != null && !old.equals(declaration.runtime)) throw new IOException("Conflicting embedded class alias");
            }
        }
        IMappingBuilder builder = IMappingBuilder.create("canonical", "legacy_runtime");
        Map<String, ApiIndex.ApiClass> api = new TreeMap<>();
        for (Declaration declaration : declarations.values()) {
            OperationControl.checkpoint();
            var type = builder.addClass(declaration.canonical, declaration.runtime);
            List<ApiIndex.ApiMember> methods = new ArrayList<>(), fields = new ArrayList<>();
            for (Member member : declaration.members) {
                String descriptor = canonicalDescriptor(Type.getType(member.descriptor), toCanonical);
                if (member.method) type.method(descriptor, member.name, member.alias);
                else type.field(member.name, member.alias).descriptor(descriptor);
                (member.method ? methods : fields).add(new ApiIndex.ApiMember(member.name, member.descriptor, member.access));
            }
            api.put(declaration.runtime, new ApiIndex.ApiClass(declaration.runtime, declaration.access,
                    declaration.superclass, declaration.interfaces, methods, fields));
        }
        sources.add(new MappingSource("embedded.simple-content-v1.source-contract-v1", hash(bytes), "EMBEDDED_FINITE_SOURCE_CONTRACT"));
        IMappingFile empty = IMappingBuilder.create("source", "target").build().getMap("source", "target");
        return new MappingInputs(builder.build().getMap("canonical", "legacy_runtime"), empty, empty, empty,
                new ApiIndex(api), ApiIndex.empty(), sources, List.of(
                "SIMPLE_CONTENT_CONTRACT: finite canonical-to-SRG source aliases and selected declaration facts only; no obfuscated mapping or complete API hierarchy",
                "Admission and semantics remain the existing simple-content-v1 interpreter and implemented adapter contracts; recognized names do not enable unsupported properties",
                "Other conversion profiles and general mapping analysis require separately selected full development data"),
                false, MappingInputs.Scope.SIMPLE_CONTENT_CONTRACT);
    }

    private static String canonicalDescriptor(Type type, Map<String, String> owners) {
        return switch (type.getSort()) {
            case Type.OBJECT -> "L" + owners.getOrDefault(type.getInternalName(), type.getInternalName()) + ";";
            case Type.METHOD -> {
                StringBuilder result = new StringBuilder("(");
                for (Type argument : type.getArgumentTypes()) result.append(canonicalDescriptor(argument, owners));
                yield result.append(')').append(canonicalDescriptor(type.getReturnType(), owners)).toString();
            }
            default -> type.getDescriptor();
        };
    }

    private static void validateDescriptor(String descriptor, boolean method) throws IOException {
        try {
            if (descriptor.length() > 2048 || descriptor.startsWith("(") != method)
                throw new IllegalArgumentException("Descriptor kind");
            Type type = Type.getType(descriptor);
            if (method) {
                StringBuilder canonical = new StringBuilder("(");
                for (Type argument : type.getArgumentTypes()) {
                    valueType(argument, false); canonical.append(argument.getDescriptor());
                }
                valueType(type.getReturnType(), true);
                if (!canonical.append(')').append(type.getReturnType().getDescriptor()).toString().equals(descriptor))
                    throw new IllegalArgumentException("Descriptor trailing text");
            } else { valueType(type, false); }
        } catch (IllegalArgumentException | IndexOutOfBoundsException malformed) {
            throw new IOException("Malformed or unsupported embedded member descriptor", malformed);
        }
    }

    private static void valueType(Type type, boolean voidAllowed) throws IOException {
        if (type.getSort() == Type.OBJECT) owner(type.getInternalName());
        else if (!Set.of(Type.INT, Type.FLOAT, Type.BOOLEAN).contains(type.getSort())
                && !(voidAllowed && type.getSort() == Type.VOID))
            throw new IOException("Type outside the finite registration vocabulary");
    }

    private static String owner(String value) throws IOException {
        if (value.length() > 512 || !value.matches("[A-Za-z_$][A-Za-z0-9_$]*(/[A-Za-z_$][A-Za-z0-9_$]*)+"))
            throw new IOException("Invalid internal class name in embedded source contract");
        return value;
    }

    private static void memberName(String value, boolean method) throws IOException {
        if (!value.matches("[A-Za-z_$][A-Za-z0-9_$]{0,127}") && !(method && value.equals("<init>")))
            throw new IOException("Invalid embedded source member name");
    }

    private static int access(String value) throws IOException {
        try {
            if (!value.matches("[0-9]{1,5}")) throw new NumberFormatException();
            int access = Integer.parseInt(value);
            if (access > 65535 || (access & Opcodes.ACC_PUBLIC) == 0
                    || (access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) != 0) throw new NumberFormatException();
            return access;
        } catch (NumberFormatException invalid) { throw new IOException("Invalid or non-public source access flags", invalid); }
    }

    private static void columns(String[] columns, int count) throws IOException {
        if (columns.length != count) throw new IOException("Invalid embedded source contract column count");
    }

    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private record Member(boolean method, String name, String alias, String descriptor, int access) { }

    private static final class Declaration {
        final String runtime, canonical, superclass;
        final int access;
        final List<String> interfaces;
        final List<Member> members = new ArrayList<>();
        final Set<String> signatures = new HashSet<>(), aliases = new HashSet<>();
        Declaration(String runtime, String canonical, int access, String superclass, List<String> interfaces) {
            this.runtime = runtime; this.canonical = canonical; this.access = access;
            this.superclass = superclass; this.interfaces = List.copyOf(interfaces);
        }
    }
}
