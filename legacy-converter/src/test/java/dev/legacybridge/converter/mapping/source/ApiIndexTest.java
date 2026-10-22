package dev.legacybridge.converter.mapping.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ApiIndexTest {
    @TempDir Path temporary;

    @Test
    void readsDeclarationsAndExactInputHashWithoutRunningInitializer() throws Exception {
        String property = "legacybridge.api.fixture.executed";
        System.clearProperty(property);
        Path jar = jar("api.jar", "fixture/Api.class", api("fixture/Api", "run", property));
        ApiIndex.ReadResult result = ApiIndex.readWithSources(List.of(jar), "legacy.apiJars", "LOCAL_PROFILE");

        assertNull(System.getProperty(property));
        ApiIndex.ApiClass api = result.index().getClass("fixture/Api");
        assertEquals("java/lang/Object", api.superName());
        assertEquals(List.of("java/lang/Runnable"), api.interfaces());
        assertTrue(api.methods().contains(new ApiIndex.ApiMember("<init>", "()V", Opcodes.ACC_PUBLIC)));
        assertTrue(api.methods().contains(new ApiIndex.ApiMember("run", "()V", Opcodes.ACC_PUBLIC)));
        assertEquals(List.of(new ApiIndex.ApiMember("value", "Ljava/lang/String;", Opcodes.ACC_PRIVATE)), api.fields());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))),
                result.sources().get(0).sha256());
        assertThrows(UnsupportedOperationException.class, () -> result.index().classes().clear());
        assertThrows(UnsupportedOperationException.class, () -> api.methods().clear());
    }

    @Test
    void acceptsEquivalentDeclarationsButRejectsConflictsAcrossApiJars() throws Exception {
        Path first = jar("first.jar", "fixture/Api.class", api("fixture/Api", "run", "a"));
        Path sameDeclarations = jar("same.jar", "fixture/Api.class", api("fixture/Api", "run", "b"));
        Path changed = jar("changed.jar", "fixture/Api.class", api("fixture/Api", "changed", "c"));

        assertEquals(1, ApiIndex.read(List.of(first, sameDeclarations)).classes().size());
        IOException error = assertThrows(IOException.class, () -> ApiIndex.read(List.of(first, changed)));
        assertTrue(error.getMessage().contains("Conflicting API declarations"));
    }

    @Test
    void rejectsMismatchedEntryNamesAndMultiReleaseInputs() throws Exception {
        Path wrongName = jar("wrong.jar", "fixture/Wrong.class", api("fixture/Api", "run", "a"));
        assertTrue(assertThrows(IOException.class, () -> ApiIndex.read(List.of(wrongName)))
                .getMessage().contains("does not match"));
        Path multiRelease = jar("multi.jar", "META-INF/versions/17/fixture/Api.class", api("fixture/Api", "run", "a"));
        assertTrue(assertThrows(IOException.class, () -> ApiIndex.read(List.of(multiRelease)))
                .getMessage().contains("multi-release"));
    }

    @Test
    void rejectsMalformedArchiveAndOversizedInflatedClass() throws Exception {
        Path malformed = temporary.resolve("malformed.jar");
        Files.writeString(malformed, "This is not a JAR");
        assertThrows(IOException.class, () -> ApiIndex.read(List.of(malformed)));
        Path oversized = jar("large.jar", "fixture/Api.class", new byte[(int) BoundedInputs.CLASS_BYTES + 1]);
        assertTrue(assertThrows(IOException.class, () -> ApiIndex.read(List.of(oversized)))
                .getMessage().contains("exceeds byte limit"));
    }

    @Test
    void validatesImmutableIndexKeys() {
        assertThrows(IllegalArgumentException.class, () -> new ApiIndex(Map.of("wrong",
                new ApiIndex.ApiClass("fixture/Api", 0, null, List.of(), List.of(), List.of()))));
    }

    @Test
    void rejectsDuplicateMethodDeclarationsInsteadOfChoosingOne() throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "fixture/Duplicate", null,
                "java/lang/Object", null);
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "same", "()V", null, null).visitEnd();
        writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_NATIVE, "same", "()V", null, null).visitEnd();
        writer.visitEnd();
        Path duplicate = jar("duplicate.jar", "fixture/Duplicate.class", writer.toByteArray());
        assertTrue(assertThrows(IOException.class, () -> ApiIndex.read(List.of(duplicate)))
                .getMessage().contains("duplicate declarations"));
    }

    Path jar(String filename, String entry, byte[] bytes) throws IOException {
        Path path = temporary.resolve(filename);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry(entry));
            output.write(bytes);
            output.closeEntry();
        }
        return path;
    }

    static byte[] api(String name, String methodName, String initializerProperty) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", new String[]{"java/lang/Runnable"});
        writer.visitField(Opcodes.ACC_PRIVATE, "value", "Ljava/lang/String;", null, null).visitEnd();
        MethodVisitor initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitLdcInsn(initializerProperty);
        initializer.visitLdcInsn("executed");
        initializer.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
        initializer.visitInsn(Opcodes.POP);
        initializer.visitInsn(Opcodes.RETURN);
        initializer.visitMaxs(2, 0);
        initializer.visitEnd();
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
