package dev.legacybridge.product.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import static org.objectweb.asm.Opcodes.*;

/** Inert Java-8 ASM fixture, using the already audited simple DeferredRegister recipe. */
final class ReceiptSourceFixture {
    private static final String ITEM = "net/minecraft/item/Item", PROPS = ITEM + "$Properties";
    private static final String DR = "net/minecraftforge/registries/DeferredRegister";
    private static final String REG = "net/minecraftforge/registries/IForgeRegistry";
    private static final String RO = "net/minecraftforge/fml/RegistryObject";
    private static final String CTX = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";

    static Path write(Path path, String id) throws Exception {
        String owner = "fixture/" + id + "/Mod";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, owner, null, "java/lang/Object", null);
        var annotation = writer.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true);
        annotation.visit("value", id); annotation.visitEnd();
        writer.visitField(ACC_STATIC | ACC_FINAL, "ITEMS", "L" + DR + ";", null, null).visitEnd();
        writer.visitField(ACC_STATIC | ACC_FINAL, "TOKEN", "L" + RO + ";", null, null).visitEnd();
        var init = method(writer, ACC_STATIC, "<clinit>", "()V");
        init.visitFieldInsn(GETSTATIC, "net/minecraftforge/registries/ForgeRegistries", "ITEMS", "L" + REG + ";");
        init.visitLdcInsn(id);
        init.visitMethodInsn(INVOKESTATIC, DR, "create", "(L" + REG + ";Ljava/lang/String;)L" + DR + ";", false);
        init.visitFieldInsn(PUTSTATIC, owner, "ITEMS", "L" + DR + ";");
        init.visitFieldInsn(GETSTATIC, owner, "ITEMS", "L" + DR + ";"); init.visitLdcInsn("token");
        init.visitInvokeDynamicInsn("get", "()Ljava/util/function/Supplier;",
                new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                Type.getMethodType("()Ljava/lang/Object;"), new Handle(H_INVOKESTATIC, owner, "item", "()L" + ITEM + ";", false),
                Type.getMethodType("()L" + ITEM + ";"));
        init.visitMethodInsn(INVOKEVIRTUAL, DR, "register", "(Ljava/lang/String;Ljava/util/function/Supplier;)L" + RO + ";", false);
        init.visitFieldInsn(PUTSTATIC, owner, "TOKEN", "L" + RO + ";"); end(init, RETURN);
        var constructor = method(writer, ACC_PUBLIC, "<init>", "()V");
        constructor.visitVarInsn(ALOAD, 0); constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitFieldInsn(GETSTATIC, owner, "ITEMS", "L" + DR + ";");
        constructor.visitMethodInsn(INVOKESTATIC, CTX, "get", "()L" + CTX + ";", false);
        constructor.visitMethodInsn(INVOKEVIRTUAL, CTX, "getModEventBus", "()L" + BUS + ";", false);
        constructor.visitMethodInsn(INVOKEVIRTUAL, DR, "register", "(L" + BUS + ";)V", false); end(constructor, RETURN);
        var item = method(writer, ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "item", "()L" + ITEM + ";");
        item.visitTypeInsn(NEW, ITEM); item.visitInsn(DUP); item.visitTypeInsn(NEW, PROPS); item.visitInsn(DUP);
        item.visitMethodInsn(INVOKESPECIAL, PROPS, "<init>", "()V", false);
        item.visitMethodInsn(INVOKESPECIAL, ITEM, "<init>", "(L" + PROPS + ";)V", false); end(item, ARETURN);
        writer.visitEnd();
        String metadata = "modLoader=\"javafml\"\nloaderVersion=\"[36,37)\"\nlicense=\"MIT\"\n[[mods]]\nmodId=\""
                + id + "\"\nversion=\"1.0\"\n";
        for (var dep : new TreeMap<>(Map.of("forge", "[36.2.42,37)", "minecraft", "[1.16.5]")).entrySet())
            metadata += "[[dependencies." + id + "]]\nmodId=\"" + dep.getKey() + "\"\nmandatory=true\nversionRange=\""
                    + dep.getValue() + "\"\nordering=\"NONE\"\nside=\"BOTH\"\n";
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put(owner + ".class", writer.toByteArray());
        entries.put("META-INF/mods.toml", metadata.getBytes(StandardCharsets.UTF_8));
        entries.put("pack.mcmeta", "{\"pack\":{\"pack_format\":6,\"description\":\"Inert receipt fixture\"}}".getBytes(StandardCharsets.UTF_8));
        entries.put("assets/" + id + "/models/item/token.json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/iron_ingot\"}}".getBytes(StandardCharsets.UTF_8));
        try (var zip = new ZipOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            for (var entry : entries.entrySet()) {
                var zipEntry = new ZipEntry(entry.getKey()); zipEntry.setTime(0);
                zip.putNextEntry(zipEntry); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        return path;
    }
    private static MethodVisitor method(ClassWriter writer, int access, String name, String descriptor) {
        var method = writer.visitMethod(access, name, descriptor, null, null); method.visitCode(); return method;
    }
    private static void end(MethodVisitor method, int opcode) { method.visitInsn(opcode); method.visitMaxs(0, 0); method.visitEnd(); }
}
