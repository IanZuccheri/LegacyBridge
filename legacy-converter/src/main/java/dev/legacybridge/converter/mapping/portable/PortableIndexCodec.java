package dev.legacybridge.converter.mapping.portable;

import dev.legacybridge.converter.mapping.source.ApiIndex;
import net.minecraftforge.srgutils.IMappingBuilder;
import net.minecraftforge.srgutils.IMappingFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Versioned length-prefixed metadata, deliberately not Java ObjectInputStream or classfiles. */
final class PortableIndexCodec {
    static final int MAPPING_MAGIC = 0x4c424d50; // LBMP
    static final int API_MAGIC = 0x4c424150; // LBAP
    static final int MAX_CLASSES = 100_000;
    static final int MAX_MEMBERS = 500_000;
    static final int MAX_RECORDS = 2_000_000;
    static final int MAX_STRING_BYTES = 4096;

    private PortableIndexCodec() { }

    static void writeMappings(Path file, IMappingFile mapping) throws IOException {
        try (DataOutputStream out = output(file)) {
            out.writeInt(MAPPING_MAGIC);
            out.writeInt(PortableMappingBundle.SCHEMA_VERSION);
            List<? extends IMappingFile.IPackage> packages = mapping.getPackages().stream()
                    .sorted(Comparator.comparing(IMappingFile.INode::getOriginal)).toList();
            writeCount(out, packages.size(), MAX_CLASSES);
            for (IMappingFile.IPackage item : packages) {
                name(item.getOriginal(), true);
                name(item.getMapped(), true);
                string(out, item.getOriginal());
                string(out, item.getMapped());
            }
            List<? extends IMappingFile.IClass> classes = mapping.getClasses().stream()
                    .sorted(Comparator.comparing(IMappingFile.INode::getOriginal)).toList();
            writeCount(out, classes.size(), MAX_CLASSES);
            Set<String> mapped = new HashSet<>();
            long members = 0;
            for (IMappingFile.IClass type : classes) {
                PortableMappingBundle.checkpoint();
                name(type.getOriginal(), false);
                name(type.getMapped(), false);
                if (!mapped.add(type.getMapped())) throw new IOException("Mapping classes are not injective");
                string(out, type.getOriginal());
                string(out, type.getMapped());
                List<? extends IMappingFile.IField> fields = type.getFields().stream()
                        .sorted(Comparator.comparing(IMappingFile.INode::getOriginal)).toList();
                members += fields.size();
                writeCount(out, fields.size(), MAX_MEMBERS);
                for (IMappingFile.IField field : fields) {
                    memberName(field.getOriginal(), false);
                    memberName(field.getMapped(), false);
                    if (field.getDescriptor() != null) descriptor(field.getDescriptor(), false);
                    string(out, field.getOriginal());
                    string(out, field.getMapped());
                    nullable(out, field.getDescriptor());
                }
                List<? extends IMappingFile.IMethod> methods = type.getMethods().stream()
                        .sorted(Comparator.comparing(IMappingFile.IMethod::getOriginal)
                                .thenComparing(IMappingFile.IMethod::getDescriptor)).toList();
                members += methods.size();
                if (members > MAX_MEMBERS) throw new IOException("Mapping member limit exceeded");
                writeCount(out, methods.size(), MAX_MEMBERS);
                for (IMappingFile.IMethod method : methods) {
                    memberName(method.getOriginal(), true);
                    memberName(method.getMapped(), true);
                    descriptor(method.getDescriptor(), true);
                    string(out, method.getOriginal());
                    string(out, method.getMapped());
                    string(out, method.getDescriptor());
                }
            }
        }
    }

