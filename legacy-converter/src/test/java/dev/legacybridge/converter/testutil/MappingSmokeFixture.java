package dev.legacybridge.converter.testutil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** Build-only fixture producer. The resulting mod JAR is deliberately inert. */
public final class MappingSmokeFixture {
    private MappingSmokeFixture() { }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output.getParent());
        SyntheticForgeJarFactory.create(output, Map.of("fixture/SymbolProbe.class", probe()));
    }

    private static byte[] probe() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/SymbolProbe", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "registry", "Lnet/minecraftforge/registries/IForgeRegistryEntry;", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "removedWorldType", "Lnet/minecraftforge/common/world/ForgeWorldType;", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "external", "Lexample/external/Library;", null, null).visitEnd();
        var getter = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "path",
                "(Lnet/minecraft/util/ResourceLocation;)Ljava/lang/String;", null, null);
        getter.visitCode();
        getter.visitVarInsn(Opcodes.ALOAD, 0);
        getter.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/util/ResourceLocation", "func_110623_a", "()Ljava/lang/String;", false);
        getter.visitInsn(Opcodes.ARETURN);
        getter.visitMaxs(1, 1);
        getter.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
