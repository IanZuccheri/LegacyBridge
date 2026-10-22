package dev.legacybridge.product.validation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import static dev.legacybridge.product.validation.ProductProbeSupport.require;

/** Tiny original QA fixtures generated as inert bytes, never defined or invoked by a classloader. */
final class P4FixturePreflight {
    static final String EXECUTED_PROPERTY = "legacybridge.p4.original.executed";
    static final String UNSUPPORTED = "p4-unsupported.jar";
    static final String CORRUPT = "p4-corrupt.jar";
    static final String MULTIPLE = "p4-multiple.jar";
    static final String AMBIGUOUS = "p4-ambiguous.jar";
    static final String TARGET = "p4-target.jar";
    static final String DUPLICATE_A = "p4-duplicate-a.jar";
    static final String DUPLICATE_B = "p4-duplicate-b.jar";

    record Prepared(Path original, Map<Path, String> originalHashes) {
        Prepared { originalHashes = Map.copyOf(originalHashes); }

        void verifyUnchanged() throws Exception {
            require(System.getProperty(EXECUTED_PROPERTY) == null, "An original fixture initializer was executed");
            for (var entry : originalHashes.entrySet()) {
                require(ProductProbeSupport.sha256(entry.getKey()).equals(entry.getValue()),
                        "Input fixture changed: " + entry.getKey().getFileName());
            }
        }
    }

    private P4FixturePreflight() { }

    static Prepared prepare() throws Exception {
        require(Boolean.getBoolean("legacybridge.product.p4"), "P4 fixture generation requires its explicit flag");
        require(System.getProperty(EXECUTED_PROPERTY) == null, "Original execution marker was already set");
        Path original = ProductProbeSupport.input();
        Path input = original.getParent();
        require(input.equals(input.toRealPath()) && original.equals(original.toRealPath()), "Aliased P4 input directory/file");
        require(ProductProbeSupport.sha256(original).equals(ProductProbeSupport.SOURCE_SHA), "Unexpected positive original fixture");
        Map<String, byte[]> fixtures = new LinkedHashMap<>();
        fixtures.put(CORRUPT, "This is an intentionally corrupt original P4 test JAR.\n".getBytes(StandardCharsets.UTF_8));
        fixtures.put(UNSUPPORTED, jar(metadata(36, "1.16.5", "p4_unsupported"),
                Map.of("p4/unsupported/Original.class", type("p4/unsupported/Original", "p4_unsupported", false, true))));
        fixtures.put(MULTIPLE, jar(metadata(36, "1.16.5", "p4_first", "p4_second"), Map.of(
                "p4/multiple/First.class", type("p4/multiple/First", "p4_first", false, false),
                "p4/multiple/Second.class", type("p4/multiple/Second", "p4_second", false, false))));
        fixtures.put(AMBIGUOUS, jar(metadata(36, null, "p4_ambiguous"),
                Map.of("p4/ambiguous/Original.class", type("p4/ambiguous/Original", "p4_ambiguous", false, false))));
        fixtures.put(TARGET, jar(metadata(47, "1.20.1", "p4_target"),
                Map.of("p4/target/Original.class", type("p4/target/Original", "p4_target", true, false))));
        byte[] duplicate = jar(metadata(36, "1.16.5", "p4_duplicate"),
                Map.of("p4/duplicate/Original.class", type("p4/duplicate/Original", "p4_duplicate", false, false)));
        fixtures.put(DUPLICATE_A, duplicate);
        fixtures.put(DUPLICATE_B, duplicate);
        Map<Path, String> hashes = new LinkedHashMap<>();
        hashes.put(original, ProductProbeSupport.SOURCE_SHA);
        for (var entry : fixtures.entrySet()) {
            Path destination = input.resolve(entry.getKey());
            require(Files.isDirectory(input, LinkOption.NOFOLLOW_LINKS) && input.equals(input.toRealPath()), "Input directory changed during preflight");
            try { Files.write(destination, entry.getValue(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
            catch (FileAlreadyExistsException exists) {
                require(Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
                                && destination.equals(destination.toRealPath())
                                && Files.size(destination) == entry.getValue().length
                                && java.util.Arrays.equals(Files.readAllBytes(destination), entry.getValue()),
                        "Preflight refuses to overwrite an existing nonmatching fixture: " + entry.getKey());
            }
            hashes.put(destination, ProductProbeSupport.sha256(destination));
        }
        return new Prepared(original, hashes);
    }

    private static String metadata(int forgeMajor, String minecraft, String... ids) {
        StringBuilder text = new StringBuilder("modLoader=\"javafml\"\nloaderVersion=\"[" + forgeMajor + ",)\"\nlicense=\"All Rights Reserved\"\n");
        for (String id : ids) {
            text.append("[[mods]]\nmodId=\"").append(id).append("\"\nversion=\"1.0.0\"\ndisplayName=\"P4 QA ")
                    .append(id).append("\"\n[[dependencies.").append(id)
                    .append("]]\nmodId=\"forge\"\nmandatory=true\nversionRange=\"[").append(forgeMajor)
                    .append(",)\"\nordering=\"NONE\"\nside=\"BOTH\"\n");
            if (minecraft != null) text.append("[[dependencies.").append(id)
                    .append("]]\nmodId=\"minecraft\"\nmandatory=true\nversionRange=\"[").append(minecraft)
                    .append("]\"\nordering=\"NONE\"\nside=\"BOTH\"\n");
        }
        return text.toString();
    }

    private static byte[] jar(String toml, Map<String, byte[]> classes) throws Exception {
        Map<String, byte[]> entries = new TreeMap<>(classes);
        entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/mods.toml", toml.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var output = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                ZipEntry zip = new ZipEntry(entry.getKey());
                zip.setTime(0);
                output.putNextEntry(zip);
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] type(String name, String id, boolean target, boolean unsupported) {
        var writer = new ClassWriter(0);
        writer.visit(target ? Opcodes.V17 : Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                name, null, "java/lang/Object", null);
        var mod = writer.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true);
        mod.visit("value", id);
        mod.visitEnd();
        if (unsupported) writer.visitField(Opcodes.ACC_PUBLIC, "unsupportedLegacyNetwork",
                "Lnet/minecraftforge/fml/network/simple/SimpleChannel;", null, null).visitEnd();
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        var initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitLdcInsn(EXECUTED_PROPERTY);
        initializer.visitLdcInsn(id);
        initializer.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        initializer.visitInsn(Opcodes.POP);
        initializer.visitInsn(Opcodes.RETURN);
        initializer.visitMaxs(2, 0);
        initializer.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