    static IMappingFile readMappings(Path file, String hash, Budget budget) throws IOException {
        try (VerifiedInput source = new VerifiedInput(file, hash)) {
            DataInputStream in = source.input;
            header(in, MAPPING_MAGIC);
            IMappingBuilder builder = IMappingBuilder.create("source", "target");
            int packages = count(in, MAX_CLASSES, budget);
            Set<String> originalPackages = new HashSet<>();
            for (int index = 0; index < packages; index++) {
                String original = string(in);
                String mapped = string(in);
                name(original, true);
                name(mapped, true);
                unique(originalPackages, original, "package");
                builder.addPackage(original, mapped);
            }
            int classes = count(in, MAX_CLASSES, budget);
            if (classes == 0) throw new IOException("Empty mapping index");
            Set<String> originals = new HashSet<>();
            Set<String> mappedNames = new HashSet<>();
            long members = 0;
            for (int index = 0; index < classes; index++) {
                PortableMappingBundle.checkpoint();
                String original = string(in);
                String mapped = string(in);
                name(original, false);
                name(mapped, false);
                unique(originals, original, "class");
                unique(mappedNames, mapped, "mapped class");
                IMappingBuilder.IClass type = builder.addClass(original, mapped);
                int fields = count(in, MAX_MEMBERS, budget);
                members += fields;
                if (members > MAX_MEMBERS) throw new IOException("Mapping member limit exceeded");
                Set<String> fieldNames = new HashSet<>();
                for (int fieldIndex = 0; fieldIndex < fields; fieldIndex++) {
                    String fieldOriginal = string(in);
                    String fieldMapped = string(in);
                    String descriptor = nullable(in);
                    memberName(fieldOriginal, false);
                    memberName(fieldMapped, false);
                    unique(fieldNames, fieldOriginal, "field");
                    IMappingBuilder.IField field = type.field(fieldOriginal, fieldMapped);
                    if (descriptor != null) {
                        descriptor(descriptor, false);
                        field.descriptor(descriptor);
                    }
                }
                int methods = count(in, MAX_MEMBERS, budget);
                members += methods;
                if (members > MAX_MEMBERS) throw new IOException("Mapping member limit exceeded");
                Set<String> methodNames = new HashSet<>();
                for (int methodIndex = 0; methodIndex < methods; methodIndex++) {
                    String methodOriginal = string(in);
                    String methodMapped = string(in);
                    String descriptor = string(in);
                    memberName(methodOriginal, true);
                    memberName(methodMapped, true);
                    descriptor(descriptor, true);
                    unique(methodNames, methodOriginal + descriptor, "method");
                    type.method(descriptor, methodOriginal, methodMapped);
                }
            }
            source.verifyEnd();
            return builder.build().getMap("source", "target");
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Invalid mapping index declarations", malformed);
        }
    }

    static void writeApi(Path file, ApiIndex index) throws IOException {
        try (DataOutputStream out = output(file)) {
            out.writeInt(API_MAGIC);
            out.writeInt(PortableMappingBundle.SCHEMA_VERSION);
            writeCount(out, index.classes().size(), MAX_CLASSES);
            long members = 0;
            for (ApiIndex.ApiClass type : index.classes().values()) {
                PortableMappingBundle.checkpoint();
                name(type.name(), false);
                string(out, type.name());
                access(type.access());
                out.writeInt(type.access());
                if (type.superName() != null) name(type.superName(), false);
                nullable(out, type.superName());
                writeCount(out, type.interfaces().size(), 1024);
                Set<String> interfaces = new HashSet<>();
                for (String implemented : type.interfaces()) {
                    name(implemented, false);
                    unique(interfaces, implemented, "interface");
                    string(out, implemented);
                }
                members += type.fields().size() + type.methods().size();
                if (members > MAX_MEMBERS) throw new IOException("API member limit exceeded");
                writeMembers(out, type.fields(), false);
                writeMembers(out, type.methods(), true);
            }
        }
    }

    static ApiIndex readApi(Path file, String hash, Budget budget) throws IOException {
        try (VerifiedInput source = new VerifiedInput(file, hash)) {
            DataInputStream in = source.input;
            header(in, API_MAGIC);
            int count = count(in, MAX_CLASSES, budget);
            if (count == 0) throw new IOException("Empty API index");
            Map<String, ApiIndex.ApiClass> classes = new TreeMap<>();
            long totalMembers = 0;
            for (int index = 0; index < count; index++) {
                PortableMappingBundle.checkpoint();
                String name = string(in);
                name(name, false);
                if (classes.containsKey(name)) throw new IOException("Duplicate API class");
                int access = in.readInt();
                access(access);
                String superName = nullable(in);
                if (superName != null) name(superName, false);
                int interfaceCount = count(in, 1024, budget);
                List<String> interfaces = new ArrayList<>();
                Set<String> interfaceNames = new HashSet<>();
                for (int interfaceIndex = 0; interfaceIndex < interfaceCount; interfaceIndex++) {
                    String implemented = string(in);
                    name(implemented, false);
                    unique(interfaceNames, implemented, "interface");
                    interfaces.add(implemented);
                }
                List<ApiIndex.ApiMember> fields = readMembers(in, false, budget);
                List<ApiIndex.ApiMember> methods = readMembers(in, true, budget);
                totalMembers += fields.size() + methods.size();
                if (totalMembers > MAX_MEMBERS) throw new IOException("API member limit exceeded");
                classes.put(name, new ApiIndex.ApiClass(name, access, superName, interfaces, methods, fields));
            }
            source.verifyEnd();
            return new ApiIndex(classes);
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Invalid API index declarations", malformed);
        }
    }

