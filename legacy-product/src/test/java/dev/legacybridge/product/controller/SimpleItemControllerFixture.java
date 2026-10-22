package dev.legacybridge.product.controller;

import java.io.IOException;
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

/** Inert, original one-item fixture. Never loaded; no generated API inventory or mock conversion engine. */
final class SimpleItemControllerFixture {
    private static final String OWNER = "fixture/IncludedItem";
    private static final String MOD_ID = "included_item";
    private static final String ITEM = "net/minecraft/item/Item";
    private static final String PROPERTIES = ITEM + "$Properties";
    private static final String REGISTER = "net/minecraftforge/registries/DeferredRegister";
    private static final String REGISTRY = "net/minecraftforge/registries/IForgeRegistry";
    private static final String OBJECT = "net/minecraftforge/fml/RegistryObject";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String BUS = "net/minecraftforge/eventbus/api/IEventBus";

    private SimpleItemControllerFixture() { }

    static Path write(Path path) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put("META-INF/mods.toml", utf8("""
                modLoader="javafml"
                loaderVersion="[36,)"
                license="MIT"
                [[mods]]
                modId="included_item"
                version="1.0.0"
                displayName="Included contract item"
                [[dependencies.included_item]]
                modId="forge"
                mandatory=true
                versionRange="[36.2.42,37)"
                ordering="NONE"
                side="BOTH"
                [[dependencies.included_item]]
                modId="minecraft"
                mandatory=true
                versionRange="[1.16.5]"
                ordering="NONE"
                side="BOTH"
                """));
        entries.put("pack.mcmeta", utf8("{\"pack\":{\"pack_format\":6,\"description\":\"Inert controller fixture\"}}"));
        entries.put("assets/included_item/models/item/ingot.json", utf8(
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/iron_ingot\"}}"));
        entries.put(OWNER + ".class", bytecode());
        try (var zip = new ZipOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return path;
    }

    private static byte[] bytecode() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, OWNER, null, "java/lang/Object", null);
        var annotation = writer.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true);
        annotation.visit("value", MOD_ID);
        annotation.visitEnd();
        writer.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "ITEMS", "L" + REGISTER + ";", null, null).visitEnd();
        writer.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "INGOT", "L" + OBJECT + ";", null, null).visitEnd();
        MethodVisitor initializer = method(writer, ACC_STATIC, "<clinit>", "()V");
        initializer.visitFieldInsn(GETSTATIC, "net/minecraftforge/registries/ForgeRegistries", "ITEMS", "L" + REGISTRY + ";");
        initializer.visitLdcInsn(MOD_ID);
        initializer.visitMethodInsn(INVOKESTATIC, REGISTER, "create", "(L" + REGISTRY + ";Ljava/lang/String;)L" + REGISTER + ";", false);
        initializer.visitFieldInsn(PUTSTATIC, OWNER, "ITEMS", "L" + REGISTER + ";");
        initializer.visitFieldInsn(GETSTATIC, OWNER, "ITEMS", "L" + REGISTER + ";");
        initializer.visitLdcInsn("ingot");
        Handle metafactory = new Handle(H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                        + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
        initializer.visitInvokeDynamicInsn("get", "()Ljava/util/function/Supplier;", metafactory,
                Type.getMethodType("()Ljava/lang/Object;"), new Handle(H_INVOKESTATIC, OWNER, "item", "()L" + ITEM + ";", false),
                Type.getMethodType("()L" + ITEM + ";"));
        initializer.visitMethodInsn(INVOKEVIRTUAL, REGISTER, "register", "(Ljava/lang/String;Ljava/util/function/Supplier;)L" + OBJECT + ";", false);
        initializer.visitFieldInsn(PUTSTATIC, OWNER, "INGOT", "L" + OBJECT + ";");
        end(initializer, RETURN);

        MethodVisitor constructor = method(writer, ACC_PUBLIC, "<init>", "()V");
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitFieldInsn(GETSTATIC, OWNER, "ITEMS", "L" + REGISTER + ";");
        constructor.visitMethodInsn(INVOKESTATIC, CONTEXT, "get", "()L" + CONTEXT + ";", false);
        constructor.visitMethodInsn(INVOKEVIRTUAL, CONTEXT, "getModEventBus", "()L" + BUS + ";", false);
        constructor.visitMethodInsn(INVOKEVIRTUAL, REGISTER, "register", "(L" + BUS + ";)V", false);
        end(constructor, RETURN);

        MethodVisitor item = method(writer, ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC, "item", "()L" + ITEM + ";");
        item.visitTypeInsn(NEW, ITEM);
        item.visitInsn(DUP);
        item.visitTypeInsn(NEW, PROPERTIES);
        item.visitInsn(DUP);
        item.visitMethodInsn(INVOKESPECIAL, PROPERTIES, "<init>", "()V", false);
        item.visitMethodInsn(INVOKESPECIAL, ITEM, "<init>", "(L" + PROPERTIES + ";)V", false);
        end(item, ARETURN);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static MethodVisitor method(ClassWriter writer, int access, String name, String descriptor) {
        MethodVisitor method = writer.visitMethod(access, name, descriptor, null, null);
        method.visitCode();
        return method;
    }

    private static void end(MethodVisitor method, int opcode) {
        method.visitInsn(opcode);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static byte[] utf8(String text) { return text.getBytes(StandardCharsets.UTF_8); }
}
