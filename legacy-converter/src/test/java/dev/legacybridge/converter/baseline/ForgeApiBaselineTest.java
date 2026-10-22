package dev.legacybridge.converter.baseline;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeApiBaselineTest {
    private static final String DEFERRED_REGISTER =
            "net/minecraftforge/registries/DeferredRegister.class";

    @Test
    void pinnedArtifactsProveRegistryApiIsNotBinaryCompatible() throws Exception {
        Path legacyJar = Path.of(requiredProperty("legacybridge.legacyForgeBaseline"));
        Path targetJar = Path.of(requiredProperty("legacybridge.targetForgeBaseline"));

        Set<String> legacyDescriptors = methodDescriptors(legacyJar, DEFERRED_REGISTER, "register");
        Set<String> targetDescriptors = methodDescriptors(targetJar, DEFERRED_REGISTER, "register");

        assertTrue(legacyDescriptors.contains(
                "(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/fml/RegistryObject;"));
        assertTrue(targetDescriptors.contains(
                "(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/registries/RegistryObject;"));
        assertFalse(targetDescriptors.contains(
                "(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/fml/RegistryObject;"));

        assertTrue(hasEntry(legacyJar, "net/minecraftforge/event/RegistryEvent$Register.class"));
        assertFalse(hasEntry(targetJar, "net/minecraftforge/event/RegistryEvent$Register.class"));
        assertTrue(hasEntry(targetJar, "net/minecraftforge/registries/RegisterEvent.class"));
    }

    private static Set<String> methodDescriptors(Path archive, String classEntry, String methodName)
            throws IOException {
        Set<String> descriptors = new HashSet<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(classEntry);
            if (entry == null) {
                throw new IOException("Missing baseline class " + classEntry);
            }
            try (InputStream input = zip.getInputStream(entry)) {
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(
                            int access,
                            String name,
                            String descriptor,
                            String signature,
                            String[] exceptions
                    ) {
                        if (methodName.equals(name)) {
                            descriptors.add(descriptor);
                        }
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        return descriptors;
    }

    private static boolean hasEntry(Path archive, String entry) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return zip.getEntry(entry) != null;
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing test system property " + name);
        }
        return value;
    }
}