    private static void writeMembers(DataOutputStream out, List<ApiIndex.ApiMember> members, boolean method) throws IOException {
        writeCount(out, members.size(), MAX_MEMBERS);
        Set<String> names = new HashSet<>();
        for (ApiIndex.ApiMember member : members) {
            memberName(member.name(), method);
            descriptor(member.descriptor(), method);
            unique(names, member.name() + "\u0000" + member.descriptor(), "API member");
            string(out, member.name());
            string(out, member.descriptor());
            access(member.access());
            out.writeInt(member.access());
        }
    }

    private static List<ApiIndex.ApiMember> readMembers(DataInputStream in, boolean method, Budget budget) throws IOException {
        int count = count(in, MAX_MEMBERS, budget);
        List<ApiIndex.ApiMember> members = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < count; index++) {
            String name = string(in);
            String descriptor = string(in);
            int access = in.readInt();
            memberName(name, method);
            descriptor(descriptor, method);
            access(access);
            unique(names, name + "\u0000" + descriptor, "API member");
            members.add(new ApiIndex.ApiMember(name, descriptor, access));
        }
        return members;
    }

    private static void header(DataInputStream in, int magic) throws IOException {
        if (in.readInt() != magic || in.readInt() != PortableMappingBundle.SCHEMA_VERSION) {
            throw new IOException("Unsupported mapping/API index magic or schema");
        }
    }

    private static void string(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IOException("Mapping/API string byte limit exceeded");
        if (!PortableMappingBundle.decode(bytes).equals(value)) throw new IOException("Invalid mapping/API Unicode string");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String string(DataInputStream in) throws IOException {
        return readString(in, in.readInt());
    }

    private static String readString(DataInputStream in, int length) throws IOException {
        if (length < 0 || length > MAX_STRING_BYTES) throw new IOException("Invalid mapping/API string byte length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return PortableMappingBundle.decode(bytes);
    }

    private static void nullable(DataOutputStream out, String value) throws IOException {
        if (value == null) out.writeInt(-1);
        else string(out, value);
    }

    private static String nullable(DataInputStream in) throws IOException {
        int length = in.readInt();
        return length == -1 ? null : readString(in, length);
    }

    private static int count(DataInputStream in, int limit, Budget budget) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > limit) throw new IOException("Invalid or oversized mapping/API record count");
        budget.consume(count);
        return count;
    }

    private static void writeCount(DataOutputStream out, int count, int limit) throws IOException {
        if (count < 0 || count > limit) throw new IOException("Mapping/API record count limit exceeded");
        out.writeInt(count);
    }

    private static void unique(Set<String> names, String name, String label) throws IOException {
        if (!names.add(name)) throw new IOException("Duplicate mapping/API " + label);
    }

    private static void access(int access) throws IOException {
        // ASM also exposes ACC_RECORD / ACC_DEPRECATED beyond the JVM u2 flags.
        if (access < 0 || (access & ~0x3ffff) != 0) throw new IOException("Invalid API access flags");
    }

    private static void name(String name, boolean allowEmpty) throws IOException {
        if (allowEmpty && name.isEmpty()) return;
        if (name.isEmpty() || name.startsWith("/") || name.endsWith("/") || name.contains("//")
                || name.chars().anyMatch(character -> character <= 0x20 || ".;[\\".indexOf(character) >= 0)) {
            throw new IOException("Invalid mapping/API internal name");
        }
    }

    private static void memberName(String name, boolean method) throws IOException {
        if (method && (name.equals("<init>") || name.equals("<clinit>"))) return;
        if (name.isEmpty() || name.chars().anyMatch(character -> character <= 0x20 || ".;[/\\<>".indexOf(character) >= 0)) {
            throw new IOException("Invalid mapping/API member name");
        }
    }

    private static void descriptor(String descriptor, boolean method) throws IOException {
        if (descriptor.isEmpty()) throw new IOException("Empty mapping/API descriptor");
        int position = 0;
        if (method) {
            if (descriptor.charAt(position++) != '(') throw new IOException("Invalid method descriptor");
            int arguments = 0;
            while (position < descriptor.length() && descriptor.charAt(position) != ')') {
                position = type(descriptor, position, false);
                if (++arguments > 255) throw new IOException("Method argument count exceeds JVM limit");
            }
            if (position >= descriptor.length() || descriptor.charAt(position++) != ')') throw new IOException("Invalid method descriptor");
            position = type(descriptor, position, true);
        } else position = type(descriptor, position, false);
        if (position != descriptor.length()) throw new IOException("Trailing content in mapping/API descriptor");
    }

    private static int type(String descriptor, int position, boolean allowVoid) throws IOException {
        int arrays = 0;
        while (position < descriptor.length() && descriptor.charAt(position) == '[') {
            position++;
            if (++arrays > 255) throw new IOException("Descriptor array rank exceeds JVM limit");
        }
        if (position >= descriptor.length()) throw new IOException("Truncated mapping/API descriptor");
        char type = descriptor.charAt(position++);
        if ("ZCBSIFJD".indexOf(type) >= 0) return position;
        if (type == 'V' && allowVoid && arrays == 0) return position;
        if (type == 'L') {
            int end = descriptor.indexOf(';', position);
            if (end < 0) throw new IOException("Truncated object descriptor");
            name(descriptor.substring(position, end), false);
            return end + 1;
        }
        throw new IOException("Invalid mapping/API descriptor type");
    }

    private static DataOutputStream output(Path path) throws IOException {
        OutputStream raw = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return new DataOutputStream(new BufferedOutputStream(new FilterOutputStream(raw) {
            private long count;
            @Override public void write(int value) throws IOException {
                account(1);
                out.write(value);
            }
            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                account(length);
                out.write(bytes, offset, length);
            }
            private void account(int length) throws IOException {
                PortableMappingBundle.checkpoint();
                count += length;
                if (count > PortableMappingBundle.MAX_FILE_BYTES) throw new IOException("Mapping/API index byte limit exceeded");
            }
        }));
    }

    static final class Budget {
        private long records;
        void consume(int count) throws IOException {
            records += count;
            if (records > MAX_RECORDS) throw new IOException("Mapping bundle total record limit exceeded");
        }
    }

    private static final class VerifiedInput implements AutoCloseable {
        private final MessageDigest digest = PortableMappingBundle.sha256();
        private final String expected;
        private final DataInputStream input;

        private VerifiedInput(Path path, String expected) throws IOException {
            PortableMappingBundle.regularFile(path, PortableMappingBundle.MAX_FILE_BYTES);
            this.expected = expected;
            InputStream raw = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
            FilterInputStream bounded = new FilterInputStream(raw) {
                private long count;
                @Override public int read() throws IOException {
                    int value = in.read();
                    if (value >= 0) account(1);
                    return value;
                }
                @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                    int read = in.read(bytes, offset, length);
                    if (read > 0) account(read);
                    return read;
                }
                private void account(int length) throws IOException {
                    PortableMappingBundle.checkpoint();
                    count += length;
                    if (count > PortableMappingBundle.MAX_FILE_BYTES) throw new IOException("Mapping/API index byte limit exceeded");
                }
            };
            input = new DataInputStream(new BufferedInputStream(new DigestInputStream(bounded, digest)));
        }

        private void verifyEnd() throws IOException {
            if (input.read() != -1) throw new IOException("Trailing bytes in mapping/API index");
            if (!HexFormat.of().formatHex(digest.digest()).equals(expected)) {
                throw new IOException("Mapping/API index changed while parsing");
            }
        }

        @Override public void close() throws IOException { input.close(); }
    }
}
